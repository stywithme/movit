package com.trainingvalidator.poc.overlay

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.JointArrowInfo
import com.trainingvalidator.poc.training.models.AngleRange
import com.trainingvalidator.poc.training.models.AngleColorResolver
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.models.StateConfig
import com.trainingvalidator.poc.training.models.StateRanges
import kotlin.math.abs

/**
 * LineRangeIndicator - Draws a colored line on the appropriate limb segment
 * 
 * TWO-LAYER VISUAL SYSTEM:
 * 
 * 1. STATIC TRACK (background):
 *    - Full-length colored gradient showing the entire movement path
 *    - Colors: Green (center) → Yellow → Orange → Red (edges)
 *    - Configurable via app_settings.json
 * 
 * 2. MOVING INDICATOR (foreground):
 *    - Current position indicator that moves along the track
 *    - Color matches current position in the range
 *    - Thicker, more visible line with endpoint circle
 * 
 * All settings are loaded from app_settings.json via SettingsManager
 */
class LineRangeIndicator {
    
    /**
     * Which limb segment to draw on
     */
    enum class LimbType { 
        UPPER,      // Draw on the upper limb (angle > centerAngle)
        LOWER,      // Draw on the lower limb (angle < centerAngle)
        NONE        // At center (angle ≈ centerAngle)
    }
    
    // ==================== Settings (from SettingsManager) ====================
    
    private val centerAngle: Double
        get() = SettingsManager.getLineIndicatorCenterAngle()
    
    private val smoothingFactor: Float
        get() = SettingsManager.getLineIndicatorSmoothingFactor()
    
    private val snapToZeroThreshold: Float
        get() = SettingsManager.getLineIndicatorSnapThreshold()
    
    private val trackAlpha: Int
        get() = SettingsManager.getLineIndicatorTrackAlpha()
    
    private val trackWidthRatio: Float
        get() = SettingsManager.getLineIndicatorTrackWidthRatio()
    
    private val indicatorWidthMultiplier: Float
        get() = SettingsManager.getLineIndicatorWidthMultiplier()
    
    // ==================== Paint Objects ====================
    
    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    
    private val trackPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    
    // ==================== Smoothing State ====================
    
    private val previousLengths = mutableMapOf<String, Float>()
    private val previousLimbTypes = mutableMapOf<String, LimbType>()
    private val previousIndicatorColors = mutableMapOf<String, Int>()
    
    // Limb transition state - for smooth crossover
    private val limbTransitionProgress = mutableMapOf<String, Float>()  // 0.0 = old limb, 1.0 = new limb
    private val pendingLimbChange = mutableMapOf<String, LimbType>()
    
    // Color smoothing factor (0.1 = slow, 0.5 = fast)
    private val colorSmoothingFactor = 0.3f
    
    // Limb switch thresholds
    private val limbSwitchThreshold = 5.0      // Degrees past center to confirm switch
    private val limbHysteresisThreshold = 3.0  // Degrees to prevent flickering back
    private val limbTransitionSpeed = 0.15f    // How fast to transition (0.1 = slow, 0.5 = fast)
    
    // ==================== Public API ====================
    
    /**
     * Determine which limb to draw on based on current angle (simple version)
     */
    fun getTargetLimb(currentAngle: Double): LimbType {
        val deviation = currentAngle - centerAngle
        
        return when {
            deviation > 2.0 -> LimbType.UPPER
            deviation < -2.0 -> LimbType.LOWER
            else -> LimbType.NONE
        }
    }
    
    /**
     * Determine which limb to draw on based on current angle with optional inversion
     * 
     * When invertIndicator = true:
     * - Flips the angle (180 - angle) so movement appears on opposite limb
     * - Used for exercises where lower limb moves UP (bicep curl: forearm → shoulder)
     * 
     * @param currentAngle Current joint angle
     * @param invertIndicator Whether to invert the angle for limb calculation
     * @return LimbType based on effective angle
     */
    fun getTargetLimb(currentAngle: Double, invertIndicator: Boolean): LimbType {
        // When inverted, flip the angle: 0° ↔ 180°
        val effectiveAngle = if (invertIndicator) 180.0 - currentAngle else currentAngle
        return getTargetLimb(effectiveAngle)
    }
    
