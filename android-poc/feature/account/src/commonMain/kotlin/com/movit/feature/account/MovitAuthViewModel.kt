package com.movit.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.shared.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovitAuthViewModel(
    private val repository: AuthRepository = defaultAuthRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovitAuthUiState())
    val state: StateFlow<MovitAuthUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitAuthEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitAuthEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            delay(SPLASH_DELAY_MS)
            if (_state.value.screen == AuthScreen.Splash) {
                _state.update { it.copy(screen = AuthScreen.Intro) }
            }
        }
    }

    fun onEvent(event: MovitAuthEvent) {
        when (event) {
            MovitAuthEvent.SplashFinished -> {
                _state.update { it.copy(screen = AuthScreen.Intro) }
            }
            MovitAuthEvent.IntroContinueClicked -> {
                val nextPage = _state.value.introPage + 1
                if (nextPage >= INTRO_PAGE_COUNT) {
                    _state.update { it.copy(screen = AuthScreen.SignIn, introPage = 0) }
                } else {
                    _state.update { it.copy(introPage = nextPage) }
                }
            }
            MovitAuthEvent.IntroSkipClicked -> {
                _state.update { it.copy(screen = AuthScreen.SignIn, introPage = 0) }
            }
            MovitAuthEvent.SignInClicked -> submitSignIn()
            MovitAuthEvent.CreateAccountClicked -> submitSignUp()
            MovitAuthEvent.ForgotPasswordLinkClicked -> {
                _state.update { it.copy(screen = AuthScreen.Forgot, errorMessage = null) }
            }
            MovitAuthEvent.ForgotSubmitClicked -> submitForgotPassword()
            MovitAuthEvent.BackFromForgotClicked -> {
                _state.update { it.copy(screen = AuthScreen.SignIn, errorMessage = null) }
            }
            MovitAuthEvent.GoToSignUpClicked -> {
                _state.update { it.copy(screen = AuthScreen.SignUp, errorMessage = null) }
            }
            MovitAuthEvent.GoToSignInClicked -> {
                _state.update { it.copy(screen = AuthScreen.SignIn, errorMessage = null) }
            }
            MovitAuthEvent.SignUpClicked -> Unit
            is MovitAuthEvent.EmailChanged -> {
                _state.update { it.copy(email = event.value, errorMessage = null) }
            }
            is MovitAuthEvent.PasswordChanged -> {
                _state.update { it.copy(password = event.value, errorMessage = null) }
            }
            is MovitAuthEvent.NameChanged -> {
                _state.update { it.copy(name = event.value, errorMessage = null) }
            }
            is MovitAuthEvent.RememberMeChanged -> {
                _state.update { it.copy(rememberMe = event.value) }
            }
        }
    }

    private fun submitSignIn() {
        val email = _state.value.email.trim()
        val password = _state.value.password
        val validationError = validateSignIn(email, password)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.login(email, password)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isLoading = false) }
                    emitPostAuthNavigation()
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    private fun submitSignUp() {
        val name = _state.value.name.trim()
        val email = _state.value.email.trim()
        val password = _state.value.password
        val validationError = validateSignUp(name, email, password)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.register(name, email, password)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isLoading = false) }
                    emitPostAuthNavigation()
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    private fun submitForgotPassword() {
        val email = _state.value.email.trim()
        if (email.isBlank()) {
            _state.update { it.copy(errorMessage = "Enter your email address.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.forgotPassword(email)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            screen = AuthScreen.SignIn,
                            infoMessage = "Reset link sent if an account exists.",
                        )
                    }
                    _effects.tryEmit(MovitAuthEffect.ShowMessage("Reset link sent if an account exists."))
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    private fun emitPostAuthNavigation() {
        val needsOnboarding = MovitData.isInstalled &&
            !MovitData.requirePlatform().isOnboardingCompleted()
        if (needsOnboarding) {
            _effects.tryEmit(MovitAuthEffect.OpenOnboarding)
        } else {
            _effects.tryEmit(MovitAuthEffect.OpenShell)
        }
    }

    companion object {
        const val SPLASH_DELAY_MS = 1_800L
        const val INTRO_PAGE_COUNT = 3

        fun validateSignIn(email: String, password: String): String? {
            if (email.isBlank()) return "Enter your email address."
            if (!email.contains("@")) return "Enter a valid email address."
            if (password.isBlank()) return "Enter your password."
            return null
        }

        fun validateSignUp(name: String, email: String, password: String): String? {
            if (name.isBlank()) return "Enter your full name."
            if (email.isBlank() || !email.contains("@")) return "Enter a valid email address."
            if (password.length < 8) return "Password must be at least 8 characters."
            return null
        }
    }
}
