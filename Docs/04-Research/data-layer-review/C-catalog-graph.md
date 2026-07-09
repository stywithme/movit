# المحور C — سلامة العلاقات بين الكيانات (Catalog Graph Integrity)
**المراجع:** independent review agent · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**
`SyncCatalogGraphValidator.kt`, `SyncCatalogMapper.kt`, `ExerciseIdResolver.kt`, `ExerciseMessageLibraryMerger.kt`, `WorkoutExportMapper.kt`, `TrainingConfigRepository.kt` (aliases + message merge), `SyncCatalogOfflineRepository.kt`, `MovitSyncOrchestrator.kt` (apply path), `MessageLibraryCache.kt`, `AudioManifestCache.kt`, `AudioClipResolver.kt`, `ExploreMerge.kt`, `TrainingConfigEnsure.kt`, `ProgramExportDto.kt`, `WorkoutExportDto.kt`, `PlanSyncDto.kt` (`SyncMessageTemplateDto` / `LocalizedNameDto`), `LocalizedNameDto.kt`, `ExercisePreferenceLocalStore.kt`, `MovitCacheKeys.kt` (pref/alias keys), `TrainingStartResolver.kt`, `WorkoutTemplateSessionMapper.kt`, `WorkoutSessionApiMapper.kt` (effective-plan items), `TrainingSessionViewModel.kt` (`configUnavailable`), `TrainingMotionSession.kt` / `WorkoutUploadMapper.kt` (upload id = slug), والباك: `workout-templates.service.ts` (`buildWorkoutExport`), `programs.service.ts` (`exportPlannedWorkout` / `getPublishedForMobile` / `buildProgramExport`), `program-workout-export.ts` (`flattenTemplatePhasesToProgramItems`), `exercises/json-builder.ts` (`buildExerciseConfig` options), `exercises.service.ts` (`update` — لا يحدّث slug), `mobile-sync.service.ts` (exercise config + tombstones), `mobile-audio-manifest.service.ts` (`buildMessageLibrary` / `buildAudioManifest`), `workout-executions.service.ts` (resolve id|slug), `prisma/schema.prisma` (`Exercise.slug` unique + `onUpdate: Cascade` على progression). اختبارات: `SyncCatalogOfflineRepositoryTest`, `TrainingConfigRepositoryTest`, `SyncCatalogMapperTest`.

---

## 1. الحكم التنفيذي (≤ 10 أسطر)

**يحتاج إصلاحات محددة (S1 على صوت الرسائل المدموجة + S2 على تقرير الجراف الميت وازدواج/ركود النصوص).** الجراف مبني على slug كـ canonical للتمارين داخل templates/programs، مع alias `id→slug` محليًا وقبول الباك لـ id|slug عند الرفع. تغيير slug عبر API الموبايل/الادمن الحالي **غير مدعوم** (`exercises.service.update` لا يكتب `slug`) — خطر إعادة التسمية نظري/يدوي أكثر منه مسار منتج. الأخطر عمليًا: دمج `messageLibrary` عبر `LocalizedNameDto` (en/ar فقط) **يسقط `audioAr`/`audioEn`**، و`applySyncMessageLibrary` يعيد `resolveRecords` فيضاعف motivational/tips. `SyncCatalogGraphValidator` يعمل ويُختبر، لكن نتيجته **تُهمل** في `MovitSyncOrchestrator`. علم `deletedExercise` موجود في العقد لكن مسار `exportPlannedWorkout` **لا يمرّر `resolveExerciseMeta`** فيبقى العلم دائمًا غير مضبوط.

**أهم 3 نتائج:** (1) صوت الرسائل يُفقد عند الدمج إلى configs. (2) تقرير الجراف dead code تشغيليًا. (3) `deletedExercise` غير مُفعَّل من الباك + لا حماية UI عند slug ناقص سوى `configUnavailable`.

---

## 2. إجابات الأسئلة

### C1. المعرّفات وتغيير slug لتمرين منشور