    /**
     * Get target limb with hysteresis to prevent flickering at center
     * 
     * Uses a two-threshold system:
     * - limbSwitchThreshold: Degrees past center needed to SWITCH to new limb
     * - limbHysteresisThreshold: Degrees past center needed to STAY on current limb
     * 
     * This creates a "dead zone" where the limb won't switch back and forth.
     */
    fun getTargetLimbWithHysteresis(
        jointCode: String, 
        currentAngle: Double, 
        invertIndicator: Boolean = false
    ): LimbType {
        val effectiveAngle = if (invertIndicator) 180.0 - currentAngle else currentAngle
        val deviation = effectiveAngle - centerAngle
        val previousLimb = previousLimbTypes[jointCode] ?: LimbType.NONE
        
        val newLimb = when (previousLimb) {
            LimbType.UPPER -> {
                // Currently on UPPER - need significant move below center to switch to LOWER
                when {
                    deviation < -limbSwitchThreshold -> LimbType.LOWER
                    deviation > limbHysteresisThreshold -> LimbType.UPPER
                    else -> LimbType.UPPER  // Stay on UPPER in hysteresis zone
                }
            }
            LimbType.LOWER -> {
                // Currently on LOWER - need significant move above center to switch to UPPER
                when {
                    deviation > limbSwitchThreshold -> LimbType.UPPER
                    deviation < -limbHysteresisThreshold -> LimbType.LOWER
                    else -> LimbType.LOWER  // Stay on LOWER in hysteresis zone
                }
            }
            LimbType.NONE -> {
                // No previous limb - use standard threshold
                when {
                    deviation > limbSwitchThreshold -> LimbType.UPPER
                    deviation < -limbSwitchThreshold -> LimbType.LOWER
                    else -> LimbType.NONE
                }
            }
        }
        
        // Track if limb changed for transition animation
        if (newLimb != previousLimb && newLimb != LimbType.NONE) {
            pendingLimbChange[jointCode] = newLimb
            limbTransitionProgress[jointCode] = 0f
        }
        
        previousLimbTypes[jointCode] = newLimb
        return newLimb
    }
    
    /**
     * Get transition progress for smooth crossover animation
     * Returns 0.0 when just switched, increases to 1.0 over time
     */
    fun updateTransitionProgress(jointCode: String): Float {
        val current = limbTransitionProgress[jointCode] ?: 1f
        val updated = (current + limbTransitionSpeed).coerceAtMost(1f)
        limbTransitionProgress[jointCode] = updated
        return updated
    }
    
    /**
     * Check if currently in a limb transition
     */
    fun isInLimbTransition(jointCode: String): Boolean {
        val progress = limbTransitionProgress[jointCode] ?: 1f
        return progress < 1f
    }
    
    /**
     * Get effective angle for visual calculations
     * When invertIndicator = true: flips 0° ↔ 180°
     */
    fun getEffectiveAngle(currentAngle: Double, invertIndicator: Boolean): Double {
        return if (invertIndicator) 180.0 - currentAngle else currentAngle
    }
    
