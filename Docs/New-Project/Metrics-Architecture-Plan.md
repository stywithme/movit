# Metrics Architecture Plan

## خطة إعادة هيكلة نظام المقاييس

---

## 1. الرؤية الشاملة

### المشكلة الحالية
- الـ Score يعتمد فقط على "أسوأ joint" = نتائج غير عادلة (0% دائماً)
- المقاييس محسوبة بشكل تقريبي وليس من البيانات الفعلية
- لا يوجد تكامل منطقي بين المقاييس
- لا يوجد مقياس شامل يجمع كل شيء

### الهدف
نظام مقاييس متكامل:
- كل مقياس له مصدر بيانات واضح
- كل مقياس له منطق حساب دقيق
- المقاييس مترابطة بشكل منطقي
- مقياس شامل (Overall Quality) يجمع كل شيء

---

## 2. هرمية المقاييس

```
                    ┌─────────────────────┐
                    │   Overall Quality   │
                    │      (0-100%)       │
                    └──────────┬──────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  Form Score   │    │ Safety Score  │    │ Control Score │
│     40%       │    │     35%       │    │     25%       │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │
   ┌────┴────┐          ┌────┴────┐          ┌────┴────┐
   │         │          │         │          │         │
   ▼         ▼          ▼         ▼          ▼         ▼
┌─────┐  ┌─────┐    ┌─────┐  ┌─────┐    ┌─────┐  ┌─────┐
│ ROM │  │ Sym │    │Align│  │Stab │    │Tempo│  │Cons │
└─────┘  └─────┘    └─────┘  └─────┘    └─────┘  └─────┘
```

---

## 3. تفاصيل كل مقياس

### 3.1 Form Score (جودة الشكل) - 40% من الإجمالي

#### المكونات:
| المقياس | الوزن | المصدر | الوصف |
|---------|-------|--------|-------|
| **ROM** | 50% | زوايا المفاصل الفعلية | هل وصل للمدى المطلوب؟ |
| **Symmetry** | 30% | مقارنة يمين/شمال | هل الجانبان متساويان؟ |
| **Joint Quality** | 20% | JointState rates | جودة الزوايا في كل frame |

#### حساب ROM:
```kotlin
// لكل عدة: نقارن الزاوية الفعلية بالـ expected range
fun calculateROM(jointCode: String, angles: List<Float>, config: TrackedJoint): Float {
    val targetRange = config.downPose // أو upPose حسب التمرين
    val minRequired = targetRange.min  // e.g., 80° للـ squat
    val maxRequired = targetRange.max  // e.g., 100°
    
    val achievedMin = angles.minOrNull() ?: return 0f
    
    // ROM Score = how close to target range
    return when {
        achievedMin <= maxRequired -> 100f  // وصل للمطلوب
        achievedMin <= maxRequired + 10 -> 80f  // قريب
        achievedMin <= maxRequired + 20 -> 60f  // متوسط
        else -> 40f  // لم يصل
    }
}
```

#### حساب Symmetry:
```kotlin
// فقط للتمارين bilateral
fun calculateSymmetry(leftAngles: List<Float>, rightAngles: List<Float>): Float {
    val avgDiff = leftAngles.zip(rightAngles) { l, r -> 
        abs(l - r) 
    }.average()
    
    return when {
        avgDiff < 3 -> 100f    // مثالي
        avgDiff < 5 -> 90f     // ممتاز
        avgDiff < 10 -> 75f    // جيد
        avgDiff < 15 -> 60f    // مقبول
        else -> 40f            // يحتاج تحسين
    }
}
```

#### حساب Joint Quality:
```kotlin
// Weighted average من كل الـ joints
fun calculateJointQuality(
    jointStates: Map<String, JointState>,
    config: ExerciseConfig
): Float {
    var weightedSum = 0f
    var totalWeight = 0f
    
    for ((jointCode, state) in jointStates) {
        val isPrimary = config.isPrimaryJoint(jointCode)
        val weight = if (isPrimary) 1.0f else 0.3f
        
        val rate = when (state) {
            PERFECT -> 100f
            NORMAL -> 80f
            PAD -> 60f
            WARNING -> 40f
            DANGER -> 0f
        }
        
        weightedSum += rate * weight
        totalWeight += weight
    }
    
    return weightedSum / totalWeight
}
```

