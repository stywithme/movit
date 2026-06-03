package com.trainingvalidator.poc.ui.report

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.databinding.FragmentReportPageBinding
import com.trainingvalidator.poc.segmentation.ReportBackgroundEffectProcessor
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.report.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var currentHeroImagePath: String? = null

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
        applyHeroOverlay()

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
            (activity as? SessionReportActivity)?.shareReport()
                ?: (activity as? ReportPagerActivity)?.shareReport()
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
            currentHeroImagePath = null
            binding.ivFullScreenImage.visibility = View.GONE
            binding.placeholderContainer.visibility = View.VISIBLE
            return
        }
        val file = File(uri)
        if (file.exists()) {
            val imagePath = file.absolutePath
            currentHeroImagePath = imagePath
            binding.ivFullScreenImage.visibility = View.GONE
            binding.placeholderContainer.visibility = View.VISIBLE
            val appContext = requireContext().applicationContext

            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        ReportBackgroundEffectProcessor(appContext).apply(File(imagePath))
                    } catch (e: Exception) {
                        Log.e("ReportPageFragment", "Failed to load report hero image: ${e.message}", e)
                        null
                    }
                }

                if (_binding == null || currentHeroImagePath != imagePath) return@launch
                if (bitmap != null) {
                    binding.ivFullScreenImage.setImageBitmap(bitmap)
                    binding.ivFullScreenImage.visibility = View.VISIBLE
                    binding.placeholderContainer.visibility = View.GONE
                } else {
                    binding.ivFullScreenImage.visibility = View.GONE
                    binding.placeholderContainer.visibility = View.VISIBLE
                }
            }
        } else {
            currentHeroImagePath = null
            binding.ivFullScreenImage.visibility = View.GONE
            binding.placeholderContainer.visibility = View.VISIBLE
        }
    }

    private fun applyHeroOverlay() {
        val overlaySettings = SettingsManager.getReportHeroOverlaySettings()
        val overlay = binding.reportHeroOverlay

        if (!overlaySettings.enabled) {
            overlay.visibility = View.GONE
            return
        }

        overlay.visibility = View.VISIBLE
        val baseColor = parseOverlayColor(overlaySettings.color)
        val strength = overlaySettings.overlayAlpha.coerceIn(0f, 1f)
        if (strength <= 0f) {
            overlay.visibility = View.GONE
            return
        }

        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        val alpha = (strength * 255f).toInt()
        overlay.background = ColorDrawable(Color.argb(alpha, r, g, b))
    }

    private fun parseOverlayColor(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: IllegalArgumentException) {
            Color.BLACK
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
