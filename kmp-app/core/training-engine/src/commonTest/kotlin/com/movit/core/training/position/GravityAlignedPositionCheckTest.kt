package com.movit.core.training.geometry

import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.config.CheckSpace
import com.movit.core.training.config.LandmarkGroup
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PositionCheck
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.config.PositionCondition
import com.movit.core.training.config.PositionOperator
import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.engine.Phase
import com.movit.core.training.model.Landmark
import com.movit.core.training.position.PoseSceneExpectation
import com.movit.core.training.position.PositionValidator
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class GravityAlignedPositionCheckTest {

    @Test
    fun gravity3d_vertical_passesWhenTorsoUprightRelativeToTiltedGravity() {
        // Device tilted ~20°: gravity has gx component; world person still upright in camera Y.
        val tiltRad = (20.0 * PI / 180.0).toFloat()
        val gravity = floatArrayOf(sin(tiltRad), kotlin.math.cos(tiltRad), 0f)
        val world = uprightWorld()
        val tilt = FakeTilt(gravity)
        val check = PositionCheck(
            id = "torso_upright",
            type = PositionCheckType.VERTICAL_COMPARISON,
            landmarks = LandmarkGroup(primary = "left_shoulder", secondary = "left_hip"),
            condition = PositionCondition(
                operator = PositionOperator.SHOULD_NOT_EXCEED,
                threshold = 35.0,
            ),
            severity = CheckSeverity.WARNING,
            errorMessage = LocalizedText(en = "Stand upright"),
            space = CheckSpace.GRAVITY_3D,
            minErrorFrames = 1,
        )
        val validator = PositionValidator(
            positionChecks = listOf(check),
            sceneExpectation = PoseSceneExpectation(
                postures = emptyList(),
                directions = emptyList(),
                regions = emptyList(),
            ),
            tiltSource = tilt,
            alwaysCollectDebugChecks = true,
        )
        val image = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
        val result = validator.validate(
            landmarks = image,
            currentPhase = Phase.UP,
            worldLandmarks = world,
        )
        assertTrue(result.errors.isEmpty() && result.warnings.isEmpty(), "expected pass: $result")
    }

    private fun uprightWorld(): List<Landmark> {
        val lm = MutableList(33) { Landmark(0f, 0f, 0f, 1f, 1f) }
        lm[11] = Landmark(-0.1f, 0.4f, 0f, 1f, 1f)
        lm[12] = Landmark(0.1f, 0.4f, 0f, 1f, 1f)
        lm[23] = Landmark(-0.1f, 0f, 0f, 1f, 1f)
        lm[24] = Landmark(0.1f, 0f, 0f, 1f, 1f)
        return lm
    }

    private class FakeTilt(
        private val gravity: FloatArray,
    ) : DeviceTiltPort {
        override val isAvailable: Boolean = true
        override val correctionRadians: Float = 0f
        override val rollDegrees: Float = 0f
        override val gravityVector: FloatArray = gravity
    }
}