---

### 3.2 Safety Score (السلامة) - 35% من الإجمالي

#### المكونات:
| المقياس | الوزن | المصدر | الوصف |
|---------|-------|--------|-------|
| **Alignment** | 50% | Position checks | المحاذاة الصحيحة |
| **Stability** | 30% | CoM tracking | ثبات الجسم |
| **Danger Events** | 20% | DANGER state count | أحداث الخطر |

#### حساب Alignment:
```kotlin
// من Position checks (إن وجدت)
fun calculateAlignment(
    positionErrors: List<PositionError>,
    totalFrames: Int
): Float {
    val errorFrames = positionErrors.size
    val errorRate = errorFrames.toFloat() / totalFrames
    
    return (1f - errorRate) * 100f
}

// أو من WARNING states
fun calculateAlignmentFromStates(
    warningCount: Int,
    totalCount: Int
): Float {
    return ((totalCount - warningCount).toFloat() / totalCount) * 100f
}
```

#### حساب Stability (CoM):
```kotlin
// تتبع مركز الكتلة
fun calculateStability(
    hipPositions: List<Point>,
    shoulderPositions: List<Point>
): Float {
    // حساب CoM لكل frame
    val comPositions = hipPositions.zip(shoulderPositions) { hip, shoulder ->
        Point((hip.x + shoulder.x) / 2, (hip.y + shoulder.y) / 2)
    }
    
    // حساب الانحراف المعياري
    val avgX = comPositions.map { it.x }.average()
    val avgY = comPositions.map { it.y }.average()
    
    val variance = comPositions.map { 
        (it.x - avgX).pow(2) + (it.y - avgY).pow(2) 
    }.average()
    
    val stdDev = sqrt(variance)
    
    // تحويل لـ score (أقل انحراف = أعلى score)
    return when {
        stdDev < 0.02 -> 100f  // مستقر جداً
        stdDev < 0.04 -> 85f
        stdDev < 0.06 -> 70f
        stdDev < 0.08 -> 55f
        else -> 40f
    }
}
```

#### حساب Danger Events:
```kotlin
fun calculateDangerPenalty(dangerCount: Int, totalReps: Int): Float {
    val dangerRate = dangerCount.toFloat() / totalReps
    
    return when {
        dangerRate == 0f -> 100f
        dangerRate < 0.1 -> 80f
        dangerRate < 0.2 -> 60f
        dangerRate < 0.3 -> 40f
        else -> 20f
    }
}
```

---

### 3.3 Control Score (التحكم) - 25% من الإجمالي

#### المكونات:
| المقياس | الوزن | المصدر | الوصف |
|---------|-------|--------|-------|
| **Tempo** | 40% | Phase durations | سرعة مناسبة؟ |
| **Consistency** | 40% | Score variance | ثبات الأداء |
| **TUT** | 20% | Total time | الوقت تحت الضغط |

#### حساب Tempo:
```kotlin
// هل الإيقاع مناسب؟
fun calculateTempoScore(
    eccentricMs: Int,
    concentricMs: Int,
    isometricMs: Int
): Float {
    // المثالي: 2-1-2 ثانية
    val idealEccentric = 2000
    val idealConcentric = 2000
    val idealIsometric = 1000
    
    val eccentricScore = 100 - abs(eccentricMs - idealEccentric) / 20
    val concentricScore = 100 - abs(concentricMs - idealConcentric) / 20
    val isometricScore = 100 - abs(isometricMs - idealIsometric) / 10
    
    return ((eccentricScore + concentricScore + isometricScore) / 3f)
        .coerceIn(0f, 100f)
}
```

#### حساب Consistency:
```kotlin
// ثبات الأداء عبر العدات
fun calculateConsistency(repScores: List<Float>): Float {
    if (repScores.size < 2) return 100f
    
    val mean = repScores.average()
    val variance = repScores.map { (it - mean).pow(2) }.average()
    val stdDev = sqrt(variance)
    
    // أقل تباين = أعلى consistency
    return (100f - stdDev * 2).coerceIn(0f, 100f)
}
```

