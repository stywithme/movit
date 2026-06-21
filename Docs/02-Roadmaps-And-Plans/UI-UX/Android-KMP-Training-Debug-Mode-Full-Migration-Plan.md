# Android KMP Training Debug Mode Full Migration Plan

آخر تحديث: **2026-06-14**
الحالة: **قيد التنفيذ — Android أساسي جاهز؛ iOS live-camera + tests + docs (D10/D11) مكتملة جزئياً**
النطاق: نقل Debug Mode القديم الخاص بمحرك التدريب من Legacy Android `MO` إلى KMP/Compose، مع الحفاظ على قدرته كأداة قياس وتحليل وليست مجرد شاشة عرض.

المراجع التي تمت مراجعتها مباشرة:

- Legacy readonly:
  - `D:/laragon/www/POSE-2-MO-readonly/kmp-app/app/src/main/java/com/trainingvalidator/poc/ui/debug/DebugActivity.kt`
  - `D:/laragon/www/POSE-2-MO-readonly/kmp-app/app/src/main/res/layout/activity_debug.xml`
  - `D:/laragon/www/POSE-2-MO-readonly/kmp-app/app/src/main/res/layout/dialog_debug_settings.xml`
  - `D:/laragon/www/POSE-2-MO-readonly/kmp-app/app/src/main/java/com/trainingvalidator/poc/video/VideoManager.kt`
  - `D:/laragon/www/POSE-2-MO-readonly/kmp-app/app/src/main/java/com/trainingvalidator/poc/pose/PoseLandmarkerHelper.kt`
  - `D:/laragon/www/POSE-2-MO-readonly/kmp-app/app/src/main/java/com/trainingvalidator/poc/analysis/ElbowAngleEstimator.kt`
- KMP الحالي:
  - `kmp-app/settings.gradle.kts`
  - `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt`
  - `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/LandmarkSmoother.kt`
  - `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/geometry/ElbowAngleEstimator.kt`
  - `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/position/PositionValidator.kt`
  - `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/position/CameraPositionDetector.kt`
  - `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/position/PoseSceneDetector.kt`
  - `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/position/PoseSceneExpectation.kt`
  - `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/SetupReadinessGate.kt`
  - `kmp-app/core/designsystem/src/commonMain/kotlin/com/movit/designsystem/components/MovitSkeletonOverlay.kt`
  - `kmp-app/core/designsystem/src/commonMain/kotlin/com/movit/designsystem/components/SkeletonOverlayContract.kt`
  - `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingDebugOverlay.kt`
  - `kmp-app/app/src/debug/AndroidManifest.xml`

---

## 1) الحكم المختصر

الـ Debug Mode القديم **غير منقول** إلى KMP بالشكل المطلوب.

الموجود حاليا في KMP هو:

- Debug FPS overlay بسيط داخل جلسة التدريب.
- `TrainingPipelineDiagnostics` و`PipelineTrace` لتسجيل بعض مؤشرات pipeline.
- debug-only pilot activities للـ shell/design system.
- primitives جيدة في المحرك الجديد: `PositionValidator`, `PoseSceneDetector`, `CameraPositionDetector`, `ElbowAngleEstimator`, `DisplayLandmarkTransform`, `MovitSkeletonOverlay`.

لكن غير موجود حاليا:

- شاشة Debug Lab مكافئة لـ `DebugActivity`.
- اختيار input mode بين Camera / Video / Image.
- Angle Diagnostics tab.
- Position Check tab قابل للتعديل الحي.
- Camera / Scene Detection tab.
- عرض raw vs smoothed vs world diagnostics.
- copy/export لنص التشخيص.
- public bitmap/video detection API في pose-capture.
- diagnostics كاملة لتصحيح المرفق `ElbowAngleEstimator.lastDiagnostics`.
- UI controls للـ model Full/Heavy داخل Debug.
- entry point واضح من debug build أو shell.

الخطة المقترحة تنقل الـ Debug كأداة KMP منظمة باسم **Training Debug Lab**، مع common analyzer مشترك، وplatform sources منفصلة، وCompose UI، ودون إدخال منطق Android-only إلى `commonMain`.

---

## 2) ماذا كان يفعل Legacy Debug فعليا؟

Legacy `DebugActivity` لم يكن شاشة زخرفية. كان مختبرا فوق نفس منطق pose/training primitives لاختبار:

- مصدر الإدخال: كاميرا مباشرة، فيديو، صورة ثابتة.
- MediaPipe model: Full / Heavy.
- flip camera وتأثير `isFrontCamera` على المرآة والقياسات.
- smoothing للـ landmarks.
- world landmarks مقابل normalized landmarks.
- حساب الزوايا قبل المحرك.
- تصحيح زوايا المرفق بالـ depth/hysteresis/hold logic.
- position checks بنفس أنواع المقارنة المستخدمة في التدريب.
- camera scene detection: posture/direction/visible region/facing/depth.
- عرض overlay مرئي فوق الشخص.
- FPS للكاميرا والاستدلال.
- نسخ report نصي للتشخيص.

ملاحظة مهمة: Legacy Debug **لا يشغل جلسة Workout كاملة ولا يعد التكرارات كاملة** مثل `TrainingActivity`. هو يختبر الطبقات التي تسبق وتغذي العد: pose, angles, elbow correction, position checks, scene/camera gates. لذلك نقل الـ Debug لا يعني تشغيل session كاملة، لكنه يجب أن يستخدم نفس primitives التي يستخدمها محرك التدريب الجديد.

---

## 3) Legacy Debug Inventory

### 3.1 Entry Point

| العنصر | Legacy |
|---|---|
| Activity | `DebugActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener` |
| Layout | `activity_debug.xml` |
| Settings dialog | `dialog_debug_settings.xml` |
| Manifest | مسجلة في `AndroidManifest.xml` |
| Profile launch | كان يوجد مسار من profile/debug area لفتحها |

### 3.2 Input Modes

| Mode | المنطق القديم | المطلوب نقله |
|---|---|---|
| `CAMERA` | CameraX عبر `CameraManager`, permission gate, live MediaPipe stream | live debug source يعيد نفس `PoseFrame`/raw landmarks للمحلل |
| `VIDEO` | `VideoManager` يستخدم ExoPlayer + `TextureView.getBitmap()` ويستخرج frames كل 33ms video-time تقريبا | video source deterministic، seek/play/pause/reset، وعدم تراكم frames |
| `IMAGE` | image picker، decode bitmap مع EXIF rotation، تشغيل تحليل ثابت وإعادة التحليل عند تغيير الإعدادات | image source synchronous، يحافظ على نفس أبعاد الصورة وfit-center overlay |

### 3.3 Settings

| الإعداد | Legacy behavior | ملاحظات النقل |
|---|---|---|
| Show info panel | إظهار/إخفاء debug panel | مطلوب في Compose bottom/side panel |
| Input mode | Camera/Video/Image | يغير source وoverlay scale mode |
| Pick file | فيديو أو صورة حسب mode | Android أولا، iOS لاحقا عبر expect/actual |
| Model | Full/Heavy | حاليا KMP يقرأ preference داخلي في pose-capture، يحتاج port نظيف |
| Tabs | Angle Diagnostics / Position / Camera | يجب نقلها كـ tabs فعلية |
| Copy debug info | Clipboard export | مطلوب للنص الحالي + JSON مختصر |

