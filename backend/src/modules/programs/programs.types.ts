/**
 * Programs Types
 * ===============
 * Types for Program / Week / Day / PlannedWorkout structure.
 */

import type { LocalizedText } from '@/lib/types/localized';
import type {
  PlannedWorkoutItemType,
  ProgramAttributeMode,
  ProgramType,
  WeekType,
} from '@prisma/client';

export type { PlannedWorkoutItemType };
import type { WorkoutPhaseExport, WorkoutPhaseInput } from '@/modules/workout-templates/workout-templates.types';

export interface ProgramAttributeInput {
  attributeValueId: string;
  mode?: ProgramAttributeMode;
}

export interface PlannedWorkoutItemInput {
  id?: string;
  type: PlannedWorkoutItemType;
  exerciseId?: string;
  sets?: number;
  targetReps?: number;
  targetDuration?: number;
  restBetweenSetsMs?: number;
  weightKg?: number;
  weightPerSet?: number[];
  notes?: LocalizedText;
  restDurationMs?: number;
  sourceWorkoutTemplateId?: string;
  sortOrder?: number;
}

export interface PlannedWorkoutInput {
  id?: string;
  name: LocalizedText;
  sortOrder?: number;
  estimatedDurationMin?: number | null;
  /** Link to standalone catalog template (read-only reference until edited). */
  workoutTemplateId?: string;
  /** Preferred: phased workout content (same shape as workout templates). */
  phases?: WorkoutPhaseInput[];
  /** Legacy admin flat list — converted to a single MAIN phase on save. */
  items?: PlannedWorkoutItemInput[];
}

export interface ProgramDayInput {
  id?: string;
  dayNumber: number;
  isRestDay?: boolean;
  name?: LocalizedText;
  dayFocus?: string;
  plannedWorkouts?: PlannedWorkoutInput[];
}

export interface ProgramWeekInput {
  id?: string;
  weekNumber: number;
  name?: LocalizedText;
  description?: LocalizedText;
  sortOrder?: number;
  weekType?: WeekType;
  days?: ProgramDayInput[];
}

/** Phase input for phase-based program editor. */
export interface ProgramPhaseInput {
  id?: string;
  name: LocalizedText;
  description?: LocalizedText;
  weekType?: WeekType;
  startWeek: number;
  endWeek: number;
  sortOrder?: number;
}

export interface CreateProgramInput {
  name: LocalizedText;
  description?: LocalizedText;
  slug?: string;
  coverImageUrl?: string;
  durationWeeks: number;
  tags?: string[];
  isDefault?: boolean;
  programType?: ProgramType;
  autoAssignable?: boolean;
  version?: number;
  ownerId?: string | null;
  forkedFromId?: string | null;
  coachingNotes?: Record<string, unknown>;
  weeklyWorkoutTarget?: number | null;
  estimatedWorkoutMinutes?: number | null;
  levelMinId?: string | null;
  levelMaxId?: string | null;
  /** @deprecated Use levelMinId / levelMaxId */
  levelRangeMin?: number;
  /** @deprecated Use levelMinId / levelMaxId */
  levelRangeMax?: number;
  prescriptionPriority?: number;
  prerequisiteProgramId?: string;
  nextProgramId?: string;
  /** Program matching dimensions (domain, goal, equipment, etc.). */
  programAttributes?: ProgramAttributeInput[];
  /** Phase metadata for the editor. Calendar source of truth remains `weeks`. */
  phases?: ProgramPhaseInput[];
  /** Legacy flat weeks (still accepted for backward compat). */
  weeks?: ProgramWeekInput[];
}

export interface UpdateProgramInput extends Partial<CreateProgramInput> {
  isPublished?: boolean;
}

export interface UpdateUserProgramInput {
  name?: LocalizedText;
  customizations?: Record<string, unknown>;
  isActive?: boolean;
}

export interface TodayPlanResponse {
  userProgramId: string;
  programId?: string;
  weekNumber: number;
  dayNumber: number;
  date: string;
  isProgramComplete: boolean;
  progress: Record<string, string>; // "weekNum_dayNum_plannedWorkoutId" -> status
  plannedWorkouts: ProgramExportPlannedWorkout[];
  /** False when today (UTC) is not one of the user's training weekdays. */
  isTrainingDay?: boolean;
  /** 0=Sun..6=Sat, copy of profile.trainingWeekdays for mobile. */
  trainingWeekdays: number[];
  catchUpSuggestion?: {
    resetType: 'none' | 'week_restart' | 'program_restart';
    resetToWeek: number;
    resetToDay: number;
    calendarDaysSinceLastWorkout: number;
    messageAr: string;
    messageEn: string;
  } | null;
}

