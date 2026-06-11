package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.cache.CacheState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgramListUiState(
    val isLoading: Boolean = false,
    val programs: List<ProgramListItemUi> = emptyList(),
    val filteredPrograms: List<ProgramListItemUi> = emptyList(),
    val chips: List<String> = emptyList(),
    val selectedChip: String = "All",
    val errorMessage: String? = null,
)

class ProgramListViewModel(
    private val repository: ProgramFlowRepository = defaultProgramFlowRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ProgramListUiState(isLoading = true))
    val state: StateFlow<ProgramListUiState> = _state.asStateFlow()

    private var allPrograms: List<ProgramListItemUi> = emptyList()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        if (allPrograms.isEmpty()) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
        }
        repository.observePrograms().collect { cacheState ->
            when (cacheState) {
                is CacheState.Cached -> applyPrograms(cacheState.value)
                is CacheState.Fresh -> applyPrograms(cacheState.value)
                is CacheState.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = cacheState.message)
                }
                is CacheState.Loading -> Unit
            }
        }
    }

    private fun applyPrograms(programs: List<ProgramListItemUi>) {
        allPrograms = programs
        val chips = buildChips(allPrograms)
        _state.update {
            it.copy(
                isLoading = false,
                programs = allPrograms,
                chips = chips,
                selectedChip = chips.firstOrNull() ?: "All",
            )
        }
        publishFiltered()
    }

    fun onChipSelected(chip: String) {
        _state.update { it.copy(selectedChip = chip) }
        publishFiltered()
    }

    private fun publishFiltered() {
        val chip = _state.value.selectedChip
        val filtered = allPrograms.filter { matchesChip(it, chip) }
        _state.update { it.copy(filteredPrograms = filtered) }
    }

    private fun buildChips(programs: List<ProgramListItemUi>): List<String> {
        val levels = programs.map { it.levelLabel }.distinct().sorted()
        return listOf("All") + levels
    }

    private fun matchesChip(program: ProgramListItemUi, chip: String): Boolean {
        if (chip == "All") return true
        return program.levelLabel.equals(chip, ignoreCase = true)
    }
}
