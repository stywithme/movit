package com.movit.feature.train

object MovitTrainPreviewData {

    val week = TrainWeekPreviewUi(
        title = "June · Week 2",
        days = listOf(
            TrainWeekDayUi("Mon", "1", TrainWeekDayState.Done),
            TrainWeekDayUi("Tue", "2", TrainWeekDayState.Done),
            TrainWeekDayUi("Wed", "3", TrainWeekDayState.Missed),
            TrainWeekDayUi("Thu", "4", TrainWeekDayState.Today),
            TrainWeekDayUi("Fri", "5", TrainWeekDayState.Planned),
            TrainWeekDayUi("Sat", "6", TrainWeekDayState.Rest),
            TrainWeekDayUi("Sun", "7", TrainWeekDayState.Planned),
        ),
    )

    val program = TrainProgramUi(
        name = "Full Body 4-Week Challenge",
        positionLabel = "Week 2 of 4 · Day 3",
        levelLabel = "Beginner",
        progressPercent = 35,
        daysTrainedLabel = "12 days trained",
        streakLabel = "3 day streak",
        gradeLabel = "A",
    )

    val todayWorkout = TrainTodayWorkoutUi(
        title = "Lower Body Strength",
        subtitle = "Today · Day 3",
        durationLabel = "~22 min",
        exerciseCountLabel = "5 exercises",
        focusLabel = "Strength + control",
        primaryActionLabel = "Start workout",
    )

    val readiness = TrainReadinessUi(
        title = "Ready to train",
        message = "Your recent sessions show stable form. Keep the pace controlled today.",
        scoreLabel = "82%",
        progressPercent = 82,
        guidanceLabel = "Warm up hips and ankles before the first set.",
    )

    val report = TrainReportSummaryUi(
        title = "Program report",
        insight = "Form score is up 5% versus last week.",
        metrics = listOf(
            TrainMetricUi("Days", "12"),
            TrainMetricUi("Exercises", "86"),
            TrainMetricUi("Time", "6h"),
            TrainMetricUi("Accuracy", "85%"),
        ),
    )

    val quickActions = listOf(
        TrainQuickActionUi("explore", "Explore programs", "Find another program or light workout"),
        TrainQuickActionUi("reports", "View reports", "Review form score and recent progress"),
        TrainQuickActionUi("preferences", "Training preferences", "Goal and schedule tuning later"),
    )

    val activePlan = TrainDashboardUi(
        status = TrainDashboardStatus.ActivePlan,
        title = "Train",
        subtitle = "Your program and today's plan.",
        program = program,
        today = todayWorkout,
        week = week,
        readiness = readiness,
        report = report,
        quickActions = quickActions,
    )

    val noPlan = TrainDashboardUi(
        status = TrainDashboardStatus.NoPlan,
        title = "Train",
        subtitle = "Choose a program to build your plan.",
        program = null,
        today = null,
        week = TrainWeekPreviewUi(title = "This week", days = emptyList()),
        readiness = readiness.copy(
            title = "Start with a plan",
            message = "Pick a guided program and Movit will organize your training week.",
            scoreLabel = "New",
            progressPercent = 0,
            guidanceLabel = "Explore programs that match your level.",
        ),
        report = null,
        quickActions = quickActions,
    )

    val restDay = activePlan.copy(
        status = TrainDashboardStatus.RestDay,
        subtitle = "Recovery is part of the plan.",
        today = TrainTodayWorkoutUi(
            title = "Rest day",
            subtitle = "Recovery day",
            durationLabel = "0 min",
            exerciseCountLabel = "No planned workout",
            focusLabel = "Recovery + mobility",
            primaryActionLabel = "Explore light workout",
        ),
        readiness = readiness.copy(
            title = "Recovery recommended",
            message = "Your plan is intentionally light today. Mobility work is optional.",
            scoreLabel = "Recover",
            progressPercent = 64,
            guidanceLabel = "Short walk, stretching, and good sleep count today.",
        ),
    )

    val completedToday = activePlan.copy(
        status = TrainDashboardStatus.CompletedToday,
        subtitle = "Today's training is complete.",
        today = todayWorkout.copy(
            title = "Day complete",
            subtitle = "2 of 2 sessions completed",
            durationLabel = "38 min",
            exerciseCountLabel = "9 exercises",
            focusLabel = "88% form score",
            primaryActionLabel = "View report",
        ),
        readiness = readiness.copy(
            title = "Strong finish",
            message = "You completed today's work. The next useful step is reviewing the report.",
            scoreLabel = "Done",
            progressPercent = 100,
            guidanceLabel = "Check the report before changing tomorrow's load.",
        ),
    )

    val programComplete = activePlan.copy(
        status = TrainDashboardStatus.ProgramComplete,
        subtitle = "Program complete. Time to review and choose what is next.",
        today = TrainTodayWorkoutUi(
            title = "Program complete",
            subtitle = "Full Body 4-Week Challenge",
            durationLabel = "28 days",
            exerciseCountLabel = "92% avg accuracy",
            focusLabel = "Reassess or pick the next plan",
            primaryActionLabel = "View journey",
        ),
        readiness = readiness.copy(
            title = "Ready for the next block",
            message = "Use the journey report before selecting a harder program.",
            scoreLabel = "Level up",
            progressPercent = 100,
            guidanceLabel = "Reassessment should happen before a major intensity jump.",
        ),
    )
}
