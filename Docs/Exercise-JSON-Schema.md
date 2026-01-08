## Exercise JSON Schema (Android POC) — Backend Contract

## الهدف

هذا المستند يوضح **كل الحقول (parameters)** الموجودة في ملفات تمارين الـ JSON داخل:

- `android-poc/app/src/main/assets/exercises/*.json`

ويشرح:

- **أنواع الحقول** (types) + هل هي مطلوبة أم اختيارية
- **Enums** والقيم المسموحة (بالـ string بالضبط)
- **Validation / Runtime expectations** داخل الأندرويد (إيه اللي بيتحقق وإمتى وليه)
- ملاحظات مهمة جدًا على **حساسية أسماء الحقول** (خصوصًا `"Range"` بحرف R كبير)

---

## أين وكيف يتم استخدام الـ JSON داخل الأندرويد

- **التحميل/البارس**: `ExerciseLoader` يقرأ من `assets/exercises/<name>.json` ويعمل parse إلى `ExerciseConfig` باستخدام Gson (Lenient).
- **الاختيار حسب اسم الملف**: اسم التمرين في التطبيق هو **اسم الملف بدون `.json`** (مثال: `squat`).
- **التشغيل**: `TrainingEngine` يبني الـ validators والـ state machine من الـ config:
  - `PhaseStateMachine` (phases / rep counting)
  - `FormValidator` (angle-based validation: upRange/downRange + Range للـ secondary)
  - `PositionValidator` (position-based checks إن وُجدت)
  - `HoldTimer` (للـ HOLD)

مهم: الحقول غير المعروفة (unknown) غالبًا Gson يتجاهلها، لكن **القيم الغلط** في enums أو الحقول الأساسية قد تسبب Null/Crash أثناء التشغيل.

---

## 1) Root Object: `ExerciseConfig`

### 1.1 الحقول (Top-level)

- **`name`**: `LocalizedText` **(Required)**
- **`description`**: `LocalizedText` **(Optional)**
- **`instructions`**: `LocalizedText` **(Optional)**
- **`category`**: `CategoryInfo` **(Required)**
- **`countingMethod`**: `CountingMethod` **(Required, enum string)**
- **`muscles`**: `string[]` **(Optional, default empty)**
- **`equipment`**: `string[]` **(Optional, default empty)**
- **`tags`**: `string[]` **(Optional, default empty)**
- **`poseVariants`**: `PoseVariant[]` **(Required عمليًا)**  
  ملاحظة: الكود يفترض وجود variant على الأقل لأن `TrainingEngine` يستخدم `exerciseConfig.poseVariants[poseVariantIndex]`.

### 1.2 `LocalizedText`

```json
{
  "ar": "نص عربي",
  "en": "English text"
}
```

- **`ar`**: `string` (Optional, default empty)
- **`en`**: `string` (Optional, default empty)

ملاحظة: عمليًا، يفضّل وجود نص واحد على الأقل (ar أو en) للعرض في UI.

### 1.3 `CategoryInfo`

```json
{
  "code": "legs",
  "name": { "ar": "تمارين الأرجل", "en": "Legs" }
}
```

- **`code`**: `string` (مثال: `legs`, `arms`, `core`...)
- **`name`**: `LocalizedText`

### 1.4 `CountingMethod` (enum)

القيم المسموحة (لازم حرفيًا):

- **`"up_down"`** → `CountingMethod.UP_DOWN`
- **`"push_pull"`** → `CountingMethod.PUSH_PULL`
- **`"hold"`** → `CountingMethod.HOLD`

تأثيرها في التشغيل:

- **UP_DOWN**: flow = `IDLE → START → DOWN → BOTTOM → UP → START` (rep completed عند `UP → START`)
- **PUSH_PULL**: flow = `IDLE → START → PUSH → EXTENDED → PULL → START` (rep completed عند `PULL → START`)
- **HOLD**: flow = `IDLE ↔ COUNT` (الـ timer شغال فقط في `COUNT`)

