package com.movit.feature.account

enum class AuthScreen {
    Splash,
    Intro,
    SignIn,
    SignUp,
    Forgot,
}

data class MovitAuthUiState(
    val screen: AuthScreen = AuthScreen.Splash,
    val introPage: Int = 0,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val forgotPasswordSent: Boolean = false,
    val pendingGoogleSignIn: Boolean = false,
    /** UX.7 — non-null while the guest-attribution dialog is visible. */
    val guestOutboxPromptCount: Int? = null,
    /** User id for [guestOutboxPromptCount] accept path. */
    val authenticatedUserId: String? = null,
)
