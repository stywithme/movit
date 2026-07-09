> **Status:** `ARCHIVED` — implemented or superseded; not current product truth.
> **Current SSOT:** [`Docs/00-Active-Reference/Engine/Positions-Check-Concept.md`](../../00-Active-Reference/Engine/Positions-Check-Concept.md)
> **Archived:** 2026-06-22

# 📋 خطة تنفيذ Position-Based Validation System (V2)

## 🎯 الهدف

إضافة نظام فحوصات جديد يعتمد على **مواضع الـ landmarks** بدلاً من الزوايا فقط.
هذا يمكّننا من:
- فحص "هل الركبة تجاوزت القدم؟" (knee-over-toe)
- فحص "هل الكتف بمحاذاة الركبة؟" (alignment)
- فحص "هل القدمين متباعدتين بشكل كافي؟" (distance ratio)
- الكشف التلقائي عن زاوية الكاميرا واتجاه الجسم

---

## 📊 ملخص التغييرات على الكود الحالي

| الملف | نوع التغيير | الوصف |
|-------|-------------|-------|
| `ExerciseConfig.kt` | تعديل | إضافة `positionChecks` للـ `PoseVariant` |
| `TrainingEngine.kt` | تعديل | تعديل `processFrame` لاستقبال landmarks + تكامل PositionValidator |
| `PositionValidator.kt` | جديد ⭐ | المحرك الرئيسي لفحوصات المواضع |
| `CameraPositionDetector.kt` | جديد ⭐ | الكشف التلقائي عن زاوية الكاميرا |
| `FeedbackEvent.kt` | تعديل | إضافة Position events |
| `FeedbackManager.kt` | تعديل | معالجة Position events |
| `SkeletonOverlayView.kt` | تعديل | عرض position errors بصريًا |

---

## 🏗️ 1. الـ Data Models (ExerciseConfig.kt)

### 1.1 تعديل PoseVariant

```kotlin
/**
 * Pose variant - represents one camera angle/view
 * UPDATED: Added positionChecks field
 */
data class PoseVariant(
    val name: LocalizedText,
    val cameraPosition: String,                              // "side_view", "front_view", "back_view"
    val expectedFacingDirection: FacingDirection? = null,    // NEW: اتجاه الجسم المتوقع
    val trackedJoints: List<TrackedJoint> = emptyList(),
    val positionChecks: List<PositionCheck> = emptyList(),   // NEW: فحوصات المواضع
    val feedbackMessages: FeedbackMessages = FeedbackMessages(),
    val difficultyLevels: List<DifficultyLevel> = emptyList()
)

/**
 * Expected facing direction of the person in the frame
 * Required for accurate position comparisons
 */
enum class FacingDirection {
    @SerializedName("facing_right")
    FACING_RIGHT,     // الشخص ينظر لليمين (جانب أيسر أقرب للكاميرا)
    
    @SerializedName("facing_left")
    FACING_LEFT,      // الشخص ينظر لليسار (جانب أيمن أقرب للكاميرا)
    
    @SerializedName("facing_camera")
    FACING_CAMERA,    // الشخص مواجه للكاميرا
    
    @SerializedName("facing_away")
    FACING_AWAY,      // الشخص ظهره للكاميرا
    
    @SerializedName("auto_detect")
    AUTO_DETECT       // كشف تلقائي
}
```

### 1.2 Position Check Models

```kotlin
/**
 * Position-based validation check
 * Works alongside angle-based validation (FormValidator)
 */
data class PositionCheck(
    val id: String,                                    // معرف فريد (e.g., "left_knee_over_toe")
    val type: PositionCheckType,                       // نوع الفحص
    val landmarks: LandmarkGroup,                      // الـ landmarks المستخدمة
    val condition: PositionCondition,                  // الشرط المطلوب
    val activePhases: List<String>,                    // الـ phases التي يُفعّل فيها
    val errorMessage: LocalizedText,                   // رسالة الخطأ (Arabic + English)
    val severity: CheckSeverity = CheckSeverity.WARNING,
    val cooldownMs: Long = 2000,                       // فترة التهدئة بين نفس الخطأ
    val minErrorFrames: Int = 3                        // عدد الـ frames المتتالية لتأكيد الخطأ (منع flicker)
)

/**
 * Position check types - unified and clear
 */
enum class PositionCheckType {
    @SerializedName("forward_comparison")
    FORWARD_COMPARISON,       // مقارنة على المحور الأمامي (X في side_view, Z في front_view)
    
    @SerializedName("vertical_comparison")
    VERTICAL_COMPARISON,      // مقارنة رأسية (Y axis - works in all views)
    
    @SerializedName("sideways_comparison")
    SIDEWAYS_COMPARISON,      // مقارنة جانبية (Z في side_view, X في front_view)
    
    @SerializedName("distance_ratio")
    DISTANCE_RATIO,           // نسبة المسافة بين مجموعتين من landmarks
    
    @SerializedName("horizontal_alignment")
    HORIZONTAL_ALIGNMENT,     // هل النقاط على خط أفقي واحد
    
    @SerializedName("vertical_alignment")
    VERTICAL_ALIGNMENT,       // هل النقاط على خط رأسي واحد
    
    @SerializedName("depth_alignment")
    DEPTH_ALIGNMENT           // هل النقاط على نفس العمق من الكاميرا
}

/**
 * Landmark group for comparison
 * Supports 2-4 landmarks depending on check type
 */
data class LandmarkGroup(
    val primary: String,              // الـ landmark الأساسي (e.g., "left_knee")
    val secondary: String,            // الـ landmark للمقارنة (e.g., "left_foot_index")
    val tertiary: String? = null,     // اختياري - للـ alignment (3 نقاط)
    val quaternary: String? = null    // اختياري - للـ distance ratio (مجموعة ثانية)
)

/**
 * Position condition with difficulty-aware thresholds
 * Follows existing DifficultyRanges pattern
 */
data class PositionCondition(
    val operator: PositionOperator,
    val thresholds: DifficultyThresholds     // حدود حسب الصعوبة
)

/**
 * Threshold values per difficulty level
 * Similar to existing DifficultyRanges pattern
 */
data class DifficultyThresholds(
    val beginner: Double,
    val normal: Double,
    val advanced: Double
)

/**
 * Position comparison operators
 */
enum class PositionOperator {
    @SerializedName("should_not_exceed")
    SHOULD_NOT_EXCEED,        // primary لا يجب أن يتجاوز secondary
    
    @SerializedName("should_exceed")
    SHOULD_EXCEED,            // primary يجب أن يتجاوز secondary
    
    @SerializedName("approximately_equal")
    APPROXIMATELY_EQUAL,      // الفرق أقل من threshold
    
    @SerializedName("greater_than_ratio")
    GREATER_THAN_RATIO,       // النسبة أكبر من threshold
    
    @SerializedName("less_than_ratio")
    LESS_THAN_RATIO           // النسبة أقل من threshold
}

/**
 * Check severity - affects scoring
 */
enum class CheckSeverity {
    @SerializedName("error")
    ERROR,      // يؤثر على صحة الـ rep (مثل angle errors)
    
    @SerializedName("warning")
    WARNING,    // تحذير للـ form - لا يؤثر على العد
    
    @SerializedName("tip")
    TIP         // نصيحة تحسينية فقط
}

// NOTE:
// In the current implementation we use LocalizedText directly for PositionCheck.errorMessage
// (simpler JSON + matches existing project patterns).
```

