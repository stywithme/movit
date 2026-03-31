/**
 * Training Sessions Service
 * 
 * Handles saving and retrieving exercise session data
 * Uses separate tables for Session, SessionMetrics, and RepMetrics
 */

import { prisma } from '@/lib/prisma/client';
import { Prisma } from '@prisma/client';
import { intX10ToFloat } from '@/lib/metrics';
import { progressionService } from '@/modules/progression/progression.service';
import {
  SessionUploadPayload,
  ExploreSessionUploadPayload,
  ExploreSessionResponse,
  TrainingSessionResponse,
  ExerciseHistoryResponse,
  ExerciseHistoryItem,
  HistoryQueryParams,
  ProgressionData,
  RepMetricsData,
  SessionMetrics as SessionMetricsType,
  ProgramSessionStartPayload,
  ProgramSessionCompletePayload,
} from './training-sessions.types';

// ============================================
// Save Session
// ============================================

export async function saveSession(
  userId: string,
  payload: SessionUploadPayload,
  skipStatsUpdate = false,
): Promise<TrainingSessionResponse> {
  // Validate exercise exists (search by id OR slug)
  let exercise = await prisma.exercise.findUnique({
    where: { id: payload.exerciseId },
    select: { id: true, name: true, slug: true },
  });
  
  // If not found by id, try by slug
  if (!exercise) {
    exercise = await prisma.exercise.findUnique({
      where: { slug: payload.exerciseId },
      select: { id: true, name: true, slug: true },
    });
  }

  if (!exercise) {
    throw new Error(`Exercise not found: ${payload.exerciseId}`);
  }

  // Use transaction to ensure all data is saved together
  const result = await prisma.$transaction(async (tx) => {
    // 1. Create or update TrainingSession (use actual exercise.id, not slug)
    const session = await tx.trainingSession.upsert({
      where: { id: payload.id },
      create: {
        id: payload.id,
        userId,
        exerciseId: exercise.id,
        timestamp: new Date(payload.timestamp),
        durationMs: payload.durationMs,
        totalReps: payload.totalReps,
        countedReps: payload.countedReps,
        invalidReps: payload.invalidReps,
        weightKg: payload.weightKg,
        weightUnit: payload.weightUnit,
        context: payload.context ?? 'free',
        groupId: payload.groupId ?? null,
        workoutId: payload.workoutId ?? null,
        legacyReport: payload.legacyReport as any,
      },
      update: {
        durationMs: payload.durationMs,
        totalReps: payload.totalReps,
        countedReps: payload.countedReps,
        invalidReps: payload.invalidReps,
        weightKg: payload.weightKg,
        context: payload.context ?? 'free',
        groupId: payload.groupId ?? null,
        workoutId: payload.workoutId ?? null,
        legacyReport: payload.legacyReport as any,
      },
    });

    // 2. Delete existing metrics (for upsert behavior)
    await tx.sessionMetrics.deleteMany({ where: { sessionId: session.id } });
    await tx.repMetrics.deleteMany({ where: { sessionId: session.id } });

    // 3. Create SessionMetrics (if provided)
    if (payload.sessionMetrics) {
      const sm = payload.sessionMetrics;
      await tx.sessionMetrics.create({
        data: {
          sessionId: session.id,
          avgRom: sm.avgRom,
          avgSymmetry: sm.avgSymmetry,
          avgStability: sm.avgStability,
          avgVelocity: sm.avgVelocity,
          avgFormScore: sm.avgFormScore,
          avgAlignmentAccuracy: sm.avgAlignmentAccuracy,
          avgTempo: sm.avgTempo,
          totalTUT: sm.totalTUT,
          totalVolume: sm.totalVolume,
          maxWeight: sm.maxWeight,
          est1RM: sm.est1RM,
          relativeStrength: sm.relativeStrength,
          intensityPercentage: sm.intensityPercentage,
          formConsistency: sm.formConsistency,
          fatigueIndex: sm.fatigueIndex,
        },
      });
    }

    // 4. Create RepMetrics (batch insert)
    if (payload.repMetrics && payload.repMetrics.length > 0) {
      await tx.repMetrics.createMany({
        data: payload.repMetrics.map((rep) => ({
          sessionId: session.id,
          repNumber: rep.num,
          durationMs: rep.durationMs,
          worstState: rep.worstState,
          score: rep.score,
          weightKg: rep.weightKg,
          rom: rep.metrics.rom,
          symmetry: rep.metrics.symmetry,
          stability: rep.metrics.stability,
          velocity: rep.metrics.velocity,
          formScore: rep.metrics.formScore,
          alignmentAccuracy: rep.metrics.alignmentAccuracy,
          tempo: rep.metrics.tempo,
        })),
      });
    }

    return session;
  });

  // Update user stats (skipped for bulk saves — caller updates once at the end)
  if (!skipStatsUpdate) {
    await updateUserStats(userId);
  }

  console.log(`[Sessions] Saved session ${result.id}:`, {
    totalReps: result.totalReps,
    repMetricsSaved: payload.repMetrics?.length || 0,
  });

  // Fetch complete session for response
  return getSession(userId, result.id) as Promise<TrainingSessionResponse>;
}

// ============================================
// Save Explore Session (multi-exercise free session)
// ============================================

export async function saveExploreSession(
  userId: string,
  payload: ExploreSessionUploadPayload,
): Promise<ExploreSessionResponse> {
  const savedSessions: { id: string; exerciseId: string; totalReps: number }[] = [];

  // Save each exercise session without triggering a stats update per session.
  // A single stats update is issued after all sessions are persisted.
  for (const sessionPayload of payload.sessions) {
    const enriched: SessionUploadPayload = {
      ...sessionPayload,
      context: payload.context,
      groupId: payload.groupId,
      workoutId: payload.workoutId,
    };

    const saved = await saveSession(userId, enriched, /* skipStatsUpdate */ true);
    savedSessions.push({
      id: saved.id,
      exerciseId: saved.exerciseId,
      totalReps: saved.totalReps,
    });
  }

  // Single stats update after the full workout is saved
  if (savedSessions.length > 0) {
    await updateUserStats(userId);
  }

  return {
    groupId: payload.groupId,
    savedCount: savedSessions.length,
    sessions: savedSessions,
  };
}

