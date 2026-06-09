package com.movit.feature.account

data class MovitProfileUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val profile: ProfileUi? = null,
    val errorMessage: String? = null,
    val showSubscription: Boolean = false,
    val isLoggingOut: Boolean = false,
)
