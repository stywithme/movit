package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.BestRepHighlight
import com.trainingvalidator.poc.training.report.FrameCapture
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.training.report.WorstRepHighlight
import java.io.File

/**
 * KeyMomentsSection - Shows best and worst rep comparison
 * 
 * Side-by-side comparison of:
 * - Best rep (with skeleton overlay)
 * - Worst rep (for comparison)
 * 
 * Each moment shows:
 * - Frame image
 * - Rep number
 * - Score
 * - Short description
 */
class KeyMomentsSection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private val sectionTitle: TextView
    private val momentsContainer: LinearLayout
    
    private val bestMomentCard: LinearLayout
    private val ivBestFrame: ImageView
    private val tvBestLabel: TextView
    private val tvBestRep: TextView
    private val tvBestScore: TextView
    private val tvBestMessage: TextView
    
    private val worstMomentCard: LinearLayout
    private val ivWorstFrame: ImageView
    private val tvWorstLabel: TextView
    private val tvWorstRep: TextView
    private val tvWorstScore: TextView
    private val tvWorstMessage: TextView
    
    init {
        orientation = VERTICAL
        
        val view = LayoutInflater.from(context).inflate(R.layout.component_key_moments, this, true)
        
        sectionTitle = view.findViewById(R.id.sectionTitle)
        momentsContainer = view.findViewById(R.id.momentsContainer)
        
        // Best moment
        bestMomentCard = view.findViewById(R.id.bestMomentCard)
        ivBestFrame = view.findViewById(R.id.ivBestFrame)
        tvBestLabel = view.findViewById(R.id.tvBestLabel)
        tvBestRep = view.findViewById(R.id.tvBestRep)
        tvBestScore = view.findViewById(R.id.tvBestScore)
        tvBestMessage = view.findViewById(R.id.tvBestMessage)
        
        // Worst moment
        worstMomentCard = view.findViewById(R.id.worstMomentCard)
        ivWorstFrame = view.findViewById(R.id.ivWorstFrame)
        tvWorstLabel = view.findViewById(R.id.tvWorstLabel)
        tvWorstRep = view.findViewById(R.id.tvWorstRep)
        tvWorstScore = view.findViewById(R.id.tvWorstScore)
        tvWorstMessage = view.findViewById(R.id.tvWorstMessage)
        
        // Apply glassmorphic style
        applyCardStyle(bestMomentCard, 0x334CAF50) // Green border
        applyCardStyle(worstMomentCard, 0x33FF9800) // Orange border
    }
    
    /**
     * Bind report data
     */
    fun bind(report: PostTrainingReport, isArabic: Boolean = false) {
        sectionTitle.text = if (isArabic) "📸 لحظات مميزة" else "📸 Key Moments"
        
        // Best moment
        val bestRep = report.bestReps.firstOrNull()
        if (bestRep != null) {
            bestMomentCard.visibility = View.VISIBLE
            bindBestMoment(bestRep, isArabic)
        } else {
            bestMomentCard.visibility = View.GONE
        }
        
        // Worst moment - only show if there's significant difference
        val worstRep = report.worstRep
        val scoreDifference = (bestRep?.score ?: 0f) - (worstRep?.let { 100f - it.errorCount * 10f } ?: 0f)
        
        if (worstRep != null && scoreDifference > 20) {
            worstMomentCard.visibility = View.VISIBLE
            bindWorstMoment(worstRep, isArabic)
        } else {
            worstMomentCard.visibility = View.GONE
        }
        
        // If only best moment, center it
        if (worstMomentCard.visibility == View.GONE) {
            momentsContainer.gravity = android.view.Gravity.CENTER
        }
    }
    
    private fun bindBestMoment(bestRep: BestRepHighlight, isArabic: Boolean) {
        tvBestLabel.text = if (isArabic) "⭐ أفضل لحظة" else "⭐ Best Moment"
        tvBestRep.text = if (isArabic) "العدة #${bestRep.repNumber}" else "Rep #${bestRep.repNumber}"
        tvBestScore.text = "${bestRep.score.toInt()}%"
        tvBestScore.setTextColor(0xFF4CAF50.toInt())
        
        val message = bestRep.reasons.firstOrNull()
        tvBestMessage.text = if (isArabic) {
            message?.ar ?: "شكل مثالي!"
        } else {
            message?.en ?: "Perfect form!"
        }
        
        // Load frame
        loadFrame(bestRep.frameCapture, ivBestFrame)
    }
    
    private fun bindWorstMoment(worstRep: WorstRepHighlight, isArabic: Boolean) {
        tvWorstLabel.text = if (isArabic) "📉 لحظة للتحسين" else "📉 Needs Improvement"
        tvWorstRep.text = if (isArabic) "العدة #${worstRep.repNumber}" else "Rep #${worstRep.repNumber}"
        
        val estimatedScore = (100 - worstRep.errorCount * 15).coerceAtLeast(0)
        tvWorstScore.text = "${estimatedScore}%"
        tvWorstScore.setTextColor(0xFFFF9800.toInt())
        
        tvWorstMessage.text = if (isArabic) {
            worstRep.primaryError.ar
        } else {
            worstRep.primaryError.en
        }
        
        // Load frame
        loadFrame(worstRep.frameCapture, ivWorstFrame)
    }
    
    private fun loadFrame(frame: FrameCapture?, imageView: ImageView) {
        if (frame != null) {
            val file = File(frame.frameUri.ifEmpty { frame.thumbnailUri })
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                return
            }
        }
        
        // Placeholder
        imageView.setImageResource(R.drawable.ic_person_placeholder)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
    }
    
    private fun applyCardStyle(card: LinearLayout, borderColor: Int) {
        val background = GradientDrawable().apply {
            setColor(0x1A1A1A1A) // 10% dark
            setStroke(1, borderColor)
            cornerRadius = 12 * resources.displayMetrics.density
        }
        card.background = background
    }
}
