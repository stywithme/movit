# Camera Training Engine — Comprehensive Review Brief
# وثيقة تكليف: المراجعة الشاملة لمحرك أداء التمارين أمام الكاميرا

> **الغرض**: هذه الوثيقة هي مرجع التكليف الكامل لفريق مراجعة (بشري + وكلاء) لفحص محرك التدريب بالكاميرا في `kmp-app` من نواحي: **الأداء والسرعة، صحة المنطق، سلامة الهيكلة والتدفق، تنظيم البيانات وإعادة استخدامها، التكرار، والكود الميت**.
>
> **كيفية الاستخدام**: كل "مسار مراجعة" (Track) أدناه مستقل ويمكن إسناده لوكيل منفصل بالتوازي. كل وكيل يقرأ: (1) قسم الخريطة العامة §2–§4، (2) قسم مساره فقط §6، (3) سجل الشكوك الأولية §7 الخاصة بمساره، (4) مواصفات المخرجات §8. **المراجعة قراءة فقط — لا تعديلات على الكود.**
>
> **تاريخ الإعداد**: 2026-07-09 — بناءً على مراجعة أولية فعلية للكود (كل `file:line` في هذه الوثيقة تم التحقق منه وقت الكتابة).

---

## 1. نطاق المراجعة (Scope)

المحرك يتوزع على 3 وحدات رئيسية داخل `kmp-app/`:

| الوحدة | المسار | الدور | الحجم التقريبي |
|---|---|---|---|
| **pose-capture** | `core/pose-capture/` | الكاميرا + كشف الوضعية (MediaPipe) + تنعيم المعالم — Android (CameraX) و iOS (AVFoundation) | ~45 ملف |
| **training-engine** | `core/training-engine/` | المحرك المشترك (KMP commonMain): الهندسة، التقييم، آلة الأطوار، عدّ التكرارات، التغذية الراجعة، التقارير | ~140 ملف |
| **feature/training** | `feature/training/` | طبقة الجلسة: `TrainingSessionViewModel` + الشاشة + ربط الكاميرا + الكتابة/الرفع | ~60 ملف |

**خارج النطاق**: `feature/train` (شاشة تبويب Train — ليست المحرك)، `feature/training-debug` (أداة تشخيص منفصلة — تدخل النطاق فقط في سؤال OQ-06: هل تُستبعد من إصدارات release؟).

---

## 2. رحلة الإطار من الكاميرا إلى الشاشة (Frame Journey — Android)

هذا هو **المسار الساخن (hot path)** الذي يتكرر 20–30 مرة/ثانية. أي مراجعة أداء تبدأ من هنا:

```
[1] TrainingSessionCameraHost.android.kt
      يربط PreviewView + يسجّل frameListener
      feature/training/src/androidMain/.../TrainingSessionCameraHost.android.kt:85-127
        ↓
[2] CameraXFrameSource.bindUseCases
      Preview (4:3) + ImageAnalysis (KEEP_ONLY_LATEST + دقة مستهدفة من CameraSourceConfiguration)
      core/pose-capture/src/androidMain/.../CameraXFrameSource.kt:236-353
        ↓  (خيط: analysisExecutor — منفّذ أحادي الخيط)
[3] Analyzer: shouldAnalyzeFrame() ← خانق fps يدوي (:407-416)
      → MediaPipePoseDetector.detectAsync(proxy, useFrontCamera)
        ↓
[4] detectAsync: بوابة انشغال inferenceInFlight → imageProxy.toBitmap()
      → rotateBitmapForAnalysis (bitmap جديد لكل إطار!) → تخزين lastFrameBitmap
      → PoseLandmarker.detectAsync (LIVE_STREAM)
      core/pose-capture/src/androidMain/.../MediaPipePoseDetector.kt:153-190
        ↓  (خيط: MediaPipe result callback thread)
[5] onPoseResult (:275-313):
      LandmarkSmoother (One-Euro ×33 معلم ×(normalized+world))
      → PoseRefiner (اختياري) → PoseFrameAssembler.assemble
        (معالم افتراضية 33/34 + زوايا 3D/2D + تصحيح الكوع)
      → CameraXFrameSource.emitPoseFrame → LensSwitchFrameGate → frameListener
        ↓  (نفس خيط MediaPipe callback)
[6] Host onFrame → TrainingSessionViewModel.onPoseFrame
      → poseFrameChannel (Channel.CONFLATED — يُسقط الأقدم)
      feature/training/src/commonMain/.../TrainingSessionViewModel.kt:257-261
        ↓  (خيط: poseFrameWorker على Dispatchers.Default)
[7] processPoseFrameOnWorker (:272-360):
      applyPoseLandmarksToUi (تحديث state لكل إطار)
      ├─ TRAINING → engine.processFrame(frame) مباشرة + elapsed + overlay + رسائل عشوائية
      └─ setup/countdown → SupervisorSignal.PoseFrame → قد يُصدر SupervisorAction.ProcessFrame
           (يُعالَج على main thread عبر viewModelScope! — انظر PF-07)
           + SetupReadinessGate.validate + تحديثات state متعددة
        ↓
[8] MovitTrainingEngine.processFrame (:567-585) → processPoseFrame (:591-759):
      PresenceSupervisorBridge → FrameIngressGate (تسلسل أحادي)
      → frame.mirrored() (نسخة كاملة إن كانت كاميرا أمامية)
      → JointAngleTracker.extractTrackedAngles
      → buildJointVisibilities + VisibilityMonitor (استدعاءان: evaluateJointVisibility ثم checkVisibility)
      → PauseController.processVisibilityResult (قرار تخطي العد)
      → FramePipelineExecutor.runMainPath:
           AngleSmoother (متوسط متحرك) → StartPoseGate → PhaseStateMachine
           → PositionValidator (اختياري) → JointEvaluator
      → FrameFeedbackEmitter + RepCounter/MotionRecorder hooks
      → HoldExerciseCoordinator أو RepCompletionCoordinator
        ↓  (callbacks ترجع للـ VM)
[9] engine callbacks → FeedbackRouter (صوت/اهتزاز/رسائل) + _state.update
      → MovitTrainingRoutes.kt:107 يجمع UiState كاملاً بـ collectAsStateWithLifecycle
      → إعادة تركيب TrainingSessionScreen
        ↓  (عند الاكتمال)
[10] finalizeCurrentExercise (:876-931) → writeHooks.finalizeUpload
      → بناء التقارير → TrainingSessionReportCache → enqueueUpload (مباشر أو WorkoutExecutionBatchCoordinator)
      → TrainingSessionFlowCoordinator (تمرين تالٍ / راحة / اكتمال)
```

**مسار iOS الموازي**: `IosCameraFrameSource` (237 سطر) → `IosPoseDetector` (155 سطر، عبر `IosPoseLandmarkerBridge`) → `PoseLandmarkSmoother` المشترك (نفس معاملات One-Euro) → نفس `PoseFrameAssembler` → نفس الـ VM.

---

## 3. نموذج الخيوط (Threading Model)

| المرحلة | الخيط | ملاحظات |
|---|---|---|
| تحليل CameraX + تحويل/تدوير Bitmap | `analysisExecutor` (أحادي) | أعمال الـ bitmap تتم هنا (خطوة 3–4) |
| One-Euro + تجميع الزوايا + تسليم الإطار | خيط MediaPipe callback | `inferenceInFlight` لا يُحرَّر إلا بعد اكتمال كل سلسلة الاستماع (خطوة 5–6) |
| معالجة المحرك الكاملة + معظم `_state.update` | `poseFrameWorker` على `Dispatchers.Default` | خطوة 7–8 |
| **`SupervisorAction.ProcessFrame` أثناء العد التنازلي** | **main thread** (`viewModelScope` عبر `wireSupervisor` :531) | ⚠️ مسار ثانٍ يصل إلى `engine.processFrame` — PF-07 |
| العد التنازلي، مؤقت الراحة، الكتابة/الرفع | main + `Dispatchers.Default` جزئياً | `finalizeCurrentExercise` يخلط السياقين |
| كتابة `frameCounter` (عداد fps للتشخيص) | خيط MediaPipe callback → Compose state | ⚠️ PF-08 |

