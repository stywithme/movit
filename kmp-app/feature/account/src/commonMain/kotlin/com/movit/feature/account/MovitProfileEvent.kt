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
    data object LogoutUploadThenSignOut : MovitProfileEvent
    data object LogoutDiscardPending : MovitProfileEvent
    data object LogoutDismissed : MovitProfileEvent
    data object DeleteAccountClicked : MovitProfileEvent
    data object DeleteAccountConfirmed : MovitProfileEvent
    data object DeleteAccountUploadThenDelete : MovitProfileEvent
    data object DeleteAccountDiscardPending : MovitProfileEvent
    data object DeleteAccountDismissed : MovitProfileEvent
    data object PickerDismissed : MovitProfileEvent
    data class LanguageSelected(val languageCode: String) : MovitProfileEvent
    data class AppearanceSelected(val themeMode: String) : MovitProfileEvent
    data class AudioCuesChanged(val enabled: Boolean) : MovitProfileEvent
    data class HapticChanged(val enabled: Boolean) : MovitProfileEvent
    data object TrainingDebugLabClicked : MovitProfileEvent
    data object SyncRetryClicked : MovitProfileEvent
    data object SyncRepairCatalogClicked : MovitProfileEvent
    data object SyncNowClicked : MovitProfileEvent
}
