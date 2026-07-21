package com.movit.core.data.repository

import com.movit.core.data.MovitData
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.sync.MovitSyncOrchestrator
import com.movit.core.data.sync.currentTimeMs
import com.movit.core.network.MovitApiException
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.TrainingConfigApiResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
 * B3: single-flight + negative-cache for ensure attempts.
 * Negative entries expire after [NEGATIVE_CACHE_TTL_MS].
 */
internal object TrainingConfigEnsureGate {
    private val mapMutex = Mutex()
    private val flightLocks = mutableMapOf<String, Mutex>()
    private val negativeUntilMs = mutableMapOf<String, Long>()

    suspend fun <T> withSingleFlight(key: String, block: suspend () -> T): T {
        val lock = mapMutex.withLock {
            flightLocks.getOrPut(key) { Mutex() }
        }
        return lock.withLock { block() }
    }

    fun isNegativeCached(key: String, nowMs: Long = currentTimeMs()): Boolean {
        val until = negativeUntilMs[key] ?: return false
        if (nowMs >= until) {
            negativeUntilMs.remove(key)
            return false
        }
        return true
    }

    fun markNegative(key: String, nowMs: Long = currentTimeMs()) {
        negativeUntilMs[key] = nowMs + NEGATIVE_CACHE_TTL_MS
    }

    fun clearNegative(key: String) {
        negativeUntilMs.remove(key)
    }

    /** Test seam. */
    internal fun resetForTests() {
        negativeUntilMs.clear()
        flightLocks.clear()
    }

    const val NEGATIVE_CACHE_TTL_MS = 60_000L
}

/**
 * Ensures a training config exists locally. When missing and online:
 * 1) workout-template training-config (if template id known)
 * 2) delta sync
 * 3) GET /mobile/exercises/:slug/training-config (R4 client stub — backend may still 404)
 *
 * Never auto-escalates to full refresh (P2.11). B3: single-flight + negative-cache.
 */
suspend fun TrainingConfigRepository.ensure(
    slug: String,
    workoutTemplateId: String? = null,
    sync: MovitSyncOrchestrator? = null,
    api: MovitMobileApi? = null,
    platform: MovitPlatformBindings? = null,
): TrainingConfigEnsureResult {
    val normalized = slug.trim()
    if (normalized.isBlank()) {
        return TrainingConfigEnsureResult.Unavailable(TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync)
    }
    if (supports(normalized)) return TrainingConfigEnsureResult.Available

    return TrainingConfigEnsureGate.withSingleFlight(normalized) {
        ensureUnlocked(
            slug = normalized,
            workoutTemplateId = workoutTemplateId,
            sync = sync,
            api = api,
            platform = platform,
        )
    }
}

