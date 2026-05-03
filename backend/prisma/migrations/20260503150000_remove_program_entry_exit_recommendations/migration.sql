-- Remove program entry/exit JSON gates (replaced by progression assessment template matching).
ALTER TABLE "programs" DROP COLUMN IF EXISTS "entryRecommendations";
ALTER TABLE "programs" DROP COLUMN IF EXISTS "exitRecommendations";
