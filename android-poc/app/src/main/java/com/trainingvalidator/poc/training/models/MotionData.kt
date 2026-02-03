package com.trainingvalidator.poc.training.models

import com.trainingvalidator.poc.training.engine.Phase
import java.util.Arrays

/**
 * MotionData.kt - Lightweight data models for motion recording and analytics
 * 
 * Design Principles:
 * - Minimal memory footprint (ShortArray instead of Map<String, Double>)
 * - No redundancy (joint names stored once per session, not per frame)
 * - Fast serialization (primitive arrays)
 * 
 * Data Flow:
 * TrainingEngine.processFrame() → MotionRecorder.record() → FrameSample
 * RepCounter.completeRep() → MotionRecorder.finalizeRep() → RepRecord
 * Session end → MotionRecorder.finalize() → SessionRecord → JSON.gz
 */

// ==================== Constants ====================

/**
 * Phase codes for compact storage (1 byte instead of enum name string)
 */
object PhaseCode {
    const val IDLE: Byte = 0
    const val ECCENTRIC: Byte = 1      // Down/Lowering phase
    const val ISOMETRIC: Byte = 2      // Hold/Peak phase
    const val CONCENTRIC: Byte = 3     // Up/Lifting phase
    
    fun fromPhase(phase: Phase): Byte = when (phase) {
        Phase.IDLE, Phase.START -> IDLE
        Phase.DOWN -> ECCENTRIC
        Phase.BOTTOM, Phase.EXTENDED, Phase.COUNT -> ISOMETRIC
        Phase.UP, Phase.PUSH, Phase.PULL -> CONCENTRIC
    }
    
    fun toDisplayName(code: Byte): String = when (code) {
        ECCENTRIC -> "Eccentric"
        ISOMETRIC -> "Isometric"
        CONCENTRIC -> "Concentric"
        else -> "Idle"
    }
}

/**
 * Joint state codes for compact storage (1 byte instead of enum)
 */
object StateCode {
    const val PERFECT: Byte = 0
    const val NORMAL: Byte = 1
    const val PAD: Byte = 2
    const val WARNING: Byte = 3
    const val DANGER: Byte = 4
    
    fun fromJointState(state: JointState): Byte = when (state) {
        JointState.PERFECT -> PERFECT
        JointState.NORMAL -> NORMAL
        JointState.PAD -> PAD
        JointState.WARNING -> WARNING
        JointState.DANGER -> DANGER
        JointState.TRANSITION -> NORMAL
    }
    
    fun isGood(code: Byte): Boolean = code <= NORMAL
}

// ==================== Frame Level ====================

/**
 * FrameSample - Lightweight frame data (~20 bytes per frame)
 * 
 * Recorded at ~30fps during exercise. Contains only essential data
 * for metrics calculation.
 * 
 * Memory estimation:
 * - t: 4 bytes (Int)
 * - phase: 1 byte (Byte)
 * - angles: ~10 bytes (5 joints × 2 bytes each)
 * - states: ~5 bytes (5 joints × 1 byte, nullable)
 * Total: ~20 bytes/frame vs ~200+ bytes with Map<String, Double>
 * 
 * @param t Milliseconds since session start (not Unix timestamp for compactness)
 * @param phase Current movement phase (PhaseCode)
 * @param angles Joint angles × 10 (e.g., 90.5° → 905). Order matches trackedJoints in SessionRecord
 * @param states Joint states (optional, only recorded on state change to save space)
 */
data class FrameSample(
    val t: Int,
    val phase: Byte,
    val angles: ShortArray,
    val states: ByteArray? = null
) {
    /**
     * Get angle in degrees for a specific joint index
     */
    fun getAngleDegrees(jointIndex: Int): Double {
        return if (jointIndex in angles.indices) {
            angles[jointIndex] / 10.0
        } else 0.0
    }
    
    /**
     * Check if all joints are in good state (PERFECT or NORMAL)
     */
    fun isGoodForm(): Boolean {
        return states?.all { StateCode.isGood(it) } ?: true
    }
    
    // Required for data class with arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameSample
        if (t != other.t) return false
        if (phase != other.phase) return false
        if (!angles.contentEquals(other.angles)) return false
        if (states != null) {
            if (other.states == null) return false
            if (!states.contentEquals(other.states)) return false
        } else if (other.states != null) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = t
        result = 31 * result + phase
        result = 31 * result + angles.contentHashCode()
        result = 31 * result + (states?.contentHashCode() ?: 0)
        return result
    }
}

// ==================== Rep Level ====================

