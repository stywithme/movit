package com.movit.feature.home

import com.movit.resources.strings.HomeStrings
import com.movit.core.network.dto.HomeAlertDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.TrainActiveProgramDto
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.TrainTodayWorkoutDto

object HomeApiMapper {
    suspend fun map(
        data: HomeDataDto,
        language: String,
        userDisplayName: String,
        strings: HomeStrings,
    ): HomeDashboardUi {
        val user = data.user
        val fullName = user?.name?.takeIf { it.isNotBlank() } ?: userDisplayName
        val displayName = fullName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: fullName
        val trainMode = data.trainMode
        val program = trainMode?.activeProgram
        val workout = trainMode?.todayWorkout
        val stats = data.stats
        val legacyStats = data.userStats

        val weeklyWorkouts = stats?.thisWeekExecutions ?: legacyStats?.weeklyPlannedWorkouts ?: 0
        val formScore = stats?.avgFormScore ?: legacyStats?.avgFormScore?.toInt() ?: 0
        val streak = stats?.streak ?: legacyStats?.streak ?: 0

        val metricTiles = listOf(
            HomeMetricTileUi(weeklyWorkouts.toString(), strings.metricThisWeek),
            HomeMetricTileUi(
                if (formScore > 0) "$formScore%" else strings.dash,
                strings.metricFormAvg,
            ),
            HomeMetricTileUi(streak.toString(), strings.metricStreak),
        )

        val levelCard = when {
            user?.level != null && user.bodyScore != null -> {
                val userLevel = user.level
                val userBodyScore = user.bodyScore
                if (userLevel == null || userBodyScore == null) {
                    null
                } else {
                    val levelCode = user.levelCode?.replaceFirstChar { it.uppercase() }.orEmpty()
                    val progress = user.levelProgress ?: 0
                    val pointsToNext = (100 - progress).coerceAtLeast(0)
                    val codeSuffix = levelCode.takeIf { it.isNotBlank() }?.let { strings.levelCodeSuffix(it) }.orEmpty()
                    HomeLevelCardUi(
                        eyebrow = strings.yourLevel,
                        title = strings.levelTitle(userLevel, codeSuffix),
                        subtitle = strings.bodyScorePtsNext(userBodyScore.toInt(), pointsToNext),
                        progressPercent = progress,
                    )
                }
            }
            data.levelProfile != null -> {
                val profile = data.levelProfile!!
                HomeLevelCardUi(
                    eyebrow = strings.yourLevel,
                    title = strings.levelNamed(profile.overallLevel, profile.levelInfo.name.display(language)),
                    subtitle = strings.bodyScoreOnly(profile.bodyScore.toInt()),
                    progressPercent = 0,
                )
            }
            else -> null
        }

        val alert = data.alerts?.firstOrNull()?.toHomeAlert(language)
        val status = trainMode?.status

        val activeProgram = program?.let {
            HomeActiveProgramUi(
                label = strings.currentPlan,
                title = it.name.localized(language).ifBlank { strings.activeProgramFallback },
                subtitle = when (status) {
                    "program_complete" -> strings.programComplete
                    else -> strings.programWeekSubtitle(
                        week = it.weekNumber,
                        totalWeeks = it.totalWeeks,
                        completed = it.weekProgress.completed,
                        total = it.weekProgress.total,
                    )
                },
                actionLabel = strings.viewProgram,
                showViewAction = status != "program_complete",
            )
        }

        val todayPlan = buildTodayPlan(trainMode, program, workout, language, strings)
        val showBodyScanCta = status == "no_assessment"
        val showNoProgramEmpty = status == "no_plan" || (status == null && program == null && workout == null)

        val journeyRows = buildJourneyRows(trainMode, language, strings)

        val recentActivities = data.recentWorkouts.orEmpty().take(3).mapIndexed { index, recent ->
            val name = recent.exerciseName.localized(language)
            HomeActivityUi(
                id = recent.exerciseId.ifBlank { index.toString() },
                title = strings.activityForm(name, recent.formScore),
                subtitle = strings.activityReps(recent.totalReps, recent.date),
            )
        }

        val weekProgress = program?.weekProgress
        val weeklyPercent = if (weekProgress != null && weekProgress.total > 0) {
            (weekProgress.completed * 100) / weekProgress.total
        } else {
            0
        }

        val progress = HomeProgressUi(
            weeklyCompletionPercent = weeklyPercent,
            streakDays = streak,
            activeMinutesLabel = strings.minTotal(stats?.totalMinutes ?: 0),
            formScoreLabel = if (formScore > 0) "$formScore%" else strings.dash,
        )

        val reportPreview = data.recentWorkouts?.firstOrNull()?.let { recent ->
            HomeReportPreviewUi(
                title = strings.lastSession,
                subtitle = recent.exerciseName.localized(language),
                scoreLabel = "${recent.formScore}%",
                trendLabel = strings.reportReps(recent.totalReps),
            )
        }

        return HomeDashboardUi(
            userName = displayName,
            greetingEyebrow = greetingEyebrowFor(strings),
            greetingTitle = displayName,
            greetingSubtitle = greetingSubtitleFor(status, strings),
            metricTiles = metricTiles,
            levelCard = levelCard,
            alert = alert,
            activeProgram = activeProgram,
            todayPlan = todayPlan,
            showBodyScanCta = showBodyScanCta,
            showNoProgramEmpty = showNoProgramEmpty,
            journeyRows = journeyRows,
            recentActivities = recentActivities,
            progress = progress,
            reportPreview = reportPreview,
            quickActions = listOf(
                HomeQuickActionUi("explore", strings.quickExplore, strings.quickExploreSub),
                HomeQuickActionUi("reports", strings.quickReports, strings.quickReportsSub),
            ),
            insightMessage = alert?.message,
        )
    }
}

