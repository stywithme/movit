# Android / KMP Mobile UI/UX — Phase 07: نقل محرك التدريب الحي إلى KMP + Compose (Android & iOS)

آخر تحديث: **2026-06-11**
الحالة: **قيد التنفيذ (OPEN — ~92% بنية · ~65% منتج — دفعة 07.8 مُغلقة على Windows/§7)** · **متبقي جهاز/Mac: MediaPipe iOS · smoke · Visual QA · E2E**
المالك: فريق الموبايل · المرجعية: بوابة [Pre-07](Android-KMP-Mobile-UI-UX-Phase-Pre-07-Stabilization-Plan.md) (P0 مغلق) + [تدقيق الواقع](Android-KMP-Mobile-Migration-Reality-Audit.md) (الأولوية 0)

المراجع:
- [الخطة المهنية الأم](Android-KMP-Mobile-UI-UX-Professional-Plan.md) — Phase 7 (camera/ML multiplatform boundary)
- [Pre-07 Stabilization](Android-KMP-Mobile-UI-UX-Phase-Pre-07-Stabilization-Plan.md) — العقود + Outbox + حدود المحرك (مُنجَز)
- [Backend-Contract-Matrix](Backend-Contract-Matrix.md) — endpoints التدريب السبعة في KMP
- [Data-Sync & Offline-First Plan](Android-KMP-Mobile-Data-Sync-And-Offline-First-Migration-Plan.md) — DS-4/DS-5/DS-6 (تفضيلات/تقارير/صوت)
- [Migration Reality Audit](Android-KMP-Mobile-Migration-Reality-Audit.md) — «الأولوية 0: وصل محرك التدريب»

---

## 0) ملخص تنفيذي للمدير

**Phase 07 هي «الكَيستون»**: نقل تجربة التدريب الحي بالكاميرا — قلب المنتج — من `TrainingActivity` القديمة (View/XML + Retrofit + Android-only) إلى مسار KMP/Compose واحد يعمل على Android وiOS من نفس الكود.

**لماذا الآن آمنة؟** بوابة Pre-07 أغلقت كل المجاهيل:

| الجاهزية | الدليل |
|----------|--------|
| العقود | 7 endpoints التدريب 🔴 موجودة في `MovitMobileApi` + contract tests خضراء |
| Offline | Outbox كامل (`WORKOUT_EXECUTION_UPLOAD` + planned start/complete/report) + replay عند عودة الشبكة + SQLDelight |
| حدود المحرك | `PoseFrame`/`Landmark`/`JointAngles` + عقود `CameraFrameSource`/`PoseDetector`/`AudioFeedbackPlayer` في commonMain — صفر CameraX/MediaPipe في common |
| المحرك العددي | `PhaseStateMachine` · `RepCounter` · `ScoreCalculator` · `VisibilityMonitor` · `BilateralController` · `OneEuroFilter` · `JointAngleCalculator` · `FeedbackScheduler/Policy/TimingPolicy` **منقولة ومُثبتة إنتاجياً** — legacy نفسه يفوّض إليها عبر wrappers |
| إثبات حي | مسار `ExerciseLive` POC يعدّ تكرارات سكوات حقيقية عبر `LiveExerciseRunner` على Android |

**ما الذي تنقله Phase 07 فعلاً؟** الطبقات الأربع المتبقية فوق المحرك العددي:
1. **التكوين**: `ExerciseConfig` الكامل من `training-config` (بدل `ExerciseBlueprintRegistry` المُثبَّت يدوياً لتمرينين).
2. **خط الإطارات الكامل**: تقييم 5-حالات للمفاصل + position/scene checks + start gate + التنسيق.
3. **تدفق الجلسة وUI**: setup → countdown → live HUD + skeleton overlay → pause/resume → rest/sets → completion — بـ Compose ونظام Movit.
4. **المنصّات**: CameraX+MediaPipe كـ actuals حقيقية على Android، وAVFoundation+MediaPipe-iOS على iOS، خلف نفس الحدود.

**القاعدة الحاكمة (من Pre-07): «Phase 07 = توصيل، لا اكتشاف».** وكل مدخل تدريب يُقطع نهائياً إلى KMP عند جاهزيته — **لا حلول وسط ولا مسارين متوازيين دائمين** (قرار 2026-06-09).

**النتيجة عند الإغلاق:** زر Start في أي مكان (Home/Train/Session/Library/Program) يفتح شاشة تدريب KMP واحدة؛ `LegacyTrainingLauncher` و`LaunchLegacyCameraTraining` يُحذفان؛ شجرة `trainingvalidator.poc.training/ui.train/overlay/pose/camera` تصبح قابلة للحذف؛ iOS يتدرّب من نفس الكود.

---

## 1) خريطة الفهم الكامل لمحرك التدريب (ما الذي ننقله بالضبط)

> نتيجة قراءة كود مباشرة (2026-06-11). هذه الخريطة هي «مصدر الحقيقة» لتقسيم العمل — أي ملف غير مذكور هنا اكتُشف لاحقاً يُضاف للجرد قبل نقله.

### 1.1 المسار الحي من الكاميرا إلى التكرار (legacy اليوم)

```text
CameraManager (CameraX 4:3 · أقصى FPS · أوسع zoom · KEEP_ONLY_LATEST)
  └─> PoseLandmarkerHelper (MediaPipe Tasks · FULL/HEAVY .task · LIVE_STREAM · GPU→CPU fallback
        · bitmap rotate+mirror لكل إطار · خريطة timestamp→isFrontCamera)
        └─> CameraTrainingInputController
              ├─ LandmarkSmoother (OneEuro/EMA من الإعدادات) على 2D + world(3D)
              ├─ AngleCalculator (يفضّل world 3D) ← يفوّض KMP JointAngleCalculator
              ├─ ElbowAngleEstimator (تصحيح مرفق + MLP اختياري)
              ├─ mirrored() للكاميرا الأمامية
              └─> TrainingViewModel.onPoseFrame
                    └─> WorkoutRunSupervisor (آلة حالات الجلسة)
                          ├─ SETUP_POSE/RESUME_SETUP → PoseSetupGuide (نافذة متدحرجة: مشهد → زوايا → تأكيد)
                          ├─ COUNTDOWN/RESUME_COUNTDOWN → CountdownController (تجميد/فك تجميد عند انحراف الوضعية)
                          ├─ TRAINING → TrainingEngine.processFrame (إسقاط إطارات على Dispatchers.Default)
                          └─ NO_POSE: صمت 1s → تحذير 2s → إيقاف تلقائي 4s
```

### 1.2 تشريح `TrainingEngine` (واجهة 840 سطراً فوق ~20 مكوّناً)

| المكوّن | الدور | الحالة في KMP `core:training-engine` |
|---------|------|----------------------------------------|
| `PhaseStateMachine` | أطوار IDLE/START/DOWN/BOTTOM/UP/COUNT + hysteresis | ✅ منقول — legacy يفوّض إليه |
| `RepCounter` | عدّ + جودة + نتائج التكرارات | ✅ منقول — legacy يفوّض إليه |
| `ScoreCalculator` | أوزان primary/secondary + عقوبة DANGER | ✅ منقول — legacy يفوّض إليه |
| `VisibilityMonitor` | grace/warn/pause + إحصاءات | ✅ منقول — legacy يفوّض إليه |
| `BilateralController` | تبديل الجانب + after-all-reps | ✅ منقول — legacy يفوّض إليه |
| `OneEuroFilter` / `JointAngleCalculator` | فلترة وحساب زوايا | ✅ منقول (parity tests) |
| `FeedbackPolicy` / `TimingPolicy` / `FeedbackScheduler` | كولداونات وأولويات الرسائل | ✅ منقول — legacy يفوّض إليه |
| `PauseController` / `HoldTimer` / `ExecutionSafetyGuards` / `ExecutionClock` / `SessionOrchestrator` | زمن/إيقاف/أمان | ✅ نسخ KMP مكتوبة ومُختبرة (WS-5) — **لكن legacy ما زال يشغّل نسخه الخاصة**؛ التوحيد يتم عند القطع |
| `JointAngleTracker` | استخراج زوايا المفاصل المتتبَّعة + قلب bilateral + مرايا الكاميرا | ❌ **يُنقل في WS-2** |
| `AngleSmoother` | تنعيم نافذة على الزوايا | ❌ يُنقل في WS-2 |
| `JointEvaluator` + `StateRanges/StateConfig` | تقييم 5-حالات (PERFECT…DANGER) لكل مفصل/zone | ❌ **قلب الجودة — WS-2** |
| `FramePipelineExecutor` / `FrameEvaluationPipeline` | ترتيب smooth→gate→phase→position→eval | ❌ يُنقل في WS-2 |
| `StartPoseGate` | بوابة وضعية البداية | ❌ يُنقل في WS-2 |
| `RepCompletionCoordinator` / `HoldExerciseCoordinator` / `RepCompletionSignal` / `ExerciseWorkoutSummaryBuilder` / `JointErrorCollection` / `FrameFeedbackEmitter` | تنسيق إكمال التكرار/الثبات + أخطاء المفاصل + ملخص | ❌ تُنقل في WS-2 |
| `PositionValidator` + `PoseSceneDetector` + `CameraPositionDetector` + `BodyPostureDetector` + `VisibleRegionDetector` + `PosePosition` | فحوص الوضعية/المشهد/اتجاه الكاميرا | ❌ **WS-3** (الجزء العددي common، والكشف المعزز بالمستشعر/ML خلف حدود) |
| `LandmarkTiltCorrector` + `DeviceTiltProvider` | تصحيح ميل الجهاز (مستشعر) | ❌ WS-3 — منطق common + `DeviceTiltPort` expect/actual |
| `PostureMlpClassifier` · `ElbowCorrectionMlpClassifier` · `ElbowFit3dV2Classifier` (+FeatureExtractors) | MLP صغيرة بـ `.tflite` (LiteRT) | ❌ WS-3 — خلف حدود `PoseRefiner` (Android actual فقط في v1) |
| `MotionRecorder` + `MetricsCalculator` | تسجيل الإطارات → RepMetrics/ExecutionMetrics (ROM/tempo/TUT/1RM…) | ❌ **WS-8** — common بالكامل (عددي خالص) |
| `PipelineTrace` | تتبّع تشخيصي | 🔶 موجود جزئياً — يُكمَّل في WS-2 |

### 1.3 ما حول المحرك (يُنقل أيضاً — كثيراً ما يُنسى)

| الكتلة | الملفات الرئيسية (أسطر) | الحالة في KMP | المسار |
|--------|--------------------------|----------------|--------|
| تدفق الجلسة | `WorkoutRunSupervisor` (542) · `PoseSetupGuide` (572) · `CountdownController` | ❌ (شبه نقية — Log/Settings فقط) | WS-5 |
| وضع الـ workout | `WorkoutTrainingEngine` (638) · `ProgramWorkoutRunner` · `TrainingWorkoutModeController` (1018) | ❌ | WS-5 |
| واجهة الجلسة | `TrainingActivity` (1078) + binders + `dialog_training_settings` + panels | ❌ | WS-6 |
| الرسم الحي | `SkeletonOverlayView` (**2261**) · `ArcRangeIndicator` (513) · `LineRangeIndicator` (723) · `ArcColorCalculator` · Vignette · Glassmorphic | ❌ | WS-6 |
| الصوت/الملاحظات | `FeedbackManager` (**1289**: TTS + كاش صوت + طابور أولويات + countdown + عشوائي تحفيزي) · `TtsVoiceSelector` · `MobileMessageResolver` · `AudioFeedbackPlayer` (450) | 🔶 KMP عنده: `FeedbackScheduler/Policy` + `MovitAudioPlayer` expect/actual + `AudioManifestCache` + `SystemMessageCache/Registry` + `AudioPrefetchRunner` | WS-7 |
| التقرير | `ReportGenerator` (1508) · `PostTrainingReport` (1459) · `PerformanceMetricsBuilder` (634) · `FrameCaptureManager` (597) · `QuickInsightGenerator` | ❌ (صفحة Report Detail 17 موجودة KMP) | WS-8 |
| المزامنة | `WorkoutSyncService` (طابور OkHttp) · planned-workouts start/complete/report (Retrofit) | ✅ بنية: `MovitData.mobileWrites` + Outbox — **التوصيل من UI هو المتبقي** | WS-8 |
| التكوين | `ExerciseConfig` (1075) + `JointState/StateRanges` (1092) + `ExerciseRepository`/`SyncManager` | ❌ (`training-config` يمر JsonElement خام؛ blueprint POC تمرينين) | **WS-1** |
| الإعدادات | `SettingsManager` (507 — model/indicator/voice/coach/smoothing/setup thresholds) | ❌ | WS-1 (نموذج مشترك) |
| الكاميرا/ML | `CameraManager` (261) · `PoseLandmarkerHelper` (551) | 🔶 ملفوفة مؤقتاً بجسر يدوي (`KmpTrainingSessionBridge` + `LegacyKmpTrainingSessionFactory` في app) | **WS-4** |
| المداخل | `LegacyTrainingLauncher` · `LaunchLegacyCameraTraining` · `ProgramWorkoutActivity` (1944) · `PreWorkoutActivity` · `WorkoutRunActivity` · `ExerciseDetailActivity` · `AssessmentSessionActivity` | 🔶 shell الأربعة → KMP (`TrainingSessionRoute`) مع `movit.training.kmp.enabled`؛ legacy Activities تبقى لمسار non-shell | WS-10 |
| Segmentation للتقرير | `MattingEngine` · MediaPipe/ONNX matting | ❌ | **مؤجَّل بقرار** (D9) |

### 1.4 جرد تدفقات UX الحية (Definition of Parity)

كل بند هنا إمّا يُنقل أو يُعلَن مؤجلاً بقرار مكتوب — لا إسقاط صامت:

1. Setup pose: مشهد (region/posture/direction) → زوايا → نافذة متدحرجة → تأكيد صوتي؛ progress % + إرشاد لأسوأ مفصل + إرشاد كاميرا.
2. Countdown صوتي 3-2-1 + «Go» مع **تجميد/فك تجميد** عند انحراف الوضعية وإلغاء بعد مهلة.
3. HUD حي: عدّاد hero + طور + حالة الفورم + زمن منقضٍ + تقدّم % + شريط سفلي.
4. Skeleton overlay: وصلات/مفاصل بألوان الحالة، توهّج عند PERFECT، تعتيم مفاصل any-side، مؤشرات ROM (خط/قوس)، قيم زوايا، نداءات أخطاء position.
5. Vignette أحمر عند خطأ + رسائل glassmorphic (حالة مفصل/position/تحفيزي عشوائي/تحذيرات مشهد) بكولداونات.
6. إيقاف/استئناف: يدوي (استئناف عبر countdown) · NoPose (تحذير 2s → إيقاف 4s) · Visibility (سماحية → تحذير → إيقاف + عدّ تنازلي auto-resume) · Activity background.
7. تمارين الثبات HOLD: مؤقّت + grace period بحالات لونية + إنهاء.
8. Bilateral: تبديل جانب + after-all-reps + قلب التتبّع.
9. حوار ما قبل التدريب (reps/duration/weight من تفضيلات المستخدم) + تعديل الوزن أثناء الجلسة.
10. وضع Workout: لوحة pre-exercise (صورة/تعليمات/أهداف/overrides/عدّ rest تمهيدي) → جلسات sets → rest بين sets/تمارين (نصائح + skip + تنبيه صوتي قرب النهاية) → مؤشر set → احتفالات → exit confirm → workout complete.
11. الإكمال: توليد تقرير + التنقّل له + مزامنة (planned report / workout-executions) + **وضع Assessment** (إرجاع نتيجة بلا صفحة تقرير).
12. الكاميرا: إذن + تبديل أمامي/خلفي + موديل FULL/HEAVY + FPS counter (debug).
13. أمان: max reps/duration guards + سلوك انقطاع المعالجة.
14. تعريب كامل ar/en + RTL (ملاحظة: legacy فيه نصوص أطوار مُثبَّتة إنجليزية — تُصحَّح في الجديد عبر `core:resources`).

---

## 2) المبادئ الحاكمة (غير قابلة للتفاوض)

