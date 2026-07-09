# المحور A — هيكل التخزين والحجم
**المراجع:** grok-4.5-xhigh · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**
`Data-Layer-Review-Brief.md` (أقسام 1، 2، 4-A، 5، 6)، `MovitCacheKeys.kt`، `JsonCacheEntry.sq`، `SqlDelightMovitLocalStore.kt`، `MovitLocalStore.kt`، `TrainingConfigRepository.kt`، `SyncCatalogOfflineRepository.kt`، `ExploreSyncRepository.kt`، `ExploreMerge.kt`، `SyncCatalogMapper.kt`، `WorkoutExportMapper.kt`، `MessageLibraryCache.kt`، `SystemMessageCache.kt`، `AudioManifestCache.kt`، `AudioPrefetchRunner.kt`، `AudioClipResolver.kt`، `AudioFileDownloadPort.kt`، `AudioFileDownloader.android.kt`، `AudioFileDownloader.ios.kt`، `PostTrainingReportLocalStore.kt`، `SessionJournalStore.kt`، `ReportsSyncRepository.kt`، `ProgramFlowSyncRepository.kt`، `WorkoutSessionSyncRepository.kt`، `WeekOfflinePackPrefetcher.kt`، `OutboxMaintenance.kt`، `MovitData.kt` (clearAllUserData)، `MovitSyncOrchestrator.kt` (apply path)، `ExerciseMessageLibraryMerger.kt`، `MovitCachePolicy.kt`، `SharedExploreRepository.kt`، `HomeSyncRepository.kt`، `AndroidTrainingFrameSnapshotPort.kt`، `IosTrainingFrameSnapshotPort.kt`، `AndroidMovitPlatform.clearLegacyUserCaches`، `MovitDataClearAllUserDataTest.kt`، واختبارات ذات صلة (`AudioPrefetchRunnerTest`، `ExploreSyncRepositoryTest`).

---

## 1. الحكم التنفيذي (≤ 10 أسطر)

**يحتاج إصلاحات محددة (S2 تضخم/ازدواج، لا كسر جوهري للمعمارية).** التخزين JSON-blob + outbox يحقق offline للكتالوج والتدريب، لكن **لا يوجد أي GC لـ `json_cache_entry`** بينما النمو التاريخي (تقارير planned، post-training لكل set، metrics بمفاتيح query، effective plans، لقطات `frame_captures/`) غير محدود. ازدواج مقصود جزئيًا (explore summary مقابل full export) وازدواج إهدار واضح (workout export + training-config مكتوب، نصوص الرسائل في المكتبة وداخل كل config، journal SQL+json_cache). الصوت وحده له سقف 100MB + orphan cleanup عند full sync؛ اللقطات وملفات الصوت **لا تُمسح عند logout** رغم مسح SQL. write amplification مثبت عند دلتا رسائل (`applySyncMessageLibrary` يعيد كتابة كل configs).

**أهم 3 نتائج:** (1) H10 مؤكدة — لا GC للـ json_cache. (2) H09 مؤكدة — أربعة ازدواجات تخزين. (3) H11 (جزء التخزين) مؤكدة — إعادة كتابة كل exercise configs عند دلتا رسائل.

---

## 2. إجابات الأسئلة (سؤالًا سؤالًا)

### A1. جدول namespaces / المفاتيح / الحجم التقديري / النمو غير المحدود

**سيناريو التقدير (6 أشهر):** كتالوج ~200 تمرين، ~50 template، ~10 برامج؛ تدريب 4 أيام/أسبوع ≈ **~104 يوم مكتمل**؛ بافتراض ~4 تمارين/يوم × ~3 sets ≈ **~1 250 post-training report**؛ planned reports ≈ 104؛ prefetch أسبوعي يراكم effective plans.

