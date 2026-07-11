package com.movit.core.training.position

import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.LandmarkGroup
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PositionCheck
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.config.PositionCondition
import com.movit.core.training.config.PositionOperator
import com.movit.core.training.engine.Phase
import com.movit.core.training.geometry.LandmarkTiltCorrector
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import com.movit.core.training.testing.readExerciseFixture
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PositionValidatorTest {

    @Test
    fun positionChecksDeskFixture_parsesAndValidatesHorizontalAlignment() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("position-checks-desk.json"))
        val variant = config.poseVariants.first()
        val validator = PositionValidator(
            positionChecks = variant.positionChecks,
            sceneExpectation = variant.resolveSceneExpectation(),
        )
        val landmarks = deskPoseLandmarks()
        val result = validator.validate(landmarks, Phase.DOWN, isFrontCamera = true)
        assertTrue(result.debugChecks.isNotEmpty())
        val symmetry = result.debugChecks.first { it.checkId == "wrist_symmetry" }
        assertEquals(PositionCheckDebugStatus.PASS, symmetry.status)
    }

    @Test
    fun startPhase_keepsValidationButSkipsDebugAllocations() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("position-checks-desk.json"))
        val variant = config.poseVariants.first()
        val validator = PositionValidator(
            positionChecks = variant.positionChecks,
            sceneExpectation = variant.resolveSceneExpectation(),
        )
        val startResult = validator.validate(deskPoseLandmarks(), Phase.START, isFrontCamera = true)
        assertTrue(startResult.debugChecks.isEmpty())
        val downResult = validator.validate(deskPoseLandmarks(), Phase.DOWN, isFrontCamera = true)
        assertTrue(downResult.debugChecks.isNotEmpty())
    }

    @Test
    fun tiltCorrection_appliedBeforeVerticalComparison() {
        val original = uprightLandmarks()
        val tilted = LandmarkTiltCorrector.correct(original, (PI / 6.0).toFloat())
        val validator = PositionValidator(
            positionChecks = listOf(verticalKneeToAnkleCheck()),
            sceneExpectation = PoseSceneExpectation.fromLegacyCode("standing_side"),
            tiltSource = FakeTiltSource((-PI / 6.0).toFloat()),
        )
        val result = validator.validate(tilted, Phase.DOWN)
        assertEquals(-0.20, result.debugChecks.first().actualValue ?: 0.0, 0.001)
    }

    private fun verticalKneeToAnkleCheck() = PositionCheck(
        id = "left_knee_above_ankle",
        type = PositionCheckType.VERTICAL_COMPARISON,
        landmarks = LandmarkGroup(primary = "left_knee", secondary = "left_ankle"),
        condition = PositionCondition(
            operator = PositionOperator.SHOULD_NOT_EXCEED,
            threshold = 1.0,
        ),
        activePhases = listOf("all"),
        errorMessage = LocalizedText(en = "Debug check failed"),
        severity = CheckSeverity.ERROR,
        cooldownMs = 0L,
        minErrorFrames = 1,
    )

    private fun deskPoseLandmarks(): List<Landmark> {
        val landmarks = MutableList(33) { point(0.5f, 0.5f) }
        landmarks[PoseLandmarkIndices.LEFT_WRIST] = point(0.35f, 0.25f)
        landmarks[PoseLandmarkIndices.RIGHT_WRIST] = point(0.65f, 0.25f)
        landmarks[PoseLandmarkIndices.NOSE] = point(0.5f, 0.35f)
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = point(0.4f, 0.4f)
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = point(0.6f, 0.4f)
        landmarks[PoseLandmarkIndices.LEFT_ELBOW] = point(0.38f, 0.32f)
        landmarks[PoseLandmarkIndices.RIGHT_ELBOW] = point(0.62f, 0.32f)
        landmarks[PoseLandmarkIndices.LEFT_HIP] = point(0.42f, 0.6f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = point(0.58f, 0.6f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = point(0.43f, 0.75f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = point(0.57f, 0.75f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = point(0.44f, 0.9f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = point(0.56f, 0.9f)
        return landmarks
    }

    private fun uprightLandmarks(): List<Landmark> {
        val landmarks = MutableList(33) { point(0.5f, 0.5f) }
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = point(0.45f, 0.30f)
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = point(0.55f, 0.30f)
        landmarks[PoseLandmarkIndices.LEFT_HIP] = point(0.45f, 0.55f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = point(0.55f, 0.55f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = point(0.50f, 0.40f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = point(0.58f, 0.72f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = point(0.50f, 0.60f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = point(0.58f, 0.90f)
        landmarks[PoseLandmarkIndices.NOSE] = point(0.50f, 0.20f)
        return landmarks
    }

    private fun point(x: Float, y: Float) = Landmark(
        x = x,
        y = y,
        z = 0f,
        visibility = 1f,
        presence = 1f,
    )

    private class FakeTiltSource(
        override val correctionRadians: Float,
    ) : DeviceTiltPort {
        override val isAvailable: Boolean = true
        override val rollDegrees: Float = -(correctionRadians * (180.0 / PI)).toFloat()
    }
}