---

## 2) `PoseVariant`

كل تمرين ممكن يحتوي أكثر من variant (مثال: pushup عنده side_view + front_view).

```json
{
  "name": { "ar": "...", "en": "..." },
  "cameraPosition": "side_view",
  "expectedFacingDirection": "auto_detect",
  "trackedJoints": [],
  "positionChecks": [],
  "feedbackMessages": { "motivational": [], "common_mistake": [], "tip": [] },
  "difficultyLevels": []
}
```

- **`name`**: `LocalizedText` **(Required)**
- **`cameraPosition`**: `string` **(Required)**  
  القيم المتوقعة (المستخدمة فعليًا في التحذير):  
  - `"side_view" | "front_view" | "back_view"`  
  ملاحظة: لو اترسل أي string آخر، النظام "هيقبل" (Gson Lenient) لكنه ممكن يقلل فائدة تحذير زاوية الكاميرا في `PositionValidator`.
- **`expectedFacingDirection`**: `FacingDirection` **(Optional)**  
  لو null/غير موجود → يعتبر Auto detect في `PositionValidator`.
- **`trackedJoints`**: `TrackedJoint[]` **(Required عمليًا)**  
  التطبيق يعتمد عليها في: العد + الـ form validation + visibility monitor.
- **`positionChecks`**: `PositionCheck[]` **(Optional)**  
  لو غير موجودة/فارغة → PositionValidator لا يتفعل.
- **`feedbackMessages`**: `FeedbackMessages` **(Optional)**
- **`difficultyLevels`**: `DifficultyLevel[]` **(Required عمليًا)**  
  لازم يحتوي على entry للـ difficulty المطلوب (`beginner/normal/advanced`)، وإلا `TrainingEngine` يرمي exception.

---

## 3) `DifficultyLevel`

```json
{
  "level": "normal",
  "repCountingConfig": { "reps": 12, "minRepIntervalMs": 1500, "maxRepIntervalMs": 5000 },
  "phases": ["start", "down", "bottom", "up"]
}
```

- **`level`**: `DifficultyType` **(Required, enum string)**
- **`repCountingConfig`**: `RepCountingConfig` **(Required)**
- **`phases`**: `string[]` **(Required)**  
  مهم جدًا: داخل الأندرويد **الأسماء نفسها لا تُستخدم**، فقط **عدد العناصر** `phases.size` يُستخدم كـ `numberOfPhases` لحساب `minPhaseDuration`.
  
  ملاحظة: بعض ملفات JSON تحتوي `"hold"` داخل `phases` array (مثل `["start", "hold"]`)، لكن هذا **لا يؤثر** لأن الأسماء لا تُستخدم. الـ phase الفعلي في الأندرويد للـ HOLD exercises هو `count` (ليس `hold`).

### 3.1 `DifficultyType` (enum)

- **`"beginner"`**
- **`"normal"`**
- **`"advanced"`**

---

## 4) `RepCountingConfig`

```json
{
  "reps": 12,
  "minRepIntervalMs": 1500,
  "maxRepIntervalMs": 5000,
  "duration": 30,
  "gracePeriodMs": 2500
}
```

**ملاحظة مهمة**: كل الحقول **optional** ويمكن أن تكون موجودة معًا. الاستخدام يعتمد على `countingMethod`:

- **للتمارين Rep-based (UP_DOWN / PUSH_PULL)**:
  - **`reps`**: `number` (Int, default = 12) **(Required عمليًا)**  
    يستخدم كـ target reps (إلا لو فيه override من workout mode).
  - **`minRepIntervalMs`**: `number` (Long, milliseconds) **(Optional)**  
    يستخدم في:
    - `PhaseStateMachine`: يمنع phase transitions السريعة جدًا عبر `minPhaseDuration` المشتق منه
    - `RepCounter`: safety backup لمنع double-count
  - **`maxRepIntervalMs`**: `number` (Long, milliseconds) **(Optional)**  
    موجود في الـ model، لكن **غير مستخدم فعليًا حاليًا** في الـ engine.

