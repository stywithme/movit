package com.trainingvalidator.poc.training.models

import android.graphics.Color

/**
 * JointState - Unified state for joint angle quality assessment
 *
 * Represents the quality level of a joint's current angle position.
 * This is the SINGLE SOURCE OF TRUTH for all training decisions:
 * - Rep counting
 * - Scoring
 * - Visual feedback (colors)
 * - Audio/haptic feedback
 * - Report generation
 *
 * Priority order (from highest to lowest):
 * 1. DANGER - Highest priority (safety first)
 * 2. WARNING - Error state
 * 3. PERFECT - Best form (narrowest valid range)
 * 4. NORMAL - Good form (overlaps with perfect)
 * 5. PAD - Acceptable form (overlaps with normal)
 * 6. TRANSITION - Movement zone (no scoring)
 */
enum class JointState {
    /** Perfect form - the ideal angle range (narrowest) */
    PERFECT,

    /** Good form - acceptable but not perfect (overlaps with perfect) */
    NORMAL,

    /** Acceptable form - on the edge but still counted (overlaps with normal) */
    PAD,

    /** Warning - angle error, does NOT count rep (outer edges) */
    WARNING,

    /** Danger - risk of injury, INVALIDATES rep (extreme outer edges) */
    DANGER,

    /** Transition - movement zone between up/down, no scoring */
    TRANSITION;

    /**
     * Priority value for state comparison (higher = worse quality)
     *
     * Used for:
     * - Determining worst state in a rep
     * - Hysteresis state transitions
     * - Score calculations
     */
    val priority: Int get() = when (this) {
        DANGER -> 5      // Highest priority - injury risk
        WARNING -> 4     // Error state
        PAD -> 3         // Acceptable but borderline
        NORMAL -> 2      // Good form
        PERFECT -> 1     // Best form
        TRANSITION -> 0  // Movement zone - not a quality state
    }

    /**
     * Check if this state is worse (higher priority) than another
     */
    fun isWorseThan(other: JointState): Boolean = this.priority > other.priority

    /**
     * Check if this state is better (lower priority) than another
     */
    fun isBetterThan(other: JointState): Boolean = this.priority < other.priority

    companion object {
        /**
         * Get the worst state from a collection of states
         *
         * @param states Collection of states to compare
         * @return The state with highest priority (worst quality)
         */
        fun getWorst(states: Collection<JointState>): JointState {
            return states.maxByOrNull { it.priority } ?: PERFECT
        }

        /**
         * Get the worst state from vararg states
         */
        fun getWorst(vararg states: JointState): JointState {
            return states.maxByOrNull { it.priority } ?: PERFECT
        }

        /**
         * Get the best state from a collection of states
         */
        fun getBest(states: Collection<JointState>): JointState {
            return states.filter { it != TRANSITION }.minByOrNull { it.priority } ?: PERFECT
        }
    }
}

/**
 * ZoneType - Identifies which range zone the angle is in
 *
 * Used to determine which StateRanges to evaluate against.
 * Separate from JointState which represents quality within a zone.
 */
enum class ZoneType {
    /** Angle is in the up/start range (e.g., arm extended in bicep curl) */
    UP_ZONE,

    /** Angle is in the down/target range (e.g., arm bent in bicep curl) */
    DOWN_ZONE,

    /** Angle is in the transition zone between up and down */
    TRANSITION
}

/**
 * OutwardDirection - Direction away from the body's center of motion
 *
 * When an angle falls outside all configured state ranges, this indicates
 * which direction to extend the outermost defined state:
 * - TOWARDS_HIGH: UP zone outward (towards 180°)
 * - TOWARDS_LOW:  DOWN zone outward (towards 0°)
 */
enum class OutwardDirection {
    TOWARDS_HIGH,
    TOWARDS_LOW
}

/**
 * Severity - Feedback intensity level
 */
enum class Severity {
    NONE,       // No feedback needed
    LOW,        // Subtle feedback
    MEDIUM,     // Noticeable feedback
    HIGH        // Urgent feedback (danger)
}

/**
 * StateConfig - Pre-defined configuration for each JointState
 *
 * Contains all decision parameters for a state:
 * - rate: Score percentage (0-100)
 * - isRepCounted: Whether reps in this state count toward total
 * - invalidatesRep: Whether entering this state invalidates the current rep
 * - color: Visual representation color
 * - severity: Feedback intensity
 */
