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
      // Prescription metadata
      type: 'training',
      targetDomain: null,
      targetRegions: [],
      levelRangeMin: 1,
      levelRangeMax: 2,
      prescriptionPriority: 50,
      entryCriteria: {},
      exitCriteria: { bodyScore: { min: 40 } },
      contraindications: [],
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
      type: 'training',
      targetDomain: null,
      targetRegions: [],
      levelRangeMin: 1,
      levelRangeMax: 2,
      prescriptionPriority: 50,
      entryCriteria: {},
      exitCriteria: { bodyScore: { min: 40 } },
      contraindications: [],
    },
  });

  // Clean old weeks (cascade deletes days, sessions, items)
  await prisma.programWeek.deleteMany({
    where: { programId: program.id },
  });

  // Helper to build a session with exercises
  const buildSession = (
    name: { ar: string; en: string },
    items: any[],
    sortOrder = 1,
  ) => ({
    name,
    sortOrder,
    items: { create: items },
  });

  // Helper to build an exercise item
  const exItem = (
    exercise: (typeof exercises)[number],
    opts: { sets?: number; reps?: number; duration?: number; restMs?: number; weight?: number; weightPerSet?: number[]; sortOrder: number },
  ) => ({
    type: 'exercise' as const,
    exerciseId: exercise.id,
    sets: opts.sets ?? 3,
    ...buildExerciseTarget(exercise, { reps: opts.reps, duration: opts.duration }),
    restBetweenSetsMs: opts.restMs ?? 30000,
    weightKg: opts.weight,
    weightPerSet: opts.weightPerSet,
    sortOrder: opts.sortOrder,
  });

  // Helper to build a rest item
  const restItem = (durationMs: number, sortOrder: number) => ({
    type: 'rest' as const,
    restDurationMs: durationMs,
    sortOrder,
  });

  // Day structure type
  type DayDef = {
    dayNumber: number;
    isRestDay?: boolean;
    sessions?: any[];
  };

  const createWeek = async (
    weekNumber: number,
    days: DayDef[],
  ) => {
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
              ? { create: day.sessions }
              : undefined,
          })),
        },
      },
    });
  };

  // ════════════════════════════════════════════════════════
  // WEEK 1 — Foundation (7 days: 4 training + 3 rest)
  // Day 1=Sat, 2=Sun, 3=Mon, 4=Tue, 5=Wed, 6=Thu, 7=Fri
  // ════════════════════════════════════════════════════════
  await createWeek(1, [
    {
      dayNumber: 1, // Saturday — Upper body basics
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex1, { sets: 3, reps: 10, duration: 30, restMs: 30000, sortOrder: 1 }),
          restItem(60000, 2),
          exItem(ex2, { sets: 2, reps: 10, duration: 30, restMs: 20000, sortOrder: 3 }),
        ]),
      ],
    },
    {
      dayNumber: 2, // Sunday — Lower body
      sessions: [
        buildSession({ ar: 'قبل النوم', en: 'Evening' }, [
          exItem(ex3, { sets: 3, reps: 8, duration: 30, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex4, { sets: 3, reps: 12, duration: 30, restMs: 30000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 3, isRestDay: true }, // Monday — Rest
    {
      dayNumber: 4, // Tuesday — Full body
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex5, { sets: 2, reps: 10, duration: 40, restMs: 20000, weight: 5, weightPerSet: [5, 7.5], sortOrder: 1 }),
          restItem(50000, 2),
          exItem(ex6, { sets: 3, reps: 10, duration: 30, restMs: 25000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 5, isRestDay: true }, // Wednesday — Rest
    {
      dayNumber: 6, // Thursday — Core & upper
      sessions: [
        buildSession({ ar: 'مساءً', en: 'Evening' }, [
          exItem(ex1, { sets: 2, reps: 12, duration: 35, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex3, { sets: 2, reps: 10, duration: 30, restMs: 20000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 7, isRestDay: true }, // Friday — Rest
  ]);

  // ════════════════════════════════════════════════════════
  // WEEK 2 — Build (7 days: 4 training + 3 rest)
  // ════════════════════════════════════════════════════════
  await createWeek(2, [
    {
      dayNumber: 1, // Saturday — Push
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex2, { sets: 3, reps: 12, duration: 40, restMs: 30000, sortOrder: 1 }),
          restItem(60000, 2),
          exItem(ex4, { sets: 2, reps: 10, duration: 40, restMs: 20000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 2, isRestDay: true }, // Sunday — Rest
    {
      dayNumber: 3, // Monday — Core
      sessions: [
        buildSession({ ar: 'خلال العمل', en: 'Midday' }, [
          exItem(ex1, { sets: 3, reps: 10, duration: 30, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex3, { sets: 2, reps: 10, duration: 30, restMs: 20000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 4, isRestDay: true }, // Tuesday — Rest
    {
      dayNumber: 5, // Wednesday — Full body
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex5, { sets: 3, reps: 10, duration: 35, restMs: 25000, weight: 5, sortOrder: 1 }),
          restItem(50000, 2),
          exItem(ex6, { sets: 3, reps: 12, duration: 35, restMs: 25000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 6, isRestDay: true }, // Thursday — Rest
    {
      dayNumber: 7, // Friday — Upper body
      sessions: [
        buildSession({ ar: 'مساءً', en: 'Evening' }, [
          exItem(ex2, { sets: 3, reps: 10, duration: 30, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex4, { sets: 2, reps: 12, duration: 35, restMs: 20000, sortOrder: 3 }),
        ]),
      ],
    },
  ]);

  // ════════════════════════════════════════════════════════
  // WEEK 3 — Intensity (7 days: 4 training + 3 rest)
  // ════════════════════════════════════════════════════════
  await createWeek(3, [
    {
      dayNumber: 1, // Saturday — Full body compound
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex1, { sets: 3, reps: 12, duration: 35, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex2, { sets: 3, reps: 12, duration: 35, restMs: 25000, sortOrder: 3 }),
          restItem(45000, 4),
          exItem(ex3, { sets: 2, reps: 10, duration: 30, restMs: 20000, sortOrder: 5 }),
        ]),
      ],
    },
    { dayNumber: 2, isRestDay: true }, // Sunday — Rest
    {
      dayNumber: 3, // Monday — Lower body focus
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex4, { sets: 3, reps: 12, duration: 35, restMs: 30000, sortOrder: 1 }),
          restItem(50000, 2),
          exItem(ex5, { sets: 3, reps: 12, duration: 40, restMs: 25000, weight: 7.5, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 4, isRestDay: true }, // Tuesday — Rest
    {
      dayNumber: 5, // Wednesday — Core & stability
      sessions: [
        buildSession({ ar: 'خلال العمل', en: 'Midday' }, [
          exItem(ex6, { sets: 3, reps: 12, duration: 35, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex1, { sets: 3, reps: 12, duration: 40, restMs: 25000, sortOrder: 3 }),
        ]),
      ],
    },
    {
      dayNumber: 6, // Thursday — Upper body
      sessions: [
        buildSession({ ar: 'مساءً', en: 'Evening' }, [
          exItem(ex3, { sets: 3, reps: 10, duration: 30, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex4, { sets: 3, reps: 10, duration: 30, restMs: 25000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 7, isRestDay: true }, // Friday — Rest
  ]);

  // ════════════════════════════════════════════════════════
  // WEEK 4 — Peak (7 days: 4 training + 3 rest)
  // ════════════════════════════════════════════════════════
  await createWeek(4, [
    {
      dayNumber: 1, // Saturday — Full body A
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex1, { sets: 3, reps: 12, duration: 40, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex2, { sets: 3, reps: 12, duration: 40, restMs: 25000, sortOrder: 3 }),
        ]),
      ],
    },
    {
      dayNumber: 2, // Sunday — Full body B
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex3, { sets: 3, reps: 12, duration: 35, restMs: 25000, sortOrder: 1 }),
          restItem(45000, 2),
          exItem(ex4, { sets: 3, reps: 12, duration: 35, restMs: 25000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 3, isRestDay: true }, // Monday — Rest
    {
      dayNumber: 4, // Tuesday — Weighted
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex5, { sets: 3, reps: 12, duration: 40, restMs: 25000, weight: 10, weightPerSet: [7.5, 10, 10], sortOrder: 1 }),
          restItem(50000, 2),
          exItem(ex6, { sets: 3, reps: 12, duration: 35, restMs: 25000, sortOrder: 3 }),
        ]),
      ],
    },
    { dayNumber: 5, isRestDay: true }, // Wednesday — Rest
    {
      dayNumber: 6, // Thursday — Final challenge
      sessions: [
        buildSession({ ar: 'صباحًا', en: 'Morning' }, [
          exItem(ex1, { sets: 3, reps: 15, duration: 45, restMs: 20000, sortOrder: 1 }),
          restItem(40000, 2),
          exItem(ex3, { sets: 3, reps: 12, duration: 35, restMs: 20000, sortOrder: 3 }),
          restItem(40000, 4),
          exItem(ex5, { sets: 3, reps: 12, duration: 40, restMs: 20000, weight: 10, sortOrder: 5 }),
        ]),
      ],
    },
    { dayNumber: 7, isRestDay: true }, // Friday — Rest
  ]);

  console.log('  ✅ Starter program seeded (4 weeks × 7 days)');

  // ════════════════════════════════════════════════════════════════
  // PROGRAM 2 — Mobility Focus (3 weeks)
  // Targets users with low mobility scores
  // ════════════════════════════════════════════════════════════════
  const mobilityProgram = await prisma.program.upsert({
    where: { slug: 'mobility-focus-3w' },
    update: {
      name: { ar: 'برنامج المرونة والحركة', en: 'Mobility Focus Program' },
      description: { ar: 'تحسين المرونة ونطاق الحركة خلال 3 أسابيع', en: 'Improve flexibility and range of motion over 3 weeks' },
      durationWeeks: 3,
      difficulty: 'beginner',
      isPublished: true,
      tags: ['mobility', 'flexibility', 'correction'],
      type: 'mobility',
      targetDomain: 'mobility',
      targetRegions: ['shoulder', 'hip', 'spine'],
      levelRangeMin: 1,
      levelRangeMax: 3,
      prescriptionPriority: 30,
      entryCriteria: { mobilityScore: { max: 50 } },
      exitCriteria: { mobilityScore: { min: 65 } },
      contraindications: [],
    },
    create: {
      slug: 'mobility-focus-3w',
      name: { ar: 'برنامج المرونة والحركة', en: 'Mobility Focus Program' },
      description: { ar: 'تحسين المرونة ونطاق الحركة خلال 3 أسابيع', en: 'Improve flexibility and range of motion over 3 weeks' },
      durationWeeks: 3,
      difficulty: 'beginner',
      isPublished: true,
      tags: ['mobility', 'flexibility', 'correction'],
      type: 'mobility',
      targetDomain: 'mobility',
      targetRegions: ['shoulder', 'hip', 'spine'],
      levelRangeMin: 1,
      levelRangeMax: 3,
      prescriptionPriority: 30,
      entryCriteria: { mobilityScore: { max: 50 } },
      exitCriteria: { mobilityScore: { min: 65 } },
      contraindications: [],
    },
  });

  await prisma.programWeek.deleteMany({ where: { programId: mobilityProgram.id } });

  for (let w = 1; w <= 3; w++) {
    await createWeekForProgram(prisma, mobilityProgram.id, w, [
      {
        dayNumber: 1,
        sessions: [
          buildSession({ ar: 'جلسة مرونة', en: 'Mobility Session' }, [
            exItem(ex1, { sets: 2, reps: 12, duration: 40, restMs: 20000, sortOrder: 1 }),
            restItem(45000, 2),
            exItem(ex3, { sets: 2, reps: 10, duration: 35, restMs: 20000, sortOrder: 3 }),
          ]),
        ],
      },
      { dayNumber: 2, isRestDay: true },
      {
        dayNumber: 3,
        sessions: [
          buildSession({ ar: 'تمديد وتحرك', en: 'Stretch & Move' }, [
            exItem(ex2, { sets: 2, reps: 10, duration: 35, restMs: 20000, sortOrder: 1 }),
            restItem(40000, 2),
            exItem(ex4, { sets: 2, reps: 10, duration: 35, restMs: 20000, sortOrder: 3 }),
          ]),
        ],
      },
      { dayNumber: 4, isRestDay: true },
      {
        dayNumber: 5,
        sessions: [
          buildSession({ ar: 'مرونة شاملة', en: 'Full Mobility' }, [
            exItem(ex5, { sets: 2, reps: 10, duration: 40, restMs: 20000, sortOrder: 1 }),
            restItem(40000, 2),
            exItem(ex6, { sets: 2, reps: 10, duration: 35, restMs: 20000, sortOrder: 3 }),
          ]),
        ],
      },
      { dayNumber: 6, isRestDay: true },
      { dayNumber: 7, isRestDay: true },
    ]);
  }

  console.log('  ✅ Mobility Focus program seeded (3 weeks)');

  // ════════════════════════════════════════════════════════════════
  // PROGRAM 3 — Intermediate Strength (4 weeks)
  // For users who completed the starter program
  // ════════════════════════════════════════════════════════════════
  const strengthProgram = await prisma.program.upsert({
    where: { slug: 'intermediate-strength-4w' },
    update: {
      name: { ar: 'برنامج القوة المتوسط', en: 'Intermediate Strength Program' },
      description: { ar: 'بناء القوة والتحكم لمستوى متقدم', en: 'Build strength and control for intermediate level' },
      durationWeeks: 4,
      difficulty: 'intermediate',
      isPublished: true,
      tags: ['strength', 'intermediate', 'progression'],
      type: 'training',
      targetDomain: 'strength',
      targetRegions: [],
      levelRangeMin: 2,
      levelRangeMax: 4,
      prescriptionPriority: 60,
      entryCriteria: { bodyScore: { min: 40 } },
      exitCriteria: { bodyScore: { min: 65 } },
      contraindications: [],
      prerequisiteProgramId: program.id,
    },
    create: {
      slug: 'intermediate-strength-4w',
      name: { ar: 'برنامج القوة المتوسط', en: 'Intermediate Strength Program' },
      description: { ar: 'بناء القوة والتحكم لمستوى متقدم', en: 'Build strength and control for intermediate level' },
      durationWeeks: 4,
      difficulty: 'intermediate',
      isPublished: true,
      tags: ['strength', 'intermediate', 'progression'],
      type: 'training',
      targetDomain: 'strength',
      targetRegions: [],
      levelRangeMin: 2,
      levelRangeMax: 4,
      prescriptionPriority: 60,
      entryCriteria: { bodyScore: { min: 40 } },
      exitCriteria: { bodyScore: { min: 65 } },
      contraindications: [],
      prerequisiteProgramId: program.id,
    },
  });

  // Link starter → intermediate
  await prisma.program.update({
    where: { id: program.id },
    data: { nextProgramId: strengthProgram.id },
  });

  await prisma.programWeek.deleteMany({ where: { programId: strengthProgram.id } });

  for (let w = 1; w <= 4; w++) {
    const baseSets = w <= 2 ? 3 : 4;
    const baseReps = w <= 2 ? 10 : 12;
    const baseWeight = 5 + (w - 1) * 2.5;

    await createWeekForProgram(prisma, strengthProgram.id, w, [
      {
        dayNumber: 1,
        sessions: [
          buildSession({ ar: 'قوة علوية', en: 'Upper Strength' }, [
            exItem(ex1, { sets: baseSets, reps: baseReps, duration: 40, restMs: 45000, weight: baseWeight, sortOrder: 1 }),
            restItem(60000, 2),
            exItem(ex2, { sets: baseSets, reps: baseReps, duration: 40, restMs: 45000, weight: baseWeight, sortOrder: 3 }),
          ]),
        ],
      },
      {
        dayNumber: 2,
        sessions: [
          buildSession({ ar: 'قوة سفلية', en: 'Lower Strength' }, [
            exItem(ex3, { sets: baseSets, reps: baseReps, duration: 40, restMs: 45000, weight: baseWeight, sortOrder: 1 }),
            restItem(60000, 2),
            exItem(ex4, { sets: baseSets, reps: baseReps, duration: 40, restMs: 45000, weight: baseWeight, sortOrder: 3 }),
          ]),
        ],
      },
      { dayNumber: 3, isRestDay: true },
      {
        dayNumber: 4,
        sessions: [
          buildSession({ ar: 'قوة شاملة', en: 'Full Body Power' }, [
            exItem(ex5, { sets: baseSets, reps: baseReps, duration: 45, restMs: 50000, weight: baseWeight + 2.5, sortOrder: 1 }),
            restItem(60000, 2),
            exItem(ex6, { sets: baseSets, reps: baseReps, duration: 40, restMs: 45000, weight: baseWeight, sortOrder: 3 }),
          ]),
        ],
      },
      { dayNumber: 5, isRestDay: true },
      {
        dayNumber: 6,
        sessions: [
          buildSession({ ar: 'تحدي القوة', en: 'Strength Challenge' }, [
            exItem(ex1, { sets: baseSets + 1, reps: baseReps, duration: 45, restMs: 40000, weight: baseWeight + 2.5, sortOrder: 1 }),
            restItem(50000, 2),
            exItem(ex3, { sets: baseSets, reps: baseReps, duration: 40, restMs: 40000, weight: baseWeight, sortOrder: 3 }),
          ]),
        ],
      },
      { dayNumber: 7, isRestDay: true },
    ]);
  }

  console.log('  ✅ Intermediate Strength program seeded (4 weeks)');
  console.log('✅ All programs seeded (3 programs)');
}

// Helper to create a week for any program
async function createWeekForProgram(
  prisma: any,
  programId: string,
  weekNumber: number,
  days: { dayNumber: number; isRestDay?: boolean; sessions?: any[] }[],
) {
  await prisma.programWeek.create({
    data: {
      programId,
      weekNumber,
      sortOrder: weekNumber,
      name: { ar: `الأسبوع ${weekNumber}`, en: `Week ${weekNumber}` },
      days: {
        create: days.map((day) => ({
          dayNumber: day.dayNumber,
          isRestDay: day.isRestDay ?? false,
          name: day.isRestDay ? { ar: 'راحة', en: 'Rest' } : undefined,
          sessions: day.sessions
            ? { create: day.sessions }
            : undefined,
        })),
      },
    },
  });
}