---

## 🔧 2. الـ Engine Components

### 2.1 CameraPositionDetector.kt (جديد)

```kotlin
package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs

/**
 * CameraPositionDetector - Automatically detects camera position and body facing
 * 
 * Uses Z-depth and X-position analysis to determine:
 * - Camera angle (front, side_left, side_right, back)
 * - Which side of the body is closer to camera
 * - Body facing direction
 */
object CameraPositionDetector {
    
    private const val TAG = "CameraPositionDetector"
    
    // Detection thresholds (configurable via app_settings.json in future)
    private const val SHOULDER_X_DIFF_THRESHOLD = 0.12f  // إذا أقل = front/back view
    private const val SHOULDER_Z_DIFF_THRESHOLD = 0.08f  // إذا أكبر = side view
    private const val NOSE_VISIBILITY_THRESHOLD = 0.6f   // لتحديد front vs back
    private const val FACING_Z_THRESHOLD = 0.03f         // لتحديد اتجاه الجسم
    
    /**
     * Detected camera position with confidence and body info
     */
    data class CameraDetectionResult(
        val position: DetectedCameraPosition,
        val confidence: Float,                    // 0.0 - 1.0
        val facingDirection: DetectedFacing,      // اتجاه الجسم
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
     * Detected facing direction
     */
    enum class DetectedFacing {
        FACING_RIGHT,    // الشخص ينظر لليمين في الـ frame
        FACING_LEFT,     // الشخص ينظر لليسار في الـ frame
        FACING_CAMERA,   // الشخص مواجه للكاميرا
        FACING_AWAY,     // الشخص ظهره للكاميرا
        UNKNOWN
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
     * Depth information for key landmarks
     */
    data class DepthInfo(
        val leftShoulderZ: Float,
        val rightShoulderZ: Float,
        val leftHipZ: Float,
        val rightHipZ: Float,
        val leftKneeZ: Float,
        val rightKneeZ: Float,
        val averageBodyZ: Float,
        val bodyDepthRange: Float
    )
    
    /**
     * Detect camera position and facing from landmarks
     * 
     * @param landmarks List of smoothed landmarks (33 points)
     * @return Detection result with position, facing, and confidence
     */
    fun detect(landmarks: List<SmoothedLandmark>): CameraDetectionResult {
        if (landmarks.size < 33) {
            return createUnknownResult()
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
            averageBodyZ = (leftShoulder.z + rightShoulder.z + leftHip.z + rightHip.z) / 4f,
            bodyDepthRange = calculateBodyDepthRange(landmarks)
        )
        
        // Calculate differences for detection
        val shoulderXDiff = abs(leftShoulder.x - rightShoulder.x)
        val shoulderZDiff = abs(leftShoulder.z - rightShoulder.z)
        
        // Average Z difference between left and right side
        val avgLeftZ = (leftShoulder.z + leftHip.z + leftKnee.z) / 3f
        val avgRightZ = (rightShoulder.z + rightHip.z + rightKnee.z) / 3f
        val sideZDiff = avgLeftZ - avgRightZ
        
        // Determine closer side
        val closerSide = when {
            abs(sideZDiff) < FACING_Z_THRESHOLD -> BodySide.BOTH_EQUAL
            sideZDiff < 0 -> BodySide.LEFT   // Left side has smaller Z = closer
            else -> BodySide.RIGHT
        }
        
        // Detect camera position
        val (position, confidence) = detectCameraPosition(
            shoulderXDiff, shoulderZDiff, sideZDiff, nose.visibility
        )
        
        // Detect facing direction
        val facingDirection = detectFacingDirection(position, closerSide, nose.visibility)
        
        return CameraDetectionResult(
            position = position,
            confidence = confidence,
            facingDirection = facingDirection,
            closerSide = closerSide,
            depthInfo = depthInfo
        )
    }
    
    private fun detectCameraPosition(
        shoulderXDiff: Float,
        shoulderZDiff: Float,
        sideZDiff: Float,
        noseVisibility: Float
    ): Pair<DetectedCameraPosition, Float> {
        return when {
            // Front/Back View: Shoulders are spread in X, similar in Z
            shoulderXDiff > SHOULDER_X_DIFF_THRESHOLD && 
            shoulderZDiff < SHOULDER_Z_DIFF_THRESHOLD -> {
                if (noseVisibility > NOSE_VISIBILITY_THRESHOLD) {
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
    }
    
    private fun detectFacingDirection(
        cameraPosition: DetectedCameraPosition,
        closerSide: BodySide,
        noseVisibility: Float
    ): DetectedFacing {
        return when (cameraPosition) {
            DetectedCameraPosition.FRONT_VIEW -> DetectedFacing.FACING_CAMERA
            DetectedCameraPosition.BACK_VIEW -> DetectedFacing.FACING_AWAY
            
            DetectedCameraPosition.SIDE_VIEW_LEFT,
            DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
                // In side view, facing depends on which side is closer
                // If left side is closer, person is facing right (and vice versa)
                when (closerSide) {
                    BodySide.LEFT -> DetectedFacing.FACING_RIGHT
                    BodySide.RIGHT -> DetectedFacing.FACING_LEFT
                    else -> DetectedFacing.UNKNOWN
                }
            }
            
            else -> DetectedFacing.UNKNOWN
        }
    }
    
    private fun calculateBodyDepthRange(landmarks: List<SmoothedLandmark>): Float {
        val keyIndices = listOf(
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
            BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP,
            BodyLandmarks.LEFT_KNEE, BodyLandmarks.RIGHT_KNEE
        )
        
        val zValues = keyIndices.mapNotNull { landmarks.getOrNull(it)?.z }
        if (zValues.isEmpty()) return 0f
        
        return (zValues.maxOrNull() ?: 0f) - (zValues.minOrNull() ?: 0f)
    }
    
    private fun createUnknownResult() = CameraDetectionResult(
        DetectedCameraPosition.UNKNOWN, 0f,
        DetectedFacing.UNKNOWN, BodySide.UNKNOWN,
        DepthInfo(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    )
    
    /**
     * Convert detected position to JSON camera position string
     */
    fun toJsonCameraPosition(detected: DetectedCameraPosition): String {
        return when (detected) {
            DetectedCameraPosition.FRONT_VIEW -> "front_view"
            DetectedCameraPosition.BACK_VIEW -> "back_view"
            DetectedCameraPosition.SIDE_VIEW_LEFT,
            DetectedCameraPosition.SIDE_VIEW_RIGHT -> "side_view"
            DetectedCameraPosition.DIAGONAL -> "diagonal"
            DetectedCameraPosition.UNKNOWN -> "unknown"
        }
    }
    
    /**
     * Check if detected position matches expected position from config
     */
    fun matchesExpected(detected: DetectedCameraPosition, expected: String): Boolean {
        return when (expected) {
            "side_view" -> detected == DetectedCameraPosition.SIDE_VIEW_LEFT ||
                          detected == DetectedCameraPosition.SIDE_VIEW_RIGHT
            "front_view" -> detected == DetectedCameraPosition.FRONT_VIEW
            "back_view" -> detected == DetectedCameraPosition.BACK_VIEW
            else -> true  // If unknown expected, accept any
        }
    }
}
```