private suspend fun TrainingConfigRepository.ensureUnlocked(
    slug: String,
    workoutTemplateId: String?,
    sync: MovitSyncOrchestrator?,
    api: MovitMobileApi?,
    platform: MovitPlatformBindings?,
): TrainingConfigEnsureResult {
    if (supports(slug)) return TrainingConfigEnsureResult.Available

    if (TrainingConfigEnsureGate.isNegativeCached(slug)) {
        return TrainingConfigEnsureResult.Unavailable(
            TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync,
        )
    }

    val resolvedPlatform = platform ?: MovitData.requirePlatform()
    val resolvedSync = sync ?: MovitData.sync
    val resolvedApi = api ?: MovitData.koin().get()

    if (!resolvedPlatform.isNetworkAvailable()) {
        return TrainingConfigEnsureResult.Unavailable(TrainingConfigEnsureResult.Unavailable.Reason.Offline)
    }

    val templateId = workoutTemplateId?.sanitizeWorkoutTemplateId()
    var sawHardFailure = false

    if (templateId != null) {
        when (
            fetchAndApplyWorkoutTemplateConfigs(
                templateId = templateId,
                api = resolvedApi,
                platform = resolvedPlatform,
                repository = this,
            )
        ) {
            FetchConfigOutcome.Applied -> {
                if (supports(slug)) {
                    TrainingConfigEnsureGate.clearNegative(slug)
                    return TrainingConfigEnsureResult.Available
                }
            }
            FetchConfigOutcome.HardFailure -> sawHardFailure = true
            FetchConfigOutcome.Miss -> Unit
        }
    }

    // Delta sync may bring the config; wait if another cycle holds the lock (P2.7 / B-N3).
    // forceCheck respects HTTP cooldown (B3); manual fullRefresh in profile bypasses it.
    runSyncAttempt(resolvedSync, forceCheck = true)
    if (supports(slug)) {
        TrainingConfigEnsureGate.clearNegative(slug)
        return TrainingConfigEnsureResult.Available
    }

    // P2.11: retry single-template endpoint only — never forceFullRefresh=true here.
    if (templateId != null) {
        when (
            fetchAndApplyWorkoutTemplateConfigs(
                templateId = templateId,
                api = resolvedApi,
                platform = resolvedPlatform,
                repository = this,
            )
        ) {
            FetchConfigOutcome.Applied -> {
                if (supports(slug)) {
                    TrainingConfigEnsureGate.clearNegative(slug)
                    return TrainingConfigEnsureResult.Available
                }
            }
            FetchConfigOutcome.HardFailure -> sawHardFailure = true
            FetchConfigOutcome.Miss -> Unit
        }
    }

    // R4 step 3: per-exercise training-config (client ready; backend may 404 until shipped).
    when (
        fetchAndApplyExerciseTrainingConfig(
            slug = slug,
            api = resolvedApi,
            platform = resolvedPlatform,
            repository = this,
        )
    ) {
        FetchConfigOutcome.Applied -> {
            if (supports(slug)) {
                TrainingConfigEnsureGate.clearNegative(slug)
                return TrainingConfigEnsureResult.Available
            }
        }
        FetchConfigOutcome.HardFailure -> sawHardFailure = true
        FetchConfigOutcome.Miss -> Unit
    }

    if (sawHardFailure || !supports(slug)) {
        TrainingConfigEnsureGate.markNegative(slug)
    }
    return TrainingConfigEnsureResult.Unavailable(
        TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync,
    )
}

/**
 * Preflight for a whole workout snapshot: prepare configs for every exercise slug.
 * Uses one template fetch when possible (not per-exercise Start).
 */
