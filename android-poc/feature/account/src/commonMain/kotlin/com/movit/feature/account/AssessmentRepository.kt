package com.movit.feature.account

import com.movit.shared.AppResult

interface AssessmentRepository {
    suspend fun fetchLastResults(language: String = "en"): AppResult<AssessmentResultsUi>
}

class FakeAssessmentRepository(
    private val results: AssessmentResultsUi = FakeAssessmentPreviewData.results,
    private val shouldFail: Boolean = false,
) : AssessmentRepository {
    override suspend fun fetchLastResults(language: String): AppResult<AssessmentResultsUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load assessment results.")
        } else {
            AppResult.Success(results)
        }
    }
}
