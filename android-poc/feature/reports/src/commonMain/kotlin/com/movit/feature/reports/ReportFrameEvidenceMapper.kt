package com.movit.feature.reports

import com.movit.core.training.report.MovitPeakCaptureType
import com.movit.core.training.report.MovitPeakFrameCapture
import com.movit.resources.strings.ReportDetailStrings

object ReportFrameEvidenceMapper {

    suspend fun mapCaptures(
        captures: List<MovitPeakFrameCapture>,
        strings: ReportDetailStrings,
    ): List<ReportFrameEvidenceUi> =
        captures.map { capture ->
            ReportFrameEvidenceUi(
                label = labelFor(capture.captureType, capture.repNumber, strings),
                localPath = toDisplayUri(capture.localPath),
                thumbnailPath = capture.thumbnailPath?.let(::toDisplayUri),
                captureType = capture.captureType.name,
                repNumber = capture.repNumber,
            )
        }

    fun heroFramePath(captures: List<MovitPeakFrameCapture>): String? {
        val preferred = listOf(
            MovitPeakCaptureType.DANGER_FRAME,
            MovitPeakCaptureType.BEST_REP,
            MovitPeakCaptureType.ERROR_FRAME,
            MovitPeakCaptureType.PEAK_FRAME,
            MovitPeakCaptureType.HOLD_SAMPLE,
        )
        for (type in preferred) {
            val match = captures.firstOrNull { it.captureType == type }
            if (match != null) {
                return toDisplayUri(match.thumbnailPath ?: match.localPath)
            }
        }
        return null
    }

    internal suspend fun labelFor(
        captureType: MovitPeakCaptureType,
        repNumber: Int,
        strings: ReportDetailStrings,
    ): String = when (captureType) {
        MovitPeakCaptureType.DANGER_FRAME -> strings.frameEvidenceDanger(repNumber)
        MovitPeakCaptureType.BEST_REP -> strings.frameEvidenceBest(repNumber)
        MovitPeakCaptureType.ERROR_FRAME -> strings.frameEvidenceFormIssue(repNumber)
        MovitPeakCaptureType.PEAK_FRAME -> strings.frameEvidencePeak(repNumber)
        MovitPeakCaptureType.HOLD_SAMPLE -> strings.frameEvidenceHoldSample()
    }

    internal fun toDisplayUri(localPath: String): String =
        if (localPath.startsWith("file://")) localPath else "file://$localPath"
}
