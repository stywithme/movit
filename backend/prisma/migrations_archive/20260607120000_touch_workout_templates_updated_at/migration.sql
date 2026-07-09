-- After workout domain rename, catalog rows keep their old updatedAt timestamps.
-- Incremental mobile sync (updatedAfter) would skip them until edited in the dashboard.
-- Bump updatedAt so the next sync delivers all published workout templates.

UPDATE "workout_templates"
SET "updatedAt" = CURRENT_TIMESTAMP
WHERE "deletedAt" IS NULL
  AND status = 'published';
