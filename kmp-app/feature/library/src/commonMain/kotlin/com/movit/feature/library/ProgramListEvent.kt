package com.movit.feature.library

sealed interface ProgramListEvent {
    data class QueryChanged(val query: String) : ProgramListEvent
    data class ChipSelected(val chip: String) : ProgramListEvent
    data object LoadMore : ProgramListEvent
    data class ProgramClicked(val programId: String) : ProgramListEvent
    data object RetryClicked : ProgramListEvent
}
