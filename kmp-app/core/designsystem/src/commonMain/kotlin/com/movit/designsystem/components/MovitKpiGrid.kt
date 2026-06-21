package com.movit.designsystem.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

data class MovitKpiItem(
    val value: String,
    val label: String,
    val valueColor: Color? = null,
    val highlighted: Boolean = false,
)

@Composable
fun MovitKpiGrid(
    items: List<MovitKpiItem>,
    modifier: Modifier = Modifier,
) {
    val rows = items.chunked(2)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                rowItems.forEach { item ->
                    KpiCell(item = item, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KpiCell(item: MovitKpiItem, modifier: Modifier = Modifier) {
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MovitRadius.lg),
        color = if (item.highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(MovitSpacing.lg),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = item.value,
                style = MaterialTheme.typography.headlineSmall,
                color = item.valueColor ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.W800,
                textAlign = TextAlign.Center,
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = movit.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
        }
    }
}