// ============================================
// Get Session by ID
// ============================================

export async function getSession(
  userId: string,
  sessionId: string
): Promise<TrainingSessionResponse | null> {
  const session = await prisma.trainingSession.findFirst({
    where: {
      id: sessionId,
      userId,
    },
    include: {
      exercise: { select: { name: true } },
      sessionMetrics: true,
      repMetrics: { orderBy: { repNumber: 'asc' } },
    },
  });

  if (!session) return null;

  return mapSessionToResponse(session);
}

// ============================================
// Get Exercise History
// ============================================

export async function getExerciseHistory(
  userId: string,
  exerciseId: string,
  params: HistoryQueryParams = {}
): Promise<ExerciseHistoryResponse> {
  const { limit = 20, offset = 0, startDate, endDate } = params;

  // Get exercise info
  const exercise = await prisma.exercise.findUnique({
    where: { id: exerciseId },
    select: { id: true, name: true },
  });

  if (!exercise) {
    throw new Error(`Exercise not found: ${exerciseId}`);
  }

  // Build date filter
  const dateFilter: any = {};
  if (startDate) dateFilter.gte = new Date(startDate);
  if (endDate) dateFilter.lte = new Date(endDate);

  // Get sessions with metrics
  const sessions = await prisma.trainingSession.findMany({
    where: {
      userId,
      exerciseId,
      ...(Object.keys(dateFilter).length > 0 && { timestamp: dateFilter }),
    },
    orderBy: { timestamp: 'desc' },
    take: limit,
    skip: offset,
    include: {
      sessionMetrics: true,
    },
  });

  // Get total count
  const totalSessions = await prisma.trainingSession.count({
    where: {
      userId,
      exerciseId,
      ...(Object.keys(dateFilter).length > 0 && { timestamp: dateFilter }),
    },
  });

  // Map to history items
  const historyItems: ExerciseHistoryItem[] = sessions.map((s) => ({
    id: s.id,
    timestamp: s.timestamp.toISOString(),
    durationMs: s.durationMs,
    totalReps: s.totalReps,
    countedReps: s.countedReps,
    avgScore: s.sessionMetrics ? s.sessionMetrics.avgFormScore / 10 : 0,
    weightKg: s.weightKg,
    totalVolume: s.sessionMetrics?.totalVolume || null,
    est1RM: s.sessionMetrics?.est1RM || null,
  }));

  // Calculate aggregated stats
  const stats = await calculateExerciseStats(userId, exerciseId);

  return {
    exerciseId,
    exerciseName: exercise.name as { ar: string; en: string },
    totalSessions,
    sessions: historyItems,
    stats,
  };
}

// ============================================
// Get All History
// ============================================

export async function getAllHistory(
  userId: string,
  params: HistoryQueryParams = {}
): Promise<ExerciseHistoryItem[]> {
  const { limit = 50, offset = 0, startDate, endDate } = params;

  const dateFilter: any = {};
  if (startDate) dateFilter.gte = new Date(startDate);
  if (endDate) dateFilter.lte = new Date(endDate);

  const sessions = await prisma.trainingSession.findMany({
    where: {
      userId,
      ...(Object.keys(dateFilter).length > 0 && { timestamp: dateFilter }),
    },
    orderBy: { timestamp: 'desc' },
    take: limit,
    skip: offset,
    include: {
      exercise: { select: { name: true } },
      sessionMetrics: true,
    },
  });

  return sessions.map((s) => ({
    id: s.id,
    timestamp: s.timestamp.toISOString(),
    durationMs: s.durationMs,
    totalReps: s.totalReps,
    countedReps: s.countedReps,
    avgScore: s.sessionMetrics ? s.sessionMetrics.avgFormScore / 10 : 0,
    weightKg: s.weightKg,
    totalVolume: s.sessionMetrics?.totalVolume || null,
    est1RM: s.sessionMetrics?.est1RM || null,
  }));
}

// ============================================
// Get Session Details (with all rep metrics)
// ============================================

export async function getSessionDetails(
  userId: string,
  sessionId: string
): Promise<TrainingSessionResponse | null> {
  return getSession(userId, sessionId);
}

// ============================================
// Delete Session
// ============================================

export async function deleteSession(
  userId: string,
  sessionId: string
): Promise<boolean> {
  // Cascade will delete related metrics
  const result = await prisma.trainingSession.deleteMany({
    where: {
      id: sessionId,
      userId,
    },
  });

  if (result.count > 0) {
    await updateUserStats(userId);
  }

  return result.count > 0;
}

// ============================================
// Program Session Reports
// ============================================

type JsonRecord = Record<string, unknown>;

type StateBreakdownCounts = {
  perfect: number;
  normal: number;
  pad: number;
  warning: number;
  danger: number;
};

type CountingSnapshot = {
  totalReps: number;
  countedReps: number;
  invalidatedReps: number;
  uncountedReps: number;
  incorrectReps: number;
  countedRatio: number;
  invalidatedRatio: number;
  uncountedRatio: number;
  stateBreakdown: StateBreakdownCounts;
  positionErrorReps: number;
  positionWarningReps: number;
  positionTipReps: number;
};

function isRecord(value: unknown): value is JsonRecord {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function toFiniteNumber(value: unknown): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  return value;
}

function toNonNegativeInt(value: unknown): number | null {
  const num = toFiniteNumber(value);
  if (num == null) return null;
  return Math.max(0, Math.round(num));
}

function normalizePercent(value: unknown): number | null {
  const num = toFiniteNumber(value);
  if (num == null) return null;
  const normalized = num >= 0 && num <= 1 ? num * 100 : num;
  return Math.max(0, Math.min(100, normalized));
}

function ratioPercent(numerator: number, denominator: number): number {
  if (denominator <= 0) return 0;
  return Math.max(0, Math.min(100, (numerator / denominator) * 100));
}

function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

function emptyStateBreakdown(): StateBreakdownCounts {
  return { perfect: 0, normal: 0, pad: 0, warning: 0, danger: 0 };
}

