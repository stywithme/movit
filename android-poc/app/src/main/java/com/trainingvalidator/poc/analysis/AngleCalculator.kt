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
     * Calculate 3D angle between three points (in degrees)
     */
    private fun calculateAngleFromCoords3D(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float
    ): Double {
        // Vector BA
        val baX = ax - bx
        val baY = ay - by
        val baZ = az - bz
        
        // Vector BC
        val bcX = cx - bx
        val bcY = cy - by
        val bcZ = cz - bz
        
        // Dot product
        val dot = baX * bcX + baY * bcY + baZ * bcZ
        
        // Magnitudes
        val magBA = kotlin.math.sqrt(baX * baX + baY * baY + baZ * baZ)
        val magBC = kotlin.math.sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)
        
        if (magBA == 0f || magBC == 0f) return 0.0
        
        val cosAngle = dot / (magBA * magBC)
        val angle = Math.toDegrees(kotlin.math.acos(cosAngle.coerceIn(-1f, 1f).toDouble()))
        
        return angle
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
     * Calculate 3D angle from SmoothedLandmark
     */
    fun calculateAngle3D(
        pointA: SmoothedLandmark,
        pointB: SmoothedLandmark,
        pointC: SmoothedLandmark
    ): Double {
        return calculateAngleFromCoords3D(
            pointA.x, pointA.y, pointA.z,
            pointB.x, pointB.y, pointB.z,
            pointC.x, pointC.y, pointC.z
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
        visibilityThreshold: Float = 0.5f,
        use3D: Boolean = false
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
        
        return if (use3D) {
            calculateAngle3D(a, b, c)
        } else {
            calculateAngle(a, b, c)
        }
    }

    /**
     * Calculate all angles from SmoothedLandmark list
     */
    fun calculateAllAnglesSmoothed(
        landmarks: List<SmoothedLandmark>,
        visibilityThreshold: Float = 0.5f,
        use3D: Boolean = false
    ): JointAngles {
        // Calculate raw angles first
        val rawAngles = JointAngles(
            // Elbows
            leftElbow = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_ELBOW,
                BodyLandmarks.LEFT_WRIST,
                visibilityThreshold,
                use3D
            ),
            rightElbow = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_ELBOW,
                BodyLandmarks.RIGHT_WRIST,
                visibilityThreshold,
                use3D
            ),
            
            // Shoulders
            leftShoulder = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_ELBOW,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_HIP,
                visibilityThreshold,
                use3D
            ),
            rightShoulder = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_ELBOW,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_HIP,
                visibilityThreshold,
                use3D
            ),
            
            // Cross Shoulders - arm angle relative to shoulder line
            // Left: Left Elbow(13) -> Left Shoulder(11) -> Right Shoulder(12)
            leftShoulderCross = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_ELBOW,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.RIGHT_SHOULDER,
                visibilityThreshold,
                use3D
            ),
            // Right: Right Elbow(14) -> Right Shoulder(12) -> Left Shoulder(11)
            rightShoulderCross = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_ELBOW,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.LEFT_SHOULDER,
                visibilityThreshold,
                use3D
            ),
            
            // Hips
            leftHip = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.LEFT_HIP,
                BodyLandmarks.LEFT_KNEE,
                visibilityThreshold,
                use3D
            ),
            rightHip = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.RIGHT_HIP,
                BodyLandmarks.RIGHT_KNEE,
                visibilityThreshold,
                use3D
            ),
            
            // Knees
            leftKnee = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_HIP,
                BodyLandmarks.LEFT_KNEE,
                BodyLandmarks.LEFT_ANKLE,
                visibilityThreshold,
                use3D
            ),
            rightKnee = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_HIP,
                BodyLandmarks.RIGHT_KNEE,
                BodyLandmarks.RIGHT_ANKLE,
                visibilityThreshold,
                use3D
            ),
            
            // Ankles
            leftAnkle = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_KNEE,
                BodyLandmarks.LEFT_ANKLE,
                BodyLandmarks.LEFT_FOOT_INDEX,
                visibilityThreshold,
                use3D
            ),
            rightAnkle = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_KNEE,
                BodyLandmarks.RIGHT_ANKLE,
                BodyLandmarks.RIGHT_FOOT_INDEX,
                visibilityThreshold,
                use3D
            ),
            
            // Neck variants - using virtual landmark (index 33 = midpoint of shoulders)
            // Each variant uses a different reference point for different camera angles
            
            // neck_left: Left Shoulder(11) -> Neck(33) -> Nose(0)
            // Best for Right Side View camera
            neckLeft = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.LEFT_SHOULDER,
                BodyLandmarks.NECK,
                BodyLandmarks.NOSE,
                visibilityThreshold,
                use3D
            ),
            
            // neck_right: Right Shoulder(12) -> Neck(33) -> Nose(0)
            // Best for Left Side View camera
            neckRight = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.RIGHT_SHOULDER,
                BodyLandmarks.NECK,
                BodyLandmarks.NOSE,
                visibilityThreshold,
                use3D
            ),
            
            // neck_spine: Spine(34) -> Neck(33) -> Nose(0)
            // Best for Front View camera
            neckSpine = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.SPINE,
                BodyLandmarks.NECK,
                BodyLandmarks.NOSE,
                visibilityThreshold,
                use3D
            ),
            
            // Spine - using virtual landmarks (neck=33, spine=34)
            // Angle: neck(33) -> spine(34) -> left_knee(25)
            // Falls back to angle-from-vertical if virtual landmarks unavailable
            spine = calculateAngleSmoothed(
                landmarks,
                BodyLandmarks.NECK,
                BodyLandmarks.SPINE,
                BodyLandmarks.LEFT_KNEE,
                visibilityThreshold,
                use3D
            ) ?: calculateSpineAngleSmoothed(landmarks, visibilityThreshold, use3D)
        )
        
        return rawAngles
    }

    /**
     * Calculate spine angle from SmoothedLandmark
     */
    private fun calculateSpineAngleSmoothed(
        landmarks: List<SmoothedLandmark>,
        visibilityThreshold: Float,
        use3D: Boolean = false
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
        
        if (use3D) {
            // Use 3D calculation for accurate spine angle
            val shoulderMidX = (ls.x + rs.x) / 2
            val shoulderMidY = (ls.y + rs.y) / 2
            val shoulderMidZ = (ls.z + rs.z) / 2
            val hipMidX = (lh.x + rh.x) / 2
            val hipMidY = (lh.y + rh.y) / 2
            val hipMidZ = (lh.z + rh.z) / 2
            
            // Calculate angle from vertical using 3D vectors
            val dx = shoulderMidX - hipMidX
            val dy = shoulderMidY - hipMidY
            val dz = shoulderMidZ - hipMidZ
            
            // Angle from vertical (0, -1, 0) direction
            val verticalDot = -dy
            val magnitude = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            
            if (magnitude == 0f) return null
            
            val cosAngle = verticalDot / magnitude
            val angleFromVertical = Math.toDegrees(kotlin.math.acos(cosAngle.coerceIn(-1f, 1f).toDouble()))
            
            return abs(angleFromVertical)
        } else {
            // 2D calculation (original)
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
    val leftShoulderCross: Double?,   // Left Elbow -> Left Shoulder -> Right Shoulder
    val rightShoulderCross: Double?,  // Right Elbow -> Right Shoulder -> Left Shoulder
    
    // Torso
    val leftHip: Double?,
    val rightHip: Double?,
    val neckLeft: Double?,     // Left Shoulder -> Neck -> Nose
    val neckRight: Double?,    // Right Shoulder -> Neck -> Nose
    val neckSpine: Double?,    // Spine -> Neck -> Nose
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
            "left_shoulder_cross" -> leftShoulderCross
            "right_shoulder_cross" -> rightShoulderCross
            "left_hip" -> leftHip
            "right_hip" -> rightHip
            "left_knee" -> leftKnee
            "right_knee" -> rightKnee
            "left_ankle" -> leftAnkle
            "right_ankle" -> rightAnkle
            "neck", "neck_left" -> neckLeft
            "neck_right" -> neckRight
            "neck_spine" -> neckSpine
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
            "left_shoulder_cross" to (leftShoulderCross ?: 0.0),
            "right_shoulder_cross" to (rightShoulderCross ?: 0.0),
            "left_hip" to (leftHip ?: 0.0),
            "right_hip" to (rightHip ?: 0.0),
            "left_knee" to (leftKnee ?: 0.0),
            "right_knee" to (rightKnee ?: 0.0),
            "left_ankle" to (leftAnkle ?: 0.0),
            "right_ankle" to (rightAnkle ?: 0.0),
            "neck_left" to (neckLeft ?: 0.0),
            "neck_right" to (neckRight ?: 0.0),
            "neck_spine" to (neckSpine ?: 0.0),
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
            appendLine("Left Shoulder Cross: ${leftShoulderCross?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Right Shoulder Cross: ${rightShoulderCross?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Left Hip: ${leftHip?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Right Hip: ${rightHip?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Left Knee: ${leftKnee?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Right Knee: ${rightKnee?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Neck (Left):  ${neckLeft?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Neck (Right): ${neckRight?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Neck (Spine): ${neckSpine?.let { "%.1f°".format(it) } ?: "N/A"}")
            appendLine("Spine: ${spine?.let { "%.1f°".format(it) } ?: "N/A"}")
        }
    }
    
    /**
     * Create a mirrored version of these angles (swap LEFT ↔ RIGHT)
     * 
     * This is used for front camera correction:
     * When using front camera, the image is mirrored before pose detection,
     * causing LEFT/RIGHT joints to be swapped. This function corrects that.
     * 
     * @return New JointAngles with LEFT and RIGHT swapped
     */
    fun mirrored(): JointAngles {
        return JointAngles(
            // Swap elbows
            leftElbow = rightElbow,
            rightElbow = leftElbow,
            // Swap shoulders
            leftShoulder = rightShoulder,
            rightShoulder = leftShoulder,
            // Swap cross shoulders
            leftShoulderCross = rightShoulderCross,
            rightShoulderCross = leftShoulderCross,
            // Swap hips
            leftHip = rightHip,
            rightHip = leftHip,
            // Swap neck left/right, keep spine variant as-is
            neckLeft = neckRight,
            neckRight = neckLeft,
            neckSpine = neckSpine,
            spine = spine,
            // Swap knees
            leftKnee = rightKnee,
            rightKnee = leftKnee,
            // Swap ankles
            leftAnkle = rightAnkle,
            rightAnkle = leftAnkle
        )
    }
}
