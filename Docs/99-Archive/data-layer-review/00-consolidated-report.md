# التقرير الموحد — مراجعة طبقة البيانات Offline-first

**التاريخ:** 2026-07-09  
**الفريق:** 10 وكلاء مستقلين (Cursor Grok 4.5 high) — محاور A–J  
**المصدر:** `Data-Layer-Review-Brief.md` + تقارير المحاور في هذا المجلد  
**قاعدة:** لا تعديلات كود في هذه الجولة — تقارير فقط

---

## 0. الحكم الإجمالي

طبقة البيانات **تحقق offline-first للكتالوج والقراءة** (JSON-blob + SWR + cold seed + outbox)، لكن **تكسر هدف «تدريب يصل للباك بدون فقدان»** في عدة مسارات S1 مؤكدة: مسح outbox عند انتهاء الجلسة، replay بلا قفل، فقد صامت بعد 3 محاولات، تجميد تخصيصات الأيام، واستعادة journal مكسورة بعد crash.

| المحور | الحكم | أخطر نتيجة |
|---|---|---|
| A تخزين | يحتاج إصلاحات (S2) | لا GC لـ json_cache؛ ازدواج + write amplification |
| B مزامنة | يحتاج إصلاحات (S2) | drift من explore + ابتلاع Throwable + syncBusy بلا mutex |
| C علاقات | يحتاج إصلاحات (S1 صوت / S2 جراف) | سقوط audio عند دمج الرسائل؛ تقرير الجراف مُهمل |
| D Outbox | يحتاج إصلاحات (S1) | double-dispatch + FAILED_PERMANENT صامت |
| E تدريب | يحتاج إصلاحات (S1 جزئي) | `restore` ثم `start()` يمسح العدات؛ batch في RAM |
| F قراءة UI | يحتاج إصلاحات (S2) | Explore pull = syncFull؛ dataRevision ناقص |
| G محفزات | يحتاج إصلاحات (S2) | عاصفة Android connectivity؛ لا قفل أثناء التدريب |
| H باك/حمولة | يحتاج إصلاحات (S2) | كل reports/userPrograms كل sync؛ enroll = full sync |
| I Auth | **كسر جوهري (S1)** | session expiry يمسح outbox؛ أي فشل refresh = مسح |
| J Overrides/تقارير | يحتاج إصلاحات (S1/S2) | isUserModified أبدي؛ تقرير متفائل يحجب السيرفر |

---

## 1. النتائج المؤكدة الموحّدة (منزوعة التكرار)

مرتبة: **S1 → S2 → S3**. الفرضيات من Brief + نتائج جديدة من المحاور.

### S1 — فقدان بيانات / كسر وظيفي

| ID | المصدر | الحكم | الملخص | الأثر |
|---|---|---|---|---|
| **H01** | I | مؤكدة | `notifySessionExpired` → `clearAllUserData` → `outboxQueries.deleteAll()` | تدريبات غير مرفوعة تُمحى عند 401/فشل refresh |
| **I-refresh** | I (جديد) | مؤكدة | أي فشل `refreshAndPersist` (5xx/timeout/شبكة) = انتهاء جلسة مدمّر | مسح بيانات على خلل سيرفر مؤقت |
| **H02** | D | مؤكدة | `replayPending` بلا mutex وبلا `IN_FLIGHT` قبل HTTP؛ 5+ محفزات متوازية | إرسال مزدوج لـ planned complete/start/override غير idempotent |
| **H03** | D | مؤكدة | `MAX_ATTEMPTS=3` → `FAILED_PERMANENT` بلا UI ولا إحياء | أسبوع أوفلاين + شبكة متقلبة = فقد صامت |
| **H04** | J | مؤكدة | `isUserModified=true` يمنع hydrate إلى الأبد؛ لا تصفير بعد SUCCESS | جهاز A لا يرى تعديلات B/Admin أبدًا |
| **E-restore** | E (جديد) | مؤكدة | `readJournal`→`restore` ثم `recorder.start()` يمسح العدات المستعادة | crash mid-set يفقد العدات المكتملة عند إعادة الفتح |
| **C-audio** | C (جديد C-R1) | مؤكدة | دمج `messageLibrary` عبر `LocalizedNameDto` يسقط `audioAr`/`audioEn` | صوت الرسائل يُفقد من configs بعد دلتا رسائل |

