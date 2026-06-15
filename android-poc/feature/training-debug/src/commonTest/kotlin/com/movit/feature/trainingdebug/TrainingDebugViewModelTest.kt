package com.movit.feature.trainingdebug

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingDebugViewModelTest {
    @Test
    fun onFrame_null_clearsStaleAnalysis() {
        val viewModel = TrainingDebugViewModel()
        viewModel.onFrame(squatFrame())
        assertTrue(viewModel.uiState.value.analysis.hasPose)

        viewModel.onFrame(null)

        val analysis = viewModel.uiState.value.analysis
        assertFalse(analysis.hasPose)
        assertEquals("No pose", analysis.statusText)
        assertEquals("—", analysis.liveValueText)
        assertTrue(analysis.overlayState.selectedJointHighlights.isEmpty())
    }

    private fun squatFrame(): TrainingDebugFrameInput =
        TrainingDebugFrameFactory.fromLandmarks(
            landmarks = squatLandmarks(),
            worldLandmarks = squatLandmarks(),
            timestampMs = 1_000L,
            isFrontCamera = false,
            analysisImageWidth = 640,
            analysisImageHeight = 480,
        )

    private fun squatLandmarks(): List<Landmark> {
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
