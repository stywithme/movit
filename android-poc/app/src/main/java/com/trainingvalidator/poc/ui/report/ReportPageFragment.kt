package com.trainingvalidator.poc.ui.report

import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentReportPageBinding
import com.trainingvalidator.poc.training.report.*
import java.io.File

/**
 * ReportPageFragment - Displays a single page in the report pager
 * 
 * Can display either:
 * - Exercise summary (first page)
 * - Individual rep details (subsequent pages)
 * 
 * Full screen image with overlay stats - no cards, no clutter.
 */
class ReportPageFragment : Fragment() {
    
    companion object {
        private const val ARG_PAGE_TYPE = "page_type"
        private const val ARG_REP_INDEX = "rep_index"
        
        const val PAGE_TYPE_SUMMARY = 0
        const val PAGE_TYPE_REP = 1
        
        /**
         * Create fragment for exercise summary
         */
        fun newSummaryInstance(): ReportPageFragment {
            return ReportPageFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PAGE_TYPE, PAGE_TYPE_SUMMARY)
                }
            }
        }
        
        /**
         * Create fragment for specific rep
         */
        fun newRepInstance(repIndex: Int): ReportPageFragment {
            return ReportPageFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PAGE_TYPE, PAGE_TYPE_REP)
                    putInt(ARG_REP_INDEX, repIndex)
                }
            }
        }
    }
    
    private var _binding: FragmentReportPageBinding? = null
    private val binding get() = _binding!!
    
    private var report: PostTrainingReport? = null
    private var isArabic: Boolean = false
    private var pageType: Int = PAGE_TYPE_SUMMARY
    private var repIndex: Int = 0
    private var totalPages: Int = 1
    private var currentPage: Int = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportPageBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        arguments?.let {
            pageType = it.getInt(ARG_PAGE_TYPE, PAGE_TYPE_SUMMARY)
            repIndex = it.getInt(ARG_REP_INDEX, 0)
        }
        
        // Data will be bound when setData is called
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * Set report data and refresh UI
     */
    fun setData(
        report: PostTrainingReport,
        isArabic: Boolean,
        totalPages: Int,
        currentPage: Int
    ) {
        this.report = report
        this.isArabic = isArabic
        this.totalPages = totalPages
        this.currentPage = currentPage
        
        if (_binding != null) {
            bindData()
        }
    }
    
    private fun bindData() {
        val report = this.report ?: return
        
        when (pageType) {
            PAGE_TYPE_SUMMARY -> bindSummaryPage(report)
            PAGE_TYPE_REP -> bindRepPage(report)
        }
        
        // Update page indicators
        updatePageIndicators()
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Summary Page (Exercise Overview)
    // ═══════════════════════════════════════════════════════════════
    
    private fun bindSummaryPage(report: PostTrainingReport) {
        // Page type label
        binding.tvPageType.text = if (isArabic) "ملخص التمرين" else "EXERCISE SUMMARY"
        
        // Exercise name
        binding.tvExerciseName.text = if (isArabic) {
            report.exerciseName.ar
        } else {
            report.exerciseName.en
        }
        
        // Load hero image - try multiple sources
        val heroImageUri = report.heroFrame?.frameUri
            ?: report.getBestRepFrame()?.frameUri
            ?: report.bestReps.firstOrNull()?.frameCapture?.frameUri
            ?: report.repTimeline.firstOrNull { it.isBestRep }?.frameCapture?.frameUri
            ?: report.repTimeline.firstOrNull()?.frameCapture?.frameUri
        loadImage(heroImageUri)
        
        // Primary stats
        val stats = MetricDisplayBuilder.buildPrimaryStats(report, isArabic)
        bindPrimaryStats(stats)
        
        // Message
        val message = MetricDisplayBuilder.buildMessage(report, isArabic)
        bindMessage(message)
        
        // Secondary metrics
        val secondaryMetrics = MetricDisplayBuilder.buildSecondaryMetrics(report, isArabic)
        bindSecondaryMetrics(secondaryMetrics)
        
        // Show swipe hint if there are reps
        binding.tvSwipeHint.isVisible = report.repTimeline.size > 1
        binding.tvSwipeHint.text = if (isArabic) "← اسحب لرؤية العدات" else "← Swipe for reps"
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Rep Page (Individual Rep Details)
    // ═══════════════════════════════════════════════════════════════
    
    private fun bindRepPage(report: PostTrainingReport) {
        val timeline = report.repTimeline
        if (repIndex >= timeline.size) return
        
        val rep = timeline[repIndex]
        
        // Page type label
        val repNum = rep.repNumber
        binding.tvPageType.text = when {
            rep.isBestRep -> if (isArabic) "⭐ أفضل عدة" else "⭐ BEST REP"
            rep.isWorstRep -> if (isArabic) "📈 للتحسين" else "📈 TO IMPROVE"
            else -> if (isArabic) "العدة #$repNum" else "REP #$repNum"
        }
        
        // Exercise name
        binding.tvExerciseName.text = if (isArabic) {
            report.exerciseName.ar
        } else {
            report.exerciseName.en
        }
        
        // Load rep image
        loadImage(rep.frameCapture?.frameUri)
        
        // Primary stats for rep
        val stats = MetricDisplayBuilder.buildRepStats(rep, isArabic)
        bindPrimaryStats(stats)
        
        // Message
        val message = MetricDisplayBuilder.buildRepMessage(rep, isArabic)
        bindMessage(message)
        
        // Hide secondary metrics for rep view (keep it simple)
        binding.secondaryMetricsContainer.isVisible = false
        
        // Hide swipe hint on rep pages
        binding.tvSwipeHint.isVisible = false
    }
    
    // ═══════════════════════════════════════════════════════════════
    // UI Binding Helpers
    // ═══════════════════════════════════════════════════════════════
    
    private fun loadImage(uri: String?) {
        if (uri.isNullOrEmpty()) {
            binding.ivFullScreenImage.visibility = View.GONE
            binding.placeholderContainer.visibility = View.VISIBLE
            return
        }
        
        val file = File(uri)
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                binding.ivFullScreenImage.setImageBitmap(bitmap)
                binding.ivFullScreenImage.visibility = View.VISIBLE
                binding.placeholderContainer.visibility = View.GONE
            } catch (e: Exception) {
                binding.ivFullScreenImage.visibility = View.GONE
                binding.placeholderContainer.visibility = View.VISIBLE
            }
        } else {
            binding.ivFullScreenImage.visibility = View.GONE
            binding.placeholderContainer.visibility = View.VISIBLE
        }
    }
    
    private fun bindPrimaryStats(stats: List<StatItem>) {
        if (stats.isEmpty()) return
        
        // Stat 1
        if (stats.isNotEmpty()) {
            val stat = stats[0]
            binding.tvStat1Value.text = stat.value
            binding.tvStat1Label.text = stat.label
            binding.tvStat1Value.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (stat.isPrimary) R.color.primary else R.color.text_primary
                )
            )
        }
        
        // Stat 2
        if (stats.size > 1) {
            val stat = stats[1]
            binding.tvStat2Value.text = stat.value
            binding.tvStat2Label.text = stat.label
            binding.stat2Container.isVisible = true
        } else {
            binding.stat2Container.isVisible = false
        }
        
        // Stat 3
        if (stats.size > 2) {
            val stat = stats[2]
            binding.tvStat3Value.text = stat.value
            binding.tvStat3Label.text = stat.label
            binding.stat3Container.isVisible = true
        } else {
            binding.stat3Container.isVisible = false
        }
    }
    
    private fun bindMessage(message: MessageItem) {
        val fullMessage = "${message.icon} ${message.text}"
        binding.tvMessage.text = if (message.subtext.isNotEmpty()) {
            "$fullMessage\n${message.subtext}"
        } else {
            fullMessage
        }
        
        // Color based on type
        val color = when (message.type) {
            InsightType.CELEBRATION -> R.color.success
            InsightType.DANGER_WARNING -> R.color.error
            InsightType.FOCUS_POINT -> R.color.warning
        }
        binding.tvMessage.setTextColor(ContextCompat.getColor(requireContext(), color))
    }
    
    private fun bindSecondaryMetrics(metrics: List<MetricDisplayItem>) {
        binding.secondaryMetricsContainer.removeAllViews()
        
        if (metrics.isEmpty()) {
            binding.secondaryMetricsContainer.isVisible = false
            return
        }
        
        binding.secondaryMetricsContainer.isVisible = true
        
        // Limit to max 8 metrics (4 rows) to prevent overflow
        val limitedMetrics = metrics.take(8)
        
        // Create rows of metrics (2 per row)
        val chunkedMetrics = limitedMetrics.chunked(2)
        
        for (row in chunkedMetrics) {
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8 * resources.displayMetrics.density).toInt()
                }
            }
            
            for (metric in row) {
                val metricView = createMetricView(metric)
                rowLayout.addView(metricView)
            }
            
            // Add spacer if only one item in row
            if (row.size == 1) {
                val spacer = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                rowLayout.addView(spacer)
            }
            
            binding.secondaryMetricsContainer.addView(rowLayout)
        }
    }
    
    private fun createMetricView(metric: MetricDisplayItem): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            
            // Glass background
            background = GradientDrawable().apply {
                setColor(0x1AFFFFFF)
                cornerRadius = 8 * resources.displayMetrics.density
            }
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
        }
        
        // Icon
        val icon = TextView(requireContext()).apply {
            text = metric.icon
            textSize = 16f
        }
        container.addView(icon)
        
        // Label + Value
        val textContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = (8 * resources.displayMetrics.density).toInt()
            }
        }
        
        val label = TextView(requireContext()).apply {
            text = metric.label
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            textSize = 11f
        }
        textContainer.addView(label)
        
        val value = TextView(requireContext()).apply {
            text = metric.value
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        textContainer.addView(value)
        
        container.addView(textContainer)
        
        // Status indicator (if present)
        metric.status?.let { status ->
            val statusDot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (8 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt()
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(status.getColor())
                }
            }
            container.addView(statusDot)
        }
        
        return container
    }
    
    private fun updatePageIndicators() {
        binding.pageIndicatorContainer.removeAllViews()
        
        if (totalPages <= 1) {
            binding.pageIndicatorContainer.isVisible = false
            return
        }
        
        binding.pageIndicatorContainer.isVisible = true
        
        val density = resources.displayMetrics.density
        val canGoLeft = currentPage > 0
        val canGoRight = currentPage < totalPages - 1
        
        // Left arrow (if can go left)
        if (canGoLeft) {
            val leftArrow = TextView(requireContext()).apply {
                text = "‹"
                textSize = 28f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (12 * density).toInt()
                }
            }
            binding.pageIndicatorContainer.addView(leftArrow)
        }
        
        // Page number indicator
        val pageText = TextView(requireContext()).apply {
            text = "${currentPage + 1} / $totalPages"
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        binding.pageIndicatorContainer.addView(pageText)
        
        // Right arrow (if can go right)
        if (canGoRight) {
            val rightArrow = TextView(requireContext()).apply {
                text = "›"
                textSize = 28f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (12 * density).toInt()
                }
            }
            binding.pageIndicatorContainer.addView(rightArrow)
        }
    }
}
