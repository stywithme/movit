package com.movit.core.training.session

import com.movit.core.training.engine.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SessionSupervisor - Unified State Machine for a single-exercise workout run
 *
 * This is the Single Source of Truth for run state management.
 * It receives signals from UI, pose detection, and engine, then issues
 * commands to control the training flow.
 *
 * Key responsibilities:
 * - Manage workout-run state transitions
 * - Handle NoPose auto-pause (4s timeout)
 * - Coordinate countdown → training flow
 * - Distinguish between fresh start and resume (preserving rep count)
 * - Handle video mode specifics (pause video on auto-pause)
 *
 * Threading (WP-03): all mutations are serialized on a single consumer coroutine.
 * [processSignal] / [reset] / [onExerciseLoaded] / [onTrainingPoseFrameProcessed]
 * only enqueue work via [signalChannel]; they must not touch internal fields.
 *
 * Usage:
 * 1. Create instance in ViewModel (pass [scope] = viewModelScope)
 * 2. Collect `state` flow to update UI
 * 3. Collect `actions` flow to execute commands
 * 4. Call `processSignal()` for all events
 */
class SessionSupervisor(
    private val setupValidation: SetupValidationConfig = SetupValidationConfig(),
    private val timeProvider: () -> Long = { currentTimeMillis() },
    scope: CoroutineScope? = null,
    dispatcher: CoroutineDispatcher = if (scope != null) Dispatchers.Default else Dispatchers.Unconfined,
) {

    companion object {
        private const val TAG = "SessionSupervisor"

        // NoPose timing configuration
        private const val NO_POSE_GRACE_MS = 1000L  // 1s  - ignore brief glitches
        private const val NO_POSE_WARN_MS  = 2000L  // 2s  - show warning (handled by UI)
        private const val NO_POSE_PAUSE_MS = 4000L  // 4s  - trigger auto-pause
    }

    private sealed class InboxMessage {
        data class Signal(val signal: SupervisorSignal) : InboxMessage()
        data object Reset : InboxMessage()
        data object ExerciseLoaded : InboxMessage()
        data object TrainingPoseSeen : InboxMessage()
    }

    private val ownedJob = if (scope == null) SupervisorJob() else null
    private val signalChannel = Channel<InboxMessage>(Channel.UNLIMITED)
    private val consumerScope = scope ?: CoroutineScope(ownedJob!! + dispatcher)

    // ==================== State ====================

    private val _state = MutableStateFlow(SessionRunState.IDLE)
    val state: StateFlow<SessionRunState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<SupervisorAction>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    var droppedActionCount: Int = 0
        private set
    val actions: SharedFlow<SupervisorAction> = _actions.asSharedFlow()

    // ==================== Configuration ====================

    /** Whether in video mode (affects pause behavior) */
    var isVideoMode: Boolean = false

    // ==================== Internal State ====================

    /** Reason for current pause (MANUAL, VISIBILITY, NO_POSE) */
    private var pauseReason: PauseReason? = null

    /** Timestamp when NoPose started (for 4s timeout) */
    private var noPoseStartTime: Long = 0L

    /** Whether current countdown is for resume (preserves rep count) vs fresh start */
    private var isResumeCountdown: Boolean = false

    /** Engine was paused because the Activity left the foreground. */
    private var activityPausedEngine: Boolean = false

    /** Timestamp (ms) when consecutive invalid pose started during countdown, 0 = none. */
    private var countdownInvalidStartMs: Long = 0L

    /** Whether the countdown is currently frozen (pose lost, waiting for recovery). */
    private var countdownFrozen: Boolean = false

    /** Whether setup no-pose hint was already shown (cleared on PoseFrame). */
    private var setupNoPoseHintActive: Boolean = false

    init {
        consumerScope.launch(dispatcher) {
            for (message in signalChannel) {
                when (message) {
                    is InboxMessage.Signal -> dispatchSignal(message.signal)
                    InboxMessage.Reset -> performReset()
                    InboxMessage.ExerciseLoaded -> performOnExerciseLoaded()
                    InboxMessage.TrainingPoseSeen -> {
                        noPoseStartTime = 0L
                    }
                }
            }
        }
    }

    // ==================== Public API ====================

    /**
     * Enqueue an incoming signal for serialized processing on the supervisor consumer.
     */
    fun processSignal(signal: SupervisorSignal) {
        signalChannel.trySend(InboxMessage.Signal(signal))
    }

    /**
     * Notify that exercise was loaded - transitions from IDLE to SETUP_POSE (camera)
     * or stays in IDLE (video mode, waiting for user to press play)
     */
    fun onExerciseLoaded() {
        signalChannel.trySend(InboxMessage.ExerciseLoaded)
    }

    /**
     * Get current pause reason (for UI display)
     */
    fun getPauseReason(): PauseReason? = pauseReason

    /**
     * Reset supervisor to initial state
     */
    fun reset() {
        signalChannel.trySend(InboxMessage.Reset)
    }

    /**
     * Some high-frequency training frames are processed directly by the VM to avoid
     * SharedFlow back-pressure. Keep the supervisor's no-pose timer in sync.
     */
    fun onTrainingPoseFrameProcessed() {
        signalChannel.trySend(InboxMessage.TrainingPoseSeen)
    }

    /** Cancel the owned consumer when this supervisor was created without an external [scope]. */
    fun close() {
        signalChannel.close()
        ownedJob?.cancel()
    }

    private fun performOnExerciseLoaded() {
        if (_state.value == SessionRunState.IDLE) {
            if (isVideoMode) {
            } else {
                transitionTo(SessionRunState.SETUP_POSE)
                emit(SupervisorAction.ShowSetupPose)
            }
        }
    }

    private fun performReset() {
        _state.value = SessionRunState.IDLE
        pauseReason = null
        noPoseStartTime = 0L
        isResumeCountdown = false
        activityPausedEngine = false
        isVideoMode = false
        countdownInvalidStartMs = 0L
        countdownFrozen = false
        setupNoPoseHintActive = false
    }

    private fun dispatchSignal(signal: SupervisorSignal) {
        val currentState = _state.value

        if (handleGlobalSignal(signal, currentState)) {
            return
        }

        when (currentState) {
            SessionRunState.IDLE -> handleIdle(signal)
            SessionRunState.SETUP_POSE -> handleSetupPose(signal)
            SessionRunState.COUNTDOWN -> handleCountdown(signal)
            SessionRunState.TRAINING -> handleTraining(signal)
            SessionRunState.PAUSED -> handlePaused(signal)
            SessionRunState.AUTO_PAUSED -> handleAutoPaused(signal)
            SessionRunState.RESUME_SETUP -> handleResumeSetup(signal)
            SessionRunState.RESUME_COUNTDOWN -> handleResumeCountdown(signal)
            SessionRunState.COMPLETED -> handleCompleted(signal)
        }
    }

    // ==================== Global Signal Handler ====================

    /**
     * Handle signals that can occur from any state
     * @return true if signal was handled, false to continue to state-specific handler
     */
    private fun handleGlobalSignal(signal: SupervisorSignal, currentState: SessionRunState): Boolean {
        when (signal) {
            is SupervisorSignal.StopRequested -> {
                if (currentState != SessionRunState.IDLE && currentState != SessionRunState.COMPLETED) {
                    transitionTo(SessionRunState.COMPLETED)
                    if (isVideoMode) emit(SupervisorAction.PauseVideo)
                    emit(SupervisorAction.StopEngine)
                    emit(SupervisorAction.ShowCompleted)
                    return true
                }
            }

            is SupervisorSignal.VideoSeeked -> {
                if (currentState == SessionRunState.TRAINING) {
                    emit(SupervisorAction.ResetEngine)
                    return true
                }
            }

            is SupervisorSignal.ActivityPaused -> {
                if (currentState == SessionRunState.TRAINING && !activityPausedEngine) {
                    activityPausedEngine = true
                    emit(SupervisorAction.PauseEngine)
                    return true
                }
            }

            is SupervisorSignal.ActivityResumed -> {
                if (currentState == SessionRunState.TRAINING && activityPausedEngine) {
                    activityPausedEngine = false
                    emit(SupervisorAction.ResumeEngine)
                    return true
                }
            }

            else -> {}
        }
        return false
    }

    // ==================== State Handlers ====================

    private fun handleIdle(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.StartRequested -> {
                if (isVideoMode) {
                    transitionTo(SessionRunState.TRAINING)
                    emit(SupervisorAction.StartEngine)
                }
            }
            else -> {}
        }
    }

    private fun handleSetupPose(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.PoseFrame -> {
                setupNoPoseHintActive = false
                // Pose validation runs on the VM worker (WP-03 / I-12) — no ValidatePose action.
            }

            is SupervisorSignal.PoseConfirmed -> {
                setupNoPoseHintActive = false
                countdownInvalidStartMs = 0L
                countdownFrozen = false
                transitionTo(SessionRunState.COUNTDOWN)
                emit(SupervisorAction.StartCountdown)
            }

            is SupervisorSignal.StartRequested -> {
                if (isVideoMode) {
                    transitionTo(SessionRunState.TRAINING)
                    emit(SupervisorAction.StartEngine)
                }
            }

            is SupervisorSignal.NoPoseFrame -> {
                if (!setupNoPoseHintActive) {
                    setupNoPoseHintActive = true
                    emit(SupervisorAction.ShowSetupNoPoseHint)
                }
            }

            else -> {}
        }
    }

    private fun handleCountdown(signal: SupervisorSignal) {
        val cfg = setupValidation

        when (signal) {
            is SupervisorSignal.CountdownFinished -> {
                countdownInvalidStartMs = 0L
                countdownFrozen = false
                transitionTo(SessionRunState.TRAINING)
                if (isResumeCountdown) {
                    emit(SupervisorAction.ResumeEngine)
                } else {
                    emit(SupervisorAction.StartEngine)
                }
                isResumeCountdown = false
            }

            is SupervisorSignal.PoseFrame -> {
                // Validation on worker — no per-frame action (closes G-09 buffer pressure).
            }

            is SupervisorSignal.CountdownPoseValid -> {
                countdownInvalidStartMs = 0L
                if (countdownFrozen) {
                    countdownFrozen = false
                    emit(SupervisorAction.UnfreezeCountdown)
                }
            }

            is SupervisorSignal.PoseInvalid -> {
                handleCountdownPoseLost(cfg, signalTimestampMs = timeProvider())
            }

            is SupervisorSignal.NoPoseFrame -> {
                handleCountdownPoseLost(cfg, signalTimestampMs = signal.timestampMs)
            }

            else -> {}
        }
    }

    /**
     * Time-based countdown invalidation (device-FPS-independent):
     * - < toleranceMs  → silently ignored (noise)
     * - < cancelMs     → freeze countdown + warn user
     * - = cancelMs     → cancel countdown, back to SETUP_POSE
     */
    private fun handleCountdownPoseLost(
        cfg: SetupValidationConfig,
        signalTimestampMs: Long,
    ) {
        val now = effectivePresenceNow(signalTimestampMs)
        if (countdownInvalidStartMs == 0L) {
            countdownInvalidStartMs = now
        }
        val durationMs = now - countdownInvalidStartMs

        when {
            durationMs < cfg.countdownToleranceMs -> {
                // Grace: tiny glitch → ignore silently
            }
            durationMs < cfg.countdownCancelMs -> {
                if (!countdownFrozen) {
                    countdownFrozen = true
                    emit(SupervisorAction.FreezeCountdown)
                }
            }
            else -> {
                countdownInvalidStartMs = 0L
                countdownFrozen = false
                transitionTo(SessionRunState.SETUP_POSE)
                emit(SupervisorAction.CancelCountdown)
                emit(SupervisorAction.ShowSetupPose)
            }
        }
    }

    private fun handleTraining(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.PoseFrame -> {
                // WP-03: engine.processFrame only on the pose-frame worker — never via action.
                noPoseStartTime = 0L
            }

            is SupervisorSignal.NoPoseFrame -> {
                handleNoPoseDuringTraining(signal.timestampMs)
            }

            is SupervisorSignal.PauseRequested -> {
                pauseReason = PauseReason.MANUAL
                transitionTo(SessionRunState.PAUSED)
                emit(SupervisorAction.PauseEngine)
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
            }

            is SupervisorSignal.TargetReached -> {
                transitionTo(SessionRunState.COMPLETED)
                emit(SupervisorAction.StopEngine)
                emit(SupervisorAction.ShowCompleted)
            }

            is SupervisorSignal.VisibilityPaused -> {
                pauseReason = PauseReason.VISIBILITY
                transitionTo(SessionRunState.AUTO_PAUSED)
                emit(SupervisorAction.PauseEngine)
                emit(SupervisorAction.ShowAutoPaused(PauseReason.VISIBILITY))
            }

            is SupervisorSignal.VideoEnded -> {
                transitionTo(SessionRunState.COMPLETED)
                emit(SupervisorAction.StopEngine)
                emit(SupervisorAction.ShowCompleted)
            }

            else -> {}
        }
    }

    private fun handlePaused(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.ResumeRequested -> {
                if (isVideoMode) {
                    transitionTo(SessionRunState.TRAINING)
                    emit(SupervisorAction.ResumeEngine)
                    emit(SupervisorAction.ResumeVideo)
                } else {
                    isResumeCountdown = true
                    transitionTo(SessionRunState.COUNTDOWN)
                    emit(SupervisorAction.StartCountdown)
                }
            }

            else -> {}
        }
    }

    private fun handleAutoPaused(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.PoseFrame -> {
                if (pauseReason == PauseReason.NO_POSE) {
                    if (isVideoMode) {
                        transitionTo(SessionRunState.TRAINING)
                        emit(SupervisorAction.ResumeFromVisibilityPause)
                    } else {
                        transitionTo(SessionRunState.RESUME_SETUP)
                        emit(SupervisorAction.ShowSetupPose)
                    }
                }
            }

            is SupervisorSignal.VisibilityRestored -> {
                if (pauseReason == PauseReason.VISIBILITY) {
                    isResumeCountdown = true
                    transitionTo(SessionRunState.RESUME_COUNTDOWN)
                    emit(SupervisorAction.StartCountdown)
                }
            }

            is SupervisorSignal.ResumeRequested -> {
                if (isVideoMode) {
                    transitionTo(SessionRunState.TRAINING)
                    emit(SupervisorAction.ResumeFromVisibilityPause)
                    emit(SupervisorAction.ResumeVideo)
                } else {
                    transitionTo(SessionRunState.RESUME_SETUP)
                    emit(SupervisorAction.ShowSetupPose)
                }
            }

            else -> {}
        }
    }

    private fun handleResumeSetup(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.PoseFrame -> {
                // Validation on worker — no ValidatePose action.
            }

            is SupervisorSignal.PoseConfirmed -> {
                transitionTo(SessionRunState.RESUME_COUNTDOWN)
                emit(SupervisorAction.StartCountdown)
            }

            is SupervisorSignal.NoPoseFrame -> {
                transitionTo(SessionRunState.AUTO_PAUSED)
                emit(SupervisorAction.ShowAutoPaused(pauseReason ?: PauseReason.NO_POSE))
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
            }

            else -> {}
        }
    }

    private fun handleResumeCountdown(signal: SupervisorSignal) {
        val cfg = setupValidation
        when (signal) {
            is SupervisorSignal.CountdownFinished -> {
                countdownInvalidStartMs = 0L
                countdownFrozen = false
                transitionTo(SessionRunState.TRAINING)
                emit(SupervisorAction.ResumeFromVisibilityPause)
            }

            is SupervisorSignal.PoseFrame -> {
                // Validation on worker — no ValidatePose action.
            }

            is SupervisorSignal.CountdownPoseValid -> {
                countdownInvalidStartMs = 0L
                if (countdownFrozen) {
                    countdownFrozen = false
                    emit(SupervisorAction.UnfreezeCountdown)
                }
            }

            is SupervisorSignal.PoseInvalid -> {
                handleCountdownPoseLost(cfg, signalTimestampMs = timeProvider())
                if (_state.value == SessionRunState.SETUP_POSE) {
                    transitionTo(SessionRunState.AUTO_PAUSED)
                    emit(SupervisorAction.ShowAutoPaused(pauseReason ?: PauseReason.VISIBILITY))
                }
            }

            is SupervisorSignal.NoPoseFrame -> {
                handleCountdownPoseLost(cfg, signalTimestampMs = signal.timestampMs)
                if (_state.value == SessionRunState.SETUP_POSE) {
                    transitionTo(SessionRunState.AUTO_PAUSED)
                    emit(SupervisorAction.ShowAutoPaused(PauseReason.NO_POSE))
                    if (isVideoMode) emit(SupervisorAction.PauseVideo)
                }
            }

            else -> {}
        }
    }

    private fun handleCompleted(signal: SupervisorSignal) {
        // Most signals ignored in COMPLETED state
    }

    // ==================== NoPose Logic ====================

    /**
     * Full-body absence during TRAINING (layer 1 — see [PresenceSupervisorBridge] for partial visibility).
     */
    private fun handleNoPoseDuringTraining(signalTimestampMs: Long) {
        val now = effectivePresenceNow(signalTimestampMs)

        if (noPoseStartTime == 0L) {
            noPoseStartTime = now
            return
        }

        val duration = now - noPoseStartTime

        when {
            duration >= NO_POSE_PAUSE_MS -> {
                pauseReason = PauseReason.NO_POSE
                transitionTo(SessionRunState.AUTO_PAUSED)
                emit(SupervisorAction.PauseEngine)
                emit(SupervisorAction.ShowAutoPaused(PauseReason.NO_POSE))
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
                noPoseStartTime = 0L
            }
            duration >= NO_POSE_WARN_MS -> emitNoPoseWarning(duration)
        }
    }

    private fun emitNoPoseWarning(elapsedMs: Long) {
        emit(SupervisorAction.ShowNoPoseWarning(elapsedMs))
    }

    // ==================== Helpers ====================

    internal fun testEffectivePresenceNow(signalMs: Long): Long = effectivePresenceNow(signalMs)

    internal var testNoPoseStartTimeMs: Long
        get() = noPoseStartTime
        set(value) { noPoseStartTime = value }

    private fun effectivePresenceNow(signalMs: Long): Long {
        val wall = timeProvider()
        if (signalMs <= 0L) return wall
        if (noPoseStartTime > 0L && signalMs <= noPoseStartTime) return wall
        if (countdownInvalidStartMs > 0L && signalMs <= countdownInvalidStartMs) return wall
        return signalMs
    }

    private fun transitionTo(newState: SessionRunState) {
        _state.value = newState
    }

    private fun emit(action: SupervisorAction) {
        if (!_actions.tryEmit(action)) {
            droppedActionCount++
        }
    }
}