**نقطة تصميم مركزية للتحقق**: المحرك (`MovitTrainingEngine`) مبني كأنه **أحادي الخيط** (كل حالته `var` عادية، و`FrameIngressGate` مجرد `Boolean` غير ذري — `FrameIngressGate.kt:9`). هل يضمن التدفق فعلياً وصول كل الاستدعاءات من خيط واحد؟ (انظر PF-07).

---

## 4. طبقات إسقاط الإطارات (Backpressure) — 5 طبقات متراكبة

للمراجعة كبنية واحدة (هل كلها ضرورية؟ أيها يعمل فعلاً؟):

1. `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` (CameraX).
2. خانق `shouldAnalyzeFrame()` اليدوي حسب `targetFps` — `CameraXFrameSource.kt:407-416`.
3. بوابة `inferenceInFlight` + عدّاد `skippedBusyFrames` — `MediaPipePoseDetector.kt:192-202`.
4. `Channel.CONFLATED` في الـ VM — `TrainingSessionViewModel.kt:167`.
5. `FrameIngressGate` داخل المحرك — `MovitTrainingEngine.kt:576`.

---

## 5. قواعد المراجعة (Rules of Engagement)

1. **قراءة فقط** — لا تعديل، لا إصلاح. الناتج تقارير فقط.
2. **دليل أو لا وجود** — كل نتيجة تُسند إلى `file:line` مع مقتطف كود أو سلسلة استدعاء كاملة. الادعاء بلا دليل يُرفض.
3. **كل شك أولي (PF-xx) يحصل على حكم صريح**: `CONFIRMED` أو `REFUTED` أو `NEEDS-DATA` (يحتاج قياس على جهاز) — مع الدليل.
4. **القياس قبل الحكم في الأداء**: عبّر كمّياً — عدد التخصيصات/إطار، استدعاءات/إطار، بايت/إطار، ms/مرحلة. التقدير النظري مقبول إذا وُسم `NEEDS-DATA`.
5. **المنصتان معاً**: أي نتيجة في طبقة الالتقاط أو التنعيم تُفحص على Android و iOS (سؤال التكافؤ Parity دائم).
6. **لا تفترض أن الاسم يعكس السلوك** — اقرأ الجسم الفعلي. ولا تفترض أن الاختبارات الموجودة (`commonTest` غنية جداً) تغطي الادعاء — استشهد بالاختبار المحدد إن وُجد.
7. **افصل بين "خطأ" و"قرار تصميم مقصود"**: المشروع منقول من محرك قديم (legacy parity — انظر تعليقات `I-2 split`, `Phase 07 WS-2`, `parity` في الكود). ما يبدو غريباً قد يكون تكافؤاً مقصوداً. سجّله كـ **OQ (سؤال مفتوح)** وليس كخطأ، إلا إذا أثبتّ ضرراً.
8. **إعادة تحقق عدائية**: كل نتيجة بخطورة P0 أو P1 يجب أن يتحقق منها وكيل ثانٍ مستقل يحاول **دحضها** قبل اعتمادها في الفهرس النهائي.
9. أوامر مفيدة: `./gradlew :core:training-engine:allTests`، `./gradlew :core:pose-capture:allTests`، `./gradlew :feature:training:allTests` (من داخل `kmp-app/`).

---

## 6. مسارات المراجعة (Review Tracks)

> كل مسار: **الملفات** ثم **أسئلة التحقق** ثم **الشكوك المرتبطة به من §7**. المسارات مستقلة وقابلة للتوازي. التقدير الزمني إرشادي لعمق القراءة المطلوب.

---

### Track A — طبقة الالتقاط Android (CameraX + MediaPipe)

**الملفات** (كلها تحت `core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/`):
`android/CameraXFrameSource.kt` (500)، `android/MediaPipePoseDetector.kt` (355)، `android/LandmarkSmoother.kt` (77)، `android/MediaPipeLandmarkMapper.kt`، `android/PoseModelResolver.kt`، `android/PoseLandmarkerHeavyModelStore.kt`، `android/MediaPipeSyncPoseDetector.kt`، `android/AndroidPoseRefiner.kt`، والبوابات المشتركة `commonMain/.../CameraStartGate.kt`، `LensSwitchFrameGate.kt`، وكذلك `feature/training/src/commonMain+androidMain: TrainingCameraSettings.kt`، `TrainingThroughputFlags.kt`، `core/training-engine/.../boundary/TrainingThroughputProfile.kt`.

**أسئلة التحقق**:
- A1. **تكلفة تجهيز الإطار**: تتبّع دورة حياة الـ Bitmap في `detectAsync` (`MediaPipePoseDetector.kt:153-190`): `toBitmap()` ثم `rotateBitmapForAnalysis` (ينشئ `Bitmap.createBitmap` جديداً لكل إطار عند أي دوران ≠ 0 — السطر 213-227) ثم `BitmapImageBuilder`. كم bitmap يُخصَّص لكل إطار؟ هل يمكن إعادة استخدام buffer (bitmap pool / reuse)؟ ما كلفة هذا عند 30fps بدقة التحليل الفعلية؟
- A2. **تسلسل الاستدلال**: `inferenceInFlight` يُحرَّر في `finally` بعد اكتمال **كل** سلسلة المعالجة اللاحقة (تنعيم + تجميع + تسليم حتى `channel.trySend`) — `onPoseResult:275-313`. كم ms تضيف هذه السلسلة إلى "فترة انشغال" الاستدلال، وهل تخفض fps الفعلي؟ هل يجب تحرير البوابة فور استلام النتيجة؟
- A3. **تسريب `frameCameraState`**: خريطة `ConcurrentHashMap<Long, Boolean>` تُملأ عند كل إرسال (:165) وتُمسح عند كل نتيجة (:279). إذا أسقط MediaPipe إطاراً داخلياً دون تسليم نتيجة، هل يبقى المدخل للأبد؟ قدّر معدل النمو.
- A4. **مسار GPU→CPU fallback** في `warmUp` (:101-146): استدعاء `warmUp` ذاتياً داخل `synchronized(initLock)` — هل هو re-entrant آمن؟ وما سلوك `scheduleHeavyUpgrade` المتزامن معه؟
- A5. **دورة الحياة**: من يستدعي `dispose()`؟ الـ Host يستدعي `stop()` فقط عند مغادرة الشاشة (`TrainingSessionCameraHost.android.kt:102-107`) — هل يبقى `PoseLandmarker` + `analysisExecutor` + `lastFrameBitmap` أحياء بعد الخروج من التدريب؟ ما أثر ذلك على الذاكرة، وهل هو مقصود (إبقاء دافئ للجلسة التالية)؟
- A6. **بوابات البدء/التبديل**: راجع منطق `CameraStartGate` + `LensSwitchFrameGate` + `switchingCamera` + `bindingInProgress` + `providerInitializing/Ready` — 5 أعلام حالة متداخلة. هل توجد حالة سباق تعلّق الربط (مثلاً: `pendingProviderReady` يُستبدل قبل التنفيذ :214-216)؟ هل `providerInitializing` يبقى `true` للأبد بعد النجاح (:219-231) وما أثره؟
- A7. **اختيار fps والدقة**: قيّم `chooseFpsRange` (:396-405) و`ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER` (:294-301) و`applyWidestZoom` بإعادتيه المؤجلتين (:441-452) — صحة واكتمالاً وتسريباً محتملاً بعد unbind.
- A8. **التقاط snapshot أثناء التدريب**: `takeSnapshotJpeg` (:233-241) ينسخ الـ bitmap كاملاً ثم يضغطه — على أي خيط يُستدعى (تتبّع من `AndroidTrainingFrameSnapshotPort`)؟ وكم مرة في الجلسة (لكل rep peak؟) وما أثره على سلاسة المعالجة؟

**شكوك مرتبطة**: PF-01, PF-02, PF-03, PF-04, PF-22, PF-23, PF-24, PF-25.

---

### Track B — طبقة الالتقاط iOS + تكافؤ المنصتين (Parity)

