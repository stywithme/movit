package com.movit.feature.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.core.training.session.PauseReason
import com.movit.core.training.session.SessionRunState
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.GlassMessageSeverity
import com.movit.designsystem.components.MovitBackButton
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitChromeButtonStyle
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitGlassMessage
import com.movit.designsystem.components.MovitSkeletonOverlay
import com.movit.designsystem.components.TrainingHud
import com.movit.designsystem.components.VignetteEffect
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun TrainingSessionScreen(
    state: TrainingSessionUiState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    onViewReport: () -> Unit = {},
    onSkipRest: () -> Unit,
    cameraSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onFlipCamera: (() -> Unit)? = null,
    debugFps: Int? = null,
) {
    val romIndicators = remember(state.landmarks, state.skeletonOverlayParity) {
        buildSkeletonRomIndicators(
            landmarks = state.landmarks,
            jointVisuals = state.skeletonOverlayParity.jointVisuals,
            isBilateralFlipped = state.skeletonOverlayParity.isBilateralFlipped,
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    cameraSlot()

                    if (state.requiresCamera()) {
                        MovitSkeletonOverlay(
                            landmarks = state.landmarks,
                            parity = state.skeletonOverlayParity,
                            romIndicators = romIndicators,
                            landmarkProjector = landmarkProjector,
                            modifier = Modifier.fillMaxSize(),
                        )

                        VignetteEffect(
                            visible = state.showVignette,
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

                    TrainingAttentionFrame(
                        severity = state.glassMessage?.severity,
                        visible = state.showVignette || state.glassMessage != null,
                        modifier = Modifier.fillMaxSize(),
                    )

                    TrainingSessionStateOverlay(
                        state = state,
                        localizedPhase = localizedPhase,
                        onViewReport = onViewReport,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (
                        state.glassMessage != null &&
                        state.runState != SessionRunState.TRAINING &&
                        state.runState != SessionRunState.AUTO_PAUSED
                    ) {
                        val message = state.glassMessage
                        MovitGlassMessage(
                            text = message.text,
                            severity = message.severity,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    start = MovitSpacing.lg,
                                    end = MovitSpacing.lg,
                                    bottom = 128.dp,
                                ),
                        )
                    }

                    TrainingSessionTopChrome(
                        exerciseName = state.exerciseName.ifBlank { movitText("workout_live_title") },
                        onBack = onBack,
                        onFlipCamera = onFlipCamera?.takeIf { !state.isComplete && !state.isResting },
                        flipEnabled = !state.isCameraSwitching,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    TrainingDebugFpsOverlay(
                        fps = debugFps,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 58.dp, end = MovitSpacing.md),
                    )

                    TrainingSessionControls(
                        state = state,
                        onPause = onPause,
                        onResume = onResume,
                        onStop = onStop,
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

@Composable
private fun TrainingSessionTopChrome(
    exerciseName: String,
    onBack: () -> Unit,
    onFlipCamera: (() -> Unit)?,
    flipEnabled: Boolean,
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
    }
}

@Composable
private fun TrainingAttentionFrame(
    severity: GlassMessageSeverity?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val movit = MaterialTheme.movitColors
    val color = when (severity) {
        GlassMessageSeverity.SUCCESS -> movit.success
        GlassMessageSeverity.ERROR -> MaterialTheme.colorScheme.tertiary
        GlassMessageSeverity.WARNING, null -> movit.warning
        GlassMessageSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier.border(
            border = BorderStroke(width = 6.dp, color = color.copy(alpha = 0.86f)),
        ),
    )
}

@Composable
private fun TrainingSessionStateOverlay(
    state: TrainingSessionUiState,
    localizedPhase: String,
    onViewReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (state.isResting || state.workoutFlowPhase == WorkoutFlowPhase.REST) {
            RestPanel(
                secondsRemaining = state.restSecondsRemaining,
                nextExerciseName = state.nextExerciseName,
                tip = state.restTip,
                modifier = Modifier.align(Alignment.Center),
            )
            return
        }
        when (state.runState) {
            SessionRunState.SETUP_POSE, SessionRunState.RESUME_SETUP -> {
                SetupPosePanel(
                    progressPercent = state.setupProgressPercent,
                    phaseLabel = localizedSetupPhase(state.setupPhase),
                    guidance = state.setupGuidance,
                    actionMessage = state.setupActionMessage,
                    cameraTip = state.setupCameraTip,
                    regionStatus = state.setupRegionStatus,
                    postureStatus = state.setupPostureStatus,
                    directionStatus = state.setupDirectionStatus,
                    jointRows = state.setupJointRows,
                    referenceImageUrl = state.setupReferenceImageUrl,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            SessionRunState.COUNTDOWN, SessionRunState.RESUME_COUNTDOWN -> {
                CountdownOverlay(
                    value = state.countdownValue,
                    frozen = state.countdownFrozen,
                    freezeReason = state.setupActionMessage?.takeIf { state.countdownFrozen },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            SessionRunState.TRAINING -> {
                TrainingHud(
                    repCount = state.repCount,
                    targetReps = state.targetReps,
                    formPercent = state.liveFormPercent,
                    phaseLabel = localizedPhase,
                    elapsedLabel = state.elapsedLabel,
                    progressPercent = state.progressPercent,
                    formLabel = movitText("training_session_form_label"),
                    repsLabel = movitText("workout_flow_reps_label"),
                    timeLabel = movitText("train_metric_time"),
                    progressLabel = movitText("home_progress"),
                    coachMessage = state.glassMessage?.text,
                    coachSeverity = state.glassMessage?.severity ?: GlassMessageSeverity.INFO,
                    repsContentDescription = if (state.targetReps > 0) {
                        movitText(
                            "workout_live_reps",
                            state.repCount,
                            state.targetReps,
                        )
                    } else {
                        null
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 78.dp, start = MovitSpacing.lg, end = MovitSpacing.lg),
                )
            }
            SessionRunState.COMPLETED -> {
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
            SessionRunState.AUTO_PAUSED -> {
                val message = when (state.pauseReason) {
                    PauseReason.VISIBILITY -> movitText("training_session_auto_pause_visibility")
                    PauseReason.NO_POSE -> movitText("training_session_auto_pause_nopose")
                    else -> movitText("training_session_auto_pause_generic")
                }
                MovitGlassMessage(
                    text = message,
                    severity = GlassMessageSeverity.WARNING,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(MovitSpacing.lg),
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun TrainingSessionControls(
    state: TrainingSessionUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
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
            state.runState == SessionRunState.TRAINING -> {
                MovitButton(
                    text = movitText("training_session_pause"),
                    onClick = onPause,
                    variant = MovitButtonVariant.Filled,
                    leadingIcon = Icons.Default.Pause,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.runState.isPaused() -> {
                MovitButton(
                    text = movitText("training_session_resume"),
                    onClick = onResume,
                    variant = MovitButtonVariant.Filled,
                    leadingIcon = Icons.Default.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (
            !state.isComplete &&
            !state.isResting &&
            state.workoutFlowPhase != WorkoutFlowPhase.REST &&
            state.runState != SessionRunState.IDLE
        ) {
            MovitButton(
                text = movitText("training_session_stop"),
                onClick = onStop,
                variant = MovitButtonVariant.Outlined,
                leadingIcon = Icons.Default.Stop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MovitSpacing.sm),
            )
        }
    }
}
