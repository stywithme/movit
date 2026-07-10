# المحور H — عقد الباك وحجم الحمولة (Backend Contract & Payload Budget)
**المراجع:** independent review agent · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**  
`Data-Layer-Review-Brief.md` (§1, §2, §4-H, §5, §6) ·  
`backend/src/modules/mobile-sync/mobile-sync.service.ts` · `mobile-sync.controller.ts` · `mobile-sync.types.ts` · `mobile-home.controller.ts` · `mobile-audio-manifest.service.ts` · `mobile-explore.controller.ts` ·  
`backend/src/modules/programs/programs.service.ts` (`getPublishedForMobile` + `programFullInclude`) · `mobile-user-programs.controller.ts` ·  
`backend/src/modules/reports/reports.controller.ts` · `backend/src/modules/effective-plan/effective-plan.service.ts` (واجهة الاستجابة) ·  
`backend/prisma/schema.prisma` (UserProgram / PlannedWorkoutReport / WorkoutExecution*) ·  
`backend/src/modules/workout-executions/workout-executions.types.ts` + `workout-executions.service.ts` (مسار الرفع) ·  
`kmp-app/.../MovitMobileApi.kt` · `PlanSyncDto.kt` · `TrainingApiDto.kt` · `HomeDto.kt` · `ReportsDto.kt` · `ExploreDto.kt` ·  
`PlanSyncRepository.kt` · `HomeTrainModeHydrator.kt` · `HomeSyncRepository.kt` · `SharedExploreRepository.kt` · `WorkoutUploadMapper.kt` ·  
`core/network/contract/*` + fixtures · `cold_offline_bundle.json` (قياس حجم مرجعي) · `git diff` لـ TrainingApiDto / MobileWriteSyncRepository

**ملاحظة قياس:** بيئة الـ backend المحلية غير متاحة (`curl` على `:3000/:3001` فشل). **لم يُنفَّذ قياس curl فعلي** لـ `/api/mobile/sync`. التقديرات أدناه من الكود + حجم `cold_offline_bundle.json` كمرجع سفلي للكتالوج الجزئي.

---

## 1. الحكم التنفيذي (≤ 10 أسطر)

**يحتاج إصلاحات محددة (S2)** — عقد الدلتا للكتالوج موجود، لكن حمولة المستخدم تنمو بلا سقف وتُعاد كاملة في كل sync، ومسار enroll يُجبر full sync كاملًا لقراءة enrollments فقط.

1. **H08 مؤكدة:** `plannedWorkoutReports` (كل المكتملة) و`userPrograms` (كل الصفوف) بلا `updatedAfter` ولا pagination — تنمو مع تاريخ التدريب.
2. **H07 مؤكدة:** `fetchSyncUserPrograms(forceRefresh=true)` = `GET /mobile/sync?forceRefresh=true` كامل ثم استخراج `userPrograms` فقط؛ لا يوجد `GET /mobile/user-programs` خفيف (الموجود تحت `mobile/user-programs` هو per-id فقط).
3. **H06 جزئية لمحور H:** Explore pull-to-refresh = `syncFull()` على `/mobile/explore` (كتالوج ملخصات، ليس sync الكامل) — إسراف طلبات لكنه أصغر من `/mobile/sync` full؛ ما زال يضرب هدف «لا مطالبات ضخمة» على مستوى الشاشة.

---

## 2. إجابات الأسئلة

### H1. حجم استجابة `/mobile/sync` full ودلتا-فارغة — أكبر ثلاثة مساهمين

**الإجابة:** القياس الفعلي (bytes/gzip على حساب حقيقي) **لم يُنفَّذ** — الباك غير reachable محليًا. التقدير من بنية الحمولة + مرجع cold bundle.

**ما يُبنى في كل `sync()` (حتى الدلتا الفارغة للكتالوج):**

