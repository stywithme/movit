package com.trainingvalidator.poc.training.analytics

import com.trainingvalidator.poc.training.models.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * MetricsCalculator - Calculates performance metrics from motion data
 * 
 * All calculations are performed ONCE after session ends to avoid
 * impacting real-time FPS during exercise.
 * 
 * Metric Categories:
 * 1. Kinematic (ROM, Symmetry, Stability)
 * 2. Temporal (Tempo, TUT)
 * 3. Power (Velocity)
 * 4. Quality (Alignment, Consistency, Fatigue)
 * 5. Load (Volume, 1RM)
 */
object MetricsCalculator {
    
    // ==================== Rep-Level Metrics ====================
    
    /**
     * Calculate all metrics for a single rep
     * 
     * @param frames Frame samples for this rep
     * @param primaryJointIndex Index of primary joint for ROM/velocity
     * @param leftJointIndex Index of left joint for symmetry (null for single-side)
     * @param rightJointIndex Index of right joint for symmetry (null for single-side)
     * @param hipIndices Indices of left and right hip for stability
     * @param phaseTimings Phase durations [eccentric, iso, concentric]
     * @param score Form score from RepCounter
     */
    fun calculateRepMetrics(
        frames: List<FrameSample>,
        primaryJointIndex: Int,
        leftJointIndex: Int? = null,
        rightJointIndex: Int? = null,
        hipIndices: Pair<Int, Int>? = null,
        phaseTimings: IntArray,
        score: Short
    ): RepMetrics {
        if (frames.isEmpty()) {
            return createEmptyRepMetrics(phaseTimings, score)
        }
        
        return RepMetrics(
            rom = calculateROM(frames, primaryJointIndex),
            symmetry = if (leftJointIndex != null && rightJointIndex != null) {
                calculateSymmetry(frames, leftJointIndex, rightJointIndex)
            } else null,
            stability = hipIndices?.let { calculateStability(frames, it) } ?: 1000,
            tempo = phaseTimings,
            velocity = calculateVelocity(frames, primaryJointIndex),
            formScore = score,
            alignmentAccuracy = calculateAlignmentAccuracy(frames)
        )
    }
    
    /**
     * Create empty metrics when no frames available
     */
    private fun createEmptyRepMetrics(phaseTimings: IntArray, score: Short): RepMetrics {
        return RepMetrics(
            rom = 0,
            symmetry = null,
            stability = 1000,
            tempo = phaseTimings,
            velocity = null,
            formScore = score,
            alignmentAccuracy = 1000
        )
    }
    
    // ==================== Session-Level Metrics ====================
    
    /**
     * Calculate all metrics for a session from rep records
     * 
     * @param reps All rep records for the session
     * @param primaryJointIndex Index of primary joint
     * @param leftJointIndex Index of left joint for symmetry
     * @param rightJointIndex Index of right joint for symmetry
     * @param hipIndices Hip joint indices for stability
     */
    fun calculateSessionMetrics(
        reps: List<RepRecord>,
        primaryJointIndex: Int,
        leftJointIndex: Int? = null,
        rightJointIndex: Int? = null,
        hipIndices: Pair<Int, Int>? = null
    ): SessionMetrics {
        if (reps.isEmpty()) {
            return createEmptySessionMetrics()
        }
        
        // Calculate metrics for each rep
        val repMetricsList = reps.map { rep ->
            calculateRepMetrics(
                frames = rep.frames,
                primaryJointIndex = primaryJointIndex,
                leftJointIndex = leftJointIndex,
                rightJointIndex = rightJointIndex,
                hipIndices = hipIndices,
                phaseTimings = rep.phases,
                score = rep.score
            )
        }
        
        // Aggregate averages
        val avgRom = repMetricsList.map { it.rom }.average().toInt().toShort()
        val avgSymmetry = repMetricsList.mapNotNull { it.symmetry }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.toShort()
        val avgStability = repMetricsList.map { it.stability }.average().toInt().toShort()
        val avgVelocity = repMetricsList.mapNotNull { it.velocity }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.toShort()
        val avgFormScore = repMetricsList.map { it.formScore }.average().toInt().toShort()
        val avgAlignment = repMetricsList.map { it.alignmentAccuracy }.average().toInt().toShort()
        
        // Average tempo
        val avgTempo = IntArray(3) { i ->
            repMetricsList.map { it.tempo.getOrElse(i) { 0 } }.average().toInt()
        }
        
        // Total TUT (sum of all rep durations)
        val totalTUT = reps.sumOf { it.durationMs }
        
        // Load metrics
        val weights = reps.mapNotNull { it.weightKg }
        val totalVolume = if (weights.isNotEmpty()) {
            calculateVolume(reps)
        } else null
        val maxWeight = weights.maxOrNull()
        val est1RM = if (maxWeight != null && reps.isNotEmpty()) {
            calculateEst1RM(maxWeight, reps.count { it.isCounted() })
        } else null
        
        // Quality metrics
        val formConsistency = calculateFormConsistency(reps, primaryJointIndex)
        val fatigueIndex = calculateFatigueIndex(reps)
        
        return SessionMetrics(
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
            formConsistency = formConsistency,
            fatigueIndex = fatigueIndex
        )
    }
    
