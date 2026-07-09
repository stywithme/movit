package com.movit.core.training.report

/**
 * Merges per-set [MovitPostTrainingReport] uploads for the same exercise session so UI can show
 * cross-set best/worst reps and form-by-set without rebuilding from raw motion buffers.
 */
object MovitPostTrainingReportCrossSetAggregator {

    fun merge(reports: List<MovitPostTrainingReport>): MovitPostTrainingReport? {
        if (reports.isEmpty()) return null
        if (reports.size == 1) return reports.first()

        val sorted = reports.sortedBy { it.resolvedSetNumber() }
        val mergedTimeline = sorted.flatMap { it.repTimeline }
        val mergedSetSummaries = sorted
            .flatMap { it.setSummaries }
            .sortedBy { it.setNumber }
            .ifEmpty {
                sorted.map { report ->
                    MovitSetSummary(
                        setNumber = report.resolvedSetNumber(),
                        repsCompleted = report.summary.totalReps,
                        repsTarget = report.summary.totalReps,
                        averageScore = report.summary.averageScore,
                        durationMs = report.summary.durationMs,
                        countedReps = report.summary.countedReps,
                        invalidatedReps = report.summary.invalidatedReps,
                        weightKg = report.summary.weightKg,
                    )
                }
            }

        if (mergedTimeline.isEmpty()) {
            return sorted.last().copy(setSummaries = mergedSetSummaries)
        }

        val bestEntry = mergedTimeline.maxBy { it.score }
        val worstEntry = mergedTimeline.minBy { it.score }
        val sameRep = bestEntry.setNumber == worstEntry.setNumber && bestEntry.repNumber == worstEntry.repNumber
        val flaggedTimeline = mergedTimeline.map { entry ->
            val isBest = entry.setNumber == bestEntry.setNumber && entry.repNumber == bestEntry.repNumber
            val isWorst = !sameRep && entry.setNumber == worstEntry.setNumber && entry.repNumber == worstEntry.repNumber
            entry.copy(isBestRep = isBest, isWorstRep = isWorst)
        }

        val bestHighlight = MovitBestRepHighlight(
            repNumber = bestEntry.repNumber,
            setNumber = bestEntry.setNumber,
            durationMs = bestEntry.durationMs,
            score = bestEntry.score,
            frameCapture = bestEntry.frameCapture,
        )
        val worstHighlight = if (sameRep) {
            null
        } else {
            MovitWorstRepHighlight(
                repNumber = worstEntry.repNumber,
                setNumber = worstEntry.setNumber,
                durationMs = worstEntry.durationMs,
                score = worstEntry.score,
                frameCapture = worstEntry.frameCapture,
            )
        }

        val primary = sorted.last()
        val weightedForm = mergedSetSummaries.map { it.averageScore }.average().toFloat()
        return primary.copy(
            summary = primary.summary.copy(averageScore = weightedForm),
            repTimeline = flaggedTimeline,
            setSummaries = mergedSetSummaries,
            bestReps = listOf(bestHighlight),
            worstRep = worstHighlight,
        )
    }

    private fun MovitPostTrainingReport.resolvedSetNumber(): Int =
        setSummaries.firstOrNull()?.setNumber
            ?: repTimeline.firstOrNull()?.setNumber
            ?: 1
}
