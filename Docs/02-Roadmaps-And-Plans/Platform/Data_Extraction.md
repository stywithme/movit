# هيكلة استخراج وتحليل بيانات الأداء الرياضي (Enhanced Data Extraction Architecture)

هذه الوثيقة تصف البنية التقنية لجمع، تحليل، وحفظ بيانات الأداء الرياضي في مشروع `TrainingValidator`. الهدف هو تحويل التطبيق من "عداد عدات" إلى "منصة تحليل أداء" احترافية.

**المبادئ الأساسية:**
- **لا تكرار**: نستخدم البيانات المحسوبة أثناء التمرين، لا نعيد حسابها.
- **خفة الوزن**: مصفوفات بدون مسميات مكررة، IDs بدل النصوص.
- **تدفق ذكي**: ذاكرة مؤقتة → ملف مضغوط → سيرفر (اختياري).

---

## 1. النواة الحالية (The Core Nucleus)

نبني على المكونات الموجودة مباشرة:

| المكون | ماذا نأخذ منه (بدون إعادة حساب) |
|--------|-------------------------------|
| `TrainingEngine.processFrame()` | `smoothedAngles`, `currentPhase`, `jointStateInfos` |
| `PhaseStateMachine` | توقيت تغيّر المراحل (timestamps) |
| `RepCounter.completeRep()` | نقطة إغلاق العدة + `phaseTimings` + `worstState` |
| `FormValidator` | `JointState` لكل مفصل (موجود في `jointStateInfos`) |

**ملاحظة:** لا نحتاج `PoseLandmarkerHelper` مباشرة لأن الزوايا (`smoothedAngles`) كافية للمقاييس المطلوبة. landmarks الخام ثقيلة ونادراً ما نحتاجها.

---

## 2. هيكل البيانات الخفيف (Lightweight Data Schema)

### أ. خريطة المفاصل (Joint Index Map) - ثابتة
بدلاً من تكرار أسماء المفاصل في كل إطار، نستخدم index:

```kotlin
// ثابت في الكود - لا يُخزّن مع كل جلسة
object JointIndex {
    const val LEFT_KNEE = 0
    const val RIGHT_KNEE = 1
    const val LEFT_HIP = 2
    const val RIGHT_HIP = 3
    const val LEFT_ELBOW = 4
    const val RIGHT_ELBOW = 5
    // ... حسب التمرين
}

// Phase كـ Byte
object PhaseCode {
    const val IDLE: Byte = 0
    const val ECCENTRIC: Byte = 1
    const val ISOMETRIC: Byte = 2
    const val CONCENTRIC: Byte = 3
}

// JointState كـ Byte
object StateCode {
    const val PERFECT: Byte = 0
    const val NORMAL: Byte = 1
    const val PAD: Byte = 2
    const val WARNING: Byte = 3
    const val DANGER: Byte = 4
}
```

### ب. الإطار الخفيف (FrameSample)
فقط البيانات المطلوبة للمقاييس:

```kotlin
data class FrameSample(
    val t: Int,                    // milliseconds منذ بدء الجلسة (4 bytes)
    val phase: Byte,               // PhaseCode (1 byte)
    val angles: ShortArray,        // زوايا المفاصل المتتبعة × 10 (e.g., 90.5° → 905)
    val states: ByteArray?         // حالة كل مفصل (اختياري - فقط عند التغيّر)
)
```

**لماذا ShortArray؟**
- الزاوية من 0-180، نضربها في 10 لحفظ رقم عشري واحد → 0-1800 (Short كافي).
- بدلاً من `Map<String, Double>` (ثقيل جداً)، نستخدم `ShortArray` بترتيب ثابت حسب `JointIndex`.

### ج. سجل العدة (RepRecord)
يُنشأ عند `completeRep()`:

```kotlin
data class RepRecord(
    val num: Int,                  // رقم العدة
    val startT: Int,               // بداية العدة (ms)
    val endT: Int,                 // نهاية العدة (ms)
    val frames: List<FrameSample>, // إطارات هذه العدة فقط
    val phases: IntArray,          // [eccentricMs, isometricMs, concentricMs]
    val worstState: Byte,          // أسوأ حالة وصلها
    val score: Short,              // النتيجة × 10 (e.g., 85.5 → 855)
    val weightKg: Float? = null    // الوزن المستخدم (اختياري)
)
```

