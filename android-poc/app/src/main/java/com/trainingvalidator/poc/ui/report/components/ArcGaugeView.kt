package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * ArcGaugeView — A half-circle arc gauge that visually represents a 0-100 score.
 *
 * Visual:
 *   - Background track: dark gray arc (~240 degrees)
 *   - Foreground arc: gradient-colored proportional to the score
 *   - Score text centered inside the arc
 *   - Optional label below the score
 *
 * Inspired by modern fitness dashboard gauges.
 */
class ArcGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var score: Float = 0f
    private var label: String? = null

    // Arc geometry
    private val sweepAngle = 240f
    private val startAngle = 150f   // starts from lower-left

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x1AFFFFFF
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }

    // Gradient colors (green -> yellow -> red, reversed for low scores)
    private val gradientColors = intArrayOf(
        0xFFFF5252.toInt(),   // Red   (low)
        0xFFFFC107.toInt(),   // Yellow (mid)
        0xFF8BC34A.toInt(),   // Light green
        0xFF4CAF50.toInt()    // Green (high)
    )
    private val gradientPositions = floatArrayOf(0f, 0.35f, 0.7f, 1f)

    private val arcRect = RectF()

    fun setScore(score: Float, label: String? = null) {
        this.score = score.coerceIn(0f, 100f)
        this.label = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val strokeWidth = 8f * density
        trackPaint.strokeWidth = strokeWidth
        arcPaint.strokeWidth = strokeWidth

        val padding = strokeWidth / 2f + 2f * density
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f

        // Arc bounding rect (centered, square)
        val radius = (size - padding * 2) / 2f
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 1. Background track
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, trackPaint)

        // 2. Foreground arc with gradient
        if (score > 0) {
            val progressSweep = (score / 100f) * sweepAngle

            // SweepGradient from the arc start
            val shader = SweepGradient(cx, cy, gradientColors, gradientPositions).apply {
                // Rotate so gradient aligns with the arc start
                val matrix = Matrix()
                matrix.postRotate(startAngle, cx, cy)
                setLocalMatrix(matrix)
            }
            arcPaint.shader = shader
            canvas.drawArc(arcRect, startAngle, progressSweep, false, arcPaint)
            arcPaint.shader = null

            // Draw end-cap circle (knob at the end of the arc)
            val endAngleRad = Math.toRadians((startAngle + progressSweep).toDouble())
            val knobX = cx + radius * Math.cos(endAngleRad).toFloat()
            val knobY = cy + radius * Math.sin(endAngleRad).toFloat()
            val knobRadius = strokeWidth * 0.7f
            val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = getScoreColor(score)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(knobX, knobY, knobRadius, knobPaint)
            // White inner dot
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A0F1A.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(knobX, knobY, knobRadius * 0.45f, innerPaint)
        }

        // 3. Score text
        scorePaint.textSize = size * 0.28f
        scorePaint.color = getScoreColor(score)
        val scoreText = "${score.toInt()}%"
        canvas.drawText(scoreText, cx, cy + scorePaint.textSize * 0.15f, scorePaint)

        // 4. Label below score
        label?.let {
            labelPaint.textSize = size * 0.12f
            canvas.drawText(it, cx, cy + scorePaint.textSize * 0.15f + labelPaint.textSize * 1.4f, labelPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = (80 * resources.displayMetrics.density).toInt()
        val w = resolveSize(desiredSize, widthMeasureSpec)
        val h = resolveSize(desiredSize, heightMeasureSpec)
        val size = min(w, h)
        setMeasuredDimension(size, size)
    }

    companion object {
        fun getScoreColor(score: Float): Int = when {
            score >= 90 -> 0xFF4CAF50.toInt()
            score >= 80 -> 0xFF8BC34A.toInt()
            score >= 70 -> 0xFFFFC107.toInt()
            score >= 50 -> 0xFFFF9800.toInt()
            else -> 0xFFFF5252.toInt()
        }
    }
}