**الإجابة:** محليًا التمرين يُخزَّن بمفتاح slug (`exercise_config_<slug>`) + فهرس slugs + خريطة aliases `id→slug` + مفتاح `exercise_id_slug_<id>`. القوالب/برامج تشير للتمرين بـ **slug نصي** داخل export. الرفع يرسل `exerciseId = exerciseSlug` من الجلسة؛ الباك يحلّ id ثم slug. تغيير slug عبر `exercises.service.update` **غير ممكن حاليًا** (الحقل غير منسوخ إلى `updateData`). لو تغيّر slug يدويًا/بـ SQL: لا يوجد alias `oldSlug→newSlug`؛ الدلتا تكتب المفتاح الجديد دون حذف القديم؛ القوالب القديمة تبقى على الـ slug القديم حتى يُعاد تصدير الـ template؛ التفضيلات بمفتاح `pref_<exerciseId>` (UUID) تبقى سليمة؛ التقارير/الرفع المعلق بالـ slug القديم تنجح طالما الباك ما زال يجد السجل بالـ slug أو id.

**الدليل — aliases id→slug فقط:**

```192:198:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
    private fun registerSyncSlugAliases(record: ExerciseConfigRecord, aliases: MutableMap<String, String>) {
        val canonical = record.slug
        if (canonical.isBlank()) return
        if (record.id.isNotBlank() && record.id != canonical) {
            aliases[record.id] = canonical
        }
    }
```

**الدليل — الرفع يقبل id أو slug:**

```39:52:backend/src/modules/workout-executions/workout-executions.service.ts
    where: { id: payload.exerciseId },
    // ...
      where: { slug: payload.exerciseId },
    // ...
    throw new Error(`Exercise not found: ${payload.exerciseId}`);
```

**الدليل — الجلسة ترفع بالـ slug:**

```43:43:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/TrainingMotionSession.kt
        exerciseId = exerciseSlug,
```

**الدليل — update لا يمس slug:**

```419:429:backend/src/modules/exercises/exercises.service.ts
    const updateData: Record<string, unknown> = {
      updatedBy,
    };
    if (data.name !== undefined) updateData.name = data.name;
    // ... لا يوجد data.slug / updateData.slug
    if (data.status !== undefined) updateData.status = data.status;
```

**الدليل — تفضيلات بالمعرّف الخلفي:**

```21:29:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ExercisePreferenceLocalStore.kt
    fun get(exerciseIdOrSlug: String): UserExercisePreferenceUpsertRequest? {
        val canonicalId = exerciseIdResolver.resolveCanonicalExerciseId(exerciseIdOrSlug)
        return readPreference(canonicalId)
```

**الحكم:** مسار المنتج الحالي يفترض slug ثابتًا. حماية إعادة التسمية ناقصة (لا old→new alias، لا تنظيف مفتاح config القديم في الدلتا). التفضيلات والتقارير المربوطة بـ UUID أأمن من المسارات المربوطة بالـ slug.

---

### C2. حذف/unpublish تمرين داخل template أو program (`deletedExercise`)

**الإجابة:** الـ tombstone `deletedExerciseIds` يُطبَّق على exercise configs وexplore cards. داخل program items، الحقل `deletedExercise` موجود في DTO والـ validator يتخطّاه إن كان `true`، لكن **مسار التصدير الفعلي لا يضبطه أبدًا**: `exportPlannedWorkout` يستدعي `flattenTemplatePhasesToProgramItems(templatePhases)` بدون `resolveExerciseMeta`. عند حذف تمرين مع بقاء template قديم محليًا يشير لـ slug محذوف: الواجهة ما زالت تعرض الصف (اسم = slug أو fallback)، وبدء التدريب يفشل بـ `configUnavailable` إن لم يوجد config؛ `TrainingStartResolver` يعيد `null` إن لم يُحلّ slug من الكاش. `SyncCatalogGraphValidator` يُرجع تقريرًا من `applyFromSync`، لكن الـ orchestrator **يتجاهل القيمة** — لا log، لا drift، لا حجب sync.

**الدليل — العلم لا يُمرَّر من الباك:**

```428:428:backend/src/modules/programs/programs.service.ts
  const items = flattenTemplatePhasesToProgramItems(templatePhases);
```

```80:125:backend/src/lib/program-workout-export.ts
      const meta = slug && options?.resolveExerciseMeta ? options.resolveExerciseMeta(slug) : undefined;
      // ...
        deletedExercise: meta?.deletedExercise,
```

(لا يوجد أي استدعاء آخر لـ `resolveExerciseMeta` في المستودع.)

