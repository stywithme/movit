# Android / KMP Mobile UI/UX Professional Plan

آخر تحديث: 2026-06-09 (post Pre-06 CLOSED + دفعة Account في Phase 05)

> **Phase Pre-06 مغلقة (2026-06-09):** [`Android-KMP-Mobile-UI-UX-Phase-Pre-06-Completion-Report.md`](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Completion-Report.md) — navigation/back · training-engine · secure session · launcher gate · scorecards · visual QA.

> **قرار محدِّث (2026-06-09): انتقال كامل بلا حلول وسط ولا حلول انتقالية.** أُغلقت بوابة Pre-05 (جسور، Koin، iOS compile، نصوص مشتركة) ثم اُستكملت **دفعة الحساب والتقييم** في Phase 05 — التفاصيل في قسم «ملخص تنفيذي للمدير» أدناه وفي [`Phase-05`](Android-KMP-Mobile-UI-UX-Phase-05-Page-By-Page-Modernization-Plan.md).

## ملخص تنفيذي للمدير (2026-06-09)

### ماذا أنجزنا في هذه الدفعة؟

بعد إغلاق **Phase Pre-05** (تثبيت الأساس بدون جسور Retrofit مؤقتة)، نُفِّذت **أول دفعة كاملة من تدفقات الحساب والتقييم** في مسار KMP — الصفحات 10–14 من الـ prototypes — داخل موديول جديد `:feature:account`، مع ربطها بـ Shell وHome.

| الصفحة | Prototype | الحالة في KMP | ملاحظة للمدير |
|--------|-----------|---------------|----------------|
| **10 Auth** | `10-auth.html` | ✅ **76%** | Splash · Intro · Sign in/up · Forgot — API Ktor + جلسة آمنة (WS-D) — تفصيل: [`Page-Scorecards.md`](Page-Scorecards.md) |
| **11 Profile** | `11-profile.html` | ✅ **86%** | Hero · Pro/Free · إعدادات فعّالة · اشتراك · logout confirm — تبويب Profile |
| **12 Onboarding** | `12-profile-onboarding.html` | ✅ **74%** | معالج 7 خطوات · `PUT /api/mobile/training-profile` |
| **13 Assessment** | `13-assessment.html` | ✅ **55%** | PAR-Q · Body scan (UI فقط) — **بدون كاميرا** (Phase 07) |
| **14 Level & Plan** | `14-level-plan.html` | ✅ **58%** | Level profile · Plan overview · `GET /api/mobile/level-profile` |

### البنية التقنية (باختصار)

```text
feature:account (جديد)
  ├── Auth / Profile / Onboarding / Assessment / Level
  └── UDF: ViewModel + State / Event / Effect + Movit* UI

core:network  → login, register, profile, training-profile, level-profile (Ktor)
core:data     → AccountSyncRepository + MovitData.account
core:resources → نصوص ar/en لكل الشاشات — العدد في [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md)

feature:shell → مسارات داخلية: Auth, Onboarding, Assessment, LevelProfile
feature:home  → Body scan → Assessment · بطاقة المستوى → Level
```

### التحقق (Build & Tests)

| الأمر | النتيجة |
|-------|---------|
| `:app:assembleDebug` | ✅ |
| `:feature:account:testDebugUnitTest` | ✅ (11) |
| `:feature:shell:testDebugUnitTest` | ✅ (20) |
| `:core:training-engine:testDebugUnitTest` | ✅ (18) |
| `:core:data:testDebugUnitTest` | ✅ (17) |
| `:feature:shell:compileKotlinIosSimulatorArm64` | ✅ |

### كيف يُعرض للمدير اليوم؟

- **Android (فريق التطوير):** `MovitShellPilotActivity` — debug فقط، ليس launcher الإنتاج.
- **iOS:** `iosApp` → نفس Shell؛ يحتاج `MovitData.install` + مفاتيح `access_token` إن أُريدت بيانات حقيقية.
- **تدفقات تجريبية:** Account → Sign in · Home → Start scan · Home → View level · Account → Training profile / Body assessment.

### ما لم يُنجز بعد (متعمد أو لاحقاً)

| البند | السبب |
|-------|--------|
| Google Sign-In | يحتاج جسر Android/iOS (Credentials) — الزر UI فقط حالياً |
| كاميرا Assessment الحية | Phase 07 (camera/ML) |
| Launcher production | Shell ما زال debug-only (WS-C — [Launcher Gate](Android-KMP-Mobile-UI-UX-Launcher-Gate.md)) |
| مطابقة بصرية 100% مع prototypes | فجوات UX ثانوية (صور، animations) |

### المراجع التفصيلية

- حالة صفحة بصفحة: [`Android-UI-UX-Modernization-Status.md`](Android-UI-UX-Modernization-Status.md)
- خطة Phase 05 (Train + Account): [`Android-KMP-Mobile-UI-UX-Phase-05-Page-By-Page-Modernization-Plan.md`](Android-KMP-Mobile-UI-UX-Phase-05-Page-By-Page-Modernization-Plan.md)
- Pre-05 (مغلقة): [`Android-KMP-Mobile-UI-UX-Phase-Pre-05-Stabilization-And-Debt-Closure-Plan.md`](Android-KMP-Mobile-UI-UX-Phase-Pre-05-Stabilization-And-Debt-Closure-Plan.md)

