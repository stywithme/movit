package com.movit.core.training.engine.evaluation

import com.movit.core.training.config.JointRole
import com.movit.core.training.config.OutwardDirection
import com.movit.core.training.config.StateRanges
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.getPhaseRange
import com.movit.core.training.config.getStateHoldRange
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.ZoneType
import com.movit.core.training.engine.policy.StabilityPolicy

class JointEvaluator(
    private val trackedJoints: List<TrackedJoint>,
    private val policy: StabilityPolicy = StabilityPolicy.default(),
) {
    private val previousStates = mutableMapOf<String, JointState>()
    private val dangerFrameCounts = mutableMapOf<String, Int>()
    private val previousPhases = mutableMapOf<String, Phase>()
    private val reusable = mutableMapOf<String, JointEval>()

    fun reset() {
        previousStates.clear()
        dangerFrameCounts.clear()
        previousPhases.clear()
        reusable.clear()
    }

    fun evaluate(
        currentAngles: Map<String, Double>,
        currentPhase: Phase = Phase.IDLE,
    ): Map<String, JointEval> {
        reusable.clear()
        for (joint in trackedJoints) {
            val angle = currentAngles[joint.joint] ?: run {
                clearJointCaches(joint.joint)
                continue
            }
            if (!isJointActiveInPhase(joint, currentPhase)) {
                clearJointCaches(joint.joint)
                continue
            }
            reusable[joint.joint] = evaluateOne(joint, angle, currentPhase, rawAngle = angle)
        }
        return reusable.toMap()
    }

    private fun clearJointCaches(jointCode: String) {
        previousStates.remove(jointCode)
        dangerFrameCounts.remove(jointCode)
        previousPhases.remove(jointCode)
    }

    private fun isJointActiveInPhase(joint: TrackedJoint, phase: Phase): Boolean {
        if (joint.role != JointRole.SECONDARY || joint.phaseRanges == null) return true
        val phaseName = mapPhaseToName(phase) ?: return false
        return joint.getPhaseRange(phaseName) != null
    }

    private fun mapPhaseToName(phase: Phase): String? = when (phase) {
        Phase.START -> "top"
        Phase.DOWN -> "down"
        Phase.BOTTOM -> "bottom"
        Phase.UP -> "up"
        Phase.COUNT -> "all"
        Phase.IDLE -> null
    }

    private fun evaluateOne(
        joint: TrackedJoint,
        angle: Double,
        currentPhase: Phase,
        rawAngle: Double?,
    ): JointEval {
        val isPrimary = joint.role == JointRole.PRIMARY
        if (!isPrimary && joint.phaseRanges != null) {
            val lastPhase = previousPhases[joint.joint]
            if (lastPhase != null && lastPhase != currentPhase) {
                previousStates.remove(joint.joint)
                dangerFrameCounts.remove(joint.joint)
            }
            previousPhases[joint.joint] = currentPhase
        }

        val zoneType: ZoneType = if (isPrimary && joint.hasStateUpDownRanges()) {
            joint.determineZoneType(angle)
        } else {
            ZoneType.UP_ZONE
        }

        val stateRanges: StateRanges? = getApplicableStateRanges(joint, zoneType, currentPhase)
        val upStateRanges = if (joint.hasStateUpDownRanges()) joint.getStateUpRange() else stateRanges
        val downStateRanges = if (joint.hasStateUpDownRanges()) joint.getStateDownRange() else stateRanges

        val outward: OutwardDirection? = when (zoneType) {
            ZoneType.UP_ZONE -> OutwardDirection.TOWARDS_HIGH
            ZoneType.DOWN_ZONE -> OutwardDirection.TOWARDS_LOW
            ZoneType.TRANSITION -> null
        }

        val rawState: JointState = if (zoneType == ZoneType.TRANSITION) {
            JointState.TRANSITION
        } else {
            stateRanges?.determineState(angle, outward) ?: JointState.WARNING
        }

        val isOutwardFallback = outward != null && stateRanges != null && when (outward) {
            OutwardDirection.TOWARDS_HIGH -> angle > stateRanges.outermostMax
            OutwardDirection.TOWARDS_LOW -> angle < stateRanges.outermostMin
        }

        val state = applyHysteresis(joint.joint, rawState, angle, stateRanges, isOutwardFallback)

        // J-05: messages resolved lazily on throttled feedback paths (VM / engine cache).
        return JointEval(
            code = joint.joint,
            rawAngle = rawAngle,
            smoothedAngle = angle,
            zoneType = zoneType,
            state = state,
            stateRanges = stateRanges,
            upStateRanges = upStateRanges,
            downStateRanges = downStateRanges,
            isPrimary = isPrimary,
            invertIndicator = joint.invertIndicator,
        )
    }

    private fun getApplicableStateRanges(
        joint: TrackedJoint,
        zoneType: ZoneType,
        currentPhase: Phase,
    ): StateRanges? {
        if (joint.role == JointRole.SECONDARY && joint.phaseRanges != null) {
            val phaseName = mapPhaseToName(currentPhase) ?: return null
            return joint.getPhaseRange(phaseName)
        }
        return when {
            joint.hasStateHoldRange() -> joint.getStateHoldRange()
            zoneType == ZoneType.UP_ZONE && joint.hasStateUpDownRanges() -> joint.getStateUpRange()
            zoneType == ZoneType.DOWN_ZONE && joint.hasStateUpDownRanges() -> joint.getStateDownRange()
            else -> null
        }
    }

    private fun applyHysteresis(
        jointCode: String,
        rawState: JointState,
        angle: Double,
        stateRanges: StateRanges?,
        isOutwardFallback: Boolean,
    ): JointState {
        val previousState = previousStates[jointCode]
        if (previousState == null) {
            val initial = if (rawState == JointState.DANGER) JointState.WARNING else rawState
            previousStates[jointCode] = initial
            dangerFrameCounts[jointCode] = if (rawState == JointState.DANGER) 1 else 0
            return initial
        }
        if (rawState != JointState.DANGER) dangerFrameCounts[jointCode] = 0
        if (rawState == previousState) {
            if (rawState == JointState.DANGER) {
                dangerFrameCounts[jointCode] = (dangerFrameCounts[jointCode] ?: 0) + 1
            }
            return rawState
        }
        if (isOutwardFallback) {
            if (rawState == JointState.DANGER && previousState != JointState.DANGER) {
                val count = (dangerFrameCounts[jointCode] ?: 0) + 1
                dangerFrameCounts[jointCode] = count
                if (count >= policy.minDangerFrames) {
                    previousStates[jointCode] = JointState.DANGER
                    return JointState.DANGER
                }
                return previousState
            }
            previousStates[jointCode] = rawState
            if (rawState != JointState.DANGER) {
                dangerFrameCounts[jointCode] = 0
            }
            return rawState
        }
        val hysteresisDegree = policy.statePairHysteresis(previousState, rawState)
        if (rawState == JointState.DANGER && previousState != JointState.DANGER) {
            val count = (dangerFrameCounts[jointCode] ?: 0) + 1
            dangerFrameCounts[jointCode] = count
            if (count >= policy.minDangerFrames) {
                previousStates[jointCode] = JointState.DANGER
                return JointState.DANGER
            }
            return previousState
        }
        if (stateRanges != null && hysteresisDegree > 0) {
            val ok = checkTransitionConfirmed(previousState, rawState, angle, stateRanges, hysteresisDegree)
            if (!ok) return previousState
        }
        previousStates[jointCode] = rawState
        return rawState
    }

    private fun checkTransitionConfirmed(
        previousState: JointState,
        newState: JointState,
        angle: Double,
        stateRanges: StateRanges,
        hysteresisDegree: Double,
    ): Boolean {
        val minMargin = policy.minTransitionMarginDegrees
        return when (newState) {
            JointState.PERFECT -> {
                val range = stateRanges.perfect
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.NORMAL -> {
                val range = stateRanges.normal ?: return true
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.PAD -> {
                val range = stateRanges.pad ?: return true
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.WARNING -> {
                val range = stateRanges.warning
                if (range != null) {
                    angle >= (range.min + minMargin) && angle <= (range.max - minMargin)
                } else {
                    val outer = stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
                    val dMax = if (angle > outer.max) angle - outer.max else 0.0
                    val dMin = if (angle < outer.min) outer.min - angle else 0.0
                    maxOf(dMax, dMin) >= minMargin
                }
            }
            JointState.TRANSITION -> true
            else -> true
        }
    }
}
