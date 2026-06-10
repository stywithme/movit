package com.movit.feature.library

import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProgramDetailMapperTest {

    @Test
    fun map_parsesMetadataIntoStats() {
        val program = ExploreItemUi(
            id = "mobility-starter",
            title = "Mobility Starter",
            subtitle = "Guided plan",
            type = ExploreItemType.Program,
            metadata = listOf("4 weeks", "3 days/week", "Beginner"),
        )
        val state = ProgramDetailMapper.map(
            program = program,
            enrollment = ProgramEnrollmentUi(isEnrolled = false),
            selectedWeekNumber = 1,
            edit = ProgramEditUiState(),
        )

        assertEquals("4 weeks", state.stats[0].value)
        assertEquals("3 / week", state.stats[1].value)
        assertEquals(4, state.weeks.size)
        assertTrue(state.weeks.first().days.isNotEmpty())
    }

    @Test
    fun enrolled_exposesNextSession() {
        val program = ExploreItemUi(
            id = "program-starter",
            title = "Starter",
            subtitle = "Sub",
            type = ExploreItemType.Program,
            metadata = listOf("4 weeks", "3 days/week"),
        )
        val state = ProgramDetailMapper.map(
            program = program,
            enrollment = ProgramEnrollmentUi(isEnrolled = true),
            selectedWeekNumber = 1,
            edit = ProgramEditUiState(),
        )

        assertNotNull(state.nextSession)
        assertTrue(state.nextSession!!.sessionWorkoutId.startsWith("session:"))
    }
}
