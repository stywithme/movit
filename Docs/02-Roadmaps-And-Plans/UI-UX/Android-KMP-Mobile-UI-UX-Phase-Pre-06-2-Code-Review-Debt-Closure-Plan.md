# Android / KMP Mobile UI/UX — Phase Pre-06.2: إغلاق ديون مراجعة الكود

آخر تحديث: **2026-06-10**
الحالة: **مفتوحة (OPEN) — خطة عمل للفريق**
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

**التحقق الحالي (2026-06-10):** الاختبارات خضراء — `account 60` · `shell 37` · `train 18` · `core:data 17` = **132 اختبار ناجح**. لا blockers تمنع العمل؛ الديون أدناه هي تحسينات جودة/إنتاج وليست كسراً للبناء.

---

## نقاط ضعف الخطة (Process / Planning)

> هذه ملاحظات على **الخطة وإدارتها**، مستقلة عن جودة الكود. معالجتها رخيصة وتعالج إرباكاً حقيقياً لأي مطوّر/مدير جديد.

| # | الضعف | الدليل | الإصلاح |
|---|-------|--------|---------|
| PW-1 | **المستندات تتناقض مع الكود ومع بعضها** | خطة Phase-05 تقول «التزم بـ `collectAsState`» بينما الكود (وstatus doc) انتقل بالكامل لـ `collectAsStateWithLifecycle` (27 موضعاً، صفر استثناء) | تحديث Phase-05 + إضافة سطر «iOS debts P1/P2/P3 مغلقة في Pre-05/WS-E» |
| PW-2 | **أرقام قديمة في الاتجاهين** | «514 مفتاح» مُدّعى مقابل **827** فعلياً · «~70 مكوّن» مقابل **~48 ملف/~56 composable** · status doc يقول «Auth–Assessment 0%» وهي منفَّذة | توليد الأرقام بسكربت (WS-7) بدل كتابتها يدوياً |
| PW-3 | **وعود بنية لم تُبنَ وبدأت تؤلم** | `build-logic` convention plugins (مذكورة في الخطة المهنية: سطر 238، 515) غير موجودة؛ `core:model` غائب وهو سبب مخالفة library→explore | WS-4 |
| PW-4 | **وعد «استخدام الـ Logic القديم» كان أوسع من التنفيذ** | حتى Pre-06 كان rewrite موازياً يشارك backend فقط؛ `core:training-engine` بدأ الاستخراج الحقيقي لكن غطّى ~4-5% من منظومة التدريب | WS-5 (مواصلة الاستخراج العددي قبل الكاميرا) |
| PW-5 | **لا مصدر حقيقة واحد للنِسب** | scorecards أحدث من Professional Plan؛ تقارير قديمة ما زالت تُقتبس | اعتماد [Page-Scorecards.md](Page-Scorecards.md) كمصدر وحيد + ربط بقية المستندات إليه |

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

خط الأساس الحالي (2026-06-10): `account 60 · shell 37 · train 18 · core:data 17 = 132 اختبار ناجح`. أي WS يجب ألا يُنقص هذا العدد، ويُفضّل أن يزيده باختبارات WS الجديدة.

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
| WS-7 | أرقام المستندات مولَّدة؛ لا تناقض `collectAsState` |
| التحقق | 132+ اختبار أخضر · `assembleDebug` · iOS compile أخضر |

**لا تُعلَم Pre-06.2 مغلقة** إلا بمرور P0+P1 على الأقل؛ P2 يمكن أن يمتد بالتوازي مع صفحات 15/16 إن لزم.
