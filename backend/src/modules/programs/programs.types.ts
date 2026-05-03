/**
 * Programs Types
 * ===============
 * Types for Program / Week / Day / Session structure.
 */

import type { LocalizedText } from '@/lib/types/localized';
import type {
  ProgramAttributeMode,
  ProgramType,
  SessionItemIntent,
  SessionItemRole,
  WeekType,
} from '@prisma/client';

export interface ProgramAttributeInput {
  attributeValueId: string;
  mode?: ProgramAttributeMode;
}

export type ProgramSessionItemType = 'exercise' | 'rest';

export interface ProgramSessionItemInput {
  id?: string;
  type: ProgramSessionItemType;
  exerciseId?: string;
  sets?: number;
  targetReps?: number;
  targetDuration?: number;
  restBetweenSetsMs?: number;
  weightKg?: number;
  weightPerSet?: number[];
  notes?: LocalizedText;
  restDurationMs?: number;
  sourceWorkoutId?: string;
  sortOrder?: number;
  /** Preferred name; `alternatives` accepted for backward compatibility */
  allowedSubstitutions?: string[];
  /** @deprecated use allowedSubstitutions */
  alternatives?: string[];
  role?: SessionItemRole;
  intent?: SessionItemIntent;
  coachingNotes?: LocalizedText;
}

export interface ProgramSessionInput {
  id?: string;
  name: LocalizedText;
  sortOrder?: number;
  items?: ProgramSessionItemInput[];
}

export interface ProgramDayInput {
  id?: string;
  dayNumber: number;
  isRestDay?: boolean;
  name?: LocalizedText;
  dayFocus?: string;
  sessions?: ProgramSessionInput[];
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

export interface CreateProgramInput {
  name: LocalizedText;
  description?: LocalizedText;
  slug?: string;
  coverImageUrl?: string;
  durationWeeks: number;
  tags?: string[];
  isDefault?: boolean;
  weeks?: ProgramWeekInput[];
  programType?: ProgramType;
  autoAssignable?: boolean;
  version?: number;
  ownerId?: string | null;
  forkedFromId?: string | null;
  coachingNotes?: Record<string, unknown>;
  weeklySessionTarget?: number | null;
  estimatedSessionMinutes?: number | null;
  levelRangeMin?: number;
  levelRangeMax?: number;
  prescriptionPriority?: number;
  prerequisiteProgramId?: string;
  nextProgramId?: string;
  /** Program matching dimensions (domain, goal, equipment, …). */
  programAttributes?: ProgramAttributeInput[];
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
  progress: Record<string, string>; // "weekNum_dayNum_sessionId" → status
  sessions: ProgramExportSession[];
  /** False when today (UTC) is not one of the user's training weekdays. */
  isTrainingDay?: boolean;
  /** 0=Sun … 6=Sat — copy of profile.trainingWeekdays for mobile. */
  trainingWeekdays: number[];
  catchUpSuggestion?: {
    resetType: 'none' | 'week_restart' | 'program_restart';
    resetToWeek: number;
    resetToDay: number;
    calendarDaysSinceLastSession: number;
    messageAr: string;
    messageEn: string;
  } | null;
}

export interface ProgramExportItem {
  type: ProgramSessionItemType;
  /** Stable server id for overrides / progression targeting */
  serverItemId?: string;
  exerciseSlug?: string;
  /** True when the linked exercise was removed from the catalog */
  deletedExercise?: boolean;
  role?: string;
  intent?: string;
  allowedSubstitutions?: string[];
  sets?: number;
  targetReps?: number;
  targetDuration?: number;
  restBetweenSetsMs?: number;
  weightKg?: number;
  weightPerSet?: number[];
  notes?: LocalizedText;
  restDurationMs?: number;
  sortOrder: number;
}

export interface ProgramExportSession {
  id: string;
  name: LocalizedText;
  sortOrder: number;
  estimatedDurationMin?: number | null;
  items: ProgramExportItem[];
}

export interface ProgramExportDay {
  dayNumber: number;
  isRestDay: boolean;
  name?: LocalizedText;
  sessions: ProgramExportSession[];
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
  levelRangeMin: number;
  levelRangeMax: number;
  tags?: string[];
  weeklySessionTarget?: number | null;
  estimatedSessionMinutes?: number | null;
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
  levelRangeMin: number;
  levelRangeMax: number;
  totalExercisesInFirstWeek: number;
  muscleGroups: string[];
  weeks: ProgramExportWeek[];
  updatedAt: string;
}
