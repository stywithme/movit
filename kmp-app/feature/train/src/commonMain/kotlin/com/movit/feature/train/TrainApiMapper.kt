package com.movit.feature.train

import com.movit.resources.strings.TrainStrings
import com.movit.resources.localizedString
import com.movit.core.data.MovitData
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreProgramDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.HomeStatsDto
import com.movit.core.network.dto.TrainActiveProgramDto
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.TrainTodayWorkoutDto
import com.movit.core.network.dto.WeekCalendarDayDto
import com.movit.core.network.dto.WeekCalendarDto
import kotlin.math.roundToInt

object TrainApiMapper {

    suspend fun map(
        data: HomeDataDto,
        language: String,
        strings: TrainStrings,
        explore: ExploreDataDto? = null,
    ): TrainDashboardUi {
        val trainMode = data.trainMode
        val activeProgram = trainMode?.activeProgram
        val todayWorkout = trainMode?.todayWorkout
        val status = resolveStatus(trainMode, todayWorkout)

        val featuredPrograms = if (status == TrainDashboardStatus.NoPlan) {
            featuredProgramsFromExplore(explore, language, strings)
        } else {
            emptyList()
        }

        return TrainDashboardUi(
            status = status,
            title = titleFor(status, strings),
            subtitle = subtitleFor(status, activeProgram, trainMode, language, strings),
            program = buildProgramUi(activeProgram, data.stats, language, strings),
            today = buildTodayWorkout(status, trainMode, activeProgram, todayWorkout, language, strings),
            week = buildCurrentWeek(trainMode, activeProgram, status, language, strings),
            weekOptions = buildWeekOptions(trainMode, activeProgram, status, language, strings),
            readiness = buildReadiness(data, status, language, strings),
            report = buildReportSummary(data, language, strings),
            quickActions = quickActions(strings),
            featuredPrograms = featuredPrograms,
        )
    }

    private fun resolveStatus(
        trainMode: TrainModeDto?,
        todayWorkout: TrainTodayWorkoutDto?,
    ): TrainDashboardStatus {
        return when (trainMode?.status) {
            "program_complete" -> TrainDashboardStatus.ProgramComplete
            "rest_day" -> TrainDashboardStatus.RestDay
            "no_assessment" -> TrainDashboardStatus.NoAssessment
            "reassessment_due" -> TrainDashboardStatus.ReassessmentDue
            "no_plan" -> TrainDashboardStatus.NoPlan
            "active" -> when {
                todayWorkout?.isCompleted == true -> TrainDashboardStatus.CompletedToday
                else -> TrainDashboardStatus.ActivePlan
            }
            else -> TrainDashboardStatus.NoPlan
        }
    }

    private suspend fun buildProgramUi(
        active: TrainActiveProgramDto?,
        stats: HomeStatsDto?,
        language: String,
        strings: TrainStrings,
    ): TrainProgramUi? {
        if (active == null) return null
        val totalWeeks = active.totalWeeks.coerceAtLeast(1)
        val weekProgress = active.weekProgress
        val progressPercent = if (weekProgress.total > 0) {
            ((weekProgress.completed * 100) / weekProgress.total).coerceIn(0, 100)
        } else {
            0
        }
        val daysTrained = weekProgress.completed

        return TrainProgramUi(
            id = active.id,
            slug = active.id,
            weekNumber = active.weekNumber,
            name = active.name.localized(language).ifBlank { strings.activeProgram },
            positionLabel = strings.weekDayPosition(active.weekNumber, totalWeeks, active.dayNumber),
            levelLabel = strings.activePlan,
            progressPercent = progressPercent,
            daysTrainedLabel = strings.workoutsThisWeek(daysTrained),
            streakLabel = strings.dayStreak(stats?.streak ?: 0),
            gradeLabel = progressGrade(progressPercent, strings),
        )
    }

