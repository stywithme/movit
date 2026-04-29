-- AlterTable
ALTER TABLE "user_programs" ADD COLUMN "pausedAt" TIMESTAMP(3),
ADD COLUMN "totalPausedDays" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN "customizationsUpdatedAt" TIMESTAMP(3);

-- AlterTable
ALTER TABLE "program_session_reports" ADD COLUMN "rpe" INTEGER;
