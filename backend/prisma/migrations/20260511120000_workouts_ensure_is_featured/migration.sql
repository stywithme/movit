-- Idempotent: add isFeatured on workouts if missing (default false for existing rows; no seed).
ALTER TABLE "workouts" ADD COLUMN IF NOT EXISTS "isFeatured" BOOLEAN NOT NULL DEFAULT false;
