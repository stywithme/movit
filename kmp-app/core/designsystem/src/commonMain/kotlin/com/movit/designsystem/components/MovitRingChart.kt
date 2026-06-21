package com.movit.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun MovitRingChart(
    percent: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    label: String? = null,
) {
    val movit = MaterialTheme.movitColors
    val clamped = percent.coerceIn(0, 100)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(96.dp)) {
                val stroke = 9.dp.toPx()
                val radius = size.minDimension / 2f - stroke / 2f
                drawCircle(
                    color = movit.surface2,
                    radius = radius,
                    style = Stroke(width = stroke),
                )
                val sweep = 360f * clamped / 100f
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$clamped%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = movit.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(start = MovitSpacing.md)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.W700)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = movit.textTertiary,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
        }
    }
}
