package com.movit.feature.library

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutFlowStateTest {

    @Test
    fun run_loadsSequenceFromSessionAndCachesForPrepare() = runBlocking {
        WorkoutFlowCache.clearAll()
        val run = WorkoutRunViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )

        run.load()

        val state = run.state.value
        assertNotNull(state.config)
        assertTrue(state.config!!.exercises.isNotEmpty())
        assertEquals(state.config, WorkoutFlowCache.get("preview"))
        assertEquals(1, state.currentSet)
        assertTrue(state.progressPercent in 0..100)
        assertTrue(state.sequenceItems().any { it.status == WorkoutRunExerciseStatus.Active })
    }

    @Test
    fun workoutFlowCache_evictsOldestWhenOverCapacity() {
        WorkoutFlowCache.clearAll()
        repeat(5) { index ->
            WorkoutFlowCache.put(
                WorkoutFlowConfigUi(
                    workoutId = "workout-$index",
                    title = "Workout $index",
                    subtitle = "",
                    exercises = emptyList(),
                ),
            )
        }
        assertNull(WorkoutFlowCache.get("workout-0"))
        assertNotNull(WorkoutFlowCache.get("workout-4"))
    }

    @Test
    fun mapper_buildsFlowFromSessionPreview() {
        val config = WorkoutFlowMapper.fromSession(WorkoutSessionPreviewData.preview)
        assertEquals("preview", config.workoutId)
        assertTrue(config.exercises.size >= 5)
        assertEquals(60, config.restBetweenSetsSeconds)
    }

    @Test
    fun run_legacyFileName_matchesCurrentExerciseSlug() = runBlocking {
        WorkoutFlowCache.clearAll()
        val run = WorkoutRunViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )

        run.load()

        assertEquals("bodyweight-squat", run.legacyFileNameForStart())
    }
}
