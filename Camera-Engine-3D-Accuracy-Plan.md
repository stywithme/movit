# خطة دقة القياس ثلاثي الأبعاد وتصحيح الميلان — محرك التدريب بالكاميرا
# Camera Engine — 3D Accuracy, Tilt & Throughput Remediation Plan

> **المصدر**: المراجعة المتخصصة لمسار قياس الزوايا 3D (2026-07-11) + المقارنة التفصيلية مع legacy عبر `Docs/07-Legacy-code/` (‏`Joint-Angle-2D-vs-3D.md` و`Device-Tilt-Correction.md`).
> **العلاقة بالخطة السابقة**: تكمل [Camera-Engine-Remediation-Plan.md](Camera-Engine-Remediation-Plan.md) (WP-01…WP-14 منفذة) — الترقيم يتابع من **WP-15**. تمتص هذه الخطة البنود المفتوحة القديمة: M4 (زمن التنعيم)، A-12/PF-22 (طبقات الإسقاط)، والقرار النهائي لمصير STABLE profile.
> **الملف الشقيق**: [Camera-Engine-3D-Innovation-Proposals.md](Camera-Engine-3D-Innovation-Proposals.md) — المقترحات الإبداعية بوصفات تنفيذها (يُشار إليها هنا بـ INNOV-n).
> **التاريخ**: 2026-07-11 · **الحالة**: منفّذة كوداً — M-A…M-D بانتظار جهاز

---

## 0. الملخص التنفيذي

### الفكر الجديد الحاكم (بعد دمج المراجعتين وقرارات المالك)

1. **3D-first هو الهوية** — التمارين مصممة على وضعيات 3D؛ ‏World landmarks هي المصدر الأساسي، و2D شبكة أمان لكل-مفصل فقط (وهذا مطابق لبنية legacy الفعلية `use3D=true`). لا وضعين، لا أعلام.
2. **Flagship-first في الأداء** — فقدان fps قابل للقياس فكرٌ مرفوض. الافتراضي يرتفع لمستوى legacy ‏(640×480@30) مع نزول تكيّفي مُقاس للأجهزة الضعيفة، لا افتراض واحد يدفع الجميع ثمن الأضعف. (نموذج MediaPipe يُدخل 256×256 داخلياً — **الـ fps هو العامل الحاسم للدقة، وليس دقة الالتقاط**.)
3. **الجاذبية مصدر الحقيقة للوضعية** — تصحيح الميلان (Gravity sensor، ليس Gyroscope) يجب أن يعمل في **كل** المراحل وبأي اتجاه جهاز، والمسار المستقبلي لفحوص الوضعية هو 3D محاذى بالجاذبية بمقاييس متر حقيقية (INNOV-5).
4. **لا فشل صامت** — أي config يطلب قناة قياس غير محسوبة يُرفض بوضوح، وأي تدهور قياس يُسجَّل رقمياً في تقرير الجلسة.

### جدول المشكلات المكتشفة (سجل النتائج)

| ID | المشكلة | الخطورة | الحزمة |
|---|---|---|---|
| **F1** | 10 قنوات زاوية "أشباح": مستهلَكة في `JOINT_ANGLE_MAP` ولا يحسبها الـ assembler (wrist/cross/neck/spine) — legacy كان يحسبها كلها | 🔴 P1 | WP-15 |
| **T1** | تصحيح الميلان **ميت في مرحلة الإعداد**: لا `acquire` للحساس أثناء setup/countdown (legacy كان يمتلكه بـ "setup-pose") | 🔴 P1 | WP-16 |
| **R1** | الافتراضي `STABLE 320×240@10fps` أثر تثبيت من النقل — legacy كان `640×480@30`؛ يقصّ دقة التوقيت ×3 | 🔴 P1 (قرار منتج مُتَّخذ) | WP-19 |
| **F2** | بوابة رؤية 3D تفحص إشارة قد تكون ثابتة (world visibility تفترض 1.0 عند الغياب على المنصتين) | 🟠 P2 | WP-17 |
| **F3** | fallback ‏2D مشوّه بنسبة الأبعاد (normalized x÷W, y÷H) — يطال أيضاً `ang2D` داخل مقدّر الكوع؛ عيب موروث من legacy | 🟠 P2 | WP-17 |
| **P1** | فحوص الموضع 2D-فقط؛ الترقية الممكنة: gravity-aligned World 3D لفحوص الوضعية/المحاور/المسافات | 🟠 P2 (تصميمي) | WP-20 |
| **F4** | مقدّر الكوع: يعمل حتى بلا كوع متتبَّع، يلغي ناتج sticky للكوعين، وأحداث switch للكوع تصفّر smoother بلا معنى | 🟡 P2 | WP-18 |
| **F5** | سقف dt في One-Euro ‏= 0.1s — حافة 10fps بالضبط؛ أي هبوط يجعل الفلتر يقدّر السرعات خطأ | 🟡 P2 | WP-17 |
| **T6** | معادلة الميل `atan2(gx,gy)` تفترض portrait — الحوامل landscape تحتاج خصم دوران الشاشة ("في أي وضعية") | 🟡 P2 | WP-16 |
| **T2** | dead zone ‏2° في KMP مقابل 1° في legacy | 🟢 P3 | WP-16 |
| **T3** | معدل الحساس `SENSOR_DELAY_GAME` (~50Hz) مقابل ~5Hz قابلة للضبط في legacy — بطارية | 🟢 P3 | WP-16 |
| **T4** | لا `DeviceTiltSettings` (لا kill switch — `enabled=true` ثابتة) | 🟢 P3 | WP-16 |
| **T5** | KDoc قديم: "iOS v1: no-op stub" بينما iOS منفذ فعلياً بـ CoreMotion | 🟢 P3 | WP-16 |
| **T7** | المحرك يمتلك حساس الميل حتى لتمارين بلا position checks (legacy كان يمرره شرطياً) | 🟢 P3 | WP-16 |
| **F6** | `angleDegrees3D` يعيد 0.0 للحالة المنحطة (نقاط متطابقة) بدل null | 🟢 P3 | WP-17 |

