# تدقيق محرك التدريب في KMP: استخراج الـ Metrics والتقرير

**التاريخ:** 2026-07-09
**النطاق:** `kmp-app` فقط، من `PoseFrame` داخل `MovitTrainingEngine` حتى `WorkoutUpload` و`MovitPostTrainingReport` ورفع `workout-executions`.
**الحالة:** الوضع الحالي مُراجع من واقع الكود ومثبت باختبارات `testAndroidHostTest`. لا توجد بنود إصلاح مفتوحة داخل هذا النطاق.

---

## 1. النتيجة التنفيذية

مسار الـ metrics الحالي متسق على مستوى العدة، الـ set، والتقرير:

| المحور | الحكم الحالي |
|---|---|
| عزل العدة | `IDLE`/`START` خارج التسجيل، والمحاولات غير المكتملة المعتمدة تُمسح من recorder وcounter |
| ROM والسرعة | ROM من مفاصل `PRIMARY`، والسرعة تختار primary joint به عينتان concentric صالحتان |
| الغياب وجودة البيانات | missing angles تُسجل sentinel؛ stability/alignment/symmetry nullable؛ compressed states تُحسب بـ fill-forward |
| التقييم | أخطاء joint/position داخل أطوار الحركة فقط؛ outward `DANGER` يحترم `minDangerFrames` من أول frame |
| multi-set | `setNumber` حاضر في التقرير واللقطات والـ replay؛ الدمج cross-set يتم عند العرض والكاش |
| التخزين والرفع | التقرير الغني يُخزن دائمًا ويرفع per-set في `legacyReport`; guest execution يدخل outbox بدون auth |

---

## 2. مسار التشغيل الحالي

```text
TrainingSessionViewModel.processPoseFrameOnWorker
-> MovitTrainingEngine.processFrame
-> JointAngleTracker.extractTrackedAngles
-> FramePipelineExecutor
   -> AngleSmoother
   -> StartPoseGate
   -> PhaseStateMachine
   -> PositionValidator
   -> JointEvaluator
-> if shouldTrackState:
   -> RepCounter.updateJointEvals
   -> TrainingMotionSession.onMotionFrameRecorded
   -> MotionRecorder.record
   -> RepCounter.addError / addPosition*
```

`shouldTrackState` الحالي:

| نوع التمرين | شرط تسجيل الحالة |
|---|---|
| عدات | `currentPhase != Phase.IDLE && currentPhase != Phase.START` |
| ثبات | `currentPhase == Phase.COUNT` |

عند `RepIncompleteReason.NO_TARGET_DEPTH` أو `RepIncompleteReason.TOO_FAST`:

```text
shouldDiscardRepAttemptOnIncomplete(reason)
-> RepCounter.discardCurrentRepAttempt(reason)
-> PhaseStateMachine.clearTimings()
-> TrainingSessionViewModel.submitRepIncompleteFeedback
-> TrainingSessionWriteHooks.discardCurrentRepAttempt()
-> MotionRecorder.discardCurrentRepAttempt()
```

`NO_FULL_RETURN` و`TOO_SLOW` لا يمسحان المحاولة الحالية.

---

## 3. عقود الـ Metrics الحالية

### 3.1 MotionRecorder

- `TrainingMotionSession` يستخرج فهارس مفاصل `JointRole.PRIMARY` ويمررها إلى `MotionRecorder(primaryJointIndices)`.
- `MetricsCalculator.calculatePrimaryROM` يحسب ROM من أكبر مدى صالح بين مفاصل `PRIMARY`.
- `MetricsCalculator.primaryJointIndexForVelocity` يفضل أول primary joint لديه عينتان صالحتان في `PhaseCode.CONCENTRIC`.
- أي joint angle غير موجود أو متخطى يُخزن كـ `JOINT_SKIPPED_ANGLE_SENTINEL`.
- الحسابات التي تقرأ الزوايا تستبعد sentinel ولا تحوله إلى صفر.
- `currentRepBuffer` يعمل كـ ring buffer بسقف `MAX_FRAMES_PER_REP = 300` ويحذف أقدم frame عند الامتلاء.
- `discardCurrentRepAttempt()` يمسح buffer المحاولة الحالية فقط ولا يلمس العدات المكتملة.

### 3.2 Nullable Metrics

- `RepMetrics.stability`, `RepMetrics.alignmentAccuracy`, `RepMetrics.symmetry` nullable.
- `WorkoutExecutionMetrics.avgStability`, `avgAlignmentAccuracy`, `avgSymmetry` nullable.
- `WorkoutUploadMapper` يحافظ على null في DTO ولا يحوله إلى 100 أو 0.
- `MetricsCalculator.calculateAlignmentAccuracy` يعيد بناء frames ذات `states = null` كـ repeated state من آخر ByteArray محفوظ، لأن null هنا ضغط بيانات وليس غيابًا دائمًا.
- لو لا توجد state coverage فعلية، alignment يرجع `null`.

### 3.3 Tempo وStability وFatigue