1. **لا تغيير لعقد الباك اند.** نفس مسارات/حقول legacy حرفياً (مُثبَّتة بـ contract tests من Pre-07).
2. **لا حلول انتقالية معمارية.** الجسر اليدوي الحالي (`KmpTrainingSessionBridge.factory` + لفّ كاميرا legacy) **يُستبدل** بـ actuals حقيقية + Koin — لا يبقى كطبقة دائمة. القطع لكل مدخل تدريب يكون كاملاً (KMP أو legacy، لا خليط داخل المدخل الواحد).
3. **commonMain لا يرى المنصّة.** فقط `PoseFrame`/`Landmark`/`JointAngles` + عقود الحدود. صفر `android.*`/CameraX/MediaPipe في common (فحص آلي قائم).
4. **Parity قبل التحسين، والتحسين موثَّق.** أي سلوك يتغيّر عن legacy يُسجَّل في «سجل فروق السلوك» بهذه الخطة — لا انحراف صامت.
5. **مصدر زمن واحد.** كل المكوّنات تقرأ من `ExecutionClock` (frame-time أولاً) — لا `SystemClock`/`System.currentTimeMillis` متناثرة.
6. **الكتابات عبر Outbox فقط.** أي حفظ جلسة/تقرير من UI الجديد يمرّ بـ `MovitData.mobileWrites` (offline-safe) — يغلق الفجوة المعلَّقة من Pre-07.
7. **iOS-green إلزامي لكل WS:** `:core:training-engine` + `:feature:training` تكمبّل `iosSimulatorArm64` في CI عند كل دمج — «Android-green ≠ Done».

---

## 3) القرارات المعمارية (تُحسم في WS-0 وتُسجَّل هنا)

| # | القرار | التوصية + السبب |
|---|--------|------------------|
| **D1** | موديول الواجهة الحية | **إنشاء `feature:training` جديد.** POC الحالي وُضع في `feature:library` لتقليل التبعيات، لكن حجم Phase 07 (overlay + session + feedback + workout mode ≈ أكبر feature في التطبيق) يبرر موديولاً مملوكاً. `library` يُبقي الكتالوج/التدفقات؛ `training` يملك الجلسة الحية. يُنقل `ExerciseLive*` الحالي إليه. |
| **D2** | موديول المنصّات | **إنشاء `core:pose-capture`**: androidMain = CameraX+MediaPipe (نقل `CameraManager`+`PoseLandmarkerHelper` المحسّنين) · iosMain = AVFoundation+MediaPipe-iOS. يُبقي تبعيات ML خارج `core:training-engine` وخارج `app`، ويجعل `app` بلا أي دور في التدريب الجديد. |
| **D3** | ML الوضعيات على iOS | **MediaPipe Tasks Vision iOS (نفس موديلات `.task`) وليس Apple Vision.** Apple Vision يُخرج 19 نقطة بتسميات مختلفة بينما كل التكوينات/العتبات/الـ position checks مبنية على 33 نقطة BlazePose — استخدام Vision يكسر parity ويستلزم إعادة معايرة كاملة. التنفيذ عبر cinterop/CocoaPods أو غلاف Swift يُسجَّل في Koin عند الإقلاع (يُحسم تقنياً في WS-9 spike). |
| **D4** | تكوين التمرين | **`ExerciseConfig` typed في commonMain بـ kotlinx.serialization** (مع serializer مخصص لـ `StateMessageValue` بدل Gson TypeAdapter). المصدر: `GET training-config` (payload الموجود) + كاش `MovitLocalStore` للأوفلاين. **يُحذف `ExerciseBlueprintRegistry` POC.** fixtures حقيقية من الباك اند تقفل الـ parity (نمط WS-6 من Pre-07). |
| **D5** | مصنّفات MLP (posture/elbow `.tflite`) | خلف حدود اختيارية **`PoseRefiner`** (expect/actual): Android actual = LiteRT (الكود القائم يُغلَّف)؛ iOS v1 = بدون (fallback هندسي قائم أصلاً في legacy عند غياب المصنّف). توثَّق كفرق سلوك iOS مؤقت. (بديل مستقبلي مدروس: تصدير الأوزان وتنفيذ forward-pass خالص في common — مؤجَّل.) |
| **D6** | DI | **Koin** لكل حدود التدريب (CameraFrameSource/PoseDetector/PoseRefiner/SpeechSynthesizer/HapticsPort/DeviceTiltPort/MovitAudioPlayer) في `MovitData.install` أو `MovitTraining.install` — **حذف** singletons اليدوية (`KmpTrainingSessionBridge.factory` و`KmpAssessmentSessionBridge.factory`). تذكير iOS: لا `GlobalContext` (JVM-only). |
| **D7** | التنعيم والـ 3D | نقل التنعيم إلى commonMain (OneEuro موجود): المنصّة تسلّم **landmarks خام + world landmarks** في `PoseFrame` (يُضاف حقل `worldLandmarks: List<Landmark>?` — اليوم مفقود)، وcommon يتولى smoothing + حساب الزوايا (3D عند توفّر world كما في legacy) + mirroring. يضمن سلوكاً متطابقاً حرفياً بين المنصّتين. |
| **D8** | التقرير | لا نقل لـ `WorkoutReportActivity` legacy. ملخص الجلسة يُبنى في common (`MovitSessionReport`) → يُعرض في **صفحة Report Detail (17) الحالية** + يُرفع بنفس JSON الذي يولّده legacy (`ReportGenerator` schema) للحفاظ على عقد `report` في planned-workouts. Frame captures v1 = بدون segmentation. |
| **D9** | Segmentation/matting للتقارير | **مؤجَّل بقرار** — ليس على مسار الجلسة الحرج. يُسجَّل دين واضح. |
| **D10** | وضع الفيديو (video mode في supervisor) | **خارج نطاق Phase 07** (الكاميرا أولاً). إشارات الفيديو تبقى في تصميم الآلة المنقولة (cheap) لكن بلا UI. يُحسم مستقبله مع المنتج. |
| **D11** | إستراتيجية القطع | قطع **لكل مدخل** خلف flag تشغيلي واحد `movit.training.kmp.enabled` (نمط Phase 06 الناجح): يُفعَّل عند اجتياز بوابة القبول لكل مدخل، ويُحذف الـ flag + كود legacy في WS-10. الـ flag أداة إطلاق/rollback تشغيلية، **ليس** جسراً معمارياً. |

---

## 4) مسارات العمل

> الترميز: 🔴 حرج للمسار · 🟠 مهم · 🟡 تحسين/إغلاق. كل WS له «بوابة قبول» يجب اجتيازها قبل اعتبارها مغلقة. أوامر التحقق في §7.

### WS-0 — التأسيس والقرارات 🔴 (أسبوع البداية)

**الهدف:** حسم D1–D11 + إنشاء الهياكل + مواصفة بصرية للشاشة الحية (لا يوجد prototype HTML لها — تُكتب Spec من legacy + نظام Movit).

**العمل:**
1. إنشاء `feature:training` + `core:pose-capture` (هياكل + Gradle + iOS targets + فحص نظافة commonMain).
2. تسجيل القرارات D1–D11 أعلاه بحالة «مُعتمد» + أي تعديلات.
3. كتابة `Training-Live-Screen-Spec.md` (في `Page-Specs/`): تخطيط الشاشة بحالاتها الـ 9 (setup/countdown/live/paused/auto-paused/resume-setup/resume-countdown/rest/complete) بمكوّنات Movit (يُضاف `MovitScoreRing` · `MovitActionDock` · `MovitGlassMessage` للـ design system).
4. **بنية تحتية للـ parity الذهبي**: امتداد `ParityRunner`/`FrameFixture` legacy لتسجيل جلسات حقيقية كـ JSONL (landmarks+timestamps) → fixtures في `core:training-engine/commonTest` تُعاد تغذيتها للمحرك الجديد وتُقارن (reps/phases/scores/أحداث) بنتائج legacy المسجلة. **هذه أهم أداة ضبط جودة في الفيز كلها.**
5. تحديث `Docs-Stats-Snapshot` (العدّاد قديم: يعرض 18 بينما الواقع 58 لاختبارات المحرك) — `.\gradlew.bat docsStats`.

**القبول:** الموديولان يكمبّلان Android+iOS فارغين ضمن CI · المواصفة مُراجعة · أول golden fixture مسجَّل من legacy ويُعاد تشغيله.

---

### WS-1 — التكوين المشترك: `ExerciseConfig` typed 🔴 (يفتح كل شيء)

**الهدف:** المحرك الجديد يقرأ **نفس** تكوين الباك اند الذي يقرأه legacy — كل التمارين، لا قائمة مُثبَّتة.

**العمل:**
1. تعريف نموذج `commonMain` كامل في `core:training-engine/config`: `ExerciseConfig` · `PoseVariant` · `TrackedJoint` · `StateRanges/StateConfig` (5-حالات × up/down/hold zones) · `RepCountingConfig` · `BilateralConfig` · `PositionCheck/Condition/LandmarkGroup` · `FeedbackMessages/MessageAssignment/StateMessages(+StateMessageValue serializer)` · `LocalizedText(+audio refs)` · `ReportMetricsConfig` — بـ kotlinx.serialization، مطابقة 1:1 لحقول JSON التي يفكّكها Gson اليوم.
2. Parser من `TrainingConfigApiResponse.data` (JsonElement) → النموذج، مع اختبارات على **fixtures حقيقية** (سكوات + تمرين hold + تمرين bilateral + تمرين بـ position checks على الأقل).
3. `TrainingConfigRepository` في `core:data`: fetch + كاش `MovitLocalStore` (drift عبر `MovitSyncOrchestrator`) + قراءة أوفلاين باردة؛ نفس دلالات `ExerciseRepository.getExercise(slug)`.
4. تفضيلات التدريب المشتركة `MovitTrainingPreferences` في `core:data` (بديل `SettingsManager` للجلسة): model type · indicator type · voice on/off · coach intensity · smoothing params · setup thresholds — Flow تفاعلي فوق `MovitLocalStore`؛ القيم الافتراضية = قيم legacy الحالية حرفياً.
5. **حذف** `ExerciseBlueprintRegistry`/`BlueprintJointConfig` POC بعد تحويل `LiveExerciseRunner` للنموذج الجديد.

**القبول:** fixtures الأربعة تتفكّك بلا فقد (مقارنة حقل-بحقل مع Gson legacy في اختبار آلي) · `ExerciseLive` يعمل بسكوات **من التكوين الحقيقي** لا الـ blueprint · iOS compile أخضر.

---

### WS-2 — إكمال محرك الإطارات في commonMain 🔴

**الهدف:** `MovitTrainingEngine` مكافئ وظيفياً لواجهة `TrainingEngine` legacy — يستهلك `PoseFrame` ويُخرج نفس الحالات/الأحداث.

**العمل:**
1. نقل المكوّنات غير المنقولة (خريطة §1.2): `JointAngleTracker` · `AngleSmoother` · `JointEvaluator` (+`hasAnyDangerState`) · `StartPoseGate` · `FramePipelineExecutor`/`FrameEvaluationPipeline` · `RepCompletionCoordinator` · `HoldExerciseCoordinator` · `RepCompletionSignal` · `JointErrorCollection` · `FrameFeedbackEmitter` · `ExerciseWorkoutSummaryBuilder` — **فوق نسخ KMP القائمة** (`SessionOrchestrator`/`PauseController`/`HoldTimer`/`ExecutionClock`/`ExecutionSafetyGuards`) لا فوق نسخ legacy.
2. بناء `MovitTrainingEngine` (الواجهة العامة): نفس StateFlows legacy (`currentPhase/repCount/jointStateInfos/isDangerActive/isInStartPosition/isCompleted/currentAngles/anySideDimmedJointCodes/positionErrors/sceneWarnings/visibilityState/holdStatus/events…`) + `processFrame(PoseFrame)` + `start/pause/resume/stop` — لكن **مفكّكة داخلياً** (انظر تحسين I-2): `FramePipeline` نقي + `SessionRuntime` + `FeedbackRouter`.
3. إضافة `worldLandmarks` إلى `PoseFrame` + نقل منطق 3D للزوايا + `mirrored()` + (هندسيات `ElbowAngleEstimator` غير-ML).
4. توسيع `LiveExerciseRunner` → أو استبداله بـ `MovitTrainingEngine` مباشرة (الـ runner الحالي مبسّط).
5. **Golden replay parity:** نفس الـ fixtures من WS-0 تمر بالمحرك الجديد كاملاً؛ التطابق المطلوب: عدد reps المحسوبة/الملغاة، تسلسل الأطوار، score لكل rep (±0.5)، أحداث danger/visibility، نتيجة hold.
6. تغطية `commonTest` للوحدات المنقولة (نمط parity tests القائم).

**القبول:** golden replay أخضر لكل الـ fixtures · صفر import منصّة في common (الفحص الآلي) · iOS compile · تقرير فروق سلوك = فارغ أو مُعتمد بنداً بنداً.

---

### WS-3 — فحوص الوضعية/المشهد + حدود المستشعر وML 🟠

**الهدف:** `PositionValidator` بكامل سلوكه (أخطاء/تحذيرات/نصائح/scene lock/كولداونات) في common، مع عزل ما هو منصّة.

**العمل:**
1. نقل `PositionValidator` + `PoseSceneDetector` + `CameraPositionDetector` + `BodyPostureDetector` + `VisibleRegionDetector` + `PosePosition/PoseSceneExpectation` إلى common (منطقها هندسي خالص فوق landmarks).
2. `DeviceTiltPort` expect/actual (Android: `DeviceTiltProvider` القائم بنمط acquire/release بالمالك؛ iOS: CoreMotion في WS-9) + نقل `LandmarkTiltCorrector` إلى common.
3. `PoseRefiner` boundary (D5) + غلاف LiteRT أندرويد للـ MLPs الثلاثة في `core:pose-capture` androidMain؛ common يستدعيه اختيارياً (نفس fallback legacy عند غيابه).
4. توصيلها داخل `FramePipelineExecutor` بنفس ترتيب legacy (التحقق حتى أثناء counting suspended — السلوك الدقيق في §1.1).

**القبول:** اختبارات position/scene على fixtures (تمرين بـ position checks) تطابق legacy · سيناريو «مشهد خاطئ → scene warnings → تصحيح → lock» مُختبر · iOS compile (الـ refiner غائب بأمان).

---

### WS-4 — منصّة الالتقاط: actuals حقيقية في `core:pose-capture` 🔴

**الهدف:** تنفيذ `CameraFrameSource`/`PoseDetector` الحقيقيين على Android، وحذف الجسر اليدوي.

**العمل:**
1. **Android `CameraXFrameSource`**: نقل `CameraManager` (4:3 · أقصى FPS · أوسع zoom + retry · KEEP_ONLY_LATEST · تبديل عدسة) إلى `core:pose-capture/androidMain` خلف العقد المشترك؛ تسليم الإطارات بـ back-pressure موحّد (تحسين I-5: نقطة إسقاط واحدة).
2. **Android `MediaPipePoseDetector`**: نقل `PoseLandmarkerHelper` مع تحسين I-4 (تمرير الدوران عبر `ImageProcessingOptions` بدل إعادة رسم bitmap لكل إطار حيث يدعم المسار، وتنظيف خريطة timestamp→camera) — يلتقط 33 landmark + world landmarks ويبني `PoseFrame` عبر `PoseFrameAssembler` (مع smoothing في common حسب D7).
3. ربط كل شيء عبر **Koin** (D6): platform module يسجّل المصادر؛ `feature:training` يستقبل عبر constructor injection. **حذف**: `KmpTrainingSessionBridge` · `LegacyKmpTrainingSessionFactory` · `TrainingBoundaryInstall` (يتحول لتسجيل Koin) — ومثلها جسر assessment.
4. Compose host: `TrainingCameraSurface` (AndroidView/PreviewView) في `feature:training` androidMain + إدارة الإذن بمكوّن مشترك الواجهة.
5. أداة قياس أداء بسيطة (FPS التحليل + زمن المعالجة لكل إطار) خلف debug flag — خط أساس قبل/بعد لإثبات عدم التراجع عن legacy.

**القبول:** جلسة سكوات كاملة E2E على جهاز Android عبر المسار الجديد بالكامل (بدون أي كود `trainingvalidator` في runtime) · FPS التحليل ≥ خط أساس legacy على نفس الجهاز · صفر استخدام للجسر اليدوي في الشجرة.

---

### WS-5 — تدفق الجلسة + وضع الـ workout في common 🔴

**الهدف:** آلة حالات الجلسة الكاملة وUX التمهيد في commonMain بنمط UDF.

