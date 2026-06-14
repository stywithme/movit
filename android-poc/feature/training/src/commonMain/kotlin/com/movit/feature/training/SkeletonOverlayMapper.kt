package com.movit.feature.training

import androidx.compose.ui.geometry.Offset
import com.movit.core.training.geometry.CameraFrameLayout
import com.movit.core.training.geometry.DisplayLandmarkTransform
import com.movit.core.training.geometry.DisplayScaleMode

/**
 * Projects normalized analysis landmarks onto the Compose overlay canvas using the same
 * FILL_CENTER math as [com.movit.feature.training.TrainingCameraSurface] / PreviewView.
 */
fun skeletonLandmarkProjector(
    analysisWidth: Int,
    analysisHeight: Int,
    mirrorPreview: Boolean,
): ((Float, Float, Float, Float) -> Offset)? {
    if (analysisWidth <= 0 || analysisHeight <= 0) return null
    return { normalizedX, normalizedY, canvasWidth, canvasHeight ->
        val transform = DisplayLandmarkTransform.fromLayout(
            layout = CameraFrameLayout(
                analysisWidth = analysisWidth,
                analysisHeight = analysisHeight,
                previewWidth = canvasWidth.toInt().coerceAtLeast(1),
                previewHeight = canvasHeight.toInt().coerceAtLeast(1),
            ),
            isFrontCamera = mirrorPreview,
            scaleMode = DisplayScaleMode.FILL_CENTER,
        )
        val (px, py) = transform.mapNormalized(normalizedX, normalizedY)
        Offset(px, py)
    }
}
