/**
 * Effective plan — template + progression state + user overrides (Blueprint).
 */

import { getPrisma } from '@/lib/prisma/client';
import { programService } from '@/modules/programs/programs.service';
import type { Prisma, TrainingGoal } from '@prisma/client';
import type { OverrideType } from '@prisma/client';
import { getGoalDefaults } from '@/constants/goal-defaults';

export type SuggestionSource = 'progression_state' | 'template' | 'goal_default';

export interface EffectivePlanItem {
  id: string;
  type: string;
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
  role: string | null;
  intent: string | null;
  coachingNotes: Prisma.JsonValue | null;
  skipped?: boolean;
  suggestion?: {
    suggestedWeightKg: number | null;
    suggestedReps: number | null;
    suggestedSets: number | null;
    suggestedDuration: number | null;
    source: SuggestionSource;
  };
}

export interface EffectivePlanSession {
  id: string;
  name: Prisma.JsonValue;
  sortOrder: number;
  items: EffectivePlanItem[];
}

export interface EffectivePlanResponse {
  userProgramId: string;
  programId: string | null;
  weekNumber: number;
  dayNumber: number;
  sessions: EffectivePlanSession[];
}

type SessionItemRow = {
  id: string;
  type: string;
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
  role: string | null;
  intent: string | null;
  coachingNotes: Prisma.JsonValue | null;
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
  item: SessionItemRow,
  state: ProgressionStateRow | undefined,
  trainingGoal: TrainingGoal
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
    role: item.role,
    intent: item.intent,
    coachingNotes: item.coachingNotes,
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
  anchor: SessionItemRow,
  ov: OverrideRow | undefined,
  trainingGoal: TrainingGoal,
): EffectivePlanItem | null {
  if (!ov || ov.overrideType !== 'ADD_ITEM') return null;

  const patch =
    ov.data && typeof ov.data === 'object' && !Array.isArray(ov.data)
      ? (ov.data as Record<string, unknown>)
      : {};

  const type =
    typeof patch.type === 'string'
      ? patch.type
      : typeof patch.exerciseId === 'string'
        ? 'exercise'
        : 'rest';
  const exerciseId = typeof patch.exerciseId === 'string' ? patch.exerciseId : null;
  if (type === 'exercise' && !exerciseId) {
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
    role: typeof patch.role === 'string' ? patch.role : null,
    intent: typeof patch.intent === 'string' ? patch.intent : null,
    coachingNotes:
      patch.coachingNotes && typeof patch.coachingNotes === 'object' && !Array.isArray(patch.coachingNotes)
        ? (patch.coachingNotes as Prisma.JsonValue)
        : null,
  };

  return {
    ...base,
    suggestion: buildSuggestion(base, undefined, trainingGoal),
  };
}

/** Count non-skipped exercise items for home / today-plan summaries. */
export function countEffectiveExerciseItems(session: EffectivePlanSession): number {
  return session.items.filter((i) => i.type === 'exercise' && !i.skipped).length;
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
      const bucket = overridesByItem.get(o.sessionItemId) ?? [];
      bucket.push(o);
      overridesByItem.set(o.sessionItemId, bucket);
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
        sessions: [],
      };
    }

    const trainingGoal = up.user.trainingGoal;

    const sessions: EffectivePlanSession[] = day.sessions.map((session) => {
      const items: EffectivePlanItem[] = [];

      for (const raw of session.items) {
        const st = raw.exerciseId ? stateByExercise.get(raw.exerciseId) : undefined;
        let merged = mergeProgression(raw, st, trainingGoal);
        const itemOverrides = overridesByItem.get(raw.id) ?? [];

        for (const ov of itemOverrides) {
          if (ov.overrideType !== 'ADD_ITEM') {
            merged = applyOverride(merged, ov);
          }
        }
        items.push(merged);

        for (const ov of itemOverrides) {
          const added = buildAddedItem(raw, ov, trainingGoal);
          if (added) {
            items.push(added);
          }
        }
      }

      return {
        id: session.id,
        name: session.name as Prisma.JsonValue,
        sortOrder: session.sortOrder,
        items: items.map((item, index) => ({ ...item, sortOrder: index })),
      };
    });

    return {
      userProgramId,
      programId: up.programId,
      weekNumber,
      dayNumber,
      sessions,
    };
  },
};
