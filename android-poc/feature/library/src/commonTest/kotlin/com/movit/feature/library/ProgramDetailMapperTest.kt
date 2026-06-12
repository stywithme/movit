package com.movit.feature.library

import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import com.movit.resources.strings.ProgramDetailStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProgramDetailMapperTest {

    @Test
    fun map_parsesMetadataIntoStats() = runBlocking {
        val strings = ProgramDetailStrings.load("en")
        val program = ExploreItemUi(
            id = "mobility-starter",
            title = "Mobility Starter",
            subtitle = "Guided plan",
            type = ExploreItemType.Program,
            metadata = listOf("4 weeks", "3 days/week", "Beginner"),
        )
        val weeks = ProgramDetailPreviewData.weeksFor(program.id, durationWeeks = 4, weeklyTarget = 3)
        val state = ProgramDetailMapper.map(
            program = program,
            enrollment = ProgramEnrollmentUi(isEnrolled = false),
            selectedWeekNumber = 1,
            selectedDayNumber = null,
            edit = ProgramEditUiState(),
            weeks = weeks,
            nextSession = null,
            strings = strings,
        )

        assertEquals("4 weeks", state.stats[0].value)
        assertEquals("Duration", state.stats[0].label)
        assertEquals("3 / week", state.stats[1].value)
        assertEquals(4, state.weeks.size)
        assertTrue(state.weeks.first().days.isNotEmpty())
    }

    @Test
    fun map_preservesEditDaySessions() = runBlocking {
        val strings = ProgramDetailStrings.load("en")
        val program = ExploreItemUi(
            id = "program-starter",
            title = "Starter",
            subtitle = "Sub",
            type = ExploreItemType.Program,
            metadata = listOf("4 weeks", "3 days/week"),
        )
        val editSessions = listOf(
            ProgramEditSessionUi(
                id = "session-a",
                title = "Morning flow",
                sortOrder = 0,
                exercises = listOf(
                    ProgramEditExerciseUi(
                        id = "ex-1",
                        name = "Squat",
                        sets = 3,
                        reps = 12,
                        weightKg = 16.0,
                        restSeconds = 60,
                    ),
                ),
            ),
        )
        val state = ProgramDetailMapper.map(
            program = program,
            enrollment = ProgramEnrollmentUi(isEnrolled = true),
            selectedWeekNumber = 1,
            selectedDayNumber = null,
            edit = ProgramEditUiState(daySessions = editSessions, editingDayTitle = "Week 1 · Day 2"),
            weeks = emptyList(),
            nextSession = null,
            strings = strings,
        )

        assertEquals(1, state.edit.daySessions.size)
        assertEquals("Morning flow", state.edit.daySessions.first().title)
        assertEquals("Week 1 · Day 2", state.edit.editingDayTitle)
    }

    @Test
    fun enrolled_exposesNextSession() = runBlocking {
        val strings = ProgramDetailStrings.load("en")
        val program = ExploreItemUi(
            id = "program-starter",
            title = "Starter",
            subtitle = "Sub",
            type = ExploreItemType.Program,
            metadata = listOf("4 weeks", "3 days/week"),
        )
        val weeks = ProgramDetailPreviewData.weeksFor(program.id, durationWeeks = 4, weeklyTarget = 3)
        val nextSession = ProgramDetailPreviewData.nextSession(program.id)
        val state = ProgramDetailMapper.map(
            program = program,
            enrollment = ProgramEnrollmentUi(isEnrolled = true),
            selectedWeekNumber = 1,
            selectedDayNumber = null,
            edit = ProgramEditUiState(),
            weeks = weeks,
            nextSession = nextSession,
            strings = strings,
        )

        assertNotNull(state.nextSession)
        assertTrue(state.nextSession!!.sessionWorkoutId.startsWith("session:"))
    }
}
