# المحور G — أحداث وتوقيتات التحديث (Refresh Triggers & Races)
**المراجع:** وكيل مراجعة مستقل (Axis G) · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**
`MovitAppShellViewModel.kt`, `ShellSyncCoordinator.kt`, `ShellSyncLifecycleEffects.kt`, `MovitConnectivitySignals.kt`, `BackgroundSyncScheduler.kt` (common), `BackgroundSyncScheduler.kt` (android), `BackgroundSyncScheduler.ios.kt`, `MovitBackgroundSyncWorker.kt`, `WeekOfflinePackPrefetcher.kt`, `TrainingConfigEnsure.kt`, `AudioPrefetchRunner.kt`, `MovitSyncOrchestrator.kt` (throttle/`syncBusy`/full apply), `ColdOfflineBundleSeeder.kt`, `MovitData.kt` (`bootstrapLocalCaches`), `OutboxConnectivityReplay.android/ios.kt`, `IosNetworkMonitor.kt`, `TrainingConfigRepository.applySyncExercises`, `SyncCatalogOfflineRepository.applyFromSync`, `PoseApp.kt`, `MovitDataInstall.kt`, `MainViewController.kt`, `MovitBillingHost.kt`, `IosSubscriptionCoordinator.kt` (post-purchase), `TrainingSessionAudioHooks.kt`, `TrainingSessionViewModel` (حماية الجلسة الحية), `AudioDownloadSupport.kt`, واختبارات: `MovitSyncOrchestratorTest.kt`, `WeekOfflinePackPrefetcherTest.kt`, `TrainingConfigEnsureTest.kt`, `AudioPrefetchRunnerTest.kt`, `ColdOfflineBundleSeederTest.kt` (وجودها).

## 1. الحكم التنفيذي (≤ 10 أسطر)
**يحتاج إصلاحات محددة** — محفزات الجدول 1.6 موجودة فعليًا، لكن السياسة مبعثرة بلا قفل تدريب وبلا تزامن آمن، مع عاصفة طلبات على Android عند استعادة الاتصال، وفجوات بعد Pro/لغة/إكمال تدريب.

1. **عاصفة Android عند connectivity:** `requestNow` + `notifyConnectivityRestored` → shell `forceCheck` + outbox replay منفصل — ثلاثة مسارات متوازية لنفس الحدث.
2. **H14 جزئيًا مؤكدة:** `syncBusy` check-then-set بلا mutex؛ full sync يمسح configs/catalog قبل إعادة الكتابة بلا transaction — انقطاع أو تداخل = كاش ناقص.
3. **لا حماية أثناء جلسة تدريب حية**؛ و`TrainingConfigEnsure` قد يستدعي `fullRefresh()` أثناء التحضير. محفزات ناقصة: شراء Pro، تغيير لغة→أصوات، إكمال تدريب→refresh UI/sync خفيف.

## 2. إجابات الأسئلة (سؤالًا سؤالًا)

### G1 — جرد المحفزات والتغطية

| الحدث (جدول 1.6) | المسار الفعلي | النوع | تقييم |
|---|---|---|---|
| فتح التطبيق بجلسة نشطة | `MovitAppShellViewModel.init` → `bootstrapLocalCaches()` + `requestSyncIfNeeded()` | throttled (5 دقائق) | موجود؛ سباق مع bootstrap (G2) |
| عودة للمقدمة | `ShellSyncLifecycleEffects` `ON_RESUME` → `onAppResumed()` | throttled | موجود |
| استعادة الاتصال | Android: `OutboxConnectivityReplay` → `requestNow` + `notifyConnectivityRestored` → `ShellSyncCoordinator.requestSync(forceCheck=true)`؛ iOS: `IosNetworkMonitor` → نفس الإشارة + outbox replay | فوري | موجود + **زائد على Android** |
| تسجيل دخول / onboarding | `handleAuthEffect` / `handleOnboardingEffect` → `requestSyncIfNeeded(forceCheck=true)` | فوري | موجود |
| Pull-to-refresh (شاشات) | خارج shell؛ Explore = `syncFull` (محور F) | فوري | موجود (تكلفة عالية في Explore) |
| خلفية دورية | `BackgroundSyncScheduler` → `runBackgroundSyncIfReady` → `syncIfNeeded(forceCheck=true)` | فوري عند التشغيل | موجود |
| حزمة الأسبوع | `WeekOfflinePackPrefetcher.prefetchWeek` → `syncIfNeeded(forceCheck=true)` + plans + audio | فوري | موجود (يدوي من UI) |
| انتهاء جلسة / logout | `clearAllUserData` (محور I) | مدمّر | خارج نطاق التوقيت؛ موثّق في 1.6 |