**الملفات**: `core/pose-capture/src/iosMain/` كاملاً (أهمها `IosCameraFrameSource.kt` 237، `IosPoseDetector.kt` 155، `IosPoseLandmarkerBridge.kt`)، `core/pose-capture/src/commonMain/.../PoseLandmarkSmoother.kt`، `PoseLandmarkFlatCodec.kt`، `core/training-engine/src/iosMain/` (المنافذ)، `iosApp/` (جسر Swift إن لزم)، `feature/training/src/iosMain/` (Host + المنافذ).

**أسئلة التحقق**:
- B1. **تكافؤ خط الأنابيب**: طابق كل مرحلة من §2 بمقابلها على iOS: هل يمر إطار iOS بنفس الترتيب (تنعيم → refine → assemble)? هل معاملات One-Euro متطابقة فعلاً (`LandmarkSmoother` Android يستهلك ثوابت `PoseLandmarkSmoother` — تحقق من الطرف الآخر)؟
- B2. **الترميز المسطّح**: `PoseLandmarkFlatCodec` يُستخدم في `IosPoseDetector` — ما كلفة التحويل flat↔objects لكل إطار؟ وهل هناك نسخ مزدوج (Swift→Kotlin→Landmark)؟
- B3. **إدارة الذاكرة**: دورة حياة `CVPixelBuffer`/الإطارات في `IosCameraFrameSource` — هل يوجد retain zombie أو نسخ غير ضروري؟ من يملك خيط التسليم؟
- B4. **الإسقاط والاتجاه**: هل منطق المرآة/الدوران/أبعاد التحليل (`analysisImageWidth/Height`) متطابق مع Android بحيث يتطابق الـ overlay؟
- B5. **الفجوات**: ما الموجود على Android وغائب عن iOS (مثلاً: التشخيصات معطّلة كلياً `TrainingPipelineLogger.ios.kt:9` = `false`، خانق fps؟ بوابة انشغال؟ heavy model؟) — اجرد الفروق في جدول.

**شكوك مرتبطة**: PF-05 (شق iOS)، OQ-03, OQ-04.

---

### Track C — الدخول إلى المحرك والتزامن (Ingress, Threading & Concurrency) ⭐ الأهم

**الملفات**: `feature/training/src/commonMain/.../TrainingSessionViewModel.kt` (1646 — الأسطر 123-360 و 509-874)، `core/training-engine/.../session/SessionSupervisor.kt` (565)، `FrameIngressGate.kt` (33)، `PresenceSupervisorBridge.kt`، `PauseController*.kt`، `SupervisorAction.kt`، `SupervisorSignal.kt`، `TrainingSessionCameraHost.android.kt`.

**أسئلة التحقق**:
- C1. **المسار المزدوج إلى `engine.processFrame`** (أخطر سؤال في المراجعة): في حالة `TRAINING` يستدعي الـ worker المحرك مباشرة (`TrainingSessionViewModel.kt:294` على `Dispatchers.Default`). في حالات الإعداد/العد التنازلي تُرسل إشارة إلى `SessionSupervisor` الذي قد يُصدر `SupervisorAction.ProcessFrame` (`SessionSupervisor.kt:332-344`) — ويُعالَج في `wireSupervisor` عبر `viewModelScope` أي **على main thread** (`TrainingSessionViewModel.kt:531` ثم `:796-809`). ارسم مخطط الحالات الزمني: هل توجد نافذة انتقال (مثلاً COUNTDOWN→TRAINING) يمكن أن يُنفَّذ فيها `processFrame` من الخيطين **بالتداخل**؟ لاحظ أن `FrameIngressGate` غير ذري (plain `Boolean`) ولا يضمن رؤية الذاكرة (memory visibility) بين خيطين، وكل حالة المحرك `var` عادية.
- C2. متى يُصدر الـ Supervisor فعلاً `ProcessFrame`؟ اجرد كل حالات `SessionSupervisor` التي تعالج `PoseFrame` signal، وحدد هل المحرك يكون `isRunning=true` أصلاً في تلك الحالات (وإلا فالاستدعاء ضائع — عمل main thread بلا فائدة).
- C3. **قناة CONFLATED**: هل إسقاط الإطارات صامت مقبول لكل الحالات (بما فيها إطارات "لا وضعية" التي تغذي مؤقت NoPose)؟ هل يمكن أن يُسقَط إطار `null` حرج فيتأخر إنذار "قف أمام الكاميرا"؟
- C4. `onPoseFrame` يقرأ `_state.value.requiresCamera()` (:258) والـ worker يقرأ `_state.value.isCameraSwitching` (:273) لكل إطار — تقييم صحة القراءة عبر الخيوط وتكلفتها.
- C5. **كتابة Compose state من خيط الكاميرا**: `frameCounter++` في `TrainingSessionCameraHost.android.kt:92` يكتب `mutableIntStateOf` من خيط MediaPipe callback (مسار debug فقط). هل هذا آمن في Compose؟ وهل شرط `onDebugFps != null && isTrainingDebugBuild()` يضمن صفر كلفة في release؟
- C6. **تعدد طبقات الحضور (presence)**: توجد 3 آليات متوازية لغياب الوضعية: (أ) `SessionSupervisor.NoPoseFrame` timers، (ب) `PresenceSupervisorBridge` داخل المحرك، (ج) علم `visibilityWarningActive` في الـ VM. تتبّع سيناريو "المستخدم خرج من الكادر أثناء التدريب" خطوة بخطوة: هل تتصادم الآليات (رسالتان؟ إيقافان؟) أم تتكامل؟ وثّق مخطط التسلسل.
- C7. `handleSupervisorAction(ShowNoPoseWarning)` (:838-855) و`handlePresenceEvent(NoPoseWarning)` (:1285-1302) يرسلان نفس إشارة التغذية الراجعة حرفياً — هل المساران متنافيان زمنياً (أحدهما setup والآخر training)؟ أم تكرار فعلي؟
- C8. صحة `updateSessionElapsed` (:375-388): يعتمد على `timestampMs` من الإطارات — ماذا يحدث للـ elapsed عند توقف الإطارات (كاميرا معلقة) أو عند lens switch (إعادة تعيين `lastTrainingTimestampMs = 0L` في handleSupervisorAction فقط)؟ هل يتجمد المؤقت؟

**شكوك مرتبطة**: PF-06, PF-07, PF-08, PF-16.

---

### Track D — قلب المحرك: مسار الإطار الواحد (Engine Core Per-Frame Path)

**الملفات** (كلها `core/training-engine/src/commonMain/.../`): `session/MovitTrainingEngine.kt` (898 — ركّز على `processPoseFrame` :591-759)، `engine/pipeline/FramePipelineExecutor.kt` (150)، `FrameEvaluationPipeline.kt` (27)، `engine/JointAngleTracker.kt`، `engine/AngleSmoother.kt` (43)، `engine/evaluation/JointEvaluator.kt` + `JointEval.kt`، `engine/PhaseStateMachine.kt` (340)، `engine/StartPoseGate.kt`، `engine/RepCounter.kt` (443)، `session/RepCompletionCoordinator.kt`، `session/SessionOrchestrator.kt` (156)، `session/ExecutionClock.kt`، `position/PositionValidator.kt`، `visibility/VisibilityMonitor.kt`، `engine/feedback/*` (4 ملفات)، `bilateral/BilateralController.kt`.

