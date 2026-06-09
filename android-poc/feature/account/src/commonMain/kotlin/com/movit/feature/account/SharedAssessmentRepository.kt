package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.shared.AppResult

class SharedAssessmentRepository(
    private val fallback: AssessmentRepository = FakeAssessmentRepository(),
) : AssessmentRepository {

    override suspend fun fetchLastResults(language: String): AppResult<AssessmentResultsUi> {
        if (!MovitData.isInstalled) {
            return fallback.fetchLastResults(language)
        }
        val platform = MovitData.requirePlatform()
        if (platform.authHeader() == null) {
            return fallback.fetchLastResults(language)
        }
        return when (val result = MovitData.account.fetchLevelProfile()) {
            is AppResult.Success -> {
                val mapped = AssessmentApiMapper.map(result.value, language)
                if (mapped.bodyScore > 0 || mapped.regions.isNotEmpty()) {
                    AppResult.Success(mapped)
                } else {
                    fallback.fetchLastResults(language)
                }
            }
            is AppResult.Failure -> fallback.fetchLastResults(language)
        }
    }
}