function mergeStateBreakdown(a: StateBreakdownCounts, b: StateBreakdownCounts): StateBreakdownCounts {
  return {
    perfect: a.perfect + b.perfect,
    normal: a.normal + b.normal,
    pad: a.pad + b.pad,
    warning: a.warning + b.warning,
    danger: a.danger + b.danger,
  };
}

function normalizeStateKey(raw: unknown): keyof StateBreakdownCounts {
  const state = toNonNegativeInt(raw) ?? 0;
  if (state <= 0) return 'perfect';
  if (state === 1) return 'normal';
  if (state === 2) return 'pad';
  if (state === 3) return 'warning';
  return 'danger';
}

function normalizePositionCount(value: unknown): number {
  if (Array.isArray(value)) return value.length;
  return toNonNegativeInt(value) ?? 0;
}

function getRepPositionCount(
  rep: JsonRecord,
  explicitKey: string,
  arrayKey: string,
): number {
  const explicit = normalizePositionCount(rep[explicitKey]);
  if (explicit > 0) return explicit;
  return normalizePositionCount(rep[arrayKey]);
}

function getRepIsInvalidated(rep: JsonRecord): boolean {
  if (typeof rep.isInvalidated === 'boolean') return rep.isInvalidated;
  return (toNonNegativeInt(rep.worstState) ?? 0) >= 4;
}

function normalizeSnapshot(raw: {
  totalReps: number;
  countedReps: number;
  invalidatedReps: number;
  uncountedReps: number;
  incorrectReps: number;
  countedRatio?: number | null;
  invalidatedRatio?: number | null;
  uncountedRatio?: number | null;
  stateBreakdown?: StateBreakdownCounts;
  positionErrorReps?: number;
  positionWarningReps?: number;
  positionTipReps?: number;
}): CountingSnapshot {
  const totalReps = Math.max(0, Math.round(raw.totalReps));
  let countedReps = Math.min(totalReps, Math.max(0, Math.round(raw.countedReps)));
  let incorrectReps = Math.min(totalReps, Math.max(0, Math.round(raw.incorrectReps)));
  let invalidatedReps = Math.min(totalReps, Math.max(0, Math.round(raw.invalidatedReps)));
  if (invalidatedReps > incorrectReps) invalidatedReps = incorrectReps;
  let uncountedReps = Math.min(totalReps, Math.max(0, Math.round(raw.uncountedReps)));
  if (uncountedReps + invalidatedReps > incorrectReps) {
    uncountedReps = Math.max(0, incorrectReps - invalidatedReps);
  }

  if (countedReps + incorrectReps > totalReps) {
    incorrectReps = Math.max(0, totalReps - countedReps);
    if (invalidatedReps > incorrectReps) invalidatedReps = incorrectReps;
    if (uncountedReps > incorrectReps - invalidatedReps) {
      uncountedReps = Math.max(0, incorrectReps - invalidatedReps);
    }
  }

  const countedRatio =
    normalizePercent(raw.countedRatio) ?? ratioPercent(countedReps, totalReps);
  const invalidatedRatio =
    normalizePercent(raw.invalidatedRatio) ?? ratioPercent(invalidatedReps, totalReps);
  const uncountedRatio =
    normalizePercent(raw.uncountedRatio) ?? ratioPercent(uncountedReps, totalReps);

  return {
    totalReps,
    countedReps,
    invalidatedReps,
    uncountedReps,
    incorrectReps,
    countedRatio,
    invalidatedRatio,
    uncountedRatio,
    stateBreakdown: raw.stateBreakdown ?? emptyStateBreakdown(),
    positionErrorReps: Math.max(0, Math.round(raw.positionErrorReps ?? 0)),
    positionWarningReps: Math.max(0, Math.round(raw.positionWarningReps ?? 0)),
    positionTipReps: Math.max(0, Math.round(raw.positionTipReps ?? 0)),
  };
}

function parseStateBreakdown(source: unknown): StateBreakdownCounts {
  if (!isRecord(source)) return emptyStateBreakdown();
  return {
    perfect: toNonNegativeInt(source.perfect) ?? 0,
    normal: toNonNegativeInt(source.normal) ?? 0,
    pad: toNonNegativeInt(source.pad) ?? 0,
    warning: toNonNegativeInt(source.warning) ?? 0,
    danger: toNonNegativeInt(source.danger) ?? 0,
  };
}

function snapshotFromRepDetails(repDetails: JsonRecord[]): CountingSnapshot {
  let totalReps = 0;
  let countedReps = 0;
  let invalidatedReps = 0;
  let positionErrorReps = 0;
  let positionWarningReps = 0;
  let positionTipReps = 0;
  const stateBreakdown = emptyStateBreakdown();

  for (const rep of repDetails) {
    totalReps += 1;
    if (rep.isCounted === true) countedReps += 1;

    if (getRepIsInvalidated(rep)) {
      invalidatedReps += 1;
      rep.isInvalidated = true;
    }

    const stateKey = normalizeStateKey(rep.worstState);
    stateBreakdown[stateKey] += 1;

    const positionErrorCount = getRepPositionCount(rep, 'positionErrorCount', 'positionErrors');
    const positionWarningCount = getRepPositionCount(rep, 'positionWarningCount', 'positionWarnings');
    const positionTipCount = getRepPositionCount(rep, 'positionTipCount', 'errors');

    rep.positionErrorCount = positionErrorCount;
    rep.positionWarningCount = positionWarningCount;
    rep.positionTipCount = positionTipCount;

    if (positionErrorCount > 0) positionErrorReps += 1;
    if (positionWarningCount > 0) positionWarningReps += 1;
    if (positionTipCount > 0) positionTipReps += 1;
  }

  return normalizeSnapshot({
    totalReps,
    countedReps,
    invalidatedReps,
    incorrectReps: Math.max(0, totalReps - countedReps),
    uncountedReps: Math.max(0, totalReps - countedReps - invalidatedReps),
    stateBreakdown,
    positionErrorReps,
    positionWarningReps,
    positionTipReps,
  });
}

