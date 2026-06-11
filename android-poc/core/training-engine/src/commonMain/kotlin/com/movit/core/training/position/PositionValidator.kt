package com.movit.core.training.position

import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.config.LandmarkGroup
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PositionCheck
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.config.PositionOperator
import com.movit.core.training.engine.JointAngleTracker
import com.movit.core.training.engine.Phase
import com.movit.core.training.geometry.JointLandmarkMapping
import com.movit.core.training.geometry.LandmarkTiltCorrector
import com.movit.core.training.model.Landmark
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PositionValidator - Validates position-based checks
 * 
 * Works alongside [JointEvaluator] (angle/quality) for full exercise feedback.
 * Joint quality uses state-based ranges; PositionValidator handles world/landmark checks.
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
    private val visibilityThreshold: Float = 0.5f,
    private val tiltSource: DeviceTiltPort? = null
) {
    
    companion object {
        private const val TAG = "PositionValidator"
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
        landmarks: List<Landmark>,
        currentPhase: Phase,
        isBilateralFlipped: Boolean = false,
        isFrontCamera: Boolean = false
    ): PositionValidationResult {
        if (landmarks.size < 33) {
            return PositionValidationResult.empty().copy(
                debugChecks = positionChecks.map { check ->
                    val requiredFrames = check.minErrorFrames.takeIf { it > 0 } ?: DEFAULT_MIN_ERROR_FRAMES
                    PositionCheckDebug(
                        checkId = check.id,
                        type = check.type,
                        status = PositionCheckDebugStatus.SKIPPED,
                        actualValue = null,
                        threshold = check.condition.threshold,
                        skipReason = "Insufficient landmarks (${landmarks.size})",
                        frameCount = 0,
                        requiredFrames = requiredFrames,
                        landmark1 = check.landmarks.primary,
                        landmark2 = check.landmarks.secondary
                    )
                }
            )
        }

        val effectiveLandmarks = getTiltCorrectedLandmarks(landmarks)
        
        // 1. Live scene detection (always runs — feeds rolling window & warnings)
        val liveScene = sceneDetector.detect(effectiveLandmarks, isFrontCamera)

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
        val debugChecks = mutableListOf<PositionCheckDebug>()
        
        for (check in positionChecks) {
            val requiredFrames = check.minErrorFrames.takeIf { it > 0 } ?: DEFAULT_MIN_ERROR_FRAMES

            // Skip if not active in current phase
            if (!isActiveInPhase(check, currentPhase)) {
                // Reset frame count when not active
                errorFrameCounts.remove(check.id)
                debugChecks.add(
                    PositionCheckDebug(
                        checkId = check.id,
                        type = check.type,
                        status = PositionCheckDebugStatus.SKIPPED,
                        actualValue = null,
                        threshold = check.condition.threshold,
                        skipReason = "Inactive in phase ${currentPhase.name}",
                        frameCount = 0,
                        requiredFrames = requiredFrames,
                        landmark1 = check.landmarks.primary,
                        landmark2 = check.landmarks.secondary
                    )
                )
                continue
            }
            
            // Mirror landmark names when needed:
            // - Bilateral flip: config says right_elbow but user is doing left side
            // - Front camera: image is mirrored, so MediaPipe's left = person's right
            // XOR: if both active, they cancel out (double mirror = no mirror)
            val shouldMirrorLandmarks = isBilateralFlipped xor isFrontCamera
            val effectiveCheck = if (shouldMirrorLandmarks) mirrorCheckLandmarks(check) else check
            val result = validateCheck(effectiveCheck, effectiveLandmarks, cameraResult, effectiveFacing)
            
            if (!result.passed && !result.skipped) {
                // Increment frame count for stability (cap at requiredFrames)
                val frameCount = ((errorFrameCounts[check.id] ?: 0) + 1).coerceAtMost(requiredFrames)
                errorFrameCounts[check.id] = frameCount
                
                // After confirmation, keep returning the issue every frame (visual overlay stays visible)
                if (frameCount >= requiredFrames) {
                    val error = result.error ?: continue  // Skip if error is unexpectedly null
                    debugChecks.add(
                        PositionCheckDebug(
                            checkId = check.id,
                            type = check.type,
                            status = PositionCheckDebugStatus.FAIL,
                            actualValue = result.actualValue,
                            threshold = result.threshold ?: check.condition.threshold,
                            frameCount = frameCount,
                            requiredFrames = requiredFrames,
                            landmark1 = effectiveCheck.landmarks.primary,
                            landmark2 = effectiveCheck.landmarks.secondary
                        )
                    )
                    when (check.severity) {
                        CheckSeverity.ERROR -> errors.add(error)
                        CheckSeverity.WARNING -> warnings.add(error)
                        CheckSeverity.TIP -> tips.add(error)
                    }
                } else {
                    debugChecks.add(
                        PositionCheckDebug(
                            checkId = check.id,
                            type = check.type,
                            status = PositionCheckDebugStatus.FAIL_PENDING,
                            actualValue = result.actualValue,
                            threshold = result.threshold ?: check.condition.threshold,
                            frameCount = frameCount,
                            requiredFrames = requiredFrames,
                            landmark1 = effectiveCheck.landmarks.primary,
                            landmark2 = effectiveCheck.landmarks.secondary
                        )
                    )
                }
            } else {
                // Reset frame count on success or skip
                errorFrameCounts.remove(check.id)
                debugChecks.add(
                    PositionCheckDebug(
                        checkId = check.id,
                        type = check.type,
                        status = if (result.skipped) PositionCheckDebugStatus.SKIPPED else PositionCheckDebugStatus.PASS,
                        actualValue = result.actualValue,
                        threshold = result.threshold ?: check.condition.threshold,
                        skipReason = result.skipReason,
                        frameCount = 0,
                        requiredFrames = requiredFrames,
                        landmark1 = effectiveCheck.landmarks.primary,
                        landmark2 = effectiveCheck.landmarks.secondary
                    )
                )
            }
        }
        
        return PositionValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            tips = tips,
            sceneWarnings = sceneWarnings,
            detectedCameraPosition = cameraResult.position,
            detectedFacing = cameraResult.facingDirection,
            debugChecks = debugChecks
        )
    }

    private fun getTiltCorrectedLandmarks(landmarks: List<Landmark>): List<Landmark> {
        val source = tiltSource ?: return landmarks
        if (!source.isAvailable) return landmarks
        val correctionRadians = source.correctionRadians
        if (correctionRadians == 0f || !correctionRadians.isFinite()) return landmarks
        return LandmarkTiltCorrector.correct(landmarks, correctionRadians)
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
                message = PositionMessageResolver.resolvePostureAxisWarning(sceneExpectation.postures)
            ))
        }

        if (!result.directionMatch) {
            warnings.add(SceneAxisWarning(
                axis = "direction",
                expected = sceneExpectation.directions.map { it.code },
                detected = CameraPositionDetector.toJsonCameraPosition(scene.direction),
                message = PositionMessageResolver.resolveDirectionAxisWarning(sceneExpectation.directions)
            ))
        }

        if (!result.regionMatch) {
            warnings.add(SceneAxisWarning(
                axis = "region",
                expected = sceneExpectation.regions.map { it.name.lowercase() },
                detected = scene.region.name.lowercase(),
                message = PositionMessageResolver.resolveRegionAxisWarning(sceneExpectation.regions)
            ))
        }

        return warnings
    }
    
    /**
     * Check if a position check is active in current phase.
     * Maps Phase enum to API phase names: START->"top", others use lowercase enum name.
     * "all" in activePhases means active in every non-IDLE phase.
     */
    /** Legacy alias: some payloads used `hold` for the hold-timer phase; engine phase is [Phase.COUNT] → `"count"`. */
    private fun normalizeActivePhaseToken(raw: String): String =
        when (raw.lowercase()) {
            "hold" -> "count"
            else -> raw.lowercase()
        }

    private fun isActiveInPhase(check: PositionCheck, phase: Phase): Boolean {
        if (phase == Phase.IDLE) return false
        if (check.activePhases.any { normalizeActivePhaseToken(it) == "all" }) return true
        val phaseName = when (phase) {
            Phase.START -> "top"
            else -> phase.name.lowercase()
        }
        return check.activePhases.any { normalizeActivePhaseToken(it) == phaseName }
    }
    
    /**
     * Validate a single position check
     */
    private fun validateCheck(
        check: PositionCheck,
        landmarks: List<Landmark>,
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
        primary: Landmark,
        secondary: Landmark,
        threshold: Double,
        cameraResult: CameraPositionDetector.CameraDetectionResult,
        facing: CameraPositionDetector.DetectedFacing
    ): CheckResult {
        val (primaryValue, secondaryValue) = getForwardAxisValues(
            primary, secondary, cameraResult.position, facing
        )
        
        val diff = primaryValue - secondaryValue
        
        val passed = when (check.condition.operator) {
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold
            PositionOperator.SHOULD_EXCEED -> diff >= threshold
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed(diff.toDouble(), threshold)
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
        primary: Landmark,
        secondary: Landmark,
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
        primary: Landmark,
        secondary: Landmark,
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
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold
            PositionOperator.SHOULD_EXCEED -> diff >= threshold
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold
            else -> true
        }

        return if (passed) CheckResult.passed(diff.toDouble(), threshold)
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
        primary: Landmark,
        secondary: Landmark,
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
            PositionOperator.APPROXIMATELY_EQUAL -> abs(diff) <= threshold
            PositionOperator.SHOULD_NOT_EXCEED -> diff <= threshold
            PositionOperator.SHOULD_EXCEED -> diff >= threshold
            else -> true
        }

        return if (passed) CheckResult.passed(diff.toDouble(), threshold)
        else CheckResult.failed(createError(check, diff.toDouble(), threshold))
    }
    
    /**
     * Validate distance ratio between two pairs of landmarks
     */
    private fun validateDistanceRatio(
        check: PositionCheck,
        landmarks: List<Landmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped("Primary landmark not found")
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped("Secondary landmark not found")
        val l3 = check.landmarks.tertiary?.let { getLandmark(it, landmarks) } 
            ?: return CheckResult.skipped("Tertiary landmark not configured or not found")
        val l4 = check.landmarks.quaternary?.let { getLandmark(it, landmarks) } 
            ?: return CheckResult.skipped("Quaternary landmark not configured or not found")
        
        // Check visibility
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold) ||
            !l3.isVisible(visibilityThreshold) || !l4.isVisible(visibilityThreshold)) {
            return CheckResult.skipped("Landmarks not visible")
        }
        
        // Calculate distances
        val distance1 = calculate3DDistance(l1, l2)
        val distance2 = calculate3DDistance(l3, l4)
        
        if (distance2 < 0.001f) return CheckResult.skipped("Reference distance too small")  // Avoid division by zero
        
        val ratio = distance1 / distance2
        
        val passed = when (check.condition.operator) {
            PositionOperator.GREATER_THAN_RATIO -> ratio >= threshold
            PositionOperator.LESS_THAN_RATIO -> ratio <= threshold
            else -> true
        }
        
        return if (passed) {
            CheckResult.passed(ratio.toDouble(), threshold)
        } else {
            CheckResult.failed(createError(check, ratio.toDouble(), threshold))
        }
    }
    
    /**
     * Validate horizontal alignment (3 points on same Y level)
     */
    private fun validateHorizontalAlignment(
        check: PositionCheck,
        landmarks: List<Landmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped("Primary landmark not found")
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped("Secondary landmark not found")
        val l3 = check.landmarks.tertiary?.let { getLandmark(it, landmarks) }
        
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold)) {
            return CheckResult.skipped("Landmarks not visible")
        }
        
        // Check if all Y values are within threshold
        val yValues = mutableListOf(l1.y, l2.y)
        if (l3 != null && l3.isVisible(visibilityThreshold)) {
            yValues.add(l3.y)
        }
        
        val maxY = yValues.maxOrNull() ?: return CheckResult.skipped("No Y values")
        val minY = yValues.minOrNull() ?: return CheckResult.skipped("No Y values")
        val range = maxY - minY
        
        val passed = range <= threshold
        
        return if (passed) {
            CheckResult.passed(range.toDouble(), threshold)
        } else {
            CheckResult.failed(createError(check, range.toDouble(), threshold))
        }
    }
    
    /**
     * Validate vertical alignment (points on same X level)
     */
    private fun validateVerticalAlignment(
        check: PositionCheck,
        landmarks: List<Landmark>,
        threshold: Double
    ): CheckResult {
        val l1 = getLandmark(check.landmarks.primary, landmarks) ?: return CheckResult.skipped("Primary landmark not found")
        val l2 = getLandmark(check.landmarks.secondary, landmarks) ?: return CheckResult.skipped("Secondary landmark not found")
        val l3 = check.landmarks.tertiary?.let { getLandmark(it, landmarks) }
        
        if (!l1.isVisible(visibilityThreshold) || !l2.isVisible(visibilityThreshold)) {
            return CheckResult.skipped("Landmarks not visible")
        }
        
        val xValues = mutableListOf(l1.x, l2.x)
        if (l3 != null && l3.isVisible(visibilityThreshold)) {
            xValues.add(l3.x)
        }
        
        val maxX = xValues.maxOrNull() ?: return CheckResult.skipped("No X values")
        val minX = xValues.minOrNull() ?: return CheckResult.skipped("No X values")
        val range = maxX - minX
        
        val passed = range <= threshold
        
        return if (passed) {
            CheckResult.passed(range.toDouble(), threshold)
        } else {
            CheckResult.failed(createError(check, range.toDouble(), threshold))
        }
    }
    
    /**
     * Validate depth alignment (landmarks at same Z level)
     */
    private fun validateDepthAlignment(
        check: PositionCheck,
        primary: Landmark,
        secondary: Landmark,
        threshold: Double
    ): CheckResult {
        val zDiff = abs(primary.z - secondary.z)
        val passed = zDiff <= threshold
        
        return if (passed) {
            CheckResult.passed(zDiff.toDouble(), threshold)
        } else {
            CheckResult.failed(createError(check, zDiff.toDouble(), threshold))
        }
    }
    
    // ==================== Helper Methods ====================
    
    private fun getLandmark(name: String, landmarks: List<Landmark>): Landmark? {
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
    
    private fun calculate3DDistance(l1: Landmark, l2: Landmark): Float {
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
     * Clear cooldowns and reset scene detector (call when exercise run resets)
     */
    fun lockScene() {
        cachedSceneResult?.let { scene ->
            lockedSceneResult = scene
            lockedCameraResult = CameraPositionDetector.CameraDetectionResult(
                scene.direction, scene.directionConfidence,
                scene.facing, scene.closerSide, scene.depthInfo
            )
        }
    }

    fun unlockScene() {
        lockedSceneResult = null
        lockedCameraResult = null
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
    val detectedFacing: CameraPositionDetector.DetectedFacing,
    val debugChecks: List<PositionCheckDebug> = emptyList()
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
 * Per-check diagnostics emitted from the same validator path as production checks.
 */
data class PositionCheckDebug(
    val checkId: String,
    val type: PositionCheckType,
    val status: PositionCheckDebugStatus,
    val actualValue: Double?,
    val threshold: Double,
    val skipReason: String? = null,
    val frameCount: Int = 0,
    val requiredFrames: Int = 0,
    val landmark1: String,
    val landmark2: String
)

enum class PositionCheckDebugStatus {
    PASS,
    FAIL,
    FAIL_PENDING,
    SKIPPED
}

/**
 * Internal check result
 */
internal data class CheckResult(
    val passed: Boolean,
    val skipped: Boolean = false,
    val error: PositionError? = null,
    val skipReason: String? = null,
    val actualValue: Double? = null,
    val threshold: Double? = null
) {
    companion object {
        fun passed(actualValue: Double, threshold: Double) =
            CheckResult(passed = true, actualValue = actualValue, threshold = threshold)

        fun failed(error: PositionError) =
            CheckResult(
                passed = false,
                error = error,
                actualValue = error.actualValue,
                threshold = error.threshold
            )

        fun skipped(reason: String = "") = CheckResult(passed = true, skipped = true, skipReason = reason)
    }
}
