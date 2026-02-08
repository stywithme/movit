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
  WorkoutType,
  ExecutionMode,
} from './workouts.types';

// ============================================
// VALIDATION HELPERS
// ============================================

/**
 * Validate workout type
 */
export function isValidWorkoutType(type: string): type is WorkoutType {
  return type === 'circuit' || type === 'super_set';
}

/**
 * Validate execution mode
 */
export function isValidExecutionMode(mode: string): mode is ExecutionMode {
  return mode === 'sequential' || mode === 'alternating';
}

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

  if (exercise.targetDuration !== undefined && exercise.targetDuration <= 0) {
    errors.push(`${prefix}: targetDuration must be positive`);
  }

  if (
    exercise.difficulty && 
    !['beginner', 'normal', 'advanced'].includes(exercise.difficulty)
  ) {
    errors.push(`${prefix}: invalid difficulty level`);
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

  if (!input.type) {
    errors.push('Workout type is required');
  } else if (!isValidWorkoutType(input.type)) {
    errors.push('Invalid workout type. Must be "circuit" or "super_set"');
  }

  if (!input.executionMode) {
    errors.push('Execution mode is required');
  } else if (!isValidExecutionMode(input.executionMode)) {
    errors.push('Invalid execution mode. Must be "sequential" or "alternating"');
  }

  // Alternating mode specific validations
  if (input.executionMode === 'alternating') {
    if (!input.repsPerSwitch || input.repsPerSwitch <= 0) {
      errors.push('repsPerSwitch is required for alternating mode and must be positive');
    }
  }

  // Rounds validation
  if (input.rounds !== undefined && input.rounds <= 0) {
    errors.push('rounds must be positive');
  }

  // Rest time validations
  if (input.restBetweenSwitchMs !== undefined && input.restBetweenSwitchMs < 0) {
    errors.push('restBetweenSwitchMs must be non-negative');
  }
  if (input.restBetweenExercisesMs !== undefined && input.restBetweenExercisesMs < 0) {
    errors.push('restBetweenExercisesMs must be non-negative');
  }
  if (input.restBetweenRoundsMs !== undefined && input.restBetweenRoundsMs < 0) {
    errors.push('restBetweenRoundsMs must be non-negative');
  }

  // Exercises validation
  if (input.exercises && input.exercises.length > 0) {
    for (let i = 0; i < input.exercises.length; i++) {
      errors.push(...validateWorkoutExercise(input.exercises[i], i));
    }
  }

  return errors;
}

/**
 * Validate update workout input
 */
export function validateUpdateWorkout(input: UpdateWorkoutInput): string[] {
  const errors: string[] = [];

  // Validate type if provided
  if (input.type && !isValidWorkoutType(input.type)) {
    errors.push('Invalid workout type. Must be "circuit" or "super_set"');
  }

  // Validate execution mode if provided
  if (input.executionMode && !isValidExecutionMode(input.executionMode)) {
    errors.push('Invalid execution mode. Must be "sequential" or "alternating"');
  }

  // Validate rounds if provided
  if (input.rounds !== undefined && input.rounds <= 0) {
    errors.push('rounds must be positive');
  }

  // Validate rest times if provided
  if (input.restBetweenSwitchMs !== undefined && input.restBetweenSwitchMs < 0) {
    errors.push('restBetweenSwitchMs must be non-negative');
  }
  if (input.restBetweenExercisesMs !== undefined && input.restBetweenExercisesMs < 0) {
    errors.push('restBetweenExercisesMs must be non-negative');
  }
  if (input.restBetweenRoundsMs !== undefined && input.restBetweenRoundsMs < 0) {
    errors.push('restBetweenRoundsMs must be non-negative');
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

  return errors;
}

/**
 * Validate workout can be published
 */
export function validateCanPublish(workout: {
  name: { ar?: string; en?: string };
  type: string;
  executionMode: string;
  exercises: unknown[];
}): string[] {
  const errors: string[] = [];

  if (!workout.name?.en || !workout.name?.ar) {
    errors.push('Both Arabic and English names are required to publish');
  }

  if (!workout.exercises || workout.exercises.length === 0) {
    errors.push('At least one exercise is required to publish');
  }

  if (workout.exercises && workout.exercises.length < 2) {
    errors.push('Super Set/Circuit requires at least 2 exercises');
  }

  return errors;
}
