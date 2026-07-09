# المحور D — موثوقية الكتابة والـ Outbox (Write-Path Reliability)
**المراجع:** independent review agent · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**
`OfflineWriteQueue.kt`, `OutboxDispatcher.kt`, `OutboxConflictPolicy.kt`, `OutboxReplayOrdering.kt`, `OutboxMaintenance.kt`, `OfflineWriteOptimisticCache.kt`, `OutboxModels.kt`, `OutboxPayloads.kt`, `LegacyWorkoutSyncGate.kt`, `OutboxConnectivityReplay.android.kt`, `OutboxConnectivityReplay.ios.kt`, `MobileWriteSyncRepository.kt` (+ `git diff` الحالي), `Outbox.sq`, `SqlDelightMovitLocalStore.kt` (outbox), `MovitMobileApi.kt` (write paths), `TrainingSessionWriteCoordinator.kt`, `OfflineWriteQueueTest.kt`, `MobileWriteSyncRepositoryTest.kt`, `MovitSyncOrchestrator.kt` (replay hooks), `MovitConnectivitySignals.kt`, `IosNetworkMonitor.kt` (replay trigger), والباك: `mobile-planned-workouts.controller.ts`, `workout-executions.service.ts` (save + planned start/complete/report), `mobile-workout-executions.controller.ts`, `active-plan.controller/service.ts` (`completeActiveProgram`), `mobile-user-programs.controller.ts` (overrides + customizations), `user-exercise-preferences.service.ts`, `progression.service.ts` (`markSeen`).

---

## 1. الحكم التنفيذي (≤ 10 أسطر)

**يحتاج إصلاحات محددة (S1 على التسليم المزدوج والفقد الصامت).** الطابور متين كتخزين محلي (SQLDelight + idempotency key محلي + ترتيب executions→complete)، ورفع الـ execution على الباك **idempotent فعليًا** عبر upsert بالـ client id. لكن `replayPending` لا يعلّم `IN_FLIGHT` قبل الإرسال ولا يوجد mutex مشترك بين محفزات الـ replay الخمسة → إرسال متوازٍ ممكن لعمليات **غير idempotent** على السيرفر (`PLANNED_WORKOUT_START/COMPLETE`, `USER_PROGRAM_OVERRIDE_CREATE`, `PLAN_COMPLETE`). بعد 3 محاولات شبكة/5xx تُدفن الصفوف في `FAILED_PERMANENT` بلا إشعار ولا استرجاع. استخراج HTTP status بـ regex هش لكنه يعمل حاليًا لأن `MovitMobileApi` يمرّر `(status)` بشكل متسق؛ أخطاء الشبكة بلا status تُعامل كـ retryable (صحيح) حتى السقف القاتل.

**أهم 3 نتائج:** (1) H02 مؤكدة — double-dispatch بدون قفل. (2) H03 مؤكدة — فقد صامت بعد 3 محاولات. (3) خريطة optimistic مجزأة + لا rollback عند permanent/server-wins.

---

## 2. إجابات الأسئلة

### D1. التسليم المزدوج (double dispatch)

**الإجابة:** إمكانية الإرسال المتوازي **مثبتة** لنفس صف `PENDING`. لا يوجد قفل على `replayPending`، ولا كتابة `IN_FLIGHT` قبل `dispatch`. الحالة `IN_FLIGHT` موجودة في المخطط والاستعادة فقط، لكنها **ميتة في مسار التشغيل العادي**.

**الدليل — لا IN_FLIGHT قبل الإرسال:**

```152:186:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OfflineWriteQueue.kt
        for (entry in OutboxReplayOrdering.sortForReplay(localStore.listPendingOutbox())) {
            val current = localStore.getOutboxById(entry.id) ?: continue
            if (current.status != OutboxStatus.PENDING) {
                skipped++
                continue
            }

            attempted++

            when (dispatcher.dispatch(current, auth)) {
                // ... status update فقط بعد اكتمال الـ HTTP
```

