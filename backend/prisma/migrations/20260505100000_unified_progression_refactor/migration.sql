-- Unified Progression Refactor - Step 1: Schema additions + data backfill
-- This migration adds new columns/enum (nullable) and backfills data from old item-level fields.
-- Old columns on ProgramSessionItem remain for now (dropped in later migration).

-- 1. Create new enum SessionRole (independent of SessionItemRole)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'SessionRole') THEN
    CREATE TYPE "SessionRole" AS ENUM ('WARMUP', 'ACTIVATION', 'MAIN', 'ACCESSORY', 'CORRECTIVE', 'COOLDOWN', 'TEST');
  END IF;
END $$;

-- 2. Add role column to program_sessions (nullable for backfill phase)
ALTER TABLE "program_sessions" ADD COLUMN IF NOT EXISTS "role" "SessionRole";

-- 3. Add intent and coachingNotes to exercises (nullable)
ALTER TABLE "exercises" ADD COLUMN IF NOT EXISTS "intent" "SessionItemIntent";
ALTER TABLE "exercises" ADD COLUMN IF NOT EXISTS "coachingNotes" JSONB;

-- 4. Backfill ProgramSession.role from the most common role in its items
-- (or first non-null role)
UPDATE "program_sessions" ps
SET "role" = (
  SELECT "role"
  FROM "program_session_items" psi
  WHERE psi."sessionId" = ps."id" AND psi."role" IS NOT NULL
  ORDER BY psi."sortOrder" ASC
  LIMIT 1
)
WHERE ps."role" IS NULL;

-- For sessions with no items or no roles: default to MAIN
UPDATE "program_sessions"
SET "role" = 'MAIN'
WHERE "role" IS NULL;

-- 5. Backfill Exercise.intent from any item that had it
UPDATE "exercises" e
SET "intent" = (
  SELECT "intent"
  FROM "program_session_items" psi
  WHERE psi."exerciseId" = e."id" AND psi."intent" IS NOT NULL
  LIMIT 1
)
WHERE e."intent" IS NULL;

-- 6. Backfill Exercise.coachingNotes from any item
UPDATE "exercises" e
SET "coachingNotes" = (
  SELECT "coachingNotes"
  FROM "program_session_items" psi
  WHERE psi."exerciseId" = e."id" AND psi."coachingNotes" IS NOT NULL
  LIMIT 1
)
WHERE e."coachingNotes" IS NULL;

-- Note: After this migration, the application code should be updated to read/write the new fields.
-- The next migration will drop the old columns and enforce NOT NULL where appropriate.