package com.movit.designsystem.components

enum class SkeletonRomIndicatorStyle {
    ARC,
    LINE,
}

enum class SkeletonRomState {
    PERFECT,
    NORMAL,
    PAD,
    WARNING,
    DANGER,
    TRANSITION,
}

data class SkeletonRomAngleRange(
    val minDeg: Float,
    val maxDeg: Float,
) {
    fun contains(angleDeg: Float): Boolean = angleDeg >= minDeg && angleDeg <= maxDeg
}

data class SkeletonRomStateRanges(
    val perfect: SkeletonRomAngleRange,
    val normal: SkeletonRomAngleRange? = null,
    val pad: SkeletonRomAngleRange? = null,
    val warning: SkeletonRomAngleRange? = null,
    val danger: SkeletonRomAngleRange? = null,
) {
    val effectiveMinDeg: Float
        get() = listOfNotNull(perfect, normal, pad).minOf { it.minDeg }

    val effectiveMaxDeg: Float
        get() = listOfNotNull(perfect, normal, pad).maxOf { it.maxDeg }

    val outermostMinDeg: Float
        get() = listOfNotNull(perfect, normal, pad, warning, danger).minOf { it.minDeg }

    val outermostMaxDeg: Float
        get() = listOfNotNull(perfect, normal, pad, warning, danger).maxOf { it.maxDeg }

    fun determineState(
        angleDeg: Float,
        outwardDirection: SkeletonRomOutwardDirection? = null,
    ): SkeletonRomState {
        if (perfect.contains(angleDeg)) return SkeletonRomState.PERFECT
        if (normal?.contains(angleDeg) == true) return SkeletonRomState.NORMAL
        if (pad?.contains(angleDeg) == true) return SkeletonRomState.PAD
        if (danger?.contains(angleDeg) == true) return SkeletonRomState.DANGER
        if (warning?.contains(angleDeg) == true) return SkeletonRomState.WARNING
        return outwardDirection?.let(::outermostState) ?: SkeletonRomState.WARNING
    }

    private fun outermostState(direction: SkeletonRomOutwardDirection): SkeletonRomState =
        when (direction) {
            SkeletonRomOutwardDirection.TOWARDS_HIGH -> {
                listOfNotNull(
                    SkeletonRomState.PERFECT to perfect.maxDeg,
                    normal?.let { SkeletonRomState.NORMAL to it.maxDeg },
                    pad?.let { SkeletonRomState.PAD to it.maxDeg },
                    warning?.let { SkeletonRomState.WARNING to it.maxDeg },
                    danger?.let { SkeletonRomState.DANGER to it.maxDeg },
                ).maxBy { it.second }.first
            }
            SkeletonRomOutwardDirection.TOWARDS_LOW -> {
                listOfNotNull(
                    SkeletonRomState.PERFECT to perfect.minDeg,
                    normal?.let { SkeletonRomState.NORMAL to it.minDeg },
                    pad?.let { SkeletonRomState.PAD to it.minDeg },
                    warning?.let { SkeletonRomState.WARNING to it.minDeg },
                    danger?.let { SkeletonRomState.DANGER to it.minDeg },
                ).minBy { it.second }.first
            }
        }
}

enum class SkeletonRomOutwardDirection {
    TOWARDS_HIGH,
    TOWARDS_LOW,
}

/**
 * Visual spec for a ROM range indicator on the skeleton overlay.
 * Positions are normalized 0-1 in preview space.
 */
data class SkeletonRomIndicator(
    val jointCode: String,
    val style: SkeletonRomIndicatorStyle = SkeletonRomIndicatorStyle.ARC,
    val centerX: Float,
    val centerY: Float,
    val upperEndX: Float = centerX,
    val upperEndY: Float = centerY,
    val lowerEndX: Float = centerX,
    val lowerEndY: Float = centerY,
    val limbEndX: Float = lowerEndX,
    val limbEndY: Float = lowerEndY,
    val currentAngleDeg: Float,
    val rangeMinDeg: Float,
    val rangeMaxDeg: Float,
    val trackMinDeg: Float = 0f,
    val trackMaxDeg: Float = 180f,
    val markerColorArgb: Long = 0xFF4FC3F7,
    val trackColorArgb: Long = 0x40FFFFFF,
    val currentState: SkeletonRomState = SkeletonRomState.NORMAL,
    val upStateRanges: SkeletonRomStateRanges? = null,
    val downStateRanges: SkeletonRomStateRanges? = null,
    val invertAngles: Boolean = false,
    val isHoldRange: Boolean = false,
    val isPrimary: Boolean = true,
    val dimmed: Boolean = false,
    val showCurrentMarker: Boolean = true,
)