### د. ملخص الجلسة (Planned WorkoutRecord)
```kotlin
data class Planned WorkoutRecord(
    val id: String,
    val exerciseId: String,
    val startEpoch: Long,          // Unix timestamp
    val durationMs: Int,
    val trackedJoints: List<String>, // أسماء المفاصل المتتبعة (مرة واحدة)
    val defaultWeightKg: Float?,     // الوزن الافتراضي للجلسة
    val weightUnit: String = "kg",   // الوحدة المستخدمة (kg/lbs) - للتخزين فقط، التحويل في العرض
    val reps: List<RepRecord>,
    val metrics: WorkoutExecutionMetrics    // المقاييس المحسوبة
)
```

---

## 3. المقاييس المحسوبة (Calculated Metrics)

تُحسب مرة واحدة عند انتهاء الجلسة من `RepRecord.frames`:

```kotlin
data class RepMetrics(
    val rom: Short,             // Range of Motion (max - min للمفصل الرئيسي) × 10
    val symmetry: Short?,       // نسبة التماثل × 10 (null لتمارين جانب واحد)
    val stability: Short,       // ثبات مركز الجسم × 10
    val tempo: IntArray,        // [eccentric, iso, concentric] بالـ ms
    val velocity: Short?,       // متوسط السرعة × 100 (للمرحلة الإيجابية)
    val formScore: Short,       // من RepCounter (موجود بالفعل)
    val alignmentAccuracy: Short // نسبة الوقت في وضعية صحيحة × 10 (0-1000)
)

data class WorkoutExecutionMetrics(
    val avgRom: Short,
    val avgSymmetry: Short?,
    val avgStability: Short,
    val avgTempo: IntArray,
    val avgVelocity: Short?,
    val avgFormScore: Short,
    val avgAlignmentAccuracy: Short,  // متوسط دقة المحاذاة
    val totalTUT: Int,                // Time Under Tension (ms)
    
    // مقاييس الأحمال (Load Metrics)
    val totalVolume: Float?,          // الحجم التدريبي (reps × weight)
    val maxWeight: Float?,            // أقصى وزن تم استخدامه
    val est1RM: Float?,               // القوة القصوى التقديرية (1RM)
    
    // مقاييس متقدمة (Future/Later)
    val relativeStrength: Float?,     // القوة النسبية (weight/bodyWeight)
    val intensityPercentage: Float?,  // الشدة (% of 1RM)
    
    // مقاييس جودة الأداء (Quality Metrics)
    val formConsistency: Short?,      // تناسق الشكل بين العدات (DTW) - 1000 = متطابق تماماً
    val fatigueIndex: Short?          // رقم العدة التي بدأ فيها التعب (null = لا تعب ملحوظ)
)
```

### منطق الحساب (بسيط وفعال):

