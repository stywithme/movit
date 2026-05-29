> **Status:** `ARCHIVED` â€” superseded, cancelled, or historical review only.
> **Current SSOT:** `Docs/00-Active-Reference/README.md`
> **Archived:** 2026-05-29

# 🧭 خطة تطوير عرض الزوايا والـ Feedback أثناء التمرين (Android POC)

> الهدف من الخطة: **Minimalist, high-signal coaching** يساعد المتدرّب *مايوصلش للخطأ* ويحفّزه، مع تقليل التشتيت وهو "منهمك" في التمرين.

---

## 🎯 الفلسفة الأساسية (UX Principles)

### المبدأ الأول: المستخدم لا ينظر للشاشة أثناء التمرين
- في **وضع الكاميرا**: الصوت هو القناة الرئيسية
- في **وضع الفيديو**: الرسائل المرئية هي القناة الرئيسية (لأن المستخدم يشاهد)

### المبدأ الثاني: Less is More
- كل عنصر إضافي على الشاشة = تشتيت محتمل
- الصمت/الفراغ = المستخدم يؤدي بشكل صحيح

### المبدأ الثالث: Prevent, Don't Punish
- الهدف ليس "إخبار المستخدم بالخطأ" بل "منعه من الوصول للخطأ"
- التدخل المبكر الخفيف > التحذير القوي المتأخر

### المبدأ الرابع: Contextual Intelligence
- الرسالة الصحيحة في اللحظة الصحيحة بالطريقة الصحيحة
- لا رسائل generic—كل feedback مرتبط بالحالة الفعلية

---

## ✅ المتطلبات الأساسية (Non‑Negotiables)

1) **No visuals for non-tracked angles/joints**
- أثناء التدريب: **لا نعرض أي زوايا/Labels/Indicators** على joints غير موجودة في `PoseVariant.trackedJoints`.
- أي visuals تخص angle لازم تكون مرتبطة بـ tracked joints فقط (Primary/Secondary).

2) **Minimalist design**
- الافتراضي: أقل قدر من العناصر + أكبر وضوح للمطلوب.
- "المساعدة" تظهر فقط عندما تضيف قيمة (Guidance → Warning → Error)، وتختفي فورًا لما المشكلة تروح.

3) **Multi-level feedback (smart escalation)**
- نستغل الموجود: `JointZone` + `CheckSeverity (TIP/WARNING/ERROR)` + `FeedbackPriority` + cooldowns.
- نقلّل الـ spam باستخدام throttle/priority و"عرض مشكلة واحدة/مشكلتين كحد أقصى" في نفس اللحظة.

4) **Prevent errors, not just react**
- إظهار Guidance مبكرًا (قبل الـ error) باستخدام zones/threshold proximity بدل "مفاجأة المستخدم" عند الخطأ.

5) **Motivation without distraction**
- تحفيز ذكي: short, infrequent, and earned (مثلاً بعد streak من reps صحيحة).

6) **Mode-aware feedback channels**
- **Camera mode**: Audio-first + Haptic + Minimal visuals
- **Video mode**: Visual-first (Glassmorphic) + No audio

---

## 🔊 استراتيجية الصوت (Camera Mode Only)

### لماذا الصوت ضروري في وضع الكاميرا؟
- المستخدم **لا ينظر للشاشة** أثناء التمرين الفعلي
- الصوت هو **الطريقة الوحيدة الفعالة** للتوجيه real-time
- الـ Haptic وحده غير كافٍ (يحتاج انتباه لتفسيره)

### مبادئ استخدام الصوت
1. **Short & Clear**: جمل قصيرة جدًا (2-4 كلمات)
2. **Calm & Encouraging**: نبرة هادئة غير صارخة
3. **Contextual**: الرسالة مرتبطة بالحالة الفعلية
4. **Not Annoying**: cooldowns + لا تكرار مزعج

### Audio Cues Map

#### 1) Training Lifecycle
| الحدث | الصوت (English) | ملاحظات |
|---|---|---|
| Countdown | `"3... 2... 1..."` | صوت واضح مع rhythm |
| Training Start | `"Go!"` أو `"Begin"` | energetic لكن مش صارخ |
| Rep Completed (Correct) | `"Good"` أو رقم العدة فقط | كل rep أو كل 3 reps |
| Target Reached | `"Great job! Exercise complete"` | celebratory |
| Training Paused | `"Paused"` | neutral |
| Training Resumed | `"Continue"` | neutral |

