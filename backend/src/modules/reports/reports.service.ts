/**
 * Reports Service — Unified Metrics Aggregation Engine
 * =====================================================
 *
 * Single source of truth for all report computations.
 * Reads Rep-level data from ProgramSessionReport.report JSON
 * and aggregates upward through every level.
 */

import { getPrisma } from '@/lib/prisma/client';
import type {
  MetricsQuery,
  MetricsResponse,
  StoredSessionReport,
  StoredExerciseReport,
  StoredSetMetrics,
  StoredRepDetail,
  CountingSummary,
  StateBreakdown,
  RepMetricsOutput,
  SetMetricsOutput,
  ExerciseMetricsOutput,
  SessionMetricsOutput,
  DayMetricsOutput,
  WeekMetricsOutput,
  ProgramMetricsOutput,
  ComparisonData,
  FormRating,
  ProgramGrade,
  TrendDirection,
  Insight,
} from './reports.types';
import { generateInsights, type InsightContext } from './insights.engine';

// ============================================
// HELPER UTILITIES
// ============================================

function getFormRating(score: number): FormRating {
  if (score >= 85) return 'Excellent';
  if (score >= 70) return 'Good';
  if (score >= 50) return 'Solid';
  return 'Keep Practicing';
}

function getProgramGrade(attendance: number, formScore: number, consistency: number): ProgramGrade {
  // Weighted: 40% attendance, 40% formScore, 20% consistency
  const composite = attendance * 0.4 + formScore * 0.4 + consistency * 0.2;
  if (composite >= 95) return 'A+';
  if (composite >= 85) return 'A';
  if (composite >= 78) return 'B+';
  if (composite >= 70) return 'B';
  if (composite >= 60) return 'C+';
  if (composite >= 50) return 'C';
  return 'D';
}

function getTrendDirection(values: number[]): TrendDirection {
  if (values.length < 2) return 'stable';
  const firstHalf = values.slice(0, Math.ceil(values.length / 2));
  const secondHalf = values.slice(Math.ceil(values.length / 2));
  const avg1 = firstHalf.reduce((a, b) => a + b, 0) / firstHalf.length;
  const avg2 = secondHalf.reduce((a, b) => a + b, 0) / secondHalf.length;
  const diff = avg2 - avg1;
  if (diff > 3) return 'improving';
  if (diff < -3) return 'declining';
  return 'stable';
}

function stddev(values: number[]): number {
  if (values.length < 2) return 0;
  const mean = values.reduce((a, b) => a + b, 0) / values.length;
  const squaredDiffs = values.map((v) => (v - mean) ** 2);
  return Math.sqrt(squaredDiffs.reduce((a, b) => a + b, 0) / values.length);
}

function safeAvg(values: number[]): number {
  const filtered = values.filter((v) => v > 0);
  if (filtered.length === 0) return 0;
  return filtered.reduce((a, b) => a + b, 0) / filtered.length;
}

function safeParseReport(json: unknown): StoredSessionReport | null {
  if (!json || typeof json !== 'object') return null;
  const report = json as Record<string, unknown>;
  if (typeof report.totalExercises !== 'number') return null;
  return json as StoredSessionReport;
}

function emptyStateBreakdown(): StateBreakdown {
  return { perfect: 0, normal: 0, pad: 0, warning: 0, danger: 0 };
}

function normalizeStateValue(state: number): keyof StateBreakdown {
  switch (state) {
    case 0:
      return 'perfect';
    case 1:
      return 'normal';
    case 2:
      return 'pad';
    case 3:
      return 'warning';
    default:
      return 'danger';
  }
}

function normalizePercentage(value: number): number {
  if (!Number.isFinite(value)) return 0;
  const normalized = value >= 0 && value <= 1 ? value * 100 : value;
  return Math.max(0, Math.min(100, normalized));
}

function ratioPercent(numerator: number, denominator: number): number {
  if (denominator <= 0) return 0;
  return normalizePercentage((numerator / denominator) * 100);
}

function sanitizeNonNegative(value: number | undefined | null): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  return Math.max(0, Math.round(value));
}

function normalizePositionCount(raw: unknown): number {
  if (Array.isArray(raw)) return raw.length;
  if (typeof raw === 'number' && Number.isFinite(raw)) return Math.max(0, Math.round(raw));
  return 0;
}

function resolveRepPositionCount(
  rep: StoredRepDetail,
  explicitKey: 'positionErrorCount' | 'positionWarningCount' | 'positionTipCount',
  legacyArrayKey: 'positionErrors' | 'positionWarnings' | 'errors',
): number {
  const explicit = (rep as unknown as Record<string, unknown>)[explicitKey];
  if (typeof explicit === 'number' && Number.isFinite(explicit)) {
    return Math.max(0, Math.round(explicit));
  }
  const legacy = (rep as unknown as Record<string, unknown>)[legacyArrayKey];
  return normalizePositionCount(legacy);
}

function resolveRepIsInvalidated(rep: StoredRepDetail): boolean {
  if (typeof rep.isInvalidated === 'boolean') return rep.isInvalidated;
  return rep.worstState >= 4;
}

function normalizeRepDetails(repDetails: StoredRepDetail[] | undefined): StoredRepDetail[] {
  return Array.isArray(repDetails) ? repDetails : [];
}

function buildCountingFromRepDetails(repDetails: StoredRepDetail[]): CountingSummary {
  const breakdown = emptyStateBreakdown();
  let countedReps = 0;
  let invalidatedReps = 0;
  let positionErrorReps = 0;
  let positionWarningReps = 0;
  let positionTipReps = 0;

  for (const rep of repDetails) {
    const stateKey = normalizeStateValue(rep.worstState);
    breakdown[stateKey] += 1;

    if (rep.isCounted) countedReps += 1;
    if (resolveRepIsInvalidated(rep)) invalidatedReps += 1;

    if (resolveRepPositionCount(rep, 'positionErrorCount', 'positionErrors') > 0) {
      positionErrorReps += 1;
    }
    if (resolveRepPositionCount(rep, 'positionWarningCount', 'positionWarnings') > 0) {
      positionWarningReps += 1;
    }
    if (resolveRepPositionCount(rep, 'positionTipCount', 'errors') > 0) {
      positionTipReps += 1;
    }
  }

  const totalReps = repDetails.length;
  const incorrectReps = Math.max(0, totalReps - countedReps);
  const uncountedReps = Math.max(0, incorrectReps - invalidatedReps);

  const countedRatio = ratioPercent(countedReps, totalReps);
  const invalidatedRatio = ratioPercent(invalidatedReps, totalReps);
  const uncountedRatio = ratioPercent(uncountedReps, totalReps);

  return {
    totalReps,
    countedReps,
    invalidatedReps,
    uncountedReps,
    incorrectReps,
    countedRatio,
    accuracy: countedRatio,
    invalidatedRatio,
    uncountedRatio,
    stateBreakdown: breakdown,
    positionErrorReps,
    positionWarningReps,
    positionTipReps,
  };
}

