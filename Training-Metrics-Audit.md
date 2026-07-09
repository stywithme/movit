# تدقيق محرك التدريب في KMP: استخراج الـ Metrics والتقرير

**التاريخ:** 2026-07-09  
**النطاق:** `kmp-app` فقط، من `PoseFrame` داخل `MovitTrainingEngine` حتى بناء `WorkoutUpload` و`MovitPostTrainingReport` ورفع `workout-executions`.

هذه النسخة توثق واقع الكود الحالي فقط. أي أسماء أو مسارات غير موجودة في KMP الحالي أزيلت من التشخيص.

---

## 0. الملخص التنفيذي

المسار الحالي أفضل من حيث العزل المعماري والرفع غير المتزامن، لكنه ما زال يحتوي على مشاكل مؤثرة في دقة الـ metrics، خصوصا ROM، تسرب محاولات العدة الفاشلة، وتجميع المجموعات.

| الحكم الحالي | النتيجة |
|---|---|
| مشاكل مؤكدة حرجة | ROM ما زال يعتمد على أول مفصل فقط، الزوايا المفقودة تتحول إلى 0، محاولات العدة الفاشلة لا تصفر حالة العدة ولا buffer الحركة، وتقرير اليوم يبالغ في عدد المجموعات متعددة الـ sets |
| مشاكل مؤكدة عالية | أخطاء joint/position تدخل `RepCounter` خارج شرط أطوار الحركة، frame captures/replay مفاتيحها `repNumber` فقط وتختلط عبر sets، وغياب البيانات يكافأ في stability/alignment/safety/control |
| بنود تغير حكمها | `MotionRecorder` لا يسجل IDLE/START حاليا، وترتيب إغلاق العدة أصبح موحد الدرجة، والتقرير الغني يرسل داخل `WorkoutExecutionUploadRequestDto.legacyReport` |
| مشاكل جديدة من KMP | `MovitSessionReportBuilder.mergeExercise` يجمع `setsCompleted` كقيم تراكمية، و`TrainingSessionReportCache` ذاكرة فقط بسقف 10، و`ReportsSyncRepository.patchExerciseMetricsFromUpload` يجعل متوسط الفورم المحلي قيمة آخر set |

الأولوية العملية: إصلاح عزل العدة أولا، ثم تصحيح ROM/absence semantics، ثم إعادة تصميم هوية set/rep في التقرير واللقطات.

---

## 1. خريطة المسار الحالي

### 1.1 المسار اللحظي

```
TrainingSessionViewModel.processPoseFrameOnWorker
→ MovitTrainingEngine.processFrame
→ JointAngleTracker.extractTrackedAngles
→ FramePipelineExecutor:
   AngleSmoother → StartPoseGate → PhaseStateMachine → PositionValidator → JointEvaluator
→ RepCounter.updateJointEvals عند أطوار الحركة فقط
→ TrainingMotionSession.onMotionFrameRecorded
→ MotionRecorder.record
```

مهم: `MotionRecorder.record` يستدعى فقط عندما:

- في تمارين العد: `currentPhase != IDLE && currentPhase != START`.
- في تمارين الثبات: `currentPhase == COUNT`.

إذن إطارات الراحة/START لا تدخل buffer الحركة حاليا. لكن هذا لا يمنع تسرب إطارات محاولة فاشلة، لأن `onRepIncomplete` لا يصفر الـ recorder ولا `RepCounter`.

### 1.2 إغلاق العدة

المسار الحالي:

```
PhaseStateMachine.onRepCompleted
→ RepCompletionCoordinator.consumeIfPendingAndHandle
→ repCounter.setPhaseTimings(...)
→ repCounter.completeRep()
→ MovitTrainingEngine.repCounter.onRepCountChanged
→ onRepCompletedForMotion(result, result.phaseTimings, bilateral.currentSide)
→ TrainingMotionSession.finalizeRep(...)
→ MotionRecorder.finalizeRep(score = result.score)
```