#### حساب TUT Score:
```kotlin
// هل الوقت تحت الضغط كافي؟
fun calculateTUTScore(
    totalTUT: Int,
    reps: Int,
    exerciseType: ExerciseType
): Float {
    // المثالي: 40-60 ثانية لمجموعة hypertrophy
    val idealTUT = when (exerciseType) {
        STRENGTH -> 20_000  // 20 seconds
        HYPERTROPHY -> 45_000  // 45 seconds
        ENDURANCE -> 60_000  // 60 seconds
    }
    
    val ratio = totalTUT.toFloat() / idealTUT
    
    return when {
        ratio in 0.8f..1.2f -> 100f
        ratio in 0.6f..1.4f -> 80f
        ratio in 0.4f..1.6f -> 60f
        else -> 40f
    }
}
```

---

## 4. حساب Overall Quality

```kotlin
data class OverallQuality(
    val score: Float,
    val formScore: Float,
    val safetyScore: Float,
    val controlScore: Float,
    val breakdown: QualityBreakdown
)

fun calculateOverallQuality(
    formScore: Float,
    safetyScore: Float,
    controlScore: Float
): OverallQuality {
    // الأوزان
    val formWeight = 0.40f
    val safetyWeight = 0.35f
    val controlWeight = 0.25f
    
    val overall = (
        formScore * formWeight +
        safetyScore * safetyWeight +
        controlScore * controlWeight
    )
    
    return OverallQuality(
        score = overall,
        formScore = formScore,
        safetyScore = safetyScore,
        controlScore = controlScore,
        breakdown = QualityBreakdown(...)
    )
}
```

---

## 5. حساب Rep Score الجديد

```kotlin
// يُحسب في كل frame أثناء التمرين
fun calculateRepScore(
    jointStates: Map<String, JointStateInfo>,
    config: ExerciseConfig,
    positionErrors: List<PositionError>
): Float {
    // 1. Joint Quality (weighted by primary/secondary)
    val jointQuality = calculateJointQuality(jointStates, config)
    
    // 2. Alignment from position checks
    val alignmentScore = if (positionErrors.isEmpty()) 100f 
        else (100f - positionErrors.size * 10f).coerceAtLeast(0f)
    
    // 3. DANGER penalty
    val dangerCount = jointStates.values.count { it.state == DANGER }
    val dangerPenalty = dangerCount * 15f
    
    // 4. Calculate final score
    val baseScore = (jointQuality * 0.7f + alignmentScore * 0.3f)
    
    return (baseScore - dangerPenalty).coerceIn(0f, 100f)
}
```

---

## 6. مصادر البيانات المطلوبة

### بيانات تُجمع أثناء التمرين (Real-time):

| البيانات | المصدر الحالي | مطلوب |
|----------|---------------|-------|
| زوايا المفاصل | ✅ FormValidator | ✅ |
| JointState | ✅ FormValidator | ✅ |
| Position Errors | ✅ FormValidator | ✅ |
| Phase (Up/Down) | ✅ PhaseStateMachine | ✅ |
| Timestamps | ✅ RepCounter | ✅ |
| Left/Right angles | ⚠️ جزئي | مطلوب توسيع |
| Hip/Shoulder positions | ❌ غير متوفر | مطلوب لـ CoM |

### بيانات تُحسب بعد كل Rep:

| البيانات | الحالة | الإجراء |
|----------|--------|---------|
| Rep Score | ⚠️ worst only | إعادة حساب |
| Phase durations | ✅ متوفر | استخدام |
| Max/Min angles | ⚠️ غير مسجل | تسجيل |

### بيانات تُحسب في التقرير:

| المقياس | الحالة | الإجراء |
|---------|--------|---------|
| Form Score | ⚠️ تقريبي | إصلاح |
| ROM | ⚠️ تقريبي | حساب من زوايا فعلية |
| Symmetry | ⚠️ تقريبي | حساب من مقارنة فعلية |
| Stability | ⚠️ من timing | حساب من CoM |
| Tempo | ✅ موجود | تحسين |
| Consistency | ✅ موجود | تحسين |

