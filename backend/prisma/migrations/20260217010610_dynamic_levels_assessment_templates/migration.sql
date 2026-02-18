-- AlterTable
ALTER TABLE "body_scan_results" ADD COLUMN     "templateId" TEXT;

-- AlterTable
ALTER TABLE "levels" ADD COLUMN     "maxThreshold" DOUBLE PRECISION;

-- CreateTable
CREATE TABLE "assessment_templates" (
    "id" TEXT NOT NULL,
    "name" JSONB NOT NULL,
    "description" JSONB,
    "type" TEXT NOT NULL DEFAULT 'initial',
    "targetLevelId" TEXT,
    "levelRangeMin" INTEGER NOT NULL DEFAULT 1,
    "levelRangeMax" INTEGER NOT NULL DEFAULT 5,
    "domainWeights" JSONB NOT NULL DEFAULT '{"mobility":0.35,"control":0.25,"symmetry":0.20,"safety":0.20}',
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

-- CreateIndex
CREATE INDEX "assessment_templates_type_idx" ON "assessment_templates"("type");

-- CreateIndex
CREATE INDEX "assessment_templates_targetLevelId_idx" ON "assessment_templates"("targetLevelId");

-- CreateIndex
CREATE INDEX "assessment_templates_isPublished_idx" ON "assessment_templates"("isPublished");

-- CreateIndex
CREATE INDEX "assessment_template_exercises_templateId_idx" ON "assessment_template_exercises"("templateId");

-- CreateIndex
CREATE INDEX "assessment_template_exercises_exerciseId_idx" ON "assessment_template_exercises"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "assessment_template_exercises_templateId_exerciseId_key" ON "assessment_template_exercises"("templateId", "exerciseId");

-- CreateIndex
CREATE INDEX "body_scan_results_templateId_idx" ON "body_scan_results"("templateId");

-- AddForeignKey
ALTER TABLE "assessment_templates" ADD CONSTRAINT "assessment_templates_targetLevelId_fkey" FOREIGN KEY ("targetLevelId") REFERENCES "levels"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "assessment_template_exercises" ADD CONSTRAINT "assessment_template_exercises_templateId_fkey" FOREIGN KEY ("templateId") REFERENCES "assessment_templates"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "assessment_template_exercises" ADD CONSTRAINT "assessment_template_exercises_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "body_scan_results" ADD CONSTRAINT "body_scan_results_templateId_fkey" FOREIGN KEY ("templateId") REFERENCES "assessment_templates"("id") ON DELETE SET NULL ON UPDATE CASCADE;
