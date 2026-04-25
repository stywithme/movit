package com.trainingvalidator.poc.training.engine.testing

import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.models.JointState

/**
 * Immutable per-frame snapshot for parity testing and debugging.
 * Keep fields serializable and stable across refactors.
 */
data class TrainingEngineTrace(
    val frameIndex: Int,
    val timestampMs: Long,
    val phase: Phase,
    val repCount: Int,
    val isInStartPosition: Boolean,
    val isCountingSuspended: Boolean,
    val isVisibilityPaused: Boolean,
    /** Per-joint worst quality state this frame (for summary compare). */
    val jointStateSummary: Map<String, JointState>,
    /** String keys for events emitted this frame (type + payload hash). */
    val eventKeys: List<String>
) {
    fun toComparableString(): String = buildString {
        append("f=").append(frameIndex)
        append("|t=").append(timestampMs)
        append("|ph=").append(phase.name)
        append("|r=").append(repCount)
        append("|start=").append(isInStartPosition)
        append("|sus=").append(isCountingSuspended)
        append("|vp=").append(isVisibilityPaused)
        jointStateSummary.entries.sortedBy { it.key }.forEach { (k, v) ->
            append("|j:").append(k).append("=").append(v.name)
        }
        eventKeys.sorted().forEach { append("|e:").append(it) }
    }
}
