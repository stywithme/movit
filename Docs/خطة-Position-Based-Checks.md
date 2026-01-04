# 📋 خطة تنفيذ Position-Based Validation System

## 🎯 الهدف

إضافة نظام فحوصات جديد يعتمد على **مواضع الـ landmarks** بدلاً من الزوايا فقط.
هذا يمكّننا من:
- فحص "هل الركبة تجاوزت القدم؟"
- فحص "هل الكتف بمحاذاة الركبة؟"
- فحص "هل القدمين متباعدتين بشكل كافي؟"

---

## 📊 أنواع الـ Position Checks المقترحة

| النوع | الوصف | الاستخدام |
|-------|-------|-----------|
| `LANDMARK_COMPARISON_X` | مقارنة الموضع الأفقي لـ 2 landmarks | الركبة vs القدم |
| `LANDMARK_COMPARISON_Y` | مقارنة الموضع الرأسي لـ 2 landmarks | الكتف فوق الورك |
| `DISTANCE_RATIO` | نسبة المسافة بين landmarks | عرض القدمين vs الكتفين |
| `ALIGNMENT` | هل 3 نقاط على خط واحد | استقامة الجسم |

---

## 🏗️ التصميم المقترح

### 1. الـ Data Models (ExerciseConfig.kt)

```kotlin
/**
 * Position-based validation check
 * يتم تنفيذها بجانب الـ angle-based validation
 */
data class PositionCheck(
    val id: String,                      // معرف فريد للفحص
    val type: PositionCheckType,         // نوع الفحص
    val landmarks: LandmarkPair,         // الـ landmarks المستخدمة
    val condition: PositionCondition,    // الشرط المطلوب
    val activePhases: List<String>,      // الـ phases التي يُفعّل فيها الفحص
    val errorMessage: ErrorMessages,     // رسائل الخطأ
    val severity: CheckSeverity = CheckSeverity.WARNING  // خطورة الخطأ
)

/**
 * أنواع فحوصات الموضع
 */
enum class PositionCheckType {
    @SerializedName("landmark_x_comparison")
    LANDMARK_X_COMPARISON,    // مقارنة x coordinates
    
    @SerializedName("landmark_y_comparison")
    LANDMARK_Y_COMPARISON,    // مقارنة y coordinates
    
    @SerializedName("distance_ratio")
    DISTANCE_RATIO,           // نسبة المسافات
    
    @SerializedName("horizontal_alignment")
    HORIZONTAL_ALIGNMENT,     // محاذاة أفقية
    
    @SerializedName("vertical_alignment")
    VERTICAL_ALIGNMENT        // محاذاة رأسية
}

/**
 * زوج الـ landmarks للمقارنة
 */
data class LandmarkPair(
    val landmark1: String,    // الـ landmark الأول (e.g., "left_knee")
    val landmark2: String,    // الـ landmark الثاني (e.g., "left_foot_index")
    val landmark3: String? = null,  // اختياري للـ alignment checks
    val landmark4: String? = null   // اختياري للـ distance ratio
)

/**
 * الشرط المطلوب
 */
data class PositionCondition(
    val operator: ComparisonOperator,   // نوع المقارنة
    val threshold: Double? = null,      // الحد المسموح (للـ alignment)
    val targetRatio: Double? = null,    // النسبة المستهدفة (للـ distance ratio)
    val tolerancePercent: Double = 10.0 // نسبة التسامح
)

enum class ComparisonOperator {
    @SerializedName("less_than")
    LESS_THAN,           // landmark1.x < landmark2.x
    
    @SerializedName("greater_than")
    GREATER_THAN,        // landmark1.x > landmark2.x
    
    @SerializedName("approximately_equal")
    APPROXIMATELY_EQUAL, // |landmark1.x - landmark2.x| < threshold
    
    @SerializedName("ratio_between")
    RATIO_BETWEEN        // نسبة المسافات
}

/**
 * خطورة الخطأ
 */
enum class CheckSeverity {
    @SerializedName("error")
    ERROR,      // خطأ يؤثر على صحة الـ rep
    
    @SerializedName("warning")
    WARNING,    // تحذير للـ form
    
    @SerializedName("tip")
    TIP         // نصيحة تحسينية
}
```

### 2. الـ JSON Schema

