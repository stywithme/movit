package com.trainingvalidator.poc.ui.report

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityReportBinding
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.report.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * ReportActivity - Post-Training Report Screen
 * 
 * Displays comprehensive training report with:
 * - Performance summary
 * - Best reps with captured frames
 * - Error analysis
 * - Rep timeline
 * - Improvement tips
 */
class ReportActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ReportActivity"
        
        const val EXTRA_REPORT_ID = "report_id"
        const val EXTRA_LOAD_LATEST = "load_latest"
        const val EXTRA_EXERCISE_NAME = "exercise_name"
        
        /**
         * Create intent to show specific report
         */
        fun createIntent(context: Context, reportId: String): Intent {
            return Intent(context, ReportActivity::class.java).apply {
                putExtra(EXTRA_REPORT_ID, reportId)
            }
        }
        
        /**
         * Create intent to show latest report
         */
        fun createLatestIntent(context: Context): Intent {
            return Intent(context, ReportActivity::class.java).apply {
                putExtra(EXTRA_LOAD_LATEST, true)
            }
        }
    }
    
    private lateinit var binding: ActivityReportBinding
    
    private val viewModel: ReportViewModel by viewModels { 
        ReportViewModel.Factory(application)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupFullscreen()
        
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupViewPager()
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
    }
    
    private fun setupViewPager() {
        binding.viewPager.adapter = ReportPagerAdapter(this)
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "⭐ Best"
                1 -> "⚠️ Errors"
                2 -> "📊 States"
                3 -> "💡 Tips"
                else -> ""
            }
        }.attach()
        
        // If there are DANGER alerts, switch to Errors tab after loading
        lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                if (report?.hasDangerAlerts() == true) {
                    binding.viewPager.currentItem = 1 // Switch to Errors tab
                }
            }
        }
    }
    
    private fun setupButtons() {
        binding.btnTrainAgain.setOnClickListener {
            // Return to training with same exercise
            val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME)
            // For now, just finish - implement training restart later
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
                    // Show error and finish
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
        val language = getCurrentLanguage()
        
        // Hero section - use current language
        binding.tvExerciseName.text = report.exerciseName.get(language).ifBlank { report.exerciseName.en }
        // Show rating instead of difficulty (difficulty has been removed)
        val ratingText = when (report.summary.rating) {
            PerformanceRating.EXCELLENT -> getString(R.string.excellent)
            PerformanceRating.GOOD -> getString(R.string.good)
            PerformanceRating.FAIR -> getString(R.string.medium)
            PerformanceRating.NEEDS_WORK -> getString(R.string.needs_work)
        }
        binding.tvDifficulty.text = ratingText
        binding.tvMotivationalMessage.text = report.summary.motivationalMessage.get(language).ifBlank { 
            report.summary.motivationalMessage.en 
        }
        
        // Rating icon based on performance (with DANGER alert override)
        binding.tvRatingIcon.text = when {
            report.hasDangerAlerts() -> "🚨" // DANGER takes priority
            report.shouldCelebrate() -> "🎉"  // Celebration for excellent
            else -> when (report.summary.rating) {
                PerformanceRating.EXCELLENT -> "🏆"
                PerformanceRating.GOOD -> "🏅"
                PerformanceRating.FAIR -> "💪"
                PerformanceRating.NEEDS_WORK -> "📈"
            }
        }
        
        // Update hero background color based on rating (DANGER override)
        val heroColorRes = when {
            report.hasDangerAlerts() -> R.drawable.gradient_report_hero_needs_work
            else -> when (report.summary.rating) {
                PerformanceRating.EXCELLENT -> R.drawable.gradient_report_hero_excellent
                PerformanceRating.GOOD -> R.drawable.gradient_report_hero_good
                PerformanceRating.FAIR -> R.drawable.gradient_report_hero_fair
                PerformanceRating.NEEDS_WORK -> R.drawable.gradient_report_hero_needs_work
            }
        }
        // Only set if drawable exists
        try {
            binding.heroBackground.setBackgroundResource(heroColorRes)
        } catch (e: Exception) {
            // Use default gradient
        }
        
        // State-based summary
        binding.tvTotalReps.text = report.summary.totalReps.toString()
        binding.tvAccuracy.text = report.summary.getFormattedScore() // Show score instead of accuracy
        binding.tvDuration.text = report.summary.getFormattedDuration()
        binding.progressAccuracy.progress = report.summary.averageScore.toInt()
        
        // State-based rep counts with localized strings
        val stateBreakdown = report.summary.stateBreakdown
        val perfectText = getString(R.string.perfect_count_format, stateBreakdown.perfectCount)
        val goodText = getString(R.string.good_count_format, stateBreakdown.normalCount)
        binding.tvCorrectReps.text = "⭐ $perfectText  ✓ $goodText"
        
        binding.tvIncorrectReps.text = when {
            stateBreakdown.dangerCount > 0 -> {
                val dangerText = getString(R.string.danger_count_format, stateBreakdown.dangerCount)
                val warningText = getString(R.string.warning_count_format, stateBreakdown.warningCount)
                "🚨 $dangerText  ⚠️ $warningText"
            }
            stateBreakdown.warningCount > 0 -> {
                val warningText = getString(R.string.warning_count_format, stateBreakdown.warningCount)
                "⚠️ $warningText"
            }
            else -> "✓ ${getString(R.string.all_good)}"
        }
        
        // Color score based on value and DANGER state
        val scoreColor = when {
            stateBreakdown.dangerCount > 0 -> 0xFFB71C1C.toInt() // Dark Red for DANGER
            report.summary.averageScore >= 90 -> 0xFF4CAF50.toInt() // Green
            report.summary.averageScore >= 75 -> 0xFF8BC34A.toInt() // Light Green
            report.summary.averageScore >= 60 -> 0xFFFFC107.toInt() // Yellow
            else -> 0xFFF44336.toInt() // Red
        }
        binding.tvAccuracy.setTextColor(scoreColor)
        
        Log.d(TAG, "UI updated for report: ${report.id}")
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
    
    // ==================== ViewPager Adapter ====================
    
    inner class ReportPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 4
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> BestRepsFragment()
                1 -> ErrorsFragment()
                2 -> StateBreakdownFragment()  // Replaced Timeline with State Breakdown
                3 -> TipsFragment()
                else -> BestRepsFragment()
            }
        }
    }
}

