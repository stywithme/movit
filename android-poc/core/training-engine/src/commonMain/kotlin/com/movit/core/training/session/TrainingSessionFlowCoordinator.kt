package com.movit.core.training.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Planned-workout flow semantics (legacy [WorkoutTrainingEngine] + [ProgramWorkoutRunner] subset).
 *
 * Pre-exercise → live set → optional rest → next set/exercise → workout complete.
 */
class TrainingSessionFlowCoordinator(
    private val items: List<TrainingFlowItem>,
) {
    sealed class State {
        data object Idle : State()

        data class PreExercise(
            val index: Int,
            val setNumber: Int,
            val totalSets: Int,
            val item: TrainingFlowItem.Exercise,
        ) : State()

        data class Rest(
            val index: Int,
            val setNumber: Int,
            val totalSets: Int,
            val nextExercise: TrainingFlowItem.Exercise,
            val restContext: RestContext,
            val remainingMs: Long,
            val totalMs: Long,
            val tip: String? = null,
        ) : State()

        data object Training : State()
        data object WorkoutComplete : State()
    }

    enum class RestContext {
        BETWEEN_SETS,
        BETWEEN_EXERCISES,
    }

    private var itemIndex = 0
    private var setIndex = 1
    private var previousExerciseRestMs: Long = DEFAULT_REST_BETWEEN_EXERCISES_MS

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun start() {
        itemIndex = 0
        setIndex = 1
        previousExerciseRestMs = DEFAULT_REST_BETWEEN_EXERCISES_MS
        moveToCurrentItem()
    }

    fun markExercising() {
        _state.value = State.Training
    }

    fun onExerciseCompleted() {
        val current = items.getOrNull(itemIndex) as? TrainingFlowItem.Exercise ?: run {
            advanceToNextExercise()
            return
        }
        if (setIndex < current.sets) {
            setIndex++
            enterRest(
                durationMs = current.restBetweenSetsMs,
                context = RestContext.BETWEEN_SETS,
                nextExercise = current,
            )
            return
        }
        advanceToNextExercise()
    }

    fun onRestCompleted() {
        moveToCurrentItem()
    }

    fun skipRest() {
        onRestCompleted()
    }

    /**
     * @return true when rest countdown reached zero and flow advanced to pre-exercise.
     */
    fun tickRest(deltaMs: Long): Boolean {
        val rest = _state.value as? State.Rest ?: return false
        val nextRemaining = (rest.remainingMs - deltaMs).coerceAtLeast(0L)
        if (nextRemaining > 0L) {
            _state.value = rest.copy(remainingMs = nextRemaining)
            return false
        }
        onRestCompleted()
        return true
    }

    fun currentExerciseOrNull(): TrainingFlowItem.Exercise? = when (val state = _state.value) {
        is State.PreExercise -> state.item
        is State.Rest -> state.nextExercise
        State.Training -> items.getOrNull(itemIndex) as? TrainingFlowItem.Exercise
        else -> null
    }

    private fun enterRest(
        durationMs: Long,
        context: RestContext,
        nextExercise: TrainingFlowItem.Exercise,
        tip: String? = null,
    ) {
        if (durationMs <= 0L) {
            onRestCompleted()
            return
        }
        _state.value = State.Rest(
            index = itemIndex,
            setNumber = setIndex,
            totalSets = nextExercise.sets,
            nextExercise = nextExercise,
            restContext = context,
            remainingMs = durationMs,
            totalMs = durationMs,
            tip = tip ?: defaultRestTip(context),
        )
    }

    private fun advanceToNextExercise() {
        val finished = items.getOrNull(itemIndex) as? TrainingFlowItem.Exercise
        if (finished != null) {
            previousExerciseRestMs = finished.restAfterExerciseMs
        }
        itemIndex++
        setIndex = 1
        if (itemIndex >= items.size) {
            _state.value = State.WorkoutComplete
            return
        }
        when (val next = items[itemIndex]) {
            is TrainingFlowItem.Exercise -> {
                val restMs = previousExerciseRestMs
                if (restMs > 0L) {
                    enterRest(
                        durationMs = restMs,
                        context = RestContext.BETWEEN_EXERCISES,
                        nextExercise = next,
                    )
                } else {
                    showPreExercise(next)
                }
            }
            is TrainingFlowItem.Rest -> {
                val placeholder = nextExerciseAfter(itemIndex + 1)
                enterRest(
                    durationMs = next.durationMs,
                    context = RestContext.BETWEEN_EXERCISES,
                    nextExercise = placeholder,
                )
            }
        }
    }

    private fun moveToCurrentItem() {
        if (itemIndex >= items.size) {
            _state.value = State.WorkoutComplete
            return
        }
        when (val item = items[itemIndex]) {
            is TrainingFlowItem.Exercise -> showPreExercise(item)
            is TrainingFlowItem.Rest -> {
                val placeholder = nextExerciseAfter(itemIndex + 1)
                enterRest(
                    durationMs = item.durationMs,
                    context = RestContext.BETWEEN_EXERCISES,
                    nextExercise = placeholder,
                )
            }
        }
    }

    private fun showPreExercise(item: TrainingFlowItem.Exercise) {
        _state.value = State.PreExercise(
            index = itemIndex,
            setNumber = setIndex,
            totalSets = item.sets,
            item = item,
        )
    }

    private fun nextExerciseAfter(fromIndex: Int): TrainingFlowItem.Exercise {
        for (i in fromIndex until items.size) {
            when (val item = items[i]) {
                is TrainingFlowItem.Exercise -> return item
                is TrainingFlowItem.Rest -> Unit
            }
        }
        return TrainingFlowItem.Exercise(slug = "rest", displayName = "Rest")
    }

    private fun defaultRestTip(context: RestContext): String? = when (context) {
        RestContext.BETWEEN_SETS -> "Breathe and reset before the next set."
        RestContext.BETWEEN_EXERCISES -> "Get ready for the next exercise."
    }

    companion object {
        const val REST_NEAR_END_MS = 3_000L
        private const val DEFAULT_REST_BETWEEN_EXERCISES_MS = 60_000L
    }
}

sealed class TrainingFlowItem {
    data class Exercise(
        val slug: String,
        val displayName: String,
        val sets: Int = 1,
        val targetReps: Int = 12,
        val restBetweenSetsMs: Long = 30_000L,
        val restAfterExerciseMs: Long = 60_000L,
        val tip: String? = null,
    ) : TrainingFlowItem()

    data class Rest(val durationMs: Long) : TrainingFlowItem()
}
