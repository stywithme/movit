/**
 * P2.2 / PR-3: delta filters + includeReports + gated systemMessages/audioManifest.
 */

const exerciseFindMany = jest.fn();
const exerciseCount = jest.fn();
const workoutFindMany = jest.fn();
const workoutCount = jest.fn();
const programCount = jest.fn();
const userProgramFindMany = jest.fn();
const trainingProfileFindUnique = jest.fn();
const plannedWorkoutReportFindMany = jest.fn();
const feedbackAssignmentCount = jest.fn();
const feedbackAssignmentFindMany = jest.fn();
const feedbackAssignAggregate = jest.fn();
const feedbackTplAggregate = jest.fn();
const feedbackTplFindMany = jest.fn();

/** Controllable fingerprint inputs for getGlobalMessageStats. */
let fingerprintState = {
  linkedTplMax: null as Date | null,
  systemTplMax: null as Date | null,
  assignCreatedMax: null as Date | null,
  totalAssignments: 0,
  messageIds: [] as string[],
};

jest.mock('@/lib/prisma/client', () => ({
  getPrisma: async () => ({
    exercise: {
      findMany: (...args: unknown[]) => exerciseFindMany(...args),
      count: (...args: unknown[]) => exerciseCount(...args),
    },
    workoutTemplate: {
      findMany: (...args: unknown[]) => workoutFindMany(...args),
      count: (...args: unknown[]) => workoutCount(...args),
    },
    program: {
      count: (...args: unknown[]) => programCount(...args),
      findMany: jest.fn(async () => []),
    },
    userProgram: {
      findMany: (...args: unknown[]) => userProgramFindMany(...args),
    },
    trainingProfile: {
      findUnique: (...args: unknown[]) => trainingProfileFindUnique(...args),
    },
    plannedWorkoutReport: {
      findMany: (...args: unknown[]) => plannedWorkoutReportFindMany(...args),
    },
    feedbackMessageAssignment: {
      count: () => Promise.resolve(fingerprintState.totalAssignments),
      findMany: () =>
        Promise.resolve(fingerprintState.messageIds.map((messageId) => ({ messageId }))),
      aggregate: () =>
        Promise.resolve({ _max: { createdAt: fingerprintState.assignCreatedMax } }),
    },
    feedbackMessageTemplate: {
      aggregate: (args: { where?: { isSystem?: boolean } }) => {
        if (args?.where?.isSystem) {
          return Promise.resolve({ _max: { updatedAt: fingerprintState.systemTplMax } });
        }
        return Promise.resolve({ _max: { updatedAt: fingerprintState.linkedTplMax } });
      },
      findMany: (...args: unknown[]) => feedbackTplFindMany(...args),
    },
  }),
}));

jest.mock('@/modules/exercises/json-builder', () => ({
  exerciseFullInclude: {},
  buildExerciseConfig: () => ({ name: { en: 'x', ar: 'x' } }),
}));

jest.mock('@/modules/programs/programs.service', () => ({
  programService: {
    getPublishedForMobile: jest.fn(async () => []),
  },
}));

jest.mock('@/modules/workout-templates/workout-templates.service', () => ({
  workoutService: {
    buildWorkoutExport: jest.fn(() => null),
  },
}));

jest.mock('@/modules/user-exercise-preferences/user-exercise-preferences.service', () => ({
  listUserExercisePreferences: jest.fn(async () => []),
}));

const loadSystemMessages = jest.fn(async () => [
  { code: 'welcome', content: { en: 'Hi', ar: 'مرحبا' }, updatedAt: '2026-01-01T00:00:00.000Z' },
]);
const buildAudioManifest = jest.fn(async () => ({
  baseUrl: 'http://test/audio/tts',
  files: [{ filename: 'a.wav', url: '/audio/tts/a.wav', language: 'en' as const }],
}));

jest.mock('../mobile-audio-manifest.service', () => ({
  buildMessageLibrary: () => [],
  loadSystemMessages: (...args: unknown[]) => loadSystemMessages(...args),
  buildAudioManifest: (...args: unknown[]) => buildAudioManifest(...args),
}));

import { mobileSyncService } from '../mobile-sync.service';
import { programService } from '@/modules/programs/programs.service';

