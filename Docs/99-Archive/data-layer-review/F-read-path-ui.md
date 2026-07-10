# المحور F — مسار القراءة وحداثة الواجهة (Read Path & UI Freshness)
**المراجع:** وكيل مراجعة مستقل (Axis F) · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**
`StaleWhileRevalidate.kt`, `SharedExploreRepository.kt`, `InMemoryExploreRepository.kt`, `ExploreApiMapper.kt`, `ExploreSyncRepository.kt`, `HomeTrainModeHydrator.kt`, `HomeSyncRepository.kt`, `ProgramFlowSyncRepository.kt`, `MovitLocalStore.kt`, `SqlDelightMovitLocalStore.kt`, `MovitCachePolicy.kt`, `MovitAppShellViewModel.kt`, `MovitAppShellRoute.kt`, `MovitAppShellState.kt`, `SharedHomeRepository.kt`, `MovitHomeViewModel.kt`, `MovitHomeRoute.kt`, `SharedTrainRepository.kt`, `MovitTrainViewModel.kt`, `MovitTrainRoute.kt`, `MovitExploreViewModel.kt`, `MovitExploreRoute.kt`, `SharedReportsRepository.kt`, `MovitReportsViewModel.kt`, `MovitReportsRoute.kt`, `SharedReportDetailRepository.kt`, `ReportDetailViewModel.kt`, `ProgramDetailViewModel.kt`, `WorkoutSessionViewModel.kt`, `SharedWorkoutSessionRepository.kt`, `WorkoutSessionApiMapper.kt`, `ExercisePrepareViewModel.kt`, `ExerciseContentMapper.kt`, `LibraryRepository.kt`, `SharedProgramFlowRepository.kt`, `MobileWriteSyncRepository.kt` (مسار patch home), `ReportsSyncRepository.kt` (optimistic dashboard), `LocalizedNameDto.kt`, `ExploreModels.kt`, `StaleWhileRevalidateTest.kt`, `ExploreSyncRepositoryTest.kt` (جزئيًا لـ `exerciseImageUrl`).

## 1. الحكم التنفيذي (≤ 10 أسطر)
**يحتاج إصلاحات محددة** — نمط cache-first/SWR موجود على Home/Train/Reports/Workout Session، لكن حداثة الواجهة بعد sync/كتابات متفائلة غير مكتملة، وExplore pull يفرض full catalog download.

1. **`dataRevision` يزيد فقط عند `SyncOutcome.Success`** ولا يغطي الكتابات المتفائلة ولا `Offline`؛ ومع وجود `innerRoute` يُتخطى إعادة تحميل التبويبات بالكامل.
2. **Pull-to-refresh في Explore (وأيضًا `ProgramFlowSyncRepository.syncExplore`) = `syncFull()`** يمسح `explore_last_sync` → تنزيل كتالوج كامل في كل سحب (H06 مؤكدة).
3. **قراءات JSON متزامنة** على مسار الـ UI: `readJsonCache` بلا `Dispatchers.IO`، و`exerciseImageUrl` يعيد فك البلوب كاملًا داخل حلقات بناء الكتالوج (H22 مؤكدة عمليًا).

## 2. إجابات الأسئلة (سؤالًا سؤالًا)

### F1 — مصفوفة شاشة × مصدر × محفز تحديث

