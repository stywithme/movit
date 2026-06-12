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
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
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

    private var visibleLimit = PAGE_SIZE

    fun onQueryChange(query: String) {
        visibleLimit = PAGE_SIZE
        _state.update { it.copy(query = query) }
        publishFiltered()
    }

    fun onFilterSelected(filter: LibraryFilterChip) {
        visibleLimit = PAGE_SIZE
        _state.update { it.copy(selectedFilter = filter, filterSheetVisible = false) }
        publishFiltered()
    }

    fun onLoadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        visibleLimit += PAGE_SIZE
        publishFiltered()
        _state.update { it.copy(isLoadingMore = false) }
    }

    fun onFilterClick() {
        _state.update { it.copy(filterSheetVisible = true) }
    }

    fun onDismissFilterSheet() {
        _state.update { it.copy(filterSheetVisible = false) }
    }

    fun onClearFilters() {
        visibleLimit = PAGE_SIZE
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
        val limit = minOf(filtered.size, visibleLimit)
        _state.update {
            it.copy(
                items = filtered.take(limit),
                totalCount = allItems.size,
                visibleCount = filtered.size,
                hasMore = limit < filtered.size,
                isFilteredEmpty = filtered.isEmpty() && allItems.isNotEmpty(),
            )
        }
    }

    companion object {
        const val PAGE_SIZE = 20
        const val DEFAULT_VISIBLE = PAGE_SIZE
    }
}
