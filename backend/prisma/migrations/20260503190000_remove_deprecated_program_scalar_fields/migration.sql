-- Remove deprecated Program scalar columns (replaced by ProgramAttribute + level range).

DROP INDEX IF EXISTS "programs_programDomain_idx";

ALTER TABLE "programs" DROP COLUMN IF EXISTS "difficulty";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "contraindications";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "targetDomain";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "targetRegions";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "targetEquipment";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "programDomain";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "trainingGoal";
