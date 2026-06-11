package com.movit.core.posecapture.android

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.movit.core.training.filter.OneEuroFilter3D
import com.movit.core.training.model.Landmark

/** One-Euro smoothing for MediaPipe landmarks (Android actual). */
class LandmarkSmoother(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 1.5f,
) {
    private val filters = arrayOfNulls<OneEuroFilter3D>(NUM_LANDMARKS)

    fun smooth(landmarks: List<NormalizedLandmark>, timestampMs: Long): List<Landmark> =
        landmarks.mapIndexed { index, lm ->
            val filter = filters.getOrPut(index) { OneEuroFilter3D(minCutoff, beta) }
            val smoothed = filter.filter(lm.x(), lm.y(), lm.z(), timestampMs)
            Landmark(
                x = smoothed.x,
                y = smoothed.y,
                z = smoothed.z,
                visibility = lm.visibility().orElse(0f),
                presence = lm.presence().orElse(0f),
            )
        }

    fun reset() {
        filters.fill(null)
    }

    private fun Array<OneEuroFilter3D?>.getOrPut(index: Int, factory: () -> OneEuroFilter3D): OneEuroFilter3D {
        if (index >= NUM_LANDMARKS) return factory()
        val existing = this[index]
        if (existing != null) return existing
        val created = factory()
        this[index] = created
        return created
    }

    companion object {
        private const val NUM_LANDMARKS = 33
    }
}
