# Android / KMP Mobile UI/UX - Phase 05 Page-by-Page Modernization + Train Page Plan

آخر تحديث: 2026-06-08 (post-Phase-04 مكتملة: ViewModels + iOS targets + API bridge + CI أخضر + Home bridge + iOS render proof ناجح؛ Phase 05 يبدأ بـ Train)

## تعريف المرحلة

هذه المرحلة تنقلنا من بناء features منفصلة (`Home`, `Explore`, `Shell`) إلى طريقة عمل منظمة لتحديث التطبيق كله صفحة بصفحة. الهدف ليس فقط بناء صفحة جديدة، بل تثبيت منهج احترافي يراجع الصفحة الحالية، يفهم سلوكها الحقيقي، يحدد تجربة المستخدم المثالية، يضع تصوراً بصرياً مطابقاً لهوية `Movit` وMaterial 3، ثم ينفذها ويغلقها قبل الانتقال للصفحة التالية.

Phase 05 تتكون من جزئين:

```text
1. Page Modernization Playbook
2. Train Page Pilot
```

السبب:

- `Home` و`Explore` أصبحا موجودين داخل shell.
- `Train`, `Reports`, و`Profile` ما زالت placeholders.
- `Train` هي أهم صفحة تالية لأنها مدخل التدريب اليومي، لكنها يجب ألا تدخل بعد في camera/session/ML.
- نحتاج نظاماً ثابتاً لاختيار، تحليل، تصميم، وتنفيذ كل صفحة بدلاً من تنفيذ الشاشات بالحدس.

## المراجع التصميمية

المراجع الأساسية:

- Material 3 Foundations: `https://m3.material.io/foundations`
- Material 3 Layout Overview: `https://m3.material.io/foundations/layout/layout-overview/overview`
- Material 3 Window Size Classes: `https://m3.material.io/foundations/layout/applying-layout/window-size-classes`
- Material 3 Parts of Layout: `https://m3.material.io/foundations/layout/understanding-layout/parts-of-layout`
- Android adaptive apps guidance: `https://developer.android.com/develop/ui/compose/building-adaptive-apps`
- Android adaptive Material guidance codelab: `https://developer.android.com/codelabs/adaptive-material-guidance`

مبادئ Material 3 التي سنطبقها:

- التصميم يعتمد على app window وليس افتراض مقاس جهاز ثابت.
- التخطيط يقسم الشاشة إلى system bars, navigation area, body/content.
- compact width يستخدم bottom navigation غالباً.
- medium/expanded width يستعد لـ navigation rail أو multi-pane layouts.
- لا نقفل layout على أبعاد ثابتة؛ نستخدم constraints, responsive spacing, flexible columns.
- نستخدم canonical layouts عندما تظهر الحاجة مثل list/detail أو supporting pane.

## قرارات Phase 05

### 1. العمل صفحة صفحة

لا يتم تنفيذ أكثر من صفحة واحدة في نفس المرحلة إلا إذا كانت صفحة صغيرة جداً ومتصلة مباشرة.

لكل صفحة:

```text
Review current page
Extract behavior and content
Define target UX
Define visual/layout spec
Implement
Verify
Review gate
Move to next page
```

### 2. أول صفحة في هذا المنهج

أول صفحة يتم تطبيق المنهج عليها:

```text
Train
```

لكن المقصود في Phase 05:

```text
Train Dashboard / Today Training Overview
```

وليس:

```text
Live Training Session
Camera Overlay
Pose Detection
ML Evaluation
```

### 3. Shell status

يبقى:

```text
MovitShellPilotActivity debug-only
```

لا يتم نقله إلى launcher production في Phase 05 إلا إذا طلبنا قراراً صريحاً قبل التنفيذ.

### 4. Navigation بعد Phase 05

يبقى bottom nav:

```text
Home
Train
Explore
Reports
Profile
```

لكن `Train` يصبح صفحة حقيقية بدلاً من placeholder.

## مداخل المرحلة

قبل بدء التنفيذ:

- Phase 04 مقبولة.
- `feature:home` يعمل.
- `feature:explore` يعمل.
- `feature:shell` يعمل.
- `:feature:shell:testDebugUnitTest` ناجح.
- `:feature:home:testDebugUnitTest` ناجح.
- `releaseRuntimeClasspath` نظيف من pilot dependencies.
- `:app:testDebugUnitTest` إن فشل، يكون نفس TensorFlowLite failures القديمة فقط.
- **Post-review fixes مغلقة ومتحقَّق منها:**
  - `Movit*ViewModel` (ليس Controller) في Home/Explore/Shell. ✅
  - iOS targets: `iosArm64` + `iosSimulatorArm64` في موديولات Compose؛ `iosX64` فقط في `:shared`. ✅
  - CI macOS `.github/workflows/movit-kmp-ios.yml` — **أخضر** (يكمبّل commonMain لـ iOS + يربط framework الـ shell). ✅
  - API bridge عبر debug pilot لـ **Explore و Home** (`/api/mobile/explore` + `/api/mobile/home`) — النمط متحقَّق إنه يتعمّم على شاشتين وعقدين بيانات. ✅
  - theme boundary tests في `androidUnitTest`. ✅
- **iOS entry point (`iosApp/`) — render proof نجح ✅** على Xcode/Simulator (تقرير `Android-KMP-iOS-Xcode-Mac-Validation-Report.md`)؛ التحقّقات الثلاثة اكتملت.
- **معرفة لازمة لـ Train على iOS:** توجد ديون iOS متتبَّعة في الخطة الأساسية (deployment target 18.5، استبدال `collectAsStateWithLifecycle`→`collectAsState` في الـ routes المشتركة، ViewModels عبر `remember`). أى `MovitTrainRoute` جديد **يتبع نفس نمط routes الحالية الآمن على iOS** ولا يعيد إدخال `collectAsStateWithLifecycle` قبل حسم `LifecycleOwner` على iOS. ولا يفترض deployment target نهائى حتى تُحسم P1.

## خارج نطاق هذه المرحلة

- لا نقل لـ `TrainingActivity` live session.
- لا CameraX.
- لا MediaPipe.
- لا LiteRT/ONNX.
- لا skeleton overlay.
- لا permission flow.
- لا تشغيل timer/rep detection حقيقي.
- لا API حقيقي إلا إذا كان adapter بسيطاً وموجوداً مسبقاً.
- لا ربط shell بالـ launcher.
- لا iOS app production.
- لا حذف صفحات legacy.

## الجزء 1 - Page Modernization Playbook

### 1.1 ملف مواصفات لكل صفحة

قبل تنفيذ أي صفحة، أنشئ مستند مواصفات صغير لها داخل:

```text
Docs/02-Roadmaps-And-Plans/UI-UX/Page-Specs/
```

لصفحة Train:

```text
Docs/02-Roadmaps-And-Plans/UI-UX/Page-Specs/Train-Page-Modernization-Spec.md
```

هذا الملف يجب أن يحتوي:

- current page inventory.
- user goals.
- content model.
- states.
- actions.
- layout spec.
- visual spec.
- acceptance checklist.

### 1.2 قالب مواصفات الصفحة

استخدم هذا القالب لكل صفحة:

```text
# Page Name

## Current Implementation
- legacy files
- entry points
- dependencies
- UI states
- user actions
- known issues

## User Goals
- primary goal
- secondary goals
- failure/empty states

## Content Inventory
- visible content
- hidden/conditional content
- source of data

## UX Target
- ideal flow
- information hierarchy
- actions priority
- accessibility

## Layout Spec
- compact
- medium
- expanded
- scroll behavior
- system/nav/body regions

## Visual Spec
- color roles
- typography
- spacing
- shape
- motion
- icons
- imagery

## Implementation Plan
- modules
- files
- state/events/effects
- repository/fake data
- tests

## Acceptance Criteria
```

### 1.3 Page review gate

لا يتم تنفيذ الصفحة قبل إغلاق review gate:

```text
Current page understood
Target UX approved
Visual spec approved
Implementation scope approved
```

