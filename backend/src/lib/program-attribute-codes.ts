import type { ProgramDomain, TrainingGoal } from '@prisma/client';

/** AttributeValue.code for program domain (seed: `domain` attribute). */
export const PROGRAM_DOMAIN_VALUE_CODE: Record<ProgramDomain, string> = {
  TRAINING: 'pd_training',
  MOBILITY: 'pd_mobility',
  THERAPEUTIC: 'pd_therapeutic',
};

/** AttributeValue.code for training goal (seed: `goal` attribute). */
export const TRAINING_GOAL_VALUE_CODE: Record<TrainingGoal, string> = {
  STRENGTH: 'pg_strength',
  HYPERTROPHY: 'pg_hypertrophy',
  POWER: 'pg_power',
  GENERAL_HEALTH: 'pg_general_health',
};

/** Classification `requiredType` (training | mobility | therapeutic) → domain value code. */
export function requiredTypeToDomainValueCode(requiredType: string): string {
  const t = (requiredType || 'training').toLowerCase();
  if (t === 'mobility') return PROGRAM_DOMAIN_VALUE_CODE.MOBILITY;
  if (t === 'therapeutic') return PROGRAM_DOMAIN_VALUE_CODE.THERAPEUTIC;
  return PROGRAM_DOMAIN_VALUE_CODE.TRAINING;
}

export function trainingGoalToValueCode(goal: TrainingGoal | null | undefined): string | null {
  if (!goal) return null;
  return TRAINING_GOAL_VALUE_CODE[goal] ?? null;
}

/** Region labels from assessment / profile → `body_region` value codes. */
export function bodyRegionValueCodeFromLabel(region: string): string | null {
  const r = region.trim().toLowerCase();
  const map: Record<string, string> = {
    shoulder: 'pbr_shoulder',
    hip: 'pbr_hip',
    spine: 'pbr_spine',
    knee: 'pbr_knee',
    core: 'pbr_core',
    balance: 'pbr_balance',
  };
  return map[r] ?? null;
}

/** Classification / program focus hint → `focus` value code. */
export function focusValueCodeFromTargetHint(domain: string | null | undefined): string | null {
  if (!domain || !domain.trim()) return null;
  const d = domain.trim().toLowerCase();
  const map: Record<string, string> = {
    symmetry: 'pf_symmetry',
    mobility: 'pf_mobility',
    strength: 'pf_strength',
    control: 'pf_control',
    upper_body: 'pf_upper_body',
    lower_body: 'pf_lower_body',
    full_body: 'pf_full_body',
  };
  return map[d] ?? null;
}

const KNOWN_EQUIPMENT_CODES = new Set([
  'bodyweight',
  'dumbbell',
  'barbell',
  'kettlebell',
  'resistance_band',
  'bands',
  'pull_up_bar',
  'bench',
  'mat',
  'cable',
  'machine',
]);

/** Profile / JSON equipment string → equipment AttributeValue.code (matches seed). */
export function equipmentValueCodeFromProfileString(eq: string): string | null {
  const raw = eq.trim().toLowerCase().replace(/\s+/g, '_').replace(/-/g, '_');
  const map: Record<string, string> = {
    barbell: 'barbell',
    dumbbell: 'dumbbell',
    cable: 'cable',
    machine: 'machine',
    bodyweight: 'bodyweight',
    kettlebell: 'kettlebell',
    bands: 'bands',
    band: 'bands',
    resistance_band: 'resistance_band',
    resistance_bands: 'bands',
    pull_up_bar: 'pull_up_bar',
    pullup_bar: 'pull_up_bar',
    pull_up: 'pull_up_bar',
    bench: 'bench',
    mat: 'mat',
    gym_mat: 'mat',
  };
  const mapped = map[raw];
  if (mapped) return mapped;
  if (KNOWN_EQUIPMENT_CODES.has(raw)) return raw;
  return null;
}

export function genderValueCodeFromProfile(biologicalSex: string | null | undefined): string | null {
  const s = (biologicalSex || '').trim().toLowerCase();
  if (s === 'male' || s === 'm') return 'pgen_male';
  if (s === 'female' || s === 'f') return 'pgen_female';
  if (s === 'unisex' || s === 'other') return 'pgen_unisex';
  return null;
}

export function placeValueCodeFromProfile(trainingLocation: string | null | undefined): string | null {
  const s = (trainingLocation || '').trim().toLowerCase().replace(/\s+/g, '_');
  if (s === 'gym' || s === 'commercial_gym' || s === 'fitness_center') return 'pl_gym';
  if (s === 'home') return 'pl_home';
  if (s === 'outdoor' || s === 'outside' || s === 'park') return 'pl_outdoor';
  return null;
}
