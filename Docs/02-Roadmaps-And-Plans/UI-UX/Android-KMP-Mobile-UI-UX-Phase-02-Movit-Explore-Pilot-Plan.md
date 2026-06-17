# Android / KMP Mobile UI/UX - Phase 02 Movit Identity + Explore Pilot Plan

آخر تحديث: 2026-06-08

## تعريف المرحلة

هذه هي المرحلة الثانية بعد تأسيس الـ Foundation. هدفها تحويل الأساس الذي تم بناؤه في Phase 01 إلى أول تجربة feature حقيقية مبنية على Compose Multiplatform وMaterial 3، مع تثبيت هوية التطبيق الجديدة `Movit`.

هذه المرحلة تغطي عملياً:

- إغلاق ملاحظات مراجعة Phase 01.
- تثبيت اسم وهوية `Movit` في الطبقات المناسبة.
- توسيع Design System بما يكفي لأول شاشة فعلية.
- بناء أول feature pilot: `Explore`.
- عدم لمس camera/ML/training session flow.

## القرار الرئيسي

أول feature pilot هو:

```text
Explore
```

السبب:

- الشاشة catalog/list/filter/search، وهي مناسبة جداً لاختبار Compose + Material 3 + Design System.
- أقل خطورة من `TrainingActivity` أو أي flow فيه CameraX/MediaPipe/LiteRT.
- موجود لها prototype حديث: `Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/04-explore.html`.
- ستجبرنا على بناء مكونات حقيقية قابلة لإعادة الاستخدام لاحقاً في `Train`, `Programs`, و`Reports`.

## خارج نطاق هذه المرحلة

- لا نقل لـ `TrainingActivity`.
- لا نقل لـ camera overlay أو skeleton overlay.
- لا نقل لـ ML/MediaPipe/LiteRT.
- لا refactor واسع للـ storage.
- لا إعادة بناء كاملة للـ navigation الرئيسي.
- لا حذف للشاشة القديمة قبل وجود fallback واضح.
- لا iOS production app. يمكن تجهيز source sets فقط، لكن ليس مطلوباً تشغيل iOS على Windows.

## مداخل المرحلة

يجب أن تكون Phase 01 مقبولة بعد إصلاح التالي:

- إزالة أو تصفير `letterSpacing` السالب في `PoseTypography`.
- قبول التحذير الخاص بـ KMP + `com.android.library` كـ follow-up أو إصلاحه لو قررت التعامل معه الآن.
- `:app:assembleDebug` ناجح.
- `:shared` و`:core:designsystem` يبنيان بنجاح.
- component catalog يعمل.

## مخرجات المرحلة

بنهاية Phase 02 يجب أن يكون لدينا:

- هوية `Movit` مثبتة على مستوى العرض والـ Design System.
- مكونات Design System إضافية لازمة لـ Explore.
- موديول feature جديد لـ Explore أو حزمة منظمة حسب قرار التنفيذ.
- شاشة `MovitExploreScreen` مبنية بـ Compose.
- `UiState / Event / Effect` واضح.
- Android debug/entry route لفتح الشاشة الجديدة.
- بيانات fake/local أو adapter مؤقت من البيانات الحالية.
- Build ناجح.
- Screenshot أو تسجيل بصري للشاشة في Light/Dark.

## الجزء 1 - إغلاق ملاحظات Foundation

### 1.1 Typography

المطلوب:

- إزالة `letterSpacing` السالب من `PoseTypography`.
- استخدم `letterSpacing = 0.sp` فقط إن احتجت للتصريح.
- أضف test بسيط يمنع الرجوع لقيم سالبة.

سبب القرار:

- العربية وRTL تتضرر سريعاً من negative tracking.
- الخطة التصميمية الأصلية تشترط letter spacing = 0.

### 1.2 KMP Android plugin warning

التحذير الحالي:

```text
org.jetbrains.kotlin.multiplatform + com.android.library deprecated before AGP 9
```

قرار Phase 02:

- ليس blocker للـ pilot.
- افتح له TODO موثق في خطة تقنية أو عالجه الآن إذا كان التغيير صغيراً ومستقراً.
- لا تبدأ pilot وأنت في منتصف تغيير Gradle كبير غير مستقر.

## الجزء 2 - تثبيت هوية Movit

### 2.1 مستويات التسمية

نفرق بين أربعة مستويات:

```text
Product name       = Movit
Project name       = Movit
Visible app label  = Movit
Package/applicationId = قرار تقني/release identity
```

### 2.2 المطلوب الآن

نفذ في Phase 02:

- `rootProject.name = "Movit"` إذا لم يكن مثبتاً بالفعل.
- تحديث `app_name` وstrings المرئية إلى `Movit`.
- تحديث عنوان component catalog من `Pose Design System` إلى `Movit Design System`.
- تحديث أسماء theme الجديدة في code الجديد:
  - `MovitTheme`
  - `MovitColorScheme`
  - `MovitTypography`
  - `MovitShapes`
  - `MovitSpacing`
  - `MovitMotion`
