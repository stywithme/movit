/**
 * Programs Service
 * =================
 * Service for Program CRUD operations.
 */

import { randomUUID } from 'crypto';
import { getPrisma } from '@/lib/prisma/client';
import {
  cloneTemplateForPlannedWorkout,
  syncPlannedWorkoutTemplate,
} from '@/lib/program-embedded-workout';
import { flattenTemplatePhasesToProgramItems } from '@/lib/program-workout-export';
import {
  Prisma,
  type Program,
  ProgramAttributeMode,
  type PrismaClient,
} from '@prisma/client';
import { workoutService } from '@/modules/workout-templates/workout-templates.service';
import { resolveTrainingPositionMeta, countTrainingDaySlots } from '@/modules/active-plan/plan-position';
import { getAutoAssignmentReadiness } from './program-assignment';
import { validateCalendarProgramStructure, inferWeeklySessionTargetFromWeeks } from './calendar-program-structure';
import {
  buildCatchUpSuggestionFromMeta,
  getLastPlannedWorkoutCompletedAt,
} from './program-catchup';
import type {
  CreateProgramInput,
  ProgramExport,
  ProgramPreviewExport,
  ProgramDayInput,
  PlannedWorkoutItemInput,
  PlannedWorkoutInput,
  ProgramWeekInput,
  UpdateUserProgramInput,
  TodayPlanResponse,
  UpdateProgramInput,
  ProgramAttributeInput,
  ProgramDayType,
} from './programs.types';
import { validateProgramGraphRefs } from './program-graph-validation';

interface LocalizedText {
  ar: string;
  en: string;
}

function parseLocalizedText(value: unknown): LocalizedText | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const record = value as Record<string, unknown>;
  if (typeof record.ar !== 'string' || typeof record.en !== 'string') return undefined;
  return { ar: record.ar, en: record.en };
}

function toInputJson(value: unknown): Prisma.InputJsonValue | undefined {
  if (value === null || value === undefined) return undefined;
  return value as Prisma.InputJsonValue;
}

function toInputJsonOrNull(value: unknown): Prisma.InputJsonValue | Prisma.JsonNullValueInput {
  if (value === null || value === undefined) return Prisma.JsonNull;
  return value as Prisma.InputJsonValue;
}

function generateSlug(name: { en?: string; ar?: string }): string {
  const baseName = name.en || name.ar || 'program';
  return baseName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    + '_' + Date.now().toString(36);
}

function normalizeProgramDayType(dayType?: string): ProgramDayType {
  if (dayType === 'rest' || dayType === 'active_recovery') return dayType;
  return 'training';
}

function deriveIsRestDayFromDayType(dayType?: string): boolean {
  return normalizeProgramDayType(dayType) !== 'training';
}

function programDayScalarsFromInput(day: ProgramDayInput, dayNumber: number) {
  const dayType = normalizeProgramDayType(day.dayType);
  return {
    dayNumber,
    dayType,
    isRestDay: deriveIsRestDayFromDayType(dayType),
  };
}

function normalizeWeeksInput(weeks: ProgramWeekInput[] | undefined): ProgramWeekInput[] {
  if (!weeks) return [];
  return weeks.map((week, weekIndex) => ({
    ...week,
    weekNumber: weekIndex + 1,
    sortOrder: week.sortOrder ?? weekIndex,
    days: (week.days ?? []).map((day, dayIndex) => ({
      ...day,
      ...programDayScalarsFromInput(day, dayIndex + 1),
    })),
  }));
}

async function assertMuscleAttributeValueIds(
  db: Pick<PrismaClient, 'attributeValue'>,
  valueIds: string[],
) {
  if (valueIds.length === 0) return;
  const uniqueIds = Array.from(new Set(valueIds));
  const rows = await db.attributeValue.findMany({
    where: { id: { in: uniqueIds } },
    include: { attribute: true },
  });
  if (rows.length !== uniqueIds.length) {
    throw new Error('One or more target muscle value ids are invalid');
  }
  const invalid = rows.find((row) => row.attribute.code !== 'muscle');
  if (invalid) {
    throw new Error(`Attribute value ${invalid.id} is not a muscle attribute`);
  }
}

async function syncDayTargetMuscles(
  tx: Prisma.TransactionClient,
  dayId: string,
  targetMuscleValueIds: string[] | undefined,
) {
  if (targetMuscleValueIds === undefined) return;
  const uniqueIds = Array.from(new Set(targetMuscleValueIds));
  await assertMuscleAttributeValueIds(tx, uniqueIds);
  await tx.programDayAttribute.deleteMany({ where: { dayId } });
  if (uniqueIds.length === 0) return;
  await tx.programDayAttribute.createMany({
    data: uniqueIds.map((attributeValueId) => ({ dayId, attributeValueId })),
  });
}

