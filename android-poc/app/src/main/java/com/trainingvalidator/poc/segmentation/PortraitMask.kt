package com.trainingvalidator.poc.segmentation

/**
 * Soft alpha matte aligned to the source image (one confidence value per pixel, 0–1).
 */
data class PortraitMask(
    val confidence: FloatArray,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PortraitMask
        return width == other.width &&
            height == other.height &&
            confidence.contentEquals(other.confidence)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + confidence.contentHashCode()
        return result
    }
}
