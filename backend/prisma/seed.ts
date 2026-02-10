import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { clearDatabase } from './seeders/clear';
import { seedAttributes } from './seeders/attributes';
import { createMessageTemplateHelper, seedBaseMessageTemplates } from './seeders/messages';
import { seedCameraPositions } from './seeders/camera-positions';
import { seedExercisesAndWorkouts } from './seeders/exercises-workouts';
import { seedPrograms } from './seeders/programs';
import { seedUsers } from './seeders/users';
import { seedUserPrograms } from './seeders/user-programs';
import { seedAdmins } from './seeders/admins';

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

  await seedCameraPositions(prisma);
  await seedExercisesAndWorkouts(prisma, ensureMessageTemplate);
  await seedUsers(prisma);
  await seedPrograms(prisma);
  await seedUserPrograms(prisma);
  await seedAdmins(prisma);

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
