# مراجعة شاملة لطبقة البيانات في تطبيق الموبايل (Offline-First) — Review Brief

**التاريخ:** 2026-07-09
**النطاق:** `kmp-app/core/data` + `kmp-app/core/network` + `kmp-app/core/training-engine/journal|report` + أطراف الـ features المتصلة بالبيانات + عقد الـ API في `backend/src/modules/mobile-sync` وما يتبعه.
**الهدف:** التأكد من أن التطبيق يحقق مبدأ Offline-First بشكل صحيح: قراءة فورية من الكاش، تحديث في الأوقات المناسبة بدون مطالبات ضخمة من الباك، تخزين بدون تضخم أو تكرار، وكتابة تدريبات مضمونة الوصول للباك حتى في أسوأ ظروف الشبكة.

هذا الملف مكتوب بعد **مراجعة أولية يدوية للكود** (قراءة فعلية للملفات المذكورة أدناه، وليس تخمينًا). كل فرضية في القسم 5 مذكور معها مصدرها `file:line` تقريبيًا. مهمة فريق المراجعة: **التحقق، النفي أو التأكيد، والتوسع** حسب محاور القسم 4، ثم إخراج تقارير بالصيغة المحددة في القسم 6.

---

## 1. خريطة المعمارية كما هي مبنية فعليًا (As-Built)

### 1.1 التخزين المحلي — SQLDelight بأربع جداول فقط

المخطط في `kmp-app/core/data/src/commonMain/sqldelight/com/movit/core/data/db/`:

| الجدول | الملف | الدور |
|---|---|---|
| `json_cache_entry (store, cache_key, json_payload, updated_at_epoch_ms)` | `JsonCacheEntry.sq` | **كل** كيانات القراءة مخزنة كـ JSON blobs داخل namespaces منطقية — لا يوجد schema علائقي للكيانات |
| `outbox_entry (id, operation_type, payload_json, idempotency_key, attempts, status, last_error)` | `Outbox.sq` | طابور الكتابة الأوفلاين |
| `session_journal_entry (session_id, exercise_id, payload_json, status)` | `SessionJournal.sq` | checkpoints جلسة التدريب الحية |
| `sync_metadata (scope, version, last_sync_at)` | `SyncMetadata.sq` | طوابع المزامنة |

- الواجهة: `MovitLocalStore` في `core/data/local/MovitLocalStore.kt` (synchronous للـ JSON cache، suspend للـ outbox).
- التنفيذ: `SqlDelightMovitLocalStore.kt`، مع `MigratingMovitLocalStore.kt` (هجرة one-time من legacy Android prefs) و`CanonicalCacheKeyMigrator.kt` (إعادة كتابة مفاتيح legacy).
- **كل أسماء الـ namespaces والمفاتيح** في `core/data/repository/MovitCacheKeys.kt` — هذا الملف هو "فهرس" التخزين كله وأول ما يجب أن يقرأه أي مراجع.

### 1.2 المزامنة (القراءة من الباك)

- **المنسق المركزي:** `core/data/sync/MovitSyncOrchestrator.kt`
  - `syncIfNeeded(forceCheck)` مع throttle افتراضي 5 دقائق (in-memory فقط: `lastSyncAttemptMs`, `syncBusy`).
  - `fullRefresh()` يتجاهل الـ throttle.
  - الدورة: replay outbox → `GET /api/mobile/sync?updatedAfter=...` → drift detection (قد تعيد الدورة `forceFullRefresh=true`) → تطبيق الحمولة على المخازن → تحديث `lastSyncTimestamp` → `homeSync.sync()` → `reportsSync.syncDashboard()` (للـ Pro فقط) → replay outbox مرة ثانية.
  - أي `Throwable` أثناء الدورة → `SyncOutcome.Offline(readColdOfflineBundle())` (ابتلاع صامت).
- **الدلتا:** `updatedAfter` timestamp واحد عام (scope `sync_manager_prefs`)، + tombstones (`deletedExerciseIds/deletedWorkoutTemplateIds/deletedProgramIds` تشمل المحذوف والـ unpublished).
- **كشف الانحراف:** `MovitCacheDriftDetector.kt` — مقارنة عدّادات محلية (exercises/workouts/programs) بـ `meta.total*` + مقارنة `messageLibraryStats` (counts + fingerprint). أي انحراف → إعادة الدورة كاملة full.
- **مخازن مشتقة من نفس الحمولة:**
  - `TrainingConfigRepository.kt` → exercise configs كاملة بمفتاح slug + فهرس slugs + خريطة aliases (id→slug) في `exercise_config_cache`.
  - `SyncCatalogOfflineRepository.kt` → full `ProgramExportDto` لكل برنامج (`program_cache`) + full `WorkoutExportDto` لكل template (`workout_template_cache`) + **نسخة محوّلة** `WorkoutTemplateTrainingConfigDto` لكل template في `session_cache`، مع فهارس ids في `catalog_index_cache`، ثم `SyncCatalogGraphValidator` للتحقق من الجراف.
  - `ExploreSyncRepository.kt` → blob واحد `explore_data_json` (ملخصات) عبر `SyncCatalogMapper` + merge في `ExploreMerge.kt`. له أيضًا مسار مزامنة **مستقل** عبر `GET /api/mobile/explore` بطابع دلتا **منفصل** (`explore_last_sync`).
  - `MessageLibraryCache.kt` (blob واحد لكل الرسائل) + `SystemMessageCache.kt` + `AudioManifestCache.kt` (replaceFull/mergePartial) + `AudioPrefetchRunner` (تنزيل ملفات صوت فعلية).
  - `UserProgramEnrollmentLocalStore.kt` (enrollments) + `DayCustomizationLocalStore.kt` (تخصيصات الأيام) + `ExercisePreferenceLocalStore.kt` (تفضيلات التمارين مع حماية pending outbox) + `ReportsSyncRepository.hydrateFromSync` (تقارير planned workouts — backfill فقط).
- **قراءات per-screen خارج `/mobile/sync`:** `HomeSyncRepository` (`/mobile/home` بدون دلتا + `HomeTrainModeHydrator`)، `ReportsSyncRepository` (`/mobile/reports/dashboard` + `/mobile/reports/metrics` بمفاتيح كاش لكل تركيبة query)، `WorkoutSessionSyncRepository` (`/mobile/user-programs/:id/effective-plan` لكل يوم + training-config لكل template)، `ProgramFlowSyncRepository` (`/mobile/programs/:id`)، `PlanSyncRepository` (enroll + resolve active userProgramId).
- **نمط القراءة للشاشات:** `StaleWhileRevalidate.kt` (`CacheState: Loading/Cached/Fresh/Error`) — كل الشاشات تقرأ الكاش فورًا ثم تحدّث في الخلفية. الـ shell يرفع `dataRevision` بعد كل Sync ناجح لإعادة تحميل الشاشات.
- **البذرة الباردة:** `ColdOfflineBundleSeeder.kt` — أول تشغيل بدون نت يزرع كتالوج + configs + رسائل من JSON مضمّن في التطبيق (`scripts/README-cold-offline-bundle.md`).