`recoverInFlightOutbox` يعيد `in_flight → pending`، لكن لا شيء يكتب `in_flight` أثناء الـ dispatch:

```27:30:kmp-app/core/data/src/commonMain/sqldelight/com/movit/core/data/db/Outbox.sq
recoverInFlight:
UPDATE outbox_entry
SET status = 'pending'
WHERE status = 'in_flight';
```

الاختبار الوحيد لـ `IN_FLIGHT` يُدخل الصف يدويًا (`OfflineWriteQueueTest.inFlightEntries_recoveredAndReplayedAfterCrash`) — أي أن المسار مصمَّم للاستعادة بعد crash افتراضي لم يُنفَّذ أصلًا.

**محفزات replay بدون قفل مشترك (5+):**

| # | المحفز | الموضع |
|---|---|---|
| 1 | بعد `enqueue` إن أونلاين+auth | `OfflineWriteQueue.kt:217-218` |
| 2 | بداية دورة sync | `MovitSyncOrchestrator.kt:127` |
| 3 | نهاية دورة sync | `MovitSyncOrchestrator.kt:231` |
| 4 | استعادة اتصال Android | `OutboxConnectivityReplay.android.kt:37,62-66` (+ `BackgroundSyncScheduler.requestNow` → sync → replay إضافي) |
| 5 | استعادة اتصال iOS | `IosNetworkMonitor` → `replayPendingOutboxIfInstalled` |

لا `Mutex` على `OfflineWriteQueue` (الـ Mutex الوحيد في `LegacyWorkoutSyncGate` لـ drain legacy فقط).

**سيناريو خطوة بخطوة:**
1. مستخدم يكمل يومًا أوفلاين → صف `PLANNED_WORKOUT_COMPLETE` بحالة `PENDING`.
2. يعود الاتصال → Android `onAvailable` يطلق `replayPendingOutboxIfInstalled()` **و** `BackgroundSyncScheduler.requestNow()` الذي يشغّل sync → `replayPending` مرة أخرى.
3. كلا الـ coroutines يقرآن `listPendingOutbox()` ويريان نفس الصف `PENDING`.
4. كلاهما يستدعيان `POST .../complete` قبل أن يعلّم أيّهما `SUCCEEDED`.

**Idempotency على الباك:**

| عملية | Idempotent؟ | الدليل | أثر التكرار |
|---|---|---|---|
| `WORKOUT_EXECUTION_UPLOAD` | **نعم** | `upsert where: { id: payload.id }` ثم delete/recreate metrics — `workout-executions.service.ts:62-95` | آمن للبيانات؛ `updateUserStats` يُستدعى مرتين لكنه aggregate من الجدول |
| `EXERCISE_PREFERENCE_UPSERT/DELETE` | **نعم** | upsert/deleteMany على `(userId, exerciseId)` — `user-exercise-preferences.service.ts:89-115` | آمن |
| `SAVE_DAY_CUSTOMIZATIONS` | **تقريبًا نعم** | merge customizations — `programs.service.ts:1643-1682` | نفس الحمولة → نفس النتيجة |
| `PROGRESSION_MARK_SEEN` | **نعم** | `updateMany where seen: false` — `progression.service.ts:723-731` | آمن |
| `PLANNED_WORKOUT_START` | **لا** | `plannedWorkoutReport.create` دائمًا — `workout-executions.service.ts:941-951` | صفوف `in_progress` مكررة |
| `PLANNED_WORKOUT_COMPLETE` | **لا** | إن وُجد تقرير `completed` فقط (لا `in_progress`)، يُنشئ تقريرًا جديدًا ثم يكمله — `workout-executions.service.ts:960-1013` | **تقارير يوم مكررة** + إعادة تشغيل progression engine |
| `PLANNED_WORKOUT_REPORT` | جزئي | يحدّث أحدث تقرير — `updatePlannedWorkoutReport:1145-1172`؛ لا يغيّر status | أقل ضررًا من complete إن وُجد صف؛ يفشل إن لم يوجد |
| `USER_PROGRAM_OVERRIDE_CREATE` | **لا** | `userProgramOverride.create` — `mobile-user-programs.controller.ts:185-197` | overrides مكررة |
| `PLAN_COMPLETE` | **لا** | `completeActiveProgram` يعلّم active→completed وينقل للبرنامج التالي — `active-plan.service.ts:654-745` | قفز برنامج / إكمال مزدوج |

