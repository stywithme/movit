package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.ExercisePreferenceDeleteOutboxPayload
import com.movit.core.data.outbox.ExercisePreferenceUpsertOutboxPayload
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.UserExercisePreferenceSyncDto
import com.movit.core.network.dto.UserExercisePreferenceUpsertRequest

/**
 * KMP equivalent of legacy [com.movit.storage.UserExercisePreferenceStore].
 * Preferences are stored by backend [exerciseId]; [ExerciseIdResolver] resolves slug aliases on read/write.
 */
open class ExercisePreferenceLocalStore(
    private val localStore: MovitLocalStore,
    private val exerciseIdResolver: ExerciseIdResolver = ExerciseIdResolver(localStore),
) {
    fun get(exerciseIdOrSlug: String): UserExercisePreferenceUpsertRequest? {
        val canonicalId = exerciseIdResolver.resolveCanonicalExerciseId(exerciseIdOrSlug)
        return readPreference(canonicalId)
            ?: if (canonicalId != exerciseIdOrSlug) readPreference(exerciseIdOrSlug) else null
    }

    fun upsert(exerciseIdOrSlug: String, request: UserExercisePreferenceUpsertRequest) {
        val canonicalId = exerciseIdResolver.resolveCanonicalExerciseId(exerciseIdOrSlug)
        writePreference(canonicalId, request)
        if (canonicalId != exerciseIdOrSlug) {
            removeLegacyAliasKey(exerciseIdOrSlug)
        }
    }

    fun remove(exerciseIdOrSlug: String) {
        val canonicalId = exerciseIdResolver.resolveCanonicalExerciseId(exerciseIdOrSlug)
        removePreference(canonicalId)
        if (canonicalId != exerciseIdOrSlug) {
            removeLegacyAliasKey(exerciseIdOrSlug)
        }
    }

    open suspend fun hydrateFromSync(
        rows: List<UserExercisePreferenceSyncDto>,
        pendingExerciseIds: Set<String> = emptySet(),
    ) {
        rows.forEach { row ->
            val exerciseId = row.exerciseId.takeIf { it.isNotBlank() }
                ?: exerciseIdResolver.resolveCanonicalExerciseId(row.exerciseSlug)
            if (exerciseId.isBlank()) return@forEach
            val pending = pendingExerciseIds.any { pendingId ->
                pendingId == exerciseId ||
                    exerciseIdResolver.resolveCanonicalExerciseId(pendingId) == exerciseId
            }
            if (pending) return@forEach

            val hasAny = row.customReps != null ||
                row.customDurationSec != null ||
                row.customWeightKg != null
            if (!hasAny) {
                remove(exerciseId)
                return@forEach
            }

            upsert(
                exerciseId,
                UserExercisePreferenceUpsertRequest(
                    customReps = row.customReps,
                    customDurationSec = row.customDurationSec,
                    customWeightKg = row.customWeightKg?.toFloat(),
                ),
            )
        }
    }

    private fun readPreference(exerciseId: String): UserExercisePreferenceUpsertRequest? =
        MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey(exerciseId),
            UserExercisePreferenceUpsertRequest.serializer(),
        )

    private fun writePreference(exerciseId: String, request: UserExercisePreferenceUpsertRequest) {
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey(exerciseId),
            request,
            UserExercisePreferenceUpsertRequest.serializer(),
        )
    }

    private fun removePreference(exerciseId: String) {
        localStore.remove(
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey(exerciseId),
        )
    }

    private fun removeLegacyAliasKey(exerciseIdOrSlug: String) {
        if (exerciseIdOrSlug.isBlank()) return
        localStore.remove(
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey(exerciseIdOrSlug),
        )
    }

    companion object {
        suspend fun pendingExerciseIdsFromOutbox(localStore: MovitLocalStore): Set<String> {
            val ids = mutableSetOf<String>()
            for (entry in localStore.listPendingOutbox()) {
                if (entry.status != OutboxStatus.PENDING) continue
                when (entry.type) {
                    OutboxOperationType.EXERCISE_PREFERENCE_UPSERT -> {
                        runCatching {
                            MovitJson.decodeFromString<ExercisePreferenceUpsertOutboxPayload>(
                                entry.payload,
                            ).exerciseId
                        }.getOrNull()?.let(ids::add)
                    }
                    OutboxOperationType.EXERCISE_PREFERENCE_DELETE -> {
                        runCatching {
                            MovitJson.decodeFromString<ExercisePreferenceDeleteOutboxPayload>(entry.payload)
                                .exerciseId
                        }.getOrNull()?.let(ids::add)
                    }
                    else -> Unit
                }
            }
            return ids
        }
    }
}