    /**
     * Create empty session metrics
     */
    private fun createEmptySessionMetrics(): SessionMetrics {
        return SessionMetrics(
            avgRom = 0,
            avgSymmetry = null,
            avgStability = 1000,
            avgTempo = intArrayOf(0, 0, 0),
            avgVelocity = null,
            avgFormScore = 0,
            avgAlignmentAccuracy = 1000,
            totalTUT = 0,
            totalVolume = null,
            maxWeight = null,
            est1RM = null,
            formConsistency = null,
            fatigueIndex = null
        )
    }
    
    // ==================== Kinematic Metrics ====================
    
    /**
     * Calculate Range of Motion (max - min angle)
     * 
     * @return ROM × 10 (e.g., 90° → 900)
     */
    fun calculateROM(frames: List<FrameSample>, jointIndex: Int): Short {
        if (frames.isEmpty() || jointIndex < 0) return 0
        
        val angles = frames.mapNotNull { 
            it.angles.getOrNull(jointIndex)?.toInt() 
        }
        if (angles.isEmpty()) return 0
        
        val max = angles.maxOrNull() ?: 0
        val min = angles.minOrNull() ?: 0
        return (max - min).coerceIn(0, Short.MAX_VALUE.toInt()).toShort()
    }
    
    /**
     * Calculate bilateral symmetry (how similar left and right sides are)
     * 
     * @return Symmetry score × 10 (1000 = perfect symmetry, 0 = 180° difference)
     */
    fun calculateSymmetry(frames: List<FrameSample>, leftIdx: Int, rightIdx: Int): Short {
        if (frames.isEmpty()) return 1000
        
        val diffs = frames.mapNotNull { frame ->
            val left = frame.angles.getOrNull(leftIdx)?.toInt()
            val right = frame.angles.getOrNull(rightIdx)?.toInt()
            if (left != null && right != null) abs(left - right) else null
        }
        
        if (diffs.isEmpty()) return 1000
        
        val avgDiff = diffs.average()
        // 0 diff = 100%, 1800 (180°) diff = 0%
        val score = ((1 - avgDiff / 1800.0) * 1000).toInt().coerceIn(0, 1000)
        return score.toShort()
    }
    
    /**
     * Calculate core stability from hip angle variance
     * 
     * Lower variance = higher stability score
     * 
     * @return Stability score × 10 (1000 = perfectly stable)
     */
    fun calculateStability(frames: List<FrameSample>, hipIndices: Pair<Int, Int>): Short {
        if (frames.isEmpty()) return 1000
        
        // Calculate midpoint of hips for each frame
        val midpoints = frames.mapNotNull { frame ->
            val left = frame.angles.getOrNull(hipIndices.first)?.toInt()
            val right = frame.angles.getOrNull(hipIndices.second)?.toInt()
            if (left != null && right != null) (left + right) / 2 else null
        }
        
        if (midpoints.size < 2) return 1000
        
        val std = standardDeviation(midpoints)
        // Low std = high stability. 500 units std = 0% stability
        val score = ((1 - std / 500.0).coerceIn(0.0, 1.0) * 1000).toInt()
        return score.toShort()
    }
    
    // ==================== Power Metrics ====================
    
    /**
     * Calculate mean concentric velocity (angular velocity during lifting phase)
     * 
     * @return Velocity × 100 (degrees/second / 100)
     */
    fun calculateVelocity(frames: List<FrameSample>, jointIndex: Int): Short? {
        // Filter to concentric phase only
        val concentric = frames.filter { it.phase == PhaseCode.CONCENTRIC }
        if (concentric.size < 2) return null
        
        val firstAngle = concentric.first().angles.getOrNull(jointIndex)?.toInt() ?: return null
        val lastAngle = concentric.last().angles.getOrNull(jointIndex)?.toInt() ?: return null
        val timeDeltaMs = concentric.last().t - concentric.first().t
        
        if (timeDeltaMs <= 0) return null
        
        val angleDelta = abs(lastAngle - firstAngle)
        val timeSec = timeDeltaMs / 1000f
        
        // Velocity in degrees/second, divided by 10 for storage
        val velocity = (angleDelta / timeSec / 10).toInt().coerceIn(0, Short.MAX_VALUE.toInt())
        return velocity.toShort()
    }
    
