# خطة إصلاح وتحسين طبقة البيانات — Movit Mobile (Offline-First)

**التاريخ:** 2026-07-09 · **الإصدار:** v1.1 (بعد مراجعة تحكيمية بثلاث عدسات: اكتمال التغطية، السلامة التقنية ضد الكود، التسلسل والمخاطر)
**المصادر:** `Data-Layer-Review-Brief.md` + التقرير الموحد وتقارير المحاور العشرة في `Docs/04-Research/data-layer-review/`
**الهدف:** تجربة مستخدم احترافية — تدريب لا يُفقد أبدًا، تطبيق سريع يعمل كاملًا أوفلاين، مزامنة خفيفة في التوقيت الصحيح، وتخزين لا يتضخم.

---

## 0. الملخص التنفيذي

المراجعة أثبتت أن المعمارية الحالية (SQLDelight JSON-blob + Outbox + SWR + Cold Seed) **صحيحة كأساس** ولا تحتاج إعادة بناء. المشكلة في **السياسات** المبنية فوقها: متى نمسح، متى نعيد المحاولة، من يفوز عند التعارض، ومتى ننزّل full.

الخطة مقسمة إلى **4 مراحل + مسار UX موازٍ**:

| المرحلة | الهدف | المدة التقديرية* |
|---|---|---|
| **P0 — أساس نظيف وشبكة أمان** | إنزال الـ diff الحالي بأمان + telemetry + قياس حمولة + بنية الـ migrations | أسبوع 1 |
| **P1 — وقف فقدان البيانات (S1)** | «تدريب يصل للباك بدون فقدان ولا تكرار» — 10 إصلاحات | أسابيع 2–5 |
| **P2 — الحمولة والأداء والاتساق (S2)** | دلتا حقيقية في كل مكان + GC + سرعة قراءة + حداثة UI | أسابيع 5–9 |
| **P3 — الجودة والتحصين (S3)** | ديون تقنية + عقود typed + توثيق سياسة البيانات | أسابيع 9–11 |
| **UX (موازٍ لـ P1/P2)** | سطح «بيانات معلقة»، استئناف جلسة، مؤشرات أوفلاين، شفافية | ضمن المراحل |

\* **افتراض التوظيف:** مطوّران موبايل (KMP) + مطوّر باك بنصف وقت. بمطوّر واحد فقط: أضف ~40% للمدد ونفّذ المسارات تسلسليًا.
**تعريف الجهد:** S = ≤ يوم عمل، M = 2–4 أيام، L = 5–8 أيام (شاملة الاختبارات).

**قاعدة ذهبية للتنفيذ كله:** كل بند يُسلَّم مع اختباراته — لا يُعتبر بند مكتملًا بدون اختبار regression يفشل قبل الإصلاح وينجح بعده.

---

## 1. مبادئ حاكمة (تُعتمد قبل البدء)

هذه القرارات تحل «القرارات المفتوحة» في التقرير الموحد، وهي أساس أكثر من بند:

| # | القرار | التوصية المعتمدة في هذه الخطة |
|---|---|---|
| PR-1 | مصير الـ outbox عند انتهاء الجلسة | **يُحتفَظ به.** الـ outbox يُربط بـ `owner_user_id` (عمود جديد). انتهاء الجلسة يمسح كاش القراءة والتوكنات فقط. عند login: صفوف نفس المستخدم تُستأنف؛ صفوف مستخدم مختلف تُحذف؛ صفوف الضيف (`owner_user_id=null`) تُنسب **بعد موافقة المستخدم** (UX.7) |
| PR-2 | ازدواج workout export + training-config | **يُلغى التخزين المزدوج.** الاشتقاق عند القراءة (المسار المفضل موجود `readWorkoutTrainingConfig`)؛ نسخة `SESSION_STORE` تبقى فقط للمسار الشبكي المباشر `syncTrainingConfig` |
| PR-3 | فصل `plannedWorkoutReports` عن `/mobile/sync` | **دلتا داخل sync أولًا** (`reportsUpdatedAfter` + وضع summary) — أرخص توافقيًا من endpoint منفصل، مع إبقاء full dump عند `forceRefresh` لإعادة التثبيت. **شرط ملزم:** يُشحن مع إصلاح الـ watermark (B-N2) في نفس الـ PR |
| PR-4 | Guest uploads على جهاز مشترك | تُنسب للحساب الأول الذي يسجّل دخولًا **بعد سؤاله صراحةً** (UX.7: «لديك N تدريبات قبل تسجيل الدخول — إضافتها لهذا الحساب؟»). احتفاظ بصفوف الضيف **30 يومًا** ثم حذف؛ logout صريح يحذفها |
| PR-5 | لا إعادة بناء معمارية | يبقى JSON-blob store + outbox. ممنوع إدخال Room/normalized schema في هذه الخطة. **استثناء واحد:** كشف `transaction {}` على `MovitLocalStore` (يلفّ SQLDelight transaction) لأن ذرية الـ full apply تستحيل بدونه |
| PR-6 | التعارض العام | **Pending outbox يفوز مؤقتًا؛ بعد SUCCEEDED السيرفر يفوز** — مع استثناء واحد: حقل التقرير الغني المحلي لا يُستبدل بملخص أفقر (دمج على مستوى الحقول — P1.9) |
| PR-7 | تقسيم `ClearScope` (يحسم تعارض I×A) | **كاش قراءة** = بلوبات الكتالوج/home/explore/metrics/الدلتا. **كتابات معمّرة** = outbox + journal + `post_training_report_*` وفهارسها + `frame_captures/<sessionId>/` **لأي تقرير رفعه غير مؤكد**. انتهاء الجلسة يمسح الأول فقط؛ logout الصريح (بعد التحذير) يمسح الاثنين |

---

## 2. المرحلة P0 — أساس نظيف وشبكة أمان (الأسبوع 1)

### P0.1 — PR#0: إنزال الـ diff الحالي بأمان 🔴 يحجب كل ما بعده
**يغطي:** H27، E-N4، وترتيب العمل الجاري (blocker من المراجعة التحكيمية)

الـ working tree الحالي يحتوي تعديلات كبيرة (nullable metrics في `TrainingApiDto`/`WorkoutUploadMapper`، إزالة شرط auth من `uploadWorkoutExecution` للضيف، تعديلات training-engine). **كل تقديرات P1 مبنية على الكود بعد هذا الـ diff** — يجب إنزاله أولًا كـ PR#0، بالترتيب التالي:

1. **الباك أولًا — إغلاق فجوة null (قبل نشر أي عميل يرسل null):**
   - **الإصلاح الأساسي:** migration يجعل `stability/alignmentAccuracy/avgStability/avgAlignmentAccuracy` أعمدة `Int?` في Prisma (توافقي خلفيًا — العملاء القدامى يرسلون قيمًا) + تحديث `workout-executions.types.ts` إلى `number | null` + استعلامات المتوسطات في التقارير/الأدمن تتجاهل null.
   - إن استحال شحن الـ migration فورًا: hotfix مؤقت `?? 0` مع **تسجيل تاريخ بدايته** — الصفوف في هذه النافذة ملوثة بأصفار زائفة وتُستثنى من المتوسطات لاحقًا. (لا تجعل الـ hotfix حلًا دائمًا.)
2. **قرار الضيف المؤقت:** guest enqueue المفعّل في الـ diff يعمل **قبل** وصول `owner_user_id` (P1.1) — في النافذة البينية بيانات الضيف مهددة بمسح session-expiry (H01 لم يُصلح بعد) وستُنسب بلا سؤال. القرار: **مقبول مؤقتًا** لأن الضيف كان محرومًا من الرفع أصلًا (لا تراجع)، لكن يُمنع الإعلان عن الميزة حتى P1.1 + UX.7.
3. ثم commit الـ diff كاملًا مع اختباراته.
- **اختبارات:** `WorkoutExecutionNullMetricsContractTest` (باك: null يُقبل ويُخزن null)، `WorkoutUploadMapperNullableMetricsTest.kt` (KMP: null يبقى null في JSON).
- **جهد:** M.

### P0.2 — بنية SQLDelight Migrations (أول migration في تاريخ المشروع)
**يغطي:** متطلب مسبق لـ P1.1/P1.2 (blocker تقني من المراجعة)

المشروع **لم يشحن أي schema migration من قبل** — لا ملفات `.sqm`، والـ drivers يبنون من `MovitDatabase.Schema` مباشرة (القاعدة المثبتة عند المستخدمين = version 1 ضمنيًا).

- أنشئ أول `1.sqm` يحمل **كل أعمدة P1 دفعة واحدة** (migration واحد، لا اثنين متتاليين):
  `ALTER TABLE outbox_entry ADD COLUMN owner_user_id TEXT; ALTER TABLE outbox_entry ADD COLUMN next_attempt_at_epoch_ms INTEGER;`
- فعّل `verifyMigrations` في Gradle + اختبار upgrade من fixture قاعدة version-1 على `AndroidSqliteDriver` و`NativeSqliteDriver`.
- حدّث كل التنفيذات: `SqlDelightMovitLocalStore` (insert/updateStatus الموضعية)، `InMemoryMovitLocalStore`، `MigratingMovitLocalStore`، وmodel `OutboxEntry`.
- **backfill عند أول تشغيل بعد الترقية:** صفوف outbox القائمة تُملأ `owner_user_id` من الجلسة الحالية إن وُجدت (هي ملك المستخدم المسجل بحكم التعريف) — يمنع تحويل صفوف مستخدمين حقيقيين إلى «ضيف» (blocker خصوصية من المراجعة). null يبقى فقط لصفوف أُنشئت فعلًا بدون جلسة.
- **متطلب مرافق:** إضافة `userId(): String?` إلى `MovitPlatformBindings` (غير موجود اليوم — `AuthSessionSnapshot.userId` يُخزَّن ولا يُقرأ في common) بتنفيذي Android/iOS.
- **جهد:** M. **مخاطر:** أعلى بند خطورة تشغيلية في الخطة — soft-launch إلزامي.

### P0.3 — Telemetry وتصنيف أخطاء (Sync + Outbox معًا)
**يغطي:** H13، B-N5، B-R7 + امتداد للـ dispatcher (من المراجعة)
- `MovitSyncOrchestrator`: أعد رمي `CancellationException`؛ صنّف: `Offline(network)` / `Error(decode)` / `Error(http)` / `Error(unknown)`.
- **`OutboxDispatcher` أيضًا:** ميّز `IOException/timeout` (شبكة حقيقية) عن استثناءات غير متوقعة (decode/mapping bug) — التصنيف يُغذّي سياسة P1.2.
- وسّع `MovitCacheFreshnessDiagnostics`: آخر دورة (`reason/isFull/updatedAfter/escalatedToFull/outcome`) + عدّادات (`sync_error_decode`, `outbox_failed_permanent_count`, `outbox_retry_exhausted`...). سطر log لكل replay بنتائجه.
- **اختبار:** `MovitSyncOrchestratorErrorClassificationTest.kt`.
- **جهد:** S–M.

### P0.4 — قياس حمولة فعلي (baseline)
- سكربت `curl --compressed` على حساب seed: full sync، دلتا فارغة، `/mobile/home`، `/mobile/explore` — bytes قبل/بعد gzip + عدد استعلامات Prisma (log middleware مؤقت). النتائج موثّقة في `Data-Layer-Execution-Report.md` §4.
- **تحقق أن ضغط gzip/br مفعّل فعلًا** على استجابات NestJS في الإنتاج وأن Ktor يرسل `Accept-Encoding` — مكسب مجاني كبير إن كان مغلقًا.
- **جهد:** S.

---

## 3. المرحلة P1 — وقف فقدان البيانات (S1) — أسابيع 2–5

