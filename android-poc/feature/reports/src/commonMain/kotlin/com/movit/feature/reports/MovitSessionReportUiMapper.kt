package com.movit.feature.reports

import com.movit.core.training.report.MovitExerciseSessionReport
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitSessionReport
import com.movit.resources.strings.ReportDetailStrings
import kotlin.math.roundToInt

/**
 * Maps domain session/post-training reports to [ReportDetailUi] — keeps models out of the UI layer (I-13).
 */
object MovitSessionReportUiMapper {

    suspend fun mapPostTraining(
        report: MovitPostTrainingReport,
        strings: ReportDetailStrings,
    ): ReportDetailUi {
        val summary = report.summary
        val formScore = summary.averageScore.roundToInt().coerceIn(0, 100)
        val quality = report.sessionQuality
        val dropOff = quality?.frameDropRate ?: 0f
        val fatigueTitle = when {
            dropOff >= 20f -> strings.fatigueElevated
            dropOff >= 10f -> strings.fatigueModerate
            else -> strings.fatigueLow
        }
        return ReportDetailUi(
            id = report.id,
            exerciseName = report.exerciseName.en,
            formScore = formScore,
            badgeLabel = summary.rating.name.takeIf { summary.shouldCelebrate },
            sets = "1",
            reps = summary.totalReps.toString(),
            durationLabel = ReportsFormatting.formatDuration(summary.durationMs),
            overviewInsightTitle = strings.sessionOverview,
            overviewInsightMessage = strings.avgForm(formScore),
            joints = emptyList(),
            jointsEmptyReason = ReportJointsEmptyReason.SessionUntracked,
            repCompare = emptyList(),
            fatigueLabel = strings.fatigueLabel,
            fatigueTitle = fatigueTitle,
            fatigueMessage = quality?.let {
                strings.fatigueNotEnough
            } ?: strings.fatigueNotEnough,
            fatigueProgressPercent = dropOff.roundToInt().coerceIn(0, 100),
            formBySetValues = listOf(formScore.toFloat()),
            formBySetLabels = listOf(strings.setShort(1)),
            tips = emptyList(),
        )
    }

    suspend fun mapExerciseRow(
        exercise: MovitExerciseSessionReport,
        strings: ReportDetailStrings,
    ): ReportDetailUi {
        val formScore = exercise.averageFormScore.roundToInt().coerceIn(0, 100)
        return ReportDetailUi(
            id = exercise.reportId ?: exercise.exerciseSlug,
            exerciseName = exercise.exerciseName,
            formScore = formScore,
            badgeLabel = null,
            sets = exercise.setsCompleted.toString(),
            reps = exercise.totalReps.toString(),
            durationLabel = "—",
            overviewInsightTitle = strings.sessionOverview,
            overviewInsightMessage = strings.avgForm(formScore),
            joints = emptyList(),
            jointsEmptyReason = ReportJointsEmptyReason.SessionUntracked,
            repCompare = emptyList(),
            fatigueLabel = strings.fatigueLabel,
            fatigueTitle = strings.fatigueLow,
            fatigueMessage = strings.fatigueNotEnough,
            fatigueProgressPercent = 0,
            formBySetValues = emptyList(),
            formBySetLabels = emptyList(),
            tips = emptyList(),
        )
    }

    suspend fun mapSessionDay(
        report: MovitSessionReport,
        strings: ReportDetailStrings,
    ): List<ReportDetailUi> = report.exerciseReports.map { mapExerciseRow(it, strings) }
}
