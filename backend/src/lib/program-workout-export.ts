/**
 * Flatten workout template phases into program/mobile line items.
 */

import { PlannedWorkoutItemType } from '@prisma/client';
import type { ProgramExportItem } from '@/modules/programs/programs.types';
import type { WorkoutPhaseExport } from '@/modules/workout-templates/workout-templates.types';

export interface TemplateExerciseRow {
  id: string;
  exercise?: { slug: string; intent?: string | null; coachingNotes?: unknown } | null;
  sets: number | null;
  targetReps: number | null;
  targetRepsPerSet?: number[] | null;
  targetDuration: number | null;
  restBetweenSetsMs: number | null;
  restBetweenSetsPerSetMs?: number[] | null;
  restAfterExerciseMs: number | null;
  weightKg?: number | null;
  weightPerSet?: number[] | null;
  notes?: { ar: string; en: string } | null;
  sortOrder: number;
}

export interface TemplatePhaseRow {
  id: string;
  sortOrder: number;
  phase: { role: string };
  exercises: TemplateExerciseRow[];
}

function parseLocalizedText(value: unknown): { ar: string; en: string } | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const record = value as Record<string, unknown>;
  if (typeof record.ar !== 'string' || typeof record.en !== 'string') return undefined;
  return { ar: record.ar, en: record.en };
}

/**
 * Build flattened program export items from template phases (with phase metadata on each row).
 */
export function flattenTemplatePhasesToProgramItems(
  phases: TemplatePhaseRow[] | WorkoutPhaseExport[],
  options?: {
    resolveExerciseMeta?: (exerciseSlug: string) => {
      intent?: string | null;
      coachingNotes?: unknown;
      deletedExercise?: boolean;
    };
  },
): ProgramExportItem[] {
  const sorted = [...phases].sort((a, b) => a.sortOrder - b.sortOrder);
  const items: ProgramExportItem[] = [];
  let sortIndex = 0;

  sorted.forEach((phase, phaseIndex) => {
    const phaseRole =
      'role' in phase && typeof phase.role === 'string'
        ? phase.role
        : (phase as TemplatePhaseRow).phase.role;
    const canSkip =
      'canSkip' in phase && typeof phase.canSkip === 'boolean' ? phase.canSkip : undefined;
    const canContinue =
      'canContinue' in phase && typeof phase.canContinue === 'boolean' ? phase.canContinue : undefined;
    const maxContinueTimeMs =
      'maxContinueTimeMs' in phase && typeof phase.maxContinueTimeMs === 'number'
        ? phase.maxContinueTimeMs
        : undefined;

    const exercises =
      'exercises' in phase
        ? phase.exercises
        : (phase as TemplatePhaseRow).exercises;

    exercises.forEach((exercise, exerciseIndex) => {
      const isExportExercise = 'exercise' in exercise && typeof exercise.exercise === 'string';
      const slug = isExportExercise
        ? (exercise as WorkoutPhaseExport['exercises'][0]).exercise
        : exercise.exercise?.slug;
      const meta = slug && options?.resolveExerciseMeta ? options.resolveExerciseMeta(slug) : undefined;

      const serverItemId = isExportExercise
        ? undefined
        : (exercise as TemplateExerciseRow).id;

      const sets = exercise.sets ?? undefined;
      const targetReps =
        'targetReps' in exercise
          ? exercise.targetReps ?? undefined
          : (exercise as TemplateExerciseRow).targetReps ?? undefined;
      const targetRepsPerSet =
        'targetRepsPerSet' in exercise ? exercise.targetRepsPerSet ?? undefined : undefined;
      const targetDuration =
        'targetDuration' in exercise
          ? exercise.targetDuration ?? undefined
          : (exercise as TemplateExerciseRow).targetDuration ?? undefined;
      const restBetweenSetsMs =
        'restBetweenSetsMs' in exercise
          ? exercise.restBetweenSetsMs ?? undefined
          : (exercise as TemplateExerciseRow).restBetweenSetsMs ?? undefined;
      const restBetweenSetsPerSetMs =
        'restBetweenSetsPerSetMs' in exercise
          ? exercise.restBetweenSetsPerSetMs ?? undefined
          : (exercise as TemplateExerciseRow).restBetweenSetsPerSetMs ?? undefined;
      const weightPerSet =
        'weightPerSet' in exercise ? exercise.weightPerSet ?? undefined : undefined;
      const weightKg =
        'weightKg' in exercise
          ? (exercise as TemplateExerciseRow).weightKg ?? undefined
          : undefined;
      const notes =
        'notes' in exercise && exercise.notes
          ? parseLocalizedText(exercise.notes) ??
            (typeof exercise.notes === 'object' ? (exercise.notes as { ar: string; en: string }) : undefined)
          : undefined;
      const restAfterExerciseMs =
        'restAfterExerciseMs' in exercise
          ? exercise.restAfterExerciseMs ?? 0
          : (exercise as TemplateExerciseRow).restAfterExerciseMs ?? 0;

      items.push({
        type: PlannedWorkoutItemType.exercise,
        serverItemId,
        exerciseSlug: slug ?? undefined,
        deletedExercise: meta?.deletedExercise,
        intent: meta?.intent ?? undefined,
        coachingNotes: meta?.coachingNotes ?? undefined,
        sets,
        targetReps,
        targetRepsPerSet,
        targetDuration,
        restBetweenSetsMs,
        restBetweenSetsPerSetMs,
        weightKg: weightKg ?? weightPerSet?.[0],
        weightPerSet,
        notes,
        sortOrder: sortIndex++,
        phaseIndex,
        phaseRole,
        phaseCanSkip: canSkip,
        phaseCanContinue: canContinue,
        phaseMaxContinueTimeMs: maxContinueTimeMs,
      });

      const isLastOverall =
        phaseIndex === sorted.length - 1 && exerciseIndex === exercises.length - 1;
      if (!isLastOverall && restAfterExerciseMs > 0) {
        items.push({
          type: PlannedWorkoutItemType.rest,
          restDurationMs: restAfterExerciseMs,
          sortOrder: sortIndex++,
          phaseIndex,
          phaseRole,
          phaseCanSkip: canSkip,
          phaseCanContinue: canContinue,
          phaseMaxContinueTimeMs: maxContinueTimeMs,
        });
      }
    });
  });

  return items;
}