| الشاشة | مصدر القراءة | Mapper / نمط | محفزات إعادة القراءة | يتحدث بعد sync ناجح؟ | يتحدث بعد كتابة متفائلة؟ |
|---|---|---|---|---|---|
| **Home** | `HOME_DATA` blob عبر `MovitData.home.readCached()` / `home.sync()` | `SharedHomeRepository` → `staleWhileRevalidate` → `HomeApiMapper` | `loadInitial`؛ `dataRevision` (تبويب ظاهر فقط)؛ pull عبر Route | نعم إن كان التبويب ظاهرًا و`innerRoute==null` | **لا** — الكاش يُحدَّث (`patchHomeTrainMode`) بلا bump لـ `dataRevision` |
| **Train** | نفس home blob + `explore.readCached()` للصور/أسماء | `SharedTrainRepository` SWR → `TrainApiMapper` | نفس Home | نعم (نفس قيد التبويب) | **لا** (نفس السبب) |
| **Explore** | `EXPLORE_DATA` blob | `SharedExploreRepository` + `ExploreApiMapper` (ليس SWR stream؛ قراءة one-shot) | `loadInitial`؛ `dataRevision`؛ pull → `refreshExploreContent` → **`syncFull`** | نعم (إعادة `getExploreContent` من الكاش المحدَّث) | غير ذي صلة مباشرة |
| **Program Detail** | Explore summaries عبر `LibraryRepository` + `programFlow.syncProgram` / `readCachedProgram` + `home.readCached()` للأسابيع | `ProgramDetailViewModel.load` one-shot | `load()` عند الفتح/Retry فقط | **لا أثناء الفتح** — ليست على `dataRevision`؛ و`innerRoute!=null` يمنع reload التبويبات أصلًا | جزئيًا محليًا بعد save/enroll فقط |
| **Workout Session** | effective-plan / training-config + explore catalog | `SharedWorkoutSessionRepository` SWR | `load()` عند الفتح/Retry | **لا عبر dataRevision** (inner route يوقف أثر الـ shell) | بعد save محلي في الـ VM فقط |
| **Exercise Prepare** | Explore item / workout flow cache + `trainingConfig.ensure` | `ExerciseContentMapper` | `load()` مرة عند الفتح | لا | لا |
| **Reports** | `REPORTS_DASHBOARD` (+ نشاط محلي لغير Pro) | `SharedReportsRepository` SWR / `ReportsApiMapper` | `loadInitial`؛ `dataRevision`؛ pull عبر Route | نعم (قيد التبويب) | **لا** — `patchDashboardFromCompletion` يكتب الكاش بلا إشعار UI |
| **Report Detail** | `TrainingSessionReportCache` ثم metrics API/cache | `MovitSessionReportUiMapper` / `ReportDetailApiMapper` | `load()` مرة | لا عبر dataRevision | لا (إلا بإعادة فتح) |

**دليل `dataRevision`:**

```99:101:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellViewModel.kt
            if (outcome is MovitSyncOrchestrator.SyncOutcome.Success) {
                _state.update { it.copy(dataRevision = it.dataRevision + 1) }
            }
```

```87:95:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellRoute.kt
    LaunchedEffect(state.dataRevision, state.selectedDestination, state.currentInnerRoute) {
        if (state.dataRevision == 0 || state.currentInnerRoute != null) return@LaunchedEffect
        when (state.selectedDestination) {
            MovitAppDestination.Home -> homeViewModel.load(isRefresh = false)
            MovitAppDestination.Train -> trainViewModel.load(isRefresh = false)
            MovitAppDestination.Explore -> exploreViewModel.load(isRefresh = false)
            MovitAppDestination.Reports -> reportsViewModel.load(isRefresh = false)
            MovitAppDestination.Profile -> Unit
        }
    }
```

**ملاحظات مسار القراءة:**
- Home/Train/Reports تستخدم `staleWhileRevalidate` (cache ثم sync في الخلفية).
- Explore: `getExploreContent` يقرأ الكاش فورًا؛ إن فُقد → `syncFull`؛ الـ pull يستدعي `refreshExploreContent` → `syncFull` دائمًا.
- Program Detail: `repository.loadContent()` → `exploreRepository.getExploreContent()` (كاش إن وُجد) ثم `programExportLoader` لـ full program.
- Workout Session: SWR على effective-plan أو training-config؛ يبني كتالوج التمارين من explore blob.
- `HomeTrainModeHydrator`: عند `home.sync()` فقط — إن كان `/mobile/home` يعيد `no_plan`/`no_assessment` بلا `activeProgram`، يطلق `fetchActivePlan` + `fetchTodayPlan` ويُصلح `trainMode` قبل الكتابة للكاش.

