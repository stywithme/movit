# Events Implementation Ticket

## الهدف

تذكرة تنفيذية لفريق البرمجة: إضافة طبقة **أحداث سلوكية (product analytics)** لقياس الـ funnel والـ retention (ملف 14)، منفصلة عن رفع مقاييس التمرين (`WorkoutExecution`) الموجود.

## الوضع الحالي (حقائق من الكود)

- **يوجد**: رفع *مقاييس التمرين* (`WorkoutExecutionUpload` عبر `AnalyticsStorage` + `WorkoutSyncService` / `MobileSyncApi`)، تقارير محلية (`ProgramWorkoutReportStore` / `PlannedWorkoutReport`)، وإحصائيات home (`HomeStatsData`: streak، avgFormScore، thisWeekExecutions، totalMinutes).
- **لا يوجد**: أي SDK تحليلات سلوكية (مفيش Firebase Analytics / Amplitude / Mixpanel)، ولا تتبّع funnel (onboarding/assessment/activation/retention).
- **الخلاصة**: عندنا "ماذا حدث داخل التمرين"، لكن ينقصنا "أين يتسرّب المستخدم في الرحلة".

## المطلوب

طبقة أحداث خفيفة، تحترم الخصوصية (لا فيديو، لا PII غير ضروري)، offline-first على نمط `PlannedWorkoutReportStore.PendingSyncEntry` (queue + retry)، ترسل لـ endpoint جديد (مثلاً `POST /api/mobile/events`).

### الأحداث ومكان إطلاقها

| الحدث | مكان الإطلاق (Activity / Class) |
| --- | --- |
| app_opened | `MainContainerActivity.onCreate` |
| onboarding_started / _completed | `OnboardingActivity` |
| safety_gate_completed | `PreScreeningActivity` (النتيجة) |
| assessment_started / _completed | `AssessmentSessionActivity` ← `AssessmentResultActivity` |
| level_assigned | `LevelProfileActivity` (ظهور المستوى) |
| plan_started | `PlanOverviewActivity` / أول `ProgramDayActivity` |
| today_workout_viewed | `HomeFragment` / `TrainFragment` (status=active) |
| workout_run_started | `WorkoutRunState` IDLE→SETUP_POSE |
| workout_first_rep | أول `RepCompletionSignal` (لحظة الـ Aha — مهم) |
| workout_completed | `WorkoutRunState`→COMPLETED (+ isCountedWorkout، formScore، reps، durationMs) |
| workout_abandoned | خروج قبل COMPLETED (+ at_state) |
| camera_setup_duration | من SETUP_POSE حتى COUNTDOWN |
| correction_shown | عند عرض تصحيح (`MobileMessageResolver` / feedback) |
| report_viewed | `WorkoutReportActivity.onCreate` |
| weekly_report_viewed | `WeeklyReportActivity.onCreate` |
| streak_updated | عند تحديث streak (HomeStats) |
| reassessment_completed | `AssessmentResultActivity` (إعادة تقييم) — إشارة تطور |

`day2_return` و `3 planned workouts / 7 days` يُحسبان سيرفر-سايد من `app_opened` + `workout_completed` والتواريخ.

التطور والاستمرار (المقياس الحقيقي، ملف 16) يُحسبان سيرفر-سايد: خط أساس (التقييم أو الأسبوع 1) + delta من `workout_completed` + `reassessment_completed`، عبر نافذة 14 يوم.

### الحقول لكل حدث

- `event` (String)، `ts` (epoch)، `anonUserId` (موجود من الـ auth)، `props` (Map خفيفة).
- ممنوع: أي صورة أو فيديو، أي بيانات حساسة.

### تعريف الإنجاز (Definition of Done)

- `AnalyticsTracker` مركزي بواجهة `track(event, props)`.
- offline-first: queue + retry (النمط موجود).
- `workout_completed` يحمل `isCountedWorkout` (ملف 16).
- شيت أو لوحة تقرأ الـ funnel من install حتى 3 تمارين/7 أيام (North Star).

### مبدأ

ابدأ بالحد الأدنى من الأحداث التي تقيس funnel ملف 14، لا كل حدث ممكن. أي حدث بلا قرار مرتبط = يؤجل.
