ALTER TABLE "plans" ALTER COLUMN "currency" SET DEFAULT 'SAR';
ALTER TABLE "subscription_checkouts" ALTER COLUMN "currency" SET DEFAULT 'SAR';

UPDATE "plans"
SET "currency" = 'SAR'
WHERE "currency" = 'EGP';

INSERT INTO "system" ("key", "value", "createdAt", "updatedAt")
VALUES ('currency', 'SAR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT ("key") DO UPDATE
SET "value" = 'SAR',
    "updatedAt" = CURRENT_TIMESTAMP;

INSERT INTO "system" ("key", "value", "createdAt", "updatedAt")
VALUES ('booking_currency', 'SAR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT ("key") DO UPDATE
SET "value" = 'SAR',
    "updatedAt" = CURRENT_TIMESTAMP;
