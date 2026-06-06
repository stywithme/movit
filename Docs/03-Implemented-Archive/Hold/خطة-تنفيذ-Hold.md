> **Status:** `ARCHIVED` â€” implemented or superseded; not current product truth.
> **Current SSOT:** `Docs/00-Active-Reference/Engine/training-engine.md, Positions-Check-Concept.md`
> **Archived:** 2026-05-29

# خطة تنفيذ تمرين Hold (عداد الوقت)

## نظرة عامة

تمرين Hold هو تمرين يعتمد على **الثبات في وضع معين لفترة زمنية محددة** بدلاً من عد التكرارات.

### أمثلة
- Plank (30-60 ثانية)
- Wall Sit (45 ثانية)
- Static Lunge Hold

---

## المتطلبات

### السلوك المطلوب

| الحالة | السلوك |
|--------|--------|
| **دخول الوضع** | يبدأ العداد الزمني |
| **البقاء في الوضع** | العداد يستمر حتى الوصول للهدف |
| **خروج مؤقت** | فترة سماح (Grace Period) قبل الفشل |
| **تجاوز فترة السماح** | التمرين يفشل ويعيد من الأول |
| **الوصول للهدف** | التمرين ينتهي بنجاح |

### القيود

- ❌ لا يُسمح بالـ Breaks (الخروج والعودة لاستكمال)
- ✅ فترة سماح قصيرة للخروج العرضي (مثلاً 2-3 ثواني)
- ✅ بعد تجاوز فترة السماح → التمرين يفشل ويبدأ من جديد
- ✅ الـ Pause اليدوي (زر) مسموح ومنفصل عن الـ Grace Period

### تعريف "الخروج من الوضع"

> **مهم:** الخروج من الوضع يعني خروج **أي primary joint** من الـ `downRange`.
> أخطاء الـ Form (secondary joints) لا توقف العداد، لكن تُسجَّل وتؤثر على الـ `formQuality`.

---

## التكامل مع الكود الحالي

### ما هو موجود بالفعل ✅

| Component | الوضع الحالي |
|-----------|--------------|
| `CountingMethod.HOLD` | ✅ موجود في `ExerciseConfig.kt` |
| `Phase.COUNT` | ✅ موجود في `PhaseStateMachine` كـ "hold zone" |
| `PhaseStateMachine.updateHold()` | ✅ موجود، يتتبع IDLE ↔ COUNT |
| `FormValidator` مع `Phase.COUNT` | ✅ يعمل، يتحقق من `downRange` |
| `RepCountingConfig.duration` | ✅ موجود (حقل `duration: Int?`) |

### ما هو ناقص ❌

| Component | المطلوب |
|-----------|---------|
| `gracePeriodMs` | إضافة حقل جديد في `RepCountingConfig` |
| `HoldTimer` | Component جديد لإدارة العداد والـ Grace |
| Hold StateFlows | `holdState`, `elapsedMs`, `graceRemainingMs` |
| Hold Events | `HoldStarted`, `HoldCompleted`, `HoldFailed` |
| UI للـ Hold | عرض الوقت بدل الـ Reps |

---

## الـ States (داخل TrainingEngine فقط)

> **ملاحظة:** لن نضيف states جديدة لـ `Phase` enum. سنستخدم `HoldState` enum منفصل داخل `HoldTimer` فقط.

```
HoldState (internal to HoldTimer)
├── IDLE          → في انتظار دخول الوضع (Phase.IDLE)
├── HOLDING       → داخل الوضع الصحيح (Phase.COUNT)
├── GRACE_PERIOD  → خرج مؤقتاً (Phase.IDLE + grace timer)
├── COMPLETED     → وصل للهدف الزمني
└── FAILED        → تجاوز فترة السماح
```

### مخطط الانتقالات

