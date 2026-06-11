package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.shared.AppResult

class SharedOnboardingRepository : OnboardingRepository {

    override suspend fun putTrainingProfile(data: OnboardingData): AppResult<Unit> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        return when (val result = MovitData.account.putTrainingProfile(data.toTrainingProfileRequest())) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
    }
}