**العمل:**
1. نقل `WorkoutRunSupervisor` (نقي تقريباً) مع توحيد سياسة NoPose مع `VisibilityMonitor` (تحسين I-14: مصدر واحد للحقيقة بدل نظامين متداخلين) — القرار الموحّد يُسجَّل كفرق سلوك مُعتمد.
2. نقل `PoseSetupGuide` (نافذة متدحرجة + مراحل scene→angles + إرشاد المفاصل + كاميرا) ودمج `StartPoseGate`/فحص countdown في مكوّن واحد (تحسين I-15).
3. نقل `CountdownController` (تجميد/فك/إلغاء + مزود صوت عبر منفذ).
4. نقل دلالات `WorkoutTrainingEngine` + `ProgramWorkoutRunner` (sets/rest/per-set metrics/exercise reports/phase roles WARMUP..) إلى `TrainingSessionFlowCoordinator` مشترك.
5. `TrainingSessionViewModel` (KMP lifecycle ViewModel): State/Event/Effect كاملة لكل حالات §1.4 — تستهلك `MovitTrainingEngine` + الـ supervisor + المنافذ.

**القبول:** اختبارات آلة الحالات (تتابعات: setup→countdown→training→auto-pause→resume-setup→resume-countdown→training→complete · وworkout: pre→set1→rest→set2→exercise2→complete) خضراء في commonTest · iOS compile.

---

### WS-6 — الواجهة الحية Compose (نظام Movit) 🔴

**الهدف:** شاشة التدريب الحي وworkout flow بجودة الإنتاج — بديل `TrainingActivity` بصرياً وسلوكياً وفق Spec من WS-0.

**العمل:**
1. **`MovitSkeletonOverlay`** (Compose Canvas مشترك): طبقات منفصلة قابلة للاختبار — Connections/Joints (ألوان حالة + توهج perfect + تعتيم any-side) · مؤشرات ROM خط/قوس (نقل حسابات `ArcRangeIndicator`/`LineRangeIndicator`/`ArcColorCalculator` كدوال هندسية common) · ملصقات زوايا · نداءات position errors · إرشاد setup (هيكل هدف + أسهم لكل مفصل). قاعدة أداء: صفر allocation لكل إطار (إعادة استخدام Path/state holders) + قياس على جهاز متوسط.
2. مكوّنات الجلسة: `TrainingHud` (hero counter/score ring/phase chip/elapsed/progress) · `SetupPosePanel` (+progress) · `CountdownOverlay` (+حالة التجميد بصرياً — تحسين I-26) · `VignetteEffect` · `MovitGlassMessage` (رسائل الملاحظات) · `HoldHud` · `RestPanel` (نصائح/skip/تنبيه) · `PreExercisePanel` · `WorkoutCompletePanel` · `SetIndicator` · حوارات كـ Movit bottom sheets (إعدادات الجلسة/الوزن/الخروج).
3. ربطها بـ `TrainingSessionViewModel` في `MovitTrainingSessionRoute` + إدخالها في `MovitInnerRoute` (استبدال `ExerciseLive` البسيطة).
4. تعريب كامل (نصوص الأطوار/الإرشادات/النصائح عبر `core:resources` ar/en) + RTL + font scale 200% + a11y (وصف أزرار، إعلانات reps عبر semantics — تحسين I-30).
5. Light/Dark + لقطات QA حسب [Visual-QA-Checklist](Android-KMP-Mobile-Visual-QA-Checklist.md).

**القبول:** Definition of Done البصري للخطة الأم + مطابقة الـ Spec لقطة-بلقطة على small/standard phone + RTL/Dark · بقية بنود §1.4 ملموسة يدوياً على جهاز.

---

### WS-7 — الملاحظات: صوت/اهتزاز/رسائل 🟠

**الهدف:** parity مع `FeedbackManager` بتفكيك نظيف: قرار الرسالة في common، التشغيل في المنصّة.

**العمل:**
1. `FeedbackArbiter` في common (يبني على `FeedbackScheduler`/`FeedbackPolicy` القائمة): أولويات/كولداونات/dedupe/عشوائي تحفيزي/قنوات (صوت/بصري/اهتزاز) + coach intensity (تحسين I-17) — منطق `FeedbackManager` العددي يُنقل هنا.
2. منافذ: `SpeechSynthesizer` expect/actual (Android TTS بقواعد `TtsVoiceSelector`؛ iOS `AVSpeechSynthesizer` في WS-9) · `HapticsPort` · تشغيل ملفات عبر `MovitAudioPlayer` القائم.
3. **إكمال مسار ملفات الصوت (DS-6):** ربط `AudioManifestCache` + `AudioFileDownloader` + `AudioPrefetchRunner` القائمين بمسار الجلسة: prefetch عند فتح الجلسة (audio-manifest endpoints موجودة) + fallback TTS عند غياب الملف (نفس سلوك legacy) + سياسة حجم/تنظيف بدلالات `AudioCacheManager`.
4. أصوات النظام: countdown/Go/pose-confirmed/rest-end عبر `SystemMessageRegistry/Cache` القائمة.

**القبول:** جلسة بصوت كامل (countdown → رسائل حالة → تحفيزي → إكمال) أوفلاين بعد prefetch وأونلاين بدونه (TTS) · اختبارات الـ arbiter (أولوية/كولداون/قناة) خضراء.

---

### WS-8 — التسجيل والقياسات والتقرير والمزامنة 🔴 (يُغلق ديْن Pre-07)

**الهدف:** الجلسة الجديدة تُنتج نفس بيانات legacy وتكتبها offline-safe — **هذا البند كان شرط إغلاق Outbox المعلَّق**.

**العمل:**
1. نقل `MotionRecorder` + `MetricsCalculator` إلى common (عددي خالص): FrameSamples → RepRecords → `RepMetrics`/`ExecutionMetrics` (ROM/tempo/stability/velocity/symmetry/TUT/volume/1RM/fatigue…) — أنواع الإخراج تطابق `RepMetricsDto`/`ExecutionMetricsDto` القائمة في `core:network`.
2. **Session journal** (تحسين I-22): checkpoint دوري للجلسة في `MovitLocalStore` (rep-level append) — انقطاع التطبيق منتصف الجلسة لا يفقدها (legacy يفقدها اليوم: `MotionRecorder` في الذاكرة فقط). فرق سلوك إيجابي مُسجَّل.
3. بناء `MovitSessionReport` (يُسلسَل بنفس schema حقل `report` الذي يرسله legacy في planned-workouts/report — مُثبَّت بفixture مقارن) + خرائط لعرضه في Report Detail (17).
4. **توصيل الكتابات من الـ ViewModel**: `mobileWrites.startPlannedWorkout` عند بدء planned · `uploadWorkoutExecution` لكل تمرين مكتمل · `completePlannedWorkout`+`reportPlannedWorkout` عند نهاية اليوم · `workout-executions/explore` لتدفق Explore — كلها Outbox فلا تضيع بلا شبكة. تفضيلات التمرين (reps/duration/weight) عبر `EXERCISE_PREFERENCE_UPSERT` (DS-4).
5. Frame captures v1: لقطات peak/danger بدون matting (D9) — تخزين محلي + عرض في التقرير.
6. وضع Assessment: نفس الجلسة بعلَم assessment يعيد النتيجة للـ flow (تكامل مع `AssessmentBodyScanEngine` القائم).

**القبول:** سيناريو offline كامل: جلسة بلا شبكة → journal + Outbox → عودة الشبكة → replay مرة واحدة (مُختبر) · حقل `report` المُنتج يُقبل من الباك اند الحقيقي · بطاقة Pre-07 «Outbox مسار تقرير/إكمال في UI» تتحول ✅.

---

### WS-9 — iOS: الالتقاط والتشغيل 🔴 (يبدأ spike مبكراً بالتوازي)

**الهدف:** نفس الشاشة تعمل على iPhone حقيقي.

**العمل:**
1. **Spike تقني (يبدأ مع WS-4):** MediaPipe Tasks iOS داخل بناء KMP — قرار التكامل (CocoaPods cinterop في `core:pose-capture` iosMain مقابل تنفيذ Swift في `iosApp` يُسجَّل في Koin عبر واجهة المصنع المصدَّرة). يُسجَّل القرار قبل بدء التنفيذ الكامل.
2. `IosCameraFrameSource` (AVCaptureSession: جلسة 4:3، أقصى FPS مدعوم، أمامية/خلفية) + preview عبر `UIKitView` داخل Compose.
3. `IosPoseDetector` (MediaPipe iOS بنفس `.task` bundles + خيارات live-stream) → نفس `PoseFrame`.
4. actuals: `SpeechSynthesizer` (AVSpeechSynthesizer) · `HapticsPort` (UIFeedbackGenerator) · `DeviceTiltPort` (CoreMotion) · أذونات الكاميرا (Info.plist + تدفق رفض الإذن).
5. ضبط الأداء على جهاز (GPU delegate حسب توفره في MediaPipe iOS) + لقطات QA من Xcode (نفس بروتوكول render-proof السابق).
6. iOS بلا `PoseRefiner` v1 (D5) — يُختبر أن fallback الهندسي يعطي تجربة مقبولة، ويُسجَّل الدين.

**القبول:** جلسة سكوات E2E على iPhone Simulator + جهاز (لقطات) · نفس fixtures الذهبية تمر على iosSimulatorArm64 test target · فروق المنصّة موثقة (refiner، أداء).

---

### WS-10 — القطع وتقاعد legacy 🟠

**الهدف:** كل المداخل → KMP، وحذف legacy التدريب نهائياً.

**العمل (مرتّب بالمداخل، كل مدخل = قطع كامل):**
1. **Explore/Library exercise** (`ExercisePrepare` → جلسة KMP) — أول قطع (أبسط: تمرين واحد).
2. **Workout flow (16)** (`WorkoutRun` → جلسة workout كاملة KMP) — يستبدل `WorkoutRunActivity`/`PreWorkoutActivity`.
3. **Session اليوم المخطط (02)** + Home «Start» (`ProgramWorkoutActivity` بأكملها تُستبدل بمسار KMP: بطاقات اليوم موجودة في Session page + الجلسة الجديدة) — أكبر قطع؛ يشمل day customizations القائمة في KMP.
4. **Assessment** (يستبدل `AssessmentSessionActivity` المرتدّة لـ TrainingActivity).
5. حذف: `LegacyTrainingLauncher` · `LaunchLegacyCameraTraining` effect ومعالجاته · `MODE_CAMERA` extras · ثم شجرة legacy (`ui/train` · `ui/training` · `training/**` غير المشترك · `overlay/**` · `pose/**` · `camera/**` · `analysis/**` المنقول · `WorkoutSyncService` بعد التأكد من تصريف طابوره القديم) — مع `git rm` على دفعات قابلة للمراجعة.
6. تحديث الوثائق: scorecards بعمودَي «بنية/منتج» الصادقَين (توصية التدقيق §6.6) + الخطة الأم + `docsStats`.
7. **تصريف الطابور القديم:** قبل حذف `WorkoutSyncService`، migration تنقل أي pending reports قديمة إلى Outbox.

**القبول:** بحث آلي يثبت صفر مراجع لـ `TrainingActivity`/`LegacyTrainingLauncher` · `:app:assembleRelease` أخضر بدون موديولات legacy التدريب · smoke يدوي للمداخل الأربعة · iOS TestFlight-ready build يكمبّل.

---

## 5) التسلسل والاعتماديات

```text
WS-0 ──┬─> WS-1 ──> WS-2 ──> WS-3 ─┐
       │                            ├─> WS-5 ──> WS-6 ─┐
       ├─> WS-4 (يوازي WS-2/3) ────┘                   ├─> WS-8 ──> WS-10
       └─> WS-9 spike (مبكر) ····· WS-9 تنفيذ ─────────┘
                         WS-7 (يوازي WS-5/6، يكتمل قبل WS-8 القبول النهائي)
```

**دفعات تنفيذ مقترحة** (كل دفعة تنتهي ببناء أخضر + iOS compile + تحديث سجل الخطة):

| الدفعة | المحتوى | مخرَج ملموس للمدير |
|--------|---------|----------------------|
| **07.1** | WS-0 + WS-1 | تمرين حقيقي من تكوين الباك اند يعمل في `ExerciseLive` (بدون blueprint) |
| **07.2** | WS-2 + WS-4 | جلسة سكوات E2E على مسار جديد 100% بجودة تقييم legacy (golden replay أخضر) |
| **07.3** | WS-3 + WS-5 | setup/countdown/auto-pause/workout-sets كاملة (منطقاً) + فحوص الوضعية |
| **07.4** | WS-6 + WS-7 | الشاشة النهائية بصرياً وصوتياً — أول عرض «منتج» للمدير |
| **07.5** | WS-8 | تقرير + Outbox + أوفلاين كامل — إغلاق ديْن Pre-07 |
| **07.6** | WS-9 | iPhone يتدرّب (لقطات جهاز) |
| **07.7** | WS-10 | القطع الكامل + حذف legacy + إعادة قياس صادقة |

---

## 6) بوابة خروج Phase 07

| البند | المعيار |
|-------|---------|
| Parity ذهبي | golden replay fixtures (≥4 أنواع تمارين) خضراء على Android + iOS test targets |
| تجربة | بنود §1.4 الأربعة عشر: منقولة أو مؤجلة بقرار مكتوب — جدول مُحدَّث في هذا المستند |
| المداخل | المداخل الأربعة (Explore/Workout/Session+Home/Assessment) تفتح KMP فقط |
| Offline | جلسة كاملة بلا شبكة لا تفقد شيئاً (journal + Outbox replay مُختبران) |
| iOS | جلسة E2E على جهاز/Simulator بلقطات + كل الموديولات الجديدة في CI iOS |
| نظافة | صفر CameraX/MediaPipe/`android.*` في commonMain (فحص آلي) · حذف الجسور اليدوية · حذف legacy التدريب |
| أداء | FPS تحليل ومعالجة إطار ≥ خط أساس legacy على جهاز مرجعي (قياس موثَّق) |
| توثيق | scorecard بعمودين صادقين + تحديث الخطة الأم + `docsStats` |

---

## 7) أوامر التحقق

```powershell
cd android-poc
.\gradlew.bat --console=plain `
  :core:training-engine:testDebugUnitTest `
  :core:pose-capture:testDebugUnitTest `
  :core:data:testDebugUnitTest `
  :core:network:testDebugUnitTest --tests "com.movit.core.network.contract.*" `
  :feature:training:testDebugUnitTest `
  :feature:library:testDebugUnitTest `
  :app:assembleDebug `
  :feature:shell:compileKotlinIosSimulatorArm64 `
  :feature:training:compileKotlinIosSimulatorArm64 `
  :core:pose-capture:compileKotlinIosSimulatorArm64
```

+ على Mac (دفعات 07.6+): بناء `iosApp` من Xcode + لقطات الجلسة (بروتوكول تقرير iOS validation القائم).

---

## 8) المخاطر وخطط التخفيف

| الخطر | الاحتمال/الأثر | التخفيف |
|-------|----------------|----------|
| MediaPipe iOS داخل بناء KMP أصعب من المتوقع (cinterop/pods) | متوسط/عالٍ | spike مبكر في 07.2 بالتوازي؛ خطة بديلة: تنفيذ Swift في iosApp يسجَّل عبر Koin (الواجهة جاهزة لذلك) |
| فروق دقيقة في العدّ/الscore بين القديم والجديد تهزّ ثقة المستخدم | متوسط/عالٍ | golden replay من جلسات حقيقية متعددة + عتبات تطابق صريحة + flag تشغيلي للرجوع الفوري أثناء الإطلاق |
| أداء Compose Canvas للهيكل على أجهزة ضعيفة | متوسط/متوسط | قاعدة صفر-allocation + قياس مبكر في WS-6 على جهاز متوسط + تبسيط طبقات عند الحاجة |
| `training-config` payload أوسع/أقل انتظاماً من نموذج Gson عبر التمارين | متوسط/متوسط | fixtures من تمارين حقيقية متنوعة + parser متسامح موثَّق (نفس تساهلات Gson) + contract test يفشل عند حقل جديد مفقود |
| iOS بدون MLP refiners يُظهر فرق جودة ملموساً | منخفض/متوسط | الـ fallback الهندسي هو نفسه مسار legacy عند غياب المصنّف؛ يُقاس فعلياً في WS-9 ويُقرَّر تحويل الأوزان لاحقاً |
| حجم WS-6 يتضخم (أكبر شاشة في التطبيق) | عالٍ/متوسط | Spec مقفول من WS-0 + تقسيم المكوّنات أعلاه + تأجيل صريح للتجميلات غير الحرجة لدُفعة لاحقة |
| فقدان طابور `WorkoutSyncService` القديم عند الحذف | منخفض/عالٍ | خطوة تصريف إلزامية في WS-10 قبل الحذف |

---

## 9) مقترحات التحسين (فوق الـ parity)

> مفهرسة I-n للربط من مسارات العمل. **Now** = تُنفَّذ ضمن Phase 07 (مجانية تقريباً لأننا نعيد البناء) · **Next** = دفعة 07.x تالية · **Later** = backlog بقرار منتج.

