package com.movit.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitDashboardHeroVariant
import com.movit.feature.home.HomeProgressUi
import com.movit.feature.home.HomeSummaryCalculator

@Composable
fun HomeHeroSummary(
    greetingEyebrow: String,
    greetingTitle: String,
    greetingSubtitle: String,
    progress: HomeProgressUi?,
    modifier: Modifier = Modifier,
) {
    val progressPercent = progress?.let {
        HomeSummaryCalculator.clampPercent(it.weeklyCompletionPercent)
    } ?: 0

    MovitDashboardHero(
        eyebrow = greetingEyebrow,
        title = greetingTitle,
        subtitle = greetingSubtitle,
        progressPercent = progressPercent,
        variant = MovitDashboardHeroVariant.Default,
        modifier = modifier,
    )
}
