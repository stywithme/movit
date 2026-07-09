-- Workout domain rename: Session terminology removal (big-bang, production-safe renames)
-- Preserves all UUIDs and row data.

-- =============================================================================
-- 0. Ensure training_sessions has columns from schema drift before rename
-- =============================================================================
ALTER TABLE "training_sessions" ADD COLUMN IF NOT EXISTS "context" TEXT NOT NULL DEFAULT 'free';
ALTER TABLE "training_sessions" ADD COLUMN IF NOT EXISTS "groupId" TEXT;
ALTER TABLE "training_sessions" ADD COLUMN IF NOT EXISTS "workoutId" TEXT;
CREATE INDEX IF NOT EXISTS "training_sessions_groupId_idx" ON "training_sessions"("groupId");
CREATE INDEX IF NOT EXISTS "training_sessions_context_idx" ON "training_sessions"("context");

-- =============================================================================
-- 1. Rename enums
-- =============================================================================
ALTER TYPE "SessionRole" RENAME TO "WorkoutBlockRole";
ALTER TYPE "SessionItemIntent" RENAME TO "WorkoutItemIntent";

-- =============================================================================
-- 2. Catalog: workouts → workout_templates
-- =============================================================================
ALTER TABLE "workouts" RENAME TO "workout_templates";
ALTER TABLE "workout_exercises" RENAME TO "workout_template_exercises";
ALTER TABLE "workout_template_exercises" RENAME COLUMN "workoutId" TO "workoutTemplateId";

-- =============================================================================
-- 3. Program blocks: program_sessions → planned_workouts
-- =============================================================================
ALTER TABLE "program_sessions" RENAME TO "planned_workouts";

-- =============================================================================
-- 4. Program items: program_session_items → planned_workout_items
-- =============================================================================
ALTER TABLE "program_session_items" RENAME TO "planned_workout_items";
ALTER TABLE "planned_workout_items" RENAME COLUMN "sessionId" TO "plannedWorkoutId";
ALTER TABLE "planned_workout_items" RENAME COLUMN "sourceWorkoutId" TO "sourceWorkoutTemplateId";

-- =============================================================================
-- 5. Program reports: program_session_reports → planned_workout_reports
-- =============================================================================
ALTER TABLE "program_session_reports" RENAME TO "planned_workout_reports";
ALTER TABLE "planned_workout_reports" RENAME COLUMN "programSessionId" TO "plannedWorkoutId";

-- =============================================================================
-- 6. Executions: training_sessions → workout_executions
-- =============================================================================
ALTER TABLE "training_sessions" RENAME TO "workout_executions";
ALTER TABLE "workout_executions" RENAME COLUMN "groupId" TO "workoutGroupId";
ALTER TABLE "workout_executions" RENAME COLUMN "workoutId" TO "workoutTemplateId";

-- =============================================================================
-- 7. Execution metrics: session_metrics → workout_execution_metrics
-- =============================================================================
ALTER TABLE "session_metrics" RENAME TO "workout_execution_metrics";
ALTER TABLE "workout_execution_metrics" RENAME COLUMN "sessionId" TO "workoutExecutionId";

-- =============================================================================
-- 8. Rep metrics FK column
-- =============================================================================
ALTER TABLE "rep_metrics" RENAME COLUMN "sessionId" TO "workoutExecutionId";

-- =============================================================================
-- 9. User program progress & overrides
-- =============================================================================
ALTER TABLE "user_program_progress" RENAME COLUMN "sessionId" TO "plannedWorkoutId";
ALTER TABLE "user_program_overrides" RENAME COLUMN "sessionItemId" TO "plannedWorkoutItemId";

-- =============================================================================
-- 10. Progression history
-- =============================================================================
ALTER TABLE "progression_history" RENAME COLUMN "sessionId" TO "plannedWorkoutId";

-- =============================================================================
-- 11. Scalar renames on related tables
-- =============================================================================
ALTER TABLE "programs" RENAME COLUMN "weeklySessionTarget" TO "weeklyWorkoutTarget";
ALTER TABLE "programs" RENAME COLUMN "estimatedSessionMinutes" TO "estimatedWorkoutMinutes";

ALTER TABLE "users" RENAME COLUMN "totalWorkouts" TO "totalWorkoutExecutions";

ALTER TABLE "training_profiles" RENAME COLUMN "maxSessionMinutes" TO "maxWorkoutMinutes";

ALTER TABLE "levels" RENAME COLUMN "defaultSessionDurMin" TO "defaultWorkoutDurMin";
ALTER TABLE "levels" RENAME COLUMN "defaultSessionDurMax" TO "defaultWorkoutDurMax";

ALTER TABLE "plans" RENAME COLUMN "maxWorkoutsLimit" TO "maxWorkoutTemplatesLimit";
