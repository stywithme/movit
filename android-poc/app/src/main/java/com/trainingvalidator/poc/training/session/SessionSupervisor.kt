package com.trainingvalidator.poc.training.session

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SessionSupervisor - Unified State Machine for Training Session
 * 
 * This is the Single Source of Truth for session state management.
 * It receives signals from UI, pose detection, and engine, then issues
 * commands to control the training flow.
 * 
 * Key responsibilities:
 * - Manage session state transitions
 * - Handle NoPose auto-pause (4s timeout)
 * - Coordinate countdown → training flow
 * - Distinguish between fresh start and resume (preserving rep count)
 * - Handle video mode specifics (pause video on auto-pause)
 * 
 * Usage:
 * 1. Create instance in ViewModel
 * 2. Collect `state` flow to update UI
 * 3. Collect `actions` flow to execute commands
 * 4. Call `processSignal()` for all events
 */
class SessionSupervisor {
    
    companion object {
        private const val TAG = "SessionSupervisor"

        // NoPose timing configuration
        private const val NO_POSE_GRACE_MS = 1000L  // 1s  - ignore brief glitches
        private const val NO_POSE_WARN_MS  = 2000L  // 2s  - show warning (handled by UI)
        private const val NO_POSE_PAUSE_MS = 4000L  // 4s  - trigger auto-pause
    }
    
    // ==================== State ====================
    
    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
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

