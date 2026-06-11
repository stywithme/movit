package com.movit.core.training.blueprint

import com.movit.core.training.engine.PhaseJointConfig

data class BlueprintJointConfig(
    override val jointCode: String,
    val upMin: Double,
    val upMax: Double,
    val downMin: Double,
    val downMax: Double,
) : PhaseJointConfig {
    override fun hasUpDownRanges(): Boolean = true
    override fun hasHoldRange(): Boolean = false
    override fun upRangeEffectiveMin(): Double = upMin
    override fun upRangeOutermostMax(): Double = upMax
    override fun downRangeOutermostMin(): Double = downMin
    override fun downRangeEffectiveMax(): Double = downMax
    override fun holdRangeOutermostMin(): Double = 0.0
    override fun holdRangeEffectiveMax(): Double = 0.0
}
