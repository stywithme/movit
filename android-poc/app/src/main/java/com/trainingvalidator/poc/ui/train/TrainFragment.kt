package com.trainingvalidator.poc.ui.train

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.trainingvalidator.poc.ui.utils.bindUserAvatar
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.ui.utils.formatProgramLevel
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentTrainBinding
import com.trainingvalidator.poc.network.UserProgramExport
import com.trainingvalidator.poc.storage.DayCustomizationStore
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.ProgramDayCalculator
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramWorkoutReportStore
import com.trainingvalidator.poc.training.workout.ReportAggregator
import com.trainingvalidator.poc.training.models.PlannedWorkoutItemType
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramDay
import com.trainingvalidator.poc.training.models.ProgramWorkout
import com.trainingvalidator.poc.training.models.ProgramWeek
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import com.trainingvalidator.poc.ui.profile.ProfileActivity
import com.trainingvalidator.poc.ui.programs.ProgramDetailActivity
import com.trainingvalidator.poc.ui.programs.ProgramListActivity
import com.trainingvalidator.poc.ui.programs.ProgramWorkoutActivity
import com.trainingvalidator.poc.ui.report.WorkoutReportActivity
import com.trainingvalidator.poc.ui.programs.WeeklyReportActivity
import android.graphics.Color
import android.util.Log
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.trainingvalidator.poc.network.MetricsResponse
import com.trainingvalidator.poc.storage.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import android.widget.Toast
import com.trainingvalidator.poc.assessment.models.AssessmentType
import com.trainingvalidator.poc.assessment.ui.PreScreeningActivity
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.storage.HomeRepository

/**
 * TrainFragment — Redesigned program page.
 *
 * State 1: No active program ? show browse list of all programs
 * State 2: Active program ? show dashboard with identity card, week calendar,
 *          today planned workouts, and report summary.
 */
class TrainFragment : Fragment() {

    private var _binding: FragmentTrainBinding? = null
    private val binding get() = _binding!!

    private lateinit var programRepo: ProgramRepository
    private lateinit var exerciseRepo: ExerciseRepository
    private lateinit var reportStore: ProgramWorkoutReportStore
    private lateinit var customizationStore: DayCustomizationStore
    private lateinit var reportRepo: ReportRepository

    private var expandedPlannedWorkoutIndex = 0
    private var isFirstLoad = true
    private var cachedProgramMetrics: MetricsResponse? = null