- **للتمارين Hold-based (HOLD)**:
  - **`duration`**: `number` (Int, seconds) **(Required عمليًا)**  
    يتحول داخل الأندرويد إلى milliseconds.
  - **`gracePeriodMs`**: `number` (Long, milliseconds) **(Optional)**  
    يسمح بالخروج من وضع الـ hold مؤقتًا قبل الفشل.

ملاحظة مهمة: الـ `PhaseStateMachine` في وضع HOLD يعتبر **`downRange` هو hold zone** (Phase.COUNT).  
لذلك يفضّل أن تكون `upRange` و`downRange` متطابقة أو متقاربة للـ primary joint في تمارين الـ hold (كما في `plank.json`).

---

## 5) `TrackedJoint`

```json
{
  "joint": "left_knee",
  "role": "primary",
  "startPose": { "min": 120, "max": 180 },
  "upRange": {
    "beginner": { "min": 135, "max": 180 },
    "normal": { "min": 140, "max": 180 },
    "advanced": { "min": 145, "max": 180 }
  },
  "downRange": {
    "beginner": { "min": 50, "max": 110 },
    "normal": { "min": 40, "max": 100 },
    "advanced": { "min": 35, "max": 95 }
  },
  "errorMessages": {
    "tooLow": { "ar": "...", "en": "..." },
    "tooHigh": { "ar": "...", "en": "..." }
  },
  "pairedWith": "right_knee"
}
```

- **`joint`**: `string` **(Required)**  
  لازم يكون من قائمة الـ joint codes المدعومة (قسم 5.5).
- **`role`**: `JointRole` **(Required, enum string)**
- **`startPose`**: `AngleRange` **(Required)**  
  يستخدم في شاشة “Setup Pose” (`PoseValidator`) للتحقق قبل بدء التدريب.
- **`upRange`**: `DifficultyRanges` **(Required للـ PRIMARY)**
- **`downRange`**: `DifficultyRanges` **(Required للـ PRIMARY)**
- **`Range`**: `DifficultyRanges` **(Required للـ SECONDARY)**  
  ملاحظة حرجة جدًا: اسم الحقل في JSON هو **`"Range"`** بحرف R كبير.  
  ده متربط في الكود بـ `@SerializedName("Range")`.
- **`errorMessages`**: `ErrorMessages` **(Required)**
- **`pairedWith`**: `string` **(Optional)**  
  موجود في JSON لبعض التمارين، لكن **غير مستخدم حاليًا** في الكود (محجوز للسيمتري).

### 5.1 `JointRole` (enum)

- **`"primary"`**
- **`"secondary"`**

### 5.2 `AngleRange`

```json
{ "min": 150, "max": 180 }
```

- **`min`**: `number` (Double)
- **`max`**: `number` (Double)

الوحدة: **degrees (0 → 180)**.

### 5.3 `DifficultyRanges`

```json
{
  "beginner": { "min": 150, "max": 180 },
  "normal":   { "min": 160, "max": 180 },
  "advanced": { "min": 165, "max": 180 }
}
```

### 5.4 `ErrorMessages`

```json
{
  "tooLow":  { "ar": "...", "en": "..." },
  "tooHigh": { "ar": "...", "en": "..." }
}
```

- **`tooLow`**: `LocalizedText` **(Required)**  
  يمكن أن يكون empty string (`""`) في بعض الحالات (مثل التمارين الاختبارية).
- **`tooHigh`**: `LocalizedText` **(Required)**  
  يمكن أن يكون empty string (`""`) في بعض الحالات.

ملاحظة: داخل `FormValidator`:

- في الـ UP range:  
  - angle > upRange.max → TOO_HIGH → `tooHigh`
  - angle < upRange.min → TOO_LOW → `tooLow`
