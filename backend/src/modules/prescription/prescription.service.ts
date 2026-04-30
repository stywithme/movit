/**
 * Prescription Engine V1 — Rule-based program recommendation.
 *
 * Flow: Build user attribute set (profile + goal + assessment hints) → Filter programs → Return best match.
 */

import { ProgramAttributeMode, type ProgramDomain } from '@prisma/client';
import { getPrisma } from '@/lib/prisma/client';
import { scoreToLevel } from '@/lib/metrics';
import {
  buildUserAttributeSet,
  countOptionalMatches,
  deriveUserAttributeHintsFromAssessment,
  passesAttributeFilter,
  type EntityAttributeRow,
  type UserAttributeHints,
} from '@/lib/attribute-matching';
import {
  bodyRegionValueCodeFromLabel,
  focusValueCodeFromTargetHint,
  requiredTypeToDomainValueCode,
  trainingGoalToValueCode,
} from '@/lib/program-attribute-codes';
import { typeStringFromProgramDomain } from '@/lib/program-domain';
import {
  buildAssignmentReason,
  getEffectiveProgramDomain,
  isProgramEligibleForAutoAssignment,
  type ProgramAssignmentReason,
} from '@/modules/programs/program-assignment';

export type ClassificationCategory =
  | 'SAFETY_BLOCK'
  | 'CORRECTION_NEED'
  | 'IMBALANCE'
  | 'WEAKNESS'
  | 'NORMAL';

export interface Classification {
  category: ClassificationCategory;
  priority: number;
  requiredType: string;
  targetDomain: string | null;
  targetRegions: string[];
  safetyGateCodes: string[];
  reason: string;
}

export interface PrescriptionResult {
  classification: Classification;
  recommendedProgram: RecommendedProgram | null;
  assignmentReason: ProgramAssignmentReason | null;
  fallbackUsed: boolean;
}

export interface RecommendedProgram {
  id: string;
  name: Record<string, string>;
  slug: string;
  type: string;
  targetDomain: string | null;
  durationWeeks: number;
  difficulty: string;
  coverImageUrl: string | null;
  matchReason: string;
}

type ProgramForPrescription = {
  id: string;
  name: unknown;
  slug: string;
  programDomain: import('@prisma/client').ProgramDomain;
  trainingGoal: import('@prisma/client').TrainingGoal | null;
  targetEquipment: unknown;
  targetDomain: string | null;
  targetRegions: string[];
  contraindications: string[];
  difficulty: string;
  coverImageUrl: string | null;
  durationWeeks: number;
  prescriptionPriority: number;
  weeklySessionTarget: number | null;
  programType: import('@prisma/client').ProgramType;
  autoAssignable: boolean;
  isPublished: boolean;
  deletedAt: Date | null;
  programAttributes: Array<{
    mode: ProgramAttributeMode;
    attributeValue: {
      code: string;
      attribute: { code: string };
    };
  }>;
};

const programPrescriptionInclude = {
  programAttributes: {
    include: { attributeValue: { include: { attribute: true } } },
  },
} as const;

interface DomainLevel {
  domain: string;
  level: number;
  score: number;
}

function parseDomainLevels(raw: unknown): DomainLevel[] {
  if (!Array.isArray(raw)) return [];
  return raw.filter(
    (d) => d && typeof d === 'object' && 'domain' in d && 'level' in d && 'score' in d,
  ) as DomainLevel[];
}

function determineLimitingFactor(
  safetyGateCodes: string[],
  domainLevelsRaw: unknown,
): string | null {
  if (safetyGateCodes.length > 0) return 'safety';

  const domainLevels = parseDomainLevels(domainLevelsRaw);
  if (domainLevels.length === 0) return null;

  return (
    [...domainLevels].sort(
      (a, b) => a.level - b.level || a.score - b.score || a.domain.localeCompare(b.domain),
    )[0]?.domain ?? null
  );
}

function programToRows(p: ProgramForPrescription): EntityAttributeRow[] {
  return p.programAttributes.map((row) => ({
    mode: row.mode,
    attributeValueCode: row.attributeValue.code,
  }));
}