// ==================== Tab Fragments ====================

/**
 * Best Reps Tab - Shows perfect reps with captured frames
 */
class BestRepsFragment : Fragment() {
    
    private val viewModel: ReportViewModel by lazy {
        (requireActivity() as ReportActivity).let {
            androidx.lifecycle.ViewModelProvider(it)[ReportViewModel::class.java]
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(16, 16, 16, 16)
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let {
                    recyclerView.adapter = BestRepsAdapter(it.bestReps)
                }
            }
        }
        
        return recyclerView
    }
    
    inner class BestRepsAdapter(
        private val bestReps: List<BestRepHighlight>
    ) : RecyclerView.Adapter<BestRepsAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val frameContainer: View = itemView.findViewById(R.id.frameContainer)
            val ivFrame: ImageView = itemView.findViewById(R.id.ivFrame)
            val placeholderFrame: View = itemView.findViewById(R.id.placeholderFrame)
            val tvRepNumber: TextView = itemView.findViewById(R.id.tvRepNumber)
            val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
            val tvReason: TextView = itemView.findViewById(R.id.tvReason)
            val tvScore: TextView = itemView.findViewById(R.id.tvScore)
            val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)
            val tvExpandHint: TextView = itemView.findViewById(R.id.tvExpandHint)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_best_rep, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bestRep = bestReps[position]
            val context = holder.itemView.context
            
            holder.tvRepNumber.text = context.getString(R.string.rep_number_format, bestRep.repNumber)
            holder.tvDuration.text = "⏱ ${bestRep.getFormattedDuration()}"
            
            // Score display
            holder.tvScore.text = context.getString(R.string.score_format, bestRep.score.toInt())
            
            // Badge based on position
            holder.tvBadge.text = when (position) {
                0 -> "🏆 Best Rep"
                else -> "⭐ Great Rep"
            }
            
            // Meaningful message based on state and reasons
            val message = when {
                bestRep.reasons.isNotEmpty() -> {
                    "✓ ${bestRep.reasons.first().en}"
                }
                bestRep.worstState == JointState.PERFECT -> {
                    "✓ Perfect execution! All joints maintained optimal positions."
                }
                bestRep.worstState == JointState.NORMAL -> {
                    "✓ Great form! Minor adjustments could make it even better."
                }
                bestRep.score >= 90 -> {
                    "✓ Excellent performance with ${bestRep.score.toInt()}% accuracy."
                }
                bestRep.score >= 70 -> {
                    "✓ Good effort! Keep practicing for even better results."
                }
                else -> {
                    "✓ Best performance in this session."
                }
            }
            holder.tvReason.text = message
            
            // Load frame if available
            bestRep.frameCapture?.let { frame ->
                val file = File(frame.frameUri.ifEmpty { frame.thumbnailUri })
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    holder.ivFrame.setImageBitmap(bitmap)
                    holder.ivFrame.visibility = View.VISIBLE
                    holder.placeholderFrame.visibility = View.GONE
                    holder.tvExpandHint.visibility = View.VISIBLE
                    
                    // Click to expand image
                    holder.frameContainer.setOnClickListener {
                        val title = if (position == 0) "🏆 Best Rep #${bestRep.repNumber}" else "⭐ Rep #${bestRep.repNumber}"
                        val details = "Score: ${bestRep.score.toInt()}% | Duration: ${bestRep.getFormattedDuration()}"
                        ImageViewerDialog(holder.itemView.context, frame, title, details).show()
                    }
                } else {
                    holder.ivFrame.visibility = View.GONE
                    holder.placeholderFrame.visibility = View.VISIBLE
                    holder.tvExpandHint.visibility = View.GONE
                    holder.frameContainer.setOnClickListener(null)
                }
            } ?: run {
                holder.ivFrame.visibility = View.GONE
                holder.placeholderFrame.visibility = View.VISIBLE
                holder.tvExpandHint.visibility = View.GONE
                holder.frameContainer.setOnClickListener(null)
            }
        }
        
        override fun getItemCount(): Int = bestReps.size
    }
}

