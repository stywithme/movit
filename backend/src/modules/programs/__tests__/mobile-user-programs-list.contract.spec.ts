/**
 * P2.1 contract: lightweight enrollment list matches sync userPrograms shape.
 */

const userProgramFindMany = jest.fn();
const trainingProfileFindUnique = jest.fn();

jest.mock('@/lib/prisma/client', () => ({
  getPrisma: async () => ({
    userProgram: {
      findMany: (...args: unknown[]) => userProgramFindMany(...args),
    },
    trainingProfile: {
      findUnique: (...args: unknown[]) => trainingProfileFindUnique(...args),
    },
  }),
}));

import { programService } from '../programs.service';

describe('mobile user-programs list (P2.1)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    trainingProfileFindUnique.mockResolvedValue({ trainingWeekdays: [1, 3, 5] });
  });

  it('exports enrollments with trainingWeekdays', async () => {
    const updatedAt = new Date('2026-06-10T12:00:00.000Z');
    userProgramFindMany.mockResolvedValue([
      {
        id: 'up-1',
        programId: 'prog-1',
        name: { en: 'Foundation', ar: 'أساس' },
        startDate: new Date('2026-01-01T00:00:00.000Z'),
        isActive: true,
        customizations: { day_1_1: [] },
        updatedAt,
        customizationsUpdatedAt: updatedAt,
      },
    ]);

    const rows = await programService.listUserProgramsForMobile('user-1');

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      id: 'up-1',
      programId: 'prog-1',
      isActive: true,
      trainingWeekdays: [1, 3, 5],
      customizationsUpdatedAt: '2026-06-10T12:00:00.000Z',
    });
    expect(rows[0].startDate).toBe('2026-01-01T00:00:00.000Z');
    expect(rows[0].updatedAt).toBe('2026-06-10T12:00:00.000Z');
  });

  it('applies updatedAfter delta filter', async () => {
    userProgramFindMany.mockResolvedValue([]);
    const updatedAfter = new Date('2026-06-09T00:00:00.000Z');

    await programService.listUserProgramsForMobile('user-1', updatedAfter);

    expect(userProgramFindMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          userId: 'user-1',
          updatedAt: { gt: updatedAfter },
        },
      }),
    );
  });
});
