package com.movit.feature.account

sealed interface MovitProfileEffect {
    data object OpenAuth : MovitProfileEffect
    data object OpenSubscription : MovitProfileEffect
    data object OpenOnboarding : MovitProfileEffect
    data object OpenAssessment : MovitProfileEffect
    data object OpenLevel : MovitProfileEffect
    data object LoggedOut : MovitProfileEffect
    data class ShowMessage(val message: String) : MovitProfileEffect
}
