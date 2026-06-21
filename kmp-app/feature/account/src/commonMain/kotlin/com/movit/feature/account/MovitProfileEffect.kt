package com.movit.feature.account

sealed interface MovitProfileEffect {
    data object OpenAuth : MovitProfileEffect
    data class OpenSubscription(val restorePurchases: Boolean = false) : MovitProfileEffect
    data object OpenOnboarding : MovitProfileEffect
    data object OpenAssessment : MovitProfileEffect
    data object OpenLevel : MovitProfileEffect
    data object LoggedOut : MovitProfileEffect
    data class LanguageChanged(val languageCode: String) : MovitProfileEffect
    data class ThemeModeChanged(val themeMode: String) : MovitProfileEffect
    data class ShowMessage(val message: String) : MovitProfileEffect
    data class ShowLocalizedMessage(val key: String) : MovitProfileEffect
    /** Hidden debug entry (Profile → Training Debug Lab). */
    data object OpenTrainingDebugLab : MovitProfileEffect
}
