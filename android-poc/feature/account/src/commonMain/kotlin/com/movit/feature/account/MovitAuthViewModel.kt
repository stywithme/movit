package com.movit.feature.account

import androidx.lifecycle.ViewModel
import com.movit.core.data.MovitData
import com.movit.shared.AppResult
import com.movit.shared.PlatformInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val bootstrap: AuthBootstrapContext = AuthBootstrapContext.fromMovitData(),
    initialScreen: AuthScreen = AuthScreen.Splash,
) : ViewModel() {
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val _state = MutableStateFlow(MovitAuthUiState(screen = initialScreen))
    val state: StateFlow<MovitAuthUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitAuthEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitAuthEffect> = _effects.asSharedFlow()

    init {
        if (initialScreen == AuthScreen.Splash) {
            workScope.launch {
                when (resolveBootstrapTarget(bootstrap)) {
                    AuthBootstrapTarget.ActiveSession -> emitPostAuthNavigation()
                    AuthBootstrapTarget.SignIn -> {
                        _state.update { it.copy(screen = AuthScreen.SignIn) }
                    }
                    AuthBootstrapTarget.SplashThenIntro -> {
                        delay(SPLASH_DELAY_MS)
                        if (_state.value.screen == AuthScreen.Splash) {
                            _state.update { it.copy(screen = AuthScreen.Intro) }
                        }
                    }
                }
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
                    completeIntro()
                } else {
                    _state.update { it.copy(introPage = nextPage) }
                }
            }
            MovitAuthEvent.IntroSkipClicked -> completeIntro()
            MovitAuthEvent.SignInClicked -> submitSignIn()
            MovitAuthEvent.SignUpClicked -> submitSignUp()
            MovitAuthEvent.GoogleSignInClicked -> {
                if (!PlatformInfo.supportsGoogleSignIn) {
                    _effects.tryEmit(MovitAuthEffect.ShowLocalizedMessage("auth_google_ios_blocker"))
                    return
                }
                _state.update { it.copy(pendingGoogleSignIn = true, errorMessage = null) }
            }
            is MovitAuthEvent.GoogleSignInCompleted -> completeGoogleSignIn(event.credentials)
            MovitAuthEvent.CreateAccountClicked -> submitSignUp()
            MovitAuthEvent.ForgotPasswordLinkClicked -> {
                _state.update {
                    it.copy(
                        screen = AuthScreen.Forgot,
                        errorMessage = null,
                        forgotPasswordSent = false,
                    )
                }
            }
            MovitAuthEvent.ForgotSubmitClicked -> submitForgotPassword()
            MovitAuthEvent.BackFromForgotClicked -> {
                _state.update {
                    it.copy(
                        screen = AuthScreen.SignIn,
                        errorMessage = null,
                        forgotPasswordSent = false,
                    )
                }
            }
            MovitAuthEvent.GoToSignUpClicked -> {
                _state.update { it.copy(screen = AuthScreen.SignUp, errorMessage = null) }
            }
            MovitAuthEvent.GoToSignInClicked -> {
                _state.update {
                    it.copy(
                        screen = AuthScreen.SignIn,
                        errorMessage = null,
                        infoMessage = null,
                    )
                }
            }
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

    private fun completeIntro() {
        markAuthIntroSeen(bootstrap)
        _state.update { it.copy(screen = AuthScreen.SignIn, introPage = 0) }
    }

    private fun submitSignIn() {
        val email = _state.value.email.trim()
        val password = _state.value.password
        val validationError = validateSignIn(email, password)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        workScope.launch {
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
        workScope.launch {
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

    private fun completeGoogleSignIn(credentials: GoogleSignInCredentials?) {
        _state.update { it.copy(pendingGoogleSignIn = false) }
        if (credentials == null) {
            _effects.tryEmit(MovitAuthEffect.ShowLocalizedMessage("auth_google_unavailable"))
            return
        }
        workScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.googleSignIn(credentials)) {
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
        val validationError = validateForgotPassword(email)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        workScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.forgotPassword(email)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            forgotPasswordSent = true,
                            errorMessage = null,
                        )
                    }
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

    override fun onCleared() {
        workScope.cancel()
        super.onCleared()
    }

    companion object {
        const val SPLASH_DELAY_MS = 1_800L
        const val INTRO_PAGE_COUNT = 3
        const val AUTH_PREFS_STORE = "movit_auth"
        const val AUTH_INTRO_SEEN_KEY = "intro_seen"
        fun resolveBootstrapTarget(context: AuthBootstrapContext): AuthBootstrapTarget {
            if (context.hasActiveSession) return AuthBootstrapTarget.ActiveSession
            if (!context.movitDataInstalled || context.introSeen) {
                return AuthBootstrapTarget.SignIn
            }
            return AuthBootstrapTarget.SplashThenIntro
        }

        fun isAuthIntroSeen(context: AuthBootstrapContext): Boolean = context.introSeen

        fun markAuthIntroSeen(context: AuthBootstrapContext) {
            context.markIntroSeen()
        }

        fun validateSignIn(email: String, password: String): String? {
            if (email.isBlank()) return "auth_error_email_required"
            if (!email.contains("@")) return "auth_error_email_invalid"
            if (password.isBlank()) return "auth_error_password_required"
            return null
        }

        fun validateSignUp(name: String, email: String, password: String): String? {
            if (name.isBlank()) return "auth_error_name_required"
            if (email.isBlank() || !email.contains("@")) return "auth_error_email_invalid"
            if (password.length < 8) return "auth_error_password_short"
            return null
        }

        fun validateForgotPassword(email: String): String? {
            if (email.isBlank()) return "auth_error_email_required"
            if (!email.contains("@")) return "auth_error_email_invalid"
            return null
        }
    }
}

enum class AuthBootstrapTarget {
    ActiveSession,
    SignIn,
    SplashThenIntro,
}

data class AuthBootstrapContext(
    val movitDataInstalled: Boolean,
    val hasActiveSession: Boolean,
    val introSeen: Boolean,
    private val onIntroSeen: () -> Unit = {},
) {
    fun markIntroSeen() {
        onIntroSeen()
    }

    companion object {
        fun fromMovitData(): AuthBootstrapContext {
            if (!MovitData.isInstalled) {
                return AuthBootstrapContext(
                    movitDataInstalled = false,
                    hasActiveSession = false,
                    introSeen = true,
                )
            }
            val platform = MovitData.requirePlatform()
            return AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = !platform.authHeader().isNullOrBlank(),
                introSeen = platform.readCache(MovitAuthViewModel.AUTH_PREFS_STORE, MovitAuthViewModel.AUTH_INTRO_SEEN_KEY) == "true",
                onIntroSeen = {
                    platform.writeCache(MovitAuthViewModel.AUTH_PREFS_STORE, MovitAuthViewModel.AUTH_INTRO_SEEN_KEY, "true")
                },
            )
        }
    }
}
