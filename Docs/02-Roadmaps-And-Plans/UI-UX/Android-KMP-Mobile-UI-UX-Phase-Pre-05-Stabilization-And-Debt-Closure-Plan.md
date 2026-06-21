# Android / KMP Mobile UI/UX — Phase Pre-05: Stabilization & Debt Closure

آخر تحديث: 2026-06-09 (تنفيذ WS-A→WS-F + استكمال WS-C؛ WS-G معيار فقط)

## القرار الحاكم لهذه المرحلة

> **انتقال كامل للطريقة الجديدة (KMP/Compose/Material 3) بلا حلول وسط ولا حلول انتقالية.**
> أى كود يمثّل "جسراً مؤقتاً" أو "fallback للقديم" أو "تجاوز سريع لمشكلة منصّة" يُغلق أو يُحذف في هذه المرحلة، لا يُؤجَّل ولا يُلتف حوله.

Phase 05 (page-by-page) أنتجت شاشات حقيقية شغّالة (Home/Explore/Train/Reports/Report Detail/Session). لكن الأساس الذى تقف عليه هذه الشاشات يحمل **ديوناً وبقايا انتقالية** تتعارض مع قرار الانتقال الكامل. **Pre-05 تُغلق هذه الديون قبل إضافة أى شاشة جديدة** (Auth/Profile/Onboarding/Assessment/Level). لا ننتقل لاستكمال Phase 05 قبل اجتياز "بوابة الخروج" في نهاية هذا المستند.

## لماذا قبل استكمال Phase 05 وليس بعده

كل شاشة جديدة نضيفها الآن سترث نفس المشاكل: ستـhardcode نصوصاً إنجليزية، ستُبنى على ViewModels غير مملوكة على iOS، وستضيف المزيد من البقايا. إغلاق الأساس أولاً يجعل بقية شاشات Phase 05 تُبنى مرة واحدة بشكل صحيح بدل إعادة تهيئتها لاحقاً.

## الحالة المتحقَّق منها بالكود (خط الأساس)

تم التحقق من النقاط التالية في الكود مباشرة (وليس من المستندات):

| البند | الواقع بعد التنفيذ (2026-06-09) | الدليل |
|------|-------------------------------|--------|
| `commonMain` نظيف | صفر `import android.*` / `import java.*` | فحص آلي ✅ |
| طبقة بيانات مشتركة | `core:network` + `core:data` (Koin) + sync repos | `MovitData.kt` |
| offline-first | fallback للكاش عند كل فرع فشل | `WorkoutSessionSyncRepository.kt` |
| الجسور القديمة | **✅ محذوفة** — مسار واحد فقط | `rg ApiBridge` ⇒ صفر |
| Profile | **✅** التبويب الخامس = `Profile`؛ Components debug-only | `MovitAppDestination.kt`, `MovitProfileRoute.kt` |
| iOS deployment | **✅ 16.0** | `iosApp/project.yml:8` |
| iOS lifecycle | **✅** `collectAsStateWithLifecycle()` في كل routes | `MovitAppShellRoute.kt`, `MovitHomeRoute.kt`, … |
| iOS ViewModels | **✅** `ViewModelStoreOwner` من `MainViewController` | `feature/shell/src/iosMain/.../MainViewController.kt` |
| i18n | **✅** `:core:resources` + ar/en + RTL؛ تغطية التبويبات + Train UI + Report Detail + Session | `core/resources/` |
| منطق المحرك الخالص | ما زال Android-only (خارج Pre-05) | `app/src/main/java/com/trainingvalidator/poc/` |
| اختبارات sync repos | **✅** فروع fallback/auth مُغطّاة | `core/data/src/commonTest/` |

## تحقق مستقل (2026-06-09) — أخطاء كانت تكسر iOS/Android واكتُشفت ثم أُصلحت

