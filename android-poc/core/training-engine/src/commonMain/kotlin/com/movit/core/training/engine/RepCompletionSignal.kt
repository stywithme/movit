package com.movit.core.training.engine

class RepCompletionSignal {
    private var pending: Boolean = false

    fun signalFromPhaseMachine() {
        pending = true
    }

    fun clear() {
        pending = false
    }

    fun consumeIfPending(): Boolean {
        if (!pending) return false
        pending = false
        return true
    }
}
