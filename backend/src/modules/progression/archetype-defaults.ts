/**
 * Scientific Progression Defaults per Archetype
 *
 * Each archetype defines:
 *  - allowedAxes: which progression axes apply
 *  - priorityOrder: which axis to try first when promoting
 *  - axis configs: { default, floor, cap, step } per axis
 *  - qualityGate: eligibility thresholds to allow promotion
 *  - promotionPolicy: how many consecutive qualifying sessions needed
 *  - regressionPolicy: when to reduce load/difficulty
 *  - difficultyLadder: ordered list of difficulty codes (if applicable)
 */

import type { TrainingGoal } from '@prisma/client';

export interface AxisConfig {
  default: number;
  floor: number;
  cap: number;
  step: number;
}

export interface QualityGate {
  minFormScore: number;
  minCompletionRate: number;
  minROM?: number;
  minSymmetry?: number;
  minStability?: number;
}

export interface PromotionPolicy {
  requiredStreakSessions: number;
}

export interface RegressionPolicy {
  maxFormScore: number;
}

export interface ArchetypeDefaults {
  allowedAxes: string[];
  priorityOrder: string[];
  repAxis?: AxisConfig;
  loadAxis?: AxisConfig;
  durationAxis?: AxisConfig;
  setAxis?: AxisConfig;
  difficultyLadder?: string[];
  qualityGate: QualityGate;
  promotionPolicy: PromotionPolicy;
  regressionPolicy: RegressionPolicy;
}

export const ARCHETYPE_DEFAULTS: Record<string, ArchetypeDefaults> = {
  weighted_strength: {
    allowedAxes: ['reps', 'load', 'sets'],
    priorityOrder: ['reps', 'load', 'sets'],
    repAxis: { default: 8, floor: 5, cap: 12, step: 1 },
    loadAxis: { default: 0, floor: 0, cap: 200, step: 2.5 },
    setAxis: { default: 3, floor: 2, cap: 5, step: 1 },
    qualityGate: { minFormScore: 70, minCompletionRate: 85 },
    promotionPolicy: { requiredStreakSessions: 2 },
    regressionPolicy: { maxFormScore: 55 },
  },

  bodyweight_dynamic: {
    allowedAxes: ['reps', 'difficulty'],
    priorityOrder: ['reps', 'difficulty'],
    repAxis: { default: 8, floor: 4, cap: 15, step: 1 },
    difficultyLadder: ['beginner', 'intermediate', 'advanced'],
    qualityGate: { minFormScore: 75, minCompletionRate: 85 },
    promotionPolicy: { requiredStreakSessions: 2 },
    regressionPolicy: { maxFormScore: 55 },
  },

  isometric_hold: {
    allowedAxes: ['duration', 'difficulty'],
    priorityOrder: ['duration', 'difficulty'],
    durationAxis: { default: 15, floor: 5, cap: 45, step: 5 },
    difficultyLadder: ['beginner', 'intermediate', 'advanced'],
    qualityGate: { minFormScore: 70, minCompletionRate: 85 },
    promotionPolicy: { requiredStreakSessions: 2 },
    regressionPolicy: { maxFormScore: 55 },
  },

  mobility_rom: {
    allowedAxes: ['reps', 'duration'],
    priorityOrder: ['reps', 'duration'],
    repAxis: { default: 8, floor: 5, cap: 15, step: 1 },
    durationAxis: { default: 3, floor: 2, cap: 10, step: 1 },
    qualityGate: { minFormScore: 70, minCompletionRate: 80, minROM: 70 },
    promotionPolicy: { requiredStreakSessions: 3 },
    regressionPolicy: { maxFormScore: 50 },
  },

  motor_control: {
    allowedAxes: ['reps', 'difficulty'],
    priorityOrder: ['reps', 'difficulty'],
    repAxis: { default: 6, floor: 3, cap: 12, step: 1 },
    difficultyLadder: ['beginner', 'intermediate', 'advanced'],
    qualityGate: { minFormScore: 80, minCompletionRate: 90, minStability: 70 },
    promotionPolicy: { requiredStreakSessions: 3 },
    regressionPolicy: { maxFormScore: 60 },
  },
};

const GOAL_PRIORITY_OVERRIDES: Record<TrainingGoal, string[]> = {
  STRENGTH: ['load', 'reps', 'sets', 'difficulty', 'duration'],
  HYPERTROPHY: ['reps', 'sets', 'load', 'difficulty', 'duration'],
  POWER: ['load', 'difficulty', 'reps', 'sets', 'duration'],
  GENERAL_HEALTH: ['reps', 'duration', 'sets', 'difficulty', 'load'],
};

export function getArchetypeDefaults(archetype: string): ArchetypeDefaults | null {
  return ARCHETYPE_DEFAULTS[archetype] ?? null;
}

export function applyGoalPriorityOrder(
  basePriorityOrder: string[],
  allowedAxes: string[],
  trainingGoal: TrainingGoal | null | undefined,
): string[] {
  const desired = GOAL_PRIORITY_OVERRIDES[trainingGoal ?? 'GENERAL_HEALTH'];
  const seen = new Set<string>();

  const merged = [...desired, ...basePriorityOrder].filter((axis) => {
    if (!allowedAxes.includes(axis) || seen.has(axis)) return false;
    seen.add(axis);
    return true;
  });

  return merged.length > 0 ? merged : basePriorityOrder.filter((axis) => allowedAxes.includes(axis));
}

export function buildDefaultProfile(archetype: string) {
  const defaults = getArchetypeDefaults(archetype);
  if (!defaults) return null;

  return {
    archetype,
    allowedAxes: defaults.allowedAxes,
    priorityOrder: defaults.priorityOrder,
    repAxis: defaults.repAxis ?? null,
    loadAxis: defaults.loadAxis ?? null,
    durationAxis: defaults.durationAxis ?? null,
    setAxis: defaults.setAxis ?? null,
    difficultyLadder: defaults.difficultyLadder ?? null,
    qualityGate: defaults.qualityGate,
    promotionPolicy: defaults.promotionPolicy,
    regressionPolicy: defaults.regressionPolicy,
    isAutoGenerated: true,
  };
}
