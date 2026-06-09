package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExercisePrepareUi(
    val id: String,
    val name: String,
    val category: String,
    val sets: String,
    val reps: String,
    val rest: String,
    val equipment: String,
    val axesLabel: String,
    val distanceTip: String,
    val sessionProgressPercent: Int,
)

data class ExercisePrepareUiState(
    val isLoading: Boolean = false,
    val exercise: ExercisePrepareUi? = null,
    val errorMessage: String? = null,
)

class ExercisePrepareViewModel(
    private val exerciseId: String,
    private val repository: LibraryRepository = defaultLibraryRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ExercisePrepareUiState(isLoading = true))
    val state: StateFlow<ExercisePrepareUiState> = _state.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val exercise = buildExercise(exerciseId)
        if (exercise == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "Exercise not found.")
            }
        } else {
            _state.update {
                it.copy(isLoading = false, exercise = exercise)
            }
        }
    }

    private suspend fun buildExercise(exerciseId: String): ExercisePrepareUi? {
        val preview = ExercisePreparePreviewData.byId(exerciseId)
        if (preview != null) return preview
        val item = repository.findItem(exerciseId) ?: return null
        return ExercisePrepareUi(
            id = item.id,
            name = item.title,
            category = item.subtitle,
            sets = "3",
            reps = "12",
            rest = "60s",
            equipment = item.metadata.firstOrNull() ?: "None",
            axesLabel = "Front · Side · 45°",
            distanceTip = "Stand ~2 m from the camera, full body in frame.",
            sessionProgressPercent = 20,
        )
    }
}

private object ExercisePreparePreviewData {
    fun byId(id: String): ExercisePrepareUi? = when (id) {
        "ex-squat", "ex-squat-warm" -> preview.copy(id = id, name = "Bodyweight Squat")
        else -> preview.takeIf { id == "preview" }
    }

    val preview = ExercisePrepareUi(
        id = "preview",
        name = "Bodyweight Squat",
        category = "Lower Body · Quads & Glutes focus",
        sets = "3",
        reps = "15",
        rest = "30s",
        equipment = "None",
        axesLabel = "Front · Side · 45°",
        distanceTip = "Stand ~2 m from the camera, full body in frame.",
        sessionProgressPercent = 20,
    )
}
