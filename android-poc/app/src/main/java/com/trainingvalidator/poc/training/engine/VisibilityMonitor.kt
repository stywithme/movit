package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.movit.core.training.visibility.VisibilityCheckResult as KmpVisibilityCheckResult
import com.movit.core.training.visibility.VisibilityJointConfig
import com.movit.core.training.visibility.VisibilityJointRole
import com.movit.core.training.visibility.VisibilityMonitor as KmpVisibilityMonitor
import com.movit.core.training.visibility.VisibilityState as KmpVisibilityState
import com.movit.core.training.visibility.VisibilityStats as KmpVisibilityStats
import com.movit.core.training.visibility.VisibilityTrackingMode
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
 * VisibilityMonitor - Tracks visibility of required joints and manages pause/resume.
 * Delegates state machine to KMP [com.movit.core.training.visibility.VisibilityMonitor].
 */
class VisibilityMonitor(
    private val trackedJoints: List<TrackedJoint>,
    private val minVisibility: Float = 0.5f,
    private val graceDurationMs: Long = 500,
    private val warningDurationMs: Long = 1500,
    private val pauseAfterMs: Long = 3000,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "VisibilityMonitor"
    }

    private val core = KmpVisibilityMonitor(
        visibilityTrackedJoints = trackedJoints.map { it.toKmpVisibilityConfig() },
        minVisibility = minVisibility,
        graceDurationMs = graceDurationMs,
        warningDurationMs = warningDurationMs,
        pauseAfterMs = pauseAfterMs,
        timeProvider = timeProvider,
    )

    private val _state = MutableStateFlow(KmpVisibilityState.VISIBLE.toApp())
    val state: StateFlow<VisibilityState> = _state.asStateFlow()

    init {
        syncStateFromCore()
    }

    private fun syncStateFromCore() {
        _state.value = core.state.toApp()
    }

    fun checkVisibility(
        landmarks: List<SmoothedLandmark>,
        currentRepCount: Int,
        currentPhase: Phase,
        isFrontCamera: Boolean = false,
    ): VisibilityCheckResult {
        if (landmarks.size < 33) {
            val result = core.checkVisibility(
                jointVisibilities = emptyMap(),
                currentRepCount = currentRepCount,
                currentPhase = currentPhase.toKmp(),
            )
            return mapResult(result)
        }

        val jointVisibilities = buildJointVisibilityMap(landmarks, isFrontCamera)
        val result = core.checkVisibility(
            jointVisibilities = jointVisibilities,
            currentRepCount = currentRepCount,
            currentPhase = currentPhase.toKmp(),
        )
        logTransition(result)
        return mapResult(result)
    }

    private fun buildJointVisibilityMap(
        landmarks: List<SmoothedLandmark>,
        isFrontCamera: Boolean,
    ): Map<String, Float> {
        val codes = mutableSetOf<String>()
        for (joint in trackedJoints) {
            codes.add(joint.joint)
            joint.pairedWith?.let { codes.add(it) }
        }
        return codes.associateWith { code ->
            minVisibilityForJoint(code, landmarks, isFrontCamera)
        }
    }

    private fun minVisibilityForJoint(
        jointCode: String,
        landmarks: List<SmoothedLandmark>,
        isFrontCamera: Boolean,
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

    private fun logTransition(result: KmpVisibilityCheckResult) {
        when (result) {
            is KmpVisibilityCheckResult.ShowWarning ->
                Log.d(TAG, "Warning - joints invisible: ${result.invisibleJoints}")
            is KmpVisibilityCheckResult.PauseTraining ->
                Log.d(TAG, "Pausing training - saved rep ${result.savedRepCount}")
            is KmpVisibilityCheckResult.StartResumeCountdown ->
                Log.d(TAG, "Joints visible after pause - starting resume countdown")
            KmpVisibilityCheckResult.ContinueTraining -> {
                if (core.state == KmpVisibilityState.VISIBLE && _state.value == VisibilityState.WARNING) {
                    Log.d(TAG, "Joints visible again - cancelling warning")
                }
            }
            else -> Unit
        }
        syncStateFromCore()
    }

    private fun mapResult(result: KmpVisibilityCheckResult): VisibilityCheckResult =
        when (result) {
            KmpVisibilityCheckResult.ContinueTraining -> VisibilityCheckResult.ContinueTraining
            KmpVisibilityCheckResult.ContinueCountdown -> VisibilityCheckResult.ContinueCountdown
            is KmpVisibilityCheckResult.ShowWarning -> VisibilityCheckResult.ShowWarning(
                message = createWarningMessage(result.invisibleJoints),
                remainingBeforePause = result.remainingBeforePause,
                invisibleJoints = result.invisibleJoints,
            )
            is KmpVisibilityCheckResult.PauseTraining -> VisibilityCheckResult.PauseTraining(
                savedRepCount = result.savedRepCount,
                savedPhase = result.savedPhase.toApp(),
                message = createPauseMessage(result.invisibleJoints),
            )
            is KmpVisibilityCheckResult.StartResumeCountdown ->
                VisibilityCheckResult.StartResumeCountdown(
                    resumeFromRep = result.resumeFromRep,
                    resumeFromPhase = result.resumeFromPhase.toApp(),
                )
        }

    private fun createWarningMessage(invisibleJoints: List<String>): LocalizedText {
        val jointNamesEn = invisibleJoints.joinToString(", ") { formatJointName(it, "en") }
        val jointNamesAr = invisibleJoints.joinToString("، ") { formatJointName(it, "ar") }
        val t = SystemMessageRegistry.get(
            "visibility_joints_not_visible",
            "⚠️ {joints} غير مرئية",
            "⚠️ {joints} not visible",
        )
        return LocalizedText(
            ar = t.ar.replace("{joints}", jointNamesAr),
            en = t.en.replace("{joints}", jointNamesEn),
            audioAr = t.audioAr,
            audioEn = t.audioEn,
        )
    }

    private fun createPauseMessage(invisibleJoints: List<String>): LocalizedText {
        @Suppress("UNUSED_PARAMETER")
        val unused = invisibleJoints
        return SystemMessageRegistry.get(
            "visibility_pause_full_body",
            "⏸️ تأكد من ظهور جسمك بالكامل في الإطار",
            "⏸️ Make sure your full body is visible in frame",
        )
    }

    private fun formatJointName(joint: String, language: String): String {
        val key = "visibility_joint_$joint"
        val nameMap = mapOf(
            "left_elbow" to Pair("الكوع الأيسر", "Left Elbow"),
            "right_elbow" to Pair("الكوع الأيمن", "Right Elbow"),
            "left_shoulder" to Pair("الكتف الأيسر", "Left Shoulder"),
            "right_shoulder" to Pair("الكتف الأيمن", "Right Shoulder"),
            "left_hip" to Pair("الورك الأيسر", "Left Hip"),
            "right_hip" to Pair("الورك الأيمن", "Right Hip"),
            "left_shoulder_cross" to Pair("الكتف الأيسر (تقاطع)", "Left Shoulder Cross"),
            "right_shoulder_cross" to Pair("الكتف الأيمن (تقاطع)", "Right Shoulder Cross"),
            "left_hip_cross" to Pair("الورك الأيسر (تقاطع)", "Left Hip Cross"),
            "right_hip_cross" to Pair("الورك الأيمن (تقاطع)", "Right Hip Cross"),
            "left_knee" to Pair("الركبة اليسرى", "Left Knee"),
            "right_knee" to Pair("الركبة اليمنى", "Right Knee"),
            "left_wrist" to Pair("المعصم الأيسر", "Left Wrist"),
            "right_wrist" to Pair("المعصم الأيمن", "Right Wrist"),
            "left_ankle" to Pair("الكاحل الأيسر", "Left Ankle"),
            "right_ankle" to Pair("الكاحل الأيمن", "Right Ankle"),
        )
        val names = nameMap[joint] ?: Pair(joint, joint)
        val lt = SystemMessageRegistry.get(key, names.first, names.second)
        return lt.get(language)
    }

    fun onResumeCountdownComplete() {
        core.onResumeCountdownComplete()
        syncStateFromCore()
        Log.d(TAG, "Resume countdown complete - back to VISIBLE")
    }

    fun isPausedOrResuming(): Boolean = core.isPausedOrResuming()

    fun reset() {
        core.reset()
        syncStateFromCore()
        Log.d(TAG, "VisibilityMonitor reset")
    }

    fun getStats(): VisibilityStats = core.getStats().toApp()

    fun resetStats() = core.resetStats()
}