### 2.2 PositionValidator.kt (جديد)

```kotlin
package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import com.trainingvalidator.poc.training.models.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PositionValidator - Validates position-based checks
 * 
 * Works alongside FormValidator to provide comprehensive exercise validation.
 * FormValidator handles angle-based checks, PositionValidator handles position-based.
 * 
 * Features:
 * - Camera-aware axis selection (FORWARD/VERTICAL/SIDEWAYS)
 * - Automatic facing direction detection
 * - Difficulty-aware thresholds
 * - Visibility gating (only checks visible landmarks)
 * - Cooldown to prevent error spam
 */
class PositionValidator(
    private val positionChecks: List<PositionCheck>,
    private val expectedCameraPosition: String,
    private val expectedFacingDirection: FacingDirection?,
    private val visibilityThreshold: Float = 0.5f
) {
    
    companion object {
        private const val TAG = "PositionValidator"
        
        // Landmark name to index mapping (matches JointAngleTracker pattern)
        val LANDMARK_INDEX_MAP = mapOf(
            "nose" to BodyLandmarks.NOSE,
            "left_shoulder" to BodyLandmarks.LEFT_SHOULDER,
            "right_shoulder" to BodyLandmarks.RIGHT_SHOULDER,
            "left_elbow" to BodyLandmarks.LEFT_ELBOW,
            "right_elbow" to BodyLandmarks.RIGHT_ELBOW,
            "left_wrist" to BodyLandmarks.LEFT_WRIST,
            "right_wrist" to BodyLandmarks.RIGHT_WRIST,
            "left_hip" to BodyLandmarks.LEFT_HIP,
            "right_hip" to BodyLandmarks.RIGHT_HIP,
            "left_knee" to BodyLandmarks.LEFT_KNEE,
            "right_knee" to BodyLandmarks.RIGHT_KNEE,
            "left_ankle" to BodyLandmarks.LEFT_ANKLE,
            "right_ankle" to BodyLandmarks.RIGHT_ANKLE,
            "left_foot_index" to BodyLandmarks.LEFT_FOOT_INDEX,
            "right_foot_index" to BodyLandmarks.RIGHT_FOOT_INDEX,
            "left_heel" to BodyLandmarks.LEFT_HEEL,
            "right_heel" to BodyLandmarks.RIGHT_HEEL
        )
        
        // Hysteresis buffer to prevent flickering
        private const val HYSTERESIS_BUFFER = 0.02f
        
        // Minimum frames to confirm a position error
        private const val MIN_ERROR_FRAMES = 3
    }
    
    // Error cooldown tracking
    private val lastErrorTimes = mutableMapOf<String, Long>()
    
    // Error frame counting for stability
    private val errorFrameCounts = mutableMapOf<String, Int>()
    
    // Cached camera detection result
    private var cachedCameraResult: CameraPositionDetector.CameraDetectionResult? = null
    
    /**
     * Validate all position checks for current phase
     * 
     * @param landmarks List of smoothed landmarks (33 points)
     * @param currentPhase Current phase from PhaseStateMachine
     * @param difficulty Current difficulty level
     * @return Validation result with errors and warnings
     */
    fun validate(
        landmarks: List<SmoothedLandmark>,
        currentPhase: Phase,
        difficulty: DifficultyType
    ): PositionValidationResult {
        if (landmarks.size < 33 || positionChecks.isEmpty()) {
            return PositionValidationResult.empty()
        }
        
        // 1. Detect camera position and facing
        cachedCameraResult = CameraPositionDetector.detect(landmarks)
        val cameraResult = cachedCameraResult!!
        
        // 2. Check if camera position matches expected
        val cameraWarning = checkCameraPosition(cameraResult)
        
        // 3. Determine effective facing direction
        val effectiveFacing = determineEffectiveFacing(cameraResult)
        
        // 4. Run position checks
        val errors = mutableListOf<PositionError>()
        val warnings = mutableListOf<PositionError>()
        val tips = mutableListOf<PositionError>()
        
        val now = System.currentTimeMillis()
        
        for (check in positionChecks) {
            // Skip if not active in current phase
            if (!isActiveInPhase(check, currentPhase)) {
                // Reset frame count when not active
                errorFrameCounts.remove(check.id)
                continue
            }
            
            // Check cooldown
            val lastErrorTime = lastErrorTimes[check.id] ?: 0L
            if (now - lastErrorTime < check.cooldownMs) {
                continue
            }
            
            // Validate the check
            val result = validateCheck(check, landmarks, difficulty, cameraResult, effectiveFacing)
            
            if (!result.passed) {
                // Increment frame count for stability
                val frameCount = (errorFrameCounts[check.id] ?: 0) + 1
                errorFrameCounts[check.id] = frameCount
                
                // Only report error if sustained for MIN_ERROR_FRAMES
                if (frameCount >= MIN_ERROR_FRAMES) {
                    val error = result.error!!
                    lastErrorTimes[check.id] = now
                    errorFrameCounts.remove(check.id)
                    
                    when (check.severity) {
                        CheckSeverity.ERROR -> errors.add(error)
                        CheckSeverity.WARNING -> warnings.add(error)
                        CheckSeverity.TIP -> tips.add(error)
                    }
                }
            } else {
                // Reset frame count on success
                errorFrameCounts.remove(check.id)
            }
        }
        
        return PositionValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            tips = tips,
            cameraWarning = cameraWarning,
            detectedCameraPosition = cameraResult.position,
            detectedFacing = cameraResult.facingDirection
        )
    }
    
    /**
     * Check if camera position matches expected
     */
    private fun checkCameraPosition(
        cameraResult: CameraPositionDetector.CameraDetectionResult
    ): CameraPositionWarning? {
        if (!CameraPositionDetector.matchesExpected(cameraResult.position, expectedCameraPosition)) {
            return CameraPositionWarning(
                expectedPosition = expectedCameraPosition,
                detectedPosition = CameraPositionDetector.toJsonCameraPosition(cameraResult.position),
                confidence = cameraResult.confidence,
                message = LocalizedText(
                    ar = "يُفضل التصوير من ${getPositionNameAr(expectedCameraPosition)} للحصول على نتائج أفضل",
                    en = "For best results, film from ${expectedCameraPosition.replace("_", " ")}"
                )
            )
        }
        return null
    }
    
    /**
     * Determine effective facing direction
     */
    private fun determineEffectiveFacing(
        cameraResult: CameraPositionDetector.CameraDetectionResult
    ): CameraPositionDetector.DetectedFacing {
        // If config specifies a direction and it's not AUTO_DETECT, use config
        return when (expectedFacingDirection) {
            FacingDirection.FACING_RIGHT -> CameraPositionDetector.DetectedFacing.FACING_RIGHT
            FacingDirection.FACING_LEFT -> CameraPositionDetector.DetectedFacing.FACING_LEFT
            FacingDirection.FACING_CAMERA -> CameraPositionDetector.DetectedFacing.FACING_CAMERA
            FacingDirection.FACING_AWAY -> CameraPositionDetector.DetectedFacing.FACING_AWAY
            FacingDirection.AUTO_DETECT, null -> cameraResult.facingDirection
        }
    }
    
    /**
     * Check if a position check is active in current phase
     */
    private fun isActiveInPhase(check: PositionCheck, phase: Phase): Boolean {
        val phaseName = phase.name.lowercase()
        return check.activePhases.any { it.lowercase() == phaseName }
    }
    
    /**
     * Validate a single position check
     */
    private fun validateCheck(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        difficulty: DifficultyType,
        cameraResult: CameraPositionDetector.CameraDetectionResult,
        facing: CameraPositionDetector.DetectedFacing
    ): CheckResult {
        // Get landmarks
        val primary = getLandmark(check.landmarks.primary, landmarks) 
            ?: return CheckResult.skipped("Primary landmark not visible")
        val secondary = getLandmark(check.landmarks.secondary, landmarks)
            ?: return CheckResult.skipped("Secondary landmark not visible")
        
        // Check visibility
        if (!primary.isVisible(visibilityThreshold) || !secondary.isVisible(visibilityThreshold)) {
            return CheckResult.skipped("Landmarks not visible")
        }
        
        // Get threshold for current difficulty
        val threshold = getThreshold(check.condition.thresholds, difficulty)
        
        // Validate based on check type
        return when (check.type) {
            PositionCheckType.FORWARD_COMPARISON -> 
                validateForwardComparison(check, primary, secondary, threshold, cameraResult, facing)
            
            PositionCheckType.VERTICAL_COMPARISON -> 
                validateVerticalComparison(check, primary, secondary, threshold)
            
            PositionCheckType.SIDEWAYS_COMPARISON -> 
                validateSidewaysComparison(check, primary, secondary, threshold, cameraResult)
            
            PositionCheckType.DISTANCE_RATIO -> 
                validateDistanceRatio(check, landmarks, threshold)
            
            PositionCheckType.HORIZONTAL_ALIGNMENT -> 
                validateHorizontalAlignment(check, landmarks, threshold)
            
            PositionCheckType.VERTICAL_ALIGNMENT -> 
                validateVerticalAlignment(check, landmarks, threshold)
            
            PositionCheckType.DEPTH_ALIGNMENT -> 
                validateDepthAlignment(check, primary, secondary, threshold)
        }
    }
    
    /**
     * Validate forward comparison (knee-over-toe, etc.)
     * 
     * FORWARD axis changes based on camera position:
     * - side_view: X axis (horizontal on screen)
     * - front_view/back_view: Z axis (depth)
     * 
     * Facing direction affects comparison direction:
     * - facing_right: primary.x should be <= secondary.x (for knee-over-toe)
     * - facing_left: primary.x should be >= secondary.x
     */
    private fun validateForwardComparison(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double,
        cameraResult: CameraPositionDetector.CameraDetectionResult,
        facing: CameraPositionDetector.DetectedFacing
    ): CheckResult {
        val (primaryValue, secondaryValue) = getForwardAxisValues(
            primary, secondary, cameraResult.position, facing
        )
        
        val diff = primaryValue - secondaryValue
        
        val passed = when (check.condition.operator) {
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_EXCEED -> diff >= -threshold - HYSTERESIS_BUFFER
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold + HYSTERESIS_BUFFER
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, diff, threshold))
        }
    }
    
    /**
     * Get forward axis values adjusted for camera position and facing
     */
    private fun getForwardAxisValues(
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        cameraPosition: CameraPositionDetector.DetectedCameraPosition,
        facing: CameraPositionDetector.DetectedFacing
    ): Pair<Float, Float> {
        return when (cameraPosition) {
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT,
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
                // In side view, FORWARD is X axis
                // But direction depends on facing
                when (facing) {
                    CameraPositionDetector.DetectedFacing.FACING_RIGHT -> 
                        Pair(primary.x, secondary.x)
                    CameraPositionDetector.DetectedFacing.FACING_LEFT -> 
                        Pair(-primary.x, -secondary.x)  // Flip for opposite facing
                    else -> 
                        Pair(primary.x, secondary.x)
                }
            }
            
            CameraPositionDetector.DetectedCameraPosition.FRONT_VIEW,
            CameraPositionDetector.DetectedCameraPosition.BACK_VIEW -> {
                // In front/back view, FORWARD is Z axis (depth)
                // Smaller Z = closer to camera = more forward
                Pair(-primary.z, -secondary.z)  // Negate so smaller is "more forward"
            }
            
            else -> Pair(primary.x, secondary.x)  // Fallback to X
        }
    }
    
    /**
     * Validate vertical comparison (Y axis - works in all views)
     */
    private fun validateVerticalComparison(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double
    ): CheckResult {
        // Y axis: smaller = higher on screen
        val diff = primary.y - secondary.y
        
        val passed = when (check.condition.operator) {
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_EXCEED -> diff >= -threshold - HYSTERESIS_BUFFER
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold + HYSTERESIS_BUFFER
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, diff.toDouble(), threshold))
        }
    }
    
    /**
     * Validate sideways comparison
     */
    private fun validateSidewaysComparison(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double,
        cameraResult: CameraPositionDetector.CameraDetectionResult
    ): CheckResult {
        val (primaryValue, secondaryValue) = when (cameraResult.position) {
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT,
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
                // In side view, SIDEWAYS is Z axis
                Pair(primary.z, secondary.z)
            }
            else -> {
                // In front/back view, SIDEWAYS is X axis
                Pair(primary.x, secondary.x)
            }
        }
        
        val diff = primaryValue - secondaryValue
        
        val passed = when (check.condition.operator) {
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold + HYSTERESIS_BUFFER
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, diff.toDouble(), threshold))
        }
    }
    
    /**
     * Validate distance ratio between two pairs of landmarks
     */
    private fun validateDistanceRatio(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped()
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped()
        val l3 = getLandmark(check.landmarks.tertiary ?: return CheckResult.skipped(), landmarks) 
            ?: return CheckResult.skipped()
        val l4 = getLandmark(check.landmarks.quaternary ?: return CheckResult.skipped(), landmarks) 
            ?: return CheckResult.skipped()
        
        // Check visibility
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold) ||
            !l3.isVisible(visibilityThreshold) || !l4.isVisible(visibilityThreshold)) {
            return CheckResult.skipped()
        }
        
        // Calculate distances
        val distance1 = calculate3DDistance(l1, l2)
        val distance2 = calculate3DDistance(l3, l4)
        
        if (distance2 < 0.001f) return CheckResult.skipped()  // Avoid division by zero
        
        val ratio = distance1 / distance2
        
        val passed = when (check.condition.operator) {
            PositionOperator.GREATER_THAN_RATIO -> ratio >= threshold - HYSTERESIS_BUFFER
            PositionOperator.LESS_THAN_RATIO -> ratio <= threshold + HYSTERESIS_BUFFER
            PositionOperator.RATIO_BETWEEN -> true  // Would need min/max in condition
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, ratio, threshold))
        }
    }
    
    /**
     * Validate horizontal alignment (3 points on same Y level)
     */
    private fun validateHorizontalAlignment(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped()
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped()
        val l3 = getLandmark(check.landmarks.tertiary ?: return CheckResult.skipped(), landmarks) 
            ?: return CheckResult.skipped()
        
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold) ||
            !l3.isVisible(visibilityThreshold)) {
            return CheckResult.skipped()
        }
        
        // Check if all Y values are within threshold
        val yValues = listOf(l1.y, l2.y, l3.y)
        val maxY = yValues.maxOrNull() ?: return CheckResult.skipped()
        val minY = yValues.minOrNull() ?: return CheckResult.skipped()
        val range = maxY - minY
        
        val passed = range <= threshold + HYSTERESIS_BUFFER
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, range.toDouble(), threshold))
        }
    }
    
    /**
     * Validate vertical alignment (3 points on same X level)
     */
    private fun validateVerticalAlignment(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped()
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped()
        val l3 = check.landmarks.tertiary?.let { getLandmark(it, landmarks) }
        
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold)) {
            return CheckResult.skipped()
        }
        
        val xValues = mutableListOf(l1.x, l2.x)
        if (l3 != null && l3.isVisible(visibilityThreshold)) {
            xValues.add(l3.x)
        }
        
        val maxX = xValues.maxOrNull() ?: return CheckResult.skipped()
        val minX = xValues.minOrNull() ?: return CheckResult.skipped()
        val range = maxX - minX
        
        val passed = range <= threshold + HYSTERESIS_BUFFER
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, range.toDouble(), threshold))
        }
    }
    
    /**
     * Validate depth alignment (landmarks at same Z level)
     */
    private fun validateDepthAlignment(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double
    ): CheckResult {
        val zDiff = abs(primary.z - secondary.z)
        val passed = zDiff <= threshold + HYSTERESIS_BUFFER
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, zDiff.toDouble(), threshold))
        }
    }
    
    // ==================== Helper Methods ====================
    
    private fun getLandmark(name: String, landmarks: List<SmoothedLandmark>): SmoothedLandmark? {
        val index = LANDMARK_INDEX_MAP[name] ?: return null
        return landmarks.getOrNull(index)
    }
    
    private fun getThreshold(thresholds: DifficultyThresholds, difficulty: DifficultyType): Double {
        return when (difficulty) {
            DifficultyType.BEGINNER -> thresholds.beginner
            DifficultyType.NORMAL -> thresholds.normal
            DifficultyType.ADVANCED -> thresholds.advanced
        }
    }
    
    private fun calculate3DDistance(l1: SmoothedLandmark, l2: SmoothedLandmark): Float {
        val dx = l1.x - l2.x
        val dy = l1.y - l2.y
        val dz = l1.z - l2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    private fun createError(check: PositionCheck, actualValue: Double, threshold: Double): PositionError {
        return PositionError(
            checkId = check.id,
            type = check.type,
            severity = check.severity,
            message = check.errorMessage.exceeded,
            actualValue = actualValue,
            threshold = threshold
        )
    }
    
    private fun getPositionNameAr(position: String): String {
        return when (position) {
            "side_view" -> "الجانب"
            "front_view" -> "الأمام"
            "back_view" -> "الخلف"
            else -> position
        }
    }
    
    /**
     * Clear cooldowns (call when planned workout resets)
     */
    fun clearCooldowns() {
        lastErrorTimes.clear()
        errorFrameCounts.clear()
    }
    
    /**
     * Get last detected camera result
     */
    fun getLastCameraResult(): CameraPositionDetector.CameraDetectionResult? = cachedCameraResult
}

// ==================== Result Types ====================

/**
 * Result of position validation
 */
data class PositionValidationResult(
    val isValid: Boolean,
    val errors: List<PositionError>,          // Severity: ERROR - affects rep
    val warnings: List<PositionError>,        // Severity: WARNING - form feedback
    val tips: List<PositionError>,            // Severity: TIP - improvement suggestions
    val cameraWarning: CameraPositionWarning?,
    val detectedCameraPosition: CameraPositionDetector.DetectedCameraPosition,
    val detectedFacing: CameraPositionDetector.DetectedFacing
) {
    companion object {
        fun empty() = PositionValidationResult(
            true, emptyList(), emptyList(), emptyList(), null,
            CameraPositionDetector.DetectedCameraPosition.UNKNOWN,
            CameraPositionDetector.DetectedFacing.UNKNOWN
        )
    }
    
    /**
     * Get all issues (errors + warnings + tips)
     */
    fun getAllIssues(): List<PositionError> = errors + warnings + tips
}

/**
 * Position error details
 */
data class PositionError(
    val checkId: String,
    val type: PositionCheckType,
    val severity: CheckSeverity,
    val message: LocalizedText,
    val actualValue: Double,
    val threshold: Double
)

/**
 * Camera position warning
 */
data class CameraPositionWarning(
    val expectedPosition: String,
    val detectedPosition: String,
    val confidence: Float,
    val message: LocalizedText
)

/**
 * Internal check result
 */
internal data class CheckResult(
    val passed: Boolean,
    val error: PositionError? = null,
    val skipReason: String? = null
) {
    companion object {
        fun passed() = CheckResult(true)
        fun failed(error: PositionError) = CheckResult(false, error)
        fun skipped(reason: String = "") = CheckResult(true, skipReason = reason)
    }
}
```

