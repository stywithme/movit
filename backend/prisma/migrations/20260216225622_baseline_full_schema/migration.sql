-- CreateEnum
CREATE TYPE "ContentStatus" AS ENUM ('draft', 'published');

-- CreateEnum
CREATE TYPE "Difficulty" AS ENUM ('beginner', 'intermediate', 'advanced');

-- CreateEnum
CREATE TYPE "ReportStatus" AS ENUM ('in_progress', 'completed', 'abandoned');

-- CreateEnum
CREATE TYPE "ProgressStatus" AS ENUM ('pending', 'in_progress', 'completed', 'skipped');

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

    CONSTRAINT "exercises_pkey" PRIMARY KEY ("id")
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
CREATE TABLE "camera_positions" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "schemaCode" TEXT,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "imageUrl" TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "camera_positions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "camera_position_joints" (
    "id" TEXT NOT NULL,
    "cameraPositionId" TEXT NOT NULL,
    "jointId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "camera_position_joints_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "pose_variants" (
    "id" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "cameraPositionId" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "referenceImageUrl" TEXT,
    "expectedFacingDirection" TEXT,
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
CREATE TABLE "workouts" (
    "id" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "slug" TEXT NOT NULL,
    "coverImageUrl" TEXT,
    "difficulty" "Difficulty" NOT NULL DEFAULT 'beginner',
    "estimatedDurationMin" INTEGER,
    "tags" JSONB,
    "status" "ContentStatus" NOT NULL DEFAULT 'draft',
    "publishedAt" TIMESTAMP(3),
    "createdBy" TEXT,
    "updatedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "workouts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "workout_exercises" (
    "id" TEXT NOT NULL,
    "workoutId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "variantIndex" INTEGER NOT NULL DEFAULT 0,
    "difficulty" TEXT NOT NULL DEFAULT 'beginner',
    "targetReps" INTEGER,
    "targetDuration" INTEGER,
    "sets" INTEGER NOT NULL DEFAULT 1,
    "restBetweenSetsMs" INTEGER NOT NULL DEFAULT 30000,
    "restAfterExerciseMs" INTEGER NOT NULL DEFAULT 60000,
    "weightKg" DOUBLE PRECISION,
    "weightPerSet" JSONB,
    "notes" JSONB,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "workout_exercises_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "programs" (
    "id" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "slug" TEXT NOT NULL,
    "coverImageUrl" TEXT,
    "durationWeeks" INTEGER NOT NULL,
    "difficulty" "Difficulty" NOT NULL DEFAULT 'beginner',
    "isDefault" BOOLEAN NOT NULL DEFAULT false,
    "isPublished" BOOLEAN NOT NULL DEFAULT false,
    "tags" JSONB,
    "createdBy" TEXT,
    "updatedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "programs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "program_weeks" (
    "id" TEXT NOT NULL,
    "programId" TEXT NOT NULL,
    "weekNumber" INTEGER NOT NULL,
    "name" JSONB,
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
    "isRestDay" BOOLEAN NOT NULL DEFAULT false,
    "name" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "program_days_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "program_sessions" (
    "id" TEXT NOT NULL,
    "dayId" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "program_sessions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "program_session_items" (
    "id" TEXT NOT NULL,
    "sessionId" TEXT NOT NULL,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "type" TEXT NOT NULL,
    "exerciseId" TEXT,
    "sets" INTEGER DEFAULT 1,
    "targetReps" INTEGER,
    "targetDuration" INTEGER,
    "restBetweenSetsMs" INTEGER DEFAULT 30000,
    "weightKg" DOUBLE PRECISION,
    "weightPerSet" JSONB,
    "notes" JSONB,
    "restDurationMs" INTEGER,
    "sourceWorkoutId" TEXT,
    "isModified" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "program_session_items_pkey" PRIMARY KEY ("id")
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
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "user_programs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_program_progress" (
    "id" TEXT NOT NULL,
    "userProgramId" TEXT NOT NULL,
    "weekNumber" INTEGER NOT NULL,
    "dayNumber" INTEGER NOT NULL,
    "sessionId" TEXT NOT NULL DEFAULT '__day__',
    "completedAt" TIMESTAMP(3),
    "status" "ProgressStatus" NOT NULL DEFAULT 'pending',

    CONSTRAINT "user_program_progress_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "program_session_reports" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "programId" TEXT,
    "programSessionId" TEXT NOT NULL,
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
    "report" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "program_session_reports_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "admins" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "password" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'admin',
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "resetToken" TEXT,
    "resetTokenExpiry" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

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
    "totalWorkouts" INTEGER NOT NULL DEFAULT 0,
    "totalMinutes" INTEGER NOT NULL DEFAULT 0,
    "resetToken" TEXT,
    "resetTokenExpiry" TIMESTAMP(3),
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "emailVerified" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
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
    "defaultSessionDurMin" INTEGER NOT NULL DEFAULT 20,
    "defaultSessionDurMax" INTEGER NOT NULL DEFAULT 30,
    "defaultWeeklyFreqMin" INTEGER NOT NULL DEFAULT 2,
    "defaultWeeklyFreqMax" INTEGER NOT NULL DEFAULT 3,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

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
CREATE TABLE "body_scan_results" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" TEXT NOT NULL DEFAULT 'initial',
    "bodyScore" DOUBLE PRECISION NOT NULL,
    "mobilityScore" DOUBLE PRECISION NOT NULL,
    "controlScore" DOUBLE PRECISION NOT NULL,
    "symmetryScore" DOUBLE PRECISION,
    "safetyScore" DOUBLE PRECISION NOT NULL,
    "fitnessLevel" TEXT NOT NULL,
    "regions" JSONB NOT NULL,
    "symmetryData" JSONB,
    "hypotheses" JSONB,
    "safetyGates" JSONB,
    "painFlags" JSONB,
    "recommendations" JSONB,
    "parqPassed" BOOLEAN NOT NULL DEFAULT true,
    "parqFlags" JSONB,
    "rawReportIds" JSONB,
    "previousId" TEXT,
    "durationMs" INTEGER,
    "movementCount" INTEGER NOT NULL DEFAULT 3,
    "completedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "body_scan_results_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "training_sessions" (
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
    "legacyReport" JSONB,
    "deviceId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "training_sessions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "session_metrics" (
    "id" TEXT NOT NULL,
    "sessionId" TEXT NOT NULL,
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

    CONSTRAINT "session_metrics_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "rep_metrics" (
    "id" TEXT NOT NULL,
    "sessionId" TEXT NOT NULL,
    "repNumber" INTEGER NOT NULL,
    "durationMs" INTEGER NOT NULL,
    "worstState" INTEGER NOT NULL,
    "score" INTEGER NOT NULL,
    "weightKg" DOUBLE PRECISION,
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
CREATE INDEX "exercises_status_idx" ON "exercises"("status");

-- CreateIndex
CREATE INDEX "exercises_deletedAt_idx" ON "exercises"("deletedAt");

-- CreateIndex
CREATE INDEX "exercise_attributes_exerciseId_idx" ON "exercise_attributes"("exerciseId");

-- CreateIndex
CREATE INDEX "exercise_attributes_attributeValueId_idx" ON "exercise_attributes"("attributeValueId");

-- CreateIndex
CREATE UNIQUE INDEX "exercise_attributes_exerciseId_attributeValueId_key" ON "exercise_attributes"("exerciseId", "attributeValueId");

-- CreateIndex
CREATE INDEX "exercise_media_exerciseId_idx" ON "exercise_media"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "camera_positions_code_key" ON "camera_positions"("code");

-- CreateIndex
CREATE INDEX "camera_position_joints_cameraPositionId_idx" ON "camera_position_joints"("cameraPositionId");

-- CreateIndex
CREATE INDEX "camera_position_joints_jointId_idx" ON "camera_position_joints"("jointId");

-- CreateIndex
CREATE UNIQUE INDEX "camera_position_joints_cameraPositionId_jointId_key" ON "camera_position_joints"("cameraPositionId", "jointId");

-- CreateIndex
CREATE INDEX "pose_variants_exerciseId_idx" ON "pose_variants"("exerciseId");

-- CreateIndex
CREATE INDEX "pose_variants_cameraPositionId_idx" ON "pose_variants"("cameraPositionId");

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
CREATE UNIQUE INDEX "workouts_slug_key" ON "workouts"("slug");

-- CreateIndex
CREATE INDEX "workouts_status_idx" ON "workouts"("status");

-- CreateIndex
CREATE INDEX "workouts_deletedAt_idx" ON "workouts"("deletedAt");

-- CreateIndex
CREATE INDEX "workout_exercises_workoutId_idx" ON "workout_exercises"("workoutId");

-- CreateIndex
CREATE INDEX "workout_exercises_exerciseId_idx" ON "workout_exercises"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "programs_slug_key" ON "programs"("slug");

-- CreateIndex
CREATE INDEX "programs_deletedAt_idx" ON "programs"("deletedAt");

-- CreateIndex
CREATE INDEX "programs_isPublished_idx" ON "programs"("isPublished");

-- CreateIndex
CREATE INDEX "program_weeks_programId_idx" ON "program_weeks"("programId");

-- CreateIndex
CREATE UNIQUE INDEX "program_weeks_programId_weekNumber_key" ON "program_weeks"("programId", "weekNumber");

-- CreateIndex
CREATE INDEX "program_days_weekId_idx" ON "program_days"("weekId");

-- CreateIndex
CREATE UNIQUE INDEX "program_days_weekId_dayNumber_key" ON "program_days"("weekId", "dayNumber");

-- CreateIndex
CREATE INDEX "program_sessions_dayId_idx" ON "program_sessions"("dayId");

-- CreateIndex
CREATE INDEX "program_session_items_sessionId_idx" ON "program_session_items"("sessionId");

-- CreateIndex
CREATE INDEX "program_session_items_exerciseId_idx" ON "program_session_items"("exerciseId");

-- CreateIndex
CREATE INDEX "program_session_items_sourceWorkoutId_idx" ON "program_session_items"("sourceWorkoutId");

-- CreateIndex
CREATE INDEX "user_programs_userId_idx" ON "user_programs"("userId");

-- CreateIndex
CREATE INDEX "user_programs_programId_idx" ON "user_programs"("programId");

-- CreateIndex
CREATE INDEX "user_program_progress_userProgramId_idx" ON "user_program_progress"("userProgramId");

-- CreateIndex
CREATE INDEX "user_program_progress_userProgramId_weekNumber_idx" ON "user_program_progress"("userProgramId", "weekNumber");

-- CreateIndex
CREATE UNIQUE INDEX "user_program_progress_userProgramId_weekNumber_dayNumber_se_key" ON "user_program_progress"("userProgramId", "weekNumber", "dayNumber", "sessionId");

-- CreateIndex
CREATE INDEX "program_session_reports_userId_idx" ON "program_session_reports"("userId");

-- CreateIndex
CREATE INDEX "program_session_reports_programId_idx" ON "program_session_reports"("programId");

-- CreateIndex
CREATE INDEX "program_session_reports_programSessionId_idx" ON "program_session_reports"("programSessionId");

-- CreateIndex
CREATE INDEX "program_session_reports_userId_programId_idx" ON "program_session_reports"("userId", "programId");

-- CreateIndex
CREATE INDEX "program_session_reports_userId_programSessionId_status_idx" ON "program_session_reports"("userId", "programSessionId", "status");

-- CreateIndex
CREATE INDEX "program_session_reports_userId_weekNumber_dayNumber_idx" ON "program_session_reports"("userId", "weekNumber", "dayNumber");

-- CreateIndex
CREATE UNIQUE INDEX "admins_email_key" ON "admins"("email");

-- CreateIndex
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");

-- CreateIndex
CREATE UNIQUE INDEX "users_googleId_key" ON "users"("googleId");

-- CreateIndex
CREATE INDEX "users_email_idx" ON "users"("email");

-- CreateIndex
CREATE INDEX "users_googleId_idx" ON "users"("googleId");

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
CREATE INDEX "user_level_profiles_userId_idx" ON "user_level_profiles"("userId");

-- CreateIndex
CREATE INDEX "user_level_profiles_userId_classifiedAt_idx" ON "user_level_profiles"("userId", "classifiedAt");

-- CreateIndex
CREATE UNIQUE INDEX "user_level_profiles_assessmentId_key" ON "user_level_profiles"("assessmentId");

-- CreateIndex
CREATE INDEX "body_scan_results_userId_idx" ON "body_scan_results"("userId");

-- CreateIndex
CREATE INDEX "body_scan_results_userId_type_idx" ON "body_scan_results"("userId", "type");

-- CreateIndex
CREATE INDEX "body_scan_results_userId_completedAt_idx" ON "body_scan_results"("userId", "completedAt");

-- CreateIndex
CREATE INDEX "training_sessions_userId_idx" ON "training_sessions"("userId");

-- CreateIndex
CREATE INDEX "training_sessions_exerciseId_idx" ON "training_sessions"("exerciseId");

-- CreateIndex
CREATE INDEX "training_sessions_timestamp_idx" ON "training_sessions"("timestamp");

-- CreateIndex
CREATE INDEX "training_sessions_userId_exerciseId_idx" ON "training_sessions"("userId", "exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "session_metrics_sessionId_key" ON "session_metrics"("sessionId");

-- CreateIndex
CREATE INDEX "rep_metrics_sessionId_idx" ON "rep_metrics"("sessionId");

-- CreateIndex
CREATE INDEX "rep_metrics_sessionId_repNumber_idx" ON "rep_metrics"("sessionId", "repNumber");

-- AddForeignKey
ALTER TABLE "attribute_values" ADD CONSTRAINT "attribute_values_attributeId_fkey" FOREIGN KEY ("attributeId") REFERENCES "attributes"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercises" ADD CONSTRAINT "exercises_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercises" ADD CONSTRAINT "exercises_countingMethodId_fkey" FOREIGN KEY ("countingMethodId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercise_attributes" ADD CONSTRAINT "exercise_attributes_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercise_attributes" ADD CONSTRAINT "exercise_attributes_attributeValueId_fkey" FOREIGN KEY ("attributeValueId") REFERENCES "attribute_values"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "exercise_media" ADD CONSTRAINT "exercise_media_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "camera_position_joints" ADD CONSTRAINT "camera_position_joints_cameraPositionId_fkey" FOREIGN KEY ("cameraPositionId") REFERENCES "camera_positions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "camera_position_joints" ADD CONSTRAINT "camera_position_joints_jointId_fkey" FOREIGN KEY ("jointId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "pose_variants" ADD CONSTRAINT "pose_variants_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "pose_variants" ADD CONSTRAINT "pose_variants_cameraPositionId_fkey" FOREIGN KEY ("cameraPositionId") REFERENCES "camera_positions"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "position_checks" ADD CONSTRAINT "position_checks_poseVariantId_fkey" FOREIGN KEY ("poseVariantId") REFERENCES "pose_variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "difficulty_levels" ADD CONSTRAINT "difficulty_levels_poseVariantId_fkey" FOREIGN KEY ("poseVariantId") REFERENCES "pose_variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "difficulty_levels" ADD CONSTRAINT "difficulty_levels_difficultyTypeId_fkey" FOREIGN KEY ("difficultyTypeId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "feedback_message_assignments" ADD CONSTRAINT "feedback_message_assignments_poseVariantId_fkey" FOREIGN KEY ("poseVariantId") REFERENCES "pose_variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "feedback_message_assignments" ADD CONSTRAINT "feedback_message_assignments_messageId_fkey" FOREIGN KEY ("messageId") REFERENCES "feedback_message_templates"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_exercises" ADD CONSTRAINT "workout_exercises_workoutId_fkey" FOREIGN KEY ("workoutId") REFERENCES "workouts"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "workout_exercises" ADD CONSTRAINT "workout_exercises_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_weeks" ADD CONSTRAINT "program_weeks_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_days" ADD CONSTRAINT "program_days_weekId_fkey" FOREIGN KEY ("weekId") REFERENCES "program_weeks"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_sessions" ADD CONSTRAINT "program_sessions_dayId_fkey" FOREIGN KEY ("dayId") REFERENCES "program_days"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_session_items" ADD CONSTRAINT "program_session_items_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "program_sessions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_session_items" ADD CONSTRAINT "program_session_items_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_session_items" ADD CONSTRAINT "program_session_items_sourceWorkoutId_fkey" FOREIGN KEY ("sourceWorkoutId") REFERENCES "workouts"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_programs" ADD CONSTRAINT "user_programs_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_programs" ADD CONSTRAINT "user_programs_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_program_progress" ADD CONSTRAINT "user_program_progress_userProgramId_fkey" FOREIGN KEY ("userProgramId") REFERENCES "user_programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_session_reports" ADD CONSTRAINT "program_session_reports_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_session_reports" ADD CONSTRAINT "program_session_reports_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_session_reports" ADD CONSTRAINT "program_session_reports_programSessionId_fkey" FOREIGN KEY ("programSessionId") REFERENCES "program_sessions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "refresh_tokens" ADD CONSTRAINT "refresh_tokens_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_level_profiles" ADD CONSTRAINT "user_level_profiles_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_level_profiles" ADD CONSTRAINT "user_level_profiles_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "body_scan_results"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "body_scan_results" ADD CONSTRAINT "body_scan_results_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "body_scan_results" ADD CONSTRAINT "body_scan_results_previousId_fkey" FOREIGN KEY ("previousId") REFERENCES "body_scan_results"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "training_sessions" ADD CONSTRAINT "training_sessions_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "training_sessions" ADD CONSTRAINT "training_sessions_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "session_metrics" ADD CONSTRAINT "session_metrics_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "training_sessions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "rep_metrics" ADD CONSTRAINT "rep_metrics_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "training_sessions"("id") ON DELETE CASCADE ON UPDATE CASCADE;
