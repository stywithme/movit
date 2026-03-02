package com.trainingvalidator.poc.ui.programs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramDetailBinding
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramWeek
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.launch

/**
 * ProgramDetailActivity - Shows program overview and week list.
 * Data loading and enrollment logic delegated to ProgramDetailViewModel.
 */
class ProgramDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROGRAM_SLUG = "program_slug"
    }

    private lateinit var binding: ActivityProgramDetailBinding
    private val viewModel: ProgramDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgramDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()

        val slug = intent.getStringExtra(EXTRA_PROGRAM_SLUG)
        if (slug.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.error_program_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        viewModel.loadProgram(slug)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnrollment()
        binding.rvWeeks.adapter?.notifyDataSetChanged()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvWeeks.layoutManager = LinearLayoutManager(this)
        binding.btnStartProgram.setOnClickListener { handleCTA() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is ProgramDetailUiState.Loading -> Unit
                            is ProgramDetailUiState.Success -> bindProgram(state.program, state.isEnrolled)
                            is ProgramDetailUiState.Error -> {
                                Toast.makeText(
                                    this@ProgramDetailActivity,
                                    getString(R.string.error_program_not_found),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }
                    }
                }
                launch {
                    viewModel.enrollState.collect { state ->
                        when (state) {
                            is EnrollState.Idle -> Unit
                            is EnrollState.Loading -> {
                                binding.btnStartProgram.text = getString(R.string.loading)
                                binding.btnStartProgram.isEnabled = false
                            }
                            is EnrollState.Success -> {
                                Toast.makeText(this@ProgramDetailActivity, getString(R.string.enrolled_success), Toast.LENGTH_SHORT).show()
                                viewModel.resetEnrollState()
                            }
                            is EnrollState.Error -> {
                                val msg = when (state.message) {
                                    "no_auth" -> getString(R.string.error_login_required)
                                    "enroll_failed" -> getString(R.string.error_enroll_failed)
                                    else -> getString(R.string.error_generic, state.message)
                                }
                                Toast.makeText(this@ProgramDetailActivity, msg, Toast.LENGTH_SHORT).show()
                                updateCTAButton(isEnrolled = (viewModel.uiState.value as? ProgramDetailUiState.Success)?.isEnrolled ?: false)
                                viewModel.resetEnrollState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleCTA() {
        val state = viewModel.uiState.value as? ProgramDetailUiState.Success ?: return
        if (state.isEnrolled) {
            val currentWeek = viewModel.resolveCurrentWeek(state.program.id)
            openWeek(state.program.slug, currentWeek)
        } else {
            viewModel.enrollInProgram(state.program.id)
        }
    }

    private fun bindProgram(program: ProgramConfig, isEnrolled: Boolean) {
        val language = currentLanguage
        val programName = program.name.get(language).ifBlank { program.name.en }

        binding.collapsingToolbar.title = programName
        binding.tvProgramName.text = programName
        binding.tvProgramDescription.text = program.description?.let { desc ->
            desc.get(language).ifBlank { desc.en }
        } ?: ""

        val totalDays = program.weeks.sumOf { it.days.size }
        val totalSessions = program.weeks.sumOf { week -> week.days.sumOf { day -> day.sessions.size } }

        binding.tvProgramWeeks.text = getString(R.string.weeks_count_format, program.durationWeeks)
        binding.tvProgramDays.text = getString(R.string.days_count_format, totalDays)
        binding.tvProgramSessions.text = getString(R.string.sessions_count_format, totalSessions)
        binding.tvProgramDifficulty.text = formatDifficulty(program.difficulty)

        binding.btnWeeklyReport.setOnClickListener {
            startActivity(Intent(this, WeeklyReportActivity::class.java).apply {
                putExtra(WeeklyReportActivity.EXTRA_PROGRAM_SLUG, program.slug)
                putExtra(WeeklyReportActivity.EXTRA_PROGRAM_ID, program.id)
            })
        }

        binding.rvWeeks.adapter = WeekAdapter(program.weeks, program.id, program.slug)
        updateCTAButton(isEnrolled)
    }

    private fun updateCTAButton(isEnrolled: Boolean) {
        binding.btnStartProgram.isEnabled = true
        binding.btnStartProgram.text = if (isEnrolled) {
            getString(R.string.resume_program)
        } else {
            getString(R.string.start_program)
        }
    }

    private fun openWeek(slug: String, weekNumber: Int) {
        startActivity(Intent(this, ProgramDayActivity::class.java).apply {
            putExtra(ProgramDayActivity.EXTRA_PROGRAM_SLUG, slug)
            putExtra(ProgramDayActivity.EXTRA_WEEK_NUMBER, weekNumber)
        })
    }

    private fun formatDifficulty(difficulty: String): String {
        if (difficulty.isBlank()) return getString(R.string.workout_detail_default_difficulty)
        return difficulty.replace('_', ' ').lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    inner class WeekAdapter(
        private val weeks: List<ProgramWeek>,
        private val programId: String,
        private val programSlug: String
    ) : RecyclerView.Adapter<WeekAdapter.ViewHolder>() {

        private val expandedWeeks = mutableSetOf<Int>()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvWeekTitle: TextView = view.findViewById(R.id.tvWeekTitle)
            val tvWeekSubtitle: TextView = view.findViewById(R.id.tvWeekSubtitle)
            val tvWeekProgress: TextView = view.findViewById(R.id.tvWeekProgress)
            val pbWeekProgress: ProgressBar = view.findViewById(R.id.pbWeekProgress)
            val layoutWeekDays: LinearLayout = view.findViewById(R.id.layoutWeekDays)
            val layoutWeekHeader: LinearLayout = view.findViewById(R.id.layoutWeekHeader)
            val ivExpand: ImageView = view.findViewById(R.id.ivExpand)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_week, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val week = weeks[position]
            val language = currentLanguage
            val title = week.name?.let { it.get(language).ifBlank { it.en } } ?: ""
            holder.tvWeekTitle.text = if (title.isNotBlank()) {
                getString(R.string.week_title_with_name, week.weekNumber, title)
            } else {
                getString(R.string.week_title_only, week.weekNumber)
            }

            val sessions = week.days.sumOf { it.sessions.size }
            val restDays = week.days.count { it.isRestDay }
            holder.tvWeekSubtitle.text = getString(R.string.week_sessions_rest_format, sessions, restDays)

            val completedSessions = viewModel.reportStore.getByWeek(programId, week.weekNumber).size
            holder.pbWeekProgress.max = if (sessions > 0) sessions else 1
            holder.pbWeekProgress.progress = completedSessions
            holder.tvWeekProgress.text = if (sessions > 0 && completedSessions >= sessions) {
                getString(R.string.week_progress_completed)
            } else {
                getString(R.string.week_progress_format, completedSessions, sessions)
            }

            renderWeekDays(holder.layoutWeekDays, week, programId, programSlug)

            val isExpanded = expandedWeeks.contains(week.weekNumber)
            holder.layoutWeekDays.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.ivExpand.rotation = if (isExpanded) 90f else 0f

            holder.layoutWeekHeader.setOnClickListener {
                if (expandedWeeks.contains(week.weekNumber)) expandedWeeks.remove(week.weekNumber)
                else expandedWeeks.add(week.weekNumber)
                notifyItemChanged(position)
            }
        }

        override fun getItemCount() = weeks.size
    }

    private fun renderWeekDays(container: LinearLayout, week: ProgramWeek, programId: String, programSlug: String) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        week.days.sortedBy { it.dayNumber }.forEach { day ->
            val completedSessions = viewModel.reportStore.getByDay(programId, week.weekNumber, day.dayNumber)
            val isCompleted = !day.isRestDay && completedSessions.isNotEmpty() &&
                completedSessions.size >= day.sessions.size

            val view = inflater.inflate(R.layout.item_program_day_compact, container, false)
            val ivDayIcon = view.findViewById<ImageView>(R.id.ivDayIcon)
            val tvDayName = view.findViewById<TextView>(R.id.tvDayName)
            val tvDayDetails = view.findViewById<TextView>(R.id.tvDayDetails)
            val ivDayStatus = view.findViewById<ImageView>(R.id.ivDayStatus)

            tvDayName.text = getString(R.string.day_number_format, day.dayNumber)

            if (day.isRestDay) {
                ivDayIcon.setImageResource(R.drawable.ic_rest)
                ivDayIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_tertiary))
                tvDayDetails.text = getString(R.string.rest_day_label)
            } else {
                ivDayIcon.setImageResource(R.drawable.ic_workout)
                ivDayIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
                tvDayDetails.text = getString(R.string.sessions_count_format, day.sessions.size)
            }

            ivDayStatus.visibility = if (isCompleted) View.VISIBLE else View.GONE
            view.setOnClickListener { openWeek(programSlug, week.weekNumber) }
            container.addView(view)
        }
    }
}
