| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Android exercise JSON contract (state-based) |
| **Code** | `backend/src/lib/types/android-schema.ts`, `backend/src/modules/exercises/json-builder.ts`, `kmp-app/core/training-engine/.../ExerciseConfigModels.kt` |
| **Supersedes** | difficulty-level ranges, `push_pull`, per-difficulty `positionChecks.thresholds` |
| **Verified** | 2026-06-22 |

# Exercise JSON Schema — Backend → Mobile Contract

## الهدف

هذا المستند يصف **شكل JSON التمرين** الذي يُصدّره الباك إند ويستهلكه تطبيق Android/KMP.

- **مصدر الحقيقة في TypeScript:** `backend/src/lib/types/android-schema.ts`
- **التحويل من DB:** `backend/src/modules/exercises/json-builder.ts` (النقطة الوحيدة للتصدير)
- **الاستهلاك في الموبايل:** `ExerciseConfig` / `ExerciseConfigRecord` عبر mobile sync أو assets

> **نظام state-based:** لا توجد `difficultyLevels` ولا `beginner` / `normal` / `advanced`. الزوايا والرسائل والعتبات تُعرَّف بـ **حالات** (`perfect`, `normal`, `pad`, `warning`, `danger`).

---

## مسار البيانات

```
Admin Exercise Wizard → Prisma (Exercise, PoseVariant, PositionCheck, …)
                              ↓
                    json-builder.buildExerciseConfig()
                              ↓
              mobile-sync / workout-templates export → Android TrainingEngine
```

---

## قواعد عامة

1. **لا ترسل `null`** — احذف المفتاح إذا لم تكن القيمة مطلوبة.
2. **`LocalizedText`** يفضّل وجود `ar` و `en` (واختياريًا `audioAr` / `audioEn`).
3. **أولوية الحالة** عند تداخل النطاقات: `danger` > `warning` > `perfect` > `normal` > `pad`.
4. **`countingMethod` المسموح:** `"up_down"` | `"hold"` فقط — **`push_pull` مُزال**.
5. **`repCountingConfig`** على مستوى التمرين (root) — **ليس** داخل مستويات صعوبة.

---

## 1) Root: `ExerciseConfig`

### 1.1 الحقول (Top-level)

| الحقل | النوع | مطلوب | ملاحظات |
|-------|------|--------|---------|
| `name` | `LocalizedText` | ✅ | |
| `description` | `LocalizedText` | ❌ | |
| `instructions` | `LocalizedText` | ❌ | |
| `imageUrl` | `string` | ❌ | صورة أساسية من الـ media |
| `category` | `Category` | ✅ | `{ code, name }` |
| `countingMethod` | enum | ✅ | `up_down` \| `hold` |
| `muscles` | `string[]` | ❌ | default `[]` |
| `equipment` | `string[]` | ❌ | default `[]` |
| `tags` | `string[]` | ❌ | default `[]` |
| `repCountingConfig` | `RepCountingConfig` | ✅ | على مستوى التمرين |
| `poseVariants` | `PoseVariantConfig[]` | ✅ عمليًا | عنصر واحد على الأقل |
| `supportsWeight` | `boolean` | ❌ | |
| `minWeight` / `maxWeight` / `defaultWeight` | `number` | ❌ | kg |
| `reportMetrics` | `ReportMetricsConfig` | ❌ | يُولَّد تلقائيًا إن لم يُحدَّد |
| `isBilateral` | `boolean` | ❌ | تناوب جانبي (ليس مجرد `pairedWith`) |
| `bilateralConfig` | `BilateralConfig` | ❌ | مع `isBilateral: true` |
| `hasPositionChecks` | `boolean` | ❌ | flag اشتقاقي |

**محذوف من العقد:** `difficultyLevels[]`, أي حقول `beginner`/`normal`/`advanced` في النطاقات.

### 1.2 `CountingMethod`

| القيمة | السلوك |
|--------|--------|
| `"up_down"` | عدّ تكرارات: `IDLE → START(top) → DOWN → BOTTOM → UP → START` — اكتمال التكرار عند العودة لـ top |
| `"hold"` | مؤقت ثبات: `IDLE ↔ COUNT` — المؤقت في phase `count` |

---

## 2) `RepCountingConfig`

```json
{
  "reps": 12,
  "minRepIntervalMs": 1500,
  "maxRepIntervalMs": 5000,
  "duration": 30,
  "gracePeriodMs": 2500
}
```

