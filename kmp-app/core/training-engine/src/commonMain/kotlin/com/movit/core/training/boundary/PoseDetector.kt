package com.movit.core.training.boundary

/**
 * Platform pose ML boundary. Phase 07 actuals: Android MediaPipe, iOS Vision.
 */
expect interface PoseDetector {
    fun warmUp(configuration: PoseDetectorConfiguration)

    /** Clears landmark smoothing / tracking state after a lens switch (Legacy parity). */
    fun resetTrackingState()

    fun shutdown()
}
