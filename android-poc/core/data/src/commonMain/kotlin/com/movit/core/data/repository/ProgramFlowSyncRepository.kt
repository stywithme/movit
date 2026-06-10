package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.network.dto.ProgramProgressMetricsPayloadDto
import com.movit.shared.AppResult

class ProgramFlowSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val exploreSync: ExploreSyncRepository,
    private val homeSync: HomeSyncRepository,
    private val planSync: PlanSyncRepository,
) {
    fun readCachedExplore(): ExploreDataDto? = exploreSync.readCached()

    fun readCachedHome(): HomeDataDto? = homeSync.readCached()

    fun readCachedProgram(programId: String): ProgramExportDto? {
        val raw = platform().readCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.programKey(programId))
            ?: return null
        return runCatching { MovitJson.decodeFromString<ProgramExportDto>(raw) }.getOrNull()
    }

    suspend fun syncExplore(): AppResult<ExploreDataDto> = exploreSync.sync()

    suspend fun syncHome(): AppResult<HomeDataDto> = homeSync.sync()

    suspend fun refreshActiveUserProgramId(): AppResult<String?> = planSync.refreshActiveUserProgramId()

    suspend fun syncProgram(programId: String): AppResult<ProgramExportDto> {
        val bindings = platform()
        val cacheKey = MovitCacheKeys.programKey(programId)
        val cached = readCachedProgram(programId)

        val response = api.fetchProgram(
            programId = programId,
            authorization = bindings.authHeader(),
        ).getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(error.message ?: "Unable to load program.")
        }

        if (!response.success) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(response.error ?: "Unable to load program.")
        }

        val payload = response.data
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Program response was empty.")

        bindings.writeCache(
            MovitCacheKeys.PROGRAM_STORE,
            cacheKey,
            MovitJson.encodeToString(ProgramExportDto.serializer(), payload),
        )

        return AppResult.Success(payload)
    }

    suspend fun syncProgressMetrics(
        userProgramId: String,
    ): AppResult<ProgramProgressMetricsPayloadDto> {
        val bindings = platform()
        val auth = bindings.authHeader()
            ?: return AppResult.Failure("Sign in to load weekly report.")

        val response = api.fetchProgramProgressMetrics(
            userProgramId = userProgramId,
            authorization = auth,
        ).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Unable to load weekly report.")
        }

        if (!response.success) {
            return AppResult.Failure(response.error ?: "Unable to load weekly report.")
        }

        val payload = response.data
            ?: return AppResult.Failure("Weekly report response was empty.")

        return AppResult.Success(payload)
    }
}
