# Setup Pose Enhancement Plan
## "ساعد المستخدم، ما تمنعوش"

> **الهدف:** تحويل مرحلة ما قبل التمرين من "حارس بوابة" يمنع المستخدم إلى "مرشد ذكي" يساعده يوصل للوضع الصحيح بسلاسة وسرعة.

---

## 1. تحليل الوضع الحالي

### 1.1 المسار الحالي (SETUP_POSE → COUNTDOWN → TRAINING)

```
loadExercise()
    ↓ [IDLE → SETUP_POSE]
لوحة نصية تعرض:
    "Get into starting position:"
    "● Left Knee: 150° - 175°"
    "○ Right Elbow: 140° - 170°"
    ↓
كل فريم → PoseValidator.validate()
    → لازم كل المفاصل (primary + secondary) في النطاق
    → 10 فريم متتالية صحيحة بالكامل (فريم واحد غلط = صفر)
    ↓ [SETUP_POSE → COUNTDOWN]
CountDownTimer يبدأ: 3 → 2 → 1 → GO!
    → لو فريم واحد invalid أثناء العد → إلغاء فوري
    ↓ [COUNTDOWN → TRAINING]
TrainingEngine.start()
```

### 1.2 المشاكل الحالية

| # | المشكلة | التأثير على المستخدم |
|---|---------|---------------------|
| 1 | **لا يوجد إرشاد كاميرا** - `CameraPositionDetector` لا يُستخدم في `SETUP_POSE` | المستخدم ممكن يبدأ التمرين بزاوية كاميرا غلط → نتائج غير دقيقة |
| 2 | **10 فريم متتالية بدون أي تسامح** - فريم واحد glitch = صفر | إحباط شديد: المستخدم يفضل يحاول يثبت والنظام يرفض |
| 3 | **نص بالإنجليزي فقط** - "Get into starting position", "Not visible" | التطبيق يدعم العربية لكن شاشة الإعداد لا |
| 4 | **أرقام زوايا جافة** - "150° - 175°" بدون سياق | المستخدم مش فاهم يعمل إيه بالأرقام دي |
| 5 | **Skeleton كامل** أثناء الإعداد - كل الجسم مرسوم | تشتيت: المستخدم مش عارف يركز على إيه |
| 6 | **لا إرشاد صوتي** أثناء الإعداد | المستخدم بعيد عن الموبايل ومش قادر يقرأ النص الصغير |
| 7 | **Countdown يلغي فوراً** على أي خلل | كل ما يبدأ العد ويتحرك شعرة → يرجع من الأول |
| 8 | **تأخير بعد GO!** - `onCountdownFinished()` ينتظر animation | فريمات ضائعة بين "GO!" وبداية التدريب الفعلي |

---

## 2. التصميم الجديد

### 2.1 الفلسفة الأساسية

```
❌ القديم: "أنت غلط في 3 مفاصل. ما تقدرش تبدأ."
✅ الجديد: "قريب! اخفض كتفك الأيسر شوية وهتبدأ."
```

**3 مبادئ:**
1. **لا تمنع، أرشد** - الكاميرا مش في المكان الصح؟ → tip ودي بدل حظر
2. **تسامح مع الضوضاء** - Rolling window (9 من 12) بدل 10 من 10 متتالية
3. **المستخدم بعيد** - أرقام كبيرة، ألوان واضحة، صوت يرشد

### 2.2 المسار الجديد

```
loadExercise()
    ↓ [IDLE → SETUP_POSE]

╔══════════════════════════════════════════════════╗
║              SETUP_POSE الجديد                   ║
║                                                  ║
║  1. Camera Check (كل 12 فريم)                   ║
║     → Soft tip في مكان الـ Form                 ║
║                                                  ║
║  2. Joint Guidance (كل فريم)                    ║
║     → Skeleton مبسّط (المفاصل المطلوبة فقط)     ║
║     → أرقام كبيرة + ألوان (أخضر/أصفر/أحمر)     ║
║     → صوت يرشد (أسوأ مفصل)                      ║
║                                                  ║
║  3. Confirmation (Rolling Window 9/12)           ║
║     → Progress bar يمتلئ تدريجياً                ║
║     → لما يوصل 100% → COUNTDOWN تلقائي         ║
╚══════════════════════════════════════════════════╝

    ↓ [SETUP_POSE → COUNTDOWN]

╔══════════════════════════════════════════════════╗
║           COUNTDOWN المتسامح                     ║
║                                                  ║
║  - 1-2 فريم invalid → تجاهل                     ║
║  - 3+ فريم متتالية → freeze + تحذير             ║
║  - 5+ فريم متتالية → إلغاء → SETUP_POSE        ║
╚══════════════════════════════════════════════════╝

    ↓ [COUNTDOWN → TRAINING]
TrainingEngine.start() ← فوراً مع الـ GO! بدون انتظار animation
```

