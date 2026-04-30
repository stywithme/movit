import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { clearDatabase } from './seeders/clear';
import { seedAttributes } from './seeders/attributes';
import { createMessageTemplateHelper, seedBaseMessageTemplates } from './seeders/messages';
import { seedPosePositions } from './seeders/pose-positions';
import { seedExercisesAndWorkouts } from './seeders/exercises-workouts';
import { migrateProgramAttributesFromLegacy } from './seeders/migrate-program-attributes';
import { seedPrograms } from './seeders/programs';
import { seedUsers } from './seeders/users';
import { seedUserPrograms } from './seeders/user-programs';
import { seedAdmins } from './seeders/admins';
import { seedLevels } from './seeders/levels';
import { seedProgressionRules, assignArchetypesAndGenerateProfiles, backfillProgressionState } from './seeders/progression-rules';
import { seedAssessmentTemplates } from './seeders/assessment-templates';
import { runSeedValidatorsAndReport } from './seeders/seeder-validators';
import { seedPermissions } from './seeders/permissions';
import { seedSystemConfig } from './seeders/system';
import { seedSystemMessages } from './seeders/system-messages';

if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL is not set in environment variables');
}

const adapter = new PrismaPg({
  connectionString: process.env.DATABASE_URL,
});

const prisma = new PrismaClient({ adapter });

function seedClearPreservesMessageTemplates(): boolean {
  const argv = process.argv.slice(2);
  const fullClear =
    argv.includes('--full') ||
    process.env.SEED_FULL_CLEAR === 'true' ||
    process.env.SEED_CLEAR_MESSAGE_TEMPLATES === 'true';
  return !fullClear;
}

async function main() {
  console.log('🌱 Seeding database...');

  const preserveMessageTemplates = seedClearPreservesMessageTemplates();
  console.log(
    preserveMessageTemplates
      ? 'ℹ️ Clear mode: default — preserves feedback_message_templates (merge/upsert messages; keeps audio). Use --full or SEED_FULL_CLEAR=true to wipe templates too.'
      : 'ℹ️ Clear mode: full — will delete feedback_message_templates before re-seeding.',
  );

  await clearDatabase(prisma, { preserveMessageTemplates });

  await seedAttributes(prisma);

  const { ensureMessageTemplate } = createMessageTemplateHelper(prisma);
  await seedBaseMessageTemplates(ensureMessageTemplate);

  await seedPosePositions(prisma);
  await seedExercisesAndWorkouts(prisma, ensureMessageTemplate);
  await seedUsers(prisma);

  const exerciseCount = await prisma.exercise.count();
  if (exerciseCount >= 4) {
    await seedPrograms(prisma);
    await seedUserPrograms(prisma);
    await runSeedValidatorsAndReport(prisma);
  } else {
    console.warn('⚠️ Skipping programs & user programs (not enough exercises).');
  }

  const mig = await migrateProgramAttributesFromLegacy(prisma);
  if (mig.programsUpdated > 0) {
    console.log(`✅ Migrated legacy program fields → ProgramAttribute for ${mig.programsUpdated} program(s)`);
  }

  await seedSystemConfig(prisma);
  await seedSystemMessages(prisma);
  await seedPermissions(prisma);
  await seedAdmins(prisma);
  await seedLevels(prisma);
  await seedProgressionRules(prisma);
  await assignArchetypesAndGenerateProfiles(prisma);
  await backfillProgressionState(prisma);
  await seedAssessmentTemplates(prisma);

  console.log('🎉 Seeding completed!');
}

main()
  .catch((e) => {
    console.error('❌ Seeding failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
