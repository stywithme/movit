package com.movit.core.training.config

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.ZoneType

fun StateRanges.isInCountedState(angle: Double): Boolean =
    perfect.contains(angle) ||
        (normal != null && normal.contains(angle)) ||
        (pad != null && pad.contains(angle))

fun TrackedJoint.getPhaseRange(phaseName: String): StateRanges? = phaseRanges?.get(phaseName)

fun TrackedJoint.resolveStateMessagesForPhase(phaseName: String?): StateMessages? {
    if (phaseName != null && role == JointRole.SECONDARY && phaseRanges != null) {
        phaseStateMessages?.get(phaseName)?.let { return it }
    }
    return stateMessages
}

fun TrackedJoint.getMessagesForState(
    state: JointState,
    zone: ZoneType,
    phaseName: String? = null,
): List<LocalizedText> {
    val message = resolveStateMessagesForPhase(phaseName)?.getMessage(state, zone)
    return if (message != null) listOf(message) else emptyList()
}

fun TrackedJoint.isInStartPose(angle: Double): Boolean =
    angle >= startPose.min && angle <= startPose.max

fun TrackedJoint.getStateHoldRange(): StateRanges =
    range ?: error("range required for hold joint $joint")

fun TrackedJoint.isInCountedState(angle: Double): Boolean = when (val s = determineState(angle)) {
    JointState.PERFECT, JointState.NORMAL, JointState.PAD -> true
    else -> false
}