data class StateConfig(
    val state: JointState,
    val rate: Float,
    val isRepCounted: Boolean,
    val invalidatesRep: Boolean,
    val color: Int,
    val severity: Severity
) {
    companion object {
        // ==================== Color Constants (Cached for Performance) ====================
        // Using @JvmField for direct field access without getter overhead

        @JvmField val COLOR_PERFECT = Color.parseColor("#4CAF50")      // Green
        @JvmField val COLOR_NORMAL = Color.parseColor("#FFEB3B")       // Yellow
        @JvmField val COLOR_PAD = Color.parseColor("#FF9800")          // Orange
        @JvmField val COLOR_WARNING = Color.parseColor("#FF5252")      // Light Red
        @JvmField val COLOR_DANGER = Color.parseColor("#B71C1C")       // Dark Red
        @JvmField val COLOR_TRANSITION = Color.parseColor("#607D8B")   // Blue Gray

        // Legacy color names (for backward compatibility)
        private val COLOR_GREEN = COLOR_PERFECT
        private val COLOR_YELLOW = COLOR_NORMAL
        private val COLOR_ORANGE = COLOR_PAD
        private val COLOR_LIGHT_RED = COLOR_WARNING
        private val COLOR_DARK_RED = COLOR_DANGER
        private val COLOR_BLUE_GRAY = COLOR_TRANSITION

        /**
         * Pre-defined StateConfigs - the SINGLE SOURCE OF TRUTH
         *
         * These values determine ALL training decisions.
         */
        val STATE_CONFIGS: Map<JointState, StateConfig> = mapOf(
            JointState.PERFECT to StateConfig(
                state = JointState.PERFECT,
                rate = 100f,
                isRepCounted = true,
                invalidatesRep = false,
                color = COLOR_GREEN,
                severity = Severity.NONE
            ),
            JointState.NORMAL to StateConfig(
                state = JointState.NORMAL,
                rate = 60f,
                isRepCounted = true,
                invalidatesRep = false,
                color = COLOR_YELLOW,
                severity = Severity.NONE
            ),
            JointState.PAD to StateConfig(
                state = JointState.PAD,
                rate = 20f,
                isRepCounted = true,
                invalidatesRep = false,
                color = COLOR_ORANGE,
                severity = Severity.LOW
            ),
            JointState.WARNING to StateConfig(
                state = JointState.WARNING,
                rate = 0f,
                isRepCounted = false,
                invalidatesRep = false,
                color = COLOR_LIGHT_RED,
                severity = Severity.MEDIUM
            ),
            JointState.DANGER to StateConfig(
                state = JointState.DANGER,
                rate = 0f,
                isRepCounted = false,
                invalidatesRep = true,
                color = COLOR_DARK_RED,
                severity = Severity.HIGH
            ),
            JointState.TRANSITION to StateConfig(
                state = JointState.TRANSITION,
                rate = -1f,  // Not applicable
                isRepCounted = false,
                invalidatesRep = false,
                color = COLOR_YELLOW,  // Same as NORMAL - transition is movement zone
                severity = Severity.NONE
            )
        )

        /**
         * Get StateConfig for a given JointState
         */
        fun getConfig(state: JointState): StateConfig {
            return STATE_CONFIGS[state]
                ?: throw IllegalArgumentException("No config defined for state: $state")
        }

        /**
         * Get color for a given JointState
         *
         * OPTIMIZED: Uses direct lookup instead of map access for hot path
         */
        fun getColor(state: JointState): Int {
            // Direct lookup - avoids map access overhead
            return when (state) {
                JointState.PERFECT -> COLOR_PERFECT
                JointState.NORMAL -> COLOR_NORMAL
                JointState.PAD -> COLOR_PAD
                JointState.WARNING -> COLOR_WARNING
                JointState.DANGER -> COLOR_DANGER
                JointState.TRANSITION -> COLOR_NORMAL  // Same as NORMAL for transition
            }
        }

        /**
         * Check if a state counts toward rep total
         */
        fun isRepCounted(state: JointState): Boolean {
            return getConfig(state).isRepCounted
        }

        /**
         * Check if a state invalidates the current rep
         */
        fun invalidatesRep(state: JointState): Boolean {
            return getConfig(state).invalidatesRep
        }

        /**
         * Get the rate (score percentage) for a state
         */
        fun getRate(state: JointState): Float {
            return getConfig(state).rate
        }
    }
}

/**
 * StateRanges - Defines angle ranges for each state within an upRange or downRange
 *
 * Structure:
 * - perfect: Required. The narrowest, ideal range.
 * - normal: Optional. Overlaps with or extends from perfect.
 * - pad: Optional. Overlaps with or extends from normal.
 * - warning: Optional. On outer edges only.
 * - danger: Optional. On extreme outer edges.
 *
 * Validation rules:
 * 1. Each outer layer must cover the bounds of inner layers
 * 2. warning/danger must NOT overlap with perfect/normal/pad
 * 3. Only perfect is required; others are optional
 */
