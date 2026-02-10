package com.trainingvalidator.poc.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentHomeBinding
import com.trainingvalidator.poc.ui.TrainingActivity
import com.trainingvalidator.poc.ui.ProgramDetailActivity
import com.trainingvalidator.poc.ui.ProgramSessionActivity
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.training.models.ProgramConfig
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HomeFragment - Main dashboard with stats and quick actions
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupGreeting()
        loadUserData()
        loadActiveProgram()
        setupListeners()
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greetingRes = when {
            hour < 12 -> R.string.greeting_morning
            hour < 17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
        binding.tvGreeting.text = getString(greetingRes)
    }

    private fun loadUserData() {
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", "Athlete") ?: "Athlete"
        val firstName = name.split(" ").firstOrNull() ?: name
        binding.tvUserName.text = firstName
        
        // Mock stats - Replace with real data
        binding.tvWeeklyWorkouts.text = "5"
        binding.tvFormScore.text = "87%"
        binding.tvStreak.text = "12🔥"
        
        // Last exercise
        binding.tvLastExercise.text = getString(R.string.sample_exercise_squats)
        binding.tvLastProgress.text = getString(R.string.sets_completed_format, 2, 3)
    }

    private fun setupListeners() {
        binding.cardContinue.setOnClickListener {
            // Navigate to last exercise
        }

        binding.btnContinue.setOnClickListener {
            // Start training
            startActivity(Intent(requireContext(), TrainingActivity::class.java))
        }

        binding.cardStartCamera.setOnClickListener {
            // Navigate to exercises
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_exercises)
        }

        binding.cardAnalyzeVideo.setOnClickListener {
            // Open video picker
        }

        binding.ivAvatar.setOnClickListener {
            // Navigate to profile
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_profile)
        }
    }

    private fun loadActiveProgram() {
        lifecycleScope.launch {
            val programRepo = ProgramRepository.getInstance(requireContext())
            withContext(Dispatchers.IO) {
                programRepo.initialize()
            }

            val activeProgram = programRepo.getActiveProgram()
            val fallbackProgram = programRepo.getAllPrograms().firstOrNull()
            val program = activeProgram ?: fallbackProgram

            if (program == null) {
                binding.cardActiveProgram.visibility = View.GONE
                return@launch
            }

            val totalSessions = program.weeks.sumOf { week ->
                week.days.sumOf { day -> day.sessions.size }
            }

            binding.cardActiveProgram.visibility = View.VISIBLE
            binding.tvActiveProgramName.text = program.name.en
            binding.tvActiveProgramStats.text = getString(
                R.string.program_stats_format,
                program.durationWeeks,
                totalSessions
            )
            binding.btnViewProgram.setOnClickListener {
                val intent = Intent(requireContext(), ProgramDetailActivity::class.java).apply {
                    putExtra(ProgramDetailActivity.EXTRA_PROGRAM_SLUG, program.slug)
                }
                startActivity(intent)
            }

            bindTodayPlan(program)
        }
    }

    private fun bindTodayPlan(program: ProgramConfig) {
        val reportStore = ProgramSessionReportStore(requireContext())
        val language = java.util.Locale.getDefault().language

        val orderedSessions = program.weeks.sortedBy { it.weekNumber }.flatMap { week ->
            week.days.sortedBy { it.dayNumber }.flatMap { day ->
                if (day.isRestDay) {
                    emptyList()
                } else {
                    day.sessions.sortedBy { it.sortOrder }.map { session ->
                        Triple(week, day, session)
                    }
                }
            }
        }

        val nextSession = orderedSessions.firstOrNull { triple ->
            val session = triple.third
            reportStore.getBySession(session.id) == null
        }

        if (nextSession == null) {
            binding.cardTodayPlan.visibility = View.GONE
            return
        }

        val week = nextSession.first
        val day = nextSession.second
        val session = nextSession.third
        val sessionName = session.name.get(language).ifBlank { session.name.en }

        binding.cardTodayPlan.visibility = View.VISIBLE
        binding.tvTodayPlanTitle.text = getString(
            R.string.today_plan_title_format,
            week.weekNumber,
            day.dayNumber
        )
        binding.tvTodayPlanSubtitle.text = getString(
            R.string.today_plan_session_format,
            sessionName,
            session.items.size
        )
        binding.btnStartTodayPlan.setOnClickListener {
            val intent = Intent(requireContext(), ProgramSessionActivity::class.java).apply {
                putExtra(ProgramSessionActivity.EXTRA_PROGRAM_SLUG, program.slug)
                putExtra(ProgramSessionActivity.EXTRA_PROGRAM_ID, program.id)
                putExtra(ProgramSessionActivity.EXTRA_WEEK_NUMBER, week.weekNumber)
                putExtra(ProgramSessionActivity.EXTRA_DAY_NUMBER, day.dayNumber)
                putExtra(ProgramSessionActivity.EXTRA_TARGET_SESSION_ID, session.id)
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
