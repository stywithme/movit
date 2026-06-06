# State-Machine Unified Plan
## Single Source of Truth for Training Decisions

---

## 1. الرؤية العامة (Vision)

### المشكلة الحالية
- الـ Difficulty (Beginner/Normal/Advanced) موزعة في كل مكان: Config, FormValidator, PhaseStateMachine, UI
- قرارات "العدّ" و"التقييم" و"الألوان" و"الرسائل" تُحسب في أماكن مختلفة بمنطق مختلف
- المستخدم يختار مستوى صعوبة مسبقًا، مما يحدّ من التقييم الحقيقي لأدائه

### الحل المقترح
- **إلغاء مفهوم Difficulty بالكامل** - لا يوجد اختيار مستوى
- **تعريف 5 حالات (States)** + **TRANSITION** لكل Range (Up/Down)
- **كل حالة تحمل معها قراراتها** (هل تُعدّ؟ ما اللون؟ ما الرسالة؟)
- **التقييم النهائي** يُحسب من الـ State الذي وصل إليه المتدرب

### النتيجة المتوقعة
- تمرين واحد للجميع
- تقييم عادل ودقيق بناءً على الأداء الفعلي
- كود أبسط وأكثر اتساقًا
- رسائل وتغذية بصرية أغنى

---

## 2. تعريف الحالات (State Definitions)

### الحالات الست

| State | اسم عربي | Color | Rate | is_rep_counted | severity | ملاحظة |
|-------|----------|-------|------|----------------|----------|--------|
| **PERFECT** | مثالي | Green | 100% | ✅ Yes | none | المدى المثالي |
| **NORMAL** | جيد | Yellow | 60% | ✅ Yes | none | متداخل مع Perfect |
| **PAD** | مقبول | Orange | 20% | ✅ Yes | low | متداخل مع Normal/Perfect |
| **WARNING** | تحذير | Light Red | 0% | ❌ No | medium | على الأطراف الخارجية فقط |
| **DANGER** | خطر | Dark Red | 0% | ❌ No | high | تحذير شديد + يُفسد التكرار |
| **TRANSITION** | انتقال | Blue/Gray | - | 🔄 N/A | none | منطقة حركة بين Up/Down |

### ملاحظات مهمة على الحالات

#### الحالات المُعدّة (Counted States): PERFECT → NORMAL → PAD
- كل طبقة **تمتد للخارج** من الطبقة الأضيق منها
- ممكن تكون متداخلة (overlapping) أو متصلة (adjacent)
- الأولوية للأضيق: إذا الزاوية في PERFECT، نعتبرها PERFECT حتى لو في NORMAL أيضًا
- **أمثلة صحيحة:**
  - متداخلة: PERFECT(130-150), NORMAL(120-160) ← NORMAL يحتوي PERFECT
  - متصلة: PERFECT(130-150), NORMAL(150-170) ← NORMAL يبدأ من نهاية PERFECT

#### الحالات غير المتداخلة (Exclusive States)
- **WARNING** و **DANGER**: على الأطراف الخارجية فقط
- لا يمكن أن تتداخل مع PERFECT/NORMAL/PAD
- هذه للزوايا "الخطيرة" خارج نطاق الحركة الطبيعية

#### TRANSITION Zone
- **ليست State بل منطقة حركة** بين upRange و downRange
- لا تُعدّ ولا تُقيَّم (المتدرب يتحرك)
- تُحسب تلقائيًا: بين `upRange.pad.min` و `downRange.max`

#### سلوك DANGER
- **لا يوقف التمرين** (القرار للمتدرب + قد يكون فيديو)
- **يُصدر تحذير شديد** (صوت + اهتزاز قوي + لون أحمر غامق)
- **يُفسد التكرار الحالي والتالي** حتى يخرج من منطقة الخطر

---

## 2.1 الرسم التوضيحي للـ States

### مثال عملي (Elbow Extension) - حالة التداخل

```
         upRange                              downRange
    ┌─────────────────┐                  ┌─────────────────┐
    │                 │                  │                 │
180°├─ DANGER ────────┤                  │                 │
    │   (170-180)     │                  │                 │
170°├─ WARNING ───────┤                  │                 │
    │   (160-170)     │                  │                 │
160°├─────────────────┤                  │                 │
    │      PAD        │                  │                 │
    │   (110-160)     │                  │                 │
    │  ┌───────────┐  │                  │                 │
    │  │  NORMAL   │  │                  │                 │
    │  │ (120-160) │  │                  │                 │
    │  │ ┌───────┐ │  │                  │                 │
    │  │ │PERFECT│ │  │                  │                 │
    │  │ │130-150│ │  │                  │                 │
    │  │ └───────┘ │  │                  │                 │
    │  └───────────┘  │                  │                 │
110°├─────────────────┤                  │                 │
    │                 │                  │                 │
    │   TRANSITION    │                  │                 │
    │    (80-110)     │                  │                 │
    │   لا تُعدّ      │                  │                 │
 80°├─────────────────┤──────────────────├─────────────────┤
    │                 │                  │      NORMAL     │
    │                 │                  │    (30-80)      │
    │                 │                  │  ┌───────────┐  │
    │                 │                  │  │  PERFECT  │  │
    │                 │                  │  │  (40-70)  │  │
    │                 │                  │  └───────────┘  │
 30°│                 │                  ├─────────────────┤
    │                 │                  │    WARNING      │
    │                 │                  │    (10-30)      │
 10°│                 │                  ├─────────────────┤
    │                 │                  │    DANGER       │
    │                 │                  │    (0-10)       │
  0°└─────────────────┴──────────────────┴─────────────────┘
```

