package com.trainingvalidator.poc.network

import com.google.gson.annotations.SerializedName

/**
 * Unified Reports API response models.
 * Maps to the backend's GET /api/mobile/reports/metrics endpoint.
 */

// ── Top-level response ──

data class MetricsResponse(
    val success: Boolean,
    val scope: String,
    val summary: MetricsSummary?,
    val comparison: ComparisonData?,
    val insights: List<InsightData>?,
    val error: String?
)

/**
 * Generic summary that can hold any scope's metrics.
 * Gson deserializes all fields; unused ones are null.
 */
data class MetricsSummary(
    // Program-level
    val programId: String? = null,
    val programProgress: Float? = null,
    val daysTrained: Int? = null,
    val totalDays: Int? = null,
    val totalTrainingTime: Long? = null,
    val totalVolume: Float? = null,
    val totalReps: Int? = null,
    val overallFormScore: Float? = null,
    val currentStreak: Int? = null,
    val programGrade: String? = null,
    val improvementRate: Float? = null,
    val bestWeekNumber: Int? = null,
    val weeklyFormScores: List<Float>? = null,
    val weeks: List<WeekMetrics>? = null,

    // Week-level
    val weekNumber: Int? = null,
    val daysTotal: Int? = null,
    val consistencyScore: Float? = null,
    val formScoreTrend: List<Float>? = null,
    val weekOverWeekChange: WeekComparison? = null,
    val days: List<DayMetrics>? = null,

    // Day-level
    val dayNumber: Int? = null,
    val isRestDay: Boolean? = null,
    val sessionsCompleted: Int? = null,
    val sessionsPlanned: Int? = null,
    val dayRating: String? = null,
    val isComplete: Boolean? = null,
    val sessions: List<SessionMetrics>? = null,

    // Session-level
    val sessionId: String? = null,
    val completedAt: String? = null,
    val totalDurationMs: Long? = null,
    val exercisesCompleted: Int? = null,
    val exercisesTotal: Int? = null,
    val totalSets: Int? = null,
    val averageAccuracy: Float? = null,
    val averageFormScore: Float? = null,
    val sessionRating: String? = null,
    val strongestExercise: String? = null,
    val weakestExercise: String? = null,
    val exercises: List<ExerciseMetrics>? = null,

    // Exercise-level
    val exerciseSlug: String? = null,
    val exerciseName: String? = null,
    val averageCompletionRate: Float? = null,
    val setsCompleted: Int? = null,
    val setsPlanned: Int? = null,
    val bestSetNumber: Int? = null,
    val dropOffRate: Float? = null,
    val formRating: String? = null,
    val sets: List<SetMetrics>? = null
)

// ── Week metrics ──

data class WeekMetrics(
    val weekNumber: Int,
    val daysTrained: Int,
    val daysTotal: Int,
    val totalTrainingTime: Long,
    val totalVolume: Float,
    val totalReps: Int,
    val averageFormScore: Float,
    val consistencyScore: Float,
    val formScoreTrend: List<Float>?,
    val weekOverWeekChange: WeekComparison?,
    val days: List<DayMetrics>?
)

data class WeekComparison(
    val formScore: Float,
    val volume: Float,
    val attendance: Int
)

// ── Day metrics ──

data class DayMetrics(
    val weekNumber: Int,
    val dayNumber: Int,
    val isRestDay: Boolean,
    val sessionsCompleted: Int,
    val sessionsPlanned: Int,
    val totalTrainingTime: Long,
    val averageFormScore: Float,
    val dayRating: String,
    val isComplete: Boolean,
    val sessions: List<SessionMetrics>?
)

// ── Session metrics ──

data class SessionMetrics(
    val sessionId: String,
    val weekNumber: Int,
    val dayNumber: Int,
    val completedAt: String?,
    val totalDurationMs: Long,
    val exercisesCompleted: Int,
    val exercisesTotal: Int,
    val totalSets: Int,
    val totalReps: Int,
    val averageAccuracy: Float,
    val averageFormScore: Float,
    val sessionRating: String,
    val strongestExercise: String?,
    val weakestExercise: String?,
    val exercises: List<ExerciseMetrics>?
)

// ── Exercise metrics ──

data class ExerciseMetrics(
    val exerciseSlug: String,
    val exerciseName: String,
    val averageFormScore: Float,
    val averageCompletionRate: Float,
    val totalVolume: Float,
    val sessionsCount: Int?,
    val setsCompleted: Int,
    val setsPlanned: Int,
    val totalReps: Int,
    val bestSetNumber: Int?,
    val dropOffRate: Float,
    val formRating: String,
    val sets: List<SetMetrics>?
)

// ── Set metrics ──

data class SetMetrics(
    val setNumber: Int,
    val exerciseSlug: String,
    val completionRate: Float,
    val averageFormScore: Float,
    val totalReps: Int,
    val repsTarget: Int,
    val durationMs: Long,
    val weightKg: Float?,
    val tut: Long,
    val fatigueIndex: Int?,
    val formConsistency: Float,
    val repDetails: List<RepMetrics>?
)

// ── Rep metrics ──

data class RepMetrics(
    val repNumber: Int,
    val formScore: Float,
    val worstState: Int,
    val isCounted: Boolean,
    val durationMs: Long
)

// ── Insight ──

data class InsightData(
    val type: String,
    val icon: String,
    val message: String
)

// ── Comparison data ──

data class ComparisonData(
    val previousFormScore: Float?,
    val previousVolume: Float?,
    val previousReps: Int?,
    val formScoreDelta: Float?,
    val volumeDelta: Float?,
    val repsDelta: Int?,
    val trendDirection: String?
)