function normalizePartialStateBreakdown(
  source: Partial<StateBreakdown> | undefined,
): StateBreakdown {
  const zero = emptyStateBreakdown();
  if (!source) return zero;
  return {
    perfect: sanitizeNonNegative(source.perfect) ?? 0,
    normal: sanitizeNonNegative(source.normal) ?? 0,
    pad: sanitizeNonNegative(source.pad) ?? 0,
    warning: sanitizeNonNegative(source.warning) ?? 0,
    danger: sanitizeNonNegative(source.danger) ?? 0,
  };
}

function mergeStateBreakdown(target: StateBreakdown, source: StateBreakdown): StateBreakdown {
  return {
    perfect: target.perfect + source.perfect,
    normal: target.normal + source.normal,
    pad: target.pad + source.pad,
    warning: target.warning + source.warning,
    danger: target.danger + source.danger,
  };
}

function buildCountingSummary(input: {
  totalReps?: number | null;
  countedReps?: number | null;
  invalidatedReps?: number | null;
  uncountedReps?: number | null;
  incorrectReps?: number | null;
  countedRatio?: number | null;
  accuracy?: number | null;
  invalidatedRatio?: number | null;
  uncountedRatio?: number | null;
  stateBreakdown?: Partial<StateBreakdown>;
  positionErrorReps?: number | null;
  positionWarningReps?: number | null;
  positionTipReps?: number | null;
  repDetails?: StoredRepDetail[] | null;
}): CountingSummary {
  const repDetails = normalizeRepDetails(input.repDetails ?? undefined);
  const fromRepDetails = buildCountingFromRepDetails(repDetails);

  const totalFromInput = sanitizeNonNegative(input.totalReps);
  const countedFromInput = sanitizeNonNegative(input.countedReps);
  const invalidatedFromInput = sanitizeNonNegative(input.invalidatedReps);
  const incorrectFromInput = sanitizeNonNegative(input.incorrectReps);
  const uncountedFromInput = sanitizeNonNegative(input.uncountedReps);

  const totalReps = totalFromInput ?? fromRepDetails.totalReps;

  let countedReps = countedFromInput ?? fromRepDetails.countedReps;
  countedReps = Math.min(totalReps, Math.max(0, countedReps));

  let incorrectReps = incorrectFromInput ?? Math.max(0, totalReps - countedReps);
  incorrectReps = Math.min(totalReps, Math.max(0, incorrectReps));

  let invalidatedReps = invalidatedFromInput ?? fromRepDetails.invalidatedReps;
  invalidatedReps = Math.min(totalReps, Math.max(0, invalidatedReps));
  if (invalidatedReps > incorrectReps) {
    invalidatedReps = incorrectReps;
  }

  let uncountedReps = uncountedFromInput ?? Math.max(0, incorrectReps - invalidatedReps);
  uncountedReps = Math.min(totalReps, Math.max(0, uncountedReps));
  if (uncountedReps + invalidatedReps > incorrectReps) {
    uncountedReps = Math.max(0, incorrectReps - invalidatedReps);
  }

  const countedRatio =
    typeof input.countedRatio === 'number'
      ? normalizePercentage(input.countedRatio)
      : ratioPercent(countedReps, totalReps);
  const accuracy =
    typeof input.accuracy === 'number'
      ? normalizePercentage(input.accuracy)
      : countedRatio;
  const invalidatedRatio =
    typeof input.invalidatedRatio === 'number'
      ? normalizePercentage(input.invalidatedRatio)
      : ratioPercent(invalidatedReps, totalReps);
  const uncountedRatio =
    typeof input.uncountedRatio === 'number'
      ? normalizePercentage(input.uncountedRatio)
      : ratioPercent(uncountedReps, totalReps);

  const stateBreakdown =
    repDetails.length > 0
      ? fromRepDetails.stateBreakdown
      : normalizePartialStateBreakdown(input.stateBreakdown);

  const positionErrorReps =
    sanitizeNonNegative(input.positionErrorReps) ?? fromRepDetails.positionErrorReps;
  const positionWarningReps =
    sanitizeNonNegative(input.positionWarningReps) ?? fromRepDetails.positionWarningReps;
  const positionTipReps =
    sanitizeNonNegative(input.positionTipReps) ?? fromRepDetails.positionTipReps;

  return {
    totalReps,
    countedReps,
    invalidatedReps,
    uncountedReps,
    incorrectReps,
    countedRatio,
    accuracy,
    invalidatedRatio,
    uncountedRatio,
    stateBreakdown,
    positionErrorReps,
    positionWarningReps,
    positionTipReps,
  };
}

function aggregateCountingSummaries(summaries: CountingSummary[]): CountingSummary {
  if (summaries.length === 0) {
    return buildCountingSummary({});
  }

  const totalReps = summaries.reduce((sum, s) => sum + s.totalReps, 0);
  const countedReps = summaries.reduce((sum, s) => sum + s.countedReps, 0);
  const invalidatedReps = summaries.reduce((sum, s) => sum + s.invalidatedReps, 0);
  const uncountedReps = summaries.reduce((sum, s) => sum + s.uncountedReps, 0);
  const incorrectReps = summaries.reduce((sum, s) => sum + s.incorrectReps, 0);
  const positionErrorReps = summaries.reduce((sum, s) => sum + s.positionErrorReps, 0);
  const positionWarningReps = summaries.reduce((sum, s) => sum + s.positionWarningReps, 0);
  const positionTipReps = summaries.reduce((sum, s) => sum + s.positionTipReps, 0);
  const stateBreakdown = summaries.reduce(
    (acc, s) => mergeStateBreakdown(acc, s.stateBreakdown),
    emptyStateBreakdown(),
  );

  return buildCountingSummary({
    totalReps,
    countedReps,
    invalidatedReps,
    uncountedReps,
    incorrectReps,
    stateBreakdown,
    positionErrorReps,
    positionWarningReps,
    positionTipReps,
  });
}

function extractVolumeFromSet(set: StoredSetMetrics): number {
  const weight = typeof set.weightKg === 'number' ? set.weightKg : 0;
  const setCounting = buildCountingSummary(set);
  return weight * setCounting.totalReps;
}

