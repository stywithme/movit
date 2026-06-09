package com.movit.feature.reports

import com.movit.shared.AppResult

interface ReportDetailRepository {
    suspend fun getReportDetail(reportId: String): AppResult<ReportDetailUi>
}

class DefaultReportDetailRepository : ReportDetailRepository {
    override suspend fun getReportDetail(reportId: String): AppResult<ReportDetailUi> {
        val detail = ReportDetailPreviewData.forId(reportId)
            ?: buildFromSlug(reportId)
        return if (detail == null) {
            AppResult.Failure("Report not found.")
        } else {
            AppResult.Success(detail)
        }
    }

    private suspend fun buildFromSlug(reportId: String): ReportDetailUi? {
        val name = reportId.replace('-', ' ').replaceFirstChar { it.uppercase() }
        return ReportDetailPreviewData.squat().copy(
            id = reportId,
            exerciseName = name,
            formScore = 80,
            badgeLabel = null,
        )
    }
}

