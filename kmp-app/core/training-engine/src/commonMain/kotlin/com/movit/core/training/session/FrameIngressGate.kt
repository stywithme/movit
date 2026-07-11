package com.movit.core.training.session

import kotlinx.atomicfu.atomic

/**
 * Safety net — the contract is a single worker thread enforced at the call site
 * ([TrainingSessionViewModel] pose-frame worker). This gate still ensures at most one
 * [MovitTrainingEngine.processFrame] runs at a time if that contract is ever violated.
 */
class FrameIngressGate {
    private val processing = atomic(false)
    private val dropped = atomic(0)

    val droppedFrameCount: Int
        get() = dropped.value

    /** @return `true` when the frame may enter the pipeline. */
    fun tryAcquire(): Boolean {
        if (!processing.compareAndSet(expect = false, update = true)) {
            dropped.incrementAndGet()
            return false
        }
        return true
    }

    fun release() {
        processing.value = false
    }

    fun reset() {
        processing.value = false
        dropped.value = 0
    }
}