### 1.3 الكتابة (الموبايل → الباك)

- **الطابور:** `core/data/outbox/OfflineWriteQueue.kt` — enqueue بمعرّف عملية ثابت (`op-<epoch>-<rand>` أو id خارجي) → `OfflineWriteOptimisticCache.apply()` (تحديث متفائل للكاش لأنواع محددة) → محاولة replay فورية لو أونلاين.
- **الأنواع (11):** في `OutboxModels.kt`: planned start/complete/report، plan complete، exercise preference upsert/delete، program override create/delete، day customizations، progression mark-seen، **workout execution upload** (رفع متريكس تمرين واحد — الـ id بتاع الرفع نفسه هو مفتاح الـ idempotency).
- **الترتيب:** `OutboxReplayOrdering.kt` — executions أولًا (priority 0) ثم start(10) ... complete(80)/report(85)/plan complete(90)، مرتبة داخليًا بـ createdAt.
- **سياسة التعارض:** `OutboxConflictPolicy.kt` — 409 = server-wins (يُعلَّم SUCCEEDED ويُسقط)، 4xx = FAILED_PERMANENT فورًا، 5xx/شبكة = retry حتى `MAX_ATTEMPTS=3` ثم FAILED_PERMANENT. **لا يوجد Idempotency-Key header على مستوى السيرفر** (موثّق داخل الملف نفسه).
- **الالتقاط:** `OutboxDispatcher.kt` يستخرج HTTP status **بـ regex من نص رسالة الخطأ** `"(\d{3})"`.
- **الاستئناف:** `recoverInFlightOutbox()` عند بدء replay، و`OutboxMaintenance` (حذف SUCCEEDED أقدم من 7 أيام). محفزات replay: enqueue، دورة الـ sync (مرتين)، استعادة الاتصال (`OutboxConnectivityReplay.android/ios`).
- **بوابة legacy:** `LegacyWorkoutSyncGate` + `LegacyAnalyticsPendingCleaner` — drain لملفات pre-KMP قبل أي enqueue.

### 1.4 مسار جلسة التدريب (الأهم تجاريًا)

```
TrainingSessionViewModel (feature/training)
  → TrainingSessionWriteHooks.attach(engine, config)        ← يبني TrainingMotionSession
  → MovitTrainingEngine callbacks → MotionRecorder.record() ← لكل frame
  → عند اكتمال العدة: recorder.finalizeRep() + checkpoint() ← snapshot للجورنال
  → checkpointJournal → SessionJournalStore.saveCheckpoint  ← يكتب في جدول SQL + json_cache معًا
  → عند نهاية التمرين: finalizeUpload() → WorkoutUpload
  → TrainingSessionWriteCoordinator.uploadWorkoutExecution:
      - WorkoutUploadMapper.toUploadRequest (قسمة المقاييس /10 لتحويلها من 0-1000 إلى 0-100)
      - reportsSync.patchExerciseMetricsFromUpload (تحديث متفائل لمتريكس التمرين)
      - mobileWrites.uploadWorkoutExecution → outbox (operationId = upload.id)
  → التقارير الغنية: MovitPostTrainingReport تُخزن محليًا في PostTrainingReportLocalStore
      (لكل set، مع فهرس reportsBySet + rekey من upload id إلى server id)
      وتُرفع كـ legacyReport JSON داخل نفس الـ execution upload
  → إنهاء اليوم المخطط: completePlannedDay → recordPendingPlannedWorkoutCompletion (تقرير محلي متفائل
      + patchDashboardFromCompletion) → outbox PLANNED_WORKOUT_COMPLETE
  → finalizeJournal(sessionId) → حذف checkpoint
```

- **الضيف (guest):** `uploadWorkoutExecution` هو **الوحيد** الذي يدخل outbox بدون auth؛ بقية الكتابات ترفض بدون تسجيل دخول. الرفع يتم بعد تسجيل الدخول عبر replay.
- **الباك:** `POST /mobile/workout-executions` → `saveWorkoutExecution` يعمل **upsert بالـ client id** + حذف/إعادة إنشاء المقاييس داخل transaction (idempotent فعليًا). `workout-executions.service.ts:58-95`. الحقول: WorkoutExecution + WorkoutExecutionMetrics (1:1) + RepMetrics (1:N) + `legacyReport` JSON.
- **لقطات الفريمات:** `MovitPeakFrameCapture.localPath/thumbnailPath` و`MovitRepReplayClip.frames[].frameUri` = مسارات ملفات محلية على الجهاز تدخل في تقرير `MovitPostTrainingReport` المخزن.

### 1.5 عقد البيانات مع الباك (الملفات المرجعية)

- **SSOT للمسارات:** `Docs/00-Active-Reference/Contracts/API_ENDPOINTS.md` (249 مسار). قسم الموبايل: sync/explore/home + plan/programs/user-programs + workout-executions + planned-workouts + reports + progression + preferences + auth.
- **حمولة `/api/mobile/sync`:** `backend/src/modules/mobile-sync/mobile-sync.service.ts` → `{ exercises[] (config كامل + معرفات), messageLibrary[] (مكررات محذوفة، نصوص فقط), systemMessages[], workoutTemplates[] (export كامل بمراحل وتمارين slug-based), programs[] (export كامل: weeks→days→plannedWorkouts→items), userPrograms[], userExercisePreferences[], plannedWorkoutReports[] (**كل** المكتملة، بلا فلتر زمني), audioManifest, deleted*Ids[], meta { totals, isFullSync, messageLibraryStats+fingerprint } }`.
  - **aliases قديمة مدعومة:** `workouts`/`workoutTemplates`، `deletedWorkoutIds`/`deletedWorkoutTemplateIds` (الموبايل يقرأ الاثنين عبر `@JsonNames`).
  - الدلتا: `updatedAt > updatedAfter` لكل كيان published؛ الرسائل تُبنى **فقط من تمارين الدفعة**، والـ stats العالمية تعوّض عن ذلك بكشف drift.
- **DTOs الموبايل:** `core/network/dto/PlanSyncDto.kt` (الحمولة الكاملة)، `ProgramExportDto.kt`، `WorkoutExportDto.kt`، `TrainingApiDto.kt` (الرفع)، `ReportsDto.kt`، `HomeDto.kt`، `ExploreDto.kt`.
- **اختبارات العقد موجودة:** `core/network/contract/*` (BackendContractParityTest, DtoPayloadContractTest, BackendMobileContractCoverageTest...) — يجب على المراجعين تشغيلها والاستناد إليها.
- **نموذج العلاقات:** exercise (slug canonical) ←N:M عبر messageAssignments← messages (منزوعة التكرار في مكتبة واحدة)؛ workoutTemplate → phases → exercises (بالـ slug + sets/reps/rest)؛ program → weeks → days → plannedWorkouts (workoutTemplateId + items بالـ slug)؛ userProgram (enrollment + customizations JSON) → overrides؛ plannedWorkoutReport (تقرير اليوم) وworkoutExecution (تقرير التمرين الواحد).

