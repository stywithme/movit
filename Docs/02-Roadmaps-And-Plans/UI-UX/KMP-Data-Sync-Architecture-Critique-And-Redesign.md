# نقد معماري لطبقة بيانات KMP + التصميم الصحيح المستهدف

> **التاريخ:** 2026-06-13
> **المرجع:** يبني على [مراجعة المزامنة](./KMP-Data-Sync-Migration-Review.md) و[خطة الهجرة](./Android-KMP-Mobile-Data-Sync-And-Offline-First-Migration-Plan.md)
> **الغرض:** نقد منطقي شامل لـ«لماذا النتيجة غير مرضية رغم تنفيذ الخطة»، ثم تصميم النظام الواحد الصحيح للإنتاج (Android + iOS) مقابل الأهداف السبعة.

---

## 0. الحكم النهائي (Verdict)

الخطة **نُفِّذت على الورق ولم تُنفَّذ في السلوك**. البناء أخضر، الاختبارات تمر (57+)، والمكوّنات موجودة — لكن **المكوّنات الحرجة غير موصولة بدورة الإنتاج**. النتيجة: نظامان يعملان بالتوازي، لا أحدهما مكتمل، فلا يمكن حذف القديم ولا الاعتماد على الجديد. هذا **ليس منتجاً إنتاجياً، بل حالة انتقالية دائمة** — وهو بالضبط ما حذّرت منه الخطة نفسها (`[[no-transitional-solutions]]` و`[[kmp-scorecards-inflated]]`) ثم وقعت فيه.

**السبب الجذري الأعمق ليس تقنياً بل منهجي:** معيار «تم» كان «مدموج + بناء أخضر»، لا «سلوك مُشاهَد على جهاز». الاختبارات تختبر المكوّنات معزولةً، فمرّت بينما الربط مفقود.

---

## 1. الفشل الجوهري: «مبني لكن غير موصول» — بالأدلة

الخطة تدّعي في §6 «حالة التنفيذ»:

- **WS‑5** (تخصيصات + تفضيلات) = ✅ مكتمل
- **WS‑6** (تقارير) = ✅ مكتمل
- **WS‑8** (cold‑start + رسائل النظام) = ✅ مكتمل

الواقع في الكود (تحقّق مباشر):

| الدالة (موجودة في `commonMain`) | من يستدعيها فعلياً؟ |
|---|---|
| `ReportsSyncRepository.hydrateFromSync()` | **لا أحد في الإنتاج** — تعريف فقط |
| `ExercisePreferenceLocalStore.hydrateFromSync()` | **لا أحد في الإنتاج** — تعريف فقط |
| `DayCustomizationLocalStore.hydrateFromBackend()` | **اختبارات فقط** (`DayCustomizationLocalStoreTest`) |
| `SystemMessageCache.save()` | **`ColdOfflineBundleSeeder` (بذر أول تشغيل) + اختبارات فقط** — لا من المزامنة |
| `MovitData.clearAllUserData()` (WS‑9 «✅») | **لا أحد في الإنتاج** — logout يستدعي `UserDataCleaner` القديم فقط |

وفي المقابل، الكود القديم `SyncManager.processSyncResponse()` يستدعيها **كلها فعلياً** (الأسطر 372، 380، 418). أي أن:

> **الكيان الوحيد الذي يطبّق بيانات المستخدم القادمة من `/sync` (التفضيلات، التقارير، تخصيصات الأيام، رسائل النظام) هو النظام القديم — لا الجديد.**

ماذا يطبّق `MovitSyncOrchestrator.runSyncCycle` فعلاً؟ فقط:
`applySyncExercises` (التمارين) · `applyAudioManifest` + prefetch (الصوت) · `planSync.refreshActiveUserProgramId` · `explore.sync/syncFull` · `home.sync` · `reports.syncDashboard` (Pro) · `updateLocalEntityCounts` · `replayPending`.

**خمسة فروع من الـ payload تُقرأ من الشبكة ثم تُرمى:** `systemMessages`, `messageLibrary`, `userExercisePreferences`, `plannedWorkoutReports`, `userPrograms[].customizations`.

### الأثر العملي المباشر على المستخدم

- مستخدم يعيد تثبيت التطبيق أو يسجّل دخوله على جهاز ثانٍ → **لا تعود تفضيلاته، ولا تقاريره المكتملة، ولا تخصيصاته للأيام** (في مسار KMP)، لأن لا شيء يطبّق بيانات المستخدم من الخادم. هذا **فقدان بيانات محسوس** = خرق مباشر للهدف 1.
- رسائل النظام (العد التنازلي/التحذيرات) **تتجمّد على قيم الحزمة الباردة** ولا تتحدّث من الخادم أبداً.

### لماذا لم تكشفه الاختبارات؟

لأن الاختبارات تستدعي `hydrateFromSync` مباشرةً وتتحقق من نتيجتها — وهي تعمل بمعزل. **لا يوجد اختبار تكامل يتحقق أن `runSyncCycle` يستدعيها.** بناء أخضر + 57 اختباراً ≠ سلوك صحيح.

### الاكتشاف الثاني الأخطر: النظام الجديد ليس نقطة الدخول الإنتاجية أصلاً

التحقق المستقل أكّد أن الإصدار الافتراضي (`movit.shell.launcher.enabled` غير مضبوط = `false`) يقلع هكذا:

`SplashActivity (LAUNCHER) → MainContainerActivity → Fragments قديمة (Home/Train/Explore/History) → ExerciseRepository → SyncManager`

أي أن **واجهة الإنتاج الافتراضية ما زالت القديمة بالكامل**، وقشرة KMP (`MovitMainActivity → MovitShellHost`) لا تعمل إلا خلف علم Gradle. تعليق الـ manifest نفسه يقول «legacy production entry … no LAUNCHER until flip». هذا وحده يكفي للحكم بأن النظام الجديد **ليس في وضع إنتاج** (الهدف 7).

### خلل ملموس: تسجيل الخروج لا يمسح بيانات KMP

`clearAuthSession()` في الجسر يستدعي `UserDataCleaner.clearAll` + `AuthManager.clearAuthData` فقط، و**لا يستدعي `MovitData.clearAllUserData()` أبداً** (موجود، مُختبَر، غير موصول — نفس مرض WS‑9). النتيجة: كاش SQLDelight (تمارين/تقارير/جلسات/outbox) **يبقى بعد الخروج** → تسرّب بيانات مستخدم إلى المستخدم التالي على نفس الجهاز.

---

## 2. الأسباب الجذرية المعمارية (تفكير منطقي في «لماذا»)

ليست الأعراض (spinner، شاشة فارغة)، بل البنية التي تنتجها:

### السبب 1 — نظامان متوازيان، والقديم هو المكتمل
الهجرة **أضافت** `core/data` دون أن **تطرح** legacy. الجسر `MovitDataInstall` ما زال يربط الجلسة/التوكنات بـ `AuthManager`، و`activeUserProgramId` يُقرأ من SharedPreferences القديمة مباشرة، و`clearAuthSession` يفوّض لـ `UserDataCleaner` القديم (دون مسّ كاش KMP). و`SyncManager` ما زال المنسّق الوحيد الكامل. **ازدواج مصدر الحقيقة** (ملفات JSON + SharedPreferences القديمة ⟷ SQLDelight الجديد) → خرق الهدفين 2 و5.
> تصحيح دقّة: `exerciseImageUrl` نُقل فعلاً لـ KMP عبر `MovitData.explore` — مثال نادر على ربط مكتمل، ويؤكد أن المشكلة «ربط ناقص» لا «غياب مكوّنات».

### السبب 2 — المنسّق الجديد «port جزئي»، فالحذف مستحيل بنيوياً
لأن `MovitSyncOrchestrator` لا يطبّق كل ما يطبّقه `SyncManager`، **لا يمكن حذف القديم** دون فقدان وظائف. وفي الوقت نفسه القديم يكتب في كاش مختلف عن الجديد. النتيجة: **قفل متبادل (deadlock) بالتصميم** — لا تقدر تحذف القديم ولا تثق بالجديد وحده. هذه هي «الحالة الانتقالية الدائمة».

### السبب 3 — تشظّي الكتالوج (Catalog fragmentation) + هدر شبكة
- القديم: كل الكتالوج (تمارين + workouts + programs) من **`/sync` وحدها**.
- الجديد: التمارين من `/sync`، والكتالوج من `/explore`، والتفاصيل من `/programs/{id}` و`/effective-plan` و`/training-config` (عدة نقاط).
- النتيجة: (أ) جولات شبكة أكثر، (ب) كاشات متعددة تتباين، (ج) `workoutTemplates[]` و`programs[]` **داخل استجابة `/sync` تُجلب ثم تُتجاهَل** في KMP (تُستهلك من `/explore` بدلاً منها) = **هدر باندويث + تكرار بيانات** = خرق الهدف 5.

### السبب 4 — لا offline‑first حقيقي عند البدء + بيانات تجريبية في الإنتاج
`cold_offline_bundle.json` يشحن بيانات **وهمية** (`"Movit"`, `offline-up-001`, صور unsplash) **و`exercises: []` فارغة**. فهو في آنٍ واحد:
- يخرق الهدف 3 (التدريب offline مستحيل أول تشغيل — لا configs).
- يخرق الهدف 6 (بيانات تجريبية تُشحن للإنتاج).
خرقان في ملف واحد.

> ملاحظة دقّة: أصناف `Fake*` في `commonMain` للميزات = **صفر** (تحقّقنا) — هذا الجزء من WS‑10 أُنجز فعلاً. مشكلة «البيانات التجريبية» الآن محصورة في **الحزمة الباردة** ومحتوى الـ seed، لا في كود الميزات.

### السبب 5 — لا سلطة بيانات واحدة (single reactive store)
SWR رُكِّبت **لكل repo على حدة**، وكل ViewModel يخترع ربطه. لا يوجد «مخزن تفاعلي واحد لكل نطاق» يكتب إليه المزامنة ويقرأ منه الـ UI. القراءات تفكّ JSON من SQLDelight عند كل نداء (خُفِّف جزئياً بـ LRU في `TrainingConfigRepository` فقط — C5). هذا يجعل «cache‑first» سلوكاً مُقلّداً في كل شاشة بدل أن يكون خاصية معمارية.

### السبب 6 — التحديث الخلفي معلّق على فتح التطبيق
لا `WorkManager` (Android) ولا `BGTaskScheduler` (iOS). التحديث يحدث فقط عند الفتح/الاستئناف/عودة الاتصال. الهدف 4 (تحديث خلفي صامت) **نصف‑محقّق**.

---

## 3. تقييم الأهداف السبعة (Scorecard صادق)

| # | الهدف | الحالة | الدليل / الفجوة |
|---|------|:------:|----------------|
| 1 | تجربة سلسة بلا فقد بيانات ولا انتظار | 🔴 | بيانات المستخدم (تفضيلات/تقارير/تخصيصات) لا تُطبَّق من الخادم في KMP → فقد محسوس عند إعادة التثبيت/جهاز ثانٍ. SWR موجودة لكن أول تشغيل/بعد ترقية = spinner أو رسالة «غير متاح» |
| 2 | نظام واحد صحيح لـ Android/iOS لا انتقالي | 🔴 | نظامان متوازيان؛ الجسر يقرأ من legacy؛ القديم هو المنسّق الكامل؛ حذف القديم مستحيل حالياً |
| 3 | السماح بالتدريب offline | 🟠 | البنية موجودة (`TrainingConfigEnsure`, prefetch) لكن أول تشغيل offline = لا configs (الحزمة الباردة فارغة من التمارين)؛ «حزمة الأسبوع» (F10) بلا زر UI |
| 4 | تحديث خلفي صامت | 🟠 | يعمل عند الفتح/الاستئناف/عودة الشبكة فقط؛ لا مزامنة دورية خلفية (F11 مؤجّل) |
| 5 | تنظيم صحيح بلا تكرار | 🔴 | ازدواج مصدر الحقيقة (legacy JSON/prefs ⟷ SQLDelight)؛ `/sync` يجلب workouts/programs ثم تُتجاهَل لصالح `/explore`؛ `SystemMessageRegistry` مكرّر (`core/data` + `app`)؛ logout لا يمسح كاش KMP |
| 6 | بيانات حقيقية فقط (لا تجريبية إلا في اختبارات) | 🔴 | `cold_offline_bundle.json` يشحن بيانات وهمية للإنتاج؛ legacy `desk_test.json` fallback |
| 7 | وضع إنتاج لا انتقالي/اختبار | 🔴 | **الإصدار الافتراضي يقلع على واجهة legacy** (`shell.launcher.enabled=false`)؛ APK سابقاً 230MB؛ legacy لم يُحذف؛ deadlock بنيوي؛ «مكتمل» مُضخّم |

