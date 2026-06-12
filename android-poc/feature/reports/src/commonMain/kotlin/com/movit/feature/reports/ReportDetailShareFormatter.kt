package com.movit.feature.reports

import com.movit.resources.localizedString

object ReportDetailShareFormatter {

    suspend fun sharePayload(
        report: ReportDetailUi,
        language: String,
        isExport: Boolean,
    ): ReportSharePayload {
        val text = if (isExport) {
            buildExportText(report, language)
        } else {
            localizedString(
                language,
                "report_detail_share_text",
                report.exerciseName,
                report.formScore,
            )
        }
        val chooserKey = if (isExport) {
            "report_detail_export_chooser_title"
        } else {
            "report_detail_share_chooser_title"
        }
        return ReportSharePayload(text = text, chooserTitleKey = chooserKey)
    }

    private suspend fun buildExportText(report: ReportDetailUi, language: String): String {
        val header = localizedString(
            language,
            "report_detail_export_header",
            report.exerciseName,
            report.formScore,
        )
        val stats = localizedString(
            language,
            "report_detail_export_stats",
            report.sets,
            report.reps,
            report.durationLabel,
        )
        return "$header\n$stats"
    }
}
