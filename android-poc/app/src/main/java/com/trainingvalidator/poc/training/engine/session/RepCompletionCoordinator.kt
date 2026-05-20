package com.trainingvalidator.poc.training.engine.session

import android.util.Log
import com.trainingvalidator.poc.training.analytics.MotionRecorder
import com.trainingvalidator.poc.training.engine.PhaseStateMachine
import com.trainingvalidator.poc.training.engine.RepCompletionSignal
import com.trainingvalidator.poc.training.engine.RepCounter
import com.trainingvalidator.poc.training.engine.bilateral.BilateralController
import com.trainingvalidator.poc.training.engine.observability.PipelineTrace

/**
 * Defer and execute rep completion so it runs after joint/position error collection
 * in the same frame, then finalize motion + side effects.
 */
class RepCompletionCoordinator(
    private val tag: String,
    private val stateMachine: PhaseStateMachine,
    private val repCounter: RepCounter,
    private val repCompletionSignal: RepCompletionSignal,
    private val motionRecorder: () -> MotionRecorder?,
    private val bilateral: BilateralController,
    private val pipelineTrace: PipelineTrace
) {

    fun clear() {
        repCompletionSignal.clear()
    }

    fun onPhaseMachineWantsComplete() {
        repCompletionSignal.signalFromPhaseMachine()
    }

    /**
     * Call from the per-frame path after [JointErrorCollection] and position hooks.
     */
    fun consumeIfPendingAndHandle() {
        if (repCompletionSignal.consumeIfPending()) {
            completeRep()
        }
    }

    private fun completeRep() {
        val phaseTimings = stateMachine.getPhaseTimings()
        repCounter.setPhaseTimings(phaseTimings)
        val worstState = repCounter.getCurrentWorstState()
        val score = repCounter.getPendingScore()
        val previousCount = repCounter.count
        repCounter.completeRep()
        stateMachine.clearTimings()
        val repCompleted = repCounter.count > previousCount
        if (!repCompleted) {
            Log.w(tag, "Rep completion ignored by RepCounter (likely min interval guard)")
            return
        }
        pipelineTrace.record("rep complete n=${repCounter.count} score=$score worst=$worstState")
        motionRecorder()?.finalizeRep(
            repNumber = repCounter.count,
            phaseTimings = phaseTimings.mapKeys { it.key.name.lowercase() },
            worstState = worstState,
            score = score,
            side = bilateral.currentSideCode.takeIf { bilateral.isBilateral }
        )
        bilateral.onRepCounted(repCounter.count)
        Log.d(tag, "Rep ${repCounter.count} completed. Correct: ${repCounter.correctCount}/${repCounter.count}")
    }
}
