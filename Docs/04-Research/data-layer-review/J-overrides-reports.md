# المحور J — تخصيصات المستخدم والتقارير (User Overrides & Reports Consistency)
**المراجع:** independent-review-agent (axis J) · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**
`DayCustomizationLocalStore.kt`, `DayCustomizationKeyResolver.kt`, `UserProgramEnrollmentLocalStore.kt`, `ExercisePreferenceLocalStore.kt`, `ExerciseIdResolver.kt`, `ReportsSyncRepository.kt` (+ git diff الجاري), `ExerciseMetricsPatch.kt`, `PlanSyncRepository.kt`, `HomeTrainModeHydrator.kt`, `MobileWriteSyncRepository.kt` (مسارات prefs/customizations), `OfflineWriteOptimisticCache.kt`, `OutboxDispatcher.kt`, `OutboxPayloads.kt`, `MovitSyncOrchestrator.kt` (hydration block), `TrainingSessionWriteCoordinator.kt`, `PlanSyncDto.kt` (export DTOs), اختبارات: `DayCustomizationLocalStoreTest`, `ExercisePreferenceLocalStoreTest`, `ReportsSyncRepositoryTest`, `UserProgramEnrollmentLocalStoreTest`, `HomeTrainModeHydratorTest`, `ReportsPatchWeightedAverageTest` · باك: `mobile-sync.service.ts` (userPrograms/plannedWorkoutReports), `programs.service.ts` (`updateUserProgram`), `mobile-user-programs.controller.ts`, `user-exercise-preferences.service.ts` + controller, `workout-executions.service.ts` (complete + streak).

**سياق git (ReportsSyncRepository):** التعديل الجاري يصلح فقط `patchExerciseMetricsFromUpload` ليحسب متوسط form موزون بالـ sets (`computeWeightedAverageFormScore`) بدل استبدال المتوسط بآخر set — لا يمس `hydrateFromSync` / `recordPending` / `patchDashboardFromCompletion`.

---

## 1. الحكم التنفيذي (≤ 10 أسطر)

**يحتاج إصلاحات محددة** — المسارات offline-first للتخصيصات والتفضيلات والتقارير تعمل محليًا، لكن ثلاث قواعد conflict/merge مكسورة بشكل دائم:

1. **H04 مؤكدة (S1):** `isUserModified=true` يمنع أي hydrate من السيرفر لذلك اليوم إلى الأبد؛ لا يوجد تصفير بعد SUCCEEDED للـ outbox.
2. **H05 مؤكدة (S2):** `hydrateFromSync` backfill-only + تقرير متفائل بمفتاح `plannedWorkoutId` و`id=workoutId` و`completedAt` كـ epoch-string → النسخة الأفقر تحجب تقرير السيرفر نهائيًا.
3. **H16 مؤكدة (S2):** `patchDashboardFromCompletion` يزيد `daysTrained`/`currentStreak` بلا فحص لليوم التقويمي، و`syncDashboard` محجوب لغير Pro فلا تصحيح سيرفر.

التفضيلات (J4) سليمة نسبيًا مع حماية pending؛ `parseIsoToEpochMs` (H20) قنبلة صامتة موثّقة.

---

## 2. إجابات الأسئلة (سؤالًا سؤالًا)

### J1 — تجميد التعارض الدائم (`isUserModified`)

**الإجابة:** الفرضية صحيحة بالكامل. بعد `saveUserCustomizations` يُضبط `isUserModified = true`. في `hydrateFromBackend` أي يوم موجود محليًا بهذا العلم يُتخطى بـ `continue` قبل مقارنة `customizationsUpdatedAt`. لا يوجد أي مسار في الكود يصفّر العلم بعد نجاح رفع `SAVE_DAY_CUSTOMIZATIONS` (بحث `isUserModified` يظهر فقط في الـ store + الـ DTO + اختبار التخطي).

**الدليل:**

```72:88:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/DayCustomizationLocalStore.kt
    fun saveUserCustomizations(...) {
        ...
                isUserModified = true,
```

```107:115:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/DayCustomizationLocalStore.kt
            if (hasCustomization(userProgramId, weekNumber, dayNumber)) {
                val existing = get(userProgramId, weekNumber, dayNumber) ?: continue
                if (existing.isUserModified) {
                    continue
                }
                if (serverMs != null && serverMs <= existing.lastModifiedAt) {
                    continue
                }
            }
```

الباك يدمج التخصيصات ويحدّث `customizationsUpdatedAt` عند كل PUT:

```1643:1683:backend/src/modules/programs/programs.service.ts
    // Merge customizations instead of replacing them
    ...
        ...(mergedCustomizations !== undefined
          ? { customizationsUpdatedAt: new Date() }
          : {}),
```

والـ sync يمرّر الطابع إلى الموبايل:

```184:191:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
                    dayCustomizationLocalStore.hydrateFromBackend(
                        userProgramId = userProgramId,
                        customizations = userProgram.customizations,
                        serverCustomizationsUpdatedAt = userProgram.customizationsUpdatedAt,
                    )
```

لكن المقارنة الزمنية لا تُنفَّذ أصلًا إذا بقي العلم true. الاختبار يثبّت السلوك كعقد متعمّد حاليًا: `hydrateFromBackend_skipsUserModifiedDay` حتى مع `serverCustomizationsUpdatedAt = "2099-..."`.

**سيناريو الإعادة:**
1. جهاز A يعدّل يوم 1_1 → outbox + `isUserModified=true`.
2. الرفع ينجح (SUCCEEDED) — العلم يبقى true.
3. جهاز B أو Admin يعدّل نفس اليوم على السيرفر → `customizationsUpdatedAt` أحدث.
4. جهاز A يعمل sync → `hydrateFromBackend` يتخطى اليوم → A يرى نسخته القديمة إلى الأبد.

**قاعدة conflict-resolution المقترحة:**
- عند `OutboxDispatchOutcome.SUCCESS` لـ `SAVE_DAY_CUSTOMIZATIONS`: أعد كتابة الصف المحلي بـ `isUserModified=false` و`lastModifiedAt = now` (أو طابع السيرفر إن وُجد في الاستجابة).
- عند hydrate: إن `isUserModified` **و** يوجد outbox PENDING لنفس `(userProgramId, week, day)` → احتفظ بالمحلي (pending wins). وإلا قارن `serverCustomizationsUpdatedAt` vs `lastModifiedAt`؛ الأحدث يفوز. إن تساويا → server wins (أو merge بالمفتاح إن لزم).
- لا تعتمد على العلم وحده كقفل دائم.

**الحكم:** كسر وظيفي متعدد الأجهزة (S1) — يحتاج إصلاحًا محددًا وليس إعادة معمارية.

---

### J2 — التقارير المحلية تحجب السيرفر

**الإجابة:** مؤكد. `hydrateFromSync` يتخطى أي مفتاح موجود محليًا بلا مقارنة جودة/طابع. `recordPendingPlannedWorkoutCompletion` يكتب تقريرًا متفائلًا بمفتاح `plannedWorkoutId` و`id = workoutId` (ليس id تقرير السيرفر) و`completedAt = formatEpochMillis(...)` (رقم epoch كنص) بينما السيرفر يرسل ISO Z.

**الدليل:**

```83:95:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ReportsSyncRepository.kt
    open fun hydrateFromSync(exports: List<PlannedWorkoutReportExportDto>) {
        ...
            if (readCachedPlannedWorkoutReport(workoutId) != null) continue
            writePlannedWorkoutReport(store, workoutId, export.copy(plannedWorkoutId = workoutId))
```

```97:125:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ReportsSyncRepository.kt
        val export = PlannedWorkoutReportExportDto(
            id = workoutId,
            plannedWorkoutId = workoutId,
            ...
            completedAt = formatEpochMillis(request.completedAt),
            ...
            avgAccuracy = (request.avgAccuracy ?: 0f).toDouble(),
```

```402:403:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ReportsSyncRepository.kt
    private fun formatEpochMillis(epochMs: Long?): String =
        epochMs?.toString().orEmpty()
```

السيرفر يصدّر ISO و`id` الحقيقي للتقرير:

```579:586:backend/src/modules/mobile-sync/mobile-sync.service.ts
      plannedWorkoutReports = reportRows.map((r) => ({
        id: r.id,
        plannedWorkoutId: r.plannedWorkoutId,
        ...
        completedAt: r.completedAt?.toISOString() ?? r.startedAt.toISOString(),
```

المسار يستدعى من `TrainingSessionWriteCoordinator.completePlannedWorkout` / `reportPlannedWorkout` قبل الـ outbox. التعليق في الكود يصرّح أن "pending offline completions win" — صحيح أثناء PENDING، خاطئ بعد SUCCEEDED وإلى الأبد.

**قاعدة استبدال آمنة:**
- أثناء وجود outbox PENDING/IN_FLIGHT لـ `PLANNED_WORKOUT_COMPLETE|REPORT` لنفس `plannedWorkoutId` → لا تستبدل المحلي.
- بعد SUCCEEDED أو غياب pending: **server wins** — اكتب export السيرفر فوق المحلي (يصحّح `id`, `completedAt` ISO, المقاييس المحسوبة على السيرفر).
- بديل أخف: استبدل إذا `export.id != local.id` أو إذا `local.completedAt` يطابق regex أرقام فقط (epoch-string) بينما السيرفر ISO.

