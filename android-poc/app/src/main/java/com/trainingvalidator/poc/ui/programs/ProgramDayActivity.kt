package com.trainingvalidator.poc.ui.programs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramDayBinding
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramDay
import com.trainingvalidator.poc.training.models.ProgramSession
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramDayActivity - Shows days and sessions for a selected week (Week Plan)
 */
class ProgramDayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROGRAM_SLUG = "program_slug"
        const val EXTRA_WEEK_NUMBER = "week_number"
    }

    private lateinit var binding: ActivityProgramDayBinding
    private var program: ProgramConfig? = null
    private var weekNumber: Int = 1
    private val reportStore by lazy { ProgramSessionReportStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProgramDayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        weekNumber = intent.getIntExtra(EXTRA_WEEK_NUMBER, 1)

        setupUI()
        loadProgram()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvDays.layoutManager = LinearLayoutManager(this)
    }

    private fun loadProgram() {
        val slug = intent.getStringExtra(EXTRA_PROGRAM_SLUG)
        if (slug.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.error_program_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val repository = ProgramRepository.getInstance(this@ProgramDayActivity)

            program = withContext(Dispatchers.IO) {
                repository.getOrFetchProgram(slug)
            }

            val resolvedProgram = program
            if (resolvedProgram == null) {
                Toast.makeText(this@ProgramDayActivity, getString(R.string.error_program_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            bindWeek(resolvedProgram)
        }
    }

    private fun bindWeek(program: ProgramConfig) {
        val week = program.weeks.firstOrNull { it.weekNumber == weekNumber }
        if (week == null) {
            Toast.makeText(this, getString(R.string.error_week_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val language = currentLanguage
        val title = week.name?.let { it.get(language).ifBlank { it.en } } ?: ""
        
        binding.tvWeekTitle.text = if (title.isNotBlank()) {
            getString(R.string.week_title_with_name, week.weekNumber, title)
        } else {
            getString(R.string.week_title_only, week.weekNumber)
        }
        
        val sessionsCount = week.days.sumOf { it.sessions.size }
        val restDaysCount = week.days.count { it.isRestDay }
        binding.tvWeekSubtitle.text = getString(R.string.week_sessions_rest_format, sessionsCount, restDaysCount)

        // Sort days logically
        val sortedDays = week.days.sortedBy { it.dayNumber }
        binding.rvDays.adapter = DayAdapter(sortedDays, program.id)
    }

    inner class DayAdapter(
        private val days: List<ProgramDay>,
        private val programId: String
    ) : RecyclerView.Adapter<DayAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivDayIcon: ImageView = view.findViewById(R.id.ivDayIcon)
            val tvDayTitle: TextView = view.findViewById(R.id.tvDayTitle)
            val tvDaySubtitle: TextView = view.findViewById(R.id.tvDaySubtitle)
            val sessionsContainer: LinearLayout = view.findViewById(R.id.sessionsContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_day, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val day = days[position]
            val language = currentLanguage

            val dayName = day.name?.let { it.get(language).ifBlank { it.en } } ?: ""
            holder.tvDayTitle.text = if (dayName.isNotBlank()) {
                getString(R.string.day_title_with_name, day.dayNumber, dayName)
            } else {
                getString(R.string.day_title_only, day.dayNumber)
            }

            if (day.isRestDay) {
                holder.ivDayIcon.setImageResource(R.drawable.ic_rest)
                holder.ivDayIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_tertiary))
                holder.tvDaySubtitle.text = getString(R.string.rest_day)
                holder.sessionsContainer.removeAllViews()
                return
            } else {
                holder.ivDayIcon.setImageResource(R.drawable.ic_workout)
                holder.ivDayIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
            }

            val completedSessions = reportStore.getByDay(programId, weekNumber, day.dayNumber).size
            holder.tvDaySubtitle.text = getString(
                R.string.sessions_progress_format,
                completedSessions,
                day.sessions.size
            )

            holder.sessionsContainer.removeAllViews()
            day.sessions.forEach { session ->
                val sessionView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_program_session, holder.sessionsContainer, false)

                val ivSessionIcon = sessionView.findViewById<ImageView>(R.id.ivSessionIcon)
                val tvSessionName = sessionView.findViewById<TextView>(R.id.tvSessionName)
                val tvSessionDetails = sessionView.findViewById<TextView>(R.id.tvSessionDetails)
                val ivSessionStatus = sessionView.findViewById<ImageView>(R.id.ivSessionStatus)
                val ivSessionPlay = sessionView.findViewById<ImageView>(R.id.ivSessionPlay)
                val layoutSessionScore = sessionView.findViewById<LinearLayout>(R.id.layoutSessionScore)
                val btnSessionRestart = sessionView.findViewById<ImageView>(R.id.btnSessionRestart)

                tvSessionName.text = session.name.get(language).ifBlank { session.name.en }

                // Details (e.g., 6 exercises • ~25 min)
                val durationText = session.estimatedDurationMin?.let { em ->
                    getString(R.string.session_duration_badge, em)
                } ?: "${session.items.size * 5} min"
                tvSessionDetails.text = "${session.items.size} exercises • $durationText"

                val report = reportStore.getBySession(session.id)
                val isCompleted = report != null
                
                if (isCompleted) {
                    ivSessionStatus.visibility = View.VISIBLE
                    ivSessionPlay.visibility = View.GONE
                    layoutSessionScore.visibility = View.VISIBLE
                    
                    // Dim the entire card to show it's done
                    sessionView.alpha = 0.7f
                } else {
                    ivSessionStatus.visibility = View.GONE
                    ivSessionPlay.visibility = View.VISIBLE
                    layoutSessionScore.visibility = View.GONE
                    
                    sessionView.alpha = 1.0f
                }

                // Click card to start/resume
                sessionView.setOnClickListener {
                    openSession(day.dayNumber, session)
                }

                // Click restart icon to reset
                btnSessionRestart.setOnClickListener {
                    reportStore.delete(session.id)
                    notifyItemChanged(position)
                }

                holder.sessionsContainer.addView(sessionView)
            }
        }

        override fun getItemCount() = days.size
    }

    private fun openSession(dayNumber: Int, session: ProgramSession) {
        val slug = program?.slug ?: return
        val intent = Intent(this, ProgramSessionActivity::class.java).apply {
            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_SLUG, slug)
            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_ID, program?.id ?: "")
            putExtra(ProgramSessionActivity.EXTRA_WEEK_NUMBER, weekNumber)
            putExtra(ProgramSessionActivity.EXTRA_DAY_NUMBER, dayNumber)
            putExtra(ProgramSessionActivity.EXTRA_TARGET_SESSION_ID, session.id)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Refresh when coming back (might have completed a session)
        program?.let { bindWeek(it) }
    }
}