### 3.4 Angle Diagnostics Tab

المنطق القديم:

- اختيار مفصل أو أكثر من `JointLandmarkMapping.trackedJointCodes`.
- حساب الزاوية المعروضة من angles التي ستدخل التدريب.
- عرض:
  - source joint بعد mirror.
  - effective landmark indices.
  - displayed angle.
  - pipeline source: `World XYZ` أو `Screen XY fallback`.
  - normalized raw.
  - normalized smoothed.
  - world raw.
  - world smoothed.
  - XY/XZ/YZ/XYZ angles.
  - 3D-2D delta.
  - smoothing drift.
  - visibility/presence gates.
  - segment depth share / planar ratio.
- overlay يبرز joints المختارة، endpoints، vertex، وقيمة الزاوية.
- كل frame يعيد بناء diagnostics، والـ panel يحدث كل frameين أو في image mode.

نقطة حرجة:

- Legacy `ElbowAngleEstimator` كان يحتوي `lastDiagnostics`.
- KMP الحالي نقل التصحيح نفسه تقريبا، لكن **لا يحتوي `lastDiagnostics`**.
- نقل Debug كاملا يتطلب إعادة diagnostic output للمرفقين دون تغيير output الإنتاجي.

### 3.5 Elbow Correction / Hysteresis Diagnostics

Legacy `ElbowAngleEstimator` كان يسجل لكل مرفق:

- `facingRatio`
- `screenAngle`
- `worldAngle`
- `maxDzShare`
- `dzImbalance`
- `correctionPct`
- `outputAngle`
- `isHolding`
- `uaDzShare`
- `faDzShare`
- `strategy`

الاستراتيجيات القديمة:

- `STRAIGHT`
- `TRUST_3D`
- `TRUST_2D`
- `MILD_DOWN`
- `DEEP_DOWN`
- `LOW_CONF`
- `HOLD`

المنطق المهم:

- smoothing للـ depth ratios.
- output smoothing.
- last stable hold لمدة `HOLD_TIMEOUT_MS = 500ms`.
- gates مثل `STRAIGHT_ARM_GATE`, `INFLATION_GATE`, `LOW_DEPTH`, `MID_DEPTH`, `HIGH_DEPTH`.

المطلوب في KMP:

- إضافة data class common مثل `ElbowCorrectionDiagnostics`.
- جعل `ElbowAngleEstimator.correct(...)` يرجع diagnostics اختياريا أو يحتفظ بـ `lastDiagnostics` read-only.
- كتابة tests تؤكد أن إضافة diagnostics لا تغير `JointAngles` output.
- عرض diagnostics في Angle tab كما كان Legacy.

### 3.6 Position Check Tab

المنطق القديم:

- يبني `PositionCheck` synthetic باسم `debug_check`.
- يسمح بتغيير:
  - `PositionCheckType`
  - primary landmark
  - secondary landmark
  - operator
  - threshold
  - tilt correction on/off
- يستخدم `PositionValidator.validate(...)`.
- يعرض PASS / FAIL / FAIL_PENDING / SKIPPED.
- يعرض:
  - actual value.
  - threshold.
  - delta.
  - effective landmarks.
  - confirm frames.
  - skip reason.
  - detected camera position/facing.
  - active axis.
  - raw landmarks.
  - corrected landmarks عند tilt.

المطلوب في KMP:

- reuse كامل لـ `PositionValidator`.
- exposed debug adapter يبني synthetic check من UI state.
- نقل tilt correction كمنفذ platform-aware، وليس coupling مباشر إلى `PoseApp`.
- overlay يبرز الخط بين primary/secondary مع لون الحالة.
- tests تغطي كل `PositionCheckDebugStatus`.

### 3.7 Camera / Scene Detection Tab

المنطق القديم:

- يستخدم `PoseSceneDetector.detect(...)`.
- يطابق النتيجة مع `PoseSceneExpectation`.
- يعرض:
  - detected direction.
  - expected direction.
  - confidence.
  - facing.
  - closer side.
  - detected posture.
  - expected posture.
  - body axis.
  - detected visible region.
  - upper/core/lower visibility.
  - depth info.
  - match per axis.
- overlay يعرض camera detection state.

المطلوب في KMP:

- reuse `PoseSceneDetector`, `PoseSceneExpectation`, `CameraPositionDetector`.
- UI selectors متعددة للقيم المسموحة:
  - posture: standing/sitting/lying prone/lying supine/lying side.
  - direction: front/back/side any/side left/side right/any.
  - region: full/upper/lower/any.
- إضافة Debug Scene panel يعرض raw detector metrics التي لا تظهر في session العادية.

### 3.8 FPS / Backpressure / Reset

Legacy Debug كان يعيد reset للحالة عند:

- تغيير input mode.
- switch camera.
- seek video.
- تغيير tabs.
- تغيير settings المؤثرة.
- no pose.

ويعيد reset لـ:

- `LandmarkSmoother`
- `ElbowAngleEstimator`
- `PoseSceneDetector`
- `PositionValidator`
- FPS counters
- MediaPipe tracking state للفيديو والصورة.

المطلوب في KMP:

- state machine واضحة للـ Debug Lab lifecycle.
- reset موحد باسم `resetAnalysisState(reason)`.
- عدادات منفصلة:
  - camera/source FPS.
  - inference FPS.
  - processed analysis FPS.
  - skipped/busy frames.
- integration مع `TrainingPipelineDiagnostics` و`PipelineTrace` دون الاعتماد عليهما وحدهما.

---

## 4) Current KMP Gap Analysis

| المجال | الموجود حاليا | الفجوة |
|---|---|---|
| module structure | لا يوجد `feature:training-debug` | مطلوب module أو package مستقل لتجنب تضخيم `feature:training` |
| live camera | `CameraXFrameSource` + `MediaPipePoseDetector.detectAsync(ImageProxy)` | لا يوجد Debug screen يستهلك raw detection details |
| image/video | لا يوجد public `detectPoseFromBitmap` أو `detectPoseFromImage` في KMP current | مطلوب `DebugPoseInputSource` وMediaPipe running modes IMAGE/VIDEO |
| smoothing | `LandmarkSmoother` موجود Android-side داخل detector | لا يعرض raw vs smoothed للـ Debug |
| elbow correction | `ElbowAngleEstimator` موجود في common | diagnostics القديمة مفقودة |
| position debug | `PositionValidator` يحتوي `PositionCheckDebug` | لا توجد UI/adapter لبناء synthetic checks |
| scene debug | `PoseSceneDetector` و`CameraPositionDetector` موجودان | لا توجد شاشة أو panel لعرض metrics |
| overlay | `MovitSkeletonOverlay` و`SkeletonOverlayParityState` موجودان للتدريب | لا يدعم selected joint highlights / debug position line / camera scene overlay بشكل كامل |
| model switch | pose-capture يقرأ `PoseModelTypePreference` internal | لا توجد واجهة KMP سليمة لتغيير Full/Heavy من debug UI |
| debug entry | `app/src/debug/AndroidManifest.xml` يحتوي pilot activities | لا يوجد Training Debug Lab entry |
| iOS | camera host بدأ يتحسن للجلسة | لا يوجد Debug Lab ولا image/video analyzer |

