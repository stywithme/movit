package com.trainingvalidator.poc.pose

/**
 * BodyLandmarks - MediaPipe Pose Landmark indices
 * 
 * MediaPipe BlazePose provides 33 landmarks on the human body.
 * This object provides named constants for easy access.
 * 
 * Reference: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker
 */
object BodyLandmarks {
    // Face landmarks
    const val NOSE = 0
    const val LEFT_EYE_INNER = 1
    const val LEFT_EYE = 2
    const val LEFT_EYE_OUTER = 3
    const val RIGHT_EYE_INNER = 4
    const val RIGHT_EYE = 5
    const val RIGHT_EYE_OUTER = 6
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10

    // Upper body landmarks
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_PINKY = 17
    const val RIGHT_PINKY = 18
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_THUMB = 21
    const val RIGHT_THUMB = 22

    // Lower body landmarks
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    // Total landmarks count
    const val TOTAL_LANDMARKS = 33

    /**
     * Get landmark name by index
     */
    fun getName(index: Int): String {
        return when (index) {
            NOSE -> "Nose"
            LEFT_EYE_INNER -> "Left Eye Inner"
            LEFT_EYE -> "Left Eye"
            LEFT_EYE_OUTER -> "Left Eye Outer"
            RIGHT_EYE_INNER -> "Right Eye Inner"
            RIGHT_EYE -> "Right Eye"
            RIGHT_EYE_OUTER -> "Right Eye Outer"
            LEFT_EAR -> "Left Ear"
            RIGHT_EAR -> "Right Ear"
            MOUTH_LEFT -> "Mouth Left"
            MOUTH_RIGHT -> "Mouth Right"
            LEFT_SHOULDER -> "Left Shoulder"
            RIGHT_SHOULDER -> "Right Shoulder"
            LEFT_ELBOW -> "Left Elbow"
            RIGHT_ELBOW -> "Right Elbow"
            LEFT_WRIST -> "Left Wrist"
            RIGHT_WRIST -> "Right Wrist"
            LEFT_PINKY -> "Left Pinky"
            RIGHT_PINKY -> "Right Pinky"
            LEFT_INDEX -> "Left Index"
            RIGHT_INDEX -> "Right Index"
            LEFT_THUMB -> "Left Thumb"
            RIGHT_THUMB -> "Right Thumb"
            LEFT_HIP -> "Left Hip"
            RIGHT_HIP -> "Right Hip"
            LEFT_KNEE -> "Left Knee"
            RIGHT_KNEE -> "Right Knee"
            LEFT_ANKLE -> "Left Ankle"
            RIGHT_ANKLE -> "Right Ankle"
            LEFT_HEEL -> "Left Heel"
            RIGHT_HEEL -> "Right Heel"
            LEFT_FOOT_INDEX -> "Left Foot Index"
            RIGHT_FOOT_INDEX -> "Right Foot Index"
            else -> "Unknown"
        }
    }

    /**
     * Connections for drawing skeleton
     * Each pair represents a line between two landmarks
     */
    val POSE_CONNECTIONS: List<Pair<Int, Int>> = listOf(
        // Face
        Pair(NOSE, LEFT_EYE_INNER),
        Pair(LEFT_EYE_INNER, LEFT_EYE),
        Pair(LEFT_EYE, LEFT_EYE_OUTER),
        Pair(LEFT_EYE_OUTER, LEFT_EAR),
        Pair(NOSE, RIGHT_EYE_INNER),
        Pair(RIGHT_EYE_INNER, RIGHT_EYE),
        Pair(RIGHT_EYE, RIGHT_EYE_OUTER),
        Pair(RIGHT_EYE_OUTER, RIGHT_EAR),
        Pair(MOUTH_LEFT, MOUTH_RIGHT),
        
        // Torso
        Pair(LEFT_SHOULDER, RIGHT_SHOULDER),
        Pair(LEFT_SHOULDER, LEFT_HIP),
        Pair(RIGHT_SHOULDER, RIGHT_HIP),
        Pair(LEFT_HIP, RIGHT_HIP),
        
        // Left arm
        Pair(LEFT_SHOULDER, LEFT_ELBOW),
        Pair(LEFT_ELBOW, LEFT_WRIST),
        Pair(LEFT_WRIST, LEFT_PINKY),
        Pair(LEFT_WRIST, LEFT_INDEX),
        Pair(LEFT_WRIST, LEFT_THUMB),
        Pair(LEFT_PINKY, LEFT_INDEX),
        
        // Right arm
        Pair(RIGHT_SHOULDER, RIGHT_ELBOW),
        Pair(RIGHT_ELBOW, RIGHT_WRIST),
        Pair(RIGHT_WRIST, RIGHT_PINKY),
        Pair(RIGHT_WRIST, RIGHT_INDEX),
        Pair(RIGHT_WRIST, RIGHT_THUMB),
        Pair(RIGHT_PINKY, RIGHT_INDEX),
        
        // Left leg
        Pair(LEFT_HIP, LEFT_KNEE),
        Pair(LEFT_KNEE, LEFT_ANKLE),
        Pair(LEFT_ANKLE, LEFT_HEEL),
        Pair(LEFT_ANKLE, LEFT_FOOT_INDEX),
        Pair(LEFT_HEEL, LEFT_FOOT_INDEX),
        
        // Right leg
        Pair(RIGHT_HIP, RIGHT_KNEE),
        Pair(RIGHT_KNEE, RIGHT_ANKLE),
        Pair(RIGHT_ANKLE, RIGHT_HEEL),
        Pair(RIGHT_ANKLE, RIGHT_FOOT_INDEX),
        Pair(RIGHT_HEEL, RIGHT_FOOT_INDEX)
    )

    /**
     * Essential landmarks for full body visibility check
     */
    val FULL_BODY_LANDMARKS: List<Int> = listOf(
        LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE,
        LEFT_ANKLE, RIGHT_ANKLE
    )

    /**
     * Upper body landmarks for exercises like push-ups
     */
    val UPPER_BODY_LANDMARKS: List<Int> = listOf(
        LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_ELBOW, RIGHT_ELBOW,
        LEFT_WRIST, RIGHT_WRIST,
        LEFT_HIP, RIGHT_HIP
    )
}
