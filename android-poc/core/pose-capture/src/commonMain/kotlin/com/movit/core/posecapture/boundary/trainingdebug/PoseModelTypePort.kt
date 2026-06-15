package com.movit.core.posecapture.boundary.trainingdebug

/**
 * D9: official model selector port for Training Debug Lab and pose detectors.
 * UI must call this port instead of writing legacy SharedPreferences directly.
 */
interface PoseModelTypePort {
    fun getSelectedModel(): PoseModelType

    fun setSelectedModel(model: PoseModelType)

    /**
     * Resolves the model asset for MediaPipe initialization, including heavy download fallback.
     */
    fun resolveForInitialization(requested: PoseModelType? = null): ResolvedPoseModel
}