**الحكم:** H02 **مؤكدة**. الخطر التجاري الأكبر: `PLANNED_WORKOUT_COMPLETE` و`PLAN_COMPLETE` و`OVERRIDE_CREATE`.

> **ملاحظة git جارٍ:** `MobileWriteSyncRepository.uploadWorkoutExecution` أزال شرط auth ليسمح لضيف بـ enqueue (replay بعد تسجيل الدخول). هذا **لا يتعارض** مع إصلاح القفل/`IN_FLIGHT`؛ بل يزيد أهمية أن يكون الـ replay آمنًا بعد sign-in (محفز إضافي محتمل مع sync).

---

### D2. الفقد الصامت بعد MAX_ATTEMPTS

**الإجابة:** مؤكد. بعد 3 محاولات retryable (شبكة أو 5xx أو status=null) → `FAILED_PERMANENT`. لا UI، لا telemetry ظاهرة للمستخدم، لا API لإعادة الإحياء سوى إعادة `enqueue` بنفس id (انظر D6).

**الدليل:**

```40:42:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OutboxConflictPolicy.kt
    fun shouldMarkPermanent(attempts: Int, httpStatus: Int?): Boolean =
        (httpStatus != null && isPermanentClientError(httpStatus)) ||
            (attempts >= MAX_ATTEMPTS && isRetryable(httpStatus))
```

```176:184:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OfflineWriteQueue.kt
                OutboxDispatchOutcome.RETRYABLE -> {
                    val nextAttempts = current.attempts + 1
                    val status = if (OutboxConflictPolicy.shouldMarkPermanent(nextAttempts, null)) {
                        OutboxStatus.FAILED_PERMANENT
                    } else {
                        OutboxStatus.PENDING
                    }
```

ملاحظة: فرع `RETRYABLE` يمرّر دائمًا `httpStatus=null` إلى `shouldMarkPermanent` — أي أن السقف `attempts>=3` يُطبَّق على **كل** الأخطاء retryable بما فيها 5xx المستخرَج statusها أصلًا في الـ dispatcher ثم يُهمَل هنا. السلوك متوافق مع النية الموثّقة، لكن الـ API مضلّل.

**أسوأ سيناريو (أسبوع أوفلاين ثم شبكة متقلبة):**
1. مستخدم يتدرب 4 أيام/أسبوع أوفلاين → عشرات `WORKOUT_EXECUTION_UPLOAD` + عدة `PLANNED_WORKOUT_COMPLETE` في outbox.
2. يعود لشبكة ضعيفة (gym Wi‑Fi / captive portal): كل replay يفشل timeout/5xx.
3. ثلاثة محفزات replay متتالية (connectivity + sync start + sync end، أو ثلاثة resumes) تستهلك المحاولات الثلاث **بسرعة** دون backoff.
4. كل الصفوف → `FAILED_PERMANENT`. الكاش المحلي ما زال يُظهر أيامًا مكتملة (optimistic في `MobileWriteSyncRepository` / `ReportsSyncRepository`)، لكن السيرفر لا يملك البيانات → تقارير/progression/admin فارغة إلى الأبد ما لم يُعد enqueue يدويًا.

**سياسة مقترحة:**
- أخطاء الشبكة/`status==null`/5xx: **لا سقف محاولات** (أو سقف عالٍ جدًا + exponential backoff مع jitter، و`nextAttemptAt`).
- 4xx (غير 409): يبقى permanent.
- سطح UI: شارة "بيانات معلقة / فشل المزامنة" من `countOutboxByStatus(FAILED_PERMANENT|PENDING)` + زر "إعادة المحاولة" يعيد `attempts=0, status=PENDING`.
- لا تُحذف `FAILED_PERMANENT` تلقائيًا (حاليًا الـ purge للـ SUCCEEDED فقط — جيد).