```
                    ┌─────────────┐
                    │    IDLE     │ (Phase.IDLE)
                    └──────┬──────┘
                           │ Phase → COUNT
                           ▼
                    ┌─────────────┐
            ┌──────►│   HOLDING   │◄──────┐ (Phase.COUNT)
            │       └──────┬──────┘       │
            │              │              │
            │   Phase→IDLE │   وصل للهدف  │  رجع للوضع
            │              │              │  (Phase→COUNT)
            │              ▼              │
            │       ┌─────────────┐       │
            │       │  COMPLETED  │       │
            │       └─────────────┘       │
            │                             │
            │       ┌─────────────┐       │
            └───────│GRACE_PERIOD │───────┘ (Phase.IDLE + grace active)
                    └──────┬──────┘
                           │ grace timeout
                           ▼
                    ┌─────────────┐
                    │   FAILED    │
                    └──────┬──────┘
                           │ auto-reset
                           ▼
                    ┌─────────────┐
                    │    IDLE     │
                    └─────────────┘
```

---

## الإعدادات المطلوبة

### في JSON التمرين

> **ملاحظة:** نستخدم `duration` الموجود + نضيف `gracePeriodMs` فقط.

```json
{
  "countingMethod": "hold",
  "poseVariants": [{
    "difficultyLevels": [{
      "level": "beginner",
      "repCountingConfig": {
        "duration": 30,
        "gracePeriodMs": 3000
      }
    }]
  }]
}
```

| الحقل | النوع | الوصف | Default |
|-------|-------|-------|---------|
| `duration` | `Int?` | الهدف الزمني بالثواني | 30 |
| `gracePeriodMs` | `Long?` | فترة السماح بالمللي ثانية | 3000 |

### في app_settings.json (defaults)

```json
{
  "holdDefaults": {
    "defaultDurationSeconds": 30,
    "defaultGracePeriodMs": 3000
  }
}
```

---

## الـ State Flows (للـ UI)

> **نمط المشروع:** StateFlow للحالات المستمرة، Events للحظات المحددة.

### StateFlows جديدة في TrainingEngine

```kotlin
// Hold-specific state (null for non-hold exercises)
val holdState: StateFlow<HoldState?>

// Time tracking (null for non-hold)
val holdElapsedMs: StateFlow<Long?>
val holdRemainingMs: StateFlow<Long?>
val holdProgress: StateFlow<Float?>  // 0.0 - 1.0

// Grace period (null when not in grace)
val graceRemainingMs: StateFlow<Long?>
```

### استخدام الـ UI

```kotlin
// في TrainingActivity
if (exerciseConfig.countingMethod == CountingMethod.HOLD) {
    // Observe hold-specific flows
    engine.holdProgress.collectLatest { progress ->
        binding.holdProgressBar.progress = (progress * 100).toInt()
    }
    engine.holdRemainingMs.collectLatest { remaining ->
        binding.tvHoldTime.text = formatTime(remaining)
    }
} else {
    // Existing rep-based UI
    engine.repCount.collectLatest { ... }
}
```

---

## الـ Events (اللحظية فقط)

> **ملاحظة:** لا نستخدم Events للتحديثات المتكررة (مثل progress كل ثانية). نستخدم StateFlow بدلاً.

| Event | الوصف | البيانات |
|-------|-------|---------|
| `HoldStarted` | بدأ الثبات لأول مرة | — |
| `HoldGraceStarted` | خرج من الوضع، بدأ السماح | `gracePeriodMs` |
| `HoldResumed` | رجع للوضع في الوقت | `elapsedMs` |
| `HoldCompleted` | اكتمل الهدف بنجاح | `totalMs`, `formQuality` |
| `HoldFailed` | فشل (تجاوز السماح) | `elapsedBeforeFailMs`, `gracePeriodCount` |

### إضافة في FeedbackEvent.kt

