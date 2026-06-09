package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.shared.AppResult

class SharedOnboardingRepository(
    private val fallback: OnboardingRepository = FakeOnboardingRepository(),
) : OnboardingRepository {

    override suspend fun putTrainingProfile(data: OnboardingData): AppResult<Unit> {
        if (!MovitData.isInstalled) {
            return fallback.putTrainingProfile(data)
        }
        return when (val result = MovitData.account.putTrainingProfile(data.toTrainingProfileRequest())) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }
}