| جزء الحمولة | فلتر دلتا؟ | ملاحظة حجم |
|---|---|---|
| `exercises[]` (config كامل + assignments) | نعم `updatedAt > updatedAfter` | الأكبر في **full** — ~200 تمرين × config غني |
| `programs[]` عبر `getPublishedForMobile` | نعم | export كامل weeks→days→plannedWorkouts→items + template phases — ثقيل جدًا لكل برنامج |
| `workoutTemplates[]` | نعم | export كامل phases/exercises |
| `messageLibrary` | مشتق من تمارين **الدفعة فقط** | صغير في دلتا فارغة؛ كبير في full |
| `systemMessages` | **لا** — يُحمَّل كاملًا كل مرة | cold bundle: ~66 KB لـ 321 رسالة نظام |
| `audioManifest` | مبني من library + system + exercise audio في الدفعة | في full: مئات الملفات + `getAudioFileSize` لكل URL |
| `userPrograms` | **لا** | كل enrollments + `customizations` JSON |
| `plannedWorkoutReports` | **لا** (status=completed فقط) | يشمل حقل `report` JSON الكامل — ينمو خطيًا مع الأيام المكتملة |
| `userExercisePreferences` | **لا** | عادة صغير |
| `meta` + `messageLibraryStats` | دائمًا | صغير في الحجم، غالي في الاستعلامات |

**مرجع سفلي من الكود المحلي:** `cold_offline_bundle.json` ≈ 229 KB على القرص؛ تفكيك جزئي: `exercises` (10) ≈ 54 KB، `systemMessages` (321) ≈ 66 KB. كتالوج إنتاج (~200 تمرين + ~50 template + ~10 برامج كاملة) يُقدَّر **عدة MB uncompressed** لـ full sync؛ gzip عادة يخفض النص إلى ~20–35% لكن `report` JSON داخل التقارير يبقى كبيرًا.

**أكبر ثلاثة مساهمين (توقّع مرتّب):**
1. **`exercises` configs** في full (أو `programs` exports إن كان عدد البرامج/الأسابيع كبيرًا).
2. **`plannedWorkoutReports[].report`** لمستخدم نشط 6+ أشهر (ينمو بلا سقف؛ يظهر حتى في «دلتا كتالوج فارغة»).
3. **`audioManifest` + `systemMessages`** في full / كل نداء (system messages بلا دلتا).

**دلتا-فارغة (كتالوج بلا تغييرات + مستخدم مسجّل):** جسم الاستجابة ما زال يحمل `systemMessages` + `audioManifest` (من system على الأقل) + **كل** `userPrograms` + **كل** `plannedWorkoutReports` + meta/stats — ليست «فارغة» فعليًا للمستخدم.

**الحكم:** هدف المنتج #2 («لا مطالبات ضخمة») مكسور جزئيًا على مسار المستخدم-التاريخي حتى عندما يكون الكتالوج مستقرًا.

**دليل:**
```545:597:backend/src/modules/mobile-sync/mobile-sync.service.ts
    if (userId) {
      // ...
      const [userProgramRows, trainingProfile] = await Promise.all([
        prisma.userProgram.findMany({
          where: { userId },
          orderBy: { updatedAt: 'desc' },
        }),
        // ...
      ]);
      // ...
      const reportRows = await prisma.plannedWorkoutReport.findMany({
        where: {
          userId,
          status: 'completed',
        },
        orderBy: [{ weekNumber: 'asc' }, { dayNumber: 'asc' }],
      });
      plannedWorkoutReports = reportRows.map((r) => ({
        // ...
        report: r.report ?? undefined,
      }));
    }
```

---

### H2. حد تعاقدي لـ `plannedWorkoutReports` و`userPrograms`

**الإجابة:** الفرضية H08 **مؤكدة**. لا `updatedAfter` ولا `take` ولا endpoint منفصل.

**اقتراح عقد متوافق خلفيًا (بدون كسر عملاء قديمة):**

