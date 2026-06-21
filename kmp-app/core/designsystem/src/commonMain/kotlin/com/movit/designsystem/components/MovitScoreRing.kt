package com.movit.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movit.designsystem.movitColors

@Composable
fun MovitScoreRing(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    label: String? = null,
    contentDescription: String? = null,
) {
    val movit = MaterialTheme.movitColors
    val clamped = percent.coerceIn(0, 100)

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 7.dp.toPx()
            val radius = this.size.minDimension / 2f - stroke / 2f
            drawCircle(color = movit.surface2, radius = radius, style = Stroke(width = stroke))
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * clamped / 100f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$clamped%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
            )
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = movit.textTertiary,
                )
            }
        }
    }
}
