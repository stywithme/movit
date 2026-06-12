package com.trainingvalidator.poc.segmentation

import android.content.Context
import android.graphics.Bitmap

/**
 * Release stub — ONNX matting ships with debug builds only (F8 / deferred D9).
 * [ReportBackgroundEffectProcessor] falls back to the original hero image.
 */
class OnnxPortraitMatting(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") modelAsset: String,
    @Suppress("UNUSED_PARAMETER") engine: MattingEngine,
    @Suppress("UNUSED_PARAMETER") inputSize: Int,
) : AutoCloseable {

    fun extractMask(@Suppress("UNUSED_PARAMETER") source: Bitmap): PortraitMask? = null

    override fun close() = Unit
}
