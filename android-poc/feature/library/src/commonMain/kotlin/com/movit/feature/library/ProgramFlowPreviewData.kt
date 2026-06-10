package com.movit.feature.library

object ProgramFlowPreviewData {

    val programs: List<ProgramListItemUi> = listOf(
        ProgramListItemUi(
            id = "prog-full-body",
            slug = "full-body-4-week",
            title = "Full Body 4-Week",
            description = "Balanced strength for all major muscle groups.",
            imageUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=200&q=70",
            badge = "Active",
            levelLabel = "Beginner",
            durationWeeks = 4,
            daysPerWeek = 5,
            isActive = true,
        ),
        ProgramListItemUi(
            id = "program-starter",
            slug = "mobility-starter",
            title = "Mobility Starter",
            description = "Gentle plan for safe movement.",
            imageUrl = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=200&q=70",
            badge = "Beginner",
            levelLabel = "Beginner",
            durationWeeks = 3,
            daysPerWeek = 4,
        ),
        ProgramListItemUi(
            id = "prog-strength",
            slug = "strength-builder",
            title = "Strength Builder",
            description = "Progressive overload for intermediate lifters.",
            imageUrl = "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=200&q=70",
            levelLabel = "Intermediate",
            durationWeeks = 6,
            daysPerWeek = 4,
        ),
    )

    fun weekPlan(programId: String, weekNumber: Int): ProgramWeekPlanUi {
        val program = programs.firstOrNull { it.id == programId } ?: programs.first()
        val days = when (weekNumber) {
            2 -> listOf(
                day(1, "Lower Body A", "Completed · 92% form", ProgramFlowDayStatus.Done, 6, 32, "pw-lower-a"),
                day(2, "Upper Push", "Completed", ProgramFlowDayStatus.Done, 5, 28, "pw-upper-push"),
                day(3, "Upper Body Strength", "Today · 5 exercises · 38 min", ProgramFlowDayStatus.Today, 5, 38, "pw-upper-strength"),
                day(4, "Rest / Mobility", "Optional light session", ProgramFlowDayStatus.Rest, isRestDay = true),
                day(5, "Full Body B", "Scheduled Sat", ProgramFlowDayStatus.Planned, 6, 35, "pw-full-b"),
            )
            else -> listOf(
                day(1, "Foundation A", "Completed", ProgramFlowDayStatus.Done, 5, 30, "pw-foundation-a"),
                day(2, "Foundation B", "Completed", ProgramFlowDayStatus.Done, 5, 28, "pw-foundation-b"),
                day(3, "Active recovery", "Optional mobility", ProgramFlowDayStatus.Rest, isRestDay = true),
                day(4, "Full Body Intro", "Today · 4 exercises · 25 min", ProgramFlowDayStatus.Today, 4, 25, "pw-intro"),
                day(5, "Core + stability", "Scheduled", ProgramFlowDayStatus.Planned, 4, 22, "pw-core"),
            )
        }
        return ProgramWeekPlanUi(
            programId = program.id,
            programSlug = program.slug,
            programName = program.title,
            weekNumber = weekNumber,
            weekTitle = "Week $weekNumber",
            weekSubtitle = "${days.count { !it.isRestDay }} workouts · ${days.count { it.isRestDay }} rest",
            days = days,
            todayDayNumber = days.firstOrNull { it.status == ProgramFlowDayStatus.Today }?.dayNumber,
        )
    }

    fun weeklyReport(programId: String, weekNumber: Int): WeeklyReportUi {
        val program = programs.firstOrNull { it.id == programId } ?: programs.first()
        return WeeklyReportUi(
            programId = program.id,
            programSlug = program.slug,
            programName = program.title,
            weekNumber = weekNumber,
            heroEyebrow = "Week $weekNumber summary",
            heroTitle = "Great week!",
            heroSubtitle = "You completed 4 of 5 planned sessions.",
            sessionsCompleted = 4,
            sessionsPlanned = 5,
            avgFormPercent = 86,
            totalReps = 2100,
            dailyScores = listOf(
                WeeklyReportDayScoreUi("M", 88),
                WeeklyReportDayScoreUi("T", 92),
                WeeklyReportDayScoreUi("W", 0),
                WeeklyReportDayScoreUi("T", 78),
                WeeklyReportDayScoreUi("F", 85),
            ),
        )
    }

    private fun day(
        dayNumber: Int,
        title: String,
        subtitle: String,
        status: ProgramFlowDayStatus,
        exerciseCount: Int = 0,
        durationMinutes: Int? = null,
        plannedWorkoutId: String? = null,
        isRestDay: Boolean = false,
    ) = ProgramFlowDayUi(
        dayNumber = dayNumber,
        title = title,
        subtitle = subtitle,
        status = status,
        exerciseCount = exerciseCount,
        durationMinutes = durationMinutes,
        plannedWorkoutId = plannedWorkoutId,
        isRestDay = isRestDay,
    )
}