    /**
     * Calculate indicator length based on distance from center angle
     * 
     * IMPROVED: Uses enhanced smoothing for limb transitions:
     * - During transition, applies extra smoothing to prevent jumps
     * - Gradually increases/decreases length based on transition progress
     */
    fun calculateLineLength(
        jointCode: String,
        currentAngle: Double,
        maxLength: Float,
        invertIndicator: Boolean = false
    ): Float {
        // Apply angle inversion if needed (flips 0° ↔ 180°)
        val effectiveAngle = getEffectiveAngle(currentAngle, invertIndicator)
        
        val maxDeviation = 90.0  // 0° or 180° from center
        val deviation = abs(effectiveAngle - centerAngle)
        val normalizedDeviation = (deviation / maxDeviation).coerceIn(0.0, 1.0)
        val rawLength = normalizedDeviation.toFloat() * maxLength
        
        // Check if we're in a limb transition
        val transitionProgress = limbTransitionProgress[jointCode] ?: 1f
        val isTransitioning = transitionProgress < 1f
        
        // Apply extra smoothing during transitions
        val effectiveSmoothingFactor = if (isTransitioning) {
            smoothingFactor * 0.5f  // Slower smoothing during transition
        } else {
            smoothingFactor
        }
        
        // Apply smoothing
        val previousLength = previousLengths[jointCode] ?: rawLength
        val smoothedLength = lerp(previousLength, rawLength, effectiveSmoothingFactor)
        previousLengths[jointCode] = smoothedLength
        
        // During transition, scale the length by transition progress
        // This creates a "fade in" effect on the new limb
        val finalLength = if (isTransitioning) {
            smoothedLength * transitionProgress
        } else {
            smoothedLength
        }
        
        return if (finalLength < maxLength * snapToZeroThreshold) 0f else finalLength
    }
    
    private fun lerp(from: Float, to: Float, factor: Float): Float {
        return from + (to - from) * factor
    }
    
    fun reset() {
        previousLengths.clear()
        previousLimbTypes.clear()
        previousIndicatorColors.clear()
        limbTransitionProgress.clear()
        pendingLimbChange.clear()
    }
    
    // ==================== NEW: State-based Methods ====================
    
    /**
     * Draw static colored track using JointStateInfo
     * 
     * Uses StateConfig colors for consistency with unified state system.
     * Calculates gradient positions from actual StateRanges.
     */
    fun drawTrackNew(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        targetX: Float,
        targetY: Float,
        maxLength: Float,
        style: LineStyleNew,
        stateInfo: JointStateInfo,
        limbType: LimbType
    ) {
        val dx = targetX - centerX
        val dy = targetY - centerY
        val fullLength = kotlin.math.sqrt(dx * dx + dy * dy)
        if (fullLength <= 0) return
        
        val nx = dx / fullLength
        val ny = dy / fullLength
        
        val trackEndX = centerX + nx * maxLength
        val trackEndY = centerY + ny * maxLength
        
        // Draw track using BOTH upRanges and downRanges for proper TRANSITION detection
        drawColoredTrackWithFullRanges(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            endX = trackEndX,
            endY = trackEndY,
            style = style,
            upRanges = stateInfo.upStateRanges,
            downRanges = stateInfo.downStateRanges,
            limbType = limbType,
            invertAngles = stateInfo.invertIndicator
        )
    }
    
    /**
     * Draw moving indicator using JointStateInfo
     */
    fun drawIndicatorNew(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        targetX: Float,
        targetY: Float,
        lineLength: Float,
        stateInfo: JointStateInfo,
        style: LineStyleNew,
        density: Float,
        maxLength: Float,
        limbType: LimbType
    ) {
        if (lineLength <= 0) return
        
        val dx = targetX - centerX
        val dy = targetY - centerY
        val fullLength = kotlin.math.sqrt(dx * dx + dy * dy)
        if (fullLength <= 0) return
        
        val nx = dx / fullLength
        val ny = dy / fullLength
        
        val indicatorEndX = centerX + nx * lineLength
        val indicatorEndY = centerY + ny * lineLength
        
        val isEndpointValid = isEndpointValidFromState(stateInfo, limbType)
        
        drawMovingIndicatorNew(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            endX = indicatorEndX,
            endY = indicatorEndY,
            stateInfo = stateInfo,
            style = style,
            lineLength = lineLength,
            maxLength = maxLength,
            isEndpointValid = isEndpointValid
        )
    }
    