- يفضل إبقاء aliases مؤقتة من `Pose*` إلى `Movit*` أو تنفيذ rename كامل داخل الموديولات الجديدة فقط.

### 2.3 ما لا يتم الآن إلا بقرار صريح

- `applicationId` = `com.movit.androidApp` (متوازٍ مع iOS `com.movit.iosApp`) — قائمة Play جديدة؛ لا يحدّث تثبيتات `com.trainingvalidator.poc` القديمة.
- لا تعيد تسمية كل packages القديمة مرة واحدة.
- لا تعيد تسمية ملفات training/pose engine التي تستخدم كلمة `Pose` بمعنى human pose. هنا `Pose` مصطلح domain وليس brand.

### 2.4 naming strategy المقترحة

للجديد فقط:

```text
com.movit.*
```

أو إذا قررت عدم تغيير package الآن:

```text
com.movit.movit.*
```

القرار الموصى به لهذه المرحلة:

- display/brand = `Movit`.
- الموديولات الجديدة يمكن أن تستخدم `com.movit` إذا كنت مستعداً للـ package migration.
- الموديولات القديمة تبقى كما هي إلى حين مرحلة rename منظمة.

## الجزء 3 - توسيع Design System

قبل بناء Explore، أضف أو ثبّت المكونات التالية داخل Design System.

### 3.1 Core layout

```text
MovitScaffold
MovitTopBar
MovitSearchBar
MovitScreenContainer
```

المطلوب:

- دعم content padding.
- دعم background من `MaterialTheme.colorScheme.background`.
- دعم title/subtitle/action.
- عدم ربطها بأي feature.

### 3.2 Cards

```text
MovitMediaCard
MovitListCard
MovitExerciseCard
MovitWorkoutCard
MovitProgramCard
```

في Phase 02 يكفي:

- `MovitMediaCard`
- `MovitExerciseCard`

المواصفات:

- صورة أو placeholder.
- title.
- subtitle.
- metadata row.
- optional badge.
- click callback.
- loading/disabled state.

### 3.3 Selection and filters

```text
MovitFilterChip
MovitFilterRow
MovitSegmentedControl
```

المطلوب:

- single-select filter row.
- horizontal scroll أو FlowRow حسب المساحة.
- selected/unselected states من Material 3 roles.

### 3.4 States

```text
MovitLoadingState
MovitEmptyState
MovitErrorState
```

المطلوب:

- `Explore` يجب أن تعرض الحالات الثلاثة.
- لا تعرض شاشة فارغة بدون state واضح.

### 3.5 Catalog update

حدّث component catalog ليعرض:

- Movit brand title.
- Search bar.
- Exercise card.
- Media card.
- Filter row.
- Loading/empty/error states.
- Arabic/RTL sample block إن أمكن.

## الجزء 4 - Feature module / package

الخيار الأفضل:

```text
android-poc/feature/explore/
```

وتسجيل:

```kotlin
include(":feature:explore")
```

لو أردت تقليل تغييرات Gradle في هذه المرحلة، يمكن مؤقتاً وضعها داخل:

```text
core/designsystem/catalog or app/debug
```

لكن الخيار الاحترافي هو feature module مستقل.

### الهيكلة المقترحة

```text
feature/explore/
  build.gradle.kts
  src/
    commonMain/kotlin/com/movit/feature/explore/
      MovitExploreRoute.kt
      MovitExploreScreen.kt
      MovitExploreUiState.kt
      MovitExploreEvent.kt
      MovitExploreEffect.kt
      MovitExploreModels.kt
      MovitExplorePreviewData.kt
      components/
        ExploreHero.kt
        ExploreSearchSection.kt
        ExploreFilterSection.kt
        ExploreExerciseList.kt
        ExploreExerciseCard.kt
    commonTest/kotlin/com/movit/feature/explore/
      MovitExploreStateTest.kt
    androidMain/kotlin/com/movit/feature/explore/
      AndroidExplorePreviewHost.kt optional
```

لو لم تغير package:

```text
com.movit.feature.explore
```

## الجزء 5 - Explore UI contract

### 5.1 UiState

```kotlin
data class MovitExploreUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val selectedFilter: ExploreFilter = ExploreFilter.All,
    val filters: List<ExploreFilter> = ExploreFilter.defaults,
    val featured: List<ExploreItemUi> = emptyList(),
    val exercises: List<ExploreItemUi> = emptyList(),
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
)
```

### 5.2 Events

```kotlin
sealed interface MovitExploreEvent {
    data class QueryChanged(val value: String) : MovitExploreEvent
    data class FilterSelected(val filter: ExploreFilter) : MovitExploreEvent
    data class ItemClicked(val id: String) : MovitExploreEvent
    data object RetryClicked : MovitExploreEvent
    data object RefreshRequested : MovitExploreEvent
}
```

### 5.3 Effects

```kotlin
sealed interface MovitExploreEffect {
    data class NavigateToExercise(val id: String) : MovitExploreEffect
    data class ShowMessage(val message: String) : MovitExploreEffect
}
```

### 5.4 UI models

```kotlin
data class ExploreItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val badge: String? = null,
    val metadata: List<String> = emptyList(),
)
```

