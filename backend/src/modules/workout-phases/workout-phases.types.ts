import type { WorkoutBlockRole } from '@prisma/client';
import type { LocalizedText } from '@/lib/types/localized';

export interface CreateWorkoutPhaseInput {
  slug?: string;
  name: LocalizedText;
  description?: LocalizedText;
  role?: WorkoutBlockRole;
  canSkip?: boolean;
  canContinue?: boolean;
  maxContinueTimeMs?: number | null;
  color?: string | null;
  icon?: string | null;
  isActive?: boolean;
  sortOrder?: number;
}

export interface UpdateWorkoutPhaseInput extends Partial<CreateWorkoutPhaseInput> {}

export interface WorkoutPhaseListFilters {
  active?: boolean;
  search?: string;
}
