# 🎯 خطة تطوير نظام الإرشاد البصري (Smart Visual Guidance System)

## المشكلة الحالية

```
┌────────────────────────────────────────────────────────┐
│  الآن: "Information Overload"                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │  • Skeleton كامل (33 نقطة + خطوط)               │  │
│  │  • Arcs على كل مفصل tracked                     │  │
│  │  • ألوان متغيرة باستمرار (Flickering)           │  │
│  │  • Position error lines متعددة                  │  │
│  │  • Angle numbers فوق المفاصل                    │  │
│  │  • Vignette + Messages                          │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  النتيجة: المتدرب لا يعرف أين ينظر!                   │
└────────────────────────────────────────────────────────┘
```

---

## 🧠 الفلسفة الجديدة: "Minimal + Smart Guidance"

```
┌─────────────────────────────────────────────────────────────────┐
│                    Visual Priority Hierarchy                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────┐                                               │
│   │  CRITICAL   │  ← أولوية 1: خطأ بأقصى وضوح                   │
│   │   (Error)   │     (Line indicator كامل + vignette)          │
│   └──────┬──────┘                                               │
│          │                                                      │
│   ┌──────▼──────┐                                               │
│   │   WARNING   │  ← أولوية 2: تحذير (peripheral vision)        │
│   │  (Caution)  │     (Line indicator متوسط + vignette خفيف)   │
│   └──────┬──────┘                                               │
│          │                                                      │
│   ┌──────▼──────┐                                               │
│   │   OPTIMAL   │  ← أولوية 3: "أنت تمام" (minimal feedback)    │
│   │  (Correct)  │     (Line indicator قصير/أخضر + glow هادئ)   │
│   └─────────────┘                                               │
│                                                                 │
│   📌 الـ Visuals تظهر على كل المفاصل الـ PRIMARY               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🆕 العنصر الجديد: Line Range Indicator (بديل الـ Arc)

### المفهوم الأساسي

**بدلاً من رسم Arc حول المفصل، نستخدم خطوط الـ Skeleton نفسها كـ Indicator:**

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│   الزاوية = 3 Joints + 2 Lines (أضلاع)                                 │
│                                                                         │
│        ○ Joint A (الطرف العلوي - مثلاً: الكتف)                         │
│        │                                                                │
│        │ ← Upper Limb (الضلع العلوي) - يُرسم عليه في UP phases         │
│        │                                                                │
│        ● Joint B (المفصل الأوسط - نقطة الزاوية - مثلاً: الكوع)         │
│        │                                                                │
│        │ ← Lower Limb (الضلع السفلي) - يُرسم عليه في DOWN phases       │
│        │                                                                │
│        ○ Joint C (الطرف السفلي - مثلاً: الرسغ)                         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## ⚠️ تصحيح مهم: اختيار الـ Limb بناءً على Zone/Phase

### المشكلة في النسخة السابقة
كانت الخطة تعتمد على `transitionCenter` لتحديد اتجاه الرسم، لكن هذا خاطئ لأن:
- **UP/DOWN في الـ Engine هي Zones (جاهزية/هدف)** وليست "اتجاه حركة فيزيائي"
- الـ Phase Machine عندنا فيه `DOWN/PUSH` و `UP/PULL` كمراحل حركة

### التصحيح: ربط الـ Limb بالـ Zone الحالي

```kotlin
/**
 * تحديد أي ضلع نرسم عليه بناءً على الـ Zone الحالي
 * 
 * المنطق:
 * - UP_ZONE / TOO_HIGH → نرسم على Upper Limb (المستخدم في/فوق منطقة UP)
 * - DOWN_ZONE / TOO_LOW → نرسم على Lower Limb (المستخدم في/تحت منطقة DOWN)
 * - TRANSITION → لا نرسم (الخط = 0، المستخدم يتحرك بين المناطق)
 */
