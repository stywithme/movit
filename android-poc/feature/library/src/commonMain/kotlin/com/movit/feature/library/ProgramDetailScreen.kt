package com.movit.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitSegmentedControl
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors

@Composable
fun ProgramDetailScreen(
    state: ProgramDetailUiState,
    onBack: () -> Unit,
    onTabSelected: (ProgramDetailTab) -> Unit,
    onStartProgram: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val program = state.program
    Column(modifier = modifier.fillMaxSize()) {
        MovitInnerPageHeader(
            onBack = onBack,
            backLabel = "Explore",
            actionLabel = if (state.selectedTab == ProgramDetailTab.Overview) "Edit" else "Done",
            actionIcon = Icons.Default.Edit,
            onAction = {
                onTabSelected(
                    if (state.selectedTab == ProgramDetailTab.Overview) {
                        ProgramDetailTab.Edit
                    } else {
                        ProgramDetailTab.Overview
                    },
                )
            },
            modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
        )
        when {
            state.isLoading -> MovitLoadingState(message = "Loading program…")
            state.errorMessage != null -> MovitErrorState(message = state.errorMessage, onRetry = onRetry)
            program != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = program.title.take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.W800,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                        program.metadata.firstOrNull()?.let {
                            MovitTag(text = it, variant = MovitTagVariant.Blue)
                        }
                        Text(
                            text = program.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.W800,
                        )
                        Text(
                            text = program.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.movitColors.textSecondary,
                        )
                    }
                    MovitSegmentedControl(
                        options = listOf("Overview", "Edit"),
                        selectedIndex = if (state.selectedTab == ProgramDetailTab.Overview) 0 else 1,
                        onOptionSelected = { index ->
                            onTabSelected(if (index == 0) ProgramDetailTab.Overview else ProgramDetailTab.Edit)
                        },
                    )
                    if (state.selectedTab == ProgramDetailTab.Overview) {
                        ProgramOverviewPanel(state = state, onStartProgram = onStartProgram)
                    } else {
                        ProgramEditPanel()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramOverviewPanel(
    state: ProgramDetailUiState,
    onStartProgram: () -> Unit,
) {
    val program = state.program ?: return
    MovitStatTileRow(
        stats = listOf(
            MovitStatTileData(program.metadata.getOrNull(0) ?: "4 weeks", "Duration"),
            MovitStatTileData("3 / week", "Frequency"),
            MovitStatTileData(program.metadata.getOrNull(1) ?: "Beginner", "Level"),
        ),
    )
    MovitSectionHeader(title = "Your journey", subtitle = "Week by week")
    state.weeks.forEach { week ->
        MovitCard(
            variant = if (week.isActive) MovitCardVariant.Outlined else MovitCardVariant.Filled,
        ) {
            Column(modifier = Modifier.padding(MovitSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = week.label, fontWeight = FontWeight.W800)
                    if (week.isActive) {
                        MovitTag(text = "Active", variant = MovitTagVariant.Lime)
                    }
                }
                Text(
                    text = week.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
                if (week.progressPercent > 0) {
                    MovitProgressBar(
                        progressPercent = week.progressPercent,
                        modifier = Modifier.padding(top = MovitSpacing.sm),
                    )
                }
            }
        }
    }
    MovitButton(
        text = "Start program",
        onClick = onStartProgram,
        variant = MovitButtonVariant.Filled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProgramEditPanel() {
    MovitCard(variant = MovitCardVariant.Filled) {
        Column(
            modifier = Modifier.padding(MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            Text(
                text = "Customize your plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
            )
            Text(
                text = "Adjust training days, swap exercises, or pause the program. Changes sync when you save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
            )
        }
    }
    MovitSectionHeader(title = "Quick settings")
    MovitCard(variant = MovitCardVariant.Outlined) {
        Column(modifier = Modifier.padding(MovitSpacing.lg)) {
            Text("Training days per week", fontWeight = FontWeight.W700)
            Text(
                "3 days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
        }
    }
}
