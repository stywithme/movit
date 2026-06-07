-- PlannedWorkoutItem.type and WorkoutExecution.context → Prisma enums

CREATE TYPE "PlannedWorkoutItemType" AS ENUM ('exercise', 'rest');
CREATE TYPE "WorkoutExecutionContext" AS ENUM (
  'free',
  'program',
  'assessment',
  'explore_workout',
  'quick_start'
);

-- Normalize any legacy/invalid values before cast
UPDATE "planned_workout_items"
SET "type" = 'exercise'
WHERE "type" IS NULL OR "type" NOT IN ('exercise', 'rest');

UPDATE "workout_executions"
SET "context" = 'free'
WHERE "context" IS NULL
   OR "context" NOT IN ('free', 'program', 'assessment', 'explore_workout', 'quick_start');

ALTER TABLE "planned_workout_items"
  ALTER COLUMN "type" TYPE "PlannedWorkoutItemType"
  USING ("type"::"PlannedWorkoutItemType");

ALTER TABLE "workout_executions"
  ALTER COLUMN "context" TYPE "WorkoutExecutionContext"
  USING ("context"::"WorkoutExecutionContext");

ALTER TABLE "workout_executions"
  ALTER COLUMN "context" SET DEFAULT 'free';
