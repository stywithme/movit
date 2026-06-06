package com.trainingvalidator.poc.ui.report

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.databinding.FragmentReportPageBinding
import com.trainingvalidator.poc.training.report.*
import java.io.File

/**
 * ReportPageFragment — Screen 1 (Hero)
 *
 * Minimal full-screen image with only:
 *   • Exercise name
 *   • 3 primary stats: Quality / Reps / Weight (or Duration)
 *   • Swipe hint to next screens
 *
 * The Share button in the Activity toolbar captures a screenshot
 * of this screen and shares it as an image.
 */
class ReportPageFragment : Fragment() {

    companion object {
        fun newSummaryInstance(): ReportPageFragment = ReportPageFragment()
    }

    private var _binding: FragmentReportPageBinding? = null
    private val binding get() = _binding!!

    private var report: PostTrainingReport? = null
    private var isArabic: Boolean = false
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
        // Bind data if setData() was called before the view was created
        if (report != null) bindData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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
        if (_binding != null) bindData()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Data binding
    // ═══════════════════════════════════════════════════════════════

    private fun bindData() {
        val report = this.report ?: return

        // Page type badge
        binding.tvPageType.text = if (isArabic) "ملخص التمرين" else "EXERCISE SUMMARY"

        // Exercise name
        binding.tvExerciseName.text =
            if (isArabic) report.exerciseName.ar else report.exerciseName.en

        // Hero image — try multiple sources
        val heroImageUri = report.heroFrame?.frameUri
            ?: report.getBestRepFrame()?.frameUri
            ?: report.bestReps.firstOrNull()?.frameCapture?.frameUri
            ?: report.repTimeline.firstOrNull { it.isBestRep }?.frameCapture?.frameUri
            ?: report.repTimeline.firstOrNull()?.frameCapture?.frameUri
        loadImage(heroImageUri)

        // Primary stats row (Quality / Reps / Weight or Duration)
        val stats = MetricDisplayBuilder.buildPrimaryStats(report, isArabic)
        bindPrimaryStats(stats)

        // Hide elements not needed on the Hero screen
        binding.tvMessage.isVisible = false
        binding.secondaryMetricsContainer.isVisible = false
        binding.pageIndicatorContainer.isVisible = false

        // Share button
        binding.tvShareLabel.text = if (isArabic) "مشاركة" else "Share"
        binding.btnShareHero.setOnClickListener {
            (activity as? WorkoutReportActivity)?.shareReport()
        }

        // Swipe hint
        binding.tvSwipeHint.isVisible = totalPages > 1
        binding.tvSwipeHint.text = if (isArabic) "← اسحب للتفاصيل" else "Swipe for details →"
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI helpers
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
            } catch (_: Exception) {
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

        // Stat 1 — Quality (large, accent color set in XML)
        stats.getOrNull(0)?.let { stat ->
            binding.tvStat1Value.text = stat.value
            binding.tvStat1Label.text = stat.label
        }

        // Stat 2 — Reps / Hold
        stats.getOrNull(1)?.let { stat ->
            binding.tvStat2Value.text = stat.value
            binding.tvStat2Label.text = stat.label
            binding.stat2Container.isVisible = true
        } ?: run { binding.stat2Container.isVisible = false }

        // Stat 3 — Weight or Duration
        stats.getOrNull(2)?.let { stat ->
            binding.tvStat3Value.text = stat.value
            binding.tvStat3Label.text = stat.label
            binding.stat3Container.isVisible = true
        } ?: run { binding.stat3Container.isVisible = false }
    }

    // Note: Message, secondary metrics, and page indicators have been
    // moved to the Performance Overview screen (Screen 2).
}