## الجزء 6 - البيانات

### القرار لهذه المرحلة

لا تربط API حقيقي في أول pilot إلا إذا كان الربط سهل وآمن.

المسار الموصى به:

1. ابدأ بـ `MovitExplorePreviewData`.
2. اعرض الشاشة كاملة بالحالات.
3. أضف interface بسيط:

```kotlin
interface ExploreRepository {
    suspend fun getExploreItems(): AppResult<List<ExploreItemUi>>
}
```

4. استخدم fake repository في debug.
5. اربط API الحقيقي في مرحلة لاحقة أو نهاية Phase 02 فقط لو لم يزود المخاطر.

السبب:

- هدف Phase 02 هو إثبات UI architecture وDesign System، وليس حل data layer بالكامل.

## الجزء 7 - Android host

أضف debug Activity أو debug route:

```text
app/src/debug/java/.../MovitExplorePilotActivity.kt
```

المطلوب:

- تعرض `MovitExploreRoute` أو `MovitExploreScreen`.
- ليست launcher.
- يمكن فتحها بـ adb:

```powershell
adb shell am start -n com.movit.androidApp/.debug.MovitExplorePilotActivity
```

لو غيرت package/activity path، وثق الأمر الفعلي.

## الجزء 8 - التصميم المطلوب للشاشة

استلهم من:

```text
Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/04-explore.html
Docs/02-Roadmaps-And-Plans/UI-UX/prototypes/00-components.html
```

الشاشة يجب أن تحتوي:

- Top bar باسم Movit أو عنوان Explore.
- Search input.
- Filter chips.
- Featured section.
- Exercise cards.
- Empty state عند عدم وجود نتائج.
- Error state.
- Loading state.
- Light/Dark.
- نصوص عربية/إنجليزية قابلة لاحقاً للـ localization.

لا يشترط pixel-perfect، لكن يجب أن تكون أقرب للاتجاه الجديد لا للـ XML legacy.

## الجزء 9 - اختبارات المرحلة

### Unit tests

مطلوب على الأقل:

- filter selection updates state.
- query filters list.
- empty state appears when no matches.
- error state preserved.

### Build checks

```powershell
cd android-poc
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :shared:testDebugUnitTest
.\gradlew.bat :core:designsystem:testDebugUnitTest
.\gradlew.bat :feature:explore:testDebugUnitTest
```

ولو متاح:

```powershell
.\gradlew.bat :feature:explore:compileKotlinMetadata
.\gradlew.bat :feature:explore:compileDebugKotlinAndroid
```

ملاحظة:

- `:app:testDebugUnitTest` معروف أن به failures قديمة من TensorFlowLite. لا يعتبر blocker لهذه المرحلة إلا إذا ظهرت failures جديدة مرتبطة بـ Movit/Explore.

## الجزء 10 - معايير القبول

لن ننتقل للمرحلة التالية إلا إذا تحقق الآتي:

- Phase 01 P1 typography fixed.
- اسم `Movit` ظاهر في catalog أو pilot host.
- لا توجد negative letter spacing في Design System.
- `:app:assembleDebug` ناجح.
- `:feature:explore` موجود أو يوجد سبب موثق لعدم فصله الآن.
- `MovitExploreScreen` مبنية بـ Compose.
- الشاشة تستخدم `MovitTheme`/Design System فقط.
- لا raw colors داخل feature components.
- لا business logic داخل Composables.
- توجد loading/empty/error states.
- توجد tests للـ state أو filtering.
- لا تغيير غير منظم لـ `applicationId`.
- لا لمس لـ camera/training session.
- screenshot أو مراجعة بصرية Light/Dark موجودة.

## الجزء 11 - ما أحتاجه منك عند الرجوع للمراجعة

أرسل:

- ملخص التغييرات.
- هل استخدمت `Movit*` rename كامل أم aliases مؤقتة؟
- هل غيّرت package/applicationId أم فقط display name؟
- نتيجة أوامر Gradle.
- أي failures جديدة.
- screenshot من Explore في Light/Dark.
- `git status --short`.
- قائمة الملفات الجديدة/المعدلة.

سأراجع بعدها:

- جودة naming بعد Movit.
- هل feature module boundary صحيح.
- هل Design System توسع بدون فوضى.
- هل Explore جاهزة كنمط لباقي الشاشات.
- هل ننتقل بعدها إلى `Home` أو `Level Profile`.

## ملاحظات تنفيذية مهمة

- إذا كان rename من `Pose*` إلى `Movit*` كبيراً، نفذه داخل الموديولات الجديدة فقط الآن.
- لا تخلط rename شامل للـ legacy XML themes مع Explore pilot إلا لو كان صغيراً وآمناً.
- `Pose` في سياق human pose/training engine ليس brand قديم، فلا تغيره عشوائياً.
- العلامة والثيم في XML legacy: **Movit** (`Theme.Movit` · `Widget.Movit.*`). Deep links: `movit://`.
- الهدف من Phase 02 هو بناء أول شاشة صح، لا تنظيف كل التاريخ السابق.

