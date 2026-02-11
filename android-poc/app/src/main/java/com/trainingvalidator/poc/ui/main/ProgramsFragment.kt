package com.trainingvalidator.poc.ui.main

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
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentProgramsBinding
import com.trainingvalidator.poc.storage.DayCustomizationStore
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.ProgramDayCalculator
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.training.session.ReportAggregator
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramDay
import com.trainingvalidator.poc.training.models.ProgramSession
import com.trainingvalidator.poc.training.models.ProgramWeek
import com.trainingvalidator.poc.ui.ProgramDetailActivity
import com.trainingvalidator.poc.ui.ProgramListActivity
import com.trainingvalidator.poc.ui.ProgramSessionActivity
import com.trainingvalidator.poc.ui.ProgramSessionReportActivity
import com.trainingvalidator.poc.ui.WeeklyReportActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * ProgramsFragment — Redesigned program page.
 *
 * State 1: No active program → show browse list of all programs
 * State 2: Active program → show dashboard with identity card, week calendar,
 *          today sessions, and report summary.
 */
class ProgramsFragment : Fragment() {

    private var _binding: FragmentProgramsBinding? = null
    private val binding get() = _binding!!

    private lateinit var programRepo: ProgramRepository
    private lateinit var exerciseRepo: ExerciseRepository
    private lateinit var reportStore: ProgramSessionReportStore
    private lateinit var customizationStore: DayCustomizationStore

    private var expandedSessionIndex = 0 // First session expanded by default

    // ═══════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgramsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPage()
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning from session activity
        if (_binding != null) loadPage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ═══════════════════════════════════════════════════════════
    // Main Load
    // ═══════════════════════════════════════════════════════════