    /**
     * Determine if endpoint is valid from StateRanges
     * 
     * FIXED: Uses correct ranges for each limb:
     * - UPPER limb uses upStateRanges
     * - LOWER limb uses downStateRanges
     * 
     * No hardcoded values - uses actual config:
     * - If no DANGER zone defined for that direction → endpoint is valid
     * - If DANGER zone exists but doesn't reach the extreme (0° or 180°) → valid
     */
    private fun isEndpointValidFromState(stateInfo: JointStateInfo, limbType: LimbType): Boolean {
        // Use the correct ranges for each limb
        val rangesForLimb = when (limbType) {
            LimbType.UPPER -> stateInfo.upStateRanges
            LimbType.LOWER -> stateInfo.downStateRanges
            LimbType.NONE -> return true
        } ?: return true
        
        return when (limbType) {
            LimbType.UPPER -> {
                // Valid if no danger at high angles, or danger.max < 180
                rangesForLimb.danger == null || rangesForLimb.danger.max < 180.0
            }
            LimbType.LOWER -> {
                // Valid if no danger at low angles, or danger.min > 0
                rangesForLimb.danger == null || rangesForLimb.danger.min > 0.0
            }
            LimbType.NONE -> true
        }
    }
    
    /**
     * Draw colored track using BOTH upRanges and downRanges
     * 
     * This is the CORRECT implementation because:
     * - TRANSITION zone is determined by the GAP between upRange and downRange
     * - A single limb track can contain: DOWN states → TRANSITION → UP states
     * - We need BOTH ranges to properly identify what's TRANSITION
     * 
     * Uses AngleColorResolver.resolve() which is the SINGLE SOURCE OF TRUTH.
     */
    private fun drawColoredTrackWithFullRanges(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        endX: Float,
        endY: Float,
        style: LineStyleNew,
        upRanges: StateRanges?,
        downRanges: StateRanges?,
        limbType: LimbType,
        invertAngles: Boolean
    ) {
        val isUpperLimb = limbType == LimbType.UPPER
        val steps = 30
        
        val colors = mutableListOf<Int>()
        val positions = mutableListOf<Float>()
        var lastColor: Int? = null
        
        for (i in 0..steps) {
            val position = i.toFloat() / steps
            
            // Convert position to visual angle
            val visualAngle = if (isUpperLimb) {
                90.0 + (position * 90.0)  // 90° → 180°
            } else {
                90.0 - (position * 90.0)  // 90° → 0°
            }
            
            // Convert to original angle (for range checking)
            val originalAngle = if (invertAngles) 180.0 - visualAngle else visualAngle
            
            // Use AngleColorResolver.resolve() - SINGLE SOURCE OF TRUTH
            // This properly determines zone (UP/DOWN/TRANSITION) and state
            val color = AngleColorResolver.resolve(originalAngle, upRanges, downRanges).color
            
            if (color != lastColor) {
                if (lastColor != null && positions.isNotEmpty()) {
                    colors.add(lastColor)
                    positions.add(position)
                }
                colors.add(color)
                positions.add(position)
                lastColor = color
            }
        }
        
        // Ensure valid gradient
        if (colors.isEmpty()) {
            val fallback = StateConfig.getColor(JointState.NORMAL)
            colors.add(fallback)
            colors.add(fallback)
            positions.add(0f)
            positions.add(1f)
        }
        
        if (positions.first() > 0f) {
            colors.add(0, colors.first())
            positions.add(0, 0f)
        }
        if (positions.last() < 1f) {
            colors.add(colors.last())
            positions.add(1f)
        }
        
        val gradient = LinearGradient(
            centerX, centerY, endX, endY,
            colors.toIntArray(), positions.toFloatArray(),
            Shader.TileMode.CLAMP
        )
        
        trackPaint.shader = gradient
        trackPaint.strokeWidth = style.strokeWidth * trackWidthRatio
        trackPaint.alpha = trackAlpha
        
        canvas.drawLine(centerX, centerY, endX, endY, trackPaint)
        
        trackPaint.shader = null
    }
    
