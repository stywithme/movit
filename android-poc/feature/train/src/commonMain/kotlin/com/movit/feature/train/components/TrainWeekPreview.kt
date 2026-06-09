package com.movit.feature.train.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitWeekDay
import com.movit.designsystem.components.MovitWeekDayState
import com.movit.designsystem.components.MovitWeekStrip
import com.movit.feature.train.TrainWeekDayState
import com.movit.feature.train.TrainWeekDayUi
import com.movit.feature.train.TrainWeekPreviewUi

@Composable
fun TrainWeekPreview(
    week: TrainWeekPreviewUi,
    modifier: Modifier = Modifier,
) {
    MovitWeekStrip(
        modifier = modifier,
        title = week.title,
        days = week.days.map { it.toMovitWeekDay() },
    )
}

private fun TrainWeekDayUi.toMovitWeekDay(): MovitWeekDay = MovitWeekDay(
    label = label,
    dayNumber = dayNumber,
    state = when (state) {
        TrainWeekDayState.Done -> MovitWeekDayState.Done
        TrainWeekDayState.Today -> MovitWeekDayState.Today
        TrainWeekDayState.Planned -> MovitWeekDayState.Planned
        TrainWeekDayState.Rest -> MovitWeekDayState.Rest
        TrainWeekDayState.Missed -> MovitWeekDayState.Missed
    },
)
