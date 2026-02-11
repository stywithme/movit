package com.trainingvalidator.poc.overlay

import android.graphics.Color
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.StateConfig
import com.trainingvalidator.poc.training.models.StateRanges
import com.trainingvalidator.poc.training.models.ZoneType

/**
 * ArcColorCalculator - Calculates colors for Arc Range Indicator zones
 * 
 * REFACTORED: Now uses StateConfig as the SINGLE SOURCE OF TRUTH for colors.
 * 
 * NEW: Added StateRanges-based color calculation for accurate gradient
 * matching with LineRangeIndicator.
 * 
 * Zone Color Mapping (from StateConfig):
 * - DANGER: dark red
 * - WARNING: light red  
 * - PAD: orange
 * - NORMAL: yellow
 * - PERFECT: green
 * - TRANSITION: blue-gray
 */
object ArcColorCalculator {
    
    // ==================== Colors from StateConfig (Single Source of Truth) ====================
    
    /** Error zone color - delegates to StateConfig.DANGER */
    val COLOR_ERROR: Int get() = StateConfig.getColor(JointState.DANGER)
    
    /** Optimal position color - delegates to StateConfig.PERFECT */
    val COLOR_OPTIMAL: Int get() = StateConfig.getColor(JointState.PERFECT)
    
    /** Approaching boundary color - delegates to StateConfig.NORMAL */
    val COLOR_NEAR_BOUNDARY: Int get() = StateConfig.getColor(JointState.NORMAL)
    
    /** At boundary color - delegates to StateConfig.PAD */
    val COLOR_BOUNDARY: Int get() = StateConfig.getColor(JointState.PAD)
    
    /** Transition zone color - delegates to StateConfig.TRANSITION */
    val COLOR_TRANSITION: Int get() = StateConfig.getColor(JointState.TRANSITION)
    
    /** Warning color - delegates to StateConfig.WARNING */
    val COLOR_WARNING: Int get() = StateConfig.getColor(JointState.WARNING)
    
    // ==================== Zone Determination (Same as Engine) ====================
    
    /**
     * Determine which zone an angle belongs to.
     *
     * Returns a pair of (ZoneType, isOutOfBounds).
     * isOutOfBounds is true when the angle exceeds the defined ranges.
     *
     * @return Pair(ZoneType, isOutOfBounds)
     */
    fun determineZone(
        angle: Double,
        upRangeMin: Double,
        upRangeMax: Double,
        downRangeMin: Double,
        downRangeMax: Double
    ): Pair<ZoneType, Boolean> {
        return when {
            angle > upRangeMax -> Pair(ZoneType.UP_ZONE, true)      // was TOO_HIGH
            angle >= upRangeMin -> Pair(ZoneType.UP_ZONE, false)
            angle > downRangeMax -> Pair(ZoneType.TRANSITION, false)
            angle >= downRangeMin -> Pair(ZoneType.DOWN_ZONE, false)
            else -> Pair(ZoneType.DOWN_ZONE, true)                  // was TOO_LOW
        }
    }
    
    // ==================== Zone Color Getters ====================
    
    /**
     * Get solid color for a specific zone
     * 
     * @param zone The zone type
     * @param isOutOfBounds Whether the angle is outside valid bounds (error state)
     * @return Color for that zone
     */
    fun getZoneColor(zone: ZoneType, isOutOfBounds: Boolean = false): Int {
        if (isOutOfBounds) return COLOR_ERROR
        return when (zone) {
            ZoneType.UP_ZONE, ZoneType.DOWN_ZONE -> COLOR_OPTIMAL
            ZoneType.TRANSITION -> COLOR_TRANSITION
        }
    }
    
    /**
     * Get zone edge color (for gradient edges)
     */
    fun getZoneEdgeColor(): Int = COLOR_BOUNDARY
    
    /**
     * Get zone center color (for gradient center)
     */
    fun getZoneCenterColor(): Int = COLOR_OPTIMAL
    
    // ==================== Angle-based Color Calculation ====================
    