function extractVolumeFromExercise(exercise: StoredExerciseReport): number {
  return (exercise.setMetrics || []).reduce((sum, set) => sum + extractVolumeFromSet(set), 0);
}

function extractVolumeFromSessionReport(report: StoredSessionReport | null): number {
  if (!report?.exerciseReports) return 0;
  return report.exerciseReports.reduce((sum, ex) => sum + extractVolumeFromExercise(ex), 0);
}
// ============================================
// LEVEL AGGREGATORS
// ============================================

/** Build rep-level output from stored data */
function buildRepMetrics(rep: StoredRepDetail): RepMetricsOutput {
  const isInvalidated = resolveRepIsInvalidated(rep);
  const positionErrorCount = resolveRepPositionCount(rep, 'positionErrorCount', 'positionErrors');
  const positionWarningCount = resolveRepPositionCount(rep, 'positionWarningCount', 'positionWarnings');
  const positionTipCount = resolveRepPositionCount(rep, 'positionTipCount', 'errors');

  return {
    repNumber: rep.repNumber,
    formScore: rep.score,
    worstState: rep.worstState,
    isCounted: rep.isCounted,
    durationMs: rep.durationMs,
    isInvalidated,
    isIncorrect: !rep.isCounted || isInvalidated,
    positionErrorCount,
    positionWarningCount,
    positionTipCount,
  };
}

/** Build set-level metrics from stored set data */
function buildSetMetrics(set: StoredSetMetrics, includeReps: boolean): SetMetricsOutput {
  const repDetails = normalizeRepDetails(set.repDetails);
  const repScores = repDetails.map((r) => r.score);
  const tut = repDetails.reduce((sum, r) => sum + r.durationMs, 0);

  // Fatigue index: first rep where form dropped below 80% of first rep's score
  let fatigueIndex: number | null = null;
  if (repScores.length >= 3) {
    const threshold = repScores[0] * 0.8;
    for (let i = 1; i < repScores.length; i++) {
      if (repScores[i] < threshold) {
        fatigueIndex = i + 1; // 1-indexed rep number
        break;
      }
    }
  }

  // Form consistency: 100 - stddev (capped at 0-100)
  const formConsistency = repScores.length >= 2
    ? Math.max(0, Math.min(100, 100 - stddev(repScores)))
    : 100;

  const counting = buildCountingSummary({
    ...set,
    repDetails,
    totalReps: set.totalReps ?? set.repsCompleted,
    accuracy: set.accuracy,
  });

  return {
    setNumber: set.setNumber,
    exerciseSlug: set.exerciseSlug,
    completionRate: counting.accuracy,
    averageFormScore: set.formScore,
    totalReps: counting.totalReps,
    repsTarget: set.repsTarget,
    durationMs: set.durationMs,
    weightKg: set.weightKg ?? null,
    tut,
    fatigueIndex,
    formConsistency: Math.round(formConsistency * 10) / 10,
    repDetails: includeReps ? repDetails.map(buildRepMetrics) : undefined,
    countedReps: counting.countedReps,
    invalidatedReps: counting.invalidatedReps,
    uncountedReps: counting.uncountedReps,
    incorrectReps: counting.incorrectReps,
    countedRatio: counting.countedRatio,
    invalidatedRatio: counting.invalidatedRatio,
    uncountedRatio: counting.uncountedRatio,
    stateBreakdown: counting.stateBreakdown,
    positionErrorReps: counting.positionErrorReps,
    positionWarningReps: counting.positionWarningReps,
    positionTipReps: counting.positionTipReps,
  };
}

/** Build exercise-level metrics from stored exercise report */
function buildExerciseMetrics(
  ex: StoredExerciseReport,
  includeSets: boolean,
): ExerciseMetricsOutput {
  const sets = (ex.setMetrics || []).map((s) => buildSetMetrics(s, includeSets));
  const formScores = sets.map((s) => s.averageFormScore);
  const setCountings = sets.map((s) =>
    buildCountingSummary({
      totalReps: s.totalReps,
      countedReps: s.countedReps,
      invalidatedReps: s.invalidatedReps,
      uncountedReps: s.uncountedReps,
      incorrectReps: s.incorrectReps,
      countedRatio: s.countedRatio,
      invalidatedRatio: s.invalidatedRatio,
      uncountedRatio: s.uncountedRatio,
      stateBreakdown: s.stateBreakdown,
      positionErrorReps: s.positionErrorReps,
      positionWarningReps: s.positionWarningReps,
      positionTipReps: s.positionTipReps,
    }),
  );
  const countingFromSets = aggregateCountingSummaries(setCountings);
  const counting = buildCountingSummary({
    ...ex,
    totalReps: ex.totalReps || countingFromSets.totalReps,
    countedReps: ex.countedReps ?? countingFromSets.countedReps,
    invalidatedReps: ex.invalidatedReps ?? countingFromSets.invalidatedReps,
    uncountedReps: ex.uncountedReps ?? countingFromSets.uncountedReps,
    incorrectReps: ex.incorrectReps ?? countingFromSets.incorrectReps,
    countedRatio: ex.countedRatio ?? countingFromSets.countedRatio,
    accuracy: ex.countedRatio ?? ex.averageAccuracy,
    invalidatedRatio: ex.invalidatedRatio ?? countingFromSets.invalidatedRatio,
    uncountedRatio: ex.uncountedRatio ?? countingFromSets.uncountedRatio,
    stateBreakdown: ex.stateBreakdown ?? countingFromSets.stateBreakdown,
    positionErrorReps: ex.positionErrorReps ?? countingFromSets.positionErrorReps,
    positionWarningReps: ex.positionWarningReps ?? countingFromSets.positionWarningReps,
    positionTipReps: ex.positionTipReps ?? countingFromSets.positionTipReps,
  });

  // Total volume = sum of (weightKg * reps) per set
  const totalVolume = sets.reduce(
    (sum, s) => sum + (s.weightKg || 0) * s.totalReps,
    0,
  );

  // Best set by form score
  let bestSetNumber: number | null = null;
  let bestFormScore = -1;
  for (const s of sets) {
    if (s.averageFormScore > bestFormScore) {
      bestFormScore = s.averageFormScore;
      bestSetNumber = s.setNumber;
    }
  }

  // Drop-off rate: first set score minus last set score
  const dropOffRate =
    formScores.length >= 2 ? formScores[0] - formScores[formScores.length - 1] : 0;

  const effectiveFormScore = ex.averageFormScore || safeAvg(formScores);

  return {
    exerciseSlug: ex.exerciseSlug,
    exerciseName: ex.exerciseName,
    averageFormScore: Math.round(effectiveFormScore * 10) / 10,
    averageCompletionRate: counting.accuracy,
    totalVolume: Math.round(totalVolume * 10) / 10,
    setsCompleted: ex.setsCompleted,
    setsPlanned: ex.totalSets,
    totalReps: counting.totalReps,
    bestSetNumber,
    dropOffRate: Math.round(dropOffRate * 10) / 10,
    formRating: getFormRating(effectiveFormScore),
    sets: includeSets ? sets : undefined,
    countedReps: counting.countedReps,
    invalidatedReps: counting.invalidatedReps,
    uncountedReps: counting.uncountedReps,
    incorrectReps: counting.incorrectReps,
    countedRatio: counting.countedRatio,
    invalidatedRatio: counting.invalidatedRatio,
    uncountedRatio: counting.uncountedRatio,
    stateBreakdown: counting.stateBreakdown,
    positionErrorReps: counting.positionErrorReps,
    positionWarningReps: counting.positionWarningReps,
    positionTipReps: counting.positionTipReps,
  };
}
// ============================================
// DATABASE ROW TYPE
// ============================================