**أسئلة التحقق**:
- D1. **جرد التخصيصات لكل إطار**: أنشئ جدولاً بكل ما يُخصَّص في استدعاء `processPoseFrame` واحد: `frame.mirrored()` (نسخ 35 معلماً ×2 قوائم + JointAngles)، `extractTrackedAngles` (خرائط)، `buildJointVisibilities` (خريطة + `ensureAppended`)، `smoothedAngles` (`mapValues`)، `primaryAngles` (`filterKeys`)، `MainPathFrameResult`، `forScoring(...)`، نتائج `JointErrorCollection`... إلخ. قدّر العدد الإجمالي للكائنات/إطار وأثر GC عند 25fps.
- D2. **ازدواجية حساب الرؤية**: `visibilityMonitor.evaluateJointVisibility(visibilities)` (:618) ثم `visibilityMonitor.checkVisibility(...)` (:622) في نفس الإطار بنفس المدخلات — افتح `VisibilityMonitor.kt`: هل يكرر الثاني عمل الأول داخلياً؟ هل يمكن دمجهما في تمريرة واحدة؟
- D3. **ترتيب اتخاذ قرار التخطي**: `skipCounting` يُحسب (:629-642) **بعد** استخراج الزوايا والرؤية لكن **قبل** `runMainPath` — هل هذا الترتيب صحيح؟ وماذا عن `positionResult` الذي يُحسب داخل `runMainPath` حتى أثناء أطوار لا تحتاجه؟ (لاحظ شرط التخزين `shouldTrackState` :696-704 منفصل عن شرط الحساب.)
- D4. **إشارة المرآة المزدوجة**: المحرك يعكس الإطار (`workingFrame = frame.mirrored()` :600 — والمرآة تقلب `isFrontCamera=false` في النسخة) لكنه يمرر **العلم الأصلي** `frame.isFrontCamera=true` إلى `extractTrackedAngles` (:610) و`buildJointVisibilities` (:616) و`runMainPath` (:654). افتح `JointAngleTracker` و`JointLandmarkMapping.computeJointVisibility` و`PositionValidator.validate`: ماذا يفعل كلٌّ بعلم `isFrontCamera` مع معالم **معكوسة مسبقاً**؟ تتبّع مفصلاً واحداً (مثلاً left_elbow بكاميرا أمامية) عبر السلسلة كاملة رقمياً وأثبت عدم وجود قلب مزدوج.
- D5. **آلة الأطوار والعد**: راجع `PhaseStateMachine` (hysteresis، timings، onRepCompleted/onRepIncomplete) و`RepCounter` (minRepInterval، الخصم، النتائج) و`RepCompletionCoordinator.consumeIfPendingAndHandle` (:755) — هل يمكن فقدان rep أو عدّه مرتين عند تزامن `onRepCompleted` من آلة الأطوار مع bilateral flip أو مع `discardCurrentRepAttempt` (:420-426)؟
- D6. **المتغيرات الميتة**: أكّد أن `executionStartMs` (:399, :480, :596) يُكتب ولا يُقرأ، وأن `lastSmoothedAngles` (:403) لا يُستخدم إطلاقاً. ابحث عن غيرهما في المحرك (حقول تُحدَّث ولا تُقرأ، معاملات ممررة ولا تُستخدم — مثلاً `allJointsVisible` داخل `MainPathFrameResult` هل يستهلكه أحد؟ `rawTrackedAngles` في النتيجة؟).
- D7. **تكافؤ الساعة**: المحرك يخلط مصدرين للوقت: `frame.timestampMs` (زمن الإطار) و`nowMs()` عبر `ExecutionClock` (زمن حائطي مع إيقاف مؤقت). اجرد أي المكونات يستخدم أيهما (`PhaseStateMachine` timeProvider=nowMs لكن الأطوار تتغذى على زوايا إطارات قد تتأخر...) — هل يوجد انحراف يؤثر على `minRepIntervalMs` أو مدد الأطوار عند تقطع الإطارات؟
- D8. `SessionOrchestrator.snapshot()` (:121-135) — هل يستدعيه أحد في الإنتاج أم للاختبارات فقط؟

**شكوك مرتبطة**: PF-09, PF-10, PF-11, PF-12 (شق المحرك), PF-14, PF-19.

---

### Track E — صحة الهندسة الرياضية (Geometry & Math Correctness)

**الملفات**: `geometry/` كاملاً: `JointAngleCalculator.kt`، `PoseFrameAssembler.kt` (106)، `ElbowAngleEstimator.kt`، `ElbowCorrectionDiagnostics.kt`، `VirtualLandmarks.kt` (41)، `PoseLandmarkMirroring.kt`، `JointLandmarkMapping.kt`، `LandmarkTiltCorrector.kt`، `DisplayLandmarkTransform.kt`، `PosePoint.kt`، `model/JointAngles.kt`، `model/PoseLandmarkIndices.kt`، `filter/OneEuroFilter.kt`، والاختبارات المقابلة في `commonTest` (خاصة `*ParityTest`).

**أسئلة التحقق**:
- E1. **fallback ثلاثي/ثنائي الأبعاد**: `angleAt3D` (`PoseFrameAssembler.kt:65-84`) يسقط إلى 2D عندما تقل رؤية معالم world عن العتبة — هل يمكن أن يتذبذب المفصل الواحد بين 3D و2D بين إطارين متتاليين (قيم زاوية مختلفة جوهرياً لنفس الوضعية)؟ ما أثر ذلك على آلة الأطوار، وهل يعالجه `AngleSmoother`؟
- E2. **الحالة العالمية المشتركة**: `PoseFrameAssembler` هو `object` مفرد بحالة قابلة للتغيير (`elbowEstimator` :12). يُستدعى `assemble` من خيط MediaPipe و`resetElbowEstimator` من مسار تبديل العدسة (`CameraXFrameSource.kt:142`) — سباق؟ وماذا لو عمل كاشفان بالتوازي (شاشة debug + تدريب)؟ تلوث حالة بين الجلسات؟
- E3. **المرآة على المعالم الافتراضية**: `mirrored()` يستدعي `PoseLandmarkMirroring.mirrorLandmarks` على قائمة من 35 معلماً (بعد إلحاق NECK=33 وSPINE=34). افتح جدول تبديل اليسار/يمين: هل يتعامل مع 33/34 (نقاط منتصف — يكفي قلب x) دون إفساد؟ وهل `mirrorAngles` يبدّل كل الأزواج صح؟
- E4. **تصحيح الكوع**: راجع خوارزمية `ElbowAngleEstimator.correct` — شروط تفعيلها (worldLandmarks≥33)، ثباتها الزمني (تعتمد timestamp)، وأثر reset عند تبديل العدسة فقط (وليس عند بدء تمرين جديد — هل يجب؟).
- E5. **عتبة الرؤية 0.5**: هل هي متسقة في كل مكان (`isVisible()` الافتراضي، `visibilityThreshold` في assemble، `minVisibility` في `VisibilityMonitor` من `TimingPolicy`)؟ اجرد كل العتبات في جدول — أي تفاوت غير مقصود؟
- E6. **One-Euro**: قارن `OneEuroFilter`/`OneEuroFilter3D` مع الورقة المرجعية (معالجة dt غير المنتظم، أول إطار، timestamps متساوية أو راجعة للخلف عند lens switch reset) — و`OneEuroFilterParityTest` هل يغطي هذه الحواف؟
- E7. **تحويل العرض**: `DisplayLandmarkTransform` + `SkeletonOverlayMapper` + `skeletonLandmarkProjector` (`TrainingSessionScreen.kt:92-102`) — تطابق الإسقاط مع أبعاد التحليل (letterbox/crop)، خاصة مع `RATIO_4_3` للمعاينة ودقة تحليل مختلفة، والمرآة الأمامية (`skeletonMirrorPreview`).

**شكوك مرتبطة**: PF-10, PF-11, PF-18, PF-19 (الشق الرياضي).

---

### Track F — أداء الحالة والواجهة (Compose/UI State Performance)

**الملفات**: `TrainingSessionViewModel.kt` (بنية `TrainingSessionUiState` :1568-1626 وكل مواضع `_state.update`)، `MovitTrainingRoutes.kt`، `TrainingSessionScreen.kt` (565)، `TrainingSessionPanels.kt`، `SkeletonOverlayMapper.kt`، `TrainingRomIndicatorMapper.kt`، `designsystem/components/MovitSkeletonOverlay` (في `core/designsystem`)، `TrainingDebugOverlay.kt`.

