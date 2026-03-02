package com.trainingvalidator.poc.training.models

import com.google.gson.annotations.SerializedName

/**
 * ExerciseConfig - Complete exercise configuration from JSON
 * 
 * This represents the full exercise data structure as defined by the admin dashboard.
 * Supports multiple pose variants and tracked joints with STATE-BASED quality assessment.
 * 
 * NOTE: Difficulty levels have been REMOVED. All users get the same exercise,
 * and quality is assessed based on JointState (PERFECT/NORMAL/PAD/WARNING/DANGER).
 */
data class ExerciseConfig(
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val instructions: LocalizedText? = null,
    val imageUrl: String? = null,
    val category: CategoryInfo,
    val countingMethod: CountingMethod,
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val poseVariants: List<PoseVariant> = emptyList(),
    val repCountingConfig: RepCountingConfig = RepCountingConfig(), // Moved from DifficultyLevel
    
    // ═══════════════════════════════════════════════════════════════
    // WEIGHT & METRICS CONFIGURATION (from backend)
    // ═══════════════════════════════════════════════════════════════
    
    /** Does this exercise support weights? */
    val supportsWeight: Boolean = false,
    
    /** Weight limits (kg) */
    val minWeight: Float? = null,
    val maxWeight: Float? = null,
    val defaultWeight: Float? = null,
    
    /** Report metrics configuration */
    val reportMetrics: ReportMetricsConfig? = null,
    
    /** Does this exercise have position checks? (for Alignment metric) */
    val hasPositionChecks: Boolean = false,

    /** Bilateral config (per-rep side alternation) */
    val isBilateral: Boolean = false,
    val bilateralConfig: BilateralConfig? = null,
    
    // Runtime field - set by ExerciseLoader
    @Transient
    var fileName: String = ""
) {
    /**
     * Get pose variant by index (default first)
     */
    fun getPoseVariant(index: Int = 0): PoseVariant? {
        return poseVariants.getOrNull(index)
    }
    
    /**
     * Get primary joints for a pose variant
     */
    fun getPrimaryJoints(variantIndex: Int = 0): List<TrackedJoint> {
        return poseVariants.getOrNull(variantIndex)
            ?.trackedJoints
            ?.filter { it.role == JointRole.PRIMARY }
            ?: emptyList()
    }
    
    /**
     * Get secondary joints for a pose variant
     */
    fun getSecondaryJoints(variantIndex: Int = 0): List<TrackedJoint> {
        return poseVariants.getOrNull(variantIndex)
            ?.trackedJoints
            ?.filter { it.role == JointRole.SECONDARY }
            ?: emptyList()
    }
    
    /**
     * Get all tracked joints for a pose variant
     */
    fun getTrackedJoints(variantIndex: Int = 0): List<TrackedJoint> {
        return poseVariants.getOrNull(variantIndex)?.trackedJoints ?: emptyList()
    }
    
    // ═══════════════════════════════════════════════════════════════
    // EXERCISE TYPE DETECTION
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Check if this is a hold exercise
     */
    fun isHoldExercise(): Boolean = countingMethod == CountingMethod.HOLD
    
    /**
     * Check if this exercise has bilateral joints (left/right pairs)
     */
    fun isBilateralExercise(variantIndex: Int = 0): Boolean {
        return getTrackedJoints(variantIndex).any { it.pairedWith != null }
    }
    
    /**
     * Get the effective report metrics config
     * Falls back to auto-detected defaults if not configured
     */
    fun getEffectiveMetricsConfig(variantIndex: Int = 0): ReportMetricsConfig {
        return reportMetrics ?: MetricCode.getDefaults(
            isHold = isHoldExercise(),
            isBilateral = isBilateralExercise(variantIndex),
            supportsWeight = supportsWeight
        )
    }
    
    /**
     * Check if a specific metric should be shown for this exercise.
     * 
     * The backend sends a complete excluded list (user + auto-disabled),
     * so we just need to check if the metric is in the primary/optional
     * and not in the excluded list.
     */
    fun shouldShowMetric(metric: MetricCode, variantIndex: Int = 0): Boolean {
        val config = getEffectiveMetricsConfig(variantIndex)
        return config.shouldShow(metric)
    }
    
    /**
     * Check if this exercise has position checks defined (from local variants or server flag)
     */
    fun hasAnyPositionChecks(variantIndex: Int = 0): Boolean {
        return poseVariants.getOrNull(variantIndex)?.positionChecks?.isNotEmpty() == true ||
               hasPositionChecks
    }
    
    /**
     * Sanitize Gson-null fields.
     * 
     * Gson ignores Kotlin default parameter values when a JSON field is missing,
     * setting the field to null at runtime even if the Kotlin type is non-null.
     * This method ensures all fields have proper defaults after Gson deserialization.
     */
    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    fun sanitizeGsonDefaults(): ExerciseConfig = copy(
        fileName = fileName ?: "",
        poseVariants = (poseVariants ?: emptyList()).map { it.sanitizeGsonDefaults() },
        muscles = muscles ?: emptyList(),
        equipment = equipment ?: emptyList(),
        tags = tags ?: emptyList()
    )
}