الحكم: لا توجد درجتان مختلفتان لنفس العدة بسبب ترتيب الإغلاق. `MotionRecorder` يأخذ الآن `RepResult.score` بعد خصومات position.

### 1.3 التقرير والرفع

```
TrainingSessionViewModel.finalizeCurrentExercise
→ writeHooks.finalizeUpload()
→ writeHooks.buildPostTrainingReport()
→ TrainingSessionReportCache.put(upload.id, report)
→ writeHooks.enqueueExecutionUpload(... legacyReport = postReport)
→ WorkoutUploadMapper.toUploadRequest
→ MobileWriteSyncRepository.uploadWorkoutExecution
→ OfflineWriteQueue
→ POST /api/mobile/workout-executions
```

للتدريب المخطط:

```
accumulateDayReport(...)
→ MovitSessionReportBuilder.fromExerciseExecution / mergeExercise
→ completePlannedDay(...)
→ POST /api/mobile/planned-workouts/{id}/complete
```

الـ rich post-training report يرسل مع تنفيذ التمرين، وليس فقط محليا. تقرير اليوم المخطط يرسل ملخص `MovitSessionReport` منفصل.

---

## 2. مشاكل ROM و MotionRecorder

### M1 — مؤكد حرج: ROM يعتمد على أول مفصل في `trackedJoints`

الموضع: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/MotionRecorder.kt:254` و`:142`.

`setupJointIndices()` يثبت `primaryJointIndex = 0`، ثم `finalizeRep()` يحسب:

```kotlin
rom = MetricsCalculator.calculateROM(currentRepBuffer, primaryJointIndex)
```

هذا يتجاهل `JointRole.PRIMARY`. في تمارين `ANY_SIDE` أو أي config يبدأ بمفصل ثانوي/مفصل غير مرئي، يمكن أن يكون العد والتقييم صحيحين على مفصل primary مرئي، بينما ROM يخرج 0 أو قيمة مفصل مختلف.

الإصلاح المقترح: اجعل `TrainingMotionSession` يمرر `List<TrackedJoint>` أو على الأقل `primaryJointCodes` إلى `MotionRecorder`، واحسب ROM من مفاصل PRIMARY الصالحة: `maxOf(validPrimaryRoms)`.

### M2 — مؤكد حرج: الزاوية المفقودة تتحول إلى 0 بدل sentinel

الموضع: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/MotionDataModels.kt:197`.

```kotlin
val angle = angles[jointName] ?: 0.0
```

لو زاوية غير موجودة في `angles` وليست ضمن `skippedJointCodes`، تسجل كـ 0 درجة صالحة. هذا يلوث ROM والسرعة والثبات، وقد يحول dropout عابر إلى ROM مبالغ فيه أو stability ضعيف جدا.

الإصلاح المقترح: المفتاح المفقود يجب أن يسجل `JOINT_SKIPPED_ANGLE_SENTINEL`، وتظل حسابات downstream تستبعد sentinel كما تفعل بالفعل.

### M3 — مؤكد عال: buffer يمسح نفسه بالكامل عند 300 إطار

الموضع: `MotionRecorder.kt:64-72`.

عند وصول `currentRepBuffer` إلى `MAX_FRAMES_PER_REP` يتم:

```kotlin
framesDropped += currentRepBuffer.size.coerceAtLeast(1)
currentRepBuffer.clear()
lastStates = null
currentRepStartT = (timestamp - recordingStartMs).toInt()
```

لأن KMP لا يسجل IDLE/START، هذا لم يعد يتأثر بالراحة بين العدات. لكنه ما زال خطرا للعدّات البطيئة أو الـ fps العالي: يمسح بداية الحركة ويحسب ROM/velocity/duration من ذيل العدة فقط.

الإصلاح المقترح: استخدام `ArrayDeque` وحذف أقدم إطار فقط، أو ضبط السقف بالزمن لا بعدد الإطارات، مع تسجيل warning في session quality.

