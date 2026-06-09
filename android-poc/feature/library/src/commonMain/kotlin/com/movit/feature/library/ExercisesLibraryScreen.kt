package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.feature.library.components.ExerciseGridCard
import com.movit.feature.library.components.LibraryToolbar
import com.movit.resources.movitText

@Composable
fun ExercisesLibraryScreen(
    state: LibraryListUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (LibraryFilterChip) -> Unit,
    onFilterClick: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onSeeMore: () -> Unit,
    onClearFilters: () -> Unit,
    onItemClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MovitInnerPageHeader(
                onBack = onBack,
                backLabel = movitText("library_back_explore"),
                modifier = Modifier.weight(1f),
            )
            if (!state.isLoading && state.errorMessage == null) {
                MovitTag(
                    text = movitText("library_items_count", state.totalCount),
                    variant = MovitTagVariant.Blue,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MovitSpacing.lg),
        ) {
            when {
                state.isLoading -> MovitLoadingState(message = movitText("library_loading_exercises"))
                state.errorMessage != null -> MovitErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                )
                state.isFilteredEmpty -> {
                    LibraryToolbar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        filters = state.filters,
                        selectedFilter = state.selectedFilter,
                        accentFilter = state.accentFilter,
                        onFilterSelected = onFilterSelected,
                        resultSummary = movitText(
                            "library_result_exercises",
                            state.items.size,
                            state.visibleCount,
                        ),
                        searchPlaceholder = movitText("library_search_exercises"),
                        filterContentDescription = movitText("library_filter_button"),
                        onFilterClick = onFilterClick,
                        filterSheetVisible = state.filterSheetVisible,
                        onDismissFilterSheet = onDismissFilterSheet,
                        filterSheetTitle = movitText("library_filter_sheet_title"),
                    )
                    MovitEmptyState(
                        title = movitText("library_no_results"),
                        message = movitText("library_try_filter"),
                        actionLabel = movitText("library_clear_filters"),
                        onActionClick = onClearFilters,
                    )
                }
                else -> {
                    LibraryToolbar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        filters = state.filters,
                        selectedFilter = state.selectedFilter,
                        accentFilter = state.accentFilter,
                        onFilterSelected = onFilterSelected,
                        resultSummary = movitText(
                            "library_result_exercises",
                            state.items.size,
                            state.visibleCount,
                        ),
                        searchPlaceholder = movitText("library_search_exercises"),
                        filterContentDescription = movitText("library_filter_button"),
                        onFilterClick = onFilterClick,
                        filterSheetVisible = state.filterSheetVisible,
                        onDismissFilterSheet = onDismissFilterSheet,
                        filterSheetTitle = movitText("library_filter_sheet_title"),
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
                            text = movitText("library_see_more_exercises"),
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