---

## 🔄 3. تعديل TrainingEngine.kt

### 3.1 التغييرات المطلوبة

```kotlin
// ==================== في أعلى الملف - Imports ====================
import com.trainingvalidator.poc.analysis.SmoothedLandmark

// ==================== في الـ Components section ====================

// إضافة PositionValidator
private val positionValidator: PositionValidator? = 
    poseVariant.positionChecks.takeIf { it.isNotEmpty() }?.let {
        PositionValidator(
            positionChecks = it,
            expectedCameraPosition = poseVariant.cameraPosition,
            expectedFacingDirection = poseVariant.expectedFacingDirection
        )
    }

// ==================== في الـ State Flows section ====================

// إضافة position validation state
private val _positionErrors = MutableStateFlow<List<PositionError>>(emptyList())
val positionErrors: StateFlow<List<PositionError>> = _positionErrors

private val _cameraWarning = MutableStateFlow<CameraPositionWarning?>(null)
val cameraWarning: StateFlow<CameraPositionWarning?> = _cameraWarning

// ==================== تعديل processFrame ====================

/**
 * Process a frame with joint angles AND landmarks
 * 
 * UPDATED: Now accepts landmarks for position-based validation
 * 
 * @param angles JointAngles from AngleCalculator
 * @param landmarks Optional smoothed landmarks for position checks
 */
fun processFrame(angles: JointAngles, landmarks: List<SmoothedLandmark>? = null) {
    if (!isRunning || isPaused) return
    
    // 1. Extract tracked joint angles
    val trackedAngles = jointTracker.extractTrackedAngles(angles)
    _currentAngles.value = trackedAngles
    
    if (trackedAngles.isEmpty()) {
        return
    }
    
    // 2. Extract primary joint angles for state machine
    val primaryAngles = jointTracker.extractPrimaryAngles(angles)
    
    // 3. Check if in start position
    val inStartPos = formValidator.isInStartPosition(trackedAngles)
    _isInStartPosition.value = inStartPos
    
    // 4. Update state machine
    val currentPhase = stateMachine.update(primaryAngles)
    _currentPhase.value = currentPhase
    
    // 5. Validate form (angles)
    val formValidation = formValidator.validate(trackedAngles, currentPhase)
    lastValidationResult = formValidation
    _jointStatuses.value = formValidation.jointStatuses
    
    // 6. Update arrow infos for visual feedback
    _arrowInfos.value = formValidator.getJointArrowInfos(trackedAngles)
    
    // 7. Position validation (NEW)
    val positionValidation = if (landmarks != null && positionValidator != null) {
        positionValidator.validate(landmarks, currentPhase, difficulty)
    } else null
    
    // Update position-related state
    positionValidation?.let {
        _positionErrors.value = it.errors + it.warnings
        _cameraWarning.value = it.cameraWarning
    }
    
    // 8. Handle form errors (add to current rep)
    for (error in formValidation.errors) {
        if (!isHoldExercise) {
            repCounter.addError(error)
        }
        emitEvent(FeedbackEvent.JointErrorDetected(error))
    }
    
    // 9. Handle position errors (NEW)
    positionValidation?.errors?.forEach { error ->
        // Only ERROR severity affects rep (not WARNING or TIP)
        if (!isHoldExercise) {
            repCounter.addPositionError(error)
        }
        emitEvent(FeedbackEvent.PositionErrorDetected(error))
    }
    
    positionValidation?.warnings?.forEach { error ->
        emitEvent(FeedbackEvent.PositionWarningDetected(error))
    }
    
    positionValidation?.cameraWarning?.let { warning ->
        emitEvent(FeedbackEvent.CameraPositionWarning(warning))
    }
    
    // 10. Handle based on counting method
    if (isHoldExercise) {
        val isInHoldZone = (currentPhase == Phase.COUNT)
        updateHoldTimer(isInHoldZone)
    } else {
        if (pendingRepCompletion) {
            pendingRepCompletion = false
            handleRepCompleted()
        }
    }
}

// ==================== تعديل start() ====================

fun start() {
    // ... existing code ...
    
    // Reset position validator cooldowns
    positionValidator?.clearCooldowns()
    _positionErrors.value = emptyList()
    _cameraWarning.value = null
    
    // ... rest of existing code ...
}
```