### 1.6 محفزات التحديث (متى تحدث المزامنة فعليًا)

| الحدث | المسار | النوع |
|---|---|---|
| فتح التطبيق بجلسة نشطة | `MovitAppShellViewModel.init` → `bootstrapLocalCaches()` + `requestSyncIfNeeded()` | throttled |
| عودة التطبيق للمقدمة | `onAppResumed()` | throttled |
| استعادة الاتصال | `MovitConnectivitySignals` → `requestSync(forceCheck=true)` + `OutboxConnectivityReplay` | فوري |
| تسجيل دخول / onboarding مكتمل | `handleAuthEffect/handleOnboardingEffect` → `requestSyncIfNeeded(forceCheck=true)` | فوري |
| Pull-to-refresh (مثال Home) | يستدعي sync الخاص بالشاشة (وexplore يستدعي `syncFull()`) | فوري |
| مزامنة خلفية دورية | `BackgroundSyncScheduler` (WorkManager / BGAppRefreshTask) → `runBackgroundSyncIfReady` → `syncIfNeeded(forceCheck=true)` | فوري |
| «حزمة الأسبوع» | `WeekOfflinePackPrefetcher.prefetchWeek` → sync + effective plans لكل يوم + صوتيات | فوري |
| انتهاء الجلسة (401 وفشل refresh) | `MovitData.notifySessionExpired` → **`clearAllUserData()`** → شاشة auth | مدمّر |
| تسجيل الخروج | `AccountSyncRepository.clearLocalSession` → `clearAllUserData()` | مدمّر |

---

## 2. أهداف المنتج التي تُقاس عليها المراجعة

1. **Offline كامل:** المستخدم داخل جيم بدون إنترنت يقدر يفتح التطبيق، يشوف برنامجه ويومه، يتدرب بالكاميرا، ويتسجل كل شيء محليًا ويصل للباك لاحقًا **بدون فقدان ولا تكرار**.
2. **لا مطالبات ضخمة:** لا full-download إلا عند الضرورة (تثبيت جديد، drift حقيقي، ريفريش يدوي مقصود). الدلتا هي الوضع الطبيعي.
3. **تخزين رشيق:** لا تخزين نفس البيانات مرتين بلا مبرر، ولا نمو غير محدود بلا تنظيف.
4. **صحة العلاقات:** التمرين هو الكيان الأساس؛ الرسائل/الأصوات/القوالب/البرامج/التقارير كلها يجب أن تظل متسقة معه بعد أي دلتا أو حذف أو إعادة تسمية slug.
5. **تحديث في التوقيت الصحيح:** فتح التطبيق، عودة من الخلفية، تسجيل دخول، استعادة اتصال، ريفريش يدوي — بدون عواصف طلبات ولا سباقات.

---

## 3. كيف تُدار المراجعة (تعليمات المنسّق)

- كل محور في القسم 4 = **وكيل مراجعة مستقل** يعمل بعمق على ملفاته المذكورة.
- كل وكيل: يقرأ هذا الملف أولًا، ثم كودَ محوره **قراءة كاملة** (ليس عينات)، ويتحقق من فرضيات القسم 5 المُسندة لمحوره، ويجيب على أسئلة محوره سؤالًا سؤالًا.
- **قاعدة الإثبات:** أي نتيجة يجب أن تُسند بـ `file:line` ومقتطف كود، وسيناريو خطوة-بخطوة يوضح متى تحدث المشكلة. الفرضية التي لا تثبت تُسجَّل "منفية" مع سبب النفي — النفي الموثق قيمته مساوية للتأكيد.
- **الاختبارات:** لكل محور اختبارات موجودة في `core/data/src/commonTest` و`core/network/.../contract` — راجعها واذكر فجوات التغطية. يمكن تشغيلها بـ `./gradlew :core:data:testDebugUnitTest` (أو `testAndroidHostTest` حسب الإعداد) من داخل `kmp-app/`.
- ممنوع اقتراح إعادة كتابة معمارية شاملة؛ المطلوب إصلاحات محددة قابلة للتنفيذ على البنية الحالية (JSON-blob store + outbox)، إلا إذا ثبت أن البنية نفسها تكسر هدفًا من أهداف القسم 2 — عندها يُكتب ذلك في "قرارات معمارية مقترحة" منفصلة بمبرراتها وتكلفتها.

---

## 4. محاور المراجعة

### المحور A — هيكل التخزين والحجم (Storage & Size Audit)

**الملفات:** `MovitCacheKeys.kt`، `JsonCacheEntry.sq`، `SqlDelightMovitLocalStore.kt`، `TrainingConfigRepository.kt`، `SyncCatalogOfflineRepository.kt`، `ExploreSyncRepository.kt` + `ExploreMerge.kt`، `MessageLibraryCache.kt`، `SystemMessageCache.kt`، `AudioManifestCache.kt` + `AudioPrefetchRunner.kt` + `AudioClipResolver.kt`، `PostTrainingReportLocalStore.kt`، `SessionJournalStore.kt`، `ReportsSyncRepository.kt`.

**الأسئلة:**
- A1. ارسم جدولًا كاملًا بكل الـ namespaces/المفاتيح وما يُخزَّن فيها وحجمه التقديري لمستخدم نشط بعد 6 أشهر (كتالوج ~200 تمرين، ~50 template، ~10 برامج، تدريب 4 أيام/أسبوع). أين النمو غير المحدود؟
- A2. أين تُخزَّن **نفس المعلومة أكثر من مرة**؟ قيّم كل ازدواج: مقصود (index/summary مقابل full) أم إهدار؟ بالأخص: نصوص الرسائل (مكتبة + مدموجة داخل كل exercise config)، الـ workout export + نسخته المحولة training-config، الجورنال (SQL + json_cache)، البرنامج تحت مفتاح id ومفتاح slug.
- A3. لا يوجد أي GC للـ `json_cache_entry` (الـ outbox فقط له retention). ما خطة التنظيف المقترحة لكل namespace (تقارير قديمة، effective plans، metrics بمفاتيح query متراكمة، post-training reports لكل set، لقطات فريمات على الـ filesystem)؟
- A4. write amplification: أي عمليات تعيد كتابة blobs كبيرة كاملة عند تغيّر صغير (explore blob، إعادة كتابة كل exercise configs عند دلتا رسائل)؟ قِس التكلفة واقترح حلًا.
- A5. الـ blobs single-key (explore_data_json, message_library_json) — هل تسبب مشاكل قراءة/كتابة على main thread أو parsing متكرر؟ راجع الـ threading الفعلي (`MovitLocalStore` synchronous) ومواضع الاستدعاء من الـ UI.
- A6. ملفات الصوت واللقطات: أين تُخزن فعليًا، حجمها، وسياسة حذفها عند حذف التمرين/التقرير/الـ logout؟