#### 2) Form Correction (من `TrackedJoint.errorMessages`)
| الحالة | مصدر النص | مثال |
|---|---|---|
| TOO_HIGH | `errorMessages.tooHigh.en` | `"Go lower"` |
| TOO_LOW | `errorMessages.tooLow.en` | `"Don't go too low"` |
| Position WARNING | `PositionCheck.errorMessage.en` | `"Keep your torso upright"` |
| Position ERROR | `PositionCheck.errorMessage.en` | `"Knee past toes! Push hips back"` |

#### 3) Hold Exercises
| الحدث | الصوت |
|---|---|
| Hold Started | `"Hold..."` |
| Grace Period | `"Stay in position"` |
| Hold Resumed | `"Good, keep holding"` |
| Hold Completed | `"Well done!"` |
| Hold Failed | `"Position lost. Try again"` |

#### 4) Motivation (من `feedbackMessages.motivational[]`)
| Trigger | مثال |
|---|---|
| Streak of 3+ correct reps | `"Excellent! Keep going!"` |
| 50% progress | `"Halfway there!"` |
| 75% progress | `"Almost done!"` |
| Flawless last 10 seconds | `"Great form!"` |

### Audio Cooldowns (لمنع الإزعاج)
```
Error/Warning messages: 2000ms minimum gap (same error type)
Rep count announcements: every 3 reps (not every rep)
Motivational: 8000ms minimum gap
```

### تنفيذ تقني
- **تفعيل TTS**: إعادة `enableTTS = true` في `FeedbackManager`
- **إضافة Audio cues جاهزة**: يفضل pre-recorded audio files لجودة أفضل من TTS
- **Language support**: استخدام `config.language` للتبديل ar/en

---

## 🪟 استراتيجية Glassmorphic (Video Mode Only)

### لماذا Glassmorphic؟
- تصميم **حديث وراقي** يناسب تجربة عالمية
- **لا يحجب المحتوى**: الشفافية تخلي الفيديو مرئي
- **ناعم على العين**: أقل تشتيت من solid colors

### المبادئ
1. **Floating pills**: الرسائل تظهر كـ pills عائمة وليس banners ثابتة
2. **Blur + Transparency**: خلفية blur (8-16dp) + transparency 70-80%
3. **Soft shadows**: elevation خفيف
4. **Smooth animations**: fade in/out + subtle slide

### تصميم الـ Message Pill

```
┌─────────────────────────────────────┐
│  ┌─────────────────────────────┐    │
│  │ 💡 "Keep your torso upright" │   │  ← Glassmorphic pill
│  └─────────────────────────────┘    │
│                                     │
│                                     │
│         [Video Content]             │
│                                     │
│                                     │
│  ┌─────────────────────────────┐    │
│  │      Spinal Twist           │    │  ← Bottom card (existing)
│  │        00:34                │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

### Glassmorphic Styling (Android Implementation)

```xml
<!-- Message Pill Style -->
<shape android:shape="rectangle">
    <corners android:radius="24dp"/>
    <solid android:color="#B3FFFFFF"/>  <!-- 70% white -->
</shape>

<!-- Apply blur via RenderEffect (API 31+) or BlurView library -->
```

```kotlin
// Glassmorphic blur effect
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    view.setRenderEffect(
        RenderEffect.createBlurEffect(16f, 16f, Shader.TileMode.CLAMP)
    )
}
```

### Message Types (Video Mode)

| النوع | Icon | لون الـ accent | مثال |
|---|---|---|---|
| **Tip** | 💡 | Blue | `"Spread your feet wider"` |
| **Warning** | ⚠️ | Amber | `"Keep your torso upright"` |
| **Error** | ❌ | Red | `"Knee past toes!"` |
| **Motivation** | ✨ | Green | `"Great form!"` |
| **Phase/Context** | 🧘 | Neutral | `"Pay attention to your breath"` |

### Animation Specs

```kotlin
// Fade in
messagePill.alpha = 0f
messagePill.animate()
    .alpha(1f)
    .translationY(0f)
    .setDuration(300)
    .setInterpolator(DecelerateInterpolator())
    .start()

// Auto-dismiss after 3 seconds
messagePill.postDelayed({
    messagePill.animate()
        .alpha(0f)
        .translationY(-20f)
        .setDuration(200)
        .start()
}, 3000)
```

### موقع الـ Pill
- **Default**: أعلى الشاشة تحت الـ top bar
- **Alternative**: يسار الشاشة (كما في الصورة المرفقة)
- **لا تحجب**: الـ skeleton أو الـ HUD الأساسي

---

## 🔀 الفرق بين Camera Mode و Video Mode

| الجانب | Camera Mode | Video Mode |
|---|---|---|
| **Primary channel** | 🔊 Audio | 👁️ Visual (Glassmorphic) |
| **Secondary channel** | 📳 Haptic | — |
| **Tertiary channel** | 👁️ Minimal visuals | — |
| **Skeleton** | Tracked joints only (faint) | Tracked joints only |
| **Angles on screen** | Error only (brief) | Error/Warning |
| **Messages** | Audio (TTS/pre-recorded) | Glassmorphic pills |
| **Motivation** | Spoken | Glassmorphic pill + icon |

### تنفيذ تقني
```kotlin
// In TrainingActivity / FeedbackManager
val isVideoMode = trainingMode == MODE_VIDEO

