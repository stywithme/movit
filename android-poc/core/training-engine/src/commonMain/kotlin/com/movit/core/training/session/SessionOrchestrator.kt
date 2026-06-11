package com.movit.core.training.session

import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.policy.TimingPolicy
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.model.TrainingSessionState
import com.movit.core.training.visibility.VisibilityState

/**
 * Pure lifecycle/time orchestration for one exercise run.
 * Frame evaluation stays in legacy [com.trainingvalidator.poc.training.TrainingEngine] until Phase 07.
 */
class SessionOrchestrator(
    timingPolicy: TimingPolicy = TimingPolicy.DEFAULT,
    private val isHoldExercise: Boolean = false,
    targetReps: Int = 12,
    targetDurationMs: Long? = null,
    minRepIntervalMs: Long = timingPolicy.defaultMinRepIntervalMs,
    private val wallClock: () -> Long = { com.movit.core.training.engine.currentTimeMillis() },
    executionClock: ExecutionClock? = null,
) {
    val clock: ExecutionClock = executionClock ?: ExecutionClock(wallClock)
    val pauseController = PauseController.fromTiming(timingPolicy) { clock.nowMs() }
    val safetyGuards = ExecutionSafetyGuards(
        timingPolicy = timingPolicy,
        isHoldExercise = isHoldExercise,
        targetReps = targetReps,
        targetDurationMs = targetDurationMs,
        minRepIntervalMs = minRepIntervalMs,
    )

    val holdTimer: HoldTimer? = targetDurationMs?.let { duration ->
        HoldTimer(
            targetDurationMs = duration,
            gracePeriodMs = timingPolicy.defaultGracePeriodMs,
        )
    }

    var lifecycle: SessionLifecycle = SessionLifecycle.IDLE
        private set

    var isCompleted: Boolean = false
        private set

    var safetyStopTriggered: Boolean = false
        private set

    var currentPhase: Phase = Phase.IDLE
        private set

    var repCount: Int = 0
        private set

    var visibilityState: VisibilityState = VisibilityState.VISIBLE
        private set

    fun start() {
        lifecycle = SessionLifecycle.RUNNING
        isCompleted = false
        safetyStopTriggered = false
        currentPhase = Phase.IDLE
        repCount = 0
        visibilityState = VisibilityState.VISIBLE
        clock.reset()
        pauseController.resetExecution()
        holdTimer?.reset()
    }

    fun pause() {
        clock.pause()
    }

    fun resume() {
        clock.resume()
    }

    fun stop(): Long {
        val durationMs = clock.finalizeDurationMs()
        lifecycle = SessionLifecycle.STOPPED
        clock.resume()
        pauseController.resetExecution()
        return durationMs
    }

    fun onFrameClock(frame: PoseFrame) {
        if (lifecycle != SessionLifecycle.RUNNING || isCompleted) return
        clock.onFrame(frame.timestampMs)
        evaluateSafetyStop()
    }

    fun shouldProcessFrame(): Boolean {
        if (lifecycle != SessionLifecycle.RUNNING || isCompleted) return false
        if (clock.isPaused &&
            !pauseController.isVisibilityPaused &&
            !pauseController.isCountingSuspended
        ) {
            return false
        }
        return true
    }

    fun updatePhase(phase: Phase) {
        currentPhase = phase
    }

    fun updateRepCount(count: Int) {
        repCount = count
        evaluateSafetyStop()
    }

    fun markCompleted() {
        isCompleted = true
    }

    fun updateVisibilityState(state: VisibilityState) {
        visibilityState = state
    }

    fun snapshot(): TrainingSessionState = TrainingSessionState(
        lifecycle = lifecycle,
        isPaused = clock.isPaused,
        isCompleted = isCompleted,
        currentPhase = currentPhase,
        repCount = repCount,
        activeDurationMs = clock.getActiveExecutionDurationMs(),
        visibilityState = visibilityState,
        isCountingSuspended = pauseController.isCountingSuspended,
        isVisibilityPaused = pauseController.isVisibilityPaused,
        visibilityResumeCountdown = pauseController.visibilityResumeCountdown,
        holdState = holdTimer?.state,
        holdElapsedMs = holdTimer?.elapsedMs ?: 0L,
        safetyStopTriggered = safetyStopTriggered,
    )

    private fun evaluateSafetyStop(): Boolean {
        if (safetyStopTriggered || isCompleted) return true
        val activeDurationMs = clock.getActiveExecutionDurationMs()
        if (repCount >= safetyGuards.maxRepsGuard) {
            triggerSafetyStop()
            return true
        }
        if (activeDurationMs >= safetyGuards.maxExecutionDurationGuardMs) {
            triggerSafetyStop()
            return true
        }
        return false
    }

    private fun triggerSafetyStop() {
        if (safetyStopTriggered || isCompleted) return
        safetyStopTriggered = true
        isCompleted = true
    }
}
