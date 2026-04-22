package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.training.feedback.SystemMessageRegistry
import com.trainingvalidator.poc.training.models.JointRole
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.TrackedJoint
import com.trainingvalidator.poc.training.models.TrackingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VisibilityMonitor - Tracks visibility of required joints and manages pause/resume
 * 
 * This component monitors the visibility of joints required for the current exercise.
 * When joints become invisible (user moves out of frame, occlusion, etc.), it:
 * 
 * 1. GRACE PERIOD (0 - graceDurationMs): Ignores brief invisibility
 * 2. WARNING (graceDurationMs - pauseAfterMs): Shows warning, continues training
 * 3. PAUSED (> pauseAfterMs): Pauses training, saves state
 * 4. RESUMING: When visible again, triggers countdown before resuming
 * 
 * This provides a smart, non-intrusive experience that:
 * - Ignores momentary visibility glitches
 * - Warns user before pausing
 * - Preserves rep count and state
 * - Provides smooth resume with countdown
 */
class VisibilityMonitor(
    /** Primary + secondary joints from the active pose variant (visibility rules use roles + tracking mode). */
    private val visibilityTrackedJoints: List<TrackedJoint>,
    private val minVisibility: Float = 0.5f,
    private val graceDurationMs: Long = 500,      // 0.5 seconds - ignore brief glitches
    private val warningDurationMs: Long = 1500,   // 1.5 seconds before showing warning
    private val pauseAfterMs: Long = 3000,        // 3 seconds total before pause
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "VisibilityMonitor"
    }

    private data class LenientPair(val jointA: String, val jointB: String)

    private val primarySecondary: List<TrackedJoint> =
        visibilityTrackedJoints.filter { it.role == JointRole.PRIMARY || it.role == JointRole.SECONDARY }

    private val lenientPairs: List<LenientPair>
    private val strictJointCodes: Set<String>

    init {
        val strict = mutableSetOf<String>()
        val lenient = mutableListOf<LenientPair>()
        val seenLenientKeys = mutableSetOf<Pair<String, String>>()
        for (j in primarySecondary) {
            val p = j.pairedWith
            if (p != null) {
                val partner = primarySecondary.find { it.joint == p }
                val bothAnySide = j.trackingMode == TrackingMode.ANY_SIDE &&
                    partner != null && partner.trackingMode == TrackingMode.ANY_SIDE
                if (bothAnySide) {
                    val a = minOf(j.joint, p)
                    val b = maxOf(j.joint, p)
                    val key = a to b
                    if (seenLenientKeys.add(key)) {
                        lenient.add(LenientPair(a, b))
                    }
                    continue
                }
            }
            strict.add(j.joint)
        }
        strictJointCodes = strict
        lenientPairs = lenient
    }
    
    // Current visibility state
    private val _state = MutableStateFlow(VisibilityState.VISIBLE)
    val state: StateFlow<VisibilityState> = _state.asStateFlow()
    
    // Tracking variables
    private var invisibleStartTime: Long = 0L
    private var lastVisibleRepCount: Int = 0
    private var lastPhase: Phase = Phase.IDLE
    
    // Statistics
    private var totalPauseCount: Int = 0
    private var totalWarningCount: Int = 0
    
    /**
     * Check visibility of required joints
     * Should be called every frame during training
     * 
     * @param landmarks Current frame landmarks (NOT mirrored - raw from MediaPipe)
     * @param currentRepCount Current rep count (to save on pause)
     * @param currentPhase Current phase (to save on pause)
     * @param isFrontCamera Whether using front camera (for landmark index mirroring)
     * @return Result indicating what action to take
     */
    fun checkVisibility(
        landmarks: List<SmoothedLandmark>,
        currentRepCount: Int,
        currentPhase: Phase,
        isFrontCamera: Boolean = false
    ): VisibilityCheckResult {
        if (landmarks.size < 33) {
            return handleInvisible(timeProvider(), currentRepCount, currentPhase)
        }
        
        // Check if all required joints are visible with sufficient confidence
        // For front camera, we need to check mirrored indices since the image is mirrored
        val visibilityDetails = checkJointVisibility(landmarks, isFrontCamera)
        val allVisible = visibilityDetails.all { it.isVisible }
        
        val now = timeProvider()
        
        return if (allVisible) {
            handleVisible(currentRepCount, currentPhase)
        } else {
            handleInvisible(now, currentRepCount, currentPhase, visibilityDetails)
        }
    }
    
    /**
     * Minimum visibility across the three angle landmarks for [jointCode], using the same
     * front-camera mirroring rules as the rest of the monitor.
     */
    private fun minVisibilityForJoint(
        jointCode: String,
        landmarks: List<SmoothedLandmark>,
        isFrontCamera: Boolean
    ): Float {
        val indices = JointLandmarkMapping.getLandmarksForAngle(jointCode)
        if (indices.isEmpty()) return 0f
        var minV = 1f
        for (rawIndex in indices) {
            val effectiveIndex = if (isFrontCamera) BodyLandmarks.getMirroredIndex(rawIndex) else rawIndex
            val landmark = landmarks.getOrNull(effectiveIndex)
            val v = landmark?.visibility ?: 0f
            minV = kotlin.math.min(minV, v)
        }
        return minV
    }

    /**
     * Build per-joint visibility rows for messages, and mark lenient pairs as visible
     * when **at least one** side has all three landmarks ≥ [minVisibility].
     */
    private fun checkJointVisibility(
        landmarks: List<SmoothedLandmark>,
        isFrontCamera: Boolean
    ): List<JointVisibility> {
        val details = mutableListOf<JointVisibility>()
        for (code in strictJointCodes) {
            val v = minVisibilityForJoint(code, landmarks, isFrontCamera)
            details.add(
                JointVisibility(
                    jointName = code,
                    visibility = v,
                    isVisible = v >= minVisibility
                )
            )
        }
        for (pair in lenientPairs) {
            val vA = minVisibilityForJoint(pair.jointA, landmarks, isFrontCamera)
            val vB = minVisibilityForJoint(pair.jointB, landmarks, isFrontCamera)
            val okA = vA >= minVisibility
            val okB = vB >= minVisibility
            val pairOk = okA || okB
            details.add(JointVisibility(pair.jointA, vA, pairOk))
            details.add(JointVisibility(pair.jointB, vB, pairOk))
        }
        return details
    }
    
    /**
     * Handle visible state - joints are visible
     */
    private fun handleVisible(repCount: Int, phase: Phase): VisibilityCheckResult {
        return when (_state.value) {
            VisibilityState.WARNING -> {
                // Was in warning, joints visible again → back to normal
                Log.d(TAG, "Joints visible again - cancelling warning")
                _state.value = VisibilityState.VISIBLE
                invisibleStartTime = 0L
                VisibilityCheckResult.ContinueTraining
            }
            
            VisibilityState.PAUSED -> {
                // Was paused, joints visible → start resume countdown
                Log.d(TAG, "Joints visible after pause - starting resume countdown")
                _state.value = VisibilityState.RESUMING
                VisibilityCheckResult.StartResumeCountdown(
                    resumeFromRep = lastVisibleRepCount,
                    resumeFromPhase = lastPhase
                )
            }
            
            VisibilityState.RESUMING -> {
                // In countdown, check if still visible
                VisibilityCheckResult.ContinueCountdown
            }
            
            VisibilityState.VISIBLE -> {
                // Normal visible state - update saved values
                lastVisibleRepCount = repCount
                lastPhase = phase
                VisibilityCheckResult.ContinueTraining
            }
        }
    }
    
    /**
     * Handle invisible state - some joints not visible
     */
    private fun handleInvisible(
        now: Long,
        repCount: Int,
        phase: Phase,
        visibilityDetails: List<JointVisibility> = emptyList()
    ): VisibilityCheckResult {
        // If this is the start of invisibility, record the time and last good state
        if (invisibleStartTime == 0L) {
            invisibleStartTime = now
            lastVisibleRepCount = repCount
            lastPhase = phase
            Log.d(TAG, "Joints became invisible - starting timer")
        }
        
        val invisibleDuration = now - invisibleStartTime
        
        // Get invisible joints for message
        val invisibleJoints = visibilityDetails
            .filter { !it.isVisible }
            .map { it.jointName }
        
        return when {
            // PAUSE: Long enough invisibility → pause training
            invisibleDuration >= pauseAfterMs -> {
                if (_state.value != VisibilityState.PAUSED) {
                    _state.value = VisibilityState.PAUSED
                    totalPauseCount++
                    Log.d(TAG, "Pausing training - joints invisible for ${invisibleDuration}ms")
                }
                
                VisibilityCheckResult.PauseTraining(
                    savedRepCount = lastVisibleRepCount,
                    savedPhase = lastPhase,
                    message = createPauseMessage(invisibleJoints)
                )
            }
            
            // WARNING: Show warning but continue
            invisibleDuration >= warningDurationMs -> {
                if (_state.value != VisibilityState.WARNING) {
                    _state.value = VisibilityState.WARNING
                    totalWarningCount++
                    Log.d(TAG, "Warning - joints invisible for ${invisibleDuration}ms")
                }
                
                val remainingBeforePause = pauseAfterMs - invisibleDuration
                
                VisibilityCheckResult.ShowWarning(
                    message = createWarningMessage(invisibleJoints),
                    remainingBeforePause = remainingBeforePause,
                    invisibleJoints = invisibleJoints
                )
            }
            
            // GRACE: Brief invisibility within grace period → ignore
            invisibleDuration < graceDurationMs -> {
                VisibilityCheckResult.ContinueTraining
            }
            
            // Between grace and warning - continue but track
            else -> {
                VisibilityCheckResult.ContinueTraining
            }
        }
    }
    
    /**
     * Create warning message based on invisible joints
     */
    private fun createWarningMessage(invisibleJoints: List<String>): LocalizedText {
        val jointNamesEn = invisibleJoints.joinToString(", ") { formatJointName(it, "en") }
        val jointNamesAr = invisibleJoints.joinToString("، ") { formatJointName(it, "ar") }
        val t = SystemMessageRegistry.get(
            "visibility_joints_not_visible",
            "⚠️ {joints} غير مرئية",
            "⚠️ {joints} not visible"
        )
        return LocalizedText(
            ar = t.ar.replace("{joints}", jointNamesAr),
            en = t.en.replace("{joints}", jointNamesEn),
            audioAr = t.audioAr,
            audioEn = t.audioEn
        )
    }
    
    /**
     * Create pause message
     */
    @Suppress("UNUSED_PARAMETER")
    private fun createPauseMessage(invisibleJoints: List<String>): LocalizedText {
        return SystemMessageRegistry.get(
            "visibility_pause_full_body",
            "⏸️ تأكد من ظهور جسمك بالكامل في الإطار",
            "⏸️ Make sure your full body is visible in frame"
        )
    }
    
    /**
     * Format joint name for display
     */
    private fun formatJointName(joint: String, language: String): String {
        val key = "visibility_joint_${joint}"
        val nameMap = mapOf(
            "left_elbow" to Pair("الكوع الأيسر", "Left Elbow"),
            "right_elbow" to Pair("الكوع الأيمن", "Right Elbow"),
            "left_shoulder" to Pair("الكتف الأيسر", "Left Shoulder"),
            "right_shoulder" to Pair("الكتف الأيمن", "Right Shoulder"),
            "left_hip" to Pair("الورك الأيسر", "Left Hip"),
            "right_hip" to Pair("الورك الأيمن", "Right Hip"),
            "left_knee" to Pair("الركبة اليسرى", "Left Knee"),
            "right_knee" to Pair("الركبة اليمنى", "Right Knee"),
            "left_wrist" to Pair("المعصم الأيسر", "Left Wrist"),
            "right_wrist" to Pair("المعصم الأيمن", "Right Wrist"),
            "left_ankle" to Pair("الكاحل الأيسر", "Left Ankle"),
            "right_ankle" to Pair("الكاحل الأيمن", "Right Ankle")
        )
        val names = nameMap[joint] ?: Pair(joint, joint)
        val lt = SystemMessageRegistry.get(key, names.first, names.second)
        return lt.get(language)
    }
    
    /**
     * Called when resume countdown finishes
     * Resets visibility monitor to normal state
     */
    fun onResumeCountdownComplete() {
        _state.value = VisibilityState.VISIBLE
        invisibleStartTime = 0L
        Log.d(TAG, "Resume countdown complete - back to VISIBLE")
    }
    
    /**
     * Check if currently in a paused state (PAUSED or RESUMING)
     */
    fun isPausedOrResuming(): Boolean {
        return _state.value == VisibilityState.PAUSED || _state.value == VisibilityState.RESUMING
    }
    
    /**
     * Force reset to visible state (e.g., when training stops)
     */
    fun reset() {
        _state.value = VisibilityState.VISIBLE
        invisibleStartTime = 0L
        lastVisibleRepCount = 0
        lastPhase = Phase.IDLE
        Log.d(TAG, "VisibilityMonitor reset")
    }
    
    /**
     * Get statistics about visibility events
     */
    fun getStats(): VisibilityStats {
        return VisibilityStats(
            totalPauseCount = totalPauseCount,
            totalWarningCount = totalWarningCount
        )
    }
    
    /**
     * Reset statistics (e.g., at start of new session)
     */
    fun resetStats() {
        totalPauseCount = 0
        totalWarningCount = 0
    }
}