export interface ProgramExportItem {
  type: PlannedWorkoutItemType;
  /** Stable server id for overrides / progression targeting */
  serverItemId?: string;
  exerciseSlug?: string;
  /** True when the linked exercise was removed from the catalog */
  deletedExercise?: boolean;
  /** From Exercise (catalog), not from planned workout item */
  intent?: string | null;
  coachingNotes?: unknown;
  sets?: number;
  targetReps?: number;
  targetRepsPerSet?: number[];
  targetDuration?: number;
  restBetweenSetsMs?: number;
  restBetweenSetsPerSetMs?: number[];
  weightKg?: number;
  weightPerSet?: number[];
  notes?: LocalizedText;
  restDurationMs?: number;
  sortOrder: number;
  phaseIndex?: number;
  phaseRole?: string;
  phaseCanSkip?: boolean;
  phaseCanContinue?: boolean;
  phaseMaxContinueTimeMs?: number;
}

export interface ProgramExportPlannedWorkout {
  id: string;
  name: LocalizedText;
  sortOrder: number;
  workoutTemplateId: string;
  workoutTemplateSlug?: string;
  estimatedDurationMin?: number | null;
  phases: WorkoutPhaseExport[];
  /** Flattened line items (derived from phases) for mobile runners. */
  items: ProgramExportItem[];
}

export interface ProgramExportDay {
  dayNumber: number;
  isRestDay: boolean;
  name?: LocalizedText;
  plannedWorkouts: ProgramExportPlannedWorkout[];
}

export interface ProgramExportWeek {
  weekNumber: number;
  name?: LocalizedText;
  description?: LocalizedText;
  days: ProgramExportDay[];
}

export interface ProgramExport {
  id: string;
  slug: string;
  name: LocalizedText;
  description?: LocalizedText;
  coverImageUrl?: string;
  durationWeeks: number;
  levelMinId?: string | null;
  levelMaxId?: string | null;
  levelMin?: { id: string; number: number; code: string; name: LocalizedText } | null;
  levelMax?: { id: string; number: number; code: string; name: LocalizedText } | null;
  /** Derived legacy field for old mobile/admin clients. */
  levelRangeMin?: number | null;
  /** Derived legacy field for old mobile/admin clients. */
  levelRangeMax?: number | null;
  tags?: string[];
  weeklyWorkoutTarget?: number | null;
  estimatedWorkoutMinutes?: number | null;
  isFeatured?: boolean;
  weeks: ProgramExportWeek[];
  updatedAt: string;
}

/** Lightweight catalog preview (first calendar week only). */
export interface ProgramPreviewExport {
  id: string;
  slug: string;
  name: LocalizedText;
  description?: LocalizedText;
  coverImageUrl?: string;
  durationWeeks: number;
  levelMinId?: string | null;
  levelMaxId?: string | null;
  levelRangeMin?: number | null;
  levelRangeMax?: number | null;
  totalExercisesInFirstWeek: number;
  muscleGroups: string[];
  weeks: ProgramExportWeek[];
  updatedAt: string;
}

/**
 * Validates phase structure for strict sequential contiguous phases.
 * Returns errors if gaps, overlaps, out of range, or invalid patterns.
 */
export function validatePhaseStructure(
  phases: ProgramPhaseInput[] | undefined,
  durationWeeks: number,
): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  if (!phases || phases.length === 0) {
    return { valid: true, errors: [] }; // allow empty for legacy
  }

  // Sort by sortOrder or startWeek
  const sorted = [...phases].sort((a, b) => (a.sortOrder ?? a.startWeek) - (b.sortOrder ?? b.startWeek));

  let expectedWeek = 1;
  for (let i = 0; i < sorted.length; i++) {
    const p = sorted[i];
    if (p.startWeek !== expectedWeek) {
      errors.push(`Phase ${i + 1} must start at week ${expectedWeek}, got ${p.startWeek}`);
    }
    if (p.endWeek < p.startWeek) {
      errors.push(`Phase ${i + 1} endWeek < startWeek`);
    }
    expectedWeek = p.endWeek + 1;

  }

  if (expectedWeek - 1 !== durationWeeks) {
    errors.push(`Phases cover up to week ${expectedWeek - 1}, but program durationWeeks=${durationWeeks}`);
  }

  return { valid: errors.length === 0, errors };
}
