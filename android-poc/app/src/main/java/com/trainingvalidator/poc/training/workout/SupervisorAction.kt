package com.trainingvalidator.poc.training.workout

import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark

/**
 * SupervisorAction - Output commands issued by WorkoutRunSupervisor
 * 
 * These actions are executed by TrainingViewModel which:
 * - Forwards engine commands to TrainingEngine
 * - Forwards UI commands to TrainingActivity via events
 * - Handles pose validation
 */
sealed class SupervisorAction {
    
    // ==================== Engine Commands ====================
    
    /** Start TrainingEngine (fresh start) */
    object StartEngine : SupervisorAction()
    
    /** Pause TrainingEngine */
    object PauseEngine : SupervisorAction()
    
    /** Resume TrainingEngine (after manual pause) */
    object ResumeEngine : SupervisorAction()
    
    /** Stop TrainingEngine and get summary */
    object StopEngine : SupervisorAction()
    
    /** Resume from visibility pause - preserves rep count */
    object ResumeFromVisibilityPause : SupervisorAction()

    /** Reset engine for video seek (stop + start) */
    object ResetEngine : SupervisorAction()
    
    // ==================== Frame Processing ====================
    
    /**
     * Process frame through TrainingEngine
     * Only emitted when state is TRAINING
     */
    data class ProcessFrame(
        val angles: JointAngles,
        val landmarks: List<SmoothedLandmark>?,
        val isFrontCamera: Boolean,
        val timestampMs: Long
    ) : SupervisorAction()
    
    /**
     * Validate pose through PoseSetupGuide
     * Emitted during SETUP_POSE, COUNTDOWN, RESUME_SETUP, RESUME_COUNTDOWN
     *
     * Carries both angles and landmarks so PoseSetupGuide can run
     * camera-position detection alongside joint-angle validation.
     */
    data class ValidatePose(
        val angles: JointAngles,
        val landmarks: List<SmoothedLandmark>?,
        val isFrontCamera: Boolean = false
    ) : SupervisorAction()
    
    // ==================== UI Commands ====================
    
    /** Show setup pose panel */
    object ShowSetupPose : SupervisorAction()
    
    /** Start countdown timer (3-2-1-GO) */
    object StartCountdown : SupervisorAction()
    
    /** Cancel countdown and return to setup */
    object CancelCountdown : SupervisorAction()

    /** Freeze (pause) the visible countdown display - pose temporarily lost */
    object FreezeCountdown : SupervisorAction()

    /** Unfreeze the countdown - pose recovered */
    object UnfreezeCountdown : SupervisorAction()
    
    /** Show auto-paused overlay with reason */
    data class ShowAutoPaused(val reason: PauseReason) : SupervisorAction()
    
    /** Show warning when no pose detected (before auto-pause) */
    data class ShowNoPoseWarning(val elapsedMs: Long) : SupervisorAction()
    
    /** Show completed panel */
    object ShowCompleted : SupervisorAction()

    // ==================== Video Commands ====================

    /** Pause video playback */
    object PauseVideo : SupervisorAction()

    /** Resume video playback */
    object ResumeVideo : SupervisorAction()
}

/**
 * Reason for automatic pause
 */
enum class PauseReason {
    /** User pressed pause button */
    MANUAL,
    
    /** Required joints not visible (from VisibilityMonitor) */
    VISIBILITY,
    
    /** No pose detected for 4 seconds */
    NO_POSE
}