### M4 — مؤكد حرج: محاولات العدة الفاشلة تبقى داخل MotionRecorder

المواضع:

- `MovitTrainingEngine.kt:414`: `onRepIncomplete` يرسل السبب فقط.
- `TrainingSessionViewModel.kt:668`: يعرض feedback فقط.
- `MotionRecorder.finalizeRep()` يمسح buffer فقط عند عدة مكتملة.

لو المستخدم نزل نصف نزلة ثم عاد، `PhaseStateMachine` يرسل `NO_TARGET_DEPTH` ولا يحدث `finalizeRep`. الإطارات المسجلة أثناء المحاولة تبقى في `currentRepBuffer` وتدخل مع العدة التالية. هذا يلوث ROM، duration، TUT، velocity، stability، وalignment.

الإصلاح المقترح: إضافة `discardCurrentRepAttempt()` إلى `TrainingMotionSession/MotionRecorder` واستدعاؤها عند `RepIncompleteReason.NO_TARGET_DEPTH` و`TOO_FAST`. بالنسبة لـ `NO_FULL_RETURN` يجب الحذر لأنها قد تكون استمرار دورة فيزيائية وليست محاولة منفصلة.

### M5 — مؤكد متوسط: مقاييس absence لا تزال تكافئ غياب البيانات

المواضع:

- `MotionRecorder.emptyExecutionMetrics()`: `avgStability = 1000`, `avgAlignmentAccuracy = 1000`.
- `MetricsCalculator.calculateTrunkStability()` و`calculateStability()`: أقل من عينتين صالحتين يرجع 1000.
- `MetricsCalculator.calculateAlignmentAccuracy()`: `states == null` يحتسب الإطار جيدًا.
- `MetricsCalculator.calculateBilateralRomSymmetry()`: إذا `maxAvg <= 0` يرجع 1000.

الحكم: النمط الحالي لا يميز بين "أداء مثالي" و"لا توجد بيانات كافية". التقرير الغني يخفي `avgRom` عندما تكون 0، لكنه لا يحل stability/alignment/symmetry/control.

الإصلاح المقترح: جعل هذه الحقول nullable في الـ domain أو إضافة quality flags صريحة، ثم إخفاء metric غير المحسوبة وإعادة توزيع وزن البطاقة.

### M6 — مؤكد متوسط: `tempoConsistency` يستخدم مدة العدة لا tempo الأطوار

الموضع: `MotionRecorder.kt:309-311`.

```kotlin
MetricsCalculator.calculateTempoConsistency(completedRepMetrics.map { it.durationMs })
```

يوجد في `MetricsCalculator` دالة أنسب: `calculateTempoConsistencyFromRepMetrics(repMetricsList)`، لكنها ليست المستخدمة في مسار `MotionRecorder`.

الإصلاح المقترح: استخدام مجموع `RepMetrics.tempo` بدل `durationMs` حتى لا تتأثر القيمة بأي تلوث في بداية/نهاية buffer.

### M7 — مؤكد عال: مدة العدة في التقرير الغني تأتي من `phaseTimings` غير منقاة

المواضع:

- `PhaseStateMachine.kt:260`: يسجل مدة كل `currentPhase` في `phaseTimings`.
- `RepCompletionCoordinator.kt:20-21`: يمرر الخريطة كاملة إلى `RepCounter`.
- `MovitPostTrainingReportBuilderV2.kt:618-619`: يحسب مدة العدة من `rep.phaseTimings.values.sum()`.

`phaseTimings` قد تحتوي START/IDLE، وقد تبقى بها انتقالات من محاولة incomplete لأن `onRepIncomplete` لا يمسح timings. `MotionRecorder` يحسب duration من buffer الحركة فقط، لكن rich report timeline وbest/worst timing penalty وconsistency يستخدمون `RepResult.phaseTimings`.