### المحور B — صحة المزامنة (Delta/Full/Drift Correctness)

**الملفات:** `MovitSyncOrchestrator.kt`، `MovitSyncMetadataStore.kt`، `MovitCacheDriftDetector.kt`، `MovitCacheFreshnessDiagnostics/Log.kt`، `backend/src/modules/mobile-sync/mobile-sync.service.ts`، `TrainingConfigEnsure.kt`، `LegacyCatalogReadPolicy.kt`.

**الأسئلة:**
- B1. تتبّع دورة sync كاملة (دلتا) ودورة full: هل كل مخزن يُحدَّث ويُنظَّف صح في الحالتين؟ ركّز على أن `isFullSync` client-side يعني مسح المخازن قبل الكتابة — ماذا يحدث لو انقطع التطبيق **بين المسح والكتابة** (لا transaction تغطي الدورة)؟
- B2. الدلتا تستخدم **طابعًا زمنيًا واحدًا** لكل الكيانات + طابعًا منفصلًا للـ explore endpoint. هل يمكن أن يتعارضا (explore أحدث من العام أو العكس) وما الأثر؟ هل نحتاج توحيدهما؟
- B3. حساسية الساعة: الطابع من `response.timestamp` (ساعة السيرفر) — تحقق أن مقارنات `updatedAt > updatedAfter` في الباك لا تفقد تحديثات (سجل يُكتب في نفس اللحظة، فروق precision، معاملات طويلة).
- B4. سيناريوهات drift: (1) explore blob فاضي وexercise configs موجودة → هل تدخل حلقة full-refresh كل دورة؟ (`updateLocalEntityCounts` يعتمد على explore blob للأعداد). (2) رسالة تعدلت بدون تعديل تمرينها → fingerprint mismatch → full — هل مقبول أن تعديل رسالة واحدة يجبر **كل** الأجهزة على full sync؟ (3) fingerprint من السيرفر مقابل fallback المحسوب محليًا في `TrainingConfigRepository.computeMessageLibraryStats` بصيغة مختلفة تمامًا — متى يُستخدم الfallback وهل يسبب mismatch دائمًا؟
- B5. `runSyncCycle` recursion (backfill + drift → استدعاء ذاتي full) — أثبت أنه لا يمكن أن يحدث أكثر من مرة واحدة ولا يوجد سيناريو حلقة.
- B6. ابتلاع الأخطاء: `catch (_: Throwable) → Offline` — كيف نفرّق بين أوفلاين حقيقي وخطأ serialization/منطق؟ اقترح حدًا أدنى من الـ telemetry.
- B7. الـ throttle والتزامن: `syncBusy`/`lastSyncAttemptMs` بدون mutex/atomic — هل يمكن دورتا sync متوازيتان (shell + background worker + prefetcher)؟ ما الأثر على المخازن؟

### المحور C — سلامة العلاقات بين الكيانات (Catalog Graph Integrity)

**الملفات:** `SyncCatalogGraphValidator.kt`، `SyncCatalogMapper.kt`، `ExerciseIdResolver.kt`، `ExerciseMessageLibraryMerger.kt`، `WorkoutExportMapper.kt`، `TrainingConfigRepository.kt` (aliases)، `ProgramExportDto/WorkoutExportDto`، والباك `workout-templates.service.ts buildWorkoutExport` + `programs.service.ts getPublishedForMobile` + `exercises/json-builder.ts`.

**الأسئلة:**
- C1. المعرّفات: التمرين يُعنون بالـ slug محليًا، والقوالب/البرامج تشير له بالـ slug، والسيرفر يقبل id أو slug في الرفع. ماذا يحدث عند **تغيير slug لتمرين منشور**؟ تتبّع: alias map، القوالب المخزنة القديمة، التفضيلات (`pref_<id>`)، التقارير القديمة، والرفع المعلق في outbox.
- C2. حذف/unpublish تمرين مستخدم داخل template أو program items (`deletedExercise` flag): هل كل مسارات العرض والتدريب تتعامل مع العنصر الناقص بأمان (بما فيها بدء جلسة لتمرين محذوف من كاش قديم)؟ ماذا يفعل `SyncCatalogGraphValidator` بالضبط بالنتيجة — هل تقاريره تُستخدم أم مجرد log؟
- C3. الرسائل: تتبّع دورة حياة رسالة (نص + صوت) من `messageLibrary` إلى `messageAssignments` داخل الـ config إلى `AudioManifest` إلى الملف الصوتي المحمّل. عدّل رسالة → هل يتحدث النص والصوت معًا في كل الأماكن؟ احذف assignment → هل تُنظف النسخة المدموجة داخل config التمرين؟
- C4. الـ dedup الفعلي: هل دمج نصوص الرسائل داخل كل config (`mergeRecordForPersist` + `applySyncMessageLibrary`) يلغي فائدة المكتبة المنزوعة التكرار؟ اقترح بنية قراءة تحافظ على parity بدون تكرار التخزين.
- C5. workoutTemplate → planned workout items: نفس التمرين قد يظهر في `phases[].exercises` و`exercises[]` (legacy). هل الموبايل يقرأ مصدرًا واحدًا دائمًا؟ هل يوجد ازدواج عند البناء في `WorkoutExportMapper.toTrainingConfig`؟

### المحور D — موثوقية الكتابة والـ Outbox (Write-Path Reliability)

**الملفات:** `OfflineWriteQueue.kt`، `OutboxDispatcher.kt`، `OutboxConflictPolicy.kt`، `OutboxReplayOrdering.kt`، `OutboxMaintenance.kt`، `OfflineWriteOptimisticCache.kt`، `MobileWriteSyncRepository.kt`، `OutboxConnectivityReplay.android/ios.kt`، `LegacyWorkoutSyncGate.kt`، والباك: `mobile-planned-workouts.controller.ts`، `workout-executions.service.ts`، `active-plan`, `user-exercise-preferences`.