**الدليل — validator يتخطّى العلم إن وُجد:**

```39:45:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/SyncCatalogGraphValidator.kt
                        planned.items.forEach { item ->
                            if (item.type.equals("exercise", ignoreCase = true) && !item.deletedExercise) {
                                val slug = item.exerciseSlug?.trim().orEmpty()
                                if (slug.isNotBlank() && !trainingConfig.supports(slug)) {
                                    missingExercises += slug
```

**الدليل — التقرير يُرمى:**

```206:207:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
            exploreData = exploreSync.applyFromSync(payload, isFullSync)
            catalogOffline.applyFromSync(payload, isFullSync)
```

**الدليل — عرض بدون فلتر نقص config:**

```156:180:kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutTemplateSessionMapper.kt
    private fun mapExercise(...): WorkoutSessionBlockUi.Exercise {
        val slug = item.exercise.slug.ifBlank { item.exercise.id }
        val catalog = exerciseBySlug[slug]
        // ...
            name = catalog?.name ?: item.exercise.name.localized(language).ifBlank {
                slug.ifBlank { strings.exerciseFallback }
            },
```

**الدليل — بدء جلسة بدون config:**

```117:175:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  private var exerciseConfig: ExerciseConfig? = configRepository.getBySlug(activeSlug)?.config
  // ...
      configUnavailable = exerciseConfig == null,
```

**الحكم:** الحذف من كتالوج التمارين يُنظَّف جزئيًا (config + explore). علاقات template/program تبقى قادرة على الإشارة لـ slug ميت؛ العلم `deletedExercise` عقد ميت؛ تقرير الجراف تشخيصي فقط في الاختبارات.

---

### C3. دورة حياة الرسالة (نص + صوت)

**الإجابة:** الباك يبني `messageLibrary` منزوعة التكرار مع `audioAr`/`audioEn`، ويبني configs بـ `includeMessages: false` + `includeAssignments: true`. الموبايل يخزن المكتبة، ثم يدمج النصوص داخل كل config عبر `ExerciseMessageLibraryMerger`. **الصوت يُقطع عند الحدود:** `SyncMessageTemplateDto.content` من نوع `LocalizedNameDto` (en/ar فقط) — kotlinx.serialization يتجاهل حقول الصوت الزائدة. الدمج ينسخ `LocalizedText(en, ar)` بلا audio. `AudioManifest` منفصل يحمل الملفات للـ prefetch، لكن مسار التغذية الراجعة في المحرك يقرأ `LocalizedText.getAudioUrl()` من الـ config المدموج → صوت فارغ بعد الدمج. تعديل رسالة: إن بقي `feedbackMessages` ممتلئًا، `hasUnresolvedAssignments` يعيد false فلا يُحدَّث النص عند القراءة؛ `applySyncMessageLibrary` يستدعي `resolveRecords` مباشرة فيُضيف نسخًا جديدة فوق القديمة (تكرار) بدل استبدال. حذف assignment: لا يوجد مسار ينظّف النصوص المدموجة من configs قديمة إلا full re-sync للتمرين نفسه بحمولة assignments جديدة فارغة الحقول المقابلة.

**الدليل — الباك يرسل صوتًا في المكتبة:**

```106:130:backend/src/modules/mobile-sync/mobile-audio-manifest.service.ts
  const toLocalizedText = (value: Record<string, unknown> | null | undefined) => {
    // ...
    return {
      ar, en,
      ...(audioAr ? { audioAr } : {}),
      ...(audioEn ? { audioEn } : {}),
    };
  };
```

**الدليل — DTO الموبايل بلا audio:**

```5:8:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/dto/LocalizedNameDto.kt
data class LocalizedNameDto(
    val en: String = "",
    val ar: String = "",
)
```

```70:76:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/dto/PlanSyncDto.kt
data class SyncMessageTemplateDto(
    // ...
    val content: LocalizedNameDto = LocalizedNameDto(),
)
```

**الدليل — الدمج يسقط الصوت:**

```259:260:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/ExerciseMessageLibraryMerger.kt
    private fun LocalizedNameDto.toLocalizedText(): LocalizedText =
        LocalizedText(en = en, ar = ar)
```

**الدليل — المحرك يعتمد audio من LocalizedText:**