---

## 📣 4. تعديل FeedbackEvent.kt

```kotlin
// إضافة في sealed class FeedbackEvent

// ==================== Position-based Events ====================

/**
 * Position error detected (severity: ERROR - affects rep)
 */
data class PositionErrorDetected(
    val error: PositionError,
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.HIGH
) : FeedbackEvent()

/**
 * Position warning detected (severity: WARNING - form feedback only)
 */
data class PositionWarningDetected(
    val error: PositionError,
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
) : FeedbackEvent()

/**
 * Position tip (severity: TIP - improvement suggestion)
 */
data class PositionTipDetected(
    val error: PositionError,
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.LOW
) : FeedbackEvent()

/**
 * Camera position warning
 */
data class CameraPositionWarning(
    val warning: com.trainingvalidator.poc.training.engine.CameraPositionWarning,
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
) : FeedbackEvent()
```

---

## 📊 5. تعديل RepCounter.kt

```kotlin
// إضافة في RepCounter

// Position errors للـ rep الحالي
private val currentPositionErrors = mutableListOf<PositionError>()

/**
 * Add a position error to current rep
 * Only ERROR severity should be added (not WARNING or TIP)
 */
fun addPositionError(error: PositionError) {
    // Avoid duplicates
    val exists = currentPositionErrors.any { it.checkId == error.checkId }
    if (!exists) {
        currentPositionErrors.add(error)
    }
}

// تعديل completeRep()
fun completeRep() {
    // ... existing code ...
    
    // Determine if rep was correct (no high-priority errors from angles OR positions)
    val isCorrect = currentRepErrors.isEmpty() && currentPositionErrors.isEmpty()
    
    // Create rep result with position errors
    val result = RepResult(
        repNumber = count,
        isCorrect = isCorrect,
        errors = currentRepErrors.toList(),
        positionErrors = currentPositionErrors.toList(),  // NEW
        phaseTimings = currentPhaseTimings
    )
    
    // ... rest of existing code ...
    
    // Clear position errors too
    currentPositionErrors.clear()
}

// تعديل reset()
fun reset() {
    // ... existing code ...
    currentPositionErrors.clear()
}
```

