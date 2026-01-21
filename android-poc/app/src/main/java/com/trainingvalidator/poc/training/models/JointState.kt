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
        // Color constants
        private val COLOR_GREEN = Color.parseColor("#4CAF50")      // Perfect
        private val COLOR_YELLOW = Color.parseColor("#FFEB3B")     // Normal
        private val COLOR_ORANGE = Color.parseColor("#FF9800")     // Pad
        private val COLOR_LIGHT_RED = Color.parseColor("#FF5252")  // Warning
        private val COLOR_DARK_RED = Color.parseColor("#B71C1C")   // Danger
        private val COLOR_BLUE_GRAY = Color.parseColor("#607D8B")  // Transition
        
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
         */
        fun getColor(state: JointState): Int {
            return getConfig(state).color
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
    /**
     * Get the effective min angle for this range (lowest min across all counted states)
     * Used for TRANSITION calculation
     */
    fun getEffectiveMin(): Double {
        val candidates = listOfNotNull(
            perfect.min,
            normal?.min,
            pad?.min
        )
        return candidates.minOrNull() ?: perfect.min
    }
    
    /**
     * Get the effective max angle for this range (highest max across all counted states)
     * Used for TRANSITION calculation
     */
    fun getEffectiveMax(): Double {
        val candidates = listOfNotNull(
            perfect.max,
            normal?.max,
            pad?.max
        )
        return candidates.maxOrNull() ?: perfect.max
    }
    
    /**
     * Get the outermost min (including warning/danger)
     */
    fun getOutermostMin(): Double {
        val candidates = listOfNotNull(
            warning?.min,
            danger?.min,
            pad?.min,
            normal?.min,
            perfect.min
        )
        return candidates.minOrNull() ?: perfect.min
    }
    
    /**
     * Get the outermost max (including warning/danger)
     */
    fun getOutermostMax(): Double {
        val candidates = listOfNotNull(
            danger?.max,
            warning?.max,
            pad?.max,
            normal?.max,
            perfect.max
        )
        return candidates.maxOrNull() ?: perfect.max
    }
    
    /**
     * Determine the JointState for a given angle within this range
     * 
     * Priority order (BEST state wins in case of overlap):
     * 1. PERFECT (highest priority - best form)
     * 2. NORMAL (good form)
     * 3. PAD (acceptable form)
     * 4. WARNING (error state)
     * 5. DANGER (injury risk)
     * 6. WARNING as fallback for undefined zones
     * 
     * This means if config has overlapping ranges (configuration error),
     * the BETTER state takes priority. This prevents false negatives
     * where a good angle is marked as warning/danger due to config mistakes.
     * 
     * @param angle The current joint angle
     * @return The determined JointState
     */
    fun determineState(angle: Double): JointState {
        // Check PERFECT first (best state, highest priority)
        if (perfect.contains(angle)) {
            return JointState.PERFECT
        }
        
        // Check NORMAL (good state)
        if (normal != null && normal.contains(angle)) {
            return JointState.NORMAL
        }
        
        // Check PAD (acceptable state)
        if (pad != null && pad.contains(angle)) {
            return JointState.PAD
        }
        
        // Check WARNING (error but doesn't invalidate)
        if (warning != null && warning.contains(angle)) {
            return JointState.WARNING
        }
        
        // Check DANGER (injury risk, invalidates rep)
        if (danger != null && danger.contains(angle)) {
            return JointState.DANGER
        }
        
        // Fallback: angle is outside all defined ranges
        // Treat as WARNING (safe default - doesn't invalidate rep like DANGER)
        return JointState.WARNING
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
                    warnings.add("$prefix DANGER range overlaps with WARNING range. WARNING will take priority.")
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
        val transitionMin = downRanges?.getEffectiveMax() ?: 0.0
        val transitionMax = upRanges?.getEffectiveMin() ?: 180.0
        
        // Determine zone
        val zone = when {
            angle >= transitionMax && upRanges != null -> ZoneType.UP_ZONE
            angle <= transitionMin && downRanges != null -> ZoneType.DOWN_ZONE
            else -> ZoneType.TRANSITION
        }
        
        // Determine state within zone
        val state = when (zone) {
            ZoneType.UP_ZONE -> upRanges?.determineState(angle) ?: JointState.TRANSITION
            ZoneType.DOWN_ZONE -> downRanges?.determineState(angle) ?: JointState.TRANSITION
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
        val effectiveMin = ranges.getEffectiveMin()
        val effectiveMax = ranges.getEffectiveMax()
        
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
        
        val state = ranges.determineState(angle)
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
        val effectiveMin = ranges.getEffectiveMin()
        val effectiveMax = ranges.getEffectiveMax()
        
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
        
        val state = ranges.determineState(angleForRangeCheck)
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
 * Each state has a single LocalizedText message (not a collection).
 * 
 * Message Delivery:
 * - DANGER: CRITICAL priority - always delivered, strong haptic
 * - WARNING: WARNING priority - first = audio+visual, repeat = visual only
 * - PAD: TIP priority - visual only
 * - NORMAL: INFO priority - one-time, visual only
 * - PERFECT: MOTIVATION priority - encouragement when reaching perfect form
 * 
 * Example JSON:
 * ```json
 * "stateMessages": {
 *   "perfect": { "ar": "ممتاز!", "en": "Perfect!" },
 *   "normal": { "ar": "جيد", "en": "Good" },
 *   "pad": { "ar": "قريب من الحد", "en": "Near limit" },
 *   "warning": { "ar": "اثنِ أكثر", "en": "Bend more" },
 *   "danger": { "ar": "توقف!", "en": "Stop!" }
 * }
 * ```
 */
data class StateMessages(
    val perfect: LocalizedText? = null,
    val normal: LocalizedText? = null,
    val pad: LocalizedText? = null,
    val warning: LocalizedText? = null,
    val danger: LocalizedText? = null
) {
    /**
     * Get message for a specific state
     * Returns a list for API compatibility (may contain 0 or 1 message)
     */
    fun getMessages(state: JointState): List<LocalizedText> {
        val message = getMessage(state) ?: return emptyList()
        return listOf(message)
    }
    
    /**
     * Get single message for a state (null if not defined)
     */
    fun getMessage(state: JointState): LocalizedText? {
        return when (state) {
            JointState.PERFECT -> perfect
            JointState.NORMAL -> normal
            JointState.PAD -> pad
            JointState.WARNING -> warning
            JointState.DANGER -> danger
            JointState.TRANSITION -> null
        }
    }
    
    /**
     * Check if message is defined for a state
     */
    fun hasMessage(state: JointState): Boolean = getMessage(state) != null
}
