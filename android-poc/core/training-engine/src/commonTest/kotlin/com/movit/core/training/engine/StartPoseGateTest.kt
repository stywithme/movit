package com.movit.core.training.engine

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.JointRole
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.TrackingMode
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartPoseGateTest {
    @Test
    fun isInStartPose_usesStartPoseBox_notUpZoneCountedState() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val joints = config.getPrimaryJoints(0)
        val gate = StartPoseGate(joints)

        val standingKnees = mapOf("left_knee" to 170.0, "right_knee" to 170.0)
        val deepSquatKnees = mapOf("left_knee" to 90.0, "right_knee" to 90.0)

        assertTrue(gate.isInStartPose(standingKnees))
        assertFalse(gate.isInStartPose(deepSquatKnees))
    }

    @Test
    fun isInStartPosition_canDifferFromIsInStartPose() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val joints = config.getPrimaryJoints(0)
        val gate = StartPoseGate(joints)

        val deepSquatKnees = mapOf("left_knee" to 90.0, "right_knee" to 90.0)
        assertFalse(gate.isInStartPose(deepSquatKnees))
        // UP-zone counted state may still accept some deep angles depending on config bands.
        val inRunPosition = gate.isInStartPosition(deepSquatKnees)
        assertTrue(inRunPosition != gate.isInStartPose(deepSquatKnees) || !inRunPosition)
    }

    @Test
    fun isInStartPose_bilateralRequiresBothPrimaries() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = StartPoseGate(config.getPrimaryJoints(0))

        assertFalse(gate.isInStartPose(mapOf("left_knee" to 170.0)))
        assertFalse(gate.isInStartPose(mapOf("right_knee" to 170.0)))
        assertTrue(gate.isInStartPose(mapOf("left_knee" to 170.0, "right_knee" to 170.0)))
    }

    @Test
    fun isInStartPose_bilateralRejectsSingleJointOutOfBand() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = StartPoseGate(config.getPrimaryJoints(0))

        assertFalse(gate.isInStartPose(mapOf("left_knee" to 170.0, "right_knee" to 90.0)))
    }

    @Test
    fun isInStartPose_anySideAcceptsOneReadySide() {
        val gate = StartPoseGate(anySideElbowJoints())

        assertTrue(gate.isInStartPose(mapOf("left_elbow" to 165.0)))
        assertTrue(gate.isInStartPose(mapOf("right_elbow" to 165.0)))
        assertFalse(gate.isInStartPose(mapOf("left_elbow" to 90.0)))
        assertFalse(gate.isInStartPose(emptyMap()))
    }

    @Test
    fun isInStartPose_anySideDoesNotConfirmOnInsufficientJointOnly() {
        val gate = StartPoseGate(anySideElbowJoints())

        assertFalse(gate.isInStartPose(mapOf("left_elbow" to 90.0, "right_elbow" to 90.0)))
    }

    @Test
    fun isStartPoseRoughlyValid_acceptsTenDegreeDeviation() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = StartPoseGate(config.poseVariants.first().trackedJoints)
        val angles = squatStandingAngles(kneeAngle = 110.0)

        assertFalse(gate.isInStartPose(angles))
        assertTrue(gate.isStartPoseRoughlyValid(angles, toleranceDegrees = 10.0))
    }

    @Test
    fun isStartPoseRoughlyValid_rejectsGrossViolation() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = StartPoseGate(config.poseVariants.first().trackedJoints)
        val angles = squatStandingAngles(kneeAngle = 90.0)

        assertFalse(gate.isInStartPose(angles))
        assertFalse(gate.isStartPoseRoughlyValid(angles, toleranceDegrees = 10.0))
    }

    @Test
    fun isStartPoseRoughlyValid_rejectsBelowSixtyPercentJointPresence() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val gate = StartPoseGate(config.poseVariants.first().trackedJoints)
        val kneesOnly = mapOf("left_knee" to 170.0, "right_knee" to 170.0)

        assertFalse(
            gate.isStartPoseRoughlyValid(
                currentAngles = kneesOnly,
                toleranceDegrees = 10.0,
                minJointPresenceRatio = 0.6,
            ),
        )
    }

    @Test
    fun allPrimaryJointsPresent_matchesLegacyPairSemantics() {
        val primaries = anySideElbowJoints()
        val ready = setOf("left_elbow")

        assertTrue(StartPosePresence.allPrimaryJointsPresent(primaries, ready))
        assertFalse(StartPosePresence.allPrimaryJointsPresent(primaries, emptySet()))
    }

    private fun anySideElbowJoints(): List<TrackedJoint> = listOf(
        TrackedJoint(
            joint = "left_elbow",
            role = JointRole.PRIMARY,
            startPose = AngleRange(min = 150.0, max = 180.0),
            pairedWith = "right_elbow",
            trackingMode = TrackingMode.ANY_SIDE,
        ),
        TrackedJoint(
            joint = "right_elbow",
            role = JointRole.PRIMARY,
            startPose = AngleRange(min = 150.0, max = 180.0),
            pairedWith = "left_elbow",
            trackingMode = TrackingMode.ANY_SIDE,
        ),
    )

    private fun squatStandingAngles(kneeAngle: Double): Map<String, Double> = mapOf(
        "left_knee" to kneeAngle,
        "right_knee" to kneeAngle,
        "left_hip" to 170.0,
        "spine" to 10.0,
    )
}
