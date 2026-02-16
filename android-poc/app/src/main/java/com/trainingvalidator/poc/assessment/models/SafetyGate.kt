package com.trainingvalidator.poc.assessment.models

import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * SafetyGate - A safety restriction that blocks certain exercises
 * when a region is assessed as LIMITED or WEAK.
 * 
 * Safety gates CANNOT be overridden by overall Body Score.
 */
data class SafetyGate(
    val region: BodyRegion,
    val reason: LocalizedText,
    val blockedExerciseTypes: List<String>,
    val allowedAlternatives: List<String>,
    val resolveCondition: String
) {
    fun isResolved(currentRegionalScore: Float): Boolean {
        // Simple threshold check - region must improve above 50%
        return currentRegionalScore > 50f
    }
}
