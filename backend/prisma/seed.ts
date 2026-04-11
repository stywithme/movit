import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { clearDatabase } from './seeders/clear';
import { seedAttributes } from './seeders/attributes';
import { createMessageTemplateHelper, seedBaseMessageTemplates } from './seeders/messages';
import { seedPosePositions } from './seeders/pose-positions';
import { seedExercisesAndWorkouts } from './seeders/exercises-workouts';
import { seedPrograms } from './seeders/programs';
import { seedUsers } from './seeders/users';
import { seedUserPrograms } from './seeders/user-programs';
import { seedAdmins } from './seeders/admins';
import { seedLevels } from './seeders/levels';
import { seedProgressionRules, assignArchetypesAndGenerateProfiles, backfillProgressionState } from './seeders/progression-rules';
import { seedAssessmentTemplates } from './seeders/assessment-templates';
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

async function main() {
  console.log('🌱 Seeding database...');

  await clearDatabase(prisma);

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
  } else {
    console.warn('⚠️ Skipping programs & user programs (not enough exercises).');
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
