package com.movit.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun MovitLineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 110.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
    showEndDot: Boolean = true,
) {
    if (points.size < 2) return

    val dotHaloColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val width = size.width
        val chartHeight = size.height
        val stepX = width / (points.lastIndex.coerceAtLeast(1))
        val offsets = points.mapIndexed { index, value ->
            Offset(x = stepX * index, y = chartHeight * (1f - value.coerceIn(0f, 1f)))
        }

        val linePath = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fillPath = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(width, chartHeight)
            lineTo(0f, chartHeight)
            close()
        }

        drawPath(path = fillPath, color = fillColor)
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        if (showEndDot) {
            val end = offsets.last()
            drawCircle(color = dotHaloColor, radius = 4.5.dp.toPx(), center = end)
            drawCircle(
                color = lineColor,
                radius = 4.5.dp.toPx(),
                center = end,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
