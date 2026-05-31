package com.trainingvalidator.poc.training.feedback

import com.trainingvalidator.poc.training.engine.RepIncompleteReason
import com.trainingvalidator.poc.training.engine.SceneAxisWarning
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.ZoneType

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
     * Rep completed event
     * 
     * STATE-BASED SCORING:
     * - score: Rep score (0-100) based on worst state
     * - worstState: The worst JointState reached during the rep
     * - isCorrect: Legacy - maps to whether rep was counted
     */
    data class RepCompleted(
        val repNumber: Int,
        val isCorrect: Boolean,
        val errors: List<JointError> = emptyList(),
        val score: Float = 0f,
        val worstState: JointState? = null,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()

    /**
     * Rep attempt did not complete (partial path or outside min/max timing window).
     */
    data class RepIncomplete(
        val reason: RepIncompleteReason,
        val attemptNumber: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.HIGH
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
    
    // ==================== Joint quality (state messages + form errors) ====================
    
    /**
     * Real-time joint quality: informational state lines or [JointError] (WARNING/DANGER, etc.).
     * Replaces the former separate state-message and joint-error event types in the public taxonomy.
     */
    data class JointQuality(
        val content: JointQualityContent,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
    // ==================== Position-based (single type; severity on [PositionError]) ====================
    
    /**
     * One position / alignment check. Use [PositionError.severity] for ERROR vs WARNING vs TIP.
     */
    data class PositionCheckFeedback(
        val check: PositionError,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = when (check.severity) {
            CheckSeverity.ERROR -> FeedbackPriority.HIGH
            CheckSeverity.WARNING -> FeedbackPriority.MEDIUM
            CheckSeverity.TIP -> FeedbackPriority.LOW
        }
    ) : FeedbackEvent()
    
    /**
     * Scene axis warnings - one or more axes (posture/direction/region) mismatch.
     */
    data class SceneWarnings(
        val warnings: List<SceneAxisWarning>,
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
     * Visibility resumed - countdown finished, training resumed
     */
    data class VisibilityResumed(
        val repCount: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val priority: FeedbackPriority = FeedbackPriority.MEDIUM
    ) : FeedbackEvent()
    
}

/**
 * Unified payload for [FeedbackEvent.JointQuality] (per-frame guidance vs errors).
 */
sealed class JointQualityContent {
    data class StateMessage(
        val jointCode: String,
        val state: JointState,
        val zone: ZoneType,
        val message: LocalizedText
    ) : JointQualityContent()

    data class Error(
        val error: JointError
    ) : JointQualityContent()
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
