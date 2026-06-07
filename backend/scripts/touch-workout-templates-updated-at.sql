UPDATE "workout_templates"
SET "updatedAt" = CURRENT_TIMESTAMP
WHERE "deletedAt" IS NULL
  AND status = 'published';
