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
    private val posePositionCode: String,
    val sceneExpectation: PoseSceneExpectation,
    private val visibilityThreshold: Float = 0.5f
) {
    
    companion object {
        private const val TAG = "PositionValidator"
        private const val HYSTERESIS_BUFFER = 0.02f
        private const val DEFAULT_MIN_ERROR_FRAMES = 3
    }
    
    private val errorFrameCounts = mutableMapOf<String, Int>()
    
    /** Full 3-axis scene detector (direction + posture + region). */
    private val sceneDetector = PoseSceneDetector()
    
    /** @deprecated Kept for legacy callers. Use [sceneExpectation] instead. */
    val resolvedPosition: PosePosition? = PosePosition.fromCode(posePositionCode)
    
    private var cachedSceneResult: PoseSceneResult? = null
    private var cachedCameraResult: CameraPositionDetector.CameraDetectionResult? = null

    private var lockedSceneResult: PoseSceneResult? = null
    private var lockedCameraResult: CameraPositionDetector.CameraDetectionResult? = null
    
    /**
     * Validate all position checks for current phase
     * 
     * @param landmarks List of smoothed landmarks (33 points)
     * @param currentPhase Current phase from PhaseStateMachine
     * @return Validation result with errors and warnings
     */
    fun validate(
        landmarks: List<SmoothedLandmark>,
        currentPhase: Phase,
        isBilateralFlipped: Boolean = false,
        isFrontCamera: Boolean = false
    ): PositionValidationResult {
        if (landmarks.size < 33) {
            return PositionValidationResult.empty()
        }
        
        // 1. Live scene detection (always runs — feeds rolling window & warnings)
        val liveScene = sceneDetector.detect(landmarks, isFrontCamera)

        // 2. For axis selection in position checks, prefer locked scene (stable)
        val effectiveScene = lockedSceneResult ?: liveScene
        cachedSceneResult = effectiveScene
        cachedCameraResult = lockedCameraResult ?: CameraPositionDetector.CameraDetectionResult(
            liveScene.direction, liveScene.directionConfidence,
            liveScene.facing, liveScene.closerSide, liveScene.depthInfo
        )
        val cameraResult = cachedCameraResult ?: return PositionValidationResult.empty()

        // 3. Scene warnings always compare LIVE detection against expectation
        val sceneWarnings = checkSceneAxes(liveScene)
        
        // 4. Facing for position checks follows effective scene (locked when set)
        val effectiveFacing = effectiveScene.facing
        
        // 5. Run position checks
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
            
            // Mirror landmark names when needed:
            // - Bilateral flip: config says right_elbow but user is doing left side
            // - Front camera: image is mirrored, so MediaPipe's left = person's right
            // XOR: if both active, they cancel out (double mirror = no mirror)
            val shouldMirrorLandmarks = isBilateralFlipped xor isFrontCamera
            val effectiveCheck = if (shouldMirrorLandmarks) mirrorCheckLandmarks(check) else check
            val result = validateCheck(effectiveCheck, landmarks, cameraResult, effectiveFacing)
            
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
            sceneWarnings = sceneWarnings,
            detectedCameraPosition = cameraResult.position,
            detectedFacing = cameraResult.facingDirection
        )
    }
    
    /**
     * Check each of the 3 scene axes independently. Returns a warning per
     * mismatched axis so the UI / TTS can give specific guidance.
     */
    private fun checkSceneAxes(scene: PoseSceneResult): List<SceneAxisWarning> {
        val result = sceneExpectation.matchesScene(scene)
        if (result.allMatch) return emptyList()

        val warnings = mutableListOf<SceneAxisWarning>()

        if (!result.postureMatch) {
            warnings.add(SceneAxisWarning(
                axis = "posture",
                expected = sceneExpectation.postures.map { it.name.lowercase() },
                detected = scene.posture.name.lowercase(),
                message = buildPostureWarning(sceneExpectation.postures)
            ))
        }

        if (!result.directionMatch) {
            warnings.add(SceneAxisWarning(
                axis = "direction",
                expected = sceneExpectation.directions.map { it.code },
                detected = CameraPositionDetector.toJsonCameraPosition(scene.direction),
                message = buildDirectionWarning(sceneExpectation.directions)
            ))
        }

        if (!result.regionMatch) {
            warnings.add(SceneAxisWarning(
                axis = "region",
                expected = sceneExpectation.regions.map { it.name.lowercase() },
                detected = scene.region.name.lowercase(),
                message = buildRegionWarning(sceneExpectation.regions)
            ))
        }

        return warnings
    }

    private fun buildPostureWarning(expected: List<BodyPosture>): LocalizedText {
        val arParts = expected.mapNotNull { postureNameAr(it) }
        val enParts = expected.mapNotNull { postureNameEn(it) }
        return LocalizedText(
            ar = arParts.joinToString(" أو "),
            en = enParts.joinToString(" or ")
        )
    }

    private fun buildDirectionWarning(expected: List<ExpectedDirection>): LocalizedText {
        val arParts = expected.mapNotNull { directionNameAr(it) }
        val enParts = expected.mapNotNull { directionNameEn(it) }
        return LocalizedText(
            ar = "صوّر من ${arParts.joinToString(" أو ")}",
            en = "Film from ${enParts.joinToString(" or ")}"
        )
    }

    private fun buildRegionWarning(expected: List<VisibleRegion>): LocalizedText {
        val arParts = expected.mapNotNull { regionNameAr(it) }
        val enParts = expected.mapNotNull { regionNameEn(it) }
        return LocalizedText(
            ar = "أظهر ${arParts.joinToString(" أو ")}",
            en = "Show ${enParts.joinToString(" or ")}"
        )
    }

    private fun postureNameAr(p: BodyPosture) = when (p) {
        BodyPosture.STANDING -> "قف مستقيماً"
        BodyPosture.LYING_PRONE -> "استلقِ على وجهك"
        BodyPosture.LYING_SUPINE -> "استلقِ على ظهرك"
        BodyPosture.LYING_SIDE -> "استلقِ على جنبك"
        BodyPosture.SITTING -> "اجلس"
        BodyPosture.UNKNOWN -> null
    }
    private fun postureNameEn(p: BodyPosture) = when (p) {
        BodyPosture.STANDING -> "Stand upright"
        BodyPosture.LYING_PRONE -> "Lie face down"
        BodyPosture.LYING_SUPINE -> "Lie face up"
        BodyPosture.LYING_SIDE -> "Lie on your side"
        BodyPosture.SITTING -> "Sit down"
        BodyPosture.UNKNOWN -> null
    }
    private fun directionNameAr(d: ExpectedDirection) = when (d) {
        ExpectedDirection.FRONT -> "الأمام"
        ExpectedDirection.BACK -> "الخلف"
        ExpectedDirection.SIDE_ANY -> "الجانب"
        ExpectedDirection.SIDE_LEFT -> "الجانب الأيسر"
        ExpectedDirection.SIDE_RIGHT -> "الجانب الأيمن"
        ExpectedDirection.DIAGONAL -> "بزاوية مائلة"
        ExpectedDirection.ANY -> null
    }
    private fun directionNameEn(d: ExpectedDirection) = when (d) {
        ExpectedDirection.FRONT -> "the front"
        ExpectedDirection.BACK -> "the back"
        ExpectedDirection.SIDE_ANY -> "the side"
        ExpectedDirection.SIDE_LEFT -> "the left side"
        ExpectedDirection.SIDE_RIGHT -> "the right side"
        ExpectedDirection.DIAGONAL -> "an angle"
        ExpectedDirection.ANY -> null
    }
    private fun regionNameAr(r: VisibleRegion) = when (r) {
        VisibleRegion.FULL_BODY -> "الجسم بالكامل"
        VisibleRegion.UPPER_BODY -> "الجزء العلوي"
        VisibleRegion.LOWER_BODY -> "الجزء السفلي"
        VisibleRegion.UNKNOWN -> null
    }
    private fun regionNameEn(r: VisibleRegion) = when (r) {
        VisibleRegion.FULL_BODY -> "your full body"
        VisibleRegion.UPPER_BODY -> "your upper body"
        VisibleRegion.LOWER_BODY -> "your lower body"
        VisibleRegion.UNKNOWN -> null
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
     * Get forward axis values adjusted for camera direction, facing, and posture.
     *
     * When standing:
     *   side view  → forward = X (flip by facing)
     *   front/back → forward = -Z
     *
     * When lying (camera from side):
     *   forward (along the body) is Y-axis in the image
     */
    private fun getForwardAxisValues(
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        cameraPosition: CameraPositionDetector.DetectedCameraPosition,
        facing: CameraPositionDetector.DetectedFacing
    ): Pair<Float, Float> {
        val posture = cachedSceneResult?.posture ?: BodyPosture.STANDING
        val isLying = posture == BodyPosture.LYING_PRONE || posture == BodyPosture.LYING_SUPINE || posture == BodyPosture.LYING_SIDE

        if (isLying) {
            val isSideCamera = cameraPosition == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT ||
                    cameraPosition == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT
            return if (isSideCamera) {
                // Lying + side camera: body length runs along Y
                Pair(primary.y, secondary.y)
            } else {
                // Lying + front/back camera: body length runs along X
                Pair(primary.x, secondary.x)
            }
        }

        return when (cameraPosition) {
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT,
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
                when (facing) {
                    CameraPositionDetector.DetectedFacing.FACING_RIGHT -> Pair(primary.x, secondary.x)
                    CameraPositionDetector.DetectedFacing.FACING_LEFT -> Pair(-primary.x, -secondary.x)
                    else -> Pair(primary.x, secondary.x)
                }
            }
            CameraPositionDetector.DetectedCameraPosition.FRONT_VIEW,
            CameraPositionDetector.DetectedCameraPosition.BACK_VIEW -> {
                Pair(-primary.z, -secondary.z)
            }
            else -> Pair(primary.x, secondary.x)
        }
    }

    /**
     * Validate vertical comparison.
     *
     * Standing → Y axis (gravity vertical).
     * Lying + side camera → X axis (body length axis in image).
     */
    private fun validateVerticalComparison(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double
    ): CheckResult {
        val posture = cachedSceneResult?.posture ?: BodyPosture.STANDING
        val isLying = posture == BodyPosture.LYING_PRONE || posture == BodyPosture.LYING_SUPINE || posture == BodyPosture.LYING_SIDE
        val direction = cachedSceneResult?.direction
        val isSideCamera = direction == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT ||
                direction == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT

        val diff = if (isLying && isSideCamera) {
            primary.x - secondary.x
        } else {
            primary.y - secondary.y
        }

        val passed = when (check.condition.operator) {
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_EXCEED -> diff >= -threshold - HYSTERESIS_BUFFER
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold + HYSTERESIS_BUFFER
            else -> true
        }

        return if (passed) CheckResult.passed()
        else CheckResult.failed(createError(check, diff.toDouble(), threshold))
    }

    /**
     * Validate sideways comparison.
     *
     * Standing + side camera → Z axis; Standing + front → X axis.
     * Lying + side camera → Z axis; Lying + front → Y axis.
     */
    private fun validateSidewaysComparison(
        check: PositionCheck,
        primary: SmoothedLandmark,
        secondary: SmoothedLandmark,
        threshold: Double,
        cameraResult: CameraPositionDetector.CameraDetectionResult
    ): CheckResult {
        val posture = cachedSceneResult?.posture ?: BodyPosture.STANDING
        val isLying = posture == BodyPosture.LYING_PRONE || posture == BodyPosture.LYING_SUPINE || posture == BodyPosture.LYING_SIDE
        val isSideCamera = cameraResult.position == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT ||
                cameraResult.position == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT

        val (pv, sv) = when {
            isSideCamera -> Pair(primary.z, secondary.z)
            isLying -> Pair(primary.y, secondary.y)
            else -> Pair(primary.x, secondary.x)
        }

        val diff = pv - sv

        val passed = when (check.condition.operator) {
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold + HYSTERESIS_BUFFER
            PositionOperator.SHOULD_EXCEED -> diff >= -threshold - HYSTERESIS_BUFFER
            else -> true
        }

        return if (passed) CheckResult.passed()
        else CheckResult.failed(createError(check, diff.toDouble(), threshold))
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
    
    /**
     * Mirror position check landmark names for bilateral flipping.
     * Swaps left_* <-> right_* in primary, secondary, tertiary, quaternary.
     * Shared landmarks (nose, spine, neck, etc.) pass through unchanged.
     */
    private fun mirrorCheckLandmarks(check: PositionCheck): PositionCheck {
        val mirrored = LandmarkGroup(
            primary = JointAngleTracker.mirrorJointCode(check.landmarks.primary),
            secondary = JointAngleTracker.mirrorJointCode(check.landmarks.secondary),
            tertiary = check.landmarks.tertiary?.let { JointAngleTracker.mirrorJointCode(it) },
            quaternary = check.landmarks.quaternary?.let { JointAngleTracker.mirrorJointCode(it) }
        )
        return check.copy(landmarks = mirrored)
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
    
    
    /**
     * Clear cooldowns and reset scene detector (call when session resets)
     */
    fun lockScene() {
        cachedSceneResult?.let { scene ->
            lockedSceneResult = scene
            lockedCameraResult = CameraPositionDetector.CameraDetectionResult(
                scene.direction, scene.directionConfidence,
                scene.facing, scene.closerSide, scene.depthInfo
            )
            Log.d(TAG, "Scene locked: dir=${scene.direction}, posture=${scene.posture}, facing=${scene.facing}")
        }
    }

    fun unlockScene() {
        lockedSceneResult = null
        lockedCameraResult = null
        Log.d(TAG, "Scene unlocked — will re-lock on next valid frame")
    }

    val isSceneLocked: Boolean get() = lockedSceneResult != null

    fun clearCooldowns() {
        errorFrameCounts.clear()
        sceneDetector.reset()
    }
    
    fun getLastCameraResult(): CameraPositionDetector.CameraDetectionResult? = cachedCameraResult
    fun getLastSceneResult(): PoseSceneResult? = cachedSceneResult
}

// ==================== Result Types ====================

/**
 * Result of position validation
 */
data class PositionValidationResult(
    val isValid: Boolean,
    val errors: List<PositionError>,
    val warnings: List<PositionError>,
    val tips: List<PositionError>,
    val sceneWarnings: List<SceneAxisWarning>,
    val detectedCameraPosition: CameraPositionDetector.DetectedCameraPosition,
    val detectedFacing: CameraPositionDetector.DetectedFacing
) {
    companion object {
        fun empty() = PositionValidationResult(
            true, emptyList(), emptyList(), emptyList(), emptyList(),
            CameraPositionDetector.DetectedCameraPosition.UNKNOWN,
            CameraPositionDetector.DetectedFacing.UNKNOWN
        )
    }

    val hasSceneWarnings: Boolean get() = sceneWarnings.isNotEmpty()

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
 * Per-axis scene warning — one entry per mismatched axis (posture / direction / region).
 */
data class SceneAxisWarning(
    val axis: String,
    val expected: List<String>,
    val detected: String,
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