| Namespace (`store`) | المفاتيح | المحتوى | نمو؟ | تقدير حجم بعد 6 أشهر |
|---|---|---|---|---|
| `explore_cache` | `explore_data_json`, `explore_last_sync` | ملخصات exercises/templates/programs (بطاقات) | محدود بالكتالوج | ~0.5–1.5 MB |
| `exercise_config_cache` | `exercise_config_<slug>` ×N، `exercise_config_slug_index`، `exercise_config_slug_aliases`، `exercise_id_slug_<id>` | configs كاملة + نصوص رسائل مدموجة | محدود بالكتالوج؛ يُعاد كتابته كثيرًا | ~8–25 MB (الأكبر في الكتالوج) |
| `message_library_cache` | `message_library_json` | مكتبة رسائل منزوعة التكرار | محدود | ~0.2–1 MB |
| `system_message_cache` | `system_messages_json` | رسائل نظام | محدود | ~50–200 KB |
| `audio_manifest_cache` | `audio_base_url`, `audio_manifest_json` | metadata ملفات صوت | محدود (merge قد يراكم أسماء حتى full) | ~50–300 KB JSON؛ **ملفات FS حتى 100 MB** |
| `program_cache` | `program_export_<id\|slug?>` | `ProgramExportDto` كامل | محدود؛ خطر مفتاح slug مزدوج | ~1–4 MB |
| `workout_template_cache` | `workout_export_<id>` | `WorkoutExportDto` كامل | محدود | ~2–6 MB |
| `catalog_index_cache` | `program_id_index`, `workout_template_id_index` | فهارس ids | محدود | ~5–20 KB |
| `session_cache` | `workout_template_training_config_<id>`، `effective_plan_<up>_<w>_<d>` | training-config محوّل + خطط أيام | **effective plans تنمو بلا حذف** | configs: ~1–3 MB مكرر؛ plans: ~0.5–3 MB+ |
| `reports_cache` | `reports_dashboard_json`، `planned_workout_report_*` + index، `reports_metrics_*`، `reports_exercise_*`، `post_training_report_*`، `session_report_*`، `exercise_set_reports_*`، `report_session_exercise_*` | تقارير ولوحة ومتريكس | **غير محدود مع التاريخ** | planned ~0.5–2 MB؛ metrics query keys ~0.5–5 MB؛ **post-training ~50–250 MB** (الأكبر) |
| `session_journal_cache` | `session_journal_<id>` | checkpoint جلسة حية (مكرر مع جدول SQL) | مؤقت إن اكتمل المسار | ~0–2 MB أثناء الجلسة |
| `home_cache` | `home_data_json` | لوحة المنزل | محدود (مفتاح واحد) | ~50–300 KB |
| `day_customization_cache` | `day_<up>_<w>_<d>` | تخصيصات أيام | ينمو مع الأسابيع المستخدمة | ~0.1–1 MB |
| `exercise_preferences_cache` | `pref_<id>` | تفضيلات تمارين | محدود بعدد التمارين المعدّلة | ~50–500 KB |
| `user_program_enrollment_cache` | `user_program_enrollment_*` + index | enrollments | محدود بعدد البرامج | ~20–200 KB |
| `training_preferences_cache` | `training_preferences_json` | تفضيلات تدريب محلية | محدود | ~1–10 KB |
| `sync_manager_prefs` | طوابع/عدادات/fingerprint | metadata مزامنة | محدود | ~1–5 KB |
| **جدول SQL** `session_journal_entry` | per session | نفس payload الجورنال | مؤقت | مكرر مع json_cache |
| **جدول SQL** `outbox_entry` | عمليات معلقة | كتابات أوفلاين | له retention 7 أيام لـ SUCCEEDED فقط | عادة <1 MB |
| **Filesystem** `audio_cache/{ar,en}/` | ملفات صوت | clips | سقف 100 MB + orphan على full | ≤100 MB |
| **Filesystem** `frame_captures/<sessionId>/` | JPEG peak + thumb + replay | لقطات تدريب | **بلا سقف ولا حذف** | ~20–150 MB+ بعد 6 أشهر |

**إجمالي تقديري (مستخدم نشط 6 أشهر):** ~**100–400 MB** محليًا، الغالبية من **post-training JSON + frame JPEGs + audio**؛ الكتالوج نفسه عادة <40 MB.

**أين النمو غير المحدود؟**
1. `reports_cache` — `post_training_report_*` + فهارس set + `planned_workout_report_*` (hydrate يضيف ولا يحذف) + `reports_metrics_*` لكل تركيبة query.
2. `session_cache` — `effective_plan_*` من `WorkoutSessionSyncRepository` / `WeekOfflinePackPrefetcher` بلا eviction.
3. `frame_captures/` على القرص — كتابة فقط، لا cleanup في أي مسار مراجعة.
4. (ثانوي) `AudioManifestCache.mergePartial` يراكم filenames حتى full sync ينظّف الملفات؛ الـ JSON نفسه قد ينتفخ بين fulls.

**الدليل — لا عمليات حذف عامة للكاش:**

```29:30:kmp-app/core/data/src/commonMain/sqldelight/com/movit/core/data/db/JsonCacheEntry.sq
deleteAll:
DELETE FROM json_cache_entry;
```

