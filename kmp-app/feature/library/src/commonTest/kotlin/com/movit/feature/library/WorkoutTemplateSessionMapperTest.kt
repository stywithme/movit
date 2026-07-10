package com.movit.feature.library

import com.movit.core.network.dto.ExploreWorkoutDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.WorkoutTemplateEmbeddedExerciseDto
import com.movit.core.network.dto.WorkoutTemplateExerciseDto
import com.movit.core.network.dto.WorkoutTemplateTrainingConfigDto
import com.movit.resources.strings.SessionStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkoutTemplateSessionMapperTest {

    private val strings = runBlocking {
        SessionStrings.load("en")
    }

    @Test
    fun mapSession_buildsExerciseBlocksFromTrainingConfig() {
        val config = WorkoutTemplateTrainingConfigDto(
            id = "wt-001",
            slug = "quick-legs",
            name = mapOf("en" to "Quick Legs"),
            estimatedDurationMin = 20,
            exercises = listOf(
                WorkoutTemplateExerciseDto(
                    workoutExerciseId = "we-1",
                    sortOrder = 1,
                    sets = 3,
                    targetReps = 12,
                    restBetweenSetsMs = 60_000,
                    exercise = WorkoutTemplateEmbeddedExerciseDto(
                        slug = "bodyweight-squat",
                        name = mapOf("en" to "Squat"),
                    ),
                ),
            ),
        )

        val session = WorkoutTemplateSessionMapper.mapSession(
            slugOrId = "quick-legs",
            config = config,
            templateMeta = null,
            language = "en",
            strings = strings,
            exerciseBySlug = emptyMap(),
        )

        assertEquals("quick-legs", session.id)
        assertEquals("Quick Legs", session.title)
        assertEquals(1, session.exerciseCount)
        val exercise = session.sections
            .flatMap { it.items }
            .filterIsInstance<WorkoutSessionBlockUi.Exercise>()
            .first()
        assertEquals("bodyweight-squat", exercise.exerciseSlug)
        assertEquals(3, exercise.sets)
        assertEquals(12, exercise.reps)
    }

    @Test
    fun mapExploreFallback_usesExploreMetadataWhenApiUnavailable() {
        val meta = ExploreWorkoutDto(
            id = "wt-001",
            slug = "quick-legs",
            name = LocalizedNameDto(en = "Quick Legs"),
            exerciseCount = 4,
            estimatedDurationMin = 25,
        )

        val session = WorkoutTemplateSessionMapper.mapExploreFallback(
            slugOrId = "quick-legs",
            templateMeta = meta,
            explore = null,
            language = "en",
            strings = strings,
            exerciseBySlug = emptyMap(),
        )

        assertNotNull(session)
        assertEquals("quick-legs", session.id)
        assertEquals("Quick Legs", session.title)
        assertTrue(session.sections.isEmpty())
        assertEquals(0, session.setCount)
        assertTrue(!session.toRunSnapshot().isStartable)
    }
}
