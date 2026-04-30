/**
 * Programs Validation
 * ====================
 * Validation helpers for program data.
 */

import type {
  CreateProgramInput,
  ProgramDayInput,
  ProgramSessionInput,
  ProgramSessionItemInput,
  ProgramWeekInput,
  UpdateProgramInput,
} from './programs.types';

const VALID_DIFFICULTIES = ['beginner', 'intermediate', 'advanced'];

function validateSessionItem(item: ProgramSessionItemInput, index: number): string[] {
  const errors: string[] = [];
  const prefix = `Session item ${index + 1}`;

  if (!item.type || !['exercise', 'rest'].includes(item.type)) {
    errors.push(`${prefix}: type must be "exercise" or "rest"`);
  }

  if (item.type === 'exercise') {
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

  if (item.type === 'rest') {
    if (item.restDurationMs === undefined || item.restDurationMs < 0) {
      errors.push(`${prefix}: restDurationMs must be non-negative for rest items`);
    }
  }

  return errors;
}

function validateSession(session: ProgramSessionInput, index: number): string[] {
  const errors: string[] = [];
  const prefix = `Session ${index + 1}`;

  if (!session.name?.en || !session.name?.ar) {
    errors.push(`${prefix}: both Arabic and English names are required`);
  }

  if (session.items && session.items.length > 0) {
    session.items.forEach((item, itemIndex) => {
      errors.push(...validateSessionItem(item, itemIndex));
    });
  }

  return errors;
}

function validateDay(day: ProgramDayInput, index: number): string[] {
  const errors: string[] = [];
  const prefix = `Day ${index + 1}`;

  if (!day.dayNumber || day.dayNumber < 1 || day.dayNumber > 7) {
    errors.push(`${prefix}: dayNumber must be between 1 and 7`);
  }

  if (day.sessions && day.sessions.length > 0) {
    day.sessions.forEach((session, sessionIndex) => {
      errors.push(...validateSession(session, sessionIndex));
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

const VALID_PROGRAM_ATTRIBUTE_MODES = ['REQUIRED', 'OPTIONAL', 'EXCLUDED'] as const;

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

  if (input.difficulty && !VALID_DIFFICULTIES.includes(input.difficulty)) {
    errors.push('Invalid difficulty. Must be beginner, intermediate, or advanced');
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

  if (input.difficulty && !VALID_DIFFICULTIES.includes(input.difficulty)) {
    errors.push('Invalid difficulty. Must be beginner, intermediate, or advanced');
  }

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

export function validateSessionInput(input: ProgramSessionInput): string[] {
  return validateSession(input, 0);
}

export function validateSessionItemInput(input: ProgramSessionItemInput): string[] {
  return validateSessionItem(input, 0);
}
