package com.movit.feature.train

enum class TrainDashboardStatus {
    ActivePlan,
    NoPlan,
    NoAssessment,
    ReassessmentDue,
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

data class TrainFeaturedProgramUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String?,
    val metadata: List<String>,
    val imageUrl: String? = null,
    val levelLabel: String? = null,
    val durationWeeksLabel: String? = null,
)

data class TrainDashboardUi(
    val status: TrainDashboardStatus,
    val title: String,
    val subtitle: String,
    val program: TrainProgramUi?,
    val today: TrainTodayWorkoutUi?,
    val week: TrainWeekPreviewUi,
    val weekOptions: List<TrainWeekPreviewUi> = emptyList(),
    val readiness: TrainReadinessUi,
    val report: TrainReportSummaryUi?,
    val quickActions: List<TrainQuickActionUi>,
    val featuredPrograms: List<TrainFeaturedProgramUi> = emptyList(),
)

data class TrainProgramUi(
    val id: String = "",
    val slug: String = "",
    val weekNumber: Int = 1,
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
    val sessions: List<TrainWorkoutSessionUi> = emptyList(),
)

data class TrainWorkoutSessionUi(
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val exerciseCountLabel: String,
    val actionLabel: String,
    val isCompleted: Boolean = false,
    val launchTarget: TrainWorkoutLaunchUi? = null,
    val items: List<TrainWorkoutItemUi> = emptyList(),
    val thumbnailUrl: String? = null,
)

data class TrainWorkoutLaunchUi(
    val programSlug: String,
    val programId: String,
    val weekNumber: Int,
    val dayNumber: Int,
    val plannedWorkoutId: String,
)

data class TrainWorkoutItemUi(
    val typeLabel: String,
    val title: String,
    val subtitle: String,
    val isRest: Boolean = false,
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
    val trendChartPoints: List<Float> = emptyList(),
    val trendDeltaPercent: Int? = null,
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
