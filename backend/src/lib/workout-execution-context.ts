import { WorkoutExecutionContext } from '@prisma/client';

const WORKOUT_EXECUTION_CONTEXTS = new Set<string>(Object.values(WorkoutExecutionContext));

export function parseWorkoutExecutionContext(
  value: unknown,
  fallback: WorkoutExecutionContext = WorkoutExecutionContext.free,
): WorkoutExecutionContext {
  if (typeof value === 'string' && WORKOUT_EXECUTION_CONTEXTS.has(value)) {
    return value as WorkoutExecutionContext;
  }
  return fallback;
}

export function isWorkoutExecutionContext(value: unknown): value is WorkoutExecutionContext {
  return typeof value === 'string' && WORKOUT_EXECUTION_CONTEXTS.has(value);
}