```json
{
  "poseVariants": [
    {
      "name": { "ar": "...", "en": "..." },
      "cameraPosition": "side_view",
      "trackedJoints": [...],
      "positionChecks": [
        {
          "id": "knee_over_toe",
          "type": "landmark_x_comparison",
          "landmarks": {
            "landmark1": "left_knee",
            "landmark2": "left_foot_index"
          },
          "condition": {
            "operator": "less_than",
            "tolerancePercent": 5
          },
          "activePhases": ["bottom", "down"],
          "errorMessage": {
            "tooHigh": {
              "ar": "ركبتك تجاوزت أصابع قدميك - ادفع الورك للخلف",
              "en": "Knee passed your toes - push hips back"
            }
          },
          "severity": "warning"
        },
        {
          "id": "shoulder_hip_alignment",
          "type": "vertical_alignment",
          "landmarks": {
            "landmark1": "left_shoulder",
            "landmark2": "left_hip",
            "landmark3": "left_knee"
          },
          "condition": {
            "operator": "approximately_equal",
            "threshold": 0.05
          },
          "activePhases": ["bottom"],
          "errorMessage": {
            "tooHigh": {
              "ar": "الجذع مائل كثيراً للأمام",
              "en": "Torso leaning too far forward"
            }
          },
          "severity": "warning"
        }
      ],
      "difficultyLevels": [...]
    }
  ]
}
```

---

## 🔧 الـ Engine Components

### 1. PositionValidator.kt (جديد)

```kotlin
package com.trainingvalidator.poc.training.engine

/**
 * PositionValidator - Validates position-based checks
 * 
 * يعمل بجانب FormValidator لفحص المواضع
 */
class PositionValidator(
    private val positionChecks: List<PositionCheck>,
    private val cameraPosition: String
) {
    companion object {
        private const val TAG = "PositionValidator"
        
        // Landmark name to index mapping
        val LANDMARK_INDEX_MAP = mapOf(
            "left_shoulder" to BodyLandmarks.LEFT_SHOULDER,
            "right_shoulder" to BodyLandmarks.RIGHT_SHOULDER,
            "left_hip" to BodyLandmarks.LEFT_HIP,
            "right_hip" to BodyLandmarks.RIGHT_HIP,
            "left_knee" to BodyLandmarks.LEFT_KNEE,
            "right_knee" to BodyLandmarks.RIGHT_KNEE,
            "left_ankle" to BodyLandmarks.LEFT_ANKLE,
            "right_ankle" to BodyLandmarks.RIGHT_ANKLE,
            "left_foot_index" to BodyLandmarks.LEFT_FOOT_INDEX,
            "right_foot_index" to BodyLandmarks.RIGHT_FOOT_INDEX,
            // ... more landmarks
        )
    }
    
    /**
     * Validate all position checks for current phase
     */
    fun validate(
        landmarks: List<SmoothedLandmark>,
        currentPhase: Phase
    ): PositionValidationResult {
        val errors = mutableListOf<PositionError>()
        
        for (check in positionChecks) {
            // Skip if not active in current phase
            if (!isActiveInPhase(check, currentPhase)) continue
            
            val result = validateCheck(check, landmarks)
            if (!result.passed) {
                errors.add(result.error!!)
            }
        }
        
        return PositionValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    private fun validateCheck(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>
    ): CheckResult {
        return when (check.type) {
            PositionCheckType.LANDMARK_X_COMPARISON -> 
                validateXComparison(check, landmarks)
            PositionCheckType.LANDMARK_Y_COMPARISON -> 
                validateYComparison(check, landmarks)
            PositionCheckType.DISTANCE_RATIO -> 
                validateDistanceRatio(check, landmarks)
            PositionCheckType.HORIZONTAL_ALIGNMENT -> 
                validateHorizontalAlignment(check, landmarks)
            PositionCheckType.VERTICAL_ALIGNMENT -> 
                validateVerticalAlignment(check, landmarks)
        }
    }
    
    private fun validateXComparison(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.landmark1, landmarks) ?: return CheckResult.skipped()
        val l2 = getLandmark(check.landmarks.landmark2, landmarks) ?: return CheckResult.skipped()
        
        val tolerance = check.condition.tolerancePercent / 100.0
        
        val passed = when (check.condition.operator) {
            ComparisonOperator.LESS_THAN -> 
                l1.x < l2.x + tolerance
            ComparisonOperator.GREATER_THAN -> 
                l1.x > l2.x - tolerance
            ComparisonOperator.APPROXIMATELY_EQUAL -> 
                abs(l1.x - l2.x) < (check.condition.threshold ?: tolerance)
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(PositionError(
                checkId = check.id,
                type = check.type,
                severity = check.severity,
                message = check.errorMessage.tooHigh,  // Use appropriate message
                landmark1Value = l1.x.toDouble(),
                landmark2Value = l2.x.toDouble()
            ))
        }
    }
    
    // ... other validation methods
}

/**
 * Result of position validation
 */
data class PositionValidationResult(
    val isValid: Boolean,
    val errors: List<PositionError>
)

/**
 * Position error details
 */
data class PositionError(
    val checkId: String,
    val type: PositionCheckType,
    val severity: CheckSeverity,
    val message: LocalizedText,
    val landmark1Value: Double,
    val landmark2Value: Double
)
```

