# المحور B — صحة المزامنة (Delta/Full/Drift Correctness)
**المراجع:** independent review agent · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**  
`Data-Layer-Review-Brief.md` (§1, §2, §4-B, §5, §6) ·  
`kmp-app/core/data/.../sync/MovitSyncOrchestrator.kt` ·  
`kmp-app/core/data/.../cache/MovitSyncMetadataStore.kt` ·  
`kmp-app/core/data/.../cache/MovitCacheDriftDetector.kt` ·  
`kmp-app/core/data/.../cache/MovitCacheFreshnessDiagnostics.kt` ·  
`kmp-app/core/data/.../cache/MovitCacheFreshnessLog.kt` (+ android/ios expect) ·  
`kmp-app/core/data/.../repository/TrainingConfigEnsure.kt` ·  
`kmp-app/core/data/.../local/LegacyCatalogReadPolicy.kt` ·  
`kmp-app/core/data/.../repository/TrainingConfigRepository.kt` ·  
`kmp-app/core/data/.../repository/ExploreSyncRepository.kt` ·  
`kmp-app/core/data/.../repository/ExploreMerge.kt` ·  
`kmp-app/core/data/.../repository/SyncCatalogMapper.kt` ·  
`kmp-app/core/data/.../repository/SyncCatalogOfflineRepository.kt` ·  
`kmp-app/core/data/.../cache/MessageLibraryCache.kt` ·  
`kmp-app/core/data/.../repository/UserProgramEnrollmentLocalStore.kt` ·  
`kmp-app/core/data/.../repository/SharedExploreRepository.kt` ·  
`kmp-app/core/network/.../MovitMobileApi.kt` (`fetchSync` / `fetchExplore`) ·  
`backend/src/modules/mobile-sync/mobile-sync.service.ts` ·  
اختبارات: `MovitCacheDriftDetectorTest` · `MovitSyncOrchestratorTest` · `MovitSyncOrchestratorHydrationTest`

---

## 1. الحكم التنفيذي (≤ 10 أسطر)

**يحتاج إصلاحات محددة (S2)** — مسار الدلتا/الـ full/الـ drift يعمل كآلية تصحیح، لكن فيه ثغرات تصحّح نفسها بكلفة عالية (full متكرر) أو تترك الكاش ناقصًا مؤقتًا.

1. **H12 + H14 مؤكدتان:** عدّادات workouts/programs من explore blob؛ مسح المخازن عند full بلا transaction؛ انقطاع بين المسح والكتابة = كاش ناقص حتى دورة لاحقة (غالبًا عبر drift→full).
2. **H11 (جزء drift) + H21 مؤكدتان:** تعديل رسالة بلا تمرينها يغيّر fingerprint السيرفر → full لكل الأجهزة؛ fallback المحلي لـ `messageLibraryStats` بصيغة مختلفة تمامًا عن السيرفر.
3. **H13 + H23 مؤكدتان:** كل `Throwable` → `Offline`؛ طابعان منفصلان (`sync` العام vs `explore_last_sync`) ومسارَا كتابة لنفس `explore_data_json` دون توحيد watermark.

---

## 2. إجابات الأسئلة

### B1. دورة دلتا vs full — تحديث/تنظيف المخازن + انقطاع بين المسح والكتابة

**الإجابة:** الدلتا تدمج/تطبّق tombstones؛ الـ full يمسح ثم يعيد الكتابة في عدة مخازن **بدون** transaction تغطي الدورة. انقطاع في المنتصف يترك كاشًا ناقصًا؛ الطابع الزمني لا يتقدّم إلا بعد التطبيق (جيد لاسترداد لاحق عبر drift→full)، لكن الجهاز يبقى مكسورًا أوفلاين حتى نجاح sync لاحق.

**دورة الدلتا (`forceFullRefresh=false`):**

1. replay outbox → `GET /api/mobile/sync?updatedAfter=<last>`  
2. backfill إن كانت configs فارغة و`totalExercises>0` → ترقية لـ full  
3. drift على counts/fingerprint → ترقية لـ full  
4. تطبيق الحمولة: exercises (merge + deletes)، messages (mergePartial)، explore (merge)، catalog (merge + tombstones)، enrollments (merge)، إلخ  
5. كتابة `lastSyncTimestamp` من `response.timestamp` ثم home/reports + تحديث entity counts

**دورة full (`forceFullRefresh=true` أو `meta.isFullSync`):**

