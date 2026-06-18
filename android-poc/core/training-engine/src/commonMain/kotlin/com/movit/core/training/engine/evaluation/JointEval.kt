package com.movit.core.training.engine.evaluation

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.StateRanges
import com.movit.core.training.engine.JointEvalInput
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.ZoneType

data class JointEval(
    override val code: String,
    val rawAngle: Double?,
    override val smoothedAngle: Double,
    override val zoneType: ZoneType,
    override val state: JointState,
    val skipReason: String? = null,
    val stateRanges: StateRanges? = null,
    val upStateRanges: StateRanges? = null,
    val downStateRanges: StateRanges? = null,
    val messages: List<LocalizedText> = emptyList(),
    override val isPrimary: Boolean = true,
    val invertIndicator: Boolean = false,
) : JointEvalInput {
    override val isScorableForRepQuality: Boolean get() = state.isScorableForRepQuality

    override fun toJointStateInfo(): JointStateInfo = JointStateInfo(
        jointCode = code,
        state = state,
        isPrimary = isPrimary,
        currentAngle = smoothedAngle,
        currentZone = zoneType,
        stateRanges = stateRanges,
        upStateRanges = upStateRanges,
        downStateRanges = downStateRanges,
        invertIndicator = invertIndicator,
    )
}

fun Map<String, JointEval>.hasAnyDangerState(): Boolean =
    values.any { it.state == JointState.DANGER }
