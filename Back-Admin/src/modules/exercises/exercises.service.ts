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
  FeedbackMessageInput,
  RepCountingConfig,
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
  cameraPositionId: string;
  referenceImageUrl?: string;
  expectedFacingDirection?: string;
  trackedJointsConfig?: TrackedJoint[];
  positionChecks?: PositionCheckInput[];
  feedbackMessages?: FeedbackMessageInput[];
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
        cameraPositionId: pv.cameraPositionId,
        name: pv.name as object,
        description: (pv.description as object) || undefined,
        referenceImageUrl: pv.referenceImageUrl || undefined,
        expectedFacingDirection: pv.expectedFacingDirection || 'auto_detect',
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

    // Create feedback messages (audio URLs are now part of message object)
    if (pv.feedbackMessages && pv.feedbackMessages.length > 0) {
      await prisma.feedbackMessage.createMany({
        data: pv.feedbackMessages.map((fm, idx) => ({
          poseVariantId: poseVariant.id,
          type: fm.type,
          message: {
            ar: fm.message.ar,
            en: fm.message.en,
            audioAr: fm.message.audioAr || undefined,
            audioEn: fm.message.audioEn || undefined,
          },
          sortOrder: fm.sortOrder ?? idx + 1,
        })),
      });
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
        await this.createPoseVariant(prisma, id, pv, pvIndex);
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
