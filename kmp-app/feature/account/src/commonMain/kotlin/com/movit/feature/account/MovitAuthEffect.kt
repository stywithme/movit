package com.movit.feature.account

sealed interface MovitAuthEffect {
    data object OpenShell : MovitAuthEffect
    data object OpenOnboarding : MovitAuthEffect
    data class ShowMessage(val message: String) : MovitAuthEffect
    data class ShowLocalizedMessage(val key: String) : MovitAuthEffect
}
