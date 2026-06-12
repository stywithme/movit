package com.movit.feature.account

enum class AssessmentPhase {
    PreScreening,
    BodyScan,
    Results,
}

data class MovitAssessmentUiState(
    val phase: AssessmentPhase = AssessmentPhase.PreScreening,
    val assessmentMode: String = "initial",
    val parqAnswers: Map<Int, Boolean> = AssessmentDefaults.parqQuestions.indices.associateWith { false },
    val bodyScanTemplate: AssessmentTemplateUi = AssessmentDefaults.initialTemplate,
    val scanProgressPercent: Int = 0,
    val scanMovementIndex: Int = 0,
    val isScanComplete: Boolean = false,
    val isPoseDetected: Boolean = false,
    val isGuidedScan: Boolean = false,
    val scanErrorMessage: String? = null,
    val isResolvingTemplate: Boolean = false,
    val isLoadingResults: Boolean = false,
    val results: AssessmentResultsUi = AssessmentResultsUi(
        bodyScore = 0,
        levelLabel = "",
        regions = emptyList(),
        insights = emptyList(),
    ),
) {
    val isProgressionAssessment: Boolean
        get() = assessmentMode.equals("progression", ignoreCase = true)
    val parqProgressPercent: Int
        get() = if (parqAnswers.isEmpty()) {
            0
        } else {
            (parqAnswers.size * 100) / AssessmentDefaults.parqQuestions.size
        }

    val scanMovementKey: String
        get() = bodyScanTemplate.safeMovements
            .getOrElse(scanMovementIndex) { bodyScanTemplate.safeMovements.last() }
            .titleKey

    val scanMovementNumber: Int
        get() = scanMovementIndex + 1

    val scanMovementTotal: Int
        get() = bodyScanTemplate.safeMovements.size
}