- `RepCounter.setPhaseTimings` يصفّي إلى `MOVEMENT_TRACKING_PHASES`: `DOWN`, `BOTTOM`, `UP`, `COUNT`.
- `MotionRecorder` يحسب `tempoConsistency` من `RepMetrics.tempo` وليس من مدة العدة الكاملة.
- trunk/hip stability تُحسب من detrended residual spread حتى لا تُعاقب الحركة الطبيعية للزاوية.
- `fatigueIndex` يمثل رقم العدة التي بدأ عندها الهبوط، 1-based، مع بقاء اسم الحقل للتوافق مع API.

---

## 4. التقييم وعد العدات

- `RepCounter.discardCurrentRepAttempt(reason)` يمسح trackers/errors/position/timings للحالة الحالية فقط.
- `RepCounter.addError` و`addPositionError` و`addPositionWarning` و`addPositionTip` تُستدعى داخل `shouldTrackState` فقط.
- `JointEvaluator.applyHysteresis` يؤخر `DANGER` حتى `StabilityPolicy.minDangerFrames`، بما في ذلك أول frame في outward fallback.
- `bilateral.onSideChanged` يعيد تهيئة `AngleSmoother` و`JointEvaluator`.
- `PrimaryRepZoneTracker` يحتاج إطارين severity متتاليين قبل تأثير `WARNING` أو `DANGER` على درجة العدة.
- `ScoreCalculator.calculateHoldScore` يبطل تمرين الثبات عند `dangerTime >= 300ms`.

---

## 5. التقرير والـ Multi-Set

### 5.1 Session Report

- `MovitSessionReportBuilder.fromExerciseExecution` ينشئ تقرير تمرين واحد.
- `MovitSessionReportBuilder.mergeExercise` يدمج حسب `exerciseSlug`.
- كل upload يضيف `totalSetsCompleted + 1`.
- `totalSetsPlanned` يُضاف مرة واحدة لكل تمرين جديد.
- متوسط form/accuracy موزون بعدد الـ sets.

### 5.2 Post-Training Report

- `MovitPostTrainingReportBuilderV2` يستقبل `setNumber` و`repsTarget`.
- `setNumber` ينتقل إلى `setSummaries`, `repTimeline`, `bestReps`, `worstRep`, `dangerAlerts`, `perfectMoments`, captures, وreplay clips.
- `MovitPerformanceSummary.dangerRepCount` يُحسب من كل `repDetails` وليس من قائمة alerts المسقوفة.

### 5.3 Frame Evidence

- `MovitPeakFrameCaptureManager` يستخدم مفتاح `(setNumber, repNumber)`.
- `MovitRepReplaySampler` يستخدم مفتاح `(setNumber, repNumber)`.
- `TrainingFrameCaptureCoordinator.beginSet(setNumber)` يعيد تهيئة evidence عند set جديد.
- async capture jobs تثبت `setNumber` وقت طلب اللقطة، وتُلغى pending jobs عند بدء set جديد.
- replay sampler يأخذ عينة فورية عند التشغيل ثم يتابع كل `SAMPLE_INTERVAL_MS`.

### 5.4 Cross-Set Display

- `TrainingSessionReportCache.put(uploadId, report, sessionExerciseKey, setNumber)` يفهرس تقارير الـ sets.
- `TrainingSessionReportCache.getMergedForDisplay(reportId)` يجمع sibling reports لنفس `sessionExerciseKey`.
- `MovitPostTrainingReportCrossSetAggregator` يدمج `setSummaries` و`repTimeline` ويعيد best/worst على مستوى كل الـ sets.
- الرفع إلى backend يبقى per-set داخل `legacyReport`; الدمج الحالي للعرض والكاش.

---

## 6. التخزين والرفع

- `TrainingSessionReportCache` يستخدم LRU في الذاكرة بسعة 10 تقارير.
- عند تهيئة `MovitData`، `PostTrainingReportLocalStore` يخزن post-training وsession reports في `MovitLocalStore`.
- فهرس multi-set الدائم: `registerExerciseSetReport(sessionExerciseKey, setNumber, reportId)`.
- `rekeyPostTraining(fromId, toId)` يحدث التقرير والفهارس في الذاكرة والتخزين الدائم.
- `TrainingSessionWriteCoordinator.uploadWorkoutExecution` يضيف `legacyReport` الغني إلى `WorkoutExecutionUploadRequestDto`.
- `MobileWriteSyncRepository.uploadWorkoutExecution` لا يشترط auth؛ يستخدم `request.id` كـ operation/idempotency key ويدخل outbox.
- `OfflineWriteQueue.replayPending` لا يرفع إلا عند توفر auth والشبكة.
- planned workout start/complete/report ما زالت تتطلب auth.
- `ReportsSyncRepository.patchExerciseMetricsFromUpload` يحدث cache المحلي بمتوسط form موزون بعدد الـ sets.

---

## 7. وحدات القياس والـ DTO

