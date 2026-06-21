package com.movit.feature.training

import com.movit.designsystem.components.SkeletonJointVisual
import com.movit.designsystem.components.SkeletonLandmarkPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingCameraSwitchPolicyTest {
    @Test
    fun onSwitchStarted_clearsOverlayAndMarksSwitching() {
        val before = TrainingSessionUiState(
            exerciseSlug = "squat",
            exerciseName = "Squat",
            targetReps = 10,
            landmarks = listOf(SkeletonLandmarkPoint(0.5f, 0.5f, true)),
            jointVisuals = mapOf("left_elbow" to SkeletonJointVisual(jointCode = "left_elbow")),
            isCameraSwitching = false,
        )
        val after = TrainingCameraSwitchPolicy.onSwitchStarted(before)
        assertTrue(after.isCameraSwitching)
        assertEquals(emptyList<SkeletonLandmarkPoint>(), after.landmarks)
        assertTrue(after.jointVisuals.isEmpty())
    }
}