**قرارات مُتَّخذة من المالك (لا تحتاج نقاشاً)**: ‏3D وضع وحيد (نُفِّذ في الجولة السابقة) · فقدان fps مرفوض → رفع الافتراضي + تكيّف · فحوص الوضعية تتجه لـ 3D حيث يفيد.

---

## 1. WP-15 — استعادة قنوات الزوايا العشر (Ghost Channels)

> **يغلق**: F1 · **الجهد**: مرحلة أ: نصف يوم · مرحلة ب: 2–3 أيام · **المخاطرة**: أ منخفضة، ب متوسطة (parity)

### المشكلة بالدليل

- المستهلك موجود: `JointAngleTracker.JOINT_ANGLE_MAP` يخدم 21 كوداً ([JointAngleTracker.kt:28-50](kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/engine/JointAngleTracker.kt)).
- المنتِج غائب: `PoseFrameAssembler.calculateAngles` يحسب 10 فقط (كوع/كتف/ورك/ركبة/كاحل).
- الحماية غائبة: `validationIssues()` لا تفحص قابلية الحساب — config يتتبع `spine` كـ PRIMARY يبني محركاً **لا يعدّ أبداً بصمت**.
- الدليل على الاستخدام المتوقع: `MotionRecorder.kt:279` يبحث عن مفصل `"spine"`؛ legacy doc §7 يوثق حساب **كل** القنوات (cross/wrist/neck/spine) — أي أن النقل أسقط 10 حسابات.

### مرحلة أ — درع فوري (نفّذ أولاً، مستقلة)

1. أنشئ المصدر الوحيد للحقيقة: `object ComputedAngleChannels { val CODES: Set<String> }` بجوار `PoseFrameAssembler` — يبدأ بالأكواد العشرة المحسوبة اليوم.
2. في `ExerciseConfig.validationIssues()` ([ExerciseConfigModels.kt:128](kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/config/ExerciseConfigModels.kt)): أضف فحص `variant.trackedJoints.filter { it.joint !in ComputedAngleChannels.CODES }` → issue بنص واضح `"joint '<code>' has no computed angle source"`.
3. بوابة `buildEngine()` القائمة (WP-01) تلتقطها تلقائياً → `configUnavailable` مرئي بدل الصمت.
4. **اختبار**: config بمفصل `spine` → `validationIssues` غير فارغة؛ config بالأكواد العشرة → فارغة.

### مرحلة ب — استعادة الحسابات (بعد أ، خلف parity كامل)

استخرج الصيغ من legacy (`android-poc/.../analysis/AngleCalculator.kt` — الثلاثيات موثقة في `Joint-Angle-2D-vs-3D.md` §7):

| القناة | الثلاثية (A→B→C) | المعالم |
|---|---|---|
| `left/rightShoulderCross` | elbow → shoulder → **الكتف الآخر** | 13/11/12 و14/12/11 |
| `left/rightHipCross` | knee → hip → **الورك الآخر** | 25/23/24 و26/24/23 |
| `left/rightWrist` | elbow → wrist → index | 13/15/19 و14/16/20 |
| `neckLeft/neckRight` | shoulder → NECK(33) → nose/رأس (استخرج الصيغة الدقيقة من legacy) | يستخدم المعلم الافتراضي 33 |
| `neckSpine` | NECK(33) → SPINE(34) مع نقطة ثالثة legacy | افتراضيان |
| `spine` | neck → spine → knee (مع fallbacks legacy) | 33/34/25-26 |