when (event) {
    is FeedbackEvent.JointErrorDetected -> {
        if (isVideoMode) {
            showGlassmorphicMessage(event.error.message.en, MessageType.ERROR)
        } else {
            speakMessage(event.error.message.en)
            vibrateError()
        }
    }
    // ...
}
```

---

## 🧱 البنية الحالية (نستغلها بدل ما نعيد اختراعها)

### مصادر البيانات (Source of Truth)
- `TrainingEngine`
  - **StateFlows**: `arrowInfos`, `positionErrors`, `currentPhase`, `repCount`, `hold*`
  - **Events**: `FeedbackEvent` (rep/hold/position/camera…)
- `FormValidator`
  - zones + arrow direction: `JointZone`, `JointArrowInfo`
  - settings: `boundaryBuffer`, `extremeErrorThreshold`
- `PositionValidator`
  - `CheckSeverity`: `TIP/WARNING/ERROR`
  - stability: `minErrorFrames`, `cooldownMs`
- `FeedbackManager`
  - **Haptic patterns + cooldown**
  - (TTS موجود لكن disabled حاليًا)
- العرض:
  - `SkeletonOverlayView` (skeleton + arrows + position indicators + angles)
  - `TrainingActivity` (panels + rep/phase + toasts)

---

## 🎛️ طبقات الـ UI المقترحة (Minimalist Layers)

### 1) HUD ثابت (دائمًا)
- **Rep/Time + Progress + Phase** (موجود بالفعل في `trainingPanel`)
- **لا نضيف نصوص كثيرة هنا**. HUD هو مرساة المستخدم.

### 2) On-body guidance (بالقرب من الجسم)
- الأسهم/الـ highlights تظهر **على tracked segments فقط**.
- الهدف: “افعل كذا الآن” بصريًا بدون قراءة طويلة.

### 3) Message strip (عند الحاجة فقط)
- نستخدم عنصر واحد فقط كـ “Message strip” (يمكن إعادة استخدام `tvError` بدل Toasts).
- يظهر **جملة قصيرة جدًا** في الحالات الأعلى أولوية.
- UI copy examples (English):
  - Error: `"Go lower"`
  - Warning: `"Keep your torso upright"`
  - Tip: `"Spread your feet wider"`
  - Motivation: `"Great form!"`

---

## 📐 خطة عرض الزوايا (Angles) أثناء التدريب

### المشكلة الحالية
`SkeletonOverlayView.drawAngles()` يرسم زوايا لمفاصل ثابتة (knee/hip/shoulder/elbow) حتى لو joint غير tracked → ده ضد شرطك.

### المبدأ
**Angle labels ليست default.** تظهر فقط عندما تخدم قرار فوري للمستخدم.

### Policy: متى نظهر زاوية؟
#### A) Training mode (أثناء التمرين)
- **Primary tracked joints**
  - **Default**: لا رقم.
  - **Guidance** (UP_ZONE/DOWN_ZONE): إظهار arrow فقط (بدون رقم).
  - **Warning (pre-error)**: رقم صغير يظهر لو “قريب من الحد” (تفاصيل تحت).
  - **Error (TOO_HIGH/TOO_LOW)**: رقم واضح + range مختصر (اختياري) + highlight قوي.
- **Secondary tracked joints**
  - **Default**: لا رقم.
  - يظهر الرقم فقط عند:
    - تكرار نفس المشكلة (مثلاً 3 مرات خلال N ثواني)، أو
    - severity أعلى (critical/extreme)، أو
    - Position check مربوط بنفس المنطقة.

#### B) Setup pose (قبل التدريب)
- ممكن نعرض الزوايا للـ **primary only** لمساعدة الدخول للـ start pose (موجود UI status بالفعل).
- لا نعرض secondary إلا إذا كانت جزء أساسي من “start pose requirements”.

### “Pre-error Warning” بدون تشويش
نستغل `boundaryBuffer` + `extremeErrorThreshold`:
- **Near-boundary**: لو angle داخل buffer من الحد (مثلاً قريب من `upRange.max` أو `downRange.min`)
  - نعرض **warning ring/amber tint** على نفس joint + (اختياري) رقم صغير.
- **Extreme/Critical**: لو تعدّى `extremeErrorThreshold`
  - نعرض **critical styling** + message strip قصير + haptic أقوى.

> ملاحظة تنفيذية: ده لا يحتاج تغيير JSON. يعتمد على settings الموجودة.

### مكان عرض الرقم (Angle label placement)
- قريب من tracked landmark نفسه أو منتصف الـ moving segment (اختيار واحد ثابت).
- يمنع تراكب أرقام كثيرة: **max 2 angle labels** في نفس frame (الأعلى أولوية فقط).

---

## 🧠 نموذج الـ Feedback الذكي (Levels + Interventions)

### 1) Angle-based feedback (FormValidator zones)

#### 1.1 Mapping (Zone → Visual/Haptic/Message)
> المبدأ: **Guidance دائمًا بصري**، و**Haptic/Text فقط عند الحاجة**.

| الحالة | Trigger (من الموجود) | On-body visuals | Angle label | Message strip | Haptic |
|---|---|---|---|---|---|
| **Guidance** | `UP_ZONE` أو `DOWN_ZONE` + `shouldShowArrow()` | Arrow **Green** على الـ moving segment | Off | Off | Off |
| **Pre‑warning** | Near boundary باستخدام `boundaryBuffer` | Arrow **Amber** أو ring بسيط | Optional (small) | Off | Optional (light, once per cooldown) |
| **Error** | `TOO_HIGH` أو `TOO_LOW` | Arrow **Red** + highlight segment | On (clear) | Optional (very short) | Error pattern (current) |
| **Critical** | تجاوز `extremeErrorThreshold` | Red stronger + lock focus | On + range hint | On (1 line) | Stronger pattern (one-shot) |

**UI copy examples (English)**  
- Too high: `"Go lower"`  
- Too low: `"Don't go too low"`  

> مصدر النص: `TrackedJoint.errorMessages.*` (LocalizedText). في UI نستخدم `.en` حسب قاعدة المشروع.

#### 1.2 “Focus” بدل ازدحام الأسهم
بدل رسم كل الأسهم لكل tracked joints، نعمل:
- **Max 1–2 focus joints** في نفس اللحظة (الأكثر تأثيرًا).
- باقي joints تظهر “hint” أخف أو تختفي (قرار حسب Minimalism المطلوب).

معايير focus (بالترتيب):
- Primary joints قبل Secondary.
- Severity: Critical > Error > Warning > Guidance.
- Persistence: المشكلة المستمرة/المتكررة أعلى.
- Phase relevance: المشكلة النشطة في phase الحالية أعلى.

#### 1.3 Prevent errors (قبل الوصول للـ Error)
نستخدم إشارات مبكرة بدون تشويش:
- **Near-boundary visual** فقط (Amber) بدل رسالة نصية.
- **Haptic pre-warning** اختيارية ومحدودة (مرة واحدة عند دخول near-boundary، ثم cooldown).

---

### 2) Position-based feedback (PositionValidator: TIP/WARNING/ERROR)

#### 2.1 Distinct visuals per severity (لأن TIP ≠ WARNING)
المطلوب Minimalist لكن “مستوى” في الفروقات:

| Severity | Visual on skeleton | Message strip | Haptic |
|---|---|---|---|
| **TIP** | Indicator خفيف جدًا (مثلاً dotted line + no fill) | Off (إلا لو تكرّر) | Off |
| **WARNING** | Amber dashed line + circles (current idea) | Optional (1 line) | Warning pattern (limited) |
| **ERROR** | Red/Pink + stronger emphasis | On (1 line) | Error pattern |

**UI copy examples (English)**  
- Tip: `"Spread your feet wider"`  
- Warning: `"Don't let your knee go past your toes"`  

> مصدر النص: `PositionError.message.en` (من JSON).

#### 2.2 Prevent errors في position checks (اختياري – يحتاج موافقتك قبل التنفيذ)
حاليًا `PositionValidator` يرجّع error فقط عند الفشل بعد `minErrorFrames`. لتحسين “مايوصلش للخطأ” ممكن نضيف:
- **Proximity signal** (مثلاً `ratio = abs(diff)/threshold`) حتى وهو “passed”، ونطلع Tip مبكرًا لو قرب من الحد.

ده تعديل بسيط على `PositionValidator`/result models لكنه “فكرة جديدة” نسبيًا—أحتاج تأكيدك قبل ما نضيفه.

---

### 3) Rep / Phase / Hold feedback

#### 3.1 Rep completed (موجود) + ضبط Minimal
- Correct rep: **Green pulse** على `tvRepCount` (موجود)
- Incorrect rep: **Amber pulse** (موجود)
- إضافة مقترحة: **streak indicator بسيط** (بدون تشتيت) يظهر فقط عند milestones.

#### 3.2 Hold exercises
نستمر على الموجود، لكن نخليه أوضح:
- `HoldState.HOLDING`: لون أخضر على الوقت
- `GRACE_PERIOD`: phase text تحذيري (موجود)
- `COMPLETED`: completion feedback (موجود)

---

### 4) Camera position warning (بدل Toast)
Toast أثناء التمرين مزعج ومش “Minimalist” لأنه بيقطع التركيز.

الخطة:
- تحويله إلى **Message strip** ثابت أعلى/وسط الشاشة لمدة قصيرة (مثلاً 3–5 ثواني)، مع cooldown.
- UI copy (English): `"For best results, film from side view"`

> نقدر نعيد استخدام `tvError` بدل إضافة views جديدة.

---

### 5) Motivation (تحفيز ذكي من غير إزعاج)

#### 5.1 مصادر التحفيز (موجودة في JSON)
من `PoseVariant.feedbackMessages`:
- `motivational[]`
- `common_mistake[]`
- `tip[]`

#### 5.2 سياسة الإظهار (Earned + Rare)
نظهر رسالة تحفيزية فقط لو:
- آخر N ثواني/frames **بدون errors** (زاوية أو position).
- أو بعد **streak** من reps صحيحة (مثلاً 3 correct reps).
- أو عند milestones (25%/50%/75%/100% progress).

قواعد منع التشتيت:
- **لا تظهر Motivation** لو فيه Warning/Error نشط.
- مدة الظهور قصيرة (1–1.5s) وبدون stacking.

UI copy examples (English):
- `"Great form!"`
- `"Keep going!"`

---

## 🧮 خوارزمية اختيار “التدخل الأهم” (Smart Prioritization)

هدفها: المستخدم يشوف **رسالة واحدة مفهومة** بدل 6 إشارات متنافسة.

### Input signals
- Angle signals: من `arrowInfos` + zones + near-boundary + extreme
- Position signals: من `positionErrors` (مع `severity`)
- Hold/Grace signals
- Camera warning

### Ranking (High → Low)
1. **Critical safety** (extreme angle / position ERROR)
2. **Primary joint errors**
3. **Position WARNING**
4. **Secondary joint errors**
5. **Tips**
6. **Motivation**

### Output policy
- **On-body visuals**: highlight + arrows فقط للـ focus joints.
- **Message strip**: message واحد فقط (أعلى ranked).
- **Haptic**: فقط لأعلى severities/rep events، وبـ cooldown.

---

## 🧯 Anti‑Spam / Fatigue Management

### Visual
- Max 2 active focus joints.
- Hide non-essential overlays when user is “in flow” (no issues).

### Haptic
- استخدام الموجود في `FeedbackManager` + إضافة قاعدة:
  - vibrate only on state transition (enter error) أو على cooldown—not continuously.

### Messages
- message strip عند Warning/Error/Critical فقط.
- TIPs تظهر بصريًا بدون نص إلا عند التكرار.

---

## 🎨 توصيات UX واضحة (من منظور خبير)

### 1) الـ Skeleton أثناء التدريب
**التوصية**: **Tracked joints only + faint للباقي**
- **Tracked joints/segments**: واضحة بألوان مميزة
- **Non-tracked**: faint جدًا (opacity 15-20%) أو مختفية
- **السبب**: المستخدم يحتاج يشوف جسمه لكن التركيز على المطلوب

### 2) Angle Numbers
**التوصية**: **Error/Critical فقط**
- لا نظهر أرقام في الوضع الطبيعي
- Warning: visual cue فقط (amber ring) بدون رقم
- Error: رقم واضح + range hint
- **السبب**: الأرقام تشتت—المستخدم يحتاج يعرف "اتجاه" مش "رقم"

### 3) Position TIPs
**التوصية**: **Visual فقط + Message عند التكرار**
- أول مرة: visual indicator خفيف
- تكرار (3+ مرات): message strip/pill
- **السبب**: TIPs ليست أخطاء—لا نريد إزعاج المستخدم

### 4) الألوان المتسقة
```
GREEN  (#00E676): Correct / Guidance / Motivation
AMBER  (#FFC107): Warning / Near-boundary / Grace period
RED    (#FF5252): Error / Critical
BLUE   (#2196F3): Tracked joints (neutral state)
PINK   (#E91E63): Position errors
```

### 5) Typography
- **Primary numbers** (rep count, time): Large, Bold
- **Phase labels**: Medium, Semi-bold
- **Messages**: Normal, Clear font

---

## 🛠️ خطة تنفيذ مرحلية (Incremental, low-risk)

### Phase 1 — Tracked-only visuals (الأساس)
**الملفات**: `SkeletonOverlayView.kt`

- [ ] عرض angle labels فقط للـ joints في `trackedLandmarkIndices`
- [ ] حد أقصى 2 angle labels في نفس اللحظة
- [ ] إخفاء/faint للـ non-tracked skeleton

**Definition of Done**:
- أثناء التدريب: لا يوجد أي زاوية/label على joint غير tracked

---

### Phase 2 — Mode-aware feedback channels
**الملفات**: `FeedbackManager.kt`, `TrainingActivity.kt`

- [ ] إضافة `isVideoMode` flag
- [ ] Camera mode: تفعيل Audio (TTS أو pre-recorded)
- [ ] Video mode: تعطيل Audio

**Definition of Done**:
- Camera mode: الرسائل تُنطق
- Video mode: الرسائل لا تُنطق

---

### Phase 3 — Glassmorphic message pills (Video Mode)
**الملفات**: `res/layout/activity_training.xml`, `TrainingActivity.kt`

- [ ] إنشاء `GlassmorphicMessageView` أو استخدام CardView + blur
- [ ] styling: rounded corners, blur, transparency
- [ ] animations: fade in/out, auto-dismiss
- [ ] positioning: أعلى يسار أو أعلى وسط

**Definition of Done**:
- Video mode: الرسائل تظهر كـ glassmorphic pills جميلة

---

### Phase 4 — Audio cues system (Camera Mode)
**الملفات**: `FeedbackManager.kt`, `assets/audio/`

- [ ] إنشاء audio assets (أو استخدام TTS مُحسّن)
- [ ] تفعيل countdown audio
- [ ] تفعيل rep count audio (كل 3 reps)
- [ ] تفعيل error/warning audio
- [ ] cooldowns لمنع spam

**Definition of Done**:
- Camera mode: المستخدم يسمع توجيهات واضحة بدون إزعاج

---

### Phase 5 — Focus + Severity system
**الملفات**: `SkeletonOverlayView.kt`, `TrainingActivity.kt`

- [ ] Focus joints logic (max 1-2)
- [ ] TIP/WARNING/ERROR تمييز بصري واضح
- [ ] Priority ranking للتدخلات

**Definition of Done**:
- لحظة واحدة = رسالة واحدة واضحة (لا ازدحام)

---

### Phase 6 — Motivation system
**الملفات**: `TrainingEngine.kt`, `FeedbackManager.kt`

- [ ] Streak detection logic
- [ ] Milestone triggers (25%, 50%, 75%, 100%)
- [ ] استخدام `feedbackMessages.motivational[]`
- [ ] Camera: spoken / Video: glassmorphic pill

**Definition of Done**:
- المستخدم يتلقى تشجيع ذكي عند الأداء الجيد

---

## 💎 6. استراتيجية UI/UX الاحترافية (Transformation Strategy)

لتحويل الواجهة من "أداة تقنية" إلى "منتج عالمي"، سنعتمد نظام **"Fluid Glass HUD"**.

### 6.1 نظام التصميم الموحد (The Glass System)
يتم توحيد العناصر في الوضعين (Camera/Video) باستخدام "Pills" و "Decks" عائمة بدلاً من الـ Bars التقليدية.

**مواصفات الـ Glassmorphism:**
- **Background**: `Color(0x26000000)` (15% Black) لضمان التباين مع الفيديو/الكاميرا.
- **Blur**: `RenderEffect.createBlurEffect(20f, 20f)` (Android 12+) أو StackBlur للنسخ الأقدم.
- **Border**: `1dp` stroke بلون `Color(0x1AFFFFFF)` (10% White).
- **Corner Radius**: `24dp` لجميع العناصر.

### 6.2 الهيكلة الجديدة للشاشة (Layout)

```
┌─────────────────────────────────────────────────────────────┐
│  [ Glass Pill: Exit | Exercise Name | Phase ]               │ ← Top Deck
│                                                             │
│           (Skeleton Layer - Z-Index: 0)                     │
│                                                             │
│      [ Hero Number: 12 ]                                    │ ← Floating Rep Counter
│      (Ultra-Thin Font, 120sp)                               │
│                                                             │
│                                                             │
│  [ Glass Pill: Warning/Tip Message ]                        │ ← Contextual Message
│                                                             │
│                                                             │
│  [ Control Deck: Settings | Pause | Progress ]              │ ← Bottom Deck
└─────────────────────────────────────────────────────────────┘
```

### 6.3 التوحيد الوظيفي (Functional Unity)

| العنصر UI | في وضع Camera | في وضع Video |
|---|---|---|
| **Hero Number** | يعرض **Rep Count** | يعرض **Timer** (أو Reps if detected) |
| **Progress Bar** | شريط خطي يمثل **Set Progress** | شريط تفاعلي **Seek Bar** للفيديو |
| **Play/Pause** | زر عائم (Floating Action Button) | زر عائم بنفس المكان والتصميم |
| **Message Area** | تظهر عند Critical/Error فقط | تظهر للـ Insights/Tips بشكل دائم |

### 6.4 التحركات الذكية (Smart Animations)

**1) Slot Machine Counter**
عند زيادة العداد، الرقم لا يتغير فجأة:
```kotlin
// Slide Out Old
oldView.animate().translationY(-50f).alpha(0f).setDuration(200)
// Slide In New
newView.translationY = 50f
newView.animate().translationY(0f).alpha(1f).setDuration(200)
```

**2) Ambient Peripheral Alert**
بدل تلوين النص بالأحمر، نستخدم إضاءة محيطية (Vignette) ناعمة:
- **Warning**: توهج برتقالي خافـت عند حواف الشاشة.
- **Error**: توهج أحمر خافـت ينبض (Pulse) ببطء.
*الهدف: تنبيه المستخدم باستخدام رؤيته الطرفية (Peripheral Vision) دون حجب المركز.*

**3) Skeleton "Flow"**
الـ Skeleton ليس خطوطاً مصمتة، بل يحتوي على Gradient متحرك (Shimmer) في اتجاه الحركة الصحيح (مثلاً: أثناء الصعود، الضوء يتحرك لأعلى الساق).

---

## ✅ معايير القبول النهائية (Acceptance Criteria)

### Functional
- [ ] **Tracked-only**: لا تظهر أي زاوية/label على joints غير tracked
- [ ] **Mode-aware**: Camera = Audio / Video = Glassmorphic
- [ ] **No spam**: cooldowns محترمة (لا تكرار مزعج)
- [ ] **Prevention**: إشارات مبكرة قبل الأخطاء
- [ ] **Clarity**: المستخدم يعرف "يعمل إيه" في أقل من ثانية

### Visual Quality
- [ ] **Minimalist**: في الوضع الطبيعي = HUD + skeleton minimal فقط
- [ ] **Glassmorphic**: استخدام Decks و Pills شفافة مع Blur عالي
- [ ] **Cinematic Layout**: لا توجد عناصر صلبة تحجب الفيديو (Full Immersive)
- [ ] **Consistent colors**: الألوان متسقة عبر كل الـ feedback
- [ ] **Smart Motion**: العدادات والرسائل تتحرك بانسيابية (No hard cuts)

### Audio Quality (Camera Mode)
- [ ] **Clear pronunciation**: الرسائل مفهومة
- [ ] **Appropriate timing**: الصوت يأتي في الوقت المناسب
- [ ] **Not annoying**: لا صراخ أو تكرار مزعج

---

## 📊 ملخص الفروقات بين الوضعين

```
┌─────────────────────────────────────────────────────────────────┐
│                      CAMERA MODE                                │
├─────────────────────────────────────────────────────────────────┤
│  🔊 Audio: ON (primary channel)                                 │
│  📳 Haptic: ON (secondary)                                      │
│  👁️ Visuals: Minimal (skeleton + arrows only when needed)       │
│  💬 Messages: Spoken, not displayed                             │
│  🎯 Focus: User is exercising, not watching                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      VIDEO MODE                                 │
├─────────────────────────────────────────────────────────────────┤
│  🔊 Audio: OFF                                                  │
│  📳 Haptic: OFF                                                 │
│  👁️ Visuals: Rich (skeleton + glassmorphic messages)            │
│  💬 Messages: Glassmorphic pills (modern, beautiful)            │
│  🎯 Focus: User is watching/analyzing                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## ❓ قرارات تم اتخاذها

| السؤال | القرار |
|---|---|
| Skeleton غير tracked | **Faint** (opacity 15-20%) |
| Angle numbers | **Error/Critical فقط** |
| Position TIPs | **Visual فقط + Message عند التكرار** |
| Audio cues | **نعم في Camera mode فقط** |
| Video mode messages | **Glassmorphic pills** |

---

## ✨ Micro-interactions & Polish (للتجربة العالمية)

### 1) Rep Counter Animation
```kotlin
// Pulse effect on rep complete
tvRepCount.animate()
    .scaleX(1.2f).scaleY(1.2f)
    .setDuration(100)
    .withEndAction {
        tvRepCount.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(100)
            .start()
    }
    .start()
```

### 2) Phase Transition
- Smooth text transition بين phases (crossfade)
- Optional: subtle color shift

### 3) Progress Indicators
- Circular progress للـ hold exercises
- Linear progress للـ rep-based (subtle)

### 4) Skeleton Glow Effect
- عند Correct form: subtle green glow على tracked joints
- عند Error: red pulse على affected joint

### 5) Glassmorphic Pill Entrance (Video Mode)
- Slide in from top + fade
- Subtle bounce effect
- Auto-dismiss with fade out

### 6) Countdown Animation
- Large number with scale down effect
- Each number has slight bounce
- "Go!" with quick scale up

---

## 🔍 تحليل الأخطاء السابقة والتحسينات

### الأخطاء في الخطة الأصلية

| الخطأ | التأثير | الإصلاح |
|---|---|---|
| غياب استراتيجية صوت | المستخدم لا يتلقى feedback وهو يتمرن | إضافة قسم Audio Strategy كامل |
| عدم التفريق بين Camera/Video | نفس الـ UX لسيناريوهين مختلفين | إضافة Mode-aware behavior |
| Message strip بدائي | تصميم غير عصري | Glassmorphic pills |
| توصيات غير واضحة | أسئلة بدل قرارات | توصيات UX محددة |
| غياب micro-interactions | تجربة "flat" | إضافة قسم polish |

### التحسينات المُضافة

1. **Audio-first للـ Camera mode**: تجربة hands-free حقيقية
2. **Glassmorphic design**: تصميم حديث يرقى للمعايير العالمية
3. **Mode-specific UX**: كل وضع له تجربة مُحسّنة لسياقه
4. **Clear UX recommendations**: قرارات واضحة بدل أسئلة مفتوحة
5. **Micro-interactions**: polish يصنع الفرق في الجودة المُدركة
6. **Consistent color system**: ألوان موحدة عبر كل الـ feedback
7. **Phased implementation**: خطة تنفيذ واقعية ومتدرجة
8. **Smart Message Orchestrator**: نظام ذكي لمنع تكرار الرسائل المزعج

---

## 🎯 Smart Message Orchestrator

### المشكلة
الرسائل كانت تتكرر كل 2-3 ثواني طالما المستخدم في وضع خطأ، مما يؤدي لتجربة مزعجة.

### الحل: MessageOrchestrator

نظام ذكي يدير توصيل الرسائل بالمبادئ التالية:

| المبدأ | التطبيق |
|---|---|
| **First is loud, repeat is quiet** | أول مرة = صوت + visual، التكرار = visual فقط أو haptic |
| **Progressive silence** | بعد 3 تكرارات → توقف (الـ skeleton overlay كافي) |
| **Category-aware** | أنواع مختلفة = قواعد مختلفة |
| **State-aware** | لو المشكلة اختفت ورجعت = reset العداد |
| **One at a time** | أهم رسالة فقط تظهر |

### فئات الرسائل (Categories)

| الفئة | السلوك | Max Repeats | Cooldowns |
|---|---|---|---|
| `CRITICAL` | دائماً audio+visual | ∞ | 2s, 4s, 8s |
| `ERROR` | First=full, repeat=visual/haptic | 3 | 2s, 4s, 8s |
| `WARNING` | First=full, repeat=visual | 2 | 3s, 6s |
| `TIP` | Visual only | 1 | 5s |
| `MOTIVATION` | Full but infrequent | 1 | 10s |
| `INFO` | Visual only, one-time | 1 | ∞ |

### Delivery Channels

```
AUDIO_AND_VISUAL  → Full feedback (أول مرة)
VISUAL_ONLY       → رسالة مرئية فقط (تكرار)
HAPTIC_ONLY       → اهتزاز خفيف (تذكير)
SILENT            → لا شيء (الـ overlay كافي)
```

### التنفيذ

**MessageOrchestrator.kt**:
- `decide(messageKey, category, text)` → `DeliveryDecision`
- تتبع حالة كل رسالة (repeatCount, lastShownTime, lastActiveTime)
- Reset تلقائي لو المشكلة اختفت 3 ثواني
- منع تكرار نفس رسالة Motivation متتالية

**FeedbackManager.kt**:
- `deliverMessage(message, decision, type)` → توصيل حسب القناة
- `resetMessageStates()` → reset عند بداية التدريب
- كل handler يستخدم `messageOrchestrator.decide()` بدل cooldowns يدوية

---

## 📚 مراجع التصميم (Design References)

### Glassmorphism
- Apple iOS widgets style
- الصورة المرفقة (Yoga app)
- Blur radius: 8-16dp
- Background: rgba(255,255,255,0.7)

### Audio UX
- Calm app: soothing voice cues
- Nike Training: motivational without being annoying
- Peloton: clear instruction timing

### Fitness App Benchmarks
- Apple Fitness+: minimal UI, voice-led
- Nike Training Club: clear form cues
- Freeletics: smart progression feedback

