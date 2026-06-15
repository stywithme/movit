package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

/**
 * Per-day calendar states. Mapped 1:1 from the backend week calendar — there is
 * intentionally no "missed" state: the program is completion-based and waits for
 * the user. Lateness is surfaced gently elsewhere, never as a punishing red day.
 */
enum class MovitWeekDayState {
    Completed,
    Today,
    InProgress,
    Upcoming,
    Rest,
    ActiveRecovery,
}

data class MovitWeekDay(
    val label: String,
    val dayNumber: String,
    val state: MovitWeekDayState,
    val isToday: Boolean = false,
    /** 0..1 ring/bar fill for partially-completed (InProgress) days. */
    val progress: Float? = null,
    val contentDescription: String? = null,
)

data class MovitWeekStripLegend(
    val done: String,
    val today: String,
    val upcoming: String,
    val rest: String,
)

@Composable
fun MovitWeekStrip(
    title: String,
    days: List<MovitWeekDay>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onPreviousWeek: (() -> Unit)? = null,
    onNextWeek: (() -> Unit)? = null,
    previousWeekContentDescription: String? = null,
    nextWeekContentDescription: String? = null,
    legend: MovitWeekStripLegend? = null,
    selectedIndex: Int? = null,
    onDayClick: ((Int) -> Unit)? = null,
) {
    MovitCard(modifier = modifier, variant = MovitCardVariant.Elevated) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W700,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.movitColors.textTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
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
            days.forEachIndexed { index, day ->
                WeekDayCell(
                    day = day,
                    selected = selectedIndex == index,
                    onClick = onDayClick?.let { { it(index) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (legend != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MovitSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
            ) {
                WeekLegendDot(legend.done, MaterialTheme.movitColors.success)
                WeekLegendDot(legend.today, MaterialTheme.colorScheme.primary)
                WeekLegendDot(legend.upcoming, MaterialTheme.movitColors.stroke)
                WeekLegendDot(legend.rest, MaterialTheme.movitColors.textQuaternary)
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    day: MovitWeekDay,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val ringColor = when {
        day.isToday -> MaterialTheme.colorScheme.primary
        selected -> movit.primaryPress
        else -> Color.Transparent
    }
    val cellModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(cellModifier)
            .clearAndSetSemantics {
                day.contentDescription?.let { contentDescription = it }
            }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Outer ring marks "today" / current selection without recolouring the cell.
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color.Transparent,
                border = if (ringColor != Color.Transparent) BorderStroke(2.dp, ringColor) else null,
            ) {}
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(12.dp),
                color = dayContainer(day.state, movit),
                contentColor = dayContent(day.state, movit),
                border = dayBorder(day.state, movit),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    WeekDayGlyph(day = day, movit = movit)
                    if (day.state == MovitWeekDayState.InProgress && day.progress != null) {
                        WeekDayProgressBar(progress = day.progress)
                    }
                }
            }
        }
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                movit.textTertiary
            },
            fontWeight = if (day.isToday) FontWeight.W700 else FontWeight.W600,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WeekDayGlyph(day: MovitWeekDay, movit: com.movit.designsystem.MovitExtendedColors) {
    when (day.state) {
        MovitWeekDayState.Completed ->
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        MovitWeekDayState.Rest ->
            RestDot(filled = true, color = movit.textQuaternary)
        MovitWeekDayState.ActiveRecovery ->
            RestDot(filled = false, color = MaterialTheme.colorScheme.primary)
        else ->
            Text(
                day.dayNumber,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.W700,
            )
    }
}

@Composable
private fun RestDot(filled: Boolean, color: Color) {
    Surface(
        modifier = Modifier.size(8.dp),
        shape = RoundedCornerShape(50),
        color = if (filled) color else Color.Transparent,
        border = if (filled) null else BorderStroke(1.5.dp, color),
    ) {}
}

@Composable
private fun WeekDayProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.movitColors.onInkVeil22, RoundedCornerShape(50)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
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
    MovitWeekDayState.Completed -> movit.success
    MovitWeekDayState.Today -> MaterialTheme.colorScheme.primary
    MovitWeekDayState.InProgress -> movit.primaryTint
    MovitWeekDayState.ActiveRecovery -> movit.primaryTint
    MovitWeekDayState.Rest -> movit.surface2
    MovitWeekDayState.Upcoming -> MaterialTheme.colorScheme.surface
}

@Composable
private fun dayContent(state: MovitWeekDayState, movit: com.movit.designsystem.MovitExtendedColors): Color = when (state) {
    MovitWeekDayState.Completed -> MaterialTheme.colorScheme.onPrimary
    MovitWeekDayState.Today -> MaterialTheme.colorScheme.onPrimary
    MovitWeekDayState.InProgress -> MaterialTheme.colorScheme.primary
    MovitWeekDayState.ActiveRecovery -> MaterialTheme.colorScheme.primary
    MovitWeekDayState.Rest -> movit.textQuaternary
    MovitWeekDayState.Upcoming -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun dayBorder(state: MovitWeekDayState, movit: com.movit.designsystem.MovitExtendedColors): BorderStroke? = when (state) {
    MovitWeekDayState.Upcoming -> BorderStroke(1.5.dp, movit.stroke)
    MovitWeekDayState.InProgress -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    MovitWeekDayState.Rest -> BorderStroke(1.5.dp, movit.divider)
    MovitWeekDayState.ActiveRecovery -> BorderStroke(1.5.dp, movit.primaryPress)
    else -> null
}
