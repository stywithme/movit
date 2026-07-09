-- Add 3-axis columns to pose_positions
ALTER TABLE "pose_positions" ADD COLUMN "postures" JSONB NOT NULL DEFAULT '["any"]';
ALTER TABLE "pose_positions" ADD COLUMN "directions" JSONB NOT NULL DEFAULT '["any"]';
ALTER TABLE "pose_positions" ADD COLUMN "regions" JSONB NOT NULL DEFAULT '["any"]';

-- Populate from existing codes
UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["front"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'standing_front';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["back"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'standing_back';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["side"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'standing_side';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["side_left"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'standing_side_left';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["side_right"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'standing_side_right';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["front","side"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'standing_diagonal';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["front"]',
  "regions"     = '["upper_body"]'
WHERE "code" = 'standing_front_upper';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["back"]',
  "regions"     = '["upper_body"]'
WHERE "code" = 'standing_back_upper';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["side"]',
  "regions"     = '["upper_body"]'
WHERE "code" = 'standing_side_upper';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["front"]',
  "regions"     = '["lower_body"]'
WHERE "code" = 'standing_front_lower';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["back"]',
  "regions"     = '["lower_body"]'
WHERE "code" = 'standing_back_lower';

UPDATE "pose_positions" SET
  "postures"   = '["standing"]',
  "directions"  = '["side"]',
  "regions"     = '["lower_body"]'
WHERE "code" = 'standing_side_lower';

UPDATE "pose_positions" SET
  "postures"   = '["lying_prone"]',
  "directions"  = '["side"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'prone_side';

UPDATE "pose_positions" SET
  "postures"   = '["lying_prone"]',
  "directions"  = '["front"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'prone_front';

UPDATE "pose_positions" SET
  "postures"   = '["lying_supine"]',
  "directions"  = '["side"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'supine_side';

UPDATE "pose_positions" SET
  "postures"   = '["lying_supine"]',
  "directions"  = '["front"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'supine_front';

UPDATE "pose_positions" SET
  "postures"   = '["lying_side"]',
  "directions"  = '["side"]',
  "regions"     = '["full_body"]'
WHERE "code" = 'side_lying';