وفي نهاية الصفحة:

```text
Build passed
Tests passed
Visual QA passed
No scope leak
Review accepted
```

## الجزء 2 - معايير التخطيط Layout Standards

### 2.1 Screen regions

كل صفحة يجب أن تصمم بناءً على:

```text
System bars
Navigation area
Body/content
```

في compact mobile:

- bottom navigation في shell.
- page content لا يختفي تحت navigation.
- top app bar/page header ثابت داخل الصفحة أو ضمن scaffold.
- scroll body واضح.

### 2.2 Width classes

نصمم على الأقل لهذه الحالات:

```text
Compact: phone portrait
Medium: phone landscape / small tablet
Expanded: tablet / desktop-like window later
```

في Phase 05 التنفيذ الفعلي يمكن أن يظل compact-first، لكن الـ spec يجب أن يذكر كيف ستتوسع الصفحة لاحقاً.

### 2.3 Compact layout

قواعد compact:

- single column.
- sections مرتبة حسب أولوية المستخدم.
- primary CTA واضح فوق fold قدر الإمكان.
- horizontal overflow فقط للفلاتر أو chips، وليس للمحتوى الأساسي.
- bottom nav يأخذ مساحة محسوبة.

### 2.4 Medium/Expanded future layout

لا نحتاج تنفيذ multi-pane الآن، لكن يجب تحديد الاتجاه:

- Train يمكن أن يتحول إلى supporting pane:
  - main pane: today's workout.
  - supporting pane: weekly plan / readiness / tips.
- Explore لاحقاً يمكن أن يصبح list-detail.
- Reports لاحقاً يمكن أن يصبح dashboard + detail pane.

### 2.5 Spacing

كل spacing من:

```text
MovitSpacing
```

ممنوع:

- magic dp داخل feature إلا لحجم ثابت منطقي مثل icon size أو media aspect placeholder.
- negative padding أو overlap.
- nested cards داخل cards.

### 2.6 Scroll behavior

كل صفحة يجب أن تحدد:

- هل الشاشة single scroll؟
- هل توجد sections ثابتة؟
- هل CTA ثابت أم داخل المحتوى؟
- هل bottom nav يغطي أي محتوى؟

في Phase 05:

```text
Train = single vertical scroll
```

## الجزء 3 - معايير الألوان Color Standards

### 3.1 المصدر الوحيد

الألوان تأتي من:

```text
MaterialTheme.colorScheme
MovitColorScheme
```

ممنوع داخل features:

```kotlin
Color(0x...)
Brush(...)
raw hex
```

### 3.2 أدوار اللون

استخدم الأدوار كالتالي:

- `primary`: CTA / active indicator / أهم progress.
- `secondary`: supportive status / safe secondary highlights.
- `tertiary`: warnings أو standout non-error highlights.
- `surface`: cards.
- `surfaceVariant`: subtle containers / placeholders.
- `background`: page body.
- `error`: error states فقط.

### 3.3 Train color behavior

صفحة Train لا يجب أن تصبح حمراء/برتقالية بالكامل لأنها صفحة نشاط. استخدم:

- primary للبدء.
- secondary للـ readiness/support.
- tertiary فقط للتنبيه أو adjustment.
- no one-note palette.

### 3.4 Dark mode

أي صفحة جديدة يجب أن تراجع:

- contrast.
- selected states.
- progress bars.
- disabled states.
- empty/error surfaces.

## الجزء 4 - معايير الأنماط Components & Patterns

### 4.1 Page structure pattern

كل صفحة feature تتبع:

```text
FeatureRoute
FeatureScreen
FeatureViewModel          ← KMP androidx.lifecycle.ViewModel
FeatureUiState
FeatureEvent
FeatureEffect
FeatureModels
FeaturePreviewData
FeatureRepository
ExploreRepositoryFactory / remote bridge (when needed)
```

**لا تستخدم `FeatureController` في Phase 05.** الترحيل إلى ViewModel اكتمل في Home/Explore/Shell؛ Train يبدأ على ViewModel من اليوم الأول.