```25:25:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/feedback/SetupFeedbackSignals.kt
            audioUrl = message.getAudioUrl(language),
```

**الدليل — عدم إعادة الدمج عند وجود نص (قراءة/persist):**

```32:35:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/ExerciseMessageLibraryMerger.kt
                    "feedback" -> when (assignment.context) {
                        "motivational" -> variant.feedbackMessages.motivational.isEmpty()
                        "tip" -> variant.feedbackMessages.tips.isEmpty()
```

**الدليل — applySyncMessageLibrary يعيد resolve دائمًا (يضيف للقوائم):**

```122:145:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
    fun applySyncMessageLibrary(
        messageLibrary: List<SyncMessageTemplateDto>,
    ): Int {
        // ...
        val merged = ExerciseMessageLibraryMerger.resolveRecords(
            records = slugs.mapNotNull { slug -> readRecordFromDisk(slug) },
            messageLibrary = messageLibrary,
        )
```

```110:112:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/ExerciseMessageLibraryMerger.kt
                "feedback" -> when (assignment.context) {
                    "motivational" -> motivational.add(content)
                    "tip" -> tips.add(content)
```

**الحكم:** دورة النص+الصوت **مكسورة جزئيًا** بعد أول دمج: النص يصل، الصوت لا يُضمَّن في الـ config؛ تحديثات الرسائل إما تُتجاهل عند القراءة أو تُضاعف عند `applySyncMessageLibrary`.

---

### C4. الـ dedup الفعلي وتكرار التخزين

**الإجابة:** نعم — دمج النصوص داخل كل config يلغي فائدة المكتبة المنزوعة التكرار على مستوى التخزين. المكتبة تبقى كمصدر لإعادة الدمج/البصمة، بينما كل exercise config يحمل نسخًا كاملة من `feedbackMessages` / `stateMessages` / `errorMessage`. مع H11 (إعادة كتابة كل configs عند دلتا رسائل) يصبح التكرار تكلفة كتابة أيضًا. البنية المقترحة للـ parity بدون تكرار: الإبقاء على assignments + مكتبة فقط على القرص؛ دمج lazy عند `getBySlug`/`mergeRecordForRead` فقط في الذاكرة (مع LRU الموجود)؛ توسيع DTO المحتوى ليشمل audio؛ جعل `resolveRecords` **يستبدل** حقول الهدف بدل `add`؛ عدم استدعاء merge الجماعي إلا عند تغيّر fingerprint أو assignments.

**الدليل — persist يدمج قبل الكتابة:**

```92:102:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
        ExerciseConfigParser.parseRecords(exercises).forEach { record ->
            // ...
            val toPersist = mergeRecordForPersist(record, messageLibrary)
            MovitCachePolicy.writeJson(
                // ...
                toPersist.withSanitizedConfig(),
```

**الدليل — الباك أصلًا يرسل assignments بلا رسائل مضمّنة:**

```401:404:backend/src/modules/mobile-sync/mobile-sync.service.ts
      const config = buildExerciseConfig(exercise as Parameters<typeof buildExerciseConfig>[0], {
        includeMessages: false,
        includeAssignments: true,
      });
```

**الحكم:** H09-(ب) مؤكدة لهذا المحور. الحل الأبسط: stop-persist-of-merged-text + lazy merge + إصلاح DTO الصوت.

---

### C5. workoutTemplate → phases[] مقابل exercises[] (legacy)

**الإجابة:** الباك عند وجود phases يملأ `exercises` بـ flatMap من المراحل (ازدواج مقصود في الحمولة). الموبايل في `WorkoutExportMapper.toTrainingConfig` يفضّل phases إن وُجدت لبناء القائمة المسطّحة و`phases` معًا؛ وإلا يسقط إلى `export.exercises`. `exerciseSlugs` بنفس القاعدة. مسار العرض `WorkoutTemplateSessionMapper.buildSections` يقرأ `config.phases` أولًا ثم `config.exercises`. لا ازدواج في قائمة التمارين المعروضة طالما phases غير فارغة. `SyncCatalogMapper` لعدّ explore يعتمد على `exercises[]` — آمن لأن الباك يملأها عند وجود phases.

**الدليل — باك يملأ الاثنين:**

