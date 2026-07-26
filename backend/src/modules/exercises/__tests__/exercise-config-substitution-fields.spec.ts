/**
 * Offline swap: `/mobile/sync` exercise configs must carry the same grouping fields
 * `exerciseSubstitutionsService` ranks by, so mobile resolves candidates with no network.
 */

import { buildExerciseConfig } from '../json-builder';

type BuildInput = Parameters<typeof buildExerciseConfig>[0];

function dbExercise(overrides: Record<string, unknown> = {}): BuildInput {
  return {
    id: 'ex-1',
    name: { en: 'Bodyweight Squat', ar: 'سكوات' },
    category: { code: 'strength', name: { en: 'Strength', ar: 'قوة' } },
    countingMethod: { code: 'up_down' },
    attributes: [],
    poseVariants: [],
    ...overrides,
  } as unknown as BuildInput;
}

describe('buildExerciseConfig substitution grouping', () => {
  it('exports familyKey, familyOrder, movementPattern and archetype', () => {
    const config = buildExerciseConfig(
      dbExercise({
        familyKey: 'squat',
        familyOrder: 2,
        movementPattern: 'knee_dominant',
        archetype: 'squat',
      }),
    );

    expect(config).toEqual(
      expect.objectContaining({
        familyKey: 'squat',
        familyOrder: 2,
        movementPattern: 'knee_dominant',
        archetype: 'squat',
      }),
    );
  });

  it('keeps familyOrder 0 (first in family) instead of dropping it as falsy', () => {
    const config = buildExerciseConfig(dbExercise({ familyKey: 'squat', familyOrder: 0 }));

    expect(config.familyOrder).toBe(0);
  });

  it('omits grouping fields when the exercise has none', () => {
    const config = buildExerciseConfig(dbExercise());

    expect(config).not.toHaveProperty('familyKey');
    expect(config).not.toHaveProperty('familyOrder');
    expect(config).not.toHaveProperty('movementPattern');
    expect(config).not.toHaveProperty('archetype');
  });
});
