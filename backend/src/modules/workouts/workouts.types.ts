/**
 * Workouts Types
 * ===============
 * 
 * Types for workout (Super Set / Circuit) management.
 * Matches the Android app's expected JSON format.
 */

import type { LocalizedText } from '@/lib/types/localized';

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
  sets?: number;
  restBetweenSetsMs?: number;
  restAfterExerciseMs?: number;
  weightKg?: number;
  weightPerSet?: number[];
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
  targetReps?: number;
  targetDuration?: number;
  sets: number;
  restBetweenSetsMs: number;
  restAfterExerciseMs: number;
  weightKg?: number;
  weightPerSet?: number[];
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
  coverImageUrl?: string;
  difficulty?: 'beginner' | 'intermediate' | 'advanced';
  estimatedDurationMin?: number;
  tags?: string[];
  /** Highlight in Explore / ordering; defaults false in DB */
  isFeatured?: boolean;
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
  coverImageUrl?: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  estimatedDurationMin?: number;
  tags?: string[];
  isFeatured: boolean;
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
  search?: string;
  /** When true: only featured; when false: only non-featured; omit: all */
  isFeatured?: boolean;
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
  restBetweenSetsMs: 30000,
  restAfterExerciseMs: 60000,
} as const;

/**
 * Difficulty labels
 */
export const DIFFICULTY_LABELS: Record<DifficultyLevel, LocalizedText> = {
  beginner: { ar: 'مبتدئ', en: 'Beginner' },
  normal: { ar: 'متوسط', en: 'Normal' },
  advanced: { ar: 'متقدم', en: 'Advanced' },
};