> البنود 1.1–1.3 هي النواة وتدخل متتابعة (PR#2 يُبنى فوق PR#1). كل الفروع تُقطع بعد PR#0.

### P1.1 — دورة حياة الجلسة: لا مسح للـ outbox عند انتهاء الجلسة
**يغطي:** H01، I-N1، H19، I-N4، I-N2، I-N3، PR-7

**التصميم:**
1. **تمييز فشل الـ refresh** في `MovitHttpClientAuth.refreshAndPersist`:
   - `401/403` أو `success=false` من `/mobile/auth/refresh` → جلسة منتهية فعلًا → `handleSessionExpired`.
   - `5xx` / timeout / IO / parse → **ليست نهاية جلسة**: فشل retryable، التوكنات تبقى، الطلب الأصلي يفشل مؤقتًا.
2. **`ClearScope` حسب PR-7:**
   - `clearReadCaches()` — بلوبات القراءة + sync_metadata + audio manifest metadata. **لا يشمل** `post_training_report_*` غير مؤكدة الرفع ولا `frame_captures/` المرتبطة بها (تنتمي للكتابات المعمّرة — يحسم التعارض الذي رصدته المراجعة بين I وA).
   - `clearDurableWrites()` — outbox + journal + التقارير الغنية غير المؤكدة وملفاتها. يُستدعى فقط عند logout صريح (بعد P1.8) أو حذف حساب.
   - session expiry → `clearReadCaches()` + مسح توكنات فقط.
3. **ملكية الـ outbox:** `owner_user_id` (من P0.2) يُملأ عند enqueue من `MovitPlatformBindings.userId()`. `replayPending` يرفع صفوف المستخدم الحالي + صفوف الضيف المقبولة عبر UX.7. login بمستخدم مختلف عن `lastKnownUserId` → `clearReadCaches()` + حذف صفوف outbox المملوكة لغيره (I-N2). صفوف الضيف أقدم من **30 يومًا** تُحذف (PR-4).
4. **إزالة `runBlocking`:** `notifySessionExpired` تطلق coroutine على scope مخصص (`SupervisorJob + Dispatchers.IO`) يمسح ثم يُصدر حدثًا عبر `SharedFlow`؛ الـ shell يستهلكه في `viewModelScope` على Main (I-N4).
- **ملفات:** `MovitHttpClientAuth.kt`, `MovitData.kt`, `SqlDelightMovitLocalStore.kt`, `OfflineWriteQueue.kt`, `AccountSyncRepository.kt`, `MovitAppShellViewModel.kt`, `MovitDataModule.kt`, `MovitPlatformBindings.kt` + platform impls, `InMemoryMovitLocalStore.kt`, `MigratingMovitLocalStore.kt`.
- **اختبارات:** `RefreshServerErrorDoesNotExpireSessionTest`، `SessionExpiryPreservesOutboxTest`، **`SessionExpiryPreservesUnconfirmedReportsTest`** (تقرير + frames بانتظار رفع يبقيان بعد expiry)، `AccountSwitchClearsForeignCacheTest`، `GuestUploadAttributedAfterLoginTest` (مع بوابة UX.7).
- **جهد:** L. **مخاطر:** يعتمد على P0.2؛ اختبار upgrade path إلزامي.

### P1.2 — Outbox: قفل شامل (enqueue + replay) + IN_FLIGHT + retry بلا سقف للشبكة
**يغطي:** H02، H03، D-N1، D-N2، D-N3، D-N5

**التصميم:**
1. **`Mutex` واحد يغطي `replayPending` كاملة *و* مسار `enqueue` (الفحص existence + insert)** — المراجعة أثبتت أن enqueue بـ `INSERT OR REPLACE` بدون قفل يستطيع إرجاع صف SUCCEEDED إلى PENDING أثناء replay متوازٍ (إرسال مزدوج رغم قفل الـ replay وحده). داخل enqueue: استبدال مسموح **فقط** لصف FAILED_PERMANENT.
   - **دلالات القفل لكل محفز:** replay المنطلق من enqueue **ينتظر** القفل (suspend) — كي لا يفوّت تمرينٌ نافذةَ رفعه الفورية؛ محفزات connectivity/الدورية تستخدم `tryLock` وتتخطى إن كان replay جارٍ.
2. **تفعيل `IN_FLIGHT` فعليًا:** قبل `dispatch`: `updateOutboxStatus(IN_FLIGHT)`؛ بعد النتيجة → SUCCEEDED/PENDING/FAILED_PERMANENT. `recoverInFlightOutbox()` عند البدء يصبح ذا معنى بعد crash.
3. **سياسة retry جديدة** (تعتمد تصنيف P0.3 في الـ dispatcher):
   - شبكة/timeout/5xx → **لا سقف محاولات** + backoff exponential مع jitter (30s → 2m → 10m → 30m سقف) عبر عمود `next_attempt_at_epoch_ms` (من P0.2). المحاولة رقم 0 (بعد enqueue مباشرة) بلا انتظار.
   - 4xx (عدا 409) → `FAILED_PERMANENT` (كما هو) — مع سطح UX.6 (حالة «فشل»).
   - **استثناءات غير متوقعة** (decode/mapping — ليست شبكة): سقف عالٍ (50 محاولة) ثم FAILED_PERMANENT + عدّاد telemetry — يمنع حلقة أبدية على bug عميل.
   - يمرَّر الـ status الحقيقي إلى `shouldMarkPermanent` (إزالة `null` الدائم — D-N5).
4. **تبعية الجلسة (D-N3):** إضافة حقل اختياري `workoutGroupId: String? = null` إلى `PlannedWorkoutCompleteOutboxPayload` (default null — الصفوف القديمة في الطابور تظل قابلة للفك؛ kotlinx يتطلب default). يملؤه `TrainingSessionWriteHooks.completePlannedDay`. قبل إرسال complete: إن وُجدت executions PENDING لنفس `workoutGroupId` → أجّل للدورة التالية؛ إن كانت FAILED_PERMANENT → أرسل مع تحذير log. للصفوف القديمة (null): fallback «أي execution أقدم من هذا الـ complete ما زال pending».
- **ملفات:** `OfflineWriteQueue.kt`, `OutboxConflictPolicy.kt`, `OutboxDispatcher.kt`, `OutboxReplayOrdering.kt`, `OutboxPayloads.kt`, `TrainingSessionWriteHooks.kt`.
- **اختبارات:** `OfflineWriteQueueConcurrencyTest` (replay متوازيان + enqueue أثناء replay → لا إرسال مزدوج ولا إحياء SUCCEEDED)، `OfflineWriteQueueRetryExhaustionTest`، `OutboxBackoffSchedulingTest`، `OutboxDependencyOrderingTest`.
- **جهد:** M–L. **ملاحظة:** يُبنى فوق PR#1 (schema موجود مسبقًا من P0.2 — لا migration ثانٍ).

### P1.3 — Idempotency على الباك بمفتاح عملية العميل
**يغطي:** H02 (الشق الخادمي)

**تصحيح جوهري من المراجعة التحكيمية:** المفتاح الطبيعي `(userId, plannedWorkoutId)` **خاطئ** — لا unique constraint موجود أصلًا (upsert بـ find-then-create يظل racy)، و`plannedWorkoutId` يتكرر شرعيًا عند إعادة التدريب/إعادة الالتحاق بنفس البرنامج (كان سيقفل إعادة الإكمال للأبد).

**التصميم المعتمد:**
1. **مفتاح idempotency من العميل:** حقل اختياري `idempotencyKey` في أجسام `complete/start/override/plan-complete` = `operationId` الموجود أصلًا في صف الـ outbox. الموبايل يمرره من `OutboxDispatcher`.
2. **Prisma:** عمود `idempotencyKey String?` + `@@unique([userId, idempotencyKey])` على `PlannedWorkoutReport` (وnظيره للـ overrides) — **مع migration تنظيف المكررات القائمة قبل فرض الفهرس** (خطوة موثقة في الـ PR).
3. السلوك: طلب بمفتاح مسبوق → 200 بنفس النتيجة (بدون progression مكرر). طلب بلا مفتاح (عملاء قدامى) → fallback طبيعي **محدود بنافذة زمنية** (نفس اليوم التقويمي لنفس `plannedWorkoutId`) — يمتص أغلب التكرارات القديمة دون منع إعادة إكمال شرعية لاحقة.
4. `PLAN_COMPLETE`: no-op بـ 200 إن لا يوجد active slot. `START`: أعد استخدام تقرير `in_progress` القائم بنفس idempotencyKey أو نفس اليوم.
- **قرار منتج موثّق:** إعادة إكمال نفس اليوم المخطط في يوم تقويمي لاحق = مسموح وينشئ تقريرًا جديدًا.
- **ملفات:** `schema.prisma` (+2 migrations), `workout-executions.service.ts`, `mobile-planned-workouts.controller.ts`, `mobile-user-programs.controller.ts`, `active-plan.service.ts`, KMP: `OutboxDispatcher.kt`, DTOs.
- **اختبارات (باك):** `planned-workout-complete.idempotency.spec.ts` (متوازيان بنفس المفتاح → تقرير واحد؛ إعادة إكمال بيوم مختلف → تقرير جديد).
- **جهد:** L. **مخاطر:** تنظيف مكررات الإنتاج قبل الفهرس — dry-run على نسخة.

### P1.4 — قاعدة التعارض لتخصيصات الأيام + canonical id للتفضيلات
**يغطي:** H04، D-N4، **J-N1 (مرفوعة من P3 — S2 فقدان كتابة صامت)**

1. **hook نجاح عام:** `OutboxSuccessHooks.apply(type, payload)` يُنفَّذ داخل `replayPending` بعد SUCCEEDED (سيُستخدم أيضًا في P1.9/P2.8). لتخصيصات الأيام: أعد كتابة صف اليوم بـ `isUserModified=false`.
2. `hydrateFromBackend`: الحماية **فقط** مع pending/in-flight outbox لنفس `(userProgramId, week, day)`؛ غير ذلك قارن `serverCustomizationsUpdatedAt` بـ `lastModifiedAt` — الأحدث يفوز؛ التساوي = server-wins.
3. أزل الازدواج: حذف `applyCustomizationsToEffectivePlanCache` من `MobileWriteSyncRepository` (المراجعة تحققت أن `OfflineWriteOptimisticCache` يغطي المسارين فعلًا).
4. **J-N1:** `upsertExercisePreference/delete` يمرران **canonical exercise id** إلى الـ enqueue (اليوم يمرران الخام الذي قد يكون slug → الباك يبحث بالـ id فقط → 404 → FAILED_PERMANENT صامت). نفس التطبيع في `OfflineWriteOptimisticCache.applyExercisePreferenceUpsert`.
- **اختبارات:** `DayCustomizationConflictResolutionTest` (pending يحمي / بعد SUCCESS يُقبل الأحدث / الأقدم لا يكتب فوق أحدث)، `ExercisePreferenceOutboxCanonicalIdTest`.
- **جهد:** M.

### P1.5 — إصلاح استعادة جلسة التدريب بعد crash
**يغطي:** E-N1، E-N2، E-N3، H18

**تصحيح تصميمي من المراجعة:** `start(preserveCompleted=true)` غير كافٍ — `start()` يصفّر أيضًا `recordingStartMs` وعدّادات جودة الفريمات وbestVelocity، أي كل ما ضبطه `restore()` للتو (يفسد `durationMs` وSessionQualityMeta).

1. **التصميم المعتمد:** إن وُجد journal → `restore(snapshot)` **بدون استدعاء `start()` إطلاقًا** (`restore` يضبط `isRecording=true` و`setupJointIndices` بالفعل) + إصدار checkpoint فوري. بدون journal → `start()` كالمعتاد.
2. **Hydrate للواجهة:** مرّر `completedRepMetrics.size` للـ ViewModel ليضبط عدّاد العدات/الـ set (تجربة «استكمل من حيث توقفت»). العدة الجارية وقت الـ crash تُفقد بالتصميم (مقبول).
3. **Orphan policy:** عند فتح التدريب: `listActiveCheckpoints()` — جلسة أحدث من 6 ساعات لنفس التمرين → حوار استئناف (UX.3)؛ الأقدم → حذف.
4. **تبسيط `markCompleted`:** حذف كتابة COMPLETED العبثية — `delete` مباشرة.
- **ملفات:** `TrainingSessionWriteHooks.kt`, `MotionRecorder.kt`, `TrainingSessionViewModel.kt`, `SessionJournalStore.kt`.
- **اختبارات:** `TrainingSessionWriteHooksRestoreTest` (يفشل اليوم؛ يشمل **استمرارية `durationMs` وSessionQualityMeta** بعد الاستعادة)، `SessionJournalOrphanCleanupTest`، bilateral/multi-set بلا مضاعفة عدّ.
- **جهد:** M.

### P1.6 — إصلاح سقوط صوت الرسائل عند الدمج
**يغطي:** C-N1 (S1 — صوت التدريب)
- DTO جديد `SyncMessageContentDto(en, ar, audioEn?, audioAr?)` لحقل `content` في `SyncMessageTemplateDto` (لا تلمس `LocalizedNameDto` العام).
- `ExerciseMessageLibraryMerger.toLocalizedText()` ينسخ الصوت إلى `LocalizedText` (الباك يرسل `audioAr/audioEn` فعلًا — تحققت المراجعة).
- **اختبارات:** `ExerciseMessageLibraryMergerAudioTest.kt` + اختبار عقد في `core/network/contract`.
- **جهد:** M.

### P1.7 — متانة حزمة Explore (لا رفع في RAM فقط)
**يغطي:** H15
- **الرفع الفوري:** كل `finalizeCurrentExercise` في سياق Explore → enqueue فوري بنفس `workoutGroupId` (يُولَّد عند بدء الـ workout). يُلغى طابور RAM في `WorkoutExecutionBatchCoordinator`.
- **تدقيق المراجعة:** المسار عبر outbox يصل `saveWorkoutExecution` (upsert بالـ id، يقبل `context/workoutGroupId`) — يدعم الرفع الفردي. `updateUserStats` سيعمل مرة لكل تمرين بدل مرة للحزمة: **يعيد الحساب من aggregates (idempotent — لا مضاعفة)**، الكلفة استعلامات إضافية فقط؛ مقبولة، وإن أثبت القياس ضغطًا يُعمل debounce خادمي.
- **اختبار:** تكامل — Explore بـ 3 تمارين، kill بعد الثاني → صفّا outbox بنفس `workoutGroupId`.
- **جهد:** M.

### P1.8 — Logout آمن: flush أو تحذير
**يغطي:** I2
- قبل `clearLocalSession`: إن `pendingCount() > 0` → محاولة `replayPending()` (timeout قصير)؛ إن بقيت صفوف → حوار «لديك N تدريبات لم تُرفع. [رفع ثم خروج] [خروج وحذف] [إلغاء]» (UX.4). `deleteAccount` بصياغة أشد.
- **اختبار:** `LogoutFlushesOrWarnsOutboxTest`.
- **جهد:** S–M.

### P1.9 — تقارير: server-wins بدمج على مستوى الحقول + إصلاح streak
**يغطي:** H05، H16 — **بصياغة تصمد أمام P2.2** (تصحيح تسلسلي من المراجعة)

1. `hydrateFromSync` بقاعدة PR-6 المعدلة: pending/in-flight complete لنفس `plannedWorkoutId` → المحلي يبقى. غير ذلك → **دمج حقول**: السيرفر يفوز في `id` (الحقيقي)، `completedAt` (ISO)، والمقاييس المحسوبة؛ **حقل `report` الغني المحلي يُحفَظ** إن كانت حمولة السيرفر ملخصًا (summary mode من P2.2) أو بلا `report`. بهذا لا تُدمَّر تفاصيل الأوفلاين عندما يتحول العقد إلى summary لاحقًا.
2. الكتابة المتفائلة: `completedAt` بصيغة ISO من الآن (توحيد الصيغة).
3. `patchDashboardFromCompletion`: `daysTrained/currentStreak` يُحسبان من **مجموعة تواريخ** `readAllCachedPlannedWorkoutReports()` (أيام تقويمية فريدة) — يعمل لغير الـ Pro بلا سيرفر. أي انخفاض رقم بعد المصالحة يُطبق قبل العرض (compute-then-show) — لا «streak ينقص أمام عينيك» بلا تفسير.
- **اختبارات:** `ReportsSyncRepositoryHydrateTest` (+ حالة summary لا تمحو التقرير الغني — regression لـ P2.2)، `ReportsDashboardPatchTest`.
- **جهد:** M. **ملاحظة تزامن:** `ReportsSyncRepository` عليه diff جارٍ (weighted form score) — الفرع يُقطع بعد PR#0.

---

## 4. المرحلة P2 — الحمولة، الأداء، الاتساق (S2) — أسابيع 5–9

### مسار الشبكة والباك

### P2.1 — Endpoint خفيف للـ enrollments
**يغطي:** H07
- `GET /api/mobile/user-programs` يعيد `{ userPrograms: UserProgramExport[] }` (+`updatedAfter` اختياري). حوّل `fetchSyncUserPrograms` و`PlanSyncRepository` إليه. الحقل يبقى داخل sync للتوافق.
- **جهد:** S. **الأثر:** أسوأ مسار حمولة (full sync عند كل enroll) يختفي.

### P2.2 — دلتا لحمولة المستخدم + إصلاح الـ watermark (حزمة واحدة)
**يغطي:** H08، H24 (الشقان)، H26، B-N1، **B-N2/P3.9 (مسحوب هنا — شرط PR-3)**

> **قرار تسلسلي من المراجعة:** فلاتر دلتا جديدة فوق watermark غير آمن = توريث فقدان تحديثات. إصلاح الـ watermark يُشحن **في نفس PR**.

1. **Watermark آمن أولًا:** `timestamp = max(now, max(entity.updatedAt))` أو cursor `(updatedAt, id)` بدل strict-gt على `now` الملتقط في البداية (B-N2) + `mobile-sync.watermark.spec.ts`.
2. `plannedWorkoutReports`: فلتر `updatedAt > updatedAfter` + براميتر `includeReports=summary|full|none` — العميل الجديد يطلب `summary` (بلا حقل `report` الضخم؛ التفاصيل من `/mobile/reports/metrics` عند الفتح — والتقرير الغني للتدريبات الذاتية محفوظ محليًا وتحميه قاعدة الدمج P1.9).
3. `userPrograms`: فلتر `updatedAt > updatedAfter` في الدلتا.
4. `systemMessages`: تُرسل فقط عند full أو تغيّر `systemTplMax.updatedAt`. عكسيًا (B-N1): full بقائمة فارغة **يمسح** الكاش المحلي.
5. **الشق الثاني من H24 — audioManifest:** لا يُبنى ولا يُرسل في الدلتا إلا عند تغيّر fingerprint الرسائل/الأصوات (نفس بوابة systemMessages) — يوفر أيضًا file-size I/O المتكرر على السيرفر كل دورة.
6. `getPublishedForMobile`: عند دلتا، `select id` خفيف أولًا؛ الشجرة العميقة للـ ids المتغيرة فقط (H26).
- **ملفات:** `mobile-sync.service.ts`, `mobile-audio-manifest.service.ts`, `ReportsSyncRepository.kt`, `MovitSyncOrchestrator.kt`, `SystemMessageCache.kt`.
- **اختبارات:** `SyncPayloadBudgetContractTest`، fixture `sync-delta-empty-authenticated.json` (عقد ذهبي)، قياس مقابل baseline P0.4.
- **جهد:** L. **مخاطر:** كل الفلاتر خلف براميترات اختيارية — الغياب = السلوك القديم.

### P2.3 — Explore: دلتا افتراضيًا + توحيد الـ watermark
**يغطي:** H06، H23، F-N2
1. `SharedExploreRepository.refreshExploreContent` و`ProgramFlowSyncRepository.syncExplore` → `explore.sync()` (دلتا). `syncFull` حصريًا لزر «إصلاح الكتالوج» (UX.2) ولمسار drift.
2. بعد `exploreSync.applyFromSync` في الـ orchestrator: اكتب `syncResponse.timestamp` في `EXPLORE_LAST_SYNC` — طابع واحد متسق.
- **اختبارات:** `SharedExploreRepositoryRefreshPolicyTest`، `ExploreSyncWatermarkParityTest`.
- **جهد:** S.

### P2.4 — كاش السيرفر + ETag للـ Home
**يغطي:** H25، H29، J-N3
1. Redis (موجود وغير موصول): كاش `getGlobalMessageStats` + `systemMessages` snapshot + `meta.total*` — invalidate عند كتابة رسائل/كتالوج. دلتا فارغة: من ~20–25 استعلامًا إلى بضعة.
2. `/mobile/home`: ETag → 304؛ Ktor يرسل `If-None-Match` ويحتفظ بالكاش.
3. إصلاح جذر `HomeTrainModeHydrator` في الباك (trainMode صحيح بدل نداءين إضافيين من العميل).
- **جهد:** M.

### مسار التخزين المحلي

### P2.5 — GC شامل: `JsonCacheMaintenance` + تنظيف الـ filesystem
**يغطي:** H10، A-N1، I-N5، A-N4

مكوّن جديد يُستدعى من نهاية دورة sync ناجحة (+ cold start مرة يوميًا):

| الهدف | السياسة الدقيقة |
|---|---|
| `post_training_report_*` + فهارسها + `frame_captures/<sessionId>/` | **يُحذف فقط إذا:** العمر > 60 يومًا **و** لا يوجد صف outbox غير-نهائي (PENDING/IN_FLIGHT/FAILED قابل للإحياء) يشير إليه. **غياب الصف = رفع مؤكد** (صفوف SUCCEEDED تُطهَّر بعد 7 أيام — الغياب طبيعي). مفتاح الربط الموثق: `operationId == upload.id == report id` |
| `planned_workout_report_*` | آخر 90 تقريرًا أو 6 أشهر |
| `reports_metrics_*` / `reports_exercise_*` | سقف 50 مفتاحًا LRU حسب `updated_at_epoch_ms` (كشف العمود بـ query جديد في `JsonCacheEntry.sq`) |
| `effective_plan_*` | خارج (الأسبوع الحالي ± 1) للبرنامج النشط أو TTL 21 يومًا |
| `frame_captures/` orphans | مجلد جلسة بلا تقرير محلي يشير إليه |
| `audio_cache/` | orphan cleanup للدلتا أيضًا (حسب tombstones) لا full فقط (A-N4) |

- **تكامل مع PR-7:** حذف `frame_captures/`+`audio_cache/` عند **logout** يتم عبر `clearDurableWrites`/منفذ `MovitPlatformBindings.clearUserFiles()` — **ليس** عبر `clearReadCaches` (انتهاء الجلسة لا يحذف لقطات تقارير غير مرفوعة).
- **UX مرافق:** شاشة تفاصيل تقرير قديم تعرض placeholder إن غاب ملف اللقطة (وليس crash/فراغ) — اختبار على mapper التفاصيل.
- **اختبارات:** `JsonCacheMaintenanceTest` لكل سياسة + «تقرير غير مؤكد الرفع لا يُحذف مهما بلغ عمره» + حالة «صف SUCCEEDED مُطهَّر مسبقًا».
- **جهد:** M.

### P2.6 — إنهاء ازدواج الرسائل + إصلاح مضاعفة الدمج
**يغطي:** H11 (الشقان)، H09-ب

1. **إصلاح المضاعفة فورًا:** `resolvePoseVariantMessages` يستبدل القوائم بدل `add` تراكمي (كل دلتا رسائل حاليًا تضاعف نصوص feedback).
2. **الدمج عند القراءة فقط — بشرطين رصدتهما المراجعة:**
   - **بوابة القراءة الحالية لا تكفي:** `hasUnresolvedAssignments` يرجع false للـ configs المدموجة سلفًا عند كل المثبّتين — تعديل رسالة لن يُعاد دمجه أبدًا. الحل: **ختم إصدار مكتبة لكل سجل** (`messageLibraryFingerprint` مخزّن في `ExerciseConfigRecord`) — إن اختلف عن fingerprint المكتبة الحالي → إعادة resolve عند القراءة (مع LRU 8 يمتص التكلفة). يشمل ذلك إعادة كتابة كسولة تنظف الرسائل المدموجة قديمًا.
   - **البذرة الباردة يجب أن تزرع المكتبة** (تثبّتت المراجعة أنها تزرعها فعلًا في `ColdOfflineBundleSeeder` — أضف اختبار cold-install أوفلاين يثبت وصول نصوص/صوت الرسائل للمحرك).
3. **الشق الخادمي (يمنع fleet-wide full):** تعديل message template/assignment يلمس `exercise.updatedAt` للتمارين المتأثرة → الدلتا التالية تحملها بدل fingerprint-drift → full على كل الأجهزة.
- **اختبارات:** `ExerciseMessageLibraryMergerIdempotencyTest`، «config مدموج قديم + رسالة معدلة → القراءة ترجع النص الجديد» (regression أساسي)، cold-install test، اختبار باك للدلتا.
- **جهد:** L.

### P2.7 — ذرية الـ full sync عبر transaction + mutex + عدّادات drift صحيحة
**يغطي:** H14، H12، B-N3، G2-race (شق sync)

1. **Mutex/atomic** حول `beginSync/endSync` + طابور `SyncRequest(reason, mode)`. `TrainingConfigEnsure` عند `Skipped` ينتظر الدورة الجارية بدل اعتبارها فشلًا (B-N3).
2. **ذرية حقيقية:** المراجعة أثبتت أن «تبديل الفهرس» وحده ليس ذريًا (السجلات تُكتب فوق نفس المفاتيح بـ auto-commit لكل كتابة). الحل المعتمد (استثناء PR-5): كشف `transaction(block)` على `MovitLocalStore` يلفّ `database.transaction` في SQLDelight (وno-op passthrough في InMemory) — **الـ full apply كله (سجلات + فهارس + خرائط aliases) داخل transaction واحد**. مكسب جانبي: أسرع بكثير من N auto-commits.
3. **عدّادات drift من المصدر الصحيح:** `updateLocalEntityCounts` يقرأ workouts/programs من `catalogOffline.allWorkoutTemplateIds()/allProgramIds()` بدل explore blob (يفكك حلقة H12).
- **اختبارات:** `MovitSyncOrchestratorConcurrencyTest`، `MovitSyncOrchestratorCrashMidFullTest` (**يتحقق من اتساق الفهرس وخرائط الـ aliases، لا وجود السجلات فقط**)، `MovitSyncOrchestratorDriftLoopTest`.
- **جهد:** M.

### P2.8 — خريطة optimistic موحّدة + rollback
**يغطي:** D-R5، D-N4، E-N7
- جدول واحد في `OfflineWriteOptimisticCache`: لكل نوع → `applyOptimistic` / `onSuccess` (hooks P1.4) / `onServerWins` / `onPermanentFailure`.
- rollback الأدنى: فشل دائم لـ complete/plan → إزالة العلامة المتفائلة من home + علامة «فشل الرفع» (تغذي UX.6).
- `reportPlannedWorkout` لا يعلّم `isCompleted=true` (E-N7) أو يُحذف المسار من العميل.
- **جهد:** M.

### مسار القراءة والواجهة

### P2.9 — أداء القراءة: dispatcher + memoization + إصلاح O(n²)
**يغطي:** H22، A-N2، F-N3
1. `buildExerciseCatalog` يستخدم `exercise.imageUrl` من الـ explore الممرَّر مباشرة (يلغي N× فك بلوب — أكبر مكسب بأقل جهد).
2. القراءات الثقيلة تُغلَّف بـ `withContext(Dispatchers.IO)` في Shared repositories/SWR helpers.
3. **Memoized decode:** نسخة مفكوكة واحدة لـ `ExploreDataDto`/`HomeDataDto` داخل الـ repositories، تُبطَل عند أي `writeJson` لنفس المفتاح.
- **اختبارات:** `WorkoutSessionCatalogImageLookupTest` (spy: قراءة واحدة للبلوب)، قياس `FirstFrameMetric` قبل/بعد.
- **جهد:** S–M.

### P2.10 — حداثة الواجهة: dataRevision وإشارة موحّدة
**يغطي:** F4-freshness، F-N1، G1-gap، F-N4، F-N5
1. أزل شرط `currentInnerRoute != null` — التبويب الظاهر يُعاد تحميله عند `dataRevision` (debounce 300ms).
2. **إشارة `cacheInvalidated` (SharedFlow):** تُطلق بعد sync ناجح / optimistic write مؤثر / rollback / عودة من تدريب مكتمل. **الشاشات الداخلية الحساسة (Program Detail، Report Detail) تشترك فيها وتعيد التحميل أيضًا** — سيناريو F-N1 الفعلي هو مستخدم داخل شاشة داخلية، لا يكفي تحديث التبويب خلفها.
3. `localeRevision` يتبعه `load(isRefresh=false)` للتبويب الظاهر (F-N4).
4. تنظيف عقد `RefreshRequested` (يعالجه الـ VM) (F-N5).
- **اختبارات:** `MovitAppShellDataRevisionTest` (يشمل حالة inner-route)، `LocaleRevisionRemapTest`.
- **جهد:** M.

### P2.11 — سياسة محفزات موحّدة + قفل التدريب
**يغطي:** G-N1..G-N6، G3-no-guard، G4-constraints
1. **توحيد connectivity على Android:** مسار واحد — replay ثم طلب sync واحد عبر `ShellSyncCoordinator`؛ إزالة `requestNow()` من `onAvailable`.
2. **تسلسل cold start:** `bootstrapLocalCaches()` تُنتظر قبل `requestSyncIfNeeded()`.
3. **قفل تدريب:** علم `trainingSessionActive` — أثناء الجلسة: replay ودلتا مسموحان، **full refresh مؤجَّل**.
4. **`TrainingConfigEnsure` بلا full تلقائي إطلاقًا** (تشديد من المراجعة — G-R8 كاملة): عند miss → إعادة المحاولة عبر endpoint القالب المفرد (`syncTrainingConfig`) + رسالة UI؛ الـ full حصري لزر الإصلاح اليدوي وdrift detector، مع telemetry عند كل حالة «كان سيُصعّد».
5. بعد شراء Pro: `fetchProfile` → `reportsSync.syncDashboard()` + bump revision.
6. تغيير اللغة: prefetch أصوات اللغة الجديدة.
7. WorkManager: قيد `BatteryNotLow` للـ periodic؛ iOS: فحص `didExpire` داخل حلقات الـ sync.
8. حذف `onConnectivityRestored()` الميت + **توثيق سياسة المحفزات** (حدث → ماذا يُزامن) في `Docs/00-Active-Reference/`.
- **اختبارات:** `ConnectivityTriggerDedupTest`، `ShellColdStartOrderingTest`، `TrainingSessionSyncGateTest`، `ProPurchaseRefreshTest`، `TrainingConfigEnsureNoAutoFullTest`.
- **جهد:** M.

### P2.12 — إلغاء ازدواج التخزين المتبقي
**يغطي:** H09-أ/ج/د، A-N3، PR-2
1. **training-config المشتق:** إيقاف كتابته في `applyFromSync`؛ اشتقاق عند القراءة؛ تنظيف كسول للمفاتيح القديمة.
2. **الجورنال:** مصدر واحد = جدول SQL؛ إزالة الكتابة المزدوجة في json (قراءة fallback إصدارًا واحدًا ثم حذف).
3. **مفاتيح البرنامج:** `syncProgram` يطبّع slug → id قبل الكتابة + تنظيف كسول للمكرر.
- **جهد:** S–M لكل شق.

---

## 5. المرحلة P3 — الجودة والتحصين (S3) — أسابيع 9–11

| # | البند | يغطي | ملفات | جهد |
|---|---|---|---|---|
| P3.1 | HTTP status **typed**: `MovitApiException(status, body)` بدل regex `(\d{3})` | H17 | `MovitMobileApi.kt`, `OutboxDispatcher.kt` | S |
| P3.2 | `parseIsoToEpochMs` يدعم `Z/±HH:MM` + اختبار offsets | H20 | `DayCustomizationLocalStore.kt` | S |
| P3.3 | حذف fallback fingerprint المحلي؛ إن غابت stats السيرفر → لا كتابة | H21 | `TrainingConfigRepository.kt` | S |
| P3.4 | ربط `SyncCatalogGraphReport`: `!isComplete` بعد full → log/telemetry + جدولة backfill (مقيّد بالـ full) | C-N2 | `MovitSyncOrchestrator.kt` | S |
| P3.5 | تفعيل عقد `deletedExercise` (resolveExerciseMeta في exportPlannedWorkout) + فلترة/وسم في mappers الموبايل | C-N3 | `programs.service.ts`, mappers | M |
| P3.6 | `WorkoutExportMapper.mapExercise`: اسم حقيقي بدل `name = slug` | C-N5 | `WorkoutExportMapper.kt` | S |
| P3.7 | عقد slug ثابت: توثيق + حماية باك (رفض تغيير slug لمنشور) | C-N4 | `exercises.service.ts` + docs | S |
| P3.8 | إزالة الحقول الميتة `pausedAt/totalPausedDays` أو تصديرها فعليًا | H28 | DTO أو باك | S |
| P3.9 | ~~watermark الباك~~ **نُقل إلى P2.2** (شرط PR-3) | B-N2 | — | — |
| P3.10 | «سياسة البيانات» النهائية في `Data-Layer-Execution-Report.md` §1 + تحديث `KMP-Mobile-As-Built.md` | 3.10 | docs | S |
| P3.11 | حماية `IN_FLIGHT` في `pendingExerciseIdsFromOutbox` (J-N2) — *(J-N1 نُقلت إلى P1.4)* | J-N2 | `ExercisePreferenceLocalStore.kt` | S |
| P3.12 | حذف استدعاء `rekeyPostTraining` الميت (no-op دائم — الـ id لا يتغير) أو توثيق سبب بقائه | E-N5 | `TrainingSessionViewModel.kt` | S |

---

## 6. مسار UX الموازي — «تجربة احترافية» للأوفلاين

| # | العنصر | يعتمد على | التوقيت | الوصف |
|---|---|---|---|---|
| UX.1 | **شارة «بيانات معلقة»** | P1.2 | مع PR#6 | Chip في Home/Profile: `pendingCount` («3 تدريبات بانتظار الرفع») + لحظة تأكيد عابرة «تمت مزامنة كل شيء ✓» عند الوصول للصفر — الاختفاء الصامت لا يبني ثقة |
| UX.2a | **قسم «مزامنة» في الإعدادات — الأساس** | P1.2 | مع PR#6 | قائمة العناصر المعلقة/الفاشلة + «إعادة المحاولة» (reset→PENDING) + آخر مزامنة ناجحة + زر «إصلاح الكتالوج» (الـ full الوحيد يدويًا) + **آخر تحديث لكل شاشة** (من `updated_at_epoch_ms`) |
| UX.2b | **سطح الفشل والاسترجاع** | P2.8 | مع PR#12 | حالات rollback، تصدير بيانات جلسة خام كملاذ أخير، وسطر «مساحة التخزين + سياسة الاحتفاظ» (شفافية GC للـ P2.5 — حذف تقرير قديم بلا إفصاح يُقرأ كـ bug) |
| UX.3 | **حوار استئناف الجلسة** | P1.5 | مع PR#5 | «لديك جلسة سابقة (7 عدات) — استئناف أم بدء من جديد؟» |
| UX.4 | **تحذير الخروج** | P1.8 | مع PR#6 | حوار «N عناصر لم تُرفع» قبل logout/delete |
| UX.5 | **مؤشر أوفلاين لطيف** | — | مع PR#12 | Banner رفيع «تعمل دون اتصال — كل شيء يُحفظ محليًا» في شاشات التدريب/البرنامج |
| UX.6 | **حالة الرفع بثلاث قيم على التقرير** | P1.2 | مع PR#6 | ☁ معلق / ✓ مرفوع / ⚠ **فشل يحتاج انتباهك** — مربوطة بانتقال FAILED_PERMANENT مباشرة (وليس بـ P2.8) + **تنبيه على مستوى Home عند `failedCount>0`** بمسار فعل (إعادة/تواصل مع الدعم). الفشل الدائم الصامت هو أخطر ثغرة UX في النظام الحالي |
| UX.7 | **بوابة نسب بيانات الضيف** | P1.1 | مع PR#1 | عند أول login بعد نشاط ضيف: «لديك N تدريبات من قبل تسجيل الدخول — إضافتها لهذا الحساب؟ [إضافة] [حذف]» — لا نسب صامت على جهاز مشترك (PR-4) |

---

## 7. تحسينات أداء إضافية (من خارج نتائج المراجعة)

1. **تفعيل/التحقق من ضغط HTTP** (P0.4) — إن كان compression غير مفعّل خلف الـ proxy فهذا أكبر مكسب مجاني.
2. **قياس دوري في CI:** اختبار staging شهري يقيس full/دلتا مقابل baseline ويفشل عند تجاوز الميزانية — يمنع عودة التضخم.
3. **Cold-bundle refresh آلي:** ربط سكربت الـ bundle بـ CI ليتحدث مع كل تغيير كتالوج كبير.
4. **بعد استقرار P2 وبالقياس فقط:** صيغة أكثف للـ sync payload (CBOR/protobuf) إن ثبت أن JSON parsing عنق زجاجة على أجهزة دنيا.

---

## 8. مصفوفة تغطية النتائج (كل نتيجة → بند)

| نتيجة | خطورة | بند الخطة |
|---|---|---|
| H27, E-N4 + إنزال الـ diff | S1/S2 | **P0.1 (PR#0)** |
| بنية migrations + userId binding | متطلب | P0.2 |
| H13, B-N5 + تصنيف dispatcher | S2/S3 | P0.3 |
| H01, I-N1, H19, I-N4, I-N2, I-N3, PR-7 | S1 | P1.1 |
| H02, D-N1, D-N2, D-N3, D-N5, H03 | S1 | P1.2 (+P1.3 خادميًا) |
| H04, D-N4, **J-N1 (S2)** | S1/S2 | P1.4 |
| E-N1, E-N2, E-N3, H18 | S1/S2 | P1.5 |
| C-N1 | S1 | P1.6 |
| H15 | S2 | P1.7 |
| I2 (logout) | S1 | P1.8 |
| H05, H16 | S2 | P1.9 |
| H07 | S2 | P2.1 |
| H08, **H24 بشقيه (systemMessages + audioManifest)**, H26, B-N1, **B-N2** | S2 | P2.2 |
| H06, H23, F-N2 | S2 | P2.3 |
| H25, H29, J-N3 | S2/S3 | P2.4 |
| H10, A-N1, I-N5, A-N4 | S2 | P2.5 |
| H11, H09-ب | S2 | P2.6 |
| H14, H12, B-N3, G2-race | S2 | P2.7 |
| D-R5, E-N7 | S2 | P2.8 |
| H22, A-N2, F-N3 | S2 | P2.9 |
| F4-freshness, F-N1 (يشمل الشاشات الداخلية), F-N4, F-N5, G1-gap | S2/S3 | P2.10 |
| G-N1..G-N6, G3-no-guard, G4-constraints | S2/S3 | P2.11 |
| H09-أ/ج/د, A-N3, PR-2 | S2/S3 | P2.12 |
| H17 / H20 / H21 / C-N2 / C-N3 / C-N5 / C-N4 / H28 / J-N2 / E-N5 | S3 (C-N2/C-N3: S2) | P3.1–P3.8، P3.11، P3.12 |
| **منفية — لا إجراء:** B5-recursion (آمنة)؛ تغيير slug كمسار منتج (وقائيًا P3.7)؛ E-N6 (سلوك متوقع) | — | — |

---

## 9. ترتيب التنفيذ والتسليم (PRs)

```
الأسبوع 1:      PR#0: P0.1 (باك أولًا ثم commit الـ diff) → P0.2 (migration واحد بكل الأعمدة)
                → P0.3 + P0.4
الأسبوع 2-3:    PR#1: P1.1 (lifecycle + ملكية outbox + UX.7)          [موبايل A]
                PR#3: P1.3 (idempotency الباك — يبدأ مبكرًا لطول مدته)  [باك]
الأسبوع 3-4:    PR#2: P1.2 (قفل شامل + IN_FLIGHT + retry/backoff)      [موبايل A — فوق PR#1]
                PR#4: P1.4 + P1.9 (قواعد التعارض J + canonical id)     [موبايل B]
الأسبوع 4-5:    PR#5: P1.5 (استعادة الجلسة) + UX.3                     [موبايل B]
                PR#6: P1.6 + P1.7 + P1.8 + UX.1 + UX.2a + UX.4 + UX.6  [موبايل A+B]
   ✅ بوابة P1: كل الاختبارات خضراء + سيناريو «أسبوع الجيم» (القسم 10) يدويًا + يومان buffer
الأسبوع 5-7:    PR#7: P2.1 + P2.2 (شاملة الـ watermark) + P2.4          [باك]
                PR#8: P2.3 + P2.7                                       [موبايل A]
الأسبوع 7-8:    PR#9: P2.5 + P2.12                                      [موبايل B]
                PR#10: P2.6 (رسائل — فوق PR#8)                          [موبايل A]
الأسبوع 8-9:    PR#11: P2.9 + P2.10                                     [موبايل B]
                PR#12: P2.8 + P2.11 + UX.2b + UX.5                      [موبايل A]
   ✅ بوابة P2: إعادة قياس P0.4 — دلتا فارغة < 30KB gzip؛ full أقل 40%+؛ لا فقد في سيناريو القسم 10
الأسبوع 9-11:   P3.x + P3.10 (التوثيق النهائي) + معالجة ملاحظات الإنتاج
```

**قواعد الدمج:** كل PR يحمل اختباراته. PR#2 يُبنى فوق PR#1 (لا يُشحنان لمستخدمين بمهاجرتين منفصلتين — الـ schema كله في P0.2). تغييرات عقد الباك خلف براميترات اختيارية. Soft-launch لـ PR#0–PR#2 على نسبة صغيرة قبل التعميم.

---

## 10. سيناريو القبول الشامل (E2E يدوي قبل كل بوابة)

> «أسبوع الجيم بلا إنترنت» — يجب أن ينجح بالكامل بعد P1:

1. تثبيت جديد أوفلاين → تصفح الكتالوج من البذرة ✓
2. login أونلاين → sync أولي → قطع النت.
3. 4 جلسات تدريب على 4 أيام (منها واحدة تُقتل عمدًا منتصف التمرين → استئناف عند إعادة الفتح **بنفس عدد العدات ومدة صحيحة** ✓).
4. تعديل تخصيص يوم + تفضيل تمرين (بمعرّف slug عمدًا) أوفلاين.
5. محاولة logout → تحذير «بيانات معلقة» → إلغاء ✓.
6. عودة النت على شبكة متقلبة (فشلان ثم نجاح) → كل شيء يُرفع بلا تكرار (تحقق DB: تقرير واحد لكل يوم؛ التفضيل وصل رغم الـ slug) ✓.
7. انتهاء refresh token مصطنع مع عناصر معلقة → شاشة auth، **الـ outbox والتقارير الغنية ولقطاتها باقية**؛ login → الرفع يكتمل ✓.
8. تدريب كضيف ثم login → **يظهر سؤال النسب** (UX.7) ✓.
9. تعديل نفس اليوم من الأدمن → sync → الجهاز يعرض نسخة السيرفر (بعد رفع نسخته) ✓.
10. تعديل رسالة صوتية من الأدمن → دلتا (لا full) → النص والصوت الجديدان يعملان في التدريب ✓ (بعد P2.6).
11. فشل دائم مصطنع (4xx) → **تنبيه ظاهر على Home** + مسار إعادة/تصدير (UX.6) ✓.
12. فحص الحجم بعد أسبوع محاكى: `json_cache` + filesystem ضمن الميزانية (بعد P2.5) ✓.

---

## 11. المخاطر العامة وخطط التراجع

| خطر | التخفيف |
|---|---|
| **أول migration في تاريخ المشروع** (P0.2) على قاعدة مثبتة بلا رقم إصدار صريح | migration واحد يجمع كل الأعمدة؛ `verifyMigrations` + اختبار upgrade من fixture v1 على الـ drivers الاثنين؛ soft-launch |
| نافذة الضيف قبل P1.1 (الـ diff الحالي يفعّل guest enqueue الآن) | مقبولة بقرار موثق (P0.1)؛ لا إعلان عن الميزة قبل UX.7؛ backfill `owner_user_id` عند الترقية يمنع تحويل صفوف مستخدمين لضيوف |
| تنظيف مكررات `PlannedWorkoutReport` قبل فرض unique (P1.3) | dry-run على نسخة إنتاج + سكربت دمج موثق |
| تغيير سلوك retry يراكم outbox قديمًا | UX.1/UX.2a يعرضانه؛ jitter يوزع الضغط؛ حد 30 يومًا لصفوف الضيف فقط (PR-4) |
| إيقاف الدمج عند persist يترك configs مدموجة قديمة راكدة | ختم fingerprint لكل سجل + إعادة كتابة كسولة (P2.6) + اختبار «رسالة معدلة تصل لمثبّت قديم» |
| P2.2 summary يمسح التقرير الغني المحلي | قاعدة الدمج على مستوى الحقول في P1.9 من اليوم الأول + regression test في P2.2 |
| عقد الباك الجديد مع عملاء قدامى | كل شيء خلف براميترات اختيارية؛ fixtures ذهبية في CI للطرفين |
| transaction الجديد يخفي أخطاء كانت تظهر بالمسح الجزئي | telemetry P0.3 + `MovitSyncOrchestratorCrashMidFullTest` (فهارس + aliases) |
| جدول 10 أسابيع بفريق أصغر من المفترض | الافتراض معلن (2 موبايل + ½ باك)؛ بمطوّر واحد: +40% وتسلسل كامل — البوابات لا تُقفز |

---

## 12. سجل التنفيذ (Execution Log)

### الحالة العامة

| بند | الحالة | الوكيل | التاريخ | ملاحظات |
|---|---|---|---|---|
| **P0.1** — إنزال diff + فجوة null | **مكتمل** | وكيل P0.1/P0.2 | 2026-07-09 | الـ working tree كان نظيفًا (diff مدمج في `Extract Metrics`)؛ أُغلق null على الباك + اختبارات. commit `df6a7e04` |
| **P0.2** — أول SQLDelight migration | **مكتمل*** | وكيل P0.1/P0.2 | 2026-07-09 | `1.sqm` + `1.db` + `userId()` + backfill. *`verifyMigrations=false` على Windows (AccessDenied لـ sqlite-jdbc تحت `C:\WINDOWS`) — الاختبار يغطي upgrade |
| **P0.3** — Telemetry وتصنيف أخطاء | **مكتمل** | وكيل تصفية | 2026-07-10 | commit `f8f44b13` — diagnostics + orchestrator classification |
| **P0.4** — قياس حمولة baseline | **جزئي (توثيق)** | وكيل P0.4 + إغلاق فجوات | 2026-07-10 | موثّق في `Data-Layer-Execution-Report.md` §4؛ قياس curl حي على staging معلّق |
| **P1.1** — دورة حياة الجلسة | **مكتمل** | وكيل P1.1 | 2026-07-09 | ClearScope + refresh classification + outbox ownership + UX.7 gate + no runBlocking |
| **P1.2** — قفل outbox + IN_FLIGHT + retry | **مكتمل** | وكيل P1.1+P1.2 | 2026-07-10 | commit `e97ddfb0` — Mutex + IN_FLIGHT + backoff + dependency gate |
| **P1.4** — تعارض تخصيصات + canonical id | **مكتمل** | وكيل تصفية | 2026-07-10 | commit `0e1743e6` (+ `18851345` إصلاح assertions) |
| **P1.9** — دمج تقارير + streak | **مكتمل** | وكيل تصفية | 2026-07-10 | commit `0e1743e6` — field merge + ISO completedAt |
| **P1.3** — Idempotency باك + مفتاح outbox | **مكتمل** | وكيل P1.3 | 2026-07-09 | مفتاح عميل = `operationId`؛ لا unique على `(userId, plannedWorkoutId)` |
| **P1.5** — استعادة جلسة بعد crash | **مكتمل** | وكيل P1 بوابة | 2026-07-10 | commit `b7f2cb10` — journal API + restore بلا start + UX.3 |
| **P1.6** — صوت الرسائل عند الدمج | **مكتمل** | وكيل تصفية | 2026-07-10 | commit `c1f86bd0` |
| **P1.7** — متانة حزمة Explore | **مكتمل** | وكيل P1 بوابة | 2026-07-10 | enqueue فوري + حذف RAM batch؛ يتعايش مع P1.5 في نفس VM |
| **P1.8** — Logout آمن | **مكتمل** | وكيل تصفية | 2026-07-10 | commit `0404971d` — flush + UX.4 + سلاسل i18n |
| **UX.3** — حوار استئناف الجلسة | **مكتمل** | وكيل P1 بوابة | 2026-07-10 | مربوط بـ P1.5 (`resumePrompt` + أحداث Resume/Discard) |
| **UX.4** — تحذير logout مع pending | **مكتمل** | وكيل تصفية | 2026-07-10 | commit `0404971d` |
| **UX.7** — نسب ضيف بعد سؤال | **مكتمل** | وكيل P1.1 | 2026-07-09 | مع P1.1 |
| **بوابة P1** — جاهزية فتح P2 | **مكتمل (كود)** | وكيل إغلاق فجوات | 2026-07-10 | كل بنود P1 مُلتزَمة؛ 27+ اختبار مركّز PASS؛ سيناريو «أسبوع الجيم» اليدوي ما زال تشغيليًا |
| **P2.1** — Endpoint خفيف enrollments | **مكتمل** (مبكر) | وكيل تصفية | 2026-07-10 | commits `17d6461f` (باك) + `ace5ddb5` (KMP) |
| **P2.2** — دلتا حمولة مستخدم + watermark | **مكتمل** (مبكر) | وكيل Composer | 2026-07-10 | `0fed24c0` — watermark آمن + includeReports=summary + gated systemMessages/audioManifest |
| **P2.3** — Explore دلتا + watermark | **مكتمل** (مبكر) | وكيل تصفية | 2026-07-10 | commit `3cd56e35` |
| **P2.4** — كاش سيرفر + ETag Home | **جزئي** | وكيل P2.4/P3/UX | 2026-07-10 | ETag+If-None-Match + trainMode fallback؛ Redis **محجوب** (غير موصول بسهولة) |
| **P2.8** — optimistic موحّد + rollback | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | onSuccess/onServerWins/onPermanentFailure؛ لا isCompleted من report |
| **P3.1** — MovitApiException typed | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | status+body؛ OutboxDispatcher يفضّل typed |
| **P3.2** — parseIsoToEpochMs offsets | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | Z/±HH:MM + اختبار |
| **P3.3** — لا fingerprint محلي | **مكتمل*** | وكيل P2.4/P3/UX | 2026-07-10 | *في orchestrator مع P2.6 WIP — لا كتابة بدون server stats |
| **P3.4** — SyncCatalogGraphReport | **مكتمل*** | وكيل P2.4/P3/UX | 2026-07-10 | *log+diag بعد full؛ نفس ملف orchestrator مع P2.6 |
| **P3.5** — deletedExercise في export | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | resolveExerciseMeta في exportPlannedWorkout |
| **P3.6** — WorkoutExportMapper اسم | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | name من الباك + DTO |
| **P3.7** — رفض تغيير slug منشور | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | BadRequestException |
| **P3.8** — pausedAt/totalPausedDays | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | حُذفا من DTO العميل |
| **P3.10** — سياسة البيانات | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | مدموجة في `Data-Layer-Execution-Report.md` §1 |
| **P3.11** — IN_FLIGHT في pending prefs | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | listAllOutbox + PENDING/IN_FLIGHT |
| **P3.12** — rekeyPostTraining | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | يُستدعى فقط إن اختلف id |
| **UX.1** — شارة pending | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | Home chip + synced flash |
| **UX.2a** — قسم مزامنة | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | Profile: قائمة + retry + repairExploreCatalog |
| **UX.5** — banner أوفلاين | **مكتمل** | وكيل إغلاق فجوات | 2026-07-10 | training + Program Detail؛ سلسلة `training_offline_banner` |
| **UX.6** — حالة رفع + failed Home | **مكتمل** | وكيل P2.4/P3/UX | 2026-07-10 | تقرير + تنبيه Home |
| **P2.5** — JsonCacheMaintenance + GC | **مكتمل** | وكيل P2 المتبقي + إغلاق فجوات | 2026-07-10 | TTL/LRU + حماية outbox؛ `cleanupOrphanFrameCaptures` بعد sync ناجح |
| **P2.9** — قراءة: imageUrl + IO + memo | **مكتمل** | وكيل P2 المتبقي | 2026-07-10 | explore.imageUrl مباشر؛ SWR/SharedExplore على IO؛ memo Home/Explore |
| **P2.10** — dataRevision + cacheInvalidated | **مكتمل** | وكيل P2 المتبقي | 2026-07-10 | أُزيل شرط innerRoute؛ SharedFlow بعد sync/optimistic؛ locale→load؛ Program/Report Detail تشترك |
| **P2.11** — محفزات + قفل تدريب | **مكتمل*** | وكيل P2 المتبقي + إغلاق فجوات | 2026-07-10 | WorkManager periodic `BatteryNotLow`؛ باقي: iOS didExpire / prefetch أصوات لغة |
| **P2.12** — إلغاء ازدواج التخزين | **مكتمل** | وكيل P2 المتبقي | 2026-07-10 | لا كتابة training-config مشتق؛ journal SQL فقط (+ fallback هجرة)؛ syncProgram تحت program.id |

### تفاصيل دفعة P2.5 / P2.9–P2.12 — 2026-07-10

- **ما تغيّر:** `JsonCacheMaintenance` + timestamps query؛ orchestrator GC + `cacheInvalidated`؛ shell freshness؛ TrainingConfigEnsure بلا full؛ connectivity Android موحّد؛ P2.12 catalog/journal/program keys.
- **اختبارات:** `JsonCacheMaintenanceTest`، `TrainingSessionSyncGateTest`، `SyncCatalogOfflineDerivedConfigTest`، `SessionJournalStoreTest`، `WorkoutSessionCatalogImageLookupTest` — **PASS**؛ compile shell/library/training/reports **OK**.
- **لا push.**

### تفاصيل دفعة P2.4 / P2.8 / P3 / UX — 2026-07-10

- **محجوب / تجنّب:** Redis لكاش `getGlobalMessageStats` (BullMQ فقط؛ لا عميل Redis جاهز للمسار). ملفات P2.6 الجارية (`TrainingConfigRepository`, `ExerciseMessageLibraryMerger`, `ExerciseConfigModels`, cold-seed test) **لم تُلتزَم**. `MovitSyncOrchestrator` لمس خفيف لـ P3.3/P3.4 فوق WIP P2.6.
- **اختبارات:** `ParseIsoToEpochMsTest` + `OfflineWriteOptimisticCacheTest` — **PASS** (`testAndroidHostTest`).
- **لا push.**

### تفاصيل P0.1

- **ما تغيّر:** `schema.prisma` (`avgStability`/`avgAlignmentAccuracy`/`stability`/`alignmentAccuracy` → `Int?`)؛ migration `20260709210000_nullable_stability_alignment_metrics`؛ `workout-executions.types.ts` → `number | null`؛ `progression.service.ts` يتجاهل null في متوسط الاستقرار؛ اختبارات عقد باك + KMP mapper.
- **قرارات:** guest enqueue الموجود مقبول مؤقتًا (لا إعلان). لا hotfix `?? 0` — migration الصحيح.
- **اختبارات:** `workout-execution-null-metrics.contract.spec.ts` PASS؛ `WorkoutUploadMapperNullableMetricsTest` PASS (`testAndroidHostTest` + JBR 21).
- **commits:** `df6a7e04` — `fix(data): accept null stability/alignment metrics (P0.1)`

### تفاصيل P0.2

- **ما تغيّر:** `Outbox.sq` + `migrations/1.sqm` + fixture `1.db`؛ `OutboxEntry.ownerUserId`/`nextAttemptAtEpochMs`؛ `SqlDelightMovitLocalStore` + backfill؛ `MigratingMovitLocalStore.backfillOutboxOwnerFromSession`؛ `MovitPlatformBindings.userId()` + Android/iOS (+ تخزين `user_id` على iOS)؛ enqueue يملأ `ownerUserId`؛ `OutboxSchemaMigrationTest`.
- **قرارات:** `verifyMigrations` بقي `false` على هذا الجهاز (حظر استخراج native sqlite-jdbc) مع الإبقاء على `1.db`/`1.sqm` لإعادة التفعيل لاحقًا؛ اختبار upgrade عبر `JdbcSqliteDriver` + `Schema.migrate(1→2)`.
- **اختبارات:** `OutboxSchemaMigrationTest` PASS؛ `OfflineWriteQueueTest` PASS.
- **commits:** `66a85a1e` — `feat(data): first SQLDelight outbox migration with owner_user_id (P0.2)`
  - ملاحظة: ضُمّ الحد الأدنى من ملفات تصنيف/telemetry (`SyncFailureClassifier`, `MovitSyncTelemetry`, مفاتيح diag) لأن `OfflineWriteQueue`/`OutboxDispatcher` كانا يعتمدان عليها من WIP P0.3 في نفس الشجرة.

### تفاصيل P0.3

- **الحالة:** مكتمل (وكيل P0.3 — Jul 9, 2026).
- **الملفات المتغيرة:**
  - `kmp-app/core/data/.../sync/SyncFailureClassifier.kt` (جديد) — تصنيف `Network` / `Decode` / `Http` / `Unknown`؛ إعادة رمي `CancellationException`.
  - `kmp-app/core/data/.../sync/MovitSyncTelemetry.kt` (جديد) — آخر دورة sync + عدّادات أخطاء في `sync_manager_prefs`.
  - `kmp-app/core/data/.../sync/MovitSyncOrchestrator.kt` — لا ابتلاع عام لـ `Throwable`؛ `Offline` للشبكة/5xx مع كاش؛ `Error(kind)` للـ decode/4xx/unknown؛ تسجيل telemetry لكل دورة.
  - `kmp-app/core/data/.../outbox/OutboxDispatcher.kt` + `OutboxModels.kt` — `RETRYABLE_NETWORK` vs `RETRYABLE_UNEXPECTED` (بدل `RETRYABLE` الواحد).
  - `kmp-app/core/data/.../outbox/OfflineWriteQueue.kt` — سطر log لكل replay؛ عدّادات `outbox_failed_permanent` / `outbox_retry_exhausted`.
  - `kmp-app/core/data/.../cache/MovitCacheFreshnessDiagnostics.kt` + `MovitCacheKeys.kt` — حقول `lastSyncCycle` و`errorCounters` في التقرير و`toLogLine()`.
  - `kmp-app/core/data/.../sync/MovitSyncOrchestratorErrorClassificationTest.kt` (جديد).
- **الاختبارات ونتيجتها:** `./gradlew :core:data:testAndroidHostTest` — **BUILD SUCCESSFUL** (يشمل الاختبار الجديد + `MovitSyncOrchestratorTest` + `MovitCacheFreshnessDiagnosticsTest` + `OfflineWriteQueueTest`).
- **commits:** `f8f44b13` — `feat(data): sync telemetry and error classification (P0.3)` (+ `60cb4c4a` إصلاح compile اختبار hydration)

### تفاصيل P0.4

- **قياس حي:** مرفوض — يُعاد على staging بعد `seed` (`alustadh.manager@gmail.com` / `password`).
- **أكبر فجوة مؤكدة:** `systemMessages` + `plannedWorkoutReports` + `audioManifest` تُعاد في كل sync حتى «دلتا فارغة» — يخالف هدف بوابة P2 (&lt; 30 KB gzip).
- **مكسب سريع:** تفعيل gzip على الباك أو nginx قبل P2.2.
- **توثيق:** [`Data-Layer-Execution-Report.md`](Data-Layer-Execution-Report.md) §4

### تفاصيل P1.1

- **الحالة:** مكتمل (وكيل P1.1 — Jul 9, 2026).
- **ما تغيّر:**
  - `MovitHttpClientAuth.kt` — `RefreshOutcome` يميّز SessionExpired (401/403/`success=false`) عن TransientFailure (5xx/IO/parse).
  - `MovitLocalStore` + SQLDelight/InMemory/Migrating — `clearReadCaches` / `clearDurableWrites` (PR-7)؛ استعلامات ملكية outbox + guest retention.
  - `MovitData.kt` — إزالة `runBlocking`؛ `SupervisorJob+IO` + `sessionExpiredEvents` SharedFlow؛ `onAuthenticatedSession` + بوابة UX.7.
  - `OfflineWriteQueue` — replay لصفوف المستخدم الحالي فقط؛ الضيف بعد `GuestOutboxAttributionGate.accept`.
  - `AccountSyncRepository` — يستدعي `onAuthenticatedSession` بعد login/register/google.
  - `MovitAppShellViewModel` — يستهلك `sessionExpiredEvents` على Main عبر `viewModelScope`.
  - Android/iOS — `clearUserFiles()` لمجلدات `frame_captures`/`audio_cache` (logout فقط).
- **قرارات:** نسب الضيف ليس صامتًا — UI يستعلم `pendingGuestOutboxPrompt()` ثم `accept`/`discard`. `AUTH_LIFECYCLE_STORE` يبقى عبر session expiry. Logout ما زال `clearAllUserData` (P1.8 لاحقًا للتحذير).
- **اختبارات:** `MovitHttpClientAuthTest.refreshServerErrorDoesNotExpireSession`؛ `SessionExpiryPreservesOutboxTest`؛ `SessionExpiryPreservesUnconfirmedReportsTest`؛ `AccountSwitchClearsForeignCacheTest`؛ `GuestUploadAttributedAfterLoginTest`؛ `OfflineWriteQueueTest` + `MovitDataClearAllUserDataTest` — PASS (`testAndroidHostTest`).
- **commits:** `ad8870b4` — `feat(data): preserve outbox across session expiry (P1.1)`

### تفاصيل P1.2

- **الحالة:** مكتمل (وكيل P1.1+P1.2 إكمال بعد timeout — Jul 10, 2026). **P1.1** كان مكتملًا مسبقًا (`ad8870b4`) ولم يُعدْ لمسه.
- **ما تغيّر:**
  - `OfflineWriteQueue` — `Mutex` واحد لـ enqueue+replay؛ استبدال صف فقط إن `FAILED_PERMANENT`؛ `IN_FLIGHT` قبل dispatch؛ `OutboxReplayAcquisition.Wait` (enqueue) vs `TrySkipIfBusy` (connectivity/periodic).
  - `OutboxConflictPolicy` — شبكة/5xx بلا سقف + backoff عبر `next_attempt_at_epoch_ms`؛ unexpected سقف 50؛ 4xx → permanent؛ يمرَّر outcome الحقيقي لـ `shouldMarkPermanent`.
  - `OutboxDependencyGate` + `PlannedWorkoutCompleteOutboxPayload.workoutGroupId` — تأجيل complete/report حتى executions نفس المجموعة (أو أقدم للصفوف القديمة null).
  - تمرير `workoutGroupId` عبر `MobileWriteSyncRepository` / `TrainingSessionWriteCoordinator` / `TrainingSessionWriteHooks` / `TrainingSessionViewModel.finalizePlannedWorkoutDay`.
  - Connectivity Android/iOS → `TrySkipIfBusy`؛ `updateOutboxStatus(..., nextAttemptAtEpochMs)`.
  - اعتماد compile على `OutboxSuccessHooks` / `OutboxPendingScan` (من P1.4) بعد SUCCEEDED.
- **قرارات:** الافتراضي لـ `replayPending()` = `TrySkipIfBusy`. Logout flush (P1.8) يستخدم `Wait` — بقي في working tree غير مُدمَج في هذا الـ commit إن وُجد تداخل.
- **اختبارات:** `OfflineWriteQueueConcurrencyTest`، `OfflineWriteQueueRetryExhaustionTest`، `OutboxBackoffSchedulingTest`، `OutboxDependencyOrderingTest`، `OfflineWriteQueueTest` — **PASS** (`testAndroidHostTest` + JBR 21).
- **commits:** `e97ddfb0` — `feat(data): harden outbox mutex, IN_FLIGHT, and retry policy (P1.2)`

### تفاصيل P1.4

- **الحالة:** مكتمل (وكيل Composer — PR#4 / Jul 9, 2026).
- **ما تغيّر:**
  - `OutboxSuccessHooks.kt` (جديد) + `OutboxPendingScan.kt` (جديد) — hook بعد `SUCCEEDED` يصفّر `isUserModified` لتخصيصات الأيام؛ مسح pending/in-flight للحماية أثناء hydrate.
  - `DayCustomizationLocalStore` — `hydrateFromBackend` (suspend): حماية فقط مع outbox pending/in-flight لنفس `(userProgramId, week, day)`؛ غير ذلك مقارنة `customizationsUpdatedAt` vs `lastModifiedAt` (الأحدث يفوز؛ التعادل = server-wins)؛ `markServerAcknowledged` + `formatEpochMsToIsoUtc`.
  - `MobileWriteSyncRepository` — حذف `applyCustomizationsToEffectivePlanCache` (يغطيه `OfflineWriteOptimisticCache` عند enqueue)؛ canonical exercise id في enqueue upsert/delete.
  - `OfflineWriteOptimisticCache` — تطبيع canonical id لتفضيلات التمارين.
  - `OfflineWriteQueue` — استدعاء `OutboxSuccessHooks.apply` بعد `SUCCEEDED` (كان موصولًا جزئيًا من P1.1).
  - `MovitSyncOrchestrator` — يمرّر `pendingDayKeys` / `pendingPlannedWorkoutIds` إلى hydrate.
- **اختبارات:** `DayCustomizationConflictResolutionTest`، `ExercisePreferenceOutboxCanonicalIdTest`.
- **commits:** `0e1743e6` — `feat(data): day customization conflicts and report field merge (P1.4, P1.9)`؛ `18851345` — إصلاح assertions اختبار

### تفاصيل P1.9

- **الحالة:** مكتمل (وكيل Composer — PR#4 / Jul 9, 2026).
- **ما تغيّر:**
  - `ReportsSyncRepository.hydrateFromSync` — دمج حقول PR-6: pending/in-flight complete يحمي المحلي؛ السيرفر يفوز في `id`/`completedAt`/المقاييس؛ `report` الغني المحلي يُحفظ إن حمولة السيرفر ملخص/بلا `report`.
  - `recordPendingPlannedWorkoutCompletion` — `completedAt` بصيغة ISO-8601 UTC (`formatEpochMsToIsoUtc`).
  - `patchDashboardFromCompletion` — `daysTrained`/`currentStreak` من أيام تقويمية فريدة في `readAllCachedPlannedWorkoutReports()` (compute-then-show).
- **اختبارات:** `ReportsSyncRepositoryHydrateTest` (+ regression summary لا تمحو التقرير الغني)، `ReportsDashboardPatchTest`.
- **commits:** `0e1743e6` (مع P1.4)؛ `18851345` — إصلاح assertions
- **قرارات:** `mergeReportFromServer` / `computeCurrentStreakFromUtcDays` exposed كـ `internal` في companion للاختبار فقط.

### تفاصيل P1.3

- **ما تغيّر:**
  - Prisma: `PlannedWorkoutReport.idempotencyKey` + `@@unique([userId, idempotencyKey])`؛ `UserProgramOverride.idempotencyKey` + `@@unique([userProgramId, idempotencyKey])`.
  - Migration: `20260709220000_planned_workout_report_idempotency` (عمود + تنظيف مكررات غير-null + unique index؛ NULL مسموح متعددًا في Postgres).
  - Dry-run: `backend/prisma/scripts/p1-3-idempotency-dedupe-dry-run.mjs` (`--apply` يصفّر المفاتيح الأقدم).
  - باك: `start`/`complete` يعيدان نفس الصف بمفتاح مسبوق؛ بلا مفتاح → fallback نفس اليوم التقويمي UTC لنفس `plannedWorkoutId`؛ CAS `updateMany(status=in_progress)` يمنع progression مزدوج؛ `PLAN_COMPLETE` no-op 200 بلا active slot؛ override create يعيد الصف الموجود بنفس المفتاح.
  - موبايل: `OutboxDispatcher` يمرّر `entry.id` كـ `idempotencyKey` لـ start/complete/report/override/plan-complete؛ DTOs اختيارية.
- **قرارات:** لا unique طبيعي على `(userId, plannedWorkoutId)` — إعادة إكمال في يوم تقويمي لاحق مسموحة. مفتاح قصير (&lt;8) يُتجاهل.
- **اختبارات:** `planned-workout-complete.idempotency.spec.ts` — **PASS** (5). `prisma generate` OK.
- **عوائق:** `prisma migrate deploy` فشل محليًا — `DATABASE_URL` غير مضبوط في `.env` (datasource.url مطلوب). طبّق الـ migration على staging/prod بعد dry-run.
- **commits:** `cd3fc6c7` — `feat(data): server idempotency via client outbox keys (P1.3)`

### تفاصيل P1.5

- **الحالة:** مكتمل (وكيل P1.5 — Jul 10, 2026). منطق الاستعادة جاهز؛ **لا commit محلي** لأن `TrainingSessionViewModel` تداخل مع حذف RAM batch من P1.7 في نفس الشجرة.
- **ما تغيّر:**
  - `TrainingSessionWriteHooks.attach` — إن وُجد journal → `restore` + checkpoint فوري **بلا** `start()`؛ وإلا `start()` كالمعتاد؛ يعيد `completedRepMetrics.size`.
  - `TrainingSessionViewModel` — hydrate `repCount`/`progressPercent` + `engine.seedCompletedRepCount`؛ orphan prompt (UX.3) عبر `cleanupOrphansAndFindResume`؛ أحداث `ResumePriorSession` / `DiscardPriorSession`.
  - `SessionJournalStore` — `markCompleted` = `delete` مباشرة (H18)؛ `cleanupOrphansAndFindResume` يحذف >6 ساعات ويعيد أحدث journal لنفس التمرين.
  - `RepCounter.seedCompletedCount` + `MovitTrainingEngine.seedCompletedRepCount` — بذرة عدّاد الواجهة بعد الاستعادة.
  - UI: حوار استئناف في `TrainingSessionScreen` + سلاسل `training_session_resume_prior_*`.
  - `TrainingSessionWriteCoordinator` — `deleteJournal` / `cleanupOrphansAndFindResume` / `listActiveJournals`.
- **قرارات:** العدة الجارية وقت الـ crash تُفقد (متوقع). حوار الاستئناف يوقف `onExerciseLoaded` حتى اختيار المستخدم. نفس `sessionId` الحالي يُستعاد عبر attach بعد الموافقة (إعادة كتابة journal إن اختلف المعرف).
- **اختبارات:** `TrainingSessionWriteHooksRestoreTest` (2) **PASS**؛ `SessionJournalOrphanCleanupTest` (4) **PASS** (`testAndroidHostTest`).
- **commits:** لم يُنشأ — الشجرة غير معزولة (`TrainingSessionViewModel` يخلط P1.5 + حذف `WorkoutExecutionBatchCoordinator` من P1.7).

#### إصلاح بوابة P1 (إعادة وصل بعد فقدان coordinator) — 2026-07-10

- **المشكلة:** أثناء commit P1.2 أُعيد `TrainingSessionWriteCoordinator` إلى HEAD وفُقدت دوال journal (`deleteJournal` / `cleanupOrphansAndFindResume` / `listActiveJournals`) رغم بقائها في `SessionJournalStore` وUI/hooks الجزئية.
- **ما أُصلح:**
  - إعادة دوال journal على `TrainingSessionWriteCoordinator`.
  - `TrainingSessionWriteHooks.attach` — restore بلا `start()` + ctor اختباري (`readJournal`/`checkpointJournal`)؛ يعيد عدد العدات المستعادة.
  - `TrainingSessionViewModel` — UX.3 + `seedCompletedRepCount` + تعايش مع P1.7 (enqueue فوري، بلا `WorkoutExecutionBatchCoordinator`).
- **اختبارات (JBR Android Studio):** `SessionJournalOrphanCleanupTest` + `ExploreWorkoutGroupIdOutboxTest` + `TrainingSessionWriteHooksRestoreTest` — **PASS**؛ `:feature:training:compileAndroidMain` **OK**.
- **commits:** `b7f2cb10` — `fix(training): rewire P1.5 journal restore with P1.7 immediate enqueue`

### تفاصيل P1.7

- **الحالة:** مكتمل (وكيل Composer — Jul 10, 2026).
- **ما تغيّر:**
  - `WorkoutFlowCache.ensureWorkoutGroupId()` — يُولَّد عند بدء الـ workout run (`StartWorkout` في `MovitLibraryRoutes`).
  - `MovitInnerHost.resolveTrainingUploadContext()` — يمرّر `WorkoutUploadContext` لمسارات explore/multi-exercise (`flowItems` + `workoutId`).
  - `TrainingSessionViewModel` — كل `finalizeCurrentExercise` → `enqueueExecutionUpload` فوري بنفس `workoutGroupId`؛ حُذف `WorkoutExecutionBatchCoordinator` وطابور RAM.
  - `WorkoutUploadContext.EXPLORE_WORKOUT_CONTEXT` — ثابت السياق نُقل من الـ coordinator المحذوف.
- **قرارات:** `updateUserStats` مرة لكل تمرين (idempotent على الباك — مقبول per الخطة). برامج planned ما زالت تستخدم `plannedWorkoutId` كـ group id.
- **اختبارات:** `ExploreWorkoutGroupIdOutboxTest` — 3 executions → 3 صفوف outbox بنفس `workoutGroupId`.
- **commits:** لم يُنشأ (اختياري).

#### توحيد مع P1.5 في نفس ViewModel — 2026-07-10

- حُذف مسار `exploreBatch` / `flushExploreBatchIfNeeded` نهائيًا من VM؛ `enqueueUpload` دائمًا فوري.
- لا تعارض مع استعادة journal: attach/restore يحدث عند `StartEngine` بعد موافقة UX.3؛ الرفع يبقى outbox-first.

### ملاحظة بوابة P1 (2026-07-10 — بعد تصفية commits)

| تحقق | النتيجة |
|---|---|
| اختبارات وحدة P1.5/P1.7 المركّزة | **PASS** (`SessionJournalOrphanCleanupTest`, `TrainingSessionWriteHooksRestoreTest`, `ExploreWorkoutGroupIdOutboxTest`) |
| اختبارات مركّزة P0.3/P1.4/P1.6/P1.8/P1.9/P2.1/P2.3 | **PASS** — 27 اختبار (`:core:data:testAndroidHostTest` subset) |
| تجميع `core:data` + `feature:training` (androidMain) | **OK** (سابقًا JBR) |
| سيناريو «أسبوع الجيم» اليدوي (القسم 10) | **لم يُنفَّذ** |
| commits لكل بنود P1 في الشجرة | **مكتمل** — انظر دفعة التصفية أدناه |
| P2.2 | **مكتمل** محليًا (`0fed24c0`) — بلا push |
| حكم فتح P2 كبوابة رسمية | **لا** — حتى يُمرَّر السيناريو اليدوي |

### تفاصيل P1.8

- **الحالة:** مكتمل (وكيل Composer — Jul 10, 2026).
- **ما تغيّر:**
  - `LogoutOutboxPreparation` + `AccountSyncRepository.prepareLogout()` — flush قصير (3s) عبر `replayPending`؛ `logout`/`deleteAccount` يقبلان `discardPendingOutbox`.
  - `MovitDataModule` — حقن `OfflineWriteQueue` في `AccountSyncRepository`.
  - `ProfileRepository` / `SharedProfileRepository` / `MovitProfileViewModel` — UX.4: `ProfilePicker.LogoutPendingOutbox` و`DeleteAccountPendingOutbox` مع حوار placeholder في `MovitProfilePickers`.
  - سلاسل `profile_logout_pending_*` و`profile_delete_pending_*` في `MovitEnglishStrings`.
- **قرارات:** لم تُمسّ mutex/IN_FLIGHT (P1.2) ولا ClearScope (P1.1). `prepareLogout` يستدعي `pendingCount`/`replayPending` الموجودين فقط.
- **اختبارات:** `LogoutFlushesOrWarnsOutboxTest` — warn عند pending بعد flush؛ block بدون discard؛ نجاح مع `discardPendingOutbox=true`.
- **commits:** `0404971d` — `feat(account): safe logout with outbox flush warning (P1.8)`

### تفاصيل P1.6

- **ما تغيّر:** `SyncMessageContentDto` (جديد) — `en`/`ar` + `audioEn?`/`audioAr?`؛ `SyncMessageTemplateDto.content` يستخدمه بدل `LocalizedNameDto`؛ `ExerciseMessageLibraryMerger.toLocalizedText()` ينسخ الصوت إلى `LocalizedText`.
- **قرارات:** لم يُوسَّع `LocalizedNameDto` العام؛ `SyncSystemMessageDto` بقي على `LocalizedNameDto` (خارج نطاق C-N1).
- **اختبارات:** `ExerciseMessageLibraryMergerAudioTest`؛ `DtoPayloadContractTest.syncMessageLibraryFixture_parsesAudioUrlsInContent` — ضمن subset **PASS**.
- **commits:** `c1f86bd0` — `fix(data): preserve message audio URLs in sync merge (P1.6)`

### تفاصيل P2.1

- **الحالة:** مكتمل (وكيل Composer — Jul 10, 2026).
- **ما تغيّر:**
  - **باك:** `GET /api/mobile/user-programs` في `mobile-user-programs.controller.ts` — يعيد `{ success, userPrograms }` مع `updatedAfter` اختياري؛ `programService.listUserProgramsForMobile` بنفس شكل `UserProgramExport` في sync.
  - **KMP:** `UserProgramsApiResponse` + `MovitMobileApi.fetchUserPrograms`؛ `fetchSyncUserPrograms` يفوّض إليه (لم يعد يستدعي `/mobile/sync`).
  - `PlanSyncRepository` — enroll/refresh يستخدمان endpoint الخفيف؛ دلتا عبر `updatedAfter` من كاش enrollments عند refresh الجزئي.
- **قرارات:** `userPrograms` تبقى داخل `/mobile/sync` للتوافق؛ لم يُمسّ `mobile-sync.service.ts`.
- **اختبارات:** `mobile-user-programs-list.contract.spec.ts`؛ `PlanSyncRepositoryTest`؛ `DtoPayloadContractTest.userProgramsEndpointResponse_parsesEnrollmentList`.
- **commits:** `17d6461f` (باك) + `ace5ddb5` (KMP)

### تفاصيل P2.2

- **الحالة:** مكتمل (وكيل Composer — Jul 10, 2026).
- **ما تغيّر (باك):**
  - `computeSafeSyncWatermark` — `timestamp = max(now, max entity.updatedAt)` (B-N2).
  - `includeReports=summary|full|none` على `/mobile/sync` (غياب البراميتر = `full` للتوافق).
  - دلتا: فلتر `updatedAt` لـ `userPrograms` + `plannedWorkoutReports`؛ `systemMessages`/`audioManifest` فقط عند full أو تغيّر fingerprint؛ `getPublishedForMobile` ids أولًا ثم الشجرة (H26).
- **ما تغيّر (KMP):** `fetchSync(..., includeReports=summary)`؛ full بـ `systemMessages=[]` يمسح الكاش (B-N1)؛ دمج التقارير الغنية (P1.9) يبقى.
- **اختبارات:** `mobile-sync.watermark.spec.ts`؛ `mobile-sync.delta-payload.contract.spec.ts`؛ `get-published-for-mobile-delta.contract.spec.ts`؛ `SyncPayloadBudgetContractTest` + fixture؛ `SystemMessageCacheTest`؛ hydrate B-N1 — **PASS**. P2.1 `user-programs` لم يُكسر.
- **قياس حي:** تعذّر بدون `DATABASE_URL` / `.env` — تقدير التحسين في `Data-Layer-Execution-Report.md` §4.
- **commits:** `0fed24c0` — `feat(sync): delta user payload with safe watermark (P2.2)` (محلي، بلا push).

### تفاصيل P2.3

- **الحالة:** مكتمل (وكيل Composer — Jul 10, 2026).
- **ما تغيّر:**
  - `SharedExploreRepository.refreshExploreContent` → `explore.sync()` (دلتا) بدل `syncFull`.
  - `ProgramFlowSyncRepository.syncExplore` → `explore.sync()`؛ أُضيف `repairExploreCatalog()` → `syncFull` لزر الإصلاح/drift لاحقًا (UX.2).
  - `MovitSyncOrchestrator` — بعد `exploreSync.applyFromSync` يكتب `syncResponse.timestamp` في `EXPLORE_LAST_SYNC` عبر `ExploreSyncRepository.writeExploreLastSync`.
- **قرارات:** `syncFull` محفوظ صراحة لمسار الإصلاح فقط؛ لم تُمسّ Outbox/auth/TrainingSessionWriteCoordinator.
- **اختبارات:** `SharedExploreRepositoryRefreshPolicyTest`؛ `ExploreSyncWatermarkParityTest`؛ تحديث `MovitSyncOrchestratorTest` (P2.1 يقلّل استدعاءات sync).
- **commits:** `3cd56e35` — `feat(data): explore delta refresh with watermark parity (P2.3)`

### تفاصيل P2.6

- **الحالة:** مكتمل (وكيل P2 — Jul 10, 2026).
- **ما تغيّر:**
  - `resolvePoseVariantMessages` يستبدل قوائم feedback من assignments (لا `add` تراكمي).
  - `ExerciseConfigRecord.messageLibraryFingerprint` + `ExerciseMessageLibraryMerger.fingerprint` (محتوى النص/الصوت)؛ `needsResolve` يعيد الدمج عند اختلاف البصمة.
  - `TrainingConfigRepository` — lazy rewrite عند القراءة؛ LRU يُبطَل عند تغيّر المكتبة؛ `applySyncMessageLibrary` يحدّث السجلات ذات البصمة القديمة فقط؛ sync يمرّر المكتبة الكاملة بعد merge.
- **قرارات:** بصمة إحصائيات sync-meta بقيت `id:code` (توافق drift مع السيرفر)؛ بصمة المحتوى للسجل فقط. الشق الخادمي `touchExercisesForMessageIds` كان موجودًا مسبقًا.
- **اختبارات:** `ExerciseMessageLibraryMergerIdempotencyTest`؛ cold-install في `ColdOfflineBundleSeederTest` — **PASS**.
- **commits:** `a3b9f712` — `fix(data): replace message merge and stamp library fingerprint (P2.6)`

### تفاصيل P2.7

- **الحالة:** مكتمل (وكيل P2 — Jul 10, 2026).
- **ما تغيّر:**
  - `Mutex.tryLock` حول `beginSync/endSync`؛ `awaitSyncIdle` لـ TrainingConfigEnsure عند Skipped.
  - `MovitLocalStore.transaction {}` → SQLDelight `transactionWithResult`؛ full/delta catalog apply داخلها (hydrates معلّقة خارجها).
  - `updateLocalEntityCounts` من `catalogOffline` indexes لا explore blob.
- **اختبارات:** `MovitSyncOrchestratorConcurrencyTest`؛ `MovitSyncOrchestratorDriftLoopTest` — **PASS**.
- **commits:** `2a970570` — `fix(data): atomic sync apply with mutex and catalog drift counts (P2.7)`

### تفاصيل P2.5

- **الحالة:** مكتمل (وكيل P2 — Jul 10, 2026).
- **ما تغيّر:**
  - `JsonCacheMaintenance` — سياسات: post_training (60 يوم + حماية outbox غير-SUCCEEDED)، planned reports (90/6 أشهر)، metrics LRU 50، effective plans (أسبوع ±1 أو TTL 21).
  - `listJsonCacheEntriesWithTimestamps` + استعلام SQLDelight؛ يُستدعى بعد sync ناجح.
- **اختبارات:** `JsonCacheMaintenanceTest` (حماية pending / غياب SUCCEEDED / LRU / TTL).
- **commits:** `01053911` — `feat(data): JsonCacheMaintenance GC after successful sync (P2.5)`

### تفاصيل P2.9 + P2.10

- **الحالة:** مكتمل (وكيل P2 — Jul 10, 2026).
- **ما تغيّر (P2.9):** `buildExerciseCatalog` يستخدم `exercise.imageUrl`؛ memoize Home/Explore؛ `withContext(IO)` في SWR وExplore map.
- **ما تغيّر (P2.10):** أُزيل شرط `currentInnerRoute != null`؛ debounce 300ms؛ `localeRevision` يعيد load؛ `cacheInvalidated` SharedFlow من sync + optimistic عبر `MovitCacheInvalidation.emit`.
- **اختبارات:** `WorkoutSessionCatalogImageLookupTest`.
- **commits:** `63853af4` — `feat(data): read-path performance and cacheInvalidated freshness (P2.9, P2.10)`

### تفاصيل P2.11

- **الحالة:** مكتمل (وكيل P2 — Jul 10, 2026).
- **ما تغيّر:** مسار connectivity Android واحد (بلا `requestNow`)؛ bootstrap قبل sync؛ `TrainingSessionSyncGate` يمنع full؛ TrainingConfigEnsure بلا auto-full؛ Pro → `syncDashboard`؛ محفزات موثّقة في `Data-Layer-Execution-Report.md` §1.3.
- **اختبارات:** `TrainingSessionSyncGateTest`.
- **commits:** `24240bf1` — `feat(data): unify sync triggers and gate full refresh in training (P2.11)`

### تفاصيل P2.12

- **الحالة:** مكتمل (وكيل P2 — Jul 10, 2026).
- **ما تغيّر:** إيقاف كتابة training-config المشتق في `applyFromSync` (اشتقاق عند القراءة + تنظيف كسول)؛ journal يكتب SQL فقط مع fallback JSON لمرة؛ `syncProgram` يطبّع إلى `dto.id`.
- **اختبارات:** `SyncCatalogOfflineDerivedConfigTest`.
- **commits:** `d7b6ebaa` — `fix(data): stop dual training-config and journal writes (P2.12)`

### دفعة تصفية working tree — 2026-07-10

**وكيل:** تصفية وتوثيق طبقة البيانات. **لا push.**

| commit | الرسالة | بنود |
|---|---|---|
| `c1f86bd0` | `fix(data): preserve message audio URLs in sync merge (P1.6)` | P1.6 |
| `0e1743e6` | `feat(data): day customization conflicts and report field merge (P1.4, P1.9)` | P1.4 + P1.9 |
| `3cd56e35` | `feat(data): explore delta refresh with watermark parity (P2.3)` | P2.3 |
| `f8f44b13` | `feat(data): sync telemetry and error classification (P0.3)` | P0.3 |
| `0404971d` | `feat(account): safe logout with outbox flush warning (P1.8)` | P1.8 + UX.4 |
| `17d6461f` | `feat(backend): lightweight mobile user-programs list endpoint (P2.1)` | P2.1 باك |
| `ace5ddb5` | `feat(data): route enrollment refresh through user-programs API (P2.1)` | P2.1 KMP |
| `60cb4c4a` | `fix(data): add missing assertTrue import in hydration test` | compile |
| `18851345` | `test(data): fix P1.4/P1.9 conflict regression assertions` | اختبارات |
| `0fed24c0` | `feat(sync): delta user payload with safe watermark (P2.2)` | P2.2 |
| `5d0d6e72` | `docs: mark P2.2 complete in remediation tracker` | P2.2 توثيق |
| `a3b9f712` | `fix(data): replace message merge and stamp library fingerprint (P2.6)` | P2.6 |
| `2a970570` | `fix(data): atomic sync apply with mutex and catalog drift counts (P2.7)` | P2.7 |
| `01053911` | `feat(data): JsonCacheMaintenance GC after successful sync (P2.5)` | P2.5 |
| `63853af4` | `feat(data): read-path performance and cacheInvalidated freshness (P2.9, P2.10)` | P2.9 + P2.10 |
| `24240bf1` | `feat(data): unify sync triggers and gate full refresh in training (P2.11)` | P2.11 |
| `d7b6ebaa` | `fix(data): stop dual training-config and journal writes (P2.12)` | P2.12 |

**متروك غير مُلتزَم:** ملفات مراجعة أخرى (`Camera-Engine-Review-Brief.md`, `Main-Screens-Flow-Audit.md`). لم يُمسّ `mobile-sync.service.ts` (P2.2 وكيل آخر).

### إغلاق الفجوات الجزئية — 2026-07-10

- **UX.5:** `ProgramDetailUiState.isOffline` + banner في `ProgramDetailScreen` (نفس نمط training).
- **P2.11 (جزئي):** `BackgroundSyncScheduler` Android — `setRequiresBatteryNotLow(true)` على periodic فقط.
- **P2.5 (orphans):** `MovitPlatformBindings.cleanupOrphanFrameCaptures` (Android/iOS)؛ يُستدعى بعد `JsonCacheMaintenance` مع حماية outbox + post_training cache + journals نشطة.
- **P0.4:** baseline مدمج في `Data-Layer-Execution-Report.md` §4.
- **بوابة P1:** مكتمل برمجيًا؛ سيناريو أسبوع الجيم اليدوي يبقى تشغيليًا.

### الخلاصة الختامية

**الخطة منفّذة برمجيًا؛ المتبقي التشغيلي:** قياس curl حي، `migrate deploy`، سيناريو الجيم اليدوي، Redis اختياري.
