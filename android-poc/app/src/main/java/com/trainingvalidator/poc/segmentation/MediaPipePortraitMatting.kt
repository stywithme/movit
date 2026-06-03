package com.trainingvalidator.poc.segmentation

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * MediaPipe Image Segmenter fallback (selfie_segmenter.tflite).
 */
class MediaPipePortraitMatting(
    private val context: Context,
    private val modelAsset: String,
    private val personCategoryIndexes: List<Int>
) : AutoCloseable {

    companion object {
        private val FALLBACK_PERSON_INDEXES = listOf(1)
        private const val DEEPLAB_PERSON_INDEX = 15
        private val MULTICLASS_PERSON_INDEXES = listOf(1, 2, 3, 4, 5)

        fun resolvePersonCategoryIndexes(modelAsset: String, configuredIndexes: List<Int>): List<Int> {
            if (configuredIndexes.isNotEmpty()) return configuredIndexes
            val normalized = modelAsset.lowercase()
            return when {
                normalized.contains("deeplab") -> listOf(DEEPLAB_PERSON_INDEX)
                normalized.contains("multiclass") -> MULTICLASS_PERSON_INDEXES
                else -> FALLBACK_PERSON_INDEXES
            }
        }
    }

    private val segmenter: ImageSegmenter
    private val categoryIndexes: List<Int>

    init {
        categoryIndexes = resolvePersonCategoryIndexes(modelAsset, personCategoryIndexes)
        segmenter = ImageSegmenter.createFromOptions(
            context,
            ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(modelAsset)
                        .setDelegate(Delegate.CPU)
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(true)
                .build()
        )
    }

    fun extractMask(source: Bitmap): PortraitMask? {
        val result = segmenter.segment(BitmapImageBuilder(source).build())
        return extractPersonMask(result, categoryIndexes)
    }

    override fun close() {
        segmenter.close()
    }

    private fun extractPersonMask(
        result: ImageSegmenterResult,
        personCategoryIndexes: List<Int>
    ): PortraitMask? {
        val confidenceOptional = result.confidenceMasks()
        if (confidenceOptional.isPresent) {
            val masks = confidenceOptional.get()
            if (masks.isNotEmpty()) {
                val selectedIndexes = personCategoryIndexes
                    .filter { it in masks.indices }
                    .ifEmpty { listOf(if (masks.size > 1) 1 else 0) }
                val firstMask = masks[selectedIndexes.first()]
                val confidence = FloatArray(firstMask.width * firstMask.height)

                for (categoryIndex in selectedIndexes) {
                    val maskImage = masks[categoryIndex]
                    val buffer: FloatBuffer = ByteBufferExtractor.extract(maskImage).asFloatBuffer()
                    var i = 0
                    while (buffer.hasRemaining() && i < confidence.size) {
                        confidence[i] = max(confidence[i], buffer.get().coerceIn(0f, 1f))
                        i++
                    }
                }

                return PortraitMask(confidence, firstMask.width, firstMask.height)
            }
        }

        val categoryOptional = result.categoryMask()
        if (categoryOptional.isPresent) {
            val maskImage = categoryOptional.get()
            val buffer = ByteBufferExtractor.extract(maskImage)
            val confidence = FloatArray(buffer.remaining())
            var i = 0
            while (buffer.hasRemaining()) {
                val category = buffer.get().toInt() and 0xFF
                confidence[i++] = if (category in personCategoryIndexes) 1f else 0f
            }
            return PortraitMask(confidence, maskImage.width, maskImage.height)
        }

        return null
    }
}
