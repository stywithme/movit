package com.movit.feature.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.feature.home.HomeLevelCardUi
import com.movit.resources.movitText

@Composable
fun HomeLevelCard(
    level: HomeLevelCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11yLabel = movitText("home_a11y_level_card")
    MovitDashboardHero(
        eyebrow = level.eyebrow,
        title = level.title,
        subtitle = level.subtitle,
        progressPercent = level.progressPercent,
        inkStyle = false,
        actionLabel = movitText("home_view_level"),
        onActionClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = a11yLabel },
    )
}
