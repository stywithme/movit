package com.movit.feature.account

enum class AssessmentPhase {
    PreScreening,
    BodyScan,
    Results,
}

data class MovitAssessmentUiState(
    val phase: AssessmentPhase = AssessmentPhase.PreScreening,
    val parqAnswers: Map<Int, Boolean> = FakeAssessmentPreviewData.parqQuestions.indices.associateWith { false },
    val scanProgressPercent: Int = 67,
    val scanMovementLabel: String = "Overhead squat · Movement 2 of 3",
    val results: AssessmentResultsUi = FakeAssessmentPreviewData.results,
)
