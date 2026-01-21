package com.trainingvalidator.poc.overlay

import com.trainingvalidator.poc.training.engine.JointZone
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.models.StateRanges
import com.trainingvalidator.poc.training.models.ZoneType

/**
 * ArcRangeData - Data model for Arc Range visualization
 * 
 * UPDATED: Now supports both legacy JointArrowInfo and new JointStateInfo.
 * Uses StateRanges for accurate color calculation matching LineRangeIndicator.
 * 
 * Coordinate System:
 * - Joint angles: 0° (fully bent) to 180° (fully extended)
 * - Canvas angles: 0° = East (3 o'clock), 90° = South, 180° = West, 270° = North
 * - Mapping: Joint 0° → Canvas 90° (bottom), Joint 180° → Canvas -90° (top)
 */
data class ArcRangeData(
    /** Joint identifier (e.g., "left_knee", "right_hip") */
    val jointCode: String,
    
    /** Center X coordinate in screen pixels */
    val centerX: Float,
    
    /** Center Y coordinate in screen pixels */
    val centerY: Float,
    
    /** Current joint angle in degrees (0-180) */
    val currentAngle: Double,
    
    /** UP zone minimum angle (start of ready position) */
    val upRangeMin: Double,
    
    /** UP zone maximum angle (end of ready position) */
    val upRangeMax: Double,
    
    /** DOWN zone minimum angle (start of target position) */
    val downRangeMin: Double,
    
    /** DOWN zone maximum angle (end of target position) */
    val downRangeMax: Double,
    
    /** Current zone the joint is in */
    val zone: JointZone,
    
    /** Whether joint is in error zone (TOO_HIGH or TOO_LOW) */
    val isError: Boolean,
    
    /** Whether joint is approaching boundary (warning state) */
    val isWarning: Boolean,
    
    /** Whether this is a primary joint for rep counting */
    val isPrimary: Boolean = true,
    
    // ==================== NEW: StateRanges for accurate coloring ====================
    
    /** Current JointState from engine */
    val state: JointState = JointState.NORMAL,
    
    /** Current color from StateConfig */
    val stateColor: Int = 0,
    
    /** StateRanges for UP zone (for gradient calculation) */
    val upStateRanges: StateRanges? = null,
    
    /** StateRanges for DOWN zone (for gradient calculation) */
    val downStateRanges: StateRanges? = null,
    
    /** Current ZoneType from engine */
    val zoneType: ZoneType = ZoneType.UP_ZONE,
    
    /** Whether angles should be inverted for gradient calculation */
    val invertAngles: Boolean = false
) {
    
    /**
     * Convert joint angle (0-180°) to canvas arc angle
     * 
     * Canvas arc angles:
     * - 0° = 3 o'clock (East)
     * - 90° = 6 o'clock (South)  
     * - 180° = 9 o'clock (West)
     * - 270° = 12 o'clock (North)
     * 
     * We want to map:
     * - Joint 0° → Canvas 90° (bottom of arc)
     * - Joint 180° → Canvas -90° (top of arc)
     * 
     * Formula: canvasAngle = 90° - jointAngle
     * 
     * @param jointAngle Angle in joint coordinate system (0-180)
     * @return Angle in canvas coordinate system
     */
    fun toCanvasAngle(jointAngle: Double): Float {
        return (90.0 - jointAngle).toFloat()
    }
    
    /**
     * Get canvas angle for the current joint angle
     */
    fun getCurrentCanvasAngle(): Float {
        return toCanvasAngle(currentAngle)
    }
    
    /**
     * Calculate sweep angle between two joint angles
     * 
     * @param startJointAngle Start angle in joint coordinates
     * @param endJointAngle End angle in joint coordinates
     * @return Sweep angle for canvas.drawArc()
     */
    fun getSweepAngle(startJointAngle: Double, endJointAngle: Double): Float {
        val startCanvas = toCanvasAngle(startJointAngle)
        val endCanvas = toCanvasAngle(endJointAngle)
        return endCanvas - startCanvas
    }
    
    companion object {
        /**
         * Create ArcRangeData from JointStateInfo (NEW - preferred method)
         * 
         * Uses StateRanges for accurate gradient coloring matching LineRangeIndicator.
         * Supports invertIndicator for exercises where lower limb moves up (bicep curl).
         * 
         * IMPORTANT: We store ORIGINAL ranges (no swapping).
         * The drawing code will invert angles when invertAngles = true.
         * This keeps the logic simple: ONE inversion only.
         */
        fun fromStateInfo(
            centerX: Float,
            centerY: Float,
            stateInfo: JointStateInfo
        ): ArcRangeData {
            // Store ORIGINAL ranges - NO swapping
            // The drawing code will handle angle inversion
            val upRanges = stateInfo.upStateRanges
            val downRanges = stateInfo.downStateRanges
            
            val upMin = upRanges?.getEffectiveMin() ?: 90.0
            val upMax = upRanges?.getOutermostMax() ?: 180.0
            val downMin = downRanges?.getOutermostMin() ?: 0.0
            val downMax = downRanges?.getEffectiveMax() ?: 90.0
            
            // Invert the angle for visual display when invertIndicator = true
            // This is for the MOVING INDICATOR position only
            val effectiveAngle = if (stateInfo.invertIndicator) {
                180.0 - stateInfo.currentAngle
            } else {
                stateInfo.currentAngle
            }
            
            // Convert ZoneType to legacy JointZone
            val legacyZone = when (stateInfo.currentZone) {
                ZoneType.UP_ZONE -> when (stateInfo.state) {
                    JointState.DANGER, JointState.WARNING -> {
                        if (stateInfo.currentAngle > 90) JointZone.TOO_HIGH else JointZone.TOO_LOW
                    }
                    else -> JointZone.UP_ZONE
                }
                ZoneType.DOWN_ZONE -> when (stateInfo.state) {
                    JointState.DANGER, JointState.WARNING -> {
                        if (stateInfo.currentAngle < 90) JointZone.TOO_LOW else JointZone.TOO_HIGH
                    }
                    else -> JointZone.DOWN_ZONE
                }
                ZoneType.TRANSITION -> JointZone.TRANSITION
            }
            
            return ArcRangeData(
                jointCode = stateInfo.jointCode,
                centerX = centerX,
                centerY = centerY,
                currentAngle = effectiveAngle,  // Use inverted angle for display
                upRangeMin = upMin,
                upRangeMax = upMax,
                downRangeMin = downMin,
                downRangeMax = downMax,
                zone = legacyZone,
                isError = stateInfo.state == JointState.DANGER || stateInfo.state == JointState.WARNING,
                isWarning = stateInfo.state == JointState.PAD,
                isPrimary = stateInfo.isPrimary,
                state = stateInfo.state,
                stateColor = stateInfo.color,
                upStateRanges = upRanges,
                downStateRanges = downRanges,
                zoneType = stateInfo.currentZone,
                invertAngles = stateInfo.invertIndicator
            )
        }
        
        /**
         * Create ArcRangeData from JointArrowInfo (Legacy)
         * @deprecated Use fromStateInfo() instead
         */
        @Deprecated("Use fromStateInfo() with JointStateInfo instead")
        fun fromArrowInfo(
            jointCode: String,
            centerX: Float,
            centerY: Float,
            arrowInfo: com.trainingvalidator.poc.training.engine.JointArrowInfo
        ): ArcRangeData {
            return ArcRangeData(
                jointCode = jointCode,
                centerX = centerX,
                centerY = centerY,
                currentAngle = arrowInfo.currentAngle,
                upRangeMin = arrowInfo.upRangeMin,
                upRangeMax = arrowInfo.upRangeMax,
                downRangeMin = arrowInfo.downRangeMin,
                downRangeMax = arrowInfo.downRangeMax,
                zone = arrowInfo.zone,
                isError = arrowInfo.isError,
                isWarning = arrowInfo.isWarning,
                isPrimary = arrowInfo.isPrimary
            )
        }
    }
}

