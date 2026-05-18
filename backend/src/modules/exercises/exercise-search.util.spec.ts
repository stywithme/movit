import { buildExerciseSearchWhere } from './exercise-search.util';

describe('buildExerciseSearchWhere', () => {
  it('returns undefined for empty or whitespace search', () => {
    expect(buildExerciseSearchWhere(undefined)).toBeUndefined();
    expect(buildExerciseSearchWhere('')).toBeUndefined();
    expect(buildExerciseSearchWhere('   ')).toBeUndefined();
  });

  it('builds OR clause for a single token including slug', () => {
    const where = buildExerciseSearchWhere('pushup');
    expect(where).toEqual(
      expect.objectContaining({
        OR: expect.arrayContaining([
          expect.objectContaining({ slug: { contains: 'pushup', mode: 'insensitive' } }),
        ]),
      }),
    );
  });

  it('trims search input', () => {
    const where = buildExerciseSearchWhere('  squat  ');
    expect(where).toEqual(
      expect.objectContaining({
        OR: expect.arrayContaining([
          expect.objectContaining({ slug: { contains: 'squat', mode: 'insensitive' } }),
        ]),
      }),
    );
  });

  it('requires all tokens for multi-word search', () => {
    const where = buildExerciseSearchWhere('bench press');
    expect(where).toEqual({
      AND: [
        { OR: expect.any(Array) },
        { OR: expect.any(Array) },
      ],
    });
  });

  it('matches exercise id when search is a UUID', () => {
    const id = 'a1b2c3d4-e5f6-4789-a012-3456789abcde';
    const where = buildExerciseSearchWhere(id);
    expect(where).toEqual(
      expect.objectContaining({
        OR: expect.arrayContaining([{ id }]),
      }),
    );
  });
});
