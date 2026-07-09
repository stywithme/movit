-- Production baseline v1 (squashed from schema.prisma)
-- DDL only. Reference data is seeded via npm run seed:base / seed:full.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "ContentStatus" AS ENUM ('draft', 'published');

-- CreateEnum
CREATE TYPE "ProgramType" AS ENUM ('SYSTEM', 'COACH', 'CUSTOM');

-- CreateEnum
CREATE TYPE "ProgramDomain" AS ENUM ('TRAINING', 'MOBILITY', 'THERAPEUTIC');

-- CreateEnum
CREATE TYPE "TrainingGoal" AS ENUM ('STRENGTH', 'HYPERTROPHY', 'POWER', 'GENERAL_HEALTH');

-- CreateEnum
CREATE TYPE "ProgramAttributeMode" AS ENUM ('REQUIRED', 'OPTIONAL', 'EXCLUDED');

-- CreateEnum
CREATE TYPE "WorkoutTemplateOrigin" AS ENUM ('STANDALONE', 'PROGRAM_EMBEDDED');

-- CreateEnum
CREATE TYPE "WorkoutBlockRole" AS ENUM ('WARMUP', 'ACTIVATION', 'MAIN', 'ACCESSORY', 'CORRECTIVE', 'COOLDOWN', 'TEST');

-- CreateEnum
CREATE TYPE "PlannedWorkoutItemType" AS ENUM ('exercise', 'rest');

-- CreateEnum
CREATE TYPE "WorkoutExecutionContext" AS ENUM ('free', 'program', 'assessment', 'explore_workout', 'quick_start');

-- CreateEnum
CREATE TYPE "WorkoutItemIntent" AS ENUM ('STANDARD', 'POWER', 'ECCENTRIC', 'VELOCITY_BASED');

-- CreateEnum
CREATE TYPE "MovementPattern" AS ENUM ('SQUAT', 'HINGE', 'LUNGE', 'PUSH_HORIZONTAL', 'PUSH_VERTICAL', 'PULL_HORIZONTAL', 'PULL_VERTICAL', 'CARRY', 'ROTATION', 'GAIT', 'JUMP_LAND', 'CORE_BRACE', 'MOBILITY_DRILL', 'OTHER');

-- CreateEnum
CREATE TYPE "LoadCapability" AS ENUM ('BODYWEIGHT_ONLY', 'EXTERNAL_LOAD_OPTIONAL', 'EXTERNAL_LOAD_REQUIRED');

-- CreateEnum
CREATE TYPE "OverrideType" AS ENUM ('REPLACE_EXERCISE', 'ADJUST_PRESCRIPTION', 'SKIP_ITEM', 'ADD_ITEM');

-- CreateEnum
CREATE TYPE "OverrideReason" AS ENUM ('PAIN_AVOIDANCE', 'EQUIPMENT_UNAVAILABLE', 'TIME_CONSTRAINT', 'PREFERENCE', 'COACH_RECOMMENDATION', 'OTHER');

-- CreateEnum
CREATE TYPE "OverrideAppliedBy" AS ENUM ('USER', 'COACH');

-- CreateEnum
CREATE TYPE "ReportStatus" AS ENUM ('in_progress', 'completed', 'abandoned');

-- CreateEnum
CREATE TYPE "ProgressStatus" AS ENUM ('pending', 'in_progress', 'completed', 'skipped');

-- CreateEnum
CREATE TYPE "ExerciseArchetype" AS ENUM ('weighted_strength', 'bodyweight_dynamic', 'isometric_hold', 'mobility_rom', 'motor_control');

