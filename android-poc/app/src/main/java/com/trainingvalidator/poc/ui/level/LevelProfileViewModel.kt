package com.trainingvalidator.poc.ui.level

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.LevelProfileData
import com.trainingvalidator.poc.storage.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class LevelProfileUiState {
    object Loading : LevelProfileUiState()
    data class Success(
        val profile: LevelProfileData,
        val previousProfile: LevelProfileData?
    ) : LevelProfileUiState()
    data class NoProfile(val reason: String) : LevelProfileUiState()
    data class Error(val message: String) : LevelProfileUiState()
    object NoAuth : LevelProfileUiState()
}

class LevelProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<LevelProfileUiState>(LevelProfileUiState.Loading)
    val uiState: StateFlow<LevelProfileUiState> = _uiState.asStateFlow()

    fun fetchLevelProfile() {
        _uiState.value = LevelProfileUiState.Loading
        viewModelScope.launch {
            try {
                val authHeader = AuthManager.getAuthHeader(getApplication())
                if (authHeader == null) {
                    _uiState.value = LevelProfileUiState.NoAuth
                    return@launch
                }

                val response = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getLevelProfile(authHeader)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        var previousProfile: LevelProfileData? = null
                        try {
                            val historyResponse = withContext(Dispatchers.IO) {
                                ApiClient.mobileSyncApi.getLevelProfileHistory(authHeader)
                            }
                            if (historyResponse.isSuccessful) {
                                val history = historyResponse.body()?.data
                                // history[0] = current, history[1] = previous
                                if (history != null && history.size >= 2) {
                                    previousProfile = history[1]
                                }
                            }
                        } catch (_: Exception) { }

                        _uiState.value = LevelProfileUiState.Success(data, previousProfile)
                    } else {
                        _uiState.value = LevelProfileUiState.NoProfile("no_data")
                    }
                } else {
                    val error = response.body()?.error ?: "failed"
                    _uiState.value = LevelProfileUiState.NoProfile(error)
                }
            } catch (e: Exception) {
                _uiState.value = LevelProfileUiState.Error(e.message ?: "unknown")
            }
        }
    }
}
