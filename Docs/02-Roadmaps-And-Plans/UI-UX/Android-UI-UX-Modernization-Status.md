# Android UI/UX Modernization Status

آخر تحديث: 2026-06-09 (تنفيذ Phase Pre-05 WS-A→WS-F + استكمال WS-C؛ ثم **تحقق مستقل بالبناء** كشف وأصلح 4 أخطاء كانت تكسر iOS compile — التفاصيل في قسم "تحقق مستقل" بخطة Pre-05)

> **🔧 تصحيح 2026-06-09 (post-verification):** بناء Android الأخضر لم يكن يثبت iOS. التحقق بـ `compileKotlinIosSimulatorArm64` كشف: (1) `MovitData` كان يستخدم `GlobalContext` (JVM-only) فيكسر iOS — وهو سبب فشل macOS CI؛ (2) Koin رُفع 4.0.3→4.2.1 لـ Kotlin 2.3؛ (3) `when` غير شامل في `MovitInnerHost` (routes ميتة)؛ (4) `feature:library` ناقصها deps على core:data/network/resources. **كلها أُصلحت وتم التحقق:** Android assemble + tests أخضر، وiOS compile (كامل عبر shell) أخضر. الكود مرفوع (`58f403fc fix CI`)؛ يتبقّى التحقق من نتيجة macOS CI (`linkDebugFrameworkIosSimulatorArm64`).

> **⚠️ بوابة قبل استكمال Phase 05:** بعد مراجعة الكود تبيّن وجود بقايا انتقالية وديون أساس تتعارض مع قرار **الانتقال الكامل بلا حلول وسط**. تم إنشاء [`Android-KMP-Mobile-UI-UX-Phase-Pre-05-Stabilization-And-Debt-Closure-Plan.md`](Android-KMP-Mobile-UI-UX-Phase-Pre-05-Stabilization-And-Debt-Closure-Plan.md) — **يجب إغلاق Pre-05 قبل إضافة أى شاشة جديدة** (Auth/Profile/Onboarding/Assessment). تصحيحات هذا المستند مدرجة أدناه.

## ملخص تنفيذي للمدير

هذا المستند يجمع **حالتين متوازيتين** في مشروع `android-poc`:

| المسار | الوصف | من يستخدمه اليوم؟ |
|--------|--------|-------------------|
| **Legacy Android (XML)** | التطبيق الأصلي — launcher، كاميرا، تدريب حي، ML | المستخدم النهائي عبر APK الرسمي |
| **Movit KMP (Compose)** | التطبيق الجديد المشترك Android + iOS — Design System، Shell، تبويبات رئيسية | فريق التطوير عبر **MovitShellPilotActivity** (debug) و**iosApp** (Xcode) |

**الإنجاز الأهم مؤخراً:** اكتمال **طبقة البيانات المشتركة** (`core:network` + `core:data`) بحيث التبويبات الرئيسية + Session + Report Detail تقرأ من **نفس API** على Android وiOS — بدون الاعتماد على bridges مؤقتة داخل `app/debug`.

**ما يعنيه ذلك عملياً:**

- واجهة KMP أصبحت **قابلة للعرض على iOS** ببيانات حقيقية (عند توفر تسجيل الدخول).
- Android debug shell لم يعد يعتمد على `Movit*ApiBridge` للتبويبات الرئيسية.
- Legacy app ما زال يعمل بشكل مستقل؛ النقل تدريجي وليس استبدالاً فورياً.

**ما لم يُنجز بعد (توقعات واقعية):**

- Auth / Onboarding / Assessment / Level — **أول إصدار KMP** في `feature:account` (scorecards 55–76%) — ليست 0%.
- **Profile/Account:** `MovitProfileScreen` (~70% scorecard) — Language/Appearance/Haptic غير فعّالة بعد.
- تدريب حي بالكاميرا وML — **لم يُنقل** (مقصود تأجيله).
- iOS يحتاج مزامنة مفاتيح تسجيل الدخول (`access_token`, `is_pro`, `active_user_program_id`) مع تطبيق iOS الأصلي.
- **بوابة launcher (WS-C / Pre-06):** Movit shell ما زال debug-only — قرار مكتوب في [Launcher Gate](Android-KMP-Mobile-UI-UX-Launcher-Gate.md)؛ flip بعد 15/16 مفضل. Pre-06 **مغلقة** — [تقرير الإكمال](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Completion-Report.md).

---

## لقطة سريعة — UI / UX / API / iOS

### UI / UX (مسار KMP + Prototypes)

| المنطقة | الحالة | ملاحظة للمدير |
|---------|--------|----------------|
| **HTML Prototypes** (`prototypes/`) | ✅ محدّثة (Premium v7) | مرجع بصري لـ 18 صفحة؛ ليست التطبيق الفعلي |
| **Design System** (`core:designsystem`) | ✅ ~70 مكوّن Movit* | ألوان، typography، motion، كتالوج debug |
| **App Shell** (`feature:shell`) | ✅ 5 تبويبات + مسارات داخلية | Home · Train · Explore · Reports · **Profile** + Session · Report detail · Library |
| **Home** | **79%** | شاشة KMP + API + scorecard WS-E — فجوات hero/level link |
| **Explore** | **75%** | شاشة KMP + sync — فجوات media/filter |
| **Train** | **72%** | 5 حالات + scorecard — فجوات week nav، thumbnails |
| **Reports** | **78%** | 3 تبويبات + Pro gate + Report Detail |
| **Report Detail** | **92%** | UI + API + a11y — Share/Export placeholder |
| **Session (02)** | **48%** | تحرير جزئي — Prepare/onStart فارغ |
| **Library** (05–07) | **55%** | قوائم من Explore cache — i18n hardcoded |
| **Auth** (10) | **76%** | `MovitAuthScreen` — Google stub |
| **Profile** (11) | **70%** | `MovitProfileScreen` — settings جزئية |
| **Onboarding** (12) | **74%** | 7 خطوات + training-profile API |
| **Assessment** (13) | **55%** | PAR-Q + placeholder كاميرا |
| **Level** (14) | **58%** | Profile/Plan — fake/hardcoded phases |