### مثال آخر - حالة الاتصال (Adjacent States)

```
         upRange (مثال: كوع في Bicep Curl)
    ┌─────────────────────────────────────┐
    │                                     │
180°├─ DANGER ────────────────────────────┤
    │   (170-180)                         │
170°├─ WARNING ───────────────────────────┤
    │   (160-170)                         │
160°├─ PAD ───────────────────────────────┤
    │   (150-160)  ← متصل مع NORMAL       │
150°├─ NORMAL ────────────────────────────┤
    │   (140-150)  ← متصل مع PERFECT      │
140°├─ PERFECT ───────────────────────────┤
    │   (120-140)                         │
120°├─────────────────────────────────────┤
    │   TRANSITION                        │
```

**ملاحظة**: في هذا المثال الـ states متصلة (adjacent) وليست متداخلة (overlapping).
كلا النمطين صحيح طالما القاعدة الأساسية محققة: **كل طبقة تغطي الحدود الخارجية للطبقة الأضيق منها**.

### قواعد تحديد الـ TRANSITION
```
TRANSITION.min = أعلى Max في downRange (أيًا كان الـ state)
TRANSITION.max = أقل Min في upRange (أيًا كان الـ state)
```

**مثال:**
- إذا downRange فيه perfect(40-70), normal(30-80) → أعلى max = 80
- إذا upRange فيه perfect(130-150), pad(110-160) → أقل min = 110
- إذن TRANSITION = 80 → 110

### قواعد الأولوية (Priority)
```
1. DANGER     → أعلى أولوية (خطر)
2. WARNING    → تحذير
3. PERFECT    → أضيق مدى صحيح
4. NORMAL     → مدى صحيح متوسط
5. PAD        → أوسع مدى صحيح
6. TRANSITION → منطقة حركة (لا تقييم )
```

---

## 3. هيكل الـ Config الجديد

### ما سيُحذف
- `DifficultyRanges` (beginner/normal/advanced)
- `DifficultyType` enum
- `DifficultyLevel` من `ExerciseConfig`
- اختيار المستوى من UI
- `errorMessages` (tooLow/tooHigh) من `TrackedJoint`
- `feedbackMessages.common_mistake`

### إضافة StateRanges

بنية جديدة لكل joint:

```
TrackedJoint:
  - joint: "left_elbow"
  - role: primary/secondary
  - startPose: { min, max }
  - upRange: StateRanges       ← جديد
  - downRange: StateRanges     ← جديد
  - range: StateRanges         ← للـ secondary (hold)
  - stateMessages: StateMessages   ← رسالة واحدة لكل State
```

### بنية StateRanges

```
StateRanges:
  # الحالات المتداخلة (مطلوب على الأقل perfect)
  perfect: { min, max }        ← مطلوب (الأولوية 1)
  normal: { min, max }         ← اختياري (متداخل مع perfect)
  pad: { min, max }            ← اختياري (متداخل مع normal/perfect)
  
  # الحالات الخارجية (اختيارية)
  warning: { min, max }        ← اختياري (على الأطراف الخارجية)
  danger: { min, max }         ← اختياري (على الأطراف الخارجية)
```

### مثال JSON حقيقي

```json
{
  "joint": "left_elbow",
  "role": "primary",
  "startPose": { "min": 150, "max": 180 },
  "upRange": {
    "perfect": { "min": 130, "max": 150 },
    "normal": { "min": 120, "max": 160 },
    "pad": { "min": 110, "max": 160 },
    "warning": { "min": 160, "max": 170 },
    "danger": { "min": 170, "max": 180 }
  },
  "downRange": {
    "perfect": { "min": 40, "max": 70 },
    "normal": { "min": 30, "max": 80 },
    "warning": { "min": 10, "max": 30 },
    "danger": { "min": 0, "max": 10 }
  },
  "stateMessages": {
    "perfect": { "ar": "ممتاز!", "en": "Perfect!" },
    "normal": { "ar": "جيد", "en": "Good" },
    "pad": { "ar": "قريب من الحد", "en": "Near limit" },
    "warning": { "ar": "اثنِ أكثر", "en": "Bend more" },
    "danger": { "ar": "توقف! زاوية خطيرة", "en": "Stop! Dangerous angle" }
  }
}
```

### قواعد الـ Validation للـ Config

#### القاعدة الأساسية: التغطية الخارجية
كل طبقة أعلى (أوسع) **يجب أن تغطي الحدود الخارجية** للطبقة الأضيق منها.

#### قاعدة سلامة الانتقال (Transition Safety)
- يجب أن يكون `upRange.min` أكبر من `downRange.max` بمسافة آمنة (مثلاً 5 درجات).
- الهدف: تجنب Transition بالسالب أو الصفر، وضمان وجود منطقة حركة منطقية.
- إذا لم يتحقق الشرط ← رفض تحميل التمرين (ExerciseLoader error).

#### ترتيب الطبقات (من الأضيق للأوسع)
```
PERFECT → NORMAL → PAD → [TRANSITION] → WARNING → DANGER
```

#### قواعد التغطية (لـ upRange)

1. **`perfect`**: مطلوب دائمًا
   - مثال: `{ min: 130, max: 150 }`

