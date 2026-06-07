import { WorkoutBlockRole } from '@prisma/client';
import type { CreateWorkoutPhaseInput, UpdateWorkoutPhaseInput } from './workout-phases.types';

const WORKOUT_BLOCK_ROLES = new Set<string>(Object.values(WorkoutBlockRole));

export function validateCreateWorkoutPhase(input: CreateWorkoutPhaseInput): string[] {
  const errors: string[] = [];

  if (!input.name?.en) {
    errors.push('English name is required');
  }
  if (!input.name?.ar) {
    errors.push('Arabic name is required');
  }

  validateCommon(input, errors);
  return errors;
}

export function validateUpdateWorkoutPhase(input: UpdateWorkoutPhaseInput): string[] {
  const errors: string[] = [];

  if (input.name !== undefined) {
    if (!input.name?.en) {
      errors.push('English name is required');
    }
    if (!input.name?.ar) {
      errors.push('Arabic name is required');
    }
  }

  validateCommon(input, errors);
  return errors;
}

function validateCommon(input: UpdateWorkoutPhaseInput, errors: string[]) {
  if (input.role !== undefined && !WORKOUT_BLOCK_ROLES.has(input.role)) {
    errors.push('Invalid workout phase role');
  }

  if (input.maxContinueTimeMs !== undefined && input.maxContinueTimeMs !== null && input.maxContinueTimeMs < 0) {
    errors.push('maxContinueTimeMs must be non-negative');
  }

  if (input.sortOrder !== undefined && input.sortOrder < 0) {
    errors.push('sortOrder must be non-negative');
  }
}
