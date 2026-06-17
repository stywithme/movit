-- App Store subscription fields (iOS StoreKit 2 parity)
ALTER TABLE "plans"
  ADD COLUMN IF NOT EXISTS "monthlyAppStoreProductId" TEXT,
  ADD COLUMN IF NOT EXISTS "yearlyAppStoreProductId" TEXT;

ALTER TABLE "subscriptions"
  ADD COLUMN IF NOT EXISTS "appStoreProductId" TEXT,
  ADD COLUMN IF NOT EXISTS "appStoreTransactionId" TEXT,
  ADD COLUMN IF NOT EXISTS "appStoreOriginalTransactionId" TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS "subscriptions_appStoreTransactionId_key"
  ON "subscriptions"("appStoreTransactionId")
  WHERE "appStoreTransactionId" IS NOT NULL;

CREATE INDEX IF NOT EXISTS "subscriptions_appStoreProductId_idx"
  ON "subscriptions"("appStoreProductId");

CREATE INDEX IF NOT EXISTS "subscriptions_appStoreOriginalTransactionId_idx"
  ON "subscriptions"("appStoreOriginalTransactionId");
