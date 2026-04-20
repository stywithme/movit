/**
 * Default prescription ranges per training goal (Blueprint).
 * Used when template values are missing and for suggestion / progression fallbacks.
 */

import type { TrainingGoal } from '@prisma/client';

export interface GoalDefaultRanges {
  /** Typical working sets per main lift */
  setsMin: number;
  setsMax: number;
  /** Rep range for strength-style work */
  repsMin: number;
  repsMax: number;
  /** Rest between sets (ms) */
  restBetweenSetsMsMin: number;
  restBetweenSetsMsMax: number;
}

export const GOAL_DEFAULTS: Record<TrainingGoal, GoalDefaultRanges> = {
  STRENGTH: {
    setsMin: 3,
    setsMax: 6,
    repsMin: 1,
    repsMax: 6,
    restBetweenSetsMsMin: 120_000,
    restBetweenSetsMsMax: 300_000,
  },
  HYPERTROPHY: {
    setsMin: 3,
    setsMax: 5,
    repsMin: 6,
    repsMax: 15,
    restBetweenSetsMsMin: 60_000,
    restBetweenSetsMsMax: 120_000,
  },
  POWER: {
    setsMin: 3,
    setsMax: 6,
    repsMin: 1,
    repsMax: 5,
    restBetweenSetsMsMin: 120_000,
    restBetweenSetsMsMax: 300_000,
  },
  GENERAL_HEALTH: {
    setsMin: 2,
    setsMax: 4,
    repsMin: 8,
    repsMax: 15,
    restBetweenSetsMsMin: 45_000,
    restBetweenSetsMsMax: 90_000,
  },
};

export function getGoalDefaults(goal: TrainingGoal | null | undefined): GoalDefaultRanges {
  return GOAL_DEFAULTS[goal ?? 'GENERAL_HEALTH'];
}
