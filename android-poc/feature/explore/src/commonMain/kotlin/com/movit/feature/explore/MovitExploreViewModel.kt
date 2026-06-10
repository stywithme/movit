package com.movit.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.repository.defaultExploreRepository
import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import com.movit.core.model.ExploreRepository
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
            is MovitExploreEvent.WorkoutFilterSelected -> {
                _state.update { it.copy(selectedWorkoutFilter = event.filter) }
                publishFiltered()
            }
            is MovitExploreEvent.ExerciseCategorySelected -> {
                _state.update { it.copy(selectedExerciseCategory = event.categoryCode) }
                publishFiltered()
            }
            MovitExploreEvent.FilterButtonClicked -> {
                _state.update { it.copy(secondaryFiltersVisible = !it.secondaryFiltersVisible) }
            }
            is MovitExploreEvent.ItemClicked -> {
                when (event.type) {
                    ExploreItemType.Workout ->
                        _effects.tryEmit(MovitExploreEffect.OpenWorkoutSession(event.id))
                    ExploreItemType.Exercise ->
                        _effects.tryEmit(MovitExploreEffect.OpenExercisePrepare(event.id))
                    ExploreItemType.Program ->
                        _effects.tryEmit(MovitExploreEffect.OpenProgramDetail(event.id))
                }
            }
            MovitExploreEvent.SeeAllExercisesClicked -> {
                _state.update {
                    it.copy(
                        selectedFilter = ExploreFilter.Exercises,
                        selectedExerciseCategory = null,
                        scrollToExercises = true,
                    )
                }
                publishFiltered()
            }
            MovitExploreEvent.SeeAllWorkoutsClicked -> {
                _state.update {
                    it.copy(
                        selectedFilter = ExploreFilter.Workouts,
                        selectedWorkoutFilter = ExploreWorkoutFilter.All,
                        scrollToWorkouts = true,
                    )
                }
                publishFiltered()
            }
            MovitExploreEvent.SeeAllProgramsClicked -> {
                _effects.tryEmit(MovitExploreEffect.OpenProgramList)
            }
            MovitExploreEvent.OpenFeaturedProgramClicked -> {
                val programId = cachedPrograms.firstOrNull()?.id
                    ?: cachedFeatured.firstOrNull { it.type == ExploreItemType.Program }?.id
                if (programId != null) {
                    _effects.tryEmit(MovitExploreEffect.OpenProgramDetail(programId))
                }
            }
            MovitExploreEvent.ScrollToExercisesHandled -> {
                _state.update { it.copy(scrollToExercises = false) }
            }
            MovitExploreEvent.ScrollToWorkoutsHandled -> {
                _state.update { it.copy(scrollToWorkouts = false) }
            }
            MovitExploreEvent.RetryClicked,
            MovitExploreEvent.RefreshRequested,
            -> Unit
        }
    }

    private fun publishFiltered() {
        val current = _state.value
        val categoryChips = ExploreContentFilter.buildExerciseCategoryChips(cachedExercises)
        val selectedCategory = current.selectedExerciseCategory?.takeIf { code ->
            categoryChips.any { chip ->
                chip.code != null && chip.code.equals(code, ignoreCase = true)
            }
        }
        val filteredWorkouts = ExploreContentFilter.filterItems(
            items = cachedWorkouts,
            query = current.query,
            filter = current.selectedFilter,
            workoutFilter = current.selectedWorkoutFilter,
        )
        val filteredExercises = ExploreContentFilter.filterItems(
            items = cachedExercises,
            query = current.query,
            filter = current.selectedFilter,
            exerciseCategoryCode = selectedCategory,
        )
        _state.update {
            it.copy(
                featured = ExploreContentFilter.filterItems(cachedFeatured, current.query, current.selectedFilter),
                workouts = filteredWorkouts,
                exercises = filteredExercises,
                programs = ExploreContentFilter.filterItems(cachedPrograms, current.query, current.selectedFilter),
                exerciseCategoryChips = categoryChips,
                selectedExerciseCategory = selectedCategory,
                filteredWorkoutCount = filteredWorkouts.size,
                filteredExerciseCount = filteredExercises.size,
            )
        }
    }
}
