-- Unify program planned workouts with WorkoutTemplate (remove PlannedWorkout.role).

CREATE TYPE "WorkoutTemplateOrigin" AS ENUM ('STANDALONE', 'PROGRAM_EMBEDDED');

ALTER TABLE "workout_templates"
  ADD COLUMN IF NOT EXISTS "origin" "WorkoutTemplateOrigin" NOT NULL DEFAULT 'STANDALONE',
  ADD COLUMN IF NOT EXISTS "programId" TEXT;

ALTER TABLE "workout_templates"
  ADD CONSTRAINT "workout_templates_programId_fkey"
  FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS "workout_templates_origin_idx" ON "workout_templates"("origin");
CREATE INDEX IF NOT EXISTS "workout_templates_programId_idx" ON "workout_templates"("programId");

ALTER TABLE "planned_workouts"
  ADD COLUMN IF NOT EXISTS "workoutTemplateId" TEXT;

-- Backfill: each planned workout gets a PROGRAM_EMBEDDED template with one phase (legacy role).
DO $$
DECLARE
  pw RECORD;
  prog_id TEXT;
  template_id TEXT;
  phase_rec RECORD;
  template_phase_id TEXT;
  item RECORD;
  prev_wte_id TEXT;
  ex_sort INT;
BEGIN
  FOR pw IN SELECT * FROM "planned_workouts" WHERE "workoutTemplateId" IS NULL LOOP
    SELECT p."id"
    INTO prog_id
    FROM "program_days" d
    JOIN "program_weeks" wk ON wk."id" = d."weekId"
    JOIN "programs" p ON p."id" = wk."programId"
    WHERE d."id" = pw."dayId";

    template_id := gen_random_uuid()::text;

    INSERT INTO "workout_templates" (
      "id", "name", "slug", "difficulty", "status", "origin", "programId",
      "isFeatured", "createdAt", "updatedAt"
    ) VALUES (
      template_id,
      pw."name",
      'emb_' || replace(substr(pw."id", 1, 12), '-', '') || '_' || floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint,
      'beginner',
      'draft',
      'PROGRAM_EMBEDDED',
      prog_id,
      false,
      CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP
    );

    SELECT wp.*
    INTO phase_rec
    FROM "workout_phases" wp
    WHERE wp."slug" = CASE COALESCE(pw."role"::text, 'MAIN')
      WHEN 'WARMUP' THEN 'warmup'
      WHEN 'ACTIVATION' THEN 'warmup'
      WHEN 'COOLDOWN' THEN 'cooldown'
      ELSE 'main'
    END
      AND wp."deletedAt" IS NULL
    LIMIT 1;

    IF phase_rec."id" IS NULL THEN
      SELECT wp.* INTO phase_rec FROM "workout_phases" wp WHERE wp."slug" = 'main' AND wp."deletedAt" IS NULL LIMIT 1;
    END IF;

    template_phase_id := gen_random_uuid()::text;
    INSERT INTO "workout_template_phases" (
      "id", "workoutTemplateId", "phaseId", "sortOrder", "createdAt", "updatedAt"
    ) VALUES (
      template_phase_id, template_id, phase_rec."id", 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    prev_wte_id := NULL;
    ex_sort := 0;

    FOR item IN
      SELECT * FROM "planned_workout_items"
      WHERE "plannedWorkoutId" = pw."id"
      ORDER BY "sortOrder" ASC
    LOOP
      IF item."type" = 'rest' THEN
        IF prev_wte_id IS NOT NULL THEN
          UPDATE "workout_template_exercises"
          SET "restAfterExerciseMs" = COALESCE(item."restDurationMs", 60000)
          WHERE "id" = prev_wte_id;
        END IF;
      ELSIF item."type" = 'exercise' AND item."exerciseId" IS NOT NULL THEN
        prev_wte_id := gen_random_uuid()::text;
        INSERT INTO "workout_template_exercises" (
          "id", "workoutTemplateId", "workoutTemplatePhaseId", "exerciseId",
          "variantIndex", "difficulty", "targetReps", "targetDuration",
          "sets", "restBetweenSetsMs", "restAfterExerciseMs",
          "weightKg", "weightPerSet", "notes", "sortOrder", "createdAt"
        ) VALUES (
          prev_wte_id,
          template_id,
          template_phase_id,
          item."exerciseId",
          0,
          'beginner',
          item."targetReps",
          item."targetDuration",
          COALESCE(item."sets", 1),
          COALESCE(item."restBetweenSetsMs", 30000),
          60000,
          item."weightKg",
          item."weightPerSet",
          item."notes",
          ex_sort,
          CURRENT_TIMESTAMP
        );
        ex_sort := ex_sort + 1;
      END IF;
    END LOOP;

    UPDATE "planned_workouts" SET "workoutTemplateId" = template_id WHERE "id" = pw."id";
  END LOOP;
END $$;

-- Planned workouts with no rows yet still need a template shell.
DO $$
DECLARE
  pw RECORD;
  prog_id TEXT;
  template_id TEXT;
  phase_rec RECORD;
  template_phase_id TEXT;
BEGIN
  FOR pw IN SELECT * FROM "planned_workouts" WHERE "workoutTemplateId" IS NULL LOOP
    SELECT p."id"
    INTO prog_id
    FROM "program_days" d
    JOIN "program_weeks" wk ON wk."id" = d."weekId"
    JOIN "programs" p ON p."id" = wk."programId"
    WHERE d."id" = pw."dayId";

    template_id := gen_random_uuid()::text;

    INSERT INTO "workout_templates" (
      "id", "name", "slug", "difficulty", "status", "origin", "programId",
      "isFeatured", "createdAt", "updatedAt"
    ) VALUES (
      template_id,
      pw."name",
      'emb_' || replace(substr(pw."id", 1, 12), '-', '') || '_' || floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint,
      'beginner',
      'draft',
      'PROGRAM_EMBEDDED',
      prog_id,
      false,
      CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP
    );

    SELECT wp.* INTO phase_rec FROM "workout_phases" wp WHERE wp."slug" = 'main' AND wp."deletedAt" IS NULL LIMIT 1;

    template_phase_id := gen_random_uuid()::text;
    INSERT INTO "workout_template_phases" (
      "id", "workoutTemplateId", "phaseId", "sortOrder", "createdAt", "updatedAt"
    ) VALUES (
      template_phase_id, template_id, phase_rec."id", 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    UPDATE "planned_workouts" SET "workoutTemplateId" = template_id WHERE "id" = pw."id";
  END LOOP;
END $$;

ALTER TABLE "planned_workouts"
  ALTER COLUMN "workoutTemplateId" SET NOT NULL;

ALTER TABLE "planned_workouts"
  ADD CONSTRAINT "planned_workouts_workoutTemplateId_fkey"
  FOREIGN KEY ("workoutTemplateId") REFERENCES "workout_templates"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS "planned_workouts_workoutTemplateId_idx" ON "planned_workouts"("workoutTemplateId");

ALTER TABLE "planned_workouts" DROP COLUMN IF EXISTS "role";

ALTER TABLE "user_program_overrides"
  ADD COLUMN IF NOT EXISTS "workoutTemplateExerciseId" TEXT;

ALTER TABLE "user_program_overrides"
  ALTER COLUMN "plannedWorkoutItemId" DROP NOT NULL;

-- Map exercise overrides to template exercises (match by exercise + order within planned workout).
WITH ranked_items AS (
  SELECT
    pwi."id" AS item_id,
    pwi."plannedWorkoutId",
    pwi."exerciseId",
    ROW_NUMBER() OVER (PARTITION BY pwi."plannedWorkoutId" ORDER BY pwi."sortOrder") - 1 AS ex_ord
  FROM "planned_workout_items" pwi
  WHERE pwi."type" = 'exercise' AND pwi."exerciseId" IS NOT NULL
),
ranked_wte AS (
  SELECT
    wte."id" AS wte_id,
    pw."id" AS planned_workout_id,
    wte."exerciseId",
    ROW_NUMBER() OVER (PARTITION BY pw."id" ORDER BY wte."sortOrder") - 1 AS ex_ord
  FROM "workout_template_exercises" wte
  JOIN "planned_workouts" pw ON pw."workoutTemplateId" = wte."workoutTemplateId"
)
UPDATE "user_program_overrides" o
SET "workoutTemplateExerciseId" = rw.wte_id
FROM ranked_items ri
JOIN "planned_workouts" pw ON pw."id" = ri."plannedWorkoutId"
JOIN ranked_wte rw ON rw.planned_workout_id = pw."id"
  AND rw."exerciseId" = ri."exerciseId"
  AND rw.ex_ord = ri.ex_ord
WHERE o."plannedWorkoutItemId" = ri.item_id
  AND o."workoutTemplateExerciseId" IS NULL;

ALTER TABLE "user_program_overrides"
  ADD CONSTRAINT "user_program_overrides_workoutTemplateExerciseId_fkey"
  FOREIGN KEY ("workoutTemplateExerciseId") REFERENCES "workout_template_exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS "user_program_overrides_workoutTemplateExerciseId_idx"
  ON "user_program_overrides"("workoutTemplateExerciseId");
