package com.movit.core.training.model

import com.movit.core.training.engine.Phase
import com.movit.core.training.session.HoldState
import com.movit.core.training.session.SessionLifecycle
import com.movit.core.training.visibility.VisibilityState

/**
 * Immutable snapshot of one exercise run for UI/supervisor layers.
 * Mutable orchestration lives in [com.movit.core.training.session.SessionOrchestrator].
 */
data class TrainingSessionState(
    val lifecycle: SessionLifecycle = SessionLifecycle.IDLE,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val currentPhase: Phase = Phase.IDLE,
    val repCount: Int = 0,
    val activeDurationMs: Long = 0L,
    val visibilityState: VisibilityState = VisibilityState.VISIBLE,
    val isCountingSuspended: Boolean = false,
    val isVisibilityPaused: Boolean = false,
    val visibilityResumeCountdown: Int? = null,
    val holdState: HoldState? = null,
    val holdElapsedMs: Long = 0L,
    val safetyStopTriggered: Boolean = false,
) {
    val isRunning: Boolean get() = lifecycle == SessionLifecycle.RUNNING
}
