package com.movit.feature.library

import com.movit.core.model.ExploreItemUi

internal object ProgramDetailMapper {

    fun map(
        program: ExploreItemUi,
        enrollment: ProgramEnrollmentUi,
        selectedWeekNumber: Int,
        edit: ProgramEditUiState,
    ): ProgramDetailUiState {
        val durationWeeks = parseWeeksCount(program.metadata)
        val weeklyTarget = parseWeeklyTarget(program.metadata)
        val trainingDays = durationWeeks * weeklyTarget
        val totalSessions = (trainingDays * 0.6).toInt().coerceAtLeast(weeklyTarget)
        val restDays = (trainingDays - totalSessions).coerceAtLeast(0)
        val sessionMinutes = parseSessionMinutes(program.metadata) ?: 25

        val weeks = ProgramDetailPreviewData.weeksFor(program.id, durationWeeks, weeklyTarget)
        val currentWeek = weeks.firstOrNull { it.weekNumber == selectedWeekNumber } ?: weeks.firstOrNull()
        val nextSession = if (enrollment.isEnrolled) {
            ProgramDetailPreviewData.nextSession(program.id)
        } else {
            null
        }

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
                    value = "${durationWeeks} weeks",
                    label = "Duration",
                    hint = "$trainingDays training days",
                ),
                ProgramStatUi(
                    value = "$weeklyTarget / week",
                    label = "Weekly target",
                    hint = "Flexible calendar",
                ),
                ProgramStatUi(
                    value = "$sessionMinutes min",
                    label = "Session time",
                    hint = "Estimated average",
                ),
                ProgramStatUi(
                    value = "$totalSessions sessions",
                    label = "Plan load",
                    hint = "$restDays rest days",
                ),
            ),
            detailCards = defaultDetailCards(),
            selectedWeekNumber = selectedWeekNumber,
            weeks = weeks,
            enrollment = enrollment,
            nextSession = nextSession,
            edit = edit.copy(
                weeklyTarget = weeklyTarget,
                editingDayTitle = currentWeek?.days?.firstOrNull { it.status == ProgramDayStatus.Next }?.title
                    ?: currentWeek?.days?.firstOrNull()?.title.orEmpty(),
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
