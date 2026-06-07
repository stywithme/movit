import type { PrismaClient } from '@prisma/client';

type DbClient = Pick<PrismaClient, 'program'>;

export interface ProgramGraphRefs {
  nextProgramId?: string | null;
  prerequisiteProgramId?: string | null;
}

export interface ValidateProgramGraphOptions {
  /** Referenced programs must be published (e.g. when publishing the source program). */
  requirePublishedTargets?: boolean;
}

export async function validateProgramGraphRefs(
  db: DbClient,
  programId: string,
  refs: ProgramGraphRefs,
  options?: ValidateProgramGraphOptions,
): Promise<void> {
  const pairs: Array<[keyof ProgramGraphRefs, string | null | undefined]> = [
    ['nextProgramId', refs.nextProgramId],
    ['prerequisiteProgramId', refs.prerequisiteProgramId],
  ];

  for (const [field, targetId] of pairs) {
    if (!targetId) continue;
    if (targetId === programId) {
      throw new Error(`${field}: cannot reference the same program (self-loop)`);
    }

    const target = await db.program.findFirst({
      where: { id: targetId },
      select: { id: true, deletedAt: true, isPublished: true },
    });

    if (!target || target.deletedAt) {
      throw new Error(`${field}: references a missing or deleted program`);
    }

    if (options?.requirePublishedTargets && !target.isPublished) {
      throw new Error(`${field}: references an unpublished program`);
    }
  }

  if (refs.nextProgramId) {
    await assertNoNextProgramCycle(db, programId, refs.nextProgramId);
  }
}

async function assertNoNextProgramCycle(
  db: DbClient,
  startProgramId: string,
  firstNextId: string,
): Promise<void> {
  const visited = new Set<string>([startProgramId]);
  let currentId: string | null = firstNextId;

  while (currentId) {
    if (visited.has(currentId)) {
      throw new Error('nextProgramId: creates a cycle in the program chain');
    }
    visited.add(currentId);

    const row = await db.program.findFirst({
      where: { id: currentId, deletedAt: null },
      select: { nextProgramId: true },
    });
    currentId = row?.nextProgramId ?? null;
  }
}

/** Ensures a program can be enrolled (published, not soft-deleted). */
export async function assertEnrollableProgram(db: DbClient, programId: string): Promise<void> {
  const program = await db.program.findFirst({
    where: { id: programId },
    select: { id: true, deletedAt: true, isPublished: true },
  });

  if (!program || program.deletedAt) {
    throw new Error('Program is not available for enrollment (missing or deleted)');
  }

  if (!program.isPublished) {
    throw new Error('Program is not published and cannot be enrolled');
  }
}
