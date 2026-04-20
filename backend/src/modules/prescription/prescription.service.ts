/**
 * Prescription Engine V1 — Rule-based program recommendation.
 *
 * Flow: Classify user → Filter programs → Return one recommended program.
 *
 * Classification priorities (Section 6.2 of architecture plan):
 *   1. SAFETY_BLOCK  — safetyGates/painFlags/parqFailed
 *   2. CORRECTION_NEED — any region score < 25
 *   3. IMBALANCE     — symmetry < 60% or symmetryLevel gap >= 2
 *   4. WEAKNESS      — any domain level gap >= 2
 *   5. NORMAL        — balanced, standard training
 */

import { getPrisma } from '@/lib/prisma/client';
import { scoreToLevel } from '@/lib/metrics';
import { legacyTypeToProgramDomain, programDomainToLegacyString } from '@/lib/program-domain';
import {
  buildAssignmentReason,
  isProgramEligibleForAutoAssignment,
  type ProgramAssignmentReason,
} from '@/modules/programs/program-assignment';

// ── Types ──

export type ClassificationCategory =
  | 'SAFETY_BLOCK'
  | 'CORRECTION_NEED'
  | 'IMBALANCE'
  | 'WEAKNESS'
  | 'NORMAL';

export interface Classification {
  category: ClassificationCategory;
  priority: number;
  requiredType: string; // training | mobility | therapeutic
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

// ── Helpers ──

interface DomainLevel { domain: string; level: number; score: number }
interface RegionLevel { region: string; level: number; score: number; isLimiting: boolean }

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

  return [...domainLevels]
    .sort((a, b) => a.level - b.level || a.score - b.score || a.domain.localeCompare(b.domain))[0]
    ?.domain ?? null;
}

function scoreProgramForLimitingFactor(
  program: { targetDomain: string | null; targetRegions: string[] },
  limitingFactor: string | null,
): number {
  if (!limitingFactor || limitingFactor === 'safety') return 0;
  if (program.targetDomain === limitingFactor) return 3;
  if (program.targetRegions.includes(limitingFactor)) return 1;
  return 0;
}

function collectMatchedFactors(
  program: {
    trainingGoal: string | null;
    targetEquipment: unknown;
    targetDomain: string | null;
    targetRegions: string[];
  },
  userTrainingGoal: string | null | undefined,
  availableEquipment: string[],
  classification: Classification,
  limitingFactor: string | null,
): string[] {
  const factors = ['levelRange'];
  if (userTrainingGoal && program.trainingGoal === userTrainingGoal) {
    factors.push('trainingGoal');
  }

  const requiredEquipment = Array.isArray(program.targetEquipment)
    ? (program.targetEquipment as string[])
    : [];
  if (
    availableEquipment.length > 0 &&
    requiredEquipment.length > 0 &&
    requiredEquipment.some((code) => availableEquipment.includes(code))
  ) {
    factors.push('equipment');
  }

  if (classification.targetDomain && program.targetDomain === classification.targetDomain) {
    factors.push('targetDomain');
  }

  if (
    classification.targetRegions.length > 0 &&
    program.targetRegions.some((region) => classification.targetRegions.includes(region))
  ) {
    factors.push('targetRegions');
  }

  if (classification.safetyGateCodes.length > 0) {
    factors.push('contraindications');
  }

  if (limitingFactor && program.targetDomain === limitingFactor) {
    factors.push('limitingFactor');
  }

  return factors;
}

// ── Classification Logic ──

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
  // Priority 1: SAFETY_BLOCK
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
      reason: gates.length > 0
        ? `Safety gates active for: ${safetyGateCodes.join(', ')}`
        : pains.length > 0
          ? `Pain reported in: ${painRegions.join(', ')}`
          : 'PAR-Q+ screening failed',
    };
  }

  // Priority 2: CORRECTION_NEED — any region score < 25
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

  // Priority 3: IMBALANCE — symmetry < 60 or symmetryLevel gap >= 2
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

  // Priority 4: WEAKNESS — any domain level gap >= 2
  const weakDomains = domainLevels.filter(
    (d) => profile.overallLevel - d.level >= 2,
  );
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

  // Priority 5: NORMAL — balanced training
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

// ── Service ──

