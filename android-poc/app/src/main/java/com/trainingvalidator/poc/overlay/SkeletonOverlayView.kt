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
import com.trainingvalidator.poc.pose.BodyLandmarks
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.JointArrowInfo
import com.trainingvalidator.poc.training.engine.JointZone
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.models.StateConfig
import com.trainingvalidator.poc.training.models.ZoneType
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
        
        // Default visibility threshold (can be overridden by settings)
        private const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f
        
        // UI-specific colors (not from StateConfig)
        private val COLOR_DEFAULT = Color.parseColor("#80FFFFFF")          // Faint white (non-tracked)
        private val COLOR_POSITION_ERROR = Color.parseColor("#E91E63")     // Pink (position checks)
        private val COLOR_TRACKED = Color.parseColor("#64B5F6")            // Light Blue (tracked default)
        private val COLOR_LINE_DEFAULT = Color.parseColor("#40FFFFFF")     // Faint line
        private val COLOR_LINE_TRACKED = Color.parseColor("#64B5F6")       // Light Blue line
        private val COLOR_GLOW = Color.parseColor("#4000E676")             // Green glow
        
        // Color smoothing constants (prevents flickering)
        const val COLOR_LERP_FACTOR = 0.25f  // How fast to transition (0.1 = slow, 0.5 = fast)
    }
    
    // ==================== Settings-based values ====================
    
    // Visibility threshold from settings
    private val visibilityThreshold: Float
        get() = if (SettingsManager.isLoaded) SettingsManager.getOverlayVisibility() 
                else DEFAULT_VISIBILITY_THRESHOLD
    
    // Opacity values from settings
    private val nonTrackedOpacity: Float
        get() = if (SettingsManager.isLoaded) SettingsManager.getNonTrackedOpacity() else 0.18f
    
    private val trackedOpacityCorrect: Float
        get() = if (SettingsManager.isLoaded) SettingsManager.getTrackedCorrectOpacity() else 0.50f
    
    private val trackedOpacityError: Float
        get() = if (SettingsManager.isLoaded) SettingsManager.getTrackedErrorOpacity() else 0.75f

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
        color = StateConfig.getColor(JointState.WARNING)
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
    private var rawTrackedLandmarkIndices: Set<Int> = emptySet()  // Original (non-mirrored) indices
    private var isTrainingMode: Boolean = false
    private var isFrontCamera: Boolean = false  // For mirroring tracked indices
    
    // Joint state info for each tracked joint (calculated by FormValidator)
    // NEW: Uses JointStateInfo from unified state system
    private var jointStateInfos: Map<String, JointStateInfo> = emptyMap()
    
    // Legacy: kept for backward compatibility during migration
    @Deprecated("Use jointStateInfos instead")
    private var jointArrowInfos: Map<String, JointArrowInfo> = emptyMap()
    
    // Error state - which joints have errors (DANGER or WARNING)
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
    private val previousStates = mutableMapOf<String, JointState>()
    
    // Flow animation
    private var flowAnimator: ValueAnimator? = null
    private var flowPhase: Float = 0f
    private var isFlowAnimating = false

    
    // Visual Indicators
    private val lineRangeIndicator = LineRangeIndicator()
    private val arcRangeIndicator = ArcRangeIndicator()
    private var arcConfig = ArcConfig()
    
    // Indicator type setting (read from SettingsManager)
    private var useArcIndicator: Boolean = false
    private var showIndicators = true

    init {
        // Start flow animation
        startFlowAnimation()
        
        // Read indicator type from settings
        useArcIndicator = SettingsManager.useArcIndicator()
        showIndicators = true
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
     * Update with state infos for visual feedback
     * NEW: Uses JointStateInfo from unified state system
     */
    fun updateWithStateInfos(
        smoothedLandmarks: List<SmoothedLandmark>?,
        inputImageWidth: Int = 1,
        inputImageHeight: Int = 1,
        angles: JointAngles? = null,
        stateInfos: Map<String, JointStateInfo>,
        positionErrors: List<PositionError> = emptyList()
    ) {
        landmarks = smoothedLandmarks
        imageWidth = inputImageWidth
        imageHeight = inputImageHeight
        jointAngles = angles
        jointStateInfos = stateInfos
        this.positionErrors = positionErrors
        scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        
        // Collect error joints (DANGER or WARNING states)
        errorJointCodes = stateInfos.filter { 
            it.value.state == JointState.DANGER || it.value.state == JointState.WARNING 
        }.keys
        
        invalidate()
    }
    
    /**
     * Update with arrow infos for direction feedback
     * @deprecated Use updateWithStateInfos() instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use updateWithStateInfos() instead")
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
     * 
     * @param enabled Whether training mode is enabled
     * @param trackedIndices Set of landmark indices to track (from exercise config)
     * @param useFrontCamera Whether using front camera (for mirroring indices)
     */
    fun setTrainingMode(
        enabled: Boolean, 
        trackedIndices: Set<Int> = emptySet(),
        useFrontCamera: Boolean = false
    ) {
        isTrainingMode = enabled
        isFrontCamera = useFrontCamera
        rawTrackedLandmarkIndices = trackedIndices
        
        // For front camera, mirror the tracked indices since the image is mirrored
        // e.g., if tracking right_elbow (14), we need to highlight left_elbow (13) in the image
        trackedLandmarkIndices = if (useFrontCamera) {
            trackedIndices.map { BodyLandmarks.getMirroredIndex(it) }.toSet()
        } else {
            trackedIndices
        }
        
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
     * Update state infos (for real-time feedback)
     * NEW: Uses JointStateInfo from unified state system
     */
    fun setStateInfos(stateInfos: Map<String, JointStateInfo>) {
        jointStateInfos = stateInfos
        errorJointCodes = stateInfos.filter { 
            it.value.state == JointState.DANGER || it.value.state == JointState.WARNING 
        }.keys
        invalidate()
    }
    
    /**
     * Update arrow infos (for real-time feedback)
     * @deprecated Use setStateInfos() instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use setStateInfos() instead")
    fun setArrowInfos(arrowInfos: Map<String, JointArrowInfo>) {
        jointArrowInfos = arrowInfos
        errorJointCodes = arrowInfos.filter { it.value.isError }.keys
        invalidate()
    }

    fun setShowAngles(show: Boolean) {
        showAngles = show
        invalidate()
    }
    
    // NOTE: Removed empty setShowLowVisibility() - was unused/unimplemented
    
    /**
     * Enable/disable range indicators (both Arc and Line)
     */
    fun setShowIndicators(show: Boolean) {
        showIndicators = show
        invalidate()
    }
    
    /**
     * Set indicator type: "arc" or "line"
     */
    fun setIndicatorType(type: String) {
        useArcIndicator = type.equals("arc", ignoreCase = true)
        invalidate()
    }
    
    /**
     * Switch to Arc indicator
     */
    fun useArcIndicator() {
        useArcIndicator = true
        invalidate()
    }
    
    /**
     * Switch to Line indicator
     */
    fun useLineIndicator() {
        useArcIndicator = false
        invalidate()
    }
    
    /**
     * Enable/disable line range indicators on limb segments
     * @deprecated Use setShowIndicators() and setIndicatorType() instead
     */
    @Deprecated("Use setShowIndicators() and setIndicatorType() instead")
    fun setShowLineIndicators(show: Boolean) {
        showIndicators = show
        invalidate()
    }
    
    /**
     * Enable/disable arc range indicators around joints
     * @deprecated Use setShowIndicators() and setIndicatorType() instead
     */
    @Deprecated("Use setShowIndicators() and setIndicatorType() instead")
    fun setShowArcIndicators(show: Boolean) {
        showIndicators = show
        invalidate()
    }
    
    /**
     * Set arc indicator configuration
     */
    fun setArcConfig(config: ArcConfig) {
        arcConfig = config
        invalidate()
    }

    @Suppress("DEPRECATION")
    fun clear() {
        landmarks = null
        jointAngles = null
        jointStateInfos = emptyMap()
        jointArrowInfos = emptyMap()
        errorJointCodes = emptySet()
        positionErrors = emptyList()
        
        // Reset color smoothing state
        previousColors.clear()
        previousStates.clear()
        
        // Reset line indicator smoothing state
        lineRangeIndicator.reset()
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentLandmarks = landmarks ?: return
        if (currentLandmarks.isEmpty()) return

        // TRAINING MODE: Show Range Indicators (Arc or Line based on settings)
        // NON-TRAINING MODE: Show full skeleton
        if (isTrainingMode) {
            // During training: Range Indicators + Glowing Joints + Position Errors
            // No skeleton lines, no angles - just the essential guidance
            
            if (showIndicators && jointStateInfos.isNotEmpty()) {
                if (useArcIndicator) {
                    drawArcRangeIndicatorsNew(canvas, currentLandmarks)
                } else {
                    drawLineRangeIndicatorsNew(canvas, currentLandmarks)
                }
            }
            
            // Draw glowing joints on all tracked joints (Primary + Secondary)
            if (jointStateInfos.isNotEmpty()) {
                drawGlowingJoints(canvas, currentLandmarks)
            }
            
            if (positionErrors.isNotEmpty()) {
                drawPositionErrors(canvas, currentLandmarks)
            }
        } else {
            // Non-training mode: Show full skeleton
            drawConnections(canvas, currentLandmarks)
            drawLandmarks(canvas, currentLandmarks)
            
            if (showAngles) {
                drawAngles(canvas, currentLandmarks)
            }
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
                    
                    if (start.visibility >= visibilityThreshold && 
                        end.visibility >= visibilityThreshold) {
                        
                        // Check if this connection is part of a tracked joint
                        val startJointCode = landmarkIndexToJointCode(startIdx)
                        val endJointCode = landmarkIndexToJointCode(endIdx)
                        
                        // For front camera, we need to look up the mirrored joint code
                        // because jointArrowInfos uses the "logical" joint (after angle mirroring)
                        val effectiveStartJointCode = getEffectiveJointCode(startJointCode)
                        val effectiveEndJointCode = getEffectiveJointCode(endJointCode)
                        
                        // Find the relevant joint for this connection (if any)
                        val relevantJointCode = when {
                            effectiveStartJointCode in jointArrowInfos -> effectiveStartJointCode
                            effectiveEndJointCode in jointArrowInfos -> effectiveEndJointCode
                            else -> null
                        }
                        
                        // Check if tracked
                        val isTrackedConnection = isTrainingMode && 
                            (startIdx in trackedLandmarkIndices || endIdx in trackedLandmarkIndices)
                        
                        // Get gradient color and state for tracked joints
                        val arrowInfo = relevantJointCode?.let { jointArrowInfos[it] }
                        val gradientColor = arrowInfo?.let { getGradientColorForPosition(it) }
                        
                        // Determine opacity based on joint state
                        // Lower opacity (50%) for correct/warning state to focus on Arc
                        // Higher opacity (75%) for error state to draw attention
                        val trackedOpacity = when {
                            arrowInfo?.isError == true -> trackedOpacityError
                            arrowInfo?.isWarning == true -> trackedOpacityError
                            else -> trackedOpacityCorrect
                        }
                        
                        // Set color, width, and opacity based on state
                        // Using inline assignments to avoid Triple allocation per frame
                        when {
                            gradientColor != null -> {
                                linePaint.color = gradientColor
                                linePaint.strokeWidth = TRACKED_LINE_WIDTH
                                linePaint.alpha = (trackedOpacity * 255).toInt()
                            }
                            isTrackedConnection -> {
                                linePaint.color = COLOR_LINE_TRACKED
                                linePaint.strokeWidth = TRACKED_LINE_WIDTH
                                linePaint.alpha = (trackedOpacityCorrect * 255).toInt()
                            }
                            isTrainingMode -> {
                                linePaint.color = COLOR_LINE_DEFAULT
                                linePaint.strokeWidth = LINE_WIDTH
                                linePaint.alpha = (nonTrackedOpacity * 255).toInt()
                            }
                            else -> {
                                linePaint.color = COLOR_LINE_DEFAULT
                                linePaint.strokeWidth = LINE_WIDTH
                                linePaint.alpha = 153 // 0.6f * 255
                            }
                        }
                        
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
    
    // ==================== NEW: State-based Color Calculation ====================
    
    /**
     * Calculate gradient color based on JointStateInfo with SMOOTHING
     * 
     * SIMPLIFIED: Uses StateConfig colors as base, with gradient within zones
     * 
     * Color logic:
     * - DANGER/WARNING: Use StateConfig color directly (no gradient)
     * - PERFECT/NORMAL/PAD: Gradient based on position within stateRanges
     * - TRANSITION: Use StateConfig color (blue-gray)
     */
    private fun getGradientColorForState(stateInfo: JointStateInfo): Int {
        val jointCode = stateInfo.jointCode
        
        // Calculate target color based on state and position
        val targetColor = calculateTargetColorFromState(stateInfo)
        
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
     * Calculate target color from JointStateInfo
     * 
     * FIXED: Calculates color interpolation based on ACTUAL ranges
     * instead of fixed percentages. This ensures the skeleton color
     * matches exactly with the LineRangeIndicator and exercise config.
     */
    private fun calculateTargetColorFromState(stateInfo: JointStateInfo): Int {
        val state = stateInfo.state
        val stateRanges = stateInfo.stateRanges
        val currentAngle = stateInfo.currentAngle
        
        // Colors from StateConfig
        val perfectColor = StateConfig.getColor(JointState.PERFECT)
        val normalColor = StateConfig.getColor(JointState.NORMAL)
        val padColor = StateConfig.getColor(JointState.PAD)
        val warningColor = StateConfig.getColor(JointState.WARNING)
        val dangerColor = StateConfig.getColor(JointState.DANGER)
        val transitionColor = StateConfig.getColor(JointState.TRANSITION)
        
        // 1. Handle non-gradual states directly
        if (state == JointState.DANGER) return dangerColor
        if (state == JointState.WARNING) return warningColor
        if (state == JointState.TRANSITION) return transitionColor
        if (stateRanges == null) return stateInfo.color
        
        // 2. Determine ranges and calculate smooth gradient
        // We need to find where the current angle falls between the range boundaries
        
        val perfect = stateRanges.perfect
        val normal = stateRanges.normal
        val pad = stateRanges.pad
        
        // Calculate center of perfect range (optimal point)
        val center = (perfect.min + perfect.max) / 2.0
        
        // Check if we are on the "upper" side (angle > center) or "lower" side
        val isUpperSide = currentAngle > center
        
        // Get the boundaries for the current side
        val perfectBound = if (isUpperSide) perfect.max else perfect.min
        val normalBound = if (normal != null) (if (isUpperSide) normal.max else normal.min) else perfectBound
        val padBound = if (pad != null) (if (isUpperSide) pad.max else pad.min) else normalBound
        
        // Determine distance from center to boundaries (to handle direction correctly)
        val distToAngle = kotlin.math.abs(currentAngle - center)
        val distToPerfect = kotlin.math.abs(perfectBound - center)
        val distToNormal = kotlin.math.abs(normalBound - center)
        val distToPad = kotlin.math.abs(padBound - center)
        
        return when {
            // Case 1: Inside Perfect Range
            distToAngle <= distToPerfect -> {
                perfectColor
            }
            
            // Case 2: Between Perfect and Normal (Gradient Green -> Yellow)
            distToAngle <= distToNormal -> {
                val range = distToNormal - distToPerfect
                if (range <= 0) return normalColor
                val t = ((distToAngle - distToPerfect) / range).toFloat().coerceIn(0f, 1f)
                interpolateColor(perfectColor, normalColor, t)
            }
            
            // Case 3: Between Normal and Pad (Gradient Yellow -> Orange)
            distToAngle <= distToPad -> {
                val range = distToPad - distToNormal
                if (range <= 0) return padColor
                val t = ((distToAngle - distToNormal) / range).toFloat().coerceIn(0f, 1f)
                interpolateColor(normalColor, padColor, t)
            }
            
            // Case 4: Outside Pad (Gradient Orange -> Warning Red)
            else -> {
                // Smooth transition to warning color for a few degrees outside pad
                val warningThreshold = 5.0 // degrees
                val t = ((distToAngle - distToPad) / warningThreshold).toFloat().coerceIn(0f, 1f)
                interpolateColor(padColor, warningColor, t)
            }
        }
    }
    
    // ==================== Legacy: Arrow-based Color Calculation ====================
    
    /**
     * Get color for normalized position with state-aware coloring
     * @deprecated Use calculateTargetColorFromState instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use calculateTargetColorFromState instead")
    private fun getColorForNormalizedPositionNew(
        normalizedDist: Double, 
        isOuterSide: Boolean,
        currentState: JointState
    ): Int {
        val perfectColor = StateConfig.getColor(JointState.PERFECT)
        val normalColor = StateConfig.getColor(JointState.NORMAL)
        val padColor = StateConfig.getColor(JointState.PAD)
        
        return when {
            normalizedDist < 0.4 -> perfectColor
            normalizedDist < 0.7 -> {
                val t = ((normalizedDist - 0.4) / 0.3).toFloat()
                interpolateColor(perfectColor, normalColor, t)
            }
            else -> {
                if (isOuterSide) {
                    val t = ((normalizedDist - 0.7) / 0.3).toFloat()
                    interpolateColor(normalColor, padColor, t)
                } else {
                    normalColor
                }
            }
        }
    }
    
    // ==================== Legacy: Arrow-based Color Calculation ====================
    
    /**
     * Calculate gradient color based on angle position with SMOOTHING
     * @deprecated Use getGradientColorForState() instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use getGradientColorForState() instead")
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
     * @deprecated Use calculateTargetColorFromState() instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use calculateTargetColorFromState() instead")
    private fun calculateTargetColor(arrowInfo: JointArrowInfo, currentAngle: Double): Int {
        // Use StateConfig colors instead of hardcoded values
        val perfectColor = StateConfig.getColor(JointState.PERFECT)
        val normalColor = StateConfig.getColor(JointState.NORMAL)
        val padColor = StateConfig.getColor(JointState.PAD)
        val warningColor = StateConfig.getColor(JointState.WARNING)
        val dangerColor = StateConfig.getColor(JointState.DANGER)
        val transitionColor = StateConfig.getColor(JointState.TRANSITION)
        
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
            // Quick ramp to danger color (within 10°)
            val errorRatio = (distanceOutside / 10.0).coerceIn(0.0, 1.0)
            return interpolateColor(padColor, dangerColor, errorRatio.toFloat())
        }
        
        // For valid zones, use unified gradient approach
        return when (arrowInfo.zone) {
            JointZone.DOWN_ZONE -> {
                val rangeSize = downMax - downMin
                if (rangeSize <= 0) return perfectColor
                
                val center = (downMin + downMax) / 2
                val distFromCenter = kotlin.math.abs(currentAngle - center)
                val normalizedDist = (distFromCenter / (rangeSize / 2)).coerceIn(0.0, 1.0)
                val isOuterSide = currentAngle < center
                
                getColorForNormalizedPositionLegacy(normalizedDist, isOuterSide)
            }
            
            JointZone.UP_ZONE -> {
                val rangeSize = upMax - upMin
                if (rangeSize <= 0) return perfectColor
                
                val center = (upMin + upMax) / 2
                val distFromCenter = kotlin.math.abs(currentAngle - center)
                val normalizedDist = (distFromCenter / (rangeSize / 2)).coerceIn(0.0, 1.0)
                val isOuterSide = currentAngle > center
                
                getColorForNormalizedPositionLegacy(normalizedDist, isOuterSide)
            }
            
            JointZone.TRANSITION -> transitionColor
            
            else -> perfectColor
        }
    }
    
    /**
     * Get color for normalized position (legacy version using StateConfig)
     */
    private fun getColorForNormalizedPositionLegacy(normalizedDist: Double, isOuterSide: Boolean = true): Int {
        val perfectColor = StateConfig.getColor(JointState.PERFECT)
        val normalColor = StateConfig.getColor(JointState.NORMAL)
        val padColor = StateConfig.getColor(JointState.PAD)
        
        return when {
            normalizedDist < 0.4 -> perfectColor
            normalizedDist < 0.7 -> {
                val t = ((normalizedDist - 0.4) / 0.3).toFloat()
                interpolateColor(perfectColor, normalColor, t)
            }
            else -> {
                if (isOuterSide) {
                    val t = ((normalizedDist - 0.7) / 0.3).toFloat()
                    interpolateColor(normalColor, padColor, t)
                } else {
                    normalColor
                }
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
            if (landmark.visibility >= visibilityThreshold) {
                val x = landmark.x * imageWidth * scaleFactor
                val y = landmark.y * imageHeight * scaleFactor
                
                val jointCode = landmarkIndexToJointCode(index)
                val isTracked = index in trackedLandmarkIndices
                
                // For front camera, look up mirrored joint code in jointArrowInfos
                val effectiveJointCode = getEffectiveJointCode(jointCode)
                
                // Get gradient color and state for tracked joints
                val arrowInfo = effectiveJointCode?.let { jointArrowInfos[it] }
                val gradientColor = arrowInfo?.let { getGradientColorForPosition(it) }
                
                // Determine opacity based on joint state (consistent with lines)
                // Lower opacity (50%) for correct state to focus on Arc
                // Higher opacity (75%) for error/warning state
                val trackedOpacity = when {
                    arrowInfo?.isError == true -> trackedOpacityError
                    arrowInfo?.isWarning == true -> trackedOpacityError
                    else -> trackedOpacityCorrect
                }
                
                // Determine appearance based on state
                // Using inline assignments to avoid Triple allocation per frame
                val radius: Float
                when {
                    gradientColor != null -> {
                        pointPaint.color = gradientColor
                        pointPaint.alpha = (trackedOpacity * 255).toInt()
                        radius = TRACKED_LANDMARK_STROKE_WIDTH / 2
                    }
                    isTracked -> {
                        pointPaint.color = COLOR_TRACKED
                        pointPaint.alpha = (trackedOpacityCorrect * 255).toInt()
                        radius = TRACKED_LANDMARK_STROKE_WIDTH / 2
                    }
                    isTrainingMode -> {
                        pointPaint.color = COLOR_DEFAULT
                        pointPaint.alpha = (nonTrackedOpacity * 255).toInt()
                        radius = LANDMARK_STROKE_WIDTH / 2
                    }
                    else -> {
                        pointPaint.color = COLOR_DEFAULT
                        pointPaint.alpha = 153 // 0.6f * 255
                        radius = LANDMARK_STROKE_WIDTH / 2
                    }
                }
                
                canvas.drawCircle(x, y, radius, pointPaint)
                
                // Draw outer ring for tracked joints (with same opacity)
                if (isTrainingMode && isTracked) {
                    linePaint.style = Paint.Style.STROKE
                    linePaint.strokeWidth = 2f
                    linePaint.color = pointPaint.color  // Use same color as the point
                    linePaint.alpha = pointPaint.alpha  // Use same opacity
                    canvas.drawCircle(x, y, radius + 4f, linePaint)
                    linePaint.style = Paint.Style.STROKE
                }
                
                // Reset alpha
                pointPaint.alpha = 255
            }
        }
    }
    
    /**
     * Draw subtle glow effect on tracked joints when form is correct
     * @deprecated Use drawGlowingJoints() instead
     */
    @Deprecated("Use drawGlowingJoints() instead")
    private fun drawCorrectFormGlow(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        // Animate glow intensity
        val glowIntensity = 0.3f + 0.4f * sin(flowPhase * Math.PI * 2).toFloat()
        glowPaint.alpha = (glowIntensity * 255).toInt()
        
        for (index in trackedLandmarkIndices) {
            if (index < landmarks.size) {
                val landmark = landmarks[index]
                if (landmark.visibility >= visibilityThreshold) {
                    val x = landmark.x * imageWidth * scaleFactor
                    val y = landmark.y * imageHeight * scaleFactor
                    
                    canvas.drawCircle(x, y, 25f, glowPaint)
                }
            }
        }
    }
    
    /**
     * Draw glowing joints on Primary and Secondary joints with state-based colors
     * 
     * Features:
     * - Draws on all tracked joints (Primary and Secondary)
     * - Color matches the current JointState
     * - Animated glow effect with pulsing intensity
     * - Glow radius varies by joint importance (Primary larger than Secondary)
     */
    private fun drawGlowingJoints(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        if (jointStateInfos.isEmpty()) return
        
        // Animate glow intensity with flow phase
        val baseGlowIntensity = 0.4f + 0.3f * sin(flowPhase * Math.PI * 2).toFloat()
        
        for ((jointCode, stateInfo) in jointStateInfos) {
            // Get the center landmark for this joint
            val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(jointCode)
            if (angleLandmarks.size != 3) continue
            
            val centerIdx = angleLandmarks[1]
            val effectiveCenterIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(centerIdx) else centerIdx
            
            if (effectiveCenterIdx >= landmarks.size) continue
            val landmark = landmarks[effectiveCenterIdx]
            if (landmark.visibility < visibilityThreshold) continue
            
            // Calculate screen coordinates
            val x = landmark.x * imageWidth * scaleFactor
            val y = landmark.y * imageHeight * scaleFactor
            
            // Get color from state (using StateConfig for consistency)
            val stateColor = stateInfo.color
            
            // Size multiplier based on state severity
            // More severe states = larger glow to draw attention
            val sizeMultiplier = when (stateInfo.state) {
                JointState.PERFECT -> 1.0f      // Base size
                JointState.NORMAL -> 1.25f      // +25%
                JointState.TRANSITION -> 1.25f  // Same as NORMAL
                JointState.PAD -> 1.5f          // +50%
                JointState.WARNING -> 2.0f      // +100%
                JointState.DANGER -> 2.0f       // +100%
            }
            
            // Base glow parameters (different for Primary vs Secondary)
            val (baseGlowRadius, baseInnerRadius, baseGlowAlpha) = if (stateInfo.isPrimary) {
                Triple(28f, 10f, (baseGlowIntensity * 0.7f * 255).toInt())
            } else {
                Triple(20f, 7f, (baseGlowIntensity * 0.5f * 255).toInt())
            }
            
            // Apply size multiplier
            val glowRadius = baseGlowRadius * sizeMultiplier
            val innerRadius = baseInnerRadius * sizeMultiplier
            
            // Alpha also increases with severity for more emphasis
            val finalGlowAlpha = when (stateInfo.state) {
                JointState.DANGER -> (baseGlowAlpha * 1.4f).toInt().coerceAtMost(255)
                JointState.WARNING -> (baseGlowAlpha * 1.3f).toInt().coerceAtMost(255)
                JointState.PAD -> (baseGlowAlpha * 1.1f).toInt().coerceAtMost(255)
                else -> baseGlowAlpha
            }
            
            // 1. Draw outer glow (blurred, larger circle)
            glowPaint.color = stateColor
            glowPaint.alpha = finalGlowAlpha
            glowPaint.maskFilter = android.graphics.BlurMaskFilter(
                glowRadius * 0.6f, 
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
            canvas.drawCircle(x, y, glowRadius, glowPaint)
            glowPaint.maskFilter = null
            
            // 2. Draw inner solid circle (more visible center)
            pointPaint.color = stateColor
            pointPaint.alpha = (baseGlowIntensity * 255).toInt().coerceIn(150, 255)
            canvas.drawCircle(x, y, innerRadius, pointPaint)
            
            // 3. Draw outer ring for emphasis
            linePaint.style = Paint.Style.STROKE
            linePaint.strokeWidth = 2f
            linePaint.color = stateColor
            linePaint.alpha = (baseGlowIntensity * 200).toInt().coerceIn(100, 200)
            canvas.drawCircle(x, y, innerRadius + 4f, linePaint)
            linePaint.style = Paint.Style.STROKE
            
            // Reset paint states
            pointPaint.alpha = 255
            linePaint.alpha = 255
        }
    }
    
    /**
     * Convert landmark index to joint code
     * Uses JointLandmarkMapping as single source of truth
     */
    private fun landmarkIndexToJointCode(index: Int): String? {
        return JointLandmarkMapping.landmarkToJoint(index)
    }
    
    /**
     * Get the effective joint code for looking up in jointArrowInfos
     * 
     * For front camera, the image is mirrored, so:
     * - left_elbow in the image → right_elbow in jointArrowInfos
     * - right_elbow in the image → left_elbow in jointArrowInfos
     * 
     * This is because angles are mirrored before being sent to the engine,
     * so jointArrowInfos contains "right_elbow" even though the visible
     * landmark in the mirrored image is at the left_elbow position.
     */
    private fun getEffectiveJointCode(jointCode: String?): String? {
        if (jointCode == null) return null
        return if (isFrontCamera) {
            mirrorJointCode(jointCode)
        } else {
            jointCode
        }
    }
    
    /**
     * Mirror a joint code (left ↔ right)
     */
    private fun mirrorJointCode(jointCode: String): String {
        return when {
            jointCode.startsWith("left_") -> jointCode.replace("left_", "right_")
            jointCode.startsWith("right_") -> jointCode.replace("right_", "left_")
            else -> jointCode
        }
    }
    
    /**
     * Convert joint code to landmark index
     * Uses JointLandmarkMapping as single source of truth
     */
    /**
     * Convert joint code to landmark index
     * For front camera, applies mirroring since the image is mirrored
     */
    private fun jointCodeToLandmarkIndex(jointCode: String): Int? {
        val index = JointLandmarkMapping.jointToLandmark(jointCode) ?: return null
        // Apply mirroring for front camera
        return if (isFrontCamera) {
            BodyLandmarks.getMirroredIndex(index)
        } else {
            index
        }
    }
    
    // ==================== Line Range Indicator Drawing ====================
    
    // ==================== Arc Range Indicators (NEW) ====================
    
    /**
     * Draw Arc Range Indicators using JointStateInfo
     * 
     * Alternative visual style to Line Indicators:
     * - Arc around the joint showing valid angle ranges
     * - Current position indicator on the arc
     * 
     * Uses StateRanges for accurate gradient coloring matching LineRangeIndicator
     */
    private fun drawArcRangeIndicatorsNew(
        canvas: Canvas,
        landmarks: List<SmoothedLandmark>
    ) {
        val density = resources.displayMetrics.density
        
        for ((jointCode, stateInfo) in jointStateInfos) {
            // Only show Arc for PRIMARY joints
            if (!stateInfo.isPrimary) continue
            
            // Get center landmark for this joint
            val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(jointCode)
            if (angleLandmarks.size != 3) continue
            
            val centerIdx = angleLandmarks[1]
            val effectiveCenterIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(centerIdx) else centerIdx
            
            if (effectiveCenterIdx >= landmarks.size) continue
            val centerLm = landmarks[effectiveCenterIdx]
            if (centerLm.visibility < visibilityThreshold) continue
            
            // Convert to screen coordinates (same calculation as LineRangeIndicator)
            val centerX = centerLm.x * imageWidth * scaleFactor
            val centerY = centerLm.y * imageHeight * scaleFactor
            
            // Create ArcRangeData from JointStateInfo
            val arcData = ArcRangeData.fromStateInfo(centerX, centerY, stateInfo)
            
            // Check if should show based on config
            if (!arcRangeIndicator.shouldShowArc(arcData, arcConfig)) continue
            
            // Draw using StateRanges-based coloring
            arcRangeIndicator.drawWithStateRanges(canvas, arcData, arcConfig, density)
        }
    }
    
    // ==================== Line Range Indicators ====================
    
    /**
     * Draw Line Range Indicators using NEW JointStateInfo
     * 
     * TWO-LAYER SYSTEM:
     * 1. Static Tracks on BOTH limbs (UP and DOWN) - shows full movement range
     * 2. Moving Indicator on current limb only - shows current position
     * 
     * Uses StateConfig colors for consistency with unified state system
     */
    private fun drawLineRangeIndicatorsNew(
        canvas: Canvas,
        landmarks: List<SmoothedLandmark>
    ) {
        val density = resources.displayMetrics.density
        
        for ((jointCode, stateInfo) in jointStateInfos) {
            // Only show Line Indicator for PRIMARY joints
            if (!stateInfo.isPrimary) continue
            
            val currentAngle = stateInfo.currentAngle
            
            // Get the 3 landmarks that form this angle
            val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(jointCode)
            if (angleLandmarks.size != 3) continue
            
            val (upperIdx, centerIdx, lowerIdx) = angleLandmarks
            
            // Apply mirroring for front camera
            val effectiveUpperIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(upperIdx) else upperIdx
            val effectiveCenterIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(centerIdx) else centerIdx
            val effectiveLowerIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(lowerIdx) else lowerIdx
            
            // Validate center landmark
            if (effectiveCenterIdx >= landmarks.size) continue
            val centerLm = landmarks[effectiveCenterIdx]
            if (centerLm.visibility < visibilityThreshold) continue
            
            // Get UPPER landmark
            if (effectiveUpperIdx >= landmarks.size) continue
            val upperLm = landmarks[effectiveUpperIdx]
            
            // Get LOWER landmark
            if (effectiveLowerIdx >= landmarks.size) continue
            val lowerLm = landmarks[effectiveLowerIdx]
            
            // Calculate screen coordinates
            val centerX = centerLm.x * imageWidth * scaleFactor
            val centerY = centerLm.y * imageHeight * scaleFactor
            val upperX = upperLm.x * imageWidth * scaleFactor
            val upperY = upperLm.y * imageHeight * scaleFactor
            val lowerX = lowerLm.x * imageWidth * scaleFactor
            val lowerY = lowerLm.y * imageHeight * scaleFactor
            
            // Calculate distances
            val upperDistance = kotlin.math.sqrt(
                (upperX - centerX) * (upperX - centerX) +
                (upperY - centerY) * (upperY - centerY)
            )
            val lowerDistance = kotlin.math.sqrt(
                (lowerX - centerX) * (lowerX - centerX) +
                (lowerY - centerY) * (lowerY - centerY)
            )
            
            // Max lengths from settings
            val upperMaxLength = upperDistance * SettingsManager.getLineIndicatorUpperLengthRatio()
            val lowerMaxLength = lowerDistance * SettingsManager.getLineIndicatorLowerLengthRatio()
            
            // Get style based on state
            val style = LineStyleNew.forState(stateInfo, density)
            
            // Determine if this is a HOLD exercise (single range, not up/down)
            // Hold exercises have same ranges for both up and down
            val isHoldExercise = stateInfo.upStateRanges != null && 
                stateInfo.upStateRanges == stateInfo.downStateRanges
            
            // For Hold exercises: determine which limb to show based on target angle
            val holdTargetLimb = if (isHoldExercise) {
                val perfectRange = stateInfo.stateRanges?.perfect
                val targetCenter = perfectRange?.let { (it.min + it.max) / 2 } ?: 90.0
                if (targetCenter >= 90.0) {
                    LineRangeIndicator.LimbType.UPPER
                } else {
                    LineRangeIndicator.LimbType.LOWER
                }
            } else null
            
            // ========== 1. Draw STATIC TRACKS ==========
            
            // For Hold: Only draw ONE track on the target limb
            // For Rep exercises: Draw tracks on BOTH limbs
            
            // Draw UPPER track (if visible and applicable)
            val shouldDrawUpperTrack = upperLm.visibility >= visibilityThreshold &&
                (holdTargetLimb == null || holdTargetLimb == LineRangeIndicator.LimbType.UPPER)
            
            if (shouldDrawUpperTrack) {
                lineRangeIndicator.drawTrackNew(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = centerY,
                    targetX = upperX,
                    targetY = upperY,
                    maxLength = upperMaxLength,
                    style = style,
                    stateInfo = stateInfo,
                    limbType = LineRangeIndicator.LimbType.UPPER
                )
            }
            
            // Draw LOWER track (if visible and applicable)
            val shouldDrawLowerTrack = lowerLm.visibility >= visibilityThreshold &&
                (holdTargetLimb == null || holdTargetLimb == LineRangeIndicator.LimbType.LOWER)
            
            if (shouldDrawLowerTrack) {
                lineRangeIndicator.drawTrackNew(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = centerY,
                    targetX = lowerX,
                    targetY = lowerY,
                    maxLength = lowerMaxLength,
                    style = style,
                    stateInfo = stateInfo,
                    limbType = LineRangeIndicator.LimbType.LOWER
                )
            }
            
            // ========== 2. Draw MOVING INDICATOR ==========
            
            // For Hold: Use the target limb direction
            // For Rep: Use hysteresis-based limb detection to prevent flickering at center
            val limbType = if (isHoldExercise && holdTargetLimb != null) {
                holdTargetLimb
            } else {
                // Use hysteresis to prevent indicator flickering when crossing center angle
                lineRangeIndicator.getTargetLimbWithHysteresis(
                    jointCode = jointCode,
                    currentAngle = currentAngle,
                    invertIndicator = stateInfo.invertIndicator
                )
            }
            
            // Update transition progress for smooth crossover animation
            lineRangeIndicator.updateTransitionProgress(jointCode)
            
            if (limbType == LineRangeIndicator.LimbType.NONE) continue
            
            val (targetX, targetY, maxLength) = when (limbType) {
                LineRangeIndicator.LimbType.UPPER -> Triple(upperX, upperY, upperMaxLength)
                LineRangeIndicator.LimbType.LOWER -> Triple(lowerX, lowerY, lowerMaxLength)
                LineRangeIndicator.LimbType.NONE -> continue
            }
            
            // Calculate indicator length based on angle (with inversion support)
            // This also applies transition smoothing when switching limbs
            val lineLength = lineRangeIndicator.calculateLineLength(
                jointCode = jointCode,
                currentAngle = currentAngle,
                maxLength = maxLength,
                invertIndicator = stateInfo.invertIndicator
            )
            
            // Draw the moving indicator
            lineRangeIndicator.drawIndicatorNew(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                targetX = targetX,
                targetY = targetY,
                lineLength = lineLength,
                stateInfo = stateInfo,
                style = style,
                density = density,
                maxLength = maxLength,
                limbType = limbType
            )
        }
    }
    
    /**
     * Draw Line Range Indicators - LEGACY (using JointArrowInfo)
     * @deprecated Use drawLineRangeIndicatorsNew() instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use drawLineRangeIndicatorsNew() instead")
    private fun drawLineRangeIndicators(
        canvas: Canvas,
        landmarks: List<SmoothedLandmark>
    ) {
        val density = resources.displayMetrics.density
        
        for ((jointCode, arrowInfo) in jointArrowInfos) {
            // Only show Line Indicator for PRIMARY joints
            if (!arrowInfo.isPrimary) continue
            
            val currentAngle = arrowInfo.currentAngle
            
            // Get the 3 landmarks that form this angle
            val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(jointCode)
            if (angleLandmarks.size != 3) continue
            
            val (upperIdx, centerIdx, lowerIdx) = angleLandmarks
            
            // Apply mirroring for front camera
            val effectiveUpperIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(upperIdx) else upperIdx
            val effectiveCenterIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(centerIdx) else centerIdx
            val effectiveLowerIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(lowerIdx) else lowerIdx
            
            // Validate center landmark
            if (effectiveCenterIdx >= landmarks.size) continue
            val centerLm = landmarks[effectiveCenterIdx]
            if (centerLm.visibility < visibilityThreshold) continue
            
            // Get UPPER landmark
            if (effectiveUpperIdx >= landmarks.size) continue
            val upperLm = landmarks[effectiveUpperIdx]
            
            // Get LOWER landmark
            if (effectiveLowerIdx >= landmarks.size) continue
            val lowerLm = landmarks[effectiveLowerIdx]
            
            // Calculate screen coordinates for center
            val centerX = centerLm.x * imageWidth * scaleFactor
            val centerY = centerLm.y * imageHeight * scaleFactor
            
            // Calculate screen coordinates for UPPER limb
            val upperX = upperLm.x * imageWidth * scaleFactor
            val upperY = upperLm.y * imageHeight * scaleFactor
            
            // Calculate screen coordinates for LOWER limb
            val lowerX = lowerLm.x * imageWidth * scaleFactor
            val lowerY = lowerLm.y * imageHeight * scaleFactor
            
            // Calculate distances
            val upperDistance = kotlin.math.sqrt(
                (upperX - centerX) * (upperX - centerX) +
                (upperY - centerY) * (upperY - centerY)
            )
            val lowerDistance = kotlin.math.sqrt(
                (lowerX - centerX) * (lowerX - centerX) +
                (lowerY - centerY) * (lowerY - centerY)
            )
            
            // Max lengths from settings
            val upperMaxLength = upperDistance * SettingsManager.getLineIndicatorUpperLengthRatio()
            val lowerMaxLength = lowerDistance * SettingsManager.getLineIndicatorLowerLengthRatio()
            
            // Get style
            val style = LineStyle.forState(arrowInfo, density)
            
            // ========== 1. Draw STATIC TRACKS on BOTH limbs ==========
            
            // Draw UPPER track (if visible)
            if (upperLm.visibility >= visibilityThreshold) {
                lineRangeIndicator.drawTrack(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = centerY,
                    targetX = upperX,
                    targetY = upperY,
                    maxLength = upperMaxLength,
                    style = style,
                    arrowInfo = arrowInfo,
                    limbType = LineRangeIndicator.LimbType.UPPER
                )
            }
            
            // Draw LOWER track (if visible)
            if (lowerLm.visibility >= visibilityThreshold) {
                lineRangeIndicator.drawTrack(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = centerY,
                    targetX = lowerX,
                    targetY = lowerY,
                    maxLength = lowerMaxLength,
                    style = style,
                    arrowInfo = arrowInfo,
                    limbType = LineRangeIndicator.LimbType.LOWER
                )
            }
            
            // ========== 2. Draw MOVING INDICATOR on current limb only ==========
            
            // Use hysteresis to prevent flickering when crossing center angle
            val limbType = lineRangeIndicator.getTargetLimbWithHysteresis(
                jointCode = jointCode,
                currentAngle = currentAngle,
                invertIndicator = false
            )
            lineRangeIndicator.updateTransitionProgress(jointCode)
            
            if (limbType == LineRangeIndicator.LimbType.NONE) continue
            
            val (targetX, targetY, maxLength) = when (limbType) {
                LineRangeIndicator.LimbType.UPPER -> Triple(upperX, upperY, upperMaxLength)
                LineRangeIndicator.LimbType.LOWER -> Triple(lowerX, lowerY, lowerMaxLength)
                LineRangeIndicator.LimbType.NONE -> continue
            }
            
            // Calculate indicator length based on angle (with transition smoothing)
            val lineLength = lineRangeIndicator.calculateLineLength(
                jointCode = jointCode,
                currentAngle = currentAngle,
                maxLength = maxLength
            )
            
            // Draw the moving indicator
            lineRangeIndicator.drawIndicator(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                targetX = targetX,
                targetY = targetY,
                lineLength = lineLength,
                arrowInfo = arrowInfo,
                style = style,
                density = density,
                maxLength = maxLength,
                limbType = limbType
            )
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
            hasError -> StateConfig.getColor(JointState.DANGER)
            isWarning -> StateConfig.getColor(JointState.WARNING)
            else -> Color.WHITE
        }
        
        canvas.drawText("%.0f°".format(angle), x, y, textPaint)
        textPaint.color = Color.WHITE
    }
    
    // ==================== Position Error Drawing ====================
    
    /**
     * Draw visual indicator for position errors
     * 
     * SINGLE ERROR DISPLAY: Only shows the highest severity error to reduce clutter
     * Priority: ERROR > WARNING > TIP
     */
    private fun drawPositionErrors(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        // Select single highest priority error
        val primaryError = getPrimaryPositionError() ?: return
        
        drawSinglePositionError(canvas, landmarks, primaryError)
    }
    
    /**
     * Get the highest priority position error
     * Priority: ERROR > WARNING > TIP
     */
    private fun getPrimaryPositionError(): PositionError? {
        return positionErrors.firstOrNull { it.severity == CheckSeverity.ERROR }
            ?: positionErrors.firstOrNull { it.severity == CheckSeverity.WARNING }
            ?: positionErrors.firstOrNull()
    }
    
    /**
     * Draw a single position error with bracket-style highlighting
     */
    private fun drawSinglePositionError(
        canvas: Canvas,
        landmarks: List<SmoothedLandmark>,
        error: PositionError
    ) {
        val landmark1Idx = landmarkNameToIndex(error.landmark1)
        val landmark2Idx = landmarkNameToIndex(error.landmark2)
        
        if (landmark1Idx == null || landmark2Idx == null ||
            landmark1Idx >= landmarks.size || landmark2Idx >= landmarks.size) {
            return
        }
        
        val lm1 = landmarks[landmark1Idx]
        val lm2 = landmarks[landmark2Idx]
        
        if (lm1.visibility < visibilityThreshold || 
            lm2.visibility < visibilityThreshold) {
            return
        }
        
        val x1 = lm1.x * imageWidth * scaleFactor
        val y1 = lm1.y * imageHeight * scaleFactor
        val x2 = lm2.x * imageWidth * scaleFactor
        val y2 = lm2.y * imageHeight * scaleFactor
        
        // Choose paint based on severity
        val (linePaintToUse, circleRadius) = when (error.severity) {
            CheckSeverity.ERROR -> positionErrorPaint to 22f
            CheckSeverity.WARNING -> positionWarningPaint to 18f
            CheckSeverity.TIP -> positionTipPaint to 14f
        }
        
        // Draw dashed line between the two landmarks
        canvas.drawLine(x1, y1, x2, y2, linePaintToUse)
        
        // Draw bracket-style circles at both landmarks
        canvas.drawCircle(x1, y1, circleRadius, linePaintToUse)
        canvas.drawCircle(x2, y2, circleRadius, linePaintToUse)
        
        // For ERROR severity, add filled circle with transparency
        if (error.severity == CheckSeverity.ERROR) {
            canvas.drawCircle(x1, y1, circleRadius - 5f, positionErrorFillPaint)
            canvas.drawCircle(x2, y2, circleRadius - 5f, positionErrorFillPaint)
        }
    }
    
    /**
     * Convert landmark name to MediaPipe landmark index
     * Uses JointLandmarkMapping as single source of truth
     */
    /**
     * Convert landmark name to index
     * For front camera, applies mirroring since the image is mirrored
     */
    private fun landmarkNameToIndex(name: String): Int? {
        val index = JointLandmarkMapping.jointToLandmark(name) ?: return null
        // Apply mirroring for front camera
        return if (isFrontCamera) {
            BodyLandmarks.getMirroredIndex(index)
        } else {
            index
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flowAnimator?.cancel()
    }
}