### S2 — تضخم / أداء / تعارض بيانات

| ID | المصدر | الحكم | الملخص |
|---|---|---|---|
| **H05** | J | مؤكدة | تقرير متفائل (`id=workoutId`, epoch-string) + `hydrateFromSync` backfill-only يحجب تقرير السيرفر |
| **H06** | F (مؤكدة) / H (جزئية) | مؤكدة للـ Explore | pull Explore + `syncExplore` = `syncFull()` يمسح watermark؛ ليس `/mobile/sync` الكامل لكن يخالف «دلتا طبيعية» |
| **H07** | H | مؤكدة | `fetchSyncUserPrograms(forceRefresh=true)` = full `/mobile/sync` لقراءة enrollments فقط |
| **H08** | H | مؤكدة | كل `plannedWorkoutReports` المكتملة + كل `userPrograms` في كل sync بلا `updatedAfter` |
| **H09** | A/C | مؤكدة | ازدواج: export+training-config؛ رسائل مكتبة+config؛ journal SQL+json؛ برنامج id+slug |
| **H10** | A | مؤكدة | لا GC لـ json_cache (تقارير/metrics/plans/post-training/frames) |
| **H11** | A/B/C | مؤكدة | دلتا رسائل → إعادة كتابة كل configs + fingerprint drift → full؛ و`resolveRecords` يضاعف feedback |
| **H12** | B | مؤكدة | عدّادات drift من explore blob فارغ + configs موجودة → حلقة full-refresh |
| **H13** | B | مؤكدة | كل `Throwable` → `Offline` (يشمل serialization وربما cancellation) |
| **H14** | B/G | مؤكدة/جزئية | `syncBusy` بلا mutex؛ full sync يمسح بلا transaction → كاش ناقص عند انقطاع |
| **H15** | E | مؤكدة | Explore batch في الذاكرة فقط حتى flush → kill يفقد الرفع |
| **H16** | J | مؤكدة | streak/daysTrained +1 أعمى؛ غير Pro بلا تصحيح dashboard |
| **H23** | B | مؤكدة | طابعا دلتا منفصلان (sync العام vs `explore_last_sync`) لمساري كتابة لنفس البلوب |
| **A-N1** | A | جديدة | logout/clear لا يمسح `audio_cache/` ولا `frame_captures/` |
| **D-N2** | D | جديدة | لا backoff — محفزات متزامنة تستهلك 3 محاولات في ثوانٍ |
| **F-N1** | F | جديدة | `dataRevision` يُتجاهل عند أي `innerRoute` |
| **G-N1** | G | جديدة | عاصفة Android: WorkManager + shell sync + outbox replay معًا عند connectivity |
| **G-N2/N3** | G | جديدة | لا sync بعد شراء Pro؛ لا prefetch أصوات بعد تغيير اللغة |

### S3 — جودة / صيانة

| ID | المصدر | الحكم | الملخص |
|---|---|---|---|
| **H17** | D | جزئية | regex لـ HTTP status يعمل اليوم لأن API يمرّر `(status)` — عقد هش |
| **H18** | E | مؤكدة | `markCompleted` يكتب COMPLETED ثم يحذف فورًا (waste) |
| **H19** | I | مؤكدة | `runBlocking` في مسار Auth interceptor → خطر ANR/deadlock |
| **H20** | J | مؤكدة (كامنة) | `parseIsoToEpochMs` يتجاهل timezone offset |
| **H21** | B | مؤكدة/جزئية | fallback fingerprint محلي بصيغة مختلفة؛ نادر لأن السيرفر يرسل stats |
| **H22** | F/A | مؤكدة عمليًا | قراءات blob متزامنة + `exerciseImageUrl` يعيد decode في loops |

