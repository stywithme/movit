package com.movit.feature.training

import kotlin.test.Test
import kotlin.test.assertTrue

class TrainingCameraSwitchPolicyTest {
    @Test
    fun onSwitchStarted_marksSwitchingAndClearsError() {
        val before = TrainingSessionUiState(
            exerciseSlug = "squat",
            exerciseName = "Squat",
            targetReps = 10,
            isCameraSwitching = false,
            errorMessage = "camera busy",
        )
        val after = TrainingCameraSwitchPolicy.onSwitchStarted(before)
        assertTrue(after.isCameraSwitching)
        assertTrue(after.errorMessage == null)
    }
}