---

## 5) Target Architecture

### 5.1 Module Decision

الخيار المفضل:

- إضافة module جديد: `:feature:training-debug`.

الأسباب:

- Debug Lab أداة كبيرة، وليست جزءا من جلسة المستخدم الإنتاجية.
- تحتاج Android-only media pickers وvideo controls لكن core analyzer يجب أن يبقى common.
- يمكن ربطها فقط في debug builds أو internal builds.
- تمنع تضخم `feature:training`.

تعديلات Gradle المطلوبة:

- إضافة `include(":feature:training-debug")` في `kmp-app/settings.gradle.kts`.
- module يعتمد على:
  - `:core:training-engine`
  - `:core:pose-capture`
  - `:core:designsystem`
  - `:core:resources`
  - `:feature:training` عند الحاجة لإعادة استخدام mappers فقط، أو نقل mappers المشتركة إلى core/designsystem إذا لزم.

### 5.2 Common Debug Domain

ملفات مقترحة:

- `feature/training-debug/src/commonMain/kotlin/com/movit/feature/trainingdebug/TrainingDebugModels.kt`
- `TrainingDebugAnalyzer.kt`
- `TrainingDebugViewModel.kt`
- `TrainingDebugReducers.kt`
- `TrainingDebugOverlayMapper.kt`

النماذج الأساسية:

```kotlin
enum class TrainingDebugInputMode { CAMERA, VIDEO, IMAGE }
enum class TrainingDebugTab { ANGLE_DIAGNOSTICS, POSITION_CHECK, CAMERA_SCENE, SETUP_GATE }

data class TrainingDebugConfig(
    val inputMode: TrainingDebugInputMode,
    val selectedJoints: Set<String>,
    val positionCheck: DebugPositionCheckConfig,
    val sceneExpectation: DebugSceneExpectation,
    val modelType: DebugPoseModelType,
    val tiltCorrectionEnabled: Boolean,
    val infoPanelVisible: Boolean,
)

data class TrainingDebugFrameInput(
    val landmarks: List<Landmark>,
    val worldLandmarks: List<Landmark>?,
    val timestampMs: Long,
    val isFrontCamera: Boolean,
    val analysisImageWidth: Int,
    val analysisImageHeight: Int,
)
```

### 5.3 Analyzer

`TrainingDebugAnalyzer` يكون common ويعمل على `TrainingDebugFrameInput`.

مسؤولياته:

- حساب `JointAngles` بنفس path الإنتاجي.
- تطبيق `ElbowAngleEstimator`.
- إنشاء angle diagnostics لكل joint مختار.
- تشغيل `PositionValidator` على synthetic check.
- تشغيل `PoseSceneDetector` و`CameraPositionDetector`.
- تشغيل `SetupReadinessGate` كـ KMP-only extension مفيد لقياس setup gate.
- إنتاج state واحد للـ UI:
  - live value.
  - status.
  - info panel text.
  - structured JSON snapshot.
  - overlay debug state.

ممنوع:

- أن يعتمد analyzer على Android `Bitmap`, `ImageProxy`, `Uri`, `Context`.
- أن يكتب إلى SharedPreferences.
- أن يغير منطق المحرك الإنتاجي لأجل العرض فقط.

### 5.4 Platform Input Sources

واجهة common:

```kotlin
interface TrainingDebugPoseSource {
    val mode: TrainingDebugInputMode
    suspend fun start(config: TrainingDebugSourceConfig)
    suspend fun stop()
    suspend fun resetTracking(reason: String)
}
```

Android implementations:

- `AndroidDebugCameraPoseSource`
  - يعيد استخدام `CameraXFrameSource` أو يشارك lower-level `MediaPipePoseDetector`.
  - يدعم switch camera.
  - يعرض source FPS وinference FPS.
- `AndroidDebugImagePoseSource`
  - image picker.
  - EXIF rotation.
  - MediaPipe `RunningMode.IMAGE`.
  - يعيد تحليل الصورة عند تغيير settings.
- `AndroidDebugVideoPoseSource`
  - ExoPlayer/TextureView أو Media3 video frame extraction.
  - deterministic frame timestamps مثل Legacy.
  - play/pause/seek/reset.
  - MediaPipe `RunningMode.VIDEO`.

iOS implementations:

- المرحلة الأولى: live camera فقط بنفس common analyzer.
- المرحلة الثانية: image picker.
- المرحلة الثالثة: video source إذا كانت MediaPipe iOS path جاهزة.

### 5.5 Pose Capture API Changes

مطلوب إضافة boundary نظيف في `core:pose-capture`:

- لا نعيد إحياء `PoseLandmarkerHelper` القديم.
- نضيف APIs أو class جديد debug-oriented:
  - `MediaPipeStillPoseDetector`
  - `MediaPipeVideoPoseDetector`
  - أو `MediaPipePoseDetector` يدعم `RunningMode` configurable.

المطلوب من API:

- إرجاع raw normalized landmarks قبل smoothing.
- إرجاع smoothed normalized landmarks.
- إرجاع raw/smoothed world landmarks إن أمكن.
- إرجاع `analysisImageWidth/Height`.
- إرجاع inference time.
- reset tracking state.
- model type selection.

قبول المرحلة:

- لا يتم تكرار conversion والمنطق في feature module.
- لا يتم تمرير Android classes إلى common analyzer.
- image/video detection لا تستخدم `LIVE_STREAM` landmarker بطريقة خاطئة.

### 5.6 Overlay Contract

الموجود `SkeletonOverlayParityState` مناسب للتدريب، لكنه لا يكفي لكل Debug.

إضافة contract جديد أو extension:

```kotlin
data class SkeletonDebugOverlayState(
    val selectedJointHighlights: List<DebugJointHighlight> = emptyList(),
    val positionLine: DebugPositionLine? = null,
    val sceneExpectation: DebugSceneOverlay? = null,
    val scaleMode: DebugOverlayScaleMode = DebugOverlayScaleMode.FillCenter,
)
```

يدعم:

- Angle tab:
  - endpoint A/C.
  - vertex B.
  - angle label.
  - multiple selected joints.
- Position tab:
  - primary/secondary line.
  - pass/fail/skipped color.
- Camera tab:
  - scene status badge.
  - detected direction/facing/region/posture.
- Image mode:
  - `FIT_CENTER`.
- Camera/video mode:
  - `FILL_CENTER`.

### 5.7 Compose UI

الشاشة الأولى يجب أن تكون الأداة مباشرة:

- full-screen camera/video/image surface.
- overlay فوق المصدر.
- top toolbar:
  - back.
  - FPS.
  - flip camera.
  - settings.
  - copy.
- live value/status band.
- collapsible info panel.
- settings bottom sheet:
  - input mode segmented control.
  - pick media button.
  - Full/Heavy selector.
  - tab selector.
  - tab-specific controls.
- video controls عند `VIDEO`:
  - play/pause.
  - seekbar.
  - current/duration.

لا يتم عمل landing page. هذه أداة قياس مباشرة.

