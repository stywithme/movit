-- AlterTable
ALTER TABLE "attribute_values" ADD COLUMN     "metadata" JSONB;

-- AlterTable
ALTER TABLE "exercises" ADD COLUMN     "defaultWeight" DOUBLE PRECISION,
ADD COLUMN     "maxWeight" DOUBLE PRECISION,
ADD COLUMN     "minWeight" DOUBLE PRECISION,
ADD COLUMN     "reportMetrics" JSONB,
ADD COLUMN     "supportsWeight" BOOLEAN NOT NULL DEFAULT false;
