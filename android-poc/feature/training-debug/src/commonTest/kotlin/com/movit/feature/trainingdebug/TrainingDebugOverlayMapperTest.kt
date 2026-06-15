package com.movit.feature.trainingdebug

import com.movit.core.training.geometry.PoseLandmarkMirroring
import com.movit.designsystem.components.DebugOverlayScaleMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrainingDebugOverlayMapperTest {
    @Test
    fun angleTab_mapsSelectedJointHighlightsOnly() {
        val analysis = sampleAnalysis()
        val overlay = TrainingDebugOverlayMapper.map(
            config = TrainingDebugConfig(
                activeTab = TrainingDebugTab.ANGLE_DIAGNOSTICS,
                selectedJoints = setOf("left_knee", "right_knee"),
            ),
            analysis = analysis,
            isFrontCamera = false,
        )

        assertEquals(2, overlay.selectedJointHighlights.size)
        assertNull(overlay.positionLine)
        assertNull(overlay.sceneExpectation)
    }

    @Test
    fun positionTab_mirrorsLandmarkIndicesOnFrontCamera() {
        val analysis = sampleAnalysis()
        val back = TrainingDebugOverlayMapper.map(
            config = positionConfig(),
            analysis = analysis,
            isFrontCamera = false,
        )
        val front = TrainingDebugOverlayMapper.map(
            config = positionConfig(),
            analysis = analysis,
            isFrontCamera = true,
        )

        val backLine = back.positionLine
        val frontLine = front.positionLine
        assertNotNull(backLine)
        assertNotNull(frontLine)
        assertEquals(
            PoseLandmarkMirroring.mirroredIndex(backLine.primaryIndex),
            frontLine.primaryIndex,
        )
        assertEquals(
            PoseLandmarkMirroring.mirroredIndex(backLine.secondaryIndex),
            frontLine.secondaryIndex,
        )
    }

    @Test
    fun imageMode_usesFitCenterScale() {
        val overlay = TrainingDebugOverlayMapper.map(
            config = TrainingDebugConfig(
                inputMode = TrainingDebugInputMode.IMAGE,
                activeTab = TrainingDebugTab.ANGLE_DIAGNOSTICS,
            ),
            analysis = sampleAnalysis(),
            isFrontCamera = false,
        )
        assertEquals(DebugOverlayScaleMode.FIT_CENTER, overlay.scaleMode)
    }

    private fun positionConfig() = TrainingDebugConfig(
        activeTab = TrainingDebugTab.POSITION_CHECK,
        positionCheck = DebugPositionCheckConfig(
            primaryLandmark = "left_knee",
            secondaryLandmark = "left_ankle",
        ),
    )

    private fun sampleAnalysis(): TrainingDebugAnalysisResult {
        val diagnostics = listOf(
            AngleDiagnosticsData(
                displayJointCode = "left_knee",
                sourceJointCode = "left_knee",
                effectiveIndices = listOf(23, 25, 27),
                displayedAngle = 95.0,
                pipelineSourceLabel = "World XYZ",
                normalizedRaw = null,
                normalizedSmoothed = null,
                worldRaw = null,
                worldSmoothed = null,
            ),
            AngleDiagnosticsData(
                displayJointCode = "right_knee",
                sourceJointCode = "right_knee",
                effectiveIndices = listOf(24, 26, 28),
                displayedAngle = 96.0,
                pipelineSourceLabel = "World XYZ",
                normalizedRaw = null,
                normalizedSmoothed = null,
                worldRaw = null,
                worldSmoothed = null,
            ),
        )
        return TrainingDebugAnalysisResult(
            hasPose = true,
            liveValueText = "95°",
            statusText = "ok",
            infoPanelText = "panel",
            angleDiagnostics = diagnostics,
        )
    }
}