## الهدف

بناء واجهة موبايل احترافية وقابلة للتوسع لتطبيق `android-poc`، مع تجهيز المسار الطبيعي لتطبيق iOS عبر Kotlin Multiplatform. بما أن التطبيق ما زال تحت التطوير والتعديلات الكبيرة ستكون على فرع آخر، فالهدف ليس الحفاظ المفرط على الشكل الحالي، بل تنفيذ التحول الصحيح من البداية. التدرج هنا ليس خوفاً من التغيير، بل طريقة منظمة للتجربة، البناء، والتحقق بعد كل خطوة.

القرار التصميمي المعتمد:

- Material Design 3 كأساس تصميمي.
- Compose Multiplatform كمسار الواجهة الجديد القابل للمشاركة بين Android وiOS.
- View/XML يصبح legacy مؤقتاً وليس الأساس الذي نقيّد به التصميم الجديد.
- Design System واحد بمصدر حقيقة واحد للألوان، typography، spacing، radius، elevation، motion، والحالات.
- تبني أفكار Material 3 من foundations وcomponents، مع تخصيصها لهوية التطبيق بدل نسخ شكل Google الافتراضي.

## ملخص القرار التنفيذي

الخط الأساس الذي بدأنا منه كان Android فقط، View/XML، بموديول واحد `:app`. على فرع التحول الحالي لم يعد هذا هو الواقع الكامل: تم إنشاء KMP/Compose foundation وموديولات `shared`, `core:designsystem`, `feature:home`, `feature:explore`, و`feature:shell`، كما تم إثبات iOS من Xcode. لذلك أصبح القرار الاستراتيجي الآن هو توسيع البنية الجديدة صفحة بصفحة، مع استخدام التطبيق القديم كمصدر سلوك وبيانات وتجارب، وليس كقيد تصميمي دائم.

أفضل مسار هو:

1. تثبيت foundations الجديدة كقرار منتج، وليس كتجميل فوق النظام القديم.
2. إنشاء Design System مبني على Material 3 ويعمل في Compose Multiplatform.
3. إنشاء KMP structure مبكراً حتى يكون Android وiOS جزءاً من نفس التفكير.
4. نقل المنطق القابل للمشاركة إلى `commonMain`.
5. بناء الشاشات الجديدة بـ Compose Multiplatform، مع استخدام Android الحالي كمرجع سلوك فقط.
6. إنشاء iOS entry point مبكراً بعد أول feature قابلة للمشاركة.

هذا يتماشى مع دليل JetBrains الرسمي للهجرة التدريجية من Android إلى KMP، لكن تفسير التدرج هنا عملي: كل خطوة يجب أن تبني وتجرب وتثبت القرار التالي. لا نتمسك بـ View/XML أو single-module لأنهما موجودان حالياً فقط.

## حالة التنفيذ الفعلية (تحديث 2026-06-09)

تم تنفيذ Phase 01→04 بالكامل، **Phase Pre-05** (إغلاق ديون الأساس)، ودفعة **Phase 05** (Train + Reports + Session + **Account 10–14**). اكتمال التحقّقات الثلاثة (آخرها iOS render proof على Xcode) + iOS compile لـ shell بعد Account.

### مبنيّ ومتحقَّق منه

- **موديولات KMP:** `:shared`, `:core:designsystem`, `:core:network`, `:core:data`, `:core:resources`, `:feature:{explore,home,train,reports,library,account,shell}`.
- **طبقة بيانات مشتركة (Ktor):** `core:network` (`MovitMobileApi` + DTOs) + `core:data` (`MovitData` + sync repositories + `MovitPlatformBindings`) — تخدم التبويبات الأربعة + Report Detail + Workout Session + **Auth / training-profile / level-profile** على Android/iOS من نفس المصدر.
- **`commonMain` نظيف 100%** من أى `android.*` / `java.*` — تحقّق آلي.
- **UDF لكل feature:** `State / Event / Effect / ViewModel / Route / Screen`، باستخدام KMP `androidx.lifecycle.ViewModel` (لا Controller، لا Android-only ViewModel).
- **Design System كمصدر حقيقة واحد:** `MovitTheme` + tokens؛ الـ palette الوحيدة بها hex؛ Light/Dark؛ `error` مميّز عن `tertiary`؛ line-heights مناسبة للعربى.
- **iOS مفروض بالـ build:** كل الموديولات بها `iosArm64 + iosSimulatorArm64` (`iosX64` في `:shared` فقط)، وCI على macOS (`.github/workflows/movit-kmp-ios.yml`) يكمبّل `commonMain` لـ iOS عند كل push على `android-poc/**` — **أخضر**.
- **بيانات حقيقية عبر MovitData (بدون جسور Retrofit):** Explore · Home · Train · Reports · Session · Report Detail · **Account (login, profile, onboarding, level)** — `MovitDataInstall` على Android debug · `IosMovitPlatform` على iOS.
- **Release hygiene:** كل موديولات Movit `debugImplementation`؛ الـ launcher القديم لم يُلمس؛ `releaseRuntimeClasspath` نظيف.
- **Tests:** خضراء عبر كل الموديولات — أعداد KMP في [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) (`.\gradlew.bat docsStats`).
- **نِسب الصفحات:** مصدر وحيد — [`Page-Scorecards.md`](Page-Scorecards.md).

