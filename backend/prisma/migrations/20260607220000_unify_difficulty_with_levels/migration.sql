-- Unify all content/assessment/program difficulty semantics through levels.

ALTER TABLE "exercises" ADD COLUMN IF NOT EXISTS "levelId" TEXT;
ALTER TABLE "workout_templates" ADD COLUMN IF NOT EXISTS "levelId" TEXT;
ALTER TABLE "programs" ADD COLUMN IF NOT EXISTS "levelMinId" TEXT;
ALTER TABLE "programs" ADD COLUMN IF NOT EXISTS "levelMaxId" TEXT;
ALTER TABLE "body_scan_results" ADD COLUMN IF NOT EXISTS "levelId" TEXT;

-- Exercises did not have a first-class difficulty; use foundation as the baseline.
UPDATE "exercises" e
SET "levelId" = l."id"
FROM "levels" l
WHERE e."levelId" IS NULL
  AND l."number" = 1;

-- Workout difficulty enum -> Level.
UPDATE "workout_templates" wt
SET "levelId" = l."id"
FROM "levels" l
WHERE wt."levelId" IS NULL
  AND l."number" = CASE wt."difficulty"::text
    WHEN 'beginner' THEN 1
    WHEN 'intermediate' THEN 3
    WHEN 'advanced' THEN 5
    ELSE 1
  END;

-- Program numeric level range -> Level rows.
UPDATE "programs" p
SET "levelMinId" = l."id"
FROM "levels" l
WHERE p."levelMinId" IS NULL
  AND l."number" = p."levelRangeMin";

UPDATE "programs" p
SET "levelMaxId" = l."id"
FROM "levels" l
WHERE p."levelMaxId" IS NULL
  AND l."number" = p."levelRangeMax";

UPDATE "programs" p
SET "levelMinId" = l."id"
FROM "levels" l
WHERE p."levelMinId" IS NULL
  AND l."number" = 1;

UPDATE "programs" p
SET "levelMaxId" = l."id"
FROM "levels" l
WHERE p."levelMaxId" IS NULL
  AND l."number" = 5;

-- Assessment fitness level / score -> Level.
UPDATE "body_scan_results" b
SET "levelId" = l."id"
FROM "levels" l
WHERE b."levelId" IS NULL
  AND l."number" = CASE lower(b."fitnessLevel")
    WHEN 'needs_rehab' THEN 1
    WHEN 'limited' THEN 2
    WHEN 'average' THEN 3
    WHEN 'good' THEN 4
    WHEN 'excellent' THEN 5
    ELSE NULL
  END;

UPDATE "body_scan_results" b
SET "levelId" = l."id"
FROM "levels" l
WHERE b."levelId" IS NULL
  AND b."bodyScore" >= l."entryThreshold"
  AND (l."maxThreshold" IS NULL OR b."bodyScore" <= l."maxThreshold");

UPDATE "body_scan_results" b
SET "levelId" = l."id"
FROM "levels" l
WHERE b."levelId" IS NULL
  AND l."number" = 1;

ALTER TABLE "exercises" ALTER COLUMN "levelId" SET NOT NULL;
ALTER TABLE "workout_templates" ALTER COLUMN "levelId" SET NOT NULL;
ALTER TABLE "programs" ALTER COLUMN "levelMinId" SET NOT NULL;
ALTER TABLE "programs" ALTER COLUMN "levelMaxId" SET NOT NULL;
ALTER TABLE "body_scan_results" ALTER COLUMN "levelId" SET NOT NULL;

ALTER TABLE "exercises"
  ADD CONSTRAINT "exercises_levelId_fkey"
  FOREIGN KEY ("levelId") REFERENCES "levels"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "workout_templates"
  ADD CONSTRAINT "workout_templates_levelId_fkey"
  FOREIGN KEY ("levelId") REFERENCES "levels"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "programs"
  ADD CONSTRAINT "programs_levelMinId_fkey"
  FOREIGN KEY ("levelMinId") REFERENCES "levels"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "programs"
  ADD CONSTRAINT "programs_levelMaxId_fkey"
  FOREIGN KEY ("levelMaxId") REFERENCES "levels"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "body_scan_results"
  ADD CONSTRAINT "body_scan_results_levelId_fkey"
  FOREIGN KEY ("levelId") REFERENCES "levels"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS "exercises_levelId_idx" ON "exercises"("levelId");
CREATE INDEX IF NOT EXISTS "workout_templates_levelId_idx" ON "workout_templates"("levelId");
CREATE INDEX IF NOT EXISTS "programs_levelMinId_idx" ON "programs"("levelMinId");
CREATE INDEX IF NOT EXISTS "programs_levelMaxId_idx" ON "programs"("levelMaxId");
CREATE INDEX IF NOT EXISTS "body_scan_results_levelId_idx" ON "body_scan_results"("levelId");

DROP INDEX IF EXISTS "programs_levelRangeMin_levelRangeMax_idx";

ALTER TABLE "workout_template_exercises" DROP COLUMN IF EXISTS "difficulty";
ALTER TABLE "workout_templates" DROP COLUMN IF EXISTS "difficulty";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "levelRangeMin";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "levelRangeMax";
ALTER TABLE "body_scan_results" DROP COLUMN IF EXISTS "fitnessLevel";

DROP TYPE IF EXISTS "Difficulty";
