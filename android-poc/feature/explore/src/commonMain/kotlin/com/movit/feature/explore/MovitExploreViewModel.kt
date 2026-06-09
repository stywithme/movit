package com.movit.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovitExploreViewModel(
    private val repository: ExploreRepository = defaultExploreRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovitExploreUiState())
    val state: StateFlow<MovitExploreUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitExploreEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitExploreEffect> = _effects.asSharedFlow()

    private var cachedFeatured: List<ExploreItemUi> = emptyList()
    private var cachedWorkouts: List<ExploreItemUi> = emptyList()
    private var cachedExercises: List<ExploreItemUi> = emptyList()
    private var cachedPrograms: List<ExploreItemUi> = emptyList()

    fun loadInitial() {
        viewModelScope.launch { load(isRefresh = false) }
    }

    suspend fun load(isRefresh: Boolean) {
        _state.update {
            it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                errorMessage = null,
            )
        }
        when (val result = repository.getExploreContent()) {
            is AppResult.Success -> {
                cachedFeatured = result.value.featured
                cachedWorkouts = result.value.workouts
                cachedExercises = result.value.exercises
                cachedPrograms = result.value.programs
                publishFiltered()
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun onEvent(event: MovitExploreEvent) {
        when (event) {
            is MovitExploreEvent.QueryChanged -> {
                _state.update { it.copy(query = event.value) }
                publishFiltered()
            }
            is MovitExploreEvent.FilterSelected -> {
                _state.update { it.copy(selectedFilter = event.filter) }
                publishFiltered()
            }
            is MovitExploreEvent.ItemClicked -> {
                _effects.tryEmit(MovitExploreEffect.NavigateToItem(event.id, event.type))
            }
            MovitExploreEvent.SeeAllExercisesClicked -> {
                _effects.tryEmit(MovitExploreEffect.OpenExercisesLibrary)
            }
            MovitExploreEvent.SeeAllWorkoutsClicked -> {
                _effects.tryEmit(MovitExploreEffect.OpenWorkoutsLibrary)
            }
            MovitExploreEvent.OpenFeaturedProgramClicked -> {
                val programId = cachedPrograms.firstOrNull()?.id
                    ?: cachedFeatured.firstOrNull { it.type == ExploreItemType.Program }?.id
                if (programId != null) {
                    _effects.tryEmit(MovitExploreEffect.OpenProgramDetail(programId))
                }
            }
            MovitExploreEvent.RetryClicked,
            MovitExploreEvent.RefreshRequested,
            -> Unit
        }
    }

    private fun publishFiltered() {
        val current = _state.value
        _state.update {
            it.copy(
                featured = ExploreContentFilter.filterItems(cachedFeatured, current.query, current.selectedFilter),
                workouts = ExploreContentFilter.filterItems(cachedWorkouts, current.query, current.selectedFilter),
                exercises = ExploreContentFilter.filterItems(cachedExercises, current.query, current.selectedFilter),
                programs = ExploreContentFilter.filterItems(cachedPrograms, current.query, current.selectedFilter),
            )
        }
    }
}
