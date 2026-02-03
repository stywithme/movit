package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.training.report.PerformanceRating
import java.io.File

/**
 * HeroSection - The top section of the report with user image and quick stats
 * 
 * Glassmorphic design with:
 * - Full-body user image (or placeholder)
 * - Glass overlay with 4 key stats
 * - Motivational badge
 */
class HeroSection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val ivHeroImage: ImageView
    private val overlayContainer: LinearLayout
    private val tvBadge: TextView
    
    // Stats
    private val tvStatScore: TextView
    private val tvStatWeight: TextView
    private val tvStatDuration: TextView
    private val tvStatReps: TextView
    private val weightContainer: View
    
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.component_hero_section, this, true)
        
        ivHeroImage = view.findViewById(R.id.ivHeroImage)
        overlayContainer = view.findViewById(R.id.overlayContainer)
        tvBadge = view.findViewById(R.id.tvBadge)
        
        tvStatScore = view.findViewById(R.id.tvStatScore)
        tvStatWeight = view.findViewById(R.id.tvStatWeight)
        tvStatDuration = view.findViewById(R.id.tvStatDuration)
        tvStatReps = view.findViewById(R.id.tvStatReps)
        weightContainer = view.findViewById(R.id.weightContainer)
    }
    
    /**
     * Bind report data to the hero section
     */
    fun bind(report: PostTrainingReport, isArabic: Boolean = false) {
        // Load hero image
        loadHeroImage(report)
        
        // Set stats
        val summary = report.summary
        tvStatScore.text = "${summary.averageScore.toInt()}%"
        tvStatDuration.text = summary.getFormattedDuration()
        tvStatReps.text = summary.totalReps.toString()
        
        // Weight (if available)
        if (summary.weightKg != null) {
            weightContainer.visibility = View.VISIBLE
            tvStatWeight.text = summary.getFormattedWeight() ?: ""
        } else {
            weightContainer.visibility = View.GONE
        }
        
        // Badge based on performance
        val badge = when {
            report.hasDangerAlerts() -> Pair("⚠️", if (isArabic) "انتبه" else "Attention")
            report.shouldCelebrate() -> Pair("🏆", if (isArabic) "أداء رائع!" else "Outstanding!")
            summary.rating == PerformanceRating.EXCELLENT -> Pair("🏆", if (isArabic) "ممتاز" else "Excellent")
            summary.rating == PerformanceRating.GOOD -> Pair("🏅", if (isArabic) "جيد جداً" else "Great Job")
            summary.rating == PerformanceRating.FAIR -> Pair("💪", if (isArabic) "مجهود جيد" else "Good Effort")
            else -> Pair("📈", if (isArabic) "استمر" else "Keep Going")
        }
        tvBadge.text = "${badge.first} ${badge.second}"
        
        // Badge color based on rating
        val badgeColor = when {
            report.hasDangerAlerts() -> 0xFFFF5252.toInt()
            summary.rating == PerformanceRating.EXCELLENT -> 0xFF4CAF50.toInt()
            summary.rating == PerformanceRating.GOOD -> 0xFF8BC34A.toInt()
            summary.rating == PerformanceRating.FAIR -> 0xFFFFC107.toInt()
            else -> 0xFFFF9800.toInt()
        }
        tvBadge.setTextColor(badgeColor)
    }
    
    private fun loadHeroImage(report: PostTrainingReport) {
        // Try to load hero frame
        val heroFrame = report.heroFrame ?: report.getBestRepFrame()
        
        if (heroFrame != null) {
            val file = File(heroFrame.frameUri.ifEmpty { heroFrame.thumbnailUri })
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ivHeroImage.setImageBitmap(bitmap)
                ivHeroImage.scaleType = ImageView.ScaleType.CENTER_CROP
                return
            }
        }
        
        // Show placeholder
        ivHeroImage.setImageResource(R.drawable.ic_person_placeholder)
        ivHeroImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
    }
}