---

## 3. التصميم التفصيلي

### 3.1 الطبقة 1: إرشاد الكاميرا (Camera Position Guidance)

#### الفكرة
التمرين يحدد `cameraPosition: "side_view"` أو `"front_view"`. حالياً هذا الإعداد يُستخدم فقط أثناء التدريب في `PositionValidator`. نريد استخدامه أثناء `SETUP_POSE` كـ **إرشاد ودي**.

#### آلية العمل

```
CameraPositionDetector.detect(landmarks) → DetectedCameraPosition
    ↓
مقارنة مع exerciseConfig.poseVariant.cameraPosition
    ↓
النتيجة تظهر في مكان الـ FORM card أسفل الشاشة
```

#### الفحص بنظام Rolling Window

```
كل فريم → أضف نتيجة الكشف للـ window
نافذة من 12 فريم: [S S F S S S S F S S S S]
                     (S = side_view, F = front_view)
                     
Count(side_view) = 10 من 12 → confirmed: side_view ✅
Count(front_view) = 2 من 12 → noise, ignored

القرار: لو 9+ من 12 فريم متفقين → تأكيد الوضع
```

**ليه Rolling Window وليس كل فريم؟**
- `CameraPositionDetector` ممكن يعطي نتيجة متذبذبة (DIAGONAL / SIDE) لو المستخدم بيتحرك
- الفحص كل 12 فريم يوفر نتيجة مستقرة بدون وميض

#### العرض على الشاشة

مكان كارت الـ **FORM** أسفل الشاشة (بجانب TIME وزر Stop):

```
┌─────────────┐    ┌──────┐    ┌─────────────┐
│   TIME      │    │ ⏹️  │    │    VIEW     │
│   00:00     │    │      │    │  ✓ جانبي   │  ← أخضر
└─────────────┘    └──────┘    └─────────────┘

                أو:

┌─────────────┐    ┌──────┐    ┌─────────────┐
│   TIME      │    │ ⏹️  │    │    VIEW     │
│   00:00     │    │      │    │ ↻ أدر جانبي│  ← أصفر/برتقالي
└─────────────┘    └──────┘    └─────────────┘
```

**الحالات:**

| الحالة | اللون | النص (عربي) | النص (إنجليزي) |
|--------|-------|-------------|----------------|
| مطابق | أخضر `#00E676` | `✓ جانبي` / `✓ أمامي` | `✓ Side` / `✓ Front` |
| غير مطابق | برتقالي `#FFA726` | `↻ أدر جانبي` / `↻ أدر أمامي` | `↻ Turn Side` / `↻ Turn Front` |
| DIAGONAL | أصفر `#FFD54F` | `↻ عدّل الزاوية` | `↻ Adjust angle` |
| UNKNOWN | رمادي `#9E9E9E` | `📐 جاري الكشف...` | `📐 Detecting...` |

**القواعد:**
- لا يمنع البدء أبداً (soft tip فقط)
- يختفي تلقائياً لما الكاميرا تكون صح
- لا يظهر أصلاً لو التمرين ما يحددش `cameraPosition`

---

### 3.2 الطبقة 2: إرشاد المفاصل (Joint Guidance)

#### 3.2.1 Skeleton مبسّط (Tracked Joints Only)

**الحالي أثناء `SETUP_POSE`:**
- `isTrainingMode = false` → يرسم **كل** الـ 33 landmark + كل الخطوط
- كل الجسم ملوّن بلون واحد - المستخدم مش عارف يركز فين

**الجديد أثناء `SETUP_POSE`:**
- وضع خاص: `isSetupMode = true`
- يرسم **فقط** المفاصل المتعلقة بالتمرين وخطوطها
- كل مفصل مطلوب عليه دايرة ملوّنة حسب حالته
- الخطوط بين المفاصل ملوّنة بلون المفصل الرئيسي

