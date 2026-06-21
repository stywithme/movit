package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitExtendedColors
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

/**
 * Per-day calendar states. Most plans are completion-based, but the component
 * also supports a Coral attention state when the backend explicitly marks an
 * unperformed workout day.
 */
enum class MovitWeekDayState {
    Completed,
    Today,
    InProgress,
    Upcoming,
    Missed,
    Rest,
    ActiveRecovery,
}

data class MovitWeekDay(
    val label: String,
    val dayNumber: String,
    val state: MovitWeekDayState,
    val isToday: Boolean = false,
    /** 0..1 rail fill for partially-completed (InProgress) days. */
    val progress: Float? = null,
    val contentDescription: String? = null,
)

data class MovitWeekStripLegend(
    val done: String,
    val today: String,
    val upcoming: String,
    val rest: String,
    val missed: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W800,
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
                    modifier = Modifier.size(22.dp),
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
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
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
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MovitSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                WeekLegendPill(legend.done, DayVisuals.completed())
                WeekLegendPill(legend.today, DayVisuals.today())
                legend.missed?.let { WeekLegendPill(it, DayVisuals.missed()) }
                WeekLegendPill(legend.upcoming, DayVisuals.upcoming())
                WeekLegendPill(legend.rest, DayVisuals.rest(MaterialTheme.movitColors))
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
    val visuals = day.visuals(movit)
    val isPendingToday = day.isToday &&
        (day.state == MovitWeekDayState.Today || day.state == MovitWeekDayState.InProgress)
    val ringColor = when {
        selected -> movit.primaryPress
        isPendingToday -> MaterialTheme.colorScheme.primary
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
            .padding(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = if (ringColor != Color.Transparent) BorderStroke(2.dp, ringColor) else null,
            ) {}
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(2.dp),
                shape = RoundedCornerShape(14.dp),
                color = visuals.container,
                contentColor = visuals.content,
                border = BorderStroke(1.3.dp, visuals.border),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        visuals.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = visuals.content,
                    )
                    if (day.state == MovitWeekDayState.InProgress && day.progress != null) {
                        WeekDayProgressBar(
                            progress = day.progress,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
        }
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isPendingToday) MaterialTheme.colorScheme.primary else visuals.muted,
            fontWeight = if (day.isToday || selected) FontWeight.W800 else FontWeight.W700,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WeekDayProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.movitColors.surface2, RoundedCornerShape(50)),
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
private fun WeekLegendPill(label: String, visuals: DayVisuals) {
    Surface(
        shape = RoundedCornerShape(50),
        color = visuals.legendContainer,
        contentColor = visuals.content,
        border = BorderStroke(1.dp, visuals.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                visuals.icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = visuals.content,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = visuals.content,
                fontWeight = FontWeight.W700,
            )
        }
    }
}

@Composable
private fun MovitWeekDay.visuals(movit: MovitExtendedColors): DayVisuals = when (state) {
    MovitWeekDayState.Completed -> DayVisuals.completed()
    MovitWeekDayState.Today -> DayVisuals.today()
    MovitWeekDayState.InProgress -> DayVisuals.today().copy(
        container = movit.primaryTint,
        content = MaterialTheme.colorScheme.primary,
        muted = movit.textSecondary,
        legendContainer = movit.primaryTint,
    )
    MovitWeekDayState.Upcoming -> DayVisuals.upcoming()
    MovitWeekDayState.Missed -> DayVisuals.missed()
    MovitWeekDayState.Rest -> DayVisuals.rest(movit)
    MovitWeekDayState.ActiveRecovery -> DayVisuals.rest(movit).copy(
        container = movit.primaryTint,
        border = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        content = MaterialTheme.colorScheme.primary,
        muted = movit.textSecondary,
        icon = Icons.Default.FitnessCenter,
        legendContainer = movit.primaryTint,
    )
}

private data class DayVisuals(
    val container: Color,
    val content: Color,
    val muted: Color,
    val border: Color,
    val legendContainer: Color,
    val icon: ImageVector,
) {
    companion object {
        @Composable
        fun completed(): DayVisuals {
            val movit = MaterialTheme.movitColors
            return DayVisuals(
                container = movit.success,
                content = MaterialTheme.colorScheme.onSecondary,
                muted = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.72f),
                border = movit.success.copy(alpha = 0.85f),
                legendContainer = movit.successTint,
                icon = Icons.Default.Check,
            )
        }

        @Composable
        fun today(): DayVisuals {
            val movit = MaterialTheme.movitColors
            return DayVisuals(
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                muted = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f),
                border = MaterialTheme.colorScheme.primary,
                legendContainer = movit.primaryTint,
                icon = Icons.Default.FitnessCenter,
            )
        }

        @Composable
        fun upcoming(): DayVisuals {
            val movit = MaterialTheme.movitColors
            return DayVisuals(
                container = movit.primaryTint,
                content = MaterialTheme.colorScheme.primary,
                muted = movit.textSecondary,
                border = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                legendContainer = movit.primaryTint,
                icon = Icons.Default.FitnessCenter,
            )
        }

        @Composable
        fun missed(): DayVisuals {
            val movit = MaterialTheme.movitColors
            return DayVisuals(
                container = movit.coralTint,
                content = MaterialTheme.colorScheme.tertiary,
                muted = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.78f),
                border = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.50f),
                legendContainer = movit.coralTint,
                icon = Icons.Default.Close,
            )
        }

        @Composable
        fun rest(movit: MovitExtendedColors): DayVisuals = DayVisuals(
            container = movit.surface2,
            content = movit.textSecondary,
            muted = movit.textTertiary,
            border = movit.divider,
            legendContainer = movit.surface2,
            icon = Icons.Default.Bedtime,
        )
    }
}