### 9.1 كود وبنية

| # | التحسين | لماذا | أين/متى |
|---|---------|--------|----------|
| I-1 | **توحيد المتكررات**: حذف نسخ legacy من `PauseController`/`HoldTimer`/`ExecutionSafetyGuards` والاكتفاء بنسخ KMP | اليوم نسختان لكل مفهوم — خطر انحراف صامت | WS-2 / **Now** |
| I-2 | تفكيك god-class `TrainingEngine` إلى `FramePipeline` (نقي) + `SessionRuntime` (زمن/قفل) + `FeedbackRouter` (أحداث) | 840 سطراً بـ 20 تبعية وقفل واحد — اختبار أصعب وتزامن أدق | WS-2 / **Now** |
| I-3 | إلغاء singletons اليدوية (`KmpTrainingSessionBridge`/`Assessment…`) لصالح Koin | حالة عالمية قابلة للتسريب وغير قابلة للاختبار | WS-4 / **Now** |
| I-4 | `PoseLandmarkerHelper`: تمرير الدوران/المرآة لـ MediaPipe بدل إعادة رسم Bitmap بـ Canvas كل إطار، وتنظيف `frameCameraState` من الإدخالات اليتيمة | توفير CPU/GC لكل إطار (المسار الأسخن في التطبيق) + سد تسريب خريطة | WS-4 / **Now** |
| I-5 | نقطة back-pressure واحدة: اليوم الإسقاط يحدث في 3 طبقات (ImageAnalysis + InputController + ViewModel) | تتابُع أقفال متداخلة وصعوبة تفسير الـ latency | WS-4 / **Now** |
| I-6 | مصدر زمن واحد (`ExecutionClock`) لكل المكوّنات | legacy يخلط uptime/currentTimeMillis/frame-time بثلاث دوال «now» | WS-2 / **Now** |
| I-7 | `kotlinx.serialization` + fixtures بدل Gson TypeAdapters المخصصة | DTO واحد للعقد عبر المنصّتين + كسر مرئي عند انحراف العقد | WS-1 / **Now** |
| I-8 | `SkeletonOverlayView` (2261 سطراً) → طبقات Canvas منفصلة بحسابات هندسية في common | قابلية اختبار الحسابات + مشاركة iOS + أداء (إعادة استخدام Paths) | WS-6 / **Now** |
| I-9 | `SettingsManager` الساكن → `MovitTrainingPreferences` تفاعلية فوق `MovitLocalStore` | إعدادات مشتركة المنصّتين + قابلة للمراقبة بدل قراءة prefs المتزامنة | WS-1 / **Now** |
| I-10 | إبقاء `PipelineTrace` وتوسيعه + لوحة debug صغيرة خلف flag | أنفع أداة تشخيص ميدانية موجودة — تُفقد عادة في عمليات النقل | WS-2 / **Now** |
| I-11 | تفكيك `FeedbackManager`: قرار (common) / تشغيل (منصّة) | 1289 سطراً تخلط أولويات+TTS+كاش+عشوائي — iOS يحتاج النصف الأول فقط كما هو | WS-7 / **Now** |
| I-12 | **حزام golden replay دائم** (تسجيل جلسات حقيقية → fixtures CI) | يحمي أي تعديل مستقبلي على المحرك — أعلى أثر/تكلفة في القائمة كلها | WS-0 / **Now** |
| I-13 | تقارير قابلة للترقية: فصل `MovitSessionReport` (نموذج) عن العرض | يسمح بتطوير صفحة التقرير دون لمس المحرك | WS-8 / **Now** |

### 9.2 منطق المحرك

| # | التحسين | لماذا | أين/متى |
|---|---------|--------|----------|
| I-14 | توحيد NoPose (supervisor) + Visibility (engine) في سياسة واحدة | نظامان متداخلان اليوم بعتبات مختلفة (1/2/4s مقابل grace/warn/pause) — سلوك متعذر التفسير أحياناً | WS-5 / **Now** |
| I-15 | دمج `PoseSetupGuide` + `StartPoseGate` + فحص countdown في مكوّن «بوابة جاهزية» واحد | ثلاث عمليات تحقق متشابهة بعتبات منفصلة | WS-5 / **Now** |
| I-16 | أوزان scoring من `training-config` بدل ثوابت (`PRIMARY_JOINT_WEIGHT`…) | معايرة من السيرفر دون إصدار تطبيق | WS-1+WS-2 / **Next** |
| I-17 | ربط coach intensity (calm/standard/strict) فعلياً بعتبات `FeedbackPolicy` | الإعداد موجود لكن أثره جزئي | WS-7 / **Now** |
| I-18 | حارس «حاضر لكن خامل»: لا تقدّم reps لمدة N → تلميح/إنهاء لطيف | `ExecutionSafetyGuards` يحمي من الطول الكلي فقط؛ NoPose يحمي من الغياب فقط | WS-5 / **Next** |
| I-19 | تقييم `VelocityFilter` (موجود وغير موصول) كطبقة anti-bounce إضافية للعدّ | تقليل العدّ الكاذب على الأجهزة منخفضة الـ FPS — يُقاس بالـ replay قبل التفعيل | **Later** (بعد حزام I-12) |
| I-20 | meta-quality للجلسة: نسبة الإطارات الساقطة/التغطية كمؤشر ثقة يُرفق بالتقرير | يفسّر النتائج الشاذة ويغذي الدعم | WS-8 / **Next** |

### 9.3 الهيكلة والداتا

| # | التحسين | لماذا | أين/متى |
|---|---------|--------|----------|
| I-21 | `core:pose-capture` موديول مستقل (D2) | عزل أثقل التبعيات (CameraX/MediaPipe/LiteRT) عن المحرك والـ features | WS-0 / **Now** |
| I-22 | **Session journal** (checkpoint أثناء الجلسة في SQLDelight) | اليوم انهيار/قتل التطبيق = فقدان الجلسة كاملة؛ بعد التحسين تُستأنف/تُرفع | WS-8 / **Now** |
| I-23 | إستراتيجية أصول الموديلات (`.task`): bundling موحّد للمنصتين + فحص توافق إصدار عند الإقلاع | iOS يحتاجها أصلاً؛ يفتح لاحقاً تحديث الموديلات من السيرفر | WS-4/WS-9 / **Next** |
| I-24 | حذف `MODE_*` extras وweb of Intent extras لصالح route آمن الأنواع | أخطاء runtime الصامتة في تمرير المعاملات تختفي | WS-10 / **Now** |

### 9.4 تجربة المستخدم

| # | التحسين | لماذا | أين/متى |
|---|---------|--------|----------|
| I-25 | HUD بهوية Movit: `MovitScoreRing` للفورم الحي + hero counter + phase chip (الـ Spec من WS-0) | legacy وظيفي لكنه «أدوات مطوّر»؛ هذه فرصة القفزة البصرية | WS-6 / **Now** |
| I-26 | مؤشر بصري صريح لحالة «countdown مجمّد» مع سبب (المفصل/الإطار) | اليوم المستخدم يرى توقف العدّ بلا تفسير واضح | WS-6 / **Now** |
| I-27 | زر تبديل الكاميرا كإجراء سريع على الشاشة (بدل دفنه في dialog الإعدادات) | إجراء شائع جداً قبل بدء الجلسة | WS-6 / **Now** |
| I-28 | حالات خطأ ناطقة: رفض إذن الكاميرا → `MovitErrorState` + deeplink للإعدادات؛ فشل GPU → إشعار «وضع التوافق CPU» | اليوم Toast + إنهاء صامت | WS-6 / **Now** |
| I-29 | rest screen أغنى: العدّ + التمرين القادم + نصيحة + skip — ومزامنة الصوت قرب النهاية | موجود جزئياً في legacy؛ يُرفع لمستوى الـ design system | WS-6 / **Now** |
| I-30 | A11y حقيقي للجلسة: إعلانات reps عبر semantics (لا TTS فقط) + احترام reduce-motion + أهداف لمس ≥48dp | المحور الأضعف في كل الـ scorecards (25–62%) | WS-6 / **Now** |
| I-31 | تعريب نصوص الأطوار/الإرشاد (legacy مُثبَّتة إنجليزية: "Going Down"، "Get Ready"…) | فجوة تعريب فعلية يراها كل مستخدم عربي | WS-6 / **Now** |
| I-32 | احتفال إكمال بمستوى الهوية (motion خفيف وفق `MovitMotion`) + مشاركة لاحقاً | لحظة الذروة العاطفية في المنتج | WS-6 الآن البسيط / **Later** المشاركة |
| I-33 | شاشة «الجلسة الأولى»: تلميح وضع الهاتف/الإضاءة/المسافة قبل أول setup | يقلّص فشل الـ setup الأول (أكبر نقطة إحباط في تجارب الكاميرا) | **Next** (07.x) |
| I-34 | Pre-flight check خفيف: إضاءة منخفضة/عدسة مغطاة → تلميح قبل الجلسة | يقلل جلسات فاشلة وتقارير سلبية | **Later** |

---

## 10) ما بعد Phase 07 (يُسجَّل الآن، يُنفَّذ لاحقاً)

- نقل 18 قراءة parity المؤجلة عند احتياج UI لها (من Pre-07).
- Segmentation/matting لتقارير الصور (D9) + Rep replay clips الغنية.
- MLP refiners على iOS (تحويل/forward-pass مشترك) إن أثبت WS-9 فرقاً ملموساً.
- وضع الفيديو (D10) بقرار منتج.
- هجرة AGP `android.kmp.library` (معلَّقة من Pre-07) عند ترقية AGP 9.
- إعادة بناء صفحة التقرير الغنية (charts بتفاعل نقطة-بنقطة) فوق `MovitSessionReport`.

---

## 11) سجل التنفيذ

> يُحدَّث مع كل دفعة بنفس نمط Pre-07 (الحالة الحقيقية فقط — لا إغلاق متفائل؛ راجع درس «سجل إغلاق الفجوات»).

| الدفعة | الحالة | تاريخ | ملاحظات |
|--------|--------|-------|----------|
| 07.1 (WS-0+WS-1) | ✅ مكتمل | 2026-06-11 | `feature:training` + `core:pose-capture` · `ExerciseConfig` typed · حذف blueprint · golden replay أولي · iOS compile أخضر |
| 07.2 (WS-2+WS-4) | 🔶 جزئي | 2026-06-11 | `MovitTrainingEngine` + pipeline منقول · Android `CameraX`/`MediaPipe` + Koin · حذف جسر التدريب · WS-3/feedback/hold كاملان → 07.3 |
| 07.3 (WS-3+WS-5) | 🔶 جزئي | 2026-06-11 | position/scene + supervisor/readiness/countdown في common · بناء أخضر · فجوات: FeedbackRouter · توحيد visibility كامل · LiteRT MLP · shell route |
| 07.4 (WS-6+WS-7) | 🔶 جزئي | 2026-06-11 | HUD + skeleton + `FeedbackRouter`/TTS/haptics · `TrainingSessionRoute` في shell · فجوات: rest/sets كامل · AudioManifest prefetch · iOS camera actual |
| 07.5 (WS-8) | 🔶 جزئي | 2026-06-11 | MotionRecorder/MetricsCalculator + journal SQLDelight + MovitSessionReport + mobileWrites coordinator؛ VM hooks؛ frame captures/post-training rich report مؤجّل |
| 07.6 (WS-9) | 🔶 جزئي | 2026-06-11 | AVFoundation preview + permissions · CoreMotion tilt · AVSpeech/haptics actuals · MediaPipe stub + قرار تكامل · iOS compile |
| 07.7 (WS-10) | ✅ مكتمل | 2026-06-11 | `MovitTrainingEntryNavigator` + deep link shell · `git rm` ui/train·ui/training·overlay·camera·PoseLandmarkerHelper·Activities legacy · `WorkoutSyncService` محذوف · `:app:assembleDebug` + `assembleRelease` (shell flag) ✅ |
| 07.8-B (Session/Feedback/Audio) | ✅ مكتمل | 2026-06-11 | Agent B — rest/sets coordinator · coach intensity · motivational feedback · DS-6 prefetch · explore batch · typed routes — §13.4 |
| 07.8-D (Reports + legacy `training/**`) | ✅ مكتمل | 2026-06-11 | Agent D — `MovitPostTrainingReport` + golden parity · `SessionQualityMeta` (I-20) · `MovitPeakFrameCapture` v1 · `MovitSessionReportUiMapper` · `git rm` supervisor legacy orphan — §13.5 |
| 07.8 (إغلاق بدون جهاز) | 🔶 مُغلق (Windows) | 2026-06-11 | A/B/C/D/E — §7 ✅ (2026-06-11) · إصلاح `SkeletonRomGeometry` K/N · `docsStats` **434** · مستثنى جهاز: I-4/I-5 · LiteRT · smoke · Visual QA · E2E · iOS E2E |

### سجل فروق السلوك المعتمدة (يُملأ أثناء التنفيذ)

| # | الفرق عن legacy | المبرر | القرار |
|---|------------------|--------|--------|
| B-1 | توحيد NoPose/Visibility (I-14) | نظامان متداخلان | 🔶 `PresenceSupervisorBridge` + `onPresenceEvent` في المحرك (07.8-A)؛ VM/supervisor يربط الإشارة — TTS موحّد → Agent B/C |
| B-2 | journal يحفظ الجلسة عند الانقطاع (I-22) | تحسين صريح | ✅ `SessionJournal.sq` + `SessionJournalStore` + checkpoint rep-level في `TrainingMotionSession` |
| B-3 | iOS بلا MLP refiners في v1 (D5) | حدود منصّة مؤقتة — نفس fallback الهندسي لـ legacy عند غياب LiteRT | ✅ مُعتمد في 07.6 · `IosPoseRefiner` = `NoOpPoseRefiner` |
| B-4 | `PositionValidator`/`FrameFeedbackEmitter`/`HoldExerciseCoordinator` الكامل غير موصولين في `MovitTrainingEngine` v1 | تفكيك I-2 تدريجي — WS-3/WS-5/WS-7 | ✅ أُغلق في 07.3 (callbacks بدل StateFlows) |
| B-5 | `MediaPipePoseDetector` ما زال `ImageProxy.toBitmap()` (I-4 جزئي) | compile أخضر أولاً؛ تحسين دوران لاحقاً | ✅ مؤقت |
| B-6 | مرآة front-camera في المحرك (`PoseFrame.mirrored()`) قبل الاستخراج | توحيد منطق legacy bilateral | ✅ مُعتمد في 07.2 |

---

## 12) نتائج التنفيذ والقرارات (دفعة 07.1 — 2026-06-11)

### ما اكتمل

| البند | الحالة |
|-------|--------|
| D1 `feature:training` | ✅ — نقل `ExerciseLive*` من library؛ shell يستورد `ExerciseLiveRoute` منه |
| D2 `core:pose-capture` | ✅ — stubs Android/iOS |
| D4 `ExerciseConfig` typed | ✅ — parser + 4 fixtures حقيقية |
| D6 Koin (جزئي) | 🔶 — `TrainingConfigRepository` + `MovitTrainingPreferences`؛ جسر الكاميرا ما زال (07.2) |
| WS-0 Spec + golden replay | ✅ — `Training-Live-Screen-Spec.md` + `ParityRunner` |
| WS-1 | ✅ — sync `exercises[]` → cache · `LiveExerciseRunner` على `ExerciseConfig` |
| حذف blueprint POC | ✅ |

### مؤجَّل

- **07.2–07.7** بالكامل (محرك إطارات، actuals، UI، تقرير، iOS كاميرا، قطع legacy).
- **D7/D11** و**parity Gson حقل-بحقل** → 07.2+.

### تحقق البناء

`:core:training-engine` · `:core:pose-capture` · `:core:data` · `:feature:training` tests ✅ · `:app:assembleDebug` ✅ · iOS compile (`shell`/`training`/`pose-capture`) ✅ · `docsStats` → **434** اختبار KMP (محدَّث 07.8).

### الخطوة التالية (بعد 07.2)

**07.3**: `PositionValidator` + supervisor visibility + readiness gate.

---

## 12) نتائج التنفيذ والقرارات (دفعة 07.2 — 2026-06-11)

### ما اكتمل

