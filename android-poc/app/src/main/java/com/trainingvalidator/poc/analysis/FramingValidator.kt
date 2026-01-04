package com.trainingvalidator.poc.analysis

import com.trainingvalidator.poc.pose.BodyLandmarks

/**
 * FramingValidator - Checks if the user is correctly positioned in the camera frame
 * 
 * Only uses HIGH VISIBILITY landmarks for validation to avoid false positives
 * from bad coordinate estimates.
 */
object FramingValidator {
    private const val EDGE_MARGIN = 0.05f  // 5% from edges
    private const val MIN_BODY_SIZE = 0.3f // Body should be at least 30% of frame
    private const val MAX_BODY_SIZE = 0.95f // Body shouldn't exceed 95%
    private const val VISIBILITY_THRESHOLD = 0.5f // Only use landmarks we can see

    data class FramingResult(
        val isValid: Boolean,
        val issues: List<FramingIssue>
    )

    enum class FramingIssue(val message: String) {
        HEAD_CUT_OFF("Head cut off - Move camera up or step back"),
        FEET_CUT_OFF("Feet cut off - Move camera down or step back"),
        LEFT_ARM_CUT_OFF("Left arm cut off - Move right"),
        RIGHT_ARM_CUT_OFF("Right arm cut off - Move left"),
        TOO_CLOSE("Too close - Step back"),
        TOO_FAR("Too far - Step closer"),
        MOVE_LEFT("Move left"),
        MOVE_RIGHT("Move right"),
        BODY_NOT_VISIBLE("Stand where camera can see you")
    }

    /**
     * Validate if the user is correctly framed
     */
    fun validate(landmarks: List<SmoothedLandmark>): FramingResult {
        val issues = mutableListOf<FramingIssue>()
        
        if (landmarks.isEmpty()) return FramingResult(false, listOf(FramingIssue.BODY_NOT_VISIBLE))

        // Get key landmarks
        val nose = landmarks[BodyLandmarks.NOSE]
        val leftAnkle = landmarks[BodyLandmarks.LEFT_ANKLE]
        val rightAnkle = landmarks[BodyLandmarks.RIGHT_ANKLE]
        val leftHip = landmarks[BodyLandmarks.LEFT_HIP]
        val rightHip = landmarks[BodyLandmarks.RIGHT_HIP]
        val leftWrist = landmarks[BodyLandmarks.LEFT_WRIST]
        val rightWrist = landmarks[BodyLandmarks.RIGHT_WRIST]

        // Check if we can see the core body (torso must be visible for any validation)
        val coreVisible = leftHip.isVisible(VISIBILITY_THRESHOLD) && 
                         rightHip.isVisible(VISIBILITY_THRESHOLD)
        
        if (!coreVisible) {
            return FramingResult(false, listOf(FramingIssue.BODY_NOT_VISIBLE))
        }
        
        // Check head - only if visible
        if (nose.isVisible(VISIBILITY_THRESHOLD)) {
            if (nose.y < EDGE_MARGIN) {
                issues.add(FramingIssue.HEAD_CUT_OFF)
            }
        }
        
        // Check feet - only if at least one ankle is visible
        // If neither is visible, they're probably cut off
        val leftAnkleVisible = leftAnkle.isVisible(VISIBILITY_THRESHOLD)
        val rightAnkleVisible = rightAnkle.isVisible(VISIBILITY_THRESHOLD)
        
        if (!leftAnkleVisible && !rightAnkleVisible) {
            // Neither ankle visible - probably feet are cut off
            issues.add(FramingIssue.FEET_CUT_OFF)
        } else {
            // At least one visible - check if it's at edge
            if (leftAnkleVisible && leftAnkle.y > 1 - EDGE_MARGIN) {
                issues.add(FramingIssue.FEET_CUT_OFF)
            } else if (rightAnkleVisible && rightAnkle.y > 1 - EDGE_MARGIN) {
                issues.add(FramingIssue.FEET_CUT_OFF)
            }
        }
        
        // Check body size - only if we can see both head and feet
        if (nose.isVisible(VISIBILITY_THRESHOLD) && (leftAnkleVisible || rightAnkleVisible)) {
            val avgAnkleY = if (leftAnkleVisible && rightAnkleVisible) {
                (leftAnkle.y + rightAnkle.y) / 2
            } else if (leftAnkleVisible) {
                leftAnkle.y
            } else {
                rightAnkle.y
            }
            
            val bodyHeight = avgAnkleY - nose.y
            
            if (bodyHeight > 0) { // Sanity check
                if (bodyHeight < MIN_BODY_SIZE) issues.add(FramingIssue.TOO_FAR)
                if (bodyHeight > MAX_BODY_SIZE) issues.add(FramingIssue.TOO_CLOSE)
            }
        }
        
        // Check horizontal centering (using visible hips)
        val centerX = (leftHip.x + rightHip.x) / 2
        if (centerX < 0.3f) issues.add(FramingIssue.MOVE_RIGHT)
        if (centerX > 0.7f) issues.add(FramingIssue.MOVE_LEFT)
        
        // Check arms - only if visible
        if (leftWrist.isVisible(VISIBILITY_THRESHOLD) && leftWrist.x > 1 - EDGE_MARGIN) {
            issues.add(FramingIssue.LEFT_ARM_CUT_OFF)
        }
        if (rightWrist.isVisible(VISIBILITY_THRESHOLD) && rightWrist.x < EDGE_MARGIN) {
            issues.add(FramingIssue.RIGHT_ARM_CUT_OFF)
        }

        return FramingResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
}
