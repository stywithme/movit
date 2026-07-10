/**
 * P0.1 contract: null stability/alignment metrics must persist as null
 * (not coerced to 0 — that would pollute averages and quality gates).
 */

import type { WorkoutExecutionUploadPayload } from '../workout-executions.types';

const metricsCreate = jest.fn();
const repCreateMany = jest.fn();
const workoutUpsert = jest.fn();
const metricsDeleteMany = jest.fn();
const repDeleteMany = jest.fn();
const exerciseFindUnique = jest.fn();
const executionFindFirst = jest.fn();

jest.mock('@/lib/prisma/client', () => ({
  prisma: {
    exercise: { findUnique: (...args: unknown[]) => exerciseFindUnique(...args) },
    workoutExecution: {
      findFirst: (...args: unknown[]) => executionFindFirst(...args),
      upsert: (...args: unknown[]) => workoutUpsert(...args),
    },
    workoutExecutionMetrics: {
      create: (...args: unknown[]) => metricsCreate(...args),
      deleteMany: (...args: unknown[]) => metricsDeleteMany(...args),
    },
    repMetrics: {
      createMany: (...args: unknown[]) => repCreateMany(...args),
      deleteMany: (...args: unknown[]) => repDeleteMany(...args),
    },
    $transaction: async (fn: (tx: unknown) => Promise<unknown>) =>
      fn({
        workoutExecution: {
          upsert: (...args: unknown[]) => workoutUpsert(...args),
        },
        workoutExecutionMetrics: {
          create: (...args: unknown[]) => metricsCreate(...args),
          deleteMany: (...args: unknown[]) => metricsDeleteMany(...args),
        },
        repMetrics: {
          createMany: (...args: unknown[]) => repCreateMany(...args),
          deleteMany: (...args: unknown[]) => repDeleteMany(...args),
        },
      }),
  },
}));

jest.mock('@/modules/progression/progression.service', () => ({
  progressionService: { evaluateAfterWorkout: jest.fn() },
}));

import { saveWorkoutExecution } from '../workout-executions.service';

describe('WorkoutExecutionNullMetricsContract', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    exerciseFindUnique.mockResolvedValue({
      id: 'ex-1',
      name: { ar: 'سكوات', en: 'Squat' },
      slug: 'bodyweight-squat',
    });
    workoutUpsert.mockResolvedValue({
      id: 'exec-null-1',
      totalReps: 1,
      countedReps: 1,
      invalidReps: 0,
    });
    metricsCreate.mockResolvedValue({});
    repCreateMany.mockResolvedValue({ count: 1 });
    metricsDeleteMany.mockResolvedValue({ count: 0 });
    repDeleteMany.mockResolvedValue({ count: 0 });
    executionFindFirst.mockResolvedValue({
      id: 'exec-null-1',
      exerciseId: 'ex-1',
      exercise: { name: { ar: 'سكوات', en: 'Squat' } },
      timestamp: new Date('2026-07-09T12:00:00.000Z'),
      durationMs: 3000,
      totalReps: 1,
      countedReps: 1,
      invalidReps: 0,
      weightKg: null,
      weightUnit: 'kg',
      executionMetrics: {
        avgRom: 90,
        avgSymmetry: null,
        avgStability: null,
        avgTempo: [1000, 0, 1000],
        avgVelocity: null,
        avgFormScore: 80,
        avgAlignmentAccuracy: null,
        totalTUT: 2000,
        totalVolume: null,
        maxWeight: null,
        est1RM: null,
        relativeStrength: null,
        intensityPercentage: null,
        formConsistency: null,
        fatigueIndex: null,
      },
      repMetrics: [
        {
          repNumber: 1,
          durationMs: 2000,
          worstState: 0,
          score: 80,
          weightKg: null,
          side: null,
          rom: 90,
          symmetry: null,
          stability: null,
          tempo: [1000, 0, 1000],
          velocity: null,
          formScore: 80,
          alignmentAccuracy: null,
        },
      ],
    });
  });

  it('accepts null stability/alignment and stores null (not 0)', async () => {
    const payload: WorkoutExecutionUploadPayload = {
      id: 'exec-null-1',
      exerciseId: 'bodyweight-squat',
      timestamp: Date.parse('2026-07-09T12:00:00.000Z'),
      durationMs: 3000,
      totalReps: 1,
      countedReps: 1,
      invalidReps: 0,
      weightKg: null,
      weightUnit: 'kg',
      repMetrics: [
        {
          num: 1,
          durationMs: 2000,
          worstState: 0,
          score: 80,
          weightKg: null,
          metrics: {
            rom: 90,
            symmetry: null,
            stability: null,
            tempo: [1000, 0, 1000],
            velocity: null,
            formScore: 80,
            alignmentAccuracy: null,
          },
        },
      ],
      executionMetrics: {
        avgRom: 90,
        avgSymmetry: null,
        avgStability: null,
        avgTempo: [1000, 0, 1000],
        avgVelocity: null,
        avgFormScore: 80,
        avgAlignmentAccuracy: null,
        totalTUT: 2000,
        totalVolume: null,
        maxWeight: null,
        est1RM: null,
        relativeStrength: null,
        intensityPercentage: null,
        formConsistency: null,
        fatigueIndex: null,
      },
    };

    const response = await saveWorkoutExecution('user-1', payload, true);

    expect(metricsCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          avgStability: null,
          avgAlignmentAccuracy: null,
        }),
      }),
    );
    expect(repCreateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        data: [
          expect.objectContaining({
            stability: null,
            alignmentAccuracy: null,
          }),
        ],
      }),
    );
    expect(response.executionMetrics?.avgStability).toBeNull();
    expect(response.executionMetrics?.avgAlignmentAccuracy).toBeNull();
    expect(response.repMetrics[0]?.metrics.stability).toBeNull();
    expect(response.repMetrics[0]?.metrics.alignmentAccuracy).toBeNull();
  });
});
