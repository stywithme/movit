package com.movit.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

data class SkeletonLandmarkPoint(
    val x: Float,
    val y: Float,
    val visible: Boolean = true,
)

enum class SkeletonJointQuality {
    PERFECT,
    NORMAL,
    PAD,
    WARNING,
    DANGER,
}

data class SkeletonJointVisual(
    val jointCode: String,
    val quality: SkeletonJointQuality = SkeletonJointQuality.NORMAL,
    val dimmed: Boolean = false,
    val isPrimary: Boolean = true,
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

private val COLOR_POSITION_ERROR = Color(0xFFE91E63)
private val COLOR_POSITION_WARNING = Color(0xFFFFB020)
private val COLOR_POSITION_TIP = Color(0xFF64B5F6)
private val COLOR_LINE_DEFAULT = Color.White.copy(alpha = 0.25f)
private val COLOR_LINE_TRACKED = Color(0xFF64B5F6)
private val COLOR_JOINT_DEFAULT = Color.White.copy(alpha = 0.18f)

@Composable
fun MovitSkeletonOverlay(
    landmarks: List<SkeletonLandmarkPoint>?,
    modifier: Modifier = Modifier,
    parity: SkeletonOverlayParityState = SkeletonOverlayParityState(),
    /** @deprecated Prefer [parity.jointVisuals]; kept for incremental migration. */
    jointStates: Map<String, SkeletonJointVisual> = parity.jointVisuals,
    romIndicators: List<SkeletonRomIndicator> = emptyList(),
    /** Maps normalized analysis coords to canvas pixels; null = stretch to full canvas. */
    landmarkProjector: ((normalizedX: Float, normalizedY: Float, canvasWidth: Float, canvasHeight: Float) -> Offset)? = null,
    showBilateralSideHint: Boolean = true,
) {
    val overlayState = parity.copy(jointVisuals = jointStates.ifEmpty { parity.jointVisuals })
    val trackPath = remember { Path() }
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        if (overlayState.mode == SkeletonOverlayMode.SCENE_CHECK) return@Canvas

        val points = landmarks ?: return@Canvas
        if (points.size < 33) return@Canvas

        fun point(index: Int): Offset? {
            val lm = points.getOrNull(index) ?: return null
            if (!lm.visible) return null
            return project(lm.x, lm.y, landmarkProjector, size)
        }

        fun projectNormalized(x: Float, y: Float): Offset =
            project(x, y, landmarkProjector, size)

        when (overlayState.mode) {
            SkeletonOverlayMode.SETUP_ANGLES -> {
                drawSetupHighlights(
                    points = points,
                    highlights = overlayState.setupHighlights,
                    isBilateralFlipped = overlayState.isBilateralFlipped,
                    projector = landmarkProjector,
                    canvasSize = size,
                    textMeasurer = textMeasurer,
                )
            }
            SkeletonOverlayMode.TRAINING, SkeletonOverlayMode.PREVIEW -> {
                val trainingPolish = overlayState.mode == SkeletonOverlayMode.TRAINING
                drawSkeletonConnections(
                    jointVisuals = overlayState.jointVisuals,
                    isBilateralFlipped = overlayState.isBilateralFlipped,
                    trainingPolish = trainingPolish,
                    point = ::point,
                )
                if (trainingPolish) {
                    drawRomIndicators(
                        romIndicators = romIndicators,
                        trackPath = trackPath,
                        projectNormalized = ::projectNormalized,
                    )
                    drawPositionErrorMarks(
                        marks = overlayState.positionErrors,
                        point = ::point,
                    )
                }
                drawSkeletonJoints(
                    jointVisuals = overlayState.jointVisuals,
                    isBilateralFlipped = overlayState.isBilateralFlipped,
                    trainingPolish = trainingPolish,
                    point = ::point,
                )
                if (trainingPolish && showBilateralSideHint) {
                    overlayState.bilateralSideHint?.let { hint ->
                        drawBilateralSideHint(hint, textMeasurer)
                    }
                }
            }
            SkeletonOverlayMode.SCENE_CHECK -> Unit
        }
    }
}

private fun project(
    x: Float,
    y: Float,
    landmarkProjector: ((Float, Float, Float, Float) -> Offset)?,
    size: Size,
): Offset =
    if (landmarkProjector != null) {
        landmarkProjector(x, y, size.width, size.height)
    } else {
        Offset(x * size.width, y * size.height)
    }

