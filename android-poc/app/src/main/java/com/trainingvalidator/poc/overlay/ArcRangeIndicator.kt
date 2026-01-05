package com.trainingvalidator.poc.overlay

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Cap
import android.graphics.RectF
import com.trainingvalidator.poc.training.engine.JointZone

/**
 * ArcRangeIndicator - Draws segmented arc showing valid angle ranges
 * 
 * Visual representation of movement range around tracked joints.
 * 
 * Drawing Strategy:
 * Uses segment-based drawing instead of SweepGradient for precise color mapping:
 * - Each zone (TOO_LOW, DOWN_ZONE, TRANSITION, UP_ZONE, TOO_HIGH) drawn separately
 * - Gradient within UP/DOWN zones for smooth edge-to-center transitions
 * - Better performance and accuracy than full SweepGradient
 * 
 * Arc Orientation:
 * - Joint 0° at bottom (Canvas 90°)
 * - Joint 180° at top (Canvas -90°)
 * - Arc spans right half-circle
 * 
 * Usage:
 * ```kotlin
 * val indicator = ArcRangeIndicator()
 * indicator.draw(canvas, arcData, config, density)
 * ```
 */
class ArcRangeIndicator {
    
    companion object {
        private const val INDICATOR_RADIUS_MULTIPLIER = 1.0f
        private const val GLOW_RADIUS_MULTIPLIER = 2.0f
        private const val LINE_ALPHA = 150
        private const val GLOW_ALPHA_NORMAL = 100
        private const val GLOW_ALPHA_ERROR = 150
    }
    
    // ==================== Paint Objects (reused for performance) ====================
    
