package com.movit.feature.home

object MovitHomePreviewData {

    val activePlan = HomeTrainingPlanUi(
        title = "Upper Body Strength",
        subtitle = "Week 2 · Day 3",
        durationLabel = "~38 min",
        exerciseCountLabel = "5 exercises",
        statusLabel = "Workout 1 of 2 today",
    )

    val progress = HomeProgressUi(
        weeklyCompletionPercent = 71,
        streakDays = 12,
        activeMinutesLabel = "124 min",
        formScoreLabel = "87%",
    )

    val reportPreview = HomeReportPreviewUi(
        title = "Last session",
        subtitle = "Upper Body Strength",
        scoreLabel = "87%",
        trendLabel = "+4% vs last week",
    )

    val quickActions = listOf(
        HomeQuickActionUi("train", "Train", "Start or resume training"),
        HomeQuickActionUi("explore", "Explore", "Browse workouts and exercises"),
        HomeQuickActionUi("reports", "Reports", "Review your progress"),
        HomeQuickActionUi("profile", "Profile", "Account and preferences"),
    )

    val dashboardWithPlan = HomeDashboardUi(
        greetingTitle = "Good morning",
        greetingSubtitle = "Ready for today's session?",
        todayPlan = activePlan,
        progress = progress,
        reportPreview = reportPreview,
        quickActions = quickActions,
        insightMessage = "Training plan adjusted — 2 exercises updated from your last session.",
    )

    val dashboardNoPlan = HomeDashboardUi(
        greetingTitle = "Good morning",
        greetingSubtitle = "Let's find your next workout.",
        todayPlan = null,
        progress = progress.copy(weeklyCompletionPercent = 24, streakDays = 2),
        reportPreview = null,
        quickActions = quickActions,
        insightMessage = null,
    )
}
