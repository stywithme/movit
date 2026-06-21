package com.movit.core.training.engine

import com.movit.core.training.config.JointRole
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.TrackingMode
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
        val totalTrackedUnits = groupedJointUnitCount(trackedJoints)

        val primaryJointCodes = trackedJoints
            .filter { it.role == JointRole.PRIMARY }
            .map { it.joint }
            .toSet()

        val minVisibleJoints = if (totalTrackedUnits == 1) {
            1
        } else {
            ceil(totalTrackedUnits * minJointPresenceRatio).toInt().coerceAtLeast(2)
        }

        val checkedJointCodes = mutableSetOf<String>()
        for (joint in trackedJoints) {
            val currentAngle = currentAngles[joint.joint] ?: continue
            checkedJointCodes.add(joint.joint)
            val min = joint.startPose.min - toleranceDegrees
            val max = joint.startPose.max + toleranceDegrees
            if (currentAngle < min || currentAngle > max) return false
        }

        val primaryJoints = trackedJoints.filter { it.role == JointRole.PRIMARY }
        if (requireAllPrimaryPresent && primaryJoints.isNotEmpty() &&
            !StartPosePresence.allPrimaryJointsPresent(primaryJoints, checkedJointCodes)
        ) {
            return false
        }

        if (!requireAllPrimaryPresent && primaryJointCodes.isNotEmpty()) {
            val totalPrimaryUnits = groupedJointUnitCount(primaryJoints)
            val checkedPrimaryUnits = checkedGroupedJointUnitCount(primaryJoints, checkedJointCodes)
            val minPrimaryVisible = ceil(totalPrimaryUnits * minJointPresenceRatio)
                .toInt()
                .coerceAtLeast(1)
            if (checkedPrimaryUnits < minPrimaryVisible) return false
        }

        return checkedGroupedJointUnitCount(trackedJoints, checkedJointCodes) >= minVisibleJoints
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

    private fun groupedJointUnitCount(joints: List<TrackedJoint>): Int =
        checkedGroupedJointUnitCount(joints, joints.map { it.joint }.toSet())

    private fun checkedGroupedJointUnitCount(
        joints: List<TrackedJoint>,
        checkedJointCodes: Set<String>,
    ): Int {
        val isAnySideExercise = joints.any { it.trackingMode == TrackingMode.ANY_SIDE }
        val visited = mutableSetOf<String>()
        var count = 0
        for (joint in joints) {
            if (joint.joint in visited) continue
            val partnerCode = joint.pairedWith
            val partner = partnerCode?.let { code -> joints.find { it.joint == code } }
            val treatAsLenientPair = partnerCode != null &&
                partner != null &&
                (isAnySideExercise ||
                    (joint.trackingMode == TrackingMode.ANY_SIDE &&
                        partner.trackingMode == TrackingMode.ANY_SIDE))
            if (treatAsLenientPair) {
                visited.add(joint.joint)
                visited.add(partnerCode)
                if (joint.joint in checkedJointCodes || partnerCode in checkedJointCodes) {
                    count++
                }
            } else {
                visited.add(joint.joint)
                if (joint.joint in checkedJointCodes) count++
            }
        }
        return count
    }
}