/**
 * Bilateral configuration for an exercise.
 * Controls per-rep left/right side alternation.
 */
data class BilateralConfig(
    val switchEvery: Int = 1,       // Switch side every N reps (default: 1)
    val startSide: String = "right" // "left" or "right"
)

/**
 * Localized text (Arabic + English)
 * 
 * Optionally includes pre-generated audio file URLs for TTS replacement.
 * When audio URLs are available, the app can play cached audio files
 * instead of using Android's built-in TTS.
 */
data class LocalizedText(
    val ar: String = "",
    val en: String = "",
    /** Pre-generated Arabic audio file URL */
    val audioAr: String? = null,
    /** Pre-generated English audio file URL */
    val audioEn: String? = null
) {
    /**
     * Get text for the specified language
     */
    fun get(language: String = "en"): String {
        return if (language == "ar") ar else en
    }
    
    /**
     * Get audio URL for the specified language
     * @return Audio URL or null if not available
     */
    fun getAudioUrl(language: String = "en"): String? {
        return if (language == "ar") audioAr else audioEn
    }
    
    /**
     * Check if audio is available for the specified language
     */
    fun hasAudio(language: String = "en"): Boolean {
        return getAudioUrl(language)?.isNotBlank() == true
    }
}

/**
 * Category information
 */
data class CategoryInfo(
    val code: String,
    val name: LocalizedText
)

// ═══════════════════════════════════════════════════════════════════════════
// METRICS CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Report metrics configuration - determines which metrics to show in the report
 * 
 * Backend sends a comprehensive 'excluded' list that includes:
 * - User-disabled metrics (admin choice)
 * - Auto-disabled metrics (e.g., TEMPO for hold exercises, ALIGNMENT when no position checks)
 * 
 * @param primary Main metrics shown as cards (2-3 recommended)
 * @param optional Additional metrics admin selected
 * @param excluded Metrics explicitly hidden from report (includes auto-disabled)
 */
data class ReportMetricsConfig(
    val primary: List<MetricCode> = listOf(MetricCode.FORM_SCORE),
    val optional: List<MetricCode> = emptyList(),
    val excluded: List<MetricCode> = emptyList()
) {
    /**
     * Check if a metric should be displayed
     * Simply checks if the metric is NOT in the excluded list.
     */
    fun shouldShow(metric: MetricCode): Boolean {
        return !excluded.contains(metric)
    }
    
    /**
     * Check if a metric is primary (shown as main card)
     */
    fun isPrimary(metric: MetricCode): Boolean = primary.contains(metric)
    
    /**
     * Check if a metric is optional (shown as secondary)
     */
    fun isOptional(metric: MetricCode): Boolean = optional.contains(metric)
    
    /**
     * Get primary + optional metrics (suggested display order)
     */
    fun getVisibleMetrics(): List<MetricCode> = primary + optional
}