    /**
     * Get color for a specific angle based on its position within the ranges
     * 
     * USES ENGINE ZONE LOGIC:
     * First determines zone using same logic as FormValidator,
     * then applies gradient within valid zones.
     * 
     * @param angle Current joint angle
     * @param upRangeMin UP zone minimum
     * @param upRangeMax UP zone maximum
     * @param downRangeMin DOWN zone minimum
     * @param downRangeMax DOWN zone maximum
     * @return Color for the current angle position
     */
    fun getColorForAngle(
        angle: Double,
        upRangeMin: Double,
        upRangeMax: Double,
        downRangeMin: Double,
        downRangeMax: Double
    ): Int {
        // Use Engine-compatible zone determination
        val (zone, isOutOfBounds) = determineZone(angle, upRangeMin, upRangeMax, downRangeMin, downRangeMax)
        
        if (isOutOfBounds) return COLOR_ERROR
        
        return when (zone) {
            ZoneType.UP_ZONE -> {
                // Apply gradient within UP zone (Asymmetric)
                getGradientColorInZone(angle, upRangeMin, upRangeMax, ZoneType.UP_ZONE)
            }
            
            ZoneType.DOWN_ZONE -> {
                // Apply gradient within DOWN zone (Asymmetric)
                getGradientColorInZone(angle, downRangeMin, downRangeMax, ZoneType.DOWN_ZONE)
            }
            
            ZoneType.TRANSITION -> COLOR_TRANSITION
        }
    }
    
    /**
     * Calculate gradient color within a valid zone with ASYMMETRIC logic
     * 
     * Logic:
     * - Outer side (towards error): Green -> Yellow -> Orange
     * - Inner side (towards transition): Green -> Yellow (stays yellow)
     */
    private fun getGradientColorInZone(
        angle: Double, 
        min: Double, 
        max: Double,
        zone: ZoneType
    ): Int {
        return getColorForAngleInRange(angle, min, max, zone)
    }
    
    /**
     * Get color for an angle within a specific range
     * Now supports ASYMMETRIC coloring based on zone type
     */
    fun getColorForAngleInRange(
        angle: Double, 
        rangeMin: Double, 
        rangeMax: Double,
        zone: ZoneType? = null // Optional for backward compatibility
    ): Int {
        val center = (rangeMin + rangeMax) / 2
        val halfRange = (rangeMax - rangeMin) / 2
        
        // Handle edge case of zero range
        if (halfRange <= 0) return COLOR_OPTIMAL
        
        val distFromCenter = kotlin.math.abs(angle - center)
        val normalizedDist = (distFromCenter / halfRange).coerceIn(0.0, 1.0)
        
        // Determine if we are on the "Outer" side (towards error) or "Inner" side (towards transition)
        val isOuterSide = when (zone) {
            ZoneType.UP_ZONE -> angle > center    // UpZone: Higher angles are outer (towards TOO_HIGH)
            ZoneType.DOWN_ZONE -> angle < center  // DownZone: Lower angles are outer (towards TOO_LOW)
            else -> true // Default to symmetric behavior if zone not specified
        }

        return when {
            // Center 40% - pure optimal green
            normalizedDist < 0.4 -> COLOR_OPTIMAL
            
            // 40% - 70% - gradient from green to yellow (Both sides)
            normalizedDist < 0.7 -> {
                val t = ((normalizedDist - 0.4) / 0.3).toFloat()
                interpolateColor(COLOR_OPTIMAL, COLOR_NEAR_BOUNDARY, t)
            }
            
            // 70% - 100% - Outer Edge
            else -> {
                if (isOuterSide) {
                    // Outer side: Yellow -> Orange (Warning/Danger)
                    val t = ((normalizedDist - 0.7) / 0.3).toFloat()
                    interpolateColor(COLOR_NEAR_BOUNDARY, COLOR_BOUNDARY, t)
                } else {
                    // Inner side: Stay Yellow (Safe transition)
                    // We keep it at COLOR_NEAR_BOUNDARY (Yellow)
                    COLOR_NEAR_BOUNDARY
                }
            }
        }
    }
    
    // ==================== Color Interpolation ====================
    
    /**
     * Interpolate between two colors
     * 
     * @param colorA Start color
     * @param colorB End color
     * @param ratio Interpolation factor (0.0 = colorA, 1.0 = colorB)
     * @return Interpolated color
     */
    fun interpolateColor(colorA: Int, colorB: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        
        val aA = Color.alpha(colorA)
        val rA = Color.red(colorA)
        val gA = Color.green(colorA)
        val bA = Color.blue(colorA)
        
        val aB = Color.alpha(colorB)
        val rB = Color.red(colorB)
        val gB = Color.green(colorB)
        val bB = Color.blue(colorB)
        
        return Color.argb(
            (aA + (aB - aA) * r).toInt(),
            (rA + (rB - rA) * r).toInt(),
            (gA + (gB - gA) * r).toInt(),
            (bA + (bB - bA) * r).toInt()
        )
    }
    
