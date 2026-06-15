package com.movit.core.posecapture.android

import com.google.mediapipe.tasks.components.containers.Landmark as MpWorldLandmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.movit.core.training.model.Landmark

internal object MediaPipeLandmarkMapper {
    fun mapNormalizedRaw(landmarks: List<NormalizedLandmark>): List<Landmark> =
        landmarks.map(::mapNormalized)

    fun mapWorldRaw(world: List<MpWorldLandmark>): List<Landmark> =
        world.map(::mapWorld)

    private fun mapNormalized(lm: NormalizedLandmark): Landmark =
        Landmark(
            x = lm.x(),
            y = lm.y(),
            z = lm.z(),
            visibility = lm.visibility().orElse(0f),
            presence = lm.presence().orElse(0f),
        )

    private fun mapWorld(lm: MpWorldLandmark): Landmark =
        Landmark(
            x = lm.x(),
            y = lm.y(),
            z = lm.z(),
            visibility = lm.visibility().orElse(1f),
            presence = lm.presence().orElse(1f),
        )
}
