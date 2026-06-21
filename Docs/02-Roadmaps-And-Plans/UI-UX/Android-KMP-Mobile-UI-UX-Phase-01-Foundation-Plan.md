# Android / KMP Mobile UI/UX - Phase 01 Foundation Plan

آخر تحديث: 2026-06-08

## تعريف المرحلة

هذه هي أول مرحلة تنفيذية من خطة التحول الكبيرة. المقصود بها تأسيس فرع التحول والبنية الجديدة قبل نقل أي شاشة feature حقيقية.

في الخطة الكبيرة، هذه المرحلة تغطي عملياً:

- `Phase 0 - تأسيس فرع التحول`
- `Phase 1 - Design System + KMP skeleton`

السبب في دمجهما هنا أن فرع التحول، الـ KMP skeleton، وDesign System يجب أن يبدأوا معاً حتى يكون اتجاه المشروع صحيحاً من البداية.

## الهدف

إنشاء أساس تقني وتصميمي جديد يسمح ببناء Android وiOS لاحقاً من نفس المنطق ونفس Design System، مع استخدام التطبيق الحالي كمرجع للسلوك والـ APIs فقط، وليس كقيد على الواجهة أو الملفات.

نهاية هذه المرحلة لا تعني نقل أي شاشة إنتاجية. المطلوب فقط أن يصبح لدينا:

- فرع تحول واضح.
- Gradle structure قابل للتوسع.
- KMP module أولي.
- Compose Multiplatform / Material 3 Design System module.
- Android host بسيط لعرض component catalog.
- أول مجموعة `Pose*` components تعمل وتبنى.

## خارج نطاق هذه المرحلة

- لا ننقل `Home`, `Train`, `Explore`, أو أي شاشة إنتاجية.
- لا نلمس `TrainingActivity` أو camera/ML flow.
- لا نضيف iOS implementation كامل.
- لا نحذف Retrofit/Gson أو View/XML.
- لا نعيد تسمية `applicationId` أو package الرئيسي.
- لا نبدأ refactor واسع للـ repositories أو storage.

## قرارات المرحلة

- Compose Multiplatform هو مسار UI الجديد.
- Material 3 هو أساس السلوك والتصميم.
- كل مكونات الواجهة الجديدة تمر عبر `Pose*` wrappers.
- `View/XML` يبقى موجوداً فقط لأن التطبيق الحالي يستخدمه، وليس لأنه معيار المستقبل.
- على Windows، لا نلزم تشغيل iOS build. يتم تجهيز source sets أو conditional targets فقط، والتحقق الكامل من iOS يكون لاحقاً على macOS.

## المخرجات المطلوبة

### 1. فرع التحول

اسم مقترح:

```text
codex/kmp-mobile-foundation
```

المطلوب:

- إنشاء الفرع من آخر حالة مستقرة.
- تشغيل baseline build قبل أي تعديل.
- توثيق نتيجة baseline في ملاحظات التنفيذ.

أوامر مقترحة:

```powershell
cd kmp-app
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

### 2. Version catalog

إنشاء:

```text
kmp-app/gradle/libs.versions.toml
```

ونقل الإصدارات من:

```text
kmp-app/build.gradle.kts
kmp-app/app/build.gradle.kts
```

إلى version catalog تدريجياً.

الحد الأدنى المطلوب في هذه المرحلة:

- Android Gradle Plugin.
- Kotlin.
- Compose Multiplatform plugin.
- Kotlin Compose compiler plugin إذا احتاجته النسخة المستخدمة.
- AndroidX Activity Compose.
- Compose Material 3.
- Compose UI tooling/debug tooling.
- Koin لاحقاً يمكن إضافته في Phase 02، وليس إجبارياً هنا.
- Ktor/kotlinx.serialization لاحقاً، وليس إجبارياً هنا.

قاعدة مهمة: لا تختار أرقام versions عشوائياً. استخدم compatibility matrix الرسمي لـ Kotlin/Compose Multiplatform/AGP، وحافظ على عدم الرجوع للخلف عن الإصدارات الحالية إلا إذا كان هناك سبب واضح.

### 3. KMP skeleton

إنشاء موديول أولي:

```text
kmp-app/shared/
```

وتسجيله في:

```text
kmp-app/settings.gradle.kts
```

الهيكلة المطلوبة:

```text
shared/
  build.gradle.kts
  src/
    commonMain/kotlin/com/trainingvalidator/poc/shared/
      PlatformInfo.kt
      AppResult.kt
    commonTest/kotlin/com/trainingvalidator/poc/shared/
      AppResultTest.kt
    androidMain/kotlin/com/trainingvalidator/poc/shared/
      AndroidPlatformInfo.kt