---

## 🔄 6. تعديل TrainingActivity.kt

```kotlin
// في onPoseDetected()

override fun onPoseDetected(result: PoseResult) {
    lifecycleScope.launch(Dispatchers.Main) {
        updateFps()
        
        // Smooth landmarks
        val smoothedLandmarks = landmarkSmoother.smooth(
            result.landmarks,
            result.timestampMs
        )
        
        // Get world landmarks for 3D angle calculation
        val worldLandmarks = result.worldLandmarks?.let {
            landmarkSmoother.convertWorld(it)
        }
        
        // Calculate angles
        val angles = if (worldLandmarks != null) {
            AngleCalculator.calculateAllAnglesSmoothed(
                worldLandmarks, 
                visibilityThreshold = 0.3f,
                use3D = true
            )
        } else {
            AngleCalculator.calculateAllAnglesSmoothed(
                smoothedLandmarks,
                visibilityThreshold = 0.3f
            )
        }
        
        currentAngles = angles
        
        // Handle based on current state
        when (trainingState) {
            TrainingState.SETUP_POSE -> {
                // ... existing code ...
            }
            
            TrainingState.COUNTDOWN -> {
                // ... existing code ...
            }
            
            TrainingState.TRAINING -> {
                // UPDATED: Pass both angles AND landmarks
                // Use worldLandmarks if available (more stable), else smoothedLandmarks
                val landmarksForValidation = worldLandmarks ?: smoothedLandmarks
                trainingEngine?.processFrame(angles, landmarksForValidation)
            }
            
            else -> {}
        }
        
        // Get arrow infos for visual feedback
        val arrowInfos = trainingEngine?.arrowInfos?.value ?: emptyMap()
        
        // Get position errors for visual feedback (NEW)
        val positionErrors = trainingEngine?.positionErrors?.value ?: emptyList()
        
        // Update skeleton overlay with arrow infos AND position errors
        binding.skeletonOverlay.updateWithArrowInfos(
            smoothedLandmarks = smoothedLandmarks,
            inputImageWidth = result.imageWidth,
            inputImageHeight = result.imageHeight,
            angles = angles,
            arrowInfos = arrowInfos,
            positionErrors = positionErrors  // NEW parameter
        )
    }
}

// إضافة في observeTrainingState()
private fun observeTrainingState(engine: TrainingEngine) {
    // ... existing observations ...
    
    // Observe camera warning (NEW)
    lifecycleScope.launch {
        engine.cameraWarning.collectLatest { warning ->
            if (warning != null) {
                // Show camera position warning (e.g., toast or banner)
                showCameraWarning(warning)
            }
        }
    }
}

private fun showCameraWarning(warning: CameraPositionWarning) {
    // Show once and don't spam
    // Could use a Snackbar or a banner at top of screen
    binding.tvCameraWarning?.apply {
        text = warning.message.en
        visibility = View.VISIBLE
        postDelayed({ visibility = View.GONE }, 5000)
    }
}

// تعديل handleFeedbackEvent()
private fun handleFeedbackEvent(event: FeedbackEvent) {
    when (event) {
        // ... existing cases ...
        
        is FeedbackEvent.PositionErrorDetected -> {
            // Visual feedback handled by SkeletonOverlayView
            Log.d(TAG, "Position error: ${event.error.checkId}")
        }
        
        is FeedbackEvent.PositionWarningDetected -> {
            Log.d(TAG, "Position warning: ${event.error.checkId}")
        }
        
        is FeedbackEvent.CameraPositionWarning -> {
            Log.d(TAG, "Camera warning: ${event.warning.message.en}")
        }
        
        else -> {}
    }
}
```

