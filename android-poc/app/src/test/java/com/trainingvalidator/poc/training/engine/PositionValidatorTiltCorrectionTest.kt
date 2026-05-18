package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.models.LandmarkGroup
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.PositionCheck
import com.trainingvalidator.poc.training.models.PositionCheckType
import com.trainingvalidator.poc.training.models.PositionCondition
import com.trainingvalidator.poc.training.models.PositionOperator
import org.junit.Assert.assertEquals
import org.junit.Test

class PositionValidatorTiltCorrectionTest {

    @Test
    fun `position validator applies tilt correction before vertical comparison`() {
        val original = uprightLandmarks()
        val tilted = LandmarkTiltCorrector.correct(
            original,
            Math.toRadians(30.0).toFloat()
        )
        val validator = PositionValidator(
            positionChecks = listOf(verticalKneeToAnkleCheck()),
            posePositionCode = "standing_side",
            sceneExpectation = PoseSceneExpectation.fromLegacyCode("standing_side"),
            tiltSource = FakeTiltSource(Math.toRadians(-30.0).toFloat())
        )

        val result = validator.validate(
            landmarks = tilted,
            currentPhase = Phase.START,
            isBilateralFlipped = false,
            isFrontCamera = false
        )

        assertEquals(-0.20, result.debugChecks.first().actualValue ?: 0.0, 0.001)
    }

    private fun verticalKneeToAnkleCheck() = PositionCheck(
        id = "left_knee_above_ankle",
        type = PositionCheckType.VERTICAL_COMPARISON,
        landmarks = LandmarkGroup(
            primary = "left_knee",
            secondary = "left_ankle"
        ),
        condition = PositionCondition(
            operator = PositionOperator.SHOULD_NOT_EXCEED,
            threshold = 1.0
        ),
        activePhases = listOf("all"),
        errorMessage = LocalizedText(en = "Debug check failed"),
        severity = CheckSeverity.ERROR,
        cooldownMs = 0L,
        minErrorFrames = 1
    )

    private fun uprightLandmarks(): List<SmoothedLandmark> {
        val landmarks = MutableList(BodyLandmarks.TOTAL_LANDMARKS) { point(0.5f, 0.5f) }
        landmarks[BodyLandmarks.LEFT_SHOULDER] = point(0.45f, 0.30f)
        landmarks[BodyLandmarks.RIGHT_SHOULDER] = point(0.55f, 0.30f)
        landmarks[BodyLandmarks.LEFT_HIP] = point(0.45f, 0.55f)
        landmarks[BodyLandmarks.RIGHT_HIP] = point(0.55f, 0.55f)
        landmarks[BodyLandmarks.LEFT_KNEE] = point(0.50f, 0.40f)
        landmarks[BodyLandmarks.RIGHT_KNEE] = point(0.58f, 0.72f)
        landmarks[BodyLandmarks.LEFT_ANKLE] = point(0.50f, 0.60f)
        landmarks[BodyLandmarks.RIGHT_ANKLE] = point(0.58f, 0.90f)
        landmarks[BodyLandmarks.NOSE] = point(0.50f, 0.20f)
        return landmarks
    }

    private fun point(x: Float, y: Float) = SmoothedLandmark(
        x = x,
        y = y,
        z = 0f,
        visibility = 1f,
        presence = 1f
    )

    private class FakeTiltSource(
        override val correctionRadians: Float
    ) : TiltCorrectionSource {
        override val isAvailable: Boolean = true
        override val rollDegrees: Float = -Math.toDegrees(correctionRadians.toDouble()).toFloat()
    }
}
