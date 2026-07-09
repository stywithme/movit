-- UserProgramOverride: exactly one target key (legacy item OR template exercise).

-- Prefer template exercise when both were populated during migration.
UPDATE "user_program_overrides"
SET "plannedWorkoutItemId" = NULL
WHERE "plannedWorkoutItemId" IS NOT NULL
  AND "workoutTemplateExerciseId" IS NOT NULL;

-- Remove invalid rows with no target.
DELETE FROM "user_program_overrides"
WHERE "plannedWorkoutItemId" IS NULL
  AND "workoutTemplateExerciseId" IS NULL;

ALTER TABLE "user_program_overrides"
  ADD CONSTRAINT "user_program_overrides_target_xor_chk"
  CHECK (
    (
      CASE WHEN "plannedWorkoutItemId" IS NOT NULL THEN 1 ELSE 0 END +
      CASE WHEN "workoutTemplateExerciseId" IS NOT NULL THEN 1 ELSE 0 END
    ) = 1
  );
