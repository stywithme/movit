package com.movit.core.training.journal

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.Phase
import com.movit.core.training.report.SessionQualityMeta
import kotlin.random.Random

/**
 * Records motion metrics during training — metrics only, no raw frame persistence.
 */
class MotionRecorder(
    private val trackedJoints: List<String>,
    private val exerciseId: String,
    private val defaultWeightKg: Float? = null,
    private val weightUnit: String = "kg",
    private val timeProvider: () -> Long = { 0L },
    private val idGenerator: () -> String = { randomUuid() },
) {
    private var recordingStartMs: Long = 0L
    private var isRecording = false
    private val currentRepBuffer = mutableListOf<FrameSample>()
    private var currentRepStartT: Int = 0
    private var lastStates: ByteArray? = null
    private var maxFramesWarningLogged = false
    private val completedRepMetrics = mutableListOf<RepMetricsData>()
    private var primaryJointIndex: Int = 0
    private var leftJointIndex: Int? = null
    private var rightJointIndex: Int? = null
    private var hipIndices: Pair<Int, Int>? = null
    private var spineJointIndex: Int? = null
    private var bestVelocity: Short? = null
    private var framesOffered: Int = 0
    private var framesRecorded: Int = 0
    private var framesDropped: Int = 0
    private var jointCoverageNumerator: Long = 0
    private var jointCoverageDenominator: Long = 0

    fun start(startTimestampMs: Long = timeProvider()) {
        recordingStartMs = startTimestampMs
        isRecording = true
        currentRepBuffer.clear()
        completedRepMetrics.clear()
        lastStates = null
        bestVelocity = null
        framesOffered = 0
        framesRecorded = 0
        framesDropped = 0
        jointCoverageNumerator = 0
        jointCoverageDenominator = 0
        setupJointIndices()
    }

    fun record(
        timestamp: Long,
        phase: Phase,
        angles: Map<String, Double>,
        states: Map<String, JointStateInfo>? = null,
        skippedJointCodes: Set<String> = emptySet(),
    ) {
        if (!isRecording) return
        if (recordingStartMs == 0L) recordingStartMs = timestamp
        framesOffered++

        if (currentRepBuffer.size >= MAX_FRAMES_PER_REP) {
            if (!maxFramesWarningLogged) maxFramesWarningLogged = true
            framesDropped += currentRepBuffer.size.coerceAtLeast(1)
            currentRepBuffer.clear()
            lastStates = null
            currentRepStartT = (timestamp - recordingStartMs).toInt()
        }

        val relativeT = (timestamp - recordingStartMs).toInt()
        val angleArray = anglesToShortArray(trackedJoints, angles, skippedJointCodes)
        val stateArray = jointStatesToByteArray(trackedJoints, states)
        val statesForStorage = if (byteArrayEquals(stateArray, lastStates)) {
            null
        } else {
            lastStates = stateArray
            stateArray
        }

        currentRepBuffer += FrameSample(
            t = relativeT,
            phase = PhaseCode.fromPhase(phase),
            angles = angleArray,
            states = statesForStorage,
        )
        framesRecorded++
        if (trackedJoints.isNotEmpty()) {
            jointCoverageDenominator += trackedJoints.size
            jointCoverageNumerator += angleArray.count { it != JOINT_SKIPPED_ANGLE_SENTINEL }
        }
        if (currentRepBuffer.size == 1) currentRepStartT = relativeT
    }

    fun finalizeRep(
        repNumber: Int,
        phaseTimings: Map<String, Long>,
        worstState: JointState,
        score: Float,
        weightKg: Float? = null,
        side: String? = null,
    ) {
        if (!isRecording) return
        if (completedRepMetrics.size >= MAX_REPS) {
            currentRepBuffer.clear()
            return
        }
        if (currentRepBuffer.isEmpty()) return

        maxFramesWarningLogged = false
        val endT = currentRepBuffer.lastOrNull()?.t ?: currentRepStartT
        val durationMs = endT - currentRepStartT
        val phases = listOf(
            phaseTimings["eccentric"]?.toInt() ?: phaseTimings["down"]?.toInt() ?: 0,
            phaseTimings["isometric"]?.toInt() ?: phaseTimings["bottom"]?.toInt()
                ?: phaseTimings["extended"]?.toInt() ?: 0,
            phaseTimings["concentric"]?.toInt() ?: phaseTimings["up"]?.toInt()
                ?: phaseTimings["push"]?.toInt() ?: phaseTimings["pull"]?.toInt() ?: 0,
        )

        val velocity = MetricsCalculator.calculateVelocity(currentRepBuffer, primaryJointIndex)
        val velocityLoss = if (bestVelocity != null && bestVelocity!! > 0 && velocity != null) {
            val loss = ((bestVelocity!! - velocity).toFloat() / bestVelocity!! * 1000).toInt()
            loss.coerceIn(0, 1000).toShort()
        } else {
            null
        }
        velocity?.let { value ->
            if (bestVelocity == null || value > bestVelocity!!) bestVelocity = value
        }

        val stability = if (spineJointIndex != null) {
            MetricsCalculator.calculateTrunkStability(currentRepBuffer, spineJointIndex!!)
        } else {
            hipIndices?.let { MetricsCalculator.calculateStability(currentRepBuffer, it) } ?: 1000
        }

        val repSide = normalizeSide(side)
        val repMetrics = RepMetrics(
            rom = MetricsCalculator.calculateROM(currentRepBuffer, primaryJointIndex),
            symmetry = if (repSide == null && leftJointIndex != null && rightJointIndex != null) {
                MetricsCalculator.calculateSymmetry(currentRepBuffer, leftJointIndex!!, rightJointIndex!!)
            } else {
                null
            },
            stability = stability,
            tempo = phases,
            velocity = velocity,
            formScore = (score * 10).toInt().toShort(),
            alignmentAccuracy = MetricsCalculator.calculateAlignmentAccuracy(currentRepBuffer),
            velocityLoss = velocityLoss,
        )

        completedRepMetrics += RepMetricsData(
            num = repNumber,
            durationMs = durationMs,
            worstState = StateCode.fromJointState(worstState),
            score = (score * 10).toInt().toShort(),
            weightKg = weightKg ?: defaultWeightKg,
            side = repSide,
            metrics = repMetrics,
        )
        currentRepBuffer.clear()
        lastStates = null
    }

    fun finalize(workoutId: String? = null, endTimestampMs: Long = timeProvider()): WorkoutUpload {
        isRecording = false
        val id = workoutId ?: idGenerator()
        val deltaMs = (endTimestampMs - recordingStartMs).coerceAtLeast(0L)
        val durationMs = deltaMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val executionMetrics = calculateWorkoutExecutionMetrics()
        return WorkoutUpload(
            id = id,
            exerciseId = exerciseId,
            timestamp = recordingStartMs,
            durationMs = durationMs,
            totalReps = completedRepMetrics.size,
            countedReps = completedRepMetrics.count { it.worstState <= StateCode.PAD },
            invalidReps = completedRepMetrics.count { it.worstState == StateCode.DANGER },
            weightKg = defaultWeightKg,
            weightUnit = weightUnit,
            repMetrics = completedRepMetrics.toList(),
            executionMetrics = executionMetrics,
        )
    }

    fun sessionQualityMeta(
        visibilityPauseCount: Int = 0,
        cameraWarningCount: Int = 0,
    ): SessionQualityMeta = SessionQualityMeta.fromFrameStats(
        framesOffered = framesOffered,
        framesRecorded = framesRecorded,
        framesDropped = framesDropped,
        jointCoverageRatio = jointCoverageRatio(),
        visibilityPauseCount = visibilityPauseCount,
        cameraWarningCount = cameraWarningCount,
    )

    fun snapshot(sessionId: String, isAssessmentMode: Boolean = false): SessionJournalSnapshot =
        SessionJournalSnapshot(
            sessionId = sessionId,
            exerciseId = exerciseId,
            trackedJoints = trackedJoints,
            recordingStartMs = recordingStartMs,
            defaultWeightKg = defaultWeightKg,
            weightUnit = weightUnit,
            completedRepMetrics = completedRepMetrics.toList(),
            isAssessmentMode = isAssessmentMode,
            framesOffered = framesOffered,
            framesRecorded = framesRecorded,
            framesDropped = framesDropped,
            jointCoverageNumerator = jointCoverageNumerator,
            jointCoverageDenominator = jointCoverageDenominator,
        )

    fun restore(snapshot: SessionJournalSnapshot) {
        require(snapshot.exerciseId == exerciseId) {
            "Journal exercise mismatch: expected $exerciseId got ${snapshot.exerciseId}"
        }
        recordingStartMs = snapshot.recordingStartMs
        isRecording = true
        completedRepMetrics.clear()
        completedRepMetrics += snapshot.completedRepMetrics
        currentRepBuffer.clear()
        lastStates = null
        bestVelocity = snapshot.completedRepMetrics.mapNotNull { it.metrics.velocity }.maxOrNull()
        framesOffered = snapshot.framesOffered
        framesRecorded = snapshot.framesRecorded
        framesDropped = snapshot.framesDropped
        jointCoverageNumerator = snapshot.jointCoverageNumerator
        jointCoverageDenominator = snapshot.jointCoverageDenominator
        setupJointIndices()
    }

    fun cancel() {
        isRecording = false
        currentRepBuffer.clear()
        completedRepMetrics.clear()
    }

    fun isActive(): Boolean = isRecording

    fun completedRepCount(): Int = completedRepMetrics.size

    private fun jointCoverageRatio(): Float? {
        if (jointCoverageDenominator <= 0L) return null
        return (jointCoverageNumerator.toFloat() / jointCoverageDenominator.toFloat()).coerceIn(0f, 1f)
    }

    private fun setupJointIndices() {
        primaryJointIndex = 0
        val leftKnee = trackedJoints.indexOfFirst { it.contains("left_knee", ignoreCase = true) }
        val rightKnee = trackedJoints.indexOfFirst { it.contains("right_knee", ignoreCase = true) }
        if (leftKnee >= 0 && rightKnee >= 0) {
            leftJointIndex = leftKnee
            rightJointIndex = rightKnee
        }
        val leftHip = trackedJoints.indexOfFirst { it.contains("left_hip", ignoreCase = true) }
        val rightHip = trackedJoints.indexOfFirst { it.contains("right_hip", ignoreCase = true) }
        if (leftHip >= 0 && rightHip >= 0) hipIndices = Pair(leftHip, rightHip)
        val spine = trackedJoints.indexOfFirst { it.equals("spine", ignoreCase = true) }
        if (spine >= 0) spineJointIndex = spine
    }

    private fun calculateWorkoutExecutionMetrics(): WorkoutExecutionMetrics {
        if (completedRepMetrics.isEmpty()) return emptyExecutionMetrics()
        val repMetricsList = completedRepMetrics.map { it.metrics }
        val avgRom = repMetricsList.map { it.rom }.average().toInt().toShort()
        val avgSymmetry = calculateBilateralSymmetryFromSides()
            ?: repMetricsList.mapNotNull { it.symmetry }.takeIf { it.isNotEmpty() }
                ?.average()?.toInt()?.toShort()
        val avgStability = repMetricsList.map { it.stability }.average().toInt().toShort()
        val avgVelocity = repMetricsList.mapNotNull { it.velocity }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.toShort()
        val avgFormScore = repMetricsList.map { it.formScore }.average().toInt().toShort()
        val avgAlignment = repMetricsList.map { it.alignmentAccuracy }.average().toInt().toShort()
        val avgTempo = List(3) { index ->
            repMetricsList.map { it.tempo.getOrElse(index) { 0 } }.average().toInt()
        }
        val totalTUT = completedRepMetrics.sumOf { it.durationMs }
        val weights = completedRepMetrics.mapNotNull { it.weightKg }
        val totalVolume = if (weights.isNotEmpty()) weights.sum() else null
        val maxWeight = weights.maxOrNull()
        val countedReps = completedRepMetrics.count { it.worstState <= StateCode.PAD }
        val est1RM = if (maxWeight != null && countedReps > 0) {
            MetricsCalculator.calculateEst1RM(maxWeight, countedReps)
        } else {
            null
        }
        val scores = completedRepMetrics.map { it.score / 10f }
        return WorkoutExecutionMetrics(
            avgRom = avgRom,
            avgSymmetry = avgSymmetry,
            avgStability = avgStability,
            avgTempo = avgTempo,
            avgVelocity = avgVelocity,
            avgFormScore = avgFormScore,
            avgAlignmentAccuracy = avgAlignment,
            totalTUT = totalTUT,
            totalVolume = totalVolume,
            maxWeight = maxWeight,
            est1RM = est1RM,
            formConsistency = MetricsCalculator.calculateFormConsistencyFromScores(scores),
            fatigueIndex = MetricsCalculator.calculateFatigueIndexFromScores(scores),
            velocityLoss = repMetricsList.mapNotNull { it.velocityLoss }.maxOrNull(),
            tempoConsistency = MetricsCalculator.calculateTempoConsistency(
                completedRepMetrics.map { it.durationMs },
            ),
        )
    }

    private fun calculateBilateralSymmetryFromSides(): Short? {
        val leftRom = completedRepMetrics.filter { it.side == "left" }.map { it.metrics.rom }
        val rightRom = completedRepMetrics.filter { it.side == "right" }.map { it.metrics.rom }
        return MetricsCalculator.calculateBilateralRomSymmetry(leftRom, rightRom)
    }

    private fun emptyExecutionMetrics(): WorkoutExecutionMetrics = WorkoutExecutionMetrics(
        avgRom = 0,
        avgSymmetry = null,
        avgStability = 1000,
        avgTempo = listOf(0, 0, 0),
        avgVelocity = null,
        avgFormScore = 0,
        avgAlignmentAccuracy = 1000,
        totalTUT = 0,
        totalVolume = null,
        maxWeight = null,
        est1RM = null,
        formConsistency = null,
        fatigueIndex = null,
        velocityLoss = null,
        tempoConsistency = null,
    )

    private fun normalizeSide(side: String?): String? = when (side?.lowercase()) {
        "left" -> "left"
        "right" -> "right"
        else -> null
    }

    private companion object {
        const val MAX_FRAMES_PER_REP = 300
        const val MAX_REPS = 100
    }
}

private fun byteArrayEquals(a: ByteArray?, b: ByteArray?): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    return a.contentEquals(b)
}

private fun randomUuid(): String {
    val bytes = Random.nextBytes(16)
    bytes[6] = (bytes[6].toInt() and 0x0f or 0x40).toByte()
    bytes[8] = (bytes[8].toInt() and 0x3f or 0x80).toByte()
    val hex = bytes.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
        "${hex.substring(16, 20)}-${hex.substring(20)}"
}
