package com.movit.core.training.engine

import com.movit.core.training.config.StateRanges

data class JointStateInfo(
    val jointCode: String,
    val state: JointState,
    val isPrimary: Boolean,
    val currentAngle: Double = 0.0,
    val currentZone: ZoneType = ZoneType.TRANSITION,
    val stateRanges: StateRanges? = null,
    val upStateRanges: StateRanges? = null,
    val downStateRanges: StateRanges? = null,
    val invertIndicator: Boolean = false,
)
