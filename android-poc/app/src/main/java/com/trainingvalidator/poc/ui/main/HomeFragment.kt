package com.trainingvalidator.poc.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentHomeBinding
import com.trainingvalidator.poc.assessment.ui.PreScreeningActivity
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.LevelProfileActivity
import com.trainingvalidator.poc.ui.PlanOverviewActivity
import com.trainingvalidator.poc.ui.TrainingActivity
import com.trainingvalidator.poc.ui.ProgramDetailActivity
import com.trainingvalidator.poc.ui.ProgramSessionActivity
import com.trainingvalidator.poc.storage.DayCustomizationStore
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

    companion object {
        private const val TAG = "HomeFragment"
    }

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
        loadLevelProfile()
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
        val ctx = requireContext()
        val name = AuthManager.getUserName(ctx, "Athlete")
        val firstName = name.split(" ").firstOrNull() ?: name
        binding.tvUserName.text = firstName

        // Show cached stats immediately (from last known values)
        showCachedStats()

        // Fetch real stats from backend
        fetchLiveStats()
    }

    /**
     * Show locally cached stats as placeholder while fetching from the server.
     */
    private fun showCachedStats() {
        val ctx = requireContext()
        val totalWorkouts = AuthManager.getTotalWorkouts(ctx)
        binding.tvWeeklyWorkouts.text = "$totalWorkouts"
        binding.tvFormScore.text = "--"
        binding.tvStreak.text = "0"
    }

    /**
     * Fetch live stats from GET /mobile/sessions/stats and update the UI.
     */
    private fun fetchLiveStats() {
        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                val authHeader = AuthManager.getAuthHeader(ctx) ?: return@launch

                val response = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getUserStats(authHeader)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    val stats = response.body()?.data ?: return@launch

                    binding.tvWeeklyWorkouts.text = "${stats.weeklyWorkouts}"
                    binding.tvFormScore.text = if (stats.avgFormScore > 0) {
                        "${stats.avgFormScore.toInt()}%"
                    } else {
                        "--"
                    }
                    binding.tvStreak.text = if (stats.streak > 0) {
                        "${stats.streak}\uD83D\uDD25"
                    } else {
                        "0"
                    }
                } else {
                    Log.w(TAG, "Failed to fetch stats: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching live stats (offline?)", e)
            }
        }
    }

    /**
     * Load the user's level profile from the backend and show the "My Level" card.
     */
    private fun loadLevelProfile() {
        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                val authHeader = AuthManager.getAuthHeader(ctx) ?: return@launch

                val response = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getLevelProfile(authHeader)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    val profile = response.body()?.data ?: return@launch
                    val levelName = profile.levelInfo.name.en
                    val levelNumber = profile.overallLevel

                    binding.cardMyLevel.visibility = View.VISIBLE
                    binding.tvLevelName.text = "Level $levelNumber — $levelName"
                    binding.tvLevelScore.text = "Body Score: ${profile.bodyScore.toInt()}"

                    // Tap to open full level profile
                    binding.cardMyLevel.setOnClickListener {
                        startActivity(LevelProfileActivity.createIntent(ctx))
                    }
                } else {
                    binding.cardMyLevel.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load level profile", e)
                binding.cardMyLevel.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        binding.cardContinue.setOnClickListener {
            // Navigate to last exercise
        }

        binding.btnContinue.setOnClickListener {
            // Start training
            startActivity(Intent(requireContext(), TrainingActivity::class.java))
        }

        binding.cardBodyScan.setOnClickListener {
            startActivity(Intent(requireContext(), PreScreeningActivity::class.java))
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
            val ctx = requireContext()
            val authHeader = AuthManager.getAuthHeader(ctx)

            // Try loading from ActivePlan API first (server-driven)
            if (authHeader != null) {
                try {
                    val loaded = loadActivePlanFromServer(authHeader)
                    if (loaded) return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "ActivePlan API unavailable, falling back to local", e)
                }
            }

            // Fallback: load from local ProgramRepository (offline-first)
            loadActiveProgramFromLocal()
        }
    }

    /**
     * Load active program and today's plan from the ActivePlan API.
     * Returns true if successfully loaded and bound to UI.
     */
    private suspend fun loadActivePlanFromServer(authHeader: String): Boolean {
        val planResponse = withContext(Dispatchers.IO) {
            ApiClient.mobileSyncApi.getActivePlan(authHeader)
        }

        if (!planResponse.isSuccessful || planResponse.body()?.success != true) return false

        val planData = planResponse.body()?.data ?: return false
        val activeProgram = planData.programs.firstOrNull { it.status == "active" }
            ?: return false
        val programInfo = activeProgram.program ?: return false

        val language = java.util.Locale.getDefault().language
        val programName = programInfo.name[language] ?: programInfo.name["en"] ?: ""

        binding.cardActiveProgram.visibility = View.VISIBLE
        binding.tvActiveProgramName.text = programName
        binding.tvActiveProgramStats.text = getString(
            R.string.program_stats_format,
            programInfo.durationWeeks,
            activeProgram.progress.completedDays
        )
        binding.btnViewProgram.setOnClickListener {
            val intent = Intent(requireContext(), ProgramDetailActivity::class.java).apply {
                putExtra(ProgramDetailActivity.EXTRA_PROGRAM_SLUG, programInfo.slug)
            }
            startActivity(intent)
        }
        binding.cardActiveProgram.setOnLongClickListener {
            startActivity(PlanOverviewActivity.createIntent(requireContext()))
            true
        }

        // Load today's plan from server
        try {
            val todayResponse = withContext(Dispatchers.IO) {
                ApiClient.mobileSyncApi.getTodayPlan(authHeader)
            }
            if (todayResponse.isSuccessful && todayResponse.body()?.success == true) {
                val today = todayResponse.body()?.data
                val currentProgram = today?.currentProgram
                if (currentProgram != null && !currentProgram.isRestDay && currentProgram.sessions.isNotEmpty()) {
                    val firstSession = currentProgram.sessions.firstOrNull { !it.isCompleted }
                    if (firstSession != null) {
                        val sessionName = firstSession.name[language] ?: firstSession.name["en"] ?: ""
                        binding.cardTodayPlan.visibility = View.VISIBLE
                        binding.tvTodayPlanTitle.text = getString(
                            R.string.today_plan_title_format,
                            currentProgram.weekNumber,
                            currentProgram.dayNumber
                        )
                        binding.tvTodayPlanSubtitle.text = getString(
                            R.string.today_plan_session_format,
                            sessionName,
                            firstSession.itemCount
                        )
                        binding.btnStartTodayPlan.setOnClickListener {
                            val intent = Intent(requireContext(), ProgramSessionActivity::class.java).apply {
                                putExtra(ProgramSessionActivity.EXTRA_PROGRAM_SLUG, programInfo.slug)
                                putExtra(ProgramSessionActivity.EXTRA_PROGRAM_ID, programInfo.id)
                                putExtra(ProgramSessionActivity.EXTRA_WEEK_NUMBER, currentProgram.weekNumber)
                                putExtra(ProgramSessionActivity.EXTRA_DAY_NUMBER, currentProgram.dayNumber)
                                putExtra(ProgramSessionActivity.EXTRA_TARGET_SESSION_ID, firstSession.id)
                            }
                            startActivity(intent)
                        }
                    } else {
                        binding.cardTodayPlan.visibility = View.GONE
                    }
                } else {
                    binding.cardTodayPlan.visibility = View.GONE
                }
            } else {
                binding.cardTodayPlan.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load today plan from server", e)
            binding.cardTodayPlan.visibility = View.GONE
        }

        return true
    }

    /**
     * Fallback: load active program from local ProgramRepository (offline-first).
     */
    private suspend fun loadActiveProgramFromLocal() {
        val programRepo = ProgramRepository.getInstance(requireContext())
        withContext(Dispatchers.IO) {
            programRepo.initialize()
        }

        val activeProgram = programRepo.getActiveProgram()
        val fallbackProgram = programRepo.getAllPrograms().firstOrNull()
        val program = activeProgram ?: fallbackProgram

        if (program == null) {
            binding.cardActiveProgram.visibility = View.GONE
            return
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
        binding.cardActiveProgram.setOnLongClickListener {
            startActivity(PlanOverviewActivity.createIntent(requireContext()))
            true
        }

        bindTodayPlanFromLocal(program)
    }

    private fun bindTodayPlanFromLocal(program: ProgramConfig) {
        val reportStore = ProgramSessionReportStore(requireContext())
        val customizationStore = DayCustomizationStore(requireContext())
        val language = java.util.Locale.getDefault().language

        val orderedSessions = program.weeks.sortedBy { it.weekNumber }.flatMap { week ->
            week.days.sortedBy { it.dayNumber }.flatMap { day ->
                if (day.isRestDay) {
                    emptyList()
                } else {
                    val effectiveSessions = customizationStore.getEffectiveSessions(
                        programId = program.id,
                        weekNumber = week.weekNumber,
                        dayNumber = day.dayNumber,
                        originalSessions = day.sessions
                    )
                    effectiveSessions.sortedBy { it.sortOrder }.map { cs ->
                        Triple(week, day, com.trainingvalidator.poc.training.models.ProgramSession(
                            id = cs.id, name = cs.name, sortOrder = cs.sortOrder, items = cs.items
                        ))
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
