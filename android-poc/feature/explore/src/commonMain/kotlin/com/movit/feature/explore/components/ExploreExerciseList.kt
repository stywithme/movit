package com.movit.feature.explore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitExerciseCard
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.movitColors
import com.movit.feature.explore.ExploreItemUi
import com.movit.resources.movitText

@Composable
fun ExploreExerciseList(
    items: List<ExploreItemUi>,
    categoryChips: List<com.movit.feature.explore.ExploreCategoryChip>,
    selectedCategoryCode: String?,
    secondaryFiltersVisible: Boolean,
    onCategorySelected: (String?) -> Unit,
    onItemClick: (ExploreItemUi) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    MovitSectionHeader(
        title = movitText("explore_popular"),
        subtitle = movitText("explore_exercises"),
        actionLabel = movitText("explore_see_all"),
        onActionClick = onSeeAllClick,
        modifier = modifier,
    )
    if (secondaryFiltersVisible && categoryChips.isNotEmpty()) {
        ExploreExerciseChips(
            chips = categoryChips,
            selectedCategoryCode = selectedCategoryCode,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.padding(top = MovitSpacing.sm),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MovitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        items.take(4).chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                rowItems.forEach { item ->
                    MovitExerciseCard(
                        title = item.title,
                        subtitle = item.tags.joinToString(" · ").ifBlank {
                            item.metadata.joinToString(" · ").ifBlank { item.subtitle }
                        },
                        badge = item.badge,
                        imageUrl = item.imageUrl,
                        metadata = item.metadata,
                        modifier = Modifier.weight(1f),
                        onClick = { onItemClick(item) },
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ExploreWorkoutIntro(
    modifier: Modifier = Modifier,
) {
    Text(
        text = movitText("explore_workout_intro"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.movitColors.textSecondary,
        modifier = modifier.padding(bottom = MovitSpacing.sm),
    )
}