    private suspend fun buildTodayWorkout(
        status: TrainDashboardStatus,
        trainMode: TrainModeDto?,
        activeProgram: TrainActiveProgramDto?,
        todayWorkout: TrainTodayWorkoutDto?,
        language: String,
        strings: TrainStrings,
    ): TrainTodayWorkoutUi {
        when (status) {
            TrainDashboardStatus.NoPlan,
            TrainDashboardStatus.NoAssessment,
            TrainDashboardStatus.ReassessmentDue,
            -> {
                val isReassessment = status == TrainDashboardStatus.ReassessmentDue
                val isAssessment = status == TrainDashboardStatus.NoAssessment
                return TrainTodayWorkoutUi(
                    title = when {
                        isReassessment -> strings.reassessmentDue
                        isAssessment -> strings.assessment
                        else -> strings.noActiveProgram
                    },
                    subtitle = subtitleFor(status, activeProgram, trainMode, language, strings),
                    durationLabel = strings.dash,
                    exerciseCountLabel = strings.zeroExercises,
                    focusLabel = if (isAssessment || isReassessment) strings.assessment else strings.focusProgram,
                    primaryActionLabel = when {
                        isReassessment -> strings.startReassessment
                        isAssessment -> strings.startBodyScan
                        else -> strings.explorePrograms
                    },
                )
            }
            TrainDashboardStatus.ProgramComplete -> {
                return TrainTodayWorkoutUi(
                    title = strings.programComplete,
                    subtitle = strings.programCompleteSub,
                    durationLabel = strings.dash,
                    exerciseCountLabel = strings.complete,
                    focusLabel = strings.focusProgress,
                    primaryActionLabel = strings.viewReport,
                )
            }
            TrainDashboardStatus.RestDay -> {
                val programName = activeProgram?.name?.localized(language).orEmpty()
                return TrainTodayWorkoutUi(
                    title = if (trainMode?.dayType == "active_recovery") {
                        strings.activeRecovery
                    } else {
                        strings.recoveryDay
                    },
                    subtitle = if (programName.isNotBlank()) {
                        strings.restInProgram(programName)
                    } else {
                        strings.recoverToday
                    },
                    durationLabel = strings.recovery,
                    exerciseCountLabel = strings.noWorkout,
                    focusLabel = strings.recovery,
                    primaryActionLabel = strings.exploreRecovery,
                )
            }
            else -> Unit
        }

        val sessions = buildSessions(trainMode, activeProgram, todayWorkout, language, status, strings)
        val exerciseCount = sessions.sumOf { session ->
            session.items.count { !it.isRest }
        }.takeIf { it > 0 }
            ?: todayWorkout?.exerciseCount
            ?: 0
        val totalMinutes = sessions.sumOf { it.durationLabel.toMinutesOrZero(strings.dash) }
            .takeIf { it > 0 }
            ?: todayWorkout?.estimatedMinutes
            ?: 0
        val primaryLaunch = sessions.firstOrNull { !it.isCompleted && it.launchTarget != null }?.launchTarget
            ?: sessions.firstOrNull { it.launchTarget != null }?.launchTarget
        val plannedId = todayWorkout?.plannedWorkoutId.orEmpty()
        val dayReportId = cachedPlannedReportId(plannedId)
        val reportTarget = if (status == TrainDashboardStatus.CompletedToday && activeProgram != null) {
            if (!dayReportId.isNullOrBlank()) {
                TrainReportTargetUi.ProgramDay(
                    programId = activeProgram.id,
                    weekNumber = activeProgram.weekNumber,
                    dayNumber = activeProgram.dayNumber,
                    plannedWorkoutId = plannedId,
                    reportId = dayReportId,
                )
            } else {
                TrainReportTargetUi.ProgramWeek(
                    programId = activeProgram.id,
                    weekNumber = activeProgram.weekNumber,
                )
            }
        } else {
            null
        }

        return TrainTodayWorkoutUi(
            title = todayTitle(status, todayWorkout, language, strings),
            subtitle = todaySubtitle(activeProgram, strings),
            durationLabel = if (totalMinutes > 0) strings.minEst(totalMinutes) else strings.dash,
            exerciseCountLabel = strings.exercisesCount(exerciseCount),
            focusLabel = strings.training,
            primaryActionLabel = when (status) {
                TrainDashboardStatus.CompletedToday -> {
                    if (!dayReportId.isNullOrBlank()) {
                        localizedString(language, "train_view_day_report")
                    } else {
                        localizedString(language, "program_flow_view_week_report")
                    }
                }
                else -> strings.startSession
            },
            primaryLaunchTarget = primaryLaunch,
            reportTarget = reportTarget,
            sessions = sessions,
        )
    }

