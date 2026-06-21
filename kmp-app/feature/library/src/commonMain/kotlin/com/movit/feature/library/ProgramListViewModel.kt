package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.cache.CacheState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgramListUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val programs: List<ProgramListItemUi> = emptyList(),
    val filteredPrograms: List<ProgramListItemUi> = emptyList(),
    val visiblePrograms: List<ProgramListItemUi> = emptyList(),
    val chips: List<String> = emptyList(),
    val selectedChip: String = "All",
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
)

class ProgramListViewModel(
    private val repository: ProgramFlowRepository = defaultProgramFlowRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ProgramListUiState(isLoading = true))
    val state: StateFlow<ProgramListUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ProgramListEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ProgramListEffect> = _effects.asSharedFlow()

    private var allPrograms: List<ProgramListItemUi> = emptyList()
    private var visibleLimit = PAGE_SIZE

    fun onEvent(event: ProgramListEvent) {
        when (event) {
            is ProgramListEvent.QueryChanged -> onQueryChange(event.query)
            is ProgramListEvent.ChipSelected -> onChipSelected(event.chip)
            ProgramListEvent.LoadMore -> onLoadMore()
            is ProgramListEvent.ProgramClicked -> {
                if (event.programId.isNotBlank()) {
                    _effects.tryEmit(ProgramListEffect.OpenProgram(event.programId))
                }
            }
            ProgramListEvent.RetryClicked -> Unit
        }
    }

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

    fun onQueryChange(query: String) {
        visibleLimit = PAGE_SIZE
        _state.update { it.copy(query = query) }
        publishFiltered()
    }

    fun onChipSelected(chip: String) {
        visibleLimit = PAGE_SIZE
        _state.update { it.copy(selectedChip = chip) }
        publishFiltered()
    }

    fun onLoadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        visibleLimit += PAGE_SIZE
        publishFiltered()
        _state.update { it.copy(isLoadingMore = false) }
    }

    private fun publishFiltered() {
        val current = _state.value
        val chip = current.selectedChip
        val filtered = allPrograms
            .filter { matchesChip(it, chip) }
            .filter { matchesQuery(it, current.query) }
        val limit = minOf(filtered.size, visibleLimit)
        _state.update {
            it.copy(
                filteredPrograms = filtered,
                visiblePrograms = filtered.take(limit),
                hasMore = limit < filtered.size,
            )
        }
    }

    private fun buildChips(programs: List<ProgramListItemUi>): List<String> {
        val levels = programs.map { it.levelLabel }.distinct().sorted()
        return listOf("All") + levels
    }

    private fun matchesChip(program: ProgramListItemUi, chip: String): Boolean {
        if (chip == "All") return true
        return program.levelLabel.equals(chip, ignoreCase = true)
    }

    private fun matchesQuery(program: ProgramListItemUi, query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim()
        return program.title.contains(needle, ignoreCase = true) ||
            program.description.contains(needle, ignoreCase = true)
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
