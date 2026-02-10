/**
 * Programs Service
 * =================
 * Service for Program CRUD operations.
 */

import { getPrisma } from '@/lib/prisma/client';
import type {
  CreateProgramInput,
  ProgramExport,
  ProgramSessionItemInput,
  ProgramWeekInput,
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

    const where: Record<string, unknown> = {};
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
    return prisma.program.findUnique({
      where: { id },
      include: programFullInclude,
    });
  },

  async getBySlug(slug: string) {
    const prisma = await getPrisma();
    return prisma.program.findUnique({
      where: { slug },
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
    await prisma.program.update({
      where: { id },
      data: { updatedBy },
    });
    return prisma.program.delete({ where: { id } });
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

  async getPublishedForMobile(): Promise<ProgramExport[]> {
    const prisma = await getPrisma();
    const programs = await prisma.program.findMany({
      where: { isPublished: true },
      include: programFullInclude,
      orderBy: { updatedAt: 'desc' },
    });
    return programs
      .map((program) => this.buildProgramExport(program))
      .filter((program): program is ProgramExport => program !== null);
  },
};
