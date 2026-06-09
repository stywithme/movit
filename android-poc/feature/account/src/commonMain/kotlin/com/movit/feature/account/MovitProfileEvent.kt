package com.movit.feature.account

sealed interface MovitProfileEvent {
    data object RetryClicked : MovitProfileEvent
    data object SignInClicked : MovitProfileEvent
    data object ViewPlansClicked : MovitProfileEvent
    data object ManageSubscriptionClicked : MovitProfileEvent
    data object CloseSubscriptionClicked : MovitProfileEvent
    data object TrainingProfileClicked : MovitProfileEvent
    data object AssessmentClicked : MovitProfileEvent
    data object LevelClicked : MovitProfileEvent
    data object LogoutClicked : MovitProfileEvent
    data class AudioCuesChanged(val enabled: Boolean) : MovitProfileEvent
}
