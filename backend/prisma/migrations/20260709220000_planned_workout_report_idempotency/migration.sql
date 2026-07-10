-- P1.3: client idempotency keys for planned workout reports + overrides.
-- PostgreSQL UNIQUE allows multiple NULLs, so legacy rows without a key remain valid.

ALTER TABLE "planned_workout_reports" ADD COLUMN IF NOT EXISTS "idempotencyKey" TEXT;
ALTER TABLE "user_program_overrides" ADD COLUMN IF NOT EXISTS "idempotencyKey" TEXT;

-- Safety: clear accidental duplicate non-null keys before unique index (keep newest).
-- No-op when column is freshly added (all NULL).
WITH ranked_reports AS (
  SELECT
    id,
    ROW_NUMBER() OVER (
      PARTITION BY "userId", "idempotencyKey"
      ORDER BY "updatedAt" DESC NULLS LAST, "createdAt" DESC NULLS LAST, id DESC
    ) AS rn
  FROM "planned_workout_reports"
  WHERE "idempotencyKey" IS NOT NULL
)
UPDATE "planned_workout_reports" AS p
SET "idempotencyKey" = NULL
FROM ranked_reports r
WHERE p.id = r.id AND r.rn > 1;

WITH ranked_overrides AS (
  SELECT
    id,
    ROW_NUMBER() OVER (
      PARTITION BY "userProgramId", "idempotencyKey"
      ORDER BY "createdAt" DESC NULLS LAST, id DESC
    ) AS rn
  FROM "user_program_overrides"
  WHERE "idempotencyKey" IS NOT NULL
)
UPDATE "user_program_overrides" AS o
SET "idempotencyKey" = NULL
FROM ranked_overrides r
WHERE o.id = r.id AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS "planned_workout_reports_userId_idempotencyKey_key"
  ON "planned_workout_reports" ("userId", "idempotencyKey");

CREATE UNIQUE INDEX IF NOT EXISTS "user_program_overrides_userProgramId_idempotencyKey_key"
  ON "user_program_overrides" ("userProgramId", "idempotencyKey");
