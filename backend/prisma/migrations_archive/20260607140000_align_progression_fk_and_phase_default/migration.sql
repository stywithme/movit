-- Align DB with Prisma schema for two pre-existing mismatches.
-- NOTE: these mismatches exist on ALL environments (including production), because they
-- come from the migration history not matching schema.prisma — NOT from this dev DB.

-- 1) progression_history.ruleId is an OPTIONAL relation (ruleId String?), so Prisma expects
--    ON DELETE SET NULL. The original migration (20260216230023) created the FK as ON DELETE
--    CASCADE back when ruleId was required; making it nullable (20260402140813) did not fix the
--    referential action. Deleting a progression rule should null out history, not delete it.
ALTER TABLE "progression_history" DROP CONSTRAINT "progression_history_ruleId_fkey";
ALTER TABLE "progression_history" ADD CONSTRAINT "progression_history_ruleId_fkey"
  FOREIGN KEY ("ruleId") REFERENCES "progression_rules"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- 2) program_phases.updatedAt is Prisma @updatedAt (managed in the app layer). The create-table
--    migration (20260505200000) added a DB-level DEFAULT CURRENT_TIMESTAMP for backfill; the
--    schema does not declare a default, so drop it to match.
ALTER TABLE "program_phases" ALTER COLUMN "updatedAt" DROP DEFAULT;