```kotlin
object MetricsCalculator {
    
    // ROM: الفرق بين أعلى وأقل زاوية للمفصل الرئيسي
    fun calculateROM(frames: List<FrameSample>, jointIndex: Int): Short {
        val angles = frames.map { it.angles[jointIndex] }
        return (angles.maxOrNull()!! - angles.minOrNull()!!).toShort()
    }
    
    // Symmetry: متوسط الفرق بين اليمين واليسار
    fun calculateSymmetry(frames: List<FrameSample>, leftIdx: Int, rightIdx: Int): Short {
        val avgDiff = frames.map { abs(it.angles[leftIdx] - it.angles[rightIdx]) }.average()
        // تحويل لنسبة (0 فرق = 100%, 180° فرق = 0%)
        return ((1 - avgDiff / 1800.0) * 1000).toInt().toShort()
    }
    
    // Velocity: المسافة الزاوية ÷ الزمن (في مرحلة Concentric)
    fun calculateVelocity(frames: List<FrameSample>, jointIndex: Int): Short? {
        val concentric = frames.filter { it.phase == PhaseCode.CONCENTRIC }
        if (concentric.size < 2) return null
        
        val angleDelta = abs(concentric.last().angles[jointIndex] - concentric.first().angles[jointIndex])
        val timeSec = (concentric.last().t - concentric.first().t) / 1000f
        
        return if (timeSec > 0) ((angleDelta / timeSec) / 10).toInt().toShort() else null
    }
    
    // Stability: انحراف مركز الجسم (من hips midpoint)
    fun calculateStability(frames: List<FrameSample>, hipIndices: Pair<Int, Int>): Short {
        // نحسب متوسط الوركين ونقيس الانحراف المعياري
        val midpoints = frames.map { (it.angles[hipIndices.first] + it.angles[hipIndices.second]) / 2 }
        val std = standardDeviation(midpoints)
        // تحويل لنسبة (0 انحراف = 100%)
        return ((1 - std / 500.0).coerceIn(0.0, 1.0) * 1000).toInt().toShort()
    }
    
    // ==================== مقاييس الجودة (Quality Metrics) ====================
    
    // Alignment Accuracy: نسبة الوقت في وضعية صحيحة (PERFECT أو NORMAL)
    // المصدر: JointState الموجود بالفعل في jointStateInfos
    fun calculateAlignmentAccuracy(frames: List<FrameSample>): Short {
        val goodFrames = frames.count { frame ->
            frame.states?.all { it <= StateCode.NORMAL } ?: true
        }
        return ((goodFrames.toFloat() / frames.size) * 1000).toInt().toShort()
    }
    
    // Form Consistency: مقارنة منحنى العدات الأولى بالأخيرة (DTW)
    // يكشف "الإجهاد التقني" - متى بدأ الشكل ينهار
    fun calculateFormConsistency(reps: List<RepRecord>, jointIndex: Int): Short? {
        if (reps.size < 4) return null // لا يوجد ما يكفي للمقارنة
        
        val firstReps = reps.take(3).flatMap { it.frames.map { f -> f.angles[jointIndex] } }
        val lastReps = reps.takeLast(3).flatMap { it.frames.map { f -> f.angles[jointIndex] } }
        
        val dtw = dynamicTimeWarping(firstReps, lastReps)
        return (1000 - dtw.coerceIn(0, 1000)).toShort() // 1000 = متطابق تماماً
    }
    
    // Fatigue Index: رقم العدة التي بدأ فيها الأداء ينهار
    // المصدر: RepRecord.score الموجود بالفعل
    fun calculateFatigueIndex(reps: List<RepRecord>): Short? {
        if (reps.size < 4) return null
        
        val avgFirstHalf = reps.take(reps.size / 2).map { it.score }.average()
        
        for (i in reps.indices) {
            if (reps[i].score < avgFirstHalf * 0.8) { // انخفاض 20%+
                return (i + 1).toShort() // رقم العدة (1-based)
            }
        }
        return null // لم يحدث تعب ملحوظ
    }
    
    // ==================== مقاييس الأحمال (Load Metrics) ====================
    
    // Estimated 1RM: تقدير القوة القصوى (معادلة Epley)
    fun calculateEst1RM(weight: Float, reps: Int): Float {
        if (weight <= 0) return 0f
        return if (reps <= 1) weight else weight * (1 + reps / 30f)
    }
    
    // Total Volume: مجموع الأحمال (weight * reps)
    fun calculateVolume(reps: List<RepRecord>): Float {
        return reps.sumOf { (it.weightKg ?: 0f).toDouble() }.toFloat() * reps.size // تقريبي إذا الوزن ثابت
        // أو بدقة أكثر لو الوزن متغير لكل عدة:
        // return reps.sumOf { (it.weightKg ?: 0f) * 1.0 }.toFloat()
    }

    // ==================== DTW Helper ====================
    
    // Dynamic Time Warping - خوارزمية بسيطة لمقارنة منحنيات
    private fun dynamicTimeWarping(seq1: List<Short>, seq2: List<Short>): Int {
        val n = seq1.size
        val m = seq2.size
        if (n == 0 || m == 0) return 1000
        
        val dtw = Array(n + 1) { IntArray(m + 1) { Int.MAX_VALUE } }
        dtw[0][0] = 0
        
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = abs(seq1[i - 1] - seq2[j - 1])
                dtw[i][j] = cost + minOf(dtw[i-1][j], dtw[i][j-1], dtw[i-1][j-1])
            }
        }
        
        // تطبيع النتيجة لتكون بين 0-1000
        return (dtw[n][m] / (n + m)).coerceIn(0, 1000)
    }
}
```