| المخزن | سلوك full | الدليل |
|---|---|---|
| exercise configs | مسح كل slugs ثم إعادة كتابة | `TrainingConfigRepository.applySyncExercises:72-78` |
| program/workout catalog | `clearProgramStore` / `clearWorkoutStore` ثم كتابة | `SyncCatalogOfflineRepository.applyFromSync:55-58` |
| explore blob | `mergeExploreData(..., isFullSync=true)` = استبدال كامل | `ExploreMerge.kt:10` |
| message library | `replaceFull` | `MovitSyncOrchestrator.kt:196-198` |
| audio manifest | `replaceFull` | `MovitSyncOrchestrator.kt:279-280` |
| enrollments | حذف الغائبة عن الحمولة + إعادة فهرس | `UserProgramEnrollmentLocalStore.hydrateFromSync:57-65` |

**سيناريو الانقطاع (مثبت بالكود):**

1. `isFullSync=true` → `applySyncExercises` يمسح كل configs (`:72-77`).  
2. قبل انتهاء كتابة التمارين/البرامج يُقتل التطبيق.  
3. `writeLastSyncTimestamp` لم يُستدعَ بعد (`MovitSyncOrchestrator.kt:211-213` بعد كتلة التطبيق) → الطابع القديم يبقى.  
4. الدورة التالية: دلتا بـ watermark قديم، لكن المخازن المحلية ناقصة → `hasNoEntityDelta` غالبًا true مع underflow → drift → full.  
5. حتى ذلك الحين: تدريب/تصفح أوفلاين على كاش ناقص.

```72:78:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
        if (isFullSync) {
            readSlugIndex().forEach { slug ->
                localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseConfigKey(slug))
            }
            writeSlugIndex(emptyList())
            writeSlugAliasMap(emptyMap())
        }
```

```55:58:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/SyncCatalogOfflineRepository.kt
        if (isFullSync) {
            clearProgramStore()
            clearWorkoutStore()
        }
```

**ملاحظة جانبية:** `systemMessages` تُحفظ فقط إن `isNotEmpty` — full بحمولة رسائل نظام فارغة لا يمسح الكاش القديم (`MovitSyncOrchestrator.kt:162-166`). التقارير عبر `hydrateFromSync` backfill-only بلا مسار full-clear (نطاق J أكثر من B).

**الحكم:** صحيح وظيفيًا مع فجوة متانة (لا atomic apply) — الاسترداد يعتمد على drift وليس على سلامة محلية فورية.

---

### B2. طابع sync العام vs طابع explore — تعارض؟ توحيد؟

**الإجابة:** نعم يمكن أن يتعارضا. المساران يكتبان نفس `explore_data_json` بطابعين منفصلين؛ مسار الـ orchestrator **لا يحدّث** `explore_last_sync` أصلًا.

| الطابع | المفتاح | من يحدّثه |
|---|---|---|
| عام | `SYNC_LAST_TIMESTAMP` / `sync_metadata.lastSyncAt` | `MovitSyncOrchestrator` بعد `/mobile/sync` |
| explore | `EXPLORE_LAST_SYNC` | فقط `ExploreSyncRepository.syncInternal` بعد `/mobile/explore` |

```206:213:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
            exploreData = exploreSync.applyFromSync(payload, isFullSync)
            catalogOffline.applyFromSync(payload, isFullSync)
        }

        metadataStore.writeFromSyncMeta(syncResponse.meta)
        if (syncResponse.timestamp.isNotBlank()) {
            metadataStore.writeLastSyncTimestamp(syncResponse.timestamp)
        }
```

`applyFromSync` يكتب البلوب ولا يلمس `EXPLORE_LAST_SYNC` (`ExploreSyncRepository.kt:50-75`). بينما:

```110:116:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/ExploreSyncRepository.kt
        if (response.timestamp.isNotBlank()) {
            store.writeJsonCache(
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_LAST_SYNC,
                response.timestamp,
            )
        }
```

**أثر التعارض:**

1. Sync عام يحدّث explore من `/mobile/sync` (ملخصات مشتقة عبر `SyncCatalogMapper` — بلا levels كاملة أحيانًا، مشتقة من workouts/programs).  
2. لاحقًا `SharedExploreRepository.refreshExploreContent` → `syncFull()` يمسح `explore_last_sync` وينزّل `/mobile/explore` كاملًا ويستبدل البلوب.  
3. أو العكس: explore أحدث من sync العام → عدّادات drift تُحدَّث من بلوب explore بينما configs من sync أقدم → mismatch مؤقت حتى دورة sync التالية.