    private suspend fun buildSessions(
        trainMode: TrainModeDto?,
        activeProgram: TrainActiveProgramDto?,
        todayWorkout: TrainTodayWorkoutDto?,
        language: String,
        status: TrainDashboardStatus,
        strings: TrainStrings,
    ): List<TrainWorkoutSessionUi> {
        val workout = todayWorkout ?: return emptyList()
        val isCompleted = workout.isCompleted || status == TrainDashboardStatus.CompletedToday
        val launchTarget = if (activeProgram != null && workout.plannedWorkoutId.isNotBlank()) {
            TrainWorkoutLaunchUi(
                programSlug = activeProgram.id,
                programId = activeProgram.id,
                weekNumber = activeProgram.weekNumber,
                dayNumber = activeProgram.dayNumber,
                plannedWorkoutId = workout.plannedWorkoutId,
            )
        } else {
            null
        }

        val subtitle = when {
            isCompleted -> strings.completed
            workout.allWorkoutsCount > 1 ->
                strings.workoutNOfM(workout.completedWorkoutsCount + 1, workout.allWorkoutsCount)
            else -> strings.ready
        }
        val isInProgress = !isCompleted && workout.completedWorkoutsCount > 0

        return listOf(
            TrainWorkoutSessionUi(
                title = workout.name.localized(language).ifBlank { strings.todaysWorkout },
                subtitle = subtitle,
                durationLabel = workout.estimatedMinutes?.let { strings.minEst(it) } ?: strings.dash,
                exerciseCountLabel = strings.exercisesCount(workout.exerciseCount),
                actionLabel = when {
                    isCompleted -> localizedString(language, "train_view_day_report")
                    isInProgress -> localizedString(language, "train_resume_workout")
                    else -> strings.startSession
                },
                isCompleted = isCompleted,
                launchTarget = launchTarget,
            ),
        )
    }

    /**
     * Current week strip. Consumes the backend [TrainModeDto.weekCalendars] directly
     * (per-day truth, zero client-side guessing). Falls back to an honest minimal
     * derivation only when the calendar payload is absent (old cache / hydrator path).
     */
    private suspend fun buildCurrentWeek(
        trainMode: TrainModeDto?,
        activeProgram: TrainActiveProgramDto?,
        status: TrainDashboardStatus,
        language: String,
        strings: TrainStrings,
    ): TrainWeekPreviewUi {
        if (activeProgram == null) {
            return TrainWeekPreviewUi(title = strings.thisWeek, days = emptyList())
        }
        val calendars = trainMode?.weekCalendars.orEmpty()
        val current = calendars.firstOrNull { it.isCurrentWeek }
            ?: calendars.firstOrNull { it.weekNumber == activeProgram.weekNumber }
        if (current != null) {
            return mapWeekCalendar(current, activeProgram, language, strings)
        }
        return fallbackWeek(activeProgram, status, strings)
    }

    private suspend fun buildWeekOptions(
        trainMode: TrainModeDto?,
        activeProgram: TrainActiveProgramDto?,
        status: TrainDashboardStatus,
        language: String,
        strings: TrainStrings,
    ): List<TrainWeekPreviewUi> {
        if (
            activeProgram == null ||
            status == TrainDashboardStatus.NoPlan ||
            status == TrainDashboardStatus.NoAssessment ||
            status == TrainDashboardStatus.ReassessmentDue
        ) {
            return emptyList()
        }
        val calendars = trainMode?.weekCalendars.orEmpty()
        if (calendars.isNotEmpty()) {
            return calendars
                .sortedBy { it.weekNumber }
                .map { mapWeekCalendar(it, activeProgram, language, strings) }
        }
        // Fallback: no per-day payload — show current week truthfully, others as upcoming.
        val totalWeeks = activeProgram.totalWeeks.coerceAtLeast(1)
        return (1..totalWeeks).map { weekNumber ->
            if (weekNumber == activeProgram.weekNumber) {
                fallbackWeek(activeProgram, status, strings)
            } else {
                fallbackWeek(
                    activeProgram = activeProgram.copy(weekNumber = weekNumber, dayNumber = 1),
                    status = TrainDashboardStatus.ActivePlan,
                    strings = strings,
                ).copy(weekNumber = weekNumber, isCurrentWeek = false)
            }
        }
    }