### 5.8 Entry Points

Android debug build:

- إضافة `TrainingDebugActivity` في `app/src/debug/java/com/movit/debug`.
- تسجيلها في `app/src/debug/AndroidManifest.xml`.
- فتحها عبر:
  - adb.
  - debug menu داخل shell.
  - profile/debug area إذا كان موجودا في build debug.

iOS:

- route مخفي داخل debug/internal build shell.
- لا يظهر في production.

---

## 6) Migration Phases

### Phase D0 - Freeze Legacy Behavior

الهدف: تثبيت الحقيقة قبل النقل.

المهام:

- أخذ inventory نهائي لكل controls والـ strings القديمة.
- تسجيل sample debug exports من Legacy:
  - camera side-view elbow.
  - front camera mirror.
  - image mode.
  - video seek/reset.
  - position pass/fail/skipped.
  - scene match/mismatch.
- حفظ 2-3 fixtures صور/فيديو داخل test fixtures إن أمكن.
- توثيق golden outputs للزوايا والـ diagnostics.

القبول:

- لدينا baseline نصي أو JSON يمكن مقارنة KMP به.
- أي فرق مقصود يتم تسجيله قبل التنفيذ.

### Phase D1 - Common Debug Analyzer

الهدف: نقل منطق التحليل من Activity ضخمة إلى common analyzer.

المهام:

- إنشاء `:feature:training-debug`.
- إضافة `TrainingDebugModels`.
- إضافة `TrainingDebugAnalyzer`.
- ربطه بـ:
  - `PoseFrameAssembler`.
  - `ElbowAngleEstimator`.
  - `PositionValidator`.
  - `PoseSceneDetector`.
  - `CameraPositionDetector`.
  - `SetupReadinessGate`.
- إنتاج diagnostics structured + display text.

القبول:

- common unit tests تعمل بدون Android.
- analyzer يقبل frame input ويخرج angle/position/scene/setup diagnostics.
- لا يوجد Android import في commonMain.

### Phase D2 - Restore Elbow Diagnostics

الهدف: نقل الجزء الذي كان يجيب على سؤال "لماذا زاوية المرفق خرجت بهذا الشكل؟".

المهام:

- إضافة `ElbowCorrectionDiagnostics` في `core:training-engine`.
- تعديل `ElbowAngleEstimator` ليعرض آخر diagnostics read-only أو يرجعها مع النتيجة.
- نقل أسماء الاستراتيجيات القديمة.
- tests تقارن outputs قبل/بعد إضافة diagnostics.

القبول:

- `correct(...)` يعطي نفس `JointAngles` السابق.
- Debug analyzer يستطيع عرض `strategy`, `maxDzShare`, `correctionPct`, `isHolding`.
- حالة HOLD تظهر عند low-confidence ضمن timeout.

### Phase D3 - Android Live Camera Debug

الهدف: تشغيل أول نسخة حية من Debug Lab على Android.

المهام:

- إضافة `TrainingDebugActivity`.
- إضافة Compose screen.
- إضافة `AndroidDebugCameraPoseSource`.
- flip camera يعيد reset للـ analyzer/smoother/scene.
- عرض FPS.
- عرض basic skeleton overlay.
- copy debug info.

القبول:

- يفتح من debug build.
- الكاميرا تعمل.
- flip camera لا يترك حالة قديمة.
- no-pose يعرض حالة واضحة ويصفر overlay.

### Phase D4 - Angle Diagnostics Parity

الهدف: نقل Angle tab بالكامل.

المهام:

- multi-select joints.
- raw/smoothed/world diagnostics.
- mirrored source joint/effective indices.
- overlay joint highlights.
- elbow diagnostics section.
- JSON/text export.

القبول:

- اختيار `left_knee` default.
- اختيار مفاصل متعددة يعمل.
- front camera يعرض source/effective mirror بشكل صحيح.
- elbow section يظهر للمرفقين عند وجود world landmarks.

### Phase D5 - Position Check Lab

الهدف: نقل Position tab بالكامل.

المهام:

- selectors لـ check type/landmarks/operator/threshold.
- synthetic `PositionCheck`.
- status PASS/FAIL/FAIL_PENDING/SKIPPED.
- tilt correction source.
- overlay primary-secondary line.
- debug text للـ raw/corrected landmarks والـ active axis.

القبول:

- كل status قابل للوصول باختبارات.
- تغيير threshold يعيد التحليل للصورة الحالية.
- tilt correction يعمل في camera mode ولا يتسرب إلى image/video إلا لو صممنا source واضح له.

### Phase D6 - Camera / Scene Lab

الهدف: نقل Camera tab بالكامل.

المهام:

- selectors لـ expected posture/direction/region.
- scene detection + expectation match.
- عرض detector metrics.
- overlay scene state.
- reset scene detector عند تغيير tab/input/camera.

القبول:

- match/mismatch per axis ظاهر.
- `PoseSceneExpectation.matchesScene` هو مصدر الحقيقة.
- camera/front mirror لا يكسر side-left/side-right.

### Phase D7 - Image Mode

الهدف: نقل image picker والتحليل الثابت.

المهام:

- Android image picker.
- EXIF rotation.
- MediaPipe IMAGE mode.
- fit-center source + overlay.
- reanalyze on settings change.

القبول:

- الصورة لا تتمدد بشكل خاطئ.
- overlay متطابق مع الصورة.
- تغيير joint/position/scene settings يعيد إنتاج diagnostics دون اختيار الصورة ثانية.

### Phase D8 - Video Mode

الهدف: نقل video lab.

المهام:

- Android video picker.
- playback controls.
- deterministic frame extraction.
- seek resets analyzer/detector state.
- MediaPipe VIDEO mode.
- backpressure/skipped-frame counters.

القبول:

- نفس الفيديو يعطي frames متقاربة التوقيت بين التشغيلات.
- seek يعيد reset للـ smoother/elbow/scene.
- لا يوجد queue buildup عند inference بطيء.

### Phase D9 - Model Full/Heavy Control

الهدف: جعل Debug يختبر model type فعليا.

المهام:

- إضافة model selector port في `core:pose-capture`.
- توحيد القراءة مع `MovitTrainingPreferences` أو preference bridge رسمي.
- عدم كتابة SharedPreferences legacy مباشرة من UI.
- عرض model label الفعلي: full/heavy/heavy pending/fallback.

القبول:

- تغيير model يعيد تهيئة detector.
- يظهر fallback عند عدم توفر heavy.
- لا يوجد dependency من `core:pose-capture` إلى app module.

### Phase D10 - iOS Debug Path

الهدف: جعل Debug Lab KMP فعلا وليس Android-only forever.

المهام:

- تشغيل common screen داخل iOS debug route.
- live camera source أولا.
- image/video لاحقا حسب جاهزية MediaPipe iOS.
- الحفاظ على نفس analyzer والـ UI state.

القبول:

- iOS build يرى نفس tabs.
- live camera frames تدخل analyzer.
- أي source غير جاهز يظهر disabled state واضح وليس crash.

### Phase D11 - QA, Tests, And Documentation

المهام:

- common unit tests:
  - angle diagnostics.
  - elbow diagnostics.
  - position synthetic checks.
  - scene expectation.
  - setup gate probe.
