package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkoutTrainingFinishResolverTest {

    @Test
    fun onTrainingSessionFinish_whenWorkoutFlowComplete_clearsProgress() {
        WorkoutRunProgressStore.clear("w-complete")
        WorkoutRunProgressStore.write("w-complete", WorkoutRunProgress(exerciseIndex = 1, currentSet = 2))
        val config = twoExerciseConfig("w-complete")

        val nav = WorkoutRunProgressStore.onTrainingSessionFinish(
            workoutId = "w-complete",
            config = config,
            completedExerciseIndex = 0,
            isWorkoutFlowComplete = true,
        )

        assertEquals(WorkoutRunPostNav.Complete, nav)
        assertEquals(WorkoutRunProgress(), WorkoutRunProgressStore.read("w-complete"))
    }

    @Test
    fun onTrainingSessionFinish_whenSingleExerciseStillAdvancesProgress() {
        WorkoutRunProgressStore.clear("w-partial")
        val config = twoExerciseConfig("w-partial")

        val nav = WorkoutRunProgressStore.onTrainingSessionFinish(
            workoutId = "w-partial",
            config = config,
            completedExerciseIndex = 0,
            isWorkoutFlowComplete = false,
        )

        assertIs<WorkoutRunPostNav.Rest>(nav)
        assertEquals("ex-2", nav.upNextExerciseId)
    }

    @Test
    fun onTrainingSessionFinish_withoutConfig_returnsBackToRun() {
        WorkoutRunProgressStore.clear("w-missing")
        val nav = WorkoutRunProgressStore.onTrainingSessionFinish(
            workoutId = "w-missing",
            config = null,
            completedExerciseIndex = 0,
            isWorkoutFlowComplete = false,
        )
        assertEquals(WorkoutRunPostNav.BackToRun, nav)
    }

    private fun twoExerciseConfig(workoutId: String): WorkoutFlowConfigUi = WorkoutFlowConfigUi(
        workoutId = workoutId,
        title = "Test",
        subtitle = "Sub",
        exercises = listOf(
            WorkoutFlowExerciseUi("ex-1", "squat", "Squat", sets = 1, reps = 10, durationSeconds = null),
            WorkoutFlowExerciseUi("ex-2", "lunge", "Lunge", sets = 1, reps = 10, durationSeconds = null),
        ),
        restBetweenSetsSeconds = 45,
    )
}