**الحكم:** H03 **مؤكدة** — S1 لهدف المنتج #1 (لا فقدان).

---

### D3. استخراج HTTP status بـ regex

**الإجابة:** العقد الحالي بين الطبقتين **متسق عمليًا** لكل مسارات الكتابة في `MovitMobileApi`، لكنه هش. أخطاء Ktor بلا رسالة `(ddd)` → `status=null` → `RETRYABLE` (صحيح حتى السقف).

**الدليل — الـ parser:**

```48:54:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OutboxModels.kt
internal fun parseHttpStatusFromError(message: String?): Int? {
    if (message == null) return null
    val match = HTTP_STATUS_IN_ERROR.find(message) ?: return null
    return match.groupValues[1].toIntOrNull()
}
private val HTTP_STATUS_IN_ERROR = Regex("\\((\\d{3})\\)")
```

**مسارات الكتابة في `MovitMobileApi` — كلها تمرّر `(status.value)`:**
- start/complete/report planned: `:523,539,555`
- upload execution: `:570`
- plan complete: `:685`
- preference upsert/delete: `:701,713`
- override create/delete: `:728,744`
- mark-seen: `:795`
- day customizations عبر `updateUserProgramCustomizations`: `:252`

**مسار الـ dispatcher:**

```64:75:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OutboxDispatcher.kt
        return result.fold(
            onSuccess = { OutboxDispatchOutcome.SUCCESS },
            onFailure = { error ->
                val status = parseHttpStatusFromError(error.message)
                when {
                    status != null && OutboxConflictPolicy.isServerWins(status) -> SERVER_WINS
                    status != null && OutboxConflictPolicy.isPermanentClientError(status) -> PERMANENT_FAILURE
                    else -> RETRYABLE
                }
            },
        )
```

**ماذا عن timeout/DNS؟** `runCatching` يلتقط `HttpRequestTimeoutException` / IO exceptions برسائل بلا `(ddd)` → `RETRYABLE`. صحيح. خطر: لو تغيّرت صيغة رسالة واحدة في المستقبل، أو ظهر رقم ثلاثي آخر بين أقواس في الرسالة، يُصنَّف خطأ. كذلك `error("Sync request failed.")` بلا status في مسار sync (ليس outbox) يوضح أن النمط غير مفروض على مستوى النوع.

**الحكم:** H17 **جزئية/مؤكدة كدين تقني S3** — السلوك الحالي سليم بفضل انضباط الرسائل، لا بفضل عقد نوعي. الإصلاح المفضّل: `sealed class ApiFailure(val status: Int?)` بدل regex.

---

### D4. الترتيب والتبعيات

**الإجابة:** `OutboxReplayOrdering` كافٍ لجلسة واحدة مختلطة (executions قبل complete). **غير كافٍ** كضمان تبعية صارمة عند فشل صف، ولا يربط صفوف جلستين/يومين.

**الدليل:**

```15:27:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OutboxReplayOrdering.kt
    private fun priority(type: OutboxOperationType): Int = when (type) {
        OutboxOperationType.WORKOUT_EXECUTION_UPLOAD -> 0
        ...
        OutboxOperationType.PLANNED_WORKOUT_COMPLETE -> 80
        OutboxOperationType.PLANNED_WORKOUT_REPORT -> 85
        OutboxOperationType.PLAN_COMPLETE -> 90
    }
```

الترتيب داخل النوع بـ `createdAt` ثم `id` — جلستان ليومين مختلفين: كل executions (اليومين) تُرسل قبل أي complete، وهو مقبول عمليًا لأن الباك لا يشترط ترتيبًا عبر الأيام.

