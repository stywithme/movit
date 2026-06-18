package com.movit.core.data.repository

import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.cache.MovitLruCache
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.sync.ExerciseMessageLibraryMerger
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.MessageLibraryStatsDto
import com.movit.core.network.dto.SyncMessageTemplateDto
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
    private val messageLibraryCache: MessageLibraryCache? = null,
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

    fun resolveAvailableSlug(vararg candidates: String?): String? {
        val index = readSlugIndexSet()
        candidates
            .asSequence()
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { candidate ->
                val canonical = resolveCanonicalSlug(candidate)
                when {
                    canonical in index -> return canonical
                    candidate in index -> return candidate
                }
            }
        return null
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
        val messageLibrary = messageLibraryCache?.read().orEmpty()
        ExerciseConfigParser.parseRecords(exercises).forEach { record ->
            val slug = record.slug
            if (slug.isBlank()) return@forEach
            val toPersist = mergeRecordForPersist(record, messageLibrary)
            MovitCachePolicy.writeJson(
                localStore,
                MovitCacheKeys.EXERCISE_CONFIG_STORE,
                MovitCacheKeys.exerciseConfigKey(slug),
                toPersist.withSanitizedConfig(),
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

    /**
     * Persists [messageLibrary] and merges template text into cached exercise configs (legacy parity).
     * @return number of exercise records updated when merge ran on a non-empty cache.
     */
    fun applySyncMessageLibrary(
        messageLibrary: List<SyncMessageTemplateDto>,
    ): Int {
        if (messageLibrary.isEmpty()) return 0

        val slugs = readSlugIndex()
        if (slugs.isEmpty()) return 0

        val merged = ExerciseMessageLibraryMerger.resolveRecords(
            records = slugs.mapNotNull { slug -> readRecordFromDisk(slug) },
            messageLibrary = messageLibrary,
        )
        merged.forEach { record ->
            if (record.slug.isBlank()) return@forEach
            MovitCachePolicy.writeJson(
                localStore,
                MovitCacheKeys.EXERCISE_CONFIG_STORE,
                MovitCacheKeys.exerciseConfigKey(record.slug),
                record.withSanitizedConfig(),
                ExerciseConfigRecord.serializer(),
            )
        }
        invalidateMemoryCaches()
        return merged.size
    }

    fun computeMessageLibraryStats(
        messageLibrary: List<SyncMessageTemplateDto>,
        assignmentsInCachedExercises: Int,
    ): MessageLibraryStatsDto {
        val withAudio = messageLibrary.count { template ->
            val content = template.content
            content.en.isNotBlank() || content.ar.isNotBlank()
        }
        return MessageLibraryStatsDto(
            totalMessages = messageLibrary.size,
            totalWithAudio = withAudio,
            totalAssignments = assignmentsInCachedExercises,
            fingerprint = messageLibrary
                .sortedBy { it.id }
                .joinToString("|") { "${it.id}:${it.code}" },
        )
    }

    fun countMessageAssignmentsInCache(): Int =
        readSlugIndex().sumOf { slug ->
            readRecordFromDisk(slug)?.config?.poseVariants
                ?.sumOf { variant -> variant.messageAssignments.size }
                ?: 0
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

    private fun readRecordFromDisk(slug: String): ExerciseConfigRecord? {
        val record = MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.exerciseConfigKey(slug),
            ExerciseConfigRecord.serializer(),
        ) ?: return null
        return mergeRecordForRead(record)
    }

    private fun mergeRecordForRead(record: ExerciseConfigRecord): ExerciseConfigRecord {
        val library = messageLibraryCache?.read().orEmpty()
        if (library.isEmpty()) return record
        if (!ExerciseMessageLibraryMerger.hasUnresolvedAssignments(record, library)) return record
        return ExerciseMessageLibraryMerger.resolveRecords(listOf(record), library).single()
    }

    private fun mergeRecordForPersist(
        record: ExerciseConfigRecord,
        messageLibrary: List<SyncMessageTemplateDto>,
    ): ExerciseConfigRecord {
        if (messageLibrary.isEmpty()) return record
        if (!ExerciseMessageLibraryMerger.hasUnresolvedAssignments(record, messageLibrary)) return record
        return ExerciseMessageLibraryMerger.resolveRecords(listOf(record), messageLibrary).single()
    }

    fun slugForExerciseId(id: String): String? = findSlugById(id)

    fun resolveCanonicalExerciseId(alias: String): String =
        ExerciseIdResolver(localStore).resolveCanonicalExerciseId(alias)

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
    }
}
