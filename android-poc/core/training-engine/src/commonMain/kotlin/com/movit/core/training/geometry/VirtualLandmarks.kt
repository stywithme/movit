package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices

/**
 * Virtual neck/spine landmarks (indices 33–34) — parity with legacy
 * [com.trainingvalidator.poc.analysis.LandmarkSmoother.appendVirtualLandmarks].
 */
object VirtualLandmarks {
    const val NECK = 33
    const val SPINE = 34
    const val TOTAL_WITH_VIRTUAL = 35

    fun ensureAppended(landmarks: List<Landmark>): List<Landmark> {
        if (landmarks.size >= TOTAL_WITH_VIRTUAL) return landmarks
        if (landmarks.size < 33) return landmarks

        val leftShoulder = landmarks[PoseLandmarkIndices.LEFT_SHOULDER]
        val rightShoulder = landmarks[PoseLandmarkIndices.RIGHT_SHOULDER]
        val leftHip = landmarks[PoseLandmarkIndices.LEFT_HIP]
        val rightHip = landmarks[PoseLandmarkIndices.RIGHT_HIP]

        val neck = Landmark(
            x = (leftShoulder.x + rightShoulder.x) / 2f,
            y = (leftShoulder.y + rightShoulder.y) / 2f,
            z = (leftShoulder.z + rightShoulder.z) / 2f,
            visibility = minOf(leftShoulder.visibility, rightShoulder.visibility),
            presence = minOf(leftShoulder.presence, rightShoulder.presence),
        )
        val spine = Landmark(
            x = (leftHip.x + rightHip.x) / 2f,
            y = (leftHip.y + rightHip.y) / 2f,
            z = (leftHip.z + rightHip.z) / 2f,
            visibility = minOf(leftHip.visibility, rightHip.visibility),
            presence = minOf(leftHip.presence, rightHip.presence),
        )
        return landmarks + listOf(neck, spine)
    }
}