data class StateRanges(
    val perfect: AngleRange,
    val normal: AngleRange? = null,
    val pad: AngleRange? = null,
    val warning: AngleRange? = null,
    val danger: AngleRange? = null
) {
    // ==================== Cached Properties (Performance Optimization) ====================
    // Using @Transient backing fields to cache computed values
    // These are not serialized by Gson and computed on first access

    @Transient private var _effectiveMin: Double? = null
    @Transient private var _effectiveMax: Double? = null
    @Transient private var _outermostMin: Double? = null
    @Transient private var _outermostMax: Double? = null

    /**
     * Cached effective min - computed once on first access
     * Eliminates listOfNotNull() allocation on every call
     */
    val effectiveMin: Double get() {
        return _effectiveMin ?: run {
            var min = perfect.min
            normal?.min?.let { if (it < min) min = it }
            pad?.min?.let { if (it < min) min = it }
            _effectiveMin = min
            min
        }
    }

    /**
     * Cached effective max - computed once on first access
     * Eliminates listOfNotNull() allocation on every call
     */
    val effectiveMax: Double get() {
        return _effectiveMax ?: run {
            var max = perfect.max
            normal?.max?.let { if (it > max) max = it }
            pad?.max?.let { if (it > max) max = it }
            _effectiveMax = max
            max
        }
    }

    /**
     * Cached outermost min - computed once on first access
     */
    val outermostMin: Double get() {
        return _outermostMin ?: run {
            var min = perfect.min
            normal?.min?.let { if (it < min) min = it }
            pad?.min?.let { if (it < min) min = it }
            warning?.min?.let { if (it < min) min = it }
            danger?.min?.let { if (it < min) min = it }
            _outermostMin = min
            min
        }
    }

    /**
     * Cached outermost max - computed once on first access
     */
    val outermostMax: Double get() {
        return _outermostMax ?: run {
            var max = perfect.max
            normal?.max?.let { if (it > max) max = it }
            pad?.max?.let { if (it > max) max = it }
            warning?.max?.let { if (it > max) max = it }
            danger?.max?.let { if (it > max) max = it }
            _outermostMax = max
            max
        }
    }

    /**
     * Determine the JointState for a given angle within this range
     *
     * Priority order (BEST state wins in case of overlap):
     * 1. PERFECT (highest priority - best form)
     * 2. NORMAL (good form)
     * 3. PAD (acceptable form)
     * 4. DANGER (injury risk)
     * 5. WARNING (error state)
     * 6. Outward fallback → extend outermost defined state
     * 7. WARNING as final fallback
     *
     * @param angle The current joint angle
     * @param outwardDirection When set, angles beyond all defined ranges
     *        are classified as the outermost state in that direction
     *        instead of defaulting to WARNING.
     * @return The determined JointState
     */
    fun determineState(angle: Double, outwardDirection: OutwardDirection? = null): JointState {
        if (perfect.contains(angle)) return JointState.PERFECT
        if (normal != null && normal.contains(angle)) return JointState.NORMAL
        if (pad != null && pad.contains(angle)) return JointState.PAD
        if (danger != null && danger.contains(angle)) return JointState.DANGER
        if (warning != null && warning.contains(angle)) return JointState.WARNING

        if (outwardDirection != null) {
            return getOutermostState(outwardDirection)
        }

        return JointState.WARNING
    }

    /**
     * Find the state whose defined range extends furthest in the given direction.
     * Used to fill undefined outer regions with the nearest configured state.
     */
    private fun getOutermostState(direction: OutwardDirection): JointState {
        return when (direction) {
            OutwardDirection.TOWARDS_HIGH -> {
                var best = JointState.PERFECT; var bestVal = perfect.max
                normal?.let  { if (it.max > bestVal) { bestVal = it.max; best = JointState.NORMAL  } }
                pad?.let     { if (it.max > bestVal) { bestVal = it.max; best = JointState.PAD     } }
                warning?.let { if (it.max > bestVal) { bestVal = it.max; best = JointState.WARNING } }
                danger?.let  { if (it.max > bestVal) { bestVal = it.max; best = JointState.DANGER  } }
                best
            }
            OutwardDirection.TOWARDS_LOW -> {
                var best = JointState.PERFECT; var bestVal = perfect.min
                normal?.let  { if (it.min < bestVal) { bestVal = it.min; best = JointState.NORMAL  } }
                pad?.let     { if (it.min < bestVal) { bestVal = it.min; best = JointState.PAD     } }
                warning?.let { if (it.min < bestVal) { bestVal = it.min; best = JointState.WARNING } }
                danger?.let  { if (it.min < bestVal) { bestVal = it.min; best = JointState.DANGER  } }
                best
            }
        }
    }

    /**
     * Check if an angle is within any counted state (PERFECT, NORMAL, PAD)
     */
    fun isInCountedState(angle: Double): Boolean {
        return perfect.contains(angle) ||
               (normal != null && normal.contains(angle)) ||
               (pad != null && pad.contains(angle))
    }

    // ==================== Validation ====================

    /**
     * Validation result for StateRanges configuration
     */
    data class ValidationResult(
        val isValid: Boolean,
        val warnings: List<String>,
        val errors: List<String>
    )

    /**
     * Validate the StateRanges configuration
     *
     * Checks for:
     * 1. Overlaps between WARNING/DANGER and PERFECT/NORMAL/PAD (warning)
     * 2. Layer coverage rules (warning if outer layer doesn't cover inner)
     *
     * @param jointCode Joint identifier for error messages
     * @param rangeName Range name (upRange/downRange) for error messages
     * @return ValidationResult with warnings and errors
     */
    fun validate(jointCode: String, rangeName: String): ValidationResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val prefix = "[$jointCode.$rangeName]"

        // Check WARNING/DANGER overlap with counted states
        val countedStates = listOfNotNull(perfect, normal, pad)

        warning?.let { warningRange ->
            for (counted in countedStates) {
                if (warningRange.overlaps(counted)) {
                    warnings.add("$prefix WARNING range (${warningRange.min}-${warningRange.max}) overlaps with counted state (${counted.min}-${counted.max}). PERFECT/NORMAL/PAD will take priority.")
                }
            }
        }

        danger?.let { dangerRange ->
            for (counted in countedStates) {
                if (dangerRange.overlaps(counted)) {
                    warnings.add("$prefix DANGER range (${dangerRange.min}-${dangerRange.max}) overlaps with counted state (${counted.min}-${counted.max}). PERFECT/NORMAL/PAD will take priority.")
                }
            }

            // Check DANGER overlaps with WARNING
            warning?.let { warningRange ->
                if (dangerRange.overlaps(warningRange)) {
                    warnings.add("$prefix DANGER range overlaps with WARNING range. DANGER will take priority.")
                }
            }
        }

        // Check layer coverage (outer should cover inner's bounds)
        normal?.let { normalRange ->
            // Normal should extend beyond or equal to perfect's bounds
            if (normalRange.min > perfect.min || normalRange.max < perfect.max) {
                warnings.add("$prefix NORMAL (${normalRange.min}-${normalRange.max}) doesn't fully cover PERFECT (${perfect.min}-${perfect.max}). This is unusual but allowed.")
            }
        }

        pad?.let { padRange ->
            val innerRange = normal ?: perfect
            if (padRange.min > innerRange.min || padRange.max < innerRange.max) {
                warnings.add("$prefix PAD (${padRange.min}-${padRange.max}) doesn't fully cover inner range (${innerRange.min}-${innerRange.max}). This is unusual but allowed.")
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            warnings = warnings,
            errors = errors
        )
    }
}

