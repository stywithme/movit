package com.movit.core.training.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure-Kotlin joint angle math shared between Android and iOS.
 * Legacy [com.movit.analysis.AngleCalculator] delegates here over time.
 */
object JointAngleCalculator {
    fun angleDegrees(
        pointA: PosePoint2D,
        pointB: PosePoint2D,
        pointC: PosePoint2D,
    ): Double {
        val baX = pointA.x - pointB.x
        val baY = pointA.y - pointB.y
        val bcX = pointC.x - pointB.x
        val bcY = pointC.y - pointB.y

        val angleA = atan2(baY.toDouble(), baX.toDouble())
        val angleC = atan2(bcY.toDouble(), bcX.toDouble())
        var angle = abs(angleA - angleC)
        if (angle > PI) {
            angle = (2.0 * PI) - angle
        }
        return angle * (180.0 / PI)
    }

    fun angleDegrees3D(
        pointA: PosePoint3D,
        pointB: PosePoint3D,
        pointC: PosePoint3D,
    ): Double {
        val baX = pointA.x - pointB.x
        val baY = pointA.y - pointB.y
        val baZ = pointA.z - pointB.z
        val bcX = pointC.x - pointB.x
        val bcY = pointC.y - pointB.y
        val bcZ = pointC.z - pointB.z

        val dot = baX * bcX + baY * bcY + baZ * bcZ
        val magBa = sqrt(baX * baX + baY * baY + baZ * baZ)
        val magBc = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)
        if (magBa == 0f || magBc == 0f) return 0.0

        val cosAngle = (dot / (magBa * magBc)).coerceIn(-1f, 1f)
        return acos(cosAngle.toDouble()) * (180.0 / PI)
    }
}
