package com.movit.feature.library

import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.ProgramExportDayDto
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.network.dto.ProgramExportWeekDto
import com.movit.resources.strings.ProgramFlowStrings
import kotlin.math.roundToInt

internal object ProgramDetailApiMapper {

    suspend fun mapWeeks(
        program: ProgramExportDto,
        home: HomeDataDto?,
        language: String,
        strings: ProgramFlowStrings,
    ): List<ProgramWeekUi> {
        val active = home?.trainMode?.activeProgram
        val isActiveProgram = active?.id == program.id || active?.id == program.slug
        val activeWeek = active?.weekNumber?.coerceAtLeast(1) ?: 1
        val completedDays = active?.weekProgress?.completed ?: 0

        return program.weeks.sortedBy { it.weekNumber }.map { week ->
            val isCurrent = isActiveProgram && week.weekNumber == activeWeek
            val isLocked = isActiveProgram && week.weekNumber > activeWeek
            val days = week.days.sortedBy { it.dayNumber }.map { day ->
                mapDay(
                    day = day,
                    weekNumber = week.weekNumber,
                    language = language,
                    strings = strings,
                    isActiveProgram = isActiveProgram,
                    activeWeek = activeWeek,
                    activeDay = active?.dayNumber ?: 1,
                    completedDays = completedDays,
                    isTodayRestDay = home?.trainMode?.status == "rest_day" && isCurrent,
                )
            }
            val sessions = days.count { it.status != ProgramDayStatus.Rest }
            val restDays = days.count { it.status == ProgramDayStatus.Rest }
            val progress = if (isCurrent && sessions > 0) {
                ((days.count { it.status == ProgramDayStatus.Done } * 100f) / sessions)
                    .roundToInt()
                    .coerceIn(0, 100)
            } else {
                0
            }
            val theme = week.target?.localized(language)?.takeIf { it.isNotBlank() }
                ?: strings.weekTitle(week.weekNumber)
            ProgramWeekUi(
                weekNumber = week.weekNumber,
                label = strings.weekTitle(week.weekNumber),
                theme = theme,
                subtitle = when {
                    isLocked -> "locked until week ${week.weekNumber - 1}"
                    isCurrent -> "$sessions sessions · $restDays rest days · $progress% complete"
                    else -> "$sessions sessions · $restDays rest days"
                },
                progressPercent = progress,
                isCurrent = isCurrent,
                isLocked = isLocked,
                days = days,
            )
        }
    }

    suspend fun nextSession(
        program: ProgramExportDto,
        home: HomeDataDto?,
        language: String,
        strings: ProgramFlowStrings,
    ): ProgramNextSessionUi? {
        val active = home?.trainMode?.activeProgram ?: return null
        if (active.id != program.id && active.id != program.slug) return null

        return sessionForPosition(
            program = program,
            weekNumber = active.weekNumber.coerceAtLeast(1),
            dayNumber = active.dayNumber.coerceAtLeast(1),
            language = language,
            strings = strings,
        )
    }

    suspend fun previewNextSession(
        program: ProgramExportDto,
        language: String,
        strings: ProgramFlowStrings,
    ): ProgramNextSessionUi? {
        val week = program.weeks.minByOrNull { it.weekNumber } ?: return null
        val day = week.days
            .sortedBy { it.dayNumber }
            .firstOrNull { !it.isRestDay && it.plannedWorkouts.isNotEmpty() }
            ?: return null
        return sessionForPosition(
            program = program,
            weekNumber = week.weekNumber,
            dayNumber = day.dayNumber,
            language = language,
            strings = strings,
        )
    }

    private suspend fun sessionForPosition(
        program: ProgramExportDto,
        weekNumber: Int,
        dayNumber: Int,
        language: String,
        strings: ProgramFlowStrings,
    ): ProgramNextSessionUi? {
        val week = program.weeks.firstOrNull { it.weekNumber == weekNumber } ?: return null
        val day = week.days
            .sortedBy { it.dayNumber }
            .firstOrNull { candidate ->
                !candidate.isRestDay &&
                    candidate.dayNumber >= dayNumber.coerceAtLeast(1) &&
                    candidate.plannedWorkouts.isNotEmpty()
            }
            ?: week.days.firstOrNull { !it.isRestDay && it.plannedWorkouts.isNotEmpty() }
            ?: return null

        val workout = day.plannedWorkouts.first()
        val workoutId = workout.id.takeIf { it.isNotBlank() } ?: return null
        return ProgramNextSessionUi(
            weekNumber = weekNumber,
            dayNumber = day.dayNumber,
            title = strings.weekTitle(weekNumber) + " · " + strings.dayShort(day.dayNumber),
            subtitle = workout.name.localized(language).ifBlank { "Next session ready" },
            sessionWorkoutId = WorkoutSessionKeys.encode(
                programId = program.id,
                weekNumber = weekNumber,
                dayNumber = day.dayNumber,
                plannedWorkoutId = workoutId,
            ),
        )
    }