    // ==================== Quality Metrics ====================
    
    /**
     * Calculate alignment accuracy (percentage of time in good form)
     * 
     * Good form = PERFECT or NORMAL state
     * 
     * @return Accuracy × 10 (1000 = 100% perfect form)
     */
    fun calculateAlignmentAccuracy(frames: List<FrameSample>): Short {
        if (frames.isEmpty()) return 1000
        
        val goodFrames = frames.count { frame ->
            frame.states?.all { StateCode.isGood(it) } ?: true
        }
        
        val score = (goodFrames.toFloat() / frames.size * 1000).toInt()
        return score.coerceIn(0, 1000).toShort()
    }
    
    /**
     * Calculate form consistency using Dynamic Time Warping
     * 
     * Compares first 3 reps with last 3 reps to detect technique breakdown
     * 
     * @return Consistency score (1000 = identical patterns, lower = more variation)
     */
    fun calculateFormConsistency(reps: List<RepRecord>, jointIndex: Int): Short? {
        if (reps.size < 4) return null
        
        // Get angle sequences from first and last 3 reps
        val firstReps = reps.take(3).flatMap { rep ->
            rep.frames.mapNotNull { it.angles.getOrNull(jointIndex) }
        }
        val lastReps = reps.takeLast(3).flatMap { rep ->
            rep.frames.mapNotNull { it.angles.getOrNull(jointIndex) }
        }
        
        if (firstReps.isEmpty() || lastReps.isEmpty()) return null
        
        val dtw = dynamicTimeWarping(firstReps, lastReps)
        return (1000 - dtw.coerceIn(0, 1000)).toShort()
    }
    
    /**
     * Calculate fatigue index (rep number where performance dropped)
     * 
     * Looks for 20%+ drop in score compared to first half average
     * 
     * @return Rep number where fatigue started (1-based), null if no significant fatigue
     */
    fun calculateFatigueIndex(reps: List<RepRecord>): Short? {
        if (reps.size < 4) return null
        
        val firstHalfAvg = reps.take(reps.size / 2).map { it.score }.average()
        
        for (i in reps.indices) {
            if (reps[i].score < firstHalfAvg * 0.8) { // 20% drop
                return (i + 1).toShort() // 1-based rep number
            }
        }
        
        return null // No significant fatigue detected
    }
    
    // ==================== Load Metrics ====================
    
    /**
     * Calculate estimated 1 Rep Max using Epley formula
     * 
     * 1RM = weight × (1 + reps/30)
     */
    fun calculateEst1RM(weight: Float, reps: Int): Float {
        if (weight <= 0 || reps <= 0) return 0f
        return if (reps == 1) weight else weight * (1 + reps / 30f)
    }
    
    /**
     * Calculate total training volume (sum of weight × 1 for each rep)
     */
    fun calculateVolume(reps: List<RepRecord>): Float {
        return reps.sumOf { (it.weightKg ?: 0f).toDouble() }.toFloat()
    }
    
    // ==================== Helper Functions ====================
    
    /**
     * Calculate standard deviation of a list of integers
     */
    private fun standardDeviation(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
    
    /**
     * Dynamic Time Warping algorithm for comparing movement patterns
     * 
     * Returns normalized distance (0 = identical, higher = more different)
     */
    private fun dynamicTimeWarping(seq1: List<Short>, seq2: List<Short>): Int {
        val n = seq1.size
        val m = seq2.size
        
        if (n == 0 || m == 0) return 1000
        
        // Use Int.MAX_VALUE / 2 to avoid overflow when adding
        val INF = Int.MAX_VALUE / 2
        val dtw = Array(n + 1) { IntArray(m + 1) { INF } }
        dtw[0][0] = 0
        
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = abs(seq1[i - 1].toInt() - seq2[j - 1].toInt())
                dtw[i][j] = cost + minOf(
                    dtw[i - 1][j],      // insertion
                    dtw[i][j - 1],      // deletion
                    dtw[i - 1][j - 1]   // match
                )
            }
        }
        
        // Normalize by path length
        return (dtw[n][m] / (n + m)).coerceIn(0, 1000)
    }
}
