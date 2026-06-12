package com.movit.feature.account

sealed interface MovitProfileEvent {
    data object RetryClicked : MovitProfileEvent
    data object SignInClicked : MovitProfileEvent
    data object ViewPlansClicked : MovitProfileEvent
    data object ManageSubscriptionClicked : MovitProfileEvent
    data object SubscribeNowClicked : MovitProfileEvent
    data object RestorePurchasesClicked : MovitProfileEvent
    data object CloseSubscriptionClicked : MovitProfileEvent
    data object EditProfileClicked : MovitProfileEvent
    data object TrainingProfileClicked : MovitProfileEvent
    data object AssessmentClicked : MovitProfileEvent
    data object LevelClicked : MovitProfileEvent
    data object LanguageClicked : MovitProfileEvent
    data object AppearanceClicked : MovitProfileEvent
    data object LogoutClicked : MovitProfileEvent
    data object LogoutConfirmed : MovitProfileEvent
    data object LogoutDismissed : MovitProfileEvent
    data object PickerDismissed : MovitProfileEvent
    data class LanguageSelected(val languageCode: String) : MovitProfileEvent
    data class AppearanceSelected(val themeMode: String) : MovitProfileEvent
    data class AudioCuesChanged(val enabled: Boolean) : MovitProfileEvent
    data class HapticChanged(val enabled: Boolean) : MovitProfileEvent
}