/**
 * Available metric codes - matches backend MetricCode
 */
enum class MetricCode {
    // Core (always available)
    FORM_SCORE,
    REP_COUNT,
    DURATION,
    
    // Kinematic
    ROM,
    SYMMETRY,
    STABILITY,
    
    // Temporal
    TEMPO,
    TUT,
    HOLD_DURATION,
    
    // Quality
    ALIGNMENT,
    FORM_CONSISTENCY,
    FATIGUE_INDEX,
    TEMPO_CONSISTENCY,
    
    // Power
    VELOCITY,
    VELOCITY_LOSS,
    
    // Load
    WEIGHT,
    VOLUME,
    EST_1RM;
    
    companion object {
        /**
         * Get default metrics for an exercise type
         */
        fun getDefaults(
            isHold: Boolean,
            isBilateral: Boolean,
            supportsWeight: Boolean
        ): ReportMetricsConfig {
            val primary = mutableListOf(FORM_SCORE)
            val optional = mutableListOf<MetricCode>()
            
            if (isHold) {
                primary.add(HOLD_DURATION)
                optional.add(STABILITY)
            } else {
                primary.add(ROM)
                optional.addAll(listOf(TEMPO, TUT))
            }
            
            if (isBilateral && !isHold) {
                primary.add(SYMMETRY)
            }
            
            if (supportsWeight) {
                primary.add(WEIGHT)
                optional.addAll(listOf(VOLUME, EST_1RM))
            }
            
            optional.add(ALIGNMENT)
            
            return ReportMetricsConfig(
                primary = primary.take(3), // Max 3 primary
                optional = optional
            )
        }
    }
}

/**
 * Counting method enum - determines the state machine type
 */
enum class CountingMethod {
    @SerializedName("up_down")
    UP_DOWN,        // Squat, Bicep Curl - up position then down
    
    @SerializedName("push_pull")
    PUSH_PULL,      // Push-up, Bench Press - extended then bent
    
    @SerializedName("hold")
    HOLD            // Plank, Wall Sit - hold position for time
}

/**
 * Pose variant - represents one pose position for an exercise.
 * 
 * @param posePosition 3-axis pose code (e.g. "standing_front", "prone_side")
 * @param positionChecks Position-based validation checks (knee-over-toe, alignment, etc.)
 */
data class PoseVariant(
    val name: LocalizedText,
    /** New field — pose position code (e.g. "standing_front", "prone_side"). */
    val posePosition: String? = null,
    /** @deprecated Legacy field — kept for backward compat with old JSON. */
    val cameraPosition: String? = null,
    val expectedPostures: List<String>? = null,
    val expectedDirections: List<String>? = null,
    val expectedRegions: List<String>? = null,
    val trackedJoints: List<TrackedJoint> = emptyList(),
    val positionChecks: List<PositionCheck> = emptyList(),
    val feedbackMessages: FeedbackMessages = FeedbackMessages(),
    val messageAssignments: List<MessageAssignment> = emptyList()
) {
    /**
     * Get tracked joint by code
     */
    fun getJoint(jointCode: String): TrackedJoint? {
        return trackedJoints.find { it.joint == jointCode }
    }
    
    /**
     * Get primary joints only
     */
    fun getPrimaryJoints(): List<TrackedJoint> {
        return trackedJoints.filter { it.role == JointRole.PRIMARY }
    }
    
    /**
     * Get secondary joints only
     */
    fun getSecondaryJoints(): List<TrackedJoint> {
        return trackedJoints.filter { it.role == JointRole.SECONDARY }
    }
    
    /**
     * Sanitize Gson-null fields for this PoseVariant and its children.
     */
    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    fun sanitizeGsonDefaults(): PoseVariant = copy(
        trackedJoints = trackedJoints ?: emptyList(),
        positionChecks = (positionChecks ?: emptyList()).map { it.sanitizeGsonDefaults() },
        feedbackMessages = (feedbackMessages ?: FeedbackMessages()).sanitizeGsonDefaults(),
        messageAssignments = messageAssignments ?: emptyList()
    )
}