    private fun loadPage() {
        lifecycleScope.launch {
            programRepo = ProgramRepository.getInstance(requireContext())
            exerciseRepo = ExerciseRepository.getInstance(requireContext())
            reportStore = ProgramSessionReportStore(requireContext())
            customizationStore = DayCustomizationStore(requireContext())

            withContext(Dispatchers.IO) {
                programRepo.initialize()
                exerciseRepo.initialize(autoSync = false)
            }

            val activeProgram = programRepo.getActiveProgram()

            if (activeProgram != null) {
                showActiveProgramState(activeProgram)
            } else {
                showBrowseState()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STATE 1: Browse Programs (No Active Program)
    // ═══════════════════════════════════════════════════════════

    private fun showBrowseState() {
        binding.layoutNoProgramState.visibility = View.VISIBLE
        binding.layoutActiveProgramState.visibility = View.GONE

        val allPrograms = programRepo.getAllPrograms()
        val rv = binding.rvProgramList
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = ProgramBrowseAdapter(allPrograms)
    }

    // ═══════════════════════════════════════════════════════════
    // STATE 2: Active Program Dashboard
    // ═══════════════════════════════════════════════════════════

    private fun showActiveProgramState(program: ProgramConfig) {
        binding.layoutNoProgramState.visibility = View.GONE
        binding.layoutActiveProgramState.visibility = View.VISIBLE
        binding.cardProgramComplete.visibility = View.GONE

        // Migrate legacy SharedPreferences reports (one-time)
        reportStore.migrateFromSharedPreferences(requireContext())

        val language = getCurrentLanguage()

        // Use date-based calculation for current day
        val userProgram = programRepo.getActiveUserProgramExport()
        val currentRef = if (userProgram != null) {
            ProgramDayCalculator.getCurrentDay(program, userProgram)
        } else {
            // Fallback: if no user program enrollment, use first day
            null
        }

        if (currentRef == null || currentRef.isProgramComplete) {
            // Program complete or no enrollment
            showProgramComplete(program)
            return
        }

        val week = currentRef.week
        val day = currentRef.day

        // Section 1: Identity Card
        renderIdentityCard(program, week, day)

        // Section 2: Week Calendar
        renderWeekCalendar(program, week, day)

        // Section 3: Today
        renderTodaySection(program, week, day, language)

        // Section 4: Report Summary
        renderReportSummary(program)

        // Browse All Programs button
        binding.btnBrowseAllPrograms.setOnClickListener { openProgramList() }
    }

    // ─────────────────────────────────────────────────────
    // Section 1: Program Identity Card
    // ─────────────────────────────────────────────────────

    private fun renderIdentityCard(program: ProgramConfig, week: ProgramWeek, day: ProgramDay) {
        val language = getCurrentLanguage()
        val programName = program.name.get(language).ifBlank { program.name.en }
        binding.tvProgramName.text = programName

        // Difficulty badge
        binding.tvProgramDifficulty.text = program.difficulty.replaceFirstChar { it.uppercase() }

        // Position: Week X of Y · Day Z
        binding.tvProgramPosition.text = getString(
            R.string.pg_position_format,
            week.weekNumber,
            program.durationWeeks,
            day.dayNumber
        )

        // Progress
        val totalSessions = program.weeks.sumOf { w -> w.days.sumOf { d -> d.sessions.size } }
            .coerceAtLeast(1)
        val completedSessions = reportStore.getAll().count { it.programId == program.id }
        val progressPercent = (completedSessions * 100) / totalSessions

        binding.progressProgram.progress = progressPercent
        binding.tvProgressPercent.text = "$progressPercent%"

        // Days trained
        val daysTrained = getDaysTrainedCount(program)
        binding.tvDaysTrained.text = getString(R.string.pg_days_trained_format, daysTrained)

        // Streak
        val streak = getStreakDays(program)
        binding.tvStreak.text = getString(R.string.pg_streak_format, streak)

        // Click to view full program
        binding.cardProgramIdentity.setOnClickListener { openProgramDetail(program) }
    }

    // ─────────────────────────────────────────────────────
    // Section 2: Week Calendar
    // ─────────────────────────────────────────────────────

    private fun renderWeekCalendar(program: ProgramConfig, week: ProgramWeek, currentDay: ProgramDay) {
        binding.tvWeekTitle.text = getString(R.string.pg_week_format, week.weekNumber)
        binding.layoutWeekCalendar.removeAllViews()
        binding.layoutWeekLabels.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val days = week.days.sortedBy { it.dayNumber }

        val today = Calendar.getInstance()
        val weekStartSaturday = getWeekStartSaturday(today)

        days.forEachIndexed { index, day ->
            val dayView = inflater.inflate(R.layout.item_week_day_circle, binding.layoutWeekCalendar, false)
            val card = dayView.findViewById<MaterialCardView>(R.id.cardDay)
            val tvNumber = dayView.findViewById<TextView>(R.id.tvDayNumber)
            val ivCheck = dayView.findViewById<ImageView>(R.id.ivDayCheck)
            val tvLabel = dayView.findViewById<TextView>(R.id.tvDayLabel)

            // Week is fixed Sat -> Fri. dayNumber=1 means Saturday.
            val realDate = calculateDateFromSaturdayStart(weekStartSaturday, day.dayNumber)
            val calendarDate = realDate.get(Calendar.DAY_OF_MONTH)
            val isToday = isSameDay(realDate, today)
            val isPast = realDate.before(today) && !isToday

            val dayReports = reportStore.getByDay(program.id, week.weekNumber, day.dayNumber)
            val isCompleted = day.sessions.isNotEmpty() && dayReports.size >= day.sessions.size
            val isMissed = isPast && !isCompleted && !day.isRestDay && day.sessions.isNotEmpty()

            // Day label from actual real date (not static position)
            tvLabel.text = getWeekdayShortLabel(realDate)

            when {
                // Rest day
                day.isRestDay -> {
                    tvNumber.visibility = View.GONE
                    ivCheck.visibility = View.VISIBLE
                    ivCheck.setImageResource(R.drawable.ic_rest)
                    ivCheck.imageTintList = requireContext().getColorStateList(R.color.info)
                    card.setCardBackgroundColor(requireContext().getColor(R.color.info_bg))
                    card.strokeWidth = 0
                    tvLabel.setTextColor(requireContext().getColor(R.color.info))
                }
                // Completed day — green with check
                isCompleted -> {
                    tvNumber.visibility = View.GONE
                    ivCheck.visibility = View.VISIBLE
                    ivCheck.setImageResource(R.drawable.ic_check)
                    ivCheck.imageTintList = requireContext().getColorStateList(R.color.text_on_primary)
                    card.setCardBackgroundColor(requireContext().getColor(R.color.success))
                    card.strokeWidth = 0
                    tvLabel.setTextColor(requireContext().getColor(R.color.success))
                }
                // Missed day (past, not completed) — red
                isMissed -> {
                    tvNumber.visibility = View.GONE
                    ivCheck.visibility = View.VISIBLE
                    ivCheck.setImageResource(R.drawable.ic_close)
                    ivCheck.imageTintList = requireContext().getColorStateList(R.color.text_on_primary)
                    card.setCardBackgroundColor(requireContext().getColor(R.color.error))
                    card.strokeWidth = 0
                    tvLabel.setTextColor(requireContext().getColor(R.color.error))
                }
                // Today (current day) — primary border
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
                // Future day
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
                showDayDetailSheet(program, week, day)
            }

            binding.layoutWeekCalendar.addView(dayView)
        }
    }

    /**
     * Returns the Saturday of the current week for the provided reference date.
     * Calendar.SATURDAY = 7, Calendar.SUNDAY = 1, ..., Calendar.FRIDAY = 6
     */
    private fun getWeekStartSaturday(reference: Calendar): Calendar {
        val cal = reference.clone() as Calendar
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Calculate days since last Saturday
        // SATURDAY(7)→0, SUNDAY(1)→1, MONDAY(2)→2, ..., FRIDAY(6)→6
        val daysSinceSaturday = when (dayOfWeek) {
            Calendar.SATURDAY -> 0
            else -> dayOfWeek // SUNDAY=1, MON=2, ..., FRI=6
        }
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
        return cal
    }

    /**
     * dayNumber: 1..7 where 1=Sat, 2=Sun, ... 7=Fri.
     */
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

    // ─────────────────────────────────────────────────────
    // Section 3: Today Sessions
    // ─────────────────────────────────────────────────────

    private fun renderTodaySection(
        program: ProgramConfig,
        week: ProgramWeek,
        day: ProgramDay,
        language: String
    ) {
        // Header
        if (day.isRestDay) {
            binding.tvTodayHeader.text = getString(R.string.pg_today_rest)
        } else {
            binding.tvTodayHeader.text = getString(R.string.pg_today_format, day.dayNumber)
        }

        binding.layoutTodaySessions.removeAllViews()
        binding.cardDayComplete.visibility = View.GONE
        binding.cardRestDay.visibility = View.GONE

        // Rest Day
        if (day.isRestDay) {
            binding.cardRestDay.visibility = View.VISIBLE
            // Find tomorrow's day
            val tomorrowDay = findTomorrowDay(program, week, day)
            if (tomorrowDay != null) {
                val tomorrowName = tomorrowDay.name?.get(language)?.ifBlank { tomorrowDay.name?.en }
                    ?: getString(R.string.programs_day_title_format, tomorrowDay.dayNumber)
                val exerciseCount = tomorrowDay.sessions.sumOf { s -> s.items.count { it.type == "exercise" } }
                binding.tvRestDayTomorrow.text = getString(
                    R.string.pg_rest_tomorrow_format, tomorrowName, exerciseCount
                )
                binding.tvRestDayTomorrow.visibility = View.VISIBLE
            } else {
                binding.tvRestDayTomorrow.visibility = View.GONE
            }
            return
        }

        // Render sessions — use effective (customized) sessions if available
        val effectiveSessions = customizationStore.getEffectiveSessions(
            programId = program.id,
            weekNumber = week.weekNumber,
            dayNumber = day.dayNumber,
            originalSessions = day.sessions
        )
        val sessions = effectiveSessions.map { cs ->
            ProgramSession(
                id = cs.id,
                name = cs.name,
                sortOrder = cs.sortOrder,
                items = cs.items
            )
        }.sortedBy { it.sortOrder }
        // Use date-based day completion check via ProgramDayCalculator
        val allCompleted = ProgramDayCalculator.isDayComplete(
            program = program,
            weekNumber = week.weekNumber,
            dayNumber = day.dayNumber,
            reportStore = reportStore,
            customizationStore = customizationStore
        )

        // Check if all sessions are completed → show day complete banner
        if (allCompleted && sessions.isNotEmpty()) {
            binding.cardDayComplete.visibility = View.VISIBLE
            val totalMinutes = reportStore.getByDay(program.id, week.weekNumber, day.dayNumber)
                .sumOf { it.totalDurationMs } / 60000
            binding.tvDayCompleteSubtitle.text = getString(
                R.string.pg_day_complete_format,
                sessions.size, sessions.size, totalMinutes.toInt()
            )
            binding.btnViewDaySummary.setOnClickListener {
                openWeeklyReport(program)
            }
        }

        // Find first incomplete session to auto-expand
        val firstIncompleteIndex = sessions.indexOfFirst { reportStore.getBySession(it.id) == null }
        expandedSessionIndex = if (firstIncompleteIndex >= 0) firstIncompleteIndex else 0

        val inflater = LayoutInflater.from(requireContext())

        sessions.forEachIndexed { index, session ->
            val sessionView = inflater.inflate(
                R.layout.item_today_session_card, binding.layoutTodaySessions, false
            )

            val cardSession = sessionView.findViewById<MaterialCardView>(R.id.cardSession)
            val layoutHeader = sessionView.findViewById<LinearLayout>(R.id.layoutSessionHeader)
            val viewStatusDot = sessionView.findViewById<View>(R.id.viewStatusDot)
            val tvSessionName = sessionView.findViewById<TextView>(R.id.tvSessionName)
            val tvSessionMeta = sessionView.findViewById<TextView>(R.id.tvSessionMeta)
            val tvSessionStatus = sessionView.findViewById<TextView>(R.id.tvSessionStatus)
            val ivExpandIcon = sessionView.findViewById<ImageView>(R.id.ivExpandIcon)
            val layoutContent = sessionView.findViewById<LinearLayout>(R.id.layoutSessionContent)
            val layoutItems = sessionView.findViewById<LinearLayout>(R.id.layoutSessionItems)
            val tvMoreItems = sessionView.findViewById<TextView>(R.id.tvMoreItems)
            val btnAction = sessionView.findViewById<MaterialButton>(R.id.btnSessionAction)

            // Session name
            val sessionName = session.name.get(language).ifBlank { session.name.en }
            tvSessionName.text = sessionName

            // Session meta
            val exerciseCount = session.items.count { it.type == "exercise" }
            val estimatedMin = estimateSessionDuration(session)
            tvSessionMeta.text = getString(R.string.pg_session_exercises_format, exerciseCount, estimatedMin)

            // Session status
            val report = reportStore.getBySession(session.id)
            val isCompleted = report != null

            if (isCompleted) {
                (viewStatusDot.background as? GradientDrawable)?.setColor(
                    requireContext().getColor(R.color.success)
                )
                tvSessionStatus.visibility = View.VISIBLE
                tvSessionStatus.text = getString(R.string.pg_session_completed)
                tvSessionStatus.setTextColor(requireContext().getColor(R.color.success))
                btnAction.text = getString(R.string.pg_session_completed)
                btnAction.setIconResource(R.drawable.ic_check)
                btnAction.isEnabled = true
                btnAction.backgroundTintList = requireContext().getColorStateList(R.color.surface_variant)
                btnAction.setTextColor(requireContext().getColor(R.color.success))
                btnAction.iconTint = requireContext().getColorStateList(R.color.success)
            } else {
                (viewStatusDot.background as? GradientDrawable)?.setColor(
                    requireContext().getColor(R.color.primary)
                )
                tvSessionStatus.visibility = View.GONE
                btnAction.text = getString(R.string.pg_start_session)
                btnAction.icon = null
            }

            // Render session items (first 4)
            renderSessionPreviewItems(layoutItems, session, language)

            // More items indicator
            val totalItems = session.items.size
            val shownItems = minOf(totalItems, MAX_PREVIEW_ITEMS)
            if (totalItems > shownItems) {
                tvMoreItems.visibility = View.VISIBLE
                tvMoreItems.text = getString(R.string.pg_more_items_format, totalItems - shownItems)
            } else {
                tvMoreItems.visibility = View.GONE
            }

            // Expand/collapse logic
            val isExpanded = index == expandedSessionIndex
            layoutContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            ivExpandIcon.rotation = if (isExpanded) 180f else 0f

            layoutHeader.setOnClickListener {
                val currentlyExpanded = layoutContent.visibility == View.VISIBLE
                layoutContent.visibility = if (currentlyExpanded) View.GONE else View.VISIBLE
                ivExpandIcon.animate().rotation(if (currentlyExpanded) 0f else 180f)
                    .setDuration(200).start()
            }

            // Action button
            btnAction.setOnClickListener {
                if (isCompleted && report != null) {
                    openSessionReport(report, session.items.size)
                } else {
                    openSession(program, session, week.weekNumber, day.dayNumber)
                }
            }

            binding.layoutTodaySessions.addView(sessionView)
        }
    }

    private fun renderSessionPreviewItems(
        container: LinearLayout,
        session: ProgramSession,
        language: String
    ) {
        container.removeAllViews()
        val items = session.items.sortedBy { it.sortOrder }
        val previewItems = items.take(MAX_PREVIEW_ITEMS)
        val inflater = LayoutInflater.from(requireContext())

        previewItems.forEach { item ->
            val row = inflater.inflate(R.layout.item_today_plan_row, container, false)
            val tvBullet = row.findViewById<TextView>(R.id.tvPlanBullet)
            val tvTitle = row.findViewById<TextView>(R.id.tvPlanTitle)
            val tvSubtitle = row.findViewById<TextView>(R.id.tvPlanSubtitle)
            val tvStatus = row.findViewById<TextView>(R.id.tvPlanStatus)
            tvStatus.visibility = View.GONE

            if (item.type == "rest") {
                tvBullet.text = "·"
                tvBullet.setTextColor(requireContext().getColor(R.color.text_tertiary))
                tvTitle.text = getString(R.string.programs_rest_label)
                tvTitle.setTextColor(requireContext().getColor(R.color.text_hint))
                val seconds = (item.restDurationMs ?: 0L) / 1000
                tvSubtitle.text = getString(R.string.pg_item_rest_format, seconds)
            } else {
                tvBullet.text = "●"
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

    // ─────────────────────────────────────────────────────
    // Section 4: Report Summary
    // ─────────────────────────────────────────────────────

    private fun renderReportSummary(program: ProgramConfig) {
        val allReports = reportStore.getAll().filter { it.programId == program.id }

        if (allReports.isEmpty()) {
            binding.cardProgramReport.visibility = View.GONE
            return
        }
        binding.cardProgramReport.visibility = View.VISIBLE

        // Build week reports using the unified ReportAggregator
        val weekReports = program.weeks.map { week ->
            val dayReports = week.days.map { day ->
                val sessionReports = reportStore.getByDay(program.id, week.weekNumber, day.dayNumber)
                ReportAggregator.aggregateDay(
                    programId = program.id,
                    weekNumber = week.weekNumber,
                    dayNumber = day.dayNumber,
                    sessionReports = sessionReports,
                    totalSessionsInDay = day.sessions.size
                )
            }
            val nonRestDays = week.days.count { !it.isRestDay && it.sessions.isNotEmpty() }
            ReportAggregator.aggregateWeek(program.id, week.weekNumber, dayReports, nonRestDays)
        }
        val programReport = ReportAggregator.aggregateProgram(program.id, weekReports)

        // Row 1: Days Trained
        binding.tvReportDays.text = programReport.daysTrained.toString()

        // Row 1: Total exercises done
        binding.tvReportExercises.text = programReport.totalExercises.toString()

        // Row 1: Total time
        binding.tvReportTime.text = ReportAggregator.formatDuration(programReport.totalDurationMs)

        // Row 2: Total Reps
        binding.tvReportReps.text = programReport.totalReps.toString()

        // Row 2: Form Score (not completion rate)
        val formRating = ReportAggregator.getFormRating(programReport.averageFormScore)
        binding.tvReportAccuracy.text = "${programReport.averageFormScore.toInt()}%"

        // Row 2: Total Sets Completed
        binding.tvReportSets.text = programReport.totalSets.toString()

        // Insight message based on form progression
        val completedSessions = allReports.size
        binding.tvReportInsight.text = when {
            completedSessions >= 10 -> getString(R.string.pg_report_insight_consistent)
            completedSessions >= 3 -> getString(R.string.pg_report_insight_improving)
            else -> getString(R.string.pg_report_insight_started)
        }

        // View full reports
        binding.btnViewFullReports.setOnClickListener {
            openWeeklyReport(program)
        }
    }

    // ─────────────────────────────────────────────────────
    // Program Complete State
    // ─────────────────────────────────────────────────────

    private fun showProgramComplete(program: ProgramConfig) {
        binding.cardProgramComplete.visibility = View.VISIBLE
        // Hide today section content
        binding.layoutTodaySessions.removeAllViews()
        binding.cardDayComplete.visibility = View.GONE
        binding.cardRestDay.visibility = View.GONE

        // Still show identity card (fully completed)
        val lastWeek = program.weeks.maxByOrNull { it.weekNumber }
        val lastDay = lastWeek?.days?.maxByOrNull { it.dayNumber }
        if (lastWeek != null && lastDay != null) {
            renderIdentityCard(program, lastWeek, lastDay)
            binding.progressProgram.progress = 100
            binding.tvProgressPercent.text = "100%"
            renderWeekCalendar(program, lastWeek, lastDay)
        }

        binding.tvTodayHeader.text = getString(R.string.pg_complete_title)
        renderReportSummary(program)

        binding.btnViewJourney.setOnClickListener { openWeeklyReport(program) }
        binding.btnStartNext.setOnClickListener { openProgramList() }
    }

    // ═══════════════════════════════════════════════════════════
    // Day Detail Bottom Sheet
    // ═══════════════════════════════════════════════════════════

    private fun showDayDetailSheet(program: ProgramConfig, week: ProgramWeek, day: ProgramDay) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_day_detail, null)
        dialog.setContentView(sheet)

        val tvTitle = sheet.findViewById<TextView>(R.id.tvDayDetailTitle)
        val tvSubtitle = sheet.findViewById<TextView>(R.id.tvDayDetailSubtitle)
        val layoutSessions = sheet.findViewById<LinearLayout>(R.id.layoutDaySessions)
        val tvRestHint = sheet.findViewById<TextView>(R.id.tvDayRestHint)

        val language = getCurrentLanguage()
        val dayTitle = day.name?.get(language)?.ifBlank { day.name?.en }
            ?: getString(R.string.programs_day_title_only, day.dayNumber)
        tvTitle.text = getString(R.string.programs_day_detail_title_format, dayTitle)

        // Use effective (customized) sessions if available
        val effectiveSessionsSheet = customizationStore.getEffectiveSessions(
            programId = program.id,
            weekNumber = week.weekNumber,
            dayNumber = day.dayNumber,
            originalSessions = day.sessions
        )
        val sessions = effectiveSessionsSheet.map { cs ->
            ProgramSession(id = cs.id, name = cs.name, sortOrder = cs.sortOrder, items = cs.items)
        }.sortedBy { it.sortOrder }
        tvSubtitle.text = getString(R.string.programs_sessions_count_format, sessions.size)
        layoutSessions.removeAllViews()
        tvRestHint.visibility = if (day.isRestDay) View.VISIBLE else View.GONE
        if (day.isRestDay) tvRestHint.text = getString(R.string.programs_rest_day_hint)

        val inflater = LayoutInflater.from(requireContext())
        sessions.forEach { session ->
            val row = inflater.inflate(R.layout.item_day_session_row, layoutSessions, false)
            val tvSessionTitle = row.findViewById<TextView>(R.id.tvSessionTitle)
            val tvSessionMeta = row.findViewById<TextView>(R.id.tvSessionMeta)
            val tvSessionStatus = row.findViewById<TextView>(R.id.tvSessionStatus)
            val btnAction = row.findViewById<MaterialButton>(R.id.btnSessionAction)

            val sessionName = session.name.get(language).ifBlank { session.name.en }
            val exerciseCount = session.items.count { it.type == "exercise" }
            tvSessionTitle.text = sessionName
            tvSessionMeta.text = getString(R.string.programs_exercises_count_format, exerciseCount)

            val report = reportStore.getBySession(session.id)
            val isCompleted = report != null
            tvSessionStatus.text = if (isCompleted) getString(R.string.programs_session_done)
            else getString(R.string.programs_session_not_started)
            tvSessionStatus.setTextColor(
                requireContext().getColor(if (isCompleted) R.color.success else R.color.text_secondary)
            )

            btnAction.text = if (isCompleted) getString(R.string.programs_view_summary)
            else getString(R.string.programs_start_session)
            btnAction.setOnClickListener {
                dialog.dismiss()
                if (isCompleted && report != null) {
                    openSessionReport(report, session.items.size)
                } else {
                    openSession(program, session, week.weekNumber, day.dayNumber)
                }
            }

            layoutSessions.addView(row)
        }

        dialog.show()
    }

    // ═══════════════════════════════════════════════════════════
    // Navigation
    // ═══════════════════════════════════════════════════════════

    private fun openSession(
        program: ProgramConfig, session: ProgramSession,
        weekNumber: Int? = null, dayNumber: Int? = null
    ) {
        val resolvedWeek = weekNumber ?: program.weeks.firstOrNull { w ->
            w.days.any { d -> d.sessions.any { it.id == session.id } }
        }?.weekNumber
        val resolvedDay = dayNumber ?: program.weeks.flatMap { it.days }
            .firstOrNull { d -> d.sessions.any { it.id == session.id } }?.dayNumber

        // Open the full day view with the target session highlighted
        startActivity(Intent(requireContext(), ProgramSessionActivity::class.java).apply {
            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_SLUG, program.slug)
            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_ID, program.id)
            resolvedWeek?.let { putExtra(ProgramSessionActivity.EXTRA_WEEK_NUMBER, it) }
            resolvedDay?.let { putExtra(ProgramSessionActivity.EXTRA_DAY_NUMBER, it) }
            putExtra(ProgramSessionActivity.EXTRA_TARGET_SESSION_ID, session.id)
        })
    }

    private fun openSessionReport(
        report: ProgramSessionReportStore.ProgramSessionLocalReport, totalItems: Int
    ) {
        startActivity(Intent(requireContext(), ProgramSessionReportActivity::class.java).apply {
            putExtra(ProgramSessionReportActivity.EXTRA_TOTAL_ITEMS, totalItems)
            putExtra(ProgramSessionReportActivity.EXTRA_TOTAL_SETS, report.totalSetsPlanned)
            putExtra(ProgramSessionReportActivity.EXTRA_COMPLETED_SETS, report.totalSetsCompleted)
            putExtra(ProgramSessionReportActivity.EXTRA_DURATION_MS, report.totalDurationMs)
            putExtra(ProgramSessionReportActivity.EXTRA_AVG_ACCURACY, report.averageAccuracy)
            report.report?.let {
                putExtra(
                    ProgramSessionReportActivity.EXTRA_SESSION_REPORT_JSON,
                    com.google.gson.Gson().toJson(it)
                )
            }
        })
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

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    // findCurrentDayRef is now replaced by ProgramDayCalculator.getCurrentDay()
    // which uses date-based calculation matching the backend logic.

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

    private fun estimateSessionDuration(session: ProgramSession): Int {
        var totalSeconds = 0
        session.items.forEach { item ->
            if (item.type == "rest") {
                totalSeconds += ((item.restDurationMs ?: 0L) / 1000).toInt()
            } else {
                val sets = item.sets ?: 1
                val repsTime = (item.targetReps ?: 0) * 4 // ~4 sec per rep
                val holdTime = item.targetDuration ?: 0
                val exerciseTime = sets * (repsTime + holdTime)
                val restBetweenSets = sets.coerceAtLeast(1) - 1
                totalSeconds += exerciseTime + restBetweenSets * ((item.restBetweenSetsMs ?: 30000L) / 1000).toInt()
            }
        }
        return (totalSeconds / 60).coerceAtLeast(1)
    }

    private fun getCurrentLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) resources.configuration.locales[0] else appLocales[0]
        return locale?.language ?: "en"
    }

    // ═══════════════════════════════════════════════════════════
    // Browse Programs Adapter (State 1)
    // ═══════════════════════════════════════════════════════════

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
            val language = getCurrentLanguage()

            holder.tvName.text = program.name.get(language).ifBlank { program.name.en }
            holder.tvDescription.text = program.description?.get(language)?.ifBlank {
                program.description?.en
            } ?: ""
            holder.tvDuration.text = getString(R.string.weeks_count_format, program.durationWeeks)

            val diffIcon = when (program.difficulty) {
                "intermediate" -> "⭐⭐"
                "advanced" -> "⭐⭐⭐"
                else -> "⭐"
            }
            holder.tvDifficulty.text = "$diffIcon ${program.difficulty.replaceFirstChar { it.uppercase() }}"

            val totalSessions = program.weeks.sumOf { w -> w.days.sumOf { d -> d.sessions.size } }
            holder.tvStats.text = getString(R.string.pg_stats_format, program.durationWeeks, totalSessions)

            holder.btnViewDetails.setOnClickListener { openProgramDetail(program) }
            holder.btnSubscribe.setOnClickListener {
                // TODO: Implement actual subscription via backend API
                // For now, navigate to program detail for enrollment
                openProgramDetail(program)
            }
        }

        override fun getItemCount() = programs.size
    }

    // ═══════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════

    private data class DayRef(val week: ProgramWeek, val day: ProgramDay)

    companion object {
        private const val MAX_PREVIEW_ITEMS = 4
    }
}
