package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.UserProgramExportDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Durable offline store for authenticated user-program enrollments from `/api/mobile/sync`.
 */
@Serializable
data class UserProgramEnrollmentCacheDto(
    val id: String,
    val programId: String? = null,
    val name: LocalizedNameDto? = null,
    val startDate: String? = null,
    val isActive: Boolean = false,
    val trainingWeekdays: List<Int> = emptyList(),
    val updatedAt: String? = null,
    val customizationsUpdatedAt: String? = null,
)

open class UserProgramEnrollmentLocalStore(
    private val localStore: MovitLocalStore,
) {
    fun get(userProgramId: String): UserProgramEnrollmentCacheDto? {
        if (userProgramId.isBlank()) return null
        return MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.USER_PROGRAM_ENROLLMENT_STORE,
            MovitCacheKeys.userProgramEnrollmentKey(userProgramId),
            UserProgramEnrollmentCacheDto.serializer(),
        )
    }

    fun listAll(): List<UserProgramEnrollmentCacheDto> =
        readEnrollmentIndex()
            .mapNotNull(::get)
            .sortedBy { it.id }

    fun resolveActiveUserProgramId(programId: String? = null): String? {
        val activePrograms = listAll().filter { it.isActive && it.id.isNotBlank() }
        if (programId != null) {
            activePrograms.firstOrNull { it.programId == programId }?.id?.let { return it }
        }
        return activePrograms.firstOrNull()?.id
    }

    open fun hydrateFromSync(rows: List<UserProgramExportDto>, isFullSync: Boolean) {
        val validRows = rows.filter { it.id.isNotBlank() }
        if (validRows.isEmpty() && !isFullSync) return

        val incomingIds = validRows.map { it.id }.toSet()
        if (isFullSync) {
            (readEnrollmentIndex().toSet() - incomingIds).forEach(::remove)
        }

        val index = if (isFullSync) {
            mutableSetOf<String>()
        } else {
            readEnrollmentIndex().toMutableSet()
        }

        for (row in validRows) {
            upsert(row.toCacheDto())
            index += row.id
        }
        writeEnrollmentIndex(index)
    }

    private fun upsert(enrollment: UserProgramEnrollmentCacheDto) {
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.USER_PROGRAM_ENROLLMENT_STORE,
            MovitCacheKeys.userProgramEnrollmentKey(enrollment.id),
            enrollment,
            UserProgramEnrollmentCacheDto.serializer(),
        )
    }

    private fun remove(userProgramId: String) {
        localStore.remove(
            MovitCacheKeys.USER_PROGRAM_ENROLLMENT_STORE,
            MovitCacheKeys.userProgramEnrollmentKey(userProgramId),
        )
    }

    private fun readEnrollmentIndex(): List<String> =
        MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.USER_PROGRAM_ENROLLMENT_STORE,
            MovitCacheKeys.USER_PROGRAM_ENROLLMENT_INDEX,
            ListSerializer(String.serializer()),
        ).orEmpty()

    private fun writeEnrollmentIndex(ids: Set<String>) {
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.USER_PROGRAM_ENROLLMENT_STORE,
            MovitCacheKeys.USER_PROGRAM_ENROLLMENT_INDEX,
            ids.sorted(),
            ListSerializer(String.serializer()),
        )
    }

    private fun UserProgramExportDto.toCacheDto(): UserProgramEnrollmentCacheDto =
        UserProgramEnrollmentCacheDto(
            id = id,
            programId = programId,
            name = name,
            startDate = startDate,
            isActive = isActive,
            trainingWeekdays = trainingWeekdays.orEmpty(),
            updatedAt = updatedAt,
            customizationsUpdatedAt = customizationsUpdatedAt,
        )
}