| الحقل | `up_down` | `hold` |
|-------|-----------|--------|
| `reps` | ✅ مطلوب عمليًا | — |
| `minRepIntervalMs` / `maxRepIntervalMs` | اختياري | — |
| `duration` | — | ✅ ثوانٍ، مطلوب عمليًا |
| `gracePeriodMs` | — | اختياري |

---

## 3) `PoseVariantConfig`

```json
{
  "name": { "ar": "...", "en": "..." },
  "posePosition": "standing_side",
  "positionImageUrl": "https://...",
  "expectedPostures": ["standing"],
  "expectedDirections": ["side"],
  "expectedRegions": ["full_body"],
  "trackedJoints": [],
  "positionChecks": [],
  "feedbackMessages": { "motivational": [], "tips": [] },
  "messageAssignments": []
}
```

| الحقل | مطلوب | ملاحظات |
|-------|--------|---------|
| `name` | ✅ | |
| `posePosition` | ✅ | كود من catalog الـ pose positions (انظر §3.1) |
| `positionImageUrl` | ❌ | |
| `expectedPostures` | ❌ | default `["any"]` من الـ builder |
| `expectedDirections` | ❌ | |
| `expectedRegions` | ❌ | |
| `trackedJoints` | ✅ عمليًا | joint primary واحد على الأقل |
| `positionChecks` | ❌ | |
| `feedbackMessages` | ❌ | `motivational`, `tips` فقط — **لا** `common_mistake` |
| `messageAssignments` | ❌ | مراجع مكتبة الرسائل |

**Legacy (الموبايل يقبلها للتوافق فقط):** `cameraPosition`, `expectedFacingDirection` — **الباك لا يصدّرها**؛ يستخدم `posePosition` + `expectedPostures/Directions/Regions`.

### 3.1 `PosePosition` (أمثلة)

`standing_front`, `standing_back`, `standing_side`, `standing_side_left`, `standing_side_right`, `sitting_front`, `prone_side`, `supine_front`, `side_lying`, … — القائمة الكاملة في `android-schema.ts` → `PosePosition`.

---

## 4) `StateRanges` — النظام الأساسي للزوايا

```json
{
  "perfect": { "min": 150, "max": 180 },
  "normal":  { "min": 130, "max": 180 },
  "pad":     { "min": 40,  "max": 100 },
  "warning": { "min": 30,  "max": 40 },
  "danger":  { "min": 0,   "max": 30 }
}
```

| المفتاح | مطلوب | التأثير |
|---------|--------|---------|
| `perfect` | ✅ | النطاق المثالي — أعلى أولوية للعد (بعد danger/warning) |
| `normal` | ❌ | جيد — يُحتسب للتكرار |
| `pad` | ❌ | مقبول — يُحتسب للتكرار بأولوية أقل |
| `warning` | ❌ | تحذير — **لا يُحتسب** للتكرار |
| `danger` | ❌ | خطر — **يبطل** التكرار / تنبيه قوي |

- الوحدة: **درجات (0–180)**.
- للـ `up_down`: يفضّل أن `upRange.effectiveMin` > `downRange.effectiveMax` (منطقة انتقال بين up و down).

**محذوف:** `DifficultyRanges` بمفاتيح `beginner` / `normal` / `advanced`.

---

## 5) `TrackedJoint`

### 5.1 حقول مشتركة

| الحقل | مطلوب | ملاحظات |
|-------|--------|---------|
| `joint` | ✅ | كود المفصل (§5.5) |
| `role` | ✅ | `primary` \| `secondary` |
| `startPose` | ✅ | `AngleRange` — شاشة Setup Pose |
| `stateMessages` | ❌ | رسائل حسب الحالة (§6) |
| `pairedWith` | ❌ | مفصل مقترن (تماثل) |
| `invertIndicator` | ❌ | عكس اتجاه المؤشر البصري |
| `trackingMode` | ❌ | `two_sides` (default) \| `any_side` |

### 5.2 Primary — `up_down`

```json
{
  "joint": "left_knee",
  "role": "primary",
  "startPose": { "min": 120, "max": 180 },
  "upRange": { "perfect": { "min": 150, "max": 180 } },
  "downRange": { "perfect": { "min": 60, "max": 90 }, "warning": { "min": 30, "max": 40 } }
}
```