- Android instrumentation smoke:
  - activity launches.
  - settings sheet.
  - image fixture.
  - video fixture إن أمكن.
- screenshot QA:
  - mobile portrait.
  - landscape/video.
  - small width.
- تحديث audit الرئيسي بوصلة لهذه الخطة بعد التنفيذ.

القبول:

- لا توجد regressions في `:feature:training:testDebugUnitTest`.
- Debug module tests خضراء.
- docs تذكر أي اختلاف مقصود عن Legacy.

---

## 7) Traceability Matrix

| Legacy function/area | KMP target |
|---|---|
| `DebugActivity.processPoseResult` | `TrainingDebugAnalyzer.analyze(frame, config)` |
| `updateAngleDiagnosticsDisplay` | `AngleDiagnosticsPanel` + `TrainingDebugAnalyzer.buildAngleDiagnostics` |
| `buildAngleDiagnosticsData` | common `AngleDiagnosticsBuilder` |
| `elbowAngleEstimator.lastDiagnostics` | `ElbowCorrectionDiagnostics` في `core:training-engine` |
| `updatePositionCheckDisplay` | `PositionDebugPanel` + synthetic check adapter |
| `rebuildPositionValidator` | `DebugPositionCheckFactory` |
| `updateCameraDetectionDisplay` | `SceneDebugPanel` |
| `VideoManager` | `AndroidDebugVideoPoseSource` |
| `detectPoseFromBitmap` | `MediaPipePoseDetector` IMAGE/VIDEO debug API أو classes متخصصة |
| `detectPoseFromImage` | `AndroidDebugImagePoseSource` |
| `SkeletonOverlayView.updateDebugJoints` | `SkeletonDebugOverlayState.selectedJointHighlights` |
| `SkeletonOverlayView.updateDebugJoint` | `SkeletonDebugOverlayState.positionLine` |
| `SkeletonOverlayView.updateCameraDetection` | `SkeletonDebugOverlayState.sceneExpectation` |
| `btnSwitchCamera` | debug source action `SwitchCamera` + `resetAnalysisState("camera switch")` |
| `btnCopyDebugInfo` | `TrainingDebugExportFormatter` + clipboard actual |

---

## 8) Acceptance Checklist

يعتبر النقل كاملا فقط عند تحقق الآتي:

- [x] Debug Lab يفتح في Android debug build (`TrainingDebugActivity` + Profile → Training Debug Lab في debug).
- [x] Camera mode يعمل مع flip camera وpermission gate (Android).
- [x] Image mode يعمل مع EXIF rotation وfit-center overlay (Android).
- [x] Video mode يعمل مع play/pause/seek/reset deterministic analysis (Android).
- [ ] Full/Heavy model selector يغير detector فعليا أو يعرض fallback بوضوح (port جاهز؛ UI wiring جزئي).
- [x] Angle tab يعرض selected joints وraw/smoothed/world diagnostics.
- [x] Elbow diagnostics موجودة وتعرض `strategy` و`HOLD` (`ElbowCorrectionDiagnostics` + `ElbowDiagnosticsAdapter`).
- [x] Position tab يبني synthetic checks ويعرض PASS/FAIL/FAIL_PENDING/SKIPPED.
- [ ] Tilt correction مدعوم أو disabled بوضوح في modes التي لا تدعمه (Android camera؛ iOS غير موصول بعد).
- [x] Camera tab يعرض posture/direction/region/facing/depth/match.
- [x] Setup Gate probe موجود لقياس gate الجديد في KMP (squat fixture مدمج في analyzer).
- [ ] Overlay يدعم angle highlight وposition line وscene state (contract جاهز؛ `MovitSkeletonOverlay` renderer pending).
- [x] Copy/export يعطي نصا قابلا للإرسال للفريق.
- [x] common analyzer بلا Android imports.
- [x] Android-specific media/camera code محصور في androidMain/app debug.
- [x] iOS route موجود على الأقل live-camera-ready أو disabled بوضوح للـ unsupported modes (`MovitInnerRoute.TrainingDebugLab` + image/video disabled chips).
- [x] tests تغطي diagnostics الأساسية (`training-engine`, `training-debug` common tests).

---

## 9) Risks And Decisions

| الخطر | القرار المطلوب |
|---|---|
| Image/video detection يحتاج MediaPipe running modes مختلفة | لا نستخدم LIVE_STREAM للصور والفيديو. ننشئ detectors مخصصة أو configurable safely |
| Heavy model selector حاليا internal preference فقط | إنشاء port رسمي بدلا من كتابة legacy SharedPreferences مباشرة |
| Debug قد يضخم `feature:training` | إنشاء `feature:training-debug` أو فصل package واضح |
| iOS image/video غير مضمون | live camera أولا، ثم image/video كمراحل لاحقة |
| overlay contract الحالي للتدريب فقط | إضافة debug overlay contract منفصل بدلا من تحميل `SkeletonOverlayParityState` فوق طاقته |
| diagnostics قد تغير output دون قصد | tests تثبت عدم تغير `JointAngles` بعد إضافة diagnostics |
| old Debug لا يعد reps | لا ندعي parity في count logic. نضيف Setup Gate probe وربما Rep/Session trace لاحقا كأداة منفصلة |

---

## 10) Recommended Implementation Order

1. إنشاء module والـ common models/analyzer.
2. إعادة `ElbowCorrectionDiagnostics` في `core:training-engine`.
3. تشغيل Android camera mode مع Angle tab.
4. إضافة Position tab.
5. إضافة Camera/Scene tab + Setup Gate probe.
6. إضافة Image mode.
7. إضافة Video mode.
8. إضافة Full/Heavy model selector الرسمي.
9. إضافة iOS route/live source.
10. إغلاق tests والـ QA وتحديث audit الرئيسي.

---

## 11) Definition Of Done

الـ Debug Mode يعتبر منقولا عندما يستطيع المطور أن يفتح Debug Lab في KMP، يختار Camera/Video/Image، يغير model والتبويب والإعدادات، يرى نفس القياسات التي كان يعتمد عليها في Legacy، وينسخ report يشرح لماذا خرجت زاوية أو position أو scene gate بهذه النتيجة، مع بقاء المنطق المشترك داخل KMP common وبدون الاعتماد على Activity Android ضخمة أو مسار Legacy.

---

## 12) Implementation Log

| Date | Agent | Scope | Status | Notes |
|---|---|---|---|---|
| 2026-06-15 | Agent parity | Debug/Training pipeline unification | **Done** | Camera→`CameraXFrameSource`; angles→`PoseFrameAssembler`; `TrainingGateFactory`; tests green |
| 2026-06-15 | Agent overlay | Selective annotations + multi-joint + mirror | **Done** | `MovitPoseAnnotationOverlay`; multi-select joints; no full skeleton default; front-camera index mirror in mapper |
| 2026-06-15 | Agent parity P1/P2 | Debug Lab ↔ Training parity gaps | **Done** | `LensSwitchFrameGate` debug delivery; production camera config; `PoseModelTypePort` live camera; `DeviceTiltPort`; optional `exerciseSlug` setup gate; single front-camera mirror in angle diagnostics |