مرجع scorecards: [`Page-Scorecards.md`](Page-Scorecards.md) · [`Sync-App-Pages.md`](Sync-App-Pages.md).

**نقطة الدخول للمعاينة:**

- Android: `MovitShellPilotActivity` (debug فقط — ليس launcher).
- iOS: `iosApp` → `MainViewController()` → نفس Shell.

مرجع تفصيلي صفحة بصفحة: [`Sync-App-Pages.md`](Sync-App-Pages.md) · scorecards: [`Page-Scorecards.md`](Page-Scorecards.md).

### API — طبقة البيانات المشتركة

```
Compose Screen / ViewModel
        ↓
Shared*Repository (في feature:*)
        ↓
MovitData (core:data) — MovitData.install(platform)
        ↓
MovitMobileApi (core:network / Ktor 3)
        ↓
MovitPlatformBindings
   ├── Android: MovitDataInstall → AuthManager, ApiConfig, SharedPreferences
   └── iOS: IosMovitPlatform → NSUserDefaults
```

| الميزة | Repository مشترك | Endpoint(s) | كاش |
|--------|------------------|-------------|-----|
| **Explore** | `SharedExploreRepository` | `GET /api/mobile/explore` | `explore_cache` |
| **Home** | `SharedHomeRepository` | `GET /api/mobile/home` | `home_cache` |
| **Train** | `SharedTrainRepository` | نفس home API (مشتق من `trainMode`) | يعيد استخدام home |
| **Reports** | `SharedReportsRepository` | `GET /api/mobile/reports/dashboard` | `reports_cache` |
| **Report Detail** | `SharedReportDetailRepository` | `GET /api/mobile/reports/metrics?scope=exercise` | per-exercise |
| **Workout Session** | `SharedWorkoutSessionRepository` | `GET .../effective-plan`, `PUT .../user-programs/{id}`, `GET .../substitutions` | `session_cache` |

**سياسات مشتركة:**

- **Offline-first:** عرض الكاش فوراً، ثم sync في الخلفية.
- **Auth:** بدون token → رسالة واضحة أو كاش قديم إن وُجد.
- **Pro:** Reports تتطلب `isProUser()` (Android: `AuthManager`؛ iOS: `is_pro` في UserDefaults).
- **Session:** يتطلب `activeUserProgramId()` (Android: `ProgramRepository`؛ iOS: `active_user_program_id`).

**ملفات التثبيت:**

- Android: `MovitDataInstall.install()` في `MovitShellPilotActivity`.
- iOS: `MovitData.install(IosMovitPlatform())` في `MainViewController.kt`.

**Bridges القديمة:** **✅ مُغلقة (Pre-05 / WS-A، 2026-06-09).** حُذفت ملفات `*ApiBridge` و`Remote*Repository`/`*FetcherBridge`؛ `MovitShellPilotActivity` يثبّت `MovitDataInstall` فقط. مسار بيانات واحد: `MovitData` + `Shared*Repository`.

### iOS

| البند | الحالة |
|-------|--------|
| **Compose Multiplatform على Simulator** | ✅ `linkDebugFrameworkIosSimulatorArm64` ينجح |
| **CI macOS** | ✅ workflow `movit-kmp-ios.yml` |
| **عرض Shell كامل** | ✅ Home · Train · Explore · Reports من `commonMain` |
| **بيانات حقيقية** | ✅ عند `MovitData.install` + token + (للتقارير) Pro + (للجلسة) user program id |
| **بيانات وهمية** | fallback `Fake*Repository` / preview عند غياب التثبيت أو فشل الشبكة |
| **Auth native** | ⏳ لم يُبنَ تدفق تسجيل دخول iOS؛ المفاتيح جاهزة في `IosMovitPlatform` |
| **Base URL افتراضي** | `https://back.mongz.online/` |

**مفاتيح UserDefaults المتوقعة على iOS:**

| المفتاح | الاستخدام |
|---------|-----------|
| `access_token` | Authorization header |
| `is_pro` | فتح Reports |
| `active_user_program_id` | تحميل/حفظ Workout Session |
| `user_name` | تحية Home |
| `app_language` | `ar` / `en` |

---

## هيكل الموديولات الحالي (KMP)

```
android-poc/
├── app/                    # Legacy + debug pilot (MovitShellPilotActivity)
├── shared/                 # AppResult ومساعدات مشتركة
├── core/
│   ├── designsystem/       # MovitTheme + ~70 مكوّن UI
│   ├── resources/          # composeResources ar/en + MovitLocale + movitText + *Strings loaders
│   ├── network/            # Ktor + DTOs + MovitMobileApi
│   └── data/               # MovitData (Koin) + sync repositories + platform bindings
├── feature/
│   ├── shell/              # MovitAppShell — تنقل + 4 tabs
│   ├── home/               # MovitHomeScreen
│   ├── train/              # MovitTrainScreen
│   ├── explore/            # MovitExploreScreen
│   ├── reports/            # MovitReportsScreen + ReportDetailScreen
│   ├── library/            # Session, Prepare, Library, Program detail
│   └── account/            # Auth, Profile, Onboarding, Assessment, Level
└── iosApp/                 # Xcode host → MainViewController
```

**Phases المكتملة (مرجع الخطة):**

| Phase | المحتوى | الحالة |
|-------|---------|--------|
| 01 | Foundation — KMP, DS, Gradle | ✅ |
| 02 | Explore pilot | ✅ |
| 03 | App Shell + تنقل | ✅ |
| 04 | Home dashboard | ✅ |
| 05 | صفحة بصفحة (Train → Reports → Session…) | 🔄 جارٍ — التبويبات الرئيسية + Session + Report detail على API مشترك |
| — | طبقة بيانات مشتركة | ✅ Explore · Home · Train · Reports · Session |

---

## الهدف

تحويل واجهة تطبيق `android-poc` من واجهة PoC نمت على مراحل إلى نظام UI/UX احترافي، نظيف، قابل للتوسع، ويدعم `Light / Dark / System` بشكل صحيح.

الاتجاه التصميمي المطلوب:

- Minimalist
- Clean
- Light-first health/fitness dashboard
- كروت كبيرة بحواف دائرية
- مساحات تنفس واضحة
- ألوان pastel هادئة
- Typography واضح وكبير
- Visual hierarchy قوي
- Dark mode مكافئ وليس مجرد عكس ألوان

الصور المرجعية المرفقة تشير إلى نمط:

- خلفيات فاتحة ناعمة.
- Cards بيضاء/فاتحة مع radius كبير.
- Accent blocks باللون السماوي/الأخضر الهادئ.
- CTA واضح ومحدود.
- Dashboard cards للإحصائيات، النشاط، الصحة، والتمارين.
- استخدام صور/illustrations بشكل محدود داخل كروت Hero أو Training.

## الخطة العامة

الخطة لم تعد مجرد تنظيف XML داخل `:app`. هذا المستند بدأ كمتابعة لمسار Android legacy UI/prototypes، لكن الحالة الحالية أصبحت مقسومة إلى مسارين واضحين:

- **مسار legacy Android/XML:** يحتفظ بالـ launcher والشاشات القديمة، ويُستخدم كمرجع سلوك وبيانات أثناء النقل.
- **مسار KMP/Compose الجديد:** المسار الرئيسي للتوسع. Phases 01→04 مكتملة؛ Phase 05 جارٍ. Design System + Shell + Train/Reports/Session + **طبقة بيانات مشتركة (Ktor)** + إثبات iOS.

التحويل ما زال تدريجياً، لكن الأولوية الآن ليست تجميل كل XML قديم أولاً؛ الأولوية هي نقل الشاشات صفحة بصفحة إلى KMP/Compose عندما يكون ذلك أوضح وأكثر قابلية للمشاركة بين Android وiOS.

## تحديث 2026-06-08: Phase 01→05 + طبقة البيانات المشتركة + iOS

وصل مسار KMP إلى نقطة نضج أعلى من مجرد «إثبات العرض»:

- **Phases 01→04** مكتملة؛ **Phase 05** جارٍ (Train · Reports · Session على API مشترك).
- الموديولات: `:shared`, `:core:designsystem`, `:core:network`, `:core:data`, `:feature:{home,explore,train,reports,library,shell}`.
- **طبقة بيانات مشتركة** (Ktor + repositories) تغطي التبويبات الأربعة + Report Detail + Workout Session.
- **iOS:** Shell كامل + بيانات API عند توفر credentials؛ CI macOS أخضر.
- **Android debug:** `MovitShellPilotActivity` يثبّت `MovitDataInstall` فقط — bridges القديمة لم تعد مطلوبة للتبويبات.

ديون تقنية معروفة (لا تمنع العرض التجريبي):

- Session KMP: بدون thumbnails من API (Explore exercises بلا `imageUrl`).
- SQLDelight غير مُضاف — الكاش JSON في preferences.
- Auth / Onboarding / Assessment — أول إصدار KMP (~55–76% scorecard)؛ كاميرا/ML مؤجلة.
- بيانات معاينة Report Detail (`ReportDetailPreviewData`) ما زالت إنجليزية ثابتة للتطوير.
- نصوص ديناميكية من API (أسماء تمارين، رسائل insights) تبقى كما يرسلها الخادم — لم تُترجم في العميل.

المرحلة التالية المنطقية: **صفحات غائبة (10–14)** أو **تلميع UX Train/Session** حسب أولوية المنتج — وليس نقل `TrainingActivity`/camera/ML.

## تحديث 2026-06-08: HTML Prototypes / Premium UI Refresh

تم تنفيذ موجة تحديث كبيرة على نماذج الـ HTML داخل:

- `Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/`

الغرض من هذه الموجة هو جعل الـ prototypes مرجعاً بصرياً أحدث وأوضح قبل نقل الأنماط إلى Android تدريجياً. هذه الموجة لا تعني أن كل شاشات Android الفعلية أصبحت محدثة، لكنها تثبت الاتجاه النهائي للـ UI/UX.

### ما تم تنفيذه في النماذج

- ترقية `app.css` إلى طبقة Premium مشتركة (`app.css?v=7`) تشمل مكونات reusable جديدة:
  - `dashboard-hero`
  - `metric-row`
  - `metric-grid`
  - `kpi-grid`
  - `feature-card`
  - `media-card`
  - `wide-media-card`
  - `state-card`
  - `timeline-card`
  - `chart-panel`
  - `action-dock`
  - `program-hero`
  - `stats-strip`
  - `empty-state`
  - `quick-grid`
  - `catalog-page`
  - مكونات polishing لـ Auth/Profile/Onboarding
- تحديث `00-components.html` بقسم جديد:
  - **Premium patterns**
  - يعرض hero، metrics، KPI cards، feature card، library toolbar، timeline، empty state، وaction dock.
- تحديث كل ملفات HTML لاستخدام `app.css?v=7`.
- تقليل الاعتماد على inline styles في الصفحات الرئيسية، ونقل أنماط مشتركة إلى `app.css`.
- تثبيت typography scale للـ prototypes:
  - Display 32 / 800
  - Title 18 / 700
  - Body 15 / 400
  - Caption 12 / 600

### تنظيم التنقل في النماذج

- تنظيم `nav.js` بحيث يصبح:
  - التنقل السفلي: الصفحات الرئيسية (`Home`, `Train`, `Explore`, `Reports`) + `Account` + `Components`.
  - التنقل العلوي: التدفقات الداخلية (`training`, `program`, `library`, `workout`, `assessment`, `onboarding`).
- حذف شاشة `18-training-live.html` من النماذج وإزالة روابطها من `nav.js` و`index.html`.
- تحديث مسار التدريب ليشمل:
  - `03-prepare.html`
  - `02-session.html`
  - `16-workout-flow.html`
  - `17-report-detail.html`
- إصلاح `03-prepare.html` ليكون تابعاً لـ `Train` عبر `data-main="01-train.html"`.

### الشاشات التي تم تحديثها بصرياً

- الشاشات الرئيسية:
  - `08-home.html`
  - `01-train.html`
  - `04-explore.html`
  - `05-exercises.html`
  - `06-workouts.html`
  - `09-reports.html`
