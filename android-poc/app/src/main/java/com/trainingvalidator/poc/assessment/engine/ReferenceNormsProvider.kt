package com.trainingvalidator.poc.assessment.engine

import com.trainingvalidator.poc.assessment.models.BodyRegion

/**
 * ReferenceNormsProvider - Medical reference norms for ROM comparison.
 * 
 * Based on AAOS/AMA Guidelines for normal adult ROM (18-45 years).
 * These are NOT diagnostic thresholds - they are reference points for
 * fitness-level comparison.
 */
object ReferenceNormsProvider {
    
    data class JointNorm(
        val region: BodyRegion,
        val movement: String,
        val normalRomMin: Float,  // Lower bound of normal ROM
        val normalRomMax: Float,  // Upper bound of normal ROM
        val midpoint: Float = (normalRomMin + normalRomMax) / 2f,
        val errorBand: Float      // ± measurement error in degrees
    )
    
    // Map of joint code → reference norm
    // Joint codes match ExerciseConfig tracked joint codes
    private val norms = mapOf(
        // Shoulder
        "left_shoulder" to JointNorm(BodyRegion.SHOULDER, "Flexion", 150f, 180f, errorBand = 6f),
        "right_shoulder" to JointNorm(BodyRegion.SHOULDER, "Flexion", 150f, 180f, errorBand = 6f),
        
        // Hip
        "left_hip" to JointNorm(BodyRegion.HIP, "Flexion", 110f, 120f, errorBand = 7f),
        "right_hip" to JointNorm(BodyRegion.HIP, "Flexion", 110f, 120f, errorBand = 7f),
        
        // Knee
        "left_knee" to JointNorm(BodyRegion.KNEE, "Flexion", 130f, 140f, errorBand = 5f),
        "right_knee" to JointNorm(BodyRegion.KNEE, "Flexion", 130f, 140f, errorBand = 5f),
        
        // Ankle (lower reliability)
        "left_ankle" to JointNorm(BodyRegion.ANKLE, "Dorsiflexion", 15f, 20f, errorBand = 10f),
        "right_ankle" to JointNorm(BodyRegion.ANKLE, "Dorsiflexion", 15f, 20f, errorBand = 10f),
        
        // Spine
        "spine" to JointNorm(BodyRegion.LOWER_BACK, "Flexion", 75f, 90f, errorBand = 8f)
    )
    
    fun getNorm(jointCode: String): JointNorm? = norms[jointCode]
    
    fun getErrorBand(jointCode: String): Float = norms[jointCode]?.errorBand ?: 7f
    
    fun getReferenceRom(jointCode: String): Float = norms[jointCode]?.midpoint ?: 0f
    
    fun getRegionForJoint(jointCode: String): BodyRegion? = norms[jointCode]?.region
    
    // MDC (Minimal Detectable Change) = error band × 1.5
    // Only changes exceeding MDC are considered "real improvement"
    fun getMdc(jointCode: String): Float = getErrorBand(jointCode) * 1.5f
    
    // Calculate ROM as percentage of reference norm
    fun calculateRomPercentage(jointCode: String, measuredRom: Float): Float {
        val norm = norms[jointCode] ?: return 0f
        return (measuredRom / norm.midpoint * 100f).coerceIn(0f, 120f)
    }
    
    // Calculate ROM percentage range (considering error band)
    fun calculateRomRange(jointCode: String, measuredRom: Float): Pair<Float, Float> {
        val norm = norms[jointCode] ?: return Pair(0f, 0f)
        val error = norm.errorBand
        val minRom = (measuredRom - error).coerceAtLeast(0f)
        val maxRom = measuredRom + error
        val minPct = (minRom / norm.midpoint * 100f).coerceIn(0f, 120f)
        val maxPct = (maxRom / norm.midpoint * 100f).coerceIn(0f, 120f)
        return Pair(minPct, maxPct)
    }
}
