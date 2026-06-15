package com.movit.designsystem.components

import androidx.compose.ui.graphics.Color

/** Debug Lab overlay extensions (Training Debug Mode). */
enum class DebugOverlayScaleMode {
    FILL_CENTER,
    FIT_CENTER,
}

data class DebugJointHighlight(
    val jointCode: String,
    val vertexIndex: Int,
    val endpointAIndex: Int,
    val endpointCIndex: Int,
    val angleDegrees: Double?,
    val color: Color = Color(0xFF64B5F6),
)

data class DebugPositionLine(
    val primaryIndex: Int,
    val secondaryIndex: Int,
    val status: DebugPositionLineStatus,
)

enum class DebugPositionLineStatus {
    PASS,
    FAIL,
    FAIL_PENDING,
    SKIPPED,
}

data class DebugSceneOverlay(
    val postureLabel: String,
    val directionLabel: String,
    val regionLabel: String,
    val allMatch: Boolean,
)

data class SkeletonDebugOverlayState(
    val selectedJointHighlights: List<DebugJointHighlight> = emptyList(),
    val positionLine: DebugPositionLine? = null,
    val sceneExpectation: DebugSceneOverlay? = null,
    val scaleMode: DebugOverlayScaleMode = DebugOverlayScaleMode.FILL_CENTER,
)
