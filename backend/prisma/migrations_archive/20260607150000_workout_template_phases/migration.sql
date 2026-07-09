-- Workout template phases: reusable phase catalog + ordered phase instances.
-- Keeps workout_template_exercises.workoutTemplateId for backward-compatible flat exports.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS "workout_phases" (
  "id" TEXT NOT NULL,
  "slug" TEXT NOT NULL,
  "name" JSONB NOT NULL,
  "description" JSONB,
  "role" "WorkoutBlockRole" NOT NULL DEFAULT 'MAIN',
  "canSkip" BOOLEAN NOT NULL DEFAULT false,
  "canContinue" BOOLEAN NOT NULL DEFAULT true,
  "maxContinueTimeMs" INTEGER,
  "color" TEXT,
  "icon" TEXT,
  "isActive" BOOLEAN NOT NULL DEFAULT true,
  "sortOrder" INTEGER NOT NULL DEFAULT 0,
  "createdBy" TEXT,
  "updatedBy" TEXT,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deletedAt" TIMESTAMP(3),
  CONSTRAINT "workout_phases_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "workout_phases_slug_key" ON "workout_phases"("slug");
CREATE INDEX IF NOT EXISTS "workout_phases_role_idx" ON "workout_phases"("role");
CREATE INDEX IF NOT EXISTS "workout_phases_deletedAt_idx" ON "workout_phases"("deletedAt");

CREATE TABLE IF NOT EXISTS "workout_template_phases" (
  "id" TEXT NOT NULL,
  "workoutTemplateId" TEXT NOT NULL,
  "phaseId" TEXT NOT NULL,
  "sortOrder" INTEGER NOT NULL DEFAULT 0,
  "nameOverride" JSONB,
  "canSkipOverride" BOOLEAN,
  "canContinueOverride" BOOLEAN,
  "maxContinueTimeMsOverride" INTEGER,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "workout_template_phases_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "workout_template_phases_workoutTemplateId_fkey"
    FOREIGN KEY ("workoutTemplateId") REFERENCES "workout_templates"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "workout_template_phases_phaseId_fkey"
    FOREIGN KEY ("phaseId") REFERENCES "workout_phases"("id") ON UPDATE CASCADE
);

CREATE INDEX IF NOT EXISTS "workout_template_phases_workoutTemplateId_idx" ON "workout_template_phases"("workoutTemplateId");
CREATE INDEX IF NOT EXISTS "workout_template_phases_phaseId_idx" ON "workout_template_phases"("phaseId");

ALTER TABLE "workout_template_exercises"
  ADD COLUMN IF NOT EXISTS "workoutTemplatePhaseId" TEXT;

INSERT INTO "workout_phases" (
  "id", "slug", "name", "description", "role", "canSkip", "canContinue",
  "maxContinueTimeMs", "color", "icon", "sortOrder", "createdAt", "updatedAt"
)
VALUES
  (
    gen_random_uuid()::text,
    'warmup',
    jsonb_build_object('en', 'Warm-up', 'ar', 'إحماء'),
    jsonb_build_object('en', 'Prepare the body for training.', 'ar', 'تهيئة الجسم للتمرين.'),
    'WARMUP'::"WorkoutBlockRole",
    true,
    true,
    120000,
    '#f59e0b',
    'flame',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  ),
  (
    gen_random_uuid()::text,
    'main',
    jsonb_build_object('en', 'Main Workout', 'ar', 'التمرين الأساسي'),
    jsonb_build_object('en', 'Primary training work.', 'ar', 'الجزء الأساسي من التمرين.'),
    'MAIN'::"WorkoutBlockRole",
    false,
    true,
    NULL,
    '#2563eb',
    'dumbbell',
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  ),
  (
    gen_random_uuid()::text,
    'cooldown',
    jsonb_build_object('en', 'Cool-down', 'ar', 'تهدئة'),
    jsonb_build_object('en', 'Bring the body back down after training.', 'ar', 'تهدئة الجسم بعد التمرين.'),
    'COOLDOWN'::"WorkoutBlockRole",
    true,
    true,
    120000,
    '#10b981',
    'wind',
    20,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  )
ON CONFLICT ("slug") DO UPDATE SET
  "name" = EXCLUDED."name",
  "description" = EXCLUDED."description",
  "role" = EXCLUDED."role",
  "canSkip" = EXCLUDED."canSkip",
  "canContinue" = EXCLUDED."canContinue",
  "maxContinueTimeMs" = EXCLUDED."maxContinueTimeMs",
  "color" = EXCLUDED."color",
  "icon" = EXCLUDED."icon",
  "sortOrder" = EXCLUDED."sortOrder",
  "deletedAt" = NULL,
  "updatedAt" = CURRENT_TIMESTAMP;

INSERT INTO "workout_template_phases" (
  "id", "workoutTemplateId", "phaseId", "sortOrder", "createdAt", "updatedAt"
)
SELECT
  gen_random_uuid()::text,
  wt."id",
  wp."id",
  0,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM "workout_templates" wt
CROSS JOIN "workout_phases" wp
WHERE wp."slug" = 'main'
  AND NOT EXISTS (
    SELECT 1
    FROM "workout_template_phases" wtp
    WHERE wtp."workoutTemplateId" = wt."id"
  );

UPDATE "workout_template_exercises" wte
SET "workoutTemplatePhaseId" = wtp."id"
FROM "workout_template_phases" wtp
JOIN "workout_phases" wp ON wp."id" = wtp."phaseId"
WHERE wte."workoutTemplateId" = wtp."workoutTemplateId"
  AND wp."slug" = 'main'
  AND wte."workoutTemplatePhaseId" IS NULL;

ALTER TABLE "workout_template_exercises"
  ALTER COLUMN "workoutTemplatePhaseId" SET NOT NULL;

ALTER TABLE "workout_template_exercises"
  ADD CONSTRAINT "workout_template_exercises_workoutTemplatePhaseId_fkey"
  FOREIGN KEY ("workoutTemplatePhaseId") REFERENCES "workout_template_phases"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS "workout_template_exercises_workoutTemplatePhaseId_idx"
  ON "workout_template_exercises"("workoutTemplatePhaseId");

ALTER TABLE "workout_phases" ALTER COLUMN "updatedAt" DROP DEFAULT;
ALTER TABLE "workout_template_phases" ALTER COLUMN "updatedAt" DROP DEFAULT;
