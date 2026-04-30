-- Flexible training: onboarding weekdays; remove program pause columns.
ALTER TABLE "training_profiles" ADD COLUMN "trainingWeekdays" INTEGER[] NOT NULL DEFAULT ARRAY[]::INTEGER[];

ALTER TABLE "user_programs" DROP COLUMN IF EXISTS "pausedAt";
ALTER TABLE "user_programs" DROP COLUMN IF EXISTS "totalPausedDays";
