package com.movit.feature.account

import com.movit.shared.AppResult

interface OnboardingRepository {
    suspend fun putTrainingProfile(data: OnboardingData): AppResult<Unit>
}
