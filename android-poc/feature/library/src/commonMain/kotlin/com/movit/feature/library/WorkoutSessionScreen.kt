package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.movitColors
import com.movit.feature.library.components.SessionExerciseCard
import com.movit.feature.library.components.SessionRestBlock
import com.movit.resources.movitText

@Composable
fun WorkoutSessionScreen(
    state: WorkoutSessionUiState,
    onBack: () -> Unit,
    onToggleEdit: () -> Unit,
    onExerciseClick: (String) -> Unit,
    onSwapExercise: (String) -> Unit,
    onEditExercise: (String) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onAddExercise: () -> Unit,
    onAddRest: () -> Unit,
    onStartWorkout: () -> Unit,
    onRetry: () -> Unit,
    onDismissSheet: () -> Unit,
    onSwapQueryChange: (String) -> Unit,
    onSwapCandidateSelected: (String) -> Unit,
    onAddExerciseQueryChange: (String) -> Unit,
    onAddExerciseCandidateSelected: (String) -> Unit,
    onEditDraftChange: ((ExerciseEditDraft) -> ExerciseEditDraft) -> Unit,
    onSaveEditDetails: () -> Unit,
    onSwitchEditToSwap: () -> Unit,
    onRestClick: (String) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onMoveBlock: (sectionPhaseRole: String, blockId: String, delta: Int) -> Unit,
    onRestDurationChange: (Int) -> Unit,
    onSaveRestEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MovitInnerPageHeader(
                onBack = onBack,
                backLabel = movitText("session_back"),
                actionLabel = if (state.isEditMode) movitText("session_done") else movitText("session_edit"),
                actionIcon = if (state.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                onAction = onToggleEdit,
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
            )
        },
        bottomBar = {
            if (!state.isEditMode && session != null) {
                SessionStartDock(
                    exerciseCount = session.exerciseCount,
                    durationLabel = session.durationLabel,
                    onStart = onStartWorkout,
                    modifier = Modifier.padding(MovitSpacing.lg),
                )
            } else if (state.isEditMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MovitSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                ) {
                    MovitButton(
                        text = movitText("session_add_exercise"),
                        onClick = onAddExercise,
                        variant = MovitButtonVariant.Outlined,
                        modifier = Modifier.weight(1f),
                    )
                    MovitButton(
                        text = movitText("session_rest"),
                        onClick = onAddRest,
                        variant = MovitButtonVariant.Outlined,
                        leadingIcon = Icons.Default.Schedule,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> MovitLoadingState(message = movitText("session_loading"))
            state.errorMessage != null -> MovitErrorState(
                title = movitText("common_error_title"),
                message = state.errorMessage,
                actionLabel = movitText("common_retry"),
                onRetry = onRetry,
            )
            session != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.W800,
                        )
                        Text(
                            text = buildString {
                                append(session.subtitle)
                                if (state.isEditMode) append(" · ${movitText("session_editing")}")
                                if (state.isSaving) append(" · ${movitText("session_saving")}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isEditMode) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.movitColors.textSecondary
                            },
                        )
                    }
                    MovitStatTileRow(
                        stats = listOf(
                            MovitStatTileData(session.exerciseCount.toString(), movitText("session_exercises")),
                            MovitStatTileData(session.durationLabel, movitText("session_duration")),
                            MovitStatTileData(session.setCount.toString(), movitText("session_sets")),
                        ),
                    )
                    session.sections.forEach { section ->
                        MovitSectionHeader(
                            title = section.title,
                            subtitle = section.items.count { it is WorkoutSessionBlockUi.Exercise }.toString(),
                        )
                        section.items.forEachIndexed { blockIndex, block ->
                            when (block) {
                                is WorkoutSessionBlockUi.Exercise -> SessionExerciseCard(
                                    exercise = block,
                                    isEditMode = state.isEditMode,
                                    onClick = { onExerciseClick(block.id) },
                                    onSwap = { onSwapExercise(block.id) },
                                    onEdit = { onEditExercise(block.id) },
                                    onDelete = { onDeleteExercise(block.id) },
                                    onMoveUp = {
                                        if (blockIndex > 0) {
                                            onMoveBlock(section.phaseRole, block.id, -1)
                                        }
                                    },
                                    onMoveDown = {
                                        if (blockIndex < section.items.lastIndex) {
                                            onMoveBlock(section.phaseRole, block.id, 1)
                                        }
                                    },
                                )
                                is WorkoutSessionBlockUi.Rest -> SessionRestBlock(
                                    rest = block,
                                    isEditMode = state.isEditMode,
                                    onClick = { onRestClick(block.id) },
                                    onDelete = { onDeleteBlock(block.id) },
                                    onMoveUp = {
                                        if (blockIndex > 0) {
                                            onMoveBlock(section.phaseRole, block.id, -1)
                                        }
                                    },
                                    onMoveDown = {
                                        if (blockIndex < section.items.lastIndex) {
                                            onMoveBlock(section.phaseRole, block.id, 1)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    WorkoutSessionSheets(
        activeSheet = state.activeSheet,
        session = session,
        onDismiss = onDismissSheet,
        onSwapQueryChange = onSwapQueryChange,
        onSwapCandidateSelected = onSwapCandidateSelected,
        onAddExerciseQueryChange = onAddExerciseQueryChange,
        onAddExerciseCandidateSelected = onAddExerciseCandidateSelected,
        onEditDraftChange = onEditDraftChange,
        onSaveEditDetails = onSaveEditDetails,
        onSwitchEditToSwap = onSwitchEditToSwap,
        onRestDurationChange = onRestDurationChange,
        onSaveRestEdit = onSaveRestEdit,
    )
}

@Composable
private fun SessionStartDock(
    exerciseCount: Int,
    durationLabel: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startA11y = movitText("session_a11y_start_workout")
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Elevated,
        contentPadding = MovitSpacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movitText("session_ready_to_train"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = movitText("session_summary", exerciseCount, durationLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
            MovitButton(
                text = movitText("session_start"),
                onClick = onStart,
                variant = MovitButtonVariant.Filled,
                leadingIcon = Icons.Default.PlayArrow,
                modifier = Modifier.semantics { contentDescription = startA11y },
            )
        }
    }
}
