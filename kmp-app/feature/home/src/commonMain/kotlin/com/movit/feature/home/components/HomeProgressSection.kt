package com.movit.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.feature.home.HomeProgressUi
import com.movit.feature.home.HomeSummaryCalculator
import com.movit.resources.movitText

@Composable
fun HomeProgressSection(
    progress: HomeProgressUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = movitText("home_weekly_overview"),
            subtitle = movitText("home_progress"),
        )
        MovitStatTileRow(
            stats = listOf(
                MovitStatTileData(
                    value = HomeSummaryCalculator.weeklyCompletionLabel(
                        progress.weeklyCompletionPercent,
                    ),
                    label = movitText("home_weekly_completion"),
                ),
                MovitStatTileData(
                    value = progress.formScoreLabel,
                    label = movitText("home_metric_form_avg"),
                ),
                MovitStatTileData(
                    value = progress.streakDays.toString(),
                    label = movitText("home_metric_streak"),
                ),
            ),
        )
        MovitStatTileRow(
            stats = listOf(
                MovitStatTileData(
                    value = progress.activeMinutesLabel,
                    label = movitText("home_active_minutes"),
                ),
            ),
        )
    }
}
