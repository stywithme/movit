package com.movit.feature.training

import com.movit.designsystem.components.SkeletonJointVisual
import com.movit.designsystem.components.SkeletonLandmarkPoint
import com.movit.designsystem.components.SkeletonOverlayParityState
import com.movit.designsystem.components.SkeletonRomIndicator

/**
 * High-frequency skeleton overlay snapshot (WP-07 R1).
 * Updated once per frame on the pose worker; collected only by [MovitSkeletonOverlayHost].
 */
data class TrainingOverlayState(
    val landmarks: List<SkeletonLandmarkPoint>? = null,
    val analysisWidth: Int = 0,
    val analysisHeight: Int = 0,
    val mirrorPreview: Boolean = true,
    val jointVisuals: Map<String, SkeletonJointVisual> = emptyMap(),
    val romIndicators: List<SkeletonRomIndicator> = emptyList(),
    val parity: SkeletonOverlayParityState = SkeletonOverlayParityState(),
)
