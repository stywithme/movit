package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.feature.library.components.LibraryToolbar
import com.movit.feature.library.components.WideWorkoutCard

@Composable
fun WorkoutsLibraryScreen(
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            when {
                state.isLoading -> MovitLoadingState(message = "Loading workouts…")
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
                        resultSummary = "Showing ${state.items.size} of ${state.visibleCount} workouts",
                        searchPlaceholder = "Search muscle, workout, time…",
                    )
                    state.items.forEachIndexed { index, item ->
                        WideWorkoutCard(
                            item = item,
                            featured = index == 0,
                            onClick = { onItemClick(item.id) },
                        )
                    }
                    if (!state.showAll && state.visibleCount > state.items.size) {
                        MovitButton(
                            text = "See more workouts",
                            onClick = onSeeMore,
                            variant = MovitButtonVariant.Outlined,
                        )
                    }
                }
            }
        }
    }
}