- في الـ DOWN range:  
  - angle > downRange.max → TOO_HIGH → `tooHigh` (not bending enough)
  - angle < downRange.min → TOO_LOW → `tooLow` (bending too much)

### 5.5 قائمة `joint` codes المدعومة (Angle-based)

هذه القيم هي التي يستطيع `JointAngleTracker` استخراج زواياها من `JointAngles`:

- **Arms**: `left_elbow`, `right_elbow`, `left_shoulder`, `right_shoulder`
- **Torso**: `left_hip`, `right_hip`, `spine`
- **Legs**: `left_knee`, `right_knee`, `left_ankle`, `right_ankle`

ملاحظة مهمة:

- شاشة الـ Setup Pose (`PoseValidator`) لا تتعامل مع `spine` في `getAngleForJoint()`، لذلك **لا تضع `spine` كـ PRIMARY** وإلا لن يتم تأكيد وضع البداية (لن يظهر في قائمة الـ joints المطلوبة).
- `spine` يمكن استخدامه كـ **SECONDARY** فقط (لأن الـ secondary joints لا يتم التحقق منها في Setup Pose).

---

## 6) `FeedbackMessages`

```json
{
  "motivational": [ { "ar": "...", "en": "..." } ],
  "common_mistake": [ { "ar": "...", "en": "..." } ],
  "tip": [ { "ar": "...", "en": "..." } ]
}
```

- **`motivational`**: `LocalizedText[]` (Optional)
- **`common_mistake`**: `LocalizedText[]` (Optional)  
  ملاحظة: اسم الحقل في JSON هو `common_mistake` (underscore) وهو mapped في الكود بـ `@SerializedName("common_mistake")`.
- **`tip`**: `LocalizedText[]` (Optional)

---

## 7) Position-Based Validation (اختياري): `positionChecks`

### 7.1 `FacingDirection` (enum)

القيم المسموحة:

- `"facing_right"`
- `"facing_left"`
- `"facing_camera"`
- `"facing_away"`
- `"auto_detect"`

### 7.2 `PositionCheck`

```json
{
  "id": "knee_over_toe",
  "type": "forward_comparison",
  "landmarks": { "primary": "left_knee", "secondary": "left_foot_index" },
  "condition": {
    "operator": "should_not_exceed",
    "thresholds": { "beginner": 0.08, "normal": 0.05, "advanced": 0.03 }
  },
  "activePhases": ["down", "bottom"],
  "errorMessage": { "ar": "...", "en": "..." },
  "severity": "warning",
  "cooldownMs": 2000,
  "minErrorFrames": 5
}
```

- **`id`**: `string` **(Required)**  
  لازم يكون unique داخل نفس الـ variant.
- **`type`**: `PositionCheckType` **(Required, enum string)**
- **`landmarks`**: `LandmarkGroup` **(Required)**
- **`condition`**: `PositionCondition` **(Required)**
- **`activePhases`**: `string[]` **(Required)**  
  مهم جدًا: يتم تفعيل الـ check عندما:
  - `activePhases` تحتوي `Phase.name.lowercase()`

  القيم الموثوقة حاليًا (مطابقة لـ `Phase` داخل الأندرويد):
  - `idle`, `start`, `down`, `bottom`, `up`, `push`, `extended`, `pull`, `count`

  ملاحظة مهمة: بعض ملفات JSON تحتوي `"hold"` داخل `activePhases` (مثل `["count", "hold"]`)، لكن الـ phase الفعلي في الأندرويد للـ HOLD exercises هو `count` فقط. يمكنك وضع `"hold"` كـ fallback/alias، لكن **لا تعتمد عليه وحدها** - استخدم `"count"` دائمًا.
- **`errorMessage`**: `LocalizedText` **(Required)**
- **`severity`**: `CheckSeverity` **(Optional, default = warning)**
- **`cooldownMs`**: `number` (Long, ms) **(Optional, default = 2000)**  
  يستخدم داخل `TrainingEngine` لتقليل spam في feedback events لكل check.
