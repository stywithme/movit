package com.movit.feature.library

import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreProgramDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.network.dto.ProgramProgressMetricsPayloadDto
import com.movit.core.network.dto.ReportDashboardSummaryDto
import com.movit.core.network.dto.WeekProgressPointDto
import com.movit.resources.strings.ProgramFlowStrings
import kotlin.math.roundToInt

object ProgramFlowApiMapper {

    suspend fun mapPrograms(
        explore: ExploreDataDto,
        home: HomeDataDto?,
        language: String,
        strings: ProgramFlowStrings,
    ): List<ProgramListItemUi> {
        val activeProgramId = home?.trainMode?.activeProgram?.id
        return explore.programs.map { program ->
            program.toListItem(language, strings, isActive = program.id == activeProgramId)
        }
    }

    suspend fun mapWeekSummaries(
        program: ProgramExportDto,
        metrics: ProgramProgressMetricsPayloadDto?,
        language: String,
        strings: ProgramFlowStrings,
    ): List<WeeklyReportWeekSummaryUi> {
        return program.weeks.sortedBy { it.weekNumber }.map { week ->
            val weekMetrics = metrics?.weeks?.firstOrNull { it.weekNumber == week.weekNumber }
            val plannedSessions = weekMetrics?.plannedWorkoutCount
                ?: week.days.count { !it.isRestDay && it.plannedWorkouts.isNotEmpty() }
                .coerceAtLeast(1)
            val completedSessions = estimateCompletedSessions(weekMetrics, plannedSessions, null)
            val avgForm = weekMetrics?.avgFormScore?.roundToInt() ?: 0
            val progressPercent = if (plannedSessions > 0) {
                ((completedSessions * 100f) / plannedSessions).roundToInt().coerceIn(0, 100)
            } else {
                0
            }
            val title = week.target?.localized(language)?.takeIf { it.isNotBlank() }
                ?: strings.weekTitle(week.weekNumber)
            WeeklyReportWeekSummaryUi(
                weekNumber = week.weekNumber,
                title = title,
                progressPercent = progressPercent,
                sessionsCompleted = completedSessions,
                sessionsPlanned = plannedSessions,
                avgFormPercent = avgForm.coerceIn(0, 100),
                totalReps = 0,
                message = strings.weekMessage(
                    completed = completedSessions,
                    planned = plannedSessions,
                    avgForm = avgForm,
                ),
            )
        }
    }

    suspend fun mapWeeklyReport(
        program: ProgramExportDto,
        weekNumber: Int,
        metrics: ProgramProgressMetricsPayloadDto?,
        dashboardSummary: ReportDashboardSummaryDto?,
        language: String,
        strings: ProgramFlowStrings,
    ): WeeklyReportUi {
        val weekMetrics = metrics?.weeks?.firstOrNull { it.weekNumber == weekNumber }
        val week = program.weeks.firstOrNull { it.weekNumber == weekNumber }
        val plannedSessions = weekMetrics?.plannedWorkoutCount
            ?: week?.days?.count { !it.isRestDay && it.plannedWorkouts.isNotEmpty() }
            ?: program.weeklyWorkoutTarget
            ?: 5
        val completedSessions = estimateCompletedSessions(weekMetrics, plannedSessions, dashboardSummary)
        val avgForm = weekMetrics?.avgFormScore?.roundToInt()
            ?: dashboardSummary?.overallFormScore?.roundToInt()
            ?: 0
        val totalReps = dashboardSummary?.totalReps ?: 0

        return WeeklyReportUi(
            programId = program.id,
            programSlug = program.slug,
            programName = program.name.localized(language),
            weekNumber = weekNumber,
            heroEyebrow = strings.reportHeroEyebrow(weekNumber),
            heroTitle = strings.reportHeroTitle,
            heroSubtitle = strings.reportHeroSubtitle(completedSessions, plannedSessions),
            sessionsCompleted = completedSessions,
            sessionsPlanned = plannedSessions,
            avgFormPercent = avgForm.coerceIn(0, 100),
            totalReps = totalReps,
            dailyScores = buildDailyScores(week, weekMetrics, strings),
        )
    }

    private fun ExploreProgramDto.toListItem(
        language: String,
        strings: ProgramFlowStrings,
        isActive: Boolean,
    ): ProgramListItemUi {
        val levelLabel = levelMin?.name?.localized(language)?.takeIf { it.isNotBlank() }
            ?: levelMax?.name?.localized(language).orEmpty()
        val daysPerWeek = weeklyWorkoutTargetFromExplore()
        return ProgramListItemUi(
            id = id.ifBlank { slug },
            slug = slug,
            title = name.localized(language),
            description = "",
            imageUrl = coverImageUrl,
            badge = if (isActive) strings.activeBadge else levelLabel.takeIf { it.isNotBlank() },
            levelLabel = levelLabel.ifBlank { strings.allChip },
            durationWeeks = durationWeeks.coerceAtLeast(1),
            daysPerWeek = daysPerWeek,
            isActive = isActive,
        )
    }

    private fun ExploreProgramDto.weeklyWorkoutTargetFromExplore(): Int = 5

    private fun estimateCompletedSessions(
        weekMetrics: WeekProgressPointDto?,
        plannedSessions: Int,
        dashboardSummary: ReportDashboardSummaryDto?,
    ): Int {
        val trained = dashboardSummary?.daysTrained
        if (trained != null && trained > 0) {
            return trained.coerceAtMost(plannedSessions)
        }
        if (weekMetrics?.avgFormScore != null && plannedSessions > 0) {
            return (plannedSessions * 0.8).roundToInt().coerceIn(1, plannedSessions)
        }
        return 0
    }

    private suspend fun buildDailyScores(
        week: com.movit.core.network.dto.ProgramExportWeekDto?,
        weekMetrics: WeekProgressPointDto?,
        strings: ProgramFlowStrings,
    ): List<WeeklyReportDayScoreUi> {
        val trainingDays = week?.days?.filter { !it.isRestDay }.orEmpty()
        if (trainingDays.isEmpty()) {
            return listOf(
                WeeklyReportDayScoreUi("1", weekMetrics?.avgFormScore?.roundToInt() ?: 0),
            )
        }
        val baseScore = weekMetrics?.avgFormScore?.roundToInt() ?: 0
        return buildList {
            trainingDays.forEach { day ->
                add(
                    WeeklyReportDayScoreUi(
                        label = strings.dayShort(day.dayNumber),
                        scorePercent = if (baseScore > 0) baseScore else 0,
                    ),
                )
            }
        }
    }

    private fun LocalizedNameDto.localized(language: String): String {
        val primary = if (language == "ar") ar else en
        val fallback = if (language == "ar") en else ar
        return primary.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: ""
    }
}
