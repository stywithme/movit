-- AlterTable
ALTER TABLE "pose_position_joints" RENAME CONSTRAINT "camera_position_joints_pkey" TO "pose_position_joints_pkey";

-- AlterTable
ALTER TABLE "pose_positions" RENAME CONSTRAINT "camera_positions_pkey" TO "pose_positions_pkey";