**هل نحتاج توحيد؟** نعم عمليًا: إما (أ) اعتبار `/mobile/sync` المصدر الوحيد للكتالوج وإيقاف دلتا explore المستقلة، أو (ب) بعد `applyFromSync` كتابة نفس `response.timestamp` إلى `EXPLORE_LAST_SYNC`، أو (ج) watermark واحد مشترك للكتالوج.

**الحكم:** ازدواج حقيقي يضرب هدف «لا مطالبات ضخمة» ويُعقّد صحة الدلتا (H23).

---

### B3. حساسية الساعة — `updatedAt > updatedAfter` و`response.timestamp`

**الإجابة:** المقارنة على السيرفر **strict greater-than**؛ الطابع المُعاد هو `now` عند **بداية** `sync()` وليس max(`updatedAt`) للكيانات المُرجَعة. خطر فقدان تحديثات ضيق لكنه حقيقي عند تساوي الميلي ثانية / سباق كتابة أثناء الطلب الطويل.

```309:337:backend/src/modules/mobile-sync/mobile-sync.service.ts
  async sync(params: SyncRequestParams, baseUrl: string, userId?: string | null): Promise<MobileSyncResponse> {
    const prisma = await getPrisma();
    const now = new Date();
    const isFullSync: boolean = !params.updatedAfter || params.forceRefresh === true;
    // ...
    if (updatedAfterDate) {
      whereCondition.updatedAt = {
        gt: updatedAfterDate,
      };
    }
```

```600:603:backend/src/modules/mobile-sync/mobile-sync.service.ts
    const response: MobileSyncResponse = {
      success: true,
      timestamp: now.toISOString(),
```

**سيناريوهات:**

1. **سجل يُكتب أثناء معالجة sync الطويلة** بعد `findMany` وبـ `updatedAt` ≤ `now` (نفس ms): لن يدخل هذه الاستجابة ولن يدخل التالية لأن `gt(now)` يستبعده → **فقدان صامت حتى تعديل لاحق أو drift**.  
2. **Precision:** JS `toISOString()` بالميلي ثانية؛ Postgres قد يخزّن ميكروثانية — عند إعادة parse للـ `updatedAfter` قد يحدث تقريب.  
3. **ما هو آمن:** كيان دخل الاستجابة السابقة لن يُفقد من الكاش لمجرد أن `updatedAt == timestamp` (موجود محليًا)؛ الخطر على كتابات متزامنة مع نافذة الطلب.

**الحكم:** العقد الحالي مقبول للدلتا العادية؛ يُفضَّل `timestamp = max(updatedAt)` للكيانات المُرسلة أو `gte` مع id-tiebreak / cursor. ليس S1 مؤكدًا بدون إعادة إنتاج، لكنه ضعف عقدي S3→S2.

---

### B4. سيناريوهات drift

#### (1) explore فارغ + configs موجودة → حلقة full؟

**مؤكد.** `updateLocalEntityCounts` يأخذ exercises من configs وworkouts/programs من explore:

```294:301:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
    private fun updateLocalEntityCounts(explore: ExploreDataDto?) {
        metadataStore.writeEntityCounts(
            MovitCacheDriftDetector.EntityCounts(
                exercises = trainingConfig.allCachedSlugs().size,
                workouts = explore?.workoutTemplates?.size ?: 0,
                programs = explore?.programs?.size ?: 0,
            ),
        )
    }
```

`detectEntityDrift` يعمل فقط عند `hasNoEntityDelta` (استجابة بلا كيانات). إن كانت العدّادات المحلية workouts/programs = 0 والسيرفر `total*>0` → `NeedsFullRefresh` في **كل** دلتا فارغة.

```35:64:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/cache/MovitCacheDriftDetector.kt
    fun detectEntityDrift(
        local: EntityCounts,
        meta: SyncMetaDto?,
        hasNoEntityDelta: Boolean,
    ): DriftVerdict {
        if (!hasNoEntityDelta || meta == null) return DriftVerdict.Ok
        // ... underflow/overflow → NeedsFullRefresh
```

