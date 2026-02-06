package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.models.*

/**
 * ScoreCalculator - Centralized scoring logic for all exercise types
 * 
 * This is the SINGLE SOURCE OF TRUTH for score calculations.
 * 
 * SCORING PHILOSOPHY:
 * - Weighted average from ALL joints (not just worst)
 * - Primary joints have higher weight than secondary
 * - DANGER state has explicit penalty
 * - Different formulas for Rep-based vs Hold-based exercises
 * 
 * SCORE RATES:
 * - PERFECT: 100
 * - NORMAL: 80
 * - PAD: 60
 * - WARNING: 40
 * - DANGER: 0 (+ penalty)
 */
object ScoreCalculator {
    
    private const val TAG = "ScoreCalculator"
    
    // ═══════════════════════════════════════════════════════════════
    // WEIGHTS
    // ═══════════════════════════════════════════════════════════════
    
    /** Weight for primary joints (tracked joints with upPose/downPose) */
    const val PRIMARY_JOINT_WEIGHT = 1.0f
    
    /** Weight for secondary joints (position checks, stability) */
    const val SECONDARY_JOINT_WEIGHT = 0.3f
    
    /** Penalty per DANGER joint (subtracted from final score) */
    const val DANGER_PENALTY_PER_JOINT = 15f
    