| المرحلة | التغيير | توافق |
|---|---|---|
| 1 | إضافة query اختياري `reportsUpdatedAfter` / احترام `updatedAfter` لـ reports و userPrograms عند وجوده؛ إن غاب → السلوك الحالي (full dump) | العملاء الحاليون بلا تغيير |
| 2 | إضافة `includeReports=summary\|full\|none` (افتراضي `full` مؤقتًا ثم `summary`) — summary بدون حقل `report` الضخم | الموبايل يقرأ الملخص للمزامنة ويطلب التفاصيل من `/reports/metrics` عند الحاجة |
| 3 | Pagination: `reportsCursor` + `reportsLimit` (مثلاً 50) مع `meta.reportsHasMore` | يتطلب دعم عميل جديد؛ القديم يبقى full إن لم يُمرَّر cursor |
| 4 | `userPrograms`: فلتر `updatedAt > updatedAfter` + tombstones لـ soft-delete إن لزم؛ أو `GET /mobile/user-programs` خفيف (انظر H3) | |

**خطة الموبايل:** في `ReportsSyncRepository.hydrateFromSync` الاعتماد على ملخصات + backfill؛ التفاصيل الغنية تبقى في `PostTrainingReportLocalStore` / metrics endpoint.

**الحكم:** إصلاح متوسط الجهد (M) على الباك + صغير على العميل؛ أولوية عالية لهدف التخزين الرشيق والمطالبات.

---

### H3. `fetchSyncUserPrograms(forceRefresh=true)` — هل نضيف endpoint خفيف؟

**الإجابة:** **نعم — مبرَّر بقوة.** الفرضية H07 **مؤكدة**.

المسار الحالي:
```452:460:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/MovitMobileApi.kt
    suspend fun fetchSyncUserPrograms(
        forceRefresh: Boolean = false,
        authorization: String? = null,
    ): Result<List<UserProgramExportDto>> = runCatching {
        fetchSync(forceRefresh = forceRefresh, authorization = authorization).getOrThrow()
            .data
            ?.userPrograms
            .orEmpty()
    }
```

يُستدعى من:
- `PlanSyncRepository.enrollProgram` دائمًا `forceRefresh = true` → full catalog + user data ثم رمي كل شيء عدا enrollments.
- `refreshActiveUserProgramId` بـ `forceRefresh = needsFullSync` عندما لا يوجد cache / عند تمرير `programId`.

`Controller('mobile/user-programs')` موجود لكن **بدون** `GET /` للقائمة — فقط `:id/effective-plan`, overrides, progress-metrics, today, إلخ.

**اقتراح:** `GET /api/mobile/user-programs` (أو `/api/mobile/plan/enrollments`) يعيد `{ userPrograms: UserProgramExport[] }` بنفس شكل sync الفرعي، مع `updatedAfter` اختياري. تحويل `fetchSyncUserPrograms` إليه. الإبقاء على تضمينها في `/mobile/sync` للتوافق.

**الحكم:** S2 واضح؛ إصلاح S على الباك + S على العميل؛ ROI عالٍ عند كل enroll.

---

### H4. عدد استعلامات Prisma في دلتا فارغة — هل نحتاج Redis؟

**الإجابة:** حتى مع صفر تغييرات كتالوج، `sync()` ما زال ينفّذ سلسلة استعلامات ثابتة التكلفة تقريبًا.

**جرد تقريبي لدلتا فارغة + `userId` حاضر:**

| # | استعلام / عمل | مصدر |
|---|---|---|
| 1 | `exercise.findMany` (نتيجة []) | sync:341 |
| 2 | `exercise.count` (totals) | :348 |
| 3–4 | deleted + unpublished exercises | :362–382 (دلتا فقط) |
| 5 | `loadSystemMessages` → `feedbackMessageTemplate.findMany` | audio-manifest:195 |
| 6–11 | `getGlobalMessageStats` — count + findMany distinct + 3 aggregates + findMany contents | :647–707 (**دائمًا**) |
| 12 | `workoutTemplate.findMany` ([]) | :425 |
| 13 | `workoutTemplate.count` | :465 |
| 14–15 | deleted/unpublished workouts | :475–489 |
| 16 | `getPublishedForMobile` → `program.findMany` + `programFullInclude` الثقيل | programs.service:1844 |
| 17 | `program.count` | :506 |
| 18–19 | deleted/unpublished programs | :512–526 |
| 20 | `buildAudioManifest` → `getAudioFileSize` لكل URL في system messages (I/O ملفات/GCS) | حتى لو exercises فارغة |
| 21 | `listUserExercisePreferences` | :546 |
| 22–23 | `userProgram.findMany` + `trainingProfile.findUnique` | :548–556 |
| 24 | `plannedWorkoutReport.findMany` (كل المكتملة) | :572 |