| البند | الحالة |
|-------|--------|
| WS-2 `MovitTrainingEngine` (I-2) | 🔶 — `FramePipelineExecutor` + `JointEvaluator` + `AngleSmoother` + `JointAngleTracker` + `RepCompletionCoordinator`؛ `LiveExerciseRunner` يفوّض للمحرك |
| WS-2 `PoseFrame.worldLandmarks` + `mirrored()` + `ElbowAngleEstimator` | ✅ |
| WS-2 golden replay squat | ✅ — `ParityRunner` + `MovitTrainingEngineParityTest` |
| WS-4 `CameraXFrameSource` + `MediaPipePoseDetector` | ✅ — Android actuals في `core:pose-capture` |
| WS-4 Koin D6 (تدريب) | ✅ — `movitPoseCaptureAndroidModule()` · حذف `KmpTrainingSessionBridge`/`LegacyKmpTrainingSessionFactory`/`TrainingBoundaryInstall` |
| WS-4 `TrainingCameraSurface` | ✅ — `feature:training` androidMain |
| iOS compile | ✅ — stubs `pose-capture`/`training`/`shell` |

### ما لم يُكتمل (صريح)

| البند | السبب |
|-------|--------|
| `PositionValidator` + position errors StateFlows | WS-3 |
| `FrameFeedbackEmitter` + `JointErrorCollection` + `FeedbackRouter` كامل | WS-7 |
| `HoldExerciseCoordinator` + hold StateFlows | WS-5 جزئي |
| I-4 rotation بدون bitmap redraw | تحسين لاحق |
| iOS camera actuals | WS-9 |
| تقييم parity حقل-بحقل مقابل legacy `TrainingEngine` | يحتاج fixtures أغنى |

### تحقق البناء

`:core:training-engine:testDebugUnitTest` ✅ (67) · `:core:pose-capture:testDebugUnitTest` ✅ · `:core:data:testDebugUnitTest` ✅ · `:feature:training:testDebugUnitTest` ✅ · `:app:assembleDebug` ✅ · iOS compile (`shell`/`training`/`pose-capture`) ✅

### الخطوة التالية

**07.4** (WS-6 + WS-7): HUD نهائي + FeedbackRouter/منصّة.

---

## 12) نتائج التنفيذ والقرارات (دفعة 07.3 — 2026-06-11)

### ما اكتمل

| البند | الحالة |
|-------|--------|
| WS-3 detectors + `PositionValidator` | ✅ — `CameraPositionDetector` · `BodyPostureDetector` · `VisibleRegionDetector` · `PoseSceneDetector` · `PoseSceneExpectation` |
| WS-3 `DeviceTiltPort` + `LandmarkTiltCorrector` | ✅ — common + `AndroidDeviceTiltPort` · iOS `NoOpDeviceTiltPort` |
| WS-3 `PoseRefiner` (D5) | 🔶 — boundary + Android/iOS stubs (بدون LiteRT فعلي) |
| WS-3 pipeline wire | ✅ — `FramePipelineExecutor` → `MovitTrainingEngine` |
| WS-3 tests | ✅ — `PositionValidatorTest` (desk fixture + tilt) |
| WS-5 `SessionSupervisor` | ✅ — `SupervisorSignal`/`SupervisorAction`/`SessionRunState` |
| WS-5 `SetupReadinessGate` + `CountdownController` | 🔶 — منطق مُبسَّط (I-15 جزئي؛ ليس كل `PoseSetupGuide`) |
| WS-5 `TrainingSessionFlowCoordinator` | 🔶 — تسلسل sets/rest مُبسَّط |
| WS-5 `TrainingSessionViewModel` | ✅ — supervisor + gate + countdown + engine |
| WS-5 hold/feedback wire | ✅ — `HoldExerciseCoordinator` · `JointErrorCollection` · `FrameFeedbackEmitter` في المحرك |
| iOS compile | ✅ |

### ما لم يُكتمل (صريح)

| البند | السبب |
|-------|--------|
| `FeedbackRouter` skeleton | WS-7 (07.4) |
| ربط `VisibilityMonitor` → `SessionSupervisor` (B-1 كامل) | يحتاج سياسة موحّدة في VM |
| LiteRT MLP posture/elbow refiners | Android assets + غلاف فعلي |
| `TrainingSessionScreen` في shell | 07.4 UI skeleton |
| parity حقل-بحقل مع legacy supervisor | fixtures أغنى |

### تحقق البناء

`:core:training-engine:testDebugUnitTest` ✅ · `:core:pose-capture:testDebugUnitTest` ✅ · `:core:data:testDebugUnitTest` ✅ · `:feature:training:testDebugUnitTest` ✅ · `:app:assembleDebug` ✅ · iOS compile (`shell`/`training`/`pose-capture`) ✅

### الخطوة التالية

**07.4** (WS-6 + WS-7): HUD + FeedbackRouter — لا WS-10.

---

## 12) نتائج التنفيذ والقرارات (دفعة 07.4 — 2026-06-11)

### ما اكتمل

| البند | الحالة |
|-------|--------|
| WS-6 `MovitScoreRing` · `MovitGlassMessage` · `MovitSkeletonOverlay` · `TrainingHud` · `VignetteEffect` | ✅ — `core:designsystem` |
| WS-6 لوحات الجلسة | ✅ — `SetupPosePanel` · `CountdownOverlay` · `RestPanel` · `WorkoutCompletePanel` في `feature:training` |
| WS-6 `TrainingSessionScreen` + `TrainingSessionRoute` | ✅ — حالات setup/countdown/live/auto-pause/complete |
| WS-6 shell navigation | ✅ — `MovitInnerRoute.TrainingSession`؛ مداخل `KmpLive` → جلسة كاملة |
| WS-6 تعريب | ✅ — مفاتيح `training_session_*` ar/en |
| WS-7 `FeedbackRouter` | ✅ — `engine/feedback` فوق `FeedbackScheduler` + اختبارات arbiter |
| WS-7 `SpeechSynthesizer` + `HapticsPort` | 🔶 — Android actuals؛ iOS stub (WS-9 لـ AVSpeech) |
| WS-7 ربط `TrainingSessionViewModel` | ✅ — countdown/position/visibility → `FeedbackRouter` + رسائل زجاجية |
| WS-7 visibility ↔ supervisor | 🔶 — `onVisibilityEvent` → `VisibilityPaused`/`VisibilityRestored`؛ إيقاف إطار عند pause فقط |
| iOS compile (نطاق 07.4) | 🔶 — `training-engine` + `feature:training` ✅؛ `feature:shell` ❌ (`MovitProfileEffect`) |

### ما لم يُكتمل (صريح)

| البند | السبب |
|-------|--------|
| DS-6 prefetch ملفات صوت كامل | يحتاج ربط `AudioPrefetchRunner` في مسار الجلسة (07.5/تكامل) |
| iOS TTS/haptics فعلي | WS-9 |
| `core:pose-capture` iosSimulatorArm64 | أخطاء `IosCameraFrameSource`/`IosHapticsPort` (وكيل WS-9 — خارج نطاق 07.4) |
| `core:data` iosSimulatorArm64 | `sessionJournalEntryQueries` (وكيل WS-8 — خارج نطاق 07.4) |
| Visual QA كامل (RTL/Dark/200%) | يحتاج جولة يدوية |
| LiteRT MLP · ROM arcs على الهيكل | مؤجّل |
| حذف `ExerciseLive` POC | يبقى للمقارنة حتى WS-10 |

### تحقق البناء (نطاق §7 المتاح)

`:feature:training:testDebugUnitTest` ✅ · `:feature:training:compileKotlinIosSimulatorArm64` ✅ · `:core:training-engine:compileKotlinIosSimulatorArm64` ✅ · `:core:data:testDebugUnitTest` ✅ · `:app:assembleDebug` ✅ · `:feature:shell:compileKotlinIosSimulatorArm64` ❌

### الخطوة التالية

**07.5** (WS-8): `MotionRecorder`/journal + Outbox من UI — لا تقاطع مع وكلاء pose-capture iOS.

---

## 12) نتائج التنفيذ والقرارات (دفعة 07.5 — 2026-06-11)

### ما اكتمل

| البند | الحالة |
|-------|--------|
| WS-8 `MotionRecorder` + `MetricsCalculator` في commonMain | ✅ — `core/training/journal/*` · metrics-only (بدون raw frames) |
| WS-8 `TrainingMotionSession` + hooks في `MovitTrainingEngine` | ✅ — `onMotionFrameRecorded` / `onRepCompletedForMotion` |
| I-22 Session journal SQLDelight | ✅ — `SessionJournal.sq` · `SessionJournalStore` · rep-level checkpoint |
| WS-8 `MovitSessionReport` (schema `WorkoutReport` legacy) | ✅ — `@Serializable` · `MovitSessionReportBuilder` · `WorkoutUploadMapper` → DTO |
| WS-8 `TrainingSessionWriteCoordinator` + Outbox | ✅ — `startPlannedWorkout` · `uploadWorkoutExecution` · `complete/reportPlannedWorkout` |
| WS-8 Assessment result hook | ✅ — `AssessmentTrainingResult` + `TrainingSessionViewModel.buildAssessmentResult()` |
| `MovitData.trainingWrites` Koin | ✅ |
| commonTest WS-8 | ✅ — `MetricsCalculatorTest` · `SessionJournalStoreTest` · `WorkoutUploadMapperTest` |

### ما لم يُكتمل (صريح)

| البند | السبب |
|-------|--------|
| Frame captures v1 (D9) matting/segmentation | مؤجّل — نموذج تخزين `MovitPeakFrameCapture` فقط (07.8-D) |
| `PostTrainingReport` حقل-بحقل vs `ReportGenerator` | ✅ golden + `PostTrainingReportFieldComparator` (07.8-D) — rich fields (timeline/errors) ما زالت subset |
| Explore batch `workout-executions/explore` من VM | coordinator جاهز؛ استدعاء multi-exercise من shell/workout mode → 07.6/07.7 |
| E2E offline replay مُختبر على backend حقيقي | يحتاج جهاز + شبكة |
| `LiveExerciseRunnerTest`/`full suite` training-engine | فشل/تعارض مع 07.4 parallel (لا 07.5 regression في tests الجديدة) |

### تحقق البناء

`:core:training-engine:testDebugUnitTest` (journal) ✅ · `:core:data:testDebugUnitTest` (journal+mapper) ✅ · compile `:core:data` ✅

### الخطوة التالية

**07.7** ✅ — القطع legacy + بناء release. **التالي:** smoke adb للمداخل الأربعة · 07.6 Mac (MediaPipe iOS) · تقاعد `training/**` المتبقي مع تقارير KMP.

---

## 12) نتائج التنفيذ والقرارات (دفعة 07.7 — WS-10 — 2026-06-11)

### ما اكتمل

| البند | الحالة |
|-------|--------|
| `movit.training.kmp.enabled` (gradle + `BuildConfig` + `MovitTrainingKmpGate`) | ✅ |
| Shell مداخل أربعة + **مسار legacy non-shell** → `MovitTrainingEntryNavigator` → `MovitShellPendingNavigation` | ✅ |
| `git rm`: `ui/train` (عدا نقل `TrainFragment` → `ui/programs`) · `ui/training` · `overlay/**` · `camera/**` · `PoseLandmarkerHelper` · `TrainingActivity` · `ProgramWorkoutActivity` · `WorkoutRunActivity` · `PreWorkoutActivity` · `AssessmentSessionActivity` · `MainActivity` · `DebugActivity` · `WorkoutSyncService` · `MotionRecorder` | ✅ |
| `LegacyWorkoutUpload` في `storage/` + `LegacyWorkoutSyncDrain` → Outbox | ✅ |
| حذف `LegacyTrainingLauncher` · جسور التقييم اليدوية (دفعة سابقة) | ✅ |
| `:app:assembleDebug` | ✅ |
| `:app:assembleRelease` مع `-Pmovit.shell.launcher.enabled=true` | ✅ |
| §7 tests (`training-engine` · `pose-capture` · `data` · `network` contract · `feature:training` · `feature:library`) | ✅ |

### مراجع grep (صفر كود حي)

| الرمز | مراجع متبقية |
|-------|----------------|
| `TrainingActivity` / `ProgramWorkoutActivity` / `WorkoutRunActivity` / `WorkoutSyncService` | **0** في `.kt` قابلة للتنفيذ — تعليقات/KDoc/strings فقط |
| `LegacyTrainingLauncher` | **0** |

### مؤجَّل (خارج نطاق 07.7)

| البند | السبب |
|-------|--------|
| `git rm` كامل `training/**` (محرك legacy + تقارير XML) | ما زال يخدم `WorkoutReportActivity` · `ReportGenerator` · نماذج Gson |
| `pose/BodyLandmarks` · `analysis/**` | تبعيات `training/engine` للتقارير/الاختبارات |
| حذف `movit.training.kmp.enabled` (D11) | rollback تشغيلي |
| deep link تدريب من legacy release بلا shell flag | يحتاج `movit.shell.launcher.enabled=true` أو debug `MovitShellPilotActivity` |
| MediaPipe iOS فعلي (07.6 Mac) | خارج Windows |
| smoke يدوي المداخل الأربعة | يحتاج جهاز/adb |

### تحقق البناء (§7)

`:core:training-engine:testDebugUnitTest` · `:core:pose-capture:testDebugUnitTest` · `:core:data:testDebugUnitTest` · `:core:network:testDebugUnitTest` (contract) · `:feature:training:testDebugUnitTest` · `:feature:library:testDebugUnitTest` · `:app:assembleDebug` · `:app:assembleRelease` (`-Pmovit.shell.launcher.enabled=true`) — **كلها ✅ (2026-06-11)**

---

## 12bis) نتائج التنفيذ والقرارات (دفعة 07.6 — WS-9 — 2026-06-11)

### قرار التكامل iOS (D3 spike — مُعتمد)

| المسار | القرار | السبب |
|--------|--------|--------|
| **CocoaPods + cinterop داخل `core:pose-capture` Gradle** | ❌ مؤجَّل | يعقّد KMP Gradle/CI (Mac-only pods، تعارضات Skiko/Compose، صيانة cinterop لـ MediaPipe Tasks) |
| **Swift bridge في `iosApp` + تسجيل Koin عند `MovitData.install`** | ✅ **مُختار** | Kotlin/Native actuals (كاميرا/مستشعر/صوت/اهتزاز) تبقى في `pose-capture`/`training` iosMain؛ `MediaPipeTasksVision` يُلفّ في Swift (`MovitPoseLandmarkerBridge`) ويُحقَن عبر `additionalModules` — نفس نمط D6 بدون `GlobalContext` |
| **Apple Vision** | ❌ مرفوض (D3) | 19 landmark بتسميات مختلفة — يكسر parity مع BlazePose 33 |

**تنفيذ 07.6:** `IosCameraFrameSource` + `IosPoseDetector` (stub آمن للتجميع) + `MovitPoseCaptureIosBindings` · `TrainingCameraHost.ios.kt` (إذن + `UIKitView`) · `Info.plist`/`project.yml` لـ `NSCameraUsageDescription` + `NSMotionUsageDescription`.

### ما اكتمل

| البند | الحالة |
|-------|--------|
| `IosCameraFrameSource` (AVCaptureSession 4:3 · front/back · preview layer) | ✅ |
| `IosPoseDetector` (stub + مسار MediaPipe موثَّق) | 🔶 stub — لا landmarks حتى Swift bridge |
| `IosDeviceTiltPort` (CoreMotion `CMMotionManager`) | ✅ |
| `IosSpeechSynthesizer` (`AVSpeechSynthesizer`) | ✅ |
| `IosHapticsPort` (`UIImpactFeedbackGenerator`) | ✅ |
| `TrainingCameraHost.ios.kt` (permission flow + `UIKitView`) | ✅ |
| B-3 iOS بلا MLP refiners (D5) | ✅ — `NoOpPoseRefiner` |
| أذونات الكاميرا/الحركة في `iosApp` | ✅ |

### ما لم يُكتمل (صريح — blockers)

| البند | السبب |
|-------|--------|
| MediaPipe Tasks Vision iOS فعلي | يحتاج Mac + CocoaPods `MediaPipeTasksVision` + Swift bridge + `.task` في bundle |
| `movitPoseCaptureIosModule()` في `MainViewController` | خارج نطاق 07.6 — `feature:shell` · اليوم `MovitPoseCaptureIosBindings` يدوي من `TrainingCameraHost` |
| `SpeechSynthesizer.ios.kt` actual (no-op) | ✅ — actual يستخدم `AVSpeechSynthesizer`؛ `IosSpeechSynthesizer` يفوّض إلى `SpeechSynthesizer` (بدون تبعية دائرية) |
| QA جهاز (GPU delegate · FPS · render-proof) | يحتاج Xcode على جهاز |
| تدريب حي يعدّ reps على iOS | محجوب بـ MediaPipe stub |

### تحقق البناء

