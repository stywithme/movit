package com.movit.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

data class MovitBarChartItem(
    val value: Float,
    val label: String,
    val highlighted: Boolean = false,
)

@Composable
fun MovitBarChart(
    items: List<MovitBarChartItem>,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 110.dp,
) {
    val movit = MaterialTheme.movitColors
    val max = items.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { item ->
                val fraction = item.value / max
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height((chartHeight.value * fraction).dp.coerceAtLeast(8.dp)),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((chartHeight.value * fraction).dp.coerceAtLeast(8.dp)),
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        color = if (item.highlighted) MaterialTheme.colorScheme.secondary else movit.surface2,
                    ) {}
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                Text(
                    text = item.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = movit.textQuaternary,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
