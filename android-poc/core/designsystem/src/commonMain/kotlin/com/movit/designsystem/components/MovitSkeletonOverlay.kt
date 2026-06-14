package com.movit.designsystem.components



import androidx.compose.foundation.Canvas

import androidx.compose.runtime.Composable

import androidx.compose.runtime.remember

import androidx.compose.ui.Modifier

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.geometry.Rect

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.Path

import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.unit.dp



data class SkeletonLandmarkPoint(

    val x: Float,

    val y: Float,

    val visible: Boolean = true,

)



enum class SkeletonJointQuality {

    PERFECT,

    NORMAL,

    WARNING,

    DANGER,

}



data class SkeletonJointVisual(

    val jointCode: String,

    val quality: SkeletonJointQuality = SkeletonJointQuality.NORMAL,

    val dimmed: Boolean = false,

)



private const val LEFT_SHOULDER = 11

private const val RIGHT_SHOULDER = 12

private const val LEFT_ELBOW = 13

private const val RIGHT_ELBOW = 14

private const val LEFT_WRIST = 15

private const val RIGHT_WRIST = 16

private const val LEFT_HIP = 23

private const val RIGHT_HIP = 24

private const val LEFT_KNEE = 25

private const val RIGHT_KNEE = 26

private const val LEFT_ANKLE = 27

private const val RIGHT_ANKLE = 28



private val POSE_CONNECTIONS = listOf(

    LEFT_SHOULDER to RIGHT_SHOULDER,

    LEFT_SHOULDER to LEFT_ELBOW,

    LEFT_ELBOW to LEFT_WRIST,

    RIGHT_SHOULDER to RIGHT_ELBOW,

    RIGHT_ELBOW to RIGHT_WRIST,

    LEFT_SHOULDER to LEFT_HIP,

    RIGHT_SHOULDER to RIGHT_HIP,

    LEFT_HIP to RIGHT_HIP,

    LEFT_HIP to LEFT_KNEE,

    LEFT_KNEE to LEFT_ANKLE,

    RIGHT_HIP to RIGHT_KNEE,

    RIGHT_KNEE to RIGHT_ANKLE,

)



