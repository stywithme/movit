# Android / KMP Mobile UI/UX — Phase Pre-06.2: إغلاق ديون مراجعة الكود

آخر تحديث: **2026-06-10**
الحالة: **P0+P1+P2 (دفعات 1+2) مغلق · Phase 05 ص 15/16 مُفعَّل · StoreKit bridge وWS-5 د3 مفتوح**
المصدر: مراجعة كود متعددة المحاور (5 محاور + تحقق ادعاءات + تشغيل اختبارات فعلي + تحقق عدائي) على فرع `codex/kmp-mobile-foundation` بعد إغلاق Pre-06.

المراجع:
- [تقرير إكمال Pre-06](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Completion-Report.md) (مغلقة — WS-A→WS-G)
- [خطة Pre-06](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md)
- [الخطة المهنية](Android-KMP-Mobile-UI-UX-Professional-Plan.md) · [حالة التحديث](Android-UI-UX-Modernization-Status.md) · [Page Scorecards](Page-Scorecards.md)

---

## ملخص تنفيذي للمدير

**Pre-06 أغلقت السقالة الصحيحة** (زر رجوع · تخزين آمن · محرك تدريب مشترك · بوابة launcher · scorecards). لكن **مراجعة كود عميقة بعد الإغلاق** كشفت دفعة من الديون الملموسة لم يلتقطها تقرير الإكمال — معظمها **يظهر للمستخدم النهائي** أو **يكسر الجلسة الحقيقية**. هذه الخطة (Pre-06.2) تجمعها في 7 مسارات عمل قبل إطلاق صفحات 15/16 لمستخدمين حقيقيين.

**ما الذي تغيّر منذ Pre-06؟** لا شيء في الكود — تغيّر **عمق الفحص**. أمثلة على ما لم يكن مرئياً من قبل:

| الاكتشاف | الأثر على المستخدم | الخطورة |
|----------|---------------------|---------|
| لا يوجد **token refresh** في مسار KMP إطلاقاً | انتهاء التوكن = جلسة معطوبة حتى logout يدوي (خصوصاً iOS) | 🔴 عالية |
| الإنجليزية تعرض حرفياً `Today\'s workout` (بق escape في الخريطة المولَّدة) | نص مكسور في كل واجهة إنجليزية | 🔴 عالية |
| **592** استدعاء `movitText("key")` ديناميكي بلا **أي** اختبار يحمي المفاتيح | أي typo = crash وقت التشغيل بدل خطأ compile | 🔴 عالية |
| إنجليزية مُصلّبة داخل مكوّنات الـ DS (`Done/Today/Missed/Rest`، `Retry`، `"Mahmoud"`) | تظهر على الشاشة العربية | 🔴 عالية |

**ما الذي ليس في هذه الخطة (لأن Pre-06 أغلقه فعلاً):** التخزين الآمن (Keychain/Encrypted) · بوابة auth عند الإقلاع · زر Google كـ placeholder صريح · تفاعلية تبديل اللغة عبر `localeRevision` · زر الرجوع · استخراج RepCounter/PhaseStateMachine/ScoreCalculator. كل هذه **متحقَّق من إغلاقها بالكود** ولا يُعاد فتحها.

