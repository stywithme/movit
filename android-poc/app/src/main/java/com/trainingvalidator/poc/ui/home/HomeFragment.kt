package com.trainingvalidator.poc.ui.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.assessment.ui.PreScreeningActivity
import com.trainingvalidator.poc.databinding.FragmentHomeBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.HomeAlertData
import com.trainingvalidator.poc.network.HomeData
import com.trainingvalidator.poc.network.ProgressionEntryData
import com.trainingvalidator.poc.network.ProgressionMarkSeenRequest
import com.trainingvalidator.poc.network.RecentSessionData
import com.trainingvalidator.poc.network.TrainModeData
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.HomeRepository
import com.trainingvalidator.poc.ui.level.LevelProfileActivity
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import com.trainingvalidator.poc.ui.programs.PlanOverviewActivity
import com.trainingvalidator.poc.ui.programs.ProgramDetailActivity
import com.trainingvalidator.poc.ui.programs.ProgramSessionActivity
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * HomeFragment — Command Center
 *
 * Offline-first: cached data renders immediately, then syncs from server.
 * The entire UI is driven by `trainMode.status` from the enhanced home API.
 *
 * States handled:
 *   no_assessment   → Prominent Body Scan CTA, Explore available
 *   no_plan         → Assessment done, generating plan message
 *   active          → Today's session card with START button
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
    private var initialLoadDone = false
    private var skeletonAnimator: ObjectAnimator? = null

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
        if (_binding == null) return

        setupGreeting()

        if (!initialLoadDone) {
            initialLoadDone = true
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                homeRepository.syncFromServer()?.let {
                    if (_binding != null) renderData(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Resume home sync failed", e)
                homeRepository.getCachedData()?.let { renderData(it) }
            } finally {
                if (_binding != null) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    // ── Initialization ────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.primary)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(requireContext(), R.color.surface)
        )
        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    homeRepository.syncFromServer()?.let {
                        if (_binding != null) renderData(it)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Pull-to-refresh sync failed", e)
                } finally {
                    if (_binding != null) binding.swipeRefresh.isRefreshing = false
                }
            }
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
        binding.tvUserName.text = name.split(" ").firstOrNull() ?: name
    }

    private fun loadData() {
        val cached = homeRepository.getCachedData()
        if (cached != null) {
            renderData(cached)
        } else {
            showLoadingSkeleton()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                homeRepository.syncFromServer()?.let {
                    if (_binding != null) {
                        hideLoadingSkeleton()
                        renderData(it)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background home sync failed", e)
                if (_binding != null) hideLoadingSkeleton()
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderData(data: HomeData) {
        renderUserHeader(data)
        renderStats(data)
        renderTrainMode(data)
        renderAlerts(data)
        checkProgressionChanges()
        renderReportSummary(data)
        renderRecentSessions(data)
    }

    private fun renderUserHeader(data: HomeData) {
        val user = data.user
        val levelProfile = data.levelProfile // legacy fallback

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

        val sessions = stats?.thisWeekSessions ?: legacyStats?.weeklyWorkouts ?: 0
        val formScore = stats?.avgFormScore ?: legacyStats?.avgFormScore?.toInt() ?: 0
        val streak = stats?.streak ?: legacyStats?.streak ?: 0

        binding.tvWeeklyWorkouts.text = "$sessions"
        binding.tvFormScore.text = if (formScore > 0) "$formScore%" else "--"
        binding.tvStreak.text = "$streak"
    }

    private fun renderTrainMode(data: HomeData) {
        val trainMode = data.trainMode
        val language = requireContext().currentLanguage

        if (trainMode == null) {
            renderLegacyMode(data, language)
            return
        }

        when (trainMode.status) {
            "no_assessment"    -> renderNoAssessmentState()
            "no_plan"          -> renderNoPlanState()
            "active"           -> renderActiveState(trainMode, language)
            "rest_day"         -> renderRestDayState(trainMode)
            "program_complete" -> renderProgramCompleteState(trainMode, language)
            "reassessment_due" -> renderReassessmentDueState()
            else -> {
                Log.w(TAG, "Unknown trainMode status: ${trainMode.status}")
                hideAllTrainCards()
            }
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
            startActivity(Intent(requireContext(), PreScreeningActivity::class.java))
        }
        binding.tvSessionProgress.visibility = View.GONE
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
        hideAllTrainCards()
        val program = trainMode.activeProgram
        val session = trainMode.todaySession

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

        // Today's session card
        if (session != null) {
            val sessionName = session.name[language] ?: session.name["en"] ?: ""
            binding.cardTodayPlan.visibility = View.VISIBLE
            binding.tvTodayPlanLabel.text = getString(R.string.today_plan)
            binding.tvTodayPlanTitle.text = getString(
                R.string.today_plan_title_format,
                program?.weekNumber ?: 1,
                program?.dayNumber ?: 1
            )
            binding.tvTodayPlanSubtitle.text = if (session.estimatedMinutes != null) {
                getString(
                    R.string.today_plan_session_with_time_format,
                    sessionName,
                    session.exerciseCount,
                    session.estimatedMinutes
                )
            } else {
                getString(R.string.today_plan_session_format, sessionName, session.exerciseCount)
            }
            binding.btnStartTodayPlan.visibility = View.VISIBLE
            binding.btnStartTodayPlan.text = getString(R.string.start_session)
            binding.btnStartTodayPlan.setOnClickListener {
                navigateToSession(session.sessionId, program, trainMode)
            }

            // Show session progress if multiple sessions
            if (session.allSessionsCount > 1) {
                binding.tvSessionProgress.visibility = View.VISIBLE
                binding.tvSessionProgress.text = getString(
                    R.string.session_progress_format,
                    session.completedSessionsCount,
                    session.allSessionsCount
                )
            } else {
                binding.tvSessionProgress.visibility = View.GONE
            }
        } else {
            binding.cardTodayPlan.visibility = View.GONE
        }
    }

    private fun renderRestDayState(trainMode: TrainModeData) {
        hideAllTrainCards()
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
        binding.tvSessionProgress.visibility = View.GONE
    }

    private fun renderProgramCompleteState(trainMode: TrainModeData, language: String) {
        hideAllTrainCards()
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
            startActivity(Intent(requireContext(), PreScreeningActivity::class.java))
        }
        binding.tvSessionProgress.visibility = View.GONE
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
            startActivity(Intent(requireContext(), PreScreeningActivity::class.java))
        }
        binding.tvSessionProgress.visibility = View.GONE
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
                binding.tvTodayPlanSubtitle.text =
                    getString(R.string.today_plan_session_format, sessionName, firstSession.itemCount)
                binding.btnStartTodayPlan.visibility = View.VISIBLE
                binding.btnStartTodayPlan.setOnClickListener {
                    startActivity(
                        Intent(requireContext(), ProgramSessionActivity::class.java).apply {
                            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_SLUG, programInfo.slug)
                            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_ID, programInfo.id)
                            putExtra(ProgramSessionActivity.EXTRA_WEEK_NUMBER, currentProgram.weekNumber)
                            putExtra(ProgramSessionActivity.EXTRA_DAY_NUMBER, currentProgram.dayNumber)
                            putExtra(ProgramSessionActivity.EXTRA_TARGET_SESSION_ID, firstSession.id)
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

    // ── Report Summary ──────────────────────────────────────────────────────

    private fun renderReportSummary(data: HomeData) {
        val stats = data.stats
        val legacyStats = data.userStats
        val sessions = stats?.thisWeekSessions ?: legacyStats?.weeklyWorkouts ?: 0
        val formScore = stats?.avgFormScore ?: legacyStats?.avgFormScore?.toInt() ?: 0
        val streak = stats?.streak ?: legacyStats?.streak ?: 0

        binding.tvHomeReportSummary.text = if (formScore > 0) {
            getString(R.string.home_report_summary_format, sessions, "$formScore%", streak)
        } else {
            getString(R.string.home_report_summary_empty, sessions)
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
                startActivity(Intent(requireContext(), PreScreeningActivity::class.java))
            "progression_applied" ->
                (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_train)
            else -> Unit
        }
    }

    // ── Progression Card ──────────────────────────────────────────────────────

    private fun checkProgressionChanges() {
        val ctx = context ?: return
        val authHeader = AuthManager.getAuthHeader(ctx) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getRecentProgression(authHeader)
                }
                if (!isAdded || _binding == null) return@launch

                val changes = response.body()?.data
                if (changes.isNullOrEmpty()) {
                    binding.cardProgression.visibility = View.GONE
                    return@launch
                }

                renderProgressionCard(changes)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check progression", e)
                if (_binding != null) binding.cardProgression.visibility = View.GONE
            }
        }
    }

    private fun renderProgressionCard(changes: List<ProgressionEntryData>) {
        if (_binding == null) return
        val ctx = requireContext()
        val language = ctx.currentLanguage
        val isArabic = language == "ar"

        binding.cardProgression.visibility = View.VISIBLE

        val hasIncrease = changes.any { it.newValue > it.previousValue }
        binding.tvProgressionHomeTitle.text = if (hasIncrease)
            getString(R.string.progression_level_up) else getString(R.string.progression_home_title)

        val container = binding.llProgressionHomeChanges
        container.removeAllViews()

        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        for (change in changes.take(3)) {
            val isInc = change.newValue > change.previousValue
            val accentColor = if (isInc) ContextCompat.getColor(ctx, R.color.success)
                else ContextCompat.getColor(ctx, R.color.warning)

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }

            row.addView(TextView(ctx).apply {
                text = if (isInc) "\u2191" else "\u2193"
                setTextColor(accentColor)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            })

            val fieldLabel = when (change.field) {
                "weightKg" -> getString(R.string.progression_field_weight)
                "targetReps" -> getString(R.string.progression_field_reps)
                "sets" -> getString(R.string.progression_field_sets)
                else -> change.field
            }
            val from = formatFieldVal(change.field, change.previousValue)
            val to = formatFieldVal(change.field, change.newValue)
            row.addView(TextView(ctx).apply {
                text = "$fieldLabel: $from → $to"
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            container.addView(row)
        }

        binding.btnProgressionHomeView.setOnClickListener {
            startActivity(PlanOverviewActivity.createIntent(ctx))

            val authHeader = AuthManager.getAuthHeader(ctx) ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        ApiClient.mobileSyncApi.markProgressionSeen(
                            authHeader,
                            ProgressionMarkSeenRequest(changes.map { it.id })
                        )
                    }
                }
                if (_binding != null) binding.cardProgression.visibility = View.GONE
            }
        }
    }

    private fun formatFieldVal(field: String, value: Double): String {
        return when (field) {
            "weightKg" -> "${String.format("%.1f", value)}kg"
            "targetReps", "sets" -> "${value.toInt()}"
            else -> String.format("%.1f", value)
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateToSession(
        sessionId: String,
        program: com.trainingvalidator.poc.network.TrainActiveProgramData?,
        trainMode: TrainModeData
    ) {
        val weekNumber = trainMode.activeProgram?.weekNumber ?: 1
        val dayNumber = trainMode.activeProgram?.dayNumber ?: 1
        val programId = trainMode.activeProgram?.id

        val cachedData = homeRepository.getCachedData()
        val legacyProgram = cachedData?.activePlan?.programs?.firstOrNull { it.status == "active" }?.program
        val slug = legacyProgram?.slug
        val effectiveId = programId ?: legacyProgram?.id

        if (effectiveId != null) {
            startActivity(
                Intent(requireContext(), ProgramSessionActivity::class.java).apply {
                    putExtra(ProgramSessionActivity.EXTRA_PROGRAM_SLUG, slug ?: "")
                    putExtra(ProgramSessionActivity.EXTRA_PROGRAM_ID, effectiveId)
                    putExtra(ProgramSessionActivity.EXTRA_WEEK_NUMBER, weekNumber)
                    putExtra(ProgramSessionActivity.EXTRA_DAY_NUMBER, dayNumber)
                    putExtra(ProgramSessionActivity.EXTRA_TARGET_SESSION_ID, sessionId)
                }
            )
        } else {
            startActivity(PlanOverviewActivity.createIntent(requireContext()))
        }
    }

    // ── Static Listeners ──────────────────────────────────────────────────────

    private fun setupStaticListeners() {
        binding.cardBodyScan.setOnClickListener {
            startActivity(Intent(requireContext(), PreScreeningActivity::class.java))
        }

        binding.cardStartCamera.setOnClickListener {
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_explore)
        }

        binding.cardAnalyzeVideo.setOnClickListener {
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_explore)
        }

        binding.ivAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), com.trainingvalidator.poc.ui.profile.ProfileActivity::class.java))
        }

        binding.btnOpenReports.setOnClickListener {
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_reports)
        }
    }

    // ── Recent Sessions ─────────────────────────────────────────────────────

    private fun renderRecentSessions(data: HomeData) {
        val sessions = data.recentSessions
        if (sessions.isNullOrEmpty()) {
            binding.layoutRecentSessions.visibility = View.GONE
            return
        }

        binding.layoutRecentSessions.visibility = View.VISIBLE
        binding.recentSessionsList.removeAllViews()

        val language = requireContext().currentLanguage
        val limit = sessions.take(5)

        for (session in limit) {
            binding.recentSessionsList.addView(createRecentSessionRow(session, language))
        }
    }

    private fun createRecentSessionRow(session: RecentSessionData, language: String): View {
        val ctx = requireContext()
        val name = session.exerciseName[language] ?: session.exerciseName["en"] ?: session.exerciseId
        val ago = timeAgo(session.date)

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(10), 0, dpToPx(10))
        }

        val infoColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(ctx).apply {
            text = name
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }

        val tvMeta = TextView(ctx).apply {
            text = getString(R.string.recent_session_item, ago, session.context)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
            textSize = 12f
        }

        infoColumn.addView(tvTitle)
        infoColumn.addView(tvMeta)

        val tvScore = TextView(ctx).apply {
            text = getString(R.string.recent_session_score, session.formScore)
            setTextColor(
                when {
                    session.formScore >= 80 -> ContextCompat.getColor(ctx, R.color.success)
                    session.formScore >= 60 -> ContextCompat.getColor(ctx, R.color.warning)
                    else -> ContextCompat.getColor(ctx, R.color.error)
                }
            )
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }

        row.addView(infoColumn)
        row.addView(tvScore)

        return row
    }

    // ── Loading Skeleton ─────────────────────────────────────────────────────

    private fun showLoadingSkeleton() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutContent.visibility = View.GONE

        skeletonAnimator = ObjectAnimator.ofFloat(binding.layoutLoading, "alpha", 1f, 0.3f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun hideLoadingSkeleton() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        binding.layoutLoading.visibility = View.GONE
        binding.layoutContent.visibility = View.VISIBLE
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun timeAgo(isoDate: String): String {
        return try {
            val then = Instant.parse(isoDate)
            val now = Instant.now()
            val minutes = ChronoUnit.MINUTES.between(then, now)
            when {
                minutes < 1   -> getString(R.string.time_just_now)
                minutes < 60  -> getString(R.string.time_minutes_ago, minutes.toInt())
                minutes < 1440 -> getString(R.string.time_hours_ago, (minutes / 60).toInt())
                minutes < 10080 -> getString(R.string.time_days_ago, (minutes / 1440).toInt())
                minutes < 43800 -> getString(R.string.time_weeks_ago, (minutes / 10080).toInt())
                minutes < 525600 -> getString(R.string.time_months_ago, (minutes / 43800).toInt())
                else -> getString(R.string.time_years_ago, (minutes / 525600).toInt())
            }
        } catch (_: Exception) {
            isoDate
        }
    }

    private fun hideAllTrainCards() {
        binding.cardActiveProgram.visibility = View.GONE
        binding.cardTodayPlan.visibility = View.GONE
        binding.tvSessionProgress.visibility = View.GONE
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