الإصلاح المقترح: عند إغلاق العدة مرر فقط أطوار الحركة (`DOWN/BOTTOM/UP` أو `COUNT`) إلى `RepResult`، وامسح timings عند `RepIncomplete` المناسب.

### M8 — مؤكد متوسط: stability تقيس تشتت زاوية متحركة

المواضع: `MetricsCalculator.kt:189-214`.

`calculateTrunkStability` و`calculateStability` يستخدمان الانحراف المعياري الخام لزاوية spine أو متوسط hip. في تمارين فيها ميل/حركة مقصودة، هذا يقيس مقدار الحركة لا "الثبات" حول مسار متوقع.

الإصلاح المقترح: حساب deviation حول مسار phase-aware أو detrended path، أو جعل stability metric اختيارية حسب `reportMetrics`.

---

## 3. مشاكل التقييم وعدّ العدات

### S1 — مؤكد حرج: لا يوجد تصفير لمحاولة عدة فاشلة

المواضع:

- `RepCounter` لا يحتوي على API لتصفير محاولة حالية دون تصفير نتائج الجلسة.
- `MovitTrainingEngine.kt:414`: `stateMachine.onRepIncomplete = { reason -> onRepIncomplete?.invoke(reason) }`.
- `TrainingSessionViewModel.kt:668`: `engine?.onRepIncomplete = ::submitRepIncompleteFeedback`.

حالة joint/position/errors وzone trackers تبقى معلقة حتى أول `completeRep()`. لذلك محاولة فاشلة قد تسقط درجة العدة التالية أو تضيف لها أخطاء لم تحدث فيها.

الإصلاح المقترح: `RepCounter.discardCurrentRepAttempt(reason)` يصفر current trackers/errors/position/timings، ويستدعى فقط للأسباب التي تمثل محاولة منفصلة.

### S2 — مؤكد عال: أخطاء joint و position تدخل خارج شرط أطوار الحركة

الموضع: `MovitTrainingEngine.kt:684-732`.

`updateJointEvals` و`MotionRecorder.record` محصوران داخل `shouldTrackState`. لكن بعد ذلك مباشرة:

```kotlin
jointErrors.forEach { repCounter.addError(it) }
pv.errors.forEach { repCounter.addPositionError(it) }
pv.warnings.forEach { repCounter.addPositionWarning(it.checkId) }
pv.tips.forEach { repCounter.addPositionTip(it.checkId) }
```

هذه الإضافات تحدث حتى في `IDLE/START` داخل حالة TRAINING. النتيجة: تحذير وضعية أثناء الاستعداد أو الرجوع إلى start قد يخصم من العدة التالية.

الإصلاح المقترح: تطبيق نفس شرط `shouldTrackState` على `addError/addPosition*`، أو شرط أدق `currentPhase in DOWN/BOTTOM/UP/COUNT`.

### S3 — مؤكد عال: `JointEvaluator` يسمح بـ outward fallback فوري

الموضع: `JointEvaluator.kt:174-177`.

في `applyHysteresis`:

```kotlin
if (isOutwardFallback) {
    previousStates[jointCode] = rawState
    dangerFrameCounts[jointCode] = 0
    return rawState
}
```

هذا يتجاوز `minDangerFrames`. أي DANGER صادر من outward fallback يمكن أن يصبح حالة العدة من إطار واحد، بينما مسار DANGER العادي ينتظر التأكيد.

الإصلاح المقترح: اجعل outward fallback يمر بنفس عداد `dangerFrameCounts` عند DANGER، أو أضف dwell صغير قبل تصعيد الحالة إلى DANGER.

### S4 — مؤكد متوسط: تبديل جانب bilateral لا يمسح caches

المواضع:

- `BilateralController.kt:18-45` يغير الجانب فقط.
- `FramePipelineExecutor.kt:63` يمسح `AngleSmoother` فقط عند `skippedForFrame`.
- لا يوجد ربط لـ `BilateralController.onSideChanged` يمسح `AngleSmoother` أو `JointEvaluator`.

