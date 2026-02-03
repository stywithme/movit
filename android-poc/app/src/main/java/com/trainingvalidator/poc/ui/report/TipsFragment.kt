package com.trainingvalidator.poc.ui.report

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.training.report.ImprovementTip
import com.trainingvalidator.poc.training.report.PostTrainingReport

/**
 * TipsFragment - Full-screen display of improvement tips
 * 
 * Shows actionable suggestions for improvement based on the session
 */
class TipsFragment : Fragment() {
    
    companion object {
        fun newInstance() = TipsFragment()
    }
    
    private var report: PostTrainingReport? = null
    private var isArabic: Boolean = false
    
    private var tvTitle: TextView? = null
    private var tvSubtitle: TextView? = null
    private var tipsContainer: LinearLayout? = null
    
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
                text = "💡 Tips for Next Time"
                textSize = 24f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            addView(tvTitle)
            
            // Subtitle
            tvSubtitle = TextView(context).apply {
                text = "Focus on these for your next session"
                textSize = 14f
                setTextColor(0xAAFFFFFF.toInt())
                setPadding(0, 0, 0, (24 * density).toInt())
            }
            addView(tvSubtitle)
            
            // Tips container
            tipsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            addView(tipsContainer)
        }
    }
    
    private fun bindData() {
        val report = this.report ?: return
        
        // Update titles
        tvTitle?.text = if (isArabic) "💡 نصائح للمرة القادمة" else "💡 Tips for Next Time"
        tvSubtitle?.text = if (isArabic) {
            "ركز على هذه النقاط في جلستك القادمة"
        } else {
            "Focus on these for your next session"
        }
        
        // Clear and rebuild tips
        tipsContainer?.removeAllViews()
        
        val tips = report.improvementTips
        if (tips.isEmpty()) {
            // Show perfect message
            addPerfectMessage()
        } else {
            tips.forEachIndexed { index, tip ->
                addTipCard(tip, index + 1)
            }
        }
    }
    
    private fun addPerfectMessage() {
        val context = requireContext()
        val density = resources.displayMetrics.density
        
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
            setPadding(
                (20 * density).toInt(),
                (20 * density).toInt(),
                (20 * density).toInt(),
                (20 * density).toInt()
            )
            gravity = Gravity.CENTER
            
            background = GradientDrawable().apply {
                setColor(0x1A4CAF50.toInt())
                setStroke(1, 0x664CAF50.toInt())
                cornerRadius = 16 * density
            }
        }
        
        val icon = TextView(context).apply {
            text = "🎉"
            textSize = 48f
            gravity = Gravity.CENTER
        }
        card.addView(icon)
        
        val title = TextView(context).apply {
            text = if (isArabic) "أداء ممتاز!" else "Excellent Performance!"
            textSize = 20f
            setTextColor(0xFF4CAF50.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
            }
        }
        card.addView(title)
        
        val message = TextView(context).apply {
            text = if (isArabic) {
                "لا توجد ملاحظات - استمر على هذا المستوى!"
            } else {
                "No improvement notes - keep up the great work!"
            }
            textSize = 14f
            setTextColor(0xAAFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        card.addView(message)
        
        tipsContainer?.addView(card)
    }
    
    private fun addTipCard(tip: ImprovementTip, number: Int) {
        val context = requireContext()
        val density = resources.displayMetrics.density
        
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
            setPadding(
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )
            gravity = Gravity.CENTER_VERTICAL
            
            background = GradientDrawable().apply {
                setColor(0x1AFFFFFF.toInt())
                setStroke(1, getPriorityColor(tip.priority))
                cornerRadius = 12 * density
            }
        }
        
        // Number badge
        val badge = TextView(context).apply {
            text = "$number"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                (36 * density).toInt(),
                (36 * density).toInt()
            ).apply {
                marginEnd = (12 * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(getPriorityColor(tip.priority))
                cornerRadius = 18 * density
            }
        }
        card.addView(badge)
        
        // Content container
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        // Title
        val title = TextView(context).apply {
            text = if (isArabic) tip.title.ar else tip.title.en
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        content.addView(title)
        
        // Description
        val desc = TextView(context).apply {
            text = if (isArabic) tip.description.ar else tip.description.en
            textSize = 13f
            setTextColor(0xAAFFFFFF.toInt())
            maxLines = 3
        }
        content.addView(desc)
        
        // Priority indicator
        val priority = TextView(context).apply {
            text = when (tip.priority) {
                1 -> if (isArabic) "🔴 أولوية عالية" else "🔴 High Priority"
                2 -> if (isArabic) "🟡 أولوية متوسطة" else "🟡 Medium Priority"
                else -> if (isArabic) "🟢 للمستقبل" else "🟢 For Later"
            }
            textSize = 11f
            setTextColor(getPriorityColor(tip.priority))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
            }
        }
        content.addView(priority)
        
        card.addView(content)
        
        // Icon
        val icon = TextView(context).apply {
            text = tip.icon
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
        }
        card.addView(icon)
        
        tipsContainer?.addView(card)
    }
    
    private fun getPriorityColor(priority: Int): Int {
        return when (priority) {
            1 -> 0xFFFF5252.toInt() // Red - High
            2 -> 0xFFFFC107.toInt() // Yellow - Medium
            else -> 0xFF4CAF50.toInt() // Green - Low
        }
    }
}