**الحكم:** S2 مؤكد — شاشة التقارير/`hasLocalTrainingActivity` قد تعتمد على نسخة أفقر إلى الأبد؛ Pro dashboard منفصل لا يصلح هذا الـ blob.

---

### J3 — `patchDashboardFromCompletion` وتراكم غير Pro

**الإجابة:** مؤكد. كل إكمال يزيد `daysTrained += 1` و`currentStreak += 1` (إذا `totalReps > 0`) بلا فحص لتاريخ اليوم التقويمي. الباك يحسب الـ streak من مجموعة أيام فريدة (`Set` من تواريخ `completedAt`). `syncDashboard` يعيد الكاش فورًا لغير Pro دون fetch — فلا تصحيح.

**الدليل:**

```330:347:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ReportsSyncRepository.kt
        val updatedSummary = summary.copy(
            ...
            daysTrained = (summary.daysTrained ?: 0) + 1,
            ...
            currentStreak = ((summary.currentStreak ?: 0) + 1).takeIf { repsDelta > 0 }
                ?: summary.currentStreak,
        )
```

```190:193:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ReportsSyncRepository.kt
        if (!bindings.isProUser()) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Reports require a Pro subscription.")
        }
```

مقابل السيرفر (أيام فريدة):

```1210:1242:backend/src/modules/workout-executions/workout-executions.service.ts
  // Training streak: consecutive days with completed workout executions
    const trainedDays = new Set<string>();
    for (const r of allCompletedDates) {
      if (r.completedAt) {
        trainedDays.add(r.completedAt.toISOString().split('T')[0]);
      }
    }
```

**سيناريو:** مستخدم غير Pro يكمل يومين مخططين في نفس اليوم التقويمي → `daysTrained` و`currentStreak` +2 محليًا؛ sync لا يصحح أبدًا.

**التصحيح المقترح:**
- قبل الزيادة: استخرج يوم الإكمال من `request.completedAt`؛ إن وُجد تقرير محلي مكتمل بنفس اليوم التقويمي فلا تزد `daysTrained`/`currentStreak`.
- أو أعد حساب الملخص من `readAllCachedPlannedWorkoutReports()` (مجموعة تواريخ) بدل patch تراكمي.
- لغير Pro: إمّا endpoint ملخص خفيف بدون Pro، أو قبول أن الـ dashboard المحلي تقديري مع إعادة حساب من التقارير المحلية فقط.

**الحكم:** S2 مؤكد لغير Pro؛ لـ Pro يُصحَّح جزئيًا عند أول `syncDashboard` ناجح.

---

### J4 — مفاتيح `pref_<exerciseId>` مقابل aliases

**الإجابة:** المسار المحلي سليم في الغالب. `upsert`/`get`/`remove` يمرّون بـ `ExerciseIdResolver.resolveCanonicalExerciseId` ويحذفون مفتاح الـ slug القديم. `hydrateFromSync` يفضّل `row.exerciseId` ويحمي الصفوف ذات outbox PENDING عبر `pendingExerciseIdsFromOutbox` (يقارن id أو canonical). الباك يخزّن بـ `userId_exerciseId` ويقبل id فقط في `PUT :exerciseId` (`findFirst({ where: { id: exerciseId } })`).

**ثغرة متبقية (جزئية، ليست تكرارًا محليًا دائمًا):**
1. `MobileWriteSyncRepository.upsertExercisePreference` يستدعي `cacheExercisePreference` (canonical) ثم يمرّر **نفس** `exerciseId` الخام إلى `enqueueExercisePreferenceUpsert` — إن استُدعي بـ slug، الكاش canonical لكن payload الـ outbox يحمل الـ slug → الباك يرد 404 NOT_FOUND → FAILED_PERMANENT، والكاش المحلي يبقى بقيمة لم تُرفع.
2. `OfflineWriteOptimisticCache.applyExercisePreferenceUpsert` يستدعي `upsert(parsed.exerciseId, ...)` — آمن إن وُجدت خريطة aliases؛ إن غابت الخريطة يُكتب مفتاح slug.
3. `pendingExerciseIdsFromOutbox` يقرأ فقط `OutboxStatus.PENDING` — لا يحمي أثناء حالات أخرى إن وُجدت لاحقًا.

