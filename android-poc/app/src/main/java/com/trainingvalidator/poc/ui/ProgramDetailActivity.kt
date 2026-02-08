package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramDetailBinding
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramWeek
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramDetailActivity - Shows program overview and week list
 */
class ProgramDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROGRAM_SLUG = "program_slug"
    }

    private lateinit var binding: ActivityProgramDetailBinding
    private var program: ProgramConfig? = null
    private val reportStore by lazy { ProgramSessionReportStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProgramDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadProgram()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.rvWeeks.layoutManager = LinearLayoutManager(this)
    }

    private fun loadProgram() {
        val slug = intent.getStringExtra(EXTRA_PROGRAM_SLUG)
        if (slug.isNullOrBlank()) {
            Toast.makeText(this, "Program not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val repository = ProgramRepository.getInstance(this@ProgramDetailActivity)
            withContext(Dispatchers.IO) {
                repository.initialize()
            }

            program = repository.getProgram(slug)
            if (program == null) {
                Toast.makeText(this@ProgramDetailActivity, "Program not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            bindProgram(program!!)
        }
    }

    private fun bindProgram(program: ProgramConfig) {
        val language = getCurrentLanguage()
        val programId = program.id

        binding.tvProgramName.text = program.name.get(language).ifBlank { program.name.en }
        binding.tvProgramDescription.text = program.description?.let { desc ->
            desc.get(language).ifBlank { desc.en }
        } ?: ""

        val totalDays = program.weeks.sumOf { it.days.size }
        val totalSessions = program.weeks.sumOf { week ->
            week.days.sumOf { day -> day.sessions.size }
        }

        binding.tvProgramWeeks.text = getString(R.string.weeks_count_format, program.durationWeeks)
        binding.tvProgramDays.text = getString(R.string.days_count_format, totalDays)
        binding.tvProgramSessions.text = getString(R.string.sessions_count_format, totalSessions)
        binding.tvProgramDifficulty.text = formatDifficulty(program.difficulty)

        binding.rvWeeks.adapter = WeekAdapter(program.weeks, programId)
    }

    inner class WeekAdapter(
        private val weeks: List<ProgramWeek>,
        private val programId: String
    ) : RecyclerView.Adapter<WeekAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvWeekTitle: TextView = view.findViewById(R.id.tvWeekTitle)
            val tvWeekSubtitle: TextView = view.findViewById(R.id.tvWeekSubtitle)
            val tvWeekStats: TextView = view.findViewById(R.id.tvWeekStats)
            val tvWeekProgress: TextView = view.findViewById(R.id.tvWeekProgress)
            val layoutWeekDays: LinearLayout = view.findViewById(R.id.layoutWeekDays)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_week, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val week = weeks[position]
            val language = getCurrentLanguage()

            val title = week.name?.get(language)?.ifBlank { week.name?.en } ?: ""
            holder.tvWeekTitle.text = if (title.isNotBlank()) {
                getString(R.string.week_title_with_name, week.weekNumber, title)
            } else {
                getString(R.string.week_title_only, week.weekNumber)
            }

            holder.tvWeekSubtitle.text = week.description?.get(language)
                ?.ifBlank { week.description?.en }
                ?: getString(R.string.week_default_desc)

            val dayCount = week.days.size
            val restDays = week.days.count { it.isRestDay }
            val sessions = week.days.sumOf { it.sessions.size }

            holder.tvWeekStats.text = getString(
                R.string.week_stats_format,
                dayCount,
                restDays,
                sessions
            )

            val completedSessions = reportStore.getByWeek(programId, week.weekNumber).size
            holder.tvWeekProgress.text = if (sessions > 0 && completedSessions >= sessions) {
                getString(R.string.week_progress_completed)
            } else {
                getString(R.string.week_progress_format, completedSessions, sessions)
            }

            renderWeekDays(holder.layoutWeekDays, week, programId)

            holder.itemView.setOnClickListener {
                openWeek(week.weekNumber)
            }
        }

        override fun getItemCount() = weeks.size
    }

    private fun getCurrentLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) {
            resources.configuration.locales[0]
        } else {
            appLocales[0]
        }
        return locale?.language ?: "en"
    }

    private fun formatDifficulty(difficulty: String): String {
        if (difficulty.isBlank()) return getString(R.string.workout_detail_default_difficulty)
        val normalized = difficulty.replace('_', ' ').lowercase()
        return normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun openWeek(weekNumber: Int) {
        val slug = program?.slug ?: return
        val intent = Intent(this, ProgramDayActivity::class.java).apply {
            putExtra(ProgramDayActivity.EXTRA_PROGRAM_SLUG, slug)
            putExtra(ProgramDayActivity.EXTRA_WEEK_NUMBER, weekNumber)
        }
        startActivity(intent)
    }

    private fun renderWeekDays(container: LinearLayout, week: ProgramWeek, programId: String) {
        container.removeAllViews()
        val daySize = resources.getDimensionPixelSize(R.dimen.spacing_lg)
        val margin = resources.getDimensionPixelSize(R.dimen.spacing_xs)
        week.days.sortedBy { it.dayNumber }.forEach { day ->
            val completedSessions = reportStore.getByDay(programId, week.weekNumber, day.dayNumber)
            val isCompleted = !day.isRestDay && completedSessions.isNotEmpty() &&
                completedSessions.size >= day.sessions.size
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(daySize, daySize).also { params ->
                    params.marginEnd = margin
                }
                text = day.dayNumber.toString()
                setTextColor(
                    if (day.isRestDay) getColor(R.color.text_tertiary) else getColor(R.color.text_primary)
                )
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(
                    when {
                        day.isRestDay -> R.drawable.bg_circle_surface
                        isCompleted -> R.drawable.bg_circle_success
                        else -> R.drawable.bg_circle_primary_light
                    }
                )
            }
            container.addView(tv)
        }
    }
}
