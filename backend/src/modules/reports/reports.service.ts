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

// ============================================
// LEVEL AGGREGATORS
// ============================================

/** Build rep-level output from stored data */
function buildRepMetrics(rep: StoredRepDetail): RepMetricsOutput {
  return {
    repNumber: rep.repNumber,
    formScore: rep.score,
    worstState: rep.worstState,
    isCounted: rep.isCounted,
    durationMs: rep.durationMs,
  };
}

/** Build set-level metrics from stored set data */
function buildSetMetrics(set: StoredSetMetrics, includeReps: boolean): SetMetricsOutput {
  const repScores = (set.repDetails || []).map((r) => r.score);
  const tut = (set.repDetails || []).reduce((sum, r) => sum + r.durationMs, 0);

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

  return {
    setNumber: set.setNumber,
    exerciseSlug: set.exerciseSlug,
    completionRate: set.accuracy,
    averageFormScore: set.formScore,
    totalReps: set.repsCompleted,
    repsTarget: set.repsTarget,
    durationMs: set.durationMs,
    weightKg: set.weightKg ?? null,
    tut,
    fatigueIndex,
    formConsistency: Math.round(formConsistency * 10) / 10,
    repDetails: includeReps
      ? (set.repDetails || []).map(buildRepMetrics)
      : undefined,
  };
}