    /**
     * @deprecated Use drawColoredTrackWithFullRanges instead
     */
    @Deprecated("Use drawColoredTrackWithFullRanges instead")
    private fun drawColoredTrackFromRanges(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        endX: Float,
        endY: Float,
        style: LineStyleNew,
        stateRanges: StateRanges?,
        limbType: LimbType,
        invertAngles: Boolean = false
    ) {
        // Use AngleColorResolver as SINGLE SOURCE OF TRUTH
        val isUpperLimb = limbType == LimbType.UPPER
        val (colors, positions) = AngleColorResolver.buildGradientForTrack(
            ranges = stateRanges,
            isUpperLimb = isUpperLimb,
            invertAngles = invertAngles,
            steps = 30  // Higher for smoother gradient
        )
        
        val gradient = LinearGradient(
            centerX, centerY, endX, endY,
            colors, positions,
            Shader.TileMode.CLAMP
        )
        
        trackPaint.shader = gradient
        trackPaint.strokeWidth = style.strokeWidth * trackWidthRatio
        trackPaint.alpha = trackAlpha
        
        canvas.drawLine(centerX, centerY, endX, endY, trackPaint)
        
        trackPaint.shader = null
    }
    
    
    /**
     * Draw moving indicator using JointStateInfo
     * 
     * Uses AngleColorResolver as SINGLE SOURCE OF TRUTH for colors.
     * 
     * FIXED: 
     * - Ignores TRANSITION color (blue) - uses NORMAL color instead
     * - Applies color smoothing to prevent rapid color flickering
     */
    private fun drawMovingIndicatorNew(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        endX: Float,
        endY: Float,
        stateInfo: JointStateInfo,
        style: LineStyleNew,
        lineLength: Float,
        maxLength: Float,
        isEndpointValid: Boolean
    ) {
        // Get target color from AngleColorResolver (SINGLE SOURCE OF TRUTH)
        // This ensures moving indicator color matches the static track at the same angle
        val colorResult = AngleColorResolver.resolve(
            angle = stateInfo.currentAngle,
            upRanges = stateInfo.upStateRanges,
            downRanges = stateInfo.downStateRanges
        )
        
        // Use NORMAL color for TRANSITION zone (movement between up/down)
        val targetColor = if (colorResult.state == JointState.TRANSITION) {
            StateConfig.getColor(JointState.NORMAL)
        } else {
            colorResult.color
        }
        
        // Apply color smoothing to prevent rapid flickering
        val jointCode = stateInfo.jointCode
        val previousColor = previousIndicatorColors[jointCode]
        val smoothedColor = if (previousColor != null) {
            smoothColor(previousColor, targetColor, colorSmoothingFactor)
        } else {
            targetColor
        }
        previousIndicatorColors[jointCode] = smoothedColor
        
        linePaint.color = smoothedColor
        linePaint.strokeWidth = style.strokeWidth * indicatorWidthMultiplier
        linePaint.alpha = 255
        canvas.drawLine(centerX, centerY, endX, endY, linePaint)
        
        drawEndpointNew(canvas, endX, endY, smoothedColor, style, stateInfo)
    }
    
    /**
     * Smooth transition between colors
     */
    private fun smoothColor(from: Int, to: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        val aA = android.graphics.Color.alpha(from)
        val rA = android.graphics.Color.red(from)
        val gA = android.graphics.Color.green(from)
        val bA = android.graphics.Color.blue(from)
        
        val aB = android.graphics.Color.alpha(to)
        val rB = android.graphics.Color.red(to)
        val gB = android.graphics.Color.green(to)
        val bB = android.graphics.Color.blue(to)
        
        return android.graphics.Color.argb(
            (aA + (aB - aA) * f).toInt(),
            (rA + (rB - rA) * f).toInt(),
            (gA + (gB - gA) * f).toInt(),
            (bA + (bB - bA) * f).toInt()
        )
    }
    
    /**
     * Get color for position using StateConfig colors
     */
    private fun getColorForPositionNew(position: Float, isEndpointValid: Boolean): Int {
        val perfectColor = StateConfig.getColor(JointState.PERFECT)
        val normalColor = StateConfig.getColor(JointState.NORMAL)
        val padColor = StateConfig.getColor(JointState.PAD)
        val dangerColor = StateConfig.getColor(JointState.DANGER)
        
        val (colors, positions) = if (isEndpointValid) {
            intArrayOf(perfectColor, normalColor, perfectColor) to 
                floatArrayOf(0.0f, 0.5f, 1.0f)
        } else {
            intArrayOf(perfectColor, normalColor, padColor, dangerColor) to 
                floatArrayOf(0.0f, 0.3f, 0.6f, 1.0f)
        }
        
        for (i in 0 until positions.size - 1) {
            if (position <= positions[i + 1]) {
                val segmentStart = positions[i]
                val segmentEnd = positions[i + 1]
                val segmentRange = segmentEnd - segmentStart
                val segmentProgress = if (segmentRange > 0) {
                    ((position - segmentStart) / segmentRange).coerceIn(0f, 1f)
                } else 0f
                
                return blendColors(colors[i], colors[i + 1], segmentProgress)
            }
        }
        
        return colors.last()
    }
    