/**
 * Joint role in exercise
 */
enum class JointRole {
    @SerializedName("primary")
    PRIMARY,    // Used for rep counting and main validation
    
    @SerializedName("secondary")
    SECONDARY   // Used for form feedback only
}

/**
 * Tracked joint configuration - STATE-BASED STRUCTURE
 * 
 * Uses StateRanges for unified quality assessment:
 * - stateUpRange: State-based ranges for UP/START position (PRIMARY only)
 * - stateDownRange: State-based ranges for DOWN/BOTTOM position (PRIMARY only)
 * - stateRange: Single StateRanges for SECONDARY joints (must stay stable)
 * 
 * Each StateRanges contains:
 * - perfect: Ideal angle range (required)
 * - normal: Good angle range (optional, overlaps perfect)
 * - pad: Acceptable range (optional, overlaps normal)
 * - warning: Error zone (optional, outer edges)
 * - danger: Injury risk zone (optional, extreme outer edges)
 * 
 * TRANSITION zone is calculated automatically from the gap between
 * stateUpRange and stateDownRange.
 * 
 * Example for Bicep Curl:
 *   PRIMARY (left_elbow):
 *     stateUpRange:   { perfect: {130-150}, normal: {120-160}, warning: {160-170} }
 *     stateDownRange: { perfect: {40-70}, normal: {30-80} }
 *   SECONDARY (right_elbow):
 *     stateRange:     { perfect: {150-170}, warning: {170-180} }
 */
