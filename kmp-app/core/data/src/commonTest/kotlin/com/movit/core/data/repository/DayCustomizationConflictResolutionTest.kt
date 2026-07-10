package com.movit.core.data.repository

import com.movit.core.data.outbox.DayCustomizationDayKey
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.outbox.OutboxSuccessHooks
import com.movit.core.data.outbox.SaveDayCustomizationsOutboxPayload
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.UserProgramUpdateRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DayCustomizationConflictResolutionTest {

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

        val pending = setOf(DayCustomizationDayKey("up-1", 1, 1))
        store.hydrateFromBackend(
            userProgramId = "up-1",
            customizations = serverDay("pw-server"),
            serverCustomizationsUpdatedAt = "2099-01-01T00:00:00.000Z",
            pendingDayKeys = pending,
        )

        assertEquals("pw-local", store.get("up-1", 1, 1)?.plannedWorkouts?.single()?.id)
    }

    @Test
    fun hydrateFromBackend_appliesNewerServerAfterSuccessHook() = runBlocking {
        val localStore = testLocalStore()
        val store = DayCustomizationLocalStore(localStore)
        val payload = MovitJson.encodeToString(
            SaveDayCustomizationsOutboxPayload(
                userProgramId = "up-1",
                weekNumber = 1,
                dayNumber = 1,
                request = UserProgramUpdateRequest(customizations = emptyMap()),
            ),
        )
        store.saveUserCustomizations(
            userProgramId = "up-1",
            weekNumber = 1,
            dayNumber = 1,
            plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-local")),
        )
        OutboxSuccessHooks.apply(localStore, OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS, payload)

        store.hydrateFromBackend(
            userProgramId = "up-1",
            customizations = serverDay("pw-server-newer"),
            serverCustomizationsUpdatedAt = "2099-01-01T00:00:00.000Z",
        )

        val cached = store.get("up-1", 1, 1)
        assertNotNull(cached)
        assertFalse(cached.isUserModified)
        assertEquals("pw-server-newer", cached.plannedWorkouts.single().id)
    }

    @Test
    fun hydrateFromBackend_doesNotOverwriteNewerLocalWithOlderServer() = runBlocking {
        val localStore = testLocalStore()
        val store = DayCustomizationLocalStore(localStore)
        val localMs = DayCustomizationLocalStore.parseIsoToEpochMs("2026-07-10T00:00:00.000Z")!!
        MovitClock.nowEpochMs = { localMs }
        store.saveUserCustomizations(
            userProgramId = "up-1",
            weekNumber = 1,
            dayNumber = 1,
            plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-local-newer")),
        )
        OutboxSuccessHooks.apply(
            localStore,
            OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS,
            MovitJson.encodeToString(
                SaveDayCustomizationsOutboxPayload(
                    userProgramId = "up-1",
                    weekNumber = 1,
                    dayNumber = 1,
                    request = UserProgramUpdateRequest(customizations = emptyMap()),
                ),
            ),
        )

        store.hydrateFromBackend(
            userProgramId = "up-1",
            customizations = serverDay("pw-server-older"),
            serverCustomizationsUpdatedAt = "2020-01-01T00:00:00.000Z",
        )

        assertEquals("pw-local-newer", store.get("up-1", 1, 1)?.plannedWorkouts?.single()?.id)
    }

    @Test
    fun pendingDayKeysFromOutbox_includesPendingAndInFlight() = runBlocking {
        val localStore = testLocalStore()
        val payload = MovitJson.encodeToString(
            SaveDayCustomizationsOutboxPayload(
                userProgramId = "up-1",
                weekNumber = 2,
                dayNumber = 3,
                request = UserProgramUpdateRequest(customizations = emptyMap()),
            ),
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-pending",
                type = OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS,
                payload = payload,
                createdAt = 1L,
                attempts = 0,
                status = OutboxStatus.PENDING,
            ),
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-inflight",
                type = OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS,
                payload = payload,
                createdAt = 2L,
                attempts = 1,
                status = OutboxStatus.IN_FLIGHT,
            ),
        )

        val keys = DayCustomizationLocalStore.pendingDayKeysFromOutbox(localStore)

        assertTrue(keys.contains(DayCustomizationDayKey("up-1", 2, 3)))
    }

    private fun serverDay(workoutId: String) = buildJsonObject {
        putJsonArray("day_1_1") {
            add(
                buildJsonObject {
                    put("id", JsonPrimitive(workoutId))
                },
            )
        }
    }
}