```kotlin
// Hold Events
data class HoldStarted(
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
) : FeedbackEvent()

data class HoldGraceStarted(
    val gracePeriodMs: Long,
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.HIGH
) : FeedbackEvent()

data class HoldResumed(
    val elapsedMs: Long,
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
) : FeedbackEvent()

data class HoldCompleted(
    val totalMs: Long,
    val formQuality: Float,  // 0.0 - 1.0
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.HIGH
) : FeedbackEvent()

data class HoldFailed(
    val elapsedBeforeFailMs: Long,
    val gracePeriodCount: Int,
    override val timestamp: Long = System.currentTimeMillis(),
    override val priority: FeedbackPriority = FeedbackPriority.HIGH
) : FeedbackEvent()
```

---

## التعديلات المطلوبة على الـ Components

### 1. RepCountingConfig (Model) - تعديل بسيط

```kotlin
data class RepCountingConfig(
    val reps: Int = 12,
    val duration: Int? = null,              // ✅ موجود - الهدف بالثواني للـ HOLD
    val gracePeriodMs: Long? = null,        // ⭐ جديد - فترة السماح
    val minRepIntervalMs: Long? = null,
    val maxRepIntervalMs: Long? = null
) {
    // Helper methods
    fun getDurationMs(): Long? = duration?.times(1000L)
    
    fun getGracePeriod(default: Long): Long = gracePeriodMs ?: default
}
```

### 2. HoldTimer (Component جديد)

> **الموقع:** `training/engine/HoldTimer.kt`

```kotlin
class HoldTimer(
    private val targetDurationMs: Long,
    private val gracePeriodMs: Long
) {
    // State
    private val _state = MutableStateFlow(HoldState.IDLE)
    val state: StateFlow<HoldState> = _state
    
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs
    
    private val _graceRemainingMs = MutableStateFlow<Long?>(null)
    val graceRemainingMs: StateFlow<Long?> = _graceRemainingMs
    
    // Callbacks
    var onStateChanged: ((HoldState, HoldState) -> Unit)? = null
    var onCompleted: (() -> Unit)? = null
    var onFailed: ((elapsedMs: Long, graceCount: Int) -> Unit)? = null
    
    // Internal tracking
    private var holdStartTime: Long = 0
    private var accumulatedMs: Long = 0
    private var graceStartTime: Long = 0
    private var gracePeriodCount: Int = 0
    
    fun update(isInHoldZone: Boolean, currentTimeMs: Long) { ... }
    fun getProgress(): Float { ... }
    fun getRemainingMs(): Long { ... }
    fun reset() { ... }
}

enum class HoldState {
    IDLE,
    HOLDING,
    GRACE_PERIOD,
    COMPLETED,
    FAILED
}
```

### 3. PhaseStateMachine - لا تغيير ✅

> `updateHold()` يبقى كما هو. فقط يتتبع `IDLE ↔ COUNT`.
> الـ `HoldTimer` يستخدم نتيجة `currentPhase` لتحديد `isInHoldZone`.

```kotlin
// في TrainingEngine.processFrame()
val isInHoldZone = (stateMachine.currentPhase == Phase.COUNT)
holdTimer?.update(isInHoldZone, System.currentTimeMillis())
```

### 4. TrainingEngine - تعديل

```kotlin
class TrainingEngine(...) {
    // Existing components
    private val repCounter = RepCounter(...)  // للـ UP_DOWN و PUSH_PULL
    
    // Hold-specific (null for non-hold)
    private val holdTimer: HoldTimer? = if (exerciseConfig.countingMethod == CountingMethod.HOLD) {
        HoldTimer(
            targetDurationMs = (difficultyLevel.repCountingConfig.duration ?: 30) * 1000L,
            gracePeriodMs = difficultyLevel.repCountingConfig.gracePeriodMs 
                ?: SettingsManager.getDefaultGracePeriod()
        )
    } else null
    
    // Hold StateFlows
    private val _holdState = MutableStateFlow<HoldState?>(null)
    val holdState: StateFlow<HoldState?> = _holdState
    
    private val _holdElapsedMs = MutableStateFlow<Long?>(null)
    val holdElapsedMs: StateFlow<Long?> = _holdElapsedMs
    
    // ... other hold flows
    
    fun processFrame(angles: JointAngles) {
        // ... existing logic ...
        
        // 4b. Update hold timer (if hold exercise)
        if (holdTimer != null) {
            val isInHoldZone = (currentPhase == Phase.COUNT)
            holdTimer.update(isInHoldZone, System.currentTimeMillis())
            
            // Update flows
            _holdState.value = holdTimer.state.value
            _holdElapsedMs.value = holdTimer.elapsedMs.value
            // ...
        }
    }
}
```

