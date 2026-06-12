package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.shared.AppResult
import kotlin.math.roundToInt

data class WorkoutFormInsightUi(
    val formPercent: Int,
    val tip: String,
)

internal object WorkoutFormInsightLoader {

    suspend fun load(programId: String?, exerciseSlug: String?): WorkoutFormInsightUi? {
        if (programId.isNullOrBlank() || exerciseSlug.isNullOrBlank()) return null
        if (!MovitData.isInstalled) return null

        val response = when (
            val result = MovitData.reports.syncExerciseMetrics(
                programId = programId,
                exerciseSlug = exerciseSlug,
            )
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return null
        }
        return mapResponse(response)
    }

    fun mapResponse(response: MetricsApiResponse): WorkoutFormInsightUi? {
        if (!response.success) return null
        val summary = response.summary ?: return null
        val formPercent = summary.averageFormScore?.roundToInt()?.coerceIn(0, 100) ?: return null
        val tip = response.insights
            ?.firstOrNull { it.message.isNotBlank() }
            ?.message
            ?: summary.formRating?.takeIf { it.isNotBlank() }
            ?: return null
        return WorkoutFormInsightUi(formPercent = formPercent, tip = tip)
    }
}
