package com.movit.feature.home

data class HomeMetricTileUi(
    val value: String,
    val label: String,
)

data class HomeLevelCardUi(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val progressPercent: Int,
)

data class HomeAlertUi(
    val title: String,
    val message: String,
)

data class HomeActiveProgramUi(
    val label: String,
    val title: String,
    val subtitle: String,
    val actionLabel: String,
)

data class HomeJourneyRowUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val tag: String? = null,
)

data class HomeActivityUi(
    val id: String,
    val title: String,
    val subtitle: String,
)

data class HomeTrainingPlanUi(
    val label: String,
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val exerciseCountLabel: String,
    val statusLabel: String,
    val primaryActionLabel: String = "Start workout",
)

data class HomeProgressUi(
    val weeklyCompletionPercent: Int,
    val streakDays: Int,
    val activeMinutesLabel: String,
    val formScoreLabel: String,
)

data class HomeReportPreviewUi(
    val title: String,
    val subtitle: String,
    val scoreLabel: String,
    val trendLabel: String,
)

data class HomeQuickActionUi(
    val id: String,
    val label: String,
    val description: String,
)

data class HomeDashboardUi(
    val userName: String,
    val greetingEyebrow: String,
    val greetingTitle: String,
    val greetingSubtitle: String,
    val metricTiles: List<HomeMetricTileUi>,
    val levelCard: HomeLevelCardUi?,
    val alert: HomeAlertUi?,
    val activeProgram: HomeActiveProgramUi?,
    val todayPlan: HomeTrainingPlanUi?,
    val showBodyScanCta: Boolean,
    val showNoProgramEmpty: Boolean,
    val journeyRows: List<HomeJourneyRowUi>,
    val recentActivities: List<HomeActivityUi>,
    val progress: HomeProgressUi,
    val reportPreview: HomeReportPreviewUi?,
    val quickActions: List<HomeQuickActionUi>,
    val insightMessage: String? = null,
)
