/**
 * Prescription Engine V1 — Rule-based program recommendation.
 *
 * Flow: Classify user → Filter programs (ProgramAttribute–based) → Return one recommended program.
 */

import { ProgramAttributeMode, type ProgramDomain } from '@prisma/client';
import { getPrisma } from '@/lib/prisma/client';
import { scoreToLevel } from '@/lib/metrics';
import {
  bodyRegionValueCodeFromLabel,
  equipmentValueCodeFromProfileString,
  focusValueCodeFromTargetHint,
  genderValueCodeFromProfile,
  placeValueCodeFromProfile,
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
interface RegionLevel {
  region: string;
  level: number;
  score: number;
  isLimiting: boolean;
}

function parseDomainLevels(raw: unknown): DomainLevel[] {
  if (!Array.isArray(raw)) return [];
  return raw.filter(
    (d) => d && typeof d === 'object' && 'domain' in d && 'level' in d && 'score' in d,
  ) as DomainLevel[];
}

function parseRegionLevels(raw: unknown): RegionLevel[] {
  if (!Array.isArray(raw)) return [];
  return raw.filter(
    (r) => r && typeof r === 'object' && 'region' in r && 'level' in r && 'score' in r,
  ) as RegionLevel[];
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

function buildUserAttributeCodes(
  classification: Classification,
  userTrainingGoal: import('@prisma/client').TrainingGoal | null | undefined,
  availableEquipment: string[],
  profile: { biologicalSex?: string | null; trainingLocation?: string | null },
): Set<string> {
  const codes = new Set<string>();
  codes.add(requiredTypeToDomainValueCode(classification.requiredType));
  const goalCode = trainingGoalToValueCode(userTrainingGoal ?? undefined);
  if (goalCode) codes.add(goalCode);
  for (const eq of availableEquipment) {
    const c = equipmentValueCodeFromProfileString(eq);
    if (c) codes.add(c);
  }
  const focusCode = focusValueCodeFromTargetHint(classification.targetDomain);
  if (focusCode) codes.add(focusCode);
  for (const r of classification.targetRegions) {
    const br = bodyRegionValueCodeFromLabel(r);
    if (br) codes.add(br);
  }
  for (const r of classification.safetyGateCodes) {
    const br = bodyRegionValueCodeFromLabel(r);
    if (br) codes.add(br);
  }
  const gender = genderValueCodeFromProfile(profile.biologicalSex);
  if (gender) codes.add(gender);
  const place = placeValueCodeFromProfile(profile.trainingLocation);
  if (place) codes.add(place);
  return codes;
}

function passesProgramAttributes(p: ProgramForPrescription, userCodes: Set<string>): boolean {
  for (const row of p.programAttributes) {
    const code = row.attributeValue.code;
    if (row.mode === ProgramAttributeMode.REQUIRED && !userCodes.has(code)) {
      return false;
    }
    if (row.mode === ProgramAttributeMode.EXCLUDED && userCodes.has(code)) {
      return false;
    }
  }
  return true;
}

function countOptionalAttributeMatches(p: ProgramForPrescription, userCodes: Set<string>): number {
  let n = 0;
  for (const row of p.programAttributes) {
    if (row.mode === ProgramAttributeMode.OPTIONAL && userCodes.has(row.attributeValue.code)) {
      n += 1;
    }
  }
  return n;
}

function narrowByFocusAndRegions(
  matches: ProgramForPrescription[],
  classification: Classification,
): ProgramForPrescription[] {
  let out = matches;
  if (classification.targetDomain) {
    const fc = focusValueCodeFromTargetHint(classification.targetDomain);
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
  if (classification.targetRegions.length > 0) {
    const wanted = new Set(
      classification.targetRegions
        .map((r) => bodyRegionValueCodeFromLabel(r))
        .filter(Boolean) as string[],
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

function classifyUser(
  assessment: {
    safetyGates: unknown;
    painFlags: unknown;
    parqPassed: boolean;
    symmetryScore: number | null;
    regions: unknown;
  },
  profile: {
    overallLevel: number;
    domainLevels: unknown;
    regionLevels: unknown;
  },
): Classification {
  const gates = Array.isArray(assessment.safetyGates) ? assessment.safetyGates : [];
  const pains = Array.isArray(assessment.painFlags) ? assessment.painFlags : [];
  const safetyGateCodes = gates
    .map((g: Record<string, unknown>) => String(g?.region || ''))
    .filter(Boolean);

  if (gates.length > 0 || pains.length > 0 || !assessment.parqPassed) {
    const painRegions = pains.map((p: Record<string, unknown>) => String(p?.region || '')).filter(Boolean);
    return {
      category: 'SAFETY_BLOCK',
      priority: 1,
      requiredType: 'therapeutic',
      targetDomain: null,
      targetRegions: [...new Set([...safetyGateCodes, ...painRegions])],
      safetyGateCodes,
      reason:
        gates.length > 0
          ? `Safety gates active for: ${safetyGateCodes.join(', ')}`
          : pains.length > 0
            ? `Pain reported in: ${painRegions.join(', ')}`
            : 'PAR-Q+ screening failed',
    };
  }

  const regionLevels = parseRegionLevels(profile.regionLevels);
  const weakRegions = regionLevels.filter((r) => r.score < 25);
  if (weakRegions.length > 0) {
    return {
      category: 'CORRECTION_NEED',
      priority: 2,
      requiredType: 'therapeutic',
      targetDomain: null,
      targetRegions: weakRegions.map((r) => r.region),
      safetyGateCodes,
      reason: `Weak regions: ${weakRegions.map((r) => `${r.region} (${r.score.toFixed(0)})`).join(', ')}`,
    };
  }

  const domainLevels = parseDomainLevels(profile.domainLevels);
  const symmetryDomain = domainLevels.find((d) => d.domain === 'symmetry');
  const symmetryScore = assessment.symmetryScore ?? symmetryDomain?.score ?? 100;
  if (
    symmetryScore < 60 ||
    (symmetryDomain && profile.overallLevel - symmetryDomain.level >= 2)
  ) {
    return {
      category: 'IMBALANCE',
      priority: 3,
      requiredType: 'training',
      targetDomain: 'symmetry',
      targetRegions: [],
      safetyGateCodes,
      reason: `Symmetry imbalance: score ${symmetryScore.toFixed(0)}`,
    };
  }

  const weakDomains = domainLevels.filter((d) => profile.overallLevel - d.level >= 2);
  if (weakDomains.length > 0) {
    const worstDomain = weakDomains.sort((a, b) => a.level - b.level)[0];
    return {
      category: 'WEAKNESS',
      priority: 4,
      requiredType: worstDomain.domain === 'mobility' ? 'mobility' : 'training',
      targetDomain: worstDomain.domain,
      targetRegions: [],
      safetyGateCodes,
      reason: `${worstDomain.domain} at level ${worstDomain.level}, overall at ${profile.overallLevel}`,
    };
  }

  return {
    category: 'NORMAL',
    priority: 5,
    requiredType: 'training',
    targetDomain: null,
    targetRegions: [],
    safetyGateCodes,
    reason: 'All domains balanced',
  };
}

function rankAndPick(
  candidates: ProgramForPrescription[],
  classification: Classification,
  limitingFactor: string | null,
  userCodes: Set<string>,
): ProgramForPrescription | null {
  let matches = candidates.filter(
    (p) =>
      p.programAttributes.length > 0 &&
      isProgramEligibleForAutoAssignment(p) &&
      passesProgramAttributes(p, userCodes),
  );
  matches = narrowByFocusAndRegions(matches, classification);
  matches = [...matches].sort((a, b) => {
    const scoreDiff =
      scoreProgramForLimitingFactor(b, limitingFactor) - scoreProgramForLimitingFactor(a, limitingFactor);
    if (scoreDiff !== 0) return scoreDiff;
    const optDiff = countOptionalAttributeMatches(b, userCodes) - countOptionalAttributeMatches(a, userCodes);
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

    if (!assessment) {
      return {
        classification: {
          category: 'NORMAL',
          priority: 5,
          requiredType: 'training',
          targetDomain: null,
          targetRegions: [],
          safetyGateCodes: [],
          reason: 'No assessment found — recommending default program',
        },
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

    const classification = classifyUser(
      {
        safetyGates: assessment.safetyGates,
        painFlags: assessment.painFlags,
        parqPassed: assessment.parqPassed,
        symmetryScore: assessment.symmetryScore,
        regions: assessment.regions,
      },
      effectiveProfile,
    );
    const limitingFactor = determineLimitingFactor(
      classification.safetyGateCodes,
      effectiveProfile.domainLevels,
    );

    const userPrefs = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        trainingGoal: true,
        trainingProfile: {
          select: {
            availableEquipment: true,
            biologicalSex: true,
            trainingLocation: true,
          },
        },
      },
    });

    const availRaw = userPrefs?.trainingProfile?.availableEquipment;
    const availList = Array.isArray(availRaw) ? (availRaw as string[]) : [];
    const userCodes = buildUserAttributeCodes(
      classification,
      userPrefs?.trainingGoal,
      availList,
      {
        biologicalSex: userPrefs?.trainingProfile?.biologicalSex,
        trainingLocation: userPrefs?.trainingProfile?.trainingLocation,
      },
    );

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
    const best = rankAndPick(typed, classification, limitingFactor, userCodes);

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
    const fallback = rankAndPick(fallbackTyped, classification, limitingFactor, userCodes);

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
