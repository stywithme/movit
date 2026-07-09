-- Per-set reps and rest arrays for workout template exercises.
-- Raw short lists are stored; runtime expands with last-value repeat.

ALTER TABLE "workout_template_exercises"
  ADD COLUMN IF NOT EXISTS "targetRepsPerSet" JSONB,
  ADD COLUMN IF NOT EXISTS "restBetweenSetsPerSetMs" JSONB;

UPDATE "workout_template_exercises"
SET "targetRepsPerSet" = jsonb_build_array("targetReps")
WHERE "targetReps" IS NOT NULL
  AND ("targetRepsPerSet" IS NULL OR "targetRepsPerSet" = '[]'::jsonb);

UPDATE "workout_template_exercises"
SET "restBetweenSetsPerSetMs" = jsonb_build_array("restBetweenSetsMs")
WHERE "restBetweenSetsMs" IS NOT NULL
  AND ("restBetweenSetsPerSetMs" IS NULL OR "restBetweenSetsPerSetMs" = '[]'::jsonb);

UPDATE "workout_template_exercises"
SET "weightPerSet" = jsonb_build_array("weightKg")
WHERE "weightKg" IS NOT NULL
  AND ("weightPerSet" IS NULL OR "weightPerSet" = '[]'::jsonb);
