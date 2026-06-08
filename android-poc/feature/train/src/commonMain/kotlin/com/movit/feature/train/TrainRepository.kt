package com.movit.feature.train

import com.movit.shared.AppResult

interface TrainRepository {
    suspend fun getTrainDashboard(): AppResult<TrainDashboardUi>
}

class FakeTrainRepository(
    private val dashboard: TrainDashboardUi = MovitTrainPreviewData.activePlan,
    private val shouldFail: Boolean = false,
) : TrainRepository {

    override suspend fun getTrainDashboard(): AppResult<TrainDashboardUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load training dashboard.")
        } else {
            AppResult.Success(dashboard)
        }
    }
}
