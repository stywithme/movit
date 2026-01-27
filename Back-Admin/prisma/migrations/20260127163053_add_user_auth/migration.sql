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
    "status" TEXT NOT NULL DEFAULT 'draft',
    "repCountingConfig" JSONB,
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
CREATE TABLE "feedback_messages" (
    "id" TEXT NOT NULL,
    "poseVariantId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "message" JSONB NOT NULL,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "feedback_messages_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "admins" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "password" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'admin',
    "isActive" BOOLEAN NOT NULL DEFAULT true,
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
CREATE INDEX "feedback_messages_poseVariantId_idx" ON "feedback_messages"("poseVariantId");

-- CreateIndex
CREATE INDEX "feedback_messages_type_idx" ON "feedback_messages"("type");

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
ALTER TABLE "feedback_messages" ADD CONSTRAINT "feedback_messages_poseVariantId_fkey" FOREIGN KEY ("poseVariantId") REFERENCES "pose_variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "refresh_tokens" ADD CONSTRAINT "refresh_tokens_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
