package com.movit.feature.reports

import com.movit.core.data.MovitData
import com.movit.resources.strings.ReportsStrings
import com.movit.shared.AppResult

class SharedReportsRepository(
    private val fallback: ReportsRepository = FakeReportsRepository(),
) : ReportsRepository {

    override suspend fun getReportsDashboard(): AppResult<ReportsDashboardUi> {
        if (!MovitData.isInstalled) {
            return fallback.getReportsDashboard()
        }

        val platform = MovitData.requirePlatform()
        if (!platform.isProUser()) {
            return AppResult.Success(ReportsDashboardUi(hubState = ReportsHubState.Locked))
        }

        val language = platform.preferredLanguage()
        val strings = ReportsStrings.load(language)
        val programId = MovitData.home.readCached()?.trainMode?.activeProgram?.id

        return when (val result = MovitData.reports.syncDashboard(programId = programId)) {
            is AppResult.Success -> {
                val mapped = ReportsApiMapper.map(result.value, strings)
                AppResult.Success(mapped)
            }
            is AppResult.Failure -> {
                val cached = MovitData.reports.readCachedDashboard()
                if (cached != null && ReportsApiMapper.hasTrainingData(cached)) {
                    AppResult.Success(ReportsApiMapper.map(cached, strings))
                } else if (cached != null) {
                    AppResult.Success(ReportsDashboardUi(hubState = ReportsHubState.Empty))
                } else {
                    AppResult.Failure(result.message)
                }
            }
        }
    }
}