    private suspend fun mapWeekCalendar(
        calendar: WeekCalendarDto,
        activeProgram: TrainActiveProgramDto,
        language: String,
        strings: TrainStrings,
    ): TrainWeekPreviewUi {
        val days = calendar.days.map { day ->
            mapCalendarDay(day, calendar.weekNumber, activeProgram, language, strings)
        }
        return TrainWeekPreviewUi(
            title = strings.weekTitle(calendar.weekNumber),
            days = days,
            weekNumber = calendar.weekNumber,
            isCurrentWeek = calendar.isCurrentWeek,
            subtitle = strings.weekProgressLabel(calendar.completedDays, calendar.totalTrainingDays),
        )
    }

    private suspend fun mapCalendarDay(
        day: WeekCalendarDayDto,
        weekNumber: Int,
        activeProgram: TrainActiveProgramDto,
        language: String,
        strings: TrainStrings,
    ): TrainWeekDayUi {
        val state = when (day.status) {
            "completed" -> TrainWeekDayState.Completed
            "today" -> TrainWeekDayState.Today
            "in_progress" -> TrainWeekDayState.InProgress
            "missed", "needs_attention", "needs_catch_up" -> TrainWeekDayState.Missed
            "rest" -> TrainWeekDayState.Rest
            "active_recovery" -> TrainWeekDayState.ActiveRecovery
            else -> TrainWeekDayState.Upcoming
        }
        val label = day.weekdayIndex?.let { strings.weekdayShort(it) } ?: strings.dayShort(day.dayNumber)
        val workout = day.workout
        val progress = if (state == TrainWeekDayState.InProgress && workout != null && workout.allWorkoutsCount > 0) {
            (workout.completedWorkoutsCount.toFloat() / workout.allWorkoutsCount).coerceIn(0f, 1f)
        } else {
            null
        }
        return TrainWeekDayUi(
            label = label,
            dayNumber = day.dayNumber.toString(),
            state = state,
            isToday = day.isToday,
            progress = progress,
            detail = buildDayDetail(day, state, weekNumber, activeProgram, language, strings),
        )
    }