/**
 * RepRecord - Complete record of a single repetition
 * 
 * Created when RepCounter.completeRep() is called.
 * Contains all frames and phase timings for detailed analysis.
 * 
 * @param num Rep number (1-based)
 * @param startT Start time in ms (relative to session start)
 * @param endT End time in ms (relative to session start)
 * @param frames All frame samples during this rep
 * @param phases Phase durations [eccentric, isometric, concentric] in ms
 * @param worstState Worst joint state reached during this rep
 * @param score Form score × 10 (e.g., 85.5% → 855)
 * @param weightKg Weight used for this rep (null for bodyweight)
 */
data class RepRecord(
    val num: Int,
    val startT: Int,
    val endT: Int,
    val frames: List<FrameSample>,
    val phases: IntArray,           // [eccentric, isometric, concentric]
    val worstState: Byte,
    val score: Short,
    val weightKg: Float? = null
) {
    /**
     * Duration in milliseconds
     */
    val durationMs: Int get() = endT - startT
    
    /**
     * Duration in seconds
     */
    val durationSec: Float get() = durationMs / 1000f
    
    /**
     * Get phase duration by code
     */
    fun getPhaseDuration(phaseCode: Byte): Int = when (phaseCode) {
        PhaseCode.ECCENTRIC -> phases.getOrElse(0) { 0 }
        PhaseCode.ISOMETRIC -> phases.getOrElse(1) { 0 }
        PhaseCode.CONCENTRIC -> phases.getOrElse(2) { 0 }
        else -> 0
    }
    
    /**
     * Tempo string (e.g., "2-1-2" for 2s down, 1s hold, 2s up)
     */
    fun getTempoString(): String {
        val ecc = (phases.getOrElse(0) { 0 } / 1000f).toInt()
        val iso = (phases.getOrElse(1) { 0 } / 1000f).toInt()
        val con = (phases.getOrElse(2) { 0 } / 1000f).toInt()
        return "$ecc-$iso-$con"
    }
    
    /**
     * Check if rep was valid (not DANGER)
     */
    fun isValid(): Boolean = worstState != StateCode.DANGER
    
    /**
     * Check if rep was counted (PERFECT, NORMAL, or PAD)
     */
    fun isCounted(): Boolean = worstState <= StateCode.PAD
    
    // Required for data class with arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RepRecord
        if (num != other.num) return false
        if (startT != other.startT) return false
        if (endT != other.endT) return false
        if (!phases.contentEquals(other.phases)) return false
        if (worstState != other.worstState) return false
        if (score != other.score) return false
        if (weightKg != other.weightKg) return false
        if (frames != other.frames) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = num
        result = 31 * result + startT
        result = 31 * result + endT
        result = 31 * result + phases.contentHashCode()
        result = 31 * result + worstState
        result = 31 * result + score
        result = 31 * result + (weightKg?.hashCode() ?: 0)
        result = 31 * result + frames.hashCode()
        return result
    }
}

// ==================== Metrics ====================

/**
 * RepMetrics - Calculated metrics for a single repetition
 * 
 * Computed from RepRecord.frames after rep completion.
 * All percentage values are × 10 (e.g., 85.5% → 855) for Short storage.
 */
data class RepMetrics(
    val rom: Short,                 // Range of Motion × 10
    val symmetry: Short?,           // Bilateral symmetry × 10 (null for single-side exercises)
    val stability: Short,           // Core stability × 10
    val tempo: IntArray,            // [eccentric, iso, concentric] in ms
    val velocity: Short?,           // Mean concentric velocity × 100
    val formScore: Short,           // Overall form score × 10
    val alignmentAccuracy: Short    // Time in correct position × 10
) {
    // Required for data class with arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RepMetrics
        return rom == other.rom &&
               symmetry == other.symmetry &&
               stability == other.stability &&
               tempo.contentEquals(other.tempo) &&
               velocity == other.velocity &&
               formScore == other.formScore &&
               alignmentAccuracy == other.alignmentAccuracy
    }
    
    override fun hashCode(): Int {
        var result = rom.toInt()
        result = 31 * result + (symmetry ?: 0)
        result = 31 * result + stability
        result = 31 * result + tempo.contentHashCode()
        result = 31 * result + (velocity ?: 0)
        result = 31 * result + formScore
        result = 31 * result + alignmentAccuracy
        return result
    }
}

/**
 * SessionMetrics - Aggregated metrics for the entire session
 * 
 * Computed from all RepRecords after session ends.
 */
