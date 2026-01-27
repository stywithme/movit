-- AlterTable
ALTER TABLE "admins" ADD COLUMN "resetToken" TEXT;
ALTER TABLE "admins" ADD COLUMN "resetTokenExpiry" TIMESTAMP(3);