/**
 * Errors Tab - Shows DANGER alerts and error analysis
 * 
 * STATE-BASED ERROR DISPLAY:
 * - DANGER alerts shown prominently at top 🚨
 * - Error analysis with state icons and messages
 * - Side-by-side comparison (error vs correct)
 * - Tips from exercise JSON
 */
class ErrorsFragment : Fragment() {
    
    private val viewModel: ReportViewModel by lazy {
        (requireActivity() as ReportActivity).let {
            androidx.lifecycle.ViewModelProvider(it)[ReportViewModel::class.java]
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = android.widget.ScrollView(requireContext())
        val mainContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        scrollView.addView(mainContainer)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let {
                    mainContainer.removeAllViews()
                    
                    val hasDangerAlerts = it.dangerAlerts.isNotEmpty()
                    val hasErrors = it.errorAnalysis.isNotEmpty()
                    
                    if (!hasDangerAlerts && !hasErrors) {
                        // Show celebration message 🎉
                        addPerfectTrainingMessage(mainContainer)
                    } else {
                        // Add DANGER alerts first 🚨
                        if (hasDangerAlerts) {
                            addDangerSection(mainContainer, it.dangerAlerts)
                        }
                        
                        // Add error analysis
                        if (hasErrors) {
                            addErrorsSection(mainContainer, it.errorAnalysis)
                        }
                    }
                }
            }
        }
        
        return scrollView
    }
    
    private fun addPerfectTrainingMessage(container: LinearLayout) {
        val messageView = TextView(requireContext()).apply {
            text = "🎉\n\n${getString(R.string.perfect_training_title)}\n\n${getString(R.string.perfect_training_desc)}"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF4CAF50.toInt())
            setPadding(32, 100, 32, 100)
        }
        container.addView(messageView)
    }
    
    private fun addDangerSection(container: LinearLayout, alerts: List<DangerAlert>) {
        // Section header
        val header = TextView(requireContext()).apply {
            text = "🚨 ${getString(R.string.safety_alerts)}"
            textSize = 18f
            setTextColor(0xFFB71C1C.toInt())
            setPadding(32, 16, 32, 8)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(header)
        
        // Check if Arabic
        val isArabic = (requireActivity() as? ReportActivity)?.let {
            AppCompatDelegate.getApplicationLocales().let { locales ->
                if (locales.isEmpty) resources.configuration.locales[0]?.language == "ar"
                else locales[0]?.language == "ar"
            }
        } ?: false
        
        // Add danger alert cards
        alerts.forEach { alert ->
            val dangerCard = com.trainingvalidator.poc.ui.report.components.DangerAlertCard(requireContext())
            dangerCard.bind(alert, isArabic = isArabic)
            container.addView(dangerCard)
        }
        
        // Divider
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(32, 24, 32, 16)
            }
            setBackgroundColor(0x22FFFFFF)
        }
        container.addView(divider)
    }
    
    private fun addErrorsSection(container: LinearLayout, errors: List<ErrorAnalysisItem>) {
        // Section header (only if there's something to show)
        val dangerErrors = errors.filter { it.isDanger() }
        val warningErrors = errors.filter { !it.isDanger() }
        
        // Check if Arabic
        val isArabic = (requireActivity() as? ReportActivity)?.let {
            AppCompatDelegate.getApplicationLocales().let { locales ->
                if (locales.isEmpty) resources.configuration.locales[0]?.language == "ar"
                else locales[0]?.language == "ar"
            }
        } ?: false
        
        if (warningErrors.isNotEmpty()) {
            val header = TextView(requireContext()).apply {
                text = "⚠️ ${getString(R.string.areas_for_improvement)}"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(32, 16, 32, 8)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            container.addView(header)
            
            // Add error cards
            warningErrors.forEach { error ->
                val errorCard = com.trainingvalidator.poc.ui.report.components.ErrorComparisonCard(requireContext())
                errorCard.bind(error, isArabic = isArabic)
                container.addView(errorCard)
            }
        }
    }
}

