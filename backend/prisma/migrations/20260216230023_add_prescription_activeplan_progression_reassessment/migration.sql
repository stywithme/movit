-- AlterTable
ALTER TABLE "program_days" ADD COLUMN     "dayType" TEXT NOT NULL DEFAULT 'training';

-- AlterTable
ALTER TABLE "program_session_items" ADD COLUMN     "alternatives" TEXT[] DEFAULT ARRAY[]::TEXT[],
ADD COLUMN     "difficultyCode" TEXT,
ADD COLUMN     "isPersonalized" BOOLEAN NOT NULL DEFAULT false,
ADD COLUMN     "levelOverride" INTEGER,
ADD COLUMN     "progressionRuleId" TEXT,
ADD COLUMN     "targetFormScore" DOUBLE PRECISION,
ADD COLUMN     "targetROM" DOUBLE PRECISION;

-- AlterTable
ALTER TABLE "program_sessions" ADD COLUMN     "estimatedDurationMin" INTEGER,
ADD COLUMN     "sessionCategory" TEXT;

-- AlterTable
ALTER TABLE "program_weeks" ADD COLUMN     "weekType" TEXT NOT NULL DEFAULT 'normal';

-- AlterTable
ALTER TABLE "programs" ADD COLUMN     "contraindications" TEXT[] DEFAULT ARRAY[]::TEXT[],
ADD COLUMN     "entryCriteria" JSONB,
ADD COLUMN     "exitCriteria" JSONB,
ADD COLUMN     "levelRangeMax" INTEGER NOT NULL DEFAULT 5,
ADD COLUMN     "levelRangeMin" INTEGER NOT NULL DEFAULT 1,
ADD COLUMN     "nextProgramId" TEXT,
ADD COLUMN     "prerequisiteProgramId" TEXT,
ADD COLUMN     "prescriptionPriority" INTEGER NOT NULL DEFAULT 100,
ADD COLUMN     "targetDomain" TEXT,
ADD COLUMN     "targetRegions" TEXT[] DEFAULT ARRAY[]::TEXT[],
ADD COLUMN     "type" TEXT NOT NULL DEFAULT 'training';

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
CREATE TABLE "progression_history" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "ruleId" TEXT NOT NULL,
    "sessionId" TEXT,
    "field" TEXT NOT NULL,
    "previousValue" DOUBLE PRECISION NOT NULL,
    "newValue" DOUBLE PRECISION NOT NULL,
    "reason" TEXT NOT NULL,
    "appliedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
CREATE INDEX "progression_rules_isActive_idx" ON "progression_rules"("isActive");

-- CreateIndex
CREATE INDEX "progression_history_userId_idx" ON "progression_history"("userId");

-- CreateIndex
CREATE INDEX "progression_history_ruleId_idx" ON "progression_history"("ruleId");

-- CreateIndex
CREATE INDEX "progression_history_userId_appliedAt_idx" ON "progression_history"("userId", "appliedAt");

-- CreateIndex
CREATE INDEX "reassessment_schedules_userId_idx" ON "reassessment_schedules"("userId");

-- CreateIndex
CREATE INDEX "reassessment_schedules_userId_status_idx" ON "reassessment_schedules"("userId", "status");

-- CreateIndex
CREATE INDEX "reassessment_schedules_scheduledDate_idx" ON "reassessment_schedules"("scheduledDate");

-- CreateIndex
CREATE INDEX "program_session_items_progressionRuleId_idx" ON "program_session_items"("progressionRuleId");

-- CreateIndex
CREATE INDEX "programs_type_idx" ON "programs"("type");

-- CreateIndex
CREATE INDEX "programs_levelRangeMin_levelRangeMax_idx" ON "programs"("levelRangeMin", "levelRangeMax");

-- AddForeignKey
ALTER TABLE "programs" ADD CONSTRAINT "programs_prerequisiteProgramId_fkey" FOREIGN KEY ("prerequisiteProgramId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "programs" ADD CONSTRAINT "programs_nextProgramId_fkey" FOREIGN KEY ("nextProgramId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_session_items" ADD CONSTRAINT "program_session_items_progressionRuleId_fkey" FOREIGN KEY ("progressionRuleId") REFERENCES "progression_rules"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "active_plans" ADD CONSTRAINT "active_plans_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "active_plan_programs" ADD CONSTRAINT "active_plan_programs_activePlanId_fkey" FOREIGN KEY ("activePlanId") REFERENCES "active_plans"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "active_plan_programs" ADD CONSTRAINT "active_plan_programs_userProgramId_fkey" FOREIGN KEY ("userProgramId") REFERENCES "user_programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "progression_rules" ADD CONSTRAINT "progression_rules_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "progression_history" ADD CONSTRAINT "progression_history_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "progression_history" ADD CONSTRAINT "progression_history_ruleId_fkey" FOREIGN KEY ("ruleId") REFERENCES "progression_rules"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "reassessment_schedules" ADD CONSTRAINT "reassessment_schedules_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "reassessment_schedules" ADD CONSTRAINT "reassessment_schedules_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "body_scan_results"("id") ON DELETE SET NULL ON UPDATE CASCADE;
