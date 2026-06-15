package com.movit.core.posecapture.boundary.trainingdebug

/**
 * Platform debug input source (camera / video / image).
 * Android implementations live in `feature:training-debug` or `androidMain` adapters.
 */
interface TrainingDebugPoseSource {
    val mode: TrainingDebugInputMode

    suspend fun start(config: TrainingDebugSourceConfig)

    suspend fun stop()

    suspend fun resetTracking(reason: String)
}
