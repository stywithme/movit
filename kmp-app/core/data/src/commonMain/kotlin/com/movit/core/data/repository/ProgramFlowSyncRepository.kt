package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.network.dto.ProgramProgressMetricsPayloadDto
import com.movit.shared.AppResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class ProgramFlowSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val localStore: () -> MovitLocalStore,
    private val exploreSync: ExploreSyncRepository,
    private val homeSync: HomeSyncRepository,
    private val planSync: PlanSyncRepository,
) {
    fun readCachedExplore(): ExploreDataDto? = exploreSync.readCached()

    fun readCachedHome(): HomeDataDto? = homeSync.readCached()

    fun readCachedProgram(programId: String): ProgramExportDto? =
        MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.PROGRAM_STORE,
            MovitCacheKeys.programKey(programId),
            ProgramExportDto.serializer(),
        )
            ?: readProgramBySlug(programId)

    private fun readProgramBySlug(slug: String): ProgramExportDto? {
        val store = localStore()
        val rawIndex = store.readJsonCache(MovitCacheKeys.CATALOG_INDEX_STORE, MovitCacheKeys.PROGRAM_ID_INDEX)
            ?: return null
        val ids = runCatching {
            MovitJson.decodeFromString(ListSerializer(String.serializer()), rawIndex)
        }.getOrNull() ?: return null
        return ids.firstNotNullOfOrNull { id ->
            MovitCachePolicy.readJson(
                store,
                MovitCacheKeys.PROGRAM_STORE,
                MovitCacheKeys.programKey(id),
                ProgramExportDto.serializer(),
            )?.takeIf { it.slug == slug }
        }
    }

    suspend fun syncExplore(): AppResult<ExploreDataDto> =
        when (val result = exploreSync.sync()) {
            is AppResult.Success -> result
            is AppResult.Failure -> exploreSync.readCached()?.let { AppResult.Success(it) }
                ?: AppResult.Failure(result.message)
        }

    /** Full catalog repair — UX.2 «إصلاح الكتالوج» / drift only. */
    suspend fun repairExploreCatalog(): AppResult<ExploreDataDto> =
        when (val result = exploreSync.syncFull()) {
            is AppResult.Success -> result
            is AppResult.Failure -> exploreSync.readCached()?.let { AppResult.Success(it) }
                ?: AppResult.Failure(result.message)
        }

    suspend fun syncHome(): AppResult<HomeDataDto> = homeSync.sync()

    suspend fun refreshActiveUserProgramId(): AppResult<String?> = planSync.refreshActiveUserProgramId()

    suspend fun syncProgram(programId: String): AppResult<ProgramExportDto> {
        val bindings = platform()
        val store = localStore()
        val cached = readCachedProgram(programId)

        val response = api.fetchProgram(
            programId = programId,
            authorization = bindings.authHeader(),
        ).getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(error.message ?: "Unable to load program.")
        }

        if (!response.success || response.data == null) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(response.error ?: "Unable to load program.")
        }

        val dto = response.data!!
        // P2.12: normalize slug → id before write; lazy-remove slug-keyed duplicate.
        val canonicalId = dto.id.ifBlank { programId }
        MovitCachePolicy.writeJson(
            store,
            MovitCacheKeys.PROGRAM_STORE,
            MovitCacheKeys.programKey(canonicalId),
            dto,
            ProgramExportDto.serializer(),
        )
        if (programId != canonicalId) {
            store.removeJsonCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.programKey(programId))
        }

        return AppResult.Success(dto)
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

        if (!response.success || response.data == null) {
            return AppResult.Failure(response.error ?: "Unable to load weekly report.")
        }

        return AppResult.Success(response.data!!)
    }
}
