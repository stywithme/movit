-- CreateEnum
CREATE TYPE "ProgramAttributeMode" AS ENUM ('REQUIRED', 'OPTIONAL', 'EXCLUDED');

-- CreateTable
CREATE TABLE "program_attributes" (
    "id" TEXT NOT NULL,
    "programId" TEXT NOT NULL,
    "attributeValueId" TEXT NOT NULL,
    "mode" "ProgramAttributeMode" NOT NULL DEFAULT 'REQUIRED',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "program_attributes_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "program_attributes_programId_attributeValueId_key" ON "program_attributes"("programId", "attributeValueId");

-- CreateIndex
CREATE INDEX "program_attributes_programId_idx" ON "program_attributes"("programId");

-- CreateIndex
CREATE INDEX "program_attributes_attributeValueId_idx" ON "program_attributes"("attributeValueId");

-- AddForeignKey
ALTER TABLE "program_attributes" ADD CONSTRAINT "program_attributes_programId_fkey" FOREIGN KEY ("programId") REFERENCES "programs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "program_attributes" ADD CONSTRAINT "program_attributes_attributeValueId_fkey" FOREIGN KEY ("attributeValueId") REFERENCES "attribute_values"("id") ON DELETE CASCADE ON UPDATE CASCADE;
