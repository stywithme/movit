package com.movit.core.data.repository

import com.movit.core.data.outbox.DayCustomizationCacheDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DayCustomizationLocalStoreTest {

    @Test
    fun getEffectivePlannedWorkouts_returnsOverrideWhenPresent() {
        val localStore = testLocalStore()
        val store = DayCustomizationLocalStore(localStore)
        store.saveUserCustomizations(
            userProgramId = "up-1",
            weekNumber = 1,
            dayNumber = 1,
            plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-custom")),
        )

        val effective = store.getEffectivePlannedWorkouts(
            userProgramId = "up-1",
            weekNumber = 1,
            dayNumber = 1,
            originalWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-server")),
        )

        assertEquals("pw-custom", effective.single().id)
    }

    @Test
    fun hydrateFromBackend_skipsDayWithPendingOutbox() = runBlocking {
        val localStore = testLocalStore()
        val store = DayCustomizationLocalStore(localStore)
        store.saveUserCustomizations(
            userProgramId = "up-1",
            weekNumber = 1,
            dayNumber = 1,
            plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-local")),
        )

        store.hydrateFromBackend(
            userProgramId = "up-1",
            customizations = buildJsonObject {
                putJsonArray("day_1_1") {
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive("pw-server"))
                        },
                    )
                }
            },
            serverCustomizationsUpdatedAt = "2099-01-01T00:00:00.000Z",
            pendingDayKeys = setOf(com.movit.core.data.outbox.DayCustomizationDayKey("up-1", 1, 1)),
        )

        val cached = store.get("up-1", 1, 1)
        assertNotNull(cached)
        assertTrue(cached.isUserModified)
        assertEquals("pw-local", cached.plannedWorkouts.single().id)
    }

    @Test
    fun hydrateFromBackend_importsServerDayWhenNoLocalOverride() = runBlocking {
        val localStore = testLocalStore()
        val store = DayCustomizationLocalStore(localStore)
        val serverCustomizations = buildJsonObject {
            putJsonArray("day_1_1") {
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive("pw-imported"))
                    },
                )
            }
        }

        store.hydrateFromBackend(
            userProgramId = "up-1",
            customizations = serverCustomizations,
            serverCustomizationsUpdatedAt = "2026-06-01T00:00:00.000Z",
        )

        val effective = store.getEffectivePlannedWorkouts(
            userProgramId = "up-1",
            weekNumber = 1,
            dayNumber = 1,
            originalWorkouts = emptyList(),
        )
        assertEquals("pw-imported", effective.single().id)
    }

    @Test
    fun get_readsLegacyProgramIdKeyedCustomizationViaResolver() {
        val localStore = testLocalStore()
        UserProgramEnrollmentLocalStore(localStore).hydrateFromSync(
            listOf(
                com.movit.core.network.dto.UserProgramExportDto(
                    id = "up-1",
                    programId = "prog-1",
                    isActive = true,
                ),
            ),
            isFullSync = true,
        )
        com.movit.core.data.cache.MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
            MovitCacheKeys.dayCustomizationKey("prog-1", 1, 1),
            DayCustomizationCacheDto(
                userProgramId = "prog-1",
                weekNumber = 1,
                dayNumber = 1,
                plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-legacy")),
            ),
            DayCustomizationCacheDto.serializer(),
        )
        val store = DayCustomizationLocalStore(localStore)

        val cached = store.get("up-1", 1, 1)

        assertNotNull(cached)
        assertEquals("pw-legacy", cached.plannedWorkouts.single().id)
    }

    @Test
    fun mergeEffectivePlanWithDayOverrides_mergesOnRead() {
        val localStore = testLocalStore()
        DayCustomizationLocalStore(localStore).saveUserCustomizations(
            userProgramId = "up-1",
            weekNumber = 1,
            dayNumber = 1,
            plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-merged")),
        )

        val merged = mergeEffectivePlanWithDayOverrides(
            localStore,
            com.movit.core.network.dto.EffectivePlanPayloadDto(
                userProgramId = "up-1",
                weekNumber = 1,
                dayNumber = 1,
                plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-base")),
            ),
        )

        assertEquals("pw-merged", merged.plannedWorkouts.single().id)
    }
}
