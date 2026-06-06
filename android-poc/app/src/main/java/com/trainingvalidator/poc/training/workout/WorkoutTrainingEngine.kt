package com.trainingvalidator.poc.training.workout

import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.trainingvalidator.poc.training.models.WorkoutLineItem

/** Where optional in-flow rest applies (merged into pre-exercise UI). */
enum class WorkoutRestContext {
    NONE,
    BETWEEN_SETS,
    BETWEEN_EXERCISES,
}

/**
 * WorkoutTrainingEngine - Orchestrates an entire planned workout run within a single Activity.
 *
 * State Machine:
 *   IDLE -> PRE_EXERCISE (optional [State.PreExercise.pendingRestMs]) -> TRAINING -> ...
 *   -> WORKOUT_COMPLETE
 *
 * Pre-exercise may include a rest countdown on the same screen as exercise details; when
 * rest ends, the host starts training without a separate rest-only state.
 *
 * This replaces the old pattern of launching separate TrainingActivity instances
 * for each exercise. The camera pipeline stays alive and TrainingEngine is recycled
 * per set/exercise.
 *
 * Responsibilities:
 *  - Track current exercise and set position
 *  - Decide rest type (between-set vs between-exercise)
 *  - Collect per-set metrics
 *  - Build workout report on completion
 */