2. **`normal`**: إذا موجود، يجب أن يمتد للخارج من `perfect`
   - `normal.max >= perfect.max` أو `normal.min <= perfect.min`
   - ✅ صحيح: perfect(130-150), normal(120-150) → normal يمتد لأسفل
   - ✅ صحيح: perfect(130-150), normal(130-160) → normal يمتد لأعلى
   - ✅ صحيح: perfect(130-150), normal(120-160) → normal يمتد للجهتين
   - ❌ خطأ: perfect(130-150), normal(140-160) → normal لا يغطي 130-140

3. **`pad`**: إذا موجود، يجب أن يمتد للخارج من `normal` (أو `perfect` إذا لا يوجد normal)
   - نفس المنطق: `pad.max >= normal.max` أو `pad.min <= normal.min`

4. **`warning`**: يجب أن يكون **خارج** كل الـ counted states (perfect/normal/pad)
   - لا تداخل مع أي منهم
   - مثال: إذا pad(110-160) → warning يبدأ من 160 أو ينتهي عند 110

5. **`danger`**: يجب أن يكون **خارج** `warning`
   - `danger.min >= warning.max` أو `danger.max <= warning.min`

#### مثال كامل صحيح
```json
"upRange": {
  "perfect": { "min": 130, "max": 150 },  // الأضيق
  "normal":  { "min": 120, "max": 160 },  // يمتد للخارج من perfect
  "pad":     { "min": 110, "max": 160 },  // يمتد للخارج من normal (من جهة min)
  "warning": { "min": 160, "max": 170 },  // خارج pad (بعد 160)
  "danger":  { "min": 170, "max": 180 }   // خارج warning
}
```

#### مثال خاطئ
```json
"upRange": {
  "perfect": { "min": 130, "max": 150 },
  "normal":  { "min": 140, "max": 160 },  // ❌ لا يغطي 130-140 من perfect
}
```

### حساب TRANSITION تلقائيًا
```
TRANSITION.max = upRange.pad.min (أو upRange.normal.min أو upRange.perfect.min)
TRANSITION.min = downRange.pad.max (أو downRange.normal.max أو downRange.perfect.max)
```
- لا يُعرّف في الـ JSON
- يُحسب runtime من الفجوة بين upRange و downRange

### Messages per State (مُحدّث)

**القرار النهائي للرسائل داخل `stateMessages`:**
- الرسائل أصبحت **مرتبطة بالـ Zone** في تمارين `up_down` و `push_pull`.
- تمارين **Hold** تستمر برسالة واحدة لكل State.
- **الرسائل اختيارية بالكامل**: ممكن تعريف رسالة في `up` فقط أو `down` فقط أو الاثنين أو لا شيء.
- لا يوجد `motivational/info/tip/correction/alert` داخل `stateMessages`.
- التصنيفات أصبحت **على مستوى الـ Feedback System** وليس داخل الـ JSON.

**صيغة Hold (رسالة واحدة لكل State):**
```
StateMessages:
  perfect: { ar: "ممتاز!", en: "Perfect!" }
  normal:  { ar: "جيد", en: "Good" }
  pad:     { ar: "أنت قريب من الحد", en: "Near limit" }
  warning: { ar: "اثنِ أكثر", en: "Bend more" }
  danger:  { ar: "انتبه! زاوية خطيرة", en: "Dangerous angle" }
```

**صيغة Up/Down (رسائل مختلفة حسب الـ Zone):**
```
StateMessages:
  perfect:
    up:   { ar: "ممتاز! ذراعك مفرودة", en: "Perfect! Arm extended" }
    down: { ar: "ممتاز! ثني مثالي", en: "Perfect! Great curl" }
  normal:
    up:   { ar: "جيد، حاول الفرد أكثر", en: "Good, try extending more" }
    down: { ar: "جيد، حاول الثني أكثر", en: "Good, try curling more" }
  pad:
    down: { ar: "مقبول، اقترب من الحد", en: "Acceptable, near limit" }
  warning:
    up:   { ar: "افرد أكثر", en: "Extend more" }
    down: { ar: "اثنِ أكثر", en: "Bend more" }
  danger:
    down: { ar: "توقف! زاوية خطيرة", en: "Stop! Dangerous angle" }
```

**ملاحظات مهمة:**
- `stateMessages` تُطلق كـ **MEDIUM priority**.
- `danger` تُعامل كـ **CRITICAL** (صوت + اهتزاز + visual دائم).
- `warning` أول مرة صوت ثم visual فقط.
- `pad` و `normal` و `perfect` رسائل خفيفة (visual/low audio).

---

### FeedbackMessages (رسائل عشوائية منخفضة الأولوية)

**القرار النهائي:**
- `feedbackMessages` تُستخدم فقط لملء الفراغ (حين لا توجد أخطاء/تحذيرات).
- تُطلق عشوائياً وبـ **LOW priority**.
- **لا تقاطع** أي رسالة أهم.

```
FeedbackMessages:
  motivational: [ ... ]   // تشجيع عام
  tips: [ ... ]            // نصائح عامة
```

**ملاحظات:**
- تم حذف `common_mistake`.
- تم توحيد المفتاح إلى `tips` بدلاً من `tip`.
---

## 4. التأثير على المكونات (Component Impact)

