/**
 * Programs Service
 * =================
 * Service for Program CRUD operations.
 */

import { getPrisma } from '@/lib/prisma/client';
import {
  Prisma,
  type Program,
  ProgramAttributeMode,
  type PrismaClient,
  type WorkoutBlockRole,
} from '@prisma/client';
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
  ProgramPhaseInput,
} from './programs.types';
import { validatePhaseStructure } from './programs.types';

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

const programFullInclude = {
  phases: {
    orderBy: { sortOrder: 'asc' as const },
  },
  programAttributes: {
    orderBy: { createdAt: 'asc' as const },
    include: {
      attributeValue: {
        include: { attribute: true },
      },
    },
  },
  weeks: {
    orderBy: [
      { sortOrder: 'asc' as const },
      { weekNumber: 'asc' as const },
    ],
    include: {
      days: {
        orderBy: { dayNumber: 'asc' as const },
        include: { plannedWorkouts: {
            orderBy: { sortOrder: 'asc' as const },
            include: {
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

function buildWeeksCreate(weeks?: ProgramWeekInput[]) {
  if (!weeks || weeks.length === 0) return undefined;
  return {
    create: weeks.map((week, weekIndex) => ({
      weekNumber: week.weekNumber,
      name: (week.name as object) || undefined,
      description: (week.description as object) || undefined,
      sortOrder: week.sortOrder ?? weekIndex,
      weekType: week.weekType ?? 'NORMAL',
      days: week.days
        ? {
            create: week.days.map((day, dayIndex) => ({
              dayNumber: day.dayNumber,
              isRestDay: day.isRestDay ?? false,
              name: (day.name as object) || undefined,
              dayFocus: day.dayFocus ?? undefined,
              plannedWorkouts: day.plannedWorkouts
                ? {
                    create: day.plannedWorkouts.map((plannedWorkout, plannedWorkoutIndex) => ({
                      ...prismaPlannedWorkoutScalars(plannedWorkout, plannedWorkoutIndex),
                      items: {
                        create: (plannedWorkout.items ?? []).map((item, ii) =>
                          plannedWorkoutItemScalarFromInput(item, item.sortOrder ?? ii),
                        ),
                      },
                    })),
                  }
                : undefined,
            })),
          }
        : undefined,
    })),
  };
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

const DEFAULT_WORKOUT_BLOCK_ROLE = 'MAIN' as WorkoutBlockRole;

function prismaPlannedWorkoutScalars(plannedWorkout: PlannedWorkoutInput, workoutIndex: number) {
  return {
    name: plannedWorkout.name as object,
    sortOrder: plannedWorkout.sortOrder ?? workoutIndex,
    estimatedDurationMin: plannedWorkout.estimatedDurationMin ?? undefined,
    role: (plannedWorkout.role ?? DEFAULT_WORKOUT_BLOCK_ROLE) as WorkoutBlockRole,
  };
}

function expandPhasesToWeeks(phases: ProgramPhaseInput[]): ProgramWeekInput[] {
  const sorted = [...phases].sort(
    (a, b) => (a.sortOrder ?? a.startWeek) - (b.sortOrder ?? b.startWeek),
  );
  const weeks: ProgramWeekInput[] = [];
  for (const phase of sorted) {
    const patternDays = phase.weeklyPattern?.days ?? [];
    for (let w = phase.startWeek; w <= phase.endWeek; w++) {
      weeks.push({
        weekNumber: w,
        name: phase.name,
        description: phase.description,
        weekType: phase.weekType ?? 'NORMAL',
        sortOrder: w - 1,
        days: patternDays.map((d) => ({
          dayNumber: d.dayNumber,
          isRestDay: d.isRestDay ?? false,
          name: d.name,
          dayFocus: d.dayFocus,
          plannedWorkouts: d.plannedWorkouts,
        })),
      });
    }
  }
  return weeks;
}

async function syncProgramPhasesStructure(
  tx: Prisma.TransactionClient,
  programId: string,
  phases: ProgramPhaseInput[],
) {
  await tx.programPhase.deleteMany({ where: { programId } });
  for (let i = 0; i < phases.length; i++) {
    const p = phases[i]!;
    await tx.programPhase.create({
      data: {
        programId,
        name: p.name as object,
        description: (p.description as object) || undefined,
        weekType: p.weekType ?? 'NORMAL',
        startWeek: p.startWeek,
        endWeek: p.endWeek,
        sortOrder: p.sortOrder ?? i,
        weeklyPattern: p.weeklyPattern
          ? (p.weeklyPattern as unknown as Prisma.InputJsonValue)
          : undefined,
      },
    });
  }
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
    select: { id: true },
  });
  const existingIds = new Set(existing.map((e) => e.id));
  const payloadIds = new Set(plannedWorkouts.map((s) => s.id).filter(Boolean) as string[]);

  const toRemove = existing.filter((e) => !payloadIds.has(e.id)).map((e) => e.id);
  if (toRemove.length > 0) {
    await tx.plannedWorkout.deleteMany({ where: { id: { in: toRemove } } });
  }

  for (let index = 0; index < plannedWorkouts.length; index++) {
    const plannedWorkout = plannedWorkouts[index]!;
    const sortOrder = plannedWorkout.sortOrder ?? index;
    if (plannedWorkout.id) {
      if (!existingIds.has(plannedWorkout.id)) {
        throw new Error(`Invalid planned workout id for day ${dayId}: ${plannedWorkout.id}`);
      }
      await tx.plannedWorkout.update({
        where: { id: plannedWorkout.id },
        data: {
          ...prismaPlannedWorkoutScalars(plannedWorkout, sortOrder),
        },
      });
      await syncPlannedWorkoutItems(tx, plannedWorkout.id, plannedWorkout.items);
    } else {
      await tx.plannedWorkout.create({
        data: {
          dayId,
          ...prismaPlannedWorkoutScalars(plannedWorkout, sortOrder),
          items: {
            create: (plannedWorkout.items ?? []).map((item, ii) =>
              plannedWorkoutItemScalarFromInput(item, item.sortOrder ?? ii),
            ),
          },
        },
      });
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
        data: {
          dayNumber: day.dayNumber,
          isRestDay: day.isRestDay ?? false,
          name: (day.name as object) || undefined,
          dayFocus: day.dayFocus ?? undefined,
        },
      });
      await syncPlannedWorkouts(tx, day.id, day.plannedWorkouts);
    } else {
      await tx.programDay.create({
        data: {
          weekId,
          dayNumber: day.dayNumber,
          isRestDay: day.isRestDay ?? false,
          name: (day.name as object) || undefined,
          dayFocus: day.dayFocus ?? undefined,
          plannedWorkouts: { create: (day.plannedWorkouts ?? []).map((plannedWorkout, pwIndex) => ({
              ...prismaPlannedWorkoutScalars(plannedWorkout, pwIndex),
              items: {
                create: (plannedWorkout.items ?? []).map((item, ii) =>
                  plannedWorkoutItemScalarFromInput(item, item.sortOrder ?? ii),
                ),
              },
            })),
          },
        },
      });
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
  const existingRows = await tx.programWeek.findMany({
    where: { programId },
    select: { id: true },
  });
  const validWeekIds = new Set(existingRows.map((w) => w.id));

  for (const week of weeksIn) {
    if (week.id && !validWeekIds.has(week.id)) {
      throw new Error(`Invalid program week id for this program: ${week.id}`);
    }
  }

  const payloadWeekIds = new Set(weeksIn.map((w) => w.id).filter(Boolean) as string[]);
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

  const existingWeeksInPayload = weeksIn.filter((w) => w.id && validAfterDelete.has(w.id));
  for (const week of existingWeeksInPayload) {
    await tx.programWeek.update({
      where: { id: week.id! },
      data: {
        weekNumber: week.weekNumber + WEEK_NUMBER_TEMP_OFFSET,
        sortOrder: week.sortOrder ?? 0,
      },
    });
  }

  for (const week of weeksIn) {
    const sortOrder = week.sortOrder ?? 0;
    if (week.id && validAfterDelete.has(week.id)) {
      await tx.programWeek.update({
        where: { id: week.id },
        data: {
          weekNumber: week.weekNumber,
          name: (week.name as object) || undefined,
          description: (week.description as object) || undefined,
          sortOrder,
          weekType: week.weekType ?? 'NORMAL',
        },
      });
      await syncDays(tx, week.id, week.days);
    } else {
      await tx.programWeek.create({
        data: {
          programId,
          weekNumber: week.weekNumber,
          name: (week.name as object) || undefined,
          description: (week.description as object) || undefined,
          sortOrder,
          weekType: week.weekType ?? 'NORMAL',
          days: {
            create: (week.days ?? []).map((day, di) => ({
              dayNumber: day.dayNumber,
              isRestDay: day.isRestDay ?? false,
              name: (day.name as object) || undefined,
              dayFocus: day.dayFocus ?? undefined,
              plannedWorkouts: { create: (day.plannedWorkouts ?? []).map((plannedWorkout, pwIndex) => ({
                  ...prismaPlannedWorkoutScalars(plannedWorkout, pwIndex),
                  items: {
                    create: (plannedWorkout.items ?? []).map((item, ii) =>
                      plannedWorkoutItemScalarFromInput(item, item.sortOrder ?? ii),
                    ),
                  },
                })),
              },
            })),
          },
        },
      });
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

export const programService = {
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

    let effectiveWeeks = data.weeks;
    if (data.phases && data.phases.length > 0) {
      const phaseValidation = validatePhaseStructure(data.phases, data.durationWeeks);
      if (!phaseValidation.valid) {
        throw new Error(`Invalid phases: ${phaseValidation.errors.join('; ')}`);
      }
      effectiveWeeks = expandPhasesToWeeks(data.phases);
    }

    const programId = await prisma.$transaction(async (tx) => {
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
          levelRangeMin: data.levelRangeMin ?? 1,
          levelRangeMax: data.levelRangeMax ?? 5,
          prescriptionPriority: data.prescriptionPriority ?? 100,
          prerequisiteProgramId: data.prerequisiteProgramId ?? undefined,
          nextProgramId: data.nextProgramId ?? undefined,
          weeks: buildWeeksCreate(effectiveWeeks),
        },
      });

      if (data.phases && data.phases.length > 0) {
        await syncProgramPhasesStructure(tx, program.id, data.phases);
      }

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
    if (data.levelRangeMin !== undefined) updateData.levelRangeMin = data.levelRangeMin;
    if (data.levelRangeMax !== undefined) updateData.levelRangeMax = data.levelRangeMax;
    if (data.prescriptionPriority !== undefined) updateData.prescriptionPriority = data.prescriptionPriority;
    if (data.prerequisiteProgramId !== undefined) updateData.prerequisiteProgramId = data.prerequisiteProgramId;
    if (data.nextProgramId !== undefined) updateData.nextProgramId = data.nextProgramId;

    await prisma.$transaction(async (tx) => {
      await tx.program.update({
        where: { id },
        data: updateData as Prisma.ProgramUpdateInput,
      });

      if (data.phases !== undefined && data.phases.length > 0) {
        const prog = await tx.program.findUnique({
          where: { id },
          select: { durationWeeks: true },
        });
        const durationWeeks = data.durationWeeks ?? prog?.durationWeeks;
        if (!durationWeeks) {
          throw new Error('durationWeeks required to validate program phases');
        }
        const phaseValidation = validatePhaseStructure(data.phases, durationWeeks);
        if (!phaseValidation.valid) {
          throw new Error(`Invalid phases: ${phaseValidation.errors.join('; ')}`);
        }
        const expandedWeeks = expandPhasesToWeeks(data.phases);
        await syncProgramPhasesStructure(tx, id, data.phases);
        await syncProgramWeeksStructure(tx, id, expandedWeeks);
      } else if (data.weeks !== undefined) {
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
      weekType: week.weekType,
      name: parseLocalizedText(week.name),
      description: parseLocalizedText(week.description),
      sortOrder: week.sortOrder ?? weekIndex,
      days: week.days.map((day) => ({
        dayNumber: day.dayNumber,
        isRestDay: day.isRestDay,
        name: parseLocalizedText(day.name),
        dayFocus: day.dayFocus ?? undefined,
        plannedWorkouts: day.plannedWorkouts.map((plannedWorkout, plannedWorkoutIndex) => ({
          name: parseLocalizedText(plannedWorkout.name) || { ar: '', en: '' },
          sortOrder: plannedWorkout.sortOrder ?? plannedWorkoutIndex,
          role: plannedWorkout.role ? String(plannedWorkout.role) : 'MAIN',
          estimatedDurationMin: plannedWorkout.estimatedDurationMin ?? undefined,
          items: plannedWorkout.items.map((item, itemIndex) => ({
            type: item.type as 'exercise' | 'rest',
            exerciseId: item.exerciseId ?? undefined,
            sets: item.sets ?? undefined,
            targetReps: item.targetReps ?? undefined,
            targetDuration: item.targetDuration ?? undefined,
            restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
            weightKg: item.weightKg ?? undefined,
            weightPerSet: (item.weightPerSet as number[]) || undefined,
            notes: toInputJson(parseLocalizedText(item.notes)),
            restDurationMs: item.restDurationMs ?? undefined,
            sourceWorkoutTemplateId: item.sourceWorkoutTemplateId ?? undefined,
            sortOrder: item.sortOrder ?? itemIndex,
          })),
        })),
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
        levelRangeMin: original.levelRangeMin,
        levelRangeMax: original.levelRangeMax,
        prescriptionPriority: original.prescriptionPriority,
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
    return prisma.programWeek.create({
      data: {
        programId,
        weekNumber: week.weekNumber,
        weekType: week.weekType ?? 'NORMAL',
        name: (week.name as object) || undefined,
        description: (week.description as object) || undefined,
        sortOrder: week.sortOrder ?? 0,
        days: buildWeeksCreate([week])?.create?.[0]?.days,
      },
      include: { days: { include: { plannedWorkouts: { include: { items: true } } } } },
    });
  },

  async updateWeek(programId: string, weekId: string, week: ProgramWeekInput) {
    const prisma = await getPrisma();
    const existing = await prisma.programWeek.findFirst({ where: { id: weekId, programId } });
    if (!existing) return null;

    await prisma.programWeek.update({
      where: { id: weekId },
      data: {
        weekNumber: week.weekNumber ?? existing.weekNumber,
        weekType: week.weekType ?? existing.weekType,
        name: week.name ? (week.name as object) : undefined,
        description: week.description ? (week.description as object) : undefined,
        sortOrder: week.sortOrder ?? existing.sortOrder,
      },
    });

    if (week.days !== undefined) {
      await prisma.programDay.deleteMany({ where: { weekId } });
      if (week.days.length > 0) {
        await prisma.programWeek.update({
          where: { id: weekId },
          data: {
            days: {
              create: week.days.map((day, dayIndex) => ({
                dayNumber: day.dayNumber,
                isRestDay: day.isRestDay ?? false,
                name: (day.name as object) || undefined,
                plannedWorkouts: day.plannedWorkouts
                  ? {
                      create: day.plannedWorkouts.map((plannedWorkout, plannedWorkoutIndex) => ({
                        ...prismaPlannedWorkoutScalars(plannedWorkout, plannedWorkoutIndex),
                        items: {
                          create: (plannedWorkout.items ?? []).map((item, ii) =>
                            plannedWorkoutItemScalarFromInput(item, item.sortOrder ?? ii),
                          ),
                        },
                      })),
                    }
                  : undefined,
              })),
            },
          },
        });
      }
    }

    return prisma.programWeek.findFirst({
      where: { id: weekId },
      include: { days: { include: { plannedWorkouts: { include: { items: true } } } } },
    });
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
      include: { days: { include: { plannedWorkouts: { include: { items: true } } } } },
    });
    if (!sourceWeek) return null;

    return prisma.programWeek.create({
      data: {
        programId,
        weekNumber: targetWeekNumber,
        weekType: sourceWeek.weekType,
        name: toInputJsonOrNull(sourceWeek.name),
        description: toInputJsonOrNull(sourceWeek.description),
        sortOrder: sourceWeek.sortOrder,
        days: {
          create: sourceWeek.days.map((day) => ({
            dayNumber: day.dayNumber,
            isRestDay: day.isRestDay,
            name: toInputJsonOrNull(day.name),
            dayFocus: day.dayFocus ?? undefined,
            plannedWorkouts: { create: day.plannedWorkouts.map((plannedWorkout, pwIndex) => ({
                name: toInputJsonOrNull(plannedWorkout.name),
                sortOrder: plannedWorkout.sortOrder,
                estimatedDurationMin: plannedWorkout.estimatedDurationMin ?? undefined,
                role: plannedWorkout.role ?? DEFAULT_WORKOUT_BLOCK_ROLE,
                items: {
                  create: plannedWorkout.items.map((item, ii) =>
                    plannedWorkoutItemScalarFromInput(
                      {
                        type: item.type as 'exercise' | 'rest',
                        exerciseId: item.exerciseId ?? undefined,
                        sets: item.sets ?? undefined,
                        targetReps: item.targetReps ?? undefined,
                        targetDuration: item.targetDuration ?? undefined,
                        restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
                        weightKg: item.weightKg ?? undefined,
                        weightPerSet: (item.weightPerSet as number[]) ?? undefined,
                        notes: parseLocalizedText(item.notes),
                        restDurationMs: item.restDurationMs ?? undefined,
                        sourceWorkoutTemplateId: item.sourceWorkoutTemplateId ?? undefined,
                        sortOrder: item.sortOrder ?? ii,
                      },
                      item.sortOrder ?? ii,
                    ),
                  ),
                },
              })),
            },
          })),
        },
      },
      include: { days: { include: { plannedWorkouts: { include: { items: true } } } } },
    });
  },

  async createDay(programId: string, weekId: string, day: ProgramDayInput) {
    const prisma = await getPrisma();
    const week = await prisma.programWeek.findFirst({ where: { id: weekId, programId } });
    if (!week) return null;
    return prisma.programDay.create({
      data: {
        weekId,
        dayNumber: day.dayNumber,
        isRestDay: day.isRestDay ?? false,
        name: (day.name as object) || undefined,
        dayFocus: day.dayFocus ?? undefined,
        plannedWorkouts: day.plannedWorkouts
          ? {
              create: day.plannedWorkouts.map((plannedWorkout, plannedWorkoutIndex) => ({
                ...prismaPlannedWorkoutScalars(plannedWorkout, plannedWorkoutIndex),
                items: buildPlannedWorkoutItems(plannedWorkout.items),
              })),
            }
          : undefined,
      },
      include: { plannedWorkouts: { include: { items: true } } },
    });
  },

  async updateDay(programId: string, dayId: string, day: ProgramDayInput) {
    const prisma = await getPrisma();
    const existing = await prisma.programDay.findFirst({
      where: { id: dayId, week: { programId } },
    });
    if (!existing) return null;

    await prisma.programDay.update({
      where: { id: dayId },
      data: {
        dayNumber: day.dayNumber ?? existing.dayNumber,
        isRestDay: day.isRestDay ?? existing.isRestDay,
        name: day.name ? (day.name as object) : undefined,
        dayFocus: day.dayFocus !== undefined ? day.dayFocus : undefined,
      },
    });

    if (day.plannedWorkouts !== undefined) {
      await prisma.plannedWorkout.deleteMany({ where: { dayId } });
      if (day.plannedWorkouts.length > 0) {
        await prisma.programDay.update({
          where: { id: dayId },
          data: {
            plannedWorkouts: { create: day.plannedWorkouts.map((plannedWorkout, plannedWorkoutIndex) => ({
                ...prismaPlannedWorkoutScalars(plannedWorkout, plannedWorkoutIndex),
                items: buildPlannedWorkoutItems(plannedWorkout.items),
              })),
            },
          },
        });
      }
    }

    return prisma.programDay.findFirst({
      where: { id: dayId },
      include: { plannedWorkouts: { include: { items: true } } },
    });
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
    return prisma.plannedWorkout.create({
      data: {
        dayId,
        ...prismaPlannedWorkoutScalars(plannedWorkout, plannedWorkout.sortOrder ?? 0),
        items: buildPlannedWorkoutItems(plannedWorkout.items),
      },
      include: { items: true },
    });
  },

  async updatePlannedWorkout(programId: string, plannedWorkoutId: string, plannedWorkout: PlannedWorkoutInput) {
    const prisma = await getPrisma();
    const existing = await prisma.plannedWorkout.findFirst({
      where: { id: plannedWorkoutId, day: { week: { programId } } },
    });
    if (!existing) return null;

    await prisma.plannedWorkout.update({
      where: { id: plannedWorkoutId },
      data: {
        name: plannedWorkout.name !== undefined ? (plannedWorkout.name as object) : undefined,
        sortOrder: plannedWorkout.sortOrder !== undefined ? plannedWorkout.sortOrder : undefined,
        estimatedDurationMin:
          plannedWorkout.estimatedDurationMin !== undefined ? plannedWorkout.estimatedDurationMin : undefined,
        role:
          plannedWorkout.role !== undefined ? (plannedWorkout.role as WorkoutBlockRole) : undefined,
      },
    });

    if (plannedWorkout.items !== undefined) {
      await prisma.plannedWorkoutItem.deleteMany({ where: { plannedWorkoutId } });
      if (plannedWorkout.items.length > 0) {
        await prisma.plannedWorkout.update({
          where: { id: plannedWorkoutId },
          data: {
            items: buildPlannedWorkoutItems(plannedWorkout.items),
          },
        });
      }
    }

    return prisma.plannedWorkout.findFirst({
      where: { id: plannedWorkoutId },
      include: { items: true },
    });
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
    });
    if (!plannedWorkout) return null;

    const workout = await prisma.workoutTemplate.findFirst({
      where: { id: workoutId, deletedAt: null },
      include: { exercises: { orderBy: { sortOrder: 'asc' } } },
    });
    if (!workout) return null;

    const existingCount = await prisma.plannedWorkoutItem.count({ where: { plannedWorkoutId } });
    let sortIndex = existingCount;
    const itemsToCreate: Prisma.PlannedWorkoutItemCreateManyInput[] = [];

    workout.exercises.forEach((exercise) => {
      itemsToCreate.push({
        plannedWorkoutId,
        type: 'exercise',
        exerciseId: exercise.exerciseId,
        sets: exercise.sets,
        targetReps: exercise.targetReps ?? undefined,
        targetDuration: exercise.targetDuration ?? undefined,
        restBetweenSetsMs: exercise.restBetweenSetsMs,
        weightKg: exercise.weightKg ?? undefined,
        weightPerSet: exercise.weightPerSet ?? undefined,
        notes: exercise.notes ?? undefined,
        sourceWorkoutTemplateId: workout.id,
        sortOrder: sortIndex++,
        isModified: false,
      });

      if (exercise.restAfterExerciseMs && exercise.restAfterExerciseMs > 0) {
        itemsToCreate.push({
          plannedWorkoutId,
          type: 'rest',
          restDurationMs: exercise.restAfterExerciseMs,
          sourceWorkoutTemplateId: workout.id,
          sortOrder: sortIndex++,
          isModified: false,
        });
      }
    });

    if (itemsToCreate.length > 0) {
      await prisma.plannedWorkoutItem.createMany({ data: itemsToCreate });
    }

    return prisma.plannedWorkout.findFirst({
      where: { id: plannedWorkoutId },
      include: { items: true },
    });
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
          ? day.plannedWorkouts.map((plannedWorkout) => ({
              id: plannedWorkout.id,
              name: parseLocalizedText(plannedWorkout.name) || { ar: '', en: '' },
              sortOrder: plannedWorkout.sortOrder,
              role: String(plannedWorkout.role),
              estimatedDurationMin: plannedWorkout.estimatedDurationMin ?? undefined,
              items: plannedWorkout.items.map((item) => ({
                type: item.type as 'exercise' | 'rest',
                serverItemId: item.id,
                exerciseSlug: item.exercise?.slug ?? undefined,
                deletedExercise:
                  item.type === 'exercise' && !item.exercise ? true : undefined,
                intent: item.exercise?.intent ?? undefined,
                coachingNotes: item.exercise?.coachingNotes ?? undefined,
                sets: item.sets ?? undefined,
                targetReps: item.targetReps ?? undefined,
                targetDuration: item.targetDuration ?? undefined,
                restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
                weightKg: item.weightKg ?? undefined,
                weightPerSet: (item.weightPerSet as number[]) || undefined,
                notes: parseLocalizedText(item.notes),
                restDurationMs: item.restDurationMs ?? undefined,
                sortOrder: item.sortOrder,
              })),
            }))
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
      levelRangeMin: program.levelRangeMin,
      levelRangeMax: program.levelRangeMax,
      tags: (program.tags as string[]) || undefined,
      weeklyWorkoutTarget: program.weeklyWorkoutTarget ?? undefined,
      estimatedWorkoutMinutes: program.estimatedWorkoutMinutes ?? undefined,
      isFeatured: program.isFeatured ?? undefined,
      weeks: program.weeks.map((week) => ({
        weekNumber: week.weekNumber,
        name: parseLocalizedText(week.name),
        description: parseLocalizedText(week.description),
        days: week.days.map((day) => ({
          dayNumber: day.dayNumber,
          isRestDay: day.isRestDay,
          name: parseLocalizedText(day.name),
          plannedWorkouts: day.plannedWorkouts.map((plannedWorkout) => ({
            id: plannedWorkout.id,
            name: parseLocalizedText(plannedWorkout.name) || { ar: '', en: '' },
            sortOrder: plannedWorkout.sortOrder,
            role: String(plannedWorkout.role),
            estimatedDurationMin: plannedWorkout.estimatedDurationMin ?? undefined,
            items: plannedWorkout.items.map((item) => ({
              type: item.type as 'exercise' | 'rest',
              serverItemId: item.id,
              exerciseSlug: item.exercise?.slug ?? undefined,
              deletedExercise:
                item.type === 'exercise' && !item.exercise ? true : undefined,
              intent: item.exercise?.intent ?? undefined,
              coachingNotes: item.exercise?.coachingNotes ?? undefined,
              sets: item.sets ?? undefined,
              targetReps: item.targetReps ?? undefined,
              targetDuration: item.targetDuration ?? undefined,
              restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
              weightKg: item.weightKg ?? undefined,
              weightPerSet: (item.weightPerSet as number[]) || undefined,
              notes: parseLocalizedText(item.notes),
              restDurationMs: item.restDurationMs ?? undefined,
              sortOrder: item.sortOrder,
            })),
          })),
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
};