**هل حلقة كل دورة؟** بعد full ناجح يُفترض امتلاء explore من `applyFromSync` فتتوقف. الحلقة **المتكررة كل دورة** تحدث إذا بقي explore فارغًا رغم full (فشل mapping، استبدال لاحق ببلوب فارغ من مسار explore، أو انقطاع بعد مسح explore قبل الكتابة). في الحالة الشائعة (explore فارغ لمرة واحدة): **دورة full إضافية واحدة على الأقل** في كل مرة تُصفَّر فيها العدّادات — تكلفة S2 متكررة عند فساد البلوب.

#### (2) رسالة تعدّلت بدون تمرينها → fingerprint → full لكل الأجهزة؟

**مؤكد ومقبول كصحة، مكلف كحمولة.**

- السيرفر يبني `messageLibrary` من تمارين **الدفعة فقط** (`mobile-sync.service.ts:390-391`).  
- تعديل template لا يرفع `exercise.updatedAt` → الدلتا لا تحمل الرسالة.  
- `getGlobalMessageStats` يغيّر fingerprint عبر `linkedTplMax.updatedAt` (`:709-716`).  
- العميل: `detectMessageStatsDrift` → `MessageStatsMismatch` → `runSyncCycle(forceFullRefresh=true)`.

نعم: تعديل رسالة واحدة يجبر **كل** الأجهزة المتزامنة على full sync في دورتها التالية. مقبول لكشف stale messages؛ غير مقبول لهدف «لا مطالبات ضخمة» إن تكرّر من لوحة الأدمن.

#### (3) fallback `computeMessageLibraryStats` vs السيرفر

**مؤكد اختلاف الصيغة؛ الاستخدام نادر لأن السيرفر يرسل stats دائمًا.**

| الحقل | سيرفر `getGlobalMessageStats` | عميل `computeMessageLibraryStats` |
|---|---|---|
| fingerprint | `tplMax:sysMax:assignMax:assignments:msgCount:audioCount` | `id:code\|id:code...` مرتب |
| totalWithAudio | وجود `audioAr`/`audioEn` في content | نص `en` أو `ar` غير فارغ |
| totalAssignments | count عالمي من DB | مجموع assignments في الكاش المحلي |
| totalMessages | distinct messageIds عالميًا | حجم دفعة `messageLibrary` فقط |

```148:163:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
    fun computeMessageLibraryStats(
        messageLibrary: List<SyncMessageTemplateDto>,
        assignmentsInCachedExercises: Int,
    ): MessageLibraryStatsDto {
        val withAudio = messageLibrary.count { template ->
            val content = template.content
            content.en.isNotBlank() || content.ar.isNotBlank()
        }
        return MessageLibraryStatsDto(
            // ...
            fingerprint = messageLibrary
                .sortedBy { it.id }
                .joinToString("|") { "${it.id}:${it.code}" },
        )
    }
```

يُستدعى فقط عند غياب `meta.messageLibraryStats` ووجود `messageLibrary` (`MovitSyncOrchestrator.kt:214-221`). السيرفر الحالي يضع `messageLibraryStats` دائمًا (`mobile-sync.service.ts:626`). إن فُعّل الـ fallback (meta ناقص/قديم): الدورة التالية مع stats السيرفر → mismatch دائم → full في كل مرة حتى تُكتب stats السيرفر فوقها بعد full ناجح.

**الحكم:** (1) مؤكد كمحفّز full؛ (2) مؤكد ومكلف؛ (3) صيغتان مختلفتان — قنبلة إن اختفى الحقل من العقد.

---

### B5. recursion في `runSyncCycle` — هل أكثر من مرة؟ حلقة؟

**الإجابة:** الترقية إلى full تحدث **مرة واحدة كحد أقصى** لكل دخول من `syncIfNeeded`/`fullRefresh`. لا حلقة لانهائية من هذا المسار.

```141:148:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
        if (!forceFullRefresh && needsTrainingConfigBackfill(syncResponse.meta)) {
            return runSyncCycle(forceFullRefresh = true)
        }

        val drift = detectDrift(syncResponse, forceFullRefresh)
        if (!forceFullRefresh && drift != MovitCacheDriftDetector.DriftVerdict.Ok) {
            return runSyncCycle(forceFullRefresh = true)
        }
```

عند `forceFullRefresh=true`: شرطا backfill/drift لا يعيدان الاستدعاء (`!forceFullRefresh` يمنع). `detectDrift` نفسه يتجاهل mismatch عندما `isFullSync` (`:256-267`). اختبار موجود: `syncIfNeeded_sparseTrainingConfig_escalatesToFullRefresh` يتوقع flags `[false, true, false]` (الثالث من `planSync.refreshActiveUserProgramId` وليس recursion drift).