**المجموع:** ≈ **20–25+** استعلام Prisma/IO لكل نداء دلتا «فارغ»، منها `getGlobalMessageStats` وحده ~6، و`programFullInclude` غالي حتى لنتيجة فارغة (تخطيط الاستعلام)، و`getAudioFileSize` متكرر على system audio.

**Redis:** موجود في المشروع (`backend/docker-compose.redis.yml`, `.env.example`) لكن **لا استخدام في `backend/src` لمسار mobile-sync** (بحث `ioredis`/`Redis` في `src` = صفر). مرشح ممتاز لكاش:
- `messageLibraryStats` / fingerprint (TTL قصير أو invalidate عند كتابة templates/assignments)
- `systemMessages` snapshot
- ربما `meta.total*` counts

**الحكم:** نعم — كاش سيرفر لـ stats + systemMessages يعطي ربحًا فوريًا بدون تغيير عقد الموبايل. الجهد M. ليس بديلًا عن إصلاح H2/H3.

---

### H5. `/mobile/home` بلا دلتا/ETag + تكلفة فتح التطبيق

**الإجابة:** كل `GET /mobile/home` يعيد بناء الاستجابة من الصفر — لا `ETag` / `If-None-Match` / `updatedAfter`.

`buildHomeData` يطلق:
- `Promise.all` لـ 8 مصادر (user, levelProfile, assessment, **activePlan بشجرة weeks→days→plannedWorkouts→items+reports**, reassessment, progression count, recent executions, trainingProfile)
- ثم `buildStats` (3 استعلامات إضافية بما فيها حتى 365 timestamp للـ streak)
- ثم `buildTrainMode` الذي قد يستدعي `effectivePlanService.getEffectivePlan` ليوم اليوم
- `buildLevelProgress` استعلام levels إضافي

على الموبايل، بعد home:
```34:38:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/HomeSyncRepository.kt
                api.fetchHome(...).map { response ->
                    HomeTrainModeHydrator.hydrateIfNeeded(...)
```
`HomeTrainModeHydrator` عند `no_assessment` / `no_plan` / status فارغ **و** لا `activeProgram` يطلق حتى **نداءين إضافيين**: `fetchActivePlan` + `fetchTodayPlan`.

دورة فتح تطبيق أونلاين نموذجية (من Brief §1.6): sync (ثقيل) + home (ثقيل) + reports dashboard (Pro) — عاصفة طلبات، وhome يُعاد كاملًا حتى لو لم يتغير شيء منذ دقائق.

**ETag:** قابل للتنفيذ برخص نسبيًا: hash من `(activePlan.updatedAt, last report completedAt, user.totalWorkoutExecutions, pendingReassessment.id, unseenProgression count)` → `304` إن تطابق. البديل الأبسط: `Cache-Control: private, max-age=30` على الموبايل فقط لا يكفي لأن SWR يعيد الطلب عند resume.

**الحكم:** تكلفة فتح التطبيق عالية؛ ETag/304 لـ home = S–M ويستحق بعد إصلاح ميزانية sync.

---

### H6. تناسق الأنواع (DTO ↔ باك) + git diff الحالي

**الإجابة:** مقارنة مركّزة + diff غير المُلتزم.

#### git diff الحالي (H6)

**`TrainingApiDto.kt`:** جعل `stability` / `alignmentAccuracy` / `avgStability` / `avgAlignmentAccuracy` اختيارية (`Float? = null`) بدل مطلوبة.

