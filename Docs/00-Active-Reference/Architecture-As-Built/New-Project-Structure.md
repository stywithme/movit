# New Project Structure (Android POC)

## الهدف من إعادة الهيكلة

الهيكلة الجديدة مبنية على **Feature-First Architecture** بدل التجميع حسب النوع فقط (كل Activities مع بعض، كل Fragments مع بعض).
الهدف هو:

- تقليل الترابط غير الضروري بين الشاشات.
- تسهيل التوسعة مستقبلا (إضافة Features جديدة بدون فوضى).
- توضيح مسؤولية كل Package بشكل مباشر.
- تحسين قابلية الصيانة والاختبار.

---

## مسار الطبقة الجديدة للـ UI

المسار الأساسي:

`kmp-app/app/src/main/java/com/trainingvalidator/poc/ui`

### التقسيم الحالي

- `ui/home`
  - `HomeFragment`
- `ui/train`
  - `TrainFragment`
  - `TrainingActivity`
  - `PreWorkoutActivity`
  - `PostWorkoutActivity`
- `ui/explore`
  - `ExploreFragment`
- `ui/reports`
  - `HistoryFragment` (Reports Hub)
  - `ReportsOverviewFragment`
  - `ReportsExercisesFragment`
  - `ReportsTrendsFragment`
  - `ReportsRecordsFragment`
  - `ReportsHubViewModel`
- `ui/programs`
  - `ProgramListActivity`
  - `ProgramDetailActivity`
  - `ProgramDayActivity`
  - `ProgramWorkoutActivity`
  - `PlannedWorkoutReportActivity`
  - `WeeklyReportActivity`
  - `PlanOverviewActivity`
  - `ProgramDetailViewModel`
  - `PlanOverviewViewModel`
- `ui/exercises`
  - `ExerciseListActivity`
  - `ExerciseDetailActivity`
  - `ExerciseHistoryActivity`
  - `ExercisesFragment`
- `ui/workouts`
  - `WorkoutListActivity`
  - `WorkoutDetailActivity`
- `ui/level`
  - `LevelProfileActivity`
  - `LevelProfileViewModel`
- `ui/main`
  - `MainContainerActivity` (Bottom Navigation container)
- `ui/utils`
  - `LocaleExt.kt` (shared locale access)

> ملاحظة: `ui/training` ما زالت تحتوي منطق التدريب المساعد (engine-related UI helpers) وتستخدم من `TrainingActivity`.

---

## المبادئ التي تم تطبيقها

## 1) Feature Boundaries واضحة

كل Feature لها Package خاص بها، ومعها الشاشات والـ ViewModels الخاصة بها.
هذا يقلل التنقل العشوائي داخل المشروع.

## 2) ViewModel-driven state

تم نقل منطق تحميل البيانات وحالات الـ UI من Activity/Fragment إلى ViewModel في الأجزاء الأساسية:

- `ProgramDetailViewModel`
- `PlanOverviewViewModel`
- `LevelProfileViewModel`
- `ReportsHubViewModel`

الـ UI أصبح مسؤول عن:

- Render فقط.
- التعامل مع User Interaction.
- مراقبة الحالة عبر Flows/Lifecycle.

## 3) Shared state في Reports Hub

بدل manual adapter callback بين `HistoryFragment` والـ tabs:

- `HistoryFragment` يطلب البيانات من `ReportsHubViewModel`.
- كل tab (`Overview/Exercises/Trends/Records`) يراقب نفس الـ ViewModel عبر `activityViewModels()`.
- تحميل البيانات يتم مرة واحدة ويُشارك على كل التبويبات.

## 4) Locale access موحد

بدل تكرار `getCurrentLanguage()` في ملفات كثيرة:

- تم توحيدها في `ui/utils/LocaleExt.kt` عبر `currentLanguage`.
- هذا يقلل التكرار ويمنع اختلاف سلوك اللغة بين الشاشات.

---

## AndroidManifest alignment

تم تحديث الـ Manifest ليعكس المسارات الجديدة للـ Activities، مثال:

- `.ui.programs.ProgramDetailActivity`
- `.ui.train.TrainingActivity`
- `.ui.exercises.ExerciseDetailActivity`
- `.ui.level.LevelProfileActivity`

وبالتالي التنقل عبر Intents بقي متوافق مع الهيكلة الجديدة.

---

## Design + i18n + accessibility (بعد التعديلات)

- دعم Day/Night mode أصبح منظم مع `values-night`.
- استبدال نصوص hardcoded بموارد `strings.xml` و`values-ar/strings.xml`.
- تحسينات Accessibility (content descriptions + touch targets) مطبقة في الشاشات الأساسية.
- استبدال ألوان hardcoded بـ color tokens.

---

## Flow سريع للـ App (UI Layer)

1. `SplashActivity` يحدد مسار البداية.
2. `MainContainerActivity` يدير التنقل بين Tabs الرئيسية.
3. كل Tab يوجه المستخدم إلى Feature package الخاص بها.
4. الشاشات الثقيلة تعتمد ViewModel للبيانات والحالات.
5. التقارير تعتمد shared ViewModel لضمان مصدر بيانات واحد.

---

## إرشادات العمل مستقبلا

- أي شاشة جديدة توضع داخل Feature package المناسب لها.
- إذا Feature جديدة بالكامل: أنشئ package جديد تحت `ui/`.
- لا تضع logic ثقيل داخل Activity/Fragment؛ انقله إلى ViewModel/Repository.
- أي نص جديد يجب أن يكون في Resources (EN + AR).
- أي لون جديد يمر عبر color tokens وليس hex مباشر في layout.

---

## الخلاصة

الهيكلة الحالية أصبحت:

- أوضح في المسؤوليات.
- أسهل في الصيانة.
- أفضل في التوسعة.
- أكثر ثباتا من ناحية lifecycle/state management.

وده يجعل التطوير القادم أسرع مع أخطاء أقل بإذن الله.

