package com.trainingvalidator.poc.training.workout

import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark

/**
 * SupervisorSignal - Input events received by WorkoutRunSupervisor
 * 
 * These signals come from:
 * - UI (user actions: pause, resume, stop)
 * - Pose Detection (frames with/without pose)
 * - PoseSetupGuide (confirmation of valid start pose)
 * - TrainingEngine (target reached, visibility events)
 * - CountdownController (countdown finished)
 */
sealed class SupervisorSignal {
    
    // ==================== UI Commands ====================
    
    /** User requested to start training */
    object StartRequested : SupervisorSignal()
    
    /** User pressed pause button */
    object PauseRequested : SupervisorSignal()
    
    /** User pressed resume button */
    object ResumeRequested : SupervisorSignal()
    
    /** User pressed stop/close button */
    object StopRequested : SupervisorSignal()

    /** Activity moved to background; pause engine resources without changing run state. */
    object ActivityPaused : SupervisorSignal()

    /** Activity returned to foreground; resume engine resources if lifecycle-paused. */
    object ActivityResumed : SupervisorSignal()
    
    // ==================== Pose Data ====================
    
    /**
     * Frame with detected pose - sent every frame from camera
     * 
     * @param angles Calculated joint angles
     * @param landmarks Smoothed landmarks for position validation
     * @param isFrontCamera Whether using front camera (for visibility check mirroring)
     * @param timestampMs Frame timestamp (monotonic)
     */
    data class PoseFrame(
        val angles: JointAngles,
        val landmarks: List<SmoothedLandmark>?,
        val isFrontCamera: Boolean,
        val timestampMs: Long
    ) : SupervisorSignal()
    
    /** No pose detected in current frame */
    data class NoPoseFrame(val timestampMs: Long) : SupervisorSignal()
    
    // ==================== Pose Validation ====================
    
    /** PoseSetupGuide confirmed valid start pose (rolling-window validation) */
    object PoseConfirmed : SupervisorSignal()
    
    /** PoseSetupGuide detected invalid pose during countdown */
    object PoseInvalid : SupervisorSignal()
    
    // ==================== Engine Events ====================
    
    /** TrainingEngine reached target reps or hold duration */
    object TargetReached : SupervisorSignal()
    
    /** VisibilityMonitor triggered pause (joints not visible for 4s) */
    object VisibilityPaused : SupervisorSignal()
    
    /** VisibilityMonitor detected joints visible again after pause */
    object VisibilityRestored : SupervisorSignal()
    
    // ==================== Countdown Events ====================
    
    /** Countdown finished (3-2-1-GO completed) */
    object CountdownFinished : SupervisorSignal()

    // ==================== Video Events ====================

    /** Video playback ended */
    object VideoEnded : SupervisorSignal()

    /** User seeked in video - requires analysis reset */
    object VideoSeeked : SupervisorSignal()
}