/**
 * ArcConfig - Configuration for arc visual appearance
 * 
 * All size values are in dp and will be converted to pixels at draw time.
 */
data class ArcConfig(
    /** Arc radius in dp */
    val radiusDp: Float = 45f,
    
    /** Arc stroke width in dp */
    val strokeWidthDp: Float = 6f,
    
    /** Indicator dot radius in dp */
    val indicatorRadiusDp: Float = 5f,
    
    /** Whether to show current position indicator */
    val showCurrentIndicator: Boolean = true,
    
    /** Whether to show angle labels (future feature) */
    val showRangeLabels: Boolean = false,
    
    /** Whether to enable animations */
    val animationEnabled: Boolean = true,
    
    /** Show arc only when in error or warning state */
    val showOnlyOnError: Boolean = false,
    
    /** Show arc only for primary joints */
    val showOnlyPrimary: Boolean = true,
    
    /** Opacity for the arc (0.0 - 1.0) */
    val arcOpacity: Float = 0.9f
) {
    companion object {
        /** Default configuration */
        val DEFAULT = ArcConfig()
        
        /** Minimal configuration - smaller, less prominent */
        val MINIMAL = ArcConfig(
            radiusDp = 35f,
            strokeWidthDp = 4f,
            indicatorRadiusDp = 4f,
            showCurrentIndicator = true,
            arcOpacity = 0.7f
        )
        
        /** Prominent configuration - larger, more visible */
        val PROMINENT = ArcConfig(
            radiusDp = 55f,
            strokeWidthDp = 8f,
            indicatorRadiusDp = 6f,
            showCurrentIndicator = true,
            arcOpacity = 1.0f
        )
    }
}
