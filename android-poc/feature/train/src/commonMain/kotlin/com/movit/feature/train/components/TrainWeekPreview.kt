package com.movit.feature.train.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitWeekDay
import com.movit.designsystem.components.MovitWeekDayState
import com.movit.designsystem.components.MovitWeekStrip
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
    onPreviousWeek: (() -> Unit)? = null,
    onNextWeek: (() -> Unit)? = null,
) {
    MovitWeekStrip(
        modifier = modifier,
        title = week.title,
        days = week.days.map { it.toMovitWeekDay() },
        onPreviousWeek = onPreviousWeek.takeIf { canGoPrevious },
        onNextWeek = onNextWeek.takeIf { canGoNext },
        previousWeekContentDescription = movitText("train_week_previous"),
        nextWeekContentDescription = movitText("train_week_next"),
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
