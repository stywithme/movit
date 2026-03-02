package com.trainingvalidator.poc.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.assessment.ui.PreScreeningActivity
import com.trainingvalidator.poc.databinding.FragmentHomeBinding
import com.trainingvalidator.poc.network.HomeData
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.HomeRepository
import com.trainingvalidator.poc.ui.level.LevelProfileActivity
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import com.trainingvalidator.poc.ui.programs.PlanOverviewActivity
import com.trainingvalidator.poc.ui.programs.ProgramDetailActivity
import com.trainingvalidator.poc.ui.programs.ProgramSessionActivity
import com.trainingvalidator.poc.ui.train.TrainingActivity
import com.trainingvalidator.poc.ui.utils.currentLanguage
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * HomeFragment - Main dashboard with stats and quick actions
 * Offline-first: cached content renders immediately, then background sync.
 */
class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var homeRepository: HomeRepository

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
        
        homeRepository = HomeRepository.getInstance(requireContext())
        
        setupGreeting()
        loadUserName()
        setupListeners()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            setupGreeting()
            homeRepository.getCachedData()?.let { renderData(it) }
        }
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

    private fun loadUserName() {
        val name = AuthManager.getUserName(requireContext(), "Athlete")
        val firstName = name.split(" ").firstOrNull() ?: name
        binding.tvUserName.text = firstName
    }
    
    private fun loadData() {
        // Render cached data immediately
        homeRepository.getCachedData()?.let { renderData(it) }

        // Sync from server in the background
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                homeRepository.syncFromServer()?.let {
                    if (_binding != null) renderData(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background home sync failed", e)
            }
        }
    }
    
    private fun renderData(data: HomeData) {
        // 1. Render User Stats
        val stats = data.userStats
        if (stats != null) {
            binding.tvWeeklyWorkouts.text = "${stats.weeklyWorkouts}"
            val formText = if (stats.avgFormScore > 0) "${stats.avgFormScore.toInt()}%" else "--"
            binding.tvFormScore.text = formText
            binding.tvStreak.text = if (stats.streak > 0) "${stats.streak}\uD83D\uDD25" else "0"
            binding.tvHomeReportSummary.text = getString(
                R.string.home_report_summary_format,
                stats.weeklyWorkouts,
                formText,
                stats.streak
            )
        } else {
            val totalWorkouts = AuthManager.getTotalWorkouts(requireContext())
            binding.tvWeeklyWorkouts.text = "$totalWorkouts"
            binding.tvFormScore.text = "--"
            binding.tvStreak.text = "0"
            binding.tvHomeReportSummary.text = getString(R.string.home_report_summary_empty, totalWorkouts)
        }

        // 2. Render Level Profile
        val profile = data.levelProfile
        if (profile != null) {
            val levelName = profile.levelInfo.name.en
            val levelNumber = profile.overallLevel
            binding.cardMyLevel.visibility = View.VISIBLE
            binding.tvLevelName.text = getString(R.string.home_level_name_format, levelNumber, levelName)
            binding.tvLevelScore.text = getString(R.string.home_level_score_format, profile.bodyScore.toInt())
            binding.cardMyLevel.setOnClickListener {
                startActivity(LevelProfileActivity.createIntent(requireContext()))
            }
        } else {
            binding.cardMyLevel.visibility = View.GONE
        }

        // 3. Render Active Program
        val activeProgram = data.activePlan?.programs?.firstOrNull { it.status == "active" }
        val programInfo = activeProgram?.program
        val language = requireContext().currentLanguage

        if (programInfo != null) {
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
        } else {
            binding.cardActiveProgram.visibility = View.GONE
        }

        // 4. Render Today's Plan
        val currentProgram = data.todayPlan?.currentProgram
        if (currentProgram != null && !currentProgram.isRestDay && currentProgram.sessions.isNotEmpty()) {
            val firstSession = currentProgram.sessions.firstOrNull { !it.isCompleted }
            if (firstSession != null && programInfo != null) {
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
            // Navigate to train
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_train)
        }

        binding.cardAnalyzeVideo.setOnClickListener {
            // Open video picker
        }

        binding.ivAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), com.trainingvalidator.poc.ui.profile.ProfileActivity::class.java))
        }

        binding.btnOpenReports.setOnClickListener {
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_reports)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
