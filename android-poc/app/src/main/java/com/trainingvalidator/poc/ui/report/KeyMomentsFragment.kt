package com.trainingvalidator.poc.ui.report

import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.BestRepHighlight
import com.trainingvalidator.poc.training.report.FrameCapture
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.training.report.WorstRepHighlight
import java.io.File

/**
 * KeyMomentsFragment - Full-screen display of best and worst moments
 * 
 * Shows side-by-side comparison:
 * - Best rep (with score and description)
 * - Worst rep (for improvement reference)
 */
class KeyMomentsFragment : Fragment() {
    
    companion object {
        fun newInstance() = KeyMomentsFragment()
    }
    
    private var report: PostTrainingReport? = null
    private var isArabic: Boolean = false
    
    private var tvTitle: TextView? = null
    private var tvSubtitle: TextView? = null
    private var bestCard: LinearLayout? = null
    private var worstCard: LinearLayout? = null
    private var ivBest: ImageView? = null
    private var ivWorst: ImageView? = null
    private var tvBestScore: TextView? = null
    private var tvWorstScore: TextView? = null
    private var tvBestLabel: TextView? = null
    private var tvWorstLabel: TextView? = null
    private var tvBestMessage: TextView? = null
    private var tvWorstMessage: TextView? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createLayout()
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindData()
    }
    
    fun setData(report: PostTrainingReport, isArabic: Boolean) {
        this.report = report
        this.isArabic = isArabic
        if (isAdded) {
            bindData()
        }
    }
    
    private fun createLayout(): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF040A01.toInt())
            setPadding(
                (24 * density).toInt(),
                (80 * density).toInt(),
                (24 * density).toInt(),
                (120 * density).toInt()
            )
            
            // Title
            tvTitle = TextView(context).apply {
                text = "📸 Key Moments"
                textSize = 24f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            addView(tvTitle)
            
            // Subtitle
            tvSubtitle = TextView(context).apply {
                text = "Your best and areas for improvement"
                textSize = 14f
                setTextColor(0xAAFFFFFF.toInt())
                setPadding(0, 0, 0, (24 * density).toInt())
            }
            addView(tvSubtitle)
            
            // Cards container
            val cardsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                weightSum = 2f
            }
            
            // Best card
            bestCard = createMomentCard(context, true, density)
            cardsContainer.addView(bestCard)
            
            // Worst card
            worstCard = createMomentCard(context, false, density)
            cardsContainer.addView(worstCard)
            
            addView(cardsContainer)
        }
    }
    
    private fun createMomentCard(context: android.content.Context, isBest: Boolean, density: Float): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = if (isBest) (8 * density).toInt() else 0
                marginStart = if (!isBest) (8 * density).toInt() else 0
            }
            setPadding(
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt()
            )
            
            // Card background
            background = GradientDrawable().apply {
                setColor(0x1AFFFFFF.toInt())
                setStroke(1, if (isBest) 0x664CAF50.toInt() else 0x66FF9800.toInt())
                cornerRadius = 16 * density
            }
            
            // Label
            val label = TextView(context).apply {
                text = if (isBest) "⭐ Best Moment" else "📉 For Improvement"
                textSize = 14f
                setTextColor(if (isBest) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt())
                gravity = Gravity.CENTER
            }
            addView(label)
            if (isBest) tvBestLabel = label else tvWorstLabel = label
            
            // Image
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (180 * density).toInt()
                ).apply {
                    topMargin = (12 * density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0x1AFFFFFF.toInt())
            }
            addView(imageView)
            if (isBest) ivBest = imageView else ivWorst = imageView
            
            // Score
            val score = TextView(context).apply {
                text = "85%"
                textSize = 32f
                setTextColor(if (isBest) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (12 * density).toInt()
                }
            }
            addView(score)
            if (isBest) tvBestScore = score else tvWorstScore = score
            
            // Message
            val message = TextView(context).apply {
                text = if (isBest) "Perfect form!" else "Check alignment"
                textSize = 12f
                setTextColor(0xAAFFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 2
            }
            addView(message)
            if (isBest) tvBestMessage = message else tvWorstMessage = message
        }
    }
    
    private fun bindData() {
        val report = this.report ?: return
        
        // Update titles
        tvTitle?.text = if (isArabic) "📸 لحظات مميزة" else "📸 Key Moments"
        tvSubtitle?.text = if (isArabic) {
            "أفضل أداء ونقاط التحسين"
        } else {
            "Your best and areas for improvement"
        }
        
        // Bind best moment
        val bestRep = report.bestReps.firstOrNull()
        if (bestRep != null) {
            bestCard?.visibility = View.VISIBLE
            bindBestMoment(bestRep)
        } else {
            bestCard?.visibility = View.GONE
        }
        
        // Bind worst moment
        val worstRep = report.worstRep
        val scoreDiff = (bestRep?.score ?: 0f) - (worstRep?.let { 100f - it.errorCount * 10f } ?: 0f)
        
        if (worstRep != null && scoreDiff > 15) {
            worstCard?.visibility = View.VISIBLE
            bindWorstMoment(worstRep)
        } else {
            worstCard?.visibility = View.GONE
            // Center the best card if worst is hidden
        }
    }
    
    private fun bindBestMoment(bestRep: BestRepHighlight) {
        tvBestLabel?.text = if (isArabic) "⭐ أفضل لحظة" else "⭐ Best Moment"
        tvBestScore?.text = "${bestRep.score.toInt()}%"
        
        val message = bestRep.reasons.firstOrNull()
        tvBestMessage?.text = if (isArabic) {
            message?.ar ?: "شكل مثالي!"
        } else {
            message?.en ?: "Perfect form!"
        }
        
        loadFrame(bestRep.frameCapture, ivBest)
    }
    
    private fun bindWorstMoment(worstRep: WorstRepHighlight) {
        tvWorstLabel?.text = if (isArabic) "📉 للتحسين" else "📉 For Improvement"
        
        val estimatedScore = (100 - worstRep.errorCount * 15).coerceAtLeast(0)
        tvWorstScore?.text = "${estimatedScore}%"
        
        tvWorstMessage?.text = if (isArabic) {
            worstRep.primaryError.ar
        } else {
            worstRep.primaryError.en
        }
        
        loadFrame(worstRep.frameCapture, ivWorst)
    }
    
    private fun loadFrame(frame: FrameCapture?, imageView: ImageView?) {
        imageView ?: return
        
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
}
