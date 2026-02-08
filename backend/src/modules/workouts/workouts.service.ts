/**
 * Workouts Service
 * =================
 * 
 * Service for workout (Super Set / Circuit) CRUD operations.
 */

import { getPrisma } from '@/lib/prisma/client';
import type {
  CreateWorkoutInput,
  UpdateWorkoutInput,
  WorkoutListFilters,
  WorkoutExerciseInput,
  WorkoutExport,
  WorkoutExerciseExport,
  DEFAULT_REST_TIMES,
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

    if (filters?.type) {
      where.type = filters.type;
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
        type: data.type,
        executionMode: data.executionMode,
        rounds: data.rounds ?? 1,
        repsPerSwitch: data.repsPerSwitch ?? undefined,
        restBetweenSwitchMs: data.restBetweenSwitchMs ?? undefined,
        restBetweenExercisesMs: data.restBetweenExercisesMs ?? undefined,
        restBetweenRoundsMs: data.restBetweenRoundsMs ?? 60000,
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
    if (data.type !== undefined) updateData.type = data.type;
    if (data.executionMode !== undefined) updateData.executionMode = data.executionMode;
    if (data.rounds !== undefined) updateData.rounds = data.rounds;
    if (data.repsPerSwitch !== undefined) updateData.repsPerSwitch = data.repsPerSwitch;
    if (data.restBetweenSwitchMs !== undefined) updateData.restBetweenSwitchMs = data.restBetweenSwitchMs;
    if (data.restBetweenExercisesMs !== undefined) updateData.restBetweenExercisesMs = data.restBetweenExercisesMs;
    if (data.restBetweenRoundsMs !== undefined) updateData.restBetweenRoundsMs = data.restBetweenRoundsMs;
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
      notes: parseLocalizedText(ex.notes),
      sortOrder: index,
    }));

    return this.create(
      {
        name: newName,
        description: parseLocalizedText(original.description),
        type: original.type as 'circuit' | 'super_set',
        executionMode: original.executionMode as 'sequential' | 'alternating',
        rounds: original.rounds,
        repsPerSwitch: original.repsPerSwitch ?? undefined,
        restBetweenSwitchMs: original.restBetweenSwitchMs ?? undefined,
        restBetweenExercisesMs: original.restBetweenExercisesMs ?? undefined,
        restBetweenRoundsMs: original.restBetweenRoundsMs,
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
      const target: { reps?: number; durationSec?: number } = {};
      if (we.targetReps) target.reps = we.targetReps;
      if (we.targetDuration) target.durationSec = we.targetDuration;

      return {
        exercise: we.exercise.slug,
        variantIndex: we.variantIndex,
        difficulty: we.difficulty as 'beginner' | 'normal' | 'advanced',
        target,
        notes: parseLocalizedText(we.notes),
      };
    });

    return {
      id: workout.id,
      slug: workout.slug,
      name: parseLocalizedText(workout.name) || { ar: '', en: '' },
      description: parseLocalizedText(workout.description),
      type: workout.type as 'circuit' | 'super_set',
      executionMode: workout.executionMode as 'sequential' | 'alternating',
      rounds: workout.rounds,
      repsPerSwitch: workout.repsPerSwitch ?? undefined,
      restBetweenSwitchMs: workout.restBetweenSwitchMs ?? undefined,
      restBetweenExercisesMs: workout.restBetweenExercisesMs ?? undefined,
      restBetweenRoundsMs: workout.restBetweenRoundsMs,
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
