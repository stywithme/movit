-- Enforce workout_template_exercises.workoutTemplatePhaseId belongs to the same template
-- as workout_template_exercises.workoutTemplateId (composite FK).

-- Align any legacy mismatches (phase is authoritative).
UPDATE "workout_template_exercises" wte
SET "workoutTemplateId" = wtp."workoutTemplateId"
FROM "workout_template_phases" wtp
WHERE wtp."id" = wte."workoutTemplatePhaseId"
  AND wte."workoutTemplateId" IS DISTINCT FROM wtp."workoutTemplateId";

ALTER TABLE "workout_template_exercises"
  DROP CONSTRAINT IF EXISTS "workout_template_exercises_workoutTemplatePhaseId_fkey";

ALTER TABLE "workout_template_phases"
  ADD CONSTRAINT "workout_template_phases_id_workoutTemplateId_key"
  UNIQUE ("id", "workoutTemplateId");

ALTER TABLE "workout_template_exercises"
  ADD CONSTRAINT "workout_template_exercises_phase_template_fkey"
  FOREIGN KEY ("workoutTemplatePhaseId", "workoutTemplateId")
  REFERENCES "workout_template_phases" ("id", "workoutTemplateId")
  ON DELETE CASCADE ON UPDATE CASCADE;