#### مثال: تمرين Bicep Curl

```
المفاصل المطلوبة: left_elbow (primary), left_shoulder (secondary)
Landmarks المرتبطة: 11 (shoulder), 13 (elbow), 15 (wrist)

الرسم:
                    
    (11)●────────●(13)────────●(15)
   shoulder    elbow        wrist
   
   حيث:
   - ●(13) = دايرة كبيرة ملوّنة (primary joint)
   - ●(11), ●(15) = دوائر أصغر
   - الخطوط ملوّنة بلون حالة الـ elbow
```

#### 3.2.2 الألوان الثلاثة

| الحالة | اللون | الشرط | المعنى |
|--------|-------|-------|--------|
| **أخضر** | `#00E676` | الزاوية ضمن `startPose.min - max` | ممتاز، استمر |
| **أصفر** | `#FFD54F` | خارج النطاق بـ ≤ 15° | قريب، عدّل قليلاً |
| **أحمر** | `#FF5252` | خارج النطاق بـ > 15° | بعيد، يحتاج تعديل |

**عتبة الـ "قريب" (CLOSE_THRESHOLD):**
```
CLOSE_THRESHOLD = 15.0°   // قابل للتعديل في app_settings.json
```

**الحساب:**
```kotlin
val distance = if (angle < startPose.min) {
    startPose.min - angle   // كم درجة تحت النطاق
} else if (angle > startPose.max) {
    angle - startPose.max   // كم درجة فوق النطاق
} else {
    0.0                     // في النطاق
}

val status = when {
    distance == 0.0 -> GREEN
    distance <= CLOSE_THRESHOLD -> YELLOW  
    else -> RED
}
```

#### 3.2.3 عرض الزوايا على الشاشة (أرقام كبيرة)

**مبدأ التصميم:** المستخدم واقف على بعد 2-3 متر من الموبايل. لازم يشوف:
1. **لون** المفصل (أخضر/أصفر/أحمر) من بعيد
2. **رقم** الزاوية الحالية بخط كبير
3. **اتجاه** التعديل (سهم أو رمز)

**الرسم على الـ SkeletonOverlay:**

```
لكل مفصل مطلوب (primary + secondary):

    ┌────────┐
    │  142°  │ ← رقم الزاوية (خط كبير 32sp+)
    │   ↑    │ ← سهم اتجاهي (لو مش في النطاق)
    └────────┘
        │
        ●  ← دايرة المفصل (ملوّنة)
       / \
      /   \
```

**تفاصيل الرسم:**
- الرقم يظهر فوق المفصل (أو بجانبه لو فيه تداخل)
- حجم الخط: `32sp` على الأقل (مرئي من بعيد)
- خلفية شفافة سوداء خلف الرقم عشان يبان على أي خلفية
- **في حالة الأخضر:** الرقم يظهر بدون سهم (فقط `✓` صغيرة أو بدون شيء)
- **في حالة الأصفر/الأحمر:** يظهر سهم `↑` أو `↓` بجانب الرقم

**الأسهم الاتجاهية:**

| الحالة | السهم | المعنى |
|--------|-------|--------|
| الزاوية أقل من `min` | `↑` | "ارفع" / "افتح أكثر" |
| الزاوية أعلى من `max` | `↓` | "اخفض" / "ضم أكثر" |
| في النطاق | لا سهم | "ممتاز" |

---

### 3.3 الطبقة 3: الإرشاد الصوتي (Voice Guidance)

#### الفكرة
المستخدم بعيد عن الموبايل → الصوت هو الوسيلة الأساسية للإرشاد.

#### آلية العمل

```
كل دورة validation (بعد ما الـ rolling window يتحدث):

1. جمع كل المفاصل الغير صحيحة
2. رتبهم حسب الأسوأ (الأبعد عن النطاق أولاً)
3. انطق إرشاد لأسوأ مفصل فقط (مش كلهم)
4. Cooldown: مش أقل من 2 ثانية بين كل رسالة
```

#### الرسائل الصوتية

