package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.training.models.AngleRange
import com.trainingvalidator.poc.training.models.ErrorType
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.models.StateRanges
import com.trainingvalidator.poc.training.models.TrackedJoint

/**
 * Build [JointError] rows from per-joint [JointStateInfo] (angle-based form errors only).
 * Stateless — each call allocates its own list (no shared buffer across frames/sessions).
 */
object JointErrorCollection {

    private val acceptableStates = setOf(
        JointState.PERFECT,
        JointState.NORMAL,
        JointState.PAD,
        JointState.TRANSITION
    )

    fun collectJointErrors(
        trackedJoints: List<TrackedJoint>,
        stateInfos: Map<String, JointStateInfo>
    ): List<JointError> = collectJointErrors(
        trackedJointsByCode = trackedJoints.associateBy { it.joint },
        stateInfos = stateInfos
    )

    fun collectJointErrors(
        trackedJointsByCode: Map<String, TrackedJoint>,
        stateInfos: Map<String, JointStateInfo>
    ): List<JointError> {
        val errors = ArrayList<JointError>()
        for ((jointCode, stateInfo) in stateInfos) {
            val joint = trackedJointsByCode[jointCode] ?: continue
            if (stateInfo.state in acceptableStates) continue
            val expectedRange = resolveExpectedRangeForState(stateInfo.stateRanges, stateInfo.state)
            val errorType = resolveErrorType(
                angle = stateInfo.currentAngle,
                stateRanges = stateInfo.stateRanges,
                expectedRange = expectedRange
            )
            val errorMessage = stateInfo.messages.firstOrNull()
                ?: joint.stateMessages?.getMessage(stateInfo.state, stateInfo.currentZone)
                ?: joint.getStartPoseMessage(errorType)
            errors.add(
                JointError(
                    jointCode = jointCode,
                    errorType = errorType,
                    actualAngle = stateInfo.currentAngle,
                    expectedMin = expectedRange.min,
                    expectedMax = expectedRange.max,
                    message = errorMessage,
                    state = stateInfo.state,
                    isPrimary = stateInfo.isPrimary
                )
            )
        }
        return errors
    }
}

private fun resolveExpectedRangeForState(stateRanges: StateRanges?, state: JointState): AngleRange {
    if (stateRanges == null) return AngleRange(0.0, 180.0)
    return when (state) {
        JointState.PERFECT -> stateRanges.perfect
        JointState.NORMAL -> stateRanges.normal ?: stateRanges.perfect
        JointState.PAD -> stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
        JointState.WARNING -> stateRanges.warning
            ?: stateRanges.pad
            ?: stateRanges.normal
            ?: stateRanges.perfect
        JointState.DANGER -> stateRanges.danger
            ?: stateRanges.warning
            ?: stateRanges.pad
            ?: stateRanges.normal
            ?: stateRanges.perfect
        JointState.TRANSITION -> AngleRange(stateRanges.effectiveMin, stateRanges.effectiveMax)
    }
}

private fun resolveErrorType(
    angle: Double,
    stateRanges: StateRanges?,
    expectedRange: AngleRange
): ErrorType {
    stateRanges?.let { ranges ->
        if (angle > ranges.effectiveMax) return ErrorType.TOO_HIGH
        if (angle < ranges.effectiveMin) return ErrorType.TOO_LOW
    }
    if (angle > expectedRange.max) return ErrorType.TOO_HIGH
    if (angle < expectedRange.min) return ErrorType.TOO_LOW
    val midpoint = (expectedRange.min + expectedRange.max) / 2.0
    return if (angle >= midpoint) ErrorType.TOO_HIGH else ErrorType.TOO_LOW
}
