/**
 * Workouts Types
 * ===============
 * 
 * Types for workout (Super Set / Circuit) management.
 * Matches the Android app's expected JSON format.
 */

import type { LocalizedText } from '@/lib/types/localized';

// ============================================
// ENUMS
// ============================================

/**
 * Workout type - determines the overall structure
 */
export type WorkoutType = 'circuit' | 'super_set';

/**
 * Execution mode - how exercises are performed
 */
export type ExecutionMode = 'sequential' | 'alternating';

/**
 * Difficulty level for exercise in workout
 */
export type DifficultyLevel = 'beginner' | 'normal' | 'advanced';

// ============================================
// WORKOUT EXERCISE TYPES
// ============================================

/**
 * Exercise target - reps or duration
 */
export interface ExerciseTarget {
  reps?: number;
  durationSec?: number;
}

/**
 * Workout exercise input for creating/updating
 */
export interface WorkoutExerciseInput {
  id?: string;
  exerciseId: string;
  variantIndex?: number;
  difficulty?: DifficultyLevel;
  targetReps?: number;
  targetDuration?: number;
  notes?: LocalizedText;
  sortOrder?: number;
}

/**
 * Workout exercise for mobile export
 */
export interface WorkoutExerciseExport {
  exercise: string;        // Exercise slug
  variantIndex: number;
  difficulty: DifficultyLevel;
  target: ExerciseTarget;
  notes?: LocalizedText;
}

// ============================================
// WORKOUT TYPES
// ============================================

/**
 * Create workout input
 */
export interface CreateWorkoutInput {
  name: LocalizedText;
  description?: LocalizedText;
  slug?: string;
  type: WorkoutType;
  executionMode: ExecutionMode;
  rounds?: number;
  repsPerSwitch?: number;
  restBetweenSwitchMs?: number;
  restBetweenExercisesMs?: number;
  restBetweenRoundsMs?: number;
  exercises?: WorkoutExerciseInput[];
}

/**
 * Update workout input
 */
export interface UpdateWorkoutInput extends Partial<CreateWorkoutInput> {
  status?: 'draft' | 'published';
}

/**
 * Workout export for mobile
 */
export interface WorkoutExport {
  id: string;
  slug: string;
  name: LocalizedText;
  description?: LocalizedText;
  type: WorkoutType;
  executionMode: ExecutionMode;
  rounds: number;
  repsPerSwitch?: number;
  restBetweenSwitchMs?: number;
  restBetweenExercisesMs?: number;
  restBetweenRoundsMs: number;
  exercises: WorkoutExerciseExport[];
  updatedAt: string;
}

// ============================================
// LIST FILTERS
// ============================================

/**
 * Workout list filters
 */
export interface WorkoutListFilters {
  status?: 'draft' | 'published';
  type?: WorkoutType;
  search?: string;
  page?: number;
  limit?: number;
}

// ============================================
// CONSTANTS
// ============================================

/**
 * Default rest times in milliseconds
 */
export const DEFAULT_REST_TIMES = {
  restBetweenSwitchMs: 5000,
  restBetweenExercisesMs: 15000,
  restBetweenRoundsMs: 60000,
} as const;

/**
 * Workout type labels
 */
export const WORKOUT_TYPE_LABELS: Record<WorkoutType, LocalizedText> = {
  circuit: { ar: 'دائرة تدريبية', en: 'Circuit' },
  super_set: { ar: 'سوبر ست', en: 'Super Set' },
};

/**
 * Execution mode labels
 */
export const EXECUTION_MODE_LABELS: Record<ExecutionMode, LocalizedText> = {
  sequential: { ar: 'تتابعي', en: 'Sequential' },
  alternating: { ar: 'تبادلي', en: 'Alternating' },
};

/**
 * Difficulty labels
 */
export const DIFFICULTY_LABELS: Record<DifficultyLevel, LocalizedText> = {
  beginner: { ar: 'مبتدئ', en: 'Beginner' },
  normal: { ar: 'متوسط', en: 'Normal' },
  advanced: { ar: 'متقدم', en: 'Advanced' },
};
