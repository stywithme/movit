package com.movit.feature.reports

enum class ReportsHubState {
    Loading,
    Locked,
    Empty,
    Success,
    Error,
}

enum class ReportsTab {
    Overview,
    Exercises,
    Trends,
}

data class ReportKpiUi(
    val value: String,
    val label: String,
    val highlighted: Boolean = false,
)

data class ReportExerciseUi(
    val id: String,
    val name: String,
    val scoreLabel: String,
    val sessionsLabel: String,
    val scorePercent: Int,
)

data class ReportInsightUi(
    val title: String,
    val message: String,
)

data class ReportFatigueUi(
    val label: String,
    val title: String,
    val message: String,
)

data class ReportsDashboardUi(
    val hubState: ReportsHubState,
    val periodLabel: String = "Last 30 days",
    val kpis: List<ReportKpiUi> = emptyList(),
    val formScorePoints: List<Float> = emptyList(),
    val weeklyBarValues: List<Float> = emptyList(),
    val weeklyBarLabels: List<String> = emptyList(),
    val exercises: List<ReportExerciseUi> = emptyList(),
    val trendInsight: ReportInsightUi? = null,
    val improvementRatePercent: Float? = null,
    val volumeBarValues: List<Float> = emptyList(),
    val volumeBarLabels: List<String> = emptyList(),
    val fatigueIndex: ReportFatigueUi? = null,
    val errorMessage: String? = null,
)
