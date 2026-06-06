package com.trainingvalidator.poc.ui.report.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.models.MetricCode
import com.trainingvalidator.poc.training.report.*

/**
 * ExpandableMetricsSection - Collapsible section showing all metrics
 * 
 * Shows:
 * - Section header with expand/collapse toggle
 * - Grouped metrics (Performance, Safety, Control, Load)
 * - Each metric with progress bar and status
 * 
 * Filters metrics based on ExerciseConfigSnapshot if provided.
 */
class ExpandableMetricsSection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private val headerContainer: LinearLayout
    private val tvTitle: TextView
    private val ivExpandIcon: ImageView
    private val contentContainer: LinearLayout
    
    private var isExpanded = false
    
    init {
        orientation = VERTICAL
        
        val view = LayoutInflater.from(context).inflate(R.layout.component_expandable_metrics, this, true)
        
        headerContainer = view.findViewById(R.id.headerContainer)
        tvTitle = view.findViewById(R.id.tvTitle)
        ivExpandIcon = view.findViewById(R.id.ivExpandIcon)
        contentContainer = view.findViewById(R.id.contentContainer)
        
        // Initially collapsed
        contentContainer.visibility = View.GONE
        
        // Toggle on header click
        headerContainer.setOnClickListener {
            toggleExpanded()
        }
        
        applyHeaderStyle()
    }
    
    /**
     * Bind all metrics data
     * @param config Optional exercise config for filtering metrics
     */
    fun bind(
        metrics: EnhancedPerformanceMetrics, 
        isArabic: Boolean = false,
        config: ExerciseConfigSnapshot? = null
    ) {
        tvTitle.text = if (isArabic) "📊 كل المقاييس" else "📊 All Metrics"
        
        contentContainer.removeAllViews()
        
        val isHold = config?.isHoldExercise() == true
        
        // Helper to check if metric should be shown
        fun shouldShow(metric: MetricCode): Boolean {
            return config?.shouldShowMetric(metric) != false
        }
        
        // Performance metrics group
        val formMetrics = mutableListOf<MetricItem>()
        formMetrics.add(MetricItem(
            if (isArabic) "الأداء العام" else "Overall Performance",
            metrics.formCard.overallScore
        ))
        formMetrics.add(MetricItem(
            if (isArabic) "الشكل" else "Form",
            metrics.formCard.formQuality
        ))
        if (shouldShow(MetricCode.ROM)) {
            metrics.formCard.rom?.let { 
                formMetrics.add(MetricItem(if (isArabic) "المدى الحركي" else "Range of Motion", it))
            }
        }
        if (shouldShow(MetricCode.SYMMETRY)) {
            metrics.formCard.symmetry?.let { 
                formMetrics.add(MetricItem(if (isArabic) "التوازن" else "Symmetry", it))
            }
        }
        if (shouldShow(MetricCode.FORM_CONSISTENCY)) {
            metrics.formCard.formConsistency?.let { 
                formMetrics.add(MetricItem(if (isArabic) "ثبات الشكل" else "Form Consistency", it))
            }
        }
        
        addMetricsGroup(
            title = if (isArabic) "🎯 مقاييس الأداء" else "🎯 Performance Metrics",
            metrics = formMetrics,
            isArabic = isArabic
        )
        
        // Safety metrics group
        val safetyMetrics = mutableListOf<MetricItem>()
        safetyMetrics.add(MetricItem(
            if (isArabic) "الأمان العام" else "Overall Safety",
            metrics.safetyCard.overallScore
        ))
        if (shouldShow(MetricCode.ALIGNMENT)) {
            metrics.safetyCard.alignmentAccuracy?.let { 
                safetyMetrics.add(MetricItem(if (isArabic) "دقة المحاذاة" else "Alignment Accuracy", it))
            }
        }
        if (shouldShow(MetricCode.STABILITY)) {
            metrics.safetyCard.stability?.let { 
                safetyMetrics.add(MetricItem(if (isArabic) "الثبات" else "Stability", it))
            }
        }
        
        addMetricsGroup(
            title = if (isArabic) "🛡️ مقاييس الأمان" else "🛡️ Safety Metrics",
            metrics = safetyMetrics,
            isArabic = isArabic
        )
        
        // Control metrics group - different content for hold vs rep-based
        val controlTitle = if (isHold) {
            if (isArabic) "⏱️ مقاييس الثبات" else "⏱️ Hold Metrics"
        } else {
            if (isArabic) "⏱️ مقاييس التحكم" else "⏱️ Control Metrics"
        }
        
        addMetricsGroup(
            title = controlTitle,
            metrics = listOf(
                MetricItem(
                    if (isArabic) "التحكم العام" else "Overall Control",
                    metrics.controlCard.overallScore
                )
            ),
            isArabic = isArabic,
            extraContent = {
                // Tempo - only for rep-based
                if (!isHold && shouldShow(MetricCode.TEMPO)) {
                    metrics.controlCard.tempo?.let { tempo ->
                        addTempoRow(it, tempo, isArabic)
                    }
                }
                
                // TUT - only for rep-based
                if (!isHold && shouldShow(MetricCode.TUT)) {
                    metrics.controlCard.totalTUT?.let { tut ->
                        addSimpleRow(
                            it,
                            if (isArabic) "الوقت تحت الضغط" else "Time Under Tension",
                            "${tut}s"
                        )
                    }
                }
                
                // Fatigue index - only for rep-based with 4+ reps
                if (!isHold && shouldShow(MetricCode.FATIGUE_INDEX)) {
                    metrics.controlCard.fatigueIndex?.let { fatigue ->
                        addSimpleRow(
                            it,
                            if (isArabic) "نقطة التعب" else "Fatigue Point",
                            if (isArabic) "العدة #$fatigue" else "Rep #$fatigue",
                            0xFFFF9800.toInt()
                        )
                    }
                }
            }
        )
        
        // Load metrics group (only for weighted exercises)
        if (config?.supportsWeight == true) {
            metrics.loadMetrics?.let { load ->
                addMetricsGroup(
                    title = if (isArabic) "🏋️ مقاييس الحمل" else "🏋️ Load Metrics",
                    metrics = emptyList(),
                    isArabic = isArabic,
                    extraContent = {
                        if (shouldShow(MetricCode.WEIGHT)) {
                            addSimpleRow(it, if (isArabic) "الوزن" else "Weight", load.getFormattedWeight())
                        }
                        if (shouldShow(MetricCode.VOLUME)) {
                            load.getFormattedVolume()?.let { vol ->
                                addSimpleRow(it, if (isArabic) "الحجم الكلي" else "Total Volume", vol)
                            }
                        }
                        if (shouldShow(MetricCode.EST_1RM)) {
                            load.getFormattedEst1RM()?.let { rm ->
                                addSimpleRow(it, if (isArabic) "القوة القصوى المقدرة" else "Estimated 1RM", rm, 0xFFC4D489.toInt())
                            }
                        }
                    }
                )
            }
        }
    }
    
    private fun addMetricsGroup(
        title: String,
        metrics: List<MetricItem>,
        isArabic: Boolean,
        extraContent: ((LinearLayout) -> Unit)? = null
    ) {
        val groupContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        
        // Group title
        val titleView = TextView(context).apply {
            text = title
            setTextColor(0xFFE7F1F1.toInt())
            textSize = 14f
            setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
        }
        groupContainer.addView(titleView)
        
        // Metrics
        metrics.forEach { metric ->
            addMetricRow(groupContainer, metric, isArabic)
        }
        
        // Extra content
        extraContent?.invoke(groupContainer)
        
        contentContainer.addView(groupContainer)
    }
    
    private fun addMetricRow(container: LinearLayout, metric: MetricItem, isArabic: Boolean) {
        val row = LayoutInflater.from(context).inflate(R.layout.item_metric_row, container, false)
        
        row.findViewById<TextView>(R.id.tvMetricName).text = metric.name
        row.findViewById<TextView>(R.id.tvMetricValue).apply {
            text = metric.data.displayValue
            setTextColor(metric.data.status.getColor())
        }
        row.findViewById<ProgressBar>(R.id.progressMetric).apply {
            progress = metric.data.value.toInt()
            progressDrawable?.setTint(metric.data.status.getColor())
        }
        row.findViewById<TextView>(R.id.tvMetricStatus).apply {
            text = "${metric.data.status.getIcon()} ${if (isArabic) metric.data.statusLabel.ar else metric.data.statusLabel.en}"
            setTextColor(metric.data.status.getColor())
        }
        
        container.addView(row)
    }
    
    private fun addTempoRow(container: LinearLayout, tempo: TempoDisplay, isArabic: Boolean) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
        }
        
        val label = TextView(context).apply {
            text = if (isArabic) "الإيقاع" else "Tempo"
            setTextColor(0xFFB8C8C8.toInt())
            textSize = 13f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)
        
        val tempoValue = TextView(context).apply {
            text = "⬇️ ${tempo.getEccentricSeconds()}  ⏸️ ${tempo.getIsometricSeconds()}  ⬆️ ${tempo.getConcentricSeconds()}"
            setTextColor(0xFFE7F1F1.toInt())
            textSize = 13f
        }
        row.addView(tempoValue)
        
        container.addView(row)
    }
    
    private fun addSimpleRow(container: LinearLayout, label: String, value: String, valueColor: Int = 0xFFE7F1F1.toInt()) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
        }
        
        val labelView = TextView(context).apply {
            text = label
            setTextColor(0xFFB8C8C8.toInt())
            textSize = 13f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelView)
        
        val valueView = TextView(context).apply {
            text = value
            setTextColor(valueColor)
            textSize = 13f
        }
        row.addView(valueView)
        
        container.addView(row)
    }
    
    private fun toggleExpanded() {
        isExpanded = !isExpanded
        
        // Animate icon rotation
        val rotation = if (isExpanded) 180f else 0f
        ivExpandIcon.animate().rotation(rotation).setDuration(200).start()
        
        // Toggle content visibility
        if (isExpanded) {
            contentContainer.visibility = View.VISIBLE
            contentContainer.alpha = 0f
            contentContainer.animate().alpha(1f).setDuration(200).start()
        } else {
            contentContainer.animate().alpha(0f).setDuration(200).withEndAction {
                contentContainer.visibility = View.GONE
            }.start()
        }
    }
    
    private fun applyHeaderStyle() {
        val background = GradientDrawable().apply {
            setColor(0x1AFFFFFF) // 10% white
            setStroke(1, 0x33FFFFFF) // 20% white border
            cornerRadius = 12 * resources.displayMetrics.density
        }
        headerContainer.background = background
    }
    
    /**
     * Programmatically expand the section
     */
    fun expand() {
        if (!isExpanded) {
            toggleExpanded()
        }
    }
    
    /**
     * Programmatically collapse the section
     */
    fun collapse() {
        if (isExpanded) {
            toggleExpanded()
        }
    }
    
    private data class MetricItem(
        val name: String,
        val data: MetricWithStatus
    )
}
