package com.movit.feature.account

import com.movit.shared.AppResult

interface OnboardingRepository {
    suspend fun putTrainingProfile(data: OnboardingData): AppResult<Unit>
}

class FakeOnboardingRepository(
    private val shouldFail: Boolean = false,
) : OnboardingRepository {
    override suspend fun putTrainingProfile(data: OnboardingData): AppResult<Unit> {
        if (!data.isStepValid(OnboardingData.STEP_SUMMARY)) {
            return AppResult.Failure("Complete all onboarding steps.")
        }
        return if (shouldFail) {
            AppResult.Failure("Unable to save training profile.")
        } else {
            AppResult.Success(Unit)
        }
    }
}