`deleteAll` يُستدعى فقط من `clearAllUserData` (logout/session expiry)، لا من retention دوري. الـ outbox وحده له purge:

```7:12:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OutboxMaintenance.kt
object OutboxMaintenance {
    const val SUCCEEDED_RETENTION_DAYS = 7
    suspend fun purgeCompletedOlderThanRetention(localStore: MovitLocalStore): Int {
        val cutoff = MovitClock.nowEpochMs() - SUCCEEDED_RETENTION_DAYS * MS_PER_DAY
```

**الحكم:** الكتالوج bounded؛ مسار المستخدم (تقارير/خطط/لقطات) unbounded — يخالف هدف المنتج «تخزين رشيق».

---

### A2. ازدواج تخزين نفس المعلومة

| # | الازدواج | الحكم | الدليل |
|---|---|---|---|
| أ | `workout_export_<id>` + `workout_template_training_config_<id>` | **إهدار** — النسخة المحوّلة مشتقة حتميًا؛ `readWorkoutTrainingConfig` يعيد الحساب من الـ export أصلًا، لكن `applyFromSync` ما زال يكتب النسختين | أدناه |
| ب | `message_library_json` + نصوص مدموجة داخل كل `exercise_config_*` | **إهدار جزئي بمبرر legacy parity** — المكتبة للـ re-merge؛ الدمج عند persist يضاعف النصوص عبر ~200 config | أدناه |
| ج | journal في `session_journal_entry` **و** `session_journal_cache` | **إهدار** — نفس JSON مرتين في كل checkpoint | أدناه |
| د | برنامج تحت `program_export_<id>` ومن `syncProgram(slug)` تحت `program_export_<slug>` | **إهدار مشروط** — يحدث عند استدعاء API بمفتاح slug؛ القراءة تحاول id ثم بحث بالـ slug | أدناه |
| هـ | explore summaries vs full program/workout/exercise stores | **مقصود** (index/card vs full) — مقبول | `SyncCatalogMapper` يبني بطاقات خفيفة |
| و | post-training في `reports_cache` + `legacyReport` داخل outbox/upload | **مقصود مؤقتًا** حتى نجاح الرفع؛ الـ local store يبقى بعد الرفع بلا TTL | `PostTrainingReportLocalStore` |

**أ — workout export + training-config:**

```86:100:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/SyncCatalogOfflineRepository.kt
                MovitCachePolicy.writeJson(
                    localStore,
                    MovitCacheKeys.WORKOUT_TEMPLATE_STORE,
                    MovitCacheKeys.workoutTemplateExportKey(workout.id),
                    workout,
                    WorkoutExportDto.serializer(),
                )
                val trainingConfigDto = WorkoutExportMapper.toTrainingConfig(workout)
                MovitCachePolicy.writeJson(
                    localStore,
                    MovitCacheKeys.SESSION_STORE,
                    MovitCacheKeys.workoutTemplateTrainingConfigKey(workout.id),
                    trainingConfigDto,
                    WorkoutTemplateTrainingConfigDto.serializer(),
                )
```

بينما القراءة المفضّلة تعيد الاشتقاق:

```44:45:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/SyncCatalogOfflineRepository.kt
    fun readWorkoutTrainingConfig(templateId: String): WorkoutTemplateTrainingConfigDto? =
        readWorkoutExport(templateId)?.let(WorkoutExportMapper::toTrainingConfig)
```

→ النسخة في `SESSION_STORE` فائضة لمسار الكتالوج (تُستخدم كـ fallback في `WorkoutSessionSyncRepository.readCachedTrainingConfig` فقط).

**ب — رسائل مكررة:**

```217:223:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
    private fun mergeRecordForPersist(
        record: ExerciseConfigRecord,
        messageLibrary: List<SyncMessageTemplateDto>,
    ): ExerciseConfigRecord {
        if (messageLibrary.isEmpty()) return record
        if (!ExerciseMessageLibraryMerger.hasUnresolvedAssignments(record, messageLibrary)) return record
        return ExerciseMessageLibraryMerger.resolveRecords(listOf(record), messageLibrary).single()
```

+ `MessageLibraryCache.save` يحتفظ بالمكتبة كاملة. بعد الدمج، نفس النصوص ar/en موجودة في المكتبة وداخل configs.

**ج — journal مزدوج:**

