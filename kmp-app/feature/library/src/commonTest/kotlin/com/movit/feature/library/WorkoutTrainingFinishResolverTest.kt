package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutTrainingFinishResolverTest {

    @Test
    fun onTrainingSessionFinish_whenWorkoutFlowComplete_closesRun() {
        WorkoutRunStore.clearAll()
        val snapshot = twoExerciseSnapshot("w-complete")
        WorkoutRunStore.start(workoutId = "w-complete", snapshot = snapshot)

        val nav = resolveWorkoutRunFinish(
            workoutId = "w-complete",
            isWorkoutFlowComplete = true,
        )

        assertEquals(WorkoutRunFinishNav.Complete, nav)
        assertNull(WorkoutRunStore.activeForWorkout("w-complete"))
    }

    @Test
    fun onTrainingSessionFinish_whenIncomplete_returnsToSession_withoutRestRoute() {
        WorkoutRunStore.clearAll()
        val snapshot = twoExerciseSnapshot("w-partial")
        WorkoutRunStore.start(workoutId = "w-partial", snapshot = snapshot)

        val nav = resolveWorkoutRunFinish(
            workoutId = "w-partial",
            isWorkoutFlowComplete = false,
        )

        assertEquals(WorkoutRunFinishNav.BackToSession, nav)
        assertNull(WorkoutRunStore.activeForWorkout("w-partial"))
    }

    @Test
    fun onTrainingSessionFinish_withoutActiveRun_stillReturnsNav() {
        WorkoutRunStore.clearAll()
        val nav = resolveWorkoutRunFinish(
            workoutId = "w-missing",
            isWorkoutFlowComplete = false,
        )
        assertEquals(WorkoutRunFinishNav.BackToSession, nav)
    }

    private fun twoExerciseSnapshot(workoutId: String): WorkoutRunSnapshot = WorkoutRunSnapshot(
        workoutId = workoutId,
        title = "Test",
        blocks = listOf(
            WorkoutRunBlock.Exercise(
                exerciseId = "ex-1",
                slug = "squat",
                displayName = "Squat",
                phaseRole = "MAIN",
                target = ExerciseTarget.Reps(10),
                sets = 1,
                restBetweenSetsMs = 0,
                restAfterExerciseMs = 45_000,
                poseVariantIndex = 0,
                weightPerSetKg = null,
            ),
            WorkoutRunBlock.Exercise(
                exerciseId = "ex-2",
                slug = "lunge",
                displayName = "Lunge",
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
}
