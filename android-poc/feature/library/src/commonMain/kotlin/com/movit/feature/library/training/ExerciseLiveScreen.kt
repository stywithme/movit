package com.movit.feature.library.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.movit.core.training.session.LiveExerciseRunner
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun ExerciseLiveScreen(
    state: ExerciseLiveUiState,
    runner: LiveExerciseRunner?,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(runner) {
        onDispose { /* session stopped from route */ }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MovitInnerPageHeader(
                onBack = onBack,
                backLabel = movitText("workout_flow_back"),
                title = state.exerciseName.ifBlank { movitText("workout_live_title") },
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
            )
        },
    ) { padding ->
        when {
            state.bridgeUnavailable -> {
                MovitErrorState(
                    title = movitText("common_error_title"),
                    message = movitText("workout_live_bridge_unavailable"),
                    actionLabel = movitText("workout_flow_back"),
                    onRetry = onBack,
                    modifier = Modifier.padding(padding),
                )
            }
            state.errorMessage != null -> {
                MovitErrorState(
                    title = movitText("common_error_title"),
                    message = state.errorMessage,
                    actionLabel = movitText("workout_flow_back"),
                    onRetry = onBack,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        if (runner != null) {
                            TrainingCameraHost(
                                runner = runner,
                                onCameraReady = onCameraReady,
                                onError = onCameraError,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        LiveMetricsOverlay(
                            state = state,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(MovitSpacing.lg),
                        )
                    }
                    if (state.isComplete) {
                        MovitButton(
                            text = movitText("workout_live_finish"),
                            onClick = onFinish,
                            variant = MovitButtonVariant.Filled,
                            leadingIcon = Icons.Default.CheckCircle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MovitSpacing.lg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMetricsOverlay(
    state: ExerciseLiveUiState,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MovitTag(
            text = movitText("workout_live_phase", state.phaseLabel),
            variant = MovitTagVariant.Blue,
        )
        Text(
            text = movitText("workout_live_reps", state.repCount, state.targetReps),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val progress = if (state.targetReps == 0) 0 else {
            ((state.repCount.toFloat() / state.targetReps) * 100).toInt().coerceIn(0, 100)
        }
        MovitProgressBar(
            progressPercent = progress,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = movitText("workout_live_form", state.liveFormPercent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W700,
            color = movit.textSecondary,
        )
        if (state.averageFormPercent > 0) {
            Text(
                text = movitText("workout_live_avg_form", state.averageFormPercent),
                style = MaterialTheme.typography.bodySmall,
                color = movit.textTertiary,
            )
        }
    }
}