```18:29:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/journal/SessionJournalStore.kt
    fun saveCheckpoint(snapshot: SessionJournalSnapshot, status: SessionJournalStatus = SessionJournalStatus.ACTIVE) {
        localStore.writeJsonCache(
            store = MovitCacheKeys.SESSION_JOURNAL_STORE,
            key = MovitCacheKeys.sessionJournalKey(snapshot.sessionId),
            value = MovitJson.encodeToString(SessionJournalSnapshot.serializer(), snapshot),
        )
        localStore.upsertSessionJournal(
            sessionId = snapshot.sessionId,
            exerciseId = snapshot.exerciseId,
            payloadJson = MovitJson.encodeToString(SessionJournalSnapshot.serializer(), snapshot),
            status = status.storageValue,
        )
    }
```

القراءة تفضّل جدول SQL ثم json_cache — الاثنان يُكتبان دائمًا.

**د — برنامج id وslug:**

```65:90:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ProgramFlowSyncRepository.kt
    suspend fun syncProgram(programId: String): AppResult<ProgramExportDto> {
        ...
        val cacheKey = MovitCacheKeys.programKey(programId)
        ...
        MovitCachePolicy.writeJson(
            store,
            MovitCacheKeys.PROGRAM_STORE,
            cacheKey,
            response.data!!,
            ProgramExportDto.serializer(),
        )
```

`programKey` لا يُطبّع إلى id — إن مُرّر slug يُنشأ مفتاح ثانٍ بجانب `program_export_<uuid>` من sync.

**الحكم:** أ+ج إهدار واضح؛ ب إهدار بمبرر توافق؛ د إهدار عند مسار slug؛ هـ مقصود ومقبول.

---

### A3. لا GC لـ `json_cache_entry` — خطة تنظيف مقترحة

**الواقع:** لا يوجد purge/TTL/LRU على مستوى `json_cache_entry`. المسح الوحيد الشامل هو `clearAllUserData`. Tombstones من sync تحذف كيانات كتالوج محددة فقط.

**خطة مقترحة لكل namespace (على البنية الحالية، بدون schema علائقي جديد):**

| Namespace | سياسة مقترحة | محفّز |
|---|---|---|
| `explore_cache` / configs / catalogs / message / system / audio manifest | الإبقاء؛ الاعتماد على full sync + tombstones | كما هو |
| `reports_cache` → `planned_workout_report_*` | احتفظ بآخر N يومًا أو آخر 90 تقريرًا؛ احذف الأقدم من الفهرس + المفتاح | بعد `hydrateFromSync` / أسبوعيًا في sync |
| `reports_cache` → `post_training_report_*` + set index + session_exercise ref | TTL 30–60 يومًا **أو** بعد تأكيد رفع outbox SUCCEEDED + عمر >14 يوم؛ احذف ملفات `frame_captures/<session>` المرتبطة | بعد replay نجاح / صيانة ليلية |
| `reports_cache` → `reports_metrics_*` / `reports_exercise_*` | سقف مفاتيح (مثلاً 50) LRU حسب `updated_at_epoch_ms` في الصف | عند `syncMetrics` |
| `reports_cache` → dashboard | مفتاح واحد — لا حاجة | — |
| `session_cache` → `effective_plan_*` | احذف خطط أسابيع أقدم من الأسبوع الحالي±1، أو TTL 21 يومًا | بعد prefetch أسبوع جديد / sync |
| `session_cache` → `workout_template_training_config_*` | **توقف عن الكتابة** إن بقي الاشتقاق من export (A2-أ)؛ وإلا اربط الحذف بـ `deletedWorkoutTemplateIds` (موجود جزئيًا) | عند applyFromSync |
| `session_journal_cache` + SQL journal | أبقِ مسار الحذف الحالي؛ أضف sweep لـ `abandoned` أقدم من 7 أيام | cold start |
| `day_customization_cache` / enrollments / prefs | اربط بـ enrollment نشط؛ امسح enrollments قديمة عند full sync enrollments | hydrate enrollments |
| `audio_manifest` + FS | أبقِ 100MB + orphan على full؛ استدعِ orphan أيضًا عند حذف تمارين من الدلتا إن أمكن | بعد sync |
| `frame_captures/` | سياسة حذف صريحة (انظر A6) | مع GC التقارير / logout |

**تنفيذ كسول مقترح:** `JsonCacheMaintenance.purge(store, predicate)` يستخدم `listJsonCacheEntries` + `updated_at_epoch_ms` (العمود موجود في الجدول لكن غير معرّض في واجهة القراءة الحالية — يحتاج query بسيط أو تخزين timestamp داخل JSON).

---

### A4. Write amplification

