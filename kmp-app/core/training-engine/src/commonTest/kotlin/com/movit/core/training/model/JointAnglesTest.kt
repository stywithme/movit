package com.movit.core.training.model

import kotlin.test.Test
import kotlin.test.assertEquals

class JointAnglesTest {
    @Test
    fun getAngleAndToMapParity() {
        val angles = JointAngles(
            leftElbow = 45.0,
            rightKnee = 120.0,
            neckSpine = 10.0,
        )
        assertEquals(45.0, angles.getAngle("left_elbow"))
        assertEquals(120.0, angles.getAngle("right_knee"))
        assertEquals(10.0, angles.getAngle("neck_spine"))
        assertEquals(
            mapOf(
                "left_elbow" to 45.0,
                "right_knee" to 120.0,
                "neck_spine" to 10.0,
            ),
            angles.toMap(),
        )
    }
}