/**
 * JointStateInfo - Complete state information for a joint
 *
 * This replaces JointArrowInfo and provides all information needed
 * for rendering, feedback, and scoring decisions.
 */
data class JointStateInfo(
    /** Joint identifier code (e.g., "left_elbow") */
    val jointCode: String,

    /** Current quality state */
    val state: JointState,

    /** Pre-resolved state configuration */
    val stateConfig: StateConfig,

    /** Whether this is a primary joint (affects rep counting) */
    val isPrimary: Boolean,

    /** Current angle value in degrees */
    val currentAngle: Double,

    /** Which zone the angle is in (UP, DOWN, or TRANSITION) */
    val currentZone: ZoneType,

    /** State ranges for the current zone (for rendering) */
    val stateRanges: StateRanges?,

    /** State ranges for UP zone (for track rendering) */
    val upStateRanges: StateRanges? = null,

    /** State ranges for DOWN zone (for track rendering) */
    val downStateRanges: StateRanges? = null,

    /** Pre-resolved rate from stateConfig */
    val rate: Float,

    /** Pre-resolved isRepCounted from stateConfig */
    val isRepCounted: Boolean,

    /** Pre-resolved invalidatesRep from stateConfig */
    val invalidatesRep: Boolean,

    /** Pre-resolved color from stateConfig */
    val color: Int,

    /** Feedback messages for this state (from config) */
    val messages: List<LocalizedText> = emptyList(),

    /**
     * Invert visual indicator direction
     * When true: upRange displays on LOWER limb, downRange on UPPER limb
     * Used for exercises where lower limb moves up (bicep curl: forearm → shoulder)
     * Default false: Upper limb moves down (squat: thigh moves down)
     */
    val invertIndicator: Boolean = false
) {
    companion object {
        /**
         * Create JointStateInfo from components
         * Automatically resolves StateConfig values
         */
        fun create(
            jointCode: String,
            state: JointState,
            isPrimary: Boolean,
            currentAngle: Double,
            currentZone: ZoneType,
            stateRanges: StateRanges?,
            upStateRanges: StateRanges? = null,
            downStateRanges: StateRanges? = null,
            messages: List<LocalizedText> = emptyList(),
            invertIndicator: Boolean = false
        ): JointStateInfo {
            val config = StateConfig.getConfig(state)
            return JointStateInfo(
                jointCode = jointCode,
                state = state,
                stateConfig = config,
                isPrimary = isPrimary,
                currentAngle = currentAngle,
                currentZone = currentZone,
                stateRanges = stateRanges,
                upStateRanges = upStateRanges,
                downStateRanges = downStateRanges,
                rate = config.rate,
                isRepCounted = config.isRepCounted,
                invalidatesRep = config.invalidatesRep,
                color = config.color,
                messages = messages,
                invertIndicator = invertIndicator
            )
        }
    }
}