### ملخص المقاييس المغطاة (Dashboard Metrics)

| التصنيف | المقياس | الوحدة | المصدر | الهدف |
|---------|---------|--------|--------|-------|
| **Kinematic** | ROM | درجة (°) | `smoothedAngles` | قياس المرونة وكفاءة المدى الحركي |
| **Kinematic** | Symmetry | نسبة (%) | `smoothedAngles` | اكتشاف الانحرافات والضعف الجانبي |
| **Kinematic** | Stability | نسبة (%) | `smoothedAngles` (hips) | قياس الثبات والتوازن أثناء الوزن |
| **Temporal** | Tempo | ثواني (s) | `phaseTimings` | التحكم في الإيقاع العضلي |
| **Temporal** | TUT | ثواني (s) | `phaseTimings` | إجمالي الوقت تحت الضغط |
| **Power** | Velocity | م/ث (m/s) | `smoothedAngles` + `t` | قياس القدرة الانفجارية ومستوى التعب |
| **Quality** | Form Score | نسبة (%) | `RepCounter.score` | جودة الأداء العامة |
| **Quality** | Alignment Accuracy | نسبة (%) | `jointStateInfos` | حماية المفاصل من الإصابات |
| **Quality** | Form Consistency | نسبة (%) | DTW على `frames` | رصد الإجهاد التقني |
| **Quality** | Fatigue Index | رقم عدة | `RepRecord.score` | متى بدأ الأداء ينهار؟ |

---

## 4. تدفق البيانات (Data Flow) - OPTIMIZED

> **تحديث مهم:** التصميم الجديد لا يحفظ الـ frames (البيانات الخام) - فقط المقاييس المحسوبة.
> هذا يوفر ~95% من حجم التخزين ويسرع الـ sync.

```
┌─────────────────────────────────────────────────────────────────┐
│                    TrainingEngine.processFrame()                 │
│  smoothedAngles, phase, jointStateInfos (موجودين بالفعل)         │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MotionRecorder.record()                       │
│  - يأخذ البيانات الموجودة (لا يحسب شيء جديد)                      │
│  - يحوّلها لـ FrameSample خفيف                                   │
│  - يخزنها في Buffer مؤقت (في الذاكرة فقط!)                       │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼ عند completeRep()
┌─────────────────────────────────────────────────────────────────┐
│                    MotionRecorder.finalizeRep()                  │
│  ⭐ يحسب RepMetrics من الـ Buffer الحالي                         │
│  - يحفظ RepMetrics فقط (بدون frames!)                           │
│  - يُفرغ الـ Buffer ← توفير ذاكرة                                │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼ عند انتهاء الجلسة
┌─────────────────────────────────────────────────────────────────┐
│                    MotionRecorder.finalize()                     │
│  - يجمّع RepMetrics → WorkoutExecutionMetrics                             │
│  - ينشئ WorkoutExecutionUpload (metrics فقط، بدون frames)                │
│  - حجم: ~500 bytes بدل ~15KB!                                   │
└─────────────────────┬───────────────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        ▼                           ▼
┌───────────────────────┐    ┌─────────────────────────┐
│   AnalyticsStorage    │    │   WorkoutSyncService    │
│   (Pending Sync)      │    │   (Direct Upload)       │
│   /analytics/pending/ │    │   POST /api/mobile/     │
│   workout_{id}.json.gz│    │   workout-executions         │
│                       │    │                         │
│   حجم: ~500 bytes     │    │   ← يُرسل WorkoutExecutionUpload │
│   (للـ offline mode)  │    │   ← يحذف pending file   │
└───────────────────────┘    └─────────────────────────┘
                                    │
                                    ▼
                         ┌─────────────────────────┐
                         │   Backend (WorkoutExecution)
                         │   - يحفظ executionMetrics │
                         │   - يحفظ repMetrics     │
                         │   - يرجع exercise history
                         └─────────────────────────┘
```