### 4.1 ExerciseConfig.kt
**التغييرات:**
- حذف `DifficultyType`, `DifficultyLevel`, `DifficultyRanges`
- إضافة `StateRanges` data class
- إضافة `StateMessages` data class
- تعديل `TrackedJoint` ليستخدم `StateRanges` بدل `DifficultyRanges`
- تعديل `RepCountingConfig` لإزالة ارتباطه بـ difficulty

**الملفات المتأثرة:**
- `training/models/ExerciseConfig.kt`

---

### 4.2 FormValidator.kt
**التغييرات:**
- حذف parameter `difficulty` من constructor
- إضافة `determineState()` يرجع `JointState` enum بناءً على:
  1. تحديد الـ ZoneType (UP_ZONE/DOWN_ZONE/TRANSITION)
  2. داخل الـ Zone، تحديد الـ State بأولوية (DANGER→WARNING→PERFECT→NORMAL→PAD)
- تعديل `validateJoint()` يستخدم State بدل Zone
- تعديل `getJointArrowInfos()` → `getJointStateInfos()` يرجع معلومات أغنى
- إضافة logic للـ `isRepCounted` و `invalidatesRep` من `StateConfig`
- **Safe Default للـ WARNING/DANGER:**
  - إذا لم يتم تعريف `DANGER` في الـ JSON ← أي زاوية خارج `WARNING` (أو `PAD`) تُعامل كـ `WARNING` مستمر.
  - الهدف: تجنب إطلاق إنذارات Danger خاطئة لزوايا غير معرّفة.

**منطق تحديد الـ State:**
```
1. إذا الزاوية في TRANSITION zone → return TRANSITION
2. تحديد أي zone (UP أو DOWN)
3. داخل الـ zone:
   - إذا في danger range → DANGER
   - إذا في warning range → WARNING
   - إذا في perfect range → PERFECT
   - إذا في normal range → NORMAL
   - إذا في pad range → PAD
   - غير ذلك → WARNING (fallback)
```

**الملفات المتأثرة:**
- `training/engine/FormValidator.kt`

---

### 4.3 PhaseStateMachine.kt
**التغييرات:**
- حذف parameter `difficulty` من constructor
- تعديل thresholds ليقرأ من StateRanges:
  - `upRangeMin` = أقل min في upRange (pad.min أو normal.min أو perfect.min)
  - `upRangeMax` = أعلى max في upRange (danger.max أو warning.max أو pad.max)
  - نفس المنطق لـ downRange
- **لا تغيير في منطق العدّ الأساسي** (Phase transitions تبقى كما هي)
- العدّ يعتمد على Phase فقط، الـ State يحدد هل يُحتسب أم لا

**ملاحظة مهمة:**
- PhaseStateMachine تحدد **متى** يكتمل التكرار (UP→START)
- FormValidator + StateConfig يحددان **هل** يُحتسب (بناءً على State)

**الملفات المتأثرة:**
- `training/engine/PhaseStateMachine.kt`

---

### 4.4 RepCounter.kt
**التغييرات:**
- تعديل `completeRep()` ليستقبل `worstState: JointState` من الـ Engine
- حساب `repScore` بناءً على **أسوأ State وصل إليه** (من STATE_CONFIGS)
- إضافة `isInvalidated: Boolean` للـ reps التي مرّت بـ DANGER
- تعديل `isCorrect` → `isCounted`: هل يُحتسب بناءً على `stateConfig.isRepCounted`
- **تحسين لتمارين الثبات (Hold):**
  - استخدام **Weighted Average** لحساب الـ Score بدلاً من Worst State.
  - المعادلة: `(TimeInPerfect * 1.0 + TimeInNormal * 0.6 + TimeInPad * 0.2) / TotalTime`
  - حالة `DANGER` تظل تفسد الثبات فوراً (Invalidate).

**منطق العدّ الجديد:**
```
عند اكتمال التكرار (من PhaseStateMachine):
1. إذا أي joint وصل DANGER خلال الـ rep → invalidated = true, score = 0
2. إذا أي joint وصل WARNING → score = 0, لا يُحتسب
3. غير ذلك (Rep-based) → score = min(rates of all joints at critical phases)
4. غير ذلك (Hold-based) → score = weighted average of time in states
```

**بنية جديدة:**
```
RepResult:
  - repNumber: Int
  - score: Float (0-100)         ← بدل isCorrect boolean
  - worstState: JointState       ← أسوأ حالة وصل إليها
  - isCounted: Boolean           ← هل يُحتسب
  - isInvalidated: Boolean       ← هل مرّ بـ DANGER
  - errors: List<...>            ← تفاصيل للـ report
  - phaseTimings: Map<...>
```

**الملفات المتأثرة:**
- `training/engine/RepCounter.kt`
- `training/models/WorkoutExecution.kt` (RepResult)

---

### 4.5 TrainingEngine.kt
**التغييرات:**
- حذف parameter `difficulty` من constructor
- تعديل initialization للـ components بدون difficulty
- إضافة tracking للـ `worstStateThisRep: JointState` لكل rep cycle
- تعديل `processFrame()`:
  ```
  1. استدعاء formValidator.getJointStateInfos()
  2. تحديث worstStateThisRep إذا الـ state الحالي أسوأ
  3. إذا أي joint في DANGER → emit FeedbackEvent.DangerDetected
  4. تمرير stateInfos للـ overlay
  5. عند rep completion → تمرير worstStateThisRep للـ RepCounter
  6. reset worstStateThisRep للـ rep التالي
  ```
