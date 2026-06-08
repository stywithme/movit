package com.movit.feature.home

import com.movit.shared.AppResult

interface HomeRepository {
    suspend fun getHomeDashboard(): AppResult<HomeDashboardUi>
}

class FakeHomeRepository(
    private val dashboard: HomeDashboardUi = MovitHomePreviewData.dashboardWithPlan,
    private val shouldFail: Boolean = false,
) : HomeRepository {

    override suspend fun getHomeDashboard(): AppResult<HomeDashboardUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load home dashboard.")
        } else {
            AppResult.Success(dashboard)
        }
    }
}
