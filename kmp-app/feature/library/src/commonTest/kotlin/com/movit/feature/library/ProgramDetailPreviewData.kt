package com.movit.feature.library

import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi

internal object ProgramDetailPreviewData {

    private val weekThemes = listOf("Foundation", "Control", "Build", "Retest")

    fun weeksFor(programId: String, durationWeeks: Int, weeklyTarget: Int): List<ProgramWeekUi> {
        val count = durationWeeks.coerceIn(1, 12)
        return (1..count).map { weekNumber ->
            val theme = weekThemes.getOrElse(weekNumber - 1) { "Progressive load" }
            val days = if (weekNumber == 1) weekOneDays(programId) else lockedWeekDays(weekNumber, weeklyTarget)
            val progress = if (weekNumber == 1) 48 else 0
            val sessions = days.count { it.status != ProgramDayStatus.Rest }
            val restDays = days.count { it.status == ProgramDayStatus.Rest }
            ProgramWeekUi(
                weekNumber = weekNumber,
                label = "Week $weekNumber",
                theme = theme,
                subtitle = "$sessions sessions · $restDays rest days · ${if (weekNumber == 1) "$progress% complete" else "locked until week ${weekNumber - 1}"}",
                progressPercent = progress,
                isCurrent = weekNumber == 1,
                isLocked = weekNumber > 1,
                days = days,
            )
        }
    }

    fun nextSession(programId: String): ProgramNextSessionUi? {
        val week = weeksFor(programId, durationWeeks = 4, weeklyTarget = 3).firstOrNull() ?: return null
        val day = week.days.firstOrNull { it.status == ProgramDayStatus.Next } ?: return null
        val workoutId = day.plannedWorkoutId ?: "main-flow"
        return ProgramNextSessionUi(
            weekNumber = week.weekNumber,
            dayNumber = day.dayNumber,
            title = "Week ${week.weekNumber} · Day ${day.dayNumber}",
            subtitle = "Next session ready",
            sessionWorkoutId = WorkoutSessionKeys.encode(
                programId = programId,
                weekNumber = week.weekNumber,
                dayNumber = day.dayNumber,
                plannedWorkoutId = workoutId,
            ),
        )
    }

    fun sampleProgram(): ExploreItemUi = ExploreItemUi(
        id = "program-starter",
        title = "Starter Strength Plan",
        subtitle = "Four-week progression for full-body confidence.",
        type = ExploreItemType.Program,
        badge = "Featured",
        metadata = listOf("4 weeks", "3 days/week", "Beginner"),
    )

    private fun weekOneDays(programId: String): List<ProgramDayUi> = listOf(
        ProgramDayUi(
            dayNumber = 1,
            title = "Day 1 · Mobility assessment",
            meta = "1 session · 5 exercises · Completed",
            status = ProgramDayStatus.Done,
            sessionCount = 1,
            exerciseCount = 5,
            plannedWorkoutId = "assessment",
        ),
        ProgramDayUi(
            dayNumber = 2,
            title = "Day 2 · Hips and spine control",
            meta = "2 sessions · 8 exercises · 25 min",
            status = ProgramDayStatus.Next,
            sessionCount = 2,
            exerciseCount = 8,
            plannedWorkoutId = "main-flow",
        ),
        ProgramDayUi(
            dayNumber = 3,
            title = "Recovery day",
            meta = "Rest day · Calendar kept open",
            status = ProgramDayStatus.Rest,
        ),
        ProgramDayUi(
            dayNumber = 4,
            title = "Day 4 · Lower body mobility",
            meta = "6 exercises · ~24 min",
            status = ProgramDayStatus.Upcoming,
            sessionCount = 1,
            exerciseCount = 6,
            plannedWorkoutId = "lower-body",
        ),
        ProgramDayUi(
            dayNumber = 5,
            title = "Day 5 · Posture reset",
            meta = "5 exercises · ~20 min",
            status = ProgramDayStatus.Upcoming,
            sessionCount = 1,
            exerciseCount = 5,
            plannedWorkoutId = "posture",
        ),
    )

    private fun lockedWeekDays(weekNumber: Int, weeklyTarget: Int): List<ProgramDayUi> =
        (1..weeklyTarget.coerceAtLeast(2)).map { index ->
            ProgramDayUi(
                dayNumber = index,
                title = when (index) {
                    1 -> "Lower body mobility"
                    else -> "Posture reset"
                },
                meta = "${4 + index} exercises · ~${20 + index * 2} min",
                status = ProgramDayStatus.Upcoming,
                sessionCount = 1,
                exerciseCount = 4 + index,
                plannedWorkoutId = "week-$weekNumber-day-$index",
            )
        }
}