لا يوجد مسار يكتب تفضيلتين دائمتين لنفس التمرين بعد upsert ناجح مع alias map موجودة — الاختبارات تغطي canonical write + drop legacy slug.

**الحكم:** لا تكرار دائم مؤكد في الحالة الطبيعية؛ ثغرة slug-in-outbox → فشل رفع صامت محتمل (S3/S2 خفيف). يُنصح بتمرير canonical id إلى الـ enqueue دائمًا.

---

### J5 — `parseIsoToEpochMs` وتجاهل timezone offset

**الإجابة:** مؤكد. الدالة تقرأ `YYYY-MM-DDTHH:mm:ss[.sss]` وتستدعي `utcEpochMs` مباشرة دون قراءة `Z` أو `+03:00` / `-05:00`. أي offset غير UTC يُفسَّر خطأً كـ UTC wall-clock.

**الدليل:**

```208:228:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/DayCustomizationLocalStore.kt
        fun parseIsoToEpochMs(iso: String): Long? {
            ...
                return utcEpochMs(year, month, day, hour, minute, second, millis)
            }
            return null
        }
```

السيرفر الحالي يرسل `toISOString()` (Z) في `customizationsUpdatedAt` — آمن عمليًا اليوم. إن تغيّر العقد أو وُجد مصدر آخر بـ offset → مقارنات `serverMs <= existing.lastModifiedAt` تنحرف صامتًا.

**الحكم:** H20 مؤكدة كـ S3 (قنبلة صامتة) — وثّق العقد «ISO يجب أن يكون Z» أو أصلح الـ parser (kotlinx-datetime / تجاهل آمن مع اختبارات offset).

---

### ملاحظات على الملفات المساعدة (ضمن النطاق)

**`UserProgramEnrollmentLocalStore`:** hydrate كامل/دلتا سليم؛ يخزّن `customizationsUpdatedAt` لكن لا يستخدمه خارج تمريره لاحقًا عبر sync payload إلى day store. `PlanSyncRepository.fetchSyncUserPrograms(forceRefresh=true)` عند enroll — تكلفة حمولة (محور H) لا كسر اتساق overrides.

**`HomeTrainModeHydrator`:** يصلح `trainMode` عندما `/mobile/home` يقول no_plan بينما `/plan`+`/plan/today` نشطان. لا يكتب تخصيصات أيام ولا تقارير؛ أثره نداءات شبكة إضافية عند فتح Home (محور H/G) وليس تعارض overrides.

**git diff `ReportsSyncRepository`:** تحسين متوسط form في `patchExerciseMetricsFromUpload` فقط — إيجابي ولا يتعارض مع توصيات J2/J3؛ لا يحلّها.

---

## 3. نتائج الفرضيات المسندة (H..)

| فرضية | الحكم | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |
|---|---|---|---|---|
| **H04** | **مؤكدة** | `DayCustomizationLocalStore.kt:86`, `:109-110`؛ لا تصفير بعد outbox SUCCESS؛ اختبار `hydrateFromBackend_skipsUserModifiedDay` | A يعدّل يومًا → رفع ينجح → B يعدّل على السيرفر → sync على A يتخطى اليوم | جهاز A لا يرى تخصيصات أحدث من أجهزة أخرى/Admin إلى الأبد (S1) |
| **H05** | **مؤكدة** | `ReportsSyncRepository.kt:83-95`, `:105-111`, `:402-403`؛ سيرفر ISO في `mobile-sync.service.ts:579-586` | إكمال يوم أوفلاين → تقرير محلي بمفتاح workoutId → sync يجلب تقرير السيرفر → `continue` لأن المحلي موجود | تقرير أفقر/`completedAt` epoch-string/`id` خاطئ يبقى؛ لا server-wins بعد الرفع (S2) |
| **H16** | **مؤكدة** | `ReportsSyncRepository.kt:341-346`, `:190-193`؛ سيرفر streak بـ Set أيام `:1210-1242` | غير Pro يكمل تمرينين مخططين في نفس اليوم → daysTrained/streak +2؛ syncDashboard يعيد الكاش فقط | انحراف دائم في ملخص التقارير لغير Pro (S2) |
| **H20** | **مؤكدة (كامنة)** | `DayCustomizationLocalStore.kt:208-226`؛ السيرفر يرسل Z اليوم | إن وصل `customizationsUpdatedAt` بـ `+03:00` تُقارن كـ UTC خاطئ | قرارات hydrate زمنية خاطئة إن تغيّر العقد (S3) |