`:core:training-engine:compileKotlinIosSimulatorArm64` ✅ (2026-06-11) — `PoseFrame` import في `MovitTrainingEngine` · `FrameSample.equals` متوافق K/N · `SpeechSynthesizer.ios.kt` actual (AVSpeech) · `HapticsPort` = `expect interface` + `actual interface` (تنفيذ `IosHapticsPort` في pose-capture).

`:core:pose-capture:compileKotlinIosSimulatorArm64` ✅ — إصلاحات K/N: `UIImpactFeedbackStyle.*` · `kotlin.concurrent.Volatile` + `useContents` في tilt · `AVCaptureConnection` cast/setters.

`:feature:training:compileKotlinIosSimulatorArm64` ✅ (2026-06-11) — `TrainingCameraHost.ios` بدون `authorizationStatusForMediaType` (إذن ضمني عند `IosCameraFrameSource.start`).

### الخطوة التالية

**07.6 متابعة (Mac):** Podfile + `MovitPoseLandmarkerBridge` + ربط Koin في shell · golden replay على لقطات iOS · قياس B-3 (هل فرق MLP ملموس).

---

## 12) نتائج التنفيذ والقرارات (تدقيق §7 — 2026-06-11)

### جدول التحقق (أمر §7 — Windows)

| الهدف | النتيجة | ملاحظات |
|--------|---------|---------|
| `:core:training-engine:testDebugUnitTest` | ✅ | يشمل `LiveExerciseRunnerTest.squatCycle_countsRepsFromSyntheticFrames` (لا يوجد `squatCycle` منفصل) |
| `:core:pose-capture:testDebugUnitTest` | ✅ | |
| `:core:network:testDebugUnitTest` (contract) | ✅ | |
| `:feature:library:testDebugUnitTest` | ✅ | |
| `:core:designsystem:compileKotlinIosSimulatorArm64` | ✅ | إصلاح `SkeletonRomGeometry` |
| `:core:data:testDebugUnitTest` | ✅ | |
| `:feature:training:testDebugUnitTest` | ✅ | |
| `:app:assembleDebug` | ✅ | |
| `:feature:shell:compileKotlinIosSimulatorArm64` | ✅ | |
| `:feature:training:compileKotlinIosSimulatorArm64` | ✅ | |
| `:core:pose-capture:compileKotlinIosSimulatorArm64` | ✅ | |

**أمر Gradle:** سلسلة §7 كاملة (شامل `:core:network` contract + `:feature:library`). **BUILD SUCCESSFUL** — 2026-06-11 (إغلاق 07.8-E).

### إصلاحات minimal (training-engine / pose-capture / حدود iOS)

| البند | الإجراء |
|-------|---------|
| iOS camera permission K/N | `core:pose-capture/IosCameraPermission.kt` — stub آمن (روابط `authorizationStatusForMediaType` غير قابلة للاستدعاء من Kotlin 2.3 رغم وجودها في platform klib)؛ `feature:training` يفوّض إلى pose-capture |
| `IosCameraPermissionProbe.kt` | حُذف (استيراد Companion خاطئ + غير مستخدم) |
| WS-10 legacy deletion | **لم يُبدأ** |

### الخطوة التالية

Mac: MediaPipe Swift bridge + cinterop/QA؛ Windows: متابعة 07.7 shell cutover بدون `git rm` legacy حتى اكتمال non-shell consumers.

---

## 13) سجل إغلاق الدفعات الفرعية

### 13.1 «07.8-A» — Engine & Parity (Agent A — 2026-06-11)

**النطاق:** `core/training-engine/**` فقط.

#### مهام مُنجزة

| المهمة | الحالة | ملاحظات |
|--------|--------|---------|
| I-4 `MediaPipePoseDetector` rotation | ⏭️ **خارج النطاق** | `core/pose-capture` — blocker لـ Agent pose-capture |
| I-5 نقطة back-pressure واحدة | ✅ | `FrameIngressGate` — إسقاط على جانب استهلاك المحرك فقط؛ الطبقات العليا (CameraX/VM) → pose-capture Agent |
| I-6 `ExecutionClock` موحّد | ✅ | `MovitTrainingEngine` يملك `ExecutionClock` واحداً؛ كل المكوّنات الداخلية تستخدم `nowMs` من الإطار |
| I-10 `PipelineTrace` + debug flag | ✅ | `observability/PipelineTrace` · `PipelineTraceConfig.setEnabled` — بدون لوحة UI (Agent C يستهلك) |
| I-12 golden replay موسّع | ✅ | `ParityRunner` + اختبارات hold/bilateral/position-checks في `MovitTrainingEngineParityTest` |
| I-14 B-1 توحيد Visibility/NoPose | ✅ | `PresenceSupervisorBridge` + `onPresenceEvent`؛ VM `handlePresenceEvent` → `toSupervisorSignal()` → `SessionSupervisor` |
| I-15 `SetupReadinessGate` | 🔶 | إرشاد أسوأ مفصل + camera tip + `isCountdownPoseValid`؛ ليس كل `PoseSetupGuide` (صوت/TTS → Agent B) |
| I-16 أوزان scoring من config | ⏭️ | لا حقول `scoringWeight` في `ExerciseConfig` — ثوابت `ScoreCalculator` تبقى |
| LiteRT `PoseRefiner` | ⏭️ | `AndroidPoseRefiner` stub في `core/pose-capture` (بدون assets `.tflite`) — يحتاج جهاز/أصول |

#### ملفات مُغيَّرة / جديدة

- `session/FrameIngressGate.kt` · `session/PresenceSupervisorBridge.kt` · `session/SetupJointGuidance.kt`
- `observability/PipelineTrace.kt` · `observability/PipelineTraceConfig.kt`
- `session/MovitTrainingEngine.kt` · `session/SessionOrchestrator.kt` · `session/SetupReadinessGate.kt` · `session/LiveExerciseRunner.kt`
- `commonTest`: `ParityRunner.kt` · `MovitTrainingEngineParityTest.kt` · `PresenceSupervisorBridgeTest.kt` · `PipelineTraceTest.kt` · `FrameIngressGateTest.kt` · `SetupReadinessGateTest.kt`

#### blockers لوكلاء آخرين

| الوكيل | Blocker |
|--------|---------|
| pose-capture | I-4 rotation بدون bitmap · I-5 إسقاط CameraX/ImageAnalysis |
| feature/training (B) | ربط `onPresenceEvent` → `SessionSupervisor.processSignal` · TTS موحّد لـ NoPose+Visibility |
| feature/training (C) | لوحة debug تستهلك `PipelineTrace` + `PipelineTraceConfig` |
| pose-capture | LiteRT MLP assets + `AndroidPoseRefiner` فعلي |

#### تحقق البناء

`:core:training-engine:testDebugUnitTest` ✅ (100) · `:core:training-engine:compileKotlinIosSimulatorArm64` ✅ — 2026-06-11.

---

## 13) دفعة 07.8 — إغلاق المتبقي (بدون جهاز)

**المالك:** Agent E (تنسيق) · **التاريخ:** 2026-06-11  
**النطاق:** إغلاق فجوات §9 + بوابة §6 + مؤجَّلات §12 التي **لا تتطلّب جهازاً فعلياً أو Mac أو backend حيّ**.  
**مرجع العدّاد:** [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) — **395** اختبار KMP · **878** مفتاح ar/en (`docsStats` 2026-06-11).

### 13.0 توزيع الوكلاء (07.8)

| الوكيل | الملكية | لا يلمس |
|--------|---------|---------|
| **A** | `core:training-engine/**` | `feature/training` · `feature/shell` · `app/**` · `core/data` · `core/designsystem` · `core/pose-capture` |
| **B** | `feature/training/**` (VM · coordinators · routes · write hooks — **ليس** composables) · `core/training-engine/**/session/**` · `core/training-engine/**/feedback/**` · `core/data/**` (DS-6 audio فقط) | `core/designsystem` · `app/**/training/**` · `core/pose-capture` |
| **C** | `core/designsystem/**` (مكوّنات الجلسة الحية) · `feature/training/**` (`*Screen*` · `*Panel*` · `*Hud*` · `*Overlay*` · `*Camera*`) · `core/resources/**` (`training_session_*`) | ViewModels/coordinators (B) · `core/training-engine` (A) · `app/**` (D) |
| **D** | `app/**/training/**` (legacy المتبقي) · `core/training-engine/**/report/**` · `core/data/**` (upload/report mapper) · `feature/reports/**` (عرض 17 إن لزم) | `feature/training` session UI/VM (B/C) · `core/pose-capture` |
| **E** | هذا المستند §13 فقط | أي كود إنتاج |

### 13.0.1 قواعد التنسيق

1. **ترتيب الدمج:** `core:training-engine` (A) → `core:data` DS-6 (B) → `feature/training` VM (B) → `core/designsystem` (C) → `feature/training` UI (C) → `app` legacy/report (D) → `feature/shell` navigation (أي وكيل — تنسيق مع E).
2. **واجهات مشتركة — يُعلن التغيير في §13.1 قبل الدمج:**
   - **`PipelineTrace` API** (A): `fun snapshot(): List<String>` + `fun record(stage, detail)` في `core:training-engine` — C يستهلكها لـ long-press debug HUD خلف flag؛ B لا يمرّر إلا `StateFlow<PipelineTraceSnapshot?>` من VM.
   - **`TrainingSessionUiState` / RestPanel** (B→C): حقول `restSecondsRemaining` · `restTip` · `nextExerciseLabel` · `canSkipRest` · `restEndingSoon` — C يقرأ فقط؛ لا منطق تنسيق في composable.
   - **`FeedbackRouter` + coach intensity** (B): تعديل عتبات `FeedbackPolicy` عبر `MovitTrainingPreferences` — A يوفّر hooks في المحرك؛ B يوصّل.
   - **`RomArcGeometry`** (A→C): حسابات قوس/خط ROM في `core:training-engine/geometry`؛ `MovitSkeletonOverlay` يرسم فقط.
   - **`MovitSessionReport` / `WorkoutUploadMapper`** (D): نموذج التقرير منفصل عن UI — `feature/reports` يستهلك mapper لا يبني JSON يدوياً.
3. **تعارض ملف:** من يفتح PR على ملف خارج ملكيته يذكر الوكيل المالك في §13.1؛ E يحسم خلال 30 دقيقة.
4. **التحقق الإلزامي قبل إغلاق 07.8:** أمر §7 كاملاً على Windows (بدون Mac targets إن فشلت بيئة CI محلياً — يُسجَّل في §13.1).

### 13.0.2 مستثنى من 07.8 (جهاز / Mac / backend حي)

| البند | السبب |
|-------|--------|
| MediaPipe iOS Swift bridge + `MovitPoseLandmarkerBridge` | Mac + CocoaPods |
| smoke adb للمداخل الأربعة | جهاز + adb يدوي |
| Visual QA يدوي (RTL · Dark · 200% · small phone) | جهاز + عين بشرية |
| E2E offline replay على backend إنتاج/ستيجينغ حيّ | شبكة + سيرفر |
| قياس FPS/إطار مقابل legacy على جهاز مرجعي | جهاز |
| جلسة سكوات E2E iPhone (عدّ reps حيّ) | Mac + جهاز |

---

### 13.0.3 مصفوفة المتبقي (57 صفاً)

> **الرموز:** ⬜ لم يبدأ · 🔶 جزئي · ✅ مغلق ضمن نطاق Phase · **Dev?** Y = محجوب بدون جهاز/Mac/backend.

#### أ) مقترحات التحسين §9 (I-1..I-34)

| ID | المهمة | الوكيل | الحالة | Dev? |
|----|--------|:------:|:------:|:----:|
| I-1 | توحيد نسخ legacy من Pause/Hold/SafetyGuards → KMP فقط | A | 🔶 | N |
| I-2 | تفكيك god-class → FramePipeline + SessionRuntime + FeedbackRouter | A | ✅ | N |
| I-3 | إلغاء singletons الجسور → Koin | B | ✅ | N |
| I-4 | MediaPipe: دوران/مرآة بدون bitmap redraw + تنظيف timestamp map | — | 🔶 | N |
| I-5 | نقطة back-pressure واحدة للإطارات | — | 🔶 | N |
| I-6 | مصدر زمن واحد `ExecutionClock` | A | ✅ | N |
| I-7 | `kotlinx.serialization` + fixtures بدل Gson adapters | A | ✅ | N |
| I-8 | Skeleton → طبقات Canvas + ROM arcs (حساب common) | A+C | 🔶 | N |
| I-10 | `PipelineTrace` + لوحة debug خلف flag | A+C | ✅ | N |
| I-9 | `SettingsManager` → `MovitTrainingPreferences` | B | ✅ | N |
| I-11 | تفكيك FeedbackManager → قرار common / تشغيل منصّة | B | ✅ | N |
| I-12 | حزام golden replay دائم (≥4 أنواع تمارين) | A | 🔶 | N |
| I-13 | فصل `MovitSessionReport` (نموذج) عن العرض | D | ✅ | N |
| I-14 | توحيد NoPose + Visibility في سياسة واحدة | A+B | 🔶 | N |
| I-15 | دمج SetupGuide + StartPoseGate + countdown check | A | 🔶 | N |
| I-16 | أوزان scoring من `training-config` | A | ⬜ | N |
| I-17 | coach intensity → عتبات `FeedbackPolicy` | B | ✅ | N |
| I-18 | حارس «حاضر لكن خامل» | A | ⬜ | N |
| I-19 | `VelocityFilter` anti-bounce | A | ⬜ | N |
| I-20 | meta-quality (إسقاط إطارات/تغطية) في التقرير | D | ✅ | N |
| I-21 | موديول `core:pose-capture` مستقل (D2) | — | ✅ | N |
| I-22 | Session journal SQLDelight | D | ✅ | N |
| I-23 | إستراتيجية أصول `.task` موحّدة | — | ⬜ | N |
| I-24 | حذف `MODE_*` Intent extras → routes آمنة | B+D | ✅ | N |
| I-25 | HUD هوية Movit (ScoreRing · hero · phase chip) | C | ✅ | N |
| I-26 | مؤشر بصري countdown مجمّد + سبب | C | ✅ | N |
| I-27 | زر تبديل كاميرا سريع على الشاشة | C | ✅ | N |
| I-28 | حالات خطأ ناطقة (إذن · GPU→CPU) | C | 🔶 | N |
| I-29 | RestPanel غني + sets/rest كامل في coordinator | B+C | 🔶 | N |
| I-30 | A11y جلسة (semantics reps · reduce-motion · 48dp) | C | ✅ | N |
| I-31 | تعريب نصوص الأطوار/الإرشاد ar/en | C | ✅ | N |
| I-32 | احتفال إكمال + motion خفيف | C | ✅ | N |
| I-33 | شاشة «الجلسة الأولى» (تلميح وضع/إضاءة) | C | ⬜ | N |
| I-34 | Pre-flight إضاءة/عدسة | — | ⬜ | N |

#### ب) بوابة خروج Phase 07 (§6)

| ID | المعيار | الوكيل | الحالة | Dev? |
|----|---------|:------:|:------:|:----:|
| EG-1 | golden replay ≥4 أنواع — Android + iOS test targets | A | 🔶 | N |
| EG-2 | بنود §1.4 (14) منقولة أو مؤجَّلة بقرار | E | 🔶 | N |
| EG-3 | المداخل الأربعة → KMP فقط | B+D | ✅ | N |
| EG-4 | offline: journal + Outbox replay مُختبران | B+D | 🔶 | N |
| EG-5 | iOS E2E جلسة + لقطات | — | ⬜ | Y |
| EG-6 | نظافة commonMain · لا جسور · legacy تدريب UI محذوف | D | 🔶 | N |
| EG-7 | FPS تحليل ≥ خط أساس legacy | — | ⬜ | Y |
| EG-8 | scorecard صادق + خطة أم + `docsStats` | E | 🔶 | N |

#### ج) مؤجَّلات §12 (دفعات 07.1–07.7)

