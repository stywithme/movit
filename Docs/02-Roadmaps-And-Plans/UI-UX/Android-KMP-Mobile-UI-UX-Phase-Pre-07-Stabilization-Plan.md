# Android / KMP Mobile UI/UX — Phase Pre-07: Stabilization Gate (Contract · Offline · Engine Boundary)

آخر تحديث: **2026-06-10**
الحالة: **مفتوحة (OPEN) — خطة عمل للفريق**
المصدر: رأي متخصّص مستقل بعد إغلاق [Phase 06](Android-KMP-Mobile-UI-UX-Phase-06-Production-Launcher-Plan.md) — **«لا تدخل Phase 07 (كاميرا) مباشرة؛ ثبّت العقود + offline + حدود المحرك أولاً»** — مدعوماً بتحقّق كود مستقل (هذا المستند).

المراجع:
- [Phase 06 — Production Launcher](Android-KMP-Mobile-UI-UX-Phase-06-Production-Launcher-Plan.md) (مغلقة ~95%)
- [Pre-06.2](Android-KMP-Mobile-UI-UX-Phase-Pre-06-2-Code-Review-Debt-Closure-Plan.md)
- [الخطة المهنية](Android-KMP-Mobile-UI-UX-Professional-Plan.md) — Phase 07 (camera/ML boundary)

---

## ملخص تنفيذي للمدير

Phase 07 (نقل التدريب الحي/الكاميرا إلى KMP) **ليست الخطوة التالية الآمنة**. مراجعة كود مستقلة أكّدت 3 ديون بنيوية تجعل القفز للكاميرا الآن مخاطرة:

| الدين | الواقع المتحقَّق منه | الأثر على Phase 07 |
|------|---------------------|---------------------|
| **تغطية عقود ناقصة** | legacy = **~67 endpoint**، KMP `MovitMobileApi` = **22 فقط** | endpoints التدريب نفسها (`training-config`, `planned-workouts/start\|complete\|report`, `audio-manifest`) **غائبة عن KMP** — وهي مدخلات Phase 07 |
| **لا offline write queue** | صفر queue/replay في `core:data`؛ الكتابات الثلاث مباشرة | جلسة تدريب بلا شبكة ستفقد التقرير/الإكمال — كسر offline-first في أكثر مسار يحتاجه |
| **محرك التدريب الكامل لم يُفصَل** | المُستخرَج: عدّادات/زوايا/score/فلاتر/feedback/state-machine ✅؛ المتبقّي: lifecycle/time/frame-pipeline ما زال Android | نقل `TrainingActivity` كما هي = نقل تشابك الكاميرا مع المنطق |

**القرار:** أدخِل **Pre-07-Phase** كبوابة تثبيت تُغلق هذه الديون **قبل** لمس الكاميرا. هذا لا يبطّئ المشروع — بل يمنع إعادة كتابة Phase 07 مرتين.

**مبدأ حاكم (غير قابل للتفاوض):** **لا تغيير لعقد الباك اند.** KMP يجب أن **يستهلك العقد القائم بالكامل** (نفس المسارات/الحقول التي يستهلكها legacy)، لا أن يفرض عقداً جديداً.

**ما هو سليم بالفعل (لا يُعاد):** اتصال Ktor صحيح، endpoints الأساسية (home/explore/reports/programs/plan/auth/profile) تعمل، read-fallback cache في home/explore/session، والمحرك العددي مُستخرَج ومُفوَّض إليه من legacy. Pre-07 يبني فوق هذا، لا ينقضه.

---

## التحقق المستقل (2026-06-10) — أدلة الديون

### 1) فجوة العقود (≈45 endpoint في legacy غير موجودة في KMP)

legacy Retrofit (`MobileSyncApi` 383 سطراً · `AuthApi` · `SubscriptionApi` · `BookingApi`) يحوي **67** endpoint؛ `MovitMobileApi` (KMP) يحوي **22**. الناقص — مجمّعاً حسب الحاجة:

**🔴 مدخلات Phase 07 المباشرة (كتلة حاجزة):**
```
GET  api/mobile/workout-templates/{id}/training-config     ← إعدادات المحرك/العتبات
GET  api/mobile/workout-templates/{slug}/audio-manifest    ← cache الصوت/التعليمات
GET  api/mobile/exercises/{slug}/audio-manifest
POST api/mobile/planned-workouts/{workoutId}/start
POST api/mobile/planned-workouts/{workoutId}/complete
POST api/mobile/planned-workouts/{workoutId}/report        ← تقرير الجلسة (offline-critical)
POST api/mobile/workout-executions/explore
```

