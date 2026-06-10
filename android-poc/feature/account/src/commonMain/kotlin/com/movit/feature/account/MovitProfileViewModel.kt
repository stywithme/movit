package com.movit.feature.account

import androidx.lifecycle.ViewModel
import com.movit.shared.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovitProfileViewModel(
    private val repository: ProfileRepository = defaultProfileRepository(),
) : ViewModel() {
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val _state = MutableStateFlow(MovitProfileUiState(isLoading = true))
    val state: StateFlow<MovitProfileUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitProfileEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitProfileEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        workScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.loadProfile()) {
            is AppResult.Success -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isSignedIn = true,
                        profile = result.value,
                        errorMessage = null,
                    )
                }
            }
            is AppResult.Failure -> {
                val signedOut = result.message.contains("Sign in", ignoreCase = true)
                _state.update {
                    it.copy(
                        isLoading = false,
                        isSignedIn = !signedOut,
                        profile = null,
                        errorMessage = if (signedOut) null else result.message,
                    )
                }
            }
        }
    }

    fun onEvent(event: MovitProfileEvent) {
        when (event) {
            MovitProfileEvent.RetryClicked -> Unit
            MovitProfileEvent.SignInClicked -> _effects.tryEmit(MovitProfileEffect.OpenAuth)
            MovitProfileEvent.ViewPlansClicked -> openSubscription()
            MovitProfileEvent.ManageSubscriptionClicked -> openSubscription()
            MovitProfileEvent.CloseSubscriptionClicked -> {
                _state.update { it.copy(showSubscription = false) }
            }
            MovitProfileEvent.EditProfileClicked -> {
                _effects.tryEmit(MovitProfileEffect.ShowMessage("Edit profile coming soon."))
            }
            MovitProfileEvent.TrainingProfileClicked -> {
                _effects.tryEmit(MovitProfileEffect.OpenOnboarding)
            }
            MovitProfileEvent.AssessmentClicked -> {
                _effects.tryEmit(MovitProfileEffect.OpenAssessment)
            }
            MovitProfileEvent.LevelClicked -> {
                _effects.tryEmit(MovitProfileEffect.OpenLevel)
            }
            MovitProfileEvent.LanguageClicked -> {
                _state.update { it.copy(activePicker = ProfilePicker.Language) }
            }
            MovitProfileEvent.AppearanceClicked -> {
                _state.update { it.copy(activePicker = ProfilePicker.Appearance) }
            }
            MovitProfileEvent.LogoutClicked -> {
                _state.update { it.copy(activePicker = ProfilePicker.LogoutConfirm) }
            }
            MovitProfileEvent.LogoutConfirmed -> {
                _state.update { it.copy(activePicker = null) }
                logout()
            }
            MovitProfileEvent.LogoutDismissed,
            MovitProfileEvent.PickerDismissed,
            -> {
                _state.update { it.copy(activePicker = null) }
            }
            is MovitProfileEvent.LanguageSelected -> selectLanguage(event.languageCode)
            is MovitProfileEvent.AppearanceSelected -> selectAppearance(event.themeMode)
            is MovitProfileEvent.AudioCuesChanged -> toggleAudioCues(event.enabled)
            is MovitProfileEvent.HapticChanged -> toggleHaptic(event.enabled)
        }
    }

    private fun openSubscription() {
        _state.update { it.copy(showSubscription = true) }
        _effects.tryEmit(MovitProfileEffect.OpenSubscription)
    }

    private fun logout() {
        workScope.launch {
            _state.update { it.copy(isLoggingOut = true) }
            when (repository.logout()) {
                is AppResult.Success -> {
                    _state.update {
                        MovitProfileUiState(
                            isLoading = false,
                            isSignedIn = false,
                        )
                    }
                    _effects.tryEmit(MovitProfileEffect.LoggedOut)
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isLoggingOut = false) }
                    _effects.tryEmit(MovitProfileEffect.ShowMessage("Unable to sign out."))
                }
            }
        }
    }

    private fun selectLanguage(languageCode: String) {
        workScope.launch {
            _state.update { it.copy(activePicker = null) }
            when (
                val result = repository.updateSettings(
                    ProfileSettingsUpdate(preferredLanguage = languageCode),
                )
            ) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(profile = result.value)
                    }
                    _effects.tryEmit(MovitProfileEffect.LanguageChanged(languageCode))
                }
                is AppResult.Failure -> {
                    _effects.tryEmit(MovitProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    private fun selectAppearance(themeMode: String) {
        workScope.launch {
            _state.update { it.copy(activePicker = null) }
            when (val result = repository.setThemeMode(themeMode)) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(profile = result.value)
                    }
                    _effects.tryEmit(MovitProfileEffect.ThemeModeChanged(themeMode))
                }
                is AppResult.Failure -> {
                    _effects.tryEmit(MovitProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    private fun toggleAudioCues(enabled: Boolean) {
        workScope.launch {
            when (val result = repository.updateSettings(ProfileSettingsUpdate(voiceFeedback = enabled))) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(profile = result.value)
                    }
                }
                is AppResult.Failure -> {
                    _effects.tryEmit(MovitProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    private fun toggleHaptic(enabled: Boolean) {
        workScope.launch {
            when (val result = repository.updateSettings(ProfileSettingsUpdate(notifications = enabled))) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(profile = result.value)
                    }
                }
                is AppResult.Failure -> {
                    _effects.tryEmit(MovitProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    override fun onCleared() {
        workScope.cancel()
        super.onCleared()
    }
}