### ملخص الفوائد:
| البند | قبل | بعد |
|-------|-----|-----|
| حجم التمرين (upload) | ~15 KB | ~500 bytes |
| استهلاك الذاكرة | Buffer + RepRecords | Buffer مؤقت فقط |
| سرعة الـ Sync | بطيء (ملفات كبيرة) | سريع جداً |
| البيانات المحفوظة | frames + metrics | metrics فقط |

---

## 5. استراتيجية التخزين (Storage Strategy)

### أ. الذاكرة المؤقتة (In-Memory Buffer)
```kotlin
class MotionRecorder {
    // Buffer للعدة الحالية فقط - يُفرّغ عند completeRep
    private val currentRepBuffer = mutableListOf<FrameSample>()
    
    // العدات المكتملة
    private val completedReps = mutableListOf<RepRecord>()
    
    // حد أقصى للأمان (في حالة تمرين طويل جداً)
    private val maxFramesPerRep = 300  // 10 ثواني × 30fps
}
```

### ب. Room DB (خفيف - للقوائم والبحث)
```kotlin
@Entity
data class Planned WorkoutEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val timestamp: Long,
    val totalReps: Int,
    val avgScore: Float,
    val durationMs: Int,
    // المقاييس الملخصة
    val avgRom: Float?,
    val avgVelocity: Float?,
    val avgStability: Float?,
    val avgAlignmentAccuracy: Float?,  // دقة المحاذاة
    val totalVolume: Float?,           // الحجم التدريبي
    val est1RM: Float?,                // القوة القصوى
    val formConsistency: Float?,       // تناسق الشكل (DTW)
    val fatigueIndex: Int?,            // رقم عدة بداية التعب
    val synced: Boolean = false        // هل تم رفعه للسيرفر؟
)
```

### ج. نظام الملفات (JSON.gz - للتفاصيل)
```
/files/
└── analytics/
    ├── workout_abc123.json.gz   (~10 KB)
    ├── workout_def456.json.gz
    └── ...
```

**تقدير الحجم:**
- تمرين 12 عدة × 3 ثواني/عدة × 30fps = 1080 frame
- كل frame ≈ 20 bytes (بالتنسيق الخفيف)
- إجمالي ≈ 22 KB قبل الضغط → ~8 KB بعد gzip

### د. التنظيف التلقائي
```kotlin
fun cleanupOldPlanned Workouts(keepDays: Int = 30, maxCount: Int = 100) {
    // حذف التمارين الأقدم من 30 يوم
    // أو إذا تجاوز العدد 100 تمرين
}
```

---

## 6. التكامل مع الكود الحالي (Integration Points)

### أ. في `TrainingEngine.processFrame()`:
```kotlin
// بعد السطر 648 (بعد حساب smoothedAngles و jointStateInfos)
motionRecorder?.record(
    timestamp = System.currentTimeMillis(),
    phase = currentPhase,
    angles = smoothedAngles,  // موجود بالفعل
    states = jointStateInfos   // موجود بالفعل (اختياري)
)
```

### ب. في `RepCounter.completeRep()`:
```kotlin
// بعد إنشاء RepResult
motionRecorder?.finalizeRep(
    repNumber = count,
    phaseTimings = currentPhaseTimings,  // موجود بالفعل
    worstState = currentRepWorstState,
    score = score
)
```

### ج. في `TrainingActivity` عند الانتهاء:
```kotlin
// بعد engine.stop()
val workoutRecord = motionRecorder.finalize(plannedWorkoutId, exerciseId)
analyticsStorage.save(workoutRecord)
```

---

## 7. خطوات التنفيذ (Action Plan)

| الخطوة | الوصف | الملفات |
|--------|------|---------|
| 1 | إنشاء النماذج الخفيفة | `models/MotionData.kt` |
| 2 | إنشاء `MotionRecorder` | `training/MotionRecorder.kt` |
| 3 | إنشاء `MetricsCalculator` | `training/MetricsCalculator.kt` |
| 4 | إنشاء `AnalyticsStorage` | `storage/AnalyticsStorage.kt` |
| 5 | تكامل في `TrainingEngine` | تعديل `processFrame()` |
| 6 | تكامل في `RepCounter` | تعديل `completeRep()` |
| 7 | تحديث `ReportGenerator` | استخدام `WorkoutExecutionMetrics` |

