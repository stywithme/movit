import type { PrismaClient } from '@prisma/client';
import { clearSeedData } from './clear';
import { seedAttributes } from './attributes';
import { createMessageTemplateHelper, seedBaseMessageTemplates } from './messages';
import { seedPosePositions } from './pose-positions';
import { seedLevels } from './levels';
import { seedWorkoutPhases } from './workout-phases';
import { seedSystemConfig } from './system';
import { seedSystemMessages } from './system-messages';
import { seedPermissions } from './permissions';
import { seedAdmins } from './admins';
import { seedExerciseJsonFromDirectories } from './exercise-json-batch';
import { seedWorkoutsFromJsonDir } from './workouts-from-json';
import {
  seedProgressionRules,
  assignArchetypesAndGenerateProfiles,
  backfillProgressionState,
} from './progression-rules';
import { seedPrograms } from './programs';
import { seedAssessmentTemplates } from './assessment-templates';
import { runSeedValidatorsAndReport } from './seeder-validators';
import { seedUsers } from './users';
import { seedUserPrograms } from './user-programs';

export type SeedMode = 'base' | 'full';

export type SeedRunOptions = {
  mode: SeedMode;
  reset: boolean;
  resetScope: 'content' | 'all';
  preserveMessageTemplates: boolean;
  includeDemo: boolean;
};

const CANONICAL_EXERCISE_DIR = 'prisma/Exercise-json/exercises-from-db';
const MISSING_EXERCISE_DIR = 'prisma/Exercise-json/missing-exercises/exercises-from-db';

export function parseSeedRunOptions(argv: string[] = process.argv.slice(2)): SeedRunOptions {
  const modeArg = argv.find((arg) => arg.startsWith('--mode='))?.split('=')[1];
  const mode: SeedMode = modeArg === 'base' ? 'base' : modeArg === 'full' ? 'full' : 'base';

  const reset = argv.includes('--reset');
  const resetScope = argv.includes('--reset=all') ? 'all' : 'content';
  const wipeMessageTemplates =
    argv.includes('--wipe-message-templates') ||
    process.env.SEED_WIPE_MESSAGE_TEMPLATES === 'true';
  const includeDemo =
    argv.includes('--demo') ||
    process.env.SEED_DEMO === 'true';

  return {
    mode,
    reset,
    resetScope,
    preserveMessageTemplates: !wipeMessageTemplates,
    includeDemo,
  };
}

export async function runSeedBase(prisma: PrismaClient) {
  console.log('🌱 Seeding base reference data...');

  await seedAttributes(prisma);

  const { ensureMessageTemplate } = createMessageTemplateHelper(prisma);
  await seedBaseMessageTemplates(ensureMessageTemplate);

  await seedPosePositions(prisma);
  await seedLevels(prisma);
  await seedWorkoutPhases(prisma);
  await seedSystemConfig(prisma);
  await seedSystemMessages(prisma);
  await seedPermissions(prisma);

  if (process.env.ADMIN_SEED_EMAIL && process.env.ADMIN_SEED_PASSWORD) {
    await seedAdmins(prisma);
  } else {
    console.warn(
      '⚠️ Skipping admin bootstrap — set ADMIN_SEED_EMAIL and ADMIN_SEED_PASSWORD to create the initial super admin.',
    );
  }

  console.log('✅ Base seed completed.');
}

export async function runSeedContent(prisma: PrismaClient) {
  console.log('🌱 Seeding full content catalog...');

  const { ensureMessageTemplate } = createMessageTemplateHelper(prisma);
  const seededSlugs = await seedExerciseJsonFromDirectories(
    prisma,
    ensureMessageTemplate,
    [CANONICAL_EXERCISE_DIR, MISSING_EXERCISE_DIR],
  );

  if (seededSlugs.size === 0) {
    throw new Error('No exercises were seeded. Check Exercise-json directories.');
  }

  await seedWorkoutsFromJsonDir(prisma);
  await seedProgressionRules(prisma);
  await assignArchetypesAndGenerateProfiles(prisma);

  const exerciseCount = await prisma.exercise.count();
  if (exerciseCount >= 4) {
    await seedPrograms(prisma);
    await runSeedValidatorsAndReport(prisma);
  } else {
    console.warn('⚠️ Skipping programs (not enough exercises).');
  }

  await seedAssessmentTemplates(prisma);
  await backfillProgressionState(prisma);

  console.log('✅ Full content seed completed.');
}

export async function runSeedDemo(prisma: PrismaClient) {
  console.log('🌱 Seeding demo fixtures...');
  await seedUsers(prisma);

  const exerciseCount = await prisma.exercise.count();
  if (exerciseCount >= 4) {
    await seedUserPrograms(prisma);
    await backfillProgressionState(prisma);
  } else {
    console.warn('⚠️ Skipping user programs (not enough exercises).');
  }

  console.log('✅ Demo fixtures seeded.');
}

export async function runSeed(prisma: PrismaClient, options: SeedRunOptions) {
  console.log(`🌱 Seed mode: ${options.mode}`);

  if (options.reset) {
    await clearSeedData(prisma, {
      scope: options.resetScope,
      preserveMessageTemplates: options.preserveMessageTemplates,
    });
  }

  await runSeedBase(prisma);

  if (options.mode === 'full') {
    await runSeedContent(prisma);
  }

  if (options.includeDemo) {
    await runSeedDemo(prisma);
  }

  console.log('🎉 Seeding completed.');
}