**محفزات إضافية موجودة في الكود وغير مذكورة صراحة في 1.6:**

| محفز | المسار | ملاحظة |
|---|---|---|
| `TrainingConfigEnsure.ensure` | `syncIfNeeded(forceCheck)` ثم `fullRefresh()` إن لزم | قد يفرض full أثناء فتح تمرين |
| `TrainingSessionAudioHooks.prefetchOnSessionOpen` | prefetch صوت عند فتح الجلسة | لا sync كتالوج |
| Android `schedule()` | periodic + **one-time REPLACE فوري** | كل استدعاء `schedule()` يطلق sync الآن أيضًا |

**محفزات ناقصة (فجوات منتج):**

1. **بعد شراء اشتراك Pro:** `MovitBillingHost.refreshSessionAfterPurchase` و`IosSubscriptionCoordinator` يستدعيان `account.fetchProfile()` فقط — **لا** `syncIfNeeded` ولا `reportsSync.syncDashboard()`. تقارير Pro تبقى محجوبة حتى sync لاحق (resume/خلفية).
2. **تغيير اللغة:** `LanguageChanged` يزيد `localeRevision` فقط — **لا** إعادة prefetch لأصوات اللغة الجديدة (`AudioPrefetchRunner` ينزّل ما في الـ manifest الحالي؛ `resolveLanguageSubdir` موجود لكن لا يُستدعى من مسار تغيير اللغة).
3. **عودة من تدريب مكتمل:** لا bump لـ `dataRevision` ولا sync خفيف من الـ shell؛ الاعتماد على optimistic cache + فتح شاشة لاحقًا (محور F).

**محفزات زائدة / عواصف:**

```35:41:kmp-app/core/data/src/androidMain/kotlin/com/movit/core/data/outbox/OutboxConnectivityReplay.android.kt
        override fun onAvailable(network: Network) {
            BackgroundSyncScheduler.requestNow()
            replayPendingOutboxIfInstalled()
            if (wasOffline) {
                MovitConnectivitySignals.notifyConnectivityRestored()
            }
            wasOffline = false
        }
```

```45:47:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellViewModel.kt
        MovitConnectivitySignals.setOnConnectivityRestored {
            ShellSyncCoordinator.requestSync(forceCheck = true)
        }
```

على Android عند عودة الشبكة بعد offline: (1) WorkManager one-time sync، (2) shell `syncIfNeeded(forceCheck=true)`، (3) outbox replay مستقل. الـ orchestrator قد يتخطى الثاني بـ `syncBusy` → `Skipped`، لكن الطلبات/العمال تتزاحم. إن تزامن ذلك مع `ON_RESUME` (throttled) يزداد الضغط.

**ملاحظة:** `MovitAppShellViewModel.onConnectivityRestored()` **ميت** — لا يُستدعى من أي مكان؛ المسار الحي هو `ShellSyncCoordinator` فقط.

```77:79:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellViewModel.kt
    fun onConnectivityRestored() {
        requestSyncIfNeeded(forceCheck = true)
    }
```

**الحكم G1:** التغطية الأساسية جيدة؛ الفجوات بعد Pro/لغة/إكمال تدريب حقيقية؛ عاصفة Android عند connectivity مؤكدة.

---

### G2 — سباق البدء البارد (bootstrap × sync)

```63:68:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellViewModel.kt
            if (bootstrap.hasActiveSession) {
                viewModelScope.launch {
                    MovitData.bootstrapLocalCaches()
                }
                requestSyncIfNeeded()
                ensureOnboardingGateIfNeeded()
            }
```

- `bootstrapLocalCaches()` coroutine منفصلة.
- `requestSyncIfNeeded()` يطلق coroutine **أخرى** فورًا بلا انتظار انتهاء الـ seed.

```76:80:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/MovitData.kt
    suspend fun bootstrapLocalCaches() {
        if (!isInstalled) return
        val koin = koin()
        koin.get<ColdOfflineBundleSeeder>().seedIfNeeded()
        koin.get<SystemMessageCache>().loadIntoRegistry()
    }
```

