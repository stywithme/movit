package com.trainingvalidator.poc.ui.programs

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWeeklyReportBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.MetricsResponse
import com.trainingvalidator.poc.network.WeekMetrics
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.storage.ReportRepository
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramWeek
import com.trainingvalidator.poc.training.session.ReportAggregator
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WeeklyReportActivity — Enhanced with unified metrics endpoint.
 * Shows per-week progress with comparison data and aggregated metrics.
 */
class WeeklyReportActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WeeklyReport"
        const val EXTRA_PROGRAM_SLUG = "program_slug"
        const val EXTRA_PROGRAM_ID = "program_id"
    }

    private lateinit var binding: ActivityWeeklyReportBinding
    private val reportStore by lazy { ProgramSessionReportStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvWeeklyReports.layoutManager = LinearLayoutManager(this)

        loadProgram()
    }

    private fun loadProgram() {
        val slug = intent.getStringExtra(EXTRA_PROGRAM_SLUG)
        val programId = intent.getStringExtra(EXTRA_PROGRAM_ID)
        if (slug.isNullOrBlank() || programId.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val repository = ProgramRepository.getInstance(this@WeeklyReportActivity)

            val program = withContext(Dispatchers.IO) {
                repository.getOrFetchProgram(slug)
            }
            if (program == null) {
                finish()
                return@launch
            }

            // First: show local data immediately
            bindProgram(program, programId)
            fetchProgramProgressMetrics(programId)

            // Then: fetch unified metrics from backend for enhanced display
            fetchUnifiedMetrics(program, programId)
        }
    }

    private fun bindProgram(program: ProgramConfig, programId: String) {
        val language = currentLanguage
        binding.tvProgramName.text = program.name.get(language).ifBlank { program.name.en }
        binding.tvProgramSubtitle.text = getString(R.string.weekly_report_subtitle)
        binding.rvWeeklyReports.adapter = WeeklyAdapter(program.weeks, programId, null)
    }

    private fun fetchProgramProgressMetrics(programId: String) {
        lifecycleScope.launch {
            try {
                val up = ProgramRepository.getInstance(this@WeeklyReportActivity).getActiveUserProgramExport()
                if (up?.programId != programId || !up.isActive) return@launch
                val token = AuthManager.getAuthHeader(this@WeeklyReportActivity) ?: return@launch
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getProgramProgressMetrics(up.id, token)
                }
                val body = resp.body()
                if (!resp.isSuccessful || body?.success != true) return@launch
                val weeks = body.data?.weeks.orEmpty()
                if (weeks.isEmpty()) return@launch
                val lines = weeks.joinToString("\n") { w ->
                    getString(
                        R.string.progress_metrics_week_format,
                        w.weekNumber,
                        w.totalVolumeLoad,
                        w.avgFormScore ?: 0.0,
                        w.avgRpe?.let { String.format("%.1f", it) } ?: "—"
                    )
                }
                binding.tvProgressMetrics.text =
                    getString(R.string.progress_metrics_title) + "\n" + lines
                binding.tvProgressMetrics.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.w(TAG, "progress metrics", e)
            }
        }
    }

    private fun fetchUnifiedMetrics(program: ProgramConfig, programId: String) {
        lifecycleScope.launch {
            try {
                val reportRepo = ReportRepository.getInstance(this@WeeklyReportActivity)
                val metrics = withContext(Dispatchers.IO) {
                    reportRepo.getProgramMetrics(programId, includeChildren = true)
                }

                val weeks = metrics?.summary?.weeks
                if (metrics != null && metrics.success && weeks != null) {
                    // Re-bind with unified data
                    binding.rvWeeklyReports.adapter =
                        WeeklyAdapter(program.weeks, programId, weeks)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch unified metrics", e)
            }
        }
    }

    inner class WeeklyAdapter(
        private val weeks: List<ProgramWeek>,
        private val programId: String,
        private val unifiedWeeks: List<WeekMetrics>?
    ) : RecyclerView.Adapter<WeeklyAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvWeekTitle: TextView = view.findViewById(R.id.tvWeekTitle)
            val tvWeekProgress: TextView = view.findViewById(R.id.tvWeekProgress)
            val progressWeek: com.google.android.material.progressindicator.LinearProgressIndicator =
                view.findViewById(R.id.progressWeek)
            val tvWeekMessage: TextView = view.findViewById(R.id.tvWeekMessage)
            val tvWeekReps: TextView = view.findViewById(R.id.tvWeekReps)
            val tvWeekAccuracy: TextView = view.findViewById(R.id.tvWeekAccuracy)
            val tvWeekDuration: TextView = view.findViewById(R.id.tvWeekDuration)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_weekly_report, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val week = weeks[position]
            val unified = unifiedWeeks?.find { it.weekNumber == week.weekNumber }

            if (unified != null) {
                // Use unified endpoint data
                bindFromUnified(holder, week, unified)
            } else {
                // Fallback to local data
                bindFromLocal(holder, week, programId)
            }
        }

        private fun bindFromUnified(holder: ViewHolder, week: ProgramWeek, unified: WeekMetrics) {
            holder.tvWeekTitle.text = getString(R.string.week_title_only, week.weekNumber)

            val progressPercent = if (unified.daysTotal > 0) {
                (unified.daysTrained * 100) / unified.daysTotal
            } else 0
            holder.progressWeek.progress = progressPercent

            holder.tvWeekProgress.text = if (unified.daysTrained >= unified.daysTotal && unified.daysTotal > 0) {
                getString(R.string.week_progress_completed)
            } else {
                getString(R.string.week_progress_format, unified.daysTrained, unified.daysTotal)
            }

            // Week-over-week comparison as message
            val comparison = unified.weekOverWeekChange
            holder.tvWeekMessage.text = if (comparison != null) {
                val delta = comparison.formScore
                val dir = if (delta >= 0) "+" else ""
                "Form ${dir}${delta.toInt()}% vs previous week"
            } else {
                getWeekMessage(unified.daysTrained, unified.daysTotal, unified.averageFormScore)
            }

            holder.tvWeekReps.text = getString(R.string.week_reps_format, unified.totalReps)
            holder.tvWeekAccuracy.text = getString(R.string.week_accuracy_format, unified.averageFormScore.toInt())
            holder.tvWeekDuration.text = getString(
                R.string.week_duration_format,
                ReportAggregator.formatDuration(unified.totalTrainingTime)
            )
        }

        /**
         * Fallback: display basic counts from local store (no metric calculations).
         * Backend is the source of truth; this only shows raw counts for offline mode.
         */
        private fun bindFromLocal(holder: ViewHolder, week: ProgramWeek, programId: String) {
            val reports = reportStore.getByWeek(programId, week.weekNumber)
            val totalSessions = week.days.sumOf { day -> day.sessions.size }
            val completedSessions = reports.size

            holder.tvWeekTitle.text = getString(R.string.week_title_only, week.weekNumber)
            holder.tvWeekProgress.text = if (totalSessions > 0 && completedSessions >= totalSessions) {
                getString(R.string.week_progress_completed)
            } else {
                getString(R.string.week_progress_format, completedSessions, totalSessions)
            }
            holder.progressWeek.progress = if (totalSessions > 0) {
                (completedSessions * 100) / totalSessions
            } else 0

            holder.tvWeekMessage.text = getWeekMessage(completedSessions, totalSessions, 0f)
            holder.tvWeekReps.text = getString(R.string.week_reps_format, reports.sumOf { it.totalReps })
            holder.tvWeekAccuracy.text = getString(R.string.week_accuracy_format, 0)
            holder.tvWeekDuration.text = getString(
                R.string.week_duration_format,
                formatDuration(reports.sumOf { it.totalDurationMs })
            )
        }

        override fun getItemCount() = weeks.size
    }

    private fun getWeekMessage(completed: Int, total: Int, avgAccuracy: Float): String {
        if (total == 0) return getString(R.string.week_message_start)
        val progress = completed.toFloat() / total
        return when {
            completed == 0 -> getString(R.string.week_message_start)
            progress >= 0.9f && avgAccuracy >= 85 -> getString(R.string.week_message_excellent)
            progress >= 0.6f -> getString(R.string.week_message_good)
            else -> getString(R.string.week_message_keep_going)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / 60000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            getString(R.string.duration_hours_minutes_format, hours, minutes)
        } else {
            getString(R.string.duration_minutes_format, minutes)
        }
    }
}
