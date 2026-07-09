/**
 * Partial exercise JSON seed — DOES NOT clear the database.
 *
 * Usage:
 *   npm run seed:missing-exercises
 *   npm run seed:missing-exercises:dry
 *   npx tsx --env-file=.env prisma/run-exercise-json-seed.ts --dir=prisma/Exercise-json/exercises-from-db
 */
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { seedAttributes } from './seeders/attributes';
import { createMessageTemplateHelper, seedBaseMessageTemplates } from './seeders/messages';
import { seedPosePositions } from './seeders/pose-positions';
import { assignArchetypesAndGenerateProfiles } from './seeders/progression-rules';
import {
  loadExerciseJsonFilesFromDir,
  resolveExerciseJsonDir,
  seedExerciseJsonBatch,
  validateExerciseJsonFiles,
} from './seeders/exercise-json-batch';

type ParsedArgs = {
  dir: string;
  dryRun: boolean;
  skipPrerequisites: boolean;
  skipProgression: boolean;
  onlySlugs: Set<string> | null;
};

function parseArgs(): ParsedArgs {
  const args = process.argv.slice(2);
  const getValue = (name: string) => {
    const direct = args.find((arg) => arg.startsWith(`${name}=`));
    if (direct) return direct.slice(name.length + 1);
    const index = args.indexOf(name);
    return index >= 0 ? args[index + 1] : undefined;
  };

  const only = getValue('--only');
  return {
    dir: getValue('--dir') || 'prisma/Exercise-json/exercises-from-db',
    dryRun: args.includes('--dry-run'),
    skipPrerequisites: args.includes('--skip-prerequisites'),
    skipProgression: args.includes('--skip-progression'),
    onlySlugs: only ? new Set(only.split(',').map((slug) => slug.trim()).filter(Boolean)) : null,
  };
}

async function main() {
  const args = parseArgs();
  const dir = await resolveExerciseJsonDir(args.dir);
  const files = await loadExerciseJsonFilesFromDir(dir, args.onlySlugs);

  if (files.length === 0) {
    throw new Error(`No exercise JSON files found in ${dir}${args.onlySlugs ? ' for --only filter' : ''}`);
  }

  validateExerciseJsonFiles(files);

  console.log(`Exercise JSON dir: ${dir}`);
  console.log(`Exercise files: ${files.length}`);

  if (args.dryRun) {
    console.log('Dry run passed. No database writes were made.');
    return;
  }

  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL is not set in environment variables');
  }

  const adapter = new PrismaPg({ connectionString: process.env.DATABASE_URL });
  const prisma = new PrismaClient({ adapter });

  try {
    if (!args.skipPrerequisites) {
      await seedAttributes(prisma);
      const { ensureMessageTemplate } = createMessageTemplateHelper(prisma);
      await seedBaseMessageTemplates(ensureMessageTemplate);
      await seedPosePositions(prisma);
    }

    const { ensureMessageTemplate } = createMessageTemplateHelper(prisma);
    console.log('Seeding exercise JSON files (partial, no DB clear)...');
    await seedExerciseJsonBatch(prisma, files, ensureMessageTemplate);

    if (!args.skipProgression) {
      await assignArchetypesAndGenerateProfiles(prisma);
    }

    console.log('Exercise JSON partial seed completed.');
  } finally {
    await prisma.$disconnect();
  }
}

main().catch((e) => {
  console.error('Exercise JSON partial seed failed:', e);
  process.exit(1);
});
