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
                2 -> "📋 Timeline"
                3 -> "💡 Tips"
                else -> ""
            }
        }.attach()
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
        // Hero section
        binding.tvExerciseName.text = report.exerciseName.en
        binding.tvDifficulty.text = report.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }
        binding.tvMotivationalMessage.text = report.summary.motivationalMessage.en
        
        // Rating icon based on performance
        binding.tvRatingIcon.text = when (report.summary.rating) {
            PerformanceRating.EXCELLENT -> "🏆"
            PerformanceRating.GOOD -> "🏅"
            PerformanceRating.FAIR -> "💪"
            PerformanceRating.NEEDS_WORK -> "📈"
        }
        
        // Update hero background color based on rating
        val heroColorRes = when (report.summary.rating) {
            PerformanceRating.EXCELLENT -> R.drawable.gradient_report_hero_excellent
            PerformanceRating.GOOD -> R.drawable.gradient_report_hero_good
            PerformanceRating.FAIR -> R.drawable.gradient_report_hero_fair
            PerformanceRating.NEEDS_WORK -> R.drawable.gradient_report_hero_needs_work
        }
        // Only set if drawable exists
        try {
            binding.heroBackground.setBackgroundResource(heroColorRes)
        } catch (e: Exception) {
            // Use default gradient
        }
        
        // Summary card
        binding.tvTotalReps.text = report.summary.totalReps.toString()
        binding.tvAccuracy.text = report.summary.getFormattedAccuracy()
        binding.tvDuration.text = report.summary.getFormattedDuration()
        binding.progressAccuracy.progress = report.summary.accuracy.toInt()
        
        binding.tvCorrectReps.text = "✓ ${report.summary.correctReps} correct"
        binding.tvIncorrectReps.text = "✗ ${report.summary.incorrectReps} with errors"
        
        // Color accuracy based on value
        val accuracyColor = when {
            report.summary.accuracy >= 90 -> 0xFF4CAF50.toInt() // Green
            report.summary.accuracy >= 75 -> 0xFF8BC34A.toInt() // Light Green
            report.summary.accuracy >= 60 -> 0xFFFFC107.toInt() // Yellow
            else -> 0xFFF44336.toInt() // Red
        }
        binding.tvAccuracy.setTextColor(accuracyColor)
        
        Log.d(TAG, "UI updated for report: ${report.id}")
    }
    
    // ==================== ViewPager Adapter ====================
    
    inner class ReportPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 4
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> BestRepsFragment()
                1 -> ErrorsFragment()
                2 -> TimelineFragment()
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
            val ivFrame: ImageView = itemView.findViewById(R.id.ivFrame)
            val tvRepNumber: TextView = itemView.findViewById(R.id.tvRepNumber)
            val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
            val tvReason: TextView = itemView.findViewById(R.id.tvReason)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_best_rep, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bestRep = bestReps[position]
            
            holder.tvRepNumber.text = "Rep #${bestRep.repNumber}"
            holder.tvDuration.text = bestRep.getFormattedDuration()
            holder.tvReason.text = bestRep.reasons.firstOrNull()?.en ?: "Perfect form"
            
            // Load frame if available
            bestRep.frameCapture?.let { frame ->
                val file = File(frame.thumbnailUri)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    holder.ivFrame.setImageBitmap(bitmap)
                    holder.ivFrame.visibility = View.VISIBLE
                } else {
                    holder.ivFrame.visibility = View.GONE
                }
            } ?: run {
                holder.ivFrame.visibility = View.GONE
            }
        }
        
        override fun getItemCount(): Int = bestReps.size
    }
}