**الحكم:** آمن من الحلقة الذاتية. (ملاحظة: full متكرر **عبر دورات sync منفصلة** بسبب H12/H11 ممكن — ليس recursion داخل نفس الاستدعاء.)

---

### B6. ابتلاع الأخطاء — Offline vs أعطال حقيقية

**الإجابة:** لا تمييز. أي فشل شبكة/HTTP/serialization/منطق داخل `runSyncCycle` أو `Throwable` خارجي يُعرض كـ `Offline`.

```93:99:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
        return try {
            runSyncCycle(forceFullRefresh = false)
        } catch (_: Throwable) {
            SyncOutcome.Offline(readColdOfflineBundle())
        } finally {
            endSync()
        }
```

كذلك داخل الدورة: `getOrElse` على `fetchSync` و`!success` → Offline (`:133-138`). `CancellationException` يرث `Throwable` في Kotlin فيُبتلع هنا (عكس أنماط أخرى في المشروع تعيد رميه، مثل `CountdownController`).

`BackgroundSyncScheduler` يعامل `Offline` كـ `Failed` — بلا تصنيف سبب.

**حد أدنى مقترح للـ telemetry:**

1. عدم التقاط `CancellationException` (إعادة رمي).  
2. تصنيف: `Network` / `Http(status)` / `Decode` / `Logic` في `SyncOutcome.Error` أو حقل `cause` داخل Offline.  
3. سطر واحد عبر `MovitCacheFreshnessLog` / diagnostics: `sync_fail reason=... isFull=... updatedAfter=...`.  
4. عدّاد: `sync_success` / `sync_offline_true` / `sync_error_decode` / `sync_escalated_full`.

**الحكم:** H13 مؤكدة — يخفي أعطال العقد كـ«أوفلاين».

---

### B7. throttle والتزامن — `syncBusy` بلا mutex

**الإجابة:** العلم in-memory عادي بلا `Mutex`/`AtomicBoolean`/`volatile`. على منفّذ واحد تعاوني يمنع تداخلًا بعد `beginSync`؛ عبر خيوط (UI + WorkManager/BGTask) سباق check-then-act ممكن → دورتان متوازيتان.

```74:75:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
    private var syncBusy = false
    private var lastSyncAttemptMs = 0L
```

```309:317:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
    private fun beginSync(): Boolean {
        if (syncBusy) return false
        syncBusy = true
        return true
    }
```

**أثر على المخازن عند توازي:**

- دورتان full تمسحان/تكتبان نفس المفاتيح → فهرس ناقص أو بلوب نصف مكتوب.  
- دلتا + full متزامنان: full يمسح بينما دلتا تدمج → حالة غير معرّفة.  
- `lastSyncAttemptMs` قد يُحدَّث مرتين فيُفسِد throttle.

`TrainingConfigEnsure` يستدعي `syncIfNeeded` ثم `fullRefresh` تسلسليًا — إن كان syncBusy من shell، يُرجع `Skipped` ويظن أن المزامنة فشلت (`TrainingConfigEnsure.kt:61-65`).

**الحكم:** H14 (جزء التزامن) مؤكدة كضعف؛ احتمال التوازي حقيقي مع background scheduler (محور G).

---

## 3. نتائج الفرضيات المسندة (H..)

