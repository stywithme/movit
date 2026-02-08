package com.trainingvalidator.poc.training.session

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.trainingvalidator.poc.training.models.ProgramSessionItem

/**
 * SessionTrainingEngine - Orchestrates an entire training session within a single Activity.
 *
 * State Machine:
 *   IDLE → PRE_EXERCISE → TRAINING → SET_REST → PRE_EXERCISE (next set) → ...
 *         → EXERCISE_REST → PRE_EXERCISE (next exercise) → ...
 *         → SESSION_COMPLETE
 *
 * This replaces the old pattern of launching separate TrainingActivity instances
 * for each exercise. The camera pipeline stays alive and TrainingEngine is recycled
 * per set/exercise.
 *
 * Responsibilities:
 *  - Track current exercise and set position
 *  - Decide rest type (between-set vs between-exercise)
 *  - Collect per-set metrics
 *  - Build session report on completion
 */
class SessionTrainingEngine(
    private val sessionItems: List<ProgramSessionItem>
) {
    companion object {
        private const val TAG = "SessionTrainingEngine"
        private const val DEFAULT_REST_BETWEEN_SETS_MS = 30000L
        private const val DEFAULT_REST_BETWEEN_EXERCISES_MS = 60000L
    }

    // ==================== State Sealed Class ====================

    sealed class State {
        object Idle : State()

        data class PreExercise(
            val exerciseIndex: Int,
            val setNumber: Int,
            val totalSets: Int,
            val item: ProgramSessionItem,
            val exerciseName: String = ""
        ) : State()

        object Training : State()

        data class SetRest(
            val durationMs: Long,
            val exerciseIndex: Int,
            val nextSetNumber: Int,
            val totalSets: Int,
            val exerciseName: String
        ) : State()

        data class ExerciseRest(
            val durationMs: Long,
            val nextExerciseIndex: Int,
            val nextExerciseName: String = ""
        ) : State()

        data class SessionComplete(
            val report: SessionReport
        ) : State()
    }

    // ==================== Metrics Data Classes ====================

    data class SetMetrics(
        val exerciseSlug: String,
        val exerciseIndex: Int,
        val setNumber: Int,
        val repsCompleted: Int,
        val durationMs: Long,
        val accuracy: Float,
        val weightKg: Float?
    )

    data class ExerciseReport(
        val exerciseSlug: String,
        val exerciseName: String,
        val setsCompleted: Int,
        val totalSets: Int,
        val totalReps: Int,
        val averageAccuracy: Float,
        val setMetrics: List<SetMetrics>
    )

    data class SessionReport(
        val totalExercises: Int,
        val totalSetsCompleted: Int,
        val totalSetsPlanned: Int,
        val totalReps: Int,
        val totalDurationMs: Long,
        val averageAccuracy: Float,
        val exerciseReports: List<ExerciseReport>
    )

    // ==================== State Flow ====================

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // ==================== Internal Data ====================

    /**
     * Indexed exercise item (exercise-only, with computed rest-after duration).
     */
    private data class IndexedItem(
        val originalIndex: Int,
        val item: ProgramSessionItem,
        val restAfterMs: Long
    )

    /** Exercise items only (rest-type items become restAfterMs on the preceding exercise). */
    private val exerciseItems: List<IndexedItem>

    /** Current position */
    private var currentExerciseIdx = 0
    private var currentSetNumber = 1

    /** Metrics collection */
    private val allSetMetrics = mutableListOf<SetMetrics>()
    private var sessionStartTimeMs: Long = 0L

    /** Exercise names resolved externally (slug → localized name). */
    private val exerciseNames = mutableMapOf<String, String>()

    // ==================== Init ====================

    init {
        val items = mutableListOf<IndexedItem>()
        var i = 0
        while (i < sessionItems.size) {
            val item = sessionItems[i]
            if (item.type == "exercise" && item.exerciseSlug != null) {
                // Look ahead for a rest item
                val nextItem = sessionItems.getOrNull(i + 1)
                val restAfter = if (nextItem?.type == "rest") {
                    i++ // consume the rest item
                    nextItem.restDurationMs ?: DEFAULT_REST_BETWEEN_EXERCISES_MS
                } else {
                    0L
                }
                items.add(IndexedItem(i, item, restAfter))
            }
            i++
        }
        exerciseItems = items
        Log.d(TAG, "Session engine created with ${exerciseItems.size} exercise items")
    }

    // ==================== Public API ====================

    /** Register a resolved exercise name (call before start). */
    fun setExerciseName(slug: String, name: String) {
        exerciseNames[slug] = name
    }

    /** Total exercise count (not including rest items). */
    fun getExerciseCount(): Int = exerciseItems.size

    /** Total sets planned across all exercises. */
    fun getTotalSetsPlanned(): Int = exerciseItems.sumOf {
        it.item.sets?.coerceAtLeast(1) ?: 1
    }

    /** Current exercise's ProgramSessionItem (or null if out of range). */
    fun getCurrentExerciseItem(): ProgramSessionItem? {
        return exerciseItems.getOrNull(currentExerciseIdx)?.item
    }

    /** Current exercise index (0-based). */
    fun getCurrentExerciseIndex(): Int = currentExerciseIdx

    /** Current set number (1-based). */
    fun getCurrentSetNumber(): Int = currentSetNumber

    /**
     * Weight for the current set. Checks weightPerSet first, falls back to weightKg.
     */
    fun getCurrentSetWeight(): Float? {
        val item = exerciseItems.getOrNull(currentExerciseIdx)?.item ?: return null
        val perSet = item.weightPerSet
        if (perSet != null && currentSetNumber <= perSet.size) {
            return perSet[currentSetNumber - 1]
        }
        return item.weightKg
    }

    /** Start the session. */
    fun start() {
        if (exerciseItems.isEmpty()) {
            Log.w(TAG, "No exercise items in session")
            _state.value = State.SessionComplete(buildReport())
            return
        }
        sessionStartTimeMs = System.currentTimeMillis()
        currentExerciseIdx = 0
        currentSetNumber = 1
        allSetMetrics.clear()
        showPreExercise()
    }

    /** Transition from PRE_EXERCISE → TRAINING after user taps "Start Set". */
    fun startTraining() {
        _state.value = State.Training
    }

    /**
     * Called when a set completes (TrainingEngine fires completion).
     * Decides: SetRest / ExerciseRest / PreExercise / SessionComplete.
     */
    fun onSetCompleted(metrics: SetMetrics) {
        allSetMetrics.add(metrics)

        val currentItem = exerciseItems[currentExerciseIdx]
        val totalSets = currentItem.item.sets?.coerceAtLeast(1) ?: 1
        val exerciseName = getExerciseName(currentItem)

        Log.d(TAG, "Set completed: ${metrics.exerciseSlug} set ${metrics.setNumber}/$totalSets")

        if (currentSetNumber < totalSets) {
            // More sets for this exercise → set rest
            currentSetNumber++
            val restMs = currentItem.item.restBetweenSetsMs ?: DEFAULT_REST_BETWEEN_SETS_MS
            if (restMs > 0) {
                _state.value = State.SetRest(
                    durationMs = restMs,
                    exerciseIndex = currentExerciseIdx,
                    nextSetNumber = currentSetNumber,
                    totalSets = totalSets,
                    exerciseName = exerciseName
                )
            } else {
                showPreExercise()
            }
        } else {
            // All sets done → next exercise
            moveToNextExercise()
        }
    }

    /** Called when rest timer expires or user skips rest. */
    fun onRestCompleted() {
        showPreExercise()
    }

    // ==================== Private ====================

    private fun showPreExercise() {
        val current = exerciseItems.getOrNull(currentExerciseIdx)
        if (current == null) {
            _state.value = State.SessionComplete(buildReport())
            return
        }
        val totalSets = current.item.sets?.coerceAtLeast(1) ?: 1
        _state.value = State.PreExercise(
            exerciseIndex = currentExerciseIdx,
            setNumber = currentSetNumber,
            totalSets = totalSets,
            item = current.item,
            exerciseName = getExerciseName(current)
        )
    }

    private fun moveToNextExercise() {
        val finishedItem = exerciseItems[currentExerciseIdx]
        currentExerciseIdx++
        currentSetNumber = 1

        if (currentExerciseIdx >= exerciseItems.size) {
            _state.value = State.SessionComplete(buildReport())
            return
        }

        val restMs = finishedItem.restAfterMs
        if (restMs > 0) {
            val nextItem = exerciseItems[currentExerciseIdx]
            _state.value = State.ExerciseRest(
                durationMs = restMs,
                nextExerciseIndex = currentExerciseIdx,
                nextExerciseName = getExerciseName(nextItem)
            )
        } else {
            showPreExercise()
        }
    }

    private fun getExerciseName(indexed: IndexedItem): String {
        val slug = indexed.item.exerciseSlug ?: return "Exercise"
        return exerciseNames[slug] ?: slug
    }

    private fun buildReport(): SessionReport {
        val totalDuration = System.currentTimeMillis() - sessionStartTimeMs

        val grouped = allSetMetrics.groupBy { it.exerciseSlug }
        val exerciseReports = exerciseItems.map { indexed ->
            val slug = indexed.item.exerciseSlug ?: return@map null
            val sets = grouped[slug] ?: emptyList()
            val totalSetsForExercise = indexed.item.sets?.coerceAtLeast(1) ?: 1
            ExerciseReport(
                exerciseSlug = slug,
                exerciseName = getExerciseName(indexed),
                setsCompleted = sets.size,
                totalSets = totalSetsForExercise,
                totalReps = sets.sumOf { it.repsCompleted },
                averageAccuracy = if (sets.isNotEmpty())
                    sets.map { it.accuracy }.average().toFloat() else 0f,
                setMetrics = sets
            )
        }.filterNotNull()

        return SessionReport(
            totalExercises = exerciseItems.size,
            totalSetsCompleted = allSetMetrics.size,
            totalSetsPlanned = getTotalSetsPlanned(),
            totalReps = allSetMetrics.sumOf { it.repsCompleted },
            totalDurationMs = totalDuration,
            averageAccuracy = if (allSetMetrics.isNotEmpty())
                allSetMetrics.map { it.accuracy }.average().toFloat() else 0f,
            exerciseReports = exerciseReports
        )
    }
}
