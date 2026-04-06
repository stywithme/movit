package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.*

/**
 * FormValidator - STATE-BASED Form Validation
 *
 * Validates exercise form using the unified JointState system.
 * This is the SINGLE SOURCE OF TRUTH for form quality assessment.
 *
 * State Hierarchy (by priority):
 * 1. DANGER  → Injury risk, invalidates rep
 * 2. WARNING → Error, doesn't count rep
 * 3. PERFECT → Ideal form, full score
 * 4. NORMAL  → Good form, partial score
 * 5. PAD     → Acceptable form, minimal score
 * 6. TRANSITION → Movement zone, no scoring
 *
 * Key Changes from Previous Version:
 * - Removed difficulty parameter (no more beginner/normal/advanced)
 * - Uses JointState instead of JointZone for quality assessment
 * - Unified decision making from StateConfig
 * - Hysteresis applied between state transitions
 */
class FormValidator(
    private val trackedJoints: List<TrackedJoint>
) {

    companion object {
        private const val TAG = "FormValidator"

        // Hysteresis degrees for state transitions
        private const val STATE_HYSTERESIS_NORMAL_PAD = 3.0      // Between NORMAL ↔ PAD
        private const val STATE_HYSTERESIS_PAD_WARNING = 2.0     // Between PAD ↔ WARNING
        private const val STATE_HYSTERESIS_WARNING_DANGER = 2.0  // Between WARNING ↔ DANGER

        // Minimum consecutive frames to confirm DANGER (safety smoothing)
        private const val MIN_DANGER_FRAMES = 3
    }

    // Previous states for hysteresis (prevents flickering)
    private val previousStates = mutableMapOf<String, JointState>()

    // Danger frame counter for each joint (safety smoothing)
    private val dangerFrameCounts = mutableMapOf<String, Int>()

    // Last phase per joint for phase-change detection (secondary joints with phaseRanges)
    private val previousPhases = mutableMapOf<String, Phase>()

    // ==================== Reusable Collections (Performance Optimization) ====================
    // Pre-allocated to avoid allocation on every frame

    private val reusableStateInfos = mutableMapOf<String, JointStateInfo>()
    private val reusableErrors = mutableListOf<JointError>()

    // ==================== Configurable Thresholds ====================

    /**
     * Boundary buffer from global settings (prevents flickering at validation boundaries)
     */
    private val boundaryBuffer: Double = SettingsManager.getBoundaryBuffer()

    // ==================== Main Validation Methods ====================

    /**
     * Validate all tracked joints and return state information
     *
     * @param currentAngles Map of joint code to current angle
     * @param currentPhase Current phase of exercise (for context)
     * @return Map of joint code to JointStateInfo (immutable copy for thread safety)
     */
    fun getJointStateInfos(
        currentAngles: Map<String, Double>,
        currentPhase: Phase = Phase.IDLE
    ): Map<String, JointStateInfo> {
        reusableStateInfos.clear()

        for (joint in trackedJoints) {
            val currentAngle = currentAngles[joint.joint] ?: continue

            if (!isJointActiveInPhase(joint, currentPhase)) {
                clearJointCaches(joint.joint)
                continue
            }

            val stateInfo = determineJointStateInfo(joint, currentAngle, currentPhase)
            reusableStateInfos[joint.joint] = stateInfo
        }

        return reusableStateInfos.toMap()
    }

    /**
     * Determine if a joint should be evaluated in the current phase.
     * Secondary joints with phaseRanges are ONLY active in phases that have a defined range.
     */
    private fun isJointActiveInPhase(joint: TrackedJoint, phase: Phase): Boolean {
        if (joint.role != JointRole.SECONDARY || joint.phaseRanges == null) return true
        val phaseName = mapPhaseToName(phase) ?: return false
        return joint.getPhaseRange(phaseName) != null
    }

    /**
     * Clear cached hysteresis / danger-frame state for a joint.
     * Must be called when a joint transitions from active to inactive
     * so stale state doesn't carry over to the next active phase.
     */
    private fun clearJointCaches(jointCode: String) {
        previousStates.remove(jointCode)
        dangerFrameCounts.remove(jointCode)
        previousPhases.remove(jointCode)
    }

    /**
     * Legacy validation method - wraps state info into ValidationResult
     *
     * @deprecated Use getJointStateInfos() instead for new code.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use getJointStateInfos() instead. Will be removed in future version.")
    fun validate(
        currentAngles: Map<String, Double>,
        currentPhase: Phase
    ): ValidationResult {
        return validateFromStateInfos(getJointStateInfos(currentAngles, currentPhase))
    }

    /**
     * Build ValidationResult from pre-computed JointStateInfos.
     * Avoids redundant getJointStateInfos() recalculation.
     */
    @Suppress("DEPRECATION")
    fun validateFromStateInfos(stateInfos: Map<String, JointStateInfo>): ValidationResult {
        val jointStatuses = mutableMapOf<String, JointStatus>()
        reusableErrors.clear()

        for ((jointCode, stateInfo) in stateInfos) {
            val joint = trackedJoints.find { it.joint == jointCode } ?: continue

            val isCorrect = stateInfo.state in listOf(
                JointState.PERFECT, JointState.NORMAL, JointState.PAD, JointState.TRANSITION
            )

            val expectedRange = resolveExpectedRangeForState(stateInfo.stateRanges, stateInfo.state)
            val errorType = resolveErrorType(
                angle = stateInfo.currentAngle,
                stateRanges = stateInfo.stateRanges,
                expectedRange = expectedRange
            )

            val errorMessage = joint.stateMessages?.getMessage(stateInfo.state, stateInfo.currentZone)
                ?: joint.getStartPoseMessage(errorType)

            val status = JointStatus(
                jointCode = jointCode,
                isCorrect = isCorrect,
                currentAngle = stateInfo.currentAngle,
                error = if (!isCorrect) {
                    JointError(
                        jointCode = jointCode,
                        errorType = errorType,
                        actualAngle = stateInfo.currentAngle,
                        expectedMin = expectedRange.min,
                        expectedMax = expectedRange.max,
                        message = errorMessage,
                        state = stateInfo.state,
                        isPrimary = stateInfo.isPrimary
                    )
                } else null
            )

            jointStatuses[jointCode] = status

            if (!isCorrect && status.error != null) {
                reusableErrors.add(status.error)
            }
        }

        return ValidationResult(
            isCorrect = reusableErrors.isEmpty(),
            jointStatuses = jointStatuses,
            errors = reusableErrors.toList()
        )
    }

    // ==================== State Determination ====================

    /**
     * Determine complete JointStateInfo for a joint
     *
     * @param joint TrackedJoint configuration
     * @param angle Current angle in degrees
     * @return Complete JointStateInfo with all decision data
     */
    private fun determineJointStateInfo(
        joint: TrackedJoint,
        angle: Double,
        currentPhase: Phase = Phase.IDLE
    ): JointStateInfo {
        val isPrimary = joint.role == JointRole.PRIMARY

        // Track phase changes for secondary joints with phaseRanges.
        // Caches are cleared on deactivation in clearJointCaches(); here we
        // only need to reset on active-to-active phase transitions.
        if (!isPrimary && joint.phaseRanges != null) {
            val lastPhase = previousPhases[joint.joint]
            if (lastPhase != null && lastPhase != currentPhase) {
                previousStates.remove(joint.joint)
                dangerFrameCounts.remove(joint.joint)
            }
            previousPhases[joint.joint] = currentPhase
        }

        // Determine zone type (UP, DOWN, or TRANSITION)
        val zoneType = if (isPrimary && joint.hasStateUpDownRanges()) {
            joint.determineZoneType(angle)
        } else {
            ZoneType.UP_ZONE // SECONDARY joints use single zone
        }

        // Get the applicable StateRanges for this zone
        val stateRanges = getApplicableStateRanges(joint, zoneType, currentPhase)

        // Get UP and DOWN ranges for track rendering (each track needs its own ranges)
        // For secondary joints: use stateRanges (which may be phase-specific) for visual consistency
        val upStateRanges = if (joint.hasStateUpDownRanges()) {
            joint.getStateUpRange()
        } else {
            stateRanges ?: if (joint.hasStateHoldRange()) joint.getStateHoldRange() else null
        }

        val downStateRanges = if (joint.hasStateUpDownRanges()) {
            joint.getStateDownRange()
        } else {
            stateRanges ?: if (joint.hasStateHoldRange()) joint.getStateHoldRange() else null
        }

        // Determine outward direction for filling undefined outer ranges
        val outward = when (zoneType) {
            ZoneType.UP_ZONE -> OutwardDirection.TOWARDS_HIGH
            ZoneType.DOWN_ZONE -> OutwardDirection.TOWARDS_LOW
            ZoneType.TRANSITION -> null
        }

        // Determine raw state from ranges (with outward fallback)
        val rawState = if (zoneType == ZoneType.TRANSITION) {
            JointState.TRANSITION
        } else {
            stateRanges?.determineState(angle, outward) ?: JointState.WARNING
        }

        // Angle beyond all defined ranges → outward fallback is unambiguous
        val isOutwardFallback = outward != null && stateRanges != null && when (outward) {
            OutwardDirection.TOWARDS_HIGH -> angle > stateRanges.outermostMax
            OutwardDirection.TOWARDS_LOW -> angle < stateRanges.outermostMin
        }

        // Apply hysteresis and danger smoothing
        val state = applyHysteresis(joint.joint, rawState, angle, stateRanges, isOutwardFallback)

        // Get messages for this state and zone
        val messages = joint.getMessagesForState(state, zoneType)

        return JointStateInfo.create(
            jointCode = joint.joint,
            state = state,
            isPrimary = isPrimary,
            currentAngle = angle,
            currentZone = zoneType,
            stateRanges = stateRanges,
            upStateRanges = upStateRanges,
            downStateRanges = downStateRanges,
            messages = messages,
            invertIndicator = joint.invertIndicator
        )
    }

    /**
     * Get applicable StateRanges based on zone type.
     *
     * For secondary joints with phaseRanges: returns ONLY the phase-specific range.
     * The caller (getJointStateInfos) already guarantees we only reach here when
     * isJointActiveInPhase() returned true, so getPhaseRange() will not be null.
     */
    private fun getApplicableStateRanges(
        joint: TrackedJoint,
        zoneType: ZoneType,
        currentPhase: Phase
    ): StateRanges? {
        if (joint.role == JointRole.SECONDARY && joint.phaseRanges != null) {
            val phaseName = mapPhaseToName(currentPhase) ?: return null
            return joint.getPhaseRange(phaseName)
        }
        return when {
            joint.hasStateHoldRange() -> joint.getStateHoldRange()
            zoneType == ZoneType.UP_ZONE && joint.hasStateUpDownRanges() -> joint.getStateUpRange()
            zoneType == ZoneType.DOWN_ZONE && joint.hasStateUpDownRanges() -> joint.getStateDownRange()
            else -> null
        }
    }

    private fun mapPhaseToName(phase: Phase): String? {
        return when (phase) {
            Phase.START -> "top"
            Phase.DOWN -> "down"
            Phase.BOTTOM -> "bottom"
            Phase.UP -> "up"
            Phase.COUNT -> "all"
            Phase.IDLE -> null
        }
    }

    /**
     * Resolve the expected range for the current state from active zone ranges.
     * This guarantees report data uses the same configured state thresholds.
     */
    private fun resolveExpectedRangeForState(stateRanges: StateRanges?, state: JointState): AngleRange {
        if (stateRanges == null) return AngleRange(0.0, 180.0)

        return when (state) {
            JointState.PERFECT -> stateRanges.perfect
            JointState.NORMAL -> stateRanges.normal ?: stateRanges.perfect
            JointState.PAD -> stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
            JointState.WARNING -> stateRanges.warning ?: stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
            JointState.DANGER -> stateRanges.danger ?: stateRanges.warning ?: stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
            JointState.TRANSITION -> AngleRange(stateRanges.effectiveMin, stateRanges.effectiveMax)
        }
    }

    /**
     * Determine if an error is TOO_HIGH or TOO_LOW relative to counted boundaries first,
     * then fallback to the expected range itself for edge overlap cases.
     */
    private fun resolveErrorType(
        angle: Double,
        stateRanges: StateRanges?,
        expectedRange: AngleRange
    ): ErrorType {
        stateRanges?.let { ranges ->
            if (angle > ranges.effectiveMax) return ErrorType.TOO_HIGH
            if (angle < ranges.effectiveMin) return ErrorType.TOO_LOW
        }

        if (angle > expectedRange.max) return ErrorType.TOO_HIGH
        if (angle < expectedRange.min) return ErrorType.TOO_LOW

        val midpoint = (expectedRange.min + expectedRange.max) / 2.0
        return if (angle >= midpoint) ErrorType.TOO_HIGH else ErrorType.TOO_LOW
    }

    /**
     * Apply hysteresis to prevent state flickering
     *
     * Uses degree-based hysteresis for smooth state transitions:
     * - DANGER entry: Requires MIN_DANGER_FRAMES consecutive frames (safety)
     * - DANGER exit: Must move far enough from danger zone
     * - Other transitions: Apply degree buffer based on state pair
     *
     * @param jointCode Joint identifier
     * @param rawState Newly determined state
     * @param angle Current angle
     * @param stateRanges Active state ranges for reference
     * @return Final state after hysteresis
     */
    private fun applyHysteresis(
        jointCode: String,
        rawState: JointState,
        angle: Double,
        stateRanges: StateRanges?,
        isOutwardFallback: Boolean = false
    ): JointState {
        val previousState = previousStates[jointCode]

        // First frame: don't allow immediate DANGER to avoid startup spikes.
        if (previousState == null) {
            val initialState = if (rawState == JointState.DANGER && !isOutwardFallback) JointState.WARNING else rawState
            previousStates[jointCode] = initialState
            dangerFrameCounts[jointCode] = if (rawState == JointState.DANGER && !isOutwardFallback) 1 else 0
            return initialState
        }

        // CRITICAL: Reset danger frame count if NOT in DANGER
        if (rawState != JointState.DANGER) {
            dangerFrameCounts[jointCode] = 0
        }

        // Same state - no change needed
        if (rawState == previousState) {
            if (rawState == JointState.DANGER) {
                dangerFrameCounts[jointCode] = (dangerFrameCounts[jointCode] ?: 0) + 1
            }
            return rawState
        }

        // Outward fallback: angle is beyond all defined ranges.
        // Classification is unambiguous — skip hysteresis to avoid sticking.
        if (isOutwardFallback) {
            previousStates[jointCode] = rawState
            dangerFrameCounts[jointCode] = 0
            return rawState
        }

        // Get hysteresis degree for this transition
        val hysteresisDegree = getHysteresisDegree(previousState, rawState)

        // Handle DANGER entry (requires frame count confirmation for safety)
        if (rawState == JointState.DANGER && previousState != JointState.DANGER) {
            val count = (dangerFrameCounts[jointCode] ?: 0) + 1
            dangerFrameCounts[jointCode] = count

            if (count >= MIN_DANGER_FRAMES) {
                previousStates[jointCode] = JointState.DANGER
                return JointState.DANGER
            }
            return previousState
        }

        // Handle transitions using degree-based hysteresis
        if (stateRanges != null && hysteresisDegree > 0) {
            // Check if angle has moved far enough to confirm transition
            val transitionConfirmed = checkTransitionConfirmed(
                previousState = previousState,
                newState = rawState,
                angle = angle,
                stateRanges = stateRanges,
                hysteresisDegree = hysteresisDegree
            )

            if (!transitionConfirmed) {
                return previousState
            }
        }

        // Transition confirmed
        previousStates[jointCode] = rawState
        return rawState
    }

    /**
     * Get hysteresis degree for a state transition pair
     */
    private fun getHysteresisDegree(from: JointState, to: JointState): Double {
        return when {
            // DANGER transitions
            from == JointState.DANGER || to == JointState.DANGER -> STATE_HYSTERESIS_WARNING_DANGER
            // WARNING transitions
            from == JointState.WARNING || to == JointState.WARNING -> STATE_HYSTERESIS_PAD_WARNING
            // PAD/NORMAL transitions
            from == JointState.PAD || to == JointState.PAD -> STATE_HYSTERESIS_NORMAL_PAD
            // PERFECT/NORMAL - minimal hysteresis
            else -> 1.0
        }
    }

    /**
     * Check if angle has moved far enough from boundary to confirm transition
     *
     * FIXED: Now applies hysteresis to ALL transitions including degradation (WARNING/TRANSITION)
     * This prevents flickering when angle is near boundaries due to camera noise.
     */
    private fun checkTransitionConfirmed(
        previousState: JointState,
        newState: JointState,
        angle: Double,
        stateRanges: StateRanges,
        hysteresisDegree: Double
    ): Boolean {
        // Minimum margin for ALL transitions (prevents boundary flickering)
        val minMargin = 1.5  // degrees

        // Check if angle is solidly inside the new state's range
        return when (newState) {
            JointState.PERFECT -> {
                val range = stateRanges.perfect
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.NORMAL -> {
                val range = stateRanges.normal ?: return true
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.PAD -> {
                val range = stateRanges.pad ?: return true
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.WARNING -> {
                val range = stateRanges.warning
                if (range != null) {
                    angle >= (range.min + minMargin) && angle <= (range.max - minMargin)
                } else {
                    // No explicit WARNING range: require minimum distance from counted boundary
                    val outerBound = stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
                    val distFromMax = if (angle > outerBound.max) angle - outerBound.max else 0.0
                    val distFromMin = if (angle < outerBound.min) outerBound.min - angle else 0.0
                    maxOf(distFromMax, distFromMin) >= minMargin
                }
            }
            JointState.TRANSITION -> {
                // For TRANSITION: require minMargin inside transition zone
                // This is handled separately since TRANSITION doesn't have explicit ranges
                true  // Let zone determination handle it
            }
            else -> true
        }
    }

    // ==================== Query Methods ====================

    /**
     * Check if user is in valid start position (all primary joints in UP zone with counted state)
     *
     * For REP exercises: Checks if in UP_ZONE with counted state
     * For HOLD exercises: Checks if in hold range with counted state
     */
    fun isInStartPosition(currentAngles: Map<String, Double>): Boolean {
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue

            val currentAngle = currentAngles[joint.joint] ?: return false

            when {
                joint.hasStateUpDownRanges() -> {
                    // REP exercise: check UP zone
                    val zoneType = joint.determineZoneType(currentAngle)
                    if (zoneType != ZoneType.UP_ZONE) return false

                    val upRange = joint.getStateUpRange()
                    if (!upRange.isInCountedState(currentAngle)) return false
                }
                joint.hasStateHoldRange() -> {
                    // HOLD exercise: check hold range
                    val holdRange = joint.getStateHoldRange()
                    if (!holdRange.isInCountedState(currentAngle)) return false
                }
                else -> {
                    // No state ranges defined - skip this joint
                    continue
                }
            }
        }
        return true
    }

    /**
     * Check if user is in valid startPose (pre-training check)
     * Uses startPose which is independent of state ranges
     */
    fun isInStartPose(currentAngles: Map<String, Double>): Boolean {
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue

            val currentAngle = currentAngles[joint.joint] ?: return false
            if (!joint.isInStartPose(currentAngle)) {
                return false
            }
        }
        return true
    }

    /**
     * Get feedback for getting into start position
     *
     * For REP exercises: Checks UP range perfect zone
     * For HOLD exercises: Checks hold range perfect zone
     *
     * Uses stateMessages.warning for feedback (falls back to generic message)
     */
    fun getStartPositionFeedback(currentAngles: Map<String, Double>): List<JointError> {
        val errors = mutableListOf<JointError>()

        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue

            val currentAngle = currentAngles[joint.joint] ?: continue

            // Get the perfect range based on exercise type
            val perfectRange = when {
                joint.hasStateUpDownRanges() -> joint.getStateUpRange().perfect
                joint.hasStateHoldRange() -> joint.getStateHoldRange().perfect
                else -> continue
            }

            if (currentAngle > perfectRange.max + boundaryBuffer) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_HIGH,
                    actualAngle = currentAngle,
                    expectedMin = perfectRange.min,
                    expectedMax = perfectRange.max,
                    message = joint.getStartPoseMessage(ErrorType.TOO_HIGH),
                    state = JointState.WARNING,
                    isPrimary = joint.role == JointRole.PRIMARY
                ))
            } else if (currentAngle < perfectRange.min - boundaryBuffer) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_LOW,
                    actualAngle = currentAngle,
                    expectedMin = perfectRange.min,
                    expectedMax = perfectRange.max,
                    message = joint.getStartPoseMessage(ErrorType.TOO_LOW),
                    state = JointState.WARNING,
                    isPrimary = joint.role == JointRole.PRIMARY
                ))
            }
        }

        return errors
    }

    /**
     * Get feedback for getting into startPose (pre-training)
     *
     * Uses stateMessages.warning for feedback (falls back to generic message)
     */
    fun getStartPoseFeedback(currentAngles: Map<String, Double>): List<JointError> {
        val errors = mutableListOf<JointError>()

        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue

            val currentAngle = currentAngles[joint.joint] ?: continue

            if (currentAngle > joint.startPose.max) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_HIGH,
                    actualAngle = currentAngle,
                    expectedMin = joint.startPose.min,
                    expectedMax = joint.startPose.max,
                    message = joint.getStartPoseMessage(ErrorType.TOO_HIGH),
                    state = JointState.WARNING,
                    isPrimary = true
                ))
            } else if (currentAngle < joint.startPose.min) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_LOW,
                    actualAngle = currentAngle,
                    expectedMin = joint.startPose.min,
                    expectedMax = joint.startPose.max,
                    message = joint.getStartPoseMessage(ErrorType.TOO_LOW),
                    state = JointState.WARNING,
                    isPrimary = true
                ))
            }
        }

        return errors
    }

    /**
     * Get worst state across all joints
     * Used for rep scoring
     */
    fun getWorstState(stateInfos: Map<String, JointStateInfo>): JointState {
        val qualityStates = stateInfos.values
            .map { it.state }
            .filter { it != JointState.TRANSITION }

        return JointState.getWorst(qualityStates)
    }

    /**
     * Check if any joint is in DANGER state
     */
    fun hasDangerState(stateInfos: Map<String, JointStateInfo>): Boolean {
        return stateInfos.values.any { it.state == JointState.DANGER }
    }

    /**
     * Legacy method - Get visual info for all tracked joints
     * @deprecated Use getJointStateInfos() instead.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use getJointStateInfos() instead. Will be removed in future version.")
    fun getJointArrowInfos(currentAngles: Map<String, Double>): Map<String, JointArrowInfo> {
        return buildJointArrowInfos(getJointStateInfos(currentAngles))
    }

    /**
     * Build JointArrowInfos from pre-computed JointStateInfos.
     * Avoids redundant getJointStateInfos() recalculation.
     */
    @Suppress("DEPRECATION")
    fun buildJointArrowInfos(stateInfos: Map<String, JointStateInfo>): Map<String, JointArrowInfo> {
        val arrowInfos = mutableMapOf<String, JointArrowInfo>()

        for ((jointCode, stateInfo) in stateInfos) {
            val joint = trackedJoints.find { it.joint == jointCode } ?: continue

            val zone = when (stateInfo.currentZone) {
                ZoneType.UP_ZONE -> when (stateInfo.state) {
                    JointState.DANGER, JointState.WARNING -> {
                        val expectedRange = resolveExpectedRangeForState(stateInfo.stateRanges, stateInfo.state)
                        when (
                            resolveErrorType(
                                angle = stateInfo.currentAngle,
                                stateRanges = stateInfo.stateRanges,
                                expectedRange = expectedRange
                            )
                        ) {
                            ErrorType.TOO_HIGH -> JointZone.TOO_HIGH
                            ErrorType.TOO_LOW -> JointZone.TOO_LOW
                        }
                    }
                    else -> JointZone.UP_ZONE
                }
                ZoneType.DOWN_ZONE -> when (stateInfo.state) {
                    JointState.DANGER, JointState.WARNING -> {
                        val expectedRange = resolveExpectedRangeForState(stateInfo.stateRanges, stateInfo.state)
                        when (
                            resolveErrorType(
                                angle = stateInfo.currentAngle,
                                stateRanges = stateInfo.stateRanges,
                                expectedRange = expectedRange
                            )
                        ) {
                            ErrorType.TOO_HIGH -> JointZone.TOO_HIGH
                            ErrorType.TOO_LOW -> JointZone.TOO_LOW
                        }
                    }
                    else -> JointZone.DOWN_ZONE
                }
                ZoneType.TRANSITION -> JointZone.TRANSITION
            }

            val isError = stateInfo.state == JointState.DANGER || stateInfo.state == JointState.WARNING
            val isWarning = stateInfo.state == JointState.PAD

            val upRange = if (joint.hasStateUpDownRanges()) {
                joint.getStateUpRange()
            } else if (joint.hasStateHoldRange()) {
                joint.getStateHoldRange()
            } else null

            val downRange = if (joint.hasStateUpDownRanges()) {
                joint.getStateDownRange()
            } else upRange

            arrowInfos[jointCode] = JointArrowInfo(
                jointCode = jointCode,
                zone = zone,
                isError = isError,
                isWarning = isWarning,
                isPrimary = stateInfo.isPrimary,
                currentAngle = stateInfo.currentAngle,
                upRangeMin = upRange?.perfect?.min ?: 0.0,
                upRangeMax = upRange?.perfect?.max ?: 180.0,
                downRangeMin = downRange?.perfect?.min ?: 0.0,
                downRangeMax = downRange?.perfect?.max ?: 180.0
            )
        }

        return arrowInfos
    }

    /**
     * Reset state (call when training starts/restarts)
     */
    fun reset() {
        previousStates.clear()
        dangerFrameCounts.clear()
        previousPhases.clear()
        reusableStateInfos.clear()
        reusableErrors.clear()
        Log.d(TAG, "FormValidator state reset")
    }
}

