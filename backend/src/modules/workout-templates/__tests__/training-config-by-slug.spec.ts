/**
 * B2: workout training-config resolves by id then slug.
 */

const workoutFindFirst = jest.fn();

jest.mock('@/lib/prisma/client', () => ({
  getPrisma: async () => ({
    workoutTemplate: {
      findFirst: (...args: unknown[]) => workoutFindFirst(...args),
    },
  }),
}));

import { workoutService } from '../workout-templates.service';

const publishedWorkout = {
  id: '800d3683-aaaa-bbbb-cccc-dddddddddddd',
  slug: 'triple_alternating',
  name: { en: 'Triple', ar: 'ثلاثي' },
  description: null,
  coverImageUrl: null,
  levelId: null,
  level: null,
  estimatedDurationMin: 20,
  phases: [],
  exercises: [],
};

describe('training-config by id or slug (B2)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('resolves by UUID id on first findFirst', async () => {
    workoutFindFirst.mockResolvedValueOnce(publishedWorkout);

    const config = await workoutService.getTrainingConfig(publishedWorkout.id);

    expect(config).not.toBeNull();
    expect(config?.id).toBe(publishedWorkout.id);
    expect(config?.slug).toBe('triple_alternating');
    expect(workoutFindFirst).toHaveBeenCalledTimes(1);
    expect(workoutFindFirst.mock.calls[0][0].where).toEqual({
      id: publishedWorkout.id,
      status: 'published',
      deletedAt: null,
    });
  });

  it('falls back to slug when id miss', async () => {
    workoutFindFirst.mockResolvedValueOnce(null).mockResolvedValueOnce(publishedWorkout);

    const config = await workoutService.getTrainingConfig('triple_alternating');

    expect(config).not.toBeNull();
    expect(config?.slug).toBe('triple_alternating');
    expect(workoutFindFirst).toHaveBeenCalledTimes(2);
    expect(workoutFindFirst.mock.calls[0][0].where.id).toBe('triple_alternating');
    expect(workoutFindFirst.mock.calls[1][0].where).toEqual({
      slug: 'triple_alternating',
      status: 'published',
      deletedAt: null,
    });
  });

  it('returns null when neither id nor slug matches', async () => {
    workoutFindFirst.mockResolvedValue(null);

    const config = await workoutService.getTrainingConfig('missing_slug');

    expect(config).toBeNull();
    expect(workoutFindFirst).toHaveBeenCalledTimes(2);
  });
});