### فرضيات منفية أو مضيّقة

| ID | الحكم | التضييق |
|---|---|---|
| **H06 عبر H** | جزئية | Explore full = ملخصات `/mobile/explore` لا كتالوج sync الكامل — ما زال إسرافًا |
| **تغيير slug منتج** | منفي عمليًا (C) | API الحالي لا يكتب `slug`؛ الخطر نظري/يدوي فقط |
| **H21 تفعيل دائم** | جزئي | mismatch دائم فقط إن غابت stats السيرفر |

---

## 2. خطة تنفيذ مرحلية

### المرحلة 1 — سد فقدان البيانات (أولوية قصوى)

هدف المنتج: «تدريب يصل للباك بدون فقدان ولا تكرار».

| # | الإصلاح | الملفات الأساسية | الجهد | فرضيات |
|---|---|---|---|---|
| 1.1 | عزل مسح outbox عن session expiry؛ لا تمسح `WORKOUT_EXECUTION_UPLOAD` (وربما planned) إلا بعد flush أو ربط `userId` | `MovitData.kt`, `SqlDelightMovitLocalStore.kt`, `Outbox.sq` | M | H01 |
| 1.2 | تمييز فشل refresh: 5xx/شبكة ≠ إبطال جلسة؛ فقط 401/refresh باطل | `MovitHttpClientAuth.kt` | S–M | I-refresh |
| 1.3 | Mutex على `replayPending` + تعليم `IN_FLIGHT` قبل HTTP | `OfflineWriteQueue.kt`, `Outbox.sq` | M | H02, D-N1 |
| 1.4 | Retry بلا سقف لأخطاء الشبكة/5xx + backoff + UI «بيانات معلقة / إعادة» | `OutboxConflictPolicy.kt`, `OfflineWriteQueue.kt`, shell UI | M | H03, D-N2 |
| 1.5 | Idempotency باك لـ `PLANNED_WORKOUT_COMPLETE` أولًا (ثم start/override) | `workout-executions.service.ts`, controllers | M | H02 |
| 1.6 | صفّر `isUserModified` عند SUCCESS؛ احمِ فقط مع pending؛ قارن `customizationsUpdatedAt` | `DayCustomizationLocalStore.kt`, outbox success hook | M | H04 |
| 1.7 | إصلاح ترتيب استعادة الجلسة: لا تستدعِ `start()` بعد `restore` بطريقة تمسح العدات؛ فعّل orphan cleanup | `TrainingSessionWriteHooks.kt`, `MotionRecorder.kt`, ViewModel | M | E-restore |
| 1.8 | إصلاح دمج صوت الرسائل (`audioAr`/`audioEn`) في مسار sync | DTO + `ExerciseMessageLibraryMerger.kt` | M | C-audio |
| 1.9 | إزالة `runBlocking` من مسار interceptor | `MovitData.kt`, DI callback | S | H19 |

### المرحلة 2 — الحمولة والتضخم

| # | الإصلاح | الملفات | الجهد | فرضيات |
|---|---|---|---|---|
| 2.1 | `GET /mobile/user-programs` خفيف؛ حوّل `fetchSyncUserPrograms` إليه | باك controller + `MovitMobileApi.kt`, `PlanSyncRepository.kt` | M | H07 |
| 2.2 | دلتا/`summary` لـ `plannedWorkoutReports` (+ حد لـ userPrograms) | `mobile-sync.service.ts` + عميل | L | H08 |
| 2.3 | Explore pull → `explore.sync()` دلتا؛ `syncFull` لزر إصلاح فقط | `SharedExploreRepository.kt`, `ProgramFlowSyncRepository.kt` | S | H06 |
| 2.4 | GC/TTL لـ reports / metrics / effective plans / post-training + مسح frames/audio عند logout | `JsonCacheMaintenance` جديد، `MovitData.clearAllUserData`, ports | M | H10, A-N1 |
| 2.5 | إيقاف/تقليص `applySyncMessageLibrary` للمتأثر فقط؛ لا تكرار feedback | `TrainingConfigRepository.kt` | M | H11 |
| 2.6 | عدّادات drift من catalog indexes لا explore blob | `MovitSyncOrchestrator.kt` | S | H12 |
| 2.7 | Mutex sync + لا تبتلع كل Throwable كـ Offline (telemetry) | `MovitSyncOrchestrator.kt` | S–M | H13, H14 |
| 2.8 | Explore batch متين (staging قرص أو رفع فوري) | `WorkoutExecutionBatchCoordinator.kt` | M | H15 |
| 2.9 | server-wins للتقارير بعد غياب pending؛ streak من أيام فريدة | `ReportsSyncRepository.kt` | M | H05, H16 |
| 2.10 | توحيد محفز connectivity Android؛ تسلسل bootstrap→sync؛ قفل full-sync أثناء تدريب | `OutboxConnectivityReplay`, shell, orchestrator | M | G-N1, H14 |

