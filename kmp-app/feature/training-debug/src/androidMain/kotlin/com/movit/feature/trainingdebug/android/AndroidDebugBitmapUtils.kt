package com.movit.feature.trainingdebug.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.roundToInt

internal object AndroidDebugBitmapUtils {
    private val transformMatrix = Matrix()
    private val srcRect = RectF()
    private val dstRect = RectF()

    fun rotateForAnalysis(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return source
        transformMatrix.reset()
        transformMatrix.postRotate(rotationDegrees.toFloat())
        srcRect.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        transformMatrix.mapRect(dstRect, srcRect)
        val outWidth = dstRect.width().roundToInt().coerceAtLeast(1)
        val outHeight = dstRect.height().roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            translate(-dstRect.left, -dstRect.top)
            concat(transformMatrix)
            drawBitmap(source, 0f, 0f, null)
        }
        return output
    }

    fun applyExifRotation(bitmap: Bitmap, rotationDegrees: Int): Bitmap =
        rotateForAnalysis(bitmap, rotationDegrees)
}
