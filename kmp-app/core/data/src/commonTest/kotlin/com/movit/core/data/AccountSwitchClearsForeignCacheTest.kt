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

class AccountSwitchClearsForeignCacheTest {

    private val localStore = InMemoryMovitLocalStore()

    @AfterTest
    fun tearDown() {
        MovitData.onSessionExpired = null
    }

    @Test
    fun accountSwitch_clearsReadCachesAndForeignOutbox() = runBlocking {
        MovitData.install(
            platform = FakeMovitPlatformBindings(storedUserId = "user-a"),
            localStoreFactory = MovitLocalStoreFactory { localStore },
        )
        localStore.writeJsonCache(
            MovitCacheKeys.AUTH_LIFECYCLE_STORE,
            MovitCacheKeys.LAST_KNOWN_USER_ID,
            "user-a",
        )
        localStore.writeJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA, """{"user":"a"}""")
        localStore.writeJsonCache(
            MovitCacheKeys.WORKOUT_RUN_STORE,
            MovitCacheKeys.workoutRunKey("w-a"),
            "run-a|w-a|0|1|PRE_EXERCISE|squat|Active|1|user-a",
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "a-op",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = "user-a",
            ),
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "guest-op",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = null,
            ),
        )

        val prompt = MovitData.onAuthenticatedSession("user-b")

        assertNull(localStore.readJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA))
        assertNull(
            localStore.readJsonCache(
                MovitCacheKeys.WORKOUT_RUN_STORE,
                MovitCacheKeys.workoutRunKey("w-a"),
            ),
        )
        assertNull(localStore.getOutboxById("a-op"))
        assertNotNull(localStore.getOutboxById("guest-op"))
        assertEquals("user-b", localStore.readJsonCache(MovitCacheKeys.AUTH_LIFECYCLE_STORE, MovitCacheKeys.LAST_KNOWN_USER_ID))
        assertEquals(1, prompt?.guestRowCount)
    }
}