fun getTargetLimb(zone: JointZone): LimbType {
    return when (zone) {
        JointZone.UP_ZONE, JointZone.TOO_HIGH -> LimbType.UPPER
        JointZone.DOWN_ZONE, JointZone.TOO_LOW -> LimbType.LOWER
        JointZone.TRANSITION -> LimbType.NONE  // الخط مختفي
    }
}
```

### آلية العمل المصححة

```
═══════════════════════════════════════════════════════════════════════════════

     TRANSITION Zone        DOWN_ZONE              DOWN_ZONE            TOO_LOW
     (الخط = صفر)          (في المنطقة)            (قريب من الحد)        (Error)
     ════════════════      ════════════          ════════════          ════════════

          ○ A                  ○ A                   ○ A                   ○ A
          │                    │                     │                     │
          │                    │                     │                     │
          ● B ← الزاوية        ● B                   ● B                   ● B
          │                    ┃🟢                   ┃🟢                   ┃🟢
          │                    ┃ (قصير-أخضر)         ┃🟡                   ┃🟡
          │                    ▼                     ┃ (أصفر)              ┃🟠
          ○ C                  ○ C                   ▼                     ┃🔴
                                                     ○ C                   ▼
                                                                           ○ C

     Zone = TRANSITION      Zone = DOWN_ZONE      Zone = DOWN_ZONE       Zone = TOO_LOW
     الخط مختفي            في المنطقة المثالية    اقتراب من الحد         خطأ!
     (نقطة فقط)            (أخضر)                 (أصفر)                 (أحمر)


═══════════════════════════════════════════════════════════════════════════════

     TRANSITION Zone        UP_ZONE                UP_ZONE              TOO_HIGH
     (الخط = صفر)          (في المنطقة)            (قريب من الحد)        (Error)
     ════════════════      ════════════          ════════════          ════════════

          ○ A                  ○ A                   ○ A                   ○ A
          │                    ▲                     ▲                     ▲
          │                    ┃🟢                   ┃🟡                   ┃🔴
          ● B ← الزاوية        ● B                   ┃🟢                   ┃🟠
          │                    │                     ● B                   ┃🟡
          │                    │                     │                     ┃🟢
          │                    │                     │                     ● B
          ○ C                  ○ C                   ○ C                   │
                                                                           ○ C

     Zone = TRANSITION      Zone = UP_ZONE        Zone = UP_ZONE        Zone = TOO_HIGH
     الخط مختفي            في المنطقة المثالية    اقتراب من الحد         خطأ!
     (نقطة فقط)            (أخضر)                 (أصفر)                 (أحمر)

═══════════════════════════════════════════════════════════════════════════════
```

---

## 🎨 التلوين: إعادة استخدام ArcColorCalculator

### ✅ لا نخترع منطق جديد - نستخدم الموجود!

الكود الحالي في `ArcColorCalculator.kt` يحتوي على كل منطق التلوين المطلوب:

```kotlin
// من ArcColorCalculator.kt - نستخدمه مباشرة

/** Error zone color (TOO_HIGH, TOO_LOW) */
val COLOR_ERROR = Color.parseColor("#FF5252")      // أحمر

/** Optimal position color (center of valid zones) */
val COLOR_OPTIMAL = Color.parseColor("#00E676")    // أخضر

/** Approaching boundary color - isWarning = true */
val COLOR_NEAR_BOUNDARY = Color.parseColor("#FFEB3B")  // أصفر

/** At boundary color (edge of valid zones) */
val COLOR_BOUNDARY = Color.parseColor("#FF9800")   // برتقالي

/** Transition zone color */
val COLOR_TRANSITION = Color.parseColor("#FFEB3B") // أصفر (ثابت)
```

### حساب اللون للـ Line Indicator

```kotlin
// نستخدم نفس دالة getColorForAngle من ArcColorCalculator
fun getLineColor(arrowInfo: JointArrowInfo): Int {
    return ArcColorCalculator.getColorForAngle(
        angle = arrowInfo.currentAngle,
        upRangeMin = arrowInfo.upRangeMin,
        upRangeMax = arrowInfo.upRangeMax,
        downRangeMin = arrowInfo.downRangeMin,
        downRangeMax = arrowInfo.downRangeMax
    )
}
```

### تدرج الألوان على الخط (نفس الـ Arc)

```
المفصل الأوسط                                              نصف المسافة (MAX)
      ●━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━►
      │         │              │              │              │
      │ 🟡      │     🟢       │      🟡      │     🟠       │  🔴
      │Transition│   Optimal    │   Warning    │   Boundary   │ Error
      │ (Yellow) │   (Green)    │   (Yellow)   │   (Orange)   │ (Red)
      │         │              │              │              │
      ├─────────┼──────────────┼──────────────┼──────────────┤
      0%       20%            60%            80%           100%