### المرحلة 3 — الجودة والصيانة

| # | الإصلاح | الجهد | فرضيات |
|---|---|---|---|
| 3.1 | HTTP status typed بدل regex | S | H17 |
| 3.2 | حذف كتابة COMPLETED المهدرة أو ربط journal↔outbox | S | H18 |
| 3.3 | إصلاح `parseIsoToEpochMs` أو توثيق عقد Z | S | H20 |
| 3.4 | توحيد fingerprint المحلي مع السيرفر أو حذف fallback | S | H21 |
| 3.5 | `Dispatchers.IO` للقراءات الثقيلة؛ فهرس صور بدل decode كامل | S–M | H22 |
| 3.6 | ربط `SyncCatalogGraphReport` بمسار sync | S | C |
| 3.7 | `dataRevision` بعد optimistic + إصلاح شرط innerRoute | M | F-N1 |
| 3.8 | ETag/دلتا لـ `/mobile/home`؛ caching لـ empty-delta counts | M | H |
| 3.9 | محاذاة nullable metrics مع Prisma NOT NULL | S–M | E/H |
| 3.10 | سياسة تحديث موحّدة مكتوبة (حدث → ماذا يُزامن) | S (docs) | G5 |

---

## 3. سياسة البيانات المقترحة (صفحة واحدة → لاحقًا `Docs/00-Active-Reference/`)

### ماذا يُخزَّن أين

| الطبقة | المحتوى | ملاحظة |
|---|---|---|
| `json_cache` | كتالوج (explore summary + exports + configs)، home، enrollments، تفضيلات، تقارير | namespaces من `MovitCacheKeys`؛ لا ازدواج نص رسائل داخل كل config عند persist |
| `outbox_entry` | كل الكتابات للمستخدم + guest executions | مربوطة بـ `userId` عند توفره؛ لا تُمسح مع كاش الجلسة |
| `session_journal` | checkpoint جلسة حية فقط | مصدر واحد (SQL أو json — ليس الاثنين) |
| Filesystem | `audio_cache/`, `frame_captures/` | تُمسح عند logout؛ TTL بعد رفع ناجح للقطات |

### متى يُزامن

| الحدث | السلوك |
|---|---|
| فتح التطبيق / resume | دلتا throttled (5 دقائق) + replay outbox |
| استعادة اتصال | replay أولًا ثم دلتا واحدة (مسار موحّد، لا عاصفة) |
| Login / onboarding | دلتا فورية |
| Pull Explore | **دلتا**؛ زر «إصلاح الكتالوج» فقط = full |
| Enroll / resolve program | endpoint خفيف user-programs — **ليس** full sync |
| أثناء جلسة تدريب حية | replay مسموح؛ **ممنوع** full-sync يمسح configs |
| شراء Pro | sync + `reportsSync.syncDashboard` |
| تغيير لغة | localeRevision + prefetch أصوات اللغة الجديدة |

### متى يُنظف

