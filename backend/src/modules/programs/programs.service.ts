/**
 * Programs Service
 * =================
 * Service for Program CRUD operations.
 */

import { getPrisma } from '@/lib/prisma/client';
import { PROGRAM_DOMAIN_VALUE_CODE, TRAINING_GOAL_VALUE_CODE } from '@/lib/program-attribute-codes';
import { matchingColumnsFromProgramAttributeRows } from '@/lib/program-attribute-column-sync';
import {
  Prisma,
  type Program,
  ProgramAttributeMode,
  ProgramDomain,
  TrainingGoal,
  type PrismaClient,
} from '@prisma/client';
import { resolveCurrentProgramDay } from '@/modules/active-plan/plan-position';
import { getAutoAssignmentReadiness } from './program-assignment';
import { validateCalendarProgramStructure } from './calendar-program-structure';
import { computeCatchUpSuggestion } from './program-catchup';
import type {
  CreateProgramInput,
  ProgramExport,
  ProgramPreviewExport,
  ProgramDayInput,
  ProgramSessionItemInput,
  ProgramSessionInput,
  ProgramWeekInput,
  UpdateUserProgramInput,
  TodayPlanResponse,
  UpdateProgramInput,
  ProgramAttributeInput,
} from './programs.types';

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
        include: {
          sessions: {
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

async function syncProgramMatchingColumns(
  db: Pick<PrismaClient, 'programAttribute' | 'program'>,
  programId: string,
) {
  const rows = await db.programAttribute.findMany({
    where: { programId },
    include: { attributeValue: { include: { attribute: true } } },
    orderBy: { createdAt: 'asc' },
  });
  const cols = matchingColumnsFromProgramAttributeRows(
    rows.map((r) => ({
      mode: r.mode,
      attributeValue: {
        code: r.attributeValue.code,
        attribute: { code: r.attributeValue.attribute.code },
      },
    })),
  );
  await db.program.update({
    where: { id: programId },
    data: {
      programDomain: cols.programDomain,
      trainingGoal: cols.trainingGoal,
      targetEquipment: cols.targetEquipment,
      targetDomain: cols.targetDomain,
      targetRegions: cols.targetRegions,
      contraindications: cols.contraindications,
    },
  });
}

export { validateCalendarProgramStructure } from './calendar-program-structure';

function buildSessionItems(items?: ProgramSessionItemInput[]) {
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
      notes: (item.notes as object) || undefined,
      restDurationMs: item.restDurationMs ?? undefined,
      sourceWorkoutId: item.sourceWorkoutId ?? undefined,
      sortOrder: item.sortOrder ?? index,
      allowedSubstitutions: item.allowedSubstitutions ?? item.alternatives ?? [],
      role: item.role ?? undefined,
      intent: item.intent ?? undefined,
      coachingNotes: (item.coachingNotes as object) || undefined,
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
              sessions: day.sessions
                ? {
                    create: day.sessions.map((session, sessionIndex) => ({
                      name: session.name as object,
                      sortOrder: session.sortOrder ?? sessionIndex,
                      items: buildSessionItems(session.items),
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

function sessionItemScalarFromInput(item: ProgramSessionItemInput, sortOrder: number) {
  return {
    type: item.type,
    exerciseId: item.exerciseId ?? undefined,
    sets: item.sets ?? undefined,
    targetReps: item.targetReps ?? undefined,
    targetDuration: item.targetDuration ?? undefined,
    restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
    weightKg: item.weightKg ?? undefined,
    weightPerSet: item.weightPerSet ?? undefined,
    notes: (item.notes as object) || undefined,
    restDurationMs: item.restDurationMs ?? undefined,
    sourceWorkoutId: item.sourceWorkoutId ?? undefined,
    sortOrder,
    allowedSubstitutions: item.allowedSubstitutions ?? item.alternatives ?? [],
    role: item.role ?? undefined,
    intent: item.intent ?? undefined,
    coachingNotes: (item.coachingNotes as object) || undefined,
  };
}

async function syncSessionItems(
  tx: Prisma.TransactionClient,
  sessionId: string,
  itemsInput: ProgramSessionItemInput[] | undefined,
) {
  const items = itemsInput ?? [];
  const existing = await tx.programSessionItem.findMany({
    where: { sessionId },
    select: { id: true },
  });
  const existingIds = new Set(existing.map((e) => e.id));
  const payloadIds = new Set(items.map((i) => i.id).filter(Boolean) as string[]);

  const toRemove = existing.filter((e) => !payloadIds.has(e.id)).map((e) => e.id);
  if (toRemove.length > 0) {
    await tx.programSessionItem.deleteMany({ where: { id: { in: toRemove } } });
  }

  for (let index = 0; index < items.length; index++) {
    const item = items[index]!;
    const sortOrder = item.sortOrder ?? index;
    const data = sessionItemScalarFromInput(item, sortOrder);
    if (item.id) {
      if (!existingIds.has(item.id)) {
        throw new Error(`Invalid program session item id for session ${sessionId}: ${item.id}`);
      }
      await tx.programSessionItem.update({
        where: { id: item.id },
        data,
      });
    } else {
      await tx.programSessionItem.create({
        data: {
          ...data,
          sessionId,
        },
      });
    }
  }
}

async function syncSessions(
  tx: Prisma.TransactionClient,
  dayId: string,
  sessionsInput: ProgramSessionInput[] | undefined,
) {
  const sessions = sessionsInput ?? [];
  const existing = await tx.programSession.findMany({
    where: { dayId },
    select: { id: true },
  });
  const existingIds = new Set(existing.map((e) => e.id));
  const payloadIds = new Set(sessions.map((s) => s.id).filter(Boolean) as string[]);

  const toRemove = existing.filter((e) => !payloadIds.has(e.id)).map((e) => e.id);
  if (toRemove.length > 0) {
    await tx.programSession.deleteMany({ where: { id: { in: toRemove } } });
  }

  for (let index = 0; index < sessions.length; index++) {
    const session = sessions[index]!;
    const sortOrder = session.sortOrder ?? index;
    if (session.id) {
      if (!existingIds.has(session.id)) {
        throw new Error(`Invalid program session id for day ${dayId}: ${session.id}`);
      }
      await tx.programSession.update({
        where: { id: session.id },
        data: {
          name: session.name as object,
          sortOrder,
        },
      });
      await syncSessionItems(tx, session.id, session.items);
    } else {
      await tx.programSession.create({
        data: {
          dayId,
          name: session.name as object,
          sortOrder,
          items: {
            create: (session.items ?? []).map((item, ii) =>
              sessionItemScalarFromInput(item, item.sortOrder ?? ii),
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
      await syncSessions(tx, day.id, day.sessions);
    } else {
      await tx.programDay.create({
        data: {
          weekId,
          dayNumber: day.dayNumber,
          isRestDay: day.isRestDay ?? false,
          name: (day.name as object) || undefined,
          dayFocus: day.dayFocus ?? undefined,
          sessions: {
            create: (day.sessions ?? []).map((session, si) => ({
              name: session.name as object,
              sortOrder: session.sortOrder ?? si,
              items: {
                create: (session.items ?? []).map((item, ii) =>
                  sessionItemScalarFromInput(item, item.sortOrder ?? ii),
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
              sessions: {
                create: (day.sessions ?? []).map((session, si) => ({
                  name: session.name as object,
                  sortOrder: session.sortOrder ?? si,
                  items: {
                    create: (session.items ?? []).map((item, ii) =>
                      sessionItemScalarFromInput(item, item.sortOrder ?? ii),
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
    programDomain?: string;
    trainingGoal?: string;
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
    if (filters?.programDomain) {
      const enumVal = filters.programDomain as ProgramDomain;
      const code = PROGRAM_DOMAIN_VALUE_CODE[enumVal];
      if (code) {
        andFilters.push({
          programAttributes: {
            some: {
              mode: { in: [ProgramAttributeMode.REQUIRED, ProgramAttributeMode.OPTIONAL] },
              attributeValue: { code },
            },
          },
        });
      }
    }
    if (filters?.trainingGoal) {
      const tg = filters.trainingGoal as TrainingGoal;
      const gc = TRAINING_GOAL_VALUE_CODE[tg];
      if (gc) {
        andFilters.push({
          programAttributes: {
            some: {
              mode: { in: [ProgramAttributeMode.REQUIRED, ProgramAttributeMode.OPTIONAL] },
              attributeValue: { code: gc },
            },
          },
        });
      }
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
        'rows — consider narrowing filters',
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

    const programId = await prisma.$transaction(async (tx) => {
      const program = await tx.program.create({
        data: {
          name: data.name as object,
          description: (data.description as object) || undefined,
          slug,
          coverImageUrl: data.coverImageUrl ?? undefined,
          durationWeeks: data.durationWeeks,
          difficulty: data.difficulty ?? 'beginner',
          tags: data.tags ?? undefined,
          isDefault: data.isDefault ?? false,
          isPublished: false,
          createdBy,
          updatedBy: createdBy,
          programType: data.programType ?? 'SYSTEM',
          programDomain: data.programDomain ?? 'TRAINING',
          trainingGoal: data.trainingGoal ?? undefined,
          autoAssignable: data.autoAssignable ?? false,
          version: data.version ?? 1,
          ownerId: data.ownerId ?? undefined,
          forkedFromId: data.forkedFromId ?? undefined,
          coachingNotes: data.coachingNotes as object ?? undefined,
          weeklySessionTarget: data.weeklySessionTarget ?? undefined,
          estimatedSessionMinutes: data.estimatedSessionMinutes ?? undefined,
          targetEquipment: data.targetEquipment as object ?? undefined,
          targetDomain: data.targetDomain ?? undefined,
          targetRegions: data.targetRegions ?? [],
          levelRangeMin: data.levelRangeMin ?? 1,
          levelRangeMax: data.levelRangeMax ?? 5,
          entryRecommendations:
            (data.entryRecommendations ?? data.entryCriteria) as object ?? undefined,
          exitRecommendations:
            (data.exitRecommendations ?? data.exitCriteria) as object ?? undefined,
          contraindications: data.contraindications ?? [],
          prescriptionPriority: data.prescriptionPriority ?? 100,
          prerequisiteProgramId: data.prerequisiteProgramId ?? undefined,
          nextProgramId: data.nextProgramId ?? undefined,
          weeks: buildWeeksCreate(data.weeks),
        },
      });

      if (data.programAttributes !== undefined) {
        await replaceProgramAttributes(tx, program.id, data.programAttributes);
        await syncProgramMatchingColumns(tx, program.id);
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
    if (data.difficulty !== undefined) updateData.difficulty = data.difficulty;
    if (data.tags !== undefined) updateData.tags = data.tags;
    if (data.isDefault !== undefined) updateData.isDefault = data.isDefault;
    if (data.isPublished !== undefined) updateData.isPublished = data.isPublished;
    // Prescription metadata
    if (data.programType !== undefined) updateData.programType = data.programType;
    if (data.programDomain !== undefined) updateData.programDomain = data.programDomain;
    if (data.trainingGoal !== undefined) updateData.trainingGoal = data.trainingGoal;
    if (data.autoAssignable !== undefined) updateData.autoAssignable = data.autoAssignable;
    if (data.version !== undefined) updateData.version = data.version;
    if (data.ownerId !== undefined) updateData.ownerId = data.ownerId;
    if (data.forkedFromId !== undefined) updateData.forkedFromId = data.forkedFromId;
    if (data.coachingNotes !== undefined) updateData.coachingNotes = data.coachingNotes;
    if (data.weeklySessionTarget !== undefined) {
      updateData.weeklySessionTarget = data.weeklySessionTarget;
    }
    if (data.estimatedSessionMinutes !== undefined) {
      updateData.estimatedSessionMinutes = data.estimatedSessionMinutes;
    }
    if (data.targetEquipment !== undefined) updateData.targetEquipment = data.targetEquipment;
    if (data.targetDomain !== undefined) updateData.targetDomain = data.targetDomain;
    if (data.targetRegions !== undefined) updateData.targetRegions = data.targetRegions;
    if (data.levelRangeMin !== undefined) updateData.levelRangeMin = data.levelRangeMin;
    if (data.levelRangeMax !== undefined) updateData.levelRangeMax = data.levelRangeMax;
    if (data.entryRecommendations !== undefined) {
      updateData.entryRecommendations = data.entryRecommendations;
    } else if (data.entryCriteria !== undefined) {
      updateData.entryRecommendations = data.entryCriteria;
    }
    if (data.exitRecommendations !== undefined) {
      updateData.exitRecommendations = data.exitRecommendations;
    } else if (data.exitCriteria !== undefined) {
      updateData.exitRecommendations = data.exitCriteria;
    }
    if (data.contraindications !== undefined) updateData.contraindications = data.contraindications;
    if (data.prescriptionPriority !== undefined) updateData.prescriptionPriority = data.prescriptionPriority;
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
        await syncProgramMatchingColumns(tx, id);
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
      data: { isPublished: true, updatedBy },
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
      ar: `${name.ar || ''} (نسخة)`,
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
        sessions: day.sessions.map((session, sessionIndex) => ({
          name: parseLocalizedText(session.name) || { ar: '', en: '' },
          sortOrder: session.sortOrder ?? sessionIndex,
          items: session.items.map((item, itemIndex) => ({
            type: item.type as 'exercise' | 'rest',
            exerciseId: item.exerciseId ?? undefined,
            sets: item.sets ?? undefined,
            targetReps: item.targetReps ?? undefined,
            targetDuration: item.targetDuration ?? undefined,
            restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
            weightKg: item.weightKg ?? undefined,
            weightPerSet: (item.weightPerSet as number[]) || undefined,
            notes: parseLocalizedText(item.notes),
            restDurationMs: item.restDurationMs ?? undefined,
            sourceWorkoutId: item.sourceWorkoutId ?? undefined,
            sortOrder: item.sortOrder ?? itemIndex,
            allowedSubstitutions: item.allowedSubstitutions ?? [],
            role: item.role ?? undefined,
            intent: item.intent ?? undefined,
            coachingNotes: parseLocalizedText(item.coachingNotes),
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
        difficulty: original.difficulty as 'beginner' | 'intermediate' | 'advanced',
        tags: (original.tags as string[]) || undefined,
        isDefault: false,
        programType: original.programType,
        programDomain: original.programDomain,
        trainingGoal: original.trainingGoal ?? undefined,
        autoAssignable: original.autoAssignable,
        version: original.version,
        ownerId: original.ownerId ?? undefined,
        forkedFromId: original.id,
        coachingNotes: (original.coachingNotes as Record<string, unknown>) || undefined,
        weeklySessionTarget: original.weeklySessionTarget ?? undefined,
        estimatedSessionMinutes: original.estimatedSessionMinutes ?? undefined,
        targetEquipment: (original.targetEquipment as Record<string, unknown>) ?? undefined,
        targetDomain: original.targetDomain ?? undefined,
        targetRegions: original.targetRegions ?? [],
        levelRangeMin: original.levelRangeMin,
        levelRangeMax: original.levelRangeMax,
        entryRecommendations: (original.entryRecommendations as Record<string, unknown>) ?? undefined,
        exitRecommendations: (original.exitRecommendations as Record<string, unknown>) ?? undefined,
        contraindications: original.contraindications ?? [],
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
      include: { days: { include: { sessions: { include: { items: true } } } } },
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
                sessions: day.sessions
                  ? {
                      create: day.sessions.map((session, sessionIndex) => ({
                        name: session.name as object,
                        sortOrder: session.sortOrder ?? sessionIndex,
                        items: buildSessionItems(session.items),
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
      include: { days: { include: { sessions: { include: { items: true } } } } },
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
      include: { days: { include: { sessions: { include: { items: true } } } } },
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
            sessions: {
              create: day.sessions.map((session) => ({
                name: toInputJsonOrNull(session.name),
                sortOrder: session.sortOrder,
                items: {
                  create: session.items.map((item) => ({
                    type: item.type,
                    exerciseId: item.exerciseId ?? undefined,
                    sets: item.sets ?? undefined,
                    targetReps: item.targetReps ?? undefined,
                    targetDuration: item.targetDuration ?? undefined,
                    restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
                    weightKg: item.weightKg ?? undefined,
                    weightPerSet: item.weightPerSet ?? undefined,
                    notes: toInputJsonOrNull(item.notes),
                    restDurationMs: item.restDurationMs ?? undefined,
                    sourceWorkoutId: item.sourceWorkoutId ?? undefined,
                    sortOrder: item.sortOrder,
                    allowedSubstitutions: item.allowedSubstitutions ?? [],
                    role: item.role ?? undefined,
                    intent: item.intent ?? undefined,
                    coachingNotes: toInputJsonOrNull(item.coachingNotes),
                  })),
                },
              })),
            },
          })),
        },
      },
      include: { days: { include: { sessions: { include: { items: true } } } } },
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
        sessions: day.sessions
          ? {
              create: day.sessions.map((session, sessionIndex) => ({
                name: session.name as object,
                sortOrder: session.sortOrder ?? sessionIndex,
                items: buildSessionItems(session.items),
              })),
            }
          : undefined,
      },
      include: { sessions: { include: { items: true } } },
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

    if (day.sessions !== undefined) {
      await prisma.programSession.deleteMany({ where: { dayId } });
      if (day.sessions.length > 0) {
        await prisma.programDay.update({
          where: { id: dayId },
          data: {
            sessions: {
              create: day.sessions.map((session, sessionIndex) => ({
                name: session.name as object,
                sortOrder: session.sortOrder ?? sessionIndex,
                items: buildSessionItems(session.items),
              })),
            },
          },
        });
      }
    }

    return prisma.programDay.findFirst({
      where: { id: dayId },
      include: { sessions: { include: { items: true } } },
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

  async createSession(programId: string, dayId: string, session: ProgramSessionInput) {
    const prisma = await getPrisma();
    const day = await prisma.programDay.findFirst({ where: { id: dayId, week: { programId } } });
    if (!day) return null;
    return prisma.programSession.create({
      data: {
        dayId,
        name: session.name as object,
        sortOrder: session.sortOrder ?? 0,
        items: buildSessionItems(session.items),
      },
      include: { items: true },
    });
  },

  async updateSession(programId: string, sessionId: string, session: ProgramSessionInput) {
    const prisma = await getPrisma();
    const existing = await prisma.programSession.findFirst({
      where: { id: sessionId, day: { week: { programId } } },
    });
    if (!existing) return null;

    await prisma.programSession.update({
      where: { id: sessionId },
      data: {
        name: session.name ? (session.name as object) : undefined,
        sortOrder: session.sortOrder ?? existing.sortOrder,
      },
    });

    if (session.items !== undefined) {
      await prisma.programSessionItem.deleteMany({ where: { sessionId } });
      if (session.items.length > 0) {
        await prisma.programSession.update({
          where: { id: sessionId },
          data: {
            items: buildSessionItems(session.items),
          },
        });
      }
    }

    return prisma.programSession.findFirst({
      where: { id: sessionId },
      include: { items: true },
    });
  },

  async deleteSession(programId: string, sessionId: string) {
    const prisma = await getPrisma();
    const existing = await prisma.programSession.findFirst({
      where: { id: sessionId, day: { week: { programId } } },
    });
    if (!existing) return null;
    await prisma.programSession.delete({ where: { id: sessionId } });
    return true;
  },

  async createSessionItem(programId: string, sessionId: string, item: ProgramSessionItemInput) {
    const prisma = await getPrisma();
    const session = await prisma.programSession.findFirst({
      where: { id: sessionId, day: { week: { programId } } },
    });
    if (!session) return null;
    return prisma.programSessionItem.create({
      data: {
        sessionId,
        type: item.type,
        exerciseId: item.exerciseId ?? undefined,
        sets: item.sets ?? undefined,
        targetReps: item.targetReps ?? undefined,
        targetDuration: item.targetDuration ?? undefined,
        restBetweenSetsMs: item.restBetweenSetsMs ?? undefined,
        weightKg: item.weightKg ?? undefined,
        weightPerSet: item.weightPerSet ?? undefined,
        notes: (item.notes as object) || undefined,
        restDurationMs: item.restDurationMs ?? undefined,
        sourceWorkoutId: item.sourceWorkoutId ?? undefined,
        sortOrder: item.sortOrder ?? 0,
        isModified: false,
        allowedSubstitutions: item.allowedSubstitutions ?? item.alternatives ?? [],
        role: item.role ?? undefined,
        intent: item.intent ?? undefined,
        coachingNotes: (item.coachingNotes as object) || undefined,
      },
    });
  },

  async updateSessionItem(programId: string, itemId: string, item: ProgramSessionItemInput) {
    const prisma = await getPrisma();
    const existing = await prisma.programSessionItem.findFirst({
      where: { id: itemId, session: { day: { week: { programId } } } },
    });
    if (!existing) return null;

    return prisma.programSessionItem.update({
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
        notes: item.notes ? (item.notes as object) : undefined,
        restDurationMs: item.restDurationMs ?? undefined,
        sourceWorkoutId: item.sourceWorkoutId ?? undefined,
        sortOrder: item.sortOrder ?? existing.sortOrder,
        isModified: true,
        allowedSubstitutions: item.allowedSubstitutions ?? item.alternatives ?? undefined,
        role: item.role ?? undefined,
        intent: item.intent ?? undefined,
        coachingNotes: item.coachingNotes ? (item.coachingNotes as object) : undefined,
      },
    });
  },

  async deleteSessionItem(programId: string, itemId: string) {
    const prisma = await getPrisma();
    const existing = await prisma.programSessionItem.findFirst({
      where: { id: itemId, session: { day: { week: { programId } } } },
    });
    if (!existing) return null;
    await prisma.programSessionItem.delete({ where: { id: itemId } });
    return true;
  },

  async importWorkoutToSession(programId: string, sessionId: string, workoutId: string) {
    const prisma = await getPrisma();
    const session = await prisma.programSession.findFirst({
      where: { id: sessionId, day: { week: { programId } } },
    });
    if (!session) return null;

    const workout = await prisma.workout.findFirst({
      where: { id: workoutId, deletedAt: null },
      include: { exercises: { orderBy: { sortOrder: 'asc' } } },
    });
    if (!workout) return null;

    const existingCount = await prisma.programSessionItem.count({ where: { sessionId } });
    let sortIndex = existingCount;
    const itemsToCreate: Prisma.ProgramSessionItemCreateManyInput[] = [];

    workout.exercises.forEach((exercise) => {
      itemsToCreate.push({
        sessionId,
        type: 'exercise',
        exerciseId: exercise.exerciseId,
        sets: exercise.sets,
        targetReps: exercise.targetReps ?? undefined,
        targetDuration: exercise.targetDuration ?? undefined,
        restBetweenSetsMs: exercise.restBetweenSetsMs,
        weightKg: exercise.weightKg ?? undefined,
        weightPerSet: exercise.weightPerSet ?? undefined,
        notes: exercise.notes ?? undefined,
        sourceWorkoutId: workout.id,
        sortOrder: sortIndex++,
        isModified: false,
      });

      if (exercise.restAfterExerciseMs && exercise.restAfterExerciseMs > 0) {
        itemsToCreate.push({
          sessionId,
          type: 'rest',
          restDurationMs: exercise.restAfterExerciseMs,
          sourceWorkoutId: workout.id,
          sortOrder: sortIndex++,
          isModified: false,
        });
      }
    });

    if (itemsToCreate.length > 0) {
      await prisma.programSessionItem.createMany({ data: itemsToCreate });
    }

    return prisma.programSession.findFirst({
      where: { id: sessionId },
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
            `Invalid customization value for key "${key}". Expected array of sessions.`
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
    const position = resolveCurrentProgramDay(program.weeks, userProgram.progress ?? [], {
      startDate: userProgram.startDate,
      durationWeeks: program.durationWeeks,
      totalPausedDays: userProgram.totalPausedDays,
      pausedAt: userProgram.pausedAt,
    });

    const weekNumber = position.targetWeekNumber;
    const dayNumber = position.targetDayNumber;
    const isProgramComplete = position.isProgramComplete;

    const week = program.weeks.find((w) => w.weekNumber === weekNumber);
    const day = week?.days.find((d) => d.dayNumber === dayNumber);

    // Build progress map for the response
    const progressMap: Record<string, string> = {};
    if (userProgram.progress) {
      for (const p of userProgram.progress) {
        const key = `${p.weekNumber}_${p.dayNumber}${p.sessionId ? '_' + p.sessionId : ''}`;
        progressMap[key] = p.status;
      }
    }

    const catchUpSuggestion =
      !isProgramComplete && program.id && day
        ? await computeCatchUpSuggestion(
            userId,
            program.id,
            program.weeks,
            program.durationWeeks,
            weekNumber,
            dayNumber,
          )
        : null;

    return {
      userProgramId: userProgram.id,
      programId: userProgram.programId ?? undefined,
      weekNumber,
      dayNumber,
      date: new Date().toISOString(),
      isProgramComplete,
      progress: progressMap,
      isPaused: Boolean(userProgram.pausedAt),
      catchUpSuggestion,
      sessions: day
        ? day.sessions.map((session) => ({
            id: session.id,
            name: parseLocalizedText(session.name) || { ar: '', en: '' },
            sortOrder: session.sortOrder,
            estimatedDurationMin: session.estimatedDurationMin ?? undefined,
            items: session.items.map((item) => ({
              type: item.type as 'exercise' | 'rest',
              serverItemId: item.id,
              exerciseSlug: item.exercise?.slug ?? undefined,
              deletedExercise:
                item.type === 'exercise' && !item.exercise ? true : undefined,
              role: item.role ?? undefined,
              intent: item.intent ?? undefined,
              allowedSubstitutions:
                Array.isArray(item.allowedSubstitutions) && item.allowedSubstitutions.length > 0
                  ? (item.allowedSubstitutions as string[])
                  : undefined,
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
    const equipmentRaw = program.targetEquipment as unknown;
    const targetEquipment = Array.isArray(equipmentRaw)
      ? equipmentRaw.filter((x): x is string => typeof x === 'string')
      : undefined;

    return {
      id: program.id,
      slug: program.slug,
      name: parseLocalizedText(program.name) || { ar: '', en: '' },
      description: parseLocalizedText(program.description),
      coverImageUrl: program.coverImageUrl ?? undefined,
      durationWeeks: program.durationWeeks,
      difficulty: program.difficulty as 'beginner' | 'intermediate' | 'advanced',
      tags: (program.tags as string[]) || undefined,
      trainingGoal: program.trainingGoal ?? undefined,
      weeklySessionTarget: program.weeklySessionTarget ?? undefined,
      estimatedSessionMinutes: program.estimatedSessionMinutes ?? undefined,
      targetDomain: program.targetDomain ?? undefined,
      targetEquipment: targetEquipment?.length ? targetEquipment : undefined,
      isFeatured: program.isFeatured ?? undefined,
      weeks: program.weeks.map((week) => ({
        weekNumber: week.weekNumber,
        name: parseLocalizedText(week.name),
        description: parseLocalizedText(week.description),
        days: week.days.map((day) => ({
          dayNumber: day.dayNumber,
          isRestDay: day.isRestDay,
          name: parseLocalizedText(day.name),
          sessions: day.sessions.map((session) => ({
            id: session.id,
            name: parseLocalizedText(session.name) || { ar: '', en: '' },
            sortOrder: session.sortOrder,
            estimatedDurationMin: session.estimatedDurationMin ?? undefined,
            items: session.items.map((item) => ({
              type: item.type as 'exercise' | 'rest',
              serverItemId: item.id,
              exerciseSlug: item.exercise?.slug ?? undefined,
              deletedExercise:
                item.type === 'exercise' && !item.exercise ? true : undefined,
              role: item.role ?? undefined,
              intent: item.intent ?? undefined,
              allowedSubstitutions:
                Array.isArray(item.allowedSubstitutions) && item.allowedSubstitutions.length > 0
                  ? (item.allowedSubstitutions as string[])
                  : undefined,
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
        for (const s of d.sessions) {
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
      difficulty: full.difficulty,
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
