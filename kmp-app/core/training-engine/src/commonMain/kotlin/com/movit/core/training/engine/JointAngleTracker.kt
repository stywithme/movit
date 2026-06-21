package com.movit.core.training.engine

import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.TrackingMode
import com.movit.core.training.engine.policy.StabilityPolicy
import com.movit.core.training.geometry.JointLandmarkMapping
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark

class JointAngleTracker(
    private val trackedJoints: List<TrackedJoint>,
    private val stabilityPolicy: StabilityPolicy = StabilityPolicy.default(),
) {
    companion object {
        val MIRROR_MAP: Map<String, String> = mapOf(
            "left_elbow" to "right_elbow", "right_elbow" to "left_elbow",
            "left_shoulder" to "right_shoulder", "right_shoulder" to "left_shoulder",
            "left_shoulder_cross" to "right_shoulder_cross", "right_shoulder_cross" to "left_shoulder_cross",
            "left_wrist" to "right_wrist", "right_wrist" to "left_wrist",
            "left_hip" to "right_hip", "right_hip" to "left_hip",
            "left_hip_cross" to "right_hip_cross", "right_hip_cross" to "left_hip_cross",
            "left_knee" to "right_knee", "right_knee" to "left_knee",
            "left_ankle" to "right_ankle", "right_ankle" to "left_ankle",
        )

        fun mirrorJointCode(jointCode: String): String = MIRROR_MAP[jointCode] ?: jointCode

        private val JOINT_ANGLE_MAP: Map<String, (JointAngles) -> Double?> = mapOf(
            "left_elbow" to { it.leftElbow },
            "right_elbow" to { it.rightElbow },
            "left_shoulder" to { it.leftShoulder },
            "right_shoulder" to { it.rightShoulder },
            "left_shoulder_cross" to { it.leftShoulderCross },
            "right_shoulder_cross" to { it.rightShoulderCross },
            "left_wrist" to { it.leftWrist },
            "right_wrist" to { it.rightWrist },
            "left_hip" to { it.leftHip },
            "right_hip" to { it.rightHip },
            "left_hip_cross" to { it.leftHipCross },
            "right_hip_cross" to { it.rightHipCross },
            "neck" to { it.neckLeft },
            "neck_left" to { it.neckLeft },
            "neck_right" to { it.neckRight },
            "neck_spine" to { it.neckSpine },
            "spine" to { it.spine },
            "left_knee" to { it.leftKnee },
            "right_knee" to { it.rightKnee },
            "left_ankle" to { it.leftAnkle },
            "right_ankle" to { it.rightAnkle },
        )
    }

    val primaryJointCodes: Set<String> = trackedJoints
        .filter { it.role == com.movit.core.training.config.JointRole.PRIMARY }
        .map { it.joint }
        .toSet()

    fun extractTrackedAngles(
        angles: JointAngles,
        isFlipped: Boolean = false,
        landmarks: List<Landmark>? = null,
        isFrontCamera: Boolean = false,
    ): TrackedAnglesExtractResult {
        val map = buildAngleMap(angles, isFlipped)
        if (landmarks == null || landmarks.size < 33) {
            return TrackedAnglesExtractResult(map, emptySet())
        }
        val threshold = stabilityPolicy.anySideVisibilityThreshold
        val strongMin = stabilityPolicy.anySideStrongMinVisibility
        val tiebreak = stabilityPolicy.anySideTiebreakGap
        val skipped = mutableSetOf<String>()
        val processedPairs = mutableSetOf<Pair<String, String>>()
        val isAnySideExercise = trackedJoints.any { it.trackingMode == TrackingMode.ANY_SIDE }

        fun anatomical(code: String): String = if (isFlipped) mirrorJointCode(code) else code

        for (joint in trackedJoints) {
            val partnerCode = joint.pairedWith ?: continue
            val partnerCfg = trackedJoints.find { it.joint == partnerCode } ?: continue
            val bothAnySide = joint.trackingMode == TrackingMode.ANY_SIDE &&
                partnerCfg.trackingMode == TrackingMode.ANY_SIDE
            if (!bothAnySide && !isAnySideExercise) continue

            val a = minOf(joint.joint, partnerCode)
            val b = maxOf(joint.joint, partnerCode)
            val key = a to b
            if (key in processedPairs) continue
            processedPairs.add(key)

            val vA = JointLandmarkMapping.computeJointVisibility(anatomical(a), landmarks, isFrontCamera)
            val vB = JointLandmarkMapping.computeJointVisibility(anatomical(b), landmarks, isFrontCamera)
            val belowA = vA < threshold
            val belowB = vB < threshold
            val strongA = vA >= strongMin
            val strongB = vB >= strongMin
            when {
                belowA && !belowB -> { map.remove(a); skipped.add(a) }
                belowB && !belowA -> { map.remove(b); skipped.add(b) }
                belowA && belowB -> {
                    val diff = vA - vB
                    when {
                        diff > tiebreak -> { map.remove(b); skipped.add(b) }
                        diff < -tiebreak -> { map.remove(a); skipped.add(a) }
                    }
                }
                strongA && !strongB -> { map.remove(b); skipped.add(b) }
                strongB && !strongA -> { map.remove(a); skipped.add(a) }
            }
        }
        return TrackedAnglesExtractResult(map, skipped)
    }

    private fun buildAngleMap(angles: JointAngles, isFlipped: Boolean): MutableMap<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (joint in trackedJoints) {
            val lookupCode = if (isFlipped) mirrorJointCode(joint.joint) else joint.joint
            val angleValue = getAngleForJoint(angles, lookupCode) ?: continue
            result[joint.joint] = angleValue
        }
        return result
    }

    fun getAngleForJoint(angles: JointAngles, jointCode: String): Double? {
        val getter = JOINT_ANGLE_MAP[jointCode] ?: return null
        return getter(angles)
    }
}

data class TrackedAnglesExtractResult(
    val angles: Map<String, Double>,
    val skippedJointCodes: Set<String>,
)
