package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.movit.feature.library.components.LibraryToolbar
import com.movit.feature.library.components.WideWorkoutCard
import com.movit.resources.movitText

@Composable
fun WorkoutsLibraryScreen(
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            when {
                state.isLoading -> MovitLoadingState(message = movitText("library_loading_workouts"))
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
                            "library_result_workouts",
                            state.items.size,
                            state.visibleCount,
                        ),
                        searchPlaceholder = movitText("library_search_workouts"),
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
                            "library_result_workouts",
                            state.items.size,
                            state.visibleCount,
                        ),
                        searchPlaceholder = movitText("library_search_workouts"),
                        filterContentDescription = movitText("library_filter_button"),
                        onFilterClick = onFilterClick,
                        filterSheetVisible = state.filterSheetVisible,
                        onDismissFilterSheet = onDismissFilterSheet,
                        filterSheetTitle = movitText("library_filter_sheet_title"),
                    )
                    state.items.forEachIndexed { index, item ->
                        WideWorkoutCard(
                            item = item,
                            featured = index == 0,
                            featuredLabel = movitText("library_featured_workout"),
                            onClick = { onItemClick(item.id) },
                        )
                    }
                    if (!state.showAll && state.visibleCount > state.items.size) {
                        MovitButton(
                            text = movitText("library_see_more_workouts"),
                            onClick = onSeeMore,
                            variant = MovitButtonVariant.Outlined,
                        )
                    }
                }
            }
        }
    }
}