/**
 * State Breakdown Tab - Shows state distribution and analysis
 * 
 * Replaces Timeline with more useful information:
 * - State distribution bar
 * - Score analysis
 * - Optimization suggestions based on states
 */
class StateBreakdownFragment : Fragment() {
    
    private val viewModel: ReportViewModel by lazy {
        (requireActivity() as ReportActivity).let {
            androidx.lifecycle.ViewModelProvider(it)[ReportViewModel::class.java]
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = android.widget.ScrollView(requireContext())
        val mainContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        scrollView.addView(mainContainer)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let {
                    mainContainer.removeAllViews()
                    buildStateBreakdownUI(mainContainer, it)
                }
            }
        }
        
        return scrollView
    }
    
    private fun buildStateBreakdownUI(container: LinearLayout, report: PostTrainingReport) {
        val breakdown = report.summary.stateBreakdown
        
        // Title
        val title = TextView(requireContext()).apply {
            text = "📊 State Distribution"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        container.addView(title)
        
        // State Distribution Bar
        val stateBar = com.trainingvalidator.poc.ui.report.components.StateDistributionBar(requireContext())
        stateBar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (48 * resources.displayMetrics.density).toInt()
        ).apply {
            bottomMargin = (24 * resources.displayMetrics.density).toInt()
        }
        stateBar.setBreakdown(breakdown)
        container.addView(stateBar)
        
        // State counts grid
        addStateCountsGrid(container, breakdown)
        
        // Score analysis
        addScoreAnalysis(container, report)
        
        // Optimization suggestions
        addOptimizationSuggestions(container, report, breakdown)
    }
    
    private fun addStateCountsGrid(container: LinearLayout, breakdown: StateBreakdown) {
        val gridLayout = android.widget.GridLayout(requireContext()).apply {
            columnCount = 3
            setPadding(0, 16, 0, 24)
        }
        
        data class StateItem(val icon: String, val name: String, val count: Int, val color: Int)
        
        val items = listOf(
            StateItem("⭐", "Perfect", breakdown.perfectCount, 0xFF4CAF50.toInt()),
            StateItem("✓", "Good", breakdown.normalCount, 0xFFFFEB3B.toInt()),
            StateItem("~", "Acceptable", breakdown.padCount, 0xFFFF9800.toInt()),
            StateItem("⚠️", "Warning", breakdown.warningCount, 0xFFFF5252.toInt()),
            StateItem("🚨", "Danger", breakdown.dangerCount, 0xFFB71C1C.toInt())
        ).filter { it.count > 0 }
        
        items.forEach { item ->
            val itemView = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(16, 16, 16, 16)
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                }
            }
            
            val countText = TextView(requireContext()).apply {
                text = "${item.icon} ${item.count}"
                textSize = 20f
                setTextColor(item.color)
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            
            val labelText = TextView(requireContext()).apply {
                text = item.name
                textSize = 12f
                setTextColor(0xFFB0B0B0.toInt())
                gravity = android.view.Gravity.CENTER
            }
            
            itemView.addView(countText)
            itemView.addView(labelText)
            gridLayout.addView(itemView)
        }
        
        container.addView(gridLayout)
    }
    
    private fun addScoreAnalysis(container: LinearLayout, report: PostTrainingReport) {
        // Section header
        val header = TextView(requireContext()).apply {
            text = "📈 Score Analysis"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 16)
        }
        container.addView(header)
        
        // Score summary
        val scoreCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0x1AFFFFFF)
        }
        
        val avgScore = TextView(requireContext()).apply {
            text = "Average Score: ${report.summary.getFormattedScore()}"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        scoreCard.addView(avgScore)
        
        val countedRatio = TextView(requireContext()).apply {
            text = "Counted Reps: ${report.summary.countedReps}/${report.summary.totalReps} (${String.format("%.0f%%", report.summary.countedRatio * 100)})"
            textSize = 14f
            setTextColor(0xFFB0B0B0.toInt())
            setPadding(0, 8, 0, 0)
        }
        scoreCard.addView(countedRatio)
        
        if (report.summary.invalidatedReps > 0) {
            val invalidated = TextView(requireContext()).apply {
                text = "🚨 Invalidated Reps: ${report.summary.invalidatedReps}"
                textSize = 14f
                setTextColor(0xFFB71C1C.toInt())
                setPadding(0, 8, 0, 0)
            }
            scoreCard.addView(invalidated)
        }
        
        container.addView(scoreCard)
    }
    
    private fun addOptimizationSuggestions(container: LinearLayout, report: PostTrainingReport, breakdown: StateBreakdown) {
        // Section header
        val header = TextView(requireContext()).apply {
            text = "🎯 Optimizations"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 16)
        }
        container.addView(header)
        
        val suggestions = mutableListOf<Pair<String, String>>()
        
        // Generate suggestions based on state distribution
        if (breakdown.dangerCount > 0) {
            suggestions.add("🚨" to "Focus on safety! ${breakdown.dangerCount} reps reached dangerous positions. Review the Errors tab for details.")
        }
        
        if (breakdown.warningCount > breakdown.perfectCount) {
            suggestions.add("⚠️" to "Aim for better form. Most reps had warnings. Try slowing down and focusing on technique.")
        }
        
        if (breakdown.perfectCount > 0 && breakdown.perfectRatio < 0.5f) {
            suggestions.add("⭐" to "You had ${breakdown.perfectCount} perfect reps! Try to replicate that form more consistently.")
        }
        
        if (breakdown.perfectRatio >= 0.7f && breakdown.dangerCount == 0) {
            suggestions.add("🏆" to "Excellent work! ${String.format("%.0f%%", breakdown.perfectRatio * 100)} of your reps were perfect. Keep it up!")
        }
        
        if (report.summary.averageScore < 50 && breakdown.dangerCount == 0) {
            suggestions.add("💪" to "Good effort! Focus on deepening your range of motion to improve your score.")
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("📊" to "Complete more reps to get personalized optimization suggestions.")
        }
        
        suggestions.forEach { (icon, text) ->
            val suggestionView = TextView(requireContext()).apply {
                this.text = "$icon  $text"
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 12, 0, 12)
            }
            container.addView(suggestionView)
        }
    }
}