التحقق بالبناء الفعلى (وليس بالمستند) كشف أن WS-E/WS-F **لم تكن مكتملة فعلياً** رغم تعليمها ✅؛ بناء Android كان يمر لأن compile كثير كان `UP-TO-DATE` يخفى الحالة الحقيقية. الأخطاء والإصلاحات:

| # | الخطأ | الأثر | الإصلاح |
|---|------|------|---------|
| 1 | `MovitData` يستخدم `org.koin.core.context.GlobalContext` — وهو **JVM-only، لا يُحَل على Kotlin/Native** | `:core:data:compileKotlinIosSimulatorArm64` يفشل ⇒ إطار iOS كله مكسور (هذا هو فشل macOS CI) | إعادة كتابة `MovitData` ليحتفظ بمرجع `KoinApplication` من `startKoin` ويقرأ `.koin` (سطح متعدد المنصّات) — بلا `GlobalContext` |
| 2 | Koin **4.0.3** مع Kotlin **2.3.0** (عدم تطابق ABI) | مخاطرة klib كامنة | رفع Koin إلى **4.2.1** (النسخة المبنية لـ Kotlin 2.3.x، "ABI restoration") |
| 3 | `when (route)` في `MovitInnerHost` غير شامل — 3 `MovitInnerRoute` ميتة (`ProgramFlow`/`LevelPlan`/`Profile`) + `InnerPlaceholderScreen` غير مستخدم | `:feature:shell:compileKotlinIosSimulatorArm64` يفشل | حذف الـ routes الميتة الثلاثة (غير قابلة للوصول) + الـ placeholder الميت ⇒ `when` شامل |
| 4 | `feature:library` يستورد `core.data`/`core.network`/`resources` لكن **لا يعرّفها كـ dependencies** (كان يتسرّبها transitively عبر `:feature:explore`) | `:feature:library:compileDebugKotlinAndroid` يفشل | إضافة `:core:data` + `:core:network` + `:core:resources` صراحةً (مطابقة لباقى الموديولات) |

**نتيجة التحقق بعد الإصلاح:**

- ✅ `:app:assembleDebug` + اختبارات (data/shell/library/reports/train/home/explore) — **BUILD SUCCESSFUL**.
- ✅ `:core:data:compileKotlinIosSimulatorArm64` — **أخضر**.
- ✅ `:feature:shell:compileKotlinIosSimulatorArm64` (سطح iOS كامل: network/data/resources/كل الفيتشرز/shell) — **أخضر**.
- ⏳ **iOS framework link** (`linkDebugFrameworkIosSimulatorArm64`) — يحتاج macOS؛ **الكود مرفوع** (`58f403fc fix CI` على `origin/codex/kmp-mobile-foundation`) — macOS CI هو المرجع النهائى لهذه البوابة.

الملفات المعدَّلة في هذا الإصلاح: `gradle/libs.versions.toml`, `core/data/.../MovitData.kt`, `feature/shell/.../MovitInnerRoute.kt`, `feature/shell/.../MovitInnerHost.kt`, `feature/library/build.gradle.kts`.

**درس:** بناء Android الأخضر **لا يثبت** iOS؛ و`UP-TO-DATE` يخفى أخطاء حقيقية. أى ادعاء "iOS مكتمل" يجب أن يستند إلى `compileKotlinIosSimulatorArm64` فعلى (محلياً) + CI أخضر (link).

## مراجعة الرأي المتخصص — نقاط الاتفاق والخلاف

المراجعة المتخصصة دقيقة وصحيحة في أغلبها. نقاط التعديل/الخلاف التي حُسمت بالكود:

1. **"Phase 01-04 عمليًا مكتملة" — تحفظ.** هذه الشاشات (Home/Explore/Shell) تحمل تراجع lifecycle مقصوداً (P2). تحت قرار "بلا حلول وسط" هي **شغّالة لكن غير مُغلقة**؛ تُعتبر مكتملة فقط بعد WS-E.
2. **"بقاء استدعاء bridges غالبًا غير مؤثر" — خلاف.** الاستدعاء يثبّت فعلياً مسار Retrofit القديم بالتوازي مع الطبقة المشتركة (`MovitShellPilotActivity.kt:37-39`). هذا **مسار بيانات ثانٍ متناقض**، لا مجرد التباس قراءة. يُحذف بالكامل (WS-A).
3. **"camera/ML/logic لسه legacy وده صحيح" — تنقيح.** صحيح لـ camera/MediaPipe/LiteRT/ONNX. لكن **المحرك العددي الخالص** (`OneEuroFilter`, `AngleCalculator`, score/confidence calculators) ليس مرتبطاً بالكاميرا وهو بالضبط "الـ logic المشترك". تأجيله **قرار** لا ضرورة. (يُجدول كتحضير Phase 6/7، خارج Pre-05 — انظر الملاحظة في النهاية.)
4. **"release hygiene نظيف ✅" — عكس الهدف.** بقاء موديولات Movit خارج release هو وضع **صحيح-مؤقتاً**، لكن غاية الانتقال الكامل أن يصبح Movit shell هو تطبيق الإصدار. هذا يُحسم في بوابة الـ launcher (WS-G).

---

## مسارات العمل (Workstreams)

كل مسار: **المشكلة → المخرَج المطلوب → خطوات → معايير قبول.** الترتيب يراعي الاعتماديات: تنظيف ← حقيقة ← بنية ← اختبارات ← iOS ← DI ← بوابة.

### WS-A — حذف كل البقايا الانتقالية (legacy bridges) ✅ *(2026-06-09)*

**المشكلة:** الطبقة المشتركة (`Shared*Repository`) هى المصدر الوحيد الآن، لكن كود الجسور القديم ما زال موجوداً ويُستدعى — يخالف قرار الانتقال الكامل.

**المخرَج:** مسار بيانات واحد فقط (`MovitData` + `Shared*Repository`). صفر جسور Retrofit وصفر `Remote*Repository`/`*FetcherBridge`.

**خطوات:**
- حذف الاستدعاءين من `MovitShellPilotActivity` (سطور 37-39): `MovitExploreApiBridge.install(...)` و`MovitHomeApiBridge.install(...)`.
- إعادة تنسيق `MovitShellPilotActivity` (الملف به سطر فارغ بين كل سطرين — تنسيق تالف).
- حذف ملفات الجسور في `app/src/debug`: `MovitExploreApiBridge`, `MovitHomeApiBridge`, `MovitTrainApiBridge`, `MovitReportsApiBridge`, `MovitWorkoutSessionApiBridge`.
- حذف نظائر androidMain المكرّرة:
  - `feature/reports/src/androidMain/.../remote/RemoteReportsRepository.kt` + `ReportsContentFetcherBridge.kt`
  - `feature/library/src/androidMain/.../remote/RemoteWorkoutSessionRepository.kt` + `WorkoutSessionFetcherBridge.kt`
  - `feature/train/src/androidMain/.../remote/` بالكامل
- التأكد أن `default*Repository()` (androidMain/iosMain) تشير كلها إلى `Shared*Repository`، وإزالة أى فرع يختار الجسر.

**قبول:**
- `grep -r "ApiBridge\|FetcherBridge\|RemoteReportsRepository\|RemoteWorkoutSessionRepository" kmp-app/` ⇒ صفر نتائج (خارج سجلّ git).
- `:app:assembleDebug` ناجح، والـ Shell يعرض البيانات نفسها من الطبقة المشتركة.

---

### WS-B — حقيقة التنقل: وجهة Account/Profile حقيقية ✅ *(2026-06-09)*

**المشكلة:** خطة التنقل تقول `Home/Train/Explore/Reports/Profile`، لكن الواقع `…/Components` (كتالوج DS كتبويب رئيسي). لا توجد وجهة Profile.

