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

class MovitLevelViewModel(
    private val repository: LevelRepository = defaultLevelRepository(),
    private val celebrationPreferences: LevelCelebrationPreferences = LevelCelebrationPreferences.fromMovitData(),
) : ViewModel() {
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val _state = MutableStateFlow(MovitLevelUiState(isLoading = true))
    val state: StateFlow<MovitLevelUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitLevelEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitLevelEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        workScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.fetchLevelProfile()) {
            is AppResult.Success -> {
                val profile = result.value
                val lastSeenLevel = celebrationPreferences.lastSeenLevel()
                val celebration = if (lastSeenLevel > 0 && profile.levelNumber > lastSeenLevel) {
                    LevelUpCelebrationUi(
                        fromLevel = lastSeenLevel,
                        toLevel = profile.levelNumber,
                        levelName = profile.levelName,
                    )
                } else {
                    null
                }
                celebrationPreferences.markLevelSeen(profile.levelNumber)
                _state.update {
                    it.copy(
                        isLoading = false,
                        profile = profile,
                        errorMessage = null,
                        showNoProfile = false,
                        levelUpCelebration = celebration,
                    )
                }
            }
            is AppResult.Failure -> {
                val noProfile = LevelProfileLoadErrors.isNoProfile(result.message)
                _state.update {
                    it.copy(
                        isLoading = false,
                        profile = null,
                        errorMessage = if (noProfile) {
                            null
                        } else {
                            result.message
                        },
                        showNoProfile = noProfile,
                    )
                }
            }
        }
    }

    fun onEvent(event: MovitLevelEvent) {
        when (event) {
            MovitLevelEvent.RetryClicked -> {
                workScope.launch { load() }
            }
            is MovitLevelEvent.TabSelected -> {
                _state.update { it.copy(selectedTab = event.tab) }
            }
            MovitLevelEvent.StartScanClicked -> {
                val mode = if ((_state.value.profile?.levelNumber ?: 0) > 0) {
                    "progression"
                } else {
                    "initial"
                }
                _effects.tryEmit(MovitLevelEffect.OpenAssessment(mode = mode))
            }
            MovitLevelEvent.BrowseProgramsClicked -> {
                _effects.tryEmit(MovitLevelEffect.OpenExplore)
            }
            MovitLevelEvent.DismissLevelUpCelebration -> {
                _state.update { it.copy(levelUpCelebration = null) }
            }
        }
    }

    override fun onCleared() {
        workScope.cancel()
        super.onCleared()
    }
}
