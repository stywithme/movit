/** npx tsx --env-file=.env prisma/run-migrate-program-attributes.ts */
import 'dotenv/config';
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { migrateProgramAttributesFromLegacy } from './seeders/migrate-program-attributes';

if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is not set');
const prisma = new PrismaClient({ adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }) });

migrateProgramAttributesFromLegacy(prisma)
  .then((r) => console.log('migrateProgramAttributesFromLegacy:', r))
  .catch(console.error)
  .finally(() => prisma.$disconnect());