### 2. تعديل TrainingEngine.kt

```kotlin
// في TrainingEngine.kt

// إضافة PositionValidator
private val positionValidator: PositionValidator? = 
    poseVariant.positionChecks?.takeIf { it.isNotEmpty() }?.let {
        PositionValidator(it, poseVariant.cameraPosition)
    }

// في processFrame()
private fun processFrame(landmarks: List<SmoothedLandmark>, angles: JointAngles) {
    // ... existing code ...
    
    // 6. Form validation (angles)
    val formValidation = formValidator.validate(trackedAngles, currentPhase)
    
    // 7. Position validation (NEW)
    val positionValidation = positionValidator?.validate(landmarks, currentPhase)
    
    // 8. Combine errors
    val allErrors = mutableListOf<Any>()
    allErrors.addAll(formValidation.errors)
    positionValidation?.errors?.forEach { allErrors.add(it) }
    
    // 9. Emit events for position errors
    positionValidation?.errors?.forEach { error ->
        when (error.severity) {
            CheckSeverity.ERROR -> emitEvent(FeedbackEvent.PositionError(error))
            CheckSeverity.WARNING -> emitEvent(FeedbackEvent.PositionWarning(error))
            CheckSeverity.TIP -> emitEvent(FeedbackEvent.PositionTip(error))
        }
    }
    
    // ... rest of code ...
}
```

### 3. تعديل FeedbackEvent.kt

```kotlin
// إضافة events جديدة
sealed class FeedbackEvent {
    // ... existing events ...
    
    // Position-based events
    data class PositionError(val error: PositionError) : FeedbackEvent()
    data class PositionWarning(val error: PositionError) : FeedbackEvent()
    data class PositionTip(val error: PositionError) : FeedbackEvent()
}
```

---

## 📁 هيكل الملفات

```
training/
├── engine/
│   ├── TrainingEngine.kt         ← تعديل
│   ├── FormValidator.kt          ← بدون تغيير
│   ├── PhaseStateMachine.kt      ← بدون تغيير
│   ├── PositionValidator.kt      ← جديد ⭐
│   └── HoldTimer.kt              ← بدون تغيير
├── models/
│   ├── ExerciseConfig.kt         ← إضافة PositionCheck models
│   └── PositionModels.kt         ← جديد (اختياري) ⭐
├── feedback/
│   ├── FeedbackEvent.kt          ← إضافة Position events
│   └── FeedbackManager.kt        ← تعديل للـ Position events
└── config/
    └── AppSettings.kt            ← بدون تغيير
```

---

## 🎯 مثال JSON كامل للـ Squat

```json
{
  "name": { "ar": "القرفصاء", "en": "Squat" },
  "countingMethod": "up_down",
  "poseVariants": [
    {
      "name": { "ar": "زاوية جانبية", "en": "Side View" },
      "cameraPosition": "side_view",
      "trackedJoints": [
        {
          "joint": "left_knee",
          "role": "primary",
          "upRange": { ... },
          "downRange": { ... }
        }
      ],
      "positionChecks": [
        {
          "id": "left_knee_over_toe",
          "type": "landmark_x_comparison",
          "landmarks": {
            "landmark1": "left_knee",
            "landmark2": "left_foot_index"
          },
          "condition": {
            "operator": "less_than",
            "tolerancePercent": 3
          },
          "activePhases": ["down", "bottom", "up"],
          "errorMessage": {
            "tooHigh": {
              "ar": "ركبتك تجاوزت أصابع قدميك! ادفع الورك للخلف",
              "en": "Knee past toes! Push your hips back"
            }
          },
          "severity": "warning"
        },
        {
          "id": "right_knee_over_toe",
          "type": "landmark_x_comparison",
          "landmarks": {
            "landmark1": "right_knee",
            "landmark2": "right_foot_index"
          },
          "condition": {
            "operator": "less_than",
            "tolerancePercent": 3
          },
          "activePhases": ["down", "bottom", "up"],
          "errorMessage": {
            "tooHigh": {
              "ar": "ركبتك تجاوزت أصابع قدميك! ادفع الورك للخلف",
              "en": "Knee past toes! Push your hips back"
            }
          },
          "severity": "warning"
        },
        {
          "id": "torso_alignment",
          "type": "vertical_alignment",
          "landmarks": {
            "landmark1": "left_shoulder",
            "landmark2": "left_hip"
          },
          "condition": {
            "operator": "approximately_equal",
            "threshold": 0.08
          },
          "activePhases": ["bottom"],
          "errorMessage": {
            "tooHigh": {
              "ar": "الجذع مائل للأمام كثيراً - ارفع صدرك",
              "en": "Leaning forward too much - lift your chest"
            }
          },
          "severity": "warning"
        }
      ],
      "difficultyLevels": [...]
    }
  ]
}
```