| العملية | ماذا يُعاد كتابته | التكلفة التقريبية | الحكم |
|---|---|---|---|
| دلتا `messageLibrary` غير فارغة | **كل** exercise configs في الفهرس عبر `applySyncMessageLibrary` | ~200 encode+upsert حتى لو تغيّرت رسالة واحدة | **عالٍ — مثبت** |
| دلتا explore/catalog صغيرة | إعادة serialize لـ `explore_data_json` كامل بعد merge | ~0.5–1.5 MB كتابة لكل دلتا كتالوج | متوسط — مقبول للكتالوج الحالي |
| دلتا workout template واحد | كتابة export + training-config لذلك الـ id فقط | صغير لكل عنصر | مقبول؛ الازدواج يضاعف ×2 |
| `AudioManifestCache.mergePartial` | إعادة كتابة manifest JSON كامل | صغير | مقبول |
| checkpoint journal | كتابة مزدوجة SQL+json لكل عدة مكتملة | صغير×2 | إهدار طفيف متكرر أثناء الجلسة |
| `patchExerciseMetricsFromUpload` / dashboard patch | مفتاح واحد | صغير | مقبول |

**دليل H11 (جزء التخزين):**

```122:145:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
    fun applySyncMessageLibrary(
        messageLibrary: List<SyncMessageTemplateDto>,
    ): Int {
        if (messageLibrary.isEmpty()) return 0
        val slugs = readSlugIndex()
        if (slugs.isEmpty()) return 0
        val merged = ExerciseMessageLibraryMerger.resolveRecords(
            records = slugs.mapNotNull { slug -> readRecordFromDisk(slug) },
            messageLibrary = messageLibrary,
        )
        merged.forEach { record ->
            ...
            MovitCachePolicy.writeJson(... exerciseConfigKey(record.slug) ...)
        }
        return merged.size
    }
```

يُستدعى من orchestrator كلما `payload.messageLibrary.isNotEmpty()`:

```195:203:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
            if (payload.messageLibrary.isNotEmpty()) {
                if (isFullSync) {
                    messageLibraryCache.replaceFull(payload.messageLibrary)
                } else {
                    messageLibraryCache.mergePartial(payload.messageLibrary)
                }
                trainingConfig.applySyncMessageLibrary(
                    messageLibrary = payload.messageLibrary,
                )
            }
```

**ملاحظة:** `hasUnresolvedAssignments` قد يتخطى الدمج عند القراءة إن كانت النصوص مدموجة مسبقًا، لكن `applySyncMessageLibrary` **لا يفلتر** — يعيد `resolveRecords` ثم يكتب **كل** السجلات المُرجَعة من الفهرس.

**اقتراح إصلاح محدد (جهد M):**
1. اكتب فقط الـ slugs التي تغيّر محتواها فعليًا (compare hash/before-after)، **أو**
2. أوقف دمج النصوص عند persist؛ ادمج عند القراءة فقط (`mergeRecordForRead` موجود أصلًا) واحذف `applySyncMessageLibrary` من مسار الدلتا — يقلل التخزين والـ amplification معًا.
3. لـ explore: اختياريًا لاحقاً قسّم البلوب (ليس ضروريًا الآن إن بقي <2 MB).

---

### A5. Blobs single-key — main thread / parsing؟

**الواقع التقني:**
- `MovitLocalStore.readJsonCache` / `writeJsonCache` **synchronous** (ليست suspend).
- Outbox فقط يستخدم `Dispatchers.IO`؛ JSON cache يعمل على خيط المستدعي.

```20:29:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/local/SqlDelightMovitLocalStore.kt
    override fun readJsonCache(store: String, key: String): String? =
        jsonQueries.selectByStoreAndKey(store, key).executeAsOneOrNull()

    override fun writeJsonCache(store: String, key: String, value: String) {
        jsonQueries.upsert(...)
    }
```

**مواضع حساسة:**
1. `ExploreSyncRepository.readCached()` يفكّ البلوب كاملًا في كل استدعاء — يُستدعى من `SharedExploreRepository.getExploreContent` ومسارات library.
2. `exerciseImageUrl(slug)` يفكّ **كامل** `explore_data_json` لصورة واحدة:

```25:29:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ExploreSyncRepository.kt
    fun exerciseImageUrl(slug: String): String? =
        readCached()
            ?.exercises
            ?.firstOrNull { it.slug == slug }
            ?.imageUrl
```

يُستدعى من `AndroidMovitPlatform.exerciseImageUrl` وداخل حلقات `WeekOfflinePackPrefetcher` / `SharedWorkoutSessionRepository` (مع `readCached()` منفصل أحيانًا في نفس المسار → parsing مكرر).

