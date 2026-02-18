/**
 * Level Profile Service
 * =====================
 *
 * Calculates and manages UserLevelProfile from BodyScanResult data.
 *
 * Flow:
 *   BodyScanResult → scoreToLevel() per dimension → UserLevelProfile
 *
 * The profile includes:
 *   - overallLevel (1-5): derived from bodyScore
 *   - domainLevels[]: per-domain (mobility, control, symmetry, safety)
 *   - regionLevels[]: per-body-region
 *   - limitingFactors[]: domains/regions significantly behind overallLevel
 */

import { getPrisma } from '@/lib/prisma/client';
import { scoreToLevel, getLevelByNumber } from '@/lib/metrics';

// ============================================
// TYPES
// ============================================

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

interface LimitingFactor {
  type: 'domain' | 'region';
  code: string;
  currentLevel: number;
  targetLevel: number;
  gap: number;
}

export interface UserLevelProfileData {
  id: string;
  userId: string;
  overallLevel: number;
  bodyScore: number;
  domainLevels: DomainLevel[];
  regionLevels: RegionLevel[];
  limitingFactors: LimitingFactor[];
  assessmentId: string;
  classifiedAt: string;
  levelInfo: {
    number: number;
    code: string;
    name: { en: string; ar: string };
    description: { en: string; ar: string } | null;
    color: string | null;
  };
}

