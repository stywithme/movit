/**
 * Effective plan — template + progression state + user overrides (Blueprint).
 */

import { getPrisma } from '@/lib/prisma/client';
import { programService } from '@/modules/programs/programs.service';
import type { ExerciseSubstitution } from '@/modules/exercises/exercise-substitutions.service';
import { exerciseSubstitutionsService } from '@/modules/exercises/exercise-substitutions.service';
import { PlannedWorkoutItemType, type OverrideType, type Prisma, type TrainingGoal } from '@prisma/client';
import { getGoalDefaults } from '@/constants/goal-defaults';

export type SuggestionSource = 'progression_state' | 'template' | 'goal_default';

export interface EffectivePlanItem {
  id: string;
  type: PlannedWorkoutItemType;
  exerciseId: string | null;
  sets: number | null;
  targetReps: number | null;
  targetDuration: number | null;
  restBetweenSetsMs: number | null;
  weightKg: number | null;
  weightPerSet: Prisma.JsonValue | null;
  notes: Prisma.JsonValue | null;
  restDurationMs: number | null;
  sortOrder: number;
  phaseIndex?: number;
  phaseRole?: string;
  skipped?: boolean;
  /** Populated from Exercise catalog at runtime (not stored on planned workout item). */
  intent?: string | null;
  coachingNotes?: Prisma.JsonValue | null;
  /** Same-source substitutions as mobile substitution picker (family / movement pattern). */
  substitutionCandidates?: ExerciseSubstitution[];
  suggestion?: {
    suggestedWeightKg: number | null;
    suggestedReps: number | null;
    suggestedSets: number | null;
    suggestedDuration: number | null;
    source: SuggestionSource;
  };
}

export interface EffectivePlannedWorkout {
  id: string;
  name: Prisma.JsonValue;
  sortOrder: number;
  workoutTemplateId: string;
  items: EffectivePlanItem[];
}

export interface EffectivePlanResponse {
  userProgramId: string;
  programId: string | null;
  weekNumber: number;
  dayNumber: number;
  plannedWorkouts: EffectivePlannedWorkout[];
}

type PlannedWorkoutItemRow = {
  id: string;
  type: PlannedWorkoutItemType;
  exerciseId: string | null;
  sets: number | null;
  targetReps: number | null;
  targetDuration: number | null;
  restBetweenSetsMs: number | null;
  weightKg: number | null;
  weightPerSet: Prisma.JsonValue | null;
  notes: Prisma.JsonValue | null;
  restDurationMs: number | null;
  sortOrder: number;
  exercise?: {
    intent: string | null;
    coachingNotes: Prisma.JsonValue | null;
  } | null;
};

type ProgressionStateRow = {
  currentWeightKg: number | null;
  currentTargetReps: number | null;
  currentTargetDuration: number | null;
  currentTargetSets: number | null;
  currentDifficultyCode: string | null;
};

type OverrideRow = {
  id: string;
  overrideType: OverrideType;
  data: Prisma.JsonValue | null;
};

function buildSuggestion(
  merged: Omit<EffectivePlanItem, 'suggestion' | 'skipped'>,
  state: ProgressionStateRow | undefined,
  trainingGoal: TrainingGoal
): EffectivePlanItem['suggestion'] {
  const gd = getGoalDefaults(trainingGoal);
  const hasProgression =
    !!state &&
    (state.currentWeightKg != null ||
      state.currentTargetReps != null ||
      state.currentTargetDuration != null ||
      state.currentTargetSets != null);

  let source: SuggestionSource = 'goal_default';
  if (hasProgression) source = 'progression_state';
  else if (
    merged.weightKg != null ||
    merged.targetReps != null ||
    merged.targetDuration != null ||
    merged.sets != null
  ) {
    source = 'template';
  }

  return {
    suggestedWeightKg: merged.weightKg ?? null,
    suggestedReps: merged.targetReps ?? gd.repsMin,
    suggestedSets: merged.sets ?? gd.setsMin,
    suggestedDuration: merged.targetDuration ?? null,
    source,
  };
}

function mergeProgression(
  item: PlannedWorkoutItemRow,
  state: ProgressionStateRow | undefined,
  trainingGoal: TrainingGoal,
  substitutionCandidates?: ExerciseSubstitution[],
): EffectivePlanItem {
  const base: Omit<EffectivePlanItem, 'suggestion' | 'skipped'> = {
    id: item.id,
    type: item.type,
    exerciseId: item.exerciseId,
    sets: item.sets,
    targetReps: item.targetReps,
    targetDuration: item.targetDuration,
    restBetweenSetsMs: item.restBetweenSetsMs,
    weightKg: item.weightKg,
    weightPerSet: item.weightPerSet,
    notes: item.notes,
    restDurationMs: item.restDurationMs,
    sortOrder: item.sortOrder,
    intent: item.exercise?.intent ?? null,
    coachingNotes: item.exercise?.coachingNotes ?? null,
    substitutionCandidates:
      item.type === PlannedWorkoutItemType.exercise && item.exerciseId && substitutionCandidates?.length
        ? substitutionCandidates
        : undefined,
  };

  if (item.exerciseId && state) {
    base.weightKg = state.currentWeightKg ?? base.weightKg;
    base.targetReps = state.currentTargetReps ?? base.targetReps;
    base.targetDuration = state.currentTargetDuration ?? base.targetDuration;
    base.sets = state.currentTargetSets ?? base.sets;
  }

  return {
    ...base,
    suggestion: buildSuggestion(base, state, trainingGoal),
  };
}