**🟠 كتابات/تفضيلات (تحتاج write queue — WS-2):**
```
PUT    api/mobile/exercise-preferences/{exerciseId}
DELETE api/mobile/exercise-preferences/{exerciseId}
POST   api/mobile/user-programs/{id}/overrides
DELETE api/mobile/user-programs/{id}/overrides/{overrideId}
GET    api/mobile/user-programs/{id}/overrides
POST   api/mobile/plan/complete | plan/pause | plan/resume   ← pause/resume موجودان في العقد، قابلان للنقل
POST   api/mobile/progression/mark-seen
```

**🟡 قراءات parity (تحتاج تصنيف: تُنقل أم تُؤجَّل بوعي):**
```
GET api/mobile/plan/today | plan/enrollment-check | plans
GET api/mobile/training-profile (KMP عنده PUT فقط)
GET api/mobile/programs/{id}/preview
GET api/mobile/progression/history | recent | session/{id}
GET api/mobile/workout-executions/stats
GET api/mobile/level-profile/history | levels
GET api/mobile/assessment-templates/resolve · GET api/assessment/latest|progress · POST api/assessment
POST api/mobile/prescription/recommend · POST api/mobile/reassessment/request
```

**⚪ خارج نطاق المنتج الحالي (توثَّق كمتروكة):**
```
auth/google (Google bridge مؤجَّل) · auth/reset-password · auth/profile PATCH
subscriptions/* (StoreKit مخفى) · api/bookings/rules (BookingApi كامل)
```

### 2) فجوة DTO (حقول يرجعها الباك اند ولا يستهلكها KMP)

- `api/mobile/home`: `workoutTemplateId` · `isTrainingDay` · `catchUpSuggestion` — غير مُستهلكة بالكامل في `HomeDto`.
- `api/mobile/sync`: payload ضخم، KMP يستخدم جزءاً محدوداً (غالباً `userPrograms`) — `MobileSyncModels` 378 سطراً مقابل استهلاك KMP الجزئي.

### 3) عمق offline-first في legacy لم يُنقل

| ملف legacy | الأسطر | المسؤولية | حالة KMP |
|------------|--------|-----------|----------|
| `SyncManager.kt` | **911** | full refresh · drift detection · تنسيق المزامنة | ❌ غير منقول |
| `WorkoutSyncService.kt` | 379 | **pending workout reports queue** + replay | ❌ غير منقول |
| `AudioCacheManager.kt` | 495 | cache الصوت/الرسائل | ❌ |
| `ProgramCacheManager` · `WorkoutCacheManager` · `ExerciseCacheManager` | 190·216·246 | كاش مُهيكل لكل كيان | 🔶 KMP = JSON blob في prefs |
| `OfflineFallbackLoader` · `JsonEntityCacheSupport` | 50·19 | تحميل fallback مُهيكل | 🔶 جزئي |

KMP الحالي: `readCache/writeCache` = JSON في `SharedPreferences`/`UserDefaults` عبر `MovitCacheKeys`. **read-fallback يعمل** (home/explore/session)، لكن **صفر write queue** (تأكّد: لا `queue/pending/replay/outbox/drift` في `core:data`)، و`saveDayCustomizations`/`enrollProgram`/`updateSettings` تكتب مباشرة بلا replay.

### 4) بناء/أدوات

- `:core:data` · `:core:training-engine` · `:feature:account` tests + `:app:assembleDebug` ✅.
- تحذير: `com.android.library` على موديولات KMP يحتاج هجرة لـ `android.kmp.library` قبل AGP 9 (مُجهَّز alias في الـ catalog، غير مُنفَّذ).

---

## مسارات العمل

> الترتيب حسب الأولوية. **P0 = حاجز Phase 07 الحقيقي.** بعضها يكتمل دفعة أولى ثم يمتد بالتوازي مع Phase 07.

### WS-1 — مصفوفة العقود + إغلاق الفجوة 🔴 P0

**الهدف:** إثبات أن KMP يغطّي كل **بيانات وسلوك** legacy من **نفس** عقد الباك اند، أو توثيق المتروك عمداً.

**العمل:**
1. **Contract Matrix** (مستند `Backend-Contract-Matrix.md`): صف لكل endpoint × [backend controller · legacy Retrofit · KMP MovitMobileApi · DTO field coverage · الحالة].
2. صنّف الـ45 الناقصة: **تُنقَل الآن** / **مدخل Phase 07** / **متروكة عمداً (مع سبب)**.
3. أضِف endpoints الكتلة 🔴 (training-config · audio-manifest · planned-workouts ×3 · workout-executions/explore) إلى `MovitMobileApi` + DTOs بـ kotlinx.serialization.
4. أغلِق فجوة حقول DTO (home: `workoutTemplateId`/`isTrainingDay`/`catchUpSuggestion`؛ sync: الحقول المستخدمة فعلاً في legacy).
5. **لا تغيير عقد:** المسارات/الأسماء/الحقول تطابق ما يرسله الباك اند ويستهلكه legacy حرفياً.

