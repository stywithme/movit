# Android / KMP Mobile UI/UX - Phase 04 Movit Home Dashboard + Shell Alignment Plan

آخر تحديث: 2026-06-08

## تعريف المرحلة

هذه هي المرحلة الرابعة بعد إغلاق `Movit App Shell Pilot`. هدفها تحويل الـ shell من إطار يحتوي `Explore` فقط إلى تجربة تطبيق حقيقية تبدأ بـ `Home Dashboard`، مع ضبط ترتيب الـ destinations ليطابق شكل المنتج المقترح في الـ prototypes.

هذه المرحلة هي أول خطوة تبني "واجهة يومية" للمستخدم، وليست مجرد catalog أو شاشة استكشاف. سنبني `Home` كـ feature مستقلة في KMP/Compose Multiplatform، ونربطها بالـ shell كأول tab، مع إبقاء `Train`, `Reports`, و`Profile` placeholders منظمة إلى أن تبدأ مراحلها الخاصة.

## القرار الرئيسي

ننفذ في هذه المرحلة:

```text
Movit Home Dashboard as the first real app destination
```

السبب:

- Phase 02 بنت أول feature حقيقية: `Explore`.
- Phase 03 بنت `Movit App Shell`.
- الخطوة الطبيعية الآن هي بناء الشاشة التي يفتح عليها المستخدم يومياً: `Home`.
- prototype واضح موجود: `Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/08-home.html`.
- Home سيجبرنا على بناء عقود UI عامة للـ plan summary, progress, quick actions, وreports preview بدون لمس camera/ML.

## قرارات Phase 04

### 1. ترتيب الـ Navigation

الترتيب المستهدف للـ bottom navigation:

```text
Home
Train
Explore
Reports
Profile
```

قرار مهم:

- `Programs` لا يبقى root bottom tab في Phase 04.
- البرامج تصبح محتوى داخل `Explore` أو `Train` لاحقاً.
- هذا يتماشى مع prototypes التي تعرض `Home`, `Train`, `Explore`, `Reports` كوجهات رئيسية، ومع الحاجة العملية إلى وجود `Profile` كوجهة حساب واضحة.

### 2. Default destination

بعد Phase 04:

```text
MovitAppDestination.Home
```

هي البداية الافتراضية للـ shell بدلاً من `Explore`.

### 3. Shell status

القرار الموصى به:

```text
MovitShellPilotActivity يبقى debug-only في Phase 04
```

لا ننقل الـ shell إلى launcher production حتى يكون لدينا:

- Home حقيقية.
- Explore مستقرة.
- Train placeholder واضح أو Train pilot.
- قرار صريح حول legacy launcher.

يمكن تنفيذ shell production لاحقاً في Phase 05 أو Phase 06.

## مداخل المرحلة

قبل بدء Phase 04 يجب أن تكون Phase 03 مغلقة:

- `:app:assembleDebug` ناجح.
- `:feature:shell:testDebugUnitTest` ناجح.
- `:feature:shell:compileDebugKotlinAndroid` ناجح.
- `:feature:explore:testDebugUnitTest` ناجح.
- `releaseRuntimeClasspath` لا يحتوي pilot dependencies.
- `MovitExploreScreen` بدون `MovitTheme` داخلي.
- `MovitTheme` عند root hosts فقط.
- `:app:testDebugUnitTest` إن فشل، يكون نفس فشل TensorFlowLite القديم فقط.

## مخرجات المرحلة

بنهاية Phase 04 يجب أن يكون لدينا:

- موديول جديد:

```text
feature:home
```

- شاشة:

```text
MovitHomeScreen
```

- route/controller/state منظم:

```text
MovitHomeRoute
MovitHomeController
MovitHomeUiState
MovitHomeEvent
MovitHomeEffect
```

- Home Dashboard مبني على prototype `08-home.html`.
- `Home` مضاف إلى `MovitAppDestination`.
- `Home` هو default destination.
- `Programs` خارج bottom nav root.
- shell يربط Home quick actions بـ tab navigation.
- tests للـ Home state والـ Shell navigation الجديد.
- Visual QA للـ shell مع Home/Explore/placeholders.

## خارج نطاق هذه المرحلة