    private suspend fun buildDayDetail(
        day: WeekCalendarDayDto,
        state: TrainWeekDayState,
        weekNumber: Int,
        activeProgram: TrainActiveProgramDto,
        language: String,
        strings: TrainStrings,
    ): TrainWeekDayDetailUi {
        if (state == TrainWeekDayState.Rest || state == TrainWeekDayState.ActiveRecovery) {
            val isActive = state == TrainWeekDayState.ActiveRecovery
            return TrainWeekDayDetailUi(
                title = if (isActive) strings.activeRecovery else strings.recoveryDay,
                infoLabel = if (isActive) strings.recovery else strings.noWorkout,
                statusLabel = strings.statusRest,
                isWorkout = false,
            )
        }

        val workout = day.workout
        val title = workout?.name?.localized(language)?.ifBlank { strings.todaysWorkout }
            ?: strings.todaysWorkout
        val info = buildString {
            val exercises = workout?.exerciseCount ?: 0
            append(strings.exercisesCount(exercises))
            workout?.estimatedMinutes?.takeIf { it > 0 }?.let { append(" · ${strings.minEst(it)}") }
        }
        val isStartable = state == TrainWeekDayState.Today ||
            state == TrainWeekDayState.InProgress ||
            state == TrainWeekDayState.Missed
        val launchTarget = if (isStartable && workout != null && workout.plannedWorkoutId.isNotBlank()) {
            TrainWorkoutLaunchUi(
                programSlug = activeProgram.id,
                programId = activeProgram.id,
                weekNumber = weekNumber,
                dayNumber = day.dayNumber,
                plannedWorkoutId = workout.plannedWorkoutId,
            )
        } else {
            null
        }
        val dayReportId = workout?.plannedWorkoutId?.let { cachedPlannedReportId(it) }
        val reportTarget = if (state == TrainWeekDayState.Completed && workout != null) {
            if (!dayReportId.isNullOrBlank()) {
                TrainReportTargetUi.ProgramDay(
                    programId = activeProgram.id,
                    weekNumber = weekNumber,
                    dayNumber = day.dayNumber,
                    plannedWorkoutId = workout.plannedWorkoutId,
                    reportId = dayReportId,
                )
            } else {
                TrainReportTargetUi.ProgramWeek(
                    programId = activeProgram.id,
                    weekNumber = weekNumber,
                )
            }
        } else {
            null
        }
        val statusLabel = when (state) {
            TrainWeekDayState.Completed -> strings.statusCompleted
            TrainWeekDayState.Today -> strings.statusToday
            TrainWeekDayState.InProgress -> strings.statusInProgress
            TrainWeekDayState.Missed -> strings.statusMissed
            else -> strings.statusUpcoming
        }
        val actionLabel = when {
            state == TrainWeekDayState.Missed && launchTarget != null ->
                localizedString(language, "train_start_catch_up")
            state == TrainWeekDayState.InProgress && launchTarget != null ->
                localizedString(language, "train_resume_workout")
            isStartable && launchTarget != null -> strings.startSession
            state == TrainWeekDayState.Completed -> {
                if (!dayReportId.isNullOrBlank()) {
                    localizedString(language, "train_view_day_report")
                } else {
                    localizedString(language, "program_flow_view_week_report")
                }
            }
            else -> null
        }
        return TrainWeekDayDetailUi(
            title = title,
            infoLabel = info,
            statusLabel = statusLabel,
            isWorkout = true,
            isCompleted = state == TrainWeekDayState.Completed,
            actionLabel = actionLabel,
            launchTarget = launchTarget,
            reportTarget = reportTarget,
        )
    }

    /** Honest minimal week when the backend calendar payload is unavailable. */
    private suspend fun fallbackWeek(
        activeProgram: TrainActiveProgramDto,
        status: TrainDashboardStatus,
        strings: TrainStrings,
    ): TrainWeekPreviewUi {
        val total = activeProgram.weekProgress.total.coerceIn(1, 7)
        val completed = activeProgram.weekProgress.completed.coerceIn(0, total)
        val isCompletedToday = status == TrainDashboardStatus.CompletedToday
        val todayIndex = (completed + 1).coerceAtMost(total)
        val days = (1..total).map { dayNumber ->
            val state = when {
                dayNumber <= completed -> TrainWeekDayState.Completed
                dayNumber == todayIndex && isCompletedToday -> TrainWeekDayState.Completed
                dayNumber == todayIndex && status != TrainDashboardStatus.RestDay -> TrainWeekDayState.Today
                else -> TrainWeekDayState.Upcoming
            }
            TrainWeekDayUi(
                label = strings.dayShort(dayNumber),
                dayNumber = dayNumber.toString(),
                state = state,
                isToday = dayNumber == todayIndex && status != TrainDashboardStatus.RestDay,
            )
        }
        return TrainWeekPreviewUi(
            title = strings.weekTitle(activeProgram.weekNumber),
            days = days,
            weekNumber = activeProgram.weekNumber,
            isCurrentWeek = true,
            subtitle = strings.weekProgressLabel(completed, total),
        )
    }

    private fun buildReadiness(
        data: HomeDataDto,
        status: TrainDashboardStatus,
        language: String,
        strings: TrainStrings,
    ): TrainReadinessUi {
        val bodyScore = data.user?.bodyScore?.roundToInt()
        val formScore = data.stats?.avgFormScore
        val score = (bodyScore ?: formScore ?: 0).coerceIn(0, 100)
        val alertMessage = data.alerts?.firstOrNull()?.let { alert ->
            if (language == "ar") {
                alert.messageAr.ifBlank { alert.messageEn }
            } else {
                alert.messageEn.ifBlank { alert.messageAr }
            }
        }

        return TrainReadinessUi(
            title = strings.readiness,
            message = readinessMessage(status, strings),
            scoreLabel = if (score > 0) "$score%" else strings.dash,
            progressPercent = score,
            guidanceLabel = alertMessage?.takeIf { it.isNotBlank() } ?: guidanceFor(status, strings),
        )
    }