**الأسئلة:**
- D1. **التسليم المزدوج:** `replayPending` لا يعلّم الصف `IN_FLIGHT` قبل الإرسال (يقرأ PENDING → dispatch → SUCCEEDED)، ويُستدعى من 5 محفزات بدون قفل مشترك. أثبت أو انفِ إمكانية إرسال نفس العملية مرتين متوازيتين، وحدد أي endpoints على الباك **ليست idempotent** (planned complete/report/start؟ overrides؟) وما الأثر (تقارير مكررة؟ عدّادات مضاعفة؟).
- D2. **الفقد الصامت:** 3 محاولات فاشلة (5xx/شبكة متقلبة) → `FAILED_PERMANENT` بلا أي إشعار للمستخدم ولا آلية إعادة إحياء. ما السيناريو الأسوأ (تدريب أسبوع كامل أوفلاين)؟ اقترح سياسة retry أفضل (backoff، لا سقف للمحاولات على أخطاء الشبكة، وسيلة عرض "بيانات معلقة" للمستخدم).
- D3. استخراج الـ status بـ regex من نص الخطأ (`parseHttpStatusFromError`) — راجع كل رسائل الأخطاء في `MovitMobileApi` وتأكد أن كل مسار يمرر `(status)`، وماذا يحدث لأخطاء Ktor بدون status (timeout, DNS).
- D4. الترتيب: هل ترتيب `OutboxReplayOrdering` كافٍ عند وجود جلستين ليومين مختلفين معلقتين؟ فشل عملية متقدمة (مثلاً execution 4xx) — هل يجب أن يمنع إرسال `PLANNED_WORKOUT_COMPLETE` التابع لنفس الجلسة أم لا؟ (حاليًا لا توجد تبعية بين الصفوف.)
- D5. التحديث المتفائل: `OfflineWriteOptimisticCache` يغطي 3 أنواع فقط، بينما `MobileWriteSyncRepository` يعمل تحديثات متفائلة إضافية يدويًا (home trainMode, dashboard patch). وحّد الخريطة: لكل نوع عملية — ما التحديث المتفائل، وماذا يحدث عند server-wins/permanent-failure (لا يوجد rollback حاليًا)؟
- D6. `enqueue` يعيد استخدام صف FAILED_PERMANENT بنفس الـ id بالكتابة فوقه (INSERT OR REPLACE) — هل هذا مقصود؟ وهل عودة `existing.status == SUCCEEDED` بدون إعادة إرسال صحيحة لكل الأنواع (إعادة إكمال نفس اليوم بعد أسبوع)؟

### المحور E — متانة جلسة التدريب (Training Session Durability)

**الملفات:** `TrainingSessionWriteCoordinator.kt`، `TrainingSessionWriteHooks.kt`، `TrainingMotionSession.kt`، `MotionRecorder.kt`، `MetricsCalculator.kt`، `SessionJournalStore.kt`، `WorkoutUploadMapper.kt`، `PostTrainingReportLocalStore.kt`، `WorkoutExecutionBatchCoordinator.kt`، `TrainingSessionPlannedWritePolicy.kt`، `TrainingSessionLifecyclePolicy.kt`، وملف `Training-Metrics-Audit.md` الموجود بالفعل في الجذر (تدقيق سابق لمحرك المتريكس — لا تكرر نطاقه، ابنِ عليه).

**الأسئلة:**
- E1. crash/kill في منتصف الجلسة: checkpoint يحدث **عند اكتمال كل عدة**. ماذا يُفقد بالضبط (العدة الجارية فقط؟) وهل الاستعادة (`readJournal` → `restore`) تُفعَّل فعلًا في الـ ViewModel عند إعادة فتح الجلسة؟ من يستدعي `listActiveCheckpoints` لاستئناف/تنظيف جلسات يتيمة، ومتى تُعلَّم `abandoned`؟
- E2. `markCompleted` يكتب checkpoint بحالة completed ثم يحذفه فورًا (كتابة مهدرة + لا أثر بعد الإكمال). هل الحذف الفوري صحيح، أم نحتاج أثرًا حتى نجاح الرفع (ربط journal ↔ outbox)؟
- E3. تعدد الـ sets وbilateral: تتبع أن `WorkoutUpload` واحد لكل (تمرين×جلسة؟ أم لكل set؟) وأن `reportsBySet` + `rekeyPostTraining` (من upload id إلى server id) لا يفقد تقارير عند رفع متأخر. من يستدعي `rekeyPostTraining` ومتى؟
- E4. الرفع المؤجل للـ batch (`WorkoutExecutionBatchCoordinator.pending` **في الذاكرة فقط**): kill قبل `flush` → تضيع الحزمة؟ قارن بمسار planned يوم كامل.
- E5. القسمة على 10 والـ scaling في `WorkoutUploadMapper` — طابق كل حقل مع schema الباك (`RepMetrics`, `WorkoutExecutionMetrics`) والـ admin dashboard: هل من حقل يُرسل بمقياس خاطئ أو null يتحول 0؟
- E6. `PLANNED_WORKOUT_COMPLETE` مقابل `PLANNED_WORKOUT_REPORT` (legacy alias): `TrainingSessionPlannedWritePolicy` يقول لا تستخدم الاثنين لنفس الجلسة — تحقق أن كل نقاط الاستدعاء تحترم ذلك وأن الباك يعامل الاثنين بنفس المنطق.

### المحور F — مسار القراءة داخل التطبيق (Read Path & UI Freshness)

**الملفات:** `StaleWhileRevalidate.kt`، `SharedExploreRepository.kt` + `InMemoryExploreRepository.kt` + `ExploreApiMapper.kt`، `HomeTrainModeHydrator.kt`، قراءات features: `feature/home`, `feature/train`, `feature/library` (ProgramDetail/WorkoutSession/ExercisePrepare viewmodels)، `feature/reports`، و`MovitAppShellViewModel.dataRevision`.

**الأسئلة:**
- F1. لكل شاشة رئيسية (Home, Train, Explore, Program Detail, Workout Session, Reports, Report Detail): من أين تقرأ؟ (blob مباشر؟ mapper؟) ومتى تعيد القراءة (dataRevision؟ SWR خاص؟)؟ اصنع مصفوفة شاشة×مصدر×محفز-تحديث واكشف الشاشات التي **لا** تتحدث بعد sync ناجح أو بعد كتابة متفائلة.
- F2. **`SharedExploreRepository.refreshExploreContent` يستدعي `syncFull()`** (يمسح طابع الدلتا وينزّل الكتالوج كاملًا) — وكذلك `ProgramFlowSyncRepository.syncExplore`. هل كل pull-to-refresh في Explore = full download؟ هذا يضرب هدف "لا مطالبات كبيرة" — اقترح متى يكون full مبررًا (زر "إصلاح" مثلاً) ومتى دلتا.
- F3. القراءات المتزامنة الثقيلة: `readCached()` يفكّ blob كاملًا في كل استدعاء (explore, home). أين تحدث على الـ main thread؟ وهل `ExploreSyncRepository.exerciseImageUrl` (تفكيك كامل البلوب لصورة واحدة) يُستدعى في loops؟
- F4. `dataRevision` يزيد فقط عند `SyncOutcome.Success` — بعد كتابة متفائلة (إكمال يوم) أو Offline outcome، هل الشاشات تعكس الحالة الجديدة فورًا؟
- F5. i18n: الكيانات محلية الاسم (ar/en) والكاش مشترك — تغيير اللغة هل يعيد رسم كل الشاشات من نفس الكاش صح (localeRevision)؟

