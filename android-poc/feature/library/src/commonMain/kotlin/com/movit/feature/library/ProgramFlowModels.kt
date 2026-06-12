package com.movit.feature.library

enum class ProgramFlowDayStatus {
    Done,
    Today,
    Planned,
    Rest,
}

data class ProgramListItemUi(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
    val badge: String? = null,
    val levelLabel: String,
    val durationWeeks: Int,
    val daysPerWeek: Int,
    val isActive: Boolean = false,
)

data class ProgramFlowDayUi(
    val dayNumber: Int,
    val title: String,
    val subtitle: String,
    val status: ProgramFlowDayStatus,
    val exerciseCount: Int = 0,
    val durationMinutes: Int? = null,
    val plannedWorkoutId: String? = null,
    val isRestDay: Boolean = false,
)

data class ProgramWeekPlanUi(
    val programId: String,
    val programSlug: String,
    val programName: String,
    val weekNumber: Int,
    val weekTitle: String,
    val weekSubtitle: String,
    val days: List<ProgramFlowDayUi>,
    val todayDayNumber: Int?,
)

data class WeeklyReportDayScoreUi(
    val label: String,
    val scorePercent: Int,
)

data class WeeklyReportWeekSummaryUi(
    val weekNumber: Int,
    val title: String,
    val progressPercent: Int,
    val sessionsCompleted: Int,
    val sessionsPlanned: Int,
    val avgFormPercent: Int,
    val totalReps: Int,
    val message: String,
)

data class WeeklyReportUi(
    val programId: String,
    val programSlug: String,
    val programName: String,
    val weekNumber: Int,
    val heroEyebrow: String,
    val heroTitle: String,
    val heroSubtitle: String,
    val sessionsCompleted: Int,
    val sessionsPlanned: Int,
    val avgFormPercent: Int,
    val totalReps: Int,
    val dailyScores: List<WeeklyReportDayScoreUi>,
    val weekSummaries: List<WeeklyReportWeekSummaryUi> = emptyList(),
)
