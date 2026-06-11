package com.movit.core.training.session

import com.movit.core.training.config.JointRole
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.isInStartPose
import com.movit.core.training.position.PositionMessageResolver

enum class SetupGuidanceLevel {
    GREEN,
    YELLOW,
    RED,
}

enum class SetupGuidanceDirection {
    RAISE,
    LOWER,
}

data class JointSetupGuidance(
    val jointCode: String,
    val level: SetupGuidanceLevel,
    val currentAngle: Double,
    val targetMin: Double,
    val targetMax: Double,
    val distance: Double,
    val direction: SetupGuidanceDirection?,
    val message: LocalizedText,
    val isPrimary: Boolean,
)

object SetupJointGuidanceResolver {
    fun resolveWorstJoint(
        angles: Map<String, Double>,
        joints: List<TrackedJoint>,
        closeThresholdDegrees: Double,
    ): JointSetupGuidance? {
        val candidates = joints
            .filter { it.role == JointRole.PRIMARY }
            .ifEmpty { joints }
            .mapNotNull { joint ->
                val angle = angles[joint.joint] ?: return@mapNotNull null
                if (joint.isInStartPose(angle)) {
                    return@mapNotNull null
                }
                val below = joint.startPose.min - angle
                val above = angle - joint.startPose.max
                val direction = when {
                    below > 0 -> SetupGuidanceDirection.RAISE
                    above > 0 -> SetupGuidanceDirection.LOWER
                    else -> null
                }
                val distance = maxOf(below, above, 0.0)
                val level = when {
                    distance <= closeThresholdDegrees -> SetupGuidanceLevel.YELLOW
                    else -> SetupGuidanceLevel.RED
                }
                JointSetupGuidance(
                    jointCode = joint.joint,
                    level = level,
                    currentAngle = angle,
                    targetMin = joint.startPose.min,
                    targetMax = joint.startPose.max,
                    distance = distance,
                    direction = direction,
                    message = resolveMessage(joint.joint, direction, level),
                    isPrimary = joint.role == JointRole.PRIMARY,
                )
            }
        return candidates.maxByOrNull { it.distance }
    }

    fun resolveCameraTip(
        phase: SetupPhase,
        cameraTipEnabled: Boolean,
        regions: List<com.movit.core.training.position.VisibleRegion>,
    ): LocalizedText? {
        if (!cameraTipEnabled || phase != SetupPhase.REGION) return null
        return PositionMessageResolver.resolveRegionAxisWarning(regions)
    }

    private fun resolveMessage(
        jointCode: String,
        direction: SetupGuidanceDirection?,
        level: SetupGuidanceLevel,
    ): LocalizedText {
        if (level == SetupGuidanceLevel.GREEN || direction == null) {
            return LocalizedText(ar = "✓ ممتاز", en = "✓ Good")
        }
        val base = jointCode.removePrefix("right_").removePrefix("left_")
        val isRaise = direction == SetupGuidanceDirection.RAISE
        return when (base) {
            "knee" -> if (isRaise) {
                LocalizedText(ar = "افرد الركبة أكثر", en = "Straighten your knee more")
            } else {
                LocalizedText(ar = "اثنِ الركبة أكثر", en = "Bend your knee more")
            }
            "hip" -> if (isRaise) {
                LocalizedText(ar = "ارفع الورك", en = "Raise your hip")
            } else {
                LocalizedText(ar = "اخفض الورك", en = "Lower your hip")
            }
            "shoulder" -> if (isRaise) {
                LocalizedText(ar = "ارفع الكتف", en = "Raise your shoulder")
            } else {
                LocalizedText(ar = "اخفض الكتف", en = "Lower your shoulder")
            }
            "elbow" -> if (isRaise) {
                LocalizedText(ar = "ارفع الكوع", en = "Raise your elbow")
            } else {
                LocalizedText(ar = "اخفض الكوع", en = "Lower your elbow")
            }
            else -> if (isRaise) {
                LocalizedText(ar = "ارفع المفصل", en = "Raise the joint")
            } else {
                LocalizedText(ar = "اخفض المفصل", en = "Lower the joint")
            }
        }
    }
}