### Agent 2 — Pose Capture APIs (2026-06-14)

**commonMain** (`com.movit.core.posecapture.boundary.trainingdebug`):

- `TrainingDebugInputMode`, `TrainingDebugSourceConfig`, `TrainingDebugPoseSource`
- `TrainingDebugPoseFrame` — raw/smoothed normalized + world, inference time, analysis dimensions
- `MediaPipeSyncRunningMode` (`IMAGE`, `VIDEO`)
- `PoseModelType`, `ResolvedPoseModel`, `PoseModelTypePort` (D9)
- `TrainingDebugVideoFrameSelector` — deterministic 33ms video-time interval (Legacy `VideoManager` parity)

**androidMain**:

- `MediaPipeSyncPoseDetector` — `warmUp(mode, config)`, `detect(bitmap, timestampMs)`, `resetTracking(reason, recreateVideoLandmarker)`, `shutdown()`
- `MediaPipeLandmarkMapper` — shared MediaPipe → `Landmark` conversion
- `PoseModelResolver` — heavy fallback + background upgrade (shared with live detector)
- `AndroidPoseModelTypePort` — official model read/write (no app-module dependency)
- `LandmarkSmoother.smoothWorld()` — world landmark smoothing for debug diagnostics
- `MediaPipePoseDetector.DetectionResult` extended with raw normalized/world + `modelDisplayLabel`
- Koin: `PoseModelTypePort` singleton, `MediaPipeSyncPoseDetector` factory

**iosMain**: `IosPoseModelTypePort` stub (heavy pending).

**Tests**: `:core:pose-capture:testDebugUnitTest` — `TrainingDebugVideoFrameSelectorTest`, `PoseModelTypeTest` (+ existing gate/codec tests). **BUILD SUCCESSFUL**.

**Handoff → Agent 4** (D9 model UI polish, iOS D10, instrumentation QA):

1. استبدال `AndroidDebugMediaPipeLandmarker` بـ `MediaPipeSyncPoseDetector` + `PoseModelTypePort` من Agent 2.
2. ربط settings UI بـ `resolveForInitialization()` لعرض fallback heavy.
3. iOS: تفعيل image/video عند جاهزية MediaPipe iOS أو الإبقاء على placeholder.
4. instrumentation smoke: `TrainingDebugActivity` launch، image/video fixtures.
5. تحسين `MovitSkeletonOverlay` لرسم `SkeletonDebugOverlayState` (angle arcs، scene badge نصي).
6. إضافة entry من debug shell / profile.

---

### 2026-06-14 — Agent 3 (Feature Module + Android Debug Lab)

#### Phase D1 — Common Debug Analyzer ✅

| الملف | الغرض |
|---|---|
| `:feature:training-debug` module | Gradle + `settings.gradle.kts` |
| `TrainingDebugModels.kt` | config، frame input، diagnostics DTOs |
| `TrainingDebugAnalyzer.kt` | PoseFrameAssembler + Elbow + Position + Scene + SetupReadinessGate |
| `AngleDiagnosticsBuilder.kt` | raw/smoothed/world angle frames + mirror |
| `ElbowDiagnosticsAdapter.kt` | يربط `ElbowAngleEstimator.lastDiagnostics` |
| `DebugPositionCheckFactory.kt` | synthetic `debug_check` |
| `TrainingDebugOverlayMapper.kt` | → `SkeletonDebugOverlayState` |
| `TrainingDebugExportFormatter.kt` | clipboard text + JSON snapshot |
| `TrainingDebugViewModel.kt` | events + dispatch + FPS |
| `TrainingDebugRoute.kt` + `PlatformTrainingDebugScreen` | KMP entry |

**Tests:** `TrainingDebugAnalyzerTest`, `AngleDiagnosticsBuilderTest`, `DebugPositionCheckFactoryTest` — **PASS** (`:feature:training-debug:testDebugUnitTest`).

#### Phases D3–D6 — Android Live Debug Lab ✅ (أساسي)

| الملف | الغرض |
|---|---|
| `TrainingDebugActivity` | `app/src/debug` — adb entry |
| `ui/TrainingDebugScreen.kt` | Compose: camera/video/image، toolbar، settings sheet، tabs |
| `TrainingDebugCameraHost.android.kt` | CameraX + `AndroidDebugCameraPoseSource` |
| `TrainingDebugOverlay.kt` | skeleton + position line + scene badge |
| `AndroidManifest.xml` (debug) | `com.movit.debug.TrainingDebugActivity` exported |

- Camera: live stream، flip + `resetAnalysisState`
- Tabs: ANGLE / POSITION / CAMERA_SCENE / SETUP_GATE
- Copy/export عبر ViewModel → clipboard
- FPS counters (source / inference / analysis / skipped busy)

#### Phase D7 — Image Mode ✅ (أساسي)

| الملف | الغرض |
|---|---|
| `AndroidDebugImagePoseSource.kt` | picker URI، EXIF rotation، IMAGE running mode، reanalyze on settings |

#### Phase D8 — Video Mode ✅ (أساسي)

| الملف | الغرض |
|---|---|
| `AndroidDebugVideoPoseSource.kt` | Media3 ExoPlayer + TextureView، play/pause/seek/reset، deterministic 33ms frames، backpressure counter |

#### ملاحظات تقنية

- `AndroidDebugMediaPipeLandmarker` مؤقت — **Agent 4** يستبدله بـ `MediaPipeSyncPoseDetector` (Agent 2).
- iOS: `PlatformTrainingDebugScreen.ios.kt` — camera فقط؛ image/video placeholder.
- إصلاح عرضي: `MovitSkeletonOverlay` `String.format` → KMP-safe للسماح بـ metadata compile.

#### Acceptance Checklist (جزئي)

- [x] Debug Lab يفتح في Android debug build (`adb shell am start -n com.trainingvalidator.poc/com.movit.debug.TrainingDebugActivity`)
- [x] Camera mode + flip + permission
- [x] Image mode + EXIF + reanalyze
- [x] Video mode + controls + seek reset
- [ ] Full/Heavy selector يغيّر detector فعلياً (يحتاج Agent 4 + PoseModelTypePort wiring)
- [x] Angle tab multi-joint + diagnostics + elbow section (عبر `ElbowDiagnosticsAdapter`)
- [x] Position tab synthetic check + statuses
- [x] Camera/Scene + Setup Gate probe
- [x] Copy/export نصي
- [x] common analyzer بلا Android imports
- [x] Android media في androidMain فقط
- [ ] iOS route كامل (Agent 4 / D10)
- [x] unit tests diagnostics أساسية
- [x] `:feature:training:testDebugUnitTest` لا regressions

---

## 12) Implementation Log

### 2026-06-14 — Agent 1 (Engine & Design Foundation)

#### Phase D0 — Legacy Baseline Inventory

**مصادر Legacy المقروءة:**

| الملف | الغرض |
|---|---|
| `DebugActivity.kt` | تدفق التحليل، tabs، export نصي، ربط `elbowAngleEstimator.lastDiagnostics` |
| `ElbowAngleEstimator.kt` (legacy analysis) | منطق التصحيح + `ElbowDiagnostics` |
| `dialog_debug_settings.xml` | controls: Info Panel، Input (Camera/Video/Image)، Pick File، Model (Full/Heavy)، Tabs (Angle Lab / Positions / Scene)، إعدادات كل tab |

