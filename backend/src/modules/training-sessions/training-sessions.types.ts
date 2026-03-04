/**
 * Training Sessions Types
 * 
 * Types for storing and retrieving exercise session data from mobile app
 */

// ============================================
// Rep Metrics
// ============================================

export interface RepMetrics {
  rom: number;              // Range of Motion × 10
  symmetry: number | null;  // Bilateral symmetry × 10
  stability: number;        // Core stability × 10
  tempo: number[];          // [eccentric, iso, concentric] in ms
  velocity: number | null;  // Mean velocity × 100
  formScore: number;        // Form score × 10
  alignmentAccuracy: number; // Alignment × 10
}

export interface RepMetricsData {
  num: number;
  durationMs: number;
  worstState: number;       // 0=PERFECT, 1=NORMAL, 2=PAD, 3=WARNING, 4=DANGER
  score: number;            // Score × 10
  weightKg: number | null;
  metrics: RepMetrics;
}

// ============================================
// Session Metrics
// ============================================

export interface SessionMetrics {
  // Kinematic metrics (averages)
  avgRom: number;
  avgSymmetry: number | null;
  avgStability: number;
  avgTempo: number[];
  avgVelocity: number | null;
  avgFormScore: number;
  avgAlignmentAccuracy: number;
  
  // Temporal metrics
  totalTUT: number;         // Total Time Under Tension (ms)
  
  // Load metrics
  totalVolume: number | null;
  maxWeight: number | null;
  est1RM: number | null;    // Estimated 1 Rep Max
  
  // Future metrics (nullable)
  relativeStrength: number | null;
  intensityPercentage: number | null;
  
  // Quality metrics
  formConsistency: number | null;  // DTW score
  fatigueIndex: number | null;     // Rep number where fatigue started
}

// ============================================
// Session Upload (from Mobile)
// ============================================

export interface SessionUploadPayload {
  id: string;
  exerciseId: string;
  timestamp: number;        // Unix timestamp
  durationMs: number;
  totalReps: number;
  countedReps: number;
  invalidReps: number;
  weightKg: number | null;
  weightUnit: string;
  repMetrics: RepMetricsData[];
  sessionMetrics: SessionMetrics | null;

  // Context — source/mode of this session
  context?: string;         // free | program | assessment | explore_workout | quick_start

  // Grouping — shared ID for multi-exercise free sessions
  groupId?: string;

  // Source workout template (if applicable)
  workoutId?: string;

  // Legacy report (optional - for backward compatibility)
  legacyReport?: LegacyReportData;
}

// ============================================
// Explore Session Upload (multi-exercise free session)
// ============================================

export interface ExploreSessionUploadPayload {
  groupId: string;                        // Client-generated UUID linking all sessions
  workoutId?: string;                     // If started from a workout template
  isCustomized?: boolean;                 // If user modified the workout
  context: 'explore_workout' | 'quick_start';
  sessions: (SessionUploadPayload & { context: string; groupId: string; workoutId?: string })[];
}

export interface ExploreSessionResponse {
  groupId: string;
  savedCount: number;
  sessions: { id: string; exerciseId: string; totalReps: number }[];
}

// ============================================
// Legacy Report (PostTrainingReport compatible)
// ============================================

export interface LegacyReportData {
  id?: string;
  summary?: {
    totalReps: number;
    countedReps: number;
    averageScore: number;
    durationMs: number;
    rating: string;
  };
  dangerAlerts?: Array<{
    repNumber: number;
    jointCode: string;
    actualAngle: number;
    dangerMessage: { ar: string; en: string };
  }>;
  errorAnalysis?: Array<{
    jointCode: string;
    errorCount: number;
    percentage: number;
  }>;
  frameCaptures?: Array<{
    id: string;
    captureType: string;
    frameUri: string;
    thumbnailUri?: string;
  }>;
  improvementTips?: Array<{
    priority: string;
    message: { ar: string; en: string };
  }>;
}

// ============================================
// Session Response (to Mobile)
// ============================================

export interface TrainingSessionResponse {
  id: string;
  exerciseId: string;
  exerciseName: { ar: string; en: string };
  timestamp: string;
  durationMs: number;
  totalReps: number;
  countedReps: number;
  invalidReps: number;
  weightKg: number | null;
  weightUnit: string;
  sessionMetrics: SessionMetrics | null;
  repMetrics: RepMetricsData[];
}

// ============================================
// Exercise History (aggregated)
// ============================================

export interface ExerciseHistoryItem {
  id: string;
  timestamp: string;
  durationMs: number;
  totalReps: number;
  countedReps: number;
  avgScore: number;
  weightKg: number | null;
  totalVolume: number | null;
  est1RM: number | null;
}

export interface ExerciseHistoryResponse {
  exerciseId: string;
  exerciseName: { ar: string; en: string };
  totalSessions: number;
  sessions: ExerciseHistoryItem[];
  
  // Aggregated stats
  stats: {
    totalWorkouts: number;
    totalReps: number;
    totalVolume: number;
    avgScore: number;
    bestScore: number;
    maxWeight: number | null;
    maxEst1RM: number | null;
    avgROM: number;
    progression: ProgressionData | null;
  };
}

export interface ProgressionData {
  // Compare last 4 weeks to previous 4 weeks
  volumeChange: number;     // percentage
  scoreChange: number;      // percentage
  strengthChange: number;   // percentage (based on est1RM)
}

// ============================================
// Query Params
// ============================================

export interface HistoryQueryParams {
  exerciseId?: string;
  limit?: number;
  offset?: number;
  startDate?: string;
  endDate?: string;
}

// ============================================
// Program Session Reporting
// ============================================

export interface ProgramSessionStartPayload {
  programId?: string;
  weekNumber: number;
  dayNumber: number;
  startedAt?: number; // Unix timestamp
}

export interface ProgramSessionCompletePayload {
  completedAt?: number; // Unix timestamp
  totalDurationMs?: number;
  totalExercises?: number;
  totalSets?: number;
  completedSets?: number;
  totalReps?: number;
  avgAccuracy?: number;  // Completion rate (reps done / planned * 100)
  avgFormScore?: number; // Form quality (0-100, from rep-level scoring)
  report?: Record<string, unknown>;
}