---

## ⏱️ خطوات التنفيذ

| الخطوة | الوصف | الوقت المقدر |
|--------|-------|--------------|
| 1 | إضافة الـ Data Models في ExerciseConfig.kt | 30 دقيقة |
| 2 | إنشاء PositionValidator.kt | 2 ساعة |
| 3 | تعديل TrainingEngine.kt للتكامل | 1 ساعة |
| 4 | تعديل FeedbackEvent.kt و FeedbackManager.kt | 30 دقيقة |
| 5 | تعديل UI للتعامل مع Position errors | 1 ساعة |
| 6 | تحديث squat.json لإضافة positionChecks | 30 دقيقة |
| 7 | اختبار وتصحيح | 2 ساعة |
| **المجموع** | | **~7.5 ساعات** |

---

## ✅ مميزات هذا التصميم

1. **مرونة عالية** - يمكن إضافة أي نوع فحص جديد بسهولة
2. **فصل المسؤوليات** - PositionValidator منفصل عن FormValidator
3. **JSON واضح** - سهل الفهم للـ Backend team
4. **قابل للتوسع** - يمكن إضافة أنواع checks جديدة
5. **متوافق مع الـ camera position** - يعمل مع side_view و front_view
6. **severity levels** - يمكن التفريق بين أخطاء خطيرة وتحذيرات

---

## ✅ القرارات المتفق عليها

1. **نعم - نحتاج difficulty levels للـ position checks**
   - tolerancePercent مختلف للمبتدئين (5%) vs المتقدمين (2%)

2. **Position Errors تؤثر على الـ rep quality لكن لا تمنع العد**
   - `severity: "error"` → تؤثر على الـ accuracy score
   - `severity: "warning"` → لا تؤثر، فقط تحذير
   - `severity: "tip"` → نصيحة تحسينية

---

## 🎥 دعم تغير زاوية التصوير (Camera-Aware)

### المشكلة:
```
Side View:              Front View:
knee.x > toe.x ✅       knee.x ≈ toe.x ❌
(يعمل)                  (لا يعمل - يجب استخدام Z)
```

### الحل: Camera-Aware Axis Selection

بدلاً من تحديد `x` أو `y` مباشرة، نستخدم **المحاور النسبية**:

```kotlin
enum class RelativeAxis {
    FORWARD,    // للأمام (نحو الكاميرا أو بعيداً عنها)
    SIDEWAYS,   // جانبياً (يمين/يسار من منظور الكاميرا)
    VERTICAL    // رأسياً (أعلى/أسفل)
}
```

### كيف يعمل:

| Camera Position | FORWARD | SIDEWAYS | VERTICAL |
|-----------------|---------|----------|----------|
| `side_view` | X axis | Z axis | Y axis |
| `front_view` | Z axis | X axis | Y axis |
| `back_view` | -Z axis | X axis | Y axis |

### الـ JSON المحدث:

```json
{
  "id": "knee_over_toe",
  "type": "landmark_forward_comparison",
  "landmarks": {
    "landmark1": "left_knee",
    "landmark2": "left_foot_index"
  },
  "condition": {
    "operator": "should_not_exceed",
    "toleranceByDifficulty": {
      "beginner": 5,
      "normal": 3,
      "advanced": 2
    }
  },
  "activePhases": ["down", "bottom"],
  "errorMessage": {...},
  "severity": "error"
}
```

### الكود المحدث:

```kotlin
/**
 * Get the appropriate axis based on camera position
 */
fun getAxisValue(
    landmark: SmoothedLandmark,
    axis: RelativeAxis,
    cameraPosition: String
): Float {
    return when (axis) {
        RelativeAxis.VERTICAL -> landmark.y
        
        RelativeAxis.FORWARD -> when (cameraPosition) {
            "side_view" -> landmark.x
            "front_view", "back_view" -> landmark.z
            else -> landmark.x
        }
        
        RelativeAxis.SIDEWAYS -> when (cameraPosition) {
            "side_view" -> landmark.z
            "front_view", "back_view" -> landmark.x
            else -> landmark.z
        }
    }
}
```

---

## 📊 أنواع الـ Position Checks المحدثة