- تدفقات التدريب والبرامج:
  - `02-session.html`
  - `03-prepare.html`
  - `07-program.html`
  - `15-program-flow.html`
  - `16-workout-flow.html`
  - `17-report-detail.html`
- التقييم والمستوى:
  - `13-assessment.html`
  - `14-level-plan.html`
- الحساب والبروفايل:
  - `10-auth.html`
  - `11-profile.html`
  - `12-profile-onboarding.html`
- الكتالوج والنظام:
  - `index.html`
  - `00-components.html`

### إصلاحات مهمة تمت أثناء المراجعة البصرية

- إصلاح تداخل شاشة Splash في `10-auth.html` عبر فصل `auth-panel` / `auth-content` وتوسيط الحالات.
- استبدال/توحيد ألوان semantic في الـ prototypes حتى تظل ضمن palette:
  - `success` مرتبط بـ Lime.
  - `warning/error` مرتبطان بـ Coral.
  - `gold` مرتبط بـ Lime tint بدلاً من لون خارجي.
- إضافة tokens ناقصة في `app.css`:
  - `--mist`
  - `--ink-veil-18`
  - `--ink-veil-55`
- إصلاح تباين النصوص في:
  - `dashboard-hero--ink`
  - `feature-card`
  - `accent--blue`
- إصلاح تمدد الأزرار داخل الصفوف:
  - `.row .btn`
  - `.action-dock > .btn`
  - `.prog-card .btn`
  - `.copy-actions .btn`
- إصلاح بطاقات الـ rings في `00-components.html` عبر `ring-card` حتى لا يحدث قص للنص داخل الأعمدة الضيقة.
- حصر `.filter-chip` داخل `.swap-filters` لتجنب تضاربها مع filter chips الخاصة بصفحات المكتبة.
- تعطيل فواصل `.stat-grid` داخل `program-screen` لتجنب خطوط غير صحيحة في `07-program.html`.
- إضافة `@keyframes spin` و`hero-media .gpill` لدعم شاشة `03-prepare.html`.
- تنظيف ألوان raw داخل HTML، بحيث تبقى hex الخام فقط في عرض الـ palette داخل `00-components.html`.

### حالة النماذج حالياً

النماذج أصبحت مرجعاً بصرياً أفضل للمرحلة التالية، لكنها ما زالت HTML prototypes وليست تطبيق Android الفعلي. يجب استخدام هذه النماذج كمرجع عند تحويل شاشات Android التالية:

- `Train`
- `Explore`
- `Reports`
- `Program Session`
- `Level Profile`
- `Assessment`

## المرحلة 1: تثبيت Design System

### المطلوب

- فصل `light/dark tokens` فعلياً.
- اعتماد palette مبدئية (مرجع الـ HTML prototype):
  - **Primary (aqua):** `#8ECFE3`
  - **Lime / soft accent:** `#C4D489`
  - **Ink / dark background:** `#282828`
  - **Coral / action:** `#E76D46`
  - **Mist / light background:** `#D8DCDF`
- توحيد:
  - Typography
  - Spacing
  - Radius
  - Cards
  - Buttons
- دعم `Light / Dark / System` من الإعدادات.

### تم تنفيذه

- تم تحويل `values/colors.xml` إلى light palette حقيقية.
- تم تحويل `values-night/colors_night.xml` إلى dark palette حقيقية.
- تم تحديث `values/themes.xml` لاستخدام `accent_soft` كـ secondary color.
- تم توسيع `values/dimens.xml` بأبعاد مكونات مشتركة:
  - `component_card_min_height`
  - `component_stat_card_min_height`
  - `component_icon_container`
  - `component_empty_icon`
- تم توسيع `values/styles.xml` بـ styles جديدة:
  - `Widget.WayToFix.Button.Primary.Small`
  - `Widget.WayToFix.Button.Tonal`
  - `Widget.WayToFix.Card.Outlined`
  - `Widget.WayToFix.Card.List`
  - `Widget.WayToFix.Card.Stat`
- تم إضافة خيار `Mode` في `Profile`:
  - `System`
  - `Light`
  - `Dark`
- تم إنشاء `AppThemeManager` لحفظ وتطبيق وضع الثيم.
- تم ربط `AppThemeManager.applySavedMode()` داخل `PoseApp` حتى يطبق الثيم مبكراً عند تشغيل التطبيق.
- تم مزامنة الـ HTML prototype (`prototypes/app.css`, `00-components.html` — قسم Color palette) مع نفس الـ palette الخمسة أعلاه.

### ملفات تم لمسها

- `android-poc/app/src/main/res/values/colors.xml`
- `android-poc/app/src/main/res/values-night/colors_night.xml`
- `android-poc/app/src/main/res/values/themes.xml`
- `android-poc/app/src/main/res/values/dimens.xml`
- `android-poc/app/src/main/res/values/styles.xml`
- `android-poc/app/src/main/res/values/strings.xml`
- `android-poc/app/src/main/res/values-ar/strings.xml`
- `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/theme/AppThemeManager.kt`
- `android-poc/app/src/main/java/com/trainingvalidator/poc/PoseApp.kt`
- `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/profile/ProfileActivity.kt`
- `android-poc/app/src/main/res/layout/activity_profile.xml`

### الحالة

منفذة جزئياً إلى جيد. ما زالت هناك شاشات كثيرة تستخدم ألوان hardcoded أو styles قديمة، لكن النظام الأساسي موجود الآن.

## المرحلة 2: بناء UI Components قابلة لإعادة الاستخدام

### المطلوب

- `AppHeader`
- `ScreenScaffold`
- `StatCard`
- `ListCard`
- `PrimaryCTA`
- `EmptyState`
- `SectionHeader`

### تم تنفيذه

- تم تحسين `component_app_header.xml`.
- تم إنشاء:
  - `component_screen_scaffold.xml`
  - `component_stat_card.xml`
  - `component_list_card.xml`
  - `component_primary_cta.xml`
  - `component_empty_state.xml`
  - `component_section_header.xml`
