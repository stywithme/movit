# Workout Domain Naming (SSOT)

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT in code** | `backend/src/domain/workout-contract.ts` |
| **Migration runbook** | `backend/docs/WORKOUT_DOMAIN_MIGRATION_RUNBOOK.md` |
| **Verified** | 2026-06-06 (P2 Docs sync) |

---

## لماذا توحيد المصطلحات؟

كان مصطلح **Session** يُستخدم لثلاثة مفاهيم مختلفة: كتالوج التمارين، كتلة البرنامج اليومية، وتنفيذ تمرين واحد. ذلك سبّب التباساً في API والوثائق والكود. العائلة الموحّدة أدناه هي المرجع لكل الوثائق في `Docs/`.

---

## جدول التعيين

| المفهوم | Prisma model | DB table | ملاحظة |
|---------|--------------|----------|--------|
| قالب تمرين (كتالوج) | `WorkoutTemplate` | `workout_templates` | كان `Workout` |
| تمرين داخل القالب | `WorkoutTemplateExercise` | `workout_template_exercises` | |
| كتلة برنامج مخططة | `PlannedWorkout` | `planned_workouts` | كان `ProgramSession` |
| عنصر داخل الكتلة | `PlannedWorkoutItem` | `planned_workout_items` | كان `ProgramSessionItem` |
| إكمال كتلة برنامج | `PlannedWorkoutReport` | `planned_workout_reports` | كان `ProgramSessionReport` |
| تنفيذ تمرين واحد | `WorkoutExecution` | `workout_executions` | كان `TrainingSession` |
| مقاييس التنفيذ | `WorkoutExecutionMetrics` | `workout_execution_metrics` | كان `SessionMetrics` |

---

## مسارات API (بعد الترحيل)

| القديم | الجديد |
|--------|--------|
| `GET/POST /workouts` | `GET/POST /workout-templates` |
| `GET /mobile/workouts` | `GET /mobile/workout-templates` |
| `POST /mobile/workout-executions` | `POST /mobile/workout-executions` |
| `POST /mobile/planned-workouts/explore` | `POST /mobile/workout-executions/explore` |
| `POST /mobile/planned-workouts/:id/start\|complete` | `POST /mobile/planned-workouts/:id/start\|complete` |
| `.../days/:dayId/planned-workouts` | `.../days/:dayId/planned-workouts` |
| `.../planned-workouts/:plannedWorkoutId` | `.../planned-workouts/:plannedWorkoutId` |
| `import-workout-template/:workoutTemplateId` | `import-workout-template/:workoutTemplateId` |

حقول JSON شائعة:

| القديم | الجديد |
|--------|--------|
| `sessions[]` (يوم برنامج) | `plannedWorkouts[]` |
| `sessionId` (كتلة برنامج) | `plannedWorkoutId` |
| `plannedWorkoutItemId` | `plannedWorkoutItemId` |
| `executionMetrics` | `executionMetrics` |
| `sessions[]` (explore upload) | `executions[]` |
| `plannedWorkoutReports` (sync) | `plannedWorkoutReports` |
| `totalWorkoutExecutions` / `sessions` (تاريخ تمرين) | `totalExecutions` / `executions` |

---

## استثناءات — لا تُسمّى Workout

| المصطلح | السياق |
|---------|--------|
| **Doctor / booking session** | حجوزات الطبيب، Google Meet، تقارير طبية |
| **Auth session** | تسجيل الدخول، refresh token |
| **Assessment session** | جلسة تقييم/Body Scan (`AssessmentSessionActivity`) |
| **Payment session** | بوابة الدفع |

---

## Android — أسماء شاشات ومكوّنات رئيسية

| القديم (وثائق) | الحالي في الكود |
|----------------|-----------------|
| `ProgramWorkoutActivity` | `ProgramWorkoutActivity` |
| `WorkoutRunActivity` | `WorkoutRunActivity` |
| `WorkoutTrainingEngine` | `WorkoutTrainingEngine` |
| `WorkoutSyncService` | `WorkoutSyncService` |
| `PlannedWorkoutReportActivity` | `WorkoutReportActivity` |

محرك **تشغيل تمرين متعدد التمارين** داخل `TrainingActivity` يُوثَّق أحياناً كـ *workout run* (تدفق UX) وليس كـ *planned workout*.

---

## Admin-Dashboard

| القديم | الجديد |
|--------|--------|
| `/admin/workouts` | `/admin/workout-templates` |
| محرر تمرينات البرنامج المخططة | `plannedWorkouts` / `PlannedWorkoutForm` |
| تقارير تحليلات التنفيذ | `/admin/analytics/workout-executions/[id]/report` |
