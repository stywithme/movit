-- Simplify program structure: drop phases, rename week name -> target, add day muscle links

DROP TABLE IF EXISTS "program_phases";

ALTER TABLE "program_weeks" RENAME COLUMN "name" TO "target";
ALTER TABLE "program_weeks" DROP COLUMN IF EXISTS "weekType";

ALTER TABLE "program_days" DROP COLUMN IF EXISTS "name";
ALTER TABLE "program_days" DROP COLUMN IF EXISTS "dayFocus";

CREATE TABLE IF NOT EXISTS "program_day_attributes" (
    "id" TEXT NOT NULL,
    "dayId" TEXT NOT NULL,
    "attributeValueId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "program_day_attributes_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "program_day_attributes_dayId_attributeValueId_key" ON "program_day_attributes"("dayId", "attributeValueId");
CREATE INDEX IF NOT EXISTS "program_day_attributes_dayId_idx" ON "program_day_attributes"("dayId");
CREATE INDEX IF NOT EXISTS "program_day_attributes_attributeValueId_idx" ON "program_day_attributes"("attributeValueId");

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'program_day_attributes_dayId_fkey'
  ) THEN
    ALTER TABLE "program_day_attributes" ADD CONSTRAINT "program_day_attributes_dayId_fkey" FOREIGN KEY ("dayId") REFERENCES "program_days"("id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'program_day_attributes_attributeValueId_fkey'
  ) THEN
    ALTER TABLE "program_day_attributes" ADD CONSTRAINT "program_day_attributes_attributeValueId_fkey" FOREIGN KEY ("attributeValueId") REFERENCES "attribute_values"("id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DROP TYPE IF EXISTS "WeekType";
