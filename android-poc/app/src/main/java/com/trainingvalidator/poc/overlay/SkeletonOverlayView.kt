package com.trainingvalidator.poc.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.engine.ArrowDirection
import com.trainingvalidator.poc.training.engine.JointArrowInfo
import com.trainingvalidator.poc.training.engine.JointZone
import com.trainingvalidator.poc.training.models.MovingSegment
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * SkeletonOverlayView - Skeleton drawing with zone-based arrow feedback
 * 
 * Arrow Logic:
 *   Zone        | Arrow Color | Arrow Direction | When Shown
 *   ------------|-------------|-----------------|------------------
 *   TOO_HIGH    | Red         | DOWN ↓          | Always
 *   UP_ZONE     | Green       | DOWN ↓          | From middle of range
 *   TRANSITION  | None        | -               | Never (moving)
 *   DOWN_ZONE   | Green       | UP ↑            | From middle of range
 *   TOO_LOW     | Red         | UP ↑            | Always
 */
class SkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12f
        private const val TRACKED_LANDMARK_STROKE_WIDTH = 18f
        private const val LINE_WIDTH = 8f
        private const val TRACKED_LINE_WIDTH = 10f
        private const val ERROR_LINE_WIDTH = 12f
        private const val TEXT_SIZE = 28f
        private const val VISIBILITY_THRESHOLD = 0.5f
        
        // Arrow settings - LARGE and visible
        private const val ARROW_SHAFT_WIDTH = 14f
        private const val ARROW_HEAD_LENGTH = 45f
        private const val ARROW_LENGTH = 100f
        private const val ARROW_OFFSET = 50f  // Distance from segment line
        
        // Colors
        private val COLOR_DEFAULT = Color.YELLOW
        private val COLOR_CORRECT = Color.parseColor("#00E676")     // Green
        private val COLOR_ERROR = Color.parseColor("#FF5252")       // Red
        private val COLOR_TRACKED = Color.parseColor("#2196F3")     // Blue
        private val COLOR_LINE_DEFAULT = Color.CYAN
        private val COLOR_LINE_TRACKED = Color.parseColor("#64B5F6") // Light Blue
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
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    
    private val arrowPaint = Paint().apply {
        color = COLOR_CORRECT
        strokeWidth = ARROW_SHAFT_WIDTH
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    
    private val arrowFillPaint = Paint().apply {
        color = COLOR_CORRECT
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val trackedPointPaint = Paint().apply {
        color = COLOR_TRACKED
        strokeWidth = TRACKED_LANDMARK_STROKE_WIDTH
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Current pose data
    private var landmarks: List<SmoothedLandmark>? = null
    private var jointAngles: JointAngles? = null
    
    // Training data
    private var trackedLandmarkIndices: Set<Int> = emptySet()
    private var isTrainingMode: Boolean = false
    
    // Moving segments for arrow drawing
    private var movingSegments: Map<String, MovingSegment> = emptyMap()
    
    // Arrow info for each joint (calculated by FormValidator)
    private var jointArrowInfos: Map<String, JointArrowInfo> = emptyMap()
    
    // Error state - which joints have errors
    private var errorJointCodes: Set<String> = emptySet()
    
    // Scaling
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f
    
    // Settings
    private var showAngles = true
    private var showArrows = true

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
        arrowInfos: Map<String, JointArrowInfo>
    ) {
        landmarks = smoothedLandmarks
        imageWidth = inputImageWidth
        imageHeight = inputImageHeight
        jointAngles = angles
        jointArrowInfos = arrowInfos
        scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        
        // Collect error joints (TOO_HIGH or TOO_LOW zones)
        errorJointCodes = arrowInfos.filter { it.value.isError }.keys
        
        invalidate()
    }
    
    /**
     * Set training mode with tracked landmarks and moving segments
     */
    fun setTrainingMode(
        enabled: Boolean, 
        trackedIndices: Set<Int> = emptySet(),
        segments: Map<String, MovingSegment> = emptyMap()
    ) {
        isTrainingMode = enabled
        trackedLandmarkIndices = trackedIndices
        movingSegments = segments
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
    
    fun setShowArrows(show: Boolean) {
        showArrows = show
        invalidate()
    }
    
    fun setShowLowVisibility(show: Boolean) {}

    fun clear() {
        landmarks = null
        jointAngles = null
        jointArrowInfos = emptyMap()
        errorJointCodes = emptySet()
        movingSegments = emptyMap()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentLandmarks = landmarks ?: return
        if (currentLandmarks.isEmpty()) return

        // Draw connections first (behind points)
        drawConnections(canvas, currentLandmarks)
        
        // Draw landmark points
        drawLandmarks(canvas, currentLandmarks)
        
        // Draw arrows on moving segments based on zone
        if (showArrows && isTrainingMode) {
            drawZoneBasedArrows(canvas, currentLandmarks)
        }
        
        // Draw angles if enabled
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
                        
                        // Check if this connection is part of an error joint
                        val startJointCode = landmarkIndexToJointCode(startIdx)
                        val endJointCode = landmarkIndexToJointCode(endIdx)
                        val hasError = startJointCode in errorJointCodes || endJointCode in errorJointCodes
                        
                        // Check if tracked
                        val isTrackedConnection = isTrainingMode && 
                            (startIdx in trackedLandmarkIndices || endIdx in trackedLandmarkIndices)
                        
                        // Set color and width based on error state
                        val (color, width) = when {
                            hasError -> COLOR_ERROR to ERROR_LINE_WIDTH
                            isTrackedConnection -> COLOR_LINE_TRACKED to TRACKED_LINE_WIDTH
                            else -> COLOR_LINE_DEFAULT to LINE_WIDTH
                        }
                        
                        linePaint.color = color
                        linePaint.strokeWidth = width
                        
                        canvas.drawLine(
                            start.x * imageWidth * scaleFactor,
                            start.y * imageHeight * scaleFactor,
                            end.x * imageWidth * scaleFactor,
                            end.y * imageHeight * scaleFactor,
                            linePaint
                        )
                    }
                }
            }
        }
    }

    private fun drawLandmarks(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        landmarks.forEachIndexed { index, landmark ->
            if (landmark.visibility >= VISIBILITY_THRESHOLD) {
                val x = landmark.x * imageWidth * scaleFactor
                val y = landmark.y * imageHeight * scaleFactor
                
                val jointCode = landmarkIndexToJointCode(index)
                val hasError = jointCode in errorJointCodes
                
                val color = when {
                    hasError -> COLOR_ERROR
                    index in trackedLandmarkIndices -> COLOR_TRACKED
                    else -> COLOR_DEFAULT
                }
                
                val radius = if (index in trackedLandmarkIndices) {
                    TRACKED_LANDMARK_STROKE_WIDTH / 2
                } else {
                    LANDMARK_STROKE_WIDTH / 2
                }
                
                pointPaint.color = color
                canvas.drawCircle(x, y, radius, pointPaint)
                
                // Draw outer ring for tracked joints
                if (isTrainingMode && index in trackedLandmarkIndices) {
                    trackedPointPaint.style = Paint.Style.STROKE
                    trackedPointPaint.strokeWidth = 3f
                    trackedPointPaint.color = color
                    canvas.drawCircle(x, y, radius + 4f, trackedPointPaint)
                    trackedPointPaint.style = Paint.Style.FILL
                }
            }
        }
    }
    
    /**
     * Draw arrows based on current zone
     * 
     * Zone logic:
     *   TOO_HIGH    → Red arrow DOWN (go lower to enter UP zone)
     *   UP_ZONE     → Green arrow DOWN (continue to DOWN zone)
     *   TRANSITION  → No arrow (moving, let them continue)
     *   DOWN_ZONE   → Green arrow UP (go back to UP zone)
     *   TOO_LOW     → Red arrow UP (go higher to enter DOWN zone)
     */
    private fun drawZoneBasedArrows(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        for ((jointCode, arrowInfo) in jointArrowInfos) {
            // Skip if no arrow needed
            if (arrowInfo.arrowDirection == ArrowDirection.NONE) continue
            if (!arrowInfo.shouldShowArrow()) continue
            
            // Get moving segment
            val segment = movingSegments[jointCode] ?: continue
            
            val fromIdx = jointCodeToLandmarkIndex(segment.from) ?: continue
            val toIdx = jointCodeToLandmarkIndex(segment.to) ?: continue
            
            if (fromIdx >= landmarks.size || toIdx >= landmarks.size) continue
            
            val fromLandmark = landmarks[fromIdx]
            val toLandmark = landmarks[toIdx]
            
            if (fromLandmark.visibility < VISIBILITY_THRESHOLD || 
                toLandmark.visibility < VISIBILITY_THRESHOLD) continue
            
            // Get segment coordinates
            val fromX = fromLandmark.x * imageWidth * scaleFactor
            val fromY = fromLandmark.y * imageHeight * scaleFactor
            val toX = toLandmark.x * imageWidth * scaleFactor
            val toY = toLandmark.y * imageHeight * scaleFactor
            
            // Calculate midpoint of segment
            val midX = (fromX + toX) / 2
            val midY = (fromY + toY) / 2
            
            // Calculate segment angle (from → to)
            val segmentAngle = atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())
            
            // Calculate perpendicular offset (place arrow beside the segment)
            val perpAngle = segmentAngle + Math.PI / 2
            val offsetX = ARROW_OFFSET * cos(perpAngle).toFloat()
            val offsetY = ARROW_OFFSET * sin(perpAngle).toFloat()
            
            // Arrow position (beside the segment)
            val arrowX = midX + offsetX
            val arrowY = midY + offsetY
            
            // Determine arrow direction based on zone
            // DOWN = angle decreases = arrow in direction of segment (from → to)
            // UP = angle increases = arrow opposite to segment (to → from)
            val arrowAngle = when (arrowInfo.arrowDirection) {
                ArrowDirection.DOWN -> segmentAngle  // Same direction as segment
                ArrowDirection.UP -> segmentAngle + Math.PI  // Opposite direction
                ArrowDirection.NONE -> continue
            }
            
            // Color based on zone
            val color = if (arrowInfo.isError) COLOR_ERROR else COLOR_CORRECT
            
            // Draw the arrow
            drawLargeArrow(canvas, arrowX, arrowY, arrowAngle.toFloat(), color)
        }
    }
    
    /**
     * Draw a large, visible arrow
     */
    private fun drawLargeArrow(canvas: Canvas, x: Float, y: Float, angle: Float, color: Int) {
        arrowPaint.color = color
        arrowFillPaint.color = color
        
        // Calculate arrow end point
        val endX = x + ARROW_LENGTH * cos(angle)
        val endY = y + ARROW_LENGTH * sin(angle)
        
        // Draw arrow shaft (thick line)
        arrowPaint.strokeWidth = ARROW_SHAFT_WIDTH
        canvas.drawLine(x, y, endX, endY, arrowPaint)
        
        // Draw arrow head (filled triangle)
        val headAngle1 = angle + Math.toRadians(150.0).toFloat()
        val headAngle2 = angle - Math.toRadians(150.0).toFloat()
        
        val head1X = endX + ARROW_HEAD_LENGTH * cos(headAngle1)
        val head1Y = endY + ARROW_HEAD_LENGTH * sin(headAngle1)
        val head2X = endX + ARROW_HEAD_LENGTH * cos(headAngle2)
        val head2Y = endY + ARROW_HEAD_LENGTH * sin(headAngle2)
        
        val path = Path().apply {
            moveTo(endX, endY)
            lineTo(head1X, head1Y)
            lineTo(head2X, head2Y)
            close()
        }
        canvas.drawPath(path, arrowFillPaint)
        
        // Add outline to arrow head
        arrowPaint.strokeWidth = 3f
        canvas.drawPath(path, arrowPaint)
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

    private fun drawAngles(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        val angles = jointAngles ?: return
        
        drawAngleAt(canvas, landmarks, 13, angles.leftElbow, "left_elbow")
        drawAngleAt(canvas, landmarks, 14, angles.rightElbow, "right_elbow")
        drawAngleAt(canvas, landmarks, 11, angles.leftShoulder, "left_shoulder")
        drawAngleAt(canvas, landmarks, 12, angles.rightShoulder, "right_shoulder")
        drawAngleAt(canvas, landmarks, 23, angles.leftHip, "left_hip")
        drawAngleAt(canvas, landmarks, 24, angles.rightHip, "right_hip")
        drawAngleAt(canvas, landmarks, 25, angles.leftKnee, "left_knee")
        drawAngleAt(canvas, landmarks, 26, angles.rightKnee, "right_knee")
    }
    
    private fun drawAngleAt(
        canvas: Canvas, 
        landmarks: List<SmoothedLandmark>, 
        index: Int, 
        angle: Double?,
        jointCode: String
    ) {
        if (angle == null || index >= landmarks.size) return
        val landmark = landmarks[index]
        if (landmark.visibility < 0.3f) return
        
        val x = landmark.x * imageWidth * scaleFactor
        val y = landmark.y * imageHeight * scaleFactor - 20f
        
        val hasError = jointCode in errorJointCodes
        textPaint.color = if (hasError) COLOR_ERROR else Color.WHITE
        
        canvas.drawText("%.0f°".format(angle), x, y, textPaint)
        textPaint.color = Color.WHITE
    }
}
