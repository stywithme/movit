-- Add isFeatured column to exercises, workouts, and programs tables

ALTER TABLE "exercises" ADD COLUMN IF NOT EXISTS "isFeatured" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "workouts" ADD COLUMN IF NOT EXISTS "isFeatured" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "programs" ADD COLUMN IF NOT EXISTS "isFeatured" BOOLEAN NOT NULL DEFAULT false;