/** Build exercise-level metrics from stored exercise report */
function buildExerciseMetrics(
  ex: StoredExerciseReport,
  includeSets: boolean,
): ExerciseMetricsOutput {
  const sets = (ex.setMetrics || []).map((s) => buildSetMetrics(s, includeSets));
  const formScores = sets.map((s) => s.averageFormScore);

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

  return {
    exerciseSlug: ex.exerciseSlug,
    exerciseName: ex.exerciseName,
    averageFormScore: ex.averageFormScore,
    averageCompletionRate: ex.averageAccuracy,
    totalVolume: Math.round(totalVolume * 10) / 10,
    setsCompleted: ex.setsCompleted,
    setsPlanned: ex.totalSets,
    totalReps: ex.totalReps,
    bestSetNumber,
    dropOffRate: Math.round(dropOffRate * 10) / 10,
    formRating: getFormRating(ex.averageFormScore),
    sets: includeSets ? sets : undefined,
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

  return {
    sessionId: row.programSessionId,
    weekNumber: row.weekNumber,
    dayNumber: row.dayNumber,
    completedAt: row.completedAt?.toISOString() ?? null,
    totalDurationMs: row.totalDurationMs ?? 0,
    exercisesCompleted: row.totalExercises ?? exercises.length,
    exercisesTotal: parsed?.totalExercises ?? row.totalExercises ?? 0,
    totalSets: row.totalSets ?? 0,
    totalReps: row.totalReps ?? 0,
    averageAccuracy: row.avgAccuracy ?? 0,
    averageFormScore: row.avgFormScore ?? 0,
    sessionRating: getFormRating(row.avgFormScore ?? 0),
    strongestExercise,
    weakestExercise,
    exercises: includeExercises ? exercises : undefined,
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
  };
}

// ============================================
// WEEK METRICS BUILDER
// ============================================

function buildWeekMetrics(
  weekNumber: number,
  dayMetrics: DayMetricsOutput[],
  previousWeek: WeekMetricsOutput | null,
): WeekMetricsOutput {
  const trainingDays = dayMetrics.filter((d) => !d.isRestDay);
  const daysTrained = trainingDays.filter((d) => d.sessionsCompleted > 0).length;
  const totalTrainingTime = dayMetrics.reduce((sum, d) => sum + d.totalTrainingTime, 0);

  // Form scores per day for sparkline
  const formScoreTrend = dayMetrics.map((d) => Math.round(d.averageFormScore * 10) / 10);
  const activeFormScores = formScoreTrend.filter((s) => s > 0);
  const avgFormScore = safeAvg(activeFormScores);

  // Total volume & reps from sessions
  let totalVolume = 0;
  let totalReps = 0;
  for (const d of dayMetrics) {
    if (d.sessions) {
      for (const s of d.sessions) {
        totalReps += s.totalReps;
        if (s.exercises) {
          for (const e of s.exercises) {
            totalVolume += e.totalVolume;
          }
        }
      }
    }
  }

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
    totalReps,
    averageFormScore: Math.round(avgFormScore * 10) / 10,
    consistencyScore: Math.round(consistencyRaw * 10) / 10,
    formScoreTrend,
    weekOverWeekChange,
    days: dayMetrics,
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

    // Build day metrics for each day in this week
    const dayMetrics: DayMetricsOutput[] = [];
    for (let d = 1; d <= 7; d++) {
      const programDay = week.days.find((pd) => pd.dayNumber === d);
      const dayRows = rows.filter((r) => r.weekNumber === wn && r.dayNumber === d);
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
        previousWeek = buildWeekMetrics(wn - 1, prevDayMetrics, null);
      }
    }

    const summary = buildWeekMetrics(wn, dayMetrics, previousWeek);
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
      const dayMetrics: DayMetricsOutput[] = [];
      for (let d = 1; d <= 7; d++) {
        const programDay = week.days.find((pd) => pd.dayNumber === d);
        const dayRows = rows.filter(
          (r) => r.weekNumber === week.weekNumber && r.dayNumber === d,
        );
        const totalSessions = programDay?.sessions.length ?? 0;
        const isRestDay = programDay?.isRestDay ?? !programDay;
        dayMetrics.push(
          buildDayMetrics(week.weekNumber, d, dayRows, totalSessions, isRestDay, false),
        );
      }

      const wm = buildWeekMetrics(week.weekNumber, dayMetrics, previousWeek);
      weekMetrics.push(wm);
      previousWeek = wm;
    }

    // Program-level aggregation
    const totalDays = program.durationWeeks * 7;
    const daysTrained = weekMetrics.reduce((sum, w) => sum + w.daysTrained, 0);
    const totalTrainingTime = weekMetrics.reduce((sum, w) => sum + w.totalTrainingTime, 0);
    const totalVolume = weekMetrics.reduce((sum, w) => sum + w.totalVolume, 0);
    const totalReps = weekMetrics.reduce((sum, w) => sum + w.totalReps, 0);

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
    }>();

    for (const row of rows) {
      const parsed = safeParseReport(row.report);
      if (!parsed?.exerciseReports) continue;
      for (const ex of parsed.exerciseReports) {
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
        };
        existing.count++;
        existing.totalScore += ex.averageFormScore;
        existing.totalAccuracy += ex.averageAccuracy;
        existing.totalSets += ex.setsCompleted;
        existing.totalPlanned += ex.totalSets;
        existing.totalReps += ex.totalReps;

        // Calculate volume for this session's exercise
        const vol = (ex.setMetrics || []).reduce(
          (sum, s) => sum + (s.weightKg || 0) * s.repsCompleted,
          0,
        );
        existing.totalVolume += vol;

        exerciseMap.set(ex.exerciseSlug, existing);
      }
    }

    const aggregatedExercises: ExerciseMetricsOutput[] = Array.from(exerciseMap.values())
      .map((e) => ({
        exerciseSlug: e.slug,
        exerciseName: e.name,
        averageFormScore: Math.round((e.totalScore / e.count) * 10) / 10,
        averageCompletionRate: Math.round((e.totalAccuracy / e.count) * 10) / 10,
        totalVolume: Math.round(e.totalVolume * 10) / 10,
        sessionsCount: e.count,
        setsCompleted: e.totalSets,
        setsPlanned: e.totalPlanned,
        totalReps: e.totalReps,
        bestSetNumber: null,
        dropOffRate: 0,
        formRating: getFormRating(e.totalScore / e.count),
        sets: undefined,
      }))
      .sort((a, b) => b.averageFormScore - a.averageFormScore);

    const summary: ProgramMetricsOutput = {
      programId: program.id,
      programProgress,
      daysTrained,
      totalDays,
      totalTrainingTime,
      totalVolume: Math.round(totalVolume),
      totalReps,
      overallFormScore: Math.round(overallFormScore * 10) / 10,
      currentStreak,
      programGrade,
      improvementRate,
      bestWeekNumber,
      weeklyFormScores,
      weeks: includeChildren ? weekMetrics : undefined,
      exercises: aggregatedExercises,
    };

    // Generate program-level insights
    const lastWeek = weekMetrics[weekMetrics.length - 1];
    const insightCtx: InsightContext = {
      currentStreak,
      totalReps,
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
