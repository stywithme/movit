import type { PrismaClient } from '@prisma/client';

export async function seedPrograms(prisma: PrismaClient) {
  const exercises = await prisma.exercise.findMany({
    select: {
      id: true,
      slug: true,
      name: true,
      countingMethod: {
        select: {
          code: true,
        },
      },
    },
    orderBy: { createdAt: 'asc' },
  });

  if (exercises.length < 4) {
    console.warn('⚠️ Not enough exercises to build programs. Skipping program seed.');
    return;
  }

  const pick = (...indexes: number[]) => indexes.map((i) => exercises[i % exercises.length]);

  const [ex1, ex2, ex3, ex4, ex5, ex6] = pick(0, 1, 2, 3, 4, 5);

  const buildExerciseTarget = (
    exercise: (typeof exercises)[number],
    preferred: { reps?: number; duration?: number }
  ) => {
    const isHold = exercise.countingMethod?.code === 'hold';
    if (isHold) {
      return {
        targetReps: undefined,
        targetDuration: preferred.duration ?? 30,
      };
    }
    return {
      targetReps: preferred.reps ?? 10,
      targetDuration: undefined,
    };
  };

  const program = await prisma.program.upsert({
    where: { slug: 'starter-4-weeks' },
    update: {
      name: { ar: 'برنامج البداية 4 أسابيع', en: 'Starter 4-Week Program' },
      description: { ar: 'بناء أساس آمن للحركة خلال 4 أسابيع', en: 'Build a safe movement foundation over 4 weeks' },
      durationWeeks: 4,
      difficulty: 'beginner',
      isDefault: true,
      isPublished: true,
      tags: ['beginner', 'foundation'],
    },
    create: {
      slug: 'starter-4-weeks',
      name: { ar: 'برنامج البداية 4 أسابيع', en: 'Starter 4-Week Program' },
      description: { ar: 'بناء أساس آمن للحركة خلال 4 أسابيع', en: 'Build a safe movement foundation over 4 weeks' },
      durationWeeks: 4,
      difficulty: 'beginner',
      isDefault: true,
      isPublished: true,
      tags: ['beginner', 'foundation'],
    },
  });

  await prisma.programWeek.deleteMany({
    where: { programId: program.id },
  });

  const createWeek = async (weekNumber: number, days: Array<{ dayNumber: number; isRestDay?: boolean; sessions?: any[] }>) => {
    await prisma.programWeek.create({
      data: {
        programId: program.id,
        weekNumber,
        sortOrder: weekNumber,
        name: { ar: `الأسبوع ${weekNumber}`, en: `Week ${weekNumber}` },
        days: {
          create: days.map((day) => ({
            dayNumber: day.dayNumber,
            isRestDay: day.isRestDay ?? false,
            name: day.isRestDay ? { ar: 'راحة', en: 'Rest' } : undefined,
            sessions: day.sessions
              ? {
                  create: day.sessions,
                }
              : undefined,
          })),
        },
      },
    });
  };

  await createWeek(1, [
    {
      dayNumber: 1,
      sessions: [
        {
          name: { ar: 'صباحًا', en: 'Morning' },
          sortOrder: 1,
          items: {
            create: [
              {
                type: 'exercise',
                exerciseId: ex1.id,
                sets: 3,
                ...buildExerciseTarget(ex1, { reps: 10, duration: 30 }),
                restBetweenSetsMs: 30000,
                sortOrder: 1,
              },
              { type: 'rest', restDurationMs: 60000, sortOrder: 2 },
              {
                type: 'exercise',
                exerciseId: ex2.id,
                sets: 2,
                ...buildExerciseTarget(ex2, { reps: 10, duration: 30 }),
                restBetweenSetsMs: 20000,
                sortOrder: 3,
              },
            ],
          },
        },
      ],
    },
    {
      dayNumber: 2,
      sessions: [
        {
          name: { ar: 'قبل النوم', en: 'Evening' },
          sortOrder: 1,
          items: {
            create: [
              {
                type: 'exercise',
                exerciseId: ex3.id,
                sets: 3,
                ...buildExerciseTarget(ex3, { reps: 8, duration: 30 }),
                restBetweenSetsMs: 25000,
                sortOrder: 1,
              },
              { type: 'rest', restDurationMs: 45000, sortOrder: 2 },
              {
                type: 'exercise',
                exerciseId: ex4.id,
                sets: 3,
                ...buildExerciseTarget(ex4, { reps: 12, duration: 30 }),
                restBetweenSetsMs: 30000,
                sortOrder: 3,
              },
            ],
          },
        },
      ],
    },
    { dayNumber: 3, isRestDay: true },
    {
      dayNumber: 4,
      sessions: [
        {
          name: { ar: 'صباحًا', en: 'Morning' },
          sortOrder: 1,
          items: {
            create: [
              {
                type: 'exercise',
                exerciseId: ex5.id,
                sets: 2,
                ...buildExerciseTarget(ex5, { reps: 10, duration: 40 }),
                restBetweenSetsMs: 20000,
                weightKg: 5,
                weightPerSet: [5, 7.5],
                sortOrder: 1,
              },
              { type: 'rest', restDurationMs: 50000, sortOrder: 2 },
              {
                type: 'exercise',
                exerciseId: ex6.id,
                sets: 3,
                ...buildExerciseTarget(ex6, { reps: 10, duration: 30 }),
                restBetweenSetsMs: 25000,
                sortOrder: 3,
              },
            ],
          },
        },
      ],
    },
  ]);

  await createWeek(2, [
    {
      dayNumber: 1,
      sessions: [
        {
          name: { ar: 'صباحًا', en: 'Morning' },
          sortOrder: 1,
          items: {
            create: [
              {
                type: 'exercise',
                exerciseId: ex2.id,
                sets: 3,
                ...buildExerciseTarget(ex2, { reps: 12, duration: 40 }),
                restBetweenSetsMs: 30000,
                sortOrder: 1,
              },
              { type: 'rest', restDurationMs: 60000, sortOrder: 2 },
              {
                type: 'exercise',
                exerciseId: ex4.id,
                sets: 2,
                ...buildExerciseTarget(ex4, { reps: 10, duration: 40 }),
                restBetweenSetsMs: 20000,
                sortOrder: 3,
              },
            ],
          },
        },
      ],
    },
    { dayNumber: 2, isRestDay: true },
    {
      dayNumber: 3,
      sessions: [
        {
          name: { ar: 'خلال العمل', en: 'Midday' },
          sortOrder: 1,
          items: {
            create: [
              {
                type: 'exercise',
                exerciseId: ex1.id,
                sets: 3,
                ...buildExerciseTarget(ex1, { reps: 10, duration: 30 }),
                restBetweenSetsMs: 25000,
                sortOrder: 1,
              },
              { type: 'rest', restDurationMs: 45000, sortOrder: 2 },
              {
                type: 'exercise',
                exerciseId: ex3.id,
                sets: 2,
                ...buildExerciseTarget(ex3, { reps: 10, duration: 30 }),
                restBetweenSetsMs: 20000,
                sortOrder: 3,
              },
            ],
          },
        },
      ],
    },
  ]);

  console.log('✅ Programs seeded');
}
