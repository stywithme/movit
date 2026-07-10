import { PlannedWorkoutItemType, TrainingGoal } from '@prisma/client';

jest.mock('@/lib/prisma/client', () => ({ getPrisma: jest.fn() }));
jest.mock('@/modules/programs/programs.service', () => ({ programService: {} }));
jest.mock('@/modules/exercises/exercise-substitutions.service', () => ({
  exerciseSubstitutionsService: {},
}));

import {
  applyDayCustomizations,
  type EffectivePlannedWorkout,
} from '../effective-plan.service';

function workout(id: string, sortOrder: number): EffectivePlannedWorkout {
  return {
    id,
    name: { en: id },
    sortOrder,
    workoutTemplateId: `template-${id}`,
    estimatedDurationMin: 25,
    items: [
      {
        id: `item-${id}`,
        type: PlannedWorkoutItemType.exercise,
        exerciseId: `exercise-${id}`,
        sets: 3,
        targetReps: 10,
        targetDuration: null,
        variantIndex: 1,
        restBetweenSetsMs: 60_000,
        weightKg: null,
        weightPerSet: null,
        notes: null,
        restDurationMs: null,
        sortOrder: 0,
      },
    ],
  };
}

describe('applyDayCustomizations', () => {
  it('uses the saved day snapshot for reorder, removal, targets, and pose variant', () => {
    const base = [workout('morning', 0), workout('evening', 1)];
    const result = applyDayCustomizations(
      base,
      {
        day_2_3: [
          {
            id: 'evening',
            sortOrder: 0,
            items: [
              {
                id: 'item-evening',
                type: 'exercise',
                exerciseId: 'exercise-evening',
                sets: 4,
                targetReps: 8,
                variantIndex: 3,
                sortOrder: 0,
              },
            ],
          },
        ],
      },
      2,
      3,
      TrainingGoal.STRENGTH,
    );

    expect(result).toHaveLength(1);
    expect(result[0]?.id).toBe('evening');
    expect(result[0]?.items[0]).toMatchObject({
      sets: 4,
      targetReps: 8,
      variantIndex: 3,
      restBetweenSetsMs: 60_000,
    });
  });

  it('keeps the computed plan when the saved payload is malformed', () => {
    const base = [workout('morning', 0)];
    expect(
      applyDayCustomizations(base, { day_1_1: 'invalid' }, 1, 1, TrainingGoal.STRENGTH),
    ).toEqual(base);
  });
});
