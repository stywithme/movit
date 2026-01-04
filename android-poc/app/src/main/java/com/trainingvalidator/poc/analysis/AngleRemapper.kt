package com.trainingvalidator.poc.analysis

/**
 * AngleRemapper - Non-Linear Range Remapping for Joint Angles
 * 
 * Solves the problem where MediaPipe landmarks are placed at joint centers,
 * causing inaccurate angle measurements especially at full flexion.
 * 
 * The Problem:
 * - At 180° to ~100°: Angles are approximately correct
 * - Below 100°: Joint thickness causes increasing error
 * - Observed minimum ~80° should actually be ~30°
 * 
 * Solution: Piecewise linear remapping
 * - Above threshold: No correction needed
 * - Below threshold: Progressive correction applied
 */
object AngleRemapper {
    
    /**
     * Joint-specific remapping configuration
     * 
     * @param threshold The angle below which correction starts (e.g., 100° for elbow)
     * @param observedMin The minimum angle MediaPipe can measure (e.g., 80°)
     * @param actualMin The actual physical minimum angle (e.g., 30°)
     */
    data class RemapConfig(
        val threshold: Double,      // Angle where distortion starts
        val observedMin: Double,    // Minimum angle MediaPipe reports
        val actualMin: Double       // Actual physical minimum angle
    ) {
        /**
         * Calculate the scale factor for remapping
         * Scale = (threshold - actualMin) / (threshold - observedMin)
         */
        val scaleFactor: Double
            get() = if (threshold > observedMin) {
                (threshold - actualMin) / (threshold - observedMin)
            } else {
                1.0
            }
    }
    
    // ==================== Default Configurations ====================
    
    /**
     * Default remapping configurations for each joint type
     * These are based on typical MediaPipe behavior and can be calibrated per user
     */
    object DefaultConfigs {
        
        // Elbow: Problems start below 100°, observed min ~80°, actual min ~30°
        val ELBOW = RemapConfig(
            threshold = 100.0,
            observedMin = 80.0,
            actualMin = 30.0
        )
        
        // Knee: Similar to elbow but slightly different range
        val KNEE = RemapConfig(
            threshold = 110.0,
            observedMin = 85.0,
            actualMin = 40.0
        )
        
        // Shoulder: Less affected due to ball joint, minimal correction
        val SHOULDER = RemapConfig(
            threshold = 90.0,
            observedMin = 70.0,
            actualMin = 50.0
        )
        
        // Hip: Moderate correction needed
        val HIP = RemapConfig(
            threshold = 100.0,
            observedMin = 75.0,
            actualMin = 45.0
        )
        
        // Ankle: Minimal correction
        val ANKLE = RemapConfig(
            threshold = 80.0,
            observedMin = 60.0,
            actualMin = 45.0
        )
        
        // Spine: No remapping needed (different measurement type)
        val SPINE: RemapConfig? = null
        
        // Neck: No remapping needed
        val NECK: RemapConfig? = null
    }
    
    // ==================== Remapping Functions ====================
    
    /**
     * Remap a single angle using the given configuration
     * 
     * Algorithm:
     * - If angle >= threshold: return angle unchanged
     * - If angle < threshold: apply progressive correction
     * 
     * Formula for correction zone:
     * actualAngle = threshold - ((threshold - observedAngle) * scaleFactor)
     * 
     * @param observedAngle The angle measured by MediaPipe
     * @param config The remapping configuration for this joint
     * @return The corrected angle
     */
    fun remap(observedAngle: Double, config: RemapConfig): Double {
        // Above threshold: no correction needed
        if (observedAngle >= config.threshold) {
            return observedAngle
        }
        
        // Below threshold: apply progressive correction
        val distanceFromThreshold = config.threshold - observedAngle
        val correctedDistance = distanceFromThreshold * config.scaleFactor
        val correctedAngle = config.threshold - correctedDistance
        
        // Clamp to valid range [actualMin, threshold]
        return correctedAngle.coerceIn(config.actualMin, config.threshold)
    }
    
    /**
     * Remap angle with null safety
     */
    fun remapOrNull(observedAngle: Double?, config: RemapConfig?): Double? {
        if (observedAngle == null || config == null) return observedAngle
        return remap(observedAngle, config)
    }
    
