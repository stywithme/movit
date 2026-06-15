package com.movit.feature.train

object MovitTrainPreviewData {

    val week1 = TrainWeekPreviewUi(
        title = "Week 1",
        weekNumber = 1,
        subtitle = "4 of 4 workouts",
        days = listOf(
            TrainWeekDayUi("Mon", "1", TrainWeekDayState.Completed),
            TrainWeekDayUi("Tue", "2", TrainWeekDayState.Completed),
            TrainWeekDayUi("Wed", "3", TrainWeekDayState.Rest),
            TrainWeekDayUi("Thu", "4", TrainWeekDayState.Completed),
            TrainWeekDayUi("Fri", "5", TrainWeekDayState.Completed),
            TrainWeekDayUi("Sat", "6", TrainWeekDayState.Rest),
            TrainWeekDayUi("Sun", "7", TrainWeekDayState.Rest),
        ),
    )

    val week = TrainWeekPreviewUi(
        title = "Week 2",
        weekNumber = 2,
        isCurrentWeek = true,
        subtitle = "2 of 4 workouts",
        days = listOf(
            TrainWeekDayUi("Mon", "1", TrainWeekDayState.Completed),
            TrainWeekDayUi("Tue", "2", TrainWeekDayState.Completed),
            TrainWeekDayUi("Wed", "3", TrainWeekDayState.Rest),
            TrainWeekDayUi(
                "Thu", "4", TrainWeekDayState.Today, isToday = true,
                detail = TrainWeekDayDetailUi(
                    title = "Lower Body Strength",
                    infoLabel = "5 exercises · ~22 min",
                    statusLabel = "Today",
                    isWorkout = true,
                    actionLabel = "Start session",
                ),
            ),
            TrainWeekDayUi("Fri", "5", TrainWeekDayState.Upcoming),
            TrainWeekDayUi("Sat", "6", TrainWeekDayState.Rest),
            TrainWeekDayUi("Sun", "7", TrainWeekDayState.Upcoming),
        ),
    )

    val week3 = TrainWeekPreviewUi(
        title = "Week 3",
        weekNumber = 3,
        subtitle = "0 of 4 workouts",
        days = listOf(
            TrainWeekDayUi("Mon", "1", TrainWeekDayState.Upcoming),
            TrainWeekDayUi("Tue", "2", TrainWeekDayState.Upcoming),
            TrainWeekDayUi("Wed", "3", TrainWeekDayState.Rest),
            TrainWeekDayUi("Thu", "4", TrainWeekDayState.Upcoming),
            TrainWeekDayUi("Fri", "5", TrainWeekDayState.Upcoming),
            TrainWeekDayUi("Sat", "6", TrainWeekDayState.Rest),
            TrainWeekDayUi("Sun", "7", TrainWeekDayState.Upcoming),
        ),
    )

    val weekOptions = listOf(week1, week, week3)

    val program = TrainProgramUi(
        id = "prog-full-body",
        slug = "full-body-4-week",
        weekNumber = 2,
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
        sessions = listOf(
            TrainWorkoutSessionUi(
                title = "Lower Body Strength",
                subtitle = "5 exercises",
                durationLabel = "~22 min",
                exerciseCountLabel = "5 exercises",
                actionLabel = "Start session",
                thumbnailUrl = "https://images.unsplash.com/photo-1574680096145-d05b474e2155?auto=format&fit=crop&w=200&q=70",
                items = listOf(
                    TrainWorkoutItemUi("EX", "Squat", "3 sets · 12 reps"),
                    TrainWorkoutItemUi("REST", "Rest", "60 sec", isRest = true),
                    TrainWorkoutItemUi("EX", "Lunge", "3 sets · 10 reps"),
                    TrainWorkoutItemUi("EX", "Glute bridge", "3 sets · 14 reps"),
                ),
            ),
            TrainWorkoutSessionUi(
                title = "Mobility Flow",
                subtitle = "4 exercises",
                durationLabel = "~12 min",
                exerciseCountLabel = "4 exercises",
                actionLabel = "Start session",
                thumbnailUrl = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=200&q=70",
                items = listOf(
                    TrainWorkoutItemUi("EX", "Hip opener", "2 sets · 30 sec"),
                    TrainWorkoutItemUi("EX", "Ankle rocks", "2 sets · 12 reps"),
                    TrainWorkoutItemUi("REST", "Breathing reset", "45 sec", isRest = true),
                ),
            ),
        ),
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
        insight = "Accuracy is up 5% versus last week.",
        metrics = listOf(
            TrainMetricUi("Days", "12"),
            TrainMetricUi("Exercises", "86"),
            TrainMetricUi("Time", "6h"),
            TrainMetricUi("Accuracy", "85%"),
        ),
        trendChartPoints = listOf(0.58f, 0.52f, 0.40f, 0.44f, 0.30f, 0.26f, 0.18f),
        trendDeltaPercent = 5,
    )

    val featuredPrograms = listOf(
        TrainFeaturedProgramUi(
            id = "mobility-starter",
            title = "Mobility Starter",
            subtitle = "A gentle 4-week plan to build safe, controlled movement and flexibility.",
            badge = "★ Featured",
            metadata = listOf("12 sessions", "3 / week"),
            imageUrl = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=800&q=70",
            levelLabel = "Beginner",
            durationWeeksLabel = "4 weeks",
        ),
        TrainFeaturedProgramUi(
            id = "full-body-strength",
            title = "Full Body Strength",
            subtitle = "Build strength across all major muscle groups with guided form checks.",
            badge = null,
            metadata = listOf("24 sessions", "4 / week"),
            imageUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=800&q=70",
            levelLabel = "Intermediate",
            durationWeeksLabel = "6 weeks",
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
        weekOptions = weekOptions,
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
        featuredPrograms = featuredPrograms,
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
            sessions = todayWorkout.sessions.map { session ->
                session.copy(
                    isCompleted = true,
                    actionLabel = "View summary",
                )
            },
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