**فشل execution بـ 4xx:** يُعلَّم `FAILED_PERMANENT` ويستمر الـ loop لإرسال `PLANNED_WORKOUT_COMPLETE` لنفس الجلسة — **لا توجد تبعية**. الأثر: يوم مكتمل على السيرفر بدون (أو بنقص) execution metrics؛ progression قد يعمل على بيانات ناقصة. العكس (complete قبل execution) مُعالَج بالأولوية طالما replay واحد متسلسل — لكن مع double-dispatch (D1) يمكن أن يسبق completeُ متوازٍ executionًا.

**الحكم:** الترتيب حسن كـ heuristic؛ ينقص `blocksOn` / إيقاف سلسلة الجلسة عند permanent failure للـ executions التابعة، أو على الأقل عدم إرسال complete إن بقيت executions لنفس `workoutGroupId`/`plannedWorkoutId` في `FAILED_PERMANENT|PENDING`.

---

### D5. التحديث المتفائل وخريطة الأنواع

**الإجابة:** الخريطة مجزأة بين `OfflineWriteOptimisticCache` (3 أنواع) و`MobileWriteSyncRepository` / `ReportsSyncRepository` / `TrainingSessionWriteCoordinator`. **لا rollback** عند `SERVER_WINS` أو `FAILED_PERMANENT`.

| نوع العملية | أين الـ optimistic؟ | عند SUCCESS | عند SERVER_WINS / PERMANENT |
|---|---|---|---|
| `SAVE_DAY_CUSTOMIZATIONS` | `OfflineWriteOptimisticCache` + تكرار جزئي في `MobileWriteSyncRepository.applyCustomizationsToEffectivePlanCache` | يبقى | لا rollback؛ يوم `isUserModified` قد يتجمد (محور J) |
| `EXERCISE_PREFERENCE_UPSERT/DELETE` | Cache + تكرار في `MobileWriteSyncRepository.cacheExercisePreference` / `remove` | يبقى | لا rollback |
| `PLANNED_WORKOUT_COMPLETE/REPORT` | `patchHomeTrainMode(isCompleted=true)` في repo + `recordPendingPlannedWorkoutCompletion` + `patchDashboardFromCompletion` في coordinator/reports | يبقى | UI محلي يقول مكتمل؛ السيرفر قد يختلف |
| `PLAN_COMPLETE` | `patchHomeTrainMode(status=completed)` | يبقى | لا rollback |
| `WORKOUT_EXECUTION_UPLOAD` | `patchExerciseMetricsFromUpload` في coordinator (ليس في OptimisticCache) | يبقى | متريكس محلية أغنى من السيرفر |
| `PLANNED_WORKOUT_START` | لا شيء | — | — |
| `USER_PROGRAM_OVERRIDE_*` | لا شيء | — | — |
| `PROGRESSION_MARK_SEEN` | لا شيء | — | — |

`OutboxConflictPolicy` يوثّق أن على المستدعين "invalidate caches on next sync" عند server-wins، لكن `replayPending` يعلّم `SUCCEEDED` فقط **بدون** استدعاء invalidation:

```161:166:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OfflineWriteQueue.kt
            when (dispatcher.dispatch(current, auth)) {
                OutboxDispatchOutcome.SUCCESS,
                OutboxDispatchOutcome.SERVER_WINS,
                -> {
                    localStore.updateOutboxStatus(entry.id, OutboxStatus.SUCCEEDED, current.attempts)
```

**ازدواج يومي مقصود جزئيًا:** `enqueue` يستدعي `OfflineWriteOptimisticCache.apply`، و`MobileWriteSyncRepository.saveDayCustomizations` يحدّث effective-plan cache **قبل** الـ enqueue — كتابة مزدوجة لنفس الفكرة.

**الحكم:** وحّد جدولًا واحدًا (نوع → applyOptimistic / onServerWins / onPermanentFailure). الحد الأدنى: عند permanent failure لـ complete/plan، لا تترك home/dashboard في حالة "مكتمل" بلا إشارة فشل.

