import type { ExerciseArchetype } from '@prisma/client';

export interface ProgressionCondition {
  metric: string;
  operator: '>=' | '<=' | '>' | '<' | '==';
  value: number;
  window: string;
}

export interface ProgressionActionConfig {
  type: string;
  amount: number;
}

export interface ProfileDefaults {
  repRange?: { min: number; max: number };
  weightBounds?: { min: number; max: number; step: number };
  durationBounds?: { min: number; max: number; step: number };
  qualityGate: { metric: string; threshold: number };
  promotionRule: {
    conditions: ProgressionCondition[];
    action: ProgressionActionConfig;
  };
  regressionRule: {
    conditions: ProgressionCondition[];
    action: ProgressionActionConfig;
  };
  difficultyLadder?: string[];
}

export const ARCHETYPE_DEFAULTS: Record<ExerciseArchetype, ProfileDefaults> = {
  weighted_strength: {
    repRange: { min: 6, max: 15 },
    weightBounds: { min: 0, max: 200, step: 2.5 },
    qualityGate: { metric: 'avgFormScore', threshold: 75 },
    promotionRule: {
      conditions: [
        { metric: 'avgFormScore', operator: '>=', value: 75, window: 'last_2_sessions' },
        { metric: 'completionRate', operator: '>=', value: 90, window: 'last_2_sessions' },
      ],
      action: { type: 'increase_weight', amount: 2.5 },
    },
    regressionRule: {
      conditions: [
        { metric: 'avgFormScore', operator: '<', value: 60, window: 'last_2_sessions' },
      ],
      action: { type: 'decrease_weight', amount: 2.5 },
    },
  },

  bodyweight_dynamic: {
    repRange: { min: 5, max: 25 },
    qualityGate: { metric: 'avgFormScore', threshold: 80 },
    promotionRule: {
      conditions: [
        { metric: 'avgFormScore', operator: '>=', value: 80, window: 'last_2_sessions' },
        { metric: 'completionRate', operator: '>=', value: 95, window: 'last_2_sessions' },
      ],
      action: { type: 'increase_reps', amount: 2 },
    },
    regressionRule: {
      conditions: [
        { metric: 'avgFormScore', operator: '<', value: 65, window: 'last_2_sessions' },
      ],
      action: { type: 'decrease_reps', amount: 2 },
    },
    difficultyLadder: ['assisted', 'standard', 'elevated', 'weighted'],
  },

  isometric_hold: {
    durationBounds: { min: 10, max: 120, step: 5 },
    qualityGate: { metric: 'avgFormScore', threshold: 85 },
    promotionRule: {
      conditions: [
        { metric: 'avgFormScore', operator: '>=', value: 85, window: 'last_2_sessions' },
        { metric: 'completionRate', operator: '>=', value: 90, window: 'last_2_sessions' },
      ],
      action: { type: 'increase_duration', amount: 5 },
    },
    regressionRule: {
      conditions: [
        { metric: 'avgFormScore', operator: '<', value: 70, window: 'last_2_sessions' },
      ],
      action: { type: 'decrease_duration', amount: 5 },
    },
  },

  mobility_rom: {
    durationBounds: { min: 15, max: 60, step: 5 },
    qualityGate: { metric: 'avgSymmetry', threshold: 80 },
    promotionRule: {
      conditions: [
        { metric: 'avgROM', operator: '>=', value: 85, window: 'last_2_sessions' },
        { metric: 'avgSymmetry', operator: '>=', value: 80, window: 'last_2_sessions' },
      ],
      action: { type: 'increase_duration', amount: 5 },
    },
    regressionRule: {
      conditions: [
        { metric: 'avgROM', operator: '<', value: 60, window: 'last_2_sessions' },
      ],
      action: { type: 'decrease_duration', amount: 5 },
    },
  },

  motor_control: {
    repRange: { min: 5, max: 15 },
    durationBounds: { min: 15, max: 60, step: 5 },
    qualityGate: { metric: 'avgStability', threshold: 80 },
    promotionRule: {
      conditions: [
        { metric: 'avgStability', operator: '>=', value: 80, window: 'last_2_sessions' },
        { metric: 'avgFormScore', operator: '>=', value: 80, window: 'last_2_sessions' },
      ],
      action: { type: 'increase_reps', amount: 1 },
    },
    regressionRule: {
      conditions: [
        { metric: 'avgStability', operator: '<', value: 60, window: 'last_2_sessions' },
      ],
      action: { type: 'decrease_reps', amount: 1 },
    },
    difficultyLadder: ['supported', 'standard', 'eyes_closed', 'unstable_surface'],
  },
};
