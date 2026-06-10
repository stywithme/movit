package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.UserProgramExportDto
import com.movit.shared.AppResult

class PlanSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val homeSync: HomeSyncRepository,
) {
    /**
     * Enrolls in [programId] via `POST /api/mobile/plan/enroll`, then resolves the active
     * [UserProgramExportDto.id] from `/api/mobile/sync` and persists it on the platform bindings
     * (required for effective-plan APIs on iOS where legacy ProgramRepository is absent).
     */
    suspend fun enrollProgram(programId: String): AppResult<String> {
        val bindings = platform()
        bindings.authHeader()
            ?: return AppResult.Failure("Sign in to enroll in a program.")

        val enrollResponse = api.enrollProgram(programId).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Enrollment failed.")
        }
        if (!enrollResponse.success) {
            return AppResult.Failure(enrollResponse.error ?: "Enrollment failed.")
        }

        homeSync.sync()

        val userPrograms = api.fetchSyncUserPrograms(forceRefresh = true).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Enrollment sync failed.")
        }
        val userProgramId = resolveActiveUserProgramId(userPrograms, programId)
            ?: return AppResult.Failure(
                "Enrollment succeeded but the active user program id was not returned.",
            )

        bindings.setActiveUserProgramId(userProgramId)
        return AppResult.Success(userProgramId)
    }

    /** Hydrates [MovitPlatformBindings.activeUserProgramId] from the server when a session exists. */
    suspend fun refreshActiveUserProgramId(): AppResult<String?> {
        val bindings = platform()
        if (bindings.authHeader() == null) {
            return AppResult.Success(bindings.activeUserProgramId())
        }

        val userPrograms = api.fetchSyncUserPrograms(forceRefresh = false).getOrElse {
            return AppResult.Success(bindings.activeUserProgramId())
        }
        val activeId = resolveActiveUserProgramId(userPrograms, programId = null)
        if (activeId != null) {
            bindings.setActiveUserProgramId(activeId)
        }
        return AppResult.Success(activeId ?: bindings.activeUserProgramId())
    }

    private fun resolveActiveUserProgramId(
        userPrograms: List<UserProgramExportDto>,
        programId: String?,
    ): String? {
        val activePrograms = userPrograms.filter { it.isActive && it.id.isNotBlank() }
        if (programId != null) {
            activePrograms.firstOrNull { it.programId == programId }?.id?.let { return it }
        }
        return activePrograms.firstOrNull()?.id
    }
}