    /**
     * Draw endpoint circle using JointStateInfo
     */
    private fun drawEndpointNew(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
        style: LineStyleNew,
        stateInfo: JointStateInfo
    ) {
        linePaint.style = Paint.Style.FILL
        linePaint.color = color
        linePaint.alpha = 220
        
        val radius = when (stateInfo.state) {
            JointState.DANGER, JointState.WARNING -> style.jointRadius * 1.2f
            JointState.PAD -> style.jointRadius * 1.0f
            else -> style.jointRadius * 0.8f
        }
        
        canvas.drawCircle(x, y, radius, linePaint)
        
        linePaint.style = Paint.Style.STROKE
        linePaint.alpha = 255
    }
    
    // ==================== LEGACY: Arrow-based Methods ====================
    
    /**
     * Draw ONLY the static colored track (call for both UP and DOWN limbs)
     * @deprecated Use drawTrackNew() with JointStateInfo instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use drawTrackNew() with JointStateInfo instead")
    fun drawTrack(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        targetX: Float,
        targetY: Float,
        maxLength: Float,
        style: LineStyle,
        arrowInfo: JointArrowInfo,
        limbType: LimbType
    ) {
        val dx = targetX - centerX
        val dy = targetY - centerY
        val fullLength = kotlin.math.sqrt(dx * dx + dy * dy)
        if (fullLength <= 0) return
        
        val nx = dx / fullLength
        val ny = dy / fullLength
        
        val trackEndX = centerX + nx * maxLength
        val trackEndY = centerY + ny * maxLength
        
        // Check if endpoint is within valid range based on exercise config
        // - UPPER (→180°): valid if upRangeMax >= 175° (exercise allows full extension)
        // - LOWER (→0°): valid if downRangeMin <= 5° (exercise allows full flexion)
        val isEndpointValid = when (limbType) {
            LimbType.UPPER -> arrowInfo.upRangeMax >= 175.0
            LimbType.LOWER -> arrowInfo.downRangeMin <= 5.0
            LimbType.NONE -> false
        }
        
        drawColoredTrack(canvas, centerX, centerY, trackEndX, trackEndY, style, isEndpointValid)
    }
    
    /**
     * Draw ONLY the moving indicator (call for current angle direction)
     * @deprecated Use drawIndicatorNew() with JointStateInfo instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use drawIndicatorNew() with JointStateInfo instead")
    fun drawIndicator(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        targetX: Float,
        targetY: Float,
        lineLength: Float,
        arrowInfo: JointArrowInfo,
        style: LineStyle,
        density: Float,
        maxLength: Float,
        limbType: LimbType
    ) {
        if (lineLength <= 0) return
        
        val dx = targetX - centerX
        val dy = targetY - centerY
        val fullLength = kotlin.math.sqrt(dx * dx + dy * dy)
        if (fullLength <= 0) return
        
        val nx = dx / fullLength
        val ny = dy / fullLength
        
        val indicatorEndX = centerX + nx * lineLength
        val indicatorEndY = centerY + ny * lineLength
        
        // Check if endpoint is within valid range based on exercise config
        val isEndpointValid = when (limbType) {
            LimbType.UPPER -> arrowInfo.upRangeMax >= 175.0
            LimbType.LOWER -> arrowInfo.downRangeMin <= 5.0
            LimbType.NONE -> false
        }
        
        drawMovingIndicator(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            endX = indicatorEndX,
            endY = indicatorEndY,
            arrowInfo = arrowInfo,
            style = style,
            lineLength = lineLength,
            maxLength = maxLength,
            isEndpointValid = isEndpointValid
        )
    }
    
    /**
     * Draw the static colored track showing full movement range (Legacy)
     * Now uses StateConfig colors for consistency
     */
    @Suppress("DEPRECATION")
    private fun drawColoredTrack(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        endX: Float,
        endY: Float,
        style: LineStyle,
        isEndpointValid: Boolean
    ) {
        // Use StateConfig colors for consistency
        val perfectColor = StateConfig.getColor(JointState.PERFECT)
        val normalColor = StateConfig.getColor(JointState.NORMAL)
        val padColor = StateConfig.getColor(JointState.PAD)
        val dangerColor = StateConfig.getColor(JointState.DANGER)
        
        val (colors, positions) = if (isEndpointValid) {
            intArrayOf(perfectColor, normalColor, perfectColor) to 
                floatArrayOf(0.0f, 0.5f, 1.0f)
        } else {
            intArrayOf(perfectColor, normalColor, padColor, dangerColor) to 
                floatArrayOf(0.0f, 0.3f, 0.6f, 1.0f)
        }
        
        val gradient = LinearGradient(
            centerX, centerY, endX, endY,
            colors, positions,
            Shader.TileMode.CLAMP
        )
        
        trackPaint.shader = gradient
        trackPaint.strokeWidth = style.strokeWidth * trackWidthRatio
        trackPaint.alpha = trackAlpha
        
        canvas.drawLine(centerX, centerY, endX, endY, trackPaint)
        
        trackPaint.shader = null
    }
    