```

ملاحظة namespace:

- استخدم `com.trainingvalidator.poc` مؤقتاً لتجنب rename مبكر.
- تغيير الهوية/package يتم لاحقاً عندما يستقر الاسم التجاري.

محتوى الموديول:

- لا dependencies ثقيلة.
- لا networking.
- لا storage.
- فقط إثبات أن `commonMain` و`androidMain` يعملان.

### 4. Design System module

إنشاء موديول:

```text
kmp-app/core/designsystem/
```

وتسجيله في:

```text
include(":core:designsystem")
```

الهيكلة المطلوبة:

```text
core/designsystem/
  build.gradle.kts
  src/
    commonMain/kotlin/com/trainingvalidator/poc/designsystem/
      PoseTheme.kt
      PoseColorScheme.kt
      PoseTypography.kt
      PoseShapes.kt
      PoseSpacing.kt
      PoseMotion.kt
      PoseThemeMode.kt
      components/
        PoseButton.kt
        PoseCard.kt
        PoseMetricTile.kt
        PoseSectionHeader.kt
        PoseEmptyState.kt
        PoseFilterChip.kt
      catalog/
        PoseComponentCatalogScreen.kt
    commonTest/kotlin/com/trainingvalidator/poc/designsystem/
      PoseTokenTest.kt
```

الأولوية هنا ليست كثرة المكونات، بل تثبيت الأساس بشكل صحيح.

### 5. Material 3 token mapping

يجب تحويل palette الحالية إلى Material 3 roles داخل `PoseColorScheme`.

الـ primitives:

```text
Aqua  = #8ECFE3
Lime  = #C4D489
Ink   = #282828
Coral = #E76D46
Mist  = #D8DCDF
```

الحد الأدنى للـ roles:

```text
primary
onPrimary
primaryContainer
onPrimaryContainer
secondary
onSecondary
secondaryContainer
onSecondaryContainer
tertiary
onTertiary
background
onBackground
surface
onSurface
surfaceVariant
onSurfaceVariant
outline
error
onError
```

المطلوب:

- Light scheme.
- Dark scheme.
- لا يوجد استخدام hex داخل components بعد تعريف tokens.
- لا تستخدم dynamic color كافتراضي في هذه المرحلة.

### 6. Typography / shape / spacing

اعتمد prototype scale الحالي كبداية، مع ربطه بأدوار Material 3:

```text
Display: 32 / 800
Title:   18 / 700
Body:    15 / 400
Label:   12 / 600
```

المطلوب:

- `PoseTypography` يحتوي roles واضحة.
- `PoseShapes` يحتوي radius scale.
- `PoseSpacing` يحتوي spacing scale.
- لا تعتمد على magic numbers داخل components.

مقياس مقترح:

```text
spacing.xs = 4.dp
spacing.sm = 8.dp
spacing.md = 12.dp
spacing.lg = 16.dp
spacing.xl = 24.dp
spacing.xxl = 32.dp

