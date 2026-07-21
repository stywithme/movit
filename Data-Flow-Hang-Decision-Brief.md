# تدفق البيانات الحالي وتعليق الداشبورد — موجز قرار

**التاريخ:** 2026-07-16  
**الغرض:** عرض الحالة على مدير المشروع لاتخاذ قرار — **بدون تنفيذ إصلاح في هذه الوثيقة**  
**المصدر:** الكود الحالي + لوجات التشغيل (18:25 / 18:30 / 18:34)

---

## 1) تدفق البيانات كما هو مبني الآن (من الكود)

```
فتح التطبيق / جلسة نشطة
        │
        ├─① DataReadinessGate.evaluate()
        │     يفحص الكاش المحلي فقط:
        │     Explore · Exercise configs · Catalog exports ·
        │     Message library · System messages · Home
        │
        ├─② إن النواة جاهزة (Explore + configs + messages)
        │     → إخفاء Splash → فتح التبويبات فورًا
        │     → sync/prefetch في الخلفية
        │
        ├─③ إن النواة ناقصة
        │     → BootstrapSplash + fullRefresh()
        │     → GET /mobile/sync?forceRefresh=true
        │     → كتابة الحمولة في كاش محلي (transaction واحدة)
        │     → إعادة تقييم الجاهزية → Tabs أو شاشة فشل
        │
        └─④ بالتوازي دائمًا تقريبًا
              MovitBackgroundSyncWorker (WorkManager)
              يشارك نفس قفل syncMutex مع fullRefresh
```

### مصادر البيانات الأساسية

| النوع | المصدر الشبكي | التخزين المحلي | متى يُجلب |
|---|---|---|---|
| كتالوج Explore | `/mobile/sync` + `/mobile/explore` | بلوب Explore | مع دورة sync |
| إعدادات التمارين | `/mobile/sync` (+ ensure عند النقص) | `exercise_config_cache` | مع sync / تنزيل عند Start |
| برامج وقوالب | `/mobile/sync` | فهارس exports | مع sync |
| مكتبة الرسائل (~2662) | `/mobile/sync` | JSON واحد كبير | مع sync (full) |
| Home | `/mobile/home` (+ ETag) | بلوب Home | مع sync / فتح التبويب |
| كتابات المستخدم | outbox → replay | SQLDelight outbox | عند الاتصال |

### قواعد الدخول الحالية للـ Tabs (بعد آخر تعديلات)

يُسمح بالدخول إذا وُجدت محليًا:

- Explore غير فارغ  
- فهرس exercise configs غير فارغ  
- مكتبة الرسائل غير فارغة  

**لا تحجب الدخول:** Home · System messages · CatalogExports (تُكمَل في الخلفية).

---

## 2) ماذا حدث في التشغيل الأخير (مراحل المشكلة)

### المرحلة A — السيرفر سليم
الباك كان يرد بنجاح سابقًا:

- `POST /mobile/auth/login` → 201  
- `GET /mobile/sync?...` → 200 (~1 ثانية، ~184 تمرينًا، مكتبة رسائل 2662، `withAudio=0`)

المشكلة **ليست** أن الـ API معطل في هذه المرحلة.

### المرحلة B — Splash يفشل رغم sync ناجح (أُصلحت جزئيًا)
البوابة كانت صارمة أكثر من اللازم (مطابقة 1:1 Explore↔config) → شاشة «Couldn't load data» بعد HTTP 200.  
خُفِّفت الفحوصات؛ ما زال هناك أثر جانبي ثقيل عند تطبيق الحمولة.

### المرحلة C — سباق القفل `Skipped` (أُصلح منطقيًا)
اللوج السابق:

```
[MovitBootstrap] outcome=Skipped
readiness=Missing(parts=[CatalogExports])
```