**`dialog_debug_settings.xml` — عناصر التحكم:**

| Control | ID / Label | ملاحظة |
|---|---|---|
| Info Panel toggle | `switchDebugInfo` | إظهار/إخفاء لوحة التشخيص |
| Input Source | `inputModeGroup` — Camera / Video / Image | يغيّر المصدر ووضع scale للـ overlay |
| Pick File | `btnPickFile` | فيديو أو صورة حسب الوضع |
| Detection Model | `modelToggleGroup` — Full / Heavy | |
| Test Mode tabs | `tabLayout` — Angle Lab / Positions / Scene | |
| Angle: multi-joint | `btnSelectJoints`, `tvSelectedJointsSummary` | |
| Position: tilt | `switchTiltCorrection` | camera فقط في Legacy |
| Position: type/landmarks/operator/threshold | spinners + `etThreshold` | default threshold `0.05` |
| Scene: posture/direction/region | `spinnerExpectedCamPos`, `spinnerExpectedDirection`, `spinnerExpectedRegion` | |

**Golden outputs المتوقعة (Elbow pipeline — من `appendElbowPipelineDiagnostics`):**

```
--- ELBOW PIPELINE ---
Strategy:      <STRAIGHT|TRUST_3D|TRUST_2D|MILD_DOWN|DEEP_DOWN|LOW_CONF|HOLD>
Facing ratio:  %.3f (1=front 0=side)
Screen 2D:     <angle>
World 3D:      <angle>
dzShare:       UA=%.3f  FA=%.3f  max=%.3f
Correction:    %.0f%%
Output:        <angle>
Holding:       YES|no
```

**قرارات baseline:**

- KMP يحتفظ بنفس أسماء الاستراتيجيات كـ `enum` (`ElbowCorrectionStrategy`) مع `.name` مطابق لنص Legacy.
- `lastDiagnostics` مصفوفة `[left=0, right=1]` كما في Legacy؛ الوصول عبر `left_elbow` / `right_elbow` joint codes في الـ analyzer.
- منطق التصحيح الحالي في KMP مطابق للـ legacy قبل هذه المرحلة؛ إضافة diagnostics side-effect فقط على `lastDiagnostics`.
- `SkeletonOverlayParityState` يبقى للتدريب؛ Debug overlay في ملف contract منفصل.

#### Phase D2 — Elbow Correction Diagnostics ✅

**ملفات جديدة/معدّلة:**

| الملف | التغيير |
|---|---|
| `core/training-engine/.../ElbowCorrectionDiagnostics.kt` | **جديد** — `ElbowCorrectionStrategy`, `ElbowCorrectionDiagnostics` |
| `core/training-engine/.../ElbowAngleEstimator.kt` | `lastDiagnostics` read-only؛ تعبئة diagnostics في كل فرع استراتيجية + HOLD |
| `core/training-engine/.../ElbowAngleEstimatorTest.kt` | **جديد** — 6 tests (parity output، STRAIGHT، TRUST_3D، HOLD، reset) |

**API للوكلاء الآخرين:**

```kotlin
// بعد ElbowAngleEstimator.correct(...)
estimator.lastDiagnostics[0]  // left elbow
estimator.lastDiagnostics[1]  // right elbow
// ElbowCorrectionDiagnostics.strategy.name → "STRAIGHT" | "TRUST_3D" | ... | "HOLD"
```

#### Overlay Contract (§5.6) ✅

**ملف جديد:** `core/designsystem/.../SkeletonDebugOverlayContract.kt`

| النوع | الغرض |
|---|---|
| `SkeletonDebugOverlayState` | حالة overlay موحّدة للـ Debug Lab |
| `DebugJointHighlight` | Angle tab — vertex + endpoints + زاوية |
| `DebugPositionLine` | Position tab — خط primary↔secondary + status |
| `DebugSceneOverlay` | Scene tab — direction/posture/region/match |
| `DebugOverlayScaleMode` | `FillCenter` (camera/video) / `FitCenter` (image) |

`SkeletonOverlayParityState` **لم يُمس**.

#### Tests

| Task | النتيجة |
|---|---|
| `:core:training-engine:testDebugUnitTest` — `ElbowAngleEstimatorTest` | **6/6 PASS** |
| `:core:designsystem:testDebugUnitTest` | **PASS** (لا tests جديدة؛ compile نظيف) |

**ملاحظة build:** بعد `clean` — `:core:training-engine:testDebugUnitTest`, `:core:pose-capture:testDebugUnitTest`, `:feature:training-debug:testDebugUnitTest`, `:feature:training:testDebugUnitTest` — **BUILD SUCCESSFUL**.

#### ما يحتاجه الوكلاء التاليون

| الوكيل | يعتمد على |
|---|---|
| **MovitSkeletonOverlay renderer** | قراءة `SkeletonDebugOverlayState` (contract جاهز؛ رسم arcs/lines pending) |
| **Android instrumentation** | smoke `TrainingDebugActivity` launch (لم يُضف بعد) |

---

### 2026-06-14 — Agent 4 (iOS D10 + QA/Tests/Docs D11)

#### Phase D10 — iOS Debug Path ✅ (live camera)

| الملف | الغرض |
|---|---|
| `MovitInnerRoute.TrainingDebugLab` | route مخفي في shell |
| `MovitInnerHost` + `MovitAppShellViewModel` | navigation + copy/share |
| `PlatformInfo.supportsTrainingDebugLab` | Android `BuildConfig.DEBUG` / iOS `Platform.isDebugBinary` |
| `MovitProfileScreen` | صف "Training Debug Lab" في debug/internal فقط |
| `PlatformTrainingDebugScreen.ios.kt` | Compose shell: tabs + camera + disabled image/video |
| `TrainingDebugCameraHost.ios.kt` | `IosCameraFrameSource` → `TrainingDebugFrameInput` |
| `IosTrainingDebugPoseSource.kt` | `TrainingDebugPoseSource` لـ pose-capture boundary (live فقط) |
| `TrainingDebugBuild.ios.kt` | `isMediaInputModeSupported() = false` — لا crash لـ image/video |

**القبول D10:** iOS debug build يرى نفس tabs؛ الكاميرا الحية تغذي `TrainingDebugAnalyzer`؛ image/video تظهر disabled state واضح.

#### Phase D11 — QA, Tests, Documentation ✅ (common)

| المهمة | النتيجة |
|---|---|
| `TrainingDebugAnalyzer.kt` | common analyzer مكتمل (angle/position/scene/setup) |
| `TrainingDebugViewModel.kt` | API موحّد لـ Android UI (Agent 3) + iOS |
| `TrainingDebugAnalyzerTest` | angle + elbow + position + scene + setup gate |
| `AngleDiagnosticsBuilderTest` | mirror + pipeline + `ElbowDiagnosticsAdapter` |
| `DebugPositionCheckFactoryTest` | synthetic vertical check PASS |
| Gradle tests | انظر جدول النتائج أدناه |
| Acceptance checklist + §12/§13 | هذا الملف |
| Audit cross-link | سطر في `Android-KMP-Training-Engine-Legacy-MO-vs-Current-Difference-Audit.md` §1 |