interface ReportRow {
  id: string;
  programSessionId: string;
  weekNumber: number;
  dayNumber: number;
  completedAt: Date | null;
  totalDurationMs: number | null;
  totalExercises: number | null;
  totalSets: number | null;
  completedSets: number | null;
  totalReps: number | null;
  avgAccuracy: number | null;
  avgFormScore: number | null;
  report: unknown;
}

// ============================================
// SESSION METRICS BUILDER
// ============================================

function buildSessionMetrics(
  row: ReportRow,
  includeExercises: boolean,
): SessionMetricsOutput {
  const parsed = safeParseReport(row.report);
  const exercises = parsed?.exerciseReports
    ? parsed.exerciseReports.map((e) => buildExerciseMetrics(e, includeExercises))
    : [];

  let strongestExercise: string | null = null;
  let weakestExercise: string | null = null;
  if (exercises.length > 0) {
    let maxScore = -1;
    let minScore = 101;
    for (const e of exercises) {
      if (e.averageFormScore > maxScore) {
        maxScore = e.averageFormScore;
        strongestExercise = e.exerciseName;
      }
      if (e.averageFormScore < minScore) {
        minScore = e.averageFormScore;
        weakestExercise = e.exerciseName;
      }
    }
  }

  const countingFromExercises = aggregateCountingSummaries(
    exercises.map((e) =>
      buildCountingSummary({
        totalReps: e.totalReps,
        countedReps: e.countedReps,
        invalidatedReps: e.invalidatedReps,
        uncountedReps: e.uncountedReps,
        incorrectReps: e.incorrectReps,
        countedRatio: e.countedRatio,
        invalidatedRatio: e.invalidatedRatio,
        uncountedRatio: e.uncountedRatio,
        stateBreakdown: e.stateBreakdown,
        positionErrorReps: e.positionErrorReps,
        positionWarningReps: e.positionWarningReps,
        positionTipReps: e.positionTipReps,
      }),
    ),
  );

  const counting = buildCountingSummary({
    totalReps: parsed?.totalReps ?? row.totalReps ?? countingFromExercises.totalReps,
    countedReps: parsed?.countedReps ?? countingFromExercises.countedReps,
    invalidatedReps: parsed?.invalidatedReps ?? countingFromExercises.invalidatedReps,
    uncountedReps: parsed?.uncountedReps ?? countingFromExercises.uncountedReps,
    incorrectReps: parsed?.incorrectReps ?? countingFromExercises.incorrectReps,
    countedRatio: parsed?.countedRatio ?? countingFromExercises.countedRatio,
    accuracy: parsed?.averageAccuracy ?? row.avgAccuracy ?? countingFromExercises.accuracy,
    invalidatedRatio: parsed?.invalidatedRatio ?? countingFromExercises.invalidatedRatio,
    uncountedRatio: parsed?.uncountedRatio ?? countingFromExercises.uncountedRatio,
    stateBreakdown: parsed?.stateBreakdown ?? countingFromExercises.stateBreakdown,
    positionErrorReps: parsed?.positionErrorReps ?? countingFromExercises.positionErrorReps,
    positionWarningReps: parsed?.positionWarningReps ?? countingFromExercises.positionWarningReps,
    positionTipReps: parsed?.positionTipReps ?? countingFromExercises.positionTipReps,
  });

  const avgFormScoreSource =
    row.avgFormScore ?? parsed?.averageFormScore ?? safeAvg(exercises.map((e) => e.averageFormScore));
  const averageFormScore = Math.round(avgFormScoreSource * 10) / 10;

  return {
    sessionId: row.programSessionId,
    weekNumber: row.weekNumber,
    dayNumber: row.dayNumber,
    completedAt: row.completedAt?.toISOString() ?? null,
    totalDurationMs: row.totalDurationMs ?? parsed?.totalDurationMs ?? 0,
    exercisesCompleted: row.totalExercises ?? exercises.length,
    exercisesTotal: parsed?.totalExercises ?? row.totalExercises ?? exercises.length,
    totalSets: row.totalSets ?? parsed?.totalSetsCompleted ?? 0,
    totalReps: counting.totalReps,
    averageAccuracy: counting.accuracy,
    averageFormScore,
    sessionRating: getFormRating(averageFormScore),
    strongestExercise,
    weakestExercise,
    exercises: includeExercises ? exercises : undefined,
    countedReps: counting.countedReps,
    invalidatedReps: counting.invalidatedReps,
    uncountedReps: counting.uncountedReps,
    incorrectReps: counting.incorrectReps,
    countedRatio: counting.countedRatio,
    invalidatedRatio: counting.invalidatedRatio,
    uncountedRatio: counting.uncountedRatio,
    stateBreakdown: counting.stateBreakdown,
    positionErrorReps: counting.positionErrorReps,
    positionWarningReps: counting.positionWarningReps,
    positionTipReps: counting.positionTipReps,
  };
}

// ============================================
// DAY METRICS BUILDER
// ============================================

