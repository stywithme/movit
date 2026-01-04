package com.trainingvalidator.poc.training.models

import com.google.gson.annotations.SerializedName

/**
 * ExerciseConfig - Complete exercise configuration from JSON
 * 
 * This represents the full exercise data structure as defined by the admin dashboard.
 * Supports multiple pose variants, tracked joints, and difficulty levels.
 */
data class ExerciseConfig(
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val instructions: LocalizedText? = null,
    val category: CategoryInfo,
    val countingMethod: CountingMethod,
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val poseVariants: List<PoseVariant> = emptyList(),
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
     * Get difficulty level config for a pose variant
     */
    fun getDifficultyLevel(variantIndex: Int, level: DifficultyType): DifficultyLevel? {
        return poseVariants.getOrNull(variantIndex)
            ?.difficultyLevels
            ?.find { it.level == level }
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
     * Get all tracked joints for a pose variant
     */
    fun getTrackedJoints(variantIndex: Int = 0): List<TrackedJoint> {
        return poseVariants.getOrNull(variantIndex)?.trackedJoints ?: emptyList()
    }
}

/**
 * Localized text (Arabic + English)
 */
data class LocalizedText(
    val ar: String = "",
    val en: String = ""
) {
    fun get(language: String = "en"): String {
        return if (language == "ar") ar else en
    }
}

/**
 * Category information
 */
data class CategoryInfo(
    val code: String,
    val name: LocalizedText
)

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
 * Pose variant - represents one camera angle/view
 */
data class PoseVariant(
    val name: LocalizedText,
    val cameraPosition: String,
    val trackedJoints: List<TrackedJoint> = emptyList(),
    val feedbackMessages: FeedbackMessages = FeedbackMessages(),
    val difficultyLevels: List<DifficultyLevel> = emptyList()
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
 * Tracked joint configuration - NEW STRUCTURE with upRange/downRange
 * 
 * The exercise movement is divided into clear zones:
 * - upRange: Valid range for UP/START position (standing, extended)
 * - downRange: Valid range for DOWN/BOTTOM position (bent, lowered)
 * - Transition Zone: Between upRange.min and downRange.max (movement allowed)
 * - Error Zones: Above upRange.max or below downRange.min
 * 
 * Example for Bicep Curl:
 *   upRange:   { min: 150, max: 180 }  → arm extended
 *   downRange: { min: 30,  max: 70  }  → arm bent
 *   Transition: 70° - 150° → moving (no validation)
 */
data class TrackedJoint(
    val joint: String,                      // Joint code (e.g., "left_elbow")
    val role: JointRole,                    // primary or secondary
    val startPose: AngleRange,              // For pre-training position check (independent)
    val upRange: DifficultyRanges,          // Range for UP/START position
    val downRange: DifficultyRanges,        // Range for DOWN/BOTTOM position
    val movingSegment: MovingSegment? = null, // The segment that moves (for arrow display)
    val errorMessages: ErrorMessages,       // Error messages for feedback
    val pairedWith: String? = null          // Paired joint code (for symmetry)
) {
    /**
     * Get UP range for a specific difficulty level
     */
    fun getUpRange(level: DifficultyType): AngleRange {
        return when (level) {
            DifficultyType.BEGINNER -> upRange.beginner
            DifficultyType.NORMAL -> upRange.normal
            DifficultyType.ADVANCED -> upRange.advanced
        }
    }
    
    /**
     * Get DOWN range for a specific difficulty level
     */
    fun getDownRange(level: DifficultyType): AngleRange {
        return when (level) {
            DifficultyType.BEGINNER -> downRange.beginner
            DifficultyType.NORMAL -> downRange.normal
            DifficultyType.ADVANCED -> downRange.advanced
        }
    }
    
    /**
     * Check if angle is in startPose range (pre-training check)
     */
    fun isInStartPose(angle: Double): Boolean {
        return angle >= startPose.min && angle <= startPose.max
    }
    
    /**
     * Check if angle is in UP range (start position)
     */
    fun isInUpRange(angle: Double, level: DifficultyType): Boolean {
        val range = getUpRange(level)
        return angle >= range.min && angle <= range.max
    }
    
    /**
     * Check if angle is in DOWN range (target position)
     */
    fun isInDownRange(angle: Double, level: DifficultyType): Boolean {
        val range = getDownRange(level)
        return angle >= range.min && angle <= range.max
    }
    
    /**
     * Check if angle is in transition zone (between UP and DOWN)
     */
    fun isInTransition(angle: Double, level: DifficultyType): Boolean {
        val up = getUpRange(level)
        val down = getDownRange(level)
        return angle > down.max && angle < up.min
    }
    
    /**
     * Check if angle is in error zone (too high or too low)
     */
    fun isInErrorZone(angle: Double, level: DifficultyType): ErrorZone {
        val up = getUpRange(level)
        val down = getDownRange(level)
        
        return when {
            angle > up.max -> ErrorZone.TOO_HIGH
            angle < down.min -> ErrorZone.TOO_LOW
            else -> ErrorZone.NONE
        }
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
 * Moving segment - defines which body segment moves during the exercise
 * Used to draw direction arrows on the correct body part
 * 
 * Example for Bicep Curl:
 *   from: "right_elbow"  (fixed point)
 *   to: "right_wrist"    (moving point)
 *   The arrow will be drawn along this segment showing movement direction
 */
data class MovingSegment(
    val from: String,   // Starting joint code (the pivot/fixed point)
    val to: String      // Ending joint code (the moving point)
)

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
}

/**
 * Ranges per difficulty level
 */
data class DifficultyRanges(
    val beginner: AngleRange = AngleRange(0.0, 180.0),
    val normal: AngleRange = AngleRange(0.0, 180.0),
    val advanced: AngleRange = AngleRange(0.0, 180.0)
)

/**
 * Error messages for joint feedback
 */
data class ErrorMessages(
    val tooLow: LocalizedText = LocalizedText(),
    val tooHigh: LocalizedText = LocalizedText()
)

/**
 * Feedback messages collection
 */
data class FeedbackMessages(
    val motivational: List<LocalizedText> = emptyList(),
    @SerializedName("common_mistake")
    val commonMistake: List<LocalizedText> = emptyList(),
    val tip: List<LocalizedText> = emptyList()
)

/**
 * Difficulty level configuration
 */
data class DifficultyLevel(
    val level: DifficultyType,
    val repCountingConfig: RepCountingConfig,
    val phases: List<String> = emptyList()
)

/**
 * Difficulty type enum
 */
enum class DifficultyType {
    @SerializedName("beginner")
    BEGINNER,
    
    @SerializedName("normal")
    NORMAL,
    
    @SerializedName("advanced")
    ADVANCED
}

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