@Composable
fun MovitSkeletonOverlay(
    landmarks: List<SkeletonLandmarkPoint>?,
    modifier: Modifier = Modifier,
    jointStates: Map<String, SkeletonJointVisual> = emptyMap(),
    romIndicators: List<SkeletonRomIndicator> = emptyList(),
    /** Maps normalized analysis coords to canvas pixels; null = stretch to full canvas. */
    landmarkProjector: ((normalizedX: Float, normalizedY: Float, canvasWidth: Float, canvasHeight: Float) -> Offset)? = null,
) {

    val trackPath = remember { Path() }



    Canvas(modifier = modifier) {

        val points = landmarks ?: return@Canvas

        if (points.size < 33) return@Canvas



        fun point(index: Int): Offset? {
            val lm = points.getOrNull(index) ?: return null
            if (!lm.visible) return null
            return if (landmarkProjector != null) {
                landmarkProjector(lm.x, lm.y, size.width, size.height)
            } else {
                Offset(lm.x * size.width, lm.y * size.height)
            }
        }

        fun projectNormalized(x: Float, y: Float): Offset =
            if (landmarkProjector != null) {
                landmarkProjector(x, y, size.width, size.height)
            } else {
                Offset(x * size.width, y * size.height)
            }



        val stroke = 4.dp.toPx()

        val jointRadius = 6.dp.toPx()

        val romStroke = SkeletonRomGeometry.DEFAULT_STROKE_DP.dp.toPx()

        val arcRadius = SkeletonRomGeometry.DEFAULT_ARC_RADIUS_DP.dp.toPx()

        val markerRadius = SkeletonRomGeometry.DEFAULT_MARKER_RADIUS_DP.dp.toPx()



        for ((start, end) in POSE_CONNECTIONS) {

            val p1 = point(start) ?: continue

            val p2 = point(end) ?: continue

            drawLine(

                color = Color.White.copy(alpha = 0.75f),

                start = p1,

                end = p2,

                strokeWidth = stroke,

                cap = StrokeCap.Round,

            )

        }



        for (indicator in romIndicators) {

            val center = projectNormalized(indicator.centerX, indicator.centerY)

            val markerColor = Color(indicator.markerColorArgb)

            val trackColor = Color(indicator.trackColorArgb)

            when (indicator.style) {

                SkeletonRomIndicatorStyle.ARC -> {

                    val rect = Rect(

                        left = center.x - arcRadius,

                        top = center.y - arcRadius,

                        right = center.x + arcRadius,

                        bottom = center.y + arcRadius,

                    )

                    val (trackStart, trackSweep) = SkeletonRomGeometry.arcSweepForJointRange(

                        indicator.trackMinDeg.toDouble(),

                        indicator.trackMaxDeg.toDouble(),

                    )

                    drawArc(

                        color = trackColor,

                        startAngle = trackStart,

                        sweepAngle = trackSweep,

                        useCenter = false,

                        topLeft = rect.topLeft,

                        size = rect.size,

                        style = Stroke(width = romStroke, cap = StrokeCap.Round),

                    )

                    val (rangeStart, rangeSweep) = SkeletonRomGeometry.arcSweepForJointRange(

                        indicator.rangeMinDeg.toDouble(),

                        indicator.rangeMaxDeg.toDouble(),

                    )

                    drawArc(

                        color = markerColor.copy(alpha = 0.55f),

                        startAngle = rangeStart,

                        sweepAngle = rangeSweep,

                        useCenter = false,

                        topLeft = rect.topLeft,

                        size = rect.size,

                        style = Stroke(width = romStroke, cap = StrokeCap.Round),

                    )

                    if (indicator.showCurrentMarker) {

                        val marker = SkeletonRomGeometry.canvasPointOnArc(

                            centerX = center.x,

                            centerY = center.y,

                            radiusPx = arcRadius,

                            jointAngleDeg = indicator.currentAngleDeg.toDouble(),

                        )

                        drawCircle(

                            color = markerColor.copy(alpha = 0.35f),

                            radius = markerRadius * 2.4f,

                            center = marker,

                        )

                        drawCircle(color = markerColor, radius = markerRadius, center = marker)

                    }

                }

                SkeletonRomIndicatorStyle.LINE -> {

                    val end = projectNormalized(indicator.limbEndX, indicator.limbEndY)

                    trackPath.reset()

                    trackPath.moveTo(center.x, center.y)

                    trackPath.lineTo(end.x, end.y)

                    drawPath(

                        path = trackPath,

                        color = trackColor,

                        style = Stroke(width = romStroke, cap = StrokeCap.Round),

                    )

                    val progress = SkeletonRomGeometry.lineProgress(

                        currentAngleDeg = indicator.currentAngleDeg,

                        rangeMinDeg = indicator.rangeMinDeg,

                        rangeMaxDeg = indicator.rangeMaxDeg,

                        trackMinDeg = indicator.trackMinDeg,

                        trackMaxDeg = indicator.trackMaxDeg,

                    )

                    val marker = SkeletonRomGeometry.lerpAlongSegment(center, end, progress)

                    drawCircle(

                        color = markerColor.copy(alpha = 0.35f),

                        radius = markerRadius * 2.4f,

                        center = marker,

                    )

                    drawCircle(color = markerColor, radius = markerRadius, center = marker)

                }

            }

        }



        val jointIndices = listOf(

            LEFT_SHOULDER,

            RIGHT_SHOULDER,

            LEFT_ELBOW,

            RIGHT_ELBOW,

            LEFT_WRIST,

            RIGHT_WRIST,

            LEFT_HIP,

            RIGHT_HIP,

            LEFT_KNEE,

            RIGHT_KNEE,

            LEFT_ANKLE,

            RIGHT_ANKLE,

        )



        for (index in jointIndices) {

            val center = point(index) ?: continue

            val state = jointStates.values.firstOrNull { visual ->

                visual.jointCode.contains(jointLabel(index), ignoreCase = true)

            }

            val color = when {

                state?.dimmed == true -> Color.White.copy(alpha = 0.25f)

                state?.quality == SkeletonJointQuality.PERFECT -> Color(0xFF7CFF6B)

                state?.quality == SkeletonJointQuality.DANGER -> Color(0xFFFF5A5A)

                state?.quality == SkeletonJointQuality.WARNING -> Color(0xFFFFB020)

                else -> Color(0xFF4FC3F7)

            }

            drawCircle(color = color, radius = jointRadius, center = center)

            if (state?.quality == SkeletonJointQuality.PERFECT) {

                drawCircle(

                    color = color.copy(alpha = 0.35f),

                    radius = jointRadius * 2.2f,

                    center = center,

                )

            }

        }

    }

}



private fun jointLabel(index: Int): String = when (index) {

    LEFT_SHOULDER -> "left_shoulder"

    RIGHT_SHOULDER -> "right_shoulder"

    LEFT_ELBOW -> "left_elbow"

    RIGHT_ELBOW -> "right_elbow"

    LEFT_WRIST -> "left_wrist"

    RIGHT_WRIST -> "right_wrist"

    LEFT_HIP -> "left_hip"

    RIGHT_HIP -> "right_hip"

    LEFT_KNEE -> "left_knee"

    RIGHT_KNEE -> "right_knee"

    LEFT_ANKLE -> "left_ankle"

    RIGHT_ANKLE -> "right_ankle"

    else -> "joint_$index"

}


