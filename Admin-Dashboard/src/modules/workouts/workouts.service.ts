/**
 * Workouts Service
 * =================
 * 
 * Service for workout (Super Set / Circuit) CRUD operations.
 */

import { getPrisma } from '@/lib/prisma/client';
import { DEFAULT_REST_TIMES } from './workouts.types';
import type {
  CreateWorkoutInput,
  UpdateWorkoutInput,
  WorkoutListFilters,
  WorkoutExerciseInput,
  WorkoutExport,
  WorkoutExerciseExport,
} from './workouts.types';

// ============================================
// TYPES
// ============================================

interface LocalizedText {
  ar: string;
  en: string;
}

// ============================================
// HELPERS
// ============================================

function parseLocalizedText(value: unknown): LocalizedText | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const record = value as Record<string, unknown>;
  if (typeof record.ar !== 'string' || typeof record.en !== 'string') return undefined;
  return { ar: record.ar, en: record.en };
}

/**
 * Generate a unique slug from name
 */
function generateSlug(name: { en?: string; ar?: string }): string {
  const baseName = name.en || name.ar || 'workout';
  return baseName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    + '_' + Date.now().toString(36);
}

/**
 * Include for full workout with exercises
 */
const workoutFullInclude = {
  exercises: {
    orderBy: { sortOrder: 'asc' as const },
    include: {
      exercise: {
        select: {
          id: true,
          slug: true,
          name: true,
          description: true,
          status: true,
          countingMethod: {
            select: {
              code: true,
              name: true,
            },
          },
          category: {
            select: {
              code: true,
              name: true,
            },
          },
          poseVariants: {
            select: {
              id: true,
              name: true,
              sortOrder: true,
            },
            orderBy: { sortOrder: 'asc' as const },
          },
        },
      },
    },
  },
};

// ============================================
// SERVICE
// ============================================