function snapshotFromFields(
  source: JsonRecord,
  fallback?: Partial<CountingSnapshot>,
): CountingSnapshot {
  const totalReps =
    toNonNegativeInt(source.totalReps) ??
    toNonNegativeInt(source.repsCompleted) ??
    fallback?.totalReps ??
    0;

  const countedReps =
    toNonNegativeInt(source.countedReps) ?? fallback?.countedReps ?? 0;
  const incorrectReps =
    toNonNegativeInt(source.incorrectReps) ?? Math.max(0, totalReps - countedReps);
  const invalidatedReps =
    toNonNegativeInt(source.invalidatedReps) ?? fallback?.invalidatedReps ?? 0;
  const uncountedReps =
    toNonNegativeInt(source.uncountedReps) ?? Math.max(0, incorrectReps - invalidatedReps);

  return normalizeSnapshot({
    totalReps,
    countedReps,
    invalidatedReps,
    incorrectReps,
    uncountedReps,
    countedRatio: normalizePercent(source.countedRatio) ?? normalizePercent(source.accuracy),
    invalidatedRatio: normalizePercent(source.invalidatedRatio),
    uncountedRatio: normalizePercent(source.uncountedRatio),
    stateBreakdown: parseStateBreakdown(source.stateBreakdown),
    positionErrorReps:
      toNonNegativeInt(source.positionErrorReps) ?? fallback?.positionErrorReps ?? 0,
    positionWarningReps:
      toNonNegativeInt(source.positionWarningReps) ?? fallback?.positionWarningReps ?? 0,
    positionTipReps:
      toNonNegativeInt(source.positionTipReps) ?? fallback?.positionTipReps ?? 0,
  });
}

function aggregateSnapshots(snapshots: CountingSnapshot[]): CountingSnapshot {
  if (snapshots.length === 0) {
    return normalizeSnapshot({
      totalReps: 0,
      countedReps: 0,
      invalidatedReps: 0,
      uncountedReps: 0,
      incorrectReps: 0,
      stateBreakdown: emptyStateBreakdown(),
    });
  }

  const total = snapshots.reduce((sum, s) => sum + s.totalReps, 0);
  const counted = snapshots.reduce((sum, s) => sum + s.countedReps, 0);
  const invalidated = snapshots.reduce((sum, s) => sum + s.invalidatedReps, 0);
  const uncounted = snapshots.reduce((sum, s) => sum + s.uncountedReps, 0);
  const incorrect = snapshots.reduce((sum, s) => sum + s.incorrectReps, 0);
  const positionErrorReps = snapshots.reduce((sum, s) => sum + s.positionErrorReps, 0);
  const positionWarningReps = snapshots.reduce((sum, s) => sum + s.positionWarningReps, 0);
  const positionTipReps = snapshots.reduce((sum, s) => sum + s.positionTipReps, 0);
  const stateBreakdown = snapshots.reduce(
    (acc, s) => mergeStateBreakdown(acc, s.stateBreakdown),
    emptyStateBreakdown(),
  );

  return normalizeSnapshot({
    totalReps: total,
    countedReps: counted,
    invalidatedReps: invalidated,
    uncountedReps: uncounted,
    incorrectReps: incorrect,
    stateBreakdown,
    positionErrorReps,
    positionWarningReps,
    positionTipReps,
  });
}

function normalizeSetReport(set: JsonRecord): { set: JsonRecord; snapshot: CountingSnapshot; formScore: number } {
  const repDetails = Array.isArray(set.repDetails)
    ? set.repDetails.filter(isRecord).map((rep) => ({ ...rep }))
    : [];

  const fromReps = snapshotFromRepDetails(repDetails);
  const fromFields = snapshotFromFields(
    {
      ...set,
      totalReps: toNonNegativeInt(set.totalReps) ?? toNonNegativeInt(set.repsCompleted),
      accuracy: normalizePercent(set.accuracy),
    },
    fromReps,
  );

  const snapshot = normalizeSnapshot({
    ...fromFields,
    stateBreakdown: repDetails.length > 0 ? fromReps.stateBreakdown : fromFields.stateBreakdown,
    positionErrorReps: repDetails.length > 0 ? fromReps.positionErrorReps : fromFields.positionErrorReps,
    positionWarningReps: repDetails.length > 0 ? fromReps.positionWarningReps : fromFields.positionWarningReps,
    positionTipReps: repDetails.length > 0 ? fromReps.positionTipReps : fromFields.positionTipReps,
  });

  const normalizedSet: JsonRecord = {
    ...set,
    repDetails,
    totalReps: snapshot.totalReps,
    repsCompleted: snapshot.totalReps,
    countedReps: snapshot.countedReps,
    invalidatedReps: snapshot.invalidatedReps,
    uncountedReps: snapshot.uncountedReps,
    incorrectReps: snapshot.incorrectReps,
    countedRatio: round1(snapshot.countedRatio),
    accuracy: round1(snapshot.countedRatio),
    invalidatedRatio: round1(snapshot.invalidatedRatio),
    uncountedRatio: round1(snapshot.uncountedRatio),
    stateBreakdown: snapshot.stateBreakdown,
    positionErrorReps: snapshot.positionErrorReps,
    positionWarningReps: snapshot.positionWarningReps,
    positionTipReps: snapshot.positionTipReps,
  };

  const formScore = toFiniteNumber(set.formScore) ?? 0;
  return { set: normalizedSet, snapshot, formScore };
}

