package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.cache.CacheState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgramWeekPlanUiState(
    val isLoading: Boolean = false,
    val weekPlan: ProgramWeekPlanUi? = null,
    val errorMessage: String? = null,
)

class ProgramWeekPlanViewModel(
    private val programId: String,
    private val weekNumber: Int,
    private val repository: ProgramFlowRepository = defaultProgramFlowRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ProgramWeekPlanUiState(isLoading = true))
    val state: StateFlow<ProgramWeekPlanUiState> = _state.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        if (_state.value.weekPlan == null) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
        }
        repository.observeWeekPlan(programId, weekNumber).collect { cacheState ->
            when (cacheState) {
                is CacheState.Cached -> _state.update { it.copy(isLoading = false, weekPlan = cacheState.value) }
                is CacheState.Fresh -> _state.update { it.copy(isLoading = false, weekPlan = cacheState.value) }
                is CacheState.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = cacheState.message)
                }
                is CacheState.Loading -> Unit
            }
        }
    }
}
