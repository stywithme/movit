package com.movit.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.movit.core.training.session.PauseReason
import com.movit.core.training.session.SessionRunState
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.GlassMessageSeverity
import com.movit.designsystem.components.MovitActionDock
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitGlassMessage
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitSkeletonOverlay
import com.movit.designsystem.components.TrainingHud
import com.movit.designsystem.components.VignetteEffect
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
            state.configUnavailable -> {
                MovitErrorState(
                    title = movitText("common_error_title"),
                    message = movitText("workout_live_config_unavailable"),
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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

                    TrainingDebugFpsOverlay(
                        fps = debugFps,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(MovitSpacing.md),
                    )

                    if (onFlipCamera != null && !state.isComplete && !state.isResting) {
                        TrainingCameraFlipButton(
                            onClick = onFlipCamera,
                            enabled = !state.isCameraSwitching,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = MovitSpacing.xxl, end = MovitSpacing.md),
                        )
                    }

                    TrainingSessionStateOverlay(
                        state = state,
                        localizedPhase = localizedPhase,
                        onViewReport = onViewReport,
                        modifier = Modifier.fillMaxSize(),
                    )

                    state.glassMessage?.let { message ->
                        MovitGlassMessage(
                            text = message.text,
                            severity = message.severity,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = MovitSpacing.xl, start = MovitSpacing.lg, end = MovitSpacing.lg),
                        )
                    }

                    TrainingSessionControls(
                        state = state,
                        localizedPhase = localizedPhase,
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
                    repsContentDescription = movitText(
                        "workout_live_reps",
                        state.repCount,
                        state.targetReps,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(MovitSpacing.lg),
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
    localizedPhase: String,
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
                MovitActionDock(
                    timerText = state.elapsedLabel,
                    title = movitText("training_session_live_dock_title"),
                    subtitle = movitText("workout_live_phase", localizedPhase),
                    onPlayClick = onPause,
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
                leadingIcon = Icons.Default.Pause,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MovitSpacing.sm),
            )
        }
    }
}
