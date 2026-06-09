package com.movit.feature.account

sealed interface MovitOnboardingEvent {
    data object BackClicked : MovitOnboardingEvent
    data object ContinueClicked : MovitOnboardingEvent
    data object RetrySubmitClicked : MovitOnboardingEvent
    data class AgeChanged(val value: String) : MovitOnboardingEvent
    data class SexSelected(val value: String) : MovitOnboardingEvent
    data class HeightChanged(val value: String) : MovitOnboardingEvent
    data class WeightChanged(val value: String) : MovitOnboardingEvent
    data class ExperienceSelected(val value: String) : MovitOnboardingEvent
    data class TargetDaysChanged(val days: Int) : MovitOnboardingEvent
    data class GoalSelected(val value: String) : MovitOnboardingEvent
    data class WeekdayToggled(val day: Int) : MovitOnboardingEvent
    data class LocationSelected(val value: String) : MovitOnboardingEvent
    data class EquipmentToggled(val code: String, val enabled: Boolean) : MovitOnboardingEvent
    data class DisclaimerChanged(val accepted: Boolean) : MovitOnboardingEvent
}
