package com.movit.core.posecapture

import com.movit.core.training.filter.OneEuroFilter3D
import com.movit.core.training.model.Landmark

/**
 * Shared One-Euro landmark smoothing applied **identically on Android and iOS** before
 * [com.movit.core.training.geometry.PoseFrameAssembler.assemble].
 *
 * This keeps pose-frame inputs — and therefore the training evaluation (joint angles, rep
 * counting, scoring) — matched across platforms. Both platforms feed the same neutral
 * [Landmark] list through the same shared [OneEuroFilter3D] with the same parameters; the
 * Android MediaPipe path ([com.movit.core.posecapture.android.LandmarkSmoother]) shares these
 * defaults so the filters never drift apart.
 */
class PoseLandmarkSmoother(
    private val minCutoff: Float = DEFAULT_MIN_CUTOFF,
    private val beta: Float = DEFAULT_BETA,
) {
    private val filters = arrayOfNulls<OneEuroFilter3D>(NUM_LANDMARKS)
    private val worldFilters = arrayOfNulls<OneEuroFilter3D>(NUM_LANDMARKS)

    /** Smooths normalized (screen-space) landmarks. */
    fun smooth(landmarks: List<Landmark>, timestampMs: Long): List<Landmark> =
        smoothWith(filters, landmarks, timestampMs)

    /** Smooths world (metric-space) landmarks used for 3D joint angles. */
    fun smoothWorld(landmarks: List<Landmark>, timestampMs: Long): List<Landmark> =
        smoothWith(worldFilters, landmarks, timestampMs)

    /** Clears all per-landmark filter state — call on session start / lens switch. */
    fun reset() {
        filters.fill(null)
        worldFilters.fill(null)
    }

    private fun smoothWith(
        bank: Array<OneEuroFilter3D?>,
        landmarks: List<Landmark>,
        timestampMs: Long,
    ): List<Landmark> = landmarks.mapIndexed { index, lm ->
        val filtered = bank.getOrCreate(index).filter(lm.x, lm.y, lm.z, timestampMs)
        Landmark(
            x = filtered.x,
            y = filtered.y,
            z = filtered.z,
            visibility = lm.visibility,
            presence = lm.presence,
        )
    }

    private fun Array<OneEuroFilter3D?>.getOrCreate(index: Int): OneEuroFilter3D {
        if (index >= size) return OneEuroFilter3D(minCutoff, beta)
        return this[index] ?: OneEuroFilter3D(minCutoff, beta).also { this[index] = it }
    }

    companion object {
        const val DEFAULT_MIN_CUTOFF: Float = 1.0f
        const val DEFAULT_BETA: Float = 1.5f
        private const val NUM_LANDMARKS = 33
    }
}
