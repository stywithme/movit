# المحور E — متانة جلسة التدريب (Training Session Durability)
**المراجع:** independent review agent · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**
`TrainingSessionWriteCoordinator.kt`, `TrainingSessionWriteHooks.kt`, `TrainingMotionSession.kt`, `MotionRecorder.kt` (مسارات journal/finalize/restore فقط — لا تكرار لتدقيق المتريكس), `MetricsCalculator.kt` (حدود التخزين فقط), `SessionJournalStore.kt`, `SessionJournal.sq`, `WorkoutUploadMapper.kt` (+ `git diff` الحالي), `PostTrainingReportLocalStore.kt`, `WorkoutExecutionBatchCoordinator.kt`, `TrainingSessionPlannedWritePolicy.kt`, `TrainingSessionLifecyclePolicy.kt`, `TrainingSessionViewModel.kt` (مسارات attach/finalize/upload/batch/planned), `TrainingSessionReportCache.kt`, `MobileWriteSyncRepository.kt` (upload/complete/report), `OutboxDispatcher.kt` (planned + execution), `TrainingApiDto.kt`, `Training-Metrics-Audit.md`, والباك: `workout-executions.service.ts` (`saveWorkoutExecution`, `completePlannedWorkoutReport`, `updatePlannedWorkoutReport`), `mobile-planned-workouts.controller.ts`, `schema.prisma` (`WorkoutExecutionMetrics` / `RepMetrics`).

**خارج النطاق (مغطى في `Training-Metrics-Audit.md`):** منطق العدّ، ROM/velocity، discard incomplete، scoring، frame capture isolation، cross-set display merge.

---

## 1. الحكم التنفيذي (≤ 10 أسطر)

**يحتاج إصلاحات محددة (S1 جزئي على استعادة الجلسة + S2 على batch في الذاكرة).** مسار الإكمال الناجح متين نسبيًا: checkpoint عند كل عدة مكتملة، تقرير غني دائم في `PostTrainingReportLocalStore`، ورفع planned/execution عبر outbox. لكن استعادة crash **مكسورة فعليًا** رغم وجود `readJournal`→`restore` في hooks — لأن `start()` يمسح العدات المستعادة فورًا، و`listActiveCheckpoints`/`ABANDONED` بلا مستدعٍ. حزمة Explore تبقى في RAM حتى `flush` → kill يفقد الرفع رغم بقاء التقارير المحلية. `markCompleted` يكتب ثم يحذف فورًا (H18 مؤكدة كـ waste بلا ربط journal↔outbox).

**أهم 3 نتائج:** (1) استعادة journal تُستدعى ثم تُلغى بـ `recorder.start()` — crash mid-set يفقد العدات المكتملة أيضًا عند إعادة الفتح. (2) H15 مؤكدة — explore batch في الذاكرة فقط. (3) nullable stability/alignment في الموبايل تصطدم بـ `Int NOT NULL` في Prisma عند الرفع.

---

## 2. إجابات الأسئلة

### E1. crash/kill في منتصف الجلسة — ماذا يُفقد؟ وهل الاستعادة تُفعَّل؟

**الإجابة:** Checkpoint يُكتب عند **اكتمال كل عدة** فقط (+ عند `start`). العدة الجارية (buffer غير مكتمل) **تُفقد دائمًا** — وهذا متوقع. لكن الاستعادة بعد kill **لا تعمل فعليًا** رغم وجود مسار الكود: `attach` يستدعي `restore` ثم `start()` الذي يمسح `completedRepMetrics`. لا أحد يستدعي `listActiveCheckpoints`، و`ABANDONED` معرّف بلا كاتب.

**الدليل — checkpoint عند اكتمال العدة فقط:**

```62:71:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/TrainingMotionSession.kt
        engine.onRepCompletedForMotion = { result, phaseTimings, bilateralSide ->
            recorder.finalizeRep(
                repNumber = result.repNumber,
                phaseTimings = phaseTimings,
                worstState = result.worstState,
                score = result.score,
                side = bilateralSide?.wireName(),
            )
            checkpoint()
        }
```

`snapshot()` لا يتضمن `currentRepBuffer` — فقط `completedRepMetrics`:

```214:229:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/MotionRecorder.kt
    fun snapshot(sessionId: String, isAssessmentMode: Boolean = false): SessionJournalSnapshot =
        SessionJournalSnapshot(
            sessionId = sessionId,
            exerciseId = exerciseId,
            // ...
            completedRepMetrics = completedRepMetrics.toList(),
            // ...
        )
```

**الدليل — restore يُستدعى ثم يُلغى:**

```37:53:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionWriteHooks.kt
    fun attach(engine: MovitTrainingEngine, exerciseConfig: ExerciseConfig) {
        if (motionSession != null) return
        val session = TrainingMotionSession(/* ... */, onCheckpoint = writes::checkpointJournal)
        session.attach(engine)
        writes.readJournal(sessionId)?.let(session::restore)
        session.start(timeProvider())
        motionSession = session
    }
```

و`MotionRecorder.start()` يمسح العدات:

```41:45:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/MotionRecorder.kt
    fun start(startTimestampMs: Long = timeProvider()) {
        recordingStartMs = startTimestampMs
        isRecording = true
        currentRepBuffer.clear()
        completedRepMetrics.clear()
```

`attach` يُستدعى من ViewModel عند `StartEngine`:

```765:770:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
      is SupervisorAction.StartEngine -> {
        lastTrainingTimestampMs = 0L
        engine?.start()
        engine?.let { eng ->
          exerciseConfig?.let { config -> writeHooks.attach(eng, config) }
        }
```

**ملاحظة إضافية:** حتى لو أُصلح ترتيب `start`/`restore`، محرك العدّ (`MovitTrainingEngine` / `RepCounter`) يُبنى من الصفر عند إعادة فتح الجلسة — لا يوجد مسار يعيد `repCount` من journal إلى الـ UI/engine. الاستعادة الحالية (إن عملت) كانت ستعيد metrics للرفع فقط، لا عدّاد الشاشة.

**`listActiveCheckpoints` / `ABANDONED`:**

```47:52:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/journal/SessionJournalStore.kt
    fun listActiveCheckpoints(): List<SessionJournalSnapshot> =
        localStore.listActiveSessionJournals().mapNotNull { row ->
            // ...
        }
```

بحث كامل في `kmp-app`: **لا مستدعٍ** لـ `listActiveCheckpoints` خارج تعريف المتجر واختباراته. `SessionJournalStatus.ABANDONED` معرّف فقط — لا `markAbandoned` ولا كتابة status=`abandoned`. جلسات يتيمة تبقى `active` في SQL إلى الأبد (أو حتى `clearAllUserData`).

**سيناريو خطوة بخطوة:**
1. مستخدم يكمل 7 عدات → 7 checkpoints في SQL + json_cache.
2. أثناء العدة 8: OS يقتل التطبيق.
3. يعيد فتح نفس التمرين → `StartEngine` → `attach` → `restore(7 reps)` ثم `start()` يمسحها → يبدأ من صفر.
4. الـ journal القديم يبقى `active` بلا تنظيف/استئناف UI.

**الحكم:** فقدان العدة الجارية متوقع ومقبول. فقدان العدات المكتملة عند إعادة الفتح **خلل S1** في مسار الاستعادة. orphan cleanup غير موجود.

---

### E2. `markCompleted` يكتب completed ثم يحذف فورًا

**الإجابة:** نعم — كتابة مهدرة بلا أثر بعد الإكمال. الحذف الفوري **صحيح جزئيًا** كتنظيف checkpoint حي، لكنه يقطع أي ربط journal↔outbox: بعد `finalizeExercise` لا يبقى أثر جلسة حتى لو فشل الرفع لاحقًا في outbox.

**الدليل:**

```54:62:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/journal/SessionJournalStore.kt
    fun markCompleted(sessionId: String) {
        val snapshot = readCheckpoint(sessionId) ?: return
        saveCheckpoint(snapshot, SessionJournalStatus.COMPLETED)
        localStore.deleteSessionJournal(sessionId)
        localStore.removeJsonCache(
            MovitCacheKeys.SESSION_JOURNAL_STORE,
            MovitCacheKeys.sessionJournalKey(sessionId),
        )
    }
```

التسلسل في ViewModel: cache report → enqueue upload → **ثم** `finalizeExercise` (يحذف journal):