data class TrackedJoint(
    val joint: String,                          // Joint code (e.g., "left_elbow")
    val role: JointRole,                        // primary or secondary
    val startPose: AngleRange,                  // For pre-training position check
    
    // State-based ranges (replaces DifficultyRanges)
    // Keep JSON keys aligned with plan: upRange/downRange/range
    @SerializedName(value = "upRange", alternate = ["stateUpRange"])
    val upRange: StateRanges? = null,           // State ranges for UP position (PRIMARY)
    @SerializedName(value = "downRange", alternate = ["stateDownRange"])
    val downRange: StateRanges? = null,         // State ranges for DOWN position (PRIMARY)
    @SerializedName(value = "range", alternate = ["stateRange"])
    val range: StateRanges? = null,             // State ranges for SECONDARY (hold)
    
    // State-specific messages (MEDIUM priority feedback)
    // Messages for each JointState: perfect, normal, pad, warning, danger
    val stateMessages: StateMessages? = null,
    
    val pairedWith: String? = null,             // Paired joint code (for symmetry)
    
    // Visual indicator direction control
    // When true: Swaps upRange/downRange for visual indicator display
    // Use when the LOWER limb moves UP (like bicep curl: forearm moves up towards shoulder)
    // Default false: Upper limb moves down (like squat: thigh moves down)
    val invertIndicator: Boolean = false
) {
    // ==================== State-Based Methods ====================
    
    /**
     * Get StateRanges for UP position (PRIMARY only)
     */
    fun getStateUpRange(): StateRanges {
        return upRange
            ?: throw IllegalStateException("stateUpRange is required for PRIMARY joints: $joint")
    }
    
    /**
     * Get StateRanges for DOWN position (PRIMARY only)
     */
    fun getStateDownRange(): StateRanges {
        return downRange
            ?: throw IllegalStateException("stateDownRange is required for PRIMARY joints: $joint")
    }
    
    /**
     * Get StateRanges for SECONDARY joint (hold range)
     */
    fun getStateHoldRange(): StateRanges {
        return range
            ?: throw IllegalStateException("stateRange is required for SECONDARY joints: $joint")
    }
    
    /**
     * Check if this joint has state-based up/down ranges (PRIMARY)
     */
    fun hasStateUpDownRanges(): Boolean = upRange != null && downRange != null
    
    /**
     * Check if this joint has state-based hold range (SECONDARY)
     */
    fun hasStateHoldRange(): Boolean = range != null
    
    /**
     * Calculate TRANSITION zone boundaries
     * 
     * TRANSITION.min = highest max in downRange (across all states)
     * TRANSITION.max = lowest min in upRange (across all states)
     * 
     * @return Pair of (transitionMin, transitionMax) or null if not applicable
     */
    fun getTransitionZone(): Pair<Double, Double>? {
        if (!hasStateUpDownRanges()) return null
        
        val upRange = getStateUpRange()
        val downRange = getStateDownRange()
        
        // TRANSITION.min = highest max in downRange
        val transitionMin = downRange.effectiveMax
        
        // TRANSITION.max = lowest min in upRange
        val transitionMax = upRange.effectiveMin
        
        return if (transitionMin < transitionMax) {
            Pair(transitionMin, transitionMax)
        } else {
            // No valid transition zone (ranges overlap or touch)
            null
        }
    }
    
    /**
     * Determine the ZoneType for an angle (PRIMARY joints)
     * 
     * NOTE: This is a raw calculation without hysteresis.
     * For production use, prefer FormValidator.getJointStateInfos() which applies
     * hysteresis and danger frame smoothing for stable state transitions.
     * 
     * @param angle Current joint angle
     * @return ZoneType (UP_ZONE, DOWN_ZONE, or TRANSITION)
     */
    fun determineZoneType(angle: Double): ZoneType {
        if (!hasStateUpDownRanges()) {
            // SECONDARY joints are always in their hold zone
            return ZoneType.UP_ZONE // Treat as single zone
        }
        
        val transition = getTransitionZone()
        
        return when {
            transition != null && angle > transition.first && angle < transition.second -> {
                ZoneType.TRANSITION
            }
            angle >= getStateUpRange().effectiveMin -> {
                ZoneType.UP_ZONE
            }
            angle <= getStateDownRange().effectiveMax -> {
                ZoneType.DOWN_ZONE
            }
            else -> {
                // In transition zone (no explicit transition defined)
                ZoneType.TRANSITION
            }
        }
    }
    
    /**
     * Determine JointState for an angle (raw calculation)
     * 
     * NOTE: This is a raw calculation without hysteresis or danger frame smoothing.
     * For production use, prefer FormValidator.getJointStateInfos() which provides
     * stable state transitions with proper hysteresis.
     * 
     * @param angle Current joint angle
     * @return JointState based on which range the angle falls into
     */
    fun determineState(angle: Double): JointState {
        return if (hasStateHoldRange()) {
            // SECONDARY: Check against hold range
            getStateHoldRange().determineState(angle)
        } else if (hasStateUpDownRanges()) {
            // PRIMARY: Check against up/down ranges
            val zoneType = determineZoneType(angle)
            when (zoneType) {
                ZoneType.TRANSITION -> JointState.TRANSITION
                ZoneType.UP_ZONE -> getStateUpRange().determineState(angle)
                ZoneType.DOWN_ZONE -> getStateDownRange().determineState(angle)
            }
        } else {
            // Fallback
            JointState.WARNING
        }
    }
    
    /**
     * Get messages for a specific state and zone
     * 
     * @param state The JointState to get messages for
     * @param zone The current ZoneType (UP_ZONE, DOWN_ZONE, TRANSITION)
     * @return List containing the message (0 or 1 element)
     */
    fun getMessagesForState(state: JointState, zone: ZoneType): List<LocalizedText> {
        return stateMessages?.getMessages(state, zone) ?: emptyList()
    }
    
    /**
     * Legacy: Get messages for a specific state (without zone)
     * Prefers UP zone, falls back to DOWN
     */
    fun getMessagesForState(state: JointState): List<LocalizedText> {
        return stateMessages?.getMessages(state) ?: emptyList()
    }
    
    /**
     * Get message for start pose feedback
     * 
     * Uses stateMessages.warning if available, otherwise returns a generic message.
     * This replaces the old errorMessages.tooLow/tooHigh system.
     * 
     * @param errorType TOO_HIGH or TOO_LOW
     * @return LocalizedText message for the error
     */
    fun getStartPoseMessage(errorType: ErrorType): LocalizedText {
        // Try to get warning message from stateMessages (uses UP zone by default for start pose)
        val warningMessage = stateMessages?.getMessage(JointState.WARNING, ZoneType.UP_ZONE)
            ?: stateMessages?.getMessage(JointState.WARNING)
        if (warningMessage != null) {
            return warningMessage
        }
        
        // Fallback to generic messages
        return when (errorType) {
            ErrorType.TOO_HIGH -> LocalizedText(
                ar = "اخفض أكثر",
                en = "Lower more"
            )
            ErrorType.TOO_LOW -> LocalizedText(
                ar = "ارفع أكثر",
                en = "Raise more"
            )
        }
    }
    
    // ==================== Legacy/Utility Methods ====================
    
    /**
     * Check if angle is in startPose range (pre-training check)
     */
    fun isInStartPose(angle: Double): Boolean {
        return angle >= startPose.min && angle <= startPose.max
    }
    
    /**
     * Check if angle is in any counted state (PERFECT, NORMAL, PAD)
     */
    fun isInCountedState(angle: Double): Boolean {
        return when (val state = determineState(angle)) {
            JointState.PERFECT, JointState.NORMAL, JointState.PAD -> true
            else -> false
        }
    }
    
    /**
     * Check if angle causes rep invalidation (DANGER state)
     */
    fun invalidatesRep(angle: Double): Boolean {
        return determineState(angle) == JointState.DANGER
    }
}