أول إطارات بعد تبديل الجانب قد تستخدم تاريخ smoothing/hysteresis من الجانب السابق.

الإصلاح المقترح: عند `onSideChanged` امسح الأزواج المرآوية في `AngleSmoother` و`JointEvaluator`، أو أعد تهيئة pipeline side-local state.

### S5 — مؤكد متوسط: scoring للعدات الحركية يعتمد على أسوأ حالة مؤكدة لا وزن زمني

المواضع: `RepCounter.PrimaryRepZoneTracker` و`ScoreCalculator.calculateRepScore`.

التصميم الحالي جيد كبداية، لكنه يجعل إطار WARNING/DANGER مؤكد واحدا يسقف العدة كلها. تمارين hold تستخدم وزنًا زمنيا، أما العدات الحركية فلا.

الإصلاح المقترح: إما time-weighted state accumulation للعدادات الحركية، أو dwell threshold قبل اعتماد WARNING/DANGER في `PrimaryRepZoneTracker`.

### S6 — مؤكد متوسط: hold ينهار عند أي DANGER مهما كان زمنه

الموضع: `ScoreCalculator.kt:96-108`.

```kotlin
if (dangerTime > 0) return score = 0, isInvalidated = true
```

إطار DANGER عابر داخل hold يلغي التمرين كله.

الإصلاح المقترح: عتبة مثل `dangerTime >= 300ms` أو نسبة من إجمالي hold قبل invalidation.

---

## 4. مشاكل التقرير و multi-set

### R1 — مؤكد حرج: تقرير اليوم يبالغ في عدّ المجموعات

المواضع:

- `TrainingSessionViewModel.kt:876-884`: يمرر `currentSetNumber` كـ `setsCompleted`.
- `MovitSessionReport.kt:117-118`: يجمع `setsCompleted` و`totalSets` جمعا مباشرا.

سيناريو 3 sets لنفس التمرين:

| set | القيمة المرسلة | التجميع الحالي |
|---|---:|---:|
| 1 | `setsCompleted=1`, `totalSets=3` | 1/3 |
| 2 | `setsCompleted=2`, `totalSets=3` | 3/6 |
| 3 | `setsCompleted=3`, `totalSets=3` | 6/9 |

المفروض 3/3. هذا يؤثر على `/complete` للتدريب المخطط وعلى أي واجهة تعتمد على `MovitSessionReport`.

الإصلاح المقترح: عند كل set مكتمل مرر delta واضحا `setsCompletedDelta = 1` و`setsPlannedDelta = 1`، أو اجمع على مستوى exercise slug بحيث آخر set يحدث سجل التمرين بدلا من إضافة سجل جديد.

### R2 — مؤكد عال: التقرير الغني لا يملك هوية set فعلية

المواضع:

- `MovitRepTimelineEntry.setNumber` default = 1 في `MovitPostTrainingReportEnrichment.kt:156`.
- `MovitPostTrainingReportBuilderV2.generateTimeline()` لا يمرر set number.
- `MovitPostTrainingReportAnalysis.setSummaries` يبقى empty.

كل rich report يمثل تنفيذ set واحد فعليا، لكن الـ model يوحي أنه قادر على multi-set. `feature/reports` يحاول رسم form by set من `repTimeline` أو `setSummaries`، لكن builder لا يغذي هذه البيانات.

الإصلاح المقترح: إما إعلان التقرير الغني كـ per-set فقط وإخفاء رسوم by-set، أو إضافة set-aware aggregation في builder واستعمال `(setNumber, repNumber)`.

### R3 — مؤكد عال: frame captures/replay تختلط عبر sets لنفس التمرين

المواضع:

- `TrainingFrameCaptureCoordinator` لا يعاد إنشاؤه بين sets لنفس exercise.
- `MovitPeakFrameCaptureManager` يستخدم مفاتيح `repNumber` فقط: `peakByRep`, `errorsByRep`.
- `MovitRepReplaySampler` يستخدم `replayFramesByRep` فقط.

في set 2، rep 1 يصطدم مع rep 1 من set 1. قد لا تسجل peak frame جديدة، وقد تظهر صورة/Replay من set سابق في تقرير set لاحق.

الإصلاح المقترح: مفاتيح مركبة `(setNumber, repNumber)`، أو reset كامل للـ capture coordinator عند بداية كل set.

### R4 — مؤكد متوسط: أفضل/أسوأ عدة موجودان، لكن نطاقهما per-set

الموضع: `MovitPostTrainingReportBuilderV2.kt:181-260`.

لا توجد مشكلة "لا يوجد best/worst" عند وجود `repDetails`: `findBestRepsByScore` و`findWorstRep` يرجعان نتائج من reps الحالية. لكن لأن التقرير الغني يبنى لكل upload/set، فلا يوجد best/worst على مستوى تمرين متعدد المجموعات أو يوم كامل.

الإصلاح المقترح: عند عرض report لتمرين متعدد sets، اجمع repDetails عبر sets قبل اختيار best/worst، أو سمّ العناوين بوضوح "أفضل عدة في هذه المجموعة".

### R5 — مؤكد متوسط: alerts محدودة كقائمة عرض، فلا تصلح كعداد

الموضع: `MovitPostTrainingReportBuilderV2.kt:23` و`:114`.

`MAX_DANGER_ALERTS = 2`. هذا مناسب للعرض، لكن أي UI أو تحليل يعتمد على `dangerAlerts.size` سيحصل على عداد مسقوف. مسار `ReportQualityScoring` الحالي يستخدم `invalidatedReps` وليس `dangerAlerts.size`، وهذا جيد.

الإصلاح المقترح: أضف `dangerRepCount` صريحا في summary ولا تستخدم lists المسقوفة كعدادات.

---

## 5. مشاكل الرفع والتخزين المحلي

### U1 — مصحح حاليا: التقرير الغني يرسل مع تنفيذ التمرين

المواضع:

- `TrainingSessionViewModel.kt:1032-1055`.
- `TrainingSessionWriteHooks.kt:115-126`.
- `WorkoutUploadMapper.kt:19-35`.

`MovitPostTrainingReport` يشفّر ويدخل في `WorkoutExecutionUploadRequestDto.legacyReport`. إذن التقرير الغني ليس محليا فقط في مسار تنفيذ التمرين.

ملاحظة: `/complete` للتدريب المخطط يحمل `MovitSessionReport` الملخص، لا التقرير الغني. هذا مقبول إذا كان backend يعتمد على `workout-executions` للتفاصيل.

### U2 — مؤكد عال: cache التقرير داخل التطبيق ذاكرة فقط وبسقف 10

الموضع: `feature/reports/.../TrainingSessionReportCache.kt:12-14`.

التقرير الغني يخزن في `MovitLruCache` داخل الذاكرة. بعد process death أو بعد أكثر من 10 تقارير، لا توجد نسخة محلية غنية يمكن فتحها إلا إذا جاءت من backend عبر endpoint آخر.

الإصلاح المقترح: تخزين `MovitPostTrainingReport` في SQL/cache دائم keyed by report id، أو إضافة endpoint hydrate للتقرير الغني من backend.

### U3 — مؤكد عال: patch metrics المحلي يجعل المتوسط قيمة آخر set

الموضع: `ReportsSyncRepository.kt:127-154`.

`patchExerciseMetricsFromUpload` يفعل:

```kotlin
averageFormScore = request.executionMetrics.avgFormScore
setsCompleted = (summary?.setsCompleted ?: 0) + 1
totalReps = (summary?.totalReps ?: 0) + request.countedReps
```