**أسئلة التحقق**:
- F1. **عدّ `_state.update` لكل إطار**: في مسار TRAINING: `applyPoseLandmarksToUi` (:362-373) + `updateSessionElapsed` (:386) + `refreshSkeletonOverlay` (:1393-1399) + callbacks (rep/phase/hold...) — وفي مسار الإعداد: أكثر (setup guidance :320-334 + `refreshSkeletonOverlay` **مرتين** :335 و:358). اجرد العدد الفعلي لكل حالة، وكم نسخة `TrainingSessionUiState` (data class بـ ~45 حقلاً) تُنشأ في الثانية.
- F2. **نطاق إعادة التركيب**: الحالة كلها تُجمع في `MovitTrainingRoutes.kt:107` وتُمرر كوسيط واحد إلى `TrainingSessionScreen` — عند تغيّر `landmarks` كل إطار، ما الذي يُعاد تركيبه فعلياً؟ هل يلزم فصل تدفق المعالم/الهيكل العظمي عن حالة الشاشة (StateFlow منفصل يُجمع داخل الـ overlay فقط)؟ قدّم قياساً (Layout Inspector recomposition counts) أو تحليلاً بنيوياً دقيقاً.
- F3. `romIndicators` و`landmarkProjector` بـ `remember(keys...)` (`TrainingSessionScreen.kt:75-102`): المفاتيح تتغير كل إطار (`state.landmarks`) — إذن `buildSkeletonRomIndicators` يُعاد حسابه كل إطار على main thread. ما كلفته؟ وهل مكانه الصحيح الـ VM worker؟
- F4. **قنوات التغذية الراجعة**: `FeedbackRouter`/`FeedbackArbiter`/`FeedbackScheduler` (في `core/training-engine/.../feedback/`) — هل تعمل على main أم worker؟ هل تخصص كائنات لكل إشارة مع أن معظمها يُسقَط بالتهدئة (cooldown)؟ راجع ترتيب الفلترة (dedupe قبل بناء الكائن؟).
- F5. `maybeDeliverRandomMessage` (:1315-1323) يستدعي `engine.metricsSnapshot()` (بناء كائن كامل) كل ثانية فقط للتحقق من أخطاء المفاصل — هل يمكن استخدام `lastJointStateInfos` المتاح أصلاً؟
- F6. **الـ overlay نفسه**: `MovitSkeletonOverlay` — هل يستخدم `drawWithCache`/`Canvas` بكفاءة أم يبني paths/objects كل إطار؟

**شكوك مرتبطة**: PF-12, PF-13.

---

### Track G — دورة حياة الجلسة والتدفق متعدد التمارين (Session Lifecycle & Flow)

**الملفات**: `TrainingSessionViewModel.kt` (:751-1245 — `handleSupervisorAction`, `finalizeCurrentExercise`, `reloadForNextFlowItem`, `startRestTimer`, `syncFlowUi`, `onCleared`)، `session/SessionSupervisor.kt`، `session/TrainingSessionFlowCoordinator.kt` (241)، `session/CountdownController.kt`، `session/SetupReadinessGate.kt`، `SetupVoiceGuidanceGate.kt`، `session/PauseController.kt`، `TrainingSessionLifecyclePolicy.kt`، `TrainingCameraSwitchPolicy.kt`، `TrainingSessionPlannedWritePolicy.kt`.

**أسئلة التحقق**:
- G1. **ثلاثة مسارات إيقاف**: `stopSession()` (:252)، `stopAndFinalize()` (:482)، `finalizeCurrentExercise()` (:876) — ارسم من يستدعي أياً منها ومتى (زر رجوع، اكتمال، إنهاء يدوي، onCleared). هل يمكن استدعاء `engine.stop()` مرتين لنفس التمرين (مثلاً supervisor يُصدر `StopEngine` ثم `finalizeCurrentExercise` يستدعي `engine?.stop()` مجدداً)؟ ما أثر الإيقاف المزدوج على `ExerciseWorkoutSummary` (مدة صفرية؟ رفع مكرر؟).
- G2. **إعادة البناء بين التمارين**: `reloadForNextFlowItem` (:1115-1153) يستبدل engine/writeHooks/frameCaptureCoordinator — هل كائنات الجولة السابقة تُفصل بالكامل (المحرك القديم callbacks لا تزال مربوطة بـ hooks قديمة — هل يمكن أن تُستدعى بعد الاستبدال؟)؟ وهل `feedback.resetAll()` + `feedbackEventRouter.reset()` كافيان (previousHoldState يُصفَّر، لكن `lastRandomMessageCheckMs`؟ `visibilityWarningActive`؟ `visibilityPauseCount`/`cameraWarningCount` — عدادات جودة الجلسة هل يجب أن تُصفَّر لكل تمرين أم تتراكم قصداً؟).
- G3. **إعادة بناء المحرك عند تغيير التفضيلات**: `wirePreferences` (:509-519) يعمل عند أول جمع (initial emission) — هل يُعاد بناء المحرك فور الإنشاء بلا داعٍ؟ و`rebuildEngineIfNeeded` (:1413) يتحقق من `isTrainingActive()` — ماذا لو تغيّرت الشدة أثناء العد التنازلي (COUNTDOWN ليست training-active؟) — هل يضيع wiring؟
- G4. **الخلفية/العودة**: `onHostBackgrounded/Foregrounded` (:436-480) + `TrainingSessionLifecyclePolicy` — تتبّع: تدريب → خلفية 5 ثوانٍ → عودة، و→ خلفية دقيقتين → عودة. هل تتصرف السياسة كما يوحي (استئناف/إعادة جولة)؟ وماذا عن الكاميرا (CameraX lifecycle-aware لكن المحرك؟) والمؤقت `activeElapsedMs`؟
- G5. **مؤقت الراحة**: `startRestTimer` (:1155-1171) حلقة `delay(1000)` — انحراف تراكمي مقبول؟ إلغاء نظيف عند skipRest/onCleared؟ `restNearEndAnnounced` يُصفَّر في كل المسارات؟
- G6. **`onCleared`** (:1534-1546): قارن قائمة التنظيف بقائمة كل الموارد الحية (restTimerJob، poseFrameWorker، channel، countdown، feedback، writeHooks، supervisor، **engine.stop()? غير موجود!** — هل ترك المحرك دون stop مقصود لأن stopSession سبق؟ أثبت). و`frameCaptureCoordinator.awaitPendingCaptures` — ماذا يحدث للالتقاطات المعلقة عند إغلاق الشاشة فجأة؟
- G7. **SessionSupervisor نفسه** (565 سطر): راجع آلة الحالات كاملة (IDLE→SETUP→COUNTDOWN→TRAINING→PAUSED→COMPLETED...) — حالات ميتة؟ إشارات لا تُعالج؟ `droppedActionCount` (backpressure للأفعال — `extraBufferCapacity`؟) متى تسقط الأفعال وما الأثر؟

**شكوك مرتبطة**: PF-16, PF-20, PF-21.

---

### Track H — التسجيل والتقارير والرفع (Recording, Reports & Uploads)

**الملفات**: `journal/MotionRecorder.kt` (379)، `MotionDataModels.kt`، `MetricsCalculator.kt`، `TrainingMotionSession.kt`، `report/` كاملاً (12 ملفاً — أهمها `MovitPostTrainingReportBuilderV2.kt`، `MovitPeakFrameCaptureManager.kt`، `MovitRepReplaySampler.kt`)، `feature/training: TrainingSessionWriteHooks.kt`، `TrainingFrameCaptureCoordinator.kt`، `WorkoutExecutionBatchCoordinator.kt`، `TrainingSessionWriteDiagnostics.kt`، `core/data/.../TrainingSessionWriteCoordinator` (استهلاكاً فقط)، `AndroidTrainingFrameSnapshotPort.kt` + نظيره iOS.

