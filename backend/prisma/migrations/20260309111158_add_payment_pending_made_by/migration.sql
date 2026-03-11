/*
  Warnings:

  - The values [payment_needed] on the enum `BookingStatus` will be removed. If these variants are still used in the database, this will fail.

*/
-- AlterEnum
BEGIN;
CREATE TYPE "BookingStatus_new" AS ENUM ('payment_pending', 'pending', 'confirmed', 'completed', 'canceled');
ALTER TABLE "public"."bookings" ALTER COLUMN "status" DROP DEFAULT;
ALTER TABLE "bookings" ALTER COLUMN "status" TYPE "BookingStatus_new" USING ("status"::text::"BookingStatus_new");
ALTER TYPE "BookingStatus" RENAME TO "BookingStatus_old";
ALTER TYPE "BookingStatus_new" RENAME TO "BookingStatus";
DROP TYPE "public"."BookingStatus_old";
ALTER TABLE "bookings" ALTER COLUMN "status" SET DEFAULT 'payment_pending';
COMMIT;

-- AlterTable
ALTER TABLE "bookings" ADD COLUMN     "madeById" TEXT,
ADD COLUMN     "madeByType" TEXT,
ALTER COLUMN "status" SET DEFAULT 'payment_pending';

-- CreateIndex
CREATE INDEX "bookings_madeByType_idx" ON "bookings"("madeByType");
