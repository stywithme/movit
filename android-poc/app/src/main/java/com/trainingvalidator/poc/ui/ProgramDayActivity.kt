package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramDayBinding
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramDay
import com.trainingvalidator.poc.training.models.ProgramSession
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramDayActivity - Shows days and sessions for a selected week
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
        binding.btnBack.setOnClickListener { finish() }
        binding.rvDays.layoutManager = LinearLayoutManager(this)
    }

    private fun loadProgram() {
        val slug = intent.getStringExtra(EXTRA_PROGRAM_SLUG)
        if (slug.isNullOrBlank()) {
            Toast.makeText(this, "Program not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val repository = ProgramRepository.getInstance(this@ProgramDayActivity)
            withContext(Dispatchers.IO) {
                repository.initialize()
            }

            program = repository.getProgram(slug)
            if (program == null) {
                Toast.makeText(this@ProgramDayActivity, "Program not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            bindWeek(program!!)
        }
    }

    private fun bindWeek(program: ProgramConfig) {
        val week = program.weeks.firstOrNull { it.weekNumber == weekNumber }
        if (week == null) {
            Toast.makeText(this, "Week not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvWeekTitle.text = getString(R.string.week_title_only, week.weekNumber)
        binding.rvDays.adapter = DayAdapter(week.days, program.id)
    }

    inner class DayAdapter(
        private val days: List<ProgramDay>,
        private val programId: String
    ) : RecyclerView.Adapter<DayAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
            val language = getCurrentLanguage()

            val dayName = day.name?.get(language)?.ifBlank { day.name?.en } ?: ""
            holder.tvDayTitle.text = if (dayName.isNotBlank()) {
                getString(R.string.day_title_with_name, day.dayNumber, dayName)
            } else {
                getString(R.string.day_title_only, day.dayNumber)
            }

            if (day.isRestDay) {
                holder.tvDaySubtitle.text = getString(R.string.rest_day)
                holder.sessionsContainer.removeAllViews()
                return
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

                val tvSessionName = sessionView.findViewById<TextView>(R.id.tvSessionName)
                val tvSessionSubtitle = sessionView.findViewById<TextView>(R.id.tvSessionSubtitle)
                val tvSessionStatus = sessionView.findViewById<TextView>(R.id.tvSessionStatus)
                val btnPrimary = sessionView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSessionPrimary)
                val btnSecondary = sessionView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSessionSecondary)

                tvSessionName.text = session.name.get(language).ifBlank { session.name.en }
                tvSessionSubtitle.text = getString(
                    R.string.session_items_count_format,
                    session.items.size
                )

                val report = reportStore.getBySession(session.id)
                val isCompleted = report != null
                tvSessionStatus.text = if (isCompleted) {
                    getString(R.string.session_status_completed)
                } else {
                    getString(R.string.session_status_not_started)
                }

                btnPrimary.text = if (isCompleted) {
                    getString(R.string.continue_session)
                } else {
                    getString(R.string.start_session)
                }
                btnPrimary.setOnClickListener {
                    openSession(session)
                }

                btnSecondary.visibility = if (isCompleted) View.VISIBLE else View.GONE
                btnSecondary.text = getString(R.string.restart_session)
                btnSecondary.setOnClickListener {
                    reportStore.delete(session.id)
                    notifyItemChanged(position)
                }

                holder.sessionsContainer.addView(sessionView)
            }
        }

        override fun getItemCount() = days.size
    }

    private fun openSession(session: ProgramSession) {
        val slug = program?.slug ?: return
        val intent = Intent(this, ProgramSessionActivity::class.java).apply {
            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_SLUG, slug)
            putExtra(ProgramSessionActivity.EXTRA_SESSION_ID, session.id)
        }
        startActivity(intent)
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
}
