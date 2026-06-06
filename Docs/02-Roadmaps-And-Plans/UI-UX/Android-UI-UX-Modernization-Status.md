# Android UI/UX Modernization Status

آخر تحديث: 2026-06-03

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

الخطة ليست إعادة بناء كاملة مرة واحدة. التحويل يتم تدريجياً على مراحل مع الحفاظ على السلوك الحالي وربط الـ IDs الموجودة، ثم تحسين الهيكلة لاحقاً.

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

### Train

لم يبدأ بعد.

المطلوب:

- مراجعة `fragment_train.xml` و`TrainFragment`.
- إزالة hardcoded colors/textSize.
- تحويل cards إلى MaterialCardView/styles.
- الحفاظ على كل IDs.
- جعل الشاشة متوافقة مع النمط المرجعي:
  - Hero/CTA واضح.
  - exercise/program cards نظيفة.
  - quick actions مختصرة.
  - empty/loading states موحدة.

### Explore

لم يبدأ بعد.

المطلوب:

- مراجعة `fragment_explore.xml` و`ExploreFragment`.
- توحيد exercise/workout/program cards.
- تطبيق `ListCard` أو styles مكافئة.
- تحسين filters/chips.
- دعم light/dark بالكامل.

### Reports

لم يبدأ بعد.

المطلوب:

- مراجعة `fragment_history.xml`, reports fragments, report cards.
- توحيد report palette مع tokens.
- إزالة `report_*` المنفصلة تدريجياً أو جعلها aliases للـ system tokens.
- تحويل charts/cards إلى نمط clean dashboard.

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

### لم ينفذ بعد

لم يتم البدء في التحسين المعماري ضمن هذه الموجة. العمل الحالي مركز على UI/UX وDesign System.

## أهم المشاكل المعمارية التي ما زالت قائمة

- `ProgramSessionActivity` ما زالت كبيرة وتخلط UI + API + sync + state.
- `TrainingViewModel` ما زال ثقيلاً ويحتوي مسؤوليات كثيرة.
- بعض Activities تستدعي `ApiClient` مباشرة.
- لا يوجد DI واضح.
- بعض شاشات UI لا تزال programmatic.
- ما زال هناك تكرار في cards والـ layouts خارج المكونات المشتركة.

## التحقق حتى الآن

### تم

- `ReadLints` أظهر عدم وجود أخطاء على الملفات التي تم تعديلها أثناء العمل.
- المستخدم شغّل build من Android Studio ونجح قبل بعض التعديلات اللاحقة.
- بعد ظهور خطأ duplicate strings، تم إصلاحه.

### مطلوب من Android Studio

لا يوجد `gradlew` داخل المشروع ولا `gradle` متاح في PATH هنا، لذلك build النهائي يجب أن يتم من Android Studio.

بعد كل موجة تعديل، شغّل:

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

1. Build ومراجعة `Home`.
2. تحويل `Train`.
3. تحويل `Explore`.
4. تحويل `Reports`.
5. مراجعة `Exercise Detail` بعد build بصري.
6. تنظيف `Program Session` UI.
7. تحويل `Level Profile`.
8. البدء في refactor معماري تدريجي.

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
- [ ] Build نهائي بعد تحويل `Home`.
- [ ] مراجعة light/dark بصرياً.
- [ ] تحويل `Train`.
- [ ] تحويل `Explore`.
- [ ] تحويل `Reports`.
- [ ] تحويل `Program Session`.
- [ ] تحويل `Level Profile`.
- [ ] تنظيف Assessment UI.
- [ ] نقل API calls إلى repositories/use cases.
- [ ] تفكيك `ProgramSessionActivity`.
- [ ] تخفيف `TrainingViewModel`.
- [ ] إدخال DI أو factory منظم لاحقاً.