| النوع | الوصف | يعمل مع |
|-------|-------|---------|
| `landmark_forward_comparison` | مقارنة على المحور الأمامي | كل الزوايا ✅ |
| `landmark_vertical_comparison` | مقارنة رأسية | كل الزوايا ✅ |
| `landmark_sideways_comparison` | مقارنة جانبية | كل الزوايا ✅ |
| `vertical_alignment` | محاذاة رأسية | كل الزوايا ✅ |
| `distance_ratio` | نسبة المسافات | كل الزوايا ✅ |

---

## 🎯 مثال كامل مع Difficulty Levels

```json
{
  "positionChecks": [
    {
      "id": "left_knee_over_toe",
      "type": "landmark_forward_comparison",
      "landmarks": {
        "landmark1": "left_knee",
        "landmark2": "left_foot_index"
      },
      "condition": {
        "operator": "should_not_exceed",
        "toleranceByDifficulty": {
          "beginner": 0.05,
          "normal": 0.03,
          "advanced": 0.02
        }
      },
      "activePhases": ["down", "bottom", "up"],
      "errorMessage": {
        "tooHigh": {
          "ar": "ركبتك تجاوزت أصابع قدميك! ادفع الورك للخلف",
          "en": "Knee past toes! Push your hips back"
        }
      },
      "severity": "error"
    }
  ]
}
```

---

## 🔍 الكشف التلقائي عن Camera Position + استخدام Z Depth

### المبدأ:

نستخدم **المقارنة بين Z coordinates** للـ landmarks لتحديد:
1. **زاوية الكاميرا** (من أين يصور؟)
2. **وضعية الشخص** (أي جانب أقرب للكاميرا؟)
3. **المسافات النسبية** بين الـ joints

---

### 📊 كيف نكشف Camera Position:

```
Front View:                    Side View (Left):
     📱                              📱
      ↓                               ↓
   ●     ●                         ●
   L     R                         L → R
   ↓     ↓                         (L أقرب)
shoulder.z ≈ shoulder.z      leftShoulder.z < rightShoulder.z


Side View (Right):             Back View:
      📱                              📱
       ↓                               ↓
          ●                         ●     ●
     L ← R                          L     R
    (R أقرب)                    (مثل Front لكن الوجه بعيد)
rightShoulder.z < leftShoulder.z
```

---

### 🔧 CameraPositionDetector.kt (مكون جديد)

