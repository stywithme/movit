package com.movit.feature.train.components

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
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonSize
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitWeekDay
import com.movit.designsystem.components.MovitWeekDayState
import com.movit.designsystem.components.MovitWeekStrip
import com.movit.designsystem.components.MovitWeekStripLegend
import com.movit.designsystem.movitColors
import com.movit.feature.train.TrainWeekDayDetailUi
import com.movit.feature.train.TrainWeekDayState
import com.movit.feature.train.TrainWeekDayUi
import com.movit.feature.train.TrainWeekPreviewUi
import com.movit.resources.movitText

@Composable
fun TrainWeekPreview(
    week: TrainWeekPreviewUi,
    modifier: Modifier = Modifier,
    canGoPrevious: Boolean = false,
    canGoNext: Boolean = false,
    selectedDayIndex: Int? = null,
    onPreviousWeek: (() -> Unit)? = null,
    onNextWeek: (() -> Unit)? = null,
    onDayClick: ((Int) -> Unit)? = null,
    onDayAction: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        MovitWeekStrip(
            modifier = modifier,
            title = week.title,
            subtitle = week.subtitle,
            days = week.days.map { it.toMovitWeekDay() },
            selectedIndex = selectedDayIndex,
            onDayClick = onDayClick,
            onPreviousWeek = onPreviousWeek.takeIf { canGoPrevious },
            onNextWeek = onNextWeek.takeIf { canGoNext },
            previousWeekContentDescription = movitText("train_week_previous"),
            nextWeekContentDescription = movitText("train_week_next"),
            legend = MovitWeekStripLegend(
                done = movitText("ds_week_legend_done"),
                today = movitText("ds_week_legend_today"),
                upcoming = movitText("ds_week_legend_upcoming"),
                rest = movitText("ds_week_legend_rest"),
            ),
        )

        val selectedDetail = selectedDayIndex
            ?.let { week.days.getOrNull(it) }
            ?.detail
        if (selectedDetail != null) {
            TrainDayDetailCard(detail = selectedDetail, onAction = onDayAction)
        }
    }
}

@Composable
private fun TrainDayDetailCard(
    detail: TrainWeekDayDetailUi,
    onAction: (() -> Unit)?,
) {
    MovitCard(variant = MovitCardVariant.Filled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textTertiary,
                    fontWeight = FontWeight.W700,
                )
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W700,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = detail.infoLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (detail.actionLabel != null && onAction != null) {
                MovitButton(
                    text = detail.actionLabel,
                    onClick = onAction,
                    variant = if (detail.isCompleted) MovitButtonVariant.Tonal else MovitButtonVariant.Filled,
                    size = MovitButtonSize.Small,
                )
            }
        }
    }
}

private fun TrainWeekDayUi.toMovitWeekDay(): MovitWeekDay = MovitWeekDay(
    label = label,
    dayNumber = dayNumber,
    state = when (state) {
        TrainWeekDayState.Completed -> MovitWeekDayState.Completed
        TrainWeekDayState.Today -> MovitWeekDayState.Today
        TrainWeekDayState.InProgress -> MovitWeekDayState.InProgress
        TrainWeekDayState.Upcoming -> MovitWeekDayState.Upcoming
        TrainWeekDayState.Rest -> MovitWeekDayState.Rest
        TrainWeekDayState.ActiveRecovery -> MovitWeekDayState.ActiveRecovery
    },
    isToday = isToday,
    progress = progress,
    contentDescription = buildString {
        append(label)
        detail?.let {
            append(", ")
            append(it.statusLabel)
            if (it.isWorkout) {
                append(", ")
                append(it.title)
            }
        }
    },
)
