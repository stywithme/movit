package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.feature.library.components.ExerciseGridCard
import com.movit.feature.library.components.LibraryToolbar

@Composable
fun ExercisesLibraryScreen(
    state: LibraryListUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onChipSelected: (String) -> Unit,
    onSeeMore: () -> Unit,
    onItemClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MovitInnerPageHeader(
            onBack = onBack,
            backLabel = "Explore",
            modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MovitSpacing.lg),
        ) {
            when {
                state.isLoading -> MovitLoadingState(message = "Loading exercises…")
                state.errorMessage != null -> MovitErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                )
                else -> {
                    LibraryToolbar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        chips = state.chips,
                        selectedChip = state.selectedChip,
                        onChipSelected = onChipSelected,
                        resultSummary = "Showing ${state.items.size} of ${state.visibleCount} exercises",
                        searchPlaceholder = "Search exercises, muscles, category…",
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(vertical = MovitSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            ExerciseGridCard(
                                item = item,
                                onClick = { onItemClick(item.id) },
                            )
                        }
                    }
                    if (!state.showAll && state.visibleCount > state.items.size) {
                        MovitButton(
                            text = "See more exercises",
                            onClick = onSeeMore,
                            variant = MovitButtonVariant.Outlined,
                            modifier = Modifier.padding(bottom = MovitSpacing.lg),
                        )
                    }
                }
            }
        }
    }
}
