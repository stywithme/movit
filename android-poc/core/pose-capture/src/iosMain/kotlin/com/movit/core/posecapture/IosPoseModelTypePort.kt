package com.movit.core.posecapture

import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelTypePort
import com.movit.core.posecapture.boundary.trainingdebug.ResolvedPoseModel

/** iOS stub until MediaPipe debug detectors are wired. */
class IosPoseModelTypePort : PoseModelTypePort {
    private var selected: PoseModelType = PoseModelType.FULL

    override fun getSelectedModel(): PoseModelType = selected

    override fun setSelectedModel(model: PoseModelType) {
        selected = model
    }

    override fun resolveForInitialization(requested: PoseModelType?): ResolvedPoseModel {
        val type = requested ?: selected
        return if (type == PoseModelType.HEAVY) {
            ResolvedPoseModel(
                requestedType = PoseModelType.HEAVY,
                resolvedAssetLabel = ResolvedPoseModel.HEAVY_ASSET,
                displayLabel = "Heavy (iOS pending)",
                usesHeavyFallback = true,
                scheduleHeavyUpgrade = false,
            )
        } else {
            ResolvedPoseModel.fullBundled()
        }
    }
}