---

## 8. ملاحظات مهمة

1. **لا landmarks خام**: الزوايا المحسوبة (`smoothedAngles`) كافية للمقاييس. landmarks ثقيلة جداً (~33 نقطة × 4 قيم × 4 bytes = 528 bytes/frame).

2. **states اختيارية**: نسجلها فقط عند التغيّر (مثلاً من NORMAL لـ WARNING) لتوفير المساحة.

3. **الحساب مرة واحدة**: المقاييس تُحسب عند انتهاء الجلسة، لا أثناء التمرين (لتجنب التأثير على الـ FPS).

4. **السيرفر اختياري**: النظام يعمل بالكامل محلياً. الـ sync للسيرفر ميزة إضافية.

5. **التوافقية**: التنسيق الجديد لا يؤثر على `PostTrainingReport` الحالي. نضيف البيانات الجديدة كطبقة منفصلة.

6. **المقاييس الجديدة تعتمد على بيانات موجودة**:
   - `alignmentAccuracy` ← من `jointStateInfos` (موجود 100%)
   - `formConsistency` ← من `frames` المحفوظة (DTW بسيط)
   - `fatigueIndex` ← من `RepRecord.score` (موجود 100%)

7. **Stability بديل خفيف**: بدلاً من حساب مركز الكتلة الحقيقي (يتطلب landmarks)، نقيس ثبات زوايا الوركين. أقل دقة لكن كافي وخفيف جداً.

---

## 9. التوافق مع الكود الحالي (Compatibility Strategy)

**ملاحظة هامة:** الكود الحالي (TrainingEngine, FormValidator, etc.) لا يحتاج لتعديل جذري. التحويل للشكل الخفيف يحدث فقط في طبقة التخزين (`MotionRecorder`).

### كيف سيعمل التحويل في `MotionRecorder`

```kotlin
class MotionRecorder(
    private val trackedJoints: List<String>  // ترتيب ثابت للمفاصل
) {
    // تحويل من الشكل الحالي للشكل الخفيف
    fun record(
        timestamp: Long,
        phase: Phase,                        // Enum الحالي
        angles: Map<String, Double>,         // Map الحالي
        states: Map<String, JointStateInfo>? // Map الحالي
    ) {
        val sample = FrameSample(
            t = (timestamp - workoutStartMs).toInt(),
            phase = phase.toByteCode(),      // Enum → Byte
            angles = anglesToShortArray(angles),  // Map → ShortArray
            states = statesToByteArray(states)    // Map → ByteArray
        )
        currentRepBuffer.add(sample)
    }
    
    // التحويلات
    private fun Phase.toByteCode(): Byte = when (this) {
        Phase.IDLE, Phase.START -> PhaseCode.IDLE
        Phase.DOWN -> PhaseCode.ECCENTRIC
        Phase.BOTTOM, Phase.EXTENDED -> PhaseCode.ISOMETRIC
        Phase.UP, Phase.PUSH, Phase.PULL -> PhaseCode.CONCENTRIC
        else -> PhaseCode.IDLE
    }
    
    private fun anglesToShortArray(angles: Map<String, Double>): ShortArray {
        return ShortArray(trackedJoints.size) { i ->
            val angle = angles[trackedJoints[i]] ?: 0.0
            (angle * 10).toInt().toShort()  // 90.5° → 905
        }
    }
    
    private fun statesToByteArray(states: Map<String, JointStateInfo>?): ByteArray? {
        if (states == null) return null
        return ByteArray(trackedJoints.size) { i ->
            states[trackedJoints[i]]?.state?.ordinal?.toByte() ?: StateCode.NORMAL
        }
    }
}
```

### التعديلات المطلوبة فقط:
1. إنشاء `MotionRecorder.kt` (جديد)
2. إنشاء `MotionData.kt` (النماذج الخفيفة)
3. إضافة استدعاء `record()` في `TrainingEngine.processFrame()`
4. إضافة استدعاء `finalizeRep()` في `RepCounter.completeRep()`

**لا حاجة لتغيير بنية `Map<String, Double>` في المعالجة الحية.**
