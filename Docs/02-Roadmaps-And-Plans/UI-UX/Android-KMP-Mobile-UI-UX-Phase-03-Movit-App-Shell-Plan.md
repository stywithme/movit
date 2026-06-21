# Android / KMP Mobile UI/UX - Phase 03 Movit App Shell + Release Hygiene Plan

آخر تحديث: 2026-06-08

## تعريف المرحلة

هذه هي المرحلة الثالثة بعد نجاح `Movit Explore Pilot`. هدفها نقلنا من شاشة Compose منفردة إلى بنية تطبيق واضحة يمكن البناء فوقها: `Movit App Shell` يحتوي Navigation/Scaffold/Theme boundary، مع إدخال ملاحظات مراجعة Phase 02 كجزء إلزامي من التنفيذ.

هذه المرحلة ليست ترحيلاً كاملاً للتطبيق القديم. هي تثبيت لطريقة تشغيل الشاشات الجديدة داخل shell احترافي، وتجهيز الطريق لنقل `Home`, `Train`, `Programs`, `Reports`, و`Profile` لاحقاً بنفس النمط.

## القرار الرئيسي

ننفذ في هذه المرحلة:

```text
Release Hygiene + Movit App Shell Pilot
```

السبب:

- Phase 02 أثبتت أن `feature:explore` و`core:designsystem` يعملان.
- نحتاج الآن boundary واضح للـ Theme والـ Navigation بدلاً من فتح كل شاشة داخل Activity منفصلة.
- نحتاج منع تسريب pilot/debug dependencies إلى release runtime.
- لا يزال من المبكر لمس `TrainingActivity`, CameraX, MediaPipe, LiteRT.

## Findings من مراجعة Phase 02

هذه الملاحظات تدخل في بداية Phase 03 وتنفذ ككتلة واحدة مع الخطة.

### F01 - Pilot dependencies موجودة في release runtime

المشكلة:

في `:app` تم ربط الموديولات الجديدة بـ `implementation`:

```kotlin
implementation(project(":shared"))
implementation(project(":core:designsystem"))
implementation(project(":feature:explore"))
implementation(libs.androidx.activity.compose)
```

بما أن `MovitExplorePilotActivity` و`MovitDesignSystemCatalogActivity` موجودان داخل `app/src/debug` فقط، فهذا يجعل Compose وExplore يدخلان release runtime بدون استخدام إنتاجي حقيقي.

المطلوب في Phase 03:

- إذا ظل الـ Shell الجديد debug/pilot فقط، حوّل dependencies الخاصة به في `:app` إلى `debugImplementation`.
- أبق `debugImplementation(libs.compose.ui.tooling)` كما هو.
- تحقق أن `releaseRuntimeClasspath` لا يحتوي `project :feature:explore`, `project :core:designsystem`, `project :shared`, أو `activity-compose` بسبب pilot debug فقط.
- إذا قررت أثناء التنفيذ أن `Movit App Shell` سيدخل `src/main` كمسار إنتاجي، وثّق القرار بوضوح، وعندها يصبح `implementation` مقبولاً. القرار الموصى به الآن: shell pilot debug-only.

### F02 - Theme داخل feature screen

المشكلة:

`MovitExploreScreen` يلف نفسه بـ `MovitTheme`. هذا مناسب كتجربة منفردة، لكنه غير صحيح عندما يصبح لدينا App Shell؛ لأن الـ Theme يجب أن يكون عند root host.

المطلوب في Phase 03:

- اجعل `MovitExploreScreen` stateless/pure ولا يستدعي `MovitTheme`.
- اجعل `MovitExploreRoute` لا يفرض theme أيضاً، إلا إذا كان هناك preview/standalone wrapper واضح.
- طبّق `MovitTheme` في:
  - `MovitShellPilotActivity`
  - `MovitDesignSystemCatalogActivity`
  - أي root host لاحقاً
- تأكد أنه لا توجد nested theme داخل feature screens.

## خارج نطاق هذه المرحلة

