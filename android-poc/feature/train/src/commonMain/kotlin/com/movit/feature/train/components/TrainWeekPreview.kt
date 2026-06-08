package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.train.TrainWeekDayState
import com.movit.feature.train.TrainWeekDayUi
import com.movit.feature.train.TrainWeekPreviewUi

@Composable
fun TrainWeekPreview(
    week: TrainWeekPreviewUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(title = week.title, subtitle = "A compact view of your training rhythm.")
        MovitCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                week.days.forEach { day ->
                    WeekDayCell(day = day, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    day: TrainWeekDayUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.medium,
            color = dayContainerColor(day.state),
            contentColor = dayContentColor(day.state),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = day.dayNumber,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun dayContainerColor(state: TrainWeekDayState) = when (state) {
    TrainWeekDayState.Done -> MaterialTheme.colorScheme.primaryContainer
    TrainWeekDayState.Today -> MaterialTheme.colorScheme.primary
    TrainWeekDayState.Planned -> MaterialTheme.colorScheme.surfaceVariant
    TrainWeekDayState.Rest -> MaterialTheme.colorScheme.secondaryContainer
    TrainWeekDayState.Missed -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun dayContentColor(state: TrainWeekDayState) = when (state) {
    TrainWeekDayState.Done -> MaterialTheme.colorScheme.onPrimaryContainer
    TrainWeekDayState.Today -> MaterialTheme.colorScheme.onPrimary
    TrainWeekDayState.Planned -> MaterialTheme.colorScheme.onSurfaceVariant
    TrainWeekDayState.Rest -> MaterialTheme.colorScheme.onSecondaryContainer
    TrainWeekDayState.Missed -> MaterialTheme.colorScheme.onErrorContainer
}