- تم إنشاء drawables مساعدة:
  - `bg_component_icon_container.xml`
  - `bg_empty_state_icon.xml`

### ملفات تم لمسها

- `android-poc/app/src/main/res/layout/component_app_header.xml`
- `android-poc/app/src/main/res/layout/component_screen_scaffold.xml`
- `android-poc/app/src/main/res/layout/component_stat_card.xml`
- `android-poc/app/src/main/res/layout/component_list_card.xml`
- `android-poc/app/src/main/res/layout/component_primary_cta.xml`
- `android-poc/app/src/main/res/layout/component_empty_state.xml`
- `android-poc/app/src/main/res/layout/component_section_header.xml`
- `android-poc/app/src/main/res/drawable/bg_component_icon_container.xml`
- `android-poc/app/src/main/res/drawable/bg_empty_state_icon.xml`

### الحالة

منفذة كأساس. المكونات موجودة، لكن لم يتم استخدامها في كل الشاشات بعد. بعض الشاشات تم تنظيفها مباشرة داخل layout مع نفس الـ styles بدلاً من include لكل component، وهذا مقبول مؤقتاً لتقليل مخاطر كسر الـ binding.

## المرحلة 3: تنظيف الشاشات القديمة تدريجياً

### ترتيب التنفيذ المخطط

1. `Home`
2. `Train`
3. `Explore`
4. `Reports`
5. `Exercise Detail`
6. `Program Session`
7. `Level Profile`

## شاشة Home

### المطلوب

- تحويلها إلى light-first dashboard.
- استخدام cards كبيرة rounded.
- إزالة hardcoded colors/textSize.
- الحفاظ على كل IDs المستخدمة في `HomeFragment`.
- توحيد cards والإحصائيات والـ CTAs.

### تم تنفيذه

- تم تحويل `fragment_home.xml` إلى بنية MaterialCardView نظيفة.
- تم الحفاظ على IDs التي يستخدمها `HomeFragment`.
- تم إزالة hardcoded colors/textSize الواضحة من layout.
- تم تحويل quick stats إلى stat cards.
- تم تحويل cards الأساسية إلى:
  - `Widget.WayToFix.Card.Stat`
  - `Widget.WayToFix.Card.Outlined`
  - `Widget.WayToFix.Card.List`
- تم تحسين:
  - Header
  - Level card
  - Active program card
  - Today plan card
  - Alert card
  - Progression card
  - Reports summary card
  - Recent activity section
  - Quick actions
  - Body Scan CTA
  - Train CTA

### ملفات تم لمسها

- `android-poc/app/src/main/res/layout/fragment_home.xml`

### الحالة

منفذة كأول شاشة رئيسية. تحتاج build من Android Studio ومراجعة بصرية على جهاز/محاكي، خصوصاً في light/dark.

## شاشة Exercise Detail

### المطلوب

- إزالة legacy dark-only styling.
- استخدام tokens/styles.
- إزالة hardcoded colors/textSize.
- إبقاء السلوك الحالي في `ExerciseDetailActivity`.

### تم تنفيذه

- تم تحويل `activity_exercise_detail.xml` لاستخدام:
  - `@color/background`
  - `@color/surface`
  - `@color/text_*`
  - `Widget.WayToFix.*`
  - `MaterialCardView`
- تم تحديث ألوان أزرار visual indicator في Kotlin لتقرأ من الثيم بدلاً من hex ثابت.
- تم إضافة strings جديدة للعنوان والرسائل الناقصة.
- تم إصلاح duplicate strings لاحقاً بعد فشل build بسبب `indicator_line` و`indicator_arc`.

### ملفات تم لمسها

- `android-poc/app/src/main/res/layout/activity_exercise_detail.xml`
- `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/exercises/ExerciseDetailActivity.kt`
- `android-poc/app/src/main/res/values/strings.xml`
- `android-poc/app/src/main/res/values-ar/strings.xml`

### الحالة

منفذة كنموذج legacy screen. تحتاج مراجعة build وبصرية.

## شاشة Profile / Settings

### المطلوب

- إضافة خيار Mode في الإعدادات.
- ربطه بالـ light/dark/system.

### تم تنفيذه

- أضيف صف `Mode` في `activity_profile.xml`.
- أضيف dialog في `ProfileActivity`.
- أضيفت strings عربية وإنجليزية.
- تم حفظ الاختيار في `SharedPreferences`.
- تم تطبيق الاختيار عبر `AppCompatDelegate`.

### الحالة

منفذة. تحتاج اختبار يدوي للتأكد من:

- اختيار Light يطبق light tokens.
- اختيار Dark يطبق night tokens.
- اختيار System يتبع إعدادات الجهاز.
- عدم إعادة تشغيل غير مرغوبة أو فقدان حالة الشاشة.

## المرحلة 3: ما لم ينفذ بعد

### Train / Explore / Reports / Session (مسار KMP)

| الصفحة | KMP UI | بيانات API مشتركة | فجوات UX الرئيسية |
|--------|--------|-------------------|-------------------|
| **Train** | ✅ `MovitTrainScreen` | ✅ `SharedTrainRepository` | تنقل أسبوع، hero No-plan، thumbnails جلسات |
| **Explore** | ✅ `MovitExploreScreen` | ✅ `SharedExploreRepository` | بطاقات أفقية، تفاصيل برنامج |
| **Reports** | ✅ `MovitReportsScreen` | ✅ `SharedReportsRepository` | delta اتجاهات، تفاصيل charts |
| **Report Detail** | ✅ `ReportDetailScreen` | ✅ `SharedReportDetailRepository` | joints من API غير متوفرة بعد |
| **Session** | ✅ `WorkoutSessionScreen` | ✅ `SharedWorkoutSessionRepository` | Prepare، workout flow، كاميرا |

**Legacy XML** (`fragment_train`, `fragment_explore`, `fragment_history`): ما زال موجوداً للـ launcher ولم يُحدَّث بنفس موجة Premium — التحديث البصري الحالي يركز على KMP.

### Train (Legacy XML فقط)

لم يُحدَّث `fragment_train.xml` في موجة Premium. KMP Train هو المسار النشط للتطوير.