### المحور G — أحداث وتوقيتات التحديث (Refresh Triggers & Races)

**الملفات:** `MovitAppShellViewModel.kt` + `ShellSyncCoordinator`، `MovitConnectivitySignals.kt`، `BackgroundSyncScheduler.android/ios.kt`، `WeekOfflinePackPrefetcher.kt`، `TrainingConfigEnsure.kt`، `AudioPrefetchRunner.kt`.

**الأسئلة:**
- G1. جرد كل المحفزات (جدول 1.6) وقيّم التغطية: هل ينقص محفز مهم (عودة من شاشة تدريب مكتملة، بعد شراء اشتراك Pro، تغيير لغة يحتاج أصوات لغة جديدة)؟ وهل يوجد محفز زائد يسبب عواصف طلبات (resume + connectivity معًا)؟
- G2. سباق البدء البارد: `init` يطلق `bootstrapLocalCaches()` (async) و`requestSyncIfNeeded()` معًا — هل يمكن أن يتسابق الـ seeding مع الـ sync على نفس المخازن؟
- G3. `syncIfNeeded` أثناء جلسة تدريب حية: full refresh قد يمسح exercise configs بينما الجلسة تقرأها (`isFullSync` يمسح ثم يعيد الكتابة). هل توجد حماية (قفل أثناء التدريب)؟
- G4. الخلفية: راجع إعداد WorkManager/BGTask الفعلي (القيود، التكرار، والسلوك عند فشل متكرر). هل الـ background sync يحترم بطارية/شبكة مقيدة؟
- G5. اقترح "سياسة تحديث" موحدة مكتوبة: لكل حدث → ماذا يُزامن (دلتا/شاشة/replay فقط) وبأي أولوية، بدل التوزيع الحالي المبعثر.

### المحور H — عقد الباك وحجم الحمولة (Backend Contract & Payload Budget)

**الملفات:** `mobile-sync.service.ts` (sync + getExplore + getGlobalMessageStats)، `mobile-home.controller/service`، `reports` module (dashboard/metrics)، `effective-plan` module، `programs.service.getPublishedForMobile`، `prisma/schema.prisma` (الكيانات المعنية)، وقياس فعلي للحمولات إن أمكن (curl على بيئة dev بحساب موجود).

**الأسئلة:**
- H1. قِس حجم استجابة `/mobile/sync` full ودلتا-فارغة لحساب حقيقي (bytes, gzip). ما أكبر ثلاثة مساهمين؟ (توقّع: exercises configs، audioManifest، plannedWorkoutReports).
- H2. **`plannedWorkoutReports` تُعاد كلها كل sync** (فلتر status فقط) و**`userPrograms` كلها** — بلا updatedAfter ولا حد. ضع حدًا تعاقديًا (دلتا بالطابع، أو آخر N، أو endpoint منفصل مقسّم صفحات) مع خطة توافق خلفي.
- H3. **`fetchSyncUserPrograms(forceRefresh=true)` في الموبايل ينزّل الحمولة الكاملة ليقرأ enrollments فقط** (يُستدعى عند enroll وrefreshActiveUserProgramId) — `MovitMobileApi.kt:452-460`. هل نضيف endpoint خفيف `/mobile/user-programs`؟
- H4. الدلتا الفارغة: عدّ عدد استعلامات Prisma في `sync()` حتى لو لا تغييرات (counts + stats fingerprint في كل نداء) — هل يحتاج caching على السيرفر (Redis موجود بالفعل في المشروع)؟
- H5. `/mobile/home` بلا أي دلتا/ETag + `HomeTrainModeHydrator` قد يطلق نداءات إضافية — راجع تكلفة كل فتح تطبيق. هل ETag/If-None-Match أو `updatedAt` رخيص التنفيذ هنا؟
- H6. تناسق الأنواع: قارن كل DTO موبايل بحقول الباك فعليًا (خاصة الحقول المضافة حديثًا في git الحالي: `TrainingApiDto`, `ReportDetailStrings`, تعديلات `MobileWriteSyncRepository`) — اذكر أي حقل يفقد بيانات أو يختلف نوعه (epoch string مقابل ISO مثلًا).

### المحور I — دورة حياة الجلسة والحساب (Auth & Data Lifecycle)

**الملفات:** `MovitData.kt` (notifySessionExpired/clearAllUserData)، `MovitHttpClientAuth.kt`، `AccountSyncRepository.kt`، `PlatformMovitAuthTokenStore.kt`, `SecureSessionStore.kt`، `MovitDataModule.kt:67`.

**الأسئلة:**
- I1. **انتهاء الجلسة يمسح كل شيء فورًا بما فيه الـ outbox المعلق** (`notifySessionExpired → runBlocking clearAllUserData`) — قيّم: refresh token فشل مؤقتًا (سيرفر 5xx على /refresh؟) هل يعامل كانتهاء جلسة؟ ما البيانات غير المرفوعة التي تُمحى؟ اقترح: عزل مسح الكاش عن مسح الـ outbox، أو تصدير pending قبل المسح، أو ربط الـ outbox بالـ userId والاحتفاظ به.
- I2. نفس السؤال عند logout الطوعي: هل نفرّغ الـ outbox (flush) قبل المسح؟ وماذا عن جلسة تدريب نشطة أثناء انتهاء الجلسة؟
- I3. `runBlocking` داخل `notifySessionExpired` (يُستدعى من interceptor شبكة) — خطر ANR/deadlock؟
- I4. تعدد الحسابات على نفس الجهاز: الكاش لا يحمل userId (مفاتيح عامة) — تحقق أن كل مسارات التبديل تمر بـ clearAllUserData وأن الـ guest-execution uploads تُنسب للحساب الصحيح بعد الدخول.
- I5. أول تشغيل ضيف: ماذا يعمل فعليًا أوفلاين من البذرة الباردة (يتصفح؟ يتدرب؟ يرى تقاريره المحلية؟) — حدد الفجوات.

### المحور J — تخصيصات المستخدم والتقارير (User Overrides & Reports Consistency)

**الملفات:** `DayCustomizationLocalStore.kt` + `DayCustomizationKeyResolver.kt`، `UserProgramEnrollmentLocalStore.kt`، `ExercisePreferenceLocalStore.kt`، `ReportsSyncRepository.kt`، `PlanSyncRepository.kt`، `HomeTrainModeHydrator.kt`، وأجزاء الباك المقابلة (`user-programs` overrides/customizations, `planned_workout_reports`).

