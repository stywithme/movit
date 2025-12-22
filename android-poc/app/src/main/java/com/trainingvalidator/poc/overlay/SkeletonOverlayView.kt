package com.trainingvalidator.poc.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import kotlin.math.max

/**
 * SkeletonOverlayView - Custom View to draw skeleton overlay on camera preview
 * 
 * Features:
 * - Visibility filtering: Only draws landmarks above visibility threshold
 * - Smooth rendering: Uses pre-smoothed landmarks
 * - Proper scaling: Uses scaleFactor for FILL_START mode
 */
class SkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12f
        private const val LINE_WIDTH = 8f
        private const val TEXT_SIZE = 32f
        
        // Visibility threshold - landmarks below this won't be drawn
        private const val VISIBILITY_THRESHOLD = 0.5f
        
        // Colors
        private val COLOR_POINT_VISIBLE = Color.YELLOW
        private val COLOR_POINT_LOW_VISIBILITY = Color.argb(100, 255, 255, 0) // Semi-transparent
        private val COLOR_LINE = Color.CYAN
        private val COLOR_LINE_LOW_VISIBILITY = Color.argb(100, 0, 255, 255)
    }

    // Paint objects
    private val pointPaint = Paint().apply {
        color = COLOR_POINT_VISIBLE
        strokeWidth = LANDMARK_STROKE_WIDTH
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = COLOR_LINE
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
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Current pose data
    private var landmarks: List<SmoothedLandmark>? = null
    private var jointAngles: JointAngles? = null
    
    // Image dimensions for scaling
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f
    
    // Display settings
    private var showAngles = true
    private var showLowVisibility = false // Option to show semi-visible landmarks

    /**
     * Update the skeleton with new smoothed landmarks
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
        
        // Calculate scale factor for FILL_START mode
        scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        
        invalidate()
    }

    fun setShowAngles(show: Boolean) {
        showAngles = show
        invalidate()
    }

    fun setShowLowVisibility(show: Boolean) {
        showLowVisibility = show
        invalidate()
    }

    fun clear() {
        landmarks = null
        jointAngles = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val currentLandmarks = landmarks ?: return
        if (currentLandmarks.isEmpty()) return

        drawConnections(canvas, currentLandmarks)
        drawLandmarks(canvas, currentLandmarks)
        
        if (showAngles && jointAngles != null) {
            drawAngles(canvas, currentLandmarks)
        }
    }

    private fun drawConnections(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
            if (connection != null) {
                val startIdx = connection.start()
                val endIdx = connection.end()
                
                if (startIdx < landmarks.size && endIdx < landmarks.size) {
                    val startLandmark = landmarks[startIdx]
                    val endLandmark = landmarks[endIdx]
                    
                    // Check visibility of both endpoints
                    val bothVisible = startLandmark.isVisible(VISIBILITY_THRESHOLD) && 
                                     endLandmark.isVisible(VISIBILITY_THRESHOLD)
                    
                    if (bothVisible) {
                        linePaint.color = COLOR_LINE
                        canvas.drawLine(
                            getScreenX(startLandmark.x),
                            getScreenY(startLandmark.y),
                            getScreenX(endLandmark.x),
                            getScreenY(endLandmark.y),
                            linePaint
                        )
                    } else if (showLowVisibility) {
                        // Draw with lower opacity for debugging
                        linePaint.color = COLOR_LINE_LOW_VISIBILITY
                        canvas.drawLine(
                            getScreenX(startLandmark.x),
                            getScreenY(startLandmark.y),
                            getScreenX(endLandmark.x),
                            getScreenY(endLandmark.y),
                            linePaint
                        )
                    }
                    // If not visible and showLowVisibility is false, don't draw
                }
            }
        }
    }

    private fun drawLandmarks(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        landmarks.forEach { landmark ->
            val isVisible = landmark.isVisible(VISIBILITY_THRESHOLD)
            
            if (isVisible) {
                pointPaint.color = COLOR_POINT_VISIBLE
                canvas.drawCircle(
                    getScreenX(landmark.x),
                    getScreenY(landmark.y),
                    LANDMARK_STROKE_WIDTH / 2,
                    pointPaint
                )
            } else if (showLowVisibility) {
                // Draw with lower opacity for debugging
                pointPaint.color = COLOR_POINT_LOW_VISIBILITY
                canvas.drawCircle(
                    getScreenX(landmark.x),
                    getScreenY(landmark.y),
                    LANDMARK_STROKE_WIDTH / 2,
                    pointPaint
                )
            }
        }
    }

    private fun drawAngles(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
        val angles = jointAngles ?: return
        
        val anglePositions = mapOf(
            13 to angles.leftElbow,
            14 to angles.rightElbow,
            11 to angles.leftShoulder,
            12 to angles.rightShoulder,
            23 to angles.leftHip,
            24 to angles.rightHip,
            25 to angles.leftKnee,
            26 to angles.rightKnee
        )
        
        anglePositions.forEach { (landmarkIndex, angle) ->
            if (angle != null && landmarkIndex < landmarks.size) {
                val landmark = landmarks[landmarkIndex]
                
                // Only show angle if landmark is visible
                if (!landmark.isVisible(VISIBILITY_THRESHOLD)) return@forEach
                
                val x = getScreenX(landmark.x)
                val y = getScreenY(landmark.y) - LANDMARK_STROKE_WIDTH - 8f
                
                val text = "%.0f°".format(angle)
                
                val textWidth = textPaint.measureText(text)
                val padding = 6f
                canvas.drawRoundRect(
                    x - textWidth / 2 - padding,
                    y - TEXT_SIZE + padding,
                    x + textWidth / 2 + padding,
                    y + padding,
                    6f, 6f,
                    textBackgroundPaint
                )
                
                canvas.drawText(text, x, y, textPaint)
            }
        }
    }

    private fun getScreenX(normalizedX: Float): Float {
        return normalizedX * imageWidth * scaleFactor
    }

    private fun getScreenY(normalizedY: Float): Float {
        return normalizedY * imageHeight * scaleFactor
    }
}