- تعديل state flows:
  - `_arrowInfos` → `_jointStateInfos: StateFlow<Map<String, JointStateInfo>>`
  - إضافة `_dangerActive: StateFlow<Boolean>` للـ UI

**الملفات المتأثرة:**
- `training/TrainingEngine.kt`

---

### 4.6 SkeletonOverlayView.kt
**التغييرات:**
- تعديل `jointArrowInfos` → `jointStateInfos`
- تعديل `getGradientColorForPosition()` ليقرأ color من State مباشرة
- تبسيط منطق الألوان (State يحمل اللون معه)
- تعديل `calculateTargetColor()` ليستخدم State bands

**تبسيط متوقع:**
- حذف الكثير من الـ hardcoded color logic
- حذف `JointZone` enum (يُستبدل بـ `JointState`)

**الملفات المتأثرة:**
- `overlay/SkeletonOverlayView.kt`

---

### 4.7 LineRangeIndicator.kt
**التغييرات:**
- تعديل `drawColoredTrack()` ليرسم bands حسب States
- تعديل gradient ليعكس الـ 5 states (Green→Yellow→Orange→Red→DarkRed)
- تعديل `getColorForPosition()` ليستخدم State thresholds
- حذف الـ hardcoded `175°` و `5°` checks

**الملفات المتأثرة:**
- `overlay/LineRangeIndicator.kt`
- `overlay/ArcColorCalculator.kt` (إذا موجود)

---

### 4.8 FeedbackManager.kt
**التغييرات:**
- تعديل `handleJointError()` → `handleJointState()`
- الرسائل تُقرأ من `JointStateInfo.messages` بدل hardcoded
- إضافة handling للـ DANGER state (صوت/اهتزاز مميز)
- تعديل `MessageOrchestrator` categories لتشمل States

**الملفات المتأثرة:**
- `training/feedback/FeedbackManager.kt`
- `training/feedback/FeedbackEvent.kt`

---

### 4.9 PostTrainingReport.kt
**التغييرات:**
- تعديل `PerformanceSummary`:
  ```
  totalReps: Int              ← كل التكرارات
  countedReps: Int            ← التكرارات المحتسبة (PERFECT/NORMAL/PAD)
  invalidatedReps: Int        ← التكرارات الملغاة (DANGER)
  averageScore: Float         ← متوسط الـ scores
  stateBreakdown: Map<JointState, Int>  ← كم rep في كل state
  ```
- تعديل `RepTimelineEntry`:
  ```
  repNumber: Int
  score: Float               ← بدل isCorrect
  worstState: JointState     ← أسوأ حالة
  isCounted: Boolean
  isInvalidated: Boolean
  ```
- تعديل `PerformanceRating`:
  - يُحسب من `averageScore` + `countedReps/totalReps`
  - EXCELLENT: score >= 80 AND countedRatio >= 90%
  - GOOD: score >= 60 AND countedRatio >= 75%
  - FAIR: score >= 40 AND countedRatio >= 60%
  - NEEDS_WORK: else

**الملفات المتأثرة:**
- `training/report/PostTrainingReport.kt`
- `training/report/ReportGenerator.kt`

---

### 4.10 TrainingViewModel.kt
**التغييرات:**
- حذف `_difficulty` StateFlow
- حذف `parseDifficulty()`
- تعديل `createTrainingEngine()` بدون difficulty parameter
- تعديل state flows للـ UI

**الملفات المتأثرة:**
- `ui/training/TrainingViewModel.kt`

---

### 4.11 TrainingActivity.kt / UI
**التغييرات:**
- حذف difficulty selection UI
- تعديل exercise card/info display
- تعديل rep counter display (score بدل correct/incorrect)
- إضافة visual feedback للـ DANGER state

**الملفات المتأثرة:**
- `ui/TrainingActivity.kt`
- أي UI components تعرض difficulty

---

### 4.12 Exercise JSON Files
**التغييرات:**
- تحويل كل ملف JSON من البنية القديمة للجديدة
- حذف `difficultyLevels` section
- تعديل `trackedJoints` لاستخدام `StateRanges`

**الملفات المتأثرة:**
- `assets/exercises/*.json` (كل ملفات التمارين)

---

### 4.13 ExerciseLoader.kt
**التغييرات:**
- تعديل parsing للـ JSON schema الجديد
- حذف difficulty-related parsing

**الملفات المتأثرة:**
- `training/config/ExerciseLoader.kt`

---

### 4.14 Back-Admin (Dashboard)
**التغييرات:**
- تعديل Exercise form لإزالة difficulty levels
- إضافة UI لتعريف State bands
- تعديل preview/validation

**الملفات المتأثرة:**
- `Back-Admin/src/components/wizard/*`
- `Back-Admin/src/modules/exercises/*`

---

## 5. الـ Enums والـ Data Classes الجديدة

### JointState Enum (يستبدل JointZone)
```
enum JointState {
  PERFECT,      // المدى المثالي
  NORMAL,       // مدى جيد (متداخل مع perfect)
  PAD,          // مدى مقبول (متداخل مع normal)
  WARNING,      // تحذير (أطراف خارجية) - يستبدل ERROR القديم
  DANGER,       // خطر (أطراف خارجية)
  TRANSITION    // منطقة حركة بين up/down
}
```

