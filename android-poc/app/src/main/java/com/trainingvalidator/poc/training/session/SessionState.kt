package com.trainingvalidator.poc.training.session

/**
 * SessionState - Represents the current state of a training session
 * 
 * This is the Single Source of Truth for session lifecycle, managed by SessionSupervisor.
 * 
 * State Flow:
 * IDLE → SETUP_POSE → COUNTDOWN → TRAINING → COMPLETED
 *                                    ↓
 *                              PAUSED / AUTO_PAUSED
 *                                    ↓
 *                        RESUME_SETUP → RESUME_COUNTDOWN → TRAINING
 */
enum class SessionState {
    /** No exercise loaded - initial state */
    IDLE,
    
    /** Waiting for user to get into valid start position (PoseSetupGuide) */
    SETUP_POSE,
    
    /** Countdown before training starts (3-2-1-GO) */
    COUNTDOWN,
    
    /** Active training - Engine is running and processing frames */
    TRAINING,
    
    /** Manual pause by user */
    PAUSED,
    
    /** Automatic pause due to visibility loss or no pose detected */
    AUTO_PAUSED,
    
    /** After auto-pause, validating pose before resume */
    RESUME_SETUP,
    
    /** Countdown before resuming (3-2-1-GO) - preserves rep count */
    RESUME_COUNTDOWN,
    
    /** Training completed - target reached or stopped */
    COMPLETED;
    
    /**
     * Check if training is active (Engine should be running)
     */
    fun isTrainingActive(): Boolean = this == TRAINING
    
    /**
     * Check if in any paused state
     */
    fun isPaused(): Boolean = this == PAUSED || this == AUTO_PAUSED
    
    /**
     * Check if frames should be processed for pose validation
     */
    fun shouldValidatePose(): Boolean = this in listOf(
        SETUP_POSE, COUNTDOWN, RESUME_SETUP, RESUME_COUNTDOWN
    )
    
    /**
     * Check if session is in a resumable state
     */
    fun canResume(): Boolean = this == PAUSED || this == AUTO_PAUSED
}
