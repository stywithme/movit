package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.InsightType
import com.trainingvalidator.poc.training.report.QuickInsight

/**
 * QuickInsightCard - Displays the main insight message
 * 
 * Glassmorphic card with:
 * - Icon
 * - Title
 * - Subtitle
 * - Actionable tip (optional)
 * 
 * Color-coded by insight type:
 * - CELEBRATION: Green
 * - FOCUS_POINT: Yellow
 * - DANGER_WARNING: Red
 */
class QuickInsightCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private val cardContainer: LinearLayout
    private val tvIcon: TextView
    private val tvTitle: TextView
    private val tvSubtitle: TextView
    private val tvActionable: TextView
    
    init {
        orientation = VERTICAL
        
        val view = LayoutInflater.from(context).inflate(R.layout.component_quick_insight, this, true)
        
        cardContainer = view.findViewById(R.id.cardContainer)
        tvIcon = view.findViewById(R.id.tvIcon)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvSubtitle = view.findViewById(R.id.tvSubtitle)
        tvActionable = view.findViewById(R.id.tvActionable)
    }
    
    /**
     * Bind insight data
     */
    fun bind(insight: QuickInsight, isArabic: Boolean = false) {
        // Icon
        tvIcon.text = insight.icon
        
        // Title
        tvTitle.text = if (isArabic) insight.title.ar else insight.title.en
        
        // Subtitle
        tvSubtitle.text = if (isArabic) insight.subtitle.ar else insight.subtitle.en
        
        // Actionable tip
        insight.actionable?.let { actionable ->
            tvActionable.visibility = View.VISIBLE
            tvActionable.text = if (isArabic) actionable.ar else actionable.en
        } ?: run {
            tvActionable.visibility = View.GONE
        }
        
        // Apply color theme based on type
        applyTheme(insight.type)
    }
    
    private fun applyTheme(type: InsightType) {
        val (bgColor, borderColor, titleColor) = when (type) {
            InsightType.CELEBRATION -> Triple(
                0x1A4CAF50, // 10% green
                0x334CAF50, // 20% green
                0xFF4CAF50.toInt() // Green
            )
            InsightType.FOCUS_POINT -> Triple(
                0x1AFFC107, // 10% yellow
                0x33FFC107, // 20% yellow
                0xFFFFC107.toInt() // Yellow
            )
            InsightType.DANGER_WARNING -> Triple(
                0x1AFF5252, // 10% red
                0x33FF5252, // 20% red
                0xFFFF5252.toInt() // Red
            )
        }
        
        // Apply background
        val background = GradientDrawable().apply {
            setColor(bgColor)
            setStroke(1, borderColor)
            cornerRadius = 16 * resources.displayMetrics.density
        }
        cardContainer.background = background
        
        // Apply title color
        tvTitle.setTextColor(titleColor)
    }
    
    /**
     * Hide the card
     */
    fun hide() {
        visibility = View.GONE
    }
    
    /**
     * Show the card
     */
    fun show() {
        visibility = View.VISIBLE
    }
}