---

### D6. إعادة استخدام id وSUCCEEDED early-return

**الإجابة:** إعادة كتابة `FAILED_PERMANENT` بنفس id **مقصودة ومفيدة** (مسار إحياء يدوي). تخطّي `SUCCEEDED`/`PENDING`/`IN_FLIGHT` بنفس id **صحيح لمنع إعادة الإرسال**، لكنه يعتمد على أن المستدعي يعيد استخدام نفس `operationId`.

**الدليل:**

```200:214:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OfflineWriteQueue.kt
        val existing = localStore.getOutboxById(operationId)
        if (existing != null && existing.status != OutboxStatus.FAILED_PERMANENT) {
            return operationId
        }

        localStore.insertOutbox(
            OutboxEntry(
                id = operationId,
                ...
                status = OutboxStatus.PENDING,
            ),
        )
```

`insert` = `INSERT OR REPLACE` (`Outbox.sq:14-19`) → يستبدل صف `FAILED_PERMANENT` ويعيد المحاولة من `attempts=0`.

**لكل الأنواع:**
- `WORKOUT_EXECUTION_UPLOAD`: `operationId = request.id` ثابت → SUCCEEDED early-return **صحيح وضروري** (يمنع إعادة رفع بعد نجاح).
- بقية الأنواع: الافتراضي `newOperationId()` عشوائي → كل استدعاء مستخدم جديد = صف جديد. إعادة إكمال نفس اليوم بعد أسبوع من الـ UI ستُنشئ `op-<epoch>-*` جديدًا ولن تصطدم بـ SUCCEEDED القديم — وهذا قد يكون مرغوبًا (جلسة جديدة) أو خطرًا (إن أعاد التطبيق إرسال complete لنفس اليوم بسبب bug في الـ caller).
- إن مرّر الـ caller نفس `operationId` بعد نجاح سابق: لن يُعاد الإرسال — **صحيح** لـ idempotency المحلي؛ لإكمال يوم حقيقي لاحق يجب id جديد (السلوك الحالي بالافتراضي).

**الحكم:** المنطق سليم للمفاتيح المستقرة (execution id). لنوع complete/start يُفضَّل توثيق عقد: `operationId = "complete:$plannedWorkoutId:$completedAt"` أو مشابه إن أردنا منع التكرار المنطقي، مع الإبقاء على early-return لـ SUCCEEDED.

---

## 3. نتائج الفرضيات المسندة (H02, H03, H17)

| فرضية | الحكم | الدليل | سيناريو الإعادة | الأثر الفعلي |
|---|---|---|---|---|
| **H02** | **مؤكدة** | `OfflineWriteQueue.replayPending:152-161` بلا IN_FLIGHT/mutex؛ محفزات متوازية في ConnectivityReplay + SyncOrchestrator؛ `startPlannedWorkoutReport` create؛ `completePlannedWorkoutReport` ينشئ تقريرًا جديدًا إن لم يوجد `in_progress`؛ `createOverride` create؛ `completeActiveProgram` غير idempotent | أكمل يومًا أوفلاين → فعّل Wi‑Fi → راقب طلبين متوازيين لـ `/complete` (أو أطلق `replayPending` من coroutineين في اختبار) | تقارير planned مكررة، overrides مكررة، قفز برنامج؛ executions آمنة بفضل upsert |
| **H03** | **مؤكدة** | `OutboxConflictPolicy.MAX_ATTEMPTS=3` + `shouldMarkPermanent`; فرع RETRYABLE في `OfflineWriteQueue:176-184`؛ لا UI لـ `FAILED_PERMANENT`؛ purge لا يمسّها لكن لا يحييها | أسبوع أوفلاين ثم 3 فشل شبكة متتالية عبر connectivity/sync | فقدان صامت لرفع التدريب رغم كاش محلي "ناجح" |
| **H17** | **جزئية (S3 مؤكد كدين؛ السلوك الحالي سليم)** | `parseHttpStatusFromError` regex؛ كل write paths في `MovitMobileApi` تستخدم `(${response.status.value})`؛ timeout → null → RETRYABLE | غيّر رسالة خطأ واحدة لإزالة الأقواس → 4xx يُعادى كـ retryable حتى السقف ثم permanent بالسقف لا بالتصنيف | اليوم: 409/4xx/5xx تُصنَّف صح؛ غدًا: انكسار صامت عند تغيير صياغة |