/**
 * Error zone enum
 */
enum class ErrorZone {
    NONE,       // No error
    TOO_HIGH,   // Above upRange.max
    TOO_LOW     // Below downRange.min
}

/**
 * Angle range (min/max)
 */
data class AngleRange(
    val min: Double,
    val max: Double
) {
    /**
     * Check if angle is within this range
     */
    fun contains(angle: Double): Boolean {
        return angle >= min && angle <= max
    }
    
    /**
     * Check if this range overlaps with another range
     * Two ranges overlap if they share any common points
     */
    fun overlaps(other: AngleRange): Boolean {
        // Ranges overlap if one starts before the other ends
        return this.min <= other.max && other.min <= this.max
    }
    
    /**
     * Check if this range fully contains another range
     */
    fun fullyContains(other: AngleRange): Boolean {
        return this.min <= other.min && this.max >= other.max
    }
}

// NOTE: DifficultyRanges has been REMOVED. Use StateRanges instead.
// NOTE: ErrorMessages (tooLow/tooHigh) has been REMOVED. Use StateMessages instead.

/**
 * Feedback messages collection - LOW PRIORITY random messages
 * 
 * These messages are delivered randomly when there's "quiet time" 
 * (no errors/warnings active). They have the lowest priority and
 * are skipped if higher priority messages need to be delivered.
 * 
 * Message Types:
 * - motivational: Positive reinforcement (e.g., "Great job!", "Keep going!")
 * - tips: Improvement suggestions (e.g., "Push through your heels")
 * 
 * NOTE: common_mistake has been REMOVED - covered by positionChecks.errorMessage
 */
data class FeedbackMessages(
    val motivational: List<LocalizedText> = emptyList(),
    @SerializedName("tips", alternate = ["tip"])
    val tips: List<LocalizedText> = emptyList()
) {
    /**
     * Sanitize Gson-null fields.
     */
    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    fun sanitizeGsonDefaults(): FeedbackMessages = copy(
        motivational = motivational ?: emptyList(),
        tips = tips ?: emptyList()
    )
    
    /**
     * Check if there are any messages available
     */
    fun hasMessages(): Boolean = motivational.isNotEmpty() || tips.isNotEmpty()
    
    /**
     * Get a random motivational message
     */
    fun getRandomMotivational(): LocalizedText? = motivational.randomOrNull()
    
    /**
     * Get a random tip
     */
    fun getRandomTip(): LocalizedText? = tips.randomOrNull()
    
    /**
     * Get any random message (motivational or tip)
     */
    fun getRandomMessage(): LocalizedText? {
        val allMessages = motivational + tips
        return allMessages.randomOrNull()
    }
}

