package com.movit.feature.home

object MovitHomePreviewData {

    val activePlan = HomeTrainingPlanUi(
        label = "WEEK 2 · DAY 3",
        title = "Upper Body Strength",
        subtitle = "5 exercises · ~38 min",
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

    val metricTiles = listOf(
        HomeMetricTileUi("5", "This week"),
        HomeMetricTileUi("87%", "Form avg"),
        HomeMetricTileUi("12", "Day streak"),
    )

    val levelCard = HomeLevelCardUi(
        eyebrow = "Your level",
        title = "Level 2 · Building",
        subtitle = "Body score 65 · 8 pts to Level 3",
        progressPercent = 62,
    )

    val alert = HomeAlertUi(
        title = "Training plan adjusted",
        message = "2 exercises updated based on your last session performance.",
    )

    val activeProgram = HomeActiveProgramUi(
        label = "CURRENT PLAN",
        title = "Full Body 4-Week Challenge",
        subtitle = "Week 2 of 4 · 5 workouts / week",
        actionLabel = "View program",
    )

    val journeyRows = listOf(
        HomeJourneyRowUi("timeline", "Plan timeline", "2 completed · 1 active · 1 upcoming"),
        HomeJourneyRowUi("reassessment", "Reassessment due", "Week 4 · progression check", tag = "Soon"),
    )

    val recentActivities = listOf(
        HomeActivityUi("1", "Squat · 92% form", "Yesterday · 4 sets · 48 reps"),
        HomeActivityUi("2", "Weekly volume +12%", "Compared to last week"),
    )

    val quickActions = listOf(
        HomeQuickActionUi("explore", "Explore", "Browse workouts and exercises"),
        HomeQuickActionUi("reports", "Reports", "Review your progress"),
    )

    val dashboardWithPlan = HomeDashboardUi(
        userName = "Mahmoud",
        greetingEyebrow = "Good morning",
        greetingTitle = "Mahmoud",
        greetingSubtitle = "Ready for today's session?",
        metricTiles = metricTiles,
        levelCard = levelCard,
        alert = alert,
        activeProgram = activeProgram,
        todayPlan = activePlan,
        showBodyScanCta = false,
        showNoProgramEmpty = false,
        journeyRows = journeyRows,
        recentActivities = recentActivities,
        progress = progress,
        reportPreview = HomeReportPreviewUi(
            title = "Last session",
            subtitle = "Upper Body Strength",
            scoreLabel = "87%",
            trendLabel = "+4% vs last week",
        ),
        quickActions = quickActions,
    )

    val dashboardNoPlan = HomeDashboardUi(
        userName = "Mahmoud",
        greetingEyebrow = "Good morning",
        greetingTitle = "Mahmoud",
        greetingSubtitle = "Let's find your next workout.",
        metricTiles = metricTiles,
        levelCard = levelCard,
        alert = null,
        activeProgram = null,
        todayPlan = null,
        showBodyScanCta = false,
        showNoProgramEmpty = true,
        journeyRows = emptyList(),
        recentActivities = emptyList(),
        progress = progress.copy(weeklyCompletionPercent = 24, streakDays = 2),
        reportPreview = null,
        quickActions = quickActions,
    )
}
