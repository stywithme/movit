/**
 * P1.3: planned workout complete/start idempotency by client key.
 * Parallel same key → one report; different calendar day → new report.
 */

const plannedWorkoutFindFirst = jest.fn();
const reportFindUnique = jest.fn();
const reportFindFirst = jest.fn();
const reportCreate = jest.fn();
const reportUpdateMany = jest.fn();
const reportFindUniqueOrThrow = jest.fn();
const reportFindMany = jest.fn();
const userProgramFindFirst = jest.fn();
const progressUpsert = jest.fn();
const plannedWorkoutItemFindMany = jest.fn();

jest.mock('@/lib/prisma/client', () => ({
  prisma: {
    plannedWorkout: {
      findFirst: (...args: unknown[]) => plannedWorkoutFindFirst(...args),
    },
    plannedWorkoutReport: {
      findUnique: (...args: unknown[]) => reportFindUnique(...args),
      findFirst: (...args: unknown[]) => reportFindFirst(...args),
      create: (...args: unknown[]) => reportCreate(...args),
      updateMany: (...args: unknown[]) => reportUpdateMany(...args),
      findUniqueOrThrow: (...args: unknown[]) => reportFindUniqueOrThrow(...args),
      findMany: (...args: unknown[]) => reportFindMany(...args),
    },
    userProgram: {
      findFirst: (...args: unknown[]) => userProgramFindFirst(...args),
    },
    userProgramProgress: {
      upsert: (...args: unknown[]) => progressUpsert(...args),
    },
    plannedWorkoutItem: {
      findMany: (...args: unknown[]) => plannedWorkoutItemFindMany(...args),
    },
  },
}));

const evaluateAfterPlannedWorkout = jest.fn().mockResolvedValue([]);

jest.mock('@/modules/progression/progression.service', () => ({
  progressionService: {
    evaluateAfterWorkout: jest.fn(),
    evaluateAfterPlannedWorkout: (...args: unknown[]) => evaluateAfterPlannedWorkout(...args),
  },
}));

import {
  completePlannedWorkoutReport,
  startPlannedWorkoutReport,
} from '../workout-executions.service';

const USER = 'user-1';
const WORKOUT = 'pw-1';
const KEY = 'outbox-op-complete-001';

function plannedWorkoutRow() {
  return {
    id: WORKOUT,
    day: {
      dayNumber: 1,
      plannedWorkouts: [{ id: WORKOUT }],
      week: { weekNumber: 1, programId: 'prog-1' },
    },
  };
}

function inProgressReport(overrides: Record<string, unknown> = {}) {
  return {
    id: 'report-1',
    userId: USER,
    programId: 'prog-1',
    plannedWorkoutId: WORKOUT,
    weekNumber: 1,
    dayNumber: 1,
    startedAt: new Date('2026-07-09T10:00:00.000Z'),
    completedAt: null,
    status: 'in_progress',
    totalDurationMs: null,
    totalExercises: null,
    totalSets: null,
    completedSets: null,
    totalReps: null,
    avgAccuracy: null,
    avgFormScore: null,
    rpe: null,
    report: null,
    idempotencyKey: null,
    createdAt: new Date('2026-07-09T10:00:00.000Z'),
    updatedAt: new Date('2026-07-09T10:00:00.000Z'),
    ...overrides,
  };
}

function completedReport(overrides: Record<string, unknown> = {}) {
  return {
    ...inProgressReport(),
    status: 'completed',
    completedAt: new Date('2026-07-09T11:00:00.000Z'),
    totalReps: 40,
    idempotencyKey: KEY,
    ...overrides,
  };
}

