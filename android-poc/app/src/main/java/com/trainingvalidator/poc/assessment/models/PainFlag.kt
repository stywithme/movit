package com.trainingvalidator.poc.assessment.models

/**
 * PainFlag - Records when a user reports pain during assessment.
 * 
 * This is NOT a score - it's a separate state that triggers:
 * 1. Immediate stop of the current movement
 * 2. Safety gate for the affected region
 * 3. Recommendation to consult a specialist
 */
data class PainFlag(
    val movement: String,
    val region: String,
    val timestamp: Long = System.currentTimeMillis()
)
