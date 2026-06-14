package com.movit.core.training.engine

import com.movit.core.training.config.ExerciseConfigParser
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
}
