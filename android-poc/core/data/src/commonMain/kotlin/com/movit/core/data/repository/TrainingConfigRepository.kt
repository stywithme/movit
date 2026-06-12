package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.cache.MovitLruCache
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.network.MovitJson
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * Offline-safe exercise training-config cache (sync payload exercises[]).
 */
class TrainingConfigRepository(
    private val localStore: MovitLocalStore,
) {
    private var memorySlugIndex: Set<String>? = null
    private var memorySlugAliases: Map<String, String>? = null
    private val parsedRecordCache = MovitLruCache<String, ExerciseConfigRecord>(PARSED_RECORD_CACHE_SIZE)

    fun getBySlug(slug: String): ExerciseConfigRecord? = resolveBySlug(slug)

    fun getExercise(slug: String): ExerciseConfig? = resolveBySlug(slug)?.config

    fun supports(slug: String): Boolean {
        val canonical = resolveCanonicalSlug(slug)
        val index = readSlugIndexSet()
        return canonical in index || slug in index
    }

    fun resolveBySlug(slug: String): ExerciseConfigRecord? {
        val canonical = resolveCanonicalSlug(slug)
        parsedRecordCache.get(canonical)?.let { return it }
        val record = readRecordFromDisk(canonical) ?: return null
        parsedRecordCache.put(canonical, record)
        return record
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
            writeSlugAliasMap(emptyMap())
        }
        deletedExerciseIds.forEach { id ->
            findSlugById(id)?.let { slug ->
                localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseConfigKey(slug))
                writeSlugIndex(readSlugIndex().filterNot { it == slug })
                removeSlugAliasForCanonical(slug)
            }
            localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseIdToSlugKey(id))
            removeSlugAlias(id)
        }

        val slugs = readSlugIndex().toMutableSet()
        val aliases = readSlugAliasMap().toMutableMap()
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
                registerSyncSlugAliases(record, aliases)
            }
            slugs += slug
        }
        writeSlugIndex(slugs.toList())
        writeSlugAliasMap(aliases)
        invalidateMemoryCaches()
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
        invalidateMemoryCaches(parsedSlug = record.slug)
    }

    private fun resolveCanonicalSlug(slug: String): String {
        readSlugAliasMap()[slug]?.let { return it }
        BUNDLED_SLUG_ALIASES[slug]?.let { return it }
        findSlugById(slug)?.let { return it }
        return slug
    }

    private fun registerSyncSlugAliases(record: ExerciseConfigRecord, aliases: MutableMap<String, String>) {
        val canonical = record.slug
        if (canonical.isBlank()) return
        if (record.id.isNotBlank() && record.id != canonical) {
            aliases[record.id] = canonical
        }
    }

    private fun readRecordFromDisk(slug: String): ExerciseConfigRecord? =
        MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.exerciseConfigKey(slug),
            ExerciseConfigRecord.serializer(),
        )

    fun slugForExerciseId(id: String): String? = findSlugById(id)

    private fun findSlugById(id: String): String? =
        localStore.readString(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseIdToSlugKey(id))

    private fun readSlugIndexSet(): Set<String> {
        memorySlugIndex?.let { return it }
        return readSlugIndex().toSet().also { memorySlugIndex = it }
    }

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
        memorySlugIndex = slugs.distinct().toSet()
    }

    private fun readSlugAliasMap(): Map<String, String> {
        memorySlugAliases?.let { return it }
        val raw = localStore.readJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_ALIASES,
        ) ?: return emptyMap<String, String>().also { memorySlugAliases = it }
        return runCatching {
            MovitJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), raw)
        }.getOrDefault(emptyMap()).also { memorySlugAliases = it }
    }

    private fun writeSlugAliasMap(aliases: Map<String, String>) {
        val normalized = aliases.filterValues { it.isNotBlank() }
        localStore.writeJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_ALIASES,
            MovitJson.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                normalized,
            ),
        )
        memorySlugAliases = normalized
    }

    private fun removeSlugAlias(alias: String) {
        val updated = readSlugAliasMap().toMutableMap()
        if (updated.remove(alias) == null) return
        writeSlugAliasMap(updated)
    }

    private fun removeSlugAliasForCanonical(canonical: String) {
        val updated = readSlugAliasMap()
            .filterNot { (_, value) -> value == canonical }
        writeSlugAliasMap(updated)
    }

    private fun invalidateMemoryCaches(parsedSlug: String? = null) {
        memorySlugIndex = null
        memorySlugAliases = null
        if (parsedSlug != null) {
            parsedRecordCache.remove(parsedSlug)
        } else {
            parsedRecordCache.clear()
        }
    }

    companion object {
        private const val PARSED_RECORD_CACHE_SIZE = 8

        /** Bundled seed aliases — kept until sync populates [EXERCISE_CONFIG_SLUG_ALIASES]. */
        private val BUNDLED_SLUG_ALIASES = mapOf(
            "squat" to "bodyweight-squat",
            "barbell-squat" to "bodyweight-squat",
        )
    }
}