enum class VisibilityState {
    VISIBLE,
    WARNING,
    PAUSED,
    RESUMING,
}

sealed class VisibilityCheckResult {
    data object ContinueTraining : VisibilityCheckResult()

    data object ContinueCountdown : VisibilityCheckResult()

    data class ShowWarning(
        val message: LocalizedText,
        val remainingBeforePause: Long,
        val invisibleJoints: List<String>,
    ) : VisibilityCheckResult()

    data class PauseTraining(
        val savedRepCount: Int,
        val savedPhase: Phase,
        val message: LocalizedText,
    ) : VisibilityCheckResult()

    data class StartResumeCountdown(
        val resumeFromRep: Int,
        val resumeFromPhase: Phase,
    ) : VisibilityCheckResult()
}

data class JointVisibility(
    val jointName: String,
    val visibility: Float,
    val isVisible: Boolean,
)

data class VisibilityStats(
    val totalPauseCount: Int,
    val totalWarningCount: Int,
)

private fun TrackedJoint.toKmpVisibilityConfig(): VisibilityJointConfig = VisibilityJointConfig(
    joint = joint,
    role = when (role) {
        JointRole.PRIMARY -> VisibilityJointRole.PRIMARY
        JointRole.SECONDARY -> VisibilityJointRole.SECONDARY
        else -> VisibilityJointRole.SECONDARY
    },
    trackingMode = when (trackingMode) {
        TrackingMode.ANY_SIDE -> VisibilityTrackingMode.ANY_SIDE
        else -> VisibilityTrackingMode.BOTH_SIDES
    },
    pairedWith = pairedWith,
)

private fun KmpVisibilityState.toApp(): VisibilityState = when (this) {
    KmpVisibilityState.VISIBLE -> VisibilityState.VISIBLE
    KmpVisibilityState.WARNING -> VisibilityState.WARNING
    KmpVisibilityState.PAUSED -> VisibilityState.PAUSED
    KmpVisibilityState.RESUMING -> VisibilityState.RESUMING
}

private fun KmpVisibilityStats.toApp(): VisibilityStats = VisibilityStats(
    totalPauseCount = totalPauseCount,
    totalWarningCount = totalWarningCount,
)