### Program Session

لم يبدأ بعد.

المطلوب:

- تنظيف UI فقط أولاً بدون تفكيك معماري كبير.
- لاحقاً تفكيك `ProgramSessionActivity`.
- نقل sync/customization logic خارج Activity.
- إدخال ViewModel/use cases.

### Level Profile

لم يبدأ بعد.

المطلوب:

- إزالة UI المبني programmatically قدر الإمكان.
- تحويله إلى XML أو مكونات reusable.
- إزالة `Color.parseColor`.
- استخدام tokens/styles.

### Assessment UI

لم يبدأ بعد.

المطلوب:

- تقليل programmatic UI.
- بناء layouts قابلة للصيانة.
- توحيد cards/buttons/empty states.

## المرحلة 4: تحسين الهيكلة

### المطلوب

- نقل API calls خارج Activities إلى repositories/use cases.
- تفكيك `ProgramSessionActivity`.
- تخفيف `TrainingViewModel`.
- لاحقاً إدخال DI خفيف مثل Hilt أو factory منظم.

### ما تم تنفيذه ضمن مسار KMP

- بنية موديولات KMP: `core:{designsystem,network,data}` + `feature:*` + `shell`.
- نمط شاشات: `Route / Screen / ViewModel / UiState / Event / Effect`.
- KMP `androidx.lifecycle.ViewModel` في كل التبويبات الرئيسية.
- **طبقة بيانات مشتركة:** `MovitData` + `Shared*Repository` + Ktor — Android وiOS من نفس المصدر.
- `MovitPlatformBindings` يعزل Auth، base URL، كاش، Pro، active user program.
- اختبارات وحدة: Explore merge، Train/Reports/Session mappers، shell state.
- Shell debug-only — `releaseRuntimeClasspath` للـ legacy لم يتأثر.

### ما يزال ديناً داخل legacy Android

التفاصيل في القسم التالي. المهم هنا أن هذه الديون لا تمنع Phase 05، لكنها تمنع نسخ legacy architecture كما هي إلى KMP.

## أهم المشاكل المعمارية التي ما زالت قائمة

المشاكل التالية تخص legacy app أو المراحل التي لم تنتقل بعد إلى KMP. لا تمنع Phase 05، لكنها تحدد ما يجب عدم نسخه كما هو:

- `ProgramSessionActivity` ما زالت كبيرة وتخلط UI + API + sync + state.
- `TrainingViewModel` ما زال ثقيلاً ويحتوي مسؤوليات كثيرة.
- بعض Activities تستدعي `ApiClient` مباشرة.
- لا يوجد DI framework (Koin/Hilt) في KMP — `MovitData.install()` singleton كافٍ حالياً.
- بعض شاشات UI لا تزال programmatic.
- ما زال هناك تكرار في cards والـ layouts خارج المكونات المشتركة.

## التحقق حتى الآن

### تم

- `ReadLints` أظهر عدم وجود أخطاء على الملفات التي تم تعديلها أثناء العمل.
- المستخدم شغّل build من Android Studio ونجح قبل بعض التعديلات اللاحقة.
- بعد ظهور خطأ duplicate strings، تم إصلاحه.
- تم تشغيل `ReadLints` على مجلد `Docs/02-Roadmaps-And-Plans/UI-UX/prototypes` بعد موجة Premium Refresh، ولم تظهر أخطاء linter.
- تم فحص توازن أقواس `app.css` بعد تعديلات Premium، والنتيجة سليمة (`css_brace_delta 0`).
- تم التأكد من عدم وجود بقايا `app.css?v=6` داخل prototypes.
- تم التأكد من عدم وجود raw colors داخل HTML خارج قسم عرض الـ palette في `00-components.html`.
- تم التأكد من عدم وجود بقايا كلاسات/مسارات قديمة مثل:
  - `premium-floating-bar`
  - `explore-toolbar`
  - `explore-search`

### مطلوب من Android Studio

يوجد Gradle wrapper داخل `android-poc` الآن، لذلك التحقق لم يعد معتمداً على Android Studio فقط.

على Windows:

```powershell
cd android-poc
.\gradlew.bat --console=plain :app:assembleDebug
.\gradlew.bat --console=plain :feature:shell:testDebugUnitTest
```

على Mac/Linux:

```bash
cd android-poc
./gradlew --console=plain :app:assembleDebug
./gradlew --console=plain :feature:shell:testDebugUnitTest
```

بعد تعديلات KMP أو iOS entry point:

```powershell
cd android-poc
.\gradlew :app:assembleDebug :feature:shell:linkDebugFrameworkIosSimulatorArm64 :feature:train:testDebugUnitTest :feature:reports:testDebugUnitTest :feature:library:testDebugUnitTest :core:data:testDebugUnitTest
```

تشغيل Shell على Android (debug):

```text
adb shell am start -n com.trainingvalidator.poc/com.movit.debug.MovitShellPilotActivity
```

Android Studio ما زال مفيداً للمراجعة البصرية وتشغيل المحاكي:

- `Build > Make Project`
- أو task المناسب داخل Android Studio.

## قواعد التنفيذ القادمة

- لا نعيد بناء التطبيق كله دفعة واحدة.
- كل موجة تكون شاشة أو مجموعة صغيرة من الشاشات.
- نحافظ على IDs الحالية لتجنب كسر ViewBinding/Kotlin.
- لا نغير logic أثناء موجة UI إلا عند الضرورة.
- بعد كل شاشة:
  - فحص hardcoded colors/textSize.
  - فحص lints.
  - build من Android Studio.
  - مراجعة light/dark بصرياً.

## الأولوية التالية

**أولاً — [Phase Pre-05](Android-KMP-Mobile-UI-UX-Phase-Pre-05-Stabilization-And-Debt-Closure-Plan.md):**

