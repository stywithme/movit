package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PoseFrameAssemblerEstimatorOwnershipTest {
    @Test
    fun assemble_injectedEstimators_doNotShareDiagnostics() {
        val estimatorA = ElbowAngleEstimator()
        val estimatorB = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.3f, 0.4f, 0f, 1f, 1f)
        world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.05f, 0.4f, 0f, 1f, 1f)
        world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.2f, 0.4f, 0f, 1f, 1f)
        world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.3f, 0.4f, 0f, 1f, 1f)
        norm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.35f, 0.30f, 0f, 1f, 1f)
        norm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.45f, 0.30f, 0f, 1f, 1f)
        norm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.58f, 0.30f, 0f, 1f, 1f)
        norm[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.30f, 0f, 1f, 1f)

        PoseFrameAssembler.assemble(
            landmarks = norm,
            timestampMs = 1_000L,
            isFrontCamera = false,
            worldLandmarks = world,
            estimator = estimatorA,
            collectElbowDiagnostics = true,
        )

        assertNotNull(estimatorA.lastDiagnostics[0])
        assertNull(estimatorB.lastDiagnostics[0])
    }

    private fun visibleLandmarks(): MutableList<Landmark> =
        MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
}
