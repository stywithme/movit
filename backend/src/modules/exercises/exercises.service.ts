/**
 * Exercise Service - State-Based System
 * ======================================
 * 
 * Service for exercise CRUD operations.
 * Updated: State-based system (no difficulty levels)
 */

import { getPrisma } from '@/lib/prisma/client';
import { deleteByUrl } from '@/lib/storage';
import type { CountingMethodCode, PhaseName } from '@/lib/types/localized';
import type {
  TrackedJoint,
  PositionCheckInput,
  FeedbackMessageAssignmentInput,
  RepCountingConfig,
  BilateralConfigInput,
} from './exercises.types';

// ============================================
// TYPES
// ============================================

interface LocalizedText {
  ar: string;
  en: string;
  audioAr?: string;
  audioEn?: string;
}

interface PoseVariantInput {
  id?: string;
  tempId?: string;
  name: LocalizedText;
  description?: LocalizedText;
  posePositionId: string;
  trackedJointsConfig?: TrackedJoint[];
  positionChecks?: PositionCheckInput[];
  messageAssignments?: FeedbackMessageAssignmentInput[];
  sortOrder?: number;
}

interface CreateExerciseInput {
  name: LocalizedText;
  description?: LocalizedText;
  instructions?: LocalizedText;
  categoryId: string;
  countingMethodId: string;
  imageUrl?: string;
  slug?: string;
  muscles?: string[];
  equipment?: string[];
  tags?: string[];
  repCountingConfig?: RepCountingConfig;
  poseVariants?: PoseVariantInput[];
  // Weight configuration
  supportsWeight?: boolean;
  minWeight?: number;
  maxWeight?: number;
  defaultWeight?: number;
  // Report metrics configuration
  reportMetrics?: {
    primary?: string[];
    optional?: string[];
    excluded?: string[];
  };
  // Bilateral configuration
  bilateralConfig?: BilateralConfigInput;
}

interface UpdateExerciseInput extends Partial<CreateExerciseInput> {
  status?: 'draft' | 'published';
}

// ============================================
// HELPERS
// ============================================

function generateSlug(name: { en?: string; ar?: string }): string {
  const baseName = name.en || name.ar || 'exercise';
  return baseName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    + '-' + Date.now().toString(36);
}

// ============================================
// SERVICE
// ============================================