- لا نقل لـ `TrainingActivity`.
- لا فتح CameraX أو MediaPipe أو LiteRT.
- لا تشغيل session/pose detection داخل Compose.
- لا نقل reports الحقيقي.
- لا API حقيقي إلا إذا كان موجوداً بالفعل وبسيطاً جداً.
- لا DI framework.
- لا تغيير `applicationId`.
- لا iOS production app.
- لا حذف legacy screens.
- لا تحويل shell إلى launcher إلا بقرار صريح قبل التنفيذ.

## الجزء 1 - إنشاء `feature:home`

### 1.1 إضافة الموديول

أضف:

```text
kmp-app/feature/home/
```

وسجله في `settings.gradle.kts`:

```kotlin
include(":feature:home")
```

### 1.2 Gradle setup

ابدأ بنفس pattern المستخدم في `feature:explore` و`feature:shell`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}
```

Dependencies المقترحة:

```kotlin
commonMain.dependencies {
    implementation(project(":shared"))
    implementation(project(":core:designsystem"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutines.core)
}

commonTest.dependencies {
    implementation(kotlin("test"))
    implementation(libs.kotlinx.coroutines.core)
}
```

ملاحظة:

- لا تضف dependency على `feature:shell`.
- `feature:home` يجب أن يبقى مستقلاً، والـ shell هو الذي يقرر أين يذهب المستخدم بعد quick action.

## الجزء 2 - هيكلة ملفات Home

الهيكلة المطلوبة:

```text
feature/home/src/commonMain/kotlin/com/movit/feature/home/
  MovitHomeRoute.kt
  MovitHomeScreen.kt
  MovitHomeController.kt
  MovitHomeUiState.kt
  MovitHomeEvent.kt
  MovitHomeEffect.kt
  MovitHomeModels.kt
  MovitHomePreviewData.kt
  HomeRepository.kt
  HomeSummaryCalculator.kt

feature/home/src/commonMain/kotlin/com/movit/feature/home/components/
  HomeHeroSummary.kt
  TodayPlanCard.kt
  HomeProgressSection.kt
  HomeReportPreview.kt
  HomeQuickActions.kt
  HomeInsightCard.kt

feature/home/src/commonTest/kotlin/com/movit/feature/home/
  MovitHomeStateTest.kt
  HomeSummaryCalculatorTest.kt
```

قاعدة مهمة:

- أي component يحتوي domain مثل `Today's plan` يبقى داخل `feature:home`.
- أي component عام تماماً مثل stat tile أو action tile يمكن رفعه إلى `core:designsystem`.

## الجزء 3 - Home UI Contract

### 3.1 Models

ابدأ بـ models بسيطة قابلة للمشاركة:

```kotlin
data class HomeTrainingPlanUi(
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val exerciseCountLabel: String,
    val statusLabel: String,
)

data class HomeProgressUi(
    val weeklyCompletionPercent: Int,
    val streakDays: Int,
    val activeMinutesLabel: String,
    val formScoreLabel: String,
)

data class HomeReportPreviewUi(
    val title: String,
    val subtitle: String,
    val scoreLabel: String,
    val trendLabel: String,
)

data class HomeQuickActionUi(
    val id: String,
    val label: String,
    val description: String,
)
```

### 3.2 UiState

الشكل المقترح:

```kotlin
data class MovitHomeUiState(
    val isLoading: Boolean = false,
    val todayPlan: HomeTrainingPlanUi? = null,
    val progress: HomeProgressUi? = null,
    val reportPreview: HomeReportPreviewUi? = null,
    val quickActions: List<HomeQuickActionUi> = emptyList(),
    val insightMessage: String? = null,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading &&
            errorMessage == null &&
            todayPlan == null &&
            progress == null &&
            reportPreview == null
}
```

### 3.3 Events

```kotlin
sealed interface MovitHomeEvent {
    data object RetryClicked : MovitHomeEvent
    data object StartTodayPlanClicked : MovitHomeEvent
    data object ExploreClicked : MovitHomeEvent
    data object ReportsClicked : MovitHomeEvent
    data object ProfileClicked : MovitHomeEvent
}
```

### 3.4 Effects

`feature:home` لا يعرف `MovitAppDestination`. استخدم effects semantic:

```kotlin
sealed interface MovitHomeEffect {
    data object OpenTrain : MovitHomeEffect
    data object OpenExplore : MovitHomeEffect
    data object OpenReports : MovitHomeEffect
    data object OpenProfile : MovitHomeEffect
    data class ShowMessage(val message: String) : MovitHomeEffect
}
```

الـ shell يترجم هذه effects إلى destination changes.

## الجزء 4 - Home Repository وPreview Data

### 4.1 البداية

ابدأ بـ:

```text
FakeHomeRepository
MovitHomePreviewData
```

ولا تربط API حقيقي في هذه المرحلة.

### 4.2 HomeRepository

```kotlin
interface HomeRepository {
    suspend fun getHomeDashboard(): AppResult<HomeDashboardUi>
}
```

`HomeDashboardUi` يمكن أن يجمع:

```kotlin
data class HomeDashboardUi(
    val todayPlan: HomeTrainingPlanUi?,
    val progress: HomeProgressUi,
    val reportPreview: HomeReportPreviewUi?,
    val quickActions: List<HomeQuickActionUi>,
    val insightMessage: String?,
)
```

### 4.3 حالات البيانات

جهّز fixtures لهذه الحالات:

- user with active plan.
- user without plan.
- loading.
- error.
- light progress / strong progress.

هذه الحالات مهمة للـ Visual QA ولا يجب أن تنتظر API.

## الجزء 5 - تصميم Home حسب prototype

المرجع:

```text
Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/08-home.html
Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/00-components.html
```

### 5.1 أقسام الشاشة

رتب الشاشة كالتالي:

```text
Header / greeting
Hero summary / readiness card
Today's plan
Progress / journey
Reports preview
Quick actions
```

### 5.2 Header

المطلوب:

- title مثل `Home` أو `Good morning`.
- subtitle قصير مرتبط باليوم.
- لا تضف profile avatar حقيقي الآن إلا إذا كان component بسيطاً.

### 5.3 Hero summary

يعرض:

- حالة اليوم.
- training readiness أو plan adjustment.
- action واضح: `Start training` أو `Explore programs`.

### 5.4 Today's plan

يعرض:

- plan title.
- duration.
- exercise count.
- status badge.
- CTA واضح.

إذا لا يوجد plan:

- استخدم `MovitEmptyState` أو component Home خاص.
- action يذهب إلى Explore.

### 5.5 Progress / journey

يعرض:

- weekly completion.
- streak.
- active minutes.
- form score.

لا تبني chart معقد في Phase 04. استخدم metrics/cards بسيطة أو progress indicator.

### 5.6 Reports preview

يعرض summary بسيط:

- last session score.
- trend.
- action يذهب إلى Reports placeholder.

### 5.7 Quick actions

أزرار أو tiles:

```text
Train
Explore
Reports
Profile
```

كل action يرسل effect إلى shell.

## الجزء 6 - Design System Additions

لا تضخم Design System. أضف فقط ما سيتكرر.

### 6.1 مرشح للرفع إلى design system

```text
MovitActionTile
MovitStatTileRow
MovitProgressRing أو MovitProgressBar
```

قاعدة القرار:

- لو component يستخدم نصوص وmodels عامة فقط => design system.
- لو component يعرف `today plan`, `training`, `reports` => feature/home.

### 6.2 Bottom navigation icons

في Phase 03 استخدم shell أول حرف كـ icon مؤقت. في Phase 04 حاول تحسين ذلك:

الخيار المفضل:

```kotlin
implementation(compose.materialIconsExtended)
```

ثم استخدم Material icons مناسبة:

```text
Home
FitnessCenter أو PlayCircle
Explore أو TravelExplore
Assessment أو BarChart
Person
```

لو dependency غير متاح أو تسبب تعقيداً:

- لا تكسر المرحلة.
- وثق أن icons ستدخل في Phase 05.
- لكن visual QA يجب أن يتأكد أن الـ labels الحالية لا تبدو مكسورة.

### 6.3 Catalog update

إذا أضفت components عامة:

- حدث `MovitComponentCatalogScreen`.
- أضف samples لـ action tile/progress/stat row.
- تأكد أن Light/Dark toggle ما زال يعمل.
- تأكد أن RTL sample لم يتكسر.

## الجزء 7 - تعديل App Shell

### 7.1 Destination update

حدث `MovitAppDestination` إلى:

```kotlin
enum class MovitAppDestination(
    val route: String,
    val label: String,
) {
    Home("home", "Home"),
    Train("train", "Train"),
    Explore("explore", "Explore"),
    Reports("reports", "Reports"),
    Profile("profile", "Profile"),
}
```

احذف `Programs` كـ root destination في هذه المرحلة.

### 7.2 State default

```kotlin
data class MovitAppShellState(
    val selectedDestination: MovitAppDestination = MovitAppDestination.Home,
)
```

### 7.3 ربط Home داخل shell

`MovitAppShell` يجب أن يحتوي:

```kotlin
when (state.selectedDestination) {
    MovitAppDestination.Home -> MovitHomeRoute(...)
    MovitAppDestination.Explore -> MovitExploreRoute(...)
    else -> MovitPlaceholderScreen(...)
}
```

### 7.4 Home effects داخل shell

أضف homeController في shell controller:

```kotlin
class MovitAppShellController(
    val homeController: MovitHomeController = MovitHomeController(),
    val exploreController: MovitExploreController = MovitExploreController(),
)
```

الـ route يجمع effects من Home ويحولها:

```text
OpenTrain   -> selectedDestination = Train
OpenExplore -> selectedDestination = Explore
OpenReports -> selectedDestination = Reports
OpenProfile -> selectedDestination = Profile
ShowMessage -> snackbar
```

لا تجعل `feature:home` يعتمد على `feature:shell`.

## الجزء 8 - Debug Host

استمر في استخدام:

```text
MovitShellPilotActivity
```

لا تضف Activity منفصلة لـ Home إلا لو احتجت visual QA سريع جداً. الأفضل أن نختبر Home داخل shell.

أمر الفتح:

```powershell
adb shell am start -n com.trainingvalidator.poc/com.movit.debug.MovitShellPilotActivity
```

المتوقع:

- يفتح على Home.
- Bottom nav يظهر Home selected.
- Explore tab يعمل كما في Phase 03.
- Train/Reports/Profile placeholders تعمل.
- Home quick actions تغير tab أو تعرض snackbar.

## الجزء 9 - Tests

### 9.1 Home tests

في `feature:home`:

- initial state loading أو empty حسب تصميم controller.
- successful load populates dashboard.
- repository failure sets `errorMessage`.
- no-plan fixture produces empty/today-plan fallback.
- `StartTodayPlanClicked` emits `OpenTrain`.
- `ExploreClicked` emits `OpenExplore`.
- `ReportsClicked` emits `OpenReports`.
- `ProfileClicked` emits `OpenProfile`.
- summary calculator clamps percentage between 0 و100.

### 9.2 Shell tests

حدث `MovitAppShellStateTest`:

- initial destination is `Home`.
- selecting `Explore` updates destination.
- selecting same destination remains idempotent.
- homeController is retained across tab changes.
- exploreController is retained across tab changes.
- Home `OpenExplore` effect changes selected destination أو يتم اختباره من خلال shell event handler حسب التصميم.

### 9.3 Theme boundary tests

استمر في حماية:

- `MovitExploreScreen` لا يستدعي `MovitTheme`.
- أضف نفس الفكرة لـ `MovitHomeScreen` إن أمكن.

لا تعتمد على grep fragile إن كان صعباً، لكن وجود test بسيط يحرس theme boundary مفيد.

## الجزء 10 - Gradle Verification

شغّل:

```powershell
.\gradlew.bat --console=plain :app:assembleDebug
.\gradlew.bat --console=plain :shared:testDebugUnitTest
.\gradlew.bat --console=plain :core:designsystem:testDebugUnitTest
.\gradlew.bat --console=plain :feature:explore:testDebugUnitTest
.\gradlew.bat --console=plain :feature:shell:testDebugUnitTest
.\gradlew.bat --console=plain :feature:home:testDebugUnitTest
.\gradlew.bat --console=plain :feature:home:compileKotlinMetadata
.\gradlew.bat --console=plain :feature:home:compileDebugKotlinAndroid
.\gradlew.bat --console=plain :feature:shell:compileDebugKotlinAndroid
```

ثم:

```powershell
.\gradlew.bat --console=plain :app:dependencies --configuration releaseRuntimeClasspath
```

المقبول:

- إذا shell ما زال debug-only، لا يظهر `:feature:home` ولا `:feature:shell` ولا Compose داخل release runtime بسبب الـ pilot.

اختياري:

```powershell
.\gradlew.bat --console=plain :app:assembleRelease
```

### Known legacy test failure

` :app:testDebugUnitTest` قد يفشل بنفس TensorFlowLite `UnsatisfiedLinkError`.

ليس blocker إذا:

- نفس 7 tests القديمة فقط.
- لا يوجد failure جديد من `Movit`, `feature:home`, `feature:shell`, أو `feature:explore`.

## الجزء 11 - Visual QA

افتح:

```powershell
adb shell am start -n com.trainingvalidator.poc/com.movit.debug.MovitShellPilotActivity
```

راجع:

- Home default selected.
- Home content لا يختفي تحت bottom nav.
- Light mode.
- Dark mode إن أمكن.
- Quick actions تعمل.
- Explore ما زالت تعمل.
- Train placeholder.
- Reports placeholder.
- Profile placeholder.
- RTL catalog sample ما زال صحيحاً.

Screenshots المطلوبة:

```text
Shell - Home
Shell - Home no-plan state
Shell - Explore
Shell - Reports placeholder
Catalog - new components if any
```

## معايير القبول

Phase 04 لا تعتبر مكتملة إلا إذا تحقق التالي:

- `feature:home` موجود.
- `MovitHomeScreen` موجودة ولا تستدعي `MovitTheme`.
- `MovitHomeRoute/Controller/UiState/Event/Effect` موجودة.
- Home تستخدم `core:designsystem` ولا تحتوي raw colors.
- Home مبنية على prototype `08-home.html` بشكل واضح.
- Home تعرض loading/content/empty/error أو على الأقل content/empty/error حسب البيانات.
- Home quick actions تعمل عبر shell.
- Shell default destination أصبحت `Home`.
- Bottom navigation يحتوي `Home, Train, Explore, Reports, Profile`.
- `Programs` لم يعد root bottom tab.
- Explore ما زالت تعمل داخل shell.
- tests الخاصة بـ `feature:home` ناجحة.
- tests الخاصة بـ `feature:shell` ناجحة بعد تحديث default destination.
- Gradle verification ناجح.
- release runtime لا يحتوي pilot dependencies إذا shell debug-only.
- لم يتم تغيير `applicationId`.
- لم يتم لمس CameraX/MediaPipe/LiteRT/training session flow.

## ما أحتاجه عند الرجوع للمراجعة

عند تنفيذ Phase 04، أرسل:

- ملخص الملفات الجديدة/المعدلة.
- هل بقي shell debug-only أم تم اتخاذ قرار إدخاله production؟
- هل استخدمت icons في bottom nav أم بقيت placeholders؟
- نتائج Gradle:

```text
:app:assembleDebug
:shared:testDebugUnitTest
:core:designsystem:testDebugUnitTest
:feature:explore:testDebugUnitTest
:feature:shell:testDebugUnitTest
:feature:home:testDebugUnitTest
:feature:home:compileKotlinMetadata
:feature:home:compileDebugKotlinAndroid
:feature:shell:compileDebugKotlinAndroid
```

- نتيجة `releaseRuntimeClasspath`.
- نتيجة `:app:testDebugUnitTest` إن شغلتها.
- screenshots للـ Home والـ Shell.
- `git status --short`.

## ملاحظات مهمة

- لا تجعل Home تعرف shell destinations مباشرة.
- لا تجعل shell يعرف تفاصيل Home UI الداخلية.
- Home dashboard يجب أن يشعر كتجربة منتج، وليس placeholder.
- لا ترفع كل component إلى design system تلقائياً.
- إضافة Home ستغير اختبارات shell القديمة التي كانت تفترض Explore كـ default.
- إذا ظهر تعارض حول `Programs`, القرار الحالي: ليست root tab، لكنها ستعود كمحتوى داخل Explore/Train.
- Phase 04 هي آخر pilot كبير قبل التفكير في ربط shell بالـ launcher production في مرحلة لاحقة.
