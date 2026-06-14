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
        val frameEvidence = ReportFrameEvidenceMapper.mapCaptures(report.peakFrameCaptures, strings)
        val heroFramePath = ReportFrameEvidenceMapper.heroFramePath(report.peakFrameCaptures)
            ?: report.heroFrame?.let { frame ->
                val path = frame.thumbnailPath ?: frame.localPath
                if (path.startsWith("file://")) path else "file://$path"
            }
        val fatigueTitle = when {
            dropOff >= 20f -> strings.fatigueElevated
            dropOff >= 10f -> strings.fatigueModerate
            else -> strings.fatigueLow
        }
        val joints = MovitSessionReportEnrichmentMapper.mapJoints(
            report.errorAnalysis,
            strings.language,
        )
        val repCompare = MovitSessionReportEnrichmentMapper.mapRepCompare(
            bestReps = report.bestReps,
            worstRep = report.worstRep,
            repTimeline = report.repTimeline,
            strings = strings,
        )
        val (formBySetValues, formBySetLabels) = if (report.repTimeline.isNotEmpty() || report.setSummaries.isNotEmpty()) {
            MovitSessionReportEnrichmentMapper.mapFormBySet(
                report.repTimeline,
                report.setSummaries,
                strings,
            )
        } else {
            listOf(formScore.toFloat()) to listOf(strings.setShort(1))
        }
        val tips = MovitSessionReportEnrichmentMapper.mapTips(
            improvementTips = report.improvementTips,
            errorAnalysis = report.errorAnalysis,
            language = strings.language,
        )
        val timelineFatigueMessage = MovitSessionReportEnrichmentMapper.buildFatigueMessage(
            report.repTimeline,
            strings,
        )
        val hold = report.holdSummary
        val durationLabel = hold?.let(MovitSessionReportEnrichmentMapper::holdDurationLabel)
            ?: ReportsFormatting.formatDuration(summary.durationMs)
        val overviewInsightMessage = hold?.let { MovitSessionReportEnrichmentMapper.holdOverviewMessage(it, strings) }
            ?: strings.avgForm(formScore)
        val resolvedFormScore = hold?.formQuality?.roundToInt()?.coerceIn(0, 100)
            ?: report.overallQuality?.score?.roundToInt()?.coerceIn(0, 100)
            ?: formScore
        return ReportDetailUi(
            id = report.id,
            exerciseName = report.exerciseName.get(strings.language),
            formScore = resolvedFormScore,
            badgeLabel = summary.rating.name.takeIf { summary.shouldCelebrate },
            sets = formBySetLabels.size.takeIf { it > 0 }?.toString() ?: "1",
            reps = summary.totalReps.toString(),
            durationLabel = durationLabel,
            overviewInsightTitle = strings.sessionOverview,
            overviewInsightMessage = overviewInsightMessage,
            joints = joints,
            jointsEmptyReason = MovitSessionReportEnrichmentMapper.jointsEmptyReason(joints, report),
            repCompare = repCompare,
            fatigueLabel = strings.fatigueLabel,
            fatigueTitle = fatigueTitle,
            fatigueMessage = timelineFatigueMessage
                ?: strings.fatigueNotEnough,
            fatigueProgressPercent = dropOff.roundToInt().coerceIn(0, 100),
            formBySetValues = formBySetValues,
            formBySetLabels = formBySetLabels,
            tips = tips,
            frameEvidence = frameEvidence,
            heroFramePath = heroFramePath,
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

    suspend fun mapSessionOverview(
        report: MovitSessionReport,
        strings: ReportDetailStrings,
        reportId: String,
    ): ReportDetailUi {
        val formScore = report.averageFormScore.roundToInt().coerceIn(0, 100)
        val primaryName = report.exerciseReports.firstOrNull()?.exerciseName ?: "Workout"
        return ReportDetailUi(
            id = reportId,
            exerciseName = primaryName,
            formScore = formScore,
            badgeLabel = null,
            sets = report.totalSetsCompleted.toString(),
            reps = report.totalReps.toString(),
            durationLabel = ReportsFormatting.formatDuration(report.totalDurationMs),
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
}
