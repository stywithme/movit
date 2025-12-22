package com.trainingvalidator.poc.analysis

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.atan2
import kotlin.math.abs

/**
 * AngleCalculator - Calculates joint angles from pose landmarks
 * 
 * This is a core component of the training validation system.
 * It calculates angles between body parts for form analysis.
 * 
 * Supports both NormalizedLandmark (raw) and SmoothedLandmark (processed).
 */
object AngleCalculator {

    /**
     * Calculate angle between three points (in degrees)
     * Uses generic Float coordinates for flexibility
     */
    private fun calculateAngleFromCoords(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        cx: Float, cy: Float
    ): Double {
        // Vector BA
        val baX = ax - bx
        val baY = ay - by
        
        // Vector BC
        val bcX = cx - bx
        val bcY = cy - by
        
        // Calculate angle using atan2
        val angleA = atan2(baY.toDouble(), baX.toDouble())
        val angleC = atan2(bcY.toDouble(), bcX.toDouble())
        
        var angle = Math.toDegrees(angleA - angleC)
        
        // Normalize to 0-180 range
        angle = abs(angle)
        if (angle > 180) {
            angle = 360 - angle
        }
        
        return angle
    }

    /**
     * Calculate angle from NormalizedLandmark (MediaPipe raw output)
     */
    fun calculateAngle(
        pointA: NormalizedLandmark,
        pointB: NormalizedLandmark,
        pointC: NormalizedLandmark
    ): Double {
        return calculateAngleFromCoords(
            pointA.x(), pointA.y(),
            pointB.x(), pointB.y(),
            pointC.x(), pointC.y()
        )
    }

    /**
     * Calculate angle from SmoothedLandmark (processed output)
     */
    fun calculateAngle(
        pointA: SmoothedLandmark,
        pointB: SmoothedLandmark,
        pointC: SmoothedLandmark
    ): Double {
        return calculateAngleFromCoords(
            pointA.x, pointA.y,
            pointB.x, pointB.y,
            pointC.x, pointC.y
        )
    }

    /**
     * Calculate angle using landmark indices (NormalizedLandmark)
     */
    fun calculateAngle(
        landmarks: List<NormalizedLandmark>,
        indexA: Int,
        indexB: Int,
        indexC: Int
    ): Double? {
        if (landmarks.size <= maxOf(indexA, indexB, indexC)) {
            return null
        }
        return calculateAngle(
            landmarks[indexA],
            landmarks[indexB],
            landmarks[indexC]
        )
    }

    /**
     * Calculate angle using SmoothedLandmark list
     */
    fun calculateAngleSmoothed(
        landmarks: List<SmoothedLandmark>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        visibilityThreshold: Float = 0.5f
    ): Double? {
        if (landmarks.size <= maxOf(indexA, indexB, indexC)) {
            return null
        }
        
        val a = landmarks[indexA]
        val b = landmarks[indexB]
        val c = landmarks[indexC]
        
        // Only calculate if all three landmarks are visible
        if (!a.isVisible(visibilityThreshold) || 
            !b.isVisible(visibilityThreshold) || 
            !c.isVisible(visibilityThreshold)) {
            return null
        }
        
        return calculateAngle(a, b, c)
    }

    /**
     * Calculate all angles from SmoothedLandmark list
     */
    fun calculateAllAnglesSmoothed(
        landmarks: List<SmoothedLandmark>,
        visibilityThreshold: Float = 0.5f
    ): JointAngles {
        return JointAngles(
            // Elbows
            leftElbow = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_ELBOW,
                BodyLandmarks.LEFT_WRIST,
                visibilityThreshold
            ),
            rightElbow = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_ELBOW,
                BodyLandmarks.RIGHT_WRIST,
                visibilityThreshold
            ),
            