الـ seeder يكتب على نفس المخازن التي يكتبها sync:

```78:82:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/cache/ColdOfflineBundleSeeder.kt
        if (trainingConfigMissing && bundle.exercises.isNotEmpty()) {
            trainingConfig.applySyncExercises(
                exercises = bundle.exercises,
                isFullSync = true,
            )
```

و`applySyncExercises(isFullSync=true)` يمسح كل configs أولًا:

```72:78:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
        if (isFullSync) {
            readSlugIndex().forEach { slug ->
                localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseConfigKey(slug))
            }
            writeSlugIndex(emptyList())
            writeSlugAliasMap(emptyMap())
        }
```

**سيناريو إعادة:**
1. جهاز جديد/كاش فارغ، جلسة نشطة، شبكة متاحة.
2. `init` يطلق seed و sync معًا.
3. Sync يبدأ `runSyncCycle` ويصل لـ `applySyncExercises(isFullSync=…)` أو يكتب explore.
4. بالتوازي seed يمرّ بفحص `trainingConfigMissing` (كان فارغًا) ثم `isFullSync=true` فيمسح ما كتبه sync جزئيًا، أو العكس: sync full يمسح بذرة الـ seed قبل اكتمالها.
5. النتيجة المحتملة: فهرس/configs ناقصة حتى الدورة التالية؛ أو مزج cold-bundle مع دلتا السيرفر.

التخفيف الجزئي: seed يتخطى إن المخازن ممتلئة — لكن نافذة الفحص→الكتابة غير ذرّية مقابل sync.

**الحكم G2:** السباق **مؤكد** على مستوى الإطلاق؛ الأثر أسوأ على أول تشغيل أونلاين بكاش فارغ.

---

### G3 — sync أثناء جلسة تدريب حية

لا يوجد قفل/علم «training active» في `MovitSyncOrchestrator` أو الـ shell. محفزات resume/connectivity/background/week-pack/`TrainingConfigEnsure` تبقى قادرة على تشغيل sync أثناء التدريب.

عند `isFullSync`:

```153:160:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
        val isFullSync = forceFullRefresh || syncResponse.meta?.isFullSync == true
        var exploreData = exploreSync.readCached()
        syncResponse.data?.let { payload ->
            trainingConfig.applySyncExercises(
                exercises = payload.exercises,
                deletedExerciseIds = payload.deletedExerciseIds,
                isFullSync = isFullSync,
            )
```

وcatalog:

```55:58:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/SyncCatalogOfflineRepository.kt
        if (isFullSync) {
            clearProgramStore()
            clearWorkoutStore()
        }
```

الحماية الوحيدة القريبة في التدريب تخص **إعادة بناء المحرك** لا المزامنة:

```1413:1415:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  private fun rebuildEngineIfNeeded() {
    if (supervisor.state.value.isTrainingActive()) return
```

الجلسة الحية تحتفظ غالبًا بـ `exerciseConfig` في الذاكرة بعد التحميل — لذا المسح من القرص **قد لا يُسقط العدّ فورًا**. لكن:
- أي قراءة لاحقة من الكاش (ensure، رسائل، template) أثناء نافذة المسح→الكتابة ترى فراغًا.
- `TrainingConfigEnsure` قبل الجلسة قد يستدعي `fullRefresh()`:

```61:65:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigEnsure.kt
    runSyncAttempt(resolvedSync, forceCheck = true)
    if (supports(normalized)) return TrainingConfigEnsureResult.Available

    runSyncAttempt(resolvedSync, forceFullRefresh = true)
```

**سيناريو أسوأ:** جلسة حية + استعادة شبكة → drift → full refresh → مسح configs على القرص بينما خلفية أخرى تقرأ slug → فشل مؤقت / رسائل صوت ناقصة؛ إن قُتل التطبيق في منتصف المسح يبقى كاش ناقص (H14).

**الحكم G3:** **لا حماية**؛ الخطر S2 على اتساق الكاش أكثر منه سقوط فوري للمحرك في الذاكرة.

---

### G4 — الخلفية (WorkManager / BGAppRefresh)

**Android**

```21:37:kmp-app/core/data/src/androidMain/kotlin/com/movit/core/data/sync/BackgroundSyncScheduler.kt
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<MovitBackgroundSyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
```