```

**القرار الثابت:** `TRANSITION = Yellow (#FFEB3B)` - متسق مع الكود الحالي.

---

## 🔄 استغلال Hysteresis الموجود في Engine

### ✅ لا نخترع gating جديد - نستخدم الموجود!

الـ `FormValidator` عامل hysteresis بالفعل لمنع الفليكر:

```kotlin
// من FormValidator.kt - موجود ومُفعّل
private const val ZONE_HYSTERESIS = 2.0  // درجات

// Zone won't change unless angle crosses boundary by this amount
val zone = if (previousZone != null && previousZone != rawZone) {
    val shouldChange = when {
        // Entering error zones: change immediately (safety)
        rawZone == JointZone.TOO_HIGH || rawZone == JointZone.TOO_LOW -> true
        
        // Exiting error zones: require hysteresis
        previousZone == JointZone.TOO_HIGH && rawZone == JointZone.UP_ZONE ->
            currentAngle < upMaxWithBuffer - ZONE_HYSTERESIS
        // ... etc
    }
    if (shouldChange) rawZone else previousZone
} else {
    rawZone
}
```

**الاستنتاج:** الـ Line Indicator يعتمد على `arrowInfo.zone` مباشرة (اللي أصلاً عليه hysteresis) بدل ما يعيد حساب zones.

---

## 🪞 Mirroring للـ Front Camera

### ⚠️ نقطة مهمة في التنفيذ

الكود الحالي بيعمل mirroring في مكانين:
1. `TrainingActivity`: `angles.mirrored()` للزوايا
2. `SkeletonOverlayView`: mirroring للـ landmark indices + joint codes

**المطلوب في Line Indicator:**
```kotlin
// في LineRangeIndicator أو SkeletonOverlayView
fun drawLineIndicator(
    arrowInfo: JointArrowInfo,
    isFrontCamera: Boolean  // ← لازم يتمرر
) {
    // استخدام نفس mirroring logic الموجود
    val jointCode = if (isFrontCamera) {
        mirrorJointCode(arrowInfo.jointCode)
    } else {
        arrowInfo.jointCode
    }
    
    val limbData = getLimbData(jointCode, isFrontCamera)
    // ...
}
```

---

## 📐 استخراج الأضلاع من الكود الموجود

### ✅ لا نحتاج ملف جديد - نستخدم JointLandmarkMapping!

الكود الحالي بيرجع 3 نقاط مرتبة لكل زاوية:

```kotlin
// من JointLandmarkMapping.kt - موجود!
fun getLandmarksForAngle(jointCode: String): List<Int> {
    return when (jointCode.lowercase()) {
        "left_elbow" -> listOf(11, 13, 15)  // shoulder, elbow, wrist
        "right_elbow" -> listOf(12, 14, 16)
        "left_knee" -> listOf(23, 25, 27)   // hip, knee, ankle
        // ...
    }
}
```

**استخراج الأضلاع:**
```kotlin
fun getLimbEndpoints(jointCode: String): Pair<Int, Int>? {
    val landmarks = JointLandmarkMapping.getLandmarksForAngle(jointCode)
    if (landmarks.size != 3) return null
    
    val (upperEnd, center, lowerEnd) = landmarks
    return Pair(upperEnd, lowerEnd)  // upper limb = center↔upperEnd, lower limb = center↔lowerEnd
}
```

---

## 📊 نظام الحالات البصرية (Visual State Machine)

### State 1: ✅ OPTIMAL (الأداء الصحيح)

**ماذا يحدث:** المتدرب في UP_ZONE أو DOWN_ZONE بشكل صحيح.

**ما يظهر على كل مفصل PRIMARY:**

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│         ○ ← Tracked joints فقط                     │
│        ╱│╲                                          │
│       ╱ │ ╲   خطوط خفيفة جداً (20% opacity)        │
│         │                                           │
│         ● ← المفصل الأوسط                          │
│         ┃🟢 ← Line Indicator قصير (أخضر)           │
│         ▼                                           │
│         ○                                           │
│                                                     │
│     ✨ Subtle Glow (breathing animation)            │
│        لون أخضر هادئ - نبض بطيء                    │
│                                                     │
└─────────────────────────────────────────────────────┘
```

| العنصر | الوصف | التقنية |
|--------|-------|---------|
| Skeleton | Tracked joints فقط | `opacity: 20%` |
| Line Indicator | قصير (20-40% من الطول) | أخضر من `ArcColorCalculator` |
| Glow | نبض بطيء (3 ثواني) | `BlurMaskFilter + ValueAnimator` |
| Vignette | ❌ لا تظهر | - |
| Text | ❌ لا يظهر | - |

---

### State 2: ⚠️ WARNING (اقتراب من الحد)

**ماذا يحدث:** `isWarning = true` على أي مفصل PRIMARY.

**ما يظهر:**

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│                    ○                                │
│                    │                                │
│                    │                                │
│                    ● ← المفصل الأوسط               │
│                    ┃🟢                              │
│                    ┃🟡  ← Line Indicator متوسط     │
│                    ┃🟠      (أخضر→أصفر→برتقالي)    │
│                    ▼                                │
│                    ○                                │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │  Vignette: Amber glow خفيف على الحواف       │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  📌 يظهر على كل مفصل PRIMARY فيه warning          │
│                                                     │
└─────────────────────────────────────────────────────┘
```

| العنصر | الوصف | التقنية |
|--------|-------|---------|
| Line Indicator | متوسط (50-70% من الطول) | تدرج من `ArcColorCalculator` |
| Stroke | أعرض قليلاً + pulsing بسيط | للـ Color-blind support |
| Skeleton | شفاف جداً | `opacity: 10%` |
| Vignette | توهج برتقالي خفيف | `alpha: 0.15` |

---

### State 3: 🚨 ERROR (خطأ واضح)

**ماذا يحدث:** `isError = true` (TOO_HIGH أو TOO_LOW).

**ما يظهر:**

```
┌─────────────────────────────────────────────────────┐
│ ╔═══════════════════════════════════════════════╗   │
│ ║             VIGNETTE: Red Pulse               ║   │
│ ╠═══════════════════════════════════════════════╣   │
│ ║                                               ║   │
│ ║                    ○                          ║   │
│ ║                    ▲                          ║   │
│ ║                    ┃🔴                        ║   │
│ ║                    ┃🟠                        ║   │
│ ║                    ┃🟡  ← Line Indicator      ║   │
│ ║                    ┃🟢      (أقصى طول)        ║   │
│ ║                    ┃🟡                        ║   │
│ ║                    ● ← المفصل (كبير + توهج)   ║   │
│ ║                    │                          ║   │
│ ║                    ○                          ║   │
│ ║                                               ║   │
│ ║    ┌─────────────────────────────────┐       ║   │
│ ║    │   🔴 Bend your elbow more       │       ║   │
│ ║    └─────────────────────────────────┘       ║   │
│ ╚═══════════════════════════════════════════════╝   │
│                                                     │
│  📌 يظهر على كل مفصل PRIMARY فيه error             │
│                                                     │
└─────────────────────────────────────────────────────┘
```

| العنصر | الوصف | التقنية |
|--------|-------|---------|
| Line Indicator | كامل (100% من الطول) | تدرج كامل من `ArcColorCalculator` |
| Stroke | أعرض + نبض أسرع | للـ Color-blind + Peripheral |
| Joint Point | أكبر (18px) + توهج | لفت الانتباه |
| Message | جملة قصيرة واحدة | `GlassmorphicMessageView` |
| Vignette | نبض أحمر قوي | `pulse animation` |
| Skeleton | مخفي أو شفاف جداً | `opacity: 5%` |

---

### State 4: 📐 POSITION ERROR (خطأ محاذاة)

**ماذا يحدث:** خطأ في المحاذاة (مثل الركبة تتجاوز القدم).

**تحسين: عرض خطأ واحد فقط (الأعلى severity)**

```kotlin
// اختيار أول ERROR، ثم أول WARNING، ثم أول TIP
fun getPrimaryPositionError(result: PositionValidationResult): PositionError? {
    return result.errors.firstOrNull()
        ?: result.warnings.firstOrNull()
        ?: result.tips.firstOrNull()
}
```

**ما يظهر:**

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│                     ○ (hip)                         │
│                     │                               │
│                     │                               │
│               ┌─────●─────┐  ← Bracket highlight   │
│               │   KNEE    │     (خطأ واحد فقط)     │
│               └─────┬─────┘                         │
│                     │                               │
│                     ▼                               │
│               ┌─────●─────┐                         │
│               │   TOE     │                         │
│               └───────────┘                         │
│                                                     │
│    ┌─────────────────────────────────────────┐     │
│    │  ⚠️ Knee over toe - push hips back     │     │
│    └─────────────────────────────────────────┘     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## 🎨 تحسينات Color-blind + Peripheral Vision

### "شكل بدون لون" - إضافة Shape Cues

بدون تغيير كبير، نضيف تمييز بالشكل بجانب اللون:

```
┌────────────────────────────────────────────────────────────────┐
│                    Shape + Motion Cues                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│   OPTIMAL:                                                     │
│   ├─ Stroke width: 6dp (عادي)                                  │
│   ├─ Animation: breathing بطيء (3 ثواني)                       │
│   └─ Joint size: 10px (عادي)                                   │
│                                                                │
│   WARNING:                                                     │
│   ├─ Stroke width: 8dp (أعرض)                                  │
│   ├─ Animation: pulsing متوسط (1.5 ثانية)                      │
│   └─ Joint size: 14px (أكبر قليلاً)                            │
│                                                                │
│   ERROR:                                                       │
│   ├─ Stroke width: 10dp (أعرض)                                 │
│   ├─ Animation: pulsing سريع (0.8 ثانية)                       │
│   └─ Joint size: 18px (كبير) + outer ring                      │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### الكود المقترح

```kotlin
data class LineStyle(
    val strokeWidth: Float,
    val jointRadius: Float,
    val pulseSpeed: Long,  // milliseconds per cycle
    val showOuterRing: Boolean
)

fun getLineStyle(arrowInfo: JointArrowInfo, density: Float): LineStyle {
    return when {
        arrowInfo.isError -> LineStyle(
            strokeWidth = 10f * density,
            jointRadius = 18f,
            pulseSpeed = 800L,
            showOuterRing = true
        )
        arrowInfo.isWarning -> LineStyle(
            strokeWidth = 8f * density,
            jointRadius = 14f,
            pulseSpeed = 1500L,
            showOuterRing = false
        )
        else -> LineStyle(
            strokeWidth = 6f * density,
            jointRadius = 10f,
            pulseSpeed = 3000L,
            showOuterRing = false
        )
    }
}
```

---

## 🔄 قواعد الانتقال (Transition Rules)

```
┌────────────────────────────────────────────────────────────────┐
│                    Transition Rules                             │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1. ZONE-BASED HYSTERESIS (موجود في Engine)                   │
│     ├─ الـ Line Indicator يعتمد على arrowInfo.zone            │
│     └─ لا يعيد حساب zones (يستخدم المحسوب في FormValidator)   │
│                                                                │
│  2. COLOR SMOOTHING (موجود في SkeletonOverlayView)            │
│     ├─ COLOR_LERP_FACTOR = 0.25f                               │
│     └─ smooth transition بين الألوان                           │
│                                                                │
│  3. POSITION ERROR: واحد فقط                                   │
│     ├─ ERROR أولاً                                             │
│     ├─ ثم WARNING                                              │
│     └─ ثم TIP                                                  │
│                                                                │
│  4. MULTI-JOINT DISPLAY                                        │
│     └─ Line Indicator يظهر على كل مفصل PRIMARY               │
│        (لا نختار مفصل واحد)                                    │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 🎨 Visual Elements Redesign

### 1. الـ Skeleton الجديد

```
قبل (OLD):                          بعد (NEW):
┌─────────────────┐                ┌─────────────────┐
│     ○           │                │                 │
│    /│\          │                │                 │
│   / │ \         │                │        ●        │  ← tracked joint فقط
│  ○  │  ○        │       →        │       /│\       │
│     │           │                │      / │ \      │
│    / \          │                │        ●        │  ← tracked joint فقط
│   ○   ○         │                │                 │
│   │   │         │                │                 │
│   ○   ○         │                │                 │
└─────────────────┘                └─────────────────┘
   33 نقطة                            3-4 نقاط فقط
   كل الخطوط                          خطوط الـ tracked
```

### 2. الـ Arc → Line Indicator

```
قبل (OLD - Arc):                    بعد (NEW - Line):
┌─────────────────┐                ┌─────────────────┐
│                 │                │        ○        │
│   ╭─────────╮   │                │        │        │
│  ╱───────────╲  │       →        │        │        │
│ ╱─────●───────╲ │                │        ● ← المفصل│
│ ╲─────────────╱ │                │        ┃🟢      │
│  ╲───────────╱  │                │        ▼        │
│   ╰─────────╯   │                │        ○        │
│                 │                │                 │
│  Arc حول المفصل │                │  خط على الـ Limb │
│  (يشغل مساحة)   │                │  (لا مساحة إضافية)│
└─────────────────┘                └─────────────────┘
```

### 3. Direction = اتجاه الخط نفسه

```
┌────────────────────────────────────────────────────────────┐
│              Direction = الـ Limb المرسوم عليه             │
├────────────────────────────────────────────────────────────┤
│                                                            │
│    Zone = UP_ZONE/TOO_HIGH  → الخط على Upper Limb         │
│    Zone = DOWN_ZONE/TOO_LOW → الخط على Lower Limb         │
│    Zone = TRANSITION        → الخط مختفي (طول = 0)        │
│                                                            │
│    لا حاجة لأيقونات أو أسهم إضافية!                       │
│    الخط نفسه هو المؤشر والاتجاه                           │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 📁 خطة التنفيذ التقنية

### Phase 1: إضافة LineRangeIndicator.kt (ملف جديد)

```kotlin
package com.trainingvalidator.poc.overlay

import android.graphics.*
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.training.engine.JointArrowInfo
import com.trainingvalidator.poc.training.engine.JointZone

/**
 * LineRangeIndicator - رسم خط ملون على الـ Limb المناسب
 * 
 * يستخدم:
 * - JointLandmarkMapping.getLandmarksForAngle() للحصول على الأضلاع
 * - ArcColorCalculator للتلوين (إعادة استخدام)
 * - arrowInfo.zone لتحديد أي ضلع (UP_ZONE→upper, DOWN_ZONE→lower)
 */
class LineRangeIndicator {
    
    enum class LimbType { UPPER, LOWER, NONE }
    
    // Reusable paint objects
    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    
    private val glowPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    /**
     * تحديد أي ضلع نرسم عليه بناءً على الـ Zone
     */
    fun getTargetLimb(zone: JointZone): LimbType {
        return when (zone) {
            JointZone.UP_ZONE, JointZone.TOO_HIGH -> LimbType.UPPER
            JointZone.DOWN_ZONE, JointZone.TOO_LOW -> LimbType.LOWER
            JointZone.TRANSITION -> LimbType.NONE
        }
    }
    
    /**
     * حساب طول الخط بناءً على الموقع في الـ Zone
     * 
     * @param arrowInfo بيانات المفصل
     * @param maxLength أقصى طول (نصف المسافة بين المفصلين)
     * @return طول الخط بالـ pixels
     */
    fun calculateLineLength(
        arrowInfo: JointArrowInfo,
        maxLength: Float
    ): Float {
        val zone = arrowInfo.zone
        val angle = arrowInfo.currentAngle
        
        return when (zone) {
            JointZone.TRANSITION -> 0f  // مختفي
            
            JointZone.UP_ZONE -> {
                // المسافة من مركز الـ UP zone للحد
                val center = (arrowInfo.upRangeMin + arrowInfo.upRangeMax) / 2
                val halfRange = (arrowInfo.upRangeMax - arrowInfo.upRangeMin) / 2
                if (halfRange <= 0) return 0f
                
                val distFromCenter = kotlin.math.abs(angle - center)
                val ratio = (distFromCenter / halfRange).coerceIn(0.0, 1.0)
                (ratio * maxLength).toFloat()
            }
            
            JointZone.DOWN_ZONE -> {
                val center = (arrowInfo.downRangeMin + arrowInfo.downRangeMax) / 2
                val halfRange = (arrowInfo.downRangeMax - arrowInfo.downRangeMin) / 2
                if (halfRange <= 0) return 0f
                
                val distFromCenter = kotlin.math.abs(angle - center)
                val ratio = (distFromCenter / halfRange).coerceIn(0.0, 1.0)
                (ratio * maxLength).toFloat()
            }
            
            JointZone.TOO_HIGH, JointZone.TOO_LOW -> maxLength  // أقصى طول
        }
    }
    
    /**
     * رسم الخط الملون
     */
    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        targetX: Float,
        targetY: Float,
        lineLength: Float,
        arrowInfo: JointArrowInfo,
        style: LineStyle,
        density: Float
    ) {
        if (lineLength <= 0) return
        
        // حساب اتجاه الخط
        val dx = targetX - centerX
        val dy = targetY - centerY
        val fullLength = kotlin.math.sqrt(dx * dx + dy * dy)
        if (fullLength <= 0) return
        
        // تطبيع الاتجاه
        val nx = dx / fullLength
        val ny = dy / fullLength
        
        // نقطة النهاية بناءً على الطول المحسوب
        val endX = centerX + nx * lineLength
        val endY = centerY + ny * lineLength
        
        // اللون من ArcColorCalculator
        val color = ArcColorCalculator.getColorForAngle(
            arrowInfo.currentAngle,
            arrowInfo.upRangeMin,
            arrowInfo.upRangeMax,
            arrowInfo.downRangeMin,
            arrowInfo.downRangeMax
        )
        
        // رسم الـ Glow (إذا كان error أو warning)
        if (arrowInfo.isError || arrowInfo.isWarning) {
            glowPaint.color = color
            glowPaint.alpha = 80
            glowPaint.strokeWidth = style.strokeWidth * 2
            glowPaint.maskFilter = BlurMaskFilter(
                style.strokeWidth,
                BlurMaskFilter.Blur.NORMAL
            )
            canvas.drawLine(centerX, centerY, endX, endY, glowPaint)
            glowPaint.maskFilter = null
        }
        
        // رسم الخط الرئيسي
        linePaint.color = color
        linePaint.strokeWidth = style.strokeWidth
        canvas.drawLine(centerX, centerY, endX, endY, linePaint)
    }
}

