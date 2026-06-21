package com.movit.feature.trainingdebug.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitPoseAnnotationOverlay
import com.movit.designsystem.components.SkeletonDebugOverlayState
import com.movit.designsystem.components.SkeletonLandmarkPoint

@Composable
fun TrainingDebugSkeletonOverlay(
    landmarks: List<SkeletonLandmarkPoint>?,
    debug: SkeletonDebugOverlayState,
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = false,
    analysisImageWidth: Int = 0,
    analysisImageHeight: Int = 0,
) {
    MovitPoseAnnotationOverlay(
        landmarks = landmarks,
        debug = debug,
        modifier = modifier,
        isFrontCamera = isFrontCamera,
        analysisImageWidth = analysisImageWidth,
        analysisImageHeight = analysisImageHeight,
        showFullSkeleton = false,
    )
}

fun landmarksToSkeletonPoints(
    landmarks: List<com.movit.core.training.model.Landmark>,
): List<SkeletonLandmarkPoint> =
    landmarks.map { lm ->
        SkeletonLandmarkPoint(x = lm.x, y = lm.y, visible = lm.visibility > 0.3f)
    }