---

## 7. خطة التنفيذ

### المرحلة 1: إصلاح Rep Score (الآن) 🔴

**الملفات:**
- `RepCounter.kt` - تغيير منطق حساب الـ score
- `FormValidator.kt` - إضافة weighted calculation

**التغييرات:**
```kotlin
// Old:
val config = StateConfig.getConfig(currentRepWorstState)
score = config.rate

// New:
score = calculateWeightedRepScore(jointStateInfos, exerciseConfig)
```

### المرحلة 2: تسجيل بيانات إضافية 🟡

**الملفات:**
- `MotionRecorder.kt` - تسجيل زوايا min/max لكل rep
- `FormValidator.kt` - تتبع left/right angles

**البيانات الجديدة:**
```kotlin
data class RepAngleData(
    val jointCode: String,
    val minAngle: Float,
    val maxAngle: Float,
    val avgAngle: Float
)
```

### المرحلة 3: إصلاح Report Metrics 🟡

**الملفات:**
- `PerformanceMetricsBuilder.kt` - حساب من بيانات فعلية
- `ReportGenerator.kt` - إضافة OverallQuality

**المقاييس:**
- ROM من زوايا فعلية
- Symmetry من مقارنة فعلية
- Form Score من weighted joints

### المرحلة 4: إضافة CoM Stability 🟢

**الملفات:**
- `FormValidator.kt` - إضافة CoM tracking
- `PerformanceMetricsBuilder.kt` - حساب Stability

**ملاحظة:** يحتاج تتبع positions وليس فقط angles

### المرحلة 5: Overall Quality 🟢

**الملفات:**
- `PostTrainingReport.kt` - إضافة overallQuality field
- `ReportGenerator.kt` - حساب Overall
- UI components - عرض Overall

---

## 8. ملخص الأوزان النهائية

```
Overall Quality (100%)
│
├── Form Score (40%)
│   ├── Joint Quality (40%) - weighted primary/secondary
│   ├── ROM (35%) - achieved vs target range
│   └── Symmetry (25%) - bilateral comparison
│
├── Safety Score (35%)
│   ├── Alignment (50%) - position checks
│   ├── Stability (30%) - CoM variance
│   └── Danger Events (20%) - DANGER count penalty
│
└── Control Score (25%)
    ├── Tempo (40%) - phase durations
    ├── Consistency (40%) - score variance
    └── TUT (20%) - time under tension
```

---

## 8.1 المقاييس الإضافية (خارج الـ Overall)

### مقاييس الإيقاع التفصيلية (Tempo Details)

| المقياس | الوصف | كيف يُحسب | مصدر البيانات |
|---------|-------|-----------|---------------|
| **Eccentric** | زمن النزول | من phase transition | PhaseStateMachine |
| **Isometric** | زمن الثبات | وقت في BOTTOM/EXTENDED | PhaseStateMachine |
| **Concentric** | زمن الصعود | من phase transition | PhaseStateMachine |

```kotlin
data class TempoBreakdown(
    val eccentricMs: Int,      // زمن النزول
    val isometricMs: Int,      // زمن الثبات
    val concentricMs: Int,     // زمن الصعود
    val totalRepMs: Int        // إجمالي العدة
) {
    fun getFormattedTempo(): String {
        val ecc = (eccentricMs / 1000f).roundToInt()
        val iso = (isometricMs / 1000f).roundToInt()
        val con = (concentricMs / 1000f).roundToInt()
        return "$ecc-$iso-$con"  // e.g., "2-1-2"
    }
}

// حساب من phase timings
fun calculateTempoBreakdown(phaseTimings: Map<Phase, Long>): TempoBreakdown {
    return TempoBreakdown(
        eccentricMs = (phaseTimings[Phase.GOING_DOWN] ?: 0L).toInt(),
        isometricMs = (phaseTimings[Phase.BOTTOM] ?: 0L).toInt() + 
                      (phaseTimings[Phase.EXTENDED] ?: 0L).toInt(),
        concentricMs = (phaseTimings[Phase.GOING_UP] ?: 0L).toInt(),
        totalRepMs = phaseTimings.values.sum().toInt()
    )
}
```

