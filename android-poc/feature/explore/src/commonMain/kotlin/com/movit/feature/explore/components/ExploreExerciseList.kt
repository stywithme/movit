package com.movit.feature.explore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitExerciseCard
import com.movit.designsystem.components.MovitMediaCard
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.explore.ExploreItemType
import com.movit.feature.explore.ExploreItemUi

@Composable
fun ExploreExerciseList(
    items: List<ExploreItemUi>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    MovitSectionHeader(
        title = "Popular movements",
        subtitle = "Exercises & sessions",
        modifier = modifier,
    )
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
        items.forEach { item ->
            when (item.type) {
                ExploreItemType.Exercise -> MovitExerciseCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    badge = item.badge,
                    metadata = item.metadata,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onItemClick(item.id) },
                )
                ExploreItemType.Workout,
                ExploreItemType.Program,
                -> MovitMediaCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    badge = item.badge,
                    metadata = item.metadata,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onItemClick(item.id) },
                )
            }
        }
    }
}
