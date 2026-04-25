package com.trainingvalidator.poc.training.engine.evaluation

import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.models.StateRanges
import com.trainingvalidator.poc.training.models.ZoneType
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * Single per-joint frame evaluation: superset of [JointStateInfo] with optional
 * [skipReason] and separate raw vs smoothed angles.
 */
data class JointEval(
    val code: String,
    val rawAngle: Double?,
    val smoothedAngle: Double,
    val zoneType: ZoneType,
    val state: JointState,
    val skipReason: String? = null,
    val stateRanges: StateRanges? = null,
    val upStateRanges: StateRanges? = null,
    val downStateRanges: StateRanges? = null,
    val messages: List<LocalizedText> = emptyList(),
    val isPrimary: Boolean = true,
    val invertIndicator: Boolean = false
) {
    val isScorableForRepQuality: Boolean
        get() = state.isScorableForRepQuality

    fun toJointStateInfo(): JointStateInfo = JointStateInfo.create(
        jointCode = code,
        state = state,
        isPrimary = isPrimary,
        currentAngle = smoothedAngle,
        currentZone = zoneType,
        stateRanges = stateRanges,
        upStateRanges = upStateRanges,
        downStateRanges = downStateRanges,
        messages = messages,
        invertIndicator = invertIndicator
    )
}
