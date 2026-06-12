package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitFilterRow
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgramCard
import com.movit.feature.library.components.LibraryToolbar
import com.movit.resources.movitText

@Composable
fun ProgramListScreen(
    state: ProgramListUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onChipSelected: (String) -> Unit,
    onLoadMore: () -> Unit,
    onProgramClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MovitInnerPageHeader(
            onBack = onBack,
            title = movitText("program_flow_list_title"),
            modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
        )
        when {
            state.isLoading -> MovitLoadingState(message = movitText("program_flow_loading"))
            state.errorMessage != null -> MovitErrorState(
                title = movitText("common_error_title"),
                message = state.errorMessage,
                actionLabel = movitText("common_retry"),
                onRetry = onRetry,
            )
            state.filteredPrograms.isEmpty() -> {
                LibraryToolbar(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    filters = emptyList(),
                    selectedFilter = LibraryFilterChip.All,
                    onFilterSelected = {},
                    resultSummary = movitText("program_flow_list_count", state.programs.size),
                    searchPlaceholder = movitText("program_flow_search"),
                    filterContentDescription = movitText("library_a11y_filter"),
                    onFilterClick = {},
                    filterSheetVisible = false,
                    onDismissFilterSheet = {},
                    filterSheetTitle = movitText("library_filter_sheet_title"),
                )
                MovitEmptyState(
                    title = movitText("program_flow_empty_title"),
                    message = movitText("program_flow_empty_message"),
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                ) {
                    LibraryToolbar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        filters = emptyList(),
                        selectedFilter = LibraryFilterChip.All,
                        onFilterSelected = {},
                        resultSummary = movitText(
                            "program_flow_list_count_filtered",
                            state.visiblePrograms.size,
                            state.filteredPrograms.size,
                        ),
                        searchPlaceholder = movitText("program_flow_search"),
                        filterContentDescription = movitText("library_a11y_filter"),
                        onFilterClick = {},
                        filterSheetVisible = false,
                        onDismissFilterSheet = {},
                        filterSheetTitle = movitText("library_filter_sheet_title"),
                    )
                    MovitFilterRow(
                        filters = state.chips,
                        selectedFilter = state.selectedChip,
                        onFilterSelected = onChipSelected,
                    )
                    state.visiblePrograms.forEach { program ->
                        val metadata = buildList {
                            add(movitText("program_flow_weeks_days", program.durationWeeks, program.daysPerWeek))
                        }
                        MovitProgramCard(
                            title = program.title,
                            description = program.description,
                            metadata = metadata,
                            badge = program.badge,
                            levelLabel = program.levelLabel.takeIf { !program.isActive },
                            imageUrl = program.imageUrl,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onProgramClick(program.id) },
                        )
                    }
                    if (state.hasMore) {
                        LaunchedEffect(state.visiblePrograms.size) { onLoadMore() }
                        MovitLoadingState(
                            message = movitText("library_loading_more"),
                            modifier = Modifier.padding(vertical = MovitSpacing.md),
                        )
                    }
                }
            }
        }
    }
}
