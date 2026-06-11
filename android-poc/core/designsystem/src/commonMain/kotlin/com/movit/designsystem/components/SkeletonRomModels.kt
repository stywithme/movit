package com.movit.designsystem.components

enum class SkeletonRomIndicatorStyle {
    ARC,
    LINE,
}

/**
 * Visual spec for a ROM range indicator on the skeleton overlay.
 * Positions are normalized 0–1 in preview space.
 */
data class SkeletonRomIndicator(
    val jointCode: String,
    val style: SkeletonRomIndicatorStyle = SkeletonRomIndicatorStyle.ARC,
    val centerX: Float,
    val centerY: Float,
    val limbEndX: Float = centerX,
    val limbEndY: Float = centerY,
    val currentAngleDeg: Float,
    val rangeMinDeg: Float,
    val rangeMaxDeg: Float,
    val trackMinDeg: Float = 0f,
    val trackMaxDeg: Float = 180f,
    val markerColorArgb: Long = 0xFF4FC3F7,
    val trackColorArgb: Long = 0x40FFFFFF,
    val showCurrentMarker: Boolean = true,
)
