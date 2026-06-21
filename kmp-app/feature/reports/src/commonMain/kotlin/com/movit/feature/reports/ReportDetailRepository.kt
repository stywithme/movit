package com.movit.feature.reports

import com.movit.shared.AppResult

interface ReportDetailRepository {
    suspend fun getReportDetail(reportId: String): AppResult<ReportDetailUi>
}

class DefaultReportDetailRepository : ReportDetailRepository {
    override suspend fun getReportDetail(reportId: String): AppResult<ReportDetailUi> {
        return AppResult.Failure("Report not found.")
    }
}