/**
 * AngleColorResolver - SINGLE SOURCE OF TRUTH for angle colors
 *
 * This object is the ONLY place that determines what color an angle should be.
 * All visual components (LineRangeIndicator, ArcRangeIndicator, SkeletonOverlay)
 * MUST use this resolver instead of calculating colors independently.
 *
 * Color determination logic:
 * 1. Check if angle is in UP zone (>= upRange.min)
 * 2. Check if angle is in DOWN zone (<= downRange.max)
 * 3. If in between, it's TRANSITION zone
 * 4. Within each zone, use StateRanges.determineState() to get JointState
 * 5. Get color from StateConfig.getColor(state)
 */
object AngleColorResolver {

    /**
     * Result of color resolution - includes state and color
     */
    data class ColorResult(
        val state: JointState,
        val color: Int,
        val zone: ZoneType
    )

    /**
     * Get the state and color for any angle
     *
     * This is the SINGLE SOURCE OF TRUTH for all color decisions.
     *
     * @param angle The current angle in degrees
     * @param upRanges StateRanges for UP zone (null if not defined)
     * @param downRanges StateRanges for DOWN zone (null if not defined)
     * @return ColorResult with state, color, and zone
     */
    fun resolve(
        angle: Double,
        upRanges: StateRanges?,
        downRanges: StateRanges?
    ): ColorResult {
        // Calculate TRANSITION boundaries
        val transitionMin = downRanges?.effectiveMax ?: 0.0
        val transitionMax = upRanges?.effectiveMin ?: 180.0

        // Determine zone
        val zone = when {
            angle >= transitionMax && upRanges != null -> ZoneType.UP_ZONE
            angle <= transitionMin && downRanges != null -> ZoneType.DOWN_ZONE
            else -> ZoneType.TRANSITION
        }

        // Determine state within zone (with outward fallback for undefined outer regions)
        val state = when (zone) {
            ZoneType.UP_ZONE -> upRanges?.determineState(angle, OutwardDirection.TOWARDS_HIGH) ?: JointState.TRANSITION
            ZoneType.DOWN_ZONE -> downRanges?.determineState(angle, OutwardDirection.TOWARDS_LOW) ?: JointState.TRANSITION
            ZoneType.TRANSITION -> JointState.TRANSITION
        }

        // Get color from StateConfig (SINGLE SOURCE)
        val color = StateConfig.getColor(state)

        return ColorResult(state, color, zone)
    }

    /**
     * Get just the color for an angle (convenience method)
     */
    fun getColor(
        angle: Double,
        upRanges: StateRanges?,
        downRanges: StateRanges?
    ): Int {
        return resolve(angle, upRanges, downRanges).color
    }

    /**
     * Get just the state for an angle (convenience method)
     */
    fun getState(
        angle: Double,
        upRanges: StateRanges?,
        downRanges: StateRanges?
    ): JointState {
        return resolve(angle, upRanges, downRanges).state
    }