function applyOverride(
  item: EffectivePlanItem,
  ov: OverrideRow | undefined
): EffectivePlanItem {
  if (!ov) return item;
  if (ov.overrideType === 'SKIP_ITEM') {
    return { ...item, skipped: true };
  }
  if (ov.overrideType === 'ADD_ITEM') {
    return item;
  }
  const patch =
    ov.data && typeof ov.data === 'object' && !Array.isArray(ov.data)
      ? (ov.data as Record<string, unknown>)
      : {};
  if (ov.overrideType === 'REPLACE_EXERCISE' && typeof patch.exerciseId === 'string') {
    return { ...item, exerciseId: patch.exerciseId };
  }
  return {
    ...item,
    ...(typeof patch.weightKg === 'number' ? { weightKg: patch.weightKg } : {}),
    ...(typeof patch.targetReps === 'number' ? { targetReps: patch.targetReps } : {}),
    ...(typeof patch.targetDuration === 'number' ? { targetDuration: patch.targetDuration } : {}),
    ...(typeof patch.sets === 'number' ? { sets: patch.sets } : {}),
    ...(typeof patch.restBetweenSetsMs === 'number'
      ? { restBetweenSetsMs: patch.restBetweenSetsMs }
      : {}),
    ...(typeof patch.restDurationMs === 'number' ? { restDurationMs: patch.restDurationMs } : {}),
  };
}

function buildAddedItem(
  anchor: PlannedWorkoutItemRow,
  ov: OverrideRow | undefined,
  trainingGoal: TrainingGoal,
): EffectivePlanItem | null {
  if (!ov || ov.overrideType !== 'ADD_ITEM') return null;

  const patch =
    ov.data && typeof ov.data === 'object' && !Array.isArray(ov.data)
      ? (ov.data as Record<string, unknown>)
      : {};

  const type =
    patch.type === PlannedWorkoutItemType.exercise || patch.type === 'exercise'
      ? PlannedWorkoutItemType.exercise
      : patch.type === PlannedWorkoutItemType.rest || patch.type === 'rest'
        ? PlannedWorkoutItemType.rest
        : typeof patch.exerciseId === 'string'
          ? PlannedWorkoutItemType.exercise
          : PlannedWorkoutItemType.rest;
  const exerciseId = typeof patch.exerciseId === 'string' ? patch.exerciseId : null;
  if (type === PlannedWorkoutItemType.exercise && !exerciseId) {
    return null;
  }

  const base: Omit<EffectivePlanItem, 'suggestion' | 'skipped'> = {
    id: `${anchor.id}__add__${ov.id}`,
    type,
    exerciseId,
    sets: typeof patch.sets === 'number' ? patch.sets : null,
    targetReps: typeof patch.targetReps === 'number' ? patch.targetReps : null,
    targetDuration: typeof patch.targetDuration === 'number' ? patch.targetDuration : null,
    restBetweenSetsMs:
      typeof patch.restBetweenSetsMs === 'number' ? patch.restBetweenSetsMs : null,
    weightKg: typeof patch.weightKg === 'number' ? patch.weightKg : null,
    weightPerSet: Array.isArray(patch.weightPerSet)
      ? (patch.weightPerSet as Prisma.JsonValue)
      : null,
    notes:
      patch.notes && typeof patch.notes === 'object' && !Array.isArray(patch.notes)
        ? (patch.notes as Prisma.JsonValue)
        : null,
    restDurationMs: typeof patch.restDurationMs === 'number' ? patch.restDurationMs : null,
    sortOrder: anchor.sortOrder + 1,
  };

  return {
    ...base,
    suggestion: buildSuggestion(base, undefined, trainingGoal),
  };
}

/** Count non-skipped exercise items for home / today-plan summaries. */
export function countEffectiveExerciseItems(plannedWorkout: EffectivePlannedWorkout): number {
  return plannedWorkout.items.filter((i) => i.type === PlannedWorkoutItemType.exercise && !i.skipped).length;
}

