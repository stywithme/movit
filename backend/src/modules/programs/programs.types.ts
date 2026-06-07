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
} from '@prisma/client';

export type { PlannedWorkoutItemType };
import type { WorkoutPhaseExport, WorkoutPhaseInput } from '@/modules/workout-templates/workout-templates.types';

export type ProgramDayType = 'training' | 'rest' | 'active_recovery';

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
  dayNumber?: number;
  dayType?: ProgramDayType;
  targetMuscleValueIds?: string[];
  plannedWorkouts?: PlannedWorkoutInput[];
}

export interface ProgramWeekInput {
  id?: string;
  weekNumber?: number;
  target?: LocalizedText;
  description?: LocalizedText;
  sortOrder?: number;
  days?: ProgramDayInput[];
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
  prerequisiteProgramId?: string;
  nextProgramId?: string;
  /** Program matching dimensions (domain, goal, equipment, etc.). */
  programAttributes?: ProgramAttributeInput[];
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

export interface ProgramExportMuscleRef {
  code: string;
  name: LocalizedText;
}

export interface ProgramExportDay {
  dayNumber: number;
  dayType: ProgramDayType;
  isRestDay: boolean;
  targetMuscles: ProgramExportMuscleRef[];
  plannedWorkouts: ProgramExportPlannedWorkout[];
}

export interface ProgramExportWeek {
  weekNumber: number;
  target?: LocalizedText;
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