### مقاييس الحمل (Load Metrics) - للتمارين بأوزان

| المقياس | الوصف | كيف يُحسب | متى يظهر |
|---------|-------|-----------|----------|
| **Weight** | الوزن المستخدم | input من المستخدم | إذا supportsWeight |
| **Volume** | الحجم الكلي | reps × weight | إذا weight > 0 |
| **Est. 1RM** | القوة القصوى | Epley formula | إذا reps ≤ 10 |

```kotlin
data class LoadMetrics(
    val weightKg: Float,
    val weightUnit: String = "kg",
    val totalVolume: Float?,      // reps × weight
    val est1RM: Float?            // estimated 1 rep max
)

// حساب الحجم
fun calculateVolume(reps: Int, weightKg: Float): Float {
    return reps * weightKg
}

// حساب 1RM بـ Epley formula
fun calculateEst1RM(reps: Int, weightKg: Float): Float? {
    if (reps > 10 || reps < 1) return null  // غير دقيق فوق 10 عدات
    return weightKg * (1 + reps / 30f)
}

// مثال: 80kg × 8 reps = 1RM ≈ 101kg
```

### مقياس التعب (Fatigue Index)

| المقياس | الوصف | كيف يُحسب | الفائدة |
|---------|-------|-----------|---------|
| **Fatigue Index** | العدة التي بدأ فيها التعب | score drop detection | متى تتوقف؟ |

```kotlin
// كشف نقطة التعب
fun detectFatigueIndex(repScores: List<Float>): Int? {
    if (repScores.size < 4) return null
    
    // حساب متوسط أول 3 عدات كـ baseline
    val baselineAvg = repScores.take(3).average()
    
    // البحث عن أول عدة انخفضت بأكثر من 15%
    for (i in 3 until repScores.size) {
        if (repScores[i] < baselineAvg - 15) {
            return i + 1  // رقم العدة (1-based)
        }
    }
    
    return null  // لم يُكتشف تعب
}

// مثال: [85, 88, 82, 80, 75, 68, 60]
//        ↑ baseline ≈ 85    ↑ fatigue at rep 6 (68 < 70)
```

---

## 8.2 جدول شامل لكل المقاييس

### مقاييس الأداء (Performance Metrics)

| المقياس | في Overall? | الوزن | المصدر | الحالة |
|---------|-------------|-------|--------|--------|
| Form Score | ✅ | 40% | JointStates | يحتاج إصلاح |
| ROM | ✅ | 35% of Form | زوايا فعلية | يحتاج إصلاح |
| Symmetry | ✅ | 25% of Form | مقارنة L/R | يحتاج إصلاح |
| Stability | ✅ | 30% of Safety | CoM | يحتاج إضافة |
| Alignment | ✅ | 50% of Safety | PositionChecks | موجود |

### مقاييس الإيقاع (Tempo Metrics)

| المقياس | في Overall? | المصدر | الحالة |
|---------|-------------|--------|--------|
| Eccentric | ❌ عرض فقط | PhaseTimings | ✅ موجود |
| Isometric | ❌ عرض فقط | PhaseTimings | ✅ موجود |
| Concentric | ❌ عرض فقط | PhaseTimings | ✅ موجود |
| TUT | ✅ | إجمالي الوقت | ✅ موجود |
| Tempo Score | ✅ | 40% of Control | يحتاج ربط |

### مقاييس الحمل (Load Metrics)

| المقياس | في Overall? | المصدر | الحالة |
|---------|-------------|--------|--------|
| Weight | ❌ عرض فقط | User input | ✅ موجود |
| Volume | ❌ عرض فقط | Calculated | ✅ موجود |
| Est. 1RM | ❌ عرض فقط | Epley formula | ✅ موجود |

### مقاييس الجودة (Quality Metrics)

| المقياس | في Overall? | المصدر | الحالة |
|---------|-------------|--------|--------|
| Form Consistency | ✅ | Score variance | ✅ موجود |
| Fatigue Index | ❌ عرض فقط | Score drop | ✅ موجود |
| Danger Count | ✅ | DANGER states | ✅ موجود |