function buildClassificationFromHints(hints: UserAttributeHints): Classification {
  const emptySafety: string[] = [];
  if (hints.requiredType === 'therapeutic' && (hints.regionHints?.length ?? 0) > 0) {
    return {
      category: 'CORRECTION_NEED',
      priority: 2,
      requiredType: 'therapeutic',
      targetDomain: hints.focusHint ?? null,
      targetRegions: hints.regionHints ?? [],
      safetyGateCodes: emptySafety,
      reason: `Weak regions: ${(hints.regionHints ?? []).join(', ')}`,
    };
  }
  if (hints.focusHint === 'symmetry') {
    return {
      category: 'IMBALANCE',
      priority: 3,
      requiredType: 'training',
      targetDomain: 'symmetry',
      targetRegions: [],
      safetyGateCodes: emptySafety,
      reason: 'Symmetry imbalance',
    };
  }
  if (hints.focusHint) {
    const wd = hints.focusHint;
    return {
      category: 'WEAKNESS',
      priority: 4,
      requiredType: hints.requiredType === 'mobility' ? 'mobility' : 'training',
      targetDomain: wd,
      targetRegions: [],
      safetyGateCodes: emptySafety,
      reason: `${wd} lags overall level`,
    };
  }
  return {
    category: 'NORMAL',
    priority: 5,
    requiredType: 'training',
    targetDomain: null,
    targetRegions: [],
    safetyGateCodes: emptySafety,
    reason: 'All domains balanced',
  };
}

function narrowByFocusAndRegions(
  matches: ProgramForPrescription[],
  hints: UserAttributeHints,
): ProgramForPrescription[] {
  let out = matches;
  if (hints.focusHint) {
    const fc = focusValueCodeFromTargetHint(hints.focusHint);
    if (fc) {
      const narrowed = out.filter((p) =>
        p.programAttributes.some(
          (row) =>
            row.attributeValue.attribute.code === 'focus' &&
            row.attributeValue.code === fc &&
            row.mode !== ProgramAttributeMode.EXCLUDED,
        ),
      );
      if (narrowed.length > 0) out = narrowed;
    }
  }
  if ((hints.regionHints?.length ?? 0) > 0) {
    const wanted = new Set(
      (hints.regionHints ?? []).map((r) => bodyRegionValueCodeFromLabel(r)).filter(Boolean) as string[],
    );
    if (wanted.size > 0) {
      const narrowed = out.filter((p) =>
        p.programAttributes.some(
          (row) =>
            row.attributeValue.attribute.code === 'body_region' &&
            wanted.has(row.attributeValue.code) &&
            row.mode !== ProgramAttributeMode.EXCLUDED,
        ),
      );
      if (narrowed.length > 0) out = narrowed;
    }
  }
  return out;
}

function scoreProgramForLimitingFactor(
  program: ProgramForPrescription,
  limitingFactor: string | null,
): number {
  if (!limitingFactor || limitingFactor === 'safety') return 0;
  const fc = focusValueCodeFromTargetHint(limitingFactor);
  if (
    fc &&
    program.programAttributes.some(
      (r) => r.attributeValue.code === fc && r.mode !== ProgramAttributeMode.EXCLUDED,
    )
  ) {
    return 3;
  }
  const br = bodyRegionValueCodeFromLabel(limitingFactor);
  if (
    br &&
    program.programAttributes.some(
      (r) =>
        r.attributeValue.code === br &&
        (r.mode === ProgramAttributeMode.REQUIRED || r.mode === ProgramAttributeMode.OPTIONAL),
    )
  ) {
    return 1;
  }
  return 0;
}

function collectMatchedFactors(
  program: ProgramForPrescription,
  userTrainingGoal: import('@prisma/client').TrainingGoal | null | undefined,
  availableEquipment: string[],
  classification: Classification,
  limitingFactor: string | null,
  userCodes: Set<string>,
): string[] {
  const factors = ['levelRange'];

  if (userTrainingGoal) {
    const gc = trainingGoalToValueCode(userTrainingGoal);
    if (gc && program.programAttributes.some((r) => r.attributeValue.code === gc)) {
      factors.push('trainingGoal');
    }
  }
  const reqEquip = program.programAttributes.filter(
    (r) => r.attributeValue.attribute.code === 'equipment' && r.mode === ProgramAttributeMode.REQUIRED,
  );
  if (
    availableEquipment.length > 0 &&
    reqEquip.length > 0 &&
    reqEquip.some((r) => userCodes.has(r.attributeValue.code))
  ) {
    factors.push('equipment');
  }
  const fc = focusValueCodeFromTargetHint(classification.targetDomain);
  if (
    fc &&
    program.programAttributes.some((r) => r.attributeValue.code === fc && r.mode !== ProgramAttributeMode.EXCLUDED)
  ) {
    factors.push('targetDomain');
  }
  if (classification.targetRegions.length > 0) {
    const hit = classification.targetRegions.some((reg) => {
      const br = bodyRegionValueCodeFromLabel(reg);
      return (
        br &&
        program.programAttributes.some((r) => r.attributeValue.code === br && r.mode !== ProgramAttributeMode.EXCLUDED)
      );
    });
    if (hit) factors.push('targetRegions');
  }
  if (classification.safetyGateCodes.length > 0) {
    factors.push('contraindications');
  }
  if (limitingFactor && scoreProgramForLimitingFactor(program, limitingFactor) >= 3) {
    factors.push('limitingFactor');
  }

  return factors;
}