| المفصل | الاتجاه | الرسالة (عربي) | الرسالة (إنجليزي) |
|--------|---------|----------------|-------------------|
| left_elbow | ↑ raise | "ارفع الكوع الأيسر أكثر" | "Raise your left elbow more" |
| left_elbow | ↓ lower | "اخفض الكوع الأيسر أكثر" | "Lower your left elbow more" |
| left_knee | ↑ raise | "افرد الركبة اليسرى أكثر" | "Straighten your left knee more" |
| left_knee | ↓ lower | "اثني الركبة اليسرى أكثر" | "Bend your left knee more" |
| left_shoulder | ↑ raise | "ارفع الكتف الأيسر" | "Raise your left shoulder" |
| left_shoulder | ↓ lower | "اخفض الكتف الأيسر" | "Lower your left shoulder" |
| spine | ↑ raise | "افرد ظهرك أكثر" | "Straighten your back more" |
| spine | ↓ lower | "انحني أكثر" | "Bend forward more" |
| left_hip | ↑ raise | "افرد الورك الأيسر" | "Extend your left hip" |
| left_hip | ↓ lower | "اثني الورك الأيسر" | "Bend your left hip" |

**ملاحظات:**
- الرسائل اللي فوق هي defaults - كل تمرين يقدر يحدد رسائل مخصصة عبر `startPoseMessages` في الـ JSON
- نفس المنطق بيشتغل مع `right_*` variants
- الأولوية: PRIMARY joints أولاً، بعدين SECONDARY

#### متى ينطق الصوت؟

```
Cooldown = 2500ms (قابل للتعديل في app_settings.json)

Timeline:
t=0s    → أول فحص → "ارفع الكوع الأيسر أكثر"
t=2.5s  → لو لسه نفس المشكلة → "ارفع الكوع الأيسر أكثر" (تكرار)
t=5s    → لو المشكلة اتحلت لكن فيه مشكلة تانية → "اخفض الركبة أكثر"
t=7.5s  → لو كل شيء أخضر → لا صوت (أو "ممتاز، استمر" مرة واحدة)
```

#### عند اقتراب التأكيد

```
لما الـ progress يوصل 75%+ وكل المفاصل أخضر:
    → "ثابت، جاري البدء..." (مرة واحدة)
```

---

### 3.4 الطبقة 4: تأكيد الوضعية (Rolling Window Confirmation)

#### المفهوم

بدل "10 فريم متتالية صحيحة بالكامل":

```
نافذة آخر 12 فريم:
[✅ ✅ ✅ ❌ ✅ ✅ ✅ ✅ ✅ ❌ ✅ ✅]

عدد الصحيحة = 10 من 12
الحد المطلوب = 9 من 12 (75%)
→ Confirmed! ✅

التالي: COUNTDOWN
```

#### الإعدادات (في `app_settings.json`)

```json
{
  "setupValidation": {
    "windowSize": 12,
    "requiredValid": 9,
    "closeThresholdDegrees": 15.0,
    "voiceCooldownMs": 2500,
    "cameraTipEnabled": true,
    "cameraCheckWindowSize": 12,
    "cameraCheckRequired": 9
  }
}
```

#### Progress Bar

```
BOTTOM STATS BAR أثناء SETUP_POSE:

┌─────────────┐    ┌──────┐    ┌─────────────┐
│   READY     │    │ ⏹️  │    │    VIEW     │
│ ━━━━━━━━░░░ │    │      │    │  ✓ جانبي   │
│    75%      │    │      │    │             │
└─────────────┘    └──────┘    └─────────────┘

حيث:
- كارت TIME يتحول لـ "READY" progress
- يعرض progress bar + نسبة مئوية
- لما يوصل 100% → COUNTDOWN تلقائياً
```

**تصميم الـ Progress:**
- الشريط يمتلئ تدريجياً (smooth animation)
- اللون يتدرج: أحمر → أصفر → أخضر كلما اقترب من 100%
- لما ينزل (فريمات غلط) → ينقص بسلاسة (مش يرجع صفر فجأة)

---

### 3.5 الطبقة 5: العد التنازلي المتسامح

#### المشكلة الحالية

```kotlin
// handleCountdown في WorkoutRunSupervisor:
is SupervisorSignal.PoseInvalid -> {
    // فريم واحد → إلغاء فوري!
    transitionTo(WorkoutRunState.SETUP_POSE)
    emit(SupervisorAction.CancelCountdown)
}
```

#### الحل: 3 مستويات من التسامح

