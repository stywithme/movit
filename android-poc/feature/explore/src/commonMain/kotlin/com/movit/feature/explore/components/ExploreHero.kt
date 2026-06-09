package com.movit.feature.explore.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitHeroCard
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.explore.ExploreItemType
import com.movit.feature.explore.ExploreItemUi
import com.movit.resources.movitText

@Composable
fun ExploreHero(
    items: List<ExploreItemUi>,
    onItemClick: (ExploreItemUi) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    MovitSectionHeader(
        title = movitText("explore_best_start"),
        subtitle = movitText("explore_recommended"),
        actionLabel = movitText("explore_see_all"),
        onActionClick = onSeeAllClick,
        modifier = modifier,
    )
    items.firstOrNull()?.let { item ->
        MovitHeroCard(
            eyebrow = item.badge ?: movitText("explore_smart_pick"),
            title = item.title,
            membersLabel = item.subtitle.ifBlank { item.metadata.joinToString(" · ") },
            ctaLabel = movitText("explore_open_workout"),
            imageUrl = item.imageUrl,
            showPlayFab = item.type == ExploreItemType.Workout,
            onCtaClick = { onItemClick(item) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