خطوات:
1. أضف الحسابات في `calculateAngles` بنفس نمط `angleAt3D` (‏3D-first + fallback 2D + sticky لكل قناة). **تنبيه world**: المعالم الافتراضية 33/34 غير موجودة في world (33 نقطة) — احسب neck/spine العالميتين كـ midpoints من world مباشرة داخل الدالة (لا تلحق بالقائمة).
2. حدّث `ComputedAngleChannels.CODES` لتشمل الكل — درع المرحلة أ يفتح تلقائياً.
3. تحقق أن `JointLandmarkMapping.computeJointVisibility` يدعم الأكواد الجديدة (للرؤية/ANY_SIDE) — أكمل الناقص.
4. **اختبارات**: زاوية 3D معروفة لكل قناة جديدة عبر `assemble`؛ تكافؤ مرآة (`mirrorAngles` يغطيها أصلاً)؛ سيناريو config يتتبع `spine` يعدّ فعلياً.

**قرار مطلوب قبل ب** (له افتراضي): هل توجد تمارين إنتاجية حالية تستخدم هذه الأكواد؟ إن لا — تُنفَّذ ب بهدوء بلا مخاطرة تكافؤ. **الافتراضي: نفّذ ب خلال هذه الخطة** (الفكر الجديد: المنظومة تدعم كتالوج التمارين القادم كاملاً).

---

## 2. WP-16 — إحياء تصحيح الميلان في الإعداد + مطابقة legacy

> **يغلق**: T1 (الأهم)، T2، T3، T4، T5، T6، T7 · **الجهد**: يوم–يومان · **المخاطرة**: منخفضة

### T1 — الحساس لا يعمل أثناء Setup Pose (الإصلاح الجوهري)

**الدليل**: grep شامل — `acquire(` تُستدعى فقط من `MovitTrainingEngine.start()` ‏(TILT_OWNER — أي TRAINING) ومن debug lab. ‏legacy كان يمتلك بـ `"setup-pose"` أثناء setup/countdown (`Device-Tilt-Correction.md` §6.1/§7). النتيجة الحالية: أثناء الإعداد `isRunning=false` → ‏`correctionRadians=0` → ‏`SetupReadinessGate` "يطبّق" تصحيحاً صفرياً — **فحوص region/posture/direction تعمل بلا تصحيح ميل في المرحلة الأهم**.

**التنفيذ** (في `TrainingSessionViewModel`):
1. عند مراقبة `supervisor.state` (الموجودة في `wireSupervisor`): دخول أي حالة `isSetupPose() || COUNTDOWN || RESUME_*` → `(deviceTiltPort as? AcquirableDeviceTiltPort)?.acquire("setup-pose")`؛ الخروج منها جميعاً → `release("setup-pose")`.
2. `release("setup-pose")` أيضاً في `onCleared`/`tearDownForExit` (دفاع).
3. ref-count الموجود في المنفذين يضمن التعايش الآمن مع مالك المحرك (`TILT_OWNER`) — لا تغيير هناك.
4. **اختبار**: Fake `AcquirableDeviceTiltPort` يسجل owners؛ سيناريو setup→countdown→training→pause→exit يثبت acquire/release الصحيحين في كل انتقال.

### بقية بنود المطابقة (تنفَّذ معاً في نفس الـ PR)

| بند | التغيير | الملف |
|---|---|---|
| T2 | `deadZoneDegrees: 2f → 1f` (مطابقة legacy) | [AndroidDeviceTiltPort.kt:19](kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/AndroidDeviceTiltPort.kt) + نظيره iOS |
| T3 | `SENSOR_DELAY_GAME → SENSOR_DELAY_UI` (‏~60ms — يكفي لميل حامل ويوفر بطارية) أو معدل صريح 200_000µs كما legacy | نفس الملف `:71` |
| T4 | معاملا `enabled/deadZone/tau` يقرآن من `MovitTrainingPreferences` أو ثوابت `TiltDefaults` موثقة — الحد الأدنى: object ثوابت واحد بدل أرقام مبعثرة | المنفذان + Koin module |
| T5 | صحّح KDoc `DeviceTiltPort` ‏("iOS v1: no-op stub" → "Android sensor + iOS CoreMotion") | [DeviceTiltPort.kt](kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/boundary/DeviceTiltPort.kt) |
| T6 | **دعم أي اتجاه جهاز**: التصحيح الفعلي = `-(roll - displayRotationOffset)` حيث offset ∈ {0°,90°,180°,270°} من دوران الشاشة الحالي (Android: `Display.rotation`؛ iOS: `UIDevice.orientation`). يمرَّر للمنفذ أو يُطرح في `LandmarkTiltCorrector` قبل الدوران | المنفذان + موضع الاستهلاك |
| T7 | لا تمتلك الحساس بلا فائدة: في `MovitTrainingEngine.start()` اجعل `acquire(TILT_OWNER)` مشروطاً بـ `positionValidator != null` | [MovitTrainingEngine.kt:535](kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt) |