describe('planned-workout-complete.idempotency (P1.3)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    plannedWorkoutFindFirst.mockResolvedValue(plannedWorkoutRow());
    userProgramFindFirst.mockResolvedValue(null);
    plannedWorkoutItemFindMany.mockResolvedValue([]);
    reportFindMany.mockResolvedValue([]);
    progressUpsert.mockResolvedValue({});
  });

  it('parallel completes with the same idempotencyKey yield one report', async () => {
    // Simulate DB unique race: both miss findUnique, first create wins, second gets P2002
    // then re-reads; CAS updateMany ensures only one progression path.
    let createCalls = 0;
    let claimed = false;
    const open = inProgressReport({ idempotencyKey: KEY });

    reportFindUnique.mockImplementation(async () => {
      if (createCalls === 0) return null;
      if (claimed) return completedReport();
      return open;
    });
    reportFindFirst.mockResolvedValue(null);
    reportCreate.mockImplementation(async ({ data }: { data: Record<string, unknown> }) => {
      createCalls += 1;
      if (createCalls > 1) {
        throw Object.assign(new Error('Unique constraint failed'), { code: 'P2002' });
      }
      return inProgressReport({
        id: 'report-1',
        idempotencyKey: data.idempotencyKey ?? KEY,
      });
    });
    let claimWins = 0;
    reportUpdateMany.mockImplementation(async () => {
      if (claimed) return { count: 0 };
      claimed = true;
      claimWins += 1;
      return { count: 1 };
    });
    reportFindUniqueOrThrow.mockImplementation(async () => completedReport());
    userProgramFindFirst.mockResolvedValue({ id: 'up-1', programId: 'prog-1', isActive: true });
    plannedWorkoutItemFindMany.mockResolvedValue([{ exerciseId: 'ex-1' }]);

    const payload = { idempotencyKey: KEY, totalReps: 40, completedAt: Date.parse('2026-07-09T11:00:00.000Z') };
    const [a, b] = await Promise.all([
      completePlannedWorkoutReport(USER, WORKOUT, payload),
      completePlannedWorkoutReport(USER, WORKOUT, payload),
    ]);

    expect(a.id).toBe(b.id);
    expect(a.idempotencyKey).toBe(KEY);
    expect(b.idempotencyKey).toBe(KEY);
    expect(reportCreate).toHaveBeenCalledTimes(2);
    expect(claimWins).toBe(1);
    expect(evaluateAfterPlannedWorkout).toHaveBeenCalledTimes(1);
    expect(a.status).toBe('completed');
    expect(b.status).toBe('completed');
  });

  it('same key replay after completed returns prior report without progression', async () => {
    reportFindUnique.mockResolvedValue(completedReport());

    const again = await completePlannedWorkoutReport(USER, WORKOUT, {
      idempotencyKey: KEY,
      totalReps: 99,
    });

    expect(again.id).toBe('report-1');
    expect(again.totalReps).toBe(40);
    expect(reportCreate).not.toHaveBeenCalled();
    expect(reportUpdateMany).not.toHaveBeenCalled();
    expect(evaluateAfterPlannedWorkout).not.toHaveBeenCalled();
  });

  it('different calendar day without key creates a new report (re-complete allowed)', async () => {
    // No key: same-day completed lookup misses (yesterday), then create+finalize.
    reportFindUnique.mockResolvedValue(null);
    reportFindFirst
      .mockResolvedValueOnce(null) // same-day completed fallback
      .mockResolvedValueOnce(null); // in_progress lookup
    reportCreate.mockResolvedValue(
      inProgressReport({
        id: 'report-day2',
        startedAt: new Date('2026-07-10T10:00:00.000Z'),
        createdAt: new Date('2026-07-10T10:00:00.000Z'),
      }),
    );
    reportUpdateMany.mockResolvedValue({ count: 1 });
    reportFindUniqueOrThrow.mockResolvedValue(
      completedReport({
        id: 'report-day2',
        idempotencyKey: null,
        completedAt: new Date('2026-07-10T11:00:00.000Z'),
        totalReps: 50,
      }),
    );

    const result = await completePlannedWorkoutReport(USER, WORKOUT, {
      totalReps: 50,
      completedAt: Date.parse('2026-07-10T11:00:00.000Z'),
    });

    expect(result.id).toBe('report-day2');
    expect(reportCreate).toHaveBeenCalledTimes(1);
    expect(reportUpdateMany).toHaveBeenCalledTimes(1);
  });

  it('start reuses in_progress report for the same idempotencyKey', async () => {
    const existing = inProgressReport({ idempotencyKey: KEY });
    reportFindUnique.mockResolvedValue(existing);

    const result = await startPlannedWorkoutReport(USER, WORKOUT, {
      weekNumber: 1,
      dayNumber: 1,
      idempotencyKey: KEY,
    });

    expect(result.id).toBe('report-1');
    expect(reportCreate).not.toHaveBeenCalled();
  });

  it('start without key reuses same-day in_progress report', async () => {
    reportFindFirst.mockResolvedValue(inProgressReport());

    const result = await startPlannedWorkoutReport(USER, WORKOUT, {
      weekNumber: 1,
      dayNumber: 1,
    });

    expect(result.id).toBe('report-1');
    expect(reportCreate).not.toHaveBeenCalled();
  });
});