### StateRanges Data Class
```
data class StateRanges(
  // مطلوب
  perfect: AngleRange,
  
  // اختياري - متداخل
  normal: AngleRange? = null,
  pad: AngleRange? = null,
  
  // اختياري - أطراف خارجية
  warning: AngleRange? = null,
  danger: AngleRange? = null
)
```

### StateConfig Data Class (للقرارات المرتبطة بكل State)
```
data class StateConfig(
  state: JointState,
  rate: Float,              // 0-100
  isRepCounted: Boolean,
  invalidatesRep: Boolean,  // للـ DANGER
  color: Int,
  severity: Severity        // none, low, medium, high
)

// Pre-defined configs
val STATE_CONFIGS = mapOf(
  PERFECT    → StateConfig(rate=100, isRepCounted=true,  invalidatesRep=false, color=GREEN,     severity=NONE),
  NORMAL     → StateConfig(rate=60,  isRepCounted=true,  invalidatesRep=false, color=YELLOW,    severity=NONE),
  PAD        → StateConfig(rate=20,  isRepCounted=true,  invalidatesRep=false, color=ORANGE,    severity=LOW),
  WARNING    → StateConfig(rate=0,   isRepCounted=false, invalidatesRep=false, color=LIGHT_RED, severity=MEDIUM),
  DANGER     → StateConfig(rate=0,   isRepCounted=false, invalidatesRep=true,  color=DARK_RED,  severity=HIGH),
  TRANSITION → StateConfig(rate=-1,  isRepCounted=false, invalidatesRep=false, color=BLUE_GRAY, severity=NONE)
)
```

### JointStateInfo Data Class (يستبدل JointArrowInfo)
```
data class JointStateInfo(
  jointCode: String,
  state: JointState,
  stateConfig: StateConfig,       // القرارات المرتبطة
  isPrimary: Boolean,
  currentAngle: Double,
  
  // للـ Overlay
  currentZone: ZoneType,          // UP_ZONE, DOWN_ZONE, TRANSITION
  stateRanges: StateRanges,       // للرسم
  
  // Resolved decisions
  rate: Float,                    // من stateConfig
  isRepCounted: Boolean,          // من stateConfig
  invalidatesRep: Boolean,        // للـ DANGER
  color: Int,                     // من stateConfig
  
  // Messages
  messages: List<LocalizedText>   // تُبنى من stateMessages (رسالة واحدة غالباً)
)
```

### ZoneType Enum (للتفريق بين Up/Down/Transition)
```
enum ZoneType {
  UP_ZONE,      // في upRange
  DOWN_ZONE,    // في downRange
  TRANSITION    // بينهما
}
```

---

## 6. القرارات التصميمية (Design Decisions)

### ✅ Q1: Secondary Joints و العدّ
**القرار**: نفس منطق PRIMARY:
- **WARNING**: خطأ لا يؤثر على العدّ (للتنبيه فقط)
- **DANGER**: يُفسد التكرار (مثل PRIMARY)
- **PERFECT/NORMAL/PAD**: تُعدّ بشكل طبيعي

### ✅ Q2: DANGER و إيقاف التمرين
**القرار**: 
- **لا يوقف التمرين** (المتدرب يقرر + قد يكون فيديو)
- **يُصدر تحذير شديد** (صوت + اهتزاز + لون أحمر غامق)
- **يُفسد التكرار الحالي** + أي تكرار تالي حتى يخرج من DANGER

### ✅ Q3: حساب الـ Rep Score
**القرار**:
- **للـ Rep-based Exercises:**
  - بناءً على **أسوأ State وصل إليه** خلال الـ phases المهمة (BOTTOM/EXTENDED).
  - PERFECT = 100%, NORMAL = 60%, PAD = 20%, WARNING/DANGER = 0%.
- **للـ Hold Exercises:**
  - **Weighted Average** للوقت المقضي في كل State.
  - `(TimePerfect * 1.0 + TimeNormal * 0.6 + TimePad * 0.2) / TotalTime`.
  - `DANGER` يفسد الثبات فوراً (Invalidate).

### ✅ Q4: Transition Zone
**القرار**:
- **TRANSITION منفصلة تمامًا** عن States
- **لا تُعدّ ولا تُقيَّم** (منطقة حركة طبيعية)
- **تُحسب تلقائيًا** من الفجوة بين upRange و downRange
- **WARNING/DANGER على الأطراف الخارجية فقط** (مش في TRANSITION)

### ✅ Q5: Hysteresis للـ States
**القرار**: نعم، نطبق hysteresis:
- بين NORMAL↔PAD: ~3°
- بين PAD↔WARNING: ~2°
- بين WARNING↔DANGER: ~2°
- الدخول لـ DANGER فوري (safety)، الخروج يحتاج hysteresis

### ✅ Q6: Messages Frequency
**القرار**: استخدام MessageOrchestrator الموجود مع تعديله:
- كل State له category في الـ orchestrator
- DANGER: always loud (لا يُكتم)
- WARNING: first loud, then quiet
- الباقي: حسب الإعدادات الحالية

---

## 7. خطوات التنفيذ المقترحة (Implementation Order)

### المرحلة 1: البنية التحتية
1. تعريف `JointState` enum
2. تعريف `StateRanges` data class
3. تعريف `JointStateInfo` data class
4. تعديل `ExerciseConfig.kt` models

### المرحلة 2: المحرك الأساسي
5. تعديل `FormValidator.kt` للـ State-based validation
6. تعديل `PhaseStateMachine.kt` لإزالة difficulty
7. تعديل `RepCounter.kt` للـ score-based counting
8. تعديل `TrainingEngine.kt` للتكامل