private fun DrawScope.drawSkeletonConnections(
    jointVisuals: Map<String, SkeletonJointVisual>,
    isBilateralFlipped: Boolean,
    trainingPolish: Boolean,
    point: (Int) -> Offset?,
) {
    val stroke = 4.dp.toPx()
    val trackedStroke = 6.dp.toPx()

    for ((start, end) in POSE_CONNECTIONS) {
        val p1 = point(start) ?: continue
        val p2 = point(end) ?: continue
        val startJoint = landmarkIndexToJointCode(start)
        val endJoint = landmarkIndexToJointCode(end)
        val relevant = resolveJointVisual(startJoint, endJoint, jointVisuals, isBilateralFlipped)
        val (color, width, alpha) = when {
            relevant?.dimmed == true -> Triple(COLOR_LINE_DEFAULT, stroke, 0.35f)
            relevant != null && trainingPolish -> Triple(
                qualityColor(relevant.quality),
                trackedStroke,
                if (relevant.quality == SkeletonJointQuality.WARNING ||
                    relevant.quality == SkeletonJointQuality.DANGER ||
                    relevant.quality == SkeletonJointQuality.PAD
                ) {
                    0.75f
                } else {
                    0.5f
                },
            )
            trainingPolish && jointVisuals.isNotEmpty() -> Triple(COLOR_LINE_DEFAULT, stroke, 0.18f)
            else -> Triple(Color.White.copy(alpha = 0.75f), stroke, 1f)
        }
        drawLine(
            color = color.copy(alpha = alpha),
            start = p1,
            end = p2,
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSkeletonJoints(
    jointVisuals: Map<String, SkeletonJointVisual>,
    isBilateralFlipped: Boolean,
    trainingPolish: Boolean,
    point: (Int) -> Offset?,
) {
    val jointRadius = 6.dp.toPx()
    val trackedRadius = 8.dp.toPx()

    for (index in trackedJointIndices()) {
        val center = point(index) ?: continue
        val jointCode = landmarkIndexToJointCode(index) ?: continue
        val visual = jointVisualForLandmark(jointCode, jointVisuals, isBilateralFlipped)
        val radius = if (visual != null && trainingPolish) trackedRadius else jointRadius
        val color = when {
            visual?.dimmed == true -> COLOR_JOINT_DEFAULT.copy(alpha = 0.35f)
            visual != null && trainingPolish -> qualityColor(visual.quality)
            trainingPolish -> COLOR_JOINT_DEFAULT
            else -> Color.White.copy(alpha = 0.75f)
        }
        val alpha = when {
            visual?.dimmed == true -> 0.35f
            visual != null && trainingPolish &&
                (visual.quality == SkeletonJointQuality.WARNING ||
                    visual.quality == SkeletonJointQuality.DANGER ||
                    visual.quality == SkeletonJointQuality.PAD) -> 0.85f
            visual != null && trainingPolish -> 0.65f
            else -> 1f
        }
        drawCircle(color = color.copy(alpha = alpha), radius = radius, center = center)
        if (trainingPolish && visual?.quality == SkeletonJointQuality.PERFECT && visual.dimmed != true) {
            drawCircle(
                color = color.copy(alpha = 0.35f),
                radius = radius * 2.2f,
                center = center,
            )
        }
        if (trainingPolish && visual != null && visual.dimmed != true) {
            drawCircle(
                color = color.copy(alpha = alpha * 0.85f),
                radius = radius + 3.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawRomIndicators(
    romIndicators: List<SkeletonRomIndicator>,
    trackPath: Path,
    projectNormalized: (Float, Float) -> Offset,
) {
    val romStroke = SkeletonRomGeometry.DEFAULT_STROKE_DP.dp.toPx()
    val arcRadius = SkeletonRomGeometry.DEFAULT_ARC_RADIUS_DP.dp.toPx()
    val markerRadius = SkeletonRomGeometry.DEFAULT_MARKER_RADIUS_DP.dp.toPx()

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
                    drawCircle(color = markerColor.copy(alpha = 0.35f), radius = markerRadius * 2.4f, center = marker)
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
                drawCircle(color = markerColor.copy(alpha = 0.35f), radius = markerRadius * 2.4f, center = marker)
                drawCircle(color = markerColor, radius = markerRadius, center = marker)
            }
        }
    }
}

private fun DrawScope.drawPositionErrorMarks(
    marks: List<SkeletonPositionErrorMark>,
    point: (Int) -> Offset?,
) {
    if (marks.isEmpty()) return
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 8f), 0f)
    val warningDash = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
    val tipDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    for (mark in marks.take(2)) {
        val p1 = point(mark.landmark1Index) ?: continue
        val p2 = point(mark.landmark2Index) ?: continue
        val (color, strokeWidth, circleRadius, dash) = when (mark.severity) {
            SkeletonPositionSeverity.ERROR -> Quad(COLOR_POSITION_ERROR, 5.dp.toPx(), 22.dp.toPx(), dashEffect)
            SkeletonPositionSeverity.WARNING -> Quad(COLOR_POSITION_WARNING, 3.dp.toPx(), 18.dp.toPx(), warningDash)
            SkeletonPositionSeverity.TIP -> Quad(COLOR_POSITION_TIP, 2.dp.toPx(), 14.dp.toPx(), tipDash)
        }
        drawLine(
            color = color,
            start = p1,
            end = p2,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = dash,
        )
        drawCircle(color = color, radius = circleRadius, center = p1, style = Stroke(width = strokeWidth, pathEffect = dash))
        drawCircle(color = color, radius = circleRadius, center = p2, style = Stroke(width = strokeWidth, pathEffect = dash))
        if (mark.severity == SkeletonPositionSeverity.ERROR) {
            val fill = COLOR_POSITION_ERROR.copy(alpha = 0.24f)
            drawCircle(color = fill, radius = circleRadius - 5.dp.toPx(), center = p1)
            drawCircle(color = fill, radius = circleRadius - 5.dp.toPx(), center = p2)
        }
    }
}

private data class Quad(val color: Color, val stroke: Float, val radius: Float, val dash: PathEffect)

private fun DrawScope.drawSetupHighlights(
    points: List<SkeletonLandmarkPoint>,
    highlights: List<SkeletonSetupJointHighlight>,
    isBilateralFlipped: Boolean,
    projector: ((Float, Float, Float, Float) -> Offset)?,
    canvasSize: Size,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    if (highlights.isEmpty()) return

    fun screenPos(jointCode: String): Offset? {
        val index = effectiveLandmarkIndex(jointCode, isBilateralFlipped) ?: return null
        val lm = points.getOrNull(index) ?: return null
        if (!lm.visible) return null
        return project(lm.x, lm.y, projector, canvasSize)
    }

    val lineStroke = 6.dp.toPx()
    val jointStroke = 4.dp.toPx()

    for (highlight in highlights) {
        val center = screenPos(highlight.jointCode) ?: continue
        val color = setupLevelColor(highlight.level)
        for (adjacent in adjacentLandmarkIndices(highlight.jointCode)) {
            val adjJointCode = landmarkIndexToJointCode(adjacent) ?: continue
            val adj = screenPos(adjJointCode) ?: continue
            drawLine(color = color.copy(alpha = 0.78f), start = center, end = adj, strokeWidth = lineStroke, cap = StrokeCap.Round)
        }
        val radius = if (highlight.isPrimary) 14.dp.toPx() else 10.dp.toPx()
        drawCircle(color = color.copy(alpha = 0.4f), radius = radius, center = center)
        drawCircle(color = color.copy(alpha = 0.9f), radius = radius, center = center, style = Stroke(width = jointStroke))

        val angleLabel = highlight.currentAngleDeg?.let { "${it.toInt()}°" } ?: ""
        val arrow = when (highlight.direction) {
            SkeletonSetupDirection.RAISE -> " ↑"
            SkeletonSetupDirection.LOWER -> " ↓"
            SkeletonSetupDirection.HOLD -> if (highlight.level.equals("GREEN", ignoreCase = true)) " ✓" else ""
            null -> ""
        }
        if (angleLabel.isNotEmpty()) {
            val label = angleLabel + arrow
            val textStyle = TextStyle(color = color, fontSize = 16.sp)
            val layout = textMeasurer.measure(label, textStyle)
            val labelY = center.y - radius - 8.dp.toPx()
            drawRect(
                color = Color.Black.copy(alpha = 0.62f),
                topLeft = Offset(center.x - layout.size.width / 2f - 8.dp.toPx(), labelY - layout.size.height),
                size = Size(layout.size.width + 16.dp.toPx(), layout.size.height + 8.dp.toPx()),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(center.x - layout.size.width / 2f, labelY - layout.size.height),
            )
        }
    }
}

private fun DrawScope.drawBilateralSideHint(
    hint: SkeletonBilateralSideHint,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val textStyle = TextStyle(color = Color.White, fontSize = 14.sp)
    val layout = textMeasurer.measure(hint.label, textStyle)
    val padH = 12.dp.toPx()
    val padV = 6.dp.toPx()
    val top = 72.dp.toPx()
    val left = (size.width - layout.size.width) / 2f - padH
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(left, top),
        size = Size(layout.size.width + padH * 2, layout.size.height + padV * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(left + padH, top + padV),
    )
}

private fun qualityColor(quality: SkeletonJointQuality): Color = when (quality) {
    SkeletonJointQuality.PERFECT -> Color(0xFF00E676)
    SkeletonJointQuality.NORMAL -> Color(0xFF64B5F6)
    SkeletonJointQuality.PAD -> Color(0xFFFFB74D)
    SkeletonJointQuality.WARNING -> Color(0xFFFFD54F)
    SkeletonJointQuality.DANGER -> Color(0xFFFF5252)
}

private fun setupLevelColor(level: String): Color = when (level.uppercase()) {
    "GREEN" -> Color(0xFF00E676)
    "YELLOW" -> Color(0xFFFFD54F)
    "RED" -> Color(0xFFFF5252)
    else -> Color(0xFF64B5F6)
}

private fun trackedJointIndices(): List<Int> = listOf(
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
)

private fun landmarkIndexToJointCode(index: Int): String? = when (index) {
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
    else -> null
}

private fun mirrorJointCode(jointCode: String): String = when {
    jointCode.startsWith("left_") -> jointCode.replaceFirst("left_", "right_")
    jointCode.startsWith("right_") -> jointCode.replaceFirst("right_", "left_")
    else -> jointCode
}

private fun effectiveLandmarkIndex(jointCode: String, isBilateralFlipped: Boolean): Int? {
    val lookup = if (isBilateralFlipped) mirrorJointCode(jointCode) else jointCode
    return jointCodeToLandmarkIndex(lookup)
}

private fun jointCodeToLandmarkIndex(jointCode: String): Int? = when (jointCode.lowercase()) {
    "left_shoulder" -> LEFT_SHOULDER
    "right_shoulder" -> RIGHT_SHOULDER
    "left_elbow" -> LEFT_ELBOW
    "right_elbow" -> RIGHT_ELBOW
    "left_wrist" -> LEFT_WRIST
    "right_wrist" -> RIGHT_WRIST
    "left_hip" -> LEFT_HIP
    "right_hip" -> RIGHT_HIP
    "left_knee" -> LEFT_KNEE
    "right_knee" -> RIGHT_KNEE
    "left_ankle" -> LEFT_ANKLE
    "right_ankle" -> RIGHT_ANKLE
    else -> null
}

private fun jointVisualForLandmark(
    landmarkJointCode: String?,
    jointVisuals: Map<String, SkeletonJointVisual>,
    isBilateralFlipped: Boolean,
): SkeletonJointVisual? {
    if (landmarkJointCode == null) return null
    val direct = jointVisuals[landmarkJointCode]
    if (direct != null) return direct
    val mirrored = mirrorJointCode(landmarkJointCode)
    return jointVisuals[mirrored] ?: jointVisuals.entries.firstOrNull { (code, _) ->
        effectiveLandmarkIndex(code, isBilateralFlipped) == jointCodeToLandmarkIndex(landmarkJointCode)
    }?.value
}

private fun resolveJointVisual(
    startJoint: String?,
    endJoint: String?,
    jointVisuals: Map<String, SkeletonJointVisual>,
    isBilateralFlipped: Boolean,
): SkeletonJointVisual? {
    val startVisual = jointVisualForLandmark(startJoint, jointVisuals, isBilateralFlipped)
    if (startVisual != null) return startVisual
    return jointVisualForLandmark(endJoint, jointVisuals, isBilateralFlipped)
}

private fun adjacentLandmarkIndices(jointCode: String): List<Int> = when (jointCode.lowercase()) {
    "left_elbow" -> listOf(LEFT_SHOULDER, LEFT_WRIST)
    "right_elbow" -> listOf(RIGHT_SHOULDER, RIGHT_WRIST)
    "left_shoulder" -> listOf(LEFT_ELBOW, LEFT_HIP)
    "right_shoulder" -> listOf(RIGHT_ELBOW, RIGHT_HIP)
    "left_wrist" -> listOf(LEFT_ELBOW)
    "right_wrist" -> listOf(RIGHT_ELBOW)
    "left_knee" -> listOf(LEFT_HIP, LEFT_ANKLE)
    "right_knee" -> listOf(RIGHT_HIP, RIGHT_ANKLE)
    "left_hip" -> listOf(LEFT_KNEE, LEFT_SHOULDER)
    "right_hip" -> listOf(RIGHT_KNEE, RIGHT_SHOULDER)
    "left_ankle" -> listOf(LEFT_KNEE)
    "right_ankle" -> listOf(RIGHT_KNEE)
    "spine" -> listOf(LEFT_HIP, RIGHT_HIP)
    else -> emptyList()
}