function normalizeExerciseReport(exercise: JsonRecord): {
  exercise: JsonRecord;
  snapshot: CountingSnapshot;
  averageFormScore: number;
} {
  const rawSets = Array.isArray(exercise.setMetrics)
    ? exercise.setMetrics.filter(isRecord).map((set) => ({ ...set }))
    : [];

  const normalizedSets = rawSets.map((set) => normalizeSetReport(set));
  const setSnapshots = normalizedSets.map((entry) => entry.snapshot);
  const aggregated = aggregateSnapshots(setSnapshots);
  const fallback = snapshotFromFields(exercise, aggregated);
  const snapshot = normalizeSnapshot({
    ...fallback,
    stateBreakdown: setSnapshots.length > 0 ? aggregated.stateBreakdown : fallback.stateBreakdown,
    positionErrorReps: setSnapshots.length > 0 ? aggregated.positionErrorReps : fallback.positionErrorReps,
    positionWarningReps: setSnapshots.length > 0 ? aggregated.positionWarningReps : fallback.positionWarningReps,
    positionTipReps: setSnapshots.length > 0 ? aggregated.positionTipReps : fallback.positionTipReps,
  });

  const averageFormScore =
    toFiniteNumber(exercise.averageFormScore) ??
    (normalizedSets.length > 0
      ? normalizedSets.reduce((sum, entry) => sum + entry.formScore, 0) / normalizedSets.length
      : 0);

  const normalizedExercise: JsonRecord = {
    ...exercise,
    setMetrics: normalizedSets.map((entry) => entry.set),
    setsCompleted: toNonNegativeInt(exercise.setsCompleted) ?? normalizedSets.length,
    totalSets: toNonNegativeInt(exercise.totalSets) ?? normalizedSets.length,
    totalReps: snapshot.totalReps,
    countedReps: snapshot.countedReps,
    invalidatedReps: snapshot.invalidatedReps,
    uncountedReps: snapshot.uncountedReps,
    incorrectReps: snapshot.incorrectReps,
    countedRatio: round1(snapshot.countedRatio),
    averageAccuracy: round1(snapshot.countedRatio),
    invalidatedRatio: round1(snapshot.invalidatedRatio),
    uncountedRatio: round1(snapshot.uncountedRatio),
    averageFormScore: round1(averageFormScore),
    stateBreakdown: snapshot.stateBreakdown,
    positionErrorReps: snapshot.positionErrorReps,
    positionWarningReps: snapshot.positionWarningReps,
    positionTipReps: snapshot.positionTipReps,
  };

  return {
    exercise: normalizedExercise,
    snapshot,
    averageFormScore,
  };
}

function normalizeSessionReport(report: JsonRecord): {
  normalizedReport: JsonRecord;
  snapshot: CountingSnapshot;
  averageFormScore: number;
  totalExercises: number;
  totalSetsPlanned: number;
  totalSetsCompleted: number;
  totalDurationMs: number | null;
} {
  const rawExercises = Array.isArray(report.exerciseReports)
    ? report.exerciseReports.filter(isRecord).map((ex) => ({ ...ex }))
    : [];

  const normalizedExercises = rawExercises.map((ex) => normalizeExerciseReport(ex));
  const exerciseSnapshots = normalizedExercises.map((entry) => entry.snapshot);
  const aggregated = aggregateSnapshots(exerciseSnapshots);
  const fallback = snapshotFromFields(report, aggregated);
  const snapshot = normalizeSnapshot({
    ...fallback,
    stateBreakdown: exerciseSnapshots.length > 0 ? aggregated.stateBreakdown : fallback.stateBreakdown,
    positionErrorReps: exerciseSnapshots.length > 0 ? aggregated.positionErrorReps : fallback.positionErrorReps,
    positionWarningReps: exerciseSnapshots.length > 0 ? aggregated.positionWarningReps : fallback.positionWarningReps,
    positionTipReps: exerciseSnapshots.length > 0 ? aggregated.positionTipReps : fallback.positionTipReps,
  });

  const averageFormScore =
    toFiniteNumber(report.averageFormScore) ??
    (normalizedExercises.length > 0
      ? normalizedExercises.reduce((sum, entry) => sum + entry.averageFormScore, 0) /
        normalizedExercises.length
      : 0);

  const totalExercises = toNonNegativeInt(report.totalExercises) ?? normalizedExercises.length;
  const totalSetsCompleted =
    toNonNegativeInt(report.totalSetsCompleted) ??
    normalizedExercises.reduce((sum, ex) => sum + (toNonNegativeInt(ex.exercise.setsCompleted) ?? 0), 0);
  const totalSetsPlanned =
    toNonNegativeInt(report.totalSetsPlanned) ??
    normalizedExercises.reduce((sum, ex) => sum + (toNonNegativeInt(ex.exercise.totalSets) ?? 0), 0);
  const totalDurationMs = toNonNegativeInt(report.totalDurationMs);

  const normalizedReport: JsonRecord = {
    ...report,
    exerciseReports: normalizedExercises.map((entry) => entry.exercise),
    totalExercises,
    totalSetsCompleted,
    totalSetsPlanned,
    totalReps: snapshot.totalReps,
    countedReps: snapshot.countedReps,
    invalidatedReps: snapshot.invalidatedReps,
    uncountedReps: snapshot.uncountedReps,
    incorrectReps: snapshot.incorrectReps,
    countedRatio: round1(snapshot.countedRatio),
    averageAccuracy: round1(snapshot.countedRatio),
    invalidatedRatio: round1(snapshot.invalidatedRatio),
    uncountedRatio: round1(snapshot.uncountedRatio),
    averageFormScore: round1(averageFormScore),
    stateBreakdown: snapshot.stateBreakdown,
    positionErrorReps: snapshot.positionErrorReps,
    positionWarningReps: snapshot.positionWarningReps,
    positionTipReps: snapshot.positionTipReps,
  };

  return {
    normalizedReport,
    snapshot,
    averageFormScore,
    totalExercises,
    totalSetsPlanned,
    totalSetsCompleted,
    totalDurationMs,
  };
}

