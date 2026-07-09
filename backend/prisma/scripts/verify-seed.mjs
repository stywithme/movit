import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';

const prisma = new PrismaClient({
  adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }),
});

async function main() {
  const checks = [
    ['attributes', () => prisma.attribute.count()],
    ['levels', () => prisma.level.count()],
    ['pose_positions', () => prisma.posePosition.count()],
    ['workout_phases', () => prisma.workoutPhase.count()],
    ['permissions', () => prisma.permission.count()],
    ['admins', () => prisma.admin.count()],
    ['exercises', () => prisma.exercise.count()],
    ['workout_templates', () => prisma.workoutTemplate.count()],
    ['programs', () => prisma.program.count()],
    ['assessment_templates', () => prisma.assessmentTemplate.count()],
    ['progression_profiles', () => prisma.exerciseProgressionProfile.count()],
    ['system_keys', () => prisma.system.count()],
  ] ;

  console.log('=== Local DB verification ===');
  for (const [name, fn] of checks) {
    const count = await fn();
    console.log(`${name}: ${count}`);
  }

  const programsWithLevels = await prisma.program.count({
    where: { levelMinId: { not: null }, levelMaxId: { not: null } },
  });
  console.log(`programs_with_level_range: ${programsWithLevels}`);

  const admin = await prisma.admin.findFirst({ select: { email: true, isSuperAdmin: true } });
  console.log(`admin: ${admin?.email} super=${admin?.isSuperAdmin}`);

  const currency = await prisma.system.findUnique({ where: { key: 'currency' } });
  console.log(`currency: ${currency?.value}`);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