Route pattern:

```kotlin
@Composable
fun MovitTrainRoute(
    viewModel: MovitTrainViewModel = viewModel { MovitTrainViewModel() },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    // ...
}
```

ملاحظة iOS مهمة: استخدام `collectAsState()` هنا مقصود مؤقتاً، لأنه نفس النمط الذي نجّح `Shell/Home/Explore` على iOS أثناء render proof. لا نرجع إلى `collectAsStateWithLifecycle()` في routes المشتركة إلا بعد حل دين `LifecycleOwner` على iOS. على Android يمكن استعادة lifecycle-aware collection لاحقاً عبر abstraction مشتركة أو `expect/actual`.

Shell يحتفظ بـ `trainViewModel` على مستوى `MovitAppShellRoute` (نفس نمط Home/Explore) ليبقى عبر تدوير الشاشة. على iOS، يمرر `MainViewController()` الـ ViewModels صراحة باستخدام `remember` إلى أن يتوفر `ViewModelStoreOwner` حقيقي.

### 4.2 UDF

ممنوع أن تقوم Composables بتغيير state مباشرة.

المسموح:

```text
UI emits Event
ViewModel updates State (viewModelScope)
ViewModel emits Effect
Shell/Host handles navigation/snackbar
```

### 4.3 Feature independence

الـ feature لا يعرف shell destinations.

مثال:

```text
TrainEffect.OpenExplore
```

والـ shell يترجمها إلى:

```text
MovitAppDestination.Explore
```

### 4.4 Design System reuse

استخدم الموجود:

- `MovitScaffold`
- `MovitSectionHeader`
- `MovitCard`
- `MovitButton`
- `MovitMetricTile`
- `MovitActionTile`
- `MovitStatTileRow`
- `MovitProgressBar`
- `MovitEmptyState`
- `MovitErrorState`
- `MovitLoadingState`

لا تضف component عام إلا إذا ظهر استخدامه في صفحتين أو كان primitive واضحاً.

## الجزء 5 - معايير تجربة المستخدم UX Standards

### 5.1 لكل صفحة سؤال واحد

قبل التصميم:

```text
What is the user's main job on this page?
```

صفحة Train:

```text
أريد معرفة تدريب اليوم وبدء التدريب بثقة.
```

### 5.2 Information hierarchy

الترتيب:

```text
Status / context
Primary task
Supporting details
Secondary actions
Education/tips
```

### 5.3 CTA priority

كل صفحة لها CTA رئيسي واحد.

Train:

```text
Start today's workout
```

secondary actions:

- View plan.
- Explore workouts.
- Adjust preferences later.

### 5.4 States

كل صفحة يجب أن تحتوي:

- loading.
- content.
- empty.
- error.
- unavailable/coming soon إذا لازم.

Train:

- active plan.
- no plan.
- rest day.
- completed today.
- error.

### 5.5 Accessibility

المطلوب:

- labels واضحة للأيقونات.
- touch targets مناسبة.
- text لا يعتمد على اللون وحده.
- Arabic/RTL لا يتكسر.
- لا negative letter spacing.
- لا نصوص طويلة داخل أزرار ضيقة.

### 5.6 Motion

في Phase 05:

- لا motion معقد.
- استخدم transitions خفيفة فقط لو موجودة في `MovitMotion`.
- لا animations تؤخر فهم الصفحة.

## الجزء 6 - Train Page Current Review

### 6.1 legacy files to inspect

قبل تنفيذ `feature:train` يجب مراجعة:

```text
android-poc/app/src/main/java/.../ui/train/TrainFragment.kt
android-poc/app/src/main/java/.../ui/train/TrainingActivity.kt
android-poc/app/src/main/java/.../ui/programs/ProgramWorkoutActivity.kt
android-poc/app/src/main/res/layout/*train*
android-poc/app/src/main/res/layout/*training*
android-poc/app/src/main/res/layout/*program*
```

استخدم `rg` لتحديد الملفات الفعلية لأن أسماء packages القديمة قد تختلف.