function buildDayMetrics(
  weekNumber: number,
  dayNumber: number,
  rows: ReportRow[],
  totalSessionsInDay: number,
  isRestDay: boolean,
  includeSessions: boolean,
): DayMetricsOutput {
  const sessions = rows.map((r) => buildSessionMetrics(r, false));
  const formScores = sessions.map((s) => s.averageFormScore).filter((s) => s > 0);
  const avgFormScore = safeAvg(formScores);
  const dayCounting = aggregateCountingSummaries(
    sessions.map((s) =>
      buildCountingSummary({
        totalReps: s.totalReps,
        countedReps: s.countedReps,
        invalidatedReps: s.invalidatedReps,
        uncountedReps: s.uncountedReps,
        incorrectReps: s.incorrectReps,
        countedRatio: s.countedRatio,
        invalidatedRatio: s.invalidatedRatio,
        uncountedRatio: s.uncountedRatio,
        stateBreakdown: s.stateBreakdown,
        positionErrorReps: s.positionErrorReps,
        positionWarningReps: s.positionWarningReps,
        positionTipReps: s.positionTipReps,
      }),
    ),
  );

  return {
    weekNumber,
    dayNumber,
    isRestDay,
    sessionsCompleted: sessions.length,
    sessionsPlanned: totalSessionsInDay,
    totalTrainingTime: sessions.reduce((sum, s) => sum + s.totalDurationMs, 0),
    averageFormScore: Math.round(avgFormScore * 10) / 10,
    dayRating: getFormRating(avgFormScore),
    isComplete: sessions.length >= totalSessionsInDay && totalSessionsInDay > 0,
    sessions: includeSessions ? sessions : undefined,
    countedReps: dayCounting.countedReps,
    invalidatedReps: dayCounting.invalidatedReps,
    uncountedReps: dayCounting.uncountedReps,
    incorrectReps: dayCounting.incorrectReps,
    countedRatio: dayCounting.countedRatio,
    invalidatedRatio: dayCounting.invalidatedRatio,
    uncountedRatio: dayCounting.uncountedRatio,
    stateBreakdown: dayCounting.stateBreakdown,
    positionErrorReps: dayCounting.positionErrorReps,
    positionWarningReps: dayCounting.positionWarningReps,
    positionTipReps: dayCounting.positionTipReps,
  };
}

// ============================================
// WEEK METRICS BUILDER
// ============================================

function buildWeekMetrics(
  weekNumber: number,
  dayMetrics: DayMetricsOutput[],
  previousWeek: WeekMetricsOutput | null,
  weekRows: ReportRow[],
): WeekMetricsOutput {
  const trainingDays = dayMetrics.filter((d) => !d.isRestDay);
  const daysTrained = trainingDays.filter((d) => d.sessionsCompleted > 0).length;
  const totalTrainingTime = dayMetrics.reduce((sum, d) => sum + d.totalTrainingTime, 0);

  // Form scores per day for sparkline
  const formScoreTrend = dayMetrics.map((d) => Math.round(d.averageFormScore * 10) / 10);
  const activeFormScores = formScoreTrend.filter((s) => s > 0);
  const avgFormScore = safeAvg(activeFormScores);

  const weekCounting = aggregateCountingSummaries(
    dayMetrics.map((d) =>
      buildCountingSummary({
        totalReps: (d.countedReps ?? 0) + (d.incorrectReps ?? 0),
        countedReps: d.countedReps,
        invalidatedReps: d.invalidatedReps,
        uncountedReps: d.uncountedReps,
        incorrectReps: d.incorrectReps,
        countedRatio: d.countedRatio,
        invalidatedRatio: d.invalidatedRatio,
        uncountedRatio: d.uncountedRatio,
        stateBreakdown: d.stateBreakdown,
        positionErrorReps: d.positionErrorReps,
        positionWarningReps: d.positionWarningReps,
        positionTipReps: d.positionTipReps,
      }),
    ),
  );

  const totalVolume = weekRows.reduce(
    (sum, row) => sum + extractVolumeFromSessionReport(safeParseReport(row.report)),
    0,
  );

  // Consistency: how evenly distributed training was (lower stddev = higher consistency)
  const dailyCounts = trainingDays.map((d) => d.sessionsCompleted);
  const consistencyRaw = dailyCounts.length > 0
    ? Math.max(0, 100 - stddev(dailyCounts) * 20)
    : 0;

  // Week-over-week comparison
  let weekOverWeekChange: WeekMetricsOutput['weekOverWeekChange'] = null;
  if (previousWeek) {
    weekOverWeekChange = {
      formScore: Math.round((avgFormScore - previousWeek.averageFormScore) * 10) / 10,
      volume: Math.round(totalVolume - previousWeek.totalVolume),
      attendance: daysTrained - previousWeek.daysTrained,
    };
  }

  return {
    weekNumber,
    daysTrained,
    daysTotal: trainingDays.length || 7,
    totalTrainingTime,
    totalVolume: Math.round(totalVolume),
    totalReps: weekCounting.totalReps,
    averageFormScore: Math.round(avgFormScore * 10) / 10,
    consistencyScore: Math.round(consistencyRaw * 10) / 10,
    formScoreTrend,
    weekOverWeekChange,
    days: dayMetrics,
    countedReps: weekCounting.countedReps,
    invalidatedReps: weekCounting.invalidatedReps,
    uncountedReps: weekCounting.uncountedReps,
    incorrectReps: weekCounting.incorrectReps,
    countedRatio: weekCounting.countedRatio,
    invalidatedRatio: weekCounting.invalidatedRatio,
    uncountedRatio: weekCounting.uncountedRatio,
    stateBreakdown: weekCounting.stateBreakdown,
    positionErrorReps: weekCounting.positionErrorReps,
    positionWarningReps: weekCounting.positionWarningReps,
    positionTipReps: weekCounting.positionTipReps,
  };
}
// ============================================
// MAIN SERVICE
// ============================================

