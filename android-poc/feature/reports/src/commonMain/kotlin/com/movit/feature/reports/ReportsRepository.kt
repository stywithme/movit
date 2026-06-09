package com.movit.feature.reports

import com.movit.shared.AppResult

interface ReportsRepository {
    suspend fun getReportsDashboard(): AppResult<ReportsDashboardUi>
}

class FakeReportsRepository(
    private val dashboard: ReportsDashboardUi = MovitReportsPreviewData.dashboardWithData,
    private val shouldFail: Boolean = false,
) : ReportsRepository {

    override suspend fun getReportsDashboard(): AppResult<ReportsDashboardUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load reports.")
        } else {
            AppResult.Success(dashboard)
        }
    }
}
