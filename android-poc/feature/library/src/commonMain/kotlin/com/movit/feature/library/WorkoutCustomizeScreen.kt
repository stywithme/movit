package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
            if (config != null) {
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
            state.errorMessage != null -> MovitErrorState(message = state.errorMessage, onRetry = onRetry)
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
                    MovitSectionHeader(
                        title = movitText("workout_flow_exercises"),
                        subtitle = config.exerciseCount.toString(),
                    )
                    config.exercises.forEach { exercise ->
                        CustomizeExerciseRow(
                            exercise = exercise,
                            onSetsChanged = { onSetsChanged(exercise.id, it) },
                        )
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
    exercise: WorkoutFlowExerciseUi,
    onSetsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Outlined,
        contentPadding = MovitSpacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W800,
                )
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
            MovitStepper(
                value = exercise.sets,
                onDecrement = { onSetsChanged(exercise.sets - 1) },
                onIncrement = { onSetsChanged(exercise.sets + 1) },
                minValue = 1,
                maxValue = 10,
            )
        }
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
