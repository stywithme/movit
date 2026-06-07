import { validateProgramGraphRefs, assertEnrollableProgram } from './program-graph-validation';

function mockDb(programs: Record<string, { deletedAt?: Date | null; isPublished?: boolean; nextProgramId?: string | null }>) {
  return {
    program: {
      findFirst: jest.fn(async ({ where }: { where: { id: string } }) => {
        const row = programs[where.id];
        if (!row) return null;
        return {
          id: where.id,
          deletedAt: row.deletedAt ?? null,
          isPublished: row.isPublished ?? false,
          nextProgramId: row.nextProgramId ?? null,
        };
      }),
    },
  };
}

describe('validateProgramGraphRefs', () => {
  it('rejects self-loop on nextProgramId', async () => {
    const db = mockDb({ p1: { isPublished: true } });
    await expect(
      validateProgramGraphRefs(db as never, 'p1', { nextProgramId: 'p1' }),
    ).rejects.toThrow(/self-loop/);
  });

  it('rejects missing target', async () => {
    const db = mockDb({});
    await expect(
      validateProgramGraphRefs(db as never, 'p1', { nextProgramId: 'missing' }),
    ).rejects.toThrow(/missing or deleted/);
  });

  it('rejects unpublished target when publishing', async () => {
    const db = mockDb({ p2: { isPublished: false } });
    await expect(
      validateProgramGraphRefs(db as never, 'p1', { nextProgramId: 'p2' }, { requirePublishedTargets: true }),
    ).rejects.toThrow(/unpublished/);
  });

  it('detects cycle A -> B -> A', async () => {
    const db = mockDb({
      p1: { isPublished: true, nextProgramId: 'p2' },
      p2: { isPublished: true, nextProgramId: 'p1' },
    });
    await expect(
      validateProgramGraphRefs(db as never, 'p1', { nextProgramId: 'p2' }),
    ).rejects.toThrow(/cycle/);
  });
});

describe('assertEnrollableProgram', () => {
  it('rejects draft programs', async () => {
    const db = mockDb({ p1: { isPublished: false } });
    await expect(assertEnrollableProgram(db as never, 'p1')).rejects.toThrow(/not published/);
  });

  it('accepts published programs', async () => {
    const db = mockDb({ p1: { isPublished: true } });
    await expect(assertEnrollableProgram(db as never, 'p1')).resolves.toBeUndefined();
  });
});
