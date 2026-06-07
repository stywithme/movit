-- Add missing reference constraints for string fields that are semantic foreign keys.
-- Historical rows with no matching target are preserved by nulling the optional pointer.

-- WorkoutExecution.workoutTemplateId -> workout_templates.id
UPDATE "workout_executions" we
SET "workoutTemplateId" = NULL
WHERE we."workoutTemplateId" IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM "workout_templates" wt
    WHERE wt."id" = we."workoutTemplateId"
  );

ALTER TABLE "workout_executions"
  ADD CONSTRAINT "workout_executions_workoutTemplateId_fkey"
  FOREIGN KEY ("workoutTemplateId")
  REFERENCES "workout_templates"("id")
  ON DELETE SET NULL ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS "workout_executions_workoutTemplateId_idx"
  ON "workout_executions"("workoutTemplateId");

-- ProgressionRule.exerciseSlug -> exercises.slug
UPDATE "progression_rules" pr
SET "exerciseSlug" = NULL
WHERE pr."exerciseSlug" IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM "exercises" e
    WHERE e."slug" = pr."exerciseSlug"
  );

ALTER TABLE "progression_rules"
  ADD CONSTRAINT "progression_rules_exerciseSlug_fkey"
  FOREIGN KEY ("exerciseSlug")
  REFERENCES "exercises"("slug")
  ON DELETE SET NULL ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS "progression_rules_exerciseSlug_idx"
  ON "progression_rules"("exerciseSlug");

-- ProgressionHistory.plannedWorkoutId -> planned_workouts.id
UPDATE "progression_history" ph
SET "plannedWorkoutId" = NULL
WHERE ph."plannedWorkoutId" IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM "planned_workouts" pw
    WHERE pw."id" = ph."plannedWorkoutId"
  );

ALTER TABLE "progression_history"
  ADD CONSTRAINT "progression_history_plannedWorkoutId_fkey"
  FOREIGN KEY ("plannedWorkoutId")
  REFERENCES "planned_workouts"("id")
  ON DELETE SET NULL ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS "progression_history_plannedWorkoutId_idx"
  ON "progression_history"("plannedWorkoutId");
