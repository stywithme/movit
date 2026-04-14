-- CreateTable
CREATE TABLE "user_exercise_preferences" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "customReps" INTEGER,
    "customDurationSec" INTEGER,
    "customWeightKg" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "user_exercise_preferences_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "user_exercise_preferences_userId_exerciseId_key" ON "user_exercise_preferences"("userId", "exerciseId");

-- CreateIndex
CREATE INDEX "user_exercise_preferences_userId_idx" ON "user_exercise_preferences"("userId");

-- CreateIndex
CREATE INDEX "user_exercise_preferences_exerciseId_idx" ON "user_exercise_preferences"("exerciseId");

-- AddForeignKey
ALTER TABLE "user_exercise_preferences" ADD CONSTRAINT "user_exercise_preferences_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_exercise_preferences" ADD CONSTRAINT "user_exercise_preferences_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "exercises"("id") ON DELETE CASCADE ON UPDATE CASCADE;