| WS | الحالة | ملاحظة |
|----|--------|--------|
| WS-A جسور | ✅ | صفر `ApiBridge`/`FetcherBridge` في الكود |
| WS-B Profile | ✅ | تبويب Account + `MovitProfileRoute`؛ Components debug-only |
| WS-C نصوص | ✅ | `:core:resources` + تغطية التبويبات الرئيسية + Train UI + Report Detail + Session |
| WS-D اختبارات sync | ✅ | `core:data` unit tests لفروع fallback/auth |
| WS-E iOS | ✅ | deployment 16.0 · `collectAsStateWithLifecycle` · `ViewModelStoreOwner` |
| WS-F Koin | ✅ | `MovitData.install` → `startKoin`؛ repos عبر DI |
| WS-G launcher | ⏳ | معيار مكتوب — التنفيذ الإنتاجي لم يبدأ |

**ثانياً — استكمال Phase 05 (مسموح بعد Pre-05 ما عدا WS-G):**

- **صفحات غائبة:** Auth (10) → Profile (11) → Onboarding (12) → Assessment/Level حسب أولوية المنتج.
- **مزامنة iOS auth:** كتابة `access_token`, `is_pro`, `active_user_program_id` عند تسجيل الدخول في تطبيق iOS.
- **تلميع UX Train/Session** حسب [`Sync-App-Pages.md`](Sync-App-Pages.md) (أسبوع، thumbnails، Prepare flow).
- **إبقاء camera/ML/TrainingActivity** مؤجلة لـ Phase 7 — لكن **المحرك العددى الخالص** (`OneEuroFilter`/`AngleCalculator`/score calculators) يُنقل لـ `commonMain` كأول خطوة في حدود Phase 7.

## Checklist

- [x] فصل light/dark tokens.
- [x] اعتماد palette المبدئية.
- [x] توسيع typography/spacing/radius/styles.
- [x] إضافة Mode في Profile settings.
- [x] إنشاء `AppThemeManager`.
- [x] إنشاء reusable UI components الأساسية.
- [x] إضافة `SectionHeader`.
- [x] تحويل `Home` مبدئياً.
- [x] تحويل `Exercise Detail` مبدئياً.
- [x] تحديث `prototypes/app.css` إلى Premium UI v7.
- [x] تحديث `00-components.html` بقسم Premium patterns.
- [x] تحديث كتالوج prototypes في `index.html`.
- [x] تنظيم `nav.js` بين main tabs والتدفقات الداخلية.
- [x] حذف `18-training-live.html` من prototype navigation.
- [x] تحديث prototypes الرئيسية: `Home`, `Train`, `Explore`, `Reports`.
- [x] تحديث prototypes الداخلية: session, prepare, program, workout flow, assessment, level, report detail.
- [x] تحديث Auth/Profile/Onboarding prototypes.
- [x] مراجعة ألوان prototypes وحصرها في palette.
- [x] إصلاح مشاكل التداخل والمساحات والتناسق في مكونات prototypes.
- [x] إنشاء KMP foundation وموديولات `shared`, `core:designsystem`, `feature:home`, `feature:explore`, `feature:shell`.
- [x] اعتماد `MovitTheme` و`Movit*` naming في الموديولات الجديدة.
- [x] تحويل Home/Explore إلى ViewModel + UDF داخل KMP shell.
- [x] إثبات debug API bridge على Home وExplore (استُبدل لاحقاً بطبقة مشتركة).
- [x] CI macOS/iOS أخضر.
- [x] iOS render proof من Xcode ناجح.
- [x] `core:network` — Ktor 3 + DTOs + `MovitMobileApi`.
- [x] `core:data` — `MovitData`, sync repositories, platform bindings.
- [x] `SharedHomeRepository` + `SharedExploreRepository` (Android + iOS).
- [x] `SharedTrainRepository` — Train من home API.
- [x] `SharedReportsRepository` + `SharedReportDetailRepository`.
- [x] `SharedWorkoutSessionRepository` — effective plan + save + substitutions.
- [x] إزالة استدعاء `Movit*ApiBridge` من `MovitShellPilotActivity` (Pre-05 / WS-A).
- [x] `:core:resources` — نصوص ar/en + `MovitLocaleProvider` + `movitText`/`localizedString`.
- [x] نقل نصوص Home/Explore/Train/Reports/Shell (nav/profile) إلى موارد مشتركة.
- [x] نقل نصوص مكوّنات Train الثانوية + Report Detail + Workout Session.
- [x] `ReportDetailStrings` + `SessionStrings` + `generateMovitEnglishStrings` Gradle task.
- [x] Profile route حقيقية (`MovitProfileRoute`) بدل placeholder Components.
- [x] Koin في `MovitData` (Pre-05 / WS-F).
- [x] iOS P1/P2/P3 (Pre-05 / WS-E).
- [x] اختبارات sync repos (Pre-05 / WS-D).
- [ ] بوابة launcher WS-G — Movit shell كـ entry point إنتاجي.
- [ ] Build نهائي بعد تحويل `Home`.
- [ ] مراجعة light/dark بصرياً على Android.
- [ ] مراجعة prototypes بصرياً يدوياً في المتصفح بعد آخر إصلاحات Premium.
- [x] إنشاء `Train-Page-Modernization-Spec.md`.
- [x] تنفيذ `feature:train` داخل KMP shell.
- [x] ربط Train داخل `feature:shell` كصفحة حقيقية بدلاً من placeholder.
- [x] تشغيل اختبارات Train وShell بعد الربط.
- [x] تشغيل Android debug assemble بعد ربط Train.
- [x] فحص release runtime بعد ربط Train.
- [x] iOS link بعد Train + Reports + Session.
- [x] `Reports Overview` — KMP UI + API مشترك.
- [x] `Workout Session` — KMP UI + API مشترك (بدون كاميرا).
- [ ] iOS smoke يدوي ببيانات حقيقية (token + pro + user program).
- [x] تحويل `Profile / Account shell` (هيكل أدنى — `MovitProfileRoute`؛ بدون auth).
- [ ] تحويل `Program Session` بعد فصل منطقها.
- [ ] تحويل `Level Profile`.
- [ ] تنظيف Assessment UI.
- [x] نقل API للتبويبات الرئيسية + Session + Report detail إلى repositories مشتركة (KMP).
- [ ] نقل بقية legacy Activities إلى repositories (Program Session، Training، …).
- [ ] تفكيك `ProgramSessionActivity`.
- [ ] تخفيف `TrainingViewModel`.
- [x] إدخال DI (Koin في `MovitData` — Pre-05 / WS-F).

