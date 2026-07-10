package com.movit.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.repository.ExploreContentSource
import com.movit.core.data.repository.defaultExploreContentSource
import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import com.movit.shared.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MovitExploreViewModel(
    private val repository: ExploreContentSource = defaultExploreContentSource(),
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
        if (isRefresh) {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            when (val result = withContext(Dispatchers.Default) { repository.refreshExploreContent() }) {
                is AppResult.Success -> {
                    applyContent(result.value)
                    _state.update { it.copy(isRefreshing = false, errorMessage = null) }
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = if (hasCachedContent()) it.errorMessage else result.message,
                    )
                }
            }
            return
        }

        if (!hasCachedContent()) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
        } else {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
        }

        repository.observeExploreContent().collect { cacheState ->
            when (cacheState) {
                is CacheState.Cached -> applyContent(cacheState.value)
                is CacheState.Fresh -> applyContent(cacheState.value)
                is CacheState.Error -> _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = cacheState.message,
                    )
                }
                is CacheState.Loading -> Unit
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
                _effects.tryEmit(MovitExploreEffect.OpenExercisesLibrary)
            }
            MovitExploreEvent.SeeAllWorkoutsClicked -> {
                _effects.tryEmit(MovitExploreEffect.OpenWorkoutsLibrary)
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
            MovitExploreEvent.RetryClicked -> {
                viewModelScope.launch { load(isRefresh = false) }
            }
            MovitExploreEvent.RefreshRequested -> {
                viewModelScope.launch { load(isRefresh = true) }
            }
        }
    }

    private fun applyContent(content: com.movit.core.model.ExploreContent) {
        cachedFeatured = content.featured
        cachedWorkouts = content.workouts
        cachedExercises = content.exercises
        cachedPrograms = content.programs
        publishFiltered()
        _state.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = null,
                isOffline = MovitData.isInstalled && !MovitData.requirePlatform().isNetworkAvailable(),
            )
        }
    }

    private fun hasCachedContent(): Boolean =
        cachedFeatured.isNotEmpty() ||
            cachedWorkouts.isNotEmpty() ||
            cachedExercises.isNotEmpty() ||
            cachedPrograms.isNotEmpty()

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
