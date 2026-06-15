package com.movit.feature.trainingdebug

import com.movit.core.training.config.PositionOperator
import com.movit.core.training.position.PositionCheckDebugStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DebugPositionCheckFactoryTest {
    @Test
    fun syntheticVerticalCheck_passesWhenKneeAboveAnkle() {
        val validator = DebugPositionCheckFactory.buildValidator(
            config = DebugPositionCheckConfig(
                primaryLandmark = "left_knee",
                secondaryLandmark = "left_ankle",
                operator = PositionOperator.SHOULD_NOT_EXCEED,
                threshold = 1.0,
            ),
            sceneExpectation = DebugSceneExpectationConfig().toPoseSceneExpectation(),
        )
        val landmarks = squatLandmarks()
        val result = validator.validate(landmarks, com.movit.core.training.engine.Phase.START)
        val debug = result.debugChecks.first()
        assertEquals(PositionCheckDebugStatus.PASS, debug.status)
        assertNotNull(debug.actualValue)
    }

    private fun squatLandmarks(): List<com.movit.core.training.model.Landmark> {
        val landmarks = MutableList(33) { com.movit.core.training.model.Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.LEFT_HIP] =
            com.movit.core.training.model.Landmark(0.45f, 0.45f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.LEFT_KNEE] =
            com.movit.core.training.model.Landmark(0.45f, 0.60f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.LEFT_ANKLE] =
            com.movit.core.training.model.Landmark(0.45f, 0.78f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.RIGHT_HIP] =
            com.movit.core.training.model.Landmark(0.55f, 0.45f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.RIGHT_KNEE] =
            com.movit.core.training.model.Landmark(0.55f, 0.60f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.RIGHT_ANKLE] =
            com.movit.core.training.model.Landmark(0.55f, 0.78f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.LEFT_SHOULDER] =
            com.movit.core.training.model.Landmark(0.42f, 0.32f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.RIGHT_SHOULDER] =
            com.movit.core.training.model.Landmark(0.58f, 0.32f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.NOSE] =
            com.movit.core.training.model.Landmark(0.5f, 0.22f, 0f, 1f, 1f)
        return landmarks
    }
}