export const workoutService = {
  /**
   * List workouts with filters and pagination
   */
  async list(filters?: WorkoutListFilters) {
    const prisma = await getPrisma();
    const page = filters?.page || 1;
    const limit = filters?.limit || 20;
    const skip = (page - 1) * limit;

    const where: Record<string, unknown> = {
      deletedAt: null,
    };

    if (filters?.status) {
      where.status = filters.status;
    }

    if (filters?.search) {
      where.OR = [
        { name: { path: ['en'], string_contains: filters.search } },
        { name: { path: ['ar'], string_contains: filters.search } },
      ];
    }

    const [workouts, total] = await Promise.all([
      prisma.workout.findMany({
        where,
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
        include: {
          _count: {
            select: { exercises: true },
          },
        },
      }),
      prisma.workout.count({ where }),
    ]);

    return {
      workouts,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    };
  },

  /**
   * Get workout by ID with all exercises
   */
  async getById(id: string) {
    const prisma = await getPrisma();
    return prisma.workout.findUnique({
      where: { id, deletedAt: null },
      include: workoutFullInclude,
    });
  },

  /**
   * Get workout by slug
   */
  async getBySlug(slug: string) {
    const prisma = await getPrisma();
    return prisma.workout.findUnique({
      where: { slug, deletedAt: null },
      include: workoutFullInclude,
    });
  },

  /**
   * Create a new workout
   */
  async create(data: CreateWorkoutInput, createdBy?: string) {
    const prisma = await getPrisma();
    const slug = data.slug || generateSlug(data.name);

    const workout = await prisma.workout.create({
      data: {
        name: data.name as object,
        description: (data.description as object) || undefined,
        slug,
        coverImageUrl: data.coverImageUrl ?? undefined,
        difficulty: data.difficulty ?? 'beginner',
        estimatedDurationMin: data.estimatedDurationMin ?? undefined,
        tags: data.tags ?? undefined,
        status: 'draft',
        createdBy,
        updatedBy: createdBy,
      },
    });

    // Create workout exercises if provided
    if (data.exercises && data.exercises.length > 0) {
      await this.createWorkoutExercises(prisma, workout.id, data.exercises);
    }

    return this.getById(workout.id);
  },

  /**
   * Create workout exercises
   */
  async createWorkoutExercises(
    prisma: Awaited<ReturnType<typeof getPrisma>>,
    workoutId: string,
    exercises: WorkoutExerciseInput[]
  ) {
    await prisma.workoutExercise.createMany({
      data: exercises.map((ex, index) => ({
        workoutId,
        exerciseId: ex.exerciseId,
        variantIndex: ex.variantIndex ?? 0,
        difficulty: ex.difficulty ?? 'beginner',
        targetReps: ex.targetReps ?? undefined,
        targetDuration: ex.targetDuration ?? undefined,
        sets: ex.sets ?? 1,
        restBetweenSetsMs: ex.restBetweenSetsMs ?? DEFAULT_REST_TIMES.restBetweenSetsMs,
        restAfterExerciseMs: ex.restAfterExerciseMs ?? DEFAULT_REST_TIMES.restAfterExerciseMs,
        weightKg: ex.weightKg ?? undefined,
        weightPerSet: ex.weightPerSet ?? undefined,
        notes: (ex.notes as object) || undefined,
        sortOrder: ex.sortOrder ?? index,
      })),
    });
  },

  /**
   * Update a workout
   */
  async update(id: string, data: UpdateWorkoutInput, updatedBy?: string) {
    const prisma = await getPrisma();

    const updateData: Record<string, unknown> = {
      updatedBy,
    };

    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined) updateData.description = data.description;
    if (data.coverImageUrl !== undefined) updateData.coverImageUrl = data.coverImageUrl;
    if (data.difficulty !== undefined) updateData.difficulty = data.difficulty;
    if (data.estimatedDurationMin !== undefined) updateData.estimatedDurationMin = data.estimatedDurationMin;
    if (data.tags !== undefined) updateData.tags = data.tags;
    if (data.status !== undefined) updateData.status = data.status;

    await prisma.workout.update({
      where: { id },
      data: updateData,
    });

    // Update exercises if provided
    if (data.exercises !== undefined) {
      // Delete existing exercises
      await prisma.workoutExercise.deleteMany({
        where: { workoutId: id },
      });

      // Create new exercises
      if (data.exercises.length > 0) {
        await this.createWorkoutExercises(prisma, id, data.exercises);
      }
    }

    return this.getById(id);
  },

  /**
   * Publish a workout
   */
  async publish(id: string, updatedBy?: string) {
    const prisma = await getPrisma();
    return prisma.workout.update({
      where: { id },
      data: {
        status: 'published',
        publishedAt: new Date(),
        updatedBy,
      },
    });
  },

  /**
   * Unpublish a workout (back to draft)
   */
  async unpublish(id: string, updatedBy?: string) {
    const prisma = await getPrisma();
    return prisma.workout.update({
      where: { id },
      data: {
        status: 'draft',
        updatedBy,
      },
    });
  },

  /**
   * Soft delete a workout
   */
  async delete(id: string, deletedBy?: string) {
    const prisma = await getPrisma();
    return prisma.workout.update({
      where: { id },
      data: {
        deletedAt: new Date(),
        updatedBy: deletedBy,
      },
    });
  },

  /**
   * Duplicate a workout
   */
  async duplicate(id: string, createdBy?: string) {
    const original = await this.getById(id);
    if (!original) {
      throw new Error('Workout not found');
    }

    const name = parseLocalizedText(original.name) || { ar: '', en: '' };
    const newName: LocalizedText = {
      ar: `${name.ar || ''} (نسخة)`,
      en: `${name.en || ''} (Copy)`,
    };

    const exercises: WorkoutExerciseInput[] = original.exercises.map((ex, index) => ({
      exerciseId: ex.exerciseId,
      variantIndex: ex.variantIndex,
      difficulty: ex.difficulty as 'beginner' | 'normal' | 'advanced',
      targetReps: ex.targetReps ?? undefined,
      targetDuration: ex.targetDuration ?? undefined,
      sets: ex.sets ?? 1,
      restBetweenSetsMs: ex.restBetweenSetsMs ?? DEFAULT_REST_TIMES.restBetweenSetsMs,
      restAfterExerciseMs: ex.restAfterExerciseMs ?? DEFAULT_REST_TIMES.restAfterExerciseMs,
      weightKg: ex.weightKg ?? undefined,
      weightPerSet: (ex.weightPerSet as number[]) ?? undefined,
      notes: parseLocalizedText(ex.notes),
      sortOrder: index,
    }));

    return this.create(
      {
        name: newName,
        description: parseLocalizedText(original.description),
        coverImageUrl: original.coverImageUrl ?? undefined,
        difficulty: original.difficulty as 'beginner' | 'intermediate' | 'advanced',
        estimatedDurationMin: original.estimatedDurationMin ?? undefined,
        tags: (original.tags as string[]) || undefined,
        exercises,
      },
      createdBy
    );
  },

  /**
   * Get published workouts for mobile API
   */
  async getPublished() {
    const prisma = await getPrisma();
    return prisma.workout.findMany({
      where: {
        status: 'published',
        deletedAt: null,
      },
      orderBy: { updatedAt: 'desc' },
      include: workoutFullInclude,
    });
  },

  /**
   * Build workout export for mobile
   */
  buildWorkoutExport(workout: Awaited<ReturnType<typeof this.getById>>): WorkoutExport | null {
    if (!workout) return null;

    const exercises: WorkoutExerciseExport[] = workout.exercises.map((we) => {
      return {
        exercise: we.exercise.slug,
        variantIndex: we.variantIndex,
        difficulty: we.difficulty as 'beginner' | 'normal' | 'advanced',
        targetReps: we.targetReps ?? undefined,
        targetDuration: we.targetDuration ?? undefined,
        sets: we.sets ?? 1,
        restBetweenSetsMs: we.restBetweenSetsMs ?? DEFAULT_REST_TIMES.restBetweenSetsMs,
        restAfterExerciseMs: we.restAfterExerciseMs ?? DEFAULT_REST_TIMES.restAfterExerciseMs,
        weightKg: we.weightKg ?? undefined,
        weightPerSet: (we.weightPerSet as number[]) || undefined,
        notes: parseLocalizedText(we.notes),
      };
    });

    return {
      id: workout.id,
      slug: workout.slug,
      name: parseLocalizedText(workout.name) || { ar: '', en: '' },
      description: parseLocalizedText(workout.description),
      coverImageUrl: workout.coverImageUrl ?? undefined,
      difficulty: workout.difficulty as 'beginner' | 'intermediate' | 'advanced',
      estimatedDurationMin: workout.estimatedDurationMin ?? undefined,
      tags: (workout.tags as string[]) || undefined,
      exercises,
      updatedAt: workout.updatedAt.toISOString(),
    };
  },

  /**
   * Get all published workouts formatted for mobile export
   */
  async getPublishedForMobile(): Promise<WorkoutExport[]> {
    const workouts = await this.getPublished();
    return workouts
      .map((w) => this.buildWorkoutExport(w))
      .filter((w): w is WorkoutExport => w !== null);
  },
};
