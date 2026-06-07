/**
 * UserProgramOverride target key validation.
 * Exactly one of plannedWorkoutItemId (legacy) or workoutTemplateExerciseId (template model) must be set.
 */

export interface OverrideTargetInput {
  plannedWorkoutItemId?: string | null;
  workoutTemplateExerciseId?: string | null;
}

export function normalizeOverrideTargetId(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function resolveOverrideTargetKey(input: OverrideTargetInput): string | null {
  return (
    normalizeOverrideTargetId(input.workoutTemplateExerciseId) ??
    normalizeOverrideTargetId(input.plannedWorkoutItemId)
  );
}

export function validateOverrideTargetInput(input: OverrideTargetInput): {
  valid: boolean;
  error?: string;
  plannedWorkoutItemId: string | null;
  workoutTemplateExerciseId: string | null;
} {
  const plannedWorkoutItemId = normalizeOverrideTargetId(input.plannedWorkoutItemId);
  const workoutTemplateExerciseId = normalizeOverrideTargetId(input.workoutTemplateExerciseId);

  const setCount =
    (plannedWorkoutItemId ? 1 : 0) + (workoutTemplateExerciseId ? 1 : 0);

  if (setCount === 0) {
    return {
      valid: false,
      error: 'Either plannedWorkoutItemId or workoutTemplateExerciseId is required',
      plannedWorkoutItemId,
      workoutTemplateExerciseId,
    };
  }

  if (setCount > 1) {
    return {
      valid: false,
      error: 'Provide only one target: plannedWorkoutItemId or workoutTemplateExerciseId, not both',
      plannedWorkoutItemId,
      workoutTemplateExerciseId,
    };
  }

  return { valid: true, plannedWorkoutItemId, workoutTemplateExerciseId };
}
