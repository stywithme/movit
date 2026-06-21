package com.movit.feature.trainingdebug

import androidx.compose.ui.graphics.Color
import com.movit.core.training.geometry.JointLandmarkMapping
import com.movit.core.training.geometry.PoseLandmarkMirroring
import com.movit.core.training.position.PositionCheckDebugStatus
import com.movit.designsystem.components.DebugJointHighlight
import com.movit.designsystem.components.DebugOverlayScaleMode
import com.movit.designsystem.components.DebugPositionLine
import com.movit.designsystem.components.DebugPositionLineStatus
import com.movit.designsystem.components.DebugSceneOverlay
import com.movit.designsystem.components.SkeletonDebugOverlayState

object TrainingDebugOverlayMapper {
    fun map(
        config: TrainingDebugConfig,
        analysis: TrainingDebugAnalysisResult,
        isFrontCamera: Boolean = false,
    ): SkeletonDebugOverlayState {
        val scaleMode = when (config.inputMode) {
            TrainingDebugInputMode.IMAGE -> DebugOverlayScaleMode.FIT_CENTER
            TrainingDebugInputMode.CAMERA,
            TrainingDebugInputMode.VIDEO,
            -> DebugOverlayScaleMode.FILL_CENTER
        }
        return when (config.activeTab) {
            TrainingDebugTab.ANGLE_DIAGNOSTICS -> analysis.overlayState.copy(
                selectedJointHighlights = buildJointHighlights(analysis.angleDiagnostics),
                positionLine = null,
                sceneExpectation = null,
                scaleMode = scaleMode,
            )
            TrainingDebugTab.POSITION_CHECK -> analysis.overlayState.copy(
                selectedJointHighlights = emptyList(),
                positionLine = buildPositionLine(config, analysis, isFrontCamera),
                sceneExpectation = null,
                scaleMode = scaleMode,
            )
            TrainingDebugTab.CAMERA_SCENE,
            TrainingDebugTab.SETUP_GATE,
            -> analysis.overlayState.copy(
                selectedJointHighlights = emptyList(),
                positionLine = null,
                sceneExpectation = buildSceneOverlay(analysis),
                scaleMode = scaleMode,
            )
        }
    }

    private fun buildJointHighlights(
        diagnostics: List<AngleDiagnosticsData>,
    ): List<DebugJointHighlight> = diagnostics.mapNotNull { item ->
        val indices = item.effectiveIndices
        if (indices.size < 3) return@mapNotNull null
        DebugJointHighlight(
            jointCode = item.displayJointCode,
            endpointAIndex = indices[0],
            vertexIndex = indices[1],
            endpointCIndex = indices[2],
            angleDegrees = item.displayedAngle,
            color = Color(0xFF64B5F6),
        )
    }

    private fun buildPositionLine(
        config: TrainingDebugConfig,
        analysis: TrainingDebugAnalysisResult,
        isFrontCamera: Boolean,
    ): DebugPositionLine? {
        val primaryRaw = JointLandmarkMapping.jointToLandmark(config.positionCheck.primaryLandmark) ?: return null
        val secondaryRaw = JointLandmarkMapping.jointToLandmark(config.positionCheck.secondaryLandmark) ?: return null
        val primary = if (isFrontCamera) PoseLandmarkMirroring.mirroredIndex(primaryRaw) else primaryRaw
        val secondary = if (isFrontCamera) PoseLandmarkMirroring.mirroredIndex(secondaryRaw) else secondaryRaw
        val status = when (analysis.positionDebug?.status) {
            PositionCheckDebugStatus.PASS -> DebugPositionLineStatus.PASS
            PositionCheckDebugStatus.FAIL -> DebugPositionLineStatus.FAIL
            PositionCheckDebugStatus.FAIL_PENDING -> DebugPositionLineStatus.FAIL_PENDING
            PositionCheckDebugStatus.SKIPPED,
            null,
            -> DebugPositionLineStatus.SKIPPED
        }
        return DebugPositionLine(
            primaryIndex = primary,
            secondaryIndex = secondary,
            status = status,
        )
    }

    private fun buildSceneOverlay(analysis: TrainingDebugAnalysisResult): DebugSceneOverlay? {
        val scene = analysis.sceneResult ?: return null
        val match = analysis.axisMatch
        return DebugSceneOverlay(
            postureLabel = scene.posture.name,
            directionLabel = scene.direction.name,
            regionLabel = scene.region.name,
            allMatch = match?.allMatch == true,
        )
    }
}
