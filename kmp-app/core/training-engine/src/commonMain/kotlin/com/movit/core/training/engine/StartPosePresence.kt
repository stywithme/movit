package com.movit.core.training.engine

import com.movit.core.training.config.JointRole
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.TrackingMode
import com.movit.core.training.config.isInStartPose

/**
 * Legacy [PoseSetupGuide.allPrimaryJointsPresent] parity — which primary joints are in the
 * startPose band, and whether the full primary set (with any-side pairing) is satisfied.
 */
object StartPosePresence {
    fun primaryJoints(trackedJoints: List<TrackedJoint>): List<TrackedJoint> =
        trackedJoints
            .filter { it.role == JointRole.PRIMARY }
            .ifEmpty { trackedJoints }

    fun startPoseReadyJoints(
        primaryJoints: List<TrackedJoint>,
        currentAngles: Map<String, Double>,
    ): Set<String> =
        primaryJoints
            .mapNotNull { joint ->
                val angle = currentAngles[joint.joint] ?: return@mapNotNull null
                if (joint.isInStartPose(angle)) joint.joint else null
            }
            .toSet()

    /**
     * Per joint:
     * - Default / two_sides: this joint must be startPose-ready (angle present and in band).
     * - any_side pair, or any bilateral pair on an any-side exercise: at least one side ready.
     */
    fun allPrimaryJointsPresent(
        primaryJoints: List<TrackedJoint>,
        readyJointCodes: Set<String>,
    ): Boolean {
        if (primaryJoints.isEmpty()) return false
        val isAnySideExercise = primaryJoints.any { it.trackingMode == TrackingMode.ANY_SIDE }
        val visited = mutableSetOf<String>()
        for (joint in primaryJoints) {
            if (joint.joint in visited) continue
            val partnerCode = joint.pairedWith
            val partnerInPrimaries = partnerCode != null &&
                primaryJoints.any { it.joint == partnerCode }
            val explicitAnySidePair = partnerInPrimaries &&
                joint.trackingMode == TrackingMode.ANY_SIDE &&
                primaryJoints.any {
                    it.joint == partnerCode && it.trackingMode == TrackingMode.ANY_SIDE
                }
            val treatAsLenientPair = explicitAnySidePair ||
                (isAnySideExercise && partnerInPrimaries)
            if (treatAsLenientPair) {
                val partner = partnerCode
                visited.add(joint.joint)
                visited.add(partner)
                val ok = readyJointCodes.contains(joint.joint) ||
                    readyJointCodes.contains(partner)
                if (!ok) return false
            } else {
                visited.add(joint.joint)
                if (!readyJointCodes.contains(joint.joint)) return false
            }
        }
        return true
    }
}
