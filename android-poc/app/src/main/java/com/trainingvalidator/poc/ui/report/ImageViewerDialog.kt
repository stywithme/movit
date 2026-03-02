package com.trainingvalidator.poc.ui.report

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.FrameCapture
import java.io.File

/**
 * Full-screen image viewer dialog
 * 
 * Shows captured frames at full size with:
 * - Angles overlay (if available)
 * - Frame metadata (score, duration, etc.)
 * - Pinch to zoom support
 */
class ImageViewerDialog(
    context: Context,
    private val frameCapture: FrameCapture,
    private val title: String = "",
    private val details: String = ""
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_image_viewer)
        
        // Make fullscreen using modern WindowInsetsController
        window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            WindowInsetsControllerCompat(w, w.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        
        setupViews()
    }
    
    private fun setupViews() {
        val ivFullImage = findViewById<ImageView>(R.id.ivFullImage)
        val btnClose = findViewById<ImageButton>(R.id.btnClose)
        val llInfo = findViewById<LinearLayout>(R.id.llInfo)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvDetails = findViewById<TextView>(R.id.tvDetails)
        val tvAngles = findViewById<TextView>(R.id.tvAngles)
        val tvHint = findViewById<TextView>(R.id.tvHint)
        
        // Load and display image with overlay
        val bitmap = loadImageWithOverlay()
        if (bitmap != null) {
            ivFullImage.setImageBitmap(bitmap)
        }
        
        // Show info if available
        if (title.isNotEmpty() || details.isNotEmpty()) {
            llInfo.visibility = View.VISIBLE
            tvTitle.text = title
            tvDetails.text = details
            
            // Format angles
            val anglesText = frameCapture.metadata.angles.entries
                .take(6) // Limit to 6 angles
                .joinToString(" | ") { "${formatJointName(it.key)}=${it.value.toInt()}°" }
            if (anglesText.isNotEmpty()) {
                tvAngles.visibility = View.VISIBLE
                tvAngles.text = anglesText
            } else {
                tvAngles.visibility = View.GONE
            }
        } else {
            llInfo.visibility = View.GONE
        }
        
        // Close handlers
        btnClose.setOnClickListener { dismiss() }
        ivFullImage.setOnClickListener { dismiss() }
        
        // Hide hint after 2 seconds
        tvHint.postDelayed({ tvHint.visibility = View.GONE }, 2000)
    }
    
    /**
     * Load image and draw angle overlay
     */
    private fun loadImageWithOverlay(): Bitmap? {
        return try {
            val file = File(frameCapture.frameUri)
            if (!file.exists()) return null
            
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (originalBitmap == null) return null
            
            // If no angles, return original
            if (frameCapture.metadata.angles.isEmpty()) {
                return originalBitmap
            }
            
            // Create mutable bitmap for drawing
            val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            
            // Draw angle overlays
            drawAngleOverlays(canvas, mutableBitmap.width, mutableBitmap.height)
            
            // Recycle original if different
            if (mutableBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            
            mutableBitmap
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Draw angle indicators on the image
     */
    private fun drawAngleOverlays(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = width * 0.04f // 4% of image width
            textAlign = Paint.Align.CENTER
        }
        
        val bgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        // Position angles at approximate body positions
        val anglePositions = mapOf(
            "left_elbow" to Pair(0.25f, 0.45f),
            "right_elbow" to Pair(0.75f, 0.45f),
            "left_shoulder" to Pair(0.30f, 0.25f),
            "right_shoulder" to Pair(0.70f, 0.25f),
            "left_hip" to Pair(0.35f, 0.55f),
            "right_hip" to Pair(0.65f, 0.55f),
            "left_knee" to Pair(0.35f, 0.70f),
            "right_knee" to Pair(0.65f, 0.70f),
            "left_ankle" to Pair(0.35f, 0.85f),
            "right_ankle" to Pair(0.65f, 0.85f)
        )
        
        var yOffset = 0
        frameCapture.metadata.angles.forEach { (jointCode, angle) ->
            val position = anglePositions[jointCode]
            
            if (position != null) {
                // Draw at body position
                val x = width * position.first
                val y = height * position.second
                
                // Determine color based on error state
                val isError = frameCapture.metadata.hasError && 
                    frameCapture.metadata.errorDetails?.contains(jointCode) == true
                
                val bgColor = if (isError) Color.argb(200, 244, 67, 54) else Color.argb(200, 76, 175, 80)
                val textColor = Color.WHITE
                
                // Draw background pill
                bgPaint.color = bgColor
                val textWidth = paint.measureText("${angle.toInt()}°")
                val padding = paint.textSize * 0.4f
                canvas.drawRoundRect(
                    x - textWidth / 2 - padding,
                    y - paint.textSize - padding / 2,
                    x + textWidth / 2 + padding,
                    y + padding / 2,
                    paint.textSize / 2,
                    paint.textSize / 2,
                    bgPaint
                )
                
                // Draw text
                paint.color = textColor
                canvas.drawText("${angle.toInt()}°", x, y, paint)
            } else {
                // Draw in corner list
                val x = width * 0.1f
                val y = height * 0.1f + yOffset
                
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("${formatJointName(jointCode)}: ${angle.toInt()}°", x, y, paint)
                paint.textAlign = Paint.Align.CENTER
                
                yOffset += (paint.textSize * 1.5f).toInt()
            }
        }
    }
    
    private fun formatJointName(code: String): String {
        return code.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
