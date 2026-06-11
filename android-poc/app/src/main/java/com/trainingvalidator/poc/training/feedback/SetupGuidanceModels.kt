package com.trainingvalidator.poc.training.feedback

import com.trainingvalidator.poc.training.models.LocalizedText

/** Setup-pose joint guidance (formerly in legacy PoseSetupGuide). */
data class JointGuidance(
    val jointCode: String,
    val jointName: String,
    val level: GuidanceLevel,
    val currentAngle: Double,
    val targetMin: Double,
    val targetMax: Double,
    val distance: Double,
    val direction: Direction?,
    val message: LocalizedText,
    val isPrimary: Boolean,
)

enum class GuidanceLevel {
    GREEN,
    YELLOW,
    RED,
}

enum class Direction {
    RAISE,
    LOWER,
}
