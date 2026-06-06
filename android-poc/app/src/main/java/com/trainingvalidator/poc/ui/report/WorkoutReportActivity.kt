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
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutReportBinding
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * WorkoutReportActivity — Unified report screen for all training paths.
 *
 * Modes:
 *  - Single-exercise: horizontal ViewPager2 with report pages (same as legacy ReportPagerActivity)
 *  - Multi-exercise: vertical ViewPager2 where page 0 = session summary, pages 1..N = per-exercise reports
 *    (each with its own horizontal ViewPager2)
 */
class WorkoutReportActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkoutReportActivity"

        const val EXTRA_REPORT_IDS = "report_ids"
        const val EXTRA_WORKOUT_REPORT_JSON = "workout_report_json"
        const val EXTRA_LOAD_LATEST = "load_latest"

        fun createIntent(context: Context, reportId: String): Intent {
            return Intent(context, WorkoutReportActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_REPORT_IDS, arrayListOf(reportId))
            }
        }

        fun createLatestIntent(context: Context): Intent {
            return Intent(context, WorkoutReportActivity::class.java).apply {
                putExtra(EXTRA_LOAD_LATEST, true)
            }
        }

        fun createWorkoutIntent(
            context: Context,
            reportIds: List<String>,
            workoutReportJson: String?
        ): Intent {
            return Intent(context, WorkoutReportActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_REPORT_IDS, ArrayList(reportIds))
                if (!workoutReportJson.isNullOrBlank()) {
                    putExtra(EXTRA_WORKOUT_REPORT_JSON, workoutReportJson)
                }
            }
        }

        /** @deprecated Use [createWorkoutIntent] */
        fun createSessionIntent(
            context: Context,
            reportIds: List<String>,
            workoutReportJson: String?
        ): Intent = createWorkoutIntent(context, reportIds, workoutReportJson)
    }

    private lateinit var binding: ActivityWorkoutReportBinding

    private val viewModel: ReportViewModel by viewModels {
        ReportViewModel.Factory(application)
    }

    private var isArabic = false
    private var isMultiExerciseMode = false
    private var reportIds: List<String> = emptyList()
    private var workoutReport: WorkoutTrainingEngine.WorkoutReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFullscreen()

        binding = ActivityWorkoutReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isArabic = currentLanguage == "ar"
        setupButtons()

        val ids = intent.getStringArrayListExtra(EXTRA_REPORT_IDS) ?: emptyList()
        val workoutJson = intent.getStringExtra(EXTRA_WORKOUT_REPORT_JSON)
        val loadLatest = intent.getBooleanExtra(EXTRA_LOAD_LATEST, false)

        reportIds = ids
        workoutReport = parseWorkoutReport(workoutJson)

        isMultiExerciseMode = reportIds.size > 1 || workoutReport != null

        when {
            isMultiExerciseMode -> setupMultiExerciseMode()
            reportIds.size == 1 -> setupSingleExerciseMode(reportIds.first())
            loadLatest -> setupSingleExerciseLatest()
            else -> {
                Log.e(TAG, "No report data provided")
                finish()
            }
        }
    }

    // ─── Fullscreen ─────────────────────────────────────────────

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { shareReport() }
    }

    // ─── Single Exercise Mode ───────────────────────────────────

    private fun setupSingleExerciseMode(reportId: String) {
        binding.loadingOverlay.visibility = View.VISIBLE
        viewModel.loadReport(reportId)
        observeSingleReport()
    }

    private fun setupSingleExerciseLatest() {
        binding.loadingOverlay.visibility = View.VISIBLE
        viewModel.loadLatestReport()
        observeSingleReport()
    }

    private fun observeSingleReport() {
        lifecycleScope.launch {
            viewModel.report.collectLatest { report ->
                report?.let { setupSinglePager(it) }
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
                    Log.e(TAG, "Error loading report: $error")
                    finish()
                }
            }
        }
    }

    private fun setupSinglePager(report: PostTrainingReport) {
        val adapter = RepPagerAdapter(this, report, isArabic)

        binding.viewPager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            this.adapter = adapter

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.tvTitle.text = adapter.getPageTitle(position)
                }
            })
        }

        binding.tvTitle.text = if (isArabic) report.exerciseName.ar else report.exerciseName.en
        binding.loadingOverlay.visibility = View.GONE
    }

    // ─── Multi Exercise Mode ────────────────────────────────────

    private fun setupMultiExerciseMode() {
        val report = workoutReport
        val ids = reportIds

        binding.viewPager.apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            adapter = MultiExercisePagerAdapter(
                activity = this@WorkoutReportActivity,
                reportIds = ids,
                workoutReport = report,
                isArabic = isArabic
            )

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateMultiExerciseTitle(position)
                }
            })
        }

        binding.tvExerciseIndicator.visibility = View.VISIBLE
        updateMultiExerciseTitle(0)
    }

    private fun updateMultiExerciseTitle(position: Int) {
        if (position == 0) {
            binding.tvTitle.text = getString(R.string.workout_report_summary)
            binding.tvExerciseIndicator.text = getString(R.string.workout_report_swipe_hint)
        } else {
            val exerciseIndex = position - 1
            val exerciseName = workoutReport?.exerciseReports?.getOrNull(exerciseIndex)?.exerciseName
                ?: reportIds.getOrNull(exerciseIndex) ?: ""
            binding.tvTitle.text = exerciseName
            binding.tvExerciseIndicator.text = getString(
                R.string.workout_report_exercise_indicator,
                position,
                reportIds.size
            )
        }
    }

    // ─── Multi Exercise Pager Adapter ───────────────────────────

    private class MultiExercisePagerAdapter(
        activity: WorkoutReportActivity,
        private val reportIds: List<String>,
        private val workoutReport: WorkoutTrainingEngine.WorkoutReport?,
        private val isArabic: Boolean
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 1 + reportIds.size

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                WorkoutSummaryFragment.newInstance(workoutReport, isArabic)
            } else {
                val reportId = reportIds[position - 1]
                ExerciseReportContainerFragment.newInstance(reportId, isArabic)
            }
        }
    }

    // ─── Share ──────────────────────────────────────────────────

    internal fun shareReport() {
        val screenshotUri = captureScreenshot()
        val shareText = if (isMultiExerciseMode) {
            val report = workoutReport
            if (report != null) {
                val minutes = (report.totalDurationMs / 60000).toInt()
                val seconds = ((report.totalDurationMs % 60000) / 1000).toInt()
                getString(
                    R.string.workout_share_text_format,
                    report.totalSetsCompleted,
                    report.totalSetsPlanned,
                    minutes, seconds,
                    report.averageFormScore.toInt()
                )
            } else ""
        } else {
            val report = viewModel.report.value
            if (report != null) {
                val name = if (isArabic) report.exerciseName.ar else report.exerciseName.en
                val score = report.overallQuality?.getFormattedScore()
                    ?: report.summary.getFormattedScore()
                "$name — Quality: $score"
            } else ""
        }

        if (screenshotUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, screenshotUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } else {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        }
    }

    private fun captureScreenshot(): Uri? {
        return try {
            val rootView = window.decorView.rootView
            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.draw(canvas)

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

    // ─── Helpers ────────────────────────────────────────────────

    private fun parseWorkoutReport(json: String?): WorkoutTrainingEngine.WorkoutReport? {
        if (json.isNullOrBlank()) return null
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<WorkoutTrainingEngine.WorkoutReport>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse workout report JSON", e)
            null
        }
    }
}