---

## سجل التنفيذ — 2026-06-09 (Phase Pre-05 + استكمال WS-C)

### ما تم إنجازه

**WS-A — حذف الجسور الانتقالية**
- حذف ملفات `Movit*ApiBridge` من `app/src/debug`.
- حذف `Remote*Repository` و`*FetcherBridge` من `androidMain` في train/reports/library.
- `MovitShellPilotActivity` يثبّت `MovitDataInstall` فقط.
- تحقق: `rg "ApiBridge|FetcherBridge"` ⇒ صفر نتائج.

**WS-B — Profile حقيقي**
- إضافة `Profile` إلى `MovitAppDestination` وإزالة `Components` من التبويبات الرئيسية.
- `MovitProfileRoute` — إعدادات، لغة، حالة Pro من `MovitPlatformBindings`.
- `MovitComponentsRoute` أصبح debug-only.

**WS-C — نظام نصوص مشترك (الجزء الأول ثم الاستكمال)**
- موديول جديد `:core:resources` مع `composeResources/values/strings.xml` و`values-ar/strings.xml`.
- `MovitLocaleProvider` + `LocalMovitLanguage` + `movitText()` / `localizedString()`.
- loaders: `HomeStrings`, `ExploreStrings`, `TrainStrings`, `ReportsStrings`, `ReportDetailStrings`, `SessionStrings`.
- نقل نصوص التبويبات الرئيسية + shell (nav/profile).
- **استكمال لاحق:** مكوّنات Train الثانوية (`TrainTodayCard`, `TrainStatusBanner`, `TrainNoPlanSection`, `TrainQuickActions`, `TrainReportSection`, `TrainReadinessCard`).
- **استكمال لاحق:** `ReportDetailScreen` + mapper + repository؛ `WorkoutSessionScreen` + sheets + mapper + repository.
- إضافة `implementation(project(":core:resources"))` إلى `:feature:library`.
- مهمة Gradle `generateMovitEnglishStrings` تولّد `MovitEnglishStrings.kt` للاختبارات.

**WS-D — اختبارات sync repos**
- تغطية فروع fallback/auth في `HomeSyncRepository`, `ReportsSyncRepository`, `WorkoutSessionSyncRepository` عبر MockEngine.

**WS-E — إغلاق ديون iOS**
- `iosApp/project.yml`: deployment target **16.0** (كان 18.5).
- كل الـ routes تستخدم `collectAsStateWithLifecycle()` بدل `collectAsState()`.
- `MainViewController` يوفّر `ViewModelStoreOwner` حقيقي.

**WS-F — Koin**
- `MovitData.install()` يستدعي `startKoin`؛ repositories تُحل عبر DI بدل singleton مباشر.

**التحقق**
```powershell
.\gradlew.bat :app:assembleDebug :feature:train:testDebugUnitTest :feature:reports:testDebugUnitTest :feature:library:testDebugUnitTest
```
→ BUILD SUCCESSFUL

### قرارات معمارية

| القرار | السبب |
|--------|-------|
| `movitString("key")` عبر `Res.allStringResources` بدل `Res.string.*` المباشر | Compose Resources يقسّم accessors (`String0`/`String1`/`String2`) ولا تُحل مراجع `home_*` في `commonMain` حتى داخل `:core:resources` |
| `MovitEnglishStrings.kt` مُولَّد من Gradle | unit tests في JVM بدون Android context تحتاج fallback إنجليزي (تجنب `MissingResourceException` / `getSystem not mocked`) |
| `:core:resources` لا يعتمد على `:core:data` | اللغة في UI عبر `MovitLocaleProvider`؛ في repositories عبر `suspend` + `localizedString(language, key)` |
| Profile هيكل أدنى بدون auth | يغلق فجوة التنقل دون توسيع النطاق لـ Auth flow كامل في Pre-05 |
| Koin وليس Hilt | الحفاظ على KMP مشترك بين Android وiOS |

### مشكلات واجهتنا وكيف حُلَّت

**1. `Unresolved reference` لـ `Res.string.home_*` في feature modules**
- **السبب:** accessors المُولَّدة تُقسَّم حسب عدد المعاملات؛ مفاتيح الشاشات الرئيسية في `String0` لا تُصدَّر بشكل يُحلّ في `commonMain` عند compile feature modules.
- **محاولات فاشلة:** نقل `*Strings` loaders إلى `:core:resources` مع `Res.string`؛ `kotlin.srcDir` لمجلد accessors؛ `generateResClass = always` في كل feature؛ `api(project(":core:resources"))`.
- **الحل:** lookup ديناميكي `movitString("key")` + overload `movitText("key")` للـ Composables + `localizedString(language, "key", …)` للـ mappers.

**2. فشل unit tests بسبب موارد Compose على JVM**
- **السبب:** `stringResource()` يحتاج Android context غير متوفر في `testDebugUnitTest`.
- **الحل:** `MovitEnglishStrings` كخريطة fallback + مهمة Gradle تولّدها من `strings.xml` الإنجليزي.

**3. تنسيق قوالب `%1$d` / `%1$s` في commonMain**
- **الحل:** `formatMovitTemplate` في `:core:resources`؛ escape `$` في الملف المُولَّد لتجنب أخطاء Kotlin string templates.

**4. مسار بيانات مزدوج (bridges + Shared repos)**
- **الحل:** حذف كامل للجسور (WS-A) — مسار واحد فقط.

### ما لم يُنجز في هذه الجلسة

- **WS-G:** بوابة launcher — المعيار مكتوب؛ `MovitShellPilotActivity` ما زال debug-only.
- **Auth / Onboarding / Assessment / Level** — خارج نطاق Pre-05.
- **iOS smoke يدوي** ببيانات حقيقية (token + pro + user program).
- بيانات معاينة `ReportDetailPreviewData` ما زالت إنجليزية ثابتة.
- نصوص ديناميكية من API (أسماء تمارين، insights) — تبقى كما يرسلها الخادم.
