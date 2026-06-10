package com.trainingvalidator.poc.training.engine.bilateral

import android.util.Log
import com.movit.core.training.bilateral.BilateralConfigInput
import com.movit.core.training.bilateral.BilateralController as KmpBilateralController
import com.movit.core.training.bilateral.BilateralSwitchMode as KmpBilateralSwitchMode
import com.trainingvalidator.poc.training.models.BilateralConfig
import com.trainingvalidator.poc.training.models.BilateralSwitchMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns [BilateralSide] and per-rep switching for bilateral exercises.
 * Delegates to KMP [com.movit.core.training.bilateral.BilateralController].
 */
class BilateralController(
    isBilateral: Boolean,
    config: BilateralConfig?,
    targetReps: Int,
) {
    private val core = KmpBilateralController(
        isBilateral = isBilateral,
        config = config?.toKmpInput(),
        targetReps = targetReps,
    )

    val isBilateral: Boolean get() = core.isBilateral
    val startSide: BilateralSide get() = core.startSide

    private val _side = MutableStateFlow(core.currentSide)
    val side: StateFlow<BilateralSide> = _side.asStateFlow()

    val isFlipped: Boolean get() = core.isFlipped
    val currentSideCode: String get() = core.currentSideCode

    init {
        core.onSideChanged = { side ->
            _side.value = side
            Log.d(TAG, "Bilateral side switched to: $side")
        }
    }

    fun resetToConfigStart() {
        core.resetToConfigStart()
        _side.value = core.currentSide
    }

    fun onRepCounted(newCount: Int) = core.onRepCounted(newCount)

    private companion object {
        private const val TAG = "BilateralController"
    }
}

private fun BilateralConfig.toKmpInput(): BilateralConfigInput = BilateralConfigInput(
    switchMode = switchMode?.toKmp(),
    switchEvery = switchEvery,
    startSide = startSide,
)

private fun BilateralSwitchMode.toKmp(): KmpBilateralSwitchMode = when (this) {
    BilateralSwitchMode.EVERY_REP -> KmpBilateralSwitchMode.EVERY_REP
    BilateralSwitchMode.AFTER_ALL_REPS -> KmpBilateralSwitchMode.AFTER_ALL_REPS
}