    private suspend fun buildReportSummary(
        data: HomeDataDto,
        language: String,
        strings: TrainStrings,
    ): TrainReportSummaryUi? {
        val recent = data.recentWorkouts?.firstOrNull() ?: return null
        val avgForm = data.stats?.avgFormScore ?: recent.formScore
        val delta = ((avgForm - recent.formScore).coerceAtLeast(0)).coerceAtMost(15)
        return TrainReportSummaryUi(
            title = strings.latestReport,
            insight = recent.exerciseName.localized(language).ifBlank { strings.lastWorkout },
            metrics = listOf(
                TrainMetricUi(strings.metricForm, "${recent.formScore}%"),
                TrainMetricUi(strings.metricReps, recent.totalReps.toString()),
                TrainMetricUi(strings.metricStreak, strings.streakShort(data.stats?.streak ?: 0)),
                TrainMetricUi(strings.metricMinutes, "${data.stats?.totalMinutes ?: 0}"),
            ),
            trendChartPoints = listOf(0.72f, 0.62f, 0.66f, 0.44f, 0.36f, 0.22f, 0.18f),
            trendDeltaPercent = if (delta > 0) delta else 5,
        )
    }

    private suspend fun featuredProgramsFromExplore(
        explore: ExploreDataDto?,
        language: String,
        strings: TrainStrings,
    ): List<TrainFeaturedProgramUi> {
        return explore?.programs.orEmpty().take(2).mapIndexed { index, program ->
            program.toFeaturedProgram(language, index == 0, strings)
        }
    }

    private suspend fun ExploreProgramDto.toFeaturedProgram(
        language: String,
        isFeatured: Boolean,
        strings: TrainStrings,
    ): TrainFeaturedProgramUi {
        val levelLabel = levelMin?.name?.localized(language)?.takeIf { it.isNotBlank() }
        val metadata = buildList {
            add(strings.weeksCount(durationWeeks))
            levelLabel?.let { add(it) }
        }
        return TrainFeaturedProgramUi(
            id = id.ifBlank { slug },
            title = name.localized(language).ifBlank { strings.programFallback },
            subtitle = strings.guidedPlan(durationWeeks),
            badge = if (isFeatured) strings.featured else null,
            metadata = metadata,
            imageUrl = coverImageUrl,
            levelLabel = levelLabel,
            durationWeeksLabel = strings.weeksCount(durationWeeks),
        )
    }

    private fun todayTitle(
        status: TrainDashboardStatus,
        workout: TrainTodayWorkoutDto?,
        language: String,
        strings: TrainStrings,
    ): String {
        if (status == TrainDashboardStatus.CompletedToday) return strings.workComplete
        return workout?.name?.localized(language)?.ifBlank { strings.todaysWorkout } ?: strings.todaysWorkout
    }

    private suspend fun todaySubtitle(
        activeProgram: TrainActiveProgramDto?,
        strings: TrainStrings,
    ): String {
        return if (activeProgram != null) {
            strings.weekDaySub(activeProgram.weekNumber, activeProgram.dayNumber)
        } else {
            strings.currentDay
        }
    }

    private fun titleFor(status: TrainDashboardStatus, strings: TrainStrings): String = when (status) {
        TrainDashboardStatus.ActivePlan -> strings.title
        TrainDashboardStatus.CompletedToday -> strings.title
        TrainDashboardStatus.RestDay -> strings.statusRecovery
        TrainDashboardStatus.ProgramComplete -> strings.programComplete
        TrainDashboardStatus.NoAssessment -> strings.assessment
        TrainDashboardStatus.ReassessmentDue -> strings.reassessmentDue
        TrainDashboardStatus.NoPlan -> strings.findPlan
    }

