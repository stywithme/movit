package com.movit.core.training.visibility

internal data class LenientPair(val jointA: String, val jointB: String)

internal class VisibilityJointRules(
    trackedJoints: List<VisibilityJointConfig>,
) {
    private val primarySecondary: List<VisibilityJointConfig> =
        trackedJoints.filter {
            it.role == VisibilityJointRole.PRIMARY || it.role == VisibilityJointRole.SECONDARY
        }

    private val isAnySideExercise: Boolean =
        primarySecondary.any { it.trackingMode == VisibilityTrackingMode.ANY_SIDE }

    val strictJointCodes: Set<String>
    val lenientPairs: List<LenientPair>

    init {
        val strict = mutableSetOf<String>()
        val lenient = mutableListOf<LenientPair>()
        val seenLenientKeys = mutableSetOf<Pair<String, String>>()
        for (joint in primarySecondary) {
            val partnerCode = joint.pairedWith
            if (partnerCode != null) {
                val partner = primarySecondary.find { it.joint == partnerCode }
                val bothAnySide = joint.trackingMode == VisibilityTrackingMode.ANY_SIDE &&
                    partner != null && partner.trackingMode == VisibilityTrackingMode.ANY_SIDE
                val treatAsLenient = bothAnySide || (isAnySideExercise && partner != null)
                if (treatAsLenient) {
                    val a = minOf(joint.joint, partnerCode)
                    val b = maxOf(joint.joint, partnerCode)
                    val key = a to b
                    if (seenLenientKeys.add(key)) {
                        lenient.add(LenientPair(a, b))
                    }
                    continue
                }
            }
            strict.add(joint.joint)
        }
        strictJointCodes = strict
        lenientPairs = lenient
    }

    fun evaluate(jointVisibilities: Map<String, Float>, minVisibility: Float): List<JointVisibility> {
        val details = mutableListOf<JointVisibility>()
        for (code in strictJointCodes) {
            val visibility = jointVisibilities[code] ?: 0f
            details.add(
                JointVisibility(
                    jointName = code,
                    visibility = visibility,
                    isVisible = visibility >= minVisibility,
                ),
            )
        }
        for (pair in lenientPairs) {
            val visibilityA = jointVisibilities[pair.jointA] ?: 0f
            val visibilityB = jointVisibilities[pair.jointB] ?: 0f
            val pairOk = visibilityA >= minVisibility || visibilityB >= minVisibility
            details.add(JointVisibility(pair.jointA, visibilityA, pairOk))
            details.add(JointVisibility(pair.jointB, visibilityB, pairOk))
        }
        return details
    }
}