    private val segmentPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Cap.ROUND
    }
    
    private val indicatorPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Cap.ROUND
    }
    
    // Reusable RectF to avoid allocations
    private val arcRect = RectF()
    
    // ==================== Public API ====================
    
    /**
     * Draw arc range indicator for a joint
     * 
     * @param canvas Canvas to draw on
     * @param data Arc range data containing position and angle info
     * @param config Visual configuration
     * @param density Screen density for dp to px conversion
     */
    fun draw(
        canvas: Canvas,
        data: ArcRangeData,
        config: ArcConfig,
        density: Float
    ) {
        // Convert dp to px
        val radius = config.radiusDp * density
        val strokeWidth = config.strokeWidthDp * density
        
        // Calculate bounding rect for arc
        arcRect.set(
            data.centerX - radius,
            data.centerY - radius,
            data.centerX + radius,
            data.centerY + radius
        )
        
        // Apply opacity
        val alpha = (config.arcOpacity * 255).toInt()
        
        // 1. Draw arc segments (zones)
        drawArcSegments(canvas, data, strokeWidth, alpha)
        
        // 2. Draw current position indicator
        if (config.showCurrentIndicator) {
            drawCurrentIndicator(canvas, data, radius, config.indicatorRadiusDp * density)
        }
    }
    
    // ==================== Arc Segment Drawing ====================
    
    /**
     * Draw arc as segments for each zone
     * More accurate than SweepGradient for half-circle
     */
    private fun drawArcSegments(
        canvas: Canvas,
        data: ArcRangeData,
        strokeWidth: Float,
        alpha: Int
    ) {
        segmentPaint.strokeWidth = strokeWidth
        
        // Draw segments in order from 0° to 180° (joint angles)
        
        // 1. TOO_LOW zone (0° to downRangeMin)
        if (data.downRangeMin > 0) {
            drawSolidSegment(
                canvas, data,
                0.0, data.downRangeMin,
                JointZone.TOO_LOW, alpha
            )
        }
        
        // 2. DOWN_ZONE (downRangeMin to downRangeMax) - with gradient
        if (data.downRangeMax > data.downRangeMin) {
            drawGradientSegment(
                canvas, data,
                data.downRangeMin, data.downRangeMax,
                ArcColorCalculator.getZoneColor(JointZone.DOWN_ZONE),
                strokeWidth, alpha
            )
        }
        
        // 3. TRANSITION zone (downRangeMax to upRangeMin)
        if (data.upRangeMin > data.downRangeMax) {
            drawSolidSegment(
                canvas, data,
                data.downRangeMax, data.upRangeMin,
                JointZone.TRANSITION, alpha
            )
        }
        
        // 4. UP_ZONE (upRangeMin to upRangeMax) - with gradient
        if (data.upRangeMax > data.upRangeMin) {
            drawGradientSegment(
                canvas, data,
                data.upRangeMin, data.upRangeMax,
                ArcColorCalculator.getZoneColor(JointZone.UP_ZONE),
                strokeWidth, alpha
            )
        }
        
        // 5. TOO_HIGH zone (upRangeMax to 180°)
        if (data.upRangeMax < 180.0) {
            drawSolidSegment(
                canvas, data,
                data.upRangeMax, 180.0,
                JointZone.TOO_HIGH, alpha
            )
        }
    }
    
    /**
     * Draw a solid color segment
     */
    private fun drawSolidSegment(
        canvas: Canvas,
        data: ArcRangeData,
        startJointAngle: Double,
        endJointAngle: Double,
        zone: JointZone,
        alpha: Int
    ) {
        val startCanvas = data.toCanvasAngle(startJointAngle)
        val endCanvas = data.toCanvasAngle(endJointAngle)
        val sweepAngle = endCanvas - startCanvas
        
        // Skip if sweep is too small
        if (kotlin.math.abs(sweepAngle) < 0.5f) return
        
        segmentPaint.shader = null
        segmentPaint.color = ArcColorCalculator.withAlpha(
            ArcColorCalculator.getZoneColor(zone),
            alpha
        )
        
        canvas.drawArc(arcRect, startCanvas, sweepAngle, false, segmentPaint)
    }
    
    /**
     * Draw a segment with dynamic gradient based on actual range
     * 
     * Uses Engine-compatible zone logic:
     * - Determines color based on distance from center using same thresholds as Engine
     * - Center of zone: Green (optimal - like CORRECT in Engine)
     * - Approaching edges: Yellow (like isWarning in Engine)
     * - At edges: Orange (boundary - near error threshold)
     * 
     * The gradient adapts dynamically to the actual range size of each exercise
     */
    private fun drawGradientSegment(
        canvas: Canvas,
        data: ArcRangeData,
        rangeMin: Double,
        rangeMax: Double,
        centerColor: Int,
        strokeWidth: Float,
        alpha: Int
    ) {
        val totalRange = rangeMax - rangeMin
        
        // Skip if range is too small
        if (totalRange < 1.0) {
            // Draw as solid optimal color if too small for gradient
            drawSubSegment(canvas, data, rangeMin, rangeMax, centerColor, strokeWidth, alpha)
            return
        }
        
        // Determine step size based on range (smaller ranges = finer steps)
        // Use 2° steps for smooth gradient, but ensure at least 3 segments
        val stepSize = minOf(2.0, totalRange / 3.0)
        
        var currentAngle = rangeMin
        while (currentAngle < rangeMax) {
            val nextAngle = minOf(currentAngle + stepSize, rangeMax)
            val midAngle = (currentAngle + nextAngle) / 2
            
            // Calculate color based on position in range (uses Engine-compatible logic)
            val color = ArcColorCalculator.getColorForAngleInRange(midAngle, rangeMin, rangeMax)
            
            drawSubSegment(canvas, data, currentAngle, nextAngle, color, strokeWidth, alpha)
            
            currentAngle = nextAngle
        }
    }
    
    /**
     * Draw a single sub-segment with solid color
     */
    private fun drawSubSegment(
        canvas: Canvas,
        data: ArcRangeData,
        startJointAngle: Double,
        endJointAngle: Double,
        color: Int,
        strokeWidth: Float,
        alpha: Int
    ) {
        val startCanvas = data.toCanvasAngle(startJointAngle)
        val endCanvas = data.toCanvasAngle(endJointAngle)
        val sweepAngle = endCanvas - startCanvas
        
        // Skip if sweep is too small
        if (kotlin.math.abs(sweepAngle) < 0.1f) return
        
        segmentPaint.shader = null
        segmentPaint.strokeWidth = strokeWidth
        segmentPaint.color = ArcColorCalculator.withAlpha(color, alpha)
        
        canvas.drawArc(arcRect, startCanvas, sweepAngle, false, segmentPaint)
    }
    
    // ==================== Current Position Indicator ====================
    
    /**
     * Draw indicator for current angle position
     * 
     * Shows:
     * - Glow effect (larger, semi-transparent)
     * - Indicator dot (solid, colored based on position)
     * - Line from center to indicator
     */
    private fun drawCurrentIndicator(
        canvas: Canvas,
        data: ArcRangeData,
        arcRadius: Float,
        indicatorRadius: Float
    ) {
        // Convert joint angle to canvas angle
        val canvasAngle = data.toCanvasAngle(data.currentAngle)
        val angleRad = Math.toRadians(canvasAngle.toDouble())
        
        // Calculate indicator position on arc
        val indicatorX = data.centerX + (arcRadius * kotlin.math.cos(angleRad)).toFloat()
        val indicatorY = data.centerY + (arcRadius * kotlin.math.sin(angleRad)).toFloat()
        
        // Get color based on current position
        val indicatorColor = ArcColorCalculator.getColorForAngle(
            data.currentAngle,
            data.upRangeMin,
            data.upRangeMax,
            data.downRangeMin,
            data.downRangeMax
        )
        
        val glowRadius = indicatorRadius * GLOW_RADIUS_MULTIPLIER
        val glowAlpha = if (data.isError || data.isWarning) GLOW_ALPHA_ERROR else GLOW_ALPHA_NORMAL
        
        // 1. Draw glow effect
        glowPaint.color = indicatorColor
        glowPaint.alpha = glowAlpha
        glowPaint.maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius * GLOW_RADIUS_MULTIPLIER, glowPaint)
        glowPaint.maskFilter = null
        
        // 2. Draw indicator dot
        indicatorPaint.color = indicatorColor
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius, indicatorPaint)
        
        // 3. Draw line from center to indicator
        linePaint.color = indicatorColor
        linePaint.alpha = LINE_ALPHA
        linePaint.strokeWidth = indicatorRadius * 0.4f
        canvas.drawLine(data.centerX, data.centerY, indicatorX, indicatorY, linePaint)
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Check if a joint should show arc indicator based on config
     * 
     * @param data Arc data for the joint
     * @param config Configuration
     * @return True if arc should be shown
     */
    fun shouldShowArc(data: ArcRangeData, config: ArcConfig): Boolean {
        // Filter by primary only
        if (config.showOnlyPrimary && !data.isPrimary) {
            return false
        }
        
        // Filter by error only
        if (config.showOnlyOnError && !data.isError && !data.isWarning) {
            return false
        }
        
        return true
    }
}