- تكرار: 6 ساعات، `ExistingPeriodicWorkPolicy.UPDATE`.
- قيد وحيد: `CONNECTED` — **لا** `UNMETERED`، **لا** `RequiresBatteryNotLow`، **لا** charging.
- `schedule()` يضيف أيضًا one-time `REPLACE` فورًا → sync عند كل إقلاع تطبيق (`PoseApp.onCreate`).
- الفشل: `MovitBackgroundSyncWorker` → `Result.retry()` (backoff نظام WorkManager).
- الحارس: `runBackgroundSyncIfReady` يتحقق من install + auth + `isNetworkAvailable()`.

**iOS**

```41:77:kmp-app/core/data/src/iosMain/kotlin/com/movit/core/data/sync/BackgroundSyncScheduler.ios.kt
    actual fun schedule() {
        registerTaskHandlerIfNeeded()
        val request = BGAppRefreshTaskRequest(MOVIT_IOS_BACKGROUND_SYNC_TASK_ID).apply {
            earliestBeginDate = NSDate().dateByAddingTimeInterval(SYNC_INTERVAL_SECONDS)
        }
        ...
    }
    ...
        refreshTask.expirationHandler = {
            didExpire = true
        }
        val succeeded = if (didExpire) {
            false
        } else {
            runBlocking {
                runBackgroundSyncIfReady() != BackgroundSyncRunOutcome.Failed
            }
        }
```

- `earliestBeginDate` بعد ~6 ساعات؛ النظام يقرر التشغيل الفعلي (غير مضمون).
- `expirationHandler` يضبط علمًا فقط؛ **لا يلغي** `runBlocking` الجاري — فحص `didExpire` قبل البدء فقط، فانتهاء المهلة أثناء sync لا يوقف العمل فورًا.
- لا قيود بطارية/شبكة صريحة (يعتمد على سياسة iOS لـ BGAppRefresh).
- استعادة الاتصال على iOS **لا** تستدعي `requestNow` (عكس Android) — فقط outbox + shell sync عبر الإشارة.

**الحكم G4:** الخلفية تعمل لكن **لا تحترم بيانات محدودة/بطارية منخفضة** على Android؛ iOS أضعف ضمانًا زمنيًا ومعالجة انتهاء المهمة هشة.

---

### G5 — سياسة تحديث موحدة مقترحة

بدل التوزيع الحالي (shell + WorkManager + connectivity + ensure + week pack)، سياسة واحدة:

| الحدث | ماذا يُزامن | أولوية | ملاحظات |
|---|---|---|---|
| Cold start (جلسة نشطة) | 1) `bootstrapLocalCaches` **ثم** 2) delta `syncIfNeeded` | P0 | تسلسل إلزامي؛ لا توازٍ |
| `ON_RESUME` | delta throttled (5 دقائق) إن أونلاين | P2 | إن جلسة تدريب حية → **تأجيل** إلى ما بعد الجلسة |
| Connectivity restored | **replay outbox فقط** أولًا؛ ثم delta واحد عبر منسّق واحد (إلغاء `requestNow` المزدوج) | P1 | دمج مساري Android |
| Login / onboarding complete | delta `forceCheck` | P0 | كما هو |
| Pull-to-refresh Home/Train/Reports | sync الشاشة فقط (home/dashboard) | P1 | ليس full catalog |
| Pull Explore | **delta explore** افتراضيًا؛ full عبر زر «إصلاح» فقط | P1 | إصلاح H06 |
| Background 6h | delta `forceCheck` مع قيود: UNMETERED أو BatteryNotLow (قابل للضبط) | P3 | لا full إلا drift |
| Week offline pack | delta إن لزم + effective plans + audio targets | P2 | يدوي؛ لا يُطلق أثناء تدريب |
| `TrainingConfigEnsure` | template fetch → delta؛ **full فقط** إن فشل الاثنان وUI يطلب إصلاحًا | P1 | تجنّب `fullRefresh` الصامت |
| جلسة تدريب حية | **حظر** full؛ السماح بـ outbox replay فقط | P0 | علم `trainingSessionActive` |
| شراء Pro | `fetchProfile` + `reports.syncDashboard()` (+ bump `dataRevision`) | P1 | فجوة حالية |
| تغيير لغة | `localeRevision` + `AudioPrefetchRunner` للغة الجديدة | P2 | فجوة حالية |
| إكمال تدريب / يوم مخطط | لا sync كتالوج؛ bump UI من الكتابة المتفائلة أو `dataRevision` خفيف | P1 | مع محور F |
| 401 / logout | clear (سياسة محور I) | P0 | — |

