-- Blueprint Phase 1: enums, TrainingProfile, UserProgramOverride, Program/User fields

-- CreateEnum
CREATE TYPE "ProgramType" AS ENUM ('SYSTEM', 'COACH', 'CUSTOM');
CREATE TYPE "ProgramDomain" AS ENUM ('TRAINING', 'MOBILITY', 'THERAPEUTIC');
CREATE TYPE "TrainingGoal" AS ENUM ('STRENGTH', 'HYPERTROPHY', 'POWER', 'GENERAL_HEALTH');
CREATE TYPE "WeekType" AS ENUM ('NORMAL', 'DELOAD');
CREATE TYPE "SessionItemRole" AS ENUM ('WARMUP', 'ACTIVATION', 'MAIN', 'ACCESSORY', 'CORRECTIVE', 'COOLDOWN', 'TEST');
CREATE TYPE "SessionItemIntent" AS ENUM ('STANDARD', 'POWER', 'ECCENTRIC', 'VELOCITY_BASED');
CREATE TYPE "MovementPattern" AS ENUM ('SQUAT', 'HINGE', 'LUNGE', 'PUSH_HORIZONTAL', 'PUSH_VERTICAL', 'PULL_HORIZONTAL', 'PULL_VERTICAL', 'CARRY', 'ROTATION', 'GAIT', 'JUMP_LAND', 'CORE_BRACE', 'MOBILITY_DRILL', 'OTHER');
CREATE TYPE "LoadCapability" AS ENUM ('BODYWEIGHT_ONLY', 'EXTERNAL_LOAD_OPTIONAL', 'EXTERNAL_LOAD_REQUIRED');
CREATE TYPE "OverrideType" AS ENUM ('REPLACE_EXERCISE', 'ADJUST_PRESCRIPTION', 'SKIP_ITEM', 'ADD_ITEM');
CREATE TYPE "OverrideReason" AS ENUM ('PAIN_AVOIDANCE', 'EQUIPMENT_UNAVAILABLE', 'TIME_CONSTRAINT', 'PREFERENCE', 'COACH_RECOMMENDATION', 'OTHER');
CREATE TYPE "OverrideAppliedBy" AS ENUM ('USER', 'COACH');

-- AlterTable users
ALTER TABLE "users" ADD COLUMN "trainingGoal" "TrainingGoal" NOT NULL DEFAULT 'GENERAL_HEALTH';

-- AlterTable programs: replace legacy "type" with programType + programDomain; rename criteria columns
DROP INDEX IF EXISTS "programs_type_idx";

ALTER TABLE "programs" RENAME COLUMN "entryCriteria" TO "entryRecommendations";
ALTER TABLE "programs" RENAME COLUMN "exitCriteria" TO "exitRecommendations";

ALTER TABLE "programs" ADD COLUMN "targetEquipment" JSONB;
ALTER TABLE "programs" ADD COLUMN "programType" "ProgramType" NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE "programs" ADD COLUMN "programDomain" "ProgramDomain" NOT NULL DEFAULT 'TRAINING';
ALTER TABLE "programs" ADD COLUMN "trainingGoal" "TrainingGoal";
ALTER TABLE "programs" ADD COLUMN "autoAssignable" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "programs" ADD COLUMN "version" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE "programs" ADD COLUMN "ownerId" TEXT;
ALTER TABLE "programs" ADD COLUMN "forkedFromId" TEXT;
ALTER TABLE "programs" ADD COLUMN "coachingNotes" JSONB;
ALTER TABLE "programs" ADD COLUMN "weeklySessionTarget" INTEGER;
ALTER TABLE "programs" ADD COLUMN "estimatedSessionMinutes" INTEGER;

UPDATE "programs" SET "programDomain" = CASE "type"
  WHEN 'mobility' THEN 'MOBILITY'::"ProgramDomain"
  WHEN 'therapeutic' THEN 'THERAPEUTIC'::"ProgramDomain"
  ELSE 'TRAINING'::"ProgramDomain"
END;

ALTER TABLE "programs" DROP COLUMN "type";

CREATE INDEX "programs_programType_idx" ON "programs"("programType");
CREATE INDEX "programs_programDomain_idx" ON "programs"("programDomain");

