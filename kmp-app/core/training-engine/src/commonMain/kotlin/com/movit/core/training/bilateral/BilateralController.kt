package com.movit.core.training.bilateral

/**
 * Owns [BilateralSide] and per-rep switching for bilateral exercises.
 */
class BilateralController(
    val isBilateral: Boolean,
    private val config: BilateralConfigInput?,
    private val targetReps: Int,
) {
    val startSide: BilateralSide = when (config?.startSide) {
        "left" -> BilateralSide.LEFT
        else -> BilateralSide.RIGHT
    }

    private var current: BilateralSide = startSide

    var onSideChanged: ((BilateralSide) -> Unit)? = null

    val currentSide: BilateralSide
        get() = current

    val isFlipped: Boolean
        get() = isBilateral && current != startSide

    val currentSideCode: String
        get() = current.name.lowercase()

    fun resetToConfigStart() {
        if (!isBilateral) return
        current = startSide
        onSideChanged?.invoke(current)
    }

    /**
     * After a rep is counted, optionally flip the active side (every N reps).
     */
    fun onRepCounted(newCount: Int) {
        if (!isBilateral) return
        val every = when (config?.switchMode) {
            BilateralSwitchMode.EVERY_REP -> 1
            BilateralSwitchMode.AFTER_ALL_REPS -> targetReps.coerceAtLeast(1)
            null -> (config?.switchEvery ?: 1).coerceAtLeast(1)
        }
        if (newCount > 0 && newCount % every == 0) {
            current = current.flip()
            onSideChanged?.invoke(current)
        }
    }
}
