package com.movit.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun WorkoutRunScreen(
    state: WorkoutRunUiState,
    onBack: () -> Unit,
    onStartExercise: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = state.config
    val current = state.currentExercise
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MovitInnerPageHeader(
                onBack = onBack,
                backLabel = movitText("workout_flow_back"),
                title = config?.title ?: movitText("workout_flow_run_title"),
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
            )
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
            config != null && current != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    Text(
                        text = movitText(
                            "workout_flow_exercise_of",
                            state.currentExerciseIndex + 1,
                            config.exerciseCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.W800,
                        color = MaterialTheme.movitColors.textTertiary,
                    )
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.W800,
                    )
                    val progressA11y = movitText(
                        "workout_flow_a11y_progress",
                        state.progressPercent,
                    )
                    MovitProgressBar(
                        progressPercent = state.progressPercent,
                        modifier = Modifier.semantics { contentDescription = progressA11y },
                    )
                    Text(
                        text = if (current.reps != null) {
                            movitText(
                                "workout_flow_set_reps",
                                state.currentSet,
                                current.sets,
                                current.reps,
                            )
                        } else {
                            movitText(
                                "workout_flow_set_hold",
                                state.currentSet,
                                current.sets,
                                current.durationSeconds ?: 0,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.movitColors.textSecondary,
                    )
                    MovitCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MovitCardVariant.Outlined,
                        contentPadding = MovitSpacing.md,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                            state.sequenceItems().forEach { item ->
                                WorkoutRunSequenceRow(item = item)
                            }
                        }
                    }
                    if (state.previousFormPercent != null && state.previousFormTip != null) {
                        MovitInsightCard(
                            title = movitText("workout_flow_previous_form", state.previousFormPercent),
                            message = state.previousFormTip,
                            variant = MovitInsightVariant.Success,
                            icon = Icons.Default.FitnessCenter,
                        )
                    }
                    MovitButton(
                        text = movitText("workout_flow_start_exercise"),
                        onClick = onStartExercise,
                        variant = MovitButtonVariant.Filled,
                        leadingIcon = Icons.Default.PlayArrow,
                        enabled = !state.isEnsuringConfig,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutRunSequenceRow(
    item: WorkoutRunSequenceItemUi,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val isActive = item.status == WorkoutRunExerciseStatus.Active
    val isDone = item.status == WorkoutRunExerciseStatus.Done
    val statusLabel = when (item.status) {
        WorkoutRunExerciseStatus.Done -> movitText("workout_flow_done")
        WorkoutRunExerciseStatus.Active -> movitText("workout_flow_now")
        WorkoutRunExerciseStatus.Pending -> movitText("workout_flow_a11y_pending")
    }
    val rowDescription = movitText("workout_flow_a11y_sequence", item.name, statusLabel)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = rowDescription },
        shape = RoundedCornerShape(12.dp),
        color = when {
            isActive -> movit.primaryTint
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            SequenceNumberBadge(
                label = if (isDone) "✓" else item.index.toString(),
                isDone = isDone,
                isActive = isActive,
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
                modifier = Modifier.weight(1f),
            )
            when (item.status) {
                WorkoutRunExerciseStatus.Done -> {
                    Text(
                        text = movitText("workout_flow_done"),
                        style = MaterialTheme.typography.labelMedium,
                        color = movit.textTertiary,
                        fontWeight = FontWeight.W700,
                    )
                }
                WorkoutRunExerciseStatus.Active -> {
                    MovitTag(text = movitText("workout_flow_now"), variant = MovitTagVariant.Blue)
                }
                WorkoutRunExerciseStatus.Pending -> Unit
            }
        }
    }
}

@Composable
private fun SequenceNumberBadge(
    label: String,
    isDone: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    Box(
        modifier = modifier
            .size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when {
                isDone -> MaterialTheme.colorScheme.secondary
                isActive -> movit.primaryTint
                else -> movit.primaryTint
            },
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.W800,
                    color = when {
                        isDone -> MaterialTheme.colorScheme.onSecondary
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}
