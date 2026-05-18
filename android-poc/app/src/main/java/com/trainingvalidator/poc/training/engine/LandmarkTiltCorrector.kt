package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rotates normalized screen-space landmarks around the image center.
 *
 * This is intended for position/posture checks that assume image Y is gravity.
 * Joint angles should keep using the original landmarks because angle geometry is
 * already invariant to a uniform screen rotation.
 */
object LandmarkTiltCorrector {
    private const val CENTER = 0.5f

    fun correct(
        landmarks: List<SmoothedLandmark>,
        correctionRadians: Float
    ): List<SmoothedLandmark> {
        if (landmarks.isEmpty() || !correctionRadians.isFinite()) return landmarks

        val cos = cos(correctionRadians)
        val sin = sin(correctionRadians)

        return landmarks.map { landmark ->
            val dx = landmark.x - CENTER
            val dy = landmark.y - CENTER

            landmark.copy(
                x = CENTER + dx * cos - dy * sin,
                y = CENTER + dx * sin + dy * cos
            )
        }
    }
}
