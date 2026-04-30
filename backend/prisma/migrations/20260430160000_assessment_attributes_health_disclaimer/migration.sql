-- AssessmentAttribute + TrainingProfile.healthDisclaimerAccepted
-- Remove PAR-Q / safety gate JSON from body_scan_results; remove PAR-Q from training_profiles

CREATE TABLE "assessment_attributes" (
    "id" TEXT NOT NULL,
    "templateId" TEXT NOT NULL,
    "attributeValueId" TEXT NOT NULL,
    "mode" "ProgramAttributeMode" NOT NULL DEFAULT 'REQUIRED',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "assessment_attributes_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "assessment_attributes_templateId_attributeValueId_key" ON "assessment_attributes"("templateId", "attributeValueId");
CREATE INDEX "assessment_attributes_templateId_idx" ON "assessment_attributes"("templateId");
CREATE INDEX "assessment_attributes_attributeValueId_idx" ON "assessment_attributes"("attributeValueId");

ALTER TABLE "assessment_attributes" ADD CONSTRAINT "assessment_attributes_templateId_fkey" FOREIGN KEY ("templateId") REFERENCES "assessment_templates"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "assessment_attributes" ADD CONSTRAINT "assessment_attributes_attributeValueId_fkey" FOREIGN KEY ("attributeValueId") REFERENCES "attribute_values"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "training_profiles" ADD COLUMN "healthDisclaimerAccepted" BOOLEAN NOT NULL DEFAULT false;
UPDATE "training_profiles" SET "healthDisclaimerAccepted" = true WHERE "parqPassed" = true;
ALTER TABLE "training_profiles" DROP COLUMN "painFlags";
ALTER TABLE "training_profiles" DROP COLUMN "parqPassed";
ALTER TABLE "training_profiles" DROP COLUMN "parqFlags";
ALTER TABLE "training_profiles" DROP COLUMN "parqCompletedAt";

ALTER TABLE "body_scan_results" DROP COLUMN "safetyGates";
ALTER TABLE "body_scan_results" DROP COLUMN "painFlags";
ALTER TABLE "body_scan_results" DROP COLUMN "parqPassed";
ALTER TABLE "body_scan_results" DROP COLUMN "parqFlags";
