package com.movit.core.training.journal

import kotlin.math.abs
import kotlin.math.sqrt

object MetricsCalculator {
    fun calculateRepMetrics(
        frames: List<FrameSample>,
        primaryJointIndex: Int,
        leftJointIndex: Int? = null,
        rightJointIndex: Int? = null,
        stabilityJointIndex: Int? = null,
        hipIndices: Pair<Int, Int>? = null,
        phaseTimings: List<Int>,
        score: Short,
        bestVelocity: Short? = null,
    ): RepMetrics {
        if (frames.isEmpty()) {
            return createEmptyRepMetrics(phaseTimings, score)
        }

        val velocity = calculateVelocity(frames, primaryJointIndex)
        val velocityLoss = if (bestVelocity != null && bestVelocity > 0 && velocity != null) {
            val loss = ((bestVelocity - velocity).toFloat() / bestVelocity * 1000).toInt()
            loss.coerceIn(0, 1000).toShort()
        } else {
            null
        }

        val stability = if (stabilityJointIndex != null) {
            calculateTrunkStability(frames, stabilityJointIndex)
        } else {
            hipIndices?.let { calculateStability(frames, it) }
        }

        return RepMetrics(
            rom = calculateROM(frames, primaryJointIndex),
            symmetry = if (leftJointIndex != null && rightJointIndex != null) {
                calculateSymmetry(frames, leftJointIndex, rightJointIndex)
            } else {
                null
            },
            stability = stability,
            tempo = phaseTimings,
            velocity = velocity,
            formScore = score,
            alignmentAccuracy = calculateAlignmentAccuracy(frames),
            velocityLoss = velocityLoss,
        )
    }

    private fun createEmptyRepMetrics(phaseTimings: List<Int>, score: Short): RepMetrics = RepMetrics(
        rom = 0,
        symmetry = null,
        stability = null,
        tempo = phaseTimings,
        velocity = null,
        formScore = score,
        alignmentAccuracy = null,
        velocityLoss = null,
    )