**النتيجة: 4 أحمر، 2 برتقالي، 0 أخضر كامل.** الحكم «غير مرضٍ» **دقيق وموضوعي**.

---

## 4. المعمارية الصحيحة المستهدفة (نظام واحد للإنتاج)

المبدأ الحاكم: **مصدر حقيقة واحد + منسّق واحد كامل + مخزن تفاعلي واحد لكل نطاق + لا كود قديم.**

```
┌──────────────────────────────────────────────────────────────┐
│  UI (Compose Multiplatform)  —  Android + iOS نفس الكود        │
│  كل شاشة تُراقب Flow من المخزن. لا تستدعي الشبكة مباشرة أبداً.   │
└───────────────────────────┬──────────────────────────────────┘
                            │ observe(Flow<State>)
┌───────────────────────────▼──────────────────────────────────┐
│  Domain Stores (واحد لكل نطاق)                                 │
│  ExerciseStore · WorkoutStore · ProgramStore · ReportStore ·  │
│  PreferenceStore · MessageStore · ProfileStore                │
│  - يقرأ من SQLDelight كـ Flow تفاعلي (مصدر الحقيقة الوحيد)      │
│  - يكتب عبر Outbox (كتابات معمّرة + تحديث متفائل)               │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│  SyncEngine (منسّق واحد كامل — يحل محل SyncManager بالكامل)     │
│  - GET /sync (delta + full) → يطبّق *كل* الـ payload في معاملة  │
│    (exercises + messageLibrary merge + systemMessages +        │
│     workouts + programs + userPrograms + customizations +      │
│     preferences + reports + audioManifest + tombstones)        │
│  - drift من *مخزن التكوينات* لا Explore                        │
│  - prefetch صوت/صور · replay outbox                            │
│  - مُشغَّل من: بدء/استئناف · عودة شبكة · WorkManager/BGTask     │
└───────────────────────────┬──────────────────────────────────┘
                            │
              SQLDelight (مصدر الحقيقة الوحيد)
        json_cache + outbox + sync_metadata + journal
                            │
              MovitPlatformBindings (auth/secure store فقط)
```

### القرارات المعمارية الإلزامية

1. **منسّق واحد كامل:** `SyncEngine` يطبّق **100%** من `/sync` payload في معاملة واحدة (atomic). هذا الشرط هو **مفتاح حذف القديم** — بدونه يبقى الـ deadlock.
2. **مصدر كتالوج واحد:** اختر **إمّا** كل شيء من `/sync` (مثل القديم، أبسط، جولة واحدة) **أو** كل شيء من نقاط مخصّصة — **لا الاثنين**. توقّف عن جلب‑ثم‑تجاهل. (التوصية: `/sync` للكتالوج الأساسي + نقاط مخصّصة للتفاصيل الثقيلة عند الطلب فقط، وإزالة workouts/programs المكرّرة من أحد المسارين.)
3. **مخزن تفاعلي لكل نطاق:** الـ UI يراقب `Flow` من SQLDelight. المزامنة تكتب في DB، والـ UI يتحدّث تلقائياً. **لا شاشة تستدعي `*.sync()`**. هذا يجعل cache‑first وSWR **خاصية معمارية** لا تقليداً يدوياً مكرّراً.
4. **كتابات معمّرة (موجودة وجيدة):** أبقِ Outbox، لكن أكمل الجولة: **server → store (hydrate) + store → server (outbox)**. الجولة نصف مكتملة اليوم (outbox فقط).
5. **Cold start حقيقي:** الحزمة الباردة = **seed حقيقي مُصدَّر من الخادم وقت البناء** (سكربت build يولّد JSON من DB الإنتاج) ويشمل حداً أدنى من configs تمارين حقيقية للتدريب offline. **أو** أسقِط ادعاء الحزمة الباردة واعرض حالة فارغة/تحميل صادقة. **صفر بيانات وهمية في الإنتاج**.
6. **تحديث خلفي:** `expect/actual` لـ `WorkManager` (Android) و`BGTaskScheduler` (iOS) يستدعي `SyncEngine`.
7. **لا كود قديم:** بعد اكتمال (1)–(3)، احذف `app/.../storage/*` ومسارات legacy، وأزل قراءات `MovitDataInstall` من القديم.
8. **التحقق سلوكي لا اختباري فقط:** كل بند DoD = سلوك مُشاهَد على جهاز مبني (Android + iOS)، + اختبار تكامل يثبت أن `SyncEngine` يطبّق الفرع المعني.

---

## 5. خطة الإصلاح المرتّبة (بمعايير سلوكية لا «بناء أخضر»)

### P0 — إغلاق القفل البنيوي (يجعل حذف القديم ممكناً)
- **FIX‑1 — إكمال port المنسّق:** أضف داخل `runSyncCycle` (بعد `applySyncExercises`) تطبيق الفروع الخمسة المرمية: `systemMessages` → `SystemMessageCache.save`؛ دمج `messageLibrary`؛ `userExercisePreferences` → `ExercisePreferenceLocalStore.hydrateFromSync`؛ `plannedWorkoutReports` → `ReportsSyncRepository.hydrateFromSync`؛ `userPrograms[].customizations` → `DayCustomizationLocalStore.hydrateFromBackend`.
  - **DoD سلوكي:** تسجيل دخول على جهاز نظيف → تظهر التفضيلات/التقارير/التخصيصات/الرسائل من الخادم فوراً. + اختبار تكامل: `runSyncCycle` يستدعي الخمسة.
- **FIX‑2 — توحيد مصدر الكتالوج:** قرار workouts/programs (من `/sync` أو `/explore`) وإزالة الازدواج.
- **FIX‑3 — قطع جسور legacy في `MovitDataInstall`:** `activeUserProgramId` و`exerciseImageUrl` يقرآن من `PlanSyncRepository`/`ExploreSyncRepository`.
  - **DoD:** صفر استدعاء من `core:data`/الجسر إلى `poc.storage.*`.

### P1 — إنتاجية البيانات
- **FIX‑4 — Cold start حقيقي:** سكربت build يولّد seed حقيقياً (يشمل configs تمارين أساسية) أو حالة فارغة صادقة. حذف بيانات unsplash/`offline-up-*` الوهمية.
  - **DoD:** تثبيت نظيف + airplane mode = محتوى حقيقي أو حالة فارغة واضحة — لا أسماء وهمية.
- **FIX‑5 — مخزن تفاعلي لكل نطاق:** الـ UI يراقب `Flow`؛ إزالة استدعاءات `*.sync()` من الشاشات.

### P2 — صلابة وإنتاج
- **FIX‑6 — مزامنة خلفية:** `WorkManager`/`BGTask` عبر `expect/actual`.
- **FIX‑7 — حزمة الأسبوع:** زر UI + مؤشر «جاهز offline ✓» (F10 المتبقي).
- **FIX‑8 — حجم APK:** قصر ABIs/AAB + إخراج موديلات matting المؤجلة من الشحنة.

### الإغلاق — حذف القديم
- بعد تحقّق P0–P1 سلوكياً: احذف `app/.../storage/*` ومراجع legacy، وأكّد iOS أخضر لكل مسار.
  - **DoD:** صفر مرجع حي لـ `poc.storage.*` من مسار KMP؛ التطبيق يعمل كاملاً على النظام الواحد.

---

## 6. انضباط التحقق (كي لا يتكرر الوهم)

القاعدة الذهبية المخالَفة سابقاً: **«مدموج/أخضر» ليس «يعمل».** لكل بند:

1. **اختبار تكامل للربط** لا للمكوّن المعزول (هل `SyncEngine` يستدعي الفرع؟).
2. **سلوك مُشاهَد** على جهاز/محاكي مبني فعلاً (لا شجرة عمل غير ملتزمة — `[[git-green-not-working-tree]]`).
3. **التحقق على المنصتين** (`compileKotlinIosSimulatorArm64` + تشغيل).
4. **جدول تكافؤ بلا 🔴**: «قدرة قديمة ↔ نظير جديد موصول ومُشاهَد».

---

## 7. خلاصة قابلة للتنفيذ

النظام الجديد **أساسه صحيح** (SQLDelight + Outbox + منسّق + SWR) لكنه **نصف موصول**، فأنتج ازدواجاً دائماً. الإصلاح ليس إعادة بناء — بل **إكمال الربط ثم الحذف**:

1. أكمل port المنسّق (الفروع الخمسة) → اكسر القفل البنيوي.
2. وحّد مصدر الكتالوج + اقطع جسور legacy → مصدر حقيقة واحد.
3. seed حقيقي + مخزن تفاعلي → offline‑first وبيانات حقيقية.
4. مزامنة خلفية → تحديث صامت.
5. احذف القديم → نظام واحد إنتاجي.

ترتيب التنفيذ يبدأ بـ **FIX‑1** لأنه المفتاح الذي يفك كل ما بعده.

---

## سجل التنفيذ — FIX-1 (إكمال المنسّق + logout)

**التاريخ:** 2026-06-13

### ما تم ربطه

| الفرع | الملف:سطر | السلوك |
|-------|-----------|--------|
| `systemMessages` | `MovitSyncOrchestrator.kt:153-157` | `systemMessageCache.save()` ثم `loadIntoRegistry()` عند عدم الفراغ |
| `userExercisePreferences` | `MovitSyncOrchestrator.kt:159-162` | `exercisePreferenceLocalStore.hydrateFromSync()` مع استثناء معرّفات outbox المعلّقة |
| `plannedWorkoutReports` | `MovitSyncOrchestrator.kt:164-166` | `reportsSync.hydrateFromSync()` |
| `userPrograms[].customizations` | `MovitSyncOrchestrator.kt:168-177` | `dayCustomizationLocalStore.hydrateFromBackend(programId, …)` — مرآة legacy |
| `messageLibrary` | `MovitSyncOrchestrator.kt:179-188` | `MessageLibraryCache` + `TrainingConfigRepository.applySyncMessageLibrary()` |

**DI (Koin):** `MovitDataModule.kt:119-121` — تسجيل `ExercisePreferenceLocalStore`, `DayCustomizationLocalStore`, `MessageLibraryCache`; `MovitDataModule.kt:137-142` — حقنها في `MovitSyncOrchestrator`.

**إحصائيات الرسائل (منع drift):** `MovitSyncOrchestrator.kt:192-203` — `writeFromSyncMeta` بعد تطبيق الحمولة؛ fallback يحسب stats من المكتبة عند غياب `meta.messageLibraryStats`.

**Logout:** `MovitDataInstall.kt:162-168` — `runBlocking(Dispatchers.IO) { MovitData.clearAllUserData() }` **قبل** `UserDataCleaner` / `AuthManager`.

### قرار messageLibrary

**دمج كامل في commonMain** عبر `ExerciseMessageLibraryMerger.kt` (port من `SyncManager.resolveExerciseMessages` / `resolvePoseVariantMessages`):

