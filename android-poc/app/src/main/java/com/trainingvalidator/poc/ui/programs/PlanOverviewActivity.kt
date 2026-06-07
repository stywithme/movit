package com.trainingvalidator.poc.ui.programs

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.assessment.models.AssessmentType
import com.trainingvalidator.poc.assessment.ui.PreScreeningActivity
import com.trainingvalidator.poc.network.*
import com.trainingvalidator.poc.ui.utils.formatPlanProgramLevel
import kotlinx.coroutines.launch

/**
 * PlanOverviewActivity — Shows the user's active training plan timeline.
 *
 * Displays ordered programs (completed → active → upcoming) with progress,
 * next reassessment info, and progression history.
 */
class PlanOverviewActivity : AppCompatActivity() {

    private val viewModel: PlanOverviewViewModel by viewModels()
    private lateinit var container: LinearLayout

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#121212")

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(32))
        }
        root.addView(container)
        setContentView(root)

        observeViewModel()
        viewModel.loadPlanData()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    container.removeAllViews()
                    when (state) {
                        is PlanOverviewUiState.Loading -> showLoading()
                        is PlanOverviewUiState.NoAuth -> container.addView(createErrorState())
                        is PlanOverviewUiState.Error -> {
                            Log.e(TAG, "Plan load error: ${state.message}")
                            container.addView(createErrorState())
                        }
                        is PlanOverviewUiState.Success -> renderPlan(state.data)
                    }
                }
            }
        }
    }

    private fun showLoading() {
        container.addView(ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = dp(100)
            }
        })
    }

    private fun renderPlan(data: PlanOverviewData) {
        container.addView(createTitle(getString(R.string.plan_overview_title)))

        val plan = data.plan
        if (plan == null || plan.programs.isEmpty()) {
            container.addView(createEmptyState())
            return
        }

        container.addView(createStatusBadge(plan.status))
        for (slot in plan.programs) {
            container.addView(createProgramTimelineItem(slot))
        }

        if (data.reassessments.isNotEmpty()) {
            container.addView(createSectionLabel(getString(R.string.plan_upcoming_reassessment)))
            for (r in data.reassessments) {
                container.addView(createReassessmentCard(r))
            }
        }

        if (data.progression.isNotEmpty()) {
            container.addView(createSectionLabel(getString(R.string.plan_recent_adjustments)))
            for (entry in data.progression.take(5)) {
                container.addView(createProgressionCard(entry))
            }
        }
    }

    private fun createTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(20))
        }
    }

    private fun createSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(12))
        }
    }

    private fun createStatusBadge(status: String): TextView {
        val color = when (status) {
            "active" -> "#4CAF50"
            "paused" -> "#FFC107"
            "completed" -> "#2196F3"
            else -> "#757575"
        }
        return TextView(this).apply {
            text = "Plan: ${status.replaceFirstChar { it.uppercase() }}"
            setTextColor(Color.parseColor(color))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        }
    }

    private fun createProgramTimelineItem(slot: ActivePlanProgramData): LinearLayout {
        val isActive = slot.status == "active"
        val isCompleted = slot.status == "completed"
        val bgColor = when {
            isActive -> "#1B3A1B"
            isCompleted -> "#1E1E1E"
            else -> "#1A1A1A"
        }
        val borderColor = when {
            isActive -> "#4CAF50"
            isCompleted -> "#2196F3"
            else -> "#424242"
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(bgColor))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }

            // Status indicator + program name
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Status dot
            header.addView(View(context).apply {
                setBackgroundColor(Color.parseColor(borderColor))
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                    rightMargin = dp(12)
                }
            })

            val name = slot.program?.name?.get("en") ?: "Program"
            header.addView(TextView(context).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            // Status label
            header.addView(TextView(context).apply {
                text = slot.status.replaceFirstChar { it.uppercase() }
                setTextColor(Color.parseColor(borderColor))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            })

            addView(header)

            // Progress info
            if (isActive || isCompleted) {
                val progress = slot.progress
                val pct = if (progress.totalDays > 0) {
                    (progress.completedDays.toFloat() / progress.totalDays * 100).toInt()
                } else 0

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(24), dp(8), 0, 0)

                    addView(TextView(context).apply {
                        text = "Week ${progress.currentWeek}, Day ${progress.currentDay}"
                        setTextColor(Color.parseColor("#B0B0B0"))
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })

                    addView(TextView(context).apply {
                        text = "$pct% complete"
                        setTextColor(Color.parseColor("#81C784"))
                        textSize = 13f
                    })
                })

                // Progress bar
                addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    this.progress = pct
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(6)
                    ).apply { topMargin = dp(8); leftMargin = dp(24) }
                })
            }

            // Type + duration info
            val info = slot.program
            if (info != null) {
                addView(TextView(context).apply {
                    text = "${info.type} • ${info.durationWeeks} weeks • ${
                        formatPlanProgramLevel(info.levelMin, info.levelMax)
                    }"
                    setTextColor(Color.parseColor("#757575"))
                    textSize = 11f
                    setPadding(dp(24), dp(8), 0, 0)
                })
            }
        }
    }

    private fun createReassessmentCard(data: ReassessmentData): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E2A3A"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }

            addView(TextView(context).apply {
                text = "Reassessment: ${data.reason.replace('_', ' ').replaceFirstChar { it.uppercase() }}"
                setTextColor(Color.parseColor("#64B5F6"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })

            addView(TextView(context).apply {
                text = "Status: ${data.status} • ${data.notes ?: ""}"
                setTextColor(Color.parseColor("#90A4AE"))
                textSize = 12f
                setPadding(0, dp(4), 0, 0)
            })

            if (data.status == "pending" || data.status == "overdue") {
                addView(Button(context).apply {
                    text = "Start Reassessment"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#1976D2"))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                    setOnClickListener {
                        startActivity(PreScreeningActivity.createIntent(this@PlanOverviewActivity, AssessmentType.PROGRESSION))
                    }
                })
            }
        }
    }

    private fun createProgressionCard(entry: ProgressionEntryData): LinearLayout {
        val isIncrease = entry.newValue > entry.previousValue

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }

            // Arrow icon
            addView(TextView(context).apply {
                text = if (isIncrease) "↑" else "↓"
                setTextColor(if (isIncrease) Color.parseColor("#4CAF50") else Color.parseColor("#FF9800"))
                textSize = 20f
                setPadding(0, 0, dp(12), 0)
            })

            // Content
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            textCol.addView(TextView(context).apply {
                text = entry.reason
                setTextColor(Color.WHITE)
                textSize = 14f
            })
            textCol.addView(TextView(context).apply {
                val axisLabel = entry.axis?.replaceFirstChar { it.uppercase() } ?: entry.field
                text = "$axisLabel: ${entry.previousValue.toInt()} → ${entry.newValue.toInt()}"
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 12f
            })

            addView(textCol)
        }
    }

    private fun createEmptyState(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(60), dp(32), dp(60))

            addView(TextView(context).apply {
                text = "No Active Plan"
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = "Complete an assessment to get your personalized training plan."
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(24))
            })

            addView(Button(context).apply {
                text = "Start Assessment"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                textSize = 16f
                setOnClickListener {
                    startActivity(PreScreeningActivity.createIntent(this@PlanOverviewActivity))
                }
            })
        }
    }

    private fun createErrorState(): TextView {
        return TextView(this).apply {
            text = "Failed to load plan. Please try again."
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(100), dp(32), dp(32))
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val TAG = "PlanOverview"

        fun createIntent(context: Context): Intent {
            return Intent(context, PlanOverviewActivity::class.java)
        }
    }
}
