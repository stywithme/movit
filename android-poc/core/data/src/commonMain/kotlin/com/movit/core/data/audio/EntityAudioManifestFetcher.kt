package com.movit.core.data.audio

import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.network.dto.EntityAudioManifestApiResponse

/**
 * Fetches per-entity audio manifests from the backend and merges them into [AudioManifestCache].
 */
class EntityAudioManifestFetcher(
    private val client: EntityAudioManifestClient,
    private val manifestCache: AudioManifestCache,
    private val platform: () -> MovitPlatformBindings,
    private val exploreSync: ExploreSyncRepository,
) {
    data class Targets(
        val exerciseSlugs: Collection<String> = emptyList(),
        val workoutTemplateSlugs: Collection<String> = emptyList(),
        val workoutTemplateIds: Collection<String> = emptyList(),
    )

    suspend fun fetchAndMerge(targets: Targets): Int {
        val bindings = platform()
        if (!bindings.isNetworkAvailable()) return 0
        val auth = bindings.authHeader() ?: return 0

        val workoutSlugs = resolveWorkoutSlugs(targets)
        val exerciseSlugs = targets.exerciseSlugs
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (workoutSlugs.isEmpty() && exerciseSlugs.isEmpty()) return 0

        var merged = 0
        workoutSlugs.forEach { slug ->
            merged += mergeResponse(client.fetchWorkoutAudioManifest(slug, auth), bindings.apiBaseUrl())
        }
        exerciseSlugs.forEach { slug ->
            merged += mergeResponse(client.fetchExerciseAudioManifest(slug, auth), bindings.apiBaseUrl())
        }
        return merged
    }

    private fun resolveWorkoutSlugs(targets: Targets): List<String> {
        val slugs = linkedSetOf<String>()
        targets.workoutTemplateSlugs
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { slugs += it }
        targets.workoutTemplateIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { templateId ->
                val slug = exploreSync.slugForWorkoutTemplateId(templateId) ?: templateId
                slugs += slug
            }
        return slugs.toList()
    }

    private fun mergeResponse(
        result: Result<EntityAudioManifestApiResponse>,
        apiBaseUrl: String,
    ): Int {
        val response = result.getOrNull() ?: return 0
        if (!response.success) return 0
        val manifest = response.data?.audioManifest ?: return 0
        if (manifest.files.isEmpty()) return 0

        val base = AudioManifestCache.resolveEffectiveAudioBase(apiBaseUrl, manifest)
        manifestCache.mergePartial(base, manifest)
        return manifest.files.size
    }
}

interface EntityAudioManifestClient {
    suspend fun fetchWorkoutAudioManifest(
        slug: String,
        authorization: String?,
    ): Result<EntityAudioManifestApiResponse>

    suspend fun fetchExerciseAudioManifest(
        slug: String,
        authorization: String?,
    ): Result<EntityAudioManifestApiResponse>
}

class MovitMobileEntityAudioClient(
    private val api: com.movit.core.network.MovitMobileApi,
) : EntityAudioManifestClient {
    override suspend fun fetchWorkoutAudioManifest(
        slug: String,
        authorization: String?,
    ): Result<EntityAudioManifestApiResponse> = api.fetchWorkoutAudioManifest(slug, authorization)

    override suspend fun fetchExerciseAudioManifest(
        slug: String,
        authorization: String?,
    ): Result<EntityAudioManifestApiResponse> = api.fetchExerciseAudioManifest(slug, authorization)
}