| فرضية | الحكم | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |
|---|---|---|---|---|
| **H11** (جزء drift) | **مؤكدة** | `getGlobalMessageStats` fingerprint من `updatedAt` القوالب؛ `detectMessageStatsDrift`؛ ترقية full في orchestrator. Write amplification عبر `applySyncMessageLibrary` موجود لكنه محور A. | 1) عدّل نص رسالة في الأدمن دون لمس التمرين. 2) جهاز يعمل دلتا فارغة. 3) meta.fingerprint يتغيّر. 4) `MessageStatsMismatch` → `forceFullRefresh`. | Full sync لكل الأجهزة النشطة؛ حمولة كبيرة متكررة عند تحرير الرسائل. |
| **H12** | **مؤكدة** | `updateLocalEntityCounts` يعتمد explore لـ workouts/programs؛ drift عند underflow مع دلتا فارغة. | 1) امسح/أفرغ `explore_data_json` مع بقاء configs. 2) شغّل sync دلتا بلا كيانات. 3) counts: exercises>0, workouts=0. 4) `NeedsFullRefresh`. | Full إضافي؛ إن بقي explore فارغًا بعد full → تكرار كل دورة دلتا فارغة. |
| **H13** | **مؤكدة** | `catch (_: Throwable) → Offline` في `syncIfNeeded`/`fullRefresh`؛ كذلك فشل fetch يُعامل Offline. | 1) أعد استجابة sync JSON مكسور. 2) راقب outcome. 3) يظهر Offline bundle لا Error. | أعطال serialization/منطق تُشخَّص خطأ كأوفلاين؛ صعوبة مراقبة. |
| **H14** | **مؤكدة** | `syncBusy` بلا تزامن؛ `applySyncExercises`/`applyFromSync` يمسحان قبل الكتابة؛ لا transaction. | 1) اقتل التطبيق أثناء full بعد المسح وقبل اكتمال الكتابة. 2) افتح أوفلاين → كتالوج ناقص. 3) أو شغّل sync من shell وWorkManager معًا على خيطين. | كاش ناقص مؤقت؛ احتمال فساد عند توازي؛ الاسترداد عبر drift عند الاتصال. |
| **H21** | **مؤكدة** (الصيغة) / **جزئية** (التفعيل) | اختلاف fingerprint و`totalWithAudio` مثبت؛ السيرفر يرسل stats دائمًا فيصبح الـ fallback نادرًا. | 1) استجابة sync بلا `messageLibraryStats` مع `messageLibrary` غير فارغ. 2) تُكتب stats محلية بصيغة `id:code`. 3) الدورة التالية مع stats السيرفر → mismatch → full. | إن اختفى الحقل من العقد: full متكرر. حاليًا خطر كامن أكثر من نشط. |
| **H23** | **مؤكدة** | `EXPLORE_LAST_SYNC` منفصل؛ `applyFromSync` لا يحدّثه؛ `syncInternal`/`syncFull` مسار مستقل لنفس البلوب. | 1) sync عام يحدّث explore. 2) pull-to-refresh Explore → `syncFull` يمسح watermark explore ويعيد تنزيل `/mobile/explore`. 3) الطابعان وطرق الدمج يختلفان. | مطالبات explore زائدة؛ احتمال استبدال ملخصات/levels؛ تعقيد drift counts. |

---

## 4. نتائج جديدة غير مذكورة في الـ Brief

| # | الخطورة | النتيجة | الدليل | سيناريو | الأثر |
|---|---|---|---|---|---|
| **B-N1** | S3 | `systemMessages` لا تُمسَح عند full بحمولة فارغة | `MovitSyncOrchestrator.kt:162-166` | حذف كل رسائل النظام على السيرفر → full → الكاش المحلي يحتفظ بالقديم | رسائل نظام شبح أوفلاين |
| **B-N2** | S2 | watermark الاستجابة = `now` عند بداية الطلب لا max(updatedAt) + فلتر `gt` | `mobile-sync.service.ts:310-337,603` | كتابة كيان أثناء sync الطويل بنفس ms | احتمال فقدان دلتا ضيق حتى تعديل لاحق/drift |
| **B-N3** | S3 | `TrainingConfigEnsure` قد يحصل على `Skipped` بسبب `syncBusy` فيظن الفشل | `TrainingConfigEnsure.kt:61-65` + `beginSync` | ensure أثناء sync جارٍ من الـ shell | مسار ensure يصعّد لـ full أو NotFound بلا داعٍ |
| **B-N4** | S3 | `LegacyCatalogReadPolicy` يمنع fallback للمنصة على `SYNC_STORE` وغيره — جيد للقطع؛ لا يصلح ثغرات B | `LegacyCatalogReadPolicy.kt:17-42` | — | لا يخفف H12/H14؛ يذكر للسياق فقط |
| **B-N5** | S3 | Diagnostics (`MovitCacheFreshnessDiagnostics`) تسجّل counts/lastSync ولا تسجّل سبب فشل sync أو escalation | `MovitCacheFreshnessDiagnostics.kt:37-57` | فشل decode يظهر كأوفلاين بلا أثر في التقرير | فجوة مراقبة مرتبطة بـ H13 |

