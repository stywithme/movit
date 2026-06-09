package com.movit.feature.reports

import com.movit.core.network.dto.ExerciseMetricsSummaryDto
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.ReportInsightDto
import com.movit.core.network.dto.SetMetricsDto
import com.movit.resources.strings.ReportDetailStrings
import kotlin.math.roundToInt

object ReportDetailApiMapper {

    suspend fun map(
        reportId: String,
        response: MetricsApiResponse,
        strings: ReportDetailStrings,
    ): ReportDetailUi? {
        val summary = response.summary ?: return null
        val sets = summary.sets.orEmpty()
        val formScore = (summary.averageFormScore ?: 0f).roundToInt().coerceIn(0, 100)
        val exerciseName = summary.exerciseName?.takeIf { it.isNotBlank() }
            ?: reportId.replace('-', ' ').replaceFirstChar { it.uppercase() }

        val formBySet = sets.map { it.averageFormScore }
        val formLabels = sets.map { strings.setShort(it.setNumber) }
        val repCompare = buildRepCompare(sets, strings)
        val dropOff = summary.dropOffRate ?: 0f
        val fatigueTitle = when {
            dropOff >= 20f -> strings.fatigueElevated
            dropOff >= 10f -> strings.fatigueModerate
            else -> strings.fatigueLow
        }

        val tips = response.insights.orEmpty().map { insight ->
            ReportCoachingTipUi(
                title = insight.toTitle(),
                message = insight.message,
            )
        }

        val overviewInsight = response.insights?.firstOrNull()

        return ReportDetailUi(
            id = reportId,
            exerciseName = exerciseName,
            formScore = formScore,
            badgeLabel = summary.formRating?.takeIf { it.isNotBlank() },
            sets = (summary.setsCompleted ?: sets.size).toString(),
            reps = (summary.totalReps ?: sets.sumOf { it.totalReps }).toString(),
            durationLabel = ReportsFormatting.formatDuration(summary.totalDurationMs ?: 0L),
            overviewInsightTitle = overviewInsight?.toTitle() ?: strings.sessionOverview,
            overviewInsightMessage = overviewInsight?.message ?: strings.avgForm(formScore),
            joints = emptyList(),
            repCompare = repCompare,
            fatigueLabel = strings.fatigueLabel,
            fatigueTitle = fatigueTitle,
            fatigueMessage = buildFatigueMessage(dropOff, sets, strings),
            fatigueProgressPercent = dropOff.roundToInt().coerceIn(0, 100),
            formBySetValues = formBySet,
            formBySetLabels = formLabels,
            tips = tips,
        )
    }

    private suspend fun buildRepCompare(
        sets: List<SetMetricsDto>,
        strings: ReportDetailStrings,
    ): List<ReportRepCompareUi> {
        if (sets.isEmpty()) return emptyList()
        val best = sets.maxByOrNull { it.averageFormScore } ?: return emptyList()
        val worst = sets.minByOrNull { it.averageFormScore } ?: return emptyList()
        return listOf(
            ReportRepCompareUi(
                label = strings.bestSet(best.setNumber),
                score = best.averageFormScore.roundToInt(),
                isBest = true,
            ),
            ReportRepCompareUi(
                label = strings.worstSet(worst.setNumber),
                score = worst.averageFormScore.roundToInt(),
                isBest = false,
            ),
        )
    }

    private suspend fun buildFatigueMessage(
        dropOff: Float,
        sets: List<SetMetricsDto>,
        strings: ReportDetailStrings,
    ): String {
        if (sets.size < 2) {
            return strings.fatigueNotEnough
        }
        val first = sets.first().averageFormScore.roundToInt()
        val last = sets.last().averageFormScore.roundToInt()
        val delta = first - last
        return if (delta > 0) {
            strings.formDropped(delta, sets.last().setNumber)
        } else {
            strings.formSteady
        }
    }

    private fun ReportInsightDto.toTitle(): String =
        type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
