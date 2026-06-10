package com.movit.core.training.session

import com.movit.core.training.engine.Phase

sealed class PauseControllerEvent {
    data class VisibilityWarning(
        val invisibleJoints: List<String>,
        val remainingBeforePauseMs: Long,
    ) : PauseControllerEvent()

    data class VisibilityPaused(
        val savedRepCount: Int,
        val savedPhase: Phase,
        val invisibleJoints: List<String>,
    ) : PauseControllerEvent()

    data class VisibilityResumed(
        val repCount: Int,
    ) : PauseControllerEvent()
}