```886:890:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
      if (upload != null && summary != null) {
        accumulateDayReport(upload, summary, setsCompleted, totalSets)
        cachePostTrainingReport(upload, summary)
        enqueueUpload(upload, summary)
        writeHooks.finalizeExercise(upload)
      }
```

للمسار غير-Explore: `enqueueUpload` يدخل outbox قبل حذف journal — جيد. لكن بعد الحذف، الاعتماد الوحيد على نجاح الرفع هو صف الـ outbox + التقرير في `PostTrainingReportLocalStore` (لا journal). لا يوجد status `awaiting_upload`.

**الحكم:** H18 **مؤكدة** (waste + نية completed غير محققة). الحذف الفوري مقبول **إذا** بقي التقرير + outbox؛ لا حاجة للإبقاء على journal بعد enqueue ناجح — لكن كتابة `COMPLETED` قبل الحذف بلا فائدة. الأفضل: `delete` مباشرة، أو الإبقاء حتى `SUCCEEDED` إن أردنا استردادًا من journal عند فشل دائم للـ outbox.

---

### E3. تعدد الـ sets وbilateral — upload واحد؟ و`reportsBySet` + `rekey`؟

**الإجابة:** `WorkoutUpload` واحد **لكل set مكتمل** (لكل استدعاء `finalizeUpload` عند اكتمال تمرين/set في الـ flow)، وليس لكل جلسة يوم كاملة. bilateral يُخزَّن كـ `side` على مستوى العدة داخل نفس الـ upload. التقارير تُفهرس بـ `sessionExerciseKey` + `setNumber`؛ `rekeyPostTraining` يُستدعى بعد enqueue ناجح (مسار فوري ومسار batch).

**الدليل — upload per finalize (set/exercise completion):**

```876:890:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  private fun finalizeCurrentExercise() {
    // ...
    val upload = writeHooks.finalizeUpload(...)
    // ...
        cachePostTrainingReport(upload, summary)
        enqueueUpload(upload, summary)
```

`sessionExerciseKey = "$sessionId:$activeSlug"` و`setNumber = currentSetNumber` عند التخزين:

```1039:1044:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
    TrainingSessionReportCache.put(
      upload.id,
      report,
      sessionExerciseKey = "$sessionId:$activeSlug",
      setNumber = _state.value.currentSetNumber,
    )
```

**من يستدعي `rekeyPostTraining` ومتى:**

| المسار | الموضع | التوقيت |
|---|---|---|
| رفع فوري (planned / single) | `TrainingSessionViewModel.kt:1081` | بعد `enqueueExecutionUpload` Success — `reportId = result.value` |
| flush Explore batch | `TrainingSessionViewModel.kt:1108` | في `onEachEnqueued` بعد كل upload في `flushAwait` |
| التخزين الدائم | `TrainingSessionReportCache.kt:79` → `PostTrainingReportLocalStore.rekeyPostTraining` | نفس الاستدعاءات أعلاه |

**ملاحظة مهمة عن `reportId`:** `MobileWriteSyncRepository.uploadWorkoutExecution` يعيد `operationId ?: request.id` — أي **نفس client upload id**، وليس id سيرفر جديد:

```198:203:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/MobileWriteSyncRepository.kt
    suspend fun uploadWorkoutExecution(...): AppResult<String> {
        val id = operationId ?: request.id
        offlineWrites.enqueueWorkoutExecutionUpload(request, operationId = id)
        return AppResult.Success(id)
    }
```

والباك يعمل upsert بـ `payload.id` (`workout-executions.service.ts:62-63`). إذن `rekey(from, to)` غالبًا no-op (`fromId == toId` → return مبكر في `PostTrainingReportLocalStore.kt:95`). الفهرس `reportsBySet` يبقى على upload id — وهذا **متسق** مع idempotency بالـ client id. لا فقدان تقارير بسبب rekey في المسار الحالي؛ الخطر الحقيقي هو عدم استدعاء rekey أصلًا في مسار batch قبل flush (التقارير تبقى تحت upload id — صحيح).

**رفع متأخر:** التقرير يُكتب محليًا في `cachePostTrainingReport` **قبل** الرفع (حتى في batch عبر `put` قبل `record`). الرفع المتأخر عبر outbox لا يمسح الفهرس. عند نجاح لاحق، إن بقي نفس id فلا حاجة لـ rekey.

