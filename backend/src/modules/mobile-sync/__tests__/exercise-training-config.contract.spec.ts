/**
 * R4: GET exercise training-config by slug uses buildExerciseConfig.
 */

const exerciseFindFirst = jest.fn();

jest.mock('@/lib/prisma/client', () => ({
  getPrisma: async () => ({
    exercise: {
      findFirst: (...args: unknown[]) => exerciseFindFirst(...args),
    },
  }),
}));

jest.mock('@/modules/exercises/json-builder', () => ({
  exerciseFullInclude: { poseVariants: true },
  buildExerciseConfig: () => ({
    name: { en: 'Bicep Curl', ar: 'بايسبس' },
    countingMethod: 'reps',
  }),
}));

import { mobileSyncService } from '../mobile-sync.service';

describe('exercise training-config by slug (R4)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns built config with id/slug/updatedAt for published exercise', async () => {
    const updatedAt = new Date('2026-07-11T12:00:00.000Z');
    exerciseFindFirst.mockResolvedValue({
      id: 'ex-1',
      slug: 'bicep_curl',
      updatedAt,
      name: { en: 'Bicep Curl', ar: 'بايسبس' },
    });

    const config = await mobileSyncService.getExerciseTrainingConfig('bicep_curl');

    expect(config).toEqual(
      expect.objectContaining({
        id: 'ex-1',
        slug: 'bicep_curl',
        updatedAt: updatedAt.toISOString(),
        name: { en: 'Bicep Curl', ar: 'بايسبس' },
      }),
    );
    expect(exerciseFindFirst).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { slug: 'bicep_curl', status: 'published', deletedAt: null },
        include: expect.any(Object),
      }),
    );
  });

  it('returns null when exercise missing', async () => {
    exerciseFindFirst.mockResolvedValue(null);
    expect(await mobileSyncService.getExerciseTrainingConfig('nope')).toBeNull();
  });

  it('returns null for blank slug', async () => {
    expect(await mobileSyncService.getExerciseTrainingConfig('  ')).toBeNull();
    expect(exerciseFindFirst).not.toHaveBeenCalled();
  });
});
