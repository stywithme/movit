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
import androidx.compose.material.icons.filled.Warning
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
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatsStrip
import com.movit.designsystem.movitColors
import com.movit.feature.library.components.SessionExerciseCard
import com.movit.feature.library.components.SessionPlannedWorkoutCards
import com.movit.feature.library.components.SessionRestBlock
import com.movit.resources.movitText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

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
    onResumeWorkout: () -> Unit = onStartWorkout,
    onRestartWorkout: () -> Unit = {},
    onConfirmRestart: () -> Unit = {},
    onDismissRestartConfirm: () -> Unit = {},
    onRetry: () -> Unit,
    onRetrySave: () -> Unit = onRetry,
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
    onSelectPlannedWorkout: (String) -> Unit,
    onTogglePlannedWorkoutExpand: (String) -> Unit,
    onDismissCatchUpDialog: () -> Unit,
    onOpenCatchUpDay: () -> Unit,
    onSkipWarmup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val editToggleA11y = movitText("session_a11y_toggle_edit")
            val isExploreSession = session?.context == null
            MovitInnerPageHeader(
                onBack = onBack,
                backContentDescription = movitText("session_back"),
                actionLabel = when {
                    state.isEditMode -> movitText("session_done")
                    isExploreSession -> movitText("session_customize_run")
                    else -> movitText("session_edit")
                },
                actionIcon = if (state.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                onAction = onToggleEdit,
                modifier = Modifier
                    .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm)
                    .semantics { contentDescription = editToggleA11y },
            )
        },
        bottomBar = {
            if (!state.isEditMode && session != null) {
                SessionStartDock(
                    exerciseCount = session.exerciseCount,
                    durationLabel = session.durationLabel,
                    showSkipWarmup = session.hasWarmupSection() && !session.warmupSkipped,
                    launchReadiness = state.launchReadiness,
                    openRun = state.openRun,
                    isSaving = state.isSaving,
                    onSkipWarmup = onSkipWarmup,
                    onStart = {
                        when (state.launchReadiness) {
                            is LaunchReadiness.Blocked -> onRetry()
                            else -> onStartWorkout()
                        }
                    },
                    onResume = {
                        when (state.launchReadiness) {
                            is LaunchReadiness.Blocked -> onRetry()
                            else -> onResumeWorkout()
                        }
                    },
                    onRestart = onRestartWorkout,
                    modifier = Modifier.padding(MovitSpacing.lg),
                )
            } else if (state.isEditMode) {
                val addExerciseA11y = movitText("session_a11y_add_exercise")
                val addRestA11y = movitText("session_a11y_add_rest")
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
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = addExerciseA11y },
                    )
                    MovitButton(
                        text = movitText("session_rest"),
                        onClick = onAddRest,
                        variant = MovitButtonVariant.Outlined,
                        leadingIcon = Icons.Default.Schedule,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = addRestA11y },
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading && session == null -> MovitLoadingState(message = movitText("session_loading"))
            state.errorMessage != null && session == null -> MovitErrorState(
                title = movitText("common_error_title"),
                message = state.errorMessage,
                actionLabel = movitText("common_retry"),
                onRetry = onRetry,
            )
            session != null -> {
                val showWorkoutDetails = state.plannedWorkoutCards.size <= 1 ||
                    state.expandedPlannedWorkoutId == session.context?.plannedWorkoutId
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                ) {
                    state.saveError?.let { message ->
                        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                            MovitInsightCard(
                                title = movitText("session_save_failed"),
                                message = message,
                                icon = Icons.Default.Warning,
                                variant = MovitInsightVariant.Warning,
                            )
                            MovitButton(
                                text = movitText("common_retry"),
                                onClick = onRetrySave,
                                variant = MovitButtonVariant.Text,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (state.plannedWorkoutCards.size > 1) {
                        SessionPlannedWorkoutCards(
                            cards = state.plannedWorkoutCards,
                            expandedWorkoutId = state.expandedPlannedWorkoutId,
                            onToggleExpand = onTogglePlannedWorkoutExpand,
                            onSelectWorkout = onSelectPlannedWorkout,
                        )
                    }
                    if (!showWorkoutDetails) return@Column
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
                    MovitStatsStrip(
                        stats = listOf(
                            MovitStatTileData(session.exerciseCount.toString(), movitText("session_exercises")),
                            MovitStatTileData(session.durationLabel, movitText("session_duration")),
                            MovitStatTileData(session.setCount.toString(), movitText("session_sets")),
                        ),
                    )
                    session.sectionsForTraining().forEach { section ->
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
    if (state.showCatchUpDialog && state.catchUpPrompt != null) {
        val prompt = state.catchUpPrompt
        AlertDialog(
            onDismissRequest = onDismissCatchUpDialog,
            title = { Text(movitText("session_catch_up_title")) },
            text = { Text(prompt.message) },
            confirmButton = {
                TextButton(onClick = onOpenCatchUpDay) {
                    Text(movitText("session_catch_up_open_missed"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCatchUpDialog) {
                    Text(movitText("session_catch_up_continue"))
                }
            },
        )
    }
    if (state.showRestartConfirm) {
        AlertDialog(
            onDismissRequest = onDismissRestartConfirm,
            title = { Text(movitText("session_restart_confirm_title")) },
            text = { Text(movitText("session_restart_confirm_message")) },
            confirmButton = {
                TextButton(onClick = onConfirmRestart) {
                    Text(movitText("session_restart_confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestartConfirm) {
                    Text(movitText("session_cancel"))
                }
            },
        )
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
    showSkipWarmup: Boolean,
    launchReadiness: LaunchReadiness,
    openRun: WorkoutRunOpenState?,
    isSaving: Boolean = false,
    onSkipWarmup: () -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startA11y = movitText("session_a11y_start_workout")
    val ctaEnabled = !isSaving && when (launchReadiness) {
        LaunchReadiness.Ready,
        LaunchReadiness.OfflineReady,
        is LaunchReadiness.Blocked,
        -> true
        else -> false
    }
    val hasOpenRun = openRun != null
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Elevated,
        contentPadding = MovitSpacing.md,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasOpenRun) {
                            movitText("session_resume")
                        } else {
                            movitText(launchReadiness.statusLabelKey())
                        },
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
                    text = if (hasOpenRun) {
                        movitText("session_resume")
                    } else {
                        movitText(launchReadiness.ctaLabelKey())
                    },
                    onClick = if (hasOpenRun) onResume else onStart,
                    enabled = ctaEnabled,
                    variant = MovitButtonVariant.Filled,
                    leadingIcon = Icons.Default.PlayArrow,
                    modifier = Modifier.semantics { contentDescription = startA11y },
                )
            }
            if (hasOpenRun) {
                MovitButton(
                    text = movitText("session_restart"),
                    onClick = onRestart,
                    enabled = ctaEnabled,
                    variant = MovitButtonVariant.Text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showSkipWarmup && !hasOpenRun) {
                val skipWarmupA11y = movitText("session_a11y_skip_warmup")
                MovitButton(
                    text = movitText("session_skip_warmup"),
                    onClick = onSkipWarmup,
                    variant = MovitButtonVariant.Text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = skipWarmupA11y },
                )
            }
        }
    }
}