**الأسئلة:**
- J1. **تجميد التعارض الدائم:** `hydrateFromBackend` يتخطى أي يوم عليه `isUserModified=true` إلى الأبد — حتى بعد نجاح رفع التخصيص نفسه للسيرفر (لا شيء يصفّر العلم). سيناريو: عدّل يومًا على الجهاز A، ثم عدّله من جهاز B أو الادمن — الجهاز A لن يرى التعديل أبدًا. أكد وصمم قاعدة conflict-resolution صحيحة (مثلاً: صفّر العلم عند SUCCEEDED للـ outbox المقابل، وقارن `customizationsUpdatedAt`).
- J2. **التقارير المحلية تحجب السيرفر:** `hydrateFromSync` backfill-only + التقرير المتفائل يُخزن بـ `id = plannedWorkoutId` وليس id السيرفر، و`completedAt` كـ epoch-string مقابل ISO من السيرفر، و`avgAccuracy` من request قد يكون 0. النتيجة: شاشة التقارير قد تعرض للأبد النسخة المتفائلة الأفقر. صمم قاعدة استبدال آمنة (server wins بعد تأكيد الرفع).
- J3. `patchDashboardFromCompletion` يزيد `daysTrained`/`currentStreak` +1 لكل إكمال بلا فحص لليوم — يومان مكتملان في نفس اليوم = streak+2، ولغير الـ Pro (dashboard sync محجوب بـ `isProUser`) الانحراف يتراكم بلا تصحيح أبدًا. أكد وحدد التصحيح.
- J4. مفاتيح `pref_<exerciseId>` الكanonical مقابل slug/id aliases: تتبع upsert→sync→hydrate (`pendingExerciseIdsFromOutbox` تحمي المعلق) — هل من ثغرة تكتب المفتاح بمعرّف غير canonical فتتكرر التفضيلة؟
- J5. `parseIsoToEpochMs` يتجاهل الـ timezone offset تمامًا (يعامل أي ISO كـ UTC) — آمن الآن (السيرفر يرسل Z) لكنه قنبلة صامتة؛ وثّق أو أصلح.

---

## 5. فرضيات المراجعة الأولية (تُثبَّت أو تُنفى بالدليل)

مرتبة بالخطورة المتوقعة. **S1 = فقدان بيانات/كسر وظيفي، S2 = تضخم/أداء/تعارض بيانات، S3 = جودة/صيانة.**

| # | الخطورة | الفرضية | الدليل الأولي | المحور |
|---|---|---|---|---|
| H01 | S1 | انتهاء الجلسة (401+refresh فاشل) يمسح outbox فيه تدريبات غير مرفوعة → فقدان صامت | `MovitData.kt:83-101`، `MovitDataModule.kt:67`، `Outbox.sq deleteAll` عبر `clearAllUserData` | I |
| H02 | S1 | replay بدون قفل وبدون تعليم IN_FLIGHT → إمكانية إرسال مزدوج متوازٍ لعمليات غير idempotent على السيرفر (planned complete/report) | `OfflineWriteQueue.replayPending:137-191` لا يكتب IN_FLIGHT قبل dispatch؛ 5 محفزات متوازية | D |
| H03 | S1 | 3 محاولات فاشلة على شبكة متقلبة → FAILED_PERMANENT نهائي بلا إشعار ولا استرجاع لبيانات تدريب | `OutboxConflictPolicy.shouldMarkPermanent:40-42` (`attempts>=3 && isRetryable(null)`) | D |
| H04 | S1 | تخصيصات الأيام: `isUserModified=true` يمنع تحديث السيرفر لهذا اليوم إلى الأبد (لا تصفير بعد نجاح الرفع) | `DayCustomizationLocalStore.hydrateFromBackend:107-115` | J |
| H05 | S2 | التقرير المتفائل المحلي يحجب تقرير السيرفر نهائيًا + خلط صيغ (`id=workoutId`, epoch-string) | `ReportsSyncRepository.hydrateFromSync:83-95` + `recordPendingPlannedWorkoutCompletion:97-125` + `formatEpochMillis:402` | J |
| H06 | S2 | Pull-to-refresh في Explore = تنزيل الكتالوج كاملًا في كل مرة (`syncFull` يمسح طابع الدلتا) | `SharedExploreRepository.refreshExploreContent:40` → `ExploreSyncRepository.syncFull:43` | F/H |
| H07 | S2 | `fetchSyncUserPrograms(forceRefresh=true)` ينزّل حمولة sync الكاملة لقراءة enrollments فقط (عند كل enroll/resolve) | `MovitMobileApi.kt:452-460`، `PlanSyncRepository.kt:36-42,69` | H |
| H08 | S2 | الباك يعيد كل `plannedWorkoutReports` المكتملة وكل `userPrograms` في كل sync (دلتا أو full) — حمولة تنمو مع التاريخ التدريبي | `mobile-sync.service.ts:545-598` | H |
| H09 | S2 | ازدواج تخزين: (أ) workout export + نسخة training-config محوّلة، (ب) نصوص الرسائل في المكتبة وداخل كل config، (ج) جورنال في SQL + json_cache، (د) برنامج تحت مفتاح slug وid معًا عبر `syncProgram(slug)` | `SyncCatalogOfflineRepository.applyFromSync:82-104`، `TrainingConfigRepository.mergeRecordForPersist`، `SessionJournalStore.saveCheckpoint:18-30`، `ProgramFlowSyncRepository.syncProgram:65-93` | A |
| H10 | S2 | لا GC إطلاقًا للـ json_cache (تقارير، metrics بمفاتيح query، effective plans، post-training لكل set، لقطات fs) → نمو غير محدود | `MovitCacheKeys.kt` + غياب أي purge غير `OutboxMaintenance` | A |
| H11 | S2 | دلتا رسائل واحدة → `applySyncMessageLibrary` يعيد كتابة **كل** exercise configs (write amplification)، وتعديل رسالة بلا تمرينها → fingerprint drift → **full sync لكل الأجهزة** | `TrainingConfigRepository.applySyncMessageLibrary:122-146`، `MovitSyncOrchestrator.detectDrift:241-269` | A/B |
| H12 | S2 | حلقة full-refresh محتملة عندما يكون explore blob فارغًا وconfigs موجودة (العدّادات من explore تُقارن بـ totals السيرفر في كل دورة بلا دلتا) | `MovitSyncOrchestrator.updateLocalEntityCounts:294-302` + `detectDrift` | B |
| H13 | S2 | `syncIfNeeded` يبتلع كل Throwable كـ Offline (يشمل أخطاء serialization وربما CancellationException) — أعطال حقيقية تظهر كـ"أوفلاين" | `MovitSyncOrchestrator.kt:93-99` | B |
| H14 | S2 | `syncBusy/lastSyncAttemptMs` بلا تزامن + full-sync يمسح المخازن قبل إعادة كتابتها بلا transaction — انقطاع في المنتصف = كاش ناقص حتى الدورة التالية | `MovitSyncOrchestrator.beginSync:309-317`، `SyncCatalogOfflineRepository.applyFromSync:55-58`، `TrainingConfigRepository.applySyncExercises:71-77` | B/G |
| H15 | S2 | batch رفع تمارين explore محفوظ **في الذاكرة فقط** حتى flush — kill يفقد الحزمة رغم وجود journal | `WorkoutExecutionBatchCoordinator.pending:23` | E |
| H16 | S2 | streak/daysTrained المتفائلة تتضاعف لغير الـ Pro بلا تصحيح سيرفر (dashboard sync محجوب) | `ReportsSyncRepository.patchDashboardFromCompletion:330-360` + `syncDashboard:190-193` | J |
| H17 | S3 | استخراج HTTP status بـ regex من نص الخطأ — عقد هش بين طبقتين | `OutboxModels.parseHttpStatusFromError:48-54` + صيغ الرسائل في `MovitMobileApi` | D |
| H18 | S3 | `markCompleted` يكتب checkpoint completed ثم يحذفه فورًا (عمل مهدر/نية غير محققة) | `SessionJournalStore.markCompleted:54-62` | E |
| H19 | S3 | `runBlocking` في مسار interceptor لانتهاء الجلسة | `MovitData.notifySessionExpired:85` | I |
| H20 | S3 | `parseIsoToEpochMs` يتجاهل timezone offsets | `DayCustomizationLocalStore.kt:208-229` | J |
| H21 | S3 | fallback حساب `messageLibraryStats` محليًا بصيغة fingerprint مختلفة عن السيرفر + دلالة `totalWithAudio` مختلفة (نص مقابل صوت) | `TrainingConfigRepository.computeMessageLibraryStats:148-164` مقابل `mobile-sync.service.getGlobalMessageStats` | B |
| H22 | S3 | قراءات blobs متزامنة محتملة على main thread (MovitLocalStore غير suspend) | `MovitLocalStore.kt` + مواضع الاستدعاء في features | F |
| H23 | S3 | طابعا دلتا منفصلان للكتالوج (explore_last_sync مقابل sync العام) بمساري كتابة لنفس الـ blob | `ExploreSyncRepository` (applyFromSync + syncInternal) | B |

