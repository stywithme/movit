/**
 * Shared attribute-based matching for programs and assessment templates.
 */

import type { ProgramAttributeMode, TrainingGoal, TrainingProfile } from '@prisma/client';
import {
  bodyRegionValueCodeFromLabel,
  equipmentValueCodeFromProfileString,
  focusValueCodeFromTargetHint,
  genderValueCodeFromProfile,
  placeValueCodeFromProfile,
  requiredTypeToDomainValueCode,
  trainingGoalToValueCode,
} from '@/lib/program-attribute-codes';

export type AttributeMode = ProgramAttributeMode;

export interface EntityAttributeRow {
  mode: AttributeMode;
  attributeValueCode: string;
}

export interface UserAttributeHints {
  /** e.g. therapeutic / mobility / training — maps to domain attribute value */
  requiredType?: string;
  /** Focus hint (symmetry, mobility, …) */
  focusHint?: string | null;
  /** Body region labels → codes added to the set */
  regionHints?: string[];
}

export function passesAttributeFilter(
  entityAttributes: EntityAttributeRow[],
  userCodes: Set<string>,
): boolean {
  for (const row of entityAttributes) {
    const code = row.attributeValueCode;
    if (row.mode === 'REQUIRED' && !userCodes.has(code)) {
      return false;
    }
    if (row.mode === 'EXCLUDED' && userCodes.has(code)) {
      return false;
    }
  }
  return true;
}

export function countOptionalMatches(
  entityAttributes: EntityAttributeRow[],
  userCodes: Set<string>,
): number {
  let n = 0;
  for (const row of entityAttributes) {
    if (row.mode === 'OPTIONAL' && userCodes.has(row.attributeValueCode)) {
      n += 1;
    }
  }
  return n;
}

/**
 * Builds the user-side attribute code set from profile, goal, and optional hints
 * (from assessment-derived classification enrichment).
 */
interface DomainLevel {
  domain: string;
  level: number;
  score: number;
}

interface RegionLevel {
  region: string;
  level: number;
  score: number;
  isLimiting?: boolean;
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

/**
 * Enrichment hints from the latest assessment + level profile (no PAR-Q / safety-gate path).
 */
export function deriveUserAttributeHintsFromAssessment(
  assessment: { symmetryScore: number | null },
  profile: { overallLevel: number; domainLevels: unknown; regionLevels: unknown },
): UserAttributeHints {
  const regionLevels = parseRegionLevels(profile.regionLevels);
  const weakRegions = regionLevels.filter((r) => r.score < 25);
  if (weakRegions.length > 0) {
    return {
      requiredType: 'therapeutic',
      focusHint: null,
      regionHints: weakRegions.map((r) => r.region),
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
      requiredType: 'training',
      focusHint: 'symmetry',
      regionHints: [],
    };
  }

  const weakDomains = domainLevels.filter((d) => profile.overallLevel - d.level >= 2);
  if (weakDomains.length > 0) {
    const worstDomain = weakDomains.sort((a, b) => a.level - b.level)[0]!;
    return {
      requiredType: worstDomain.domain === 'mobility' ? 'mobility' : 'training',
      focusHint: worstDomain.domain,
      regionHints: [],
    };
  }

  return { requiredType: 'training', focusHint: null, regionHints: [] };
}

export function buildUserAttributeSet(
  profile: Pick<TrainingProfile, 'biologicalSex' | 'trainingLocation' | 'availableEquipment' | 'healthDisclaimerAccepted'>,
  userGoal: TrainingGoal | null | undefined,
  hints?: UserAttributeHints | null,
): Set<string> {
  const codes = new Set<string>();

  const requiredType = hints?.requiredType ?? 'training';
  codes.add(requiredTypeToDomainValueCode(requiredType));

  const goalCode = trainingGoalToValueCode(userGoal ?? undefined);
  if (goalCode) codes.add(goalCode);

  const availRaw = profile.availableEquipment;
  const availList = Array.isArray(availRaw) ? (availRaw as string[]) : [];
  for (const eq of availList) {
    const c = equipmentValueCodeFromProfileString(eq);
    if (c) codes.add(c);
  }

  const focusCode = focusValueCodeFromTargetHint(hints?.focusHint ?? null);
  if (focusCode) codes.add(focusCode);

  for (const r of hints?.regionHints ?? []) {
    const br = bodyRegionValueCodeFromLabel(r);
    if (br) codes.add(br);
  }

  const gender = genderValueCodeFromProfile(profile.biologicalSex);
  if (gender) codes.add(gender);

  const place = placeValueCodeFromProfile(profile.trainingLocation);
  if (place) codes.add(place);

  return codes;
}