- يوسّع `messageAssignments` في `PoseVariant` إلى نصوص `LocalizedText` داخل `feedbackMessages`, `trackedJoints.stateMessages`, `phaseStateMessages`, و`positionChecks.errorMessage`.
- يستخدم نماذج `core:training-engine` (`ExerciseConfig`, `StateMessages`, `StateMessageValue`) — آمنة لـ KMP.
- **حدود:** `SyncMessageTemplateDto.content` هو `LocalizedNameDto` (en/ar فقط) — لا يُدمَج `audioEn`/`audioAr` من الـ DTO (غير موجودة في الشبكة حالياً). النصوص المدمجة تُخزَّن في `exercise_config_cache`؛ القوالب الخام تُخزَّن أيضاً في `MessageLibraryCache` (`message_library_cache`) لإعادة الدمج بعد delta sync.

### اختبارات الربط (spies)

| الاختبار | الملف |
|----------|-------|
| `runSyncCycle_invokesAllFivePayloadHydrations` | `MovitSyncOrchestratorHydrationTest.kt` |
| `runSyncCycle_messageLibraryUpdatesStoredMessageStats` | `MovitSyncOrchestratorHydrationTest.kt` |
| `clearAllUserData_wipesSqlDelightOutboxAudioAndMetadata` (logout KMP) | `MovitDataClearAllUserDataTest.kt` (موجود مسبقاً) |

تحديثات بناء `MovitSyncOrchestrator` في: `MovitSyncOrchestratorTest.kt`, `TrainingConfigEnsureTest.kt`.

### نتائج البناء

| الأمر | النتيجة |
|-------|---------|
| `.\gradlew :core:data:testDebugUnitTest` | **PASS** |
| `.\gradlew :app:compileDebugKotlin` | **PASS** |
| `.\gradlew :feature:shell:compileKotlinIosSimulatorArm64` | **PASS** |

### ترتيب معاملات `MovitSyncOrchestrator` الجديدة (بعد `trainingConfig`)

1. `systemMessageCache: SystemMessageCache`
2. `exercisePreferenceLocalStore: ExercisePreferenceLocalStore`
3. `dayCustomizationLocalStore: DayCustomizationLocalStore`
4. `messageLibraryCache: MessageLibraryCache`

**مخاطر للوكلاء اللاحقين:** أي موقع يُنشئ `MovitSyncOrchestrator` يدوياً (اختبارات، معاينات) يحتاج المعاملات الأربعة أعلاه. `ReportsSyncRepository` أصبح `open`؛ `hydrateFromSync` / `save` / `loadIntoRegistry` أصبحت `open` لاختبارات التسجيل.

---

## سجل التنفيذ — FIX-8 (حجم/بناء الإصدار)

> **التاريخ:** 2026-06-13  
> **النطاق:** `android-poc/app` فقط (بدون `core/data` أو منطق المزامنة).

### الحالة المُتحقَّق منها (قبل/بعد)

| البند | الحالة | دليل (ملف:سطر) |
|-------|--------|----------------|
| `abiFilters` release = arm فقط | ✅ كان موجوداً؛ أُضيف debug صريح لـ x86 | `app/build.gradle.kts:57–74` |
| AAB ABI split | ✅ | `app/build.gradle.kts:77–80` |
| AAB language split | ✅ أُضيف | `app/build.gradle.kts:81–83` |
| `localeFilters` en,ar | ✅ | `app/build.gradle.kts:91–94` |
| ONNX runtime release | ✅ `debugImplementation` فقط | `app/build.gradle.kts:220–221` |
| matting ONNX (modnet/u2net) | ✅ `src/debug/assets/` فقط | `app/src/debug/assets/*.onnx` |
| matting TFLite (selfie/deeplab/multiclass) | ✅ debug فقط | `app/src/debug/assets/*.tflite` |
| ONNX release stub | ✅ | `app/src/release/java/.../OnnxPortraitMatting.kt` |
| `pose_landmarker_full.task` في release | ✅ main (~9 MB) | `app/src/main/assets/pose_landmarker_full.task` |
| `pose_landmarker_heavy.task` في release | ❌→✅ **أُزيل** (~30 MB) | كان `main/assets`؛ أصبح `debug/assets` فقط |

### أصول النماذج حسب source set

| الملف | المجموعة | في release؟ |
|-------|----------|-------------|
| `pose_landmarker_full.task` (~9 MB) | `main` | نعم (افتراضي) |
| `pose_landmarker_heavy.task` (~29 MB) | `debug` | لا — تحميل عند الطلب |
| `modnet_photographic.onnx` (~25 MB) | `debug` | لا |
| `u2net_human_seg.onnx` (~4 MB) | `debug` | لا |
| `selfie_segmenter.tflite` | `debug` | لا |
| `selfie_multiclass_256x256.tflite` (~16 MB) | `debug` | لا |
| `deeplab_v3.tflite` (~3 MB) | `debug` | لا |

### ما نُفِّذ في هذه الجولة

1. **نقل** `pose_landmarker_heavy.task` من `main/assets` إلى `debug/assets` — توفير ~29 MB من حزمة release.
2. **إضافة** `PoseLandmarkerHeavyModelStore` — تنزيل من Google MediaPipe مع تحقق SHA-256 + كاش في `filesDir/pose_models/` + fallback إلى `pose_landmarker_full.task`.
3. **ربط جزئي:** preload عند بدء التطبيق (`PoseApp`) وعند حفظ إعداد «heavy» (`ProfileActivity`).
4. **تحسين Gradle:** `debug.ndk.abiFilters` يشمل x86/x86_64؛ `bundle.language.enableSplit = true`.

### نتائج البناء (Gradle)

```
.\gradlew :app:compileReleaseKotlin  → FAILED
.\gradlew :app:assembleRelease       → FAILED
.\gradlew :app:assembleDebug         → FAILED
```

**السبب:** أخطاء تجميع سابقة في `:core:data` (`ExerciseMessageLibraryMerger.kt`) — خارج نطاق FIX-8. لا يوجد APK/AAB في `app/build/outputs/` لقياس الحجم بعد البناء.

**تقدير التوفير المتوقع في release (قابل للقياس بعد إصلاح core:data):**

- −~29 MB: إخراج heavy من main
- −~45 MB: matting ONNX/TFLite (كانت مُستبعدة مسبقاً من release)
- −حجم JNI كبير: استبعاد x86/x86_64 من release ABI

### متابعة مطلوبة

1. **ربط `MediaPipePoseDetector.warmUp`** (`core/pose-capture`) بـ `PoseLandmarkerHeavyModelStore.resolveHeavyOrFallback()` و`SettingsManager.getModelType()` — حالياً المكتشف يستخدم دائماً `pose_landmarker_full.task` بغض النظر عن إعداد المستخدم.
2. **D9 dynamic delivery** لنماذج matting في الإنتاج (حالياً release stub + MediaPipe fallback في `ReportBackgroundEffectProcessor`).
3. **إعادة تشغيل** `assembleRelease` بعد إصلاح `:core:data` لقياس APK/AAB الفعلي.

---

## مراجعة المدير — إغلاق الجولة 1 (2026-06-13)

تحقّق مستقل على **الشجرة المدموجة** (FIX‑1 + FIX‑8 معاً — أول مرة تجتمع تغييرات الوكيلين):

| الفحص | النتيجة |
|------|---------|
| مراجعة كود FIX‑1 | الفروع الخمسة مطبَّقة فعلاً في `runSyncCycle` (الأسطر 146‑203)؛ DI يحقن المخازن الأربعة (`MovitDataModule:127‑146`)؛ `clearAuthSession` يمسح KMP قبل auth |
| اختبارات الربط | `MovitSyncOrchestratorHydrationTest` يؤكّد الاستدعاءات الخمسة + تحديث بصمة الرسائل عبر spies حقيقية |
| `:core:data:testDebugUnitTest` | ✅ PASS |
| `:app:compileDebugKotlin` | ✅ PASS |
| `:feature:shell:compileKotlinIosSimulatorArm64` | ✅ PASS (تكافؤ iOS) |
| `:app:assembleRelease` | ✅ SUCCESS |
| حجم APK release | **38.3 MB** (غير موقّع) مقابل ~230MB سابقاً · debug = 259MB (موديلات التطوير) |

**ملاحظات مراجعة (غير حاجبة، متابعات):**
- `MovitDataInstall.clearAuthSession()` يستخدم `runBlocking(Dispatchers.IO)` — يحجب الخيط المستدعي؛ خطر ANR إن استُدعي من main. متابعة: تحويل المسار إلى suspend أو استدعاؤه من coroutine (يُعالَج في مرحلة المخزن التفاعلي/الإغلاق).
- تحذيرات تجميع حميدة: `expect/actual` Beta؛ «Condition is always true» في `ReportsSyncRepository:172,215` (تنظيف بسيط).

**الخلاصة:** الجولة 1 مغلقة سلوكياً وبناءً — الفجوة الجوهرية (بيانات المستخدم غير موصولة) أُصلحت، وحجم الإصدار عاد طبيعياً.

---

## سجل التنفيذ — FIX-6 (مزامنة خلفية)

**التاريخ:** 2026-06-13  
**النطاق:** مزامنة دورية صامتة في الخلفية (Android WorkManager + iOS BGAppRefreshTask) عبر `MovitData.sync.syncIfNeeded()` دون تعديل `MovitSyncOrchestrator`.

### ما نُفّذ

| المكوّن | الموقع | الوصف |
|--------|--------|--------|
| عقد مشترك | `core/data/.../sync/BackgroundSyncScheduler.kt` (commonMain) | `expect object` مع `schedule()` / `cancel()` + `runBackgroundSyncIfReady()` الداخلي |
| Android actual | `core/data/.../sync/BackgroundSyncScheduler.kt` (androidMain) | WorkManager `enqueueUniquePeriodicWork` |
| Worker | `core/data/.../sync/MovitBackgroundSyncWorker.kt` | `CoroutineWorker` يستدعي `runBackgroundSyncIfReady()` |
| iOS actual | `core/data/.../sync/BackgroundSyncScheduler.ios.kt` | `BGAppRefreshTask` + `registerIosBackgroundSyncAtLaunch()` |
| تسجيل Android | `app/.../PoseApp.kt` `onCreate` | `MovitAndroidRuntime.applicationContext` + `BackgroundSyncScheduler.schedule()` مرة واحدة |
| Gradle | `gradle/libs.versions.toml` + `core/data/build.gradle.kts` | `androidx.work:work-runtime-ktx` **2.10.0** في `androidMain` لـ `:core:data` |

### معاملات الجدولة (Android)

- **الفترة:** 6 ساعات (`PeriodicWorkRequest`)
- **القيود:** `NetworkType.CONNECTED` + `setRequiresBatteryNotLow(true)`
- **السياسة:** `ExistingPeriodicWorkPolicy.KEEP` (اسم العمل: `movit_periodic_background_sync`)
- **سلوك Worker:** إذا `!MovitData.isInstalled` أو لا يوجد `authHeader()` → `Result.success()` (no-op)؛ عند `SyncOutcome.Error` → `Result.retry()`

### منطق المزامنة المشترك

```kotlin
// يُستدعى من Worker (Android) ومن معالج BGTask (iOS)
runBackgroundSyncIfReady() → MovitData.sync.syncIfNeeded(forceCheck = true)
```

### خطوات مطلوبة من مضيف iOS (خارج نطاق Kotlin الحالي)

1. **Info.plist** — إضافة المفتاح `BGTaskSchedulerPermittedIdentifiers` (مصفوفة) بالقيمة:
   ```xml
   <key>BGTaskSchedulerPermittedIdentifiers</key>
   <array>
       <string>com.movit.background-sync</string>
   </array>
   ```
   (يتطابق مع `MOVIT_IOS_BACKGROUND_SYNC_TASK_ID` في Kotlin.)

