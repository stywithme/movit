package com.movit.core.training.engine

import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.position.PositionError

class RepCounter(
    private val minRepIntervalMs: Long,
    /** Session completion target (may be `perSideTarget * 2` for bilateral AFTER_ALL_REPS). */
    private val targetReps: Int = 12,
    private val isHoldExercise: Boolean = false,
    private val primaryJoints: Set<String> = emptySet(),
    private val timeProvider: () -> Long = { currentTimeMillis() },
) {
    companion object {
        private const val POSITION_ERROR_PENALTY = 15f
        private const val POSITION_WARNING_PENALTY = 6f
        private const val MIN_REP_SEVERITY_FRAMES = 2
    }

    private class PrimaryRepZoneTracker {
        var bestUpQuality: JointStateInfo? = null
        var bestDownQuality: JointStateInfo? = null
        var worstSeverityInfo: JointStateInfo? = null
        private var severityFrameCount = 0

        private fun pickBetterQuality(current: JointStateInfo?, candidate: JointStateInfo): JointStateInfo {
            if (current == null) return candidate
            return if (candidate.state.isBetterThan(current.state)) candidate else current
        }

        private fun mergeZoneQuality(up: JointStateInfo?, down: JointStateInfo?): JointStateInfo? {
            if (up == null) return down
            if (down == null) return up
            return if (up.state.isWorseThan(down.state)) up else down
        }

        fun ingest(stateInfo: JointStateInfo) {
            val state = stateInfo.state
            when (state) {
                JointState.WARNING, JointState.DANGER -> {
                    val cur = worstSeverityInfo
                    if (cur == null || state.isWorseThan(cur.state)) {
                        worstSeverityInfo = stateInfo
                    }
                }
                else -> {
                    if (!stateInfo.state.isScorableForRepQuality) return
                    when (stateInfo.currentZone) {
                        ZoneType.UP_ZONE ->
                            bestUpQuality = pickBetterQuality(bestUpQuality, stateInfo)
                        ZoneType.DOWN_ZONE ->
                            bestDownQuality = pickBetterQuality(bestDownQuality, stateInfo)
                        ZoneType.TRANSITION -> Unit
                    }
                }
            }
        }

        fun ingestFromEval(eval: JointEvalInput, stateInfo: JointStateInfo) {
            when (eval.state) {
                JointState.WARNING, JointState.DANGER -> {
                    severityFrameCount++
                    if (severityFrameCount >= MIN_REP_SEVERITY_FRAMES) {
                        val cur = worstSeverityInfo
                        if (cur == null || eval.state.isWorseThan(cur.state)) {
                            worstSeverityInfo = stateInfo
                        }
                    }
                }
                else -> {
                    severityFrameCount = 0
                    if (!eval.isScorableForRepQuality) return
                    when (eval.zoneType) {
                        ZoneType.UP_ZONE ->
                            bestUpQuality = pickBetterQuality(bestUpQuality, stateInfo)
                        ZoneType.DOWN_ZONE ->
                            bestDownQuality = pickBetterQuality(bestDownQuality, stateInfo)
                        ZoneType.TRANSITION -> Unit
                    }
                }
            }
        }

        fun mergedForScoring(): JointStateInfo? {
            val zonePeak = mergeZoneQuality(bestUpQuality, bestDownQuality)
            val sev = worstSeverityInfo ?: return zonePeak
            val z = zonePeak ?: return sev
            return if (sev.state.isWorseThan(z.state)) sev else z
        }
    }

    private var lastRepTime: Long = 0L

    var count: Int = 0
        private set

    var countedCount: Int = 0
        private set

    var uncountedCount: Int = 0
        private set

    var invalidatedCount: Int = 0
        private set

    private var totalScore: Float = 0f
    private val _repResults = mutableListOf<RepResult>()
    val repResults: List<RepResult> get() = _repResults.toList()

    private val currentRepErrors = mutableListOf<JointError>()
    private val currentPositionErrors = mutableListOf<RepPositionErrorSnapshot>()
    private val currentPositionWarningIds = mutableSetOf<String>()
    private val currentPositionTipIds = mutableSetOf<String>()
    private var currentRepWorstState: JointState = JointState.PERFECT
    private val stateTimeTracking = mutableMapOf<JointState, Long>()
    private var lastStateUpdateTime: Long = 0L
    private var currentTrackingState: JointState = JointState.PERFECT
    private var currentJointStates: Map<String, JointStateInfo> = emptyMap()
    private val primaryRepZoneTrackers = mutableMapOf<String, PrimaryRepZoneTracker>()
    private val secondaryRepWorstStates = mutableMapOf<String, JointStateInfo>()
    private var currentPhaseTimings = mapOf<String, Long>()

    var onRepCountChanged: ((Int, Float, Boolean) -> Unit)? = null
    var onTargetReached: (() -> Unit)? = null
    private var targetReachedEmitted = false

    fun updateJointEvals(evals: Map<String, JointEvalInput>) {
        val stateInfos = evals.mapValues { it.value.toJointStateInfo() }
        currentJointStates = stateInfos

        for ((jointCode, eval) in evals) {
            if (eval.state == JointState.TRANSITION) continue

            if (primaryJoints.contains(jointCode) && eval.isPrimary) {
                primaryRepZoneTrackers.getOrPut(jointCode) { PrimaryRepZoneTracker() }
                    .ingestFromEval(eval, stateInfos.getValue(jointCode))
            } else {
                val stateInfo = stateInfos.getValue(jointCode)
                val existing = secondaryRepWorstStates[jointCode]
                if (existing == null || stateInfo.state.isWorseThan(existing.state)) {
                    secondaryRepWorstStates[jointCode] = stateInfo
                }
            }
        }

        val worstState = stateInfos.values
            .map { it.state }
            .filter { it != JointState.TRANSITION }
            .maxByOrNull { it.priority } ?: JointState.PERFECT

        if (worstState.isWorseThan(currentRepWorstState)) {
            currentRepWorstState = worstState
        }

        if (isHoldExercise) {
            updateStateTimeTracking(worstState)
        }
    }

    fun updateJointStates(jointStates: Map<String, JointStateInfo>) {
        currentJointStates = jointStates

        for ((jointCode, stateInfo) in jointStates) {
            if (stateInfo.state == JointState.TRANSITION) continue

            if (primaryJoints.contains(jointCode) && stateInfo.isPrimary) {
                primaryRepZoneTrackers.getOrPut(jointCode) { PrimaryRepZoneTracker() }
                    .ingest(stateInfo)
            } else {
                val existing = secondaryRepWorstStates[jointCode]
                if (existing == null || stateInfo.state.isWorseThan(existing.state)) {
                    secondaryRepWorstStates[jointCode] = stateInfo
                }
            }
        }

        val worstState = jointStates.values
            .map { it.state }
            .filter { it != JointState.TRANSITION }
            .maxByOrNull { it.priority } ?: JointState.PERFECT

        if (worstState.isWorseThan(currentRepWorstState)) {
            currentRepWorstState = worstState
        }

        if (isHoldExercise) {
            updateStateTimeTracking(worstState)
        }
    }

    private fun buildAccumulatedStatesForScoring(): Map<String, JointStateInfo> {
        val out = LinkedHashMap<String, JointStateInfo>()
        for ((code, tracker) in primaryRepZoneTrackers) {
            tracker.mergedForScoring()?.let { out[code] = it }
        }
        for ((code, info) in secondaryRepWorstStates) {
            out[code] = info
        }
        return out
    }

    private fun updateStateTimeTracking(state: JointState) {
        val now = timeProvider()
        if (lastStateUpdateTime > 0) {
            val duration = now - lastStateUpdateTime
            stateTimeTracking[currentTrackingState] =
                (stateTimeTracking[currentTrackingState] ?: 0L) + duration
        }
        currentTrackingState = state
        lastStateUpdateTime = now
    }

    fun addError(error: JointError) {
        val existingIndex = currentRepErrors.indexOfFirst {
            it.jointCode == error.jointCode && it.errorType == error.errorType
        }
        if (existingIndex < 0) {
            currentRepErrors.add(error)
            return
        }
        val existing = currentRepErrors[existingIndex]
        if (error.state.isWorseThan(existing.state) || error.state == existing.state) {
            currentRepErrors[existingIndex] = error
        }
    }

    fun addPositionError(error: PositionError) {
        addPositionError(error.toRepSnapshot())
    }

    fun addPositionError(snapshot: RepPositionErrorSnapshot) {
        if (snapshot.severity != CheckSeverity.ERROR) return
        val exists = currentPositionErrors.any { it.checkId == snapshot.checkId }
        if (!exists) {
            currentPositionErrors.add(snapshot)
        }
    }

    /** Test/back-compat when only checkId is needed (no message/landmarks). */
    fun addPositionError(checkId: String) {
        addPositionError(RepPositionErrorSnapshot.minimalError(checkId))
    }

    fun addPositionWarning(checkId: String) {
        currentPositionWarningIds.add(checkId)
    }

    fun addPositionTip(checkId: String) {
        currentPositionTipIds.add(checkId)
    }

    fun setPhaseTimings(timings: Map<Phase, Long>) {
        currentPhaseTimings = timings
            .filterKeys { it in MOVEMENT_TRACKING_PHASES }
            .mapKeys { it.key.name.lowercase() }
    }

    fun discardCurrentRepAttempt(reason: RepIncompleteReason) {
        if (!shouldDiscardRepAttemptOnIncomplete(reason)) return
        resetCurrentRepTracking()
    }

    fun getCurrentWorstState(): JointState = currentRepWorstState

    fun getPendingScore(): Float = if (isHoldExercise) {
        updateStateTimeTracking(currentTrackingState)
        ScoreCalculator.calculateHoldScore(stateTimeTracking).score
    } else {
        val accumulated = buildAccumulatedStatesForScoring()
        when {
            accumulated.isNotEmpty() ->
                ScoreCalculator.calculateRepScore(accumulated, primaryJoints).score
            currentJointStates.isNotEmpty() ->
                ScoreCalculator.calculateRepScore(currentJointStates, primaryJoints).score
            else ->
                ScoreCalculator.calculateScoreFromWorstState(currentRepWorstState)
        }
    }

    fun completeRep() {
        val now = timeProvider()
        val timeSinceLastRep = now - lastRepTime

        if (isHoldExercise && lastRepTime > 0 && timeSinceLastRep < minRepIntervalMs) {
            return
        }

        lastRepTime = now
        count++

        var score: Float
        var isCounted: Boolean
        var isInvalidated: Boolean
        var resultWorstState = currentRepWorstState

        if (isHoldExercise) {
            updateStateTimeTracking(currentTrackingState)
            val holdResult = ScoreCalculator.calculateHoldScore(stateTimeTracking)
            score = holdResult.score
            isInvalidated = holdResult.isInvalidated
            isCounted = !isInvalidated && score > 0
        } else {
            val accumulated = buildAccumulatedStatesForScoring()
            if (accumulated.isNotEmpty()) {
                val repResult = ScoreCalculator.calculateRepScore(accumulated, primaryJoints)
                score = repResult.score
                isCounted = repResult.isCounted
                isInvalidated = repResult.isInvalidated
                resultWorstState = repResult.worstState
            } else if (currentJointStates.isNotEmpty()) {
                val repResult = ScoreCalculator.calculateRepScore(currentJointStates, primaryJoints)
                score = repResult.score
                isCounted = repResult.isCounted
                isInvalidated = repResult.isInvalidated
                resultWorstState = repResult.worstState
            } else {
                score = ScoreCalculator.calculateScoreFromWorstState(currentRepWorstState)
                isCounted = EngineStateConfig.isRepCounted(currentRepWorstState)
                isInvalidated = EngineStateConfig.invalidatesRep(currentRepWorstState)
            }
        }

        val positionErrorPenalty = currentPositionErrors.size * POSITION_ERROR_PENALTY
        val positionWarningPenalty = currentPositionWarningIds.size * POSITION_WARNING_PENALTY
        if (positionErrorPenalty > 0f || positionWarningPenalty > 0f) {
            score = (score - positionErrorPenalty - positionWarningPenalty).coerceIn(0f, 100f)
        }
        if (currentPositionErrors.isNotEmpty()) {
            if (JointState.WARNING.isWorseThan(resultWorstState)) {
                resultWorstState = JointState.WARNING
            }
            if (!isInvalidated) {
                isCounted = false
            }
        } else if (currentPositionWarningIds.isNotEmpty()) {
            if (JointState.PAD.isWorseThan(resultWorstState)) {
                resultWorstState = JointState.PAD
            }
        }

        if (isInvalidated) {
            invalidatedCount++
        } else if (isCounted) {
            countedCount++
        } else {
            uncountedCount++
        }
        totalScore += score

        val result = RepResult(
            repNumber = count,
            score = score,
            worstState = resultWorstState,
            isCounted = isCounted,
            isInvalidated = isInvalidated,
            errors = currentRepErrors.toList(),
            positionErrors = currentPositionErrors.toList(),
            positionErrorCheckIds = currentPositionErrors.map { it.checkId },
            positionWarningCount = currentPositionWarningIds.size,
            positionTipCount = currentPositionTipIds.size,
            phaseTimings = currentPhaseTimings,
            timestamp = now,
        )
        _repResults.add(result)
        resetCurrentRepTracking()
        onRepCountChanged?.invoke(count, score, isCounted)

        if (count >= targetReps && !targetReachedEmitted) {
            targetReachedEmitted = true
            onTargetReached?.invoke()
        }
    }

    fun completeRepWithState(worstState: JointState) {
        currentRepWorstState = worstState
        completeRep()
    }

    private fun resetCurrentRepTracking() {
        currentRepErrors.clear()
        currentPositionErrors.clear()
        currentPositionWarningIds.clear()
        currentPositionTipIds.clear()
        currentPhaseTimings = emptyMap()
        currentRepWorstState = JointState.PERFECT
        stateTimeTracking.clear()
        lastStateUpdateTime = 0L
        currentTrackingState = JointState.PERFECT
        currentJointStates = emptyMap()
        primaryRepZoneTrackers.clear()
        secondaryRepWorstStates.clear()
    }

    fun getAverageScore(): Float = if (count == 0) 0f else totalScore / count

    fun getAccuracy(): Float = if (count == 0) 100f else (countedCount.toFloat() / count.toFloat()) * 100f

    fun getProgress(): Float = if (targetReps == 0) 0f else (count.toFloat() / targetReps.toFloat()).coerceIn(0f, 1f)

    fun isTargetReached(): Boolean = count >= targetReps

    fun getRemainingReps(): Int = (targetReps - count).coerceAtLeast(0)

    fun getMostCommonErrors(): Map<String, Int> =
        _repResults
            .flatMap { it.errors }
            .groupingBy { "${it.jointCode}:${it.errorType}" }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .toMap()

    fun getMostCommonPositionErrors(): Map<String, Int> =
        _repResults
            .flatMap { it.positionErrors }
            .groupingBy { it.checkId }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .toMap()

    fun getStateBreakdown(): Map<JointState, Int> =
        _repResults.groupingBy { it.worstState }.eachCount()

    fun reset() {
        count = 0
        countedCount = 0
        uncountedCount = 0
        invalidatedCount = 0
        totalScore = 0f
        _repResults.clear()
        resetCurrentRepTracking()
        lastRepTime = 0L
        targetReachedEmitted = false
    }

    fun hasStarted(): Boolean = count > 0

    fun getLastRepResult(): RepResult? = _repResults.lastOrNull()

    val correctCount: Int get() = countedCount
    val incorrectCount: Int get() = uncountedCount + invalidatedCount
}