**الحكم F1:** نمط القراءة cache-first سليم للشاشات الرئيسية، لكن **الشاشات الداخلية + الكتابات المتفائلة + شرط `currentInnerRoute != null`** تترك UI قديمًا رغم تحديث الكاش.

---

### F2 — هل كل pull-to-refresh في Explore = full download؟

**نعم.** المسار:

1. UI: `MovitExploreEvent.RefreshRequested` → Route يستدعي `load(isRefresh = true)` → `repository.refreshExploreContent()`.
2. `SharedExploreRepository.refreshExploreContent` يستدعي `MovitData.explore.syncFull()`.
3. `ExploreSyncRepository.syncFull` → `syncInternal(clearLastSync = true)` يمسح `EXPLORE_LAST_SYNC` ثم يطلب explore بدون `updatedAfter` فعّال → full merge.

```36:44:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/SharedExploreRepository.kt
    private suspend fun refreshExploreContent(
        language: String,
        strings: ExploreStrings,
    ): AppResult<ExploreContent> {
        return when (val result = MovitData.explore.syncFull()) {
```

```43:84:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ExploreSyncRepository.kt
    suspend fun syncFull(limit: Int? = null): AppResult<ExploreDataDto> =
        syncInternal(clearLastSync = true, limit = limit)
    // ...
        if (clearLastSync) {
            store.removeJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_LAST_SYNC)
        }
```

**مسار إضافي بنفس الضرر:** `ProgramFlowSyncRepository.syncExplore()` يستدعي `exploreSync.syncFull()`، ويُستدعى من `SharedProgramFlowRepository.observePrograms` / `loadPrograms` في مسار `syncFresh` — أي فتح/تحديث قائمة البرامج قد يفرض full explore أيضًا.

```54:58:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ProgramFlowSyncRepository.kt
    suspend fun syncExplore(): AppResult<ExploreDataDto> =
        when (val result = exploreSync.syncFull()) {
            is AppResult.Success -> result
            is AppResult.Failure -> exploreSync.readCached()?.let { AppResult.Success(it) }
                ?: AppResult.Failure(result.message)
        }
```

```39:42:kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/SharedProgramFlowRepository.kt
                val explore = when (val result = repo.syncExplore()) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> repo.readCachedExplore()
                } ?: return@staleWhileRevalidate AppResult.Failure(strings.programNotFound)
```

**اقتراح:** pull العادي → `explore.sync()` (دلتا). زر «إصلاح الكتالوج» / بعد drift / كاش فاسد → `syncFull()` فقط.

**الحكم F2:** يخالف هدف المنتج «لا مطالبات ضخمة» في المسار اليدوي الأكثر شيوعًا. **H06 مؤكدة.**

---

### F3 — قراءات متزامنة ثقيلة / main thread / `exerciseImageUrl` في loops

**واجهة التخزين متزامنة:**

```16:21:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/local/MovitLocalStore.kt
interface MovitLocalStore {
    fun readJsonCache(store: String, key: String): String?
```

```20:21:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/local/SqlDelightMovitLocalStore.kt
    override fun readJsonCache(store: String, key: String): String? =
        jsonQueries.selectByStoreAndKey(store, key).executeAsOneOrNull()
```

`MovitCachePolicy.readJson` يفك التسلسل فورًا على نفس الخيط (لا `withContext(Dispatchers.IO)` على مسار JSON cache؛ الـ outbox فقط يستخدم IO).

**أين تُستدعى من مسار الشاشة:**
- Home/Train/Reports: `readCached` داخل `staleWhileRevalidate` يُجمَع من `viewModelScope` (Main افتراضيًا) — فك home/explore blobs على أول إطار.
- Explore: يخفّف جزئيًا بـ `withContext(Dispatchers.Default)` حول `getExploreContent` / `refreshExploreContent`.
- Workout Session: `buildExerciseCatalog` يمر على **كل** تمارين explore ويستدعي `imageUrlForSlug(slug)` لكل عنصر:

```29:30:kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutSessionApiMapper.kt
        explore?.exercises.orEmpty().forEach { exercise ->
            val entry = exercise.toCatalogEntry(language, imageUrlForSlug)
```

```264:277:kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutSessionApiMapper.kt
    private fun ExploreExerciseDto.toCatalogEntry(
        language: String,
        imageUrlForSlug: (String) -> String?,
    ): ExerciseCatalogEntry {
        val resolvedSlug = slug.ifBlank { id }
        // ...
            imageUrl = imageUrlForSlug(resolvedSlug),
```

```25:29:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ExploreSyncRepository.kt
    fun exerciseImageUrl(slug: String): String? =
        readCached()
            ?.exercises
            ?.firstOrNull { it.slug == slug }
            ?.imageUrl
```

بما أن `platform.exerciseImageUrl` → `MovitData.explore.exerciseImageUrl`، كل استدعاء يعيد `readCached()` (SQL + decode كامل). مع ~200 تمرين = **~200 فك كامل لنفس البلوب** لكل بناء كتالوج (O(n²) تكلفة parsing). والأسوأ: `imageUrl` موجود أصلًا على `ExploreExerciseDto` داخل البلوب الممرَّر — الاستدعاء زائد وظيفيًا.

**الحكم F3:** خطر أداء حقيقي على أجهزة ضعيفة/كتالوج كبير؛ Explore أخف من Home/Session بسبب `Dispatchers.Default` فقط. **H22 مؤكدة عمليًا.**

---

### F4 — `dataRevision` بعد Success فقط؛ أثر الكتابات المتفائلة وOffline

**مؤكد:** الزيادة فقط داخل فرع `SyncOutcome.Success` (انظر مقتطف F1). `Offline` / فشل لا يحرّكان UI عبر الـ shell.

**كتابات متفائلة تحدّث الكاش دون إشعار الشاشات:**

```52:56:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/MobileWriteSyncRepository.kt
        patchHomeTrainMode { mode ->
            mode.copy(
                todayWorkout = mode.todayWorkout?.copy(isCompleted = true),
            )
        }
```

```222:237:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/MobileWriteSyncRepository.kt
    private fun patchHomeTrainMode(transform: (TrainModeDto) -> TrainModeDto) {
        val store = localStore()
        val cached = MovitCachePolicy.readJson(
            store,
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            HomeDataDto.serializer(),
        ) ?: return
        // ... writeJson بدون أي إشارة للـ shell
    }
```

```124:124:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ReportsSyncRepository.kt
        patchDashboardFromCompletion(request, programId)
```

سيناريو: المستخدم يكمل يومًا → يعود لتبويب Home/Reports المفتوح → يرى حالة ما قبل الإكمال حتى: pull يدوي، أو sync Success لاحق يرفع `dataRevision`، أو إعادة دخول التبويب بطريقة تعيد `load` (الـ ViewModels طويلة العمر في الـ shell).

ملاحظة: `RefreshRequested` في Home/Train/Reports ViewModels هو `Unit`، لكن الـ Routes تعترض الحدث وتستدعي `load(isRefresh = true)` — الـ pull يعمل عبر الـ Route لا عبر `onEvent`.

**الحكم F4:** فجوة freshness واضحة بعد optimistic writes؛ Offline success-path للـ UI غير موجود.

---

### F5 — i18n / `localeRevision` مقابل كاش مشترك ar/en

- الكاش يخزّن `LocalizedNameDto(en, ar)`؛ العرض يختار اللغة عند الـ map (`ExploreApiMapper` / `LocalizedNameDto.display`).
- تغيير اللغة: `MovitProfileEffect.LanguageChanged` → `localeRevision++` → `MovitLocaleProvider` يعيد لغة الـ chrome/strings.

```6:13:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/dto/LocalizedNameDto.kt
data class LocalizedNameDto(
    val en: String = "",
    val ar: String = "",
) {
    fun display(language: String): String = when (language.lowercase()) {
        "ar" -> ar.ifBlank { en }
        else -> en.ifBlank { ar }
    }
}
```