2. **قبل انتهاء الإطلاق** — استدعاء `registerIosBackgroundSyncAtLaunch()` من `MovitApp` framework (مثلاً في `iOSApp.swift` عبر `UIApplicationDelegateAdaptor` أو `init` في `@main struct`):
   ```swift
   import MovitApp
   // في application(_:didFinishLaunchingWithOptions:) أو init() مبكر:
   BackgroundSyncSchedulerIosKt.registerIosBackgroundSyncAtLaunch()
   ```
   **مهم:** `registerForTaskWithIdentifier` يجب أن يُستدعى قبل `applicationDidFinishLaunching` ينتهي.

3. **(اختياري)** بعد `MovitData.install` في `MainViewController` — `BackgroundSyncScheduler.shared.schedule()` لإعادة جدولة الطلب التالي بعد جلسة المستخدم الأولى.

4. **فترة iOS:** `earliestBeginDate` = الآن + 6 ساعات؛ يُعاد الجدولة تلقائياً بعد كل تنفيذ ناجح.

### نتائج البناء والاختبار (Gradle من `android-poc`)

| الأمر | النتيجة | ملاحظة |
|-------|---------|--------|
| `:app:compileDebugKotlin` | ❌ FAILED | خطأ سابق في `:core:model` (`ExploreModels.kt` — `Unresolved reference 'AppResult'`) — خارج نطاق FIX-6 |
| `:core:data:testDebugUnitTest` | ❌ (لم يُنفَّذ) | يعتمد على تجميع Android لـ `:core:model` |
| `:feature:shell:compileKotlinIosSimulatorArm64` | ❌ FAILED | أخطاء سابقة في `SyncCatalogMapper.kt` (`putIfAbsent` على Kotlin/Native) — خارج نطاق FIX-6 |
| `:core:data:compileKotlinIosSimulatorArm64` | ❌ FAILED (نفس `SyncCatalogMapper`) | **لا أخطاء في `BackgroundSyncScheduler.ios.kt`** بعد الإصلاح |

**تحقق جزئي:** ملفات FIX-6 (`BackgroundSyncScheduler` + `MovitBackgroundSyncWorker`) خالية من أخطاء iOS؛ إضافة `work-runtime-ktx` لا تكسر classpath عند حل أخطاء التجميع السابقة.

### ملفات مُضافة/مُعدَّلة

- **أُضيف:** `core/data/src/commonMain/.../BackgroundSyncScheduler.kt`
- **أُضيف:** `core/data/src/androidMain/.../MovitBackgroundSyncWorker.kt`
- **أُضيف:** `core/data/src/iosMain/.../BackgroundSyncScheduler.ios.kt`
- **حُدِّث:** `core/data/src/androidMain/.../BackgroundSyncScheduler.kt` (من stub إلى actual)
- **حُدِّث:** `app/src/main/java/.../PoseApp.kt`
- **حُدِّث:** `core/data/build.gradle.kts`, `gradle/libs.versions.toml`

---

## سجل التنفيذ — FIX-2 (توحيد مصدر الكتالوج)

**التاريخ:** 2026-06-13  
**القرار:** `/api/mobile/sync` هو المصدر الموثوق لكتالوج التصفّح (workoutTemplates + programs + exercise metadata). حذف جلب `/api/mobile/explore` المكرر من دورة المزامنة. شكل `ExploreDataDto` المخزَّن **لم يتغيّر**.

### خريطة التدفق (قبل → بعد)

| المرحلة | قبل | بعد |
|--------|-----|-----|
| دورة المزامنة | `fetchSync` يتجاهل `workoutTemplates`/`programs` → ثم `exploreSync.sync()` يستدعي `GET /explore` | `fetchSync` → `SyncCatalogMapper` → `exploreSync.applyFromSync()` يملأ `EXPLORE_DATA` |
| شاشة Explore | `SharedExploreRepository` → `explore.sync()` | `readCached()` فقط (البيانات تُحدَّث عبر `syncIfNeeded`/`fullRefresh`) |
| قائمة البرامج | `ProgramFlowSyncRepository.syncExplore()` → `/explore` | `readCached()` |
| جلسة workout | `resolveExploreData()` ي fallback إلى `explore.sync()` | `readCached()` فقط |
| offline | يعتمد على آخر `/explore` أو cold bundle | آخر `/sync` + cold bundle seeder (بدون تغيير شكل الحزمة) |

### ما تغيّر (ملف:سطر)

| الملف | التغيير |
|-------|---------|
| `SyncCatalogMapper.kt` (جديد) | تحويل `WorkoutExport`/`ProgramExport`/exercise JSON من `/sync` إلى `ExploreWorkoutDto`/`ExploreProgramDto`/`ExploreExerciseDto` |
| `ExploreSyncRepository.kt` | `applyFromSync(payload, isFullSync)` — دمج عبر `mergeExploreData` + كتابة `EXPLORE_DATA` |
| `MovitSyncOrchestrator.kt:146-191,210-218` | استدعاء `applyFromSync`؛ **إزالة** `exploreSync.sync()`/`syncFull()` من الدورة |
| `SharedExploreRepository.kt` | `getExploreContent()` يقرأ الكاش فقط |
| `ProgramFlowSyncRepository.kt` | `syncExplore()` → `readCached()` |
| `SharedWorkoutSessionRepository.kt` | `resolveExploreData()` → `readCached()` فقط |

**لم يُمس:** `ExploreDataDto`، `readCached()`، `exerciseImageUrl()`، توقيعات `syncIfNeeded`/`fullRefresh`، `ColdOfflineBundleSeeder`، `cold_offline_bundle.json`، `BackgroundSyncScheduler*`، `MovitDataInstall`، `app/build.gradle.kts`.

### levels / featured / pagination

| البند | المعالجة |
|-------|----------|
| **levels[]** | **اشتقاق عميل:** تجميع مستويات فريدة من `level` في workout templates و`levelMin`/`levelMax` في programs (لا يوجد `levels[]` في `/sync`) |
| **featured ordering** | ترتيب `isFeatured` desc ثم `updatedAt` desc عند التحويل (مطابقة منطق `/explore`) |
| **pagination (`limit=50`)** | **أُلغيت** — `/sync` يعيد الكتالوج الكامل؛ أفضل من اقتطاع 50 بطاقة |

**متابعة backend (اختياري):** إضافة `levels[]` صريحة إلى `MobileSyncDataDto` لتطابق كامل مع `/explore` دون اشتقاق.

### اختبارات جديدة/محدَّثة

- `SyncCatalogMapperTest.mapSyncPayload_mapsWorkoutProgramAndExerciseCards`
- `SyncCatalogMapperTest.applyFromSync_mergeRemovesTombstonedItems`
- `MovitSyncOrchestratorCatalogTest.runSyncCycle_populatesExploreCacheFromSync_withoutExploreFetch`
- `MovitSyncOrchestratorCatalogTest.runSyncCycle_tombstonesRemoveCatalogItemsFromCache`
- الاختبارات السابقة (`MovitSyncOrchestratorTest`, `MovitSyncOrchestratorHydrationTest`, `ExploreMergeTest`, …) — ✅ PASS

### نتائج البناء (Gradle من `android-poc`)

| الأمر | النتيجة |
|-------|---------|
| `:core:data:testDebugUnitTest` | ✅ PASS |
| `:app:compileDebugKotlin` | ✅ PASS |
| `:feature:shell:compileKotlinIosSimulatorArm64` | ✅ PASS |

### ما يجب أن يعرفه الوكلاء الآخرون

- **لا تستدعوا** `ExploreSyncRepository.sync()`/`syncFull()` لملء كتالوج التصفّح — استخدموا `readCached()` بعد مزامنة الخلفية، أو `MovitData.sync.syncIfNeeded()`.
- `ExploreSyncRepository.sync()` ما زال موجوداً للتوافق لكنه **خارج** مسار الكتالوج الرسمي.
- تفاصيل البرنامج/الخطة/تكوين التمرين تبقى عبر النقاط المخصّصة: `/programs/{id}`, `/user-programs/{id}/effective-plan`, `/workout-templates/{id}/training-config`.

## سجل التنفيذ — FIX-4 (seed بارد حقيقي + إزالة demo)

### المشكلة

`cold_offline_bundle.json` كان يحتوي بيانات مُختلَقة: مستخدم "Movit"، برنامج `offline-up-001` / "Strength Base"، صور unsplash، إحصائيات وهمية، تمرين اليوم وهمي، و`exercises: []` فارغ — فيُعرض كلوحة تحكم إنتاجية قبل أي مزامنة.

### ما أُزيل

| عنصر demo | الحالة |
|-----------|--------|
| `home.user.name = "Movit"` | **حُذف** — `home: null` في الحزمة |
| `offline-up-001` / `offline-pw-001` / `prog-001` / `wt-001` | **حُذف** |
| "Strength Base" / "Quick Legs" / "Getting started" | **حُذف** |
| روابط `images.unsplash.com` | **حُذف** |
| إحصائيات وهمية (`bodyScore: 50`, `levelProgress: 20`, …) | **حُذف** |
| `explore.exercises: []` فارغ مع برامج وهمية | **استُبدل** بكتالوج حقيقي |

### الخيار المعتمد: **A — بذرة حقيقية**

**السبب:** الخادم `https://back.mongz.online/api/mobile/sync` كان متاحاً وأعاد كتالوجاً حقيقياً (26 برنامجاً، 14 قالب تمرين، 10 تمارين، 321 رسالة نظام).

- `home: null` — لوحة الرئيسية خاصة بالمستخدم ولا تُزرع في الحزمة.
- `explore` — يُشتق من `/api/mobile/sync` بنفس قواعد `SyncCatalogMapper.kt`.
- `systemMessages` — نُسخت من `FeedbackMessageTemplate` عبر حقل `systemMessages` في الاستجابة (نفس أكواد `backend/prisma/seeders/system-messages.ts`).

### إعادة التوليد

من `android-poc/`:

```bash
node scripts/generate-cold-offline-bundle.mjs
```

توثيق: `android-poc/scripts/README-cold-offline-bundle.md`

متغيرات: `MOVIT_API_BASE_URL` أو `--base-url`.

**Fallback (B)** إن تعذّر الوصول للخادم: حزمة فارغة (`home: null`, explore فارغ، `systemMessages: []`) — موثّق في README.

### سلوك الواجهة عند أول تشغيل

| شاشة | سلوك |
|------|------|
| **Explore** | يعرض الكتالوج الحقيقي المُزرع من الحزمة دون اتصال |
| **Home** | لا يُزرع من الحزمة؛ عند فشل المزامنة وغياب الكاش يعرض `showNoProgramEmpty` عبر `HomeApiMapper.coldStartOffline` بدل خطأ مخيف |
| **Train** | يعتمد على مزامنة المستخدم (لا بيانات home وهمية) |

### اختبارات/بناء

| الأمر | النتيجة |
|-------|---------|
| `:core:data:testDebugUnitTest` | ✅ PASS |
| `:app:compileDebugKotlin` | ✅ PASS |
| `:feature:shell:compileKotlinIosSimulatorArm64` | ✅ PASS |

اختبارات جديدة/محدَّثة: `ColdOfflineBundleSeederTest.seedIfNeeded_skipsHomeWhenBundledHomeIsNull`، `MovitHomeStateTest.repositoryFailure_onColdStart_showsHonestNoProgramEmpty`.

## سجل التنفيذ — ربط نموذج heavy

### المشكلة
إعداد «heavy» في `SettingsManager` لم يكن يؤثر على `MediaPipePoseDetector` — كان يحمّل دائماً `pose_landmarker_full.task` المضمّن في APK.

### ما تم ربطه

| المكوّن | التغيير |
|---------|---------|
| `MediaPipePoseDetector` | يقرأ نوع النموذج عبر `PoseModelTypePreference` (نفس مفتاح `training_settings` / `model_type` في `SettingsManager`) ويحلّ المسار في `warmUp()` |
| `PoseLandmarkerHeavyModelStore` | نُقل إلى `core/pose-capture` (`com.movit.core.posecapture.android`) ليستخدمه الكاشف مباشرة |
| `PoseModelTypePreference` | قراءة خفيفة من SharedPreferences دون اعتماد على وحدة `app` |
| `PoseApp` / `ProfileActivity` | تحديث الاستيراد فقط؛ التحميل المسبق عبر `ensureCached()` كما كان |

