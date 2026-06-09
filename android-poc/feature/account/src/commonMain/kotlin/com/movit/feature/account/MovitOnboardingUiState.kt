package com.movit.feature.account

data class MovitOnboardingUiState(
    val step: Int = OnboardingData.STEP_AGE_GENDER,
    val data: OnboardingData = OnboardingData(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canContinue: Boolean
        get() = data.isStepValid(step) && !isSubmitting

    val progressPercent: Int
        get() = ((step + 1) * 100) / OnboardingData.STEP_COUNT
}