### iOS entry point — متحقَّق (render proof نجح) ✅

- **`iosApp/` يَرسُم فعلياً على iOS Simulator من Xcode.** `:feature:shell` يُصدّر framework ثابت (`MovitApp`) عبر `MainViewController()`، وiosApp (SwiftUI + XcodeGen) يستضيفه. تم التحقق على Mac (Xcode 26.5 / iPhone 17 / iOS 26.5): الـ Movit shell ظهر مع bottom navigation (Home/Train/Explore/Reports/Profile) ببيانات fake.
- التفاصيل الكاملة (البيئة + 6 مشاكل وحلولها) في `Android-KMP-iOS-Xcode-Mac-Validation-Report.md`.
- بهذا اكتملت التحقّقات الثلاثة: iOS **يكمبّل** (CI) + النمط **يتعمّم** (Home bridge) + Compose **يَرسُم** على iOS (هذا التقرير).

### ديون iOS — **Pre-05 / WS-E: P1/P2/P3 مُغلقة (2026-06-09)**

ظهرت أثناء render proof. **أُغلق في Pre-05/WS-E:**

- **(P1) deployment target:** ضُبط على iOS **16.0** (ليس 18.5 الإثباتي).
- **(P2) lifecycle-awareness:** كل routes تستخدم `collectAsStateWithLifecycle()` — صفر `collectAsState()` في feature routes (انظر [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md)).
- **(P3) ViewModelStoreOwner:** iOS entry point يوفّر store owner حقيقياً.

**ما زال مفتوحاً لإصدار iOS نهائي (خارج نطاق Pre-05):**
- **(P4 — معلومة تشغيل) بيئة Mac:** JDK 17 + Android SDK + `DEVELOPER_DIR` لـ Xcode الكامل لازمة لربط الـ framework؛ ثبّتها في shell profile (التفاصيل في تقرير التحقق).

### مؤجَّل عمداً (بـ trigger واضح)

- **Koin/DI:** ✅ **مُنفَّذ (Pre-05/WS-F)** — `MovitData` عبر `KoinApplication` بدون `GlobalContext` (آمن على iOS).
- **Ktor/serialization على iOS:** ✅ **مُنفَّذ** — Darwin + `IosMovitPlatform`؛ Account APIs على نفس المسار.
- **`TrainingActivity` / camera / MediaPipe / LiteRT / ONNX:** Phase 7، خلف حدود `expect/actual`. (لكن **المحرك العددى الخالص** غير المرتبط بالكاميرا يُنقل لـ `commonMain` كأول خطوة في حدود Phase 7.)

### تطابق الترقيم

مستندات التنفيذ (`Phase-01..05`) أدق حبيبيّة من المراحل المفاهيمية أدناه:

- `Phase 01 Foundation` = Phase 0 + Phase 1 هنا.
- `Phase 02/03/04` = feature pilots (Explore/Shell/Home) ضمن Phase 2 + Phase 4 هنا.
- **iOS entry point (Phase 6 هنا) سُحب مبكراً** لأن الأساس أثبت نفسه أسرع من المتوقع — اكتمل كـ render proof قبل توسيع الشاشات.
- `Phase 05` = Shared feature screens page-by-page: Train · Reports · Session · **Account (Auth/Profile/Onboarding/Assessment/Level)**.

## قراءة وضع legacy الأصلي

### الموجود في التطبيق القديم الذي ننقل منه

- `android-poc/app` ما زال يحتوي launcher وlegacy screens التي يستمر الرجوع إليها كسلوك مرجعي.
- `app/build.gradle.kts` يستخدم:
  - Kotlin Android plugin.
  - Android Gradle Plugin.
  - ViewBinding.
  - Material Components Views.
  - Retrofit/Gson/OkHttp.
  - CameraX.
  - MediaPipe + LiteRT + ONNX Runtime.
  - MPAndroidChart.
  - Coil 2.x للـ Views.
- `gradlew` و`gradlew.bat` موجودان حالياً داخل `android-poc`.
- الـ prototypes داخل `Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/` أصبحت مرجعاً بصرياً جيداً، خصوصاً `app.css?v=7` و`00-components.html`.

### ديون legacy واضحة من الفحص

- داخل legacy `:app` نفسه ما زالت أغلب الشاشات View/XML أو programmatic UI. موديولات KMP الجديدة موجودة بجانبه في فرع التحول ولا تعني أن legacy UI اختفى.
- ما زال هناك `findViewById` في ملفات محورية، أهمها:
  - `ProgramWorkoutActivity`: 60
  - `TrainFragment`: 34
  - `DebugActivity`: 27
  - `ProfileActivity`: 25
  - `TrainingActivity`: 18
  - `TrainingPreferenceDialogs`: 17
  - `ExploreFragment`: 13
- توجد ملفات كبيرة تحتاج تفكيك تدريجي:
  - `overlay/SkeletonOverlayView.kt`: 2121 سطر
  - `ui/debug/DebugActivity.kt`: 1832 سطر
  - `ui/programs/ProgramWorkoutActivity.kt`: 1736 سطر
  - `ui/train/TrainFragment.kt`: 1161 سطر
  - `ui/train/TrainingActivity.kt`: 1010 سطر
  - `ui/training/TrainingViewModel.kt`: 973 سطر