**نتائج الاختبارات (2026-06-14, بعد `gradlew clean`):**

| Task | النتيجة |
|---|---|
| `:core:training-engine:testDebugUnitTest` | **PASS** (يشمل `ElbowAngleEstimatorTest` 6 tests) |
| `:core:pose-capture:testDebugUnitTest` | **PASS** |
| `:feature:training-debug:testDebugUnitTest` | **PASS** (3 test classes) |
| `:feature:training:testDebugUnitTest` | **PASS** (no regressions) |
| Android instrumentation smoke | **لم يُنفَّذ** — يتطلب `TrainingDebugActivity` instrumented test (Agent 3 scope) |

**قرارات Agent 4:**

- iOS يستخدم نفس `TrainingDebugAnalyzer` و`TrainingDebugViewModel` دون fork منطقي.
- `PlatformTrainingDebugScreen` expect/actual: Android → `ui/TrainingDebugScreen` (Agent 3)؛ iOS → شاشة مبسطة مع placeholder لـ media modes.
- لم يُعدَّل `pose-capture` APIs (Agent 2) إلا عبر consumer `IosTrainingDebugPoseSource` في feature module.
- `AndroidDebugMediaPipeLandmarker` **محذوف** — استُبدل بمسار الإنتاج (انظر 2026-06-15 أدناه).

---

### 2026-06-15 — Debug/Training Pipeline Parity

**القرارات:**

- الزوايا في Debug Lab تُقرأ حصرياً من `PoseFrame.angles` بعد `PoseFrameAssembler.assemble()` — لا `calculateAngles` ولا `ElbowAngleEstimator` منفصل في `TrainingDebugAnalyzer`.
- الكاميرا الحية تعيد استخدام `CameraXFrameSource` + `MediaPipePoseDetector` عبر `CameraXFrameSource.setDebugFrameListener` (raw/smoothed للعرض فقط).
- Image/Video: `MediaPipeSyncPoseDetector.detect()` ثم `assemble()` — نفس معادلة الزوايا.
- `TrainingGateFactory` + `SetupProbeDefaults` في `core:training-engine`؛ `MovitTrainingEngine` يبني `PositionValidator` عبر الـ factory.
- `PoseFrameAssembler.lastElbowDiagnostics()` للـ elbow tab — instance واحدة مع التدريب.

**ملفات رئيسية:**

| الملف | التغيير |
|---|---|
| `CameraXFrameSource.kt` | `setDebugFrameListener(DetectionResult, PoseFrame)` |
| `AndroidDebugCameraPoseSource.kt` | Koin `CameraXFrameSource` + production throughput profile |
| `AndroidDebugImagePoseSource.kt` / `AndroidDebugVideoPoseSource.kt` | `MediaPipeSyncPoseDetector` + mapper |
| `TrainingDebugAnalyzer.kt` | مدخل `poseFrame`؛ إزالة estimator منفصل |
| `TrainingGateFactory.kt` / `SetupProbeDefaults.kt` | **جديد** — shared gates |
| `TrainingDebugFrameFactory.kt` | **جديد** — بناء `TrainingDebugFrameInput` |
| `AndroidDebugMediaPipeLandmarker.kt` | **محذوف** |

**Tests:** `PoseFrameAssemblerDebugParityTest`, `TrainingDebugAnalyzerTest.analyzerUsesPoseFrameAngles_fromAssembler`, تحديث `AngleDiagnosticsBuilderTest`.

**متبقٍ مقصود:**

- Image/video sync path لا يمر بـ `PoseRefiner` (الكاميرا الحية تمر) — فرق بسيط للعرض فقط.
- Setup tab يحمّل `exerciseSlug` من `MovitInnerRoute.TrainingDebugLab` / `TrainingDebugActivity.EXTRA_EXERCISE_SLUG` عبر `MovitData.trainingConfig.getExercise(slug)`؛ fallback = `SetupProbeDefaults`.
- `SetupReadinessGate` في Debug Lab يمرّر `DeviceTiltPort` عند تفعيل tilt (مثل `TrainingSessionViewModel`).
- iOS camera debug يستخدم `resolveTrainingCameraConfiguration()` (parity مع `TrainingSessionCameraHost`).
- no-pose من `CameraXFrameSource` يبلّغ debug listener ويصفّر overlay عبر `TrainingDebugViewModel.onFrame(null)`.
- iOS: لا raw/smoothed split — يستخدم `poseFrame` من مسار الكاميرا الإنتاجي.

---

## 13) Known Differences from Legacy

| المجال | Legacy (`DebugActivity`) | KMP الحالي | مقصود؟ |
|---|---|---|---|
| Entry | Profile/debug + manifest | Android: `TrainingDebugActivity` + Profile row؛ iOS: Profile row في debug binary فقط | نعم — لا يظهر في production |
| Image/Video iOS | N/A (Android-only legacy) | disabled UI + `isMediaInputModeSupported() = false` | نعم — MediaPipe iOS sync path غير جاهز |
| Overlay rendering | `SkeletonOverlayView` كامل | `SkeletonDebugOverlayState` contract؛ renderer في `MovitSkeletonOverlay` pending | نعم — مرحلة لاحقة |
| Rep/session counting | لا يعد reps | لا يعد reps؛ Setup Gate probe فقط | نعم — Debug Lab ≠ training session |
| Model selector UI | Full/Heavy toggle مباشر | `PoseModelTypePort` جاهز؛ UI يعرض label جزئياً | مؤقت — wiring Agent 2→3 |
| Video frame timing | ~33ms video-time | `TrainingDebugVideoFrameSelector` (common) | نعم — parity مقصود |
| Elbow diagnostics export | نص `appendElbowPipelineDiagnostics` | `PoseFrameAssemblerElbowDiagnostics` + `TrainingDebugExportFormatter` | نعم — نفس حقول الاستراتيجية من instance الإنتاج |
| Tilt correction | camera فقط عبر `PoseApp` | `DeviceTiltPort` في analyzer عند تفعيل toggle | نعم — KMP port بدل coupling |
| Temporary landmarker | `PoseLandmarkerHelper` monolith | **محذوف** — camera: `CameraXFrameSource` + `MediaPipePoseDetector`؛ image/video: `MediaPipeSyncPoseDetector` + `PoseFrameAssembler.assemble()` | نعم — parity مع التدريب |
| Angle extraction | Legacy helper + estimator منفصل | `TrainingDebugFrameInput.poseFrame.angles` من `PoseFrameAssembler` فقط | نعم |
| PositionValidator construction | inline في التدريب | `TrainingGateFactory` مشترك (تدريب + debug synthetic check) | نعم |
| Setup probe exercise | JSON ثابت في Activity | `exerciseSlug` اختياري من shell/deep link/Activity extra؛ fallback `SetupProbeDefaults` | نعم |
| Elbow diagnostics instance | `ElbowAngleEstimator` ثانية في Analyzer | `PoseFrameAssembler.lastElbowDiagnostics()` عبر `PoseFrameAssemblerElbowDiagnostics` | نعم |
