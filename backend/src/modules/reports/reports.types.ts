/**
 * Reports Types — Unified Metrics System
 * =======================================
 *
 * One endpoint. All report levels. Rep is the atom.
 * Everything aggregates upward: Rep → Set → Exercise → Session → Day → Week → Program.
 */

// ============================================
// REQUEST TYPES
// ============================================

export type MetricsScope = 'program' | 'week' | 'day' | 'session' | 'exercise';

export interface MetricsQuery {
  programId: string;
  scope: MetricsScope;
  weekNumber?: number;
  dayNumber?: number;
  sessionId?: string;
  exerciseSlug?: string;
  includeHistory?: boolean;
  includeChildren?: boolean;
}

// ============================================
// STORED REPORT JSON STRUCTURE (from mobile)
// ============================================

/** What the mobile app stores inside ProgramSessionReport.report */
export interface StoredRepDetail {
  repNumber: number;
  score: number;        // 0-100 form quality
  worstState: number;   // 0=PERFECT … 4=DANGER
  isCounted: boolean;
  durationMs: number;
}

export interface StoredSetMetrics {
  exerciseSlug: string;
  exerciseIndex: number;
  setNumber: number;
  repsCompleted: number;
  repsTarget: number;
  durationMs: number;
  accuracy: number;     // completion rate %
  formScore: number;    // average rep quality 0-100
  weightKg?: number | null;
  repDetails?: StoredRepDetail[];
}

export interface StoredExerciseReport {
  exerciseSlug: string;
  exerciseName: string;
  setsCompleted: number;
  totalSets: number;
  totalReps: number;
  averageAccuracy: number;
  averageFormScore: number;
  setMetrics?: StoredSetMetrics[];
}

export interface StoredSessionReport {
  totalExercises: number;
  totalSetsCompleted: number;
  totalSetsPlanned: number;
  totalReps: number;
  totalDurationMs: number;
  averageAccuracy: number;
  averageFormScore: number;
  exerciseReports?: StoredExerciseReport[];
}

// ============================================
// OUTPUT METRIC TYPES (returned to client)
// ============================================

/** Form quality rating label */
export type FormRating = 'Excellent' | 'Good' | 'Solid' | 'Keep Practicing';

/** Grade for program performance */
export type ProgramGrade = 'A+' | 'A' | 'B+' | 'B' | 'C+' | 'C' | 'D';

/** Trend direction indicator */
export type TrendDirection = 'improving' | 'stable' | 'declining';

// ── Rep-level metrics ──

export interface RepMetricsOutput {
  repNumber: number;
  formScore: number;
  worstState: number;
  isCounted: boolean;
  durationMs: number;
}

// ── Set-level metrics ──

export interface SetMetricsOutput {
  setNumber: number;
  exerciseSlug: string;
  completionRate: number;       // reps done / target %
  averageFormScore: number;
  totalReps: number;
  repsTarget: number;
  durationMs: number;
  weightKg: number | null;
  tut: number;                  // time under tension (sum of rep durations)
  fatigueIndex: number | null;  // rep # where form dropped below 80% of first rep
  formConsistency: number;      // 100 - stddev of rep scores (higher = more consistent)
  repDetails?: RepMetricsOutput[];
}

// ── Exercise-level metrics ──

export interface ExerciseMetricsOutput {
  exerciseSlug: string;
  exerciseName: string;
  averageFormScore: number;
  averageCompletionRate: number;
  totalVolume: number;          // weight × reps (sum across sets)
  sessionsCount?: number;       // number of sessions this exercise was performed in (for aggregated views)
  setsCompleted: number;
  setsPlanned: number;
  totalReps: number;
  bestSetNumber: number | null;
  dropOffRate: number;          // formScore(set1) - formScore(lastSet)
  formRating: FormRating;
  sets?: SetMetricsOutput[];
}

// ── Session-level metrics ──

export interface SessionMetricsOutput {
  sessionId: string;
  weekNumber: number;
  dayNumber: number;
  completedAt: string | null;
  totalDurationMs: number;
  exercisesCompleted: number;
  exercisesTotal: number;
  totalSets: number;
  totalReps: number;
  averageAccuracy: number;
  averageFormScore: number;
  sessionRating: FormRating;
  strongestExercise: string | null;
  weakestExercise: string | null;
  exercises?: ExerciseMetricsOutput[];
}

// ── Day-level metrics ──

export interface DayMetricsOutput {
  weekNumber: number;
  dayNumber: number;
  isRestDay: boolean;
  sessionsCompleted: number;
  sessionsPlanned: number;
  totalTrainingTime: number;
  averageFormScore: number;
  dayRating: FormRating;
  isComplete: boolean;
  sessions?: SessionMetricsOutput[];
}

// ── Week-level metrics ──

export interface WeekMetricsOutput {
  weekNumber: number;
  daysTrained: number;
  daysTotal: number;
  totalTrainingTime: number;
  totalVolume: number;
  totalReps: number;
  averageFormScore: number;
  consistencyScore: number;     // how evenly distributed training was across the week
  formScoreTrend: number[];     // daily form scores for sparkline
  weekOverWeekChange: {
    formScore: number;          // delta vs previous week
    volume: number;
    attendance: number;
  } | null;
  days?: DayMetricsOutput[];
}

// ── Program-level metrics ──

export interface ProgramMetricsOutput {
  programId: string;
  programProgress: number;      // % complete
  daysTrained: number;
  totalDays: number;
  totalTrainingTime: number;
  totalVolume: number;
  totalReps: number;
  overallFormScore: number;
  currentStreak: number;
  programGrade: ProgramGrade;
  improvementRate: number;      // % change from week 1 to current
  bestWeekNumber: number | null;
  weeklyFormScores: number[];   // for trend chart
  weeks?: WeekMetricsOutput[];
  exercises?: ExerciseMetricsOutput[];
}

// ── Comparison data ──

export interface ComparisonData {
  previousFormScore: number | null;
  previousVolume: number | null;
  previousReps: number | null;
  formScoreDelta: number | null;
  volumeDelta: number | null;
  repsDelta: number | null;
  trendDirection: TrendDirection;
}

// ── Insights ──

export interface Insight {
  type: 'positive' | 'warning' | 'info' | 'milestone';
  icon: string;
  message: string;
}

// ── Unified response ──

export interface MetricsResponse {
  success: boolean;
  scope: MetricsScope;
  summary:
    | ProgramMetricsOutput
    | WeekMetricsOutput
    | DayMetricsOutput
    | SessionMetricsOutput
    | ExerciseMetricsOutput;
  comparison?: ComparisonData;
  insights?: Insight[];
  error?: string;
}