- توجد ألوان raw/legacy في XML/Kotlin، خصوصاً التدريب، التقارير، التقييم، وdrawables.
- بعض UI ما زال programmatic مثل `LevelProfileActivity` وأجزاء من assessment/report/custom views.

## المبادئ المعمارية

### 1. التحول الصحيح أهم من الحفاظ على القديم

بما أن التطبيق تحت التطوير، نسمح بتغييرات جذرية في البنية والواجهة عندما تكون هي الطريق الصحيح. لا ننقل كل شيء دفعة واحدة فقط لأننا نحتاج build وتجربة مستمرة، وليس لأننا نريد حماية الشكل الحالي. أي شاشة legacy يمكن إعادة بنائها بالكامل إذا كان ذلك أنظف وأقرب للـ KMP/iOS.

### 2. KMP يبدأ من المنطق وليس الكاميرا

الكاميرا، MediaPipe، LiteRT، ONNX، permissions، وملفات النظام Platform-specific بطبيعتها. لا نبدأ بها. نبدأ بما يمكن مشاركته بأمان:

- models
- DTOs
- validation rules
- scoring/state machines إن كانت pure Kotlin
- repositories contracts
- use cases
- formatting
- settings abstractions
- UI state models

### 3. Design System قبل feature screens

أي شاشة جديدة أو منقولة إلى Compose يجب أن تستخدم `MovitTheme` ومكونات النظام فقط. لا توجد ألوان مباشرة أو typography عشوائية داخل feature screen.

### 4. Material 3 كأسلوب تفكير لا كقالب جاهز

نستخدم Material 3 foundations لتحديد أدوار اللون، typography، shape، motion، elevation، layout، states، والتفاعل. ونستخدم components كنقطة بداية لسلوك المكونات، لكن المظهر النهائي يجب أن يعبر عن هوية تطبيق health/fitness: هادئ، نظيف، light-first، ومرتبط بالتمرين والتحليل، وليس نسخة عامة من تطبيقات Google.

### 5. Feature-first مع core واضح

نقسم المشروع حول features، لكن بدون تكرار data/network/domain. كل feature تملك UI/state/navigation الخاصة بها، والـ core يملك primitives المشتركة.

### 6. Unidirectional Data Flow

كل شاشة Compose يجب أن تكون:

- `UiState`
- `UiEvent` أو `Intent`
- `UiEffect` للأحداث لمرة واحدة مثل navigation/snackbar
- `ViewModel` exposes `StateFlow<UiState>`
- composables pure قدر الإمكان

## الهيكلة المستهدفة

### المرحلة الانتقالية داخل Android الحالي

نحافظ مؤقتاً على:

```text
android-poc/
  app/
```

ثم نضيف تدريجياً:

```text
android-poc/
  app/                       # Android shell الحالي، يبقى launcher وlegacy screens
  shared/                    # KMP shared logic كبداية
  shared-ui/                 # Compose Multiplatform UI لاحقاً
  build-logic/               # Gradle convention plugins لاحقاً
  gradle/libs.versions.toml  # Version catalog
```

### الهيكلة النهائية المقترحة

```text
android-poc/
  androidApp/                # Android entry point، قد يكون rename من app لاحقاً
  iosApp/                    # Xcode/Swift entry point
  shared/
    src/
      commonMain/
      commonTest/
      androidMain/
      androidUnitTest/
      iosMain/
      iosTest/
  core/
    model/                   # KMP
    common/                  # KMP utils, Result, errors, dispatchers abstractions
    network/                 # KMP Ktor client + serializers
    data/                    # KMP repositories implementations where possible
    domain/                  # KMP use cases
    designsystem/            # Compose Multiplatform Material 3 theme/tokens
    ui/                      # Shared UI components
    testing/                 # common test fixtures
  feature/
    home/                    # Compose Multiplatform when migrated
    train/
    explore/
    reports/
    programs/
    assessment/
    profile/
```

ملاحظة: لا نحتاج إنشاء كل الموديولات من اليوم الأول. نبدأ بـ `shared` أو `core:model/domain` فقط، ثم نفصل feature modules عندما يبدأ الحجم أو الـ ownership يبرر ذلك.

## ترتيب الملفات داخل كل feature

مثال `feature/explore`:

```text
feature/explore/src/commonMain/kotlin/com/movit/feature/explore/
  ExploreRoute.kt            # navigation boundary
  ExploreScreen.kt           # stateless screen composable
  ExploreViewModel.kt        # state holder
  ExploreUiState.kt
  ExploreEvent.kt
  ExploreEffect.kt
  ExploreModels.kt           # UI-only models
  components/
    ExerciseCard.kt
    FilterBar.kt
    SearchHeader.kt
  preview/
    ExplorePreviewData.kt
```

قاعدة مهمة: `Screen` لا يعرف network أو storage. يتعامل فقط مع `UiState` وcallbacks.

## Design System

### مصدر الحقيقة

ننقل ما تم تثبيته في prototypes إلى Design System رسمي:

- Primary / Aqua: `#8ECFE3`
- Secondary / Lime: `#C4D489`
- Neutral / Ink: `#282828`
- Action / Coral: `#E76D46`
- Mist / Background support: `#D8DCDF`

