package com.movit.feature.train

import com.movit.resources.strings.TrainStrings
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreProgramDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.HomeStatsDto
import com.movit.core.network.dto.TrainActiveProgramDto
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.TrainTodayWorkoutDto
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
            week = buildWeekPreview(activeProgram, status, strings),
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
            "no_assessment", "no_plan", "reassessment_due" -> TrainDashboardStatus.NoPlan
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
            TrainDashboardStatus.NoPlan -> {
                return TrainTodayWorkoutUi(
                    title = strings.noActiveProgram,
                    subtitle = subtitleFor(status, activeProgram, trainMode, language, strings),
                    durationLabel = strings.dash,
                    exerciseCountLabel = strings.zeroExercises,
                    focusLabel = strings.focusProgram,
                    primaryActionLabel = strings.explorePrograms,
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

        return TrainTodayWorkoutUi(
            title = todayTitle(status, todayWorkout, language, strings),
            subtitle = todaySubtitle(activeProgram, strings),
            durationLabel = if (totalMinutes > 0) strings.minEst(totalMinutes) else strings.dash,
            exerciseCountLabel = strings.exercisesCount(exerciseCount),
            focusLabel = strings.training,
            primaryActionLabel = if (status == TrainDashboardStatus.CompletedToday) {
                strings.viewReport
            } else {
                strings.startSession
            },
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

        return listOf(
            TrainWorkoutSessionUi(
                title = workout.name.localized(language).ifBlank { strings.todaysWorkout },
                subtitle = subtitle,
                durationLabel = workout.estimatedMinutes?.let { strings.minEst(it) } ?: strings.dash,
                exerciseCountLabel = strings.exercisesCount(workout.exerciseCount),
                actionLabel = if (isCompleted) strings.viewSummary else strings.startSession,
                isCompleted = isCompleted,
                launchTarget = launchTarget,
            ),
        )
    }

    private suspend fun buildWeekPreview(
        activeProgram: TrainActiveProgramDto?,
        status: TrainDashboardStatus,
        strings: TrainStrings,
    ): TrainWeekPreviewUi {
        if (activeProgram == null) {
            return TrainWeekPreviewUi(title = strings.thisWeek, days = emptyList())
        }

        val weekProgress = activeProgram.weekProgress
        val completed = weekProgress.completed.coerceIn(0, 7)
        val todayDay = activeProgram.dayNumber.coerceIn(1, 7)

        val days = (1..7).map { dayNumber ->
            val state = when {
                status == TrainDashboardStatus.RestDay && dayNumber == todayDay -> TrainWeekDayState.Rest
                dayNumber < todayDay && dayNumber <= completed -> TrainWeekDayState.Done
                dayNumber == todayDay && status == TrainDashboardStatus.CompletedToday -> TrainWeekDayState.Done
                dayNumber == todayDay -> TrainWeekDayState.Today
                dayNumber < todayDay -> TrainWeekDayState.Missed
                else -> TrainWeekDayState.Planned
            }
            TrainWeekDayUi(
                label = strings.dayShort(dayNumber),
                dayNumber = dayNumber.toString(),
                state = state,
            )
        }

        return TrainWeekPreviewUi(
            title = strings.weekTitle(activeProgram.weekNumber),
            days = days,
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
        return TrainReportSummaryUi(
            title = strings.latestReport,
            insight = recent.exerciseName.localized(language).ifBlank { strings.lastWorkout },
            metrics = listOf(
                TrainMetricUi(strings.metricForm, "${recent.formScore}%"),
                TrainMetricUi(strings.metricReps, recent.totalReps.toString()),
                TrainMetricUi(strings.metricStreak, strings.streakShort(data.stats?.streak ?: 0)),
                TrainMetricUi(strings.metricMinutes, "${data.stats?.totalMinutes ?: 0}"),
            ),
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
            id = slug.ifBlank { id },
            title = name.localized(language).ifBlank { strings.programFallback },
            subtitle = strings.guidedPlan(durationWeeks),
            badge = if (isFeatured) strings.featured else null,
            metadata = metadata,
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
        TrainDashboardStatus.NoPlan -> strings.readyMsgNoPlan
    }

    private fun guidanceFor(status: TrainDashboardStatus, strings: TrainStrings): String = when (status) {
        TrainDashboardStatus.ActivePlan -> strings.guideActive
        TrainDashboardStatus.RestDay -> strings.guideRest
        TrainDashboardStatus.CompletedToday -> strings.guideComplete
        TrainDashboardStatus.ProgramComplete -> strings.guideProgramDone
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
