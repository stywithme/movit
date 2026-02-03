package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.trainingvalidator.poc.training.report.RepTimelineEntry

/**
 * RepsJourneyChart - Visual representation of rep scores across the session
 * 
 * A simple bar chart showing:
 * - Each rep as a colored bar (green → yellow → red based on score)
 * - Fatigue point indicator
 * - Best/Worst rep highlights
 */
class RepsJourneyChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var repData: List<RepTimelineEntry> = emptyList()
    private var fatigueIndex: Int? = null
    
    // Paints
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1AFFFFFF // 10% white
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB0B0B0.toInt()
        textSize = 10 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private val fatigueMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt() // Orange
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    
    // Colors
    private val colorHigh = 0xFF4CAF50.toInt()    // Green
    private val colorMedium = 0xFFFFC107.toInt()  // Yellow
    private val colorLow = 0xFFFF5252.toInt()     // Red
    
    // Layout
    private val barRect = RectF()
    private val paddingHorizontal = 16 * resources.displayMetrics.density
    private val paddingVertical = 24 * resources.displayMetrics.density
    private val barSpacing = 4 * resources.displayMetrics.density
    private val cornerRadius = 4 * resources.displayMetrics.density
    
    /**
     * Set the rep timeline data
     */
    fun setData(timeline: List<RepTimelineEntry>, fatigue: Int? = null) {
        this.repData = timeline
        this.fatigueIndex = fatigue
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (repData.isEmpty()) {
            drawEmptyState(canvas)
            return
        }
        
        val chartWidth = width - paddingHorizontal * 2
        val chartHeight = height - paddingVertical * 2
        val barCount = repData.size
        val barWidth = (chartWidth - (barSpacing * (barCount - 1))) / barCount
        
        // Draw grid lines (at 25%, 50%, 75%)
        drawGridLines(canvas, chartHeight)
        
        // Draw bars
        repData.forEachIndexed { index, rep ->
            // Minimum bar height for visibility (at least 5% if score is 0 but rep exists)
            val minBarRatio = if (rep.score <= 0) 0.05f else rep.score / 100f
            val barHeight = (minBarRatio * chartHeight).coerceAtLeast(8 * resources.displayMetrics.density)
            val left = paddingHorizontal + index * (barWidth + barSpacing)
            val top = paddingVertical + (chartHeight - barHeight)
            val right = left + barWidth
            val bottom = paddingVertical + chartHeight
            
            // Bar color based on score
            barPaint.color = getBarColor(rep.score)
            
            barRect.set(left, top, right, bottom)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
            
            // Rep number
            if (barCount <= 15 || index % 2 == 0) {
                val labelY = bottom + textPaint.textSize + 4 * resources.displayMetrics.density
                canvas.drawText("${rep.repNumber}", left + barWidth / 2, labelY, textPaint)
            }
        }
        
        // Draw fatigue marker
        fatigueIndex?.let { fatigue ->
            val fatigueRepIndex = repData.indexOfFirst { it.repNumber == fatigue }
            if (fatigueRepIndex >= 0) {
                val x = paddingHorizontal + fatigueRepIndex * (barWidth + barSpacing) + barWidth / 2
                canvas.drawLine(x, paddingVertical / 2, x, paddingVertical + chartHeight, fatigueMarkerPaint)
                
                // Draw arrow marker
                textPaint.color = 0xFFFF9800.toInt()
                canvas.drawText("↓", x, paddingVertical - 4 * resources.displayMetrics.density, textPaint)
                textPaint.color = 0xFFB0B0B0.toInt()
            }
        }
    }
    
    private fun drawGridLines(canvas: Canvas, chartHeight: Float) {
        val levels = listOf(0.25f, 0.5f, 0.75f)
        levels.forEach { level ->
            val y = paddingVertical + chartHeight * (1 - level)
            canvas.drawLine(paddingHorizontal, y, width - paddingHorizontal, y, gridPaint)
        }
    }
    
    private fun drawEmptyState(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "No data available",
            width / 2f,
            height / 2f,
            textPaint
        )
    }
    
    private fun getBarColor(score: Float): Int {
        return when {
            score >= 80 -> colorHigh
            score >= 60 -> colorMedium
            else -> colorLow
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (120 * resources.displayMetrics.density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}
