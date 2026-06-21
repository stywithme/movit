package com.movit.core.posecapture

import com.movit.core.training.model.Landmark

/**
 * Flat float encoding for Swift ↔ Kotlin pose bridge (33 BlazePose landmarks × 5 floats).
 * Order per landmark: x, y, z, visibility, presence.
 */
object PoseLandmarkFlatCodec {
    const val LANDMARK_COUNT = 33
    const val FLOATS_PER_LANDMARK = 5
    const val FLAT_SIZE = LANDMARK_COUNT * FLOATS_PER_LANDMARK

    fun encode(landmarks: List<Landmark>): FloatArray {
        require(landmarks.size == LANDMARK_COUNT) {
            "Expected $LANDMARK_COUNT landmarks, got ${landmarks.size}"
        }
        return FloatArray(FLAT_SIZE) { index ->
            val landmarkIndex = index / FLOATS_PER_LANDMARK
            val component = index % FLOATS_PER_LANDMARK
            val landmark = landmarks[landmarkIndex]
            when (component) {
                0 -> landmark.x
                1 -> landmark.y
                2 -> landmark.z
                3 -> landmark.visibility
                else -> landmark.presence
            }
        }
    }

    fun decode(flat: FloatArray): List<Landmark> {
        require(flat.size >= FLAT_SIZE) {
            "Expected at least $FLAT_SIZE floats, got ${flat.size}"
        }
        return List(LANDMARK_COUNT) { index ->
            val base = index * FLOATS_PER_LANDMARK
            Landmark(
                x = flat[base],
                y = flat[base + 1],
                z = flat[base + 2],
                visibility = flat[base + 3],
                presence = flat[base + 4],
            )
        }
    }

    fun decodeOptional(flat: FloatArray?): List<Landmark>? =
        flat?.takeIf { it.size >= FLAT_SIZE }?.let(::decode)
}
