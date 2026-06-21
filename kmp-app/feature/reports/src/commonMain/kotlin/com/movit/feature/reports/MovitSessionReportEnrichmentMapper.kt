package com.movit.feature.reports

import com.movit.core.training.report.MovitBestRepHighlight
import com.movit.core.training.report.MovitHoldSummary
import com.movit.core.training.report.MovitImprovementTip
import com.movit.core.training.report.MovitSetSummary
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitReportErrorAnalysis
import com.movit.core.training.report.MovitRepTimelineEntry
import com.movit.core.training.report.MovitWorstRepHighlight
import com.movit.resources.strings.ReportDetailStrings
import kotlin.math.roundToInt

/**
 * Maps enriched [MovitPostTrainingReport] analysis fields to report-detail UI models.
 * Consumes enrichment when present; safe no-op when lists are empty (builder still evolving).
 */
internal object MovitSessionReportEnrichmentMapper {

    fun mapJoints(
        errorAnalysis: List<MovitReportErrorAnalysis>,
        language: String,
    ): List<ReportJointScoreUi> =
        errorAnalysis
            .groupBy { it.jointCode }
            .mapNotNull { (_, items) ->
                val primary = items.maxByOrNull { it.count } ?: return@mapNotNull null
                val score = jointScoreFromErrors(items)
                val label = primary.jointName.get(language).takeIf { it.isNotBlank() }
                    ?: primary.jointCode.replace('_', ' ').replaceFirstChar { it.uppercase() }
                ReportJointScoreUi(
                    label = label,
                    scorePercent = score,
                    tone = toneForScore(score),
                )
            }
            .sortedBy { it.scorePercent }

    suspend fun mapRepCompare(
        bestReps: List<MovitBestRepHighlight>,
        worstRep: MovitWorstRepHighlight?,
        repTimeline: List<MovitRepTimelineEntry>,
        strings: ReportDetailStrings,
    ): List<ReportRepCompareUi> {
        val best = bestReps.firstOrNull()
            ?: repTimeline.filter { it.isBestRep }.maxByOrNull { it.score }?.toBestHighlight()
            ?: repTimeline.maxByOrNull { it.score }?.toBestHighlight()
        val worst = worstRep
            ?: repTimeline.filter { it.isWorstRep }.minByOrNull { it.score }?.toWorstHighlight()
            ?: repTimeline.minByOrNull { it.score }?.toWorstHighlight()
        if (best == null || worst == null || best.repNumber == worst.repNumber) return emptyList()
        return listOf(
            ReportRepCompareUi(
                label = strings.bestRep(best.repNumber),
                score = best.score.roundToInt().coerceIn(0, 100),
                isBest = true,
            ),
            ReportRepCompareUi(
                label = strings.worstRep(worst.repNumber),
                score = worst.score.roundToInt().coerceIn(0, 100),
                isBest = false,
            ),
        )
    }

    suspend fun mapFormBySet(
        repTimeline: List<MovitRepTimelineEntry>,
        setSummaries: List<MovitSetSummary>,
        strings: ReportDetailStrings,
    ): Pair<List<Float>, List<String>> {
        if (repTimeline.isNotEmpty()) {
            val bySet = repTimeline.groupBy { it.setNumber }
            val setNumbers = bySet.keys.sorted()
            val values = setNumbers.map { set -> bySet.getValue(set).map { it.score }.average().toFloat() }
            val labels = setNumbers.map { strings.setShort(it) }
            return values to labels
        }
        if (setSummaries.isNotEmpty()) {
            val sorted = setSummaries.sortedBy { it.setNumber }
            return sorted.map { it.averageScore } to sorted.map { strings.setShort(it.setNumber) }
        }
        return emptyList<Float>() to emptyList()
    }

    fun mapTips(
        improvementTips: List<MovitImprovementTip>,
        errorAnalysis: List<MovitReportErrorAnalysis>,
        language: String,
    ): List<ReportCoachingTipUi> {
        val fromTips = improvementTips
            .sortedBy { it.priority }
            .map { tip ->
                ReportCoachingTipUi(
                    title = tip.title.get(language),
                    message = tip.description.get(language),
                )
            }
            .filter { it.title.isNotBlank() || it.message.isNotBlank() }
        val fromErrors = errorAnalysis
            .sortedByDescending { it.count }
            .take(3)
            .mapNotNull { item ->
                val message = item.tip.get(language).ifBlank { item.message.get(language) }
                if (message.isBlank()) return@mapNotNull null
                ReportCoachingTipUi(
                    title = item.jointName.get(language).ifBlank { item.jointCode },
                    message = message,
                )
            }
        return (fromTips + fromErrors).distinctBy { "${it.title}|${it.message}" }
    }

    suspend fun buildFatigueMessage(
        repTimeline: List<MovitRepTimelineEntry>,
        strings: ReportDetailStrings,
    ): String? {
        val bySet = repTimeline.groupBy { it.setNumber }
        val setNumbers = bySet.keys.sorted()
        if (setNumbers.size < 2) return null
        val firstAvg = bySet.getValue(setNumbers.first()).map { it.score }.average()
        val lastAvg = bySet.getValue(setNumbers.last()).map { it.score }.average()
        val delta = (firstAvg - lastAvg).roundToInt()
        return if (delta > 0) {
            strings.formDropped(delta, setNumbers.last())
        } else {
            strings.formSteady
        }
    }

    fun holdDurationLabel(holdSummary: MovitHoldSummary): String {
        val seconds = holdSummary.achievedMs / 1000
        return "${seconds}s"
    }

    suspend fun holdOverviewMessage(
        holdSummary: MovitHoldSummary,
        strings: ReportDetailStrings,
    ): String = strings.holdAchievement(holdSummary.percentage.roundToInt().coerceIn(0, 100))

    fun jointsEmptyReason(
        joints: List<ReportJointScoreUi>,
        report: MovitPostTrainingReport,
    ): ReportJointsEmptyReason = when {
        joints.isNotEmpty() -> ReportJointsEmptyReason.Generic
        report.errorAnalysis.isNotEmpty() || report.repTimeline.isNotEmpty() -> ReportJointsEmptyReason.Generic
        else -> ReportJointsEmptyReason.SessionUntracked
    }

    private fun jointScoreFromErrors(items: List<MovitReportErrorAnalysis>): Int {
        var penalty = 0
        items.forEach { item ->
            val perOccurrence = when (item.state.uppercase()) {
                "DANGER" -> 20
                "WARNING" -> 12
                else -> 8
            }
            penalty += item.count * perOccurrence
        }
        return (100 - penalty).coerceIn(0, 100)
    }

    private fun toneForScore(score: Int): ReportScoreTone = when {
        score >= 90 -> ReportScoreTone.Success
        score >= 75 -> ReportScoreTone.Primary
        else -> ReportScoreTone.Warning
    }

    private fun MovitRepTimelineEntry.toBestHighlight(): MovitBestRepHighlight =
        MovitBestRepHighlight(repNumber = repNumber, durationMs = durationMs, score = score)

    private fun MovitRepTimelineEntry.toWorstHighlight(): MovitWorstRepHighlight =
        MovitWorstRepHighlight(repNumber = repNumber, durationMs = durationMs, score = score)
}