**أسئلة التحقق**:
- H1. **نمو الذاكرة عبر جلسة طويلة**: `onMotionFrameRecorded` يُستدعى لكل إطار تدريب (`MovitTrainingEngine.kt:714-720`) — ماذا يخزّن `MotionRecorder` لكل إطار (زوايا؟ حالات مفاصل؟ خرائط منسوخة؟) وهل يوجد حد أقصى (cap) أم نمو خطي؟ احسب: جلسة 3 تمارين × 3 جولات × 60 ثانية @ 25fps = ~13,500 إطار — كم MB؟
- H2. **التقاط الذروة والـ replay**: `TrainingFrameCaptureCoordinator` + `MovitPeakFrameCaptureManager` + `MovitRepReplaySampler` — متى تُلتقط snapshot JPEG (لكل rep؟ لكل حالة مفصل؟) وعلى أي خيط يجري الضغط (`takeSnapshotJpeg`)، وكم صورة تُحمل في الذاكرة قبل الكتابة؟ هل التقاط JPEG أثناء rep نشط ينافس خيط التحليل؟
- H3. **بناء التقرير مرتين**: في `cachePostTrainingReport` (:1027-1048) ثم `enqueueUpload` (:1050-1094) — `writeHooks.buildPostTrainingReport` يُستدعى **مرتين** بنفس المدخلات لكل تمرين. أكّد وقدّر الكلفة (هل البناء ثقيل؟) — تكرار حقيقي أم رخيص مقبول؟
- H4. **مسار الدفعات**: `WorkoutExecutionBatchCoordinator` (سياق explore) مقابل الرفع المباشر — تكافؤ الحقول؟ `flushExploreBatchIfNeeded` يعتمد `workoutGroupId` — ماذا لو null مع batch موجود (:1101-1103 يعيد return صامتاً — ضياع بيانات؟).
- H5. **إعادة المفاتيح (rekey)**: `TrainingSessionReportCache.rekeyPostTraining` + `markReportAvailable` — سباق بين عرض التقرير والرفع؟ تسريب مدخلات كاش قديمة عبر الجلسات؟
- H6. **الاتساق متعدد الجولات**: `accumulateDayReport` (:933-958) + `MovitPostTrainingReportCrossSetAggregator` — تحقق من صحة الدمج (مجاميع، متوسطات مرجحة، أفضل/أسوأ rep) عبر جولات وتمارين متعددة، بالاستعانة بالاختبارات الموجودة (`MultiSetRichReportCrossSetIntegrationTest`, `PlannedMultiSetReportTotalsTest`) — ما الفجوات غير المغطاة؟
- H7. `syncFrameEvidenceToWriteHooks` يُستدعى في الدالتين (:1029، :1052) — تكرار؟ وهل `frameCaptureCoordinator.captures()` ينسخ قوائم كبيرة كل مرة؟

**شكوك مرتبطة**: PF-02 (الشق الاستهلاكي)، وأسئلة H1–H7 نفسها.

---

### Track I — الكود الميت والتكرار (Dead Code & Duplication Sweep)

**النطاق**: الوحدات الثلاث كاملة. منهجية مقترحة: لكل رمز public/internal في `commonMain` — ابحث عن مستدعٍ في غير الاختبارات؛ ولكل زوج ملفات متشابهة التسمية — قارن المنطق.

**قائمة بدء (تحقق منها ثم وسّع البحث)**:
- I1. `session/LiveExerciseRunner.kt` — لا مستدعي إنتاجي (المراجع: اختباره + `ParityRunner` الاختباري فقط). ميت؟ أم واجهة عامة مقصودة لـ iOS/debug؟ ابحث في `shared/` و`iosApp/` قبل الحكم.
- I2. حقلا `MovitTrainingEngine`: `executionStartMs` (يُكتب :480,:596 ولا يُقرأ) و`lastSmoothedAngles` (:403 لا يُستخدم) — مؤكدان تقريباً؛ ابحث عن أمثالهما في `RepCounter`/`PhaseStateMachine`/`SessionSupervisor`.
- I3. حقول `MainPathFrameResult` (`FramePipelineExecutor.kt:131-149`): `rawTrackedAngles`، `skippedForFrame`، `allJointsVisible` — من يستهلكها فعلاً؟
- I4. `StubCameraFrameSource.kt` و`StubPoseDetector.kt` (androidMain) — مستخدمان أم بقايا؟
- I5. ازدواج منسّق التشخيص: `formatTrainingPipelinePeriodicForTest` (`TrainingPipelineDiagnostics.kt:228-277`) ينسخ `buildPeriodicLine` يدوياً — وقد **انحرفا فعلاً** (سطر `backlog` موجود في الإنتاجي :200-201 وغائب عن الاختباري). وثّق واقترح توحيداً.
- I6. تكرار إشارة NoPose بين `handleSupervisorAction` و`handlePresenceEvent` (انظر C7).
- I7. طبقات التحقق من الجاهزية: `SetupReadinessGate` مقابل `StartPoseGate` مقابل `PositionValidator` مقابل `VisibilityMonitor` — ارسم مصفوفة "مَن يتحقق من ماذا ومتى" — أي تداخل حقيقي (نفس الفحص مرتين في نفس الإطار)؟
- I8. `EngineMetrics.positionErrorCount` (`MovitTrainingEngine.kt:793`) = `positionErrors.size` المرسلة في نفس الكائن — حقل زائد؟ من يقرأه؟
- I9. `LiveExerciseRunner.Metrics` مقابل `EngineMetrics` مقابل `TrainingSessionState` (`SessionOrchestrator.snapshot`) — ثلاث لقطات حالة متوازية: أيها مستهلك فعلاً؟
- I10. مقارنة `MovitPostTrainingReportBuilderV2` بأي Builder V1 متبقٍ، و`MovitSessionReportBuilder` — نسخ قديمة حية؟
- I11. `PoseDetector.buildPoseFrame` (الواجهة في boundary) — من يستدعيها خارج debug؟
- I12. عمليات بحث نمطية إضافية: `@Deprecated`، `TODO/FIXME`، دوال `internal fun` بلا استخدام، `expect` بلا مستهلك مشترك.

**شكوك مرتبطة**: PF-14, PF-15, PF-17.

---

### Track J — خط أنابيب الإعدادات (Exercise Config Pipeline)

**الملفات**: `config/ExerciseConfigParser.kt`، `ExerciseConfigModels.kt`، `ExerciseConfigTypes.kt`، `ExerciseConfigLiveSupport.kt`، `TrackedJointExtensions.kt`، `TrackedJointPhaseAdapter.kt`، `StateMessageValueSerializer.kt`، واستهلاكها في `core/data/.../TrainingConfigRepository` و`TrainingSessionViewModel` (`getBySlug` :117, :1121).

**أسئلة التحقق**:
- J1. `configRepository.getBySlug(slug)` — هل يعيد config محلَّلاً ومخزّناً (cache) أم يحلل JSON عند كل استدعاء؟ (يُستدعى عند init وكل `reloadForNextFlowItem`.)
- J2. الحسابات المشتقة داخل المحرك (`primaryJointCodes`, `primaryPhaseJointConfigs`, `phaseTimingConfig`) تُحسب مرة في الإنشاء — جيد؛ لكن `getMessagesForState` (`TrainingSessionViewModel.kt:697`) يبحث في القوائم **عند كل رسالة حالة** — كلفة البحث الخطي × تكرار الرسائل؟
- J3. صلابة المحلل: config ناقص/مشوه (طور بلا مفاصل، عتبات معكوسة min>max، `poseVariantIndex` خارج النطاق — لاحظ `error(...)` في `MovitTrainingEngine.kt:128-130` يرمي استثناء يقتل الجلسة!) — ما سلوك الفشل المصمم؟ `TrainingPoseVariantResolver.resolve` هل يضمن نطاقاً صالحاً دائماً قبل بناء المحرك؟
- J4. تكرار الرموز السحرية: عتبات/مدد افتراضية منثورة (`SetupProbeDefaults`, `SetupValidationConfig`, `TimingPolicy`, `StabilityPolicy`) — اجرد القيم الافتراضية المتداخلة وحدد المصدر الأوحد لكل قيمة.

---

### Track K — التشخيصات وخطة القياس (Diagnostics & Performance Measurement)

**الملفات**: `diagnostics/TrainingPipelineDiagnostics.kt` (277)، `TrainingPipelineLogger.*.kt`، `observability/PipelineTrace.kt` + `PipelineTraceConfig.kt`، `TrainingSessionWriteDiagnostics.kt`، وأداة `feature/training-debug` (نظرة عامة فقط).

