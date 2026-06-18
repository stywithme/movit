package com.movit.core.data.local

import com.movit.core.data.outbox.DayCustomizationCacheDto
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.DayCustomizationKeyResolver
import com.movit.core.data.repository.ExerciseIdResolver
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.UserProgramEnrollmentLocalStore
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.UserProgramExportDto
import kotlinx.serialization.builtins.ListSerializer

/**
 * One-time rewrite of legacy cache keys:
 * - day customizations keyed by catalog [programId] → [userProgramId]
 * - exercise preferences keyed by slug → server [exerciseId]
 */
class CanonicalCacheKeyMigrator(
    private val sqlStore: MovitLocalStore,
    private val platform: () -> MovitPlatformBindings,
) {
    fun migrateIfNeeded() {
        if (isMigrated()) return

        val enrollments = UserProgramEnrollmentLocalStore(sqlStore)
        val programIdToUserProgramId = buildProgramIdToUserProgramIdMap(enrollments)
        val dayResolver = DayCustomizationKeyResolver(enrollments)
        val exerciseResolver = ExerciseIdResolver(sqlStore)

        migrateDayCustomizations(programIdToUserProgramId, dayResolver)
        migrateExercisePreferences(exerciseResolver)

        markMigrated()
    }

    private fun isMigrated(): Boolean =
        sqlStore.readString(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.CANONICAL_CACHE_KEYS_MIGRATED) == "1"

    private fun markMigrated() {
        sqlStore.writeString(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.CANONICAL_CACHE_KEYS_MIGRATED, "1")
    }

    private fun buildProgramIdToUserProgramIdMap(
        enrollments: UserProgramEnrollmentLocalStore,
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()
        enrollments.listAll().forEach { enrollment ->
            val programId = enrollment.programId?.takeIf { it.isNotBlank() } ?: return@forEach
            map.putIfAbsent(programId, enrollment.id)
        }
        readLegacyUserPrograms().forEach { row ->
            val programId = row.programId?.takeIf { it.isNotBlank() } ?: return@forEach
            if (row.id.isNotBlank()) {
                map.putIfAbsent(programId, row.id)
            }
        }
        return map
    }

    private fun readLegacyUserPrograms(): List<UserProgramExportDto> {
        val bindings = platform()
        val legacyJson = bindings.readCache(
            MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
            MovitCacheKeys.LEGACY_USER_PROGRAMS_KEY,
        ) ?: sqlStore.readJsonCache(
            MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
            MovitCacheKeys.LEGACY_USER_PROGRAMS_KEY,
        ) ?: return emptyList()
        return runCatching {
            MovitJson.decodeFromString(ListSerializer(UserProgramExportDto.serializer()), legacyJson)
        }.getOrDefault(emptyList())
    }

    private fun migrateDayCustomizations(
        programIdToUserProgramId: Map<String, String>,
        dayResolver: DayCustomizationKeyResolver,
    ) {
        val sources = listOf(
            MovitCacheKeys.LEGACY_DAY_CUSTOMIZATION_STORE,
            MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
        )
        for (store in sources) {
            collectDayCustomizationEntries(store).forEach { (key, value) ->
                migrateDayCustomizationEntry(
                    store = MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
                    sourceStore = store,
                    key = key,
                    value = value,
                    programIdToUserProgramId = programIdToUserProgramId,
                    dayResolver = dayResolver,
                )
            }
        }
    }

    private fun collectDayCustomizationEntries(store: String): Map<String, String> {
        val bindings = platform()
        val fromPlatform = bindings.readAllCacheEntries(store)
        val fromSql = if (store == MovitCacheKeys.DAY_CUSTOMIZATION_STORE) {
            sqlStore.listJsonCacheEntries(store)
        } else {
            emptyMap()
        }
        return fromPlatform + fromSql
    }

    private fun migrateDayCustomizationEntry(
        store: String,
        sourceStore: String,
        key: String,
        value: String,
        programIdToUserProgramId: Map<String, String>,
        dayResolver: DayCustomizationKeyResolver,
    ) {
        val parts = DayCustomizationKeyResolver.parseDayCustomizationKey(key) ?: return
        val canonicalUserProgramId = when {
            dayResolver.isCanonicalUserProgramId(parts.enrollmentOrProgramId) ->
                parts.enrollmentOrProgramId
            else -> programIdToUserProgramId[parts.enrollmentOrProgramId]
        } ?: return

        val canonicalKey = MovitCacheKeys.dayCustomizationKey(
            canonicalUserProgramId,
            parts.weekNumber,
            parts.dayNumber,
        )
        if (canonicalKey == key && sourceStore == store) return

        if (sqlStore.readJsonCache(store, canonicalKey) != null) {
            sqlStore.removeJsonCache(sourceStore, key)
            platform().removeCache(sourceStore, key)
            return
        }

        val payload = rewriteDayCustomizationPayload(value, canonicalUserProgramId)
        sqlStore.writeJsonCache(store, canonicalKey, payload)
        sqlStore.removeJsonCache(sourceStore, key)
        platform().removeCache(sourceStore, key)
    }

    private fun rewriteDayCustomizationPayload(value: String, userProgramId: String): String =
        runCatching {
            val dto = MovitJson.decodeFromString(DayCustomizationCacheDto.serializer(), value)
            if (dto.userProgramId == userProgramId) {
                value
            } else {
                MovitJson.encodeToString(
                    DayCustomizationCacheDto.serializer(),
                    dto.copy(userProgramId = userProgramId),
                )
            }
        }.getOrDefault(value)

    private fun migrateExercisePreferences(exerciseResolver: ExerciseIdResolver) {
        val sources = listOf(
            MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE,
            MovitCacheKeys.PREFERENCES_STORE,
        )
        for (sourceStore in sources) {
            val targetStore = MovitCacheKeys.PREFERENCES_STORE
            collectPreferenceEntries(sourceStore).forEach { (key, value) ->
                migrateExercisePreferenceEntry(
                    targetStore = targetStore,
                    sourceStore = sourceStore,
                    key = key,
                    value = value,
                    exerciseResolver = exerciseResolver,
                )
            }
        }
    }

    private fun collectPreferenceEntries(store: String): Map<String, String> {
        val fromPlatform = platform().readAllCacheEntries(store)
        val fromSql = if (store == MovitCacheKeys.PREFERENCES_STORE) {
            sqlStore.listJsonCacheEntries(store)
        } else {
            emptyMap()
        }
        return fromPlatform + fromSql
    }

    private fun migrateExercisePreferenceEntry(
        targetStore: String,
        sourceStore: String,
        key: String,
        value: String,
        exerciseResolver: ExerciseIdResolver,
    ) {
        if (!key.startsWith(PREF_KEY_PREFIX)) return
        val alias = key.removePrefix(PREF_KEY_PREFIX)
        if (alias.isBlank()) return

        val canonicalId = exerciseResolver.resolveCanonicalExerciseId(alias)
        val canonicalKey = MovitCacheKeys.exercisePreferenceKey(canonicalId)
        if (canonicalKey == key && sourceStore == targetStore) return

        if (sqlStore.readJsonCache(targetStore, canonicalKey) == null) {
            sqlStore.writeJsonCache(targetStore, canonicalKey, value)
        }
        sqlStore.removeJsonCache(sourceStore, key)
        platform().removeCache(sourceStore, key)
    }

    private companion object {
        const val PREF_KEY_PREFIX = "pref_"
    }
}
