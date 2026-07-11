package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movit.designsystem.components.MovitSkeletonOverlay
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects [overlayFlow] internally so session chrome recomposes at low frequency
 * while the skeleton redraws at frame rate (WP-07 R1 / F-02).
 */
@Composable
fun MovitSkeletonOverlayHost(
    overlayFlow: StateFlow<TrainingOverlayState>,
    modifier: Modifier = Modifier,
    showBilateralSideHint: Boolean = false,
) {
    val overlay by overlayFlow.collectAsStateWithLifecycle()
    val landmarkProjector = remember(
        overlay.analysisWidth,
        overlay.analysisHeight,
        overlay.mirrorPreview,
    ) {
        skeletonLandmarkProjector(
            analysisWidth = overlay.analysisWidth,
            analysisHeight = overlay.analysisHeight,
            mirrorPreview = overlay.mirrorPreview,
        )
    }
    MovitSkeletonOverlay(
        landmarks = overlay.landmarks,
        parity = overlay.parity,
        romIndicators = overlay.romIndicators,
        landmarkProjector = landmarkProjector,
        showBilateralSideHint = showBilateralSideHint,
        modifier = modifier,
    )
}
