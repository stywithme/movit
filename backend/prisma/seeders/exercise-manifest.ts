import type { LoadCapability, MovementPattern, Prisma } from '@prisma/client';
import { inferExerciseBlueprintFields } from './catalog-exercises';

/**
 * Explicit per-slug overrides for exercises shipped in `prisma/Exercise-json`.
 * Slug keys must match JSON filenames (without .json).
 */

export type ExerciseManifestEntry = {
  movementPattern: MovementPattern;
  loadCapability: LoadCapability;
  familyKey: string;
  familyOrder: number;
  /** Optional: override inferred report metrics */
  reportMetrics?: Prisma.InputJsonValue;
};

export const EXERCISE_MANIFEST: Record<string, ExerciseManifestEntry> = {
  assessment_overhead_squat: {
    movementPattern: 'MOBILITY_DRILL',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'squat_pattern_family',
    familyOrder: 5,
  },
  squat: {
    movementPattern: 'SQUAT',
    loadCapability: 'EXTERNAL_LOAD_OPTIONAL',
    familyKey: 'squat_pattern_family',
    familyOrder: 40,
  },
  wall_sit: {
    movementPattern: 'SQUAT',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'squat_pattern_family',
    familyOrder: 25,
  },
  assessment_lunge: {
    movementPattern: 'MOBILITY_DRILL',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'lunge_pattern_family',
    familyOrder: 10,
  },
  lunge: {
    movementPattern: 'LUNGE',
    loadCapability: 'EXTERNAL_LOAD_OPTIONAL',
    familyKey: 'lunge_pattern_family',
    familyOrder: 50,
  },
  deadlift: {
    movementPattern: 'HINGE',
    loadCapability: 'EXTERNAL_LOAD_REQUIRED',
    familyKey: 'hinge_pattern_family',
    familyOrder: 60,
  },
  glute_bridge: {
    movementPattern: 'HINGE',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'hinge_pattern_family',
    familyOrder: 20,
  },
  pushup: {
    movementPattern: 'PUSH_HORIZONTAL',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'push_horizontal_family',
    familyOrder: 30,
  },
  tricep_dips: {
    movementPattern: 'PUSH_HORIZONTAL',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'push_horizontal_family',
    familyOrder: 45,
  },
  shoulder_press: {
    movementPattern: 'PUSH_VERTICAL',
    loadCapability: 'EXTERNAL_LOAD_OPTIONAL',
    familyKey: 'push_vertical_family',
    familyOrder: 40,
  },
  lateral_raises: {
    movementPattern: 'PUSH_VERTICAL',
    loadCapability: 'EXTERNAL_LOAD_OPTIONAL',
    familyKey: 'push_vertical_family',
    familyOrder: 25,
  },
  bicep_curl: {
    movementPattern: 'PULL_HORIZONTAL',
    loadCapability: 'EXTERNAL_LOAD_OPTIONAL',
    familyKey: 'elbow_flexion_family',
    familyOrder: 20,
  },
  bicep_curl_left: {
    movementPattern: 'PULL_HORIZONTAL',
    loadCapability: 'EXTERNAL_LOAD_OPTIONAL',
    familyKey: 'elbow_flexion_family',
    familyOrder: 15,
  },
  bicep_curl_right: {
    movementPattern: 'PULL_HORIZONTAL',
    loadCapability: 'EXTERNAL_LOAD_OPTIONAL',
    familyKey: 'elbow_flexion_family',
    familyOrder: 16,
  },
  plank: {
    movementPattern: 'CORE_BRACE',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'anti_extension_family',
    familyOrder: 20,
  },
  side_plank: {
    movementPattern: 'CORE_BRACE',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'anti_lateral_flexion_family',
    familyOrder: 20,
  },
  crunch: {
    movementPattern: 'CORE_BRACE',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'trunk_flexion_family',
    familyOrder: 20,
  },
  superman: {
    movementPattern: 'CORE_BRACE',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'back_extension_family',
    familyOrder: 15,
  },
  forearm_rest: {
    movementPattern: 'CORE_BRACE',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'anti_extension_family',
    familyOrder: 10,
  },
  arm_hold: {
    movementPattern: 'CORE_BRACE',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'overhead_stability_family',
    familyOrder: 10,
  },
  calf_raises: {
    movementPattern: 'GAIT',
    loadCapability: 'EXTERNAL_LOAD_OPTIONAL',
    familyKey: 'ankle_plantarflexion_family',
    familyOrder: 30,
  },
  assessment_shoulder_mobility: {
    movementPattern: 'MOBILITY_DRILL',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'shoulder_mobility_family',
    familyOrder: 10,
  },
  assessment_forward_fold: {
    movementPattern: 'MOBILITY_DRILL',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'posterior_chain_mobility_family',
    familyOrder: 10,
  },
  assessment_single_leg_balance: {
    movementPattern: 'MOBILITY_DRILL',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'single_leg_balance_family',
    familyOrder: 10,
  },
  desk_test: {
    movementPattern: 'OTHER',
    loadCapability: 'BODYWEIGHT_ONLY',
    familyKey: 'posture_screen_family',
    familyOrder: 5,
  },
};

export function resolveExerciseBlueprintForSlug(
  slug: string,
  ctx: {
    countingMethodCode: string;
    supportsWeight: boolean;
    equipmentCodes: string[];
    categoryCode: string;
  },
): {
  movementPattern: MovementPattern;
  loadCapability: LoadCapability;
  familyKey: string;
  familyOrder: number;
  reportMetrics: Prisma.InputJsonValue;
} {
  const inferred = inferExerciseBlueprintFields(slug, ctx);
  const m = EXERCISE_MANIFEST[slug];
  if (!m) return inferred;
  return {
    movementPattern: m.movementPattern,
    loadCapability: m.loadCapability,
    familyKey: m.familyKey,
    familyOrder: m.familyOrder,
    reportMetrics: m.reportMetrics ?? inferred.reportMetrics,
  };
}
