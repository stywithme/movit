package com.trainingvalidator.poc.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine

/**
 * First page of the multi-exercise vertical pager.
 * Shows overall workout stats and a quick exercise list.
 */
class WorkoutSummaryFragment : Fragment() {

    companion object {
        private const val ARG_REPORT_JSON = "report_json"
        private const val ARG_IS_ARABIC = "is_arabic"

        fun newInstance(
            report: WorkoutTrainingEngine.WorkoutReport?,
            isArabic: Boolean
        ): WorkoutSummaryFragment {
            return WorkoutSummaryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_REPORT_JSON, report?.let {
                        com.google.gson.Gson().toJson(it)
                    })
                    putBoolean(ARG_IS_ARABIC, isArabic)
                }
            }
        }
    }

    private var report: WorkoutTrainingEngine.WorkoutReport? = null
    private var isArabic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isArabic = arguments?.getBoolean(ARG_IS_ARABIC) ?: false
        val json = arguments?.getString(ARG_REPORT_JSON)
        if (!json.isNullOrBlank()) {
            report = try {
                val type = object : com.google.gson.reflect.TypeToken<WorkoutTrainingEngine.WorkoutReport>() {}.type
                com.google.gson.Gson().fromJson(json, type)
            } catch (_: Exception) { null }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_workout_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val r = report ?: return

        val tvRating = view.findViewById<TextView>(R.id.tvWorkoutRating)
        val tvMessage = view.findViewById<TextView>(R.id.tvWorkoutMessage)
        val tvDuration = view.findViewById<TextView>(R.id.tvDuration)
        val tvExerciseCount = view.findViewById<TextView>(R.id.tvExerciseCount)
        val tvFormScore = view.findViewById<TextView>(R.id.tvFormScore)
        val tvSetsInfo = view.findViewById<TextView>(R.id.tvSetsInfo)
        val layoutInsights = view.findViewById<View>(R.id.layoutInsights)
        val tvStrongest = view.findViewById<TextView>(R.id.tvInsightStrongest)
        val tvWeakest = view.findViewById<TextView>(R.id.tvInsightWeakest)
        val rvExercises = view.findViewById<RecyclerView>(R.id.rvExercises)
        val tvSwipeHint = view.findViewById<TextView>(R.id.tvSwipeHint)
        val btnDone = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDone)

        val avgScore = r.averageFormScore
        tvRating.text = getRating(avgScore)
        tvMessage.text = getWorkoutMessage(avgScore, r.totalSetsCompleted, r.totalSetsPlanned)

        val minutes = (r.totalDurationMs / 60000).toInt()
        val seconds = ((r.totalDurationMs % 60000) / 1000).toInt()
        tvDuration.text = String.format("%02d:%02d", minutes, seconds)
        tvExerciseCount.text = r.totalExercises.toString()
        tvFormScore.text = "${avgScore.toInt()}%"
        tvSetsInfo.text = getString(R.string.workout_report_sets_format, r.totalSetsCompleted, r.totalSetsPlanned)

        renderInsights(r.exerciseReports, layoutInsights, tvStrongest, tvWeakest)

        rvExercises.layoutManager = LinearLayoutManager(requireContext())
        rvExercises.adapter = ExerciseListAdapter(r.exerciseReports)

        tvSwipeHint.text = getString(R.string.workout_report_swipe_hint)
        btnDone.setOnClickListener { requireActivity().finish() }
    }

    private fun renderInsights(
        exercises: List<WorkoutTrainingEngine.ExerciseReport>,
        layout: View, tvStrong: TextView, tvWeak: TextView
    ) {
        if (exercises.size < 2) return
        val strongest = exercises.maxByOrNull { it.averageFormScore } ?: return
        val weakest = exercises.minByOrNull { it.averageFormScore } ?: return
        if (strongest == weakest) return

        layout.visibility = View.VISIBLE
        tvStrong.text = getString(
            R.string.workout_report_strongest,
            "${strongest.exerciseName} (${strongest.averageFormScore.toInt()}%)"
        )
        tvWeak.text = getString(
            R.string.workout_report_weakest,
            "${weakest.exerciseName} (${weakest.averageFormScore.toInt()}%)"
        )
    }

    private fun getRating(score: Float): String {
        return when {
            score >= 85f -> getString(R.string.workout_report_excellent)
            score >= 70f -> getString(R.string.workout_report_good)
            score >= 50f -> getString(R.string.workout_report_solid)
            else -> getString(R.string.workout_report_keep_going)
        }
    }

    private fun getWorkoutMessage(avgScore: Float, completed: Int, planned: Int): String {
        val ratio = if (planned > 0) completed.toFloat() / planned else 0f
        return when {
            ratio >= 0.9f && avgScore >= 85 -> getString(R.string.workout_message_excellent)
            ratio >= 0.75f -> getString(R.string.workout_message_good)
            ratio > 0f -> getString(R.string.workout_message_keep_going)
            else -> getString(R.string.workout_message_get_started)
        }
    }

    // ─── Exercise List Adapter ──────────────────────────────────

    private inner class ExerciseListAdapter(
        private val items: List<WorkoutTrainingEngine.ExerciseReport>
    ) : RecyclerView.Adapter<ExerciseListAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvReportExerciseTitle)
            val tvSets: TextView = view.findViewById(R.id.tvReportExerciseSets)
            val tvAccuracy: TextView = view.findViewById(R.id.tvReportExerciseAccuracy)
            val tvQuality: TextView = view.findViewById(R.id.tvReportExerciseQuality)
            val tvWeights: TextView = view.findViewById(R.id.tvReportExerciseWeights)
            val layoutSetsBreakdown: android.widget.LinearLayout = view.findViewById(R.id.layoutSetsBreakdown)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_workout_report_exercise, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.exerciseName
            holder.tvSets.text = getString(R.string.workout_report_sets_format, item.setsCompleted, item.totalSets)
            holder.tvAccuracy.text = getString(R.string.workout_report_accuracy_format, item.averageAccuracy.toInt())

            val qualityLabel = when {
                item.averageAccuracy >= 90 -> getString(R.string.quality_excellent)
                item.averageAccuracy >= 80 -> getString(R.string.quality_good)
                item.averageAccuracy >= 70 -> getString(R.string.quality_ok)
                else -> getString(R.string.quality_keep_practicing)
            }
            holder.tvQuality.text = getString(R.string.workout_report_quality_format, qualityLabel)

            val weights = item.setMetrics
                .mapNotNull { it.weightKg }
                .joinToString(", ") { "${it}kg" }
            holder.tvWeights.text = if (weights.isBlank()) {
                getString(R.string.workout_report_weights_empty)
            } else {
                getString(R.string.workout_report_weights_format, weights)
            }

            bindSetsBreakdown(holder.layoutSetsBreakdown, item.setMetrics)
        }

        private fun bindSetsBreakdown(
            container: android.widget.LinearLayout,
            sets: List<WorkoutTrainingEngine.SetMetrics>
        ) {
            container.removeAllViews()
            if (sets.size < 2) {
                container.visibility = View.GONE
                return
            }
            container.visibility = View.VISIBLE

            val ctx = container.context
            val dp4 = (4 * ctx.resources.displayMetrics.density).toInt()
            val dp6 = (6 * ctx.resources.displayMetrics.density).toInt()
            val dp8 = (8 * ctx.resources.displayMetrics.density).toInt()

            // Divider
            container.addView(View(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = dp4; bottomMargin = dp4 }
                setBackgroundColor(0x1AFFFFFF)
            })

            sets.forEach { set ->
                val row = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, dp4, 0, dp4)
                }

                // Set label
                row.addView(TextView(ctx).apply {
                    text = "S${set.setNumber}"
                    textSize = 11f
                    setTextColor(0xAAFFFFFF.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        (28 * ctx.resources.displayMetrics.density).toInt(),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })

                // Reps count
                row.addView(TextView(ctx).apply {
                    text = "${set.repsCompleted}/${set.repsTarget} reps"
                    textSize = 11f
                    setTextColor(0x99FFFFFF.toInt())
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                })

                // Mini score bar
                val barHeight = dp6
                val barWidth = (60 * ctx.resources.displayMetrics.density).toInt()
                val barFrame = android.widget.FrameLayout(ctx).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(barWidth, barHeight).apply {
                        marginEnd = dp8
                    }
                }
                barFrame.addView(View(ctx).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(barWidth, barHeight)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x1AFFFFFF)
                        cornerRadius = barHeight / 2f
                    }
                })
                val fillW = (barWidth * (set.formScore / 100f).coerceIn(0f, 1f)).toInt()
                val fillColor = when {
                    set.formScore >= 80 -> 0xFF4CAF50.toInt()
                    set.formScore >= 60 -> 0xFFFFA726.toInt()
                    else -> 0xFFEF5350.toInt()
                }
                barFrame.addView(View(ctx).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(fillW, barHeight)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(fillColor)
                        cornerRadius = barHeight / 2f
                    }
                })
                row.addView(barFrame)

                // Score label
                row.addView(TextView(ctx).apply {
                    text = "${set.formScore.toInt()}%"
                    textSize = 11f
                    setTextColor(fillColor)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    minWidth = (32 * ctx.resources.displayMetrics.density).toInt()
                    gravity = android.view.Gravity.END
                })

                container.addView(row)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
