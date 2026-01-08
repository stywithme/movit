import { getPrisma } from '@/lib/prisma/client';
import { getPhaseCodesForCountingMethod } from './phase-templates';
import type { CountingMethodCode, PhaseName } from '@/lib/types/localized';

// ============================================
// TYPES
// ============================================

interface LocalizedText {
  ar: string;
  en: string;
}

interface TrackedJointInput {
  joint: string;
  role: 'primary' | 'secondary';
  startPose: { min: number; max: number };
  upRange?: {
    beginner: { min: number; max: number };
    normal: { min: number; max: number };
    advanced: { min: number; max: number };
  };
  downRange?: {
    beginner: { min: number; max: number };
    normal: { min: number; max: number };
    advanced: { min: number; max: number };
  };
  Range?: {
    beginner: { min: number; max: number };
    normal: { min: number; max: number };
    advanced: { min: number; max: number };
  };
  errorMessages: {
    tooLow: LocalizedText;
    tooHigh: LocalizedText;
  };
  pairedWith?: string;
}

interface PositionCheckInput {
  checkId: string;
  type: string;
  landmarks: {
    primary: string;
    secondary: string;
    tertiary?: string;
    quaternary?: string;
  };
  condition: {
    operator: string;
    thresholds: {
      beginner: number;
      normal: number;
      advanced: number;
    };
  };
  activePhases: PhaseName[];
  errorMessage: LocalizedText;
  severity?: string;
  cooldownMs?: number;
  minErrorFrames?: number;
  sortOrder?: number;
}

interface FeedbackMessageInput {
  type: 'motivational' | 'common_mistake' | 'tip';
  message: LocalizedText;
  audioUrl?: string;
  sortOrder?: number;
}

interface RepCountingConfigInput {
  reps?: number;
  duration?: number;
  minRepIntervalMs?: number;
  maxRepIntervalMs?: number;
  gracePeriodMs?: number;
}

interface DifficultyLevelInput {
  difficultyTypeCode: 'beginner' | 'normal' | 'advanced';
  name: LocalizedText;
  description?: LocalizedText;
  repCountingConfig: RepCountingConfigInput;
  phases?: PhaseName[];
}

interface PoseVariantInput {
  id?: string;
  tempId?: string;
  name: LocalizedText;
  description?: LocalizedText;
  cameraPositionId: string;
  referenceImageUrl?: string;
  expectedFacingDirection?: string;
  trackedJointsConfig?: TrackedJointInput[];
  positionChecks?: PositionCheckInput[];
  feedbackMessages?: FeedbackMessageInput[];
  difficultyLevels?: DifficultyLevelInput[];
  sortOrder?: number;
}

