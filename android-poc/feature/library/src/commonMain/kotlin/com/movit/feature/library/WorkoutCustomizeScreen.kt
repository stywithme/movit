package com.movit.feature.library

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitSegmentedControl
import com.movit.designsystem.components.MovitStepper
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun WorkoutCustomizeScreen(
    state: WorkoutCustomizeUiState,
    restOptionsSeconds: List<Int>,
    onBack: () -> Unit,
    onSetsChanged: (exerciseId: String, sets: Int) -> Unit,
    onRepsChanged: (exerciseId: String, reps: Int) -> Unit,
    onDeleteExercise: (exerciseId: String) -> Unit,
    onMoveExercise: (exerciseId: String, delta: Int) -> Unit,
    onRestOptionSelected: (Int) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = state.config
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MovitInnerPageHeader(
                onBack = onBack,
                backLabel = movitText("workout_flow_back"),
                title = movitText("workout_flow_customize_title"),
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
            )
        },
        bottomBar = {
            if (config != null && config.exercises.isNotEmpty()) {
                CustomizeStartDock(
                    exerciseCount = config.exerciseCount,
                    restSeconds = config.restBetweenSetsSeconds,
                    onStart = onStart,
                    modifier = Modifier.padding(MovitSpacing.lg),
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> MovitLoadingState(message = movitText("workout_flow_loading"))
            state.errorMessage != null -> MovitErrorState(
                title = movitText("common_error_title"),
                message = state.errorMessage,
                actionLabel = movitText("common_retry"),
                onRetry = onRetry,
            )
            config != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                        Text(
                            text = config.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.W800,
                        )
                        Text(
                            text = movitText(
                                "workout_flow_customize_meta",
                                config.exerciseCount,
                                config.estimatedMinutes,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.movitColors.textSecondary,
                        )
                    }
                    if (config.exercises.isEmpty()) {
                        Text(
                            text = movitText("workout_flow_empty_error"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.movitColors.textSecondary,
                        )
                    } else {
                        MovitSectionHeader(
                            title = movitText("workout_flow_exercises"),
                            subtitle = config.exerciseCount.toString(),
                        )
                        config.exercises.forEachIndexed { index, exercise ->
                            CustomizeExerciseRow(
                                index = index + 1,
                                exercise = exercise,
                                canMoveUp = index > 0,
                                canMoveDown = index < config.exercises.lastIndex,
                                onSetsChanged = { onSetsChanged(exercise.id, it) },
                                onRepsChanged = { onRepsChanged(exercise.id, it) },
                                onDelete = { onDeleteExercise(exercise.id) },
                                onMoveUp = { onMoveExercise(exercise.id, -1) },
                                onMoveDown = { onMoveExercise(exercise.id, 1) },
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                        MovitSectionHeader(title = movitText("workout_flow_rest_between_sets"))
                        val labels = restOptionsSeconds.map { movitText("workout_flow_rest_seconds", it) }
                        val selectedIndex = restOptionsSeconds.indexOf(config.restBetweenSetsSeconds)
                            .coerceAtLeast(0)
                        MovitSegmentedControl(
                            options = labels,
                            selectedIndex = selectedIndex,
                            onOptionSelected = { index ->
                                restOptionsSeconds.getOrNull(index)?.let(onRestOptionSelected)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizeExerciseRow(
    index: Int,
    exercise: WorkoutFlowExerciseUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSetsChanged: (Int) -> Unit,
    onRepsChanged: (Int) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeA11y = movitText("workout_flow_a11y_remove", exercise.name)
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Outlined,
        contentPadding = MovitSpacing.md,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CustomizeDragHandle(
                    onMoveUp = { if (canMoveUp) onMoveUp() },
                    onMoveDown = { if (canMoveDown) onMoveDown() },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "$index. ${exercise.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.W800,
                    )
                    if (exercise.durationSeconds != null) {
                        Text(
                            text = WorkoutSessionFormatting.setsLabel(
                                exercise.sets,
                                exercise.reps,
                                exercise.durationSeconds,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.movitColors.textSecondary,
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.semantics { contentDescription = removeA11y },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.movitColors.textSecondary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = movitText("workout_flow_sets_label"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.W700,
                    )
                    MovitStepper(
                        value = exercise.sets,
                        onDecrement = { onSetsChanged(exercise.sets - 1) },
                        onIncrement = { onSetsChanged(exercise.sets + 1) },
                        minValue = 1,
                        maxValue = 10,
                    )
                }
                if (exercise.durationSeconds == null) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = movitText("workout_flow_reps_label"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.W700,
                        )
                        MovitStepper(
                            value = exercise.reps ?: 10,
                            onDecrement = { onRepsChanged((exercise.reps ?: 10) - 1) },
                            onIncrement = { onRepsChanged((exercise.reps ?: 10) + 1) },
                            minValue = 1,
                            maxValue = 100,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizeDragHandle(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var totalDrag by remember { mutableFloatStateOf(0f) }
    IconButton(
        onClick = {},
        modifier = modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragEnd = { totalDrag = 0f },
                onDrag = { _, dragAmount ->
                    totalDrag += dragAmount.y
                    if (totalDrag < -72f) {
                        onMoveUp()
                        totalDrag = 0f
                    } else if (totalDrag > 72f) {
                        onMoveDown()
                        totalDrag = 0f
                    }
                },
            )
        },
    ) {
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = movitText("workout_flow_a11y_reorder"),
        )
    }
}

@Composable
private fun CustomizeStartDock(
    exerciseCount: Int,
    restSeconds: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Elevated,
        contentPadding = MovitSpacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movitText("session_ready_to_train"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = movitText("workout_flow_dock_summary", exerciseCount, restSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
            MovitButton(
                text = movitText("session_start"),
                onClick = onStart,
                variant = MovitButtonVariant.Filled,
                leadingIcon = Icons.Default.PlayArrow,
            )
        }
    }
}