### Material 3 mapping

```text
primary              = Aqua
onPrimary            = Ink
primaryContainer     = Aqua tint
onPrimaryContainer   = Ink
secondary            = Lime
onSecondary          = Ink
secondaryContainer   = Lime tint
tertiary             = Coral
onTertiary           = White
background           = Light background / Dark background
surface              = Card surface
surfaceVariant       = Soft surface
outline              = Border
error                = Semantic error, derived from Coral but not identical blindly
```

### Compose files

```text
core/designsystem/src/commonMain/kotlin/com/movit/designsystem/
  MovitTheme.kt
  MovitColorScheme.kt
  MovitTypography.kt
  MovitShapes.kt
  MovitSpacing.kt
  MovitElevation.kt
  MovitMotion.kt
  MovitIcons.kt
  components/
    MovitScaffold.kt
    MovitTopBar.kt
    MovitCard.kt
    MovitButton.kt
    MovitIconButton.kt
    MovitChip.kt
    MovitMetricTile.kt
    MovitEmptyState.kt
    MovitSectionHeader.kt
    MovitActionDock.kt
```

### قواعد التصميم

- الالتزام بـ Material 3 color roles وليس استخدام hex داخل الشاشات.
- Light-first، مع Dark mode مكافئ وليس reverse مباشر.
- cards كبيرة ومنظمة، لكن لا تتحول كل الصفحة إلى cards متداخلة.
- CTA واحد واضح في كل context.
- دعم Arabic/English وRTL منذ أول مكون Compose.
- دعم font scaling حتى 200%.
- كل icon button له content description أو يتم تعليمه decorative.
- الـ charts داخل Compose تكون Canvas خفيفة ومقروءة، ولا نعتمد على MPAndroidChart في الشاشات الجديدة المشتركة.

## تبني Material 3 عملياً

### Foundations المعتمدة

نستخدم Material 3 foundations كمرجع تصميم رئيسي، خصوصاً:

- Color system: أدوار semantic مثل `primary`, `secondary`, `tertiary`, `surface`, `surfaceVariant`, `outline`, `error` بدلاً من أسماء ألوان خام.
- Typography: الاعتماد على scale واضح يقابل أدوار Material 3 مثل display/headline/title/body/label.
- Shape: shape scale موحد للمكونات، مع radius أكبر للكروت والـ CTAs بما يناسب النمط الحالي.
- Layout: spacing rhythm، touch targets، responsive/adaptive behavior.
- Motion: transitions قصيرة وواضحة، motion لخدمة feedback وليس decoration.
- Elevation: tonal elevation وحدود ناعمة بدلاً من shadows ثقيلة.
- Interaction states: pressed, focused, disabled, selected, error, loading.
- Accessibility: contrast، font scaling، semantic labels، وminimum touch target.

### Components المعتمدة كنواة

مكونات Material 3 التالية تصبح أساس مكتبة `Movit`، لكن بأسماء وهوية التطبيق:

- Buttons:
  - `MovitButton.Filled`
  - `MovitButton.Tonal`
  - `MovitButton.Outlined`
  - `MovitButton.Text`
  - `MovitIconButton`
- Cards:
  - `MovitCard.Filled`
  - `MovitCard.Outlined`
  - `MovitMetricCard`
  - `MovitMediaCard`
- Navigation:
  - `MovitNavigationBar`
  - `MovitTopAppBar`
  - `MovitTabs`
  - `MovitAdaptiveNavigation` لاحقاً للتابلت/foldables.
- Selection:
  - `MovitFilterChip`
  - `MovitAssistChip`
  - `MovitSegmentedControl`
  - `MovitSwitch`
  - `MovitCheckbox`
- Feedback:
  - `MovitSnackbar`
  - `MovitDialog`
  - `MovitBottomSheet`
  - `MovitProgressIndicator`
  - `MovitEmptyState`
- Input:
  - `MovitTextField`
  - `MovitSearchBar`
  - `MovitSlider`
- Training-specific:
  - `MovitExerciseCard`
  - `MovitWorkoutCard`
  - `MovitProgramHero`
  - `MovitMetricRow`
  - `MovitActionDock`
  - `MovitTimeline`
  - `MovitScoreRing`

### قواعد تخصيص Material 3

- لا نستخدم default Material component مباشرة داخل feature screens إلا لو مغلف داخل `Movit*`.
- أي component جديد يبدأ من Material 3 behavior ثم يأخذ visual identity من prototypes.
- dynamic color من Android يمكن دعمه كاختيار لاحق، لكن brand palette هي الافتراضي حتى لا تضيع هوية التطبيق.
- اللون لا يشرح الحالة وحده؛ يجب دعم icon/text/semantic state.
- المكونات لا تحتوي business logic. أي logic يبقى في ViewModel/use case.

## Pattern الشاشة القياسي

```kotlin
data class ExploreUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val selectedFilter: ExploreFilter = ExploreFilter.All,
    val items: List<ExploreItemUi> = emptyList(),
    val message: String? = null
)

sealed interface ExploreEvent {
    data class QueryChanged(val value: String) : ExploreEvent
    data class FilterSelected(val filter: ExploreFilter) : ExploreEvent
    data class ItemClicked(val id: String) : ExploreEvent
}

sealed interface ExploreEffect {
    data class NavigateToExercise(val id: String) : ExploreEffect
}
```

