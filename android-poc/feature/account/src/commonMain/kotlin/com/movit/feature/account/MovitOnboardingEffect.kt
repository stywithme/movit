package com.movit.feature.account

sealed interface MovitOnboardingEffect {
    data object Completed : MovitOnboardingEffect
    data class ShowMessage(val message: String) : MovitOnboardingEffect
}