### 5. AppSettings / SettingsManager - إضافة defaults

```kotlin
// في AppSettings.kt
data class HoldDefaults(
    val defaultDurationSeconds: Int = 30,
    val defaultGracePeriodMs: Long = 3000
)

data class AppSettings(
    // ... existing
    val holdDefaults: HoldDefaults = HoldDefaults()
)

// في SettingsManager.kt
fun getDefaultHoldDuration(): Int = settings.holdDefaults.defaultDurationSeconds
fun getDefaultGracePeriod(): Long = settings.holdDefaults.defaultGracePeriodMs
```

### 6. FeedbackManager - التعامل مع Hold Events

```kotlin
private fun handleHoldEvent(event: FeedbackEvent) {
    when (event) {
        is FeedbackEvent.HoldGraceStarted -> {
            if (config.enableHaptic) vibrateWarning()
            // Visual warning handled by UI via StateFlow
        }
        is FeedbackEvent.HoldCompleted -> {
            if (config.enableHaptic) vibrateComplete()
            if (config.enableAudio) speak("Great job!")
        }
        is FeedbackEvent.HoldFailed -> {
            if (config.enableHaptic) vibrateError()
        }
        else -> {}
    }
}
```

---

## ترتيب التنفيذ

### Step 1: تحديث الـ Models ⏱️ ~30 min
- [ ] إضافة `gracePeriodMs: Long?` لـ `RepCountingConfig`
- [ ] إضافة helper methods (`getDurationMs()`, `getGracePeriod()`)
- [ ] إضافة Hold events لـ `FeedbackEvent.kt`
- [ ] إضافة `HoldDefaults` لـ `AppSettings.kt`
- [ ] تحديث `app_settings.json`
- [ ] تحديث `SettingsManager.kt`

### Step 2: إنشاء HoldTimer ⏱️ ~1 hour
- [ ] إنشاء `HoldTimer.kt` في `training/engine/`
- [ ] إنشاء `HoldState` enum
- [ ] Logic للعداد الأساسي
- [ ] Logic للـ Grace Period
- [ ] Callbacks للـ state changes

### Step 3: تعديل TrainingEngine ⏱️ ~1 hour
- [ ] إضافة `holdTimer` (conditional)
- [ ] إضافة Hold StateFlows
- [ ] تعديل `processFrame()` للتعامل مع Hold
- [ ] إطلاق Hold Events
- [ ] تعديل `isCompleted` logic

### Step 4: تعديل UI ⏱️ ~1 hour
- [ ] تعديل `TrainingActivity` للتفريق بين Hold و Reps
- [ ] إضافة عناصر UI للـ Hold (time display, progress bar)
- [ ] عرض Grace Period warning
- [ ] تعديل `ExerciseDetailActivity` لعرض "Target: X sec" للـ Hold

### Step 5: تعديل FeedbackManager ⏱️ ~30 min
- [ ] التعامل مع Hold events
- [ ] Haptic feedback للـ Grace/Complete/Fail

### Step 6: إنشاء تمرين تجريبي ⏱️ ~30 min
- [ ] إنشاء `plank.json` كمثال
- [ ] اختبار السيناريوهات:
  - [ ] Hold حتى الاكتمال
  - [ ] Grace Period ثم العودة
  - [ ] Grace Period ثم الفشل
  - [ ] Pause يدوي ثم Resume

