/**
 * Workouts Service
 * =================
 * 
 * Service for workout (Super Set / Circuit) CRUD operations.
 */

import { Prisma } from '@prisma/client';
import { getPrisma } from '@/lib/prisma/client';
import { expandPerSetValues, parseNumberList } from '@/lib/per-set-values';
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
  WorkoutPhaseInput,
  WorkoutExport,
  WorkoutExerciseExport,
  WorkoutPhaseExport,
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

function toInputJson(value: unknown) {
  return value as Prisma.InputJsonValue;
}

function toInputJsonOrNull(value: unknown): Prisma.InputJsonValue | Prisma.JsonNullValueInput {
  if (value === null || value === undefined) {
    return Prisma.JsonNull;
  }
  return value as Prisma.InputJsonValue;
}

interface NormalizedWorkoutExerciseFields {
  sets: number;
  targetReps: number | null;
  targetRepsPerSet: number[] | null;
  targetDuration: number | null;
  restBetweenSetsMs: number;
  restBetweenSetsPerSetMs: number[] | null;
  weightKg: number | null;
  weightPerSet: number[] | null;
}

function resolveRawPerSetList(
  perSet: number[] | undefined,
  legacyScalar: number | undefined | null
): number[] | undefined {
  if (perSet && perSet.length > 0) return perSet;
  if (legacyScalar !== undefined && legacyScalar !== null) return [legacyScalar];
  return undefined;
}

