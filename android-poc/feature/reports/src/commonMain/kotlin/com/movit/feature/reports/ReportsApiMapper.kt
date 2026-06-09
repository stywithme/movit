package com.movit.feature.reports

import com.movit.resources.strings.ReportsStrings
import com.movit.core.network.dto.ReportDashboardSummaryDto
import com.movit.core.network.dto.ReportInsightDto
import com.movit.core.network.dto.ReportsDashboardApiResponse
import kotlin.math.roundToInt

object ReportsApiMapper {

    suspend fun map(
        dashboard: ReportsDashboardApiResponse,
        strings: ReportsStrings,
    ): ReportsDashboardUi {
        if (!hasTrainingData(dashboard)) {
            return ReportsDashboardUi(hubState = ReportsHubState.Empty)
        }

        val summary = dashboard.summary ?: return ReportsDashboardUi(hubState = ReportsHubState.Empty)
        val trends = dashboard.trends

        val kpis = listOf(
            ReportKpiUi(
                value = (summary.daysTrained ?: 0).toString(),
                label = strings.kpiDays,
            ),
            ReportKpiUi(
                value = ReportsFormatting.formatReps(summary.totalReps ?: 0),
                label = strings.kpiReps,
                highlighted = true,
            ),
            ReportKpiUi(
                value = ReportsFormatting.formatVolume(summary.totalVolume ?: 0f),
                label = strings.kpiVolume,
            ),
            ReportKpiUi(
                value = ReportsFormatting.formatDuration(summary.totalTrainingTime ?: 0L),
                label = strings.kpiTime,
            ),
        )

        val exercises = dashboard.exerciseBreakdown.orEmpty().map { exercise ->
            val score = exercise.averageFormScore.roundToInt()
            ReportExerciseUi(
                id = exercise.exerciseSlug,
                name = exercise.exerciseName,
                scoreLabel = score.toString(),
                sessionsLabel = strings.sessions(exercise.workoutsCount),
                scorePercent = score,
            )
        }

        val insight = dashboard.insights?.firstOrNull()?.toInsightUi()
            ?: summary.overallFormScore?.takeIf { it > 0f }?.let { score ->
                ReportInsightUi(
                    title = strings.formImproving,
                    message = strings.formAvgMessage(score.roundToInt()),
                )
            }

        val formScores = trends?.formScoreByWeek.orEmpty()
        val improvementRate = computeImprovementRate(formScores)

        val volumeValues = trends?.volumeByWeek.orEmpty()
        val volumeLabels = volumeValues.indices.map { index -> strings.weekShort(index + 1) }

        val fatigue = insight?.let {
            ReportFatigueUi(
                label = strings.fatigueLabel,
                title = if ((summary.currentStreak ?: 0) >= 5) {
                    strings.fatigueElevated
                } else {
                    strings.fatigueModerate
                },
                message = it.message,
            )
        }

        return ReportsDashboardUi(
            hubState = ReportsHubState.Success,
            periodLabel = dashboard.period?.replaceFirstChar { it.uppercase() }
                ?: strings.periodDefault,
            kpis = kpis,
            formScorePoints = formScores,
            improvementRatePercent = improvementRate,
            weeklyBarValues = trends?.attendanceByWeek.orEmpty().map { it.toFloat() },
            weeklyBarLabels = trends?.attendanceByWeek.orEmpty().indices.map { index ->
                strings.weekShort(index + 1)
            },
            exercises = exercises,
            trendInsight = insight,
            volumeBarValues = volumeValues,
            volumeBarLabels = volumeLabels,
            fatigueIndex = fatigue,
        )
    }

    fun hasTrainingData(dashboard: ReportsDashboardApiResponse): Boolean {
        val summary = dashboard.summary ?: return false
        return (summary.daysTrained ?: 0) > 0 ||
            (summary.totalReps ?: 0) > 0 ||
            (summary.totalTrainingTime ?: 0L) > 0L ||
            !dashboard.exerciseBreakdown.isNullOrEmpty()
    }

    private fun ReportInsightDto.toInsightUi(): ReportInsightUi {
        return ReportInsightUi(
            title = type.replace('_', ' ').replaceFirstChar { it.uppercase() },
            message = message,
        )
    }

    internal fun computeImprovementRate(formScores: List<Float>): Float? {
        if (formScores.size < 2) return null
        val first = formScores.first()
        if (first == 0f) return null
        val last = formScores.last()
        return ((last - first) / first) * 100f
    }
}
