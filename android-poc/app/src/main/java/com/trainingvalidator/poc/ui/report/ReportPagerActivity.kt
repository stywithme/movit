package com.trainingvalidator.poc.ui.report

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.trainingvalidator.poc.databinding.ActivityReportPagerBinding
import com.trainingvalidator.poc.training.report.PostTrainingReport
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ReportPagerActivity - Full-screen swipeable report
 * 
 * Navigation:
 * - Swipe LEFT/RIGHT: Navigate between exercise summary and individual reps
 * - First page: Exercise summary with best frame and overall stats
 * - Subsequent pages: Individual rep details with specific metrics
 * 
 * Design:
 * - Full screen image (no skeleton overlay)
 * - Clean overlay with dynamic metrics based on exercise config
 * - Minimal UI - no cards, no clutter
 */
class ReportPagerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ReportPagerActivity"
        
        const val EXTRA_REPORT_ID = "report_id"
        const val EXTRA_LOAD_LATEST = "load_latest"
        
        /**
         * Create intent to show specific report
         */
        fun createIntent(context: Context, reportId: String): Intent {
            return Intent(context, ReportPagerActivity::class.java).apply {
                putExtra(EXTRA_REPORT_ID, reportId)
            }
        }
        
        /**
         * Create intent to show latest report
         */
        fun createLatestIntent(context: Context): Intent {
            return Intent(context, ReportPagerActivity::class.java).apply {
                putExtra(EXTRA_LOAD_LATEST, true)
            }
        }
    }
    
    private lateinit var binding: ActivityReportPagerBinding
    
    private val viewModel: ReportViewModel by viewModels {
        ReportViewModel.Factory(application)
    }
    
    private var pagerAdapter: RepPagerAdapter? = null
    private var isArabic = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupFullscreen()
        
        binding = ActivityReportPagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        isArabic = getCurrentLanguage() == "ar"
        
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
    
    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnShare.setOnClickListener {
            shareReport()
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let { setupPager(it) }
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
    
    private fun setupPager(report: PostTrainingReport) {
        pagerAdapter = RepPagerAdapter(this, report, isArabic)
        
        binding.viewPager.apply {
            adapter = pagerAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            
            // Add page change callback for analytics/logging
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    Log.d(TAG, "Page selected: $position - ${pagerAdapter?.getPageTitle(position)}")
                    
                    // Update any page-specific UI here
                    updatePageFragments(report, position)
                }
            })
        }
        
        // Update title
        binding.tvTitle.text = if (isArabic) {
            report.exerciseName.ar
        } else {
            report.exerciseName.en
        }
        
        Log.d(TAG, "Pager setup with ${pagerAdapter?.itemCount} pages")
    }
    
    /**
     * Update fragment data when page changes
     * This ensures page indicators are updated correctly
     */
    private fun updatePageFragments(report: PostTrainingReport, currentPosition: Int) {
        val adapter = pagerAdapter ?: return
        val totalPages = adapter.itemCount
        
        // Get the fragment for current position and update it
        supportFragmentManager.fragments.filterIsInstance<ReportPageFragment>().forEach { fragment ->
            // Update page indicator for all visible fragments
            fragment.setData(report, isArabic, totalPages, currentPosition)
        }
    }
    
    private fun shareReport() {
        // TODO: Implement report sharing
        // - Generate image from current page
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