function normalizeProgramSessionReportPayload(
  payload: ProgramSessionCompletePayload,
): {
  report?: Prisma.InputJsonValue;
  totalDurationMs?: number;
  totalExercises?: number;
  totalSets?: number;
  completedSets?: number;
  totalReps?: number;
  avgAccuracy?: number;
  avgFormScore?: number;
} {
  const source = isRecord(payload.report) ? payload.report : null;

  let normalizedReportResult:
    | ReturnType<typeof normalizeSessionReport>
    | null = null;

  if (source) {
    // Report payload is expected to be plain JSON; cloning avoids mutating caller objects.
    const cloned = JSON.parse(JSON.stringify(source)) as JsonRecord;
    normalizedReportResult = normalizeSessionReport(cloned);
  }

  const totalDurationMs =
    toNonNegativeInt(normalizedReportResult?.totalDurationMs) ?? toNonNegativeInt(payload.totalDurationMs) ?? undefined;
  const totalExercises =
    toNonNegativeInt(normalizedReportResult?.totalExercises) ?? toNonNegativeInt(payload.totalExercises) ?? undefined;
  const totalSets =
    toNonNegativeInt(normalizedReportResult?.totalSetsPlanned) ?? toNonNegativeInt(payload.totalSets) ?? undefined;
  const completedSets =
    toNonNegativeInt(normalizedReportResult?.totalSetsCompleted) ?? toNonNegativeInt(payload.completedSets) ?? undefined;
  const totalReps =
    toNonNegativeInt(normalizedReportResult?.snapshot.totalReps) ?? toNonNegativeInt(payload.totalReps) ?? undefined;
  const avgAccuracy =
    normalizePercent(normalizedReportResult?.snapshot.countedRatio) ?? normalizePercent(payload.avgAccuracy) ?? undefined;
  const avgFormScore =
    toFiniteNumber(normalizedReportResult?.averageFormScore) ?? toFiniteNumber(payload.avgFormScore) ?? undefined;

  return {
    report: normalizedReportResult
      ? (normalizedReportResult.normalizedReport as Prisma.InputJsonValue)
      : undefined,
    totalDurationMs,
    totalExercises,
    totalSets,
    completedSets,
    totalReps,
    avgAccuracy,
    avgFormScore,
  };
}
export async function startProgramSessionReport(
  userId: string,
  sessionId: string,
  payload: ProgramSessionStartPayload
) {
  const session = await prisma.programSession.findFirst({
    where: { id: sessionId },
    include: { day: { include: { week: true } } },
  });

  if (!session) {
    throw new Error('Program session not found');
  }

  const weekNumber = session.day.week.weekNumber;
  const dayNumber = session.day.dayNumber;

  if (payload.weekNumber !== weekNumber || payload.dayNumber !== dayNumber) {
    throw new Error('Invalid week/day for session');
  }

  return prisma.programSessionReport.create({
    data: {
      userId,
      programId: session.day.week.programId,
      programSessionId: session.id,
      weekNumber,
      dayNumber,
      startedAt: payload.startedAt ? new Date(payload.startedAt) : new Date(),
      status: 'in_progress',
    },
  });
}

export async function completeProgramSessionReport(
  userId: string,
  sessionId: string,
  payload: ProgramSessionCompletePayload
) {
  // Find the active report (or the most recent one for this session)
  let report = await prisma.programSessionReport.findFirst({
    where: {
      userId,
      programSessionId: sessionId,
      status: 'in_progress',
    },
    orderBy: { createdAt: 'desc' },
  });

  // If no in_progress report, create one on the fly (supports offline-first: mobile may
  // call /complete without having called /start if it was offline when training began)
  if (!report) {
    const session = await prisma.programSession.findFirst({
      where: { id: sessionId },
      include: { day: { include: { week: true } } },
    });

    if (!session) {
      throw new Error('Program session not found');
    }

    report = await prisma.programSessionReport.create({
      data: {
        userId,
        programId: session.day.week.programId,
        programSessionId: session.id,
        weekNumber: session.day.week.weekNumber,
        dayNumber: session.day.dayNumber,
        startedAt: new Date(),
        status: 'in_progress',
      },
    });
  }

  const completedAt = payload.completedAt ? new Date(payload.completedAt) : new Date();
  const normalizedPayload = normalizeProgramSessionReportPayload(payload);

  // Update the report to completed
  const updatedReport = await prisma.programSessionReport.update({
    where: { id: report.id },
    data: {
      status: 'completed',
      completedAt,
      totalDurationMs: normalizedPayload.totalDurationMs ?? report.totalDurationMs ?? undefined,
      totalExercises: normalizedPayload.totalExercises ?? report.totalExercises ?? undefined,
      totalSets: normalizedPayload.totalSets ?? report.totalSets ?? undefined,
      completedSets: normalizedPayload.completedSets ?? report.completedSets ?? undefined,
      totalReps: normalizedPayload.totalReps ?? report.totalReps ?? undefined,
      avgAccuracy: normalizedPayload.avgAccuracy ?? report.avgAccuracy ?? undefined,
      avgFormScore: normalizedPayload.avgFormScore ?? report.avgFormScore ?? undefined,
      report: normalizedPayload.report ?? (report.report as Prisma.InputJsonValue | undefined),
    },
  });

  // ── Activate UserProgramProgress ──
  // Find the user's active program to update progress tracking
  try {
    const userProgram = await prisma.userProgram.findFirst({
      where: { userId, programId: report.programId ?? undefined, isActive: true },
    });

    if (userProgram) {
      await prisma.userProgramProgress.upsert({
        where: {
          userProgramId_weekNumber_dayNumber_sessionId: {
            userProgramId: userProgram.id,
            weekNumber: report.weekNumber,
            dayNumber: report.dayNumber,
            sessionId,
          },
        },
        create: {
          userProgramId: userProgram.id,
          weekNumber: report.weekNumber,
          dayNumber: report.dayNumber,
          sessionId,
          completedAt,
          status: 'completed',
        },
        update: {
          completedAt,
          status: 'completed',
        },
      });

      // Check if ALL sessions for this day are completed → mark day as completed
      const daySession = await prisma.programSession.findFirst({
        where: { id: sessionId },
        include: {
          day: {
            include: {
              sessions: { select: { id: true } },
            },
          },
        },
      });

      if (daySession) {
        const allSessionIds = daySession.day.sessions.map((s) => s.id);
        const completedReports = await prisma.programSessionReport.findMany({
          where: {
            userId,
            programSessionId: { in: allSessionIds },
            status: 'completed',
          },
          select: { programSessionId: true },
        });
        const completedSessionIds = new Set(completedReports.map((r) => r.programSessionId));

        if (allSessionIds.every((id) => completedSessionIds.has(id))) {
          // All sessions in this day are done — upsert day-level progress
          await prisma.userProgramProgress.upsert({
            where: {
              userProgramId_weekNumber_dayNumber_sessionId: {
                userProgramId: userProgram.id,
                weekNumber: report.weekNumber,
                dayNumber: report.dayNumber,
                sessionId: '__day__', // sentinel for day-level completion
              },
            },
            create: {
              userProgramId: userProgram.id,
              weekNumber: report.weekNumber,
              dayNumber: report.dayNumber,
              sessionId: '__day__',
              completedAt,
              status: 'completed',
            },
            update: {
              completedAt,
              status: 'completed',
            },
          });
        }
      }
    }
  } catch (progressError) {
    // Don't fail the main report update if progress tracking fails
    console.warn('[Sessions] Failed to update program progress:', progressError);
  }

  // ── Progression Engine — evaluate rules after session completion ──
  // Only run progression for program sessions
  if (report.programId) {
    try {
      const programId = report.programId;
      const allChanges: Awaited<ReturnType<typeof progressionService.evaluateAfterSession>> = [];

      // 1) Global + program-scoped rules (once, no exerciseId)
      const globalChanges = await progressionService.evaluateAfterSession(
        userId, sessionId, { scope: 'global_and_program', programId },
      );
      allChanges.push(...globalChanges);

      // 2) Exercise-specific rules (per distinct exercise in this session)
      const sessionItems = await prisma.programSessionItem.findMany({
        where: { session: { id: sessionId }, type: 'exercise' },
        select: { exerciseId: true },
        distinct: ['exerciseId'],
      });
      const exerciseIds = sessionItems.map((i) => i.exerciseId).filter(Boolean) as string[];

      for (const exId of exerciseIds) {
        const exChanges = await progressionService.evaluateAfterSession(
          userId, sessionId, { scope: 'exercise', exerciseId: exId, programId },
        );
        allChanges.push(...exChanges);
      }

      if (allChanges.length > 0) {
        console.log(`[Progression] ${allChanges.length} change(s) applied for user ${userId}:`,
          allChanges.map((c) => `${c.field}: ${c.previousValue} → ${c.newValue}`).join(', '));
      }
    } catch (progressionError) {
      console.warn('[Sessions] Progression engine error (non-fatal):', progressionError);
    }
  }

  return updatedReport;
}