### 6.2 المطلوب استخراجه

من الصفحة الحالية:

- entry point.
- tabs/sections.
- training plan data source.
- buttons/actions.
- empty states.
- error states.
- navigation targets.
- أي شروط مرتبطة بالاشتراك أو onboarding.
- ماذا يحدث عند الضغط على start.
- ماذا يحدث إذا لا يوجد plan.

### 6.3 ما لا ننقله

لا تنقل:

- camera setup.
- pose landmarker.
- overlay rendering.
- countdown/live timer.
- ML scoring.

نكتفي بـ:

```text
Train overview page
```

## الجزء 7 - Train Target UX

### 7.1 هدف المستخدم

المستخدم يفتح Train ليعرف:

- هل لدي تدريب اليوم؟
- ما نوعه ومدته؟
- هل أنا جاهز؟
- ما الخطوة التالية؟

### 7.2 sections المقترحة

```text
Header: Train
Today status card
Primary workout card
Week plan preview
Readiness / guidance
Quick actions
```

### 7.3 active plan state

يعرض:

- اسم البرنامج.
- اليوم الحالي.
- workout title.
- عدد التمارين.
- الوقت التقريبي.
- focus area.
- CTA: Start workout.

### 7.4 no plan state

يعرض:

- empty state مفيد.
- CTA: Explore programs.
- secondary: start free workout.

### 7.5 rest day state

يعرض:

- rest/recovery message.
- mobility suggestion.
- CTA: Explore light workout.

### 7.6 completed state

يعرض:

- completion summary.
- next scheduled session.
- CTA: View report أو Explore.

## الجزء 8 - Train Visual Spec

المرجع:

```text
Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/01-train.html
Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/08-home.html
Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/00-components.html
```

### 8.1 layout

Compact:

```text
MovitScaffold
Vertical scroll
SectionHeader + Cards
Primary CTA full width
Bottom nav from shell
```

Medium/expanded future:

```text
Main pane: today's workout
Supporting pane: week plan + readiness
```

### 8.2 visual hierarchy

- أعلى الشاشة: context + status.
- أول card: workout اليوم.
- CTA ظاهر مبكراً.
- week preview بعد المهمة الرئيسية.
- guidance في card خفيف.

### 8.3 components

يمكن إنشاء داخل `feature:train/components`:

```text
TrainTodayCard
TrainWeekPreview
TrainReadinessCard
TrainQuickActions
TrainStatusBanner
```

لا ترفعها للـ design system في Phase 05 إلا إذا كانت عامة تماماً.

### 8.4 icons

استخدم Material icons الموجودة في Compose Material Icons Extended إن كانت متاحة بالفعل من Phase 04.

أمثلة:

- FitnessCenter
- PlayCircle
- CalendarMonth
- CheckCircle
- Explore

### 8.5 imagery

لا تستخدم صور stock في Phase 05. إذا احتجنا visual anchor:

- استخدم icon/shape/status داخل DS.
- الصور الحقيقية للتمارين تأتي في Explore/Workout detail لاحقاً.

## الجزء 9 - تنفيذ `feature:train`

### 9.1 إنشاء الموديول

```text
android-poc/feature/train/
```

وسجله:

```kotlin
include(":feature:train")
```

### 9.2 Gradle setup

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidTarget { /* JVM 17 */ }
    // Compose MP 1.11 — لا iosX64؛ فقط:
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:designsystem"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies { /* FakeTrainRepository via expect/actual */ }
    }
}
```

### 9.3 Files

```text
feature/train/src/commonMain/kotlin/com/movit/feature/train/
  MovitTrainRoute.kt
  MovitTrainScreen.kt
  MovitTrainViewModel.kt
  MovitTrainUiState.kt
  MovitTrainEvent.kt
  MovitTrainEffect.kt
  MovitTrainModels.kt
  MovitTrainPreviewData.kt
  TrainRepository.kt
  TrainRepositoryFactory.kt          // expect defaultTrainRepository()

