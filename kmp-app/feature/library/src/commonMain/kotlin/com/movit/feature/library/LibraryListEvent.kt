package com.movit.feature.library

sealed interface LibraryListEvent {
    data class QueryChanged(val query: String) : LibraryListEvent
    data class FilterSelected(val filter: LibraryFilterChip) : LibraryListEvent
    data object FilterClicked : LibraryListEvent
    data object DismissFilterSheet : LibraryListEvent
    data object LoadMore : LibraryListEvent
    data object ClearFilters : LibraryListEvent
    data class ItemClicked(val itemId: String) : LibraryListEvent
    data object RetryClicked : LibraryListEvent
}
