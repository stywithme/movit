import { getPrisma } from '@/lib/prisma/client';
import { 
  CreateExerciseInput, 
  UpdateExerciseInput, 
  CompleteExerciseInput,
  DifficultyLevelFullInput 
} from './exercises.types';

/**
 * Generate a URL-friendly slug from exercise name
 */
function generateSlug(name: { en?: string; ar?: string }): string {
  const baseName = name.en || name.ar || 'exercise';
  return baseName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    + '-' + Date.now().toString(36);
}

/**
 * Exercise Service - handles all exercise-related database operations
 */
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
            cameraPosition: {
              include: {
                joints: {
                  include: {
                    joint: true,
                  },
                },
              },
            },
            difficultyLevels: {
              orderBy: { sortOrder: 'asc' },
              include: {
                difficultyType: true,
                phases: {
                  orderBy: { sortOrder: 'asc' },
                  include: {
                    angleRules: {
                      include: {
                        joint: true,
                      },
                    },
                  },
                },
                feedbackMessages: {
                  orderBy: { sortOrder: 'asc' },
                },
              },
            },
          },
        },
      },
    });
  },

  /**
   * Create a new exercise (basic creation)
   */
  async create(data: CreateExerciseInput, createdBy?: string) {
    const prisma = await getPrisma();
    const slug = data.slug || generateSlug(data.name);

    const exercise = await prisma.exercise.create({
      data: {
        name: data.name,
        description: data.description || undefined,
        instructions: data.instructions || undefined,
        categoryId: data.categoryId,
        countingMethodId: data.countingMethodId,
        slug,
        status: 'draft',
        createdBy,
        updatedBy: createdBy,
      },
      include: {
        category: true,
        countingMethod: true,
      },
    });

    // Add muscles, equipment, and tags
    const attributeIds = [
      ...(data.muscles || []),
      ...(data.equipment || []),
      ...(data.tags || []),
    ];

    if (attributeIds.length > 0) {
      await prisma.exerciseAttribute.createMany({
        data: attributeIds.map((attributeValueId) => ({
          exerciseId: exercise.id,
          attributeValueId,
        })),
      });
    }

    // Add pose variants if provided (with full tracked joints configuration)
    if (data.poseVariants && data.poseVariants.length > 0) {
      await prisma.poseVariant.createMany({
        data: data.poseVariants.map((pv, index) => ({
          exerciseId: exercise.id,
          cameraPositionId: pv.cameraPositionId,
          name: pv.name,
          description: pv.description || undefined,
          referenceImageUrl: pv.referenceImageUrl || undefined,
          startPoseAngles: pv.startPoseAngles || undefined,
          primaryJoint: pv.primaryJoint || undefined,
          trackedJointsConfig: pv.trackedJointsConfig || undefined,
          sortOrder: pv.sortOrder ?? index + 1,
        })),
      });
    }

    // Return exercise with all relations
    return prisma.exercise.findUnique({
      where: { id: exercise.id },
      include: {
        category: true,
        countingMethod: true,
        poseVariants: {
          include: {
            cameraPosition: true,
          },
          orderBy: { sortOrder: 'asc' },
        },
      },
    });
  },

  /**
   * Create a complete exercise with all nested data (wizard flow)
   */
  async createComplete(data: CompleteExerciseInput, createdBy?: string) {
    const prisma = await getPrisma();
    const slug = generateSlug(data.name);

    // Create exercise
    const exercise = await prisma.exercise.create({
      data: {
        name: data.name,
        description: data.description || undefined,
        instructions: data.instructions || undefined,
        categoryId: data.categoryId,
        countingMethodId: data.countingMethodId,
        slug,
        status: 'draft',
        createdBy,
        updatedBy: createdBy,
      },
    });

    // Add attributes
    const attributeIds = [
      ...(data.muscles || []),
      ...(data.equipment || []),
      ...(data.tags || []),
    ];

    if (attributeIds.length > 0) {
      await prisma.exerciseAttribute.createMany({
        data: attributeIds.map((attributeValueId) => ({
          exerciseId: exercise.id,
          attributeValueId,
        })),
      });
    }

    // Create pose variants and their nested data
    if (data.poseVariants && data.poseVariants.length > 0) {
      for (let pvIndex = 0; pvIndex < data.poseVariants.length; pvIndex++) {
        const pv = data.poseVariants[pvIndex];
        const poseVariant = await prisma.poseVariant.create({
          data: {
            exerciseId: exercise.id,
            cameraPositionId: pv.cameraPositionId,
            name: pv.name,
            description: pv.description || undefined,
            referenceImageUrl: pv.referenceImageUrl || undefined,
            startPoseAngles: pv.startPoseAngles || undefined,
            primaryJoint: pv.primaryJoint || undefined,
            trackedJointsConfig: pv.trackedJointsConfig || undefined,
            sortOrder: pv.sortOrder ?? pvIndex + 1,
          },
        });

        // Find difficulty levels for this pose variant by matching temp IDs
        const variantLevels = (data.difficultyLevels || []).filter(
          (dl) => dl.poseVariantId === pv.tempId || dl.poseVariantId === pv.id
        );

        // Create difficulty levels
        for (let dlIndex = 0; dlIndex < variantLevels.length; dlIndex++) {
          const dl = variantLevels[dlIndex];
          await this.createDifficultyLevel(prisma, poseVariant.id, dl, dlIndex);
        }
      }
    }

    // Return complete exercise
    return this.getById(exercise.id);
  },

  /**
   * Helper: Create a difficulty level with all nested data
   */
  async createDifficultyLevel(
    prisma: Awaited<ReturnType<typeof getPrisma>>,
    poseVariantId: string,
    data: DifficultyLevelFullInput,
    sortOrder: number
  ) {
    // If difficultyTypeCode is provided, look up the ID
    let difficultyTypeId = data.difficultyTypeId;
    if (!difficultyTypeId && data.difficultyTypeCode) {
      const difficultyType = await prisma.attributeValue.findFirst({
        where: {
          code: data.difficultyTypeCode,
          attribute: { code: 'difficulty_type' },
        },
      });
      difficultyTypeId = difficultyType?.id;
    }

    // If still no ID, try to find by name matching
    if (!difficultyTypeId) {
      const difficultyType = await prisma.attributeValue.findFirst({
        where: {
          attribute: { code: 'difficulty_type' },
        },
        orderBy: { sortOrder: 'asc' },
      });
      difficultyTypeId = difficultyType?.id;
    }

    // Ensure we have a valid difficultyTypeId before creating
    if (!difficultyTypeId) {
      throw new Error(
        `Cannot create difficulty level: No difficulty type found. ` +
        `Please ensure difficulty types (beginner, normal, advanced) exist in the database.`
      );
    }

    const difficultyLevel = await prisma.difficultyLevel.create({
      data: {
        poseVariantId,
        difficultyTypeId,
        name: data.name,
        description: data.description || undefined,
        romConfig: data.romConfig || undefined,
        repCountingConfig: data.repCountingConfig || undefined,
        sortOrder: sortOrder + 1,
      },
    });

    // Create phases
    if (data.phases && data.phases.length > 0) {
      for (let phaseIndex = 0; phaseIndex < data.phases.length; phaseIndex++) {
        const phase = data.phases[phaseIndex];
        const createdPhase = await prisma.phase.create({
          data: {
            difficultyLevelId: difficultyLevel.id,
            code: phase.code,
            name: phase.name,
            sortOrder: phase.sortOrder ?? phaseIndex + 1,
          },
        });

        // Create angle rules
        if (phase.angleRules && phase.angleRules.length > 0) {
          await prisma.angleRule.createMany({
            data: phase.angleRules.map((rule) => ({
              phaseId: createdPhase.id,
              jointId: rule.jointId,
              minAngle: rule.minAngle,
              maxAngle: rule.maxAngle,
              errorMessageOver: rule.errorMessageOver || undefined,
              errorMessageUnder: rule.errorMessageUnder || undefined,
              priority: rule.priority || 'medium',
            })),
          });
        }
      }
    }

    // Create feedback messages
    if (data.feedbackMessages && data.feedbackMessages.length > 0) {
      await prisma.feedbackMessage.createMany({
        data: data.feedbackMessages.map((msg, index) => ({
          difficultyLevelId: difficultyLevel.id,
          type: msg.type,
          message: msg.message,
          audioUrl: msg.audioUrl || undefined,
          sortOrder: msg.sortOrder ?? index + 1,
        })),
      });
    }

    return difficultyLevel;
  },

  /**
   * Update an exercise (basic update)
   */
  async update(id: string, data: UpdateExerciseInput, updatedBy?: string) {
    const prisma = await getPrisma();
    const updateData: Record<string, unknown> = {
      updatedBy,
    };

    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined) updateData.description = data.description;
    if (data.instructions !== undefined) updateData.instructions = data.instructions;
    if (data.categoryId !== undefined) updateData.categoryId = data.categoryId;
    if (data.countingMethodId !== undefined) updateData.countingMethodId = data.countingMethodId;
    if (data.status !== undefined) updateData.status = data.status;

    const exercise = await prisma.exercise.update({
      where: { id },
      data: updateData,
      include: {
        category: true,
        countingMethod: true,
      },
    });

    // Update attributes if provided
    const attributeIds = [
      ...(data.muscles || []),
      ...(data.equipment || []),
      ...(data.tags || []),
    ];

    if (data.muscles || data.equipment || data.tags) {
      // Remove old attributes
      await prisma.exerciseAttribute.deleteMany({
        where: { exerciseId: id },
      });

      // Add new attributes
      if (attributeIds.length > 0) {
        await prisma.exerciseAttribute.createMany({
          data: attributeIds.map((attributeValueId) => ({
            exerciseId: exercise.id,
            attributeValueId,
          })),
        });
      }
    }

    // Update pose variants if provided
    if (data.poseVariants !== undefined) {
      // Remove old pose variants (cascades to difficulty levels, phases, etc.)
      await prisma.poseVariant.deleteMany({
        where: { exerciseId: id },
      });

      // Add new pose variants with full tracked joints configuration
      if (data.poseVariants.length > 0) {
        await prisma.poseVariant.createMany({
          data: data.poseVariants.map((pv, index) => ({
            exerciseId: id,
            cameraPositionId: pv.cameraPositionId,
            name: pv.name,
            description: pv.description || undefined,
            referenceImageUrl: pv.referenceImageUrl || undefined,
            startPoseAngles: pv.startPoseAngles || undefined,
            primaryJoint: pv.primaryJoint || undefined,
            trackedJointsConfig: pv.trackedJointsConfig || undefined,
            sortOrder: pv.sortOrder ?? index + 1,
          })),
        });
      }
    }

    // Return exercise with all relations
    return prisma.exercise.findUnique({
      where: { id },
      include: {
        category: true,
        countingMethod: true,
        poseVariants: {
          include: {
            cameraPosition: true,
          },
          orderBy: { sortOrder: 'asc' },
        },
      },
    });
  },

  /**
   * Update a complete exercise with all nested data (wizard flow)
   */
  async updateComplete(id: string, data: CompleteExerciseInput, updatedBy?: string) {
    const prisma = await getPrisma();

    // Update basic exercise data
    await prisma.exercise.update({
      where: { id },
      data: {
        name: data.name,
        description: data.description || undefined,
        instructions: data.instructions || undefined,
        categoryId: data.categoryId,
        countingMethodId: data.countingMethodId,
        updatedBy,
      },
    });

    // Update attributes
    const attributeIds = [
      ...(data.muscles || []),
      ...(data.equipment || []),
      ...(data.tags || []),
    ];

    await prisma.exerciseAttribute.deleteMany({
      where: { exerciseId: id },
    });

    if (attributeIds.length > 0) {
      await prisma.exerciseAttribute.createMany({
        data: attributeIds.map((attributeValueId) => ({
          exerciseId: id,
          attributeValueId,
        })),
      });
    }

    // Delete all existing pose variants (cascades to difficulty levels, phases, etc.)
    await prisma.poseVariant.deleteMany({
      where: { exerciseId: id },
    });

    // Create new pose variants and their nested data
    if (data.poseVariants && data.poseVariants.length > 0) {
      for (let pvIndex = 0; pvIndex < data.poseVariants.length; pvIndex++) {
        const pv = data.poseVariants[pvIndex];
        const poseVariant = await prisma.poseVariant.create({
          data: {
            exerciseId: id,
            cameraPositionId: pv.cameraPositionId,
            name: pv.name,
            description: pv.description || undefined,
            referenceImageUrl: pv.referenceImageUrl || undefined,
            startPoseAngles: pv.startPoseAngles || undefined,
            primaryJoint: pv.primaryJoint || undefined,
            trackedJointsConfig: pv.trackedJointsConfig || undefined,
            sortOrder: pv.sortOrder ?? pvIndex + 1,
          },
        });

        // Find difficulty levels for this pose variant
        const variantLevels = (data.difficultyLevels || []).filter(
          (dl) => dl.poseVariantId === pv.tempId || dl.poseVariantId === pv.id
        );

        // Create difficulty levels
        for (let dlIndex = 0; dlIndex < variantLevels.length; dlIndex++) {
          const dl = variantLevels[dlIndex];
          await this.createDifficultyLevel(prisma, poseVariant.id, dl, dlIndex);
        }
      }
    }

    // Return complete exercise
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

  /**
   * Get full exercise config for mobile (single exercise)
   */
  async getFullConfig(id: string) {
    const exercise = await this.getById(id);
    if (!exercise || exercise.status !== 'published') {
      return null;
    }

    // Transform to mobile-friendly format with new structure
    return {
      id: exercise.id,
      name: exercise.name,
      description: exercise.description,
      instructions: exercise.instructions,
      category: {
        code: exercise.category.code,
        name: exercise.category.name,
      },
      countingMethod: exercise.countingMethod.code,
      updatedAt: exercise.updatedAt.toISOString(),
      primaryImage: exercise.media[0]?.url || null,
      muscles: exercise.attributes
        .filter((a) => a.attributeValue.attribute.code === 'muscle')
        .map((a) => a.attributeValue.code),
      equipment: exercise.attributes
        .filter((a) => a.attributeValue.attribute.code === 'equipment')
        .map((a) => a.attributeValue.code),
      poseVariants: exercise.poseVariants.map((pv) => ({
        id: pv.id,
        name: pv.name,
        cameraPosition: pv.cameraPosition.code,
        referenceImage: pv.referenceImageUrl,
        requiredJoints: pv.cameraPosition.joints.map((j) => j.joint.code),
        startPoseAngles: pv.startPoseAngles,
        primaryJoint: pv.primaryJoint,
        trackedJointsConfig: pv.trackedJointsConfig,
        difficultyLevels: pv.difficultyLevels.map((dl) => ({
          id: dl.id,
          level: dl.difficultyType.code,
          name: dl.name,
          description: dl.description,
          romConfig: dl.romConfig,
          repCountingConfig: dl.repCountingConfig,
          phases: dl.phases.map((phase) => ({
            code: phase.code,
            name: phase.name,
            rules: phase.angleRules.map((rule) => ({
              joint: rule.joint.code,
              min: rule.minAngle,
              max: rule.maxAngle,
              errorOver: rule.errorMessageOver,
              errorUnder: rule.errorMessageUnder,
              priority: rule.priority,
            })),
          })),
          feedbackMessages: {
            motivational: dl.feedbackMessages
              .filter((m) => m.type === 'motivational')
              .map((m) => m.message),
            common_mistake: dl.feedbackMessages
              .filter((m) => m.type === 'common_mistake')
              .map((m) => m.message),
            tip: dl.feedbackMessages
              .filter((m) => m.type === 'tip')
              .map((m) => m.message),
          },
        })),
      })),
    };
  },
};
