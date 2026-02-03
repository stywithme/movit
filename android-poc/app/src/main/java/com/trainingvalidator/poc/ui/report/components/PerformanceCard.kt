package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.*

/**
 * PerformanceCard - One of the 3 main performance cards
 * 
 * Glassmorphic design with:
 * - Icon and title
 * - Main score with progress bar
 * - Status label
 * - Advice text
 * - Expandable detail (click to expand)
 * 
 * Types:
 * - FORM (الشكل)
 * - SAFETY (الأمان)
 * - CONTROL (التحكم)
 */
class PerformanceCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    enum class CardType {
        FORM,
        SAFETY,
        CONTROL
    }
    
    private val cardContainer: LinearLayout
    private val tvIcon: TextView
    private val tvTitle: TextView
    private val tvScore: TextView
    private val progressBar: ProgressBar
    private val tvStatus: TextView
    private val tvAdvice: TextView
    private val ivExpand: View
    
    private var isExpanded = false
    private var expandCallback: (() -> Unit)? = null
    
    init {
        orientation = VERTICAL
        
        val view = LayoutInflater.from(context).inflate(R.layout.component_performance_card, this, true)
        
        cardContainer = view.findViewById(R.id.cardContainer)
        tvIcon = view.findViewById(R.id.tvIcon)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvScore = view.findViewById(R.id.tvScore)
        progressBar = view.findViewById(R.id.progressBar)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvAdvice = view.findViewById(R.id.tvAdvice)
        ivExpand = view.findViewById(R.id.ivExpand)
        
        // Click to expand
        cardContainer.setOnClickListener {
            expandCallback?.invoke()
        }
    }
    
    /**
     * Bind Form card data
     */
    fun bindFormCard(metrics: FormMetrics, isArabic: Boolean = false) {
        setupCard(
            type = CardType.FORM,
            icon = "🎯",
            title = if (isArabic) "الشكل" else "Form",
            metric = metrics.overallScore,
            isArabic = isArabic
        )
    }
    
    /**
     * Bind Safety card data
     */
    fun bindSafetyCard(metrics: SafetyMetrics, isArabic: Boolean = false) {
        setupCard(
            type = CardType.SAFETY,
            icon = if (metrics.hasDanger()) "⚠️" else "🛡️",
            title = if (isArabic) "الأمان" else "Safety",
            metric = metrics.overallScore,
            isArabic = isArabic
        )
        
        // Special handling for danger
        if (metrics.hasDanger()) {
            tvStatus.setTextColor(0xFFFF5252.toInt())
        }
    }
    
    /**
     * Bind Control card data
     */
    fun bindControlCard(metrics: ControlMetrics, isArabic: Boolean = false) {
        val tempoText = metrics.tempo?.getFormattedTempo()
        
        setupCard(
            type = CardType.CONTROL,
            icon = "⏱️",
            title = if (isArabic) "التحكم" else "Control",
            metric = metrics.overallScore,
            isArabic = isArabic,
            customScore = tempoText
        )
    }
    
    private fun setupCard(
        type: CardType,
        icon: String,
        title: String,
        metric: MetricWithStatus,
        isArabic: Boolean,
        customScore: String? = null
    ) {
        tvIcon.text = icon
        tvTitle.text = title
        
        // Score
        tvScore.text = customScore ?: metric.displayValue
        
        // Progress bar
        progressBar.progress = metric.value.toInt()
        
        // Set progress color based on status
        val progressColor = metric.status.getColor()
        progressBar.progressDrawable?.setTint(progressColor)
        
        // Status label
        val statusLabel = if (isArabic) metric.statusLabel.ar else metric.statusLabel.en
        tvStatus.text = "${metric.status.getIcon()} $statusLabel"
        tvStatus.setTextColor(metric.status.getColor())
        
        // Advice
        metric.advice?.let { advice ->
            tvAdvice.visibility = View.VISIBLE
            tvAdvice.text = if (isArabic) advice.ar else advice.en
        } ?: run {
            tvAdvice.visibility = View.GONE
        }
        
        // Apply card style
        applyCardStyle(type)
    }
    
    private fun applyCardStyle(type: CardType) {
        val accentColor = when (type) {
            CardType.FORM -> 0xFF8AF851.toInt()    // Primary green
            CardType.SAFETY -> 0xFF4CAF50.toInt()   // Success green
            CardType.CONTROL -> 0xFF2196F3.toInt()  // Info blue
        }
        
        // Glass background with accent border
        val background = GradientDrawable().apply {
            setColor(0x1AFFFFFF) // 10% white
            setStroke(1, 0x33FFFFFF) // 20% white border
            cornerRadius = 16 * resources.displayMetrics.density
        }
        cardContainer.background = background
        
        // Title color
        tvTitle.setTextColor(accentColor)
    }
    
    /**
     * Set click callback for expansion
     */
    fun setOnExpandClickListener(callback: () -> Unit) {
        expandCallback = callback
    }
}