| الهدف | السياسة |
|---|---|
| Outbox SUCCEEDED | احتفظ 7 أيام (الحالي) |
| Outbox شبكة فاشلة | لا سقف محاولات؛ backoff؛ UI إحياء |
| تقارير/metrics/plans | TTL أو آخر N بعد تأكيد الرفع |
| Post-training + frames | بعد SUCCEEDED أو 30 يومًا |
| Audio orphans | عند full **ودلتا** tombstones + عند logout |

### كيف تُحل التعارضات

| الكيان | القاعدة |
|---|---|
| Day customizations | حماية فقط مع pending outbox؛ بعد SUCCESS قارن `customizationsUpdatedAt` (أحدث يفوز) |
| Planned reports | متفائل محلي حتى SUCCESS؛ بعدها **server-wins** |
| Exercise preferences | canonical id دائمًا؛ pending يحمي الصف |
| 409 outbox | server-wins (الحالي) + rollback/mark-dirty للكاش المتفائل |
| Executions | idempotent بالـ client id (الحالي — حافظ عليه) |

---

## 4. قرارات معمارية مفتوحة (تحتاج قرار المالك)

1. **هل نحتفظ بـ outbox عبر انتهاء الجلسة؟** (موصى به بقوة لـ H01) — أم نصدّر لملف قبل المسح؟ أم نربط الصفوف بـ `userId` ونعيدها بعد login؟
2. **هل نوقف ازدواج workout export + training-config المخزّن؟** (A-R3) — اشتقاق عند القراءة يقلل الحجم؛ هل مسار `syncTrainingConfig` الشبكي ما زال يحتاج النسخة المخزّنة؟
3. **هل نفصل `plannedWorkoutReports` عن `/mobile/sync`؟** endpoint صفحات منفصل vs دلتا داخل sync — يؤثر على عقد الموبايل والاختبارات.
4. **هل guest uploads على جهاز مشترك مقبولة تجاريًا؟** تُنسب للحساب التالي بعد login — سياسة منتج لا تقنية فقط.
5. **قياس حمولة فعلي** (`curl` gzip على حساب حقيقي) لم يُنفَّذ في هذه الجولة — مطلوب قبل تقدير حدود المرحلة 2 بدقة.

---

## 5. فهرس تقارير المحاور

| ملف | المحور |
|---|---|
| [A-storage-size.md](./A-storage-size.md) | هيكل التخزين والحجم |
| [B-sync-correctness.md](./B-sync-correctness.md) | صحة المزامنة |
| [C-catalog-graph.md](./C-catalog-graph.md) | سلامة العلاقات |
| [D-outbox-reliability.md](./D-outbox-reliability.md) | موثوقية Outbox |
| [E-training-durability.md](./E-training-durability.md) | متانة جلسة التدريب |
| [F-read-path-ui.md](./F-read-path-ui.md) | مسار القراءة وحداثة UI |
| [G-refresh-triggers.md](./G-refresh-triggers.md) | محفزات وتوقيتات |
| [H-backend-payload.md](./H-backend-payload.md) | عقد الباك والحمولة |
| [I-auth-lifecycle.md](./I-auth-lifecycle.md) | دورة حياة الجلسة |
| [J-overrides-reports.md](./J-overrides-reports.md) | تخصيصات وتقارير |

### ملخص فرضيات Brief (H01–H23)

| مؤكدة | جزئية | منفية عمليًا |
|---|---|---|
| H01–H05, H07–H16, H18–H20, H22–H23 | H06 (نطاق Explore), H17, H21 | تغيير slug كمسار منتج (ضمن C) |

**نتائج جديدة بارزة:** E-restore (S1)، C-audio (S1)، I-refresh-any-failure (S1)، G-N1 عاصفة Android، A-N1 بقايا FS بعد logout، F-N1 innerRoute يبتلع dataRevision.

---

## 6. الخطوة التالية المقترحة

تنفيذ **المرحلة 1** كـ PR واحد أو سلسلة PRs صغيرة بالترتيب: **I (H01+refresh)** → **D (قفل+retry)** → **J (H04)** → **E (restore)** → **C (audio)** — ثم قياس حمولة حقيقي قبل المرحلة 2.