`fullRefresh()` يستخدم `tryLock`؛ إن شغل الـ Background Worker القفل → `Skipped` → كان Splash يُعاملها كفشل.  
أُضيف انتظار/إعادة محاولة + السماح بالدخول إن النواة جاهزة.

### المرحلة D — الداشبورد يفتح ثم يعلّق (الحالة الحالية — 18:34)
من لوج الجلسة الأخيرة:

| ملاحظة | المعنى |
|---|---|
| لا سطر `[MovitBootstrap]` فشل | الدخول للـ Tabs تم (النواة اعتُبرت كافية) |
| `Skipped 291 frames` + `Davey! ~5270ms` | عمل ثقيل على **الخيط الرئيسي** أثناء الرسم الأول |
| GC كبير (LOS عشرات الميجابايت) | تحميل/فك JSON ضخم (غالبًا مكتبة الرسائل / كاش Explore-Home) |
| `MovitBackgroundSyncWorker` يعمل مع الفتح | sync خلفي يتزامن مع أول رسم للـ Home |
| Debugger مفعّل | يبطّئ أكثر؛ ليس السبب الجذري لكنه يضخّم التعليق |

**الخلاصة التشخيصية للمرحلة D:**  
التطبيق تجاوز بوابة الجاهزية ودخل الداشبورد، ثم **تجمّد/تأخّر بشدة عند أول تكوين للواجهة** بسبب حجم البيانات المحلية + عمل sync/قراءة كاش متزامن مع الـ Compose، وليس بسبب انقطاع السيرفر.

---

## 3) جذر المشكلة من وجهة نظر تقنية (مختصر)

ثلاث طبقات متراكبة — ليست عطلًا واحدًا:

1. **حمولة sync كاملة ثقيلة**  
   full sync يجلب مكتبة رسائل كبيرة + كتالوج + configs ويكتبها دفعة واحدة. حتى بعد إيقاف إعادة كتابة كل configs فورًا، فك/قراءة الـ JSON الضخم ما زال غاليًا.

2. **سباق مسارات sync**  
   Bootstrap / Profile Sync / Background Worker يتشاركون قفلًا واحدًا (`tryLock` → Skipped). منطق الدخول أُصلح؛ تكلفة الـ worker عند الإقلاع ما زالت موجودة.

3. **أول إطار للـ Home غير معزول عن العمل الثقيل**  
   شاشة الداشبورد تُعرض بينما الكاش يُقرأ أو يُحدَّث، فيحدث إسقاط إطارات وتعليق ملحوظ على المحاكي (أبطأ من الجهاز الحقيقي، لكن العَرَض حقيقي).

---

## 4) خيارات الحل المقترحة (للقرار — بدون تنفيذ الآن)

### الخيار 1 — سريع ومحدود النطاق (موصى به كخطوة أولى)
**الهدف:** داشبورد يستجيب فورًا بعد الدخول.

- تأجيل `MovitBackgroundSyncWorker` / `syncIfNeeded` حتى بعد أول إطار مستقر للـ Home (مثلاً 1–2 ثانية أو بعد `onFirstFrame`).
- قراءة Home من الكاش فقط في الإطار الأول؛ أي تحديث شبكة بعد العرض.
- عدم حجب الـ UI على فك مكتبة الرسائل الكاملة عند فتح Home (الرسائل للتدريب لا للداشبورد).

**الجهد:** صغير · **المخاطر:** منخفضة · **الأثر:** يزيل معظم «علق بعد الفتح».

### الخيار 2 — تحسين مسار sync الكامل
**الهدف:** full sync لا يجمّد الجهاز.

- تقسيم تطبيق الحمولة (explore/configs أولًا → ثم message library على دفعات).
- عدم إدخال مكتبة الرسائل في مسار حرج للإقلاع إن لم تُحتَج للـ Home.
- إبقاء merge-on-read للرسائل (مطبّق جزئيًا).

**الجهد:** متوسط · **المخاطر:** متوسطة · **الأثر:** إقلاع أول مرة أسرع + أقل GC.

