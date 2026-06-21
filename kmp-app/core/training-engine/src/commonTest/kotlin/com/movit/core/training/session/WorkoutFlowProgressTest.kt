package com.movit.core.training.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutFlowProgressTest {
    private val warmup = TrainingFlowItem.Exercise(
        slug = "arm-circles",
        displayName = "Arm Circles",
        sets = 1,
        phaseRole = "WARMUP",
    )
    private val main = TrainingFlowItem.Exercise(
        slug = "squat",
        displayName = "Squat",
        sets = 2,
        phaseRole = "MAIN",
    )
    private val cooldown = TrainingFlowItem.Exercise(
        slug = "stretch",
        displayName = "Stretch",
        sets = 1,
        phaseRole = "COOLDOWN",
    )

    @Test
    fun countsTowardWorkoutProgress_excludesWarmupActivationCooldown() {
        assertFalse(WorkoutFlowProgress.countsTowardWorkoutProgress("WARMUP"))
        assertFalse(WorkoutFlowProgress.countsTowardWorkoutProgress("activation"))
        assertFalse(WorkoutFlowProgress.countsTowardWorkoutProgress("COOLDOWN"))
        assertTrue(WorkoutFlowProgress.countsTowardWorkoutProgress("MAIN"))
        assertTrue(WorkoutFlowProgress.countsTowardWorkoutProgress(null))
    }

    @Test
    fun percentComplete_ignoresWarmupAndCooldownInTotals() {
        val items = listOf(warmup, main, cooldown)
        // On first main set (index 1), 0 main sets done of 2
        assertEquals(0, WorkoutFlowProgress.percentComplete(items, itemIndex = 1, completedSetsInCurrent = 0))
        // After one main set done
        assertEquals(50, WorkoutFlowProgress.percentComplete(items, itemIndex = 1, completedSetsInCurrent = 1))
        // Cooldown does not add to denominator
        assertEquals(100, WorkoutFlowProgress.percentComplete(items, itemIndex = 2, completedSetsInCurrent = 0))
    }
}