            // Shoulders
            leftShoulder = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_ELBOW,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_HIP,
                visibilityThreshold
            ),
            rightShoulder = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_ELBOW,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_HIP,
                visibilityThreshold
            ),
            
            // Hips
            leftHip = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_HIP,
                BodyLandmarks.LEFT_KNEE,
                visibilityThreshold
            ),
            rightHip = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_HIP,
                BodyLandmarks.RIGHT_KNEE,
                visibilityThreshold
            ),
            
            // Knees
            leftKnee = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_HIP,
                BodyLandmarks.LEFT_KNEE,
                BodyLandmarks.LEFT_ANKLE,
                visibilityThreshold
            ),
            rightKnee = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_HIP,
                BodyLandmarks.RIGHT_KNEE,
                BodyLandmarks.RIGHT_ANKLE,
                visibilityThreshold
            ),
            
            // Ankles
            leftAnkle = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_KNEE,
                BodyLandmarks.LEFT_ANKLE,
                BodyLandmarks.LEFT_FOOT_INDEX,
                visibilityThreshold
            ),
            rightAnkle = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_KNEE,
                BodyLandmarks.RIGHT_ANKLE,
                BodyLandmarks.RIGHT_FOOT_INDEX,
                visibilityThreshold
            ),
            
            // Neck - using midpoints (simplified for smoothed version)
            neck = null, // Skip for smoothed version to avoid complexity
            
            // Spine
            spine = calculateSpineAngleSmoothed(landmarks, visibilityThreshold)
        )
    }

    /**
     * Calculate spine angle from SmoothedLandmark
     */
    private fun calculateSpineAngleSmoothed(
        landmarks: List<SmoothedLandmark>,
        visibilityThreshold: Float
    ): Double? {
        if (landmarks.size <= maxOf(
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
            BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP
        )) return null
        
        val ls = landmarks[BodyLandmarks.LEFT_SHOULDER]
        val rs = landmarks[BodyLandmarks.RIGHT_SHOULDER]
        val lh = landmarks[BodyLandmarks.LEFT_HIP]
        val rh = landmarks[BodyLandmarks.RIGHT_HIP]
        
        // Check visibility
        if (!ls.isVisible(visibilityThreshold) || !rs.isVisible(visibilityThreshold) ||
            !lh.isVisible(visibilityThreshold) || !rh.isVisible(visibilityThreshold)) {
            return null
        }
        
        // Calculate midpoints
        val shoulderMidX = (ls.x + rs.x) / 2
        val shoulderMidY = (ls.y + rs.y) / 2
        val hipMidX = (lh.x + rh.x) / 2
        val hipMidY = (lh.y + rh.y) / 2
        
        // Calculate angle from vertical
        val dx = shoulderMidX - hipMidX
        val dy = shoulderMidY - hipMidY
        
        val angleFromVertical = 90 - abs(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())))
        
        return abs(angleFromVertical)
    }

    /**
     * Calculate all important joint angles for a pose
     */
    fun calculateAllAngles(landmarks: List<NormalizedLandmark>): JointAngles {
        return JointAngles(
            // Elbows
            leftElbow = calculateAngle(
                landmarks,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_ELBOW,
                BodyLandmarks.LEFT_WRIST
            ),
            rightElbow = calculateAngle(
                landmarks,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_ELBOW,
                BodyLandmarks.RIGHT_WRIST
            ),
            
            // Shoulders
            leftShoulder = calculateAngle(
                landmarks,
                BodyLandmarks.LEFT_ELBOW,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_HIP
            ),
            rightShoulder = calculateAngle(
                landmarks,
                BodyLandmarks.RIGHT_ELBOW,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_HIP
            ),
            
            // Hips
            leftHip = calculateAngle(
                landmarks,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_HIP,
                BodyLandmarks.LEFT_KNEE
            ),
            rightHip = calculateAngle(
                landmarks,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_HIP,
                BodyLandmarks.RIGHT_KNEE
            ),
            
            // Knees
            leftKnee = calculateAngle(
                landmarks,
                BodyLandmarks.LEFT_HIP,
                BodyLandmarks.LEFT_KNEE,
                BodyLandmarks.LEFT_ANKLE
            ),
            rightKnee = calculateAngle(
                landmarks,
                BodyLandmarks.RIGHT_HIP,
                BodyLandmarks.RIGHT_KNEE,
                BodyLandmarks.RIGHT_ANKLE
            ),
            
            // Ankles
            leftAnkle = calculateAngle(
                landmarks,
                BodyLandmarks.LEFT_KNEE,
                BodyLandmarks.LEFT_ANKLE,
                BodyLandmarks.LEFT_FOOT_INDEX
            ),
            rightAnkle = calculateAngle(
                landmarks,
                BodyLandmarks.RIGHT_KNEE,
                BodyLandmarks.RIGHT_ANKLE,
                BodyLandmarks.RIGHT_FOOT_INDEX
            ),
            
            // Neck (head tilt relative to shoulders)
            neck = calculateAngle(
                landmarks[BodyLandmarks.NOSE],
                midpoint(landmarks[BodyLandmarks.LEFT_SHOULDER], landmarks[BodyLandmarks.RIGHT_SHOULDER]),
                midpoint(landmarks[BodyLandmarks.LEFT_HIP], landmarks[BodyLandmarks.RIGHT_HIP])
            ),
            
            // Spine angle (torso lean)
            spine = calculateSpineAngle(landmarks)
        )
    }

    /**
     * Calculate spine angle (how much the torso is leaning)
     * 0 = upright, 90 = horizontal
     */
    private fun calculateSpineAngle(landmarks: List<NormalizedLandmark>): Double? {
        val shoulderMid = midpoint(
            landmarks[BodyLandmarks.LEFT_SHOULDER],
            landmarks[BodyLandmarks.RIGHT_SHOULDER]
        )
        val hipMid = midpoint(
            landmarks[BodyLandmarks.LEFT_HIP],
            landmarks[BodyLandmarks.RIGHT_HIP]
        )
        
        // Calculate angle from vertical
        val dx = shoulderMid.x() - hipMid.x()
        val dy = shoulderMid.y() - hipMid.y()
        
        // atan2 gives angle from horizontal, we want from vertical
        val angleFromVertical = 90 - abs(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())))
        
        return abs(angleFromVertical)
    }

    /**
     * Create a virtual midpoint landmark between two landmarks
     */
    private fun midpoint(a: NormalizedLandmark, b: NormalizedLandmark): NormalizedLandmark {
        return NormalizedLandmark.create(
            (a.x() + b.x()) / 2,
            (a.y() + b.y()) / 2,
            (a.z() + b.z()) / 2
        )
    }

    /**
     * Apply smoothing filter to reduce jitter
     * Uses simple exponential moving average
     */
    class AngleSmoother(private val smoothingFactor: Float = 0.3f) {
        private val previousAngles = mutableMapOf<String, Double>()
        
        fun smooth(angleName: String, currentAngle: Double): Double {
            val previous = previousAngles[angleName]
            val smoothed = if (previous != null) {
                previous + smoothingFactor * (currentAngle - previous)
            } else {
                currentAngle
            }
            previousAngles[angleName] = smoothed
            return smoothed
        }
        
        fun reset() {
            previousAngles.clear()
        }
    }
}