---

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **B-R1** | S2 | حماية التزامن: `Mutex` (أو atomic flag) حول `beginSync`/`endSync` في `MovitSyncOrchestrator.kt`؛ طابور طلبات بدل توازي | S | Skipped إضافي تحت ضغط — مقبول |
| **B-R2** | S2 | Full apply أكثر أمانًا: اكتب إلى staging keys/فهارس مؤقتة ثم swap، أو لا تمسح حتى نجاح الكتابة؛ على الأقل لا تمسح explore/configs قبل اكتمال التحميل | M | تعقيد تخزين؛ يحتاج اختبارات انقطاع |
| **B-R3** | S2 | توحيد watermark الكتالوج: بعد `applyFromSync` اكتب `syncResponse.timestamp` إلى `EXPLORE_LAST_SYNC`؛ اجعل pull-to-refresh Explore دلتا لا `syncFull` (أو زر «إصلاح» فقط) — `ExploreSyncRepository` + `SharedExploreRepository` | M | تغيير سلوك Explore refresh (متوافق مع محور F) |
| **B-R4** | S2 | عدّادات drift: workouts/programs من `catalogOffline.allWorkoutTemplateIds()/allProgramIds()` لا من explore فقط — `updateLocalEntityCounts` | S | يجب ضمان تحديث الفهارس دائمًا مع explore |
| **B-R5** | S2 | رسائل: إمّا دلتا رسائل مستقلة (أو bump `exercise.updatedAt` عند تغيّر assignment/template)، أو قبول full مع rate-limit/إشعار أدمن — `mobile-sync.service.ts` + عقد الموبايل | L | تغيير عقد؛ توافق خلفي |
| **B-R6** | S3 | احذف أو وحّد `computeMessageLibraryStats` مع صيغة السيرفر؛ لا تكتب fallback إن اختلف العقد — `TrainingConfigRepository.kt` | S | اختبارات hydration التي تفترض fingerprint `id:code` |
| **B-R7** | S3 | صنّف أخطاء sync: أعد رمي `CancellationException`؛ `SyncOutcome.Error` للـ decode؛ log عبر freshness — `MovitSyncOrchestrator.kt` | S | قد يظهر Error في UI كان يُخفى كأوفلاين |
| **B-R8** | S3 | عقد الطابع: `timestamp = max(entity.updatedAt, now)` أو cursor `(updatedAt, id)` بدل `gt` فقط — `mobile-sync.service.ts` | M | يحتاج اختبارات تكامل DB |

**أهم 3 للتنفيذ الفوري:** B-R1، B-R4، B-R3 (ثم B-R2 للمتانة).

---

## 6. فجوات الاختبارات

**موجودة وراجعتُها:**

- `MovitCacheDriftDetectorTest` — overflow/underflow exercises + fingerprint mismatch (لا يغطي workouts/programs underflow من explore فارغ).  
- `MovitSyncOrchestratorTest` — offline bundle، prefetch، escalation backfill→full، ترتيب outbox.  
- `MovitSyncOrchestratorHydrationTest` — hydrate + كتابة message stats عند وجود meta.  
- `ExploreMergeTest` / `SyncCatalogMapperTest` — دمج explore.

**ناقصة (مقترحة):**

| ملف مقترح | ماذا يغطي |
|---|---|
| `MovitSyncOrchestratorDriftLoopTest.kt` | explore فارغ + totals>0 + دلتا فارغة → full مرة؛ بعد ملء explore لا يعيد |
| `MovitSyncOrchestratorCrashMidFullTest.kt` | محاكاة مسح configs ثم فشل قبل timestamp → الدورة التالية escalate |
| `MovitSyncOrchestratorConcurrencyTest.kt` | استدعاءان متوازيان لـ `syncIfNeeded` — أحدهما Skipped بعد Mutex (بعد الإصلاح) |
| `MovitSyncOrchestratorErrorClassificationTest.kt` | JSON مكسور → Error لا Offline؛ CancellationException يُعاد رميه |
| `ExploreSyncWatermarkParityTest.kt` | بعد `applyFromSync` من orchestrator، `EXPLORE_LAST_SYNC` يساوي timestamp العام (بعد B-R3) |
| `MessageLibraryStatsParityTest.kt` | عميل/سيرفر نفس صيغة fingerprint و`totalWithAudio` |
| `mobile-sync.watermark.spec.ts` (backend) | كيان `updatedAt == response.timestamp` لا يُفقد في الدلتا التالية |

---

*نهاية تقرير المحور B — لا تعديلات كود في هذه المراجعة.*