ALTER TABLE "programs" ADD CONSTRAINT "programs_forkedFromId_fkey" FOREIGN KEY ("forkedFromId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AlterTable program_weeks: weekType string -> enum
ALTER TABLE "program_weeks" ADD COLUMN "weekType_new" "WeekType" NOT NULL DEFAULT 'NORMAL';
UPDATE "program_weeks" SET "weekType_new" = CASE LOWER("weekType")
  WHEN 'deload' THEN 'DELOAD'::"WeekType"
  ELSE 'NORMAL'::"WeekType"
END;
ALTER TABLE "program_weeks" DROP COLUMN "weekType";
ALTER TABLE "program_weeks" RENAME COLUMN "weekType_new" TO "weekType";

-- AlterTable program_days
ALTER TABLE "program_days" ADD COLUMN "dayFocus" TEXT;

-- AlterTable program_session_items: alternatives -> allowedSubstitutions
ALTER TABLE "program_session_items" RENAME COLUMN "alternatives" TO "allowedSubstitutions";
ALTER TABLE "program_session_items" ADD COLUMN "role" "SessionItemRole";
ALTER TABLE "program_session_items" ADD COLUMN "intent" "SessionItemIntent";
ALTER TABLE "program_session_items" ADD COLUMN "coachingNotes" JSONB;

-- AlterTable exercises
ALTER TABLE "exercises" ADD COLUMN "movementPattern" "MovementPattern";
ALTER TABLE "exercises" ADD COLUMN "loadCapability" "LoadCapability";
ALTER TABLE "exercises" ADD COLUMN "familyKey" TEXT;
ALTER TABLE "exercises" ADD COLUMN "familyOrder" INTEGER;

-- AlterTable user_programs
ALTER TABLE "user_programs" ADD COLUMN "templateVersion" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE "user_programs" ADD COLUMN "assignmentReason" JSONB;

-- CreateTable training_profiles
CREATE TABLE "training_profiles" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "heightCm" DOUBLE PRECISION,
    "weightKg" DOUBLE PRECISION,
    "dateOfBirth" TIMESTAMP(3),
    "biologicalSex" TEXT,
    "currentActivityLevel" TEXT,
    "trainingExperienceMonths" INTEGER,
    "resistanceExperience" TEXT,
    "availableDaysPerWeek" INTEGER,
    "maxSessionMinutes" INTEGER,
    "availableEquipment" JSONB,
    "trainingLocation" TEXT,
    "knownInjuries" JSONB,
    "painFlags" JSONB,
    "parqPassed" BOOLEAN,
    "parqFlags" JSONB,
    "parqCompletedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "training_profiles_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "training_profiles_userId_key" ON "training_profiles"("userId");
ALTER TABLE "training_profiles" ADD CONSTRAINT "training_profiles_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- CreateTable user_program_overrides
CREATE TABLE "user_program_overrides" (
    "id" TEXT NOT NULL,
    "userProgramId" TEXT NOT NULL,
    "weekNumber" INTEGER NOT NULL,
    "dayNumber" INTEGER NOT NULL,
    "sessionItemId" TEXT NOT NULL,
    "overrideType" "OverrideType" NOT NULL,
    "reasonCode" "OverrideReason",
    "data" JSONB,
    "appliedBy" "OverrideAppliedBy" NOT NULL DEFAULT 'USER',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "user_program_overrides_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "user_program_overrides_userProgramId_weekNumber_dayNumber_idx" ON "user_program_overrides"("userProgramId", "weekNumber", "dayNumber");
CREATE INDEX "user_program_overrides_sessionItemId_idx" ON "user_program_overrides"("sessionItemId");
ALTER TABLE "user_program_overrides" ADD CONSTRAINT "user_program_overrides_userProgramId_fkey" FOREIGN KEY ("userProgramId") REFERENCES "user_programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "user_program_overrides" ADD CONSTRAINT "user_program_overrides_sessionItemId_fkey" FOREIGN KEY ("sessionItemId") REFERENCES "program_session_items"("id") ON DELETE CASCADE ON UPDATE CASCADE;