كل `ViewModel`:

- يملك `MutableStateFlow`.
- يستدعي use cases فقط.
- لا يستدعي Retrofit/ApiClient مباشرة.
- لا يعرف Android `Context` إلا عبر abstraction مبرر.

## المكتبات المقترحة

### UI

- Compose Multiplatform للـ UI المشترك.
- Material 3 عبر Compose.
- Navigation Compose Multiplatform للـ routing المشترك.
- Multiplatform ViewModel/Lifecycle عند نقل الشاشات المشتركة.

### DI

- Koin للموديولات المشتركة لأنه يدعم Android وiOS وCompose Multiplatform.
- لا نستخدم Hilt كقرار استراتيجي جديد لأنه Android-only، ويمكن أن يعرقل KMP.
- إذا بقيت شاشات Android legacy تحتاج factories بسيطة، نغلفها مؤقتاً داخل Android shell.

### Networking

- Ktor Client + Content Negotiation + kotlinx.serialization في `commonMain`.
- Android engine: OkHttp أو Android حسب القرار النهائي.
- iOS engine: Darwin.
- Retrofit/Gson يبقيان فقط في Android legacy إلى حين نقل الـ APIs.

**قرار DTO (Pre-06.2 / WS-5):** `core:network` مع `kotlinx.serialization` هو **مصدر الحقيقة الوحيد** لعقود JSON المشتركة بين Android وiOS. نماذج Gson في `:app` legacy تبقى مؤقتاً لمسارات الكاميرا/التدريب القديمة، لكن أي API جديد أو مُعاد توصيله عبر KMP يُعرَّف مرة واحدة في `MovitMobileApi` + DTOs المشتركة؛ الـ legacy يستهلكها تدريجياً عبر جسور strangler (مثل `*ApiBridge`) حتى يُحذف التكرار. **لا** نُولِّد DTOs من مصدر ثالث مشترك في هذه المرحلة — التوليد المشترك يُؤجَّل حتى يثبت استقرار العقود؛ الاستثناء الوحيد لاحقاً هو حقول backend-only لا تصل للعميل.

### Storage

- Preferences/settings: DataStore KMP أو abstraction فوق platform storage.
- Structured cache: SQLDelight إذا احتجنا SQL واضح ومشترك.
- Room KMP خيار صالح لو الفريق يريد البقاء داخل Jetpack ecosystem، لكنه يحتاج قرار منفصل حسب حجم الـ cache الحالي.
- التخزين الحالي داخل `storage/*` يتم تغليفه بعقود repositories قبل نقله.

### Images

- Coil 2 يبقى للـ Views الحالية.
- Coil 3 أو بديل Compose Multiplatform عند بناء shared Compose UI.

### Tests

- `kotlin-test` للموديولات المشتركة.
- Coroutines test للـ ViewModels/use cases.
- Screenshot/visual checks على Android للشاشات المهمة.
- iOS smoke tests بعد إضافة `iosApp`.

## خطة الهجرة

> حالة التنفيذ (انظر "حالة التنفيذ الفعلية" و«ملخص تنفيذي للمدير» أعلاه): Phase 0→4 ✅ · Pre-05 ✅ · Phase 6 (iOS entry) ✅ render proof. **Phase 5 🔄 جارٍ** — مكتمل: Train · Reports · Report Detail · Session · **Account 10–14** (`:feature:account`). متبقّي Phase 05: فجوات UX (Train week nav، Library صور، Program flow 15، Workout flow 16، …). Phase 7 (camera/ML) ⬜ مؤجّل.

### Phase 0 - تأسيس فرع التحول

الهدف: تجهيز فرع جديد للتحول الصحيح، بحيث نستطيع تغيير البنية والواجهة بحرية مع الحفاظ على build/test checkpoints.

- إضافة `gradle/libs.versions.toml`.
- نقل versions من `app/build.gradle.kts` إلى version catalog.
- إضافة convention plugins لاحقاً في `build-logic`.
- تحديد package/module naming النهائي قبل إنشاء موديولات كثيرة.
- اعتماد Compose Multiplatform وMaterial 3 كقرار مبكر، لا كمرحلة مؤجلة.
- توثيق compatibility matrix:
  - Kotlin
  - Compose Multiplatform
  - Android Gradle Plugin
  - Gradle
  - Xcode
- تشغيل:
  - `android-poc/gradlew.bat :app:assembleDebug`
  - `android-poc/gradlew.bat :app:testDebugUnitTest`
- تحديث مستند الحالة لأن `gradlew` موجود الآن.

### Phase 1 - Design System + KMP skeleton

الهدف: بناء الهيكل الصحيح قبل نقل الشاشات. التطبيق الحالي يظل مرجعاً للسلوك، لكن البنية الجديدة هي المصدر الذي سنبني عليه Android وiOS.

الأولويات:

1. إنشاء `shared` أو `core` كبداية KMP.
2. إنشاء `core/designsystem` أو `shared-ui` للـ Material 3 theme والمكونات.
3. إضافة Android Compose host مبكر.
4. إضافة iOS shell مبكر بعد أول شاشة بسيطة.
5. بناء component catalog حقيقي داخل Compose، مستوحى من `00-components.html`.
6. اختيار أول feature pilot.

