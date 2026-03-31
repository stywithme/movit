package com.trainingvalidator.poc.ui.programs

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramSessionReportBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ProgressionEntryData
import com.trainingvalidator.poc.network.ProgressionMarkSeenRequest
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramSessionReportActivity - Simple completion summary for a session
 */
class ProgramSessionReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TOTAL_ITEMS = "total_items"
        const val EXTRA_TOTAL_SETS = "total_sets"
        const val EXTRA_COMPLETED_SETS = "completed_sets"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_AVG_ACCURACY = "avg_accuracy"
        const val EXTRA_SESSION_REPORT_JSON = "session_report_json"
        const val EXTRA_REPORT_IDS = "report_ids"
        const val EXTRA_SESSION_ID = "session_id"
    }

    private lateinit var binding: ActivityProgramSessionReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProgramSessionReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val totalItems = intent.getIntExtra(EXTRA_TOTAL_ITEMS, 0)
        val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 0)
        val completedSets = intent.getIntExtra(EXTRA_COMPLETED_SETS, 0)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        val avgAccuracy = intent.getFloatExtra(EXTRA_AVG_ACCURACY, 0f)
        val reportJson = intent.getStringExtra(EXTRA_SESSION_REPORT_JSON)

        val minutes = (durationMs / 60000).toInt()
        val seconds = ((durationMs % 60000) / 1000).toInt()
        val completionRatio = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f

        // Session Rating Badge
        val rating = getFormRating(avgAccuracy)
        binding.tvSessionRating.text = rating

        binding.tvSessionMessage.text = getSessionMessage(avgAccuracy, completionRatio)

        // Hero metrics — show just the numbers, no labels (labels are in XML)
        binding.tvSessionDuration.text = String.format("%02d:%02d", minutes, seconds)
        binding.tvSessionSummary.text = totalItems.toString()
        binding.tvSessionAccuracy.text = "${avgAccuracy.toInt()}%"

        binding.tvSessionSets.text = getString(
            com.trainingvalidator.poc.R.string.session_report_sets_format,
            completedSets,
            totalSets
        )

        if (!reportJson.isNullOrBlank()) {
            val report = runCatching {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SessionReport>() {}.type
                gson.fromJson<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SessionReport>(reportJson, type)
            }.getOrNull()

            if (report != null && report.exerciseReports.isNotEmpty()) {
                binding.rvSessionExercises.apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ProgramSessionReportActivity)
                    adapter = SessionExerciseAdapter(report.exerciseReports)
                }
                binding.tvSessionExerciseHeader.visibility = android.view.View.VISIBLE
                binding.rvSessionExercises.visibility = android.view.View.VISIBLE

                // Insights: strongest and weakest exercises
                renderInsights(report.exerciseReports)
            }
        }

        binding.btnDone.setOnClickListener { finish() }

        // Check for unseen progression changes and notify user
        checkProgressionNotifications()

        binding.btnShare.setOnClickListener {
            val shareText = getString(
                com.trainingvalidator.poc.R.string.session_share_text_format,
                completedSets,
                totalSets,
                minutes,
                seconds,
                avgAccuracy.toInt()
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, getString(com.trainingvalidator.poc.R.string.share)))
        }
    }

    private inner class SessionExerciseAdapter(
        private val items: List<com.trainingvalidator.poc.training.session.SessionTrainingEngine.ExerciseReport>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<SessionExerciseAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val tvTitle: android.widget.TextView = view.findViewById(com.trainingvalidator.poc.R.id.tvReportExerciseTitle)
            val tvSets: android.widget.TextView = view.findViewById(com.trainingvalidator.poc.R.id.tvReportExerciseSets)
            val tvAccuracy: android.widget.TextView = view.findViewById(com.trainingvalidator.poc.R.id.tvReportExerciseAccuracy)
            val tvQuality: android.widget.TextView = view.findViewById(com.trainingvalidator.poc.R.id.tvReportExerciseQuality)
            val tvWeights: android.widget.TextView = view.findViewById(com.trainingvalidator.poc.R.id.tvReportExerciseWeights)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(com.trainingvalidator.poc.R.layout.item_program_session_report_exercise, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.exerciseName
            holder.tvSets.text = getString(
                com.trainingvalidator.poc.R.string.session_report_sets_format,
                item.setsCompleted,
                item.totalSets
            )
            holder.tvAccuracy.text = getString(
                com.trainingvalidator.poc.R.string.session_report_accuracy_format,
                item.averageAccuracy.toInt()
            )
            holder.tvQuality.text = getString(
                com.trainingvalidator.poc.R.string.session_report_quality_format,
                getQualityLabel(item.averageAccuracy)
            )

            val weights = item.setMetrics
                .mapNotNull { it.weightKg }
                .joinToString(separator = ", ") { weight -> "${weight}kg" }

            holder.tvWeights.text = if (weights.isBlank()) {
                getString(com.trainingvalidator.poc.R.string.session_report_weights_empty)
            } else {
                getString(com.trainingvalidator.poc.R.string.session_report_weights_format, weights)
            }

            holder.itemView.setOnClickListener {
                // If a rich report exists (PostTrainingReport), navigate to the full report
                val reportId = item.reportId
                if (reportId != null) {
                    val intent = com.trainingvalidator.poc.ui.report.ReportPagerActivity.createIntent(
                        this@ProgramSessionReportActivity,
                        reportId
                    )
                    startActivity(intent)
                } else {
                    // Fallback to simple bottom sheet summary
                    showExerciseSummarySheet(item)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun showExerciseSummarySheet(
        report: com.trainingvalidator.poc.training.session.SessionTrainingEngine.ExerciseReport
    ) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(com.trainingvalidator.poc.R.layout.bottom_sheet_exercise_summary, null)
        dialog.setContentView(sheet)

        val tvTitle = sheet.findViewById<android.widget.TextView>(com.trainingvalidator.poc.R.id.tvExerciseSummaryTitle)
        val tvMessage = sheet.findViewById<android.widget.TextView>(com.trainingvalidator.poc.R.id.tvExerciseSummaryMessage)
        val tvStats = sheet.findViewById<android.widget.TextView>(com.trainingvalidator.poc.R.id.tvExerciseSummaryStats)
        val tvTip = sheet.findViewById<android.widget.TextView>(com.trainingvalidator.poc.R.id.tvExerciseSummaryTip)

        val quality = report.averageAccuracy.toInt()
        val message = when {
            quality >= 90 -> getString(com.trainingvalidator.poc.R.string.exercise_summary_message_excellent)
            quality >= 80 -> getString(com.trainingvalidator.poc.R.string.exercise_summary_message_good)
            quality >= 70 -> getString(com.trainingvalidator.poc.R.string.exercise_summary_message_ok)
            else -> getString(com.trainingvalidator.poc.R.string.exercise_summary_message_keep_practicing)
        }

        val tip = when {
            quality >= 85 -> getString(com.trainingvalidator.poc.R.string.exercise_summary_tip_keep)
            quality >= 70 -> getString(com.trainingvalidator.poc.R.string.exercise_summary_tip_focus)
            else -> getString(com.trainingvalidator.poc.R.string.exercise_summary_tip_slow)
        }

        tvTitle.text = report.exerciseName
        tvMessage.text = message
        tvStats.text = getString(
            com.trainingvalidator.poc.R.string.exercise_summary_stats_format,
            quality,
            report.setsCompleted,
            report.totalSets,
            report.totalReps
        )
        tvTip.text = tip

        dialog.show()
    }

    private fun renderInsights(
        exercises: List<com.trainingvalidator.poc.training.session.SessionTrainingEngine.ExerciseReport>
    ) {
        if (exercises.size < 2) return

        val strongest = exercises.maxByOrNull { it.averageFormScore }
        val weakest = exercises.minByOrNull { it.averageFormScore }

        if (strongest != null && weakest != null && strongest != weakest) {
            binding.layoutInsights.visibility = android.view.View.VISIBLE
            binding.tvInsightStrongest.text = getString(
                com.trainingvalidator.poc.R.string.session_report_strongest,
                "${strongest.exerciseName} (${strongest.averageFormScore.toInt()}%)"
            )
            binding.tvInsightWeakest.text = getString(
                com.trainingvalidator.poc.R.string.session_report_weakest,
                "${weakest.exerciseName} (${weakest.averageFormScore.toInt()}%)"
            )
        }
    }

    private fun getFormRating(score: Float): String {
        return when {
            score >= 85f -> getString(com.trainingvalidator.poc.R.string.session_report_excellent)
            score >= 70f -> getString(com.trainingvalidator.poc.R.string.session_report_good)
            score >= 50f -> getString(com.trainingvalidator.poc.R.string.session_report_solid)
            else -> getString(com.trainingvalidator.poc.R.string.session_report_keep_going)
        }
    }

    private fun getSessionMessage(avgAccuracy: Float, completionRatio: Float): String {
        val accuracy = avgAccuracy.toInt()
        return when {
            completionRatio >= 0.9f && accuracy >= 85 -> getString(
                com.trainingvalidator.poc.R.string.session_message_excellent
            )
            completionRatio >= 0.75f -> getString(
                com.trainingvalidator.poc.R.string.session_message_good
            )
            completionRatio > 0f -> getString(
                com.trainingvalidator.poc.R.string.session_message_keep_going
            )
            else -> getString(com.trainingvalidator.poc.R.string.session_message_get_started)
        }
    }

    private fun getQualityLabel(avgAccuracy: Float): String {
        val accuracy = avgAccuracy.toInt()
        return when {
            accuracy >= 90 -> getString(com.trainingvalidator.poc.R.string.quality_excellent)
            accuracy >= 80 -> getString(com.trainingvalidator.poc.R.string.quality_good)
            accuracy >= 70 -> getString(com.trainingvalidator.poc.R.string.quality_ok)
            else -> getString(com.trainingvalidator.poc.R.string.quality_keep_practicing)
        }
    }

    private val isArabic by lazy { currentLanguage == "ar" }

    /**
     * Fetches session-specific progression changes and shows a notification bottom sheet.
     * Does NOT mark them as seen — that is handled by HomeFragment when the user
     * acknowledges the notification card.
     */
    private fun checkProgressionNotifications() {
        val authHeader = AuthManager.getAuthHeader(this) ?: return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    if (sessionId != null) {
                        ApiClient.mobileSyncApi.getSessionProgression(authHeader, sessionId)
                    } else {
                        ApiClient.mobileSyncApi.getRecentProgression(authHeader)
                    }
                }

                if (!response.isSuccessful) return@launch
                val changes = response.body()?.data ?: return@launch
                if (changes.isEmpty()) return@launch

                showProgressionSheet(changes)
            } catch (e: Exception) {
                Log.w("ProgressionNotif", "Failed to check progression changes", e)
            }
        }
    }

    private fun showProgressionSheet(changes: List<ProgressionEntryData>) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_progression_notification, null)
        dialog.setContentView(view)

        val hasIncrease = changes.any { it.newValue > it.previousValue }

        val tvIcon = view.findViewById<TextView>(R.id.tvProgressionIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvProgressionTitle)
        val changesContainer = view.findViewById<LinearLayout>(R.id.llProgressionChanges)
        val btnGotIt = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProgressionGotIt)

        tvIcon.text = if (hasIncrease) "\uD83C\uDF1F" else "\uD83D\uDD27"
        tvTitle.text = if (hasIncrease) getString(R.string.progression_level_up) else getString(R.string.progression_deload_title)

        for (change in changes) {
            changesContainer.addView(buildProgressionChangeCard(change))
        }

        btnGotIt.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun buildProgressionChangeCard(change: ProgressionEntryData): LinearLayout {
        val isIncrease = change.newValue > change.previousValue
        val accentColor = if (isIncrease) ContextCompat.getColor(this, R.color.success)
            else ContextCompat.getColor(this, R.color.warning)
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.surface_variant))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))

            // Top row: arrow + field change text
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            topRow.addView(TextView(context).apply {
                text = if (isIncrease) "\u2191" else "\u2193"
                setTextColor(accentColor)
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(10) }
            })

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val fieldLabel = when (change.field) {
                "weightKg" -> getString(R.string.progression_field_weight)
                "targetReps" -> getString(R.string.progression_field_reps)
                "sets" -> getString(R.string.progression_field_sets)
                "reassessment" -> getString(R.string.progression_field_reassessment)
                else -> change.field
            }

            val formattedFrom = formatFieldValue(change.field, change.previousValue)
            val formattedTo = formatFieldValue(change.field, change.newValue)
            val changeStr = if (isIncrease) {
                getString(R.string.progression_change_increase, fieldLabel, formattedFrom, formattedTo)
            } else {
                getString(R.string.progression_change_decrease, fieldLabel, formattedFrom, formattedTo)
            }

            textCol.addView(TextView(context).apply {
                text = changeStr
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })

            val exName = change.exerciseName?.let { if (isArabic) it["ar"] else it["en"] }
            if (!exName.isNullOrBlank()) {
                textCol.addView(TextView(context).apply {
                    text = getString(R.string.progression_for_exercise, exName)
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textSize = 12f
                })
            }

            topRow.addView(textCol)
            addView(topRow)

            // Visual progress bar
            val maxVal = maxOf(change.previousValue, change.newValue).toFloat()
            if (maxVal > 0f) {
                addView(android.view.View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        topMargin = dp(8); bottomMargin = dp(6)
                    }
                    setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                })

                addView(buildBarRow(if (isArabic) "قبل" else "Before", change.previousValue.toFloat(), maxVal,
                    ContextCompat.getColor(this@ProgramSessionReportActivity, R.color.text_secondary)))
                addView(buildBarRow(if (isArabic) "بعد" else "After", change.newValue.toFloat(), maxVal, accentColor))
            }
        }
    }

    private fun buildBarRow(label: String, value: Float, max: Float, color: Int): LinearLayout {
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }

            addView(TextView(context).apply {
                text = label; textSize = 11f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            val fraction = if (max > 0) (value / max).coerceIn(0f, 1f) else 0f
            val barBg = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(6), 1f)
                background = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(context, R.color.surface_variant))
                    cornerRadius = dp(3).toFloat()
                }
                weightSum = 1f
            }
            barBg.addView(android.view.View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply { weight = fraction }
                background = GradientDrawable().apply { setColor(color); cornerRadius = dp(3).toFloat() }
            })
            barBg.addView(android.view.View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply { weight = 1f - fraction }
            })
            addView(barBg)

            addView(TextView(context).apply {
                text = String.format("%.1f", value); textSize = 11f
                setTextColor(color); setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(6) }
            })
        }
    }

    private fun formatFieldValue(field: String, value: Double): String {
        return when (field) {
            "weightKg" -> "${String.format("%.1f", value)}kg"
            "targetReps", "sets" -> "${value.toInt()}"
            else -> String.format("%.1f", value)
        }
    }
}
