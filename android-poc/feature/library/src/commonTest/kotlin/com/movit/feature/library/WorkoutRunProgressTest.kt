package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkoutRunProgressTest {

    @Test
    fun advanceAfterExercise_emitsRestBeforeNextSet() {
        WorkoutRunProgressStore.clear("w1")
        val config = WorkoutFlowConfigUi(
            workoutId = "w1",
            title = "Test",
            subtitle = "Sub",
            exercises = listOf(
                WorkoutFlowExerciseUi(
                    id = "ex-1",
                    exerciseSlug = "squat",
                    name = "Squat",
                    sets = 3,
                    reps = 10,
                    durationSeconds = null,
                ),
            ),
            restBetweenSetsSeconds = 45,
        )
        val nav = WorkoutRunProgressStore.advanceAfterExercise("w1", config, completedExerciseIndex = 0)
        assertIs<WorkoutRunPostNav.Rest>(nav)
        assertEquals("ex-1", nav.upNextExerciseId)
        assertEquals(45, nav.restSeconds)
        assertEquals(2, WorkoutRunProgressStore.read("w1").currentSet)
    }

    @Test
    fun advanceAfterExercise_emitsRestBeforeNextExercise() {
        WorkoutRunProgressStore.clear("w2")
        val config = WorkoutFlowConfigUi(
            workoutId = "w2",
            title = "Test",
            subtitle = "Sub",
            exercises = listOf(
                WorkoutFlowExerciseUi("ex-1", "squat", "Squat", sets = 1, reps = 10, durationSeconds = null),
                WorkoutFlowExerciseUi("ex-2", "lunge", "Lunge", sets = 1, reps = 10, durationSeconds = null),
            ),
            restBetweenSetsSeconds = 60,
        )
        WorkoutRunProgressStore.write("w2", WorkoutRunProgress(exerciseIndex = 0, currentSet = 1))
        val nav = WorkoutRunProgressStore.advanceAfterExercise("w2", config, completedExerciseIndex = 0)
        assertIs<WorkoutRunPostNav.Rest>(nav)
        assertEquals("ex-2", nav.upNextExerciseId)
        assertEquals(60, nav.restSeconds)
    }
}