قواعد كل شاشة:

- لا raw colors.
- لا hardcoded text sizes إلا عبر tokens.
- لا ضرورة للحفاظ على IDs إذا كانت الشاشة ستعاد بناؤها في Compose داخل الفرع الجديد.
- دعم Light/Dark/System.
- مراجعة RTL.
- فصل business logic عن UI أثناء إعادة البناء بدلاً من نقلها كما هي.

### Phase 2 - Feature pilots

الهدف: إثبات أن البنية الجديدة تعمل end-to-end على Android، ثم iOS عند أول فرصة عملية.

- تفعيل Compose في Android host.
- إنشاء `MovitTheme` في shared UI، مع bridge مؤقت لـ Android إذا لزم.
- إنشاء مكونات Compose الأساسية.
- بناء أول شاشة feature كاملة بـ `UiState` و`ViewModel` وnavigation.

أفضل مرشحين:

1. `Level Profile`: لأن UI programmatic حالياً ويحتاج تنظيف.
2. `Explore`: لأنه catalog/cards/filters ويشبه prototypes.
3. `Home`: لأن تصميمها واضح ومناسب لإثبات dashboard patterns.

لا نبدأ بـ `TrainingActivity` لأن الكاميرا والـ overlay والـ ML تزيد المخاطر.

### Phase 3 - Shared logic + data

الهدف: بناء أساس iOS بدون لمس UI المعقد أولاً.

- إنشاء `shared` KMP module.
- نقل pure models.
- نقل result/error abstractions.
- نقل use cases المستقلة.
- نقل formatting والتواريخ إلى `kotlinx-datetime`.
- تحويل DTOs الجديدة إلى `kotlinx.serialization`.
- بناء Ktor API client جديد بجانب Retrofit القديم.
- نقل repository واحد بسيط كـ pilot، مثل `ExploreRepository` أو `HomeRepository`.

### Phase 4 - Shared Design System

الهدف: `MovitTheme` يعمل من `commonMain`.

- نقل theme إلى `core/designsystem`.
- استخدام `composeResources` بدلاً من Android `res` للمشترك.
- إنشاء typography/fonts/resources عبر Compose Multiplatform resources.
- إنشاء previews للحالات:
  - Light
  - Dark
  - Arabic/RTL
  - Empty/loading/error

### Phase 5 - Shared feature screens

الواقع الحالي (2026-06-09): التبويبات الرئيسية + Session + Report Detail + **تدفقات الحساب والتقييم** تعمل في KMP عبر `MovitData` على Android debug وiOS (عند credentials).

| # | الصفحة | الحالة |
|---|--------|--------|
| 1–11 | كل صفحات Phase 05 | ✅ منفَّذة جزئياً — **النِسب في [`Page-Scorecards.md`](Page-Scorecards.md) فقط** |
| 12 | Training session/camera | ⬜ Phase 7 |

السبب في الترتيب: lists/cards/state أولاً؛ camera/overlay/ML لاحقاً.

### Phase 6 - iOS entry point (تم سحبه مبكراً واكتمل كـ render proof)

الهدف: إثبات المنتج على iOS مبكراً بدون انتظار كل Android.

- **تم:** إضافة `iosApp`.
- **تم:** عرض shell بـ Compose Multiplatform من iOS عبر `MovitApp.framework`.
- **تم:** ربط navigation الأساسي بصرياً على Simulator.
- **تم:** إثبات XcodeGen + Xcode build/run على iPhone 17 Simulator.
- **لم يتم بعد:** تشغيل shared DI؛ ما زال مؤجلاً حتى Ktor/repository مشترك.
- **جزئي:** Auth/Account UI على iOS؛ حفظ جلسة عبر `UserDefaults` عند `persistAuthSession` — **لا** تدفق Google native بعد.
- **لم يتم بعد:** platform adapters:
  - secure storage
  - network engine
  - file/cache paths
  - locale/direction
  - haptics
  - permissions

بوابة هذه المرحلة قبل الإنتاج: إغلاق P1/P2/P3 المذكورة أعلاه أو توثيق بديل مقبول لها. إلى أن يحدث ذلك، iOS صالح كـ render proof ومسار تطوير، وليس كأرضية إصدار نهائي.

### Phase 7 - Camera/ML multiplatform boundary

الهدف: جعل التدريب قابلاً للتوسع بدون إجبار iOS على نفس Android implementation.

التقسيم المقترح:

```text
commonMain:
  TrainingEngine
  RepCounter
  PhaseStateMachine
  ScoreCalculator
  FeedbackPolicy
  PoseFrame
  Landmark
  JointAngles
  TrainingSessionState

androidMain:
  CameraXFrameSource
  MediaPipePoseDetector
  LiteRtClassifier
  AndroidAudioFeedback

iosMain:
  IosCameraFrameSource
  IosPoseDetectorAdapter
  IosAudioFeedback
```

قاعدة: common code يرى `PoseFrame` و`Landmark` فقط، ولا يرى CameraX أو MediaPipe classes مباشرة.

## ترتيب أول 90 يوم

### الأسبوع 1-2

