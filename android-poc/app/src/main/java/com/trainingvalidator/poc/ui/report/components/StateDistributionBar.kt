package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.report.StateBreakdown
import com.trainingvalidator.poc.training.report.StateDisplayConfig

/**
 * StateDistributionBar - Visual bar showing state distribution
 * 
 * Displays a horizontal bar with colored segments representing
 * the distribution of states (PERFECT, NORMAL, PAD, WARNING, DANGER).
 * 
 * Each segment is proportional to the count and shows user-friendly icons.
 */
class StateDistributionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var breakdown: StateBreakdown? = null
    
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
    }
    
    private val rect = RectF()
    private val cornerRadius = 8f * resources.displayMetrics.density
    
    // State colors
    private val colorPerfect = ContextCompat.getColor(context, R.color.state_perfect)
    private val colorNormal = ContextCompat.getColor(context, R.color.state_normal)
    private val colorPad = ContextCompat.getColor(context, R.color.state_pad)
    private val colorWarning = ContextCompat.getColor(context, R.color.state_warning)
    private val colorDanger = ContextCompat.getColor(context, R.color.state_danger)
    
    init {
        textPaint.textSize = 14f * resources.displayMetrics.density
    }
    
    /**
     * Set the state breakdown to display
     */
    fun setBreakdown(breakdown: StateBreakdown) {
        this.breakdown = breakdown
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val bd = breakdown ?: return
        val total = bd.total
        if (total <= 0) return
        
        val width = width.toFloat()
        val height = height.toFloat()
        val segmentHeight = height * 0.6f
        val topOffset = (height - segmentHeight) / 2
        
        // Build segments
        data class Segment(val count: Int, val color: Int, val icon: String)
        
        val segments = listOf(
            Segment(bd.perfectCount, colorPerfect, StateDisplayConfig.getIcon(JointState.PERFECT)),
            Segment(bd.normalCount, colorNormal, StateDisplayConfig.getIcon(JointState.NORMAL)),
            Segment(bd.padCount, colorPad, StateDisplayConfig.getIcon(JointState.PAD)),
            Segment(bd.warningCount, colorWarning, StateDisplayConfig.getIcon(JointState.WARNING)),
            Segment(bd.dangerCount, colorDanger, StateDisplayConfig.getIcon(JointState.DANGER))
        ).filter { it.count > 0 }
        
        if (segments.isEmpty()) return
        
        var startX = 0f
        
        segments.forEachIndexed { index, segment ->
            val segmentWidth = (segment.count.toFloat() / total) * width
            
            segmentPaint.color = segment.color
            
            rect.set(startX, topOffset, startX + segmentWidth, topOffset + segmentHeight)
            
            // Draw rounded corners only for first and last segments
            when {
                segments.size == 1 -> {
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, segmentPaint)
                }
                index == 0 -> {
                    // First segment - round left corners
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, segmentPaint)
                    // Cover right corners with rectangle
                    canvas.drawRect(
                        startX + segmentWidth - cornerRadius,
                        topOffset,
                        startX + segmentWidth,
                        topOffset + segmentHeight,
                        segmentPaint
                    )
                }
                index == segments.lastIndex -> {
                    // Last segment - round right corners
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, segmentPaint)
                    // Cover left corners with rectangle
                    canvas.drawRect(
                        startX,
                        topOffset,
                        startX + cornerRadius,
                        topOffset + segmentHeight,
                        segmentPaint
                    )
                }
                else -> {
                    // Middle segments - no rounded corners
                    canvas.drawRect(rect, segmentPaint)
                }
            }
            
            // Draw icon if segment is wide enough
            if (segmentWidth > textPaint.textSize * 1.5f) {
                val iconX = startX + segmentWidth / 2
                val iconY = topOffset + segmentHeight / 2 - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(segment.icon, iconX, iconY, textPaint)
            }
            
            startX += segmentWidth
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultHeight = (40 * resources.displayMetrics.density).toInt()
        
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> widthSize
            else -> 300
        }
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(defaultHeight, heightSize)
            else -> defaultHeight
        }
        
        setMeasuredDimension(width, height)
    }
}
