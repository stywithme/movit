/**
 * Partial seed for unified progression refactor — DOES NOT clear the database.
 *
 * Runs only:
 * - seedProgressionRules (idempotent upserts)
 * - assignArchetypesAndGenerateProfiles (exercises missing archetype only)
 * - backfillProgressionState (missing rows only)
 *
 * Usage (production):
 *   cd /var/www/pose-backend && npx tsx --env-file=.env prisma/run-unified-refactor-seed.ts
 *
 * Or: npm run seed:unified-refactor
 */
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import {
  seedProgressionRules,
  assignArchetypesAndGenerateProfiles,
  backfillProgressionState,
} from './seeders/progression-rules';

if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL is not set in environment variables');
}

const adapter = new PrismaPg({
  connectionString: process.env.DATABASE_URL,
});

const prisma = new PrismaClient({ adapter });

async function main() {
  console.log('🌱 Unified refactor partial seed (no DB clear)...');

  await seedProgressionRules(prisma);
  await assignArchetypesAndGenerateProfiles(prisma);
  await backfillProgressionState(prisma);

  console.log('🎉 Unified refactor partial seed completed.');
}

main()
  .catch((e) => {
    console.error('❌ Partial seed failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