```660:684:backend/src/modules/workout-templates/workout-templates.service.ts
    const exercises: WorkoutExerciseExport[] =
      phases.length > 0
        ? phases.flatMap((phase) => phase.exercises)
        : workout.exercises.map((we) => this.buildWorkoutExerciseExport(we));
    return {
      // ...
      exercises,
      phases,
```

**الدليل — موبايل مصدر واحد للقراءة:**

```17:23:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/WorkoutExportMapper.kt
        val phases = export.phases.mapIndexed { index, phase -> mapPhase(phase, index) }
        val flatExercises = if (phases.isNotEmpty()) {
            phases.flatMap { it.exercises }
        } else {
            export.exercises.mapIndexed { index, exercise -> mapExercise(exercise, index) }
        }
```

```97:104:kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutTemplateSessionMapper.kt
        val phases = config.phases.filter { it.exercises.isNotEmpty() }
        if (phases.isNotEmpty()) {
            return phases
                .sortedBy { it.sortOrder }
                .map { phase -> mapPhase(phase, language, strings, exerciseBySlug) }
        }
        val items = mapTemplateExercises(config.exercises, language, strings, exerciseBySlug)
```

**ملاحظة:** `toTrainingConfig` يكتب **نسخة ثانية** في `session_cache` (H09-أ / محور A) — ازدواج تخزين لا ازدواج قراءة UI.

**الحكم:** قراءة الموبايل متسقة (phases-first). الازدواج في الحمولة/التخزين موجود لكنه لا يضاعف عناصر الجلسة.

---

## 3. نتائج الفرضيات المسندة (H..)

| فرضية | الحكم | الدليل | سيناريو الإعادة | الأثر الفعلي |
|---|---|---|---|---|
| **H09** (ازدواج تخزين: workout export + training-config؛ رسائل في مكتبة + داخل config) | **جزئية / مؤكدة لشقّي C** | `SyncCatalogOfflineRepository.applyFromSync:86-100` يكتب export + `WorkoutExportMapper.toTrainingConfig`؛ `mergeRecordForPersist` + `applySyncMessageLibrary` يدمجان النص داخل كل config | sync كامل → افتح مفاتيح `workout_template_export_*` و`workout_template_training_config_*` لنفس id؛ افتح `message_library_json` و`exercise_config_<slug>` | تضخم تخزين وwrite amplification؛ ليس كسر علاقات مباشرة |
| **H11** (دلتا رسائل → إعادة كتابة كل configs) | **مؤكدة + متفاقمة** | `TrainingConfigRepository.applySyncMessageLibrary:122-145` يعيد كتابة كل slugs؛ `resolveRecords` يعمل `motivational.add` دون تفريغ | جهاز بكاش تمارين → sync دلتا فيها `messageLibrary` غير فارغ → راقب حجم/محتوى feedback في config → كرّر الدلتا | تضخيم كتابة + **تكرار نصوص feedback** في كل دورة رسائل |

---

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | النتيجة | الدليل | سيناريو | الأثر |
|---|---|---|---|---|---|
| **C-N1** | **S1** | دمج `messageLibrary` يسقط `audioAr`/`audioEn` لأن `LocalizedNameDto` نص فقط، والمحرك يعتمد `LocalizedText.getAudioUrl` | `LocalizedNameDto.kt:5-8`؛ `ExerciseMessageLibraryMerger.kt:259-260`؛ `SetupFeedbackSignals.kt:25`؛ باك `buildMessageLibrary` يرسل الصوت | sync تمرين مع رسائل صوتية → ابدأ تدريب → إشارات feedback بلا `audioUrl` رغم وجود ملفات في AudioManifest | صوت تدريب صامت للرسائل المدموجة من المكتبة |
| **C-N2** | **S2** | `SyncCatalogGraphReport` يُحسب ثم يُهمل — لا telemetry ولا إصلاح | `MovitSyncOrchestrator.kt:207` يتجاهل القيمة؛ الاختبار فقط في `SyncCatalogOfflineRepositoryTest` | برنامج يشير لـ template/exercise ناقص بعد دلتا → sync ينجح كـ Success | جراف مكسور يبقى صامتًا حتى يفشل المستخدم عند Start |
| **C-N3** | **S2** | `deletedExercise` عقد ميت: التصدير لا يستدعي `resolveExerciseMeta` | `programs.service.ts:428`؛ بحث المستودع عن `resolveExerciseMeta` = تعريف فقط | unpublish/حذف تمرين ما زال في template منشور → program export بلا `deletedExercise:true` | الموبايل لا يستطيع إخفاء/وسم العنصر الناقص من العلم |
| **C-N4** | **S2** | لا alias `oldSlug→newSlug`؛ دلتا slug جديد تترك config القديم | `registerSyncSlugAliases` id فقط؛ `applySyncExercises` يحذف بالـ id tombstone أو full sync فقط | تغيير slug يدويًا في DB ثم delta sync للتمرين | مفتاحان للتمرين؛ قوالب قديمة تكسر `supports(oldSlug)` |
| **C-N5** | **S3** | `WorkoutExportMapper.mapExercise` يضع `name = mapOf("en" to slug)` بدل اسم حقيقي من الـ export | `WorkoutExportMapper.kt:65-68` (الـ export يحمل slug فقط في `exercise` field) | جلسة من training-config بدون explore catalog | عناوين slug حتى يُEnrich من explore |

