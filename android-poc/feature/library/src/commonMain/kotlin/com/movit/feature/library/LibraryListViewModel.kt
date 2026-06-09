package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.feature.explore.ExploreContentFilter
import com.movit.feature.explore.ExploreItemUi
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryListKind {
    Exercises,
    Workouts,
}

data class LibraryListUiState(
    val kind: LibraryListKind,
    val isLoading: Boolean = false,
    val query: String = "",
    val selectedChip: String = "All",
    val chips: List<String> = emptyList(),
    val items: List<ExploreItemUi> = emptyList(),
    val totalCount: Int = 0,
    val visibleCount: Int = 0,
    val showAll: Boolean = false,
    val errorMessage: String? = null,
)

class LibraryListViewModel(
    private val kind: LibraryListKind,
    private val repository: LibraryRepository = defaultLibraryRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryListUiState(kind = kind, isLoading = true))
    val state: StateFlow<LibraryListUiState> = _state.asStateFlow()

    private var allItems: List<ExploreItemUi> = emptyList()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.loadContent()) {
            is AppResult.Success -> {
                allItems = when (kind) {
                    LibraryListKind.Exercises -> result.value.exercises
                    LibraryListKind.Workouts -> result.value.workouts
                }
                val chips = buildChips(allItems)
                _state.update {
                    it.copy(
                        isLoading = false,
                        chips = chips,
                        selectedChip = chips.firstOrNull() ?: "All",
                    )
                }
                publishFiltered()
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        publishFiltered()
    }

    fun onChipSelected(chip: String) {
        _state.update { it.copy(selectedChip = chip) }
        publishFiltered()
    }

    fun onSeeMore() {
        _state.update { it.copy(showAll = true) }
        publishFiltered()
    }

    private fun publishFiltered() {
        val current = _state.value
        val filtered = allItems
            .asSequence()
            .filter { matchesChip(it, current.selectedChip) }
            .filter { ExploreContentFilter.matchesQuery(it, current.query) }
            .toList()
        val limit = if (current.showAll) filtered.size else minOf(filtered.size, DEFAULT_VISIBLE)
        _state.update {
            it.copy(
                items = filtered.take(limit),
                totalCount = allItems.size,
                visibleCount = filtered.size,
            )
        }
    }

    private fun buildChips(items: List<ExploreItemUi>): List<String> {
        val base = when (kind) {
            LibraryListKind.Exercises -> listOf("All", "Lower body", "Core", "Mobility", "Equipment")
            LibraryListKind.Workouts -> listOf("All", "Legs", "Core", "Chest", "Mobility", "Under 20 min")
        }
        return base
    }

    private fun matchesChip(item: ExploreItemUi, chip: String): Boolean {
        if (chip == "All") return true
        val haystack = (item.title + " " + item.subtitle + " " + item.metadata.joinToString()).lowercase()
        return when (chip) {
            "Lower body", "Legs" -> haystack.contains("leg") || haystack.contains("squat") || haystack.contains("lunge")
            "Core" -> haystack.contains("core")
            "Mobility" -> haystack.contains("mobil") || haystack.contains("recovery")
            "Chest" -> haystack.contains("chest") || haystack.contains("push")
            "Equipment" -> haystack.contains("dumbbell") || haystack.contains("barbell") || haystack.contains("weight")
            "Under 20 min" -> item.metadata.any { it.contains("18") || it.contains("12") || it.contains("15") }
            else -> true
        }
    }

    companion object {
        private const val DEFAULT_VISIBLE = 6
    }
}
