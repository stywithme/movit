package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import com.movit.core.training.session.SetupReadinessGate
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SetupReadinessGateTest {
    @Test
    fun jointGuidanceResolver_picksFurthestPrimaryJoint() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val joints = config.getPrimaryJoints(0)
        val guidance = SetupJointGuidanceResolver.resolveWorstJoint(
            angles = mapOf("left_knee" to 90.0),
            joints = joints,
            closeThresholdDegrees = 15.0,
        )
        assertNotNull(guidance)
        assertEquals("left_knee", guidance.jointCode)
        assertEquals(SetupGuidanceDirection.RAISE, guidance.direction)
    }

    @Test
    fun countdownPoseValid_usesStartPoseSemantics() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = SetupReadinessGate()
        val good = squatFrame(kneeAngle = 170.0)
        val bad = squatFrame(kneeAngle = 90.0)
        assertTrue(gate.isCountdownPoseValid(good.angles, config, 0))
        assertTrue(!gate.isCountdownPoseValid(bad.angles, config, 0))
    }

    @Test
    fun validate_populatesAxisStatuses() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = SetupReadinessGate()
        val frame = squatFrame(kneeAngle = 90.0)
        val result = gate.validate(
            angles = frame.angles,
            landmarks = frame.landmarks,
            exerciseConfig = config,
            poseVariantIndex = 0,
        )
        assertNotNull(result.axisStatuses)
        assertEquals(result.inStartPose, frame.angles.leftKnee?.let { it in 120.0..180.0 } == true)
    }

    private fun squatFrame(kneeAngle: Double) = run {
        val landmarks = List(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }.toMutableList()
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.45f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.45f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.45f, 0.78f, 0f, 1f, 1f)
        val frame = PoseFrameAssembler.assemble(landmarks, 1_000L, isFrontCamera = false)
        frame.copy(
            angles = frame.angles.copy(
                leftKnee = kneeAngle,
                rightKnee = kneeAngle,
            ),
        )
    }
}