function normalizeWorkoutExerciseFields(ex: WorkoutExerciseInput): NormalizedWorkoutExerciseFields {
  const sets = ex.sets ?? 1;
  const targetRepsPerSet = resolveRawPerSetList(ex.targetRepsPerSet, ex.targetReps);
  const restBetweenSetsPerSetMs = resolveRawPerSetList(
    ex.restBetweenSetsPerSetMs,
    ex.restBetweenSetsMs ?? DEFAULT_REST_TIMES.restBetweenSetsMs
  );
  const weightPerSet = resolveRawPerSetList(ex.weightPerSet, ex.weightKg);

  const expandedReps = expandPerSetValues(targetRepsPerSet, sets);
  const expandedRest = expandPerSetValues(
    restBetweenSetsPerSetMs,
    sets,
    DEFAULT_REST_TIMES.restBetweenSetsMs
  );
  const expandedWeight = expandPerSetValues(weightPerSet, sets);

  return {
    sets,
    targetReps: expandedReps?.[0] ?? null,
    targetRepsPerSet: targetRepsPerSet ?? null,
    targetDuration: ex.targetDuration ?? null,
    restBetweenSetsMs: expandedRest?.[0] ?? DEFAULT_REST_TIMES.restBetweenSetsMs,
    restBetweenSetsPerSetMs: restBetweenSetsPerSetMs ?? null,
    weightKg: expandedWeight?.[0] ?? null,
    weightPerSet: weightPerSet ?? null,
  };
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
              description: true,
              status: true,
              categoryId: true,
              supportsWeight: true,
              defaultWeight: true,
              loadCapability: true,
              familyKey: true,
              countingMethod: {
                select: {
                  code: true,
                  name: true,
                },
              },
              category: {
                select: {
                  id: true,
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
    },
  },
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
          categoryId: true,
          supportsWeight: true,
          defaultWeight: true,
          loadCapability: true,
          familyKey: true,
          countingMethod: {
            select: {
              code: true,
              name: true,
            },
          },
          category: {
            select: {
              id: true,
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
  level: {
    select: { id: true, number: true, code: true, name: true },
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
          level: {
            select: { id: true, number: true, code: true, name: true },
          },
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
          levelId: data.levelId ?? undefined,
          estimatedDurationMin: data.estimatedDurationMin ?? undefined,
          tags: data.tags ?? undefined,
          isFeatured: data.isFeatured ?? false,
          status: 'draft',
          createdBy,
          updatedBy: createdBy,
        },
      });

      if (data.phases && data.phases.length > 0) {
        await this.createWorkoutPhases(tx, created.id, data.phases);
      } else {
        const mainPhaseInstance = await this.createDefaultMainPhaseInstance(tx, created.id);
        if (data.exercises && data.exercises.length > 0) {
          await this.createWorkoutExercises(tx, created.id, mainPhaseInstance.id, data.exercises);
        }
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
    workoutTemplatePhaseId: string,
    exercises: WorkoutExerciseInput[]
  ) {
    await prisma.workoutTemplateExercise.createMany({
      data: exercises.map((ex, index) => {
        const normalized = normalizeWorkoutExerciseFields(ex);
        return {
          workoutTemplateId,
          workoutTemplatePhaseId,
          exerciseId: ex.exerciseId,
          variantIndex: ex.variantIndex ?? 0,
          targetReps: normalized.targetReps ?? undefined,
          targetRepsPerSet: toInputJsonOrNull(normalized.targetRepsPerSet),
          targetDuration: normalized.targetDuration ?? undefined,
          sets: normalized.sets,
          restBetweenSetsMs: normalized.restBetweenSetsMs,
          restBetweenSetsPerSetMs: toInputJsonOrNull(normalized.restBetweenSetsPerSetMs),
          restAfterExerciseMs: ex.restAfterExerciseMs ?? DEFAULT_REST_TIMES.restAfterExerciseMs,
          weightKg: normalized.weightKg ?? undefined,
          weightPerSet: toInputJsonOrNull(normalized.weightPerSet),
          notes: (ex.notes as object) || undefined,
          sortOrder: ex.sortOrder ?? index,
        };
      }),
    });
  },

  /**
   * Create workout phases and their nested exercises.
   */
  async createWorkoutPhases(
    prisma: Prisma.TransactionClient,
    workoutTemplateId: string,
    phases: WorkoutPhaseInput[]
  ) {
    for (let phaseIndex = 0; phaseIndex < phases.length; phaseIndex++) {
      const phase = phases[phaseIndex];
      const createdPhase = await prisma.workoutTemplatePhase.create({
        data: {
          workoutTemplateId,
          phaseId: phase.phaseId,
          sortOrder: phase.sortOrder ?? phaseIndex,
          nameOverride: toInputJsonOrNull(phase.nameOverride),
          canSkipOverride: phase.canSkipOverride ?? null,
          canContinueOverride: phase.canContinueOverride ?? null,
          maxContinueTimeMsOverride: phase.maxContinueTimeMsOverride ?? null,
        },
      });

      if (phase.exercises && phase.exercises.length > 0) {
        await this.createWorkoutExercises(prisma, workoutTemplateId, createdPhase.id, phase.exercises);
      }
    }
  },

  async replaceWorkoutPhases(
    prisma: Prisma.TransactionClient,
    workoutTemplateId: string,
    phases: WorkoutPhaseInput[],
  ) {
    await prisma.workoutTemplatePhase.deleteMany({ where: { workoutTemplateId } });
    if (phases.length > 0) {
      await this.createWorkoutPhases(prisma, workoutTemplateId, phases);
    }
  },

  async replacePhaseExercises(
    prisma: Prisma.TransactionClient,
    workoutTemplateId: string,
    workoutTemplatePhaseId: string,
    exercises: WorkoutExerciseInput[],
  ) {
    await prisma.workoutTemplateExercise.deleteMany({
      where: { workoutTemplateId, workoutTemplatePhaseId },
    });
    if (exercises.length > 0) {
      await this.createWorkoutExercises(prisma, workoutTemplateId, workoutTemplatePhaseId, exercises);
    }
  },

  /**
   * Ensure flat legacy payloads are wrapped in one reusable Main phase.
   */
  async createDefaultMainPhaseInstance(prisma: Prisma.TransactionClient, workoutTemplateId: string) {
    const mainPhase = await prisma.workoutPhase.upsert({
      where: { slug: 'main' },
      update: { deletedAt: null, isActive: true },
      create: {
        slug: 'main',
        name: toInputJson({ en: 'Main Workout', ar: 'التمرين الأساسي' }),
        description: toInputJson({ en: 'Primary training work.', ar: 'الجزء الأساسي من التمرين.' }),
        role: 'MAIN',
        canSkip: false,
        canContinue: true,
        sortOrder: 10,
      },
    });

    return prisma.workoutTemplatePhase.create({
      data: {
        workoutTemplateId,
        phaseId: mainPhase.id,
        sortOrder: 0,
      },
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
    if (data.levelId !== undefined) updateData.levelId = data.levelId;
    if (data.estimatedDurationMin !== undefined) updateData.estimatedDurationMin = data.estimatedDurationMin;
    if (data.tags !== undefined) updateData.tags = data.tags;
    if (data.isFeatured !== undefined) updateData.isFeatured = data.isFeatured;
    if (data.status !== undefined) updateData.status = data.status;

    await prisma.$transaction(async (tx) => {
      await tx.workoutTemplate.update({
        where: { id },
        data: updateData,
      });

      if (data.phases !== undefined) {
        await tx.workoutTemplatePhase.deleteMany({
          where: { workoutTemplateId: id },
        });

        if (data.phases.length > 0) {
          await this.createWorkoutPhases(tx, id, data.phases);
        }
      } else if (data.exercises !== undefined) {
        await tx.workoutTemplatePhase.deleteMany({
          where: { workoutTemplateId: id },
        });

        const mainPhaseInstance = await this.createDefaultMainPhaseInstance(tx, id);
        if (data.exercises.length > 0) {
          await this.createWorkoutExercises(tx, id, mainPhaseInstance.id, data.exercises);
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

    const phases: WorkoutPhaseInput[] =
      original.phases.length > 0
        ? original.phases.map((phase, phaseIndex) => ({
            phaseId: phase.phaseId,
            sortOrder: phase.sortOrder ?? phaseIndex,
            nameOverride: parseLocalizedText(phase.nameOverride),
            canSkipOverride: phase.canSkipOverride ?? undefined,
            canContinueOverride: phase.canContinueOverride ?? undefined,
            maxContinueTimeMsOverride: phase.maxContinueTimeMsOverride ?? undefined,
            exercises: phase.exercises.map((ex, index) => {
              const normalized = normalizeWorkoutExerciseFields({
                exerciseId: ex.exerciseId,
                variantIndex: ex.variantIndex,
                targetReps: ex.targetReps ?? undefined,
                targetRepsPerSet: parseNumberList(ex.targetRepsPerSet) ?? undefined,
                targetDuration: ex.targetDuration ?? undefined,
                sets: ex.sets ?? 1,
                restBetweenSetsMs: ex.restBetweenSetsMs ?? undefined,
                restBetweenSetsPerSetMs: parseNumberList(ex.restBetweenSetsPerSetMs) ?? undefined,
                restAfterExerciseMs: ex.restAfterExerciseMs ?? undefined,
                weightKg: ex.weightKg ?? undefined,
                weightPerSet: parseNumberList(ex.weightPerSet) ?? undefined,
              });
              return {
                exerciseId: ex.exerciseId,
                variantIndex: ex.variantIndex,
                targetReps: normalized.targetReps ?? undefined,
                targetRepsPerSet: normalized.targetRepsPerSet ?? undefined,
                targetDuration: normalized.targetDuration ?? undefined,
                sets: normalized.sets,
                restBetweenSetsMs: normalized.restBetweenSetsMs,
                restBetweenSetsPerSetMs: normalized.restBetweenSetsPerSetMs ?? undefined,
                restAfterExerciseMs: ex.restAfterExerciseMs ?? DEFAULT_REST_TIMES.restAfterExerciseMs,
                weightPerSet: normalized.weightPerSet ?? undefined,
                notes: parseLocalizedText(ex.notes),
                sortOrder: index,
              };
            }),
          }))
        : [];

    return this.create(
      {
        name: newName,
        description: parseLocalizedText(original.description),
        coverImageUrl: original.coverImageUrl ?? undefined,
        levelId: original.levelId ?? undefined,
        estimatedDurationMin: original.estimatedDurationMin ?? undefined,
        tags: (original.tags as string[]) || undefined,
        isFeatured: false,
        phases,
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

    const phases: WorkoutPhaseExport[] = workout.phases.map((templatePhase) => {
      const phase = templatePhase.phase;
      const exercises = templatePhase.exercises.map((we) => this.buildWorkoutExerciseExport(we));

      return {
        id: templatePhase.id,
        phaseId: phase.id,
        slug: phase.slug,
        role: phase.role,
        name: parseLocalizedText(templatePhase.nameOverride) || parseLocalizedText(phase.name) || { ar: '', en: '' },
        description: parseLocalizedText(phase.description),
        canSkip: templatePhase.canSkipOverride ?? phase.canSkip,
        canContinue: templatePhase.canContinueOverride ?? phase.canContinue,
        maxContinueTimeMs: templatePhase.maxContinueTimeMsOverride ?? phase.maxContinueTimeMs ?? undefined,
        sortOrder: templatePhase.sortOrder,
        exercises,
      };
    });

    const exercises: WorkoutExerciseExport[] =
      phases.length > 0
        ? phases.flatMap((phase) => phase.exercises)
        : workout.exercises.map((we) => this.buildWorkoutExerciseExport(we));

    return {
      id: workout.id,
      slug: workout.slug,
      name: parseLocalizedText(workout.name) || { ar: '', en: '' },
      description: parseLocalizedText(workout.description),
      coverImageUrl: workout.coverImageUrl ?? undefined,
      levelId: workout.levelId ?? undefined,
      level: workout.level
        ? {
            id: workout.level.id,
            number: workout.level.number,
            code: workout.level.code,
            name: parseLocalizedText(workout.level.name) || { ar: '', en: '' },
          }
        : null,
      estimatedDurationMin: workout.estimatedDurationMin ?? undefined,
      tags: (workout.tags as string[]) || undefined,
      isFeatured: workout.isFeatured ?? false,
      exercises,
      phases,
      updatedAt: workout.updatedAt.toISOString(),
    };
  },

  buildWorkoutExerciseExport(we: {
    exercise: { slug: string; name?: unknown };
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
  }): WorkoutExerciseExport {
    const sets = we.sets ?? 1;
    const rawReps = parseNumberList(we.targetRepsPerSet) ?? (we.targetReps != null ? [we.targetReps] : undefined);
    const rawRest =
      parseNumberList(we.restBetweenSetsPerSetMs) ??
      [we.restBetweenSetsMs ?? DEFAULT_REST_TIMES.restBetweenSetsMs];
    const rawWeight =
      parseNumberList(we.weightPerSet) ?? (we.weightKg != null ? [we.weightKg] : undefined);

    const targetRepsPerSet = expandPerSetValues(rawReps, sets);
    const restBetweenSetsPerSetMs = expandPerSetValues(
      rawRest,
      sets,
      DEFAULT_REST_TIMES.restBetweenSetsMs
    );
    const weightPerSet = expandPerSetValues(rawWeight, sets);

    return {
      exercise: we.exercise.slug,
      name: parseLocalizedText(we.exercise.name) || { ar: we.exercise.slug, en: we.exercise.slug },
      variantIndex: we.variantIndex,
      targetReps: targetRepsPerSet?.[0] ?? we.targetReps ?? undefined,
      targetRepsPerSet: targetRepsPerSet ?? undefined,
      targetDuration: we.targetDuration ?? undefined,
      sets,
      restBetweenSetsMs: restBetweenSetsPerSetMs?.[0] ?? DEFAULT_REST_TIMES.restBetweenSetsMs,
      restBetweenSetsPerSetMs: restBetweenSetsPerSetMs ?? undefined,
      restAfterExerciseMs: we.restAfterExerciseMs ?? DEFAULT_REST_TIMES.restAfterExerciseMs,
      weightPerSet: weightPerSet ?? undefined,
      notes: parseLocalizedText(we.notes),
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
        level: {
          select: { id: true, number: true, code: true, name: true },
        },
        phases: {
          orderBy: { sortOrder: 'asc' },
          include: {
            phase: true,
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
        },
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

    const mapTrainingExercise = (we: (typeof workout.exercises)[number]) => ({
      workoutExerciseId: we.id,
      sortOrder: we.sortOrder,
      variantIndex: we.variantIndex,
      targetReps: we.targetReps,
      targetRepsPerSet:
        expandPerSetValues(
          parseNumberList(we.targetRepsPerSet) ?? (we.targetReps != null ? [we.targetReps] : undefined),
          we.sets ?? 1
        ) ?? null,
      targetDuration: we.targetDuration,
      sets: we.sets,
      restBetweenSetsMs: we.restBetweenSetsMs,
      restBetweenSetsPerSetMs:
        expandPerSetValues(
          parseNumberList(we.restBetweenSetsPerSetMs) ?? [we.restBetweenSetsMs],
          we.sets ?? 1,
          DEFAULT_REST_TIMES.restBetweenSetsMs
        ) ?? null,
      restAfterExerciseMs: we.restAfterExerciseMs,
      weightPerSet:
        expandPerSetValues(
          parseNumberList(we.weightPerSet) ?? (we.weightKg != null ? [we.weightKg] : undefined),
          we.sets ?? 1
        ) ?? null,
      notes: (we.notes ?? null) as Record<string, string> | null,
      exercise: {
        id: we.exercise.id,
        slug: we.exercise.slug,
        name: we.exercise.name as Record<string, string>,
        instructions: (we.exercise.instructions ?? null) as Record<string, string> | null,
        countingMethodId: we.exercise.countingMethodId,
        repCountingConfig: we.exercise.repCountingConfig,
        supportsWeight: we.exercise.supportsWeight,
        defaultWeight: we.exercise.defaultWeight,
        loadCapability: we.exercise.loadCapability,
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
    });

    const phaseConfigs = workout.phases.map((templatePhase) => ({
      id: templatePhase.id,
      phaseId: templatePhase.phase.id,
      slug: templatePhase.phase.slug,
      role: templatePhase.phase.role,
      name: (templatePhase.nameOverride ?? templatePhase.phase.name) as Record<string, string>,
      description: (templatePhase.phase.description ?? null) as Record<string, string> | null,
      canSkip: templatePhase.canSkipOverride ?? templatePhase.phase.canSkip,
      canContinue: templatePhase.canContinueOverride ?? templatePhase.phase.canContinue,
      maxContinueTimeMs: templatePhase.maxContinueTimeMsOverride ?? templatePhase.phase.maxContinueTimeMs,
      sortOrder: templatePhase.sortOrder,
      exercises: templatePhase.exercises.map((we) => mapTrainingExercise(we)),
    }));

    const flatExercises =
      phaseConfigs.length > 0
        ? phaseConfigs.flatMap((phase) => phase.exercises)
        : workout.exercises.map((we) => mapTrainingExercise(we));

    return {
      id: workout.id,
      slug: workout.slug,
      name: workout.name as Record<string, string>,
      description: (workout.description ?? null) as Record<string, string> | null,
      coverImageUrl: workout.coverImageUrl,
      levelId: workout.levelId,
      level: workout.level
        ? {
            id: workout.level.id,
            number: workout.level.number,
            code: workout.level.code,
            name: workout.level.name as Record<string, string>,
          }
        : null,
      estimatedDurationMin: workout.estimatedDurationMin,
      exercises: flatExercises,
      phases: phaseConfigs,
    };
  },
};
