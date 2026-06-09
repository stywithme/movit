package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.feature.explore.ExploreItemUi
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProgramDetailTab {
    Overview,
    Edit,
}

data class ProgramWeekUi(
    val label: String,
    val subtitle: String,
    val progressPercent: Int,
    val isActive: Boolean,
)

data class ProgramDetailUiState(
    val isLoading: Boolean = false,
    val program: ExploreItemUi? = null,
    val selectedTab: ProgramDetailTab = ProgramDetailTab.Overview,
    val weeks: List<ProgramWeekUi> = emptyList(),
    val errorMessage: String? = null,
)

class ProgramDetailViewModel(
    private val programId: String,
    private val repository: LibraryRepository = defaultLibraryRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ProgramDetailUiState(isLoading = true))
    val state: StateFlow<ProgramDetailUiState> = _state.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.loadContent()) {
            is AppResult.Success -> {
                val program = result.value.programs.firstOrNull { it.id == programId }
                    ?: result.value.featured.firstOrNull { it.id == programId }
                    ?: repository.findItem(programId)
                if (program == null) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Program not found.")
                    }
                } else {
                    val weeks = buildWeeks(program)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            program = program,
                            weeks = weeks,
                        )
                    }
                }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onTabSelected(tab: ProgramDetailTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    private fun buildWeeks(program: ExploreItemUi): List<ProgramWeekUi> {
        val weeksCount = program.metadata.firstOrNull { it.contains("week") }
            ?.filter { it.isDigit() }
            ?.toIntOrNull() ?: 4
        return (1..weeksCount).map { index ->
            ProgramWeekUi(
                label = "Week $index",
                subtitle = if (index == 1) "Foundation" else "Progressive load",
                progressPercent = when {
                    index == 1 -> 62
                    index < weeksCount -> 0
                    else -> 0
                },
                isActive = index == 1,
            )
        }
    }
}
