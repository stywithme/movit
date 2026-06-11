package com.movit.core.training.session

/**
 * I-5: single back-pressure point on the engine consumption side.
 * Upstream layers (CameraX / pose detector) may still drop; this gate ensures
 * at most one [MovitTrainingEngine.processFrame] runs at a time.
 */
class FrameIngressGate {
    private var processing: Boolean = false

    var droppedFrameCount: Int = 0
        private set

    /** @return `true` when the frame may enter the pipeline. */
    fun tryAcquire(): Boolean {
        if (processing) {
            droppedFrameCount++
            return false
        }
        processing = true
        return true
    }

    fun release() {
        processing = false
    }

    fun reset() {
        processing = false
        droppedFrameCount = 0
    }
}