describe('mobile-sync delta payload (P2.2)', () => {
  const watermark = '2026-07-01T00:00:00.000Z';

  beforeEach(() => {
    jest.clearAllMocks();
    fingerprintState = {
      linkedTplMax: null,
      systemTplMax: null,
      assignCreatedMax: null,
      totalAssignments: 0,
      messageIds: [],
    };
    exerciseFindMany.mockResolvedValue([]);
    exerciseCount.mockResolvedValue(0);
    workoutFindMany.mockResolvedValue([]);
    workoutCount.mockResolvedValue(0);
    programCount.mockResolvedValue(0);
    userProgramFindMany.mockResolvedValue([]);
    trainingProfileFindUnique.mockResolvedValue({ trainingWeekdays: [] });
    plannedWorkoutReportFindMany.mockResolvedValue([]);
    feedbackTplFindMany.mockResolvedValue([]);
    (programService.getPublishedForMobile as jest.Mock).mockResolvedValue([]);
  });

  it('empty delta omits systemMessages load and audioManifest build', async () => {
    const res = await mobileSyncService.sync(
      { updatedAfter: watermark },
      'http://localhost',
      'user-1',
    );

    expect(res.meta.isFullSync).toBe(false);
    expect(res.data.systemMessages).toEqual([]);
    expect(res.data.audioManifest.files).toEqual([]);
    expect(loadSystemMessages).not.toHaveBeenCalled();
    expect(buildAudioManifest).not.toHaveBeenCalled();
  });

  it('loads systemMessages when systemTplMax is after watermark', async () => {
    fingerprintState.systemTplMax = new Date('2026-07-05T00:00:00.000Z');

    const res = await mobileSyncService.sync(
      { updatedAfter: watermark },
      'http://localhost',
      null,
    );

    expect(loadSystemMessages).toHaveBeenCalled();
    expect(res.data.systemMessages).toHaveLength(1);
    expect(buildAudioManifest).toHaveBeenCalled();
    expect(res.data.audioManifest.files).toHaveLength(1);
  });

  it('filters userPrograms and reports by updatedAfter; summary omits report', async () => {
    const reportUpdated = new Date('2026-07-08T00:00:00.000Z');
    plannedWorkoutReportFindMany.mockResolvedValue([
      {
        id: 'r1',
        plannedWorkoutId: 'pw1',
        programId: 'p1',
        weekNumber: 1,
        dayNumber: 1,
        startedAt: reportUpdated,
        completedAt: reportUpdated,
        status: 'completed',
        totalDurationMs: 1000,
        totalExercises: 1,
        totalSets: 1,
        completedSets: 1,
        totalReps: 10,
        avgAccuracy: 90,
        avgFormScore: 88,
        rpe: 7,
        report: { huge: true },
        updatedAt: reportUpdated,
      },
    ]);

    const res = await mobileSyncService.sync(
      { updatedAfter: watermark, includeReports: 'summary' },
      'http://localhost',
      'user-1',
    );

    expect(userProgramFindMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          userId: 'user-1',
          updatedAt: { gt: new Date(watermark) },
        },
      }),
    );
    expect(plannedWorkoutReportFindMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          userId: 'user-1',
          status: 'completed',
          updatedAt: { gt: new Date(watermark) },
        },
      }),
    );
    expect(res.data.plannedWorkoutReports).toHaveLength(1);
    expect(res.data.plannedWorkoutReports?.[0].report).toBeUndefined();
  });

  it('includeReports=none skips report query', async () => {
    const res = await mobileSyncService.sync(
      { updatedAfter: watermark, includeReports: 'none' },
      'http://localhost',
      'user-1',
    );

    expect(plannedWorkoutReportFindMany).not.toHaveBeenCalled();
    expect(res.data.plannedWorkoutReports).toBeUndefined();
  });

  it('watermark is at least max entity updatedAt', async () => {
    const entityAt = new Date('2026-07-10T15:00:00.000Z');
    userProgramFindMany.mockResolvedValue([
      {
        id: 'up1',
        programId: 'p1',
        name: null,
        startDate: entityAt,
        isActive: true,
        customizations: null,
        updatedAt: entityAt,
        customizationsUpdatedAt: null,
      },
    ]);

    const res = await mobileSyncService.sync(
      { updatedAfter: watermark, includeReports: 'none' },
      'http://localhost',
      'user-1',
    );

    expect(new Date(res.timestamp).getTime()).toBeGreaterThanOrEqual(entityAt.getTime());
  });

  it('B1-3: user-slice failure returns catalog with meta.userSlicesDegraded', async () => {
    plannedWorkoutReportFindMany.mockRejectedValue(
      Object.assign(new Error('P2022 column missing'), { code: 'P2022' }),
    );

    const res = await mobileSyncService.sync(
      { updatedAfter: watermark, includeReports: 'summary' },
      'http://localhost',
      'user-1',
    );

    expect(res.success).toBe(true);
    expect(res.data.exercises).toEqual([]);
    expect(res.data.plannedWorkoutReports).toBeUndefined();
    expect(res.data.userPrograms).toBeUndefined();
    expect(res.meta.userSlicesDegraded).toBe(true);
  });
});