```
أثناء COUNTDOWN (3-2-1):

عداد invalidFrames = 0

كل فريم:
  لو valid → invalidFrames = 0 ← reset
  لو invalid → invalidFrames++

  switch (invalidFrames):
    case 1-2:
      → تجاهل (ضوضاء كاميرا)
      → العد يستمر بشكل طبيعي

    case 3-4:
      → ⏸ تجميد العد (freeze)
      → عرض تحذير: "ارجع للوضع الصحيح"
      → الرقم يظل ثابت (مثلاً يظل "2")
      → لو المستخدم رجع → يكمل العد من حيث وقف

    case 5+:
      → إلغاء العد
      → رجوع لـ SETUP_POSE
      → صوت: "الوضعية تغيرت، حاول تاني"
```

#### إصلاح دقة العد

```
// الحالي (ممكن يعرض رقم خطأ):
currentValue = (millisUntilFinished / 1000).toInt() + 1

// الجديد (دقيق):
currentValue = ((millisUntilFinished + 999) / 1000).toInt()
```

#### إصلاح تأخير بعد GO!

```
// الحالي: ينتظر animation تخلص
AnimationUtils.animateGoText(binding.tvCountdown) {
    viewModel.onCountdownFinished()  // ← متأخر
}

// الجديد: يبدأ فوراً + animation بالتوازي
viewModel.onCountdownFinished()  // ← فوراً
AnimationUtils.animateGoText(binding.tvCountdown)  // ← تجميلي فقط
```

---

## 4. الـ Skeleton Overlay أثناء SETUP_POSE

### 4.1 الوضع الجديد: `isSetupMode`

حالياً `SkeletonOverlayView` عنده وضعين:
- `isTrainingMode = false` → Skeleton كامل (33 نقطة + كل الخطوط)
- `isTrainingMode = true` → Range Indicators + Glowing Joints

**الجديد:** وضع ثالث:
- `isSetupMode = true` → **فقط المفاصل المطلوبة** + دوائر ملوّنة + أرقام كبيرة

### 4.2 ما يُرسم في `isSetupMode`

```
لكل tracked joint في exerciseConfig:

1. دايرة على المفصل الرئيسي (Joint Circle):
   - نصف قطر: 18dp (أكبر من العادي)
   - لون: أخضر/أصفر/أحمر حسب الحالة
   - حدود: 3dp stroke + fill شفاف 40%
   - Primary joints: حجم أكبر (22dp)
   - Secondary joints: حجم أصغر (14dp)

2. خطوط بين landmarks المرتبطة:
   - مثال: elbow → shoulder (line1), elbow → wrist (line2)
   - لون الخط = لون حالة المفصل
   - سمك: 6dp (واضح من بعيد)
   - opacity: 80% (واضح لكن مش مبالغ)

3. رقم الزاوية (Angle Label):
   - موقع: فوق المفصل بـ 40dp
   - حجم الخط: 34sp (كبير ومرئي من بعيد)
   - لون الخط: أبيض
   - خلفية: مستطيل أسود شفاف 60% بزوايا مدورة
   - يتضمن: الرقم + سهم اتجاهي (لو مش في النطاق)
   
4. سهم اتجاهي (Direction Arrow):
   - يظهر فقط في حالة أصفر/أحمر
   - ↑ (raise) أو ↓ (lower)
   - يرسم كسهم بسيط بجانب الرقم
   - اللون: نفس لون الحالة
```

### 4.3 مثال بصري: تمرين Squat

```
التمرين يتتبع: left_knee (primary), left_hip (secondary)

الشاشة (SETUP_POSE):

         ┌────────┐
         │ 165° ✓ │  ← hip angle (أخضر)
         └────────┘
              ●(23)  ← hip joint (دايرة خضراء صغيرة)
             /
            /  ← خط أخضر
           /
     ┌─────────┐
     │ 142° ↓  │  ← knee angle (أصفر - قريب)
     └─────────┘
          ●(25)  ← knee joint (دايرة صفراء كبيرة)
         / \
        /   \  ← خطوط صفراء
       /     \
```

### 4.4 ما لا يُرسم

- باقي الـ 33 landmark (لا نقاط ولا خطوط)
- لا glow effects
- لا range indicators (arc أو line)
- لا position error indicators

الهدف: **بساطة مطلقة** - المستخدم يشوف فقط المفاصل اللي محتاج يعدلها.

---

## 5. واجهة المستخدم (UI Layout)

### 5.1 أثناء SETUP_POSE

