package com.trainingvalidator.poc.training.analytics

import android.util.Log
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.models.*
import java.util.UUID

/**
 * MotionRecorder - Records motion data during training for analytics
 * 
 * OPTIMIZED VERSION: No raw data storage
 * 
 * Design Philosophy:
 * - Raw frames are kept ONLY in memory during current rep (for real-time metrics)
 * - When rep completes, metrics are calculated and frames are DISCARDED
 * - Only computed metrics are stored, not raw data
 * - This saves ~95% storage and reduces server upload size
 * 
 * Data Flow:
 * TrainingEngine.processFrame() → record() → buffer frames temporarily
 * RepCounter.completeRep() → finalizeRep() → calculate metrics → DISCARD frames
 * Session end → finalize() → SessionUpload (metrics only, no raw data)
 * 
 * Memory Management:
 * - Only current rep's frames in memory (~3KB max)
 * - Frames discarded after each rep
 * - Final output: ~500 bytes of metrics per session
 */
class MotionRecorder(
    private val trackedJoints: List<String>,
    private val exerciseId: String,
    private val defaultWeightKg: Float? = null,
    private val weightUnit: String = "kg"
) {
    companion object {
        private const val TAG = "MotionRecorder"
        
        // Safety limits
        private const val MAX_FRAMES_PER_REP = 300   // 10 seconds at 30fps
        private const val MAX_REPS = 100              // Prevent memory bloat
    }
    
    // Session state
    private var sessionStartMs: Long = 0L
    private var isRecording = false
    
    // Current rep buffer (TEMPORARY - discarded after metrics calculation)
    private val currentRepBuffer = mutableListOf<FrameSample>()
    private var currentRepStartT: Int = 0
    private var lastStates: ByteArray? = null  // For state-change detection
    private var maxFramesWarningLogged = false  // Prevent log spam when buffer is full
    
    // Completed rep METRICS only (no raw frames stored)
    private val completedRepMetrics = mutableListOf<RepMetricsData>()
    
    // Joint indices for metrics calculation
    private var primaryJointIndex: Int = 0
    private var leftJointIndex: Int? = null
    private var rightJointIndex: Int? = null
    private var hipIndices: Pair<Int, Int>? = null
    private var spineJointIndex: Int? = null
    
    // Track best velocity for VL% calculation across reps
    private var bestVelocity: Short? = null
    
    /**
     * Start recording a new session
     */
    fun start(startTimestampMs: Long = System.currentTimeMillis()) {
        sessionStartMs = startTimestampMs
        isRecording = true
        currentRepBuffer.clear()
        completedRepMetrics.clear()
        lastStates = null
        bestVelocity = null
        
        // Determine joint indices for metrics
        setupJointIndices()
        
        Log.d(TAG, "Motion recording started. Tracking ${trackedJoints.size} joints: $trackedJoints")
    }
    
    /**
     * Set up joint indices based on tracked joint names
     */
    private fun setupJointIndices() {
        // Primary joint is first in list
        primaryJointIndex = 0
        
        // Find left/right pairs for symmetry
        val leftKnee = trackedJoints.indexOfFirst { it.contains("left_knee", true) }
        val rightKnee = trackedJoints.indexOfFirst { it.contains("right_knee", true) }
        if (leftKnee >= 0 && rightKnee >= 0) {
            leftJointIndex = leftKnee
            rightJointIndex = rightKnee
        }
        
        // Find hips for stability (fallback)
        val leftHip = trackedJoints.indexOfFirst { it.contains("left_hip", true) }
        val rightHip = trackedJoints.indexOfFirst { it.contains("right_hip", true) }
        if (leftHip >= 0 && rightHip >= 0) {
            hipIndices = Pair(leftHip, rightHip)
        }
        
        // Find spine for trunk stability (preferred over hip fallback)
        val spine = trackedJoints.indexOfFirst { it.equals("spine", true) }
        if (spine >= 0) {
            spineJointIndex = spine
        }
    }
    
    /**
     * Record a single frame during training
     * 
     * Called from TrainingEngine.processFrame() after angle calculation
     * Frames are kept temporarily and discarded after rep metrics are calculated.
     * 
     * @param timestamp Current timestamp in milliseconds
     * @param phase Current movement phase
     * @param angles Smoothed joint angles (Map from TrainingEngine)
     * @param states Joint state infos (optional, from [JointEvaluator] / [JointStateInfo] map)
     */
    fun record(
        timestamp: Long,
        phase: Phase,
        angles: Map<String, Double>,
        states: Map<String, JointStateInfo>? = null,
        skippedJointCodes: Set<String> = emptySet()
    ) {
        if (!isRecording) return
        
        if (sessionStartMs == 0L) {
            sessionStartMs = timestamp
        }
        
        // Safety: limit frames per rep
        if (currentRepBuffer.size >= MAX_FRAMES_PER_REP) {
            if (!maxFramesWarningLogged) {
                Log.w(TAG, "Max frames per rep reached ($MAX_FRAMES_PER_REP), discarding stale buffer and restarting")
                maxFramesWarningLogged = true
            }
            // Auto-clear stale buffer so fresh frames can be recorded
            // (if rep never completes, old data isn't useful)
            currentRepBuffer.clear()
            lastStates = null
            currentRepStartT = (timestamp - sessionStartMs).toInt()
            // Don't return - let the new frame be recorded below
        }
        
        // Calculate relative time
        val relativeT = (timestamp - sessionStartMs).toInt()
        
        // Convert angles to ShortArray
        val angleArray = anglesToShortArray(angles, skippedJointCodes)
        
        // Convert states to ByteArray (only if changed)
        val stateArray = statesToByteArray(states)
        val statesForStorage = if (byteArrayEquals(stateArray, lastStates)) {
            null  // No change, don't store
        } else {
            lastStates = stateArray
            stateArray
        }
        
        // Create frame sample
        val sample = FrameSample(
            t = relativeT,
            phase = PhaseCode.fromPhase(phase),
            angles = angleArray,
            states = statesForStorage
        )
        
        // Add to buffer
        currentRepBuffer.add(sample)
        
        // Track rep start time
        if (currentRepBuffer.size == 1) {
            currentRepStartT = relativeT
        }
    }
    
    /**
     * Finalize current rep when RepCounter.completeRep() is called
     * 
     * IMPORTANT: This calculates metrics from buffered frames, then DISCARDS the frames.
     * Only computed metrics are kept.
     * 
     * @param repNumber Rep number (1-based)
     * @param phaseTimings Phase durations from RepCounter [eccentric, iso, concentric]
     * @param worstState Worst joint state reached during rep
     * @param score Form score (0-100)
     * @param weightKg Weight used for this rep (null for bodyweight)
     */
    fun finalizeRep(
        repNumber: Int,
        phaseTimings: Map<String, Long>,
        worstState: JointState,
        score: Float,
        weightKg: Float? = null
    ) {
        if (!isRecording) return
        
        // Safety: limit total reps
        if (completedRepMetrics.size >= MAX_REPS) {
            Log.w(TAG, "Max reps reached ($MAX_REPS), skipping")
            currentRepBuffer.clear()
            return
        }
        
        if (currentRepBuffer.isEmpty()) {
            Log.w(TAG, "No frames recorded for rep $repNumber")
            return
        }
        
        // Reset warning flag for next rep
        maxFramesWarningLogged = false
        
        val endT = currentRepBuffer.lastOrNull()?.t ?: currentRepStartT
        val durationMs = endT - currentRepStartT
        
        // Convert phase timings
        val phases = intArrayOf(
            phaseTimings["eccentric"]?.toInt() ?: phaseTimings["down"]?.toInt() ?: 0,
            phaseTimings["isometric"]?.toInt() ?: phaseTimings["bottom"]?.toInt() 
                ?: phaseTimings["extended"]?.toInt() ?: 0,
            phaseTimings["concentric"]?.toInt() ?: phaseTimings["up"]?.toInt() 
                ?: phaseTimings["push"]?.toInt() ?: phaseTimings["pull"]?.toInt() ?: 0
        )
        
        // Calculate metrics from buffered frames
        val velocity = MetricsCalculator.calculateVelocity(currentRepBuffer, primaryJointIndex)
        
        // Velocity Loss: compare current velocity against session best
        val velocityLoss = if (bestVelocity != null && bestVelocity!! > 0 && velocity != null) {
            val vl = ((bestVelocity!! - velocity).toFloat() / bestVelocity!! * 1000).toInt()
            vl.coerceIn(0, 1000).toShort()
        } else null
        
        // Update best velocity for next rep
        velocity?.let { v ->
            if (bestVelocity == null || v > bestVelocity!!) {
                bestVelocity = v
            }
        }
        
        // Trunk stability: prefer spine angle variance, fall back to hip midpoint variance
        val stability = if (spineJointIndex != null) {
            MetricsCalculator.calculateTrunkStability(currentRepBuffer, spineJointIndex!!)
        } else {
            hipIndices?.let { MetricsCalculator.calculateStability(currentRepBuffer, it) } ?: 1000
        }
        
        val repMetrics = RepMetrics(
            rom = MetricsCalculator.calculateROM(currentRepBuffer, primaryJointIndex),
            symmetry = if (leftJointIndex != null && rightJointIndex != null) {
                MetricsCalculator.calculateSymmetry(currentRepBuffer, leftJointIndex!!, rightJointIndex!!)
            } else null,
            stability = stability,
            tempo = phases,
            velocity = velocity,
            formScore = (score * 10).toInt().toShort(),
            alignmentAccuracy = MetricsCalculator.calculateAlignmentAccuracy(currentRepBuffer),
            velocityLoss = velocityLoss
        )
        
        // Store ONLY metrics, not raw frames
        val repData = RepMetricsData(
            num = repNumber,
            durationMs = durationMs,
            worstState = StateCode.fromJointState(worstState),
            score = (score * 10).toInt().toShort(),
            weightKg = weightKg ?: defaultWeightKg,
            metrics = repMetrics
        )
        
        completedRepMetrics.add(repData)
        
        Log.d(TAG, "Rep $repNumber finalized: ${currentRepBuffer.size} frames processed → metrics saved, frames DISCARDED")
        
        // DISCARD frames - only metrics are kept
        currentRepBuffer.clear()
        lastStates = null
    }
    
    /**
     * Finalize session and create SessionUpload
     * 
     * Called when training ends. Aggregates all rep metrics.
     * Returns a lightweight object ready for server upload.
     * 
     * @param sessionId Optional session ID (generated if null)
     * @return SessionUpload ready for API upload
     */
    fun finalize(sessionId: String? = null, endTimestampMs: Long = System.currentTimeMillis()): SessionUpload {
        isRecording = false
        
        val id = sessionId ?: UUID.randomUUID().toString()
        // Never cast (end - start) straight to Int: wall ms since epoch (~1e12) overflows Int → negative DB values.
        val deltaMs = (endTimestampMs - sessionStartMs).coerceAtLeast(0L)
        val durationMs = deltaMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        
        // Calculate aggregated session metrics
        val sessionMetrics = calculateSessionMetrics()
        
        val upload = SessionUpload(
            id = id,
            exerciseId = exerciseId,
            timestamp = sessionStartMs,
            durationMs = durationMs,
            totalReps = completedRepMetrics.size,
            countedReps = completedRepMetrics.count { it.worstState <= StateCode.PAD },
            invalidReps = completedRepMetrics.count { it.worstState == StateCode.DANGER },
            weightKg = defaultWeightKg,
            weightUnit = weightUnit,
            repMetrics = completedRepMetrics.toList(),
            sessionMetrics = sessionMetrics
        )
        
        Log.d(TAG, "Session finalized: ${completedRepMetrics.size} reps, " +
                   "avgScore=${sessionMetrics.avgFormScore / 10f}%, " +
                   "NO raw data saved (metrics only)")
        
        return upload
    }
    
    /**
     * Calculate aggregated session metrics from all rep metrics
     */
    private fun calculateSessionMetrics(): SessionMetrics {
        if (completedRepMetrics.isEmpty()) {
            return createEmptySessionMetrics()
        }
        
        val repMetricsList = completedRepMetrics.map { it.metrics }
        
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
        val totalTUT = completedRepMetrics.sumOf { it.durationMs }
        
        // Load metrics
        // Uses same logic as MetricsCalculator.calculateVolume for consistency
        val weights = completedRepMetrics.mapNotNull { it.weightKg }
        val totalVolume = if (weights.isNotEmpty()) {
            weights.sum()  // Sum of weight per rep (same as MetricsCalculator.calculateVolume)
        } else null
        val maxWeight = weights.maxOrNull()
        val countedReps = completedRepMetrics.count { it.worstState <= StateCode.PAD }
        val est1RM = if (maxWeight != null && countedReps > 0) {
            MetricsCalculator.calculateEst1RM(maxWeight, countedReps)
        } else null
        
        // Quality metrics (Form Consistency and Fatigue Index)
        // These require comparing rep-to-rep patterns, which we now calculate from RepMetrics
        val formConsistency = calculateFormConsistencyFromMetrics()
        val fatigueIndex = calculateFatigueIndexFromMetrics()
        
        // New metrics (V2)
        val maxVelocityLoss = repMetricsList.mapNotNull { it.velocityLoss }
            .maxOrNull()
        val tempoConsistency = MetricsCalculator.calculateTempoConsistency(
            completedRepMetrics.map { it.durationMs }
        )
        
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
            fatigueIndex = fatigueIndex,
            velocityLoss = maxVelocityLoss,
            tempoConsistency = tempoConsistency
        )
    }
    
    /**
     * Calculate form consistency from rep metrics (without raw frames)
     * 
     * Uses MetricsCalculator (Single Source of Truth).
     * Uses score variance as a proxy for form consistency.
     */
    private fun calculateFormConsistencyFromMetrics(): Short? {
        if (completedRepMetrics.size < 4) return null
        
        // Convert scores from ×10 format to regular float
        val scores = completedRepMetrics.map { it.score / 10f }
        return MetricsCalculator.calculateFormConsistencyFromScores(scores)
    }
    
    /**
     * Calculate fatigue index from rep metrics
     * 
     * Uses MetricsCalculator (Single Source of Truth).
     */
    private fun calculateFatigueIndexFromMetrics(): Short? {
        if (completedRepMetrics.size < 4) return null
        
        // Convert scores from ×10 format to regular float
        val scores = completedRepMetrics.map { it.score / 10f }
        return MetricsCalculator.calculateFatigueIndexFromScores(scores)
    }
    
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
            fatigueIndex = null,
            velocityLoss = null,
            tempoConsistency = null
        )
    }
    
    /**
     * Cancel recording without saving
     */
    fun cancel() {
        isRecording = false
        currentRepBuffer.clear()
        completedRepMetrics.clear()
        Log.d(TAG, "Motion recording cancelled")
    }
    
    /**
     * Check if recorder is currently active
     */
    fun isActive(): Boolean = isRecording
    
    /**
     * Get current rep count
     */
    fun getCompletedRepCount(): Int = completedRepMetrics.size
    
    /**
     * Get current buffer size (frames in current rep)
     */
    fun getCurrentBufferSize(): Int = currentRepBuffer.size
    
    // ==================== Conversion Helpers ====================
    
    /**
     * Convert angle Map to ShortArray in trackedJoints order
     */
    private fun anglesToShortArray(angles: Map<String, Double>, skippedJointCodes: Set<String>): ShortArray {
        return ShortArray(trackedJoints.size) { i ->
            val jointName = trackedJoints[i]
            if (jointName in skippedJointCodes) {
                JOINT_SKIPPED_ANGLE_SENTINEL
            } else {
                val angle = angles[jointName] ?: 0.0
                (angle * 10).toInt().coerceIn(0, 1800).toShort()  // 90.5° → 905
            }
        }
    }
    
    /**
     * Convert states Map to ByteArray
     */
    private fun statesToByteArray(states: Map<String, JointStateInfo>?): ByteArray? {
        if (states == null) return null
        
        return ByteArray(trackedJoints.size) { i ->
            val jointName = trackedJoints[i]
            val stateInfo = states[jointName]
            if (stateInfo != null) {
                StateCode.fromJointState(stateInfo.state)
            } else {
                StateCode.NORMAL
            }
        }
    }
}

/**
 * Compare ByteArrays with null-safety
 */
private fun byteArrayEquals(a: ByteArray?, b: ByteArray?): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    return a.contentEquals(b)
}

/**
 * RepMetricsData - Lightweight rep data with only metrics (no raw frames)
 */
data class RepMetricsData(
    val num: Int,
    val durationMs: Int,
    val worstState: Byte,
    val score: Short,
    val weightKg: Float?,
    val metrics: RepMetrics
)

/**
 * SessionUpload - Lightweight session data ready for server upload
 * Contains only computed metrics, no raw frame data
 */
data class SessionUpload(
    val id: String,
    val exerciseId: String,
    val timestamp: Long,
    val durationMs: Int,
    val totalReps: Int,
    val countedReps: Int,
    val invalidReps: Int,
    val weightKg: Float?,
    val weightUnit: String,
    val repMetrics: List<RepMetricsData>,
    val sessionMetrics: SessionMetrics
) {
    /**
     * Get formatted duration
     */
    fun getFormattedDuration(): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    /**
     * Get average score as percentage
     */
    fun getAverageScorePercent(): Float = sessionMetrics.avgFormScore / 10f
}