**`MobileWriteSyncRepository.kt`:** إزالة شرط `hasAuth()` من `uploadWorkoutExecution` — الضيف يُدخل outbox ويُرفع بعد تسجيل الدخول (متوافق مع Brief §1.4).

**`WorkoutUploadMapper`:** يمرّر null عبر `?.let { it / 10f }` للحقول أعلاه.

#### تعارضات عقد الرفع (مهم)

| حقل | KMP (بعد diff) | Backend TS types | Prisma |
|---|---|---|---|
| `RepMetrics.stability` | `Float?` | `number` (مطلوب) | `Int` **NOT NULL** |
| `RepMetrics.alignmentAccuracy` | `Float?` | `number` (مطلوب) | `Int` **NOT NULL** |
| `ExecutionMetrics.avgStability` | `Float?` | `number` (مطلوب) | `Int` **NOT NULL** |
| `ExecutionMetrics.avgAlignmentAccuracy` | `Float?` | `number` (مطلوب) | `Int` **NOT NULL** |

الباك يكتب القيم مباشرة بلا coalescing إلى 0:
```100:108:backend/src/modules/workout-executions/workout-executions.service.ts
      await tx.workoutExecutionMetrics.create({
        data: {
          // ...
          avgStability: sm.avgStability,
          avgAlignmentAccuracy: sm.avgAlignmentAccuracy,
```
إرسال `null` من الموبايل → خطر فشل Prisma / أو سلوك غير معرّف. **فجوة عقد حقيقية** بين اتجاه الموبايل الحالي والـ schema.

#### تناسق sync / home / reports (عينات)

| موضوع | الحكم |
|---|---|
| `plannedWorkoutReports.*.completedAt` ISO من الباك ↔ optimistic epoch-string محلي | معروف في Brief H05/J — ليس فقدان deserialize (JsonElement/String مرن) لكن تعارض دمج |
| `UserProgramExport`: الموبايل لديه `pausedAt` / `totalPausedDays`؛ الباك `UserProgramExport` في `mobile-sync.types.ts` **لا يُصدّرهما** | حقول ميتة على العميل (آمنة بفضل defaults) |
| `startDate`: باك `toISOString()` كامل؛ fixture عقد يستخدم `"2026-01-01"` | التوافق يعمل كـ String؛ لا اختبار يفرض ISO كامل |
| `customizations`: باك object/null؛ fixture sync يستخدم `[]` | lenient |
| Home `weekCalendars` / `catchUpSuggestion` / `isTrainingDay` | مغطاة جزئيًا في `DtoPayloadContractTest` + fixture |
| Reports dashboard/metrics | DTOs موجودة؛ لا fixture حجم/حقول كاملة في contract tests |
| `legacyReport` على upload | `JsonElement?` ↔ `legacyReport Json?` — متوافق شكليًا |
| Explore aliases `workouts`/`workoutTemplates` | مدعومة `@JsonNames` على الموبايل + `withLegacyWorkoutSyncAliases` على الباك |

**الحكم:** أكبر خطر H6 الحالي هو **nullability metrics مقابل NOT NULL في Prisma** — يجب إما إبقاء الحقول مطلوبة على السلك مع 0، أو جعل أعمدة Prisma قابلة للـ null + تحديث types الباك معًا.

---

## 3. نتائج الفرضيات المسندة (H06 / H07 / H08)