---

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **C-R1** | S1 | توسيع محتوى رسالة المزامنة ليشمل `audioAr`/`audioEn` (DTO مخصص أو توسيع `LocalizedNameDto` بحذر)، وتحديث `toLocalizedText()` في `ExerciseMessageLibraryMerger.kt` لنسخ الصوت؛ اختبار عقد في `core/network/contract` | M | كسر deserialize إن فُرضت حقول جديدة على كل LocalizedName — فضّل DTO رسالة منفصل |
| **C-R2** | S2 | إصلاح الدمج: في `resolvePoseVariantMessages` ابدأ قوائم feedback فارغة (أو استبدل حسب assignment id) بدل `add` على الموجود؛ اجعل `applySyncMessageLibrary` يستدعي مسار «replace targets» فقط عند تغيّر fingerprint | M | قد يزيل رسائل كانت مضمّنة قديمًا في fixtures — غطِّ باختبار تكرار |
| **C-R3** | S2 | استخدم `SyncCatalogGraphReport`: إن `!isComplete` سجّل + اعتبره drift/backfill (أو على الأقل `MovitCacheFreshnessLog`) في `MovitSyncOrchestrator.kt` بعد `catalogOffline.applyFromSync` | S | false-positive أثناء دلتا جزئية قبل وصول exercises — قيّد التحذير بـ full sync أو بعد apply exercises |
| **C-R4** | S2 | في `exportPlannedWorkout` مرّر `resolveExerciseMeta` يضبط `deletedExercise` عند `deletedAt`/غير published؛ وعلى الموبايل فلتر/وسم العناصر في mappers | M | يعتمد على include exercise.status في template query |
| **C-R5** | S3 | إن بقي slug قابلاً للتغيير لاحقًا: عند تغيّر slug اكتب alias قديم→جديد، احذف مفتاح config القديم، وأعد تصدير templates المتأثرة في نفس حمولة sync | L | نادر اليوم لأن API لا يغيّر slug |

---

## 6. فجوات الاختبارات

**موجودة راجعتُها:**
- `SyncCatalogOfflineRepositoryTest` — persist graph + tombstones + validator missing slug.
- `TrainingConfigRepositoryTest` — supports/index + id→slug alias.
- `SyncCatalogMapperTest` — تمرير `deletedExerciseIds` إلى explore slice.

**ناقصة مقترحة:**
- `ExerciseMessageLibraryMergerAudioTest.kt` — يثبت أن `audioAr`/`audioEn` يصلان إلى `LocalizedText` بعد إصلاح DTO.
- `ExerciseMessageLibraryMergerIdempotencyTest.kt` — استدعاء `applySyncMessageLibrary` مرتين لا يضاعف motivational/tips.
- `SyncCatalogGraphReportWiringTest.kt` — orchestrator (أو wrapper) يتصرّف عند `!report.isComplete`.
- `ProgramExportDeletedExerciseFlagTest` (باك) — `exportPlannedWorkout` يضبط `deletedExercise` لتمرين soft-deleted.
- `WorkoutExportMapperPhasesFirstTest.kt` — phases غير فارغة → flat list من phases فقط دون مضاعفة عند وجود exercises مكررة.
