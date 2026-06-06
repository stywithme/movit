package com.trainingvalidator.poc.training.workout

import android.util.Log
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.WorkoutLineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ProgramWorkoutRunner - Sequential planned-workout runner (items + sets)
 */
class ProgramWorkoutRunner(
    private val items: List<ResolvedWorkoutLineItem>
) {
    companion object {
        private const val TAG = "ProgramWorkoutRunner"
        private const val DEFAULT_REST_BETWEEN_SETS_MS = 30000L
    }

    private var itemIndex = 0
    private var setIndex = 1

    private val _state = MutableStateFlow(ProgramWorkoutRunState.IDLE)
    val state: StateFlow<ProgramWorkoutRunState> = _state

    private val _currentItem = MutableStateFlow<ResolvedWorkoutLineItem?>(null)
    val currentItem: StateFlow<ResolvedWorkoutLineItem?> = _currentItem

    var onExerciseReady: ((ResolvedWorkoutLineItem.ExerciseItem, Int, Int) -> Unit)? = null
    var onRestStarted: ((durationMs: Long) -> Unit)? = null
    var onPlannedWorkoutCompleted: (() -> Unit)? = null

    fun start() {
        itemIndex = 0
        setIndex = 1
        _state.value = ProgramWorkoutRunState.PREPARING
        moveToCurrentItem()
    }

    fun markExercising() {
        _state.value = ProgramWorkoutRunState.EXERCISING
    }

    fun onExerciseCompleted() {
        val current = _currentItem.value
        if (current !is ResolvedWorkoutLineItem.ExerciseItem) {
            advanceToNextItem()
            return
        }

        val totalSets = current.item.sets?.coerceAtLeast(1) ?: 1
        if (setIndex < totalSets) {
            setIndex++
            val restMs = current.item.restBetweenSetsMs ?: DEFAULT_REST_BETWEEN_SETS_MS
            startRest(restMs)
            return
        }

        advanceToNextItem()
    }

    fun onRestCompleted() {
        _state.value = ProgramWorkoutRunState.PREPARING
        moveToCurrentItem()
    }

    private fun moveToCurrentItem() {
        if (itemIndex >= items.size) {
            completePlannedWorkout()
            return
        }

        val current = items[itemIndex]
        _currentItem.value = current

        when (current) {
            is ResolvedWorkoutLineItem.RestItem -> {
                val restMs = current.item.restDurationMs ?: 0L
                startRest(restMs)
            }
            is ResolvedWorkoutLineItem.ExerciseItem -> {
                val totalSets = current.item.sets?.coerceAtLeast(1) ?: 1
                onExerciseReady?.invoke(current, setIndex, totalSets)
            }
        }
    }

    private fun advanceToNextItem() {
        itemIndex++
        setIndex = 1
        _state.value = ProgramWorkoutRunState.PREPARING
        moveToCurrentItem()
    }

    private fun startRest(durationMs: Long) {
        if (durationMs <= 0L) {
            moveToCurrentItem()
            return
        }

        _state.value = ProgramWorkoutRunState.RESTING
        onRestStarted?.invoke(durationMs)
    }

    private fun completePlannedWorkout() {
        Log.d(TAG, "Planned workout completed")
        _state.value = ProgramWorkoutRunState.COMPLETED
        onPlannedWorkoutCompleted?.invoke()
    }
}

sealed class ResolvedWorkoutLineItem {
    data class ExerciseItem(
        val item: WorkoutLineItem,
        val exerciseConfig: ExerciseConfig
    ) : ResolvedWorkoutLineItem()

    data class RestItem(
        val item: WorkoutLineItem
    ) : ResolvedWorkoutLineItem()
}

enum class ProgramWorkoutRunState {
    IDLE,
    PREPARING,
    EXERCISING,
    RESTING,
    COMPLETED
}
