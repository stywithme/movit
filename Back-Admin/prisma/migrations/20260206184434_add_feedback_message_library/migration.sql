-- CreateTable
CREATE TABLE "feedback_message_templates" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "context" TEXT,
    "content" JSONB NOT NULL,
    "tags" TEXT[],
    "isSystem" BOOLEAN NOT NULL DEFAULT false,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "feedback_message_templates_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "feedback_message_assignments" (
    "id" TEXT NOT NULL,
    "poseVariantId" TEXT NOT NULL,
    "messageId" TEXT NOT NULL,
    "target" TEXT NOT NULL,
    "context" TEXT,
    "jointCode" TEXT,
    "zone" TEXT,
    "checkId" TEXT,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "feedback_message_assignments_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "feedback_message_templates_code_key" ON "feedback_message_templates"("code");

-- CreateIndex
CREATE INDEX "feedback_message_templates_category_context_idx" ON "feedback_message_templates"("category", "context");

-- CreateIndex
CREATE INDEX "feedback_message_templates_isActive_idx" ON "feedback_message_templates"("isActive");

-- CreateIndex
CREATE INDEX "feedback_message_assignments_poseVariantId_idx" ON "feedback_message_assignments"("poseVariantId");

-- CreateIndex
CREATE INDEX "feedback_message_assignments_messageId_idx" ON "feedback_message_assignments"("messageId");

-- CreateIndex
CREATE INDEX "feedback_message_assignments_target_context_idx" ON "feedback_message_assignments"("target", "context");

-- AddForeignKey
ALTER TABLE "feedback_message_assignments" ADD CONSTRAINT "feedback_message_assignments_poseVariantId_fkey" FOREIGN KEY ("poseVariantId") REFERENCES "pose_variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "feedback_message_assignments" ADD CONSTRAINT "feedback_message_assignments_messageId_fkey" FOREIGN KEY ("messageId") REFERENCES "feedback_message_templates"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
