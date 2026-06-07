/**
 * Assessment Templates Admin Service
 * ===================================
 *
 * Full CRUD for AssessmentTemplate and AssessmentTemplateExercise.
 *
 *   list(filters?)              — All templates (not deleted), with exercise count
 *   getById(id)                 — Single template with full exercises
 *   create(data, createdBy?)    — Create template (validates domain weights)
 *   update(id, data, updatedBy?)— Update template
 *   delete(id)                  — Soft delete (set deletedAt)
 *   publish(id)                 — Set isPublished = true
 *   unpublish(id)               — Set isPublished = false
 *
 *   addExercise(templateId, data)          — Add exercise entry
 *   updateExercise(templateId, entryId, data) — Update exercise entry
 *   removeExercise(templateId, entryId)    — Delete exercise entry
 *   reorderExercises(templateId, orderedIds) — Reorder exercise entries
 *
 *   resolveForUser(userId, mode) — Attribute matching (initial | progression) + isDefault fallback only
 */

import type { PrismaClient } from '@prisma/client';
import { ProgramAttributeMode } from '@prisma/client';
import { getPrisma } from '@/lib/prisma/client';
import { assessmentMatchingService } from '@/modules/assessments/assessment-matching.service';
import type { ProgramAttributeInput } from '@/modules/programs/programs.types';

// ── Includes ──

const templateFullInclude = {
  exercises: {
    orderBy: { sortOrder: 'asc' as const },
    include: {
      exercise: { select: { id: true, slug: true, name: true } },
    },
  },
  targetLevel: { select: { id: true, number: true, code: true, name: true, color: true } },
  assessmentAttributes: {
    orderBy: { createdAt: 'asc' as const },
    include: {
      attributeValue: { include: { attribute: true } },
    },
  },
} as const;

async function replaceAssessmentAttributes(
  db: Pick<PrismaClient, 'assessmentAttribute'>,
  templateId: string,
  attrs: ProgramAttributeInput[] | undefined,
) {
  if (attrs === undefined) return;
  const unique = Array.from(new Map(attrs.map((a) => [a.attributeValueId, a])).values());
  await db.assessmentAttribute.deleteMany({ where: { templateId } });
  if (unique.length === 0) return;
  await db.assessmentAttribute.createMany({
    data: unique.map((a) => ({
      templateId,
      attributeValueId: a.attributeValueId,
      mode: a.mode ?? ProgramAttributeMode.REQUIRED,
    })),
  });
}

// ── Types ──

export interface TemplateCreateData {
  name: { ar: string; en: string };
  description?: { ar: string; en: string } | null;
  type?: string;
  targetLevelId?: string | null;
  domainWeights?: Record<string, number>;
  isDefault?: boolean;
  sortOrder?: number;
  /** Prescription-style attribute rows (domain, goal, equipment, …). */
  assessmentAttributes?: ProgramAttributeInput[];
}

export type TemplateUpdateData = Partial<TemplateCreateData>;

export interface ExerciseEntryCreateData {
  exerciseId: string;
  sortOrder?: number;
  targetRegion: string;
  side?: string;
  entryType?: string;
  activationCondition?: Record<string, unknown> | null;
  referenceNormDegrees?: number | null;
  thresholds?: Record<string, number> | null;
}

export type ExerciseEntryUpdateData = Partial<Omit<ExerciseEntryCreateData, 'exerciseId'>>;

// ── Helpers ──

/**
 * Validate that domain weights sum is approximately 1.0 (within ±0.01 tolerance).
 */
function validateDomainWeights(weights: Record<string, number>): void {
  const values = Object.values(weights);
  if (values.length === 0) {
    throw new Error('domainWeights must have at least one entry');
  }
  for (const v of values) {
    if (typeof v !== 'number' || v < 0 || v > 1) {
      throw new Error('Each domain weight must be a number between 0 and 1');
    }
  }
  const sum = values.reduce((a, b) => a + b, 0);
  if (Math.abs(sum - 1.0) > 0.01) {
    throw new Error(`Domain weights must sum to ~1.0 (got ${sum.toFixed(4)})`);
  }
}

type PrismaClientInstance = Awaited<ReturnType<typeof getPrisma>>;
type ResolveMode = 'initial' | 'progression';