// ==================== State Enum ====================

/**
 * Visibility states for the monitor
 */
enum class VisibilityState {
    VISIBLE,    // All required joints visible - normal training
    WARNING,    // Some joints not visible - showing warning, still training
    PAUSED,     // Training paused due to visibility - waiting for joints
    RESUMING    // Joints visible again - countdown to resume
}

// ==================== Result Types ====================

/**
 * Result of visibility check - tells TrainingEngine what to do
 */
sealed class VisibilityCheckResult {
    /**
     * Continue training normally
     */
    object ContinueTraining : VisibilityCheckResult()
    
    /**
     * Continue resume countdown (don't interrupt)
     */
    object ContinueCountdown : VisibilityCheckResult()
    
    /**
     * Show warning to user but continue training
     */
    data class ShowWarning(
        val message: LocalizedText,
        val remainingBeforePause: Long,
        val invisibleJoints: List<String>
    ) : VisibilityCheckResult()
    
    /**
     * Pause training and save state
     */
    data class PauseTraining(
        val savedRepCount: Int,
        val savedPhase: Phase,
        val message: LocalizedText
    ) : VisibilityCheckResult()
    
    /**
     * Start resume countdown (3-2-1)
     */
    data class StartResumeCountdown(
        val resumeFromRep: Int,
        val resumeFromPhase: Phase
    ) : VisibilityCheckResult()
}

// ==================== Helper Data Classes ====================

/**
 * Visibility info for a single joint
 */
data class JointVisibility(
    val jointName: String,
    val visibility: Float,
    val isVisible: Boolean
)

/**
 * Statistics about visibility events
 */
data class VisibilityStats(
    val totalPauseCount: Int,
    val totalWarningCount: Int
)