/**
 * Errors Tab - Shows error analysis
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
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(16, 16, 16, 16)
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let {
                    if (it.errorAnalysis.isEmpty()) {
                        // Show empty state
                        recyclerView.adapter = EmptyStateAdapter("No errors! Perfect training!")
                    } else {
                        recyclerView.adapter = ErrorsAdapter(it.errorAnalysis)
                    }
                }
            }
        }
        
        return recyclerView
    }
    
    inner class ErrorsAdapter(
        private val errors: List<ErrorAnalysisItem>
    ) : RecyclerView.Adapter<ErrorsAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvErrorName: TextView = itemView.findViewById(R.id.tvErrorName)
            val tvErrorCount: TextView = itemView.findViewById(R.id.tvErrorCount)
            val tvTip: TextView = itemView.findViewById(R.id.tvTip)
            val tvAffectedReps: TextView = itemView.findViewById(R.id.tvAffectedReps)
            val ivErrorFrame: ImageView = itemView.findViewById(R.id.ivErrorFrame)
            val ivBestFrame: ImageView = itemView.findViewById(R.id.ivBestFrame)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_error_card, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val error = errors[position]
            
            holder.tvErrorName.text = error.message.en
            holder.tvErrorCount.text = "${error.count}x"
            holder.tvTip.text = "💡 ${error.tip.en}"
            holder.tvAffectedReps.text = "Reps: ${error.getAffectedRepsText()}"
            
            // Load error frame
            error.errorFrame?.let { frame ->
                loadFrame(holder.ivErrorFrame, frame.thumbnailUri)
            }
            
            // Load best rep frame for comparison
            error.bestRepFrame?.let { frame ->
                loadFrame(holder.ivBestFrame, frame.thumbnailUri)
            }
        }
        
        private fun loadFrame(imageView: ImageView, uri: String) {
            val file = File(uri)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.GONE
            }
        }
        
        override fun getItemCount(): Int = errors.size
    }
}

/**
 * Timeline Tab - Shows rep-by-rep timeline
 */
class TimelineFragment : Fragment() {
    
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
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
        }
        
        container.addView(recyclerView)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let {
                    recyclerView.adapter = TimelineAdapter(it.repTimeline) { rep ->
                        viewModel.selectRep(rep)
                    }
                }
            }
        }
        
        return container
    }
    
    inner class TimelineAdapter(
        private val timeline: List<RepTimelineEntry>,
        private val onRepClick: (RepTimelineEntry) -> Unit
    ) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvRepNumber: TextView = itemView.findViewById(R.id.tvRepNumber)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
            val tvErrors: TextView = itemView.findViewById(R.id.tvErrors)
            val ivBadge: ImageView = itemView.findViewById(R.id.ivBadge)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_timeline_rep, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rep = timeline[position]
            
            holder.tvRepNumber.text = rep.repNumber.toString()
            holder.tvDuration.text = rep.getFormattedDuration()
            
            // Status icon
            val (statusIcon, statusColor) = when (rep.status) {
                RepStatus.CORRECT -> "✓" to 0xFF4CAF50.toInt()
                RepStatus.BEST_REP -> "⭐" to 0xFFFFD700.toInt()
                RepStatus.WORST_REP -> "⚠" to 0xFFF44336.toInt()
                RepStatus.HAS_ERRORS -> "✗" to 0xFFFFC107.toInt()
                RepStatus.FAILED -> "✗" to 0xFFF44336.toInt()
            }
            holder.tvStatus.text = statusIcon
            holder.tvStatus.setTextColor(statusColor)
            
            // Error summary
            if (rep.errors.isEmpty()) {
                holder.tvErrors.visibility = View.GONE
            } else {
                holder.tvErrors.text = rep.getErrorSummary()
                holder.tvErrors.visibility = View.VISIBLE
            }
            
            // Best rep badge
            holder.ivBadge.visibility = if (rep.isBestRep) View.VISIBLE else View.GONE
            
            holder.itemView.setOnClickListener {
                onRepClick(rep)
            }
        }
        
        override fun getItemCount(): Int = timeline.size
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
            
            holder.tvPriority.text = if (tip.isNextFocus) "🔮" else "#${tip.priority}"
            holder.tvTitle.text = tip.title.en
            holder.tvDescription.text = tip.description.en
            
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
