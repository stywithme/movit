# Android / KMP Mobile UI/UX Professional Plan

آخر تحديث: 2026-06-08

## الهدف

بناء واجهة موبايل احترافية وقابلة للتوسع لتطبيق `android-poc`، مع تجهيز المسار الطبيعي لتطبيق iOS عبر Kotlin Multiplatform. الخطة لا تفترض إعادة بناء كاملة مرة واحدة. المسار الصحيح هو تحديث Android الحالي تدريجياً، ثم نقل المنطق والـ UI إلى KMP/Compose Multiplatform شاشة بشاشة.

القرار التصميمي المعتمد:

- Material Design 3 كأساس تصميمي.
- Compose Multiplatform كمسار الواجهة الجديد القابل للمشاركة بين Android وiOS.
- الإبقاء على View/XML مؤقتاً فقط للشاشات الحالية إلى أن يتم نقلها.
- Design System واحد بمصدر حقيقة واحد للألوان، typography، spacing، radius، elevation، motion، والحالات.

## ملخص القرار التنفيذي

المشروع الحالي Android فقط، View/XML، بموديول واحد `:app`. لا يوجد Compose أو KMP حالياً. لذلك أفضل مسار هو:

1. تثبيت foundations داخل Android الحالي حتى لا يتدهور الـ UI أثناء التطوير.
2. إنشاء طبقة Design System قابلة للترجمة إلى Compose.
3. إضافة KMP تدريجياً، بدءاً من models/domain/data وليس الكاميرا أو الـ ML.
4. إضافة Compose في Android كممر انتقال.
5. نقل الشاشات المناسبة إلى Compose Multiplatform واحدة تلو الأخرى.
6. إنشاء iOS entry point بعد أن تصبح أول مجموعة features تعمل من `commonMain`.

هذا يتماشى مع دليل JetBrains الرسمي للهجرة التدريجية من Android إلى KMP، حيث يتم نقل المكتبات والمنطق أولاً، ثم UI screen-by-screen، مع إبقاء التطبيق في حالة build ناجحة بعد كل خطوة.

## قراءة الوضع الحالي

### الموجود الآن

- `android-poc` مشروع Android single-module.
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

### ديون واضحة من الفحص

- لا يوجد Compose/KMP حالياً.
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

### 1. UI حديث لكن غير متهور

لا ننقل كل شيء إلى Compose مرة واحدة. أي شاشة تعمل الآن تظل تعمل، ويتم نقلها عندما تكون لها قيمة واضحة أو عندما يكون استمرارها في XML مكلفاً.

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

أي شاشة جديدة أو منقولة إلى Compose يجب أن تستخدم `PoseTheme` ومكونات النظام فقط. لا توجد ألوان مباشرة أو typography عشوائية داخل feature screen.

### 4. Feature-first مع core واضح

نقسم المشروع حول features، لكن بدون تكرار data/network/domain. كل feature تملك UI/state/navigation الخاصة بها، والـ core يملك primitives المشتركة.

### 5. Unidirectional Data Flow

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
feature/explore/src/commonMain/kotlin/com/waytofix/feature/explore/
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
core/designsystem/src/commonMain/kotlin/com/waytofix/designsystem/
  PoseTheme.kt
  PoseColorScheme.kt
  PoseTypography.kt
  PoseShapes.kt
  PoseSpacing.kt
  PoseElevation.kt
  PoseMotion.kt
  PoseIcons.kt
  components/
    PoseScaffold.kt
    PoseTopBar.kt
    PoseCard.kt
    PoseButton.kt
    PoseIconButton.kt
    PoseChip.kt
    PoseMetricTile.kt
    PoseEmptyState.kt
    PoseSectionHeader.kt
    PoseActionDock.kt
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

### Phase 0 - تثبيت أساس التطوير

الهدف: منع الفوضى قبل KMP.

- إضافة `gradle/libs.versions.toml`.
- نقل versions من `app/build.gradle.kts` إلى version catalog.
- إضافة convention plugins لاحقاً في `build-logic`.
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

### Phase 1 - Android UI stabilization

الهدف: التطبيق الحالي يظل قابل للاستخدام أثناء التحول.

الأولويات:

1. مراجعة build وvisual لـ `Home`.
2. نقل `Train` من prototype إلى Android.
3. نقل `Explore`.
4. نقل `Reports`.
5. تنظيف `Level Profile` لأنه programmatic ومناسب لاحقاً كـ Compose pilot.
6. تأجيل `TrainingActivity` وcamera-heavy screens إلى أن يثبت الـ design system.

قواعد كل شاشة:

- لا raw colors.
- لا hardcoded text sizes إلا عبر styles/tokens.
- الحفاظ على IDs عند تعديل XML.
- دعم Light/Dark/System.
- مراجعة RTL.
- عدم تغيير business logic أثناء موجة UI إلا عند الضرورة.

### Phase 2 - Compose داخل Android

الهدف: تعلم migration داخل Android قبل iOS.

- تفعيل Compose في `:app`.
- إنشاء `PoseTheme` في Android أولاً، مطابق لـ XML tokens.
- إنشاء مكونات Compose الأساسية.
- بناء أول شاشة Compose منخفضة المخاطر.

أفضل مرشحين:

1. `Level Profile`: لأن UI programmatic حالياً ويحتاج تنظيف.
2. `Explore`: لأنه catalog/cards/filters ويشبه prototypes.

لا نبدأ بـ `TrainingActivity` لأن الكاميرا والـ overlay والـ ML تزيد المخاطر.

### Phase 3 - KMP shared logic

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

الهدف: `PoseTheme` يعمل من `commonMain`.

- نقل theme إلى `core/designsystem`.
- استخدام `composeResources` بدلاً من Android `res` للمشترك.
- إنشاء typography/fonts/resources عبر Compose Multiplatform resources.
- إنشاء previews للحالات:
  - Light
  - Dark
  - Arabic/RTL
  - Empty/loading/error

### Phase 5 - Shared feature screens

الترتيب المقترح:

1. `Explore`
2. `Level Profile`
3. `Reports Overview`
4. `Home`
5. `Train dashboard`
6. `Program plan/day`
7. `Assessment`
8. `Training session/camera`

السبب: نبدأ بالشاشات التي تعتمد على lists/cards/state ونؤجل الشاشات ذات camera/overlay/ML.

### Phase 6 - iOS entry point

الهدف: إثبات المنتج على iOS مبكراً بدون انتظار كل Android.

- إضافة `iosApp`.
- تشغيل shared DI.
- عرض أول شاشة Compose Multiplatform من iOS.
- ربط navigation الأساسي.
- ربط auth/session لاحقاً.
- تنفيذ platform adapters:
  - secure storage
  - network engine
  - file/cache paths
  - locale/direction
  - haptics
  - permissions

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

- تثبيت version catalog.
- تشغيل build/test من Gradle wrapper.
- توثيق compatibility matrix.
- مراجعة Home بصرياً.
- تحويل Train أو Explore على Android الحالي حسب الأولوية التجارية.

### الأسبوع 3-4

- إضافة Compose إلى `:app`.
- بناء `PoseTheme` Compose مطابق للـ XML.
- بناء مكونات:
  - `PoseCard`
  - `PoseButton`
  - `PoseMetricTile`
  - `PoseSectionHeader`
  - `PoseEmptyState`
  - `PoseFilterChip`
- تنفيذ Compose pilot.

### الشهر 2

- إنشاء `shared`.
- نقل أول models/use cases/repository contracts.
- إدخال Koin في shared.
- إدخال Ktor/kotlinx.serialization لميزة واحدة.
- كتابة tests مشتركة.

### الشهر 3

- نقل أول feature screen إلى `commonMain`.
- إضافة `iosApp`.
- تشغيل الشاشة الأولى على iOS.
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

## قرارات لا تؤخذ الآن

- لا نقرر مشاركة camera UI قبل تجربة iOS فعلية.
- لا نقرر Room vs SQLDelight إلا بعد حصر احتياج offline cache.
- لا نحذف Retrofit/Gson من Android دفعة واحدة.
- لا نعيد تسمية package/applicationId أثناء UI/KMP migration.
- لا ننقل `TrainingActivity` إلى Compose قبل فصل engine/state/adapters.

## قائمة مصادر تم الاعتماد عليها

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

