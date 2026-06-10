package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

enum class MovitWeekDayState {
    Planned,
    Today,
    Done,
    Missed,
    Rest,
}

data class MovitWeekDay(
    val label: String,
    val dayNumber: String,
    val state: MovitWeekDayState,
)

data class MovitWeekStripLegend(
    val done: String,
    val today: String,
    val missed: String,
    val rest: String,
)

@Composable
fun MovitWeekStrip(
    title: String,
    days: List<MovitWeekDay>,
    modifier: Modifier = Modifier,
    onPreviousWeek: (() -> Unit)? = null,
    onNextWeek: (() -> Unit)? = null,
    previousWeekContentDescription: String? = null,
    nextWeekContentDescription: String? = null,
    legend: MovitWeekStripLegend? = null,
) {
    MovitCard(modifier = modifier, variant = MovitCardVariant.Elevated) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.W700,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onPreviousWeek?.invoke() },
                modifier = Modifier.size(MovitSpacing.minTouchTarget),
                enabled = onPreviousWeek != null,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = previousWeekContentDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { onNextWeek?.invoke() },
                modifier = Modifier.size(MovitSpacing.minTouchTarget),
                enabled = onNextWeek != null,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = nextWeekContentDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            days.forEach { day ->
                WeekDayCell(day = day, modifier = Modifier.weight(1f))
            }
        }

        if (legend != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MovitSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
            ) {
                WeekLegendDot(legend.done, MaterialTheme.colorScheme.secondary)
                WeekLegendDot(legend.today, MaterialTheme.colorScheme.primary)
                WeekLegendDot(legend.missed, MaterialTheme.colorScheme.tertiary)
                WeekLegendDot(legend.rest, MaterialTheme.movitColors.textQuaternary)
            }
        }
    }
}

@Composable
private fun WeekDayCell(day: MovitWeekDay, modifier: Modifier = Modifier) {
    val movit = MaterialTheme.movitColors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = dayContainer(day.state, movit),
            contentColor = dayContent(day.state),
            border = dayBorder(day.state, movit),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (day.state) {
                    MovitWeekDayState.Done -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    else -> Text(day.dayNumber, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.W700)
                }
            }
        }
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (day.state == MovitWeekDayState.Today) {
                MaterialTheme.colorScheme.primary
            } else {
                movit.textTertiary
            },
            fontWeight = if (day.state == MovitWeekDayState.Today) FontWeight.W700 else FontWeight.W600,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WeekLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(10.dp), shape = RoundedCornerShape(50), color = color) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.movitColors.textTertiary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun dayContainer(state: MovitWeekDayState, movit: com.movit.designsystem.MovitExtendedColors): Color = when (state) {
    MovitWeekDayState.Done -> MaterialTheme.colorScheme.secondary
    MovitWeekDayState.Today -> MaterialTheme.colorScheme.primary
    MovitWeekDayState.Missed -> movit.coralTint
    MovitWeekDayState.Rest -> movit.surface2
    MovitWeekDayState.Planned -> MaterialTheme.colorScheme.surface
}

@Composable
private fun dayContent(state: MovitWeekDayState): Color = when (state) {
    MovitWeekDayState.Done -> MaterialTheme.colorScheme.onSecondary
    MovitWeekDayState.Today -> MaterialTheme.colorScheme.onPrimary
    MovitWeekDayState.Missed -> MaterialTheme.colorScheme.tertiary
    MovitWeekDayState.Rest -> MaterialTheme.movitColors.textQuaternary
    MovitWeekDayState.Planned -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun dayBorder(state: MovitWeekDayState, movit: com.movit.designsystem.MovitExtendedColors): BorderStroke? = when (state) {
    MovitWeekDayState.Planned -> BorderStroke(1.5.dp, movit.stroke)
    MovitWeekDayState.Missed -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
    MovitWeekDayState.Rest -> BorderStroke(1.5.dp, movit.stroke)
    else -> null
}
