package com.movit.feature.account

import com.movit.shared.AppResult

interface OnboardingRepository {
    suspend fun putTrainingProfile(data: OnboardingData): AppResult<Unit>

    /** Local flag + backend profile check (legacy OnboardingGate parity). */
    suspend fun resolveNeedsOnboarding(): Boolean
}
