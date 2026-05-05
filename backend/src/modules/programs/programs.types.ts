/**
 * Programs Types
 * ===============
 * Types for Program / Week / Day / Session structure.
 */

import type { LocalizedText } from '@/lib/types/localized';
import type {
  ProgramAttributeMode,
  ProgramType,
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
}

export interface ProgramSessionInput {
  id?: string;
  name: LocalizedText;
  sortOrder?: number;
  estimatedDurationMin?: number | null;
  role?: 'WARMUP' | 'ACTIVATION' | 'MAIN' | 'ACCESSORY' | 'CORRECTIVE' | 'COOLDOWN' | 'TEST';
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

/** Day pattern inside a weekly template for a Phase. Fixed 7-day structure. */
export interface DayPatternInput {
  dayNumber: number;
  isRestDay?: boolean;
  name?: LocalizedText;
  dayFocus?: string;
  sessions?: ProgramSessionInput[];
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
  weeklyPattern?: { days: DayPatternInput[] };
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
  weeklySessionTarget?: number | null;
  estimatedSessionMinutes?: number | null;
  levelRangeMin?: number;
  levelRangeMax?: number;
  prescriptionPriority?: number;
  prerequisiteProgramId?: string;
  nextProgramId?: string;
  /** Program matching dimensions (domain, goal, equipment, …). */
  programAttributes?: ProgramAttributeInput[];
  /** Phase-based structure (preferred for new editor). Backend expands to weeks at save. */
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
  /** From Exercise (catalog), not from session item */
  intent?: string | null;
  coachingNotes?: unknown;
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
  role: string;
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

    // Check pattern has 7 days if provided
    if (p.weeklyPattern?.days) {
      const days = p.weeklyPattern.days;
      if (days.length !== 7) {
        errors.push(`Phase ${i + 1} weeklyPattern must have exactly 7 days`);
      }
    }
  }

  if (expectedWeek - 1 !== durationWeeks) {
    errors.push(`Phases cover up to week ${expectedWeek - 1}, but program durationWeeks=${durationWeeks}`);
  }

  return { valid: errors.length === 0, errors };
}
