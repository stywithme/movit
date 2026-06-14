package com.movit.core.training.engine

import com.movit.core.training.config.JointRole
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.getStateHoldRange
import com.movit.core.training.config.isInCountedState
import com.movit.core.training.config.isInStartPose
import com.movit.core.training.engine.policy.StabilityPolicy

class StartPoseGate(
    private val trackedJoints: List<TrackedJoint>,
    stabilityPolicy: StabilityPolicy = StabilityPolicy.default(),
) {
    @Suppress("unused")
    private val boundaryBuffer: Double = stabilityPolicy.boundaryBuffer

    /**
     * Pre-run setup / countdown — config [TrackedJoint.startPose] box (legacy [isInStartPose]).
     */
    fun isInStartPose(currentAngles: Map<String, Double>): Boolean {
        var checked = 0
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            val currentAngle = currentAngles[joint.joint] ?: continue
            if (!joint.isInStartPose(currentAngle)) return false
            checked++
        }
        return checked > 0
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