/**
 * Data class holding all calculated joint angles
 */
data class JointAngles(
    // Arms
    val leftElbow: Double?,
    val rightElbow: Double?,
    val leftShoulder: Double?,
    val rightShoulder: Double?,
    
    // Torso
    val leftHip: Double?,
    val rightHip: Double?,
    val neck: Double?,
    val spine: Double?,
    
    // Legs
    val leftKnee: Double?,
    val rightKnee: Double?,
    val leftAnkle: Double?,
    val rightAnkle: Double?
) {
    /**
     * Get angle by name
     */
    fun getAngle(name: String): Double? {
        return when (name.lowercase()) {
            "left_elbow" -> leftElbow
            "right_elbow" -> rightElbow
            "left_shoulder" -> leftShoulder
            "right_shoulder" -> rightShoulder
            "left_hip" -> leftHip
            "right_hip" -> rightHip
            "left_knee" -> leftKnee
            "right_knee" -> rightKnee
            "left_ankle" -> leftAnkle
            "right_ankle" -> rightAnkle
            "neck" -> neck
            "spine" -> spine
            else -> null
        }
    }

    /**
     * Get all angles as a map
     */
    fun toMap(): Map<String, Double> {
        return mapOf(
            "left_elbow" to (leftElbow ?: 0.0),
            "right_elbow" to (rightElbow ?: 0.0),
            "left_shoulder" to (leftShoulder ?: 0.0),
            "right_shoulder" to (rightShoulder ?: 0.0),
            "left_hip" to (leftHip ?: 0.0),
            "right_hip" to (rightHip ?: 0.0),
            "left_knee" to (leftKnee ?: 0.0),
            "right_knee" to (rightKnee ?: 0.0),
            "left_ankle" to (leftAnkle ?: 0.0),
            "right_ankle" to (rightAnkle ?: 0.0),
            "neck" to (neck ?: 0.0),
            "spine" to (spine ?: 0.0)
        )
    }
    
    override fun toString(): String {
        return buildString {
            appendLine("=== Joint Angles ===")
            appendLine("Left Elbow: ${leftElbow?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Right Elbow: ${rightElbow?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Left Shoulder: ${leftShoulder?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Right Shoulder: ${rightShoulder?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Left Hip: ${leftHip?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Right Hip: ${rightHip?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Left Knee: ${leftKnee?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Right Knee: ${rightKnee?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Spine: ${spine?.let { "%.1f°".format(it) } ?: "N/A"}")
        }
    }
}
