package com.movit.feature.train

enum class TrainDashboardStatus {
    ActivePlan,
    NoPlan,
    RestDay,
    CompletedToday,
    ProgramComplete,
}

enum class TrainWeekDayState {
    Done,
    Today,
    Planned,
    Rest,
    Missed,
}

data class TrainDashboardUi(
    val status: TrainDashboardStatus,
    val title: String,
    val subtitle: String,
    val program: TrainProgramUi?,
    val today: TrainTodayWorkoutUi?,
    val week: TrainWeekPreviewUi,
    val readiness: TrainReadinessUi,
    val report: TrainReportSummaryUi?,
    val quickActions: List<TrainQuickActionUi>,
)

data class TrainProgramUi(
    val name: String,
    val positionLabel: String,
    val levelLabel: String,
    val progressPercent: Int,
    val daysTrainedLabel: String,
    val streakLabel: String,
    val gradeLabel: String,
)

data class TrainTodayWorkoutUi(
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val exerciseCountLabel: String,
    val focusLabel: String,
    val primaryActionLabel: String,
)

data class TrainWeekPreviewUi(
    val title: String,
    val days: List<TrainWeekDayUi>,
)

data class TrainWeekDayUi(
    val label: String,
    val dayNumber: String,
    val state: TrainWeekDayState,
)

data class TrainReadinessUi(
    val title: String,
    val message: String,
    val scoreLabel: String,
    val progressPercent: Int,
    val guidanceLabel: String,
)

data class TrainReportSummaryUi(
    val title: String,
    val insight: String,
    val metrics: List<TrainMetricUi>,
)

data class TrainMetricUi(
    val label: String,
    val value: String,
)

data class TrainQuickActionUi(
    val id: String,
    val label: String,
    val description: String,
)