/**
 * Message assignment reference (library-based)
 */
data class MessageAssignment(
    val messageId: String,
    val target: String,
    val context: String? = null,
    val jointCode: String? = null,
    val zone: String? = null,
    val checkId: String? = null,
    val sortOrder: Int = 0
)

// NOTE: DifficultyLevel and DifficultyType have been REMOVED.
// Quality is now assessed via JointState (PERFECT/NORMAL/PAD/WARNING/DANGER).
// RepCountingConfig is now at ExerciseConfig level.

/**
 * Rep counting configuration
 * 
 * For UP_DOWN/PUSH_PULL methods:
 * @param reps Target number of repetitions
 * @param minRepIntervalMs Minimum time between reps (prevents double counting)
 * @param maxRepIntervalMs Maximum time for a single rep before timeout
 * 
 * For HOLD method:
 * @param duration Target hold duration in seconds
 * @param gracePeriodMs Grace period in milliseconds when user leaves hold position
 */
data class RepCountingConfig(
    val reps: Int = 12,
    val duration: Int? = null,           // For HOLD method (seconds)
    val gracePeriodMs: Long? = null,     // For HOLD method - grace period before fail
    val minRepIntervalMs: Long? = null,  // null = use global default
    val maxRepIntervalMs: Long? = null   // null = use global default
) {
    /**
     * Calculate minimum phase duration based on min rep interval and number of phases
     * Logic: minPhaseDuration = minRepInterval / numberOfPhases
     * 
     * @param numberOfPhases Number of phases in the exercise (typically 4: start, down, bottom, up)
     * @param defaultMinPhaseDuration Fallback if minRepIntervalMs is not set
     */
    fun calculateMinPhaseDuration(numberOfPhases: Int, defaultMinPhaseDuration: Long): Long {
        val minInterval = minRepIntervalMs ?: return defaultMinPhaseDuration
        if (numberOfPhases <= 0) return defaultMinPhaseDuration
        return minInterval / numberOfPhases
    }
    
    /**
     * Get min rep interval or default
     */
    fun getMinRepInterval(default: Long): Long = minRepIntervalMs ?: default
    
    /**
     * Get max rep interval or default
     */
    fun getMaxRepInterval(default: Long): Long = maxRepIntervalMs ?: default
    
    // ==================== HOLD-specific helpers ====================
    
    /**
     * Get target duration in milliseconds for HOLD exercises
     * @param defaultSeconds Default duration if not specified
     * @return Duration in milliseconds
     */
    fun getDurationMs(defaultSeconds: Int = 30): Long {
        return (duration ?: defaultSeconds) * 1000L
    }
    
    /**
     * Get grace period or default for HOLD exercises
     * @param default Default grace period in milliseconds
     * @return Grace period in milliseconds
     */
    fun getGracePeriod(default: Long): Long = gracePeriodMs ?: default
    
    /**
     * Check if this config is for a hold exercise (has duration set)
     */
    fun isHoldConfig(): Boolean = duration != null
}

// ==================== Position-Based Validation Models ====================

/**
 * @deprecated Facing is now always auto-detected by CameraPositionDetector.
 * Kept for backward compat with old JSON files.
 */
enum class FacingDirection {
    @SerializedName("facing_right")
    FACING_RIGHT,     // Person facing right (left side closer to camera)
    
    @SerializedName("facing_left")
    FACING_LEFT,      // Person facing left (right side closer to camera)
    
    @SerializedName("facing_camera")
    FACING_CAMERA,    // Person facing the camera (front view)
    
    @SerializedName("facing_away")
    FACING_AWAY,      // Person's back to camera (back view)
    
    @SerializedName("auto_detect")
    AUTO_DETECT       // Automatically detect facing direction
}