**القبول:** مصفوفة مكتملة بلا «؟»؛ كل endpoint إما مُغطّى أو موثَّق كمتروك؛ DTOs الجديدة تُفكّك payload الباك اند الحقيقي (لا تجاهل صامت).

---

### WS-2 — Offline Write Queue (Outbox) 🔴 P0/P1

**الهدف:** استعادة offline-first في مسار **الكتابة** (أهم تحفّظ المتخصص) — لا تُفقَد جلسة/تقرير بلا شبكة.

**العمل:**
1. صمّم **Outbox** دائم في `core:data`: عملية كتابة = `{id, type, payload, createdAt, attempts, status}`.
2. غطِّ: `planned-workouts start/complete/report` · `plan complete/pause/resume` · `exercise-preferences PUT/DELETE` · `user-programs overrides POST/DELETE` · `saveDayCustomizations` · `progression/mark-seen`.
3. **Replay** عند عودة الشبكة؛ **idempotency** (مفتاح عميل لكل عملية)؛ سياسة تعارض (server-wins/merge موثّقة).
4. انقل دلالات `WorkoutSyncService` (pending reports queue) إلى commonMain.
5. الكتابة المتفائلة: حدّث الكاش المحلي فوراً ثم زامِن.

**القبول:** اختبار: كتابة بلا شبكة → تُحفظ في Outbox → عند الاتصال تُرسَل مرة واحدة بنجاح؛ فشل دائم → سياسة واضحة؛ لا ازدواج عند replay.

---

### WS-3 — عمق القراءة/الكاش (Sync parity) 🟠 P1

**الهدف:** نقل عمق `SyncManager` بما يخدم الاستخدام الفعلي (لا نسخ 911 سطراً أعمى).

**العمل:**
1. انقل: full-refresh orchestration · drift detection (نسخة/طابع زمني) · reports cache · audio/message manifest cache.
2. حدِّد ما يبقى platform (تشغيل الصوت) خلف `expect/actual`، وما ينتقل لـ commonMain (سياسة المزامنة).
3. وحِّد سياسة الكاش عبر الـ repositories (اليوم مبعثرة بين home/explore/session).

**القبول:** سيناريو offline كامل (فتح بارد بلا شبكة) يعرض بيانات مُهيكلة محدّثة بمنطق legacy؛ drift detection يمنع كاش قديم خاطئ.

---

### WS-4 — قرار طبقة التخزين 🟠 P1 (يسبق WS-2/WS-3 تقنياً)

**الهدف:** حسم البنية قبل بناء queue/cache فوق أساس غير مناسب.

**المشكلة:** الكاش الحالي JSON blob في prefs — **لا يكفي** لـ Outbox + كاش مُهيكل + drift + استعلامات.

**العمل:**
1. قرار موثّق: **SQLDelight** (موصى به — KMP أصيل، SQL صريح، مثالي لـ queue/cache مُهيكل) مقابل Room-KMP مقابل abstraction فوق prefs.
2. عرّف `MovitLocalStore` abstraction؛ نفّذ الخيار المعتمد؛ رحّل الكاش الحالي خلفه (بدون كسر المفاتيح).
3. أبقِ `MovitPlatformBindings.readCache/writeCache` كـ fallback أو استبدلها بنظافة.

**القبول:** قرار مكتوب في الخطة المهنية؛ Outbox + الكاش المُهيكل يبنيان فوق الطبقة المعتمدة؛ ترحيل بلا فقد بيانات.

---

### WS-5 — تجهيز حدود محرك التدريب 🔴 P0 (يفكّ Phase 07)

**الهدف:** فصل المحرك عن adapters المنصّة **قبل** الكاميرا — أهم خطوة تمنع نقل `TrainingActivity` كما هي.

**العمل:**
1. عرّف في commonMain (`core:training-engine`): `PoseFrame` · `Landmark` · `JointAngles` · `TrainingSessionState` (موديلات خالصة).
2. عرّف عقود `expect/actual` **كواجهات فقط (بلا تنفيذ)**: `CameraFrameSource` · `PoseDetector` · `AudioFeedbackPlayer`.
3. انقل ما تبقّى pure من `TrainingEngine` (time/phase orchestration غير المرتبط بالكاميرا) إلى commonMain بنمط wrappers/parity الحالي.
4. اعزل lifecycle/frame-pipeline القديم خلف الحدود؛ common لا يرى CameraX/MediaPipe إطلاقاً.

**القبول:** `core:training-engine` يكمبّل لـ iOS مع الموديلات الجديدة؛ اختبار parity للمنطق المنقول؛ صفر `import` لـ CameraX/MediaPipe في commonMain؛ خريطة واضحة لما تنفّذه كل منصّة في Phase 07.