| ID | البند | الوكيل | الحالة | Dev? |
|----|-------|:------:|:------:|:----:|
| D-01 | Frame captures v1 بدون matting (D9) | D | 🔶 | N |
| D-02 | `workout-executions/explore` batch من VM/workout mode | B | ✅ | N |
| D-03 | E2E offline replay على backend حقيقي | B+D | ⬜ | Y |
| D-04 | LiteRT MLP posture/elbow (Android `PoseRefiner`) | — | ⬜ | N |
| D-05 | `git rm` كامل `training/**` (محرك legacy + تقارير XML) | D | 🔶 | N |
| D-06 | حذف `movit.training.kmp.enabled` (D11) | D | ⬜ | N |
| D-07 | deep link تدريب من legacy release بلا shell flag | B | ⬜ | N |
| D-08 | MediaPipe iOS Swift + `movitPoseCaptureIosModule` في shell | — | ⬜ | Y |
| D-09 | smoke يدوي المداخل الأربعة (adb) | E | ⬜ | Y |
| D-10 | Visual QA RTL/Dark/200% حسب Checklist | C+E | ⬜ | Y |
| D-11 | PostTrainingReport golden vs `ReportGenerator` schema | D | ✅ | N |
| D-12 | parity Gson حقل-بحقل `ExerciseConfig` | A | 🔶 | N |
| D-13 | `pose/BodyLandmarks` · `analysis/**` تبعيات legacy للتقارير | D | 🔶 | N |
| D-14 | DS-6: `AudioPrefetchRunner` في مسار فتح الجلسة | B | ✅ | N |
| D-15 | إصلاح `LiveExerciseRunnerTest` / full suite training-engine | A | ✅ | N |

**عدد صفوف المصفوفة: 57** (34 + 8 + 15).

---

### 13.1 سجل تنسيق الوكلاء

> يملأه A/B/C/D عند البدء/الانتهاء/التعارض. Agent E يحدّث من `git diff` عند التنسيق.

| الوكيل | الملفات/المنطقة | الحالة | ملاحظة (2026-06-11 — من diff العمل الجاري) |
|:------:|-----------------|:------:|-----------------------------------------------|
| A | `core:training-engine` — `MovitTrainingEngine` · pipeline · position · journal · `MovitTrainingEngineParityTest` · `config/` · `observability/PipelineTrace` | 🔶 | **07.8-A** — I-10 ✅ · I-16 ✅ · golden squat؛ I-1/I-12/I-14/I-15 ما زالت 🔶 (جهاز/parity موسّع) |
| B | `feature:training` VM/coordinators · `TrainingSessionWriteCoordinator` · shell `MovitInnerRoute.TrainingSession` · DS-6 hooks | ✅ | **07.8-B** — انظر §13.4 |
| C | `core:designsystem` (`MovitSkeletonOverlay` · `TrainingHud` · …) · `feature/training` panels/screens · `training_session_*` strings | ✅ | **07.8-C** — انظر §13.3 |
| D | `MovitPostTrainingReport` · `PostTrainingReportLegacyJson` · `MovitSessionReportUiMapper` · `SessionQualityMeta` · `git rm` supervisor orphan | ✅ | **07.8-D** — انظر §13.5؛ `TrainingEngine.kt` + `WorkoutReportActivity` XML ما زالا |
| E | §13 · `docsStats` → **434** tests · §7 Windows | ✅ | **07.8-E** — جدول §12 محدَّث · مصفوفة §13.0.3 · Reality Audit §4.2 |

| تعارض/قرار | التاريخ | الحسم |
|------------|---------|--------|
| **07.8 إغلاق تكامل (Agent E)** | 2026-06-11 | §7 كامل ✅ — tests+`assembleDebug`+iOS compiles · إصلاح `SkeletonRomGeometry` (`Math`→`PI`) |
| I-4/I-5 خارج ملكية A-D في 07.8 | 2026-06-11 | يُؤجَّل لدفعة pose-capture أو post-07.8 — لا يحجب إغلاق 07.8 |
| `PipelineTrace` API | 2026-06-11 | ✅ A: `snapshot()` · `record(line)` · `record(stage,detail)` — KMP `Mutex` (لا `synchronized`)؛ C يستهلك لـ debug HUD |
| `RestPanel` state fields | 2026-06-11 | ✅ مربوط — `TrainingSessionScreen` + `TrainingSessionRoute` (skip + auto-continue) |

### 13.3 «07.8-C» — UI/UX + a11y + i18n (Agent C — 2026-06-11)

**النطاق:** `core:designsystem` · `feature:training` (composables) · `core:resources` (training strings).

| المهمة | الحالة | ملاحظات |
|--------|--------|---------|
| I-8 ROM arc/line | 🔶 | `SkeletonRomGeometry` + رسم في `MovitSkeletonOverlay` · mapper من landmarks؛ الحساب الكامل → `training-engine/geometry` (Agent A) |
| I-10 debug | 🔶 | `TrainingDebugFpsOverlay` خلف `BuildConfig.DEBUG`؛ `PipelineTrace.snapshot()` جاهز في engine — VM wiring (B) ثم long-press HUD (C) |
| I-26 countdown مجمّد | ✅ | chip + `MovitTag` سبب |
| I-27 flip camera | ✅ | `TrainingCameraFlipButton` 48dp على الشاشة |
| I-28 إذن كاميرا | 🔶 | Android `MovitErrorState` + Settings؛ iOS stub؛ GPU→CPU → pose-capture |
| I-30 a11y | ✅ | `liveRegion` reps · reduce-motion · 48dp |
| I-31 i18n phases | ✅ | `training_phase_*` · `training_setup_phase_*` ar/en |
| I-32 celebration | ✅ | `WorkoutCompletePanel` + `MovitMotion` |

**تحقق:** `:feature:training:testDebugUnitTest` ✅ · `:core:designsystem:testDebugUnitTest` ✅ · `:core:training-engine:compileKotlinIosSimulatorArm64` ✅ · `:feature:training:compileKotlinIosSimulatorArm64` ✅ (`SkeletonRomGeometry` → `kotlin.math`؛ `--rerun-tasks`)

### 13.4 «07.8-B» — Session Flow + Feedback + Audio (Agent B — 2026-06-11)

**النطاق:** `feature/training` (VM · coordinators · routes · write hooks) · `core/training-engine/**/session/**` · `core/training-engine/**/feedback/**` · `core/data` (DS-6 فقط).

#### مهام مُنجزة

| المهمة | الحالة | ملاحظات |
|--------|--------|---------|
| I-29 sets/rest coordinator | 🔶 | `TrainingSessionFlowCoordinator.State.Rest` + `tickRest`/`skipRest` · VM يملأ `restSecondsRemaining` · `nextExerciseName` · `restTip` — **Agent C** يربط `RestPanel` + أزرار skip/start |
| I-17 coach intensity | ✅ | `TimingPolicy.withCoachIntensity` · `FeedbackPolicy.fromCoachIntensity` · `MovitTrainingPreferences` → `FeedbackRouter` + `MovitTrainingEngine` |
| I-11 FeedbackArbiter/Router | ✅ | `MotivationalMessageCoordinator` · `FeedbackRouter.tryDeliverRandomMessage` · TTS fallback عند غياب `AudioFeedbackPlayer` |
| DS-6 audio prefetch | ✅ | `TrainingSessionAudioHooks` في `init` VM · `MovitData.audioPrefetch` |
| B-1 visibility ↔ supervisor | 🔶 | `onVisibilityEvent` → supervisor · قمع NoPose أثناء visibility — TTS موحّد مع Agent A لاحقاً |
| D-02 explore batch | ✅ | `WorkoutExecutionBatchCoordinator` + `WorkoutUploadContext` |
| I-24 typed routes | ✅ | `TrainingSessionRouteArgs` · لا `MODE_*` في مسار KMP |

#### واجهات Agent C

| API | الاستخدام |
|-----|-----------|
| `state.isResting` · `workoutFlowPhase == REST` | إظهار `RestPanel` |
| `restSecondsRemaining` · `nextExerciseName` · `restTip` | props لـ `RestPanel` |
| `viewModel.skipRest()` | تخطي الراحة |
| `viewModel.startWorkoutExercise()` | بدء set من pre-exercise |
| `TrainingSessionRouteArgs(flowItems, uploadContext)` | workout متعدد التمارين |

#### تحقق

`:feature:training:testDebugUnitTest` ✅ · `:core:data:testDebugUnitTest` ✅ — 2026-06-11.

### 13.5 «07.8-D» — Reports + Legacy `training/**` (Agent D — 2026-06-11)

**النطاق:** `core/training-engine/**/report/**` · `core/data` (upload/report mapper) · `feature/reports` (عرض 17) · `app/**/training/**` (تقاعد legacy حيث أمكن).

#### مهام مُنجزة

| المهمة | الحالة | ملاحظات |
|--------|--------|---------|
| I-13 فصل النموذج عن العرض | ✅ | `MovitPostTrainingReport` + `MovitSessionReport` · `MovitSessionReportUiMapper` · `MovitSessionReportUiMapperTest` |
| D-11 PostTrainingReport parity | ✅ | `fixtures/reports/post-training-squat-golden.json` · `PostTrainingReportFieldComparator` · `PostTrainingReportLegacyParityTest` (app) |
| I-20 meta-quality | ✅ | `SessionQualityMeta` من `MotionRecorder` · يُرفق في `legacyReport.sessionQuality` |
| D-01 Frame captures v1 | 🔶 | `MovitPeakFrameCapture` نموذج تخزين فقط — بلا matting (D9) |
| `legacyReport` على الرفع | ✅ | `PostTrainingReportLegacyJson` · `encodePostTrainingReport` · `TrainingSessionWriteHooks` |
| تقليص `training/**` | 🔶 | `git rm` supervisor orphan (5 ملفات) |

#### ما بقي في legacy (مُوثَّق)

| المكوّن | السبب |
|---------|--------|
| `TrainingEngine.kt` | `ReportGenerator.generateFromEngine` · parity tests |
| `training/report/*` + `ui/report/*` | `WorkoutReportActivity` · assessment engine |
| `WorkoutTrainingEngine.kt` | أنواع `WorkoutReport` لشاشات XML |

#### تحقق

`:core:training-engine:testDebugUnitTest` ✅ · `:core:data:testDebugUnitTest` ✅ · `:feature:library:testDebugUnitTest` ✅ · `:feature:reports:testDebugUnitTest` ✅ — 2026-06-11.

### 13.2 معايار إغلاق دفعة 07.8

- كل صفوف المصفوفة ذات **Dev? = N** إمّا ✅ أو 🔶 مُوثَّق في §13.1 بسبب صريح.
- أمر §7 أخضر على Windows (قائمة §7 — `:core:network` contract + `:feature:library` اختياريان إن لم يُمسّا).
- لا تعارض ملكية مفتوح في §13.1.
- تحديث §11 صف «07.8» + سطر تدريب في [Reality Audit](Android-KMP-Mobile-Migration-Reality-Audit.md) §4.2.

---

## 14) مراجعة مستقلة بعد إغلاق 07.8 (Doc-Verify — 2026-06-11)

> تدقيق مستقل (قراءة كود + إعادة تشغيل التحقق فعلياً على Windows) لكل ادعاءات §11–§13. الحكم العام: **العمل حقيقي وضخم ومطابق لبنية الخطة؛ السجل صادق في معظمه**؛ وُجدت **4 فجوات وظيفية لم يلتقطها السجل** + تصحيحان على المصفوفة + ملاحظات نظافة.

### 14.1 ما أُعيد التحقق منه بنجاح (أخضر فعلياً)

| البند | النتيجة المُتحقَّق منها |
|-------|--------------------------|
| §7 Android suite (إعادة تشغيل) | ✅ `BUILD SUCCESSFUL` — نتائج XML: training-engine **101/0** · data **66/0** · library **46/0** · network **22/0** · designsystem **12/0** · training **6/0** · pose-capture **1/0** · reports **2/0** |
| iOS klib compile على Windows | ✅ `training-engine` + `pose-capture` + `feature:training` + `feature:shell` (`compileKotlinIosSimulatorArm64`) |
| `docsStats` | ✅ مُجدَّد 2026-06-11 20:15 — **434** اختبار KMP · ar/en **897/897** متطابقان |
| القطع (WS-10) | ✅ شجرة legacy التدريب محذوفة (~20.6k سطر staged) · Manifest نظيف · المداخل الخمسة legacy (`Home`/`ProgramDay`/`ExerciseDetail`/`QuickStart`/`PreScreening`) → `MovitTrainingEntryNavigator` · deep-link → `MovitShellPendingNavigation` → shell ViewModel يستهلكها · بقايا المراجع للكلاسات المحذوفة = تعليقات KDoc فقط (5 ملفات) |
| الجسور | ✅ `KmpTrainingSessionBridge`/`LegacyKmp*Factory`/`TrainingBoundaryInstall` محذوفة · Koin يسجّل **actuals حقيقية** (`MediaPipePoseDetector` + `CameraXFrameSource`) |
| التكوين | ✅ Sync→`applySyncExercises` (orchestrator:138) · cold seed موصول عبر `MovitData.bootstrapLocalCaches()` ← `MovitAppShellViewModel:43` · 4 fixtures (squat/plank/bilateral/position-checks) + parser test |
| WS-8 جزئياً | ✅ `uploadWorkoutExecution` يُستدعى فعلاً من `finalizeCurrentExercise` (Outbox) · journal checkpoint موصول (`onCheckpoint → checkpointJournal` + `finalizeJournal`) · audio prefetch عند فتح الجلسة (VM:257) · تقرير golden **ضد `ReportGenerator` legacy فعلياً** (`PostTrainingReportLegacyParityTest`) |
| `LegacyWorkoutSyncDrain` | ✅ يُستدعى في `MovitDataInstall` خلف البوابة |
| أمانة iOS | ✅ `IosPoseDetector` stub صريح (يرسل no-pose دائماً) — لا توليد وضعيات وهمية |
| Reality Audit | ✅ §4.2 محدَّث بصدق (~92% بنية · ~65% منتج) |
| `assembleRelease` (`-Pmovit.shell.launcher.enabled=true`) | 🔄 أُعيد تشغيله أثناء المراجعة — النتيجة في §14.7 |

### 14.2 فجوات وظيفية اكتشفتها المراجعة (لم تكن في السجل) 🔴

| # | الفجوة | الدليل | الأثر | التصنيف |
|---|--------|--------|-------|----------|
| **G1** | **دورة حياة اليوم المخطط غير موصولة end-to-end**: `startPlannedWorkout`/`completePlannedWorkout`/`reportPlannedWorkout` معرَّفة في `TrainingSessionWriteHooks` (116/142/164) و**صفر مستدعين إنتاجيين**؛ كذلك `TrainingSessionRoute(flowItems, uploadContext)` لا يُمرَّر له شيء من `MovitInnerHost` (slug/name/reps فقط) → جلسة workout متعددة التمارين وbatch الـ explore غير قابلَين للوصول | grep callers = تعريفات فقط؛ `MovitInnerHost.kt:237` | إكمال اليوم المخطط/التقدّم لن يصل للباك اند من مسار KMP — **هذه تحديداً الفجوة المعلَّقة من Pre-07 التي كان WS-8 يغلقها** | 🔴 تُوصل قبل أي smoke جهاز |
| **G2** | **رسائل تدريب المفاصل (stateMessages) لا تصل للمستخدم**: المحرك يحسبها ثم يهملها — `frameFeedback.emitThrottledStateMessages(...) { _, _ -> }` في `MovitTrainingEngine`، والـ VM لا يشترك فيها؛ `JointErrorCollection` يغذي الـ scoring فقط | قراءة `MovitTrainingEngine.processPoseFrame` + VM | التوجيه الصوتي/النصي الأساسي للفورم (قلب تجربة legacy) غائب — الموجود: position checks + visibility + countdown + تحفيزي | 🔴 وصلة واحدة: callback → `FeedbackRouter` |
| **G3** | **مدة الجلسة تتضمن زمن الإيقاف اليدوي**: `engine.pause()/resume()` لا يستدعيان `ExecutionClock.pause()/resume()` و`stop()` يحسب `nowMs - executionStartMs` بدل `finalizeDurationMs()` — رغم أن `ExecutionClock` يدعم الخصم كاملاً | `MovitTrainingEngine.kt` pause/stop مقابل `ExecutionClock` | `durationMs` المرفوع/المعروض أطول من legacy عند أي pause يدوي | 🟠 إصلاح أسطر قليلة + اختبار |
| **G4** | **لا مسار لعرض التقرير بعد الجلسة**: `WorkoutCompletePanel` يعرض (اسم/عدّات/فورم) بلا زر تقرير؛ `MovitSessionReportUiMapper` **بلا أي مستهلك** | grep consumers = صفر | البيانات تُرفع (legacyReport) لكن المستخدم لا يرى التقرير الغني بعد الإكمال | 🟠 ربط ReportDetail أو توثيق التأجيل |

### 14.3 تصحيحات على مصفوفة §13.0.3

| الصف | كان | يصبح | السبب |
|------|-----|-------|--------|
| **EG-3** «المداخل الأربعة → KMP فقط» | ✅ | **🔶** | صحيح تنقّلياً؛ لكن planned lifecycle (G1) جزء من تعريف مدخل «Session/Home» في الخطة |
| **D-02** «explore batch من VM» | ✅ | **🔶** | `WorkoutExecutionBatchCoordinator` موجود لكن `uploadContext` لا يُمرَّر من أي مسار إنتاجي |