/**
 * ValidationResult - Result of form validation
 *
 * @deprecated Use getJointStateInfos() for new code.
 */
@Deprecated("Use getJointStateInfos() method instead. Will be removed in future version.")
data class ValidationResult(
    val isCorrect: Boolean,
    @Suppress("DEPRECATION") val jointStatuses: Map<String, JointStatus>,
    val errors: List<JointError>
)

/**
 * JointStatus - Status of a single joint
 *
 * @deprecated Use JointStateInfo for new code.
 */
@Deprecated("Use JointStateInfo instead. Will be removed in future version.")
data class JointStatus(
    val jointCode: String,
    val isCorrect: Boolean,
    val currentAngle: Double,
    val error: JointError? = null
) {
    /**
     * Get color for this joint (for UI)
     */
    @Suppress("DEPRECATION")
    fun getColor(): JointColor {
        return when {
            isCorrect -> JointColor.CORRECT
            error?.errorType == ErrorType.TOO_HIGH -> JointColor.ERROR_HIGH
            error?.errorType == ErrorType.TOO_LOW -> JointColor.ERROR_LOW
            else -> JointColor.DEFAULT
        }
    }
}

/**
 * Joint color enum for UI
 *
 * @deprecated Use StateConfig.getColor(JointState) instead.
 */
