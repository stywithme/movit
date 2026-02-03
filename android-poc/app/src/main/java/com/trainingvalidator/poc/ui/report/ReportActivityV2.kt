package com.trainingvalidator.poc.ui.report

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityReportV2Binding
import com.trainingvalidator.poc.training.models.MetricCode
import com.trainingvalidator.poc.training.report.*
import com.trainingvalidator.poc.ui.report.components.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ReportActivityV2 - Enhanced Post-Training Report Screen
 * 
 * Single scrollable screen with:
 * - Hero section with user image and stats
 * - Quick insight message
 * - 3 performance cards (Form, Safety, Control)
 * - Reps journey chart
 * - Key moments comparison
 * - Expandable full metrics
 * - Tips section
 * 
 * Glassmorphic design with minimal aesthetic
 */
class ReportActivityV2 : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ReportActivityV2"
        
        const val EXTRA_REPORT_ID = "report_id"
        const val EXTRA_LOAD_LATEST = "load_latest"
        const val EXTRA_EXERCISE_NAME = "exercise_name"
        
        /**
         * Create intent to show specific report
         */
        fun createIntent(context: Context, reportId: String): Intent {
            return Intent(context, ReportActivityV2::class.java).apply {
                putExtra(EXTRA_REPORT_ID, reportId)
            }
        }
        
        /**
         * Create intent to show latest report
         */
        fun createLatestIntent(context: Context): Intent {
            return Intent(context, ReportActivityV2::class.java).apply {
                putExtra(EXTRA_LOAD_LATEST, true)
            }
        }
    }
    
    private lateinit var binding: ActivityReportV2Binding
    
    private val viewModel: ReportViewModel by viewModels { 
        ReportViewModel.Factory(application)
    }
    
    private var isArabic = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupFullscreen()
        
        binding = ActivityReportV2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        
        isArabic = getCurrentLanguage() == "ar"
        
        setupToolbar()
        setupButtons()
        observeViewModel()
        
        loadReport()
    }
    
    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    shareReport()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupButtons() {
        binding.btnTrainAgain.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
        
        binding.btnDone.setOnClickListener {
            finish()
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let { updateUI(it) }
            }
        }
        
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
        
        lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                if (error != null) {
                    Log.e(TAG, "Error: $error")
                    finish()
                }
            }
        }
    }
    
    private fun loadReport() {
        val reportId = intent.getStringExtra(EXTRA_REPORT_ID)
        val loadLatest = intent.getBooleanExtra(EXTRA_LOAD_LATEST, false)
        
        when {
            reportId != null -> viewModel.loadReport(reportId)
            loadLatest -> viewModel.loadLatestReport()
            else -> {
                Log.e(TAG, "No report ID or load_latest flag provided")
                finish()
            }
        }
    }
    
    private fun updateUI(report: PostTrainingReport) {
        // Generate enhanced data if not present
        val quickInsight = report.quickInsight ?: QuickInsightGenerator.generate(report)
        val metrics = report.performanceMetrics ?: PerformanceMetricsBuilder.build(report)
        val config = report.exerciseConfig
        
        // 1. Hero Section
        binding.heroSection.bind(report, isArabic)
        
        // 2. Quick Insight
        binding.quickInsightCard.bind(quickInsight, isArabic)
        
        // 3. Performance Cards - show/hide based on exercise type
        updatePerformanceCards(metrics, config)
        
        // 4. Reps Journey Chart - only for rep-based exercises
        val isHold = config?.isHoldExercise() == true
        if (!isHold && report.repTimeline.size >= 2) {
            binding.repsJourneyChart.visibility = View.VISIBLE
            updateRepsJourneySection(report, metrics.controlCard.fatigueIndex)
        } else {
            binding.repsJourneyChart.visibility = View.GONE
            binding.repsJourneySummary.visibility = View.GONE
        }
        
        // 5. Key Moments - only for rep-based exercises with 2+ reps
        if (!isHold && report.repTimeline.size >= 2) {
            binding.keyMomentsSection.visibility = View.VISIBLE
            binding.keyMomentsSection.bind(report, isArabic)
        } else {
            binding.keyMomentsSection.visibility = View.GONE
        }
        
        // 6. Expandable Metrics - pass config for filtering
        binding.expandableMetricsSection.bind(metrics, isArabic, config)
        
        // 7. Tips Section
        updateTipsSection(report)
        
        // Update section titles based on language
        updateSectionTitles()
        
        Log.d(TAG, "UI updated for report: ${report.id}")
    }
    
    /**
     * Update performance cards based on exercise configuration
     */
    private fun updatePerformanceCards(metrics: EnhancedPerformanceMetrics, config: ExerciseConfigSnapshot?) {
        val isHold = config?.isHoldExercise() == true
        
        // Form Card - always visible, but content varies
        binding.formCard.bindFormCard(metrics.formCard, isArabic)
        binding.formCard.setOnExpandClickListener { showFormDetails(metrics.formCard) }
        
        // Safety Card - always visible
        binding.safetyCard.bindSafetyCard(metrics.safetyCard, isArabic)
        binding.safetyCard.setOnExpandClickListener { showSafetyDetails(metrics.safetyCard) }
        
        // Control Card - hide tempo/TUT for hold exercises
        if (isHold) {
            // For hold exercises, show stability instead of control
            val holdControlMetrics = ControlMetrics(
                overallScore = metrics.controlCard.overallScore,
                tempo = null,  // No tempo for hold
                totalTUT = null, // No TUT, show hold duration instead
                fatigueIndex = null
            )
            binding.controlCard.bindControlCard(holdControlMetrics, isArabic)
        } else {
            binding.controlCard.bindControlCard(metrics.controlCard, isArabic)
        }
        binding.controlCard.setOnExpandClickListener { showControlDetails(metrics.controlCard) }
        
        // Load Card - only for weighted exercises
        if (config?.supportsWeight == true && metrics.loadMetrics != null) {
            binding.loadSection.visibility = View.VISIBLE
            updateLoadSection(metrics.loadMetrics)
        } else {
            binding.loadSection.visibility = View.GONE
        }
    }
    
    /**
     * Update load section for weighted exercises
     */
    private fun updateLoadSection(loadMetrics: LoadMetrics) {
        binding.tvWeight.text = loadMetrics.getFormattedWeight()
        
        loadMetrics.totalVolume?.let {
            binding.tvVolume.visibility = View.VISIBLE
            binding.tvVolumeLabel.visibility = View.VISIBLE
            binding.tvVolume.text = loadMetrics.getFormattedVolume()
        } ?: run {
            binding.tvVolume.visibility = View.GONE
            binding.tvVolumeLabel.visibility = View.GONE
        }
        
        loadMetrics.est1RM?.let {
            binding.tvEst1RM.visibility = View.VISIBLE
            binding.tvEst1RMLabel.visibility = View.VISIBLE
            binding.tvEst1RM.text = loadMetrics.getFormattedEst1RM()
        } ?: run {
            binding.tvEst1RM.visibility = View.GONE
            binding.tvEst1RMLabel.visibility = View.GONE
        }
    }
    
    private fun updateRepsJourneySection(report: PostTrainingReport, fatigueIndex: Int?) {
        val timeline = report.repTimeline
        
        if (timeline.size < 2) {
            binding.repsJourneyChart.visibility = View.GONE
            binding.repsJourneySummary.visibility = View.GONE
            return
        }
        
        // Update chart
        binding.repsJourneyChart.setData(timeline, fatigueIndex)
        
        // Update summary text
        updateRepsJourneySummary(timeline, fatigueIndex)
    }
    
    private fun updateRepsJourneySummary(timeline: List<RepTimelineEntry>, fatigueIndex: Int?) {
        val totalReps = timeline.size
        
        // Good reps (score >= 80)
        val goodReps = timeline.filter { it.score >= 80 }
        if (goodReps.isNotEmpty()) {
            val firstGood = goodReps.first().repNumber
            val lastGood = goodReps.last().repNumber
            binding.tvRepsGood.visibility = View.VISIBLE
            binding.tvRepsGood.text = if (isArabic) {
                "🟢 العدات $firstGood-$lastGood: أداء ثابت وممتاز"
            } else {
                "🟢 Reps $firstGood-$lastGood: Consistent excellent performance"
            }
        } else {
            binding.tvRepsGood.visibility = View.GONE
        }
        
        // Fair reps (60-80)
        val fairReps = timeline.filter { it.score in 60f..80f }
        if (fairReps.isNotEmpty()) {
            val firstFair = fairReps.first().repNumber
            val lastFair = fairReps.last().repNumber
            binding.tvRepsFair.visibility = View.VISIBLE
            binding.tvRepsFair.text = if (isArabic) {
                "🟡 العدات $firstFair-$lastFair: تراجع طفيف"
            } else {
                "🟡 Reps $firstFair-$lastFair: Slight decline"
            }
        } else {
            binding.tvRepsFair.visibility = View.GONE
        }
        
        // Bad reps (< 60)
        val badReps = timeline.filter { it.score < 60 }
        if (badReps.isNotEmpty()) {
            val firstBad = badReps.first().repNumber
            val lastBad = badReps.last().repNumber
            binding.tvRepsBad.visibility = View.VISIBLE
            binding.tvRepsBad.text = if (isArabic) {
                "🔴 العدات $firstBad-$lastBad: تعب واضح"
            } else {
                "🔴 Reps $firstBad-$lastBad: Obvious fatigue"
            }
        } else {
            binding.tvRepsBad.visibility = View.GONE
        }
        
        // Tip based on fatigue
        fatigueIndex?.let { fatigue ->
            binding.tvRepsTip.visibility = View.VISIBLE
            binding.tvRepsTip.text = if (isArabic) {
                "💡 الجلسة القادمة: توقف عند ${fatigue - 1} عدة للحفاظ على الجودة"
            } else {
                "💡 Next session: Stop at ${fatigue - 1} reps to maintain quality"
            }
        } ?: run {
            binding.tvRepsTip.visibility = View.GONE
        }
    }
    
    private fun updateTipsSection(report: PostTrainingReport) {
        binding.tipsContainer.removeAllViews()
        
        if (report.improvementTips.isEmpty()) {
            binding.tipsSection.visibility = View.GONE
            return
        }
        
        binding.tipsSection.visibility = View.VISIBLE
        
        // Add tip cards
        report.improvementTips.take(3).forEach { tip ->
            val tipView = createTipCard(tip)
            binding.tipsContainer.addView(tipView)
        }
    }
    
    private fun createTipCard(tip: ImprovementTip): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }
            
            // Apply glassmorphic background
            val bg = GradientDrawable().apply {
                setColor(0x1AFFFFFF)
                setStroke(1, getBorderColorForSeverity(tip.severity))
                cornerRadius = 12 * resources.displayMetrics.density
            }
            background = bg
        }
        
        // Icon + Title row
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        val icon = TextView(this).apply {
            text = when {
                tip.isNextFocus -> "🎯"
                tip.severity == TipSeverity.CRITICAL -> "🚨"
                tip.severity == TipSeverity.IMPORTANT -> "⚠️"
                else -> tip.icon
            }
            textSize = 20f
        }
        titleRow.addView(icon)
        
        val title = TextView(this).apply {
            text = if (isArabic) tip.title.ar else tip.title.en
            textSize = 15f
            setTextColor(getTitleColorForSeverity(tip.severity))
            setPadding((8 * resources.displayMetrics.density).toInt(), 0, 0, 0)
        }
        titleRow.addView(title)
        
        container.addView(titleRow)
        
        // Description
        val description = TextView(this).apply {
            text = if (isArabic) tip.description.ar else tip.description.en
            textSize = 13f
            setTextColor(0xFFB8C8C8.toInt())
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        container.addView(description)
        
        // Next focus badge
        if (tip.isNextFocus) {
            val badge = TextView(this).apply {
                text = if (isArabic) "🎯 تركيز الجلسة القادمة" else "🎯 Next Session Focus"
                textSize = 11f
                setTextColor(0xFF8AF851.toInt())
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            container.addView(badge)
        }
        
        return container
    }
    
    private fun getBorderColorForSeverity(severity: TipSeverity): Int {
        return when (severity) {
            TipSeverity.CRITICAL -> 0x33FF5252
            TipSeverity.IMPORTANT -> 0x33FFC107
            TipSeverity.HELPFUL -> 0x33FFFFFF
        }
    }
    
    private fun getTitleColorForSeverity(severity: TipSeverity): Int {
        return when (severity) {
            TipSeverity.CRITICAL -> 0xFFFF5252.toInt()
            TipSeverity.IMPORTANT -> 0xFFFFC107.toInt()
            TipSeverity.HELPFUL -> 0xFFE7F1F1.toInt()
        }
    }
    
    private fun updateSectionTitles() {
        binding.tvPerformanceTitle.text = if (isArabic) "📊 أداؤك" else "📊 Your Performance"
        binding.tvRepsJourneyTitle.text = if (isArabic) "📈 رحلة العدات" else "📈 Reps Journey"
        binding.tvTipsTitle.text = if (isArabic) "💡 نصائح لك" else "💡 Tips for You"
    }
    
    private fun showFormDetails(metrics: FormMetrics) {
        // Future: Show bottom sheet or dialog with form details
        binding.expandableMetricsSection.expand()
        binding.scrollView.smoothScrollTo(0, binding.expandableMetricsSection.top)
    }
    
    private fun showSafetyDetails(metrics: SafetyMetrics) {
        binding.expandableMetricsSection.expand()
        binding.scrollView.smoothScrollTo(0, binding.expandableMetricsSection.top)
    }
    
    private fun showControlDetails(metrics: ControlMetrics) {
        binding.expandableMetricsSection.expand()
        binding.scrollView.smoothScrollTo(0, binding.expandableMetricsSection.top)
    }
    
    private fun shareReport() {
        // TODO: Implement report sharing
        // - Generate image from report
        // - Share via Intent
    }
    
    private fun getCurrentLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) {
            resources.configuration.locales[0]
        } else {
            appLocales[0]
        }
        return locale?.language ?: "en"
    }
}
