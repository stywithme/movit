/**
 * Attribute-based matching for published assessment templates (initial + progression).
 */

import type { ProgramAttributeMode } from '@prisma/client';
import { getPrisma } from '@/lib/prisma/client';
import {
  buildUserAttributeSet,
  countOptionalMatches,
  passesAttributeFilter,
  type EntityAttributeRow,
} from '@/lib/attribute-matching';

const PROGRESSION_TYPES = ['progression', 'post_program', 'level_specific'] as const;

const templateMatchInclude = {
  exercises: {
    orderBy: { sortOrder: 'asc' as const },
    include: {
      exercise: { select: { id: true, slug: true, name: true } },
    },
  },
  targetLevel: { select: { id: true, number: true, code: true, name: true, color: true } },
  assessmentAttributes: {
    include: {
      attributeValue: { select: { code: true } },
    },
  },
} as const;

type TemplateWithAttrs = {
  id: string;
  name: unknown;
  type: string;
  domainWeights: unknown;
  sortOrder: number;
  assessmentAttributes: Array<{
    mode: ProgramAttributeMode;
    attributeValue: { code: string };
  }>;
  exercises: Array<{
    exerciseId: string;
    sortOrder: number;
    targetRegion: string;
    side: string;
    entryType: string;
    activationCondition: unknown;
    referenceNormDegrees: number | null;
    thresholds: unknown;
    exercise: { slug: string; name: unknown };
  }>;
};

function toEntityRows(t: TemplateWithAttrs): EntityAttributeRow[] {
  return t.assessmentAttributes.map((a) => ({
    mode: a.mode,
    attributeValueCode: a.attributeValue.code,
  }));
}

function pickBestTemplate(templates: TemplateWithAttrs[], userCodes: Set<string>): TemplateWithAttrs | null {
  const eligible = templates.filter((t) => passesAttributeFilter(toEntityRows(t), userCodes));
  if (eligible.length === 0) return null;
  eligible.sort((a, b) => {
    const opt =
      countOptionalMatches(toEntityRows(b), userCodes) - countOptionalMatches(toEntityRows(a), userCodes);
    if (opt !== 0) return opt;
    return a.sortOrder - b.sortOrder;
  });
  return eligible[0] ?? null;
}

export const assessmentMatchingService = {
  async matchInitial(userId: string): Promise<TemplateWithAttrs | null> {
    const prisma = await getPrisma();
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        trainingGoal: true,
        trainingProfile: true,
      },
    });
    if (!user?.trainingProfile) return null;

    const userCodes = buildUserAttributeSet(user.trainingProfile, user.trainingGoal, null);

    const templates = (await prisma.assessmentTemplate.findMany({
      where: {
        deletedAt: null,
        isPublished: true,
        type: 'initial',
      },
      orderBy: [{ sortOrder: 'asc' }, { createdAt: 'desc' }],
      include: templateMatchInclude,
    })) as unknown as TemplateWithAttrs[];

    return pickBestTemplate(templates, userCodes);
  },

  async matchProgression(userId: string, currentLevel: number): Promise<TemplateWithAttrs | null> {
    const prisma = await getPrisma();
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        trainingGoal: true,
        trainingProfile: true,
      },
    });
    if (!user?.trainingProfile) return null;

    const userCodes = buildUserAttributeSet(user.trainingProfile, user.trainingGoal, null);

    const templates = (await prisma.assessmentTemplate.findMany({
      where: {
        deletedAt: null,
        isPublished: true,
        type: { in: [...PROGRESSION_TYPES] },
        targetLevel: { number: currentLevel },
      },
      orderBy: [{ sortOrder: 'asc' }, { createdAt: 'desc' }],
      include: templateMatchInclude,
    })) as unknown as TemplateWithAttrs[];

    return pickBestTemplate(templates, userCodes);
  },
};

export type { TemplateWithAttrs };