---

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | الحكم | الدليل | سيناريو | الأثر |
|---|---|---|---|---|---|
| **J-N1** | S2 | outbox تفضيلات قد يحمل slug بينما الكاش canonical | `MobileWriteSyncRepository.kt:106-111` vs `cacheExercisePreference` → canonical؛ باك `upsertUserExercisePreference` يبحث `id` فقط | UI يمرّر slug → كاش محلي OK → PUT 404 → FAILED_PERMANENT | تفضيل محلي لا يصل للسيرفر؛ أجهزة أخرى لا تراه |
| **J-N2** | S3 | `pendingExerciseIdsFromOutbox` يتجاهل غير PENDING | `ExercisePreferenceLocalStore.kt:112-113` | إن وُجدت حالة IN_FLIGHT لاحقًا دون حماية | hydrate قد يكتب فوق تفضيل قيد الإرسال |
| **J-N3** | S3 | `HomeTrainModeHydrator` لا يشارك في اتساق التقارير/التخصيصات | `HomeTrainModeHydrator.kt:19-44` | — | خارج مسار J للتعارض؛ تكلفة شبكة فقط |

---

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **J-R1** | S1 | صفّر `isUserModified` عند SUCCESS لـ `SAVE_DAY_CUSTOMIZATIONS`؛ في `hydrateFromBackend` احمِ فقط إن وُجد pending outbox لنفس اليوم، وإلا قارن `customizationsUpdatedAt` — `DayCustomizationLocalStore.kt`, `OfflineWriteQueue`/`OutboxDispatcher` hook بعد النجاح | M | سباق: sync يصل قبل تعليم SUCCESS — عالج بفحص pending أولًا |
| **J-R2** | S2 | `hydrateFromSync`: server-wins بعد غياب pending complete/report لنفس `plannedWorkoutId`؛ وحّد `completedAt` إلى ISO عند الكتابة المتفائلة — `ReportsSyncRepository.kt` | M | قد يومض UI إن استُبدل التقرير أثناء العرض؛ مقبول |
| **J-R3** | S2 | أعد حساب `daysTrained`/`currentStreak` من مجموعة تواريخ التقارير المحلية (أو لا تزد إن نفس اليوم موجود)؛ لا تعتمد على +1 الأعمى — `ReportsSyncRepository.patchDashboardFromCompletion` | S–M | سلوك streak المحلي قد ينخفض بعد الإصلاح (تصحيح لا انحدار) |
| **J-R4** | S2/S3 | مرّر canonical exercise id إلى `enqueueExercisePreferenceUpsert/Delete` دائمًا — `MobileWriteSyncRepository.kt` | S | منخفضة |
| **J-R5** | S3 | أصلح `parseIsoToEpochMs` لدعم `Z`/`±HH:MM` أو وثّق العقد + اختبارات — `DayCustomizationLocalStore.kt` | S | منخفضة إن بقي السيرفر على Z |

---

## 6. فجوات الاختبارات

**موجودة راجعتُها:**
- `DayCustomizationLocalStoreTest` — يثبت تخطي `isUserModified` (يوثّق الخلل كسلوك حالي؛ ينقص اختبار «بعد SUCCESS يُسمح بالhydrate»).
- `ExercisePreferenceLocalStoreTest` — canonical upsert + hydrate by id.
- `ReportsSyncRepositoryTest` — بوابة Pro/auth لـ syncDashboard/metrics فقط؛ **لا** يغطي `hydrateFromSync` ولا `recordPending` ولا `patchDashboardFromCompletion`.
- `ReportsPatchWeightedAverageTest` — يغطي diff الجاري لمتوسط form.
- `UserProgramEnrollmentLocalStoreTest`, `HomeTrainModeHydratorTest`, `PlanSyncRepositoryTest` — enrollments/hydrator؛ خارج تعارض التقارير.

**ناقصة مقترحة:**
1. `DayCustomizationConflictResolutionTest.kt` — بعد محاكاة SUCCESS يُصفَّر العلم ويقبل سيرفر أحدث؛ مع pending outbox يُرفض السيرفر.
2. `ReportsSyncRepositoryHydrateTest.kt` — pending يحجب السيرفر؛ بعد إزالة المحلي-pending أو تعليم الرفع، server export يستبدل epoch-string و`id`.
3. `ReportsDashboardPatchTest.kt` — إكمالان في نفس اليوم التقويمي لا يزيدان streak مرتين؛ غير Pro لا يعتمد على sync للتصحيح إن أُعيد الحساب محليًا.
4. `ExercisePreferenceOutboxCanonicalIdTest.kt` — enqueue يحمل id لا slug حتى لو استُدعي بـ slug.
5. `ParseIsoToEpochMsTest.kt` — `Z` و`+03:00` وبدون offset.
