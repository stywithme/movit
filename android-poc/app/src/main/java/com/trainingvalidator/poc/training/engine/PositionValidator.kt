package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.training.models.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PositionValidator - Validates position-based checks
 * 
 * Works alongside FormValidator to provide comprehensive exercise validation.
 * FormValidator handles angle-based checks, PositionValidator handles position-based.
 * 
 * Features:
 * - Camera-aware axis selection (FORWARD/VERTICAL/SIDEWAYS)
 * - Automatic facing direction detection
 * - Single threshold per check (no difficulty levels)
 * - Visibility gating (only checks visible landmarks)
 * - Cooldown to prevent error spam
 * - Frame-based confirmation to prevent flickering
 */
class PositionValidator(
    private val positionChecks: List<PositionCheck>,
    private val expectedCameraPosition: String,
    private val expectedFacingDirection: FacingDirection?,
    private val visibilityThreshold: Float = 0.5f
) {
    
    companion object {
        private const val TAG = "PositionValidator"
        
        // NOTE: Using JointLandmarkMapping as Single Source of Truth for landmark mapping
        // Removed duplicate LANDMARK_INDEX_MAP - use JointLandmarkMapping.jointToLandmark() instead
        
        // Hysteresis buffer to prevent flickering
        private const val HYSTERESIS_BUFFER = 0.02f
        
        // Default minimum frames to confirm a position error (can be overridden per check)
        private const val DEFAULT_MIN_ERROR_FRAMES = 3
    }
    
    // Error frame counting for stability
    private val errorFrameCounts = mutableMapOf<String, Int>()
    
    // Cached camera detection result
    private var cachedCameraResult: CameraPositionDetector.CameraDetectionResult? = null
    
    /**
     * Validate all position checks for current phase
     * 
     * @param landmarks List of smoothed landmarks (33 points)
     * @param currentPhase Current phase from PhaseStateMachine
     * @return Validation result with errors and warnings
     */
    fun validate(
        landmarks: List<SmoothedLandmark>,
        currentPhase: Phase
    ): PositionValidationResult {
        if (landmarks.size < 33 || positionChecks.isEmpty()) {
            return PositionValidationResult.empty()
        }
        
        // 1. Detect camera position and facing
        cachedCameraResult = CameraPositionDetector.detect(landmarks)
        val cameraResult = cachedCameraResult ?: return PositionValidationResult.empty()
        
        // 2. Check if camera position matches expected
        val cameraWarning = checkCameraPosition(cameraResult)
        
        // 3. Determine effective facing direction
        val effectiveFacing = determineEffectiveFacing(cameraResult)
        
        // 4. Run position checks
        val errors = mutableListOf<PositionError>()
        val warnings = mutableListOf<PositionError>()
        val tips = mutableListOf<PositionError>()
        
        for (check in positionChecks) {
            // Skip if not active in current phase
            if (!isActiveInPhase(check, currentPhase)) {
                // Reset frame count when not active
                errorFrameCounts.remove(check.id)
                continue
            }
            
            // Validate the check
            val result = validateCheck(check, landmarks, cameraResult, effectiveFacing)
            
            if (!result.passed && !result.skipped) {
                // Increment frame count for stability (cap at requiredFrames)
                val requiredFrames = check.minErrorFrames.takeIf { it > 0 } ?: DEFAULT_MIN_ERROR_FRAMES
                val frameCount = ((errorFrameCounts[check.id] ?: 0) + 1).coerceAtMost(requiredFrames)
                errorFrameCounts[check.id] = frameCount
                
                // After confirmation, keep returning the issue every frame (visual overlay stays visible)
                if (frameCount >= requiredFrames) {
                    val error = result.error ?: continue  // Skip if error is unexpectedly null
                    when (check.severity) {
                        CheckSeverity.ERROR -> errors.add(error)
                        CheckSeverity.WARNING -> warnings.add(error)
                        CheckSeverity.TIP -> tips.add(error)
                    }
                }
            } else {
                // Reset frame count on success or skip
                errorFrameCounts.remove(check.id)
            }
        }
        
        return PositionValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            tips = tips,
            cameraWarning = cameraWarning,
            detectedCameraPosition = cameraResult.position,
            detectedFacing = cameraResult.facingDirection
        )
    }
    
    /**
     * Check if camera position matches expected
     */
    private fun checkCameraPosition(
        cameraResult: CameraPositionDetector.CameraDetectionResult
    ): CameraPositionWarning? {
        if (!CameraPositionDetector.matchesExpected(cameraResult.position, expectedCameraPosition)) {
            return CameraPositionWarning(
                expectedPosition = expectedCameraPosition,
                detectedPosition = CameraPositionDetector.toJsonCameraPosition(cameraResult.position),
                confidence = cameraResult.confidence,
                message = LocalizedText(
                    ar = "يُفضل التصوير من ${getPositionNameAr(expectedCameraPosition)} للحصول على نتائج أفضل",
                    en = "For best results, film from ${expectedCameraPosition.replace("_", " ")}"
                )
            )
        }
        return null
    }
    
    /**
     * Determine effective facing direction
     */
    private fun determineEffectiveFacing(
        cameraResult: CameraPositionDetector.CameraDetectionResult
    ): CameraPositionDetector.DetectedFacing {
        return when (expectedFacingDirection) {
            FacingDirection.FACING_RIGHT -> CameraPositionDetector.DetectedFacing.FACING_RIGHT
            FacingDirection.FACING_LEFT -> CameraPositionDetector.DetectedFacing.FACING_LEFT
            FacingDirection.FACING_CAMERA -> CameraPositionDetector.DetectedFacing.FACING_CAMERA
            FacingDirection.FACING_AWAY -> CameraPositionDetector.DetectedFacing.FACING_AWAY
            FacingDirection.AUTO_DETECT, null -> cameraResult.facingDirection
        }
    }
    
    /**
     * Check if a position check is active in current phase
     */
    private fun isActiveInPhase(check: PositionCheck, phase: Phase): Boolean {
        val phaseName = phase.name.lowercase()
        return check.activePhases.any { it.lowercase() == phaseName }
    }
    
    /**
     * Validate a single position check
     */
    private fun validateCheck(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        cameraResult: CameraPositionDetector.CameraDetectionResult,
        facing: CameraPositionDetector.DetectedFacing
    ): CheckResult {
        // Get landmarks
        val primary = getLandmark(check.landmarks.primary, landmarks) 
            ?: return CheckResult.skipped("Primary landmark not found")
        val secondary = getLandmark(check.landmarks.secondary, landmarks)
            ?: return CheckResult.skipped("Secondary landmark not found")
        
        // Check visibility
        if (!primary.isVisible(visibilityThreshold) || !secondary.isVisible(visibilityThreshold)) {
            return CheckResult.skipped("Landmarks not visible")
        }
        
        // Get threshold (single value, no difficulty levels)
        val threshold = check.condition.threshold
        
        // Validate based on check type
        return when (check.type) {
            PositionCheckType.FORWARD_COMPARISON -> 
                validateForwardComparison(check, primary, secondary, threshold, cameraResult, facing)
            
            PositionCheckType.VERTICAL_COMPARISON -> 
                validateVerticalComparison(check, primary, secondary, threshold)
            
            PositionCheckType.SIDEWAYS_COMPARISON -> 
                validateSidewaysComparison(check, primary, secondary, threshold, cameraResult)
            
            PositionCheckType.DISTANCE_RATIO -> 
                validateDistanceRatio(check, landmarks, threshold)
            
            PositionCheckType.HORIZONTAL_ALIGNMENT -> 
                validateHorizontalAlignment(check, landmarks, threshold)
            
            PositionCheckType.VERTICAL_ALIGNMENT -> 
                validateVerticalAlignment(check, landmarks, threshold)
            
            PositionCheckType.DEPTH_ALIGNMENT -> 
                validateDepthAlignment(check, primary, secondary, threshold)
        }
    }
    
    /**
     * Validate forward comparison (knee-over-toe, etc.)
     * 
     * FORWARD axis changes based on camera position:
     * - side_view: X axis (horizontal on screen)
     * - front_view/back_view: Z axis (depth)
     * 
     * Facing direction affects comparison direction
     */
    private fun validateForwardComparison(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double,
        cameraResult: CameraPositionDetector.CameraDetectionResult,
        facing: CameraPositionDetector.DetectedFacing
    ): CheckResult {
        val (primaryValue, secondaryValue) = getForwardAxisValues(
            primary, secondary, cameraResult.position, facing
        )
        
        val diff = primaryValue - secondaryValue
        
        val passed = when (check.condition.operator) {
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_EXCEED -> diff >= -threshold - HYSTERESIS_BUFFER
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold + HYSTERESIS_BUFFER
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, diff.toDouble(), threshold))
        }
    }
    
    /**
     * Get forward axis values adjusted for camera position and facing
     */
    private fun getForwardAxisValues(
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        cameraPosition: CameraPositionDetector.DetectedCameraPosition,
        facing: CameraPositionDetector.DetectedFacing
    ): Pair<Float, Float> {
        return when (cameraPosition) {
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT,
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
                // In side view, FORWARD is X axis
                when (facing) {
                    CameraPositionDetector.DetectedFacing.FACING_RIGHT -> 
                        Pair(primary.x, secondary.x)
                    CameraPositionDetector.DetectedFacing.FACING_LEFT -> 
                        Pair(-primary.x, -secondary.x)  // Flip for opposite facing
                    else -> 
                        Pair(primary.x, secondary.x)
                }
            }
            
            CameraPositionDetector.DetectedCameraPosition.FRONT_VIEW,
            CameraPositionDetector.DetectedCameraPosition.BACK_VIEW -> {
                // In front/back view, FORWARD is Z axis (depth)
                // Smaller Z = closer to camera = more forward
                Pair(-primary.z, -secondary.z)
            }
            
            else -> Pair(primary.x, secondary.x)  // Fallback to X
        }
    }
    
    /**
     * Validate vertical comparison (Y axis - works in all views)
     */
    private fun validateVerticalComparison(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double
    ): CheckResult {
        // Y axis: smaller = higher on screen
        val diff = primary.y - secondary.y
        
        val passed = when (check.condition.operator) {
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_EXCEED -> diff >= -threshold - HYSTERESIS_BUFFER
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold + HYSTERESIS_BUFFER
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, diff.toDouble(), threshold))
        }
    }
    
    /**
     * Validate sideways comparison
     */
    private fun validateSidewaysComparison(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double,
        cameraResult: CameraPositionDetector.CameraDetectionResult
    ): CheckResult {
        val (primaryValue, secondaryValue) = when (cameraResult.position) {
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT,
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
                // In side view, SIDEWAYS is Z axis
                Pair(primary.z, secondary.z)
            }
            else -> {
                // In front/back view, SIDEWAYS is X axis
                Pair(primary.x, secondary.x)
            }
        }
        
        val diff = primaryValue - secondaryValue
        
        val passed = when (check.condition.operator) {
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_EXCEED -> diff >= -threshold - HYSTERESIS_BUFFER
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, diff.toDouble(), threshold))
        }
    }
    
    /**
     * Validate distance ratio between two pairs of landmarks
     */
    private fun validateDistanceRatio(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped()
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped()
        val l3 = check.landmarks.tertiary?.let { getLandmark(it, landmarks) } 
            ?: return CheckResult.skipped()
        val l4 = check.landmarks.quaternary?.let { getLandmark(it, landmarks) } 
            ?: return CheckResult.skipped()
        
        // Check visibility
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold) ||
            !l3.isVisible(visibilityThreshold) || !l4.isVisible(visibilityThreshold)) {
            return CheckResult.skipped()
        }
        
        // Calculate distances
        val distance1 = calculate3DDistance(l1, l2)
        val distance2 = calculate3DDistance(l3, l4)
        
        if (distance2 < 0.001f) return CheckResult.skipped()  // Avoid division by zero
        
        val ratio = distance1 / distance2
        
        val passed = when (check.condition.operator) {
            PositionOperator.GREATER_THAN_RATIO -> ratio >= threshold - HYSTERESIS_BUFFER
            PositionOperator.LESS_THAN_RATIO -> ratio <= threshold + HYSTERESIS_BUFFER
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, ratio.toDouble(), threshold))
        }
    }
    
    /**
     * Validate horizontal alignment (3 points on same Y level)
     */
    private fun validateHorizontalAlignment(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped()
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped()
        val l3 = check.landmarks.tertiary?.let { getLandmark(it, landmarks) }
        
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold)) {
            return CheckResult.skipped()
        }
        
        // Check if all Y values are within threshold
        val yValues = mutableListOf(l1.y, l2.y)
        if (l3 != null && l3.isVisible(visibilityThreshold)) {
            yValues.add(l3.y)
        }
        
        val maxY = yValues.maxOrNull() ?: return CheckResult.skipped()
        val minY = yValues.minOrNull() ?: return CheckResult.skipped()
        val range = maxY - minY
        
        val passed = range <= threshold + HYSTERESIS_BUFFER
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, range.toDouble(), threshold))
        }
    }
    
    /**
     * Validate vertical alignment (points on same X level)
     */
    private fun validateVerticalAlignment(
        check: PositionCheck,
        landmarks: List<SmoothedLandmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped()
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped()
        val l3 = check.landmarks.tertiary?.let { getLandmark(it, landmarks) }
        
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold)) {
            return CheckResult.skipped()
        }
        
        val xValues = mutableListOf(l1.x, l2.x)
        if (l3 != null && l3.isVisible(visibilityThreshold)) {
            xValues.add(l3.x)
        }
        
        val maxX = xValues.maxOrNull() ?: return CheckResult.skipped()
        val minX = xValues.minOrNull() ?: return CheckResult.skipped()
        val range = maxX - minX
        
        val passed = range <= threshold + HYSTERESIS_BUFFER
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, range.toDouble(), threshold))
        }
    }
    
    /**
     * Validate depth alignment (landmarks at same Z level)
     */
    private fun validateDepthAlignment(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double
    ): CheckResult {
        val zDiff = abs(primary.z - secondary.z)
        val passed = zDiff <= threshold + HYSTERESIS_BUFFER
        
        return if (passed) {
            CheckResult.passed()
        } else {
            CheckResult.failed(createError(check, zDiff.toDouble(), threshold))
        }
    }
    
    // ==================== Helper Methods ====================
    
    private fun getLandmark(name: String, landmarks: List<SmoothedLandmark>): SmoothedLandmark? {
        // Use JointLandmarkMapping as Single Source of Truth
        val index = JointLandmarkMapping.jointToLandmark(name) ?: return null
        return landmarks.getOrNull(index)
    }
    
    private fun calculate3DDistance(l1: SmoothedLandmark, l2: SmoothedLandmark): Float {
        val dx = l1.x - l2.x
        val dy = l1.y - l2.y
        val dz = l1.z - l2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    private fun createError(check: PositionCheck, actualValue: Double, threshold: Double): PositionError {
        return PositionError(
            checkId = check.id,
            type = check.type,
            severity = check.severity,
            message = check.errorMessage,
            actualValue = actualValue,
            threshold = threshold,
            landmark1 = check.landmarks.primary,
            landmark2 = check.landmarks.secondary
        )
    }
    
    private fun getPositionNameAr(position: String): String {
        return when (position) {
            "side_view" -> "الجانب"
            "front_view" -> "الأمام"
            "back_view" -> "الخلف"
            else -> position
        }
    }
    
    /**
     * Clear cooldowns (call when session resets)
     */
    fun clearCooldowns() {
        errorFrameCounts.clear()
    }
    
    /**
     * Get last detected camera result
     */
    fun getLastCameraResult(): CameraPositionDetector.CameraDetectionResult? = cachedCameraResult
}