- لا نقل لـ `TrainingActivity`.
- لا نقل لـ CameraX أو permissions أو overlay.
- لا نقل لـ MediaPipe/LiteRT/ONNX.
- لا تغيير `applicationId`.
- لا إعادة تسمية واسعة للـ packages القديمة.
- لا iOS production app.
- لا Navigation graph نهائي لكل التطبيق القديم.
- لا حذف لأي شاشة legacy.
- لا ترحيل فعلي إلى `com.android.kotlin.multiplatform.library` قبل AGP 9 / Gradle 9.1.

## مداخل المرحلة

قبل بدء التنفيذ يجب أن تكون Phase 02 وصلت إلى:

- `:app:assembleDebug` ناجح.
- `:shared:testDebugUnitTest` ناجح.
- `:core:designsystem:testDebugUnitTest` ناجح.
- `:feature:explore:testDebugUnitTest` ناجح.
- `:feature:explore:compileKotlinMetadata` ناجح أو `SKIPPED` بدون فشل.
- فشل `:app:testDebugUnitTest` إن وجد يجب أن يكون نفس فشل TensorFlowLite القديم فقط.

## مخرجات المرحلة

بنهاية Phase 03 يجب أن يكون لدينا:

- Phase 02 findings مغلقة.
- `MovitExploreScreen` بدون Theme داخلي.
- pilot dependencies لا تدخل release runtime إذا بقيت debug-only.
- App Shell جديد للـ Movit UI.
- Navigation bottom bar مبني على Material 3.
- `Explore` يعمل كأول tab حقيقي داخل الـ Shell.
- tabs أخرى placeholders منظمة وليست شاشات وهمية عشوائية.
- debug host واحد للـ shell:

```text
MovitShellPilotActivity
```

- اختبارات للـ shell state/navigation.
- build verification واضح.

## الجزء 1 - إصلاح Release Hygiene

### 1.1 تعديل dependencies في `:app`

إذا كان shell pilot داخل `app/src/debug`:

```kotlin
debugImplementation(project(":shared"))
debugImplementation(project(":core:designsystem"))
debugImplementation(project(":feature:explore"))
debugImplementation(libs.androidx.activity.compose)
debugImplementation(libs.compose.ui.tooling)
```

لا تستخدم `implementation` لهذه dependencies إلا لو يوجد code في `app/src/main` يحتاجها فعلاً.

### 1.2 تحقق release runtime

بعد التعديل شغّل:

```powershell
.\gradlew.bat --console=plain :app:dependencies --configuration releaseRuntimeClasspath
```

المقبول:

- لا يظهر `project :feature:explore`.
- لا يظهر `project :core:designsystem` بسبب pilot فقط.
- لا يظهر `project :shared` بسبب pilot فقط.
- لا يظهر `androidx.activity:activity-compose` بسبب pilot فقط.

لو ظهر أحدها، افهم سبب دخوله. إن كان بسبب dependency إنتاجية حقيقية، وثّق ذلك. إن كان بسبب pilot، صحح configuration.

## الجزء 2 - Theme Boundary

### 2.1 تعديل Explore

المطلوب:

- إزالة `MovitTheme { ... }` من `MovitExploreScreen`.
- `MovitExploreScreen` يستقبل `state`, `onEvent`, و`modifier` فقط.
- `MovitExploreRoute` مسؤول عن state/effects فقط، وليس theme.

الشكل المستهدف:

```kotlin
@Composable
fun MovitExploreScreen(
    state: MovitExploreUiState,
    onEvent: (MovitExploreEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitScaffold(...)
}
```

### 2.2 تطبيق Theme في root host

في debug activity:

```kotlin
setContent {
    MovitTheme {
        MovitExploreRoute(...)
    }
}
```

في shell:

```kotlin
setContent {
    MovitTheme {
        MovitAppShell(...)
    }
}
```

## الجزء 3 - App Shell Architecture

### 3.1 الموديول المقترح

الأفضل لهذه المرحلة إنشاء موديول KMP جديد:

```text
kmp-app/feature/shell/
```

السبب:

- الـ shell ليس جزءاً من design system؛ لأنه يعرف destinations وتجربة التطبيق.
- ليس مناسباً وضعه داخل `:app` إذا أردنا مشاركته لاحقاً مع iOS.
- يبقى مستقلاً عن legacy Android screens.

أضف في `settings.gradle.kts`:

```kotlin
include(":feature:shell")
```

### 3.2 هيكلة الملفات

```text
feature/shell/src/commonMain/kotlin/com/movit/feature/shell/
  MovitAppShell.kt
  MovitAppShellRoute.kt
  MovitAppShellState.kt
  MovitAppShellEvent.kt
  MovitAppDestination.kt
  MovitNavigationBar.kt
  MovitPlaceholderScreen.kt

feature/shell/src/commonTest/kotlin/com/movit/feature/shell/
  MovitAppShellStateTest.kt
```

### 3.3 Dependencies

`feature:shell` يحتاج:

```kotlin
implementation(project(":core:designsystem"))
implementation(project(":feature:explore"))
implementation(compose.runtime)
implementation(compose.foundation)
implementation(compose.material3)
implementation(compose.ui)
```

لا تضف network أو storage أو ML dependencies.

## الجزء 4 - Destinations

### 4.1 التبويبات الأولية

ابدأ بخمس destinations:

```text
Explore
Train
Programs
Reports
Profile
```

القرار:

- `Explore` = real screen من Phase 02.
- الباقي = placeholders منظمة تعرض title/subtitle/action فقط.

### 4.2 `MovitAppDestination`

الشكل المقترح:

```kotlin
enum class MovitAppDestination(
    val route: String,
    val label: String,
) {
    Explore("explore", "Explore"),
    Train("train", "Train"),
    Programs("programs", "Programs"),
    Reports("reports", "Reports"),
    Profile("profile", "Profile"),
}
```

ملاحظة:

- labels في هذه المرحلة يمكن أن تكون ثابتة.
- في مرحلة localization لاحقة ننقلها إلى resource abstraction مناسبة لـ KMP.
- لا تربطها بـ Android string resources داخل commonMain.

## الجزء 5 - Navigation Pattern

### 5.1 الاختيار لهذه المرحلة

لا تضف Navigation library الآن إلا إذا ظهرت حاجة حقيقية. استخدم state-based navigation بسيط:

```kotlin
data class MovitAppShellState(
    val selectedDestination: MovitAppDestination = MovitAppDestination.Explore,
)
```

السبب:

- لدينا tab واحد حقيقي فقط.
- إضافة Navigation graph الآن قد تكون ceremony أكثر من قيمة.
- عند وجود 2-3 features حقيقية، نقرر بين Compose Navigation Multiplatform أو router مخصص.

### 5.2 Event flow

```kotlin
sealed interface MovitAppShellEvent {
    data class DestinationSelected(val destination: MovitAppDestination) : MovitAppShellEvent
    data class ExploreItemSelected(val itemId: String) : MovitAppShellEvent
}
```

### 5.3 Bottom Navigation

استخدم Material 3:

```text
NavigationBar
NavigationBarItem
```

المطلوب:

- active/inactive states من `MaterialTheme.colorScheme`.
- labels واضحة.
- icons إن كان dependency جاهزاً، ويفضل `compose.materialIconsExtended` إن كان متاحاً في Compose Multiplatform setup.
- لو إضافة icons ستسبب تعقيد dependency، استخدم labels فقط في Phase 03 ووثّق إضافة icons كتحسين في Phase 04.

## الجزء 6 - Design System Additions

أضف فقط ما يحتاجه shell فعلياً.

### 6.1 مكونات مقترحة

داخل `:core:designsystem`:

```text
MovitNavigationBar
MovitNavigationItem
MovitPlaceholderState
MovitSnackMessage
```

أو إن كان الـ navigation مربوطاً بالـ destinations، أبقه داخل `feature:shell` ولا ترفعه للـ design system.

قاعدة القرار:

- component عام لا يعرف destinations => design system.
- component يعرف `MovitAppDestination` => feature:shell.

### 6.2 Catalog update

حدث `MovitComponentCatalogScreen` فقط لو أضفت component عام داخل design system.

المطلوب في catalog:

- Bottom navigation sample إن كان component عاماً.
- Placeholder state sample لو أضيف.
- Light/Dark preview ما زال يعمل.

## الجزء 7 - Android Debug Host

### 7.1 Activity جديدة

أضف:

```text
app/src/debug/java/com/movit/debug/MovitShellPilotActivity.kt
```

المسؤولية:

- root host فقط.
- `enableEdgeToEdge()`.
- `setContent`.
- `MovitTheme`.
- `MovitAppShellRoute`.

مثال:

```kotlin
class MovitShellPilotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovitTheme {
                MovitAppShellRoute()
            }
        }
    }
}
```

### 7.2 Manifest

سجلها في:

```text
app/src/debug/AndroidManifest.xml
```

```xml
<activity
    android:name="com.movit.debug.MovitShellPilotActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:theme="@style/Theme.Movit" />
```

### 7.3 أمر الفتح

```powershell
adb shell am start -n com.movit.androidApp/com.movit.debug.MovitShellPilotActivity
```

## الجزء 8 - UX Requirements

### 8.1 السلوك المطلوب

- يفتح shell على `Explore`.
- Bottom navigation ثابت وواضح.
- الضغط على `Train`, `Programs`, `Reports`, `Profile` يعرض placeholder محترم.
- الرجوع إلى `Explore` لا يعيد تحميل البيانات بشكل مزعج إن أمكن.
- الضغط على exercise داخل Explore يرسل event للـ shell ويعرض snackbar/toast مؤقت أو placeholder interaction.

### 8.2 شكل الـ placeholders

كل placeholder يجب أن يكون مفيداً بصرياً:

```text
Title
Short subtitle
Primary action disabled or no-op
Small status/coming soon copy
```

لا تستخدم نصوص طويلة تشرح الخطة داخل التطبيق. واجهة المستخدم يجب أن تبقى واجهة منتج، وليس مستند.

### 8.3 RTL / Arabic

المطلوب:

- لا تستخدم negative letter spacing.
- لا تفترض اتجاه LTR في layout.
- اختبر RTL sample في catalog.
- لا تستخدم strings عربية مشفرة في shell إلا لعينة preview واضحة.

## الجزء 9 - State Management

### 9.1 Shell controller

يمكن أن تبدأ بـ state holder بسيط بدون ViewModel:

```kotlin
class MovitAppShellController {
    val state: StateFlow<MovitAppShellState>
    fun onEvent(event: MovitAppShellEvent)
}
```

أو تستخدم `rememberSaveable` داخل route للـ selected destination فقط.

القرار الموصى به:

- استخدم controller بسيط إذا أردت testability.
- لا تضف DI framework في هذه المرحلة.
- لا تضف Android ViewModel داخل commonMain.

### 9.2 UDF contract

كل shell UI يجب أن يتبع:

```text
State -> UI
Event -> Controller
Effect -> Host
```

لا تجعل Bottom Navigation يغير state داخلياً بدون event واضح.

## الجزء 10 - Tests

### 10.1 Unit tests

في `feature:shell`:

- initial destination is `Explore`.
- selecting `Train` updates selected destination.
- selecting same destination is idempotent.
- Explore item selected emits effect أو يتم تسجيله حسب تصميم controller.

في `feature:explore`:

- أضف test أو static grep task بسيط إن أمكن للتأكد أن `MovitExploreScreen` لا يستدعي `MovitTheme`.

### 10.2 Build verification

شغّل:

```powershell
.\gradlew.bat --console=plain :app:assembleDebug
.\gradlew.bat --console=plain :shared:testDebugUnitTest
.\gradlew.bat --console=plain :core:designsystem:testDebugUnitTest
.\gradlew.bat --console=plain :feature:explore:testDebugUnitTest
.\gradlew.bat --console=plain :feature:shell:testDebugUnitTest
.\gradlew.bat --console=plain :feature:shell:compileKotlinMetadata
.\gradlew.bat --console=plain :feature:shell:compileDebugKotlinAndroid
```

