-- Complete Unified Refactor: program_phases table, enforce session.role, drop legacy item-level prescription columns, drop SessionItemRole enum.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Create program_phases table (matches Prisma ProgramPhase model)
CREATE TABLE IF NOT EXISTS "program_phases" (
  "id" TEXT NOT NULL,
  "programId" TEXT NOT NULL,
  "name" JSONB NOT NULL,
  "description" JSONB,
  "weekType" "WeekType" NOT NULL DEFAULT 'NORMAL',
  "startWeek" INTEGER NOT NULL,
  "endWeek" INTEGER NOT NULL,
  "sortOrder" INTEGER NOT NULL DEFAULT 0,
  "weeklyPattern" JSONB,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "program_phases_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "program_phases_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS "program_phases_programId_sortOrder_key" ON "program_phases"("programId", "sortOrder");
CREATE INDEX IF NOT EXISTS "program_phases_programId_idx" ON "program_phases"("programId");

-- 2. Backfill one default phase per non-deleted program (skip if already has phases)
INSERT INTO "program_phases" ("id", "programId", "name", "weekType", "startWeek", "endWeek", "sortOrder", "createdAt", "updatedAt")
SELECT gen_random_uuid()::text,
       p."id",
       jsonb_build_object('en', 'Phase 1', 'ar', 'المرحلة 1'),
       'NORMAL'::"WeekType",
       1,
       GREATEST(p."durationWeeks", 1),
       0,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM "programs" p
WHERE p."deletedAt" IS NULL
  AND NOT EXISTS (SELECT 1 FROM "program_phases" pf WHERE pf."programId" = p."id");

-- 3. Ensure ProgramSession.role exists and is populated (idempotent)
UPDATE "program_sessions" SET "role" = 'MAIN'::"SessionRole" WHERE "role" IS NULL;

ALTER TABLE "program_sessions" ALTER COLUMN "role" SET DEFAULT 'MAIN'::"SessionRole";
ALTER TABLE "program_sessions" ALTER COLUMN "role" SET NOT NULL;

-- 4. Drop sessionCategory (replaced by role)
ALTER TABLE "program_sessions" DROP COLUMN IF EXISTS "sessionCategory";

-- 5. Drop FK on progressionRuleId if present, then drop legacy columns on program_session_items
ALTER TABLE "program_session_items" DROP CONSTRAINT IF EXISTS "program_session_items_progressionRuleId_fkey";

ALTER TABLE "program_session_items"
  DROP COLUMN IF EXISTS "role",
  DROP COLUMN IF EXISTS "intent",
  DROP COLUMN IF EXISTS "coachingNotes",
  DROP COLUMN IF EXISTS "allowedSubstitutions",
  DROP COLUMN IF EXISTS "difficultyCode",
  DROP COLUMN IF EXISTS "isPersonalized",
  DROP COLUMN IF EXISTS "levelOverride",
  DROP COLUMN IF EXISTS "progressionRuleId",
  DROP COLUMN IF EXISTS "targetFormScore",
  DROP COLUMN IF EXISTS "targetROM";

-- 6. Drop legacy enum (no remaining columns use SessionItemRole)
DROP TYPE IF EXISTS "SessionItemRole";
