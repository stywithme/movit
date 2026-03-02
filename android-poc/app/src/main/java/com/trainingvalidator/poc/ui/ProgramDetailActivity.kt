package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageView
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
import android.widget.ProgressBar

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
    private val homeRepository by lazy { com.trainingvalidator.poc.storage.HomeRepository.getInstance(this) }
    private var isEnrolled = false
    private var isEnrolling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProgramDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadProgram()
    }

    private fun setupUI() {
        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvWeeks.layoutManager = LinearLayoutManager(this)
        
        binding.btnStartProgram.setOnClickListener {
            handleCTA()
        }
    }

    private fun handleCTA() {
        if (isEnrolled) {
            // Already enrolled, go to week 1 (or overview)
            openWeek(1)
        } else {
            enrollInProgram()
        }
    }

    private fun enrollInProgram() {
        if (isEnrolling || program == null) return
        isEnrolling = true
        binding.btnStartProgram.text = getString(R.string.loading)
        binding.btnStartProgram.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = com.trainingvalidator.poc.storage.AuthManager.getAuthHeader(this@ProgramDetailActivity)
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProgramDetailActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        resetCTAButton()
                    }
                    return@launch
                }
                
                val payload = mapOf("programId" to program!!.id)
                val response = com.trainingvalidator.poc.network.ApiClient.mobileSyncApi.enrollProgram(token, payload)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    // Refresh home cache so Train/Home screens pick up the new enrollment
                    homeRepository.syncFromServer()
                    // Also force a content sync so ProgramCacheManager has the data
                    try {
                        val exerciseRepo = com.trainingvalidator.poc.storage.ExerciseRepository.getInstance(this@ProgramDetailActivity)
                        exerciseRepo.checkForUpdates()
                    } catch (_: Exception) { }
                    withContext(Dispatchers.Main) {
                        isEnrolled = true
                        updateCTAButton()
                        Toast.makeText(this@ProgramDetailActivity, "Successfully enrolled!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProgramDetailActivity, "Failed to enroll", Toast.LENGTH_SHORT).show()
                        resetCTAButton()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProgramDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetCTAButton()
                }
            } finally {
                isEnrolling = false
            }
        }
    }

    private fun updateCTAButton() {
        binding.btnStartProgram.isEnabled = true
        if (isEnrolled) {
            binding.btnStartProgram.text = "Resume Program"
        } else {
            binding.btnStartProgram.text = "Start Program"
        }
    }

    private fun resetCTAButton() {
        binding.btnStartProgram.isEnabled = true
        updateCTAButton()
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

            program = withContext(Dispatchers.IO) {
                repository.getOrFetchProgram(slug)
            }

            if (program == null) {
                Toast.makeText(this@ProgramDetailActivity, "Program not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            bindProgram(program!!)
            
            withContext(Dispatchers.IO) {
                homeRepository.syncFromServer()
            }
            checkEnrollmentStatus()
        }
    }

    private fun bindProgram(program: ProgramConfig) {
        val language = getCurrentLanguage()
        val programId = program.id
        
        val programName = program.name.get(language).ifBlank { program.name.en }
        
        binding.collapsingToolbar.title = programName
        binding.tvProgramName.text = programName
        
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

        binding.btnWeeklyReport.setOnClickListener {
            val intent = Intent(this, WeeklyReportActivity::class.java).apply {
                putExtra(WeeklyReportActivity.EXTRA_PROGRAM_SLUG, program.slug)
                putExtra(WeeklyReportActivity.EXTRA_PROGRAM_ID, programId)
            }
            startActivity(intent)
        }

        binding.rvWeeks.adapter = WeekAdapter(program.weeks, programId)
        
        checkEnrollmentStatus()
    }

    private fun checkEnrollmentStatus() {
        val cachedPlan = homeRepository.getCachedData()?.activePlan
        isEnrolled = cachedPlan?.programs?.any { it.program?.id == program?.id && it.status == "active" } == true
        
        // Ensure UI updates run on Main thread
        runOnUiThread {
            updateCTAButton()
        }
    }

    override fun onResume() {
        super.onResume()
        if (program != null) {
            checkEnrollmentStatus()
            binding.rvWeeks.adapter?.notifyDataSetChanged()
        }
    }

    inner class WeekAdapter(
        private val weeks: List<ProgramWeek>,
        private val programId: String
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
            val language = getCurrentLanguage()

            val title = week.name?.let { it.get(language).ifBlank { it.en } } ?: ""
            holder.tvWeekTitle.text = if (title.isNotBlank()) {
                getString(R.string.week_title_with_name, week.weekNumber, title)
            } else {
                getString(R.string.week_title_only, week.weekNumber)
            }

            val dayCount = week.days.size
            val restDays = week.days.count { it.isRestDay }
            val sessions = week.days.sumOf { it.sessions.size }

            holder.tvWeekSubtitle.text = "$sessions Sessions • $restDays Rest Days"

            val completedSessions = reportStore.getByWeek(programId, week.weekNumber).size
            
            holder.pbWeekProgress.max = if (sessions > 0) sessions else 1
            holder.pbWeekProgress.progress = completedSessions

            holder.tvWeekProgress.text = if (sessions > 0 && completedSessions >= sessions) {
                getString(R.string.week_progress_completed)
            } else {
                getString(R.string.week_progress_format, completedSessions, sessions)
            }

            renderWeekDays(holder.layoutWeekDays, week, programId)

            val isExpanded = expandedWeeks.contains(week.weekNumber)
            holder.layoutWeekDays.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.ivExpand.rotation = if (isExpanded) 90f else 0f

            holder.layoutWeekHeader.setOnClickListener {
                if (expandedWeeks.contains(week.weekNumber)) {
                    expandedWeeks.remove(week.weekNumber)
                } else {
                    expandedWeeks.add(week.weekNumber)
                }
                notifyItemChanged(position)
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
        val inflater = LayoutInflater.from(container.context)

        week.days.sortedBy { it.dayNumber }.forEach { day ->
            val completedSessions = reportStore.getByDay(programId, week.weekNumber, day.dayNumber)
            val isCompleted = !day.isRestDay && completedSessions.isNotEmpty() &&
                completedSessions.size >= day.sessions.size

            val view = inflater.inflate(R.layout.item_program_day_compact, container, false)
            
            val ivDayIcon = view.findViewById<ImageView>(R.id.ivDayIcon)
            val tvDayName = view.findViewById<TextView>(R.id.tvDayName)
            val tvDayDetails = view.findViewById<TextView>(R.id.tvDayDetails)
            val ivDayStatus = view.findViewById<ImageView>(R.id.ivDayStatus)

            tvDayName.text = "Day ${day.dayNumber}"
            
            if (day.isRestDay) {
                ivDayIcon.setImageResource(R.drawable.ic_rest)
                ivDayIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_tertiary))
                tvDayDetails.text = "Rest Day"
            } else {
                ivDayIcon.setImageResource(R.drawable.ic_workout)
                ivDayIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
                val sessionText = if (day.sessions.size == 1) "1 Session" else "${day.sessions.size} Sessions"
                tvDayDetails.text = sessionText
            }

            if (isCompleted) {
                ivDayStatus.visibility = View.VISIBLE
            } else {
                ivDayStatus.visibility = View.GONE
            }

            view.setOnClickListener {
                openWeek(week.weekNumber)
            }

            container.addView(view)
        }
    }
}