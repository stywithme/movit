-- P0.1: allow null stability/alignment metrics from mobile (missing samples ≠ fake zeros).
ALTER TABLE "workout_execution_metrics" ALTER COLUMN "avgStability" DROP NOT NULL;
ALTER TABLE "workout_execution_metrics" ALTER COLUMN "avgAlignmentAccuracy" DROP NOT NULL;
ALTER TABLE "rep_metrics" ALTER COLUMN "stability" DROP NOT NULL;
ALTER TABLE "rep_metrics" ALTER COLUMN "alignmentAccuracy" DROP NOT NULL;
