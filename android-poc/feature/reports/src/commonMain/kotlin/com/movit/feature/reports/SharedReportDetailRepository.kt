package com.movit.feature.reports

import com.movit.core.data.MovitData
import com.movit.resources.strings.ReportDetailStrings
import com.movit.shared.AppResult

class SharedReportDetailRepository : ReportDetailRepository {

    override suspend fun getReportDetail(reportId: String): AppResult<ReportDetailUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        if (reportId.isBlank() || reportId == "preview") {
            return AppResult.Failure("Report not found.")
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = ReportDetailStrings.load(language)

        TrainingSessionReportCache.get(reportId)?.let { cached ->
            return AppResult.Success(MovitSessionReportUiMapper.mapPostTraining(cached, strings))
        }
        if (!platform.isProUser()) {
            return AppResult.Failure(REPORT_NOT_FOUND)
        }

        val programId = MovitData.home.readCached()?.trainMode?.activeProgram?.id
        if (programId.isNullOrBlank()) {
            return AppResult.Failure(REPORT_NOT_FOUND)
        }

        return when (
            val result = MovitData.reports.syncExerciseMetrics(
                programId = programId,
                exerciseSlug = reportId,
            )
        ) {
            is AppResult.Success -> {
                val detail = ReportDetailApiMapper.map(reportId, result.value, strings)
                if (detail != null) {
                    AppResult.Success(detail)
                } else {
                    AppResult.Failure(REPORT_NOT_FOUND)
                }
            }
            is AppResult.Failure -> {
                val cached = MovitData.reports.readCachedExerciseMetrics(reportId)
                val detail = cached?.let { ReportDetailApiMapper.map(reportId, it, strings) }
                if (detail != null) {
                    AppResult.Success(detail)
                } else {
                    AppResult.Failure(result.message)
                }
            }
        }
    }

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
        const val REPORT_NOT_FOUND = "Report not found."
    }
}

fun defaultReportDetailRepository(): ReportDetailRepository = SharedReportDetailRepository()