3. `MessageLibraryCache.read()` يفكّ المكتبة كاملة؛ يُستدعى من `mergeRecordForRead` / persist paths.
4. تخفيف جزئي موجود: `TrainingConfigRepository` لديه `MovitLruCache` بحجم 8 للسجلات المُفسَّرة — لا يغطي explore/message blobs.

**هل يثبت ANR؟** لم يُقَس زمن على جهاز؛ الحجم الحالي لـ explore (~1 MB) يجعل jank محتملًا على main إن استُدعي من composition/UI بدون إزاحة لـ IO. الاستدعاءات من `viewModelScope.launch` (Default/Main) شائعة — **مخاطر S3 مؤكدة كتصميم، أثر runtime جزئي/غير مقاس**.

**اقتراح:** (S) كاش in-memory لـ `ExploreDataDto` مع إبطال عند الكتابة؛ (S) فهرس `slug→imageUrl` منفصل؛ (M) جعل قراءات البلوب الثقيلة على `Dispatchers.IO` عند الاستدعاء من UI.

---

### A6. ملفات الصوت واللقطات

#### الصوت
| الجانب | الواقع |
|---|---|
| التخزين | Android: `filesDir/audio_cache/{ar,en}/`؛ iOS: `Caches/audio_cache/{ar,en}/` |
| metadata | `audio_manifest_cache` في SQL |
| التنزيل | `AudioPrefetchRunner.afterManifestApplied` → `downloadFiles` |
| سقف الحجم | `MAX_CACHE_BYTES = 100MB` — LRU بـ lastModified |
| orphan cleanup | فقط عندما `isFullSync == true` |
| حذف عند حذف تمرين | **لا** — الملفات تبقى حتى full orphan أو ضغط السقف |
| حذف عند logout | `AudioManifestCache.clear()` يمسح JSON فقط؛ **ملفات الصوت تبقى على القرص** (`clearAllUserData` لا يستدعي downloader cleanup) |

```22:37:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/audio/AudioPrefetchRunner.kt
    suspend fun afterManifestApplied(isFullSync: Boolean) {
        val state = manifestCache.read() ?: return
        if (isFullSync) {
            val valid = state.manifest.files.map { it.filename }.toSet()
            downloader.cleanupOrphanedFiles(valid)
        }
        ...
        downloader.enforceCacheLimit()
    }
```

```19:21:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/audio/AudioFileDownloadPort.kt
    companion object {
        const val MAX_CACHE_BYTES = 100L * 1024L * 1024L
    }
```

#### اللقطات (frames)
| الجانب | الواقع |
|---|---|
| التخزين | `filesDir|documents/frame_captures/<sessionId>/` (+ `replay/`) |
| الحجم التقريبي | full JPEG ≤720px q85 + thumb 200px + replay 540px لكل peak/rep |
| الربط | المسارات داخل `MovitPostTrainingReport` JSON في `reports_cache` |
| سياسة حذف | **لا توجد** — لا عند اكتمال الرفع، لا عند حذف التقرير، لا عند logout |
| تقدير 6 أشهر | مئات–آلاف الصور → عشرات–مئات MB |

```23:30:kmp-app/feature/training/src/androidMain/kotlin/com/movit/feature/training/AndroidTrainingFrameSnapshotPort.kt
        val dir = File(filesRoot, "frame_captures/$sessionId").apply { mkdirs() }
        val fullFile = File(dir, "$captureId.jpg")
        val thumbFile = File(dir, "${captureId}_thumb.jpg")
```

**الحكم:** الصوت مضبوط نسبيًا (سقف 100MB) مع فجوة logout/orphan-on-delta؛ اللقطات فجوة صريحة تخالف هدف التخزين الرشيق.

---

## 3. نتائج الفرضيات المسندة (H..)