/** Safety net when attribute matching returns null — one published default per mode family. */
async function legacyResolveAssessmentTemplate(prisma: PrismaClientInstance, _userId: string, mode: ResolveMode) {
  if (mode === 'initial') {
    return prisma.assessmentTemplate.findFirst({
      where: { deletedAt: null, isPublished: true, isDefault: true, type: 'initial' },
      orderBy: { sortOrder: 'asc' },
      include: templateFullInclude,
    });
  }

  return prisma.assessmentTemplate.findFirst({
    where: {
      deletedAt: null,
      isPublished: true,
      isDefault: true,
      type: { in: ['progression', 'post_program', 'level_specific'] },
    },
    orderBy: { sortOrder: 'asc' },
    include: templateFullInclude,
  });
}

function mapTemplateToResolvePayload(
  template: NonNullable<Awaited<ReturnType<typeof legacyResolveAssessmentTemplate>>>,
) {
  const exercises = template.exercises
    .sort((a, b) => {
      if (a.entryType === 'core' && b.entryType !== 'core') return -1;
      if (a.entryType !== 'core' && b.entryType === 'core') return 1;
      return a.sortOrder - b.sortOrder;
    })
    .map((e) => ({
      exerciseId: e.exerciseId,
      exerciseSlug: e.exercise.slug,
      exerciseName: e.exercise.name,
      sortOrder: e.sortOrder,
      targetRegion: e.targetRegion,
      side: e.side,
      entryType: e.entryType,
      activationCondition: e.activationCondition,
      referenceNormDegrees: e.referenceNormDegrees,
      thresholds: e.thresholds,
    }));

  return {
    templateId: template.id,
    name: template.name,
    type: template.type,
    domainWeights: template.domainWeights,
    exercises,
  };
}

// ── Service ──

