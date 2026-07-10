package com.movit.core.data

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitClock
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionExpiryPreservesOutboxTest {

    private val localStore = InMemoryMovitLocalStore()

    @AfterTest
    fun tearDown() {
        MovitData.onSessionExpired = null
    }

    @Test
    fun sessionExpiry_preservesPendingOutbox() {
        runBlocking {
            MovitData.install(
                platform = FakeMovitPlatformBindings(storedUserId = "user-a"),
                localStoreFactory = MovitLocalStoreFactory { localStore },
            )
            localStore.writeJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA, """{"x":1}""")
            localStore.insertOutbox(
                OutboxEntry(
                    id = "exec-1",
                    type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                    payload = """{"id":"exec-1"}""",
                    createdAt = MovitClock.nowEpochMs(),
                    attempts = 0,
                    status = OutboxStatus.PENDING,
                    ownerUserId = "user-a",
                ),
            )

            // Session expiry path uses clearReadCaches (not clearAllUserData).
            MovitData.clearReadCaches()

            assertNull(localStore.readJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA))
            assertEquals(1L, localStore.countOutboxByStatus(OutboxStatus.PENDING))
            assertNotNull(localStore.getOutboxById("exec-1"))
            Unit
        }
    }
}
