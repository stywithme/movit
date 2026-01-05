package com.trainingvalidator.poc.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.engine.JointArrowInfo
import com.trainingvalidator.poc.training.engine.JointZone
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.config.SettingsManager
import kotlin.math.max
import kotlin.math.sin

/**
 * SkeletonOverlayView - Professional skeleton drawing with zone-based color feedback
 * 
 * Minimalist Design Principles:
 * - Tracked joints only: Non-tracked joints are faint (15-20% opacity)
 * - Angle labels only on Error/Critical: No clutter in normal state
 * - Color gradient: Shows position quality (green = good, yellow/orange = warning, red = error)
 * - Flow animation: Skeleton has subtle shimmer when form is correct
 */
class SkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 10f
        private const val TRACKED_LANDMARK_STROKE_WIDTH = 14f
        private const val LINE_WIDTH = 6f
        private const val TRACKED_LINE_WIDTH = 8f
        private const val ERROR_LINE_WIDTH = 10f
        private const val TEXT_SIZE = 26f
        private const val VISIBILITY_THRESHOLD = 0.5f
        
        // Non-tracked skeleton opacity (Minimalist: faint but visible)
        private const val NON_TRACKED_OPACITY = 0.18f
        
        // Colors (Modern palette)
        private val COLOR_DEFAULT = Color.parseColor("#80FFFFFF")          // Faint white
        private val COLOR_CORRECT = Color.parseColor("#00E676")            // Green
        private val COLOR_ERROR = Color.parseColor("#FF5252")              // Red
        private val COLOR_WARNING = Color.parseColor("#FFC107")            // Amber
        private val COLOR_POSITION_ERROR = Color.parseColor("#E91E63")     // Pink
        private val COLOR_TRACKED = Color.parseColor("#64B5F6")            // Light Blue
        private val COLOR_LINE_DEFAULT = Color.parseColor("#40FFFFFF")     // Faint
        private val COLOR_LINE_TRACKED = Color.parseColor("#64B5F6")       // Light Blue
        private val COLOR_GLOW = Color.parseColor("#4000E676")             // Green glow
        
        // Gradient colors for range indicator
        private val COLOR_OPTIMAL = Color.parseColor("#00E676")            // Green - perfect position
        private val COLOR_NEAR_BOUNDARY = Color.parseColor("#FFEB3B")      // Yellow - approaching limit
        private val COLOR_BOUNDARY = Color.parseColor("#FF9800")           // Orange - at boundary
        private val COLOR_OUT_OF_RANGE = Color.parseColor("#FF5252")       // Red - outside range
        private val COLOR_TRANSITION = Color.parseColor("#81D4FA")         // Light Blue - natural movement
        
        // Color smoothing constants (prevents flickering)
        const val COLOR_LERP_FACTOR = 0.25f  // How fast to transition (0.1 = slow, 0.5 = fast)
        const val ZONE_HYSTERESIS_DEGREES = 2.0  // Dead zone to prevent flickering
    }

    // Paint objects
    private val pointPaint = Paint().apply {
        color = COLOR_DEFAULT
        strokeWidth = LANDMARK_STROKE_WIDTH
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = COLOR_LINE_DEFAULT
        strokeWidth = LINE_WIDTH
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = TEXT_SIZE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    
    // Paint for position error indicators
    private val positionErrorPaint = Paint().apply {
        color = COLOR_POSITION_ERROR
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 8f), 0f)
    }
    
    private val positionWarningPaint = Paint().apply {
        color = COLOR_WARNING
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    
    private val positionTipPaint = Paint().apply {
        color = COLOR_TRACKED
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    
    private val positionErrorFillPaint = Paint().apply {
        color = Color.argb(60, 233, 30, 99)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // Glow paint for correct form
    private val glowPaint = Paint().apply {
        color = COLOR_GLOW
        style = Paint.Style.STROKE
        strokeWidth = 20f
        isAntiAlias = true
        maskFilter = android.graphics.BlurMaskFilter(15f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    // Current pose data
    private var landmarks: List<SmoothedLandmark>? = null
    private var jointAngles: JointAngles? = null
    
    // Training data
    private var trackedLandmarkIndices: Set<Int> = emptySet()
    private var isTrainingMode: Boolean = false
    
    // Joint info for each tracked joint (calculated by FormValidator)
    private var jointArrowInfos: Map<String, JointArrowInfo> = emptyMap()
    
    // Error state - which joints have errors
    private var errorJointCodes: Set<String> = emptySet()
    
    // Position-based errors (knee-over-toe, alignment, etc.)
    private var positionErrors: List<PositionError> = emptyList()
    
    // Scaling
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f
    
    // Settings
    private var showAngles = true
    private var showAnglesOnlyOnError = true  // Only show angles on error/critical
    
    // Color smoothing state (prevents flickering)
    private val previousColors = mutableMapOf<String, Int>()
    private val previousZones = mutableMapOf<String, JointZone>()
    
    // Flow animation
    private var flowAnimator: ValueAnimator? = null
    private var flowPhase: Float = 0f
    private var isFlowAnimating = false
    
    // Arc Range Indicator
    private val arcRangeIndicator = ArcRangeIndicator()
    private var showArcIndicators = true
    private var arcConfig = ArcConfig()

    init {
        // Start flow animation
        startFlowAnimation()
        
        // Load arc settings from SettingsManager
        loadArcSettings()
    }
    
    /**
     * Load arc indicator settings from SettingsManager
     */
    private fun loadArcSettings() {
        showArcIndicators = SettingsManager.getShowArcIndicators()
        arcConfig = ArcConfig(
            radiusDp = SettingsManager.getArcIndicatorRadiusDp(),
            strokeWidthDp = SettingsManager.getArcIndicatorStrokeWidthDp(),
            showCurrentIndicator = SettingsManager.getArcShowCurrentIndicator(),
            showOnlyOnError = SettingsManager.getArcShowOnlyOnError(),
            showOnlyPrimary = SettingsManager.getArcShowOnlyPrimary(),
            arcOpacity = SettingsManager.getArcOpacity()
        )
    }
    
    /**
     * Start subtle flow animation for skeleton
     */
    private fun startFlowAnimation() {
        flowAnimator?.cancel()
        flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                flowPhase = animator.animatedValue as Float
                if (isTrainingMode && landmarks != null) {
                    invalidate()
                }
            }
        }
        // Don't start immediately - start when training begins
    }

    /**
     * Update skeleton with pose data
     */
    fun updateSkeleton(
        smoothedLandmarks: List<SmoothedLandmark>?,
        inputImageWidth: Int = 1,
        inputImageHeight: Int = 1,
        angles: JointAngles? = null
    ) {
        landmarks = smoothedLandmarks
        imageWidth = inputImageWidth
        imageHeight = inputImageHeight
        jointAngles = angles
        scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        invalidate()
    }
    
    /**
     * Update with arrow infos for direction feedback
     */
    fun updateWithArrowInfos(
        smoothedLandmarks: List<SmoothedLandmark>?,
        inputImageWidth: Int = 1,
        inputImageHeight: Int = 1,
        angles: JointAngles? = null,
        arrowInfos: Map<String, JointArrowInfo>,
        positionErrors: List<PositionError> = emptyList()
    ) {
        landmarks = smoothedLandmarks
        imageWidth = inputImageWidth
        imageHeight = inputImageHeight
        jointAngles = angles
        jointArrowInfos = arrowInfos
        this.positionErrors = positionErrors
        scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        
        // Collect error joints (TOO_HIGH or TOO_LOW zones)
        errorJointCodes = arrowInfos.filter { it.value.isError }.keys
        
        invalidate()
    }
    
    /**
     * Set training mode with tracked landmarks
     */
    fun setTrainingMode(
        enabled: Boolean, 
        trackedIndices: Set<Int> = emptySet()
    ) {
        isTrainingMode = enabled
        trackedLandmarkIndices = trackedIndices
        
        // Control flow animation
        if (enabled && !isFlowAnimating) {
            flowAnimator?.start()
            isFlowAnimating = true
        } else if (!enabled && isFlowAnimating) {
            flowAnimator?.cancel()
            isFlowAnimating = false
        }
        
        invalidate()
    }
    
    /**
     * Update arrow infos (for real-time feedback)
     */
    fun setArrowInfos(arrowInfos: Map<String, JointArrowInfo>) {
        jointArrowInfos = arrowInfos
        errorJointCodes = arrowInfos.filter { it.value.isError }.keys
        invalidate()
    }

    fun setShowAngles(show: Boolean) {
        showAngles = show
        invalidate()
    }
    
    fun setShowLowVisibility(show: Boolean) {}
    
    /**
     * Enable/disable arc range indicators around joints
     */
    fun setShowArcIndicators(show: Boolean) {
        showArcIndicators = show
        invalidate()
    }
    
    /**
     * Set arc indicator configuration
     */
    fun setArcConfig(config: ArcConfig) {
        arcConfig = config
        invalidate()
    }

    fun clear() {
        landmarks = null
        jointAngles = null
        jointArrowInfos = emptyMap()
        errorJointCodes = emptySet()
        positionErrors = emptyList()
        
        // Reset color smoothing state
        previousColors.clear()
        previousZones.clear()
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentLandmarks = landmarks ?: return
        if (currentLandmarks.isEmpty()) return

        // Draw connections first (behind points)
        drawConnections(canvas, currentLandmarks)
        
        // Draw glow on tracked joints when correct (no errors)
        if (isTrainingMode && errorJointCodes.isEmpty()) {
            drawCorrectFormGlow(canvas, currentLandmarks)
        }
        
        // Draw landmark points
        drawLandmarks(canvas, currentLandmarks)
        
        // Draw Arc Range Indicators for tracked joints
        if (isTrainingMode && showArcIndicators && jointArrowInfos.isNotEmpty()) {
            drawArcRangeIndicators(canvas, currentLandmarks)
        }
        
        // Draw position errors (knee-over-toe, alignment, etc.)
        if (isTrainingMode && positionErrors.isNotEmpty()) {
            drawPositionErrors(canvas, currentLandmarks)
        }
        
        // Draw angles if enabled (only on error when showAnglesOnlyOnError)
        if (showAngles) {
            drawAngles(canvas, currentLandmarks)
        }
    }

    private fun drawConnections(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
            if (connection != null) {
                val startIdx = connection.start()
                val endIdx = connection.end()
                
                if (startIdx < landmarks.size && endIdx < landmarks.size) {
                    val start = landmarks[startIdx]
                    val end = landmarks[endIdx]
                    
                    if (start.visibility >= VISIBILITY_THRESHOLD && 
                        end.visibility >= VISIBILITY_THRESHOLD) {
                        
                        // Check if this connection is part of a tracked joint
                        val startJointCode = landmarkIndexToJointCode(startIdx)
                        val endJointCode = landmarkIndexToJointCode(endIdx)
                        
                        // Find the relevant joint for this connection (if any)
                        val relevantJointCode = when {
                            startJointCode in jointArrowInfos -> startJointCode
                            endJointCode in jointArrowInfos -> endJointCode
                            else -> null
                        }
                        
                        // Check if tracked
                        val isTrackedConnection = isTrainingMode && 
                            (startIdx in trackedLandmarkIndices || endIdx in trackedLandmarkIndices)
                        
                        // Get gradient color for tracked joints
                        val gradientColor = relevantJointCode?.let { 
                            jointArrowInfos[it]?.let { info -> 
                                getGradientColorForPosition(info) 
                            }
                        }
                        
                        // Set color, width, and opacity based on state
                        val (color, width, alpha) = when {
                            // Use gradient color for tracked joints
                            gradientColor != null -> Triple(gradientColor, TRACKED_LINE_WIDTH, 1f)
                            isTrackedConnection -> Triple(COLOR_LINE_TRACKED, TRACKED_LINE_WIDTH, 1f)
                            isTrainingMode -> Triple(COLOR_LINE_DEFAULT, LINE_WIDTH, NON_TRACKED_OPACITY)
                            else -> Triple(COLOR_LINE_DEFAULT, LINE_WIDTH, 0.6f)
                        }
                        
                        linePaint.color = color
                        linePaint.strokeWidth = width
                        linePaint.alpha = (alpha * 255).toInt()
                        
                        canvas.drawLine(
                            start.x * imageWidth * scaleFactor,
                            start.y * imageHeight * scaleFactor,
                            end.x * imageWidth * scaleFactor,
                            end.y * imageHeight * scaleFactor,
                            linePaint
                        )
                        
                        // Reset alpha
                        linePaint.alpha = 255
                    }
                }
            }
        }
    }
    
    /**
     * Calculate gradient color based on angle position with SMOOTHING
     * 
     * IMPROVEMENTS:
     * 1. Color smoothing: lerp between old and new color to prevent flickering
     * 2. Unified gradient: smooth transitions across all zones
     * 3. Uses buffered ranges for consistency with zone detection
     * 
     * Color mapping (unified gradient):
     * - TOO_LOW (error): Red
     * - DOWN_ZONE edge: Orange → Yellow → Green (center) → Yellow → Orange
     * - TRANSITION: Blend between adjacent zones (not a hard jump)
     * - UP_ZONE edge: Orange → Yellow → Green (center) → Yellow → Orange
     * - TOO_HIGH (error): Red
     */
    private fun getGradientColorForPosition(arrowInfo: JointArrowInfo): Int {
        val jointCode = arrowInfo.jointCode
        val currentAngle = arrowInfo.currentAngle
        
        // Calculate target color based on position
        val targetColor = calculateTargetColor(arrowInfo, currentAngle)
        
        // Apply color smoothing (lerp with previous color)
        val previousColor = previousColors[jointCode]
        val smoothedColor = if (previousColor != null) {
            smoothInterpolateColor(previousColor, targetColor, COLOR_LERP_FACTOR)
        } else {
            targetColor
        }
        
        // Store for next frame
        previousColors[jointCode] = smoothedColor
        
        return smoothedColor
    }
    
    /**
     * Calculate target color based on position (before smoothing)
     */
    private fun calculateTargetColor(arrowInfo: JointArrowInfo, currentAngle: Double): Int {
        // Define the full range from TOO_LOW to TOO_HIGH
        val downMin = arrowInfo.downRangeMin
        val downMax = arrowInfo.downRangeMax
        val upMin = arrowInfo.upRangeMin
        val upMax = arrowInfo.upRangeMax
        
        // Handle errors first
        if (arrowInfo.isError) {
            val distanceOutside = when (arrowInfo.zone) {
                JointZone.TOO_HIGH -> currentAngle - upMax
                JointZone.TOO_LOW -> downMin - currentAngle
                else -> 0.0
            }
            // Quick ramp to red (within 10°)
            val errorRatio = (distanceOutside / 10.0).coerceIn(0.0, 1.0)
            return interpolateColor(COLOR_BOUNDARY, COLOR_OUT_OF_RANGE, errorRatio.toFloat())
        }
        
        // For valid zones, use unified gradient approach
        return when (arrowInfo.zone) {
            JointZone.DOWN_ZONE -> {
                // Calculate position within DOWN_ZONE
                val rangeSize = downMax - downMin
                if (rangeSize <= 0) return COLOR_OPTIMAL
                
                val center = (downMin + downMax) / 2
                val distFromCenter = kotlin.math.abs(currentAngle - center)
                val normalizedDist = (distFromCenter / (rangeSize / 2)).coerceIn(0.0, 1.0)
                
                getColorForNormalizedPosition(normalizedDist)
            }
            
            JointZone.UP_ZONE -> {
                // Calculate position within UP_ZONE
                val rangeSize = upMax - upMin
                if (rangeSize <= 0) return COLOR_OPTIMAL
                
                val center = (upMin + upMax) / 2
                val distFromCenter = kotlin.math.abs(currentAngle - center)
                val normalizedDist = (distFromCenter / (rangeSize / 2)).coerceIn(0.0, 1.0)
                
                getColorForNormalizedPosition(normalizedDist)
            }
            
            JointZone.TRANSITION -> {
                // TRANSITION: blend based on position between zones
                // Closer to DOWN_ZONE → more green, closer to UP_ZONE → more green
                // In the middle → neutral blue-green blend
                val transitionSize = upMin - downMax
                if (transitionSize <= 0) return COLOR_TRANSITION
                
                val posInTransition = (currentAngle - downMax) / transitionSize
                
                // Near edges of transition (close to valid zones) → greenish
                // In middle of transition → bluish
                val distFromEdge = kotlin.math.min(posInTransition, 1.0 - posInTransition) * 2
                
                // Blend between transition blue and optimal green based on edge proximity
                interpolateColor(COLOR_OPTIMAL, COLOR_TRANSITION, distFromEdge.toFloat())
            }
            
            else -> COLOR_OPTIMAL
        }
    }
    
    /**
     * Get color for normalized position (0 = center, 1 = edge)
     */
    private fun getColorForNormalizedPosition(normalizedDist: Double): Int {
        return when {
            normalizedDist < 0.4 -> COLOR_OPTIMAL  // Center 40% = pure green
            normalizedDist < 0.7 -> {
                // 40-70% = green → yellow
                val t = ((normalizedDist - 0.4) / 0.3).toFloat()
                interpolateColor(COLOR_OPTIMAL, COLOR_NEAR_BOUNDARY, t)
            }
            else -> {
                // 70-100% = yellow → orange
                val t = ((normalizedDist - 0.7) / 0.3).toFloat()
                interpolateColor(COLOR_NEAR_BOUNDARY, COLOR_BOUNDARY, t)
            }
        }
    }
    
    /**
     * Smooth interpolation between colors (for animation)
     */
    private fun smoothInterpolateColor(from: Int, to: Int, factor: Float): Int {
        return interpolateColor(from, to, factor)
    }
    
    /**
     * Interpolate between two colors
     */
    private fun interpolateColor(colorA: Int, colorB: Int, ratio: Float): Int {
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

    private fun drawLandmarks(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        landmarks.forEachIndexed { index, landmark ->
            if (landmark.visibility >= VISIBILITY_THRESHOLD) {
                val x = landmark.x * imageWidth * scaleFactor
                val y = landmark.y * imageHeight * scaleFactor
                
                val jointCode = landmarkIndexToJointCode(index)
                val isTracked = index in trackedLandmarkIndices
                
                // Get gradient color for tracked joints
                val gradientColor = jointCode?.let { 
                    jointArrowInfos[it]?.let { info -> 
                        getGradientColorForPosition(info) 
                    }
                }
                
                // Determine appearance based on state
                val (color, radius, alpha) = when {
                    // Use gradient color for tracked joints
                    gradientColor != null -> Triple(gradientColor, TRACKED_LANDMARK_STROKE_WIDTH / 2, 1f)
                    isTracked -> Triple(COLOR_TRACKED, TRACKED_LANDMARK_STROKE_WIDTH / 2, 1f)
                    isTrainingMode -> Triple(COLOR_DEFAULT, LANDMARK_STROKE_WIDTH / 2, NON_TRACKED_OPACITY)
                    else -> Triple(COLOR_DEFAULT, LANDMARK_STROKE_WIDTH / 2, 0.6f)
                }
                
                pointPaint.color = color
                pointPaint.alpha = (alpha * 255).toInt()
                canvas.drawCircle(x, y, radius, pointPaint)
                pointPaint.alpha = 255
                
                // Draw outer ring for tracked joints
                if (isTrainingMode && isTracked) {
                    linePaint.style = Paint.Style.STROKE
                    linePaint.strokeWidth = 2f
                    linePaint.color = color
                    canvas.drawCircle(x, y, radius + 4f, linePaint)
                    linePaint.style = Paint.Style.STROKE
                }
            }
        }
    }
    
    /**
     * Draw subtle glow effect on tracked joints when form is correct
     */
    private fun drawCorrectFormGlow(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        // Animate glow intensity
        val glowIntensity = 0.3f + 0.4f * sin(flowPhase * Math.PI * 2).toFloat()
        glowPaint.alpha = (glowIntensity * 255).toInt()
        
        for (index in trackedLandmarkIndices) {
            if (index < landmarks.size) {
                val landmark = landmarks[index]
                if (landmark.visibility >= VISIBILITY_THRESHOLD) {
                    val x = landmark.x * imageWidth * scaleFactor
                    val y = landmark.y * imageHeight * scaleFactor
                    
                    canvas.drawCircle(x, y, 25f, glowPaint)
                }
            }
        }
    }
    
    /**
     * Convert landmark index to joint code
     */
    private fun landmarkIndexToJointCode(index: Int): String? {
        return when (index) {
            11 -> "left_shoulder"
            12 -> "right_shoulder"
            13 -> "left_elbow"
            14 -> "right_elbow"
            15 -> "left_wrist"
            16 -> "right_wrist"
            23 -> "left_hip"
            24 -> "right_hip"
            25 -> "left_knee"
            26 -> "right_knee"
            27 -> "left_ankle"
            28 -> "right_ankle"
            else -> null
        }
    }
    
    /**
     * Convert joint code to landmark index
     */
    private fun jointCodeToLandmarkIndex(jointCode: String): Int? {
        return when (jointCode) {
            "left_shoulder" -> 11
            "right_shoulder" -> 12
            "left_elbow" -> 13
            "right_elbow" -> 14
            "left_wrist" -> 15
            "right_wrist" -> 16
            "left_hip" -> 23
            "right_hip" -> 24
            "left_knee" -> 25
            "right_knee" -> 26
            "left_ankle" -> 27
            "right_ankle" -> 28
            else -> null
        }
    }
    
    // ==================== Arc Range Indicator Drawing ====================
    
    /**
     * Draw Arc Range Indicators for all tracked joints
     * 
     * Shows a gradient arc around each tracked joint indicating:
     * - Valid UP range (green at center, orange at edges)
     * - Valid DOWN range (green at center, orange at edges)
     * - Transition zone (blue)
     * - Error zones (red)
     * - Current position indicator (dot with glow)
     */
    private fun drawArcRangeIndicators(
        canvas: Canvas,
        landmarks: List<SmoothedLandmark>
    ) {
        val density = resources.displayMetrics.density
        
        for ((jointCode, arrowInfo) in jointArrowInfos) {
            // Get landmark index for this joint
            val landmarkIndex = jointCodeToLandmarkIndex(jointCode) ?: continue
            if (landmarkIndex >= landmarks.size) continue
            
            val landmark = landmarks[landmarkIndex]
            if (landmark.visibility < VISIBILITY_THRESHOLD) continue
            
            // Calculate center position in screen coordinates
            val centerX = landmark.x * imageWidth * scaleFactor
            val centerY = landmark.y * imageHeight * scaleFactor
            
            // Create arc data from arrow info
            val arcData = ArcRangeData.fromArrowInfo(
                jointCode = jointCode,
                centerX = centerX,
                centerY = centerY,
                arrowInfo = arrowInfo
            )
            
            // Check if arc should be shown based on config
            if (!arcRangeIndicator.shouldShowArc(arcData, arcConfig)) {
                continue
            }
            
            // Draw the arc
            arcRangeIndicator.draw(canvas, arcData, arcConfig, density)
        }
    }

    /**
     * Draw angles - DATA-DRIVEN from jointArrowInfos (tracked joints only)
     * 
     * Only shows angles for:
     * - Tracked joints (from exercise config)
     * - When in error or warning state (minimalist principle)
     */
    private fun drawAngles(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        val angles = jointAngles ?: return
        
        // DATA-DRIVEN: Use jointArrowInfos which comes from tracked joints
        // This ensures we only show angles for joints that are actually tracked
        for ((jointCode, arrowInfo) in jointArrowInfos) {
            val index = jointCodeToLandmarkIndex(jointCode) ?: continue
            
            // Get the actual angle value from JointAngles
            val angle = getAngleForJointCode(angles, jointCode) ?: continue
            
            // MINIMALIST: Only show angles on error or warning
            if (showAnglesOnlyOnError && !arrowInfo.isError && !arrowInfo.isWarning) continue
            
            drawAngleAt(canvas, landmarks, index, angle, jointCode, arrowInfo.isWarning)
        }
    }
    
    /**
     * Get angle value for a joint code from JointAngles
     */
    private fun getAngleForJointCode(angles: JointAngles, jointCode: String): Double? {
        return when (jointCode) {
            "left_elbow" -> angles.leftElbow
            "right_elbow" -> angles.rightElbow
            "left_shoulder" -> angles.leftShoulder
            "right_shoulder" -> angles.rightShoulder
            "left_hip" -> angles.leftHip
            "right_hip" -> angles.rightHip
            "left_knee" -> angles.leftKnee
            "right_knee" -> angles.rightKnee
            "left_ankle" -> angles.leftAnkle
            "right_ankle" -> angles.rightAnkle
            else -> null
        }
    }
    
    private fun drawAngleAt(
        canvas: Canvas, 
        landmarks: List<SmoothedLandmark>, 
        index: Int, 
        angle: Double?,
        jointCode: String,
        isWarning: Boolean = false
    ) {
        if (angle == null || index >= landmarks.size) return
        val landmark = landmarks[index]
        if (landmark.visibility < 0.3f) return
        
        val x = landmark.x * imageWidth * scaleFactor
        val y = landmark.y * imageHeight * scaleFactor - 25f
        
        val hasError = jointCode in errorJointCodes
        
        // Color: Error (red) > Warning (amber) > Default (white)
        textPaint.color = when {
            hasError -> COLOR_ERROR
            isWarning -> COLOR_WARNING
            else -> Color.WHITE
        }
        
        canvas.drawText("%.0f°".format(angle), x, y, textPaint)
        textPaint.color = Color.WHITE
    }
    
    // ==================== Position Error Drawing ====================
    
    /**
     * Draw visual indicators for position errors with severity distinction
     */
    private fun drawPositionErrors(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        for (error in positionErrors) {
            val landmark1Idx = landmarkNameToIndex(error.landmark1)
            val landmark2Idx = landmarkNameToIndex(error.landmark2)
            
            if (landmark1Idx != null && landmark2Idx != null &&
                landmark1Idx < landmarks.size && landmark2Idx < landmarks.size) {
                
                val lm1 = landmarks[landmark1Idx]
                val lm2 = landmarks[landmark2Idx]
                
                if (lm1.visibility >= VISIBILITY_THRESHOLD && 
                    lm2.visibility >= VISIBILITY_THRESHOLD) {
                    
                    val x1 = lm1.x * imageWidth * scaleFactor
                    val y1 = lm1.y * imageHeight * scaleFactor
                    val x2 = lm2.x * imageWidth * scaleFactor
                    val y2 = lm2.y * imageHeight * scaleFactor
                    
                    // Choose paint based on severity (DISTINCT visuals per level)
                    val (linePaintToUse, circleRadius) = when (error.severity) {
                        CheckSeverity.ERROR -> positionErrorPaint to 22f
                        CheckSeverity.WARNING -> positionWarningPaint to 18f
                        CheckSeverity.TIP -> positionTipPaint to 14f
                    }
                    
                    // Draw dashed line between the two landmarks
                    canvas.drawLine(x1, y1, x2, y2, linePaintToUse)
                    
                    // Draw circles at both landmarks to highlight them
                    canvas.drawCircle(x1, y1, circleRadius, linePaintToUse)
                    canvas.drawCircle(x2, y2, circleRadius, linePaintToUse)
                    
                    // For ERROR severity, also draw a filled circle with transparency
                    if (error.severity == CheckSeverity.ERROR) {
                        canvas.drawCircle(x1, y1, circleRadius - 5f, positionErrorFillPaint)
                        canvas.drawCircle(x2, y2, circleRadius - 5f, positionErrorFillPaint)
                    }
                }
            }
        }
    }
    
    /**
     * Convert landmark name to MediaPipe landmark index
     */
    private fun landmarkNameToIndex(name: String): Int? {
        return when (name.lowercase()) {
            "nose" -> 0
            "left_eye_inner" -> 1
            "left_eye" -> 2
            "left_eye_outer" -> 3
            "right_eye_inner" -> 4
            "right_eye" -> 5
            "right_eye_outer" -> 6
            "left_ear" -> 7
            "right_ear" -> 8
            "mouth_left" -> 9
            "mouth_right" -> 10
            "left_shoulder" -> 11
            "right_shoulder" -> 12
            "left_elbow" -> 13
            "right_elbow" -> 14
            "left_wrist" -> 15
            "right_wrist" -> 16
            "left_pinky" -> 17
            "right_pinky" -> 18
            "left_index" -> 19
            "right_index" -> 20
            "left_thumb" -> 21
            "right_thumb" -> 22
            "left_hip" -> 23
            "right_hip" -> 24
            "left_knee" -> 25
            "right_knee" -> 26
            "left_ankle" -> 27
            "right_ankle" -> 28
            "left_heel" -> 29
            "right_heel" -> 30
            "left_foot_index", "left_toe" -> 31
            "right_foot_index", "right_toe" -> 32
            else -> null
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flowAnimator?.cancel()
    }
}