    private suspend fun subtitleFor(
        status: TrainDashboardStatus,
        activeProgram: TrainActiveProgramDto?,
        trainMode: TrainModeDto?,
        language: String,
        strings: TrainStrings,
    ): String = when (status) {
        TrainDashboardStatus.ActivePlan -> strings.subtitleActive
        TrainDashboardStatus.CompletedToday -> strings.subtitleCompleteToday
        TrainDashboardStatus.RestDay -> {
            val name = activeProgram?.name?.localized(language).orEmpty()
            if (name.isNotBlank()) strings.restInProgram(name) else strings.subtitleRest
        }
        TrainDashboardStatus.ProgramComplete -> strings.subtitleProgramComplete
        TrainDashboardStatus.NoAssessment -> strings.subtitleNoAssessment
        TrainDashboardStatus.ReassessmentDue -> strings.subtitleReassessment
        TrainDashboardStatus.NoPlan -> when (trainMode?.status) {
            "no_assessment" -> strings.subtitleNoAssessment
            "reassessment_due" -> strings.subtitleReassessment
            else -> strings.subtitleNoPlan
        }
    }

    private fun readinessMessage(status: TrainDashboardStatus, strings: TrainStrings): String = when (status) {
        TrainDashboardStatus.ActivePlan -> strings.readyMsgActive
        TrainDashboardStatus.RestDay -> strings.readyMsgRest
        TrainDashboardStatus.CompletedToday -> strings.readyMsgComplete
        TrainDashboardStatus.ProgramComplete -> strings.readyMsgProgramDone
        TrainDashboardStatus.NoAssessment,
        TrainDashboardStatus.ReassessmentDue,
        TrainDashboardStatus.NoPlan -> strings.readyMsgNoPlan
    }

    private fun guidanceFor(status: TrainDashboardStatus, strings: TrainStrings): String = when (status) {
        TrainDashboardStatus.ActivePlan -> strings.guideActive
        TrainDashboardStatus.RestDay -> strings.guideRest
        TrainDashboardStatus.CompletedToday -> strings.guideComplete
        TrainDashboardStatus.ProgramComplete -> strings.guideProgramDone
        TrainDashboardStatus.NoAssessment,
        TrainDashboardStatus.ReassessmentDue,
        TrainDashboardStatus.NoPlan -> strings.guideNoPlan
    }

    private fun progressGrade(progressPercent: Int, strings: TrainStrings): String = when {
        progressPercent >= 85 -> "A"
        progressPercent >= 70 -> "B"
        progressPercent >= 50 -> "C"
        progressPercent > 0 -> "D"
        else -> strings.ready
    }

    private fun String.toMinutesOrZero(dash: String): Int {
        if (this == dash || this == "-") return 0
        return substringAfter("~", this)
            .substringBefore(" ")
            .toIntOrNull()
            ?: 0
    }

    private fun quickActions(strings: TrainStrings): List<TrainQuickActionUi> = listOf(
        TrainQuickActionUi("explore", strings.quickExplore, strings.quickExploreSub),
        TrainQuickActionUi("reports", strings.quickReports, strings.quickReportsSub),
        TrainQuickActionUi("preferences", strings.quickTune, strings.quickTuneSub),
    )

    /**
     * Day report id when sync has hydrated [PlannedWorkoutReportExportDto] for this planned workout.
     * Home/trainMode DTOs do not carry reportId — without a cached export, UI uses ProgramWeek + week-report label.
     */
    private fun cachedPlannedReportId(plannedWorkoutId: String): String? {
        if (plannedWorkoutId.isBlank() || !MovitData.isInstalled) return null
        return runCatching {
            MovitData.reports.readCachedPlannedWorkoutReport(plannedWorkoutId)?.id
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun Map<String, String>.localized(language: String): String {
        val primary = if (language == "ar") this["ar"] else this["en"]
        val fallback = if (language == "ar") this["en"] else this["ar"]
        return primary?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: values.firstOrNull().orEmpty()
    }

    private fun com.movit.core.network.dto.LocalizedNameDto.localized(language: String): String {
        val primary = if (language == "ar") ar else en
        val fallback = if (language == "ar") en else ar
        return primary.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: ""
    }
}