data class SessionMetrics(
    // Kinematic metrics (averages)
    val avgRom: Short,
    val avgSymmetry: Short?,
    val avgStability: Short,
    val avgTempo: IntArray,
    val avgVelocity: Short?,
    val avgFormScore: Short,
    val avgAlignmentAccuracy: Short,
    
    // Temporal metrics
    val totalTUT: Int,              // Total Time Under Tension (ms)
    
    // Load metrics
    val totalVolume: Float?,        // Total volume (reps × weight)
    val maxWeight: Float?,          // Maximum weight used
    val est1RM: Float?,             // Estimated 1 Rep Max
    
    // Advanced metrics (Future/Later)
    val relativeStrength: Float? = null,    // weight / bodyWeight
    val intensityPercentage: Float? = null, // % of 1RM
    
    // Quality metrics
    val formConsistency: Short?,    // DTW score (1000 = perfect consistency)
    val fatigueIndex: Short?        // Rep number where fatigue started (null = no fatigue)
) {
    /**
     * Get TUT in seconds
     */
    fun getTUTSeconds(): Float = totalTUT / 1000f
    
    /**
     * Get average tempo string
     */
    fun getAvgTempoString(): String {
        val ecc = (avgTempo.getOrElse(0) { 0 } / 1000f).toInt()
        val iso = (avgTempo.getOrElse(1) { 0 } / 1000f).toInt()
        val con = (avgTempo.getOrElse(2) { 0 } / 1000f).toInt()
        return "$ecc-$iso-$con"
    }
    
    // Required for data class with arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SessionMetrics
        return avgRom == other.avgRom &&
               avgSymmetry == other.avgSymmetry &&
               avgStability == other.avgStability &&
               avgTempo.contentEquals(other.avgTempo) &&
               avgVelocity == other.avgVelocity &&
               avgFormScore == other.avgFormScore &&
               avgAlignmentAccuracy == other.avgAlignmentAccuracy &&
               totalTUT == other.totalTUT &&
               totalVolume == other.totalVolume &&
               maxWeight == other.maxWeight &&
               est1RM == other.est1RM &&
               formConsistency == other.formConsistency &&
               fatigueIndex == other.fatigueIndex
    }
    
    override fun hashCode(): Int {
        var result = avgRom.toInt()
        result = 31 * result + (avgSymmetry ?: 0)
        result = 31 * result + avgStability
        result = 31 * result + avgTempo.contentHashCode()
        result = 31 * result + (avgVelocity ?: 0)
        result = 31 * result + avgFormScore
        result = 31 * result + avgAlignmentAccuracy
        result = 31 * result + totalTUT
        result = 31 * result + (totalVolume?.hashCode() ?: 0)
        result = 31 * result + (maxWeight?.hashCode() ?: 0)
        result = 31 * result + (est1RM?.hashCode() ?: 0)
        result = 31 * result + (formConsistency ?: 0)
        result = 31 * result + (fatigueIndex ?: 0)
        return result
    }
}

// ==================== Session Level ====================

/**
 * SessionRecord - Complete session data for storage and analysis
 * 
 * Contains all rep records and computed metrics.
 * Serialized to JSON.gz for persistent storage.
 * 
 * @param id Unique session identifier
 * @param exerciseId Exercise identifier (matches ExerciseConfig.fileName)
 * @param startEpoch Unix timestamp of session start
 * @param durationMs Total session duration in milliseconds
 * @param trackedJoints List of tracked joint names (order matches FrameSample.angles)
 * @param defaultWeightKg Default weight for the session (null for bodyweight)
 * @param weightUnit Weight unit ("kg" or "lbs") - stored for display, conversion in UI
 * @param reps List of all rep records
 * @param metrics Computed session metrics
 */
data class SessionRecord(
    val id: String,
    val exerciseId: String,
    val startEpoch: Long,
    val durationMs: Int,
    val trackedJoints: List<String>,
    val defaultWeightKg: Float? = null,
    val weightUnit: String = "kg",
    val reps: List<RepRecord>,
    val metrics: SessionMetrics
) {
    /**
     * Total number of reps
     */
    val totalReps: Int get() = reps.size
    
    /**
     * Number of counted reps (PERFECT, NORMAL, PAD)
     */
    val countedReps: Int get() = reps.count { it.isCounted() }
    
    /**
     * Number of invalid reps (DANGER)
     */
    val invalidReps: Int get() = reps.count { !it.isValid() }
    
    /**
     * Get duration in mm:ss format
     */
    fun getFormattedDuration(): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    /**
     * Get average score as percentage
     */
    fun getAverageScorePercent(): Float = metrics.avgFormScore / 10f
    
    /**
     * Get weight with unit string
     */
    fun getWeightDisplay(): String? {
        return defaultWeightKg?.let { "%.1f %s".format(it, weightUnit) }
    }
}