```
┌──────────────────────────────────────────────┐
│  [X Finish]    Bicep Curl    [⚙]            │  ← topBar (موجود)
│                                              │
│                                              │
│           ┌────────┐                         │
│           │ 142° ↑ │                         │
│           └────────┘                         │
│                ● ←── المفاصل المطلوبة فقط    │
│               / \                            │
│              /   \                           │
│                                              │
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │ 🎯 Setup Position                    │    │  ← setupPosePanel (محسّن)
│  │                                      │    │
│  │  ● L.Elbow    142° ↑ ارفع أكثر      │    │  ← per-joint row (ملوّن)
│  │  ● L.Shoulder 168° ✓                 │    │  ← أخضر = ممتاز
│  │                                      │    │
│  │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │    │  ← progress bar
│  │  جاهز بنسبة 75%                     │    │
│  └──────────────────────────────────────┘    │
│                                              │
│  ┌──────┐    ┌──────┐    ┌──────────────┐   │
│  │READY │    │  ⏹️  │    │    VIEW      │   │  ← bottomStatsBar
│  │ 75%  │    │      │    │  ✓ جانبي    │   │
│  └──────┘    └──────┘    └──────────────┘   │
└──────────────────────────────────────────────┘
```

### 5.2 أثناء COUNTDOWN

```
┌──────────────────────────────────────────────┐
│                                              │
│                                              │
│               ██████████                     │
│            ███ استعد!  ███                   │
│               ██████████                     │
│                                              │
│                                              │
│                  ┌───┐                       │
│                  │ 3 │  ← رقم كبير          │
│                  └───┘                       │
│                                              │
│                                              │
│                                              │
│                                              │
│  ┌──────┐    ┌──────┐    ┌──────────────┐   │
│  │ TIME │    │  ⏹️  │    │    VIEW      │   │
│  │00:00 │    │      │    │  ✓ جانبي    │   │
│  └──────┘    └──────┘    └──────────────┘   │
└──────────────────────────────────────────────┘
```

### 5.3 أثناء TRAINING (لا تغيير)

```
Bottom bar يرجع لشكله الطبيعي:
┌──────┐    ┌──────┐    ┌──────────────┐
│ TIME │    │  ⏹️  │    │    FORM      │
│01:23 │    │      │    │   ممتاز     │
└──────┘    └──────┘    └──────────────┘
```

---

## 6. هيكل الكود

### 6.1 الملفات الجديدة

```
android-poc/app/src/main/java/com/trainingvalidator/poc/ui/training/
├── PoseValidator.kt          → يُعاد بناؤه كـ PoseSetupGuide.kt
└── CountdownController.kt    → يُحدّث (إصلاحات)

New file:
├── PoseSetupGuide.kt         → يحل محل PoseValidator
```

### 6.2 `PoseSetupGuide.kt` (يحل محل `PoseValidator.kt`)

```
class PoseSetupGuide:
    ├── Rolling window confirmation (9/12)
    ├── Camera position check (9/12 rolling)
    ├── Per-joint guidance (GREEN/YELLOW/RED)
    ├── Direction detection (RAISE/LOWER)
    ├── Voice guidance (worst joint first)
    └── Progress calculation (0-100%)

Inner classes:
    ├── RollingWindow(size=12, required=9)
    ├── JointGuidance(joint, status, angle, direction, message)
    ├── CameraGuidance(isCorrect, detected, expected, tip)
    ├── SetupProgress(percent, isConfirmed)
    └── GuidanceLevel { GREEN, YELLOW, RED }
```

**API المقترح:**