### المرحلة 3: الـ UI/Overlay
9. تعديل `SkeletonOverlayView.kt`
10. تعديل `LineRangeIndicator.kt`
11. تعديل `FeedbackManager.kt`

### المرحلة 4: التقارير
12. تعديل `PostTrainingReport.kt`
13. تعديل `ReportGenerator.kt`

### المرحلة 5: الواجهة
14. تعديل `TrainingViewModel.kt`
15. تعديل `TrainingActivity.kt`
16. حذف difficulty selection UI

### المرحلة 6: البيانات
17. تعديل `ExerciseLoader.kt`
18. تحويل ملفات JSON
19. تعديل Back-Admin dashboard

---

## 8. مخاطر وتحديات (Risks)

### R1: تعقيد ضبط التمارين
- **المشكلة**: 5 states + TRANSITION = أكثر من الـ 3 difficulties السابقة
- **الحل**: 
  - `perfect` فقط مطلوب، الباقي اختياري
  - أدوات visualization في Admin dashboard
  - templates للتمارين الشائعة

### R2: Backward Compatibility
- **المشكلة**: ملفات JSON القديمة لن تعمل
- **الحل**: تحويل يدوي (مقبول في مرحلة التطوير)

### R3: DANGER False Positives
- **المشكلة**: زاوية لحظية قد تُصنّف خطأً كـ DANGER
- **الحل**: 
  - require consecutive frames (minDangerFrames = 3)
  - hysteresis للخروج من DANGER

### R4: Performance
- **المشكلة**: حسابات أكثر per frame
- **الحل**: 
  - lookup tables / pre-computed thresholds
  - STATE_CONFIGS ثابتة (لا تُحسب runtime)

### R5: Overlap Validation
- **المشكلة**: المستخدم قد يعرّف ranges متناقضة
- **الحل**: 
  - validation في ExerciseLoader
  - validation في Admin dashboard
  - error messages واضحة

---

## 9. معايير النجاح (Success Criteria)

### إلغاء Difficulty
- [ ] لا يوجد `DifficultyType` enum
- [ ] لا يوجد `DifficultyRanges` في أي config
- [ ] لا يوجد difficulty selection في UI
- [ ] لا يوجد difficulty parameter في أي constructor

### Single Source of Truth
- [ ] كل قرار (عدّ، لون، رسالة) يأتي من `JointStateInfo` + `StateConfig`
- [ ] `STATE_CONFIGS` ثابتة ومركزية
- [ ] لا يوجد hardcoded colors/thresholds في overlay

### العدّ والتقييم
- [ ] Rep يُحتسب بناءً على `stateConfig.isRepCounted`
- [ ] Rep يُلغى إذا `stateConfig.invalidatesRep` (DANGER)
- [ ] Score يُحسب من `worstState` خلال الـ phases المهمة
- [ ] التقرير يعرض `countedReps/totalReps` + `averageScore`

### الـ Visual Feedback
- [ ] الـ overlay يعكس الـ 6 states بألوان واضحة
- [ ] DANGER له تأثير بصري مميز (flash/pulse)
- [ ] TRANSITION له لون محايد (gray/blue)

### ملفات التمارين
- [ ] JSON schema جديد يعمل
- [ ] Validation للـ overlapping ranges
- [ ] Config أبسط (perfect مطلوب، الباقي اختياري)

---

## 10. ملاحظات إضافية

### علاقة بـ Position Checks
- الـ Position Checks (knee-over-toe, alignment) تبقى منفصلة حاليًا
- لكن يمكن أن يكون لها نفس الـ severity levels
- **مستقبلًا**: توحيد severity levels مع JointState

### علاقة بـ Hold Exercises
- نفس المنطق ينطبق
- SECONDARY joints تستخدم single StateRanges (range field)
- الـ Hold timer لا يتأثر (يعمل بناءً على phase)
- Form quality يُحسب من `worstState` خلال الـ hold

### Hysteresis Implementation
- تُطبّق في `FormValidator.determineState()`
- تُخزّن `previousState` لكل joint
- قواعد:
  - الدخول لـ DANGER: فوري (safety first)
  - الخروج من DANGER: يحتاج ~3 frames أو hysteresis degrees
  - بين باقي الـ states: hysteresis ~2-3°

### Edge Cases
1. **Joint بدون normal/pad**: يُعامل كـ PERFECT فقط، أي شيء خارجه WARNING
2. **Joint بدون warning/danger**: لا يوجد تحذير خارجي، فقط PAD→TRANSITION
3. **TRANSITION = 0** (upRange.min = downRange.max): لا توجد منطقة انتقال

---

## 11. ملخص التغييرات بالأرقام

| Component | Files | Estimated Changes |
|-----------|-------|-------------------|
| Models | 2 | Major restructure |
| Engine | 5 | Medium refactor |
| Overlay | 3 | Medium refactor |
| Feedback | 2 | Small updates |
| Report | 2 | Medium refactor |
| UI | 3 | Remove difficulty |
| Config/Loader | 2 | Major restructure |
| JSON files | ~10 | Full rewrite |
| Admin Dashboard | ~5 | Major restructure |

**المجموع**: ~34 ملف متأثر

---

---

## 12. سجل التنفيذ (Implementation Log)

### التنفيذ الأول: 2026-01-13

