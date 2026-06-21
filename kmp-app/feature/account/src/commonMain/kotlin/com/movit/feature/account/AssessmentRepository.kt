package com.movit.feature.account

import com.movit.shared.AppResult

interface AssessmentRepository {
    suspend fun resolveTemplate(
        mode: String = "initial",
        language: String = "en",
    ): AppResult<AssessmentTemplateUi>

    suspend fun submitBodyScan(result: AssessmentBodyScanResult): AppResult<AssessmentResultsUi>

    suspend fun fetchLastResults(language: String = "en"): AppResult<AssessmentResultsUi>
}
