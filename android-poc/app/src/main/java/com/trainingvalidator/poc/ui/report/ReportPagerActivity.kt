package com.trainingvalidator.poc.ui.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.io.FileOutputStream
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityReportPagerBinding
import com.trainingvalidator.poc.training.report.PostTrainingReport
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ReportPagerActivity — V2 Full-screen swipeable report
 *
 * Navigation:
 *   Swipe LEFT/RIGHT through 7 screens per exercise:
 *     1. Hero         — Score + reps + duration + QuickInsight
 *     2. Overview     — Reps Journey chart + Form/Safety/Control cards
 *     3. Best/Worst   — Mirrored visual comparison
 *     4. Form         — ROM, Symmetry, Consistency deep-dive
 *     5. Safety       — Alignment, Stability, Danger alerts
 *     6. Control      — Tempo, TUT, Fatigue, Load
 *     7. Tips         — Exercise-specific tips + Share/Export
 *
 * Screens are included/excluded dynamically based on data availability.
 */
class ReportPagerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReportPagerActivity"

        const val EXTRA_REPORT_ID = "report_id"
        const val EXTRA_LOAD_LATEST = "load_latest"
        const val EXTRA_SESSION_ID = "progression_session_id"

        fun createIntent(context: Context, reportId: String, sessionId: String? = null): Intent {
            return Intent(context, ReportPagerActivity::class.java).apply {
                putExtra(EXTRA_REPORT_ID, reportId)
                if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
            }
        }

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

        isArabic = currentLanguage == "ar"

        setupButtons()
        observeViewModel()
        loadReport()
    }

    // ─── Fullscreen ─────────────────────────────────────────────

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ─── Buttons ────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { shareReport() }
    }

    // ─── Observers ──────────────────────────────────────────────

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

    // ─── Load ───────────────────────────────────────────────────

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

    // ─── Pager setup ────────────────────────────────────────────

    private fun setupPager(report: PostTrainingReport) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        pagerAdapter = RepPagerAdapter(this, report, isArabic, sessionId)

        binding.viewPager.apply {
            adapter = pagerAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val pageTitle = pagerAdapter?.getPageTitle(position)
                    Log.d(TAG, "Page $position: $pageTitle")

                    // Update title based on current screen
                    binding.tvTitle.text = pageTitle
                }
            })
        }

        // Initial title — exercise name
        binding.tvTitle.text = if (isArabic) {
            report.exerciseName.ar
        } else {
            report.exerciseName.en
        }

        Log.d(TAG, "V2 Pager: ${pagerAdapter?.itemCount} screens")
    }

    // ─── Share ──────────────────────────────────────────────────

    internal fun shareReport() {
        val report = viewModel.report.value ?: return
        val exerciseName = if (isArabic) report.exerciseName.ar else report.exerciseName.en
        val score = report.overallQuality?.getFormattedScore()
            ?: report.summary.getFormattedScore()

        val shareText = if (isArabic) {
            "🏋️ $exerciseName — الجودة: $score"
        } else {
            "🏋️ $exerciseName — Quality: $score"
        }

        // Capture screenshot of the current visible page
        val screenshotUri = captureScreenshot()
        if (screenshotUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, screenshotUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent,
                if (isArabic) "مشاركة التقرير" else "Share Report"
            ))
        } else {
            // Fallback to text-only share
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent,
                if (isArabic) "مشاركة التقرير" else "Share Report"
            ))
        }
    }

    private fun captureScreenshot(): Uri? {
        return try {
            // Hide UI chrome that shouldn't appear in the shared image
            val topBar = binding.topBar
            val heroFragment = supportFragmentManager.fragments
                .filterIsInstance<ReportPageFragment>()
                .firstOrNull()
            val heroView = heroFragment?.view

            val shareBtn = heroView?.findViewById<View>(R.id.btnShareHero)
            val swipeHint = heroView?.findViewById<View>(R.id.tvSwipeHint)

            topBar.visibility = View.INVISIBLE
            shareBtn?.visibility = View.INVISIBLE
            swipeHint?.visibility = View.INVISIBLE

            // Force layout pass so changes take effect before draw
            val rootView = window.decorView.rootView
            rootView.invalidate()

            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.draw(canvas)

            // Restore UI chrome
            topBar.visibility = View.VISIBLE
            shareBtn?.visibility = View.VISIBLE
            swipeHint?.visibility = View.VISIBLE

            val dir = File(cacheDir, "share_screenshots")
            dir.mkdirs()
            val file = File(dir, "report_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bitmap.recycle()

            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            Log.w(TAG, "Screenshot capture failed", e)
            null
        }
    }

}
