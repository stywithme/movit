# Android KMP Training Engine vs MO Legacy Difference Audit

**تاريخ المراجعة:** 2026-06-14 (آخر تحديث: مراجعة كود مستقلة بعد التجربة الحقيقية + `TrainingPipeline`)
**مرجع Legacy:** فرع `MO` / commit `aceaef8e` (`MO Prototype`) عبر worktree قراءة فقط.  
**مرجع الجديد:** الشجرة الحالية بعد Phase 07 وإصلاحات G1-G4 وhotfixes استقرار الجهاز (§29–§32).  
**النطاق:** محرك التدريب الحي، بداية التمرين، الكاميرا، MediaPipe، الفيدباك، الأداء، تدفق الـ workout، التقارير، الرفع، الاختبارات، وأفكار موجودة في طرف دون الآخر.

> الحكم المختصر (محدَّث 2026-06-14): المحرك الجديد أقوى بنيوياً وأكثر قابلية للاختبار والتوسع عبر KMP، وبعد hotfixes الجهاز (§29–§32) أصبح مسار Android مستقراً في smoke واحد. لكن قراءة الكود المستقلة في §34 تثبت وجود فروقات عميقة لا يجوز اعتبارها تحسينات مقبولة: أهمها regression في إنهاء تمارين bilateral بنمط `AFTER_ALL_REPS`، وفقدان كبير في مادة التقرير والتحليل، وغياب صوت setup parity، وفجوة iOS فعلية لأن detector/snapshot ما زالا غير منتجين. أي حكم أقدم في الوثيقة يتعارض مع §34 يُعتبر superseded.

---

## 1) منهجية المقارنة

تمت المقارنة بين:

