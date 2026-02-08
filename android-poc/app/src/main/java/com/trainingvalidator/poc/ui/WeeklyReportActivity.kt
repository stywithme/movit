package com.trainingvalidator.poc.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWeeklyReportBinding
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WeeklyReportActivity - Displays per-week progress summary for a program.
 */
class WeeklyReportActivity : AppCompatActivity() {

    companion object {
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

        CoroutineScope(Dispatchers.Main).launch {
            val repository = ProgramRepository.getInstance(this@WeeklyReportActivity)
            withContext(Dispatchers.IO) {
                repository.initialize()
            }

            val program = repository.getProgram(slug)
            if (program == null) {
                finish()
                return@launch
            }

            bindProgram(program, programId)
        }
    }

    private fun bindProgram(program: ProgramConfig, programId: String) {
        val language = java.util.Locale.getDefault().language
        binding.tvProgramName.text = program.name.get(language).ifBlank { program.name.en }
        binding.tvProgramSubtitle.text = getString(R.string.weekly_report_subtitle)
        binding.rvWeeklyReports.adapter = WeeklyAdapter(program.weeks, programId)
    }

    inner class WeeklyAdapter(
        private val weeks: List<ProgramWeek>,
        private val programId: String
    ) : RecyclerView.Adapter<WeeklyAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvWeekTitle: TextView = view.findViewById(R.id.tvWeekTitle)
            val tvWeekProgress: TextView = view.findViewById(R.id.tvWeekProgress)
            val tvWeekReps: TextView = view.findViewById(R.id.tvWeekReps)
            val tvWeekAccuracy: TextView = view.findViewById(R.id.tvWeekAccuracy)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_weekly_report, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val week = weeks[position]
            val reports = reportStore.getByWeek(programId, week.weekNumber)
            val totalSessions = week.days.sumOf { day -> day.sessions.size }
            val completedSessions = reports.size
            val totalReps = reports.sumOf { it.totalReps }
            val avgAccuracy = if (reports.isNotEmpty()) {
                reports.map { it.averageAccuracy }.average().toFloat()
            } else {
                0f
            }

            holder.tvWeekTitle.text = getString(R.string.week_title_only, week.weekNumber)
            holder.tvWeekProgress.text = if (totalSessions > 0 && completedSessions >= totalSessions) {
                getString(R.string.week_progress_completed)
            } else {
                getString(R.string.week_progress_format, completedSessions, totalSessions)
            }
            holder.tvWeekReps.text = getString(R.string.week_reps_format, totalReps)
            holder.tvWeekAccuracy.text = getString(R.string.week_accuracy_format, avgAccuracy.toInt())
        }

        override fun getItemCount() = weeks.size
    }
}