    /**
     * Draw the moving indicator showing current position (Legacy)
     * Now uses StateConfig colors for consistency
     */
    @Suppress("DEPRECATION")
    private fun drawMovingIndicator(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        endX: Float,
        endY: Float,
        arrowInfo: JointArrowInfo,
        style: LineStyle,
        lineLength: Float,
        maxLength: Float,
        isEndpointValid: Boolean
    ) {
        val positionRatio = if (maxLength > 0) (lineLength / maxLength).coerceIn(0f, 1f) else 0f
        val color = getColorForPositionLegacy(positionRatio, isEndpointValid)
        
        linePaint.color = color
        linePaint.strokeWidth = style.strokeWidth * indicatorWidthMultiplier
        linePaint.alpha = 255
        canvas.drawLine(centerX, centerY, endX, endY, linePaint)
        
        drawEndpoint(canvas, endX, endY, color, style, arrowInfo)
    }
    
    /**
     * Get color based on position (Legacy - uses StateConfig colors now)
     */
    private fun getColorForPositionLegacy(position: Float, isEndpointValid: Boolean): Int {
        val perfectColor = StateConfig.getColor(JointState.PERFECT)
        val normalColor = StateConfig.getColor(JointState.NORMAL)
        val padColor = StateConfig.getColor(JointState.PAD)
        val dangerColor = StateConfig.getColor(JointState.DANGER)
        
        val (colors, positions) = if (isEndpointValid) {
            intArrayOf(perfectColor, normalColor, perfectColor) to 
                floatArrayOf(0.0f, 0.5f, 1.0f)
        } else {
            intArrayOf(perfectColor, normalColor, padColor, dangerColor) to 
                floatArrayOf(0.0f, 0.3f, 0.6f, 1.0f)
        }
        
        for (i in 0 until positions.size - 1) {
            if (position <= positions[i + 1]) {
                val segmentStart = positions[i]
                val segmentEnd = positions[i + 1]
                val segmentRange = segmentEnd - segmentStart
                val segmentProgress = if (segmentRange > 0) {
                    ((position - segmentStart) / segmentRange).coerceIn(0f, 1f)
                } else 0f
                
                return blendColors(colors[i], colors[i + 1], segmentProgress)
            }
        }
        
        return colors.last()
    }
    
    /**
     * Blend two colors based on ratio (0 = color1, 1 = color2)
     */
    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        
        val a1 = (color1 shr 24) and 0xFF
        val r1 = (color1 shr 16) and 0xFF
        val g1 = (color1 shr 8) and 0xFF
        val b1 = color1 and 0xFF
        
