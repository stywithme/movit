package com.movit.designsystem.components

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure geometry for ROM arc/line indicators (legacy ArcRangeIndicator / LineRangeIndicator).
 * Canonical home is [com.movit.core.training.geometry] once Agent A lands engine-side builders.
 */
object SkeletonRomGeometry {
    const val DEFAULT_ARC_RADIUS_DP = 45f
    const val DEFAULT_STROKE_DP = 5f
    const val DEFAULT_MARKER_RADIUS_DP = 5f

    /**
     * Maps joint angle (0° = straight, 180° = folded) to Compose canvas degrees
     * (0° = 3 o'clock, clockwise positive).
     */
    fun jointAngleToCanvasDegrees(jointAngleDeg: Double): Float = (90.0 - jointAngleDeg).toFloat()

    fun canvasPointOnArc(
        centerX: Float,
        centerY: Float,
        radiusPx: Float,
        jointAngleDeg: Double,
    ): Offset {
        val radians = jointAngleToCanvasDegrees(jointAngleDeg).toDouble() * PI / 180.0
        return Offset(
            x = centerX + radiusPx * cos(radians).toFloat(),
            y = centerY + radiusPx * sin(radians).toFloat(),
        )
    }

    /**
     * Start/sweep pair for [androidx.compose.ui.graphics.drawscope.DrawScope.drawArc].
     */
    fun arcSweepForJointRange(
        rangeStartDeg: Double,
        rangeEndDeg: Double,
    ): Pair<Float, Float> {
        val start = jointAngleToCanvasDegrees(rangeEndDeg)
        val sweep = (rangeStartDeg - rangeEndDeg).toFloat()
        return start to sweep
    }

    fun lineProgress(
        currentAngleDeg: Float,
        rangeMinDeg: Float,
        rangeMaxDeg: Float,
        trackMinDeg: Float,
        trackMaxDeg: Float,
    ): Float {
        val span = (trackMaxDeg - trackMinDeg).coerceAtLeast(1f)
        return ((currentAngleDeg - trackMinDeg) / span).coerceIn(0f, 1f)
    }

    fun lerpAlongSegment(
        start: Offset,
        end: Offset,
        progress: Float,
    ): Offset = Offset(
        x = start.x + (end.x - start.x) * progress,
        y = start.y + (end.y - start.y) * progress,
    )
}