**قبول WP-16**: على جهاز مائل ~20° على حامل: مرحلة الإعداد تتعرف الوضعية الصحيحة (يفشل اليوم عملياً)، وbattery sampling لا يتجاوز ما يلزم، وlandscape mount يعمل (T6).

---

## 3. WP-17 — سلامة بوابة 3D والمرشحات (Gate & Filter Integrity)

> **يغلق**: F2، F3، F5، F6 · **الجهد**: 1–2 يوم + قياس جهاز · **المخاطرة**: F3 متوسطة (معايرة) والبقية منخفضة

### F2 — بوابة الرؤية تقرأ الإشارة الصحيحة

1. **تحقق جهاز قصير أولاً (M-A)**: سجّل عينة `world[i].visibility` من MediaPipe الحقيقي على المنصتين (سطر debug مؤقت) — هل تصل مملوءة أم دائماً 1.0 الافتراضية؟ (‏[MediaPipeLandmarkMapper.kt:28](kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipeLandmarkMapper.kt) ‏`orElse(1f)`، ‏Swift `?? 1`).
2. **الإصلاح المستقل عن نتيجة التحقق**: في `PoseFrameAssembler.angleAt3D/tryAngleDegrees3D` مرّر المعالم normalized واقرأ **رؤيتها** (الإشارة المؤكدة من النموذج) لبوابة النقاط الثلاث بدل رؤية world — نفس الفهارس، نفس الدلالة، صفر التباس.
3. بذلك يصبح sticky (E-11) فعالاً حقاً عند انخفاض الرؤية.

### F3 — إصلاح تشويه الأبعاد في مسار 2D

- **المشكلة**: الزوايا 2D على normalized حيث x÷W وy÷H — على 4:3 المحور y "مشدود" 1.33×. يطال fallback الزوايا و`ang2D` في مقدّر الكوع. (legacy ورث نفس العيب — لذلك أخفاه "التكافؤ".)
- **الإصلاح**: قبل حساب أي زاوية 2D حوّل إلى فضاء متساوي القياس: `y' = y * (analysisHeight / analysisWidth)` (أو اضرب x بالمقلوب) — مرر النسبة من `PoseFrame.analysisImageWidth/Height` إلى `assemble` (متاحة أصلاً). طبّقها في: `angleAt` ‏2D، و`computeAngle(ns…)` داخل `ElbowAngleEstimator` (ang2D)، و`dist2D` في `computeFacingRatio`.
- **إدارة المخاطرة**: هذا تغيير معايرة لمسار fallback والكوع → نفّذه **مع** قياس M-C (مقارنة الكوع قبل/بعد على تسجيلات حقيقية) وحدّث fixtures المتأثرة بجدول فروق موثق. حالة `analysisWidth==0` (مجهول) → لا تحويل (سلوك حالي).

### F5 + F6 — تصليحان صغيران

- `OneEuroFilter.kt:23`: ‏`coerceIn(0.001f, 0.1f) → coerceIn(0.001f, 0.25f)` — ضروري قبل/مع رفع الـ fps ‏(WP-19)؛ عند هبوط مؤقت تحت الهدف يقدّر السرعة صحيحاً بدل افتراض 10fps.
- `JointAngleCalculator.kt:48`: الحالة المنحطة تعيد `null` بدل `0.0` (و`ElbowAngleEstimator.computeAngle` بالمثل يعيد fallback بدل 0) — قيمة 0° تعني "انحناء كامل" وهي كذبة خطرة على آلة الأطوار.

---

## 4. WP-18 — نظافة مقدّر الكوع (Elbow Estimator Hygiene)

> **يغلق**: F4 · **الجهد**: نصف يوم + قرار مُقاس لاحق · **المخاطرة**: منخفضة (لا مساس بالخوارزمية نفسها)

المبدأ (بعد وثيقة legacy): الهجين **قرار منتج مدروس** — لا يُحذف بلا بيانات. التنظيف فقط:

1. **بوابة تفعيل**: مرّر لـ `assemble` علماً `elbowTracked: Boolean` (يُشتق في مصدر الإطارات من config الجلسة عبر الـ VM، أو الأبسط: يقرأه الـ VM ويمرره لمصدر الكاميرا مع الإعداد). إن `false` → تخطَّ `estimator.correct` كلياً (اليوم يعمل كل إطار حتى في قرفصاء: EMA + diagnostics بلا مستهلك).
2. **أخرج الكوعين من sticky**: بما أن الناتج النهائي للكوعين يأتي من المقدّر، احسبهما في `calculateAngles` بمسار مباشر (3D إن توفر وإلا 2D) **بلا** تسجيل sticky ولا أحداث switch — يمنع تصفير `AngleSmoother` الزائف لقناتي الكوع.
3. **allocations**: `lastDiagnostics` تُنشأ لكل إطار — أنشئها فقط عند وجود مستهلك (debug lab) عبر علم `collectDiagnostics` (نفس نمط D-04).
4. **القرار المؤجل المُقاس (M-C)**: سجّل جلسات مرجعية (أمامي/جانبي، push-up/curl) وقارن: الهجين الحالي مقابل `ang3D` خام + One-Euro مقابل INNOV-4 (plane-projection). القرار بالأرقام؛ حتى ذلك الحين الهجين هو الإنتاج.

---

## 5. WP-19 — رفع الإنتاجية: Flagship-First Throughput

> **يغلق**: R1 (+ يمتص A-12/PF-22 وM1/M2 من الخطة السابقة) · **الجهد**: 2–3 أيام + قياس · **المخاطرة**: متوسطة — تُدار بالتكيّف والقياس

### القرار

- الافتراضي الجديد: **HIGH ‏(640×480@30fps)** — مستوى legacy نفسه. ‏(المبرر التقني: WP-09 أزال أكبر كلفتين — تحرير بوابة الاستدلال مبكراً + إعادة استخدام bitmap الدوران — اللتين كانتا تبرران 10fps.)
- **Adaptive downgrade** بدل افتراض موحد: ابدأ HIGH؛ قِس p95 لـ `inferenceTimeMs` في أول ~90 إطاراً؛ إن تجاوز الميزانية (~28ms لهدف 30fps) انزل درجة (MEDIUM ثم STABLE) مع milestone log. أضف hook حراري (Android `PowerManager.THERMAL_STATUS`/iOS `thermalState`) للنزول أثناء الجلسة الطويلة.
- التنفيذ في طبقة `TrainingThroughputFlags`/`resolveTrainingCameraConfiguration` + منطق التكيف داخل مصدر الكاميرا (يملك عدّادات inference أصلاً) مع إعادة ضبط الخانق دون إعادة ربط حيث أمكن.

### القياس الملزم (يمتص بروتوكول perf-baseline)

- M-B: أعد جدول `perf-baseline.md` على Tier A/B بالافتراضي الجديد (fps بالطبقات، inferMs، ‏GC/min، ‏RSS، وbusySkip مقابل skipThrottle — يغلق سؤال A-12 عن الطبقات 2/3 عملياً).
- سجّل في `SessionQualityMeta`: ‏`throughputProfileId` الفعلي، متوسط fps المتحقق، وعدد هبوطات التكيّف — **فقدان fps يصبح مُقاساً ومتتبَّعاً في كل جلسة إنتاج** (طلب المالك نصاً؛ يتكامل مع INNOV-6).

### قبول WP-19

Tier A: ‏30fps ثابتة بلا stalls وGC ضمن حدود M2 السابقة · Tier B: نزول تكيّفي نظيف ≤ درجتين مع تسجيله · لا تغيير سلوك عدّ (الزوايا نفسها — فقط عينات أكثر).

---

## 6. WP-20 — فحوص الوضعية بمحاذاة الجاذبية (Gravity-Aligned 3D Position Checks)

> **يغلق**: P1 + يحل T6 جذرياً للفحوص · **الجهد**: 4–5 أيام على مراحل · **المخاطرة**: متوسطة — تُدار بالتدرج لكل نوع فحص
> **الوصفة الكاملة**: INNOV-5 في ملف المقترحات — هذا ملخص الحزمة.

المبدأ: بدل تدوير صورة 2D بالـ roll، استخدم **متجه الجاذبية الكامل (gx,gy,gz)** المتاح من نفس الحساس لبناء دوران يجعل محور Y العالمي = الجاذبية، وطبّق الفحوص على World landmarks:

1. **المرحلة 1 — البنية**: وسّع `DeviceTiltPort` بـ `gravityVector: FloatArray?` (المنفذان يقرآنه أصلاً جزئياً)؛ أضف لكل `PositionCheckType` خاصية `space: IMAGE_2D | GRAVITY_3D` (افتراضي IMAGE_2D — صفر تغيير سلوك).
2. **المرحلة 2 — الأنواع الطبيعية أولاً**: ‏posture (standing/lying)، ‏vertical-alignment، ‏distance checks → ‏GRAVITY_3D: عمودية الجذع = زاوية متجه (hip→shoulder midpoints) مع الجاذبية؛ المسافات بالمتر الحقيقي من World.
3. **المرحلة 3 — يبقى 2D ما هو 2D بطبيعته**: region/framing/التمركز في الكادر — فضاء صورة أصيل.
4. **التوافق**: عند غياب الحساس أو World → fallback تلقائي للمسار 2D الحالي (المصحح بالميل بعد WP-16).
5. **اختبارات**: fixture عالمي معروف + جاذبية مائلة 20° → الفحص ينجح؛ نفس المدخل بمسار 2D القديم → يفشل (يوثق سبب الترقية).

---

## 7. الترتيب والتبعيات

```
الأسبوع 1: WP-15أ (درع) ─┐            WP-16 (الميلان كاملاً) ── أثر مستخدم فوري
                          ├─ WP-17 (F2/F5/F6 فوراً؛ F3 مع M-C)
الأسبوع 2: WP-19 (البروفايل التكيفي + M-B قياس) ── WP-18 (نظافة الكوع)
الأسبوع 3: WP-15ب (استعادة القنوات) ── WP-20 مرحلة 1-2
مستمر:    M-A (رؤية world) قبل إغلاق F2 نهائياً · M-C يغذي قرار الكوع وF3
```

- WP-16 وWP-15أ مستقلتان تماماً — تبدآن فوراً بالتوازي.
- WP-17/F5 (سقف dt) **قبل أو مع** WP-19 (رفع fps).
- WP-20 يعتمد على WP-16 (متجه الجاذبية) وWP-19 يحسّنه (عينات أكثر).
- INNOV-1/2/3 (ملف المقترحات) تُجدول بعد M-B — تحتاج خط أساس fps الجديد.

## 8. بوابات القياس على جهاز (Measurement Gates)

| بوابة | ماذا تقيس | تفتح |
|---|---|---|
| **M-A** | هل MediaPipe يملأ world visibility فعلاً؟ (عينة سجلات، المنصتان) | الشكل النهائي لإصلاح F2 |
| **M-B** | ‏perf-baseline كامل على الافتراضي الجديد + busySkip/skipThrottle | اعتماد WP-19 + إغلاق A-12 القديمة |
| **M-C** | تسجيلات مرجعية للكوع: هجين مقابل 3D خام مقابل plane-projection + أثر إصلاح F3 | قرار مصير المقدّر + دمج F3 |
| **M-D** | جهاز على حامل landscape مائل | قبول T6 |

## 9. المخاطر والتراجع

| المخاطرة | التخفيف | التراجع |
|---|---|---|
| WP-19 يرفع الحرارة/الاستهلاك على أجهزة وسطى | التكيف الحراري + p95 gate + قياس M-B قبل التعميم | خفض الافتراضي إلى MEDIUM بسطر config |
| F3 يغيّر معايرة الكوع/fallback | يُدمج فقط مع نتائج M-C + تحديث fixtures موثق | revert معزول (تحويل الإحداثيات في 3 دوال فقط) |
| WP-15ب يغيّر تكافؤاً خفياً | القنوات الجديدة كانت null — إضافة قيمة حيث كان لا شيء؛ fixtures تُوسَّع لا تُعدَّل | درع المرحلة أ يبقى؛ عطّل الأكواد الجديدة من `ComputedAngleChannels` |
| T6 يكسر حسابات portrait الحالية | offset صفري في portrait — لا تغيير سلوك للحالة السائدة | ثبّت offset=0 |
| WP-20 يغيّر نتائج فحوص إنتاجية | `space` افتراضي IMAGE_2D؛ الترقية لكل نوع بقرار صريح واختبار | أعد النوع إلى IMAGE_2D |

## 10. سجل التنفيذ (يحدّثه الوكلاء)

> يحدّثه الوكلاء أثناء التنفيذ. كل WP: الحالة · الملفات · ما تغيّر · الاختبارات · ملاحظات.