    fun calculateWorkoutExecutionMetrics(
        reps: List<RepRecord>,
        primaryJointIndex: Int,
        leftJointIndex: Int? = null,
        rightJointIndex: Int? = null,
        stabilityJointIndex: Int? = null,
        hipIndices: Pair<Int, Int>? = null,
    ): WorkoutExecutionMetrics {
        if (reps.isEmpty()) return createEmptyWorkoutExecutionMetrics()

        var bestVelocity: Short? = null
        val repMetricsList = reps.map { rep ->
            val metrics = calculateRepMetrics(
                frames = rep.frames,
                primaryJointIndex = primaryJointIndex,
                leftJointIndex = leftJointIndex,
                rightJointIndex = rightJointIndex,
                stabilityJointIndex = stabilityJointIndex,
                hipIndices = hipIndices,
                phaseTimings = rep.phases,
                score = rep.score,
                bestVelocity = bestVelocity,
            )
            metrics.velocity?.let { velocity ->
                if (bestVelocity == null || velocity > bestVelocity!!) {
                    bestVelocity = velocity
                }
            }
            metrics
        }

        val avgRom = repMetricsList.map { it.rom }.average().toInt().toShort()
        val avgSymmetry = repMetricsList.mapNotNull { it.symmetry }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.toShort()
        val avgStability = repMetricsList.mapNotNull { it.stability }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.toShort()
        val avgVelocity = repMetricsList.mapNotNull { it.velocity }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.toShort()
        val avgFormScore = repMetricsList.map { it.formScore }.average().toInt().toShort()
        val avgAlignment = repMetricsList.mapNotNull { it.alignmentAccuracy }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.toShort()
        val avgTempo = List(3) { index ->
            repMetricsList.map { it.tempo.getOrElse(index) { 0 } }.average().toInt()
        }
        val totalTUT = reps.sumOf { it.durationMs }
        val weights = reps.mapNotNull { it.weightKg }
        val totalVolume = if (weights.isNotEmpty()) calculateVolume(reps) else null
        val maxWeight = weights.maxOrNull()
        val est1RM = if (maxWeight != null && reps.isNotEmpty()) {
            calculateEst1RM(maxWeight, reps.count { it.isCounted() })
        } else {
            null
        }

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
            formConsistency = calculateFormConsistency(reps, primaryJointIndex),
            fatigueIndex = calculateFatigueIndex(reps),
            velocityLoss = repMetricsList.mapNotNull { it.velocityLoss }.maxOrNull(),
            tempoConsistency = calculateTempoConsistencyFromRepMetrics(repMetricsList),
        )
    }

    private fun createEmptyWorkoutExecutionMetrics(): WorkoutExecutionMetrics = WorkoutExecutionMetrics(
        avgRom = 0,
        avgSymmetry = null,
        avgStability = null,
        avgTempo = listOf(0, 0, 0),
        avgVelocity = null,
        avgFormScore = 0,
        avgAlignmentAccuracy = null,
        totalTUT = 0,
        totalVolume = null,
        maxWeight = null,
        est1RM = null,
        formConsistency = null,
        fatigueIndex = null,
        velocityLoss = null,
        tempoConsistency = null,
    )

    fun calculatePrimaryROM(frames: List<FrameSample>, primaryIndices: List<Int>): Short {
        if (frames.isEmpty() || primaryIndices.isEmpty()) return 0
        return primaryIndices.maxOfOrNull { calculateROM(frames, it) } ?: 0
    }

    fun primaryJointIndexForVelocity(frames: List<FrameSample>, primaryIndices: List<Int>): Int {
        val concentric = frames.filter { it.phase == PhaseCode.CONCENTRIC }
        primaryIndices.firstOrNull { index ->
            concentric.count { it.isJointAngleValid(index) } >= 2
        }?.let { return it }

        for (index in primaryIndices) {
            if (frames.any { it.isJointAngleValid(index) }) return index
        }
        return primaryIndices.firstOrNull() ?: 0
    }

    fun calculateROM(frames: List<FrameSample>, jointIndex: Int): Short {
        if (frames.isEmpty() || jointIndex < 0) return 0
        val angles = frames.mapNotNull { frame ->
            val value = frame.angles.getOrNull(jointIndex) ?: return@mapNotNull null
            if (value == JOINT_SKIPPED_ANGLE_SENTINEL) return@mapNotNull null
            value.toInt()
        }
        if (angles.isEmpty()) return 0
        return (angles.maxOrNull()!! - angles.minOrNull()!!)
            .coerceIn(0, Short.MAX_VALUE.toInt())
            .toShort()
    }

    fun calculateSymmetry(frames: List<FrameSample>, leftIdx: Int, rightIdx: Int): Short? {
        if (frames.isEmpty()) return null
        var totalDiff = 0.0
        var count = 0
        for (frame in frames) {
            if (!frame.isJointAngleValid(leftIdx) || !frame.isJointAngleValid(rightIdx)) continue
            totalDiff += abs(frame.angles[leftIdx].toInt() - frame.angles[rightIdx].toInt()) / 10.0
            count++
        }
        if (count == 0) return null
        val average = totalDiff / count
        return (1000 - average.coerceAtMost(100.0) * 10).toInt().coerceIn(0, 1000).toShort()
    }

    fun calculateBilateralRomSymmetry(leftRepsRom: List<Short>, rightRepsRom: List<Short>): Short? {
        if (leftRepsRom.isEmpty() || rightRepsRom.isEmpty()) return null
        val avgLeft = leftRepsRom.map { it.toInt() }.average()
        val avgRight = rightRepsRom.map { it.toInt() }.average()
        val maxAvg = maxOf(avgLeft, avgRight)
        val minAvg = minOf(avgLeft, avgRight)
        if (maxAvg <= 0) return null
        return (minAvg / maxAvg * 1000).toInt().coerceIn(0, 1000).toShort()
    }

    fun calculateTrunkStability(frames: List<FrameSample>, spineIndex: Int): Short? {
        val spineAngles = extractValidAngles(frames, spineIndex) ?: return null
        return stabilityScoreFromDetrendedSpread(spineAngles, spreadScale = 300.0)
    }

    fun calculateStability(frames: List<FrameSample>, hipIndices: Pair<Int, Int>): Short? {
        if (frames.isEmpty()) return null
        val midpoints = frames.mapNotNull { frame ->
            val left = frame.angles.getOrNull(hipIndices.first) ?: return@mapNotNull null
            val right = frame.angles.getOrNull(hipIndices.second) ?: return@mapNotNull null
            if (left == JOINT_SKIPPED_ANGLE_SENTINEL || right == JOINT_SKIPPED_ANGLE_SENTINEL) {
                return@mapNotNull null
            }
            (left.toInt() + right.toInt()) / 2
        }
        return stabilityScoreFromDetrendedSpread(midpoints, spreadScale = 500.0)
    }

    fun calculateVelocity(frames: List<FrameSample>, jointIndex: Int): Short? {
        val concentric = frames.filter { it.phase == PhaseCode.CONCENTRIC }
        val valid = concentric.filter { frame ->
            val value = frame.angles.getOrNull(jointIndex)
            value != null && value != JOINT_SKIPPED_ANGLE_SENTINEL
        }
        if (valid.size < 2) return null
        val firstAngle = valid.first().angles[jointIndex].toInt()
        val lastAngle = valid.last().angles[jointIndex].toInt()
        val timeDeltaMs = valid.last().t - valid.first().t
        if (timeDeltaMs <= 0) return null
        val angleDelta = abs(lastAngle - firstAngle)
        val timeSec = timeDeltaMs / 1000f
        return (angleDelta / timeSec / 10).toInt().coerceIn(0, Short.MAX_VALUE.toInt()).toShort()
    }

    fun calculateAlignmentAccuracy(frames: List<FrameSample>): Short? {
        if (frames.isEmpty()) return null
        var lastStates: ByteArray? = null
        var framesWithStates = 0
        var goodFrames = 0
        for (frame in frames) {
            frame.states?.let { lastStates = it }
            val effectiveStates = frame.states ?: lastStates ?: continue
            if (effectiveStates.isEmpty()) continue
            framesWithStates++
            if (effectiveStates.all { StateCode.isGood(it) }) {
                goodFrames++
            }
        }
        if (framesWithStates == 0) return null
        return (goodFrames.toFloat() / framesWithStates * 1000).toInt().coerceIn(0, 1000).toShort()
    }

    fun calculateFormConsistency(reps: List<RepRecord>, jointIndex: Int): Short? {
        if (reps.size < 4) return null
        val firstReps = reps.take(3).flatMap { rep ->
            rep.frames.mapNotNull { frame ->
                val value = frame.angles.getOrNull(jointIndex) ?: return@mapNotNull null
                if (value == JOINT_SKIPPED_ANGLE_SENTINEL) return@mapNotNull null
                value
            }
        }
        val lastReps = reps.takeLast(3).flatMap { rep ->
            rep.frames.mapNotNull { frame ->
                val value = frame.angles.getOrNull(jointIndex) ?: return@mapNotNull null
                if (value == JOINT_SKIPPED_ANGLE_SENTINEL) return@mapNotNull null
                value
            }
        }
        if (firstReps.isEmpty() || lastReps.isEmpty()) return null
        val distance = dynamicTimeWarping(firstReps, lastReps)
        return (1000 - distance.coerceIn(0, 1000)).toShort()
    }

    fun calculateFormConsistencyFromScores(scores: List<Float>): Short? {
        if (scores.size < 4) return null
        val mean = scores.average()
        val variance = scores.map { delta -> (delta - mean) * (delta - mean) }.average()
        val stdDev = sqrt(variance)
        return ((100 - (stdDev * 3.33)).coerceIn(0.0, 100.0) * 10).toInt().toShort()
    }

    fun calculateFatigueIndex(reps: List<RepRecord>): Short? {
        if (reps.size < 4) return null
        return calculateFatigueIndexFromScores(reps.map { it.score / 10f })
    }

    /** Returns 1-based onset rep (`fatigueOnsetRep` semantics), not a percentage. */
    fun calculateFatigueIndexFromScores(scores: List<Float>): Short? {
        if (scores.size < 4) return null
        val halfSize = scores.size / 2
        val firstHalfAvg = scores.take(halfSize).average()
        val threshold = firstHalfAvg * 0.8
        for (index in halfSize until scores.size) {
            if (scores[index] < threshold) {
                return (index + 1).toShort()
            }
        }
        return null
    }

    fun calculateTempoConsistencyFromRepMetrics(repMetrics: List<RepMetrics>): Short? {
        if (repMetrics.size < 3) return null
        val durations = repMetrics.map { it.tempo.sum() }.filter { it > 0 }
        if (durations.size < 3) return null
        val std = standardDeviation(durations)
        val mean = durations.average()
        if (mean <= 0) return 1000
        val cv = (std / mean) * 100
        return ((100 - (cv * 2.5)).coerceIn(0.0, 100.0) * 10).toInt().toShort()
    }

    fun calculateTempoConsistency(durations: List<Int>): Short? {
        if (durations.size < 3) return null
        val filtered = durations.filter { it > 0 }
        if (filtered.size < 3) return null
        val std = standardDeviation(filtered)
        val mean = filtered.average()
        if (mean <= 0) return 1000
        val cv = (std / mean) * 100
        return ((100 - (cv * 2.5)).coerceIn(0.0, 100.0) * 10).toInt().toShort()
    }

    fun calculateEst1RM(weight: Float, reps: Int): Float {
        if (weight <= 0 || reps <= 0) return 0f
        return if (reps == 1) weight else weight * (1 + reps / 30f)
    }

    fun calculateVolume(reps: List<RepRecord>): Float =
        reps.filter { it.isCounted() }.sumOf { (it.weightKg ?: 0f).toDouble() }.toFloat()

    private fun extractValidAngles(frames: List<FrameSample>, jointIndex: Int): List<Int>? {
        if (frames.isEmpty()) return null
        val angles = frames.mapNotNull { frame ->
            val value = frame.angles.getOrNull(jointIndex) ?: return@mapNotNull null
            if (value == JOINT_SKIPPED_ANGLE_SENTINEL) return@mapNotNull null
            value.toInt()
        }
        return angles.takeIf { it.size >= 2 }
    }

    private fun stabilityScoreFromDetrendedSpread(angles: List<Int>, spreadScale: Double): Short? {
        if (angles.size < 2) return null
        val std = detrendedStandardDeviation(angles)
        return ((1 - std / spreadScale).coerceIn(0.0, 1.0) * 1000).toInt().toShort()
    }

    /** Residual spread after removing a linear trend — avoids punishing intentional joint movement. */
    private fun detrendedStandardDeviation(values: List<Int>): Double {
        if (values.size < 3) return standardDeviation(values)
        val n = values.size
        val meanIndex = (n - 1) / 2.0
        val meanValue = values.average()
        var slopeNumerator = 0.0
        var slopeDenominator = 0.0
        for (index in 0 until n) {
            val centeredIndex = index - meanIndex
            slopeNumerator += centeredIndex * (values[index] - meanValue)
            slopeDenominator += centeredIndex * centeredIndex
        }
        val slope = if (slopeDenominator == 0.0) 0.0 else slopeNumerator / slopeDenominator
        val intercept = meanValue - slope * meanIndex
        val residuals = values.indices.map { index -> values[index] - (intercept + slope * index) }
        return standardDeviation(residuals.map { it.toInt() })
    }

    private fun standardDeviation(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { value -> (value - mean) * (value - mean) }.average()
        return sqrt(variance)
    }

    private fun dynamicTimeWarping(seq1: List<Short>, seq2: List<Short>): Int {
        val n = seq1.size
        val m = seq2.size
        if (n == 0 || m == 0) return 1000
        val inf = Int.MAX_VALUE / 2
        val dtw = Array(n + 1) { IntArray(m + 1) { inf } }
        dtw[0][0] = 0
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = abs(seq1[i - 1].toInt() - seq2[j - 1].toInt())
                dtw[i][j] = cost + minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
            }
        }
        return (dtw[n][m] / (n + m)).coerceIn(0, 1000)
    }
}