**الحكم:** نموذج per-set upload + فهرس دائم سليم. `rekey` دفاعي أكثر منه ضروري طالما الباك يحافظ على client id.

---

### E4. الرفع المؤجل للـ batch (`pending` في الذاكرة فقط)

**الإجابة:** **نعم — kill قبل `flush` يضيّع حزمة الرفع إلى outbox.** التقارير الغنية تبقى في التخزين الدائم، لكن لا صف outbox ولا `workoutGroupId` على الرفع. مسار planned يوم كامل أفضل: كل set يدخل outbox فورًا + `completePlannedDay` يكتب outbox عند نهاية اليوم.

**الدليل — RAM فقط:**

```18:27:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/WorkoutExecutionBatchCoordinator.kt
    private data class BatchEntry(
        val upload: WorkoutUpload,
        val legacyReport: JsonElement? = null,
    )

    private val pending = mutableListOf<BatchEntry>()

    fun record(upload: WorkoutUpload, legacyReport: JsonElement? = null) {
        pending += BatchEntry(upload, legacyReport)
    }
```

ViewModel يؤجل الرفع عند وجود `exploreBatch`:

```1065:1068:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
    if (batch != null) {
      batch.record(upload, legacyJson)
      return
    }
```

`flush` فقط عند `WorkoutComplete` في الـ flow:

```900:901:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
          TrainingSessionFlowCoordinator.State.WorkoutComplete -> {
            flushExploreBatchIfNeeded()
```

**مقارنة بـ planned:**
- كل `finalizeCurrentExercise` → `enqueueUpload` → outbox فورًا (لا batch).
- نهاية اليوم → `completePlannedDay` → `recordPendingPlannedWorkoutCompletion` + outbox `PLANNED_WORKOUT_COMPLETE` (متين محليًا).
- `accumulatedDayReport` أيضًا **في الذاكرة فقط** — لكن كل execution مرفوع مسبقًا؛ فقدان تقرير اليوم المجمّع لا يفقد metrics التمارين الفردية.

**سيناريو H15:**
1. Explore workout بـ 3 تمارين؛ يُكمل تمرينين → `record` في RAM + تقارير في SQL.
2. Kill قبل التمرين الثالث / قبل `WorkoutComplete`.
3. بعد إعادة الفتح: التقارير المحلية موجودة؛ **لا** outbox executions؛ لا `workoutGroupId` grouping على السيرفر أبدًا ما لم يُعد التدريب.

**الحكم:** H15 **مؤكدة**. فجوة S2 (وقد تصل S1 تجاريًا إن اعتبرنا «تدريب مكتمل محليًا لم يصل للباك»).

---

### E5. القسمة على 10 والـ scaling في `WorkoutUploadMapper`

**الإجابة:** القسمة `/10` صحيحة للحقول المخزّنة ×10 داخليًا (score/rom/form/stability/alignment/symmetry/formConsistency). `velocity` و`tempo` و`totalTUT` و`fatigueIndex` **بدون** `/10` — متوافق مع عقد الموبايل. `git diff` الحالي يجعل stability/alignment **nullable** في الـ DTO (لا تحويل null→0 على الموبايل). لكن **Prisma يفرض `Int` غير nullable** لـ `stability` / `alignmentAccuracy` / `avgStability` / `avgAlignmentAccuracy` — رفع null قد يفشل على السيرفر أو يُخزَّن بشكل غير متوقع حسب طبقة Prisma/validation.

**جدول الحقول (داخلي → DTO → باك):**