    private suspend fun mapDay(
        day: ProgramExportDayDto,
        weekNumber: Int,
        language: String,
        strings: ProgramFlowStrings,
        isActiveProgram: Boolean,
        activeWeek: Int,
        activeDay: Int,
        completedDays: Int,
        isTodayRestDay: Boolean,
    ): ProgramDayUi {
        val flowStatus = resolveFlowDayStatus(
            dayNumber = day.dayNumber,
            isRestDay = day.isRestDay,
            isActiveProgram = isActiveProgram,
            activeWeek = activeWeek,
            viewingWeek = weekNumber,
            activeDay = activeDay,
            completedDays = completedDays,
            isTodayRestDay = isTodayRestDay,
        )
        val workout = day.plannedWorkouts.firstOrNull()
        val title = when {
            day.isRestDay -> strings.daySubtitleRest
            workout != null -> workout.name.localized(language).ifBlank { strings.dayShort(day.dayNumber) }
            else -> strings.dayShort(day.dayNumber)
        }
        val exerciseCount = workout?.let { 1 } ?: 0
        val durationMinutes = workout?.estimatedDurationMin
        val meta = when (flowStatus) {
            ProgramFlowDayStatus.Done -> "Completed"
            ProgramFlowDayStatus.Today -> buildString {
                append("1 session · $exerciseCount exercises")
                durationMinutes?.let { append(" · $it min") }
            }
            ProgramFlowDayStatus.Rest -> "Rest day"
            ProgramFlowDayStatus.Planned -> buildString {
                append("$exerciseCount exercises")
                durationMinutes?.let { append(" · ~$it min") }
            }.ifBlank { "Scheduled" }
        }
        return ProgramDayUi(
            dayNumber = day.dayNumber,
            title = title,
            meta = meta,
            status = flowStatus.toDetailStatus(),
            sessionCount = if (day.isRestDay) 0 else 1,
            exerciseCount = exerciseCount,
            plannedWorkoutId = workout?.id?.takeIf { it.isNotBlank() },
        )
    }

    private fun resolveFlowDayStatus(
        dayNumber: Int,
        isRestDay: Boolean,
        isActiveProgram: Boolean,
        activeWeek: Int,
        viewingWeek: Int,
        activeDay: Int,
        completedDays: Int,
        isTodayRestDay: Boolean,
    ): ProgramFlowDayStatus {
        if (isRestDay) return ProgramFlowDayStatus.Rest
        if (!isActiveProgram || viewingWeek != activeWeek) {
            return ProgramFlowDayStatus.Planned
        }
        return when {
            dayNumber < activeDay && dayNumber <= completedDays -> ProgramFlowDayStatus.Done
            dayNumber == activeDay && isTodayRestDay -> ProgramFlowDayStatus.Rest
            dayNumber == activeDay -> ProgramFlowDayStatus.Today
            dayNumber < activeDay -> ProgramFlowDayStatus.Done
            else -> ProgramFlowDayStatus.Planned
        }
    }

    private fun ProgramFlowDayStatus.toDetailStatus(): ProgramDayStatus = when (this) {
        ProgramFlowDayStatus.Done -> ProgramDayStatus.Done
        ProgramFlowDayStatus.Today -> ProgramDayStatus.Next
        ProgramFlowDayStatus.Rest -> ProgramDayStatus.Rest
        ProgramFlowDayStatus.Planned -> ProgramDayStatus.Upcoming
    }

    private fun LocalizedNameDto.localized(language: String): String {
        val primary = if (language == "ar") ar else en
        val fallback = if (language == "ar") en else ar
        return primary.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: ""
    }
}