/**
 * Style للـ Line بناءً على الحالة
 */
data class LineStyle(
    val strokeWidth: Float,
    val jointRadius: Float,
    val pulseSpeed: Long,
    val showOuterRing: Boolean
)
```

### Phase 2: تعديل SkeletonOverlayView

#### التغييرات المطلوبة:

| الدالة | التغيير |
|--------|---------|
| `drawArcRangeIndicators()` | استبدال بـ `drawLineRangeIndicators()` |
| `onDraw()` | استدعاء الدالة الجديدة |
| إضافة | `LineRangeIndicator` instance |
| إضافة | `getLineStyle()` helper |

```kotlin
// في SkeletonOverlayView.kt

// إضافة instance
private val lineRangeIndicator = LineRangeIndicator()

// استبدال drawArcRangeIndicators بـ:
private fun drawLineRangeIndicators(
    canvas: Canvas,
    landmarks: List<SmoothedLandmark>
) {
    val density = resources.displayMetrics.density
    
    for ((jointCode, arrowInfo) in jointArrowInfos) {
        // تخطي غير الـ PRIMARY إذا أردنا
        // (حالياً نعرض كل الـ PRIMARY)
        
        // تحديد أي limb نرسم عليه
        val limbType = lineRangeIndicator.getTargetLimb(arrowInfo.zone)
        if (limbType == LineRangeIndicator.LimbType.NONE) continue
        
        // الحصول على الـ landmarks
        val limbLandmarks = JointLandmarkMapping.getLandmarksForAngle(jointCode)
        if (limbLandmarks.size != 3) continue
        
        val (upperIdx, centerIdx, lowerIdx) = limbLandmarks
        
        // تطبيق mirroring إذا front camera
        val effectiveUpperIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(upperIdx) else upperIdx
        val effectiveCenterIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(centerIdx) else centerIdx
        val effectiveLowerIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(lowerIdx) else lowerIdx
        
        // التأكد من visibility
        if (effectiveCenterIdx >= landmarks.size) continue
        val centerLm = landmarks[effectiveCenterIdx]
        if (centerLm.visibility < visibilityThreshold) continue
        
        // تحديد الـ target landmark
        val targetIdx = if (limbType == LineRangeIndicator.LimbType.UPPER) {
            effectiveUpperIdx
        } else {
            effectiveLowerIdx
        }
        if (targetIdx >= landmarks.size) continue
        val targetLm = landmarks[targetIdx]
        if (targetLm.visibility < visibilityThreshold) continue
        
        // حساب المواقع
        val centerX = centerLm.x * imageWidth * scaleFactor
        val centerY = centerLm.y * imageHeight * scaleFactor
        val targetX = targetLm.x * imageWidth * scaleFactor
        val targetY = targetLm.y * imageHeight * scaleFactor
        
        // حساب أقصى طول (نصف المسافة)
        val fullDistance = kotlin.math.sqrt(
            (targetX - centerX) * (targetX - centerX) +
            (targetY - centerY) * (targetY - centerY)
        )
        val maxLength = fullDistance * 0.5f
        
        // حساب الطول الفعلي
        val lineLength = lineRangeIndicator.calculateLineLength(arrowInfo, maxLength)
        
        // الحصول على الـ style
        val style = getLineStyle(arrowInfo, density)
        
        // رسم الخط
        lineRangeIndicator.draw(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            targetX = targetX,
            targetY = targetY,
            lineLength = lineLength,
            arrowInfo = arrowInfo,
            style = style,
            density = density
        )
    }
}

