package com.movit.core.data.repository

import com.movit.core.network.dto.EffectivePlannedWorkoutDto
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
    fun hydrateFromBackend_skipsUserModifiedDay() {
        val localStore = testLocalStore()
        val store = DayCustomizationLocalStore(localStore)
        store.saveUserCustomizations(
            userProgramId = "up-1",
            weekNumber = 1,
            dayNumber = 1,
            plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-local")),
        )

        val serverCustomizations = buildJsonObject {
            putJsonArray("day_1_1") {
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive("pw-server"))
                    },
                )
            }
        }
        store.hydrateFromBackend(
            userProgramId = "up-1",
            customizations = serverCustomizations,
            serverCustomizationsUpdatedAt = "2099-01-01T00:00:00.000Z",
        )

        val cached = store.get("up-1", 1, 1)
        assertNotNull(cached)
        assertTrue(cached.isUserModified)
        assertEquals("pw-local", cached.plannedWorkouts.single().id)
    }

    @Test
    fun hydrateFromBackend_importsServerDayWhenNoLocalOverride() {
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