export const prescriptionService = {
  /**
   * Generate a program recommendation for a user based on their latest assessment.
   */
  async recommend(userId: string): Promise<PrescriptionResult> {
    const prisma = await getPrisma();

    // 1. Get latest assessment
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

    // 2. Get latest level profile
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

    // 3. Classify user
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
        trainingProfile: { select: { availableEquipment: true } },
      },
    });

    // 4. Filter matching programs
    const programs = await prisma.program.findMany({
      where: {
        isPublished: true,
        deletedAt: null,
        programDomain: legacyTypeToProgramDomain(classification.requiredType),
        levelRangeMin: { lte: overallLevel },
        levelRangeMax: { gte: overallLevel },
      },
      orderBy: { prescriptionPriority: 'asc' },
    });

    // Apply additional filtering
    let matches = programs.filter(isProgramEligibleForAutoAssignment);

    if (userPrefs?.trainingGoal) {
      matches = matches.filter(
        (p) => !p.trainingGoal || p.trainingGoal === userPrefs.trainingGoal,
      );
    }

    const availRaw = userPrefs?.trainingProfile?.availableEquipment;
    const availList = Array.isArray(availRaw) ? (availRaw as string[]) : [];
    if (availList.length > 0) {
      matches = matches.filter((p) => {
        const te = p.targetEquipment;
        if (te == null) return true;
        const req = Array.isArray(te) ? (te as string[]) : [];
        if (req.length === 0) return true;
        return req.some((code) => availList.includes(code));
      });
    }

    // Filter by contraindications (exclude programs blocked by safety gates)
    if (classification.safetyGateCodes.length > 0) {
      matches = matches.filter((p) => {
        const contra = p.contraindications || [];
        return !contra.some((c) => classification.safetyGateCodes.includes(c));
      });
    }

    // Filter by target domain if applicable
    if (classification.targetDomain) {
      const domainMatches = matches.filter(
        (p) => p.targetDomain === classification.targetDomain,
      );
      if (domainMatches.length > 0) {
        matches = domainMatches;
      }
    }

    // Filter by target regions if applicable
    if (classification.targetRegions.length > 0) {
      const regionMatches = matches.filter((p) => {
        const regions = p.targetRegions || [];
        return regions.some((r) => classification.targetRegions.includes(r));
      });
      if (regionMatches.length > 0) {
        matches = regionMatches;
      }
    }

    matches = [...matches].sort((a, b) => {
      const scoreDiff =
        scoreProgramForLimitingFactor(
          { targetDomain: b.targetDomain, targetRegions: b.targetRegions || [] },
          limitingFactor,
        ) -
        scoreProgramForLimitingFactor(
          { targetDomain: a.targetDomain, targetRegions: a.targetRegions || [] },
          limitingFactor,
        );

      if (scoreDiff !== 0) return scoreDiff;
      return a.prescriptionPriority - b.prescriptionPriority;
    });

    // 5. Return best match
    if (matches.length > 0) {
      const best = matches[0];
      return {
        classification,
        recommendedProgram: {
          id: best.id,
          name: best.name as Record<string, string>,
          slug: best.slug,
          type: programDomainToLegacyString(best.programDomain),
          targetDomain: best.targetDomain,
          durationWeeks: best.durationWeeks,
          difficulty: best.difficulty,
          coverImageUrl: best.coverImageUrl,
          matchReason: classification.reason,
        },
        assignmentReason: buildAssignmentReason(
          'selection_algorithm',
          collectMatchedFactors(
            {
              trainingGoal: best.trainingGoal,
              targetEquipment: best.targetEquipment,
              targetDomain: best.targetDomain,
              targetRegions: best.targetRegions || [],
            },
            userPrefs?.trainingGoal,
            availList,
            classification,
            limitingFactor,
          ),
          limitingFactor,
        ),
        fallbackUsed: false,
      };
    }

    // 6. Fallback — any program for this level
    const fallbackPrograms = await prisma.program.findMany({
      where: {
        isPublished: true,
        deletedAt: null,
        levelRangeMin: { lte: overallLevel },
        levelRangeMax: { gte: overallLevel },
      },
      orderBy: { prescriptionPriority: 'asc' },
    });
    const fallback = fallbackPrograms.find(isProgramEligibleForAutoAssignment);

    if (fallback) {
      return {
        classification,
        recommendedProgram: {
          id: fallback.id,
          name: fallback.name as Record<string, string>,
          slug: fallback.slug,
          type: programDomainToLegacyString(fallback.programDomain),
          targetDomain: fallback.targetDomain,
          durationWeeks: fallback.durationWeeks,
          difficulty: fallback.difficulty,
          coverImageUrl: fallback.coverImageUrl,
          matchReason: `Fallback: best available program for level ${overallLevel}`,
        },
        assignmentReason: buildAssignmentReason(
          'fallback_selection',
          ['levelRange'],
          limitingFactor,
        ),
        fallbackUsed: true,
      };
    }

    // 7. No programs found at all
    return {
      classification,
      recommendedProgram: null,
      assignmentReason: null,
      fallbackUsed: true,
    };
  },
};
