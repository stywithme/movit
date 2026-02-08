package com.trainingvalidator.poc.training.session

import android.util.Log
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.ProgramSessionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ProgramSessionRunner - Sequential session runner (items + sets)
 */
class ProgramSessionRunner(
    private val items: List<ResolvedSessionItem>
) {
    companion object {
        private const val TAG = "ProgramSessionRunner"
        private const val DEFAULT_REST_BETWEEN_SETS_MS = 30000L
    }

    private var itemIndex = 0
    private var setIndex = 1

    private val _state = MutableStateFlow(SessionRunState.IDLE)
    val state: StateFlow<SessionRunState> = _state

    private val _currentItem = MutableStateFlow<ResolvedSessionItem?>(null)
    val currentItem: StateFlow<ResolvedSessionItem?> = _currentItem

    var onExerciseReady: ((ResolvedSessionItem.ExerciseItem, Int, Int) -> Unit)? = null
    var onRestStarted: ((durationMs: Long) -> Unit)? = null
    var onSessionCompleted: (() -> Unit)? = null

    fun start() {
        itemIndex = 0
        setIndex = 1
        _state.value = SessionRunState.PREPARING
        moveToCurrentItem()
    }

    fun markExercising() {
        _state.value = SessionRunState.EXERCISING
    }

    fun onExerciseCompleted() {
        val current = _currentItem.value
        if (current !is ResolvedSessionItem.ExerciseItem) {
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
        _state.value = SessionRunState.PREPARING
        moveToCurrentItem()
    }

    private fun moveToCurrentItem() {
        if (itemIndex >= items.size) {
            completeSession()
            return
        }

        val current = items[itemIndex]
        _currentItem.value = current

        when (current) {
            is ResolvedSessionItem.RestItem -> {
                val restMs = current.item.restDurationMs ?: 0L
                startRest(restMs)
            }
            is ResolvedSessionItem.ExerciseItem -> {
                val totalSets = current.item.sets?.coerceAtLeast(1) ?: 1
                onExerciseReady?.invoke(current, setIndex, totalSets)
            }
        }
    }

    private fun advanceToNextItem() {
        itemIndex++
        setIndex = 1
        _state.value = SessionRunState.PREPARING
        moveToCurrentItem()
    }

    private fun startRest(durationMs: Long) {
        if (durationMs <= 0L) {
            moveToCurrentItem()
            return
        }

        _state.value = SessionRunState.RESTING
        onRestStarted?.invoke(durationMs)
    }

    private fun completeSession() {
        Log.d(TAG, "Session completed")
        _state.value = SessionRunState.COMPLETED
        onSessionCompleted?.invoke()
    }
}

sealed class ResolvedSessionItem {
    data class ExerciseItem(
        val item: ProgramSessionItem,
        val exerciseConfig: ExerciseConfig
    ) : ResolvedSessionItem()

    data class RestItem(
        val item: ProgramSessionItem
    ) : ResolvedSessionItem()
}

enum class SessionRunState {
    IDLE,
    PREPARING,
    EXERCISING,
    RESTING,
    COMPLETED
}
