package com.movit.feature.library

import com.movit.core.network.dto.EffectivePlanItemDto
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.resources.strings.SessionStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WorkoutSessionApiMapperTest {

    @Test
    fun mapsEffectivePlanToSession() {
        runBlocking {
        val strings = SessionStrings.load("en")
        val parsed = ParsedSessionKey(
            programId = "prog-1",
            weekNumber = 2,
            dayNumber = 3,
            plannedWorkoutId = "pw-1",
        )
        val plan = EffectivePlanPayloadDto(
            programId = "prog-1",
            weekNumber = 2,
            dayNumber = 3,
            plannedWorkouts = listOf(
                EffectivePlannedWorkoutDto(
                    id = "pw-1",
                    name = mapOf("en" to "Lower Body"),
                    estimatedDurationMin = 22,
                    items = listOf(
                        EffectivePlanItemDto(
                            id = "item-1",
                            type = "exercise",
                            exerciseId = "ex-1",
                            sets = 3,
                            targetReps = 12,
                            restBetweenSetsMs = 60_000,
                            sortOrder = 0,
                            phaseRole = "MAIN",
                        ),
                        EffectivePlanItemDto(
                            id = "rest-1",
                            type = "rest",
                            restDurationMs = 90_000,
                            sortOrder = 1,
                            phaseRole = "MAIN",
                        ),
                    ),
                ),
            ),
        )
        val catalog = mapOf(
            "ex-1" to ExerciseCatalogEntry(
                slug = "barbell-squat",
                serverId = "ex-1",
                name = "Barbell Squat",
                category = "Quads",
            ),
        )

        val session = WorkoutSessionApiMapper.mapSession(
            parsed = parsed,
            plan = plan,
            language = "en",
            strings = strings,
            exerciseBySlug = emptyMap(),
            exerciseById = catalog,
        )

        assertNotNull(session)
        assertEquals("Lower Body", session.title)
        assertEquals(1, session.exerciseCount)
        assertEquals("pw-1", session.context?.plannedWorkoutId)
        val blocks = session.sections.single().items
        assertEquals(2, blocks.size)
        assertEquals("barbell-squat", (blocks[0] as WorkoutSessionBlockUi.Exercise).exerciseSlug)
        }
    }
}
