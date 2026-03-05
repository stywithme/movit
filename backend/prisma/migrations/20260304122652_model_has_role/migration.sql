/*
  Warnings:

  - You are about to drop the column `roleId` on the `admins` table. All the data in the column will be lost.

*/
-- DropForeignKey
ALTER TABLE "admins" DROP CONSTRAINT "admins_roleId_fkey";

-- DropIndex
DROP INDEX "admins_roleId_idx";

-- AlterTable
ALTER TABLE "admins" DROP COLUMN "roleId";

-- CreateTable
CREATE TABLE "model_has_roles" (
    "roleId" TEXT NOT NULL,
    "modelId" TEXT NOT NULL,
    "modelType" TEXT NOT NULL,

    CONSTRAINT "model_has_roles_pkey" PRIMARY KEY ("roleId","modelId","modelType")
);

-- CreateIndex
CREATE INDEX "model_has_roles_modelId_modelType_idx" ON "model_has_roles"("modelId", "modelType");

-- CreateIndex
CREATE INDEX "model_has_roles_roleId_idx" ON "model_has_roles"("roleId");

-- AddForeignKey
ALTER TABLE "model_has_roles" ADD CONSTRAINT "model_has_roles_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES "roles"("id") ON DELETE CASCADE ON UPDATE CASCADE;