النتيجة أن `averageFormScore` ليس متوسطا تراكميا بل قيمة آخر upload، بينما العدادات تراكمية. هذا يجعل شاشة التقارير المحلية غير متسقة حتى قبل sync.

الإصلاح المقترح: حساب متوسط موزون بالعدات/sets، أو الاحتفاظ بقائمة set summaries محلية ثم اشتقاق summary منها.

### U4 — مصحح حاليا: outbox لا يسد الطابور عند 4xx وله سقف retry

المواضع:

- `OutboxDispatcher.kt` يحول 4xx إلى `PERMANENT_FAILURE`.
- `OfflineWriteQueue.kt` يحدث status ويكمل باقي الصفوف.
- `OutboxConflictPolicy.MAX_ATTEMPTS = 3`.
- `OutboxReplayOrdering` يرفع `WORKOUT_EXECUTION_UPLOAD` قبل planned complete.

الحكم: لا توجد مشكلة retry/blocking في مسار KMP الحالي.

### U5 — مؤكد متوسط: عدم وجود auth يمنع enqueue التنفيذ

الموضع: `MobileWriteSyncRepository.uploadWorkoutExecution()`.

إذا لم يوجد auth يرجع Failure ولا يدخل upload في outbox. التقرير الغني قد يبقى في memory cache فقط. لو هذا مقصود لتدريبات الضيف فهو يحتاج سياسة واضحة؛ لو لا، فهو فقدان بيانات محتمل.

الإصلاح المقترح: إما منع بدء جلسة ترفع metrics بدون auth، أو تخزين local pending execution ثم ربطه بالحساب بعد تسجيل الدخول.

---

## 6. وحدات القياس الحالية

| القيمة | التخزين الداخلي | الرفع للباك | الحكم |
|---|---:|---:|---|
| زوايا `FrameSample.angles` | `Short`, درجة ×10، sentinel = `Short.MIN_VALUE` | لا ترفع raw | صحيح إلا مشكلة missing key = 0 |
| `RepMetricsData.score` | `Short` ×10 | Float 0-100 | صحيح |
| `RepMetrics.rom` / `avgRom` | `Short` درجة ×10 | Float درجة | صحيح كوحدة، لكن اختيار المفصل/absence خاطئ |
| `velocity` | `Short` deg/s تقريبا | Float deg/s | الوحدة الحالية متسقة في mapper |
| `fatigueIndex` | رقم عدة | Float في DTO | يحتاج اسم أوضح مثل `fatigueOnsetRep` |
| `stability/alignment/symmetry` | 0-1000 | Float 0-100 | يحتاج nullable/quality semantics |

---

## 7. بنود لا تنطبق على KMP الحالي

هذه ليست مشاكل في الكود الحالي:

- تسجيل IDLE/START في `MotionRecorder`: غير قائم حاليا لأن التسجيل محصور في أطوار الحركة/COUNT.
- اختلاف score بين التقرير ورفع `repMetrics` بسبب ترتيب `finalizeRep`: غير قائم حاليا لأن `finalizeRep` يأخذ `RepResult.score`.
- انسداد outbox بسبب 4xx أو retry لا نهائي: غير قائم في `OfflineWriteQueue`.
- تقرير غني محلي فقط: غير دقيق حاليا؛ التقرير يرفع داخل `legacyReport` مع تنفيذ التمرين.
- تحويل ROM إلى نسبة باستخدام target ROM من variant 0: لا يوجد مسار target ROM في KMP report الحالي؛ `avgRom` يعرض/يرفع كدرجات.

---

## 8. خطة الإصلاح المقترحة

### المرحلة 1 — عزل العدة وتصحيح ROM

1. `anglesToShortArray`: المفتاح المفقود يسجل sentinel.
2. `MotionRecorder`: استخدم مفاصل PRIMARY بدل index 0.
3. `RepCounter` و`MotionRecorder`: أضف discard لمحاولة العدة الفاشلة.
4. `MovitTrainingEngine`: اجعل `addError/addPosition*` داخل شرط أطوار الحركة.
5. `JointEvaluator`: لا تسمح لـ outward DANGER بتجاوز `minDangerFrames`.

