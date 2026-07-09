-- Difficulty is represented only by Level, but assigning a Level is optional.

ALTER TABLE "exercises"
  ALTER COLUMN "levelId" DROP NOT NULL;

ALTER TABLE "workout_templates"
  ALTER COLUMN "levelId" DROP NOT NULL;

ALTER TABLE "programs"
  ALTER COLUMN "levelMinId" DROP NOT NULL,
  ALTER COLUMN "levelMaxId" DROP NOT NULL;

ALTER TABLE "body_scan_results"
  ALTER COLUMN "levelId" DROP NOT NULL;
