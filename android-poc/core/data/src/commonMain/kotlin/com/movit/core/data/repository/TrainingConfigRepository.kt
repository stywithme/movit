package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.network.MovitJson
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * Offline-safe exercise training-config cache (sync payload exercises[]).
 */
class TrainingConfigRepository(
    private val localStore: MovitLocalStore,
) {
    fun getBySlug(slug: String): ExerciseConfigRecord? = resolveBySlug(slug)

    fun getExercise(slug: String): ExerciseConfig? = resolveBySlug(slug)?.config

    fun supports(slug: String): Boolean = resolveBySlug(slug) != null

    fun resolveBySlug(slug: String): ExerciseConfigRecord? =
        readRecord(slug) ?: SLUG_ALIASES[slug]?.let(::readRecord)

    private fun readRecord(slug: String): ExerciseConfigRecord? =
        MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.exerciseConfigKey(slug),
            ExerciseConfigRecord.serializer(),
        )

    companion object {
        private val SLUG_ALIASES = mapOf(
            "squat" to "bodyweight-squat",
            "barbell-squat" to "bodyweight-squat",
        )
    }

    fun allCachedSlugs(): List<String> = readSlugIndex()

    fun applySyncExercises(
        exercises: List<JsonElement>,
        deletedExerciseIds: List<String> = emptyList(),
        isFullSync: Boolean = false,
    ) {
        if (isFullSync) {
            readSlugIndex().forEach { slug ->
                localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseConfigKey(slug))
            }
            writeSlugIndex(emptyList())
        }
        deletedExerciseIds.forEach { id ->
            findSlugById(id)?.let { slug ->
                localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseConfigKey(slug))
                writeSlugIndex(readSlugIndex().filterNot { it == slug })
            }
            localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseIdToSlugKey(id))
        }

        val slugs = readSlugIndex().toMutableSet()
        ExerciseConfigParser.parseRecords(exercises).forEach { record ->
            val slug = record.slug
            if (slug.isBlank()) return@forEach
            MovitCachePolicy.writeJson(
                localStore,
                MovitCacheKeys.EXERCISE_CONFIG_STORE,
                MovitCacheKeys.exerciseConfigKey(slug),
                record.withSanitizedConfig(),
                ExerciseConfigRecord.serializer(),
            )
            if (record.id.isNotBlank()) {
                localStore.writeString(
                    MovitCacheKeys.EXERCISE_CONFIG_STORE,
                    MovitCacheKeys.exerciseIdToSlugKey(record.id),
                    slug,
                )
            }
            slugs += slug
        }
        writeSlugIndex(slugs.toList())
    }

    fun seedRecord(record: ExerciseConfigRecord) {
        if (record.slug.isBlank()) return
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.exerciseConfigKey(record.slug),
            record.withSanitizedConfig(),
            ExerciseConfigRecord.serializer(),
        )
        writeSlugIndex((readSlugIndex() + record.slug).distinct())
    }

    private fun findSlugById(id: String): String? =
        localStore.readString(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseIdToSlugKey(id))

    private fun readSlugIndex(): List<String> {
        val raw = localStore.readJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_INDEX,
        ) ?: return emptyList()
        return runCatching {
            MovitJson.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun writeSlugIndex(slugs: List<String>) {
        localStore.writeJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_INDEX,
            MovitJson.encodeToString(ListSerializer(String.serializer()), slugs.distinct()),
        )
    }
}
