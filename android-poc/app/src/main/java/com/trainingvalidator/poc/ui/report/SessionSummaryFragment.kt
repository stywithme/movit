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
import com.trainingvalidator.poc.training.session.SessionTrainingEngine

/**
 * First page of the multi-exercise vertical pager.
 * Shows overall session stats and a quick exercise list.
 */
class SessionSummaryFragment : Fragment() {

    companion object {
        private const val ARG_REPORT_JSON = "report_json"
        private const val ARG_IS_ARABIC = "is_arabic"

        fun newInstance(
            report: SessionTrainingEngine.SessionReport?,
            isArabic: Boolean
        ): SessionSummaryFragment {
            return SessionSummaryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_REPORT_JSON, report?.let {
                        com.google.gson.Gson().toJson(it)
                    })
                    putBoolean(ARG_IS_ARABIC, isArabic)
                }
            }
        }
    }

    private var report: SessionTrainingEngine.SessionReport? = null
    private var isArabic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isArabic = arguments?.getBoolean(ARG_IS_ARABIC) ?: false
        val json = arguments?.getString(ARG_REPORT_JSON)
        if (!json.isNullOrBlank()) {
            report = try {
                val type = object : com.google.gson.reflect.TypeToken<SessionTrainingEngine.SessionReport>() {}.type
                com.google.gson.Gson().fromJson(json, type)
            } catch (_: Exception) { null }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_session_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val r = report ?: return

        val tvRating = view.findViewById<TextView>(R.id.tvSessionRating)
        val tvMessage = view.findViewById<TextView>(R.id.tvSessionMessage)
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
        tvMessage.text = getSessionMessage(avgScore, r.totalSetsCompleted, r.totalSetsPlanned)

        val minutes = (r.totalDurationMs / 60000).toInt()
        val seconds = ((r.totalDurationMs % 60000) / 1000).toInt()
        tvDuration.text = String.format("%02d:%02d", minutes, seconds)
        tvExerciseCount.text = r.totalExercises.toString()
        tvFormScore.text = "${avgScore.toInt()}%"
        tvSetsInfo.text = getString(R.string.session_report_sets_format, r.totalSetsCompleted, r.totalSetsPlanned)

        renderInsights(r.exerciseReports, layoutInsights, tvStrongest, tvWeakest)

        rvExercises.layoutManager = LinearLayoutManager(requireContext())
        rvExercises.adapter = ExerciseListAdapter(r.exerciseReports)

        tvSwipeHint.text = getString(R.string.session_report_swipe_hint)
        btnDone.setOnClickListener { requireActivity().finish() }
    }

    private fun renderInsights(
        exercises: List<SessionTrainingEngine.ExerciseReport>,
        layout: View, tvStrong: TextView, tvWeak: TextView
    ) {
        if (exercises.size < 2) return
        val strongest = exercises.maxByOrNull { it.averageFormScore } ?: return
        val weakest = exercises.minByOrNull { it.averageFormScore } ?: return
        if (strongest == weakest) return

        layout.visibility = View.VISIBLE
        tvStrong.text = getString(
            R.string.session_report_strongest,
            "${strongest.exerciseName} (${strongest.averageFormScore.toInt()}%)"
        )
        tvWeak.text = getString(
            R.string.session_report_weakest,
            "${weakest.exerciseName} (${weakest.averageFormScore.toInt()}%)"
        )
    }

    private fun getRating(score: Float): String {
        return when {
            score >= 85f -> getString(R.string.session_report_excellent)
            score >= 70f -> getString(R.string.session_report_good)
            score >= 50f -> getString(R.string.session_report_solid)
            else -> getString(R.string.session_report_keep_going)
        }
    }

    private fun getSessionMessage(avgScore: Float, completed: Int, planned: Int): String {
        val ratio = if (planned > 0) completed.toFloat() / planned else 0f
        return when {
            ratio >= 0.9f && avgScore >= 85 -> getString(R.string.session_message_excellent)
            ratio >= 0.75f -> getString(R.string.session_message_good)
            ratio > 0f -> getString(R.string.session_message_keep_going)
            else -> getString(R.string.session_message_get_started)
        }
    }

    // ─── Exercise List Adapter ──────────────────────────────────

    private inner class ExerciseListAdapter(
        private val items: List<SessionTrainingEngine.ExerciseReport>
    ) : RecyclerView.Adapter<ExerciseListAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvReportExerciseTitle)
            val tvSets: TextView = view.findViewById(R.id.tvReportExerciseSets)
            val tvAccuracy: TextView = view.findViewById(R.id.tvReportExerciseAccuracy)
            val tvQuality: TextView = view.findViewById(R.id.tvReportExerciseQuality)
            val tvWeights: TextView = view.findViewById(R.id.tvReportExerciseWeights)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_session_report_exercise, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.exerciseName
            holder.tvSets.text = getString(R.string.session_report_sets_format, item.setsCompleted, item.totalSets)
            holder.tvAccuracy.text = getString(R.string.session_report_accuracy_format, item.averageAccuracy.toInt())

            val qualityLabel = when {
                item.averageAccuracy >= 90 -> getString(R.string.quality_excellent)
                item.averageAccuracy >= 80 -> getString(R.string.quality_good)
                item.averageAccuracy >= 70 -> getString(R.string.quality_ok)
                else -> getString(R.string.quality_keep_practicing)
            }
            holder.tvQuality.text = getString(R.string.session_report_quality_format, qualityLabel)

            val weights = item.setMetrics
                .mapNotNull { it.weightKg }
                .joinToString(", ") { "${it}kg" }
            holder.tvWeights.text = if (weights.isBlank()) {
                getString(R.string.session_report_weights_empty)
            } else {
                getString(R.string.session_report_weights_format, weights)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