```kotlin
package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs

/**
 * CameraPositionDetector - Automatically detects camera position
 * 
 * Uses Z-depth analysis to determine:
 * - Camera angle (front, side_left, side_right, back)
 * - Which side of the body is closer to camera
 * - Relative distances between joints
 */
object CameraPositionDetector {
    
    companion object {
        private const val TAG = "CameraPositionDetector"
        
        // Thresholds for detection
        const val SHOULDER_X_DIFF_THRESHOLD = 0.12f  // إذا أقل = front/back view
        const val SHOULDER_Z_DIFF_THRESHOLD = 0.08f  // إذا أكبر = side view
        const val NOSE_VISIBILITY_THRESHOLD = 0.6f   // لتحديد front vs back
    }
    
    /**
     * Detected camera position with confidence
     */
    data class CameraDetectionResult(
        val position: DetectedCameraPosition,
        val confidence: Float,                    // 0.0 - 1.0
        val closerSide: BodySide,                 // أي جانب أقرب للكاميرا
        val depthInfo: DepthInfo                  // معلومات العمق
    )
    
    /**
     * Detected camera positions
     */
    enum class DetectedCameraPosition {
        FRONT_VIEW,      // الكاميرا من الأمام
        BACK_VIEW,       // الكاميرا من الخلف
        SIDE_VIEW_LEFT,  // الكاميرا من اليسار (الجانب الأيسر أقرب)
        SIDE_VIEW_RIGHT, // الكاميرا من اليمين (الجانب الأيمن أقرب)
        DIAGONAL,        // زاوية مائلة
        UNKNOWN          // غير محدد
    }
    
    /**
     * Which side of body is closer to camera
     */
    enum class BodySide {
        LEFT,
        RIGHT,
        BOTH_EQUAL,  // Front/Back view
        UNKNOWN
    }
    
    /**
     * Depth information for all key landmarks
     */
    data class DepthInfo(
        val leftShoulderZ: Float,
        val rightShoulderZ: Float,
        val leftHipZ: Float,
        val rightHipZ: Float,
        val leftKneeZ: Float,
        val rightKneeZ: Float,
        val averageBodyZ: Float,           // متوسط عمق الجسم
        val bodyDepthRange: Float          // الفرق بين أقرب وأبعد نقطة
    )
    
    /**
     * Detect camera position from landmarks
     */
    fun detect(landmarks: List<SmoothedLandmark>): CameraDetectionResult {
        if (landmarks.size < 33) {
            return CameraDetectionResult(
                DetectedCameraPosition.UNKNOWN, 0f, 
                BodySide.UNKNOWN, createEmptyDepthInfo()
            )
        }
        
        // Extract key landmarks
        val leftShoulder = landmarks[BodyLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks[BodyLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks[BodyLandmarks.LEFT_HIP]
        val rightHip = landmarks[BodyLandmarks.RIGHT_HIP]
        val leftKnee = landmarks[BodyLandmarks.LEFT_KNEE]
        val rightKnee = landmarks[BodyLandmarks.RIGHT_KNEE]
        val nose = landmarks[BodyLandmarks.NOSE]
        
        // Calculate depth info
        val depthInfo = DepthInfo(
            leftShoulderZ = leftShoulder.z,
            rightShoulderZ = rightShoulder.z,
            leftHipZ = leftHip.z,
            rightHipZ = rightHip.z,
            leftKneeZ = leftKnee.z,
            rightKneeZ = rightKnee.z,
            averageBodyZ = (leftShoulder.z + rightShoulder.z + 
                           leftHip.z + rightHip.z) / 4f,
            bodyDepthRange = calculateBodyDepthRange(landmarks)
        )
        
        // Calculate differences
        val shoulderXDiff = abs(leftShoulder.x - rightShoulder.x)
        val shoulderZDiff = abs(leftShoulder.z - rightShoulder.z)
        val hipZDiff = abs(leftHip.z - rightHip.z)
        
        // Average Z difference between left and right side
        val avgLeftZ = (leftShoulder.z + leftHip.z + leftKnee.z) / 3f
        val avgRightZ = (rightShoulder.z + rightHip.z + rightKnee.z) / 3f
        val sideZDiff = avgLeftZ - avgRightZ
        
        // Determine closer side
        val closerSide = when {
            abs(sideZDiff) < 0.03f -> BodySide.BOTH_EQUAL
            sideZDiff < 0 -> BodySide.LEFT   // Left side has smaller Z = closer
            else -> BodySide.RIGHT
        }
        
        // Detect camera position
        val (position, confidence) = when {
            // Front/Back View: Shoulders are spread in X, similar in Z
            shoulderXDiff > SHOULDER_X_DIFF_THRESHOLD && 
            shoulderZDiff < SHOULDER_Z_DIFF_THRESHOLD -> {
                // Check if nose is visible to distinguish front from back
                if (nose.visibility > NOSE_VISIBILITY_THRESHOLD) {
                    DetectedCameraPosition.FRONT_VIEW to 0.9f
                } else {
                    DetectedCameraPosition.BACK_VIEW to 0.7f
                }
            }
            
            // Side View: Shoulders are close in X, different in Z
            shoulderXDiff < SHOULDER_X_DIFF_THRESHOLD &&
            shoulderZDiff > SHOULDER_Z_DIFF_THRESHOLD -> {
                val sideConfidence = minOf(shoulderZDiff / 0.15f, 1f)
                if (sideZDiff < 0) {
                    DetectedCameraPosition.SIDE_VIEW_LEFT to sideConfidence
                } else {
                    DetectedCameraPosition.SIDE_VIEW_RIGHT to sideConfidence
                }
            }
            
            // Diagonal: Mix of both
            shoulderXDiff > 0.08f && shoulderZDiff > 0.05f -> {
                DetectedCameraPosition.DIAGONAL to 0.6f
            }
            
            else -> DetectedCameraPosition.UNKNOWN to 0.3f
        }
        
        return CameraDetectionResult(position, confidence, closerSide, depthInfo)
    }
    
    /**
     * Calculate the depth range of the body
     */
    private fun calculateBodyDepthRange(landmarks: List<SmoothedLandmark>): Float {
        val keyIndices = listOf(
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
            BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP,
            BodyLandmarks.LEFT_KNEE, BodyLandmarks.RIGHT_KNEE
        )
        
        val zValues = keyIndices.mapNotNull { 
            landmarks.getOrNull(it)?.z 
        }
        
        if (zValues.isEmpty()) return 0f
        
        return (zValues.maxOrNull() ?: 0f) - (zValues.minOrNull() ?: 0f)
    }
    
    private fun createEmptyDepthInfo() = DepthInfo(
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    )
    
    /**
     * Convert detected position to JSON camera position string
     */
    fun toJsonCameraPosition(detected: DetectedCameraPosition): String {
        return when (detected) {
            DetectedCameraPosition.FRONT_VIEW -> "front_view"
            DetectedCameraPosition.BACK_VIEW -> "back_view"
            DetectedCameraPosition.SIDE_VIEW_LEFT -> "side_view"
            DetectedCameraPosition.SIDE_VIEW_RIGHT -> "side_view"
            DetectedCameraPosition.DIAGONAL -> "diagonal"
            DetectedCameraPosition.UNKNOWN -> "unknown"
        }
    }
    
    /**
     * Check if detected position matches expected position
     */
    fun matchesExpected(
        detected: DetectedCameraPosition,
        expected: String
    ): Boolean {
        return when (expected) {
            "side_view" -> detected == DetectedCameraPosition.SIDE_VIEW_LEFT ||
                          detected == DetectedCameraPosition.SIDE_VIEW_RIGHT
            "front_view" -> detected == DetectedCameraPosition.FRONT_VIEW
            "back_view" -> detected == DetectedCameraPosition.BACK_VIEW
            else -> true
        }
    }
}
```