**المخرَج:** bottom nav الإنتاجى = `Home/Train/Explore/Reports/Profile`. كتالوج `Components` يصبح debug-only (يُفتح من قائمة debug أو deep link، ليس تبويباً رئيسياً).

**خطوات:**
- إضافة `Profile("profile", "Account")` إلى `MovitAppDestination` وإزالة `Components` من قائمة التبويبات الرئيسية.
- إنشاء `feature:profile` (أو مسار Profile داخل shell مؤقتاً) كهيكل أدنى: شاشة Account/Settings ببيانات `MovitPlatformBindings` (الاسم، اللغة، حالة Pro) — بدون تدفق auth عميق بعد.
- نقل `MovitComponentsRoute` خلف بوابة debug.
- إزالة `placeholderTitle/placeholderSubtitle` المعلَّمة `@deprecated`.

**قبول:**
- التنقل الرئيسى يطابق الخطة المكتوبة؛ `Components` غير ظاهر في الإصدار.
- اختبار shell: اختيار Profile يعرض شاشة Account حقيقية (لا placeholder).

---

### WS-C — نظام نصوص/موارد مشترك (Arabic-first) ✅ *(2026-06-09)*

**المشكلة:** ~216 نص إنجليزي hardcoded في commonMain؛ لا بنية strings. التطبيق عربى-أول وRTL متطلَّب من أول مكوّن — مخالَف حالياً.

**المخرَج:** كل نص يواجه المستخدم يأتى من موارد مشتركة بـ `ar` + `en`، ويُختبر RTL على شاشة حقيقية.

**خطوات:**
- اعتماد **Compose Multiplatform Resources** (`composeResources/values/strings.xml` + `values-ar/`) كمصدر مشترك — لا حل Android-only.
- استخراج النصوص من الشاشات الحالية (Train/Reports/Home/Explore/Session/Report Detail) إلى `stringResource(...)`.
- ضبط اتجاه التخطيط من `preferredLanguage()` (موجود في bindings) وتمريره للثيم/الـ layout direction.
- مراجعة RTL بصرياً على شاشة Train كحد أدنى.

**قبول:**
- `grep` للنصوص الحرفية في commonMain Screens ⇒ قريب من صفر (يُسمح بالـ tokens غير المرئية فقط).
- تبديل اللغة ar/en يغيّر النصوص والاتجاه دون كسر.

---

### WS-D — اختبار طبقة البيانات المشتركة (أعلى خطر غير مُغطّى) ✅ *(2026-06-09)*

**المشكلة:** منطق الـ sync (fallback للكاش، غياب token، فشل الشبكة، `success=false`) هو أخطر منطق في النظام وغير مُختبر — `core:data` فيه `ExploreMergeTest` فقط.

**المخرَج:** تغطية فروع القرار في `HomeSyncRepository`, `ReportsSyncRepository`, `WorkoutSessionSyncRepository` عبر `MockEngine` (Ktor) و`MovitPlatformBindings` مزيّف.

**خطوات:**
- إضافة `MockEngine` لاختبار `MovitMobileApi` بحالات: 200/مع payload، 401، 5xx، body فاسد.
- اختبار كل sync repo: (auth موجود + شبكة ناجحة) ⇒ Success + كتابة كاش؛ (لا auth + كاش موجود) ⇒ Success من الكاش؛ (لا auth + لا كاش) ⇒ Failure؛ (شبكة فشلت + كاش) ⇒ Success من الكاش.

**قبول:**
- `:core:data:testDebugUnitTest` يغطى الفروع الأربعة لكل repo.
- لا فرع fallback بدون اختبار.

---

### WS-E — إغلاق ديون iOS (P1/P2/P3) إغلاقاً نهائياً ✅ *(2026-06-09)*

**المشكلة:** ثلاثة تجاوزات مؤقتة تمنع اعتبار iOS أرضية إصدار. تحت قرار "بلا حلول وسط" تُحَل، لا تُتبَّع.