#### المرحلة 1: البنية التحتية ✅
| الملف | التغييرات |
|-------|----------|
| `JointState.kt` | ✅ إنشاء: `JointState` enum, `StateConfig`, `StateRanges`, `JointStateInfo`, `ZoneType`, `StateMessages` |
| `ExerciseConfig.kt` | ✅ إزالة `DifficultyType/Level/Ranges`, إضافة `StateRanges` للـ `TrackedJoint` |

#### المرحلة 2: المحرك الأساسي ✅
| الملف | التغييرات |
|-------|----------|
| `FormValidator.kt` | ✅ State-based validation مع hysteresis بالدرجات |
| `PhaseStateMachine.kt` | ✅ إزالة difficulty, استخدام `StateRanges.getOuterMin/Max()` |
| `RepCounter.kt` | ✅ Score-based counting, `worstState`, `isCounted`, `isInvalidated` |
| `TrainingEngine.kt` | ✅ تكامل كامل، إزالة difficulty، تتبع `worstStateThisRep` |
| `WorkoutExecution.kt` | ✅ `RepResult` جديد مع score/worstState، `WorkoutRunSummary` محدث |
| `PositionValidator.kt` | ✅ إزالة difficulty |
| `FeedbackEvent.kt` | ✅ `RepCompleted` مع score/worstState، `DangerDetected` جديد |

#### المرحلة 3-6: UI و Loaders ✅
| الملف | التغييرات |
|-------|----------|
| `TrainingViewModel.kt` | ✅ إزالة difficulty fields و parsing |
| `ExerciseDetailActivity.kt` | ✅ إزالة اختيار الصعوبة، عرض StateRanges.perfect |
| `WorkoutActivity.kt` | ✅ إزالة difficulty |
| `WorkoutRunner.kt` | ✅ إزالة defaultDifficulty |
| `WorkoutTrainingEngine.kt` | ✅ إزالة difficulty من constructor |
| `PostTrainingReport.kt` | ✅ إزالة difficulty field |
| `ReportGenerator.kt` | ✅ إزالة difficulty من report creation |
| `VideoModeController.kt` | ✅ إزالة difficulty parameter |
| `VideoAnalysisResult.kt` | ✅ إزالة difficulty field |
| `WorkoutConfig.kt` | ✅ إزالة `WorkoutExercise.difficulty` |

#### تحسينات ما بعد التنفيذ ✅
| التحسين | الوصف |
|---------|------|
| **Duration Tracking** | ✅ إضافة `workoutStartTimeMs`, `totalPausedDurationMs` في `TrainingEngine` لحساب المدة الفعلية |
| **Proper Hysteresis** | ✅ تطبيق hysteresis بالدرجات (3°/2°/2°) في `FormValidator.applyHysteresis()` |
| **Safety-First Transitions** | ✅ التدهور (getting worse) يُؤكَّد فوراً، التحسّن يحتاج margin |
| **Hold Exercises Support** | ✅ إضافة دعم لحساب الـ Weighted Score للتمارين الثابتة (Perfect=1.0, Normal=0.6, Pad=0.2) |
| **Overlap Resolution** | ✅ تعديل `determineState` ليعطي الأولوية للحالة الأفضل (Perfect) في حالة التداخل |
| **Config Validation** | ✅ إضافة validation في `ExerciseLoader` لاكتشاف التداخلات ومشاكل الـ ranges |
| **Danger Throttling** | ✅ إضافة throttling للـ Danger Events لمنع تكرار التنبيهات كل frame |
| **State Flows** | ✅ إضافة `jointStateInfos` و `isDangerActive` flows للـ UI الجديد |

#### الملفات المتبقية للتحديث اللاحق
- [ ] ملفات JSON للتمارين (تحتاج تحويل من `upRange/downRange` القديمة إلى `StateRanges`)
- [ ] Admin Dashboard (تحديث واجهة إنشاء التمارين)

### معايير النجاح المحققة ✅

#### إلغاء Difficulty
- [x] لا يوجد `DifficultyType` enum في الكود التشغيلي
- [x] لا يوجد `DifficultyRanges` في أي config
- [x] لا يوجد difficulty selection في UI
- [x] لا يوجد difficulty parameter في أي constructor

#### Single Source of Truth
- [x] كل قرار (عدّ، لون، رسالة) يأتي من `JointStateInfo` + `StateConfig`
- [x] `STATE_CONFIGS` ثابتة ومركزية في `JointState.kt`
- [x] Legacy wrappers للـ overlay backward compatibility

#### العدّ والتقييم
- [x] Rep يُحتسب بناءً على `stateConfig.isRepCounted`
- [x] Rep يُلغى إذا `stateConfig.invalidatesRep` (DANGER)
- [x] Score يُحسب من `worstState` خلال الـ phases المهمة
- [x] `WorkoutRunSummary` يعرض `countedReps/totalReps` + `averageScore`

### تحديث إضافي على الخطة
- ✅ تمت إضافة دعم رسائل **Up/Down لكل State** في تمارين `up_down` و `push_pull`.
- ✅ تم الإبقاء على رسالة واحدة لكل State في تمارين **Hold**.
- ✅ الرسائل اختيارية ويمكن تعريف `up` فقط أو `down` فقط أو كلاهما أو لا شيء.

---

*آخر تحديث: 2026-01-13*
*الحالة: ✅ IMPLEMENTED - Phase 1 Complete*