```236:238:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellViewModel.kt
            is MovitProfileEffect.LanguageChanged -> {
                _state.update { it.copy(localeRevision = it.localeRevision + 1) }
            }
```

```43:51:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellRoute.kt
    val language = remember(shellState.localeRevision) {
        if (MovitData.isInstalled) {
            MovitData.requirePlatform().preferredLanguage()
        } else {
            "en"
        }
    }
```

**لا يوجد** ربط `localeRevision` → `home/train/explore/reports.load(...)`. عناوين الكيانات المخزّنة مسبقًا في `UiState` (مثل `ExploreItemUi.title` بعد map بلغة قديمة) تبقى حتى إعادة تحميل صريحة.

**الحكم F5:** الكاش المشترك صحيح للغتين؛ **إعادة رسم أسماء الكيانات بعد تغيير اللغة غير مضمونة** للشاشات ذات الحالة المحفوظة في ViewModel.

## 3. نتائج الفرضيات المسندة (H..)

| فرضية | الحكم | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |
|---|---|---|---|---|
| **H06** | **مؤكدة** | `SharedExploreRepository.kt:40` → `ExploreSyncRepository.syncFull:43-44` → مسح `EXPLORE_LAST_SYNC` في `syncInternal:82-84`؛ UI: `MovitExploreViewModel.load(isRefresh=true):48-49` + Route | 1) افتح Explore بكاش دلتا سليم. 2) اسحب للتحديث. 3) راقب طلب `/mobile/explore` بدون `updatedAfter` / استجابة full. | كل pull = تنزيل كتالوج كامل؛ ضرب هدف «لا مطالبات ضخمة». نفس النمط عبر `ProgramFlowSyncRepository.syncExplore`. |
| **H22** | **مؤكدة عمليًا** | `MovitLocalStore` sync API؛ `SqlDelightMovitLocalStore.readJsonCache:20-21` بلا IO؛ استدعاءات SWR من Main؛ `exerciseImageUrl` يعيد decode داخل loop `buildExerciseCatalog` (`toCatalogEntry:276`) | 1) افتح Workout Session لبرنامج فيه يوم بعدة تمارين. 2) راقب زمن `readCached`/CPU أثناء `observeSession`. 3) كرّر مع كتالوج ~200 تمرين. | تأخير أول إطار / jank محتمل؛ تكلفة parsing تتضاعف مع حجم الكتالوج. Explore يخفّف بـ `Dispatchers.Default` فقط. |

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | الفرضية الجديدة | الدليل | سيناريو | الأثر |
|---|---|---|---|---|---|
| **F-N1** | S2 | `dataRevision` يُتجاهل بالكامل عند وجود أي `innerRoute` | `MovitAppShellRoute.kt:88` | أكمل sync بينما المستخدم في Program Detail / Session | تبويبات الخلفية لا تُحدَّث؛ عند الرجوع قد تبقى قديمة حتى تفاعل لاحق |
| **F-N2** | S2 | قائمة البرامج (`observePrograms`/`loadPrograms`) تستدعي `syncExplore()` = `syncFull` | `SharedProgramFlowRepository.kt:39` + `ProgramFlowSyncRepository.kt:55` | افتح Program List أونلاين | full explore download حتى بدون سحب Explore |
| **F-N3** | S2 | `exerciseImageUrl` داخل `buildExerciseCatalog` يعيد فك البلوب لكل slug رغم أن `explore` ممرَّر أصلًا ويحوي `imageUrl` | `WorkoutSessionApiMapper.kt:276` + `ExploreSyncRepository.kt:25-29` | بناء كتالوج جلسة | O(n) SQL+decode لنفس البلوب |
| **F-N4** | S3 | تغيير اللغة يحدّث chrome فقط؛ لا يعيد map لعناوين الكيانات في تبويبات الـ shell | `localeRevision` بلا `load()` مرافق | غيّر ar↔en وابقَ على Explore | عناوين تمارين/برامج بلغة قديمة حتى refresh |
| **F-N5** | S3 | Home/Train/Reports: `RefreshRequested -> Unit` داخل VM بينما Route يعالجها — عقد هش إن استُدعي `onEvent` مباشرة | `MovitHomeViewModel.kt:121` مقابل `MovitHomeRoute.kt:43` | استدعاء `onEvent(RefreshRequested)` من اختبار/مسار بديل | لا يحدث refresh |

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **F-R1** | S2 | اجعل Explore pull و`ProgramFlowSyncRepository.syncExplore` يستخدمان `explore.sync()` (دلتا). ابقِ `syncFull` لزر إصلاح / drift / كاش تالف. الملفات: `SharedExploreRepository.kt`, `ProgramFlowSyncRepository.kt`, اختياريًا API صريح `repairExploreCatalog()`. | S | منخفضة — سلوك الدلتا موجود أصلًا في `syncInternal(clearLastSync=false)` |
| **F-R2** | S2 | وحّد إشارة freshness: بعد optimistic home/reports patches ارفع `dataRevision` (أو SharedFlow `cacheInvalidated`)؛ أزل شرط `currentInnerRoute != null` أو خصّص reload للتبويب الظاهر + أعد تحميل الشاشات الداخلية الحساسة. الملفات: `MovitAppShellViewModel.kt`, `MovitAppShellRoute.kt`, نقاط `patchHomeTrainMode` / `patchDashboardFromCompletion`. | M | متوسطة — تجنب عواصف `load()`؛ قد تحتاج debounce |
| **F-R3** | S2/S3 | (أ) في `buildExerciseCatalog` استخدم `exercise.imageUrl` مباشرة بدل `exerciseImageUrl(slug)`. (ب) انقل `readCached` الثقيل إلى `Dispatchers.Default/IO` في SWR helpers أو repositories. الملفات: `WorkoutSessionApiMapper.kt`, `SharedWorkoutSessionRepository.kt`, `StaleWhileRevalidate.kt` / Shared* repos. | S–M | منخفضة لـ (أ)؛ متوسطة لـ (ب) إن تغيّر ترتيب الإصدارات |