---

### 📐 استخدام Z في Position Checks

```kotlin
/**
 * Z-Aware Position Comparisons
 */
object ZDepthComparison {
    
    /**
     * Compare landmarks in FORWARD direction
     * Uses X for side_view, Z for front_view
     */
    fun compareForward(
        landmark1: SmoothedLandmark,
        landmark2: SmoothedLandmark,
        cameraPosition: DetectedCameraPosition
    ): Float {
        return when (cameraPosition) {
            DetectedCameraPosition.SIDE_VIEW_LEFT,
            DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
                // في side view: X هو المحور الأمامي
                landmark1.x - landmark2.x
            }
            DetectedCameraPosition.FRONT_VIEW,
            DetectedCameraPosition.BACK_VIEW -> {
                // في front/back view: Z هو المحور الأمامي
                // Z سالب = أقرب للكاميرا = أكثر للأمام
                landmark2.z - landmark1.z  // عكس لأن Z سالب = أقرب
            }
            else -> {
                // Fallback: استخدام Z
                landmark2.z - landmark1.z
            }
        }
    }
    
    /**
     * Get relative depth (distance from camera) for a landmark
     * Returns normalized value: 0 = closest, 1 = farthest
     */
    fun getRelativeDepth(
        landmark: SmoothedLandmark,
        depthInfo: DepthInfo
    ): Float {
        val minZ = minOf(depthInfo.leftShoulderZ, depthInfo.rightShoulderZ,
                        depthInfo.leftHipZ, depthInfo.rightHipZ)
        val maxZ = maxOf(depthInfo.leftShoulderZ, depthInfo.rightShoulderZ,
                        depthInfo.leftHipZ, depthInfo.rightHipZ)
        
        if (maxZ == minZ) return 0.5f
        
        return (landmark.z - minZ) / (maxZ - minZ)
    }
    
    /**
     * Check if two landmarks are aligned in depth (same distance from camera)
     */
    fun areAlignedInDepth(
        landmark1: SmoothedLandmark,
        landmark2: SmoothedLandmark,
        tolerance: Float = 0.05f
    ): Boolean {
        return abs(landmark1.z - landmark2.z) < tolerance
    }
    
    /**
     * Calculate 3D distance between two landmarks
     * More accurate than 2D for position checks
     */
    fun calculate3DDistance(
        landmark1: SmoothedLandmark,
        landmark2: SmoothedLandmark
    ): Float {
        val dx = landmark1.x - landmark2.x
        val dy = landmark1.y - landmark2.y
        val dz = landmark1.z - landmark2.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
}
```

---

### 🔄 تحديث PositionValidator لاستخدام الكشف التلقائي