---

## البيانات المطلوب تتبعها (Analytics)

```kotlin
data class HoldPlanned WorkoutResult(
    val targetDurationMs: Long,
    val actualDurationMs: Long,
    val isCompleted: Boolean,
    val failedAtMs: Long?,           // null if completed
    val gracePeriodTriggered: Boolean,
    val gracePeriodCount: Int,
    val formQuality: Float,          // 0.0 - 1.0 (من validation errors)
    val formErrors: List<JointError>
)
```

### حساب formQuality

```kotlin
// نسبة الوقت بدون أخطاء form
formQuality = (totalHoldMs - errorMs) / totalHoldMs
```

---

## UI المتوقع

### أثناء الـ HOLDING

```
┌─────────────────────────────────┐
│                                 │
│         ⏱️ 00:23 / 00:30        │
│                                 │
│    ████████████████░░░░░░░░     │
│              76%                │
│                                 │
│         🟢 Good Form            │
│                                 │
└─────────────────────────────────┘
```

### أثناء الـ GRACE_PERIOD

```
┌─────────────────────────────────┐
│                                 │
│      ⚠️ Return to position!     │
│                                 │
│         ⏱️ 2.5s remaining       │
│                                 │
│    ░░░░░░░░░░░░░░░░░░░░░░░░     │
│                                 │
└─────────────────────────────────┘
```

### عند الـ COMPLETED

```
┌─────────────────────────────────┐
│                                 │
│         🎉 Great Job!           │
│                                 │
│      You held for 30 seconds    │
│                                 │
│         Form Quality: 92%       │
│                                 │
└─────────────────────────────────┘
```

### عند الـ FAILED

```
┌─────────────────────────────────┐
│                                 │
│         ❌ Hold Failed          │
│                                 │
│      You held for 18 seconds    │
│                                 │
│         [ Try Again ]           │
│                                 │
└─────────────────────────────────┘
```

---

## ملاحظات إضافية

### التوافق مع الكود الحالي ✅
- `PhaseStateMachine.updateHold()` → **لا تغيير** (يبقى IDLE ↔ COUNT)
- `FormValidator` مع `Phase.COUNT` → **لا تغيير** (يتحقق من downRange)
- `JointAngleTracker` → **لا تغيير**
- `RepCounter` → يبقى للـ UP_DOWN و PUSH_PULL، `HoldTimer` للـ HOLD

### الـ Pause اليدوي vs Grace Period
| الحالة | Pause اليدوي | Grace Period |
|--------|--------------|--------------|
| **السبب** | المستخدم ضغط زر | خرج من الوضع |
| **العداد الرئيسي** | يتوقف | يتوقف |
| **عداد منفصل** | لا | نعم (grace countdown) |
| **النتيجة لو طال** | يبقى متوقف | فشل |
| **الرجوع** | Resume يدوي | تلقائي عند العودة للوضع |

### التبادل (ALTERNATING) - مؤجل
- سيتم تنفيذه كـ "طبقة" منفصلة فوق أي `countingMethod`
- يمكن تطبيقه على: `hold + alternating` (مثلاً: hold left leg 15s → hold right leg 15s)

---

## مثال JSON كامل: plank.json

