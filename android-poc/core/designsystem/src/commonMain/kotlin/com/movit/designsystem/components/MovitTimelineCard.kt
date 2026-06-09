package com.movit.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

data class MovitTimelineEntry(
    val title: String,
    val subtitle: String,
    val dotColor: Color? = null,
)

@Composable
fun MovitTimelineList(
    entries: List<MovitTimelineEntry>,
    modifier: Modifier = Modifier,
) {
    MovitCard(modifier = modifier, variant = MovitCardVariant.Flat, contentPadding = MovitSpacing.lg) {
        entries.forEachIndexed { index, entry ->
            TimelineRow(entry = entry)
            if (index < entries.lastIndex) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(vertical = MovitSpacing.sm))
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: MovitTimelineEntry) {
    val movit = MaterialTheme.movitColors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp),
            shape = CircleShape,
            color = entry.dotColor ?: MaterialTheme.colorScheme.primary,
        ) {}
        Column(modifier = Modifier.padding(start = MovitSpacing.md)) {
            Text(text = entry.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.W700)
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = movit.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