```kotlin
class PositionValidator(
    private val positionChecks: List<PositionCheck>,
    private val expectedCameraPosition: String  // من الـ JSON
) {
    
    // الكشف التلقائي عن الـ camera position
    private var detectedCamera: CameraPositionDetector.CameraDetectionResult? = null
    private var cameraWarningEmitted = false
    
    /**
     * Validate with auto-detection
     */
    fun validate(
        landmarks: List<SmoothedLandmark>,
        currentPhase: Phase,
        difficulty: DifficultyType
    ): PositionValidationResult {
        
        // 1. كشف camera position تلقائياً
        detectedCamera = CameraPositionDetector.detect(landmarks)
        
        // 2. تحذير إذا كانت الزاوية غير مطابقة
        val cameraWarning = checkCameraPosition()
        
        // 3. تنفيذ الفحوصات
        val errors = mutableListOf<PositionError>()
        
        for (check in positionChecks) {
            if (!isActiveInPhase(check, currentPhase)) continue
            
            val result = validateCheck(
                check, 
                landmarks, 
                difficulty,
                detectedCamera!!
            )
            
            if (!result.passed) {
                errors.add(result.error!!)
            }
        }
        
        return PositionValidationResult(
            isValid = errors.none { it.severity == CheckSeverity.ERROR },
            errors = errors,
            cameraWarning = cameraWarning,
            detectedCameraPosition = detectedCamera!!.position,
            depthInfo = detectedCamera!!.depthInfo
        )
    }
    
    private fun checkCameraPosition(): PositionWarning? {
        val detected = detectedCamera ?: return null
        
        if (!CameraPositionDetector.matchesExpected(
                detected.position, expectedCameraPosition)) {
            
            return PositionWarning(
                type = WarningType.WRONG_CAMERA_ANGLE,
                message = LocalizedText(
                    ar = "يُفضل التصوير من ${getPositionNameAr(expectedCameraPosition)} للحصول على نتائج أفضل",
                    en = "For best results, film from ${expectedCameraPosition.replace("_", " ")}"
                ),
                confidence = detected.confidence
            )
        }
        return null
    }
    
    /**
     * Validate single check using detected camera position
     */
    private fun validateCheck(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        difficulty: DifficultyType,
        cameraResult: CameraPositionDetector.CameraDetectionResult
    ): CheckResult {
        
        val l1 = getLandmark(check.landmarks.landmark1, landmarks) 
            ?: return CheckResult.skipped()
        val l2 = getLandmark(check.landmarks.landmark2, landmarks) 
            ?: return CheckResult.skipped()
        
        // Get tolerance based on difficulty
        val tolerance = check.condition.toleranceByDifficulty?.let {
            when (difficulty) {
                DifficultyType.BEGINNER -> it.beginner
                DifficultyType.NORMAL -> it.normal
                DifficultyType.ADVANCED -> it.advanced
            }
        } ?: check.condition.tolerancePercent ?: 0.05
        
        return when (check.type) {
            PositionCheckType.LANDMARK_FORWARD_COMPARISON -> {
                val forwardDiff = ZDepthComparison.compareForward(
                    l1, l2, cameraResult.position
                )
                
                val passed = when (check.condition.operator) {
                    ComparisonOperator.SHOULD_NOT_EXCEED -> 
                        forwardDiff <= tolerance
                    ComparisonOperator.SHOULD_EXCEED ->
                        forwardDiff >= -tolerance
                    else -> true
                }
                
                createResult(check, passed, forwardDiff.toDouble())
            }
            
            PositionCheckType.LANDMARK_VERTICAL_COMPARISON -> {
                // Y works the same in all camera positions
                val yDiff = l1.y - l2.y
                val passed = when (check.condition.operator) {
                    ComparisonOperator.LESS_THAN -> yDiff < tolerance
                    ComparisonOperator.GREATER_THAN -> yDiff > -tolerance
                    else -> true
                }
                createResult(check, passed, yDiff.toDouble())
            }
            
            PositionCheckType.DEPTH_ALIGNMENT -> {
                // Check if landmarks are at same depth
                val aligned = ZDepthComparison.areAlignedInDepth(
                    l1, l2, tolerance.toFloat()
                )
                createResult(check, aligned, abs(l1.z - l2.z).toDouble())
            }
            
            else -> CheckResult.passed()
        }
    }
}
```

---

### 📊 الـ JSON المحدث مع Depth Checks

```json
{
  "positionChecks": [
    {
      "id": "knee_over_toe",
      "type": "landmark_forward_comparison",
      "landmarks": {
        "landmark1": "left_knee",
        "landmark2": "left_foot_index"
      },
      "condition": {
        "operator": "should_not_exceed",
        "toleranceByDifficulty": {
          "beginner": 0.05,
          "normal": 0.03,
          "advanced": 0.02
        }
      },
      "activePhases": ["down", "bottom"],
      "errorMessage": {
        "tooHigh": {
          "ar": "ركبتك تجاوزت أصابع قدميك!",
          "en": "Knee past toes!"
        }
      },
      "severity": "error"
    },
    {
      "id": "shoulder_knee_depth_alignment",
      "type": "depth_alignment",
      "landmarks": {
        "landmark1": "left_shoulder",
        "landmark2": "left_knee"
      },
      "condition": {
        "operator": "approximately_equal",
        "toleranceByDifficulty": {
          "beginner": 0.08,
          "normal": 0.06,
          "advanced": 0.04
        }
      },
      "activePhases": ["bottom"],
      "errorMessage": {
        "tooHigh": {
          "ar": "الكتف يجب أن يكون بمحاذاة الركبة",
          "en": "Shoulder should align with knee"
        }
      },
      "severity": "warning"
    }
  ]
}
```

---

## 📝 ملاحظات مهمة

- الـ `FORWARD` axis هو الأهم للـ knee_over_toe check
- الـ Z coordinate من MediaPipe ليس دقيق 100% في front_view
- يُفضل استخدام side_view للتمارين التي تحتاج forward checks
- الـ VERTICAL axis يعمل بدقة في كل الزوايا