    /**
     * Get the appropriate config for a joint by name
     */
    fun getConfigForJoint(jointName: String): RemapConfig? {
        return when (jointName.lowercase()) {
            "left_elbow", "right_elbow" -> DefaultConfigs.ELBOW
            "left_knee", "right_knee" -> DefaultConfigs.KNEE
            "left_shoulder", "right_shoulder" -> DefaultConfigs.SHOULDER
            "left_hip", "right_hip" -> DefaultConfigs.HIP
            "left_ankle", "right_ankle" -> DefaultConfigs.ANKLE
            "spine" -> DefaultConfigs.SPINE
            "neck" -> DefaultConfigs.NECK
            else -> null
        }
    }
    
    /**
     * Remap angle by joint name using default config
     */
    fun remapByJointName(observedAngle: Double?, jointName: String): Double? {
        if (observedAngle == null) return null
        val config = getConfigForJoint(jointName) ?: return observedAngle
        return remap(observedAngle, config)
    }
    
    /**
     * Remap all angles in a JointAngles object
     */
    fun remapAllAngles(angles: JointAngles): JointAngles {
        return JointAngles(
            leftElbow = remapOrNull(angles.leftElbow, DefaultConfigs.ELBOW),
            rightElbow = remapOrNull(angles.rightElbow, DefaultConfigs.ELBOW),
            leftShoulder = remapOrNull(angles.leftShoulder, DefaultConfigs.SHOULDER),
            rightShoulder = remapOrNull(angles.rightShoulder, DefaultConfigs.SHOULDER),
            leftHip = remapOrNull(angles.leftHip, DefaultConfigs.HIP),
            rightHip = remapOrNull(angles.rightHip, DefaultConfigs.HIP),
            leftKnee = remapOrNull(angles.leftKnee, DefaultConfigs.KNEE),
            rightKnee = remapOrNull(angles.rightKnee, DefaultConfigs.KNEE),
            leftAnkle = remapOrNull(angles.leftAnkle, DefaultConfigs.ANKLE),
            rightAnkle = remapOrNull(angles.rightAnkle, DefaultConfigs.ANKLE),
            neck = angles.neck, // No remapping
            spine = angles.spine // No remapping
        )
    }
    
    // ==================== Visualization Helpers ====================
    
    /**
     * Get both raw and remapped angle for debugging/display
     */
    data class AngleComparison(
        val raw: Double,
        val remapped: Double,
        val wasRemapped: Boolean
    )
    
    fun compareAngles(observedAngle: Double, config: RemapConfig): AngleComparison {
        val remapped = remap(observedAngle, config)
        return AngleComparison(
            raw = observedAngle,
            remapped = remapped,
            wasRemapped = observedAngle < config.threshold
        )
    }
    
    // ==================== Calibration Support ====================
    
    /**
     * Create a custom config based on user calibration
     * 
     * @param threshold The angle where user's joint starts showing error
     * @param userObservedMin The minimum angle observed for this user
     * @param userActualMin The actual minimum angle (usually ~30° for elbow)
     */
    fun createCustomConfig(
        threshold: Double = 100.0,
        userObservedMin: Double,
        userActualMin: Double = 30.0
    ): RemapConfig {
        return RemapConfig(
            threshold = threshold,
            observedMin = userObservedMin,
            actualMin = userActualMin
        )
    }
    
    /**
     * Example usage and verification
     */
    fun printRemappingExample() {
        val config = DefaultConfigs.ELBOW
        println("=== Elbow Angle Remapping Example ===")
        println("Config: threshold=${config.threshold}°, observedMin=${config.observedMin}°, actualMin=${config.actualMin}°")
        println("Scale Factor: ${config.scaleFactor}")
        println()
        println("Observed → Remapped")
        listOf(180.0, 150.0, 120.0, 100.0, 95.0, 90.0, 85.0, 80.0).forEach { observed ->
            val remapped = remap(observed, config)
            val marker = if (observed < config.threshold) "*" else ""
            println("  ${observed}° → ${"%.1f".format(remapped)}° $marker")
        }
        println()
        println("* = Remapping applied")
    }
}