function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toFiniteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function toNonEmptyString(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function parseDomainLevels(value: unknown): DomainLevel[] {
  if (!Array.isArray(value)) return [];

  return value
    .map((item) => {
      if (!isObjectRecord(item)) return null;

      const domain = toNonEmptyString(item.domain);
      const level = toFiniteNumber(item.level);
      const score = toFiniteNumber(item.score);

      if (!domain || level == null || score == null) return null;

      return {
        domain,
        level: Math.round(level),
        score,
      } satisfies DomainLevel;
    })
    .filter((item): item is DomainLevel => item !== null);
}

function parseRegionLevels(value: unknown): RegionLevel[] {
  if (!Array.isArray(value)) return [];

  return value
    .map((item) => {
      if (!isObjectRecord(item)) return null;

      const region = toNonEmptyString(item.region);
      const level = toFiniteNumber(item.level);
      const score = toFiniteNumber(item.score);
      const isLimiting = typeof item.isLimiting === 'boolean' ? item.isLimiting : false;

      if (!region || level == null || score == null) return null;

      return {
        region,
        level: Math.round(level),
        score,
        isLimiting,
      } satisfies RegionLevel;
    })
    .filter((item): item is RegionLevel => item !== null);
}

function parseLimitingFactors(value: unknown): LimitingFactor[] {
  if (!Array.isArray(value)) return [];

  return value
    .map((item) => {
      if (!isObjectRecord(item)) return null;

      const type = item.type === 'domain' || item.type === 'region' ? item.type : null;
      const code = toNonEmptyString(item.code);
      const currentLevel = toFiniteNumber(item.currentLevel);
      const targetLevel = toFiniteNumber(item.targetLevel);
      const gap = toFiniteNumber(item.gap);

      if (!type || !code || currentLevel == null || targetLevel == null || gap == null) return null;

      return {
        type,
        code,
        currentLevel: Math.round(currentLevel),
        targetLevel: Math.round(targetLevel),
        gap: Math.round(gap),
      } satisfies LimitingFactor;
    })
    .filter((item): item is LimitingFactor => item !== null);
}

// ============================================
// SERVICE
// ============================================

export const levelProfileService = {
  /**
   * Calculate and save a UserLevelProfile from a BodyScanResult.
   *
   * Called automatically after each assessment upload (Phase 0 hook).
   */
  async calculateFromAssessment(assessmentId: string): Promise<UserLevelProfileData | null> {
    const prisma = await getPrisma();

    const assessment = await prisma.bodyScanResult.findUnique({
      where: { id: assessmentId },
    });

    if (!assessment) {
      console.warn(`[LevelProfile] Assessment not found: ${assessmentId}`);
      return null;
    }

    const { userId, bodyScore, mobilityScore, controlScore, symmetryScore, safetyScore } = assessment;

    // Calculate overall level from bodyScore
    const overallLevel = scoreToLevel(bodyScore);

    // Calculate domain levels
    const domainLevels: DomainLevel[] = [
      { domain: 'mobility', level: scoreToLevel(mobilityScore), score: mobilityScore },
      { domain: 'control', level: scoreToLevel(controlScore), score: controlScore },
      { domain: 'safety', level: scoreToLevel(safetyScore), score: safetyScore },
    ];

    if (symmetryScore != null) {
      domainLevels.push({
        domain: 'symmetry',
        level: scoreToLevel(symmetryScore),
        score: symmetryScore,
      });
    }

    // Calculate region levels from the assessment's regions JSON
    const regionLevels: RegionLevel[] = [];
    const regions = Array.isArray(assessment.regions) ? assessment.regions : [];

    if (regions.length > 0) {
      // Group by region (may have left/right/center)
      const regionMap = new Map<string, number[]>();

      for (const r of regions) {
        if (!isObjectRecord(r)) continue;

        const regionCode =
          toNonEmptyString(r.region)?.toLowerCase() ?? 'unknown';
        const score = toFiniteNumber(r.regionalScore) ?? 0;

        if (!regionMap.has(regionCode)) {
          regionMap.set(regionCode, []);
        }
        regionMap.get(regionCode)!.push(score);
      }

      for (const [region, scores] of regionMap) {
        const avgScore = scores.reduce((a, b) => a + b, 0) / scores.length;
        const level = scoreToLevel(avgScore);
        regionLevels.push({
          region,
          level,
          score: Math.round(avgScore * 10) / 10,
          isLimiting: level < overallLevel - 1,
        });
      }
    }

    // Detect limiting factors (domains/regions behind overallLevel by 2+ levels)
    const limitingFactors: LimitingFactor[] = [];

    for (const dl of domainLevels) {
      if (dl.level < overallLevel - 1) {
        limitingFactors.push({
          type: 'domain',
          code: dl.domain,
          currentLevel: dl.level,
          targetLevel: overallLevel,
          gap: overallLevel - dl.level,
        });
      }
    }

    for (const rl of regionLevels) {
      if (rl.level < overallLevel - 1) {
        limitingFactors.push({
          type: 'region',
          code: rl.region,
          currentLevel: rl.level,
          targetLevel: overallLevel,
          gap: overallLevel - rl.level,
        });
      }
    }

    // Sort limiting factors by gap (biggest gap first)
    limitingFactors.sort((a, b) => b.gap - a.gap);

    // Save the profile (upsert: one profile per assessment)
    const profile = await prisma.userLevelProfile.upsert({
      where: { assessmentId },
      create: {
        userId,
        overallLevel,
        bodyScore,
        domainLevels: domainLevels as any,
        regionLevels: regionLevels as any,
        limitingFactors: limitingFactors as any,
        assessmentId,
      },
      update: {
        overallLevel,
        bodyScore,
        domainLevels: domainLevels as any,
        regionLevels: regionLevels as any,
        limitingFactors: limitingFactors as any,
      },
    });

    const levelInfo = getLevelByNumber(overallLevel);

    return {
      id: profile.id,
      userId: profile.userId,
      overallLevel: profile.overallLevel,
      bodyScore: profile.bodyScore,
      domainLevels,
      regionLevels,
      limitingFactors,
      assessmentId: profile.assessmentId,
      classifiedAt: profile.classifiedAt.toISOString(),
      levelInfo: {
        number: levelInfo.number,
        code: levelInfo.code,
        name: levelInfo.name as { en: string; ar: string },
        description: levelInfo.description as { en: string; ar: string },
        color: levelInfo.color,
      },
    };
  },

  /**
   * Get the latest UserLevelProfile for a user.
   */
  async getLatest(userId: string): Promise<UserLevelProfileData | null> {
    const prisma = await getPrisma();

    const profile = await prisma.userLevelProfile.findFirst({
      where: { userId },
      orderBy: { classifiedAt: 'desc' },
    });

    if (!profile) return null;

    const levelInfo = getLevelByNumber(profile.overallLevel);

    return {
      id: profile.id,
      userId: profile.userId,
      overallLevel: profile.overallLevel,
      bodyScore: profile.bodyScore,
      domainLevels: parseDomainLevels(profile.domainLevels),
      regionLevels: parseRegionLevels(profile.regionLevels),
      limitingFactors: parseLimitingFactors(profile.limitingFactors),
      assessmentId: profile.assessmentId,
      classifiedAt: profile.classifiedAt.toISOString(),
      levelInfo: {
        number: levelInfo.number,
        code: levelInfo.code,
        name: levelInfo.name as { en: string; ar: string },
        description: levelInfo.description as { en: string; ar: string },
        color: levelInfo.color,
      },
    };
  },

  /**
   * Get the level profile history for a user (all profiles ordered by date).
   */
  async getHistory(userId: string): Promise<UserLevelProfileData[]> {
    const prisma = await getPrisma();

    const profiles = await prisma.userLevelProfile.findMany({
      where: { userId },
      orderBy: { classifiedAt: 'desc' },
    });

    return profiles.map((profile) => {
      const levelInfo = getLevelByNumber(profile.overallLevel);
      return {
        id: profile.id,
        userId: profile.userId,
        overallLevel: profile.overallLevel,
        bodyScore: profile.bodyScore,
        domainLevels: parseDomainLevels(profile.domainLevels),
        regionLevels: parseRegionLevels(profile.regionLevels),
        limitingFactors: parseLimitingFactors(profile.limitingFactors),
        assessmentId: profile.assessmentId,
        classifiedAt: profile.classifiedAt.toISOString(),
        levelInfo: {
          number: levelInfo.number,
          code: levelInfo.code,
          name: levelInfo.name as { en: string; ar: string },
          description: levelInfo.description as { en: string; ar: string },
          color: levelInfo.color,
        },
      };
    });
  },
};
