package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.shared.AppResult

class SharedAssessmentRepository(
) : AssessmentRepository {
    override suspend fun resolveTemplate(
        mode: String,
        language: String,
    ): AppResult<AssessmentTemplateUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        if (platform.authHeader() == null) {
            return AppResult.Failure("Sign in to start your assessment.")
        }
        return when (val result = MovitData.account.resolveAssessmentTemplate(mode)) {
            is AppResult.Success -> AppResult.Success(AssessmentApiMapper.mapTemplate(result.value, language))
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    override suspend fun submitBodyScan(result: AssessmentBodyScanResult): AppResult<AssessmentResultsUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        if (platform.authHeader() == null) {
            return AppResult.Failure("Sign in to save your assessment.")
        }
        return when (val upload = MovitData.account.uploadAssessment(result.uploadRequest)) {
            is AppResult.Success -> AppResult.Success(AssessmentApiMapper.map(upload.value, platform.preferredLanguage()))
            is AppResult.Failure -> AppResult.Failure(upload.message)
        }
    }

    override suspend fun fetchLastResults(language: String): AppResult<AssessmentResultsUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        if (platform.authHeader() == null) {
            return AppResult.Failure("Sign in to load your assessment.")
        }
        return when (val result = MovitData.account.fetchLatestAssessment()) {
            is AppResult.Success -> AppResult.Success(AssessmentApiMapper.map(result.value, language))
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }
}

private const val DATA_LAYER_NOT_INSTALLED = "Movit data layer is not installed."