| فرضية | الحكم | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |
|---|---|---|---|---|
| **H06** (جزء payload / Explore = full) | **جزئية** لمحور H | `SharedExploreRepository.refreshExploreContent` → `explore.syncFull()`؛ الباك `getExplore` بدون `updatedAfter` = full summaries. ليس `/mobile/sync` الكامل | 1) افتح Explore 2) pull-to-refresh 3) يُستدعى `GET /mobile/explore` بلا `updatedAfter` 4) تنزيل كل المستويات/البرامج/القوالب/التمارين الملخصة | إسراف شبكة/CPU على شاشة متكررة؛ أخف من sync الكامل لكن يخالف هدف «دلتا هي الوضع الطبيعي» |
| **H07** | **مؤكدة** | `MovitMobileApi.kt:452-460`؛ `PlanSyncRepository.kt:36-42,69-70`؛ لا `GET` قائمة على `mobile-user-programs.controller` | 1) سجّل دخول 2) enroll برنامج 3) بعد POST enroll يُستدعى sync بـ `forceRefresh=true` 4) السيرفر يبني exercises+templates+programs+audio+reports كاملة 5) العميل يقرأ `userPrograms` فقط | على كل enroll (وأحيانًا refresh id): full download كتالوج+تاريخ — أسوأ مسار ميزانية حمولة |
| **H08** | **مؤكدة** | `mobile-sync.service.ts:548-597` — `findMany` بلا `updatedAt` filter؛ `report` كامل مضمّن | 1) مستخدم أكمل 100 يوم 2) أي sync (حتى دلتا كتالوج فارغة) 3) السيرفر يعيد 100 تقرير + JSON `report` 4) الحجم ينمو كل أسبوع تدريب | حمولة sync تنمو مع العمر التدريبي؛ تضخم كاش تقارير؛ ضغط DB/CPU ثابت |

---

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | الفرضية الجديدة | الدليل | الأثر |
|---|---|---|---|---|
| **H24** | S2 | `systemMessages` (+ جزء كبير من `audioManifest`) يُعادان في **كل** sync بلا دلتا | `loadSystemMessages()` داخل كل `sync()`؛ `buildAudioManifest` يمر على system دائمًا | دلتا «فارغة» ليست فارغة؛ ~عشرات KB + I/O أحجام ملفات متكرر |
| **H25** | S2 | `getGlobalMessageStats` ≈ 6 استعلامات Prisma **في كل نداء** حتى بدون تغيير رسائل؛ Redis غير موصول للمسار | `getGlobalMessageStats:647-716`؛ لا استخدام Redis في `src` | تكلفة سيرفر ثابتة × كل جهاز × كل دورة throttle |
| **H26** | S2 | `getPublishedForMobile` يستخدم `programFullInclude` (شجرة عميقة inkl. template phases) حتى عندما الدلتا تُرجع 0 برامج — تخطيط استعلام ثقيل | `programs.service.ts:140-214,1835-1851` | زمن استجابة دلتا فارغة أعلى من اللازم |
| **H27** | S1/S2 | بعد جعل metrics اختيارية في KMP، الرفع بـ `null` يصطدم بأعمدة Prisma `Int` غير قابلة للـ null | `TrainingApiDto` diff؛ `schema.prisma:1218-1252`؛ service يمرر القيمة كما هي | فشل رفع execution / outbox permanent — خطر فقدان بيانات تدريب إن أُرسل null |
| **H28** | S3 | `UserProgramExport` على الموبايل يتوقع `pausedAt`/`totalPausedDays` غير المُصدَّرة من sync | `PlanSyncDto.kt:94-95` vs `mobile-sync.types.ts:212-223` | لا كسر؛ حقول ميتة / توقعات خاطئة للمستقبل |
| **H29** | S3 | Home بلا ETag + hydrator قد يضاعف الطلبات (home + plan + today) عند بوابات no_plan | `mobile-home.controller.ts:185-268`؛ `HomeTrainModeHydrator.kt:24-32` | 3 round-trips بدل 1 في حالات شائعة بعد assessment |