interface CreateExerciseInput {
  name: LocalizedText;
  description?: LocalizedText;
  instructions?: LocalizedText;
  categoryId: string;
  countingMethodId: string;
  slug?: string;
  muscles?: string[];
  equipment?: string[];
  tags?: string[];
  poseVariants?: PoseVariantInput[];
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
            cameraPosition: {
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
            feedbackMessages: {
              orderBy: { sortOrder: 'asc' },
            },
            difficultyLevels: {
              orderBy: { sortOrder: 'asc' },
              include: {
                difficultyType: true,
              },
            },
          },
        },
      },
    });
  },

  /**
   * Create a new exercise (basic creation - draft)
   */
  async create(data: CreateExerciseInput, createdBy?: string) {
    const prisma = await getPrisma();
    const slug = data.slug || generateSlug(data.name);

    // Get counting method code for phase generation
    const countingMethod = await prisma.attributeValue.findUnique({
      where: { id: data.countingMethodId },
      select: { code: true },
    });
    const countingMethodCode = (countingMethod?.code || 'up_down') as CountingMethodCode;

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

    // Create pose variants if provided
    if (data.poseVariants && data.poseVariants.length > 0) {
      for (let pvIndex = 0; pvIndex < data.poseVariants.length; pvIndex++) {
        const pv = data.poseVariants[pvIndex];
        await this.createPoseVariant(prisma, exercise.id, pv, pvIndex, countingMethodCode);
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
    sortOrder: number,
    countingMethodCode: CountingMethodCode
  ) {
    // Create pose variant
    const poseVariant = await prisma.poseVariant.create({
      data: {
        exerciseId,
        cameraPositionId: pv.cameraPositionId,
        name: pv.name,
        description: pv.description || undefined,
        referenceImageUrl: pv.referenceImageUrl || undefined,
        expectedFacingDirection: pv.expectedFacingDirection || 'auto_detect',
        trackedJointsConfig: pv.trackedJointsConfig || undefined,
        sortOrder: pv.sortOrder ?? sortOrder + 1,
      },
    });

    // Create position checks
    if (pv.positionChecks && pv.positionChecks.length > 0) {
      await prisma.positionCheck.createMany({
        data: pv.positionChecks.map((pc, idx) => ({
          poseVariantId: poseVariant.id,
          checkId: pc.checkId,
          type: pc.type,
          landmarks: pc.landmarks,
          condition: pc.condition,
          activePhases: pc.activePhases,
          errorMessage: pc.errorMessage,
          severity: pc.severity || 'warning',
          cooldownMs: pc.cooldownMs ?? 2000,
          minErrorFrames: pc.minErrorFrames ?? 3,
          sortOrder: pc.sortOrder ?? idx + 1,
        })),
      });
    }

    // Create feedback messages
    if (pv.feedbackMessages && pv.feedbackMessages.length > 0) {
      await prisma.feedbackMessage.createMany({
        data: pv.feedbackMessages.map((fm, idx) => ({
          poseVariantId: poseVariant.id,
          type: fm.type,
          message: fm.message,
          audioUrl: fm.audioUrl || undefined,
          sortOrder: fm.sortOrder ?? idx + 1,
        })),
      });
    }

    // Create difficulty levels
    if (pv.difficultyLevels && pv.difficultyLevels.length > 0) {
      for (let dlIndex = 0; dlIndex < pv.difficultyLevels.length; dlIndex++) {
        const dl = pv.difficultyLevels[dlIndex];
        await this.createDifficultyLevel(prisma, poseVariant.id, dl, dlIndex, countingMethodCode);
      }
    }

    return poseVariant;
  },

  /**
   * Create a difficulty level
   */
  async createDifficultyLevel(
    prisma: Awaited<ReturnType<typeof getPrisma>>,
    poseVariantId: string,
    data: DifficultyLevelInput,
    sortOrder: number,
    countingMethodCode: CountingMethodCode
  ) {
    // Look up difficulty type ID
    const difficultyType = await prisma.attributeValue.findFirst({
      where: {
        code: data.difficultyTypeCode,
        attribute: { code: 'difficulty_type' },
      },
    });

    if (!difficultyType) {
      throw new Error(`Difficulty type not found: ${data.difficultyTypeCode}`);
    }

    // Use provided phases or generate from counting method
    const phases = data.phases || getPhaseCodesForCountingMethod(countingMethodCode);

    return prisma.difficultyLevel.create({
      data: {
        poseVariantId,
        difficultyTypeId: difficultyType.id,
        name: data.name,
        description: data.description || undefined,
        repCountingConfig: data.repCountingConfig,
        phases,
        sortOrder: sortOrder + 1,
      },
    });
  },

  /**
   * Update an exercise
   */
  async update(id: string, data: UpdateExerciseInput, updatedBy?: string) {
    const prisma = await getPrisma();
    
    // Get current exercise for counting method
    const currentExercise = await prisma.exercise.findUnique({
      where: { id },
      include: { countingMethod: true },
    });

    if (!currentExercise) {
      throw new Error('Exercise not found');
    }

    // Get counting method code
    let countingMethodCode = currentExercise.countingMethod.code as CountingMethodCode;
    if (data.countingMethodId) {
      const newMethod = await prisma.attributeValue.findUnique({
        where: { id: data.countingMethodId },
        select: { code: true },
      });
      countingMethodCode = (newMethod?.code || countingMethodCode) as CountingMethodCode;
    }

    const updateData: Record<string, unknown> = {
      updatedBy,
    };

    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined) updateData.description = data.description;
    if (data.instructions !== undefined) updateData.instructions = data.instructions;
    if (data.categoryId !== undefined) updateData.categoryId = data.categoryId;
    if (data.countingMethodId !== undefined) updateData.countingMethodId = data.countingMethodId;
    if (data.status !== undefined) updateData.status = data.status;

    await prisma.exercise.update({
      where: { id },
      data: updateData,
    });

    // Update attributes if provided
    const attributeIds = [
      ...(data.muscles || []),
      ...(data.equipment || []),
      ...(data.tags || []),
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
        });
      }
    }

    // Update pose variants if provided
    if (data.poseVariants !== undefined) {
      // Delete existing pose variants (cascades to nested data)
      await prisma.poseVariant.deleteMany({
        where: { exerciseId: id },
      });

      // Create new pose variants
      for (let pvIndex = 0; pvIndex < data.poseVariants.length; pvIndex++) {
        const pv = data.poseVariants[pvIndex];
        await this.createPoseVariant(prisma, id, pv, pvIndex, countingMethodCode);
      }
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
};
