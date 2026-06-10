package com.movit.core.training.bilateral

/**
 * Engine input for bilateral side switching (not a network JSON DTO).
 *
 * WS-5 note: JSON contracts remain in `core:network` kotlinx.serialization;
 * this type mirrors legacy [BilateralConfig] fields for pure-Kotlin engine logic only.
 */
data class BilateralConfigInput(
    val switchMode: BilateralSwitchMode? = null,
    val switchEvery: Int? = 1,
    val startSide: String = "right",
)

enum class BilateralSwitchMode {
    EVERY_REP,
    AFTER_ALL_REPS,
}

enum class BilateralSide {
    LEFT,
    RIGHT;

    fun flip(): BilateralSide = if (this == LEFT) RIGHT else LEFT
}