function recommendedProgramTypeString(p: ProgramForPrescription): string {
  const eff = getEffectiveProgramDomain({ programAttributes: p.programAttributes });
  const domain: ProgramDomain = eff ?? 'TRAINING';
  return typeStringFromProgramDomain(domain);
}

function rankAndPick(
  candidates: ProgramForPrescription[],
  classification: Classification,
  limitingFactor: string | null,
  userCodes: Set<string>,
  hints: UserAttributeHints,
  userTrainingDaysPerWeek: number | null,
): ProgramForPrescription | null {
  let matches = candidates.filter(
    (p) =>
      p.programAttributes.length > 0 &&
      isProgramEligibleForAutoAssignment(p) &&
      passesAttributeFilter(programToRows(p), userCodes),
  );
  matches = narrowByFocusAndRegions(matches, hints);
  matches = [...matches].sort((a, b) => {
    const trainMatch =
      userTrainingDaysPerWeek != null
        ? (b.weeklySessionTarget === userTrainingDaysPerWeek ? 1 : 0) -
          (a.weeklySessionTarget === userTrainingDaysPerWeek ? 1 : 0)
        : 0;
    if (trainMatch !== 0) return trainMatch;
    const scoreDiff =
      scoreProgramForLimitingFactor(b, limitingFactor) - scoreProgramForLimitingFactor(a, limitingFactor);
    if (scoreDiff !== 0) return scoreDiff;
    const optDiff =
      countOptionalMatches(programToRows(b), userCodes) - countOptionalMatches(programToRows(a), userCodes);
    if (optDiff !== 0) return optDiff;
    return a.prescriptionPriority - b.prescriptionPriority;
  });
  return matches[0] ?? null;
}

