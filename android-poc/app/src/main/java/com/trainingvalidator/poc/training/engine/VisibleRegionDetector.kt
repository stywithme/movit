package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks

/**
 * Detects which body region is visible in the current frame.
 *
 * Combines **visibility score** with **position bounds** (landmark x/y within the
 * image frame) to avoid counting "hallucinated" off-screen landmarks that MediaPipe
 * returns with moderate visibility.
 */
object VisibleRegionDetector {

    private const val VISIBILITY_THRESHOLD = 0.45f

    // Landmarks beyond this margin from the normalised image frame are penalised
    private const val FRAME_MARGIN = 0.05f

    private val UPPER_INDICES = intArrayOf(
        BodyLandmarks.NOSE,
        BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
        BodyLandmarks.LEFT_ELBOW, BodyLandmarks.RIGHT_ELBOW,
        BodyLandmarks.LEFT_WRIST, BodyLandmarks.RIGHT_WRIST,
    )

    private val CORE_INDICES = intArrayOf(
        BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
        BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP,
    )

    private val LOWER_INDICES = intArrayOf(
        BodyLandmarks.LEFT_KNEE, BodyLandmarks.RIGHT_KNEE,
        BodyLandmarks.LEFT_ANKLE, BodyLandmarks.RIGHT_ANKLE,
        BodyLandmarks.LEFT_HEEL, BodyLandmarks.RIGHT_HEEL,
    )

    data class RegionResult(
        val region: VisibleRegion,
        val upperScore: Float,
        val coreScore: Float,
        val lowerScore: Float
    )

    fun detect(landmarks: List<SmoothedLandmark>): RegionResult {
        if (landmarks.size < 33) return RegionResult(VisibleRegion.UNKNOWN, 0f, 0f, 0f)

        val upper = avgPresence(landmarks, UPPER_INDICES)
        val core = avgPresence(landmarks, CORE_INDICES)
        val lower = avgPresence(landmarks, LOWER_INDICES)

        val upperPresent = upper > VISIBILITY_THRESHOLD
        val corePresent = core > VISIBILITY_THRESHOLD
        val lowerPresent = lower > VISIBILITY_THRESHOLD

        val region = when {
            upperPresent && corePresent && lowerPresent -> VisibleRegion.FULL_BODY
            upperPresent && corePresent && !lowerPresent -> VisibleRegion.UPPER_BODY
            !upperPresent && corePresent && lowerPresent -> VisibleRegion.LOWER_BODY
            upperPresent && !corePresent && !lowerPresent -> VisibleRegion.UPPER_BODY
            !upperPresent && !corePresent && lowerPresent -> VisibleRegion.LOWER_BODY
            upperPresent && !corePresent && lowerPresent -> VisibleRegion.FULL_BODY
            else -> VisibleRegion.FULL_BODY
        }

        return RegionResult(region, upper, core, lower)
    }

    /**
     * Average "presence" score: visibility multiplied by a penalty if the landmark
     * position is outside the normalised image frame.
     */
    private fun avgPresence(landmarks: List<SmoothedLandmark>, indices: IntArray): Float {
        var sum = 0f
        var count = 0
        for (i in indices) {
            val lm = landmarks.getOrNull(i) ?: continue
            val inFrame = lm.x in -FRAME_MARGIN..(1f + FRAME_MARGIN) &&
                    lm.y in -FRAME_MARGIN..(1f + FRAME_MARGIN)
            sum += if (inFrame) lm.visibility else lm.visibility * 0.3f
            count++
        }
        return if (count > 0) sum / count else 0f
    }
}

enum class VisibleRegion {
    FULL_BODY,
    UPPER_BODY,
    LOWER_BODY,
    UNKNOWN
}