    // ═══════════════════════════════════════════════════════════════
    // SCORE RATES (different from StateConfig for more fair scoring)
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Get score rate for a state (for weighted calculation)
     * These rates are more balanced than StateConfig.rate
     */
    fun getScoreRate(state: JointState): Float {
        return when (state) {
            JointState.PERFECT -> 100f
            JointState.NORMAL -> 80f
            JointState.PAD -> 60f
            JointState.WARNING -> 40f
            JointState.DANGER -> 0f
            JointState.TRANSITION -> 80f  // Treat as NORMAL during movement
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // REP-BASED SCORING
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Calculate score for a rep based on all joint states
     * 
     * Uses weighted average where primary joints count more than secondary
     * 
     * @param jointStates Map of joint code to its current state info
     * @param primaryJoints Set of joint codes that are primary for this exercise
     * @return RepScoreResult with score and metadata
     */
    fun calculateRepScore(
        jointStates: Map<String, JointStateInfo>,
        primaryJoints: Set<String>
    ): RepScoreResult {
        if (jointStates.isEmpty()) {
            return RepScoreResult(
                score = 0f,
                worstState = JointState.PERFECT,
                isCounted = true,
                isInvalidated = false,
                dangerJoints = emptyList(),
                breakdown = emptyMap()
            )
        }
        
        var weightedSum = 0f
        var totalWeight = 0f
        var worstState = JointState.PERFECT
        val dangerJoints = mutableListOf<String>()
        val breakdown = mutableMapOf<String, JointScoreContribution>()
        
        for ((jointCode, stateInfo) in jointStates) {
            val state = stateInfo.state
            
            // Skip TRANSITION states in score calculation
            if (state == JointState.TRANSITION) continue
            
            // Determine weight
            val isPrimary = primaryJoints.contains(jointCode)
            val weight = if (isPrimary) PRIMARY_JOINT_WEIGHT else SECONDARY_JOINT_WEIGHT
            
            // Get rate for this state
            val rate = getScoreRate(state)
            
            // Add to weighted sum
            weightedSum += rate * weight
            totalWeight += weight
            
            // Track worst state
            if (state.isWorseThan(worstState)) {
                worstState = state
            }
            
            // Track DANGER joints
            if (state == JointState.DANGER) {
                dangerJoints.add(jointCode)
            }
            
            // Store breakdown for debugging/analysis
            breakdown[jointCode] = JointScoreContribution(
                state = state,
                rate = rate,
                weight = weight,
                contribution = rate * weight,
                isPrimary = isPrimary
            )
        }
        
        // Calculate base score
        val baseScore = if (totalWeight > 0) {
            weightedSum / totalWeight
        } else {
            0f
        }
        
        // Apply DANGER penalty
        val dangerPenalty = dangerJoints.size * DANGER_PENALTY_PER_JOINT
        val finalScore = (baseScore - dangerPenalty).coerceIn(0f, 100f)
        
        // Determine if rep should be counted
        val isCounted = worstState in listOf(
            JointState.PERFECT, 
            JointState.NORMAL, 
            JointState.PAD
        )
        
        // Determine if rep is invalidated
        val isInvalidated = worstState == JointState.DANGER
        
        // Debug logging disabled for performance
        // Log.d(TAG, "Rep Score: base=${"%.1f".format(baseScore)}, final=${"%.1f".format(finalScore)}")
        
        return RepScoreResult(
            score = finalScore,
            worstState = worstState,
            isCounted = isCounted,
            isInvalidated = isInvalidated,
            dangerJoints = dangerJoints,
            breakdown = breakdown
        )
    }
    
    /**
     * Calculate score from worst state only (legacy compatibility)
     * 
     * Used when we don't have full joint state information
     */
    fun calculateScoreFromWorstState(worstState: JointState): Float {
        return getScoreRate(worstState)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // HOLD-BASED SCORING
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Calculate score for a hold exercise based on time in each state
     * 
     * @param stateTimeMs Map of state to time spent in that state (milliseconds)
     * @return HoldScoreResult with score and metadata
     */
    fun calculateHoldScore(
        stateTimeMs: Map<JointState, Long>
    ): HoldScoreResult {
        val perfectTime = stateTimeMs[JointState.PERFECT] ?: 0L
        val normalTime = stateTimeMs[JointState.NORMAL] ?: 0L
        val padTime = stateTimeMs[JointState.PAD] ?: 0L
        val warningTime = stateTimeMs[JointState.WARNING] ?: 0L
        val dangerTime = stateTimeMs[JointState.DANGER] ?: 0L
        
        val totalTime = perfectTime + normalTime + padTime + warningTime + dangerTime
        
        // DANGER during hold = invalidated
        if (dangerTime > 0) {
            return HoldScoreResult(
                score = 0f,
                isInvalidated = true,
                timeInPerfect = perfectTime,
                timeInNormal = normalTime,
                timeInWarning = warningTime,
                timeInDanger = dangerTime,
                totalTime = totalTime
            )
        }
        
        if (totalTime == 0L) {
            return HoldScoreResult(
                score = 0f,
                isInvalidated = false,
                timeInPerfect = 0L,
                timeInNormal = 0L,
                timeInWarning = 0L,
                timeInDanger = 0L,
                totalTime = 0L
            )
        }
        
        // Weighted average based on time in each state
        // Using same rates as rep scoring for consistency
        val weightedSum = (
            perfectTime * getScoreRate(JointState.PERFECT) +
            normalTime * getScoreRate(JointState.NORMAL) +
            padTime * getScoreRate(JointState.PAD) +
            warningTime * getScoreRate(JointState.WARNING)
        ).toFloat()
        
        val score = weightedSum / totalTime
        
        Log.d(TAG, "Hold Score: ${"%.1f".format(score)}%, " +
                "perfect=${perfectTime}ms, " +
                "normal=${normalTime}ms, " +
                "warning=${warningTime}ms")
        
        return HoldScoreResult(
            score = score,
            isInvalidated = false,
            timeInPerfect = perfectTime,
            timeInNormal = normalTime,
            timeInWarning = warningTime,
            timeInDanger = dangerTime,
            totalTime = totalTime
        )
    }
    
    // NOTE: Overall quality calculation has been moved to OverallQualityScore.calculate()
    // in PostTrainingReport.kt to avoid duplication. Use that as the Single Source of Truth.
}

// ═══════════════════════════════════════════════════════════════
// RESULT DATA CLASSES
// ═══════════════════════════════════════════════════════════════

/**
 * Result of rep score calculation
 */
data class RepScoreResult(
    val score: Float,
    val worstState: JointState,
    val isCounted: Boolean,
    val isInvalidated: Boolean,
    val dangerJoints: List<String>,
    val breakdown: Map<String, JointScoreContribution>
)

/**
 * Contribution of a single joint to the score
 */
data class JointScoreContribution(
    val state: JointState,
    val rate: Float,
    val weight: Float,
    val contribution: Float,
    val isPrimary: Boolean
)

/**
 * Result of hold score calculation
 */
data class HoldScoreResult(
    val score: Float,
    val isInvalidated: Boolean,
    val timeInPerfect: Long,
    val timeInNormal: Long,
    val timeInWarning: Long,
    val timeInDanger: Long,
    val totalTime: Long
) {
    fun getTimeInPerfectPercentage(): Float {
        return if (totalTime > 0) (timeInPerfect.toFloat() / totalTime) * 100f else 0f
    }
}

// NOTE: OverallQualityResult has been removed - use OverallQualityScore from PostTrainingReport.kt instead