---

## 📁 7. هيكل الملفات النهائي

```
training/
├── TrainingEngine.kt              ← تعديل (processFrame signature + position integration)
├── engine/
│   ├── FormValidator.kt           ← بدون تغيير
│   ├── HoldTimer.kt               ← بدون تغيير
│   ├── JointAngleTracker.kt       ← بدون تغيير
│   ├── PhaseStateMachine.kt       ← بدون تغيير
│   ├── RepCounter.kt              ← تعديل (addPositionError)
│   ├── PositionValidator.kt       ← جديد ⭐
│   └── CameraPositionDetector.kt  ← جديد ⭐
├── models/
│   ├── ExerciseConfig.kt          ← تعديل (PositionCheck models)
│   └── WorkoutExecution.kt         ← تعديل (RepResult with positionErrors)
├── feedback/
│   ├── FeedbackEvent.kt           ← تعديل (Position events)
│   └── FeedbackManager.kt         ← تعديل (handle position events)
├── loader/
│   └── ExerciseLoader.kt          ← بدون تغيير (Gson handles new fields)
└── config/
    ├── AppSettings.kt             ← بدون تغيير
    └── SettingsManager.kt         ← بدون تغيير
```

---

## 📋 8. مثال JSON كامل للـ Squat

```json
{
  "name": {
    "ar": "القرفصاء",
    "en": "Squat"
  },
  "description": {
    "ar": "تمرين لتقوية عضلات الأرجل والمؤخرة",
    "en": "Exercise to strengthen legs and glutes"
  },
  "category": {
    "code": "legs",
    "name": { "ar": "تمارين الأرجل", "en": "Legs" }
  },
  "countingMethod": "up_down",
  "muscles": ["quadriceps", "glutes", "hamstrings"],
  "equipment": ["bodyweight"],
  "tags": [],
  "poseVariants": [
    {
      "name": { "ar": "زاوية جانبية", "en": "Side View" },
      "cameraPosition": "side_view",
      "expectedFacingDirection": "auto_detect",
      "trackedJoints": [
        {
          "joint": "left_knee",
          "role": "primary",
          "startPose": { "min": 160, "max": 180 },
          "upRange": {
            "beginner": { "min": 155, "max": 180 },
            "normal": { "min": 160, "max": 180 },
            "advanced": { "min": 165, "max": 180 }
          },
          "downRange": {
            "beginner": { "min": 70, "max": 110 },
            "normal": { "min": 80, "max": 100 },
            "advanced": { "min": 85, "max": 95 }
          },
          "movingSegment": { "from": "left_knee", "to": "left_hip" },
          "errorMessages": {
            "tooLow": { "ar": "لا تنزل كثيراً", "en": "Don't go too low" },
            "tooHigh": { "ar": "انزل أكثر", "en": "Go lower" }
          },
          "pairedWith": "right_knee"
        },
        {
          "joint": "right_knee",
          "role": "primary",
          "startPose": { "min": 160, "max": 180 },
          "upRange": {
            "beginner": { "min": 155, "max": 180 },
            "normal": { "min": 160, "max": 180 },
            "advanced": { "min": 165, "max": 180 }
          },
          "downRange": {
            "beginner": { "min": 70, "max": 110 },
            "normal": { "min": 80, "max": 100 },
            "advanced": { "min": 85, "max": 95 }
          },
          "movingSegment": { "from": "right_knee", "to": "right_hip" },
          "errorMessages": {
            "tooLow": { "ar": "لا تنزل كثيراً", "en": "Don't go too low" },
            "tooHigh": { "ar": "انزل أكثر", "en": "Go lower" }
          },
          "pairedWith": "left_knee"
        },
        {
          "joint": "left_hip",
          "role": "secondary",
          "startPose": { "min": 160, "max": 180 },
          "upRange": {
            "beginner": { "min": 155, "max": 180 },
            "normal": { "min": 160, "max": 180 },
            "advanced": { "min": 165, "max": 180 }
          },
          "downRange": {
            "beginner": { "min": 60, "max": 100 },
            "normal": { "min": 70, "max": 90 },
            "advanced": { "min": 75, "max": 85 }
          },
          "movingSegment": { "from": "left_hip", "to": "left_shoulder" },
          "errorMessages": {
            "tooLow": { "ar": "لا تدفع الورك للخلف كثيراً", "en": "Don't push hips back too much" },
            "tooHigh": { "ar": "ادفع الورك للخلف أكثر", "en": "Push hips back more" }
          }
        }
      ],
      "positionChecks": [
        {
          "id": "left_knee_over_toe",
          "type": "forward_comparison",
          "landmarks": {
            "primary": "left_knee",
            "secondary": "left_foot_index"
          },
          "condition": {
            "operator": "should_not_exceed",
            "thresholds": {
              "beginner": 0.05,
              "normal": 0.03,
              "advanced": 0.02
            }
          },
          "activePhases": ["down", "bottom", "up"],
          "errorMessage": {
            "ar": "ركبتك تجاوزت أصابع قدميك! ادفع الورك للخلف",
            "en": "Knee past toes! Push your hips back"
          },
          "severity": "warning",
          "cooldownMs": 3000,
          "minErrorFrames": 5
        },
        {
          "id": "right_knee_over_toe",
          "type": "forward_comparison",
          "landmarks": {
            "primary": "right_knee",
            "secondary": "right_foot_index"
          },
          "condition": {
            "operator": "should_not_exceed",
            "thresholds": {
              "beginner": 0.05,
              "normal": 0.03,
              "advanced": 0.02
            }
          },
          "activePhases": ["down", "bottom", "up"],
          "errorMessage": {
            "ar": "ركبتك تجاوزت أصابع قدميك! ادفع الورك للخلف",
            "en": "Knee past toes! Push your hips back"
          },
          "severity": "warning",
          "cooldownMs": 3000,
          "minErrorFrames": 5
        },
        {
          "id": "torso_vertical_alignment",
          "type": "vertical_alignment",
          "landmarks": {
            "primary": "left_shoulder",
            "secondary": "left_hip"
          },
          "condition": {
            "operator": "approximately_equal",
            "thresholds": {
              "beginner": 0.10,
              "normal": 0.08,
              "advanced": 0.06
            }
          },
          "activePhases": ["bottom"],
          "errorMessage": {
            "ar": "الجذع مائل للأمام كثيراً - ارفع صدرك",
            "en": "Leaning forward too much - lift your chest"
          },
          "severity": "warning",
          "cooldownMs": 3000,
          "minErrorFrames": 4
        },
        {
          "id": "feet_width",
          "type": "distance_ratio",
          "landmarks": {
            "primary": "left_ankle",
            "secondary": "right_ankle",
            "tertiary": "left_shoulder",
            "quaternary": "right_shoulder"
          },
          "condition": {
            "operator": "greater_than_ratio",
            "thresholds": {
              "beginner": 0.8,
              "normal": 0.9,
              "advanced": 1.0
            }
          },
          "activePhases": ["start", "down", "bottom", "up"],
          "errorMessage": {
            "ar": "باعد بين قدميك أكثر",
            "en": "Spread your feet wider"
          },
          "severity": "tip",
          "cooldownMs": 5000,
          "minErrorFrames": 6
        }
      ],
      "feedbackMessages": {
        "motivational": [
          { "ar": "ممتاز! استمر!", "en": "Excellent! Keep going!" },
          { "ar": "أداء رائع!", "en": "Great form!" }
        ],
        "common_mistake": [
          { "ar": "لا تدع ركبتيك تتجاوز أصابع قدميك", "en": "Don't let knees go past toes" },
          { "ar": "حافظ على استقامة ظهرك", "en": "Keep your back straight" }
        ],
        "tip": [
          { "ar": "ادفع من كعبيك للصعود", "en": "Push through heels to stand" }
        ]
      },
      "difficultyLevels": [
        {
          "level": "beginner",
          "repCountingConfig": {
            "reps": 10,
            "minRepIntervalMs": 2000,
            "maxRepIntervalMs": 6000
          },
          "phases": ["start", "down", "bottom", "up"]
        },
        {
          "level": "normal",
          "repCountingConfig": {
            "reps": 12,
            "minRepIntervalMs": 1600,
            "maxRepIntervalMs": 5000
          },
          "phases": ["start", "down", "bottom", "up"]
        },
        {
          "level": "advanced",
          "repCountingConfig": {
            "reps": 15,
            "minRepIntervalMs": 1200,
            "maxRepIntervalMs": 4000
          },
          "phases": ["start", "down", "bottom", "up"]
        }
      ]
    }
  ]
}
```