/**
 * Tips Tab - Shows improvement tips
 */
class TipsFragment : Fragment() {
    
    private val viewModel: ReportViewModel by lazy {
        (requireActivity() as ReportActivity).let {
            androidx.lifecycle.ViewModelProvider(it)[ReportViewModel::class.java]
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(16, 16, 16, 16)
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let {
                    recyclerView.adapter = TipsAdapter(it.improvementTips)
                }
            }
        }
        
        return recyclerView
    }
    
    inner class TipsAdapter(
        private val tips: List<ImprovementTip>
    ) : RecyclerView.Adapter<TipsAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvPriority: TextView = itemView.findViewById(R.id.tvPriority)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
            val tvNextFocus: TextView = itemView.findViewById(R.id.tvNextFocus)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_improvement_tip, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tip = tips[position]
            
            // Use icon from tip or generate based on severity
            holder.tvPriority.text = when {
                tip.isNextFocus -> "🎯"
                tip.severity == TipSeverity.CRITICAL -> "🚨"
                tip.severity == TipSeverity.IMPORTANT -> "⚠️"
                else -> tip.icon
            }
            
            holder.tvTitle.text = tip.title.en
            holder.tvDescription.text = tip.description.en
            
            // Highlight critical tips
            val titleColor = when (tip.severity) {
                TipSeverity.CRITICAL -> 0xFFB71C1C.toInt()
                TipSeverity.IMPORTANT -> 0xFFFF9800.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            holder.tvTitle.setTextColor(titleColor)
            
            holder.tvNextFocus.visibility = if (tip.isNextFocus) View.VISIBLE else View.GONE
        }
        
        override fun getItemCount(): Int = tips.size
    }
}

/**
 * Empty state adapter
 */
class EmptyStateAdapter(
    private val message: String
) : RecyclerView.Adapter<EmptyStateAdapter.ViewHolder>() {
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView as TextView
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = android.view.Gravity.CENTER
            textSize = 18f
            setTextColor(0xFFB0B0B0.toInt())
            setPadding(32, 100, 32, 100)
        }
        return ViewHolder(textView)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvMessage.text = message
    }
    
    override fun getItemCount(): Int = 1
}