| فرضية | الحكم | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |
|---|---|---|---|---|
| **H09** ازدواج تخزين (أ–د) | **مؤكدة** | أ: `SyncCatalogOfflineRepository.applyFromSync:86-100` مع `readWorkoutTrainingConfig:44-45`؛ ب: `mergeRecordForPersist` + `MessageLibraryCache`؛ ج: `SessionJournalStore.saveCheckpoint:18-29`؛ د: `ProgramFlowSyncRepository.syncProgram:68-90` بمفتاح غير مطبّع | (أ) sync فيه template → مفتاحان؛ (ب) sync رسائل → مكتبة + نصوص داخل configs؛ (ج) أكمل عدة → صف SQL + json_cache؛ (د) افتح برنامجًا عبر slug من Explore → `program_export_<slug>` بجانب id | تضخم كتالوج ~×1.3–2 على القوالب/الرسائل؛ journal يضاعف I/O أثناء التدريب |
| **H10** لا GC لـ json_cache → نمو غير محدود | **مؤكدة** | غياب أي purge عدا `OutboxMaintenance` و`clearAllUserData`؛ مفاتيح تقارير/metrics/plans/post-training بلا TTL؛ `hydrateFromSync` يضيف فقط | درّب 4 أيام/أسبوع 6 أشهر → مئات `post_training_report_*` + `planned_workout_report_*` + `effective_plan_*` + `reports_metrics_*` تتراكم؛ لا شيء يحذفها | خطر امتلاء تخزين الجهاز + بطء `list`/قراءات؛ أسوأ مساهم: post-training + frames |
| **H11** (جزء التخزين) دلتا رسائل → إعادة كتابة كل configs | **مؤكدة** (جزء التخزين فقط؛ جزء drift/full-sync → محور B) | `applySyncMessageLibrary:122-145` + استدعاء orchestrator:195-203 | عدّل رسالة واحدة على السيرفر تدخل في دفعة sync مع `messageLibrary` غير فارغ → الموبايل يقرأ كل slugs ويعيد upsert لكل config | كتابة مضاعفة ~8–25 MB وCPU على كل دلتا رسائل؛ تآكل فلاش وبطء sync |

---

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | النتيجة | الدليل | سيناريو | الأثر |
|---|---|---|---|---|---|
| **A-N1** | S2 | `clearAllUserData` يمسح SQL/manifest JSON لكن **لا يحذف** `audio_cache/` ولا `frame_captures/` | `MovitData.clearAllUserData:94-100`؛ اختبار `MovitDataClearAllUserDataTest` يتحقق من JSON فقط؛ لا استدعاء `cleanupOrphanedFiles`/`File.delete` للقطات | logout أو 401→clear → مستخدم جديد/ضيف يرى؟ لا (المسارات غير معروضة) لكن القرص يبقى ممتلئًا؛ خصوصية: لقطات جلسات سابقة تبقى | تضخم عبر حسابات + بقايا خصوصية على الجهاز |
| **A-N2** | S2 | `exerciseImageUrl` / قراءات explore تفكّ البلوب كاملًا مرارًا بلا memoization | `ExploreSyncRepository:25-29`؛ استدعاءات متعددة في library/prefetch | فتح شاشة جلسة تمرين تحل صورًا لعدة تمارين → N× decode لنفس JSON | jank محتمل (S3→S2 تحت كتالوج أكبر) |
| **A-N3** | S3 | `SESSION_STORE` training-config يُكتب دائمًا رغم أن القراءة الأساسية تعيد الاشتقاق من export | `applyFromSync` vs `readWorkoutTrainingConfig` | أي sync قوالب | إهدار كتابة ثابت ×50 قالب في كل full/دلتا قوالب |
| **A-N4** | S3 | `AudioManifestCache.mergePartial` يستخدم `existing + incoming` ثم `distinctBy filename` — لا يحذف ملفات أُزيلت من السيرفر حتى full sync | `AudioManifestCache.mergePartial:40-46` | حذف صوت من السيرفر بدون full → metadata+ملف يبقيان حتى ضغط 100MB أو full | ملفات يتيمة حتى full |

