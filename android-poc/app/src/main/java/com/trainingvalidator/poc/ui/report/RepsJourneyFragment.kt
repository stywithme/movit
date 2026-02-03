package com.trainingvalidator.poc.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.ui.report.components.RepsJourneyChart

/**
 * RepsJourneyFragment - Full-screen display of rep scores journey
 * 
 * Shows a visual chart of rep performance across the session
 * with fatigue point indicator
 */
class RepsJourneyFragment : Fragment() {
    
    companion object {
        fun newInstance() = RepsJourneyFragment()
    }
    
    private var report: PostTrainingReport? = null
    private var isArabic: Boolean = false
    
    private var repsJourneyChart: RepsJourneyChart? = null
    private var tvTitle: TextView? = null
    private var tvSubtitle: TextView? = null
    private var tvAvgScoreValue: TextView? = null
    private var tvAvgScoreLabel: TextView? = null
    private var tvBestRepValue: TextView? = null
    private var tvBestRepLabel: TextView? = null
    private var tvFatigueValue: TextView? = null
    private var tvFatigueLabel: TextView? = null
    
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
                text = "📊 Reps Journey"
                textSize = 24f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            addView(tvTitle)
            
            // Subtitle
            tvSubtitle = TextView(context).apply {
                text = "Your performance across all reps"
                textSize = 14f
                setTextColor(0xAAFFFFFF.toInt())
                setPadding(0, 0, 0, (24 * density).toInt())
            }
            addView(tvSubtitle)
            
            // Chart
            repsJourneyChart = RepsJourneyChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (200 * density).toInt()
                )
            }
            addView(repsJourneyChart)
            
            // Stats container
            val statsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (32 * density).toInt()
                }
                weightSum = 3f
            }
            
            // Average Score stat
            val avgScoreStat = createStatContainer(context, density)
            tvAvgScoreValue = (avgScoreStat.getChildAt(0) as TextView).apply { text = "--%" }
            tvAvgScoreLabel = (avgScoreStat.getChildAt(1) as TextView).apply { text = "Avg Score" }
            statsContainer.addView(avgScoreStat)
            
            // Best Rep stat
            val bestRepStat = createStatContainer(context, density)
            tvBestRepValue = (bestRepStat.getChildAt(0) as TextView).apply { text = "--" }
            tvBestRepLabel = (bestRepStat.getChildAt(1) as TextView).apply { text = "Best Rep" }
            statsContainer.addView(bestRepStat)
            
            // Fatigue stat
            val fatigueStat = createStatContainer(context, density)
            tvFatigueValue = (fatigueStat.getChildAt(0) as TextView).apply { text = "--" }
            tvFatigueLabel = (fatigueStat.getChildAt(1) as TextView).apply { text = "Fatigue" }
            statsContainer.addView(fatigueStat)
            
            addView(statsContainer)
        }
    }
    
    private fun createStatContainer(context: android.content.Context, density: Float): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            gravity = android.view.Gravity.CENTER
            setPadding(
                (8 * density).toInt(),
                (16 * density).toInt(),
                (8 * density).toInt(),
                (16 * density).toInt()
            )
            
            // Value TextView
            addView(TextView(context).apply {
                textSize = 28f
                setTextColor(0xFF4CAF50.toInt())
                gravity = android.view.Gravity.CENTER
            })
            
            // Label TextView
            addView(TextView(context).apply {
                textSize = 12f
                setTextColor(0xAAFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
            })
        }
    }
    
    private fun bindData() {
        val report = this.report ?: return
        
        // Update title
        tvTitle?.text = if (isArabic) "📊 رحلة العدات" else "📊 Reps Journey"
        tvSubtitle?.text = if (isArabic) {
            "أداؤك عبر كل العدات"
        } else {
            "Your performance across all reps"
        }
        
        // Build metrics if not present
        val metrics = report.performanceMetrics 
            ?: com.trainingvalidator.poc.training.report.PerformanceMetricsBuilder.build(report)
        
        // Set chart data
        repsJourneyChart?.setData(
            report.repTimeline,
            metrics.controlCard.fatigueIndex
        )
        
        // Update stats - calculate avg score from timeline if summary is 0
        val avgScore = if (report.summary.averageScore > 0) {
            report.summary.averageScore
        } else if (report.repTimeline.isNotEmpty()) {
            report.repTimeline.map { it.score }.average().toFloat()
        } else {
            0f
        }
        tvAvgScoreValue?.text = "${avgScore.toInt()}%"
        tvAvgScoreLabel?.text = if (isArabic) "متوسط النتيجة" else "Avg Score"
        
        val bestRepNumber = report.bestReps.firstOrNull()?.repNumber
            ?: report.repTimeline.maxByOrNull { it.score }?.repNumber
        tvBestRepValue?.text = if (bestRepNumber != null) "#$bestRepNumber" else "--"
        tvBestRepLabel?.text = if (isArabic) "أفضل عدة" else "Best Rep"
        
        val fatigueIndex = metrics.controlCard.fatigueIndex
        tvFatigueValue?.text = if (fatigueIndex != null) "#$fatigueIndex" else "--"
        tvFatigueLabel?.text = if (isArabic) "نقطة التعب" else "Fatigue"
    }
}
