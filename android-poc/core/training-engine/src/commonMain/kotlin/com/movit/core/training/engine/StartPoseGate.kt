package com.movit.core.training.engine

import com.movit.core.training.config.JointRole
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.getStateHoldRange
import com.movit.core.training.config.isInCountedState
import com.movit.core.training.config.isInStartPose
import com.movit.core.training.engine.policy.StabilityPolicy
import kotlin.math.ceil

class StartPoseGate(
    private val trackedJoints: List<TrackedJoint>,
    stabilityPolicy: StabilityPolicy = StabilityPolicy.default(),
) {
    @Suppress("unused")
    private val boundaryBuffer: Double = stabilityPolicy.boundaryBuffer

    /**
     * Pre-run setup / countdown — config [TrackedJoint.startPose] box with legacy
     * [StartPosePresence.allPrimaryJointsPresent] any-side pairing.
     */
    fun isInStartPose(currentAngles: Map<String, Double>): Boolean {
        val primaryJoints = StartPosePresence.primaryJoints(trackedJoints)
        val ready = StartPosePresence.startPoseReadyJoints(primaryJoints, currentAngles)
        return StartPosePresence.allPrimaryJointsPresent(primaryJoints, ready)
    }

    /**
     * Countdown guard — generous [TrackedJoint.startPose] box (Legacy `isStartPoseRoughlyValid`).
     * Skips invisible joints; visible joints must stay within tolerance; enforces joint presence.
     */
    fun isStartPoseRoughlyValid(
        currentAngles: Map<String, Double>,
        toleranceDegrees: Double,
        minJointPresenceRatio: Double = 0.6,
        requireAllPrimaryPresent: Boolean = true,
    ): Boolean {
        val totalTracked = trackedJoints.size
        if (totalTracked == 0) return true

        val primaryJointCodes = trackedJoints
            .filter { it.role == JointRole.PRIMARY }
            .map { it.joint }
            .toSet()

        val minVisibleJoints = if (totalTracked == 1) {
            1
        } else {
            ceil(totalTracked * minJointPresenceRatio).toInt().coerceAtLeast(2)
        }

        var checkedCount = 0
        var visiblePrimaryCount = 0
        for (joint in trackedJoints) {
            val currentAngle = currentAngles[joint.joint] ?: continue
            checkedCount++
            if (joint.joint in primaryJointCodes) {
                visiblePrimaryCount++
            }
            val min = joint.startPose.min - toleranceDegrees
            val max = joint.startPose.max + toleranceDegrees
            if (currentAngle < min || currentAngle > max) return false
        }

        if (requireAllPrimaryPresent && primaryJointCodes.isNotEmpty() &&
            visiblePrimaryCount < primaryJointCodes.size
        ) {
            return false
        }

        if (!requireAllPrimaryPresent && primaryJointCodes.isNotEmpty()) {
            val minPrimaryVisible = ceil(primaryJointCodes.size * minJointPresenceRatio)
                .toInt()
                .coerceAtLeast(1)
            if (visiblePrimaryCount < minPrimaryVisible) return false
        }

        return checkedCount >= minVisibleJoints
    }

    /** In-run rep path — UP/hold counted bands (not used during setup). */
    fun isInStartPosition(currentAngles: Map<String, Double>): Boolean {
        var checked = 0
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            val currentAngle = currentAngles[joint.joint] ?: continue
            when {
                joint.hasStateUpDownRanges() -> {
                    val zoneType = joint.determineZoneType(currentAngle)
                    if (zoneType != ZoneType.UP_ZONE) return false
                    if (!joint.getStateUpRange().isInCountedState(currentAngle)) return false
                    checked++
                }
                joint.hasStateHoldRange() -> {
                    if (!joint.getStateHoldRange().isInCountedState(currentAngle)) return false
                    checked++
                }
                else -> continue
            }
        }
        return checked > 0
    }
}