    /**
     * Get color for an angle within a SPECIFIC zone's ranges
     *
     * Used by indicators that draw a single track (up OR down)
     *
     * IMPORTANT: Handles TRANSITION zone correctly:
     * - For UP zone: angles < effectiveMin are TRANSITION
     * - For DOWN zone: angles > effectiveMax are TRANSITION
     *
     * @param angle The angle to check
     * @param ranges The StateRanges for this specific zone
     * @param isUpZone Whether this is the UP zone (affects fallback behavior)
     */
    fun getColorForZone(
        angle: Double,
        ranges: StateRanges?,
        isUpZone: Boolean
    ): Int {
        if (ranges == null) {
            return StateConfig.getColor(JointState.TRANSITION)
        }

        // Check if angle is in TRANSITION zone (outside defined ranges)
        val effectiveMin = ranges.effectiveMin
        val effectiveMax = ranges.effectiveMax

        val isInTransition = if (isUpZone) {
            // For UP zone: angles below effectiveMin are TRANSITION
            angle < effectiveMin
        } else {
            // For DOWN zone: angles above effectiveMax are TRANSITION
            angle > effectiveMax
        }

        if (isInTransition) {
            return StateConfig.getColor(JointState.TRANSITION)
        }

        val outward = if (isUpZone) OutwardDirection.TOWARDS_HIGH else OutwardDirection.TOWARDS_LOW
        val state = ranges.determineState(angle, outward)
        return StateConfig.getColor(state)
    }

    /**
     * Get color for a position on a gradient track
     *
     * This is for drawing static tracks that show the full range.
     * The position (0.0 to 1.0) represents where on the track we are.
     *
     * IMPORTANT: This handles TRANSITION zone correctly:
     * - For UPPER limb: angles < ranges.getEffectiveMin() are TRANSITION
     * - For LOWER limb: angles > ranges.getEffectiveMax() are TRANSITION
     *
     * @param position Position on track (0.0 = center/90°, 1.0 = endpoint)
     * @param ranges StateRanges for this track's zone
     * @param isUpperLimb True if this is the upper limb (90° → 180°), false for lower (90° → 0°)
     * @param invertAngles If true, angle mapping is inverted (for invertIndicator)
     */
    fun getColorForTrackPosition(
        position: Float,
        ranges: StateRanges?,
        isUpperLimb: Boolean,
        invertAngles: Boolean = false
    ): Int {
        if (ranges == null) {
            return StateConfig.getColor(JointState.TRANSITION)
        }

        // Convert position (0-1) to angle
        // Position 0.0 = center (90°)
        // Position 1.0 = endpoint (180° for upper, 0° for lower)
        val visualAngle = if (isUpperLimb) {
            90.0 + (position * 90.0)  // 90° → 180°
        } else {
            90.0 - (position * 90.0)  // 90° → 0°
        }

        // When invertAngles, we need to check the ORIGINAL angle that this visual position represents
        // The ranges are defined in original angle space
        val angleForRangeCheck = if (invertAngles) {
            180.0 - visualAngle
        } else {
            visualAngle
        }

        // Check if this angle is in TRANSITION zone (outside the defined ranges)
        // For UPPER limb (moving up from 90°): TRANSITION if angle < effectiveMin
        // For LOWER limb (moving down from 90°): TRANSITION if angle > effectiveMax
        val effectiveMin = ranges.effectiveMin
        val effectiveMax = ranges.effectiveMax

        val isInTransition = if (isUpperLimb xor invertAngles) {
            // Upper limb (or inverted lower): angles below effectiveMin are TRANSITION
            angleForRangeCheck < effectiveMin
        } else {
            // Lower limb (or inverted upper): angles above effectiveMax are TRANSITION
            angleForRangeCheck > effectiveMax
        }

        if (isInTransition) {
            return StateConfig.getColor(JointState.TRANSITION)
        }

        val outward = if (isUpperLimb xor invertAngles) OutwardDirection.TOWARDS_HIGH else OutwardDirection.TOWARDS_LOW
        val state = ranges.determineState(angleForRangeCheck, outward)
        return StateConfig.getColor(state)
    }