        val a2 = (color2 shr 24) and 0xFF
        val r2 = (color2 shr 16) and 0xFF
        val g2 = (color2 shr 8) and 0xFF
        val b2 = color2 and 0xFF
        
        val a = (a1 * inverseRatio + a2 * ratio).toInt()
        val r = (r1 * inverseRatio + r2 * ratio).toInt()
        val g = (g1 * inverseRatio + g2 * ratio).toInt()
        val b = (b1 * inverseRatio + b2 * ratio).toInt()
        
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    
    @Suppress("DEPRECATION")
    private fun drawEndpoint(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
        style: LineStyle,
        arrowInfo: JointArrowInfo
    ) {
        linePaint.style = Paint.Style.FILL
        linePaint.color = color
        linePaint.alpha = 220
        
        val radius = when {
            arrowInfo.isError -> style.jointRadius * 1.2f
            arrowInfo.isWarning -> style.jointRadius * 1.0f
            else -> style.jointRadius * 0.8f
        }
        
        canvas.drawCircle(x, y, radius, linePaint)
        
        linePaint.style = Paint.Style.STROKE
        linePaint.alpha = 255
    }
}

/**
 * Visual style for Line Indicator using JointStateInfo
 * Uses StateConfig for state-based styling
 */
data class LineStyleNew(
    val strokeWidth: Float,
    val jointRadius: Float,
    val pulseSpeed: Long,
    val showOuterRing: Boolean
) {
    companion object {
        fun forState(stateInfo: JointStateInfo, density: Float): LineStyleNew {
            return when (stateInfo.state) {
                JointState.DANGER, JointState.WARNING -> LineStyleNew(
                    strokeWidth = SettingsManager.getLineIndicatorStrokeWidthError() * density,
                    jointRadius = SettingsManager.getLineIndicatorJointRadiusError(),
                    pulseSpeed = 800L,
                    showOuterRing = true
                )
                JointState.PAD -> LineStyleNew(
                    strokeWidth = SettingsManager.getLineIndicatorStrokeWidthWarning() * density,
                    jointRadius = SettingsManager.getLineIndicatorJointRadiusWarning(),
                    pulseSpeed = 1500L,
                    showOuterRing = false
                )
                else -> LineStyleNew(
                    strokeWidth = SettingsManager.getLineIndicatorStrokeWidthNormal() * density,
                    jointRadius = SettingsManager.getLineIndicatorJointRadiusNormal(),
                    pulseSpeed = 3000L,
                    showOuterRing = false
                )
            }
        }
    }
}

/**
 * Visual style for Line Indicator (Legacy)
 * @deprecated Use LineStyleNew instead
 */
@Suppress("DEPRECATION")
@Deprecated("Use LineStyleNew instead")
data class LineStyle(
    val strokeWidth: Float,
    val jointRadius: Float,
    val pulseSpeed: Long,
    val showOuterRing: Boolean
) {
    companion object {
        @Suppress("DEPRECATION")
        fun forState(arrowInfo: JointArrowInfo, density: Float): LineStyle {
            return when {
                arrowInfo.isError -> LineStyle(
                    strokeWidth = SettingsManager.getLineIndicatorStrokeWidthError() * density,
                    jointRadius = SettingsManager.getLineIndicatorJointRadiusError(),
                    pulseSpeed = 800L,
                    showOuterRing = true
                )
                arrowInfo.isWarning -> LineStyle(
                    strokeWidth = SettingsManager.getLineIndicatorStrokeWidthWarning() * density,
                    jointRadius = SettingsManager.getLineIndicatorJointRadiusWarning(),
                    pulseSpeed = 1500L,
                    showOuterRing = false
                )
                else -> LineStyle(
                    strokeWidth = SettingsManager.getLineIndicatorStrokeWidthNormal() * density,
                    jointRadius = SettingsManager.getLineIndicatorJointRadiusNormal(),
                    pulseSpeed = 3000L,
                    showOuterRing = false
                )
            }
        }
    }
}
