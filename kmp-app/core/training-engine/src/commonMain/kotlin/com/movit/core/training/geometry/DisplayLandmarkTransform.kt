package com.movit.core.training.geometry

import kotlin.math.max
import kotlin.math.min

/**
 * Maps normalized pose landmarks (0–1 in analysis image space) to PreviewView pixels.
 * Mirrors legacy [SkeletonOverlayView] FILL_CENTER / FIT_CENTER + front-camera preview mirror.
 *
 * ML landmarks stay in unmirrored analysis space; [mirrorX] applies only at draw time so the
 * training engine keeps using [com.movit.core.training.model.PoseFrame.mirrored] for logic.
 */
enum class DisplayScaleMode {
    /** Crop to fill the view (PreviewView default). */
    FILL_CENTER,
    /** Letterbox to fit entirely inside the view. */
    FIT_CENTER,
}

data class CameraFrameLayout(
    val analysisWidth: Int,
    val analysisHeight: Int,
    val previewWidth: Int,
    val previewHeight: Int,
)

data class DisplayLandmarkTransform(
    val analysisWidth: Int,
    val analysisHeight: Int,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val mirrorX: Boolean,
    val scaleMode: DisplayScaleMode = DisplayScaleMode.FILL_CENTER,
) {
    fun mapNormalized(x: Float, y: Float): Pair<Float, Float> {
        val nx = if (mirrorX) 1f - x else x
        val px = nx * analysisWidth * scale + offsetX
        val py = y * analysisHeight * scale + offsetY
        return px to py
    }

    companion object {
        fun fromLayout(
            layout: CameraFrameLayout,
            isFrontCamera: Boolean,
            scaleMode: DisplayScaleMode = DisplayScaleMode.FILL_CENTER,
        ): DisplayLandmarkTransform {
            val analysisW = layout.analysisWidth.coerceAtLeast(1)
            val analysisH = layout.analysisHeight.coerceAtLeast(1)
            val viewW = layout.previewWidth.coerceAtLeast(1).toFloat()
            val viewH = layout.previewHeight.coerceAtLeast(1).toFloat()
            val scaleX = viewW / analysisW
            val scaleY = viewH / analysisH
            val scale = when (scaleMode) {
                DisplayScaleMode.FILL_CENTER -> max(scaleX, scaleY)
                DisplayScaleMode.FIT_CENTER -> min(scaleX, scaleY)
            }
            val scaledW = analysisW * scale
            val scaledH = analysisH * scale
            return DisplayLandmarkTransform(
                analysisWidth = analysisW,
                analysisHeight = analysisH,
                scale = scale,
                offsetX = (viewW - scaledW) / 2f,
                offsetY = (viewH - scaledH) / 2f,
                mirrorX = isFrontCamera,
                scaleMode = scaleMode,
            )
        }

        /** Identity-ish mapping when analysis dimensions are unknown (fallback). */
        fun stretchToView(
            viewWidth: Int,
            viewHeight: Int,
            isFrontCamera: Boolean,
        ): DisplayLandmarkTransform {
            val w = viewWidth.coerceAtLeast(1)
            val h = viewHeight.coerceAtLeast(1)
            return DisplayLandmarkTransform(
                analysisWidth = 1,
                analysisHeight = 1,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                mirrorX = isFrontCamera,
                scaleMode = DisplayScaleMode.FILL_CENTER,
            ).let { base ->
                base.copy(
                    analysisWidth = w,
                    analysisHeight = h,
                    scale = 1f,
                )
            }
        }
    }
}