### الخيار 3 — إعادة تعريف «جاهزية النواة»
**الهدف:** Splash أقصر وأوضح.

- CoreReady للإقلاع = Explore + configs (+ home اختياري).  
- CatalogExports + message library = خلفية بعد Tabs.  
- Splash لا ينتظر full graph كامل.

**الجهد:** صغير–متوسط · **المخاطر:** مستخدم قد يفتح عنصرًا قبل اكتمال export (يُعالَج بزر تنزيل الموجود في R3).

### الخيار 4 — لا تغيير معماري الآن
الاكتفاء بالملاحظة: المحاكي + Debug يضخّمان التعليق؛ إعادة الاختبار على جهاز حقيقي بـ Run (ليس Debug).

**غير كافٍ وحده** إذا تكرر التعليق على جهاز حقيقي — لكنه خطوة تحقق قبل استثمار الخيار 2.

---

## 5) توصية للتنفيذ بعد القرار

| الأولوية | البند |
|---|---|
| P0 | الخيار 1 (عزل أول إطار Home عن sync/worker) |
| P1 | الخيار 3 (تخفيف تعريف الجاهزية إن لزم) |
| P2 | الخيار 2 (تقسيم apply للـ sync) |
| تحقق | قياس على جهاز حقيقي بدون debugger + فلتر Logcat: `MovitBootstrap` / frames |

---

## 6) ما الذي نحتاجه من مدير المشروع؟

اختيار واحد:

- **أ)** نفّذ الخيار 1 فقط الآن  
- **ب)** نفّذ 1 + 3  
- **ج)** خطة كاملة 1 → 3 → 2  
- **د)** تحقق ميداني أولًا (جهاز حقيقي) ثم نقرر  

---

## 7) مراجعة محايدة (Claude — 2026-07-16) والقرار الموصى به

> راجعتُ الكود الفعلي ولوج جلسة 17:40 سطرًا سطرًا. **تشخيص الوثيقة صحيح في وصف المراحل، لكنه فوّت الجذر الأعمق** — المشكلة ليست «الحمولة كبيرة» بقدر ما هي **إعادة فك JSON ضخم بلا memoization في أربعة مسارات متزامنة مع أول إطار**.

### 7.1 الجذر الحقيقي بالدليل (file:line)

| # | الاكتشاف | الدليل |
|---|---|---|
| **G1** | `MessageLibraryCache.read()` **بلا أي memo** — يفك JSON الـ 2662 رسالة (عدة MB) من SQLite ويحوّله objects **عند كل استدعاء** | `MessageLibraryCache.kt:15` |
| **G2** | `mergeRecordForRead` يستدعي `messageLibraryCache.read()` + يعيد حساب fingerprint المحتوى الكامل (2662 عنصرًا) **لكل قراءة سجل تمرين** | `TrainingConfigRepository.kt:222-226` |
| **G3** | `BackgroundMediaPrefetcher` يبدأ فور الدخول ويجهّز **12 تمرينًا** → كل تمرين = فتح سجل = **فك كامل للمكتبة + fingerprint** ×12 | `BackgroundMediaPrefetcher.kt:34` — وهذا يطابق حرفيًا حلقة GC في اللوج: تحرير **13–26MB LOS** كل ~350ms بين `17:41:01→17:41:04` (~8–12 دورة) |
| **G4** | `DataReadinessGate.evaluate()` ليست «lightweight» كما هو موثق — تفك المكتبة + system messages + explore بالكامل، وتُستدعى **3–5 مرات** في مسار bootstrap واحد (منها مرة داخل سطر الـ `println` نفسه!) | `DataReadinessGate.kt:72`، `MovitAppShellViewModel.kt:160,167,208,215,254` |
| **G5** | `BackgroundSyncScheduler.schedule()` **ما زال يحقن one-time sync عند كل إقلاع** — رغم أن قرار P2.11 كان إزالته. هذا مصدر سباق `Skipped` الذي عقّد الـ Splash، وثالث دورة تتصادم مع أول إطار (WM worker + bootstrap forceCheck + resume) | `BackgroundSyncScheduler.kt:38` + لوج `17:40:33 Starting work MovitBackgroundSyncWorker` قبل أول frame |
| **G6** | لقطة «Loading your dashboard…» الفارغة: `canEnterTabsAfterBootstrapSync` يستخدم `requireHome=false` **دائمًا** — فأول login يدخل التبويبات بلا Home ويترك المستخدم على سبينر بلا سياق | `MovitAppShellViewModel.kt:254` |
| **G7** | `mergeRecordForRead` يكتب على القرص أثناء مسار قراءة (lazy rewrite) — على خيط القارئ أيًا كان | `TrainingConfigRepository.kt:228-236` |

