package com.trainingvalidator.poc.training.engine

/**
 * Defer rep completion to after joint/position error collection in [com.trainingvalidator.poc.training.TrainingEngine.processFrame].
 * Set from the phase machine callback; consumed once in the per-frame path.
 */
class RepCompletionSignal {
    @Volatile
    private var pending: Boolean = false

    fun signalFromPhaseMachine() {
        pending = true
    }

    fun clear() {
        pending = false
    }

    /**
     * @return true if a pending completion was present and cleared (process [handleRepCompleted]).
     */
    fun consumeIfPending(): Boolean {
        if (!pending) return false
        pending = false
        return true
    }
}