---

## 8.3 التعامل مع أنواع التمارين المختلفة

### أنواع التمارين

| النوع | مثال | خصائص |
|-------|------|--------|
| **Rep-based** | Squat, Bicep Curl | عدات، phases، tempo |
| **Hold-based** | Plank, Wall Sit | مدة ثبات، لا يوجد reps |
| **Unilateral** | Single Leg Squat | جانب واحد فقط |
| **Bilateral** | Regular Squat | جانبين متماثلين |
| **Weighted** | Deadlift | يدعم إضافة وزن |
| **Bodyweight** | Push-up | بدون وزن خارجي |

---

### جدول المقاييس حسب نوع التمرين

| المقياس | Rep-based | Hold-based | Bilateral | Unilateral | Weighted | Bodyweight |
|---------|-----------|------------|-----------|------------|----------|------------|
| **Form Score** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **ROM** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Symmetry** | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Alignment** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Stability** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Eccentric** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Isometric** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Concentric** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **TUT** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Hold Duration** | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ |
| **Weight** | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **Volume** | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **Est. 1RM** | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **Consistency** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Fatigue Index** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Danger Count** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

---

### حساب Overall Quality لتمارين Hold

```kotlin
// تمارين Hold لها هيكل مختلف
fun calculateOverallQualityForHold(
    formScore: Float,      // من JointStates أثناء الثبات
    safetyScore: Float,    // Alignment + Stability + Danger
    holdQualityScore: Float // مدة × جودة الثبات
): OverallQuality {
    
    // الأوزان مختلفة لـ Hold
    val formWeight = 0.35f
    val safetyWeight = 0.40f      // أهم في Hold
    val holdWeight = 0.25f
    
    val overall = (
        formScore * formWeight +
        safetyScore * safetyWeight +
        holdQualityScore * holdWeight
    )
    
    return OverallQuality(
        score = overall,
        formScore = formScore,
        safetyScore = safetyScore,
        controlScore = holdQualityScore,  // Hold quality بدلاً من Tempo
        breakdown = ...
    )
}

// Hold Quality Score
fun calculateHoldQualityScore(
    targetDurationMs: Long,
    achievedDurationMs: Long,
    formQualityDuringHold: Float,  // متوسط quality أثناء الثبات
    gracePeriodsUsed: Int
): Float {
    // 1. Duration achievement
    val durationRatio = (achievedDurationMs.toFloat() / targetDurationMs)
        .coerceAtMost(1.0f)
    val durationScore = durationRatio * 100
    
    // 2. Form quality during hold (already 0-100)
    val formScore = formQualityDuringHold
    
    // 3. Penalty for grace periods
    val gracePenalty = gracePeriodsUsed * 5f
    
    // Combined score
    val rawScore = (durationScore * 0.4f + formScore * 0.6f)
    
    return (rawScore - gracePenalty).coerceIn(0f, 100f)
}
```

---

### Logic للتحقق من توفر المقياس

```kotlin
// الكود الموجود حالياً (ExerciseConfig.kt)
fun shouldShowMetric(metric: MetricCode, variantIndex: Int = 0): Boolean {
    val config = getEffectiveMetricsConfig(variantIndex)
    
    return when (metric) {
        // Symmetry: فقط للتمارين bilateral
        MetricCode.SYMMETRY -> isBilateralExercise(variantIndex) && config.shouldShow(metric)
        
        // Tempo/TUT: فقط للتمارين rep-based
        MetricCode.TEMPO, MetricCode.TUT -> !isHoldExercise() && config.shouldShow(metric)
        
        // Hold Duration: فقط للتمارين hold-based
        MetricCode.HOLD_DURATION -> isHoldExercise() && config.shouldShow(metric)
        
        // Load metrics: فقط إذا يدعم الوزن
        MetricCode.WEIGHT, MetricCode.VOLUME, MetricCode.EST_1RM -> 
            supportsWeight && config.shouldShow(metric)
        
        // باقي المقاييس: حسب الـ config
        else -> config.shouldShow(metric)
    }
}
```

