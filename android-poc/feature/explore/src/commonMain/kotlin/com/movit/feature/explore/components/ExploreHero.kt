package com.movit.feature.explore.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitMediaCard
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.explore.ExploreItemUi

@Composable
fun ExploreHero(
    items: List<ExploreItemUi>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    MovitSectionHeader(
        title = "Best place to start",
        subtitle = "Recommended",
        modifier = modifier,
    )
    items.forEach { item ->
        MovitMediaCard(
            title = item.title,
            subtitle = item.subtitle,
            metadata = item.metadata,
            badge = item.badge,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onItemClick(item.id) },
        )
    }
}