---

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | الفرضية الجديدة | الدليل | الأثر |
|---|---|---|---|---|
| **D-N1** | S1 | `IN_FLIGHT` حالة ميتة: الاستعادة موجودة والكتابة أثناء dispatch غير موجودة → حماية الـ crash الموثّقة وهمية | `Outbox.sq:recoverInFlight` + غياب `updateOutboxStatus(..., IN_FLIGHT)` في `replayPending`؛ الاختبار يحقن الحالة يدويًا | أي إصلاح لـ H02 يجب أن يفعّل الكتابة الفعلية لـ IN_FLIGHT (أو CAS) وإلا تبقى الاستعادة زينة |
| **D-N2** | S2 | لا backoff بين المحاولات: ثلاثة محفزات متزامنة تستهلك `MAX_ATTEMPTS` في ثوانٍ على شبكة سيئة | ConnectivityReplay يطلق replay + BackgroundSync معًا؛ sync يستدعي replay مرتين | يسرّع H03 |
| **D-N3** | S2 | `complete` بعد فشل execution 4xx ما زال يُرسل (لا تبعية صفوف) | loop في `replayPending` لا يتوقف؛ لا ربط `workoutId` | يوم مكتمل بدون متريكس تمارين |
| **D-N4** | S2 | optimistic مزدوج لـ day customizations (repo + OptimisticCache) وغياب rollback موحّد | `MobileWriteSyncRepository:176` + `OfflineWriteOptimisticCache:13` | تضارب صيانة؛ صعوبة إصلاح J1 لاحقًا |
| **D-N5** | S3 | فرع RETRYABLE يتجاهل status المستخرج ويمرّر `null` إلى `shouldMarkPermanent` | `OfflineWriteQueue:178` | لبس في السياسة؛ لا يغيّر السلوك الحالي للسقف لكنه يخفي نية "5xx vs network" |
| **D-N6** | S3 (سياق git) | Guest execution enqueue بدون auth — اتجاه صحيح offline-first | `git diff` على `MobileWriteSyncRepository.uploadWorkoutExecution` + اختبار `uploadWorkoutExecution_withoutAuth_enqueuesForReplayAfterSignIn` | بعد login، replay+sync يجب أن يكونا متسلسلين (يرتبط بـ H02) |

