/**
 * Programs Types
 * ===============
 * Types for Program / Week / Day / Session structure.
 */

import type { LocalizedText } from '@/lib/types/localized';

export type ProgramDifficulty = 'beginner' | 'intermediate' | 'advanced';

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
  items?: ProgramSessionItemInput[];
}

export interface ProgramDayInput {
  id?: string;
  dayNumber: number;
  isRestDay?: boolean;
  name?: LocalizedText;
  sessions?: ProgramSessionInput[];
}

export interface ProgramWeekInput {
  id?: string;
  weekNumber: number;
  name?: LocalizedText;
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
  difficulty?: ProgramDifficulty;
  tags?: string[];
  isDefault?: boolean;
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
}

export interface ProgramExportItem {
  type: ProgramSessionItemType;
  exerciseSlug?: string;
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
  difficulty: ProgramDifficulty;
  tags?: string[];
  weeks: ProgramExportWeek[];
  updatedAt: string;
}
