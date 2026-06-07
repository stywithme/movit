/**
 * Sync PROGRAM_EMBEDDED workout templates from program planned-workout input.
 */

import { PlannedWorkoutItemType, Prisma, type PrismaClient } from '@prisma/client';
import type {
  PlannedWorkoutItemInput,
  PlannedWorkoutInput,
} from '@/modules/programs/programs.types';
import type { WorkoutExerciseInput, WorkoutPhaseInput } from '@/modules/workout-templates/workout-templates.types';
import { workoutService } from '@/modules/workout-templates/workout-templates.service';

type Tx = Prisma.TransactionClient;

function parseLocalizedText(value: unknown): { ar: string; en: string } | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const record = value as Record<string, unknown>;
  if (typeof record.ar !== 'string' || typeof record.en !== 'string') return undefined;
  return { ar: record.ar, en: record.en };
}

function toInputJson(value: unknown): Prisma.InputJsonValue | undefined {
  if (value === null || value === undefined) return undefined;
  return value as Prisma.InputJsonValue;
}

function embeddedSlug(plannedWorkoutId: string): string {
  return `emb_${plannedWorkoutId.replace(/-/g, '').slice(0, 16)}`;
}

/** Convert legacy flat planned-workout items into a single MAIN-phase exercise list. */
export function plannedItemsToExercises(items: PlannedWorkoutItemInput[]): WorkoutExerciseInput[] {
  const exercises: WorkoutExerciseInput[] = [];
  let sortOrder = 0;

  for (const item of items) {
    if (item.type === PlannedWorkoutItemType.rest) {
      const prev = exercises[exercises.length - 1];
      if (prev && item.restDurationMs != null) {
        prev.restAfterExerciseMs = item.restDurationMs;
      }
      continue;
    }
    if (item.type !== PlannedWorkoutItemType.exercise || !item.exerciseId) continue;

    exercises.push({
      exerciseId: item.exerciseId,
      sets: item.sets,
      targetReps: item.targetReps,
      targetDuration: item.targetDuration,
      restBetweenSetsMs: item.restBetweenSetsMs,
      weightKg: item.weightKg,
      weightPerSet: item.weightPerSet,
      notes: parseLocalizedText(item.notes),
      sortOrder: item.sortOrder ?? sortOrder++,
    });
  }

  return exercises;
}

async function resolveMainPhaseId(tx: Tx): Promise<string> {
  const main = await tx.workoutPhase.findFirst({
    where: { slug: 'main', deletedAt: null },
    select: { id: true },
  });
  if (!main) throw new Error('Default workout phase "main" is missing');
  return main.id;
}

async function createEmbeddedShell(
  tx: Tx,
  params: {
    plannedWorkoutId: string;
    programId: string;
    name: Prisma.InputJsonValue;
    estimatedDurationMin?: number | null;
  },
): Promise<string> {
  const template = await tx.workoutTemplate.create({
    data: {
      name: params.name,
      slug: embeddedSlug(params.plannedWorkoutId),
      status: 'draft',
      origin: 'PROGRAM_EMBEDDED',
      programId: params.programId,
      estimatedDurationMin: params.estimatedDurationMin ?? undefined,
      isFeatured: false,
    },
  });

  const mainPhaseId = await resolveMainPhaseId(tx);
  await tx.workoutTemplatePhase.create({
    data: {
      workoutTemplateId: template.id,
      phaseId: mainPhaseId,
      sortOrder: 0,
    },
  });

  return template.id;
}

async function syncTemplateContent(
  tx: Tx,
  templateId: string,
  phases?: WorkoutPhaseInput[],
  legacyExercises?: WorkoutExerciseInput[],
) {
  if (phases && phases.length > 0) {
    await workoutService.replaceWorkoutPhases(tx, templateId, phases);
    return;
  }

  const mainPhaseInstance = await tx.workoutTemplatePhase.findFirst({
    where: { workoutTemplateId: templateId },
    orderBy: { sortOrder: 'asc' },
  });
  if (!mainPhaseInstance) {
    const mainPhaseId = await resolveMainPhaseId(tx);
    const created = await tx.workoutTemplatePhase.create({
      data: { workoutTemplateId: templateId, phaseId: mainPhaseId, sortOrder: 0 },
    });
    await workoutService.replacePhaseExercises(
      tx,
      templateId,
      created.id,
      legacyExercises ?? [],
    );
    return;
  }

  await workoutService.replacePhaseExercises(
    tx,
    templateId,
    mainPhaseInstance.id,
    legacyExercises ?? [],
  );
}

export async function resolveProgramIdForDay(tx: Tx, dayId: string): Promise<string | null> {
  const day = await tx.programDay.findUnique({
    where: { id: dayId },
    select: { week: { select: { programId: true } } },
  });
  return day?.week.programId ?? null;
}

/**
 * Ensure planned workout has an embedded or linked template; sync content from input.
 */
