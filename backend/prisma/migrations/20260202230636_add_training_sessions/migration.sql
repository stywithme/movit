/*
  Warnings:

  - You are about to drop the column `repMetrics` on the `training_sessions` table. All the data in the column will be lost.
  - You are about to drop the column `sessionMetrics` on the `training_sessions` table. All the data in the column will be lost.

*/
-- AlterTable
ALTER TABLE "training_sessions" DROP COLUMN "repMetrics",
DROP COLUMN "sessionMetrics";

-- CreateTable
CREATE TABLE "session_metrics" (
    "id" TEXT NOT NULL,
    "sessionId" TEXT NOT NULL,
    "avgRom" INTEGER NOT NULL,
    "avgSymmetry" INTEGER,
    "avgStability" INTEGER NOT NULL,
    "avgVelocity" INTEGER,
    "avgFormScore" INTEGER NOT NULL,
    "avgAlignmentAccuracy" INTEGER NOT NULL,
    "avgTempo" JSONB NOT NULL,
    "totalTUT" INTEGER NOT NULL,
    "totalVolume" DOUBLE PRECISION,
    "maxWeight" DOUBLE PRECISION,
    "est1RM" DOUBLE PRECISION,
    "relativeStrength" DOUBLE PRECISION,
    "intensityPercentage" DOUBLE PRECISION,
    "formConsistency" INTEGER,
    "fatigueIndex" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "session_metrics_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "rep_metrics" (
    "id" TEXT NOT NULL,
    "sessionId" TEXT NOT NULL,
    "repNumber" INTEGER NOT NULL,
    "durationMs" INTEGER NOT NULL,
    "worstState" INTEGER NOT NULL,
    "score" INTEGER NOT NULL,
    "weightKg" DOUBLE PRECISION,
    "rom" INTEGER NOT NULL,
    "symmetry" INTEGER,
    "stability" INTEGER NOT NULL,
    "velocity" INTEGER,
    "formScore" INTEGER NOT NULL,
    "alignmentAccuracy" INTEGER NOT NULL,
    "tempo" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "rep_metrics_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "session_metrics_sessionId_key" ON "session_metrics"("sessionId");

-- CreateIndex
CREATE INDEX "rep_metrics_sessionId_idx" ON "rep_metrics"("sessionId");

-- CreateIndex
CREATE INDEX "rep_metrics_sessionId_repNumber_idx" ON "rep_metrics"("sessionId", "repNumber");

-- AddForeignKey
ALTER TABLE "session_metrics" ADD CONSTRAINT "session_metrics_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "training_sessions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "rep_metrics" ADD CONSTRAINT "rep_metrics_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "training_sessions"("id") ON DELETE CASCADE ON UPDATE CASCADE;