**أسئلة التحقق**:
- K1. **`runBlocking` لكل إطار**: كل دوال التسجيل تستخدم `runBlocking { mutex.withLock }` (`TrainingPipelineDiagnostics.kt:79-124`) وتُستدعى من 3 خيوط مختلفة لكل إطار (analyzer، MediaPipe callback، worker). في debug فقط على Android (`MovitGeneratedBuildConfig.DEBUG`) — لكن هذا يعني أن **قياسات debug نفسها مشوَّهة** بأقفال متزاحمة. اقترح بديلاً (atomics) وقدّر التشويه.
- K2. iOS: `isTrainingPipelineDiagnosticsEnabled = false` دائماً (`TrainingPipelineLogger.ios.kt:9`) — التشخيصات ميتة بالكامل على iOS. مقصود؟ (OQ-04)
- K3. `PipelineTrace` (`MovitTrainingEngine.kt:186` — عام دائماً حتى في release): ما كلفة `record(...)` لكل إطار/حدث في release؟ ring buffer أم قائمة تنمو؟
- K4. عدّاد `recordVmIngress(wasConflated = false)` (`TrainingSessionViewModel.kt:259`) — لا يقيس الدمج أبداً؛ `vmConflated` صفر دائماً والـ backlog محسوب من فرق عدادين ملتبس. صمّم القياس الصحيح.
- K5. **خطة القياس المعيارية (Deliverable خاص بهذا المسار)**: صمّم بروتوكول قياس على جهاز حقيقي يملأ جدول `perf-baseline.md`:
  - fps فعلي عند كل طبقة (camera accepted → pose results → vm processed → engine processed) لمدة 60s تمرين قياسي.
  - متوسط/p95 لـ inference ms، وزمن مرحلة المحرك (processPoseFrame)، وزمن بناء الحالة حتى الإطار المعروض (motion-to-photon تقريبي).
  - عدد GC minor collections / دقيقة أثناء التدريب (perfetto / logcat GC).
  - ذاكرة RSS عند: فتح الشاشة، بعد جولة، بعد 3 تمارين، بعد التقرير.
  - نقاط القياس المقترح إضافتها (أين تُغرس trace sections) — اقتراح فقط دون تنفيذ.

**شكوك مرتبطة**: PF-05, PF-06, K1–K4.

---

## 7. سجل الشكوك الأولية (Preliminary Findings Register)

> نتائج مراجعتي الأولية. **ليست أحكاماً نهائية** — كل بند يحتاج تأكيداً أو دحضاً بدليل. الترميز: PF-xx. عمود "المسار" يحدد الوكيل المسؤول.

| ID | الادعاء (مختصر) | الموضع | المسار | خطورة متوقعة |
|---|---|---|---|---|
| PF-01 | سلسلة تخصيص Bitmap لكل إطار (`toBitmap` + `rotateBitmapForAnalysis` تنشئ bitmap جديداً كل إطار، بلا إعادة استخدام) | `MediaPipePoseDetector.kt:168-183, 213-227` | A | P1 أداء |
| PF-02 | `takeSnapshotJpeg` ينسخ الإطار كاملاً + ضغط JPEG — الخيط والتكرار غير محددين | `MediaPipePoseDetector.kt:233-241` | A+H | P2 |
| PF-03 | تسريب محتمل بطيء في `frameCameraState` إذا أسقط MediaPipe إطاراً دون نتيجة | `MediaPipePoseDetector.kt:75,165,279` | A | P3 |
| PF-04 | `inferenceInFlight` يُحرَّر بعد كامل سلسلة المعالجة اللاحقة → تسلسل استدلال+معالجة يخفض fps الفعلي | `MediaPipePoseDetector.kt:275-313` | A | P2 أداء |
| PF-05 | `runBlocking`+Mutex لكل إطار من 3 خيوط في التشخيصات (debug Android)؛ وميتة كلياً على iOS | `TrainingPipelineDiagnostics.kt:77-124`, `TrainingPipelineLogger.ios.kt:9` | K | P2 (debug)، OQ (iOS) |
| PF-06 | `recordVmIngress(wasConflated=false)` دائماً — قياس الدمج (conflation) لا يعمل، مقياس backlog مضلل | `TrainingSessionViewModel.kt:259` | C+K | P3 |
| PF-07 | **مساران بخيطين مختلفين إلى `engine.processFrame`** (worker مباشر في TRAINING؛ main عبر `SupervisorAction.ProcessFrame`) + `FrameIngressGate` غير ذري وكل حالة المحرك غير محمية → سباق محتمل عند انتقالات الحالة | `TrainingSessionViewModel.kt:294,531,796-809`, `SessionSupervisor.kt:332-344`, `FrameIngressGate.kt:9` | C | **P1 تزامن** |
| PF-08 | كتابة Compose state (`frameCounter`) من خيط MediaPipe لكل إطار (مسار debug) | `TrainingSessionCameraHost.android.kt:90-99` | C | P3 |
| PF-09 | `frame.mirrored()` ينسخ 35 معلماً ×2 + زوايا كل إطار (الكاميرا الأمامية = الافتراضي) — ضمن جرد تخصيصات أوسع لكل إطار | `PoseFrame.kt:22-32`, `MovitTrainingEngine.kt:600` | D | P2 أداء |
| PF-10 | `ensureAppended` يُستدعى مرتين/إطار (لا-عملية ثانية — تأكيد رخص)؛ والأهم: سلامة المرآة على المعلمين الافتراضيين 33/34 | `MovitTrainingEngine.kt:809`, `VirtualLandmarks.kt:15-17`, `PoseLandmarkMirroring.kt` | D+E | P2 صحة |
| PF-11 | تمرير معالم **معكوسة** مع علم `isFrontCamera=true` الأصلي إلى extractTrackedAngles/visibility/validator — خطر قلب مزدوج أو عقد ضمني غير موثق | `MovitTrainingEngine.kt:600-616,644-658` | D+E | **P1 صحة (إن ثبت)** |
| PF-12 | `metricsSnapshot()` يُبنى حتى 3×/إطار في الـ VM؛ و`refreshSkeletonOverlay` يُستدعى مرتين لكل إطار إعداد | `TrainingSessionViewModel.kt:392,1319,1380` و`:335,358` | D+F | P2 أداء |
| PF-13 | حتى ~4-5 `_state.update` لكل إطار على `UiState` بـ 45 حقلاً + جمع كامل الحالة في الـ Route → ضغط إعادة تركيب في fps الوضعية | `TrainingSessionViewModel.kt` مواضع متعددة، `MovitTrainingRoutes.kt:107` | F | P1 أداء |
| PF-14 | حقلان ميتان في المحرك: `executionStartMs` (يُكتب فقط)، `lastSmoothedAngles` (لا يستخدم) | `MovitTrainingEngine.kt:399,403,480,596` | I | P3 |
| PF-15 | `LiveExerciseRunner` بلا مستدعٍ إنتاجي (اختبارات فقط) | `session/LiveExerciseRunner.kt` | I | P3 |
| PF-16 | ثلاث طبقات حضور متوازية (Supervisor timers + PresenceBridge + أعلام VM) وإشارة NoPose مكررة حرفياً في موضعين | `TrainingSessionViewModel.kt:838-855,1285-1302` | C+I | P2 هيكلة |
| PF-17 | منسّق التشخيص الاختباري نسخة يدوية انحرفت فعلاً عن الإنتاجي (سطر backlog) | `TrainingPipelineDiagnostics.kt:166-210 vs 228-277` | I | P3 |
| PF-18 | `PoseFrameAssembler` كائن مفرد بحالة متغيرة مشتركة (`elbowEstimator`) — سباق reset (main) مع assemble (خيط MediaPipe) وتلوث بين مستهلكين | `PoseFrameAssembler.kt:11-12,40`, `CameraXFrameSource.kt:142` | E | P2 تزامن |
| PF-19 | تكديس تنعيم ثلاثي: One-Euro (معالم) → MA window (زوايا) → hysteresis (أطوار) — زمن استجابة تراكمي غير مقيس | `LandmarkSmoother.kt`, `AngleSmoother.kt`, `PhaseStateMachine.kt` | D+E | P2 (NEEDS-DATA) |
| PF-20 | إعادة بناء المحرك عند أول انبعاث تفضيلات (فور init) + شكوك فصل callbacks المحرك القديم عند `reloadForNextFlowItem` | `TrainingSessionViewModel.kt:509-519,1113-1153,1413-1418` | G | P2 |
| PF-21 | مؤقتا الراحة والـ elapsed مبنيان على مصادر زمن مختلطة (delay-loop / إطارات) — انجراف وتجمد محتمل عند تقطع الإطارات | `TrainingSessionViewModel.kt:375-388,1155-1171` | C+G | P2 |
| PF-22 | 5 طبقات إسقاط إطارات متراكبة (§4) — أيّها يعمل فعلاً؟ هل الخانق اليدوي (طبقة 2) زائد مع بوابة الانشغال (طبقة 3)؟ | §4 | A | P2 (NEEDS-DATA) |
| PF-23 | `postDelayed` مرتان لإعادة الزوم — استدعاء بعد dispose محمي بمطابقة هوية الكاميرا فقط | `CameraXFrameSource.kt:441-452` | A | P3 |
| PF-24 | `stop()` لا يوقف المنفّذ ولا الكاشف (يبقيان دافئين) — من يستدعي `dispose()` فعلاً؟ تسريب محتمل بعد مغادرة التدريب نهائياً | `CameraXFrameSource.kt:462-494`, `TrainingSessionCameraHost.android.kt:102-107` | A | P2 ذاكرة |
| PF-25 | مساران لإغلاق `imageProxy` في detectAsync (نجاح :169 + catch :188) — إغلاق مزدوج محتمل محمي بـ try فارغ | `MediaPipePoseDetector.kt:164-189` | A | P3 |