---

## ⏱️ 9. خطوات التنفيذ

| الخطوة | الوصف | الوقت المقدر |
|--------|-------|--------------|
| 1 | إضافة الـ Data Models في `ExerciseConfig.kt` | 45 دقيقة |
| 2 | إنشاء `CameraPositionDetector.kt` | 1.5 ساعة |
| 3 | إنشاء `PositionValidator.kt` | 2.5 ساعة |
| 4 | تعديل `TrainingEngine.kt` للتكامل | 1 ساعة |
| 5 | تعديل `RepCounter.kt` لـ position errors | 30 دقيقة |
| 6 | تعديل `FeedbackEvent.kt` و `FeedbackManager.kt` | 30 دقيقة |
| 7 | تعديل `TrainingActivity.kt` | 45 دقيقة |
| 8 | تعديل `SkeletonOverlayView.kt` لعرض position errors | 1 ساعة |
| 9 | تحديث `squat.json` لإضافة positionChecks | 30 دقيقة |
| 10 | اختبار وتصحيح | 2 ساعة |
| **المجموع** | | **~11 ساعة** |

---

## ✅ 10. مميزات التصميم النهائي

1. **متوافق 100% مع الكود الحالي** - يتبع نفس patterns (DifficultyRanges, Phase enum, etc.)
2. **Camera-Aware** - يكشف تلقائيًا زاوية الكاميرا واتجاه الجسم
3. **Facing-Direction Aware** - يعالج knee-over-toe بشكل صحيح بغض النظر عن اتجاه الجسم
4. **Stability Built-in** - hysteresis + frame counting + cooldowns لمنع الـ flicker
5. **Visibility Gating** - لا يفحص landmarks غير مرئية
6. **Difficulty-Aware** - thresholds مختلفة لكل مستوى
7. **Severity Levels** - ERROR/WARNING/TIP مع تأثير مختلف على الـ scoring
8. **Type-Safe** - لا `Any` types، كل شيء strongly typed
9. **Extensible** - سهل إضافة أنواع checks جديدة
10. **JSON-First** - الـ schema واضح وسهل للـ Backend team

---

## 📝 11. ملاحظات مهمة للتنفيذ

1. **استخدم `worldLandmarks` عندما تكون متاحة** - أكثر استقرارًا من normalized
2. **الـ Z coordinate في front_view أقل دقة** - يُفضل side_view للـ forward checks
3. **ابدأ بـ severity: WARNING** للـ checks الجديدة حتى تتأكد من دقتها
4. **راقب الـ cooldownMs** - 2-3 ثواني مناسب لمنع spam بدون تأخير الـ feedback
5. **MIN_ERROR_FRAMES = 3** يمنع الأخطاء العابرة من الظهور
6. **اختبر مع اتجاهات جسم مختلفة** (facing left vs right) قبل الـ production

---

## 🔮 12. توسعات مستقبلية (خارج نطاق V2)

- **Position History** - تتبع مسار الحركة للكشف عن أنماط خاطئة
- **Velocity Checks** - سرعة الحركة (هل ينزل ببطء كافي؟)
- **Balance Detection** - هل الوزن موزع بالتساوي؟
- **Multi-Frame Analysis** - تحليل الحركة عبر عدة frames
- **ML-based Error Detection** - نموذج مدرب على الأخطاء الشائعة