suspend fun TrainingConfigRepository.ensureAll(
    slugs: Collection<String>,
    workoutTemplateId: String? = null,
    sync: MovitSyncOrchestrator? = null,
    api: MovitMobileApi? = null,
    platform: MovitPlatformBindings? = null,
): TrainingConfigEnsureResult {
    val normalized = slugs.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (normalized.isEmpty()) return TrainingConfigEnsureResult.Available
    if (normalized.all { supports(it) }) return TrainingConfigEnsureResult.Available

    val resolvedPlatform = platform ?: MovitData.requirePlatform()
    val resolvedSync = sync ?: MovitData.sync
    val resolvedApi = api ?: MovitData.koin().get()
    val templateId = workoutTemplateId?.sanitizeWorkoutTemplateId()

    if (!resolvedPlatform.isNetworkAvailable()) {
        val anyCached = normalized.any { supports(it) }
        return if (anyCached) {
            // Partial cache is still Offline — callers must require ALL configs for OfflineReady.
            TrainingConfigEnsureResult.Unavailable(TrainingConfigEnsureResult.Unavailable.Reason.Offline)
        } else {
            TrainingConfigEnsureResult.Unavailable(TrainingConfigEnsureResult.Unavailable.Reason.Offline)
        }
    }

    if (templateId != null) {
        fetchAndApplyWorkoutTemplateConfigs(
            templateId = templateId,
            api = resolvedApi,
            platform = resolvedPlatform,
            repository = this,
        )
        if (normalized.all { supports(it) }) return TrainingConfigEnsureResult.Available
    }

    runSyncAttempt(resolvedSync, forceCheck = true)
    if (normalized.all { supports(it) }) return TrainingConfigEnsureResult.Available

    if (templateId != null) {
        fetchAndApplyWorkoutTemplateConfigs(
            templateId = templateId,
            api = resolvedApi,
            platform = resolvedPlatform,
            repository = this,
        )
        if (normalized.all { supports(it) }) return TrainingConfigEnsureResult.Available
    }

    // Fall back: ensure each missing slug individually (still no full refresh).
    for (slug in normalized) {
        if (supports(slug)) continue
        val one = ensure(
            slug = slug,
            workoutTemplateId = workoutTemplateId,
            sync = resolvedSync,
            api = resolvedApi,
            platform = resolvedPlatform,
        )
        if (one is TrainingConfigEnsureResult.Unavailable) return one
    }
    return if (normalized.all { supports(it) }) {
        TrainingConfigEnsureResult.Available
    } else {
        TrainingConfigEnsureResult.Unavailable(TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync)
    }
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

internal enum class FetchConfigOutcome {
    Applied,
    Miss,
    /** 404 / 5xx — eligible for negative-cache. */
    HardFailure,
}

private suspend fun fetchAndApplyWorkoutTemplateConfigs(
    templateId: String,
    api: MovitMobileApi,
    platform: MovitPlatformBindings,
    repository: TrainingConfigRepository,
): FetchConfigOutcome {
    val auth = platform.authHeader() ?: return FetchConfigOutcome.Miss
    val result = api.fetchWorkoutTrainingConfig(
        workoutTemplateId = templateId,
        authorization = auth,
    )
    val response = result.getOrElse { error ->
        return if (isHardHttpFailure(error)) FetchConfigOutcome.HardFailure else FetchConfigOutcome.Miss
    }
    if (!response.success) return FetchConfigOutcome.Miss
    val exercises = extractTrainingConfigExercises(response) ?: return FetchConfigOutcome.Miss
    if (exercises.isEmpty()) return FetchConfigOutcome.Miss
    repository.applySyncExercises(exercises = exercises, isFullSync = false)
    return FetchConfigOutcome.Applied
}

/**
 * R4 client: GET /api/mobile/exercises/:slug/training-config.
 * Backend may not be deployed yet — 404 is treated as HardFailure (negative-cached).
 */
private suspend fun fetchAndApplyExerciseTrainingConfig(
    slug: String,
    api: MovitMobileApi,
    platform: MovitPlatformBindings,
    repository: TrainingConfigRepository,
): FetchConfigOutcome {
    val auth = platform.authHeader() ?: return FetchConfigOutcome.Miss
    val result = api.fetchExerciseTrainingConfig(
        slug = slug,
        authorization = auth,
    )
    val response = result.getOrElse { error ->
        return if (isHardHttpFailure(error)) FetchConfigOutcome.HardFailure else FetchConfigOutcome.Miss
    }
    if (!response.success) return FetchConfigOutcome.Miss
    val exercises = extractExerciseTrainingConfigPayload(response) ?: return FetchConfigOutcome.Miss
    if (exercises.isEmpty()) return FetchConfigOutcome.Miss
    repository.applySyncExercises(exercises = exercises, isFullSync = false)
    return FetchConfigOutcome.Applied
}

private fun isHardHttpFailure(error: Throwable): Boolean {
    val status = (error as? MovitApiException)?.status ?: return false
    return status == 404 || status in 500..599
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

/**
 * Accepts either `{ exercises: [...] }` or a single exercise object as `data`.
 */
internal fun extractExerciseTrainingConfigPayload(response: TrainingConfigApiResponse): List<JsonElement>? {
    extractTrainingConfigExercises(response)?.let { return it }
    val data = response.data ?: return null
    return when (data) {
        is JsonObject -> listOf(data)
        is JsonArray -> data.toList()
        else -> null
    }
}