**الخلاصة:** الـ Splash موجود ويعمل منطقيًا. الإحساس بأن «التحميل عشوائي» سببه ثلاث دورات sync متسابقة عند الفتح + prefetch يفتح 12 سجلًا فيفك المكتبة 12 مرة + بوابة تفك البلوبات 5 مرات — كله متزامنًا مع أول composition. على المحاكي + Debugger يظهر تجمّدًا؛ على جهاز حقيقي سيظل jank حقيقيًا أخف.

### 7.2 الحكم على الخيارات

- **الخيار 1 وحده لا يكفي** (رأي مخالف للتوصية): تأجيل الـ sync/worker يؤخر المشكلة ولا يحلها — أول prefetch أو أول فتح تمرين سيعيد فك المكتبة ×N. **يجب إقرانه بإصلاح الـ memo (G1/G2)**.
- الخيار 3 صحيح لكن **معكوسًا في حالة واحدة**: عند أول تشغيل (لا كاش إطلاقًا) يجب أن يشمل الـ Splash الـ Home أيضًا (`requireHome=true` فقط حين يكون كل الكاش فارغًا) — حتى لا يهبط المستخدم على داشبورد فارغ. بعد ذلك يظل Home soft كما هو.
- الخيار 2 يصبح أقل إلحاحًا بعد إصلاح الـ memo (معظم كلفة الـ apply هي نفس الفك المتكرر) — يُنفَّذ بعد قياس.
- الخيار 4 (جهاز حقيقي بـ Run) خطوة قياس تتم بالتوازي دائمًا — ليست بديلًا.

### 7.3 القرار الموصى به: حزمة P0 واحدة (≈ يوم عمل) ثم قياس

| # | البند | الملفات | الجهد |
|---|---|---|---|
| **P0-A** | **Memo لمكتبة الرسائل** (نسخة مفكوكة واحدة + generation يُبطَل عند الكتابة — نفس نمط Home/Explore الموجود) + memo مماثل للـ system messages + `needsResolve` يستقبل fingerprint محسوبًا مرة واحدة بدل إعادة حسابه لكل سجل | `MessageLibraryCache.kt`, `SystemMessageCache.kt`, `ExerciseMessageLibraryMerger.kt`, `TrainingConfigRepository.kt` | S |
| **P0-B** | حذف `enqueueOneTimeSync` من `schedule()` نهائيًا (تنفيذ قرار P2.11 المعلق) — دلتا الفتح مسؤولية الـ shell وحده | `BackgroundSyncScheduler.kt:38` | S |
| **P0-C** | تأجيل `requestSyncIfNeeded(forceCheck=true)` + `startBackgroundPrefetch()` حتى **بعد أول إطار مستقر** للتبويب (إشارة first-frame أو تأخير 1.5–2ث بعد إخفاء الـ Splash) — هو الخيار 1 | `MovitAppShellViewModel.kt` | S |
| **P0-D** | `evaluate()` تُستدعى **مرة واحدة** لكل قرار وتُمرَّر نتيجتها (إزالة النداءات المكررة + النداء داخل println)؛ ومع P0-A تصبح رخيصة فعليًا | `MovitAppShellViewModel.kt:160-215` | S |
| **P1** | الخيار 3 المعدّل: `requireHome=true` عند أول تشغيل فقط (كل الكاش فارغ)؛ + skeleton للـ Home بدل السبينر المعلق | `MovitAppShellViewModel.kt`, `MovitHomeScreen.kt` | S–M |
| **P2** | الخيار 2 (تقسيم الـ apply على دفعات) + نقل lazy-rewrite في `mergeRecordForRead` إلى IO — **بعد قياس ما بعد P0 على جهاز حقيقي** | orchestrator + `TrainingConfigRepository.kt` | M |