| حقل | داخلي | Mapper | DTO | Prisma |
|---|---|---|---|---|
| `score` / `formScore` / `avgFormScore` | Short ×10 | `/10f` | Float | Int (قيمة 0–100 بعد scale) |
| `rom` / `avgRom` | Short ×10 | `/10f` | Float | Int |
| `symmetry` / `avgSymmetry` | Short? ×10 | `?.let /10f` | Float? | Int? |
| `stability` / `avgStability` | Short? ×10 | `?.let /10f` (**diff**) | Float? | **Int NOT NULL** |
| `alignmentAccuracy` / `avg*` | Short? ×10 | `?.let /10f` (**diff**) | Float? | **Int NOT NULL** |
| `velocity` / `avgVelocity` | Short? | `?.toFloat()` بلا /10 | Float? | Int? |
| `tempo` / `avgTempo` | List\<Int\> ms | as-is | List\<Int\> | Json |
| `totalTUT` | Int ms | as-is | Int | Int |
| `formConsistency` | Short? ×10 | `/10f` | Float? | Int? |
| `fatigueIndex` | Short? (1-based rep) | `?.toFloat()` | Float? | Int? |
| `relativeStrength` / `intensityPercentage` | غير مُرسلة من الموبايل | — | null default | Float? |

**الدليل — mapper الحالي:**

```48:72:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/WorkoutUploadMapper.kt
    private fun RepMetrics.toDto(): RepMetricsDto = RepMetricsDto(
        rom = rom / 10f,
        symmetry = symmetry?.let { it / 10f },
        stability = stability?.let { it / 10f },
        // ...
        alignmentAccuracy = alignmentAccuracy?.let { it / 10f },
    )
```

**الدليل — Prisma NOT NULL:**

```1217:1221:backend/prisma/schema.prisma
  avgRom               Int
  avgSymmetry          Int?
  avgStability         Int
  avgVelocity          Int?
  avgFormScore         Int
  avgAlignmentAccuracy Int
```

```1247:1252:backend/prisma/schema.prisma
  rom                Int
  symmetry           Int?
  stability          Int
  velocity           Int?
  formScore          Int
  alignmentAccuracy  Int
```

أنواع الباك TypeScript ما زالت تعتبر `stability` / `alignmentAccuracy` مطلوبة (`workout-executions.types.ts:18-22`) — تعارض مع DTO الموبايل الجديد.

**null → 0؟** الموبايل **لا** يحوّل null إلى 0 في mapper (تحسّن مقصود من الـ audit). الخطر عكس ذلك: null يصل للسيرفر على عمود NOT NULL → فشل حفظ metrics أو رفض الطلب.

**الحكم:** Scaling صحيح للحقول ×10. فجوة عقد S2 بين nullable الموبايل وNOT NULL السيرفر — يجب إما جعل أعمدة Prisma nullable أو إرسال sentinel صريح متفق عليه (لا صامتًا 0 على الموبايل دون توثيق).

---

### E6. `PLANNED_WORKOUT_COMPLETE` مقابل `PLANNED_WORKOUT_REPORT`

**الإجابة:** السياسة محترمة في نقاط الاستدعاء الحالية. ViewModel يستدعي **فقط** `completePlannedDay`. `reportPlannedDay` موجود كـ API legacy بلا مستدعٍ في `kmp-app`. الباك **لا** يعامل الاثنين بنفس المنطق: `/complete` يعلّم completed + progression؛ `/report` يحدّث مقاييس فقط بلا تغيير status.

**الدليل — السياسة:**

```13:18:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionPlannedWritePolicy.kt
object TrainingSessionPlannedWritePolicy {
    const val CANONICAL_COMPLETE_ENDPOINT = "complete"
    fun shouldEnqueueLegacyReportAfterComplete(): Boolean = false
}
```

تعليق hooks يمنع الاقتران:

```203:206:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionWriteHooks.kt
    /**
     * Legacy `/report` partial update — do not pair with [completePlannedDay] on the same session.
     * See [TrainingSessionPlannedWritePolicy].
     */
```

الاستدعاء الوحيد من ViewModel:

```960:970:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  private suspend fun finalizePlannedWorkoutDay() {
    val planned = plannedWorkout ?: return
    val report = accumulatedDayReport ?: return
    writeHooks.completePlannedDay(...)
```

بحث `reportPlannedDay(`: التعريف فقط — **لا استدعاءات إنتاج**.

**الباك مختلف:**

```61:61:backend/src/modules/workout-executions/mobile-planned-workouts.controller.ts
      const report = await completePlannedWorkoutReport(...)  // status → completed + progress
```

```88:88:backend/src/modules/workout-execouts/mobile-planned-workouts.controller.ts
      const report = await updatePlannedWorkoutReport(...)  // metrics only, no status change
```