| القيمة | التخزين الداخلي | الرفع |
|---|---:|---:|
| `FrameSample.angles` | `Short` درجة ×10، وsentinel = `Short.MIN_VALUE` | لا ترفع raw |
| `RepMetricsData.score` | `Short` ×10 | `Float` 0-100 |
| `RepMetrics.rom` / `avgRom` | `Short` درجة ×10 | `Float` درجة |
| `velocity` | `Short` deg/s تقريبًا | `Float` deg/s |
| `stability` / `avgStability` | `Short?` 0-1000 أو null | `Float?` 0-100 أو محذوف |
| `alignmentAccuracy` / `avgAlignmentAccuracy` | `Short?` 0-1000 أو null | `Float?` 0-100 أو محذوف |
| `symmetry` / `avgSymmetry` | `Short?` 0-1000 أو null | `Float?` 0-100 أو محذوف |
| `fatigueIndex` | رقم عدة 1-based | `Float?` باسم API الحالي |
| `dangerRepCount` | `Int` في summary | داخل `legacyReport` |

---

## 8. ملفات الكود المطابقة

| الملف | المسؤولية |
|---|---|
| `core/training-engine/.../session/MovitTrainingEngine.kt` | gating، discard، side reset |
| `core/training-engine/.../engine/RepCounter.kt` | score، position penalties، phase timings |
| `core/training-engine/.../engine/Phase.kt` | movement phases وrep incomplete policy |
| `core/training-engine/.../engine/ScoreCalculator.kt` | rep/hold scoring |
| `core/training-engine/.../engine/evaluation/JointEvaluator.kt` | hysteresis و`minDangerFrames` |
| `core/training-engine/.../journal/MotionRecorder.kt` | frame buffer، rep metrics، execution metrics |
| `core/training-engine/.../journal/MotionDataModels.kt` | sentinel وnullable models |
| `core/training-engine/.../journal/MetricsCalculator.kt` | ROM، velocity، alignment، stability، consistency |
| `core/training-engine/.../report/MovitPostTrainingReport*.kt` | report builders وJSON |
| `core/training-engine/.../report/MovitSessionReport.kt` | session/day report merge |
| `feature/training/.../TrainingFrameCaptureCoordinator.kt` | captures/replay set isolation |
| `feature/training/.../TrainingSessionViewModel.kt` | finalize، cache، upload، planned completion |
| `feature/reports/.../TrainingSessionReportCache.kt` | LRU، persistent load، cross-set merge |
| `core/data/.../PostTrainingReportLocalStore.kt` | durable report store |
| `core/data/.../WorkoutUploadMapper.kt` | nullable DTO mapping |
| `core/data/.../MobileWriteSyncRepository.kt` | guest execution outbox |
| `core/data/.../ReportsSyncRepository.kt` | local exercise metrics patch |

---

## 9. الاختبارات المنفذة

| اختبار | يغطي |
|---|---|
| `MotionRecorderAnySideRomTest` | ROM من primary، واختيار velocity joint بعينات concentric صالحة |
| `MotionRecorderMissingAngleSentinelTest` | missing angle sentinel |
| `RepIncompleteIsolationTest` | discard buffer/current rep state |
| `RepTimingMovementOnlyTest` | phase timings لأطوار الحركة فقط |
| `PositionErrorsMovementPhaseTest` | position warnings لا تتسرب من setup |
| `JointEvaluatorTest` | outward fallback danger يحترم `minDangerFrames` من أول frame |
| `MetricsCalculatorTest` | detrended stability، bilateral null، alignment fill-forward |
| `PlannedMultiSetReportTotalsTest` | planned totals 3/3 بدون تضخيم |
| `MultiSetRichReportCrossSetIntegrationTest` | cross-set best/worst وform-by-set |
| `FrameCaptureSetIsolationTest` | مفاتيح capture حسب set |
| `TrainingFrameCaptureCoordinatorTest` | replay sampling وasync pending capture isolation |
| `ReportsPatchWeightedAverageTest` | متوسط form موزون بالـ set |
| `PostTrainingReportLocalStoreTest` | تخزين واسترجاع التقارير والفهارس |
| `MobileWriteSyncRepositoryTest` | guest execution enqueue |
| `TrainingSessionReportCacheTest` | LRU، session report، cross-set merge |

تشغيل التحقق:

```text
.\gradlew.bat testAndroidHostTest
```

النتيجة الحالية: `BUILD SUCCESSFUL`.

---

## 10. الحالة النهائية

| المرحلة | الحالة |
|---|---|
| عزل العدة وROM | Done |
| nullable metrics وabsence semantics | Done |
| alignment مع compressed states | Done |
| evaluator dwell وhold policy | Done |
| multi-set totals وset identity | Done |
| frame capture/replay isolation | Done |
| persistent report cache وcross-set display | Done |
| guest execution outbox | Done |
| الاختبارات المستهدفة | Pass |

هذه الوثيقة تصف الوضع الحالي فقط داخل `kmp-app`.