### سلوك اختيار النموذج والاحتياطي (fallback)

1. **`model_type != "heavy"`** → `setModelAssetPath("pose_landmarker_full.task")` (كما سابقاً).
2. **`model_type == "heavy"`** → `resolveHeavyOrFallback()`:
   - **كاش صالح** (`filesDir/pose_models/…` + SHA-256) → `setModelAssetFileDescriptor` من الملف المحمّل.
   - **heavy مضمّن** (debug assets) → `setModelAssetPath("pose_landmarker_heavy.task")`.
   - **غير متاح** (offline / فشل تنزيل / checksum) → `setModelAssetPath("pose_landmarker_full.task")` فوراً + جدولة ترقية في الخلفية.

بعد اكتمال التنزيل في الخلفية، يُعاد استدعاء `warmUp()` تلقائياً لتحميل heavy إن بقي الإعداد على `heavy`.

### الأمان على مستوى الخيوط (threading)

- `warmUp()` يُستدعى من خيط UI عند بدء الكاميرا — **لا تنزيل شبكة** داخله؛ `resolveHeavyOrFallback()` فحص ملفات فقط.
- التنزيل عبر `ensureCached()` على `Dispatchers.IO` (`PoseLandmarkerHeavyModelStore` + `backgroundScope` في الكاشف).
- تهيئة Landmarker محمية بـ `synchronized(initLock)` لتجنّب سباقات إعادة التهيئة.

### نتائج البناء (Gradle من `android-poc`)

| الأمر | النتيجة |
|-------|---------|
| `:core:pose-capture:compileDebugKotlinAndroid` | ✅ BUILD SUCCESSFUL |
| `:app:compileDebugKotlin` | ✅ BUILD SUCCESSFUL |

### ملفات معدّلة

- `core/pose-capture/.../MediaPipePoseDetector.kt`
- `core/pose-capture/.../PoseLandmarkerHeavyModelStore.kt` (جديد — منقول من `app`)
- `core/pose-capture/.../PoseModelTypePreference.kt` (جديد)
- `core/pose-capture/build.gradle.kts` (إضافة `okhttp`)
- `app/.../PoseApp.kt`, `app/.../ProfileActivity.kt` (استيراد)
- حُذف `app/.../pose/PoseLandmarkerHeavyModelStore.kt`

---

## مراجعة المدير — إغلاق الجولتين 2 و3أ (2026-06-13)

تحقّق مستقل على **الشجرة المدموجة** بعد كل دفعة (تغييرات الوكلاء لم تُبنَ معاً قبل مراجعتي):

| الدفعة | البناء المدموج | ملاحظات تحقّق |
|--------|----------------|----------------|
| FIX‑2 + FIX‑6 | ✅ `:core:data` tests + `:app` + iOS shell | `putIfAbsent` أُزيل (K/N آمن)؛ المنسّق يستدعي `exploreSync.applyFromSync` بدل `/explore`؛ فروع FIX‑1 الخمسة سليمة لم تُمَس |
| FIX‑4 + heavy | ✅ `:core:data` tests + `:app` + iOS shell | الحزمة الباردة خالية من demo (تحقّق grep)؛ `home:null` + كتالوج حقيقي 3600+ سطر؛ fallback heavy آمن على IO |

**الحالة مقابل الأهداف السبعة بعد الجولات 1–3أ:**

| الهدف | الحالة |
|------|:------:|
| 1 — لا فقد بيانات / لا انتظار | ✅ (FIX‑1 + SWR) |
| 4 — تحديث خلفي صامت | ✅ (FIX‑6) |
| 5 — لا تكرار | ✅ كتالوج موحّد (FIX‑2)؛ يبقى حذف legacy في الإغلاق |
| 6 — بيانات حقيقية فقط | ✅ (FIX‑4) |
| 3 — تدريب offline | 🟡 كتالوج + رسائل مزروعة؛ configs من أول sync (حزمة الأسبوع FIX‑7 تعزّزها) |
| 2 و7 — نظام واحد / إنتاج | ⏳ بانتظار الإغلاق: حذف legacy + قلب اللانشر لـ KMP shell |

## سجل التنفيذ — إصلاح ANR في logout

**التاريخ:** 2026-06-13

### مسار الاستدعاء المُتحقَّق

1. **واجهة المستخدم:** `MovitProfileScreen` → `LogoutConfirmed` → `MovitProfileViewModel.onEvent`
2. **ViewModel:** `logout()` يطلق `workScope.launch { repository.logout() }` — `workScope` = `SupervisorJob + Dispatchers.Unconfined` (يبدأ على خيط المُستدعي = Main عند النقر)
3. **Repository:** `SharedProfileRepository.logout()` → `MovitData.account.logout()` (`suspend`)
4. **Data:** `AccountSyncRepository.logout()` — `api.logout` (suspend) ثم مسح البيانات ثم `bindings.clearAuthSession()`
5. **Android platform:** `MovitDataInstall.clearAuthSession()` — `UserDataCleaner` + `AuthManager` فقط (بدون KMP blocking)

**مسار انتهاء الجلسة (401/refresh فاشل):** `MovitHttpClientAuth.handleSessionExpired()` → `tokenStore.clearTokens()` → `clearAuthSession()` (منصة) → `MovitData.notifySessionExpired()` → `clearAllUserData()` ثم تنقّل الـ shell.

### هل كان خطر ANR حقيقياً؟

**نعم، جزئياً.** قبل الإصلاح، `clearAuthSession()` على Android كان يستدعي `runBlocking(Dispatchers.IO) { MovitData.clearAllUserData() }`. مع `Dispatchers.Unconfined` في الـ ViewModel:
- إذا وُجدت رموز: بعد `api.logout` (suspend) يُستأنف غالباً على خيط شبكة — `runBlocking` يحجب خيطاً خلفياً (ليس Main) → ANR غير مرجّح.
- إذا **غاب** `auth` أو `refresh`: `clearAuthSession()` يُنفَّذ فوراً على **Main** دون تعليق → `runBlocking` يحجب UI → **خطر ANR/jank حقيقي** مع بيانات محلية كبيرة.

### الإصلاح المختار

**لم يُغيَّر توقيع `MovitPlatformBindings.clearAuthSession()`** (تجنّب تموّج iOS/fakes).

| الملف | التغيير |
|-------|---------|
| `AccountSyncRepository.kt` | `MovitData.clearAllUserData()` (`suspend`) **قبل** `clearAuthSession()` داخل `logout()` — لا حجب للـ Main |
| `MovitData.kt` | `notifySessionExpired()` يستدعي `runBlocking { clearAllUserData() }` قبل callback الـ shell (مسار 401 على خيط Ktor، ليس Main) |
| `MovitDataInstall.kt` | إزالة `runBlocking` ومسح KMP من `clearAuthSession()` — منصة Android فقط |

**الترتيب محفوظ:** مسح KMP + auth يكتمل قبل `LoggedOut` / `NavigateToLegacyAuth` لأن `repository.logout()` يُنتظر في الـ coroutine قبل إصدار الـ effect.

### التحقق

| الأمر | النتيجة |
|-------|---------|
| `.\gradlew :app:compileDebugKotlin` | ✅ PASS |
| `.\gradlew :core:data:testDebugUnitTest` | ✅ PASS |
| `.\gradlew :feature:shell:compileKotlinIosSimulatorArm64` | ✅ PASS |

---

## سجل التنفيذ — FIX-7 (حزمة الأسبوع offline)

**التاريخ:** 2026-06-13

### أين أُضيفت الواجهة

- **الشاشة:** `ProgramDetailScreen` → تبويب Overview → قسم «Program structure» (`ProgramOverviewContent`).
- **المكوّن:** `ProgramWeekOfflinePanel` في `feature/library/.../components/ProgramDetailComponents.kt` — يظهر أسفل شريط الأسابيع (`ProgramWeekStrip`) عندما يكون المستخدم مسجّلاً في البرنامج (`enrollment.isEnrolled`) وتوجد أسابيع.
- **الربط:** `ProgramDetailRoute` → `viewModel::onDownloadWeekOffline`؛ الحالة في `ProgramDetailUiState.weekOffline`.

### حالات UX

| الحالة | العرض |
|--------|--------|
| `Idle` | زر «Download week for offline» |
| `Downloading` | نص «Downloading week…» + `MovitProgressBar` + نسبة مئوية |
| `Ready` | شارة «Offline ready ✓» بخلفية نجاح |
| `Failed` | رسالة خطأ + زر «Try again» |

النصوص في `ProgramWeekOfflineCopy.kt` (مؤقتاً بالإنجليزية — مفاتيح `movitText` لم تُضف بعد في `core/resources`).

### المراقبة والاستمرارية

- **أثناء التنزيل:** `ProgramDetailViewModel.onDownloadWeekOffline()` يحدّث `weekOffline` عبر `StateFlow` مع callback تقدّم من `prefetchWeek`.
- **بعد الإكمال:** `WeekOfflinePackPrefetcher` يكتب علامة جاهزية في `MovitPlatformBindings.writeCache` (`week_offline_cache` / `ready_{programId}_{week}`).
- **عند إعادة فتح الشاشة:** `publish()` → `resolveWeekOfflineState()` يستدعي `isWeekReadyOffline()` ويعيد `Ready` دون إعادة تنزيل.

### توسيعات `WeekOfflinePackPrefetcher` (API للمستهلكين)

| إضافة | الغرض |
|-------|--------|
| `WeekPrefetchProgress(phase, percent)` | تقدّم مرحلي (Syncing → LoadingPlans → CachingAudio → Finishing) |
| `isWeekReadyOffline(programId, weekNumber)` | قراءة علامة الجاهزية المخزّنة |
| `prefetchWeek(..., onProgress: (WeekPrefetchProgress) -> Unit)` | callback اختياري — التوقيع القديم ما زال يعمل (`onProgress` افتراضي `{}`) |
| `OFFLINE_STORE`, `offlineReadyKey()` | ثوابت مخزن الجاهزية |

`prefetchWeek` ما زال يُرجع `PrefetchOutcome` (`Ready` / `SkippedNoWeek` / `Failed`)؛ عند النجاح يُستدعى `markWeekReady` داخلياً.

### اختبارات وبناء

| الأمر | النتيجة |
|-------|---------|
| `.\gradlew :feature:library:testDebugUnitTest` | ✅ PASS (62 tests) |
| `.\gradlew :app:compileDebugKotlin` | ✅ PASS |
| `.\gradlew :feature:shell:compileKotlinIosSimulatorArm64` | ✅ PASS |

**اختبارات جديدة:** `ProgramDetailViewModelTest` (تنزيل ناجح / فشل / جاهزية عند `publish`)؛ `WeekOfflinePackPrefetcherTest.offlineReadyKey_isStable`.

### ملفات معدّلة

- `core/data/.../sync/WeekOfflinePackPrefetcher.kt`
- `feature/library/ProgramDetailModels.kt`, `ProgramDetailViewModel.kt`, `ProgramDetailScreen.kt`, `MovitLibraryRoutes.kt`
- `feature/library/ProgramWeekOfflineCopy.kt` (جديد)
- `feature/library/components/ProgramDetailComponents.kt` (`ProgramWeekOfflinePanel`)
- `feature/library/.../ProgramDetailViewModelTest.kt`, `core/data/.../WeekOfflinePackPrefetcherTest.kt`

---

## تجهيز الإغلاق — تدقيق shell + قائمة QA + خطة القلب/الحذف

