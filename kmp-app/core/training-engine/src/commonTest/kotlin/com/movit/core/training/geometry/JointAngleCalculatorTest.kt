package com.movit.core.training.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class JointAngleCalculatorTest {

    @Test
    fun rightAngle_is90Degrees() {
        val angle = JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(0f, 1f),
            pointB = PosePoint2D(0f, 0f),
            pointC = PosePoint2D(1f, 0f),
        )
        assertEquals(90.0, angle, absoluteTolerance = 0.01)
    }

    @Test
    fun straightLine_is180Degrees() {
        val angle = JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(-1f, 0f),
            pointB = PosePoint2D(0f, 0f),
            pointC = PosePoint2D(1f, 0f),
        )
        assertEquals(180.0, angle, absoluteTolerance = 0.01)
    }

    @Test
    fun angle3D_rightAngle_is90Degrees() {
        val angle = JointAngleCalculator.angleDegrees3D(
            pointA = PosePoint3D(0f, 1f, 0f),
            pointB = PosePoint3D(0f, 0f, 0f),
            pointC = PosePoint3D(1f, 0f, 0f),
        )
        assertEquals(90.0, angle!!, absoluteTolerance = 0.01)
    }

    @Test
    fun angle3D_degenerate_returnsNull() {
        val angle = JointAngleCalculator.angleDegrees3D(
            pointA = PosePoint3D(0f, 0f, 0f),
            pointB = PosePoint3D(0f, 0f, 0f),
            pointC = PosePoint3D(1f, 0f, 0f),
        )
        assertEquals(null, angle)
    }
}