export const prescriptionService = {
  async recommend(userId: string): Promise<PrescriptionResult> {
    const prisma = await getPrisma();

    const assessment = await prisma.bodyScanResult.findFirst({
      where: { userId },
      orderBy: { completedAt: 'desc' },
    });

    const userPrefs = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        trainingGoal: true,
        trainingProfile: {
          select: {
            availableEquipment: true,
            biologicalSex: true,
            trainingLocation: true,
            healthDisclaimerAccepted: true,
            trainingWeekdays: true,
            availableDaysPerWeek: true,
          },
        },
      },
    });

    const tp = userPrefs?.trainingProfile;
    const userTrainingDaysPerWeek =
      tp?.trainingWeekdays && tp.trainingWeekdays.length > 0
        ? tp.trainingWeekdays.length
        : typeof tp?.availableDaysPerWeek === 'number'
          ? tp.availableDaysPerWeek
          : null;
    if (tp && tp.healthDisclaimerAccepted === false) {
      return {
        classification: {
          category: 'SAFETY_BLOCK',
          priority: 1,
          requiredType: 'therapeutic',
          targetDomain: null,
          targetRegions: [],
          safetyGateCodes: [],
          reason: 'Health disclaimer not accepted — complete onboarding before training recommendations',
        },
        recommendedProgram: null,
        assignmentReason: null,
        fallbackUsed: true,
      };
    }

    const profileForCodes = tp ?? {
      biologicalSex: null,
      trainingLocation: null,
      availableEquipment: null,
      healthDisclaimerAccepted: true,
    };

    if (!assessment) {
      const hints: UserAttributeHints = { requiredType: 'training', focusHint: null, regionHints: [] };
      const classification = buildClassificationFromHints(hints);
      const userCodes = buildUserAttributeSet(profileForCodes, userPrefs?.trainingGoal, hints);
      const domainValueCode = requiredTypeToDomainValueCode(classification.requiredType);
      const overallLevel = 1;

      const programs = await prisma.program.findMany({
        where: {
          isPublished: true,
          deletedAt: null,
          levelRangeMin: { lte: overallLevel },
          levelRangeMax: { gte: overallLevel },
          programAttributes: {
            some: {
              mode: { in: [ProgramAttributeMode.REQUIRED, ProgramAttributeMode.OPTIONAL] },
              attributeValue: { code: domainValueCode },
            },
          },
        },
        include: programPrescriptionInclude,
        orderBy: { prescriptionPriority: 'asc' },
      });

      const typed = programs as ProgramForPrescription[];
      const limitingFactor = determineLimitingFactor(classification.safetyGateCodes, []);
      const best = rankAndPick(typed, classification, limitingFactor, userCodes, hints, userTrainingDaysPerWeek);
      const availList = Array.isArray(profileForCodes.availableEquipment)
        ? (profileForCodes.availableEquipment as string[])
        : [];

      if (best) {
        return {
          classification,
          recommendedProgram: {
            id: best.id,
            name: best.name as Record<string, string>,
            slug: best.slug,
            type: recommendedProgramTypeString(best),
            targetDomain: best.targetDomain,
            durationWeeks: best.durationWeeks,
            difficulty: best.difficulty,
            coverImageUrl: best.coverImageUrl,
            matchReason: classification.reason,
          },
          assignmentReason: buildAssignmentReason(
            'selection_algorithm',
            collectMatchedFactors(
              best,
              userPrefs?.trainingGoal,
              availList,
              classification,
              limitingFactor,
              userCodes,
            ),
            limitingFactor,
          ),
          fallbackUsed: false,
        };
      }

      return {
        classification,
        recommendedProgram: null,
        assignmentReason: null,
        fallbackUsed: true,
      };
    }

    const profile = await prisma.userLevelProfile.findFirst({
      where: { userId },
      orderBy: { classifiedAt: 'desc' },
    });

    const overallLevel = profile?.overallLevel ?? scoreToLevel(assessment.bodyScore);

    const effectiveProfile = {
      overallLevel,
      domainLevels: profile?.domainLevels ?? [],
      regionLevels: profile?.regionLevels ?? [],
    };

    const hints = deriveUserAttributeHintsFromAssessment(
      { symmetryScore: assessment.symmetryScore },
      effectiveProfile,
    );
    const classification = buildClassificationFromHints(hints);
    const limitingFactor = determineLimitingFactor(
      classification.safetyGateCodes,
      effectiveProfile.domainLevels,
    );

    const availList = Array.isArray(profileForCodes.availableEquipment)
      ? (profileForCodes.availableEquipment as string[])
      : [];
    const userCodes = buildUserAttributeSet(profileForCodes, userPrefs?.trainingGoal, hints);

    const domainValueCode = requiredTypeToDomainValueCode(classification.requiredType);

    const programs = await prisma.program.findMany({
      where: {
        isPublished: true,
        deletedAt: null,
        levelRangeMin: { lte: overallLevel },
        levelRangeMax: { gte: overallLevel },
        programAttributes: {
          some: {
            mode: { in: [ProgramAttributeMode.REQUIRED, ProgramAttributeMode.OPTIONAL] },
            attributeValue: { code: domainValueCode },
          },
        },
      },
      include: programPrescriptionInclude,
      orderBy: { prescriptionPriority: 'asc' },
    });

    const typed = programs as ProgramForPrescription[];
    const best = rankAndPick(typed, classification, limitingFactor, userCodes, hints, userTrainingDaysPerWeek);

    if (best) {
      return {
        classification,
        recommendedProgram: {
          id: best.id,
          name: best.name as Record<string, string>,
          slug: best.slug,
          type: recommendedProgramTypeString(best),
          targetDomain: best.targetDomain,
          durationWeeks: best.durationWeeks,
          difficulty: best.difficulty,
          coverImageUrl: best.coverImageUrl,
          matchReason: classification.reason,
        },
        assignmentReason: buildAssignmentReason(
          'selection_algorithm',
          collectMatchedFactors(
            best,
            userPrefs?.trainingGoal,
            availList,
            classification,
            limitingFactor,
            userCodes,
          ),
          limitingFactor,
        ),
        fallbackUsed: false,
      };
    }

    const fallbackPrograms = await prisma.program.findMany({
      where: {
        isPublished: true,
        deletedAt: null,
        levelRangeMin: { lte: overallLevel },
        levelRangeMax: { gte: overallLevel },
      },
      include: programPrescriptionInclude,
      orderBy: { prescriptionPriority: 'asc' },
    });

    const fallbackTyped = fallbackPrograms as ProgramForPrescription[];
    const fallback = rankAndPick(fallbackTyped, classification, limitingFactor, userCodes, hints, userTrainingDaysPerWeek);

    if (fallback) {
      return {
        classification,
        recommendedProgram: {
          id: fallback.id,
          name: fallback.name as Record<string, string>,
          slug: fallback.slug,
          type: recommendedProgramTypeString(fallback),
          targetDomain: fallback.targetDomain,
          durationWeeks: fallback.durationWeeks,
          difficulty: fallback.difficulty,
          coverImageUrl: fallback.coverImageUrl,
          matchReason: `Fallback: best available program for level ${overallLevel}`,
        },
        assignmentReason: buildAssignmentReason(
          'fallback_selection',
          collectMatchedFactors(
            fallback,
            userPrefs?.trainingGoal,
            availList,
            classification,
            limitingFactor,
            userCodes,
          ),
          limitingFactor,
        ),
        fallbackUsed: true,
      };
    }

    return {
      classification,
      recommendedProgram: null,
      assignmentReason: null,
      fallbackUsed: true,
    };
  },
};
