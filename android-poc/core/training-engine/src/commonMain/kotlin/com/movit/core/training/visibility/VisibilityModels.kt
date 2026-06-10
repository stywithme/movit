package com.movit.core.training.visibility

import com.movit.core.training.engine.Phase

enum class VisibilityJointRole {
    PRIMARY,
    SECONDARY,
}

enum class VisibilityTrackingMode {
    BOTH_SIDES,
    ANY_SIDE,
}

data class VisibilityJointConfig(
    val joint: String,
    val role: VisibilityJointRole,
    val trackingMode: VisibilityTrackingMode = VisibilityTrackingMode.BOTH_SIDES,
    val pairedWith: String? = null,
)

enum class VisibilityState {
    VISIBLE,
    WARNING,
    PAUSED,
    RESUMING,
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

sealed class VisibilityCheckResult {
    data object ContinueTraining : VisibilityCheckResult()

    data object ContinueCountdown : VisibilityCheckResult()

    data class ShowWarning(
        val invisibleJoints: List<String>,
        val remainingBeforePause: Long,
    ) : VisibilityCheckResult()

    data class PauseTraining(
        val savedRepCount: Int,
        val savedPhase: Phase,
        val invisibleJoints: List<String>,
    ) : VisibilityCheckResult()

    data class StartResumeCountdown(
        val resumeFromRep: Int,
        val resumeFromPhase: Phase,
    ) : VisibilityCheckResult()
}
