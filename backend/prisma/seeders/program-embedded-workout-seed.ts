import { PlannedWorkoutItemType, type Prisma, type PrismaClient, type WorkoutBlockRole } from '@prisma/client';

type SeedExerciseItem = {
  type: typeof PlannedWorkoutItemType.exercise;
  exerciseId: string;
  sets?: number;
  targetReps?: number;
  targetDuration?: number;
  restBetweenSetsMs?: number;
  weightKg?: number;
  weightPerSet?: number[];
  sortOrder: number;
};

type SeedRestItem = {
  type: typeof PlannedWorkoutItemType.rest;
  restDurationMs: number;
  sortOrder: number;
};

export type SeedPlannedWorkoutItem = SeedExerciseItem | SeedRestItem;

function roleToPhaseSlug(role: WorkoutBlockRole): string {
  switch (role) {
    case 'WARMUP':
    case 'ACTIVATION':
      return 'warmup';
    case 'COOLDOWN':
      return 'cooldown';
    default:
      return 'main';
  }
}

export async function createEmbeddedPlannedWorkout(
  prisma: PrismaClient,
  params: {
    dayId: string;
    programId: string;
    plannedWorkoutId: string;
    name: Prisma.InputJsonValue;
    sortOrder: number;
    estimatedDurationMin?: number;
    blockRole?: WorkoutBlockRole;
    items: SeedPlannedWorkoutItem[];
  },
) {
  const template = await prisma.workoutTemplate.create({
    data: {
      name: params.name,
      slug: `emb_${params.plannedWorkoutId.replace(/-/g, '').slice(0, 16)}`,
      status: 'draft',
      origin: 'PROGRAM_EMBEDDED',
      programId: params.programId,
      estimatedDurationMin: params.estimatedDurationMin,
      isFeatured: false,
    },
  });

  const phase = await prisma.workoutPhase.findFirst({
    where: { slug: roleToPhaseSlug(params.blockRole ?? 'MAIN'), deletedAt: null },
    select: { id: true },
  });
  if (!phase) throw new Error('Missing default workout phase for program seed');

  const templatePhase = await prisma.workoutTemplatePhase.create({
    data: {
      workoutTemplateId: template.id,
      phaseId: phase.id,
      sortOrder: 0,
    },
  });

  let exerciseSort = 0;
  let lastExerciseId: string | null = null;

  for (const item of [...params.items].sort((a, b) => a.sortOrder - b.sortOrder)) {
    if (item.type === PlannedWorkoutItemType.rest) {
      if (lastExerciseId) {
        await prisma.workoutTemplateExercise.update({
          where: { id: lastExerciseId },
          data: { restAfterExerciseMs: item.restDurationMs },
        });
      }
      continue;
    }

    const created = await prisma.workoutTemplateExercise.create({
      data: {
        workoutTemplateId: template.id,
        workoutTemplatePhaseId: templatePhase.id,
        exerciseId: item.exerciseId,
        variantIndex: 0,
        targetReps: item.targetReps,
        targetDuration: item.targetDuration,
        sets: item.sets ?? 3,
        restBetweenSetsMs: item.restBetweenSetsMs ?? 30000,
        weightKg: item.weightKg,
        weightPerSet: item.weightPerSet,
        sortOrder: exerciseSort++,
      },
    });
    lastExerciseId = created.id;
  }

  await prisma.plannedWorkout.create({
    data: {
      id: params.plannedWorkoutId,
      dayId: params.dayId,
      workoutTemplateId: template.id,
      name: params.name,
      sortOrder: params.sortOrder,
      estimatedDurationMin: params.estimatedDurationMin,
    },
  });
}