private suspend fun buildTodayPlan(
    trainMode: TrainModeDto?,
    program: TrainActiveProgramDto?,
    workout: TrainTodayWorkoutDto?,
    language: String,
    strings: HomeStrings,
): HomeTrainingPlanUi? {
    val status = trainMode?.status ?: return null
    return when (status) {
        "active" -> workout?.let {
            HomeTrainingPlanUi(
                label = strings.weekDayLabel(program?.weekNumber ?: 1, program?.dayNumber ?: 1),
                title = it.name.localized(language).ifBlank { strings.todaysWorkout },
                subtitle = buildString {
                    append(strings.exercisesCount(it.exerciseCount))
                    it.estimatedMinutes?.let { min -> append(" · ${strings.minEst(min)}") }
                },
                durationLabel = it.estimatedMinutes?.let { min -> strings.minEst(min) } ?: strings.dash,
                exerciseCountLabel = strings.exercisesCount(it.exerciseCount),
                statusLabel = when {
                    it.isCompleted -> strings.completedToday
                    it.allWorkoutsCount > 1 ->
                        strings.workoutProgress(it.completedWorkoutsCount + 1, it.allWorkoutsCount)
                    else -> strings.readyToStart
                },
                primaryActionLabel = strings.startWorkout,
                showPrimaryAction = !it.isCompleted,
            )
        }
        "rest_day" -> HomeTrainingPlanUi(
            label = strings.todayCaps,
            title = if (trainMode.dayType == "active_recovery") strings.activeRecovery else strings.restDay,
            subtitle = strings.recoverySubtitle,
            durationLabel = strings.rest,
            exerciseCountLabel = strings.noWorkout,
            statusLabel = strings.takeItEasy,
            primaryActionLabel = strings.viewTrain,
            showPrimaryAction = false,
        )
        "program_complete" -> HomeTrainingPlanUi(
            label = strings.yourPlanLabel,
            title = strings.programComplete,
            subtitle = strings.programCompleteSubtitle,
            durationLabel = strings.dash,
            exerciseCountLabel = strings.complete,
            statusLabel = strings.greatWork,
            primaryActionLabel = strings.startReassessment,
            opensAssessment = true,
        )
        "reassessment_due" -> HomeTrainingPlanUi(
            label = strings.yourPlanLabel,
            title = strings.reassessmentDue,
            subtitle = strings.greetingReassessment,
            durationLabel = strings.dash,
            exerciseCountLabel = strings.assessment,
            statusLabel = strings.soon,
            primaryActionLabel = strings.startReassessment,
            opensAssessment = true,
        )
        "no_assessment" -> HomeTrainingPlanUi(
            label = strings.yourPlanLabel,
            title = strings.startBodyScan,
            subtitle = strings.unlockLevel,
            durationLabel = strings.dash,
            exerciseCountLabel = strings.assessment,
            statusLabel = strings.requiredBeforePlan,
            primaryActionLabel = strings.startScan,
            opensAssessment = true,
        )
        else -> null
    }
}

