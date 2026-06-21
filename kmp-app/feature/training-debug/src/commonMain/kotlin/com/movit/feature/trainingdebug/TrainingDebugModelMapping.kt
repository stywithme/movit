package com.movit.feature.trainingdebug

import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType

internal fun DebugPoseModelType.toPoseModelType(): PoseModelType = when (this) {
    DebugPoseModelType.FULL -> PoseModelType.FULL
    DebugPoseModelType.HEAVY -> PoseModelType.HEAVY
}