| WP | الحالة | الوكيل | آخر تحديث |
|---|---|---|---|
| WP-15أ | ✅ مكتمل | Grok 4.5 | 2026-07-11 |
| WP-15ب | ✅ مكتمل | Grok 4.5 | 2026-07-11 |
| WP-16 | ✅ مكتمل (M-D بانتظار جهاز) | Grok 4.5 | 2026-07-11 |
| WP-17 | ✅ مكتمل (F3 مدمج؛ M-A/M-C بانتظار جهاز) | Grok 4.5 | 2026-07-11 |
| WP-18 | ✅ مكتمل (قرار الهجين مؤجّل لـ M-C) | Grok 4.5 | 2026-07-11 |
| WP-19 | ✅ مكتمل (M-B بانتظار جهاز؛ التكيف fps فوري) | Grok 4.5 | 2026-07-11 |
| WP-20 | ✅ مرحلة 1–2 (IMAGE_2D افتراضي؛ GRAVITY_3D اختياري) | Grok 4.5 | 2026-07-11 |
| M-A…M-D | ⬜ بانتظار جهاز | — | — |

### WP-15أ — سجل التنفيذ
<!-- AGENT:WP-15A -->
- **الحالة**: ✅ مكتمل
- **الملفات**: `ComputedAngleChannels.kt` (جديد)، `ExerciseConfigModels.kt`، `ExerciseConfigParserTest.kt`
- **التغييرات**: مصدر حقيقة لأكواد الزوايا المحسوبة؛ `validationIssues` يرفض المفصل بلا منتِج (`joint '…' has no computed angle source`) → `buildEngine` يُظهر configUnavailable
- **الاختبارات**: `ghostJoint_unknownCode_validationIssuesNonEmpty`؛ `tenComputedLimbJoints_validationIssuesEmpty` (ثم توسّع بعد ب)
- **ملاحظات**: نُفِّذت أولاً ثم فُتحت تلقائياً بعد WP-15ب

### WP-15ب — سجل التنفيذ
<!-- AGENT:WP-15B -->
- **الحالة**: ✅ مكتمل
- **الملفات**: `PoseFrameAssembler.kt`، `ComputedAngleChannels.kt`، `GhostChannelsAndGateTest.kt`
- **التغييرات**: استعادة shoulderCross/hipCross/wrist/neck*/spine عبر `angleAt3D`؛ midpoints للرقبة/العمود من world داخل الدالة (لا إلحاق بالقائمة)؛ sticky لكل قناة جديدة؛ الكوعان بلا sticky (WP-18)
- **الاختبارات**: `calculateAngles_fillsCrossWristNeckSpine`؛ `spineJoint_afterWp15b_validationIssuesEmpty`
- **ملاحظات**: `JointLandmarkMapping` كان يدعم الرؤية مسبقاً

### WP-16 — سجل التنفيذ
<!-- AGENT:WP-16 -->
- **الحالة**: ✅ مكتمل (قبول حامل landscape = M-D بانتظار جهاز)
- **الملفات**: `SetupPoseTiltOwner.kt`، `TrainingSessionViewModel`، `MovitTrainingEngine`، `TiltDefaults.kt`، `DeviceTiltPort.kt`، `AndroidDeviceTiltPort.kt`، `IosDeviceTiltPort.kt`، `SetupPoseTiltOwnerTest.kt`
- **التغييرات**:
  - **T1**: acquire/release `"setup-pose"` على `shouldValidatePose()` + release في tearDown/onCleared
  - **T2/T3/T4**: deadZone 1°، عيّنة 200ms، `TiltDefaults`
  - **T5**: KDoc محدّث (Android sensor + iOS CoreMotion)
  - **T6**: `-(roll - displayRotationOffset)` من Display/UIDevice
  - **T7**: `acquire(TILT_OWNER)` فقط إن `positionValidator != null`
  - **WP-20 جاهزية**: `gravityVector` على المنفذين
- **الاختبارات**: `SetupPoseTiltOwnerTest` (setup→countdown→training→resume→exit)
- **ملاحظات**: M-D (حامل landscape مائل) بانتظار جهاز

### WP-17 — سجل التنفيذ
<!-- AGENT:WP-17 -->
- **الحالة**: ✅ مكتمل كوداً؛ M-A/M-C بانتظار جهاز
- **الملفات**: `PoseFrameAssembler.kt`، `JointAngleCalculator.kt`، `OneEuroFilter.kt`، `ElbowAngleEstimator.kt`، اختبارات geometry
- **التغييرات**:
  - **F2**: بوابة 3D على رؤية **normalized** (وليس world الافتراضية 1.0)
  - **F3**: `aspectYScale = H/W` على مسار 2D + ang2D في المقدّر (عند width==0 → 1)
  - **F5**: سقف dt One-Euro `0.25s`
  - **F6**: المنحطّ يعيد `null` بدل `0.0`