    // -----------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwipeRefresh()
        setupHeaderAvatar()
        isFirstLoad = true
        loadPage()
    }

    private fun setupHeaderAvatar() {
        binding.ivAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
        binding.ivAvatar.bindUserAvatar(AuthManager.getAvatarUrl(requireContext()))
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            refreshContent()
        }
    }

    private fun refreshContent() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                programRepo = ProgramRepository.getInstance(requireContext())
                exerciseRepo = ExerciseRepository.getInstance(requireContext())
                reportStore = ProgramWorkoutReportStore(requireContext())
                customizationStore = DayCustomizationStore(requireContext())
                reportRepo = ReportRepository.getInstance(requireContext())

                withContext(Dispatchers.IO) {
                    programRepo.initialize()
                    exerciseRepo.initialize(autoSync = false)
                    exerciseRepo.checkForUpdates()
                    programRepo.reloadFromCache()
                }

                if (_binding != null) {
                    renderCurrentState()
                    fetchUnifiedMetrics()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pull-to-refresh failed", e)
            } finally {
                if (_binding != null) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return

        binding.ivAvatar.bindUserAvatar(AuthManager.getAvatarUrl(requireContext()))

        if (isFirstLoad) {
            isFirstLoad = false
            return
        }

        loadPage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -----------------------------------------------------------
    // Main Load
    // -----------------------------------------------------------

    companion object {
        private const val TAG = "TrainFragment"
        private const val MAX_PREVIEW_ITEMS = 4
    }

    /**
     * Offline-first load strategy:
     * 1. Load from cache immediately and show UI (fast, works offline)
     * 2. Trigger network sync in background
     * 3. When sync completes and has updates, reload cache and refresh UI
     */
    private fun loadPage() {
        viewLifecycleOwner.lifecycleScope.launch {
            programRepo = ProgramRepository.getInstance(requireContext())
            exerciseRepo = ExerciseRepository.getInstance(requireContext())
            reportStore = ProgramWorkoutReportStore(requireContext())
            customizationStore = DayCustomizationStore(requireContext())
            reportRepo = ReportRepository.getInstance(requireContext())

            withContext(Dispatchers.IO) {
                programRepo.initialize()
                exerciseRepo.initialize(autoSync = false)
            }

            if (_binding == null) return@launch
            renderCurrentState()

            syncAndRefresh()
        }
    }

    private fun renderCurrentState() {
        val activeProgram = programRepo.getActiveProgram()

        if (activeProgram != null) {
            showActiveProgramState(activeProgram)
        } else {
            showBrowseState()
        }
    }

    /**
     * Background sync: fetch latest data from backend,
     * then refresh the UI if anything changed.
     */
    private fun syncAndRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    exerciseRepo.checkForUpdates()
                }

                if (_binding == null) return@launch

                when (result) {
                    is com.trainingvalidator.poc.storage.SyncManager.SyncResult.Success -> {
                        Log.d(TAG, "Sync success: programs=${result.programsUpdated}, exercises=${result.exercisesUpdated}")
                    }
                    is com.trainingvalidator.poc.storage.SyncManager.SyncResult.NoChanges -> {
                        Log.d(TAG, "Sync: no changes")
                    }
                    is com.trainingvalidator.poc.storage.SyncManager.SyncResult.Offline -> {
                        Log.d(TAG, "Offline ? using cached data")
                    }
                    is com.trainingvalidator.poc.storage.SyncManager.SyncResult.Skipped -> {
                        Log.d(TAG, "Sync skipped ? another sync may have updated cache")
                    }
                    is com.trainingvalidator.poc.storage.SyncManager.SyncResult.NeedsFullRefresh -> {
                        Log.d(TAG, "Sync requested full refresh")
                    }
                    is com.trainingvalidator.poc.storage.SyncManager.SyncResult.Error -> {
                        Log.w(TAG, "Sync error: ${result.message}")
                    }
                }

                withContext(Dispatchers.IO) {
                    programRepo.reloadFromCache()
                }
                renderCurrentState()

                fetchUnifiedMetrics()

            } catch (e: Exception) {
                Log.w(TAG, "Background sync failed", e)
            }
        }
    }

    /**
     * Fetch program-level metrics from the unified reports endpoint.
     * Updates sparkline, program grade, and comparison data.
     */
    private fun fetchUnifiedMetrics() {
        val activeProgram = programRepo.getActiveProgram() ?: return
        val programId = activeProgram.id

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val metrics = withContext(Dispatchers.IO) {
                    reportRepo.getProgramMetrics(programId, includeChildren = true)
                }

                if (_binding == null) return@launch

                if (metrics != null && metrics.success) {
                    cachedProgramMetrics = metrics
                    renderProgramGrade(metrics)
                    renderSparkline(metrics)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch unified metrics", e)
            }
        }
    }

    // -----------------------------------------------------------
    // STATE 1: Browse Programs (No Active Program)
    // -----------------------------------------------------------

    private fun showBrowseState() {
        binding.layoutNoProgramState.visibility = View.VISIBLE
        binding.layoutActiveProgramState.visibility = View.GONE

        val allPrograms = programRepo.getAllPrograms()
        val rv = binding.rvProgramList
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = ProgramBrowseAdapter(allPrograms)
    }

    // -----------------------------------------------------------
    // STATE 2: Active Program Dashboard
    // -----------------------------------------------------------

    private fun showActiveProgramState(program: ProgramConfig) {
        binding.layoutNoProgramState.visibility = View.GONE
        binding.layoutActiveProgramState.visibility = View.VISIBLE
        binding.cardProgramComplete.visibility = View.GONE

        reportStore.migrateFromSharedPreferences(requireContext())

        val language = requireContext().currentLanguage

        val userProgram = programRepo.getActiveUserProgramExport()
        val currentRef = if (userProgram != null) {
            ProgramDayCalculator.getCurrentDay(program, userProgram)
        } else {
            null
        }

        if (currentRef == null || currentRef.isProgramComplete) {
            showProgramComplete(program)
            return
        }

        val week = currentRef.week
        val day = currentRef.day

        renderIdentityCard(program, week, day)
        renderWeekCalendar(program, week, day, userProgram)
        renderTodaySection(program, week, day, language)
        renderReportSummary(program)

        binding.btnBrowseAllPrograms.setOnClickListener { openProgramList() }
    }

    // -----------------------------------------------------
    // Section 1: Program Identity Card
    // -----------------------------------------------------

    private fun renderIdentityCard(program: ProgramConfig, week: ProgramWeek, day: ProgramDay) {
        val language = requireContext().currentLanguage
        val programName = program.name.get(language).ifBlank { program.name.en }
        binding.tvProgramName.text = programName

        binding.tvProgramDifficulty.text =
            requireContext().formatProgramLevel(program.levelMin, program.levelMax)

        binding.tvProgramPosition.text = getString(
            R.string.pg_position_format,
            week.weekNumber,
            program.durationWeeks,
            day.dayNumber
        )

        val totalPlannedWorkouts = program.weeks.sumOf { w -> w.days.sumOf { d -> d.workouts.size } }
            .coerceAtLeast(1)
        val completedPlannedWorkouts = reportStore.getAll().count { it.programId == program.id }
        val progressPercent = (completedPlannedWorkouts * 100) / totalPlannedWorkouts

        binding.progressProgram.progress = progressPercent
        binding.tvProgressPercent.text = "$progressPercent%"

        val daysTrained = getDaysTrainedCount(program)
        binding.tvDaysTrained.text = getString(R.string.pg_days_trained_format, daysTrained)

        val streak = getStreakDays(program)
        binding.tvStreak.text = getString(R.string.pg_streak_format, streak)

        binding.cardProgramIdentity.setOnClickListener { openProgramDetail(program) }
    }

    // -----------------------------------------------------
    // Section 2: Week Calendar
    // -----------------------------------------------------

    private fun renderWeekCalendar(
        program: ProgramConfig,
        week: ProgramWeek,
        currentDay: ProgramDay,
        userProgram: UserProgramExport? = null
    ) {
        binding.tvWeekTitle.text = getString(R.string.pg_week_format, week.weekNumber)
        binding.layoutWeekCalendar.removeAllViews()
        binding.layoutWeekLabels.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        val dayMap = week.days.associateBy { it.dayNumber }

        val today = Calendar.getInstance()
        val fallbackWeekStartSaturday = getWeekStartSaturday(today)

        for (dayNumber in 1..7) {
            val day = dayMap[dayNumber]
            val isImplicitRestDay = day != null && day.isRestDay

            val dayView = inflater.inflate(R.layout.item_week_day_circle, binding.layoutWeekCalendar, false)
            val card = dayView.findViewById<MaterialCardView>(R.id.cardDay)
            val tvNumber = dayView.findViewById<TextView>(R.id.tvDayNumber)
            val ivCheck = dayView.findViewById<ImageView>(R.id.ivDayCheck)
            val tvLabel = dayView.findViewById<TextView>(R.id.tvDayLabel)

            val realDate = userProgram
                ?.let { ProgramDayCalculator.getDateForProgramDay(it, week.weekNumber, dayNumber) }
                ?.let { date -> Calendar.getInstance().apply { time = date } }
                ?: calculateDateFromSaturdayStart(fallbackWeekStartSaturday, dayNumber)
            val calendarDate = realDate.get(Calendar.DAY_OF_MONTH)
            val isToday = isSameDay(realDate, today)
            val isPast = realDate.before(today) && !isToday

            val hasPlannedWorkouts = day != null && !day.isRestDay && day.workouts.isNotEmpty()
            val dayReports = if (hasPlannedWorkouts) {
                reportStore.getByDay(program.id, week.weekNumber, dayNumber)
            } else {
                emptyList()
            }
            val isCompleted = hasPlannedWorkouts && dayReports.size >= day.workouts.size
            val isMissed = isPast && !isCompleted && hasPlannedWorkouts

            tvLabel.text = getWeekdayShortLabel(realDate)

            when {
                day == null -> {
                    tvNumber.visibility = View.VISIBLE
                    tvNumber.text = getString(R.string.pg_calendar_day_missing)
                    tvNumber.setTextColor(requireContext().getColor(R.color.error))
                    ivCheck.visibility = View.GONE
                    card.setCardBackgroundColor(requireContext().getColor(R.color.error_bg))
                    card.strokeWidth = resources.getDimensionPixelSize(R.dimen.border_thick)
                    card.setStrokeColor(requireContext().getColorStateList(R.color.error))
                    tvLabel.setTextColor(requireContext().getColor(R.color.error))
                }
                isImplicitRestDay -> {
                    tvNumber.visibility = View.GONE
                    ivCheck.visibility = View.VISIBLE
                    ivCheck.setImageResource(R.drawable.ic_rest)
                    ivCheck.imageTintList = requireContext().getColorStateList(R.color.info)
                    card.setCardBackgroundColor(requireContext().getColor(R.color.info_bg))
                    card.strokeWidth = 0
                    tvLabel.setTextColor(requireContext().getColor(R.color.info))
                }
                isCompleted -> {
                    tvNumber.visibility = View.GONE
                    ivCheck.visibility = View.VISIBLE
                    ivCheck.setImageResource(R.drawable.ic_check)
                    ivCheck.imageTintList = requireContext().getColorStateList(R.color.text_on_primary)
                    card.setCardBackgroundColor(requireContext().getColor(R.color.success))
                    card.strokeWidth = 0
                    tvLabel.setTextColor(requireContext().getColor(R.color.success))
                }
                isMissed -> {
                    tvNumber.visibility = View.GONE
                    ivCheck.visibility = View.VISIBLE
                    ivCheck.setImageResource(R.drawable.ic_close)
                    ivCheck.imageTintList = requireContext().getColorStateList(R.color.text_on_primary)
                    card.setCardBackgroundColor(requireContext().getColor(R.color.error))
                    card.strokeWidth = 0
                    tvLabel.setTextColor(requireContext().getColor(R.color.error))
                }
                isToday -> {
                    tvNumber.visibility = View.VISIBLE
                    tvNumber.text = calendarDate.toString()
                    tvNumber.setTextColor(requireContext().getColor(R.color.primary))
                    ivCheck.visibility = View.GONE
                    card.setCardBackgroundColor(requireContext().getColor(R.color.surface))
                    card.strokeWidth = resources.getDimensionPixelSize(R.dimen.border_thick)
                    card.setStrokeColor(requireContext().getColorStateList(R.color.primary))
                    tvLabel.setTextColor(requireContext().getColor(R.color.primary))
                }
                else -> {
                    tvNumber.visibility = View.VISIBLE
                    tvNumber.text = calendarDate.toString()
                    tvNumber.setTextColor(requireContext().getColor(R.color.text_hint))
                    ivCheck.visibility = View.GONE
                    card.setCardBackgroundColor(requireContext().getColor(R.color.surface_variant))
                    card.strokeWidth = 0
                    tvLabel.setTextColor(requireContext().getColor(R.color.text_hint))
                }
            }

            dayView.setOnClickListener {
                if (day != null) {
                    showDayDetailSheet(program, week, day)
                }
            }

            binding.layoutWeekCalendar.addView(dayView)
        }
    }

    private fun getWeekStartSaturday(reference: Calendar): Calendar {
        val cal = reference.clone() as Calendar
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysSinceSaturday = when (dayOfWeek) {
            Calendar.SATURDAY -> 0
            else -> dayOfWeek
        }
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
        return cal
    }

    private fun calculateDateFromSaturdayStart(weekStartSaturday: Calendar, dayNumber: Int): Calendar {
        val cal = weekStartSaturday.clone() as Calendar
        val offset = (dayNumber - 1).coerceIn(0, 6)
        cal.add(Calendar.DAY_OF_YEAR, offset)
        return cal
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun getWeekdayShortLabel(date: Calendar): String {
        return when (date.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "Sat"
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            else -> ""
        }
    }

    // -----------------------------------------------------
    // Section 3: Today Planned Workouts
    // -----------------------------------------------------

    private fun renderTodaySection(
        program: ProgramConfig,
        week: ProgramWeek,
        day: ProgramDay,
        language: String
    ) {
        if (day.isRestDay) {
            binding.tvTodayHeader.text = getString(R.string.pg_today_rest)
        } else {
            binding.tvTodayHeader.text = getString(R.string.pg_today_format, day.dayNumber)
        }

        binding.layoutTodayWorkouts.removeAllViews()
        binding.cardDayComplete.visibility = View.GONE
        binding.cardRestDay.visibility = View.GONE

        if (day.isRestDay) {
            binding.cardRestDay.visibility = View.VISIBLE
            val tomorrowDay = findTomorrowDay(program, week, day)
            if (tomorrowDay != null) {
                val tomorrowName = tomorrowDay.name?.get(language)?.ifBlank { tomorrowDay.name.en }
                    ?: getString(R.string.programs_day_title_format, tomorrowDay.dayNumber)
                val exerciseCount = tomorrowDay.workouts.sumOf { w -> w.items.count { it.type == PlannedWorkoutItemType.EXERCISE } }
                binding.tvRestDayTomorrow.text = getString(
                    R.string.pg_rest_tomorrow_format, tomorrowName, exerciseCount
                )
                binding.tvRestDayTomorrow.visibility = View.VISIBLE
            } else {
                binding.tvRestDayTomorrow.visibility = View.GONE
            }
            return
        }

        val effectivePlannedWorkouts = customizationStore.getEffectivePlannedWorkouts(
            programId = program.id,
            weekNumber = week.weekNumber,
            dayNumber = day.dayNumber,
            originalWorkouts = day.workouts
        )
        val plannedWorkouts = effectivePlannedWorkouts.map { cs ->
            ProgramWorkout(
                id = cs.id,
                name = cs.name,
                sortOrder = cs.sortOrder,
                itemsField = cs.items
            )
        }.sortedBy { it.sortOrder }

        val allCompleted = ProgramDayCalculator.isDayComplete(
            program = program,
            weekNumber = week.weekNumber,
            dayNumber = day.dayNumber,
            reportStore = reportStore,
            customizationStore = customizationStore
        )

        if (allCompleted && plannedWorkouts.isNotEmpty()) {
            binding.cardDayComplete.visibility = View.VISIBLE
            val totalMinutes = reportStore.getByDay(program.id, week.weekNumber, day.dayNumber)
                .sumOf { it.totalDurationMs } / 60000
            binding.tvDayCompleteSubtitle.text = getString(
                R.string.pg_day_complete_format,
                plannedWorkouts.size, plannedWorkouts.size, totalMinutes.toInt()
            )
            binding.btnViewDaySummary.setOnClickListener {
                openWeeklyReport(program)
            }
        }

        val firstIncompleteIndex = plannedWorkouts.indexOfFirst { reportStore.getByWorkout(it.id) == null }
        expandedPlannedWorkoutIndex = if (firstIncompleteIndex >= 0) firstIncompleteIndex else 0

        val inflater = LayoutInflater.from(requireContext())

        plannedWorkouts.forEachIndexed { index, plannedWorkout ->
            val workoutCardView = inflater.inflate(
                R.layout.item_today_workout_card, binding.layoutTodayWorkouts, false
            )

            val cardWorkout = workoutCardView.findViewById<MaterialCardView>(R.id.cardWorkout)
            val layoutHeader = workoutCardView.findViewById<LinearLayout>(R.id.layoutWorkoutHeader)
            val viewStatusDot = workoutCardView.findViewById<View>(R.id.viewStatusDot)
            val tvPlannedWorkoutName = workoutCardView.findViewById<TextView>(R.id.tvWorkoutName)
            val tvWorkoutMeta = workoutCardView.findViewById<TextView>(R.id.tvWorkoutMeta)
            val tvWorkoutStatus = workoutCardView.findViewById<TextView>(R.id.tvWorkoutStatus)
            val ivExpandIcon = workoutCardView.findViewById<ImageView>(R.id.ivExpandIcon)
            val layoutContent = workoutCardView.findViewById<LinearLayout>(R.id.layoutWorkoutContent)
            val layoutItems = workoutCardView.findViewById<LinearLayout>(R.id.layoutWorkoutItems)
            val tvMoreItems = workoutCardView.findViewById<TextView>(R.id.tvMoreItems)
            val btnAction = workoutCardView.findViewById<MaterialButton>(R.id.btnWorkoutAction)

            val plannedWorkoutName = plannedWorkout.name.get(language).ifBlank { plannedWorkout.name.en }
            tvPlannedWorkoutName.text = plannedWorkoutName

            val exerciseCount = plannedWorkout.items.count { it.type == PlannedWorkoutItemType.EXERCISE }
            val estimatedMin = estimatePlannedWorkoutDuration(plannedWorkout)
            tvWorkoutMeta.text = getString(R.string.pg_planned_workout_exercises_format, exerciseCount, estimatedMin)

            val report = reportStore.getByWorkout(plannedWorkout.id)
            val isCompleted = report != null

            if (isCompleted) {
                (viewStatusDot.background as? GradientDrawable)?.setColor(
                    requireContext().getColor(R.color.success)
                )
                tvWorkoutStatus.visibility = View.VISIBLE
                tvWorkoutStatus.text = getString(R.string.pg_planned_workout_completed)
                tvWorkoutStatus.setTextColor(requireContext().getColor(R.color.success))
                btnAction.text = getString(R.string.pg_planned_workout_completed)
                btnAction.setIconResource(R.drawable.ic_check)
                btnAction.isEnabled = true
                btnAction.backgroundTintList = requireContext().getColorStateList(R.color.surface_variant)
                btnAction.setTextColor(requireContext().getColor(R.color.success))
                btnAction.iconTint = requireContext().getColorStateList(R.color.success)
            } else {
                (viewStatusDot.background as? GradientDrawable)?.setColor(
                    requireContext().getColor(R.color.primary)
                )
                tvWorkoutStatus.visibility = View.GONE
                btnAction.text = getString(R.string.pg_start_planned_workout)
                btnAction.icon = null
            }

            renderPlannedWorkoutPreviewItems(layoutItems, plannedWorkout, language)

            val totalItems = plannedWorkout.items.size
            val shownItems = minOf(totalItems, MAX_PREVIEW_ITEMS)
            if (totalItems > shownItems) {
                tvMoreItems.visibility = View.VISIBLE
                tvMoreItems.text = getString(R.string.pg_more_items_format, totalItems - shownItems)
            } else {
                tvMoreItems.visibility = View.GONE
            }

            val isExpanded = index == expandedPlannedWorkoutIndex
            layoutContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            ivExpandIcon.rotation = if (isExpanded) 180f else 0f

            layoutHeader.setOnClickListener {
                val currentlyExpanded = layoutContent.visibility == View.VISIBLE
                layoutContent.visibility = if (currentlyExpanded) View.GONE else View.VISIBLE
                ivExpandIcon.animate().rotation(if (currentlyExpanded) 0f else 180f)
                    .setDuration(200).start()
            }

            btnAction.setOnClickListener {
                val r = report
                if (r != null) {
                    openWorkoutReport(r, plannedWorkout.items.size)
                } else {
                    openPlannedWorkout(program, plannedWorkout, week.weekNumber, day.dayNumber)
                }
            }

            binding.layoutTodayWorkouts.addView(workoutCardView)
        }
    }

    private fun renderPlannedWorkoutPreviewItems(
        container: LinearLayout,
        plannedWorkout: ProgramWorkout,
        language: String
    ) {
        container.removeAllViews()
        val items = plannedWorkout.items.sortedBy { it.sortOrder }
        val previewItems = items.take(MAX_PREVIEW_ITEMS)
        val inflater = LayoutInflater.from(requireContext())

        previewItems.forEach { item ->
            val row = inflater.inflate(R.layout.item_today_plan_row, container, false)
            val tvBullet = row.findViewById<TextView>(R.id.tvPlanBullet)
            val tvTitle = row.findViewById<TextView>(R.id.tvPlanTitle)
            val tvSubtitle = row.findViewById<TextView>(R.id.tvPlanSubtitle)
            val tvStatus = row.findViewById<TextView>(R.id.tvPlanStatus)
            tvStatus.visibility = View.GONE

            if (item.type == PlannedWorkoutItemType.REST) {
                tvBullet.text = "?"
                tvBullet.setTextColor(requireContext().getColor(R.color.text_tertiary))
                tvTitle.text = getString(R.string.programs_rest_label)
                tvTitle.setTextColor(requireContext().getColor(R.color.text_hint))
                val seconds = (item.restDurationMs ?: 0L) / 1000
                tvSubtitle.text = getString(R.string.pg_item_rest_format, seconds)
            } else {
                tvBullet.text = "?"
                tvBullet.setTextColor(requireContext().getColor(R.color.primary))
                val exerciseName = item.exerciseSlug?.let { slug ->
                    exerciseRepo.getExercise(slug)?.name?.get(language)?.ifBlank {
                        exerciseRepo.getExercise(slug)?.name?.en ?: slug
                    } ?: slug
                } ?: getString(R.string.programs_exercise_unknown)
                tvTitle.text = exerciseName

                val sets = item.sets ?: 1
                tvSubtitle.text = when {
                    item.targetReps != null -> getString(R.string.pg_item_sets_reps_format, sets, item.targetReps)
                    item.targetDuration != null -> getString(R.string.pg_item_sets_hold_format, sets, item.targetDuration)
                    else -> "$sets sets"
                }
            }

            container.addView(row)
        }
    }

    // -----------------------------------------------------
    // Section 4: Report Summary
    // -----------------------------------------------------

    private fun renderReportSummary(program: ProgramConfig) {
        val metrics = cachedProgramMetrics
        if (metrics != null && metrics.success && metrics.summary != null) {
            renderReportSummaryFromBackend(program, metrics)
            return
        }

        val allReports = reportStore.getAll().filter { it.programId == program.id }
        if (allReports.isEmpty()) {
            binding.cardProgramReport.visibility = View.GONE
            return
        }
        binding.cardProgramReport.visibility = View.VISIBLE

        binding.tvReportDays.text = allReports.map { "${it.weekNumber}-${it.dayNumber}" }.toSet().size.toString()
        binding.tvReportExercises.text = allReports.sumOf { it.report?.exerciseReports?.size ?: 0 }.toString()
        binding.tvReportTime.text = ReportAggregator.formatDuration(allReports.sumOf { it.totalDurationMs })
        binding.tvReportReps.text = allReports.sumOf { it.totalReps }.toString()

        val avgForm = allReports.mapNotNull { it.averageFormScore.takeIf { s -> s > 0f } }
        binding.tvReportAccuracy.text = if (avgForm.isNotEmpty()) "${avgForm.average().toInt()}%" else "?"
        binding.tvReportSets.text = allReports.sumOf { it.totalSetsCompleted }.toString()

        binding.tvReportInsight.text = when {
            allReports.size >= 10 -> getString(R.string.pg_report_insight_consistent)
            allReports.size >= 3 -> getString(R.string.pg_report_insight_improving)
            else -> getString(R.string.pg_report_insight_started)
        }
        binding.btnViewFullReports.setOnClickListener { openWeeklyReport(program) }
    }

    private fun renderReportSummaryFromBackend(program: ProgramConfig, metrics: MetricsResponse) {
        binding.cardProgramReport.visibility = View.VISIBLE
        val summary = metrics.summary ?: return

        binding.tvReportDays.text = (summary.daysTrained ?: 0).toString()
        binding.tvReportExercises.text = "?"
        binding.tvReportTime.text = ReportAggregator.formatDuration(summary.totalTrainingTime ?: 0L)
        binding.tvReportReps.text = (summary.totalReps ?: 0).toString()
        binding.tvReportAccuracy.text = "${(summary.overallFormScore ?: 0f).toInt()}%"
        binding.tvReportSets.text = (summary.totalVolume ?: 0f).toInt().toString()

        val insights = metrics.insights
        binding.tvReportInsight.text = if (!insights.isNullOrEmpty()) {
            insights.first().let { "${it.icon} ${it.message}" }
        } else {
            val daysTrained = summary.daysTrained ?: 0
            when {
                daysTrained >= 10 -> getString(R.string.pg_report_insight_consistent)
                daysTrained >= 3 -> getString(R.string.pg_report_insight_improving)
                else -> getString(R.string.pg_report_insight_started)
            }
        }

        binding.btnViewFullReports.setOnClickListener { openWeeklyReport(program) }
    }

    // -----------------------------------------------------
    // Program Complete State
    // -----------------------------------------------------

    private fun showProgramComplete(program: ProgramConfig) {
        binding.cardProgramComplete.visibility = View.VISIBLE
        binding.layoutTodayWorkouts.removeAllViews()
        binding.cardDayComplete.visibility = View.GONE
        binding.cardRestDay.visibility = View.GONE

        val lastWeek = program.weeks.maxByOrNull { it.weekNumber }
        val lastDay = lastWeek?.days?.maxByOrNull { it.dayNumber }
        if (lastWeek != null && lastDay != null) {
            val userProgram = programRepo.getActiveUserProgramExport()
            renderIdentityCard(program, lastWeek, lastDay)
            binding.progressProgram.progress = 100
            binding.tvProgressPercent.text = "100%"
            renderWeekCalendar(program, lastWeek, lastDay, userProgram)
        }

        binding.tvTodayHeader.text = getString(R.string.pg_complete_title)
        renderReportSummary(program)

        binding.btnViewJourney.setOnClickListener { openWeeklyReport(program) }
        binding.btnStartNext.setOnClickListener { finalizeCompletedProgramAndNavigate(program) }
    }

    /**
     * Calls POST /api/mobile/plan/complete so server finalizes the plan, then navigates from [completion].
     */
    private fun finalizeCompletedProgramAndNavigate(program: ProgramConfig) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val token = AuthManager.getAuthHeader(ctx)
            if (token == null) {
                Toast.makeText(ctx, getString(R.string.error_login_required), Toast.LENGTH_SHORT).show()
                openProgramList()
                return@launch
            }
            binding.btnStartNext.isEnabled = false
            Toast.makeText(ctx, getString(R.string.pg_complete_finishing), Toast.LENGTH_SHORT).show()
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.completeActiveProgram(token)
                }
                binding.btnStartNext.isEnabled = true
                val body = resp.body()
                if (resp.isSuccessful && body?.success == true) {
                    withContext(Dispatchers.IO) {
                        HomeRepository.getInstance(ctx).syncFromServer()
                        try {
                            ExerciseRepository.getInstance(ctx).checkForUpdates()
                        } catch (_: Exception) {
                        }
                    }
                    val completion = body.completion
                    when {
                        completion?.nextAction == "reassess" -> {
                            startActivity(PreScreeningActivity.createIntent(ctx, AssessmentType.PROGRESSION))
                        }
                        completion?.nextAction == "next_program" || completion?.nextAction == "level_up_auto" -> {
                            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_home)
                            refreshContent()
                        }
                        completion?.nextAction == "journey_summary" -> openWeeklyReport(program)
                        else -> openProgramList()
                    }
                } else {
                    openProgramList()
                }
            } catch (_: Exception) {
                binding.btnStartNext.isEnabled = true
                openProgramList()
            }
        }
    }

    // -----------------------------------------------------------
    // Day Detail Bottom Sheet
    // -----------------------------------------------------------

    private fun showDayDetailSheet(program: ProgramConfig, week: ProgramWeek, day: ProgramDay) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_day_detail, null)
        dialog.setContentView(sheet)

        val tvTitle = sheet.findViewById<TextView>(R.id.tvDayDetailTitle)
        val tvSubtitle = sheet.findViewById<TextView>(R.id.tvDayDetailSubtitle)
        val layoutWorkouts = sheet.findViewById<LinearLayout>(R.id.layoutDayWorkouts)
        val tvRestHint = sheet.findViewById<TextView>(R.id.tvDayRestHint)

        val language = requireContext().currentLanguage
        val dayTitle = day.name?.get(language)?.ifBlank { day.name.en }
            ?: getString(R.string.programs_day_title_only, day.dayNumber)
        tvTitle.text = getString(R.string.programs_day_detail_title_format, dayTitle)

        val effectivePlannedWorkoutsSheet = customizationStore.getEffectivePlannedWorkouts(
            programId = program.id,
            weekNumber = week.weekNumber,
            dayNumber = day.dayNumber,
            originalWorkouts = day.workouts
        )
        val plannedWorkouts = effectivePlannedWorkoutsSheet.map { cs ->
            ProgramWorkout(id = cs.id, name = cs.name, sortOrder = cs.sortOrder, itemsField = cs.items)
        }.sortedBy { it.sortOrder }
        tvSubtitle.text = getString(R.string.programs_planned_workouts_count_format, plannedWorkouts.size)
        layoutWorkouts.removeAllViews()
        tvRestHint.visibility = if (day.isRestDay) View.VISIBLE else View.GONE
        if (day.isRestDay) tvRestHint.text = getString(R.string.programs_rest_day_hint)

        val inflater = LayoutInflater.from(requireContext())
        plannedWorkouts.forEach { plannedWorkout ->
            val row = inflater.inflate(R.layout.item_day_workout_row, layoutWorkouts, false)
            val tvWorkoutTitle = row.findViewById<TextView>(R.id.tvWorkoutTitle)
            val tvWorkoutMeta = row.findViewById<TextView>(R.id.tvWorkoutMeta)
            val tvWorkoutStatus = row.findViewById<TextView>(R.id.tvWorkoutStatus)
            val btnAction = row.findViewById<MaterialButton>(R.id.btnWorkoutAction)

            val plannedWorkoutName = plannedWorkout.name.get(language).ifBlank { plannedWorkout.name.en }
            val exerciseCount = plannedWorkout.items.count { it.type == PlannedWorkoutItemType.EXERCISE }
            tvWorkoutTitle.text = plannedWorkoutName
            tvWorkoutMeta.text = getString(R.string.programs_exercises_count_format, exerciseCount)

            val report = reportStore.getByWorkout(plannedWorkout.id)
            val isCompleted = report != null
            tvWorkoutStatus.text = if (isCompleted) getString(R.string.programs_planned_workout_done)
            else getString(R.string.programs_planned_workout_not_started)
            tvWorkoutStatus.setTextColor(
                requireContext().getColor(if (isCompleted) R.color.success else R.color.text_secondary)
            )

            btnAction.text = if (isCompleted) getString(R.string.programs_view_summary)
            else getString(R.string.programs_start_workout)
            btnAction.setOnClickListener {
                dialog.dismiss()
                val r = report
                if (r != null) {
                    openWorkoutReport(r, plannedWorkout.items.size)
                } else {
                    openPlannedWorkout(program, plannedWorkout, week.weekNumber, day.dayNumber)
                }
            }

            layoutWorkouts.addView(row)
        }

        dialog.show()
    }

    // -----------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------

    private fun openPlannedWorkout(
        program: ProgramConfig, plannedWorkout: ProgramWorkout,
        weekNumber: Int? = null, dayNumber: Int? = null
    ) {
        val resolvedWeek = weekNumber ?: program.weeks.firstOrNull { w ->
            w.days.any { d -> d.workouts.any { it.id == plannedWorkout.id } }
        }?.weekNumber
        val resolvedDay = dayNumber ?: program.weeks.flatMap { it.days }
            .firstOrNull { d -> d.workouts.any { it.id == plannedWorkout.id } }?.dayNumber

        startActivity(Intent(requireContext(), ProgramWorkoutActivity::class.java).apply {
            putExtra(ProgramWorkoutActivity.EXTRA_PROGRAM_SLUG, program.slug)
            putExtra(ProgramWorkoutActivity.EXTRA_PROGRAM_ID, program.id)
            resolvedWeek?.let { putExtra(ProgramWorkoutActivity.EXTRA_WEEK_NUMBER, it) }
            resolvedDay?.let { putExtra(ProgramWorkoutActivity.EXTRA_DAY_NUMBER, it) }
            putExtra(ProgramWorkoutActivity.EXTRA_TARGET_WORKOUT_ID, plannedWorkout.id)
        })
    }

    private fun openWorkoutReport(
        report: ProgramWorkoutReportStore.ProgramWorkoutLocalReport, totalItems: Int
    ) {
        val reportIds = report.report?.reportIds ?: emptyList()
        val workoutReportJson = report.report?.let { com.google.gson.Gson().toJson(it) }

        startActivity(
            com.trainingvalidator.poc.ui.report.WorkoutReportActivity.createWorkoutIntent(
                context = requireContext(),
                reportIds = reportIds,
                workoutReportJson = workoutReportJson
            )
        )
    }

    private fun openProgramDetail(program: ProgramConfig) {
        startActivity(Intent(requireContext(), ProgramDetailActivity::class.java).apply {
            putExtra(ProgramDetailActivity.EXTRA_PROGRAM_SLUG, program.slug)
        })
    }

    private fun openProgramList() {
        startActivity(Intent(requireContext(), ProgramListActivity::class.java))
    }

    private fun openWeeklyReport(program: ProgramConfig) {
        startActivity(Intent(requireContext(), WeeklyReportActivity::class.java).apply {
            putExtra(WeeklyReportActivity.EXTRA_PROGRAM_SLUG, program.slug)
            putExtra(WeeklyReportActivity.EXTRA_PROGRAM_ID, program.id)
        })
    }

    // -----------------------------------------------------------
    // Unified Metrics Rendering (Grade, Sparkline)
    // -----------------------------------------------------------

    private fun renderProgramGrade(metrics: MetricsResponse) {
        val grade = metrics.summary?.programGrade
        if (grade != null) {
            binding.tvProgramGrade.text = grade
            binding.tvProgramGrade.visibility = View.VISIBLE
        } else {
            binding.tvProgramGrade.visibility = View.GONE
        }
    }

    private fun renderSparkline(metrics: MetricsResponse) {
        val weeklyScores = metrics.summary?.weeklyFormScores
        if (weeklyScores == null || weeklyScores.all { it == 0f }) {
            binding.cardWeeklySparkline.visibility = View.GONE
            return
        }

        binding.cardWeeklySparkline.visibility = View.VISIBLE

        val entries = weeklyScores.mapIndexed { index, score ->
            Entry(index.toFloat(), score)
        }

        val dataSet = LineDataSet(entries, "").apply {
            color = requireContext().getColor(R.color.primary)
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 3f
            setCircleColor(requireContext().getColor(R.color.primary))
            setDrawCircleHole(false)
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = requireContext().getColor(R.color.primary)
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val chart = binding.chartSparkline
        chart.data = LineData(dataSet)

        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)
        chart.setViewPortOffsets(0f, 8f, 0f, 8f)

        chart.xAxis.isEnabled = false
        chart.axisLeft.isEnabled = false
        chart.axisRight.isEnabled = false

        chart.invalidate()

        val weeks = metrics.summary.weeks
        if (weeks != null && weeks.size >= 2) {
            val currentWeek = weeks.last()
            val change = currentWeek.weekOverWeekChange
            if (change != null) {
                val delta = change.formScore
                binding.tvWeekComparison.text = getString(R.string.pg_vs_last_week_format, delta)
                binding.tvWeekComparison.setTextColor(
                    requireContext().getColor(if (delta >= 0) R.color.success else R.color.error)
                )
                binding.tvWeekComparison.visibility = View.VISIBLE
            } else {
                binding.tvWeekComparison.visibility = View.GONE
            }
        } else {
            binding.tvWeekComparison.visibility = View.GONE
        }
    }

    // -----------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------

    private fun findTomorrowDay(program: ProgramConfig, week: ProgramWeek, today: ProgramDay): ProgramDay? {
        val allDays = program.weeks.sortedBy { it.weekNumber }.flatMap { w ->
            w.days.sortedBy { it.dayNumber }.map { d -> DayRef(w, d) }
        }
        val todayIndex = allDays.indexOfFirst {
            it.week.weekNumber == week.weekNumber && it.day.dayNumber == today.dayNumber
        }
        if (todayIndex < 0 || todayIndex >= allDays.size - 1) return null
        val next = allDays[todayIndex + 1]
        return if (!next.day.isRestDay) next.day else null
    }

    private fun getDaysTrainedCount(program: ProgramConfig): Int {
        val reports = reportStore.getAll().filter { it.programId == program.id }
        return reports.map { "${it.weekNumber}-${it.dayNumber}" }.toSet().size
    }

    private fun getStreakDays(program: ProgramConfig): Int {
        val reports = reportStore.getAll().filter { it.programId == program.id }
        if (reports.isEmpty()) return 0
        val dayKeys = reports.map { getDayKey(it.completedAt) }.toSet()
        val calendar = Calendar.getInstance()
        var streak = 0
        var offset = 0
        while (true) {
            val key = getDayKey(calendar.timeInMillis - (offset * 24L * 60L * 60L * 1000L))
            if (dayKeys.contains(key)) {
                streak++
                offset++
            } else {
                if (streak == 0 && offset == 0) { offset++; continue }
                break
            }
        }
        return streak
    }

    private fun getDayKey(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun estimatePlannedWorkoutDuration(plannedWorkout: ProgramWorkout): Int {
        var totalSeconds = 0
        plannedWorkout.items.forEach { item ->
            if (item.type == PlannedWorkoutItemType.REST) {
                totalSeconds += ((item.restDurationMs ?: 0L) / 1000).toInt()
            } else {
                val sets = item.sets ?: 1
                val repsTime = (item.targetReps ?: 0) * 4
                val holdTime = item.targetDuration ?: 0
                val exerciseTime = sets * (repsTime + holdTime)
                val restBetweenSets = sets.coerceAtLeast(1) - 1
                totalSeconds += exerciseTime + restBetweenSets * ((item.restBetweenSetsMs ?: 30000L) / 1000).toInt()
            }
        }
        return (totalSeconds / 60).coerceAtLeast(1)
    }

    private inner class ProgramBrowseAdapter(
        private val programs: List<ProgramConfig>
    ) : RecyclerView.Adapter<ProgramBrowseAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvProgramName)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
            val tvDifficulty: TextView = view.findViewById(R.id.tvDifficulty)
            val tvStats: TextView = view.findViewById(R.id.tvStats)
            val btnViewDetails: MaterialButton = view.findViewById(R.id.btnViewDetails)
            val btnSubscribe: MaterialButton = view.findViewById(R.id.btnSubscribe)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_browse_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val program = programs[position]
            val language = requireContext().currentLanguage

            holder.tvName.text = program.name.get(language).ifBlank { program.name.en }
            holder.tvDescription.text = program.description?.get(language)?.ifBlank {
                program.description.en
            } ?: ""
            holder.tvDuration.text = getString(R.string.weeks_count_format, program.durationWeeks)

            holder.tvDifficulty.text =
                requireContext().formatProgramLevel(program.levelMin, program.levelMax)

            val totalPlannedWorkouts = program.weeks.sumOf { w -> w.days.sumOf { d -> d.workouts.size } }
            holder.tvStats.text = getString(R.string.pg_stats_format, program.durationWeeks, totalPlannedWorkouts)

            holder.btnViewDetails.setOnClickListener { openProgramDetail(program) }
            holder.btnSubscribe.setOnClickListener {
                openProgramDetail(program)
            }
        }

        override fun getItemCount() = programs.size
    }

    // -----------------------------------------------------------
    // Data Classes
    // -----------------------------------------------------------

    private data class DayRef(val week: ProgramWeek, val day: ProgramDay)

}
