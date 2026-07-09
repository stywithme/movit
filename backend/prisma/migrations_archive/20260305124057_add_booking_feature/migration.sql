/*
  Warnings:

  - You are about to drop the `system_config` table. If the table is not empty, all the data it contains will be lost.

*/
-- CreateEnum
CREATE TYPE "BookingStatus" AS ENUM ('pending', 'confirmed', 'payment_needed', 'completed', 'canceled');

-- AlterTable
ALTER TABLE "admins" ADD COLUMN     "isDoctor" BOOLEAN NOT NULL DEFAULT false;

-- DropTable
DROP TABLE "system_config";

-- CreateTable
CREATE TABLE "system" (
    "id" SERIAL NOT NULL,
    "key" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "system_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "bookings" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "adminId" TEXT NOT NULL,
    "startAt" TIMESTAMP(3) NOT NULL,
    "endAt" TIMESTAMP(3) NOT NULL,
    "status" "BookingStatus" NOT NULL DEFAULT 'pending',
    "amount" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "isRescheduled" BOOLEAN NOT NULL DEFAULT false,
    "paymentStatus" TEXT,
    "paymentId" TEXT,
    "paymentGateway" TEXT,
    "cancelledBy" TEXT,
    "sessionUrl" TEXT,
    "sessionMeetingId" TEXT,
    "sessionPlatform" TEXT,
    "notes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "bookings_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "doctor_work_times" (
    "id" TEXT NOT NULL,
    "adminId" TEXT NOT NULL,
    "day" TEXT NOT NULL,
    "startTime" TEXT NOT NULL,
    "endTime" TEXT NOT NULL,

    CONSTRAINT "doctor_work_times_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "close_times" (
    "id" TEXT NOT NULL,
    "adminId" TEXT,
    "fromDate" TIMESTAMP(3) NOT NULL,
    "toDate" TIMESTAMP(3) NOT NULL,
    "fromTime" TEXT NOT NULL,
    "toTime" TEXT NOT NULL,

    CONSTRAINT "close_times_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "booking_reports" (
    "id" TEXT NOT NULL,
    "bookingId" TEXT NOT NULL,
    "adminId" TEXT NOT NULL,
    "content" JSONB NOT NULL,
    "attachments" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "booking_reports_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "system_key_key" ON "system"("key");

-- CreateIndex
CREATE INDEX "bookings_userId_idx" ON "bookings"("userId");

-- CreateIndex
CREATE INDEX "bookings_adminId_idx" ON "bookings"("adminId");

-- CreateIndex
CREATE INDEX "bookings_status_idx" ON "bookings"("status");

-- CreateIndex
CREATE INDEX "bookings_startAt_idx" ON "bookings"("startAt");

-- CreateIndex
CREATE INDEX "bookings_adminId_startAt_endAt_idx" ON "bookings"("adminId", "startAt", "endAt");

-- CreateIndex
CREATE INDEX "doctor_work_times_adminId_idx" ON "doctor_work_times"("adminId");

-- CreateIndex
CREATE INDEX "doctor_work_times_adminId_day_idx" ON "doctor_work_times"("adminId", "day");

-- CreateIndex
CREATE INDEX "close_times_adminId_idx" ON "close_times"("adminId");

-- CreateIndex
CREATE INDEX "close_times_fromDate_toDate_idx" ON "close_times"("fromDate", "toDate");

-- CreateIndex
CREATE INDEX "booking_reports_bookingId_idx" ON "booking_reports"("bookingId");

-- CreateIndex
CREATE INDEX "booking_reports_adminId_idx" ON "booking_reports"("adminId");

-- AddForeignKey
ALTER TABLE "bookings" ADD CONSTRAINT "bookings_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "bookings" ADD CONSTRAINT "bookings_adminId_fkey" FOREIGN KEY ("adminId") REFERENCES "admins"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "doctor_work_times" ADD CONSTRAINT "doctor_work_times_adminId_fkey" FOREIGN KEY ("adminId") REFERENCES "admins"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "close_times" ADD CONSTRAINT "close_times_adminId_fkey" FOREIGN KEY ("adminId") REFERENCES "admins"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "booking_reports" ADD CONSTRAINT "booking_reports_bookingId_fkey" FOREIGN KEY ("bookingId") REFERENCES "bookings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "booking_reports" ADD CONSTRAINT "booking_reports_adminId_fkey" FOREIGN KEY ("adminId") REFERENCES "admins"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
