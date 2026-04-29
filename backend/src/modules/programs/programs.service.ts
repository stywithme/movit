/**
 * Programs Service
 * =================
 * Service for Program CRUD operations.
 */

import { getPrisma } from '@/lib/prisma/client';
import { legacyTypeToProgramDomain } from '@/lib/program-domain';
import { Prisma } from '@prisma/client';
import { resolveCurrentProgramDay } from '@/modules/active-plan/plan-position';
import { getAutoAssignmentReadiness } from './program-assignment';
import { validateCalendarProgramStructure } from './calendar-program-structure';
import type {
  CreateProgramInput,
  ProgramExport,
  ProgramDayInput,
  ProgramSessionItemInput,
  ProgramSessionInput,
  ProgramWeekInput,
  UpdateUserProgramInput,
  TodayPlanResponse,
  UpdateProgramInput,
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

    const where: Record<string, unknown> = {
      deletedAt: null,
    };
    if (filters?.status === 'published') {
      where.isPublished = true;
    }
    if (filters?.status === 'draft') {
      where.isPublished = false;
    }
    if (filters?.search) {
      where.OR = [
        { name: { path: ['en'], string_contains: filters.search } },
        { name: { path: ['ar'], string_contains: filters.search } },
      ];
    }
    if (filters?.programDomain) {
      where.programDomain = filters.programDomain;
    }
    if (filters?.trainingGoal) {
      where.trainingGoal = filters.trainingGoal;
    }

    const rows = await prisma.program.findMany({
      where,
      orderBy: { createdAt: 'desc' },
    });

    const enriched = rows.map((program) => {
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
        autoAssignmentReadiness: {
          ...readiness,
          entersAutoAssignment,
          status,
        },
      };
    });

    const filtered = filters?.readiness
      ? enriched.filter((program) => program.autoAssignmentReadiness.status === filters.readiness)
      : enriched;

    const total = filtered.length;
    const skip = (page - 1) * limit;
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
    return prisma.program.findFirst({
      where: { id, deletedAt: null },
      include: programFullInclude,
    });
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

    const program = await prisma.program.create({
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
        // Prescription metadata
        programType: data.programType ?? 'SYSTEM',
        programDomain: data.programDomain ?? legacyTypeToProgramDomain(data.type),
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
      include: programFullInclude,
    });

    return program;
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
    if (data.type !== undefined && data.programDomain === undefined) {
      updateData.programDomain = legacyTypeToProgramDomain(data.type);
    }
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

    await prisma.program.update({
      where: { id },
      data: updateData,
    });

    if (data.weeks !== undefined) {
      await prisma.programWeek.deleteMany({ where: { programId: id } });
      if (data.weeks.length > 0) {
        await prisma.program.update({
          where: { id },
          data: {
            weeks: buildWeeksCreate(data.weeks),
          },
        });
      }
    }

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
      include: {
        weeks: {
          orderBy: [{ sortOrder: 'asc' }, { weekNumber: 'asc' }],
          include: {
            days: { orderBy: { dayNumber: 'asc' } },
          },
        },
      },
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

    return {
      userProgramId: userProgram.id,
      programId: userProgram.programId ?? undefined,
      weekNumber,
      dayNumber,
      date: new Date().toISOString(),
      isProgramComplete,
      progress: progressMap,
      sessions: day
        ? day.sessions.map((session) => ({
            id: session.id,
            name: parseLocalizedText(session.name) || { ar: '', en: '' },
            sortOrder: session.sortOrder,
            items: session.items.map((item) => ({
              type: item.type as 'exercise' | 'rest',
              serverItemId: item.id,
              exerciseSlug: item.exercise?.slug ?? undefined,
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
            items: session.items.map((item) => ({
              type: item.type as 'exercise' | 'rest',
              serverItemId: item.id,
              exerciseSlug: item.exercise?.slug ?? undefined,
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

  async enrollUser(userId: string, programId: string, name?: LocalizedText) {
    const prisma = await getPrisma();

    // Use a transaction to guarantee atomicity:
    // deactivate all existing programs, then create the new one.
    // Prevents race conditions that could leave multiple active programs.
    return prisma.$transaction(async (tx) => {
      await tx.userProgram.updateMany({
        where: { userId, isActive: true },
        data: { isActive: false },
      });

      return tx.userProgram.create({
        data: {
          userId,
          programId,
          name: (name as object) || undefined,
          isActive: true,
        },
      });
    });
  },
};
