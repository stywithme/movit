package com.movit.feature.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movit.core.data.preferences.MovitTrainingPreferencesState
import com.movit.core.training.session.SessionRunState
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitBackButton
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitChromeButtonStyle
import com.movit.designsystem.components.MovitChromeIconButton
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitSkeletonOverlay
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
@Suppress("UNUSED_PARAMETER")
fun TrainingSessionScreen(
    state: TrainingSessionUiState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    onViewReport: () -> Unit = {},
    onSkipRest: () -> Unit,
    onToggleRestPause: () -> Unit = {},
    onAddRestTime: () -> Unit = {},
    cameraSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onFlipCamera: (() -> Unit)? = null,
    trainingPreferences: MovitTrainingPreferencesState = MovitTrainingPreferencesState(),
    useFrontCamera: Boolean = true,
    onApplyTrainingSettings: (TrainingSessionSettingsSelection) -> Unit = {},
    onResumePriorSession: () -> Unit = {},
    onDiscardPriorSession: () -> Unit = {},
    onExitContinue: () -> Unit = {},
    onExitSaveAndExit: () -> Unit = {},
    onExitEndWorkout: () -> Unit = {},
    debugFps: Int? = null,
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    val romIndicators = remember(
        state.landmarks,
        state.jointStateInfos,
        state.skeletonOverlayParity.isBilateralFlipped,
        state.skeletonOverlayParity.jointVisuals,
        trainingPreferences.indicatorType,
    ) {
        buildSkeletonRomIndicators(
            landmarks = state.landmarks,
            jointStateInfos = state.jointStateInfos,
            anySideDimmedJointCodes = state.skeletonOverlayParity.jointVisuals
                .filterValues { it.dimmed }
                .keys,
            isBilateralFlipped = state.skeletonOverlayParity.isBilateralFlipped,
            indicatorType = trainingPreferences.indicatorType,
        )
    }
    val landmarkProjector = remember(
        state.skeletonAnalysisWidth,
        state.skeletonAnalysisHeight,
        state.skeletonMirrorPreview,
    ) {
        skeletonLandmarkProjector(
            analysisWidth = state.skeletonAnalysisWidth,
            analysisHeight = state.skeletonAnalysisHeight,
            mirrorPreview = state.skeletonMirrorPreview,
        )
    }
    val localizedPhase = localizedTrainingPhase(state.phaseLabel)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.configUnavailable -> {
                MovitErrorState(
                    title = movitText("common_error_title"),
                    message = movitText("workout_live_config_unavailable"),
                    actionLabel = movitText("workout_flow_back"),
                    onRetry = onBack,
                    modifier = Modifier.padding(MovitSpacing.lg),
                )
            }
            state.errorMessage != null -> {
                MovitErrorState(
                    title = movitText("common_error_title"),
                    message = state.errorMessage,
                    actionLabel = movitText("workout_flow_back"),
                    onRetry = onBack,
                    modifier = Modifier.padding(MovitSpacing.lg),
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.isOffline) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 56.dp)
                                .padding(horizontal = MovitSpacing.md)
                                .fillMaxWidth(),
                        ) {
                            Text(
                                text = movitText("training_offline_banner"),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(MovitSpacing.sm),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    cameraSlot()

                    if (state.requiresCamera()) {
                        MovitSkeletonOverlay(
                            landmarks = state.landmarks,
                            parity = state.skeletonOverlayParity,
                            romIndicators = romIndicators,
                            landmarkProjector = landmarkProjector,
                            showBilateralSideHint = false,
                            modifier = Modifier.fillMaxSize(),
                        )

                        if (state.isCameraSwitching) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.28f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    }

                    TrainingSessionStateOverlay(
                        state = state,
                        onViewReport = onViewReport,
                        modifier = Modifier.fillMaxSize(),
                    )

                    TrainingSessionTopChrome(
                        exerciseName = state.exerciseName.ifBlank { movitText("workout_live_title") },
                        onBack = onBack,
                        onFlipCamera = onFlipCamera,
                        flipEnabled = state.requiresCamera() && !state.isCameraSwitching,
                        onSettings = { showSettingsDialog = true },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    val isResting = state.isResting || state.workoutFlowPhase == WorkoutFlowPhase.REST
                    if (isResting) {
                        RestControlsDock(
                            secondsRemaining = state.restSecondsRemaining,
                            isPaused = state.isRestPaused,
                            onTogglePause = onToggleRestPause,
                            onAddTime = onAddRestTime,
                            onSkip = onSkipRest,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    } else if (state.requiresCamera()) {
                        TrainingSessionLiveBottomBar(
                            state = state,
                            localizedPhase = localizedPhase,
                            onPause = onPause,
                            onResume = onResume,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    } else {
                        TrainingSessionNonCameraControls(
                            state = state,
                            onFinish = onFinish,
                            onViewReport = onViewReport,
                            onSkipRest = onSkipRest,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        TrainingSessionSettingsDialog(
            preferences = trainingPreferences,
            useFrontCamera = useFrontCamera,
            onSwitchCamera = onFlipCamera?.takeIf { state.requiresCamera() && !state.isCameraSwitching },
            onApply = { selection ->
                onApplyTrainingSettings(selection)
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false },
        )
    }

    val resumePrompt = state.resumePrompt
    if (resumePrompt != null) {
        AlertDialog(
            onDismissRequest = onDiscardPriorSession,
            title = { Text(movitText("training_session_resume_prior_title")) },
            text = {
                Text(movitText("training_session_resume_prior_message", resumePrompt.completedReps))
            },
            confirmButton = {
                TextButton(onClick = onResumePriorSession) {
                    Text(movitText("training_session_resume_prior_continue"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDiscardPriorSession) {
                    Text(movitText("training_session_resume_prior_start_fresh"))
                }
            },
        )
    }

    if (state.exitPrompt != null) {
        val continueA11y = movitText("training_session_exit_continue")
        val saveA11y = movitText("training_session_exit_save")
        val endA11y = movitText("training_session_exit_end")
        AlertDialog(
            onDismissRequest = onExitContinue,
            title = { Text(movitText("training_session_exit_title")) },
            text = { Text(movitText("training_session_exit_message")) },
            confirmButton = {
                TextButton(
                    onClick = onExitContinue,
                    modifier = Modifier.semantics { contentDescription = continueA11y },
                ) {
                    Text(movitText("training_session_exit_continue"))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = onExitSaveAndExit,
                        modifier = Modifier.semantics { contentDescription = saveA11y },
                    ) {
                        Text(movitText("training_session_exit_save"))
                    }
                    TextButton(
                        onClick = onExitEndWorkout,
                        modifier = Modifier.semantics { contentDescription = endA11y },
                    ) {
                        Text(movitText("training_session_exit_end"))
                    }
                }
            },
        )
    }
}

@Composable
private fun TrainingSessionTopChrome(
    exerciseName: String,
    onBack: () -> Unit,
    onFlipCamera: (() -> Unit)?,
    flipEnabled: Boolean,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MovitBackButton(
            onClick = onBack,
            contentDescription = movitText("workout_flow_back"),
            style = MovitChromeButtonStyle.OnMedia,
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MovitSpacing.sm),
            shape = RoundedCornerShape(999.dp),
            color = movit.inkVeil72,
            contentColor = movit.onInk,
            border = BorderStroke(1.dp, movit.onInkVeil18),
        ) {
            Text(
                text = exerciseName,
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W800,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onFlipCamera != null) {
            TrainingCameraFlipButton(
                onClick = onFlipCamera,
                enabled = flipEnabled,
            )
        } else {
            Spacer(Modifier.size(MovitSpacing.minTouchTarget))
        }
        Spacer(Modifier.width(MovitSpacing.xs))
        MovitChromeIconButton(
            onClick = onSettings,
            icon = Icons.Default.Settings,
            contentDescription = movitText("training_settings"),
            style = MovitChromeButtonStyle.OnMedia,
        )
    }
}

@Composable
private fun TrainingSessionStateOverlay(
    state: TrainingSessionUiState,
    onViewReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (state.isResting || state.workoutFlowPhase == WorkoutFlowPhase.REST) {
            RestPanel(
                secondsRemaining = state.restSecondsRemaining,
                restContext = state.restContext,
                nextExerciseName = state.nextExerciseName,
                currentSetNumber = state.currentSetNumber,
                totalSets = state.totalSets,
                nextTargetReps = state.restNextTargetReps,
                nextTargetDurationSeconds = state.restNextTargetDurationSeconds,
                nextTargetWeightKg = state.restNextTargetWeightKg,
                previewImageUrl = state.restPreviewImageUrl,
                tip = state.restTip,
                modifier = Modifier.align(Alignment.Center),
            )
            return
        }

        if (state.runState == SessionRunState.COMPLETED) {
            WorkoutCompletePanel(
                exerciseName = state.exerciseName,
                repCount = state.repCount,
                formPercent = state.liveFormPercent,
                showViewReport = state.reportDetailId != null,
                uploadNotice = state.uploadNotice,
                onViewReport = onViewReport,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun TrainingSessionLiveBottomBar(
    state: TrainingSessionUiState,
    localizedPhase: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val isTraining = state.runState == SessionRunState.TRAINING
    val canStart = state.runState.canResume()
    val actionLabel = if (isTraining) {
        movitText("training_session_stop")
    } else {
        movitText("training_phase_start")
    }
    val actionIcon = if (isTraining) Icons.Default.Stop else Icons.Default.PlayArrow
    val actionEnabled = isTraining || canStart
    val action = if (isTraining) onPause else onResume
    val progress = liveProgressMetric(state)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
        shape = RoundedCornerShape(30.dp),
        color = movit.inkVeil78,
        contentColor = movit.onInk,
        border = BorderStroke(1.dp, movit.onInkVeil18),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MovitSpacing.md, vertical = MovitSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            TrainingBottomMetric(
                title = movitText("training_session_status"),
                value = liveStatusValue(state, localizedPhase),
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(0.9f),
            )
            LiveTimerActionButton(
                label = actionLabel,
                elapsedLabel = state.elapsedLabel,
                icon = actionIcon,
                enabled = actionEnabled,
                onClick = action,
                modifier = Modifier.weight(1.22f),
            )
            TrainingBottomMetric(
                title = progress.title,
                value = progress.value,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.9f),
            )
        }
    }
}

@Composable
private fun TrainingBottomMetric(
    title: String,
    value: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    Column(
        modifier = modifier,
        horizontalAlignment = when (textAlign) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        },
    ) {
        Text(
            text = title,
            color = movit.onInkVeil70,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.W800,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
            color = movit.onInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.W900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LiveTimerActionButton(
    label: String,
    elapsedLabel: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val container = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        movit.onInkVeil16
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        movit.onInkVeil55
    }

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, movit.onInkVeil22),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MovitSpacing.sm, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = elapsedLabel,
                fontSize = 25.sp,
                fontWeight = FontWeight.W900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun liveStatusValue(
    state: TrainingSessionUiState,
    localizedPhase: String,
): String = when (state.runState) {
    SessionRunState.TRAINING -> localizedPhase.ifBlank { movitText("train_status_in_progress") }
    SessionRunState.PAUSED,
    SessionRunState.AUTO_PAUSED,
    -> movitText("training_session_countdown_frozen_chip")
    SessionRunState.COUNTDOWN,
    SessionRunState.RESUME_COUNTDOWN,
    -> state.countdownValue?.toString() ?: movitText("training_phase_ready")
    SessionRunState.SETUP_POSE,
    SessionRunState.RESUME_SETUP,
    -> localizedSetupPhase(state.setupPhase)
    SessionRunState.COMPLETED -> movitText("train_status_completed")
    SessionRunState.IDLE -> movitText("training_phase_ready")
}

@Composable
private fun liveProgressMetric(state: TrainingSessionUiState): LiveProgressMetric {
    return if (state.targetReps > 0) {
        LiveProgressMetric(
            title = movitText("workout_flow_reps_label"),
            value = "${state.repCount} / ${state.targetReps}",
        )
    } else {
        val hold = state.holdStatus
        val value = if (hold != null && hold.elapsedMs + hold.remainingMs > 0L) {
            "${formatLiveDuration(hold.elapsedMs)} / ${formatLiveDuration(hold.elapsedMs + hold.remainingMs)}"
        } else {
            state.elapsedLabel
        }
        LiveProgressMetric(
            title = movitText("session_duration"),
            value = value,
        )
    }
}

private data class LiveProgressMetric(
    val title: String,
    val value: String,
)

private fun formatLiveDuration(elapsedMs: Long): String {
    val seconds = (elapsedMs / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
}

@Composable
private fun TrainingSessionNonCameraControls(
    state: TrainingSessionUiState,
    onFinish: () -> Unit,
    onViewReport: () -> Unit,
    onSkipRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(MovitSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.isResting || state.workoutFlowPhase == WorkoutFlowPhase.REST -> {
                MovitButton(
                    text = movitText("prepare_skip_rest"),
                    onClick = onSkipRest,
                    variant = MovitButtonVariant.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.isComplete -> {
                if (state.reportDetailId != null) {
                    MovitButton(
                        text = movitText("training_session_view_report"),
                        onClick = onViewReport,
                        variant = MovitButtonVariant.Filled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MovitButton(
                    text = movitText("workout_live_finish"),
                    onClick = onFinish,
                    variant = if (state.reportDetailId != null) {
                        MovitButtonVariant.Outlined
                    } else {
                        MovitButtonVariant.Filled
                    },
                    leadingIcon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
