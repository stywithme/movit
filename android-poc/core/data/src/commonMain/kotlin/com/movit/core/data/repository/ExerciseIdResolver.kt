package com.movit.core.data.repository

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.network.MovitJson
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Resolves training UI aliases (slug or legacy slug key) to backend [exerciseId].
 * Uses the exercise-config slug alias map populated by [TrainingConfigRepository.applySyncExercises].
 */
class ExerciseIdResolver(
    private val localStore: MovitLocalStore,
) {
    fun resolveCanonicalExerciseId(alias: String): String {
        if (alias.isBlank()) return alias
        val aliases = readSlugAliasMap()
        if (alias in aliases) return alias
        aliases.entries.firstOrNull { it.value == alias }?.key?.let { return it }
        localStore.readString(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.exerciseIdToSlugKey(alias),
        )?.takeIf { it.isNotBlank() }?.let { return alias }
        return alias
    }

    fun isLikelyServerExerciseId(value: String): Boolean {
        if (value.isBlank()) return false
        val aliases = readSlugAliasMap()
        return value in aliases ||
            localStore.readString(
                MovitCacheKeys.EXERCISE_CONFIG_STORE,
                MovitCacheKeys.exerciseIdToSlugKey(value),
            ) != null
    }

    private fun readSlugAliasMap(): Map<String, String> {
        val raw = localStore.readJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_ALIASES,
        ) ?: return emptyMap()
        return runCatching {
            MovitJson.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                raw,
            )
        }.getOrDefault(emptyMap())
    }
}
