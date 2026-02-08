package com.trainingvalidator.poc.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.trainingvalidator.poc.databinding.ActivityProgramSessionReportBinding

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
        binding.tvSessionSummary.text = getString(
            com.trainingvalidator.poc.R.string.session_report_items_format,
            totalItems
        )
        binding.tvSessionSets.text = getString(
            com.trainingvalidator.poc.R.string.session_report_sets_format,
            completedSets,
            totalSets
        )
        binding.tvSessionDuration.text = getString(
            com.trainingvalidator.poc.R.string.session_report_duration_format,
            minutes,
            seconds
        )
        binding.tvSessionAccuracy.text = getString(
            com.trainingvalidator.poc.R.string.session_report_accuracy_format,
            avgAccuracy.toInt()
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
            }
        }

        binding.btnDone.setOnClickListener { finish() }
    }

    private inner class SessionExerciseAdapter(
        private val items: List<com.trainingvalidator.poc.training.session.SessionTrainingEngine.ExerciseReport>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<SessionExerciseAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val tvTitle: android.widget.TextView = view.findViewById(com.trainingvalidator.poc.R.id.tvReportExerciseTitle)
            val tvSets: android.widget.TextView = view.findViewById(com.trainingvalidator.poc.R.id.tvReportExerciseSets)
            val tvAccuracy: android.widget.TextView = view.findViewById(com.trainingvalidator.poc.R.id.tvReportExerciseAccuracy)
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

            val weights = item.setMetrics
                .mapNotNull { it.weightKg }
                .joinToString(separator = ", ") { weight -> "${weight}kg" }

            holder.tvWeights.text = if (weights.isBlank()) {
                getString(com.trainingvalidator.poc.R.string.session_report_weights_empty)
            } else {
                getString(com.trainingvalidator.poc.R.string.session_report_weights_format, weights)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