export const reportsService = {
  /**
   * Unified metrics endpoint — returns aggregated metrics at the requested scope.
   */
  async getMetrics(userId: string, query: MetricsQuery): Promise<MetricsResponse> {
    const prisma = await getPrisma();
    const { programId, scope, weekNumber, dayNumber, sessionId, exerciseSlug, includeHistory, includeChildren } = query;

    // ── Verify enrollment (active OR historical) ──
    // Phase 0 fix: allow querying reports for completed programs, not just active ones.
    // Try active enrollment first, then fall back to any enrollment for this program.
    let userProgram = await prisma.userProgram.findFirst({
      where: { userId, programId, isActive: true },
      include: {
        program: {
          include: {
            weeks: {
              include: {
                days: {
                  include: {
                    sessions: { include: { items: true } },
                  },
                },
              },
            },
          },
        },
      },
    });

    if (!userProgram) {
      userProgram = await prisma.userProgram.findFirst({
        where: { userId, programId },
        orderBy: { createdAt: 'desc' },
        include: {
          program: {
            include: {
              weeks: {
                include: {
                  days: {
                    include: {
                      sessions: { include: { items: true } },
                    },
                  },
                },
              },
            },
          },
        },
      });
    }

    if (!userProgram || !userProgram.program) {
      return { success: false, scope, summary: {} as never, error: 'No enrollment found for this program' };
    }

    const program = userProgram.program;

    // ── Fetch all completed reports for this user + program ──
    const whereClause: Record<string, unknown> = {
      userId,
      programId,
      status: 'completed',
    };
    if (weekNumber !== undefined) whereClause.weekNumber = weekNumber;
    if (dayNumber !== undefined) whereClause.dayNumber = dayNumber;
    if (sessionId) whereClause.programSessionId = sessionId;

    const reportRows: ReportRow[] = await prisma.programSessionReport.findMany({
      where: whereClause,
      orderBy: [{ weekNumber: 'asc' }, { dayNumber: 'asc' }, { completedAt: 'asc' }],
    });

    // ── Route to scope handler ──
    switch (scope) {
      case 'session':
        return this.buildSessionScope(reportRows, query, includeChildren ?? false);

      case 'exercise':
        return this.buildExerciseScope(reportRows, query, includeHistory ?? false);

      case 'day':
        return this.buildDayScope(reportRows, program, query, includeChildren ?? false);

      case 'week':
        return this.buildWeekScope(reportRows, program, query, includeChildren ?? false, includeHistory ?? false, userId);

      case 'program':
        return this.buildProgramScope(reportRows, program, userProgram, includeChildren ?? false, includeHistory ?? false);

      default:
        return { success: false, scope, summary: {} as never, error: `Unknown scope: ${scope}` };
    }
  },

  // ── SESSION SCOPE ──
  buildSessionScope(
    rows: ReportRow[],
    query: MetricsQuery,
    includeChildren: boolean,
  ): MetricsResponse {
    const targetRow = query.sessionId
      ? rows.find((r) => r.programSessionId === query.sessionId)
      : rows[0];

    if (!targetRow) {
      return {
        success: false,
        scope: 'session',
        summary: {} as never,
        error: 'No completed session report found',
      };
    }

    const summary = buildSessionMetrics(targetRow, includeChildren);

    // Generate insights for this session
    const insightCtx: InsightContext = {
      sessionFormScore: summary.averageFormScore,
      exercises: summary.exercises?.map((e) => ({
        name: e.exerciseName,
        formScore: e.averageFormScore,
        dropOffRate: e.dropOffRate,
        setsCompleted: e.setsCompleted,
        setsPlanned: e.setsPlanned,
      })),
    };
    const insights = generateInsights(insightCtx);

    return { success: true, scope: 'session', summary, insights };
  },

  // ── EXERCISE SCOPE ──
  buildExerciseScope(
    rows: ReportRow[],
    query: MetricsQuery,
    includeHistory: boolean,
  ): MetricsResponse {
    const slug = query.exerciseSlug;
    if (!slug) {
      return { success: false, scope: 'exercise', summary: {} as never, error: 'exerciseSlug is required for exercise scope' };
    }

    // Collect all exercise data across sessions
    const allExerciseData: StoredExerciseReport[] = [];
    const sessionDates: string[] = [];
    for (const row of rows) {
      const parsed = safeParseReport(row.report);
      if (!parsed?.exerciseReports) continue;
      const match = parsed.exerciseReports.find((e) => e.exerciseSlug === slug);
      if (match) {
        allExerciseData.push(match);
        sessionDates.push(row.completedAt?.toISOString() ?? '');
      }
    }

    if (allExerciseData.length === 0) {
      return { success: false, scope: 'exercise', summary: {} as never, error: `No data found for exercise: ${slug}` };
    }

    // Build aggregate from latest session
    const latest = allExerciseData[allExerciseData.length - 1];
    const summary = buildExerciseMetrics(latest, true);

    // Comparison with previous session
    let comparison: ComparisonData | undefined;
    if (includeHistory && allExerciseData.length >= 2) {
      const prev = allExerciseData[allExerciseData.length - 2];
      const formScores = allExerciseData.map((e) => e.averageFormScore);
      comparison = {
        previousFormScore: prev.averageFormScore,
        previousVolume: prev.setMetrics
          ? prev.setMetrics.reduce((s, sm) => s + (sm.weightKg || 0) * sm.repsCompleted, 0)
          : null,
        previousReps: prev.totalReps,
        formScoreDelta: Math.round((latest.averageFormScore - prev.averageFormScore) * 10) / 10,
        volumeDelta: null,
        repsDelta: latest.totalReps - prev.totalReps,
        trendDirection: getTrendDirection(formScores),
      };
    }

    return { success: true, scope: 'exercise', summary, comparison };
  },

  // ── DAY SCOPE ──
  buildDayScope(
    rows: ReportRow[],
    program: ProgramWithStructure,
    query: MetricsQuery,
    includeChildren: boolean,
  ): MetricsResponse {
    const wn = query.weekNumber ?? 1;
    const dn = query.dayNumber ?? 1;

    const dayRows = rows.filter((r) => r.weekNumber === wn && r.dayNumber === dn);
    const week = program.weeks.find((w) => w.weekNumber === wn);
    const day = week?.days.find((d) => d.dayNumber === dn);
    const totalSessions = day?.sessions.length ?? 0;
    const isRestDay = day?.isRestDay ?? false;

    const summary = buildDayMetrics(wn, dn, dayRows, totalSessions, isRestDay, includeChildren);
    return { success: true, scope: 'day', summary };
  },

  // ── WEEK SCOPE ──
  async buildWeekScope(
    rows: ReportRow[],
    program: ProgramWithStructure,
    query: MetricsQuery,
    includeChildren: boolean,
    includeHistory: boolean,
    userId: string,
  ): Promise<MetricsResponse> {
    const wn = query.weekNumber ?? 1;
    const week = program.weeks.find((w) => w.weekNumber === wn);

    if (!week) {
      return { success: false, scope: 'week', summary: {} as never, error: `Week ${wn} not found` };
    }

    const weekRows = rows.filter((r) => r.weekNumber === wn);

    // Build day metrics for each day in this week
    const dayMetrics: DayMetricsOutput[] = [];
    for (let d = 1; d <= 7; d++) {
      const programDay = week.days.find((pd) => pd.dayNumber === d);
      const dayRows = weekRows.filter((r) => r.dayNumber === d);
      const totalSessions = programDay?.sessions.length ?? 0;
      const isRestDay = programDay?.isRestDay ?? !programDay;

      dayMetrics.push(
        buildDayMetrics(wn, d, dayRows, totalSessions, isRestDay, includeChildren),
      );
    }

    // Get previous week for comparison
    let previousWeek: WeekMetricsOutput | null = null;
    if (includeHistory && wn > 1) {
      const prevWeekStruct = program.weeks.find((w) => w.weekNumber === wn - 1);
      if (prevWeekStruct) {
        const prisma = await getPrisma();
        const prevRows: ReportRow[] = await prisma.programSessionReport.findMany({
          where: {
            userId,
            programId: query.programId,
            status: 'completed',
            weekNumber: wn - 1,
          },
          orderBy: [{ dayNumber: 'asc' }],
        });
        const prevDayMetrics: DayMetricsOutput[] = [];
        for (let d = 1; d <= 7; d++) {
          const pd = prevWeekStruct.days.find((dy) => dy.dayNumber === d);
          const dayRows = prevRows.filter((r) => r.dayNumber === d);
          prevDayMetrics.push(
            buildDayMetrics(wn - 1, d, dayRows, pd?.sessions.length ?? 0, pd?.isRestDay ?? !pd, false),
          );
        }
        previousWeek = buildWeekMetrics(wn - 1, prevDayMetrics, null, prevRows);
      }
    }

    const summary = buildWeekMetrics(wn, dayMetrics, previousWeek, weekRows);
    if (!includeChildren) {
      summary.days = undefined;
    }

    return { success: true, scope: 'week', summary };
  },

  // ── PROGRAM SCOPE ──
  buildProgramScope(
    rows: ReportRow[],
    program: ProgramWithStructure,
    userProgram: { startDate: Date },
    includeChildren: boolean,
    _includeHistory: boolean,
  ): MetricsResponse {
    // Build week-by-week metrics
    const weekMetrics: WeekMetricsOutput[] = [];
    let previousWeek: WeekMetricsOutput | null = null;

    for (const week of program.weeks.sort((a, b) => a.weekNumber - b.weekNumber)) {
      const weekRows = rows.filter((r) => r.weekNumber === week.weekNumber);
      const dayMetrics: DayMetricsOutput[] = [];
      for (let d = 1; d <= 7; d++) {
        const programDay = week.days.find((pd) => pd.dayNumber === d);
        const dayRows = weekRows.filter((r) => r.dayNumber === d);
        const totalSessions = programDay?.sessions.length ?? 0;
        const isRestDay = programDay?.isRestDay ?? !programDay;
        dayMetrics.push(
          buildDayMetrics(week.weekNumber, d, dayRows, totalSessions, isRestDay, false),
        );
      }

      const wm = buildWeekMetrics(week.weekNumber, dayMetrics, previousWeek, weekRows);
      weekMetrics.push(wm);
      previousWeek = wm;
    }

    // Program-level aggregation
    const totalDays = program.durationWeeks * 7;
    const daysTrained = weekMetrics.reduce((sum, w) => sum + w.daysTrained, 0);
    const totalTrainingTime = weekMetrics.reduce((sum, w) => sum + w.totalTrainingTime, 0);
    const totalVolume = weekMetrics.reduce((sum, w) => sum + w.totalVolume, 0);

    const programCounting = aggregateCountingSummaries(
      weekMetrics.map((w) =>
        buildCountingSummary({
          totalReps: w.totalReps,
          countedReps: w.countedReps,
          invalidatedReps: w.invalidatedReps,
          uncountedReps: w.uncountedReps,
          incorrectReps: w.incorrectReps,
          countedRatio: w.countedRatio,
          invalidatedRatio: w.invalidatedRatio,
          uncountedRatio: w.uncountedRatio,
          stateBreakdown: w.stateBreakdown,
          positionErrorReps: w.positionErrorReps,
          positionWarningReps: w.positionWarningReps,
          positionTipReps: w.positionTipReps,
        }),
      ),
    );

    const weeklyFormScores = weekMetrics.map((w) => w.averageFormScore);
    const activeFormScores = weeklyFormScores.filter((s) => s > 0);
    const overallFormScore = safeAvg(activeFormScores);

    // Improvement rate: latest week vs first active week
    const firstActiveWeek = weeklyFormScores.find((s) => s > 0);
    const lastActiveWeek = [...weeklyFormScores].reverse().find((s) => s > 0);
    const improvementRate =
      firstActiveWeek && lastActiveWeek && firstActiveWeek > 0
        ? Math.round(((lastActiveWeek - firstActiveWeek) / firstActiveWeek) * 1000) / 10
        : 0;

    // Best week
    let bestWeekNumber: number | null = null;
    let bestWeekScore = -1;
    for (const wm of weekMetrics) {
      if (wm.averageFormScore > bestWeekScore) {
        bestWeekScore = wm.averageFormScore;
        bestWeekNumber = wm.weekNumber;
      }
    }

    // Streak calculation
    const currentStreak = calculateStreak(rows, userProgram.startDate, program.durationWeeks);

    // Program progress
    const totalTrainingDays = program.weeks.reduce(
      (sum, w) => sum + w.days.filter((d) => !d.isRestDay).length,
      0,
    );
    const programProgress =
      totalTrainingDays > 0
        ? Math.min(100, Math.round((daysTrained / totalTrainingDays) * 1000) / 10)
        : 0;

    // Program grade
    const attendance = totalTrainingDays > 0 ? (daysTrained / totalTrainingDays) * 100 : 0;
    const avgConsistency = safeAvg(weekMetrics.map((w) => w.consistencyScore));
    const programGrade = getProgramGrade(attendance, overallFormScore, avgConsistency);

    // Aggregate exercises across all sessions
    const exerciseMap = new Map<string, {
      slug: string;
      name: string;
      count: number;
      totalScore: number;
      totalAccuracy: number;
      totalVolume: number;
      totalSets: number;
      totalPlanned: number;
      totalReps: number;
      countedReps: number;
      invalidatedReps: number;
      uncountedReps: number;
      incorrectReps: number;
      stateBreakdown: StateBreakdown;
      positionErrorReps: number;
      positionWarningReps: number;
      positionTipReps: number;
    }>();

    for (const row of rows) {
      const parsed = safeParseReport(row.report);
      if (!parsed?.exerciseReports) continue;
      for (const ex of parsed.exerciseReports) {
        const exMetrics = buildExerciseMetrics(ex, false);
        const existing = exerciseMap.get(ex.exerciseSlug) || {
          slug: ex.exerciseSlug,
          name: ex.exerciseName,
          count: 0,
          totalScore: 0,
          totalAccuracy: 0,
          totalVolume: 0,
          totalSets: 0,
          totalPlanned: 0,
          totalReps: 0,
          countedReps: 0,
          invalidatedReps: 0,
          uncountedReps: 0,
          incorrectReps: 0,
          stateBreakdown: emptyStateBreakdown(),
          positionErrorReps: 0,
          positionWarningReps: 0,
          positionTipReps: 0,
        };
        existing.count++;
        existing.totalScore += exMetrics.averageFormScore;
        existing.totalAccuracy += exMetrics.averageCompletionRate;
        existing.totalVolume += exMetrics.totalVolume;
        existing.totalSets += exMetrics.setsCompleted;
        existing.totalPlanned += exMetrics.setsPlanned;
        existing.totalReps += exMetrics.totalReps;
        existing.countedReps += exMetrics.countedReps ?? 0;
        existing.invalidatedReps += exMetrics.invalidatedReps ?? 0;
        existing.uncountedReps += exMetrics.uncountedReps ?? 0;
        existing.incorrectReps += exMetrics.incorrectReps ?? 0;
        existing.stateBreakdown = mergeStateBreakdown(
          existing.stateBreakdown,
          exMetrics.stateBreakdown ?? emptyStateBreakdown(),
        );
        existing.positionErrorReps += exMetrics.positionErrorReps ?? 0;
        existing.positionWarningReps += exMetrics.positionWarningReps ?? 0;
        existing.positionTipReps += exMetrics.positionTipReps ?? 0;

        exerciseMap.set(ex.exerciseSlug, existing);
      }
    }

    const aggregatedExercises: ExerciseMetricsOutput[] = Array.from(exerciseMap.values())
      .map((e) => {
        const counting = buildCountingSummary({
          totalReps: e.totalReps,
          countedReps: e.countedReps,
          invalidatedReps: e.invalidatedReps,
          uncountedReps: e.uncountedReps,
          incorrectReps: e.incorrectReps,
          stateBreakdown: e.stateBreakdown,
          positionErrorReps: e.positionErrorReps,
          positionWarningReps: e.positionWarningReps,
          positionTipReps: e.positionTipReps,
        });

        return {
          exerciseSlug: e.slug,
          exerciseName: e.name,
          averageFormScore: Math.round((e.totalScore / e.count) * 10) / 10,
          averageCompletionRate: Math.round(counting.countedRatio * 10) / 10,
          totalVolume: Math.round(e.totalVolume * 10) / 10,
          sessionsCount: e.count,
          setsCompleted: e.totalSets,
          setsPlanned: e.totalPlanned,
          totalReps: counting.totalReps,
          bestSetNumber: null,
          dropOffRate: 0,
          formRating: getFormRating(e.totalScore / e.count),
          sets: undefined,
          countedReps: counting.countedReps,
          invalidatedReps: counting.invalidatedReps,
          uncountedReps: counting.uncountedReps,
          incorrectReps: counting.incorrectReps,
          countedRatio: counting.countedRatio,
          invalidatedRatio: counting.invalidatedRatio,
          uncountedRatio: counting.uncountedRatio,
          stateBreakdown: counting.stateBreakdown,
          positionErrorReps: counting.positionErrorReps,
          positionWarningReps: counting.positionWarningReps,
          positionTipReps: counting.positionTipReps,
        };
      })
      .sort((a, b) => b.averageFormScore - a.averageFormScore);

    const summary: ProgramMetricsOutput = {
      programId: program.id,
      programProgress,
      daysTrained,
      totalDays,
      totalTrainingTime,
      totalVolume: Math.round(totalVolume),
      totalReps: programCounting.totalReps,
      overallFormScore: Math.round(overallFormScore * 10) / 10,
      currentStreak,
      programGrade,
      improvementRate,
      bestWeekNumber,
      weeklyFormScores,
      weeks: includeChildren ? weekMetrics : undefined,
      exercises: aggregatedExercises,
      countedReps: programCounting.countedReps,
      invalidatedReps: programCounting.invalidatedReps,
      uncountedReps: programCounting.uncountedReps,
      incorrectReps: programCounting.incorrectReps,
      countedRatio: programCounting.countedRatio,
      invalidatedRatio: programCounting.invalidatedRatio,
      uncountedRatio: programCounting.uncountedRatio,
      stateBreakdown: programCounting.stateBreakdown,
      positionErrorReps: programCounting.positionErrorReps,
      positionWarningReps: programCounting.positionWarningReps,
      positionTipReps: programCounting.positionTipReps,
    };
    // Generate program-level insights
    const lastWeek = weekMetrics[weekMetrics.length - 1];
    const insightCtx: InsightContext = {
      currentStreak,
      totalReps: programCounting.totalReps,
      weekDaysTrained: lastWeek?.daysTrained,
      weekDaysTotal: lastWeek?.daysTotal,
      improvementRate,
      programGrade,
    };
    const insights = generateInsights(insightCtx);

    return { success: true, scope: 'program', summary, insights };
  },
};