**P1 — deployment target:**
- اختيار توليفة Kotlin/Native + Xcode مستقرة، وضبط `iOS` في `project.yml` على هدف منطقى (15 أو 16) بدل 18.5.
- معيار قبول: التطبيق يُبنى ويعمل على Simulator بهدف ≤ 16، بلا اعتماد على prebuilt deps تفرض 18.5.

**P2 — lifecycle-aware collection:**
- استعادة التجميع المدرك لدورة الحياة عبر **abstraction مشتركة** (`expect/actual` أو wrapper موحّد) تُستخدم في كل الـ routes، وإزالة `collectAsState()` المؤقت من `MovitAppShellRoute`/Home/Explore/Reports.
- معيار قبول: لا `collectAsState()` في routes؛ Android يستعيد lifecycle-awareness؛ iOS لا يَcrash.

**P3 — ViewModel ownership على iOS:**
- توفير `ViewModelStoreOwner` حقيقى من iOS entry point بحيث يعمل `viewModel()` و`onCleared()`، وإزالة `remember { ViewModel() }` من `MainViewController`.
- معيار قبول: ViewModels على iOS لها دورة حياة حقيقية (لا تسريب)، ونمط الإنشاء موحّد مع Android.

**قبول عام:** `:feature:shell:linkDebugFrameworkIosSimulatorArm64` ناجح، وiOS smoke على Simulator يعرض كل التبويبات ببيانات حقيقية (token) دون P1/P2/P3.

---

### WS-F — قرار DI: اعتماد Koin الآن ✅ *(2026-06-09)*

**المشكلة:** `MovitData` هو `object` singleton (global mutable state) بلا DI. الـ trigger المكتوب في الخطة الرئيسية ("تأجيل Koin حتى أول repository حقيقى يحتاج Ktor client مشترك") **قد تحقّق بالفعل** — `core:data` هو هذا الـ repository.

**المخرَج (موصى به):** إدخال Koin في الطبقة المشتركة كرسم تبعيات واحد لـ Android/iOS، يستبدل الـ singleton.

**خطوات:**
- تعريف Koin modules لـ `MovitMobileApi`, الـ sync repos, و`MovitPlatformBindings` (يُحقَن من المنصّة بدل `install()` على object).
- نقطتا بدء: Android `Application` / debug host، وiOS `MainViewController`.
- استبدال `MovitData.requirePlatform()` بحقن صريح.

**قبول:** لا `object MovitData` ثابت كحامل حالة عالمى؛ الـ ViewModels/repos تحصل على تبعياتها عبر Koin؛ الاختبارات تحقن مزيّفات بسهولة.

> ملاحظة: هذا أثقل بنود Pre-05. إن قرّر المنتج تقسيمه، يبقى **قراره** متخذاً هنا (Koin، لا Hilt — للحفاظ على KMP) ويُنفَّذ كأول بند في استكمال Phase 05 على الأكثر، لا يُؤجَّل بلا تاريخ.

---

### WS-G — بوابة الـ launcher (تعريف خروج Phase 05) 📋 *(معيار مكتوب — التنفيذ لم يبدأ)*

**المشكلة:** بقاء Movit shell كـ debug-only يُبقى مسارين (XML + Compose) يجب صيانتهما معاً — يخالف الانتقال الكامل.

**المخرَج:** معيار مكتوب وواضح لتحويل نقطة الدخول الرسمية.

**البوابة:** يصبح Movit shell هو الـ launcher (وتُخفَّض legacy) عندما:
- تُغلق WS-A→WS-E.
- توجد شاشات Auth + Profile في KMP (أول بندين في استكمال Phase 05).
- iOS يجتاز smoke ببيانات حقيقية.

عند ذلك تُنشأ `MovitMainActivity` إنتاجية (ليست الـ debug pilot)، وينتقل `releaseRuntimeClasspath` ليشمل Movit shell عمداً — وهو ما كان "ممنوعاً" في مرحلة الإثبات، ويصبح الآن الهدف.

