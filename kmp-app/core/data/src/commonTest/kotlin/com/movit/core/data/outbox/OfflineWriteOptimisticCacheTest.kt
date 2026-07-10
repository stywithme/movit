package com.movit.core.data.outbox

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.TrainTodayWorkoutDto
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class OfflineWriteOptimisticCacheTest {
    @Test
    fun complete_marksTodayCompleted_and_rollbackOnPermanentFailure() = runBlocking {
        val store = InMemoryMovitLocalStore()
        seedHome(store, isCompleted = false)

        val payload = MovitJson.encodeToString(
            PlannedWorkoutCompleteOutboxPayload.serializer(),
            PlannedWorkoutCompleteOutboxPayload(
                workoutId = "pw-1",
                request = PlannedWorkoutCompleteRequestDto(),
            ),
        )
        OfflineWriteOptimisticCache.applyOptimistic(
            store,
            OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
            payload,
        )
        assertEquals(true, readHome(store)?.trainMode?.todayWorkout?.isCompleted)

        OfflineWriteOptimisticCache.onPermanentFailure(
            store,
            OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
            payload,
        )
        assertEquals(false, readHome(store)?.trainMode?.todayWorkout?.isCompleted)
        assertEquals("1", store.readString(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_UPLOAD_FAILED))
    }

    @Test
    fun report_doesNotMarkCompleted() {
        val store = InMemoryMovitLocalStore()
        seedHome(store, isCompleted = false)
        val payload = MovitJson.encodeToString(
            PlannedWorkoutCompleteOutboxPayload.serializer(),
            PlannedWorkoutCompleteOutboxPayload(
                workoutId = "pw-1",
                request = PlannedWorkoutCompleteRequestDto(),
            ),
        )
        OfflineWriteOptimisticCache.applyOptimistic(
            store,
            OutboxOperationType.PLANNED_WORKOUT_REPORT,
            payload,
        )
        assertEquals(false, readHome(store)?.trainMode?.todayWorkout?.isCompleted)
        assertNull(store.readString(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_UPLOAD_FAILED))
    }

    private fun seedHome(store: InMemoryMovitLocalStore, isCompleted: Boolean) {
        val home = HomeDataDto(
            trainMode = TrainModeDto(
                status = "active",
                todayWorkout = TrainTodayWorkoutDto(
                    plannedWorkoutId = "pw-1",
                    isCompleted = isCompleted,
                ),
            ),
        )
        store.writeJsonCache(
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            MovitJson.encodeToString(HomeDataDto.serializer(), home),
        )
    }

    private fun readHome(store: InMemoryMovitLocalStore): HomeDataDto? {
        val raw = store.readJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA) ?: return null
        return MovitJson.decodeFromString(HomeDataDto.serializer(), raw)
    }
}
