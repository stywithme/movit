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
} from './workouts.types';

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

  if (exercise.targetDuration !== undefined && exercise.targetDuration <= 0) {
    errors.push(`${prefix}: targetDuration must be positive`);
  }

  if (exercise.sets !== undefined && exercise.sets <= 0) {
    errors.push(`${prefix}: sets must be positive`);
  }

  if (exercise.restBetweenSetsMs !== undefined && exercise.restBetweenSetsMs < 0) {
    errors.push(`${prefix}: restBetweenSetsMs must be non-negative`);
  }

  if (exercise.restAfterExerciseMs !== undefined && exercise.restAfterExerciseMs < 0) {
    errors.push(`${prefix}: restAfterExerciseMs must be non-negative`);
  }

  if (exercise.weightKg !== undefined && exercise.weightKg < 0) {
    errors.push(`${prefix}: weightKg must be non-negative`);
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

  if (input.estimatedDurationMin !== undefined && input.estimatedDurationMin < 0) {
    errors.push('estimatedDurationMin must be non-negative');
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

  if (input.estimatedDurationMin !== undefined && input.estimatedDurationMin < 0) {
    errors.push('estimatedDurationMin must be non-negative');
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
  exercises: unknown[];
}): string[] {
  const errors: string[] = [];

  if (!workout.name?.en || !workout.name?.ar) {
    errors.push('Both Arabic and English names are required to publish');
  }

  if (!workout.exercises || workout.exercises.length === 0) {
    errors.push('At least one exercise is required to publish');
  }

  return errors;
}
