/**
 * Coverage matrix constants for seed validation (Blueprint + schema).
 * Comments in English per project convention.
 */

export const TRAINING_GOALS = ['STRENGTH', 'HYPERTROPHY', 'POWER', 'GENERAL_HEALTH'] as const;
export type TrainingGoalValue = (typeof TRAINING_GOALS)[number];

export const PROGRAM_DOMAINS = ['TRAINING', 'MOBILITY', 'THERAPEUTIC'] as const;
export type ProgramDomainValue = (typeof PROGRAM_DOMAINS)[number];

export const PROGRAM_TYPES = ['SYSTEM', 'COACH', 'CUSTOM'] as const;
export type ProgramTypeValue = (typeof PROGRAM_TYPES)[number];

/** User level bands used by seeded SYSTEM training programs (1–5 scale). */
export const LEVEL_BANDS: ReadonlyArray<{ min: number; max: number; label: string }> = [
  { min: 1, max: 2, label: 'L1-2' },
  { min: 2, max: 3, label: 'L2-3' },
  { min: 3, max: 4, label: 'L3-4' },
  { min: 4, max: 5, label: 'L4-5' },
];

/** Four exercise content kinds derived from counting + load (Charter / Final-Progression-Spec). */
export const EXERCISE_CONTENT_KINDS = [
  'weighted_rep',
  'bodyweight_rep',
  'weighted_hold',
  'bodyweight_hold',
] as const;
export type ExerciseContentKind = (typeof EXERCISE_CONTENT_KINDS)[number];

export function deriveExerciseContentKind(input: {
  countingMethodCode: string;
  supportsWeight: boolean;
}): ExerciseContentKind {
  const hold = input.countingMethodCode.toLowerCase() === 'hold';
  if (hold && input.supportsWeight) return 'weighted_hold';
  if (hold) return 'bodyweight_hold';
  if (input.supportsWeight) return 'weighted_rep';
  return 'bodyweight_rep';
}

/** Minimum curated exercise count after JSON + library extensions (Blueprint library depth). */
export const MIN_CURATED_EXERCISE_COUNT = 100;

/** ACSM-oriented session design hints (validators use these as soft checks). */
export const ACSM_SESSION_HINTS = {
  strengthMinSessionsPerWeek: 2,
  hypertrophyMinWeeklySetsPerMuscle: 10,
  powerRepSetProductMax: 24,
} as const;

/** Assessment template exercise slugs (must remain after seed). */
export const ASSESSMENT_EXERCISE_SLUGS = [
  'assessment_overhead_squat',
  'assessment_lunge',
  'assessment_shoulder_mobility',
  'assessment_forward_fold',
  'assessment_single_leg_balance',
] as const;

/** Programs required by user-program / QA fixtures. */
export const REQUIRED_USER_FIXTURE_PROGRAM_SLUGS = ['starter-4-weeks'] as const;