export const assessmentTemplateService = {
  /**
   * List all templates (not deleted) with optional filters and exercise count.
   */
  async list(filters?: { type?: string; levelId?: string; status?: string }) {
    const prisma = await getPrisma();

    const where: Record<string, unknown> = { deletedAt: null };

    if (filters?.type) {
      where.type = filters.type;
    }
    if (filters?.levelId) {
      where.targetLevelId = filters.levelId;
    }
    if (filters?.status === 'published') {
      where.isPublished = true;
    } else if (filters?.status === 'draft') {
      where.isPublished = false;
    }

    const templates = await prisma.assessmentTemplate.findMany({
      where,
      orderBy: [{ sortOrder: 'asc' }, { createdAt: 'desc' }],
      include: {
        targetLevel: { select: { id: true, number: true, code: true, name: true, color: true } },
        _count: { select: { exercises: true, assessmentAttributes: true } },
      },
    });

    return templates;
  },

  /**
   * Get a single template by ID with full exercises included.
   */
  async getById(id: string) {
    const prisma = await getPrisma();

    const template = await prisma.assessmentTemplate.findFirst({
      where: { id, deletedAt: null },
      include: templateFullInclude,
    });

    if (!template) {
      throw new Error('Assessment template not found');
    }

    return template;
  },

  /**
   * Create a new assessment template.
   */
  async create(data: TemplateCreateData, createdBy?: string) {
    const prisma = await getPrisma();

    if (!data.name?.en && !data.name?.ar) {
      throw new Error('Template name is required (at least en or ar)');
    }

    // Validate domain weights if provided
    if (data.domainWeights) {
      validateDomainWeights(data.domainWeights);
    }

    return prisma.$transaction(async (tx) => {
      const template = await tx.assessmentTemplate.create({
        data: {
          name: data.name,
          description: data.description ?? undefined,
          type: data.type ?? 'initial',
          targetLevelId: data.targetLevelId ?? undefined,
          domainWeights: data.domainWeights ?? undefined,
          isDefault: data.isDefault ?? false,
          sortOrder: data.sortOrder ?? 0,
          createdBy: createdBy ?? undefined,
        },
      });
      if (data.assessmentAttributes !== undefined) {
        await replaceAssessmentAttributes(tx, template.id, data.assessmentAttributes);
      }
      return tx.assessmentTemplate.findFirstOrThrow({
        where: { id: template.id },
        include: templateFullInclude,
      });
    });
  },

  /**
   * Update an existing assessment template.
   */
  async update(id: string, data: TemplateUpdateData, updatedBy?: string) {
    const prisma = await getPrisma();

    const existing = await prisma.assessmentTemplate.findFirst({
      where: { id, deletedAt: null },
    });
    if (!existing) {
      throw new Error('Assessment template not found');
    }

    // Validate domain weights if provided
    if (data.domainWeights) {
      validateDomainWeights(data.domainWeights);
    }

    const updateData: Record<string, unknown> = {};

    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined) updateData.description = data.description;
    if (data.type !== undefined) updateData.type = data.type;
    if (data.targetLevelId !== undefined) updateData.targetLevelId = data.targetLevelId;
    if (data.domainWeights !== undefined) updateData.domainWeights = data.domainWeights;
    if (data.isDefault !== undefined) updateData.isDefault = data.isDefault;
    if (data.sortOrder !== undefined) updateData.sortOrder = data.sortOrder;
    if (updatedBy) updateData.updatedBy = updatedBy;

    return prisma.$transaction(async (tx) => {
      if (Object.keys(updateData).length > 0) {
        await tx.assessmentTemplate.update({
          where: { id },
          data: updateData,
        });
      }
      if (data.assessmentAttributes !== undefined) {
        await replaceAssessmentAttributes(tx, id, data.assessmentAttributes);
      }
      return tx.assessmentTemplate.findFirstOrThrow({
        where: { id },
        include: templateFullInclude,
      });
    });
  },

  /**
   * Soft delete a template (set deletedAt).
   */
  async delete(id: string) {
    const prisma = await getPrisma();

    const existing = await prisma.assessmentTemplate.findFirst({
      where: { id, deletedAt: null },
    });
    if (!existing) {
      throw new Error('Assessment template not found');
    }

    // Check if any assessments reference this template
    const assessmentsUsingTemplate = await prisma.bodyScanResult.findFirst({
      where: { templateId: id },
    });
    if (assessmentsUsingTemplate) {
      throw new Error('Cannot delete template: assessments reference this template');
    }

    await prisma.assessmentTemplate.update({
      where: { id },
      data: { deletedAt: new Date(), isPublished: false },
    });

    return { deleted: true };
  },

  /**
   * Publish a template (set isPublished = true).
   */
  async publish(id: string) {
    const prisma = await getPrisma();

    const existing = await prisma.assessmentTemplate.findFirst({
      where: { id, deletedAt: null },
    });
    if (!existing) {
      throw new Error('Assessment template not found');
    }

    // Ensure template has at least one exercise before publishing
    const exerciseCount = await prisma.assessmentTemplateExercise.count({
      where: { templateId: id },
    });
    if (exerciseCount === 0) {
      throw new Error('Cannot publish template with no exercises');
    }

    const template = await prisma.assessmentTemplate.update({
      where: { id },
      data: { isPublished: true },
      include: templateFullInclude,
    });

    return template;
  },

  /**
   * Unpublish a template (set isPublished = false).
   */
  async unpublish(id: string) {
    const prisma = await getPrisma();

    const existing = await prisma.assessmentTemplate.findFirst({
      where: { id, deletedAt: null },
    });
    if (!existing) {
      throw new Error('Assessment template not found');
    }

    const template = await prisma.assessmentTemplate.update({
      where: { id },
      data: { isPublished: false },
      include: templateFullInclude,
    });

    return template;
  },

  // ── Exercise management ──

  /**
   * Add an exercise entry to a template.
   */
  async addExercise(templateId: string, data: ExerciseEntryCreateData) {
    const prisma = await getPrisma();

    const template = await prisma.assessmentTemplate.findFirst({
      where: { id: templateId, deletedAt: null },
    });
    if (!template) {
      throw new Error('Assessment template not found');
    }

    // Verify exercise exists
    const exercise = await prisma.exercise.findUnique({
      where: { id: data.exerciseId },
    });
    if (!exercise) {
      throw new Error('Exercise not found');
    }

    // Auto-calculate sortOrder if not provided
    let sortOrder = data.sortOrder;
    if (sortOrder == null) {
      const maxEntry = await prisma.assessmentTemplateExercise.findFirst({
        where: { templateId },
        orderBy: { sortOrder: 'desc' },
        select: { sortOrder: true },
      });
      sortOrder = (maxEntry?.sortOrder ?? -1) + 1;
    }

    const entry = await prisma.assessmentTemplateExercise.create({
      data: {
        templateId,
        exerciseId: data.exerciseId,
        sortOrder,
        targetRegion: data.targetRegion,
        side: data.side ?? 'center',
        entryType: data.entryType ?? 'core',
        activationCondition: data.activationCondition
          ? (data.activationCondition as any)
          : undefined,
        referenceNormDegrees: data.referenceNormDegrees ?? undefined,
        thresholds: data.thresholds ? (data.thresholds as any) : undefined,
      },
      include: {
        exercise: { select: { id: true, slug: true, name: true } },
      },
    });

    return entry;
  },

  /**
   * Update an exercise entry within a template.
   */
  async updateExercise(templateId: string, exerciseEntryId: string, data: ExerciseEntryUpdateData) {
    const prisma = await getPrisma();

    const entry = await prisma.assessmentTemplateExercise.findFirst({
      where: { id: exerciseEntryId, templateId },
    });
    if (!entry) {
      throw new Error('Exercise entry not found in this template');
    }

    const updateData: Record<string, unknown> = {};

    if (data.sortOrder !== undefined) updateData.sortOrder = data.sortOrder;
    if (data.targetRegion !== undefined) updateData.targetRegion = data.targetRegion;
    if (data.side !== undefined) updateData.side = data.side;
    if (data.entryType !== undefined) updateData.entryType = data.entryType;
    if (data.activationCondition !== undefined) updateData.activationCondition = data.activationCondition;
    if (data.referenceNormDegrees !== undefined) updateData.referenceNormDegrees = data.referenceNormDegrees;
    if (data.thresholds !== undefined) updateData.thresholds = data.thresholds;

    const updated = await prisma.assessmentTemplateExercise.update({
      where: { id: exerciseEntryId },
      data: updateData,
      include: {
        exercise: { select: { id: true, slug: true, name: true } },
      },
    });

    return updated;
  },

  /**
   * Remove an exercise entry from a template.
   */
  async removeExercise(templateId: string, exerciseEntryId: string) {
    const prisma = await getPrisma();

    const entry = await prisma.assessmentTemplateExercise.findFirst({
      where: { id: exerciseEntryId, templateId },
    });
    if (!entry) {
      throw new Error('Exercise entry not found in this template');
    }

    await prisma.assessmentTemplateExercise.delete({
      where: { id: exerciseEntryId },
    });

    return { deleted: true };
  },

  /**
   * Reorder exercise entries by updating sortOrder.
   * @param orderedIds - Array of exercise entry IDs in desired order.
   */
  async reorderExercises(templateId: string, orderedIds: string[]) {
    const prisma = await getPrisma();

    const entries = await prisma.assessmentTemplateExercise.findMany({
      where: { templateId },
      select: { id: true },
    });

    const entryIdSet = new Set(entries.map((e) => e.id));
    for (const id of orderedIds) {
      if (!entryIdSet.has(id)) {
        throw new Error(`Unknown exercise entry ID: ${id}`);
      }
    }

    const updates = orderedIds.map((id, index) =>
      prisma.assessmentTemplateExercise.update({
        where: { id },
        data: { sortOrder: index },
      }),
    );

    await prisma.$transaction(updates);

    // Return updated template with exercises
    const template = await prisma.assessmentTemplate.findUnique({
      where: { id: templateId },
      include: templateFullInclude,
    });

    return template;
  },

  // ── Mobile resolve ──

  /**
   * Resolve the right assessment template for a user using attribute matching.
   * Falls back to legacy level-based selection when no template matches attributes.
   *
   * @param mode `initial` (default) — first/onboarding assessment; `progression` — exit exam for current level.
   */
  async resolveForUser(userId: string, mode: ResolveMode = 'initial') {
    const prisma = await getPrisma();

    let template: Awaited<ReturnType<typeof assessmentMatchingService.matchInitial>> = null;

    if (mode === 'progression') {
      const levelProfile = await prisma.userLevelProfile.findFirst({
        where: { userId },
        orderBy: { classifiedAt: 'desc' },
      });
      const userLevel = levelProfile?.overallLevel ?? 1;
      template = await assessmentMatchingService.matchProgression(userId, userLevel);
    } else {
      template = await assessmentMatchingService.matchInitial(userId);
    }

    if (!template) {
      const legacy = await legacyResolveAssessmentTemplate(prisma, userId, mode);
      if (!legacy) return null;
      return mapTemplateToResolvePayload(legacy);
    }

    return mapTemplateToResolvePayload(
      template as NonNullable<Awaited<ReturnType<typeof legacyResolveAssessmentTemplate>>>,
    );
  },
};
