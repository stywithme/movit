@file:Suppress("DEPRECATION")
package com.trainingvalidator.poc.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.CameraPositionDetector
import com.trainingvalidator.poc.training.engine.JointArrowInfo
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.models.StateConfig
import com.trainingvalidator.poc.training.models.ZoneType
import com.trainingvalidator.poc.ui.training.Direction
import com.trainingvalidator.poc.ui.training.GuidanceLevel
import com.trainingvalidator.poc.ui.training.JointGuidance
import kotlin.math.hypot
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
        private const val TORSO_REF_PX = 500f
        
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
        // Higher = faster transitions = less visual lag. Lower = smoother but more lag.
        const val COLOR_LERP_FACTOR = 0.5f  // Doubled from 0.25 to reduce visual lag
    }
    
    // ==================== Cached values for performance ====================
    
    // Cache density to avoid repeated resource access
    private val cachedDensity: Float by lazy { resources.displayMetrics.density }
    
    // Cache BlurMaskFilters (expensive to create)
    private val blurFilterSmall: android.graphics.BlurMaskFilter by lazy {
        android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val blurFilterMedium: android.graphics.BlurMaskFilter by lazy {
        android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val blurFilterLarge: android.graphics.BlurMaskFilter by lazy {
        android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    
    // ==================== Settings-based values (cached) ====================
    
    // Cache settings values - updated when view is attached or settings change
    private var cachedVisibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    private var cachedNonTrackedOpacity: Float = 0.18f
    private var cachedTrackedOpacityCorrect: Float = 0.50f
    private var cachedTrackedOpacityError: Float = 0.75f
    
    // Public accessors use cached values
    private val visibilityThreshold: Float get() = cachedVisibilityThreshold
    private val nonTrackedOpacity: Float get() = cachedNonTrackedOpacity
    private val trackedOpacityCorrect: Float get() = cachedTrackedOpacityCorrect
    private val trackedOpacityError: Float get() = cachedTrackedOpacityError
    
    // Refresh cached settings (call when settings might have changed)
    private fun refreshCachedSettings() {
        if (SettingsManager.isLoaded) {
            cachedVisibilityThreshold = SettingsManager.getOverlayVisibility()
            cachedNonTrackedOpacity = SettingsManager.getNonTrackedOpacity()
            cachedTrackedOpacityCorrect = SettingsManager.getTrackedCorrectOpacity()
            cachedTrackedOpacityError = SettingsManager.getTrackedErrorOpacity()
        }
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
    private var isBilateralFlipped: Boolean = false  // For bilateral side flipping
    
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
    private var displayOffsetX: Float = 0f
    private var displayOffsetY: Float = 0f
    private var useFitCenter: Boolean = false
    
    // OPTIMIZED: Cached view dimensions for scale factor calculation
    private var cachedViewWidth: Int = 0
    private var cachedViewHeight: Int = 0
    
    // Settings
    private var showAngles = true
    private var showAnglesOnlyOnError = true  // Only show angles on error/critical
    
    // Color smoothing state (prevents flickering)
    private val previousColors = mutableMapOf<String, Int>()
    private val previousStates = mutableMapOf<String, JointState>()
    
    // Flow animation phase - calculated from system time in onDraw (no ValueAnimator overhead)
    private var flowPhase: Float = 0f

    
    // Visual Indicators
    private val lineRangeIndicator = LineRangeIndicator()
    private val arcRangeIndicator = ArcRangeIndicator()
    private var arcConfig = ArcConfig()
    
    // Indicator type setting (read from SettingsManager)
    private var useArcIndicator: Boolean = false
    private var showIndicators = true

    // ── Setup mode ────────────────────────────────────────────────────────
    // When true the overlay renders only tracked joints with colour-coded
    // guidance (GREEN/YELLOW/RED) and large angle labels instead of the
    // full skeleton or training range indicators.

    private var isSetupMode: Boolean = false
    private var isSceneCheckMode: Boolean = false
    private var setupJointGuidances: List<JointGuidance> = emptyList()

    // Paints reused across setup-mode draws (allocated once)
    private val setupJointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val setupJointStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val setupLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
    }
    private val setupTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 52f   // ~32sp - readable from 2-3 metres
        color = Color.WHITE
        setShadowLayer(6f, 1f, 1f, Color.BLACK)
        isFakeBoldText = true
    }
    private val setupTextBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(160, 0, 0, 0)
    }
    private val setupArrowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 6f
    }

    // ── Debug mode ───────────────────────────────────────────────────────
    // Minimal overlay: circle on vertex joint, angle label, small dots on
    // the two endpoint landmarks. No full skeleton, no bone lines.

    private var isDebugMode: Boolean = false
    private var debugJointCode: String? = null
    private var debugAngleValue: Double? = null
    private var debugEndpointIndices: List<Int> = emptyList()  // [pointA, pointC]
    private var debugVertexIndex: Int = -1

    private val debugDotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#B0FFFFFF")
    }
    private val debugLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4f
        color = Color.parseColor("#60FFFFFF")
    }
    private val debugCircleFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val debugCircleStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val debugAngleTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 52f
        color = Color.WHITE
        setShadowLayer(6f, 1f, 1f, Color.BLACK)
        isFakeBoldText = true
    }
    private val debugAngleBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(160, 0, 0, 0)
    }

    /**
     * Update overlay in debug mode: shows only the selected joint with
     * a circle, its angle value, and small dots at the two arm endpoints.
     *
     * @param jointCode   The joint being tested (e.g. "left_knee")
     * @param angle       Current angle value (nullable if not visible)
     * @param endpointA   Landmark index of endpoint A (first arm)
     * @param endpointC   Landmark index of endpoint C (second arm)
     * @param vertexIdx   Landmark index of the vertex (center joint)
     */
    fun updateDebugJoint(
        jointCode: String,
        angle: Double?,
        endpointA: Int,
        endpointC: Int,
        vertexIdx: Int,
        smoothedLandmarks: List<SmoothedLandmark>?,
        imageW: Int = imageWidth,
        imageH: Int = imageHeight,
        useFrontCamera: Boolean = false
    ) {
        isDebugMode = true
        isSetupMode = false
        isTrainingMode = false
        debugJointCode = jointCode
        debugAngleValue = angle
        debugEndpointIndices = listOf(endpointA, endpointC)
        debugVertexIndex = vertexIdx
        isFrontCamera = useFrontCamera
        landmarks = smoothedLandmarks
        val dimChanged = imageWidth != imageW || imageHeight != imageH
        imageWidth = imageW
        imageHeight = imageH
        if (dimChanged) recalculateScaleFactor()
        invalidate()
    }

    /** Disable debug mode. */
    fun clearDebugMode() {
        isDebugMode = false
        debugJointCode = null
        debugAngleValue = null
        debugEndpointIndices = emptyList()
        debugVertexIndex = -1
        smoothedDebugRadius = 0f
    }

    // ── Camera Detection mode ─────────────────────────────────────────────
    // Shows key landmarks used by CameraPositionDetector with color coding.

    private var isCameraDetectionMode: Boolean = false
    private var camDetectResult: CameraPositionDetector.CameraDetectionResult? = null
    private var camDetectMatchesExpected: Boolean = true

    private val camLeftDotPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL; color = Color.parseColor("#00E5FF")
    }
    private val camRightDotPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL; color = Color.parseColor("#FF9100")
    }
    private val camNoseDotPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL; color = Color.WHITE
    }
    private val camShoulderLinePaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val camDepthBarPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL
    }
    private val camLabelPaint = Paint().apply {
        isAntiAlias = true; textAlign = Paint.Align.CENTER; color = Color.WHITE
        isFakeBoldText = true; setShadowLayer(6f, 1f, 1f, Color.BLACK)
    }
    private val camSubLabelPaint = Paint().apply {
        isAntiAlias = true; textAlign = Paint.Align.CENTER; color = Color.parseColor("#B0FFFFFF")
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    private val camBgPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL; color = Color.argb(160, 0, 0, 0)
    }

    fun updateCameraDetection(
        detectionResult: CameraPositionDetector.CameraDetectionResult,
        matchesExpected: Boolean,
        smoothedLandmarks: List<SmoothedLandmark>?,
        imageW: Int = imageWidth,
        imageH: Int = imageHeight,
        useFrontCamera: Boolean = false
    ) {
        isCameraDetectionMode = true
        isDebugMode = false
        isSetupMode = false
        isTrainingMode = false
        camDetectResult = detectionResult
        camDetectMatchesExpected = matchesExpected
        isFrontCamera = useFrontCamera
        landmarks = smoothedLandmarks
        val dimChanged = imageWidth != imageW || imageHeight != imageH
        imageWidth = imageW
        imageHeight = imageH
        if (dimChanged) recalculateScaleFactor()
        invalidate()
    }

    fun clearCameraDetectionMode() {
        isCameraDetectionMode = false
        camDetectResult = null
    }

    init {
        // Read indicator type from settings
        useArcIndicator = SettingsManager.useArcIndicator()
        showIndicators = true
        
        // Cache settings values
        refreshCachedSettings()
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Refresh cached settings when view is attached
        refreshCachedSettings()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // OPTIMIZED: Cache view dimensions and recalculate scale factor
        cachedViewWidth = w
        cachedViewHeight = h
        recalculateScaleFactor()
    }
    
    /**
     * Recalculate scale factor when view or image dimensions change
     */
    private fun recalculateScaleFactor() {
        if (cachedViewWidth > 0 && cachedViewHeight > 0 && imageWidth > 0 && imageHeight > 0) {
            val scaleX = cachedViewWidth.toFloat() / imageWidth
            val scaleY = cachedViewHeight.toFloat() / imageHeight
            if (useFitCenter) {
                scaleFactor = kotlin.math.min(scaleX, scaleY)
                displayOffsetX = (cachedViewWidth - imageWidth * scaleFactor) / 2f
                displayOffsetY = (cachedViewHeight - imageHeight * scaleFactor) / 2f
            } else {
                scaleFactor = max(scaleX, scaleY)
                displayOffsetX = 0f
                displayOffsetY = 0f
            }
        }
    }

    fun setScaleMode(fitCenter: Boolean) {
        if (useFitCenter != fitCenter) {
            useFitCenter = fitCenter
            recalculateScaleFactor()
        }
    }

    private fun toScreenX(normalizedX: Float): Float =
        normalizedX * imageWidth * scaleFactor + displayOffsetX

    private fun toScreenY(normalizedY: Float): Float =
        normalizedY * imageHeight * scaleFactor + displayOffsetY
    
    /**
     * Calculate flow phase from system time (replaces ValueAnimator)
     * 
     * Benefits over ValueAnimator:
     * - No continuous invalidate() calls (was causing 60fps redraws even without new pose data)
     * - Flow phase is now calculated on-demand only when onDraw is triggered by new pose data
     * - Reduces Main Thread workload by ~50% (from 60fps to ~30fps redraws)
     */
    private fun updateFlowPhase() {
        if (isTrainingMode) {
            flowPhase = (SystemClock.uptimeMillis() % 2000L) / 2000f
        }
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
        val dimensionsChanged = imageWidth != inputImageWidth || imageHeight != inputImageHeight
        imageWidth = inputImageWidth
        imageHeight = inputImageHeight
        jointAngles = angles
        // OPTIMIZED: Only recalculate scale factor if dimensions changed
        if (dimensionsChanged) {
            recalculateScaleFactor()
        }
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
        positionErrors: List<PositionError> = emptyList(),
        bilateralFlipped: Boolean = false
    ) {
        landmarks = smoothedLandmarks
        val dimensionsChanged = imageWidth != inputImageWidth || imageHeight != inputImageHeight
        imageWidth = inputImageWidth
        imageHeight = inputImageHeight
        jointAngles = angles
        jointStateInfos = stateInfos
        this.positionErrors = positionErrors
        this.isBilateralFlipped = bilateralFlipped
        // OPTIMIZED: Only recalculate scale factor if dimensions changed
        if (dimensionsChanged) {
            recalculateScaleFactor()
        }
        
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
        val dimensionsChanged = imageWidth != inputImageWidth || imageHeight != inputImageHeight
        imageWidth = inputImageWidth
        imageHeight = inputImageHeight
        jointAngles = angles
        jointArrowInfos = arrowInfos
        this.positionErrors = positionErrors
        // OPTIMIZED: Only recalculate scale factor if dimensions changed
        if (dimensionsChanged) {
            recalculateScaleFactor()
        }
        
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
        if (enabled) {
            isSetupMode = false
            setupJointGuidances = emptyList()
        }
        isFrontCamera = useFrontCamera
        rawTrackedLandmarkIndices = trackedIndices
        
        // For front camera, mirror the tracked indices since the image is mirrored
        // e.g., if tracking right_elbow (14), we need to highlight left_elbow (13) in the image
        trackedLandmarkIndices = if (useFrontCamera) {
            trackedIndices.map { BodyLandmarks.getMirroredIndex(it) }.toSet()
        } else {
            trackedIndices
        }
        
        invalidate()
    }
    
    /**
     * Update front camera state without resetting training mode.
     * Call this when camera is switched mid-training to keep overlay
     * mirroring in sync with the active camera.
     */
    fun updateFrontCameraState(useFrontCamera: Boolean) {
        if (isFrontCamera == useFrontCamera) return
        isFrontCamera = useFrontCamera
        
        // Recompute mirrored tracked indices
        trackedLandmarkIndices = if (useFrontCamera) {
            rawTrackedLandmarkIndices.map { BodyLandmarks.getMirroredIndex(it) }.toSet()
        } else {
            rawTrackedLandmarkIndices
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

    // ── Setup mode public API ─────────────────────────────────────────────

    /**
     * Enable setup-mode overlay: shows only tracked joints with colour-coded
     * guidance (GREEN / YELLOW / RED) and large readable angle labels.
     *
     * Call from TrainingActivity during SETUP_POSE state.
     *
     * @param guidances Per-joint guidance from PoseSetupGuide
     * @param smoothedLandmarks Current smoothed landmarks
     * @param imageW  Camera frame width
     * @param imageH  Camera frame height
     */
    fun updateSetupGuidance(
        guidances: List<JointGuidance>,
        smoothedLandmarks: List<SmoothedLandmark>?,
        imageW: Int = imageWidth,
        imageH: Int = imageHeight,
        useFrontCamera: Boolean? = null
    ) {
        // Never override training mode — setup overlay is only for SETUP_POSE state
        if (isTrainingMode) return

        if (useFrontCamera != null) {
            isFrontCamera = useFrontCamera
        }
        isSetupMode = true
        setupJointGuidances = guidances
        landmarks = smoothedLandmarks
        val dimChanged = imageWidth != imageW || imageHeight != imageH
        imageWidth = imageW
        imageHeight = imageH
        if (dimChanged) recalculateScaleFactor()
        invalidate()
    }

    /** Disable setup mode (called when moving to COUNTDOWN or TRAINING). */
    fun clearSetupMode() {
        isSetupMode = false
        isSceneCheckMode = false
        setupJointGuidances = emptyList()
    }

    /**
     * Toggle scene-check mode: draws ONLY 4 torso landmarks (shoulders + hips)
     * as semi-transparent dots with connecting lines. No face, no hands.
     */
    fun setSceneCheckMode(enabled: Boolean) {
        isSceneCheckMode = enabled
        if (enabled) {
            isSetupMode = false
            setupJointGuidances = emptyList()
        }
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
        
        previousColors.clear()
        previousStates.clear()
        lineRangeIndicator.reset()
        smoothedDebugRadius = 0f
        smoothedSetupRadius.clear()
        smoothedBodyScale = 0f
        smoothedArcRadiusPx.clear()

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentLandmarks = landmarks ?: return
        if (currentLandmarks.isEmpty()) return

        updateFlowPhase()
        val bodyScale = computeBodyScale(currentLandmarks)

        when {
            isCameraDetectionMode -> {
                drawCameraDetection(canvas, currentLandmarks)
                return
            }
            isDebugMode -> {
                drawDebugJoint(canvas, currentLandmarks)
                return
            }
            isSceneCheckMode -> {
                drawSceneCheckOverlay(canvas, currentLandmarks)
            }
            isSetupMode -> {
                drawSetupGuidance(canvas, currentLandmarks)
            }
            isTrainingMode -> {
                if (showIndicators && jointStateInfos.isNotEmpty()) {
                    if (useArcIndicator) {
                        drawArcRangeIndicatorsNew(canvas, currentLandmarks, bodyScale)
                    } else {
                        drawLineRangeIndicatorsNew(canvas, currentLandmarks, bodyScale)
                    }
                }
                if (jointStateInfos.isNotEmpty()) {
                    drawGlowingJoints(canvas, currentLandmarks, bodyScale)
                }
                if (positionErrors.isNotEmpty()) {
                    drawPositionErrors(canvas, currentLandmarks)
                }
            }
            else -> {
                drawConnections(canvas, currentLandmarks)
                drawLandmarks(canvas, currentLandmarks)
                if (showAngles) {
                    drawAngles(canvas, currentLandmarks)
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scene Check Mode Drawing (torso-only: shoulders + hips)
    // ──────────────────────────────────────────────────────────────────────

    private fun drawSceneCheckOverlay(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        val torsoIndices = intArrayOf(11, 12, 23, 24) // left/right shoulder, left/right hip
        val connections = arrayOf(11 to 12, 11 to 23, 12 to 24, 23 to 24)

        val scaleX = width.toFloat()
        val scaleY = height.toFloat()

        fun effectiveIdx(raw: Int) = if (isFrontCamera) BodyLandmarks.getMirroredIndex(raw) else raw
        fun screenPos(rawIdx: Int): Pair<Float, Float>? {
            val eff = effectiveIdx(rawIdx)
            if (eff >= landmarks.size) return null
            val lm = landmarks[eff]
            if (lm.visibility < 0.2f) return null
            return Pair(lm.x * scaleX, lm.y * scaleY)
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(128, 255, 255, 255) // #80FFFFFF
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255) // #A0FFFFFF
            style = Paint.Style.FILL
        }

        for ((a, b) in connections) {
            val posA = screenPos(a) ?: continue
            val posB = screenPos(b) ?: continue
            canvas.drawLine(posA.first, posA.second, posB.first, posB.second, linePaint)
        }
        for (idx in torsoIndices) {
            val pos = screenPos(idx) ?: continue
            canvas.drawCircle(pos.first, pos.second, 10f, dotPaint)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Setup Mode Drawing
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Draw setup-mode guidance overlay:
     * - Only tracked joints and their connecting bones
     * - Large, coloured angle labels readable from 2-3 metres
     * - Direction arrow (↑ / ↓) when joint is outside range
     *
     * Colours: GREEN #00E676 / YELLOW #FFD54F / RED #FF5252
     */
    private val smoothedSetupRadius = mutableMapOf<Int, Float>()
    private var smoothedBodyScale: Float = 1f
    /**
     * Computes a body-relative scale factor from torso height on screen.
     * Reference: ~500px torso = scale 1.0 (typical phone at arm's length).
     * Falls back to shoulder width or last known value when torso isn't visible.
     */
    private fun computeBodyScale(landmarks: List<SmoothedLandmark>): Float {
        fun effectiveIdx(raw: Int) = if (isFrontCamera) BodyLandmarks.getMirroredIndex(raw) else raw
        fun lm(raw: Int): SmoothedLandmark? {
            val eff = effectiveIdx(raw)
            return if (eff < landmarks.size) landmarks[eff].takeIf { it.visibility > 0.3f } else null
        }

        val lShoulder = lm(BodyLandmarks.LEFT_SHOULDER)
        val rShoulder = lm(BodyLandmarks.RIGHT_SHOULDER)
        val lHip = lm(BodyLandmarks.LEFT_HIP)
        val rHip = lm(BodyLandmarks.RIGHT_HIP)

        val shoulderYs = listOfNotNull(lShoulder?.let { toScreenY(it.y) }, rShoulder?.let { toScreenY(it.y) })
        val hipYs = listOfNotNull(lHip?.let { toScreenY(it.y) }, rHip?.let { toScreenY(it.y) })

        val refPx: Float? = if (shoulderYs.isNotEmpty() && hipYs.isNotEmpty()) {
            kotlin.math.abs(hipYs.average().toFloat() - shoulderYs.average().toFloat())
        } else if (lShoulder != null && rShoulder != null) {
            val sx = toScreenX(lShoulder.x) - toScreenX(rShoulder.x)
            val sy = toScreenY(lShoulder.y) - toScreenY(rShoulder.y)
            hypot(sx.toDouble(), sy.toDouble()).toFloat() * 1.8f
        } else null

        if (refPx == null || refPx < 30f) {
            return if (smoothedBodyScale > 0f) smoothedBodyScale else 1f
        }

        val raw = (refPx / TORSO_REF_PX).coerceIn(0.15f, 2.5f)
        smoothedBodyScale = if (smoothedBodyScale <= 0f) raw
            else smoothedBodyScale + (raw - smoothedBodyScale) * 0.12f
        return smoothedBodyScale
    }

    /**
     * Asymmetric dampened scale: allows more aggressive shrinking when body is far,
     * while keeping conservative growth when body is close.
     * shrinkFactor/growFactor: 0.0 = no scaling, 1.0 = full linear scaling.
     */
    private fun dampenedScale(bodyScale: Float, shrinkFactor: Float, growFactor: Float, minScale: Float = 0f): Float {
        val delta = bodyScale - 1f
        val result = 1f + delta * (if (delta < 0f) shrinkFactor else growFactor)
        return if (minScale > 0f) result.coerceAtLeast(minScale) else result
    }

    private fun drawSetupGuidance(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        if (setupJointGuidances.isEmpty()) return

        fun effectiveIdx(raw: Int): Int =
            if (isFrontCamera) BodyLandmarks.getMirroredIndex(raw) else raw

        val indexToScreenPos = mutableMapOf<Int, Pair<Float, Float>>()

        for (guidance in setupJointGuidances) {
            val rawIdx = JointLandmarkMapping.jointToLandmark(guidance.jointCode) ?: continue
            val effIdx = effectiveIdx(rawIdx)
            if (effIdx >= landmarks.size) continue
            val lm = landmarks[effIdx]
            if (!lm.isVisible(0.15f)) continue
            indexToScreenPos[rawIdx] = Pair(toScreenX(lm.x), toScreenY(lm.y))

            for (adjRaw in getAdjacentLandmarkIndices(guidance.jointCode)) {
                val adjEff = effectiveIdx(adjRaw)
                if (adjEff < landmarks.size) {
                    val adjLm = landmarks[adjEff]
                    if (adjLm.isVisible(0.15f)) {
                        indexToScreenPos[adjRaw] = Pair(toScreenX(adjLm.x), toScreenY(adjLm.y))
                    }
                }
            }
        }

        val density = cachedDensity
        val jointRadiusPx = mutableMapOf<Int, Float>()

        for (guidance in setupJointGuidances) {
            val idx = JointLandmarkMapping.jointToLandmark(guidance.jointCode) ?: continue
            val centerPos = indexToScreenPos[idx] ?: continue
            val connectedPositions = getAdjacentLandmarkIndices(guidance.jointCode)
                .mapNotNull { indexToScreenPos[it] }

            val longestEdgePx = connectedPositions
                .maxOfOrNull { p -> hypot((p.first - centerPos.first).toDouble(), (p.second - centerPos.second).toDouble()).toFloat() }
                ?: 0f
            val scale = if (guidance.isPrimary) 0.05f else 0.035f
            val rawR = (longestEdgePx * scale).coerceIn(6f, 200f)
            val prev = smoothedSetupRadius[idx]
            val smoothR = if (prev == null || prev <= 0f) rawR else prev + (rawR - prev) * 0.25f
            smoothedSetupRadius[idx] = smoothR
            jointRadiusPx[idx] = smoothR
        }

        // ── Pass 1: draw connecting bone lines ────────────────────────────
        for (guidance in setupJointGuidances) {
            val centerIdx = JointLandmarkMapping.jointToLandmark(guidance.jointCode) ?: continue
            val centerPos = indexToScreenPos[centerIdx] ?: continue
            val lineColor = guidanceLevelToColor(guidance.level)
            setupLinePaint.color = lineColor
            setupLinePaint.alpha = 200

            for (adjIdx in getAdjacentLandmarkIndices(guidance.jointCode)) {
                val adjPos = indexToScreenPos[adjIdx] ?: continue
                canvas.drawLine(centerPos.first, centerPos.second,
                    adjPos.first, adjPos.second, setupLinePaint)
            }
        }

        // ── Pass 2: draw joint circles ────────────────────────────────────
        for (guidance in setupJointGuidances) {
            val idx = JointLandmarkMapping.jointToLandmark(guidance.jointCode) ?: continue
            val pos = indexToScreenPos[idx] ?: continue
            val color = guidanceLevelToColor(guidance.level)
            val radiusPx = jointRadiusPx[idx] ?: 20f

            setupJointPaint.color = color
            setupJointPaint.alpha = 100
            canvas.drawCircle(pos.first, pos.second, radiusPx, setupJointPaint)

            setupJointStrokePaint.color = color
            setupJointStrokePaint.alpha = 230
            canvas.drawCircle(pos.first, pos.second, radiusPx, setupJointStrokePaint)
        }

        // ── Pass 3: draw angle labels + direction arrows ──────────────────
        for (guidance in setupJointGuidances) {
            val idx = JointLandmarkMapping.jointToLandmark(guidance.jointCode) ?: continue
            val pos = indexToScreenPos[idx] ?: continue
            val color = guidanceLevelToColor(guidance.level)

            val angleText = "%.0f°".format(guidance.currentAngle)
            val arrowChar = when (guidance.direction) {
                Direction.RAISE -> " ↑"
                Direction.LOWER -> " ↓"
                null -> if (guidance.level == GuidanceLevel.GREEN) " ✓" else ""
            }
            val labelText = angleText + arrowChar

            val textWidth = setupTextPaint.measureText(labelText)
            val textHeight = setupTextPaint.textSize
            val padH = 10f * density
            val padV = 6f * density

            val radiusPx = jointRadiusPx[idx] ?: 20f
            val labelX = pos.first
            val labelY = pos.second - radiusPx - textHeight * 0.3f

            val bgLeft   = labelX - textWidth / 2f - padH
            val bgRight  = labelX + textWidth / 2f + padH
            val bgTop    = labelY - textHeight - padV
            val bgBottom = labelY + padV
            val bgRadius = 12f * density
            canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, bgRadius, bgRadius, setupTextBgPaint)

            setupTextPaint.color = color
            canvas.drawText(labelText, labelX, labelY, setupTextPaint)
        }

        setupJointPaint.alpha = 255
        setupJointStrokePaint.alpha = 255
        setupLinePaint.alpha = 255
        setupTextPaint.color = Color.WHITE
    }

    private var smoothedDebugRadius: Float = 0f

    /**
     * Debug mode drawing: minimal overlay with just the tested joint.
     * - Circle on the vertex (center joint)
     * - Thin lines from vertex to the two angle endpoints
     * - Small dots at the two endpoints
     * - Large angle label above the vertex
     */
    private fun drawDebugJoint(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        val vertexRaw = debugVertexIndex
        if (vertexRaw < 0) return

        fun effectiveIdx(raw: Int): Int =
            if (isFrontCamera) BodyLandmarks.getMirroredIndex(raw) else raw

        fun screenPos(rawIdx: Int): Pair<Float, Float>? {
            val eff = effectiveIdx(rawIdx)
            if (eff >= landmarks.size) return null
            val lm = landmarks[eff]
            if (!lm.isVisible(0.15f)) return null
            return Pair(toScreenX(lm.x), toScreenY(lm.y))
        }

        val vertexPos = screenPos(vertexRaw) ?: return
        val density = cachedDensity
        val accentColor = Color.parseColor("#64B5F6")
        val endpointPositions = debugEndpointIndices.mapNotNull { screenPos(it) }

        // ── Adaptive radius from longest edge ────────────────────────────
        val longestEdgePx = endpointPositions
            .maxOfOrNull { p -> hypot((p.first - vertexPos.first).toDouble(), (p.second - vertexPos.second).toDouble()).toFloat() }
            ?: 0f
        val rawRadius = (longestEdgePx * 0.05f).coerceIn(6f, 200f)
        smoothedDebugRadius = if (smoothedDebugRadius <= 0f) rawRadius
            else smoothedDebugRadius + (rawRadius - smoothedDebugRadius) * 0.25f
        val circleRadius = smoothedDebugRadius
        val dotRadius = circleRadius * 0.25f

        // ── 1. Thin lines from vertex to endpoints ──────────────────────
        for (epPos in endpointPositions) {
            debugLinePaint.color = Color.argb(96, 100, 181, 246)
            canvas.drawLine(vertexPos.first, vertexPos.second, epPos.first, epPos.second, debugLinePaint)
        }

        // ── 2. Small dots at endpoints ──────────────────────────────────
        for (epPos in endpointPositions) {
            debugDotPaint.color = Color.parseColor("#B0FFFFFF")
            canvas.drawCircle(epPos.first, epPos.second, dotRadius, debugDotPaint)
        }

        // ── 3. Circle on vertex joint ───────────────────────────────────
        debugCircleFillPaint.color = accentColor
        debugCircleFillPaint.alpha = 80
        canvas.drawCircle(vertexPos.first, vertexPos.second, circleRadius, debugCircleFillPaint)

        debugCircleStrokePaint.color = accentColor
        debugCircleStrokePaint.alpha = 220
        canvas.drawCircle(vertexPos.first, vertexPos.second, circleRadius, debugCircleStrokePaint)

        // ── 4. Angle label above vertex ─────────────────────────────────
        val angleVal = debugAngleValue
        if (angleVal != null) {
            val labelText = "%.1f°".format(angleVal)
            val textWidth = debugAngleTextPaint.measureText(labelText)
            val textHeight = debugAngleTextPaint.textSize
            val padH = 10f * density
            val padV = 6f * density

            val labelX = vertexPos.first
            val labelY = vertexPos.second - circleRadius - textHeight * 0.3f

            val bgLeft   = labelX - textWidth / 2f - padH
            val bgRight  = labelX + textWidth / 2f + padH
            val bgTop    = labelY - textHeight - padV
            val bgBottom = labelY + padV
            val bgRadius = 12f * density
            canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, bgRadius, bgRadius, debugAngleBgPaint)

            debugAngleTextPaint.color = Color.WHITE
            canvas.drawText(labelText, labelX, labelY, debugAngleTextPaint)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Camera Detection Mode Drawing
    // ──────────────────────────────────────────────────────────────────────

    private fun drawCameraDetection(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        val result = camDetectResult ?: return
        val density = cachedDensity

        fun effectiveIdx(raw: Int): Int =
            if (isFrontCamera) BodyLandmarks.getMirroredIndex(raw) else raw

        fun screenPos(rawIdx: Int): Pair<Float, Float>? {
            val eff = effectiveIdx(rawIdx)
            if (eff >= landmarks.size) return null
            val lm = landmarks[eff]
            if (!lm.isVisible(0.25f)) return null
            return Pair(toScreenX(lm.x), toScreenY(lm.y))
        }

        fun rawLandmark(rawIdx: Int): SmoothedLandmark? {
            val eff = effectiveIdx(rawIdx)
            return if (eff < landmarks.size) landmarks[eff] else null
        }

        val leftShoulder = screenPos(BodyLandmarks.LEFT_SHOULDER)
        val rightShoulder = screenPos(BodyLandmarks.RIGHT_SHOULDER)
        val leftHip = screenPos(BodyLandmarks.LEFT_HIP)
        val rightHip = screenPos(BodyLandmarks.RIGHT_HIP)
        val leftKnee = screenPos(BodyLandmarks.LEFT_KNEE)
        val rightKnee = screenPos(BodyLandmarks.RIGHT_KNEE)
        val nose = screenPos(BodyLandmarks.NOSE)

        val dotRadius = 10f * density
        val smallDotRadius = 7f * density

        // Left side landmarks (cyan)
        listOf(leftShoulder, leftHip, leftKnee).forEach { pos ->
            if (pos != null) canvas.drawCircle(pos.first, pos.second, dotRadius, camLeftDotPaint)
        }
        // Right side landmarks (orange)
        listOf(rightShoulder, rightHip, rightKnee).forEach { pos ->
            if (pos != null) canvas.drawCircle(pos.first, pos.second, dotRadius, camRightDotPaint)
        }

        // Nose (white, size scaled by visibility)
        if (nose != null) {
            val noseVis = rawLandmark(BodyLandmarks.NOSE)?.visibility ?: 0f
            val noseRadius = smallDotRadius * (0.4f + 0.6f * noseVis.coerceIn(0f, 1f))
            camNoseDotPaint.alpha = (100 + 155 * noseVis.coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(nose.first, nose.second, noseRadius, camNoseDotPaint)
        }

        // Shoulder line
        if (leftShoulder != null && rightShoulder != null) {
            val matchColor = if (camDetectMatchesExpected) Color.parseColor("#00E676") else Color.parseColor("#FF5252")
            camShoulderLinePaint.color = matchColor
            camShoulderLinePaint.strokeWidth = (3f + 5f * result.confidence) * density
            canvas.drawLine(leftShoulder.first, leftShoulder.second,
                rightShoulder.first, rightShoulder.second, camShoulderLinePaint)
        }

        // Z-depth bars next to shoulders
        val leftShoulderLm = rawLandmark(BodyLandmarks.LEFT_SHOULDER)
        val rightShoulderLm = rawLandmark(BodyLandmarks.RIGHT_SHOULDER)
        if (leftShoulder != null && rightShoulder != null && leftShoulderLm != null && rightShoulderLm != null) {
            val maxBarH = 40f * density
            val barW = 6f * density
            val offset = 16f * density
            val maxZ = maxOf(kotlin.math.abs(leftShoulderLm.z), kotlin.math.abs(rightShoulderLm.z), 0.01f)

            // Left bar: taller = closer (smaller Z)
            val leftCloseness = (1f - (leftShoulderLm.z / maxZ).coerceIn(-1f, 1f)) / 2f
            val leftBarH = maxBarH * leftCloseness.coerceIn(0.1f, 1f)
            camDepthBarPaint.color = Color.parseColor("#00E5FF")
            camDepthBarPaint.alpha = 180
            canvas.drawRoundRect(
                leftShoulder.first - offset - barW, leftShoulder.second - leftBarH / 2f,
                leftShoulder.first - offset, leftShoulder.second + leftBarH / 2f,
                barW / 2f, barW / 2f, camDepthBarPaint
            )

            // Right bar
            val rightCloseness = (1f - (rightShoulderLm.z / maxZ).coerceIn(-1f, 1f)) / 2f
            val rightBarH = maxBarH * rightCloseness.coerceIn(0.1f, 1f)
            camDepthBarPaint.color = Color.parseColor("#FF9100")
            camDepthBarPaint.alpha = 180
            canvas.drawRoundRect(
                rightShoulder.first + offset, rightShoulder.second - rightBarH / 2f,
                rightShoulder.first + offset + barW, rightShoulder.second + rightBarH / 2f,
                barW / 2f, barW / 2f, camDepthBarPaint
            )
        }

        // Text labels on canvas
        val canvasW = canvas.width.toFloat()
        val centerX = canvasW / 2f

        val posText = result.position.name.replace("_", " ")
        val facingText = result.facingDirection.name.replace("_", " ")
        val confText = "Confidence: %.0f%%".format(result.confidence * 100)
        val closerText = "Closer: ${result.closerSide.name}"

        val matchColor = if (camDetectMatchesExpected) Color.parseColor("#00E676") else Color.parseColor("#FF5252")

        // Position label (large)
        camLabelPaint.textSize = 40f * density
        camLabelPaint.color = matchColor
        val posY = 160f * density
        val posTextW = camLabelPaint.measureText(posText)
        canvas.drawRoundRect(
            centerX - posTextW / 2f - 12 * density, posY - 36 * density,
            centerX + posTextW / 2f + 12 * density, posY + 10 * density,
            8 * density, 8 * density, camBgPaint
        )
        canvas.drawText(posText, centerX, posY, camLabelPaint)

        // Facing label
        camSubLabelPaint.textSize = 24f * density
        val facingY = posY + 36 * density
        val facingTextW = camSubLabelPaint.measureText(facingText)
        canvas.drawRoundRect(
            centerX - facingTextW / 2f - 10 * density, facingY - 22 * density,
            centerX + facingTextW / 2f + 10 * density, facingY + 6 * density,
            6 * density, 6 * density, camBgPaint
        )
        canvas.drawText(facingText, centerX, facingY, camSubLabelPaint)

        // Confidence + closer side
        camSubLabelPaint.textSize = 18f * density
        val confY = facingY + 28 * density
        canvas.drawText("$confText  |  $closerText", centerX, confY, camSubLabelPaint)
    }

    /** Return the MediaPipe landmark indices adjacent to the joint (for bone drawing). */
    private fun getAdjacentLandmarkIndices(jointCode: String): List<Int> {
        return when (jointCode.lowercase()) {
            "left_elbow"  -> listOf(BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.LEFT_WRIST)
            "right_elbow" -> listOf(BodyLandmarks.RIGHT_SHOULDER, BodyLandmarks.RIGHT_WRIST)
            "left_shoulder" -> listOf(BodyLandmarks.LEFT_ELBOW, BodyLandmarks.LEFT_HIP)
            "right_shoulder" -> listOf(BodyLandmarks.RIGHT_ELBOW, BodyLandmarks.RIGHT_HIP)
            "left_wrist"  -> listOf(BodyLandmarks.LEFT_ELBOW)
            "right_wrist" -> listOf(BodyLandmarks.RIGHT_ELBOW)
            "left_knee"   -> listOf(BodyLandmarks.LEFT_HIP, BodyLandmarks.LEFT_ANKLE)
            "right_knee"  -> listOf(BodyLandmarks.RIGHT_HIP, BodyLandmarks.RIGHT_ANKLE)
            "left_hip"    -> listOf(BodyLandmarks.LEFT_KNEE, BodyLandmarks.LEFT_SHOULDER)
            "right_hip"   -> listOf(BodyLandmarks.RIGHT_KNEE, BodyLandmarks.RIGHT_SHOULDER)
            "left_ankle"  -> listOf(BodyLandmarks.LEFT_KNEE, BodyLandmarks.LEFT_HEEL)
            "right_ankle" -> listOf(BodyLandmarks.RIGHT_KNEE, BodyLandmarks.RIGHT_HEEL)
            "spine"       -> listOf(BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP)
            else -> emptyList()
        }
    }

    private fun guidanceLevelToColor(level: GuidanceLevel): Int = when (level) {
        GuidanceLevel.GREEN  -> Color.parseColor("#00E676")
        GuidanceLevel.YELLOW -> Color.parseColor("#FFD54F")
        GuidanceLevel.RED    -> Color.parseColor("#FF5252")
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
                            toScreenX(start.x),
                            toScreenY(start.y),
                            toScreenX(end.x),
                            toScreenY(end.y),
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
                com.trainingvalidator.poc.training.engine.JointZone.TOO_HIGH -> currentAngle - upMax
                com.trainingvalidator.poc.training.engine.JointZone.TOO_LOW -> downMin - currentAngle
                else -> 0.0
            }
            // Quick ramp to danger color (within 10°)
            val errorRatio = (distanceOutside / 10.0).coerceIn(0.0, 1.0)
            return interpolateColor(padColor, dangerColor, errorRatio.toFloat())
        }
        
        // For valid zones, use unified gradient approach
        return when (arrowInfo.zone) {
            com.trainingvalidator.poc.training.engine.JointZone.DOWN_ZONE -> {
                val rangeSize = downMax - downMin
                if (rangeSize <= 0) return perfectColor
                
                val center = (downMin + downMax) / 2
                val distFromCenter = kotlin.math.abs(currentAngle - center)
                val normalizedDist = (distFromCenter / (rangeSize / 2)).coerceIn(0.0, 1.0)
                val isOuterSide = currentAngle < center
                
                getColorForNormalizedPositionLegacy(normalizedDist, isOuterSide)
            }
            
            com.trainingvalidator.poc.training.engine.JointZone.UP_ZONE -> {
                val rangeSize = upMax - upMin
                if (rangeSize <= 0) return perfectColor
                
                val center = (upMin + upMax) / 2
                val distFromCenter = kotlin.math.abs(currentAngle - center)
                val normalizedDist = (distFromCenter / (rangeSize / 2)).coerceIn(0.0, 1.0)
                val isOuterSide = currentAngle > center
                
                getColorForNormalizedPositionLegacy(normalizedDist, isOuterSide)
            }
            
            com.trainingvalidator.poc.training.engine.JointZone.TRANSITION -> transitionColor
            
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
                val x = toScreenX(landmark.x)
                val y = toScreenY(landmark.y)
                
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
                    val x = toScreenX(landmark.x)
                    val y = toScreenY(landmark.y)
                    
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
    private fun drawGlowingJoints(canvas: Canvas, landmarks: List<SmoothedLandmark>, bodyScale: Float = 1f) {
        if (jointStateInfos.isEmpty()) return
        
        // Animate glow intensity with flow phase
        val baseGlowIntensity = 0.4f + 0.3f * sin(flowPhase * Math.PI * 2).toFloat()
        
        for ((jointCode, stateInfo) in jointStateInfos) {
            // Get the center landmark for this joint
            // Use bilateral-aware joint code for landmark lookup
            val landmarkJointCode = getEffectiveLandmarkJointCode(jointCode)
            val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(landmarkJointCode)
            if (angleLandmarks.size != 3) continue
            
            val centerIdx = angleLandmarks[1]
            val effectiveCenterIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(centerIdx) else centerIdx
            
            if (effectiveCenterIdx >= landmarks.size) continue
            val landmark = landmarks[effectiveCenterIdx]
            if (landmark.visibility < visibilityThreshold) continue
            
            // Calculate screen coordinates
            val x = toScreenX(landmark.x)
            val y = toScreenY(landmark.y)
            
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
            
            val (baseGlowRadius, baseInnerRadius, baseGlowAlpha) = if (stateInfo.isPrimary) {
                Triple(28f, 10f, (baseGlowIntensity * 0.7f * 255).toInt())
            } else {
                Triple(20f, 7f, (baseGlowIntensity * 0.5f * 255).toInt())
            }

            val glowScale = dampenedScale(bodyScale, 0.78f, 0.39f, minScale = 0.55f)
            val glowRadius = baseGlowRadius * sizeMultiplier * glowScale * 1.3f
            val innerRadius = baseInnerRadius * sizeMultiplier * glowScale * 1.3f
            
            // Alpha also increases with severity for more emphasis
            val finalGlowAlpha = when (stateInfo.state) {
                JointState.DANGER -> (baseGlowAlpha * 1.4f).toInt().coerceAtMost(255)
                JointState.WARNING -> (baseGlowAlpha * 1.3f).toInt().coerceAtMost(255)
                JointState.PAD -> (baseGlowAlpha * 1.1f).toInt().coerceAtMost(255)
                else -> baseGlowAlpha
            }
            
            // 1. Draw outer glow (blurred, larger circle)
            // Use cached BlurMaskFilters to avoid per-frame allocations
            val blurRadius = glowRadius * 0.6f
            val blurFilter = when {
                blurRadius <= 10f -> blurFilterSmall
                blurRadius <= 15f -> blurFilterMedium
                else -> blurFilterLarge
            }
            glowPaint.color = stateColor
            glowPaint.alpha = finalGlowAlpha
            glowPaint.maskFilter = blurFilter
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
     * Get the effective joint code for landmark lookup, accounting for bilateral flipping.
     * 
     * Bilateral flip mirrors the JOINT CODE so we look up landmarks on the opposite side.
     * E.g., config says "right_elbow" but user is doing left side → look up "left_elbow" landmarks.
     * Front camera mirroring is applied separately at the INDEX level.
     */
    private fun getEffectiveLandmarkJointCode(jointCode: String): String {
        return if (isBilateralFlipped) mirrorJointCode(jointCode) else jointCode
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
    private val smoothedArcRadiusPx = mutableMapOf<String, Float>()

    private fun drawArcRangeIndicatorsNew(
        canvas: Canvas,
        landmarks: List<SmoothedLandmark>,
        bodyScale: Float
    ) {
        val density = cachedDensity

        for ((jointCode, stateInfo) in jointStateInfos) {
            if (!stateInfo.isPrimary) continue

            val landmarkJointCode = getEffectiveLandmarkJointCode(jointCode)
            val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(landmarkJointCode)
            if (angleLandmarks.size != 3) continue

            val (upperIdx, centerIdx, lowerIdx) = angleLandmarks
            val effUpper = if (isFrontCamera) BodyLandmarks.getMirroredIndex(upperIdx) else upperIdx
            val effCenter = if (isFrontCamera) BodyLandmarks.getMirroredIndex(centerIdx) else centerIdx
            val effLower = if (isFrontCamera) BodyLandmarks.getMirroredIndex(lowerIdx) else lowerIdx

            if (effCenter >= landmarks.size) continue
            val centerLm = landmarks[effCenter]
            if (centerLm.visibility < visibilityThreshold) continue

            val centerX = toScreenX(centerLm.x)
            val centerY = toScreenY(centerLm.y)

            // Compute arc radius from longest adjacent limb segment (pixels).
            // Using max avoids shrinking when one limb is compressed by perspective.
            val limbs = mutableListOf<Float>()
            if (effUpper < landmarks.size && landmarks[effUpper].visibility > 0.2f) {
                val dx = toScreenX(landmarks[effUpper].x) - centerX
                val dy = toScreenY(landmarks[effUpper].y) - centerY
                limbs.add(hypot(dx, dy))
            }
            if (effLower < landmarks.size && landmarks[effLower].visibility > 0.2f) {
                val dx = toScreenX(landmarks[effLower].x) - centerX
                val dy = toScreenY(landmarks[effLower].y) - centerY
                limbs.add(hypot(dx, dy))
            }

            val refLimbPx = limbs.maxOrNull() ?: continue
            // Arc: only 20% variation between smallest and largest state
            val centerPx = 95f * (cachedDensity / 2.5f).coerceIn(0.8f, 1.4f)
            val t = ((refLimbPx - 50f) / 280f).coerceIn(0f, 1f)
            val factor = 0.9f + 0.2f * t
            val rawRadiusPx = (centerPx * factor).coerceIn(40f, 180f)

            val prev = smoothedArcRadiusPx[jointCode] ?: rawRadiusPx
            val smoothed = prev + (rawRadiusPx - prev) * 0.2f
            smoothedArcRadiusPx[jointCode] = smoothed

            val radiusDp = smoothed / density
            val strokeRatio = arcConfig.strokeWidthDp / arcConfig.radiusDp
            val indicatorRatio = arcConfig.indicatorRadiusDp / arcConfig.radiusDp

            val scaledConfig = ArcConfig(
                radiusDp = radiusDp,
                strokeWidthDp = radiusDp * strokeRatio,
                indicatorRadiusDp = radiusDp * indicatorRatio,
                showCurrentIndicator = arcConfig.showCurrentIndicator,
                showRangeLabels = arcConfig.showRangeLabels,
                animationEnabled = arcConfig.animationEnabled,
                showOnlyOnError = arcConfig.showOnlyOnError,
                showOnlyPrimary = arcConfig.showOnlyPrimary,
                arcOpacity = arcConfig.arcOpacity
            )

            val arcData = ArcRangeData.fromStateInfo(centerX, centerY, stateInfo)
            if (!arcRangeIndicator.shouldShowArc(arcData, scaledConfig)) continue
            arcRangeIndicator.drawWithStateRanges(canvas, arcData, scaledConfig, density)
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
        landmarks: List<SmoothedLandmark>,
        bodyScale: Float
    ) {
        val density = cachedDensity

        for ((jointCode, stateInfo) in jointStateInfos) {
            if (!stateInfo.isPrimary) continue

            val currentAngle = stateInfo.currentAngle

            val landmarkJointCode = getEffectiveLandmarkJointCode(jointCode)
            val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(landmarkJointCode)
            if (angleLandmarks.size != 3) continue

            val (upperIdx, centerIdx, lowerIdx) = angleLandmarks

            val effectiveUpperIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(upperIdx) else upperIdx
            val effectiveCenterIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(centerIdx) else centerIdx
            val effectiveLowerIdx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(lowerIdx) else lowerIdx

            if (effectiveCenterIdx >= landmarks.size) continue
            val centerLm = landmarks[effectiveCenterIdx]
            if (centerLm.visibility < visibilityThreshold) continue

            if (effectiveUpperIdx >= landmarks.size) continue
            val upperLm = landmarks[effectiveUpperIdx]

            if (effectiveLowerIdx >= landmarks.size) continue
            val lowerLm = landmarks[effectiveLowerIdx]

            val centerX = toScreenX(centerLm.x)
            val centerY = toScreenY(centerLm.y)
            val upperX = toScreenX(upperLm.x)
            val upperY = toScreenY(upperLm.y)
            val lowerX = toScreenX(lowerLm.x)
            val lowerY = toScreenY(lowerLm.y)

            val upperDistance = kotlin.math.sqrt(
                (upperX - centerX) * (upperX - centerX) +
                (upperY - centerY) * (upperY - centerY)
            )
            val lowerDistance = kotlin.math.sqrt(
                (lowerX - centerX) * (lowerX - centerX) +
                (lowerY - centerY) * (lowerY - centerY)
            )

            val upperMaxLength = upperDistance * SettingsManager.getLineIndicatorUpperLengthRatio()
            val lowerMaxLength = lowerDistance * SettingsManager.getLineIndicatorLowerLengthRatio()

            val lineScale = dampenedScale(bodyScale, 1.0f, 0.52f, minScale = 0.45f)
            val baseStyle = LineStyleNew.forState(stateInfo, density)
            val style = LineStyleNew(
                strokeWidth = baseStyle.strokeWidth * lineScale * 1.3f,
                jointRadius = baseStyle.jointRadius * lineScale * 1.3f,
                pulseSpeed = baseStyle.pulseSpeed,
                showOuterRing = baseStyle.showOuterRing
            )
            
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
        val density = cachedDensity
        
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
            
            val centerX = toScreenX(centerLm.x)
            val centerY = toScreenY(centerLm.y)
            val upperX = toScreenX(upperLm.x)
            val upperY = toScreenY(upperLm.y)
            val lowerX = toScreenX(lowerLm.x)
            val lowerY = toScreenY(lowerLm.y)
            
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
        
        val x = toScreenX(landmark.x)
        val y = toScreenY(landmark.y) - 25f
        
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
     * Draw visual indicators for position errors.
     *
     * Shows up to two high-priority issues to improve guidance while keeping the
     * overlay readable.
     */
    private fun drawPositionErrors(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        val topErrors = getTopPositionErrors(limit = 2)
        if (topErrors.isEmpty()) return

        topErrors.forEach { error ->
            drawSinglePositionError(canvas, landmarks, error)
        }
    }
    
    /**
     * Get top-priority position errors in descending severity.
     */
    private fun getTopPositionErrors(limit: Int): List<PositionError> {
        if (positionErrors.isEmpty()) return emptyList()

        return positionErrors
            .sortedBy { severityRank(it.severity) }
            .take(limit.coerceAtLeast(1))
    }

    private fun severityRank(severity: CheckSeverity): Int = when (severity) {
        CheckSeverity.ERROR -> 0
        CheckSeverity.WARNING -> 1
        CheckSeverity.TIP -> 2
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
        
        val x1 = toScreenX(lm1.x)
        val y1 = toScreenY(lm1.y)
        val x2 = toScreenX(lm2.x)
        val y2 = toScreenY(lm2.y)
        
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
    }
}
