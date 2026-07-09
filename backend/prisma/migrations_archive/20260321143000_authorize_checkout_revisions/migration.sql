-- AddColumns
ALTER TABLE "booking_payments"
    ADD COLUMN "revision" INTEGER,
    ADD COLUMN "operationType" TEXT,
    ADD COLUMN "authorizedAt" TIMESTAMP(3),
    ADD COLUMN "releasedAt" TIMESTAMP(3),
    ADD COLUMN "supersededAt" TIMESTAMP(3),
    ADD COLUMN "supersededById" TEXT;

-- Backfill existing rows in creation order per user
WITH ranked AS (
    SELECT
        "id",
        ROW_NUMBER() OVER (PARTITION BY "userId" ORDER BY "createdAt", "id") AS "next_revision"
    FROM "booking_payments"
)
UPDATE "booking_payments" AS bp
SET "revision" = ranked."next_revision"
FROM ranked
WHERE ranked."id" = bp."id";

UPDATE "booking_payments"
SET "revision" = 1
WHERE "revision" IS NULL;

-- Existing rows were created with the legacy PAY flow.
UPDATE "booking_payments"
SET "operationType" = 'PAY'
WHERE "operationType" IS NULL;

ALTER TABLE "booking_payments"
    ALTER COLUMN "revision" SET NOT NULL,
    ALTER COLUMN "revision" SET DEFAULT 1,
    ALTER COLUMN "operationType" SET NOT NULL,
    ALTER COLUMN "operationType" SET DEFAULT 'AUTHORIZE';

-- CreateIndex
CREATE INDEX "booking_payments_myFatoorahInvoiceId_idx" ON "booking_payments"("myFatoorahInvoiceId");

-- CreateIndex
CREATE INDEX "booking_payments_userId_supersededAt_idx" ON "booking_payments"("userId", "supersededAt");

-- CreateIndex
CREATE UNIQUE INDEX "booking_payments_userId_revision_key" ON "booking_payments"("userId", "revision");
