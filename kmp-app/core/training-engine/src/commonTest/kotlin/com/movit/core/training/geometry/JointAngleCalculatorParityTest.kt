package com.movit.core.training.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden values for legacy AngleCalculator coordinate math after delegation.
 */
class JointAngleCalculatorParityTest {

    @Test
    fun acuteAngle_matchesLegacyAtan2Normalization() {
        val angle = JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(1f, 1f),
            pointB = PosePoint2D(0f, 0f),
            pointC = PosePoint2D(1f, 0f),
        )
        assertEquals(45.0, angle, absoluteTolerance = 0.1)
    }

    @Test
    fun obtuseAngle_isNormalizedToAcuteRange() {
        val angle = JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(-1f, 1f),
            pointB = PosePoint2D(0f, 0f),
            pointC = PosePoint2D(1f, 1f),
        )
        assertEquals(90.0, angle, absoluteTolerance = 0.1)
    }

    @Test
    fun zeroLengthVector3D_returnsZero() {
        val angle = JointAngleCalculator.angleDegrees3D(
            pointA = PosePoint3D(0f, 0f, 0f),
            pointB = PosePoint3D(0f, 0f, 0f),
            pointC = PosePoint3D(1f, 0f, 0f),
        )
        assertEquals(0.0, angle)
    }
}