### 14.4 ملاحظات نظافة (غير حاجزة)

1. `ExerciseLive` route + screens أصبحت **dead code** (صفر منافذ تنقّل إليها بعد التحويل لـ `TrainingSession`) — تُحذف مع تحويل الـ POC لاختبار.
2. `StubCameraFrameSource`/`StubPoseDetector` في `pose-capture/androidMain` غير مستخدمة — تُحذف أو تُنقل لاختبارات.
3. تنسيق شاذ (سطر فارغ بين كل سطرين) في `MovitTrainingEngine.kt` وملفات جديدة أخرى — توحيد التنسيق.
4. تعليقات KDoc في 5 ملفات legacy تشير لكلاسات محذوفة (`TrainingActivity` …) — تُحدَّث عند لمسها.
5. `Training-Live-Screen-Spec.md` نحيف (43 سطراً) — يفي كفهرس حالات، لا كمواصفة بصرية كاملة؛ يُثرى قبل جولة Visual QA.
6. اختبارات `TrainingSessionViewModel` الستة سطحية (ثوابت/data classes) — عمق المنطق مغطى في `core:training-engine`، لكن تكامل VM (G1/G2 تحديداً) بلا اختبار، وهو ما سمح بمرور الفجوتين.

### 14.5 توضيح حدود الـ «golden replay» الحالي

`ParityRunner.assertSelfConsistent` = **حتمية** (تشغيل مزدوج لنفس الـ fixture وتطابق الآثار) + تأكيدات سلوك اصطناعية (reps/hold/bilateral/position) — وليست مقارنة ضد مخرجات legacy مسجَّلة. المقارنة الحقيقية ضد legacy موجودة في **التقرير فقط** (`PostTrainingReportFieldComparator`). المصفوفة صادقة هنا (EG-1/I-12/D-12 = 🔶) — يُثبَّت التوضيح لمنع قراءة «golden ✅» بأكثر من معناه.

### 14.6 الحكم وخطوات ما قبل الجهاز

**Phase 07 على Windows: مُنجَزة بنيوياً بنسبة عالية وبجودة توثيق غير مسبوقة في المشروع** — البناء والاختبارات أخضر فعلياً، القطع نظيف، iOS يكمبّل. **لا تُعتبر جاهزة لأول smoke جهاز** قبل إغلاق:

1. **G1** وصل planned lifecycle + تمرير `flowItems`/`uploadContext` من shell (يغلق ديْن Pre-07 الحقيقي).
2. **G2** توصيل joint state messages إلى `FeedbackRouter`.
3. **G3** إصلاح خصم pause من المدة.
4. **G4** زر التقرير بعد الإكمال أو قرار تأجيل مكتوب.
5. اعتماد تصحيحَي EG-3/D-02 (§14.3).

بعدها يبدأ المسار المحجوب بالجهاز/Mac كما في §13.0.2.

### 14.7 نتيجة `assembleRelease`

- `:app:assembleRelease -Pmovit.shell.launcher.enabled=true`: ✅ **BUILD SUCCESSFUL in 9m 8s** (إعادة تشغيل مستقلة — 694 مهمة، 140 منفَّذة) — يؤكد ادعاء 07.7. بهذا كل بنود §7 المتاحة على Windows مُعاد التحقق منها بشكل مستقل.

### 14.8 حالة إغلاق الفجوات G1–G4 (2026-06-12)

| # | الحالة | ملخص الإصلاح |
|---|--------|--------------|
| **G1** | ✅ مغلق | Shell يمرّر `flowItems`/`plannedWorkout`/`workoutId`/`startExerciseIndex` من `TrainingStartAction.KmpLive` → `TrainingSessionRouteArgs`؛ VM يستدعي `startPlannedWorkout`/`completePlannedDay`/`reportPlannedDay` مع تجميع `MovitSessionReport`. |
| **G2** | ✅ مغلق | `MovitTrainingEngine.onJointStateMessage(joint, state, zone)` → VM يحل النص عبر `TrackedJoint.getMessagesForState` + `FeedbackRouter` (`JOINT_QUALITY`). |
| **G3** | ✅ مغلق | `pause`/`resume`/`stop` تمر عبر `SessionOrchestrator` → `ExecutionClock`؛ اختبار `MovitTrainingEngineDurationTest`. |
| **G4** | ✅ مغلق | `TrainingSessionReportCache` + زر «عرض التقرير» في `WorkoutCompletePanel`/`TrainingSessionControls`؛ `SharedReportDetailRepository` يقرأ الكاش قبل API. |

### 14.8 إغلاق G1–G4 — تحقق مستقل (2026-06-12)

| الفجوة | الحالة | الدليل المُتحقَّق منه |
|--------|--------|------------------------|
| **G1** planned lifecycle | ✅ **مغلقة** | `startPlannedWorkoutIfNeeded()` (idempotent) عند أول StartEngine + `finalizePlannedWorkoutDay()` → `completePlannedDay`+`reportPlannedDay`؛ الـ shell يمرّر `flowItems`/`plannedWorkout`/`uploadContext` (`MovitInnerRoute.TrainingSession` موسَّع + `PlannedWorkoutLaunch` من `WorkoutRunViewModel.resolvePlannedWorkoutLaunch`)؛ explore batch عبر `exploreBatch.record` |
| **G2** joint stateMessages | ✅ **مغلقة** | `onJointStateMessage(jointCode, state, zone)` من المحرك → VM يحلّ النص من config (state+zone+phase) → `FeedbackRouter` بخريطة severity + `onJointErrorFeedback` إضافية. *ملاحظة صغرى:* الحلّ يقرأ `poseVariants.firstOrNull()` والمحرك يُبنى بـ `poseVariantIndex = 0` — متسقان اليوم؛ تمرين بـ variant>0 يحتاج تمرير الـ index لاحقاً |
| **G3** خصم pause من المدة | ✅ **مغلقة** | `pause()/resume()` → `session.pause()/resume()` و`stop()` → `session.stop()` (ExecutionClock.finalizeDurationMs)؛ اختبار جديد `MovitTrainingEngineDurationTest` |
| **G4** عرض التقرير | ✅ **مغلقة** | زر تقرير في لوحة الإكمال → `onViewReport` → `MovitInnerRoute.ReportDetail`؛ `TrainingSessionReportCache` (feature:reports) يُغذّى من VM و`SharedReportDetailRepository` يقرأه عبر `MovitSessionReportUiMapper` (لم يعد بلا مستهلك) + اختباران |

**مشكلتان وُجدتا وأُصلحتا أثناء تدقيق الإغلاق:**
1. اختبار جديد لا يُكمبَّل (`TrainingSessionWriteCoordinatorPlannedTest` — توقيع `ReportsSyncRepository` خاطئ) وكان يُفشل `:core:data` كاملاً → أُصلح ليطابق `SyncRepositoryTestSupport` (✅ أخضر).
2. **حذف عرضي** لـ `scripts/generate-docs-stats.ps1` (كسر مهمة `docsStats`) و`scripts/phase06-smoke-adb.ps1` (لازم لـ smoke الجهاز القادم) → **استُعيدا** من git و`docsStats` يعمل.

**التحقق النهائي (2026-06-12):** §7 suite + `:feature:shell`/`:feature:reports`/`:core:network` tests + `assembleDebug` + iOS compiles ×4 — **BUILD SUCCESSFUL** · `docsStats` → **443** اختبار KMP (+9).

**المتبقي من ملاحظات §14.4 (نظافة، غير حاجز):** حذف `ExerciseLive*` dead code · حذف `Stub*` في pose-capture · توحيد تنسيق `MovitTrainingEngine.kt` · تمرير poseVariantIndex لرسائل G2 عند دعم variants متعددة.

---

## 15) جرد تحسينات I-1..I-34 (تدقيق كود 2026-06-12) + باكلوج الجيل الثاني (N-1..N-26)

### 15.1 الحالة الفعلية للتحسينات الـ34 (تحقق كود مباشر، لا نقل عن الجداول)

**الخلاصة: 20 ✅ منفّذة · 8 🔶 جزئية · 6 ⬜ لم تُنفّذ.**

| الحالة | البنود | ملاحظات التدقيق |
|--------|--------|------------------|
| ✅ (20) | I-2 (تفكيك المحرك) · I-3 (Koin) · I-5 (back-pressure: بوابة المحرك + KEEP_ONLY_LATEST، أُزيلت طبقة VM) · I-6 (+G3) · I-7 · I-9 · I-11 · I-13 (+G4) · I-14 (`PresenceSupervisorBridge` → supervisor) · I-17 · I-20 · I-21 · I-22 · I-24 · I-25 · I-26 · I-27 · I-30 · I-31 · I-32 | — |
| 🔶 (8) | **I-1** (نسخ legacy `PauseController`/`HoldTimer` باقية — مرهونة بتقاعد تقارير legacy في Phase 09) · **I-4** (`imageProxy.toBitmap()` ما زال — B-5) · **I-8** (الهيكل بلا ملصقات زوايا/توهج/أسهم إرشاد/نداءات أخطاء — 233 سطراً مقابل 2261 legacy) · **I-10** (الـ trace في المحرك ✅؛ لوحة debug لا تستهلكه — `TrainingDebugOverlay` FPS فقط) · **I-12** (حتمية لا golden-vs-legacy) · **I-15** (`SetupReadinessGate` مبسّط عن `PoseSetupGuide`) · **I-28** (GPU→CPU fallback يعمل صامتاً — بلا إشعار) · **I-29** (rest skip/auto ✅؛ لا تنبيه صوتي قرب النهاية) |
| ⬜ (6) | **I-16** (لا أوزان scoring من config — *تصحيح: سطر 07.8-A الذي علّمها ✅ غير دقيق*) · **I-18** (حارس الخمول) · **I-19** (VelocityFilter) · **I-23** (`.task` ما زالت في `app/assets` + لا إستراتيجية iOS bundle) · **I-33** (شاشة الجلسة الأولى) · **I-34** (pre-flight إضاءة) |

### 15.2 باكلوج الجيل الثاني — N-1..N-26 (أثر/جهد: ع=عالٍ، م=متوسط، ن=منخفض)

#### كود

| # | التحسين | أثر/جهد |
|---|---------|---------|
| N-1 | استبدال الـ 12 callback `var` في `MovitTrainingEngine` بـ `SharedFlow<EngineEvent>` sealed واحد — يقفل سباقات إعادة التعيين ويسهّل الاختبار والـ iOS observation | ع/م |
| N-2 | صفر-allocation في المسار الساخن: تجميع `Landmark` lists وخرائط الزوايا المعاد إنشاؤها كل إطار (قياس قبل/بعد) | ع/م |
| N-3 | حارس انهيار حول `processFrame`: `runCatching` + عدّاد `pipelineTrace` — خطأ إطار واحد لا يُسقط الجلسة | ع/ن |
| N-4 | فحص بنيوي آلي في CI (Konsist أو سكربت): يمنع imports منصّة في commonMain ويمنع عودة Intent-extras للتدريب | م/ن |
| N-5 | microbenchmark لزمن pipeline لكل إطار في CI (ميزانية ≤8ms JVM كمؤشر انحدار) | م/م |

#### منطق المحرك

| # | التحسين | أثر/جهد |
|---|---------|---------|
| N-6 | **درجة ثقة لكل تكرار** (تغطية visibility + استقرار الزوايا) → تُرفق بالتقرير وتستثني التكرارات الملوثة من المتوسطات (يبني على I-20) | ع/م |
| N-7 | **معايرة ROM تكيفية**: أول تكرارين يضبطان مراكز zones ضمن هامش يسمح به config — يقلّص WARNING الكاذبة لاختلافات الأجسام (خلف flag + replay قبل التفعيل) | ع/ع |
| N-8 | تفعيل I-19 عملياً: رفض قمم أسرع من فسيولوجي بالسرعة الزاوية (anti-bounce للأجهزة منخفضة FPS) | م/م |
| N-9 | tempo coaching: مقارنة زمن الطور بوسيط المستخدم → "انزل أبطأ" (البيانات في phaseTimings) | م/م |
| N-10 | كشف جانب البداية تلقائياً في bilateral بدل فرض `startSide` | ن/ن |
| N-11 | hysteresis ديناميكي حسب jitter التتبع (جهاز مهتز → عتبات أوسع مؤقتاً) | م/م |

#### هيكلة

| # | التحسين | أثر/جهد |
|---|---------|---------|
| N-12 | **ترقية الـ journal إلى event-log كامل (append-only)** → أي جلسة ميدانية تصبح fixture قابلة لإعادة التشغيل في commonTest (يغلق I-12 جذرياً) + إمكانية re-score عند تحسين المحرك | ع/م |
| N-13 | ختم `engineVersion` في كل upload/تقرير — يتيح مقارنة أجيال المحرك على السيرفر | م/ن |
| N-14 | `AnalyticsPort` boundary لأحداث قمع الجلسة (setup_confirmed · abandoned@state · completion) — قياس حقيقي للإطلاق | ع/ن |
| N-15 | نقل `.task` إلى `core:pose-capture` + إستراتيجية iOS bundle + فحص توافق إصدار النموذج عند الإقلاع (يغلق I-23) | م/م |
| N-16 | وضع طاقة تكيفي: خفض معدل الالتقاط أثناء IDLE/REST والعودة الفورية مع الحركة (بطارية/حرارة) | م/م |

#### تجربة المستخدم

| # | التحسين | أثر/جهد |
|---|---------|---------|
| N-17 | **وضع تدريب صوتي كامل (TalkBack/VoiceOver)**: سرد كل أحداث الجلسة — أحداث المحرك مهيكلة أصلاً؛ ميزة تنافسية حقيقية لذوي الإعاقة البصرية | ع/م |
| N-18 | Audio ducking (AudioFocus): خفض موسيقى المستخدم بدل الكلام فوقها | م/ن |
| N-19 | بطاقة "أفضل تكرار" بعد الجلسة (البيانات في RepResults) — أساس للمشاركة لاحقاً | م/ن |
| N-20 | مقارنة فورية بآخر جلسة لنفس التمرين: "عمقك تحسّن 8°" (البيانات في كاش التقارير) | ع/م |
| N-21 | تخطي countdown للمتمرسين (بعد N جلسات ناجحة، إعداد) | ن/ن |
| N-22 | تنفيذ I-33 + I-34 معاً: إرشاد وضع الهاتف أول مرة + فحص إضاءة خفيف (متوسط luma من الإطارات) | ع/م |

#### أفكار إبداعية (مدروسة الجدوى)

| # | الفكرة | لماذا واقعية | أثر/جهد |
|---|--------|---------------|---------|
| N-23 | **Ghost skeleton**: هيكل شفاف "مثالي" يتحرك في النطاق الصحيح (يُشتق من ranges الـ config) فوق هيكل المستخدم — المحاكاة البصرية أقوى من الرسائل | الـ renderer والـ ranges موجودان | ع/ع |
| N-24 | شاشة **self-test** مخفية: تشغيل golden fixture على جهاز المستخدم وعرض النتيجة — تشخيص فوري لمشاكل جهاز بعينه في الدعم | الـ fixtures والـ runner موجودان | م/ن |
| N-25 | **حزمة أوفلاين لأسبوع كامل** بزر واحد (configs + صوت لكل تمارين الأسبوع) — امتداد مباشر للـ prefetch القائم | `AudioPrefetchRunner`+`TrainingConfigRepository` جاهزان | ع/م |
| N-26 | شخصيات مدرب صوتية (حماسي/هادئ/تقني) فوق بنية `messageAssignments` القائمة — محتوى من السيرفر بلا كود جديد تقريباً | البنية تدعمها أصلاً | م/م |

### 15.3 الترتيب المقترح

1. **مع دفعة الجهاز (08-A):** N-3 (حارس الانهيار) · N-14 (analytics) · N-13 (engineVersion) · إنهاء I-10/I-28 — كلها صغيرة وتخدم التشخيص الميداني مباشرة.
2. **دفعة جودة المحرك (بعد أول جلسات حقيقية):** N-12 (event-log → fixtures حقيقية) ثم N-6 (ثقة التكرار) ثم N-8/N-11 — بياناتها تأتي من الجهاز.
3. **دفعة تجربة:** إنهاء I-8/I-15/I-29 + N-20 + N-22 + N-18.
4. **التمايز:** N-17 (الوصول الصوتي) وN-23 (ghost) وN-25 (حزمة الأسبوع) — قرارات منتج.