    /**
     * Build gradient colors and positions for a track
     *
     * Returns colors and positions that can be used directly in LinearGradient or arc drawing.
     * This is the SINGLE SOURCE for gradient calculation.
     *
     * @param ranges StateRanges for this track
     * @param isUpperLimb True for upper limb track (90° → 180°)
     * @param invertAngles If true, angle mapping is inverted
     * @param steps Number of gradient steps (higher = smoother, default 10)
     * @return Pair of IntArray (colors) and FloatArray (positions 0.0 to 1.0)
     */
    fun buildGradientForTrack(
        ranges: StateRanges?,
        isUpperLimb: Boolean,
        invertAngles: Boolean = false,
        steps: Int = 20
    ): Pair<IntArray, FloatArray> {
        if (ranges == null) {
            val transitionColor = StateConfig.getColor(JointState.TRANSITION)
            return intArrayOf(transitionColor, transitionColor) to floatArrayOf(0f, 1f)
        }

        val colors = mutableListOf<Int>()
        val positions = mutableListOf<Float>()

        var lastColor: Int? = null

        for (i in 0..steps) {
            val position = i.toFloat() / steps
            val color = getColorForTrackPosition(position, ranges, isUpperLimb, invertAngles)

            // Only add if color changed (for efficiency)
            if (color != lastColor) {
                // Add transition point
                if (lastColor != null && positions.isNotEmpty()) {
                    // Add the old color at this position (end of previous segment)
                    colors.add(lastColor)
                    positions.add(position)
                }
                // Add the new color at this position (start of new segment)
                colors.add(color)
                positions.add(position)
                lastColor = color
            }
        }

        // Ensure we have at least start and end
        if (colors.isEmpty()) {
            val defaultColor = StateConfig.getColor(JointState.NORMAL)
            return intArrayOf(defaultColor, defaultColor) to floatArrayOf(0f, 1f)
        }

        // Ensure we end at 1.0
        if (positions.last() < 1f) {
            colors.add(colors.last())
            positions.add(1f)
        }

        // Fix: ensure first position is 0
        if (positions.first() > 0f) {
            colors.add(0, colors.first())
            positions.add(0, 0f)
        }

        return colors.toIntArray() to positions.toFloatArray()
    }
}

/**
 * StateMessages - Configurable messages for each JointState
 *
 * MEDIUM PRIORITY messages delivered when joint enters a specific state.
 * Supports two formats:
 *
 * 1. SINGLE MESSAGE (for Hold exercises):
 * ```json
 * "stateMessages": {
 *   "perfect": { "ar": "ممتاز!", "en": "Perfect!" },
 *   "warning": { "ar": "ابق ثابتاً", "en": "Stay steady" }
 * }
 * ```
 *
 * 2. ZONE-SPECIFIC MESSAGES (for Up&Down/Push&Pull exercises):
 * ```json
 * "stateMessages": {
 *   "perfect": {
 *     "up": { "ar": "ممتاز! ذراعك مفرودة", "en": "Perfect! Arm extended" },
 *     "down": { "ar": "ممتاز! ثني مثالي", "en": "Perfect! Great curl" }
 *   },
 *   "warning": {
 *     "up": { "ar": "افرد ذراعك أكثر", "en": "Extend your arm more" },
 *     "down": { "ar": "اثنِ المرفق أكثر", "en": "Bend your elbow more" }
 *   }
 * }
 * ```
 *
 * Message Delivery:
 * - DANGER: CRITICAL priority - always delivered, strong haptic
 * - WARNING: WARNING priority - first = audio+visual, repeat = visual only
 * - PAD: TIP priority - visual only
 * - NORMAL: INFO priority - one-time, visual only
 * - PERFECT: MOTIVATION priority - encouragement when reaching perfect form
 *
 * All messages are OPTIONAL - can have up only, down only, both, or none.
 */
data class StateMessages(
    val perfect: StateMessageValue? = null,
    val normal: StateMessageValue? = null,
    val pad: StateMessageValue? = null,
    val warning: StateMessageValue? = null,
    val danger: StateMessageValue? = null
) {
    /**
     * Get message for a specific state and zone
     *
     * @param state The JointState to get message for
     * @param zone The current ZoneType (UP_ZONE, DOWN_ZONE, or TRANSITION)
     * @return LocalizedText or null if not defined
     */
    fun getMessage(state: JointState, zone: ZoneType): LocalizedText? {
        val value = when (state) {
            JointState.PERFECT -> perfect
            JointState.NORMAL -> normal
            JointState.PAD -> pad
            JointState.WARNING -> warning
            JointState.DANGER -> danger
            JointState.TRANSITION -> null
        }
        return value?.getMessage(zone)
    }

    /**
     * Get message for a specific state (legacy - uses any available message)
     * Prefers UP zone if zone-specific, falls back to DOWN
     */
    fun getMessage(state: JointState): LocalizedText? {
        return getMessage(state, ZoneType.UP_ZONE)
            ?: getMessage(state, ZoneType.DOWN_ZONE)
    }

    /**
     * Get messages for a specific state and zone
     * Returns a list for API compatibility (may contain 0 or 1 message)
     */
    fun getMessages(state: JointState, zone: ZoneType): List<LocalizedText> {
        val message = getMessage(state, zone) ?: return emptyList()
        return listOf(message)
    }

    /**
     * Legacy: Get messages for a state (without zone)
     */
    fun getMessages(state: JointState): List<LocalizedText> {
        val message = getMessage(state) ?: return emptyList()
        return listOf(message)
    }

    /**
     * Check if message is defined for a state in any zone
     */
    fun hasMessage(state: JointState): Boolean = getMessage(state) != null

    /**
     * Check if message is defined for a state in a specific zone
     */
    fun hasMessage(state: JointState, zone: ZoneType): Boolean = getMessage(state, zone) != null
}

