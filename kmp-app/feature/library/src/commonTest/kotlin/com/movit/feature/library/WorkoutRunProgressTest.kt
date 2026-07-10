package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Legacy [WorkoutRunProgressStore] advance tests kept as documentation of retired
 * between-exercise prepare navigation. Modern finish uses [resolveWorkoutRunFinish].
 */
class WorkoutRunProgressTest {

    @Test
    fun resolveWorkoutRunFinish_whenComplete_clearsActiveRun() {
        WorkoutRunStore.clearAll()
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w1",
            title = "t",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "ex-1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(10),
                    sets = 2,
                    restBetweenSetsMs = 30_000,
                    restAfterExerciseMs = 45_000,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        WorkoutRunStore.start(workoutId = "w1", snapshot = snapshot)
        assertEquals(WorkoutRunFinishNav.Complete, resolveWorkoutRunFinish("w1", true))
        assertNull(WorkoutRunStore.activeForWorkout("w1"))
    }

    @Test
    fun resolveWorkoutRunFinish_whenIncomplete_abandonsAndReturnsToSession() {
        WorkoutRunStore.clearAll()
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w2",
            title = "t",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "ex-1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(10),
                    sets = 1,
                    restBetweenSetsMs = 0,
                    restAfterExerciseMs = 0,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        WorkoutRunStore.start(workoutId = "w2", snapshot = snapshot)
        assertEquals(WorkoutRunFinishNav.BackToSession, resolveWorkoutRunFinish("w2", false))
        assertNull(WorkoutRunStore.activeForWorkout("w2"))
    }
}