### المرحلة 2 — multi-set وهوية الأدلة

1. أصلح `setsCompleted/totalSets` في `MovitSessionReportBuilder`.
2. أضف `setNumber` إلى `WorkoutUpload` أو سياق بناء التقرير.
3. اجعل frame captures/replay بمفتاح `(setNumber, repNumber)` أو reset coordinator لكل set.
4. ابن `setSummaries` فعليا أو أخف رسومات by-set للتقارير per-set.

### المرحلة 3 — أمانة الـ metrics والغياب

1. اجعل stability/alignment/symmetry nullable عند غياب بيانات كافية.
2. استخدم `calculateTempoConsistencyFromRepMetrics`.
3. صحح `calculateBilateralRomSymmetry`: صفر/صفر = null وليس 100.
4. راجع `ReportQualityScoring.calculateSafetyScore/controlScore` حتى لا يكافئ عدم وجود بيانات.
5. سم `fatigueIndex` باسم يوضح أنه رقم عدة لا نسبة.

### المرحلة 4 — التخزين والـ sync

1. اجعل `TrainingSessionReportCache` backed by persistent storage.
2. اجعل patch metrics المحلي يحسب متوسطا موزونا لا قيمة آخر upload.
3. حدد سياسة واضحة لتدريبات بلا auth: منع، local-only، أو pending بعد تسجيل الدخول.

---

## 9. اختبارات مطلوبة

- `MotionRecorderAnySideRomTest`: نفس الأداء على الجانب المرئي الآخر يعطي ROM غير صفري ومتقارب.
- `MotionRecorderMissingAngleSentinelTest`: missing key لا يدخل 0 في ROM/stability.
- `RepIncompleteIsolationTest`: محاولة فاشلة قبل عدة مثالية لا تغير score ولا ROM للعدة المثالية.
- `RepTimingMovementOnlyTest`: مدة timeline وconsistency لا تشمل START/IDLE ولا timings محاولة incomplete.
- `PositionErrorsMovementPhaseTest`: position warning في START لا يخصم من العدة التالية.
- `PlannedMultiSetReportTotalsTest`: 3 sets تعطي `totalSetsCompleted=3` و`totalSetsPlanned=3`.
- `FrameCaptureSetIsolationTest`: rep 1 في set 2 لا يستخدم صورة rep 1 من set 1.
- `ReportsPatchWeightedAverageTest`: cache metrics المحلي يحسب average form score موزونا.

---

## 10. تحقق هذه المراجعة

تمت مطابقة الملف مع مسارات KMP التالية:

- `core/training-engine/.../session/MovitTrainingEngine.kt`
- `core/training-engine/.../session/RepCompletionCoordinator.kt`
- `core/training-engine/.../engine/RepCounter.kt`
- `core/training-engine/.../engine/evaluation/JointEvaluator.kt`
- `core/training-engine/.../journal/MotionRecorder.kt`
- `core/training-engine/.../journal/MotionDataModels.kt`
- `core/training-engine/.../journal/MetricsCalculator.kt`
- `core/training-engine/.../report/MovitPostTrainingReport.kt`
- `core/training-engine/.../report/MovitPostTrainingReportBuilderV2.kt`
- `feature/training/.../TrainingSessionViewModel.kt`
- `feature/training/.../TrainingFrameCaptureCoordinator.kt`
- `core/data/.../TrainingSessionWriteCoordinator.kt`
- `core/data/.../ReportsSyncRepository.kt`
- `core/data/.../outbox/*`

لم يتم تشغيل اختبارات Gradle لأن التغيير المطلوب هنا توثيقي ومحصور في ملف المراجعة. التوصية التالية هي تحويل اختبارات §9 إلى common tests قبل أي تعديل سلوكي.