- **`upRange`** + **`downRange`**: كلاهما `StateRanges` — مطلوبان.

### 5.3 Primary — `hold`

```json
{
  "joint": "left_hip",
  "role": "primary",
  "startPose": { "min": 150, "max": 180 },
  "range": { "perfect": { "min": 160, "max": 180 }, "normal": { "min": 150, "max": 180 } }
}
```

- **`range`** واحد (`StateRanges`) — **ليس** `upRange`/`downRange`.
- منطقة الـ hold في المحرك = phase `count`؛ يُفضّل تقارب `range` مع وضع الثبات.

### 5.4 Secondary

```json
{
  "joint": "left_hip",
  "role": "secondary",
  "startPose": { "min": 55, "max": 180 },
  "range": { "perfect": { "min": 70, "max": 180 } },
  "phaseRanges": {
    "top":    { "perfect": { "min": 165, "max": 180 } },
    "bottom": { "perfect": { "min": 70, "max": 100 } }
  },
  "phaseStateMessages": {
    "bottom": { "warning": { "ar": "...", "en": "..." } }
  }
}
```

| الحقل | ملاحظات |
|-------|---------|
| `range` | ✅ قالب أساسي — **lowercase** `range` (ليس `"Range"`) |
| `phaseRanges` | ❌ إن وُجد: الموبايل يستخدم **فقط** المراحل المعرّفة — **لا fallback** إلى `range` |
| `phaseStateMessages` | ❌ رسائل per-phase للـ secondary |

مفاتيح `phaseRanges` / `phaseStateMessages`: `top`, `down`, `bottom`, `up`.

### 5.5 أكواد `joint` (angle-based)

- **Arms:** `left_elbow`, `right_elbow`, `left_shoulder`, `right_shoulder`
- **Torso:** `left_hip`, `right_hip`, `spine`
- **Legs:** `left_knee`, `right_knee`, `left_ankle`, `right_ankle`

> `spine` كـ **PRIMARY** لا يظهر في Setup Pose — استخدمه **secondary** فقط.

---

## 6) `StateMessages`

صيغتان:

**1. بسيطة (hold أو رسالة عامة):**
```json
{ "perfect": { "ar": "ممتاز", "en": "Perfect" } }
```

**2. حسب المنطقة (up_down):**
```json
{
  "perfect": {
    "up":   { "ar": "...", "en": "..." },
    "down": { "ar": "...", "en": "..." }
  }
}
```

مفاتيح الحالة: `perfect`, `normal`, `pad`, `warning`, `danger` — كلها اختيارية.

**محذوف:** `errorMessages.tooLow` / `tooHigh` — استُبدلت بـ `stateMessages`.

---

## 7) `PositionCheck`

```json
{
  "id": "knee_over_toe",
  "type": "forward_comparison",
  "landmarks": { "primary": "left_knee", "secondary": "left_foot_index" },
  "condition": { "operator": "should_not_exceed", "threshold": 0.05 },
  "activePhases": ["down", "bottom"],
  "errorMessage": { "ar": "...", "en": "..." },
  "severity": "warning",
  "cooldownMs": 2000,
  "minErrorFrames": 5
}
```

| الحقل | مطلوب | ملاحظات |
|-------|--------|---------|
| `id` | ✅ | unique داخل الـ variant |
| `type` | ✅ | انظر §7.3 |
| `landmarks` | ✅ | `primary`, `secondary`; `tertiary`/`quaternary` لبعض الأنواع |
| `condition.operator` | ✅ | §7.4 |
| `condition.threshold` | ✅ | **قيمة واحدة** — ليست per-difficulty |
| `activePhases` | ✅ | §7.2 |
| `errorMessage` | ❌ | inline (ليس من مكتبة الرسائل) |
| `severity` | ✅ | `error` \| `warning` \| `tip` |
| `cooldownMs` | ✅ | default 2000 |
| `minErrorFrames` | ✅ | default 3 |

**محذوف:** `condition.thresholds.beginner/normal/advanced`.

### 7.1 `activePhases`

القيم في JSON (تطابق `PhaseName` في الباك):

| Phase في JSON | يقابل runtime |
|---------------|----------------|
| `all` | كل phase ما عدا IDLE |
| `top` | START (وضع علوي) |
| `down`, `bottom`, `up` | نفس الاسم |
| `count` | hold timer |

