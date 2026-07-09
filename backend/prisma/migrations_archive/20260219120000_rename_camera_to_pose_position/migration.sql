-- Rename camera_positions -> pose_positions
ALTER TABLE "camera_positions" RENAME TO "pose_positions";

-- Remove the schemaCode column (no longer needed; code IS the schema code now)
ALTER TABLE "pose_positions" DROP COLUMN IF EXISTS "schemaCode";

-- Rename camera_position_joints -> pose_position_joints
ALTER TABLE "camera_position_joints" RENAME TO "pose_position_joints";

-- Rename FK column in pose_position_joints
ALTER TABLE "pose_position_joints" RENAME COLUMN "cameraPositionId" TO "posePositionId";

-- Rename FK column in pose_variants
ALTER TABLE "pose_variants" RENAME COLUMN "cameraPositionId" TO "posePositionId";

-- Drop expectedFacingDirection from pose_variants (mobile auto-detects)
ALTER TABLE "pose_variants" DROP COLUMN IF EXISTS "expectedFacingDirection";

-- Drop old unique index temporarily to allow code remapping collisions
DROP INDEX IF EXISTS "camera_positions_code_key";

-- Migrate existing position codes to new vocabulary
UPDATE "pose_positions" SET "code" = 'standing_side' WHERE "code" IN ('side_left', 'side_right');
UPDATE "pose_positions" SET "code" = 'standing_front' WHERE "code" = 'front';
UPDATE "pose_positions" SET "code" = 'standing_back'  WHERE "code" = 'back';

-- Deduplicate: side_left and side_right now both map to standing_side.
-- Keep only one row (the one with lower sortOrder) and reassign FKs.
DO $$
DECLARE
  keep_id TEXT;
  remove_id TEXT;
BEGIN
  -- Find duplicates
  SELECT id INTO keep_id FROM "pose_positions" WHERE "code" = 'standing_side' ORDER BY "sortOrder" ASC LIMIT 1;
  FOR remove_id IN SELECT id FROM "pose_positions" WHERE "code" = 'standing_side' AND id != keep_id LOOP
    -- Move pose_variants pointing to the duplicate
    UPDATE "pose_variants" SET "posePositionId" = keep_id WHERE "posePositionId" = remove_id;
    -- Move joints pointing to the duplicate
    DELETE FROM "pose_position_joints" WHERE "posePositionId" = remove_id;
    -- Delete the duplicate pose_position row
    DELETE FROM "pose_positions" WHERE id = remove_id;
  END LOOP;
END $$;

-- Update the kept standing_side row with correct name
UPDATE "pose_positions"
SET "name" = '{"ar": "جانبي - واقف", "en": "Standing Side"}'::jsonb,
    "description" = '{"ar": "الجسم بالكامل من الجانب", "en": "Full body from the side"}'::jsonb
WHERE "code" = 'standing_side';

UPDATE "pose_positions"
SET "name" = '{"ar": "أمامي - واقف", "en": "Standing Front"}'::jsonb,
    "description" = '{"ar": "الجسم بالكامل من الأمام", "en": "Full body from front"}'::jsonb
WHERE "code" = 'standing_front';

UPDATE "pose_positions"
SET "name" = '{"ar": "خلفي - واقف", "en": "Standing Back"}'::jsonb,
    "description" = '{"ar": "الجسم بالكامل من الخلف", "en": "Full body from back"}'::jsonb
WHERE "code" = 'standing_back';

-- Rename indexes
DROP INDEX IF EXISTS "camera_positions_code_key";
CREATE UNIQUE INDEX "pose_positions_code_key" ON "pose_positions"("code");

DROP INDEX IF EXISTS "camera_position_joints_cameraPositionId_idx";
CREATE INDEX "pose_position_joints_posePositionId_idx" ON "pose_position_joints"("posePositionId");

DROP INDEX IF EXISTS "camera_position_joints_jointId_idx";
CREATE INDEX "pose_position_joints_jointId_idx" ON "pose_position_joints"("jointId");

DROP INDEX IF EXISTS "camera_position_joints_cameraPositionId_jointId_key";
CREATE UNIQUE INDEX "pose_position_joints_posePositionId_jointId_key" ON "pose_position_joints"("posePositionId", "jointId");

DROP INDEX IF EXISTS "pose_variants_cameraPositionId_idx";
CREATE INDEX "pose_variants_posePositionId_idx" ON "pose_variants"("posePositionId");

-- Rename constraints
ALTER TABLE "pose_position_joints" DROP CONSTRAINT IF EXISTS "camera_position_joints_cameraPositionId_fkey";
ALTER TABLE "pose_position_joints" ADD CONSTRAINT "pose_position_joints_posePositionId_fkey"
  FOREIGN KEY ("posePositionId") REFERENCES "pose_positions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "pose_position_joints" DROP CONSTRAINT IF EXISTS "camera_position_joints_jointId_fkey";
ALTER TABLE "pose_position_joints" ADD CONSTRAINT "pose_position_joints_jointId_fkey"
  FOREIGN KEY ("jointId") REFERENCES "attribute_values"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "pose_variants" DROP CONSTRAINT IF EXISTS "pose_variants_cameraPositionId_fkey";
ALTER TABLE "pose_variants" ADD CONSTRAINT "pose_variants_posePositionId_fkey"
  FOREIGN KEY ("posePositionId") REFERENCES "pose_positions"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
