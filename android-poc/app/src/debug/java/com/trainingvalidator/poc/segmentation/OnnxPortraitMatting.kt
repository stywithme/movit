package com.trainingvalidator.poc.segmentation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import java.nio.FloatBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Portrait matting via ONNX Runtime (MODNet / U²-Net human seg).
 *
 * Debug builds only — release uses [com.trainingvalidator.poc.segmentation.OnnxPortraitMatting] stub (F8).
 */
class OnnxPortraitMatting(
    private val context: Context,
    private val modelAsset: String,
    private val engine: MattingEngine,
    private val inputSize: Int
) : AutoCloseable {

    companion object {
        private const val TAG = "OnnxPortraitMatting"
        private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    init {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        val modelBytes = context.assets.open(modelAsset).use { it.readBytes() }
        session = ortEnv.createSession(modelBytes, options)
        inputName = session.inputNames.first()
        Log.d(TAG, "Loaded $modelAsset engine=$engine input=$inputName size=$inputSize")
    }

    fun extractMask(source: Bitmap): PortraitMask? {
        val size = inputSize.coerceIn(128, 512)
        val letterbox = letterbox(source, size)
        val input = if (engine == MattingEngine.U2NET) {
            bitmapToNchwImagenet(letterbox.bitmap, size)
        } else {
            bitmapToNchwUnit(letterbox.bitmap, size)
        }

        val shape = longArrayOf(1, 3, size.toLong(), size.toLong())
        val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape)
        return try {
            inputTensor.use { tensor ->
                session.run(mapOf(inputName to tensor)).use { results ->
                    val rawMatte = readOutputMatte(results[0].value) ?: return null
                    val normalized = if (engine == MattingEngine.U2NET) {
                        minMaxNormalize(rawMatte)
                    } else {
                        rawMatte
                    }
                    val cropped = cropLetterbox(normalized, size, letterbox)
                    upsampleToSource(cropped, letterbox, source.width, source.height)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ONNX matting failed for $modelAsset: ${e.message}", e)
            null
        } finally {
            if (letterbox.bitmap !== source) {
                letterbox.bitmap.recycle()
            }
        }
    }

    override fun close() {
        session.close()
    }

    private data class LetterboxResult(
        val bitmap: Bitmap,
        val scale: Float,
        val padLeft: Int,
        val padTop: Int,
        val contentWidth: Int,
        val contentHeight: Int
    )

    private fun letterbox(source: Bitmap, targetSize: Int): LetterboxResult {
        val scale = min(
            targetSize.toFloat() / source.width,
            targetSize.toFloat() / source.height
        )
        val contentWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val contentHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, contentWidth, contentHeight, true)
        val padded = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        val padLeft = (targetSize - contentWidth) / 2
        val padTop = (targetSize - contentHeight) / 2
        canvas.drawBitmap(scaled, padLeft.toFloat(), padTop.toFloat(), null)
        if (scaled !== source) {
            scaled.recycle()
        }
        return LetterboxResult(
            bitmap = padded,
            scale = scale,
            padLeft = padLeft,
            padTop = padTop,
            contentWidth = contentWidth,
            contentHeight = contentHeight
        )
    }

    private fun bitmapToNchwUnit(bitmap: Bitmap, size: Int): FloatArray {
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        val out = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val color = pixels[i]
            out[i] = Color.red(color) / 255f
            out[plane + i] = Color.green(color) / 255f
            out[2 * plane + i] = Color.blue(color) / 255f
        }
        return out
    }

    private fun bitmapToNchwImagenet(bitmap: Bitmap, size: Int): FloatArray {
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        val out = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val color = pixels[i]
            out[i] = (Color.red(color) / 255f - IMAGENET_MEAN[0]) / IMAGENET_STD[0]
            out[plane + i] = (Color.green(color) / 255f - IMAGENET_MEAN[1]) / IMAGENET_STD[1]
            out[2 * plane + i] = (Color.blue(color) / 255f - IMAGENET_MEAN[2]) / IMAGENET_STD[2]
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun readOutputMatte(value: Any?): FloatArray? {
        return when (value) {
            is Array<*> -> {
                val batch = value[0] as? Array<*> ?: return null
                val channel = batch[0] as? Array<*> ?: return null
                val height = channel.size
                if (height == 0) return null
                val row = channel[0] as? FloatArray ?: return null
                val width = row.size
                val out = FloatArray(width * height)
                for (y in 0 until height) {
                    val srcRow = channel[y] as FloatArray
                    System.arraycopy(srcRow, 0, out, y * width, width)
                }
                out
            }
            else -> null
        }
    }

    private fun minMaxNormalize(values: FloatArray): FloatArray {
        var minValue = Float.MAX_VALUE
        var maxValue = -Float.MAX_VALUE
        for (v in values) {
            if (v < minValue) minValue = v
            if (v > maxValue) maxValue = v
        }
        if (maxValue <= minValue) {
            return FloatArray(values.size)
        }
        val range = maxValue - minValue
        return FloatArray(values.size) { i ->
            ((values[i] - minValue) / range).coerceIn(0f, 1f)
        }
    }

    private fun cropLetterbox(
        matte: FloatArray,
        modelSize: Int,
        letterbox: LetterboxResult
    ): FloatArray {
        val outW = letterbox.contentWidth
        val outH = letterbox.contentHeight
        val cropped = FloatArray(outW * outH)
        for (y in 0 until outH) {
            val srcY = letterbox.padTop + y
            for (x in 0 until outW) {
                val srcX = letterbox.padLeft + x
                cropped[y * outW + x] = matte[srcY * modelSize + srcX].coerceIn(0f, 1f)
            }
        }
        return cropped
    }

    private fun upsampleToSource(
        cropped: FloatArray,
        letterbox: LetterboxResult,
        targetWidth: Int,
        targetHeight: Int
    ): PortraitMask {
        val confidence = FloatArray(targetWidth * targetHeight)
        val srcW = letterbox.contentWidth
        val srcH = letterbox.contentHeight
        for (y in 0 until targetHeight) {
            val srcY = ((y + 0.5f) / targetHeight * srcH - 0.5f).coerceIn(0f, (srcH - 1).toFloat())
            val y0 = srcY.toInt()
            val y1 = min(y0 + 1, srcH - 1)
            val yLerp = srcY - y0
            for (x in 0 until targetWidth) {
                val srcX = ((x + 0.5f) / targetWidth * srcW - 0.5f).coerceIn(0f, (srcW - 1).toFloat())
                val x0 = srcX.toInt()
                val x1 = min(x0 + 1, srcW - 1)
                val xLerp = srcX - x0
                val v00 = cropped[y0 * srcW + x0]
                val v10 = cropped[y0 * srcW + x1]
                val v01 = cropped[y1 * srcW + x0]
                val v11 = cropped[y1 * srcW + x1]
                val top = v00 + (v10 - v00) * xLerp
                val bottom = v01 + (v11 - v01) * xLerp
                confidence[y * targetWidth + x] = (top + (bottom - top) * yLerp).coerceIn(0f, 1f)
            }
        }
        return PortraitMask(confidence, targetWidth, targetHeight)
    }
}