**التدفق المستهدف بعد P0/P1:**
```
فتح → (Splash فقط إن كان الكاش ناقصًا — وأول مرة تشمل Home) → Tabs ترسم من الكاش بلا أي شغل ثقيل
    → بعد أول إطار: دورة دلتا واحدة هادئة + prefetch (المكتبة تُفك مرة واحدة وتبقى في الذاكرة)
    → مؤشر الهيدر يحكي القصة (أصفر أثناء العمل → أخضر)
```

**إجابة سؤال §6: القرار = (ج) بصيغتها المعدلة أعلاه — P0 (A+B+C+D) الآن، ثم P1، وP2 بعد القياس على جهاز حقيقي.**

---

## 7) نتائج التنفيذ

### 7.1 — الخيار 1 (عزل أول إطار Home)

**التاريخ:** 2026-07-16  
**الحالة:** منفَّذ (بدون commit)

| تغيير | ملف | ماذا يفعل |
|---|---|---|
| تأجيل sync + prefetch 1.5ث بعد دخول التبويبات | `MovitAppShellViewModel.kt` | `schedulePostFirstFrameWork()`؛ `allowBackgroundSync` يمنع `onAppResumed` / connectivity حتى استقرار أول إطار |
| إيقاف one-time WM عند الإقلاع | `BackgroundSyncScheduler.kt` (Android) | `schedule()` يبقي الدوري فقط؛ دلتا الفتح من الـ shell بعد أول إطار |
| Home: كاش أولًا ثم شبكة | `SharedHomeRepository.kt` + `MovitHomeViewModel.kt` | SWR يُصدر الكاش فورًا؛ `delay(300ms)` قبل `home.sync()`؛ لوج `[MovitHomeFirstFrame]` |

**أثر متوقع:** لا يتزامن `MovitBackgroundSyncWorker` one-shot ولا prefetch (فك مكتبة الرسائل عبر configs) مع أول composition للـ Home.

**تحقق:** فلتر Logcat: `MovitHomeFirstFrame` — يفترض ظهور `deferring` ثم `source=cache` ثم `releasing sync/prefetch`.

**اختبارات:** `:feature:shell:testDebugUnitTest` + `:feature:home:testDebugUnitTest` — BUILD SUCCESSFUL.

### 7.2 — الخيار 3 (تخفيف CoreReady)

**التاريخ:** 2026-07-16 · **الحالة:** منفّذ (P1 — بدون commit)

#### التعريف الجديد

| الطبقة | المطلوب | يحجب Splash / `canEnterTabs`؟ |
|---|---|---|
| **CoreReady** | Explore غير فارغ + فهرس exercise configs غير فارغ | نعم |
| Home | اختياري (`requireHome=false` الافتراضي) | لا |
| CatalogExports · MessageLibrary · SystemMessages | خلفية بعد Tabs | لا |

#### ما تغيّر