(`updatePlannedWorkoutReport` في `workout-executions.service.ts:1140-1172` لا يضع `status: 'completed'`.)

**ملاحظة:** `MobileWriteSyncRepository.reportPlannedWorkout` يحدّث `todayWorkout.isCompleted = true` محليًا بنفس طريقة complete — أي optimistic محلي لـ `/report` **أكثر عدوانية** من سلوك السيرفر. خطر نظري فقط طالما لا يُستدعى من العميل الجديد.

**الحكم:** سياسة العميل محترمة. الباك يؤكد أنهما ليسا aliasين متكافئين — التسمية «legacy alias» في الـ Brief مضلّلة قليلًا؛ `/report` = partial update، ليس complete.

---

## 3. نتائج الفرضيات المسندة (H..)

| فرضية | الحكم | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |
|---|---|---|---|---|
| **H15** | **مؤكدة** | `WorkoutExecutionBatchCoordinator.kt:23` `pending` في الذاكرة؛ `ViewModel:1065-1068` يؤجل؛ flush فقط عند `WorkoutComplete` | أكمل تمرينين في Explore → kill → لا outbox executions رغم تقارير محلية | رفع Explore مفقود حتى إعادة التدريب؛ لا grouping على السيرفر |
| **H18** | **مؤكدة** | `SessionJournalStore.markCompleted:54-62` يكتب COMPLETED ثم يحذف فورًا | أي إكمال تمرين ناجح يمر بـ `finalizeExercise` | عمل I/O مهدر؛ لا أثر `completed`؛ لا ربط journal↔outbox بعد الحذف |

---

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | الفرضية الجديدة | الدليل | سيناريو | الأثر |
|---|---|---|---|---|---|
| **E-N1** | **S1** | `attach` يستدعي `restore` ثم `start()` الذي يمسح `completedRepMetrics` → استعادة crash مكسورة | `TrainingSessionWriteHooks.kt:51-52` + `MotionRecorder.start:45` | crash بعد 7 عدات → إعادة فتح → عدّاد/metrics من صفر | فقدان عدات مكتملة كانت في journal |
| **E-N2** | **S2** | `listActiveCheckpoints` و`ABANDONED` ميتان — لا orphan GC ولا UI استئناف | لا مستدعين في الكود؛ enum فقط | جلسات يتيمة تبقى `active` | تضخم journal + لا مسار «استكمل جلستك» |
| **E-N3** | **S2** | حتى مع إصلاح restore، لا مزامنة `RepCounter`/UI من journal | `StartEngine` يبني engine جديدًا بلا hydrate من snapshot | استعادة metrics للرفع دون عدّاد شاشة | تجربة استئناف ناقصة |
| **E-N4** | **S2** | DTO nullable لـ stability/alignment يصطدم بـ Prisma `Int` NOT NULL | `WorkoutUploadMapper` diff + `schema.prisma:1218-1252` | رفع عدة بلا عينات stability | فشل حفظ metrics أو رفض API |
| **E-N5** | **S3** | `rekeyPostTraining` بعد enqueue يعيد نفس client id → no-op دائم تقريبًا | `MobileWriteSyncRepository:201-203` + upsert بالـ client id | أي رفع ناجح | كود دفاعي بلا ضرر؛ يضلّل القارئ أنه يوجد server id منفصل |
| **E-N6** | **S3** | `TrainingSessionLifecyclePolicy` يغطي pause/resume للـ phase فقط — لا persistence عبر process death | `TrainingSessionLifecyclePolicy.kt` كاملًا in-memory snapshot في VM | background قصير vs kill | متوقع؛ ليس بديلاً عن journal |
| **E-N7** | **S3** | `reportPlannedWorkout` يعلّم home `isCompleted=true` بينما السيرفر لا يكمل اليوم | `MobileWriteSyncRepository.kt:73-77` vs `updatePlannedWorkoutReport` | إن استُدعي legacy report من كود مستقبلي | تفاؤل محلي كاذب |

