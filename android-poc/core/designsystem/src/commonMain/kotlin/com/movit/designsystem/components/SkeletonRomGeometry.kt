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
    const val LINE_CENTER_ANGLE_DEG = 90f
    const val LINE_SMOOTHING_FACTOR = 0.12f
    const val LINE_SNAP_TO_ZERO_THRESHOLD = 0.04f
    const val LINE_UPPER_LENGTH_RATIO = 0.50f
    const val LINE_LOWER_LENGTH_RATIO = 0.75f
    const val LINE_TRACK_ALPHA = 0.51f
    const val LINE_TRACK_WIDTH_RATIO = 0.70f
    const val LINE_INDICATOR_WIDTH_MULTIPLIER = 1.30f

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

    fun stateColorArgb(state: SkeletonRomState): Long = when (state) {
        SkeletonRomState.PERFECT -> 0xFF4CAF50
        SkeletonRomState.NORMAL -> 0xFFFFEB3B
        SkeletonRomState.PAD -> 0xFFFF9800
        SkeletonRomState.WARNING -> 0xFFFF5252
        SkeletonRomState.DANGER -> 0xFFB71C1C
        SkeletonRomState.TRANSITION -> 0xFFFFEB3B
    }

    fun resolvedStateForAngle(
        angleDeg: Float,
        upRanges: SkeletonRomStateRanges?,
        downRanges: SkeletonRomStateRanges?,
    ): SkeletonRomState {
        val transitionMin = downRanges?.effectiveMaxDeg ?: 0f
        val transitionMax = upRanges?.effectiveMinDeg ?: 180f
        return when {
            angleDeg >= transitionMax && upRanges != null ->
                upRanges.determineState(angleDeg, SkeletonRomOutwardDirection.TOWARDS_HIGH)
            angleDeg <= transitionMin && downRanges != null ->
                downRanges.determineState(angleDeg, SkeletonRomOutwardDirection.TOWARDS_LOW)
            else -> SkeletonRomState.TRANSITION
        }
    }

    fun resolvedColorArgbForAngle(
        angleDeg: Float,
        upRanges: SkeletonRomStateRanges?,
        downRanges: SkeletonRomStateRanges?,
    ): Long = stateColorArgb(resolvedStateForAngle(angleDeg, upRanges, downRanges))

    fun visualAngleForIndicator(indicator: SkeletonRomIndicator): Float =
        if (indicator.invertAngles) 180f - indicator.currentAngleDeg else indicator.currentAngleDeg

    fun originalAngleForVisual(
        visualAngleDeg: Float,
        invertAngles: Boolean,
    ): Float = if (invertAngles) 180f - visualAngleDeg else visualAngleDeg

    fun lineEffectiveAngle(
        currentAngleDeg: Float,
        invertAngles: Boolean,
    ): Float = originalAngleForVisual(currentAngleDeg, invertAngles)

    fun lineRawLength(
        currentAngleDeg: Float,
        maxLengthPx: Float,
        invertAngles: Boolean,
    ): Float {
        val effectiveAngle = lineEffectiveAngle(currentAngleDeg, invertAngles)
        val deviation = kotlin.math.abs(effectiveAngle - LINE_CENTER_ANGLE_DEG)
        val normalized = (deviation / LINE_CENTER_ANGLE_DEG).coerceIn(0f, 1f)
        return normalized * maxLengthPx
    }
}