- إنشاء فرع التحول.
- تثبيت version catalog.
- تشغيل build/test من Gradle wrapper.
- توثيق compatibility matrix.
- إنشاء KMP skeleton.
- إنشاء Compose/Material 3 design system skeleton.
- تحويل `00-components.html` إلى component backlog داخل `Movit` components.

### الأسبوع 3-4

- إضافة Compose host إلى Android.
- بناء `MovitTheme` Compose مطابق للـ palette والـ Material 3 roles.
- بناء مكونات:
  - `MovitCard`
  - `MovitButton`
  - `MovitMetricTile`
  - `MovitSectionHeader`
  - `MovitEmptyState`
  - `MovitFilterChip`
- تنفيذ أول feature pilot: `Explore` أو `Level Profile`.

### الشهر 2

- توسيع `shared`.
- نقل أول models/use cases/repository contracts.
- إدخال Koin في shared.
- إدخال Ktor/kotlinx.serialization لميزة واحدة.
- كتابة tests مشتركة.
- إضافة iOS shell إذا كان أول feature أصبح قابلاً للعرض.

### الشهر 3

- نقل أول feature screen إلى `commonMain`.
- تشغيلها على Android وiOS.
- نقل feature ثانية من prototypes إلى Compose.
- مراجعة RTL/theme/accessibility على Android وiOS.

## Definition of Done لأي شاشة

- تستخدم Design System فقط.
- لا توجد raw colors أو sizes خارج whitelist.
- تعمل في Light/Dark/System.
- تعمل بالعربية والإنجليزية وRTL/LTR.
- loading/empty/error states موجودة.
- لا يوجد business logic داخل Composable/Activity.
- ViewModel قابل للاختبار.
- build ناجح.
- مراجعة بصرية على small phone وstandard phone.
- لا يوجد تداخل نصوص عند font scale كبير.
- navigation/back behavior واضح.

## قرارات تؤخذ مبكراً في فرع التحول

- Compose Multiplatform هو مسار UI الجديد.
- Material 3 هو أساس الـ Design System.
- KMP structure يبدأ مبكراً حتى لو بقيت بعض الشاشات Android-only مؤقتاً.
- الشاشات الجديدة في KMP تبنى بـ `Movit*` components وليس XML. أسماء `Pose*` أو `WayToFix*` تبقى فقط في legacy Android/prototype history إلى أن تُستبدل تدريجياً.
- التطبيق الحالي مرجع للسلوك والـ APIs، وليس قيداً على UI أو الملفات.
- (مُنفَّذ) KMP `androidx.lifecycle.ViewModel` كحامل حالة مشترك، بدل Controller أو Android-only ViewModel.
- (مُنفَّذ) iOS targets + CI على macOS من البداية — "iOS-ready بالـ build وليس بالاتفاق".
- (مُنفَّذ) ربط الـ features بالـ APIs القديمة عبر debug bridge (app يحقن fetcher)، بدل إجبار الـ feature على معرفة Retrofit.
- (مُنفَّذ) الهوية الجديدة `Movit*` بدل `Pose*` في الموديولات الجديدة.

## قرارات تؤجل حتى تتضح حدودها

- لا نقرر مشاركة camera UI قبل تجربة iOS فعلية.
- لا نقرر Room vs SQLDelight إلا بعد حصر احتياج offline cache.
- Retrofit/Gson يبقيان في legacy `:app` فقط. **جسور الـ Retrofit داخل موديولات Movit الجديدة (`*ApiBridge`/`Remote*Repository`) تُحذف في Pre-05/WS-A** — أثبت Ktor نفسه end-to-end على كل التبويبات.
- لا نعيد تسمية package/applicationId إلا عندما يكون الاسم التجاري النهائي واضحاً.
- لا ننقل `TrainingActivity` إلى Compose قبل فصل engine/state/adapters.
- (محدَّث 2026-06-09) **Koin/DI لم يعد مؤجّلاً** — الـ trigger تحقّق؛ يُعتمد في Pre-05/WS-F.
- (محدَّث 2026-06-09) **Ktor على iOS مُنفَّذ** — iOS على الطبقة المشتركة (Darwin)، لا fake عند توفّر credentials.

## قائمة مصادر تم الاعتماد عليها

- [Material Design 3](https://m3.material.io/)
- [Material 3 foundations](https://m3.material.io/foundations)
- [Material 3 components](https://m3.material.io/components)
- [Android app architecture](https://developer.android.com/topic/architecture)
- [Android modularization guide](https://developer.android.com/topic/modularization)
- [Android Kotlin Multiplatform guidance](https://developer.android.com/kotlin/multiplatform)
- [Kotlin Multiplatform project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html)
- [Compose Multiplatform and Jetpack Compose relationship](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html)
- [Migrating a Jetpack Compose app to Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/migrate-from-android.html)
- [Compose Multiplatform compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Compose Multiplatform navigation and routing](https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html)
- [Ktor client supported platforms](https://ktor.io/docs/client-supported-platforms.html)
- [Koin Kotlin Multiplatform setup](https://insert-koin.io/docs/reference/koin-core/kmp-setup/)
- [KMP with Ktor and SQLDelight tutorial](https://kotlinlang.org/docs/multiplatform/multiplatform-ktor-sqldelight.html)