// ==================== Result Types ====================

/**
 * Result of position validation
 */
data class PositionValidationResult(
    val isValid: Boolean,
    val errors: List<PositionError>,          // Severity: ERROR - affects rep
    val warnings: List<PositionError>,        // Severity: WARNING - form feedback
    val tips: List<PositionError>,            // Severity: TIP - improvement suggestions
    val cameraWarning: CameraPositionWarning?,
    val detectedCameraPosition: CameraPositionDetector.DetectedCameraPosition,
    val detectedFacing: CameraPositionDetector.DetectedFacing
) {
    companion object {
        fun empty() = PositionValidationResult(
            true, emptyList(), emptyList(), emptyList(), null,
            CameraPositionDetector.DetectedCameraPosition.UNKNOWN,
            CameraPositionDetector.DetectedFacing.UNKNOWN
        )
    }
    
    /**
     * Get all issues (errors + warnings + tips)
     */
    fun getAllIssues(): List<PositionError> = errors + warnings + tips
}

/**
 * Position error details
 */
data class PositionError(
    val checkId: String,
    val type: PositionCheckType,
    val severity: CheckSeverity,
    val message: LocalizedText,
    val actualValue: Double,
    val threshold: Double,
    val landmark1: String,
    val landmark2: String
)

/**
 * Camera position warning
 */
data class CameraPositionWarning(
    val expectedPosition: String,
    val detectedPosition: String,
    val confidence: Float,
    val message: LocalizedText
)

/**
 * Internal check result
 */
internal data class CheckResult(
    val passed: Boolean,
    val skipped: Boolean = false,
    val error: PositionError? = null,
    val skipReason: String? = null
) {
    companion object {
        fun passed() = CheckResult(passed = true)
        fun failed(error: PositionError) = CheckResult(passed = false, error = error)
        fun skipped(reason: String = "") = CheckResult(passed = true, skipped = true, skipReason = reason)
    }
}
