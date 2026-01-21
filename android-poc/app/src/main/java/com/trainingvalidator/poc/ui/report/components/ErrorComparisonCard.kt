package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.report.ErrorAnalysisItem
import com.trainingvalidator.poc.training.report.FrameCapture
import com.trainingvalidator.poc.training.report.StateDisplayConfig
import com.trainingvalidator.poc.ui.report.ImageViewerDialog
import java.io.File

/**
 * ErrorComparisonCard - Side-by-side comparison of error vs correct form
 * 
 * Shows:
 * - Error header with state icon and joint name
 * - State message from exercise JSON
 * - Side-by-side comparison images (error vs user's best rep)
 * - Actual angle vs best rep angle
 * - Affected reps list
 * - Solution tip from feedbackMessages
 */
class ErrorComparisonCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val cardView: CardView
    private val tvStateIcon: TextView
    private val tvJointName: TextView
    private val tvStateLabel: TextView
    private val tvStateMessage: TextView
    private val tvOccurrenceCount: TextView
    
    // Single error mode (no comparison available)
    private val llSingleError: LinearLayout
    private val singleFrameContainer: View
    private val ivSingleErrorFrame: ImageView
    private val tvSingleActualAngle: TextView
    private val tvSingleExpectedRange: TextView
    
    // Comparison mode (both frames available)
    private val llComparison: LinearLayout
    private val ivErrorFrame: ImageView
    private val ivCorrectFrame: ImageView
    private val tvErrorAngle: TextView
    private val tvCorrectAngle: TextView
    
    private val tvAffectedReps: TextView
    private val tvSolutionTip: TextView
    
    init {
        val view = LayoutInflater.from(context).inflate(
            R.layout.component_error_comparison_card,
            this,
            true
        )
        
        cardView = view.findViewById(R.id.cardError)
        tvStateIcon = view.findViewById(R.id.tvStateIcon)
        tvJointName = view.findViewById(R.id.tvJointName)
        tvStateLabel = view.findViewById(R.id.tvStateLabel)
        tvStateMessage = view.findViewById(R.id.tvStateMessage)
        tvOccurrenceCount = view.findViewById(R.id.tvOccurrenceCount)
        
        // Single error mode views
        llSingleError = view.findViewById(R.id.llSingleError)
        singleFrameContainer = view.findViewById(R.id.singleFrameContainer)
        ivSingleErrorFrame = view.findViewById(R.id.ivSingleErrorFrame)
        tvSingleActualAngle = view.findViewById(R.id.tvSingleActualAngle)
        tvSingleExpectedRange = view.findViewById(R.id.tvSingleExpectedRange)
        
        // Comparison mode views
        llComparison = view.findViewById(R.id.llComparison)
        ivErrorFrame = view.findViewById(R.id.ivErrorFrame)
        ivCorrectFrame = view.findViewById(R.id.ivCorrectFrame)
        tvErrorAngle = view.findViewById(R.id.tvErrorAngle)
        tvCorrectAngle = view.findViewById(R.id.tvCorrectAngle)
        
        tvAffectedReps = view.findViewById(R.id.tvAffectedReps)
        tvSolutionTip = view.findViewById(R.id.tvSolutionTip)
    }
    
    /**
     * Bind an ErrorAnalysisItem to this card
     */
    fun bind(error: ErrorAnalysisItem, isArabic: Boolean = false) {
        // Set background based on state
        val bgColor = when (error.state) {
            JointState.DANGER -> R.color.state_danger_bg
            JointState.WARNING -> R.color.state_warning_bg
            else -> R.color.state_warning_bg
        }
        cardView.setCardBackgroundColor(ContextCompat.getColor(context, bgColor))
        
        // State icon
        tvStateIcon.text = error.stateIcon
        
        // Joint name
        tvJointName.text = if (isArabic) error.jointName.ar else error.jointName.en
        
        // State label
        tvStateLabel.text = if (isArabic) error.stateDisplayName.ar else error.stateDisplayName.en
        tvStateLabel.setTextColor(ContextCompat.getColor(
            context,
            StateDisplayConfig.getColorRes(error.state)
        ))
        
        // Occurrence count
        tvOccurrenceCount.text = "(${error.count}×)"
        
        // State message from exercise JSON
        tvStateMessage.text = if (isArabic) error.message.ar else error.message.en
        
        // Load comparison frames
        var hasErrorFrame = false
        var hasCorrectFrame = false
        
        error.errorFrame?.let { frame ->
            hasErrorFrame = loadImage(ivErrorFrame, frame.frameUri)
            // Also load to single error view
            loadImage(ivSingleErrorFrame, frame.frameUri)
        }
        
        error.bestRepFrame?.let { frame ->
            hasCorrectFrame = loadImage(ivCorrectFrame, frame.frameUri)
        }
        
        // Choose display mode based on available frames
        when {
            hasErrorFrame && hasCorrectFrame -> {
                // Full comparison mode
                llComparison.visibility = View.VISIBLE
                llSingleError.visibility = View.GONE
                tvErrorAngle.text = error.getActualAngleText()
                tvCorrectAngle.text = error.getBestRepAngleText() ?: error.getExpectedRangeText()
                
                // Click to expand error frame
                ivErrorFrame.setOnClickListener {
                    error.errorFrame?.let { frame ->
                        showImageViewer(frame, error, isArabic)
                    }
                }
                ivCorrectFrame.setOnClickListener {
                    error.bestRepFrame?.let { frame ->
                        val title = if (isArabic) "أفضل أداء" else "Best Performance"
                        ImageViewerDialog(context, frame, title, "").show()
                    }
                }
            }
            hasErrorFrame -> {
                // Single error mode - show larger image
                llComparison.visibility = View.GONE
                llSingleError.visibility = View.VISIBLE
                tvSingleActualAngle.text = error.getActualAngleText()
                tvSingleExpectedRange.text = error.getExpectedRangeText()
                
                // Click to expand
                singleFrameContainer.setOnClickListener {
                    error.errorFrame?.let { frame ->
                        showImageViewer(frame, error, isArabic)
                    }
                }
            }
            else -> {
                // No frames available
                llComparison.visibility = View.GONE
                llSingleError.visibility = View.GONE
            }
        }
        
        // Affected reps
        tvAffectedReps.text = if (isArabic) {
            "التكرارات: ${error.getAffectedRepsText()}"
        } else {
            "Reps: ${error.getAffectedRepsText()}"
        }
        
        // Solution tip
        tvSolutionTip.text = "💡 ${if (isArabic) error.tip.ar else error.tip.en}"
    }
    
    private fun loadImage(imageView: ImageView, uri: String): Boolean {
        return try {
            val file = File(uri)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
                true
            } else {
                imageView.visibility = View.GONE
                false
            }
        } catch (e: Exception) {
            imageView.visibility = View.GONE
            false
        }
    }
    
    /**
     * Show fullscreen image viewer for error frame
     */
    private fun showImageViewer(frame: FrameCapture, error: ErrorAnalysisItem, isArabic: Boolean) {
        val jointName = if (isArabic) error.jointName.ar else error.jointName.en
        val stateName = if (isArabic) error.stateDisplayName.ar else error.stateDisplayName.en
        val title = "${error.stateIcon} $jointName - $stateName"
        val details = if (isArabic) {
            "الزاوية: ${error.getActualAngleText()} | المتوقع: ${error.getExpectedRangeText()}"
        } else {
            "Angle: ${error.getActualAngleText()} | Expected: ${error.getExpectedRangeText()}"
        }
        ImageViewerDialog(context, frame, title, details).show()
    }
}
