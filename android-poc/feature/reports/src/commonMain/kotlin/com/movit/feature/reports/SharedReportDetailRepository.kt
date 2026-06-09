package com.movit.feature.reports

import com.movit.core.data.MovitData
import com.movit.resources.strings.ReportDetailStrings
import com.movit.shared.AppResult

class SharedReportDetailRepository(
    private val fallback: ReportDetailRepository = DefaultReportDetailRepository(),
) : ReportDetailRepository {

    override suspend fun getReportDetail(reportId: String): AppResult<ReportDetailUi> {
        if (!MovitData.isInstalled || reportId.isBlank() || reportId == "preview") {
            return fallback.getReportDetail(reportId)
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = ReportDetailStrings.load(language)
        if (!platform.isProUser()) {
            return fallback.getReportDetail(reportId)
        }

        val programId = MovitData.home.readCached()?.trainMode?.activeProgram?.id
        if (programId.isNullOrBlank()) {
            return fallback.getReportDetail(reportId)
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
                    fallback.getReportDetail(reportId)
                }
            }
            is AppResult.Failure -> {
                val cached = MovitData.reports.readCachedExerciseMetrics(reportId)
                val detail = cached?.let { ReportDetailApiMapper.map(reportId, it, strings) }
                if (detail != null) {
                    AppResult.Success(detail)
                } else {
                    fallback.getReportDetail(reportId)
                }
            }
        }
    }
}

fun defaultReportDetailRepository(): ReportDetailRepository = SharedReportDetailRepository()