```kotlin
class PoseSetupGuide(
    private val language: String = "ar"
) {
    // Main validation (called every frame from ValidatePose action)
    fun validate(
        angles: JointAngles?,
        landmarks: List<SmoothedLandmark>?,
        exerciseConfig: ExerciseConfig?,
        poseVariantIndex: Int
    ): SetupResult
    
    // Get current progress (0.0 - 1.0)
    fun getProgress(): Float
    
    // Get requirements text for initial display
    fun getPoseRequirementsText(config: ExerciseConfig?, variantIndex: Int): String
    
    // Reset state
    fun reset()
}

data class SetupResult(
    val joints: List<JointGuidance>,      // Per-joint status
    val camera: CameraGuidance?,          // Camera position (null if no expected position)
    val progress: SetupProgress,          // Overall progress
    val worstJoint: JointGuidance?,       // For voice guidance
    val isConfirmed: Boolean              // Ready for countdown?
)

data class JointGuidance(
    val jointCode: String,
    val jointName: LocalizedText,         // "الكوع الأيسر" / "Left Elbow"
    val level: GuidanceLevel,             // GREEN, YELLOW, RED
    val currentAngle: Double,
    val targetMin: Double,
    val targetMax: Double,
    val distance: Double,                 // How far from range (0 = in range)
    val direction: Direction?,            // RAISE, LOWER, null (in range)
    val message: LocalizedText,           // "ارفع أكثر" / "Raise more"
    val isPrimary: Boolean
)

enum class GuidanceLevel { GREEN, YELLOW, RED }
enum class Direction { RAISE, LOWER }
```

### 6.3 الملفات المعدّلة

| الملف | التعديل |
|-------|---------|
| **`WorkoutRunSupervisor.kt`** | `handleCountdown()` - نظام 3 مستويات بدل إلغاء فوري |
| **`SupervisorAction.kt`** | إضافة `ValidatePoseSetup(angles, landmarks)` بدل `ValidatePose(angles)` |
| **`SupervisorSignal.kt`** | `PoseFrame` يبقى كما هو (يحمل landmarks بالفعل) |
| **`TrainingViewModel.kt`** | استخدام `PoseSetupGuide` بدل `PoseValidator` |
| **`CountdownController.kt`** | إصلاح الدقة + إضافة `freeze()`/`unfreeze()` |
| **`SkeletonOverlayView.kt`** | إضافة `isSetupMode` + `drawSetupGuidance()` |
| **`TrainingActivity.kt`** | `updatePoseValidationUI()` الجديد + progress bar + camera tip |
| **`activity_training.xml`** | تعديل `setupPosePanel` + إضافة progress bar |
| **`app_settings.json`** | إضافة قسم `setupValidation` |
| **`FeedbackManager.kt`** | إضافة `speakSetupGuidance(joint, direction)` |

---

## 7. تدفق البيانات الجديد

```
Camera Frame
    ↓
PoseDetector → landmarks + angles
    ↓
supervisor.processSignal(PoseFrame(angles, landmarks, ...))
    ↓
[State = SETUP_POSE]
    ↓
emit(ValidatePoseSetup(angles, landmarks))
    ↓
TrainingViewModel.executeAction():
    ↓
    setupGuide.validate(angles, landmarks, config, variantIndex)
    ↓
    SetupResult {
        joints: [
            JointGuidance(left_elbow, YELLOW, 142°, RAISE, "ارفع أكثر"),
            JointGuidance(left_shoulder, GREEN, 168°, null, "ممتاز")
        ],
        camera: CameraGuidance(isCorrect=true, "side_view"),
        progress: SetupProgress(75%, isConfirmed=false),
        worstJoint: JointGuidance(left_elbow, ...),
        isConfirmed: false
    }
    ↓
    emit(TrainingUIEvent.SetupUpdate(result))
    ↓
TrainingActivity:
    1. skeletonOverlay.updateSetupGuidance(result.joints, landmarks)
    2. updateSetupPanel(result)
    3. updateCameraTip(result.camera)
    4. updateProgress(result.progress)
    5. feedbackManager.speakSetupGuidance(result.worstJoint)  // كل 2.5s
    ↓
    لما result.isConfirmed:
        supervisor.processSignal(PoseConfirmed)
        → COUNTDOWN
```

---

## 8. خطة التنفيذ (الترتيب)

### المرحلة 1: البنية الأساسية
1. إنشاء `PoseSetupGuide.kt` مع `RollingWindow` و `JointGuidance`
2. تحديث `app_settings.json` بإعدادات `setupValidation`
3. تحديث `SupervisorAction.ValidatePose` لتحمل `landmarks` أيضاً

### المرحلة 2: Skeleton Overlay
4. إضافة `isSetupMode` في `SkeletonOverlayView`
5. إنشاء `drawSetupGuidance()` - دوائر + خطوط + أرقام كبيرة
6. ربط `SetupResult.joints` بالـ overlay