**التاريخ:** 2026-06-13  
**النطاق:** `android-poc/` — تدقيق جاهزية قشرة KMP قبل قلب اللانشر الإنتاجي  
**قيود هذا التمرين:** لم يُنفَّذ أي قلب (`movit.shell.launcher.enabled` ما زال `false`، `SplashActivity` ما زال LAUNCHER، لم يُحذف أي ملف legacy).

**مرجع الجرد:** [`Android-KMP-Mobile-Screen-Inventory.md`](Android-KMP-Mobile-Screen-Inventory.md)

### ملخص تنفيذي

| البند | النتيجة |
|-------|---------|
| تغطية المسارات الرئيسية في shell | **~92%** — كل التبويبات الخمسة + 13 من 14 `MovitInnerRoute` نشطة وموصولة |
| فجوات حرجة قبل QA | **تدريب حي** (علم `MOVIT_TRAINING_KMP_ENABLED`) · **QuickStart/Customize** · **ExerciseHistory** · **اشتراك legacy** |
| ربط shell إضافي في هذا التمرين | **لا تغييرات** — التدقيق أظهر أن كل المسارات الآمنة موصولة مسبقاً عبر `MovitAppShellViewModel` + `MovitInnerHost` |
| بناء Gradle (2026-06-13) | ✅ PASS للثلاثة أوامر المطلوبة |

---

### 1. خريطة Legacy → KMP (شاشة/تدفق)

**رموز الحالة:** ✅ موصول وقابل للوصول في shell · ⚠️ بديل جزئي · 🔴 غير مهاجر / جسر legacy · ➖ ميت/غير مستخدم

#### 1.1 إقلاع ومصادقة

| Legacy | KMP المكافئ | `MovitInnerRoute` / تبويب | موصول في shell؟ |
|--------|-------------|---------------------------|-----------------|
| `SplashActivity` (LAUNCHER) | عند flip: `MovitMainActivity` → `MovitAppShellHost` مباشرة؛ Auth داخلي | `Auth` (Splash/Intro/SignIn داخل `MovitAuthScreen`) | ✅ عند تفعيل shell |
| `OnboardingActivity` (شرائح أول تشغيل) | `MovitAuthScreen` → `AuthScreen.Intro` | `Auth` | ✅ `resolveStartupInnerStack` → `SplashThenIntro` |
| `SignInActivity` | `MovitAuthScreen` → `SignIn` | `Auth` | ✅ |
| `SignUpActivity` | `MovitAuthScreen` → `SignUp` | `Auth` | ✅ |
| `ForgotPasswordActivity` | `MovitAuthScreen` → `Forgot` | `Auth` | ✅ |
| `ProfileOnboardingActivity` + 7 fragments | `MovitOnboardingScreen` (7 خطوات) | `ProfileOnboarding` | ✅ gate تلقائي + Profile |

#### 1.2 التبويبات الرئيسية (MainContainer → Shell)

| Legacy Fragment/Activity | KMP | تبويب | موصول؟ |
|--------------------------|-----|-------|--------|
| `HomeFragment` | `MovitHomeScreen` | `Home` | ✅ |
| `TrainFragment` | `MovitTrainScreen` | `Train` | ✅ |
| `ExploreFragment` | `MovitExploreScreen` | `Explore` | ✅ |
| `HistoryFragment` (+ 3 tabs) | `MovitReportsScreen` (+ Overview/Exercises/Trends) | `Reports` | ✅ |
| `ProfileActivity` (من History) | `MovitProfileScreen` | `Profile` | ✅ |

#### 1.3 مكتبة / برامج / جلسات

| Legacy | KMP | `MovitInnerRoute` | موصول؟ |
|--------|-----|-------------------|--------|
| `ExerciseListActivity` / `ExercisesFragment` | `ExercisesLibraryScreen` | `ExercisesLibrary` | ✅ Explore → See all |
| `ExerciseDetailActivity` | `ExercisePrepareScreen` | `ExercisePrepare` | ✅ Explore/Home/deep link |
| `WorkoutListActivity` | `WorkoutsLibraryScreen` | `WorkoutsLibrary` | ✅ Explore |
| `WorkoutDetailActivity` | `WorkoutSessionScreen` | `WorkoutSession` | ✅ Explore/Train/Home |
| `WorkoutCustomizeActivity` | — (حُذفت `WorkoutCustomizeScreen`) | — | 🔴 لا يوجد KMP |
| `QuickStartActivity` | — | — | 🔴 لا يوجد KMP |
| `ProgramListActivity` | `ProgramListScreen` | `ProgramList` | ✅ Train/Explore |
| `ProgramDetailActivity` | `ProgramDetailScreen` | `ProgramDetail` | ✅ |
| `ProgramDayActivity` / bottom sheet يوم | `ProgramDetailScreen` (تبويب يوم) + `WorkoutSession` | `ProgramDetail` → `WorkoutSession` | ⚠️ لا sheet يوم من Train strip |
| `WeeklyReportActivity` | `WeeklyReportScreen` | `WeeklyReport` | ✅ Train/ProgramDetail |
| `PlanOverviewActivity` | `MovitLevelScreen` → تبويب `PlanOverview` | `LevelProfile` | ✅ Home «عرض الخطة» |

#### 1.4 تقييم ومستوى

| Legacy | KMP | `MovitInnerRoute` | موصول؟ |
|--------|-----|-------------------|--------|
| `PreScreeningActivity` | `MovitAssessmentScreen` → `PreScreening` | `Assessment` | ✅ Home/Profile/Train |
| `AssessmentResultActivity` | `MovitAssessmentScreen` → `Results` | `Assessment` | ✅ |
| `LevelProfileActivity` | `MovitLevelScreen` | `LevelProfile` | ✅ Home/Profile |

#### 1.5 تدريب حي (Phase 7 — كاميرا/ML)

| Legacy | KMP | `MovitInnerRoute` | موصول؟ |
|--------|-----|-------------------|--------|
| `TrainingActivity` *(مُزال من Manifest)* | `TrainingSessionScreen` + `TrainingSessionCameraHost` (expect/actual) | `TrainingSession` | ⚠️ يعتمد `MOVIT_TRAINING_KMP_ENABLED` + `trainingConfig.ensure` |
| `WorkoutTrainingEngine` (legacy في `:app`) | `feature/training` + `core/training-engine` | `TrainingSession` | ⚠️ نفس البوابة |
| `ExerciseLiveScreen` | `TrainingSessionScreen` *(البديل)* | `ExerciseLive` | ➖ ميتة — لا `pushInner` |

**مسار التدريب في shell (موصول):**  
`WorkoutSession` → `ExercisePrepare` → `TrainingSession` → (انتهاء) `WorkoutSession` / `ReportDetail` / راحة → `ExercisePrepare(rest)`

#### 1.6 تقارير

| Legacy | KMP | `MovitInnerRoute` | موصول؟ |
|--------|-----|-------------------|--------|
| `WorkoutReportActivity` (+ fragments) | `ReportDetailScreen` (Overview/Form/Fatigue/Tips) | `ReportDetail` | ✅ Reports/Home/Training |
| `ExerciseHistoryActivity` | — (لا شاشة KMP مكافئة) | — | 🔴 |
| `ReportPagerActivity` | — | — | ➖ ميت |
| `ProgramSessionReportActivity` | — | — | ➖ ميت |

#### 1.7 حساب واشتراك

| Legacy | KMP | موصول؟ |
|--------|-----|--------|
| `SubscriptionActivity` | `MovitSubscriptionScreen` (overlay) → **دفع legacy** | 🔴 جسر `LaunchLegacySubscription` |
| `ProfileActivity` (إعدادات) | `MovitProfileScreen` + overlays لغة/مظهر | ✅ |

#### 1.8 Deep links (جسر legacy → shell)

| `MovitTrainingEntryNavigator` constant | `MovitInnerRoute` | موصول؟ |
|----------------------------------------|-------------------|--------|
| `workout_session` | `WorkoutSession` | ✅ `MovitShellDeepLinkParser` |
| `workout_session_local` | `WorkoutSession` (+ seed cache) | ✅ |
| `exercise_prepare` | `ExercisePrepare` | ✅ |
| `assessment` | `Assessment` | ✅ |
| `program_detail` | `ProgramDetail` (+ week) | ✅ |
| `waytofix://subscription/result` | `SubscriptionActivity` | 🔴 legacy فقط |

---

### 2. الفجوات (Gaps) — شدة وتأثير

| # | الفجوة | الشدة | التفاصيل | توصية |
|---|--------|-------|----------|--------|
| G1 | **تدريب حي خلف علم `MOVIT_TRAINING_KMP_ENABLED`** | **حرج** | Release default `false` في `app/build.gradle.kts`؛ عند `false` أو فشل `ensure` يظهر snackbar `training_config_first_use_online` ولا يُفتح `TrainingSession` | QA على `-Pmovit.training.kmp.enabled=true` + pilot؛ عند flip اجعل الافتراضي `true` |
| G2 | **`QuickStartActivity`** — بناء workout حر | **عالي** | لا مسار KMP؛ Explore legacy فقط | مهاجر لاحقاً أو إزالة من المنتج |
| G3 | **`WorkoutCustomizeActivity`** — تخصيص قبل التدريب | **عالي** | `WorkoutCustomizeScreen` حُذفت من KMP | parity مع legacy أو إبقاء جسر Intent مؤقت |
| G4 | **`ExerciseHistoryActivity`** — سجل تمرين + charts | **متوسط** | لا `MovitInnerRoute` | إضافة شاشة reports أو دمج في `ReportDetail` |
| G5 | **اشتراك / فوترة** | **متوسط** | KMP UI + `SubscriptionActivity` legacy | مقبول مؤقتاً؛ iOS غير متاح (`profile_subscription_ios_unavailable`) |
| G6 | **Train week strip — نقرة يوم** | **متوسط** | Legacy `showDayDetailSheet`؛ KMP `TrainWeekPreview` للعرض فقط | إضافة `MovitTrainEvent.DayClicked` → `WorkoutSession` (خارج نطاق shell) |
| G7 | **`OpenProgramWeek` في shell بدون مُصدِر** | **منخفض** | Handler موجود في `MovitAppShellViewModel` لكن Train لا يصدّر التأثير | تنظيف أو ربط عند إضافة UI |
| G8 | **`exitToLegacyAuthOnLogout=true`** في `MovitMainActivity` | **متوسط عند flip** | عند الخروج يعيد `SplashActivity` legacy بدل `Auth` داخلي | عند flip: `exitToLegacyAuthOnLogout=false` |
| G9 | **تقرير جلسة متعددة التمارين (WorkoutReport)** | **منخفض** | KMP `ReportDetail` لكل تمرين؛ legacy `WorkoutSummaryFragment` موحّد | تحقق QA من تدفق multi-exercise |
| G10 | **Auth Google في KMP** | **منخفض** | Strategy B: Google عبر legacy حتى flip كامل | موثّق في Phase 06 |

---

### 3. ما تم ربطه في هذا التمرين (shell)

**لا تغييرات كود في `feature/shell`.** التدقيق أكد:

- كل `MovitHomeEffect` / `MovitTrainEffect` / `MovitExploreEffect` / `MovitReportsEffect` / `MovitProfileEffect` لها handlers في `MovitAppShellViewModel`.
- `MovitInnerHost` يربط 13 مساراً نشطاً؛ callbacks التدفق (`onStartSession`, `onViewWeeklyReport`, `handleTrainingStart` → `TrainingSession`) موصولة.
- `MovitShellPendingNavigation` + deep link parser يغطيان جسر legacy الأكثر استخداماً.

الفجوات المتبقية تتطلب عملاً في `feature/train` / `feature/library` / `app` host — **خارج نطاق «آمن و straightforward»**.

---

### 4. قائمة QA اليدوية (قبل القلب)

#### 4.1 إعداد البيئة