- **`minErrorFrames`**: `number` (Int) **(Optional, default = 3)**  
  يستخدم داخل `PositionValidator`: يجب تكرار الخطأ لعدد frames متتالية قبل اعتباره confirmed.
- **`condition.thresholds.*`**: `number` (Double)  
  مهم: هذه القيم **ليست درجات (degrees)**. هذه thresholds مبنية على إحداثيات الـ landmarks:
  - `x` و `y` عادةً normalized (تقريبًا 0..1)
  - `z` يمثل العمق (depth) وقيمه تكون صغيرة نسبيًا وقد تكون سالبة/موجبة حسب الموديل
  - **يمكن أن تكون القيم سالبة** (مثل `-0.05`, `-0.1`) حسب نوع المقارنة والـ operator

ملاحظات إضافية (منطق التنفيذ):

- `PositionValidator` يتجاهل الـ check لو أي landmark visibility أقل من `0.5` (افتراضيًا).
- يوجد hysteresis صغير داخل المقارنة (حوالي `0.02`) لتقليل flickering.

### 7.3 `PositionCheckType` (enum)

- `"forward_comparison"`
- `"vertical_comparison"`
- `"sideways_comparison"`
- `"distance_ratio"`
- `"horizontal_alignment"`
- `"vertical_alignment"`
- `"depth_alignment"`

ملاحظات تشغيل مهمة من `PositionValidator`:

- **FORWARD_COMPARISON**:
  - في `side_view`: forward axis = **X**
  - في `front_view/back_view`: forward axis = **Z** (عمق)
  - اتجاه الجسم (facing) قد يعكس الإشارة في side view
- **VERTICAL_COMPARISON**: يستخدم محور **Y**
- **SIDEWAYS_COMPARISON**:
  - في `side_view`: axis = **Z**
  - في `front/back`: axis = **X**
- **DISTANCE_RATIO**: يحتاج 4 landmarks (primary+secondary+tertiary+quaternary)
- **ALIGNMENT**: يقيس مدى alignment باستخدام threshold فقط (operator غالبًا لا يؤثر)

### 7.4 `PositionOperator` (enum)

- `"should_not_exceed"`
- `"should_exceed"`
- `"approximately_equal"`
- `"greater_than_ratio"`
- `"less_than_ratio"`

ملاحظات:

- في `distance_ratio` يتم استخدام `greater_than_ratio` / `less_than_ratio`.
- في بعض الأنواع (alignment) الـ operator لا يتم استخدامه فعليًا، لكن وجوده مطلوب داخل JSON لأن `PositionCondition` يحتاجه.

### 7.5 `CheckSeverity` (enum)

- `"error"`: يؤثر على correctness للـ rep (يُسجل داخل `RepCounter`)
- `"warning"`: form feedback فقط
- `"tip"`: تحسينات فقط

### 7.6 `LandmarkGroup`

```json
{
  "primary": "left_knee",
  "secondary": "left_foot_index",
  "tertiary": "left_hip",
  "quaternary": "right_knee"
}
```

- **`primary`**: `string` **(Required)**
- **`secondary`**: `string` **(Required)**
- **`tertiary`**: `string` **(Optional)**  
  مطلوب فقط لـ `horizontal_alignment`, `vertical_alignment`, و`distance_ratio`.
- **`quaternary`**: `string` **(Optional)**  
  مطلوب فقط لـ `distance_ratio` (للمجموعة الثانية من landmarks).

### 7.7 قائمة الـ landmarks المسموحة

القيم تأتي من `JointLandmarkMapping` (MediaPipe Pose 0..32).  
هذه هي الأسماء التي يمكن استخدامها في `landmarks.*` داخل `positionChecks`:

- **0**: `nose`
- **1**: `left_eye_inner`
- **2**: `left_eye`
- **3**: `left_eye_outer`
- **4**: `right_eye_inner`
- **5**: `right_eye`
- **6**: `right_eye_outer`
- **7**: `left_ear`
- **8**: `right_ear`
- **9**: `mouth_left`
- **10**: `mouth_right`
- **11**: `left_shoulder`
- **12**: `right_shoulder`
- **13**: `left_elbow`
- **14**: `right_elbow`
- **15**: `left_wrist`
- **16**: `right_wrist`
- **17**: `left_pinky`
- **18**: `right_pinky`
- **19**: `left_index`
- **20**: `right_index`
- **21**: `left_thumb`
- **22**: `right_thumb`
- **23**: `left_hip`
- **24**: `right_hip`
- **25**: `left_knee`
- **26**: `right_knee`
- **27**: `left_ankle`
- **28**: `right_ankle`
- **29**: `left_heel`
- **30**: `right_heel`
- **31**: `left_foot_index` (Alias: `left_toe`)
- **32**: `right_foot_index` (Alias: `right_toe`)

---

## 8) Validation / Runtime Expectations (مهم للباك)

- **لا ترسل `null`** في الحقول الأساسية (خصوصًا enums والقوائم). Kotlin تعتبرها non-null لكن Gson قد يضع null وتحدث مشاكل لاحقًا.
- **`poseVariants` لازم تحتوي element على الأقل**؛ وإلا `TrainingEngine` سيكسر عند index.
- **`difficultyLevels` لازم تحتوي config للـ difficulty المطلوب** (`beginner/normal/advanced`)؛ وإلا يحصل crash.
- **للـ PRIMARY joints**:
  - لازم وجود `upRange` و`downRange`، وإلا الكود يرمي `IllegalStateException`.
- **للـ SECONDARY joints**:
  - لازم وجود `"Range"` (capital R)، وإلا الكود يرمي `IllegalStateException`.
- **`startPose`**:
  - يستخدم فقط لبدء التدريب في `PoseValidator` (ويحتاج 10 frames صحيحة متتالية).
- **Multi-primary joints**:
  - `PhaseStateMachine` يأخذ thresholds من **أول primary joint فقط**، لكنه يحسب **متوسط الزوايا** لكل primary joints أثناء التحديث.  
  لذلك يفضّل أن تكون ranges للـ primary joints متقاربة جدًا.
- **Position checks**:
  - لا تعمل إن كانت landmarks غير مرئية (visibility < threshold) أو إن كانت `activePhases` غير مطابقة لاسم الـ phase الحالي.
  - `cooldownMs` يمنع spam في الأحداث، لكن الـ overlay يظل ظاهر طالما المشكلة موجودة.

---

## 9) قائمة الحقول الموجودة حاليًا في ملفات الـ assets (مرجع سريع)

هذه هي مجموعة الحقول الفعلية الموجودة في الـ 20 JSON الحالية (union):

- **Top-level**: `name`, `description`, `instructions`, `category`, `countingMethod`, `muscles`, `equipment`, `tags`, `poseVariants`
- **PoseVariant**: `name`, `cameraPosition`, `expectedFacingDirection`, `trackedJoints`, `positionChecks`, `feedbackMessages`, `difficultyLevels`
- **TrackedJoint**: `joint`, `role`, `startPose`, `upRange`, `downRange`, `"Range"`, `errorMessages`, `pairedWith`
- **FeedbackMessages**: `motivational`, `common_mistake`, `tip`
- **DifficultyLevel**: `level`, `repCountingConfig`, `phases`
- **RepCountingConfig**: `reps`, `minRepIntervalMs`, `maxRepIntervalMs`, `duration`, `gracePeriodMs`
- **PositionCheck**: `id`, `type`, `landmarks`, `condition`, `activePhases`, `errorMessage`, `severity`, `cooldownMs`, `minErrorFrames`
- **PositionCondition**: `operator`, `thresholds`
- **DifficultyThresholds**: `beginner`, `normal`, `advanced`

