package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitFilterRow
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitMediaCard
import com.movit.resources.movitText

@Composable
fun ProgramListScreen(
    state: ProgramListUiState,
    onBack: () -> Unit,
    onChipSelected: (String) -> Unit,
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
                message = state.errorMessage,
                onRetry = onRetry,
            )
            state.filteredPrograms.isEmpty() -> MovitEmptyState(
                title = movitText("program_flow_empty_title"),
                message = movitText("program_flow_empty_message"),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                ) {
                    MovitFilterRow(
                        filters = state.chips,
                        selectedFilter = state.selectedChip,
                        onFilterSelected = onChipSelected,
                    )
                    state.filteredPrograms.forEach { program ->
                        val metadata = buildList {
                            add(movitText("program_flow_weeks_days", program.durationWeeks, program.daysPerWeek))
                        }
                        MovitMediaCard(
                            title = program.title,
                            subtitle = program.description,
                            metadata = metadata,
                            badge = program.badge ?: program.levelLabel.takeIf { !program.isActive },
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onProgramClick(program.id) },
                        )
                    }
                }
            }
        }
    }
}