export const effectivePlanService = {
  async getEffectivePlan(
    userId: string,
    userProgramId: string,
    weekNumber: number,
    dayNumber: number
  ): Promise<EffectivePlanResponse | null> {
    const prisma = await getPrisma();

    const up = await prisma.userProgram.findFirst({
      where: { id: userProgramId, userId },
      include: {
        progressionStates: true,
        overrides: {
          where: { weekNumber, dayNumber },
          orderBy: { createdAt: 'asc' },
        },
        user: { select: { trainingGoal: true } },
      },
    });

    if (!up?.programId) return null;

    const program = await programService.getById(up.programId);
    if (!program) return null;

    const overridesByItem = new Map<string, Array<(typeof up.overrides)[0]>>();
    for (const o of up.overrides) {
      const key = o.workoutTemplateExerciseId ?? o.plannedWorkoutItemId;
      if (!key) continue;
      const bucket = overridesByItem.get(key) ?? [];
      bucket.push(o);
      overridesByItem.set(key, bucket);
    }

    const stateByExercise = new Map(up.progressionStates.map((s) => [s.exerciseId, s]));

    const week = program.weeks.find((w) => w.weekNumber === weekNumber);
    const day = week?.days.find((d) => d.dayNumber === dayNumber);
    if (!week || !day) {
      return {
        userProgramId,
        programId: up.programId,
        weekNumber,
        dayNumber,
        plannedWorkouts: [],
      };
    }

    const trainingGoal = up.user.trainingGoal;

    const exerciseIdsForSubs = new Set<string>();
    for (const s of day.plannedWorkouts) {
      for (const phase of s.workoutTemplate.phases) {
        for (const ex of phase.exercises) {
          if (ex.exerciseId) exerciseIdsForSubs.add(ex.exerciseId);
        }
      }
    }
    const substitutionByExercise = new Map<string, ExerciseSubstitution[]>();
    await Promise.all(
      [...exerciseIdsForSubs].map(async (exerciseId) => {
        const subs = await exerciseSubstitutionsService.getSubstitutions(exerciseId);
        substitutionByExercise.set(exerciseId, subs);
      }),
    );

    const plannedWorkouts: EffectivePlannedWorkout[] = day.plannedWorkouts.map((plannedWorkoutRow) => {
      const items: EffectivePlanItem[] = [];
      const sortedPhases = [...plannedWorkoutRow.workoutTemplate.phases].sort(
        (a, b) => a.sortOrder - b.sortOrder,
      );

      for (const [phaseIndex, phase] of sortedPhases.entries()) {
        const sortedExercises = [...phase.exercises].sort((a, b) => a.sortOrder - b.sortOrder);
        const phaseRole = String(phase.phase.role);
        for (let exerciseIndex = 0; exerciseIndex < sortedExercises.length; exerciseIndex++) {
          const exercise = sortedExercises[exerciseIndex]!;
          const raw: PlannedWorkoutItemRow = {
            id: exercise.id,
            type: PlannedWorkoutItemType.exercise,
            exerciseId: exercise.exerciseId,
            sets: exercise.sets,
            targetReps: exercise.targetReps,
            targetDuration: exercise.targetDuration,
            restBetweenSetsMs: exercise.restBetweenSetsMs,
            weightKg: exercise.weightKg,
            weightPerSet: exercise.weightPerSet,
            notes: exercise.notes,
            restDurationMs: null,
            sortOrder: items.length,
            exercise: exercise.exercise,
          };

          const st = raw.exerciseId ? stateByExercise.get(raw.exerciseId) : undefined;
          const subs = raw.exerciseId ? substitutionByExercise.get(raw.exerciseId) : undefined;
          let merged = mergeProgression(raw, st, trainingGoal, subs);
          const itemOverrides = overridesByItem.get(exercise.id) ?? [];

          for (const ov of itemOverrides) {
            if (ov.overrideType !== 'ADD_ITEM') {
              merged = applyOverride(merged, ov);
            }
          }
          items.push({ ...merged, phaseIndex, phaseRole });

          for (const ov of itemOverrides) {
            const added = buildAddedItem(raw, ov, trainingGoal);
            if (added) items.push({ ...added, phaseIndex, phaseRole });
          }

          const isLastOverall =
            phaseIndex === sortedPhases.length - 1 &&
            exerciseIndex === sortedExercises.length - 1;
          const restAfter = exercise.restAfterExerciseMs ?? 0;
          if (!isLastOverall && restAfter > 0) {
            items.push({
              id: `${exercise.id}__rest`,
              type: PlannedWorkoutItemType.rest,
              exerciseId: null,
              sets: null,
              targetReps: null,
              targetDuration: null,
              restBetweenSetsMs: null,
              weightKg: null,
              weightPerSet: null,
              notes: null,
              restDurationMs: restAfter,
              sortOrder: items.length,
              phaseIndex,
              phaseRole,
              suggestion: buildSuggestion(
                {
                  id: `${exercise.id}__rest`,
                  type: PlannedWorkoutItemType.rest,
                  exerciseId: null,
                  sets: null,
                  targetReps: null,
                  targetDuration: null,
                  restBetweenSetsMs: null,
                  weightKg: null,
                  weightPerSet: null,
                  notes: null,
                  restDurationMs: restAfter,
                  sortOrder: items.length,
                },
                undefined,
                trainingGoal,
              ),
            });
          }
        }
      }

      return {
        id: plannedWorkoutRow.id,
        name: plannedWorkoutRow.name as Prisma.JsonValue,
        sortOrder: plannedWorkoutRow.sortOrder,
        workoutTemplateId: plannedWorkoutRow.workoutTemplateId,
        items: items.map((item, index) => ({ ...item, sortOrder: index })),
      };
    });

    return {
      userProgramId,
      programId: up.programId,
      weekNumber,
      dayNumber,
      plannedWorkouts,
    };
  },
};