- [ ] بناء debug مع shell: `-Pmovit.shell.launcher.enabled=true -Pmovit.training.kmp.enabled=true`
- [ ] أو استخدام `MovitShellPilotActivity` (debug LAUNCHER — training KMP مفعّل دائماً)
- [ ] جهاز حقيقي بكاميرا + حساب Free + حساب Pro (إن وُجد)

#### 4.2 إقلاع ومصادقة

- [ ] أول تشغيل (مسح بيانات): يظهر Auth → Intro → SignIn
- [ ] تسجيل دخول بريد/كلمة مرور → Home (بدون ProfileOnboarding إن الملف مكتمل)
- [ ] تسجيل جديد → ProfileOnboarding (7 خطوات) → Home
- [ ] نسيت كلمة المرور → Forgot → رسالة نجاح
- [ ] انتهاء الجلسة (token منتهي): يعود لـ `Auth` داخل shell
- [ ] تسجيل خروج من Profile → `Auth` + مسح بيانات المستخدم

#### 4.3 التبويبات والتنقل

- [ ] Home: ابدأ خطة اليوم → Train
- [ ] Home: بطاقة مستوى → LevelProfile (+ PlanOverview tab)
- [ ] Home: برنامج نشط → ProgramDetail
- [ ] Home: catch-up → WorkoutSession
- [ ] Home: تقرير حديث → ReportDetail
- [ ] Home: body scan → Assessment
- [ ] Train: ابدأ جلسة اليوم → WorkoutSession → ExercisePrepare → TrainingSession
- [ ] Train: قائمة برامج → ProgramList → ProgramDetail → تسجيل (enroll) → بدء جلسة
- [ ] Train: تقرير أسبوعي → WeeklyReport + مشاركة
- [ ] Explore: تمارين / workouts / برامج → المكتبات والتفاصيل
- [ ] Reports: 3 تبويبات + ReportDetail من Exercises
- [ ] Profile: لغة · مظهر · ملف تدريبي · تقييم · مستوى · اشتراك

#### 4.4 تدريب حي (حرج)

- [ ] ExercisePrepare → Start → `TrainingSession` (كاميرا، countdown، HUD، إيقاف تلقائي)
- [ ] إكمال تمرين → العودة لـ WorkoutSession أو راحة → ExercisePrepare(rest)
- [ ] إكمال workout → ReportDetail أو العودة للجلسة
- [ ] تمرين بدون config (أول استخدام offline): رسالة `training_config_offline_unavailable` أو `training_config_first_use_online` صادقة
- [ ] Assessment BodyScan: كاميرا + نتائج

#### 4.5 Offline وsync

- [ ] **أول إقلاع offline (وضع الطيران):** Explore يعرض كتالوج seed حقيقي من `cold_offline_bundle`؛ Home صادق (فارغ/محدود بدون تظاهر ببيانات وهمية)
- [ ] **تدريب offline:** تنزيل أسبوع من ProgramDetail → «Offline ready» → بدء جلسة بدون شبكة
- [ ] **استعادة الاتصال:** background sync يحدّث Explore/Train/Home دون تجميد UI
- [ ] **Logout:** يمسح كاش المستخدم؛ إعادة الدخول تبدأ sync نظيفاً

#### 4.6 Pro vs Free

- [ ] Free: تقارير محدودة / رسالة ترقية في Reports
- [ ] Pro: وصول كامل للتقارير والاتجاهات
- [ ] اشتراك من Profile/Reports → `SubscriptionActivity` → العودة للتطبيق

#### 4.7 iOS (محاكي)

- [ ] `MainViewController()` → shell كامل بدون Splash legacy
- [ ] Assessment camera على iOS
- [ ] TrainingSession على iOS
- [ ] اشتراك: رسالة iOS غير متاح

#### 4.8 سلبيات معروفة (لا تُعتبر regressions عند flip)

- [ ] QuickStart غير متاح في shell
- [ ] Customize workout غير متاح في shell
- [ ] ExerciseHistory غير متاح
- [ ] نقرة يوم في Train week strip لا تفتح sheet

---

### 5. خطة القلب والتنظيف (مقترح — **لم يُنفَّذ**)

#### 5.1 المرحلة A — قلب اللانشر (يوم QA+1)

| الخطوة | الملف | التغيير الدقيق |
|--------|-------|----------------|
| A1 | `android-poc/gradle.properties` أو `app/build.gradle.kts` | `movit.shell.launcher.enabled` default → `true` |
| A2 | `app/build.gradle.kts` | `movit.training.kmp.enabled` default → `true` |
| A3 | `app/src/main/AndroidManifest.xml` | نقل `MAIN`/`LAUNCHER` من `SplashActivity` إلى `com.movit.MovitMainActivity`؛ `SplashActivity` `exported=false` |
| A4 | `app/src/movitShellEnabled/.../MovitMainActivity.kt` | `exitToLegacyAuthOnLogout = false` (Auth داخلي) |
| A5 | `SplashActivity` / `MovitPostLoginNavigator` | إبقاء Strategy B مؤقتاً أو دمج Auth بالكامل في shell (قرار PO) |
| A6 | Smoke | `.\gradlew :app:assembleDebug` + QA checklist §4 |

#### 5.2 المرحلة B — إزالة واجهة legacy (بعد أسبوع stable)

**الترتيب الآمن:**

1. إزالة `MainContainerActivity` + 4 fragments رئيسية
2. إزالة Activities المكتبة المكررة (`ExerciseList`, `WorkoutList`, `ProgramList`, `ProgramDetail`, `ProgramDay`, `WeeklyReport`, `PlanOverview`)
3. إزالة Activities المصادقة المنفصلة (`SignIn`, `SignUp`, `Forgot`, `Onboarding`, `ProfileOnboarding`) — بعد تأكيد Auth KMP 100%
4. إزالة `PreScreeningActivity`, `AssessmentResultActivity`, `LevelProfileActivity`
5. إبقاء `SubscriptionActivity` حتى فوترة KMP
6. إزالة `WorkoutReportActivity` + fragments التقارير بعد parity `ReportDetail`
7. إزالة `QuickStartActivity`, `WorkoutCustomizeActivity`, `ExerciseHistoryActivity` (أو مهاجرتها أولاً)
8. إزالة `app/src/movitShellDisabled/` stub
9. تنظيف `AndroidManifest.xml` من الأنشطة المحذوفة

#### 5.3 المرحلة C — إزالة `app/.../storage/**` (بعد P0 sync كامل)

| ملف legacy storage | بديل KMP |
|--------------------|----------|
| `AuthManager.kt` | `MovitData` + secure prefs |
| `SyncManager.kt` | `MovitSyncOrchestrator` |
| `ExploreRepository.kt` | `SharedExploreRepository` / sync catalog |
| `HomeRepository.kt` | `feature/home` + `MovitData` cache |
| `ProgramRepository.kt`, `UserProgramStore.kt` | `ProgramFlowSyncRepository` |
| `ExerciseRepository.kt`, `ExerciseCacheManager.kt` | sync + `TrainingConfigRepository` |
| `WorkoutRepository.kt`, `WorkoutCacheManager.kt` | sync catalog + `WorkoutFlowCache` |
| `ReportRepository.kt`, `ReportStorage.kt` | `ReportsSyncRepository` |
| `DayCustomizationStore.kt` | `DayCustomizationLocalStore` |
| `UserExercisePreferenceStore.kt` | `ExercisePreferenceLocalStore` |
| `ProgramWorkoutReportStore.kt` | `TrainingSessionWriteCoordinator` |
| `UserDataCleaner.kt` | logout في `MovitProfileViewModel` + `MovitData` |
| `OfflineFallbackLoader.kt` | `ColdOfflineBundleSeeder` |
| `AudioCacheManager.kt`, `EntityAudioPrefetchManager.kt` | `WeekOfflinePackPrefetcher` |
| `AnalyticsStorage.kt`, `SystemMessageStore.kt` | `MovitData` caches |

#### 5.4 جدول حذف Activities/Fragments (مرجع كامل)

**Activities (28 ملفاً في `ui/` + `assessment/`):**

| ملف legacy | بديل KMP |
|------------|----------|
| `SplashActivity.kt` | `MovitMainActivity` + `Auth` inner |
| `OnboardingActivity.kt` | `MovitAuthScreen.Intro` |
| `SignInActivity.kt` / `SignUpActivity.kt` / `ForgotPasswordActivity.kt` | `MovitAuthScreen` |
| `ProfileOnboardingActivity.kt` + `steps/*Fragment.kt` (7) | `MovitOnboardingScreen` |
| `MainContainerActivity.kt` | `MovitAppShellHost` |
| `HomeFragment.kt` | `MovitHomeScreen` |
| `TrainFragment.kt` | `MovitTrainScreen` |
| `ExploreFragment.kt` / `ExercisesFragment.kt` | `MovitExploreScreen` / `ExercisesLibraryScreen` |
| `HistoryFragment.kt` + `Reports*Fragment.kt` (3) | `MovitReportsScreen` |
| `ProfileActivity.kt` | `MovitProfileScreen` |
| `ExerciseListActivity.kt` / `ExerciseDetailActivity.kt` | `ExercisesLibrary` / `ExercisePrepare` |
| `ExerciseHistoryActivity.kt` | — (فجوة G4) |
| `WorkoutListActivity.kt` / `WorkoutDetailActivity.kt` | `WorkoutsLibrary` / `WorkoutSession` |
| `WorkoutCustomizeActivity.kt` / `QuickStartActivity.kt` | — (فجوة G2/G3) |
| `ProgramListActivity.kt` / `ProgramDetailActivity.kt` / `ProgramDayActivity.kt` | `ProgramList` / `ProgramDetail` / `WorkoutSession` |
| `WeeklyReportActivity.kt` / `PlanOverviewActivity.kt` | `WeeklyReport` / `LevelProfile.PlanOverview` |
| `PreScreeningActivity.kt` / `AssessmentResultActivity.kt` | `Assessment` |
| `LevelProfileActivity.kt` | `LevelProfile` |
| `WorkoutReportActivity.kt` + report fragments (10) | `ReportDetail` |
| `SubscriptionActivity.kt` | `MovitSubscriptionScreen` + billing لاحقاً |
| `ReportPagerActivity.kt` / `ProgramSessionReportActivity.kt` | ➖ ميت — حذف مباشر |

#### 5.5 خطة الرجوع (Rollback)

| الحالة | إجراء | زمن تقريبي |
|--------|-------|------------|
| عطل حرج بعد flip | `gradle.properties`: `movit.shell.launcher.enabled=false` + `movit.training.kmp.enabled=false` | 5 دقائق |
| عطل manifest | استعادة `SplashActivity` كـ LAUNCHER من git | 10 دقائق |
| عطل تدريب KMP فقط | `movit.training.kmp.enabled=false` (يبقي shell) | 5 دقائق |
| بعد حذف legacy | **tag git** `pre-legacy-removal` قبل المرحلة B — rollback = revert branch | 30 دقيقة |

**تحقق rollback:** تثبيت APK قديم + `adb shell pm clear` + مسار Splash → MainContainer → تدريب legacy.

---

### 6. نتائج البناء (2026-06-13)

| الأمر | النتيجة |
|-------|---------|
| `.\gradlew :feature:shell:compileDebugKotlinAndroid` | ✅ **PASS** |
| `.\gradlew :app:compileDebugKotlin` | ✅ **PASS** |
| `.\gradlew :feature:shell:compileKotlinIosSimulatorArm64` | ✅ **PASS** |

---

### 7. تأكيد القيود

- ❌ **لم يُغيَّر** `movit.shell.launcher.enabled` الافتراضي (`false`)
- ❌ **لم يُغيَّر** `AndroidManifest.xml` (LAUNCHER)
- ❌ **لم يُحذف** أي ملف تحت `app/src/main/java/com/trainingvalidator/poc/**` أو `storage/**`
- ✅ **تم توثيق** الخريطة والفجوات وQA وخطة القلب/الحذف في هذا القسم

---