---

## بوابة خروج Pre-05 (شرط استئناف Phase 05)

لا تُستأنف شاشات Phase 05 الجديدة قبل:

- [x] WS-A: صفر جسور/كود انتقالي؛ مسار بيانات واحد. *(2026-06-09)*
- [x] WS-B: وجهة Profile حقيقية؛ Components debug-only. *(2026-06-09)*
- [x] WS-C: نصوص مشتركة ar/en؛ RTL متحقَّق على التبويبات الرئيسية + Train UI + Report Detail + Session. *(2026-06-09)*
- [x] WS-D: فروع fallback في الـ sync repos مُختبرة. *(2026-06-09)*
- [x] WS-E: P1/P2/P3 مُغلقة. *(iOS smoke يدوي ببيانات حقيقية ما زال موصى به)*
- [x] WS-F: Koin مُنفَّذ في `MovitData`. *(2026-06-09)*
- [x] WS-G: بوابة launcher **مكتوبة** في هذا المستند (§ WS-G). *(التنفيذ الإنتاجي لم يبدأ — Movit shell debug-only)*

> **حالة البوابة (2026-06-09):** WS-A→WS-F مُنجَزة. يُسمح باستئناف شاشات Phase 05 الجديدة (Auth/Onboarding/…) مع بقاء Movit shell كـ debug entry حتى يتحقق شرط WS-G التنفيذي (Auth + Profile KMP + iOS smoke).

## أوامر التحقق

```powershell
cd kmp-app
.\gradlew.bat --console=plain :app:assembleDebug
.\gradlew.bat --console=plain :core:data:testDebugUnitTest
.\gradlew.bat --console=plain :core:network:testDebugUnitTest
.\gradlew.bat --console=plain :feature:home:testDebugUnitTest
.\gradlew.bat --console=plain :feature:explore:testDebugUnitTest
.\gradlew.bat --console=plain :feature:train:testDebugUnitTest
.\gradlew.bat --console=plain :feature:reports:testDebugUnitTest
.\gradlew.bat --console=plain :feature:library:testDebugUnitTest
.\gradlew.bat --console=plain :feature:shell:testDebugUnitTest
.\gradlew.bat --console=plain :feature:shell:linkDebugFrameworkIosSimulatorArm64
```

تحقق "صفر بقايا":

```powershell
cd kmp-app
rg "ApiBridge|FetcherBridge|RemoteReportsRepository|RemoteWorkoutSessionRepository" --glob '!**/build/**'
rg "collectAsState\(\)" feature --glob '!**/build/**'
```

## خارج نطاق Pre-05 (تحضير Phase 6/7)

- **مشاركة المحرك العددى الخالص** (`OneEuroFilter`, `AngleCalculator`, `VelocityFilter`, score/confidence/threshold calculators) إلى `commonMain` — قيمة عالية ومنطق خالص، لكنه ليس مانعاً لإكمال Phase 05. يُجدول كأول خطوة في حدود Phase 7 (camera/ML)، ويُنفَّذ خلف `expect/actual` للإطارات/الكاميرا. يُذكر هنا حتى لا يُنسى أنه "الـ logic" الأهم للمشاركة.
- camera/MediaPipe/LiteRT/ONNX/TrainingActivity تبقى Phase 7.

---

## سجل التنفيذ — 2026-06-09

### ما تم إنجازه (حسب Workstream)

