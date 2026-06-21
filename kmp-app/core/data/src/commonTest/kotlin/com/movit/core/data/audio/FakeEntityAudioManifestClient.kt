package com.movit.core.data.audio

import com.movit.core.network.dto.EntityAudioManifestApiResponse
import com.movit.core.network.dto.EntityAudioManifestPayloadDto

class FakeEntityAudioManifestClient : EntityAudioManifestClient {
    val workoutCalls = mutableListOf<String>()
    val exerciseCalls = mutableListOf<String>()

    var workoutResponse: EntityAudioManifestApiResponse? = null
    var exerciseResponse: EntityAudioManifestApiResponse? = null

    override suspend fun fetchWorkoutAudioManifest(
        slug: String,
        authorization: String?,
    ): Result<EntityAudioManifestApiResponse> {
        workoutCalls += slug
        return Result.success(
            workoutResponse ?: EntityAudioManifestApiResponse(success = false),
        )
    }

    override suspend fun fetchExerciseAudioManifest(
        slug: String,
        authorization: String?,
    ): Result<EntityAudioManifestApiResponse> {
        exerciseCalls += slug
        return Result.success(
            exerciseResponse ?: EntityAudioManifestApiResponse(success = false),
        )
    }

    companion object {
        fun manifestResponse(
            slug: String,
            entityType: String,
            filename: String,
            baseUrl: String = "https://cdn.test/audio",
        ): EntityAudioManifestApiResponse = EntityAudioManifestApiResponse(
            success = true,
            data = EntityAudioManifestPayloadDto(
                entityType = entityType,
                slug = slug,
                audioManifest = com.movit.core.network.dto.AudioManifestDto(
                    baseUrl = baseUrl,
                    files = listOf(
                        com.movit.core.network.dto.AudioFileInfoDto(
                            filename = filename,
                            url = "$filename.wav",
                            language = "en",
                        ),
                    ),
                ),
            ),
        )
    }
}
