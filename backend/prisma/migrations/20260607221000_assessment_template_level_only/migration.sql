-- Assessment templates target a single Level through targetLevelId.
-- Numeric level ranges are removed as an alternate difficulty representation.

ALTER TABLE "assessment_templates"
  DROP COLUMN IF EXISTS "levelRangeMin",
  DROP COLUMN IF EXISTS "levelRangeMax";
