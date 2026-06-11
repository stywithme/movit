package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

/**
 * Tinted quick-stats strip matching the prototype `.stats-strip` (02-session · 03-prepare):
 * position-tinted cells — cells 1·2 lime, cell 3 coral, cell 4 primary — each with a bold
 * colored value over a tinted background + a muted label. Reuses [MovitStatTileData].
 *
 * Distinct from [MovitStatTileRow] (neutral metric tiles for the Home `.metric-row`).
 */
@Composable
fun MovitStatsStrip(
    stats: List<MovitStatTileData>,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        stats.forEachIndexed { index, stat ->
            val bg: Color
            val border: Color
            val valueColor: Color
            when (index) {
                0, 1 -> {
                    bg = movit.limeTint
                    border = scheme.secondary
                    valueColor = movit.limeDeep
                }
                2 -> {
                    bg = movit.coralTint
                    border = scheme.tertiary
                    valueColor = scheme.tertiary
                }
                else -> {
                    bg = movit.primaryTint
                    border = scheme.primary
                    valueColor = scheme.primary
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MovitSpacing.sm, horizontal = MovitSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stat.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W800,
                        color = valueColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Text(
                        text = stat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = movit.textTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
