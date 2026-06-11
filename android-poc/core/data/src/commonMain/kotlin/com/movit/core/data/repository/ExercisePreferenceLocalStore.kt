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
 * KMP equivalent of legacy [com.trainingvalidator.poc.storage.UserExercisePreferenceStore].
 */
class ExercisePreferenceLocalStore(
    private val localStore: MovitLocalStore,
) {
    fun get(exerciseId: String): UserExercisePreferenceUpsertRequest? =
        MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey(exerciseId),
            UserExercisePreferenceUpsertRequest.serializer(),
        )

    fun upsert(exerciseId: String, request: UserExercisePreferenceUpsertRequest) {
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey(exerciseId),
            request,
            UserExercisePreferenceUpsertRequest.serializer(),
        )
    }

    fun remove(exerciseId: String) {
        localStore.remove(
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey(exerciseId),
        )
    }

    suspend fun hydrateFromSync(
        rows: List<UserExercisePreferenceSyncDto>,
        pendingExerciseIds: Set<String> = emptySet(),
    ) {
        rows.forEach { row ->
            val exerciseId = row.exerciseId.ifBlank { row.exerciseSlug }
            if (exerciseId.isBlank() || exerciseId in pendingExerciseIds) return@forEach

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
