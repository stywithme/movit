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

}