/**
 * StateMessageValue - Either a single message or zone-specific messages
 *
 * Supports JSON formats:
 * 1. Single: { "ar": "...", "en": "..." }
 * 2. Zone-specific: { "up": {"ar": "...", "en": "..."}, "down": {"ar": "...", "en": "..."} }
 *
 * All fields are optional - can have up only, down only, both, or neither.
 */
sealed class StateMessageValue {
    /**
     * Get message for a specific zone
     * @param zone The zone to get message for
     * @return LocalizedText or null if not defined for this zone
     */
    abstract fun getMessage(zone: ZoneType): LocalizedText?

    /**
     * Single message for all zones (Hold exercises)
     */
    data class Single(val message: LocalizedText) : StateMessageValue() {
        override fun getMessage(zone: ZoneType): LocalizedText = message
    }

    /**
     * Zone-specific messages (Up&Down/Push&Pull exercises)
     * Both up and down are optional
     */
    data class ZoneSpecific(
        val up: LocalizedText? = null,
        val down: LocalizedText? = null
    ) : StateMessageValue() {
        override fun getMessage(zone: ZoneType): LocalizedText? {
            return when (zone) {
                ZoneType.UP_ZONE -> up
                ZoneType.DOWN_ZONE -> down
                ZoneType.TRANSITION -> null // No message during transition
            }
        }
    }
}

/**
 * Gson TypeAdapter for StateMessageValue
 *
 * Handles both JSON formats:
 * 1. Single: { "ar": "...", "en": "..." }
 * 2. Zone-specific: { "up": {...}, "down": {...} }
 *
 * Detection logic:
 * - If object has "up" or "down" keys → ZoneSpecific
 * - Otherwise → Single (treat as LocalizedText)
 */
class StateMessageValueTypeAdapter : com.google.gson.TypeAdapter<StateMessageValue>() {

    private val gson = com.google.gson.Gson()

    override fun write(out: com.google.gson.stream.JsonWriter, value: StateMessageValue?) {
        if (value == null) {
            out.nullValue()
            return
        }

        when (value) {
            is StateMessageValue.Single -> {
                // Write as LocalizedText with audio URLs
                out.beginObject()
                out.name("ar").value(value.message.ar)
                out.name("en").value(value.message.en)
                value.message.audioAr?.let { out.name("audioAr").value(it) }
                value.message.audioEn?.let { out.name("audioEn").value(it) }
                out.endObject()
            }
            is StateMessageValue.ZoneSpecific -> {
                // Write as zone-specific with audio URLs
                out.beginObject()
                value.up?.let { up ->
                    out.name("up")
                    out.beginObject()
                    out.name("ar").value(up.ar)
                    out.name("en").value(up.en)
                    up.audioAr?.let { out.name("audioAr").value(it) }
                    up.audioEn?.let { out.name("audioEn").value(it) }
                    out.endObject()
                }
                value.down?.let { down ->
                    out.name("down")
                    out.beginObject()
                    out.name("ar").value(down.ar)
                    out.name("en").value(down.en)
                    down.audioAr?.let { out.name("audioAr").value(it) }
                    down.audioEn?.let { out.name("audioEn").value(it) }
                    out.endObject()
                }
                out.endObject()
            }
        }
    }

    override fun read(reader: com.google.gson.stream.JsonReader): StateMessageValue? {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        // Parse as JsonObject first to inspect keys
        val jsonElement = com.google.gson.JsonParser.parseReader(reader)
        if (!jsonElement.isJsonObject) {
            return null
        }

        val jsonObject = jsonElement.asJsonObject

        // Check if it's zone-specific (has "up" or "down" keys)
        return if (jsonObject.has("up") || jsonObject.has("down")) {
            // Zone-specific format
            val up = jsonObject.get("up")?.let { upElement ->
                if (upElement.isJsonObject) {
                    gson.fromJson(upElement, LocalizedText::class.java)
                } else null
            }
            val down = jsonObject.get("down")?.let { downElement ->
                if (downElement.isJsonObject) {
                    gson.fromJson(downElement, LocalizedText::class.java)
                } else null
            }
            StateMessageValue.ZoneSpecific(up = up, down = down)
        } else {
            // Single message format (LocalizedText)
            val message = gson.fromJson(jsonObject, LocalizedText::class.java)
            StateMessageValue.Single(message)
        }
    }
}