private suspend fun buildJourneyRows(
    trainMode: TrainModeDto?,
    @Suppress("UNUSED_PARAMETER") language: String,
    strings: HomeStrings,
): List<HomeJourneyRowUi> {
    val status = trainMode?.status ?: return emptyList()
    if (status != "active" && status != "rest_day") return emptyList()
    val program = trainMode.activeProgram ?: return emptyList()
    val completedWeeks = (program.weekNumber - 1).coerceAtLeast(0)
    val upcomingWeeks = (program.totalWeeks - program.weekNumber).coerceAtLeast(0)
    val rows = mutableListOf(
        HomeJourneyRowUi(
            id = "timeline",
            title = strings.planTimeline,
            subtitle = strings.timelineSummary(
                completed = completedWeeks,
                active = 1,
                upcoming = upcomingWeeks,
            ),
        ),
    )
    trainMode.nextReassessment?.let { next ->
        rows += HomeJourneyRowUi(
            id = "reassessment",
            title = strings.reassessmentDue,
            subtitle = next.reason.ifBlank { next.scheduledDate },
            tag = strings.soon,
        )
    }
    return rows
}

private fun HomeAlertDto.toHomeAlert(language: String): HomeAlertUi = HomeAlertUi(
    title = if (language == "ar") titleAr.ifBlank { titleEn } else titleEn.ifBlank { titleAr },
    message = if (language == "ar") messageAr.ifBlank { messageEn } else messageEn.ifBlank { messageAr },
    type = type,
)

private fun greetingEyebrowFor(strings: HomeStrings): String = when (currentLocalHour()) {
    in 0..11 -> strings.greetingMorning
    in 12..16 -> strings.greetingAfternoon
    else -> strings.greetingEvening
}

private fun greetingSubtitleFor(status: String?, strings: HomeStrings): String = when (status) {
    "active" -> strings.greetingActive
    "rest_day" -> strings.greetingRest
    "no_plan" -> strings.greetingNoPlan
    "no_assessment" -> strings.greetingNoAssessment
    "program_complete" -> strings.greetingProgramComplete
    "reassessment_due" -> strings.greetingReassessment
    else -> strings.greetingDefault
}

private fun Map<String, String>.localized(language: String): String {
    val primary = if (language == "ar") this["ar"] else this["en"]
    val fallback = if (language == "ar") this["en"] else this["ar"]
    return primary?.takeIf { it.isNotBlank() }
        ?: fallback?.takeIf { it.isNotBlank() }
        ?: values.firstOrNull().orEmpty()
}

private fun com.movit.core.network.dto.LocalizedNameDto.display(language: String): String {
    val primary = if (language == "ar") ar else en
    val fallback = if (language == "ar") en else ar
    return primary.takeIf { it.isNotBlank() }
        ?: fallback.takeIf { it.isNotBlank() }
        ?: ""
}