### المرحلة 3: الكاميرا والـ UI
7. إضافة `CameraGuidance` في `PoseSetupGuide` (rolling window)
8. تعديل `bottomStatsBar` ← الـ FORM card تصبح VIEW card أثناء SETUP
9. تعديل الـ TIME card ← تصبح progress/READY أثناء SETUP
10. تعديل `setupPosePanel` ← عرض per-joint rows ملوّنة

### المرحلة 4: الإرشاد الصوتي
11. إضافة `speakSetupGuidance()` في `FeedbackManager`
12. إضافة رسائل مترجمة (عربي/إنجليزي) لكل مفصل + اتجاه
13. Cooldown logic (2.5s بين الرسائل)

### المرحلة 5: العد التنازلي
14. إصلاح `CountdownController` ← دقة + `freeze()/unfreeze()`
15. تعديل `WorkoutRunSupervisor.handleCountdown()` ← 3 مستويات تسامح
16. إصلاح تأخير `onCountdownFinished()` ← فوري

### المرحلة 6: التكامل والاختبار
17. ربط كل شيء في `TrainingViewModel` + `TrainingActivity`
18. اختبار على تمارين مختلفة (squat, bicep curl, plank)
19. ضبط الإعدادات (window size, thresholds, cooldowns)

---

## 9. إعدادات `app_settings.json` الجديدة

```json
{
  "setupValidation": {
    "_comment": "إعدادات مرحلة الإعداد قبل التدريب (SETUP_POSE)",
    
    "windowSize": 12,
    "_windowSize_comment": "عدد الفريمات في النافذة المتحركة للتأكيد",
    
    "requiredValid": 9,
    "_requiredValid_comment": "عدد الفريمات الصحيحة المطلوبة من النافذة (9 من 12 = 75%)",
    
    "closeThresholdDegrees": 15.0,
    "_closeThresholdDegrees_comment": "عتبة 'قريب' - الزاوية خارج النطاق بأقل من هذا = أصفر، أكثر = أحمر",
    
    "voiceCooldownMs": 2500,
    "_voiceCooldownMs_comment": "الحد الأدنى بين رسائل الإرشاد الصوتي (بالميلي ثانية)",
    
    "cameraTipEnabled": true,
    "_cameraTipEnabled_comment": "تفعيل إرشاد وضع الكاميرا (true/false)",
    
    "cameraCheckWindowSize": 12,
    "_cameraCheckWindowSize_comment": "عدد الفريمات لفحص وضع الكاميرا",
    
    "cameraCheckRequired": 9,
    "_cameraCheckRequired_comment": "عدد الفريمات المتفقة لتأكيد وضع الكاميرا",
    
    "countdownToleranceFrames": 2,
    "_countdownToleranceFrames_comment": "عدد الفريمات المسموح تجاهلها أثناء العد التنازلي",
    
    "countdownFreezeFrames": 4,
    "_countdownFreezeFrames_comment": "عدد الفريمات قبل تجميد العد التنازلي",
    
    "countdownCancelFrames": 6,
    "_countdownCancelFrames_comment": "عدد الفريمات قبل إلغاء العد التنازلي"
  }
}
```

---

## 10. ملاحظات مهمة

### 10.1 الأداء
- `CameraPositionDetector.detect()` خفيف (حسابات بسيطة على landmarks) → لا مشكلة في استدعائه كل فريم
- لكن **القرار** يُتخذ بناءً على الـ rolling window فقط (كل 12 فريم تتجمع)
- `PoseSetupGuide` أخف من `PoseValidator` الحالي لأنه لا يفرض 10 متتالية

### 10.2 التوافق
- `PoseValidator.kt` الحالي يبقى (deprecated) لحين إزالته
- `PoseSetupGuide` يحل محله تماماً في الاستخدام
- `isSetupMode` مستقل عن `isTrainingMode` في الـ overlay

### 10.3 حالات خاصة
- **Video Mode:** لا setup pose (يبدأ فوراً) - لا تأثير
- **تمارين Hold:** `startPose` مطلوب لوضع البداية + `stateHoldRange` أثناء التدريب
- **Bilateral:** الإرشاد يكون للجانب النشط فقط
- **لا `cameraPosition` محدد:** لا يظهر camera tip أصلاً

### 10.4 اللغة
- كل النصوص بالعربية والإنجليزية عبر `LocalizedText`
- اللغة تُحدد من `app_settings.json` → `feedback.language`
- أسماء المفاصل + رسائل الاتجاه كلها مترجمة