**توصيات إضافية قصيرة:** اربط `localeRevision` بإعادة `load(isRefresh=false)` للتبويب الظاهر؛ أضف اختبار shell يثبت أن Explore refresh لا يمسح `EXPLORE_LAST_SYNC`.

## 6. فجوات الاختبارات

**موجودة راجعتُها:**
- `StaleWhileRevalidateTest.kt` — ترتيب Cached→Fresh/Error.
- `ExploreSyncRepositoryTest.kt` — `exerciseImageUrl` على كاش صغير؛ لا يغطي تكلفة الحلقات ولا أن refresh UI يستدعي `syncFull`.
- `MovitAppShellStateTest.kt` — يزيد `localeRevision`؛ **لا** يختبر `dataRevision` مقابل `SyncOutcome` ولا شرط `innerRoute`.
- اختبارات Explore/Home/Train/Reports state — تغطي refresh flags، لا تثبت مسار الدلتا مقابل full.

**ناقصة مقترحة:**
1. `SharedExploreRepositoryRefreshPolicyTest.kt` — pull يستدعي `sync()` لا `syncFull`؛ زر إصلاح فقط يمسح last sync.
2. `MovitAppShellDataRevisionTest.kt` — Success يرفع revision ويعيد load؛ Offline لا؛ optimistic complete يرفع أو يعادل إشارة؛ `innerRoute` لا يبتلع تحديث التبويب الظاهر.
3. `WorkoutSessionCatalogImageLookupTest.kt` — بناء كتالوج لا يستدعي `readCached` N مرة (spy على store).
4. `LocaleRevisionRemapTest.kt` — بعد `LanguageChanged` عناوين Explore/Home تُعاد من نفس الكاش باللغة الجديدة.
