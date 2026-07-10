package com.movit.feature.library

import com.movit.core.model.ExploreItemUi
import com.movit.resources.strings.ProgramDetailStrings

internal object ProgramDetailMapper {

    suspend fun map(
        program: ExploreItemUi,
        enrollment: ProgramEnrollmentUi,
        selectedWeekNumber: Int,
        selectedDayNumber: Int?,
        edit: ProgramEditUiState,
        weeks: List<ProgramWeekUi>,
        nextSession: ProgramNextSessionUi?,
        strings: ProgramDetailStrings,
    ): ProgramDetailUiState {
        val durationWeeks = parseWeeksCount(program.metadata)
        val weeklyTarget = parseWeeklyTarget(program.metadata)
        val trainingDays = durationWeeks * weeklyTarget
        val totalSessions = (trainingDays * 0.6).toInt().coerceAtLeast(weeklyTarget)
        val restDays = (trainingDays - totalSessions).coerceAtLeast(0)
        val sessionMinutes = parseSessionMinutes(program.metadata) ?: 25

        val currentWeek = weeks.firstOrNull { it.weekNumber == selectedWeekNumber } ?: weeks.firstOrNull()
        val resolvedDayNumber = resolveSelectedDayNumber(currentWeek, selectedDayNumber)
        val selectedDay = currentWeek?.days?.firstOrNull { it.dayNumber == resolvedDayNumber }
        val selectedDaySessions = buildDaySessions(
            programId = program.id,
            weekNumber = selectedWeekNumber,
            day = selectedDay,
        )
        val resolvedNextSession = if (enrollment.isEnrolled) nextSession else null

        val kickers = buildList {
            program.badge?.let { add(it) }
            addAll(program.metadata.filter { !it.contains("week", ignoreCase = true) })
        }.distinct().take(3)

        return ProgramDetailUiState(
            isLoading = false,
            programId = program.id,
            title = program.title,
            subtitle = program.subtitle,
            description = program.subtitle,
            imageUrl = program.imageUrl,
            kickers = kickers,
            stats = listOf(
                ProgramStatUi(
                    value = strings.durationValue(durationWeeks),
                    label = strings.durationLabel,
                    hint = strings.durationHint(trainingDays),
                ),
                ProgramStatUi(
                    value = strings.weeklyValue(weeklyTarget),
                    label = strings.weeklyTargetLabel,
                    hint = strings.weeklyHint,
                ),
                ProgramStatUi(
                    value = strings.sessionValue(sessionMinutes),
                    label = strings.sessionTimeLabel,
                    hint = strings.sessionHint,
                ),
                ProgramStatUi(
                    value = strings.planValue(totalSessions),
                    label = strings.planLoadLabel,
                    hint = strings.planHint(restDays),
                ),
            ),
            detailCards = defaultDetailCards(),
            selectedWeekNumber = selectedWeekNumber,
            selectedDayNumber = resolvedDayNumber,
            selectedDaySessions = selectedDaySessions,
            weeks = weeks,
            enrollment = enrollment,
            nextSession = resolvedNextSession,
            edit = edit.copy(
                editingDayTitle = edit.editingDayTitle.ifBlank {
                    currentWeek?.days?.firstOrNull { it.status == ProgramDayStatus.Next }?.title
                        ?: currentWeek?.days?.firstOrNull()?.title.orEmpty()
                },
            ),
        )
    }

    private fun resolveSelectedDayNumber(
        week: ProgramWeekUi?,
        selectedDayNumber: Int?,
    ): Int? {
        if (week == null) return null
        if (selectedDayNumber != null && week.days.any { it.dayNumber == selectedDayNumber }) {
            return selectedDayNumber
        }
        return week.days.firstOrNull { it.status == ProgramDayStatus.Next }?.dayNumber
            ?: week.days.firstOrNull { it.status != ProgramDayStatus.Rest }?.dayNumber
    }

    private fun buildDaySessions(
        programId: String,
        weekNumber: Int,
        day: ProgramDayUi?,
    ): List<ProgramDaySessionUi> {
        if (day == null || day.status == ProgramDayStatus.Rest) return emptyList()
        val plannedWorkoutId = day.plannedWorkoutId ?: return emptyList()
        return listOf(
            ProgramDaySessionUi(
                title = day.title,
                subtitle = day.meta,
                exerciseCount = day.exerciseCount,
                sessionKey = WorkoutSessionKeys.encode(
                    programId = programId,
                    weekNumber = weekNumber,
                    dayNumber = day.dayNumber,
                    plannedWorkoutId = plannedWorkoutId,
                ),
            ),
        )
    }

    private fun parseWeeksCount(metadata: List<String>): Int =
        metadata.firstOrNull { it.contains("week", ignoreCase = true) }
            ?.filter { it.isDigit() }
            ?.toIntOrNull()
            ?: 4

    private fun parseWeeklyTarget(metadata: List<String>): Int {
        val raw = metadata.firstOrNull { it.contains("/") && it.contains("week", ignoreCase = true) }
            ?: metadata.firstOrNull { it.contains("day", ignoreCase = true) }
        return raw?.filter { it.isDigit() }?.toIntOrNull() ?: 3
    }

    private fun parseSessionMinutes(metadata: List<String>): Int? =
        metadata.firstOrNull { it.contains("min", ignoreCase = true) }
            ?.filter { it.isDigit() }
            ?.toIntOrNull()

    private fun defaultDetailCards(): List<ProgramDetailCardUi> = listOf(
        ProgramDetailCardUi(
            title = "Goal-based progression",
            description = "Weeks move from basic control to stronger range and a final reassessment block.",
        ),
        ProgramDetailCardUi(
            title = "Session-level detail",
            description = "Each day contains sessions with exercises, sets, reps, duration, weight, and rest targets.",
        ),
        ProgramDetailCardUi(
            title = "Calendar aware",
            description = "Start date, paused days, and active status are preserved so the plan can pause and resume cleanly.",
        ),
    )
}