تنفيذ مقترح: توسيع `ShellSyncCoordinator` (أو `MovitSyncOrchestrator`) بـ `SyncRequest(reason, mode, priority)` وطابور داخلي بدل إطلاقات متوازية من 5 أماكن.

**الحكم G5:** السياسة أعلاه قابلة للتنفيذ على البنية الحالية بدون إعادة كتابة معمارية.

## 3. نتائج الفرضيات المسندة (H..)

| فرضية | الحكم | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |
|---|---|---|---|---|
| **H14** (جزء races / G+B) | **جزئية مؤكدة** | `beginSync` check-then-set بلا mutex: `MovitSyncOrchestrator.kt:309-317`؛ مسح قبل كتابة: `TrainingConfigRepository.kt:72-78`، `SyncCatalogOfflineRepository.kt:55-58`؛ محفزات متوازية: shell + WorkManager + connectivity + ensure + week pack | 1) افتح التطبيق (shell sync). 2) اقطع/أعد الشبكة فورًا (Android `requestNow` + shell force). 3) أو شغّل week prefetch أثناء sync. 4) إن تداخل `beginSync` قبل ضبط `syncBusy` من خيطين → دورتان؛ أو الثانية `Skipped` بينما الأولى في منتصف `isFullSync` clear. 5) اقتل العملية بعد clear وقبل rewrite. | كاش ناقص/متضارب حتى sync لاحق؛ طلبات متزاحمة؛ ليس فقدان outbox بحد ذاته (ذلك محور D/I) |

جزء «بلا تزامن» في H14: البوابة اللينة تمنع معظم التوازي لكنها **ليست آمنة** تحت تسابق حقيقي. جزء «مسح بلا transaction» **مؤكد**.

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | الفرضية الجديدة | الدليل | سيناريو | الأثر |
|---|---|---|---|---|---|
| **G-N1** | S2 | عاصفة Android عند استعادة الاتصال: WorkManager one-time + shell force sync + outbox replay | `OutboxConnectivityReplay.android.kt:35-41` + `MovitAppShellViewModel.kt:45-47` | Offline → online | ضغط شبكة/CPU؛ تسابق replay (مع H02) |
| **G-N2** | S2 | لا sync/dashboard بعد ترقية Pro — التقارير تبقى غير مُحدَّثة حتى محفز لاحق | `MovitBillingHost.kt:22-27`؛ orchestrator يحجب dashboard لغير Pro: `MovitSyncOrchestrator.kt:227` | اشترِ Pro داخل التطبيق وافتح Reports فورًا | UX Pro ناقص؛ انحراف متريكس محلي بلا تصحيح (يرتبط J/H16) |
| **G-N3** | S3 | تغيير اللغة لا يطلق prefetch أصوات اللغة الجديدة | `MovitAppShellViewModel.kt:236-238` يزيد `localeRevision` فقط؛ لا استدعاء `AudioPrefetchRunner` | بدّل ar↔en ثم ابدأ تمرينًا | TTS fallback أو صمت حتى sync/prefetch لاحق |
| **G-N4** | S3 | `onConnectivityRestored()` في الـ ViewModel كود ميت | تعريف بلا callers خارج الملف | — | لبس صيانة؛ مساران ظاهريان لمسار واحد حي |
| **G-N5** | S3 | iOS `expirationHandler` لا يوقف `runBlocking` للـ sync | `BackgroundSyncScheduler.ios.kt:63-73` | BGAppRefresh ينتهي أثناء دورة طويلة | عمل بعد انتهاء المهلة؛ `setTaskCompleted` قد يُستدعى متأخرًا |
| **G-N6** | S2 | `TrainingConfigEnsure` يفرض `fullRefresh` صامتًا عند miss | `TrainingConfigEnsure.kt:61-65` | افتح تمرينًا ناقصًا من الكاش أثناء خلفية/جلسة أخرى | مسح كتالوج كامل غير مقصود من المستخدم |

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **G-R1** | S2 | توحيد محفز connectivity على Android: أزل `BackgroundSyncScheduler.requestNow()` من `onAvailable` **أو** أزل shell sync من الإشارة — مسار واحد فقط + outbox replay. ملف: `OutboxConnectivityReplay.android.kt`, اختياريًا `MovitAppShellViewModel.kt` | S | تأخير طفيف لـ sync بعد الشبكة إن أُبقي replay أولًا |
| **G-R2** | S2 | تسلسل cold start: `await bootstrapLocalCaches()` ثم `requestSyncIfNeeded()`. ملف: `MovitAppShellViewModel.kt` | S | تأخير عشرات–مئات ms قبل أول sync على أجهزة بطيئة |
| **G-R3** | S2 | قفل تدريب: `MovitData`/`ShellSyncCoordinator` يرفض `forceFullRefresh` ويفضّل تأجيل delta أثناء `isTrainingActive`؛ السماح بـ outbox replay فقط. ملفات: `MovitSyncOrchestrator.kt`, hook من `TrainingSessionViewModel` | M | تأجيل تحديثات كتالوج أثناء جلسة طويلة |
| **G-R4** | S2 | بعد شراء Pro: `fetchProfile` ثم `reports.syncDashboard()` + bump `dataRevision`. ملفات: `MovitBillingHost.kt`, `IosSubscriptionCoordinator.kt`, shell | S | يعتمد على صحة `isProUser` بعد profile |
| **G-R5** | S2 | استبدل check-then-set لـ `syncBusy` بـ Mutex/atomic gate + طابور طلبات (سبب+أولوية). ملف: `MovitSyncOrchestrator.kt` (+ اختياري `ShellSyncCoordinator`) | M | تغيير سلوك `Skipped`؛ يحتاج اختبارات تزامن |
| **G-R6** | S3 | قيود WorkManager: `UNMETERED` و/أو `BatteryNotLow` للـ periodic؛ أبقِ one-time عند connectivity بقيود أخف. ملف: `BackgroundSyncScheduler.kt` (android) | S | قلة تشغيل الخلفية على بيانات خلوية |
| **G-R7** | S3 | عند `LanguageChanged`: استدعِ `audioPrefetch.afterManifestApplied(false)` أو prefetch موجّه للغة. ملفات: shell/profile + `AudioPrefetchRunner` | S | تنزيل إضافي عند تبديل اللغة |
| **G-R8** | S3 | خفّض `TrainingConfigEnsure`: لا `fullRefresh` تلقائيًا؛ أعد المحاولة بالـ template endpoint + رسالة UI. ملف: `TrainingConfigEnsure.kt` | S | تمرين نادر قد يبقى unavailable حتى إصلاح يدوي |