// ============================================
// STREAK CALCULATION
// ============================================

function calculateStreak(rows: ReportRow[], startDate: Date, durationWeeks: number): number {
  // Build a set of trained dayIndex values
  const trainedDayIndices = new Set<number>();
  for (const row of rows) {
    const dayIndex = (row.weekNumber - 1) * 7 + (row.dayNumber - 1);
    trainedDayIndices.add(dayIndex);
  }

  // Current day index
  const now = new Date();
  const diffMs = now.getTime() - startDate.getTime();
  const currentDayIndex = Math.max(0, Math.floor(diffMs / (24 * 60 * 60 * 1000)));
  const maxDayIndex = Math.min(currentDayIndex, durationWeeks * 7 - 1);

  // Count consecutive days backwards from today (or yesterday if today not trained yet)
  let streak = 0;
  for (let i = maxDayIndex; i >= 0; i--) {
    if (trainedDayIndices.has(i)) {
      streak++;
    } else {
      // Allow today to be untrained (they might train later)
      if (i === maxDayIndex) continue;
      break;
    }
  }

  return streak;
}

// ============================================
// PROGRAM STRUCTURE TYPE (from Prisma include)
// ============================================

type ProgramWithStructure = {
  id: string;
  durationWeeks: number;
  weeks: Array<{
    weekNumber: number;
    days: Array<{
      dayNumber: number;
      isRestDay: boolean;
      sessions: Array<{
        id: string;
        items: Array<{
          id: string;
          type: string;
        }>;
      }>;
    }>;
  }>;
};

