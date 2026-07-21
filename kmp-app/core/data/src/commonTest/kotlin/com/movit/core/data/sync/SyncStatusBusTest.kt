package com.movit.core.data.sync

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.sync.MovitSyncOrchestrator.SyncOutcome
import com.movit.core.network.MovitClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusBusTest {

    @Test
    fun successWithCleanOutbox_isSyncedRing() = runBlocking {
        val bus = bus(online = true)

        bus.onSyncFinished(SyncOutcome.Success(home = null, explore = null, reports = null, isFullSync = true))

        assertEquals(SyncRingState.Synced, bus.status.value.ring)
    }

    @Test
    fun offlineNetworkWhileOnline_isProblemUnreachable() = runBlocking {
        val bus = bus(online = true)
        delay(50)

        bus.onSyncFinished(SyncOutcome.Offline(MovitSyncOrchestrator.ColdOfflineBundle(null, null, null, 0)))
        delay(50)

        assertEquals(SyncRingState.Problem, bus.status.value.ring)
        assertEquals(SyncProblemKind.ServerUnreachable, bus.status.value.problemKind)
    }

    @Test
    fun noNetwork_isOfflineQuiet() = runBlocking {
        val bus = bus(online = false)

        bus.onSyncFinished(SyncOutcome.Error("offline", SyncOutcome.ErrorKind.Network))

        assertEquals(SyncRingState.OfflineQuiet, bus.status.value.ring)
    }

    @Test
    fun syncInProgress_isSyncingRing() = runBlocking {
        val bus = bus(online = true)

        bus.onSyncStarted()

        assertEquals(SyncRingState.Syncing, bus.status.value.ring)
    }

    @Test
    fun failedOutbox_isProblem() = runBlocking {
        val store = InMemoryMovitLocalStore()
        store.insertOutbox(
            OutboxEntry(
                id = "ob-1",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = "{}",
                status = OutboxStatus.FAILED_PERMANENT,
                attempts = 3,
                createdAt = MovitClock.nowEpochMs(),
                ownerUserId = "user-test",
            ),
        )
        val bus = SyncStatusBus({ NetworkFake(true) }, store)
        delay(50)
        bus.refreshFromTelemetry()
        delay(50)

        assertEquals(SyncRingState.Problem, bus.status.value.ring)
        assertEquals(SyncProblemKind.OutboxFailed, bus.status.value.problemKind)
    }

    private fun bus(online: Boolean): SyncStatusBus =
        SyncStatusBus({ NetworkFake(online) }, InMemoryMovitLocalStore())

    private class NetworkFake(private val online: Boolean) : FakeMovitPlatformBindings() {
        override fun isNetworkAvailable(): Boolean = online
    }
}