---

### WS-6 — اختبارات العقود + نظافة البناء 🟡 P1

**العمل:**
1. **Contract/payload tests:** ثبّت JSON حقيقية من الباك اند كـ fixtures؛ اختبر أن DTOs تفكّكها بلا فقد، وأن request bodies تطابق ما يتوقعه legacy/الباك اند.
2. هجرة `com.android.library` → `android.kmp.library` قبل AGP 9 (alias مُجهَّز).
3. CI: أضِف `:core:data` + `:core:network` contract tests + `assembleRelease` (من P6-3).

**القبول:** fixtures تغطّي endpoints الكتلة 🔴؛ اختبار ينكسر لو انحرف DTO عن العقد؛ تحذير AGP محسوم أو موثّق بخطة.

---

## ترتيب الأولويات

| الأولوية | المسارات | لماذا |
|----------|----------|-------|
| **🔴 P0 — حاجز Phase 07** | WS-1 (عقود + endpoints التدريب) · WS-5 (حدود المحرك) · WS-4 (قرار التخزين) | بدونها Phase 07 تُبنى على رمل وتُعاد كتابتها |
| **🟠 P1 — offline-first الحقيقي** | WS-2 (write queue) · WS-3 (sync depth) | جلسة التدريب تولّد كتابات؛ بلا queue تُفقَد بلا شبكة |
| **🟡 P1 — تثبيت** | WS-6 (contract tests + AGP) | يمنع انحراف صامت عن العقد |

**تسلسل تقني:** WS-4 (قرار التخزين) **يسبق** WS-2/WS-3 (يُبنيان فوقه). WS-1 و WS-5 يتوازيان.

---

## بوابة خروج Pre-07

| البند | المعيار |
|-------|---------|
| WS-1 | مصفوفة عقود مكتملة · endpoints التدريب 🔴 في KMP · فجوة DTO (home/sync) مغلقة · لا تغيير عقد |
| WS-2 | Outbox يعمل: كتابة offline → replay مرة واحدة عند الاتصال · اختبارات |
| WS-3 | offline بارد كامل يعرض بيانات مُهيكلة · drift detection |
| WS-4 | قرار تخزين مكتوب + منفّذ + ترحيل بلا فقد |
| WS-5 | موديولات+عقود الكاميرا (interfaces) في commonMain · iOS compile · صفر CameraX/MediaPipe في common |
| WS-6 | contract/payload tests خضراء · مسار AGP 9 محسوم |
| التحقق | tests خضراء · `assembleDebug`+`assembleRelease` · `compileKotlinIosSimulatorArm64` |

**Pre-07 لا تُغلق** قبل P0 (WS-1/4/5) كاملاً + WS-2 (Outbox) على الأقل لمسار التقرير/الإكمال — فهذان ما تولّده الكاميرا.

---

## أوامر التحقق

```powershell
cd android-poc
.\gradlew.bat --console=plain `
  :core:network:testDebugUnitTest `
  :core:data:testDebugUnitTest `
  :core:training-engine:testDebugUnitTest `
  :feature:library:testDebugUnitTest `
  :app:assembleDebug `
  :feature:shell:compileKotlinIosSimulatorArm64
```

فحوص العقود (بعد WS-1):
```powershell
# كل endpoint في legacy إمّا في KMP أو موثَّق كمتروك
# (يُؤتمت كاختبار يقارن قائمة legacy Retrofit بـ MovitMobileApi)
```

---

## ما بعد Pre-07 → Phase 07

بإغلاق هذه البوابة تدخل Phase 07 على أرض صلبة: العقود كاملة، الكتابات مضمونة offline، وحدود الكاميرا معرّفة. عندها فقط:
- نفّذ `CameraFrameSource`/`PoseDetector` actuals (Android: CameraX+MediaPipe، iOS: AVFoundation+Vision).
- اربط المحرك المُستخرَج بمصدر الإطارات عبر `PoseFrame` فقط.
- جلسة التدريب تكتب التقرير عبر Outbox (WS-2) — offline-safe.

**القاعدة:** Phase 07 = توصيل، لا اكتشاف. كل المجهول يُحَل في Pre-07.

---

## ملاحظة على رأي المتخصص

رأي المتخصص دقيق ومدعوم بالكود؛ هذه الخطة تتبنّاه بالكامل مع تصويبين صغيرين:
1. **pause/resume موجودان** في عقد legacy (`POST api/mobile/plan/pause|resume`) — لا يحتاجان «حسماً مع الباك اند»، بل نقلاً مباشراً (WS-1).
2. الأولوية بين WS: **قرار التخزين (WS-4) يسبق** بناء الـ queue/cache تقنياً، رغم أن offline-first هو الدافع.
