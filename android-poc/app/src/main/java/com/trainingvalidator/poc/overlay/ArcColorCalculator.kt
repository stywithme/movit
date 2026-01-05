package com.trainingvalidator.poc.overlay

import android.graphics.Color
import com.trainingvalidator.poc.training.engine.JointZone

/**
 * ArcColorCalculator - Calculates colors for Arc Range Indicator zones
 * 
 * INTEGRATED with Engine's JointZone system:
 * Uses the same zone calculation logic as FormValidator/PhaseStateMachine
 * 
 * Zone Color Mapping:
 * - TOO_HIGH / TOO_LOW: Red (#FF5252) - Error zones
 * - UP_ZONE / DOWN_ZONE: Green (#00E676) with edge gradient - Valid zones
 * - TRANSITION: Light Blue (#81D4FA) - Movement zone
 * 
 * Gradient within valid zones (matches Engine's warning concept):
 * - Center (40%): Green (optimal)
 * - Near edge (30%): Yellow (approaching boundary - like isWarning)
 * - Edge (30%): Orange (at boundary)
 */
object ArcColorCalculator {
    
    // ==================== Zone Colors (Match Engine States) ====================
    
    /** Error zone color (TOO_HIGH, TOO_LOW) - Maps to JointColor.ERROR_HIGH/ERROR_LOW */
    val COLOR_ERROR = Color.parseColor("#FF5252")
    
    /** Optimal position color (center of valid zones) - Maps to JointColor.CORRECT */
    val COLOR_OPTIMAL = Color.parseColor("#00E676")
    
    /** Approaching boundary color - Maps to isWarning = true */
    val COLOR_NEAR_BOUNDARY = Color.parseColor("#FFEB3B")
    
    /** At boundary color (edge of valid zones) - Near error threshold */
    val COLOR_BOUNDARY = Color.parseColor("#FF9800")
    
    /** Transition zone color - TRANSITION state in Engine */
    val COLOR_TRANSITION = Color.parseColor("#81D4FA")
    
    /** Warning color (approaching error) */
    val COLOR_WARNING = Color.parseColor("#FFC107")
    
    // ==================== Zone Determination (Same as Engine) ====================
    
    /**
     * Determine which zone an angle belongs to
     * 
     * This uses the SAME logic as FormValidator.determineZone() to ensure
     * visual feedback matches the Engine's state.
     * 
     * Zone layout (from PhaseStateMachine):
     *   180° ───── TOO_HIGH (above upRange.max)
     *        ───── upRange.max
     *              UP_ZONE (within upRange)
     *        ───── upRange.min
     *              TRANSITION (between upRange.min and downRange.max)
     *        ───── downRange.max
     *              DOWN_ZONE (within downRange)
     *        ───── downRange.min
     *    0°  ───── TOO_LOW (below downRange.min)
     * 
     * @param angle Current angle to check
     * @param upRangeMin Minimum of UP zone
     * @param upRangeMax Maximum of UP zone
     * @param downRangeMin Minimum of DOWN zone
     * @param downRangeMax Maximum of DOWN zone
     * @return JointZone for this angle
     */
    fun determineZone(
        angle: Double,
        upRangeMin: Double,
        upRangeMax: Double,
        downRangeMin: Double,
        downRangeMax: Double
    ): JointZone {
        return when {
            angle > upRangeMax -> JointZone.TOO_HIGH
            angle >= upRangeMin -> JointZone.UP_ZONE
            angle > downRangeMax -> JointZone.TRANSITION
            angle >= downRangeMin -> JointZone.DOWN_ZONE
            else -> JointZone.TOO_LOW
        }
    }
    
    // ==================== Zone Color Getters ====================
    
    /**
     * Get solid color for a specific zone
     * 
     * @param zone The joint zone
     * @return Color for that zone
     */
    fun getZoneColor(zone: JointZone): Int {
        return when (zone) {
            JointZone.TOO_HIGH, JointZone.TOO_LOW -> COLOR_ERROR
            JointZone.UP_ZONE, JointZone.DOWN_ZONE -> COLOR_OPTIMAL
            JointZone.TRANSITION -> COLOR_TRANSITION
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
        val zone = determineZone(angle, upRangeMin, upRangeMax, downRangeMin, downRangeMax)
        
        return when (zone) {
            JointZone.TOO_HIGH, JointZone.TOO_LOW -> COLOR_ERROR
            
            JointZone.UP_ZONE -> {
                // Apply gradient within UP zone
                getGradientColorInZone(angle, upRangeMin, upRangeMax)
            }
            
            JointZone.DOWN_ZONE -> {
                // Apply gradient within DOWN zone
                getGradientColorInZone(angle, downRangeMin, downRangeMax)
            }
            
            JointZone.TRANSITION -> COLOR_TRANSITION
        }
    }
    
    /**
     * Calculate gradient color within a valid zone
     * 
     * Color distribution:
     * - 0% - 40% from center: Pure green (optimal)
     * - 40% - 70% from center: Green → Yellow gradient
     * - 70% - 100% from center: Yellow → Orange gradient
     * 
     * @param angle Current angle
     * @param min Zone minimum
     * @param max Zone maximum
     * @return Interpolated color based on position within zone
     */
    private fun getGradientColorInZone(angle: Double, min: Double, max: Double): Int {
        return getColorForAngleInRange(angle, min, max)
    }
    
    /**
     * Get color for an angle within a specific range (public version)
     * 
     * Used by ArcRangeIndicator to calculate color for each sub-segment
     * based on actual distance from center of the range.
     * 
     * Color distribution:
     * - 0% - 40% from center: Pure green (optimal)
     * - 40% - 70% from center: Green → Yellow gradient
     * - 70% - 100% from center: Yellow → Orange gradient
     * 
     * @param angle Current angle
     * @param rangeMin Range minimum
     * @param rangeMax Range maximum
     * @return Interpolated color based on position within range
     */
    fun getColorForAngleInRange(angle: Double, rangeMin: Double, rangeMax: Double): Int {
        val center = (rangeMin + rangeMax) / 2
        val halfRange = (rangeMax - rangeMin) / 2
        
        // Handle edge case of zero range
        if (halfRange <= 0) return COLOR_OPTIMAL
        
        val distFromCenter = kotlin.math.abs(angle - center)
        val normalizedDist = (distFromCenter / halfRange).coerceIn(0.0, 1.0)
        
        return when {
            // Center 40% - pure optimal green
            normalizedDist < 0.4 -> COLOR_OPTIMAL
            
            // 40% - 70% - gradient from green to yellow
            normalizedDist < 0.7 -> {
                val t = ((normalizedDist - 0.4) / 0.3).toFloat()
                interpolateColor(COLOR_OPTIMAL, COLOR_NEAR_BOUNDARY, t)
            }
            
            // 70% - 100% - gradient from yellow to orange
            else -> {
                val t = ((normalizedDist - 0.7) / 0.3).toFloat()
                interpolateColor(COLOR_NEAR_BOUNDARY, COLOR_BOUNDARY, t)
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
}
