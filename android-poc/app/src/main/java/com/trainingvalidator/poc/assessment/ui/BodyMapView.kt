package com.trainingvalidator.poc.assessment.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.trainingvalidator.poc.assessment.models.AssessmentRegion
import com.trainingvalidator.poc.assessment.models.BodyRegion
import com.trainingvalidator.poc.assessment.models.ConfidenceLevel

/**
 * BodyMapView - Custom view that draws a human body outline
 * with colored regions based on assessment scores.
 * 
 * Colors:
 * - Green: Excellent/Good
 * - Yellow: Average
 * - Orange: Limited
 * - Red: Weak
 * - Gray: Inconclusive (LOW confidence)
 */
class BodyMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var regions: List<AssessmentRegion> = emptyList()
    
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#424242")
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    
    private val regionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 180
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    private val confidencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(8f)
        textAlign = Paint.Align.CENTER
    }
    
    fun setRegions(newRegions: List<AssessmentRegion>) {
        regions = newRegions
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height / 2f
        val scale = minOf(width, height) / 300f
        
        // Draw body outline (simplified stick figure)
        drawBodyOutline(canvas, cx, cy, scale)
        
        // Draw colored regions
        val grouped = regions.groupBy { it.region }
        for ((region, data) in grouped) {
            val best = data.maxByOrNull { it.regionalScore } ?: continue
            drawRegionHighlight(canvas, region, best, cx, cy, scale)
        }
    }
    
    private fun drawBodyOutline(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        val path = Path()
        
        // Head
        canvas.drawCircle(cx, cy - 100 * scale, 18 * scale, outlinePaint)
        
        // Neck
        path.moveTo(cx, cy - 82 * scale)
        path.lineTo(cx, cy - 70 * scale)
        
        // Shoulders
        path.moveTo(cx - 50 * scale, cy - 65 * scale)
        path.lineTo(cx + 50 * scale, cy - 65 * scale)
        
        // Torso
        path.moveTo(cx - 35 * scale, cy - 65 * scale)
        path.lineTo(cx - 30 * scale, cy + 10 * scale)
        path.moveTo(cx + 35 * scale, cy - 65 * scale)
        path.lineTo(cx + 30 * scale, cy + 10 * scale)
        
        // Hip line
        path.moveTo(cx - 30 * scale, cy + 10 * scale)
        path.lineTo(cx + 30 * scale, cy + 10 * scale)
        
        // Left leg
        path.moveTo(cx - 20 * scale, cy + 10 * scale)
        path.lineTo(cx - 25 * scale, cy + 70 * scale)
        path.lineTo(cx - 22 * scale, cy + 110 * scale)
        
        // Right leg
        path.moveTo(cx + 20 * scale, cy + 10 * scale)
        path.lineTo(cx + 25 * scale, cy + 70 * scale)
        path.lineTo(cx + 22 * scale, cy + 110 * scale)
        
        // Left arm
        path.moveTo(cx - 50 * scale, cy - 65 * scale)
        path.lineTo(cx - 65 * scale, cy - 20 * scale)
        path.lineTo(cx - 60 * scale, cy + 15 * scale)
        
        // Right arm
        path.moveTo(cx + 50 * scale, cy - 65 * scale)
        path.lineTo(cx + 65 * scale, cy - 20 * scale)
        path.lineTo(cx + 60 * scale, cy + 15 * scale)
        
        canvas.drawPath(path, outlinePaint)
    }
    
    private fun drawRegionHighlight(
        canvas: Canvas,
        region: BodyRegion,
        data: AssessmentRegion,
        cx: Float,
        cy: Float,
        scale: Float
    ) {
        regionPaint.color = data.status.color
        regionPaint.alpha = if (data.confidence == ConfidenceLevel.LOW) 60 else 160
        
        val (rx, ry, radius) = getRegionPosition(region, data, cx, cy, scale)
        
        canvas.drawCircle(rx, ry, radius, regionPaint)
        
        // Score label
        textPaint.color = Color.WHITE
        canvas.drawText("${data.regionalScore.toInt()}%", rx, ry + dp(4f), textPaint)
        
        // Confidence indicator
        if (data.confidence != ConfidenceLevel.HIGH) {
            confidencePaint.color = data.confidence.getColor()
            val symbol = if (data.confidence == ConfidenceLevel.MEDIUM) "~" else "?"
            canvas.drawText(symbol, rx + radius * 0.7f, ry - radius * 0.5f, confidencePaint)
        }
    }
    
    private fun getRegionPosition(
        region: BodyRegion,
        data: AssessmentRegion,
        cx: Float,
        cy: Float,
        scale: Float
    ): Triple<Float, Float, Float> {
        val isLeft = data.side == com.trainingvalidator.poc.assessment.models.RegionSide.LEFT
        val sideOffset = if (isLeft) -1f else if (data.side == com.trainingvalidator.poc.assessment.models.RegionSide.RIGHT) 1f else 0f
        
        return when (region) {
            BodyRegion.SHOULDER -> Triple(cx + sideOffset * 50 * scale, cy - 65 * scale, 15 * scale)
            BodyRegion.UPPER_BACK -> Triple(cx, cy - 50 * scale, 14 * scale)
            BodyRegion.CORE -> Triple(cx, cy - 25 * scale, 16 * scale)
            BodyRegion.LOWER_BACK -> Triple(cx, cy, 13 * scale)
            BodyRegion.HIP -> Triple(cx + sideOffset * 25 * scale, cy + 10 * scale, 14 * scale)
            BodyRegion.KNEE -> Triple(cx + sideOffset * 25 * scale, cy + 55 * scale, 12 * scale)
            BodyRegion.ANKLE -> Triple(cx + sideOffset * 23 * scale, cy + 100 * scale, 10 * scale)
            BodyRegion.BALANCE -> Triple(cx, cy + 120 * scale, 12 * scale)
        }
    }
    
    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(300f).toInt()
        val desiredHeight = dp(250f).toInt()
        
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