---

### تعديل حساب Overall Quality ليتعامل مع الأنواع

```kotlin
fun calculateOverallQuality(report: PostTrainingReport): OverallQuality {
    val config = report.exerciseConfig
    val isHold = config?.isHoldExercise() == true
    val isBilateral = config?.isBilateral == true
    
    if (isHold) {
        return calculateOverallQualityForHold(report)
    }
    
    // Rep-based exercise
    return calculateOverallQualityForReps(report, isBilateral)
}

fun calculateOverallQualityForReps(
    report: PostTrainingReport,
    isBilateral: Boolean
): OverallQuality {
    
    // 1. Form Score
    val jointQuality = calculateJointQuality(report)
    val romScore = calculateROM(report)
    val symmetryScore = if (isBilateral) calculateSymmetry(report) else null
    
    val formScore = if (symmetryScore != null) {
        jointQuality * 0.40f + romScore * 0.35f + symmetryScore * 0.25f
    } else {
        // بدون Symmetry - توزيع الأوزان مختلف
        jointQuality * 0.55f + romScore * 0.45f
    }
    
    // 2. Safety Score (نفسه لكل الأنواع)
    val alignmentScore = calculateAlignment(report)
    val stabilityScore = calculateStability(report)
    val dangerScore = calculateDangerPenalty(report)
    
    val safetyScore = alignmentScore * 0.50f + 
                      stabilityScore * 0.30f + 
                      dangerScore * 0.20f
    
    // 3. Control Score
    val tempoScore = calculateTempoScore(report)
    val consistencyScore = calculateConsistency(report)
    val tutScore = calculateTUTScore(report)
    
    val controlScore = tempoScore * 0.40f + 
                       consistencyScore * 0.40f + 
                       tutScore * 0.20f
    
    // 4. Overall
    val overall = formScore * 0.40f + 
                  safetyScore * 0.35f + 
                  controlScore * 0.25f
    
    return OverallQuality(
        score = overall,
        formScore = formScore,
        safetyScore = safetyScore,
        controlScore = controlScore,
        ...
    )
}
```

---

### مقاييس خاصة بـ Hold Exercises

| المقياس | الوصف | كيف يُحسب |
|---------|-------|-----------|
| **Hold Duration** | المدة المحققة | achievedMs / targetMs |
| **Hold Quality** | جودة الثبات | weighted avg of states during hold |
| **Grace Periods** | عدد مرات الخروج والعودة | count |
| **Time in Perfect** | وقت في PERFECT state | percentage |

```kotlin
// بيانات Hold الخاصة
data class HoldMetrics(
    val targetDurationMs: Long,
    val achievedDurationMs: Long,
    val holdQuality: Float,           // 0-100
    val gracePeriodsUsed: Int,
    val timeInPerfect: Float,         // percentage
    val timeInNormal: Float,
    val timeInWarning: Float,
    val formBreakdownPoint: Long?     // متى انهار الشكل؟
) {
    fun getAchievementRatio(): Float {
        return (achievedDurationMs.toFloat() / targetDurationMs)
            .coerceAtMost(1.0f)
    }
    
    fun getFormattedDuration(): String {
        val seconds = achievedDurationMs / 1000
        return "${seconds}s"
    }
}
```

---

## 9. التوافق مع Backend

الـ Backend يحدد:
- `reportMetrics.primary` - المقاييس الأساسية للعرض
- `reportMetrics.optional` - المقاييس الإضافية
- `reportMetrics.excluded` - المقاييس المخفية

**ملاحظة:** هذا للعرض فقط. الحساب الداخلي يشمل كل المقاييس دائماً.

---

## 10. الخلاصة

| المقياس | الحالة الحالية | بعد الإصلاح |
|---------|---------------|-------------|
| Rep Score | 0% (worst only) | 40-80% (weighted) |
| Form Score | تقريبي | من بيانات فعلية |
| ROM | تقدير | من زوايا فعلية |
| Symmetry | من errors | من مقارنة زوايا |
| Alignment | من warnings | من position checks |
| Stability | من timing | من CoM tracking |
| Overall | غير موجود | موجود ومحسوب |

