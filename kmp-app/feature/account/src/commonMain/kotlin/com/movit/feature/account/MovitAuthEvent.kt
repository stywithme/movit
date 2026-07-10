package com.movit.feature.account

sealed interface MovitAuthEvent {
    data object SplashFinished : MovitAuthEvent
    data object IntroContinueClicked : MovitAuthEvent
    data object IntroSkipClicked : MovitAuthEvent
    data object SignInClicked : MovitAuthEvent
    data object SignUpClicked : MovitAuthEvent
    data object GoogleSignInClicked : MovitAuthEvent
    data object CreateAccountClicked : MovitAuthEvent
    data object ForgotPasswordLinkClicked : MovitAuthEvent
    data object ForgotSubmitClicked : MovitAuthEvent
    data object BackFromForgotClicked : MovitAuthEvent
    data object GoToSignUpClicked : MovitAuthEvent
    data object GoToSignInClicked : MovitAuthEvent
    data class EmailChanged(val value: String) : MovitAuthEvent
    data class PasswordChanged(val value: String) : MovitAuthEvent
    data class NameChanged(val value: String) : MovitAuthEvent
    data class RememberMeChanged(val value: Boolean) : MovitAuthEvent
    data class GoogleSignInCompleted(val credentials: GoogleSignInCredentials?) : MovitAuthEvent
    data object GuestOutboxAcceptClicked : MovitAuthEvent
    data object GuestOutboxDiscardClicked : MovitAuthEvent
}
