package com.movit.core.network.dto

import kotlinx.serialization.Serializable

/** Backend `scope` query param for `GET /api/mobile/reports/metrics`. */
enum class MetricsScope(val wireValue: String) {
    Program("program"),
    Week("week"),
    Day("day"),
    PlannedWorkout("plannedWorkout"),
    Exercise("exercise"),
}

data class MetricsQuery(
    val programId: String,
    val scope: MetricsScope,
    val weekNumber: Int? = null,
    val dayNumber: Int? = null,
    val plannedWorkoutId: String? = null,
    val exerciseSlug: String? = null,
    val includeHistory: Boolean = false,
    val includeChildren: Boolean = false,
)

@Serializable
data class ReportsDashboardApiResponse(
    val success: Boolean = false,
    val scope: String = "",
    val period: String? = null,
    val source: String? = null,
    val summary: ReportDashboardSummaryDto? = null,
    val trends: ReportDashboardTrendsDto? = null,
    val exerciseBreakdown: List<ReportDashboardExerciseDto>? = null,
    val insights: List<ReportInsightDto>? = null,
    val error: String? = null,
)

@Serializable
data class ReportDashboardSummaryDto(
    val programId: String? = null,
    val programProgress: Float? = null,
    val overallFormScore: Float? = null,
    val totalReps: Int? = null,
    val totalVolume: Float? = null,
    val totalTrainingTime: Long? = null,
    val daysTrained: Int? = null,
    val currentStreak: Int? = null,
    val programGrade: String? = null,
    val strongestExercise: String? = null,
    val weakestExercise: String? = null,
)

@Serializable
data class ReportDashboardTrendsDto(
    val formScoreByWeek: List<Float>? = null,
    val attendanceByWeek: List<Int>? = null,
    val volumeByWeek: List<Float>? = null,
    val repsByWeek: List<Int>? = null,
)

@Serializable
data class ReportDashboardExerciseDto(
    val exerciseSlug: String = "",
    val exerciseName: String = "",
    val averageFormScore: Float = 0f,
    val workoutsCount: Int = 0,
    val totalReps: Int = 0,
    val totalVolume: Float = 0f,
    val focusArea: String = "",
)

@Serializable
data class ReportInsightDto(
    val type: String = "",
    val icon: String = "",
    val message: String = "",
)

@Serializable
data class MetricsApiResponse(
    val success: Boolean = false,
    val scope: String = "",
    val summary: ExerciseMetricsSummaryDto? = null,
    val insights: List<ReportInsightDto>? = null,
    val error: String? = null,
)

@Serializable
data class JointMetricsDto(
    val jointCode: String = "",
    val jointName: String = "",
    val score: Float = 0f,
)

@Serializable
data class ExerciseMetricsSummaryDto(
    val exerciseSlug: String? = null,
    val exerciseName: String? = null,
    val averageFormScore: Float? = null,
    val averageCompletionRate: Float? = null,
    val setsCompleted: Int? = null,
    val setsPlanned: Int? = null,
    val totalReps: Int? = null,
    val totalDurationMs: Long? = null,
    val bestSetNumber: Int? = null,
    val dropOffRate: Float? = null,
    val formRating: String? = null,
    val jointBreakdown: List<JointMetricsDto>? = null,
    val sets: List<SetMetricsDto>? = null,
    // Program scope
    val programId: String? = null,
    val programProgress: Float? = null,
    val daysTrained: Int? = null,
    val totalDays: Int? = null,
    val totalTrainingTime: Long? = null,
    val totalVolume: Float? = null,
    val overallFormScore: Float? = null,
    val currentStreak: Int? = null,
    val programGrade: String? = null,
    // Week scope
    val weekNumber: Int? = null,
    val daysTotal: Int? = null,
    val consistencyScore: Float? = null,
    val formScoreTrend: List<Float>? = null,
    // Day scope
    val dayNumber: Int? = null,
    val isRestDay: Boolean? = null,
    val workoutsCompleted: Int? = null,
    val workoutsPlanned: Int? = null,
    val isComplete: Boolean? = null,
    val dayRating: String? = null,
    // Planned workout scope
    val plannedWorkoutId: String? = null,
    val completedAt: String? = null,
    val exercisesCompleted: Int? = null,
    val exercisesTotal: Int? = null,
    val totalSets: Int? = null,
    val averageAccuracy: Float? = null,
    val workoutRating: String? = null,
    val strongestExercise: String? = null,
    val weakestExercise: String? = null,
)

@Serializable
data class SetMetricsDto(
    val setNumber: Int = 0,
    val averageFormScore: Float = 0f,
    val totalReps: Int = 0,
    val durationMs: Long = 0L,
    val fatigueIndex: Int? = null,
)
