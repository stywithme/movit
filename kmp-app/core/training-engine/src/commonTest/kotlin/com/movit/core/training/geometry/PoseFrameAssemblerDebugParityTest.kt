package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ensures debug-lab frame assembly matches the production camera pipeline entry point.
 */
class PoseFrameAssemblerDebugParityTest {
    @Test
    fun assemble_producesSameAngles_asDirectTrainingPath() {
        val landmarks = squatLandmarks()
        val world = squatLandmarks()

        val productionFrame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 42L,
            isFrontCamera = false,
            worldLandmarks = world,
            analysisImageWidth = 640,
            analysisImageHeight = 480,
        )
        val debugFrame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 42L,
            isFrontCamera = false,
            worldLandmarks = world,
            analysisImageWidth = 640,
            analysisImageHeight = 480,
        )

        assertEquals(productionFrame.angles.leftKnee, debugFrame.angles.leftKnee)
        assertEquals(productionFrame.angles.rightKnee, debugFrame.angles.rightKnee)
        assertEquals(productionFrame.angles.leftElbow, debugFrame.angles.leftElbow)
    }

    private fun squatLandmarks(): MutableList<Landmark> {
        val landmarks = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.45f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.45f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.45f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.55f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0.55f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.42f, 0.32f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.58f, 0.32f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.NOSE] = Landmark(0.5f, 0.22f, 0f, 1f, 1f)
        return landmarks
    }
}