---

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **D-R1** | S1 | **قفل replay + تعليم IN_FLIGHT قبل الإرسال:** `Mutex` على `OfflineWriteQueue.replayPending`؛ قبل `dispatch`: CAS/`updateOutboxStatus(IN_FLIGHT)`؛ بعد النتيجة: SUCCEEDED/PENDING/FAILED. اجعل ConnectivityReplay لا يطلق replay منفصلًا إن كان sync سيفعل ذلك (أو اترك القفل يكفي). ملفات: `OfflineWriteQueue.kt`, اختياريًا `OutboxConnectivityReplay.*`. **لا يتعارض مع guest-upload diff.** | M | منخفضة إن بقي القفل داخل العملية الواحدة؛ اختبر التوازي |
| **D-R2** | S1 | **Idempotency على الباك للعمليات الحرجة:** `start`/`complete` planned: upsert على `(userId, plannedWorkoutId)` أو ارفض duplicate complete بهدوء 200؛ `createOverride`: idempotency key أو unique طبيعي؛ `completeActiveProgram`: no-op إن لم يعد هناك active slot. ملفات: `workout-executions.service.ts`, `mobile-user-programs.controller.ts`, `active-plan.service.ts`. | M–L | يحتاج توافق عملاء قدماء؛ ابدأ بـ complete (أعلى أثر) |
| **D-R3** | S1 | **سياسة retry بلا سقف للشبكة + إحياء:** أزل `attempts>=3` لـ `status==null`/5xx؛ أضف `nextAttemptAt` أو backoff؛ اكشف `pendingCount`/`failedPermanentCount` للـ UI مع "إعادة المحاولة" = reset attempts. ملفات: `OutboxConflictPolicy.kt`, `OfflineWriteQueue.kt`, `Outbox.sq` (عمود اختياري), shell/settings UI. | M | نمو outbox إن بقيت أخطاء دائمة بلا تمييز — ابقِ 4xx permanent |
| **D-R4** | S2 | مرّر `HttpStatus` كحقل typed من `MovitMobileApi` (أو exception مخصّص) بدل regex — `MovitMobileApi.kt` + `OutboxDispatcher.kt` | S | منخفضة |
| **D-R5** | S2 | جدول optimistic موحّد + rollback/mark-dirty عند permanent/server-wins لـ complete/plan/prefs — `OfflineWriteOptimisticCache.kt`, `MobileWriteSyncRepository.kt` | M | تنسيق مع محور J (تقارير/تخصيصات) |
| **D-R6** | S3 | تبعية اختيارية: لا تُرسل `PLANNED_WORKOUT_COMPLETE` إن وُجدت executions لنفس الجلسة في PENDING/FAILED_PERMANENT — `OutboxReplayOrdering.kt` / `OfflineWriteQueue.kt` | S–M | قد يؤخر إكمال يوم إن علق execution واحد؛ يحتاج سياسة منتج |

**أهم 3 للتنفيذ الفوري:** D-R1، D-R3، D-R2 (complete أولًا).

---

## 6. فجوات الاختبارات

**موجودة وراجعتُها:**
- `OfflineWriteQueueTest`: optimistic day cache، replay مرة واحدة، dedupe operationId، offline gate، استعادة IN_FLIGHT المحقونة، execution/progression offline replay، 409→SUCCEEDED، ترتيب execution→complete، legacy drain gate، purge retention.
- `MobileWriteSyncRepositoryTest`: complete offline enqueue، prefs cache، execution id، **guest upload بدون auth** (يتوافق مع diff الجاري)، complete بدون auth يفشل.

**ناقصة (مقترحة):**
| ملف مقترح | ماذا يغطي |
|---|---|
| `OfflineWriteQueueConcurrencyTest.kt` | coroutineان يستدعيان `replayPending` على صف واحد → يجب طلب API واحد بعد D-R1 |
| `OutboxConflictPolicyTest.kt` | وحدات لـ `shouldMarkPermanent` / تمييز شبكة بلا سقف بعد D-R3 |
| `OutboxHttpStatusParsingTest.kt` أو نقل status typed | رسائل `(409)`/`(400)`/timeout بلا أقواس |
| `OfflineWriteQueueRetryExhaustionTest.kt` | 3× RETRYABLE → FAILED_PERMANENT + أن الشبكة لا تُدفن بعد إصلاح السياسة |
| Backend: `planned-workout-complete.idempotency.spec.ts` | استدعاءان متوازيان لـ `/complete` → تقرير واحد |
| `OutboxOptimisticRollbackTest.kt` | بعد permanent failure لـ complete، home trainMode لا يبقى مكتملًا بلا إشارة (بعد D-R5) |

---

## ملحق: خريطة سريعة as-built

```
enqueue(opId) → LegacyGate → skip if exists≠FAILED_PERMANENT
            → INSERT OR REPLACE PENDING → OptimisticCache(3 types)
            → if online+auth: replayPending()

replayPending → require auth+network → recoverInFlight (no-op اليوم)
             → sort by OutboxReplayOrdering
             → for each PENDING: dispatch HTTP → update status
             → purge SUCCEEDED > 7d

Backend safety net today: execution upsert only.
Weakest links: planned complete/start, override create, plan complete.
```