class WorkoutTrainingEngine(
    private val workoutLineItems: List<WorkoutLineItem>,
    private val plannedWorkoutRole: String = "MAIN",
) {
    companion object {
        private const val TAG = "WorkoutTrainingEngine"
        private const val DEFAULT_REST_BETWEEN_SETS_MS = 30000L
        private const val DEFAULT_REST_BETWEEN_EXERCISES_MS = 60000L
        private const val MIN_EXERCISE_PREVIEW_MS = 5000L
    }

    /** Warm-up / activation / cooldown roles are excluded from workout-level progress totals. */
    private fun countsTowardWorkoutProgress(@Suppress("UNUSED_PARAMETER") item: WorkoutLineItem): Boolean {
        val r = plannedWorkoutRole.trim().uppercase(Locale.US)
        if (r.isEmpty()) return true
        return r !in setOf("WARMUP", "ACTIVATION", "COOLDOWN")
    }

    // ==================== State Sealed Class ====================

    sealed class State {
        object Idle : State()

        data class PreExercise(
            val exerciseIndex: Int,
            val setNumber: Int,
            val totalSets: Int,
            val item: WorkoutLineItem,
            val exerciseName: String = "",
            /** When &gt; 0, UI shows this countdown on the same screen before training auto-starts. */
            val pendingRestMs: Long = 0L,
            val restContext: WorkoutRestContext = WorkoutRestContext.NONE,
        ) : State()

        object Training : State()

        data class WorkoutComplete(
            val report: WorkoutReport
        ) : State()
    }

    // ==================== Metrics Data Classes ====================

    /**
     * Per-rep detail captured during training.
     * This is the atomic unit of all reporting � everything aggregates from here.
     */
    data class RepDetail(
        val repNumber: Int,
        val score: Float,           // Form quality score (0-100)
        val worstState: Int,        // 0=PERFECT, 1=NORMAL, 2=PAD, 3=WARNING, 4=DANGER
        val isCounted: Boolean,     // Was this rep valid?
        val durationMs: Long
    )

    /**
     * Per-set metrics � enriched with rep-level details.
     * Accuracy = valid-rep ratio (counted/total); formScore = quality score average.
     */
    data class SetMetrics(
        val exerciseSlug: String,
        val exerciseIndex: Int,
        val setNumber: Int,
        val repsCompleted: Int,
        val repsTarget: Int,
        val durationMs: Long,
        val accuracy: Float,        // Form validity ratio from TrainingEngine (0-100)
        val formScore: Float,       // Average rep score (form quality, 0-100)
        val weightKg: Float?,
        val repDetails: List<RepDetail> = emptyList()
    )

    data class ExerciseReport(
        val exerciseSlug: String,
        val exerciseName: String,
        val setsCompleted: Int,
        val totalSets: Int,
        val totalReps: Int,
        val averageAccuracy: Float,
        val averageFormScore: Float,
        val setMetrics: List<SetMetrics>,
        val reportId: String? = null,   // PostTrainingReport ID (local rich report)
        val workoutId: String? = null   // WorkoutUpload ID (backend WorkoutExecution)
    )

    data class WorkoutReport(
        val totalExercises: Int,
        val totalSetsCompleted: Int,
        val totalSetsPlanned: Int,
        val totalReps: Int,
        val totalDurationMs: Long,
        val averageAccuracy: Float,
        val averageFormScore: Float,
        val exerciseReports: List<ExerciseReport>,
        val reportIds: List<String> = emptyList(),   // PostTrainingReport IDs (for report UI)
        val executionIds: List<String> = emptyList()   // WorkoutExecution IDs (for backend linking)
    )

    /**
     * Callback interface for exercise completion events.
     * Called when the LAST set of an exercise finishes, before rest/next-exercise.
     * This allows the host Activity to generate a rich PostTrainingReport while
     * the TrainingEngine still has the exercise data loaded.
     */
    interface OnExerciseCompletedListener {
        /**
         * Called when all sets for an exercise are done.
         * @param exerciseIndex Index of the completed exercise
         * @param exerciseSlug Exercise slug identifier
         * @param sets All SetMetrics collected for this exercise
         */
        fun onExerciseCompleted(exerciseIndex: Int, exerciseSlug: String, sets: List<SetMetrics>)
    }

    // ==================== State Flow ====================

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // ==================== Internal Data ====================

    /**
     * Indexed exercise item (exercise-only, with computed rest-after duration).
     */
    private data class IndexedItem(
        val originalIndex: Int,
        val item: WorkoutLineItem,
        val restAfterMs: Long
    )

    /** Exercise items only (rest-type items become restAfterMs on the preceding exercise). */
    private val exerciseItems = mutableListOf<IndexedItem>()

    /** Current position */
    private var currentExerciseIdx = 0
    private var currentSetNumber = 1

    /** Metrics collection */
    private val allSetMetrics = mutableListOf<SetMetrics>()
    private var workoutStartTimeMs: Long = 0L

    /** Exercise names resolved externally (slug ? localized name). */
    private val exerciseNames = mutableMapOf<String, String>()

    /** Rich report IDs per exercise (exerciseIndex ? PostTrainingReport.id). */
    private val exerciseReportIds = mutableMapOf<Int, String>()

    /** Backend execution IDs per exercise (exerciseIndex ? WorkoutUpload.id). */
    private val exerciseExecutionIds = mutableMapOf<Int, String>()

    /** Listener for exercise completion events. */
    var onExerciseCompletedListener: OnExerciseCompletedListener? = null

    // ==================== Init ====================

    init {
        val items = mutableListOf<IndexedItem>()
        var i = 0
        while (i < workoutLineItems.size) {
            val item = workoutLineItems[i]
            if (item.type == "exercise" && item.exerciseSlug != null) {
                // Look ahead for a rest item
                val nextItem = workoutLineItems.getOrNull(i + 1)
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
        exerciseItems.clear()
        exerciseItems.addAll(items)
        Log.d(TAG, "Workout engine created with ${exerciseItems.size} exercise items")
    }

    // ==================== Public API ====================

    /** Register a resolved exercise name (call before start). */
    fun setExerciseName(slug: String, name: String) {
        exerciseNames[slug] = name
    }

    /** Associate a rich PostTrainingReport ID with a completed exercise. */
    fun setExerciseReportId(exerciseIndex: Int, reportId: String) {
        exerciseReportIds[exerciseIndex] = reportId
    }

    /** Associate a backend WorkoutExecution ID with a completed exercise. */
    fun setExerciseExecutionId(exerciseIndex: Int, executionId: String) {
        exerciseExecutionIds[exerciseIndex] = executionId
    }

    /** Total exercise count (not including rest items). */
    fun getExerciseCount(): Int = exerciseItems.size

    /** Total sets planned across exercises that count toward workout progress (excludes warm-up ladder roles). */
    fun getTotalSetsPlanned(): Int = exerciseItems.sumOf { indexed ->
        if (!countsTowardWorkoutProgress(indexed.item)) 0
        else indexed.item.sets?.coerceAtLeast(1) ?: 1
    }

    /** Current exercise's WorkoutLineItem (or null if out of range). */
    fun getCurrentExerciseItem(): WorkoutLineItem? {
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

    /** Update planned weights for the current exercise (workout run mode, before training). */
    fun setWeightPerSetForCurrentExercise(weights: List<Float>?) {
        val idx = currentExerciseIdx
        val cur = exerciseItems.getOrNull(idx) ?: return
        val first = weights?.firstOrNull { it > 0f }
        val newItem = cur.item.copy(
            weightPerSet = weights,
            weightKg = first ?: cur.item.weightKg
        )
        exerciseItems[idx] = cur.copy(item = newItem)
    }

    /** Update target reps for the current exercise (affects remaining sets). */
    fun updateCurrentTargetReps(reps: Int?) {
        val idx = currentExerciseIdx
        val cur = exerciseItems.getOrNull(idx) ?: return
        val newItem = cur.item.copy(targetReps = reps?.coerceAtLeast(1))
        exerciseItems[idx] = cur.copy(item = newItem)
    }

    /** Update target hold duration (seconds) for the current exercise. */
    fun updateCurrentTargetDuration(seconds: Int?) {
        val idx = currentExerciseIdx
        val cur = exerciseItems.getOrNull(idx) ?: return
        val newItem = cur.item.copy(targetDuration = seconds?.coerceAtLeast(1))
        exerciseItems[idx] = cur.copy(item = newItem)
    }

    /** Update weight for the *current set only* (preserves other sets if per-set list exists). */
    fun updateCurrentSetWeight(kg: Float) {
        val idx = currentExerciseIdx
        val cur = exerciseItems.getOrNull(idx) ?: return
        val totalSets = cur.item.sets?.coerceAtLeast(1) ?: 1
        val currentSetIdx = (currentSetNumber - 1).coerceIn(0, totalSets - 1)
        val baseList = cur.item.weightPerSet?.toMutableList()
            ?: MutableList(totalSets) { cur.item.weightKg ?: 0f }
        if (currentSetIdx < baseList.size) {
            baseList[currentSetIdx] = kg
        }
        val newItem = cur.item.copy(
            weightPerSet = baseList,
            weightKg = kg
        )
        exerciseItems[idx] = cur.copy(item = newItem)
    }

    /** Start the workout run. */
    fun start() {
        if (exerciseItems.isEmpty()) {
            Log.w(TAG, "No exercise items in workout")
            _state.value = State.WorkoutComplete(buildReport())
            return
        }
        workoutStartTimeMs = System.currentTimeMillis()
        currentExerciseIdx = 0
        currentSetNumber = 1
        allSetMetrics.clear()
        showPreExercise()
    }

    /** Transition from PRE_EXERCISE ? TRAINING after user taps "Start Set". */
    fun startTraining() {
        _state.value = State.Training
    }

    /**
     * If the UI is still on pre-exercise with an in-flow rest countdown, clears rest so the
     * user can start manually (e.g. weight dialog cancelled after auto-start from rest).
     */
    fun clearPrepRestFlagsIfPreExercise() {
        val cur = _state.value as? State.PreExercise ?: return
        if (cur.pendingRestMs <= 0L) return
        _state.value = cur.copy(
            pendingRestMs = 0L,
            restContext = WorkoutRestContext.NONE,
        )
    }

    /**
     * Called when a set completes (TrainingEngine fires completion).
     * Decides: PreExercise (with optional rest) / WorkoutComplete.
     */
    fun onSetCompleted(metrics: SetMetrics) {
        allSetMetrics.add(metrics)

        val currentItem = exerciseItems[currentExerciseIdx]
        val totalSets = currentItem.item.sets?.coerceAtLeast(1) ?: 1

        Log.d(TAG, "Set completed: ${metrics.exerciseSlug} set ${metrics.setNumber}/$totalSets")

        if (currentSetNumber < totalSets) {
            // More sets for this exercise ? set rest
            currentSetNumber++
            val restMs = currentItem.item.restBetweenSetsMs ?: DEFAULT_REST_BETWEEN_SETS_MS
            if (restMs > 0) {
                showPreExercise(
                    pendingRestMs = restMs,
                    restContext = WorkoutRestContext.BETWEEN_SETS,
                )
            } else {
                showPreExercise()
            }
        } else {
            // All sets done ? notify listener then move to next exercise
            val slug = currentItem.item.exerciseSlug
            if (slug != null) {
                val exerciseSets = allSetMetrics.filter { it.exerciseSlug == slug }
                onExerciseCompletedListener?.onExerciseCompleted(
                    exerciseIndex = currentExerciseIdx,
                    exerciseSlug = slug,
                    sets = exerciseSets
                )
            }
            moveToNextExercise()
        }
    }

    /** Re-build the workout report with the latest data (including any report IDs set after initial build). */
    fun getCurrentReport(): WorkoutReport = buildReport()

    // ==================== Private ====================

    private fun showPreExercise(
        pendingRestMs: Long = 0L,
        restContext: WorkoutRestContext = WorkoutRestContext.NONE,
    ) {
        val current = exerciseItems.getOrNull(currentExerciseIdx)
        if (current == null) {
            _state.value = State.WorkoutComplete(buildReport())
            return
        }
        val totalSets = current.item.sets?.coerceAtLeast(1) ?: 1
        _state.value = State.PreExercise(
            exerciseIndex = currentExerciseIdx,
            setNumber = currentSetNumber,
            totalSets = totalSets,
            item = current.item,
            exerciseName = getExerciseName(current),
            pendingRestMs = pendingRestMs,
            restContext = restContext,
        )
    }

    private fun moveToNextExercise() {
        val finishedItem = exerciseItems[currentExerciseIdx]
        currentExerciseIdx++
        currentSetNumber = 1

        if (currentExerciseIdx >= exerciseItems.size) {
            _state.value = State.WorkoutComplete(buildReport())
            return
        }

        val restMs = finishedItem.restAfterMs.coerceAtLeast(MIN_EXERCISE_PREVIEW_MS)
        if (restMs > 0) {
            showPreExercise(
                pendingRestMs = restMs,
                restContext = WorkoutRestContext.BETWEEN_EXERCISES,
            )
        } else {
            showPreExercise()
        }
    }

    private fun getExerciseName(indexed: IndexedItem): String {
        val slug = indexed.item.exerciseSlug ?: return "Exercise"
        return exerciseNames[slug] ?: slug
    }

    private fun buildReport(): WorkoutReport {
        val totalDuration = System.currentTimeMillis() - workoutStartTimeMs

        val exerciseReports = exerciseItems.mapIndexed { idx, indexed ->
            val slug = indexed.item.exerciseSlug ?: return@mapIndexed null
            val sets = allSetMetrics.filter { it.exerciseIndex == idx }
            val totalSetsForExercise = indexed.item.sets?.coerceAtLeast(1) ?: 1
            ExerciseReport(
                exerciseSlug = slug,
                exerciseName = getExerciseName(indexed),
                setsCompleted = sets.size,
                totalSets = totalSetsForExercise,
                totalReps = sets.sumOf { it.repsCompleted },
                averageAccuracy = if (sets.isNotEmpty())
                    sets.map { it.accuracy }.average().toFloat() else 0f,
                averageFormScore = if (sets.isNotEmpty())
                    sets.map { it.formScore }.average().toFloat() else 0f,
                setMetrics = sets,
                reportId = exerciseReportIds[idx],
                workoutId = exerciseExecutionIds[idx]
            )
        }.filterNotNull()

        val progressMetrics = allSetMetrics.filter { m ->
            val indexed = exerciseItems.getOrNull(m.exerciseIndex) ?: return@filter false
            countsTowardWorkoutProgress(indexed.item)
        }
        val progressFormScores = progressMetrics.map { it.formScore }

        val progressExerciseCount = exerciseItems.count { countsTowardWorkoutProgress(it.item) }

        return WorkoutReport(
            totalExercises = progressExerciseCount,
            totalSetsCompleted = progressMetrics.size,
            totalSetsPlanned = getTotalSetsPlanned(),
            totalReps = progressMetrics.sumOf { it.repsCompleted },
            totalDurationMs = totalDuration,
            averageAccuracy = if (progressMetrics.isNotEmpty())
                progressMetrics.map { it.accuracy }.average().toFloat() else 0f,
            averageFormScore = if (progressFormScores.isNotEmpty())
                progressFormScores.average().toFloat() else 0f,
            exerciseReports = exerciseReports,
            reportIds = exerciseReports.mapNotNull { it.reportId },
            executionIds = exerciseReports.mapNotNull { it.workoutId }
        )
    }
}

