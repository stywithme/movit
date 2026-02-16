package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.trainingvalidator.poc.training.report.RepTimelineEntry

/**
 * RepsJourneyChart — Visual bar chart of rep scores across the session.
 *
 * V2 visual upgrades:
 *   - Gradient-filled bars (bright at top, darker at base)
 *   - Dashed average-score line with floating label
 *   - Softer corner radius
 *   - Best/Worst rep border highlights
 *   - Orange bars for reps with position issues
 */
class RepsJourneyChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var repData: List<RepTimelineEntry> = emptyList()
    private var fatigueIndex: Int? = null

    private val density = resources.displayMetrics.density

    // Paints
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x12FFFFFF
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB0B0B0.toInt()
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
    }
    private val fatigueLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }
    private val avgLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    }
    private val avgLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 10f * density
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val avgLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
    }

    // Colors
    private val colorHigh = 0xFF4CAF50.toInt()
    private val colorHighDark = 0xFF2E7D32.toInt()
    private val colorMedium = 0xFFFFC107.toInt()
    private val colorMediumDark = 0xFFF57F17.toInt()
    private val colorLow = 0xFFFF5252.toInt()
    private val colorLowDark = 0xFFB71C1C.toInt()
    private val colorWarning = 0xFFFF9800.toInt()
    private val colorWarningDark = 0xFFE65100.toInt()

    // Marker paints
    private val bestMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CAF50.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val worstMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5252.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    // Layout
    private val barRect = RectF()
    private val labelRect = RectF()
    private val paddingH = 16f * density
    private val paddingV = 36f * density
    private val barSpacing = 4f * density
    private val cornerRadius = 6f * density

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

        val chartWidth = width - paddingH * 2
        val chartHeight = height - paddingV * 2
        val barCount = repData.size
        val barWidth = (chartWidth - (barSpacing * (barCount - 1))) / barCount

        // Grid lines
        drawGridLines(canvas, chartHeight)

        // Average line
        drawAverageLine(canvas, chartWidth, chartHeight)

        // Score label paint (shown on top of bars)
        val scoreLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 9f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Position issue marker paint (small dot)
        val issueMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorWarning
            style = Paint.Style.FILL
        }

        // Bars
        repData.forEachIndexed { index, rep ->
            val hasPositionIssues = rep.positionWarningCount > 0 || rep.positionErrorCount > 0
            val minBarRatio = if (rep.score <= 0) 0.05f else rep.score / 100f
            val barHeight = (minBarRatio * chartHeight).coerceAtLeast(8f * density)
            val left = paddingH + index * (barWidth + barSpacing)
            val top = paddingV + (chartHeight - barHeight)
            val right = left + barWidth
            val bottom = paddingV + chartHeight
            val barCenterX = left + barWidth / 2

            barRect.set(left, top, right, bottom)

            // Always use score-based gradient color
            val (topColor, bottomColor) = getBarColors(rep.score)
            barPaint.shader = LinearGradient(
                left, top, left, bottom,
                topColor, bottomColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
            barPaint.shader = null

            // Best / worst border highlight
            if (rep.isBestRep) {
                barRect.inset(-1f, -1f)
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, bestMarkerPaint)
            } else if (rep.isWorstRep) {
                barRect.inset(-1f, -1f)
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, worstMarkerPaint)
            }

            // Position issue indicator (small orange dot above bar)
            if (hasPositionIssues) {
                val dotY = top - 5f * density
                val dotR = 3f * density
                if (rep.positionErrorCount > 0) {
                    issueMarkerPaint.color = colorLow  // red for errors
                } else {
                    issueMarkerPaint.color = colorWarning // orange for warnings
                }
                canvas.drawCircle(barCenterX, dotY, dotR, issueMarkerPaint)
            }

            // Score label on top of bar (only when <=20 reps for readability)
            if (barCount <= 20) {
                val scoreY = if (hasPositionIssues) {
                    top - 16f * density  // shift up to make room for dot
                } else {
                    top - 6f * density
                }
                canvas.drawText("${rep.score.toInt()}", barCenterX, scoreY, scoreLabelPaint)
            }

            // Rep number label at bottom
            if (barCount <= 15 || index % 2 == 0) {
                val labelY = bottom + textPaint.textSize + 4f * density
                canvas.drawText("${rep.repNumber}", barCenterX, labelY, textPaint)
            }
        }

        // Fatigue marker
        fatigueIndex?.let { fatigue ->
            val fatigueRepIndex = repData.indexOfFirst { it.repNumber == fatigue }
            if (fatigueRepIndex >= 0) {
                val x = paddingH + fatigueRepIndex * (barWidth + barSpacing) + barWidth / 2
                canvas.drawLine(x, paddingV / 2, x, paddingV + chartHeight, fatigueLinePaint)
                textPaint.color = 0xFFFF9800.toInt()
                canvas.drawText("↓", x, paddingV - 4f * density, textPaint)
                textPaint.color = 0xFFB0B0B0.toInt()
            }
        }
    }

    private fun drawGridLines(canvas: Canvas, chartHeight: Float) {
        for (level in listOf(0.25f, 0.5f, 0.75f)) {
            val y = paddingV + chartHeight * (1 - level)
            canvas.drawLine(paddingH, y, width - paddingH, y, gridPaint)
        }
    }

    private fun drawAverageLine(canvas: Canvas, chartWidth: Float, chartHeight: Float) {
        if (repData.size < 2) return
        val avg = repData.map { it.score }.average().toFloat()
        if (avg <= 0) return

        val y = paddingV + chartHeight * (1 - avg / 100f)
        canvas.drawLine(paddingH, y, paddingH + chartWidth, y, avgLinePaint)

        // Floating label
        val labelText = "${avg.toInt()}"
        val labelW = avgLabelPaint.measureText(labelText) + 10f * density
        val labelH = avgLabelPaint.textSize + 6f * density
        val labelX = paddingH + chartWidth + 2f * density
        labelRect.set(labelX, y - labelH / 2, labelX + labelW, y + labelH / 2)

        // Only draw if it fits in the view
        if (labelRect.right < width) {
            canvas.drawRoundRect(labelRect, 4f * density, 4f * density, avgLabelBgPaint)
            canvas.drawText(labelText, labelX + 5f * density, y + avgLabelPaint.textSize * 0.35f, avgLabelPaint)
        }
    }

    private fun drawEmptyState(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("No data available", width / 2f, height / 2f, textPaint)
    }

    private fun getBarColors(score: Float): Pair<Int, Int> = when {
        score >= 80 -> colorHigh to colorHighDark
        score >= 60 -> colorMedium to colorMediumDark
        else -> colorLow to colorLowDark
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (160 * density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}
