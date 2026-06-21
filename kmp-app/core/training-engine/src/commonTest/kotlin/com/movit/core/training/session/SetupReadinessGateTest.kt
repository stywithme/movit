package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun isSetupPoseConfirmed_anySideAcceptsSingleReadyElbow() {
        val gate = SetupReadinessGate()
        val config = anySideElbowExerciseConfig()

        assertTrue(gate.isSetupPoseConfirmed(JointAngles(leftElbow = 165.0), config, 0))
        assertFalse(gate.isSetupPoseConfirmed(JointAngles(leftElbow = 90.0), config, 0))
    }

    @Test
    fun validate_anySideSetupUsesHigherConfidenceSide() {
        val gate = SetupReadinessGate(
            config = SetupValidationConfig(
                windowSize = 1,
                requiredValid = 1,
                cameraCheckWindowSize = 1,
                cameraCheckRequired = 1,
            ),
        )
        val config = anySideElbowExerciseConfig()

        val result = gate.validate(
            angles = JointAngles(leftElbow = 165.0, rightElbow = 90.0),
            landmarks = elbowLandmarks(leftVisibility = 0.1f, rightVisibility = 0.95f),
            exerciseConfig = config,
            poseVariantIndex = 0,
        )

        assertFalse(result.inStartPose)
        assertFalse(result.isConfirmed)
        assertEquals("right_elbow", result.worstJointGuidance?.jointCode)
    }

    @Test
    fun validate_anySideSetupIgnoresHiddenPartnerGuidanceWhenVisibleSideReady() {
        val gate = SetupReadinessGate(
            config = SetupValidationConfig(
                windowSize = 1,
                requiredValid = 1,
                cameraCheckWindowSize = 1,
                cameraCheckRequired = 1,
            ),
        )
        val config = anySideElbowExerciseConfig()

        val result = gate.validate(
            angles = JointAngles(leftElbow = 90.0, rightElbow = 165.0),
            landmarks = elbowLandmarks(leftVisibility = 0.1f, rightVisibility = 0.95f),
            exerciseConfig = config,
            poseVariantIndex = 0,
        )

        assertTrue(result.inStartPose)
        assertTrue(result.isConfirmed)
        assertTrue(result.jointGuidanceRows.none { it.jointCode == "left_elbow" })
    }

    @Test
    fun countdownPoseValid_anySideUsesHigherConfidenceSide() {
        val gate = SetupReadinessGate()
        val config = anySideElbowExerciseConfig()
        val landmarks = elbowLandmarks(leftVisibility = 0.1f, rightVisibility = 0.95f)

        assertTrue(
            gate.isCountdownPoseValid(
                angles = JointAngles(leftElbow = 90.0, rightElbow = 165.0),
                exerciseConfig = config,
                poseVariantIndex = 0,
                landmarks = landmarks,
            ),
        )
        assertFalse(
            gate.isCountdownPoseValid(
                angles = JointAngles(leftElbow = 165.0, rightElbow = 90.0),
                exerciseConfig = config,
                poseVariantIndex = 0,
                landmarks = landmarks,
            ),
        )
    }

    @Test
    fun isSetupPoseConfirmed_bilateralRequiresBothKnees() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = SetupReadinessGate()
        val good = squatFrame(kneeAngle = 170.0)

        assertTrue(gate.isSetupPoseConfirmed(good.angles, config, 0))
        assertFalse(
            gate.isSetupPoseConfirmed(
                good.angles.copy(leftKnee = 170.0, rightKnee = null),
                config,
                0,
            ),
        )
    }

    @Test
    fun countdownPoseValid_bilateralRequiresBothKnees() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = SetupReadinessGate()
        val good = squatFrame(kneeAngle = 170.0)

        assertTrue(gate.isCountdownPoseValid(good.angles, config, 0))
        assertFalse(
            gate.isCountdownPoseValid(
                good.angles.copy(leftKnee = 170.0, rightKnee = null),
                config,
                0,
            ),
        )
    }

    @Test
    fun countdownPoseValid_acceptsTenDegreeDeviationRejectedByStrictSetup() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = SetupReadinessGate(
            config = SetupValidationConfig(
                closeThresholdDegrees = 15.0,
                countdownAngleToleranceDegrees = 10.0,
            ),
        )
        val drifted = squatFrame(kneeAngle = 110.0)

        assertTrue(gate.isCountdownPoseValid(drifted.angles, config, 0))
        assertFalse(gate.isSetupPoseConfirmed(drifted.angles, config, 0))
    }

    @Test
    fun countdownPoseValid_rejectsGrossViolation() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = SetupReadinessGate()
        val bad = squatFrame(kneeAngle = 90.0)

        assertFalse(gate.isCountdownPoseValid(bad.angles, config, 0))
        assertFalse(gate.isSetupPoseConfirmed(bad.angles, config, 0))
    }

    @Test
    fun countdownPoseValid_rejectsInsufficientJointPresence() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = SetupReadinessGate(
            config = SetupValidationConfig(countdownMinJointPresenceRatio = 0.6),
        )
        val kneesOnly = squatFrame(kneeAngle = 170.0).angles.copy(
            leftHip = null,
            spine = null,
        )

        assertFalse(gate.isCountdownPoseValid(kneesOnly, config, 0))
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
        assertEquals(
            result.inStartPose,
            frame.angles.leftKnee?.let { it in 120.0..180.0 } == true &&
                frame.angles.rightKnee?.let { it in 120.0..180.0 } == true,
        )
    }

    private fun anySideElbowExerciseConfig() = ExerciseConfigParser.parseConfigJson(
        """
        {
          "name": {"ar": "اختبار", "en": "Test"},
          "countingMethod": "up_down",
          "poseVariants": [{
            "expectedPostures": ["any"],
            "expectedDirections": ["any"],
            "expectedRegions": ["any"],
            "trackedJoints": [
              {
                "joint": "left_elbow",
                "role": "primary",
                "startPose": {"min": 150, "max": 180},
                "pairedWith": "right_elbow",
                "trackingMode": "any_side",
                "upRange": {"perfect": {"min": 130, "max": 180}},
                "downRange": {"perfect": {"min": 60, "max": 100}}
              },
              {
                "joint": "right_elbow",
                "role": "primary",
                "startPose": {"min": 150, "max": 180},
                "pairedWith": "left_elbow",
                "trackingMode": "any_side",
                "upRange": {"perfect": {"min": 130, "max": 180}},
                "downRange": {"perfect": {"min": 60, "max": 100}}
              }
            ]
          }]
        }
        """.trimIndent(),
    )

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
                leftHip = 170.0,
                spine = 10.0,
            ),
        )
    }

    private fun elbowLandmarks(leftVisibility: Float, rightVisibility: Float): List<Landmark> {
        val landmarks = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
        listOf(
            PoseLandmarkIndices.LEFT_SHOULDER,
            PoseLandmarkIndices.LEFT_ELBOW,
            PoseLandmarkIndices.LEFT_WRIST,
        ).forEach { index ->
            landmarks[index] = landmarks[index].copy(visibility = leftVisibility)
        }
        listOf(
            PoseLandmarkIndices.RIGHT_SHOULDER,
            PoseLandmarkIndices.RIGHT_ELBOW,
            PoseLandmarkIndices.RIGHT_WRIST,
        ).forEach { index ->
            landmarks[index] = landmarks[index].copy(visibility = rightVisibility)
        }
        return landmarks
    }
}
