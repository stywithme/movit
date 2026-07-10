package com.movit.core.data.repository

import com.movit.core.data.MovitData
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.sync.MovitSyncOrchestrator
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.TrainingConfigApiResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

sealed interface TrainingConfigEnsureResult {
    data object Available : TrainingConfigEnsureResult

    data class Unavailable(
        val reason: Reason,
    ) : TrainingConfigEnsureResult {
        enum class Reason {
            Offline,
            NotFoundAfterSync,
        }
    }
}

/**
 * Ensures a training config exists locally. When missing and online, tries the
 * workout-template training-config endpoint only — never auto-escalates to full refresh (P2.11).
 */
suspend fun TrainingConfigRepository.ensure(
    slug: String,
    workoutTemplateId: String? = null,
    sync: MovitSyncOrchestrator? = null,
    api: MovitMobileApi? = null,
    platform: MovitPlatformBindings? = null,
): TrainingConfigEnsureResult {
    val resolvedPlatform = platform ?: MovitData.requirePlatform()
    val resolvedSync = sync ?: MovitData.sync
    val resolvedApi = api ?: MovitData.koin().get()
    val normalized = slug.trim()
    if (normalized.isBlank()) {
        return TrainingConfigEnsureResult.Unavailable(TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync)
    }
    if (supports(normalized)) return TrainingConfigEnsureResult.Available

    if (!resolvedPlatform.isNetworkAvailable()) {
        return TrainingConfigEnsureResult.Unavailable(TrainingConfigEnsureResult.Unavailable.Reason.Offline)
    }

    val templateId = workoutTemplateId?.sanitizeWorkoutTemplateId()
    if (templateId != null) {
        fetchAndApplyWorkoutTemplateConfigs(
            templateId = templateId,
            api = resolvedApi,
            platform = resolvedPlatform,
            repository = this,
        )
        if (supports(normalized)) return TrainingConfigEnsureResult.Available
    }

    // Delta sync may bring the config; wait if another cycle holds the lock (P2.7 / B-N3).
    runSyncAttempt(resolvedSync, forceCheck = true)
    if (supports(normalized)) return TrainingConfigEnsureResult.Available

    // P2.11: retry single-template endpoint only — never forceFullRefresh=true here.
    if (templateId != null) {
        fetchAndApplyWorkoutTemplateConfigs(
            templateId = templateId,
            api = resolvedApi,
            platform = resolvedPlatform,
            repository = this,
        )
        if (supports(normalized)) return TrainingConfigEnsureResult.Available
    }

    return TrainingConfigEnsureResult.Unavailable(
        TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync,
    )
}

private suspend fun runSyncAttempt(
    sync: MovitSyncOrchestrator,
    forceCheck: Boolean = false,
) {
    val outcome = sync.syncIfNeeded(forceCheck = forceCheck)
    if (outcome is MovitSyncOrchestrator.SyncOutcome.Skipped) {
        sync.awaitSyncIdle()
    }
}

private suspend fun fetchAndApplyWorkoutTemplateConfigs(
    templateId: String,
    api: MovitMobileApi,
    platform: MovitPlatformBindings,
    repository: TrainingConfigRepository,
): Boolean {
    val auth = platform.authHeader() ?: return false
    val response = api.fetchWorkoutTrainingConfig(
        workoutTemplateId = templateId,
        authorization = auth,
    ).getOrNull() ?: return false
    if (!response.success) return false
    val exercises = extractTrainingConfigExercises(response) ?: return false
    if (exercises.isEmpty()) return false
    repository.applySyncExercises(exercises = exercises, isFullSync = false)
    return true
}

/** Session workout keys (`session:…`) are not valid workout-template API ids. */
internal fun String.sanitizeWorkoutTemplateId(): String? =
    trim().takeIf { it.isNotBlank() && !it.startsWith(SESSION_WORKOUT_KEY_PREFIX) }

private const val SESSION_WORKOUT_KEY_PREFIX = "session:"

internal fun extractTrainingConfigExercises(response: TrainingConfigApiResponse): List<JsonElement>? {
    val data = response.data?.jsonObject ?: return null
    val exercisesElement = data["exercises"] ?: return null
    return when (exercisesElement) {
        is JsonArray -> exercisesElement.toList()
        else -> exercisesElement.jsonArray.toList()
    }
}
