/**
 * Programs Service
 * =================
 * Service for Program CRUD operations.
 */

import { getPrisma } from '@/lib/prisma/client';
import { Prisma } from '@prisma/client';
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

function normalizeDate(date: Date) {
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}

function getProgramDayIndex(startDate: Date, now: Date) {
  const start = normalizeDate(startDate);
  const today = normalizeDate(now);
  const diffMs = Math.max(0, today.getTime() - start.getTime());
  const diffDays = Math.floor(diffMs / (24 * 60 * 60 * 1000));
  return diffDays;
}

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
      days: week.days
        ? {
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
          }
        : undefined,
    })),
  };
}

export const programService = {
  async list(filters?: { status?: 'draft' | 'published'; search?: string; page?: number; limit?: number }) {
    const prisma = await getPrisma();
    const page = filters?.page || 1;
    const limit = filters?.limit || 20;
    const skip = (page - 1) * limit;

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

    const [programs, total] = await Promise.all([
      prisma.program.findMany({
        where,
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
      }),
      prisma.program.count({ where }),
    ]);

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
      name: parseLocalizedText(week.name),
      description: parseLocalizedText(week.description),
      sortOrder: week.sortOrder ?? weekIndex,
      days: week.days.map((day) => ({
        dayNumber: day.dayNumber,
        isRestDay: day.isRestDay,
        name: parseLocalizedText(day.name),
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
        name: toInputJsonOrNull(sourceWeek.name),
        description: toInputJsonOrNull(sourceWeek.description),
        sortOrder: sourceWeek.sortOrder,
        days: {
          create: sourceWeek.days.map((day) => ({
            dayNumber: day.dayNumber,
            isRestDay: day.isRestDay,
            name: toInputJsonOrNull(day.name),
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
    return prisma.userProgram.updateMany({
      where: { id: userProgramId, userId },
      data: {
        name: data.name ? (data.name as object) : undefined,
        customizations: toInputJson(data.customizations),
        isActive: data.isActive ?? undefined,
      },
    });
  },

  async getTodayPlan(userId: string): Promise<TodayPlanResponse | null> {
    const prisma = await getPrisma();
    const userProgram = await prisma.userProgram.findFirst({
      where: { userId, isActive: true },
      include: { program: { include: programFullInclude } },
    });
    if (!userProgram || !userProgram.program) return null;

    const dayIndex = getProgramDayIndex(userProgram.startDate, new Date());
    const totalWeeks = Math.max(1, userProgram.program.durationWeeks || 1);
    const weekNumber = (Math.floor(dayIndex / 7) % totalWeeks) + 1;
    const dayNumber = (dayIndex % 7) + 1;

    const week = userProgram.program.weeks.find((w) => w.weekNumber === weekNumber);
    const day = week?.days.find((d) => d.dayNumber === dayNumber);

    return {
      userProgramId: userProgram.id,
      programId: userProgram.programId ?? undefined,
      weekNumber,
      dayNumber,
      date: new Date().toISOString(),
      sessions: day
        ? day.sessions.map((session) => ({
            id: session.id,
            name: parseLocalizedText(session.name) || { ar: '', en: '' },
            sortOrder: session.sortOrder,
            items: session.items.map((item) => ({
              type: item.type as 'exercise' | 'rest',
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
    return {
      id: program.id,
      slug: program.slug,
      name: parseLocalizedText(program.name) || { ar: '', en: '' },
      description: parseLocalizedText(program.description),
      coverImageUrl: program.coverImageUrl ?? undefined,
      durationWeeks: program.durationWeeks,
      difficulty: program.difficulty as 'beginner' | 'intermediate' | 'advanced',
      tags: (program.tags as string[]) || undefined,
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

    await prisma.userProgram.updateMany({
      where: { userId, isActive: true },
      data: { isActive: false },
    });

    return prisma.userProgram.create({
      data: {
        userId,
        programId,
        name: (name as object) || undefined,
        isActive: true,
      },
    });
  },
};
