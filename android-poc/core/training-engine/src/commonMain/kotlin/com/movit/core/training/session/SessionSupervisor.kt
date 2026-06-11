package com.movit.core.training.session

import com.movit.core.training.engine.currentTimeMillis
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * - Coordinate countdown ? training flow
 * - Distinguish between fresh start and resume (preserving rep count)
 * - Handle video mode specifics (pause video on auto-pause)
 * 
 * Usage:
 * 1. Create instance in ViewModel
 * 2. Collect `state` flow to update UI
 * 3. Collect `actions` flow to execute commands
 * 4. Call `processSignal()` for all events
 */
class SessionSupervisor(
    private val setupValidation: SetupValidationConfig = SetupValidationConfig(),
    private val timeProvider: () -> Long = { currentTimeMillis() },
) {
    
    companion object {
        private const val TAG = "SessionSupervisor"

        // NoPose timing configuration
        private const val NO_POSE_GRACE_MS = 1000L  // 1s  - ignore brief glitches
        private const val NO_POSE_WARN_MS  = 2000L  // 2s  - show warning (handled by UI)
        private const val NO_POSE_PAUSE_MS = 4000L  // 4s  - trigger auto-pause
    }
    
    // ==================== State ====================
    
    private val _state = MutableStateFlow(SessionRunState.IDLE)
    val state: StateFlow<SessionRunState> = _state.asStateFlow()
    
    private val _actions = MutableSharedFlow<SupervisorAction>(
        replay = 0,
        extraBufferCapacity = 16
    )
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
    
    // ==================== Public API ====================
    
    /**
     * Process an incoming signal and update state accordingly
     * 
     * This is the main entry point. All events (UI, pose, engine) 
     * should be routed through this method.
     */
    fun processSignal(signal: SupervisorSignal) {
        val currentState = _state.value
        
        // Handle global signals that work from any state
        if (handleGlobalSignal(signal, currentState)) {
            return
        }
        
        // Handle state-specific signals
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
    
    /**
     * Notify that exercise was loaded - transitions from IDLE to SETUP_POSE (camera)
     * or stays in IDLE (video mode, waiting for user to press play)
     */
    fun onExerciseLoaded() {
        if (_state.value == SessionRunState.IDLE) {
            if (isVideoMode) {
            } else {
                transitionTo(SessionRunState.SETUP_POSE)
                emit(SupervisorAction.ShowSetupPose)
            }
        }
    }
    
    /**
     * Get current pause reason (for UI display)
     */
    fun getPauseReason(): PauseReason? = pauseReason
    
    /**
     * Reset supervisor to initial state
     */
    fun reset() {
        _state.value = SessionRunState.IDLE
        pauseReason = null
        noPoseStartTime = 0L
        isResumeCountdown = false
        activityPausedEngine = false
        isVideoMode = false
        countdownInvalidStartMs = 0L
        countdownFrozen = false
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
        // Most signals ignored in IDLE - waiting for exercise to load
        when (signal) {
            is SupervisorSignal.StartRequested -> {
                // Video mode can start immediately from IDLE if exercise is loaded
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
                // Forward to PoseSetupGuide (carries landmarks for camera check)
                emit(SupervisorAction.ValidatePose(signal.angles, signal.landmarks, signal.isFrontCamera))
            }

            is SupervisorSignal.PoseConfirmed -> {
                // Rolling window confirmed - start countdown
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
                // UI can show a "stand in front of camera" hint
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
                // Valid frame ? reset tolerance
                countdownInvalidStartMs = 0L
                if (countdownFrozen) {
                    countdownFrozen = false
                    emit(SupervisorAction.UnfreezeCountdown)
                }
                // Lightweight validation ? ViewModel will send PoseInvalid if angles are off
                emit(SupervisorAction.ValidatePose(signal.angles, signal.landmarks, signal.isFrontCamera))
            }

            is SupervisorSignal.PoseInvalid -> {
                handleCountdownPoseLost(cfg)
            }

            is SupervisorSignal.NoPoseFrame -> {
                handleCountdownPoseLost(cfg)
            }

            else -> {}
        }
    }

    /**
     * Time-based countdown invalidation (device-FPS-independent):
     * - < toleranceMs  ? silently ignored (noise)
     * - < cancelMs     ? freeze countdown + warn user
     * - = cancelMs     ? cancel countdown, back to SETUP_POSE
     */
    private fun handleCountdownPoseLost(
        cfg: com.movit.core.training.session.SetupValidationConfig
    ) {
        val now = timeProvider()
        if (countdownInvalidStartMs == 0L) {
            countdownInvalidStartMs = now
        }
        val durationMs = now - countdownInvalidStartMs

        when {
            durationMs < cfg.countdownToleranceMs -> {
                // Grace: tiny glitch ? ignore silently
            }
            durationMs < cfg.countdownCancelMs -> {
                if (!countdownFrozen) {
                    countdownFrozen = true
                    emit(SupervisorAction.FreezeCountdown)
                }
            }
            else -> {
                val elapsed = durationMs
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
                // Reset NoPose timer
                noPoseStartTime = 0L
                // Process frame through engine
                emit(SupervisorAction.ProcessFrame(
                    signal.angles,
                    signal.landmarks,
                    signal.isFrontCamera,
                    signal.timestampMs
                ))
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
                emit(SupervisorAction.ValidatePose(signal.angles, signal.landmarks, signal.isFrontCamera))
            }
            
            is SupervisorSignal.PoseConfirmed -> {
                transitionTo(SessionRunState.RESUME_COUNTDOWN)
                emit(SupervisorAction.StartCountdown)
            }
            
            is SupervisorSignal.NoPoseFrame -> {
                // Lost pose during resume setup - go back to AUTO_PAUSED
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
                // KEY: Use ResumeFromVisibilityPause to preserve rep count
                emit(SupervisorAction.ResumeFromVisibilityPause)
            }

            is SupervisorSignal.PoseFrame -> {
                countdownInvalidStartMs = 0L
                if (countdownFrozen) {
                    countdownFrozen = false
                    emit(SupervisorAction.UnfreezeCountdown)
                }
                emit(SupervisorAction.ValidatePose(signal.angles, signal.landmarks, signal.isFrontCamera))
            }

            is SupervisorSignal.PoseInvalid -> {
                handleCountdownPoseLost(cfg)
                if (_state.value == SessionRunState.SETUP_POSE) {
                    transitionTo(SessionRunState.AUTO_PAUSED)
                    emit(SupervisorAction.ShowAutoPaused(pauseReason ?: PauseReason.VISIBILITY))
                }
            }

            is SupervisorSignal.NoPoseFrame -> {
                handleCountdownPoseLost(cfg)
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
        // Could handle restart here if needed
    }
    
    // ==================== NoPose Logic ====================
    
    /**
     * Handle NoPose frames during TRAINING with 4s timeout
     */
    private fun handleNoPoseDuringTraining(now: Long) {
        
        // Start timer if not already started
        if (noPoseStartTime == 0L) {
            noPoseStartTime = now
            return
        }
        
        val duration = now - noPoseStartTime
        
        when {
            duration >= NO_POSE_PAUSE_MS -> {
                // 4s elapsed - trigger auto-pause
                pauseReason = PauseReason.NO_POSE
                transitionTo(SessionRunState.AUTO_PAUSED)
                emit(SupervisorAction.PauseEngine)
                emit(SupervisorAction.ShowAutoPaused(PauseReason.NO_POSE))
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
                noPoseStartTime = 0L
            }
            duration >= NO_POSE_WARN_MS -> {
                // 2s elapsed - show warning to user
                emit(SupervisorAction.ShowNoPoseWarning(duration))
            }
            // < 2s: grace period, ignored
        }
    }
    
    // ==================== Helpers ====================
    
    private fun transitionTo(newState: SessionRunState) {
        val oldState = _state.value
        _state.value = newState
    }
    
    private fun emit(action: SupervisorAction) {
        val emitted = _actions.tryEmit(action)
        if (!emitted) {
        }
    }
}