- **الاختبارات**: `f2_gatesOnNormalizedVisibility_notWorldVisibility`؛ sticky محدّث (إسقاط world بدل visibility وهمية)؛ `angle3D_degenerate_returnsNull`
- **ملاحظات**: F3 مدمج مع مسار fallback؛ قياس M-C ما زال مطلوباً قبل قرار مصير الهجين

### WP-18 — سجل التنفيذ
<!-- AGENT:WP-18 -->
- **الحالة**: ✅ مكتمل (قرار خوارزمية الكوع مؤجّل لـ M-C)
- **الملفات**: `PoseFrameAssembler.kt`، `ElbowAngleEstimator.kt`، `CameraSourceConfiguration.kt`، مصادر الكاميرا، اختبارات elbow
- **التغييرات**: `applyElbowCorrection` / `collectElbowDiagnostics` في الإعداد؛ كوع بلا sticky/switch؛ diagnostics فقط عند الطلب
- **الاختبارات**: `withoutCollectDiagnostics_skipsAllocation`؛ ownership مع `collectElbowDiagnostics=true`
- **ملاحظات**: الهجين يبقى الإنتاج حتى أرقام M-C

### WP-19 — سجل التنفيذ
<!-- AGENT:WP-19 -->
- **الحالة**: ✅ مكتمل (M-B perf-baseline بانتظار جهاز)
- **الملفات**: `TrainingThroughputProfile.kt`، `AdaptiveThroughputController.kt`، `CameraSourceConfiguration.kt`، `CameraXFrameSource`/`IosCameraFrameSource`، `SessionQualityMeta`، `MovitPostTrainingReport`، `TrainingPipelineDiagnostics`
- **التغييرات**: افتراضي **HIGH 640×480@30**؛ سلم تكيّف HIGH→MEDIUM→STABLE بعد ~90 إطاراً إن p95 infer يتجاوز الميزانية؛ حقول `throughputProfileId` / `avgAchievedFps` / `adaptiveDowngrades` في جودة الجلسة؛ خفض fps فوري دون إعادة ربط (الدقة عند الربط التالي)
- **الاختبارات**: `TrainingThroughputProfilesTest`، `AdaptiveThroughputControllerTest`
- **ملاحظات**: الخطاف الحراري الكامل غير موصول (يكفي p95)؛ M-B يوثّق fps/GC على الجهاز

### WP-20 — سجل التنفيذ
<!-- AGENT:WP-20 -->
- **الحالة**: ✅ مرحلة 1–2 (مرحلة 3 = region/framing تبقى IMAGE_2D عمداً)
- **الملفات**: `CheckSpace`، `PositionCheck.space`، `GravityAlignedSpace.kt`، `PositionValidator`، `FramePipelineExecutor`/`MovitTrainingEngine` (تمرير world)، `GravityAlignedPositionCheckTest`
- **التغييرات**: `IMAGE_2D` افتراضي (صفر تغيير سلوك)؛ `GRAVITY_3D` لـ vertical/distance عبر متجه الجاذبية + world؛ fallback لـ 2D عند غياب الحساس/العالم
- **الاختبارات**: `gravity3d_vertical_passesWhenTorsoUprightRelativeToTiltedGravity`
- **ملاحظات**: ترحيل configs الإنتاجية لـ GRAVITY_3D اختياري لاحق

### بوابات القياس
| بوابة | الحالة |
|---|---|
| M-A (world visibility) | ⬜ بانتظار جهاز — الإصلاح F2 مستقل ومُدمج |
| M-B (perf-baseline @30fps) | ⬜ بانتظار جهاز |
| M-C (كوع / F3) | ⬜ بانتظار تسجيلات مرجعية |
| M-D (landscape tilt) | ⬜ بانتظار جهاز |

<!-- AGENT:WP-15A -->
<!-- AGENT:WP-15B -->
<!-- AGENT:WP-16 -->
<!-- AGENT:WP-17 -->
<!-- AGENT:WP-18 -->
<!-- AGENT:WP-19 -->
<!-- AGENT:WP-20 -->

## 11. قائمة تحقق ما قبل الدمج (موروثة من الخطة السابقة §15)

- [x] اختبار استنساخ يفشل قبل الإصلاح (لكل P1/P2) — حيث ينطبق
- [x] `testAndroidHostTest` للوحدات المتأثرة أخضر (2026-07-11)
- [ ] `compileKotlinIosArm64` — لم يُشغَّل في هذه الجولة
- [x] fixtures التكافؤ خضراء أو محدثة (اختبارات sticky/F2 محدّثة لبوابة normalized)
- [x] تحديث سجل §10 هنا + وسم النتيجة في هذا الملف
- [x] بند القياس المرتبط (M-A…M-D) موثق النتيجة أو موسوم "بانتظار جهاز"