- `D:/laragon/www/POSE-2-MO-readonly` كنسخة `MO` Legacy.
- `D:/laragon/www/POSE-2` كنسخة KMP الحالية.
- ملفات Legacy الأساسية:
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/train/TrainingActivity.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/training/TrainingViewModel.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/training/PoseSetupGuide.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/train/SetupCountdownBinder.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/train/CameraTrainingInputController.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/camera/CameraManager.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/pose/PoseLandmarkerHelper.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/training/TrainingEngine.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/training/workout/WorkoutTrainingEngine.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/training/report/ReportGenerator.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/training/report/FrameCaptureManager.kt`
- ملفات KMP الأساسية:
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt`
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/SetupReadinessGate.kt`
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/SetupJointGuidance.kt`
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/engine/StartPoseGate.kt`
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/TrainingSessionFlowCoordinator.kt`
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/FrameIngressGate.kt`
  - `android-poc/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/CameraXFrameSource.kt`
  - `android-poc/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt`
  - `android-poc/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt`
  - `android-poc/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingKeepScreenOn.kt`
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/diagnostics/TrainingPipelineDiagnostics.kt`
  - `android-poc/feature/training/src/androidMain/kotlin/com/movit/feature/training/TrainingSessionCameraHost.android.kt`
  - `android-poc/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionScreen.kt`
  - `android-poc/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionPanels.kt`
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/report/MovitPostTrainingReport.kt`
  - `android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/MotionRecorder.kt`
  - `android-poc/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionWriteHooks.kt`

---

## 2) معايير التقييم

### 2.1 مقياس الدرجات

| الدرجة | المعنى |
|---|---|
| 5 | أفضلية واضحة ومكتملة إنتاجياً |
| 4 | جيدة وقريبة من الإنتاج مع فجوات صغيرة |
| 3 | تعمل جزئياً أو تعتمد على ظروف محددة |
| 2 | موجودة لكن ناقصة أو مكسورة في مسار مهم |
| 1 | غائبة أو خطر إنتاجي واضح |

### 2.2 المعايير المستخدمة

| المعيار | ما يتم قياسه |
|---|---|
| **UX** | هل المستخدم يفهم المطلوب؟ هل توجد رسائل/صور/صوت/تدرج واضح؟ |
| **Performance** | FPS، تقليل allocations، backpressure، بدء الكاميرا، latency، استقرار MediaPipe. |
| **Correctness** | صحة العد، بداية الوضعية، phases، visibility، pause/resume، bilateral/hold. |
| **Data & Backend** | journal، offline writes، planned workout، تقارير، sync، report IDs. |
| **Cross-platform** | قابلية العمل على Android/iOS/KMP دون Android-only coupling. |
| **Maintainability** | فصل الطبقات، الاختبارات، قابلية القراءة، عدم تكرار المنطق. |
| **Risk** | احتمال كسر تجربة أول جهاز، أو فقد بيانات، أو ضعف فهم المستخدم. |

---

## 3) النتيجة الإجمالية حسب المجال

| المجال | Legacy `MO` | KMP الحالي | الحكم |
|---|---:|---:|---|
| تجربة بداية التمرين | 5 | 2 | Legacy أفضل بكثير للمستخدم حالياً. |
| فهم الوضعية المطلوبة | 5 | 2 | KMP يحسب بعض الإرشادات لكنه لا يعرضها كاملة. |
| الكاميرا/FPS | 4 | 3 | Legacy أسرع (~15+ fps)؛ KMP **مستقر** بعد §29–§32 لكن محافظ (`target=10`، `320×240`) ≈7 fps فعلي. |
| مسار العد والفورم | 4 | 4 | شبه parity؛ **مُتحقَّق على جهاز** 12/12 بدون dropped frames (لوج 2026-06-14 15:28). |
| visibility / pause / resume | 4 | 4 | متقارب؛ KMP أنظف بنيوياً بعد `PresenceSupervisorBridge`. |
| الأداء داخل المحرك | 3 | 4 | KMP أضاف `FrameIngressGate` وقياسات journal؛ Legacy لديه coalescing في VM. |
| التقارير المرئية بعد الجلسة | 5 | 2 | Legacy أغنى: صور، best/worst, danger, replay. |
| رفع البيانات/offline writes | 3 | 5 | KMP أقوى بعد G1/G4: Outbox, planned writes, journal. |
| تعدد المنصات | 1 | 4 | KMP متقدم، iOS detector ما زال stub. |
| الاختبارات | 2 | 5 | KMP لديه suite واسعة وparity tests. |
| قابلية الصيانة | 2 | 4 | Legacy غني لكنه Android Activity-heavy؛ KMP أنظف. |

---

## 4) خريطة المعمارية

### 4.1 Legacy `MO`

المسار القديم كان Android-first:

1. `TrainingActivity` يمسك lifecycle، UI، permission، report، sync، feedback، camera.
2. `CameraTrainingInputController` يربط `CameraManager` و`PoseLandmarkerHelper`.
3. `PoseLandmarkerHelper` يحول `ImageProxy` إلى bitmap، يدور/يعكس الصورة، ويرسل `PoseResult`.
4. `TrainingViewModel` يدير `WorkoutRunSupervisor`, `PoseSetupGuide`, `CountdownController`, `TrainingEngine`, `FeedbackManager`.
5. `PoseSetupGuide` يقود بداية التمرين بتدرج region/posture/direction/angles.
6. `TrainingEngine` يعالج الإطارات والعد والفيدباك.
7. `SetupCountdownBinder` و`TrainingFeedbackBinder` يحولان النتائج إلى UI وصوت.
8. `FrameCaptureManager` و`ReportGenerator` ينتجان تقريراً غنياً.

**القوة:** UX غنية ومباشرة.  
**الضعف:** تشابك شديد بين Activity، Android، الكاميرا، الفيدباك، التقرير، والـ engine.

### 4.2 KMP الحالي

المسار الجديد مفصول:

1. `TrainingSessionRoute` يخلق `TrainingSessionViewModel`.
2. `TrainingSessionCameraHost.android.kt` يحصل على `CameraFrameSource` من Koin.
3. `CameraXFrameSource` يربط CameraX و`MediaPipePoseDetector`.
4. `PoseFrameAssembler` ينتج `PoseFrame` مشترك.
5. `TrainingSessionViewModel` ينسق `SessionSupervisor`, `SetupReadinessGate`, `CountdownController`, `MovitTrainingEngine`, `FeedbackRouter`, write hooks.
6. `MovitTrainingEngine` يعالج الإطارات في commonMain.
7. `TrainingMotionSession` و`MotionRecorder` يكتبان journal وupload.
8. `MovitPostTrainingReport` و`MovitSessionReport` يبنيان payloads وتقارير Compose أخف.

**القوة:** طبقات نظيفة، KMP، اختبارات، offline-first.  
**الضعف:** تجربة setup والتقرير والكاميرا لم تستعد كامل Legacy.

---

## 5) فروقات بداية التمرين Setup

### 5.1 ما كان موجوداً في Legacy

`PoseSetupGuide` في `MO` كان منظومة كاملة:

- مراحل متسلسلة:
  - `REGION`: هل الجزء المطلوب ظاهر؟
  - `POSTURE`: واقف/جالس/مستلقي؟
  - `DIRECTION`: أمامي/جانبي/خلفي؟
  - `ANGLES`: ضبط مفاصل البداية.
- يستخدم `PoseSceneDetector` بنافذة 12/9.
- يعرض حالات `AxisStatus` لكل محور: pending/failed/passed.
- يحسب `JointGuidance` لكل مفصل: `GREEN/YELLOW/RED`, الزاوية الحالية، min/max، الاتجاه `RAISE/LOWER`.
- يؤكد البداية عبر `startPose` وليس `isInStartPosition`.
- يدعم any-side pairs: إذا تمرين يقبل أي جانب، يكفي جانب واحد من الزوج.
- يملك voice cooldown:
  - تغيير phase ينطق فوراً.
  - نفس phase/joint يستخدم cooldown مضاعف.
- يقدم `getPoseRequirementsText()` وفيه زوايا البداية لكل مفصل.

`SetupCountdownBinder` كان يحول ذلك إلى تجربة واضحة:

- يخفي panel النصي القديم ويعرض setup indicator.
- يعرض region/posture/direction كصفوف بأيقونات وألوان.
- يعرض صورة مرجعية `positionImageUrl` إن وجدت.
- يحدث skeleton overlay في setup mode.
- عند الوصول لـ `ANGLES` يحدث skeleton بتوجيهات المفاصل.
- ينطق:
  - إرشاد المرحلة (`speakSetupPhaseGuidance`)
  - إرشاد المفصل الأسوأ (`speakSetupGuidance`)
  - رسالة انتقال scene-to-visibility.

### 5.2 ما ينفذه KMP حالياً

`SetupReadinessGate` نقل جزءاً من الفكرة:

- يحسب phase: `REGION/POSTURE/DIRECTION/ANGLES`.
- يحسب `phaseMessage`.
- يحسب `worstJointGuidance`.
- يحسب `cameraTip`.
- يحسب rolling window.

لكن `TrainingSessionViewModel` يستهلك فقط:

- `progressPercent`
- `phase.name`
- `phaseMessage?.get(language)`

ولا يستهلك:

- `worstJointGuidance.message`
- `cameraTip`
- statuses لكل axis
- قائمة كل joint guidance
- صورة المرجع
- نص زوايا البداية
- صوت setup

### 5.3 فجوة `startPose` مقابل `isInStartPosition`

Legacy كان يفرق بين مفهومين في `StartPoseGate`:

- `isInStartPose`: قبل التمرين، يستخدم `TrackedJoint.startPose`.
- `isInStartPosition`: أثناء التمرين، يستخدم UP zone / counted state.

KMP `StartPoseGate` يحتوي `isInStartPosition` فقط.  
`SetupReadinessGate` يستخدمه لتأكيد البداية، بينما `SetupJointGuidanceResolver` يستخدم `startPose` لحساب الإرشاد.

**الأثر:** المستخدم قد يرى إرشاداً مبنياً على `startPose`، بينما gate تؤكد/ترفض بناء على معيار آخر. هذا قد يسبب انتظاراً غير مفهوم أو “0% ready” رغم أن المستخدم يتبع النص.

### 5.4 تقييم مجال setup

| بند | Legacy | KMP | تقييم |
|---|---:|---:|---|
| مرحلة region/posture/direction | 5 | 3 | الحساب موجود، العرض ناقص. |
| إرشاد المفصل | 5 | 2 | محسوب جزئياً ولا يظهر. |
| صورة مرجعية | 4 | 1 | KMP لا يعرض `positionImageUrl`. |
| skeleton setup guidance | 5 | 2 | KMP skeleton عام، لا يعرض rows/axis بنفس Legacy. |
| صوت setup | 5 | 1 | KMP لا يرسل `FeedbackKind.SETUP` فعلياً. |
| وضوح “ماذا أفعل الآن؟” | 5 | 2 | سبب شاشة “Get into position / Step: Frame”. |

**الأولوية:** P0 قبل أي smoke جهاز.

---

## 6) تفسير شاشة `Get into position / Step: Frame / 0% ready`

من الكود الحالي:

1. `setupPhase` يبدأ فارغاً في `TrainingSessionUiState`.
2. `localizedSetupPhase("")` يقع في default ويرجع مفتاح `training_setup_phase_region`.
3. النص الإنجليزي لهذا المفتاح هو `Frame`.
4. `setupGuidance` لا يمتلئ إلا من `phaseMessage`.
5. لو لا توجد pose، أو الكاميرا لم تبدأ، أو landmarks ناقصة، لا توجد رسالة تفصيلية.
6. لو وصلنا `ANGLES`، `phaseMessage = null` عمداً، والمفصل الأسوأ محسوب لكنه غير معروض.

يوجد أيضاً خطر تقني يفسر `FPS 0`:

- `TrainingSessionCameraHost.android.kt` يستدعي `cameraSource.start(...)` في `LaunchedEffect`.
- `CameraXFrameSource.start()` يرجع مبكراً إذا `previewView` أو `lifecycleOwner` لم يُربطا بعد.
- الربط يحدث لاحقاً من `TrainingCameraSurface(onPreviewReady)`.
- إذا حدث start قبل preview binding، لا توجد إعادة start تلقائية.
- `onNoPoseDetected()` في `CameraXFrameSource` يرسل `null`، لكن `TrainingSessionCameraHost` يتجاهله بسبب `frame?.let`.

**الحكم:** هذه شاشة بداية غير كافية UX، وقد تكون أيضاً symptom لعدم بدء الكاميرا فعلياً.

---

## 7) فروقات الكاميرا وMediaPipe والأداء

### 7.1 Legacy

`CameraManager`:

- يستخدم 4:3 aspect ratio.
- يطلب أعلى FPS متاح عبر `Camera2CameraControl` و`CONTROL_AE_TARGET_FPS_RANGE`.
- يضبط أوسع zoom ratio للحفاظ على أكبر مجال رؤية.
- يسجل diagnostics: supported FPS ranges, applied range, zoom, resolution.
- يستخدم `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`.

`PoseLandmarkerHelper`:

- GPU ثم CPU fallback.
- يدعم Full وHeavy model.
- يحول `ImageProxy.toBitmap()`.
- يدوّر ويعكس الصورة باستخدام matrix.
- يستخدم bitmap pool من صورتين لتقليل allocations.
- يحتفظ بـ `frameCameraState` لكل timestamp لتفادي race.
- يدعم LIVE_STREAM وVIDEO mode.
- يرسل `onNoPoseDetected()` بوضوح.

`CameraTrainingInputController`:

- coalescing عبر `isProcessingPoseFrame`.
- smoothing على landmarks.
- يستخدم world landmarks للزوايا إن وجدت.
- يصحح elbow.
- يحدث FPS في UI.
- يمسح overlay عند no-pose.
- يرسل `viewModel.onNoPoseDetected`.

### 7.2 KMP الحالي (محدَّث بعد §22.3 و§29–§32)

`CameraXFrameSource`:

- يستخدم 4:3 و`STRATEGY_KEEP_ONLY_LATEST`.
- **يطبّق** `applyHighFps()` عبر `chooseFpsRange(targetFps)` و`CONTROL_AE_TARGET_FPS_RANGE`.
- **يطبّق** `applyWidestZoom()` مع retries محدودة (§29 — لا حلقة لا نهائية).
- **يسجّل** diagnostics: supported/applied FPS، zoom، resolution (preview + analysis).
- **`CameraStartGate`** يمنع bind قبل preview؛ إعادة start عند جاهزية الـ preview.
- **تحليل محافظ:** `TRAINING_ANALYSIS_SIZE = 320×240` + `shouldAnalyzeFrame()` (§30).
- **`targetFps = 10`** من `TrainingSessionCameraHost` (قرار استقرار — §30.4).
- **`clearAnalyzer()`** قبل `unbindAll()` عند `stop()` (§31).
- guard ضد duplicate bind لنفس الـ facing.
- يرسل `null` عند no-pose → VM يعالجها (لم يعد يُتجاهل في المسار الكامل).

`MediaPipePoseDetector`:

- GPU ثم CPU fallback.
- يدعم heavy model preference وتنزيل/ترقية في الخلفية.
- يحتفظ بـ `frameCameraState`.
- يستخدم `LandmarkSmoother`.
- **`inferenceInFlight`** + `tryAcquireInferenceSlot()` — backpressure صريح (§30).
- **`rotateBitmapForAnalysis`** — دوران upright قبل MediaPipe (يغلق جزءاً من S2 في خطة 07 §16).
- ينتج common `Landmark` و`worldLandmarks`.
- **`DisplayLandmarkTransform`** يعالج mirror للعرض (§25).

`TrainingSessionCameraHost` + `TrainingSessionViewModel`:

- **`Channel.CONFLATED` + worker على `Dispatchers.Default`** لمعالجة الإطارات (§30 — يغلق S4 جزئياً).
- **`requiresCamera()`** — الكاميرا تُزال من composition عند الإكمال/الراحة (§31).
- **`TrainingKeepScreenOnEffect`** — `keepScreenOn` / `isIdleTimerDisabled` (§31).
- FPS overlay في debug يحسب من callback الإطارات فقط.

`TrainingPipelineDiagnostics` (§32):

- قناة لوج موحّدة `tag:TrainingPipeline` — سطر كل 2 ثانية + milestones.

### 7.3 تقييم الأداء (محدَّث 2026-06-14)

| بند | Legacy | KMP | الحكم |
|---|---:|---:|---|
| Camera startup reliability | 4 | 4 | تحسّن بعد `CameraStartGate` + §29؛ **مُتحقَّق** `camera bound` milestone. |
| FPS throughput | 5 | 3 | Legacy ~15+ fps؛ KMP **~7 fps فعلي** (قرار محافظ — §30.4). |
| FOV / widest zoom | 5 | 4 | KMP يطبّق widest zoom بعد §29؛ parity قريب. |
| Backpressure | 4 | 5 | `FrameIngressGate` + `inferenceInFlight` + throttle + VM worker. |
| Bitmap allocation control | 4 | 2 | لا bitmap pool بعد؛ `toBitmap()` كل إطار — ضغط GC خفيف لكن موجود. |
| Session stability (no freeze) | 3 | 4 | **مُتحقَّق** جلسة كاملة 12/12 بدون ImageReader stall (لوج 15:28). |
| No-pose pipeline | 5 | 4 | VM يمرّر `NoPoseFrame` للمشرف؛ تحسّن عن الحالة القديمة. |
| Heavy model support | 3 | 4 | KMP لديه cache/upgrade للـ heavy model. |
| Observability | 3 | 5 | `TrainingPipeline` أفضل من لوجات CameraX المتفرقة. |

**قرار أداء معتمد (§30.4):** الاستقرار أولاً — خفض الدقة والـ fps قبل استعادة throughput تدريجياً مع مراقبة `TrainingPipeline`.

**المتبقي لparity الأداء:** bitmap pool أو مسار بدون `toBitmap` (N-2 / R3 في خطة 07 §16) · رفع `targetFps` إلى 12–15 · تجربة `480×360` أو `640×480` على أجهزة قوية.

---

## 8) فروقات المحرك الحركي والعد

### 8.1 نقاط parity قوية

المنطق التالي منقول أو مكافئ بدرجة عالية:

- `AngleSmoother`
- `PhaseStateMachine`
- `RepCounter`
- `ScoreCalculator`
- `JointEvaluator`
- `JointAngleTracker`
- `PositionValidator`
- `VisibilityMonitor`
- `BilateralController`
- `HoldTimer`
- `RepCompletionCoordinator`
- `TimingPolicy`
- `FeedbackPolicy`
- `PipelineTrace`

KMP أضاف اختبارات parity كثيرة في `core/training-engine/src/commonTest`.

### 8.2 اختلافات مهمة

| الفرق | Legacy | KMP | الأثر |
|---|---|---|---|
| `RepIncomplete` | `PhaseStateMachine.onRepIncomplete` يتحول إلى `FeedbackEvent.RepIncomplete` | لا يظهر موصولاً من `MovitTrainingEngine` للـ VM | المستخدم قد لا يسمع/يرى “لم تصل للعمق/سريع/بطيء”. |
| Events | event stream موحد `FeedbackEvent` | callbacks متفرقة + `FeedbackRouter` | KMP أنظف، لكن يفقد سياق event غني إن لم يُوصل. |
| execution clock | Legacy يدوي داخل engine | KMP `ExecutionClock` بعد G3 | KMP أفضل بعد الإصلاح. |
| frame ingress | Legacy coalescing في UI/VM | KMP داخل engine | KMP أفضل isolation. |
| position scene warnings | Legacy يرسل `SceneWarnings` events | KMP callback يركز على أول position error | قد تفقد رسائل camera direction/posture أثناء التدريب. |
| current angles exposure | Legacy flows علنية `_currentAngles`, `_jointStateInfos` | KMP snapshot/callback | KMP أقل تفاعلية للـ UI debug، لكنه أنظف. |
| زوايا المفاصل (3D world) | `calculateAllAnglesSmoothed(world, use3D=true)` لكل المفاصل | ~~كان 3D للمرفقين فقط~~ → **مُصلَح في §22.6** | كان يسبب انحراف rep/phase في أوضاع جانبية. |

### 8.3 تقييم المحرك

| بند | Legacy | KMP | الحكم |
|---|---:|---:|---|
| عد reps | 4 | 4 | parity جيدة. |
| تقييم الفورم | 4 | 4 | state-based parity. |
| hold exercises | 4 | 4 | parity جيدة. |
| bilateral/any-side | 4 | 4 | KMP يحتاج تأكيد setup any-side. |
| incomplete rep feedback | 4 | 2 | ناقص في KMP UX. |
| testability | 2 | 5 | KMP أفضل بوضوح. |

---

## 9) فروقات الفيدباك والصوت

### 9.1 Legacy

`FeedbackManager` يستقبل `FeedbackEvent` ويحولها إلى:

- visual message
- TTS/audio cache
- haptic
- tone
- cooldown/scheduler

أنواع feedback تشمل:

- `JointQuality.StateMessage`
- `JointQuality.Error`
- `RepCompleted`
- `RepIncomplete`
- `TargetReached`
- `PositionCheckFeedback`
- `SceneWarnings`
- `VisibilityWarning/Paused/Resumed`
- hold events
- setup-specific: `speakSetupGuidance`, `speakSetupPhaseGuidance`, `speakPoseConfirmed`

### 9.2 KMP

`FeedbackRouter` و`FeedbackScheduler` موجودان في commonMain، وهذا أفضل للبنية. بعد G2 صار:

- `onJointStateMessage` موصول.
- `onJointErrorFeedback` موصول.
- position errors موصولة جزئياً.
- countdown/pose-confirmed موصول.

لكن ما زال ناقصاً:

- setup guidance كـ `FeedbackKind.SETUP`.
- scene phase guidance في setup.
- `RepIncomplete`.
- عرض أكثر من رسالة واحدة في UI.
- الاستفادة الكاملة من audio cache/preload.

### 9.3 تقييم الفيدباك

| بند | Legacy | KMP | الحكم |
|---|---:|---:|---|
| setup voice | 5 | 1 | ناقص. |
| joint state voice during training | 5 | 4 | تحسن بعد G2. |
| position voice | 4 | 3 | KMP يختار أول خطأ فقط. |
| countdown audio | 5 | 3 | KMP الأرقام `playCountdownNumber` فارغة حالياً، رغم وجود `onTick` feedback. |
| scheduler model | 3 | 4 | KMP أنظف وأقرب للتوسع. |
| UX clarity | 5 | 3 | KMP يحتاج setup + incomplete. |

### 9.4 فجوة الصوت المخزن

Legacy كان يملك `AudioFeedbackPlayer` و`AudioCacheManager` ومسار cached coaching audio؛ عند عدم وجود ملف يرجع إلى TTS.

KMP الحالي يملك `TrainingSessionAudioHooks.prefetchOnSessionOpen` و`AudioManifestCache` و`AudioFeedbackPlayer` كـ boundary، لكن `TrainingSessionRoute` يمرر `speech` و`haptics` فقط إلى `FeedbackRouter`، ولا يوجد مشغل إنتاجي موصول يقرأ الملفات المحملة. النتيجة أن prefetch موجود، لكن التجربة الحية تعتمد غالباً على TTS.

**الأثر UX/Performance:** latency أعلى، جودة صوت أقل، واحتمال تأخير أثناء العد أو التصحيح.

---

## 10) فروقات واجهة التدريب

### 10.1 Legacy UI

Legacy كان XML/Activity لكنه غني:

- preview fullscreen.
- skeleton overlay مخصص.
- setup indicator bar.
- axis icons للمنطقة/الوضع/الاتجاه.
- reference image من `positionImageUrl`.
- bottom stats يتحول بين setup/form.
- progress ready bar.
- joint guidance rows.
- vignette/Glass messages.
- workout panels داخل نفس Activity.
- camera settings / flip / model settings.

### 10.2 KMP UI

KMP Compose أنظف:

- `TrainingSessionScreen`
- `SetupPosePanel`
- `CountdownOverlay`
- `RestPanel`
- `WorkoutCompletePanel`
- `MovitSkeletonOverlay`
- camera flip button
- report button بعد G4

لكن setup panel الحالي يعرض:

- title ثابت.
- step label.
- progress bar.
- guidance نص واحد فقط.

### 10.3 تقييم UI

| بند | Legacy | KMP | الحكم |
|---|---:|---:|---|
| شكل حديث ومتسق | 3 | 4 | KMP يتبع DesignSystem. |
| غنى setup | 5 | 2 | Legacy أفضل. |
| قابلية iOS | 1 | 4 | KMP أفضل. |
| overlays التشريحية | 5 | 3 | KMP يحتاج setup-specific overlays. |
| التعامل مع no-pose | 5 | 2 | KMP host يضيع null. |
| report CTA | 4 | 4 | KMP تحسن بعد G4. |

---

## 11) فروقات workout flow

### 11.1 Legacy

`WorkoutTrainingEngine`:

- يدير workout كامل داخل Activity واحدة.
- يحول rest item إلى `pendingRestMs` على pre-exercise screen.
- يميز rest بين sets/exercises.
- لديه `MIN_EXERCISE_PREVIEW_MS = 5000`.
- يستبعد warmup/activation/cooldown من progress totals.
- يجمع `SetMetrics`.
- يحتفظ بـ `reportIds` و`executionIds`.
- يدعم phase continue policy عند عودة Activity من الخلفية.

### 11.2 KMP

`TrainingSessionFlowCoordinator`:

- `PreExercise`
- `Rest`
- `Training`
- `WorkoutComplete`
- rest منفصل.
- `REST_NEAR_END_MS = 3000`.
- يدعم skip rest.
- يدعم `flowItems`.

بعد G1:

- shell يمرر `flowItems/plannedWorkout`.
- VM يستدعي start/complete/report planned workout.

### 11.3 الفروقات السلوكية

| الفرق | Legacy | KMP | تقييم |
|---|---|---|---|
| rest UI | rest مدمج في pre-exercise | RestPanel منفصل | KMP أوضح للراحة، Legacy أوضح للانتقال. |
| minimum preview | 5 ثواني قبل تمرين جديد | غير واضح بنفس القاعدة | KMP يحتاج policy لو UX مطلوب. |
| phase continue after background | موجود | غير ظاهر بنفس التفاصيل | KMP يحتاج مراجعة lifecycle. |
| warmup/cooldown progress exclusion | موجود | غير واضح في flow items | يحتاج mapping من backend roles. |
| planned writes | غير مثالي/legacy sync | KMP قوي بعد G1 | KMP أفضل backend. |

### 11.4 تحفظ planned workout بعد G1

بعد إغلاق G1 أصبح KMP يستدعي planned workout lifecycle، لكن يجب تثبيت عقد الـ backend:

- `TrainingSessionViewModel` يجمع `MovitSessionReport` عبر التمارين.
- عند `WorkoutComplete` يتم استدعاء `completePlannedDay` و`reportPlannedDay`.

إذا كان endpoint `reportPlannedWorkout` بديلًا عن `completePlannedWorkout` وليس مكملاً له، فقد ينتج duplicate write أو حالة متضاربة. يحتاج هذا بند تحقق API قبل smoke backend حي.

---

## 12) فروقات التقارير

### 12.1 Legacy

`ReportGenerator` كان قوياً:

- danger alerts.
- perfect moments.
- best reps.
- worst rep.
- error analysis.
- rep timeline.
- consistency.
- execution quality.
- improvement tips.
- hold summary.
- overall quality.
- config snapshot.
- frame captures.
- replay clips.
- hero frame.

`FrameCaptureManager`:

- danger frames.
- peak frames.
- best reps.
- warning/error frames.
- hold samples.
- replay frames per rep.
- storage limits.
- thumbnails/full images.

### 12.2 KMP

`MovitPostTrainingReport`:

- performance summary.
- state breakdown.
- execution quality.
- session quality.
- peakFrameCaptures model exists.
- builder encodes to legacy JSON shape.

لكن عملياً:

- `MotionRecorder` يسجل metrics فقط، لا raw frame persistence.
- `MovitPeakFrameCapture` نموذج موجود لكن لا يوجد capture manager فعلي مكافئ لـ Legacy.
- تقرير Compose الحالي عبر mapper أبسط.
- لا يوجد replay/best/worst/danger visual richness مثل Legacy.

### 12.3 تقييم التقارير

| بند | Legacy | KMP | الحكم |
|---|---:|---:|---|
| report data model | 5 | 3 | KMP أخف. |
| visual evidence | 5 | 1 | KMP لا يحفظ frames فعلياً. |
| best/worst replay | 5 | 1 | ناقص. |
| backend payload | 3 | 5 | KMP أفضل توحيداً وoffline. |
| UI بعد الجلسة | 5 | 3 | زر التقرير موجود، المحتوى أخف. |
| storage control | 4 | 3 | Legacy لديه limits؛ KMP لا يلتقط frames أصلاً. |

**الخلاصة:** KMP أغلق رفع البيانات، لكنه لم يستعد قيمة التقرير التعليمية للمستخدم.

---

## 13) فروقات البيانات والرفع والأوفلاين

### 13.1 Legacy

- `TrainingActivity` يزامن pending executions قبل بدء تدريب جديد.
- `WorkoutTrainingEngine` يحمل report IDs/execution IDs.
- توجد `WorkoutSyncService` و`ReportStorage`.
- كثير من الرفع مربوط بـ Activity/Android.

### 13.2 KMP

- `TrainingSessionWriteHooks`
- `TrainingSessionWriteCoordinator`
- `WorkoutExecutionBatchCoordinator`
- `OfflineWriteQueue`
- `OutboxDispatcher`
- `TrainingMotionSession`
- journal checkpoint/finalize
- planned start/complete/report بعد G1
- `TrainingSessionReportCache` بعد G4

### 13.3 تقييم البيانات

| بند | Legacy | KMP | الحكم |
|---|---:|---:|---|
| offline safety | 3 | 5 | KMP أفضل. |
| planned workout lifecycle | 3 | 5 | KMP بعد G1 أفضل. |
| journal/resume data | 2 | 4 | KMP أفضل، يحتاج UX/lifecycle smoke. |
| report richness | 5 | 2 | Legacy أفضل. |
| فصل backend عن UI | 2 | 5 | KMP أفضل. |

---

## 14) أفكار جديدة في KMP غير موجودة في Legacy

| الفكرة | القيمة | الحالة | تقييم |
|---|---|---|---|
| KMP common engine | Android/iOS من نفس المنطق | قوي | 5 |
| `FrameIngressGate` داخل engine | backpressure مستقل عن UI | قوي | 5 |
| `PresenceSupervisorBridge` | توحيد no-pose/visibility | جيد لكن host يضيع no-pose | 3 |
| `ExecutionClock` | مدة جلسة صحيحة مع pauses | مغلق بعد G3 | 5 |
| `TrainingMotionSession` + journal | checkpoint/offline resilient | جيد | 4 |
| Outbox planned writes | backend progress صحيح | مغلق بعد G1 | 5 |
| `TrainingSessionReportCache` | عرض تقرير بعد الجلسة | مفيد | 4 |
| iOS actual boundaries | تمهيد iOS | detector stub | 3 |
| Compose design system | UI موحد | setup ناقص | 3 |
| cold offline bundle | configs/messages offline | قوي | 4 |
| tests/parity suite | ثقة عالية | قوي | 5 |
| heavy model cache/upgrade | تحميل/استخدام heavy model عند توفره | جيد Android، يحتاج قياس | 4 |

---

## 15) أفكار Legacy لم تُنقل بالكامل

| الفكرة | مكانها في Legacy | ما ينقص KMP | الأولوية |
|---|---|---|---|
| setup axis UI | `SetupCountdownBinder` | region/posture/direction statuses في UI | P0 |
| reference image | `positionImageUrl` | عرض صورة/placeholder في setup | P0/P1 |
| joint guidance rows | `updateJointGuidanceRows` | قائمة مفاصل/زوايا/أسهم | P0 |
| setup skeleton guidance | `updateSetupGuidance` | تلوين/أسهم setup على skeleton | P0 |
| setup voice | `speakSetupGuidance`, `speakSetupPhaseGuidance` | `FeedbackKind.SETUP` + cooldown | P0 |
| cached coaching audio | `AudioFeedbackPlayer` + audio cache | ربط `AudioManifestCache` بمشغل فعلي | P0/P1 |
| no-pose pipeline | `onNoPoseDetected` | تمرير no-pose من host إلى VM | P0 |
| high FPS | `applyHighFps` | Camera2 target FPS | P1 |
| widest zoom | `applyWidestZoom` | min zoom ratio | P1 |
| camera diagnostics | `diagSupportedRanges`, etc. | debug/telemetry | P1 |
| tilt correction | `PoseApp.instance.tiltProvider` | تمرير `DeviceTiltPort` إلى setup/engine | P1 |
| posture/refinement MLP | `PostureMlpClassifier` / refiners | `AndroidPoseRefiner` موجود كـ placeholder وغير موصول | P1/P2 |
| frame captures | `FrameCaptureManager` | KMP capture implementation | P1/P2 |
| best/worst replay | report UI components | KMP report detail richer | P2 |
| video mode analysis | `PoseLandmarkerHelper` VIDEO mode | غير موجود في KMP live path | P3 حسب المنتج |
| phase continue policy | `PhaseResumeAction` | lifecycle/background semantics | P1 |
| warmup/cooldown progress exclusion | `countsTowardWorkoutProgress` | flow role semantics | P1 |

---

## 16) أفكار Legacy نفسها كانت ناقصة أو مخاطرة

ليس كل ما في Legacy يجب نسخه كما هو:

| النقطة | تقييم |
|---|---|
| `TrainingActivity` كان thin host بالاسم فقط؛ فعلياً يملك lifecycle وUI وsync وreport وcamera، وهذا حمل كبير. |
| اعتماد Android-only يجعل iOS مستحيلاً تقريباً. |
| التقرير غني لكنه مكلف تخزينياً ومعقد، ويحتاج إدارة واضحة للحذف/الخصوصية. |
| `FrameCaptureManager` يحفظ صوراً محلية، وهذا مفيد UX لكنه أعلى مخاطرة خصوصية وأداء. |
| `PoseLandmarkerHelper` فيه تخصيص bitmap/rotation قوي، لكنه قريب جداً من Android/MediaPipe API. |
| كثرة coordinators حول Activity تجعل الاختبار أصعب. |
| `WorkoutTrainingEngine` غني، لكنه يخلط flow/report/progress في Android module. |

**قرار هندسي:** لا ننقل Legacy حرفياً؛ ننقل السلوك المنتجّي المهم إلى KMP بواجهات common وactuals للمنصة.

---

## 17) أولويات الإصلاح المقترحة

### P0 — قبل smoke جهاز

1. إصلاح ترتيب بدء الكاميرا:
   - لا تستدعِ `CameraFrameSource.start()` قبل `bindPreview`.
   - أو اجعل `bindPreview()` يعيد محاولة start إذا كانت configuration موجودة.
2. تمرير no-pose:
   - غيّر `TrainingSessionCameraHost` ليستدعي VM عند `frame == null`.
   - أو غيّر contract إلى `onFrame(PoseFrame?)`.
3. إعادة `isInStartPose` إلى KMP `StartPoseGate` أو داخل `SetupReadinessGate`.
4. جعل readiness confirmation يستخدم `startPose` في setup/countdown.
5. ربط `worstJointGuidance.message` و`cameraTip` في `TrainingSessionViewModel`.
6. إضافة `FeedbackKind.SETUP` مع cooldown من `SetupValidationConfig.voiceCooldownMs`.
7. عرض رسائل قابلة للتنفيذ:
   - “أظهر الجزء العلوي”
   - “قف جانبياً للكاميرا”
   - “ارفع الكوع الأيمن”
   - “اثنِ الركبة أكثر”
8. عرض `positionImageUrl` أو placeholder في setup.

### P1 — قبل pilot داخلي

1. نقل axis status إلى UI:
   - region/posture/direction icons.
2. تلوين skeleton أثناء setup بحسب joint guidance.
3. high FPS / widest zoom / diagnostics في `CameraXFrameSource`.
4. ربط `RepIncomplete` من `PhaseStateMachine` إلى VM/FeedbackRouter.
5. تمرير `poseVariantIndex` الحقيقي بدل الثابت `0`.
6. تمرير `DeviceTiltPort` إلى `SetupReadinessGate` و`MovitTrainingEngine` من Koin.
7. ربط `AudioFeedbackPlayer` فعلياً بالـ audio cache أو توثيق أن TTS هو المنتج الحالي.
8. تثبيت عقد `completePlannedWorkout` مقابل `reportPlannedWorkout` حتى لا يتكرر الرفع.
9. دعم workout roles: warmup/activation/cooldown في progress.
10. lifecycle/background phase policy شبيه `PhaseResumeAction`.

### P2 — قبل إطلاق تجربة تقرير كاملة

1. KMP frame capture manager:
   - danger/peak/error/hold samples.
   - limits واضحة.
   - privacy policy.
2. best/worst replay في Compose report.
3. hero frame في report detail.
4. مقارنة زاوية best/worst مثل Legacy.
5. export/share report UX.

### P3 — تحسينات لاحقة

1. video mode analysis في KMP.
2. إعدادات model full/heavy داخل UI KMP.
3. لوحة debug camera diagnostics للمطورين.
4. حفظ trace مختصر لمراجعة bugs الميدانية.

---

## 18) توصية تصميمية لاستعادة تجربة البداية

بدل نسخ `SetupCountdownBinder` حرفياً، نقترح نموذج KMP:

```kotlin
data class SetupGuidanceUi(
    val phase: SetupPhase,
    val progressPercent: Int,
    val regionStatus: AxisStatusUi,
    val postureStatus: AxisStatusUi,
    val directionStatus: AxisStatusUi,
    val headline: String,
    val actionMessage: String?,
    val referenceImageUrl: String?,
    val joints: List<JointGuidanceUi>,
)
```

ثم:

- `SetupReadinessGate` يرجع result غني.
- `TrainingSessionViewModel` يحوله إلى `SetupGuidanceUi`.
- `SetupPosePanel` يعرض:
  - title واضح.
  - 3 axis chips.
  - reference image/illustration.
  - action message.
  - joint rows عند `ANGLES`.
  - progress.
- `FeedbackRouter` ينطق فقط `actionMessage` مع setup cooldown.

---

## 19) جدول قرارات النقل

| Legacy capability | ننقله؟ | السبب |
|---|---|---|
| sequential setup phases | نعم | جوهر فهم المستخدم. |
| axis icons/status | نعم | UX مهم جداً. |
| reference image | نعم | يحل “ما الوضع المطلوب؟”. |
| joint rows بالزوايا | نعم، لكن مبسط | مفيد للمستخدم والمطور. |
| setup voice | نعم | أساسي أثناء الابتعاد عن الشاشة. |
| cached coaching audio | نعم | يقلل latency ويرفع جودة التجربة؛ KMP يملك prefetch لكن المشغل غير موصول. |
| Posture/tilt refinement | نعم | مهم للوضعيات الصعبة، لكن يحتاج actual platform نظيف. |
| Camera2 high FPS | نعم | مهم للأداء. |
| widest zoom | نعم | مهم للـ full body / setup. |
| bitmap pool | نعم إذا أثبت القياس allocations | لا ننقل بلا قياس. |
| frame captures | نعم لكن بضوابط privacy | قيمة تعليمية كبيرة. |
| replay clips | لاحقاً | مكلف، لكن مفيد premium. |
| Android Activity coordinators | لا | نستخرج السلوك فقط إلى KMP. |
| VIDEO mode | لاحقاً | ليس حرجاً للتدريب الحي. |

---

## 20) خلاصة تنفيذية

1. **KMP current ليس تراجعاً في core engine**: العد، المراحل، الفورم، visibility، hold، bilateral، والتوقيت كلها قريبة من Legacy أو أفضل اختبارياً — **ومُتحقَّق على جهاز** 12/12 (§32).
2. **التراجع الحقيقي في UX حول بداية التمرين**: Legacy كان يقول للمستخدم ماذا يرى وماذا يغير؛ KMP تحسّن بعد §22.5 لكن صوت setup ما زال ناقصاً.
3. **الكاميرا (محدَّث):** بعد §29–§32 المسار **مستقر** على جهاز حقيقي؛ التراجع المتبقي **في throughput** (~7 fps فعلي vs ~15+ Legacy) — قرار محافظ مؤقت (§30.4).
4. **التراجع في التقارير المرئية**: Legacy أغنى بالصور/replay؛ KMP يرفع ويعرض تقريراً و**ينتقل تلقائياً** بعد الإكمال (§31).
5. **الجديد يتفوق في البنية والبيانات**: KMP، outbox، journal، planned workout (G1)، tests، iOS readiness.
6. **أفضل استراتيجية**: استعادة throughput تدريجياً مع `TrainingPipeline`؛ لا نعيد Legacy Activity-heavy.

---

## 21) قائمة فحص قبل اعتبار KMP أفضل من Legacy للمستخدم

- [x] FPS لا يبقى 0 بعد فتح الشاشة على جهاز حقيقي — **مُتحقَّق** (لوج 15:28 · `cam≈pose` في `TrainingPipeline`).
- [x] عند عدم وجود جسم، تظهر رسالة أو يُعالَج no-pose — **جزئياً** (مشرف + setup؛ تحسّن VM).
- [~] تظهر رسالة واضحة للـ region/posture/direction — **§22.5** chips + actionMessage.
- [~] تظهر صورة مرجعية — إن وُجد `positionImageUrl`.
- [x] في مرحلة angles تظهر رسالة مفصلية — **§22.5**.
- [ ] يتم نطق رسالة setup بدون spam.
- [x] start pose يستخدم `startPose` — **§22.5**.
- [x] countdown يتجمد عند فقدان الوضعية — **§22.5**.
- [x] FPS/zoom diagnostics — **`TrainingPipeline`** (§32) + debug overlay.
- [~] high FPS — **مطبَّق بقياس محافظ** `target=10` (~7 فعلي)؛ Legacy أسرع.
- [x] بعد الجلسة يرى المستخدم تقريراً — **G4 + انتقال تلقائي §31**.
- [x] planned workout يرفع start/complete/report — **G1**.
- [x] مدة pause لا تدخل في مدة الجلسة — **G3**.
- [x] لا تجميد بعد الإكمال — **§31** (`requiresCamera` + `clearAnalyzer`).
- [x] الشاشة لا تُطفأ أثناء التمرين — **§31 L1** `TrainingKeepScreenOn`.
- [x] visual report evidence مخطط أو موثق كفجوة متبقية — **v1 منفَّذ** (انظر §22.1؛ replay والتنظيف التلقائي متبقيان).

---

## 22.1 تنفيذ إصلاحات التقارير المرئية و evidence (2026-06-14)

### النطاق المنفَّذ (v1 محدود)

تم تنفيذ مسار إصلاح مستقل **بدون replay clips** وبدون matting/segmentation، مع الحفاظ على KMP common path خالياً من `Bitmap`.

| طبقة | ملف/مكوّن | الدور |
|---|---|---|
| Domain | `MovitPeakFrameCaptureManager` | منطق القبول/الحدود (peak واحد/عدة، danger≤6، error per rep+key، hold≤3، best≤3) — مرآة آمنة لـ Legacy `FrameCaptureManager` بدون I/O |
| Boundary | `TrainingFrameSnapshotPort` + `NoOpTrainingFrameSnapshotPort` | عقد حفظ JPEG محلي؛ iOS كان no-op في v1، ثم صار `IosTrainingFrameSnapshotPort` عند جاهزية bridge في §36 |
| Android | `MediaPipePoseDetector.takeSnapshotJpeg()` | يحتفظ بآخر إطار كاميرا مؤقتاً (يُستبدل كل frame) ويُصدّر JPEG عند الطلب فقط |
| Android | `AndroidTrainingFrameSnapshotPort` | يكتب full+thumb تحت `filesDir/frame_captures/{sessionId}/` |
| Feature | `TrainingFrameCaptureCoordinator` | يربط أحداث المحرك (BOTTOM/DANGER/WARNING/hold/rep complete) بالمدير + المنفذ |
| Feature | `TrainingSessionViewModel` | يمرّر الالتقاطات إلى `TrainingSessionWriteHooks.peakFrameCaptures` قبل بناء التقرير والـ cache |
| Data | `MovitPostTrainingReportBuilder` | يقبل `peakFrameCaptures` ويمرّرها إلى `legacyReport` JSON |
| UI | `ReportFrameEvidenceMapper` + حقول `ReportDetailUi.frameEvidence/heroFramePath` | يستفيد من `TrainingSessionReportCache` عبر `MovitSessionReportUiMapper` |
| UI | `ReportDetailScreen` | يعرض hero في Overview وقائمة Key moments في Form عند توفر مسارات محلية |

### حدود الخصوصية والتخزين (موروثة من Legacy ومطبَّقة في v1)

- **لا رفع تلقائي للصور**: المسارات المحلية فقط داخل `legacyReport.frameCaptures` عند رفع التنفيذ؛ لا blob في common journal.
- **حدود العدد**: danger 6، best 3، hold 3، peak واحد/عدة، error واحد لكل `(rep, errorKey)` مع cooldown.
- **إطار واحد في الذاكرة**: `lastFrameBitmap` يُستبدل كل إطار ويُحرَّر عند `shutdown` — لا أرشيف في الذاكرة.
- **التخزين على القرص**: `frame_captures/{sessionId}/*.jpg` — **لم يُنفَّذ بعد** حذف تلقائي عند logout/انتهاء الاحتفاظ (انظر المتبقي).

### ما لم يُنفَّذ (متعمداً في v1)

| بند | السبب |
|---|---|
| Replay burst / `RepReplayClip` | يوسّع النطاق (16 إطار/عدة + تتبع 10 عدات) — مؤجَّل |
| `ReportGenerator` richness كامل | danger alerts، error analysis، rep timeline، improvement tips — ما زالت فارغة في `PostTrainingReportLegacyJson` |
| مقارنة best/worst من الصور في Form | يعتمد replay أو set-level API metrics |
| تنظيف ملفات `frame_captures` | يحتاج سياسة retention مثل Legacy `ReportStorage.deleteFrameCaptures` |
| iOS snapshot فعلي | كان `NoOpTrainingFrameSnapshotPort` في v1؛ تم تجاوزه في §36 عبر `IosTrainingFrameSnapshotPort` + Swift bridge |
| Coil لـ `file://` على iOS | غير مُختبر؛ Android يستخدم `MovitRemoteImage` |

### الاختبارات المضافة/المحدَّثة

- `MovitPeakFrameCaptureManagerTest` — حدود peak/danger/error/best
- `MovitPostTrainingReportBuilderTest.legacyJson_encodesPeakFrameCaptures`
- `ReportFrameEvidenceMapperTest`
- `MovitSessionReportUiMapperTest.mapPostTraining_mapsPeakFrameEvidence`
- `TrainingFrameCaptureCoordinatorTest`

أمر التشغيل الموصى به:

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest :feature:reports:testDebugUnitTest :feature:training:testDebugUnitTest
```

### مسار المتابعة المقترح (v2)

1. سياسة حذف `frame_captures` عند إزالة التقرير أو logout.
2. Replay synthetic من stills الموجودة (بدون burst live) — أقرب لـ `BestWorstReplayPipeline` Legacy.
3. ربط `heroFrame` و`dangerAlerts` في JSON الغني تدريجياً من نفس `peakFrameCaptures`.
4. iOS: منفذ snapshot عبر `AVCapturePhoto` أو إطار آخر متاح في bridge.

---

## 22.2 تنفيذ إصلاحات planned workout والـ flow

**تاريخ التنفيذ:** 2026-06-14  
**النطاق:** planned workout write lifecycle · workout flow · explore batch · upload UX · workout roles · background phase policy (محدود).

### 22.2.1 عقد `completePlannedWorkout` مقابل `reportPlannedWorkout`

| المصدر | السلوك |
|---|---|
| Backend `POST …/complete` | `completePlannedWorkoutReport` — يضع `status=completed`، يحدّث `UserProgramProgress`، ويشغّل progression |
| Backend `POST …/report` | `updatePlannedWorkoutReport` — **تحديث جزئي للمقاييس فقط** (legacy compat؛ التعليق في الـ controller: "prefer /complete") |
| Legacy `SyncManager.flushPendingWorkoutReports` | يستدعي **`/complete` فقط** |
| KMP قبل الإصلاح | `finalizePlannedWorkoutDay()` كان يستدعي **الاثنين** → خطر outbox مزدوج وتعارض حالة |

**القرار المنفّذ:** مسار KMP يستدعي **`completePlannedDay` فقط** عند `WorkoutComplete`.  
`reportPlannedDay` / `reportPlannedWorkout` تبقى في الـ API للتوافق مع APKs قديمة ولكن **لا تُستدعى** من مسار الجلسة الحي.

**الملفات:** `TrainingSessionPlannedWritePolicy.kt` · `TrainingSessionViewModel.finalizePlannedWorkoutDay()` · `TrainingSessionWriteHooks.kt` (توثيق).

### 22.2.2 فشل الرفع وعدم اختفائه صامتاً

| قبل | بعد |
|---|---|
| `enqueueExecutionUpload` و batch `flush` يبلعان `AppResult.Failure` | `TrainingSessionWriteDiagnostics` يتابع نجاح/فشل enqueue |
| لا رسالة مستخدم | `uploadNotice` في `TrainingSessionUiState` + `WorkoutCompletePanel` |
| — | مفاتيح UX: `training_upload_outbox_pending` / `training_upload_enqueue_failed` (مع fallback عربي/إنجليزي عبر `SystemMessageRegistry`) |

**ملاحظة:** فشل enqueue الحالي يعني غالباً **غياب auth**؛ نجاح enqueue = **outbox pending** (يُزامَن لاحقاً عبر `OutboxDispatcher`). فشل dispatch الشبكي اللاحق ما زال مسؤولية orchestrator الـ sync — لم يُربط بشاشة التدريب في هذا الإصلاح.

### 22.2.3 Explore batch و `legacyReport`

| قبل | بعد |
|---|---|
| `WorkoutExecutionBatchCoordinator.flush` لا يمرّر `legacyReport` | `record(upload, legacyReport)` يحفظ JSON لكل تمرين؛ `flush` يمرّره إلى `uploadWorkoutExecution` |
| تمرين واحد كان يحصل على `legacyReport` عبر المسار المباشر فقط | explore/multi-exercise batch **parity** مع الرفع الفردي |

**الملفات:** `WorkoutExecutionBatchCoordinator.kt` · `TrainingSessionViewModel.enqueueUpload()` / `flushExploreBatchIfNeeded()`.

### 22.2.4 أدوار warmup / activation / cooldown في التقدّم

| طبقة | التغيير |
|---|---|
| `TrainingFlowItem.Exercise` | حقل `phaseRole` |
| `WorkoutFlowExerciseUi` + `WorkoutFlowMapper` + `toTrainingFlowItems` | تمرير `phaseRole` من `WorkoutSessionBlockUi` |
| `WorkoutFlowProgress` | `countsTowardWorkoutProgress` + `percentComplete` (يستبعد WARMUP/ACTIVATION/COOLDOWN) |
| `TrainingSessionFlowCoordinator` | `workoutProgressPercent()` |
| VM UI | `workoutFlowProgressPercent` في `syncFlowUi` |

**فجوة متبقية:** `phaseCanContinue` / `phaseMaxContinueTimeMs` على عناصر الخطة من الـ backend **غير منقولة** إلى `TrainingFlowItem` — lifecycle policy يستخدم defaults حتى يُضاف mapping من `PlannedWorkoutItemDto`.

**فجوة explore template:** `WorkoutTemplateSessionMapper` ما زال يضع `phaseRole = "MAIN"` لكل التمارين — مسار القوالب الجاهزة لا يحمل أدواراً بعد.

### 22.2.5 lifecycle / background (PhaseResumeAction)

**منفّذ (محدود):**

- `TrainingSessionLifecyclePolicy` — منطق pure قابل للاختبار (timeout · no-continue · resume).
- `TrainingSessionViewModel.onHostBackgrounded` / `onHostForegrounded` — إيقاف مؤقت أثناء TRAINING؛ إعادة تشغيل phase برسالة عند timeout/no-continue.
- `MovitTrainingRoutes` — `LifecycleEventObserver` ON_STOP/ON_START.

**متبقٍ (موثّق):**

- ربط `phaseCanContinue` / `phaseMaxContinueTimeMs` من بيانات الخطة.
- إعادة تشغيل كاملة لـ pre-exercise/countdown مثل Legacy `restartCurrentPhase(clearPhaseMetrics=true)`.
- iOS lifecycle parity (نفس الـ API على VM؛ الربط في route يعتمد على `LocalLifecycleOwner` المتاح في Compose Multiplatform).

### 22.2.6 اختبارات مضافة

| ملف | ما يغطيه |
|---|---|
| `TrainingSessionPlannedWritePolicyTest` | عدم استدعاء report بعد complete |
| `TrainingSessionWriteDiagnosticsTest` | pending / failure / planned complete — **غير منفَّذ بعد** (§23.4) |
| `TrainingSessionLifecyclePolicyTest` | resume · timeout · no-continue — **غير منفَّذ بعد** (§23.4) |
| `WorkoutFlowProgressTest` | استبعاد الأدوار + نسبة التقدّم |
| `TrainingSessionFlowCoordinatorTest` | `workoutProgressPercent` مع warmup |
| `WorkoutFlowPhaseRoleLaunchTest` | تمرير `phaseRole` من الجلسة إلى flow items |

### 22.2.7 ملخص الملفات المعدّلة

- `core/training-engine/.../WorkoutFlowProgress.kt` (جديد)
- `core/training-engine/.../TrainingSessionFlowCoordinator.kt`
- `core/training-engine/.../WorkoutFlowProgressTest.kt` (جديد)
- `core/training-engine/.../TrainingSessionFlowCoordinatorTest.kt`
- `feature/training/.../TrainingSessionPlannedWritePolicy.kt` (جديد)
- `feature/training/.../TrainingSessionWriteDiagnostics.kt` (جديد)
- `feature/training/.../TrainingSessionLifecyclePolicy.kt` (جديد)
- `feature/training/.../TrainingSessionViewModel.kt`
- `feature/training/.../TrainingSessionWriteHooks.kt`
- `feature/training/.../WorkoutExecutionBatchCoordinator.kt`
- `feature/training/.../TrainingSessionPanels.kt`
- `feature/training/.../TrainingSessionScreen.kt`
- `feature/training/.../MovitTrainingRoutes.kt`
- `feature/training/.../TrainingSessionPlannedWritePolicyTest.kt` (جديد)
- `feature/library/.../WorkoutFlowModels.kt`
- `feature/library/.../WorkoutTrainingLaunch.kt`
- `feature/library/.../WorkoutFlowPhaseRoleLaunchTest.kt` (جديد)

---

## 22.3 تنفيذ إصلاحات الكاميرا و no-pose

**تاريخ التنفيذ:** 2026-06-14  
**النطاق:** `core/pose-capture`, `feature/training` camera host, `SessionSupervisor` / no-pose pipeline — وفق أولويات P0/P1 في القسم 7 و19 من هذا التقرير.

### ما نُفّذ

| الهدف | الحالة | التفاصيل |
|---|---|---|
| ترتيب بدء الكاميرا (FPS=0) | ✅ | `CameraStartGate` يؤجل `start` حتى `bindPreview` جاهز (أي ترتيب). `TrainingSessionCameraHost` يبدأ الكاميرا فقط بعد `onPreviewReady` + `previewBound`. |
| تمرير no-pose إلى VM | ✅ | عقد `TrainingSessionCameraHost` أصبح `onFrame(PoseFrame?)`. الـ host يمرّر `null` من `CameraXFrameSource.onNoPoseDetected`. `TrainingSessionViewModel.onPoseFrame(PoseFrame?)` يرسل `SupervisorSignal.NoPoseFrame`. |
| SETUP_POSE + NoPose | ✅ | `SessionSupervisor.handleSetupPose` يصدّر `SupervisorAction.ShowSetupNoPoseHint` (مرة واحدة حتى يعود pose). VM يعرض `training_session_setup_no_pose` ويصفّر التقدّم. |
| high FPS / widest zoom / diagnostics | ✅ | `CameraXFrameSource`: `applyHighFps` عبر Camera2 interop، `applyWidestZoom` + retry، `CameraDiagnostics` + سجلات debug. |
| اختبارات | ✅ | `CameraStartGateTest`, `SessionSupervisorSetupNoPoseTest` |

### الملفات المعدّلة

- `core/pose-capture/.../CameraStartGate.kt` (جديد)
- `core/pose-capture/.../CameraXFrameSource.kt`
- `core/pose-capture/src/commonTest/.../CameraStartGateTest.kt` (جديد)
- `feature/training/.../TrainingSessionCameraHost.kt` (+ android/ios actuals)
- `feature/training/.../TrainingSessionViewModel.kt`
- `core/training-engine/.../SupervisorAction.kt`
- `core/training-engine/.../SessionSupervisor.kt`
- `core/training-engine/src/commonTest/.../SessionSupervisorSetupNoPoseTest.kt` (جديد)
- `core/resources` — مفتاح `training_session_setup_no_pose` (en/ar)

### ما بقي خارج هذا المسار

- ~~`isInStartPose` مقابل `isInStartPosition` في `SetupReadinessGate`~~ — **نُفّذ** في §22.5.
- إرشادات setup الغنية (محاور، skeleton ملوّن، صوت) — **جزئياً** (chips + رسائل؛ الصوت والـ skeleton ما زالا مفتوحين).
- iOS camera actual — كان placeholder؛ تم تجاوزه في §36 عبر `TrainingSessionCameraHost.ios` الفعلي.
- `TrainingCameraHost` (غير session) ما زال يتجاهل `frame?.let` — لم يُلمس ضمن النطاق.
- تحقق جهاز حقيقي: FPS>0، FOV أوسع، وتأثير high FPS على البطارية.

### تحديث قائمة الفحص (القسم 21)

- [x] تمرير no-pose من host إلى VM (منطقياً + unit tests).
- [x] رسالة setup عند عدم وجود جسم (`training_session_setup_no_pose`).
- [x] إصلاح ترتيب start/bind (منطقياً).
- [x] high FPS / widest zoom / diagnostics في debug logs.
- [ ] FPS لا يبقى 0 على جهاز حقيقي — يحتاج smoke يدوي.
- [ ] بقية بنود القسم 21 (setup غني، تقارير، إلخ).

---

## 22.4 تنفيذ إصلاحات الصوت و tilt/refiner

**تاريخ التنفيذ:** 2026-06-14  
**النطاق:** مسار إصلاح مستقل — ربط الصوت المخزن، `DeviceTiltPort`، و`PoseRefiner` placeholders.

### 22.4.1 الصوت المخزن + TTS fallback

| البند | الحالة | التفاصيل |
|---|---|---|
| `CachedAudioFeedbackPlayer` | **منفّذ** | `core/data/.../CachedAudioFeedbackPlayer.kt` — يقرأ الملف من `AudioFileDownloadPort` عبر `AudioClipResolver`، وإلا يعود إلى `SpeechSynthesizer`. |
| `FeedbackSignal.audioUrl` | **منفّذ** | حقل اختياري يُملأ من `LocalizedText.getAudioUrl()` في `TrainingSessionViewModel` لرسائل المفاصل والموضع. |
| `FeedbackRouter` wiring | **منفّذ** | `MovitTrainingRoutes` يمرّر `feedbackPorts.audioPlayer` إلى `FeedbackRouter` بجانب `speech`/`haptics`. |
| Prefetch | **بدون تغيير سلوك** | `TrainingSessionAudioHooks.prefetchOnSessionOpen` ما زال يعمل عند فتح الجلسة؛ المشغل يستهلك مجلد `audio_cache`. |
| TTS fallback | **محفوظ** | عند غياب الملف أو فشل `MediaPlayer`، `CachedAudioFeedbackPlayer` ينطق النص عبر TTS (عقد DS-6). |

**ملفات:** `AudioClipResolver.kt`, `CachedAudioFeedbackPlayer.kt`, `TrainingFeedbackPorts.android.kt`, `MovitTrainingRoutes.kt`, `TrainingSessionViewModel.kt`, `FeedbackModels.kt`.

**اختبارات:** `AudioClipResolverTest`, `FeedbackRouterTest.routesVoiceThroughAudioPlayerWhenPresent`.

### 22.4.2 DeviceTiltPort

| البند | الحالة | التفاصيل |
|---|---|---|
| Koin registration | **منفّذ** | `AndroidDeviceTiltPort` كـ `AcquirableDeviceTiltPort` في `movitPoseCaptureAndroidModule()`. |
| `SetupReadinessGate` | **موصول** | `TrainingSessionViewModel` يمرّر `deviceTiltPort` لتصحيح الملامح أثناء setup. |
| `MovitTrainingEngine` | **موصول** | نفس المنفذ إلى المحرك لـ `PositionValidator` و`acquire/release` أثناء التدريب. |
| Route resolution | **منفّذ** | `resolveTrainingDeviceTiltPort()` (Android: Koin؛ iOS: `MovitPoseCaptureIosBindings`). |

**ملفات:** `MovitPoseCaptureModule.kt`, `TrainingDeviceTiltPort.kt` (+ actuals), `TrainingSessionViewModel.kt`, `MovitTrainingRoutes.kt`.

### 22.4.3 PoseRefiner / MLP

| البند | الحالة | التفاصيل |
|---|---|---|
| Koin + detector hook | **موصول بأمان** | `PoseRefiner` يُحقَن في `MediaPipePoseDetector`؛ يُستدعى فقط عند `isAvailable == true`. |
| `AndroidPoseRefiner` | **placeholder** | `isAvailable = false` — لا يغيّر الملامح حالياً. |
| أصول MLP في KMP | **غائبة** | `posture_mlp.tflite` + `posture_mlp_norm.json` في `app/src/main/assets` (Legacy) فقط، وليست في `core:pose-capture` أو `core:resources`. |
| `PostureMlpClassifier` (Legacy) | **غير منقول** | يعتمد LiteRT/TFLite في حزمة `app`؛ نقله يتطلب نقل الأصول وطبقة inference إلى `core:pose-capture`. |

**فجوة باقية (P1/P2):** نقل أصول `posture_mlp_*` (واختيارياً `elbow_correction_mlp_*`) إلى module KMP مشترك، وتنفيذ `AndroidPoseRefiner` فوق LiteRT مع `PosePostureRefiner` في `PoseSceneDetector`.

### 22.4.4 ما لم يُمس

- setup UI panels، camera startup، FPS/zoom — لم تُعدَّل إلا ربط `PoseRefiner` الاختياري داخل `MediaPipePoseDetector` (no-op حتى توفر الأصول).
- Legacy `app` `AudioFeedbackPlayer` / `FeedbackManager` — لم تُحذف؛ المسار الجديد في `feature:training` + `core:data`.

### 22.4.5 اختبارات

| أمر Gradle | الغرض |
|---|---|
| `:core:data:cleanAllTests --tests "*AudioClipResolver*"` | resolver + fake cache |
| `:core:training-engine:cleanAllTests --tests "*FeedbackRouter*"` | تمرير الإشارة إلى `audioPlayer` |

---

## 22.5 تنفيذ إصلاحات Setup UX و startPose

**تاريخ التنفيذ:** 2026-06-14  
**النطاق:** مسار إصلاح مستقل (بدون commit) — بداية التمرين، `startPose`، وعرض إرشاد الإعداد في KMP.

### ما نُفّذ

| الهدف | الحالة | ملاحظات |
|---|---|---|
| `isInStartPose` في `StartPoseGate` | ✅ | أُضيفت الدالة باستخدام صندوق `TrackedJoint.startPose` (مثل Legacy MO). `isInStartPosition` بقي لمسار العد أثناء التمرين. |
| `SetupReadinessGate` يؤكد البداية عبر `startPose` | ✅ | `primaryReady`، `inStartPose`، و`isCountdownPoseValid` تستخدم `isInStartPose` بدل UP-zone counted state. |
| توسيع `SetupReadinessResult` | ✅ | `axisStatuses`، `jointGuidanceRows`، `referenceImageUrl` بجانب الحقول السابقة. |
| `TrainingSessionViewModel` → رسائل قابلة للتنفيذ | ✅ | `SetupGuidanceMapper` يبني `actionMessage` من `phaseMessage` / `worstJointGuidance` / `cameraTip`. أثناء العدdown يُستخدم `isCountdownPoseValid` فقط (لا `isConfirmed`). |
| `SetupPosePanel` / `TrainingSessionScreen` | ✅ | chips للمحاور (region/posture/direction)، رسالة إجراء رئيسية، صفوف مفاصل مختصرة في ANGLES، `MovitRemoteImage` لـ `positionImageUrl` عند توفرها. |
| اختبارات | ✅ | `StartPoseGateTest`، `SetupReadinessGateTest`، `SetupGuidanceMapperTest` — **نجحت** ضمن دمج §23. |
| `positionImageUrl` | ✅ مع فجوة بيانات | الحقل موجود في `PoseVariant`؛ يُمرَّر إلى UI ويُعرض عبر `MovitRemoteImage`. **فجوة:** معظم fixtures/`cold_offline_bundle` لا تملأ `positionImageUrl` بعد — يظهر placeholder حتى يُزوَّد الـ CDN من الـ backend. |

### ملفات مُعدَّلة / مُضافة

**core/training-engine**
- `engine/StartPoseGate.kt` — `isInStartPose()`
- `session/SetupReadinessGate.kt` — semantics + حقول result
- `session/SetupJointGuidance.kt` — `resolveAllJoints()`
- `session/SetupAxisStatus.kt` — **جديد**
- `commonTest/.../StartPoseGateTest.kt` — **جديد**
- `commonTest/.../SetupReadinessGateTest.kt` — محدَّث

**feature/training**
- `SetupGuidanceMapper.kt` — **جديد**
- `TrainingSessionViewModel.kt` — mapping + countdown validation
- `TrainingSessionPanels.kt` — `SetupPosePanel` غني
- `TrainingSessionScreen.kt` — تمرير الحقول الجديدة
- `commonTest/.../SetupGuidanceMapperTest.kt` — **جديد**

**إصلاح compile عرضي (لتشغيل اختبارات المحرك)**
- `report/MovitPeakFrameCaptureManager.kt` — إصلاح مراجع constructor مكسورة (كانت تمنع compile المشروع).

### اختبارات مُشغَّلة

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest \
  --tests "com.movit.core.training.engine.StartPoseGateTest" \
  --tests "com.movit.core.training.session.SetupReadinessGateTest"
```

**النتيجة:** BUILD SUCCESSFUL.

### ما بقي (خارج هذا المسار)

| فجوة | الأولوية |
|---|---|
| صوت إرشاد setup (`speakSetupPhaseGuidance` / cooldown) | عالية — لم يُربط `FeedbackRouter` بـ `actionMessage` |
| `getPoseRequirementsText()` / زوايا البداية كنص | متوسطة |
| any-side pairs في إرشاد المفاصل | متوسطة |
| skeleton overlay ملوّن في setup mode | متوسطة |
| تعبئة `positionImageUrl` في bundle/backend | بيانات |
| compile `:feature:training` (frame capture WIP) | ~~حاجز~~ — **انحل** بعد دمج المسارات (§23) |
| تحديث بند §22.3 (الكاميرا) — `isInStartPose` أصبح منفّذاً في §22.5 | توثيق |

### تحديث قائمة الفحص (§21)

- [x] في مرحلة angles تظهر رسالة مفصلية محددة (أسوأ مفصل + صفوف مختصرة).
- [x] start pose يستخدم `startPose` وليس UP-zone counted state.
- [x] countdown يتجمد عند فقدان الوضعية مع رسالة من `setupActionMessage`.
- [~] تظهر رسالة واضحة للـ region/posture/direction — **نعم** عبر `actionMessage`؛ chips للمحاور.
- [~] تظهر صورة مرجعية — **نعم** إن وُجد `positionImageUrl`؛ وإلا placeholder.
- [ ] يتم نطق رسالة setup بدون spam.

---

## 22.6 إصلاح parity حساب الزوايا (world 3D)

**تاريخ:** 2026-06-14  
**النطاق:** محاذاة `PoseFrameAssembler` مع Legacy `AngleCalculator.calculateAllAnglesSmoothed(worldLandmarks, use3D=true)` — **بدون commit**.

### المشكلة

| المسار | السلوك قبل الإصلاح |
|---|---|
| Legacy `CameraTrainingInputController` | يمرّر **world landmarks** المُنعّمة إلى `AngleCalculator` مع `use3D=true` لكل المفاصل (كتف/ورك/ركبة/كاحل/مرفق…). |
| KMP `PoseFrameAssembler.calculateAngles` | كان يستخدم `angleAt3D` **للمرفقين فقط**؛ بقية المفاصل عبر `angleAt` (إسقاط 2D على normalized landmarks). |

**الأثر:** في أوضاع جانبية أو عمق حقيقي (squat/lunge)، زوايا الورك/الركبة/الكتف في KMP كانت تُسطّح بصرياً وتنحرف عن منطق المحرك Legacy — خصوصاً عند تفعيل عتبات phase/rep على `left_knee` / `left_hip`.

### الإصلاح

1. **`PoseFrameAssembler.kt`** — استبدال `angleAt` بـ `angleAt3D` لـ shoulder/hip/knee/ankle (نفس مسار المرفقين): world 3D عند توفر landmarks مرئية، وإلا fallback 2D على normalized.
2. **المرآة (front camera)** — بدون تغيير UI: المحرك يعتمد على `PoseLandmarkMirroring.mirrorAngles` + `mirroredIndex` عند القراءة (مثل Legacy)، وليس على swap فعلي لقيم الـ landmarks. `mirrorLandmarks` يحتوي swap ثنائي الاتجاه في `swapMap` فيُعيد الـ landmarks لحالتها الأصلية (no-op) — وهذا متوافق مع `MovitTrainingEngine` الذي يمرّر `frame.isFrontCamera` الأصلي مع `workingFrame.landmarks` بعد `mirrored()`.
3. **مسار MediaPipe** — `CameraXFrameSource` كان يمرّر `worldLandmarks` إلى `assemble` أصلاً؛ لا حاجة لتعديل flip الكاميرا في هذا المسار.

### الاختبارات الجديدة

`PoseFrameAssemblerTest`:

- `limbAngles_use3DWorldWhenAvailable` — ركبة 3D ≠ إسقاط 2D.
- `shoulderHipAnkle_use3DWorldWhenAvailable` — parity مباشر مع `JointAngleCalculator.angleDegrees3D`.
- `limbAngles_fallbackTo2D_whenWorldNull` / `whenWorldLandmarkNotVisible`.
- `frontCameraMirror_restoresAnatomicalLimbAngles_with3DWorld`.
- `worldLandmarksShorterThanNorm_fallsBackTo2D`.

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest \
  --tests "com.movit.core.training.geometry.PoseFrameAssemblerTest"
```

### فجوات متبقية (خارج هذا الإصلاح)

| البند | الملاحظة |
|---|---|
| زوايا cross-shoulder / cross-hip / neck / spine / wrist | Legacy يحسبها؛ KMP `JointAngles` ما زالت `null` لها في `calculateAngles` — لم تُستخدم بعد في configs الحالية. |
| smoothing لـ world landmarks | Legacy `LandmarkSmoother.convertWorld`؛ KMP يمرّر world خام من MediaPipe (بدون One Euro على world). |
| `visibilityThreshold` | Legacy training يستخدم ~0.3؛ KMP default 0.5 في assembler — قرار منفصل. |

---

## 23) نتيجة الدمج والتحقق النهائي

**تاريخ:** 2026-06-14  
**النطاق:** دمج منطقي لخمسة مسارات متوازية (camera/no-pose · setup UX/startPose · audio/tilt/refiner · planned workout/flow · report evidence) **بدون commit**.

### 23.1 فحص Git والتعارضات

| البند | النتيجة |
|---|---|
| علامات merge conflict (`<<<<<<<`) | **لا يوجد** في `.kt` / `.md` |
| تعارض منطقي في الملفات الحرجة | **لم يُكتشف** — التكامل عبر حقن منافذ (`frameSnapshotPort`، `deviceTiltPort`، `audioPlayer`) وعقود nullable (`onFrame: PoseFrame?`) |
| ملفات معدّلة/جديدة غير متتبعة | ~70 ملفاً في `android-poc` + ملف audit (انظر `git status`) |

**نقاط التقاء رئيسية (تم التحقق من التوافق):**

- `TrainingSessionViewModel` — يجمع: no-pose، setup guidance، frame capture → `peakFrameCaptures`، planned complete فقط، explore batch + upload notice.
- `MovitTrainingRoutes` — `FeedbackRouter(audioPlayer)` + `deviceTiltPort` + lifecycle observer + `TrainingSessionCameraHost(onFrame)`.
- `TrainingSessionCameraHost` expect/actual — `PoseFrame?`؛ Android يبدأ الكاميرا بعد `previewBound`.
- `CameraXFrameSource` / `MediaPipePoseDetector` — high FPS، zoom، snapshot JPEG، `PoseRefiner` اختياري.
- `FeedbackModels.audioUrl` + `CachedAudioFeedbackPlayer` + `AudioClipResolver`.
- `MovitPeakFrameCaptureManager` → `TrainingFrameCaptureCoordinator` → `TrainingSessionWriteHooks`.
- `TrainingSessionPlannedWritePolicy` — `/complete` فقط؛ لا `/report` مزدوج.

### 23.2 أوامر التحقق المُنفَّذة

```bash
cd android-poc

# اختبارات الوحدات المركزة (المسارات الخمسة)
./gradlew :core:training-engine:testDebugUnitTest \
  :core:pose-capture:testDebugUnitTest \
  :feature:training:testDebugUnitTest \
  :feature:library:testDebugUnitTest \
  :feature:reports:testDebugUnitTest

# صوت + resolver
./gradlew :core:data:testDebugUnitTest

# تجميع APK debug (compile كامل للتطبيق)
./gradlew :app:assembleDebug
```

### 23.3 نتائج البناء والاختبارات

| الأمر | النتيجة |
|---|---|
| `:core:training-engine:testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `:core:pose-capture:testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `:feature:training:testDebugUnitTest` | **BUILD SUCCESSFUL** (يشمل `SetupGuidanceMapperTest`، `TrainingFrameCaptureCoordinatorTest`، `TrainingSessionPlannedWritePolicyTest`) |
| `:feature:library:testDebugUnitTest` | **BUILD SUCCESSFUL** (يشمل `WorkoutFlowPhaseRoleLaunchTest`) |
| `:feature:reports:testDebugUnitTest` | **BUILD SUCCESSFUL** (يشمل `ReportFrameEvidenceMapperTest`، `MovitSessionReportUiMapperTest`) |
| `:core:data:testDebugUnitTest` | **BUILD SUCCESSFUL** (يشمل `AudioClipResolverTest`) |
| `:app:assembleDebug` | **BUILD SUCCESSFUL** |

**Linter (IDE):** لا أخطاء على الملفات الحرجة التي فُحصت (`TrainingSessionViewModel`، `MovitTrainingRoutes`، `CameraXFrameSource`، `MovitPeakFrameCaptureManager`).

### 23.4 ما لم يُنفَّذ / فجوات موثَّقة (تحتاج جهاز أو قرار)

| البند | السبب / المالك |
|---|---|
| FPS>0 و FOV أوسع على جهاز حقيقي | smoke يدوي Android |
| iOS camera + snapshot + lifecycle parity | Mac / bridge iOS |
| `posture_mlp_*` أصول + `AndroidPoseRefiner` فعلي | نقل أصول إلى `core:pose-capture` |
| `positionImageUrl` في `cold_offline_bundle` / CDN | backend + script توليد bundle |
| `phaseCanContinue` / `phaseMaxContinueTimeMs` من الخطة | backend DTO + mapper |
| `TrainingSessionWriteDiagnosticsTest` / `TrainingSessionLifecyclePolicyTest` | **مذكورة في §22.2.6 لكن الملفات غير موجودة بعد** — اختياري للمتابعة |
| تنظيف `frame_captures/` عند logout/retention | سياسة تخزين |
| Replay clips / تقرير JSON غني كامل | v2 report evidence |
| صوت إرشاد setup بدون spam | ربط `actionMessage` → `FeedbackRouter` |
| `TrainingCameraHost` (غير session) ما زال `frame?.let` | مسار ExerciseLive منفصل |

### 23.5 ملخص تنفيذي

الدمج المنطقي لمسارات الـ Composer الخمسة **نجح**: compile كامل، اختبارات الوحدات المستهدفة، و`assembleDebug` — **بدون أخطاء compile أو فشل اختبار**. لم تُجرَ تعديلات كود إضافية أثناء الدمج (الشجرة كانت متسقة مسبقاً). أُعيد ترقيم أقسام §22 إلى 22.1–22.5 لتجنب تكرار `22.x`.

---

## 24) إصلاح منطق visibility / no-pose / countdown (2026-06-14)

**النطاق:** `MovitTrainingEngine`, `SessionSupervisor`, `TrainingSessionViewModel`, `TrainingSessionWriteHooks` — **بدون commit**.

### 24.1 المشاكل التي أُصلحت

| # | المشكلة | الإصلاح |
|---|---|---|
| 1 | `ResumeFromVisibilityPause` يستدعي `engine.resume()` دون مسح `pauseController.isVisibilityPaused` | `MovitTrainingEngine.resume()` يستدعي الآن `pauseController.onUserOrSupervisorResume(visibilityMonitor)` مثل Legacy `onUserOrSupervisorResume`. |
| 2 | أثناء تحذير الرؤية، `isCountingSuspended` يُضبط لكن المحرك يواصل العد | `processPoseFrame` يخرج مبكراً عند `processVisibilityResult() == true` (يشمل التحذير والإيقاف المؤقت). |
| 3 | no-pose أثناء `visibilityWarningActive` لا يصل للمشرف | `TrainingSessionViewModel.onPoseFrame` يمرّر `NoPoseFrame` دائماً للمشرف (أُزيل gate `visibilityWarningActive`). |
| 4 | fallback الطابع الزمني لـ no-pose ثابت فلا يتراكم الزمن | `TrainingPresenceClock` + `SessionSupervisor.effectivePresenceNow()` يستخدمان ساعة حائط متصاعدة عند غياب timestamp صالح. |
| 5 | `PoseFrame` في COUNTDOWN يصفّر `countdownInvalidStartMs` قبل `PoseInvalid` | أُزيل reset من `PoseFrame`؛ يُرسل `CountdownPoseValid` من VM عند نجاح التحقق فقط. |
| 6 | `DeviceTiltPort` غير موصول لـ `SetupReadinessGate` | `readinessGate = SetupReadinessGate(setupValidation, deviceTiltPort)`. |
| 7 | `sessionQualityMeta` بلا إحصاءات visibility أو إسقاط إطارات ingress | `resolveSessionQualityMeta()` يدمج `visibilityPauseCount`, `cameraWarningCount`, `engine.droppedFrameCount()`. |

### 24.2 اختبارات مضافة

- `PauseControllerResumeTest` — مسح أعلام visibility عند الاستئناف.
- `MovitTrainingEngineVisibilitySuspendTest` — تعليق العد أثناء التحذير + `resume()` يمسح الأعلام.
- `SessionSupervisorCountdownInvalidTest` — تراكم زمن pose غير صالح + `CountdownPoseValid`.
- `SessionSupervisorNoPoseTimestampTest` — تراكم no-pose مع timestamp ثابت.
- `TrainingPresenceClockTest` — ساعة presence متصاعدة.
- `TrainingSessionWriteHooksQualityTest` — دمج إحصاءات الجودة.

### 24.3 أوامر التحقق

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest :feature:training:testDebugUnitTest
```

**الخطوة التالية:** smoke على جهاز حقيقي — إيقاف visibility تلقائي → countdown استئناف → استئناف تدريب مع الحفاظ على العدد.


---

## 25) إصلاح محاذاة الكاميرا والـ skeleton (2026-06-14)

> **الحالة:** ✅ مُنفَّذ في الشجرة الحالية (بدون commit في هذه الجلسة).  
> **البلاغ:** على جهاز حقيقي، رسم المفاصل لا يقع فوق صورة الكاميرا — الهيكل «يطفو» أو ينعكس أفقياً عن الجسم.

### 25.1 الأسباب الجذرية (مؤكدة)

| # | السبب | الموضع السابق |
|---|---|---|
| S2a | لا mirror للكاميرا الأمامية في الرسم بينما `PreviewView` يعكس المعاينة | `MovitSkeletonOverlay` كان يرسم `x * canvasWidth` مباشرة |
| S2b | لا تدوير buffer التحليل — `toBitmap()` + `setTargetRotation` ميتاداتا فقط | `MediaPipePoseDetector.detectAsync` |
| S2c | لا تعويض aspect/crop — تحليل 4:3 داخل شاشة طويلة مع `FILL_CENTER` | overlay vs `PreviewView` |

### 25.2 ما تم تنفيذه

| المكوّن | التغيير |
|---|---|
| `DisplayLandmarkTransform` + `CameraFrameLayout` + `DisplayScaleMode` | تحويل FILL_CENTER / FIT_CENTER: `scale = max/fmin(view/analysis)`, offset مركزي، `mirrorX` للعرض فقط |
| `PoseFrame` | حقول `analysisImageWidth` / `analysisImageHeight` |
| `MediaPipePoseDetector` | تدوير bitmap إلى وضع عمودي قبل MediaPipe (بدون mirror ML — المحرك يبقى على `PoseFrame.mirrored()`) |
| `CameraXFrameSource` | تمرير أبعاد التحليل إلى `PoseFrameAssembler` |
| `TrainingSessionViewModel` + `TrainingSessionScreen` | `skeletonAnalysisWidth/Height`, `skeletonMirrorPreview` → `skeletonLandmarkProjector` |
| `MovitSkeletonOverlay` | `landmarkProjector` اختياري بدل stretch مباشر |
| `TrainingCameraSurface` | `PreviewView.ScaleType.FILL_CENTER` صريحاً لمطابقة الحساب |
| `SkeletonOverlayMapper` | جسر feature/training بين `DisplayLandmarkTransform` والـ overlay |

### 25.3 قرارات التصميم

1. **Mirror للعرض فقط:** Legacy كان يعكس bitmap قبل ML؛ KMP يبقي landmarks غير معكوسة للمحرك ويطبّق `mirrorX` في `DisplayLandmarkTransform` فقط — متوافق مع `MovitTrainingEngine` الذي يستدعي `frame.mirrored()` للزوايا.
2. **Rotation قبل ML:** تدوير bitmap مثل Legacy (بدون `ImageProcessingOptions` حتى الآن — نفس النتيجة، مسار أوضح للاختبار).
3. **Fallback:** إذا `analysisImageWidth/Height == 0` يعود overlay إلى stretch كامل (سلوك قديم).

### 25.4 اختبارات وتحقق

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest \
  :feature:training:testDebugUnitTest \
  :core:pose-capture:testDebugUnitTest \
  :core:designsystem:compileDebugKotlinAndroid \
  :feature:training:compileDebugKotlinAndroid
```

| اختبار | يغطي |
|---|---|
| `DisplayLandmarkTransformTest` | FILL_CENTER center/crop، FIT_CENTER، mirrorX |
| `SkeletonOverlayMapperTest` | جسر projector → canvas |

### 25.5 ما بقي (خارج نطاق هذا الإصلاح)

| البند | المالك |
|---|---|
| smoke يدوي على جهاز (محاذاة بصرية + flip كاميرا) | QA / مطوّر |
| R1/R3 من §16 (مسار رسم منفصل عن UiState، قتل toBitmap) | أداء لاحق |
| `ImageProcessingOptions` بدل rotate bitmap (I-4) | تحسين CPU/GC |
| `AssessmentCameraHost` / `ExerciseLive` hosts الأخرى | parity اختياري |
| تنعيم مزدوج (رسم خام vs محرك) | R5 |

### 25.6 ملخص

إصلاح المحاذاة الهندسية مكتمل في common + Android: landmarks + preview + overlay يشاركون نفس FILL_CENTER crop وmirror العرض للكاميرا الأمامية، مع buffer تحليل مُدوَّر. **لم يُجرَ commit.** التحقق التالي: smoke على جهاز حقيقي.

---

## 26) إصلاح تبديل الكاميرا (Camera Flip) — 2026-06-14

**النطاق:** `CameraXFrameSource`, `CameraStartGate`, `CameraFrameSource`, `TrainingSessionCameraHost`, `TrainingSessionViewModel`, `TrainingSessionScreen` — استجابة لملاحظات جهاز حقيقي: زر flip غير موثوق.

### 26.1 المشاكل المؤكدة (قبل الإصلاح)

| المشكلة | الأثر |
|---|---|
| `beginCameraSession()` يُستدعى عند كل flip | إعادة `warmUp` لـ MediaPipe + `ProcessCameraProvider.getInstance` listener جديد |
| `providerFuture.addListener` متكرر | سباقات (races) عند تبديل سريع |
| `stop()` يستدعي `executor.shutdown()` على singleton Koin | جلسات لاحقة تفشل — الـ executor مُغلق |
| أخطاء `bindToLifecycle` تُسجَّل فقط في Logcat | لا تصل للواجهة / VM |
| زر flip معطّل حين `isCameraReady=false` | المستخدم لا يرى سبب التعطيل ولا يستطيع التبديل المبكر |

### 26.2 التصميم بعد الإصلاح

**`CameraStartGate`** يميّز الآن:
- `InitialBind` — أول ربط بعد جاهزية preview + start
- `SwitchFacing` — تغيير `useFrontCamera` بعد `markBound`
- `NoOp` — نفس العدسة المطلوبة
- `Defer` — أحد الطرفين لم يجهّز بعد

**`CameraXFrameSource`:**
- `ensurePoseDetectorReady()` — `warmUp` مرة واحدة (`detectorWarmedUp`)
- `ensureCameraProvider()` — listener واحد (`providerInitializing` / `providerReady`)
- `switchCamera()` — `unbindAll` + `bindToLifecycle` بدون إعادة تشغيل كامل
- `analysisExecutor()` — يُعاد إنشاؤه إذا كان مُغلقاً؛ `stop()` لا يغلق الـ executor
- `dispose()` — تنظيف كامل اختياري (landmarker + executor) منفصل عن `stop()`
- `setErrorListener` / `setOnCameraBoundListener` — عقد واضح للأخطاء والنجاح

**UX:**
- `isCameraSwitching` في `TrainingSessionUiState`
- الزر يُعطَّل أثناء التبديل فقط (`!isCameraSwitching`) وليس عند `!isCameraReady`
- `onCameraSwitchStarted()` قبل تبديل الحالة في `MovitTrainingRoutes`

### 26.3 الملفات المتأثرة

| الملف | التغيير |
|---|---|
| `core/pose-capture/.../CameraStartGate.kt` | state machine: InitialBind / SwitchFacing / NoOp / Defer |
| `core/pose-capture/.../CameraXFrameSource.kt` | إعادة تصميم switch + lifecycle executor |
| `core/training-engine/.../CameraFrameSource.kt` | `setErrorListener`, `setOnCameraBoundListener` |
| `feature/training/.../TrainingSessionCameraHost.android.kt` | ربط callbacks؛ إزالة `onCameraReady` المباشر من bindPreview |
| `feature/training/.../TrainingSessionViewModel.kt` | `isCameraSwitching`, `onCameraSwitchStarted` |
| `feature/training/.../TrainingSessionScreen.kt` | تمكين الزر أثناء عدم الجاهزية ما لم يكن switching |
| `core/resources/...` | `training_session_camera_switch_failed` |

### 26.4 الاختبارات

| الاختبار | النوع |
|---|---|
| `CameraStartGateTest` — InitialBind / SwitchFacing / NoOp / reset | unit (`commonTest`) |
| compile + `:core:pose-capture:testDebugUnitTest` | Gradle |
| smoke يدوي — flip متكرر على جهاز حقيقي | يدوي (P0) |

### 26.5 فجوات متبقية

| البند | المالك |
|---|---|
| iOS flip بنفس نموذج SwitchFacing خفيف | Mac / `IosCameraFrameSource` |
| `TrainingCameraHost` (ExerciseLive) — نفس نمط flip | مسار منفصل |
| رسالة خطأ flip مترجمة في VM بدل نص Logcat الخام | تحسين UX اختياري |

---

## 27) إصلاح تكامل التدريب / التقارير / workout flow

**تاريخ:** 2026-06-14  
**النطاق:** إغلاق C1 · C2 · H3 · H5 من تقرير التكامل (بدون commit).

### 27.1 المشاكل المُصلَحة

| الرمز | المشكلة | الإصلاح |
|---|---|---|
| **C1** | `onFinish` / `handleWorkoutTrainingFinish` يتقدّم تمريناً واحداً في `WorkoutRunProgressStore` بعد إكمال workout كامل داخل `TrainingSession` | `WorkoutRunProgressStore.onTrainingSessionFinish(..., isWorkoutFlowComplete)` يمسح التقدّم عند `isWorkoutComplete`؛ `TrainingSessionRoute.onFinish(Boolean)` يمرّر الحالة من الـ VM |
| **C2** | `reportDetailId` في batch/planned يستخدم `activeSlug` بدل معرّف الرفع/الـ enqueue | `flushExploreBatchIfNeeded` → `flushAwait` مع `uploadId`+`reportId`؛ `TrainingSessionReportCache.rekeyPostTraining`؛ planned → `putSession(plannedWorkoutId)` |
| **H3** | `ExerciseLiveRoute` بلا `onViewReport` | `onViewReport` + تمرير من `MovitInnerHost` → `ReportDetail` |
| **H5** | فشل `startPlannedWorkout` لا يدخل diagnostics | `recordWriteOutcome(..., PLANNED_START)` |
| **H5 (frames)** | التقاط الإطارات async قد لا يلحق `finalize` | `TrainingFrameCaptureCoordinator.awaitPendingCaptures()` قبل بناء التقرير |
| **i18n** | تسميات frame evidence إنجليزية ثابتة | `ReportDetailStrings` + `report_detail_frame_*` |

### 27.2 ملفات رئيسية

`WorkoutRunProgress.kt` · `MovitInnerHost.kt` · `MovitTrainingRoutes.kt` · `TrainingSessionViewModel.kt` · `TrainingFrameCaptureCoordinator.kt` · `WorkoutExecutionBatchCoordinator.kt` · `TrainingSessionReportCache.kt` · `ReportFrameEvidenceMapper.kt` · `SharedReportDetailRepository.kt`

### 27.3 اختبارات مُضافة

`WorkoutTrainingFinishResolverTest` · `TrainingSessionWriteDiagnosticsTest` · `TrainingFrameCaptureCoordinatorTest.awaitPendingCaptures_*` · `TrainingSessionReportCacheTest` (rekey + session) · `ReportFrameEvidenceMapperTest` (ar)

### 27.4 فجوات متبقية

| البند | الملاحظة |
|---|---|
| تنظيف `frame_captures/` عند logout | سياسة تخزين — توثيق فقط |
| تقرير planned متعدد التمارين كصفوف UI | `mapSessionOverview` ملخص يوم؛ تفصيل لكل تمرين لاحقاً |

---

## 28) نتيجة الدمج والتحقق بعد إصلاحات runtime (2026-06-14)

**النطاق:** دمج خمسة وكلاء runtime (display/skeleton · camera flip/lifecycle · engine visibility/no-pose/countdown · angle 3D parity · reports/workout integration) + إصلاح فشل `LiveExerciseRunnerTest` — **بدون commit**.

### 28.1 فحص Git والتداخلات

| البند | النتيجة |
|---|---|
| علامات merge conflict | **لا يوجد** |
| `:feature:training` compile (`MovitTrainingRoutes` / `TrainingPresenceClock` / `TrainingSessionRoute`) | **نجح** — التوقيعات متوافقة مع `MovitInnerHost` (`onFinish(Boolean)`, `onViewReport`) |
| تعارض منطقي بين الوكلاء | **لم يُكتشف** — التقاء عبر منافذ (`deviceTiltPort`, `frameSnapshotPort`, `FeedbackRouter`) وحقول `PoseFrame` الجديدة |
| إصلاح إضافي أثناء الدمج | `VirtualLandmarks` في `PoseFrameAssembler` — parity مع Legacy (neck/spine 33–34) لإصلاح visibility على `spine` و`LiveExerciseRunnerTest` |

### 28.2 أوامر التحقق

```bash
cd android-poc

./gradlew :core:training-engine:testDebugUnitTest \
  :core:pose-capture:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :feature:training:testDebugUnitTest \
  :feature:library:testDebugUnitTest \
  :feature:reports:testDebugUnitTest

./gradlew :app:assembleDebug
```

### 28.3 نتائج البناء والاختبارات

| الأمر | النتيجة |
|---|---|
| `:core:training-engine:testDebugUnitTest` | **BUILD SUCCESSFUL** (138 اختباراً؛ كان `LiveExerciseRunnerTest.squatCycle_countsRepsFromSyntheticFrames` يفشل بـ 0 reps قبل إصلاح virtual landmarks) |
| `:core:pose-capture:testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `:core:designsystem:testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `:feature:training:testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `:feature:library:testDebugUnitTest` | **جزئي** — اختبارات workout (`Workout*`) **خضراء**؛ 3 فشل في `ProgramDetailViewModelTest` (خارج نطاق runtime — enroll/edit API fakes) |
| `:feature:reports:testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `:app:assembleDebug` | **BUILD SUCCESSFUL** |

### 28.4 ترقيم الأقسام

أُعيد ترقيم أقسام الإصلاحات المتداخلة: §24 visibility · §25 skeleton · §26 camera flip · §27 integration — لتجنب تكرار «§24» ثلاث مرات.

### 28.5 ما بقي (جهاز / Mac / backend فقط)

| البند | المالك |
|---|---|
| smoke محاذاة skeleton + flip كاميرا على جهاز حقيقي | QA Android |
| iOS camera flip + snapshot + lifecycle | Mac |
| `posture_mlp_*` + `AndroidPoseRefiner` فعلي | نقل أصول |
| `positionImageUrl` في cold bundle / CDN | backend |
| حساب زوايا `spine`/`neck` في `PoseFrameAssembler` (ليس فقط visibility) | parity لاحق مع Legacy `AngleCalculator` |
| `ProgramDetailViewModelTest` (3 فشل enroll/edit) | خارج نطاق runtime — فحص منفصل |
| `phaseCanContinue` من backend DTO | backend |

### 28.6 ملخص تنفيذي

الشجرة **تبني بالكامل** (`:app:assembleDebug`) واختبارات runtime المستهدفة **خضراء** بعد إصلاح `VirtualLandmarks` وتحديث اختبار visibility. استثناء موثّق: 3 اختبارات `ProgramDetailViewModelTest` في `:feature:library` (خارج نطاق الوكلاء الخمسة).

---

## 29) Hotfix بعد smoke الجهاز: حل لاج Camera2 المتكرر (2026-06-14)

**العرض على الجهاز:** عند فتح شاشة التدريب تبدأ رسائل CameraX/Camera2 بالتكرر بكثافة:

- `CaptureSession Issuing request for session`
- `Camera2CaptureRequestBuilder createCaptureRequest`
- `applyAeFpsRange: expectedFrameRateRange = [0, 0]`

ومعها يتجمد العد عند `3` تقريباً، والـ skeleton لا يتحرك، والتطبيق لا يستجيب بسلاسة.

### 29.1 السبب

في `CameraXFrameSource.applyWidestZoom()` كان منطق retry غير منتهٍ:

1. `applyWidestZoom()` يطبق `setLinearZoom`/`setZoomRatio`.
2. ثم يستدعي `scheduleWidestZoomRetry()`.
3. الـ retry يستدعي `applyWidestZoom()` مرة أخرى.
4. فتُجدول retries جديدة كل مرة.

هذا يصنع حلقة متزايدة من أوامر camera control، وكل أمر يعيد إصدار CaptureRequest داخل CameraX، وهو ما يفسر فيضان اللوج واللاج.

### 29.2 الإصلاح

تم فصل تطبيق الزوم عن جدولة الـ retry:

- `applyWidestZoom(previewView)` يطبق الزوم مرة واحدة ويجدول محاولتين فقط.
- `applyWidestZoomOnce(camera)` يطبق الزوم بدون جدولة retries جديدة.
- retries عند `300ms` و`1000ms` تستدعي `applyWidestZoomOnce` فقط.
- إذا كانت `zoomState` جاهزة نستخدم `setZoomRatio(minZoomRatio)` فقط، ولا نستدعي `setLinearZoom(0f)` معها لتقليل CaptureRequests.
- إذا لم تكن `zoomState` جاهزة نستخدم `setLinearZoom(0f)` كطلب مؤقت.

### 29.3 التحقق

```bash
cd android-poc
./gradlew :core:pose-capture:compileDebugKotlinAndroid \
  :core:pose-capture:testDebugUnitTest --no-daemon
```

**النتيجة:** `BUILD SUCCESSFUL`.

### 29.4 تحقق الجهاز

- [x] فتح شاشة التدريب — لا فيضان `CaptureRequest` مستمر (بعد §29).
- [x] العد `3-2-1` يكمل.
- [x] skeleton يتحرك بعد أول pose detection.
- [x] flip أمامي/خلفي بعد ثبات الكاميرا.
- [x] جلسة كاملة 12/12 بدون تجميد (لوج 2026-06-14 15:28 — §32.4).

---

## 30) Hotfixes استقرار الجهاز: تجميد · GC · عدّ Reps (2026-06-14)

**السياق:** بعد smoke الجهاز الأول (`Training.log.md`) ظهرت: عدم عدّ Reps، تجميد بعد ~20 ثانية، `ImageReader timeout`، `blocking GC Alloc`، `Pose inference stalled`.

### 30.1 الأعراض في اللوج الأول (قبل الإصلاح)

| العرض | الدليل |
|---|---|
| Reps لا تتقدم | `reps=0/12` ثابت |
| تجميد | `blocking GC Alloc` · `dequeueBuffer: timeout` |
| inference عالق | `Pose inference appears stalled` |
| إعدادات قديمة | `analysis=640×480` · `fps=[15,15]` |

### 30.2 الإصلاحات المنفَّذة

| # | الإصلاح | الملفات |
|---|---|---|
| H1 | `Channel.CONFLATED` + worker `Dispatchers.Default` | `TrainingSessionViewModel.kt` |
| H2 | buffer 64 + `onTrainingPoseFrameProcessed()` + `droppedActionCount` | `SessionSupervisor.kt` |
| H3 | `targetFps=10` | `TrainingSessionCameraHost.android.kt` |
| H4 | `320×240` + `shouldAnalyzeFrame()` | `CameraXFrameSource.kt` |
| H5 | `inferenceInFlight` + `tryAcquireInferenceSlot()` | `MediaPipePoseDetector.kt` |
| H6 | duplicate bind guard | `CameraXFrameSource.kt` |

### 30.3 نتيجة اللوج الثاني

- Reps `0→12` ✅ · `droppedEngine=0` · لا stall أثناء التمرين ✅
- **لكن** `ImageReader timeout` بعد `COMPLETED` — أُغلق في §31

### 30.4 قرار أداء معتمد (تنازل مقصود عن Legacy)

| البند | Legacy | KMP | السبب |
|---|---|---|---|
| دقة التحليل | ~640×480 | 320×240 | تقليل GC |
| throughput | ~15+ fps | ~7 fps فعلي | استقرار |
| bitmap pool | نعم | لا | I-4 مفتوح |

---

## 31) إصلاح ما بعد الإكمال: كاميرا · تقرير · شاشة مضيئة (2026-06-14)

| # | الإصلاح | الملفات |
|---|---|---|
| L1 | `TrainingKeepScreenOnEffect` (Android/iOS) | `TrainingKeepScreenOn.*` · `MovitTrainingRoutes` |
| L2 | `requiresCamera()` — إزالة الكاميرا عند الإكمال/REST | VM · Routes · Screen |
| L3 | `clearAnalyzer()` قبل `unbindAll()` | `CameraXFrameSource.stop()` |
| L4 | انتقال تلقائي للتقرير | `LaunchedEffect` في `MovitTrainingRoutes` |
| L5 | `awaitPendingCaptures` على `Dispatchers.Default` | `TrainingSessionViewModel` |

**تحقق جهاز (15:28):** `COMPLETED` → `report ready` (~56ms) → كاميرا `CLOSED` — **لا** ImageReader timeout.

**ملاحظة:** `report ready` milestone قد يظهر مرتين (cache + enqueue) — ضوضاء لوج فقط.

---

## 32) `TrainingPipelineDiagnostics` — قناة الحكم الموحّدة (2026-06-14)

**قرار:** قناة لوج واحدة بدل لوجات متفرقة.

| البند | القيمة |
|---|---|
| Tag | `TrainingPipeline` |
| دوري | كل 2s أثناء الكاميرا |
| Milestones | تغيير حالة المشرف · camera bound · report ready |

**مثال:**

```text
window=2s | cam=13fps(skipThrottle=7 target=10 ae=[10,10] analysis=320x240)
| pose=13fps(body=13 nopose=0 inferMs=43 busySkip=0) | vm=in 13 proc=13
| engine=state=TRAINING phase=BOTTOM reps=3/12 score=95 drop=0 | supervisor=drop=0
```

**لوج مرجعي 15:28:** جلسة صحية — `cam≈pose` · `busySkip=0` · `drop=0` · 12/12 reps.

**ملفات:** `core/training-engine/.../diagnostics/TrainingPipelineDiagnostics.kt` · ربط pose-capture + VM · `TrainingPipelineDiagnosticsTest`.

**فلتر Logcat:** `tag:TrainingPipeline`

---

## 33) جدول قرارات الجهاز — مرجع سريع

| التاريخ | القرار | السبب |
|---|---|---|
| 2026-06-14 | إصلاح zoom retry loop (§29) | فيضان CaptureRequest |
| 2026-06-14 | 320×240 @ target 10 | GC + stall |
| 2026-06-14 | VM worker + inferenceInFlight | تجميد UI |
| 2026-06-14 | إيقاف كاميرا عند الإكمال | ImageReader بعد COMPLETED |
| 2026-06-14 | انتقال تلقائي للتقرير | بلاغ المستخدم |
| 2026-06-14 | `TrainingPipeline` موحّد | تشخيص بدون ضوضاء |
| 2026-06-14 | Throughput profiles + flag (§35.2) | rollout تدريجي؛ افتراضي `stable` |
| مؤجَّل | رفع fps/دقة تدريجياً | بعد ثبات على أجهزة متعددة (مراحل §35.2) |

---

## 34) مراجعة كود مستقلة بعد التجربة الحقيقية (2026-06-14)

> هذا القسم مبني على قراءة مباشرة للكود في `D:/laragon/www/POSE-2` مقابل worktree `D:/laragon/www/POSE-2-MO-readonly`، وليس على افتراض صحة الأقسام السابقة. عند وجود تعارض بين هذا القسم وأي حكم سابق في الوثيقة، هذا القسم هو المرجع الأحدث. لم يتم تعديل worktree `MO`.

### 34.1 الحكم التنفيذي

KMP ليس مجرد نقل ناقص، وفيه تحسينات حقيقية: فصل common engine، اختبارات أكثر، backpressure أوضح، offline writes، `TrainingPipelineDiagnostics`، وتجميع أوضح لمسار الكاميرا/المحرك. لكن التجربة الحقيقية التي أظهرت فروقات في `Feedback` و`UI` و`Count Logic` و`Setup Pose` و`Count Metrics` متوافقة مع الكود: توجد فجوات correctness وUX وتقارير لا يجوز قبولها كتحسينات أثناء الهجرة.

أخطر فرق مؤكد هو `AFTER_ALL_REPS` في تمارين bilateral: Legacy ينهي التمرين على `targetReps * 2` حتى يكتمل الجانبان، بينما KMP يستخدم `targetReps` نفسه للتبديل ولإنهاء الجلسة. هذا regression في منطق العد وليس اختلاف UX.

### 34.2 فروقات مؤكدة وخطة المعالجة

| الأولوية | المجال | Legacy MO | KMP الحالي | الأثر | المعالجة المطلوبة |
|---|---|---|---|---|---|
| P0 | Count Logic / bilateral completion | `TrainingEngine` يحسب `completionTargetReps = targetReps * 2` عندما يكون `switchMode == AFTER_ALL_REPS` أو `switchEvery == targetReps`، ويمرره إلى `RepCounter` و`SessionOrchestrator`. | `MovitTrainingEngine` يستخدم `targetReps` مباشرة في `RepCounter` و`SessionOrchestrator`، بينما `BilateralController` يبدل الجانب بعد نفس الرقم. | تمرين bilateral قد يكتمل بعد أول جانب فقط. هذا يفسر اختلافات عميقة في العد والتقدم والـ HOLD/side flow. | إضافة `completionTargetReps` في KMP بنفس semantics القديمة. يبقى `BilateralController.targetReps` هو target per-side، لكن session completion وprogress النهائي يستخدمان total target. إضافة اختبار `AFTER_ALL_REPS` يثبت أن 12 reps per side تعني 24 completion reps. |
| P0 | Report richness / post-session analysis | `ReportGenerator` يبني `dangerAlerts`, `perfectMoments`, `bestReps`, `worstRep`, `errorAnalysis`, `repTimeline`, `consistency`, `improvementTips`, `holdSummary`, `heroFrame`, `performanceMetrics`, `overallQuality`, `exerciseConfig`, `setSummaries`. | ~~`MovitPostTrainingReport` أخف كثيرا، و`PostTrainingReportLegacyJson.encode` يضع أغلب هذه الحقول `empty` أو `null`.~~ **بعد §35.x P0 Report richness:** `MovitPostTrainingReportBuilderV2` يملأ النواة التحليلية من `repDetails` + captures؛ JSON لا يفرغ الحقول عند توفر البيانات. متبقٍ: `performanceMetrics`, `setSummaries`, `quickInsight`. | ~~التقرير بعد KMP يفقد معظم التفسير~~ — النواة مُستعادة؛ بطاقات metrics المتقدمة وmulti-set لاحقاً. | ✅ §35.x P0 Report richness؛ P1: `PerformanceMetricsBuilder`, multi-set `setSummaries`, `QuickInsightGenerator`. |
| P0 | Count Metrics / per-rep summary | `ExerciseWorkoutSummary` في Legacy يحتوي `stateBreakdown`, `commonErrors`, `repDetails`, `weightKg`, `weightUnit`. | KMP `ExerciseWorkoutSummary` يحتوي totals فقط: reps, counted, invalidated, averageScore, countedRatio, duration. | لا يمكن بناء تحليل مفاصل، مقارنة عدات، fatigue حقيقي، أو tips محلية من summary الحالي. | توسيع summary في KMP بنفس المادة: state breakdown، common errors، rep details، weights. تحديث write hooks والتقرير والاختبارات. |
| P1 | Position check details داخل العدة | Legacy `RepResult` يحتفظ بـ `positionErrors: List<PositionError>` مع الرسالة، landmarks، severity/type، والقيم. | KMP `RepResult` يحتفظ بـ `positionErrorCheckIds` فقط. | التقرير والـ feedback اللاحق يفقدان تفاصيل الخطأ، ويصبح تحليل common errors أقل دقة. | تخزين snapshot كامل أو DTO common من `PositionError` داخل `RepResult`، مع checkId فقط كحقل إضافي لا كبديل. |
| P1 | Setup Pose confirmation / any-side | Legacy `PoseSetupGuide` يحسب readiness من `startPose` مع قبول paired any-side عندما ينجح أحد الجانبين، ويشترط presence مناسب لكل primary. | KMP `SetupReadinessGate` يستخدم `StartPoseGate.isInStartPose` على المفاصل primary الموجودة فقط، ويتجاوز الزوايا الغائبة، ولا يظهر فيه نفس منطق paired any-side الموجود في `PoseSetupGuide.allPrimaryJointsPresent`. | تمارين any-side أو bilateral قد تدخل setup مبكرا جدا عند ظهور مفصل واحد، أو تختلف عند tracking mode/side pairing. | نقل منطق `allPrimaryJointsPresent` وpaired any-side إلى KMP setup gate، مع حد أدنى لحضور المفاصل واختبارات لتمارين any-side وbilateral. |
| P1 | Countdown pose validation | Legacy أثناء `COUNTDOWN` يستخدم `isStartPoseRoughlyValid`: سماحية `±10°`، ويقبل عند توفر 60% من primary joints تقريبا مع scene valid. | KMP `isCountdownPoseValid` يستخدم `StartPoseGate.isInStartPose`: صارم في الزاوية للمفاصل الموجودة، لكنه لا يفرض نفس نسبة الحضور. | العد التنازلي قد يتجمد عند انحراف بسيط كان Legacy يقبله، أو يستمر عند حضور مفاصل أقل من Legacy. | فصل شرط setup confirmation الصارم عن countdown guard. أضف `isStartPoseRoughlyValid` في KMP مع tolerance configurable ونسبة حضور مفاصل، واختبارات. |
| P1 | Setup feedback الصوتي | Legacy ينطق phase guidance وworst joint guidance عبر `speakSetupPhaseGuidance` و`speakSetupGuidance` مع cooldown. | KMP يعرض `actionMessage`, `cameraTip`, `jointRows`, `referenceImageUrl` بصريا، لكن لا يرسل setup guidance الصوتي فعليا إلا pose-confirmed/countdown/system messages. | المستخدم يرى التعليمات ولا يسمع نفس توجيه Legacy في مرحلة الإعداد. | إضافة `FeedbackKind.SETUP` أو active-key setup route في `FeedbackRouter`، وربطه بتغير phase و`worstJointGuidance` مع cooldown من `SetupValidationConfig.voiceCooldownMs`. |
| P1 | Setup skeleton guidance | Legacy يرسل `updateSetupGuidance` إلى `SkeletonOverlayView` لتلوين/إبراز مفاصل setup بجانب الصفوف النصية. | KMP يعرض صفوف joint guidance في panel، لكن لا يظهر مسار setup-specific skeleton arrows/highlights بنفس ثراء Legacy. | فرق UI واضح أثناء “ظبط الوضعية” حتى لو النص متاح. | توسيع `SkeletonOverlayMapper`/overlay state ليستقبل setup joint rows واتجاه `RAISE/LOWER` ويعرض highlight خفيف غير مزعج. |
| P1 | Training session polish / overlay | Legacy `SkeletonOverlayView` يجمع state infos، position errors، setup scene-check mode، setup guidance، front-camera mirroring، bilateral flip، any-side dimming، وFILL_CENTER alignment في مكان واحد. | KMP عنده `MovitSkeletonOverlay`, `skeletonLandmarkProjector`, `romIndicators`, `VignetteEffect`، و`mirrorPreview` من frame، وهذا أساس جيد. لكن لا تظهر كل طبقات Legacy polish: setup scene-check overlay، setup arrows/highlights، position-error overlay الغني، any-side dimming، واختبار visual alignment بعد flip/resize. | قد يشعر المستخدم أن الجلسة أقل “مصقولة” حتى لو العد يعمل: skeleton أقل شرحا، ومحاذاة أو mirror bug صغير يغير الثقة في التصحيح. | تعريف overlay parity contract: live joint colors، setup highlights، position-error marks، any-side dimming، bilateral side hint، FILL_CENTER projection، mirror after flip. ثم إضافة screenshot/mapper tests على Android وCompose. |
| P1 | Flip camera polish | Legacy `CameraTrainingInputController.applySwitchCameraFromSettings` يعيد binding الكاميرا، يحدّث overlay front-camera state، ويعمل reset لـ landmark smoother و`elbowAngleEstimator`. | KMP يبدل `useFrontCamera` في route، يعيد `CameraFrameSource.start(...)`، يعطل زر flip أثناء `isCameraSwitching`، ويحدث overlay mirror من أول frame جديد. لا يظهر reset صريح للـ smoothing/estimators عند flip، ولا اختبار يثبت أن setup gate/countdown/training لا يتلوثان بإطارات العدسة السابقة. | بعد flip ممكن يظهر frame/angle stale أو عدم محاذاة مؤقتة، وقد يتأثر setup gate أو العد إذا لم تُصفّر حالة smoothing/side/mirror في لحظة التبديل. | عند `onCameraSwitchStarted`: تصفير landmarks/joint visuals أو إظهار loading قصير، reset detector smoothing/elbow estimator عبر boundary، وتجميد setup/countdown حتى أول frame من العدسة الجديدة. إضافة tests/smoke: flip في setup، في countdown، وفي training بدون كسر العد أو overlay. |
| P1 | RepIncomplete feedback | Legacy `PhaseStateMachine.onRepIncomplete` موصول إلى `FeedbackEvent.RepIncomplete` ثم `FeedbackManager` برسائل مثل العمق/الرجوع/السرعة. | ~~KMP `PhaseStateMachine` ما زال يملك callback، لكن لم يظهر أنه موصول من `MovitTrainingEngine` إلى `TrainingSessionViewModel`/`FeedbackRouter`.~~ **مُنفَّذ §35.1** | المستخدم قد لا يحصل على سبب “العدة لم تُحتسب” بنفس الوضوح. | ربط callback إلى feedback signal common، مع dedupe/cooldown واختبارات للأسباب الأربعة. |
| P1 | Frame evidence / replay | Legacy `FrameCaptureManager` يحفظ danger/peak/error/best/hold مع metadata angles/errorDetails، ويجمع replay frames لكل rep حتى 16 frame. | KMP يحفظ still captures أساسية (`PEAK_FRAME`, `ERROR_FRAME`, `DANGER`, `HOLD_SAMPLE`) لكن capture model لا يحمل angles/errorDetails كاملة، وreplay burst خارج النطاق، وiOS snapshot NoOp. | تقارير KMP أقل إقناعا بصريا ولا تستطيع إعادة أفضل/أسوأ عدة كحركة قصيرة. | توسيع `MovitPeakFrameCapture` metadata، إضافة replay sampler platform-aware، وربط evidence بالتقرير الجديد. |
| P1 | Report UI local mapping | Legacy report screens تعتمد على مادة التقرير الغنية. | `MovitSessionReportUiMapper.mapPostTraining` يملأ joint analysis/tips/rep compare بقوائم فارغة للتقارير المحلية. | حتى لو تم حفظ التقرير، واجهة التقارير لا تعرض نفس التحليل بعد الجلسة. | بعد توسيع التقرير، تحديث mapper ليقرأ `errorAnalysis`, `repTimeline`, `bestReps`, `worstRep`, `improvementTips`, `holdSummary`. |
| P1 | iOS live training readiness | Legacy Android-only. | KMP يملك preview/camera boundaries على iOS؛ **بعد §35.x:** جسر Swift↔Kotlin + `IosTrainingFrameSnapshotPort` هيكليان؛ landmarks حية ما زالت تحتاج MediaPipe Pod + Mac smoke. | دعم iOS الحالي بنيوي وليس تجربة تدريب فعلية مكافئة حتى §35.x.3. | على Mac: CocoaPods + `.task` bundle + smoke §35.x.3؛ smoothing iOS اختياري لاحقاً. |
| P2 | Pose variant index | Legacy يمرر `poseVariantIndex` في setup/report وبعض المسارات. | ~~KMP engine يدعم parameter، لكن `TrainingSessionViewModel` يبني `SetupReadinessGate` و`MovitTrainingEngine` بـ `poseVariantIndex = 0` فقط.~~ **مُنفَّذ §35.2** | تمارين لها أكثر من pose variant قد تعمل بvariant خاطئ، فتختلف setup/count/report. | تمرير `poseVariantIndex` من route/session context إلى VM/engine/setup/report، وتسجيله ضمن session summary. |
| P2 | Throughput/performance | Legacy كان أعلى تقريبا في throughput ودقة التحليل. | KMP ثبت على `320×240` و`targetFps=10` وفعليا ~7fps في smoke واحد لحل GC/stall. | القرار مقبول مؤقتا للاستقرار، لكنه قد يغير حساسية العد والـ feedback مقارنة بـ MO. | ✅ بنية `TrainingThroughputProfiles` + flag `movit.training.throughput.profile` — انظر §35.2؛ الرفع التدريجي بعد قياسات `TrainingPipeline`. |
| P2 | Feedback/UI event model | Legacy event stream موحد `FeedbackEvent` غني، و`TrainingFeedbackBinder` يربط vignette/captures/audio معا. | KMP callbacks و`FeedbackRouter` أنظف بنيويا، لكن بعض الأحداث غير مكتملة الربط أو موزعة بين VM/coordinator. | اختلافات ملموسة في توقيت الرسائل، أولوية الصوت، والتحفيز/التحذير. | ✅ §35.4 — `TrainingFeedbackEventRouter` + ربط rep/target/hold/countdown/visibility vignette؛ setup voice وRepIncomplete audio في §35.1/§35.2. |

### 34.3 نقاط ليست regressions أو أصبحت أفضل في KMP

| المجال | الحكم |
|---|---|
| Visual setup panel | الأقسام القديمة التي تقول إن KMP لا يعرض axis statuses أو `referenceImageUrl` أو joint rows أصبحت قديمة. الكود الحالي يعرض chips، image، camera tip، وصفوف مفاصل. المتبقي هو صوت setup وskeleton guidance وsetup gate semantics وpolish overlay. |
| HoldTimer | KMP قريب من Legacy في state machine: `IDLE`, `HOLDING`, `GRACE_PERIOD`, `COMPLETED`, `FAILED`. لم يظهر regression مباشر في timer نفسه؛ الفروقات المحتملة تأتي من setup/visibility/feedback/report. |
| Angle assembly على Android | KMP common `PoseFrameAssembler` يستخدم worldLandmarks عندما تتوفر ويحتوي `ElbowAngleEstimator` ported من Legacy، وAndroid module يربط `AndroidPoseRefiner`. هذا ليس فجوة رئيسية الآن، لكن iOS لا ينتج landmarks بعد. |
| Stability fixes | `Channel.CONFLATED`, inference in-flight guard, camera shutdown after completion، و`TrainingPipelineDiagnostics` تحسينات حقيقية ولا يجب إزالتها أثناء استعادة parity. |
| Data/offline architecture | KMP أفضل من Legacy في حدود write hooks/outbox/cache، بشرط أن لا تُفقر مادة التقرير والـ summary. |

### 34.4 ترتيب التنفيذ المقترح

1. [x] إصلاح `completionTargetReps` وتمارين bilateral `AFTER_ALL_REPS` أولا، لأنه correctness مباشر في العد. → §35.x P0 Bilateral
2. [x] توسيع `RepResult`/`ExerciseWorkoutSummary` ليحملا `positionErrors`, `stateBreakdown`, `commonErrors`, `repDetails`, weight. → §35.1 Position snapshots · §35.x P0 Count metrics
3. [x] بناء report generator KMP كامل بدلا من JSON فارغ، ثم تحديث report UI mapper. → §35.x P0 Report richness · §35.2 Report UI (نواة؛ `setSummaries`/`performanceMetrics` مؤجَّلة)
4. [x] استعادة setup gate parity: any-side readiness، نسبة حضور المفاصل، countdown rough validation، وتجميد countdown الصحيح. → §35.1 Setup any-side · §35.3 Countdown rough
5. [x] تلميع جلسة التدريب: overlay parity، setup skeleton guidance، flip camera reset/alignment. → §35.11 Overlay · §35.3 Setup skeleton · §35.2 Flip camera
6. [x] ربط setup audio cooldown و`RepIncomplete` وباقي feedback event matrix. → §35.2 Setup voice · §35.1 RepIncomplete · §35.4 Feedback matrix
7. [~] استكمال evidence/replay metadata وiOS snapshot. → §35.x Frame evidence (Android ✅) · §35.x iOS (بنيوي فقط)
8. [x] تمرير `poseVariantIndex` من navigation/session context. → §35.2 Pose variant
9. [~] بعد correctness، رفع الأداء تدريجيا بقياسات `TrainingPipeline` وليس بالتخمين. → §35.2 Throughput flags (افتراضي `stable` لم يتغيّر)

> `[x]` مُنفَّذ · `[~]` جزئي/مؤجَّل تشغيلياً · `[ ]` لم يُغلق

### 34.5 اختبارات قبول مطلوبة

| الاختبار | المطلوب |
|---|---|
| Bilateral after-all-reps | تمرين target 12 لكل جانب لا يكتمل قبل 24 total reps، ويظل side switching عند 12. |
| Any-side setup | تمرين any-side يؤكد setup عند نجاح أحد الجانبين المسموحين، ولا يؤكد setup بمجرد مفصل واحد غير كاف، ولا يفشل بسبب غياب الجانب الآخر إذا كان Legacy يقبله. |
| Countdown rough pose | انحراف `±10°` أثناء العد التنازلي لا يلغي countdown إذا كانت scene valid و60% من primary joints موجودة، ولا يستمر countdown تحت هذه النسبة. |
| Setup voice | phase guidance وworst joint guidance لا يتكرران spam، ويعملان عبر audio cache/TTS fallback. |
| Overlay polish | live skeleton يحافظ على محاذاة FILL_CENTER، يعرض setup highlights، position-error marks، any-side dimming، وbilateral side hint بنفس intent الخاص بـ Legacy. |
| Flip camera | flip في setup/countdown/training لا يترك landmarks قديمة، لا يكسر mirror، لا يغير العد، ويستأنف setup gate بعد أول frame من العدسة الجديدة. |
| Rep incomplete | أسباب `NO_TARGET_DEPTH`, `NO_FULL_RETURN`, `TOO_FAST`, `TOO_SLOW` تصل إلى UI/audio. |
| Report parity | تقرير KMP يحتوي best/worst/error/timeline/tips/hold/evidence عندما تحتوي session على نفس المادة. |
| iOS smoke | camera preview + pose landmarks + setup progress + rep count + report evidence أو fallback واضح. |

---

## 35) تنفيذ مطابقة §34 — ملخص التكامل

**التاريخ:** 2026-06-14 · **التحقق الآلي:** `assembleDebug` + `training-engine` / `feature:training` / `pose-capture` unit tests — **BUILD SUCCESSFUL** (بعد دمج إصلاحات التجميع والاختبارات أدناه).

### 35.0 جدول §34.2 — الحالة بعد الدمج المتوازي

| # | الأولوية | المجال | الحالة | قسم الوكيل / التفاصيل |
|---|---|---|---|---|
| 1 | P0 | Count Logic / bilateral `AFTER_ALL_REPS` | **done** | [§35.x P0 Bilateral](#35x-p0-bilateral-completiontargetreps-342-صف-1) |
| 2 | P0 | Report richness / post-session analysis | **done** (نواة) | [§35.x P0 Report richness](#35x-p0-report-richness-342-صف-2) — `setSummaries` / `performanceMetrics` / `quickInsight` مؤجَّلة |
| 3 | P0 | Count Metrics / per-rep summary | **done** | [§35.x P0 Count metrics](#35x-p0-count-metrics-summary-342-صف-3) |
| 4 | P1 | Position check details في العدة | **done** | [§35.1 Position snapshots](#351-p1--position-error-snapshots-في-represult-342-صف-4) |
| 5 | P1 | Setup Pose any-side | **done** | [§35.1 Setup any-side](#351-p1-setup-any-side-parity) |
| 6 | P1 | Countdown rough validation | **done** | [§35.3 Countdown rough](#353-p1-countdown-rough-validation-342-صف-6) |
| 7 | P1 | Setup feedback الصوتي | **done** | [§35.2 Setup voice](#352-p1-setup-voice-feedback) |
| 8 | P1 | Setup skeleton guidance | **done** | [§35.3 Setup skeleton](#353-p1-setup-skeleton-guidance-342-صف-8) |
| 9 | P1 | Training session polish / overlay | **done** (نواة) | [§35.11 Overlay polish](#3511-p1--training-session-overlay-polish-342-row-9-2026-06-14) — flow/glow مؤجَّل |
| 10 | P1 | Flip camera polish | **done** | [§35.2 Flip camera](#352-p1-flip-camera-polish) |
| 11 | P1 | RepIncomplete feedback | **done** | [§35.1 RepIncomplete](#351-p1-repincomplete-feedback) |
| 12 | P1 | Frame evidence / replay | **done** (بنيوي) | [§35.x Frame evidence](#35x-p1-frame-evidence--replay-342-صف-12) — Android ✅؛ iOS JPEG/replay عبر Swift bridge عند جاهزية MediaPipe |
| 13 | P1 | Report UI local mapping | **done** | [§35.2 Report UI](#352-p1-report-ui-local-mapping) |
| 14 | P1 | iOS live training readiness | **done** (بنيوي) | [§35.x iOS](#35x-p1--ios-live-training-readiness-structural-wiring-2026-06-14) — Session camera/flip/bridge موصولة؛ يحتاج Mac + MediaPipe Pod smoke |
| 15 | P2 | `poseVariantIndex` | **done** | [§35.2 Pose variant](#352-p2-pose-variant-index) |
| 16 | P2 | Throughput/performance | **partial** | [§35.2 Throughput flags](#352-p2--throughputperformance-flags-342-صف-16) — flags جاهزة؛ الإنتاج `stable` |
| 17 | P2 | Feedback/UI event model | **done** | [§35.4 Feedback matrix](#354-p2-feedbackui-event-model-parity-342-صف-17) |

### 35.0.1 §34.5 اختبارات القبول — بعد الدمج

| الاختبار | حالة الوحدة | تحقق جهاز |
|---|---|---|
| Bilateral after-all-reps | ✅ `BilateralCompletionTargetRepsTest` | ⏳ smoke bilateral 12+12 |
| Any-side setup | ✅ `StartPoseGateTest` / `SetupReadinessGateTest` | ⏳ |
| Countdown rough pose | ✅ `SetupReadinessGateTest` + `StartPoseGateTest` | ⏳ |
| Setup voice | ✅ `SetupVoiceGuidanceGateTest` / `FeedbackRouterTest` | ⏳ صوت فعلي + cache |
| Overlay polish | ✅ `SkeletonOverlayMapperTest` / `DisplayLandmarkTransformTest` | ⏳ محاذاة FILL_CENTER بعد flip |
| Flip camera | ✅ `LensSwitchFrameGateTest` / `TrainingCameraSwitchPolicyTest` | ⏳ setup/countdown/training |
| Rep incomplete | ✅ `RepIncompleteFeedbackTest` | ⏳ |
| Report parity | ✅ `MovitPostTrainingReportBuilderTest` / `MovitSessionReportUiMapperTest` | ⏳ جلسة حقيقية + captures |
| iOS smoke | — | ❌ محجوب (Mac + Pod + model) |

### 35.0.2 متبقٍ لتحقق الجهاز فقط (لا يُغلق بالوحدة)

- جلسة bilateral كاملة 24 rep مع تبديل جانب عند 12 دون إنهاء مبكر.
- Setup voice على جهاز (TTS fallback + clips من cold bundle).
- Flip كاميرا في setup / countdown / training: mirror، عدم تلويث العد، استئناف gate بعد أول إطار.
- تقرير ما بعد الجلسة مع peak frames وreplay clips مرئية في UI.
- iOS: preview + landmarks حية + تقرير (أو fallback واضح) بعد ربط MediaPipe على Mac.
- رفع throughput فوق `stable` على 3+ أجهزة عبر `TrainingPipeline` (مراحل rollout في §35.2 Throughput).

### 35.0.3 قرارات معمارية مُوحَّدة (بعد الدمج)

| القرار | المبرر |
|---|---|
| `completionTargetReps` منفصل عن per-side `targetReps` | يطابق MO؛ `BilateralController` يبقى per-side للتبديل فقط. |
| `RepPositionErrorSnapshot` بجانب `positionErrorCheckIds` | لا استبدال للـ IDs؛ مادة تقرير كاملة في common. |
| `MovitPostTrainingReportBuilderV2` + `MovitPostTrainingReportEnrichment` | تحليل common؛ `MovitPostTrainingReportBuilder` يدمج V2 عند وجود `repDetails`. |
| Setup صارم vs countdown متسامح | `isSetupPoseConfirmed` ≠ `isCountdownPoseValid` / `isStartPoseRoughlyValid`. |
| `TrainingFeedbackEventRouter` + `FeedbackRouter` | مصفوفة أحداث موحّدة؛ setup/rep-incomplete مسارات فرعية مع dedupe خاص. |
| `SkeletonOverlayParityState` | عقد overlay واحد لـ setup/training/position-errors/any-side/bilateral. |
| `LensSwitchFrameGate` + reset tracking على flip | منع إطارات stale بعد تبديل العدسة. |
| Throughput `stable` افتراضي + profiles في Gradle | استقرار §29–§32 أولاً؛ رفع تدريجي بقياس `TrainingPipeline`. |
| iOS bridge registry + no-pose صادق | لا landmarks وهمية؛ snapshot/replay يتبعان `bridge.isAvailable`. |

### 35.0.4 إصلاحات التكامل (هذه الجلسة)

| المشكلة | الإصلاح |
|---|---|
| تعارض imports / `CountingMethod` في report enrichment | `com.movit.core.training.engine.CountingMethod` |
| `TrainingFeedbackEventRouter` / `VignetteCue` / `TrainingSystemMessagePort` | أسماء enum + constructor `messages=` في الاختبارات |
| `FeedbackRouterTest` streak عند rep#3 | بناء streak عبر reps 1–2 قبل rep 3 |
| `RepIncompleteFeedback` + active slot | `INTERRUPT` لأربعة أسباب force-audible |
| `MovitRepReplaySamplerTest` eviction | توقعات تطابق evict أعلى rep قديم (11) مع الإبقاء على 12 |
| `SkeletonOverlayMapperTest` landmark indices | 26/28 لـ `right_knee`/`right_ankle` (MediaPipe) |
| `ExerciseWorkoutSummaryBuilderTest` | `RepCounter(minRepIntervalMs = …)` |

```bash
cd android-poc
./gradlew :app:assembleDebug \
  :core:training-engine:testDebugUnitTest \
  :feature:training:testDebugUnitTest \
  :core:pose-capture:testDebugUnitTest
```

**النتيجة:** BUILD SUCCESSFUL (2026-06-14).

> التفاصيل التنفيذية لكل وكيل متوازٍ تبقى في الأقسام الفرعية أدناه (§35.x / §35.1–§35.11).

---

## 35.x P1 — iOS live training readiness (structural wiring, 2026-06-14)

> تنفيذ صف §34.2 row 14 (P1). الهدف: جاهزية بنيوية لربط MediaPipe-iOS مع صدق حول ما يعمل اليوم مقابل ما يحتاج Mac + جهاز.

### 35.x.1 ما وُصِل في الكود

| المكوّن | الحالة | الملاحظات |
|---|---|---|
| `IosPoseLandmarkerBridge` (Kotlin interface) | ✅ | `core/pose-capture/iosMain` — عقد `warmUp` / `detectAsync` / `takeSnapshotJpeg` / `bindResultHandler`. |
| `IosPoseLandmarkerBridgeRegistry` + `installIosPoseLandmarkerBridge` | ✅ | تسجيل من Swift قبل `MainViewController`. |
| `IosPoseDetector` | ✅ | يفوّض للجسر عند `READY`؛ `NOT_INSTALLED` / `INSTALLED_UNAVAILABLE` → `onNoPoseDetected()` بصدق (لا landmarks وهمية). |
| `PoseLandmarkFlatCodec` | ✅ | ترميز 33×5 floats بين Swift وKotlin؛ اختبار JVM `PoseLandmarkFlatCodecTest`. |
| `MovitPoseLandmarkerBridge.swift` | 🔶 هيكلي | يُجمَّع بدون CocoaPod (`#if canImport(MediaPipeTasksVision)`). `isAvailable == false` حتى Pod + `pose_landmarker_full.task`. |
| `iOSApp.swift` | ✅ | يستدعي `IosPoseLandmarkerBridgeInstallKt.installIosPoseLandmarkerBridge` في `init`. |
| `IosTrainingFrameSnapshotPort` | ✅ | يحفظ JPEG عند `bridge.isAvailable`؛ وإلا `NoOpTrainingFrameSnapshotPort` (metadata-only في coordinator). |
| `TrainingFrameSnapshotPortBinding.ios` | ✅ | يربط `IosTrainingFrameSnapshotPort` عند جسر جاهز. |
| `IosCameraFrameSource` | ✅ | يمرّر `worldLandmarks` + أبعاد التحليل إلى `PoseFrameAssembler`. |
| `iosApp/README.md` | ✅ | خطوات Pod + model bundle موثّقة. |

### 35.x.2 ما بقي محجوباً (يتطلب Mac)

| البند | السبب |
|---|---|
| تجميع `compileKotlinIosSimulatorArm64` على Windows (هذه الجلسة) | `kotlin-native-prebuilt-windows` حُجب `callbacks.dll` بسياسة Application Control على الجهاز — ليس خطأ Kotlin في الكود. CI على `macos-15` هو المرجع. |
| `xcodebuild` + Swift compile مع MediaPipe | CocoaPod `MediaPipeTasksVision` غير مضاف في `project.yml` بعد؛ يحتاج Mac + `pod install`. |
| landmarks حية على Simulator/Device | يحتاج Pod + نموذج `.task` في bundle + تحقق على جهاز iOS فعلي (كاميرا + GPU/CPU inference). |
| JPEG evidence في التقرير على iOS | `IosTrainingFrameSnapshotPort` جاهز؛ يعمل فقط بعد `takeSnapshotJpeg` من جسر MediaPipe حيّ. |
| Landmark smoothing iOS | Android يستخدم `LandmarkSmoother` (One Euro)؛ iOS لم يُنقل بعد — اختياري بعد ثبات inference. |

### 35.x.3 مسار التحقق على Mac (قبول §34.5 iOS smoke)

1. `brew install xcodegen` · `cd android-poc/iosApp && xcodegen generate`
2. أضف CocoaPods: `pod 'MediaPipeTasksVision'` · انسخ `pose_landmarker_full.task` إلى `iosApp/iosApp/`
3. `./gradlew :feature:shell:embedAndSignAppleFrameworkForXcode` · `xcodebuild` simulator
4. شغّل تدريباً حياً: preview + landmarks + setup progress + عدّ + تقرير (evidence أو fallback metadata واضح في UI)

### 35.x.4 حكم الصف §34.2

| قبل | بعد هذا التنفيذ |
|---|---|
| `IosPoseDetector` stub صامت (no-pose دائماً بدون مسار جسر) | مسار جسر Swift↔Kotlin موصول؛ no-pose **بصدق** حتى MediaPipe جاهز |
| `TrainingFrameSnapshotPortBinding.ios` = NoOp دائماً | NoOp عند غياب جسر؛ `IosTrainingFrameSnapshotPort` عند `bridge.isAvailable` |
| لا Swift bridge في repo | `MovitPoseLandmarkerBridge.swift` + تسجيل في `iOSApp` |

**لم يُعلَن** iOS live training مكافئاً لـ Android — الجاهزية **بنيوية** حتى تحقق Mac smoke أعلاه.

---

## 35) تنفيذ فجوات §34.2 (P0/P1)

> **المرجع:** §34.2 فروقات مؤكدة وخطة المعالجة. كل بند فرعي يُغلق هنا عند اكتمال التنفيذ والاختبارات.

### 35.1 P1 — Position error snapshots في `RepResult` (§34.2 صف 4)

> **الحالة:** ✅ مُنفَّذ.

| المكوّن | التغيير |
|---|---|
| `RepPositionErrorSnapshot` | DTO common قابل للتسلسل: `checkId`, `type`, `severity`, `message`, `actualValue`, `threshold`, `landmark1`, `landmark2`. |
| `PositionError.toRepSnapshot()` | تحويل من مسار `PositionValidator` إلى snapshot دائم داخل العدة. |
| `RepResult` | حقل `positionErrors: List<RepPositionErrorSnapshot>` بجانب `positionErrorCheckIds` (إضافي، ليس بديلاً). `getTotalErrorCount()` للتقرير. |
| `RepCounter` | `addPositionError(PositionError)` يخزّن snapshot كامل؛ `addPositionError(String)` للاختبارات فقط. `getMostCommonPositionErrors()` لتجميع التقرير. |
| `MovitTrainingEngine` | `pv.errors.forEach { repCounter.addPositionError(it) }` بدل تمرير `checkId` فقط. |
| `ExerciseWorkoutSummary` | `repDetails` يحمل `RepResult` كاملة — التقرير يقرأ `positionErrors` و`positionErrorRepCount()`. |
| `MovitPostTrainingReportBuilderV2` | `worstRep` / `repTimeline` / ترتيب best reps يستخدمون `positionErrors` (رسالة، landmarks، عدّ) بدل `positionErrorCheckIds` فقط. |

**اختبارات:** `RepCounterTest` (snapshot كامل، تجميع common position errors)، `ExerciseWorkoutSummaryPositionErrorsTest`.

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest
```

**ما بقي:** توسيع `MovitPostTrainingReportBuilder` / `ReportGenerator` V2 لاستخدام `positionErrors` في `errorAnalysis` و`worstRep` (§34.2 صف 2 — P0 report richness).

---

## 35) سجل إغلاق فجوات P1 (2026-06-14)

### 35.1 P1 Setup any-side parity

| البند | الحالة |
|---|---|
| **المصدر Legacy** | `PoseSetupGuide.allPrimaryJointsPresent` — يبني `startPoseReadyByJoint` من المفاصل ذات `GuidanceLevel.GREEN`، ثم يتحقق من كل primary مع paired any-side semantics. |
| **التنفيذ KMP** | `StartPosePresence` (common) + `StartPoseGate.isInStartPose` + `SetupReadinessGate` rolling window. |
| **المنطق المنقول** | لكل primary: `two_sides` يشترط زاوية حاضرة داخل `startPose`؛ زوج `any_side` صريح أو زوج bilateral على تمرين any-side يكفي نجاح جانب واحد؛ غياب الزاوية = غير حاضر (لا يُتخطى بـ `continue`). |
| **الملفات** | `StartPosePresence.kt`, `StartPoseGate.kt`, `SetupReadinessGate.kt` |
| **الاختبارات** | `StartPoseGateTest`: bilateral يشترط الركبتين؛ any-side يقبل كوعاً واحداً جاهزاً؛ يرفض زاوية واحدة خارج النطاق. `SetupReadinessGateTest`: `isCountdownPoseValid` بنفس semantics عبر البوابة المدمجة. |
| **متبقٍ (P1 منفصل)** | ~~countdown rough validation (`±10°` + 60% presence)~~ — نُفِّذ في §35.3. |

**حكم القبول (§34.5 Any-side setup):** تمرين any-side يؤكد عند نجاح جانب مسموح واحد؛ لا يؤكد بمفصل واحد غير كافٍ على تمارين bilateral؛ لا يفشل لغياب الجانب الآخر عندما يقبله Legacy.

---

## 35) تنفيذ P1 — مصفوفة feedback

### 35.2 P1 Setup voice feedback

**الحالة:** مُنفَّذ (2026-06-14).

**المشكلة (§34.2 صف 7):** KMP يعرض `actionMessage` و`jointRows` بصرياً لكن لا ينطق phase guidance وworst-joint guidance مثل Legacy (`speakSetupPhaseGuidance` / `speakSetupGuidance`).

**المرجع Legacy:** `SetupCountdownBinder.updateSetupGuidanceUI` → `FeedbackManager.speakSetupPhaseGuidance` / `speakSetupGuidance` مع `FeedbackKind.SETUP`, `activeKey = setup`, `WAIT_FOR_SLOT`, وcooldown من `PoseSetupGuide` (base `voiceCooldownMs`, 2× لنفس المفصل/المرحلة).

**التنفيذ KMP:**

| الطبقة | الملف | التغيير |
|---|---|---|
| Cooldown gate | `SetupVoiceGuidanceGate.kt` | منطق `shouldSpeakPhaseGuidance` / `shouldSpeakJointGuidance` مع `SetupValidationConfig.voiceCooldownMs`. |
| Signals | `SetupFeedbackSignals.kt` | بناء `FeedbackSignal` لـ phase/joint (`dedupeKey` = `setup_phase:{hash}` / `setup:{jointCode}`). |
| Router | `FeedbackRouter.kt` | `submitSetup()` + `resetSetupFeedback()` (`activeKey = setup`). |
| VM | `TrainingSessionViewModel.kt` | `deliverSetupVoiceFeedback` عند كل frame في `SETUP_POSE`/`RESUME_SETUP`: transition إلى ANGLES، phase messages، worst joint RED. |
| Messages | `PositionMessageResolver.resolveSetupSceneToVisibility()` + `SystemMessageRegistry` | نص الانتقال scene→angles؛ joint messages عبر TTS عند غياب clip. |

**سلوك الصوت:**

| الحدث | الشرط | المصدر |
|---|---|---|
| Scene → angles transition | `phase` أصبح `ANGLES` من مرحلة سابقة | `training_setup_scene_to_visibility` |
| Phase guidance | `REGION`/`POSTURE`/`DIRECTION` + `phaseMessage` | `PositionMessageResolver` |
| Worst joint | `ANGLES` + `worstJointGuidance.level == RED` | `SetupJointGuidanceResolver` |

**Dedupe/cooldown:** طبقتان — `SetupVoiceGuidanceGate` (legacy `PoseSetupGuide`) ثم `FeedbackScheduler` عبر `submitSetup`. عند مغادرة setup: `resetSetupVoiceState()` يصفّر gate و`resetSetupFeedback()`.

**Audio:** `CachedAudioFeedbackPlayer` يشغّل clip من `audioUrl` عند توفره؛ وإلا TTS على `FeedbackSignal.text`.

**اختبارات:** `SetupVoiceGuidanceGateTest`, `SetupFeedbackSignalsTest`, `FeedbackRouterTest` (setup dedupe + `resetSetupFeedback`).

**§34.5 Setup voice:** ✅ مغطى.

### 35.1 P1 RepIncomplete feedback

**الحالة:** مُنفَّذ (2026-06-14).

**المشكلة (§34.2 صف 11):** `PhaseStateMachine.onRepIncomplete` كان موجوداً في KMP لكن غير موصول من `MovitTrainingEngine` إلى `TrainingSessionViewModel`/`FeedbackRouter`، فالمستخدم لا يسمع/يرى سبب عدم احتساب العدة.

**المرجع Legacy:** `TrainingEngine` → `FeedbackEvent.RepIncomplete` → `FeedbackManager.handleRepIncomplete` مع `dedupeKey = rep_incomplete:{reason}`, `cooldownGroup` مطابق، `ERROR` + `REPLACE_LOWER`, `forceAudible = true`.

**التنفيذ KMP:**

| الطبقة | الملف | التغيير |
|---|---|---|
| Engine | `MovitTrainingEngine.kt` | `onRepIncomplete` callback يُمرَّر من `stateMachine.onRepIncomplete`. |
| Mapping | `RepIncompleteFeedback.kt` | تحويل `RepIncompleteReason` → `FeedbackSignal` (`FeedbackKind.REP`, `ERROR`, dedupe/cooldown per reason). |
| VM | `TrainingSessionViewModel.kt` | `wireEngineCallbacks` → `submitRepIncompleteFeedback` → `FeedbackRouter.submit`. |
| رسائل | `cold_offline_bundle.json` / `SystemMessageRegistry` | `training_rep_incomplete_depth`, `training_rep_incomplete_return`, `training_rep_too_fast`, `training_rep_too_slow`. |

**خرائط الأسباب الأربعة:**

| `RepIncompleteReason` | message code | EN (افتراضي) |
|---|---|---|
| `NO_TARGET_DEPTH` | `training_rep_incomplete_depth` | You didn't reach the target. Complete the full range. |
| `NO_FULL_RETURN` | `training_rep_incomplete_return` | Return fully to the start position. |
| `TOO_FAST` | `training_rep_too_fast` | Too fast — slow down. |
| `TOO_SLOW` | `training_rep_too_slow` | Too slow — keep a steady pace. |

**Dedupe/cooldown:** `dedupeKey` و`cooldownGroup` = `rep_incomplete:{reason}`؛ `activeKey = correction`؛ `forceAudible = true`؛ `REPLACE_LOWER` لاستبدال تحذير مفصل أدنى أولوية.

**اختبارات:** `RepIncompleteFeedbackTest` — mapping لكل سبب + تسليم `FeedbackRouter` مع cooldown.

**§34.5 Rep incomplete:** ✅ مغطى.

### 35.2 P2 Pose variant index

**الحالة:** مُنفَّذ (2026-06-14).

**المشكلة (§34.2 صف 15):** `TrainingSessionViewModel` كان يمرّر `poseVariantIndex = 0` ثابتاً إلى `SetupReadinessGate` و`MovitTrainingEngine` ورسائل G2، رغم أن Legacy/MO يمرّر الفهرس من Intent أو workout item أو شاشة الإعداد.

**التنفيذ KMP:**

| الطبقة | الملف | التغيير |
|---|---|---|
| Navigation | `TrainingSessionRouteArgs`, `MovitInnerRoute.TrainingSession`, `TrainingStartAction.KmpLive` | حقل `poseVariantIndex` من prepare/deep-link/workout flow. |
| Library | `WorkoutFlowExerciseUi.variantIndex` → `TrainingFlowItem.Exercise.poseVariantIndex` | تمرير per-exercise variant في الجولات المتعددة. |
| Prepare | `ExercisePrepareViewModel` | `selectedPoseVariantIndex` يُنسخ إلى `KmpLive.poseVariantIndex` عند Start. |
| Resolver | `TrainingPoseVariantResolver` | route index أو flow item، مع clamp على `poseVariants.size`. |
| VM | `TrainingSessionViewModel` | `activePoseVariantIndex` → setup gate، countdown validation، engine، joint messages، feedback random messages. |
| Engine | `MovitTrainingEngine` | `val poseVariantIndex` عام؛ `stop()` يكتب الفهرس في `ExerciseWorkoutSummary`. |
| Writes | `TrainingSessionWriteHooks` / `TrainingMotionSession` | journal يتتبع joints للـ variant النشط. |
| Reports | `ExerciseWorkoutSummary`, `MovitExerciseSessionReport`, `MovitPostTrainingReport` | `poseVariantIndex` مسجّل في summary والتقارير. |

**اختبارات:** `TrainingPoseVariantResolverTest`, `ExerciseWorkoutSummaryBuilderTest`, `WorkoutTrainingLaunchTest.toTrainingFlowItems_mapsVariantIndex`, `TrainingSessionViewModelTest.trainingSessionRouteArgs_carriesPoseVariantIndex`.

**§34.2 Pose variant index:** ✅ مغلق.

### 35.2 P1 Report UI local mapping

**الحالة:** مُنفَّذ (2026-06-14).

**المشكلة (§34.2 صف 13):** `MovitSessionReportUiMapper.mapPostTraining` كان يملأ `joints` و`repCompare` و`tips` بقوائم فارغة للتقارير المحلية من `TrainingSessionReportCache`، حتى لو احتوت الجلسة على تحليل غني.

**التنفيذ KMP:**

| الطبقة | الملف | الدور |
|---|---|---|
| Domain | `MovitPostTrainingReportEnrichment.kt` | أنواع `errorAnalysis`, `repTimeline`, `bestReps`, `worstRep`, `improvementTips`, `holdSummary`, `setSummaries`, `heroFrame`, … — حقول اختيارية على `MovitPostTrainingReport` (تُملأ تدريجياً عبر `MovitPostTrainingReportBuilderV2`). |
| Encode | `PostTrainingReportLegacyJson` | يُصدّر الحقول الغنية عند توفرها بدل `empty`/`null` ثابت. |
| Mapper | `MovitSessionReportEnrichmentMapper.kt` | يحوّل التحليل إلى `ReportJointScoreUi`, `ReportRepCompareUi`, `ReportCoachingTipUi`, `formBySet*`, fatigue من timeline، وhold insight. |
| UI bridge | `MovitSessionReportUiMapper.mapPostTraining` | يستهلك الحقول عند الحضور؛ يبقى `SessionUntracked` فقط للتقارير الناقصة. |
| Strings | `ReportDetailStrings` + `strings.xml` | `report_detail_best_rep`, `report_detail_worst_rep`, `report_detail_hold_achievement`. |

**خرائط الحقول:**

| مصدر التقرير | هدف UI |
|---|---|
| `errorAnalysis` (مجمّع per joint) | `joints` + tips ثانوية من `tip`/`message` |
| `bestReps` + `worstRep` (أو min/max من `repTimeline`) | `repCompare` |
| `repTimeline` (مجمّع per set) أو `setSummaries` | `formBySetValues` / `formBySetLabels` + `fatigueMessage` |
| `improvementTips` | `tips` (مرتبة بـ `priority`) |
| `holdSummary` | `durationLabel`, `formScore`, `overviewInsightMessage` |
| `overallQuality` / `heroFrame` | `formScore` / `heroFramePath` عند الغياب في captures |

**اختبارات:** `MovitSessionReportUiMapperTest` — fixture `post-training-enriched-squat.json` + hold + timeline-only rep compare + sparse fallback.

**§34.5 Report parity (UI):** ✅ mapper جاهز؛ يعتمد على إثراء builder P0 لتعبئة الحقول في الإنتاج.

### 35.2 P1 Flip camera polish

**الحالة:** مُنفَّذ (2026-06-14).

**المشكلة (§34.2 صف 10):** KMP كان يبدّل العدسة عبر `CameraFrameSource.start` و`isCameraSwitching` لكن بدون reset صريح لـ landmark smoothing و`ElbowAngleEstimator`، وبدون تجميد setup/countdown/training حتى أول إطار من العدسة الجديدة — ما يسمح بإطارات/زوايا stale وتلويث العد أو setup gate.

**المرجع Legacy:** `CameraTrainingInputController.applySwitchCameraFromSettings` — `switchCamera` + `landmarkSmoother.reset()` + `elbowAngleEstimator.reset()` + `skeletonOverlay.updateFrontCameraState`.

**التنفيذ KMP:**

| الطبقة | الملف | التغيير |
|---|---|---|
| Gate | `LensSwitchFrameGate.kt` | يحجب إطارات العدسة السابقة حتى `isFrontCamera` يطابق الوجهة الجديدة؛ يُستدعى مع `CameraStartGate.Action.SwitchFacing`. |
| Camera | `CameraXFrameSource.kt` | عند flip: `resetTrackingState` + `PoseFrameAssembler.resetElbowEstimator` + `emitPoseFrame`؛ `onCameraBound` مؤجَّل لأول إطار بعد التبديل (وليس عند bind فقط). |
| Boundary | `PoseDetector.kt` | `resetTrackingState()` — Android يصفّر `LandmarkSmoother`. |
| VM | `TrainingSessionViewModel.kt` + `TrainingCameraSwitchPolicy.kt` | `onCameraSwitchStarted` يمسح landmarks/joint visuals؛ المعالجة مجمّدة أثناء `isCameraSwitching`؛ `onCameraReady` (بعد أول إطار من العدسة الجديدة) يفك التجميد. |
| UI | `TrainingSessionScreen.kt` | overlay تحميل قصير أثناء `isCameraSwitching`. |

**اختبارات:** `LensSwitchFrameGateTest`, `CameraStartGateTest` (switch يحافظ على bind), `TrainingCameraSwitchPolicyTest`.

**§34.5 Flip camera:** ✅ مغطى (وحدة + smoke policy؛ اختبار يدوي على جهاز للـ mirror/عد موصى به).

### 35.3 P1 Setup skeleton guidance (§34.2 صف 8)

**الحالة:** مُنفَّذ (2026-06-14).

**المشكلة:** Legacy يرسل `SkeletonOverlayView.updateSetupGuidance` أثناء `SETUP_POSE` في مرحلة `ANGLES` لتلوين عظام المفاصل وإظهار أسهم `RAISE/LOWER` بجانب الزاوية. KMP كان يعرض `SetupJointGuidanceUi` في اللوحة النصية فقط.

**المرجع Legacy:** `SetupCountdownBinder.updateSetupGuidanceUI` → `drawSetupGuidance`: عظام ملوّنة، دوائر حسب `GREEN/YELLOW/RED`، ملصق `%.0f° ↑/↓`.

**التنفيذ KMP:**

| المكوّن | التغيير |
|---|---|
| `SkeletonOverlayContract.kt` | `SkeletonOverlayParityState`, `SkeletonSetupJointHighlight`, `SkeletonOverlayMode.SETUP_ANGLES` / `SCENE_CHECK` |
| `MovitSkeletonOverlay.drawSetupHighlights` | عظام + دوائر + ملصق زاوية/سهم عبر `TextMeasurer` |
| `SetupJointGuidanceUi` | `direction`, `currentAngle`, `isPrimary` من `JointSetupGuidance` |
| `SkeletonOverlayMapper` | `buildSkeletonOverlayParityState`, `mapSetupHighlights`, `resolveSkeletonOverlayMode` |
| `JointLandmarkMapping.adjacentLandmarkIndices` | جيران العظام (منقول من Legacy) |
| `TrainingSessionViewModel.refreshSkeletonOverlay` | يحدّث `skeletonOverlayParity` كل إطار (setup + training) |
| `TrainingSessionScreen` | `parity = state.skeletonOverlayParity` → `MovitSkeletonOverlay` |

**سلوك الوضع:**

| `SkeletonOverlayMode` | متى | الرسم |
|---|---|---|
| `SCENE_CHECK` | setup `REGION`/`POSTURE`/`DIRECTION` | لا overlay (مثل Legacy `setSceneCheckMode`) |
| `SETUP_ANGLES` | setup `ANGLES` + صفوف joint | highlights ملوّنة + `↑/↓` |
| `PREVIEW` | countdown | هيكل خفيف |
| `TRAINING` | تدريب حي | joint colors + ROM + position errors |

**اختبارات:** `SkeletonOverlayMapperTest` (`parityState_setupAngles_*`, `resolveOverlayMode_*`), `SetupGuidanceMapperTest` (`jointRows` مع `direction`).

**§34.2 صف 8:** ✅ مغلق. المتبقي ضمن «Training session polish» (position-error marks، any-side dimming أثناء training).

---

### 35.2 P2 — Throughput/performance flags (§34.2 صف 16)

> **الحالة:** ✅ مُنفَّذ (2026-06-14). القيم الافتراضية لم تتغيّر — `stable` = `320×240` @ `targetFps=10`.

**المشكلة:** KMP ثبّت دقة/معدل إطارات منخفضاً لاستقرار GC/stall (~7fps فعلي في smoke). Legacy كان أعلى؛ الفرق قد يغيّر حساسية العد والـ feedback. المطلوب: بنية rollout تدريجي دون رفع الإنتاج تلقائياً.

**التنفيذ:**

| المكوّن | الملف | الدور |
|---|---|---|
| Device profiles | `TrainingThroughputProfiles.kt` | `stable` (افتراضي)، `medium` (480×360@15)، `high` (640×480@20)، `legacy` (640×480@30). |
| Camera config | `CameraSourceConfiguration.kt` | `analysisWidth/Height`, `throughputProfileId` — افتراضيات = `stable`. |
| Feature flag | `gradle.properties` → `movit.training.throughput.profile` → `BuildConfig.TRAINING_THROUGHPUT_PROFILE` | Android فقط؛ iOS يبقى `stable` حتى يُربط AVFoundation بنفس العقد. |
| Resolver | `TrainingThroughputFlags.kt` | `resolveTrainingCameraConfiguration(useFrontCamera)` في `TrainingSessionCameraHost`. |
| Pipeline | `CameraXFrameSource.kt` | دقة التحليل من `configuration` بدل ثابت `320×240`. |
| Diagnostics | `TrainingPipelineDiagnostics.kt` | `profile=` في السطر الدوري + milestone عند `setCameraConfig`. |

**مثال لوج بعد الربط:**

```text
milestone | throughput profile=stable target=10fps analysis=320x240 ae=[10,10]
window=2s | cam=7fps(skipThrottle=3 profile=stable target=10 ae=[10,10] analysis=320x240) | pose=7fps(...)
```

**تفعيل profile تجريبي (محلي/QA فقط):**

```properties
# android-poc/gradle.properties
movit.training.throughput.profile=medium
```

ثم إعادة بناء `:feature:training` والتطبيق. لا تُرفع القيمة عن `stable` في CI/release حتى اكتمال مسار القبول أدناه.

**مسار rollout المقترح:**

| المرحلة | Profile | معايير قبول (`TrainingPipeline` على 3+ أجهزة) |
|---|---|---|
| 0 — الإنتاج الحالي | `stable` | `cam≈pose`، `busySkip=0`، `stalls=0`، `drop=0`، لا تجميد UI. |
| 1 — QA داخلي | `medium` | `pose` ≥ 12fps متوسط، `inferMs` < 80، `busySkip` < 5% من `cam`، rep parity smoke على 3 تمارين. |
| 2 — beta محدود | `high` | نفس المرحلة 1 + `stalls=0` لجلسة 12 rep كاملة على جهاز متوسط وآخر ضعيف. |
| 3 — parity lab | `legacy` | مقارنة عد/feedback مع MO-readonly على نفس التمرين؛ لا يُفعَّل عاماً قبل إغلاق P0 count/report. |

**اختبارات:**

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest --tests "*TrainingThroughputProfilesTest*"
./gradlew :core:training-engine:testDebugUnitTest --tests "*TrainingPipelineDiagnosticsTest*"
```

**§34.2 صف 16:** ✅ بنية flags + diagnostics؛ الرفع التدريجي للقيم يبقى قرار تشغيلي بعد قياسات المراحل 1–2.

---

### 35.x P0 Count metrics summary (§34.2 صف 3)

**الحالة:** ✅ مُنفَّذ (2026-06-14).

| الحقل | Legacy MO | KMP بعد |
|---|---|---|
| `stateBreakdown` | `repCounter.getStateBreakdown()` | `Map<JointState, Int>` في `ExerciseWorkoutSummary` |
| `commonErrors` | `repCounter.getMostCommonErrors()` | `Map<String, Int>` (`jointCode:errorType`) |
| `repDetails` | `repCounter.repResults` | `List<RepResult>` |
| `weightKg` / `weightUnit` | يُضاف في `ReportGenerator.generateFromEngine` | builder + `MovitTrainingEngine` session weight + write hooks enrichment |

**الملفات:** `ExerciseWorkoutSummary.kt`, `MovitTrainingEngine.kt`, `MovitPostTrainingReport.kt`, `TrainingSessionWriteHooks.kt`.

**اختبارات:** `ExerciseWorkoutSummaryBuilderTest` (جلسة squat اصطناعية + commonErrors)، `MovitPostTrainingReportBuilderTest.build_prefersSummaryRepDetailsForStateBreakdown`.

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest --tests "com.movit.core.training.session.ExerciseWorkoutSummaryBuilderTest" --tests "com.movit.core.training.report.MovitPostTrainingReportBuilderTest"
```

**متبقٍ:** تمرير `defaultWeightKg` من workout route إلى VM.

---

### 35.x P0 Report richness (§34.2 صف 2)

> **الحالة:** ✅ مُنفَّذ (2026-06-14).

**المشكلة:** `MovitPostTrainingReport` / `PostTrainingReportLegacyJson` كانا يملآن معظم حقول التحليل (`dangerAlerts`, `perfectMoments`, `errorAnalysis`, `repTimeline`, `consistency`, `improvementTips`, `heroFrame`, `overallQuality`, `exerciseConfig`, …) بقيم `empty` / `null` رغم توفر `repDetails` وcaptures بعد §35.x Count metrics.

**التنفيذ KMP:**

| المكوّن | الملف | الدور |
|---|---|---|
| محرّك التحليل | `MovitPostTrainingReportBuilderV2.kt` | منفذ common لمنطق `ReportGenerator`: danger/perfect/best/worst، error groups، timeline، consistency، tips، hold summary، hero frame، overall quality، exercise config snapshot. |
| نماذج التقرير | `MovitPostTrainingReportEnrichment.kt` | توسيع DTOs (timeline، error analysis، tips، hold، overall quality، …). |
| تسجيل | `ReportQualityScoring.kt` | Form/Safety/Control legs لـ `overallQuality` (من `PerformanceMetricsBuilder` / `ReportGenerator`). |
| Builder | `MovitPostTrainingReportBuilder` | عند `summary.repDetails.isNotEmpty()` يستدعي V2 ويملأ `MovitPostTrainingReport`. |
| JSON | `PostTrainingReportLegacyJson` | ترميز الحقول الغنية عند وجود بيانات (لا placeholders فارغة للنواة). |
| Hold | `MovitTrainingEngine.snapshotHoldReportData()` | `holdSummary` لتمارين HOLD. |
| VM | `TrainingSessionViewModel` + `TrainingSessionWriteHooks` | تمرير `holdData` إلى builder عند cache/upload. |

**ما نُقل من Legacy MO:**

| الحقل | الحالة |
|---|---|
| `dangerAlerts`, `perfectMoments`, `bestReps`, `worstRep` | ✅ |
| `errorAnalysis`, `repTimeline`, `consistency`, `improvementTips` | ✅ |
| `holdSummary`, `heroFrame`, `overallQuality`, `exerciseConfig` | ✅ |
| `frameCaptures` (metadata angles/errorDetails) | ✅ عبر `MovitPeakFrameCapture` الموسّع |
| `repReplayClips` | ✅ (sampler موجود؛ ربط write hooks) |
| `setSummaries` | ⏸️ مؤجّل — يحتاج تجميع multi-set من workout flow (مثل `enrichWithSetData` في MO) |
| `performanceMetrics` (`EnhancedPerformanceMetrics`) | ⏸️ مؤجّل — يعتمد على `PerformanceMetricsBuilder.build` كامل (بطاقات Form/Safety/Control) |
| `quickInsight` | ⏸️ مؤجّل — `QuickInsightGenerator` لم يُنقل بعد |

**قرارات:**

- مصدر الحقيقة للتحليل: `ExerciseWorkoutSummary.repDetails` + `peakFrameCaptures` + `WorkoutUpload.executionMetrics` (لا إعادة قراءة journal عند الإمكان).
- `MovitReportErrorAnalysis.state` يبقى `String` لتوافق mapper/UI الحالي؛ بقية الحقول موسّعة بصرياً (frames، ranges، icons).
- بدون `repDetails` يبقى التقرير على summary الأساسي فقط (توافق رجعي).

**اختبارات:**

| اختبار | الملف |
|---|---|
| state breakdown من repDetails | `MovitPostTrainingReportBuilderTest.build_prefersSummaryRepDetailsForStateBreakdown` |
| errorAnalysis / timeline / overallQuality / legacy JSON غير فارغ | `MovitPostTrainingReportBuilderTest.build_populatesRichAnalysisWhenRepDetailsPresent` |
| golden parity للحقول الأساسية | `MovitPostTrainingReportBuilderTest.legacyJson_matchesGoldenParityFields` |

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest --tests "com.movit.core.training.report.MovitPostTrainingReportBuilderTest"
```

**§34.2 صف 2:** ✅ مغطى للنواة التحليلية؛ `performanceMetrics` / `setSummaries` / `quickInsight` في P1 لاحق.

---

### 35.3 P1 Countdown rough validation (§34.2 صف 6)

> **الحالة:** ✅ مُنفَّذ (2026-06-14).

**المشكلة:** KMP `isCountdownPoseValid` كان يستخدم `StartPoseGate.isInStartPose` الصارم (نفس شرط تأكيد SETUP)، فيجمّد العد التنازلي عند انحراف بسيط كان Legacy يقبله عبر `isStartPoseRoughlyValid` (`±10°` على الأقل، ونسبة حضور مفاصل).

**المرجع Legacy:** `TrainingViewModel.isStartPoseRoughlyValid` — tolerance = `closeThreshold.coerceAtLeast(10°)`؛ يتخطى المفاصل غير المرئية؛ أي مفصل مرئي خارج النطاق الموسّع يُلغي العد؛ يشترط كل primary حاضراً؛ و`ceil(tracked × 0.6)` مفاصل مرئية (حد أدنى 2).

**التنفيذ KMP:**

| الطبقة | الملف | التغيير |
|---|---|---|
| Gate | `StartPoseGate.kt` | `isStartPoseRoughlyValid(toleranceDegrees, minJointPresenceRatio, requireAllPrimaryPresent)` — منطق Legacy المنقول. |
| Config | `SetupValidationConfig.kt` | `countdownAngleToleranceDegrees` (افتراضي `max(closeThreshold, 10°)`)، `countdownMinJointPresenceRatio` (0.6)، `countdownRequireAllPrimaryPresent` (true). |
| Setup | `SetupReadinessGate.kt` | `validate()` / `isSetupPoseConfirmed` → صارم؛ `isCountdownPoseValid` → rough فقط. |
| VM | `TrainingSessionViewModel.kt` | أثناء COUNTDOWN: `sceneStillValid` (phase == ANGLES + landmarks ≥ 33) **و** `isCountdownPoseValid`؛ يُرسل `CountdownPoseValid` عند النجاح و`PoseInvalid` عند الفشل. |

**فصل الصارم عن المتسامح:** تأكيد SETUP (`primaryReady` + rolling window) يبقى على `isInStartPose` / `StartPosePresence`؛ حارس العد التنازلي منفصل ولا يعيد استخدام نفس الشرط.

**اختبارات (§34.5 Countdown rough pose):**

| اختبار | الملف |
|---|---|
| انحراف `±10°` يُقبل في countdown ويُرفض في setup صارم | `SetupReadinessGateTest.countdownPoseValid_acceptsTenDegreeDeviationRejectedByStrictSetup` |
| خرق جسيم خارج التسامح | `StartPoseGateTest.isStartPoseRoughlyValid_rejectsGrossViolation` |
| حضور أقل من 60% من المفاصل المتتبعة | `StartPoseGateTest.isStartPoseRoughlyValid_rejectsBelowSixtyPercentJointPresence` |
| bilateral يشترط الركبتين أثناء countdown | `SetupReadinessGateTest.countdownPoseValid_bilateralRequiresBothKnees` |

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest --tests "com.movit.core.training.engine.StartPoseGateTest" --tests "com.movit.core.training.session.SetupReadinessGateTest"
```

**§34.2 صف 6 / §34.5 Countdown rough pose:** ✅ مغطى.

### 35.x P0 Bilateral completionTargetReps (§34.2 صف 1)

> **الحالة:** ✅ مُنفَّذ.

**المشكلة:** Legacy `TrainingEngine` يحسب `completionTargetReps = targetReps * 2` عند `AFTER_ALL_REPS` (أو `switchEvery == targetReps`) ويمرّره إلى `RepCounter` و`ExecutionSafetyGuards`؛ KMP كان يستخدم `targetReps` نفسه لإنهاء الجلسة فيتم إكمال تمرين bilateral بعد الجانب الأول فقط.

**التغييرات:**

| المكوّن | السلوك |
|---|---|
| `BilateralCompletion.kt` | `isAfterAllRepsBilateral()` + `completionTargetReps()` بنفس semantics Legacy MO. |
| `MovitTrainingEngine` | `targetReps` = per-side؛ `completionTargetReps` → `RepCounter`, `SessionOrchestrator`, `metricsSnapshot().targetReps`. |
| `BilateralController` | بدون تغيير — `targetReps` per-side لتبديل الجانب عند 12. |
| `RepCounter` / `SessionOrchestrator` | توثيق أن `targetReps` هو session completion target (قد يكون `perSide * 2`). |

**الملفات:** `bilateral/BilateralCompletion.kt`, `session/MovitTrainingEngine.kt`, `engine/RepCounter.kt`, `session/SessionOrchestrator.kt`, `bilateral/BilateralCompletionTargetRepsTest.kt`.

**قرارات:** فصل per-side target عن completion target في helper مشترك قابل للاختبار؛ عدم تعديل `BilateralController` لأن تبديل الجانب يبقى عند `targetReps` per-side.

**اختبارات:** `BilateralCompletionTargetRepsTest` — 12 reps per side → side switch عند 12، completion عند 24؛ + حالات `switchEvery == target` و`EVERY_REP`.

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest --tests "com.movit.core.training.bilateral.BilateralCompletionTargetRepsTest" --tests "com.movit.core.training.bilateral.BilateralControllerParityTest"
```

**نتيجة التشغيل (2026-06-14):** ✅ `BilateralCompletionTargetRepsTest` + بقية `:core:training-engine:testDebugUnitTest` ضمن BUILD SUCCESSFUL بعد §35.0.4.

### 35.x P1 Frame evidence / replay (§34.2 صف 12)

> **الحالة:** ✅ مُنفَّذ بنيوياً على Android وiOS؛ iOS runtime ينتظر Mac/MediaPipe smoke.

**المشكلة:** Legacy `FrameCaptureManager` يحفظ still captures مع `metadata.angles` و`errorDetails`، ويجمع حتى 16 إطار replay لكل rep كل ~180ms. KMP كان يحفظ مسارات JPEG فقط بدون metadata كاملة، وبدون burst replay؛ و`TrainingFrameSnapshotPortBinding.ios` NoOp.

**التغييرات:**

| المكوّن | السلوك |
|---|---|
| `MovitPeakFrameCapture` | `errorType` + `MovitFrameCaptureMetadata` (`angles`, `hasError`, `errorDetails`). |
| `MovitPeakFrameCaptureManager` | يمرّر الزوايا من `RegisterRequest` إلى metadata عند التسجيل. |
| `MovitRepReplaySampler` | سجل common لـ burst (16 إطار/rep، 10 reps متتبعة، حد أدنى 2 إطار للـ clip) — يطابق حدود Legacy. |
| `TrainingFrameSnapshotPort` | `persistReplaySnapshot` (540px / quality 82 على Android، ونفس الحدود على iOS عند وجود Swift bridge). |
| `TrainingFrameCaptureCoordinator` | يمرّر `JointAngles` من الجلسة؛ يشغّل/يوقف sampler أثناء `TRAINING` (ليس hold). |
| `MovitPostTrainingReport` | `repReplayClips` + ترميز `frameCaptures.metadata` و`repReplayClips` في `PostTrainingReportLegacyJson`. |
| `TrainingSessionViewModel` / `TrainingSessionWriteHooks` | ربط captures + replay clips بمُنشئ التقرير. |

**الملفات:** `report/MovitFrameCaptureMetadata.kt`, `report/MovitRepReplaySampler.kt`, `report/MovitPeakFrameCapture*.kt`, `boundary/TrainingFrameSnapshotPort.kt`, `feature/training/TrainingFrameCaptureCoordinator.kt`, `feature/training/AndroidTrainingFrameSnapshotPort.kt`, `report/MovitPostTrainingReport.kt`, `feature/training/TrainingSessionViewModel.kt`, `feature/training/TrainingSessionWriteHooks.kt`.

**حالة iOS بعد التحقق النهائي (§36):**

| البند | الحالة |
|---|---|
| still captures | `IosTrainingFrameSnapshotPort.persistSnapshot` يحفظ JPEG عند `bridge.isAvailable`؛ وإلا NoOp صريح. |
| burst replay | `IosTrainingFrameSnapshotPort.persistReplaySnapshot` موصول بـ 540px / quality 82. |
| التقرير المحلي | `repReplayClips` تُملأ عند وجود bridge حي؛ بدون MediaPipe تبقى فارغة بوضوح. |
| التحقق | يحتاج Mac/Xcode + Pod + model smoke؛ Windows لا يبني Swift/iOS runtime. |

**اختبارات:**

| اختبار | الملف |
|---|---|
| metadata angles/error على peak/error | `MovitPeakFrameCaptureManagerTest` |
| حدود replay burst وeviction | `MovitRepReplaySamplerTest` |
| legacy JSON metadata + repReplayClips | `MovitPostTrainingReportBuilderTest.legacyJson_encodesPeakFrameCaptures` |
| coordinator angles + sampler | `TrainingFrameCaptureCoordinatorTest` |

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest --tests "com.movit.core.training.report.MovitPeakFrameCaptureManagerTest" --tests "com.movit.core.training.report.MovitRepReplaySamplerTest" --tests "com.movit.core.training.report.MovitPostTrainingReportBuilderTest"
./gradlew :feature:training:testDebugUnitTest --tests "com.movit.feature.training.TrainingFrameCaptureCoordinatorTest"
```

**§34.2 صف 12 / Frame evidence replay:** ✅ مغطى على Android؛ iOS موصول بنيوياً عبر snapshot bridge، واعتماده التشغيلي يحتاج smoke على Mac/iOS.

**متبقٍ (P2):** `ReportFrameEvidenceMapper` / `RepReplayPlayer` في Compose report — عرض burst best/worst في UI التقرير (§34.2 صف Report UI local mapping).

### 35.4 P2 Feedback/UI event model parity (§34.2 صف 17)

> **الحالة:** ✅ مُنفَّذ (2026-06-14).

**المشكلة:** Legacy يستخدم تدفقاً موحداً `FeedbackEvent` + `TrainingFeedbackBinder` يربط audio/vignette/captures. KMP كان يوزّع المسارات بين VM callbacks مباشرة، و`FeedbackKind.REP` / `TARGET` / `HOLD` لم تكن موصولة لعدة أحداث حيوية.

**المرجع Legacy:**

| `FeedbackEvent` | `FeedbackManager` (صوت) | `TrainingFeedbackBinder` (بصري/capture) |
|---|---|---|
| `RepCompleted` | كل 3 عدات + streak motivation | pulse العداد؛ `markAsBestRep` عند صحيحة |
| `RepIncomplete` | 4 أسباب، `forceAudible` | vignette warning + shake |
| `TargetReached` | `training_target_reached` | — |
| `HoldGraceStarted` | `training_hold_stay` | shake |
| `HoldResumed` | `training_hold_resumed` | — |
| `HoldCompleted` | `training_hold_completed` | — |
| `HoldFailed` | `training_hold_failed` | shake + reset timer |
| `JointQuality` | per-frame | danger/error frame capture |
| `PositionCheckFeedback` | per severity | — |
| `VisibilityWarning` | عبر presence | vignette warning |
| Countdown freeze | — | vignette warning (`showCountdownPoseIssue`) |
| Setup guidance | `FeedbackKind.SETUP` | skeleton (P1 منفصل) |

**مصفوفة التكافؤ KMP (بعد الإصلاح):**

| الحدث | Legacy | KMP قبل | KMP بعد | ملاحظات |
|---|---|---|---|---|
| Setup voice | `speakSetupPhaseGuidance` | ❌ | ✅ | **P1 منفصل** — §35.2؛ `SetupFeedbackSignals` + `submitSetup` |
| Countdown tick/go | numeral + Go | ✅ | ✅ | `FeedbackKind.COUNTDOWN` في `wireCountdown` |
| Countdown pose freeze | vignette warning | ❌ | ✅ | `onFrozen`/`onUnfrozen` → `applyVignetteCue` |
| Rep complete audio | كل 3 + streak | ❌ | ✅ | `TrainingFeedbackEventRouter.routeRepCompleted` |
| Rep complete visual | pulse | جزئي | جزئي | pulse UI لاحقاً؛ capture عبر `FrameCaptureCoordinator` |
| Rep incomplete | audio + vignette | ❌ | ✅ | **P1 منفصل** — `RepIncompleteFeedback` + vignette في `submitRepIncompleteFeedback` |
| Target reached | motivation voice | ❌ | ✅ | `routeTargetReached` في `onTargetReached` |
| Hold grace/resume/complete/fail | `FeedbackKind.HOLD` | ❌ | ✅ | `routeHoldFeedback` على انتقالات `HoldState` |
| Hold started | log only | — | — | لا صوت Legacy؛ لا إجراء |
| Joint quality | voice + capture | ✅ | ✅ | `submitJointStateMessage` / `submitJointErrorFeedback` |
| Position check | voice | ✅ | ⚠️ | موصول؛ severity ERROR في VM ما زال WARNING فقط (P1 لاحق) |
| Visibility warning | vignette + voice | صوت فقط | ✅ | `routeVisibilityWarning` vignette + `FeedbackKind.VISIBILITY` |
| Visibility pause | vignette error | ✅ | ✅ | `ShowAutoPaused` + `showVignette` |
| Random motivation | idle fill | ✅ | ✅ | `tryDeliverRandomMessage` |

**التنفيذ:**

| الطبقة | الملف | التغيير |
|---|---|---|
| Router | `TrainingFeedbackEventRouter.kt` | تحويل rep/target/hold/countdown/visibility → `FeedbackSignal` / `VignetteCue` مع نفس dedupe/severity Legacy. |
| VM | `TrainingSessionViewModel.kt` | `wireEngineCallbacks`: rep/target/hold؛ countdown freeze vignette؛ visibility vignette؛ `applyVignetteCue`. |
| Router tests | `TrainingFeedbackEventRouterTest.kt` | interval العد، streak، hold kinds، numeral keys. |
| Integration tests | `FeedbackRouterTest.kt` | تسليم TARGET/HOLD/REP batch عبر `FeedbackRouter`. |

**تداخل واعٍ (لا تكرار):**

| البند | المالك | هذا القسم |
|---|---|---|
| Setup phase/joint voice | §35.2 P1 Setup voice | يُوثَّق فقط في المصفوفة |
| Rep incomplete audio mapping | §35.1 P1 RepIncomplete | أضيف vignette warning فقط هنا |
| Rep incomplete shake animation | UI polish | خارج نطاق P2 |

**اختبارات:**

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest \
  --tests "com.movit.core.training.engine.feedback.TrainingFeedbackEventRouterTest" \
  --tests "com.movit.core.training.engine.feedback.FeedbackRouterTest" \
  --tests "com.movit.core.training.feedback.RepIncompleteFeedbackTest"
```

**§34.2 صف 17 / Feedback event matrix:** ✅ مغطى للمسارات المحددة؛ متبقٍ: rep counter pulse animation، position ERROR severity في vignette، scene warnings (`FeedbackKind.SCENE`).

---

## 35.11 P1 — Training session overlay polish (§34.2 row 9, 2026-06-14)

> تنفيذ عقد parity للـ skeleton overlay مقابل Legacy `SkeletonOverlayView` + ربط `TrainingSessionScreen` / `TrainingSessionViewModel`.

### 35.11.1 عقد الـ overlay (`SkeletonOverlayParityState`)

| الحقل | Legacy | KMP بعد التنفيذ |
|---|---|---|
| `SkeletonOverlayMode` | setup scene-check / setup guidance / training | `SCENE_CHECK` (REGION/POSTURE/DIRECTION)، `SETUP_ANGLES`، `TRAINING`، `PREVIEW` (countdown) |
| `jointVisuals` + `dimmed` | `anySideDimmedJointCodes` من `TrainingEngine` | `EngineMetrics.anySideDimmedJointCodes` ← `JointAngleTracker.skippedJointCodes` |
| `positionErrors` | `drawPositionErrors` (top 2 dashed brackets) | `mapPositionErrorMarks` → رسم ERROR/WARNING/TIP في `MovitSkeletonOverlay` |
| `setupHighlights` | `updateSetupGuidance` (bones + ألوان + ↑/↓) | `mapSetupHighlights` من `setupJointRows` |
| `bilateralSideHint` | flip للـ landmark lookup + side state | شارة "Left/Right side" أثناء `TRAINING` + `isBilateralFlipped` للـ ROM/joint lookup |
| `landmarkProjector` | `FILL_CENTER` + `toScreenX/Y` | `SkeletonLandmarkProjector` (cached `DisplayLandmarkTransform`) |

### 35.11.2 مصفوفة parity

| طبقة Legacy | الحالة | ملاحظات |
|---|---|---|
| FILL_CENTER + front-camera mirrorX | ✅ | §25 + projector مُخزَّن لكل حجم canvas |
| Live joint colors (PERFECT→DANGER + PAD) | ✅ | ألوان على المفاصل والأضلاع؛ glow خفيف عند PERFECT |
| Position-error marks (top 2) | ✅ | خط متقطع + دوائر bracket |
| Any-side dimming | ✅ | `dimmed` يخفّي ROM ويُبهت المفاصل/الأضلاع |
| Bilateral side hint | ✅ | شارة نصية؛ flip للـ landmark lookup في overlay + ROM |
| Setup scene-check blank overlay | ✅ | `SCENE_CHECK` لا يرسم فوق الكاميرا |
| Setup angle highlights | ✅ | bones + تسميات زاوية + اتجاه |
| Arc/line ROM indicators (primary) | 🔶 | موجود مسبقاً؛ يتخطى `dimmed`؛ نطاقات تقريبية حتى I-8 |
| Flow animation / body-scale glow | ⏳ | متعمد تأجيله (تجميل ثانوي) |
| Color lerp anti-flicker | ⏳ | ألوان فورية؛ يمكن إضافة lerp لاحقاً |

### 35.11.3 ملفات رئيسية

- `core/designsystem/.../SkeletonOverlayContract.kt` — عقد parity
- `core/designsystem/.../MovitSkeletonOverlay.kt` — رسم الطبقات
- `feature/training/.../SkeletonOverlayMapper.kt` — `buildSkeletonOverlayParityState`, projector, mappers
- `feature/training/.../TrainingSessionViewModel.kt` — `skeletonOverlayParity` في UiState
- `feature/training/.../TrainingSessionScreen.kt` — تمرير `parity` للـ overlay
- `core/training-engine/.../MovitTrainingEngine.kt` — `lastAnySideDimmedJointCodes` + حقول `EngineMetrics`

### 35.11.4 اختبارات

```bash
cd android-poc
./gradlew :feature:training:testDebugUnitTest \
  --tests "com.movit.feature.training.SkeletonOverlayMapperTest" \
  :core:training-engine:testDebugUnitTest \
  --tests "com.movit.core.training.geometry.DisplayLandmarkTransformTest"
```

| اختبار | يغطي |
|---|---|
| `SkeletonOverlayMapperTest` | FILL_CENTER projector، scene/setup/training modes، dimming، position marks، bilateral hint |
| `DisplayLandmarkTransformTest` | crop/center/mirrorX |

### 35.11.5 متبقٍ (خارج هذا الـ P1)

| البند | المالك |
|---|---|
| Smoke بصري على جهاز (محاذاة + flip) | QA |
| ROM ranges من `JointStateInfo.stateRanges` (I-8) | training-engine feed |
| Flow shimmer + body-scale glow | polish لاحق |
| Screenshot/regression Compose tests | CI اختياري |

---

## 36) تحقق نهائي بعد تنفيذ الخطة (2026-06-14)

**الحكم:** التنفيذ الحالي مقبول بنيوياً لمحرك KMP بعد مراجعة مسارات العد، setup، feedback، overlay، flip camera، التقارير، وframe evidence. تم العثور أثناء التحقق على فجوات تكامل فعلية وتم إصلاحها في نفس الجولة قبل اعتماد النتيجة أدناه.

### 36.1 ما تم التحقق منه في الكود

| المحور | نتيجة المراجعة |
|---|---|
| Count logic / bilateral | `completionTargetReps` يضاعف الهدف في `AFTER_ALL_REPS` أو `switchEvery == targetReps`، مع بقاء `BilateralController` على target per-side. |
| HOLD / metrics / summaries | `RepCounter` و`ExerciseWorkoutSummary` و`MovitPostTrainingReportBuilderV2` ينقلون per-rep details، position errors، state breakdown، hold summary، best/worst، tips، timeline، وquality metrics. |
| Setup gate | `SetupReadinessGate` + `StartPosePresence` يغطيان any-side pairing، rough countdown validation، ومراحل REGION/POSTURE/DIRECTION/ANGLES. |
| Setup feedback | `SetupVoiceGuidanceGate` + `SetupFeedbackSignals` + `FeedbackRouter` موصولة للـ ViewModel مع cooldown/dedupe. |
| Overlay polish | `SkeletonOverlayParityState` موصول من engine metrics إلى `MovitSkeletonOverlay`: modes، dimming، position marks، setup highlights، bilateral side hint، وFILL_CENTER projector. |
| Flip camera | Android يستخدم `LensSwitchFrameGate` ويمسح overlay أثناء switch؛ تم إصلاح no-pose frames كي تحمل اتجاه العدسة ولا تفتح الـ gate بإطار قديم. |
| iOS session camera | `TrainingSessionCameraHost.ios` لم يعد placeholder؛ أصبح يستخدم `IosCameraFrameSource` مع preview، `useFrontCamera`، error/ready callbacks، وrestart عند flip. |
| Frame evidence / replay | Android وiOS snapshot ports يدعمان still captures وreplay burst؛ iOS يعمل عند جاهزية Swift MediaPipe bridge. |
| Throughput | Android يطبق analysis size + target FPS؛ iOS يطبق target FPS throttle بنيوياً، مع بقاء smoke الفعلي على جهاز iOS مطلوباً. |

### 36.2 إصلاحات تمت أثناء التحقق

| الإصلاح | الملفات |
|---|---|
| no-pose callback يحمل `isFrontCamera` على Android حتى لا تُقبل إطارات العدسة القديمة بعد flip. | `MediaPipePoseDetector.kt`, `CameraXFrameSource.kt` |
| توحيد no-pose callback بين Kotlin/Native bridge وSwift bridge. | `IosPoseLandmarkerBridge.kt`, `IosPoseDetector.kt`, `MovitPoseLandmarkerBridge.swift` |
| استبدال placeholder الخاص بجلسة التدريب على iOS بربط كاميرا حقيقي. | `TrainingSessionCameraHost.ios.kt`, `IosCameraFrameSource.kt` |
| إضافة replay snapshot على iOS بنفس حدود Legacy/Android. | `IosTrainingFrameSnapshotPort.kt` |
| تفعيل error/bound callbacks وtarget FPS throttle في iOS camera source. | `IosCameraFrameSource.kt` |

### 36.3 أوامر التحقق

```bash
cd android-poc
./gradlew :core:training-engine:testDebugUnitTest :core:pose-capture:testDebugUnitTest :feature:training:testDebugUnitTest :feature:reports:testDebugUnitTest :feature:library:testDebugUnitTest --no-daemon
```

**النتيجة:** ✅ `BUILD SUCCESSFUL` في 2026-06-14 بعد إصلاحات التحقق.

```bash
git diff --check -- android-poc/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt android-poc/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/CameraXFrameSource.kt android-poc/core/pose-capture/src/iosMain/kotlin/com/movit/core/posecapture/IosPoseDetector.kt android-poc/core/pose-capture/src/iosMain/kotlin/com/movit/core/posecapture/IosCameraFrameSource.kt android-poc/core/pose-capture/src/iosMain/kotlin/com/movit/core/posecapture/IosPoseLandmarkerBridge.kt android-poc/iosApp/iosApp/MovitPoseLandmarkerBridge.swift android-poc/feature/training/src/iosMain/kotlin/com/movit/feature/training/IosTrainingFrameSnapshotPort.kt android-poc/feature/training/src/iosMain/kotlin/com/movit/feature/training/TrainingSessionCameraHost.ios.kt
```

**النتيجة:** ✅ لا توجد أخطاء whitespace؛ ظهرت فقط تحذيرات CRLF الخاصة بـ Windows.

### 36.4 حدود الاعتماد

| البند | الحكم |
|---|---|
| Android/common | معتمد آلياً بالاختبارات أعلاه + مراجعة الكود. |
| iOS Kotlin/Swift runtime | غير معتمد تشغيلياً من Windows؛ يحتاج Mac/Xcode + `MediaPipeTasksVision` Pod + `pose_landmarker_full.task` + smoke كاميرا فعلي. |
| iOS compile attempt | `compileKotlinIosX64` غير موجود في المشروع؛ `compileKotlinIosArm64` لم يكتمل ضمن مهلة Windows، لذلك لا يُستخدم كدليل نجاح أو فشل. |
| Device visual QA | ما زال مطلوباً لجلسة كاملة: setup gate، countdown، flip أثناء setup/training، overlay alignment، bilateral 12+12، وreport captures/replay. |

### 36.5 قرار نهائي

لا توجد حالياً نواقص بنيوية ظاهرة في المحاور التي تسببت في الاختلافات العميقة مع Legacy: feedback، UI overlay، count/HOLD logic، setup pose، count metrics، frame evidence، وflip camera. المتبقي ليس إعادة تصميم للمحرك، بل smoke تشغيلي على أجهزة فعلية، خصوصاً iOS لأن Windows لا يستطيع اعتماد Swift/MediaPipe runtime.

### 36.6 توضيح خاص: Elbow correction / hysteresis

| البند | الحالة |
|---|---|
| `ElbowAngleEstimator` القاعدي | ✅ منقول إلى KMP في `core/training-engine/.../geometry/ElbowAngleEstimator.kt` ومربوط داخل `PoseFrameAssembler.assemble` عند توفر `worldLandmarks`. |
| reset عند تبديل العدسة | ✅ `CameraXFrameSource.prepareForLensSwitch` يستدعي `PoseFrameAssembler.resetElbowEstimator()` حتى لا تنتقل EMA/hold من العدسة القديمة. |
| phase/state hysteresis | ✅ موجود في `PhaseStateMachine`, `JointEvaluator`, و`StabilityPolicy`. |
| MLP/FIT3D elbow classifiers | ⚠️ غير منقولة ككود إلى KMP حالياً: Legacy يحتوي `ElbowCorrectionMlpClassifier`, `ElbowFit3dV2Classifier`, وfeature extractors؛ KMP الحالي يحتوي assets داخل `app/src/main/assets` لكن لا يحتوي classifiers/ports التي تستخدمها. |

**الحكم:** إذا كان المقصود بالمصطلح “Hysteresis” العام أو مصحح `ElbowAngleEstimator` المستخدم في المسار الحي، فهو منقول ومستخدم. إذا كان المقصود نموذج ML/FIT3D الخاص بتصحيح الكوع، فهذا غير منقول وظيفياً ويجب فتحه كبند P1/P2 منفصل قبل اعتبار parity كاملة في elbow ML correction.