const programFullInclude = {
  programAttributes: {
    orderBy: { createdAt: 'asc' as const },
    include: {
      attributeValue: {
        include: { attribute: true },
      },
    },
  },
  levelMin: {
    select: { id: true, number: true, code: true, name: true },
  },
  levelMax: {
    select: { id: true, number: true, code: true, name: true },
  },
  weeks: {
    orderBy: [
      { sortOrder: 'asc' as const },
      { weekNumber: 'asc' as const },
    ],
    include: {
      days: {
        orderBy: { dayNumber: 'asc' as const },
        include: {
          targetMuscles: {
            include: { attributeValue: true },
          },
          plannedWorkouts: {
            orderBy: { sortOrder: 'asc' as const },
            include: {
              workoutTemplate: {
                include: {
                  phases: {
                    orderBy: { sortOrder: 'asc' as const },
                    include: {
                      phase: true,
                      exercises: {
                        orderBy: { sortOrder: 'asc' as const },
                        include: {
                          exercise: {
                            select: {
                              id: true,
                              slug: true,
                              name: true,
                              intent: true,
                              coachingNotes: true,
                            },
                          },
                        },
                      },
                    },
                  },
                },
              },
              items: {
                orderBy: { sortOrder: 'asc' as const },
                include: {
                  exercise: {
                    select: {
                      id: true,
                      slug: true,
                      name: true,
                      intent: true,
                      coachingNotes: true,
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  },
};

async function replaceProgramAttributes(
  db: Pick<PrismaClient, 'programAttribute'>,
  programId: string,
  attrs: ProgramAttributeInput[] | undefined,
) {
  if (attrs === undefined) return;
  const unique = Array.from(new Map(attrs.map((a) => [a.attributeValueId, a])).values());
  await db.programAttribute.deleteMany({ where: { programId } });
  if (unique.length === 0) return;
  await db.programAttribute.createMany({
    data: unique.map((a) => ({
      programId,
      attributeValueId: a.attributeValueId,
      mode: a.mode ?? ProgramAttributeMode.REQUIRED,
    })),
  });
}

async function resolveLevelIdByNumber(
  db: Pick<PrismaClient, 'level'>,
  levelNumber: number | undefined,
): Promise<string | undefined> {
  if (levelNumber === undefined || levelNumber === null) return undefined;
  const level = await db.level.findUnique({
    where: { number: levelNumber },
    select: { id: true },
  });
  return level?.id;
}

async function resolveProgramLevelIds(
  db: Pick<PrismaClient, 'level'>,
  data: {
    levelMinId?: string | null;
    levelMaxId?: string | null;
    levelRangeMin?: number;
    levelRangeMax?: number;
  },
) {
  return {
    levelMinId:
      data.levelMinId !== undefined
        ? data.levelMinId ?? undefined
        : await resolveLevelIdByNumber(db, data.levelRangeMin),
    levelMaxId:
      data.levelMaxId !== undefined
        ? data.levelMaxId ?? undefined
        : await resolveLevelIdByNumber(db, data.levelRangeMax),
  };
}

function exportLevel(level: { id: string; number: number; code: string; name: unknown } | null | undefined) {
  if (!level) return null;
  return {
    id: level.id,
    number: level.number,
    code: level.code,
    name: parseLocalizedText(level.name) || { ar: '', en: '' },
  };
}

export { validateCalendarProgramStructure, inferWeeklySessionTargetFromWeeks } from './calendar-program-structure';

function buildPlannedWorkoutItems(items?: PlannedWorkoutItemInput[]) {
  if (!items || items.length === 0) return undefined;
  return {
    create: items.map((item, index) => ({
      type: item.type,
      exerciseId: item.exerciseId ?? undefined,
      sets: item.sets ?? undefined,
      targetReps: item.targetReps ?? undefined,
      targetDuration: item.targetDuration ?? undefined,
      restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
      weightKg: item.weightKg ?? undefined,
      weightPerSet: item.weightPerSet ?? undefined,
      notes: toInputJson(parseLocalizedText(item.notes)),
      restDurationMs: item.restDurationMs ?? undefined,
      sourceWorkoutTemplateId: item.sourceWorkoutTemplateId ?? undefined,
      sortOrder: item.sortOrder ?? index,
    })),
  };
}

async function seedProgramWeeks(
  tx: Prisma.TransactionClient,
  programId: string,
  weeks?: ProgramWeekInput[],
) {
  const normalized = normalizeWeeksInput(weeks);
  if (normalized.length === 0) return;

  for (const [weekIndex, week] of normalized.entries()) {
    const createdWeek = await tx.programWeek.create({
      data: {
        programId,
        weekNumber: week.weekNumber!,
        target: (week.target as object) || undefined,
        description: (week.description as object) || undefined,
        sortOrder: week.sortOrder ?? weekIndex,
      },
    });

    for (const day of week.days ?? []) {
      const createdDay = await tx.programDay.create({
        data: {
          weekId: createdWeek.id,
          ...programDayScalarsFromInput(day, day.dayNumber!),
        },
      });
      await syncDayTargetMuscles(tx, createdDay.id, day.targetMuscleValueIds);
      await syncPlannedWorkouts(tx, createdDay.id, day.plannedWorkouts);
    }
  }
}

const WEEK_NUMBER_TEMP_OFFSET = 100_000;

function plannedWorkoutItemScalarFromInput(item: PlannedWorkoutItemInput, sortOrder: number) {
  return {
    type: item.type,
    exerciseId: item.exerciseId ?? undefined,
    sets: item.sets ?? undefined,
    targetReps: item.targetReps ?? undefined,
    targetDuration: item.targetDuration ?? undefined,
    restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
    weightKg: item.weightKg ?? undefined,
    weightPerSet: item.weightPerSet ?? undefined,
    notes: toInputJson(parseLocalizedText(item.notes)),
    restDurationMs: item.restDurationMs ?? undefined,
    sourceWorkoutTemplateId: item.sourceWorkoutTemplateId ?? undefined,
    sortOrder,
  };
}

function prismaPlannedWorkoutScalars(plannedWorkout: PlannedWorkoutInput, workoutIndex: number) {
  return {
    name: plannedWorkout.name as object,
    sortOrder: plannedWorkout.sortOrder ?? workoutIndex,
    estimatedDurationMin: plannedWorkout.estimatedDurationMin ?? undefined,
  };
}

type PlannedWorkoutWithTemplate = {
  id: string;
  name: unknown;
  sortOrder: number;
  workoutTemplateId: string;
  estimatedDurationMin: number | null;
  workoutTemplate: NonNullable<Parameters<typeof workoutService.buildWorkoutExport>[0]> & {
    phases: Array<{
      id: string;
      phaseId: string;
      sortOrder: number;
      nameOverride: unknown;
      canSkipOverride: boolean | null;
      canContinueOverride: boolean | null;
      maxContinueTimeMsOverride: number | null;
      phase: { role: string };
      exercises: Array<{
        id: string;
        exerciseId: string;
        variantIndex: number;
        targetReps: number | null;
        targetRepsPerSet: unknown;
        targetDuration: number | null;
        sets: number;
        restBetweenSetsMs: number;
        restBetweenSetsPerSetMs: unknown;
        restAfterExerciseMs: number;
        weightKg: number | null;
        weightPerSet: unknown;
        notes: unknown;
        sortOrder: number;
        exercise: {
          slug: string;
          intent?: string | null;
          coachingNotes?: unknown;
        } | null;
      }>;
    }>;
  };
};

function exportPlannedWorkout(plannedWorkout: PlannedWorkoutWithTemplate) {
  const workoutExport = workoutService.buildWorkoutExport(
    plannedWorkout.workoutTemplate as Parameters<typeof workoutService.buildWorkoutExport>[0],
  );
  const phases = workoutExport?.phases ?? [];
  const templatePhases = plannedWorkout.workoutTemplate.phases.map((templatePhase) => ({
    id: templatePhase.id,
    sortOrder: templatePhase.sortOrder,
    phase: { role: String(templatePhase.phase.role) },
    canSkip: templatePhase.canSkipOverride ?? templatePhase.phase.canSkip,
    canContinue: templatePhase.canContinueOverride ?? templatePhase.phase.canContinue,
    maxContinueTimeMs:
      templatePhase.maxContinueTimeMsOverride ?? templatePhase.phase.maxContinueTimeMs ?? undefined,
    exercises: templatePhase.exercises.map((exercise) => ({
      id: exercise.id,
      exercise: exercise.exercise,
      sets: exercise.sets,
      targetReps: exercise.targetReps,
      targetRepsPerSet: (exercise.targetRepsPerSet as number[]) ?? undefined,
      targetDuration: exercise.targetDuration,
      restBetweenSetsMs: exercise.restBetweenSetsMs,
      restBetweenSetsPerSetMs: (exercise.restBetweenSetsPerSetMs as number[]) ?? undefined,
      restAfterExerciseMs: exercise.restAfterExerciseMs,
      weightKg: exercise.weightKg,
      weightPerSet: (exercise.weightPerSet as number[]) ?? undefined,
      notes: parseLocalizedText(exercise.notes),
      sortOrder: exercise.sortOrder,
    })),
  }));
  // P3.5: mark missing/unpublished exercises so mobile can filter safely.
  const items = flattenTemplatePhasesToProgramItems(templatePhases, {
    resolveExerciseMeta: (exerciseSlug) => {
      for (const phase of plannedWorkout.workoutTemplate.phases) {
        for (const row of phase.exercises) {
          if (row.exercise?.slug === exerciseSlug) {
            return {
              intent: row.exercise.intent ?? null,
              coachingNotes: row.exercise.coachingNotes,
              deletedExercise: false,
            };
          }
        }
      }
      return { deletedExercise: true };
    },
  });
  return {
    id: plannedWorkout.id,
    name: parseLocalizedText(plannedWorkout.name) || workoutExport?.name || { ar: '', en: '' },
    sortOrder: plannedWorkout.sortOrder,
    workoutTemplateId: plannedWorkout.workoutTemplateId,
    workoutTemplateSlug: workoutExport?.slug,
    estimatedDurationMin:
      plannedWorkout.estimatedDurationMin ?? workoutExport?.estimatedDurationMin ?? undefined,
    phases,
    items,
  };
}

function plannedWorkoutInputFromTemplate(
  plannedWorkout: PlannedWorkoutWithTemplate,
  plannedWorkoutIndex: number,
): PlannedWorkoutInput {
  const template = plannedWorkout.workoutTemplate;
  return {
    name: parseLocalizedText(plannedWorkout.name) || { ar: '', en: '' },
    sortOrder: plannedWorkout.sortOrder ?? plannedWorkoutIndex,
    estimatedDurationMin: plannedWorkout.estimatedDurationMin ?? undefined,
    workoutTemplateId: plannedWorkout.workoutTemplateId,
    phases: template.phases.map((templatePhase, phaseIndex) => ({
      phaseId: templatePhase.phaseId,
      sortOrder: templatePhase.sortOrder ?? phaseIndex,
      nameOverride: parseLocalizedText(templatePhase.nameOverride),
      canSkipOverride: templatePhase.canSkipOverride ?? undefined,
      canContinueOverride: templatePhase.canContinueOverride ?? undefined,
      maxContinueTimeMsOverride: templatePhase.maxContinueTimeMsOverride ?? undefined,
      exercises: templatePhase.exercises.map((exercise, exerciseIndex) => ({
        exerciseId: exercise.exerciseId,
        variantIndex: exercise.variantIndex,
        targetReps: exercise.targetReps ?? undefined,
        targetRepsPerSet: (exercise.targetRepsPerSet as number[]) ?? undefined,
        targetDuration: exercise.targetDuration ?? undefined,
        sets: exercise.sets ?? undefined,
        restBetweenSetsMs: exercise.restBetweenSetsMs ?? undefined,
        restBetweenSetsPerSetMs: (exercise.restBetweenSetsPerSetMs as number[]) ?? undefined,
        restAfterExerciseMs: exercise.restAfterExerciseMs ?? undefined,
        weightKg: exercise.weightKg ?? undefined,
        weightPerSet: (exercise.weightPerSet as number[]) ?? undefined,
        notes: parseLocalizedText(exercise.notes),
        sortOrder: exercise.sortOrder ?? exerciseIndex,
      })),
    })),
  };
}

async function createPlannedWorkoutRecord(
  tx: Prisma.TransactionClient,
  dayId: string,
  plannedWorkout: PlannedWorkoutInput,
  sortOrder: number,
): Promise<string> {
  const id = plannedWorkout.id ?? randomUUID();
  const templateId = await syncPlannedWorkoutTemplate(tx, {
    plannedWorkoutId: id,
    dayId,
    input: plannedWorkout,
    existingTemplateId: plannedWorkout.workoutTemplateId,
  });
  await tx.plannedWorkout.create({
    data: {
      id,
      dayId,
      workoutTemplateId: templateId,
      ...prismaPlannedWorkoutScalars(plannedWorkout, sortOrder),
    },
  });
  return id;
}

async function syncPlannedWorkoutItems(
  tx: Prisma.TransactionClient,
  plannedWorkoutId: string,
  itemsInput: PlannedWorkoutItemInput[] | undefined,
) {
  const items = itemsInput ?? [];
  const existing = await tx.plannedWorkoutItem.findMany({
    where: { plannedWorkoutId },
    select: { id: true },
  });
  const existingIds = new Set(existing.map((e) => e.id));
  const payloadIds = new Set(items.map((i) => i.id).filter(Boolean) as string[]);

  const toRemove = existing.filter((e) => !payloadIds.has(e.id)).map((e) => e.id);
  if (toRemove.length > 0) {
    await tx.plannedWorkoutItem.deleteMany({ where: { id: { in: toRemove } } });
  }

  for (let index = 0; index < items.length; index++) {
    const item = items[index]!;
    const sortOrder = item.sortOrder ?? index;
    const data = plannedWorkoutItemScalarFromInput(item, sortOrder);
    if (item.id) {
      if (!existingIds.has(item.id)) {
        throw new Error(`Invalid planned workout item id for workout ${plannedWorkoutId}: ${item.id}`);
      }
      await tx.plannedWorkoutItem.update({
        where: { id: item.id },
        data,
      });
    } else {
      await tx.plannedWorkoutItem.create({
        data: {
          ...data,
          plannedWorkoutId,
        },
      });
    }
  }
}

async function syncPlannedWorkouts(
  tx: Prisma.TransactionClient,
  dayId: string,
  workoutsInput: PlannedWorkoutInput[] | undefined,
) {
  const plannedWorkouts = workoutsInput ?? [];
  const existing = await tx.plannedWorkout.findMany({
    where: { dayId },
    select: { id: true, workoutTemplateId: true },
  });
  const existingById = new Map(existing.map((e) => [e.id, e]));
  const payloadIds = new Set(plannedWorkouts.map((s) => s.id).filter(Boolean) as string[]);

  const toRemove = existing.filter((e) => !payloadIds.has(e.id)).map((e) => e.id);
  if (toRemove.length > 0) {
    await tx.plannedWorkout.deleteMany({ where: { id: { in: toRemove } } });
  }

  for (let index = 0; index < plannedWorkouts.length; index++) {
    const plannedWorkout = plannedWorkouts[index]!;
    const sortOrder = plannedWorkout.sortOrder ?? index;
    if (plannedWorkout.id && existingById.has(plannedWorkout.id)) {
      const current = existingById.get(plannedWorkout.id)!;
      const templateId = await syncPlannedWorkoutTemplate(tx, {
        plannedWorkoutId: plannedWorkout.id,
        dayId,
        input: plannedWorkout,
        existingTemplateId: current.workoutTemplateId,
      });
      await tx.plannedWorkout.update({
        where: { id: plannedWorkout.id },
        data: {
          ...prismaPlannedWorkoutScalars(plannedWorkout, sortOrder),
          workoutTemplateId: templateId,
        },
      });
    } else {
      await createPlannedWorkoutRecord(tx, dayId, plannedWorkout, sortOrder);
    }
  }
}

async function syncDays(
  tx: Prisma.TransactionClient,
  weekId: string,
  daysInput: ProgramDayInput[] | undefined,
) {
  const days = daysInput ?? [];
  const existing = await tx.programDay.findMany({
    where: { weekId },
    select: { id: true },
  });
  const existingIds = new Set(existing.map((e) => e.id));
  const payloadIds = new Set(days.map((d) => d.id).filter(Boolean) as string[]);

  const toRemove = existing.filter((e) => !payloadIds.has(e.id)).map((e) => e.id);
  if (toRemove.length > 0) {
    await tx.programDay.deleteMany({ where: { id: { in: toRemove } } });
  }

  for (let index = 0; index < days.length; index++) {
    const day = days[index]!;
    if (day.id) {
      if (!existingIds.has(day.id)) {
        throw new Error(`Invalid program day id for week ${weekId}: ${day.id}`);
      }
      await tx.programDay.update({
        where: { id: day.id },
        data: programDayScalarsFromInput(day, day.dayNumber!),
      });
      await syncDayTargetMuscles(tx, day.id, day.targetMuscleValueIds);
      await syncPlannedWorkouts(tx, day.id, day.plannedWorkouts);
    } else {
      const createdDay = await tx.programDay.create({
        data: {
          weekId,
          ...programDayScalarsFromInput(day, day.dayNumber!),
        },
      });
      await syncDayTargetMuscles(tx, createdDay.id, day.targetMuscleValueIds);
      await syncPlannedWorkouts(tx, createdDay.id, day.plannedWorkouts);
    }
  }
}

/**
 * Upsert program calendar: preserve row ids where `id` is supplied and valid;
 * create missing rows; delete DB rows omitted from the payload (per level).
 */
async function syncProgramWeeksStructure(
  tx: Prisma.TransactionClient,
  programId: string,
  weeksIn: ProgramWeekInput[],
) {
  const weeks = normalizeWeeksInput(weeksIn);
  const existingRows = await tx.programWeek.findMany({
    where: { programId },
    select: { id: true },
  });
  const validWeekIds = new Set(existingRows.map((w) => w.id));

  for (const week of weeks) {
    if (week.id && !validWeekIds.has(week.id)) {
      throw new Error(`Invalid program week id for this program: ${week.id}`);
    }
  }

  const payloadWeekIds = new Set(weeks.map((w) => w.id).filter(Boolean) as string[]);
  const weekIdsToDelete = existingRows.map((w) => w.id).filter((wid) => !payloadWeekIds.has(wid));
  if (weekIdsToDelete.length > 0) {
    await tx.programWeek.deleteMany({
      where: { programId, id: { in: weekIdsToDelete } },
    });
  }

  const refreshed = await tx.programWeek.findMany({
    where: { programId },
    select: { id: true },
  });
  const validAfterDelete = new Set(refreshed.map((w) => w.id));

  const existingWeeksInPayload = weeks.filter((w) => w.id && validAfterDelete.has(w.id));
  for (const week of existingWeeksInPayload) {
    await tx.programWeek.update({
      where: { id: week.id! },
      data: {
        weekNumber: week.weekNumber! + WEEK_NUMBER_TEMP_OFFSET,
        sortOrder: week.sortOrder ?? 0,
      },
    });
  }

  for (const week of weeks) {
    const sortOrder = week.sortOrder ?? 0;
    if (week.id && validAfterDelete.has(week.id)) {
      await tx.programWeek.update({
        where: { id: week.id },
        data: {
          weekNumber: week.weekNumber!,
          target: (week.target as object) || undefined,
          description: (week.description as object) || undefined,
          sortOrder,
        },
      });
      await syncDays(tx, week.id, week.days);
    } else {
      const createdWeek = await tx.programWeek.create({
        data: {
          programId,
          weekNumber: week.weekNumber!,
          target: (week.target as object) || undefined,
          description: (week.description as object) || undefined,
          sortOrder,
        },
      });
      for (const day of week.days ?? []) {
        const createdDay = await tx.programDay.create({
          data: {
            weekId: createdWeek.id,
            ...programDayScalarsFromInput(day, day.dayNumber!),
          },
        });
        await syncDayTargetMuscles(tx, createdDay.id, day.targetMuscleValueIds);
        await syncPlannedWorkouts(tx, createdDay.id, day.plannedWorkouts);
      }
    }
  }
}

/** Readiness badge for admin list (same shape as previous inline logic). */
function enrichProgramListRow(program: Program, activeEnrollmentCount: number) {
  const readiness = getAutoAssignmentReadiness(program);
  const entersAutoAssignment =
    program.programType === 'SYSTEM' ||
    (program.programType === 'COACH' && program.autoAssignable);
  const status = !entersAutoAssignment
    ? 'manual_only'
    : readiness.ready
      ? 'ready'
      : 'incomplete';

  return {
    ...program,
    activeEnrollmentCount,
    autoAssignmentReadiness: {
      ...readiness,
      entersAutoAssignment,
      status,
    },
  };
}

async function batchActiveEnrollmentCounts(
  prisma: Awaited<ReturnType<typeof getPrisma>>,
  programIds: string[],
): Promise<Map<string, number>> {
  if (programIds.length === 0) return new Map();
  const agg = await prisma.userProgram.groupBy({
    by: ['programId'],
    where: {
      programId: { in: programIds },
      isActive: true,
    },
    _count: { id: true },
  });
  return new Map(
    agg
      .filter((a): a is typeof a & { programId: string } => a.programId != null)
      .map((a) => [a.programId, a._count.id]),
  );
}

function formatMapProgramRow(
  program: Program & {
    levelMin?: { number: number } | null;
    levelMax?: { number: number } | null;
  },
  activeEnrollmentCount: number,
) {
  const base = enrichProgramListRow(program, activeEnrollmentCount);
  return {
    ...base,
    levelRangeMin: program.levelMin?.number ?? null,
    levelRangeMax: program.levelMax?.number ?? null,
  };
}

export const programService = {
  /**
   * Programs map payload: all non-deleted programs plus chain target metadata
   * for next/prerequisite refs (including targets outside the main list).
   */
  async getMapData() {
    const prisma = await getPrisma();

    const rows = await prisma.program.findMany({
      where: { deletedAt: null },
      orderBy: { createdAt: 'desc' },
      include: {
        levelMin: { select: { number: true } },
        levelMax: { select: { number: true } },
        programAttributes: {
          orderBy: { createdAt: 'asc' },
          include: {
            attributeValue: { include: { attribute: true } },
          },
        },
      },
    });

    const refIds = new Set<string>();
    for (const program of rows) {
      if (program.nextProgramId) refIds.add(program.nextProgramId);
      if (program.prerequisiteProgramId) refIds.add(program.prerequisiteProgramId);
    }

    const chainTargetRows =
      refIds.size > 0
        ? await prisma.program.findMany({
            where: { id: { in: [...refIds] } },
            select: {
              id: true,
              slug: true,
              name: true,
              isPublished: true,
              deletedAt: true,
            },
          })
        : [];

    const counts = await batchActiveEnrollmentCounts(
      prisma,
      rows.map((r) => r.id),
    );

    const chainTargets: Record<
      string,
      {
        id: string;
        slug: string;
        name: LocalizedText;
        isPublished: boolean;
        deletedAt: string | null;
      }
    > = {};

    for (const target of chainTargetRows) {
      chainTargets[target.id] = {
        id: target.id,
        slug: target.slug,
        name: parseLocalizedText(target.name) || { ar: '', en: '' },
        isPublished: target.isPublished,
        deletedAt: target.deletedAt?.toISOString() ?? null,
      };
    }

    return {
      programs: rows.map((program) =>
        formatMapProgramRow(program, counts.get(program.id) ?? 0),
      ),
      chainTargets,
    };
  },

  async list(filters?: {
    status?: 'draft' | 'published';
    search?: string;
    page?: number;
    limit?: number;
    readiness?: 'ready' | 'incomplete' | 'manual_only';
  }) {
    const prisma = await getPrisma();
    const page = filters?.page || 1;
    const limit = filters?.limit || 20;
    const skip = (page - 1) * limit;

    const where: Prisma.ProgramWhereInput = {
      deletedAt: null,
    };
    if (filters?.status === 'published') {
      where.isPublished = true;
    }
    if (filters?.status === 'draft') {
      where.isPublished = false;
    }

    const andFilters: Prisma.ProgramWhereInput[] = [];
    if (filters?.search) {
      andFilters.push({
        OR: [
          { name: { path: ['en'], string_contains: filters.search } },
          { name: { path: ['ar'], string_contains: filters.search } },
        ],
      });
    }
    if (andFilters.length > 0) {
      where.AND = andFilters;
    }

    if (!filters?.readiness) {
      const [rows, total] = await Promise.all([
        prisma.program.findMany({
          where,
          orderBy: { createdAt: 'desc' },
          skip,
          take: limit,
          include: {
            programAttributes: {
              orderBy: { createdAt: 'asc' },
              include: {
                attributeValue: { include: { attribute: true } },
              },
            },
          },
        }),
        prisma.program.count({ where }),
      ]);
      const ids = rows.map((r) => r.id);
      const counts = await batchActiveEnrollmentCounts(prisma, ids);
      const programs = rows.map((program) =>
        enrichProgramListRow(program, counts.get(program.id) ?? 0),
      );
      return {
        programs,
        pagination: {
          page,
          limit,
          total,
          totalPages: Math.ceil(total / limit),
        },
      };
    }

    const rows = await prisma.program.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      include: {
        programAttributes: {
          orderBy: { createdAt: 'asc' },
          include: {
            attributeValue: { include: { attribute: true } },
          },
        },
      },
    });
    if (rows.length > 500) {
      console.warn(
        '[ProgramsService] list with readiness filter loaded',
        rows.length,
        'rows ? consider narrowing filters',
      );
    }

    const allIds = rows.map((r) => r.id);
    const allCounts = await batchActiveEnrollmentCounts(prisma, allIds);
    const enriched = rows.map((program) =>
      enrichProgramListRow(program, allCounts.get(program.id) ?? 0),
    );
    const filtered = enriched.filter(
      (program) => program.autoAssignmentReadiness.status === filters.readiness,
    );
    const total = filtered.length;
    const programs = filtered.slice(skip, skip + limit);

    return {
      programs,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    };
  },

  async getById(id: string) {
    const prisma = await getPrisma();
    const program = await prisma.program.findFirst({
      where: { id, deletedAt: null },
      include: programFullInclude,
    });
    if (!program) return null;
    const counts = await batchActiveEnrollmentCounts(prisma, [program.id]);
    return {
      ...program,
      activeEnrollmentCount: counts.get(program.id) ?? 0,
    };
  },

  async getBySlug(slug: string) {
    const prisma = await getPrisma();
    return prisma.program.findFirst({
      where: { slug, deletedAt: null },
      include: programFullInclude,
    });
  },

  async create(data: CreateProgramInput, createdBy?: string) {
    const prisma = await getPrisma();
    const slug = data.slug || generateSlug(data.name);

    const effectiveWeeks = normalizeWeeksInput(data.weeks);

    await validateProgramGraphRefs(prisma, '', {
      nextProgramId: data.nextProgramId,
      prerequisiteProgramId: data.prerequisiteProgramId,
    });

    const programId = await prisma.$transaction(async (tx) => {
      const resolvedLevels = await resolveProgramLevelIds(tx, data);
      const program = await tx.program.create({
        data: {
          name: data.name as object,
          description: (data.description as object) || undefined,
          slug,
          coverImageUrl: data.coverImageUrl ?? undefined,
          durationWeeks: data.durationWeeks,
          tags: data.tags ?? undefined,
          isDefault: data.isDefault ?? false,
          isPublished: false,
          createdBy,
          updatedBy: createdBy,
          programType: data.programType ?? 'SYSTEM',
          autoAssignable: data.autoAssignable ?? false,
          version: data.version ?? 1,
          ownerId: data.ownerId ?? undefined,
          forkedFromId: data.forkedFromId ?? undefined,
          coachingNotes: data.coachingNotes as object ?? undefined,
          weeklyWorkoutTarget:
            data.weeklyWorkoutTarget ??
            inferWeeklySessionTargetFromWeeks(
              effectiveWeeks as {
                weekNumber: number;
                days: { isRestDay?: boolean; dayType?: string | null }[];
              }[],
            ) ??
            undefined,
          estimatedWorkoutMinutes: data.estimatedWorkoutMinutes ?? undefined,
          levelMinId: resolvedLevels.levelMinId,
          levelMaxId: resolvedLevels.levelMaxId,
          prerequisiteProgramId: data.prerequisiteProgramId ?? undefined,
          nextProgramId: data.nextProgramId ?? undefined,
        },
      });

      await seedProgramWeeks(tx, program.id, effectiveWeeks);

      if (data.programAttributes !== undefined) {
        await replaceProgramAttributes(tx, program.id, data.programAttributes);
      }
      return program.id;
    });

    const refreshed = await this.getById(programId);
    if (!refreshed) throw new Error('Program not found after create');
    return refreshed;
  },

  async update(id: string, data: UpdateProgramInput, updatedBy?: string) {
    const prisma = await getPrisma();

    const current = await prisma.program.findFirst({
      where: { id, deletedAt: null },
      select: { nextProgramId: true, prerequisiteProgramId: true, isPublished: true },
    });
    if (!current) throw new Error('Program not found');

    const graphRefsTouched =
      data.nextProgramId !== undefined ||
      data.prerequisiteProgramId !== undefined ||
      data.isPublished === true;

    if (graphRefsTouched) {
      await validateProgramGraphRefs(
        prisma,
        id,
        {
          nextProgramId:
            data.nextProgramId !== undefined ? data.nextProgramId : current.nextProgramId,
          prerequisiteProgramId:
            data.prerequisiteProgramId !== undefined
              ? data.prerequisiteProgramId
              : current.prerequisiteProgramId,
        },
        {
          requirePublishedTargets: data.isPublished === true || current.isPublished,
        },
      );
    }

    const updateData: Record<string, unknown> = {
      updatedBy,
    };
    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined) updateData.description = data.description;
    if (data.coverImageUrl !== undefined) updateData.coverImageUrl = data.coverImageUrl;
    if (data.durationWeeks !== undefined) updateData.durationWeeks = data.durationWeeks;
    if (data.tags !== undefined) updateData.tags = data.tags;
    if (data.isDefault !== undefined) updateData.isDefault = data.isDefault;
    if (data.isPublished !== undefined) updateData.isPublished = data.isPublished;
    // Prescription metadata
    if (data.programType !== undefined) updateData.programType = data.programType;
    if (data.autoAssignable !== undefined) updateData.autoAssignable = data.autoAssignable;
    if (data.version !== undefined) updateData.version = data.version;
    if (data.ownerId !== undefined) updateData.ownerId = data.ownerId;
    if (data.forkedFromId !== undefined) updateData.forkedFromId = data.forkedFromId;
    if (data.coachingNotes !== undefined) updateData.coachingNotes = data.coachingNotes;
    if (data.weeklyWorkoutTarget !== undefined) {
      updateData.weeklyWorkoutTarget = data.weeklyWorkoutTarget;
    }
    if (data.estimatedWorkoutMinutes !== undefined) {
      updateData.estimatedWorkoutMinutes = data.estimatedWorkoutMinutes;
    }
    if (
      data.levelMinId !== undefined ||
      data.levelMaxId !== undefined ||
      data.levelRangeMin !== undefined ||
      data.levelRangeMax !== undefined
    ) {
      const resolvedLevels = await resolveProgramLevelIds(prisma, data);
      if (data.levelMinId !== undefined || data.levelRangeMin !== undefined) {
        updateData.levelMinId = resolvedLevels.levelMinId ?? null;
      }
      if (data.levelMaxId !== undefined || data.levelRangeMax !== undefined) {
        updateData.levelMaxId = resolvedLevels.levelMaxId ?? null;
      }
    }
    if (data.prerequisiteProgramId !== undefined) updateData.prerequisiteProgramId = data.prerequisiteProgramId;
    if (data.nextProgramId !== undefined) updateData.nextProgramId = data.nextProgramId;

    await prisma.$transaction(async (tx) => {
      await tx.program.update({
        where: { id },
        data: updateData as Prisma.ProgramUpdateInput,
      });

      if (data.weeks !== undefined) {
        await syncProgramWeeksStructure(tx, id, data.weeks);
      }

      if (data.programAttributes !== undefined) {
        await replaceProgramAttributes(tx, id, data.programAttributes);
      }
    });

    return this.getById(id);
  },

  async delete(id: string, updatedBy?: string) {
    const prisma = await getPrisma();
    const now = new Date();
    return prisma.program.update({
      where: { id },
      data: {
        updatedBy,
        updatedAt: now,
        deletedAt: now,
        isPublished: false,
      },
    });
  },

  async publish(id: string, updatedBy?: string) {
    const prisma = await getPrisma();
    const program = await prisma.program.findFirst({
      where: { id, deletedAt: null },
      include: programFullInclude,
    });
    if (!program) {
      throw new Error('Program not found');
    }

    validateCalendarProgramStructure(program.durationWeeks, program.weeks);
    const inferred = inferWeeklySessionTargetFromWeeks(program.weeks);

    const entersAutoAssignment =
      program.programType === 'SYSTEM' ||
      (program.programType === 'COACH' && program.autoAssignable);

    if (entersAutoAssignment) {
      const readiness = getAutoAssignmentReadiness(program);
      if (!readiness.ready) {
        throw new Error(
          `Program is not auto-assignment ready: ${readiness.missingFields.join(', ')}`,
        );
      }
    }

    await validateProgramGraphRefs(
      prisma,
      id,
      {
        nextProgramId: program.nextProgramId,
        prerequisiteProgramId: program.prerequisiteProgramId,
      },
      { requirePublishedTargets: true },
    );

    return prisma.program.update({
      where: { id },
      data: {
        isPublished: true,
        updatedBy,
        ...(inferred != null ? { weeklyWorkoutTarget: inferred } : {}),
      },
    });
  },

  async unpublish(id: string, updatedBy?: string) {
    const prisma = await getPrisma();
    return prisma.program.update({
      where: { id },
      data: { isPublished: false, updatedBy },
    });
  },

  async duplicate(id: string, createdBy?: string) {
    const original = await this.getById(id);
    if (!original) throw new Error('Program not found');

    const name = parseLocalizedText(original.name) || { ar: '', en: '' };
    const newName: LocalizedText = {
      ar: `${name.ar || ''} (????)`,
      en: `${name.en || ''} (Copy)`,
    };

    const weeks = original.weeks.map((week, weekIndex) => ({
      weekNumber: week.weekNumber,
      target: parseLocalizedText(week.target),
      description: parseLocalizedText(week.description),
      sortOrder: week.sortOrder ?? weekIndex,
      days: week.days.map((day) => ({
        dayNumber: day.dayNumber,
        dayType: normalizeProgramDayType(day.dayType),
        targetMuscleValueIds: day.targetMuscles?.map((m) => m.attributeValueId) ?? [],
        plannedWorkouts: day.plannedWorkouts.map((plannedWorkout, plannedWorkoutIndex) =>
          plannedWorkoutInputFromTemplate(plannedWorkout as PlannedWorkoutWithTemplate, plannedWorkoutIndex),
        ),
      })),
    }));

    return this.create(
      {
        name: newName,
        description: parseLocalizedText(original.description),
        coverImageUrl: original.coverImageUrl ?? undefined,
        durationWeeks: original.durationWeeks,
        tags: (original.tags as string[]) || undefined,
        isDefault: false,
        programType: original.programType,
        autoAssignable: original.autoAssignable,
        version: original.version,
        ownerId: original.ownerId ?? undefined,
        forkedFromId: original.id,
        coachingNotes: (original.coachingNotes as Record<string, unknown>) || undefined,
        weeklyWorkoutTarget: original.weeklyWorkoutTarget ?? undefined,
        estimatedWorkoutMinutes: original.estimatedWorkoutMinutes ?? undefined,
        levelMinId: original.levelMinId,
        levelMaxId: original.levelMaxId,
        prerequisiteProgramId: original.prerequisiteProgramId ?? undefined,
        nextProgramId: original.nextProgramId ?? undefined,
        programAttributes: original.programAttributes?.map((pa) => ({
          attributeValueId: pa.attributeValueId,
          mode: pa.mode,
        })),
        weeks,
      },
      createdBy
    );
  },

  async createWeek(programId: string, week: ProgramWeekInput) {
    const prisma = await getPrisma();
    const normalized = normalizeWeeksInput([week])[0]!;
    const weekId = await prisma.$transaction(async (tx) => {
      const createdWeek = await tx.programWeek.create({
        data: {
          programId,
          weekNumber: normalized.weekNumber!,
          target: (normalized.target as object) || undefined,
          description: (normalized.description as object) || undefined,
          sortOrder: normalized.sortOrder ?? 0,
        },
      });
      for (const day of normalized.days ?? []) {
        const createdDay = await tx.programDay.create({
          data: {
            weekId: createdWeek.id,
            ...programDayScalarsFromInput(day, day.dayNumber!),
          },
        });
        await syncDayTargetMuscles(tx, createdDay.id, day.targetMuscleValueIds);
        await syncPlannedWorkouts(tx, createdDay.id, day.plannedWorkouts);
      }
      return createdWeek.id;
    });
    return this.getById(programId).then((p) => p?.weeks.find((w) => w.id === weekId) ?? null);
  },

  async updateWeek(programId: string, weekId: string, week: ProgramWeekInput) {
    const prisma = await getPrisma();
    const existing = await prisma.programWeek.findFirst({ where: { id: weekId, programId } });
    if (!existing) return null;

    const normalizedDays = week.days
      ? (week.days ?? []).map((day, dayIndex) => ({
          ...day,
          ...programDayScalarsFromInput(day, dayIndex + 1),
        }))
      : undefined;

    await prisma.programWeek.update({
      where: { id: weekId },
      data: {
        weekNumber: week.weekNumber ?? existing.weekNumber,
        target: week.target ? (week.target as object) : undefined,
        description: week.description ? (week.description as object) : undefined,
        sortOrder: week.sortOrder ?? existing.sortOrder,
      },
    });

    if (normalizedDays !== undefined) {
      await prisma.$transaction(async (tx) => {
        await tx.programDay.deleteMany({ where: { weekId } });
        for (const day of normalizedDays) {
          const createdDay = await tx.programDay.create({
            data: {
              weekId,
              ...programDayScalarsFromInput(day, day.dayNumber!),
            },
          });
          await syncDayTargetMuscles(tx, createdDay.id, day.targetMuscleValueIds);
          await syncPlannedWorkouts(tx, createdDay.id, day.plannedWorkouts);
        }
      });
    }

    return this.getById(programId).then((p) => p?.weeks.find((w) => w.id === weekId) ?? null);
  },

  async deleteWeek(programId: string, weekId: string) {
    const prisma = await getPrisma();
    const existing = await prisma.programWeek.findFirst({ where: { id: weekId, programId } });
    if (!existing) return null;
    await prisma.programWeek.delete({ where: { id: weekId } });
    return true;
  },

  async copyWeek(programId: string, weekId: string, targetWeekNumber: number) {
    const prisma = await getPrisma();
    const sourceWeek = await prisma.programWeek.findFirst({
      where: { id: weekId, programId },
      include: {
        days: {
          include: {
            plannedWorkouts: {
              include: {
                workoutTemplate: {
                  include: {
                    phases: {
                      orderBy: { sortOrder: 'asc' },
                      include: { exercises: { orderBy: { sortOrder: 'asc' } } },
                    },
                  },
                },
              },
            },
          },
        },
      },
    });
    if (!sourceWeek) return null;

    const newWeekId = await prisma.$transaction(async (tx) => {
      const createdWeek = await tx.programWeek.create({
        data: {
          programId,
          weekNumber: targetWeekNumber,
          target: toInputJsonOrNull(sourceWeek.target),
          description: toInputJsonOrNull(sourceWeek.description),
          sortOrder: sourceWeek.sortOrder,
        },
      });

      for (const day of sourceWeek.days) {
        const createdDay = await tx.programDay.create({
          data: {
            weekId: createdWeek.id,
            dayNumber: day.dayNumber,
            dayType: day.dayType,
            isRestDay: day.isRestDay,
          },
        });

        const sourceMuscles = await tx.programDayAttribute.findMany({
          where: { dayId: day.id },
          select: { attributeValueId: true },
        });
        if (sourceMuscles.length > 0) {
          await tx.programDayAttribute.createMany({
            data: sourceMuscles.map((m) => ({
              dayId: createdDay.id,
              attributeValueId: m.attributeValueId,
            })),
          });
        }

        for (const [pwIndex, plannedWorkout] of day.plannedWorkouts.entries()) {
          const newPwId = randomUUID();
          const templateId = await cloneTemplateForPlannedWorkout(tx, {
            sourceTemplateId: plannedWorkout.workoutTemplateId,
            plannedWorkoutId: newPwId,
            programId,
            name: plannedWorkout.name as Prisma.InputJsonValue,
          });
          await tx.plannedWorkout.create({
            data: {
              id: newPwId,
              dayId: createdDay.id,
              workoutTemplateId: templateId,
              name: plannedWorkout.name as object,
              sortOrder: plannedWorkout.sortOrder ?? pwIndex,
              estimatedDurationMin: plannedWorkout.estimatedDurationMin ?? undefined,
            },
          });
        }
      }

      return createdWeek.id;
    });

    return this.getById(programId).then((p) => p?.weeks.find((w) => w.id === newWeekId) ?? null);
  },

  async createDay(programId: string, weekId: string, day: ProgramDayInput) {
    const prisma = await getPrisma();
    const week = await prisma.programWeek.findFirst({ where: { id: weekId, programId } });
    if (!week) return null;

    const dayId = await prisma.$transaction(async (tx) => {
      const scalars = programDayScalarsFromInput(day, day.dayNumber ?? 1);
      const createdDay = await tx.programDay.create({
        data: {
          weekId,
          ...scalars,
        },
      });
      await syncDayTargetMuscles(tx, createdDay.id, day.targetMuscleValueIds);
      await syncPlannedWorkouts(tx, createdDay.id, day.plannedWorkouts);
      return createdDay.id;
    });

    const program = await this.getById(programId);
    return program?.weeks.flatMap((w) => w.days).find((d) => d.id === dayId) ?? null;
  },

  async updateDay(programId: string, dayId: string, day: ProgramDayInput) {
    const prisma = await getPrisma();
    const existing = await prisma.programDay.findFirst({
      where: { id: dayId, week: { programId } },
    });
    if (!existing) return null;

    const dayNumber = day.dayNumber ?? existing.dayNumber;
    await prisma.programDay.update({
      where: { id: dayId },
      data: day.dayType !== undefined
        ? programDayScalarsFromInput(day, dayNumber)
        : { dayNumber },
    });

    await prisma.$transaction(async (tx) => {
      if (day.targetMuscleValueIds !== undefined) {
        await syncDayTargetMuscles(tx, dayId, day.targetMuscleValueIds);
      }
      if (day.plannedWorkouts !== undefined) {
        await syncPlannedWorkouts(tx, dayId, day.plannedWorkouts);
      }
    });

    const program = await this.getById(programId);
    return program?.weeks.flatMap((w) => w.days).find((d) => d.id === dayId) ?? null;
  },

  async deleteDay(programId: string, dayId: string) {
    const prisma = await getPrisma();
    const existing = await prisma.programDay.findFirst({
      where: { id: dayId, week: { programId } },
    });
    if (!existing) return null;
    await prisma.programDay.delete({ where: { id: dayId } });
    return true;
  },

  async createPlannedWorkout(programId: string, dayId: string, plannedWorkout: PlannedWorkoutInput) {
    const prisma = await getPrisma();
    const day = await prisma.programDay.findFirst({ where: { id: dayId, week: { programId } } });
    if (!day) return null;

    const plannedWorkoutId = await prisma.$transaction(async (tx) =>
      createPlannedWorkoutRecord(tx, dayId, plannedWorkout, plannedWorkout.sortOrder ?? 0),
    );

    const program = await this.getById(programId);
    return (
      program?.weeks
        .flatMap((w) => w.days)
        .flatMap((d) => d.plannedWorkouts)
        .find((pw) => pw.id === plannedWorkoutId) ?? null
    );
  },

  async updatePlannedWorkout(programId: string, plannedWorkoutId: string, plannedWorkout: PlannedWorkoutInput) {
    const prisma = await getPrisma();
    const existing = await prisma.plannedWorkout.findFirst({
      where: { id: plannedWorkoutId, day: { week: { programId } } },
    });
    if (!existing) return null;

    await prisma.$transaction(async (tx) => {
      const templateId = await syncPlannedWorkoutTemplate(tx, {
        plannedWorkoutId,
        dayId: existing.dayId,
        input: plannedWorkout,
        existingTemplateId: existing.workoutTemplateId,
      });
      await tx.plannedWorkout.update({
        where: { id: plannedWorkoutId },
        data: {
          name: plannedWorkout.name !== undefined ? (plannedWorkout.name as object) : undefined,
          sortOrder: plannedWorkout.sortOrder !== undefined ? plannedWorkout.sortOrder : undefined,
          estimatedDurationMin:
            plannedWorkout.estimatedDurationMin !== undefined
              ? plannedWorkout.estimatedDurationMin
              : undefined,
          workoutTemplateId: templateId,
        },
      });
    });

    const program = await this.getById(programId);
    return (
      program?.weeks
        .flatMap((w) => w.days)
        .flatMap((d) => d.plannedWorkouts)
        .find((pw) => pw.id === plannedWorkoutId) ?? null
    );
  },

  async deletePlannedWorkout(programId: string, plannedWorkoutId: string) {
    const prisma = await getPrisma();
    const existing = await prisma.plannedWorkout.findFirst({
      where: { id: plannedWorkoutId, day: { week: { programId } } },
    });
    if (!existing) return null;
    await prisma.plannedWorkout.delete({ where: { id: plannedWorkoutId } });
    return true;
  },

  async createPlannedWorkoutItem(programId: string, plannedWorkoutId: string, item: PlannedWorkoutItemInput) {
    const prisma = await getPrisma();
    const workout = await prisma.plannedWorkout.findFirst({
      where: { id: plannedWorkoutId, day: { week: { programId } } },
    });
    if (!workout) return null;
    return prisma.plannedWorkoutItem.create({
      data: {
        plannedWorkoutId,
        type: item.type,
        exerciseId: item.exerciseId ?? undefined,
        sets: item.sets ?? undefined,
        targetReps: item.targetReps ?? undefined,
        targetDuration: item.targetDuration ?? undefined,
        restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
        weightKg: item.weightKg ?? undefined,
        weightPerSet: item.weightPerSet ?? undefined,
        notes: toInputJson(parseLocalizedText(item.notes)),
        restDurationMs: item.restDurationMs ?? undefined,
        sourceWorkoutTemplateId: item.sourceWorkoutTemplateId ?? undefined,
        sortOrder: item.sortOrder ?? 0,
        isModified: false,
      },
    });
  },

  async updatePlannedWorkoutItem(programId: string, itemId: string, item: PlannedWorkoutItemInput) {
    const prisma = await getPrisma();
    const existing = await prisma.plannedWorkoutItem.findFirst({
      where: { id: itemId, plannedWorkout: { day: { week: { programId } } } },
    });
    if (!existing) return null;

    return prisma.plannedWorkoutItem.update({
      where: { id: itemId },
      data: {
        type: item.type ?? existing.type,
        exerciseId: item.exerciseId ?? undefined,
        sets: item.sets ?? undefined,
        targetReps: item.targetReps ?? undefined,
        targetDuration: item.targetDuration ?? undefined,
        restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
        weightKg: item.weightKg ?? undefined,
        weightPerSet: item.weightPerSet ?? undefined,
        notes: toInputJson(parseLocalizedText(item.notes)),
        restDurationMs: item.restDurationMs ?? undefined,
        sourceWorkoutTemplateId: item.sourceWorkoutTemplateId ?? undefined,
        sortOrder: item.sortOrder ?? existing.sortOrder,
        isModified: true,
      },
    });
  },

  async deletePlannedWorkoutItem(programId: string, itemId: string) {
    const prisma = await getPrisma();
    const existing = await prisma.plannedWorkoutItem.findFirst({
      where: { id: itemId, plannedWorkout: { day: { week: { programId } } } },
    });
    if (!existing) return null;
    await prisma.plannedWorkoutItem.delete({ where: { id: itemId } });
    return true;
  },

  async importWorkoutTemplateToPlannedWorkout(programId: string, plannedWorkoutId: string, workoutId: string) {
    const prisma = await getPrisma();
    const plannedWorkout = await prisma.plannedWorkout.findFirst({
      where: { id: plannedWorkoutId, day: { week: { programId } } },
      select: { id: true, dayId: true, name: true },
    });
    if (!plannedWorkout) return null;

    const source = await prisma.workoutTemplate.findFirst({
      where: { id: workoutId, deletedAt: null },
    });
    if (!source) return null;

    await prisma.$transaction(async (tx) => {
      const templateId = await cloneTemplateForPlannedWorkout(tx, {
        sourceTemplateId: workoutId,
        plannedWorkoutId,
        programId,
        name: plannedWorkout.name as Prisma.InputJsonValue,
      });
      await tx.plannedWorkout.update({
        where: { id: plannedWorkoutId },
        data: { workoutTemplateId: templateId },
      });
    });

    const program = await this.getById(programId);
    return (
      program?.weeks
        .flatMap((w) => w.days)
        .flatMap((d) => d.plannedWorkouts)
        .find((pw) => pw.id === plannedWorkoutId) ?? null
    );
  },

  async updateUserProgram(userProgramId: string, userId: string, data: UpdateUserProgramInput) {
    const prisma = await getPrisma();

    // Merge customizations instead of replacing them
    // This way customizing day 1 won't erase day 2's customizations
    let mergedCustomizations: Prisma.InputJsonValue | undefined = undefined;
    if (data.customizations) {
      // Validate customization keys match expected format: "day_{weekNumber}_{dayNumber}"
      const customizationKeyRegex = /^day_\d+_\d+$/;
      for (const key of Object.keys(data.customizations)) {
        if (!customizationKeyRegex.test(key)) {
          throw new Error(
            `Invalid customization key: "${key}". Expected format: "day_{weekNumber}_{dayNumber}"`
          );
        }
        const value = data.customizations[key];
        if (!Array.isArray(value)) {
          throw new Error(
            `Invalid customization value for key "${key}". Expected array of planned workouts.`
          );
        }
      }

      const existing = await prisma.userProgram.findFirst({
        where: { id: userProgramId, userId },
        select: { customizations: true },
      });
      const existingCustomizations =
        (existing?.customizations as Record<string, unknown>) || {};
      mergedCustomizations = {
        ...existingCustomizations,
        ...data.customizations,
      } as Prisma.InputJsonValue;
    }

    return prisma.userProgram.updateMany({
      where: { id: userProgramId, userId },
      data: {
        name: data.name ? (data.name as object) : undefined,
        customizations: mergedCustomizations,
        isActive: data.isActive ?? undefined,
        ...(mergedCustomizations !== undefined
          ? { customizationsUpdatedAt: new Date() }
          : {}),
      },
    });
  },

  async getTodayPlan(userId: string): Promise<TodayPlanResponse | null> {
    const prisma = await getPrisma();
    const userProgram = await prisma.userProgram.findFirst({
      where: { userId, isActive: true },
      include: {
        program: { include: programFullInclude },
        progress: true,
      },
    });
    if (!userProgram || !userProgram.program) return null;

    const program = userProgram.program;
    const [profile, lastAt] = await Promise.all([
      prisma.trainingProfile.findUnique({
        where: { userId },
        select: { trainingWeekdays: true },
      }),
      program.id ? getLastPlannedWorkoutCompletedAt(userId, program.id) : Promise.resolve(null),
    ]);
    const trainingWeekdays =
      profile?.trainingWeekdays && profile.trainingWeekdays.length > 0
        ? profile.trainingWeekdays
        : null;

    const meta = resolveTrainingPositionMeta(program.weeks, userProgram.progress ?? [], {
      lastWorkoutCompletedAt: lastAt,
      trainingWeekdays,
      durationWeeks: program.durationWeeks,
    });
    const position = meta.position;
    const catchUpSuggestion = buildCatchUpSuggestionFromMeta(meta);

    const weekNumber = position.targetWeekNumber;
    const dayNumber = position.targetDayNumber;
    const isProgramComplete = position.isProgramComplete;

    const week = program.weeks.find((w) => w.weekNumber === weekNumber);
    const day = week?.days.find((d) => d.dayNumber === dayNumber);

    // Build progress map for the response
    const progressMap: Record<string, string> = {};
    if (userProgram.progress) {
      for (const p of userProgram.progress) {
        const key = `${p.weekNumber}_${p.dayNumber}${p.plannedWorkoutId ? '_' + p.plannedWorkoutId : ''}`;
        progressMap[key] = p.status;
      }
    }

    const isUserOffDay = !position.isTrainingDay;
    const isTemplateRest =
      day &&
      (day.isRestDay || day.dayType === 'rest' || day.dayType === 'active_recovery');
    const showSessions = Boolean(day) && !isTemplateRest && !isUserOffDay && !isProgramComplete;

    return {
      userProgramId: userProgram.id,
      programId: userProgram.programId ?? undefined,
      weekNumber,
      dayNumber,
      date: new Date().toISOString(),
      isProgramComplete,
      progress: progressMap,
      isTrainingDay: position.isTrainingDay ?? true,
      trainingWeekdays: trainingWeekdays ?? [],
      catchUpSuggestion,
      plannedWorkouts:
        showSessions && day
          ? day.plannedWorkouts.map((plannedWorkout) =>
              exportPlannedWorkout(plannedWorkout as PlannedWorkoutWithTemplate),
            )
          : [],
    };
  },

  buildProgramExport(program: Awaited<ReturnType<typeof this.getById>>): ProgramExport | null {
    if (!program) return null;

    return {
      id: program.id,
      slug: program.slug,
      name: parseLocalizedText(program.name) || { ar: '', en: '' },
      description: parseLocalizedText(program.description),
      coverImageUrl: program.coverImageUrl ?? undefined,
      durationWeeks: program.durationWeeks,
      levelMinId: program.levelMinId,
      levelMaxId: program.levelMaxId,
      levelMin: exportLevel(program.levelMin),
      levelMax: exportLevel(program.levelMax),
      levelRangeMin: program.levelMin?.number ?? null,
      levelRangeMax: program.levelMax?.number ?? null,
      tags: (program.tags as string[]) || undefined,
      weeklyWorkoutTarget: program.weeklyWorkoutTarget ?? undefined,
      estimatedWorkoutMinutes: program.estimatedWorkoutMinutes ?? undefined,
      isFeatured: program.isFeatured ?? undefined,
      weeks: program.weeks.map((week) => ({
        weekNumber: week.weekNumber,
        target: parseLocalizedText(week.target),
        description: parseLocalizedText(week.description),
        days: week.days.map((day) => ({
          dayNumber: day.dayNumber,
          dayType: normalizeProgramDayType(day.dayType),
          isRestDay: day.isRestDay,
          targetMuscles: (day.targetMuscles ?? []).map((link) => ({
            code: link.attributeValue.code,
            name: parseLocalizedText(link.attributeValue.name) || { ar: '', en: link.attributeValue.code },
          })),
          plannedWorkouts: day.plannedWorkouts.map((plannedWorkout) =>
            exportPlannedWorkout(plannedWorkout as PlannedWorkoutWithTemplate),
          ),
        })),
      })),
      updatedAt: program.updatedAt.toISOString(),
    };
  },

  buildProgramPreview(program: Awaited<ReturnType<typeof this.getById>>): ProgramPreviewExport | null {
    const full = this.buildProgramExport(program);
    if (!full) return null;
    const firstWeeks = full.weeks.filter((w) => w.weekNumber === 1);
    let exerciseCount = 0;
    for (const w of firstWeeks) {
      for (const d of w.days) {
        for (const s of d.plannedWorkouts) {
          for (const it of s.items) {
            if (it.type === 'exercise' && !it.deletedExercise) exerciseCount++;
          }
        }
      }
    }
    return {
      id: full.id,
      slug: full.slug,
      name: full.name,
      description: full.description,
      coverImageUrl: full.coverImageUrl,
      durationWeeks: full.durationWeeks,
      levelMinId: full.levelMinId,
      levelMaxId: full.levelMaxId,
      levelRangeMin: full.levelRangeMin,
      levelRangeMax: full.levelRangeMax,
      totalExercisesInFirstWeek: exerciseCount,
      muscleGroups: [],
      weeks: firstWeeks,
      updatedAt: full.updatedAt,
    };
  },

  async getPublishedForMobile(updatedAfter?: Date | null): Promise<ProgramExport[]> {
    const prisma = await getPrisma();
    const where: Record<string, unknown> = {
      isPublished: true,
      deletedAt: null,
    };
    if (updatedAfter) {
      where.updatedAt = { gt: updatedAfter };
      // H26 / P2.2: light id probe first — skip deep include when delta is empty.
      const changedIds = await prisma.program.findMany({
        where,
        select: { id: true },
        orderBy: { updatedAt: 'desc' },
      });
      if (changedIds.length === 0) {
        return [];
      }
      const programs = await prisma.program.findMany({
        where: { id: { in: changedIds.map((row) => row.id) } },
        include: programFullInclude,
        orderBy: { updatedAt: 'desc' },
      });
      return programs
        .map((program) => this.buildProgramExport(program))
        .filter((program): program is ProgramExport => program !== null);
    }
    const programs = await prisma.program.findMany({
      where,
      include: programFullInclude,
      orderBy: { updatedAt: 'desc' },
    });
    return programs
      .map((program) => this.buildProgramExport(program))
      .filter((program): program is ProgramExport => program !== null);
  },

  /**
   * Lightweight enrollment list for `GET /api/mobile/user-programs` (P2.1).
   * Same shape as `userPrograms` in `/mobile/sync` — optional delta via `updatedAfter`.
   */
  async listUserProgramsForMobile(
    userId: string,
    updatedAfter?: Date,
  ): Promise<import('@/modules/mobile-sync/mobile-sync.types').UserProgramExport[]> {
    const prisma = await getPrisma();
    const where: Prisma.UserProgramWhereInput = { userId };
    if (updatedAfter) {
      where.updatedAt = { gt: updatedAfter };
    }

    const [userProgramRows, trainingProfile] = await Promise.all([
      prisma.userProgram.findMany({
        where,
        orderBy: { updatedAt: 'desc' },
      }),
      prisma.trainingProfile.findUnique({
        where: { userId },
        select: { trainingWeekdays: true },
      }),
    ]);
    const trainingWeekdays = trainingProfile?.trainingWeekdays ?? [];

    return userProgramRows.map((row) => ({
      id: row.id,
      programId: row.programId,
      name: row.name
        ? {
            ar: typeof (row.name as Record<string, unknown>).ar === 'string'
              ? ((row.name as Record<string, unknown>).ar as string)
              : '',
            en: typeof (row.name as Record<string, unknown>).en === 'string'
              ? ((row.name as Record<string, unknown>).en as string)
              : '',
          }
        : undefined,
      startDate: row.startDate.toISOString(),
      isActive: row.isActive,
      customizations: (row.customizations as Record<string, unknown>) || null,
      updatedAt: row.updatedAt.toISOString(),
      customizationsUpdatedAt: row.customizationsUpdatedAt?.toISOString() ?? null,
      trainingWeekdays,
    }));
  },
};
