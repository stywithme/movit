package com.movit.feature.account

import com.movit.shared.AppResult

class FakeAssessmentRepository(
    private val results: AssessmentResultsUi = FakeAssessmentPreviewData.results,
    private val template: AssessmentTemplateUi = AssessmentDefaults.initialTemplate,
    private val shouldFail: Boolean = false,
) : AssessmentRepository {
    override suspend fun resolveTemplate(
        mode: String,
        language: String,
    ): AppResult<AssessmentTemplateUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load assessment template.")
        } else {
            AppResult.Success(template.copy(type = mode))
        }
    }

    override suspend fun submitBodyScan(result: AssessmentBodyScanResult): AppResult<AssessmentResultsUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to save assessment results.")
        } else {
            AppResult.Success(results)
        }
    }

    override suspend fun fetchLastResults(language: String): AppResult<AssessmentResultsUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load assessment results.")
        } else {
            AppResult.Success(results)
        }
    }
}
