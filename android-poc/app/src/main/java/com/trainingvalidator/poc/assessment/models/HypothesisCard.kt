package com.trainingvalidator.poc.assessment.models

import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * HypothesisCard - An observed compensation pattern with possible causes.
 * 
 * IMPORTANT: These are hypotheses, NOT diagnoses. The language used
 * in the UI must reflect this distinction.
 */
data class HypothesisCard(
    val observation: LocalizedText,
    val possibleCauses: List<PossibleCause>,
    val recommendations: List<HypothesisRecommendation>,
    val confidence: ConfidenceLevel
)

data class PossibleCause(
    val cause: LocalizedText,
    val status: CauseStatus,
    val evidence: String? = null
)

enum class CauseStatus {
    CONFIRMED,
    POSSIBLE,
    RULED_OUT
}

data class HypothesisRecommendation(
    val type: RecommendationType,
    val priority: RecommendationPriority,
    val description: LocalizedText
)

enum class RecommendationType {
    STRETCH,
    STRENGTHEN,
    MOBILIZE
}

enum class RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW
}