export async function syncPlannedWorkoutTemplate(
  tx: Tx,
  params: {
    plannedWorkoutId: string;
    dayId: string;
    input: PlannedWorkoutInput;
    existingTemplateId?: string | null;
  },
): Promise<string> {
  const programId = await resolveProgramIdForDay(tx, params.dayId);
  if (!programId) throw new Error(`Program not found for day ${params.dayId}`);

  let templateId = params.existingTemplateId ?? params.input.workoutTemplateId ?? null;

  const hasContentEdits =
    (params.input.phases?.length ?? 0) > 0 || (params.input.items?.length ?? 0) > 0;

  if (templateId) {
    const linked = await tx.workoutTemplate.findFirst({
      where: { id: templateId, deletedAt: null },
      select: { id: true, origin: true },
    });
    if (!linked) {
      templateId = null;
    } else if (linked.origin === 'STANDALONE' && !hasContentEdits) {
      await tx.workoutTemplate.update({
        where: { id: templateId },
        data: {
          name: params.input.name as object,
          estimatedDurationMin: params.input.estimatedDurationMin ?? undefined,
        },
      });
      return templateId;
    } else if (linked.origin === 'STANDALONE' && hasContentEdits) {
      templateId = null;
    }
  }

  if (!templateId) {
    templateId = await createEmbeddedShell(tx, {
      plannedWorkoutId: params.plannedWorkoutId,
      programId,
      name: params.input.name as Prisma.InputJsonValue,
      estimatedDurationMin: params.input.estimatedDurationMin,
    });
  } else {
    await tx.workoutTemplate.update({
      where: { id: templateId },
      data: {
        name: params.input.name as object,
        estimatedDurationMin: params.input.estimatedDurationMin ?? undefined,
        programId,
        origin: 'PROGRAM_EMBEDDED',
      },
    });
  }

  const legacyExercises =
    params.input.items && params.input.items.length > 0
      ? plannedItemsToExercises(params.input.items)
      : undefined;

  await syncTemplateContent(tx, templateId, params.input.phases, legacyExercises);

  return templateId;
}

/** Clone a standalone template into a new PROGRAM_EMBEDDED copy for a planned workout slot. */
export async function cloneTemplateForPlannedWorkout(
  tx: Tx,
  params: {
    sourceTemplateId: string;
    plannedWorkoutId: string;
    programId: string;
    name: Prisma.InputJsonValue;
  },
): Promise<string> {
  const source = await tx.workoutTemplate.findFirst({
    where: { id: params.sourceTemplateId, deletedAt: null },
    include: {
      phases: {
        orderBy: { sortOrder: 'asc' },
        include: {
          exercises: { orderBy: { sortOrder: 'asc' } },
        },
      },
    },
  });
  if (!source) throw new Error('Source workout template not found');

  const templateId = await createEmbeddedShell(tx, {
    plannedWorkoutId: params.plannedWorkoutId,
    programId: params.programId,
    name: params.name,
    estimatedDurationMin: source.estimatedDurationMin,
  });

  if (source.phases.length > 0) {
    for (const phase of source.phases) {
      const createdPhase = await tx.workoutTemplatePhase.create({
        data: {
          workoutTemplateId: templateId,
          phaseId: phase.phaseId,
          sortOrder: phase.sortOrder,
          nameOverride: phase.nameOverride ?? undefined,
          canSkipOverride: phase.canSkipOverride ?? undefined,
          canContinueOverride: phase.canContinueOverride ?? undefined,
          maxContinueTimeMsOverride: phase.maxContinueTimeMsOverride ?? undefined,
        },
      });
      if (phase.exercises.length > 0) {
        await tx.workoutTemplateExercise.createMany({
          data: phase.exercises.map((ex) => ({
            workoutTemplateId: templateId,
            workoutTemplatePhaseId: createdPhase.id,
            exerciseId: ex.exerciseId,
            variantIndex: ex.variantIndex,
            targetReps: ex.targetReps,
            targetRepsPerSet: ex.targetRepsPerSet ?? undefined,
            targetDuration: ex.targetDuration,
            sets: ex.sets,
            restBetweenSetsMs: ex.restBetweenSetsMs,
            restBetweenSetsPerSetMs: ex.restBetweenSetsPerSetMs ?? undefined,
            restAfterExerciseMs: ex.restAfterExerciseMs,
            weightKg: ex.weightKg,
            weightPerSet: ex.weightPerSet ?? undefined,
            notes: ex.notes ?? undefined,
            sortOrder: ex.sortOrder,
          })),
        });
      }
    }
    // Remove default empty main phase created by shell
    await tx.workoutTemplatePhase.deleteMany({
      where: {
        workoutTemplateId: templateId,
        exercises: { none: {} },
      },
    });
  }

  return templateId;
}

export type ProgramEmbeddedWorkoutDb = Pick<PrismaClient, 'workoutTemplate' | 'plannedWorkout'>;