**أهم 3 توصيات للتنفيذ أولًا:** G-R1، G-R2، G-R3 (ثم G-R5 مع محور B).

## 6. فجوات الاختبارات

**موجودة راجعتُها:**
- `MovitSyncOrchestratorTest` / `HydrationTest` / `CatalogTest` — سعادة المسار، offline، audio prefetch؛ **لا** تزامن متوازٍ ولا throttle races.
- `WeekOfflinePackPrefetcherTest` — تخطيط الأسبوع فقط؛ **لا** تكامل sync+plans تحت تسابق.
- `TrainingConfigEnsureTest` — مسارات ensure؛ لا يثبت منع full أثناء تدريب.
- `AudioPrefetchRunnerTest` — تنزيل/تنظيف؛ لا ربط بتغيير لغة.
- `ColdOfflineBundleSeederTest` — seed منفرد؛ **لا** سباق مع `syncIfNeeded`.

**ناقصة مقترحة:**
1. `MovitSyncOrchestratorConcurrencyTest.kt` — إطلاقان متوازيان لـ `syncIfNeeded`/`fullRefresh`؛ إثبات بوابة آمنة أو طابور.
2. `ShellColdStartOrderingTest.kt` — bootstrap يكتمل قبل أول apply sync على نفس الـ store.
3. `ConnectivityTriggerDedupTest.kt` (android host أو fake) — حدث شبكة واحد → طلب sync واحد + replay واحد.
4. `TrainingSessionSyncGateTest.kt` — أثناء `TRAINING`، `fullRefresh` يُرفض أو يُؤجَّل.
5. `ProPurchaseRefreshTest.kt` — بعد verify/purchase يُستدعى dashboard sync عند `isProUser=true`.
6. `BackgroundSyncConstraintsTest.kt` — توثيق/فحص قيود WorkManager بعد G-R6.
