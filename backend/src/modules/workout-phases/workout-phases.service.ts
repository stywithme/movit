import { Prisma, WorkoutBlockRole } from '@prisma/client';
import { getPrisma } from '@/lib/prisma/client';
import type {
  CreateWorkoutPhaseInput,
  UpdateWorkoutPhaseInput,
  WorkoutPhaseListFilters,
} from './workout-phases.types';
import { WorkoutPhaseInUseError } from './workout-phases.errors';

function toInputJson(value: unknown) {
  return value as Prisma.InputJsonValue;
}

function toInputJsonOrNull(value: unknown): Prisma.InputJsonValue | Prisma.JsonNullValueInput {
  if (value === null || value === undefined) {
    return Prisma.JsonNull;
  }
  return value as Prisma.InputJsonValue;
}

function generateSlug(name: { en?: string; ar?: string }) {
  const baseName = name.en || name.ar || 'phase';
  return baseName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    || `phase_${Date.now().toString(36)}`;
}

export const workoutPhasesService = {
  async list(filters?: WorkoutPhaseListFilters) {
    const prisma = await getPrisma();
    const where: Prisma.WorkoutPhaseWhereInput = {
      deletedAt: null,
    };

    if (filters?.active !== undefined) {
      where.isActive = filters.active;
    }

    if (filters?.search) {
      where.OR = [
        { slug: { contains: filters.search, mode: 'insensitive' } },
        { name: { path: ['en'], string_contains: filters.search } },
        { name: { path: ['ar'], string_contains: filters.search } },
      ];
    }

    return prisma.workoutPhase.findMany({
      where,
      orderBy: [{ sortOrder: 'asc' }, { createdAt: 'asc' }],
    });
  },

  async getById(id: string) {
    const prisma = await getPrisma();
    return prisma.workoutPhase.findUnique({
      where: { id, deletedAt: null },
      include: {
        _count: {
          select: { templatePhases: true },
        },
      },
    });
  },

  async getBySlug(slug: string) {
    const prisma = await getPrisma();
    return prisma.workoutPhase.findUnique({
      where: { slug, deletedAt: null },
    });
  },

  async create(data: CreateWorkoutPhaseInput, createdBy?: string) {
    const prisma = await getPrisma();
    const maxSortOrder = await prisma.workoutPhase.aggregate({
      where: { deletedAt: null },
      _max: { sortOrder: true },
    });

    return prisma.workoutPhase.create({
      data: {
        slug: data.slug?.trim() || generateSlug(data.name),
        name: toInputJson(data.name),
        description: toInputJsonOrNull(data.description),
        role: data.role ?? WorkoutBlockRole.MAIN,
        canSkip: data.canSkip ?? false,
        canContinue: data.canContinue ?? true,
        maxContinueTimeMs: data.maxContinueTimeMs ?? null,
        color: data.color?.trim() || null,
        icon: data.icon?.trim() || null,
        isActive: data.isActive ?? true,
        sortOrder: data.sortOrder ?? (maxSortOrder._max.sortOrder ?? 0) + 10,
        createdBy,
        updatedBy: createdBy,
      },
    });
  },

  async update(id: string, data: UpdateWorkoutPhaseInput, updatedBy?: string) {
    const prisma = await getPrisma();
    const updateData: Prisma.WorkoutPhaseUpdateInput = {
      updatedBy,
    };

    if (data.slug !== undefined) updateData.slug = data.slug.trim();
    if (data.name !== undefined) updateData.name = toInputJson(data.name);
    if (data.description !== undefined) updateData.description = toInputJsonOrNull(data.description);
    if (data.role !== undefined) updateData.role = data.role;
    if (data.canSkip !== undefined) updateData.canSkip = data.canSkip;
    if (data.canContinue !== undefined) updateData.canContinue = data.canContinue;
    if (data.maxContinueTimeMs !== undefined) updateData.maxContinueTimeMs = data.maxContinueTimeMs;
    if (data.color !== undefined) updateData.color = data.color?.trim() || null;
    if (data.icon !== undefined) updateData.icon = data.icon?.trim() || null;
    if (data.isActive !== undefined) updateData.isActive = data.isActive;
    if (data.sortOrder !== undefined) updateData.sortOrder = data.sortOrder;

    return prisma.workoutPhase.update({
      where: { id },
      data: updateData,
    });
  },

  async delete(id: string, deletedBy?: string) {
    const prisma = await getPrisma();
    const templateCount = await prisma.workoutTemplatePhase.count({
      where: { phaseId: id },
    });
    if (templateCount > 0) {
      throw new WorkoutPhaseInUseError(templateCount);
    }

    return prisma.workoutPhase.update({
      where: { id },
      data: {
        isActive: false,
        deletedAt: new Date(),
        updatedBy: deletedBy,
      },
    });
  },
};