radius.sm = 8.dp
radius.md = 12.dp
radius.lg = 18.dp
radius.xl = 24.dp
radius.full = 999.dp
```

### 7. أول مكونات Pose

المكونات المطلوبة في هذه المرحلة:

- `PoseButton`
  - Filled
  - Tonal
  - Outlined
- `PoseCard`
  - Filled
  - Outlined
- `PoseMetricTile`
- `PoseSectionHeader`
- `PoseEmptyState`
- `PoseFilterChip`

قواعد المكونات:

- مبنية فوق Material 3 primitives قدر الإمكان.
- كلها تستخدم `PoseTheme`.
- لا تحتوي business logic.
- تقبل text/content/callbacks فقط.
- تدعم enabled/disabled على الأقل.
- تدعم dark mode تلقائياً.

### 8. Android Compose host

داخل `:app`، أضف host بسيط لعرض catalog.

خيار مقترح:

```text
app/src/debug/java/com/trainingvalidator/poc/debug/DesignSystemCatalogActivity.kt
app/src/debug/AndroidManifest.xml
```

أو أي مكان debug-only مناسب.

المطلوب:

- Activity تعرض `PoseComponentCatalogScreen`.
- لا تجعلها launcher الأساسي.
- لا تربطها بتدفق المستخدم الحالي.
- الهدف منها visual QA فقط.

### 9. Component catalog

`PoseComponentCatalogScreen` يجب أن يعرض:

- Light preview.
- Dark preview أو toggle بسيط.
- Buttons variants.
- Cards variants.
- Metric row/tile sample.
- Empty state sample.
- Filter chips sample.
- Palette swatches.
- Typography sample.

لا نحتاج pixel-perfect الآن، لكن يجب أن يظهر الاتجاه البصري العام بوضوح.

## ترتيب التنفيذ المقترح

1. إنشاء الفرع وتشغيل baseline build.
2. إضافة version catalog بدون تغيير سلوك التطبيق.
3. إضافة `:shared` KMP skeleton وتشغيل build.
4. إضافة `:core:designsystem`.
5. إضافة tokens/theme/typography/shapes/spacing.
6. إضافة أول `Pose*` components.
7. إضافة debug catalog host داخل Android.
8. تشغيل build/tests.
9. التقاط screenshots للـ catalog في Light/Dark إن أمكن.
10. إرسال النتائج للمراجعة قبل الانتقال للمرحلة التالية.

## أوامر التحقق المطلوبة

من داخل:

```powershell
cd kmp-app
```

شغل ما يلي حسب المتاح بعد التنفيذ:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :shared:compileKotlinMetadata
.\gradlew.bat :core:designsystem:compileKotlinMetadata
```

ولو أضفت Android target للموديولات:

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid
.\gradlew.bat :core:designsystem:compileDebugKotlinAndroid
```

إذا اختلفت أسماء tasks بسبب إعدادات KMP، شغل:

```powershell
.\gradlew.bat :shared:tasks
.\gradlew.bat :core:designsystem:tasks
```

ثم استخدم أقرب compile/test tasks متاحة.

## معايير القبول

لن ننتقل للمرحلة التالية إلا إذا تحقق الآتي:

- `:app:assembleDebug` ناجح.
- `:shared` موجود ويحتوي `commonMain` حقيقي.
- `:core:designsystem` موجود ويحتوي Compose Multiplatform theme.
- `PoseTheme` يدعم light/dark.
- أول مكونات `Pose*` موجودة وتستخدم tokens.
- لا توجد raw colors داخل `Pose*` components.
- لا توجد feature screen migration في هذه المرحلة.
- لا يوجد package/applicationId rename.
- Android catalog host يعمل أو على الأقل يبني بنجاح.
- version catalog لا يكسر dependencies الحالية.
- التعديلات محدودة في foundation ولا تخلط معها refactor للشاشات القديمة.

## ما أحتاجه منك عند الرجوع للمراجعة

أرسل لي:

- ملخص ما نفذته.
- أسماء الملفات/الموديولات التي أضفتها.
- نتيجة أوامر Gradle.
- أي errors أو warnings مهمة.
- screenshot من component catalog إن أمكن.
- `git status --short`.

سأراجع بعدها:

- هل البنية سليمة؟
- هل اختيار الموديولات صحيح؟
- هل Material 3 tokens متطبقة بشكل نظيف؟
- هل ننتقل إلى Phase 02 feature pilot أم نحتاج تصحيح foundation أولاً؟

