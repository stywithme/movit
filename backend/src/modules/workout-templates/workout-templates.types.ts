/**
 * Workouts Types
 * ===============
 * 
 * Types for workout (Super Set / Circuit) management.
 * Matches the Android app's expected JSON format.
 */

import type { LocalizedText } from '@/lib/types/localized';

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
  /** @deprecated Use targetRepsPerSet — kept for legacy payloads */
  targetReps?: number;
  targetRepsPerSet?: number[];
  targetDuration?: number;
  sets?: number;
  /** @deprecated Use restBetweenSetsPerSetMs — kept for legacy payloads */
  restBetweenSetsMs?: number;
  restBetweenSetsPerSetMs?: number[];
  restAfterExerciseMs?: number;
  /** @deprecated Use weightPerSet — kept for legacy payloads */
  weightKg?: number;
  weightPerSet?: number[];
  notes?: LocalizedText;
  sortOrder?: number;
}

/**
 * Workout phase input for creating/updating nested workout templates
 */
export interface WorkoutPhaseInput {
  id?: string;
  phaseId: string;
  sortOrder?: number;
  nameOverride?: LocalizedText;
  canSkipOverride?: boolean;
  canContinueOverride?: boolean;
  maxContinueTimeMsOverride?: number | null;
  exercises?: WorkoutExerciseInput[];
}

/**
 * Workout exercise for mobile export
 */
export interface WorkoutExerciseExport {
  exercise: string;        // Exercise slug
  /** Localized display name (P3.6) — optional for older clients. */
  name?: LocalizedText;
  variantIndex: number;
  targetReps?: number;
  targetRepsPerSet?: number[];
  targetDuration?: number;
  sets: number;
  restBetweenSetsMs: number;
  restBetweenSetsPerSetMs?: number[];
  restAfterExerciseMs: number;
  weightPerSet?: number[];
  notes?: LocalizedText;
}

/**
 * Workout phase for mobile export
 */
export interface WorkoutPhaseExport {
  id: string;
  phaseId: string;
  slug: string;
  role: string;
  name: LocalizedText;
  description?: LocalizedText;
  canSkip: boolean;
  canContinue: boolean;
  maxContinueTimeMs?: number;
  sortOrder: number;
  exercises: WorkoutExerciseExport[];
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
  levelId?: string | null;
  estimatedDurationMin?: number;
  tags?: string[];
  /** Highlight in Explore / ordering; defaults false in DB */
  isFeatured?: boolean;
  exercises?: WorkoutExerciseInput[];
  phases?: WorkoutPhaseInput[];
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
  levelId?: string | null;
  level?: {
    id: string;
    number: number;
    code: string;
    name: LocalizedText;
  } | null;
  estimatedDurationMin?: number;
  tags?: string[];
  isFeatured: boolean;
  exercises: WorkoutExerciseExport[];
  phases: WorkoutPhaseExport[];
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