export async function updateProgramSessionReport(
  userId: string,
  sessionId: string,
  payload: ProgramSessionCompletePayload
) {
  const report = await prisma.programSessionReport.findFirst({
    where: {
      userId,
      programSessionId: sessionId,
    },
    orderBy: { createdAt: 'desc' },
  });

  if (!report) {
    throw new Error('Report not found');
  }

  const normalizedPayload = normalizeProgramSessionReportPayload(payload);

  return prisma.programSessionReport.update({
    where: { id: report.id },
    data: {
      totalDurationMs: normalizedPayload.totalDurationMs ?? report.totalDurationMs ?? undefined,
      totalExercises: normalizedPayload.totalExercises ?? report.totalExercises ?? undefined,
      totalSets: normalizedPayload.totalSets ?? report.totalSets ?? undefined,
      completedSets: normalizedPayload.completedSets ?? report.completedSets ?? undefined,
      totalReps: normalizedPayload.totalReps ?? report.totalReps ?? undefined,
      avgAccuracy: normalizedPayload.avgAccuracy ?? report.avgAccuracy ?? undefined,
      avgFormScore: normalizedPayload.avgFormScore ?? report.avgFormScore ?? undefined,
      report: normalizedPayload.report ?? (report.report as Prisma.InputJsonValue | undefined),
    },
  });
}

// ============================================
// User Home Stats
// ============================================

export async function getUserHomeStats(userId: string) {
  const now = new Date();

  // Start of current week (Monday)
  const startOfWeek = new Date(now);
  startOfWeek.setDate(now.getDate() - ((now.getDay() + 6) % 7));
  startOfWeek.setHours(0, 0, 0, 0);

  // Weekly session count (completed program session reports this week)
  const weeklySessionCount = await prisma.programSessionReport.count({
    where: {
      userId,
      status: 'completed',
      completedAt: { gte: startOfWeek },
    },
  });

  // Average form score from last 5 completed sessions
  const recentReports = await prisma.programSessionReport.findMany({
    where: { userId, status: 'completed', avgFormScore: { not: null } },
    orderBy: { completedAt: 'desc' },
    take: 5,
    select: { avgFormScore: true },
  });

  let avgFormScore = 0;
  if (recentReports.length > 0) {
    const total = recentReports.reduce((sum, r) => sum + (r.avgFormScore ?? 0), 0);
    avgFormScore = Math.round((total / recentReports.length) * 10) / 10;
  }

  // Training streak: consecutive days with completed sessions (backwards from today)
  const allCompletedDates = await prisma.programSessionReport.findMany({
    where: { userId, status: 'completed', completedAt: { not: null } },
    orderBy: { completedAt: 'desc' },
    select: { completedAt: true },
  });

  let streak = 0;
  if (allCompletedDates.length > 0) {
    const trainedDays = new Set<string>();
    for (const r of allCompletedDates) {
      if (r.completedAt) {
        trainedDays.add(r.completedAt.toISOString().split('T')[0]);
      }
    }

    const today = now.toISOString().split('T')[0];
    const yesterday = new Date(now.getTime() - 86400000).toISOString().split('T')[0];

    // Start counting from today or yesterday
    let checkDate = trainedDays.has(today) ? now : new Date(now.getTime() - 86400000);
    if (!trainedDays.has(today) && !trainedDays.has(yesterday)) {
      streak = 0;
    } else {
      for (let i = 0; i < 365; i++) {
        const dateStr = checkDate.toISOString().split('T')[0];
        if (trainedDays.has(dateStr)) {
          streak++;
          checkDate = new Date(checkDate.getTime() - 86400000);
        } else {
          break;
        }
      }
    }
  }

  // Total lifetime stats from User record
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { totalWorkouts: true, totalMinutes: true },
  });

  // Latest assessment info
  const latestAssessment = await prisma.bodyScanResult.findFirst({
    where: { userId },
    orderBy: { completedAt: 'desc' },
    select: { bodyScore: true, fitnessLevel: true, completedAt: true },
  });

  return {
    weeklyWorkouts: weeklySessionCount,
    avgFormScore,
    streak,
    totalWorkouts: user?.totalWorkouts ?? 0,
    totalMinutes: user?.totalMinutes ?? 0,
    latestAssessment: latestAssessment
      ? {
          bodyScore: latestAssessment.bodyScore,
          fitnessLevel: latestAssessment.fitnessLevel,
          completedAt: latestAssessment.completedAt.toISOString(),
        }
      : null,
  };
}

