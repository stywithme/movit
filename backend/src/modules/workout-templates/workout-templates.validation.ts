/**
 * Workouts Validation
 * ====================
 * 
 * Validation helpers for workout data.
 */

import type { 
  CreateWorkoutInput, 
  UpdateWorkoutInput,
  WorkoutExerciseInput,
  WorkoutPhaseInput,
} from './workout-templates.types';

// ============================================
// VALIDATION HELPERS
// ============================================

/**
 * Validate workout exercise input
 */
export function validateWorkoutExercise(
  exercise: WorkoutExerciseInput,
  index: number
): string[] {
  const errors: string[] = [];
  const prefix = `Exercise ${index + 1}`;

  if (!exercise.exerciseId) {
    errors.push(`${prefix}: exerciseId is required`);
  }

  if (exercise.variantIndex !== undefined && exercise.variantIndex < 0) {
    errors.push(`${prefix}: variantIndex must be non-negative`);
  }

  if (exercise.targetReps !== undefined && exercise.targetReps <= 0) {
    errors.push(`${prefix}: targetReps must be positive`);
  }

  if (exercise.targetRepsPerSet?.some((value) => value <= 0)) {
    errors.push(`${prefix}: all targetRepsPerSet values must be positive`);
  }

  if (exercise.targetDuration !== undefined && exercise.targetDuration <= 0) {
    errors.push(`${prefix}: targetDuration must be positive`);
  }

  const hasTargetReps =
    exercise.targetReps !== undefined ||
    (exercise.targetRepsPerSet !== undefined && exercise.targetRepsPerSet.length > 0);
  const hasTargetDuration = exercise.targetDuration !== undefined;
  if (!hasTargetReps && !hasTargetDuration) {
    errors.push(`${prefix}: either targetReps/targetRepsPerSet or targetDuration is required`);
  }
  if (hasTargetReps && hasTargetDuration) {
    errors.push(`${prefix}: provide only one target (reps or duration), not both`);
  }

  if (exercise.sets !== undefined && exercise.sets <= 0) {
    errors.push(`${prefix}: sets must be positive`);
  }

  if (exercise.restBetweenSetsMs !== undefined && exercise.restBetweenSetsMs < 0) {
    errors.push(`${prefix}: restBetweenSetsMs must be non-negative`);
  }

  if (exercise.restBetweenSetsPerSetMs?.some((value) => value < 0)) {
    errors.push(`${prefix}: all restBetweenSetsPerSetMs values must be non-negative`);
  }

  if (exercise.restAfterExerciseMs !== undefined && exercise.restAfterExerciseMs < 0) {
    errors.push(`${prefix}: restAfterExerciseMs must be non-negative`);
  }

  if (exercise.weightKg !== undefined && exercise.weightKg < 0) {
    errors.push(`${prefix}: weightKg must be non-negative`);
  }

  if (exercise.weightPerSet?.some((value) => value < 0)) {
    errors.push(`${prefix}: all weightPerSet values must be non-negative`);
  }

  return errors;
}

/**
 * Validate nested workout phase input
 */
export function validateWorkoutPhase(
  phase: WorkoutPhaseInput,
  index: number
): string[] {
  const errors: string[] = [];
  const prefix = `Phase ${index + 1}`;

  if (!phase.phaseId) {
    errors.push(`${prefix}: phaseId is required`);
  }

  if (phase.sortOrder !== undefined && phase.sortOrder < 0) {
    errors.push(`${prefix}: sortOrder must be non-negative`);
  }

  if (
    phase.maxContinueTimeMsOverride !== undefined &&
    phase.maxContinueTimeMsOverride !== null &&
    phase.maxContinueTimeMsOverride < 0
  ) {
    errors.push(`${prefix}: maxContinueTimeMsOverride must be non-negative`);
  }

  if (phase.exercises && phase.exercises.length > 0) {
    for (let i = 0; i < phase.exercises.length; i++) {
      errors.push(...validateWorkoutExercise(phase.exercises[i], i).map((error) => `${prefix}: ${error}`));
    }
  }

  return errors;
}

/**
 * Validate create workout input
 */
export function validateCreateWorkout(input: CreateWorkoutInput): string[] {
  const errors: string[] = [];

  // Required fields
  if (!input.name?.en) {
    errors.push('English name is required');
  }
  if (!input.name?.ar) {
    errors.push('Arabic name is required');
  }

  if (input.estimatedDurationMin !== undefined && input.estimatedDurationMin < 0) {
    errors.push('estimatedDurationMin must be non-negative');
  }

  if (input.isFeatured !== undefined && typeof input.isFeatured !== 'boolean') {
    errors.push('isFeatured must be a boolean');
  }

  // Exercises validation
  if (input.exercises && input.exercises.length > 0) {
    for (let i = 0; i < input.exercises.length; i++) {
      errors.push(...validateWorkoutExercise(input.exercises[i], i));
    }
  }

  if (input.phases && input.phases.length > 0) {
    for (let i = 0; i < input.phases.length; i++) {
      errors.push(...validateWorkoutPhase(input.phases[i], i));
    }
  }

  return errors;
}

/**
 * Validate update workout input
 */
export function validateUpdateWorkout(input: UpdateWorkoutInput): string[] {
  const errors: string[] = [];

  if (input.estimatedDurationMin !== undefined && input.estimatedDurationMin < 0) {
    errors.push('estimatedDurationMin must be non-negative');
  }

  if (input.isFeatured !== undefined && typeof input.isFeatured !== 'boolean') {
    errors.push('isFeatured must be a boolean');
  }

  // Validate status if provided
  if (input.status && !['draft', 'published'].includes(input.status)) {
    errors.push('Invalid status. Must be "draft" or "published"');
  }

  // Exercises validation
  if (input.exercises && input.exercises.length > 0) {
    for (let i = 0; i < input.exercises.length; i++) {
      errors.push(...validateWorkoutExercise(input.exercises[i], i));
    }
  }

  if (input.phases && input.phases.length > 0) {
    for (let i = 0; i < input.phases.length; i++) {
      errors.push(...validateWorkoutPhase(input.phases[i], i));
    }
  }

  return errors;
}

/**
 * Count exercises from nested phases or the legacy flat list.
 */
export function countWorkoutExercises(workout: {
  exercises?: unknown[];
  phases?: Array<{ exercises?: unknown[] }>;
}): number {
  if (workout.phases && workout.phases.length > 0) {
    return workout.phases.reduce((sum, phase) => sum + (phase.exercises?.length ?? 0), 0);
  }
  return workout.exercises?.length ?? 0;
}

/**
 * Validate workout can be published
 */
export function validateCanPublish(workout: {
  name: { ar?: string; en?: string };
  exercises?: unknown[];
  phases?: Array<{ exercises?: unknown[] }>;
}): string[] {
  const errors: string[] = [];

  if (!workout.name?.en || !workout.name?.ar) {
    errors.push('Both Arabic and English names are required to publish');
  }

  if (countWorkoutExercises(workout) === 0) {
    errors.push('At least one exercise is required to publish');
  }

  return errors;
}