private fun getLineStyle(arrowInfo: JointArrowInfo, density: Float): LineStyle {
    return when {
        arrowInfo.isError -> LineStyle(
            strokeWidth = 10f * density,
            jointRadius = 18f,
            pulseSpeed = 800L,
            showOuterRing = true
        )
        arrowInfo.isWarning -> LineStyle(
            strokeWidth = 8f * density,
            jointRadius = 14f,
            pulseSpeed = 1500L,
            showOuterRing = false
        )
        else -> LineStyle(
            strokeWidth = 6f * density,
            jointRadius = 10f,
            pulseSpeed = 3000L,
            showOuterRing = false
        )
    }
}
```

### Phase 3: تحديث onDraw()

```kotlin
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val currentLandmarks = landmarks ?: return
    if (currentLandmarks.isEmpty()) return

    // Draw connections (behind everything)
    drawConnections(canvas, currentLandmarks)
    
    // Draw glow on tracked joints when correct
    if (isTrainingMode && errorJointCodes.isEmpty()) {
        drawCorrectFormGlow(canvas, currentLandmarks)
    }
    
    // Draw landmark points
    drawLandmarks(canvas, currentLandmarks)
    
    // ✅ NEW: Draw Line Range Indicators (بدل Arc)
    if (isTrainingMode && jointArrowInfos.isNotEmpty()) {
        drawLineRangeIndicators(canvas, currentLandmarks)
    }
    
    // Draw position errors (واحد فقط - الأعلى severity)
    if (isTrainingMode && positionErrors.isNotEmpty()) {
        drawPositionErrors(canvas, currentLandmarks)
    }
    
    // Draw angles (فقط على error)
    if (showAngles) {
        drawAngles(canvas, currentLandmarks)
    }
}
```

### Phase 4: تعديل drawPositionErrors (واحد فقط)

```kotlin
private fun drawPositionErrors(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
    // اختيار خطأ واحد فقط (الأعلى severity)
    val primaryError = positionErrors
        .filter { it.severity == CheckSeverity.ERROR }
        .firstOrNull()
        ?: positionErrors.filter { it.severity == CheckSeverity.WARNING }.firstOrNull()
        ?: positionErrors.firstOrNull()
        ?: return
    
    // رسم هذا الخطأ فقط
    drawSinglePositionError(canvas, landmarks, primaryError)
}