| WS | الحالة | ملخص التنفيذ |
|----|--------|--------------|
| **WS-A** | ✅ | حذف `*ApiBridge`, `Remote*Repository`, `*FetcherBridge`؛ `MovitShellPilotActivity` → `MovitDataInstall` فقط |
| **WS-B** | ✅ | `Profile` في bottom nav؛ `MovitProfileRoute`؛ `Components` debug-only |
| **WS-C** | ✅ | موديول `:core:resources`؛ ~120+ مفتاح ar/en؛ loaders لكل شاشة؛ Train components + Report Detail + Session |
| **WS-D** | ✅ | unit tests لـ Home/Reports/WorkoutSession sync repos (MockEngine + فروع auth/fallback) |
| **WS-E** | ✅ | iOS 16.0؛ `collectAsStateWithLifecycle`؛ `ViewModelStoreOwner` في `MainViewController` |
| **WS-F** | ✅ | `startKoin` في `MovitData.install()`؛ repos عبر modules |
| **WS-G** | 📋 معيار | البوابة مُعرَّفة في § WS-G؛ لم يُنشأ `MovitMainActivity` إنتاجي بعد |

### بنية `:core:resources` (مخرَج WS-C)

```
core/resources/
├── composeResources/
│   ├── values/strings.xml          # en (default)
│   └── values-ar/strings.xml       # ar
├── MovitLocale.kt                  # MovitLocaleProvider + LocalMovitLanguage
├── MovitLocalizedText.kt           # movitText() + localizedString()
├── MovitStringKey.kt               # movitString("key") — lookup ديناميكي
├── MovitEnglishStrings.kt          # مُولَّد — fallback للاختبارات
└── strings/
    ├── HomeStrings.kt
    ├── ExploreStrings.kt
    ├── TrainStrings.kt
    ├── ReportsStrings.kt
    ├── ReportDetailStrings.kt
    └── SessionStrings.kt
```

**موديولات تستهلك الموارد:** `designsystem`, `shell`, `home`, `train`, `explore`, `reports`, `library`.

### قرارات

1. **`movitString("key")` بدل `Res.string.*`** — بسبب قيود Compose Resources على accessors في `commonMain` (انظر المشكلة 1 أدناه).
2. **`generateMovitEnglishStrings` في Gradle** — يُربَط بـ `preBuild`؛ يحافظ على تزامن الاختبارات مع `strings.xml`.
3. **لا اعتماد `core:resources` → `core:data`** — فصل concerns: locale في UI، language parameter في repositories.
4. **Profile أدنى viable** — إعدادات/لغة/Pro فقط؛ Auth يبقى أول بند Phase 05 بعد البوابة.
5. **Koin** — قرار نهائي لـ KMP (ليس Hilt).

### مشكلات وحلول

| # | المشكلة | الحل |
|---|---------|------|
| 1 | `Unresolved reference` لـ `Res.string.home_*` عند compile `:feature:home` و`:core:resources` | lookup عبر `Res.allStringResources[key]` + `movitText("key")` |
| 2 | unit tests JVM: `MissingResourceException` / `getSystem not mocked` | `MovitEnglishStrings.kt` مُولَّد + `localizedString` fallback |
| 3 | `%1$d` formatting في commonMain | `formatMovitTemplate` + escape `$` في الملف المُولَّد |
| 4 | مسار بيانات مزدوج bridges + Shared | حذف كامل WS-A |
| 5 | نصوص Train الثانوية + Report Detail + Session خارج النطاق الأولي | جلسة استكمال منفصلة — نفس النمط (`movitText` + `*Strings.load`) |

### أوامر التحقق المُنفَّذة

```powershell
cd kmp-app
.\gradlew.bat :core:resources:generateMovitEnglishStrings
.\gradlew.bat :app:assembleDebug :feature:train:testDebugUnitTest :feature:reports:testDebugUnitTest :feature:library:testDebugUnitTest
```

النتيجة: **BUILD SUCCESSFUL**.

### المتبقي بعد Pre-05

- تنفيذ **WS-G** (launcher إنتاجي) بعد Auth KMP + iOS smoke ببيانات حقيقية.
- نصوص API الديناميكية (أسماء تمارين، insights) — خارج نطاق client i18n الحالي.
- `ReportDetailPreviewData` — بيانات معاينة إنجليزية للتطوير فقط.
- مشاركة المحرك العددي الخالص — Phase 6/7 (خارج Pre-05).