**Aliases مقبولة في الموبايل:** `start` → `top`, `hold` → `count`, `idle` → `all` (يُ normaliz في `json-builder`).

### 7.2 `PositionCheckType`

`forward_comparison`, `vertical_comparison`, `sideways_comparison`, `distance_ratio`, `horizontal_alignment`, `vertical_alignment`, `depth_alignment`

### 7.3 `ConditionOperator`

`should_not_exceed`, `should_exceed`, `approximately_equal`, `greater_than_ratio`, `less_than_ratio`

### 7.4 Landmarks

MediaPipe Pose 0–32 — نفس القائمة السابقة (`left_knee`, `left_foot_index`, …). انظر `JointLandmarkMapping` في الموبايل.

---

## 8) الرسائل والمكتبة

### 8.1 `FeedbackMessages`

```json
{
  "motivational": [{ "ar": "...", "en": "..." }],
  "tips": [{ "ar": "...", "en": "..." }]
}
```

### 8.2 `MessageAssignment`

```json
{
  "messageId": "uuid",
  "target": "joint_state",
  "context": "warning",
  "jointCode": "left_knee",
  "zone": "down",
  "sortOrder": 0
}
```

| `target` | الاستخدام |
|----------|-----------|
| `joint_state` | `context` = اسم الحالة (`perfect`, …) |
| `joint_state_phase` | `context` = `phase:state` (مثل `bottom:warning`) |
| `feedback` | `context` = `motivational` \| `tip` |
| `position` | مرتبط بـ `checkId` |

---

## 9) الوزن والمقايس والثنائية

### 9.1 `ReportMetricsConfig`

```json
{
  "primary": ["FORM_SCORE", "ROM"],
  "optional": ["TEMPO", "TUT"],
  "excluded": ["HOLD_DURATION"]
}
```

أكواد المقايس: `FORM_SCORE`, `REP_COUNT`, `DURATION`, `ROM`, `SYMMETRY`, `STABILITY`, `TEMPO`, `TUT`, `HOLD_DURATION`, `ALIGNMENT`, `WEIGHT`, `VOLUME`, `EST_1RM`, … — uppercase في JSON المُصدَّر.

### 9.2 `BilateralConfig`

```json
{
  "switchMode": "every_rep",
  "startSide": "right"
}
```

`switchMode`: `every_rep` | `after_all_reps` — legacy: `switchEvery`.

---

## 10) Validation / Runtime (للباك والأدمن)

يُطبَّق في `validateExerciseConfig()` (`android-schema.ts`) قبل التصدير:

- `name.en` مطلوب.
- `countingMethod` + `repCountingConfig` متسقان (reps vs duration).
- `poseVariants.length >= 1`، وكل variant فيه primary joint واحد على الأقل.
- Primary `up_down`: `upRange` + `downRange` مع `perfect` في كل منهما.
- Primary `hold`: `range` مع `perfect`.
- Secondary: `range` مطلوب؛ `phaseRanges` إن وُجد لا يكون فارغًا.
- Position checks: `threshold` number، landmarks primary+secondary.

**Multi-primary:** المحرك يأخذ thresholds من **أول** primary لكن يحسب **متوسط** زوايا كل primary joints.

---

## 11) مرجع سريع — حقول العقد الحالي

**Root:** `name`, `description`, `instructions`, `imageUrl`, `category`, `countingMethod`, `muscles`, `equipment`, `tags`, `repCountingConfig`, `poseVariants`, `supportsWeight`, `minWeight`, `maxWeight`, `defaultWeight`, `reportMetrics`, `isBilateral`, `bilateralConfig`, `hasPositionChecks`

**PoseVariant:** `name`, `posePosition`, `positionImageUrl`, `expectedPostures`, `expectedDirections`, `expectedRegions`, `trackedJoints`, `positionChecks`, `feedbackMessages`, `messageAssignments`

**TrackedJoint:** `joint`, `role`, `startPose`, `upRange`, `downRange`, `range`, `stateMessages`, `phaseRanges`, `phaseStateMessages`, `pairedWith`, `invertIndicator`, `trackingMode`

**محذوف نهائيًا:** `difficultyLevels`, `level`, `phases[]` داخل difficulty, `push_pull`, `errorMessages`, `"Range"` (capital R), `condition.thresholds.*`, `feedbackMessages.common_mistake`