---

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **E-R1** | S1 | إصلاح ترتيب الاستعادة: إن وُجد journal → `restore` **بعد** `start` أو اجعل `start` لا يمسح عند وجود snapshot؛ hydrate `RepCounter`/UI من `completedRepMetrics.size`. ملفات: `TrainingSessionWriteHooks.kt`, `MotionRecorder.kt`, `TrainingSessionViewModel.kt` | M | يجب اختبار bilateral/multi-set وعدم مضاعفة العدّ |
| **E-R2** | S2 | اجعل Explore batch متينًا: اكتب pending batch إلى json_cache/outbox staging عند كل `record`، أو ارفع فورًا مع `workoutGroupId` (مثل planned) وألغِ RAM queue. ملفات: `WorkoutExecutionBatchCoordinator.kt`, `TrainingSessionViewModel.kt` | M | تغيير ترتيب grouping على السيرفر إن وُجدت افتراضات batch-at-end |
| **E-R3** | S2 | orphan journal: عند فتح التدريب أو bootstrap استدعِ `listActiveCheckpoints` → عرض استئناف أو `markAbandoned`/`delete` بعد TTL. ملفات: `SessionJournalStore.kt`, shell/training entry | S–M | قرارات منتج (استئناف vs تجاهل) |
| **E-R4** | S2 | وحّد عقد null: Prisma `stability`/`alignmentAccuracy`/`avg*` → `Int?` **أو** mapper يرسل قيمة sentinel موثّقة (ليس 0 الصامت). ملفات: `schema.prisma` + migration، `workout-executions.types.ts`, اختبار عقد | M | migration إنتاج + توافق admin dashboard |
| **E-R5** | S3 | بسّط `markCompleted` → `delete` مباشرة؛ أو أبقِ الصف حتى outbox SUCCEEDED إن رُغب ربط journal↔outbox. `SessionJournalStore.kt` | S | منخفضة |
| **E-R6** | S3 | احذف أو عطّل مسار `reportPlannedDay` من العميل الجديد إن لم يُستخدم؛ لا تُعلّم `isCompleted` في `reportPlannedWorkout`. `MobileWriteSyncRepository.kt` | S | منخفضة |

---

## 6. فجوات الاختبارات

**موجودة وراجعتُها:**
- `SessionJournalStoreTest` — round-trip + `markCompleted` يحذف (لا يغطي orphan/`ABANDONED`/استعادة عبر hooks).
- `PostTrainingReportLocalStoreTest` — put/get/rekey/index.
- `TrainingSessionReportCacheTest` — rekey + cross-set.
- `WorkoutUploadMapperTest` — scaling أساسي (عيّنة غير-null؛ **لا** يغطي null stability/alignment بعد الـ diff).
- `TrainingSessionPlannedWritePolicyTest` — `shouldEnqueueLegacyReportAfterComplete == false`.
- `MobileWriteSyncRepositoryTest` — guest execution enqueue؛ complete بدون auth يفشل.
- `MotionRecorderQualityTest` — `restore` على recorder مباشرة (لا يمر عبر `WriteHooks.attach`/`start`).

**ناقصة (مقترحة):**
- `TrainingSessionWriteHooksRestoreTest.kt` — يثبت أن `attach` مع journal موجود **لا** يمسح العدات (يفشل اليوم على E-N1).
- `WorkoutExecutionBatchCoordinatorDurabilityTest.kt` — بعد `record` ثم محاكاة process death (إعادة إنشاء المنسّق) يجب أن تبقى الحزمة أو تُرفض صراحةً مع بقاء مسار استرداد من التقارير المحلية.
- `WorkoutUploadMapperNullableMetricsTest.kt` — null stability/alignment تبقى null في JSON (لا تُحذف المفاتيح خطأً / لا تتحول 0).
- `SessionJournalOrphanCleanupTest.kt` — `listActiveCheckpoints` + سياسة abandoned.
- اختبار تكامل ViewModel: planned multi-set → N outbox execution + 1 complete؛ Explore multi-exercise → flush يملأ outbox بـ نفس `workoutGroupId`.

---

## ملحق: سياق git الحالي (لا يتعارض مع التوصيات)

- `WorkoutUploadMapper`: تحويل stability/alignment إلى nullable — متوافق مع `Training-Metrics-Audit.md`؛ يحتاج محاذاة باك (E-R4).
- تعديلات training-engine/report/journal في الـ working tree تمس جودة المتريكس وmulti-set identity — **لا** تصلح E-N1/H15؛ محور E يبني عليها دون تكرار نطاقها.