| ملف | التغيير |
|---|---|
| `DataReadinessGate.kt` | `evaluate(requireHome=false, includeSoftGaps=false)` = Core فقط؛ soft gaps اختيارية للتشخيص؛ `isCoreReady()` للدخول |
| `MovitAppShellViewModel.kt` | `canEnterTabsAfterBootstrapSync()` → `isCoreReady(requireHome=false)` — أُزيل `MessageLibrary` من الحواجز؛ لوج التشخيص يستخدم `includeSoftGaps=true` |
| `DataReadinessGateTest.kt` | اختبارات CoreReady بلا رسائل/exports؛ soft gaps تُبلَّغ فقط عند الطلب |

#### ما لم يُمس (تنسيق مع الوكلاء الآخرين)

- توقيت sync/prefetch بعد أول إطار (الخيار 1).
- تقسيم apply لمكتبة الرسائل (الخيار 2).

#### تحقق

```bash
cd kmp-app
# JBR 21 على ويندوز إن فشل Java 25
.\gradlew.bat :core:data:compileAndroidHostTest :core:data:testAndroidHostTest --tests "com.movit.core.data.readiness.DataReadinessGateTest"
```

**نتيجة:** BUILD SUCCESSFUL (JBR 21) — أُعيد تجميع `compileAndroidHostTest` ثم اجتياز `DataReadinessGateTest`.

### 7.3 — الخيار 2 (تقسيم apply للـ sync)

**التاريخ:** 2026-07-16 · **الحالة:** منفّذ (P2)

#### ما تغيّر

| البند | التنفيذ |
|---|---|
| مسار حرج داخل `transaction` واحدة | `trainingConfig.applySyncExercises` · `systemMessageCache` · `userProgramEnrollment` · `exploreSync.applyFromSync` · `catalogOffline.applyFromSync` (+ user slices خارج الـ transaction كما كان) |
| مكتبة الرسائل | **خارج** الـ transaction الثقيلة — `replaceFull` / `mergePartial` بعد إغلاق المسار الحرج |
| full sync / bootstrap | الكتابة **متزامنة** قبل `Success` (مكتبة الرسائل لم تعد حاجز CoreReady — انظر §7.2) |
| delta + كاش دافئ | دمج المكتبة **مؤجّل** عبر `deferredApplyScope` بعد إرجاع `Success`؛ `merge-on-read` يغطي صحة configs حتى اكتمال الكتابة |
| `applySyncMessageLibrary` على كل config | **لم يُعاد** — التعليق السابق + merge-on-read كما هو |
| تقسيم JSON المكتبة | **غير مطلوب** حاليًا — فصل الـ transaction + التأجيل على الدلتا يكفي؛ كتابة blob واحدة تبقى (ترقية لاحقة: chunking إن ظهر GC على جهاز حقيقي) |

#### الملفات

- `kmp-app/core/data/.../sync/MovitSyncOrchestrator.kt` — تقسيم apply + `shouldDeferMessageLibraryApply` + `scheduleDeferredMessageLibraryApply`
- `kmp-app/core/data/.../sync/MovitSyncOrchestratorApplyPhasingTest.kt` — اختبار full sync متزامن + delta مؤجّل

#### عقد sync / watermark

- `metadataStore.writeLastSyncTimestamp` و `writeFromSyncMeta` ما زالت بعد المسار الحرج (قبل home) كما كانت.
- إحصائيات المكتبة (`writeMessageStats`) تُكتب مع apply المتزامن، أو داخل الـ job المؤجّل للدلتا — لا تُحدَّث قبل persist المكتبة.
- `notifyCacheInvalidated` ثانية بعد الـ job المؤجّل عند اكتمال دمج المكتبة.

#### ما لم يُمس

- تأجيل أول إطار Home / الـ worker (وكيل 1 — الخيار 1).
- تعريف CoreReady خُفِّف لاحقًا في §7.2 (وكيل 3) — هذا القسم يخص apply فقط.

#### تحقق

```bash
cd kmp-app && ./gradlew :core:data:testAndroidHostTest --tests "com.movit.core.data.sync.MovitSyncOrchestratorApplyPhasingTest"
```