---

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **HR1** | S2 | **فصل enrollments عن full sync:** أضف `GET /api/mobile/user-programs` (قائمة) في `mobile-user-programs.controller.ts` + غيّر `MovitMobileApi.fetchSyncUserPrograms` و`PlanSyncRepository` لاستخدامه؛ أبقِ الحقل داخل sync للتوافق | S | منخفضة — مسار جديد؛ راقب أن شكل DTO مطابق |
| **HR2** | S2 | **ميزانية تقارير المزامنة:** طبّق `updatedAfter` (أو `reportsUpdatedAfter`) + وضع `summary` بدون `report` JSON في `mobile-sync.service.ts:572-597`؛ حدّث `hydrateFromSync`؛ أضف pagination لاحقًا | M | متوسطة — عملاء قديمة تعتمد على dump كامل عند إعادة التثبيت؛ تحتاج `forceRefresh`/full ما زال يعيد الكل أو backfill endpoint |
| **HR3** | S1/S2 | **أغلق فجوة nullability للرفع:** إمّا (أ) أعد الحقول مطلوبة على السلك مع default 0 في `WorkoutUploadMapper`، أو (ب) هاجر Prisma إلى `Int?` وحدّث `workout-executions.types.ts` + create data بـ `?? 0` كحد أدنى انتقالي | S–M | (أ) منخفضة؛ (ب) تحتاج migration واهتمام لوحة الأدمن |
| **HR4** | S2 | **كاش سيرفر لـ `getGlobalMessageStats` + `systemMessages`** عبر Redis الموجود؛ invalidate عند كتابة message templates/assignments | M | منخفضة إن TTL قصير؛ تجنّب stale fingerprint طويل |
| **HR5** | S3 | **ETag لـ `/mobile/home`** + تقليل hydrator بعد إصلاح trainMode في الباك | S–M | منخفضة؛ اختبر بوابات no_assessment بعناية |
| **HR6** | S3 | Explore pull-to-refresh → دلتا (`updatedAfter`) بدل `syncFull`؛ اترك full لزر «إصلاح» (محور F يفصّل) | S | منخفضة |

**أهم 3 توصيات للتنفيذ الفوري:** HR1 → HR2 → HR3.

---

## 6. فجوات الاختبارات

### موجودة وراجعتُها
- `DtoPayloadContractTest` — fixtures صغيرة لـ home/explore/sync(userPrograms)/training-config/audio/planned-start/explore-upload/billing؛ يتحقق من lenient تجاه حقول زائدة (`audioManifestVersion`).
- `BackendMobileContractCoverageTest` + `MobileApiContractRegistry` — تصنيف المسارات لا حجمها.
- `BackendContractParityTest` — حقول اشتراك/plan/level؛ ليس sync payload budget.
- `MovitMobileApiContractTest` — عينة sync فيها `userPrograms` + `plannedWorkoutReports` بحجم 1.
- `PlanSyncRepositoryTest` — يثبت أن enroll يمرّر `forceRefresh=true` (يوثّق المشكلة ولا يمنعها).

### ناقصة (مقترحة)
| ملف مقترح | ماذا يغطي |
|---|---|
| `SyncPayloadBudgetContractTest.kt` | يفرض أن fixture/دلتا موثّقة: عند `updatedAfter` حديث، كتالوج فارغ؛ يفشل إن وُجدت تقارير بلا فلتر في عقد مستقبلي |
| `UserProgramsListEndpointContractTest.kt` | بعد HR1: مسار خفيف لا يتطلب exercises/programs |
| `WorkoutExecutionNullMetricsContractTest.kt` | يوثّق السلوك المتفق عليه عند null stability/alignment (رفض صريح أو 0) — يمنع انحراف KMP/Prisma |
| `fixtures/sync-delta-empty-authenticated.json` | عقد ذهبي لدلتا فارغة: ما يُسمح ببقائه (meta, systemMessages?, user slices) |
| اختبار تكامل باك (e2e) | قياس bytes/gzip لـ full vs delta على حساب seed — يُشغَّل في CI staging |
| توسيع `DtoPayloadContractTest.syncFixture` | تغطية `plannedWorkoutReports` + شكل `report` + ISO `startDate` — الـ fixture الحالي ناقص التقارير أصلًا |

### فجوة منهجية
اختبارات العقد الحالية = **شكل/مسار** وليست **ميزانية حمولة** ولا **سلوك دلتا للمستخدم**. لا يوجد اختبار يمنع إعادة كل `plannedWorkoutReports` في كل sync.