feature/train/src/androidMain/kotlin/com/movit/feature/train/
  TrainRepositoryFactory.android.kt
  remote/TrainContentFetcherBridge.kt
  remote/RemoteTrainRepository.kt

feature/train/src/iosMain/kotlin/com/movit/feature/train/
  TrainRepositoryFactory.ios.kt

feature/train/src/commonMain/kotlin/com/movit/feature/train/components/
  TrainTodayCard.kt
  TrainWeekPreview.kt
  TrainReadinessCard.kt
  TrainStatusBanner.kt

feature/train/src/androidUnitTest/kotlin/com/movit/feature/train/
  MovitTrainThemeBoundaryTest.kt   // java.io.File — Android JVM only
```

### 9.4 Data layer: fake first, bridge when ready

ابدأ بـ:

```text
FakeTrainRepository
MovitTrainPreviewData
expect fun defaultTrainRepository(): TrainRepository
```

states في preview/fixtures:

- active plan.
- no plan.
- rest day.
- completed.
- error.

**API bridge (اختياري في Phase 05، نفس نمط Explore):**

- `TrainContentFetcherBridge` في `androidMain` فقط.
- `MovitTrainApiBridge.install(context)` من `app/src/debug` يعرف Retrofit القديم.
- `feature:train` لا يعتمد على `app` ولا على Retrofit مباشرة.
- iOS `iosMain` يستخدم `FakeTrainRepository` حتى Ktor.

### 9.5 Events

```kotlin
sealed interface MovitTrainEvent {
    data object StartWorkoutClicked : MovitTrainEvent
    data object ExploreProgramsClicked : MovitTrainEvent
    data object ViewReportClicked : MovitTrainEvent
    data object RetryClicked : MovitTrainEvent
}
```

### 9.6 Effects

```kotlin
sealed interface MovitTrainEffect {
    data object OpenSessionPreview : MovitTrainEffect
    data object OpenExplore : MovitTrainEffect
    data object OpenReports : MovitTrainEffect
    data class ShowMessage(val message: String) : MovitTrainEffect
}
```

في Phase 05:

- `OpenSessionPreview` لا يفتح camera.
- shell يعرض snackbar أو placeholder message:

```text
Session flow starts in a later phase.
```

## الجزء 10 - ربط Train داخل Shell

### 10.1 Shell dependencies

أضف:

```kotlin
implementation(project(":feature:train"))
```

داخل `feature:shell`.

في `:app`:

```kotlin
debugImplementation(project(":feature:train"))
```

طالما shell debug-only.

### 10.2 ViewModels في Shell route

لا تضع child ViewModels داخل `MovitAppShellViewModel`. الـ shell ViewModel يدير destination state فقط.

```kotlin
@Composable
fun MovitAppShellRoute(
    shellViewModel: MovitAppShellViewModel = viewModel(),
    homeViewModel: MovitHomeViewModel = viewModel { MovitHomeViewModel() },
    trainViewModel: MovitTrainViewModel = viewModel { MovitTrainViewModel() },
    exploreViewModel: MovitExploreViewModel = viewModel { MovitExploreViewModel() },
)
```

### 10.3 Route

في shell:

```kotlin
MovitAppDestination.Train -> MovitTrainRoute(
    viewModel = trainViewModel,
    onEffect = { shellViewModel.onEvent(MovitAppShellEvent.TrainEffectReceived(it)) },
)
```

### 10.4 Effects mapping

```text
OpenExplore -> Explore tab
OpenReports -> Reports tab
OpenSessionPreview -> snackbar/placeholder
ShowMessage -> snackbar
```

## الجزء 11 - اختبارات Phase 05

### 11.1 Train tests

```text
initial state is loading
active plan loads content
no plan state maps correctly
rest day state maps correctly
completed state maps correctly
repository failure sets error
start workout emits OpenSessionPreview
explore programs emits OpenExplore
view report emits OpenReports
screen has no MovitTheme wrapper
```

### 11.2 Shell tests

```text
Train destination renders real route by contract
Train OpenExplore effect changes destination to Explore
Train OpenReports effect changes destination to Reports
Train OpenSessionPreview emits snackbar message
```

ملاحظة: اختبار "retained across tab changes" يُختبر على مستوى `MovitAppShellRoute` (ViewModels مُمرَّرة من route)، وليس داخل shell ViewModel.

### 11.3 Theme boundary (androidUnitTest)

```text
MovitTrainThemeBoundaryTest في androidUnitTest
لا MovitTheme داخل MovitTrainScreen
لا java.io.File في commonTest
```

### 11.4 Design System checks

- لا raw colors داخل `feature:train`.
- لا negative letter spacing.
- لا nested cards.

## الجزء 12 - Gradle Verification

شغّل:

```powershell
.\gradlew.bat --console=plain :app:assembleDebug
.\gradlew.bat --console=plain :shared:testDebugUnitTest
.\gradlew.bat --console=plain :core:designsystem:testDebugUnitTest
.\gradlew.bat --console=plain :feature:home:testDebugUnitTest
.\gradlew.bat --console=plain :feature:explore:testDebugUnitTest
.\gradlew.bat --console=plain :feature:train:testDebugUnitTest
.\gradlew.bat --console=plain :feature:shell:testDebugUnitTest
.\gradlew.bat --console=plain :feature:train:compileKotlinMetadata
.\gradlew.bat --console=plain :feature:train:compileDebugKotlinAndroid
.\gradlew.bat --console=plain :feature:shell:compileDebugKotlinAndroid
.\gradlew.bat --console=plain :feature:shell:linkDebugFrameworkIosSimulatorArm64
.\gradlew.bat --console=plain :feature:train:testDebugUnitTest
.\gradlew.bat --console=plain :feature:explore:testDebugUnitTest
.\gradlew.bat --console=plain :feature:home:testDebugUnitTest
```

**CI macOS (GitHub Actions):**

```text
.github/workflows/movit-kmp-ios.yml
:shared:compileKotlinIosX64
:shared:compileKotlinIosSimulatorArm64
:feature:train:compileKotlinIosSimulatorArm64
(+ designsystem, explore, home, shell iosSimulatorArm64)
```

لا تضف `compileKotlinIosX64` لموديولات Compose — Compose MP 1.11 لا يوفّر artifacts لـ iosX64.

ثم:

```powershell
.\gradlew.bat --console=plain :app:dependencies --configuration releaseRuntimeClasspath
```

المقبول:

- إذا shell ما زال debug-only، لا يظهر `:feature:train`, `:feature:shell`, `:feature:home`, أو Compose pilot dependencies في release runtime.

## الجزء 13 - Visual QA

افتح:

```powershell
adb shell am start -n com.trainingvalidator.poc/com.movit.debug.MovitShellPilotActivity
```

راجع:

- Home ما زالت default.
- Train tab يفتح صفحة حقيقية.
- active plan state.
- no plan state.
- rest day أو completed state إن كان يمكن تمرير fixture سريعاً.
- CTA لا يفتح camera.
- Explore navigation يعمل.
- Reports placeholder يعمل.
- dark mode.
- text لا يتزاحم داخل cards/buttons.
- bottom nav لا يغطي المحتوى.

Screenshots المطلوبة:

```text
Train - active plan
Train - no plan
Train - dark mode
Home after returning from Train
Explore after Train quick action
```

### 13.1 iOS smoke بعد Train

بعد ربط `feature:train` داخل `feature:shell`:

```bash
cd android-poc
./gradlew --console=plain :feature:shell:linkDebugFrameworkIosSimulatorArm64
cd iosApp
xcodegen
```

ثم شغّل `iosApp.xcodeproj` من Xcode وتأكد من:

- التطبيق ما زال يرسم Movit shell على iOS Simulator.
- Train tab يعرض صفحة Train الجديدة ببيانات fake أو fixture آمنة.
- CTA لا يفتح camera ولا session live.
- Home/Explore navigation ما زالت تعمل.
- لا يظهر crash مرتبط بـ lifecycle أو ViewModel ownership.

## معايير القبول

## حالة تنفيذ 2026-06-08

- تم إنشاء `Train-Page-Modernization-Spec.md`.
- تم إنشاء `:feature:train` كـ KMP Compose feature مستقل.
- تم ربط `MovitAppDestination.Train` بصفحة Train حقيقية داخل `:feature:shell`.
- تم الحفاظ على `MovitShellPilotActivity` كمسار debug-only، ولم يتم نقل shell إلى production launcher.
- تم تأجيل camera/session/ML كما هو محدد في نطاق Phase 05.
- تم التحقق من عدم إدخال raw colors داخل commonMain الخاص بـ Train.
- تم تشغيل `:feature:train:testDebugUnitTest`, `:feature:shell:testDebugUnitTest`, و`:app:assembleDebug` بنجاح.
- تم فحص `releaseRuntimeClasspath` ولم تظهر موديولات pilot (`feature:train`, `feature:shell`, `feature:home`, `feature:explore`, `core:designsystem`) ضمن release runtime.
- تم تشغيل `:feature:shell:linkDebugFrameworkIosSimulatorArm64` من Windows بنجاح؛ مهمة الربط نفسها بقيت host-specific/skipped، لكن Kotlin iOS simulator compilation مرّت. ما زال Xcode Simulator visual smoke على Mac مطلوباً قبل اعتبار Train مقبولاً بصرياً على iOS.

Phase 05 لا تعتبر مكتملة إلا إذا:

- تم إنشاء `Train-Page-Modernization-Spec.md`.
- تمت مراجعة الصفحة الحالية وتوثيق خصائصها.
- تم إنشاء `feature:train`.
- `MovitTrainScreen` لا تستدعي `MovitTheme`.
- `Train` أصبحت صفحة حقيقية داخل shell.
- `Train` لا تفتح camera أو ML.
- كل الألوان من `MaterialTheme.colorScheme`.
- كل spacing من `MovitSpacing` قدر الإمكان.
- لا raw colors داخل feature.
- لا nested cards.
- صفحة Train تحتوي loading/content/empty/error على الأقل.
- active plan/no plan/rest/completed ممثلة في preview data أو tests.
- tests الخاصة بـ Train ناجحة.
- tests الخاصة بـ Shell ناجحة بعد ربط Train.
- `:feature:shell:linkDebugFrameworkIosSimulatorArm64` ناجح بعد ربط Train.
- release runtime ما زال نظيفاً إذا shell debug-only.
- Visual QA مقبول.
- iOS smoke مقبول على الأقل بتشغيل Train tab ببيانات fake.

## ما أحتاجه عند الرجوع للمراجعة

أرسل:

- `Train-Page-Modernization-Spec.md`.
- ملخص current page review.
- الملفات الجديدة/المعدلة.
- هل بقي shell debug-only؟
- نتائج Gradle.
- نتيجة releaseRuntimeClasspath.
- screenshots.
- `git status --short`.

## ملاحظات مهمة

- لا تقفز إلى session/camera في Phase 05.
- لا ننسخ legacy UI؛ نستخلص السلوك ونبني تجربة أفضل.
- كل صفحة تبدأ بمواصفة قبل التنفيذ.
- لا ننتقل لـ Reports أو Profile قبل قبول Train.
- `Train` يجب أن يشعر كجزء طبيعي من Home وExplore، لا كجزيرة تصميمية جديدة.
- **لا تراجع لـ Controller pattern** — ViewModel + `viewModelScope` إلزامي.
- **iOS:** `iosArm64` + `iosSimulatorArm64` في `feature:train`؛ لا `iosX64` في أي موديول Compose.
- **iOS runtime:** التزم بنمط `collectAsState()` وViewModels الممررة من iOS entry point إلى أن تُغلق ديون P1/P2/P3 في الخطة الأساسية.
- **API:** bridge عبر `app/debug` فقط؛ الـ feature يبقى مستقلاً عن Retrofit.
- **DI/Koin:** ما زال مؤجلاً حتى أول repository حقيقي يحتاج Ktor client مشترك.
