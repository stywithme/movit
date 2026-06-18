package com.movit.core.data.repository

import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.UserProgramExportDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProgramEnrollmentLocalStoreTest {

    @Test
    fun hydrateFromSync_persistsFullEnrollmentFields() {
        val platform = FakeMovitPlatformBindings()
        val localStore = testLocalStore(platform)
        val store = UserProgramEnrollmentLocalStore(localStore)

        store.hydrateFromSync(
            rows = listOf(
                UserProgramExportDto(
                    id = "up-1",
                    programId = "prog-1",
                    name = LocalizedNameDto(en = "Strength"),
                    startDate = "2026-01-15",
                    isActive = true,
                    trainingWeekdays = listOf(1, 3, 5),
                    updatedAt = "2026-06-10T00:00:00Z",
                    customizationsUpdatedAt = "2026-06-09T00:00:00Z",
                ),
            ),
            isFullSync = true,
        )

        val cached = store.get("up-1")
        assertEquals("prog-1", cached?.programId)
        assertEquals("Strength", cached?.name?.en)
        assertEquals("2026-01-15", cached?.startDate)
        assertEquals(true, cached?.isActive)
        assertEquals(listOf(1, 3, 5), cached?.trainingWeekdays)
        assertEquals("2026-06-10T00:00:00Z", cached?.updatedAt)
        assertEquals("2026-06-09T00:00:00Z", cached?.customizationsUpdatedAt)
        assertEquals("up-1", store.resolveActiveUserProgramId())
    }

    @Test
    fun hydrateFromSync_fullSyncRemovesStaleEnrollments() {
        val platform = FakeMovitPlatformBindings()
        val localStore = testLocalStore(platform)
        val store = UserProgramEnrollmentLocalStore(localStore)

        store.hydrateFromSync(
            rows = listOf(
                UserProgramExportDto(id = "up-old", programId = "prog-old", isActive = false),
                UserProgramExportDto(id = "up-keep", programId = "prog-keep", isActive = true),
            ),
            isFullSync = true,
        )
        store.hydrateFromSync(
            rows = listOf(
                UserProgramExportDto(id = "up-keep", programId = "prog-keep", isActive = true),
            ),
            isFullSync = true,
        )

        assertNull(store.get("up-old"))
        assertEquals(1, store.listAll().size)
    }

    @Test
    fun resolveActiveUserProgramId_prefersRequestedProgram() {
        val platform = FakeMovitPlatformBindings()
        val localStore = testLocalStore(platform)
        val store = UserProgramEnrollmentLocalStore(localStore)

        store.hydrateFromSync(
            rows = listOf(
                UserProgramExportDto(id = "up-a", programId = "prog-a", isActive = true),
                UserProgramExportDto(id = "up-b", programId = "prog-b", isActive = true),
            ),
            isFullSync = true,
        )

        assertEquals("up-b", store.resolveActiveUserProgramId(programId = "prog-b"))
    }
}