---

## 6. صيغة المخرجات المطلوبة من فريق المراجعة

### 6.1 تقرير لكل محور

ملف لكل محور: `Docs/04-Research/data-layer-review/<حرف-المحور>-<اسم-قصير>.md` (مثال: `A-storage-size.md`).

هيكل إلزامي:

```markdown
# المحور X — <الاسم>
**المراجع:** <agent> · **التاريخ:** · **الملفات المقروءة بالكامل:** <قائمة>

## 1. الحكم التنفيذي (≤ 10 أسطر)
سليم / يحتاج إصلاحات محددة / كسر جوهري — مع أهم 3 نتائج.

## 2. إجابات الأسئلة (سؤالًا سؤالًا: A1، A2، ...)
لكل سؤال: الإجابة + الدليل (file:line + مقتطف) + الحكم.

## 3. نتائج الفرضيات المسندة (H..)
| فرضية | الحكم (مؤكدة/منفية/جزئية) | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |

## 4. نتائج جديدة غير مذكورة في الـ Brief
نفس جدول الفرضيات + خطورة مقترحة.

## 5. التوصيات
| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد (S/M/L) | مخاطر التنفيذ |

## 6. فجوات الاختبارات
اختبارات موجودة راجعتُها / اختبارات ناقصة يجب إضافتها (بأسماء ملفات مقترحة).
```

### 6.2 التقرير الموحد (بعد اكتمال المحاور)

`Docs/04-Research/data-layer-review/00-consolidated-report.md`:
1. جدول كل النتائج المؤكدة موحّدة ومنزوعة التكرار، مرتبة: S1 ثم S2 ثم S3.
2. **خطة تنفيذ مرحلية**: المرحلة 1 (سد فقدان البيانات: H01–H04)، المرحلة 2 (الحمولة والتضخم)، المرحلة 3 (الجودة) — كل بند بملفاته وتقدير جهده.
3. "سياسة البيانات" النهائية المقترحة كصفحة واحدة تُضاف لاحقًا إلى `Docs/00-Active-Reference/`: ماذا يُخزن أين، متى يُزامن، متى يُنظف، وكيف تُحل التعارضات.
4. قائمة القرارات المعمارية المفتوحة (إن وجدت) التي تحتاج قرار المالك.

### 6.3 قواعد عامة

- الاستشهاد إلزامي: `path/to/file.kt:line`. أي ادعاء بلا موضع كود = يُرفض.
- عند فحص سلوك السيرفر، اقرأ كود الـ NestJS الفعلي في `backend/src/modules/...` — لا تعتمد على وثائق فقط.
- لا تعديلات كود أثناء المراجعة — تقارير فقط.
- اللغة: عربية بمصطلحات تقنية إنجليزية (مثل هذا الملف).

---

## 7. ملاحق سريعة للمراجعين

### 7.1 أهم نقاط الدخول للتشغيل الذهني (mental trace)

| سيناريو | ابدأ من |
|---|---|
| فتح التطبيق بارد أوفلاين | `MovitAppShellViewModel.init` → `bootstrapLocalCaches` → `ColdOfflineBundleSeeder` |
| فتح التطبيق أونلاين | نفس المسار → `syncIfNeeded` → `MovitSyncOrchestrator.runSyncCycle` |
| تدريب تمرين واحد بالكاميرا | `TrainingSessionWriteHooks.attach` → ... → `enqueueExecutionUpload` |
| إكمال يوم برنامج | `completePlannedDay` → `recordPendingPlannedWorkoutCompletion` → outbox |
| تعديل تمارين يوم | `WorkoutSessionSyncRepository.saveDayCustomizations` → optimistic → outbox |
| عودة الإنترنت بعد أسبوع أوفلاين | `OutboxConnectivityReplay` + `onConnectivityRestored` → replay + sync |

### 7.2 حالة git الحالية (سياق العمل الجاري)

يوجد تعديل غير مكتمل commit على: `MobileWriteSyncRepository`, `MovitCacheKeys`, `ReportsSyncRepository`, `WorkoutUploadMapper`, `TrainingApiDto`, وعدة ملفات training-engine (metrics/report/journal) + strings. راجع `git diff` لفهم الاتجاه الحالي قبل اقتراح ما يتعارض معه — والمرجع `Training-Metrics-Audit.md` (بالجذر) يوثّق تدقيقًا مكتملًا لمحرك المتريكس نفسه؛ **محور E يبني عليه ولا يكرره**.

### 7.3 ما هو خارج نطاق هذه المراجعة

- منطق محرّك التدريب نفسه (rep counting, scoring, pose) — مغطى في `Training-Metrics-Audit.md`.
- واجهات المستخدم والتصميم.
- الفوترة/الاشتراكات إلا في حدود `isProUser` gating للتقارير.
- Admin Dashboard.
