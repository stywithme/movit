-- Migrate per-exercise reference images to shared pose position image
-- For any pose_variant that has a referenceImageUrl but its parent pose_position
-- does NOT have an imageUrl, copy the variant's image to the position.
UPDATE "pose_positions" pp
SET "imageUrl" = sub."referenceImageUrl",
    "updatedAt" = NOW()
FROM (
  SELECT DISTINCT ON (pv."posePositionId")
    pv."posePositionId",
    pv."referenceImageUrl"
  FROM "pose_variants" pv
  WHERE pv."referenceImageUrl" IS NOT NULL
    AND pv."referenceImageUrl" != ''
  ORDER BY pv."posePositionId", pv."sortOrder" ASC
) sub
WHERE pp.id = sub."posePositionId"
  AND (pp."imageUrl" IS NULL OR pp."imageUrl" = '');

-- Drop per-exercise reference image column (single source is now pose_positions.imageUrl)
ALTER TABLE "pose_variants" DROP COLUMN IF EXISTS "referenceImageUrl";

-- Clear description from pose_positions (no longer used in admin/mobile)
UPDATE "pose_positions" SET "description" = NULL;

-- Touch all exercises so mobile sync picks up the change
UPDATE "exercises"
SET "updatedAt" = NOW()
WHERE "deletedAt" IS NULL;
