package com.trainingvalidator.poc.training.engine

import com.movit.core.training.engine.RepCounter as KmpRepCounter
import com.trainingvalidator.poc.training.engine.evaluation.JointEval
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.models.RepResult

/**
 * RepCounter - STATE-BASED Rep Counting and Scoring.
 * Delegates to KMP [com.movit.core.training.engine.RepCounter].
 */
class RepCounter(
    minRepIntervalMs: Long,
    targetReps: Int = 12,
    isHoldExercise: Boolean = false,
    primaryJoints: Set<String> = emptySet(),
    timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val core = KmpRepCounter(
        minRepIntervalMs = minRepIntervalMs,
        targetReps = targetReps,
        isHoldExercise = isHoldExercise,
        primaryJoints = primaryJoints,
        timeProvider = timeProvider,
    )

    private val currentPositionErrors = mutableListOf<PositionError>()
    private val repPositionErrors = mutableListOf<List<PositionError>>()

    var count: Int
        get() = core.count
        private set(value) = Unit

    var countedCount: Int
        get() = core.countedCount
        private set(value) = Unit

    var uncountedCount: Int
        get() = core.uncountedCount
        private set(value) = Unit

    var invalidatedCount: Int
        get() = core.invalidatedCount
        private set(value) = Unit

    val repResults: List<RepResult>
        get() = core.repResults.mapIndexed { index, result ->
            result.toApp(repPositionErrors.getOrElse(index) { emptyList() })
        }

    var onRepCountChanged: ((Int, Float, Boolean) -> Unit)?
        get() = core.onRepCountChanged
        set(value) {
            core.onRepCountChanged = value
        }

    var onTargetReached: (() -> Unit)?
        get() = core.onTargetReached
        set(value) {
            core.onTargetReached = value
        }

    fun updateJointEvals(evals: Map<String, JointEval>) {
        core.updateJointEvals(evals.mapValues { JointEvalAdapter(it.value) })
    }

    fun updateJointStates(jointStates: Map<String, JointStateInfo>) {
        core.updateJointStates(jointStates.mapValues { it.value.toKmpScoringInfo() })
    }

    fun addError(error: JointError) {
        core.addError(error.toKmp())
    }

    fun addPositionError(error: PositionError) {
        if (error.severity != CheckSeverity.ERROR) return
        if (currentPositionErrors.none { it.checkId == error.checkId }) {
            currentPositionErrors.add(error)
        }
        core.addPositionError(error.checkId)
    }

    fun addPositionWarning(error: PositionError) {
        core.addPositionWarning(error.checkId)
    }

    fun addPositionTip(error: PositionError) {
        core.addPositionTip(error.checkId)
    }

    fun setPhaseTimings(timings: Map<Phase, Long>) {
        core.setPhaseTimings(timings.mapKeys { it.key.toKmp() })
    }

    fun getCurrentWorstState(): JointState = core.getCurrentWorstState().toApp()

    fun getPendingScore(): Float = core.getPendingScore()

    fun completeRep() {
        val before = core.count
        core.completeRep()
        if (core.count > before) {
            repPositionErrors.add(currentPositionErrors.toList())
            currentPositionErrors.clear()
        }
    }

    fun completeRepWithState(worstState: JointState) {
        val before = core.count
        core.completeRepWithState(worstState.toKmp())
        if (core.count > before) {
            repPositionErrors.add(currentPositionErrors.toList())
            currentPositionErrors.clear()
        }
    }

    fun getAverageScore(): Float = core.getAverageScore()

    fun getAccuracy(): Float = core.getAccuracy()

    fun getProgress(): Float = core.getProgress()

    fun isTargetReached(): Boolean = core.isTargetReached()

    fun getRemainingReps(): Int = core.getRemainingReps()

    fun getMostCommonErrors(): Map<String, Int> = core.getMostCommonErrors()

    fun getStateBreakdown(): Map<JointState, Int> =
        core.getStateBreakdown().mapKeys { it.key.toApp() }

    fun reset() {
        core.reset()
        currentPositionErrors.clear()
        repPositionErrors.clear()
    }

    fun hasStarted(): Boolean = core.hasStarted()

    fun getLastRepResult(): RepResult? {
        val last = core.getLastRepResult() ?: return null
        val index = core.repResults.lastIndex
        return last.toApp(repPositionErrors.getOrElse(index) { emptyList() })
    }

    val correctCount: Int get() = core.correctCount
    val incorrectCount: Int get() = core.incorrectCount
}
