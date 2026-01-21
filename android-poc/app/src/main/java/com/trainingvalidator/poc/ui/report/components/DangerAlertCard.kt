package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.DangerAlert
import java.io.File

/**
 * DangerAlertCard - Prominent card for DANGER state alerts 🚨
 * 
 * This card is designed to be visually striking and impossible to miss.
 * It shows:
 * - Large danger icon
 * - Affected rep number
 * - Large image of the dangerous position
 * - Actual angle vs safe range
 * - Danger message from exercise JSON
 * - Solution tip
 */
class DangerAlertCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val cardView: CardView
    private val ivDangerImage: ImageView
    private val tvRepNumber: TextView
    private val tvJointName: TextView
    private val tvActualAngle: TextView
    private val tvSafeRange: TextView
    private val tvDangerMessage: TextView
    private val tvSolutionTip: TextView
    
    init {
        // Inflate custom layout or create programmatically
        val view = LayoutInflater.from(context).inflate(
            R.layout.component_danger_alert_card, 
            this, 
            true
        )
        
        cardView = view.findViewById(R.id.cardDanger)
        ivDangerImage = view.findViewById(R.id.ivDangerImage)
        tvRepNumber = view.findViewById(R.id.tvRepNumber)
        tvJointName = view.findViewById(R.id.tvJointName)
        tvActualAngle = view.findViewById(R.id.tvActualAngle)
        tvSafeRange = view.findViewById(R.id.tvSafeRange)
        tvDangerMessage = view.findViewById(R.id.tvDangerMessage)
        tvSolutionTip = view.findViewById(R.id.tvSolutionTip)
        
        // Set danger background
        cardView.setCardBackgroundColor(
            ContextCompat.getColor(context, R.color.state_danger_bg)
        )
    }
    
    /**
     * Bind a DangerAlert to this card
     */
    fun bind(alert: DangerAlert, isArabic: Boolean = false) {
        // Rep number
        tvRepNumber.text = if (isArabic) {
            "التكرار #${alert.repNumber}"
        } else {
            "Rep #${alert.repNumber}"
        }
        
        // Joint name
        tvJointName.text = if (isArabic) {
            alert.jointName.ar
        } else {
            alert.jointName.en
        }
        
        // Actual angle
        tvActualAngle.text = alert.getFormattedAngle()
        
        // Safe range
        tvSafeRange.text = if (isArabic) {
            "المدى الآمن: ${alert.getSafeRangeText()}"
        } else {
            "Safe range: ${alert.getSafeRangeText()}"
        }
        
        // Danger message from exercise JSON
        tvDangerMessage.text = if (isArabic) {
            alert.dangerMessage.ar
        } else {
            alert.dangerMessage.en
        }
        
        // Solution tip
        tvSolutionTip.text = if (isArabic) {
            "💡 ${alert.solutionTip.ar}"
        } else {
            "💡 ${alert.solutionTip.en}"
        }
        
        // Load danger frame image
        alert.dangerFrame?.let { frame ->
            loadImage(frame.frameUri)
        } ?: run {
            ivDangerImage.visibility = View.GONE
        }
    }
    
    private fun loadImage(uri: String) {
        try {
            val file = File(uri)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ivDangerImage.setImageBitmap(bitmap)
                ivDangerImage.visibility = View.VISIBLE
            } else {
                ivDangerImage.visibility = View.GONE
            }
        } catch (e: Exception) {
            ivDangerImage.visibility = View.GONE
        }
    }
}
