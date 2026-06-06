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
import com.trainingvalidator.poc.assessment.models.AssessmentType
import com.trainingvalidator.poc.assessment.ui.PreScreeningActivity
import com.trainingvalidator.poc.databinding.FragmentHomeBinding
import com.trainingvalidator.poc.network.HomeAlertData
import com.trainingvalidator.poc.network.HomeData
import com.trainingvalidator.poc.network.TrainModeData
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.HomeRepository
import com.trainingvalidator.poc.ui.level.LevelProfileActivity
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import com.trainingvalidator.poc.ui.programs.PlanOverviewActivity
import com.trainingvalidator.poc.ui.programs.ProgramDetailActivity
import com.trainingvalidator.poc.ui.programs.ProgramWorkoutActivity
import com.trainingvalidator.poc.ui.utils.bindUserAvatar
import com.trainingvalidator.poc.ui.utils.currentLanguage
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * HomeFragment — Command Center
 *
 * Offline-first: cached data renders immediately, then syncs from server.
 * The entire UI is driven by `trainMode.status` from the enhanced home API.
 *
 * States handled:
 *   no_assessment   → Prominent Body Scan CTA, Explore available
 *   no_plan         → Assessment done, generating plan message
 *   active          → Today's workout card with START button
 *   rest_day        → Rest day card with recovery tip
 *   program_complete→ Level Up Challenge — reassessment prompt
 *   reassessment_due→ Reassessment banner
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

        setupSwipeRefresh()
        setupGreeting()
        loadUserName()
        setupStaticListeners()
        loadData()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            refreshContent()
        }
    }

    private fun refreshContent() {
        setupGreeting()
        loadUserName()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                homeRepository.syncFromServer()?.let {
                    if (_binding != null) renderData(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pull-to-refresh failed", e)
                homeRepository.getCachedData()?.let { renderData(it) }
            } finally {
                if (_binding != null) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            setupGreeting()
            // Always sync from server on resume to reflect latest state
            // (e.g. after Body Scan, after a workout, after plan enrollment)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    homeRepository.syncFromServer()?.let {
                        if (_binding != null) renderData(it)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Resume home sync failed", e)
                    homeRepository.getCachedData()?.let { renderData(it) }
                }
            }
        }
    }

    // ── Initialization ────────────────────────────────────────────────────────

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
        binding.tvUserName.text = name.split(" ").firstOrNull() ?: name
        binding.ivAvatar.bindUserAvatar(AuthManager.getAvatarUrl(requireContext()))
    }

    private fun loadData() {
        homeRepository.getCachedData()?.let { renderData(it) }

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

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderData(data: HomeData) {
        renderUserHeader(data)
        renderStats(data)
        renderTrainMode(data)
        renderAlerts(data)
    }

    private fun renderUserHeader(data: HomeData) {
        val user = data.user
        val levelProfile = data.levelProfile // legacy fallback

        val avatarUrl = user?.avatarUrl?.takeIf { !it.isNullOrBlank() }
            ?: AuthManager.getAvatarUrl(requireContext())
        binding.ivAvatar.bindUserAvatar(avatarUrl)

        if (user != null && !user.avatarUrl.isNullOrBlank()) {
            AuthManager.updateAvatarUrl(requireContext(), user.avatarUrl)
        }

        if (user != null) {
            if (user.level != null && user.bodyScore != null) {
                binding.cardMyLevel.visibility = View.VISIBLE
                binding.tvLevelName.text = getString(
                    R.string.home_level_name_format,
                    user.level,
                    user.levelCode?.replaceFirstChar { it.uppercase() } ?: ""
                )
                binding.tvLevelScore.text = getString(
                    R.string.home_level_score_format,
                    user.bodyScore.toInt()
                )
                // Level progress bar (if available)
                val progress = user.levelProgress ?: 0
                binding.progressLevelBar.visibility = View.VISIBLE
                binding.progressLevelBar.progress = progress

                binding.cardMyLevel.setOnClickListener {
                    startActivity(LevelProfileActivity.createIntent(requireContext()))
                }
            } else {
                binding.cardMyLevel.visibility = View.GONE
                binding.progressLevelBar.visibility = View.GONE
            }
        } else if (levelProfile != null) {
            // Legacy fallback
            val levelName = levelProfile.levelInfo.name.en
            val levelNumber = levelProfile.overallLevel
            binding.cardMyLevel.visibility = View.VISIBLE
            binding.progressLevelBar.visibility = View.GONE
            binding.tvLevelName.text = getString(R.string.home_level_name_format, levelNumber, levelName)
            binding.tvLevelScore.text = getString(
                R.string.home_level_score_format,
                levelProfile.bodyScore.toInt()
            )
            binding.cardMyLevel.setOnClickListener {
                startActivity(LevelProfileActivity.createIntent(requireContext()))
            }
        } else {
            binding.cardMyLevel.visibility = View.GONE
            binding.progressLevelBar.visibility = View.GONE
        }
    }

    private fun renderStats(data: HomeData) {
        val stats = data.stats
        val legacyStats = data.userStats

        val weeklyWorkouts = stats?.thisWeekExecutions ?: legacyStats?.weeklyPlannedWorkouts ?: 0
        val formScore = stats?.avgFormScore ?: legacyStats?.avgFormScore?.toInt() ?: 0
        val streak = stats?.streak ?: legacyStats?.streak ?: 0

        binding.tvWeeklyWorkouts.text = "$weeklyWorkouts"
        binding.tvFormScore.text = if (formScore > 0) "$formScore%" else "--"
        binding.tvStreak.text = if (streak > 0) "$streak\uD83D\uDD25" else "0"
    }

    private fun renderTrainMode(data: HomeData) {
        val trainMode = data.trainMode
        val language = requireContext().currentLanguage

        if (trainMode == null) {
            renderLegacyMode(data, language)
            return
        }

        when (trainMode.status) {
            "no_assessment" -> renderNoAssessmentState()
            "no_plan"       -> renderNoPlanState()
            "active"        -> renderActiveState(trainMode, language)
            "rest_day"      -> renderRestDayState(trainMode)
            "program_complete" -> renderProgramCompleteState(trainMode, language)
            "reassessment_due" -> renderReassessmentDueState()
            else            -> renderNoAssessmentState()
        }
    }

    // ── TrainMode State Renderers ─────────────────────────────────────────────

    private fun renderNoAssessmentState() {
        hideAllTrainCards()
        // Show a clear CTA card directing the new user to do their first Body Scan
        binding.cardTodayPlan.visibility = View.VISIBLE
        binding.tvTodayPlanLabel.text = getString(R.string.home_your_plan_label)
        binding.tvTodayPlanTitle.text = getString(R.string.home_no_assessment_title)
        binding.tvTodayPlanSubtitle.text = getString(R.string.home_no_assessment_subtitle)
        binding.btnStartTodayPlan.visibility = View.VISIBLE
        binding.btnStartTodayPlan.text = getString(R.string.start_body_scan)
        binding.btnStartTodayPlan.setOnClickListener {
            startActivity(PreScreeningActivity.createIntent(requireContext()))
        }
        binding.tvWorkoutProgress.visibility = View.GONE
    }

    private fun renderNoPlanState() {
        hideAllTrainCards()
        binding.cardTodayPlan.visibility = View.VISIBLE
        binding.tvTodayPlanLabel.text = getString(R.string.home_your_plan_label)
        binding.tvTodayPlanTitle.text = getString(R.string.home_plan_generating_title)
        binding.tvTodayPlanSubtitle.text = getString(R.string.home_plan_generating_subtitle)
        binding.btnStartTodayPlan.visibility = View.GONE
    }

    private fun renderActiveState(trainMode: TrainModeData, language: String) {
        val program = trainMode.activeProgram
        val todayWorkout = trainMode.todayWorkout

        // Active program card
        if (program != null) {
            val programName = program.name[language] ?: program.name["en"] ?: ""
            binding.cardActiveProgram.visibility = View.VISIBLE
            binding.btnViewProgram.visibility = View.VISIBLE
            binding.tvActiveProgramName.text = programName
            binding.tvActiveProgramStats.text = getString(
                R.string.program_week_progress_format,
                program.weekNumber,
                program.totalWeeks,
                program.weekProgress.completed,
                program.weekProgress.total
            )
            binding.btnViewProgram.setOnClickListener {
                startActivity(PlanOverviewActivity.createIntent(requireContext()))
            }
        } else {
            binding.cardActiveProgram.visibility = View.GONE
        }

        if (todayWorkout != null) {
            val workoutName = todayWorkout.name[language] ?: todayWorkout.name["en"] ?: ""
            binding.cardTodayPlan.visibility = View.VISIBLE
            binding.tvTodayPlanLabel.text = getString(R.string.today_plan)
            binding.tvTodayPlanTitle.text = getString(
                R.string.today_plan_title_format,
                program?.weekNumber ?: 1,
                program?.dayNumber ?: 1
            )
            binding.tvTodayPlanSubtitle.text = if (todayWorkout.estimatedMinutes != null) {
                getString(
                    R.string.today_plan_workout_with_time_format,
                    workoutName,
                    todayWorkout.exerciseCount,
                    todayWorkout.estimatedMinutes
                )
            } else {
                getString(R.string.today_plan_workout_format, workoutName, todayWorkout.exerciseCount)
            }
            binding.btnStartTodayPlan.visibility = View.VISIBLE
            binding.btnStartTodayPlan.text = getString(R.string.start_workout)
            binding.btnStartTodayPlan.setOnClickListener {
                navigateToPlannedWorkout(todayWorkout.plannedWorkoutId, program, trainMode)
            }

            if (todayWorkout.allWorkoutsCount > 1) {
                binding.tvWorkoutProgress.visibility = View.VISIBLE
                binding.tvWorkoutProgress.text = getString(
                    R.string.workout_progress_format,
                    todayWorkout.completedWorkoutsCount,
                    todayWorkout.allWorkoutsCount
                )
            } else {
                binding.tvWorkoutProgress.visibility = View.GONE
            }
            bindCatchUpUi(trainMode, program)
        } else {
            binding.cardTodayPlan.visibility = View.GONE
            clearCatchUpUi()
        }
    }

    private fun renderRestDayState(trainMode: TrainModeData) {
        val program = trainMode.activeProgram
        val language = requireContext().currentLanguage

        if (program != null) {
            val programName = program.name[language] ?: program.name["en"] ?: ""
            binding.cardActiveProgram.visibility = View.VISIBLE
            binding.btnViewProgram.visibility = View.VISIBLE
            binding.tvActiveProgramName.text = programName
            binding.tvActiveProgramStats.text = getString(
                R.string.program_week_progress_format,
                program.weekNumber,
                program.totalWeeks,
                program.weekProgress.completed,
                program.weekProgress.total
            )
            binding.btnViewProgram.setOnClickListener {
                startActivity(PlanOverviewActivity.createIntent(requireContext()))
            }
        }

        binding.cardTodayPlan.visibility = View.VISIBLE
        binding.tvTodayPlanLabel.text = getString(R.string.today_plan)
        binding.tvTodayPlanTitle.text = getString(R.string.rest_day_title)
        binding.tvTodayPlanSubtitle.text = getString(
            if (trainMode.dayType == "active_recovery")
                R.string.active_recovery_subtitle
            else
                R.string.rest_day_subtitle
        )
        binding.btnStartTodayPlan.visibility = View.GONE
        binding.tvWorkoutProgress.visibility = View.GONE
        bindCatchUpUi(trainMode, program)
    }

    private fun renderProgramCompleteState(trainMode: TrainModeData, language: String) {
        val program = trainMode.activeProgram

        if (program != null) {
            val programName = program.name[language] ?: program.name["en"] ?: ""
            binding.cardActiveProgram.visibility = View.VISIBLE
            binding.tvActiveProgramName.text = programName
            binding.tvActiveProgramStats.text = getString(R.string.program_complete_label)
            binding.btnViewProgram.visibility = View.GONE
        }

        binding.cardTodayPlan.visibility = View.VISIBLE
        binding.tvTodayPlanLabel.text = getString(R.string.home_your_plan_label)
        binding.tvTodayPlanTitle.text = getString(R.string.program_complete_cta_title)
        binding.tvTodayPlanSubtitle.text = getString(R.string.program_complete_cta_subtitle)
        binding.btnStartTodayPlan.visibility = View.VISIBLE
        binding.btnStartTodayPlan.text = getString(R.string.start_reassessment)
        binding.btnStartTodayPlan.setOnClickListener {
            startActivity(PreScreeningActivity.createIntent(requireContext(), AssessmentType.PROGRESSION))
        }
        binding.tvWorkoutProgress.visibility = View.GONE
    }

    private fun renderReassessmentDueState() {
        hideAllTrainCards()
        binding.cardTodayPlan.visibility = View.VISIBLE
        binding.tvTodayPlanLabel.text = getString(R.string.home_your_plan_label)
        binding.tvTodayPlanTitle.text = getString(R.string.reassessment_due_title)
        binding.tvTodayPlanSubtitle.text = getString(R.string.reassessment_due_subtitle)
        binding.btnStartTodayPlan.visibility = View.VISIBLE
        binding.btnStartTodayPlan.text = getString(R.string.start_reassessment)
        binding.btnStartTodayPlan.setOnClickListener {
            startActivity(PreScreeningActivity.createIntent(requireContext(), AssessmentType.PROGRESSION))
        }
        binding.tvWorkoutProgress.visibility = View.GONE
    }

    // ── Legacy mode (before API upgrade) ─────────────────────────────────────

    private fun renderLegacyMode(data: HomeData, language: String) {
        val activeProgram = data.activePlan?.programs?.firstOrNull { it.status == "active" }
        val programInfo = activeProgram?.program

        if (programInfo != null) {
            binding.cardActiveProgram.visibility = View.VISIBLE
            binding.tvActiveProgramName.text =
                programInfo.name[language] ?: programInfo.name["en"] ?: ""
            binding.tvActiveProgramStats.text = getString(
                R.string.program_stats_format,
                programInfo.durationWeeks,
                activeProgram.progress.completedDays
            )
            binding.btnViewProgram.setOnClickListener {
                startActivity(
                    Intent(requireContext(), ProgramDetailActivity::class.java).apply {
                        putExtra(ProgramDetailActivity.EXTRA_PROGRAM_SLUG, programInfo.slug)
                    }
                )
            }
        } else {
            binding.cardActiveProgram.visibility = View.GONE
        }

        val currentProgram = data.todayPlan?.currentProgram
        if (currentProgram != null && !currentProgram.isRestDay && currentProgram.plannedWorkouts.isNotEmpty()) {
            val firstPlannedWorkout = currentProgram.plannedWorkouts.firstOrNull { !it.isCompleted }
            if (firstPlannedWorkout != null && programInfo != null) {
                val workoutName = firstPlannedWorkout.name[language] ?: firstPlannedWorkout.name["en"] ?: ""
                binding.cardTodayPlan.visibility = View.VISIBLE
                binding.tvTodayPlanTitle.text = getString(
                    R.string.today_plan_title_format,
                    currentProgram.weekNumber,
                    currentProgram.dayNumber
                )
                binding.tvTodayPlanSubtitle.text =
                    getString(R.string.today_plan_workout_format, workoutName, firstPlannedWorkout.itemCount)
                binding.btnStartTodayPlan.visibility = View.VISIBLE
                binding.btnStartTodayPlan.setOnClickListener {
                    startActivity(
                        Intent(requireContext(), ProgramWorkoutActivity::class.java).apply {
                            putExtra(ProgramWorkoutActivity.EXTRA_PROGRAM_SLUG, programInfo.slug)
                            putExtra(ProgramWorkoutActivity.EXTRA_PROGRAM_ID, programInfo.id)
                            putExtra(ProgramWorkoutActivity.EXTRA_WEEK_NUMBER, currentProgram.weekNumber)
                            putExtra(ProgramWorkoutActivity.EXTRA_DAY_NUMBER, currentProgram.dayNumber)
                            putExtra(ProgramWorkoutActivity.EXTRA_TARGET_WORKOUT_ID, firstPlannedWorkout.id)
                        }
                    )
                }
            } else {
                binding.cardTodayPlan.visibility = View.GONE
            }
        } else {
            binding.cardTodayPlan.visibility = View.GONE
        }
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    private fun renderAlerts(data: HomeData) {
        val alerts = data.alerts
        if (alerts.isNullOrEmpty()) {
            binding.cardAlert.visibility = View.GONE
            return
        }

        val topAlert = alerts.first()
        val language = requireContext().currentLanguage
        binding.cardAlert.visibility = View.VISIBLE
        binding.tvAlertTitle.text = if (language == "ar") topAlert.titleAr else topAlert.titleEn
        binding.tvAlertMessage.text = if (language == "ar") topAlert.messageAr else topAlert.messageEn

        binding.cardAlert.setOnClickListener {
            handleAlertAction(topAlert)
        }
    }

    private fun handleAlertAction(alert: HomeAlertData) {
        when (alert.type) {
            "reassessment_due" ->
                startActivity(PreScreeningActivity.createIntent(requireContext(), AssessmentType.PROGRESSION))
            "progression_applied" ->
                (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_train)
            else -> Unit
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateToPlannedWorkout(
        workoutId: String,
        program: com.trainingvalidator.poc.network.TrainActiveProgramData?,
        trainMode: TrainModeData
    ) {
        val weekNumber = trainMode.activeProgram?.weekNumber ?: 1
        val dayNumber = trainMode.activeProgram?.dayNumber ?: 1

        // Use cached active plan for program slug/id
        val cachedData = homeRepository.getCachedData()
        val activePlanProgram = cachedData?.activePlan?.programs?.firstOrNull { it.status == "active" }
        val programInfo = activePlanProgram?.program

        if (programInfo != null) {
            startActivity(
                Intent(requireContext(), ProgramWorkoutActivity::class.java).apply {
                    putExtra(ProgramWorkoutActivity.EXTRA_PROGRAM_SLUG, programInfo.slug)
                    putExtra(ProgramWorkoutActivity.EXTRA_PROGRAM_ID, programInfo.id)
                    putExtra(ProgramWorkoutActivity.EXTRA_WEEK_NUMBER, weekNumber)
                    putExtra(ProgramWorkoutActivity.EXTRA_DAY_NUMBER, dayNumber)
                    putExtra(ProgramWorkoutActivity.EXTRA_TARGET_WORKOUT_ID, workoutId)
                }
            )
        } else {
            // Fallback to plan overview
            startActivity(PlanOverviewActivity.createIntent(requireContext()))
        }
    }

    // ── Static Listeners ──────────────────────────────────────────────────────

    private fun setupStaticListeners() {
        binding.cardBodyScan.setOnClickListener {
            startActivity(PreScreeningActivity.createIntent(requireContext()))
        }

        binding.cardStartCamera.setOnClickListener {
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_explore)
        }

        binding.ivAvatar.setOnClickListener {
            startActivity(
                Intent(requireContext(), com.trainingvalidator.poc.ui.profile.ProfileActivity::class.java)
            )
        }

        binding.btnOpenReports.setOnClickListener {
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_reports)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hideAllTrainCards() {
        binding.cardActiveProgram.visibility = View.GONE
        binding.cardTodayPlan.visibility = View.GONE
        clearCatchUpUi()
    }

    private fun clearCatchUpUi() {
        binding.tvCalendarPaused.visibility = View.GONE
        binding.tvCatchUpHint.visibility = View.GONE
        binding.btnCatchUpOpen.visibility = View.GONE
    }

    private fun bindCatchUpUi(
        trainMode: TrainModeData,
        program: com.trainingvalidator.poc.network.TrainActiveProgramData?
    ) {
        binding.tvCalendarPaused.visibility =
            if (trainMode.isPaused == true) View.VISIBLE else View.GONE
        binding.tvCalendarPaused.text = getString(R.string.plan_paused_hint)
        val catch = trainMode.catchUpSuggestion
        if (catch != null && catch.missedSlots.isNotEmpty() && program != null) {
            binding.tvCatchUpHint.visibility = View.VISIBLE
            binding.tvCatchUpHint.text = catch.message
            binding.btnCatchUpOpen.visibility = View.VISIBLE
            binding.btnCatchUpOpen.setOnClickListener {
                val slot = catch.missedSlots.first()
                navigateToProgramDay(program, slot.weekNumber, slot.dayNumber, workoutId = null)
            }
        } else {
            binding.tvCatchUpHint.visibility = View.GONE
            binding.btnCatchUpOpen.visibility = View.GONE
        }
    }

    private fun navigateToProgramDay(
        @Suppress("UNUSED_PARAMETER") program: com.trainingvalidator.poc.network.TrainActiveProgramData,
        weekNumber: Int,
        dayNumber: Int,
        workoutId: String?
    ) {
        val cachedData = homeRepository.getCachedData()
        val activePlanProgram = cachedData?.activePlan?.programs?.firstOrNull { it.status == "active" }
        val programInfo = activePlanProgram?.program
        if (programInfo != null) {
            startActivity(
                Intent(requireContext(), ProgramWorkoutActivity::class.java).apply {
                    putExtra(ProgramWorkoutActivity.EXTRA_PROGRAM_SLUG, programInfo.slug)
                    putExtra(ProgramWorkoutActivity.EXTRA_PROGRAM_ID, programInfo.id)
                    putExtra(ProgramWorkoutActivity.EXTRA_WEEK_NUMBER, weekNumber)
                    putExtra(ProgramWorkoutActivity.EXTRA_DAY_NUMBER, dayNumber)
                    if (workoutId != null) {
                        putExtra(ProgramWorkoutActivity.EXTRA_TARGET_WORKOUT_ID, workoutId)
                    }
                }
            )
        } else {
            startActivity(PlanOverviewActivity.createIntent(requireContext()))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
