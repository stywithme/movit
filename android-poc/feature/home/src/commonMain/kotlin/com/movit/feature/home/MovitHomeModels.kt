package com.movit.feature.home

data class HomeTrainingPlanUi(
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val exerciseCountLabel: String,
    val statusLabel: String,
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
    val greetingTitle: String,
    val greetingSubtitle: String,
    val todayPlan: HomeTrainingPlanUi?,
    val progress: HomeProgressUi,
    val reportPreview: HomeReportPreviewUi?,
    val quickActions: List<HomeQuickActionUi>,
    val insightMessage: String?,
)
