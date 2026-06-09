package com.movit.feature.reports

object MovitReportsPreviewData {

    val dashboardWithData = ReportsDashboardUi(
        hubState = ReportsHubState.Success,
        periodLabel = "Last 30 days",
        kpis = listOf(
            ReportKpiUi("18", "Days trained"),
            ReportKpiUi("1,240", "Total reps", highlighted = true),
            ReportKpiUi("8.2k", "Volume (kg)"),
            ReportKpiUi("14h", "Training time"),
        ),
        formScorePoints = listOf(72f, 75f, 78f, 82f, 85f, 88f, 90f),
        weeklyBarValues = listOf(45f, 78f, 55f, 92f, 40f),
        weeklyBarLabels = listOf("W1", "W2", "W3", "W4", "W5"),
        exercises = listOf(
            ReportExerciseUi("barbell-squat", "Barbell Squat", "92", "12 sessions", 92),
            ReportExerciseUi("romanian-deadlift", "Romanian Deadlift", "85", "8 sessions", 85),
            ReportExerciseUi("overhead-press", "Overhead Press", "71", "6 sessions", 71),
        ),
        trendInsight = ReportInsightUi(
            title = "Form improving",
            message = "+6% average form score over 4 weeks.",
        ),
        volumeBarValues = listOf(35f, 48f, 65f, 88f),
        volumeBarLabels = listOf("Jan", "Feb", "Mar", "Apr"),
        fatigueIndex = ReportFatigueUi(
            label = "FATIGUE INDEX",
            title = "Moderate",
            message = "Consider an extra rest day this week.",
        ),
    )

    val dashboardEmpty = ReportsDashboardUi(hubState = ReportsHubState.Empty)
    val dashboardLocked = ReportsDashboardUi(hubState = ReportsHubState.Locked)
}