private fun drawSinglePositionError(
    canvas: Canvas,
    landmarks: List<SmoothedLandmark>,
    error: PositionError
) {
    // ... الكود الحالي لرسم خطأ واحد
}
```

---

## 📋 ملخص التغييرات النهائي

| العنصر | الحالي | المقترح |
|--------|--------|---------|
| **Skeleton points** | 33 نقطة | Tracked فقط |
| **Skeleton opacity** | 60% | 20% (optimal), 10% (warning), 5% (error) |
| **Range Indicator** | Arc حول المفصل | Line على الـ Limb |
| **Indicator display** | Arc على كل tracked | Line على كل PRIMARY |
| **Direction detection** | ❌ غير موجود | ✅ بناءً على Zone (UP/DOWN) |
| **Color logic** | ArcColorCalculator | نفسه (إعادة استخدام) |
| **Hysteresis** | في Engine | نستخدمه مباشرة (لا جديد) |
| **Position errors** | خطوط متعددة | خطأ واحد فقط |
| **Shape cues** | ❌ لون فقط | ✅ لون + عرض + نبض |
| **Mirroring** | موجود | يُستخدم في Line Indicator |

---

## 📂 الملفات المطلوب إنشاؤها/تعديلها

### ملف جديد:
1. `LineRangeIndicator.kt` - رسم الخط المتدرج

### ملفات للتعديل:
1. `SkeletonOverlayView.kt`:
   - إضافة `lineRangeIndicator` instance
   - استبدال `drawArcRangeIndicators()` بـ `drawLineRangeIndicators()`
   - تعديل `drawPositionErrors()` لعرض خطأ واحد

### ملفات تبقى كما هي (إعادة استخدام):
1. `ArcColorCalculator.kt` - للتلوين
2. `JointLandmarkMapping.kt` - للحصول على الأضلاع
3. `FormValidator.kt` - للـ hysteresis والـ zones

### ملفات للحذف (اختياري - بعد التأكد):
1. `ArcRangeIndicator.kt`
2. `ArcRangeData.kt`

---

## ✅ ملخص التصحيحات والإضافات

### تصحيحات:
1. ✅ **ربط اختيار الـ Limb بالـ Zone** بدلاً من transitionCenter
2. ✅ **إعادة استخدام ArcColorCalculator** بدلاً من منطق جديد
3. ✅ **تثبيت Transition = Yellow**
4. ✅ **إضافة Mirroring considerations**

### إضافات:
1. ✅ **استغلال Hysteresis الموجود** في FormValidator
2. ✅ **PositionErrors: عرض واحد فقط** (الأعلى severity)
3. ✅ **Shape Cues للـ Color-blind** (عرض + نبض)
4. ✅ **Visuals على كل مفصل PRIMARY** (وليس واحد فقط)