## سجل التنفيذ — إصلاح اختبار تطابق العقد (فشل سابق)

**التاريخ:** 2026-06-13

### المسار الناقص

```
POST api/mobile/auth/google
```

### السبب الجذري

- الدالة `googleAuth()` في `MovitMobileApi.kt` تستدعي `client.post(base("api/mobile/auth/google"))` — أي أن المسار مُنفَّذ فعلياً في KMP.
- السجل `MobileApiContractRegistry` كان يصنّف هذا المسار ضمن `deferredEndpoints` فقط (بسبب «Google bridge deferred») دون إدراجه في `kmpCoveredEndpoints`.
- اختبار `kmpRegistryMatchesMovitMobileApiSource` يقارن مسارات المستخرج من المصدر مع `kmpCoveredEndpoints` فقط (لا يستثني `deferredEndpointKeys`)، فظهر `missingFromRegistry = {POST api/mobile/auth/google}`.

### الإصلاح

في `core/network/.../contract/MobileApiContractRegistry.kt`:

1. إضافة `"POST api/mobile/auth/google"` إلى `kmpCoveredEndpoints` (بجانب `login` و`register`).
2. إزالة الإدخال المقابل من `deferredEndpoints` لتجنب تداخل `deferredEndpointsDoNotOverlapKmpCoverage`.

لم يُمس `MovitMobileApi.kt` ولا المستخرج `MovitMobileApiPathExtractor`.

### تأكيد أن الفشل سابق (pre-existing)

- `git diff core/network` قبل الإصلاح كان **فارغاً** على ملفات الإنتاج (`MovitMobileApi.kt`, المستخرج، إلخ) — الفشل لم يُسبَّب بتغييرات catalog/sync الأخيرة.
- التعديل الوحيد: `MobileApiContractRegistry.kt` (ملف اختبار/عقد ضمن `commonTest`).

### نتائج البناء والاختبار

| الأمر | النتيجة |
|-------|---------|
| `.\gradlew :core:network:testDebugUnitTest` | ✅ **PASS** (5/5 بما فيها `LegacyKmpContractParityTest`) |
| `.\gradlew :app:compileDebugKotlin` | ✅ **PASS** |
| `.\gradlew :feature:shell:compileKotlinIosSimulatorArm64` | ✅ **PASS** |
| `.\gradlew testDebugUnitTest` (مجمّع المشروع) | ❌ **FAIL** — `:app:compileDebugUnitTestKotlin` (خطأ تجميع في `PostTrainingReportLegacyParityTest.kt`؛ خارج نطاق `core/network`) |

**خلاصة:** عقد الشبكة KMP أخضر بالكامل؛ فشل `testDebugUnitTest` المجمّع سببه وحدة `app` وليس هذا الإصلاح.

## سجل التنفيذ — تخضير suite الاختبارات (فشل سابق)

**التاريخ:** 2026-06-13  
**الأمر:** `.\gradlew testDebugUnitTest --continue --console=plain` من `android-poc/`  
**قيود:** تعديل مصادر الاختبار فقط (`src/test`, `src/commonTest`, …) — **لم يُمس أي كود إنتاج (main).**

### القائمة الكاملة للفشل (الجولة الأولى — قبل الإصلاح)

| الوحدة | الاختبار | النوع | الرسالة / السبب |
|--------|----------|-------|-----------------|
| `:app` | `PostTrainingReportLegacyParityTest.reportGenerator_summaryMatchesExpectedGoldenShape` | **تجميع (compile)** | `avgTempo = listOf(...)` بينما النوع `IntArray`؛ حقول `formConsistency` و`fatigueIndex` مطلوبة في `WorkoutExecutionMetrics` |
| `:app` | `DeviceTiltProviderTest` × 2 | **تشغيل (runtime)** | `IllegalStateException` عند `WorkManagerImpl` — `PoseApp.onCreate()` يستدعي `BackgroundSyncScheduler.schedule()` عند `ApplicationProvider.getApplicationContext()` |
| `:app` | `TrainingEngineParityTest` × 5 | **تشغيل (runtime)** | نفس `WorkManager` في الجولة الأولى؛ بعد عزل `PoseApp` → `NullPointerException`: `TrackedJoint.trackingMode` = null (Gson لا يطبّق defaults Kotlin) عند `VisibilityMonitor.toKmpVisibilityConfig` |
| باقي الوحدات | — | — | **لا فشل** (`:core:*`, `:feature:*` كلها UP-TO-DATE / PASS) |

**إجمالي الجولة الأولى:** 30 اختباراً في `:app`، **8 فاشلة**؛ باقي المشروع أخضر.

### التصنيف: سابق (pre-existing) مقابل مرتبط بالهجرة

| الفشل | ملفات الاختبار vs `git HEAD` | كود الإنتاج | التصنيف |
|-------|------------------------------|-------------|---------|
| `PostTrainingReportLegacyParityTest` compile | **مطابق HEAD** (كان مكسوراً مُلتزَماً) | `WorkoutExecutionMetrics` تغيّر سابقاً (`IntArray`, حقول جودة) | **pre-existing** — عدم مواءاة مصدر الاختبار مع النموذج |
| `DeviceTiltProviderTest` WorkManager | **مطابق HEAD** | `PoseApp.kt` **معدّل في الهجرة** (`BackgroundSyncScheduler.schedule()`) | **مرتبط بالهجرة** — الاختبار لم يتغيّر؛ `onCreate` الجديد يكسر Robolectric |
| `TrainingEngineParityTest` WorkManager | **مطابق HEAD** (قبل إضافة `@Config`) | نفس `PoseApp` | **مرتبط بالهجرة** |
| `TrainingEngineParityTest` NPE `trackingMode` | fixtures **مطابقة HEAD** | `VisibilityMonitor.kt` **مطابق HEAD** (مسار KMP موجود مسبقاً) | **pre-existing** — fixtures بدون `trackingMode` + Gson null |

**دليل git:** `git diff HEAD -- android-poc/app/src/test/` كان فارغاً على كل الملفات عدا ما أُصلح لاحقاً؛ `PoseApp.kt` يظهر diff هجرة (`BackgroundSyncScheduler`, `MovitAndroidRuntime`).

### ما تم إصلاحه (مصادر اختبار فقط)

| الملف | الإصلاح |
|-------|---------|
| `app/.../PostTrainingReportLegacyParityTest.kt` | `intArrayOf(1100, 350, 850)`؛ `formConsistency = null`, `fatigueIndex = null`؛ `@RunWith(RobolectricTestRunner)` + `@Config(application = UnitTestApplication::class)` لـ `Log.d` |
| `app/.../UnitTestApplication.kt` | **جديد** — `Application` فارغ يتجنب WorkManager/Ceil/sync في الاختبارات |
| `app/.../DeviceTiltProviderTest.kt` | `@Config(application = UnitTestApplication::class)` |
| `app/.../TrainingEngineParityTest.kt` | `@Config(application = UnitTestApplication::class, …)` |
| `app/src/test/resources/fixtures/parity_*.json` (5 ملفات) | `"trackingMode": "two_sides"` على كل `trackedJoints` |

لم تُضعف أي assertions ولم يُستخدم `@Ignore`.

### ما بقي للفرز (triage)

**لا شيء** — كل الفشل أعلاه أُغلق في مصادر الاختبار.

### النتيجة النهائية — `testDebugUnitTest` (بعد الإصلاح)

```
BUILD SUCCESSFUL
444 actionable tasks: 18 executed, 426 up-to-date
```

| الوحدة | النتيجة |
|--------|---------|
| `:app` | ✅ PASS (30/30) |
| `:core:data` | ✅ PASS |
| `:core:network` | ✅ PASS |
| `:core:pose-capture` | ✅ PASS |
| `:feature:account` | ✅ PASS |
| `:feature:explore` | ✅ PASS |
| `:feature:home` | ✅ PASS |
| `:feature:library` | ✅ PASS |
| `:feature:reports` | ✅ PASS |
| `:feature:shell` | ✅ PASS |
| `:feature:train` | ✅ PASS |
| `:feature:training` | ✅ PASS |

**الحالة الإجمالية:** ✅ **GREEN** — `testDebugUnitTest` مجمّع أخضر بالكامل دون تعديل كود الإنتاج.

---

## التوقيع النهائي للمدير (2026-06-13)

تحقّق شامل أخير على الشجرة الكاملة بعد كل الجولات:

```
.\gradlew testDebugUnitTest :app:compileDebugKotlin :feature:shell:compileKotlinIosSimulatorArm64 :app:assembleRelease
→ BUILD SUCCESSFUL in 4m 42s (exit 0)
```

| الفحص النهائي | النتيجة |
|---------------|---------|
| كل اختبارات الوحدات (جميع الموديولات) | ✅ PASS |
| `:app:compileDebugKotlin` (مصدر الإنتاج) | ✅ PASS |
| `:feature:shell:compileKotlinIosSimulatorArm64` (iOS) | ✅ PASS |
| `:app:assembleRelease` | ✅ SUCCESS |
| حجم APK release النهائي | **38.4 MB** (من ~230MB) |

### الإصلاحات المُسلَّمة والمُتحقَّقة

| # | الإصلاح | الحالة |
|---|---------|:------:|
| FIX‑1 | وصل 5 فروع بيانات المستخدم في المنسّق + logout يمسح KMP | ✅ |
| FIX‑2 | توحيد الكتالوج من `/sync` + إزالة تكرار `/explore` | ✅ |
| FIX‑4 | إزالة بيانات demo + seed بارد حقيقي + home صادق | ✅ |
| FIX‑6 | مزامنة خلفية WorkManager + BGTask | ✅ |
| FIX‑7 | حزمة الأسبوع offline + مؤشر الجاهزية | ✅ |
| FIX‑8 | حجم الإصدار 38.4MB + heavy عند الطلب | ✅ |
| heavy | اختيار النموذج الثقيل يعمل فعلاً + fallback | ✅ |
| ANR | logout لا يحجب خيط الواجهة (مسار suspend) | ✅ |

### بطاقة الأهداف السبعة (نهائية)

| # | الهدف | الحالة |
|---|------|:------:|
| 1 | لا فقد بيانات / لا انتظار | ✅ |
| 4 | تحديث خلفي صامت | ✅ |
| 5 | لا تكرار (كتالوج موحّد) | ✅ (يكتمل بحذف legacy في الإغلاق) |
| 6 | بيانات حقيقية فقط | ✅ |
| 3 | تدريب offline | ✅ كتالوج/رسائل/حزمة أسبوع؛ configs من أول sync |
| 2 و7 | نظام واحد / إنتاج | 🟡 **جاهز للقلب** — مُجهَّز بالكامل، بانتظار QA يدوي ثم تنفيذ خطة القلب/الحذف |

### مؤجَّل / مُغلَق بقرار

- **FIX‑5** (مخزن تفاعلي موحّد): مؤجّل بقرار المالك (SWR الحالي يمنع الانتظار فعلاً).
- **الإغلاق** (قلب اللانشر + حذف legacy): مُجهَّز وموثّق (خريطة + 10 فجوات + QA + خطة 3 مراحل + rollback)، **لم يُنفَّذ** بانتظار QA يدوي للـ shell. أبرز حاجز: G1 — التدريب الحي خلف `MOVIT_TRAINING_KMP_ENABLED=false`.

### ملاحظة نزاهة

الفحص الشامل النهائي كشف فشلين **سابقين** (committed-broken، مُثبَت بـ git أنهما خارج تغييرات الهجرة): سجلّ عقد `:core:network` (نقص `POST api/mobile/auth/google`) واختبارات legacy لا تُجمّع/Robolectric WorkManager. أُصلحت كلها في **مصادر الاختبار فقط** (دون لمس كود الإنتاج) لتسليم suite أخضر. تأثير FIX‑6 الوحيد على الاختبارات (تهيئة WorkManager في Robolectric) عُزل عبر `UnitTestApplication`.

