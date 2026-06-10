package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.model.ExploreItemUi
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryListUiState(
    val kind: LibraryListKind,
    val isLoading: Boolean = false,
    val query: String = "",
    val selectedFilter: LibraryFilterChip = LibraryFilterChip.All,
    val filters: List<LibraryFilterChip> = emptyList(),
    val items: List<ExploreItemUi> = emptyList(),
    val totalCount: Int = 0,
    val visibleCount: Int = 0,
    val showAll: Boolean = false,
    val isFilteredEmpty: Boolean = false,
    val filterSheetVisible: Boolean = false,
    val errorMessage: String? = null,
)

class LibraryListViewModel(
    private val kind: LibraryListKind,
    private val repository: LibraryRepository = defaultLibraryRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        LibraryListUiState(
            kind = kind,
            isLoading = true,
            filters = LibraryFilterChip.defaults(kind),
        ),
    )
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
                _state.update {
                    it.copy(
                        isLoading = false,
                        filters = LibraryFilterChip.defaults(kind),
                        selectedFilter = LibraryFilterChip.All,
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

    fun onFilterSelected(filter: LibraryFilterChip) {
        _state.update { it.copy(selectedFilter = filter, filterSheetVisible = false) }
        publishFiltered()
    }

    fun onSeeMore() {
        _state.update { it.copy(showAll = true) }
        publishFiltered()
    }

    fun onFilterClick() {
        _state.update { it.copy(filterSheetVisible = true) }
    }

    fun onDismissFilterSheet() {
        _state.update { it.copy(filterSheetVisible = false) }
    }

    fun onClearFilters() {
        _state.update {
            it.copy(
                query = "",
                selectedFilter = LibraryFilterChip.All,
                filterSheetVisible = false,
            )
        }
        publishFiltered()
    }

    private fun publishFiltered() {
        val current = _state.value
        val filtered = LibraryFilterLogic.filterItems(
            items = allItems,
            kind = kind,
            chip = current.selectedFilter,
            query = current.query,
        )
        val limit = if (current.showAll) filtered.size else minOf(filtered.size, DEFAULT_VISIBLE)
        _state.update {
            it.copy(
                items = filtered.take(limit),
                totalCount = allItems.size,
                visibleCount = filtered.size,
                isFilteredEmpty = filtered.isEmpty() && allItems.isNotEmpty(),
            )
        }
    }

    companion object {
        const val DEFAULT_VISIBLE = 6
    }
}