    /** Consecutive invalid frames seen during an active countdown (for tolerant cancel). */
    private var countdownInvalidFrames: Int = 0

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
            SessionState.IDLE -> handleIdle(signal)
            SessionState.SETUP_POSE -> handleSetupPose(signal)
            SessionState.COUNTDOWN -> handleCountdown(signal)
            SessionState.TRAINING -> handleTraining(signal)
            SessionState.PAUSED -> handlePaused(signal)
            SessionState.AUTO_PAUSED -> handleAutoPaused(signal)
            SessionState.RESUME_SETUP -> handleResumeSetup(signal)
            SessionState.RESUME_COUNTDOWN -> handleResumeCountdown(signal)
            SessionState.COMPLETED -> handleCompleted(signal)
        }
    }
    
    /**
     * Notify that exercise was loaded - transitions from IDLE to SETUP_POSE
     */
    fun onExerciseLoaded() {
        if (_state.value == SessionState.IDLE) {
            transitionTo(SessionState.SETUP_POSE)
            emit(SupervisorAction.ShowSetupPose)
            Log.d(TAG, "Exercise loaded - showing setup pose")
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
        _state.value = SessionState.IDLE
        pauseReason = null
        noPoseStartTime = 0L
        isResumeCountdown = false
        isVideoMode = false
        countdownInvalidFrames = 0
        countdownFrozen = false
        Log.d(TAG, "Supervisor reset to IDLE")
    }
    
    // ==================== Global Signal Handler ====================
    
    /**
     * Handle signals that can occur from any state
     * @return true if signal was handled, false to continue to state-specific handler
     */
    private fun handleGlobalSignal(signal: SupervisorSignal, currentState: SessionState): Boolean {
        when (signal) {
            is SupervisorSignal.StopRequested -> {
                if (currentState != SessionState.IDLE && currentState != SessionState.COMPLETED) {
                    transitionTo(SessionState.COMPLETED)
                    if (isVideoMode) emit(SupervisorAction.PauseVideo)
                    emit(SupervisorAction.StopEngine)
                    emit(SupervisorAction.ShowCompleted)
                    Log.d(TAG, "Stop requested - transitioning to COMPLETED")
                    return true
                }
            }
            
            is SupervisorSignal.VideoSeeked -> {
                if (currentState == SessionState.TRAINING) {
                    emit(SupervisorAction.ResetEngine)
                    Log.d(TAG, "Video seeked - resetting engine")
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
                    transitionTo(SessionState.TRAINING)
                    emit(SupervisorAction.StartEngine)
                    Log.d(TAG, "Video mode - starting immediately")
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
                countdownInvalidFrames = 0
                countdownFrozen = false
                transitionTo(SessionState.COUNTDOWN)
                emit(SupervisorAction.StartCountdown)
                Log.d(TAG, "Pose confirmed - starting countdown")
            }

            is SupervisorSignal.StartRequested -> {
                if (isVideoMode) {
                    transitionTo(SessionState.TRAINING)
                    emit(SupervisorAction.StartEngine)
                    Log.d(TAG, "Video mode - skipping countdown")
                }
            }

            is SupervisorSignal.NoPoseFrame -> {
                // UI can show a "stand in front of camera" hint
            }

            else -> {}
        }
    }

    private fun handleCountdown(signal: SupervisorSignal) {
        val cfg = SettingsManager.settings.setupValidation

        when (signal) {
            is SupervisorSignal.CountdownFinished -> {
                countdownInvalidFrames = 0
                countdownFrozen = false
                transitionTo(SessionState.TRAINING)
                if (isResumeCountdown) {
                    emit(SupervisorAction.ResumeEngine)
                    Log.d(TAG, "Countdown finished - resuming training")
                } else {
                    emit(SupervisorAction.StartEngine)
                    Log.d(TAG, "Countdown finished - starting training")
                }
                isResumeCountdown = false
            }

            is SupervisorSignal.PoseFrame -> {
                // Valid frame — reset tolerance and check if angles still in range
                countdownInvalidFrames = 0
                if (countdownFrozen) {
                    countdownFrozen = false
                    emit(SupervisorAction.UnfreezeCountdown)
                    Log.d(TAG, "Pose recovered - unfreezing countdown")
                }
                // Lightweight validation — ViewModel will send PoseInvalid if angles are off
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
     * Tolerant countdown invalidation:
     * - ≤ toleranceFrames   → silently ignored
     * - ≤ freezeFrames      → freeze countdown + warn user
     * - > cancelFrames      → cancel countdown, back to SETUP_POSE
     */
    private fun handleCountdownPoseLost(
        cfg: com.trainingvalidator.poc.training.config.SetupValidationSettings
    ) {
        countdownInvalidFrames++
        when {
            countdownInvalidFrames <= cfg.countdownToleranceFrames -> {
                // Grace: tiny glitch - ignore silently
            }
            countdownInvalidFrames <= cfg.countdownFreezeFrames -> {
                // Freeze: pause the visual countdown, show warning
                if (!countdownFrozen) {
                    countdownFrozen = true
                    emit(SupervisorAction.FreezeCountdown)
                    Log.d(TAG, "Countdown frozen at frame $countdownInvalidFrames")
                }
            }
            countdownInvalidFrames > cfg.countdownCancelFrames -> {
                // Cancel: too long out of position
                val framesBeforeReset = countdownInvalidFrames
                countdownInvalidFrames = 0
                countdownFrozen = false
                transitionTo(SessionState.SETUP_POSE)
                emit(SupervisorAction.CancelCountdown)
                emit(SupervisorAction.ShowSetupPose)
                Log.d(TAG, "Countdown cancelled after $framesBeforeReset invalid frames")
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
                transitionTo(SessionState.PAUSED)
                emit(SupervisorAction.PauseEngine)
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
                Log.d(TAG, "Manual pause requested")
            }
            
            is SupervisorSignal.VisibilityPaused -> {
                pauseReason = PauseReason.VISIBILITY
                transitionTo(SessionState.AUTO_PAUSED)
                emit(SupervisorAction.PauseEngine)
                emit(SupervisorAction.ShowAutoPaused(PauseReason.VISIBILITY))
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
                Log.d(TAG, "Visibility pause triggered")
            }
            
            is SupervisorSignal.TargetReached -> {
                transitionTo(SessionState.COMPLETED)
                emit(SupervisorAction.StopEngine)
                emit(SupervisorAction.ShowCompleted)
                Log.d(TAG, "Target reached - training completed")
            }
            
            is SupervisorSignal.VideoEnded -> {
                transitionTo(SessionState.COMPLETED)
                emit(SupervisorAction.StopEngine)
                emit(SupervisorAction.ShowCompleted)
                Log.d(TAG, "Video ended - training completed")
            }
            
            else -> {}
        }
    }
    
    private fun handlePaused(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.ResumeRequested -> {
                // Manual pause → require countdown before resume
                isResumeCountdown = true  // Mark as resume countdown
                transitionTo(SessionState.COUNTDOWN)
                emit(SupervisorAction.StartCountdown)
                if (isVideoMode) emit(SupervisorAction.ResumeVideo)
                Log.d(TAG, "Resume requested - starting countdown")
            }
            
            else -> {}
        }
    }
    
    private fun handleAutoPaused(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.VisibilityRestored -> {
                // Go to RESUME_SETUP to validate pose before resume countdown
                transitionTo(SessionState.RESUME_SETUP)
                emit(SupervisorAction.ShowSetupPose)
                Log.d(TAG, "Visibility restored - validating pose for resume")
            }
            
            is SupervisorSignal.PoseFrame -> {
                // Pose detected after NoPose pause - start resume flow
                if (pauseReason == PauseReason.NO_POSE) {
                    transitionTo(SessionState.RESUME_SETUP)
                    emit(SupervisorAction.ShowSetupPose)
                    Log.d(TAG, "Pose detected after NoPose - validating for resume")
                }
            }
            
            is SupervisorSignal.ResumeRequested -> {
                // User manually trying to resume from auto-pause
                // Go to RESUME_SETUP first
                transitionTo(SessionState.RESUME_SETUP)
                emit(SupervisorAction.ShowSetupPose)
                if (isVideoMode) emit(SupervisorAction.ResumeVideo)
                Log.d(TAG, "Manual resume from auto-pause - showing setup")
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
                transitionTo(SessionState.RESUME_COUNTDOWN)
                emit(SupervisorAction.StartCountdown)
                Log.d(TAG, "Resume pose confirmed - starting countdown")
            }
            
            is SupervisorSignal.NoPoseFrame -> {
                // Lost pose during resume setup - go back to AUTO_PAUSED
                transitionTo(SessionState.AUTO_PAUSED)
                emit(SupervisorAction.ShowAutoPaused(pauseReason ?: PauseReason.NO_POSE))
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
                Log.d(TAG, "Lost pose during resume setup - back to auto-paused")
            }
            
            else -> {}
        }
    }
    
    private fun handleResumeCountdown(signal: SupervisorSignal) {
        val cfg = SettingsManager.settings.setupValidation
        when (signal) {
            is SupervisorSignal.CountdownFinished -> {
                countdownInvalidFrames = 0
                countdownFrozen = false
                transitionTo(SessionState.TRAINING)
                // KEY: Use ResumeFromVisibilityPause to preserve rep count
                emit(SupervisorAction.ResumeFromVisibilityPause)
                Log.d(TAG, "Resume countdown finished - resuming with preserved rep count")
            }

            is SupervisorSignal.PoseFrame -> {
                countdownInvalidFrames = 0
                if (countdownFrozen) {
                    countdownFrozen = false
                    emit(SupervisorAction.UnfreezeCountdown)
                }
                emit(SupervisorAction.ValidatePose(signal.angles, signal.landmarks, signal.isFrontCamera))
            }

            is SupervisorSignal.PoseInvalid -> {
                handleCountdownPoseLost(cfg)
                if (_state.value == SessionState.SETUP_POSE) {
                    transitionTo(SessionState.AUTO_PAUSED)
                    emit(SupervisorAction.ShowAutoPaused(pauseReason ?: PauseReason.VISIBILITY))
                }
            }

            is SupervisorSignal.NoPoseFrame -> {
                handleCountdownPoseLost(cfg)
                if (_state.value == SessionState.SETUP_POSE) {
                    transitionTo(SessionState.AUTO_PAUSED)
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
            Log.d(TAG, "NoPose detected - starting timer")
            return
        }
        
        val duration = now - noPoseStartTime
        
        when {
            duration >= NO_POSE_PAUSE_MS -> {
                // 4s elapsed - trigger auto-pause
                pauseReason = PauseReason.NO_POSE
                transitionTo(SessionState.AUTO_PAUSED)
                emit(SupervisorAction.PauseEngine)
                emit(SupervisorAction.ShowAutoPaused(PauseReason.NO_POSE))
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
                noPoseStartTime = 0L
                Log.d(TAG, "NoPose for ${duration}ms - auto-pausing")
            }
            duration >= NO_POSE_WARN_MS -> {
                // 2s elapsed - show warning to user
                emit(SupervisorAction.ShowNoPoseWarning(duration))
                Log.d(TAG, "NoPose warning: ${duration}ms")
            }
            // < 2s: grace period, ignored
        }
    }
    
    // ==================== Helpers ====================
    
    private fun transitionTo(newState: SessionState) {
        val oldState = _state.value
        _state.value = newState
        Log.d(TAG, "State: $oldState → $newState")
    }
    
    private fun emit(action: SupervisorAction) {
        val emitted = _actions.tryEmit(action)
        if (!emitted) {
            Log.w(TAG, "Failed to emit action: $action (buffer full)")
        }
    }
}