**التحقق الحالي (2026-06-10):** P0+P1 مغلقان — اختبارات Android خضراء (8 موديولات + `:core:training-engine`) · `assembleDebug` ✅ · `compileKotlinIosSimulatorArm64` ✅. أعداد KMP في [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md). راجع [سجل إغلاق الفجوات](#سجل-إغلاق-الفجوات-gap-closure-log--2026-06-10) و[سجل التنفيذ](#سجل-التنفيذ-والإغلاق-execution-log) أدناه.

---

## نقاط ضعف الخطة (Process / Planning)

> هذه ملاحظات على **الخطة وإدارتها**، مستقلة عن جودة الكود. معالجتها رخيصة وتعالج إرباكاً حقيقياً لأي مطوّر/مدير جديد.

| # | الضعف | الدليل | الإصلاح |
|---|-------|--------|---------|
| PW-1 | **المستندات تتناقض مع الكود ومع بعضها** | خطة Phase-05 كانت تقول «التزم بـ `collectAsState`» | ✅ **WS-7:** Phase-05 محدَّثة · P1/P2/P3 مغلقة في Pre-05/WS-E |
| PW-2 | **أرقام قديمة في الاتجاهين** | مفاتيح/اختبارات/مكوّنات مكتوبة يدوياً | ✅ **WS-7:** [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) + `docsStats` |
| PW-3 | **وعود بنية لم تُبنَ وبدأت تؤلم** | `build-logic` convention plugins (مذكورة في الخطة المهنية: سطر 238، 515) غير موجودة؛ `core:model` غائب وهو سبب مخالفة library→explore | WS-4 |
| PW-4 | **وعد «استخدام الـ Logic القديم» كان أوسع من التنفيذ** | حتى Pre-06 كان rewrite موازياً يشارك backend فقط؛ `core:training-engine` بدأ الاستخراج الحقيقي لكن غطّى ~4-5% من منظومة التدريب | WS-5 (مواصلة الاستخراج العددي قبل الكاميرا) |
| PW-5 | **لا مصدر حقيقة واحد للنِسب** | scorecards أحدث من Professional Plan؛ تقارير قديمة ما زالت تُقتبس | ✅ **WS-7:** [Page-Scorecards.md](Page-Scorecards.md) canonical + ربط Status/Sync/Professional |

---

## المشكلات على محاور التقييم الخمسة → مسارات العمل

كل محور تقييم تحوّل إلى مسار عمل (WS). الترتيب أدناه منطقي وليس حسب الأولوية (الأولويات في القسم التالي).

### WS-1 — System Design: صمود الجلسة (Token Lifecycle) 🔴

**المشكلة (مؤكدة بالتحقق العدائي):** مسار KMP لا يملك أي تجديد للتوكن.
- لا endpoint تجديد في [`MovitMobileApi.kt`](../../../android-poc/core/network/src/commonMain/kotlin/com/movit/core/network/MovitMobileApi.kt) — **رغم أن الـ backend يملكه**، والـ legacy يستخدمه (`AuthApi.kt`: `@POST("api/mobile/auth/refresh")`).
- لا معالجة 401 في أي مكان بـ KMP (لا Ktor `Auth` plugin؛ client factories تركّب `ContentNegotiation`+`Logging` فقط).
- `expiresInSeconds` يُلتقط ويُخزَّن في Keychain (`AuthSessionSnapshot`) لكنه **لا يُستشار أبداً**.
- `AccountSyncRepository.kt:80-86` يعيد فقط حفظ refresh token الموجود بـ `expiresInSeconds = 0`؛ refresh token يُستخدم في logout فقط.

**الأثر:** على iOS (لا Authenticator legacy) انتهاء access token = كل نداء authed يفشل بـ `AppResult.Failure` والمستخدم عالق. على Android الـ OkHttp Authenticator يغطي Retrofit فقط لا Ktor.

**الحل:**
1. أضِف `refresh(refreshToken)` إلى `MovitMobileApi` يقابل `api/mobile/auth/refresh`.
2. ركّب Ktor `Auth` (Bearer) plugin بـ `loadTokens`/`refreshTokens` في `createMovitHttpClient`، يقرأ/يكتب عبر `MovitPlatformBindings` (secure store).
3. على 401 بعد فشل refresh → امسح الجلسة وأطلق effect يوجّه لـ `MovitInnerRoute.Auth` (أعد استخدام بوابة الإقلاع الموجودة من Pre-06).
4. استشر `expiresAtEpochMs` بشكل استباقي قبل النداء (refresh-ahead) لتقليل round-trips الفاشلة.

**معايير القبول:** اختبار MockEngine: (أ) 401 → refresh → إعادة المحاولة تنجح؛ (ب) refresh فاشل → مسح جلسة + توجيه Auth؛ (ج) توكن منتهٍ محلياً → refresh قبل النداء. تغطية في `core:data` + `core:network`.

---

### WS-2 — UI/UX: تقوية التدويل (i18n Hardening) 🔴

**3 مشكلات مؤكدة، كلها تظهر للمستخدم:**

**(أ) بق escape في الخريطة المولَّدة** — [`MovitEnglishStrings.kt`](../../../android-poc/core/resources/src/commonMain/kotlin/com/movit/resources/MovitEnglishStrings.kt) يحتفظ بـ `\'` من XML: 24 مفتاحاً مثل `"home_todays_workout" to "Today\'s workout"`. المسار السريع في `localizedString` يعيد هذه الخريطة مباشرة → الإنجليزية تعرض حرفياً `Today\'s workout`.
- **الحل:** أصلح مولّد `generateMovitEnglishStrings` ليفك escape الـ XML (`\'`→`'`, `\"`→`"`, `\\n`→`\n`) عند التوليد. أعد التوليد. أضِف اختباراً يرفض أي قيمة تحتوي `\\`.

**(ب) 592 استدعاء ديناميكي بلا حماية** — `movitString(name) = Res.allStringResources[name] ?: error(...)` عبر 592 موضعاً في 46 ملفاً، وصفر اختبار يتحقق من وجود المفاتيح.
- **الحل:** اختبار JVM واحد يستخرج كل `movitText("...")`/`movitString("...")` بـ regex من شجرة المصدر، ويؤكد أن كل مفتاح موجود في `values/strings.xml`. ساعة عمل تقفل أخطر فئة أخطاء. (بديل أقوى لاحقاً: الرجوع لـ typed `Res.string.*` accessors — قرار منفصل.)

**(ج) إنجليزية مُصلّبة داخل مكوّنات الـ DS تظهر في الواجهة العربية:**
- [`MovitWeekStrip.kt:109-112`](../../../android-poc/core/designsystem/src/commonMain/kotlin/com/movit/designsystem/components/MovitWeekStrip.kt) — legend `"Done"/"Today"/"Missed"/"Rest"` و`showLegend=true` افتراضياً.
- [`MovitErrorState.kt:10,15`](../../../android-poc/core/designsystem/src/commonMain/kotlin/com/movit/designsystem/components/MovitErrorState.kt) — `"Something went wrong"/"Retry"` تُستدعى من Train/Reports بلا override.
- [`MovitAppHeader.kt:31`](../../../android-poc/core/designsystem/src/commonMain/kotlin/com/movit/designsystem/components/MovitAppHeader.kt) + `MovitScaffold.kt:18` — `userName = "Mahmoud"` (اسم المطوّر) افتراضياً.
- `MovitSessionCard.kt:101` (`"Done"`)، `LibraryBadgeHelper.kt` (`"Featured"` + تلوين بمطابقة substrings إنجليزية → كل الـ badges تنهار للأزرق في العربية).
- **الحل:** مرّر النصوص من المستدعي عبر `movitText` (المكوّنات لا تعرف resources؛ تستقبل String). أضِف مفاتيح ar/en الناقصة. استبدل منطق تلوين الـ badge بـ enum/variant بدل مطابقة نص.

**معايير القبول:** صفر literal إنجليزي مرئي في commonMain للمكوّنات والـ features (grep + مراجعة)؛ اختبار وجود المفاتيح أخضر؛ لا `\\` في الخريطة المولَّدة؛ `userName` بلا افتراضي شخصي.

---

### WS-3 — UI/UX: RTL + صقل Material 3 🟡

**مشكلات مؤكدة:**
- **أيقونات غير mirrored في RTL:** `ChevronLeft/ChevronRight` (week nav, disclosure) و`ArrowForward` (HeroCard) لا تنعكس؛ بينما `AutoMirrored` مستخدم في 6 ملفات أخرى (انضباط غير متسق).
- **Touch targets دون الحد:** أزرار تنقل الأسبوع `Modifier.size(30.dp)` (الحد 48dp)؛ `MovitButton.Small` بـ 42dp.
- **لا press feedback في النظام كله:** `indication=null` كنمط بيتي (`clickableWithoutRipple`)؛ token `primaryPress` معرَّف لكنه ميت عملياً → كروت/صفوف تُلمس بلا أي رد بصري.
- **جرس إشعارات وهمي:** `MovitAppHeader` يرسم نقطة unread دائمة بلا حالة فعلية، و`onNotificationClick` nullable بـ no-op صامت.
- **لا `MovitElevation` token set:** ظلال مُصلّبة (16/12/8 dp) رغم وجود `shadow/shadowSm` في الـ extended colors.

**الحل:** استبدل الأيقونات الاتجاهية بـ `Icons.AutoMirrored.*`؛ ارفع touch targets لـ 48dp؛ أضِف طبقة pressed (scale خفيف أو ripple مضبوط) تستهلك `primaryPress`؛ اربط الجرس بحالة حقيقية أو أخفه؛ أنشئ `MovitElevation`. **معيار القبول:** lint/audit بسيط لـ AutoMirrored + مراجعة RTL يدوية على شاشتين رئيسيتين.

---

### WS-4 — الهيكلة: `core:model` + نظافة Gradle 🟡

**مشكلات مؤكدة:**
- **مخالفة معمارية وحيدة:** [`feature/library/build.gradle.kts:25`](../../../android-poc/feature/library/build.gradle.kts) يعتمد على `:feature:explore` ويستورد `ExploreItemUi/ExploreContent/ExploreItemType` مباشرة (نماذج مشتركة في موديول feature).
- **`build-logic` غائب:** نفس بلوك `kotlin{}/android{}` (~25 سطراً) منسوخ في 11 موديولاً، **والانحراف بدأ** (`testOptions` في 3 موديولات فقط؛ `androidUnitTest` deps في 5 لا 7).
- **`:app` يتجاهل version catalog:** ~50 إصداراً مكرراً كـ raw strings + `compileSdk/minSdk` مُصلّبة.
- **`local.properties` متتبَّع في git** رغم header «must NOT be checked in» (machine-specific `sdk.dir`).
- **Jetifier مفعّل** بلا داعٍ + daemon 2GB لـ 13 موديولاً.

**الحل (بالترتيب):**
1. أنشئ `core:model` وانقل إليه `ExploreItemUi/ExploreContent/ExploreItemType` (والنماذج المشتركة الأخرى)؛ حدّث library/explore/shell للاعتماد عليه؛ احذف dep المباشر.
2. أنشئ `build-logic` بـ convention plugins (`movit.kmp.feature`, `movit.kmp.core`) تجمع البلوك المكرر.
3. انقل `:app` للـ catalog + استخدم `libs.versions.compile.sdk`.
4. `git rm --cached android-poc/local.properties` + انقل `api.*` لملف متتبَّع (`api.properties` أو `gradle.properties` defaults). (ملاحظة: `.gitignore:24` يحتوي النمط أصلاً — يكفي إلغاء التتبّع.)
5. أطفئ Jetifier + ارفع `-Xmx` + فعّل `org.gradle.parallel/caching`.

**معايير القبول:** صفر dep من feature→feature؛ `build-logic` يطبَّق على كل الموديولات؛ `:app:dependencies` نظيف؛ working tree لا يتسخ بـ local.properties.

---

### WS-5 — استخدام الـ Logic القديم: مواصلة الاستخراج العددي 🟡

**مشكلات مؤكدة:**
- **`OneEuroFilter` و`JointAngleCalculator` نُسخا ولم يُنقلا** (دخلا في Pre-06 Phase 1): النسخ في [`core:training-engine`](../../../android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/) **كود ميت** بلا مستهلك، بينما الـ legacy يستخدم نسخه الكاملة (`analysis/AngleCalculator.kt` 585 سطراً، `analysis/OneEuroFilter.kt` 181 سطراً). هذا عكس نمط الـ engine الناجح تماماً.
- **الطبقة المحيطة Pure-Kotlin ما زالت Android-only:** `VisibilityMonitor`, `TimingPolicy`, `FeedbackPolicy/Scheduler`, `RepCompletionCoordinator`, `BilateralController`, `MetricsCalculator`... كلها بلا تبعيات كاميرا وقابلة للمشاركة.
- **DTOs متشعّبة:** `core:network` (kotlinx) يعيد نمذجة نفس JSON الذي تصفه نماذج Gson في legacy؛ انحراف بدأ (`RecentWorkoutData.context` في legacy ومفقود في KMP DTO).

**الحل:**
1. **احسم النسخ الميت:** إما اجعل legacy `AngleCalculator/OneEuroFilter` تفوّض إلى KMP (كما فُعل مع RepCounter) — وهو الأصح — أو احذف نسخ KMP حتى يحين دورها. لا تترك نسختين حيتين.
2. انقل دفعة الطبقة العددية المحيطة إلى `core:training-engine` بنفس نمط الـ wrappers (بدأ بـ `VisibilityMonitor`+`TimingPolicy`).
3. **استراتيجية DTO:** قرار موثّق — إما توليد DTOs من مصدر واحد، أو اعتماد KMP kotlinx كمصدر حقيقة وجعل legacy يستهلكها تدريجياً (نفس اتجاه strangler).

**معايير القبول:** صفر منطق عددي مكرَّر بنسختين حيتين؛ اختبار parity لكل دفعة منقولة؛ قرار DTO مكتوب في الخطة المهنية.

---

### WS-6 — iOS Readiness: CI حقيقي + فجوات إنتاج 🟡

**مشكلات مؤكدة:**
- **CI يغطي simulator-arm64 فقط** — لا device slice (`iosArm64`)، لا `xcodebuild` للتطبيق السويفتي، لا `iosSimulatorArm64Test`. «CI أخضر» يعني klibs تُكمبّل وframework واحد يُربط، لا أن التطبيق يبني/يعمل.
- **`iosApp/project.yml:38-48` مسارات مُصلّبة** (`/opt/homebrew/...` JDK + ANDROID_HOME) تكسر أي Mac بترتيب مختلف (Intel Homebrew، JDK آخر).
- **`active_user_program_id` لا كاتب له على iOS** — تدريب البرامج (effective-plan/customization) لن يتفعل (`IosMovitPlatform` يقرأ فقط؛ لا API enrollment).
- **لا StoreKit** — `MovitProfileEffect.OpenSubscription` يُعالَج كـ `Unit`؛ `MovitSubscriptionScreen` unreachable؛ `is_pro` لا يضبطه شيء على iOS.
- **خريطة Arabic JVM fallback 11 مفتاحاً فقط** مقابل 827 → اختبارات JVM للعربية تتحقق فعلياً من الإنجليزية.

**الحل:**
1. وسّع workflow: أضِف `compileKotlinIosArm64` + `linkDebugFrameworkIosArm64` + خطوة `xcodebuild` للتطبيق + `iosSimulatorArm64Test`. ثبّت Xcode/`DEVELOPER_DIR`.
2. اجعل `project.yml` preBuildScript يكتشف المسارات (`which java`, `brew --prefix`) بدل التصليب.
3. عرّف API enrollment (`PUT user-programs/{id}/activate` أو ما يقابله) + كاتب `activeUserProgramId` على iOS.
4. قرار StoreKit: إما bridge شراء، أو إخفاء مسار الاشتراك على iOS حتى يُبنى (لا effect صامت).

**معايير القبول:** CI يبني التطبيق السويفتي فعلياً؛ `project.yml` يعمل على Mac نظيف؛ مسار برنامج واحد يتفعّل على iOS؛ لا effect اشتراك صامت.

---

### WS-7 — Process: مزامنة المستندات مع الكود 🟢 (رخيص، يفك إرباك الفريق)

**المشكلة:** المستندات الثلاثة الرئيسية تتناقض مع الكود ومع بعضها (تفاصيل في «نقاط ضعف الخطة» أعلاه).

**الحل:**
1. سكربت `docs:stats` (Gradle task أو PowerShell) يولّد: عدد مفاتيح ar/en + تطابقها، عدد الاختبارات لكل موديول، عدد مكوّنات `Movit*`. يُحقن في المستندات بدل الأرقام اليدوية.
2. صحّح Phase-05 (`collectAsState`→`collectAsStateWithLifecycle`) وstatus doc (أقسام «0%» المنفَّذة فعلاً).
3. اعتمد [Page-Scorecards.md](Page-Scorecards.md) كمصدر وحيد للنِسب؛ اربط بقية المستندات إليه.

**معايير القبول:** صفر رقم يدوي للمفاتيح/الاختبارات في المستندات؛ لا تناقض `collectAsState` بين الوثيقتين.

---

## ترتيب الأولويات

> المعيار: **(أثر على المستخدم/الجلسة) × (انخفاض الكلفة)**. P0 يُغلق قبل أي توسعة شاشات (15/16) لمستخدمين حقيقيين.

### 🔴 P0 — قبل أي إطلاق حقيقي
| الأولوية | المسار | لماذا الآن | الكلفة التقديرية |
|----------|--------|-----------|------------------|
| 1 | **WS-1** Token refresh | بدونه أي جلسة طويلة تنكسر (خصوصاً iOS) — يحجب أي عرض/استخدام حقيقي | متوسطة (2-3 أيام) |
| 2 | **WS-2** i18n hardening | بق نص مرئي في كل واجهة إنجليزية + فئة crash كاملة بلا حماية + إنجليزية في الواجهة العربية | منخفضة-متوسطة (1-2 يوم؛ اختبار المفاتيح ساعة) |

### 🟡 P1 — مصداقية المنتج ثنائي اللغة + فك الإرباك
| الأولوية | المسار | لماذا | الكلفة |
|----------|--------|-------|--------|
| 3 | **WS-3** RTL + M3 polish | واجهة «التطبيق ثنائي اللغة» أمام أي مراجعة تصميم | متوسطة (2-3 أيام) |
| 4 | **WS-7** Docs sync | رخيص ويفك إرباك كل من يقرأ الخطة | منخفضة (يوم) |

### 🟢 P2 — صحة معمارية طويلة المدى (قبل تضخّم الكود)
| الأولوية | المسار | لماذا | الكلفة |
|----------|--------|-------|--------|
| 5 | **WS-4** core:model + Gradle | المخالفة الوحيدة قبل أن تتكرر؛ الانحراف بدأ | متوسطة (2-3 أيام) |
| 6 | **WS-5** Engine continuation | يحقق وعد «استخدام الـ Logic القديم» فعلياً قبل الكاميرا | متوسطة-عالية (3-5 أيام) |
| 7 | **WS-6** iOS CI + production gaps | يلزم لإصدار iOS فعلي؛ غير حاجب لـ Android | عالية (4-6 أيام) |

**ملاحظة جدولة:** P0 (WS-1 + WS-2) يمكن أن يتوازى بين مطوّرَين (data/network مقابل resources/designsystem) — لا تعارض ملفات. WS-7 يمكن لأي شخص إنجازه بالتوازي في أي وقت.

---

## أوامر التحقق (تُشغَّل بعد كل WS)

```powershell
cd android-poc
.\gradlew.bat --console=plain `
  :feature:account:testDebugUnitTest `
  :feature:shell:testDebugUnitTest `
  :feature:train:testDebugUnitTest `
  :core:data:testDebugUnitTest `
  :core:network:testDebugUnitTest `
  :core:resources:testDebugUnitTest `
  :app:assembleDebug `
  :feature:shell:compileKotlinIosSimulatorArm64
```

خط الأساس الحالي: راجع [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) (`.\gradlew.bat docsStats`). أي WS يجب ألا يُنقص عدد `@Test` في KMP، ويُفضّل أن يزيده.

فحوص نصية مطلوبة:
```powershell
# WS-2: لا backslash في الخريطة المولَّدة
Select-String -Path .\core\resources\src\commonMain\kotlin\com\movit\resources\MovitEnglishStrings.kt -Pattern '\\'
# WS-2/WS-3: literal إنجليزي في مكوّنات DS (يجب أن يتقلّص لصفر)
Select-String -Path .\core\designsystem\src\commonMain\**\*.kt -Pattern '"(Done|Today|Missed|Rest|Retry|Mahmoud)"'
# WS-4: لا dependency من feature إلى feature
Select-String -Path .\feature\*\build.gradle.kts -Pattern 'project\(":feature:'
```

---

## بوابة خروج Pre-06.2

| البند | المعيار |
|-------|---------|
| WS-1 | refresh + 401 handling + 3 اختبارات MockEngine خضراء؛ توكن منتهٍ لا يكسر الجلسة |
| WS-2 | صفر `\\` في الخريطة؛ اختبار وجود المفاتيح أخضر؛ صفر literal إنجليزي مرئي في features/DS |
| WS-3 | AutoMirrored على كل أيقونة اتجاهية؛ touch targets ≥48dp؛ press feedback حقيقي |
| WS-4 | صفر dep feature→feature؛ `build-logic` مطبَّق؛ `local.properties` غير متتبَّع |
| WS-5 | لا منطق عددي بنسختين حيتين؛ قرار DTO مكتوب |
| WS-6 | CI يبني التطبيق السويفتي؛ `project.yml` portable؛ لا effect اشتراك صامت |
| WS-7 | ✅ أرقام مولَّدة (`docsStats`)؛ لا تناقض `collectAsState`؛ Page-Scorecards canonical |
| التحقق | KMP tests ≥ baseline في Docs-Stats-Snapshot · `assembleDebug` · iOS compile أخضر |

**لا تُعلَم Pre-06.2 مغلقة** إلا بمرور P0+P1 على الأقل؛ P2 يمكن أن يمتد بالتوازي مع صفحات 15/16 إن لزم.

---

## سجل التنفيذ والإغلاق (Execution Log)

**تاريخ التحقق:** 2026-06-10  
**الحالة الإجمالية:** **P0 مغلق** (WS-1 + WS-2) · **P1 جزئي** (WS-7 ✅، WS-3 لم يُنفَّذ) · **P2 مفتوح** (WS-4/5/6 لم تُنفَّذ)

### جدول ملخص المسارات

| المسار | الحالة | ما تم (مختصر) | معايير القبول |
|--------|--------|---------------|---------------|
| **WS-1** Token lifecycle | ✅ مكتمل | `MovitMobileApi.refresh()` · Ktor `Auth`/`bearer` في `MovitHttpClientAuth.kt` · `refreshHttpClient` منفصل لتجنب loop · refresh-ahead (`MOVIT_REFRESH_AHEAD_MS`) · `onSessionExpired` عبر `MovitData` · 3 اختبارات MockEngine في `MovitHttpClientAuthTest` + 3 تكامل في `TokenLifecycleIntegrationTest` | ✅ 401→refresh→إعادة محاولة · ✅ refresh فاشل→مسح جلسة · ✅ توكن منتهٍ→refresh استباقي |
| **WS-2** i18n hardening | ✅ مكتمل | إصلاح `generateMovitEnglishStrings` (unescape XML) · `MovitEnglishStringsTest` · `MovitStringKeyExistenceTest` · إزالة literals من DS (`MovitWeekStripLegend?`، `MovitErrorState` بمعاملات نصية، `userName=""` في `MovitAppHeader`) · `badgeVariant: MovitTagVariant?` في `ExploreItemUi`/`LibraryBadgeHelper` | ✅ لا `\'` في الخريطة (`Today's workout` صحيح) · ✅ اختبار المفاتيح أخضر · ✅ grep DS literals = صفر |
| **WS-3** RTL + M3 polish | ⏳ لم يُنفَّذ | — | ⏳ ChevronLeft/Right وArrowForward ما زالت `Icons.Default.*` في `MovitWeekStrip`/`MovitHeroCard`/`MovitListRow`؛ touch targets 30dp |
| **WS-4** core:model + Gradle | ⏳ لم يُنفَّذ | لا مجلد `build-logic` · لا موديول `:core:model` · `feature:library`→`:feature:explore` ما زال موجوداً | ⏳ لا تغيير |
| **WS-5** Engine continuation | ✅ دفعة 1 | Legacy `OneEuroFilter`/`AngleCalculator` يفوّضان KMP · `TimingPolicy`+`VisibilityMonitor` في `core:training-engine` مع wrappers · قرار DTO في الخطة المهنية · اختبارات parity | ✅ لا نسختين حيتين للدفعة الأولى · اختبارات `:core:training-engine` |
| **WS-6** iOS CI + production | ⏳ لم يُنفَّذ | لا `compileKotlinIosArm64`/`xcodebuild` في CI · `project.yml` لم يُحدَّث | ⏳ لا تغيير |
| **WS-7** Docs sync | ✅ مكتمل | `scripts/generate-docs-stats.ps1` · Gradle task `docsStats` · [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) · Phase-05 محدَّثة (`collectAsStateWithLifecycle`) · Page-Scorecards كمصدر canonical للنِسب | ✅ أرقام مولَّدة · ✅ لا `collectAsState()` في features (0 حسب snapshot) |

### نتائج التحقق الفعلية (2026-06-10)

**Gradle — اختبارات الوحدات (6 موديولات):** `BUILD SUCCESSFUL` (~53s) — كل المهام `UP-TO-DATE` أو ناجحة:

| المهمة | النتيجة |
|--------|---------|
| `:feature:account:testDebugUnitTest` | ✅ ناجح |
| `:feature:shell:testDebugUnitTest` | ✅ ناجح |
| `:feature:train:testDebugUnitTest` | ✅ ناجح |
| `:core:data:testDebugUnitTest` | ✅ ناجح |
| `:core:network:testDebugUnitTest` | ✅ ناجح |
| `:core:resources:testDebugUnitTest` | ✅ ناجح |

> عدد `@Test` KMP في هذه الموديولات (من snapshot): account **60** · shell **37** · train **18** · data **17** · network **3** · resources **2** — المجموع **137**. إجمالي KMP **262** (baseline snapshot).

**`:app:assembleDebug`:** ✅ `BUILD SUCCESSFUL` (~50s) — APK debug يُبنى.

**`:feature:shell:compileKotlinIosSimulatorArm64`:** ❌ **فشل** — `:feature:reports:compileKotlinIosSimulatorArm64` · `MovitReportsScreen.kt:301` · `Unresolved reference 'format'` (`String.format` JVM-only في `commonMain`). **ليست انحداراً من WS-1/2/7** لكنها تمنع اجتياز بوابة iOS compile حتى يُستبدل بـ multiplatform formatter.

**فحوص نصية:**

| الفحص | النتيجة |
|-------|---------|
| `Select-String MovitEnglishStrings.kt -Pattern '\\'` | يطابق `\$` في placeholders Kotlin (`%1\$d`) في المصدر — **ليس** بق `\'` XML. لا `Today\'s` في الملف. `MovitEnglishStringsTest` يمرّ (لا `\` في قيم runtime). |
| `Select-String designsystem -Pattern '"(Done\|Today\|Missed\|Rest\|Retry\|Mahmoud)"'` | **صفر مطابقات** — literals الإنجليزية المرئية أُزيلت من DS |
| `Select-String feature/*/build.gradle.kts -Pattern 'project(":feature:'` | **7 مطابقات** — `library→explore` (مخالفة WS-4) + تبعيات shell المتوقعة (home/train/reports/library/account/explore) |

**تحقق WS-3/4/5/6 (عدم التنفيذ):**

| البند | النتيجة |
|-------|---------|
| `build-logic/` | غير موجود |
| `:core:model` | غير موجود في Gradle |
| توسيع CI iOS (`iosArm64`, `xcodebuild`) | غير موجود في `.github` |
| AutoMirrored لكل أيقونة اتجاهية | جزئي فقط (6 ملفات features؛ DS ما زال `ChevronLeft/Right` افتراضياً) |

### قرارات تقنية مهمة أثناء التنفيذ

1. **عميل HTTP منفصل لـ refresh** (`refreshHttpClient` في `MovitHttpClientConfig`) — يمنع حلقة لا نهائية عندما يعيد Ktor Auth طلب refresh عبر نفس العميل المُصادَق.
2. **Refresh-ahead ساعة واحدة** (`MOVIT_REFRESH_AHEAD_MS = 60 * 60 * 1000L`) — يقلّل 401 الاستباقية قبل انتهاء `expiresAtEpochMs`.
3. **`legend: MovitWeekStripLegend? = null`** — الـ legend اختياري؛ النصوص تُمرَّر من المستدعي عبر `movitText` بدل literals افتراضية.
4. **`badgeVariant: MovitTagVariant?`** على `ExploreItemUi` + `resolveLibraryBadge()` — تلوين الـ badge بـ enum بدل مطابقة substrings إنجليزية.
5. **`MovitErrorState`** — كل النصوص (`title`, `message`, `actionLabel`) معاملات إلزامية من الـ feature.
6. **`docsStats` Gradle task** — يولّد snapshot قابل للحقن؛ Page-Scorecards مصدر وحيد للنِسب؛ Phase-05/status محدَّثان.
7. **WS-5 دفعة 1 — DTO:** `core:network` + kotlinx.serialization مصدر حقيقة؛ Gson legacy strangler فقط.
8. **WS-5 دفعة 1 — Engine:** `OneEuroFilter`/`AngleCalculator` → KMP؛ `TimingPolicy`/`VisibilityMonitor` مستخرجان مع نمط RepCounter.

### ما تبقى وتوصية الجدولة

| المسار | الأولوية | التوصية |
|--------|----------|---------|
| **WS-3** RTL + M3 | P1 — حاجز إغلاق Pre-06.2 | جدّوله قبل عرض التطبيق لمراجعي تصميم؛ يمكن موازاته مع صفحات 15/16 |
| **إصلاح iOS compile** (`String.format` في reports) | عاجل تقني | سطر واحد — استبدال بـ `%.1f`.format()` أو helper multiplatform؛ لا ينتظر WS-6 |
| **WS-4** core:model + Gradle | P2 | قبل أي feature→feature جديد |
| **WS-5** Engine | P2 | بالتوازي مع تخطيط الكاميرا |
| **WS-6** iOS CI | P2 | قبل إصدار iOS فعلي |

**بوابة خروج Pre-06.2:** P0+P1 **مغلقان** (WS-1/2/3/7 + iOS compile). P2 **جزئي** — WS-4/5/6 دفعات أولى مكتملة؛ دفعات لاحقة (WS-5 engine layers، WS-6 StoreKit/enrollment) تبقى قبل إعلان Pre-06.2 مغلقة بالكامل.

---

## سجل إغلاق الفجوات (Gap Closure Log — 2026-06-10)

جلسة إغلاق فجوات متوازية بعد [سجل التنفيذ](#سجل-التنفيذ-والإغلاق-execution-log) الأولي. كل مسار عمل نُفِّذ بواسطة subagent مخصص ثم أُغلق بحاجز بناء موحّد.

### نتائج مسارات العمل

| المسار | الحالة | ما تم | ملاحظات |
|--------|--------|-------|---------|
| **iOS compile fix** | ✅ | استبدال `String.format` JVM-only في `MovitReportsScreen.kt` بـ formatter متعدد المنصات | كان يحجب `:feature:shell:compileKotlinIosSimulatorArm64` |
| **WS-3** RTL + M3 polish | ✅ دفعة P1 | `Icons.AutoMirrored.*` في `MovitWeekStrip`/`MovitHeroCard`/`MovitListRow`/`MovitFloatPill` · touch targets `MovitSpacing.minTouchTarget` (48dp) · `MovitClickable` يستهلك `primaryPress` ripple · إصلاح مراجع `AppResult`/`PlatformInfo` في shell | أخطاء compile shell (WS-3) لم تعد موجودة |
| **WS-4** core:model + Gradle | ✅ دفعة 1+2 | موديول `:core:model` · إزالة `library→explore` · `build-logic` · `:app` catalog · Jetifier off · `local.properties`/`api.properties` gitignore · convention plugins على 13 موديولاً | دفعة 2 موثَّقة في [سجل WS-4 دفعة 2](#ws-4-دفعة-2-2026-06-10) |
| **WS-5** Engine continuation | ✅ دفعة 1 + bridge | `TimingPolicy`/`VisibilityMonitor`/`RepCountingTimingOverrides` في KMP · legacy wrappers · تفويض `OneEuroFilter`/`AngleCalculator` | **Build blocker:** انحراف API بين `TrainingEngine.kt` والـ KMP — أُصلح في هذه الجلسة (انظر أدناه) |
| **WS-6** iOS CI + production | ✅ دفعة CI | `.github/workflows/movit-kmp-ios.yml`: `compileKotlinIosArm64` + `linkDebugFramework` + `iosSimulatorArm64Test` + job `xcodebuild` | يتطلّب Mac CI للتحقق الكامل؛ StoreKit/enrollment/iOS program activation لم تُنفَّذ |

### إصلاح حاجز البناء النهائي (TrainingEngine API drift)

`:app:assembleDebug` كان يفشل بعد دفعة WS-5 بسبب انحراف واجهة KMP:

| الملف | الإصلاح |
|-------|---------|
| `engine/policy/TimingPolicy.kt` | `fromSettings()` كدالة top-level (typealias لا يدعم `Companion` extension) |
| `TrainingEngine.kt` | `toTimingOverrides()` لـ `minRepIntervalFor` · `trackedJoints=` بدل `visibilityTrackedJoints=` في wrapper |
| `TrainingEngineBridge.kt` | `repCountingConfig?.toTimingOverrides()` في `buildPhaseTimingConfig` |
| `PhaseStateMachine.kt` | `fromSettings()` بدل `TimingPolicy.fromSettings()` |

### التحقق النهائي (2026-06-10 — بعد إغلاق الفجوات)

```powershell
cd android-poc
.\gradlew.bat --console=plain `
  :feature:account:testDebugUnitTest `
  :feature:shell:testDebugUnitTest `
  :feature:train:testDebugUnitTest `
  :core:data:testDebugUnitTest `
  :core:network:testDebugUnitTest `
  :core:resources:testDebugUnitTest `
  :core:training-engine:testDebugUnitTest `
  :feature:library:testDebugUnitTest `
  :app:assembleDebug `
  :feature:shell:compileKotlinIosSimulatorArm64
```

**النتيجة:** `BUILD SUCCESSFUL in 2m 2s` — كل المهام خضراء.

| المهمة | النتيجة |
|--------|---------|
| `:feature:account:testDebugUnitTest` | ✅ |
| `:feature:shell:testDebugUnitTest` | ✅ |
| `:feature:train:testDebugUnitTest` | ✅ |
| `:core:data:testDebugUnitTest` | ✅ |
| `:core:network:testDebugUnitTest` | ✅ |
| `:core:resources:testDebugUnitTest` | ✅ |
| `:core:training-engine:testDebugUnitTest` | ✅ |
| `:feature:library:testDebugUnitTest` | ✅ |
| `:app:assembleDebug` | ✅ |
| `:feature:shell:compileKotlinIosSimulatorArm64` | ✅ |

**أعداد `@Test` KMP** (من [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md)):

| الموديول | العدد |
|----------|-------|
| account | 60 |
| shell | 37 |
| train | 18 |
| data | 17 |
| network | 3 |
| resources | 2 |
| library | 42 |
| training-engine | 18 |
| **مجموع الموديولات الثمانية + engine** | **197** |
| **إجمالي KMP** | **262** |

### ما تبقى جزئياً (خارج نطاق إغلاق الفجوات)

| البند | السبب |
|-------|-------|
| **WS-5 دفعات لاحقة** | `FeedbackPolicy`/`BilateralController`/`RepCompletionCoordinator`/`MetricsCalculator` وطبقات DTO strangler لم تُنقل بعد |
| **WS-4 دفعة 2** | ✅ مغلق — انظر [سجل WS-4 دفعة 2](#ws-4-دفعة-2-2026-06-10) أدناه |
| **WS-6 إنتاج iOS** | StoreKit bridge · `active_user_program_id` writer · تحقق `xcodebuild` يحتاج Mac CI فعلي |
| **إعلان Pre-06.2 مغلقة بالكامل** | P2 جزئي — يمكن المتابعة بالتوازي مع صفحات 15/16 |

---

## مراجعة تحقّق مستقلة (Independent Verification — 2026-06-10)

> مراجعة لاحقة **لم تثق بسجلات التنفيذ أعلاه**، بل تحقّقت من كل مسار عمل **في الكود الفعلي** + **شغّلت البناء الكامل محلياً** (نفس منهج المراجعة التي ولّدت هذه الخطة). الخلاصة: **سجلات التنفيذ دقيقة** — كل ادعاءات WS-1→WS-7 صمدت أمام الفحص، مع ملاحظتين صغيرتين وبند نظافة واحد مهم.

### نتيجة البناء المحلي (موثوق)

```
BUILD SUCCESSFUL in 3m 23s — 459 actionable tasks (exit 0)
```

10 مهام خضراء بما فيها `:feature:shell:compileKotlinIosSimulatorArm64` (التي كانت تفشل سابقاً بسبب `String.format`) — والبناء يشمل تعديلات شجرة العمل غير المرفوعة (انظر أدناه)، أي أن الشجرة الحالية تُكمبّل لـ Android **و** iOS.

### تحقّق محور بمحور (من الكود، لا من السجل)

| المسار | التحقق | الدليل المفحوص |
|--------|--------|----------------|
| **WS-1** | ✅ مؤكد | `MovitMobileApi.refresh()` (سطر 199 → `api/mobile/auth/refresh`) · `MovitHttpClientAuth.kt` + `MovitAuthTokenStore.kt` · اختبارات `MovitHttpClientAuthTest` + `TokenLifecycleIntegrationTest` موجودة وتمر |
| **WS-2** | ✅ مؤكد (ملاحظة صغيرة) | لا `\'` في `MovitEnglishStrings.kt` (`"Today's workout"` صحيح) · `MovitStringKeyExistenceTest` + `MovitEnglishStringsTest` موجودان · literals DS أُزيلت **عدا** بند واحد ↓ |
| **WS-3** | ✅ مؤكد | `Icons.AutoMirrored.*` في WeekStrip/HeroCard/ListRow · `MovitSpacing.minTouchTarget = 48.dp` مُطبَّق على أزرار الأسبوع · `MovitClickable` يستهلك `ripple(color = movit.primaryPress)` |
| **WS-4** | ✅ دفعة 1 مؤكدة | `:core:model` موجود (ExploreModels/MovitTagVariant) · `build-logic` + `includeBuild` · `library→explore` **محذوف** · 13 موديولاً على `movit.kmp.*` · `local.properties` **لم يعد متتبَّعاً** (أفضل مما يدّعيه السجل) |
| **WS-5** | ✅ دفعة 1 مؤكدة | legacy `OneEuroFilter`/`AngleCalculator` يفوّضان KMP (`import … as KmpOneEuroFilter` · `JointAngleCalculator.angleDegrees`) · `TimingPolicy`/`VisibilityMonitor`/`RepCountingTimingOverrides` في KMP |
| **WS-6** | ✅ دفعة CI مؤكدة | workflow فيه `compileKotlinIosArm64` + `linkDebugFrameworkIosArm64` + `iosSimulatorArm64Test`×6 + **job `ios-xcodebuild` غير مشروط** (macos-15، xcodegen + `xcodebuild` للتطبيق السويفتي) — يفسّر «الأكشن أخضر» |
| **WS-7** | ✅ مؤكد | Gradle task `docsStats` موجود · snapshot في المسار المرتبط · **صفر** `collectAsState()` عارٍ في feature routes |

### ملاحظات المراجعة (لا blockers)

| # | الملاحظة | الخطورة | الإجراء |
|---|----------|---------|---------|
| V-1 | **شجرة عمل غير مرفوعة:** 14 ملفاً (~107 إضافة) منطق حقيقي في train/shell — `OpenAssessment` · `AssessmentClicked` · `LaunchLegacySubscription` · `TrainApiMapper` (+42) · `MovitTrainScreen` (+21). **الأكشن الأخضر ركض على HEAD المرفوع، لا على هذه الشجرة.** | 🟡 نظافة | ارفعها عبر CI (أو ارجعها إن كانت تجريبية) قبل إعلان أي إغلاق — كي يغطّي الأخضر الكود الفعلي |
| V-2 | **بقية literal في DS:** `MovitSessionCard` افتراضه `actionLabel = "Start session"` (إنجليزي)، ومستدعيه الوحيد `TrainTodayCard:128` لا يمرّر بديلاً → يظهر إنجليزياً على صفحة Train العربية. ادعاء «صفر literals» مبالغ قليلاً. | 🟢 منخفضة | سطر واحد: مرّر `movitText("…")` من `TrainTodayCard` — يُغلق WS-2 كاملاً |
| V-3 | **تحذيرات Keychain:** 6× `This cast can never succeed` في `IosKeychainSecureSessionStore.kt` (كود أمني، Pre-06/WS-D لا Pre-06.2). يُكمبّل وله اختبارات، لكن casts الـ CFType تستحق فحصاً. | 🟢 متابعة | راجع cinterop casts عند لمس WS-6 |

### الحكم

سجلّا التنفيذ وإغلاق الفجوات **صادقان ودقيقان** (تباين عن المستندات القديمة التي بالغت). **P0+P1 مغلقان فعلاً** والبناء أخضر Android+iOS محلياً، ومتسق مع CI الأخضر. يتبقّى: بند نظافة V-1، وV-2 لإغلاق WS-2 100%، ثم دفعات P2 اللاحقة (WS-5/6).

### WS-4 دفعة 2 (2026-06-10)

- **`:app` version catalog:** كل تبعيات المكتبات عبر `libs.*`؛ `compileSdk`/`minSdk`/`targetSdk` من `libs.versions` — لا إصدارات خام للمكتبات.
- **Jetifier:** `android.enableJetifier=false` في `gradle.properties`.
- **Gradle tuning:** `org.gradle.parallel=true` · `org.gradle.caching=true` · `-Xmx4096m`.
- **ملفات محلية:** `local.properties` غير متتبَّع (`.gitignore`)؛ `api.properties.example` موثَّق + `api.properties` مُستثنى من git.
- **Convention plugins:** 13 موديول KMP على `movit.kmp.core`/`movit.kmp.feature`؛ `:app` وحده `android.application`.
- **التحقق:** `:app:assembleDebug` ✅ `BUILD SUCCESSFUL in 1m 20s`.

---

## سجل العمل الإضافي بعد مراجعة المدير (Post-Review Follow-Up — 2026-06-10)

> بعد [مراجعة التحقّق المستقلة](#مراجعة-تحقّق-مستقلة-independent-verification--2026-06-10)، طلب مدير المشروع إغلاق ملاحظات V-2/V-3 ثم البدء في **Phase 05 — صفحتا 15/16** بالتوازي مع **دفعات P2 اللاحقة** (WS-4/5/6). نُفِّذ العمل عبر وكلاء متخصصين + إصلاحات مباشرة؛ هذا القسم يسجّل **ما طُلب** و**ما تحقّق**.

### ما طُلب (تعليمات المدير)

| # | الطلب | الأولوية |
|---|-------|----------|
| 1 | إغلاق **V-2:** `MovitSessionCard` افتراض `"Start session"` يظهر إنجليزياً على Train العربية | فوري — إغلاق WS-2 100% |
| 2 | إغلاق **V-3:** 6 تحذيرات cast في `IosKeychainSecureSessionStore.kt` | عند لمس WS-6 |
| 3 | **Phase 05 ص 15:** Program flow — API حقيقي + مسارات Train/Explore | منتج حقيقي |
| 4 | **Phase 05 ص 16:** Workout flow — Session→Customize→Run→`LegacyTrainingLauncher` | منتج حقيقي |
| 5 | **WS-4 دفعة 2:** `:app` catalog · Jetifier · `api.properties` | P2 — لا يحجب Android |
| 6 | **WS-5 دفعة 2:** `FeedbackPolicy`/`BilateralController` | P2 |
| 7 | **WS-6 دفعة 2:** StoreKit · `active_user_program_id` · Keychain نظيف | P2 — iOS |

### نتائج التنفيذ

#### ملاحظات المراجعة (V-2 · V-3)

| # | الحالة | ما تم | التحقق |
|---|--------|-------|--------|
| **V-2** | ✅ مغلق | `TrainTodayCard`: `actionLabel = movitText("train_start_session")` · `MovitSessionCard`: أُزيل الافتراضي `"Start session"` (معامل إلزامي) | «بدء الجلسة» على الواجهة العربية |
| **V-3** | ✅ مغلق | `IosKeychainSecureSessionStore`: `CFDictionaryCreate` + `keychainQuery`/`cfDictionaryOf` (نمط russhwolf/multiplatform-settings) · UTF-8 عبر `NSData`/`memcpy` | `:core:data:compileKotlinIosSimulatorArm64` ✅ بدون تحذيرات cast |

#### Phase 05 — صفحة 15 Program Flow

| البند | النتيجة |
|-------|---------|
| **API حقيقي** | `ProgramFlowSyncRepository` في `:core:data` — Explore + Home + PlanSync + `GET programs/:id` + progress-metrics/Reports |
| **المسار الافتراضي** | `SharedProgramFlowRepository` عند تثبيت `MovitData`؛ `FakeProgramFlowRepository` للمعاينة/اختبارات preview فقط |
| **التنقل** | Train/Explore → `ProgramList` → `ProgramWeekPlan` → `WorkoutSession`/`WeeklyReport` (موصول مسبقاً في shell) |
| **i18n** | 11 مفتاحاً `program_flow_*` جديداً (ar/en) |
| **المواصفة** | [`Program-Flow-Page-Spec.md`](Page-Specs/Program-Flow-Page-Spec.md) — Implementation Status محدَّث |
| **الاختبارات** | `:feature:library` · `:feature:shell` · `:feature:train` `testDebugUnitTest` ✅ |

**خارج النطاق (مؤجل):** Share التقرير · عرض كل أسابيع التقرير · صور البرامج من الشبكة.

#### Phase 05 — صفحة 16 Workout Flow

| البند | النتيجة |
|-------|---------|
| **التدفق** | `WorkoutSession` → **Start** → `WorkoutCustomize` → `WorkoutRun` → **Start exercise** → `LegacyTrainingLauncher` |
| **إصلاح رئيسي** | زر Start في Session كان يفتح `ExercisePrepare` خطأً — صُحّح في `MovitInnerHost.kt` |
| **Prepare** | `ExercisePrepare(workoutId)` → Run؛ Explore prepare → `LegacyTrainingLauncher` مباشرة |
| **Handoff** | `WorkoutCustomizeViewModel.commitForRun()` → `WorkoutFlowCache` → `WorkoutRunViewModel` |
| **Effect chain** | `LaunchLegacyCameraTraining` → shell → `MovitShellPilotActivity` → `TrainingActivity` |
| **الاختبارات** | `:feature:library` · `:feature:shell` `testDebugUnitTest` ✅ (+ `WorkoutFlowStateTest` · `MovitAppShellStateTest`) |

**مؤجل Phase 07:** كاميرا KMP · workout-mode كامل · حفظ التخصيص API.

#### WS-4 دفعة 2 — إغلاق كامل

| البند | النتيجة |
|-------|---------|
| `:app` catalog | كل `libs.*` · SDK من `libs.versions` |
| Jetifier | `android.enableJetifier=false` |
| Gradle | `parallel` · `caching` · `-Xmx4096m` |
| ملفات محلية | `local.properties` غير متتبَّع · `api.properties` في `.gitignore` · `api.properties.example` موثَّق |
| Convention plugins | 13 موديول KMP |
| **الحكم** | **WS-4 مغلق بالكامل** (دفعتا 1+2) · `:app:assembleDebug` ✅ |

#### WS-5 دفعة 2 — Engine

| المكوّن المنقول | المسار KMP |
|-----------------|------------|
| `FeedbackPolicy` | `engine/policy/FeedbackPolicy.kt` |
| `FeedbackScheduler` + نماذج | `feedback/FeedbackModels.kt` · `feedback/FeedbackScheduler.kt` |
| `BilateralController` | `bilateral/BilateralController.kt` · `bilateral/BilateralModels.kt` |

- Legacy Android: wrappers/typealias بنمط RepCounter.
- **`BilateralConfigInput`:** مدخل محرك فقط — **لم تُمس** عقود JSON في `core:network`.
- اختبارات parity: `FeedbackPolicyTest` · `FeedbackSchedulerParityTest` · `BilateralControllerParityTest`.
- **التحقق:** `:core:training-engine:testDebugUnitTest` ✅ · `:app:assembleDebug` ✅ · legacy feedback tests ✅.

**دفعة 3 (مفتوحة):** `RepCompletionCoordinator` · `MetricsCalculator` · strangler DTO.

#### WS-6 دفعة 2 — iOS إنتاج

| البند | النتيجة |
|-------|---------|
| **`active_user_program_id`** | `PlanSyncRepository.enrollProgram` → `IosMovitPlatform.setActiveUserProgramId` · hydrate عند الإقلاع · `PlanSyncRepositoryTest` |
| **StoreKit** | مسار **إخفاء صريح** (`supportsInAppSubscription=false`) · رسالة `profile_subscription_ios_unavailable` · **لا** `OpenSubscription → Unit` صامت |
| **Keychain** | تجميع نظيف بعد V-3 · `CFBridgingRelease` بعد `SecItemAdd` |
| **CI (دفعة 1)** | `iosArm64` · `linkDebugFramework` · `iosSimulatorArm64Test` · job `xcodebuild` (macos-15) |
| **التحقق Windows** | `:core:data:compileKotlinIosSimulatorArm64` ✅ · `:feature:shell:compileKotlinIosSimulatorArm64` ✅ |

**مؤجل:** StoreKit bridge فعلي · تحقق `xcodebuild` يدوي على Mac CI.

### قرارات تقنية (الجلسة الإضافية)

1. **`ReportsFormatting.formatOneDecimal`** — `kotlin.math.round` بدل `String.format`/`"%.1f".format()` (كلاهما JVM-only على iOS).
2. **`MovitSessionCard.actionLabel` إلزامي** — DS لا يحمل نصوص افتراضية؛ الميزات تمرّر `movitText`.
3. **Program flow:** repository مركّب في `:core:data` بدل feature→feature؛ preview IDs تبقى على Fake.
4. **Workout Start:** Session primary CTA → Customize (ليس Prepare) — مطابق للمواصفة 16.
5. **StoreKit iOS:** إخفاء + رسالة موضّحة أفضل من bridge نصف مكتمل قبل إصدار App Store.
6. **Keychain iOS:** `CFDictionaryCreate` بدل cast Map — نمط مثبت في multiplatform-settings.

### ملخص الحالة بعد العمل الإضافي

| المحور | الحالة |
|--------|--------|
| **Pre-06.2 P0+P1** | ✅ مغلق (WS-1/2/3/7 + iOS compile + V-2/V-3) |
| **Pre-06.2 P2** | ✅ دفعات 1+2 مكتملة · WS-4 **مغلق بالكامل** · WS-5/6 **دفعتان** · دفعة 3 مفتوحة |
| **Phase 05 ص 15/16** | ✅ مسار إنتاجي Android (API + shell + legacy launcher) · Phase 07 للكاميرا |
| **ما تبقى** | StoreKit bridge · WS-5 د3 · Share/صور Program flow · رفع شجرة git (V-1) · Mac CI يدوي |

### أوامر تحقق موصى بها (بعد هذه الجلسة)

```powershell
cd android-poc
.\gradlew.bat --console=plain `
  :feature:library:testDebugUnitTest `
  :feature:shell:testDebugUnitTest `
  :feature:train:testDebugUnitTest `
  :core:training-engine:testDebugUnitTest `
  :core:data:compileKotlinIosSimulatorArm64 `
  :feature:shell:compileKotlinIosSimulatorArm64 `
  :app:assembleDebug
```

**تجربة يدوية Android (debug pilot):** Train → Program list → Week plan → Session → Customize → Run → Start exercise → `TrainingActivity`.

---

## مراجعة تحقّق مستقلة — الجولة 2 (Independent Verification, Round 2 — 2026-06-10)

> مراجعة ثانية بعد تنفيذ توصيات الجولة 1 + Phase 05 ص15/16. **لم تثق بالسجلات**؛ تحقّقت من كل بند **في الكود** + **بنت شجرة العمل الحالية محلياً**.

### نتيجة البناء (شجرة العمل الفعلية، تشمل غير المرفوع)

```
BUILD SUCCESSFUL in 48s (exit 0)
:feature:{account,shell,train,library} testDebugUnitTest · :core:{data,network,resources,training-engine} testDebugUnitTest
:app:assembleDebug · :feature:shell:compileKotlinIosSimulatorArm64  — كلها خضراء
```

### تحقّق بند ببند (من الكود)

| البند | التحقق | الدليل |
|------|--------|--------|
| **V-2** أُغلق | ✅ | `MovitSessionCard.actionLabel` معامل إلزامي (لا افتراضي إنجليزي) · `TrainTodayCard:140 = movitText("train_start_session")` |
| **V-3** أُغلق | ✅ | `IosKeychainSecureSessionStore` يستخدم `CFBridgingRelease(...) as? NSData` + `CFDictionaryCreate` بدل casts مستحيلة |
| **WS-4 دفعة 2** | ✅ مغلق | `:app` = 54 `libs.*` · **صفر** إحداثيات خام · `enableJetifier=false` · `parallel`/`caching` · `local.properties` مُستثنى · `api.properties.example` متتبَّع |
| **WS-5 دفعة 2/3** | ✅ | KMP: `FeedbackPolicy` · `BilateralController`/`BilateralModels` · legacy يفوّض (`as KmpFeedbackPolicy`/`as KmpBilateralController`) + اختبارات parity |
| **WS-6 دفعة 2** | ✅ | `IosMovitPlatform.setActiveUserProgramId` (كاتب فعلي) + `PlanSyncRepository.enrollProgram` · StoreKit: `profile_subscription_ios_unavailable` + معالجة صريحة لـ `OpenSubscription` (لا `-> Unit` صامت) |
| **ص15 Program flow** | ✅ | `ProgramFlowSyncRepository` (core:data) + `SharedProgramFlowRepository`/`ApiMapper`/`Models`/`PreviewData` + `ProgramFlowStateTest` + 11 مفتاح `program_flow_*` |
| **ص16 Workout flow** | ✅ | `WorkoutFlowStateTest` (6 اختبارات) · إصلاح Start CTA في `MovitInnerHost` · سلسلة `LaunchLegacyCameraTraining` |

### الحكم — الجولة 2

كل ما ادّعته السجلات **مُتحقَّق في الكود**، والشجرة الحالية **تُبنى خضراء Android + iOS**. الجودة عالية والاستراتيجية (strangler للـ engine، repository مركّب بدل feature→feature، إخفاء StoreKit الصريح) سليمة.

**الملاحظة الوحيدة (متكررة — 🟡 نظافة):** كامل دفعة هذه الجولة **غير مرفوعة** — **38 ملفاً** (26 معدّل + 12 جديد، ~330+/501- عبر 23 ملف كود): Program flow · WS-5 د2 · WS-4 د2 · V-2/V-3. **الأكشن الأخضر ركض على آخر commit مرفوع، لا على هذه الشجرة** — للمرة الثالثة على التوالي. **الخطوة #1: ارفع الدفعة عبر CI** قبل إعلان أي إغلاق، كي يغطّي الأخضر الكود الفعلي (وخصوصاً تغييرات iOS: Keychain · StoreKit · program-id writer — لم يبنها أي Mac CI بعد).

