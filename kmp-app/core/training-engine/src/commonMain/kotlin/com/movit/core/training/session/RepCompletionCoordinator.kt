package com.movit.core.training.session

import com.movit.core.training.bilateral.BilateralController
import com.movit.core.training.engine.PhaseStateMachine
import com.movit.core.training.engine.RepCompletionSignal
import com.movit.core.training.engine.RepCounter

class RepCompletionCoordinator(
    private val stateMachine: PhaseStateMachine,
    private val repCounter: RepCounter,
    private val repCompletionSignal: RepCompletionSignal,
    private val bilateral: BilateralController,
) {
    fun clear() = repCompletionSignal.clear()

    fun onPhaseMachineWantsComplete() = repCompletionSignal.signalFromPhaseMachine()

    fun consumeIfPendingAndHandle() {
        if (!repCompletionSignal.consumeIfPending()) return
        val phaseTimings = stateMachine.getPhaseTimings()
        repCounter.setPhaseTimings(phaseTimings)
        val previousCount = repCounter.count
        repCounter.completeRep()
        stateMachine.clearTimings()
        if (repCounter.count > previousCount) {
            bilateral.onRepCounted(repCounter.count)
        }
    }
}
