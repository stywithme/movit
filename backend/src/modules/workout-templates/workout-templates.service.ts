/**
 * Workouts Service
 * =================
 * 
 * Service for workout (Super Set / Circuit) CRUD operations.
 */

import { Prisma } from '@prisma/client';
import { getPrisma } from '@/lib/prisma/client';
import { DEFAULT_REST_TIMES } from './workout-templates.types';

const PHASE_MIGRATION: Record<string, string> = {
  start: 'top',
  hold: 'count',
  count: 'count',
  idle: 'all',
};
function migrateActivePhases(phases: string[]): string[] {
  return [...new Set(phases.map((p) => PHASE_MIGRATION[p] ?? PHASE_MIGRATION[p.toLowerCase()] ?? p))];
}
import type {
  CreateWorkoutInput,
  UpdateWorkoutInput,
  WorkoutListFilters,
  WorkoutExerciseInput,
  WorkoutExport,
  WorkoutExerciseExport,
} from './workout-templates.types';

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

    if (filters?.isFeatured === true) {
      where.isFeatured = true;
    } else if (filters?.isFeatured === false) {
      where.isFeatured = false;
    }

    const [workouts, total] = await Promise.all([
      prisma.workoutTemplate.findMany({
        where,
        skip,
        take: limit,
        orderBy: [{ isFeatured: 'desc' }, { createdAt: 'desc' }],
        include: {
          _count: {
            select: { exercises: true },
          },
        },
      }),
      prisma.workoutTemplate.count({ where }),
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
    return prisma.workoutTemplate.findUnique({
      where: { id, deletedAt: null },
      include: workoutFullInclude,
    });
  },

  /**
   * Get workout by slug
   */
  async getBySlug(slug: string) {
    const prisma = await getPrisma();
    return prisma.workoutTemplate.findUnique({
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

    const workout = await prisma.$transaction(async (tx) => {
      const created = await tx.workoutTemplate.create({
        data: {
          name: data.name as object,
          description: (data.description as object) || undefined,
          slug,
          coverImageUrl: data.coverImageUrl ?? undefined,
          difficulty: data.difficulty ?? 'beginner',
          estimatedDurationMin: data.estimatedDurationMin ?? undefined,
          tags: data.tags ?? undefined,
          isFeatured: data.isFeatured ?? false,
          status: 'draft',
          createdBy,
          updatedBy: createdBy,
        },
      });

      // Create workout exercises if provided
      if (data.exercises && data.exercises.length > 0) {
        await this.createWorkoutExercises(tx, created.id, data.exercises);
      }

      return created;
    });

    return this.getById(workout.id);
  },

  /**
   * Create workout exercises
   */
  async createWorkoutExercises(
    prisma: Prisma.TransactionClient,
    workoutTemplateId: string,
    exercises: WorkoutExerciseInput[]
  ) {
    await prisma.workoutTemplateExercise.createMany({
      data: exercises.map((ex, index) => ({
        workoutTemplateId,
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
    if (data.isFeatured !== undefined) updateData.isFeatured = data.isFeatured;
    if (data.status !== undefined) updateData.status = data.status;

    await prisma.$transaction(async (tx) => {
      await tx.workoutTemplate.update({
        where: { id },
        data: updateData,
      });

      // Replace exercises if provided (delete + recreate, atomically)
      if (data.exercises !== undefined) {
        await tx.workoutTemplateExercise.deleteMany({
          where: { workoutTemplateId: id },
        });

        if (data.exercises.length > 0) {
          await this.createWorkoutExercises(tx, id, data.exercises);
        }
      }
    });

    return this.getById(id);
  },

  /**
   * Publish a workout
   */
  async publish(id: string, updatedBy?: string) {
    const prisma = await getPrisma();
    return prisma.workoutTemplate.update({
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
    return prisma.workoutTemplate.update({
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
    return prisma.workoutTemplate.update({
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
      weightPerSet: ex.weightPerSet ?? undefined,
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
        isFeatured: false,
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
    return prisma.workoutTemplate.findMany({
      where: {
        status: 'published',
        deletedAt: null,
      },
      orderBy: [{ isFeatured: 'desc' }, { updatedAt: 'desc' }],
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
      isFeatured: workout.isFeatured ?? false,
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

  /**
   * Get full training configuration for a workout.
   * Returns everything the mobile training engine needs to run the workout:
   * exercise data, pose variants, tracking config, position checks, etc.
   */
  async getTrainingConfig(workoutId: string) {
    const prisma = await getPrisma();

    const workout = await prisma.workoutTemplate.findUnique({
      where: { id: workoutId, status: 'published', deletedAt: null },
      include: {
        exercises: {
          orderBy: { sortOrder: 'asc' },
          include: {
            exercise: {
              include: {
                poseVariants: {
                  orderBy: { sortOrder: 'asc' },
                  include: {
                    posePosition: {
                      select: { id: true, code: true, name: true, postures: true, directions: true },
                    },
                    difficultyLevels: {
                      orderBy: { sortOrder: 'asc' },
                      include: { difficultyType: { select: { code: true, name: true } } },
                    },
                    positionChecks: {
                      orderBy: { sortOrder: 'asc' },
                    },
                    messageAssignments: {
                      include: { message: { select: { content: true, category: true, context: true } } },
                    },
                  },
                },
                attributes: {
                  include: { attributeValue: { select: { code: true, name: true } } },
                },
              },
            },
          },
        },
      },
    });

    if (!workout) return null;

    return {
      id: workout.id,
      slug: workout.slug,
      name: workout.name as Record<string, string>,
      description: (workout.description ?? null) as Record<string, string> | null,
      coverImageUrl: workout.coverImageUrl,
      difficulty: workout.difficulty,
      estimatedDurationMin: workout.estimatedDurationMin,
      exercises: workout.exercises.map((we) => ({
        workoutExerciseId: we.id,
        sortOrder: we.sortOrder,
        variantIndex: we.variantIndex,
        difficulty: we.difficulty,
        targetReps: we.targetReps,
        targetDuration: we.targetDuration,
        sets: we.sets,
        restBetweenSetsMs: we.restBetweenSetsMs,
        restAfterExerciseMs: we.restAfterExerciseMs,
        weightKg: we.weightKg,
        weightPerSet: (we.weightPerSet as number[]) ?? null,
        notes: (we.notes ?? null) as Record<string, string> | null,
        exercise: {
          id: we.exercise.id,
          slug: we.exercise.slug,
          name: we.exercise.name as Record<string, string>,
          instructions: (we.exercise.instructions ?? null) as Record<string, string> | null,
          countingMethodId: we.exercise.countingMethodId,
          repCountingConfig: we.exercise.repCountingConfig,
          supportsWeight: we.exercise.supportsWeight,
          isBilateral: we.exercise.isBilateral,
          bilateralConfig: we.exercise.bilateralConfig,
          reportMetrics: we.exercise.reportMetrics,
          poseVariants: we.exercise.poseVariants.map((pv) => ({
            id: pv.id,
            name: pv.name as Record<string, string>,
            sortOrder: pv.sortOrder,
            trackedJointsConfig: pv.trackedJointsConfig,
            posePosition: pv.posePosition,
            difficultyLevels: pv.difficultyLevels.map((dl) => ({
              id: dl.id,
              difficultyCode: dl.difficultyType.code,
              difficultyName: dl.difficultyType.name as Record<string, string>,
              repCountingConfig: dl.repCountingConfig,
              phases: dl.phases,
              romConfig: dl.romConfig,
            })),
            positionChecks: pv.positionChecks.map((pc) => ({
              checkId: pc.checkId,
              type: pc.type,
              landmarks: pc.landmarks,
              condition: pc.condition,
              activePhases: migrateActivePhases(pc.activePhases as string[]),
              errorMessage: pc.errorMessage as Record<string, string>,
              severity: pc.severity,
              cooldownMs: pc.cooldownMs,
              minErrorFrames: pc.minErrorFrames,
            })),
            feedbackMessages: pv.messageAssignments.map((ma) => ({
              target: ma.target,
              context: ma.context,
              jointCode: ma.jointCode,
              zone: ma.zone,
              checkId: ma.checkId,
              content: ma.message.content as Record<string, string>,
              category: ma.message.category,
            })),
          })),
          attributes: we.exercise.attributes.map((a) => ({
            code: a.attributeValue.code,
            name: a.attributeValue.name as Record<string, string>,
          })),
        },
      })),
    };
  },
};