---

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **A-R1** | S2 | إضافة `JsonCacheMaintenance` + استدعائه من نهاية `MovitSyncOrchestrator` / cold start: TTL/LRU لـ `post_training_report_*`، `planned_workout_report_*`، `reports_metrics_*`، `effective_plan_*`؛ حدّث `PostTrainingReportLocalStore` و`ReportsSyncRepository` بـ `purgeOlderThan` | M | حذف تقرير ما زال يُعرض أوفلاين — اربط بـ «رُفع بنجاح» أو احتفظ بآخر N |
| **A-R2** | S2 | إيقاف دمج نصوص الرسائل عند persist؛ الاعتماد على `mergeRecordForRead` + المكتبة؛ احذف أو قلّص `applySyncMessageLibrary` إلى slugs المتأثرة فقط (`TrainingConfigRepository`, `MovitSyncOrchestrator`) | M | يجب ضمان أن التدريب الأوفلاين بدون مكتبة يعمل (cold bundle يجب أن يزرع المكتبة) |
| **A-R3** | S2 | حذف كتابة `workout_template_training_config_*` من `SyncCatalogOfflineRepository.applyFromSync` والاعتماد على `WorkoutExportMapper.toTrainingConfig`؛ أبقِ المفتاح فقط لمسار `syncTrainingConfig` الشبكي إن لزم | S | تحقق أن `WorkoutSessionSyncRepository.readCachedTrainingConfig` لا يعتمد على النسخة المخزّنة عند غياب export |
| **A-R4** | S2 | في `SessionJournalStore.saveCheckpoint`: اكتب جدول SQL فقط (أو json فقط) — مصدر واحد للحقيقة | S | تأكد أن مسارات الاستعادة/`listActiveCheckpoints` تغطي المصدر المتبقي |
| **A-R5** | S2 | `ProgramFlowSyncRepository.syncProgram`: بعد الجلب اكتب دائمًا تحت `program.id` وامسح مفتاح الـ slug إن وُجد؛ طبّع `cacheKey` | S | منخفضة |
| **A-R6** | S2 | عند `clearAllUserData`: استدعِ `AudioFileDownloadPort.cleanupOrphanedFiles(emptySet())` أو امسح مجلد `audio_cache`؛ امسح `frame_captures/` بالكامل (Android/iOS ports أو helper مشترك) | S | منخفضة إن تم بعد تأكيد عدم الحاجة لرفع معلّق — **نسّق مع محور I حول outbox** |
| **A-R7** | S2 | سياسة حذف لقطات: بعد SUCCEEDED لرفع التقرير أو TTL 30 يومًا احذف `frame_captures/<sessionId>`؛ اربط من `PostTrainingReportLocalStore` purge | M | شاشة تفاصيل تقرير قديمة تفقد الصور — اعرض placeholder |
| **A-R8** | S3 | In-memory cache لـ `ExploreDataDto` + فهرس صور `slug→url`؛ تجنّب decode كامل في `exerciseImageUrl` | S | إبطال الكاش عند كل `writeJson` لـ explore |
| **A-R9** | S3 | استدعِ orphan audio cleanup أيضًا على دلتا عندما ينقص عدد الملفات أو عند tombstone تمارين (ليس فقط full) | S | لا تحذف ملفات ما زالت في assignments محلية |

**قرار معماري؟** لا يلزم. البنية JSON-blob كافية مع GC ووقف الازدواج الفائض.

---

## 6. فجوات الاختبارات

**موجود وراجعتُه (جزئيًا):**
- `ExploreSyncRepositoryTest` / `ExploreMergeTest` — دمج explore، لا حجم/GC.
- `SyncCatalogOfflineRepositoryTest` — تطبيق كتالوج، لا يفرض عدم ازدواج training-config.
- `TrainingConfigRepositoryTest` — configs/messages، لا يقيس عدد الكتابات عند دلتا رسالة واحدة.
- `AudioPrefetchRunnerTest` — orphan على full + download.
- `SessionJournalStoreTest` — checkpoint؛ لا يفشل على الكتابة المزدوجة.
- `PostTrainingReportLocalStoreTest` — put/rekey؛ **لا purge**.
- `MovitDataClearAllUserDataTest` — يمسح JSON/outbox/manifest؛ **لا يتحقق من ملفات الصوت/اللقطات على القرص**.
- `OutboxMaintenance` مغطى عبر `OfflineWriteQueueTest` — لا مقابل لـ json_cache.

**ناقص — مقترح إضافته:**
1. `JsonCacheMaintenanceTest.kt` — TTL يحذف `post_training_report_*` و`effective_plan_*` الأقدم ويبقي الحديثة.
2. `TrainingConfigRepositoryMessageAmplificationTest.kt` — دلتا رسالة واحدة → عدد `writeJsonCache` لـ exercise configs = المتأثرة فقط (بعد الإصلاح) أو يوثّق الوضع الحالي كـ characterization.
3. `SyncCatalogOfflineRepositoryDedupTest.kt` — بعد apply، إما لا يوجد `workout_template_training_config_*` أو يُشتق ويطابق دون تخزين مزدوج.
4. `SessionJournalStoreSingleSourceTest.kt` — checkpoint يكتب مصدرًا واحدًا.
5. `ClearAllUserDataFilesystemTest` (androidTest/ios) — بعد clear، مجلدات `audio_cache` و`frame_captures` فارغة.
6. `ExploreImageUrlIndexTest.kt` — `exerciseImageUrl` لا يعيد decode البلوب كاملاً N مرة (spy على store reads).
7. `ProgramFlowSyncRepositoryKeyCanonicalizationTest.kt` — sync بـ slug لا يترك مفتاحين لنفس البرنامج.
