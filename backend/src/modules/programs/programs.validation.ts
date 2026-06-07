/**
 * Programs Validation
 * ====================
 * Validation helpers for program data.
 */

import { PlannedWorkoutItemType } from '@prisma/client';
import type {
  CreateProgramInput,
  ProgramDayInput,
  PlannedWorkoutInput,
  PlannedWorkoutItemInput,
  ProgramWeekInput,
  UpdateProgramInput,
} from './programs.types';

const VALID_PROGRAM_ATTRIBUTE_MODES = ['REQUIRED', 'OPTIONAL', 'EXCLUDED'] as const;
const VALID_PLANNED_WORKOUT_ITEM_TYPES = new Set<string>(Object.values(PlannedWorkoutItemType));

function validatePlannedWorkoutItem(item: PlannedWorkoutItemInput, index: number): string[] {
  const errors: string[] = [];
  const prefix = `Planned workout item ${index + 1}`;

  if (!item.type || !VALID_PLANNED_WORKOUT_ITEM_TYPES.has(item.type)) {
    errors.push(`${prefix}: type must be "exercise" or "rest"`);
  }

  if (item.type === PlannedWorkoutItemType.exercise) {
    if (!item.exerciseId) {
      errors.push(`${prefix}: exerciseId is required for exercise items`);
    }
    if (item.sets !== undefined && item.sets <= 0) {
      errors.push(`${prefix}: sets must be positive`);
    }
    if (item.targetReps !== undefined && item.targetReps <= 0) {
      errors.push(`${prefix}: targetReps must be positive`);
    }
    if (item.targetDuration !== undefined && item.targetDuration <= 0) {
      errors.push(`${prefix}: targetDuration must be positive`);
    }
    const hasTargetReps = item.targetReps !== undefined;
    const hasTargetDuration = item.targetDuration !== undefined;
    if (!hasTargetReps && !hasTargetDuration) {
      errors.push(`${prefix}: either targetReps or targetDuration is required for exercise items`);
    }
    if (hasTargetReps && hasTargetDuration) {
      errors.push(`${prefix}: provide only one target (targetReps or targetDuration), not both`);
    }
    if (item.restBetweenSetsMs !== undefined && item.restBetweenSetsMs < 0) {
      errors.push(`${prefix}: restBetweenSetsMs must be non-negative`);
    }
    if (item.weightKg !== undefined && item.weightKg < 0) {
      errors.push(`${prefix}: weightKg must be non-negative`);
    }
  }

  if (item.type === PlannedWorkoutItemType.rest) {
    if (item.restDurationMs === undefined || item.restDurationMs < 0) {
      errors.push(`${prefix}: restDurationMs must be non-negative for rest items`);
    }
  }

  return errors;
}

function validatePlannedWorkout(plannedWorkout: PlannedWorkoutInput, index: number): string[] {
  const errors: string[] = [];
  const prefix = `Planned workout ${index + 1}`;

  if (!plannedWorkout.name?.en || !plannedWorkout.name?.ar) {
    errors.push(`${prefix}: both Arabic and English names are required`);
  }

  if (plannedWorkout.items && plannedWorkout.items.length > 0) {
    plannedWorkout.items.forEach((item, itemIndex) => {
      errors.push(...validatePlannedWorkoutItem(item, itemIndex));
    });
  }

  return errors;
}

function validateDay(day: ProgramDayInput, index: number): string[] {
  const errors: string[] = [];
  const prefix = `Day ${index + 1}`;

  if (!day.dayNumber || day.dayNumber < 1 || day.dayNumber > 14) {
    errors.push(`${prefix}: dayNumber must be between 1 and 14`);
  }

  if (day.plannedWorkouts && day.plannedWorkouts.length > 0) {
    day.plannedWorkouts.forEach((plannedWorkout, workoutIndex) => {
      errors.push(...validatePlannedWorkout(plannedWorkout, workoutIndex));
    });
  }

  return errors;
}

function validateWeek(week: ProgramWeekInput, index: number): string[] {
  const errors: string[] = [];
  const prefix = `Week ${index + 1}`;

  if (!week.weekNumber || week.weekNumber <= 0) {
    errors.push(`${prefix}: weekNumber must be positive`);
  }

  if (week.days && week.days.length > 0) {
    week.days.forEach((day, dayIndex) => {
      errors.push(...validateDay(day, dayIndex));
    });
  }

  return errors;
}

function validateProgramAttributes(attrs: unknown): string[] {
  if (attrs === undefined) return [];
  if (!Array.isArray(attrs)) {
    return ['programAttributes must be an array'];
  }
  const errors: string[] = [];
  attrs.forEach((row, i) => {
    if (!row || typeof row !== 'object') {
      errors.push(`programAttributes[${i}]: must be an object`);
      return;
    }
    const r = row as Record<string, unknown>;
    if (typeof r.attributeValueId !== 'string' || !r.attributeValueId.trim()) {
      errors.push(`programAttributes[${i}]: attributeValueId is required`);
    }
    if (r.mode !== undefined && r.mode !== null) {
      const m = String(r.mode);
      if (!VALID_PROGRAM_ATTRIBUTE_MODES.includes(m as (typeof VALID_PROGRAM_ATTRIBUTE_MODES)[number])) {
        errors.push(
          `programAttributes[${i}]: mode must be one of ${VALID_PROGRAM_ATTRIBUTE_MODES.join(', ')}`,
        );
      }
    }
  });
  return errors;
}

export function validateCreateProgram(input: CreateProgramInput): string[] {
  const errors: string[] = [];

  if (!input.name?.en || !input.name?.ar) {
    errors.push('Both Arabic and English names are required');
  }

  if (!input.durationWeeks || input.durationWeeks <= 0) {
    errors.push('durationWeeks must be positive');
  }

  if (input.weeks && input.weeks.length > 0) {
    input.weeks.forEach((week, weekIndex) => {
      errors.push(...validateWeek(week, weekIndex));
    });
  }

  errors.push(...validateProgramAttributes(input.programAttributes));

  return errors;
}

export function validateUpdateProgram(input: UpdateProgramInput): string[] {
  const errors: string[] = [];

  if (input.durationWeeks !== undefined && input.durationWeeks <= 0) {
    errors.push('durationWeeks must be positive');
  }

  if (input.weeks && input.weeks.length > 0) {
    input.weeks.forEach((week, weekIndex) => {
      errors.push(...validateWeek(week, weekIndex));
    });
  }

  errors.push(...validateProgramAttributes(input.programAttributes));

  return errors;
}

export function validateWeekInput(input: ProgramWeekInput): string[] {
  return validateWeek(input, 0);
}

export function validateDayInput(input: ProgramDayInput): string[] {
  return validateDay(input, 0);
}

export function validatePlannedWorkoutInput(input: PlannedWorkoutInput): string[] {
  return validatePlannedWorkout(input, 0);
}

export function validatePlannedWorkoutItemInput(input: PlannedWorkoutItemInput): string[] {
  return validatePlannedWorkoutItem(input, 0);
}
