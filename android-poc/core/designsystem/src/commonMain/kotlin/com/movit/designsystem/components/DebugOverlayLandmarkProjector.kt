package com.movit.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Maps normalized pose landmarks (0–1 in analysis image space) to overlay canvas pixels.
 * Mirrors [com.movit.core.training.geometry.DisplayLandmarkTransform] without coupling
 * designsystem to training-engine.
 *
 * ML landmarks stay in unmirrored analysis space; [mirrorX] applies only at draw time.
 */
class DebugOverlayLandmarkProjector(
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val mirrorX: Boolean,
    private val scaleMode: DebugOverlayScaleMode,
) {
    fun project(
        normalizedX: Float,
        normalizedY: Float,
        canvasWidth: Float,
        canvasHeight: Float,
    ): Offset {
        val viewW = canvasWidth.coerceAtLeast(1f)
        val viewH = canvasHeight.coerceAtLeast(1f)
        val nx = if (mirrorX) 1f - normalizedX else normalizedX

        if (imageWidth <= 0 || imageHeight <= 0) {
            return Offset(nx * viewW, normalizedY * viewH)
        }

        val analysisW = imageWidth.coerceAtLeast(1)
        val analysisH = imageHeight.coerceAtLeast(1)
        val scaleX = viewW / analysisW
        val scaleY = viewH / analysisH
        val scale = when (scaleMode) {
            DebugOverlayScaleMode.FILL_CENTER -> max(scaleX, scaleY)
            DebugOverlayScaleMode.FIT_CENTER -> min(scaleX, scaleY)
        }
        val scaledW = analysisW * scale
        val scaledH = analysisH * scale
        val offsetX = (viewW - scaledW) / 2f
        val offsetY = (viewH - scaledH) / 2f
        val px = nx * analysisW * scale + offsetX
        val py = normalizedY * analysisH * scale + offsetY
        return Offset(px, py)
    }
}

@Composable
fun rememberDebugOverlayLandmarkProjector(
    scaleMode: DebugOverlayScaleMode,
    isFrontCamera: Boolean,
    imageWidth: Int,
    imageHeight: Int,
): (Float, Float, Float, Float) -> Offset {
    val projector = remember(scaleMode, isFrontCamera, imageWidth, imageHeight) {
        DebugOverlayLandmarkProjector(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            mirrorX = isFrontCamera,
            scaleMode = scaleMode,
        )
    }
    return remember(projector) {
        { x, y, canvasW, canvasH -> projector.project(x, y, canvasW, canvasH) }
    }
}

internal fun sweepAngleDegrees(startDegrees: Float, endDegrees: Float): Float {
    val sweep = ((endDegrees - startDegrees + 540f) % 360f) - 180f
    return if (sweep == -180f) 180f else sweep
}
