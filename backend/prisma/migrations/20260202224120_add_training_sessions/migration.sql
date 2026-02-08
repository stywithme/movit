-- CreateTable
CREATE TABLE "training_sessions" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL,
    "durationMs" INTEGER NOT NULL,
    "totalReps" INTEGER NOT NULL,
    "countedReps" INTEGER NOT NULL,
    "invalidReps" INTEGER NOT NULL,
    "weightKg" DOUBLE PRECISION,
    "weightUnit" TEXT NOT NULL DEFAULT 'kg',
    "sessionMetrics" JSONB NOT NULL,
    "repMetrics" JSONB NOT NULL,
    "legacyReport" JSONB,
    "deviceId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "training_sessions_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "training_sessions_userId_idx" ON "training_sessions"("userId");

-- CreateIndex
CREATE INDEX "training_sessions_exerciseId_idx" ON "training_sessions"("exerciseId");

-- CreateIndex
CREATE INDEX "training_sessions_timestamp_idx" ON "training_sessions"("timestamp");

-- CreateIndex
CREATE INDEX "training_sessions_userId_exerciseId_idx" ON "training_sessions"("userId", "exerciseId");

-- AddForeignKey
ALTER TABLE "training_sessions" ADD CONSTRAINT "training_sessions_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "training_sessions" ADD CONSTRAINT "training_sessions_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