-- CreateTable
CREATE TABLE "attributes" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" TEXT,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "isSystem" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "attributes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "attribute_values" (
    "id" TEXT NOT NULL,
    "attributeId" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "icon" TEXT,
    "color" TEXT,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "metadata" JSONB,

    CONSTRAINT "attribute_values_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "exercises" (
    "id" TEXT NOT NULL,
    "categoryId" TEXT NOT NULL,
    "countingMethodId" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "instructions" JSONB,
    "slug" TEXT NOT NULL,
    "status" "ContentStatus" NOT NULL DEFAULT 'draft',
    "repCountingConfig" JSONB,
    "supportsWeight" BOOLEAN NOT NULL DEFAULT false,
    "minWeight" DOUBLE PRECISION,
    "maxWeight" DOUBLE PRECISION,
    "defaultWeight" DOUBLE PRECISION,
    "isBilateral" BOOLEAN NOT NULL DEFAULT false,
    "bilateralConfig" JSONB,
    "reportMetrics" JSONB,
    "publishedAt" TIMESTAMP(3),
    "createdBy" TEXT,
    "updatedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    "levelId" TEXT,
    "isFeatured" BOOLEAN NOT NULL DEFAULT false,
    "archetype" "ExerciseArchetype",
    "movementPattern" "MovementPattern",
    "loadCapability" "LoadCapability",
    "familyKey" TEXT,
    "familyOrder" INTEGER,
    "intent" "WorkoutItemIntent",
    "coachingNotes" JSONB,

    CONSTRAINT "exercises_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_exercise_preferences" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "customReps" INTEGER,
    "customDurationSec" INTEGER,
    "customWeightKg" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "user_exercise_preferences_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "exercise_attributes" (
    "id" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "attributeValueId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "exercise_attributes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "exercise_media" (
    "id" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "url" TEXT NOT NULL,
    "altText" TEXT,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "isPrimary" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "exercise_media_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "pose_positions" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "imageUrl" TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "postures" JSONB NOT NULL DEFAULT '["any"]',
    "directions" JSONB NOT NULL DEFAULT '["any"]',
    "regions" JSONB NOT NULL DEFAULT '["any"]',

    CONSTRAINT "pose_positions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "pose_position_joints" (
    "id" TEXT NOT NULL,
    "posePositionId" TEXT NOT NULL,
    "jointId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "pose_position_joints_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "pose_variants" (
    "id" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "posePositionId" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "trackedJointsConfig" JSONB,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "pose_variants_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "position_checks" (
    "id" TEXT NOT NULL,
    "poseVariantId" TEXT NOT NULL,
    "checkId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "landmarks" JSONB NOT NULL,
    "condition" JSONB NOT NULL,
    "activePhases" JSONB NOT NULL,
    "errorMessage" JSONB NOT NULL,
    "severity" TEXT NOT NULL DEFAULT 'warning',
    "cooldownMs" INTEGER NOT NULL DEFAULT 2000,
    "minErrorFrames" INTEGER NOT NULL DEFAULT 3,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "position_checks_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "difficulty_levels" (
    "id" TEXT NOT NULL,
    "poseVariantId" TEXT NOT NULL,
    "difficultyTypeId" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "repCountingConfig" JSONB,
    "phases" JSONB,
    "romConfig" JSONB,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "difficulty_levels_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "feedback_message_templates" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "context" TEXT,
    "description" TEXT,
    "content" JSONB NOT NULL,
    "tags" TEXT[],
    "isSystem" BOOLEAN NOT NULL DEFAULT false,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "feedback_message_templates_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "feedback_message_assignments" (
    "id" TEXT NOT NULL,
    "poseVariantId" TEXT NOT NULL,
    "messageId" TEXT NOT NULL,
    "target" TEXT NOT NULL,
    "context" TEXT,
    "jointCode" TEXT,
    "zone" TEXT,
    "checkId" TEXT,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "feedback_message_assignments_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "workout_templates" (
    "id" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "slug" TEXT NOT NULL,
    "coverImageUrl" TEXT,
    "levelId" TEXT,
    "estimatedDurationMin" INTEGER,
    "tags" JSONB,
    "status" "ContentStatus" NOT NULL DEFAULT 'draft',
    "publishedAt" TIMESTAMP(3),
    "createdBy" TEXT,
    "updatedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    "isFeatured" BOOLEAN NOT NULL DEFAULT false,
    "origin" "WorkoutTemplateOrigin" NOT NULL DEFAULT 'STANDALONE',
    "programId" TEXT,

    CONSTRAINT "workout_templates_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "workout_phases" (
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
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "workout_phases_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "workout_template_phases" (
    "id" TEXT NOT NULL,
    "workoutTemplateId" TEXT NOT NULL,
    "phaseId" TEXT NOT NULL,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "nameOverride" JSONB,
    "canSkipOverride" BOOLEAN,
    "canContinueOverride" BOOLEAN,
    "maxContinueTimeMsOverride" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "workout_template_phases_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "workout_template_exercises" (
    "id" TEXT NOT NULL,
    "workoutTemplateId" TEXT NOT NULL,
    "workoutTemplatePhaseId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "variantIndex" INTEGER NOT NULL DEFAULT 0,
    "targetReps" INTEGER,
    "targetRepsPerSet" JSONB,
    "targetDuration" INTEGER,
    "sets" INTEGER NOT NULL DEFAULT 1,
    "restBetweenSetsMs" INTEGER NOT NULL DEFAULT 30000,
    "restBetweenSetsPerSetMs" JSONB,
    "restAfterExerciseMs" INTEGER NOT NULL DEFAULT 60000,
    "weightKg" DOUBLE PRECISION,
    "weightPerSet" JSONB,
    "notes" JSONB,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "workout_template_exercises_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "programs" (
    "id" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "slug" TEXT NOT NULL,
    "coverImageUrl" TEXT,
    "durationWeeks" INTEGER NOT NULL,
    "isDefault" BOOLEAN NOT NULL DEFAULT false,
    "isPublished" BOOLEAN NOT NULL DEFAULT false,
    "tags" JSONB,
    "createdBy" TEXT,
    "updatedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    "levelMinId" TEXT,
    "levelMaxId" TEXT,
    "nextProgramId" TEXT,
    "prerequisiteProgramId" TEXT,
    "prescriptionPriority" INTEGER NOT NULL DEFAULT 100,
    "programType" "ProgramType" NOT NULL DEFAULT 'SYSTEM',
    "autoAssignable" BOOLEAN NOT NULL DEFAULT false,
    "version" INTEGER NOT NULL DEFAULT 1,
    "ownerId" TEXT,
    "forkedFromId" TEXT,
    "coachingNotes" JSONB,
    "weeklyWorkoutTarget" INTEGER,
    "estimatedWorkoutMinutes" INTEGER,
    "isFeatured" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "programs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "program_attributes" (
    "id" TEXT NOT NULL,
    "programId" TEXT NOT NULL,
    "attributeValueId" TEXT NOT NULL,
    "mode" "ProgramAttributeMode" NOT NULL DEFAULT 'REQUIRED',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "program_attributes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "program_weeks" (
    "id" TEXT NOT NULL,
    "programId" TEXT NOT NULL,
    "weekNumber" INTEGER NOT NULL,
    "target" JSONB,
    "description" JSONB,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "program_weeks_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "program_days" (
    "id" TEXT NOT NULL,
    "weekId" TEXT NOT NULL,
    "dayNumber" INTEGER NOT NULL,
    "dayType" TEXT NOT NULL DEFAULT 'training',
    "isRestDay" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "program_days_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "program_day_attributes" (
    "id" TEXT NOT NULL,
    "dayId" TEXT NOT NULL,
    "attributeValueId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "program_day_attributes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "planned_workouts" (
    "id" TEXT NOT NULL,
    "dayId" TEXT NOT NULL,
    "workoutTemplateId" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "estimatedDurationMin" INTEGER,

    CONSTRAINT "planned_workouts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "planned_workout_items" (
    "id" TEXT NOT NULL,
    "plannedWorkoutId" TEXT NOT NULL,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "type" "PlannedWorkoutItemType" NOT NULL,
    "exerciseId" TEXT,
    "sets" INTEGER DEFAULT 1,
    "targetReps" INTEGER,
    "targetDuration" INTEGER,
    "restBetweenSetsMs" INTEGER DEFAULT 30000,
    "weightKg" DOUBLE PRECISION,
    "weightPerSet" JSONB,
    "notes" JSONB,
    "restDurationMs" INTEGER,
    "sourceWorkoutTemplateId" TEXT,
    "isModified" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "planned_workout_items_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_programs" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "programId" TEXT,
    "name" JSONB,
    "startDate" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "customizations" JSONB,
    "customizationsUpdatedAt" TIMESTAMP(3),
    "templateVersion" INTEGER NOT NULL DEFAULT 1,
    "assignmentReason" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "user_programs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_program_overrides" (
    "id" TEXT NOT NULL,
    "userProgramId" TEXT NOT NULL,
    "weekNumber" INTEGER NOT NULL,
    "dayNumber" INTEGER NOT NULL,
    "plannedWorkoutItemId" TEXT,
    "workoutTemplateExerciseId" TEXT,
    "overrideType" "OverrideType" NOT NULL,
    "reasonCode" "OverrideReason",
    "data" JSONB,
    "appliedBy" "OverrideAppliedBy" NOT NULL DEFAULT 'USER',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "user_program_overrides_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_program_progress" (
    "id" TEXT NOT NULL,
    "userProgramId" TEXT NOT NULL,
    "weekNumber" INTEGER NOT NULL,
    "dayNumber" INTEGER NOT NULL,
    "plannedWorkoutId" TEXT NOT NULL DEFAULT '__day__',
    "completedAt" TIMESTAMP(3),
    "status" "ProgressStatus" NOT NULL DEFAULT 'pending',

    CONSTRAINT "user_program_progress_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "planned_workout_reports" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "programId" TEXT,
    "plannedWorkoutId" TEXT NOT NULL,
    "weekNumber" INTEGER NOT NULL,
    "dayNumber" INTEGER NOT NULL,
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" TIMESTAMP(3),
    "status" "ReportStatus" NOT NULL DEFAULT 'in_progress',
    "totalDurationMs" INTEGER,
    "totalExercises" INTEGER,
    "totalSets" INTEGER,
    "completedSets" INTEGER,
    "totalReps" INTEGER,
    "avgAccuracy" DOUBLE PRECISION,
    "avgFormScore" DOUBLE PRECISION,
    "rpe" INTEGER,
    "report" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "planned_workout_reports_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "system" (
    "id" SERIAL NOT NULL,
    "key" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "system_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "roles" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "displayName" JSONB NOT NULL,
    "description" JSONB,
    "isSystem" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "roles_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "model_has_roles" (
    "roleId" TEXT NOT NULL,
    "modelId" TEXT NOT NULL,
    "modelType" TEXT NOT NULL,

    CONSTRAINT "model_has_roles_pkey" PRIMARY KEY ("roleId","modelId","modelType")
);

-- CreateTable
CREATE TABLE "permissions" (
    "id" TEXT NOT NULL,
    "subject" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "permissions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "role_permissions" (
    "id" TEXT NOT NULL,
    "roleId" TEXT NOT NULL,
    "permissionId" TEXT NOT NULL,

    CONSTRAINT "role_permissions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "admins" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "password" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "resetToken" TEXT,
    "resetTokenExpiry" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    "isSuperAdmin" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "admins_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "users" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "password" TEXT,
    "name" TEXT NOT NULL,
    "avatarUrl" TEXT,
    "googleId" TEXT,
    "provider" TEXT NOT NULL DEFAULT 'email',
    "preferredLanguage" TEXT NOT NULL DEFAULT 'en',
    "voiceFeedback" BOOLEAN NOT NULL DEFAULT true,
    "notifications" BOOLEAN NOT NULL DEFAULT true,
    "isPro" BOOLEAN NOT NULL DEFAULT false,
    "subscriptionExpiry" TIMESTAMP(3),
    "totalWorkoutExecutions" INTEGER NOT NULL DEFAULT 0,
    "totalMinutes" INTEGER NOT NULL DEFAULT 0,
    "resetToken" TEXT,
    "resetTokenExpiry" TIMESTAMP(3),
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "emailVerified" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    "trainingGoal" "TrainingGoal" NOT NULL DEFAULT 'GENERAL_HEALTH',

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
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
    "trainingWeekdays" INTEGER[] DEFAULT ARRAY[]::INTEGER[],
    "maxWorkoutMinutes" INTEGER,
    "availableEquipment" JSONB,
    "trainingLocation" TEXT,
    "knownInjuries" JSONB,
    "healthDisclaimerAccepted" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "training_profiles_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "refresh_tokens" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "token" TEXT NOT NULL,
    "deviceInfo" TEXT,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "refresh_tokens_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "levels" (
    "id" TEXT NOT NULL,
    "number" INTEGER NOT NULL,
    "code" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "icon" TEXT,
    "color" TEXT,
    "entryThreshold" DOUBLE PRECISION NOT NULL,
    "defaultSetsMin" INTEGER NOT NULL DEFAULT 2,
    "defaultSetsMax" INTEGER NOT NULL DEFAULT 3,
    "defaultRepsMin" INTEGER NOT NULL DEFAULT 8,
    "defaultRepsMax" INTEGER NOT NULL DEFAULT 12,
    "defaultIntensityGuide" TEXT NOT NULL DEFAULT 'bodyweight_only',
    "defaultRestBetweenSetsMs" INTEGER NOT NULL DEFAULT 60000,
    "defaultWorkoutDurMin" INTEGER NOT NULL DEFAULT 20,
    "defaultWorkoutDurMax" INTEGER NOT NULL DEFAULT 30,
    "defaultWeeklyFreqMin" INTEGER NOT NULL DEFAULT 2,
    "defaultWeeklyFreqMax" INTEGER NOT NULL DEFAULT 3,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "maxThreshold" DOUBLE PRECISION,

    CONSTRAINT "levels_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_level_profiles" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "overallLevel" INTEGER NOT NULL,
    "bodyScore" DOUBLE PRECISION NOT NULL,
    "domainLevels" JSONB NOT NULL,
    "regionLevels" JSONB NOT NULL,
    "limitingFactors" JSONB NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "classifiedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "user_level_profiles_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "active_plans" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'active',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "active_plans_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "active_plan_programs" (
    "id" TEXT NOT NULL,
    "activePlanId" TEXT NOT NULL,
    "userProgramId" TEXT NOT NULL,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'upcoming',
    "scheduledStartDate" TIMESTAMP(3),
    "actualStartDate" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "active_plan_programs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "progression_rules" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "scope" TEXT NOT NULL,
    "programId" TEXT,
    "exerciseSlug" TEXT,
    "trigger" TEXT NOT NULL,
    "conditions" JSONB NOT NULL,
    "action" JSONB NOT NULL,
    "priority" INTEGER NOT NULL DEFAULT 0,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "progression_rules_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "exercise_progression_profiles" (
    "id" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "archetype" "ExerciseArchetype" NOT NULL,
    "allowedAxes" JSONB NOT NULL,
    "priorityOrder" JSONB NOT NULL,
    "repAxis" JSONB,
    "loadAxis" JSONB,
    "durationAxis" JSONB,
    "setAxis" JSONB,
    "difficultyLadder" JSONB,
    "qualityGate" JSONB NOT NULL,
    "promotionPolicy" JSONB NOT NULL,
    "regressionPolicy" JSONB NOT NULL,
    "isAutoGenerated" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "exercise_progression_profiles_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_program_exercise_progression_state" (
    "id" TEXT NOT NULL,
    "userProgramId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "currentAxis" TEXT NOT NULL,
    "currentDifficultyCode" TEXT,
    "currentWeightKg" DOUBLE PRECISION,
    "currentTargetReps" INTEGER,
    "currentTargetDuration" INTEGER,
    "currentTargetSets" INTEGER,
    "successStreak" INTEGER NOT NULL DEFAULT 0,
    "regressionStreak" INTEGER NOT NULL DEFAULT 0,
    "lastProgressedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "user_program_exercise_progression_state_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "progression_history" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "ruleId" TEXT,
    "plannedWorkoutId" TEXT,
    "field" TEXT NOT NULL,
    "previousValue" DOUBLE PRECISION NOT NULL,
    "newValue" DOUBLE PRECISION NOT NULL,
    "reason" TEXT NOT NULL,
    "appliedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "decisionType" TEXT,
    "axis" TEXT,
    "eligibilitySnapshot" JSONB,
    "stateBefore" JSONB,
    "stateAfter" JSONB,
    "seen" BOOLEAN NOT NULL DEFAULT false,
    "seenAt" TIMESTAMP(3),

    CONSTRAINT "progression_history_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "reassessment_schedules" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "reason" TEXT NOT NULL,
    "scheduledDate" TIMESTAMP(3) NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "assessmentId" TEXT,
    "notes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "reassessment_schedules_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "assessment_templates" (
    "id" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "type" TEXT NOT NULL DEFAULT 'initial',
    "targetLevelId" TEXT,
    "domainWeights" JSONB NOT NULL DEFAULT '{"safety": 0.20, "control": 0.25, "mobility": 0.35, "symmetry": 0.20}',
    "isDefault" BOOLEAN NOT NULL DEFAULT false,
    "isPublished" BOOLEAN NOT NULL DEFAULT false,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdBy" TEXT,
    "updatedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "assessment_templates_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "assessment_attributes" (
    "id" TEXT NOT NULL,
    "templateId" TEXT NOT NULL,
    "attributeValueId" TEXT NOT NULL,
    "mode" "ProgramAttributeMode" NOT NULL DEFAULT 'REQUIRED',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "assessment_attributes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "assessment_template_exercises" (
    "id" TEXT NOT NULL,
    "templateId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "targetRegion" TEXT NOT NULL,
    "side" TEXT NOT NULL DEFAULT 'center',
    "entryType" TEXT NOT NULL DEFAULT 'core',
    "activationCondition" JSONB,
    "referenceNormDegrees" DOUBLE PRECISION,
    "thresholds" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "assessment_template_exercises_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "body_scan_results" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" TEXT NOT NULL DEFAULT 'initial',
    "bodyScore" DOUBLE PRECISION NOT NULL,
    "mobilityScore" DOUBLE PRECISION NOT NULL,
    "controlScore" DOUBLE PRECISION NOT NULL,
    "symmetryScore" DOUBLE PRECISION,
    "safetyScore" DOUBLE PRECISION NOT NULL,
    "levelId" TEXT,
    "regions" JSONB NOT NULL,
    "symmetryData" JSONB,
    "hypotheses" JSONB,
    "recommendations" JSONB,
    "rawReportIds" JSONB,
    "previousId" TEXT,
    "durationMs" INTEGER,
    "movementCount" INTEGER NOT NULL DEFAULT 3,
    "completedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "templateId" TEXT,

    CONSTRAINT "body_scan_results_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "workout_executions" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL,
    "durationMs" INTEGER NOT NULL,
    "totalReps" INTEGER NOT NULL,
    "countedReps" INTEGER NOT NULL,
    "invalidReps" INTEGER NOT NULL,
    "weightKg" DOUBLE PRECISION,
    "weightUnit" TEXT NOT NULL DEFAULT 'kg',
    "context" "WorkoutExecutionContext" NOT NULL DEFAULT 'free',
    "workoutGroupId" TEXT,
    "workoutTemplateId" TEXT,
    "legacyReport" JSONB,
    "deviceId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "workout_executions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "workout_execution_metrics" (
    "id" TEXT NOT NULL,
    "workoutExecutionId" TEXT NOT NULL,
    "avgRom" INTEGER NOT NULL,
    "avgSymmetry" INTEGER,
    "avgStability" INTEGER NOT NULL,
    "avgVelocity" INTEGER,
    "avgFormScore" INTEGER NOT NULL,
    "avgAlignmentAccuracy" INTEGER NOT NULL,
    "avgTempo" JSONB NOT NULL,
    "totalTUT" INTEGER NOT NULL,
    "totalVolume" DOUBLE PRECISION,
    "maxWeight" DOUBLE PRECISION,
    "est1RM" DOUBLE PRECISION,
    "relativeStrength" DOUBLE PRECISION,
    "intensityPercentage" DOUBLE PRECISION,
    "formConsistency" INTEGER,
    "fatigueIndex" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "workout_execution_metrics_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "rep_metrics" (
    "id" TEXT NOT NULL,
    "workoutExecutionId" TEXT NOT NULL,
    "repNumber" INTEGER NOT NULL,
    "durationMs" INTEGER NOT NULL,
    "worstState" INTEGER NOT NULL,
    "score" INTEGER NOT NULL,
    "weightKg" DOUBLE PRECISION,
    "side" TEXT,
    "rom" INTEGER NOT NULL,
    "symmetry" INTEGER,
    "stability" INTEGER NOT NULL,
    "velocity" INTEGER,
    "formScore" INTEGER NOT NULL,
    "alignmentAccuracy" INTEGER NOT NULL,
    "tempo" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "rep_metrics_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "plans" (
    "id" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "monthlyPrice" DECIMAL(10,2) NOT NULL,
    "yearlyPrice" DECIMAL(10,2) NOT NULL,
    "currency" TEXT NOT NULL DEFAULT 'SAR',
    "discount" DECIMAL(10,2) DEFAULT 0,
    "maxExercisesLimit" INTEGER NOT NULL DEFAULT 0,
    "maxWorkoutTemplatesLimit" INTEGER NOT NULL DEFAULT 0,
    "monthlyGooglePlayProductId" TEXT,
    "yearlyGooglePlayProductId" TEXT,
    "monthlyAppStoreProductId" TEXT,
    "yearlyAppStoreProductId" TEXT,
    "features" JSONB,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "plans_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "subscriptions" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "planId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'active',
    "billingPeriod" TEXT NOT NULL DEFAULT 'monthly',
    "gateway" TEXT NOT NULL DEFAULT 'manual',
    "amountPaid" DECIMAL(10,2) NOT NULL,
    "startDate" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "endDate" TIMESTAMP(3) NOT NULL,
    "currentPeriodStart" TIMESTAMP(3),
    "currentPeriodEnd" TIMESTAMP(3),
    "autoRenew" BOOLEAN NOT NULL DEFAULT false,
    "cancelAtPeriodEnd" BOOLEAN NOT NULL DEFAULT false,
    "cancelledAt" TIMESTAMP(3),
    "upgradedFromId" TEXT,
    "googlePlayPackageName" TEXT,
    "googlePlayProductId" TEXT,
    "googlePlayPurchaseToken" TEXT,
    "googlePlayOrderId" TEXT,
    "appStoreProductId" TEXT,
    "appStoreTransactionId" TEXT,
    "appStoreOriginalTransactionId" TEXT,
    "myFatoorahInvoiceId" TEXT,
    "myFatoorahPaymentId" TEXT,
    "lastVerifiedAt" TIMESTAMP(3),
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "subscriptions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "subscription_checkouts" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "planId" TEXT NOT NULL,
    "subscriptionId" TEXT,
    "gateway" TEXT NOT NULL DEFAULT 'myfatoorah',
    "billingPeriod" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "currency" TEXT NOT NULL DEFAULT 'SAR',
    "amount" DECIMAL(10,2) NOT NULL,
    "paymentUrl" TEXT,
    "myFatoorahInvoiceId" TEXT,
    "myFatoorahPaymentId" TEXT,
    "googlePlayProductId" TEXT,
    "googlePlayPurchaseToken" TEXT,
    "idempotencyKey" TEXT,
    "expiresAt" TIMESTAMP(3),
    "paidAt" TIMESTAMP(3),
    "cancelledAt" TIMESTAMP(3),
    "failedAt" TIMESTAMP(3),
    "lastError" TEXT,
    "rawPayload" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "subscription_checkouts_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "attributes_code_key" ON "attributes"("code");

-- CreateIndex
CREATE UNIQUE INDEX "attribute_values_code_key" ON "attribute_values"("code");

-- CreateIndex
CREATE INDEX "attribute_values_attributeId_idx" ON "attribute_values"("attributeId");

-- CreateIndex
CREATE UNIQUE INDEX "exercises_slug_key" ON "exercises"("slug");

-- CreateIndex
CREATE INDEX "exercises_categoryId_idx" ON "exercises"("categoryId");

-- CreateIndex
CREATE INDEX "exercises_countingMethodId_idx" ON "exercises"("countingMethodId");

-- CreateIndex
CREATE INDEX "exercises_levelId_idx" ON "exercises"("levelId");

-- CreateIndex
CREATE INDEX "exercises_status_idx" ON "exercises"("status");

-- CreateIndex
CREATE INDEX "exercises_deletedAt_idx" ON "exercises"("deletedAt");

-- CreateIndex
CREATE INDEX "user_exercise_preferences_userId_idx" ON "user_exercise_preferences"("userId");

-- CreateIndex
CREATE INDEX "user_exercise_preferences_exerciseId_idx" ON "user_exercise_preferences"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "user_exercise_preferences_userId_exerciseId_key" ON "user_exercise_preferences"("userId", "exerciseId");

-- CreateIndex
CREATE INDEX "exercise_attributes_exerciseId_idx" ON "exercise_attributes"("exerciseId");

-- CreateIndex
CREATE INDEX "exercise_attributes_attributeValueId_idx" ON "exercise_attributes"("attributeValueId");

-- CreateIndex
CREATE UNIQUE INDEX "exercise_attributes_exerciseId_attributeValueId_key" ON "exercise_attributes"("exerciseId", "attributeValueId");

-- CreateIndex
CREATE INDEX "exercise_media_exerciseId_idx" ON "exercise_media"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "pose_positions_code_key" ON "pose_positions"("code");

-- CreateIndex
CREATE INDEX "pose_position_joints_posePositionId_idx" ON "pose_position_joints"("posePositionId");

-- CreateIndex
CREATE INDEX "pose_position_joints_jointId_idx" ON "pose_position_joints"("jointId");

-- CreateIndex
CREATE UNIQUE INDEX "pose_position_joints_posePositionId_jointId_key" ON "pose_position_joints"("posePositionId", "jointId");

-- CreateIndex
CREATE INDEX "pose_variants_exerciseId_idx" ON "pose_variants"("exerciseId");

-- CreateIndex
CREATE INDEX "pose_variants_posePositionId_idx" ON "pose_variants"("posePositionId");

-- CreateIndex
CREATE INDEX "position_checks_poseVariantId_idx" ON "position_checks"("poseVariantId");

-- CreateIndex
CREATE UNIQUE INDEX "position_checks_poseVariantId_checkId_key" ON "position_checks"("poseVariantId", "checkId");

-- CreateIndex
CREATE INDEX "difficulty_levels_poseVariantId_idx" ON "difficulty_levels"("poseVariantId");

-- CreateIndex
CREATE INDEX "difficulty_levels_difficultyTypeId_idx" ON "difficulty_levels"("difficultyTypeId");

-- CreateIndex
CREATE UNIQUE INDEX "feedback_message_templates_code_key" ON "feedback_message_templates"("code");

-- CreateIndex
CREATE INDEX "feedback_message_templates_category_context_idx" ON "feedback_message_templates"("category", "context");

-- CreateIndex
CREATE INDEX "feedback_message_templates_isActive_idx" ON "feedback_message_templates"("isActive");

-- CreateIndex
CREATE INDEX "feedback_message_assignments_poseVariantId_idx" ON "feedback_message_assignments"("poseVariantId");

-- CreateIndex
CREATE INDEX "feedback_message_assignments_messageId_idx" ON "feedback_message_assignments"("messageId");

-- CreateIndex
CREATE INDEX "feedback_message_assignments_target_context_idx" ON "feedback_message_assignments"("target", "context");

-- CreateIndex
CREATE UNIQUE INDEX "workout_templates_slug_key" ON "workout_templates"("slug");

-- CreateIndex
CREATE INDEX "workout_templates_status_idx" ON "workout_templates"("status");

-- CreateIndex
CREATE INDEX "workout_templates_deletedAt_idx" ON "workout_templates"("deletedAt");

-- CreateIndex
CREATE INDEX "workout_templates_levelId_idx" ON "workout_templates"("levelId");

-- CreateIndex
CREATE INDEX "workout_templates_origin_idx" ON "workout_templates"("origin");

-- CreateIndex
CREATE INDEX "workout_templates_programId_idx" ON "workout_templates"("programId");

-- CreateIndex
CREATE UNIQUE INDEX "workout_phases_slug_key" ON "workout_phases"("slug");

-- CreateIndex
CREATE INDEX "workout_phases_role_idx" ON "workout_phases"("role");

-- CreateIndex
CREATE INDEX "workout_phases_deletedAt_idx" ON "workout_phases"("deletedAt");

-- CreateIndex
CREATE INDEX "workout_template_phases_workoutTemplateId_idx" ON "workout_template_phases"("workoutTemplateId");

-- CreateIndex
CREATE INDEX "workout_template_phases_phaseId_idx" ON "workout_template_phases"("phaseId");

-- CreateIndex
CREATE UNIQUE INDEX "workout_template_phases_id_workoutTemplateId_key" ON "workout_template_phases"("id", "workoutTemplateId");

-- CreateIndex
CREATE INDEX "workout_template_exercises_workoutTemplateId_idx" ON "workout_template_exercises"("workoutTemplateId");

-- CreateIndex
CREATE INDEX "workout_template_exercises_workoutTemplatePhaseId_idx" ON "workout_template_exercises"("workoutTemplatePhaseId");

-- CreateIndex
CREATE INDEX "workout_template_exercises_exerciseId_idx" ON "workout_template_exercises"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "programs_slug_key" ON "programs"("slug");

-- CreateIndex
CREATE INDEX "programs_deletedAt_idx" ON "programs"("deletedAt");

-- CreateIndex
CREATE INDEX "programs_isPublished_idx" ON "programs"("isPublished");

-- CreateIndex
CREATE INDEX "programs_programType_idx" ON "programs"("programType");

-- CreateIndex
CREATE INDEX "programs_levelMinId_idx" ON "programs"("levelMinId");

-- CreateIndex
CREATE INDEX "programs_levelMaxId_idx" ON "programs"("levelMaxId");

-- CreateIndex
CREATE INDEX "program_attributes_programId_idx" ON "program_attributes"("programId");

-- CreateIndex
CREATE INDEX "program_attributes_attributeValueId_idx" ON "program_attributes"("attributeValueId");

-- CreateIndex
CREATE UNIQUE INDEX "program_attributes_programId_attributeValueId_key" ON "program_attributes"("programId", "attributeValueId");

-- CreateIndex
CREATE INDEX "program_weeks_programId_idx" ON "program_weeks"("programId");

-- CreateIndex
CREATE UNIQUE INDEX "program_weeks_programId_weekNumber_key" ON "program_weeks"("programId", "weekNumber");

-- CreateIndex
CREATE INDEX "program_days_weekId_idx" ON "program_days"("weekId");

-- CreateIndex
CREATE UNIQUE INDEX "program_days_weekId_dayNumber_key" ON "program_days"("weekId", "dayNumber");

-- CreateIndex
CREATE INDEX "program_day_attributes_dayId_idx" ON "program_day_attributes"("dayId");

-- CreateIndex
CREATE INDEX "program_day_attributes_attributeValueId_idx" ON "program_day_attributes"("attributeValueId");

-- CreateIndex
CREATE UNIQUE INDEX "program_day_attributes_dayId_attributeValueId_key" ON "program_day_attributes"("dayId", "attributeValueId");

-- CreateIndex
CREATE INDEX "planned_workouts_dayId_idx" ON "planned_workouts"("dayId");

-- CreateIndex
CREATE INDEX "planned_workouts_workoutTemplateId_idx" ON "planned_workouts"("workoutTemplateId");

-- CreateIndex
CREATE INDEX "planned_workout_items_plannedWorkoutId_idx" ON "planned_workout_items"("plannedWorkoutId");

-- CreateIndex
CREATE INDEX "planned_workout_items_exerciseId_idx" ON "planned_workout_items"("exerciseId");

-- CreateIndex
CREATE INDEX "planned_workout_items_sourceWorkoutTemplateId_idx" ON "planned_workout_items"("sourceWorkoutTemplateId");

-- CreateIndex
CREATE INDEX "user_programs_userId_idx" ON "user_programs"("userId");

-- CreateIndex
CREATE INDEX "user_programs_programId_idx" ON "user_programs"("programId");

-- CreateIndex
CREATE INDEX "user_program_overrides_userProgramId_weekNumber_dayNumber_idx" ON "user_program_overrides"("userProgramId", "weekNumber", "dayNumber");

-- CreateIndex
CREATE INDEX "user_program_overrides_plannedWorkoutItemId_idx" ON "user_program_overrides"("plannedWorkoutItemId");

-- CreateIndex
CREATE INDEX "user_program_overrides_workoutTemplateExerciseId_idx" ON "user_program_overrides"("workoutTemplateExerciseId");

-- CreateIndex
CREATE INDEX "user_program_progress_userProgramId_idx" ON "user_program_progress"("userProgramId");

-- CreateIndex
CREATE INDEX "user_program_progress_userProgramId_weekNumber_idx" ON "user_program_progress"("userProgramId", "weekNumber");

-- CreateIndex
CREATE UNIQUE INDEX "user_program_progress_userProgramId_weekNumber_dayNumber_pl_key" ON "user_program_progress"("userProgramId", "weekNumber", "dayNumber", "plannedWorkoutId");

-- CreateIndex
CREATE INDEX "planned_workout_reports_userId_idx" ON "planned_workout_reports"("userId");

-- CreateIndex
CREATE INDEX "planned_workout_reports_programId_idx" ON "planned_workout_reports"("programId");

-- CreateIndex
CREATE INDEX "planned_workout_reports_plannedWorkoutId_idx" ON "planned_workout_reports"("plannedWorkoutId");

-- CreateIndex
CREATE INDEX "planned_workout_reports_userId_programId_idx" ON "planned_workout_reports"("userId", "programId");

-- CreateIndex
CREATE INDEX "planned_workout_reports_userId_plannedWorkoutId_status_idx" ON "planned_workout_reports"("userId", "plannedWorkoutId", "status");

-- CreateIndex
CREATE INDEX "planned_workout_reports_userId_weekNumber_dayNumber_idx" ON "planned_workout_reports"("userId", "weekNumber", "dayNumber");

-- CreateIndex
CREATE UNIQUE INDEX "system_key_key" ON "system"("key");

-- CreateIndex
CREATE UNIQUE INDEX "roles_name_key" ON "roles"("name");

-- CreateIndex
CREATE INDEX "model_has_roles_modelId_modelType_idx" ON "model_has_roles"("modelId", "modelType");

-- CreateIndex
CREATE INDEX "model_has_roles_roleId_idx" ON "model_has_roles"("roleId");

-- CreateIndex
CREATE INDEX "permissions_subject_idx" ON "permissions"("subject");

-- CreateIndex
CREATE UNIQUE INDEX "permissions_subject_action_key" ON "permissions"("subject", "action");

-- CreateIndex
CREATE INDEX "role_permissions_roleId_idx" ON "role_permissions"("roleId");

-- CreateIndex
CREATE INDEX "role_permissions_permissionId_idx" ON "role_permissions"("permissionId");

-- CreateIndex
CREATE UNIQUE INDEX "role_permissions_roleId_permissionId_key" ON "role_permissions"("roleId", "permissionId");

-- CreateIndex
CREATE UNIQUE INDEX "admins_email_key" ON "admins"("email");

-- CreateIndex
CREATE INDEX "admins_isSuperAdmin_idx" ON "admins"("isSuperAdmin");

-- CreateIndex
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");

-- CreateIndex
CREATE UNIQUE INDEX "users_googleId_key" ON "users"("googleId");

-- CreateIndex
CREATE INDEX "users_email_idx" ON "users"("email");

-- CreateIndex
CREATE INDEX "users_googleId_idx" ON "users"("googleId");

-- CreateIndex
CREATE UNIQUE INDEX "training_profiles_userId_key" ON "training_profiles"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "refresh_tokens_token_key" ON "refresh_tokens"("token");

-- CreateIndex
CREATE INDEX "refresh_tokens_userId_idx" ON "refresh_tokens"("userId");

-- CreateIndex
CREATE INDEX "refresh_tokens_token_idx" ON "refresh_tokens"("token");

-- CreateIndex
CREATE INDEX "refresh_tokens_expiresAt_idx" ON "refresh_tokens"("expiresAt");

-- CreateIndex
CREATE UNIQUE INDEX "levels_number_key" ON "levels"("number");

-- CreateIndex
CREATE UNIQUE INDEX "levels_code_key" ON "levels"("code");

-- CreateIndex
CREATE UNIQUE INDEX "user_level_profiles_assessmentId_key" ON "user_level_profiles"("assessmentId");

-- CreateIndex
CREATE INDEX "user_level_profiles_userId_idx" ON "user_level_profiles"("userId");

-- CreateIndex
CREATE INDEX "user_level_profiles_userId_classifiedAt_idx" ON "user_level_profiles"("userId", "classifiedAt");

-- CreateIndex
CREATE UNIQUE INDEX "active_plans_userId_key" ON "active_plans"("userId");

-- CreateIndex
CREATE INDEX "active_plan_programs_activePlanId_idx" ON "active_plan_programs"("activePlanId");

-- CreateIndex
CREATE INDEX "active_plan_programs_userProgramId_idx" ON "active_plan_programs"("userProgramId");

-- CreateIndex
CREATE INDEX "progression_rules_scope_idx" ON "progression_rules"("scope");

-- CreateIndex
CREATE INDEX "progression_rules_programId_idx" ON "progression_rules"("programId");

-- CreateIndex
CREATE INDEX "progression_rules_exerciseSlug_idx" ON "progression_rules"("exerciseSlug");

-- CreateIndex
CREATE INDEX "progression_rules_isActive_idx" ON "progression_rules"("isActive");

-- CreateIndex
CREATE UNIQUE INDEX "exercise_progression_profiles_exerciseId_key" ON "exercise_progression_profiles"("exerciseId");

-- CreateIndex
CREATE INDEX "exercise_progression_profiles_archetype_idx" ON "exercise_progression_profiles"("archetype");

-- CreateIndex
CREATE INDEX "user_program_exercise_progression_state_userProgramId_idx" ON "user_program_exercise_progression_state"("userProgramId");

-- CreateIndex
CREATE INDEX "user_program_exercise_progression_state_exerciseId_idx" ON "user_program_exercise_progression_state"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "user_program_exercise_progression_state_userProgramId_exerc_key" ON "user_program_exercise_progression_state"("userProgramId", "exerciseId");

-- CreateIndex
CREATE INDEX "progression_history_userId_idx" ON "progression_history"("userId");

-- CreateIndex
CREATE INDEX "progression_history_ruleId_idx" ON "progression_history"("ruleId");

-- CreateIndex
CREATE INDEX "progression_history_plannedWorkoutId_idx" ON "progression_history"("plannedWorkoutId");

-- CreateIndex
CREATE INDEX "progression_history_userId_appliedAt_idx" ON "progression_history"("userId", "appliedAt");

-- CreateIndex
CREATE INDEX "progression_history_userId_seen_idx" ON "progression_history"("userId", "seen");

-- CreateIndex
CREATE INDEX "reassessment_schedules_userId_idx" ON "reassessment_schedules"("userId");

-- CreateIndex
CREATE INDEX "reassessment_schedules_userId_status_idx" ON "reassessment_schedules"("userId", "status");

-- CreateIndex
CREATE INDEX "reassessment_schedules_scheduledDate_idx" ON "reassessment_schedules"("scheduledDate");

-- CreateIndex
CREATE INDEX "assessment_templates_type_idx" ON "assessment_templates"("type");

-- CreateIndex
CREATE INDEX "assessment_templates_targetLevelId_idx" ON "assessment_templates"("targetLevelId");

-- CreateIndex
CREATE INDEX "assessment_templates_isPublished_idx" ON "assessment_templates"("isPublished");

-- CreateIndex
CREATE INDEX "assessment_attributes_templateId_idx" ON "assessment_attributes"("templateId");

-- CreateIndex
CREATE INDEX "assessment_attributes_attributeValueId_idx" ON "assessment_attributes"("attributeValueId");

-- CreateIndex
CREATE UNIQUE INDEX "assessment_attributes_templateId_attributeValueId_key" ON "assessment_attributes"("templateId", "attributeValueId");

-- CreateIndex
CREATE INDEX "assessment_template_exercises_templateId_idx" ON "assessment_template_exercises"("templateId");

-- CreateIndex
CREATE INDEX "assessment_template_exercises_exerciseId_idx" ON "assessment_template_exercises"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "assessment_template_exercises_templateId_exerciseId_key" ON "assessment_template_exercises"("templateId", "exerciseId");

-- CreateIndex
CREATE INDEX "body_scan_results_userId_idx" ON "body_scan_results"("userId");

-- CreateIndex
CREATE INDEX "body_scan_results_userId_type_idx" ON "body_scan_results"("userId", "type");

-- CreateIndex
CREATE INDEX "body_scan_results_userId_completedAt_idx" ON "body_scan_results"("userId", "completedAt");

-- CreateIndex
CREATE INDEX "body_scan_results_templateId_idx" ON "body_scan_results"("templateId");

-- CreateIndex
CREATE INDEX "body_scan_results_levelId_idx" ON "body_scan_results"("levelId");

-- CreateIndex
CREATE INDEX "workout_executions_userId_idx" ON "workout_executions"("userId");

-- CreateIndex
CREATE INDEX "workout_executions_exerciseId_idx" ON "workout_executions"("exerciseId");

-- CreateIndex
CREATE INDEX "workout_executions_timestamp_idx" ON "workout_executions"("timestamp");

-- CreateIndex
CREATE INDEX "workout_executions_userId_exerciseId_idx" ON "workout_executions"("userId", "exerciseId");

-- CreateIndex
CREATE INDEX "workout_executions_workoutGroupId_idx" ON "workout_executions"("workoutGroupId");

-- CreateIndex
CREATE INDEX "workout_executions_workoutTemplateId_idx" ON "workout_executions"("workoutTemplateId");

-- CreateIndex
CREATE INDEX "workout_executions_context_idx" ON "workout_executions"("context");

-- CreateIndex
CREATE UNIQUE INDEX "workout_execution_metrics_workoutExecutionId_key" ON "workout_execution_metrics"("workoutExecutionId");

-- CreateIndex
CREATE INDEX "rep_metrics_workoutExecutionId_idx" ON "rep_metrics"("workoutExecutionId");

-- CreateIndex
CREATE INDEX "rep_metrics_workoutExecutionId_repNumber_idx" ON "rep_metrics"("workoutExecutionId", "repNumber");

-- CreateIndex
CREATE UNIQUE INDEX "subscriptions_googlePlayPurchaseToken_key" ON "subscriptions"("googlePlayPurchaseToken");

-- CreateIndex
CREATE UNIQUE INDEX "subscriptions_appStoreTransactionId_key" ON "subscriptions"("appStoreTransactionId");

-- CreateIndex
CREATE INDEX "subscriptions_userId_idx" ON "subscriptions"("userId");

-- CreateIndex
CREATE INDEX "subscriptions_planId_idx" ON "subscriptions"("planId");

-- CreateIndex
CREATE INDEX "subscriptions_status_idx" ON "subscriptions"("status");

-- CreateIndex
CREATE INDEX "subscriptions_userId_status_idx" ON "subscriptions"("userId", "status");

-- CreateIndex
CREATE INDEX "subscriptions_endDate_idx" ON "subscriptions"("endDate");

-- CreateIndex
CREATE INDEX "subscriptions_googlePlayProductId_idx" ON "subscriptions"("googlePlayProductId");

-- CreateIndex
CREATE INDEX "subscriptions_appStoreProductId_idx" ON "subscriptions"("appStoreProductId");

-- CreateIndex
CREATE INDEX "subscriptions_appStoreOriginalTransactionId_idx" ON "subscriptions"("appStoreOriginalTransactionId");

-- CreateIndex
CREATE INDEX "subscriptions_myFatoorahInvoiceId_idx" ON "subscriptions"("myFatoorahInvoiceId");

-- CreateIndex
CREATE INDEX "subscriptions_myFatoorahPaymentId_idx" ON "subscriptions"("myFatoorahPaymentId");

-- CreateIndex
CREATE UNIQUE INDEX "subscription_checkouts_idempotencyKey_key" ON "subscription_checkouts"("idempotencyKey");

-- CreateIndex
CREATE INDEX "subscription_checkouts_userId_idx" ON "subscription_checkouts"("userId");

-- CreateIndex
CREATE INDEX "subscription_checkouts_planId_idx" ON "subscription_checkouts"("planId");

-- CreateIndex
CREATE INDEX "subscription_checkouts_subscriptionId_idx" ON "subscription_checkouts"("subscriptionId");

-- CreateIndex
CREATE INDEX "subscription_checkouts_gateway_idx" ON "subscription_checkouts"("gateway");

-- CreateIndex
CREATE INDEX "subscription_checkouts_status_idx" ON "subscription_checkouts"("status");

-- CreateIndex
CREATE INDEX "subscription_checkouts_myFatoorahInvoiceId_idx" ON "subscription_checkouts"("myFatoorahInvoiceId");

-- CreateIndex
CREATE INDEX "subscription_checkouts_myFatoorahPaymentId_idx" ON "subscription_checkouts"("myFatoorahPaymentId");

-- CreateIndex
CREATE INDEX "subscription_checkouts_googlePlayPurchaseToken_idx" ON "subscription_checkouts"("googlePlayPurchaseToken");

-- AddForeignKey
ALTER TABLE "attribute_values" ADD CONSTRAINT "attribute_values_attributeId_fkey" FOREIGN KEY ("attributeId") REFERENCES "attributes"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercises" ADD CONSTRAINT "exercises_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercises" ADD CONSTRAINT "exercises_countingMethodId_fkey" FOREIGN KEY ("countingMethodId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercises" ADD CONSTRAINT "exercises_levelId_fkey" FOREIGN KEY ("levelId") REFERENCES "levels"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_exercise_preferences" ADD CONSTRAINT "user_exercise_preferences_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_exercise_preferences" ADD CONSTRAINT "user_exercise_preferences_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercise_attributes" ADD CONSTRAINT "exercise_attributes_attributeValueId_fkey" FOREIGN KEY ("attributeValueId") REFERENCES "attribute_values"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercise_attributes" ADD CONSTRAINT "exercise_attributes_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercise_media" ADD CONSTRAINT "exercise_media_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "pose_position_joints" ADD CONSTRAINT "pose_position_joints_jointId_fkey" FOREIGN KEY ("jointId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "pose_position_joints" ADD CONSTRAINT "pose_position_joints_posePositionId_fkey" FOREIGN KEY ("posePositionId") REFERENCES "pose_positions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "pose_variants" ADD CONSTRAINT "pose_variants_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "pose_variants" ADD CONSTRAINT "pose_variants_posePositionId_fkey" FOREIGN KEY ("posePositionId") REFERENCES "pose_positions"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "position_checks" ADD CONSTRAINT "position_checks_poseVariantId_fkey" FOREIGN KEY ("poseVariantId") REFERENCES "pose_variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "difficulty_levels" ADD CONSTRAINT "difficulty_levels_difficultyTypeId_fkey" FOREIGN KEY ("difficultyTypeId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "difficulty_levels" ADD CONSTRAINT "difficulty_levels_poseVariantId_fkey" FOREIGN KEY ("poseVariantId") REFERENCES "pose_variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "feedback_message_assignments" ADD CONSTRAINT "feedback_message_assignments_messageId_fkey" FOREIGN KEY ("messageId") REFERENCES "feedback_message_templates"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "feedback_message_assignments" ADD CONSTRAINT "feedback_message_assignments_poseVariantId_fkey" FOREIGN KEY ("poseVariantId") REFERENCES "pose_variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_templates" ADD CONSTRAINT "workout_templates_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_templates" ADD CONSTRAINT "workout_templates_levelId_fkey" FOREIGN KEY ("levelId") REFERENCES "levels"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_template_phases" ADD CONSTRAINT "workout_template_phases_workoutTemplateId_fkey" FOREIGN KEY ("workoutTemplateId") REFERENCES "workout_templates"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_template_phases" ADD CONSTRAINT "workout_template_phases_phaseId_fkey" FOREIGN KEY ("phaseId") REFERENCES "workout_phases"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_template_exercises" ADD CONSTRAINT "workout_template_exercises_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_template_exercises" ADD CONSTRAINT "workout_template_exercises_workoutTemplateId_fkey" FOREIGN KEY ("workoutTemplateId") REFERENCES "workout_templates"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_template_exercises" ADD CONSTRAINT "workout_template_exercises_workoutTemplatePhaseId_workoutT_fkey" FOREIGN KEY ("workoutTemplatePhaseId", "workoutTemplateId") REFERENCES "workout_template_phases"("id", "workoutTemplateId") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "programs" ADD CONSTRAINT "programs_nextProgramId_fkey" FOREIGN KEY ("nextProgramId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "programs" ADD CONSTRAINT "programs_prerequisiteProgramId_fkey" FOREIGN KEY ("prerequisiteProgramId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "programs" ADD CONSTRAINT "programs_forkedFromId_fkey" FOREIGN KEY ("forkedFromId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "programs" ADD CONSTRAINT "programs_levelMinId_fkey" FOREIGN KEY ("levelMinId") REFERENCES "levels"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "programs" ADD CONSTRAINT "programs_levelMaxId_fkey" FOREIGN KEY ("levelMaxId") REFERENCES "levels"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_attributes" ADD CONSTRAINT "program_attributes_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_attributes" ADD CONSTRAINT "program_attributes_attributeValueId_fkey" FOREIGN KEY ("attributeValueId") REFERENCES "attribute_values"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_weeks" ADD CONSTRAINT "program_weeks_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_days" ADD CONSTRAINT "program_days_weekId_fkey" FOREIGN KEY ("weekId") REFERENCES "program_weeks"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_day_attributes" ADD CONSTRAINT "program_day_attributes_dayId_fkey" FOREIGN KEY ("dayId") REFERENCES "program_days"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_day_attributes" ADD CONSTRAINT "program_day_attributes_attributeValueId_fkey" FOREIGN KEY ("attributeValueId") REFERENCES "attribute_values"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "planned_workouts" ADD CONSTRAINT "planned_workouts_dayId_fkey" FOREIGN KEY ("dayId") REFERENCES "program_days"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "planned_workouts" ADD CONSTRAINT "planned_workouts_workoutTemplateId_fkey" FOREIGN KEY ("workoutTemplateId") REFERENCES "workout_templates"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "planned_workout_items" ADD CONSTRAINT "planned_workout_items_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "planned_workout_items" ADD CONSTRAINT "planned_workout_items_plannedWorkoutId_fkey" FOREIGN KEY ("plannedWorkoutId") REFERENCES "planned_workouts"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "planned_workout_items" ADD CONSTRAINT "planned_workout_items_sourceWorkoutTemplateId_fkey" FOREIGN KEY ("sourceWorkoutTemplateId") REFERENCES "workout_templates"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_programs" ADD CONSTRAINT "user_programs_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_programs" ADD CONSTRAINT "user_programs_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_program_overrides" ADD CONSTRAINT "user_program_overrides_userProgramId_fkey" FOREIGN KEY ("userProgramId") REFERENCES "user_programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_program_overrides" ADD CONSTRAINT "user_program_overrides_plannedWorkoutItemId_fkey" FOREIGN KEY ("plannedWorkoutItemId") REFERENCES "planned_workout_items"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_program_overrides" ADD CONSTRAINT "user_program_overrides_workoutTemplateExerciseId_fkey" FOREIGN KEY ("workoutTemplateExerciseId") REFERENCES "workout_template_exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_program_progress" ADD CONSTRAINT "user_program_progress_userProgramId_fkey" FOREIGN KEY ("userProgramId") REFERENCES "user_programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "planned_workout_reports" ADD CONSTRAINT "planned_workout_reports_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "planned_workout_reports" ADD CONSTRAINT "planned_workout_reports_plannedWorkoutId_fkey" FOREIGN KEY ("plannedWorkoutId") REFERENCES "planned_workouts"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "planned_workout_reports" ADD CONSTRAINT "planned_workout_reports_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "model_has_roles" ADD CONSTRAINT "model_has_roles_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES "roles"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "role_permissions" ADD CONSTRAINT "role_permissions_permissionId_fkey" FOREIGN KEY ("permissionId") REFERENCES "permissions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "role_permissions" ADD CONSTRAINT "role_permissions_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES "roles"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "training_profiles" ADD CONSTRAINT "training_profiles_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "refresh_tokens" ADD CONSTRAINT "refresh_tokens_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_level_profiles" ADD CONSTRAINT "user_level_profiles_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "body_scan_results"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_level_profiles" ADD CONSTRAINT "user_level_profiles_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "active_plans" ADD CONSTRAINT "active_plans_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "active_plan_programs" ADD CONSTRAINT "active_plan_programs_activePlanId_fkey" FOREIGN KEY ("activePlanId") REFERENCES "active_plans"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "active_plan_programs" ADD CONSTRAINT "active_plan_programs_userProgramId_fkey" FOREIGN KEY ("userProgramId") REFERENCES "user_programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "progression_rules" ADD CONSTRAINT "progression_rules_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "progression_rules" ADD CONSTRAINT "progression_rules_exerciseSlug_fkey" FOREIGN KEY ("exerciseSlug") REFERENCES "exercises"("slug") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercise_progression_profiles" ADD CONSTRAINT "exercise_progression_profiles_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_program_exercise_progression_state" ADD CONSTRAINT "user_program_exercise_progression_state_userProgramId_fkey" FOREIGN KEY ("userProgramId") REFERENCES "user_programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_program_exercise_progression_state" ADD CONSTRAINT "user_program_exercise_progression_state_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "progression_history" ADD CONSTRAINT "progression_history_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "progression_history" ADD CONSTRAINT "progression_history_ruleId_fkey" FOREIGN KEY ("ruleId") REFERENCES "progression_rules"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "progression_history" ADD CONSTRAINT "progression_history_plannedWorkoutId_fkey" FOREIGN KEY ("plannedWorkoutId") REFERENCES "planned_workouts"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "reassessment_schedules" ADD CONSTRAINT "reassessment_schedules_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "body_scan_results"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "reassessment_schedules" ADD CONSTRAINT "reassessment_schedules_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "assessment_templates" ADD CONSTRAINT "assessment_templates_targetLevelId_fkey" FOREIGN KEY ("targetLevelId") REFERENCES "levels"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "assessment_attributes" ADD CONSTRAINT "assessment_attributes_templateId_fkey" FOREIGN KEY ("templateId") REFERENCES "assessment_templates"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "assessment_attributes" ADD CONSTRAINT "assessment_attributes_attributeValueId_fkey" FOREIGN KEY ("attributeValueId") REFERENCES "attribute_values"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "assessment_template_exercises" ADD CONSTRAINT "assessment_template_exercises_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "assessment_template_exercises" ADD CONSTRAINT "assessment_template_exercises_templateId_fkey" FOREIGN KEY ("templateId") REFERENCES "assessment_templates"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "body_scan_results" ADD CONSTRAINT "body_scan_results_previousId_fkey" FOREIGN KEY ("previousId") REFERENCES "body_scan_results"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "body_scan_results" ADD CONSTRAINT "body_scan_results_templateId_fkey" FOREIGN KEY ("templateId") REFERENCES "assessment_templates"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "body_scan_results" ADD CONSTRAINT "body_scan_results_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "body_scan_results" ADD CONSTRAINT "body_scan_results_levelId_fkey" FOREIGN KEY ("levelId") REFERENCES "levels"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_executions" ADD CONSTRAINT "workout_executions_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_executions" ADD CONSTRAINT "workout_executions_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_executions" ADD CONSTRAINT "workout_executions_workoutTemplateId_fkey" FOREIGN KEY ("workoutTemplateId") REFERENCES "workout_templates"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_execution_metrics" ADD CONSTRAINT "workout_execution_metrics_workoutExecutionId_fkey" FOREIGN KEY ("workoutExecutionId") REFERENCES "workout_executions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "rep_metrics" ADD CONSTRAINT "rep_metrics_workoutExecutionId_fkey" FOREIGN KEY ("workoutExecutionId") REFERENCES "workout_executions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "subscriptions" ADD CONSTRAINT "subscriptions_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "subscriptions" ADD CONSTRAINT "subscriptions_planId_fkey" FOREIGN KEY ("planId") REFERENCES "plans"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "subscription_checkouts" ADD CONSTRAINT "subscription_checkouts_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "subscription_checkouts" ADD CONSTRAINT "subscription_checkouts_planId_fkey" FOREIGN KEY ("planId") REFERENCES "plans"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "subscription_checkouts" ADD CONSTRAINT "subscription_checkouts_subscriptionId_fkey" FOREIGN KEY ("subscriptionId") REFERENCES "subscriptions"("id") ON DELETE SET NULL ON UPDATE CASCADE;

