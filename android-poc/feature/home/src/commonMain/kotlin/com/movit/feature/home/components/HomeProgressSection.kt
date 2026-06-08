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
            title = "Your progress",
            subtitle = "Journey",
        )
        MovitStatTileRow(
            stats = listOf(
                MovitStatTileData(
                    value = HomeSummaryCalculator.weeklyCompletionLabel(
                        progress.weeklyCompletionPercent,
                    ),
                    label = "This week",
                ),
                MovitStatTileData(
                    value = progress.formScoreLabel,
                    label = "Form avg",
                ),
                MovitStatTileData(
                    value = progress.streakDays.toString(),
                    label = "Day streak",
                ),
            ),
        )
        MovitStatTileRow(
            stats = listOf(
                MovitStatTileData(
                    value = progress.activeMinutesLabel,
                    label = "Active minutes",
                ),
            ),
        )
    }
}
