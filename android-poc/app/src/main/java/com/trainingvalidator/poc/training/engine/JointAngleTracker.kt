package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.training.engine.policy.StabilityPolicy
import com.trainingvalidator.poc.training.models.TrackingMode
import com.trainingvalidator.poc.training.models.TrackedJoint

/**
 * JointAngleTracker - Extracts angles for tracked joints only
 * 
 * This component maps the general JointAngles from AngleCalculator
 * to only the joints that are being tracked for this exercise.
 * 
 * It also converts joint codes (e.g., "left_knee") to actual angle values.
 * 
 * Note: Uses JointLandmarkMapping as single source of truth for joint↔landmark mapping.
 */
class JointAngleTracker(
    private val trackedJoints: List<TrackedJoint>,
    private val stabilityPolicy: StabilityPolicy = StabilityPolicy.default()
) {
    
    companion object {
        private const val TAG = "JointAngleTracker"
        
        /**
         * Map of joint codes to their angle getter functions
         */
        private val JOINT_ANGLE_MAP = mapOf(
            // Arms
            "left_elbow" to { angles: JointAngles -> angles.leftElbow },
            "right_elbow" to { angles: JointAngles -> angles.rightElbow },
            "left_shoulder" to { angles: JointAngles -> angles.leftShoulder },
            "right_shoulder" to { angles: JointAngles -> angles.rightShoulder },
            "left_shoulder_cross" to { angles: JointAngles -> angles.leftShoulderCross },
            "right_shoulder_cross" to { angles: JointAngles -> angles.rightShoulderCross },
            "left_wrist" to { angles: JointAngles -> angles.leftWrist },
            "right_wrist" to { angles: JointAngles -> angles.rightWrist },
            
            // Torso
            "left_hip" to { angles: JointAngles -> angles.leftHip },
            "right_hip" to { angles: JointAngles -> angles.rightHip },
            "left_hip_cross" to { angles: JointAngles -> angles.leftHipCross },
            "right_hip_cross" to { angles: JointAngles -> angles.rightHipCross },
            "neck" to { angles: JointAngles -> angles.neckLeft },          // Alias for neck_left
            "neck_left" to { angles: JointAngles -> angles.neckLeft },
            "neck_right" to { angles: JointAngles -> angles.neckRight },
            "neck_spine" to { angles: JointAngles -> angles.neckSpine },
            "spine" to { angles: JointAngles -> angles.spine },
            
            // Legs
            "left_knee" to { angles: JointAngles -> angles.leftKnee },
            "right_knee" to { angles: JointAngles -> angles.rightKnee },
            "left_ankle" to { angles: JointAngles -> angles.leftAnkle },
            "right_ankle" to { angles: JointAngles -> angles.rightAnkle }
        )
        
        /**
         * Bilateral mirror map: maps each sided joint to its opposite side.
         * Shared joints (spine, neck, etc.) are NOT in this map.
         * Used for runtime flipping: when the exercise is configured for right_elbow
         * but we need to measure the left side, we look up left_elbow's angle instead.
         */
        val MIRROR_MAP: Map<String, String> = mapOf(
            // Arms
            "left_elbow" to "right_elbow", "right_elbow" to "left_elbow",
            "left_shoulder" to "right_shoulder", "right_shoulder" to "left_shoulder",
            "left_shoulder_cross" to "right_shoulder_cross", "right_shoulder_cross" to "left_shoulder_cross",
            "left_wrist" to "right_wrist", "right_wrist" to "left_wrist",
            // Torso
            "left_hip" to "right_hip", "right_hip" to "left_hip",
            "left_hip_cross" to "right_hip_cross", "right_hip_cross" to "left_hip_cross",
            // Legs
            "left_knee" to "right_knee", "right_knee" to "left_knee",
            "left_ankle" to "right_ankle", "right_ankle" to "left_ankle"
        )
        
        /**
         * Get the mirrored joint code. Shared joints return themselves.
         */
        fun mirrorJointCode(jointCode: String): String {
            return MIRROR_MAP[jointCode] ?: jointCode
        }
    }
    
    /**
     * Get tracked joint codes (for quick lookup)
     */
    val trackedJointCodes: Set<String> = trackedJoints.map { it.joint }.toSet()
    
    /**
     * Get primary joint codes
     */
    val primaryJointCodes: Set<String> = trackedJoints
        .filter { it.role == com.trainingvalidator.poc.training.models.JointRole.PRIMARY }
        .map { it.joint }
        .toSet()
    
    /**
     * Extract angles for tracked joints only
     * 
     * @param angles All joint angles from AngleCalculator
     * @return Map of joint code to angle value (only tracked joints)
     */
    fun extractTrackedAngles(angles: JointAngles): Map<String, Double> =
        extractTrackedAngles(angles, isFlipped = false, landmarks = null, isFrontCamera = false).angles
    
    /**
     * Extract angles for tracked joints with optional bilateral flipping.
     * When isFlipped=true, reads the OPPOSITE side's angle from JointAngles
     * but stores it under the ORIGINAL config key.
     * 
     * Example: config has "right_elbow", isFlipped=true
     *   -> reads angles.leftElbow (the mirrored side)
     *   -> stores result["right_elbow"] = leftElbowAngle
     *   -> PhaseStateMachine / joint pipeline see "right_elbow" with left side's angle
     * 
     * Shared joints (spine, neck, etc.) read from the same key regardless of flip.
     * 
     * @param angles All joint angles from AngleCalculator
     * @param isFlipped true when measuring the opposite side in bilateral mode
     * @return Map of config joint code to angle value
     */
    fun extractTrackedAngles(angles: JointAngles, isFlipped: Boolean): Map<String, Double> =
        extractTrackedAngles(angles, isFlipped, landmarks = null, isFrontCamera = false).angles

    /**
     * Extract tracked angles with optional per-frame skipping for [TrackingMode.ANY_SIDE] pairs
     * when one side's landmarks fall below [StabilityPolicy.anySideVisibilityThreshold]
     * while the partner remains visible.
     *
     * @param isFrontCamera True when landmarks were produced from a mirrored image;
     *  visibility lookups will use the mirrored raw index so the anatomical side is checked.
     */
    fun extractTrackedAngles(
        angles: JointAngles,
        isFlipped: Boolean,
        landmarks: List<SmoothedLandmark>?,
        isFrontCamera: Boolean = false
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

        fun anatomical(code: String): String = if (isFlipped) mirrorJointCode(code) else code

        // Exercise-level Any-Side mode: if any tracked joint is any_side, apply the
        // per-frame skip logic to every bilateral pair — otherwise a pair left at the
        // default `two_sides` would pollute evaluation with the occluded side's
        // hallucinated angle while the user is in a side-view.
        val isAnySideExercise = trackedJoints.any { it.trackingMode == TrackingMode.ANY_SIDE }

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
                // Clear winner: one side passes the hard threshold, the other does not
                //   → drop the weaker side regardless of how big the gap is.
                belowA && !belowB -> {
                    map.remove(a); skipped.add(a)
                }
                belowB && !belowA -> {
                    map.remove(b); skipped.add(b)
                }
                // Both below threshold (e.g. side-view where even the "visible"
                // side dips momentarily). Keep the clearly better side so phase
                // counting survives, skip the weaker one. If both are equally
                // low we keep both and let VisibilityMonitor decide to pause.
                belowA && belowB -> {
                    val diff = vA - vB
                    when {
                        diff > tiebreak -> {
                            map.remove(b); skipped.add(b)
                        }
                        diff < -tiebreak -> {
                            map.remove(a); skipped.add(a)
                        }
                    }
                }
                // Both above hard threshold but one is clearly dominant:
                // MediaPipe commonly reports the back-side limbs with a fake
                // confidence in the 0.5–0.7 "borderline" band even when they are
                // fully occluded. In that case we trust only the front-facing
                // (strong) side so phase transitions are not corrupted by the
                // hallucinated back-side angle.
                strongA && !strongB -> {
                    map.remove(b); skipped.add(b)
                }
                strongB && !strongA -> {
                    map.remove(a); skipped.add(a)
                }
                // Both clearly visible OR both borderline (frontal view / symmetrical
                // partial occlusion) — keep both sides and let Symmetry / joint eval
                // use them normally.
                else -> { /* keep both */ }
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
    
    /**
     * Extract angles for primary joints only (used for rep counting)
     */
    fun extractPrimaryAngles(angles: JointAngles): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        
        for (joint in trackedJoints) {
            if (joint.role == com.trainingvalidator.poc.training.models.JointRole.PRIMARY) {
                val angleValue = getAngleForJoint(angles, joint.joint)
                if (angleValue != null) {
                    result[joint.joint] = angleValue
                }
            }
        }
        
        return result
    }
    
    /**
     * Get average angle of primary joints (for state machine)
     */
    fun getAveragePrimaryAngle(angles: JointAngles): Double? {
        val primaryAngles = extractPrimaryAngles(angles)
        if (primaryAngles.isEmpty()) return null
        return primaryAngles.values.average()
    }
    
    /**
     * Get the angle for a specific joint code
     */
    fun getAngleForJoint(angles: JointAngles, jointCode: String): Double? {
        val getter = JOINT_ANGLE_MAP[jointCode] ?: return null
        return getter(angles)
    }
    
    /**
     * Check if a joint is being tracked
     */
    fun isTracked(jointCode: String): Boolean {
        return jointCode in trackedJointCodes
    }
    
    /**
     * Check if a joint is primary
     */
    fun isPrimary(jointCode: String): Boolean {
        return jointCode in primaryJointCodes
    }
    
    /**
     * Get TrackedJoint config by code
     */
    fun getJointConfig(jointCode: String): TrackedJoint? {
        return trackedJoints.find { it.joint == jointCode }
    }
    
    /**
     * Get landmark index for a joint (for skeleton overlay)
     * Uses JointLandmarkMapping as single source of truth
     */
    fun getLandmarkIndex(jointCode: String): Int? {
        return JointLandmarkMapping.jointToLandmark(jointCode)
    }
    
    /**
     * Get all landmark indices for tracked joints
     */
    fun getTrackedLandmarkIndices(): List<Int> {
        return trackedJoints.mapNotNull { JointLandmarkMapping.jointToLandmark(it.joint) }
    }
}

/**
 * Result of [JointAngleTracker.extractTrackedAngles] including joints intentionally
 * omitted this frame (Any-Side occlusion while partner visible).
 */
data class TrackedAnglesExtractResult(
    val angles: Map<String, Double>,
    val skippedJointCodes: Set<String>
)

/**
 * TrackedAngleResult - Result of tracking angles for one frame
 */
data class TrackedAngleResult(
    val jointCode: String,
    val angle: Double,
    val isPrimary: Boolean,
    val config: TrackedJoint
)
