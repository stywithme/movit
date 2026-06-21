package com.movit.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

data class MovitStatTileData(
    val value: String,
    val label: String,
)

@Composable
fun MovitStatTileRow(
    stats: List<MovitStatTileData>,
    modifier: Modifier = Modifier,
    coloredValues: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        stats.forEachIndexed { index, stat ->
            // prototype `.metric-row`: values colored by position (primary · lime · coral)
            val valueColor = if (coloredValues) {
                when (index % 3) {
                    0 -> MaterialTheme.colorScheme.primary
                    1 -> MaterialTheme.movitColors.limeDeep
                    else -> MaterialTheme.colorScheme.tertiary
                }
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            MovitCard(
                modifier = Modifier.weight(1f),
                variant = MovitCardVariant.Filled,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MovitSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                ) {
                    Text(
                        text = stat.value,
                        style = MaterialTheme.typography.titleLarge,
                        color = valueColor,
                        fontWeight = FontWeight.W800,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
