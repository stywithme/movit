package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.training.engine.policy.StabilityPolicy
import com.trainingvalidator.poc.training.models.ErrorType
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.JointRole
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.TrackedJoint
import com.trainingvalidator.poc.training.models.ZoneType

/**
 * Start checks use **two different** angle semantics; do not conflate them.
 *
 * 1) **Config `TrackedJoint.startPose` (min/max)**
 *    - Used by [isInStartPose] and [getStartPoseFeedback].
 *    - Aligns with **pre-run** setup / countdown: [com.trainingvalidator.poc.ui.training.PoseSetupGuide]
 *      and rough validation on the way into training. Not what drives the phase machine once reps run.
 *
 * 2) **In-run "ready" / start of rep path** – [isInStartPosition]
 *    - For PRIMARY with up/down: user must be in **UP zone** and angle must be in the **counted** band
 *      of the up-range (see [isInCountedState]).
 *    - For PRIMARY with hold only: must be in **counted** state in the hold range.
 *    - Drives [com.trainingvalidator.poc.training.TrainingEngine._isInStartPosition] during `processFrame`;
 *      this is *not* the same predicate as the setup box check.
 *
 * [getStartPositionFeedback] and [getStartPoseFeedback] build [JointError] rows for the two cases above
 * and are available if UI wants structured feedback; the live engine path uses [isInStartPosition] for state.
 */
class StartPoseGate(
    private val trackedJoints: List<TrackedJoint>,
    stabilityPolicy: StabilityPolicy = StabilityPolicy.default()
) {
    private val boundaryBuffer: Double = stabilityPolicy.boundaryBuffer

    fun isInStartPosition(currentAngles: Map<String, Double>): Boolean {
        var checked = 0
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            val currentAngle = currentAngles[joint.joint] ?: continue
            when {
                joint.hasStateUpDownRanges() -> {
                    val zoneType = joint.determineZoneType(currentAngle)
                    if (zoneType != ZoneType.UP_ZONE) return false
                    val upRange = joint.getStateUpRange()
                    if (!upRange.isInCountedState(currentAngle)) return false
                    checked++
                }
                joint.hasStateHoldRange() -> {
                    val holdRange = joint.getStateHoldRange()
                    if (!holdRange.isInCountedState(currentAngle)) return false
                    checked++
                }
                else -> continue
            }
        }
        return checked > 0
    }

    fun isInStartPose(currentAngles: Map<String, Double>): Boolean {
        var checked = 0
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            val currentAngle = currentAngles[joint.joint] ?: continue
            if (!joint.isInStartPose(currentAngle)) return false
            checked++
        }
        if (checked == 0) return false
        return true
    }

    /**
     * Structured [JointError]s for the **UP/hold "counted"** bands (see [isInStartPosition]).
     * Not used by the live [com.trainingvalidator.poc.training.TrainingEngine] path; for optional UI.
     */
    @Suppress("unused")
    fun getStartPositionFeedback(currentAngles: Map<String, Double>): List<JointError> {
        val errors = mutableListOf<JointError>()
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            val currentAngle = currentAngles[joint.joint] ?: continue
            val perfectRange = when {
                joint.hasStateUpDownRanges() -> joint.getStateUpRange().perfect
                joint.hasStateHoldRange() -> joint.getStateHoldRange().perfect
                else -> continue
            }
            if (currentAngle > perfectRange.max + boundaryBuffer) {
                errors.add(
                    JointError(
                        jointCode = joint.joint,
                        errorType = ErrorType.TOO_HIGH,
                        actualAngle = currentAngle,
                        expectedMin = perfectRange.min,
                        expectedMax = perfectRange.max,
                        message = joint.getStartPoseMessage(ErrorType.TOO_HIGH),
                        state = JointState.WARNING,
                        isPrimary = joint.role == JointRole.PRIMARY
                    )
                )
            } else if (currentAngle < perfectRange.min - boundaryBuffer) {
                errors.add(
                    JointError(
                        jointCode = joint.joint,
                        errorType = ErrorType.TOO_LOW,
                        actualAngle = currentAngle,
                        expectedMin = perfectRange.min,
                        expectedMax = perfectRange.max,
                        message = joint.getStartPoseMessage(ErrorType.TOO_LOW),
                        state = JointState.WARNING,
                        isPrimary = joint.role == JointRole.PRIMARY
                    )
                )
            }
        }
        return errors
    }

    /**
     * Structured [JointError]s for **config [TrackedJoint.startPose] box** (see [isInStartPose]).
     * Pre-run [com.trainingvalidator.poc.ui.training.PoseSetupGuide] may use a similar check; this API is
     * available if you want engine-driven messages without wiring yet.
     */
    @Suppress("unused")
    fun getStartPoseFeedback(currentAngles: Map<String, Double>): List<JointError> {
        val errors = mutableListOf<JointError>()
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            val currentAngle = currentAngles[joint.joint] ?: continue
            if (currentAngle > joint.startPose.max) {
                errors.add(
                    JointError(
                        jointCode = joint.joint,
                        errorType = ErrorType.TOO_HIGH,
                        actualAngle = currentAngle,
                        expectedMin = joint.startPose.min,
                        expectedMax = joint.startPose.max,
                        message = joint.getStartPoseMessage(ErrorType.TOO_HIGH),
                        state = JointState.WARNING,
                        isPrimary = true
                    )
                )
            } else if (currentAngle < joint.startPose.min) {
                errors.add(
                    JointError(
                        jointCode = joint.joint,
                        errorType = ErrorType.TOO_LOW,
                        actualAngle = currentAngle,
                        expectedMin = joint.startPose.min,
                        expectedMax = joint.startPose.max,
                        message = joint.getStartPoseMessage(ErrorType.TOO_LOW),
                        state = JointState.WARNING,
                        isPrimary = true
                    )
                )
            }
        }
        return errors
    }
}