@Deprecated("Use StateConfig.getColor(JointState) instead. Will be removed in future version.")
enum class JointColor {
    DEFAULT,    // Not tracked or no data
    CORRECT,    // Within range (green)
    ERROR_HIGH, // Too high - not bending enough (red)
    ERROR_LOW   // Too low - bending too much (orange)
}

/**
 * JointZone - Legacy enum for backward compatibility
 *
 * @deprecated Use JointState from JointState.kt for new code.
 * This is kept for overlay compatibility during migration.
 */
@Deprecated("Use JointState instead. Will be removed in future version.")
enum class JointZone {
    TOO_HIGH,       // Error: above valid range
    UP_ZONE,        // In up/start position
    TRANSITION,     // Moving between positions
    DOWN_ZONE,      // In down/target position
    TOO_LOW         // Error: below valid range
}

/**
 * JointArrowInfo - Legacy data class for backward compatibility
 *
 * @deprecated Use JointStateInfo from JointState.kt for new code.
 * This is kept for overlay compatibility during migration.
 */
@Deprecated("Use JointStateInfo instead. Will be removed in future version.")
data class JointArrowInfo(
    val jointCode: String,
    @Suppress("DEPRECATION") val zone: JointZone,
    val isError: Boolean,
    val isWarning: Boolean = false,
    val isPrimary: Boolean = true,
    val currentAngle: Double,
    val upRangeMin: Double,
    val upRangeMax: Double,
    val downRangeMin: Double,
    val downRangeMax: Double
)