/**
 * Position-based validation check
 * Works alongside angle-based validation (FormValidator)
 * 
 * @param id Unique identifier for the check (e.g., "left_knee_over_toe")
 * @param type Type of position check
 * @param landmarks Landmarks to compare
 * @param condition Comparison condition with thresholds
 * @param activePhases Phases where this check is active (e.g., ["down", "bottom"])
 * @param errorMessage Error messages for feedback
 * @param severity Error severity (affects scoring)
 * @param cooldownMs Cooldown between repeated errors (prevents spam)
 */
data class PositionCheck(
    val id: String,
    val type: PositionCheckType,
    val landmarks: LandmarkGroup,
    val condition: PositionCondition,
    val activePhases: List<String>,
    val errorMessage: LocalizedText = LocalizedText(),
    val severity: CheckSeverity = CheckSeverity.WARNING,
    val cooldownMs: Long = 2000,
    val minErrorFrames: Int = 3  // Number of consecutive frames to confirm error
) {
    /**
     * Sanitize Gson-null fields.
     */
    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    fun sanitizeGsonDefaults(): PositionCheck {
        val safeMessage = errorMessage ?: LocalizedText()
        val safePhases = activePhases ?: emptyList()
        return if (safeMessage !== errorMessage || safePhases !== activePhases) {
            copy(errorMessage = safeMessage, activePhases = safePhases)
        } else this
    }
}

/**
 * Position check types
 */
enum class PositionCheckType {
    @SerializedName("forward_comparison")
    FORWARD_COMPARISON,       // Compare on forward axis (X in side_view, Z in front_view)
    
    @SerializedName("vertical_comparison")
    VERTICAL_COMPARISON,      // Compare on Y axis (works in all views)
    
    @SerializedName("sideways_comparison")
    SIDEWAYS_COMPARISON,      // Compare on sideways axis (Z in side_view, X in front_view)
    
    @SerializedName("distance_ratio")
    DISTANCE_RATIO,           // Ratio of distances between landmark pairs
    
    @SerializedName("horizontal_alignment")
    HORIZONTAL_ALIGNMENT,     // Check if points are on same horizontal line
    
    @SerializedName("vertical_alignment")
    VERTICAL_ALIGNMENT,       // Check if points are on same vertical line
    
    @SerializedName("depth_alignment")
    DEPTH_ALIGNMENT           // Check if points are at same depth from camera
}

/**
 * Landmark group for comparison
 * Supports 2-4 landmarks depending on check type
 */
data class LandmarkGroup(
    val primary: String,              // Primary landmark (e.g., "left_knee")
    val secondary: String,            // Secondary landmark for comparison (e.g., "left_foot_index")
    val tertiary: String? = null,     // Optional - for alignment checks (3 points)
    val quaternary: String? = null    // Optional - for distance ratio (second pair)
)

/**
 * Position condition with threshold
 */
data class PositionCondition(
    val operator: PositionOperator,
    val threshold: Double  // Single threshold value (no difficulty levels)
)

/**
 * Position comparison operators
 */
enum class PositionOperator {
    @SerializedName("should_not_exceed")
    SHOULD_NOT_EXCEED,        // Primary should not exceed secondary by more than threshold
    
    @SerializedName("should_exceed")
    SHOULD_EXCEED,            // Primary should exceed secondary by at least threshold
    
    @SerializedName("approximately_equal")
    APPROXIMATELY_EQUAL,      // Difference should be less than threshold
    
    @SerializedName("greater_than_ratio")
    GREATER_THAN_RATIO,       // Ratio should be greater than threshold
    
    @SerializedName("less_than_ratio")
    LESS_THAN_RATIO           // Ratio should be less than threshold
}

/**
 * Check severity - affects scoring
 */
enum class CheckSeverity {
    @SerializedName("error")
    ERROR,      // Affects rep correctness (like angle errors)
    
    @SerializedName("warning")
    WARNING,    // Form feedback only - doesn't affect counting
    
    @SerializedName("tip")
    TIP         // Improvement suggestion only
}