ثم تحقق release runtime:

```powershell
.\gradlew.bat --console=plain :app:dependencies --configuration releaseRuntimeClasspath
```

اختياري إذا كان release build لا يحتاج signing خاص:

```powershell
.\gradlew.bat --console=plain :app:assembleRelease
```

### 10.3 Known legacy failure

` :app:testDebugUnitTest` قد يفشل بسبب TensorFlowLite `UnsatisfiedLinkError` القديم. هذا ليس blocker للمرحلة الثالثة بشرط:

- نفس 7 tests القديمة فقط.
- لا يظهر test failure جديد متعلق بـ Movit أو Compose أو shell.

## الجزء 11 - Visual QA

افتح:

```powershell
adb shell am start -n com.movit.androidApp/com.movit.debug.MovitShellPilotActivity
```

راجع بصرياً:

- Light mode.
- Dark mode إن أمكن.
- Explore tab.
- Placeholder tabs.
- Bottom navigation selected/unselected.
- scrolling في Explore لا يتداخل مع navigation bar.
- Arabic/RTL sample في catalog ما زال صحيحاً.

مطلوب تسليم screenshots:

```text
Shell - Explore
Shell - Train placeholder
Shell - Dark mode
Catalog - RTL sample
```

## معايير القبول

لا تعتبر Phase 03 مكتملة إلا إذا تحقق التالي:

- تم إغلاق F01 أو توثيق قرار production dependency بوضوح.
- تم إغلاق F02 وإزالة Theme الداخلي من `MovitExploreScreen`.
- `MovitShellPilotActivity` موجودة debug-only.
- `feature:shell` موجود أو تم توثيق سبب إبقاء shell داخل debug app مؤقتاً.
- `Explore` يعمل داخل shell كأول tab.
- باقي التبويبات placeholders منظمة.
- Bottom navigation يستخدم Material 3 roles.
- لا raw colors داخل feature:shell.
- لا business logic داخل Composables.
- tests الخاصة بـ shell ناجحة.
- أوامر Gradle الأساسية ناجحة.
- لا تغيير لـ `applicationId`.
- لا لمس لـ camera/ML/training session flow.

## ما أحتاجه عند الرجوع للمراجعة

عند تنفيذ Phase 03، أرسل:

- ملخص الملفات الجديدة/المعدلة.
- هل shell بقي debug-only أم دخل production path؟
- نتيجة:

```powershell
:app:assembleDebug
:shared:testDebugUnitTest
:core:designsystem:testDebugUnitTest
:feature:explore:testDebugUnitTest
:feature:shell:testDebugUnitTest
:feature:shell:compileKotlinMetadata
:feature:shell:compileDebugKotlinAndroid
```

- نتيجة فحص `releaseRuntimeClasspath` وهل ظهرت pilot dependencies أم لا.
- نتيجة `:app:testDebugUnitTest` إذا شغلتها.
- screenshots للـ shell.
- `git status --short`.

## ملاحظات مهمة

- `MovitDesignSystemCatalogActivity` يمكن أن تبقى مستقلة ومغلّفة بـ `MovitTheme` لأنها catalog root.
- `MovitExplorePilotActivity` يمكن إبقاؤها مؤقتاً، لكن بعد نجاح `MovitShellPilotActivity` تصبح أقل أهمية. لا تحذفها إلا بعد مراجعة.
- لا تدخل Navigation library قبل الحاجة. state-based navigation كافٍ لهذه المرحلة.
- أي component يعرف destinations لا ينتمي للـ design system.
- أي component عام لا يعرف التطبيق يمكن رفعه للـ design system.
- هذه المرحلة هي بداية شكل التطبيق الحقيقي، لكنها ما زالت pilot آمن قبل ربطها بالـ launcher.