### أسئلة مفتوحة معمارية (OQ) — تُجاب بتوثيق النية، لا بحكم صح/خطأ

- **OQ-01**: هل نمط "UiState واحد ضخم لكل شيء بما فيه معالم كل إطار" قرار واعٍ أم إرث نقل؟ ما البديل المفضل للفريق (تدفق overlay منفصل)؟
- **OQ-02**: هل التنعيم الثلاثي (PF-19) مطلوب لتكافؤ legacy أم قابل للتبسيط بعد القياس؟
- **OQ-03**: ما مستوى التكافؤ الملزم بين Android وiOS (نفس الأرقام بالضبط أم نفس السلوك المحسوس)؟
- **OQ-04**: تعطيل تشخيصات iOS كلياً — مقصود أم فجوة؟
- **OQ-05**: إبقاء الكاشف والمنفّذ دافئين بعد `stop()` (PF-24) — سياسة مقصودة لسرعة العودة؟
- **OQ-06**: هل `feature/training-debug` و`TrainingDebugOverlay` وأدوات debug مستبعدة فعلياً من إصدار release (بالاعتماد على `isTrainingDebugBuild`/`MovitGeneratedBuildConfig.DEBUG` فقط أم بفصل الوحدة)؟
- **OQ-07**: `LiveExerciseRunner` — واجهة عامة مستقبلية أم بقايا مرحلة نقل (Phase 07 WS-2)؟

---

## 8. مواصفات المخرجات (Output Specification)

### 8.1 هيكل الملفات

```
Docs/04-Research/camera-engine-review/
  00-INDEX.md                     ← الفهرس الرئيسي (يُجمَّع أخيراً)
  01-frame-flow-verified.md       ← رحلة الإطار §2 بعد التحقق والتصحيح (Track C/D يملآنها)
  perf-baseline.md                ← جدول القياسات (Track K)
  track-A-android-capture.md
  track-B-ios-parity.md
  track-C-ingress-concurrency.md
  track-D-engine-core.md
  track-E-geometry-math.md
  track-F-ui-state-perf.md
  track-G-session-lifecycle.md
  track-H-recording-reports.md
  track-I-dead-code-duplication.md
  track-J-config-pipeline.md
  track-K-diagnostics-measurement.md
```

### 8.2 قالب النتيجة الواحدة (إلزامي حرفياً)

```markdown
### [<Track>-<NN>] <عنوان من سطر واحد>
- **Severity**: P0 | P1 | P2 | P3
- **Type**: Correctness | Performance | Concurrency | Memory | Dead-code | Duplication | Architecture
- **Status**: CONFIRMED | REFUTED | NEEDS-DATA
- **Related-PF**: PF-xx أو —
- **Files**: <path:line>, <path:line>
- **Evidence**: <مقتطف كود أو سلسلة استدعاء كاملة أو رقم قياس — إلزامي>
- **Impact**: <الأثر الملموس: على المستخدم/الدقة/الذاكرة — كمّياً كلما أمكن (تخصيصات/إطار، ms، MB)>
- **Fix-sketch**: <سطر إلى ثلاثة: اتجاه الحل دون تنفيذ>
- **Effort**: S | M | L
- **Verified-by**: <معرّف وكيل التحقق العدائي — إلزامي لـ P0/P1>
```

### 8.3 سلّم الخطورة

| درجة | تعريف |
|---|---|
| **P0** | عدّ تكرارات/نتيجة خاطئة، انهيار/ANR، فقدان بيانات تمرين |
| **P1** | تدهور fps/زمن استجابة قابل للقياس على المسار الساخن؛ سباق تزامن له سيناريو تحفيز واقعي |
| **P2** | هدر (تخصيصات، عمل مكرر) بلا أثر مستخدم مثبت؛ تصميم هش؛ ذاكرة تنمو ببطء |
| **P3** | كود ميت، تكرار نصي، نظافة، تشخيصات معطوبة |

### 8.4 محتوى `00-INDEX.md` (يجمّعه وكيل التجميع بعد اكتمال المسارات)

1. **جدول كل النتائج** (كل المسارات) مرتباً: Severity ثم Track — أعمدة: ID، العنوان، Severity، Type، Status، Files، Effort.
2. **جدول أحكام PF-01 → PF-25**: كل شك أولي ← الحكم (CONFIRMED/REFUTED/NEEDS-DATA) + رقم النتيجة المفصلة.
3. **إجابات OQ-01 → OQ-07** (أو "يحتاج قرار مالك المنتج").
4. **قائمة المعالجة العشرة الأوائل (Top-10 Remediation)**: مرتبة بـ (خطورة × جهد) مع تسلسل تنفيذ مقترح وتبعيات بينها.
5. **بيان التغطية**: ما الملفات التي لم تُقرأ في أي مسار (إن وجدت) — الشفافية عن الفجوات إلزامية.

### 8.5 تعريف الاكتمال (Definition of Done)

- [ ] كل المسارات A–K سلّمت تقريرها بالقالب أعلاه.
- [ ] كل PF-01→PF-25 حصل على حكم صريح مسند بدليل.
- [ ] كل نتيجة P0/P1 مرّت بتحقق عدائي مستقل (وكيل ثانٍ حاول دحضها) ووُسمت `Verified-by`.
- [ ] `01-frame-flow-verified.md` يحتوي رحلة الإطار النهائية المصححة + جدول الخيوط المؤكد (يشمل iOS).
- [ ] `perf-baseline.md` مملوء بالقياسات أو موسوم صراحة `NEEDS-DEVICE` مع بروتوكول قياس جاهز للتشغيل.
- [ ] `00-INDEX.md` مكتمل بالأقسام الخمسة بما فيها Top-10.

---

## 9. ملاحظات ختامية للمراجعين

- **الاختبارات ثروة**: `commonTest` يضم اختبارات تكافؤ (Parity) وحواف كثيرة (`FrozenParityFixtureTest`, `MovitTrainingEngineParityTest`, `ParityRunner`...). قبل الحكم على سلوك، ابحث عن اختباره — وإن وجدت سلوكاً بلا اختبار على مسار حرج، سجّل ذلك كنتيجة `Type: Correctness` بحد ذاته (فجوة تغطية).
- **السياق التاريخي**: تعليقات مثل `I-2 split`, `I-5`, `Phase 07 WS-2`, `07.8-B` تشير لخطة نقل مرحلية من محرك legacy — بعض "الغرابة" ديون نقل واعية. ميّزوا بين "دَين موثق" و"خطأ".
- **أولوية القراءة إن ضاق الوقت**: Track C ثم D ثم F (المسار الساخن والتزامن) — هذه الثلاثة تحمل أعلى احتمال لمشاكل حقيقية مؤثرة.
