package com.movit.feature.account

sealed interface ProfilePicker {
    data object Language : ProfilePicker
    data object Appearance : ProfilePicker
    data object LogoutConfirm : ProfilePicker
    data object DeleteAccountConfirm : ProfilePicker
}

data class MovitProfileUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val profile: ProfileUi? = null,
    val errorMessage: String? = null,
    val showSubscription: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val activePicker: ProfilePicker? = null,
)