export const exerciseService = {
  /**
   * List all exercises with optional filters
   */
  async list(filters?: {
    status?: 'draft' | 'published';
    categoryId?: string;
    search?: string;
    page?: number;
    limit?: number;
  }) {
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

    if (filters?.categoryId) {
      where.categoryId = filters.categoryId;
    }

    if (filters?.search) {
      where.OR = [
        { name: { path: ['en'], string_contains: filters.search } },
        { name: { path: ['ar'], string_contains: filters.search } },
      ];
    }

    const [exercises, total] = await Promise.all([
      prisma.exercise.findMany({
        where,
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
        include: {
          category: true,
          countingMethod: true,
          media: {
            where: { isPrimary: true },
            take: 1,
          },
          _count: {
            select: { poseVariants: true },
          },
        },
      }),
      prisma.exercise.count({ where }),
    ]);

    return {
      exercises,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    };
  },

  /**
   * Get a single exercise by ID with all related data
   */
  async getById(id: string) {
    const prisma = await getPrisma();
    return prisma.exercise.findUnique({
      where: { id, deletedAt: null },
      include: {
        category: true,
        countingMethod: true,
        attributes: {
          include: {
            attributeValue: {
              include: {
                attribute: true,
              },
            },
          },
        },
        media: {
          orderBy: { sortOrder: 'asc' },
        },
        poseVariants: {
          orderBy: { sortOrder: 'asc' },
          include: {
            posePosition: {
              include: {
                joints: {
                  include: {
                    joint: true,
                  },
                },
              },
            },
            positionChecks: {
              orderBy: { sortOrder: 'asc' },
            },
            messageAssignments: {
              orderBy: { sortOrder: 'asc' },
              include: {
                message: true,
              },
            },
          },
        },
      },
    });
  },

  /**
   * Create a new exercise
   */
  async create(data: CreateExerciseInput, createdBy?: string) {
    const prisma = await getPrisma();
    const slug = data.slug || generateSlug(data.name);

    const exercise = await prisma.exercise.create({
      data: {
        name: data.name as object,
        description: (data.description as object) || undefined,
        instructions: (data.instructions as object) || undefined,
        categoryId: data.categoryId,
        countingMethodId: data.countingMethodId,
        repCountingConfig: (data.repCountingConfig as object) || undefined,
        slug,
        status: 'draft',
        createdBy,
        updatedBy: createdBy,
        // Bilateral configuration
        isBilateral: Boolean(data.bilateralConfig),
        bilateralConfig: data.bilateralConfig ? (data.bilateralConfig as object) : undefined,
        // Weight configuration
        supportsWeight: data.supportsWeight ?? false,
        minWeight: data.minWeight ?? null,
        maxWeight: data.maxWeight ?? null,
        defaultWeight: data.defaultWeight ?? null,
        // Report metrics configuration
        reportMetrics: data.reportMetrics ? (data.reportMetrics as object) : undefined,
        media: data.imageUrl
          ? {
            create: {
              type: 'image',
              url: data.imageUrl,
              isPrimary: true,
              sortOrder: 1,
            },
          }
          : undefined,
      },
      include: {
        category: true,
        countingMethod: true,
      },
    });

    // Add muscles, equipment, and tags (deduplicated)
    const attributeIds = [
      ...new Set([
        ...(data.muscles || []),
        ...(data.equipment || []),
        ...(data.tags || []),
      ]),
    ];

    if (attributeIds.length > 0) {
      await prisma.exerciseAttribute.createMany({
        data: attributeIds.map((attributeValueId) => ({
          exerciseId: exercise.id,
          attributeValueId,
        })),
        skipDuplicates: true,
      });
    }

    // Create pose variants if provided
    if (data.poseVariants && data.poseVariants.length > 0) {
      for (let pvIndex = 0; pvIndex < data.poseVariants.length; pvIndex++) {
        const pv = data.poseVariants[pvIndex];
        await this.createPoseVariant(prisma, exercise.id, pv, pvIndex);
      }
    }

    return this.getById(exercise.id);
  },

  /**
   * Create a pose variant with all nested data
   */
  async createPoseVariant(
    prisma: Awaited<ReturnType<typeof getPrisma>>,
    exerciseId: string,
    pv: PoseVariantInput,
    sortOrder: number
  ) {
    // Create pose variant
    const poseVariant = await prisma.poseVariant.create({
      data: {
        exerciseId,
        posePositionId: pv.posePositionId,
        name: pv.name as object,
        description: (pv.description as object) || undefined,
        trackedJointsConfig: (pv.trackedJointsConfig as object) || undefined,
        sortOrder: pv.sortOrder ?? sortOrder + 1,
      },
    });

    // Create position checks (errorMessage includes audio URLs)
    if (pv.positionChecks && pv.positionChecks.length > 0) {
      await prisma.positionCheck.createMany({
        data: pv.positionChecks.map((pc, idx) => ({
          poseVariantId: poseVariant.id,
          checkId: pc.checkId,
          type: pc.type,
          landmarks: pc.landmarks as object,
          condition: pc.condition as object,
          activePhases: pc.activePhases,
          errorMessage: {
            ar: pc.errorMessage.ar,
            en: pc.errorMessage.en,
            audioAr: pc.errorMessage.audioAr || undefined,
            audioEn: pc.errorMessage.audioEn || undefined,
          },
          severity: pc.severity || 'warning',
          cooldownMs: pc.cooldownMs ?? 2000,
          minErrorFrames: pc.minErrorFrames ?? 3,
          sortOrder: pc.sortOrder ?? idx + 1,
        })),
      });
    }

    // Create message assignments (library-based, optional)
    const assignmentsToCreate = [...(pv.messageAssignments || [])];

    // Add assignments from linked position checks
    if (pv.positionChecks && pv.positionChecks.length > 0) {
      pv.positionChecks.forEach((pc, idx) => {
        if (pc.messageId) {
          assignmentsToCreate.push({
            messageId: pc.messageId,
            target: 'position',
            checkId: pc.checkId,
            sortOrder: (pc.sortOrder ?? idx + 1) + 100, // Ensure unique sort order
          });
        }
      });
    }

    if (assignmentsToCreate.length > 0) {
      await prisma.feedbackMessageAssignment.createMany({
        data: assignmentsToCreate.map((assignment, idx) => ({
          poseVariantId: poseVariant.id,
          messageId: assignment.messageId,
          target: assignment.target,
          context: assignment.context || null,
          jointCode: assignment.jointCode || null,
          zone: assignment.zone || null,
          checkId: assignment.checkId || null,
          sortOrder: assignment.sortOrder ?? idx + 1,
        })),
      });

      await this.touchExercise(prisma, exerciseId);
    }

    return poseVariant;
  },

  /**
   * Update an exercise
   */
  async update(id: string, data: UpdateExerciseInput, updatedBy?: string) {
    const prisma = await getPrisma();
    const existingMedia = data.imageUrl !== undefined
      ? await prisma.exerciseMedia.findFirst({
        where: {
          exerciseId: id,
          type: 'image',
          isPrimary: true,
        },
        select: { url: true },
      })
      : null;

    const updateData: Record<string, unknown> = {
      updatedBy,
    };

    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined) updateData.description = data.description;
    if (data.instructions !== undefined) updateData.instructions = data.instructions;
    if (data.categoryId !== undefined) updateData.categoryId = data.categoryId;
    if (data.countingMethodId !== undefined) updateData.countingMethodId = data.countingMethodId;
    if (data.repCountingConfig !== undefined) updateData.repCountingConfig = data.repCountingConfig;
    if (data.status !== undefined) updateData.status = data.status;
    if (data.bilateralConfig !== undefined) {
      updateData.isBilateral = Boolean(data.bilateralConfig);
      updateData.bilateralConfig = data.bilateralConfig ? (data.bilateralConfig as object) : null;
    }
    // Weight configuration
    if (data.supportsWeight !== undefined) updateData.supportsWeight = data.supportsWeight;
    if (data.minWeight !== undefined) updateData.minWeight = data.minWeight;
    if (data.maxWeight !== undefined) updateData.maxWeight = data.maxWeight;
    if (data.defaultWeight !== undefined) updateData.defaultWeight = data.defaultWeight;
    // Report metrics configuration
    if (data.reportMetrics !== undefined) updateData.reportMetrics = data.reportMetrics;

    await prisma.exercise.update({
      where: { id },
      data: updateData,
    });

    if (data.imageUrl !== undefined) {
      await prisma.exerciseMedia.deleteMany({
        where: {
          exerciseId: id,
          type: 'image',
          isPrimary: true,
        },
      });

      if (data.imageUrl) {
        await prisma.exerciseMedia.create({
          data: {
            exerciseId: id,
            type: 'image',
            url: data.imageUrl,
            isPrimary: true,
            sortOrder: 1,
          },
        });
      }

      if (existingMedia?.url && existingMedia.url !== data.imageUrl) {
        await deleteByUrl(existingMedia.url);
      }
    }

    // Update attributes if provided
    const attributeIds = [
      ...new Set([
        ...(data.muscles || []),
        ...(data.equipment || []),
        ...(data.tags || []),
      ]),
    ];

    if (data.muscles || data.equipment || data.tags) {
      await prisma.exerciseAttribute.deleteMany({
        where: { exerciseId: id },
      });

      if (attributeIds.length > 0) {
        await prisma.exerciseAttribute.createMany({
          data: attributeIds.map((attributeValueId) => ({
            exerciseId: id,
            attributeValueId,
          })),
          skipDuplicates: true,
        });
      }
    }

    // Update pose variants if provided (transactional upsert)
    if (data.poseVariants !== undefined) {
      await prisma.$transaction(async (tx) => {
        const existingVariants = await tx.poseVariant.findMany({
          where: { exerciseId: id },
          include: { positionChecks: true, messageAssignments: true },
        });

        const incomingPositionIds = data.poseVariants!.map(pv => pv.posePositionId);

        // Remove variants not in incoming data
        const variantsToDelete = existingVariants.filter(
          v => !incomingPositionIds.includes(v.posePositionId)
        );
        if (variantsToDelete.length > 0) {
          await tx.poseVariant.deleteMany({
            where: { id: { in: variantsToDelete.map(v => v.id) } },
          });
        }

        for (let pvIndex = 0; pvIndex < data.poseVariants!.length; pvIndex++) {
          const pv = data.poseVariants![pvIndex];
          const existing = existingVariants.find(v => v.posePositionId === pv.posePositionId);

          if (existing) {
            // Update variant in-place (preserves ID)
            await tx.poseVariant.update({
              where: { id: existing.id },
              data: {
                name: pv.name as object,
                description: (pv.description as object) || undefined,
                trackedJointsConfig: (pv.trackedJointsConfig as object) || undefined,
                sortOrder: pv.sortOrder ?? pvIndex + 1,
              },
            });

            // Upsert position checks
            const incomingCheckIds = (pv.positionChecks || []).map(pc => pc.checkId);
            await tx.positionCheck.deleteMany({
              where: { poseVariantId: existing.id, checkId: { notIn: incomingCheckIds } },
            });

            for (const [idx, pc] of (pv.positionChecks || []).entries()) {
              await tx.positionCheck.upsert({
                where: { poseVariantId_checkId: { poseVariantId: existing.id, checkId: pc.checkId } },
                create: {
                  poseVariantId: existing.id,
                  checkId: pc.checkId,
                  type: pc.type,
                  landmarks: pc.landmarks as object,
                  condition: pc.condition as object,
                  activePhases: pc.activePhases,
                  errorMessage: {
                    ar: pc.errorMessage.ar,
                    en: pc.errorMessage.en,
                    audioAr: pc.errorMessage.audioAr || undefined,
                    audioEn: pc.errorMessage.audioEn || undefined,
                  },
                  severity: pc.severity || 'warning',
                  cooldownMs: pc.cooldownMs ?? 2000,
                  minErrorFrames: pc.minErrorFrames ?? 3,
                  sortOrder: pc.sortOrder ?? idx + 1,
                },
                update: {
                  type: pc.type,
                  landmarks: pc.landmarks as object,
                  condition: pc.condition as object,
                  activePhases: pc.activePhases,
                  errorMessage: {
                    ar: pc.errorMessage.ar,
                    en: pc.errorMessage.en,
                    audioAr: pc.errorMessage.audioAr || undefined,
                    audioEn: pc.errorMessage.audioEn || undefined,
                  },
                  severity: pc.severity || 'warning',
                  cooldownMs: pc.cooldownMs ?? 2000,
                  minErrorFrames: pc.minErrorFrames ?? 3,
                  sortOrder: pc.sortOrder ?? idx + 1,
                },
              });
            }

            // Rebuild message assignments for this variant
            await tx.feedbackMessageAssignment.deleteMany({
              where: { poseVariantId: existing.id },
            });
            const assignmentsToCreate = [...(pv.messageAssignments || [])];
            if (pv.positionChecks) {
              pv.positionChecks.forEach((pc, pcIdx) => {
                if (pc.messageId) {
                  assignmentsToCreate.push({
                    messageId: pc.messageId,
                    target: 'position',
                    checkId: pc.checkId,
                    sortOrder: (pc.sortOrder ?? pcIdx + 1) + 100,
                  });
                }
              });
            }
            if (assignmentsToCreate.length > 0) {
              await tx.feedbackMessageAssignment.createMany({
                data: assignmentsToCreate.map((a, aIdx) => ({
                  poseVariantId: existing.id,
                  messageId: a.messageId,
                  target: a.target,
                  context: a.context || null,
                  jointCode: a.jointCode || null,
                  zone: a.zone || null,
                  checkId: a.checkId || null,
                  sortOrder: a.sortOrder ?? aIdx + 1,
                })),
              });
            }
          } else {
            // New variant - create from scratch
            await this.createPoseVariant(tx, id, pv, pvIndex);
          }
        }

        // Touch exercise so incremental sync picks up assignment changes
        await tx.exercise.update({
          where: { id },
          data: { updatedBy: updatedBy ?? undefined },
        });
      });
    }

    return this.getById(id);
  },

  /**
   * Publish an exercise
   */
  async publish(id: string, updatedBy?: string) {
    const prisma = await getPrisma();
    return prisma.exercise.update({
      where: { id },
      data: {
        status: 'published',
        publishedAt: new Date(),
        updatedBy,
      },
    });
  },

  /**
   * Unpublish an exercise (back to draft)
   */
  async unpublish(id: string, updatedBy?: string) {
    const prisma = await getPrisma();
    return prisma.exercise.update({
      where: { id },
      data: {
        status: 'draft',
        updatedBy,
      },
    });
  },

  /**
   * Soft delete an exercise
   */
  async delete(id: string, deletedBy?: string) {
    const prisma = await getPrisma();
    return prisma.exercise.update({
      where: { id },
      data: {
        deletedAt: new Date(),
        updatedBy: deletedBy,
      },
    });
  },

  /**
   * Get published exercises for mobile API
   */
  async getPublished() {
    const prisma = await getPrisma();
    return prisma.exercise.findMany({
      where: {
        status: 'published',
        deletedAt: null,
      },
      orderBy: { name: 'asc' },
      include: {
        category: true,
        countingMethod: true,
        media: {
          where: { isPrimary: true },
          take: 1,
        },
      },
    });
  },

  // ============================================
  // WEIGHT & METRICS CONFIGURATION
  // ============================================

  /**
   * Update weight configuration for an exercise
   */
  async updateWeightConfig(
    id: string,
    config: {
      supportsWeight: boolean;
      minWeight?: number;
      maxWeight?: number;
      defaultWeight?: number;
    },
    updatedBy?: string
  ) {
    const prisma = await getPrisma();
    return prisma.exercise.update({
      where: { id },
      data: {
        supportsWeight: config.supportsWeight,
        minWeight: config.minWeight || null,
        maxWeight: config.maxWeight || null,
        defaultWeight: config.defaultWeight || null,
        updatedBy,
      },
    });
  },

  /**
   * Update report metrics configuration
   */
  async updateReportMetrics(
    id: string,
    config: {
      primary: string[];
      optional?: string[];
      excluded?: string[];
    },
    updatedBy?: string
  ) {
    const prisma = await getPrisma();
    return prisma.exercise.update({
      where: { id },
      data: {
        reportMetrics: config,
        updatedBy,
      },
    });
  },

  /**
   * Get exercise configuration for mobile (includes metrics info)
   */
  async getExerciseConfig(id: string) {
    const prisma = await getPrisma();
    const exercise = await prisma.exercise.findUnique({
      where: { id, deletedAt: null },
      include: {
        countingMethod: true,
        poseVariants: {
          take: 1,
          select: {
            trackedJointsConfig: true,
          },
        },
      },
    });

    if (!exercise) return null;

    // Determine if bilateral from tracked joints
    const trackedJoints = exercise.poseVariants[0]?.trackedJointsConfig as any[] || [];
    const hasPairedJoints = trackedJoints.some((j: any) => j.pairedWith);

    // Get counting method code
    const countingMethodCode = (exercise.countingMethod as any)?.code as string;
    const isHold = countingMethodCode === 'hold';

    return {
      id: exercise.id,
      name: exercise.name,
      countingMethod: countingMethodCode,

      // Weight config
      weight: {
        supported: exercise.supportsWeight,
        min: exercise.minWeight,
        max: exercise.maxWeight,
        default: exercise.defaultWeight,
      },

      // Metrics config
      metrics: {
        isBilateral: hasPairedJoints,
        isHold,
        config: exercise.reportMetrics as any,
      },
    };
  },

  /**
   * Bump exercise.updatedAt so incremental sync picks up child changes
   * (e.g. new message assignments, position checks).
   */
  async touchExercise(
    prisma: Awaited<ReturnType<typeof getPrisma>>,
    exerciseId: string
  ) {
    await prisma.exercise.update({
      where: { id: exerciseId },
      data: {},
    });
  },
};
