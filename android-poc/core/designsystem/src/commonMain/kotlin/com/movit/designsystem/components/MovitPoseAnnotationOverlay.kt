package com.movit.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

private val accentColors = listOf(
    Color(0xFF64B5F6),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFFBA68C8),
    Color(0xFF4DD0E1),
)

/**
 * Debug Lab overlay — selective pose annotations only (angle arcs, position line, scene badge).
 * Does not draw a full skeleton unless [showFullSkeleton] is enabled.
 */
@Composable
fun MovitPoseAnnotationOverlay(
    landmarks: List<SkeletonLandmarkPoint>?,
    debug: SkeletonDebugOverlayState,
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = false,
    analysisImageWidth: Int = 0,
    analysisImageHeight: Int = 0,
    showFullSkeleton: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    val projector = rememberDebugOverlayLandmarkProjector(
        scaleMode = debug.scaleMode,
        isFrontCamera = isFrontCamera,
        imageWidth = analysisImageWidth,
        imageHeight = analysisImageHeight,
    )

    if (showFullSkeleton) {
        MovitSkeletonOverlay(
            landmarks = landmarks,
            modifier = modifier,
            parity = SkeletonOverlayParityState(mode = SkeletonOverlayMode.PREVIEW),
            landmarkProjector = projector,
        )
    }

    Canvas(modifier = modifier) {
        val points = landmarks ?: return@Canvas
        if (points.size < 33) return@Canvas

        val project: (Float, Float) -> Offset = { x, y ->
            projector(x, y, size.width, size.height)
        }

        debug.selectedJointHighlights.forEachIndexed { index, highlight ->
            drawAngleHighlight(highlight, points, project, textMeasurer, index)
        }
        debug.positionLine?.let { line ->
            drawPositionLine(line, points, project)
        }
        debug.sceneExpectation?.let { scene ->
            drawSceneBadge(scene, textMeasurer)
        }
    }
}

private fun DrawScope.drawAngleHighlight(
    highlight: DebugJointHighlight,
    points: List<SkeletonLandmarkPoint>,
    project: (Float, Float) -> Offset,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    colorIndex: Int,
) {
    val vertex = landmarkScreenPos(points, highlight.vertexIndex, project) ?: return
    val accent = highlight.color.takeIf { it != Color(0xFF64B5F6) }
        ?: accentColors[colorIndex % accentColors.size]
    val endpoints = listOf(highlight.endpointAIndex, highlight.endpointCIndex)
        .mapNotNull { landmarkScreenPos(points, it, project) }

    val longestEdgePx = endpoints.maxOfOrNull { ep ->
        hypot((ep.x - vertex.x).toDouble(), (ep.y - vertex.y).toDouble()).toFloat()
    } ?: 0f
    val circleRadius = (longestEdgePx * 0.05f).coerceIn(6.dp.toPx(), 200.dp.toPx())
    val dotRadius = circleRadius * 0.25f
    val lineStroke = 4.dp.toPx()
    val jointStroke = 3.dp.toPx()

    for (ep in endpoints) {
        drawLine(
            color = accent.copy(alpha = 0.38f),
            start = vertex,
            end = ep,
            strokeWidth = lineStroke,
            cap = StrokeCap.Round,
        )
        drawCircle(color = Color.White.copy(alpha = 0.7f), radius = dotRadius, center = ep)
    }

    drawCircle(color = accent.copy(alpha = 0.32f), radius = circleRadius, center = vertex)
    drawCircle(
        color = accent.copy(alpha = 0.86f),
        radius = circleRadius,
        center = vertex,
        style = Stroke(width = jointStroke),
    )

    highlight.angleDegrees?.let { angle ->
        val shortCode = highlight.jointCode.replace('_', ' ').take(12)
        val label = "$shortCode ${angle.roundToInt()}°"
        val textStyle = TextStyle(color = accent, fontSize = 14.sp)
        val layout = textMeasurer.measure(label, textStyle)
        val padH = 8.dp.toPx()
        val padV = 5.dp.toPx()
        val labelY = vertex.y - circleRadius - layout.size.height - 8.dp.toPx() - colorIndex * (layout.size.height + padV * 2f)
        val bgLeft = vertex.x - layout.size.width / 2f - padH
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.62f),
            topLeft = Offset(bgLeft, labelY - padV),
            size = Size(layout.size.width + padH * 2f, layout.size.height + padV * 2f),
            cornerRadius = CornerRadius(10.dp.toPx()),
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(vertex.x - layout.size.width / 2f, labelY),
        )
    }
}

private fun DrawScope.drawPositionLine(
    line: DebugPositionLine,
    points: List<SkeletonLandmarkPoint>,
    project: (Float, Float) -> Offset,
) {
    val start = landmarkScreenPos(points, line.primaryIndex, project) ?: return
    val end = landmarkScreenPos(points, line.secondaryIndex, project) ?: return
    val color = when (line.status) {
        DebugPositionLineStatus.PASS -> Color(0xFF4CAF50)
        DebugPositionLineStatus.FAIL -> Color(0xFFE91E63)
        DebugPositionLineStatus.FAIL_PENDING -> Color(0xFFFFB020)
        DebugPositionLineStatus.SKIPPED -> Color.White.copy(alpha = 0.4f)
    }
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round,
    )
    val markerRadius = 10.dp.toPx()
    drawCircle(color = color.copy(alpha = 0.35f), radius = markerRadius, center = start, style = Stroke(width = 3.dp.toPx()))
    drawCircle(color = color.copy(alpha = 0.35f), radius = markerRadius, center = end, style = Stroke(width = 3.dp.toPx()))
}

private fun DrawScope.drawSceneBadge(
    scene: DebugSceneOverlay,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val color = if (scene.allMatch) Color(0xFF4CAF50) else Color(0xFFE91E63)
    val label = buildString {
        append(scene.postureLabel.take(3).uppercase())
        append(" · ")
        append(scene.directionLabel.take(3).uppercase())
        append(" · ")
        append(scene.regionLabel.take(3).uppercase())
    }
    val textStyle = TextStyle(color = Color.White, fontSize = 11.sp)
    val layout = textMeasurer.measure(label, textStyle)
    val padH = 10.dp.toPx()
    val padV = 6.dp.toPx()
    val topLeft = Offset(12.dp.toPx(), 12.dp.toPx())
    drawRoundRect(
        color = color.copy(alpha = 0.82f),
        topLeft = topLeft,
        size = Size(layout.size.width + padH * 2f, layout.size.height + padV * 2f),
        cornerRadius = CornerRadius(8.dp.toPx()),
    )
    drawText(textLayoutResult = layout, topLeft = Offset(topLeft.x + padH, topLeft.y + padV))
}

private fun landmarkScreenPos(
    points: List<SkeletonLandmarkPoint>,
    index: Int,
    project: (Float, Float) -> Offset,
): Offset? {
    val lm = points.getOrNull(index) ?: return null
    if (!lm.visible) return null
    return project(lm.x, lm.y)
}
