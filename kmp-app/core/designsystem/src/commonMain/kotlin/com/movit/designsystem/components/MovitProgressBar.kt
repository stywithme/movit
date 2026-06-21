package com.movit.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun MovitProgressBar(
    progressPercent: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    showPercent: Boolean = false,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.movitColors.surface2,
) {
    val clamped = progressPercent.coerceIn(0, 100)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
    ) {
        if (label != null || showPercent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.movitColors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
                if (showPercent) {
                    Text(
                        text = "$clamped%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.movitColors.textSecondary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { clamped / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(MovitRadius.full)),
            color = progressColor,
            trackColor = trackColor,
        )
    }
}
