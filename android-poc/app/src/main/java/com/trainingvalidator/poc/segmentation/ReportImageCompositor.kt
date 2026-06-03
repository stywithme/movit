package com.trainingvalidator.poc.segmentation

import android.graphics.Bitmap
import android.graphics.Color
import com.trainingvalidator.poc.training.config.BackgroundEffectSettings
import kotlin.math.max
import kotlin.math.roundToInt

internal object ReportImageCompositor {

    fun composite(
        source: Bitmap,
        mask: PortraitMask,
        settings: BackgroundEffectSettings
    ): Bitmap {
        val width = source.width
        val height = source.height
        val pixelCount = width * height

        val sourcePixels = IntArray(pixelCount)
        val backgroundPixels = IntArray(pixelCount)
        val outputPixels = IntArray(pixelCount)

        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)

        val blurredBackground = createBlurredBackground(source, settings.blurRadius)
        blurredBackground.getPixels(backgroundPixels, 0, width, 0, 0, width, height)
        if (blurredBackground !== source) {
            blurredBackground.recycle()
        }

        val tintColor = parseTintColor(settings.tintColor)
        val tintAlpha = settings.tintAlpha.coerceIn(0f, 1f)
        val threshold = settings.personThreshold.coerceIn(0f, 1f)
        val edge = 0.12f

        for (y in 0 until height) {
            val maskY = ((y.toFloat() / height) * mask.height).toInt().coerceIn(0, mask.height - 1)
            for (x in 0 until width) {
                val index = y * width + x
                val maskX = ((x.toFloat() / width) * mask.width).toInt().coerceIn(0, mask.width - 1)
                val personConfidence = mask.confidence[maskY * mask.width + maskX].coerceIn(0f, 1f)
                val personBlend = smoothStep(threshold - edge, threshold + edge, personConfidence)
                val background = blendColors(backgroundPixels[index], tintColor, tintAlpha)
                outputPixels[index] = blendColors(background, sourcePixels[index], personBlend)
            }
        }

        return Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun createBlurredBackground(source: Bitmap, blurRadius: Int): Bitmap {
        val radius = blurRadius.coerceIn(0, 40)
        if (radius == 0) return source.copy(Bitmap.Config.ARGB_8888, false)

        val sampleFactor = when {
            radius <= 6 -> 2
            radius <= 14 -> 4
            radius <= 24 -> 6
            else -> 8
        }
        val smallWidth = max(1, source.width / sampleFactor)
        val smallHeight = max(1, source.height / sampleFactor)
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        val blurredSmall = boxBlur(small, (radius / sampleFactor.toFloat()).roundToInt().coerceAtLeast(1))
        if (blurredSmall !== small) {
            small.recycle()
        }
        val full = Bitmap.createScaledBitmap(blurredSmall, source.width, source.height, true)
        if (blurredSmall !== full) {
            blurredSmall.recycle()
        }
        return full
    }

    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        val temp = IntArray(width * height)
        val output = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val startX = (x - radius).coerceAtLeast(0)
                val endX = (x + radius).coerceAtMost(width - 1)
                for (kx in startX..endX) {
                    val color = input[y * width + kx]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                temp[y * width + x] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val startY = (y - radius).coerceAtLeast(0)
                val endY = (y + radius).coerceAtMost(height - 1)
                for (ky in startY..endY) {
                    val color = temp[ky * width + x]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                output[y * width + x] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }

        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun blendColors(background: Int, foreground: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        val inv = 1f - a
        return Color.argb(
            (Color.alpha(background) * inv + Color.alpha(foreground) * a).roundToInt(),
            (Color.red(background) * inv + Color.red(foreground) * a).roundToInt(),
            (Color.green(background) * inv + Color.green(foreground) * a).roundToInt(),
            (Color.blue(background) * inv + Color.blue(foreground) * a).roundToInt()
        )
    }

    private fun parseTintColor(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: IllegalArgumentException) {
            Color.BLACK
        }
    }
}
