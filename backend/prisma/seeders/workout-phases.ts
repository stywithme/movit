import type { PrismaClient } from '@prisma/client';

const WORKOUT_PHASE_CATALOG = [
  {
    slug: 'warmup',
    name: { en: 'Warm-up', ar: 'إحماء' },
    description: { en: 'Prepare the body for training.', ar: 'تهيئة الجسم للتمرين.' },
    role: 'WARMUP' as const,
    canSkip: true,
    canContinue: true,
    maxContinueTimeMs: 120_000,
    color: '#f59e0b',
    icon: 'flame',
    sortOrder: 0,
  },
  {
    slug: 'main',
    name: { en: 'Main Workout', ar: 'التمرين الأساسي' },
    description: { en: 'Primary training work.', ar: 'الجزء الأساسي من التمرين.' },
    role: 'MAIN' as const,
    canSkip: false,
    canContinue: true,
    maxContinueTimeMs: null,
    color: '#3b82f6',
    icon: 'dumbbell',
    sortOrder: 10,
  },
  {
    slug: 'cooldown',
    name: { en: 'Cool-down', ar: 'تهدئة' },
    description: { en: 'Restore breathing and mobility after training.', ar: 'استعادة التنفس والمرونة بعد التمرين.' },
    role: 'COOLDOWN' as const,
    canSkip: true,
    canContinue: true,
    maxContinueTimeMs: 90_000,
    color: '#10b981',
    icon: 'wind',
    sortOrder: 20,
  },
];

export async function seedWorkoutPhases(prisma: PrismaClient) {
  console.log('🏋️ Seeding workout phase catalog...');

  for (const phase of WORKOUT_PHASE_CATALOG) {
    await prisma.workoutPhase.upsert({
      where: { slug: phase.slug },
      update: {
        name: phase.name,
        description: phase.description,
        role: phase.role,
        canSkip: phase.canSkip,
        canContinue: phase.canContinue,
        maxContinueTimeMs: phase.maxContinueTimeMs,
        color: phase.color,
        icon: phase.icon,
        sortOrder: phase.sortOrder,
        isActive: true,
        deletedAt: null,
      },
      create: {
        slug: phase.slug,
        name: phase.name,
        description: phase.description,
        role: phase.role,
        canSkip: phase.canSkip,
        canContinue: phase.canContinue,
        maxContinueTimeMs: phase.maxContinueTimeMs,
        color: phase.color,
        icon: phase.icon,
        sortOrder: phase.sortOrder,
        isActive: true,
      },
    });
  }

  console.log(`✅ Workout phases seeded (${WORKOUT_PHASE_CATALOG.length}).`);
}

export async function getWorkoutPhaseIdBySlug(
  prisma: PrismaClient,
  slug: string,
): Promise<string> {
  const phase = await prisma.workoutPhase.findFirst({
    where: { slug, deletedAt: null },
    select: { id: true },
  });
  if (!phase) {
    throw new Error(`Workout phase not found: ${slug}. Run seed:base first.`);
  }
  return phase.id;
}
