/**
 * P2.2 H26: getPublishedForMobile probes ids before deep include on delta.
 */

const programFindMany = jest.fn();

jest.mock('@/lib/prisma/client', () => ({
  getPrisma: async () => ({
    program: {
      findMany: (...args: unknown[]) => programFindMany(...args),
    },
  }),
}));

import { programService } from '../programs.service';

describe('getPublishedForMobile delta (P2.2 H26)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns [] after light id probe when nothing changed', async () => {
    programFindMany.mockResolvedValueOnce([]);

    const updatedAfter = new Date('2026-07-01T00:00:00.000Z');
    const rows = await programService.getPublishedForMobile(updatedAfter);

    expect(rows).toEqual([]);
    expect(programFindMany).toHaveBeenCalledTimes(1);
    expect(programFindMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          isPublished: true,
          deletedAt: null,
          updatedAt: { gt: updatedAfter },
        }),
        select: { id: true },
      }),
    );
  });

  it('loads deep tree only for changed ids', async () => {
    programFindMany
      .mockResolvedValueOnce([{ id: 'prog-1' }])
      .mockResolvedValueOnce([]); // second call returns raw programs; empty → no exports

    const updatedAfter = new Date('2026-07-01T00:00:00.000Z');
    await programService.getPublishedForMobile(updatedAfter);

    expect(programFindMany).toHaveBeenCalledTimes(2);
    expect(programFindMany.mock.calls[1][0]).toEqual(
      expect.objectContaining({
        where: { id: { in: ['prog-1'] } },
        include: expect.any(Object),
      }),
    );
  });
});
