package com.trainingvalidator.poc.training.engine.bilateral

import android.util.Log
import com.trainingvalidator.poc.training.models.BilateralConfig
import com.trainingvalidator.poc.training.models.BilateralSwitchMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns [BilateralSide] and per-rep switching for bilateral exercises.
 */
class BilateralController(
    val isBilateral: Boolean,
    private val config: BilateralConfig?,
    private val targetReps: Int,
) {
    val startSide: BilateralSide = when (config?.startSide) {
        "left" -> BilateralSide.LEFT
        else -> BilateralSide.RIGHT
    }

    private var current: BilateralSide = startSide
    private val _side = MutableStateFlow(current)
    val side: StateFlow<BilateralSide> = _side.asStateFlow()

    val isFlipped: Boolean
        get() = isBilateral && current != startSide

    val currentSideCode: String
        get() = current.name.lowercase()

    fun resetToConfigStart() {
        if (!isBilateral) return
        current = startSide
        _side.value = current
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
            _side.value = current
            Log.d(TAG, "Bilateral side switched to: $current")
        }
    }

    private companion object {
        private const val TAG = "BilateralController"
    }
}
