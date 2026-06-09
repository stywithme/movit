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
    val scanMovementIndex: Int = 1,
    val isLoadingResults: Boolean = false,
    val results: AssessmentResultsUi = FakeAssessmentPreviewData.results,
) {
    val parqProgressPercent: Int
        get() = if (parqAnswers.isEmpty()) {
            0
        } else {
            (parqAnswers.size * 100) / FakeAssessmentPreviewData.parqQuestions.size
        }

    val scanMovementKey: String
        get() = FakeAssessmentPreviewData.bodyScanMovements
            .getOrElse(scanMovementIndex) { FakeAssessmentPreviewData.bodyScanMovements.last() }

    val scanMovementNumber: Int
        get() = scanMovementIndex + 1

    val scanMovementTotal: Int
        get() = FakeAssessmentPreviewData.bodyScanMovements.size
}