    /**
     * Apply alpha to a color
     * 
     * @param color Base color
     * @param alpha Alpha value (0-255)
     * @return Color with modified alpha
     */
    fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
    
    /**
     * Apply opacity to a color
     * 
     * @param color Base color
     * @param opacity Opacity value (0.0 - 1.0)
     * @return Color with modified alpha
     */
    fun withOpacity(color: Int, opacity: Float): Int {
        return withAlpha(color, (opacity.coerceIn(0f, 1f) * 255).toInt())
    }
    
    // ==================== NEW: StateRanges-based Color Calculation ====================
    
    /**
     * Get color for an angle using StateRanges (matches LineRangeIndicator logic)
     * 
     * This uses the ACTUAL range boundaries from the exercise config for accurate
     * color calculation, instead of fixed percentages.
     * 
     * @param angle Current angle
     * @param stateRanges StateRanges for this zone (up or down)
     * @param zoneType Current zone type
     * @return Color based on which state the angle falls into
     */
    fun getColorForAngleFromRanges(
        angle: Double,
        stateRanges: StateRanges?,
        zoneType: ZoneType
    ): Int {
        if (stateRanges == null) {
            return COLOR_OPTIMAL
        }
        
        // Use the same logic as LineRangeIndicator
        return when {
            stateRanges.perfect.contains(angle) -> COLOR_OPTIMAL
            stateRanges.normal?.contains(angle) == true -> COLOR_NEAR_BOUNDARY
            stateRanges.pad?.contains(angle) == true -> COLOR_BOUNDARY
            stateRanges.warning?.contains(angle) == true -> COLOR_WARNING
            stateRanges.danger?.contains(angle) == true -> COLOR_ERROR
            else -> COLOR_WARNING  // Fallback for angles outside all ranges
        }
    }
    
    /**
     * Get gradient color for arc segment using StateRanges
     * 
     * Calculates smooth gradient based on actual range boundaries.
     * Used for drawing arc segments with proper color transitions.
     * 
     * @param angle Angle to get color for
     * @param stateRanges StateRanges for this zone
     * @param zoneType Zone type (UP_ZONE or DOWN_ZONE)
     * @return Interpolated color
     */
    fun getGradientColorFromRanges(
        angle: Double,
        stateRanges: StateRanges?,
        zoneType: ZoneType
    ): Int {
        if (stateRanges == null) return COLOR_OPTIMAL
        
        val perfect = stateRanges.perfect
        val normal = stateRanges.normal
        val pad = stateRanges.pad
        
        // Calculate center of perfect range
        val center = (perfect.min + perfect.max) / 2.0
        
        // Determine if on upper or lower side of center
        val isUpperSide = angle > center
        
        // Get boundaries for current side
        val perfectBound = if (isUpperSide) perfect.max else perfect.min
        val normalBound = if (normal != null) (if (isUpperSide) normal.max else normal.min) else perfectBound
        val padBound = if (pad != null) (if (isUpperSide) pad.max else pad.min) else normalBound
        
        // Calculate distances
        val distToAngle = kotlin.math.abs(angle - center)
        val distToPerfect = kotlin.math.abs(perfectBound - center)
        val distToNormal = kotlin.math.abs(normalBound - center)
        val distToPad = kotlin.math.abs(padBound - center)
        
        return when {
            // Inside Perfect Range
            distToAngle <= distToPerfect -> COLOR_OPTIMAL
            
            // Between Perfect and Normal (Gradient Green -> Yellow)
            distToAngle <= distToNormal -> {
                val range = distToNormal - distToPerfect
                if (range <= 0) return COLOR_NEAR_BOUNDARY
                val t = ((distToAngle - distToPerfect) / range).toFloat().coerceIn(0f, 1f)
                interpolateColor(COLOR_OPTIMAL, COLOR_NEAR_BOUNDARY, t)
            }
            
            // Between Normal and Pad (Gradient Yellow -> Orange)
            distToAngle <= distToPad -> {
                val range = distToPad - distToNormal
                if (range <= 0) return COLOR_BOUNDARY
                val t = ((distToAngle - distToNormal) / range).toFloat().coerceIn(0f, 1f)
                interpolateColor(COLOR_NEAR_BOUNDARY, COLOR_BOUNDARY, t)
            }
            
            // Outside Pad (Gradient Orange -> Warning Red)
            else -> {
                val warningThreshold = 5.0
                val t = ((distToAngle - distToPad) / warningThreshold).toFloat().coerceIn(0f, 1f)
                interpolateColor(COLOR_BOUNDARY, COLOR_WARNING, t)
            }
        }
    }
}
