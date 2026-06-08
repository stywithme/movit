# Train Page Modernization Spec

آخر تحديث: 2026-06-08

## Implementation Status

- تم تنفيذ أول نسخة KMP من صفحة Train داخل `android-poc/feature/train`.
- تم ربطها داخل `feature:shell` بدلاً من placeholder الخاص بـ `MovitAppDestination.Train`.
- التنفيذ الحالي يعتمد على fixture/fake repository آمن على Android وiOS؛ الربط الحقيقي مع API أو legacy Retrofit مؤجل حتى قرار bridge منفصل.
- CTA الخاص ببدء التدريب لا يفتح camera/session في هذه المرحلة، ويصدر effect يتعامل معه shell برسالة واضحة.
- تحقق Gradle الحالي يغطي `feature:train`, `feature:shell`, Android debug assemble، وKMP iOS simulator compile/link task من بيئة Windows. يبقى Xcode Simulator visual smoke على Mac هو خطوة القبول البصرية التالية.

## Current Implementation

- المدخل القديم: `TrainFragment` داخل legacy Android `:app`.
- الملفات الأساسية:
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/train/TrainFragment.kt`
  - `android-poc/app/src/main/res/layout/fragment_train.xml`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/programs/ProgramWorkoutActivity.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/programs/ProgramDetailActivity.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/programs/ProgramListActivity.kt`
  - `android-poc/app/src/main/java/com/trainingvalidator/poc/ui/programs/WeeklyReportActivity.kt`
- السلوك الحالي يعتمد على `HomeRepository.trainMode` عندما يكون متاحاً، ثم fallback إلى `ProgramRepository`.
- الحالات القديمة المهمة:
  - لا يوجد assessment أو لا توجد plan.
  - active program مع workout اليوم.
  - rest day.
  - day complete.
  - program complete.
- الأفعال القديمة:
  - بدء workout يفتح `ProgramWorkoutActivity`.
  - browse programs يفتح قائمة/تفاصيل البرامج.
  - view reports يفتح weekly/report screens.
  - avatar يفتح profile.

## User Goals

- الهدف الرئيسي: معرفة تدريب اليوم وبدء الخطوة التالية بثقة.
- الأهداف الثانوية:
  - فهم موقعه داخل البرنامج.
  - معرفة ما إذا كان اليوم تدريباً أو راحة.
  - رؤية تقدم الأسبوع بشكل سريع.
  - الوصول إلى Explore أو Reports بدون تشويش.
- حالات الفشل يجب أن تكون قابلة للاسترداد عبر retry، وليست شاشة فارغة.

## Content Inventory

- Program name, level, week/day position.
- Progress percent, streak, days trained, grade/score.
- Today workout title, exercise count, duration, focus.
- Week preview.
- Readiness/guidance.
- Quick actions: Explore, Reports, preferences لاحقاً.

## UX Target

- الصفحة تبدأ بسياق واضح: `Train` + subtitle قصير.
- أول محتوى فعلي يجيب: هل لدي تدريب اليوم؟
- CTA رئيسي واحد حسب الحالة:
  - active: `Start workout`.
  - no plan: `Explore programs`.
  - rest day: `Explore light workout`.
  - completed: `View report`.
- لا يتم فتح camera/session في Phase 05؛ `Start workout` يطلق effect فقط، والـ shell يعرض رسالة.

## Layout Spec

- Compact:
  - single vertical scroll.
  - `MovitScaffold`.
  - status/program card.
  - today card.
  - week preview.
  - readiness/guidance.
  - quick actions.
- Medium/expanded لاحقاً:
  - main pane: today workout.
  - supporting pane: week/readiness/report.
- bottom navigation من shell ولا يغطي المحتوى.

## Visual Spec

- كل الألوان من `MaterialTheme.colorScheme`.
- كل spacing من `MovitSpacing`.
- لا raw colors، لا nested cards، لا stock imagery.
- استخدم cards هادئة مع hierarchy واضح، وprogress/status surfaces بدون تحويل الصفحة للون واحد.
- النصوص يجب أن تتحمل Arabic/RTL لاحقاً وfont scaling.

## Implementation Plan

- إنشاء `:feature:train` KMP module.
- استخدام:
  - `MovitTrainRoute`
  - `MovitTrainScreen`
  - `MovitTrainViewModel`
  - `MovitTrainUiState`
  - `MovitTrainEvent`
  - `MovitTrainEffect`
  - `TrainRepository`
  - `FakeTrainRepository`
- البداية fake-first مع حالات:
  - active plan.
  - no plan.
  - rest day.
  - completed today.
  - program complete.
  - error via repository failure.
- ربط Train داخل `feature:shell`.
- iOS يستخدم نفس fake data عبر `iosMain` إلى أن يتم إدخال Ktor أو bridge مشترك.

## Acceptance Criteria

- `feature:train` موجود ومضاف إلى Gradle.
- Train tab لم يعد placeholder داخل shell.
- `MovitTrainScreen` لا يلف نفسه بـ `MovitTheme`.
- `Start workout` لا يفتح camera/session/ML.
- الحالات الأساسية ممثلة في tests أو preview data.
- shell يترجم effects:
  - `OpenExplore` -> Explore tab.
  - `OpenReports` -> Reports tab.
  - `OpenSessionPreview` -> snackbar/message.
- Android tests الخاصة بـ Train/Shell ناجحة.
- iOS framework link يظل قابلاً للتشغيل بعد الربط.
