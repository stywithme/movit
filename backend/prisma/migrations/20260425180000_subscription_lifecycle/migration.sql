-- Add mobile subscription lifecycle fields and checkout audit records.

ALTER TABLE "plans"
  ADD COLUMN IF NOT EXISTS "currency" TEXT NOT NULL DEFAULT 'EGP',
  ADD COLUMN IF NOT EXISTS "monthlyGooglePlayProductId" TEXT,
  ADD COLUMN IF NOT EXISTS "yearlyGooglePlayProductId" TEXT,
  ADD COLUMN IF NOT EXISTS "features" JSONB;

ALTER TABLE "subscriptions"
  ADD COLUMN IF NOT EXISTS "billingPeriod" TEXT NOT NULL DEFAULT 'monthly',
  ADD COLUMN IF NOT EXISTS "gateway" TEXT NOT NULL DEFAULT 'manual',
  ADD COLUMN IF NOT EXISTS "currentPeriodStart" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "currentPeriodEnd" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "autoRenew" BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS "cancelAtPeriodEnd" BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS "cancelledAt" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "upgradedFromId" TEXT,
  ADD COLUMN IF NOT EXISTS "googlePlayPackageName" TEXT,
  ADD COLUMN IF NOT EXISTS "googlePlayProductId" TEXT,
  ADD COLUMN IF NOT EXISTS "googlePlayPurchaseToken" TEXT,
  ADD COLUMN IF NOT EXISTS "googlePlayOrderId" TEXT,
  ADD COLUMN IF NOT EXISTS "myFatoorahInvoiceId" TEXT,
  ADD COLUMN IF NOT EXISTS "myFatoorahPaymentId" TEXT,
  ADD COLUMN IF NOT EXISTS "lastVerifiedAt" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "metadata" JSONB;

CREATE UNIQUE INDEX IF NOT EXISTS "subscriptions_googlePlayPurchaseToken_key"
  ON "subscriptions"("googlePlayPurchaseToken")
  WHERE "googlePlayPurchaseToken" IS NOT NULL;

CREATE INDEX IF NOT EXISTS "subscriptions_userId_status_idx" ON "subscriptions"("userId", "status");
CREATE INDEX IF NOT EXISTS "subscriptions_endDate_idx" ON "subscriptions"("endDate");
CREATE INDEX IF NOT EXISTS "subscriptions_googlePlayProductId_idx" ON "subscriptions"("googlePlayProductId");
CREATE INDEX IF NOT EXISTS "subscriptions_myFatoorahInvoiceId_idx" ON "subscriptions"("myFatoorahInvoiceId");
CREATE INDEX IF NOT EXISTS "subscriptions_myFatoorahPaymentId_idx" ON "subscriptions"("myFatoorahPaymentId");

CREATE TABLE IF NOT EXISTS "subscription_checkouts" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "planId" TEXT NOT NULL,
  "subscriptionId" TEXT,
  "gateway" TEXT NOT NULL DEFAULT 'myfatoorah',
  "billingPeriod" TEXT NOT NULL,
  "status" TEXT NOT NULL DEFAULT 'pending',
  "currency" TEXT NOT NULL DEFAULT 'EGP',
  "amount" DECIMAL(10,2) NOT NULL,
  "paymentUrl" TEXT,
  "myFatoorahInvoiceId" TEXT,
  "myFatoorahPaymentId" TEXT,
  "googlePlayProductId" TEXT,
  "googlePlayPurchaseToken" TEXT,
  "idempotencyKey" TEXT,
  "expiresAt" TIMESTAMP(3),
  "paidAt" TIMESTAMP(3),
  "cancelledAt" TIMESTAMP(3),
  "failedAt" TIMESTAMP(3),
  "lastError" TEXT,
  "rawPayload" JSONB,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,

  CONSTRAINT "subscription_checkouts_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "subscription_checkouts_idempotencyKey_key"
  ON "subscription_checkouts"("idempotencyKey")
  WHERE "idempotencyKey" IS NOT NULL;

CREATE INDEX IF NOT EXISTS "subscription_checkouts_userId_idx" ON "subscription_checkouts"("userId");
CREATE INDEX IF NOT EXISTS "subscription_checkouts_planId_idx" ON "subscription_checkouts"("planId");
CREATE INDEX IF NOT EXISTS "subscription_checkouts_subscriptionId_idx" ON "subscription_checkouts"("subscriptionId");
CREATE INDEX IF NOT EXISTS "subscription_checkouts_gateway_idx" ON "subscription_checkouts"("gateway");
CREATE INDEX IF NOT EXISTS "subscription_checkouts_status_idx" ON "subscription_checkouts"("status");
CREATE INDEX IF NOT EXISTS "subscription_checkouts_myFatoorahInvoiceId_idx" ON "subscription_checkouts"("myFatoorahInvoiceId");
CREATE INDEX IF NOT EXISTS "subscription_checkouts_myFatoorahPaymentId_idx" ON "subscription_checkouts"("myFatoorahPaymentId");
CREATE INDEX IF NOT EXISTS "subscription_checkouts_googlePlayPurchaseToken_idx" ON "subscription_checkouts"("googlePlayPurchaseToken");

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'subscription_checkouts_userId_fkey'
  ) THEN
    ALTER TABLE "subscription_checkouts"
      ADD CONSTRAINT "subscription_checkouts_userId_fkey"
      FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'subscription_checkouts_planId_fkey'
  ) THEN
    ALTER TABLE "subscription_checkouts"
      ADD CONSTRAINT "subscription_checkouts_planId_fkey"
      FOREIGN KEY ("planId") REFERENCES "plans"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'subscription_checkouts_subscriptionId_fkey'
  ) THEN
    ALTER TABLE "subscription_checkouts"
      ADD CONSTRAINT "subscription_checkouts_subscriptionId_fkey"
      FOREIGN KEY ("subscriptionId") REFERENCES "subscriptions"("id") ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