// ============================================
// Helper Functions
// ============================================

function mapSessionToResponse(session: any): TrainingSessionResponse {
  const sm = session.sessionMetrics;
  
  return {
    id: session.id,
    exerciseId: session.exerciseId,
    exerciseName: session.exercise?.name as { ar: string; en: string },
    timestamp: session.timestamp.toISOString(),
    durationMs: session.durationMs,
    totalReps: session.totalReps,
    countedReps: session.countedReps,
    invalidReps: session.invalidReps,
    weightKg: session.weightKg,
    weightUnit: session.weightUnit,
    sessionMetrics: sm ? {
      avgRom: sm.avgRom,
      avgSymmetry: sm.avgSymmetry,
      avgStability: sm.avgStability,
      avgTempo: sm.avgTempo as number[],
      avgVelocity: sm.avgVelocity,
      avgFormScore: sm.avgFormScore,
      avgAlignmentAccuracy: sm.avgAlignmentAccuracy,
      totalTUT: sm.totalTUT,
      totalVolume: sm.totalVolume,
      maxWeight: sm.maxWeight,
      est1RM: sm.est1RM,
      relativeStrength: sm.relativeStrength,
      intensityPercentage: sm.intensityPercentage,
      formConsistency: sm.formConsistency,
      fatigueIndex: sm.fatigueIndex,
    } : null,
    repMetrics: session.repMetrics?.map((rep: any) => ({
      num: rep.repNumber,
      durationMs: rep.durationMs,
      worstState: rep.worstState,
      score: rep.score,
      weightKg: rep.weightKg,
      metrics: {
        rom: rep.rom,
        symmetry: rep.symmetry,
        stability: rep.stability,
        tempo: rep.tempo as number[],
        velocity: rep.velocity,
        formScore: rep.formScore,
        alignmentAccuracy: rep.alignmentAccuracy,
      },
    })) || [],
  };
}

async function calculateExerciseStats(userId: string, exerciseId: string) {
  // Get all sessions with metrics
  const sessions = await prisma.trainingSession.findMany({
    where: { userId, exerciseId },
    orderBy: { timestamp: 'desc' },
    include: { sessionMetrics: true },
  });

  if (sessions.length === 0) {
    return {
      totalWorkouts: 0,
      totalReps: 0,
      totalVolume: 0,
      avgScore: 0,
      bestScore: 0,
      maxWeight: null,
      maxEst1RM: null,
      avgROM: 0,
      progression: null,
    };
  }

  const totalWorkouts = sessions.length;
  const totalReps = sessions.reduce((sum, s) => sum + s.totalReps, 0);
  const totalVolume = sessions.reduce(
    (sum, s) => sum + (s.sessionMetrics?.totalVolume || 0),
    0
  );

  const scores = sessions
    .map((s) => s.sessionMetrics?.avgFormScore || 0)
    .filter((s) => s > 0);
  const avgScore = scores.length > 0 
    ? scores.reduce((a, b) => a + b, 0) / scores.length / 10 
    : 0;
  const bestScore = scores.length > 0 ? Math.max(...scores) / 10 : 0;

  const weights = sessions
    .map((s) => s.weightKg)
    .filter((w): w is number => w !== null);
  const maxWeight = weights.length > 0 ? Math.max(...weights) : null;

  const est1RMs = sessions
    .map((s) => s.sessionMetrics?.est1RM)
    .filter((e): e is number => typeof e === 'number' && e > 0);
  const maxEst1RM = est1RMs.length > 0 ? Math.max(...est1RMs) : null;

  const roms = sessions
    .map((s) => s.sessionMetrics?.avgRom || 0)
    .filter((r) => r > 0);
  const avgROM = roms.length > 0 
    ? roms.reduce((a, b) => a + b, 0) / roms.length / 10 
    : 0;

  const progression = calculateProgression(sessions);

  return {
    totalWorkouts,
    totalReps,
    totalVolume,
    avgScore,
    bestScore,
    maxWeight,
    maxEst1RM,
    avgROM,
    progression,
  };
}

function calculateProgression(sessions: any[]): ProgressionData | null {
  if (sessions.length < 4) return null;

  const recent = sessions.slice(0, 4);
  const previous = sessions.slice(4, 8);

  if (previous.length < 2) return null;

  const avg = (arr: number[]) => 
    arr.length > 0 ? arr.reduce((a, b) => a + b, 0) / arr.length : 0;

  const recentVolume = avg(recent.map((s) => s.sessionMetrics?.totalVolume || 0));
  const previousVolume = avg(previous.map((s) => s.sessionMetrics?.totalVolume || 0));

  const recentScore = avg(recent.map((s) => s.sessionMetrics?.avgFormScore || 0));
  const previousScore = avg(previous.map((s) => s.sessionMetrics?.avgFormScore || 0));

  const recentStrength = avg(recent.map((s) => s.sessionMetrics?.est1RM || 0));
  const previousStrength = avg(previous.map((s) => s.sessionMetrics?.est1RM || 0));

  return {
    volumeChange: previousVolume > 0
      ? ((recentVolume - previousVolume) / previousVolume) * 100
      : 0,
    scoreChange: previousScore > 0
      ? ((recentScore - previousScore) / previousScore) * 100
      : 0,
    strengthChange: previousStrength > 0
      ? ((recentStrength - previousStrength) / previousStrength) * 100
      : 0,
  };
}

async function updateUserStats(userId: string) {
  const stats = await prisma.trainingSession.aggregate({
    where: { userId },
    _count: true,
    _sum: { durationMs: true },
  });

  await prisma.user.update({
    where: { id: userId },
    data: {
      totalWorkouts: stats._count,
      totalMinutes: Math.floor((stats._sum.durationMs || 0) / 60000),
    },
  });
}