```json
{
  "name": {
    "ar": "تمرين البلانك",
    "en": "Plank"
  },
  "description": {
    "ar": "تمرين لتقوية عضلات البطن والجذع",
    "en": "Core strengthening exercise"
  },
  "instructions": {
    "ar": "ارتكز على ساعديك وأصابع قدميك، حافظ على استقامة الجسم",
    "en": "Rest on forearms and toes, keep body straight"
  },
  "category": {
    "code": "core",
    "name": {
      "ar": "تمارين الجذع",
      "en": "Core"
    }
  },
  "countingMethod": "hold",
  "muscles": ["abs", "core", "shoulders"],
  "equipment": ["bodyweight"],
  "tags": ["isometric", "beginner-friendly"],
  "poseVariants": [
    {
      "name": {
        "ar": "زاوية جانبية",
        "en": "Side View"
      },
      "cameraPosition": "side_view",
      "trackedJoints": [
        {
          "joint": "left_hip",
          "role": "primary",
          "startPose": {
            "min": 160,
            "max": 180
          },
          "upRange": {
            "beginner": { "min": 160, "max": 180 },
            "normal": { "min": 165, "max": 180 },
            "advanced": { "min": 170, "max": 180 }
          },
          "downRange": {
            "beginner": { "min": 160, "max": 180 },
            "normal": { "min": 165, "max": 180 },
            "advanced": { "min": 170, "max": 180 }
          },
          "errorMessages": {
            "tooLow": {
              "ar": "ارفع الوسط قليلاً",
              "en": "Raise your hips slightly"
            },
            "tooHigh": {
              "ar": "اخفض الوسط قليلاً",
              "en": "Lower your hips slightly"
            }
          }
        },
        {
          "joint": "left_shoulder",
          "role": "secondary",
          "startPose": {
            "min": 80,
            "max": 100
          },
          "upRange": {
            "beginner": { "min": 75, "max": 105 },
            "normal": { "min": 80, "max": 100 },
            "advanced": { "min": 85, "max": 95 }
          },
          "downRange": {
            "beginner": { "min": 75, "max": 105 },
            "normal": { "min": 80, "max": 100 },
            "advanced": { "min": 85, "max": 95 }
          },
          "errorMessages": {
            "tooLow": {
              "ar": "تقدم قليلاً للأمام",
              "en": "Move forward slightly"
            },
            "tooHigh": {
              "ar": "ارجع قليلاً للخلف",
              "en": "Move back slightly"
            }
          }
        }
      ],
      "feedbackMessages": {
        "motivational": [
          { "ar": "استمر!", "en": "Keep going!" },
          { "ar": "أنت تفعلها!", "en": "You're doing great!" }
        ],
        "tip": [
          { "ar": "تنفس بانتظام", "en": "Breathe steadily" }
        ]
      },
      "difficultyLevels": [
        {
          "level": "beginner",
          "repCountingConfig": {
            "duration": 20,
            "gracePeriodMs": 3000
          },
          "phases": ["hold"]
        },
        {
          "level": "normal",
          "repCountingConfig": {
            "duration": 30,
            "gracePeriodMs": 2500
          },
          "phases": ["hold"]
        },
        {
          "level": "advanced",
          "repCountingConfig": {
            "duration": 45,
            "gracePeriodMs": 2000
          },
          "phases": ["hold"]
        }
      ]
    }
  ]
}
```

---

## ملخص التغييرات

| الملف | نوع التغيير | التفاصيل |
|-------|-------------|----------|
| `RepCountingConfig.kt` | تعديل | إضافة `gracePeriodMs` + helpers |
| `FeedbackEvent.kt` | تعديل | إضافة 5 Hold events |
| `AppSettings.kt` | تعديل | إضافة `HoldDefaults` |
| `app_settings.json` | تعديل | إضافة `holdDefaults` section |
| `SettingsManager.kt` | تعديل | إضافة getters للـ hold defaults |
| `HoldTimer.kt` | **جديد** | Component لإدارة Hold timing |
| `TrainingEngine.kt` | تعديل | إضافة hold logic + StateFlows |
| `FeedbackManager.kt` | تعديل | التعامل مع Hold events |
| `TrainingActivity.kt` | تعديل | UI للـ Hold mode |
| `ExerciseDetailActivity.kt` | تعديل | عرض duration بدل reps |
| `plank.json` | **جديد** | تمرين تجريبي |

---
