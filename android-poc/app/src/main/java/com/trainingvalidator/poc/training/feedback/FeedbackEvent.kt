package com.trainingvalidator.poc.training.feedback

import com.trainingvalidator.poc.training.engine.CameraPositionWarning
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * FeedbackEvent - Represents a feedback event to be displayed/played
 * 
 * These events are emitted by the TrainingEngine and consumed
 * by the UI to show visual/audio feedback.
 */
sealed class FeedbackEvent {
    abstract val timestamp: Long
    abstract val priority: FeedbackPriority
    
    /**
     * Phase changed event
     */
    data class PhaseChanged(
        val previousPhase: Phase,
        val newPhase: Phase,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.LOW
    ) : FeedbackEvent()
    
    /**
     * Rep completed event
     */
    data class RepCompleted(
        val repNumber: Int,
        val isCorrect: Boolean,
        val errors: List<JointError> = emptyList(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    /**
     * Joint error event (real-time feedback)
     */
    data class JointErrorDetected(
        val error: JointError,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    /**
     * Start position feedback
     */
    data class StartPositionGuide(
        val message: LocalizedText,
        val isInPosition: Boolean,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    /**
     * Target reached event
     */
    data class TargetReached(
        val totalReps: Int,
        val correctReps: Int,
        val accuracy: Float,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    /**
     * Motivational message
     */
    data class MotivationalMessage(
        val message: LocalizedText,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.LOW
    ) : FeedbackEvent()
    
    /**
     * Training started
     */
    data class TrainingStarted(
        val exerciseName: LocalizedText,
        val targetReps: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    /**
     * Training paused
     */
    data class TrainingPaused(
        val currentRep: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    /**
     * Training resumed
     */
    data class TrainingResumed(
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.LOW
    ) : FeedbackEvent()
    
    // ==================== HOLD-specific Events ====================
    
    /**
     * Hold exercise started - user entered hold position for the first time
     */
    data class HoldStarted(
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    /**
     * Grace period started - user left hold position temporarily
     */
    data class HoldGraceStarted(
        val gracePeriodMs: Long,
        val elapsedBeforeGraceMs: Long,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    /**
     * Hold resumed - user returned to hold position within grace period
     */
    data class HoldResumed(
        val elapsedMs: Long,
        val gracePeriodsUsed: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    /**
     * Hold completed successfully - user held for target duration
     */
    data class HoldCompleted(
        val totalMs: Long,
        val targetMs: Long,
        val formQuality: Float,  // 0.0 - 1.0
        val gracePeriodsUsed: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    /**
     * Hold failed - user exceeded grace period
     */
    data class HoldFailed(
        val elapsedBeforeFailMs: Long,
        val targetMs: Long,
        val gracePeriodCount: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    // ==================== Position-based Events ====================
    
    /**
     * Position error detected (severity: ERROR - affects rep correctness)
     */
    data class PositionErrorDetected(
        val error: PositionError,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    /**
     * Position warning detected (severity: WARNING - form feedback only)
     */
    data class PositionWarningDetected(
        val error: PositionError,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    /**
     * Position tip detected (severity: TIP - improvement suggestion)
     */
    data class PositionTipDetected(
        val error: PositionError,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.LOW
    ) : FeedbackEvent()
    
    /**
     * Camera position warning - detected camera doesn't match expected
     */
    data class CameraPositionWarning(
        val warning: com.trainingvalidator.poc.training.engine.CameraPositionWarning,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    // ==================== Visibility-based Events ====================
    
    /**
     * Visibility warning - some required joints not visible
     * Training continues but user is warned
     */
    data class VisibilityWarning(
        val message: LocalizedText,
        val remainingBeforePauseMs: Long,
        val invisibleJoints: List<String>,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    /**
     * Visibility paused - training paused due to joints not visible
     * State is saved for resume
     */
    data class VisibilityPaused(
        val savedRepCount: Int,
        val savedPhase: Phase,
        val message: LocalizedText,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    /**
     * Visibility resume countdown - joints visible again, starting countdown
     */
    data class VisibilityResumeCountdown(
        val resumeFromRep: Int,
        val resumeFromPhase: Phase,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
    ) : FeedbackEvent()
    
    /**
     * Visibility resumed - countdown finished, training resumed
     */
    data class VisibilityResumed(
        val repCount: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
}

/**
 * Feedback priority
 */
enum class FeedbackPriority {
    LOW,        // Phase changes, info
    MEDIUM,     // Rep counts, motivational
    HIGH        // Errors, safety warnings
}

/**
 * FeedbackConfig - Configuration for feedback behavior
 */
data class FeedbackConfig(
    val enableAudio: Boolean = true,
    val enableHaptic: Boolean = true,
    val enableVisual: Boolean = true,
    val language: String = "en",
    val errorCooldownMs: Long = 2000,  // Don't repeat same error within this time
    val motivationalInterval: Int = 5   // Show motivational message every N reps
)
