package com.movit.core.network.dto

import kotlinx.serialization.Serializable

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
    val sets: List<SetMetricsDto>? = null,
)

@Serializable
data class SetMetricsDto(
    val setNumber: Int = 0,
    val averageFormScore: Float = 0f,
    val totalReps: Int = 0,
    val durationMs: Long = 0L,
    val fatigueIndex: Int? = null,
)
